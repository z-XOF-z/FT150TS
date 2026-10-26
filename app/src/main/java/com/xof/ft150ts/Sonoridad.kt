package com.xof.ft150ts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Sonoridad + (v3.8): sube la sonoridad de la música por encima del 100 % del volumen con el Loudness Enhancer de
 * Android (compresor + limitador: sube lo bajito sin reventar los picos, así no satura). El "150 %" que pediste
 * equivale a unos +3.5 dB de amplitud; se puede elegir de +2 a +6 dB y está apagado de fábrica.
 *
 * Dónde se engancha (medido en el POCO F4: trae Dolby DAP, MiSound y el Loudness Enhancer de AOSP):
 *  - A cada reproductor que anuncia su sesión de audio (Spotify, YouTube Music, etc. mandan
 *    OPEN_AUDIO_EFFECT_CONTROL_SESSION, el mismo aviso que usan los ecualizadores).
 *  - Y, si el teléfono lo permite, a la mezcla general (sesión 0).
 * Va antes de la salida, así que también suena en audífonos y cascos Bluetooth. Apagarlo libera todo: no cambia nada
 * del sistema. El Dolby Atmos se prende o apaga en su pantalla del sistema (botón en Ajustes > Bluetooth y audio).
 */
object Sonoridad {
    val activo = MutableStateFlow(false)
    /** Ganancia en milibelios (100 mB = 1 dB). */
    val ganancia = MutableStateFlow(350)
    /** Sesiones a las que se enganchó (para mostrar). */
    val sesiones = MutableStateFlow(0)
    val global = MutableStateFlow(false)

    private var prefs: SharedPreferences? = null
    private val efectos = HashMap<Int, LoudnessEnhancer>()

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("sonoridad", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", false)
        ganancia.value = p.getInt("ganancia", 350).coerceIn(0, 600)
        if (activo.value) engancharGlobal()
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on; prefs?.edit()?.putBoolean("activo", on)?.apply()
        if (on) engancharGlobal() else soltarTodo()
    }

    fun ponerGanancia(mb: Int) {
        ganancia.value = mb.coerceIn(0, 600); prefs?.edit()?.putInt("ganancia", ganancia.value)?.apply()
        synchronized(efectos) { efectos.values.forEach { try { it.setTargetGain(ganancia.value) } catch (_: Exception) { } } }
    }

    private fun engancharGlobal() { global.value = enganchar(0) }

    /** true si quedó enganchado a esa sesión. */
    fun enganchar(sesion: Int): Boolean {
        if (!activo.value || sesion < 0) return false
        synchronized(efectos) {
            if (efectos.containsKey(sesion)) return true
            return try {
                val e = LoudnessEnhancer(sesion)
                e.setTargetGain(ganancia.value)
                e.enabled = true
                efectos[sesion] = e
                sesiones.value = efectos.size
                true
            } catch (_: Exception) { false }       // el sistema no dejó (p. ej. la sesión 0 en algunos teléfonos)
        }
    }

    fun soltar(sesion: Int) {
        synchronized(efectos) {
            efectos.remove(sesion)?.let { try { it.release() } catch (_: Exception) { } }
            sesiones.value = efectos.size
        }
        if (sesion == 0) global.value = false
    }

    private fun soltarTodo() {
        synchronized(efectos) {
            efectos.values.forEach { try { it.release() } catch (_: Exception) { } }
            efectos.clear()
            sesiones.value = 0
        }
        global.value = false
    }
}

/**
 * Recibe los avisos de los reproductores ("abrí / cerré mi sesión de audio") para engancharles la Sonoridad +.
 * Solo lee un número de sesión: no hace nada más con el aviso.
 */
class ReceptorSesionAudio : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val sesion = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sesion <= 0) return
        when (i.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                if (Sonoridad.activo.value || c.getSharedPreferences("sonoridad", Context.MODE_PRIVATE).getBoolean("activo", false)) {
                    if (!Sonoridad.activo.value) Sonoridad.iniciar(c)
                    Sonoridad.enganchar(sesion)
                }
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> Sonoridad.soltar(sesion)
        }
    }
}
