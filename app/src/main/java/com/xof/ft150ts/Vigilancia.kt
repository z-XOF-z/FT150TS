package com.xof.ft150ts

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Antirrobo del teléfono: con la moto estacionada, si alguien mueve el teléfono (o la tarjeta avisa que movieron la
 * moto) y no eres tú en 20 s, te manda por Telegram la ubicación, una foto de cada cámara y ~20 s de audio. Usa tu bot
 * (el mismo de la tarjeta): el teléfono solo MANDA mensajes, nunca lee órdenes (eso lo hace la tarjeta), así no chocan.
 *
 * Android 14: la cámara y el micrófono con la pantalla apagada solo se pueden usar si el servicio "App activa" se puso
 * en primer plano con esos tipos mientras la app estaba a la vista (ServicioMoto lo hace cuando esto está prendido).
 */
@SuppressLint("MissingPermission")
object Vigilancia {
    val activa = MutableStateFlow(false)      // la función está prendida (Ajustes)
    val autoArmar = MutableStateFlow(true)    // se arma sola al apagar la moto
    val estado = MutableStateFlow(VigilanciaLogica.Estado())
    val mensaje = MutableStateFlow("")
    val token = MutableStateFlow("")
    val chat = MutableStateFlow("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private var prefs: SharedPreferences? = null
    private var app: Context? = null
    private var sm: SensorManager? = null
    private var reloj: Job? = null
    private var enviando: Job? = null
    private var base: FloatArray? = null
    private var seguidas = 0

    fun iniciar(ctx: Context) {
        if (app != null) return
        val a = ctx.applicationContext
        app = a
        val p = a.getSharedPreferences("vigilancia", Context.MODE_PRIVATE)
        prefs = p
        activa.value = p.getBoolean("activa", false)
        autoArmar.value = p.getBoolean("auto", true)
        token.value = p.getString("token", "") ?: ""
        chat.value = p.getString("chat", "") ?: ""
        sm = a.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        // Desbloqueaste el teléfono: eres tú
        try {
            ContextCompat.registerReceiver(a, object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) { alDueno("Desbloqueaste el teléfono") }
            }, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) { }
        // Prendiste la moto (carga del switch) o giraste la llave: eres tú
        scope.launch { Energia.enMoto.collect { if (it) alDueno("Prendiste la moto") } }
        scope.launch { Moto.rapida.collect { if (it.ignicion) alDueno("Giraste la llave") } }
    }

    fun listo() = activa.value && LogicaTelegram.tokenValido(token.value) && chat.value.isNotBlank()

    fun ponerActiva(on: Boolean) {
        activa.value = on
        prefs?.edit()?.putBoolean("activa", on)?.apply()
        if (!on) alDueno("")
        app?.let { ServicioMoto.arrancar(it) }      // con cámara y micrófono (o sin ellos) según esto
    }

    fun ponerAutoArmar(on: Boolean) { autoArmar.value = on; prefs?.edit()?.putBoolean("auto", on)?.apply() }

    fun ponerTelegram(t: String, c: String) {
        token.value = t.trim(); chat.value = c.trim()
        prefs?.edit()?.putString("token", token.value)?.putString("chat", chat.value)?.apply()
    }

    /** La llama Encendido al apagar el switch de la moto. */
    fun alApagarMoto() { if (listo() && autoArmar.value) armar() }

    fun armar() {
        if (!listo()) { mensaje.value = "Primero pon tu bot de Telegram y prende el antirrobo."; return }
        poner(VigilanciaLogica.armar(System.currentTimeMillis()))
        mensaje.value = "Se arma en 2 min si nadie mueve el teléfono."
    }

    /** La tarjeta avisó que movieron la moto (mensaje "m" por Bluetooth). */
    fun movimientoExterno() = moverse()

    private fun alDueno(motivo: String) {
        if (estado.value.e == EstadoVigilancia.APAGADA) return
        poner(VigilanciaLogica.dueno())
        if (motivo.isNotBlank()) mensaje.value = "Desarmada: $motivo."
    }

    private fun poner(s: VigilanciaLogica.Estado) {
        estado.value = s
        if (s.e == EstadoVigilancia.APAGADA) { detenerSensores(); reloj?.cancel(); reloj = null; return }
        arrancarSensores()
        if (reloj?.isActive != true) reloj = scope.launch {
            while (isActive) {
                delay(1_000)
                val paso = VigilanciaLogica.tick(estado.value, System.currentTimeMillis())
                if (paso.estado != estado.value) {
                    estado.value = paso.estado
                    if (paso.estado.e == EstadoVigilancia.ARMADA) mensaje.value = "Armada: vigilando."
                }
                if (paso.enviar) enviar(false)
            }
        }
    }

    private fun moverse() {
        val paso = VigilanciaLogica.movimiento(estado.value, System.currentTimeMillis())
        if (paso.estado != estado.value) {
            estado.value = paso.estado
            if (paso.estado.e == EstadoVigilancia.SOSPECHA) mensaje.value = "Se movió: si no eres tú en 20 s, te aviso."
        }
        if (paso.enviar) enviar(false)
    }

    // ---------- Sensor (bajo consumo: lecturas en lote cada segundo) ----------
    private val oyente = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val v = e.values
            val b = base
            if (b == null) { base = floatArrayOf(v[0], v[1], v[2]); return }
            val dx = v[0] - b[0]; val dy = v[1] - b[1]; val dz = v[2] - b[2]
            val cambio = sqrt(dx * dx + dy * dy + dz * dz)
            seguidas = if (cambio > 1.2f) seguidas + 1 else 0
            for (k in 0..2) b[k] += 0.02f * (v[k] - b[k])          // se acomoda despacio a la posición en reposo
            if (VigilanciaLogica.seMovio(cambio, seguidas)) { seguidas = 0; moverse() }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private var escuchando = false
    private fun arrancarSensores() {
        if (escuchando) return
        val s = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        base = null
        escuchando = sm?.registerListener(oyente, s, 100_000, 1_000_000) == true
    }

    private fun detenerSensores() {
        if (!escuchando) return
        sm?.unregisterListener(oyente)
        escuchando = false
    }

    // ---------- Envío ----------
    /** Manda la alarma (o una prueba): texto + ubicación, foto trasera, foto delantera y audio. */
    fun enviar(prueba: Boolean) {
        val ctx = app ?: return
        if (!LogicaTelegram.tokenValido(token.value) || chat.value.isBlank()) { mensaje.value = "Falta tu bot de Telegram."; return }
        if (enviando?.isActive == true) return
        enviando = scope.launch {
            val t = token.value; val c = chat.value
            mensaje.value = if (prueba) "Mandando la prueba…" else "¡Alarma! Mandando fotos y audio…"
            val pos = UbicacionManager.posicionActual()
            withContext(Dispatchers.IO) {
                TelegramTel.mensaje(t, c, if (prueba) "✅ Prueba del antirrobo del teléfono (FT150TS)." else "🚨 Movieron el teléfono de la moto y no eras tú.")
                pos?.let { TelegramTel.ubicacion(t, c, it.first, it.second) }
            }
            for (frontal in listOf(false, true)) {
                val f = try { foto(ctx, frontal) } catch (_: Exception) { null }
                if (f != null) withContext(Dispatchers.IO) {
                    TelegramTel.archivo(t, c, "sendPhoto", "photo", f, "image/jpeg", if (frontal) "Cámara delantera" else "Cámara trasera")
                    f.delete()
                }
            }
            val a = audio(ctx, if (prueba) 5 else 20)
            if (a != null) withContext(Dispatchers.IO) {
                TelegramTel.archivo(t, c, "sendAudio", "audio", a, "audio/mp4", "Audio del momento")
                a.delete()
            }
            mensaje.value = if (prueba) "Prueba enviada: revisa tu Telegram." else "Alarma enviada a tu Telegram."
        }
    }

    private class Dueno : LifecycleOwner {
        val reg = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = reg
    }

    private suspend fun proveedor(ctx: Context): ProcessCameraProvider = suspendCancellableCoroutine { c ->
        val f = ProcessCameraProvider.getInstance(ctx)
        f.addListener({ c.resume(f.get()) }, ContextCompat.getMainExecutor(ctx))
    }

    /** Una foto de la cámara trasera o la delantera (en segundo plano, sin vista previa). */
    private suspend fun foto(ctx: Context, frontal: Boolean): File? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return null
        val prov = proveedor(ctx)
        val sel = if (frontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (!prov.hasCamera(sel)) return null
        val dueno = Dueno().apply { reg.currentState = Lifecycle.State.RESUMED }
        val cap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        return try {
            prov.bindToLifecycle(dueno, sel, cap)
            delay(1_200)                                   // que ajuste la luz
            val f = File(ctx.cacheDir, "antirrobo_${if (frontal) "delantera" else "trasera"}.jpg")
            val ok = suspendCancellableCoroutine { c ->
                cap.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(ctx),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(r: ImageCapture.OutputFileResults) { c.resume(true) }
                        override fun onError(e: ImageCaptureException) { c.resume(false) }
                    })
            }
            if (ok) f else null
        } finally {
            try { prov.unbind(cap) } catch (_: Exception) { }
            dueno.reg.currentState = Lifecycle.State.DESTROYED
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun audio(ctx: Context, seg: Int): File? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return@withContext null
        val f = File(ctx.cacheDir, "antirrobo.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000)
            r.setOutputFile(f.path)
            r.prepare()
            r.start()
            delay(seg * 1000L)
            r.stop()
            f
        } catch (_: Exception) {
            null
        } finally {
            r.release()
        }
    }
}

/** Mandar cosas a Telegram desde el teléfono (solo envíos: las órdenes las lee la tarjeta). */
object TelegramTel {
    private fun url(token: String, metodo: String) = URL("https://api.telegram.org/bot$token/$metodo")

    fun mensaje(token: String, chat: String, texto: String): Boolean =
        json(token, "sendMessage", JSONObject().put("chat_id", chat).put("text", texto))

    fun ubicacion(token: String, chat: String, lat: Double, lon: Double): Boolean =
        json(token, "sendLocation", JSONObject().put("chat_id", chat).put("latitude", lat).put("longitude", lon))

    private fun json(token: String, metodo: String, cuerpo: JSONObject): Boolean = try {
        val c = url(token, metodo).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000; c.doOutput = true; c.requestMethod = "POST"
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(cuerpo.toString().toByteArray()) }
        val ok = c.responseCode == 200
        c.disconnect()
        ok
    } catch (_: Exception) { false }

    /** Foto o audio (multipart/form-data). */
    fun archivo(token: String, chat: String, metodo: String, campo: String, f: File, tipo: String, texto: String): Boolean = try {
        val limite = "----ft150ts" + System.nanoTime()
        val c = url(token, metodo).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 60_000; c.doOutput = true; c.requestMethod = "POST"
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$limite")
        DataOutputStream(c.outputStream).use { o ->
            fun campoTexto(n: String, v: String) {
                o.writeBytes("--$limite\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n")
                o.write(v.toByteArray()); o.writeBytes("\r\n")
            }
            campoTexto("chat_id", chat)
            campoTexto("caption", texto)
            o.writeBytes("--$limite\r\nContent-Disposition: form-data; name=\"$campo\"; filename=\"${f.name}\"\r\nContent-Type: $tipo\r\n\r\n")
            f.inputStream().use { it.copyTo(o) }
            o.writeBytes("\r\n--$limite--\r\n")
        }
        val ok = c.responseCode == 200
        c.disconnect()
        ok
    } catch (_: Exception) { false }
}
