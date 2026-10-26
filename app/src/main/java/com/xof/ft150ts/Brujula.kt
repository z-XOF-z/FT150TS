package com.xof.ft150ts

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Brújula del teléfono (sensor de rotación: magnetómetro + giroscopio + acelerómetro).
 * Da hacia dónde apunta la parte de atrás del teléfono (= hacia donde mira la moto con el teléfono
 * en el soporte), corregida al norte verdadero. Sirve parado o despacio, cuando el GPS no sabe el rumbo.
 * Ojo: el metal y el imán de algunos soportes la pueden desviar unos grados.
 */
object Brujula {
    /** Grados (0 = norte verdadero), -1 = sin brújula. */
    val rumbo = MutableStateFlow(-1f)
    val hay = MutableStateFlow(false)
    /** Calibración del magnetómetro según Android (0 = mala ... 3 = buena). */
    val calidad = MutableStateFlow(3)
    /**
     * Rumbo fino (cada lectura, ~50 por segundo), -1 = sin brújula. No es un flujo a propósito: lo lee
     * el giro del mapa en cada cuadro, sin redibujar la pantalla con cada lectura. [rumbo] es el de los textos.
     */
    @Volatile var fino = -1f
        private set

    private var sm: SensorManager? = null
    private var usuarios = 0
    private val r = FloatArray(9)
    private var sx = 0f
    private var sy = 0f
    private var ultimaSalida = 0L
    private var declinacion = 0f
    private var ultimaDeclinacion = 0L

    fun iniciar(c: Context) {
        if (sm != null) return
        sm = sensores(c)
        hay.value = sensor() != null
    }

    private fun sensor(): Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sm?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    /** Cada pantalla que la usa la prende y la apaga (se queda prendida mientras alguien la use). */
    fun arrancar() {
        usuarios++
        if (usuarios > 1) return
        val s = sensor() ?: return
        // Ritmo de juego (~50 por segundo): el mapa gira suave sin escalones
        sm?.registerListener(oyente, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun detener() {
        if (usuarios == 0) return
        usuarios--
        if (usuarios > 0) return
        sm?.unregisterListener(oyente)
        rumbo.value = -1f
        fino = -1f
    }

    private val oyente = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(r, e.values)
            // Hacia donde apunta la parte de atrás del teléfono: −(tercera columna) en el mundo (x = este, y = norte)
            var este = -r[2]
            var norte = -r[5]
            // Teléfono casi acostado (pantalla hacia arriba): se usa la parte de arriba del teléfono
            if (sqrt(este * este + norte * norte) < 0.5f) { este = r[1]; norte = r[4] }
            val magnetico = atan2(este, norte)
            // Suavizado en círculo (para que no brinque de 359° a 0°). Ligero: el PID del mapa termina de suavizar
            sx += 0.2f * (sin(magnetico) - sx)
            sy += 0.2f * (cos(magnetico) - sy)
            val grados = ((atan2(sx, sy) * 180f / PI.toFloat()) + declinacion + 360f) % 360f
            fino = grados
            // Para los textos basta 5 veces por segundo
            val ahora = SystemClock.elapsedRealtime()
            if (ahora - ultimaSalida < 200) return
            ultimaSalida = ahora
            actualizarDeclinacion(ahora)
            if (rumbo.value < 0f || abs(GiroPid.diferencia(grados, rumbo.value)) > 1f) rumbo.value = grados
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { calidad.value = accuracy }
    }

    /** Norte magnético → verdadero (la diferencia depende del lugar; se recalcula cada 10 min). */
    private fun actualizarDeclinacion(ahora: Long) {
        if (ahora - ultimaDeclinacion < 600_000 && ultimaDeclinacion != 0L) return
        val p = UbicacionManager.posicionActual() ?: return
        ultimaDeclinacion = ahora
        declinacion = try {
            GeomagneticField(p.first.toFloat(), p.second.toFloat(), 0f, System.currentTimeMillis()).declination
        } catch (_: Exception) { 0f }
    }
}
