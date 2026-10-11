package com.xof.ft150ts

import kotlin.math.abs
import kotlin.math.max

/**
 * Controlador PID clásico.
 * - Derivada sobre la medición (no da un "patadón" cuando cambia el objetivo de golpe).
 * - Anti-windup: deja de acumular la integral mientras la salida está saturada.
 */
class ControlPid(
    var kp: Float,
    var ki: Float,
    var kd: Float,
    var salidaMin: Float = -Float.MAX_VALUE,
    var salidaMax: Float = Float.MAX_VALUE,
) {
    private var integral = 0f
    private var medicionAnt = Float.NaN

    fun reiniciar() { integral = 0f; medicionAnt = Float.NaN }

    fun paso(objetivo: Float, medicion: Float, dt: Float): Float {
        val error = objetivo - medicion
        val derivada = if (medicionAnt.isNaN() || dt <= 0f) 0f else -(medicion - medicionAnt) / dt
        medicionAnt = medicion
        val tentativa = integral + error * dt
        var u = kp * error + ki * tentativa + kd * derivada
        when {
            u > salidaMax -> { u = salidaMax; if (error < 0) integral = tentativa }
            u < salidaMin -> { u = salidaMin; if (error > 0) integral = tentativa }
            else -> integral = tentativa
        }
        return u
    }
}

/**
 * Aguja con inercia controlada por PID, como la de un tacómetro de verdad:
 * el PID calcula la "fuerza" del motorcito, la aguja tiene masa (1) y fricción.
 * Ecuación: a = u − fricción·v  →  con PD queda x'' + (fricción + Kd)·x' + Kp·x = Kp·objetivo.
 * Frecuencia natural = √Kp, amortiguamiento = (fricción + Kd) / (2·√Kp).
 */
class AgujaPid(kp: Float = 400f, ki: Float = 10f, kd: Float = 35f) {
    val pid = ControlPid(kp, ki, kd, -FUERZA_MAX, FUERZA_MAX)
    var posicion = 0f
        private set
    var velocidad = 0f
        private set

    fun colocar(valor: Float) { posicion = valor; velocidad = 0f; pid.reiniciar() }

    /** Un paso de simulación de [dt] segundos (Euler semi-implícito: estable a 30–60 Hz). */
    fun paso(objetivo: Float, dt: Float): Float {
        val u = pid.paso(objetivo, posicion, dt)
        val a = u - FRICCION * velocidad
        velocidad += a * dt
        posicion += velocidad * dt
        if (posicion < 0f) { posicion = 0f; velocidad = max(0f, velocidad) }
        return posicion
    }

    fun quieta(objetivo: Float) = abs(objetivo - posicion) < 4f && abs(velocidad) < 40f

    companion object {
        const val FRICCION = 2f
        const val FUERZA_MAX = 400_000f     // rpm/s²: lo más fuerte que empuja el "motorcito"

        /** Respuesta a un escalón (para la gráfica de Ajustes): lista de posiciones cada [dt]. */
        fun escalon(kp: Float, ki: Float, kd: Float, desde: Float, hasta: Float, segundos: Float = 1.5f, dt: Float = 1f / 60f): List<Float> {
            val a = AgujaPid(kp, ki, kd)
            a.colocar(desde)
            val n = (segundos / dt).toInt()
            return List(n) { a.paso(hasta, dt) }
        }

        /** Sobrepaso (%) y tiempo de asentamiento (s, banda del 3 %) de una respuesta. */
        fun metricas(r: List<Float>, desde: Float, hasta: Float, dt: Float = 1f / 60f): Pair<Float, Float> {
            val salto = hasta - desde
            if (salto == 0f || r.isEmpty()) return 0f to 0f
            val pico = if (salto > 0) r.max() else r.min()
            val sobrepaso = ((pico - hasta) / salto * 100f).coerceAtLeast(0f)
            var ultimoFuera = -1
            r.forEachIndexed { i, x -> if (abs(x - hasta) > abs(salto) * 0.03f) ultimoFuera = i }
            return sobrepaso to (ultimoFuera + 1) * dt
        }
    }
}

/**
 * El mismo PID con masa, pero para ángulos (0–360°): siempre gira por el camino corto
 * (de 350° a 10° son 20°, no 340°). Lo usa el mapa para girar suave con la brújula o el GPS.
 * De fábrica: Kp 150, Kd 22.5 → amortiguamiento ≈ 1 (un giro de 90° llega en ~0.45 s sin pasarse ni rebotar).
 */
class GiroPid(kp: Float = 150f, ki: Float = 0f, kd: Float = 22.5f) {
    private val pid = ControlPid(kp, ki, kd, -30_000f, 30_000f)
    /** Ángulo sin "dar la vuelta" (puede pasar de 360 o ser negativo: así no brinca). */
    private var angulo = 0f
    var velocidad = 0f
        private set
    private var iniciado = false

    fun grados() = ((angulo % 360f) + 360f) % 360f

    fun colocar(g: Float) { angulo = g; velocidad = 0f; pid.reiniciar(); iniciado = true }

    /** Un paso de [dt] s hacia [objetivo] grados; regresa los grados actuales (0–360). */
    fun paso(objetivo: Float, dt: Float): Float {
        if (!iniciado) { colocar(objetivo); return grados() }
        val o = angulo + diferencia(objetivo, grados())
        val u = pid.paso(o, angulo, dt)
        val a = u - AgujaPid.FRICCION * velocidad
        velocidad += a * dt
        angulo += velocidad * dt
        // que el número no crezca sin fin
        if (abs(angulo) > 3600f) { val g = grados(); angulo = g; pid.reiniciar() }
        return grados()
    }

    /** Ya llegó: a menos de medio grado y casi sin moverse. */
    fun quieta(objetivo: Float) = abs(diferencia(objetivo, grados())) < 0.5f && abs(velocidad) < 3f

    companion object {
        /** Diferencia más corta de [b] a [a] en grados (−180…180). */
        fun diferencia(a: Float, b: Float): Float {
            var d = (a - b) % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }
    }
}
