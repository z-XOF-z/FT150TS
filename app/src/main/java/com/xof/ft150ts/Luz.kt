package com.xof.ft150ts

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Luz del día real con el sensor de luz del teléfono (no con la hora): de noche o en un túnel
 * el mapa se pone en Noche. Con margen y 15 s de espera para que una sombra no lo cambie.
 */
object Luz {
    val lux = MutableStateFlow(-1f)
    /** null = no se sabe (sin sensor o sin lecturas todavía). */
    val oscuro = MutableStateFlow<Boolean?>(null)
    val usar = MutableStateFlow(true)
    val hay = MutableStateFlow(false)

    private var ctx: Context? = null
    private var sm: SensorManager? = null
    private var escuchando = false
    private var desde = 0L               // desde cuándo la luz pide cambiar
    private const val OSCURO_LUX = 8f
    private const val CLARO_LUX = 35f
    private const val ESPERA_MS = 15_000L

    fun iniciar(c: Context) {
        if (ctx != null) return
        val app = c.applicationContext
        ctx = app
        usar.value = app.getSharedPreferences("luz", Context.MODE_PRIVATE).getBoolean("usar", true)
        sm = sensores(app)
        hay.value = sm?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
    }

    fun ponerUsar(on: Boolean) {
        usar.value = on
        ctx?.getSharedPreferences("luz", Context.MODE_PRIVATE)?.edit()?.putBoolean("usar", on)?.apply()
    }

    /** Con la app en pantalla (el sensor de luz casi no gasta). */
    fun arrancar() {
        val s = sm ?: return
        if (escuchando) return
        val sensor = s.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        escuchando = s.registerListener(oyente, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun detener() {
        if (!escuchando) return
        sm?.unregisterListener(oyente)
        escuchando = false
    }

    private val oyente = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val l = e.values[0]
            lux.value = l
            val ahora = SystemClock.elapsedRealtime()
            val actual = oscuro.value
            if (actual == null) { oscuro.value = l < OSCURO_LUX * 2; return }
            val pide = if (actual) l > CLARO_LUX else l < OSCURO_LUX
            if (!pide) { desde = 0L; return }
            if (desde == 0L) desde = ahora
            else if (ahora - desde > ESPERA_MS) { oscuro.value = !actual; desde = 0L }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
