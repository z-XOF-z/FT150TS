package com.xof.ft150ts

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Parte pura del volumen progresivo (sin Android, se prueba en VolumenTest). Sube el volumen de la música con la
 * velocidad para tapar el viento del casco, sin brincos:
 *  - Entre [Ajustes.velIni] y [Ajustes.velMax] el volumen va de [Ajustes.volMin] a [Ajustes.volMax] (en % del máximo).
 *  - Histéresis: la velocidad que manda solo se mueve si cambió más de [Ajustes.histeresis] km/h (los altibajos del
 *    GPS no mueven el volumen).
 *  - Suave: un nivel a la vez y como mucho uno cada [Ajustes.pasoMs] ms.
 *  - Parado (< 3 km/h por [Ajustes.paradoMs]): según [Ajustes.alDetenerse] mantiene, baja al mínimo o regresa a tu volumen.
 */
enum class AlDetenerse { MANTENER, MINIMO, ORIGINAL }

data class AjustesVolumen(
    val velIni: Float = 20f,
    val velMax: Float = 90f,
    val volMin: Int = 45,          // % del volumen máximo del sistema
    val volMax: Int = 90,
    val histeresis: Float = 4f,
    val pasoMs: Long = 1_500L,
    val paradoMs: Long = 8_000L,
    val alDetenerse: AlDetenerse = AlDetenerse.MANTENER,
)

class VolumenProgresivo(private val nivelesMax: Int) {
    var ajustes = AjustesVolumen()
    /** Velocidad que manda (con histéresis). */
    private var velRef = -1f
    private var ultimoPaso = 0L
    private var paradoDesde = -1L

    /** Volumen (nivel 0..[nivelesMax]) que corresponde a una velocidad, sin suavizar. */
    fun nivelPara(vel: Float): Int {
        val a = ajustes
        val t = if (a.velMax <= a.velIni) 1f else ((vel - a.velIni) / (a.velMax - a.velIni)).coerceIn(0f, 1f)
        val pct = a.volMin + (a.volMax - a.volMin) * t
        return (pct / 100f * nivelesMax).roundToInt().coerceIn(0, nivelesMax)
    }

    /**
     * Siguiente nivel a poner, o null si no hay que tocar nada. [vel] en km/h (confiable; < 0 = sin dato),
     * [actual] el nivel que tiene ahorita el sistema, [original] el que tenías al activar la función.
     */
    fun paso(ahoraMs: Long, vel: Float, actual: Int, original: Int): Int? {
        if (vel < 0f) return null                                  // sin velocidad confiable: no se toca nada
        val a = ajustes
        // Parado
        if (vel < 3f) {
            if (paradoDesde < 0) paradoDesde = ahoraMs
            if (ahoraMs - paradoDesde < a.paradoMs) return null
            val meta = when (a.alDetenerse) {
                AlDetenerse.MANTENER -> return null
                AlDetenerse.MINIMO -> nivelPara(0f)
                AlDetenerse.ORIGINAL -> original
            }
            velRef = 0f
            return acercar(ahoraMs, actual, meta)
        }
        paradoDesde = -1L
        // Histéresis
        if (velRef < 0f || abs(vel - velRef) > a.histeresis) velRef = vel
        return acercar(ahoraMs, actual, nivelPara(velRef))
    }

    private fun acercar(ahoraMs: Long, actual: Int, meta: Int): Int? {
        if (meta == actual) return null
        if (ahoraMs - ultimoPaso < ajustes.pasoMs) return null
        ultimoPaso = ahoraMs
        return if (meta > actual) actual + 1 else actual - 1
    }

    fun reiniciar() { velRef = -1f; ultimoPaso = 0L; paradoDesde = -1L }
}
