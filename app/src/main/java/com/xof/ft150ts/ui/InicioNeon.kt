package com.xof.ft150ts.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.EstiloTablero
import com.xof.ft150ts.TemaApp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pantallas de inicio neón (v3.10), de los conceptos de Andrew:
 *  1 Potencia: "FT150TS" con barrido, "POTENCIA EN CADA KILÓMETRO", rayos de luz en diagonal y barra de carga.
 *  2 Ride mode: casco en neón, "RIDE MODE", cuatro íconos que se van encendiendo (casco, ajustes, Bluetooth, teléfono)
 *    y "CONECTANDO…".
 *  3 Sistema: anillos de radar con un barrido, el logo al centro, "///" y "CONEXIÓN CON TU MOTO" con puntos de carga.
 * Morada con el tema o el tablero Racing Morado; verde con los demás.
 * Todo en un Canvas; dura lo mismo que la clásica (la controla PantallaInicio) y se salta tocando.
 */
@Composable
internal fun InicioNeon(variante: Int, ms: Float, version: String) {
    val pal = if (Apariencia.tema == TemaApp.RACING || Apariencia.estiloTablero == EstiloTablero.MORADO) NEON_MORADO else NEON_VERDE
    val medidor = rememberTextMeasurer(cacheSize = 24)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF020304))
        when (variante) {
            1 -> potencia(ms, pal, medidor)
            2 -> rideMode(ms, pal, medidor)
            else -> sistema(ms, pal, medidor)
        }
        val v = medidor.measure("v$version", estiloI(size.minDimension * 0.022f, Color(0xFF6B7480), Fuentes.pixel))
        drawText(v, topLeft = Offset((size.width - v.size.width) / 2f, size.height - v.size.height * 2.6f), alpha = f(ms, 1500f, 500f))
    }
}

/** Colores de las pantallas de inicio: principal, secundario, tercero y alerta. */
internal class PaletaNeon(val principal: Color, val secundario: Color, val tercero: Color, val alerta: Color)

internal val NEON_VERDE = PaletaNeon(Color(0xFF39FF14), Color(0xFF9BFF5A), Color(0xFF14C85A), Color(0xFFFF3B3B))
internal val NEON_MORADO = PaletaNeon(Color(0xFFC42CFF), Color(0xFFFF45E8), Color(0xFF8D37FF), Color(0xFFFF3B5C))

private fun f(t: Float, ini: Float, dur: Float, suave: Boolean = true): Float {
    val x = ((t - ini) / dur).coerceIn(0f, 1f)
    return if (suave) FastOutSlowInEasing.transform(x) else x
}

private fun DrawScope.estiloI(px: Float, color: Color, fuente: FontFamily = Fuentes.ui, peso: FontWeight = FontWeight.SemiBold, espacio: Float = 0f) =
    TextStyle(color = color, fontSize = px.toSp(), fontFamily = fuente, fontWeight = peso, letterSpacing = espacio.sp)

private fun DrawScope.textoCentrado(m: TextMeasurer, t: String, y: Float, estilo: TextStyle, alfa: Float = 1f) {
    val r = m.measure(t, estilo)
    drawText(r, topLeft = Offset((size.width - r.size.width) / 2f, y - r.size.height / 2f), alpha = alfa.coerceIn(0f, 1f))
}

/** "FT150" blanco + "TS" del color, inclinado como el logo de la moto, con barrido de izquierda a derecha. */
private fun DrawScope.logo(m: TextMeasurer, c: Offset, alto: Float, pal: PaletaNeon, barrido: Float) {
    if (barrido <= 0f) return
    val ft = m.measure("FT150", TextStyle(color = Color.White, fontSize = alto.toSp(), fontFamily = Fuentes.logo))
    val ts = m.measure("TS", TextStyle(color = pal.principal, fontSize = alto.toSp(), fontFamily = Fuentes.logo))
    val ancho = ft.size.width + ts.size.width
    val x0 = c.x - ancho / 2f
    val y0 = c.y - ft.size.height / 2f
    clipRect(right = x0 + ancho * barrido + alto * 0.3f) {
        val lienzo = drawContext.canvas
        lienzo.save()
        lienzo.translate(c.x, c.y); lienzo.skew(-0.22f, 0f); lienzo.translate(-c.x, -c.y)
        // halo del TS
        drawRect(Brush.radialGradient(listOf(pal.principal.copy(alpha = 0.25f), Color.Transparent),
            center = Offset(x0 + ft.size.width + ts.size.width / 2f, c.y), radius = alto),
            topLeft = Offset(x0 + ft.size.width - alto, c.y - alto), size = Size(ts.size.width + alto * 2, alto * 2))
        drawText(ft, topLeft = Offset(x0, y0))
        drawText(ts, topLeft = Offset(x0 + ft.size.width, y0))
        lienzo.restore()
    }
}

/** Barra de carga neón. */
private fun DrawScope.barra(r: Rect, p: Float, pal: PaletaNeon) {
    val rad = CornerRadius(r.height / 2f)
    drawRoundRect(Color.White.copy(alpha = 0.08f), r.topLeft, r.size, rad)
    val w = r.width * p.coerceIn(0f, 1f)
    if (w <= 1f) return
    drawRoundRect(pal.principal.copy(alpha = 0.18f), Offset(r.left, r.top - r.height), Size(w, r.height * 3f), CornerRadius(r.height * 1.5f))
    drawRoundRect(Brush.horizontalGradient(listOf(pal.principal, pal.secundario), r.left, r.left + r.width), r.topLeft, Size(w, r.height), rad)
}

private val ESTADOS_N = listOf("INICIANDO SISTEMA", "BLUETOOTH", "GPS", "SENSORES", "LISTO")

// ------------------------------------------------------------
// 1 · Potencia
// ------------------------------------------------------------
private fun DrawScope.potencia(ms: Float, pal: PaletaNeon, m: TextMeasurer) {
    val w = size.width; val h = size.height
    val u = min(w, h * 1.8f) / 100f
    // Rayos de luz en diagonal (entran desde las esquinas)
    val pr = f(ms, 0f, 900f)
    val rayos = listOf(0.08f to 0.9f, 0.16f to 0.6f, 0.82f to 0.75f, 0.90f to 0.5f)
    rayos.forEachIndexed { i, (fx, alfa) ->
        val desde = if (i < 2) Offset(-w * 0.2f, h * (0.95f - i * 0.12f)) else Offset(w * 1.2f, h * (0.05f + (i - 2) * 0.12f))
        val hacia = Offset(w * fx, h * if (i < 2) 0.55f - i * 0.1f else 0.45f + (i - 2) * 0.1f)
        val fin = Offset(desde.x + (hacia.x - desde.x) * pr, desde.y + (hacia.y - desde.y) * pr)
        drawLine(pal.principal.copy(alpha = 0.10f * alfa), desde, fin, u * 3.2f, StrokeCap.Round)
        drawLine(pal.principal.copy(alpha = 0.7f * alfa), desde, fin, u * 0.5f, StrokeCap.Round)
    }
    // Marco: esquinas en ángulo
    val pm = f(ms, 300f, 700f)
    val e = u * 6f * pm
    val mg = u * 3f
    listOf(Offset(mg, mg) to Offset(1f, 1f), Offset(w - mg, mg) to Offset(-1f, 1f), Offset(mg, h - mg) to Offset(1f, -1f), Offset(w - mg, h - mg) to Offset(-1f, -1f))
        .forEach { (o, d) ->
            drawLine(pal.secundario.copy(alpha = 0.6f), o, Offset(o.x + e * d.x, o.y), u * 0.35f)
            drawLine(pal.secundario.copy(alpha = 0.6f), o, Offset(o.x, o.y + e * d.y), u * 0.35f)
        }
    textoCentrado(m, "ITALIKA", h * 0.25f, estiloI(u * 2.4f, Color.White, peso = FontWeight.Bold, espacio = 8f), f(ms, 700f, 500f))
    logo(m, Offset(w / 2f, h * 0.43f), u * 9.5f, pal, f(ms, 450f, 700f))
    textoCentrado(m, "POTENCIA EN CADA KILÓMETRO", h * 0.585f, estiloI(u * 1.9f, Color(0xFFD9DEE4), espacio = 4f), f(ms, 1150f, 450f))
    val pc = f(ms, 1300f, 1700f, suave = false)
    barra(Rect(w * 0.32f, h * 0.73f, w * 0.68f, h * 0.73f + u * 0.9f), pc, pal)
    textoCentrado(m, "CARGANDO...", h * 0.80f, estiloI(u * 1.5f, Color(0xFFAEB6C0), espacio = 3f), f(ms, 1300f, 300f))
}

// ------------------------------------------------------------
// 2 · Ride mode
// ------------------------------------------------------------
private fun DrawScope.rideMode(ms: Float, pal: PaletaNeon, m: TextMeasurer) {
    val w = size.width; val h = size.height
    val u = min(w, h * 1.8f) / 100f
    val c = Offset(w / 2f, h * 0.30f)
    val r = h * 0.17f
    val pc = f(ms, 100f, 1000f)
    // Casco: concha, visera y mentonera, dibujados con "trazo" que avanza
    val casco = Path().apply {
        moveTo(c.x - r * 0.95f, c.y + r * 0.75f)
        cubicTo(c.x - r * 1.25f, c.y - r * 0.9f, c.x + r * 1.25f, c.y - r * 0.9f, c.x + r * 0.95f, c.y + r * 0.75f)
        lineTo(c.x + r * 0.55f, c.y + r * 1.05f)
        lineTo(c.x - r * 0.55f, c.y + r * 1.05f)
        close()
    }
    val visera = Path().apply {
        moveTo(c.x - r * 0.80f, c.y + r * 0.05f)
        quadraticTo(c.x, c.y - r * 0.25f, c.x + r * 0.80f, c.y + r * 0.05f)
        lineTo(c.x + r * 0.62f, c.y + r * 0.50f)
        quadraticTo(c.x, c.y + r * 0.62f, c.x - r * 0.62f, c.y + r * 0.50f)
        close()
    }
    val alfa = pc
    drawPath(casco, pal.principal.copy(alpha = 0.10f * alfa), style = Stroke(u * 2.4f))
    drawPath(casco, pal.principal.copy(alpha = alfa), style = Stroke(u * 0.45f))
    drawPath(visera, pal.secundario.copy(alpha = 0.12f * alfa), style = Stroke(u * 2f))
    drawPath(visera, pal.secundario.copy(alpha = alfa), style = Stroke(u * 0.35f))
    // Rayas laterales del casco
    for (k in 0..2) drawLine(pal.principal.copy(alpha = 0.6f * alfa), Offset(c.x + r * (0.35f + k * 0.18f), c.y - r * 0.55f + k * r * 0.08f),
        Offset(c.x + r * (0.55f + k * 0.18f), c.y - r * 0.15f + k * r * 0.08f), u * 0.3f)
    textoCentrado(m, "R I D E   M O D E", h * 0.585f, estiloI(u * 2.6f, pal.principal, Fuentes.logo, FontWeight.Normal, 4f), f(ms, 800f, 450f))
    // Íconos que se encienden en orden
    val iconos = 4
    val sep = u * 9f
    val x0 = w / 2f - sep * (iconos - 1) / 2f
    for (i in 0 until iconos) {
        val on = f(ms, 1150f + i * 280f, 220f)
        val col = Color.White.copy(alpha = 0.25f + 0.75f * on)
        val ci = Offset(x0 + i * sep, h * 0.69f)
        val t = u * 1.7f
        when (i) {
            0 -> { drawArc(col, 180f, 180f, false, Offset(ci.x - t, ci.y - t), Size(t * 2, t * 2), style = Stroke(u * 0.3f))
                drawLine(col, Offset(ci.x - t, ci.y), Offset(ci.x + t, ci.y + t * 0.3f), u * 0.3f) }
            1 -> { drawCircle(col, t * 0.55f, ci, style = Stroke(u * 0.3f))
                for (k in 0 until 8) { val a = k * Math.PI / 4
                    drawLine(col, Offset(ci.x + t * 0.7f * cos(a).toFloat(), ci.y + t * 0.7f * sin(a).toFloat()),
                        Offset(ci.x + t * cos(a).toFloat(), ci.y + t * sin(a).toFloat()), u * 0.3f) } }
            2 -> { val p = Path().apply { moveTo(ci.x - t * 0.6f, ci.y - t * 0.5f); lineTo(ci.x + t * 0.6f, ci.y + t * 0.5f)
                    lineTo(ci.x, ci.y + t); lineTo(ci.x, ci.y - t); lineTo(ci.x + t * 0.6f, ci.y - t * 0.5f); lineTo(ci.x - t * 0.6f, ci.y + t * 0.5f) }
                drawPath(p, col, style = Stroke(u * 0.3f)) }
            else -> { drawRoundRect(col, Offset(ci.x - t * 0.55f, ci.y - t), Size(t * 1.1f, t * 2f), CornerRadius(t * 0.2f), style = Stroke(u * 0.3f))
                drawLine(col, Offset(ci.x - t * 0.15f, ci.y + t * 0.7f), Offset(ci.x + t * 0.15f, ci.y + t * 0.7f), u * 0.3f) }
        }
        if (on > 0.5f) drawCircle(pal.principal.copy(alpha = 0.18f * on), t * 1.9f, ci)
    }
    val pb = f(ms, 1300f, 1700f, suave = false)
    textoCentrado(m, "CONECTANDO...", h * 0.79f, estiloI(u * 1.5f, Color(0xFFAEB6C0), espacio = 3f), f(ms, 1300f, 300f))
    barra(Rect(w * 0.36f, h * 0.845f, w * 0.64f, h * 0.845f + u * 0.8f), pb, pal)
}

// ------------------------------------------------------------
// 3 · Sistema
// ------------------------------------------------------------
private fun DrawScope.sistema(ms: Float, pal: PaletaNeon, m: TextMeasurer) {
    val w = size.width; val h = size.height
    val u = min(w, h * 1.8f) / 100f
    val c = Offset(w / 2f, h * 0.44f)
    // Anillos de radar que se abren
    val pa = f(ms, 0f, 1100f)
    for (k in 1..5) {
        val rr = h * 0.08f * k * pa
        drawCircle(pal.principal.copy(alpha = 0.22f - k * 0.03f), rr, c, style = Stroke(u * 0.25f))
    }
    // Barrido del radar
    val ang = (ms / 1400f * 360f) % 360f
    val rr = h * 0.40f * pa
    drawArc(Brush.sweepGradient(listOf(Color.Transparent, pal.principal.copy(alpha = 0.18f), Color.Transparent), center = c),
        ang - 40f, 40f, true, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2))
    // Marcas de 30 en 30 grados en el anillo de afuera
    for (k in 0 until 12) {
        val a = Math.toRadians(k * 30.0)
        val r1 = h * 0.40f * pa
        drawLine(pal.secundario.copy(alpha = 0.5f * pa), Offset(c.x + r1 * 0.95f * cos(a).toFloat(), c.y + r1 * 0.95f * sin(a).toFloat()),
            Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()), u * 0.35f)
    }
    logo(m, c, u * 8f, pal, f(ms, 500f, 700f))
    // "///"
    val pf = f(ms, 1000f, 400f)
    for (k in 0 until 3) {
        val x = w / 2f - u * 3f + k * u * 2.2f
        val y = c.y + u * 7.5f
        val p = Path().apply { moveTo(x, y + u * 1.6f); lineTo(x + u * 1.0f, y); lineTo(x + u * 2.0f, y); lineTo(x + u * 1.0f, y + u * 1.6f); close() }
        drawPath(p, pal.principal.copy(alpha = pf))
    }
    textoCentrado(m, "CONEXIÓN CON TU MOTO", h * 0.76f, estiloI(u * 1.6f, Color(0xFFD9DEE4), espacio = 4f), f(ms, 1150f, 400f))
    // Puntos de carga
    val pc = f(ms, 1300f, 1700f, suave = false)
    val n = 9
    val sep = u * 2.2f
    val x0 = w / 2f - sep * (n - 1) / 2f
    for (k in 0 until n) {
        val on = pc * n > k
        drawCircle(if (on) pal.principal else Color.White.copy(alpha = 0.15f), u * 0.45f, Offset(x0 + k * sep, h * 0.82f))
    }
    val estado = ESTADOS_N[(pc * (ESTADOS_N.size - 1)).toInt().coerceIn(0, ESTADOS_N.size - 1)]
    textoCentrado(m, estado, h * 0.865f, estiloI(u * 1.2f, Color(0xFF8C96A3), Fuentes.pixel), f(ms, 1300f, 300f))
}
