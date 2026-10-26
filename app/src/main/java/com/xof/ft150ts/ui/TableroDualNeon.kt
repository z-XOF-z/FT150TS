package com.xof.ft150ts.ui

import android.media.AudioManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.Moto
import com.xof.ft150ts.MusicaManager
import com.xof.ft150ts.PAGINA_APPS
import com.xof.ft150ts.PAGINA_MOTO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Tablero "Dual Neón" (v3.10): recreación del concepto de Andrew con velocímetro hexagonal cian al centro, la "N" en
 * su pestaña, arco de RPM multicolor a la izquierda, gasolina en arco rojo a la derecha, cuatro paneles rojos abajo con
 * la línea roja del borde, y a los lados los botones (inicio, MODO RACE, PRCXE, LAUNCH) y las dos crucetas.
 * Paleta de su especificación: #080812, #100D1B, #00DFFF, #176BFF, #FF303F, #D91F35, #54FF70, #A34AFF, #F8F8FF, #C9C6D5.
 *
 * Datos reales: la FT150TS no tiene presión de aceite ni TPS (es de carburador), así que esos dos paneles muestran TRIP
 * e INCLINACIÓN con el mismo diseño. La barra de arriba del concepto la pone la app (tema Racing Morado).
 * Botones: casa → tus apps; PRCXE y LAUNCH → página Moto (arrancones y estadísticas); crucetas → música y volumen.
 */
private val FONDO_D = Color(0xFF080812)
private val PANEL_D = Color(0xFF100D1B)
private val CIAN: Color get() = tinteCian(Color(0xFF00DFFF))
private val AZUL: Color get() = tinteCian(Color(0xFF176BFF))
private val ROJO = Color(0xFFFF303F)
private val ROJO2 = Color(0xFFD91F35)
private val VERDE_D = Color(0xFF54FF70)
private val MORADO_D: Color get() = tinteCian(Color(0xFFA34AFF))
private val BLANCO_D = Color(0xFFF8F8FF)
private val GRIS_D = Color(0xFFC9C6D5)
private const val TOPE_D = 10_000f

private class GeoDual(val s: Size) {
    private val desde = 0.115f
    private val hasta = 0.935f
    val hd = min(s.height / (hasta - desde), s.width / 2.20f)
    val wd = hd * 2.20f
    val extra = max(0f, s.width - wd)
    val oy = -desde * hd
    fun x(f: Float): Float = when {
        f <= 0.15f -> f * wd
        f >= 0.85f -> f * wd + extra
        else -> 0.15f * wd + (f - 0.15f) / 0.70f * (0.70f * wd + extra)
    }
    fun y(f: Float) = oy + f * hd
    fun p(fx: Float, fy: Float) = Offset(x(fx), y(fy))
    fun r(x0: Float, y0: Float, x1: Float, y1: Float) = Rect(x(x0), y(y0), x(x1), y(y1))
    val u = hd / 100f

    val casa = r(0.030f, 0.255f, 0.085f, 0.375f)
    val modo = r(0.026f, 0.490f, 0.118f, 0.600f)
    val crucetaIzq = r(0.030f, 0.640f, 0.125f, 0.830f)
    val prcxe = r(0.858f, 0.200f, 0.952f, 0.315f)
    val launch = r(0.858f, 0.390f, 0.952f, 0.505f)
    val crucetaDer = r(0.860f, 0.560f, 0.955f, 0.790f)
    val hex = r(0.360f, 0.190f, 0.640f, 0.625f)
    val tabN = r(0.468f, 0.150f, 0.532f, 0.255f)
    // RPM: arco que sube de abajo-izquierda hacia arriba-centro
    val centroRpm = Offset(x(0.335f), y(0.625f))
    val radioRpm = min(hd * 0.40f, (x(0.335f) - x(0.165f)))
    // Gasolina: arco rojo de arriba-izquierda a abajo-derecha
    val centroGas = Offset(x(0.665f), y(0.625f))
    val radioGas = min(hd * 0.40f, (x(0.835f) - x(0.665f)))
    val paneles = List(4) { i ->
        val x0 = 0.168f + i * 0.168f
        r(x0, 0.665f, x0 + 0.160f, 0.835f)
    }
}

@Composable
internal fun TableroDualNeon() {
    val rS = Moto.rapida.enVivo()
    val vjS = Moto.viaje.enVivo()
    val eS = Moto.estado.enVivo()
    val rpm = rpmSuave()
    val velS = velocidadAnimadaEstado()
    val arrS = LocalArranque.current
    val medidor = rememberTextMeasurer(cacheSize = 48)
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }
    val geo = remember { arrayOfNulls<GeoDual>(1) }
    Box(
        Modifier.fillMaxSize().animacionSwitch(arrS, CIAN)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { Apariencia.siguienteTablero() }) { pos ->
                    val g = geo[0] ?: return@detectTapGestures
                    fun en(r: Rect) = r.inflate(g.u * 1.5f).contains(pos)
                    when {
                        en(g.casa) -> Lanzador.irA(PAGINA_APPS)
                        en(g.prcxe) || en(g.launch) -> Lanzador.irA(PAGINA_MOTO)
                        en(g.crucetaIzq) || en(g.crucetaDer) -> {
                            val r = if (en(g.crucetaIzq)) g.crucetaIzq else g.crucetaDer
                            val dx = pos.x - r.center.x; val dy = pos.y - r.center.y
                            when {
                                abs(dx) < r.width * 0.16f && abs(dy) < r.height * 0.16f -> MusicaManager.playPausa()
                                abs(dx) > abs(dy) -> if (dx < 0) MusicaManager.anterior() else MusicaManager.siguiente()
                                else -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                                    if (dy < 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                            }
                        }
                    }
                }
            }
            .graphicsLayer()
            .drawWithCache {
                val g = GeoDual(size)
                geo[0] = g
                val fijo = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(fijo), size) { fijoDual(g, medidor) }
                val p = Path()
                onDrawBehind {
                    drawImage(fijo)
                    val r = rS.value
                    val prueba = pruebaSegmentos(arrS.value)
                    datosDual(g, medidor, p, prueba, if (prueba) 888f else velS.value, rpm.value, r.marcha, r.gas, r.temp, r.vMoto,
                        vjS.value.km, r.incl, eS.value.perfil == 1)
                }
            },
    )
}

/** Polígono con esquinas cortadas (las cuatro). */
private fun Path.cortado(r: Rect, c: Float) {
    reset()
    moveTo(r.left + c, r.top); lineTo(r.right - c, r.top); lineTo(r.right, r.top + c); lineTo(r.right, r.bottom - c)
    lineTo(r.right - c, r.bottom); lineTo(r.left + c, r.bottom); lineTo(r.left, r.bottom - c); lineTo(r.left, r.top + c); close()
}

// ============================================================
// Capa fija
// ============================================================
private fun DrawScope.fijoDual(g: GeoDual, m: TextMeasurer) {
    val u = g.u
    val cv = g.p(0.5f, 0.45f); val rv = min(g.s.width, g.s.height * 1.6f) * 0.5f
    drawCircle(Brush.radialGradient(0f to AZUL.copy(alpha = 0.10f), 0.6f to AZUL.copy(alpha = 0.035f), 1f to Color.Transparent, center = cv, radius = rv), rv, cv)
    val pp = Path()

    // ---- Marco angular del grupo central (morado y azul) ----
    val marco = Path().apply {
        moveTo(g.x(0.160f), g.y(0.640f)); lineTo(g.x(0.160f), g.y(0.300f)); lineTo(g.x(0.235f), g.y(0.165f))
        lineTo(g.x(0.420f), g.y(0.165f)); lineTo(g.x(0.440f), g.y(0.135f)); lineTo(g.x(0.560f), g.y(0.135f))
        lineTo(g.x(0.580f), g.y(0.165f)); lineTo(g.x(0.765f), g.y(0.165f)); lineTo(g.x(0.840f), g.y(0.300f)); lineTo(g.x(0.840f), g.y(0.640f))
    }
    // Panel azul marino dentro del marco (como en el concepto los medidores no van sobre negro)
    val relleno = Path().apply { addPath(marco); lineTo(g.x(0.160f), g.y(0.640f)); close() }
    drawPath(relleno, Brush.verticalGradient(listOf(Color(0xFF14123A), tinteCian(Color(0xFF0D0B22)), Color(0xFF0A0818)), g.y(0.135f), g.y(0.64f)))
    halo(marco, MORADO_D, u * 0.9f, u * 1.4f)
    drawPath(marco, MORADO_D.copy(alpha = 0.85f), style = Stroke(u * 0.30f, join = StrokeJoin.Miter))
    // Segunda línea azul por dentro del marco (doble filete del concepto)
    val marco2 = Path().apply {
        moveTo(g.x(0.172f), g.y(0.640f)); lineTo(g.x(0.172f), g.y(0.310f)); lineTo(g.x(0.243f), g.y(0.185f)); lineTo(g.x(0.345f), g.y(0.185f))
        moveTo(g.x(0.655f), g.y(0.185f)); lineTo(g.x(0.757f), g.y(0.185f)); lineTo(g.x(0.828f), g.y(0.310f)); lineTo(g.x(0.828f), g.y(0.640f))
    }
    drawPath(marco2, AZUL.copy(alpha = 0.55f), style = Stroke(u * 0.22f, join = StrokeJoin.Miter))
    // brillos azules en la parte de arriba
    val brillo = Path().apply { moveTo(g.x(0.30f), g.y(0.185f)); lineTo(g.x(0.42f), g.y(0.185f)) ; moveTo(g.x(0.58f), g.y(0.185f)); lineTo(g.x(0.70f), g.y(0.185f)) }
    halo(brillo, AZUL, u * 1.2f, u * 1.6f); drawPath(brillo, CIAN.copy(alpha = 0.8f), style = Stroke(u * 0.3f))

    // ---- Hexágono del velocímetro (capas cian y azul) ----
    val h = g.hex
    fun hexP(r: Rect, punta: Float) = Path().apply {
        moveTo(r.left + punta, r.top); lineTo(r.right - punta, r.top); lineTo(r.right, r.center.y)
        lineTo(r.right - punta, r.bottom); lineTo(r.left + punta, r.bottom); lineTo(r.left, r.center.y); close()
    }
    val exterior = hexP(Rect(h.left - u * 2f, h.top - u * 1.5f, h.right + u * 2f, h.bottom + u * 1.5f), h.width * 0.20f)
    drawPath(exterior, tinteCian(Color(0xFF0A1030)))
    halo(exterior, AZUL, u * 1.2f, u * 1.8f)
    drawPath(exterior, AZUL, style = Stroke(u * 0.45f, join = StrokeJoin.Miter))
    val interior = hexP(h, h.width * 0.18f)
    drawPath(interior, Brush.verticalGradient(listOf(Color(0xFF0B1636), tinteCian(Color(0xFF080B1C))), h.top, h.bottom))
    halo(interior, CIAN, u * 0.8f, u * 1.2f)
    drawPath(interior, CIAN.copy(alpha = 0.9f), style = Stroke(u * 0.3f, join = StrokeJoin.Miter))
    // "Alas" azules que salen de las puntas del hexágono hacia el marco
    val alas = Path().apply {
        moveTo(h.left - u * 2f, h.center.y); lineTo(h.left - u * 6f, h.center.y - u * 6f); lineTo(h.left - u * 6f, h.top + u * 2f)
        moveTo(h.right + u * 2f, h.center.y); lineTo(h.right + u * 6f, h.center.y - u * 6f); lineTo(h.right + u * 6f, h.top + u * 2f)
        moveTo(h.left - u * 2f, h.center.y); lineTo(h.left - u * 6f, h.center.y + u * 6f)
        moveTo(h.right + u * 2f, h.center.y); lineTo(h.right + u * 6f, h.center.y + u * 6f)
    }
    halo(alas, CIAN, u * 0.8f, u * 1.2f)
    drawPath(alas, CIAN.copy(alpha = 0.85f), style = Stroke(u * 0.3f, join = StrokeJoin.Miter))
    // acentos morados en las puntas
    drawLine(MORADO_D, Offset(h.left + u * 0.4f, h.center.y - u * 4f), Offset(h.left + u * 0.4f, h.center.y + u * 4f), u * 0.5f)
    drawLine(MORADO_D, Offset(h.right - u * 0.4f, h.center.y - u * 4f), Offset(h.right - u * 0.4f, h.center.y + u * 4f), u * 0.5f)
    // pestaña de la marcha
    pp.cortado(g.tabN, u * 1.2f)
    drawPath(pp, tinteCian(Color(0xFF092B43))); halo(pp, CIAN, u * 0.6f, u * 1.2f); drawPath(pp, CIAN, style = Stroke(u * 0.3f))
    texto(m, "KM/H", Offset(h.center.x, h.top + h.height * 0.82f), estiloM(u * 4.2f, BLANCO_D, Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f)

    // ---- RPM: números, "RPM x1000" y bordes ----
    val c = g.centroRpm; val rr = g.radioRpm
    drawArc(MORADO_D.copy(alpha = 0.8f), 180f, 90f, false, Offset(c.x - rr * 1.02f, c.y - rr * 1.02f), Size(rr * 2.04f, rr * 2.04f), style = Stroke(u * 0.35f))
    drawArc(CIAN.copy(alpha = 0.6f), 180f, 90f, false, Offset(c.x - rr * 0.735f, c.y - rr * 0.735f), Size(rr * 1.47f, rr * 1.47f), style = Stroke(u * 0.3f))
    for (k in 0..10 step 2) {
        val a = Math.toRadians(180.0 + 90.0 * k / 10.0)
        val pos = Offset(c.x + rr * 0.58f * cos(a).toFloat(), c.y + rr * 0.58f * sin(a).toFloat())
        italica(pos, -0.18f) { texto(m, "$k", pos, estiloM(u * 3.8f, if (k == 10) ROJO else Color(0xFFE9E7FF), Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f) }
    }
    texto(m, "RPM", Offset(c.x - rr * 0.30f, c.y - rr * 0.30f), estiloM(u * 4.0f, Color(0xFFE9E7FF), Fuentes.logo, FontWeight.Normal, d = this))
    texto(m, "x1000", Offset(c.x - rr * 0.30f, c.y - rr * 0.14f), estiloM(u * 3.4f, CIAN, Fuentes.logo, FontWeight.Normal, d = this))

    // ---- Gasolina: marco rojo y morado, F/E y bomba ----
    val cg = g.centroGas; val rg = g.radioGas
    drawArc(ROJO.copy(alpha = 0.8f), 270f, 90f, false, Offset(cg.x - rg * 1.02f, cg.y - rg * 1.02f), Size(rg * 2.04f, rg * 2.04f), style = Stroke(u * 0.35f))
    drawArc(MORADO_D.copy(alpha = 0.7f), 270f, 90f, false, Offset(cg.x - rg * 0.735f, cg.y - rg * 0.735f), Size(rg * 1.47f, rg * 1.47f), style = Stroke(u * 0.3f))
    drawArc(Color(0xFF130B19), 272f, 86f, false, Offset(cg.x - rg * 0.875f, cg.y - rg * 0.875f), Size(rg * 1.75f, rg * 1.75f), style = Stroke(rg * 0.25f))
    texto(m, "F", Offset(cg.x - rg * 0.16f, cg.y - rg * 0.86f), estiloM(u * 4.2f, BLANCO_D, Fuentes.logo, FontWeight.Normal, d = this))
    texto(m, "E", Offset(cg.x + rg * 0.85f, cg.y + u * 0.5f), estiloM(u * 4.2f, ROJO, Fuentes.logo, FontWeight.Normal, d = this))
    bomba(Offset(cg.x + rg * 0.32f, cg.y - rg * 0.35f), u * 2.6f, BLANCO_D)

    // ---- Columna izquierda ----
    pp.redondo(g.casa, u * 1.8f); drawPath(pp, Color(0xFF24101A)); halo(pp, ROJO, u * 0.8f, u * 1.4f); drawPath(pp, Color(0xFFFF3548), style = Stroke(u * 0.35f))
    casita(g.casa.center, u * 2.4f, Color(0xFFFF4655))
    texto(m, "MODO:", g.p(0.030f, 0.445f), estiloM(u * 3.8f, Color(0xFFF4EFF8), d = this))
    pp.redondo(g.modo, u * 1.8f); drawPath(pp, Color(0xFF260C17)); halo(pp, ROJO, u * 0.8f, u * 1.4f); drawPath(pp, Color(0xFFFF3448), style = Stroke(u * 0.35f))
    cruceta(g.crucetaIzq, u, false)

    // ---- Columna derecha ----
    pp.redondo(g.prcxe, u * 1.8f); drawPath(pp, Color(0xFF0C2119)); halo(pp, VERDE_D, u * 0.8f, u * 1.4f); drawPath(pp, VERDE_D, style = Stroke(u * 0.35f))
    texto(m, "PRCXE", g.prcxe.center, estiloM(u * 4.0f, Color(0xFF75FF8B), Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f)
    pp.redondo(g.launch, u * 1.8f); drawPath(pp, Color(0xFF15151E)); drawPath(pp, Color(0xFF77727F), style = Stroke(u * 0.35f))
    texto(m, "LAUNCH", g.launch.center, estiloM(u * 3.6f, Color(0xFFE6E2EC), d = this), alinear = 0.5f)
    cruceta(g.crucetaDer, u, true)

    // ---- Paneles rojos de abajo ----
    val titulos = listOf("TEMP MOTOR", "VOLTAJE", "TRIP", "INCLINACIÓN")
    g.paneles.forEachIndexed { i, r ->
        pp.cortado(r, u * 1.4f)
        drawPath(pp, Color(0xFF160B17)); halo(pp, ROJO, u * 0.9f, u * 1.4f); drawPath(pp, Color(0xFFFF3548), style = Stroke(u * 0.35f))
        val conIcono = i < 2
        val x = if (conIcono) r.left + r.width * 0.32f else r.center.x
        texto(m, titulos[i], Offset(x, r.top + r.height * 0.30f), estiloM(u * 3.1f, Color(0xFFF2EAF2), d = this), alinear = if (conIcono) 0f else 0.5f)
        if (conIcono) termometroD(Offset(r.left + r.width * 0.16f, r.center.y), u * 1.7f, Color(0xFFFF6571))
    }
    // Línea roja del borde de abajo (sube en las esquinas)
    val borde = Path().apply {
        moveTo(g.x(0.012f), g.y(0.835f)); quadraticTo(g.x(0.014f), g.y(0.905f), g.x(0.05f), g.y(0.905f))
        lineTo(g.x(0.95f), g.y(0.905f)); quadraticTo(g.x(0.986f), g.y(0.905f), g.x(0.988f), g.y(0.835f))
    }
    halo(borde, ROJO, u * 1.6f, u * 2.2f)
    drawPath(borde, Color(0xFFFF6B76), style = Stroke(u * 0.45f, cap = StrokeCap.Round))
    // separador morado de las columnas
    val sep = Path().apply {
        moveTo(g.x(0.140f), g.y(0.22f)); lineTo(g.x(0.140f), g.y(0.86f))
        moveTo(g.x(0.848f), g.y(0.22f)); lineTo(g.x(0.848f), g.y(0.86f))
    }
    drawPath(sep, MORADO_D.copy(alpha = 0.35f), style = Stroke(u * 0.25f))
}

/** Arcoíris del arco de RPM: verde, amarillo, naranja, rojo, magenta, morado y azul arriba. */
private fun arcoiris(f: Float): Color {
    val paradas = listOf(Color(0xFF54FF70), Color(0xFFB6FF3A), Color(0xFFFFE03A), Color(0xFFFF9A2A), Color(0xFFFF303F),
        Color(0xFFFF3FB0), Color(0xFFA34AFF), Color(0xFF3D7BFF))
    val x = f.coerceIn(0f, 1f) * (paradas.size - 1)
    val k = x.toInt().coerceAtMost(paradas.size - 2)
    return androidx.compose.ui.graphics.lerp(paradas[k], paradas[k + 1], x - k)
}

private fun DrawScope.casita(c: Offset, t: Float, col: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - t); lineTo(c.x + t, c.y - t * 0.1f); lineTo(c.x + t * 0.7f, c.y - t * 0.1f); lineTo(c.x + t * 0.7f, c.y + t * 0.9f)
        lineTo(c.x + t * 0.2f, c.y + t * 0.9f); lineTo(c.x + t * 0.2f, c.y + t * 0.35f); lineTo(c.x - t * 0.2f, c.y + t * 0.35f)
        lineTo(c.x - t * 0.2f, c.y + t * 0.9f); lineTo(c.x - t * 0.7f, c.y + t * 0.9f); lineTo(c.x - t * 0.7f, c.y - t * 0.1f)
        lineTo(c.x - t, c.y - t * 0.1f); close()
    }
    drawPath(p, col)
}

private fun DrawScope.bomba(c: Offset, t: Float, col: Color) {
    drawRoundRect(col, Offset(c.x - t * 0.7f, c.y - t * 0.9f), Size(t * 1.1f, t * 1.8f), CornerRadius(t * 0.15f))
    drawRect(Color(0xFF130B19), Offset(c.x - t * 0.5f, c.y - t * 0.7f), Size(t * 0.7f, t * 0.45f))
    val mng = Path().apply { moveTo(c.x + t * 0.4f, c.y - t * 0.3f); lineTo(c.x + t * 0.75f, c.y); lineTo(c.x + t * 0.75f, c.y + t * 0.6f) }
    drawPath(mng, col, style = Stroke(t * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.termometroD(c: Offset, t: Float, col: Color) {
    drawLine(col, Offset(c.x, c.y - t * 1.1f), Offset(c.x, c.y + t * 0.4f), t * 0.42f, StrokeCap.Round)
    drawCircle(col, t * 0.55f, Offset(c.x, c.y + t * 0.75f))
    drawLine(Color(0xFF160B17), Offset(c.x, c.y - t * 0.95f), Offset(c.x, c.y + t * 0.5f), t * 0.16f, StrokeCap.Round)
}

/** Cruceta de botones con borde metálico; la derecha lleva el ▶▶ verde. */
private fun DrawScope.cruceta(r: Rect, u: Float, derecha: Boolean) {
    val p = Path()
    p.redondo(r, u * 2.2f); drawPath(p, Color(0xFF171722)); drawPath(p, Color(0xFF55525E), style = Stroke(u * 0.3f))
    val c = r.center; val e = min(r.width, r.height) * 0.27f
    val dirs = listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))
    dirs.forEach { d ->
        val t = Offset(c.x + d.x * e, c.y + d.y * e)
        val rombo = Path().apply {
            moveTo(t.x, t.y - e * 0.55f); lineTo(t.x + e * 0.55f, t.y); lineTo(t.x, t.y + e * 0.55f); lineTo(t.x - e * 0.55f, t.y); close()
        }
        drawPath(rombo, Color(0xFF22212D)); drawPath(rombo, Color(0xFF8E8A98), style = Stroke(u * 0.22f))
        val tri = Path().apply {
            moveTo(t.x + d.x * e * 0.25f, t.y + d.y * e * 0.25f)
            lineTo(t.x - d.x * e * 0.15f - d.y * e * 0.2f, t.y - d.y * e * 0.15f - d.x * e * 0.2f)
            lineTo(t.x - d.x * e * 0.15f + d.y * e * 0.2f, t.y - d.y * e * 0.15f + d.x * e * 0.2f); close()
        }
        val verde = derecha && d.x > 0f
        drawPath(tri, if (verde) VERDE_D else Color(0xFFD9D6E0))
        if (verde) {
            val tri2 = Path().apply { moveTo(t.x + e * 0.05f, t.y); lineTo(t.x - e * 0.32f, t.y - e * 0.2f); lineTo(t.x - e * 0.32f, t.y + e * 0.2f); close() }
            drawPath(tri2, VERDE_D)
        }
    }
    val centro = Path().apply { moveTo(c.x, c.y - e * 0.3f); lineTo(c.x + e * 0.3f, c.y); lineTo(c.x, c.y + e * 0.3f); lineTo(c.x - e * 0.3f, c.y); close() }
    drawPath(centro, Color(0xFF2A2935)); drawPath(centro, Color(0xFF8E8A98), style = Stroke(u * 0.2f))
}

// ============================================================
// Lo que cambia
// ============================================================
private fun DrawScope.datosDual(
    g: GeoDual, m: TextMeasurer, p: Path, prueba: Boolean, vel: Float, rpm: Float, marcha: Int, gas: Int, temp: Float,
    volt: Float, trip: Float, incl: Float, sport: Boolean,
) {
    val u = g.u
    // ---- Arco de RPM: bloques gruesos pegados, arcoíris de verde a azul (como el concepto) ----
    run {
        val c = g.centroRpm; val rr = g.radioRpm
        val n = 26
        val t = (rpm / TOPE_D).coerceIn(0f, 1f)
        val prendidos = (t * n + 0.5f).toInt()
        val rm = rr * 0.875f; val grosor = rr * 0.24f
        val caja = Size(rm * 2f, rm * 2f); val esq = Offset(c.x - rm, c.y - rm)
        val paso = 90f / n
        for (i in 0 until n) {
            val f = (i + 0.5f) / n
            val base = arcoiris(f)
            val col = if (i < prendidos) base else base.copy(alpha = 0.16f)
            drawArc(col, 180f + paso * i, paso * 0.74f, false, esq, caja, style = Stroke(grosor))
        }
        if (prendidos > 0) {
            val a = Math.toRadians(180.0 + 90.0 * prendidos / n)
            val pt = Offset(c.x + rm * cos(a).toFloat(), c.y + rm * sin(a).toFloat())
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = pt, radius = grosor), grosor, pt)
        }
    }

    // ---- Gasolina: tablillas rojas gruesas apiladas siguiendo la curva (E abajo, F arriba) ----
    run {
        val c = g.centroGas; val rg = g.radioGas
        val n = 14
        val llenas = if (gas < 0) 0 else ((gas / 100f) * n + 0.5f).toInt()
        val rm = rg * 0.875f; val grosor = rg * 0.24f
        val caja = Size(rm * 2f, rm * 2f); val esq = Offset(c.x - rm, c.y - rm)
        val paso = 86f / n
        for (i in 0 until n) {
            // i = 0 abajo (E) … n-1 arriba (F)
            val inicio = 358f - paso * (i + 1)
            val col = if (i < llenas) (if (i < 2) Color(0xFFFF6B76) else ROJO) else Color(0xFF3A0F18)
            drawArc(col, inicio + paso * 0.13f, paso * 0.74f, false, esq, caja, style = Stroke(grosor))
        }
    }

    // ---- Marcha y velocidad ----
    val txtM = when { prueba -> "8"; marcha < 0 -> "-"; marcha == 0 -> "N"; else -> "$marcha" }
    texto(m, txtM, g.tabN.center, estiloM(u * 6.2f, tinteCian(Color(0xFF00E7FF)), Fuentes.logo, FontWeight.Normal, brillo = CIAN, radio = u * 2f, d = this), alinear = 0.5f)
    val txtV = when { prueba -> "888"; vel < 0f -> "--"; else -> "${vel.toInt()}" }
    val cv = Offset(g.hex.center.x, g.hex.top + g.hex.height * 0.47f)
    // números altos y cuadrados: 7 segmentos con halo cian
    val hDig = g.hex.height * 0.50f
    val wDig = hDig * 0.50f
    val paso = wDig * 1.30f
    val n = txtV.length
    val ancho = paso * (n - 1) + anchoDigito(wDig, hDig)
    drawCircle(Brush.radialGradient(listOf(CIAN.copy(alpha = 0.20f), Color.Transparent), center = cv, radius = g.hex.width * 0.36f), g.hex.width * 0.36f, cv)
    txtV.forEachIndexed { i, ch ->
        val x = cv.x - ancho / 2f + i * paso
        digito7(ch, x - u * 0.3f, cv.y - hDig / 2f - u * 0.3f, wDig, hDig, CIAN.copy(alpha = 0.35f), Color.Transparent, p)
        digito7(ch, x, cv.y - hDig / 2f, wDig, hDig, tinteCian(Color(0xFFF5FFFF)), Color.Transparent, p)
    }

    // ---- MODO ----
    texto(m, if (sport) "RACE" else "ECO", g.modo.center, estiloM(u * 4.0f, Color(0xFFFF5263), Fuentes.logo, FontWeight.Normal, brillo = ROJO, radio = u * 1.2f, d = this), alinear = 0.5f)

    // ---- Paneles ----
    val va = { col: Color -> estiloM(u * 5.4f, col, Fuentes.logo, FontWeight.Normal, d = this) }
    val un = estiloM(u * 3.2f, BLANCO_D, d = this)
    val datos = listOf(
        (if (temp > -90f) "${temp.toInt()}" else "--") to "°C",
        (if (volt >= 0f) fmt(volt, 1) else "--") to "V",
        fmt(max(trip, 0f), 1) to "km",
        (if (incl >= 0f) "${incl.toInt()}" else "--") to "°",
    )
    g.paneles.forEachIndexed { i, r ->
        val (v, unidad) = datos[i]
        val col = when { i == 0 && temp >= 105f -> ROJO; i == 1 && volt in 0f..11.8f -> ROJO; else -> BLANCO_D }
        val res = m.measure(v, va(col))
        val resU = m.measure(unidad, un)
        val total = res.size.width + u * 0.8f + resU.size.width
        val x0 = if (i < 2) r.left + r.width * 0.32f else r.center.x - total / 2f
        val y = r.top + r.height * 0.68f
        drawText(res, topLeft = Offset(x0, y - res.size.height / 2f))
        drawText(resU, topLeft = Offset(x0 + res.size.width + u * 0.8f, y + res.size.height * 0.5f - resU.size.height * 1.05f))
    }
}

// ============================================================
// Vista para Ajustes
// ============================================================
internal fun DrawScope.vistaDualNeon() {
    drawRect(FONDO_D)
    val w = size.width; val h = size.height
    val c = Offset(w * 0.32f, h * 0.70f); val r = h * 0.50f
    for (i in 0 until 16) {
        val f = (i + 0.5f) / 16
        val a = Math.toRadians(180.0 + 90.0 * f)
        val col = when { f < 0.4f -> Color(0xFF54FF70); f < 0.68f -> Color(0xFFFFD23A); else -> ROJO }
        drawLine(col, Offset(c.x + r * 0.75f * cos(a).toFloat(), c.y + r * 0.75f * sin(a).toFloat()), Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), h * 0.04f)
    }
    val hx = Rect(w * 0.40f, h * 0.20f, w * 0.60f, h * 0.70f)
    val p = Path().apply {
        moveTo(hx.left + hx.width * 0.2f, hx.top); lineTo(hx.right - hx.width * 0.2f, hx.top); lineTo(hx.right, hx.center.y)
        lineTo(hx.right - hx.width * 0.2f, hx.bottom); lineTo(hx.left + hx.width * 0.2f, hx.bottom); lineTo(hx.left, hx.center.y); close()
    }
    drawPath(p, CIAN, style = Stroke(h * 0.015f))
    numero7(0f, 1, 0, w * 0.47f, h * 0.30f, w * 0.05f, h * 0.28f, w * 0.06f, Color.White, Color.Transparent, Path())
    val cg = Offset(w * 0.68f, h * 0.70f)
    for (i in 0 until 10) {
        val a = Math.toRadians(360.0 - 90.0 * (i + 0.5) / 10)
        drawLine(ROJO, Offset(cg.x + r * 0.75f * cos(a).toFloat(), cg.y + r * 0.75f * sin(a).toFloat()), Offset(cg.x + r * cos(a).toFloat(), cg.y + r * sin(a).toFloat()), h * 0.04f)
    }
    repeat(4) { i -> drawRoundRect(ROJO, Offset(w * (0.16f + i * 0.17f), h * 0.78f), Size(w * 0.15f, h * 0.15f), CornerRadius(h * 0.02f), style = Stroke(h * 0.012f)) }
    drawLine(ROJO, Offset(w * 0.02f, h * 0.97f), Offset(w * 0.98f, h * 0.97f), h * 0.015f)
}
