package com.xof.ft150ts

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

enum class NivelCalor(val nombre: String) { NORMAL("Normal"), TIBIO("Tibio"), CALIENTE("Caliente"), MUY_CALIENTE("Muy caliente") }

/**
 * Teléfono caliente: en el soporte, con sol, la carga del cable y la pantalla prendida se calienta.
 * Mira la temperatura de la batería y el estado térmico de Android, avisa y, si quieres,
 * baja la carga de trabajo: 60 Hz en vez de 120, sin brillo forzado y pausa la dashcam.
 */
object Termico {
    val tempBateria = MutableStateFlow(-1f)      // °C
    val estadoSistema = MutableStateFlow(0)      // PowerManager.THERMAL_STATUS_* (0 = nada)
    val nivel = MutableStateFlow(NivelCalor.NORMAL)
    val proteger = MutableStateFlow(true)
    /** true = se están aplicando las medidas (60 Hz, sin brillo máximo, dashcam en pausa). */
    val protegiendo = MutableStateFlow(false)

    private var ctx: Context? = null
    private var ultimoAviso = 0L

    fun iniciar(c: Context) {
        if (ctx != null) return
        val app = c.applicationContext
        ctx = app
        proteger.value = app.getSharedPreferences("termico", Context.MODE_PRIVATE).getBoolean("proteger", true)
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                estadoSistema.value = pm.currentThermalStatus
                pm.addThermalStatusListener(ContextCompat.getMainExecutor(app)) { s ->
                    estadoSistema.value = s
                    recalcular()
                }
            } catch (_: Exception) { }
        }
    }

    fun ponerProteger(on: Boolean) {
        proteger.value = on
        ctx?.getSharedPreferences("termico", Context.MODE_PRIVATE)?.edit()?.putBoolean("proteger", on)?.apply()
        recalcular()
    }

    /** Lo llama Energia con cada ACTION_BATTERY_CHANGED (temperatura en décimas de °C). */
    fun bateria(decimas: Int) {
        if (decimas <= 0) return
        tempBateria.value = decimas / 10f
        recalcular()
    }

    private fun recalcular() {
        val t = tempBateria.value
        val antes = nivel.value
        // Con un grado de margen para que no brinque de un nivel a otro
        val porTemp = when {
            t < 0f -> NivelCalor.NORMAL
            t >= 46f || (antes == NivelCalor.MUY_CALIENTE && t >= 45f) -> NivelCalor.MUY_CALIENTE
            t >= 43f || (antes >= NivelCalor.CALIENTE && t >= 42f) -> NivelCalor.CALIENTE
            t >= 40f || (antes >= NivelCalor.TIBIO && t >= 39f) -> NivelCalor.TIBIO
            else -> NivelCalor.NORMAL
        }
        val porSistema = when (estadoSistema.value) {
            0 -> NivelCalor.NORMAL                 // NONE
            1 -> NivelCalor.TIBIO                  // LIGHT
            2 -> NivelCalor.CALIENTE               // MODERATE
            else -> NivelCalor.MUY_CALIENTE        // SEVERE, CRITICAL, EMERGENCY, SHUTDOWN
        }
        val n = maxOf(porTemp, porSistema)
        nivel.value = n
        protegiendo.value = proteger.value && n >= NivelCalor.CALIENTE
        val ahora = System.currentTimeMillis()
        if (n > antes && n >= NivelCalor.CALIENTE && ahora - ultimoAviso > 10 * 60_000L) {
            ultimoAviso = ahora
            val grados = if (t > 0) "${t.toInt()}C" else ""
            if (n == NivelCalor.MUY_CALIENTE) {
                Moto.aviso("Cel muy caliente $grados", "El teléfono está muy caliente. Ponlo a la sombra o desconecta la carga un rato")
            } else {
                Moto.aviso("Cel caliente $grados", "El teléfono se está calentando")
            }
        }
    }
}
