package com.xof.ft150ts.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.xof.ft150ts.Apariencia

/**
 * "Tableros con mi color de acento" (v3.10): los tableros recreados de los conceptos (Racing Morado, Telemetría
 * Morada, Dual Neón) giran el tono de sus colores de identidad hacia el color de acento, conservando brillo,
 * saturación, degradados y contraste. Las alertas (rojo), el verde de "todo bien" y los textos blancos no pasan por aquí.
 * Apagado (por defecto) quedan exactos como los conceptos.
 *
 * Se llama muchas veces por cuadro: el giro se calcula una vez por acento y cada color se recuerda.
 */
internal object TinteTableros {
    private var acentoVisto = Long.MIN_VALUE
    private var tonoAcento = 0f
    private val memo = HashMap<Long, Int>()

    /** [c] con el tono girado de [base] (grados, el tono original del tablero) al del acento. */
    fun color(c: Color, base: Float): Color {
        if (!Apariencia.tablerosConAcento) return c
        val acento = Apariencia.colorAcento()
        if (acento != acentoVisto) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(acento.toInt(), hsv)
            tonoAcento = hsv[0]
            acentoVisto = acento
            memo.clear()
        }
        val clave = (c.toArgb().toLong() shl 12) or (base.toLong() and 0xFFF)
        val hecho = memo[clave]
        if (hecho != null) return Color(hecho)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[0] = ((hsv[0] + tonoAcento - base) % 360f + 360f) % 360f
        val res = android.graphics.Color.HSVToColor((c.alpha * 255).toInt(), hsv)
        memo[clave] = res
        return Color(res)
    }
}

/** Morados de los tableros morados (tono base del #C42CFF). */
internal fun tinteMorado(c: Color) = TinteTableros.color(c, 285f)
/** Cian / azul / morado del Dual Neón (tono base del #00DFFF). */
internal fun tinteCian(c: Color) = TinteTableros.color(c, 188f)
