package com.xof.ft150ts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.xof.ft150ts.Moto
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Tablero "Universal" (v3.6): réplica del tablero digital universal que se vende para Italika 125z / 150z / FT:
 * tacómetro redondo a la izquierda (0–12 ×1000 rpm, marcas blancas que pasan a rojo), anillo azul con la marcha,
 * pantalla inclinada a la derecha con la velocidad en 7 segmentos, gasolina (F/E), temperatura (L–H), testigos y
 * TRIP/ODO. Sin la marca del fabricante. Colores propios del tablero (blanco, rojo, azul), sobre el fondo del tema.
 * Todo en un solo Canvas: las figuras fijas se arman una vez por tamaño y en cada cuadro solo se pintan los datos.
 */
@Composable
fun TableroUniversal() {
    MarcoTablero { _, _ ->
        val rS = Moto.rapida.enVivo()
        val vjS = Moto.viaje.enVivo()
        val eS = Moto.estado.enVivo()
        val rpm = rpmSuave()
        val velS = velocidadAnimadaEstado()
        val arrS = LocalArranque.current
        val rojoCambio = enCambio(rpm, rS.value.shift)
        val destello = parpadeoEstado(rojoCambio)
        Box(Modifier.fillMaxSize().graphicsLayer().drawWithCache {
            // Proporción del tablero real ≈ 2.25 : 1; se centra en el espacio disponible
            val ancho = min(size.width, size.height * 2.25f)
            val alto = ancho / 2.25f
            val ox = (size.width - ancho) / 2
            val oy = (size.height - alto) / 2
            val g = Geom(ox, oy, ancho, alto)
            val p = Path()
            onDrawBehind {
                val r = rS.value
                val prueba = pruebaSegmentos(arrS.value)
                dibujarUniversal(g, p, r.marcha, rpm.value, r.shift, rojoCambio, destello.value,
                    if (prueba) 199f else velS.value, r.gas, r.temp, r.vMoto, vjS.value.odometro, eS.value.alerta.isNotBlank(), prueba)
            }
        })
    }
}

/** Medidas del tablero (todas salen del rectángulo que ocupa). */
private class Geom(val x: Float, val y: Float, val w: Float, val h: Float) {
    val cx = x + h * 0.50f
    val cy = y + h * 0.50f
    val radio = h * 0.42f
    // Pantalla de la derecha: trapecio inclinado (deja lugar a la columna de gasolina)
    val px0 = x + h * 1.22f
    val px1 = x + w * 0.98f
    val py0 = y + h * 0.16f
    val py1 = y + h * 0.74f
}

private val BLANCO = Color(0xFFF2F4F7)
private val APAGADO = Color(0xFF2A2F38)
private val ROJO = Color(0xFFFF3B30)
private val NARANJA = Color(0xFFFF8A00)
private val AZUL = Color(0xFF2D5BFF)
private val VERDE = Color(0xFF29D84A)
private val AMBAR = Color(0xFFFFC21A)
private val GRIS = Color(0xFF9AA3AE)

private fun DrawScope.dibujarUniversal(
    g: Geom, p: Path, marcha: Int, rpm: Float, shift: Int, rojo: Boolean, destello: Float,
    vel: Float, gas: Int, temp: Float, vMoto: Float, odo: Float, alerta: Boolean, prueba: Boolean,
) {
    val h = g.h
    val c0 = Offset(g.cx, g.cy)
    // ---------- Carcasa: el ocho del tablero real (círculo a la izquierda + cuerpo inclinado a la derecha) ----------
    val carcasa = Color(0xFF16191E)
    val orilla = Color(0xFF3B4048)
    p.reset()
    p.moveTo(g.cx + g.radio * 0.6f, g.y + h * 0.10f)
    p.lineTo(g.x + g.w - h * 0.02f, g.y + h * 0.10f)
    p.lineTo(g.x + g.w - h * 0.06f, g.y + h * 0.95f)
    p.lineTo(g.cx + g.radio * 0.6f, g.y + h * 0.95f)
    p.close()
    drawPath(p, carcasa)
    drawPath(p, orilla, style = Stroke(h * 0.012f))
    drawCircle(carcasa, g.radio * 1.16f, c0)
    drawArc(orilla, 95f, 265f, false, Offset(g.cx - g.radio * 1.16f, g.cy - g.radio * 1.16f),
        Size(g.radio * 2.32f, g.radio * 2.32f), style = Stroke(h * 0.012f))

    // ---------- Tacómetro: 0..12 en 230°, de abajo-izquierda a arriba-derecha ----------
    val ini = 110f
    val barrido = 230f
    val n = 48
    val enc = ((rpm / 12000f) * n).toInt().coerceIn(0, n)
    for (i in 0..n) {
        val a = Math.toRadians((ini + barrido * i / n).toDouble())
        val rpmMarca = i * 12000 / n
        val r1 = g.radio * 0.62f
        val r2 = g.radio * (if (i % 4 == 0) 0.84f else 0.80f)
        // Zona alta como el original: amarillo → naranja → rojo
        val base = when {
            rpmMarca >= 11500 -> ROJO
            rpmMarca >= 10500 -> Color(0xFFFF5A1F)
            rpmMarca >= 10000 -> NARANJA
            rpmMarca >= 9500 -> AMBAR
            else -> BLANCO
        }
        val c = when {
            rojo -> ROJO.copy(alpha = destello)
            i < enc || prueba -> base
            else -> APAGADO
        }
        drawLine(c, Offset(g.cx + r1 * cos(a).toFloat(), g.cy + r1 * sin(a).toFloat()),
            Offset(g.cx + r2 * cos(a).toFloat(), g.cy + r2 * sin(a).toFloat()), h * 0.020f, StrokeCap.Butt)
    }
    // Números 0..12 por FUERA de las marcas (como el real); 10-12 en rojo
    val px = max(1f, h / 110f)
    for (k in 0..12) {
        val a = Math.toRadians((ini + barrido * k / 12).toDouble())
        val rr = g.radio * 0.95f
        val t = "$k"
        textoPixel(t, g.cx + rr * cos(a).toFloat() - anchoPixel(t, px) / 2, g.cy + rr * sin(a).toFloat() - 3.5f * px, px,
            if (k >= 10) ROJO else BLANCO)
    }
    // Puntitos de la orilla
    for (i in 0..60) {
        val a = Math.toRadians((ini + barrido * i / 60).toDouble())
        val rr = g.radio * 1.08f
        drawCircle(GRIS, h * 0.004f, Offset(g.cx + rr * cos(a).toFloat(), g.cy + rr * sin(a).toFloat()))
    }
    // Anillo azul con la marcha
    val ra = g.radio * 0.50f
    drawCircle(Color(0xFF070A12), ra, c0)
    drawCircle(AZUL, ra, c0, style = Stroke(h * 0.016f))
    val pxc = px * 0.75f
    textoPixel("X1000RPM", g.cx - anchoPixel("X1000RPM", pxc) / 2, g.cy - ra * 0.72f, pxc, BLANCO)
    // Direccionales (la app no las mide: apagadas)
    flecha(g.cx - ra * 0.52f, g.cy - ra * 0.30f, ra * 0.16f, true, VERDE.copy(alpha = 0.18f), p)
    flecha(g.cx + ra * 0.52f, g.cy - ra * 0.30f, ra * 0.16f, false, VERDE.copy(alpha = 0.18f), p)
    textoPixel("GEAR", g.cx - anchoPixel("GEAR", pxc) / 2, g.cy - ra * 0.10f, pxc, BLANCO)
    // N verde y la marcha en 7 segmentos (roja, como el original)
    textoPixel("N", g.cx - ra * 0.68f, g.cy + ra * 0.22f, pxc * 2.4f, if (marcha == 0) VERDE else APAGADO)
    val dg = when { prueba -> '8'; marcha < 0 -> '-'; marcha == 0 -> '0'; else -> ('0' + marcha) }
    val dh = ra * 0.52f
    val dw = dh * 0.55f
    digito7(dg, g.cx + ra * 0.18f, g.cy + ra * 0.12f, dw, dh, if (marcha == 0) APAGADO else ROJO, APAGADO, p)
    // Línea blanca de abajo del tacómetro
    drawLine(BLANCO, Offset(g.cx - g.radio * 0.3f, g.cy + g.radio * 1.10f), Offset(g.cx + g.radio * 0.55f, g.cy + g.radio * 1.10f), h * 0.008f)

    // ---------- Gasolina: barras en arco pegadas al tacómetro (F arriba, E abajo, la de abajo roja) ----------
    val bloques = 6
    val llenos = if (gas < 0) 0 else ((gas / 100f) * bloques + 0.5f).toInt().coerceIn(0, bloques)
    val rg0 = g.radio * 1.24f
    val rg1 = g.radio * 1.42f
    for (b in 0 until bloques) {
        // de abajo (+40°) hacia arriba (-25°)
        val a0 = 40f - b * 11f
        val a1 = a0 - 8f
        val col = when { b >= llenos -> APAGADO; b == 0 -> ROJO; else -> BLANCO }
        p.reset()
        val r0a = Math.toRadians(a0.toDouble()); val r1a = Math.toRadians(a1.toDouble())
        p.moveTo(g.cx + rg0 * cos(r0a).toFloat(), g.cy + rg0 * sin(r0a).toFloat())
        p.lineTo(g.cx + rg1 * cos(r0a).toFloat(), g.cy + rg1 * sin(r0a).toFloat())
        p.lineTo(g.cx + rg1 * cos(r1a).toFloat(), g.cy + rg1 * sin(r1a).toFloat())
        p.lineTo(g.cx + rg0 * cos(r1a).toFloat(), g.cy + rg0 * sin(r1a).toFloat())
        p.close()
        drawPath(p, col)
    }
    val rf = g.radio * 1.30f
    val aF = Math.toRadians(-38.0); val aE = Math.toRadians(47.0)
    textoPixel("F", g.cx + rf * cos(aF).toFloat(), g.cy + rf * sin(aF).toFloat() - 3 * px, px, BLANCO)
    textoPixel("E", g.cx + rf * cos(aE).toFloat() - 4 * px, g.cy + rf * sin(aE).toFloat(), px, ROJO)
    // ícono de bomba de gasolina
    val bx = g.cx + g.radio * 1.02f; val by = g.cy - g.radio * 0.18f; val bt = h * 0.045f
    drawRect(AMBAR, Offset(bx, by), Size(bt * 0.6f, bt))
    drawLine(AMBAR, Offset(bx + bt * 0.6f, by + bt * 0.2f), Offset(bx + bt * 0.85f, by + bt * 0.45f), bt * 0.12f)

    // ---------- Pantalla de la derecha ----------
    val inc = h * 0.06f
    p.reset()
    p.moveTo(g.px0 + inc, g.py0); p.lineTo(g.px1 - h * 0.10f, g.py0 + h * 0.03f); p.lineTo(g.px1 - inc - h * 0.10f, g.py1 - h * 0.12f); p.lineTo(g.px0, g.py1 - h * 0.12f); p.close()
    drawPath(p, Color(0xFF05070B))
    drawPath(p, BLANCO.copy(alpha = 0.9f), style = Stroke(h * 0.008f))
    // Testigos de arriba: luces, alta, motor, ABS, temperatura
    val ty = g.y + h * 0.04f
    val tx = g.px0 + h * 0.08f
    val sep = (g.px1 - g.px0) / 5.6f
    luces(tx, ty, h * 0.045f, VERDE.copy(alpha = 0.25f))
    altas(tx + sep, ty, h * 0.045f, AZUL.copy(alpha = 0.3f))
    motorIcono(tx + sep * 2, ty, h * 0.045f, if (alerta) AMBAR else AMBAR.copy(alpha = 0.25f))
    abs(tx + sep * 3, ty, h * 0.045f, AMBAR.copy(alpha = 0.25f), px)
    termometro(g.px1 - h * 0.04f, g.py0 + h * 0.06f, h * 0.05f, if (temp > 105f) ROJO else ROJO.copy(alpha = 0.3f))
    // Velocidad en 7 segmentos con "mph / km/h" uno sobre otro
    val vh = h * 0.30f
    val vw = vh * 0.50f
    val paso = anchoDigito(vw, vh) * 1.08f
    val vx = g.px0 + (g.px1 - g.px0 - paso * 3 - h * 0.42f) / 2 + inc * 2
    numero7(if (vel < 0f) null else vel, 3, 0, vx, g.py0 + h * 0.08f, vw, vh, paso, BLANCO, APAGADO, p)
    textoPixel("MPH", vx + paso * 3 + h * 0.02f, g.py0 + h * 0.22f, px, GRIS.copy(alpha = 0.5f))
    textoPixel("KM/H", vx + paso * 3 + h * 0.02f, g.py0 + h * 0.31f, px, BLANCO)
    // Columna de temperatura a la derecha: barras inclinadas que se prenden de abajo hacia arriba
    val nT = 7
    val litT = if (temp <= -90f) 0 else (((temp - 40f) / 70f) * nT).toInt().coerceIn(0, nT)
    for (i in 0 until nT) {
        val yb = g.py1 - h * 0.14f - i * h * 0.075f
        val xb = g.px1 - h * 0.075f + i * h * 0.008f
        val col = if (i < litT) (if (i >= nT - 2) ROJO else BLANCO) else APAGADO
        p.reset(); p.moveTo(xb, yb); p.lineTo(xb + h * 0.035f, yb); p.lineTo(xb + h * 0.042f, yb - h * 0.055f); p.lineTo(xb + h * 0.007f, yb - h * 0.055f); p.close()
        drawPath(p, col)
    }
    // Barra de batería: L ▬ ▬ ▬ ▬ H con el ícono de batería (rojo si baja)
    val ly = g.py1 - h * 0.075f
    textoPixel("L", g.px0 + h * 0.02f, ly, px, ROJO)
    val nB = 6
    val litB = if (vMoto < 0f) 0 else (((vMoto - 11f) / 3.5f) * nB).toInt().coerceIn(0, nB)
    val bx0 = g.px0 + h * 0.08f
    val bw = (g.px1 - g.px0 - h * 0.45f) / nB
    for (i in 0 until nB) drawRect(if (i < litB) (if (i == 0) ROJO else BLANCO) else APAGADO,
        Offset(bx0 + i * bw, ly), Size(bw * 0.7f, h * 0.025f))
    textoPixel("H", bx0 + nB * bw + h * 0.02f, ly, px, BLANCO)
    drawRect(if (vMoto in 0f..11.8f) ROJO else ROJO.copy(alpha = 0.3f), Offset(bx0 + nB * bw + h * 0.10f, ly - h * 0.005f),
        Size(h * 0.04f, h * 0.03f), style = Stroke(h * 0.006f))
    // TRIP / ODO con "miles / km" uno sobre otro
    val oy = g.y + h * 0.74f
    textoPixel("TRIP  ODO", g.px0 + h * 0.10f, oy, px * 0.75f, BLANCO)
    val oh = h * 0.12f
    val ow = oh * 0.5f
    val opaso = anchoDigito(ow, oh) * 1.1f
    numero7(odo.coerceAtLeast(0f), 6, 0, g.px1 - h * 0.30f - opaso * 6, oy + h * 0.045f, ow, oh, opaso, BLANCO, APAGADO, p)
    textoPixel("MILES", g.px1 - h * 0.26f, oy + h * 0.075f, px * 0.8f, GRIS.copy(alpha = 0.5f))
    textoPixel("KM", g.px1 - h * 0.26f, oy + h * 0.125f, px * 0.8f, BLANCO)
}

// ---------- Íconos simples de testigos ----------
private fun DrawScope.flecha(x: Float, y: Float, t: Float, izquierda: Boolean, c: Color, p: Path) {
    p.reset()
    val s = if (izquierda) -1f else 1f
    p.moveTo(x + s * t, y); p.lineTo(x, y - t * 0.6f); p.lineTo(x, y - t * 0.25f); p.lineTo(x - s * t * 0.8f, y - t * 0.25f)
    p.lineTo(x - s * t * 0.8f, y + t * 0.25f); p.lineTo(x, y + t * 0.25f); p.lineTo(x, y + t * 0.6f); p.close()
    drawPath(p, c)
}

private fun DrawScope.luces(x: Float, y: Float, t: Float, c: Color) {
    drawCircle(c, t * 0.35f, Offset(x + t * 0.5f, y + t * 0.5f), style = Stroke(t * 0.12f))
    for (k in -1..1) {
        drawLine(c, Offset(x, y + t * 0.5f + k * t * 0.25f), Offset(x - t * 0.3f, y + t * 0.5f + k * t * 0.25f), t * 0.1f)
        drawLine(c, Offset(x + t, y + t * 0.5f + k * t * 0.25f), Offset(x + t * 1.3f, y + t * 0.5f + k * t * 0.25f), t * 0.1f)
    }
}

private fun DrawScope.altas(x: Float, y: Float, t: Float, c: Color) {
    drawArc(c, -90f, 180f, true, Offset(x + t * 0.2f, y), Size(t * 0.8f, t), style = Stroke(t * 0.12f))
    for (k in 0..3) drawLine(c, Offset(x - t * 0.3f, y + t * (0.15f + k * 0.23f)), Offset(x + t * 0.35f, y + t * (0.15f + k * 0.23f)), t * 0.1f)
}

private fun DrawScope.motorIcono(x: Float, y: Float, t: Float, c: Color) {
    drawRect(c, Offset(x, y + t * 0.2f), Size(t * 1.1f, t * 0.7f), style = Stroke(t * 0.12f))
    drawLine(c, Offset(x + t * 0.3f, y + t * 0.05f), Offset(x + t * 0.7f, y + t * 0.05f), t * 0.12f)
}

private fun DrawScope.abs(x: Float, y: Float, t: Float, c: Color, px: Float) {
    drawCircle(c, t * 0.6f, Offset(x + t * 0.5f, y + t * 0.5f), style = Stroke(t * 0.1f))
    textoPixel("ABS", x + t * 0.5f - anchoPixel("ABS", px * 0.6f) / 2, y + t * 0.3f, px * 0.6f, c)
}

private fun DrawScope.termometro(x: Float, y: Float, t: Float, c: Color) {
    drawLine(c, Offset(x, y), Offset(x, y + t * 0.7f), t * 0.14f)
    drawCircle(c, t * 0.18f, Offset(x, y + t * 0.8f))
    drawLine(c, Offset(x - t * 0.4f, y + t), Offset(x + t * 0.4f, y + t), t * 0.1f)
}

/** Vista previa para Ajustes. */
internal fun DrawScope.vistaUniversal() {
    val w = size.width; val h = size.height
    val r = h * 0.40f
    val c = Offset(w * 0.25f, h * 0.5f)
    for (i in 0..20) {
        val a = Math.toRadians((110 + 230 * i / 20).toDouble())
        drawLine(if (i > 16) ROJO else BLANCO, Offset(c.x + r * 0.8f * cos(a).toFloat(), c.y + r * 0.8f * sin(a).toFloat()),
            Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), h * 0.03f)
    }
    drawCircle(AZUL, r * 0.5f, c, style = Stroke(h * 0.03f))
    val p = Path()
    p.moveTo(w * 0.52f, h * 0.18f); p.lineTo(w * 0.96f, h * 0.22f); p.lineTo(w * 0.93f, h * 0.70f); p.lineTo(w * 0.50f, h * 0.70f); p.close()
    drawPath(p, BLANCO, style = Stroke(h * 0.02f))
    numero7(199f, 3, 0, w * 0.58f, h * 0.27f, w * 0.07f, h * 0.32f, w * 0.10f, BLANCO, APAGADO, Path())
    numero7(555555f, 6, 0, w * 0.58f, h * 0.76f, w * 0.03f, h * 0.12f, w * 0.05f, BLANCO, APAGADO, Path())
}
