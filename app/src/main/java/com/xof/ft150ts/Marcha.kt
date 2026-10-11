package com.xof.ft150ts

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Marcha con un filtro de probabilidad (Bayes: como el Kalman de las RPM, pero para algo que va
 * por saltos, 1ª…5ª). Cada 1/4 de segundo junta tres cosas:
 *  1. **Velocidad**: a esta velocidad, ¿qué marchas son posibles? Cada marcha daría ciertas RPM;
 *     abajo de ~1800 el motor se jalonea y arriba de ~9800 no llega. Esto solo ya descarta "4ª a 17 km/h".
 *  2. **RPM ÷ velocidad**: qué tan cerca queda de la relación de cada marcha. Pesa más cuando la
 *     vibración es clara; con las RPM en ralentí rodando (embrague apretado) no cuenta.
 *  3. **Lo anterior**: la marcha no cambia sola; se pasa de una en una y toma su tiempo.
 * La marcha que se muestra solo cambia cuando la nueva ya es clara medio segundo seguido.
 */
class EstimadorMarcha(private val n: Int = 5) {
    private val p = FloatArray(n) { 1f / n }
    private val pred = FloatArray(n)
    private val pos = FloatArray(n)

    /** Marcha mostrada (1…n), 0 = parado, -1 = no se sabe. */
    var mostrada = -1
        private set
    /** Probabilidad (0–1) de la marcha mostrada. */
    var seguridad = 0f
        private set
    private var candidata = -1
    private var candidataS = 0f
    private var parado = true

    fun reiniciar() {
        arranque()
        mostrada = -1; seguridad = 0f; candidata = -1; candidataS = 0f; parado = true
    }

    /** Al arrancar casi siempre es en 1ª. */
    private fun arranque() {
        for (i in 0 until n) p[i] = when (i) { 0 -> 0.8f; 1 -> 0.15f; 2 -> 0.04f; else -> 0.01f / max(1, n - 3) }
        normalizar(p)
    }

    /**
     * [vel] km/h (< 0 = sin GPS), [rpm] filtradas (0 = no hay), [confianza] de la vibración,
     * [ratios] rpm por km/h de cada marcha, [dt] segundos desde la última vez.
     * Regresa la marcha a mostrar (1…n), 0 parado o -1.
     */
    fun actualizar(vel: Float, rpm: Float, confianza: Float, ratios: FloatArray, ralenti: Int, dt: Float): Int {
        if (vel < 0f) { seguridad = 0f; return -1 }     // sin GPS no se sabe (lo aprendido se queda para cuando vuelva)
        if (vel < 3f) {
            if (!parado) { arranque(); parado = true }
            mostrada = 0; seguridad = 1f; candidata = -1; candidataS = 0f
            return 0
        }
        if (parado) { parado = false; mostrada = -1 }

        // 1) Lo anterior: chance de pasar a la marcha de junto (≈ 15 % por segundo), casi nada de saltarse una
        val q1 = min(0.2f, 0.15f * dt)
        val q2 = q1 * 0.05f
        for (i in 0 until n) {
            var s = 0f
            if (i - 1 >= 0) s += p[i - 1] * q1
            if (i + 1 < n) s += p[i + 1] * q1
            if (i - 2 >= 0) s += p[i - 2] * q2
            if (i + 2 < n) s += p[i + 2] * q2
            val salen = (if (i - 1 >= 0) q1 else 0f) + (if (i + 1 < n) q1 else 0f) +
                (if (i - 2 >= 0) q2 else 0f) + (if (i + 2 < n) q2 else 0f)
            pred[i] = p[i] * (1f - salen) + s
        }

        // 2) Velocidad: marchas posibles. 3) RPM ÷ velocidad
        val minimo = rpmMinima(ralenti)
        val conRpm = rpm > 0f && rpm > ralenti + 350f && rpm > minimo - 200f
        val peso = if (conRpm) ((confianza - 1.8f) / 3f).coerceIn(0f, 1f) * 0.85f else 0f
        // Sin RPM útiles (embrague, vibración floja) solo se descarta lo imposible y se sostiene la que traías
        posibles(vel, ratios, ralenti, pos, soloImposible = peso < 0.15f)
        for (i in 0 until n) {
            var e = pos[i]
            if (peso > 0f) {
                val z = ln(rpm / (ratios[i] * vel)) / SIGMA
                e *= peso * exp(-0.5f * z * z) + (1f - peso)
            }
            p[i] = pred[i] * e
        }
        if (!normalizar(p)) arranqueSegun(pos)
        for (i in 0 until n) p[i] = max(p[i], 1e-4f)
        normalizar(p)

        // Lo que se muestra: solo cambia si la nueva es clara medio segundo seguido
        var mejor = 0
        for (i in 1 until n) if (p[i] > p[mejor]) mejor = i
        if (mejor + 1 == mostrada) {
            candidata = -1; candidataS = 0f
        } else {
            if (mejor == candidata) candidataS += dt else { candidata = mejor; candidataS = dt }
            val listo = if (mostrada <= 0) p[mejor] > 0.5f else (p[mejor] > 0.6f && candidataS >= 0.5f)
            if (listo) { mostrada = mejor + 1; candidata = -1; candidataS = 0f }
        }
        seguridad = if (mostrada in 1..n) p[mostrada - 1] else 0f
        return mostrada
    }

    /**
     * Qué tan posible es cada marcha a [vel] km/h sin ver las RPM (0–1, la más posible = 1).
     * Con [soloImposible] solo descarta las que no pueden ser (sin preferir las RPM de costumbre).
     */
    fun posibles(vel: Float, ratios: FloatArray, ralenti: Int, out: FloatArray, soloImposible: Boolean = false) {
        val lo = rpmMinima(ralenti)
        var m = 1e-6f
        for (i in 0 until n) {
            val r = ratios[i] * vel
            val cabe = sigmoide((r - lo) / 150f) * sigmoide((RPM_TOPE - r) / 300f)
            val zt = ln(r / RPM_TIPICA) / 0.55f
            val tipica = exp(-0.5f * zt * zt)
            out[i] = if (soloImposible) cabe else cabe * (0.5f + 0.5f * tipica)
            m = max(m, out[i])
        }
        for (i in 0 until n) out[i] /= m
    }

    private fun arranqueSegun(w: FloatArray) { for (i in 0 until n) p[i] = w[i]; normalizar(p) }

    companion object {
        /** Qué tan ancha es la tolerancia de RPM ÷ velocidad (en logaritmo: 0.07 ≈ ±7 %). */
        const val SIGMA = 0.07f
        const val RPM_TOPE = 9800f
        const val RPM_TIPICA = 4200f

        /** Abajo de esto, en marcha y rodando, el motor se jalonea. */
        fun rpmMinima(ralenti: Int) = max(1800f, ralenti + 300f)

        private fun sigmoide(x: Float) = 1f / (1f + exp(-x))

        private fun normalizar(a: FloatArray): Boolean {
            var s = 0f
            for (x in a) s += x
            if (s <= 1e-12f || s.isNaN()) return false
            for (i in a.indices) a[i] /= s
            return true
        }
    }
}
