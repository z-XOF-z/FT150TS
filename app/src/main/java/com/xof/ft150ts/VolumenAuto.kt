package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Volumen progresivo (v3.8): sube el volumen de la música con la velocidad para tapar el viento del casco.
 * La lógica (rangos, histéresis, pasos suaves, qué hacer parado) está en VolumenLogica (con pruebas).
 *  - Velocidad: la de la tarjeta de la moto; sin tarjeta, la del GPS solo si es confiable (bajo techo no cuenta).
 *  - Solo trabaja en la moto (switch abierto) y revisa una vez por segundo.
 *  - Si cambias el volumen a mano, se pausa 2 min y respeta tu cambio.
 *  - Al apagar la función (o al cerrar el switch) regresa el volumen que tenías.
 * Es el volumen del sistema (STREAM_MUSIC); con audífonos Bluetooth con volumen absoluto, es el del audífono.
 */
object VolumenAuto {
    val activo = MutableStateFlow(false)
    val ajustes = MutableStateFlow(AjustesVolumen())
    /** Nivel puesto por la función ahora (para mostrar), -1 = sin tocar. */
    val nivel = MutableStateFlow(-1)

    private const val PAUSA_MANUAL_MS = 120_000L

    private var prefs: SharedPreferences? = null
    private var am: AudioManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ciclo: Job? = null
    private var logica: VolumenProgresivo? = null
    /** Volumen que tenías antes de que la función lo moviera (-1 = no se ha guardado). */
    private var original = -1
    private var ultimoPuesto = -1
    private var pausaHasta = 0L

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("volumenAuto", Context.MODE_PRIVATE)
        prefs = p
        am = ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        activo.value = p.getBoolean("activo", false)
        ajustes.value = AjustesVolumen(
            velIni = p.getFloat("velIni", 20f), velMax = p.getFloat("velMax", 90f),
            volMin = p.getInt("volMin", 45), volMax = p.getInt("volMax", 90),
            alDetenerse = AlDetenerse.entries.getOrElse(p.getInt("alDetenerse", 0)) { AlDetenerse.MANTENER },
        )
        original = p.getInt("original", -1)
        if (activo.value) arrancar()
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on; prefs?.edit()?.putBoolean("activo", on)?.apply()
        if (on) arrancar() else { detener(); restaurar() }
    }

    fun ponerAjustes(a: AjustesVolumen) {
        val ok = a.copy(
            velMax = a.velMax.coerceAtLeast(a.velIni + 10f),
            volMin = a.volMin.coerceIn(0, 100), volMax = a.volMax.coerceIn(a.volMin, 100),
        )
        ajustes.value = ok
        logica?.ajustes = ok
        prefs?.edit()?.putFloat("velIni", ok.velIni)?.putFloat("velMax", ok.velMax)?.putInt("volMin", ok.volMin)
            ?.putInt("volMax", ok.volMax)?.putInt("alDetenerse", ok.alDetenerse.ordinal)?.apply()
    }

    private fun arrancar() {
        val a = am ?: return
        if (ciclo?.isActive == true) return
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        logica = VolumenProgresivo(max).also { it.ajustes = ajustes.value }
        ciclo = scope.launch {
            while (isActive) {
                delay(1_000)
                if (Energia.enMoto.value) paso(a) else if (original >= 0 && !Encendido.atenuando) { restaurar(); logica?.reiniciar() }
            }
        }
    }

    private fun detener() { ciclo?.cancel(); ciclo = null; nivel.value = -1 }

    private fun paso(a: AudioManager) {
        val l = logica ?: return
        val ahora = SystemClock.elapsedRealtime()
        val actual = a.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (original < 0) { original = actual; prefs?.edit()?.putInt("original", actual)?.apply() }
        // ¿Lo cambiaste tú? Se respeta un rato
        if (ultimoPuesto >= 0 && actual != ultimoPuesto) { pausaHasta = ahora + PAUSA_MANUAL_MS; ultimoPuesto = -1; l.reiniciar() }
        if (ahora < pausaHasta) return
        val r = Moto.rapida.value
        val vel = if (r.vel >= 0f) r.vel else UbicacionManager.velocidadConfiableKmh()
        val nuevo = l.paso(ahora, vel, actual, original) ?: return
        try { a.setStreamVolume(AudioManager.STREAM_MUSIC, nuevo, 0) } catch (_: Exception) { return }
        ultimoPuesto = nuevo
        nivel.value = nuevo
    }

    /** Regresa el volumen que tenías antes. */
    private fun restaurar() {
        val a = am ?: return
        if (original >= 0) try { a.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0) } catch (_: Exception) { }
        original = -1; ultimoPuesto = -1; nivel.value = -1
        prefs?.edit()?.putInt("original", -1)?.apply()
    }
}
