package com.xof.ft150ts

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Línea fuerte en el espectro de vibración. [fuerza] = suma armónica (más alta = más clara). */
class Pico(val frecuencia: Float, val fuerza: Float)

/** Resultado de analizar una ventana de vibración. */
class Analisis(
    val fs: Float,              // muestras por segundo reales del acelerómetro
    val rms: Float,             // vibración (m/s²) entre 10 y 250 Hz: dice si el motor está prendido
    val picos: List<Pico>,      // de la más fuerte a la más débil
    val confianza: Float,       // qué tanto sobresale la línea principal sobre el ruido (>3 = clara)
    val amplitud: FloatArray = FloatArray(0),   // espectro (amplitud por renglón de frecuencia)
    val df: Float = 1f,         // Hz por renglón
    val fMax: Float = 0f,       // frecuencia más alta que se puede ver (por debajo de Nyquist)
) {
    /** Amplitud cerca de [f] (toma el máximo de ±1 renglón), 0..1 respecto a la línea más fuerte. */
    fun nivel(f: Float, maxAmp: Float): Float {
        val k = (f / df + 0.5f).toInt()
        if (k < 1 || k >= amplitud.size - 1 || f > fMax) return 0f
        return max(amplitud[k], max(amplitud[k - 1], amplitud[k + 1])) / maxAmp
    }
}

/**
 * Calcula las RPM con la vibración del motor que siente el acelerómetro del teléfono.
 *
 * Un motor de un cilindro vibra a la frecuencia del cigüeñal (RPM / 60) y a sus múltiplos.
 * Se saca el espectro (FFT) de los 3 ejes, se busca la línea más fuerte sumando sus armónicos
 * y se convierte a RPM con el "orden" que se aprende al calibrar en ralentí
 * (1 = vibra a la frecuencia del cigüeñal, 2 = al doble, 0.5 = a la mitad...).
 * Con la velocidad del GPS se elige entre las líneas la que cuadra con alguna marcha.
 *
 * No usa nada de Android: así se puede probar en la compu con vibración simulada.
 */
class EstimadorRpm(private val fMin: Float = 6f, private val fMaxAbs: Float = 250f) {
    /** Promedio del espectro con el anterior (0 = sin promedio). Quita ruido de baches y viento. */
    var promedio = 0.5f
    private var potAnt = FloatArray(0)

    private var tam = 0
    private var re = FloatArray(0)
    private var im = FloatArray(0)
    private var pot = FloatArray(0)
    private var ventana = FloatArray(0)
    private var sumaW2 = 1f

    private fun preparar(n: Int) {
        var m = 1
        while (m < n * 2) m = m shl 1          // relleno con ceros: más resolución en la frecuencia
        m = min(m, 8192)
        if (m != tam) {
            tam = m
            re = FloatArray(m); im = FloatArray(m); pot = FloatArray(m / 2 + 1)
        }
        if (ventana.size != n) {
            ventana = FloatArray(n) { i -> (0.5 - 0.5 * cos(2 * PI * i / (n - 1))).toFloat() }   // Hann
            sumaW2 = ventana.fold(0f) { a, w -> a + w * w }
        }
    }

    /** [x], [y], [z]: las últimas [n] muestras de cada eje (m/s²), tomadas a [fs] por segundo. */
    fun analizar(x: FloatArray, y: FloatArray, z: FloatArray, n: Int, fs: Float): Analisis {
        preparar(n)
        pot.fill(0f)
        for (eje in 0 until 3) {
            val s = when (eje) { 0 -> x; 1 -> y; else -> z }
            var media = 0f
            for (i in 0 until n) media += s[i]
            media /= n
            for (i in 0 until tam) {
                re[i] = if (i < n) (s[i] - media) * ventana[i] else 0f
                im[i] = 0f
            }
            fft(re, im, tam)
            for (k in 0..tam / 2) pot[k] += re[k] * re[k] + im[k] * im[k]
        }
        // Mezcla con el espectro anterior: las líneas del motor se refuerzan, el ruido se promedia
        if (promedio > 0f && potAnt.size == pot.size) {
            for (k in pot.indices) pot[k] = (1 - promedio) * pot[k] + promedio * potAnt[k]
        }
        if (potAnt.size != pot.size) potAnt = FloatArray(pot.size)
        System.arraycopy(pot, 0, potAnt, 0, pot.size)
        val df = fs / tam
        val nyq = fs / 2f
        // Vibración en la banda del motor (Parseval, de un solo lado)
        val kBajo = max(1, (10f / df).toInt())
        val kAlto = min(tam / 2 - 1, (min(fMaxAbs, nyq * 0.95f) / df).toInt())
        var suma = 0.0
        for (k in kBajo..kAlto) suma += pot[k]
        val rms = sqrt((2.0 / tam) * suma / sumaW2).toFloat()

        // Suma armónica: la línea + medio de su doble + un tercio de su triple
        val amp = FloatArray(tam / 2 + 1) { sqrt(pot[it]) }
        fun a(j: Int): Float {
            if (j < 1 || j >= tam / 2) return 0f
            return max(amp[j], max(amp[j - 1], amp[j + 1]))
        }
        val k0 = max(2, (fMin / df).toInt())
        val k1 = min(tam / 2 - 2, (min(fMaxAbs, nyq * 0.95f) / df).toInt())
        if (k1 <= k0 + 2) return Analisis(fs, rms, emptyList(), 0f, amp, df, k1 * df)
        val s = FloatArray(k1 - k0 + 1) { i -> val k = k0 + i; amp[k] + 0.5f * a(2 * k) + 0.33f * a(3 * k) }

        // Picos locales, de mayor a menor, sin repetir vecinos
        val candidatos = ArrayList<Int>()
        for (i in 1 until s.size - 1) if (s[i] >= s[i - 1] && s[i] > s[i + 1]) candidatos += i
        candidatos.sortByDescending { s[it] }
        val elegidos = ArrayList<Int>()
        for (c in candidatos) {
            if (elegidos.none { abs(it - c) <= 3 }) elegidos += c
            if (elegidos.size == 5) break
        }
        val picos = elegidos.map { i ->
            val d = if (i in 1 until s.size - 1) {
                val den = s[i - 1] - 2 * s[i] + s[i + 1]
                if (den != 0f) (0.5f * (s[i - 1] - s[i + 1]) / den).coerceIn(-0.5f, 0.5f) else 0f
            } else 0f
            Pico((k0 + i + d) * df, s[i])
        }
        val ordenado = s.sortedArray()
        val mediana = ordenado[ordenado.size / 2].coerceAtLeast(1e-6f)
        val conf = if (picos.isEmpty()) 0f else picos[0].fuerza / mediana
        return Analisis(fs, rms, picos, conf, amp, df, k1 * df)
    }

    /**
     * Elige las RPM entre las líneas del espectro.
     * [orden]: frecuencia de la línea principal ÷ (RPM / 60). [velKmh] < 0 = sin GPS.
     * [ratios]: rpm por km/h de cada marcha. [previo]: RPM anteriores (0 = no hay).
     * [posibles]: qué tan posible es cada marcha a esta velocidad (0–1), de [EstimadorMarcha.posibles].
     * Regresa (RPM, marcha): marcha 0 = parado, -1 = no se sabe.
     */
    fun elegir(
        an: Analisis, orden: Float, velKmh: Float, ratios: FloatArray, ralenti: Int, previo: Float,
        rpmMin: Float = 900f, rpmMax: Float = 11000f,
        posibles: FloatArray? = null,
    ): Pair<Float, Int> {
        if (an.picos.isEmpty()) return 0f to -1
        // Candidatos: cada línea fuerte puede ser cualquiera de los múltiplos del cigüeñal
        val ordenes = ORDENES
        var maxAmp = 1e-6f
        for (k in an.amplitud.indices) if (k * an.df >= fMin && k * an.df <= an.fMax) maxAmp = max(maxAmp, an.amplitud[k])
        var mejor = -1f
        var mejorPuntos = -99f
        for (p in an.picos) for (h in ordenes) {
            val rpm = p.frecuencia * 60f / h
            if (rpm < rpmMin || rpm > rpmMax) continue
            // "Peine": qué tanto se ven en el espectro todas las líneas que daría el motor a estas RPM.
            // La línea que se aprendió al calibrar pesa el doble.
            var suma = 0f
            var pesos = 0f
            for (o in ordenes) {
                val f = o * rpm / 60f
                if (f < fMin || f > an.fMax) continue
                val w = if (abs(o - orden) < 0.01f) 1f else 0.5f
                suma += w * an.nivel(f, maxAmp)
                pesos += w
            }
            if (pesos == 0f) continue
            var puntos = suma / pesos
            if (previo > 0f) puntos -= 0.25f * min(1f, abs(rpm - previo) / max(previo, 1000f) / 0.8f)
            if (velKmh > 8f) {
                // Cuadra con una marcha... pero solo cuenta si esa marcha es posible a esta velocidad
                // (a 17 km/h, "4ª a 1560 rpm" no lo es: eso es ralentí o embrague)
                val (g, ajuste) = ajusteMarcha(rpm, velKmh, ratios)
                val w = posibles?.getOrNull(g) ?: 1f
                puntos += if (ajuste < 0.15f) 0.25f * (1f - ajuste / 0.15f) * w - 0.08f * (1f - w) else -0.08f
            } else if (velKmh in 0f..3f && abs(rpm - ralenti) < 500f) {
                puntos += 0.1f
            }
            if (puntos > mejorPuntos) { mejorPuntos = puntos; mejor = rpm }
        }
        if (mejor < 0f) return 0f to -1
        val marcha = when {
            velKmh in 0f..3f -> 0
            velKmh > 3f -> ajusteMarcha(mejor, velKmh, ratios).let { (g, e) -> if (e < 0.15f) g + 1 else -1 }
            else -> -1
        }
        return mejor to marcha
    }

    /** Marcha (índice) cuya relación rpm/km/h queda más cerca, y qué tan lejos (fracción). */
    fun ajusteMarcha(rpm: Float, velKmh: Float, ratios: FloatArray): Pair<Int, Float> {
        val q = rpm / velKmh
        var g = 0
        var e = Float.MAX_VALUE
        for (i in ratios.indices) {
            val d = abs(q - ratios[i]) / ratios[i]
            if (d < e) { e = d; g = i }
        }
        return g to e
    }

    companion object {
        /** Múltiplos de la frecuencia del cigüeñal que vibran en un motor de un cilindro. */
        val ORDENES = floatArrayOf(0.5f, 1f, 1.5f, 2f, 3f)

        /** FFT de base 2 en el lugar ([n] potencia de 2). */
        fun fft(re: FloatArray, im: FloatArray, n: Int) {
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var len = 2
            while (len <= n) {
                val ang = -2.0 * PI / len
                val wr = cos(ang).toFloat()
                val wi = sin(ang).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f
                    var ci = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k
                        val b = a + len / 2
                        val xr = re[b] * cr - im[b] * ci
                        val xi = re[b] * ci + im[b] * cr
                        re[b] = re[a] - xr; im[b] = im[a] - xi
                        re[a] += xr; im[a] += xi
                        val nr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = nr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}

/**
 * Filtro de Kalman para las RPM: sigue las RPM y qué tan rápido suben o bajan, le cree más a las
 * lecturas claras que a las dudosas y no hace caso a un brinco suelto (mediana de 3 + rechazo de
 * lecturas a más de ~3.5 sigmas). Si el brinco se repite parejo 3 veces (un cambio de marcha), lo acepta.
 * [suavizado]: 0 rápido, 1 normal, 2 suave.
 */
class FiltroRpm(var suavizado: Int = 1) {
    private var x = 0f            // rpm
    private var v = 0f            // rpm por segundo
    private var p00 = 0f
    private var p01 = 0f
    private var p11 = 0f
    private var rechazos = 0
    private var candidato = 0f
    private val ultimas = FloatArray(3)
    private var n = 0

    val valor: Float get() = x

    fun reiniciar() { x = 0f; v = 0f; rechazos = 0; n = 0 }

    private fun mediana3(a: FloatArray): Float {
        val x0 = a[0]; val x1 = a[1]; val x2 = a[2]
        return max(min(x0, x1), min(max(x0, x1), x2))
    }

    private fun reset(z: Float, rr: Float) { x = z; v = 0f; p00 = rr; p01 = 0f; p11 = 2000f * 2000f; rechazos = 0 }

    /** [z] lectura (rpm), [conf] claridad del espectro, [dt] segundos desde la anterior. */
    fun actualizar(z: Float, conf: Float, dt: Float): Float {
        ultimas[n % 3] = z
        n++
        val zm = if (n >= 3) mediana3(ultimas) else z
        val sigma = (900f / conf.coerceAtLeast(1.2f)).coerceIn(50f, 700f)
        val rr = sigma * sigma
        if (x <= 0f) { reset(zm, rr); return x }
        // Predicción
        val q = when (suavizado) { 0 -> 6000f; 2 -> 1500f; else -> 3000f }   // qué tanto pueden cambiar (rpm/s²)
        x += v * dt
        v = (v * 0.85f).coerceIn(-8000f, 6000f)
        val dt2 = dt * dt
        p00 += dt * (2 * p01 + dt * p11) + q * q * dt2 * dt2 / 4
        p01 += dt * p11 + q * q * dt2 * dt / 2
        p11 += q * q * dt2
        // Corrección (con rechazo de brincos)
        val s = p00 + rr
        val innov = zm - x
        if (innov * innov > 12f * s) {
            // Lecturas "raras" pero parejas entre sí (subiendo o bajando de a poco) = de verdad cambió
            rechazos = if (rechazos > 0 && abs(zm - candidato) < 0.2f * candidato) rechazos + 1 else 1
            candidato = zm
            if (rechazos >= 3) reset(zm, rr)          // p. ej. cambio de marcha o acelerón fuerte
            return x
        }
        rechazos = 0
        val k0 = p00 / s
        val k1 = p01 / s
        x += k0 * innov
        v += k1 * innov
        val n00 = (1 - k0) * p00
        val n01 = (1 - k0) * p01
        val n11 = p11 - k1 * p01
        p00 = n00; p01 = n01; p11 = n11
        if (x < 0f) x = 0f
        return x
    }
}
