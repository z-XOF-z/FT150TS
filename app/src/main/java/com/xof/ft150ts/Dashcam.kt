package com.xof.ft150ts

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dashcam: graba el camino con la cámara de atrás en pedazos de 1 minuto y se queda con los
 * últimos N minutos (los viejos se borran solos). "Guardar clip" (o un golpe fuerte) copia los
 * últimos 3 minutos a la galería, en Películas/FT150TS. Sin audio (solo sería viento).
 * Graba solo con la app en pantalla y, si quieres, solo en la moto. Se pausa si el teléfono se calienta.
 */
object Dashcam {
    val activo = MutableStateFlow(false)
    val soloEnMoto = MutableStateFlow(true)
    val minutos = MutableStateFlow(10)           // minutos que se guardan en bucle
    val calidadAlta = MutableStateFlow(false)    // false = 720p (calienta menos), true = 1080p
    val grabando = MutableStateFlow(false)
    val mensaje = MutableStateFlow("")
    val usoMb = MutableStateFlow(0)
    /** La pantalla pide el permiso de la cámara (lo atiende MainActivity). */
    val pedirPermiso = MutableStateFlow(false)

    private var ctx: Context? = null
    private var actividad: ComponentActivity? = null
    private var proveedor: ProcessCameraProvider? = null
    private var captura: VideoCapture<Recorder>? = null
    private var grabacion: Recording? = null
    private var vigilante: Job? = null
    private var corte: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private const val SEGMENTO_MS = 60_000L

    fun iniciar(c: Context) {
        if (ctx != null) return
        val app = c.applicationContext
        ctx = app
        val p = app.getSharedPreferences("dashcam", Context.MODE_PRIVATE)
        activo.value = p.getBoolean("activo", false)
        soloEnMoto.value = p.getBoolean("soloEnMoto", true)
        minutos.value = p.getInt("minutos", 10)
        calidadAlta.value = p.getBoolean("alta", false)
        actualizarUso()
    }

    private fun carpeta(): File = File(ctx!!.filesDir, "dashcam").also { it.mkdirs() }

    private fun permiso(): Boolean = ctx?.let {
        ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    } == true

    private fun guardarPref(f: (android.content.SharedPreferences.Editor) -> Unit) {
        ctx?.getSharedPreferences("dashcam", Context.MODE_PRIVATE)?.edit()?.also(f)?.apply()
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on
        guardarPref { it.putBoolean("activo", on) }
        if (on && !permiso()) pedirPermiso.value = true
        revisar()
    }
    fun ponerSoloEnMoto(on: Boolean) { soloEnMoto.value = on; guardarPref { it.putBoolean("soloEnMoto", on) }; revisar() }
    fun ponerMinutos(m: Int) { minutos.value = m.coerceIn(3, 60); guardarPref { it.putInt("minutos", minutos.value) }; limpiar() }
    fun ponerCalidad(alta: Boolean) {
        calidadAlta.value = alta
        guardarPref { it.putBoolean("alta", alta) }
        // Para cambiar la calidad hay que volver a preparar la cámara
        if (actividad != null) { val a = actividad!!; soltar(); adjuntar(a) }
    }

    /** MainActivity.onStart: la cámara se usa con la app en pantalla. */
    fun adjuntar(a: ComponentActivity) {
        actividad = a
        vigilante?.cancel()
        vigilante = scope.launch {
            while (isActive) { revisar(); delay(2_000) }
        }
    }

    /** MainActivity.onStop. */
    fun soltar() {
        vigilante?.cancel()
        vigilante = null
        detener()
        try { proveedor?.unbindAll() } catch (_: Exception) { }
        captura = null
        actividad = null
    }

    private fun debeGrabar(): Boolean =
        activo.value && actividad != null && permiso() &&
            (!soloEnMoto.value || Energia.enMoto.value) && !ModoSueno.dormido.value &&
            Termico.nivel.value < NivelCalor.CALIENTE

    private fun revisar() {
        val debe = debeGrabar()
        if (debe && grabacion == null) empezar()
        else if (!debe && grabacion != null) {
            if (Termico.nivel.value >= NivelCalor.CALIENTE) mensaje.value = "En pausa: el teléfono está caliente"
            detener()
        }
    }

    private fun empezar() {
        val a = actividad ?: return
        val c = captura
        if (c != null) { segmento(c); return }
        try {
            val futuro = ProcessCameraProvider.getInstance(a)
            futuro.addListener({
                try {
                    val p = futuro.get()
                    proveedor = p
                    val calidad = if (calidadAlta.value) Quality.FHD else Quality.HD
                    val grabador = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(calidad, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                        .build()
                    val vc = VideoCapture.withOutput(grabador)
                    p.unbindAll()
                    p.bindToLifecycle(a, CameraSelector.DEFAULT_BACK_CAMERA, vc)
                    captura = vc
                    if (debeGrabar()) segmento(vc)
                } catch (e: Exception) {
                    mensaje.value = "No se pudo abrir la cámara: ${e.message ?: ""}"
                }
            }, ContextCompat.getMainExecutor(a))
        } catch (e: Exception) {
            mensaje.value = "No se pudo abrir la cámara"
        }
    }

    /** Graba un pedazo de 1 minuto; al terminar empieza el siguiente si sigue tocando grabar. */
    private fun segmento(vc: VideoCapture<Recorder>) {
        val c = ctx ?: return
        if (grabacion != null) return
        val nombre = "seg_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val archivo = File(carpeta(), nombre)
        try {
            var esta: Recording? = null
            esta = vc.output.prepareRecording(c, FileOutputOptions.Builder(archivo).build())
                .start(ContextCompat.getMainExecutor(c)) { e ->
                    if (e is VideoRecordEvent.Finalize) {
                        if (e.hasError() && archivo.length() < 1024) archivo.delete()
                        limpiar()
                        // Solo la grabación vigente encadena la siguiente
                        if (grabacion === esta) {
                            grabacion = null
                            if (debeGrabar()) captura?.let { segmento(it) } else grabando.value = false
                        }
                    }
                }
            grabacion = esta
            grabando.value = true
            mensaje.value = ""
            corte?.cancel()
            corte = scope.launch { delay(SEGMENTO_MS); grabacion?.stop() }
        } catch (e: Exception) {
            grabacion = null
            grabando.value = false
            mensaje.value = "No se pudo grabar: ${e.message ?: ""}"
        }
    }

    private fun detener() {
        corte?.cancel()
        val g = grabacion
        grabacion = null
        try { g?.stop() } catch (_: Exception) { }
        grabando.value = false
    }

    /** Borra los pedazos más viejos que los minutos elegidos. */
    private fun limpiar() {
        val c = ctx ?: return
        scope.launch(Dispatchers.IO) {
            val segs = File(c.filesDir, "dashcam").listFiles { f -> f.name.startsWith("seg_") }?.sortedBy { it.name } ?: return@launch
            val sobran = segs.size - minutos.value
            if (sobran > 0) segs.take(sobran).forEach { it.delete() }
            actualizarUso()
        }
    }

    private fun actualizarUso() {
        val c = ctx ?: return
        val total = File(c.filesDir, "dashcam").listFiles()?.sumOf { it.length() } ?: 0L
        usoMb.value = (total / (1024 * 1024)).toInt()
    }

    /** Un golpe muy fuerte (posible caída): guarda lo que se grabó. */
    fun protegerPorImpacto() {
        if (!grabando.value) return
        guardarClip("Golpe fuerte: video guardado")
    }

    /**
     * Copia los últimos 3 minutos a la galería. El pedazo que se está grabando se corta primero
     * para que quede completo (la grabación sigue sola con el siguiente).
     */
    fun guardarClip(texto: String = "Clip guardado en Películas/FT150TS") {
        val c = ctx ?: return
        if (grabacion != null) grabacion?.stop()
        scope.launch {
            delay(1_500)        // que termine de cerrarse el pedazo actual
            val n = withContext(Dispatchers.IO) {
                val segs = File(c.filesDir, "dashcam").listFiles { f -> f.name.startsWith("seg_") && f.length() > 1024 }
                    ?.sortedBy { it.name }?.takeLast(3) ?: emptyList()
                segs.count { copiarAGaleria(c, it) }
            }
            mensaje.value = if (n > 0) texto else "No hay video para guardar todavía"
        }
    }

    private fun copiarAGaleria(c: Context, f: File): Boolean = try {
        val nombre = "FT150TS_" + f.name.removePrefix("seg_")
        if (Build.VERSION.SDK_INT >= 29) {
            val valores = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, nombre)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FT150TS")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val r = c.contentResolver
            val uri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valores) ?: throw IllegalStateException()
            r.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            valores.clear()
            valores.put(MediaStore.Video.Media.IS_PENDING, 0)
            r.update(uri, valores, null, null)
        } else {
            val dir = File(c.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "FT150TS").also { it.mkdirs() }
            f.copyTo(File(dir, nombre), overwrite = true)
        }
        true
    } catch (_: Exception) { false }
}
