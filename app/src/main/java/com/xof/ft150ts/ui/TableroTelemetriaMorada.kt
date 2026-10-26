package com.xof.ft150ts.ui

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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.BateriaTel
import com.xof.ft150ts.ClimaManager
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.Moto
import com.xof.ft150ts.PAGINA_MOTO
import com.xof.ft150ts.UbicacionManager
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Tablero "Telemetría Morada" (v3.10): recreación del concepto de Andrew "168 · 5" (barra de RPM horizontal, columna
 * de temperaturas, tacómetro circular con la marcha adentro, la velocidad dominante sin tarjeta, radar de fuerza G en
 * rombo, medidor vertical y tres paneles abajo), con su paleta (#08040F, #10091D, #C72BFF, #A347FF, #F04DFF, #F5F1FF,
 * #B7A9C9, #FF515B, #70FF93).
 *
 * La FT150TS no tiene sensores de aceite, llantas ni túnel de viento: cada panel conserva el diseño pero muestra el dato
 * REAL más cercano (ver la tabla en releases/NOTAS-v3.10.0.md):
 *  - Temperaturas: motor (sensor de la moto), celular (batería) y el chip del ESP32.
 *  - Radar G: aceleración y frenado (ESP32) y lateral calculada con la inclinación, máximos del viaje.
 *  - Medidor vertical: inclinación.  - Mapa de llantas: TRIP, ODO, velocidad máxima y km/L.
 *  - Coeficiente: voltaje de la moto.  - Sector: tu ruta real de hoy dibujada como circuito, temperatura y sensación.
 * La barra de arriba del concepto (logo, hora, batería) la pone la app con el tema Racing Morado.
 */
private val FONDO_T: Color get() = tinteMorado(Color(0xFF08040F))
private val PANEL_T: Color get() = tinteMorado(Color(0xFF10091D))
private val PRI: Color get() = tinteMorado(Color(0xFFC72BFF))
private val SEC: Color get() = tinteMorado(Color(0xFFA347FF))
private val ROSA: Color get() = tinteMorado(Color(0xFFF04DFF))
private val BLANCO_T = Color(0xFFF5F1FF)
private val GRIS_T: Color get() = tinteMorado(Color(0xFFB7A9C9))
private val ALERTA = Color(0xFFFF515B)
private val VERDE_T = Color(0xFF70FF93)
private val REJILLA: Color get() = tinteMorado(Color(0xFF6F4B94))
private const val TOPE_T = 12_000f

/** Fracciones del concepto: x de 0 a 1 a lo ancho de la pantalla, y de 0 a 1 a lo alto (sin su barra de arriba). */
private class GeoTel(val s: Size) {
    private val desde = 0.10f
    private val hasta = 0.975f
    val hd = min(s.height / (hasta - desde), s.width / 2.37f)
    val wd = hd * 2.37f
    val extra = max(0f, s.width - wd)
    val oy = -desde * hd
    fun x(f: Float): Float = when {
        f <= 0.18f -> f * wd
        f >= 0.84f -> f * wd + extra
        else -> 0.18f * wd + (f - 0.18f) / 0.66f * (0.66f * wd + extra)
    }
    fun y(f: Float) = oy + f * hd
    fun p(fx: Float, fy: Float) = Offset(x(fx), y(fy))
    fun r(x0: Float, y0: Float, x1: Float, y1: Float) = Rect(x(x0), y(y0), x(x1), y(y1))
    val u = hd / 100f

    // Barra de RPM (como en el concepto): sube en rampa al principio y luego sigue recta hasta el final
    val rpmX0 = x(0.025f); val rpmX1 = x(0.705f)
    val rpmBase = y(0.295f)
    val rpmAltoMin = hd * 0.012f; val rpmAltoMax = hd * 0.085f
    private val finRampa = 0.30f
    fun rpmX(t: Float) = rpmX0 + (rpmX1 - rpmX0) * t
    fun rpmAlto(t: Float): Float {
        val f = (t / finRampa).coerceIn(0f, 1f)
        val suave = f * f * (3f - 2f * f)
        return rpmAltoMin + (rpmAltoMax - rpmAltoMin) * suave
    }

    val temps = List(3) { i -> r(0.020f, 0.335f + i * 0.120f, 0.165f, 0.445f + i * 0.120f) }
    val centroTac = Offset(x(0.262f), y(0.505f))
    val radioTac = min(hd * 0.185f, (x(0.355f) - x(0.170f)) * 0.5f)
    val centroVel = Offset(x(0.485f), y(0.505f))
    val centroG = Offset(x(0.745f), y(0.485f))
    val radioG = min(hd * 0.175f, (x(0.83f) - x(0.66f)) * 0.5f)
    val llanta = r(0.876f, 0.335f, 0.918f, 0.665f)
    val barraIncl = r(0.928f, 0.335f, 0.945f, 0.665f)
    // Fila de abajo: los lados más abajo y el de en medio más arriba (escalón a 45°, como en el concepto)
    val viaje = r(0.020f, 0.755f, 0.355f, 0.972f)
    val volt = r(0.380f, 0.700f, 0.570f, 0.880f)
    val sector = r(0.595f, 0.755f, 0.935f, 0.972f)
}

/** Máximos de fuerza G del viaje: acelerando, frenando y de lado (de la inclinación). */
private class PicosRadar {
    var acel = 0f; var freno = 0f; var lado = 0f
    fun anotar(aFwd: Float, incl: Float) {
        if (aFwd > acel) acel = aFwd
        if (-aFwd > freno) freno = -aFwd
        if (incl > 0f) { val l = tan(Math.toRadians(incl.coerceAtMost(60f).toDouble())).toFloat(); if (l > lado) lado = l }
    }
}

@Composable
internal fun TableroTelemetriaMorada() {
    val rS = Moto.rapida.enVivo()
    val vjS = Moto.viaje.enVivo()
    val cfgS = Moto.config.enVivo()
    val batS = BateriaTel.estado.enVivo()
    val climaS = ClimaManager.clima.enVivo()
    val rutaS = UbicacionManager.recorrido.enVivo()
    val eS = Moto.estado.enVivo()
    val rpm = rpmSuave()
    val velS = velocidadAnimadaEstado()
    val arrS = LocalArranque.current
    val medidor = rememberTextMeasurer(cacheSize = 64)
    val picos = remember { PicosRadar() }
    val geo = remember { arrayOfNulls<GeoTel>(1) }
    Box(
        Modifier.fillMaxSize().animacionSwitch(arrS, PRI)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { Apariencia.siguienteTablero() }) { pos ->
                    val g = geo[0] ?: return@detectTapGestures
                    if (g.viaje.contains(pos) || g.sector.contains(pos)) Lanzador.irA(PAGINA_MOTO)
                }
            }
            .graphicsLayer()
            .drawWithCache {
                val g = GeoTel(size)
                geo[0] = g
                val fijo = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(fijo), size) { fijoTel(g, medidor) }
                val p = Path()
                onDrawBehind {
                    drawImage(fijo)
                    val r = rS.value
                    val prueba = pruebaSegmentos(arrS.value)
                    picos.anotar(r.aFwd, r.incl)
                    datosTel(g, medidor, p, prueba, r.marcha, rpm.value, r.shift, if (prueba) 888f else velS.value, r.temp,
                        batS.value.temperatura, cfgS.value?.tempChip ?: 0f, picos, r.aFwd, r.incl, r.vMoto, vjS.value.km,
                        vjS.value.odometro, vjS.value.velMax, vjS.value.kmPorLitro, climaS.value?.temp, climaS.value?.sensacion,
                        rutaS.value, eS.value.alerta.isNotBlank(), batS.value.pct, r.gas)
                }
            },
    )
}

// ============================================================
// Capa fija
// ============================================================
private fun DrawScope.fijoTel(g: GeoTel, m: TextMeasurer) {
    val u = g.u
    // Viñeta suave (sin rectángulo: se funde con el fondo de la app)
    val cv = g.p(0.5f, 0.5f); val rv = min(g.s.width, g.s.height * 1.6f) * 0.5f
    drawCircle(Brush.radialGradient(0f to SEC.copy(alpha = 0.09f), 0.6f to SEC.copy(alpha = 0.03f), 1f to Color.Transparent, center = cv, radius = rv), rv, cv)

    // ---- Barra de RPM: cuña apagada, escala y separador ----
    val cuna = Path().apply {
        moveTo(g.rpmX0, g.rpmBase)
        for (k in 0..60) { val t = k / 60f; lineTo(g.rpmX(t), g.rpmBase - g.rpmAlto(t)) }
        lineTo(g.rpmX1, g.rpmBase); close()
    }
    drawPath(cuna, SEC.copy(alpha = 0.06f))
    val linea = Path().apply { moveTo(g.x(0.02f), g.rpmBase + u * 0.8f); lineTo(g.x(0.71f), g.rpmBase + u * 0.8f) }
    halo(linea, PRI, u * 0.8f, u * 1.2f); drawPath(linea, PRI, style = Stroke(u * 0.3f))
    val escala = listOf(1, 2, 4, 6, 8, 10, 12)
    escala.forEach { k ->
        val t = k / 12f
        val x = g.rpmX(t)
        drawLine(GRIS_T, Offset(x, g.rpmBase - g.rpmAlto(t) - u * 1.2f), Offset(x, g.rpmBase - g.rpmAlto(t) - u * 0.3f), u * 0.25f)
        texto(m, "$k", Offset(x, g.rpmBase - g.rpmAlto(t) - u * 3.6f), estiloM(u * 3.4f, if (k >= 12) ALERTA else BLANCO_T, d = this), alinear = 0.5f)
    }
    texto(m, "RPM", g.p(0.028f, 0.335f), estiloM(u * 3.2f, GRIS_T, d = this))

    // ---- Columna de temperaturas ----
    val etiquetas = listOf("MOTOR T.", "CEL T.", "ESP T.")
    g.temps.forEachIndexed { i, r ->
        if (i > 0) drawLine(SEC.copy(alpha = 0.55f), Offset(r.left, r.top), Offset(r.right, r.top), u * 0.25f)
        termometro(Offset(r.left + u * 2.2f, r.center.y), u * 1.5f, ALERTA)
        texto(m, etiquetas[i], Offset(r.left + u * 5f, r.top + r.height * 0.30f), estiloM(u * 2.8f, GRIS_T, d = this))
    }
    val div = Path().apply { moveTo(g.x(0.168f), g.y(0.34f)); lineTo(g.x(0.168f), g.y(0.69f)) }
    drawPath(div, PRI.copy(alpha = 0.7f), style = Stroke(u * 0.3f))

    // ---- Tacómetro circular ----
    val c = g.centroTac; val rr = g.radioTac
    drawCircle(Color(0xFF05020A), rr * 1.02f, c)
    drawCircle(Brush.radialGradient(listOf(PRI.copy(alpha = 0.0f), PRI.copy(alpha = 0.25f)), center = c, radius = rr * 1.05f), rr * 1.05f, c, style = Stroke(u * 1.0f))
    // marcas finas y números (0 abajo a la izquierda, 12 abajo a la derecha: 270° de barrido)
    for (i in 0..48) {
        val t = i / 48f
        val a = Math.toRadians(135.0 + 270.0 * t)
        val largo = if (i % 4 == 0) rr * 0.12f else rr * 0.06f
        val col = if (t > 10f / 12f) ALERTA else GRIS_T.copy(alpha = if (i % 4 == 0) 0.9f else 0.5f)
        drawLine(col, Offset(c.x + (rr * 0.80f) * cos(a).toFloat(), c.y + (rr * 0.80f) * sin(a).toFloat()),
            Offset(c.x + (rr * 0.80f - largo) * cos(a).toFloat(), c.y + (rr * 0.80f - largo) * sin(a).toFloat()), u * if (i % 4 == 0) 0.35f else 0.2f)
    }
    for (k in 0..12 step 2) {
        val a = Math.toRadians(135.0 + 270.0 * k / 12.0)
        val pos = Offset(c.x + rr * 0.56f * cos(a).toFloat(), c.y + rr * 0.56f * sin(a).toFloat())
        texto(m, "$k", pos, estiloM(u * 3.0f, if (k >= 10) ALERTA else tinteMorado(Color(0xFFD9CBEA)), Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f)
    }
    texto(m, "GEAR", Offset(c.x, c.y + rr * 0.62f), estiloM(u * 2.6f, tinteMorado(Color(0xFFD4B8FF)), d = this), alinear = 0.5f)

    // ---- Velocidad: unidad ----
    texto(m, "KM/H", Offset(g.centroVel.x, g.y(0.635f)), estiloM(u * 5.0f, tinteMorado(Color(0xFFE5D9F4)), Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f)

    // ---- Radar G: rombos concéntricos y ejes ----
    val cg = g.centroG; val rg = g.radioG
    for (k in 1..3) {
        val q = rg * k / 3f
        val rombo = Path().apply { moveTo(cg.x, cg.y - q); lineTo(cg.x + q, cg.y); lineTo(cg.x, cg.y + q); lineTo(cg.x - q, cg.y); close() }
        drawPath(rombo, REJILLA, style = Stroke(u * 0.22f))
    }
    drawCircle(REJILLA, rg * 0.66f, cg, style = Stroke(u * 0.18f))
    drawLine(REJILLA, Offset(cg.x - rg, cg.y), Offset(cg.x + rg, cg.y), u * 0.2f)
    drawLine(REJILLA, Offset(cg.x, cg.y - rg), Offset(cg.x, cg.y + rg), u * 0.2f)
    val ejes = estiloM(u * 2.8f, BLANCO_T, d = this)
    texto(m, "+1g", Offset(cg.x, cg.y - rg - u * 2.4f), ejes, alinear = 0.5f)
    texto(m, "-1g", Offset(cg.x, cg.y + rg + u * 2.4f), ejes, alinear = 0.5f)
    texto(m, "1g", Offset(cg.x + rg + u * 1.2f, cg.y), ejes)
    texto(m, "1g", Offset(cg.x - rg - u * 1.2f, cg.y), ejes, alinear = 1f)
    texto(m, "G-FORCE", g.p(0.655f, 0.69f), estiloM(u * 3.0f, tinteMorado(Color(0xFFEAE0F7)), d = this))

    // ---- Medidor vertical (inclinación) ----
    texto(m, "INCLINACIÓN", g.p(0.905f, 0.255f), estiloM(u * 2.9f, BLANCO_T, d = this), alinear = 0.5f)
    run {
        val r = g.llanta
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF1A1420), Color(0xFF3A3242), Color(0xFF15101B)), r.left, r.right), r.topLeft, r.size, CornerRadius(r.width * 0.35f))
        for (k in 1 until 14) {
            val y = r.top + r.height * k / 14f
            drawLine(Color(0xFF0B0810), Offset(r.left + r.width * 0.15f, y), Offset(r.right - r.width * 0.15f, y - r.height * 0.02f), u * 0.4f)
        }
        drawLine(Color.White.copy(alpha = 0.25f), Offset(r.left + r.width * 0.3f, r.top + r.height * 0.08f), Offset(r.left + r.width * 0.3f, r.bottom - r.height * 0.08f), u * 0.4f)
        val b = g.barraIncl
        drawRoundRect(Color(0xFF17111F), b.topLeft, b.size, CornerRadius(u * 0.8f))
        for (k in 0..10) { val y = b.top + b.height * k / 10f; drawLine(GRIS_T.copy(alpha = 0.5f), Offset(b.right + u * 0.3f, y), Offset(b.right + u * 1.1f, y), u * 0.2f) }
    }

    // ---- Paneles de abajo ----
    val pp = Path()
    listOf(g.viaje, g.volt, g.sector).forEach { r ->
        pp.reset()
        val c2 = u * 1.6f
        pp.moveTo(r.left + c2, r.top); pp.lineTo(r.right - c2, r.top); pp.lineTo(r.right, r.top + c2); pp.lineTo(r.right, r.bottom - c2)
        pp.lineTo(r.right - c2, r.bottom); pp.lineTo(r.left + c2, r.bottom); pp.lineTo(r.left, r.bottom - c2); pp.lineTo(r.left, r.top + c2); pp.close()
        contorno(pp, PRI, u * 0.3f, u, relleno = PANEL_T)
    }
    // Panel del viaje: esquema de la moto (dos pares de bloques verdes) y títulos
    run {
        val r = g.viaje
        texto(m, "TRIP · TELEMETRÍA", Offset(r.left + r.width * 0.58f, r.top + r.height * 0.17f), estiloM(u * 3.0f, BLANCO_T, d = this), alinear = 0.5f)
        val cx = r.left + r.width * 0.13f; val cy = r.center.y + r.height * 0.06f
        drawRoundRect(GRIS_T.copy(alpha = 0.5f), Offset(cx - u * 1.1f, cy - r.height * 0.30f), Size(u * 2.2f, r.height * 0.60f), CornerRadius(u * 1f), style = Stroke(u * 0.3f))
        for (fila in 0..1) for (lado in listOf(-1, 1)) {
            val bx = cx + lado * u * 3.6f - u * 1.1f
            val by = cy + (if (fila == 0) -1 else 1) * r.height * 0.20f - r.height * 0.11f
            drawRoundRect(VERDE_T, Offset(bx, by), Size(u * 2.2f, r.height * 0.22f), CornerRadius(u * 0.6f))
            drawRoundRect(Color(0xFFB1FFB9), Offset(bx, by), Size(u * 2.2f, r.height * 0.22f), CornerRadius(u * 0.6f), style = Stroke(u * 0.2f))
            for (k in 1..3) drawLine(Color(0xFF1B3A22), Offset(bx + u * 0.3f, by + r.height * 0.22f * k / 4f), Offset(bx + u * 1.9f, by + r.height * 0.22f * k / 4f), u * 0.15f)
        }
        texto(m, "F", Offset(cx - u * 6.2f, cy - r.height * 0.20f), estiloM(u * 2.6f, BLANCO_T, d = this), alinear = 0.5f)
        texto(m, "R", Offset(cx - u * 6.2f, cy + r.height * 0.20f), estiloM(u * 2.6f, BLANCO_T, d = this), alinear = 0.5f)
    }
    texto(m, "VOLTAJE MOTO", Offset(g.volt.center.x, g.volt.top + g.volt.height * 0.24f), estiloM(u * 3.0f, tinteMorado(Color(0xFFE8DDF5)), d = this), alinear = 0.5f)
    texto(m, "TEMP AIRE", Offset(g.sector.right - u * 2.5f, g.sector.top + g.sector.height * 0.22f), estiloM(u * 2.6f, BLANCO_T, d = this), alinear = 1f)
    texto(m, "SENSACIÓN", Offset(g.sector.right - u * 2.5f, g.sector.top + g.sector.height * 0.60f), estiloM(u * 2.6f, BLANCO_T, d = this), alinear = 1f)

    // Contorno escalonado sobre la fila de abajo: horizontal, sube a 45°, descansa, baja a 45°, horizontal
    run {
        val yLado = g.y(0.735f); val yMedio = g.y(0.680f)
        val d = yLado - yMedio
        val xa = g.volt.left - u * 1.5f; val xb = g.volt.right + u * 1.5f
        val esc = Path().apply {
            moveTo(g.x(0.02f), yLado); lineTo(xa - d, yLado); lineTo(xa, yMedio); lineTo(xb, yMedio); lineTo(xb + d, yLado); lineTo(g.x(0.935f), yLado)
        }
        halo(esc, PRI, u * 0.8f, u * 1.2f)
        drawPath(esc, PRI.copy(alpha = 0.85f), style = Stroke(u * 0.28f, join = StrokeJoin.Miter))
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

private fun DrawScope.termometro(c: Offset, t: Float, col: Color) {
    drawLine(col, Offset(c.x, c.y - t * 1.1f), Offset(c.x, c.y + t * 0.4f), t * 0.45f, StrokeCap.Round)
    drawCircle(col, t * 0.55f, Offset(c.x, c.y + t * 0.75f))
    for (k in 0..2) drawLine(col, Offset(c.x + t * 0.45f, c.y - t * 0.8f + k * t * 0.4f), Offset(c.x + t * 0.8f, c.y - t * 0.8f + k * t * 0.4f), t * 0.15f)
}

// ============================================================
// Lo que cambia
// ============================================================
private fun DrawScope.datosTel(
    g: GeoTel, m: TextMeasurer, p: Path, prueba: Boolean, marcha: Int, rpm: Float, shift: Int, vel: Float, temp: Float,
    tempCel: Float, tempChip: Float, picos: PicosRadar, aFwd: Float, incl: Float, volt: Float, trip: Float, odo: Float,
    velMax: Float, kmL: Float, aire: Int?, sensacion: Int?, ruta: List<org.osmdroid.util.GeoPoint>, alerta: Boolean,
    batPct: Int, gas: Int,
) {
    val u = g.u
    val t = (rpm / TOPE_T).coerceIn(0f, 1f)
    val fCambio = if (shift > 0) (shift / TOPE_T).coerceIn(0f, 1f) else 0.83f
    // Zona roja fija desde 10 000 (como el bloque rojo del concepto); el punto de cambio lo marca el triángulo
    val fRoja = 10f / 12f

    // ---- Barra de RPM: segmentos verticales densos en la cuña ----
    val segs = 110
    val w = (g.rpmX1 - g.rpmX0) / segs
    val prendidos = (t * segs).toInt()
    for (i in 0 until segs) {
        val f = (i + 0.5f) / segs
        val x = g.rpmX0 + i * w
        val h = g.rpmAlto(f)
        // encendidos: morado que se aclara a magenta; apagados: lavanda tenue; zona roja: rojo oscuro (encendida, rojo)
        val col = when {
            i >= prendidos -> if (f >= fRoja) Color(0xFF6A1F33) else tinteMorado(Color(0xFF9C8CB8)).copy(alpha = 0.30f)
            f >= fRoja -> ALERTA
            f >= fCambio -> tinteMorado(Color(0xFFFF7AD9))
            else -> lerpColor(Color(0xFF8D37FF), tinteMorado(Color(0xFFE04DFF)), (f / fCambio).coerceIn(0f, 1f))
        }
        drawRect(col, Offset(x + w * 0.12f, g.rpmBase - h), Size(w * 0.76f, h))
    }
    // marca roja del punto de cambio
    val xc = g.rpmX(fCambio)
    val tri = Path().apply { moveTo(xc, g.rpmBase + u * 1.4f); lineTo(xc - u * 1.0f, g.rpmBase + u * 3.0f); lineTo(xc + u * 1.0f, g.rpmBase + u * 3.0f); close() }
    drawPath(tri, ALERTA)

    // ---- Temperaturas ----
    val valores = listOf(temp.takeIf { it > -90f }, tempCel.takeIf { it > 0f }, tempChip.takeIf { it > 0f })
    g.temps.forEachIndexed { i, r ->
        val v = valores[i]
        val col = when { v == null -> GRIS_T; i == 0 && v >= 105f -> ALERTA; else -> BLANCO_T }
        val res = m.measure(if (v == null) "--" else "${v.toInt()}", estiloM(u * 5.4f, col, Fuentes.logo, FontWeight.Normal, d = this))
        val x = r.left + u * 5f; val y = r.top + r.height * 0.70f
        drawText(res, topLeft = Offset(x, y - res.size.height / 2f))
        texto(m, "°C", Offset(x + res.size.width + u * 0.5f, y + u * 0.4f), estiloM(u * 3.0f, BLANCO_T, d = this))
    }

    // ---- Tacómetro circular: arco de color y la marcha adentro ----
    val c = g.centroTac; val rr = g.radioTac
    val caja = Size(rr * 1.84f, rr * 1.84f); val esq = Offset(c.x - rr * 0.92f, c.y - rr * 0.92f)
    drawArc(SEC.copy(alpha = 0.15f), 135f, 270f, false, esq, caja, style = Stroke(rr * 0.10f))
    if (t > 0f) {
        val brocha = Brush.sweepGradient(0f to Color(0xFF39E07A), 0.30f to Color(0xFF2E9BFF), 0.55f to PRI, 0.75f to ROSA, 1f to ALERTA, center = c)
        drawArc(brocha, 135f, 270f * t, false, esq, caja, style = Stroke(rr * 0.10f, cap = StrokeCap.Butt))
        val a = Math.toRadians(135.0 + 270.0 * t)
        val pt = Offset(c.x + rr * 0.92f * cos(a).toFloat(), c.y + rr * 0.92f * sin(a).toFloat())
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Transparent), center = pt, radius = rr * 0.14f), rr * 0.14f, pt)
    }
    // zona roja exterior
    drawArc(ALERTA, 135f + 270f * fRoja, 270f * (1f - fRoja), false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(u * 0.6f))
    val txtM = when { prueba -> "8"; marcha < 0 -> "-"; marcha == 0 -> "N"; else -> "$marcha" }
    texto(m, txtM, Offset(c.x, c.y - rr * 0.02f), estiloM(rr * 0.78f, tinteMorado(Color(0xFFF3E9FF)), Fuentes.logo, FontWeight.Normal, brillo = PRI, radio = u * 3f, d = this), alinear = 0.5f)
    // círculo interior
    drawCircle(PRI.copy(alpha = 0.55f), rr * 0.42f, c, style = Stroke(u * 0.3f))

    // ---- Velocidad: la más grande, sin tarjeta ----
    val txtV = when { prueba -> "888"; vel < 0f -> "--"; else -> "${vel.toInt()}" }
    texto(m, txtV, g.centroVel, estiloM(u * 21f, tinteMorado(Color(0xFFF8F4FF)), Fuentes.logo, FontWeight.Normal, espacio = 2f, brillo = PRI.copy(alpha = 0.55f), radio = u * 3f, d = this), alinear = 0.5f)

    // ---- Radar G: máximos del viaje (rombo morado) y el punto actual ----
    val cg = g.centroG; val rg = g.radioG
    fun esc(v: Float) = (v / 1.0f).coerceIn(0.05f, 1f) * rg
    val arriba = esc(picos.acel); val abajo = esc(picos.freno); val lado = esc(picos.lado)
    p.reset()
    // forma de estrella: entre cada eje se hunde hacia el centro (como el trazo del concepto)
    val pts = listOf(Offset(cg.x, cg.y - arriba), Offset(cg.x + lado, cg.y), Offset(cg.x, cg.y + abajo), Offset(cg.x - lado, cg.y))
    for (i in 0 until 4) {
        val a = pts[i]; val b = pts[(i + 1) % 4]
        if (i == 0) p.moveTo(a.x, a.y)
        val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        val dentro = Offset(cg.x + (mid.x - cg.x) * 0.35f, cg.y + (mid.y - cg.y) * 0.35f)
        p.quadraticTo(dentro.x, dentro.y, b.x, b.y)
    }
    p.close()
    drawPath(p, SEC.copy(alpha = 0.22f))
    drawPath(p, ROSA.copy(alpha = 0.35f), style = Stroke(u * 1.6f, join = StrokeJoin.Round))
    drawPath(p, tinteMorado(Color(0xFFF05CFF)), style = Stroke(u * 0.4f, join = StrokeJoin.Round))
    // punto actual: aceleración (+ arriba / frenado abajo); el lado no se sabe (la inclinación no trae signo)
    val punto = Offset(cg.x, cg.y - aFwd.coerceIn(-1f, 1f) * rg)
    drawCircle(Color.White, u * 0.9f, punto)
    drawCircle(ROSA.copy(alpha = 0.4f), u * 2.0f, punto)

    // ---- Medidor vertical: inclinación (0–60°) ----
    run {
        val b = g.barraIncl
        val f = if (incl >= 0f) (incl / 60f).coerceIn(0f, 1f) else 0f
        val n = 12
        for (k in 0 until n) {
            val on = k < (f * n + 0.5f).toInt()
            val y = b.bottom - (k + 1) * b.height / n
            val col = if (!on) tinteMorado(Color(0xFF241B2E)) else if (k >= n - 3) ALERTA else tinteMorado(Color(0xFFD9CDE7))
            drawRoundRect(col, Offset(b.left + u * 0.3f, y + u * 0.25f), Size(b.width - u * 0.6f, b.height / n - u * 0.5f), CornerRadius(u * 0.3f))
        }
        val yM = b.bottom - b.height * f
        val tri = Path().apply { moveTo(b.right + u * 1.3f, yM); lineTo(b.right + u * 2.8f, yM - u * 1.0f); lineTo(b.right + u * 2.8f, yM + u * 1.0f); close() }
        drawPath(tri, Color.White)
        texto(m, if (incl >= 0f) "${incl.toInt()}°" else "--", Offset(b.center.x, b.bottom + u * 2.6f), estiloM(u * 2.8f, BLANCO_T, d = this), alinear = 0.5f)
    }

    // ---- Panel del viaje ----
    run {
        val r = g.viaje
        val et = estiloM(u * 3.0f, GRIS_T, d = this)
        val va = estiloM(u * 3.9f, BLANCO_T, Fuentes.logo, FontWeight.Normal, d = this)
        val x1 = r.left + r.width * 0.27f; val x2 = r.left + r.width * 0.63f
        val y1 = r.top + r.height * 0.45f; val y2 = r.top + r.height * 0.75f
        fun dato(x: Float, y: Float, e: String, v: String) {
            val re = m.measure(e, et); drawText(re, topLeft = Offset(x, y - re.size.height / 2f))
            texto(m, v, Offset(x + re.size.width + u * 1.0f, y), va)
        }
        dato(x1, y1, "TRIP:", "${fmt(max(trip, 0f), 1)}km")
        dato(x2, y1, "ODO:", "${max(odo, 0f).toInt()}")
        dato(x1, y2, "MÁX:", "${max(velMax, 0f).toInt()}km/h")
        dato(x2, y2, "KM/L:", if (kmL > 0f) fmt(kmL, 1) else "--")
    }

    // ---- Voltaje (el número manda) ----
    run {
        val r = g.volt
        val col = if (volt in 0f..11.8f) ALERTA else Color(0xFFF9F4FF)
        val res = m.measure(if (volt >= 0f) fmt(volt, 2) else "--.--", estiloM(u * 8f, col, Fuentes.logo, FontWeight.Normal, d = this))
        val x = r.center.x - (res.size.width + u * 3f) / 2f; val y = r.top + r.height * 0.66f
        drawText(res, topLeft = Offset(x, y - res.size.height / 2f))
        texto(m, "V", Offset(x + res.size.width + u * 0.6f, y + u * 0.8f), estiloM(u * 4f, BLANCO_T, d = this))
    }

    // ---- Ruta de hoy como circuito ----
    run {
        val r = g.sector
        texto(m, "RUTA HOY", Offset(r.left + u * 2.5f, r.top + r.height * 0.20f), estiloM(u * 3.6f, VERDE_T, Fuentes.logo, FontWeight.Normal, d = this))
        val zona = Rect(r.left + u * 2.5f, r.top + r.height * 0.36f, r.left + r.width * 0.62f, r.bottom - r.height * 0.12f)
        val pts = ruta.takeLast(600)
        if (pts.size >= 2) {
            var la0 = Double.MAX_VALUE; var la1 = -Double.MAX_VALUE; var lo0 = Double.MAX_VALUE; var lo1 = -Double.MAX_VALUE
            pts.forEach { q -> la0 = min(la0, q.latitude); la1 = max(la1, q.latitude); lo0 = min(lo0, q.longitude); lo1 = max(lo1, q.longitude) }
            val sx = zona.width / max(1e-6, lo1 - lo0).toFloat(); val sy = zona.height / max(1e-6, la1 - la0).toFloat()
            val sc = min(sx, sy)
            val ox = zona.left + (zona.width - ((lo1 - lo0) * sc).toFloat()) / 2f
            val oy2 = zona.top + (zona.height - ((la1 - la0) * sc).toFloat()) / 2f
            p.reset()
            pts.forEachIndexed { i, q ->
                val x = ox + ((q.longitude - lo0) * sc).toFloat()
                val y = oy2 + ((la1 - q.latitude) * sc).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, Color(0xFFE7E0F2), style = Stroke(u * 0.4f, join = StrokeJoin.Round, cap = StrokeCap.Round))
            val ult = pts.last()
            val xu = ox + ((ult.longitude - lo0) * sc).toFloat(); val yu = oy2 + ((la1 - ult.latitude) * sc).toFloat()
            drawCircle(VERDE_T, u * 0.9f, Offset(xu, yu))
            drawCircle(VERDE_T.copy(alpha = 0.3f), u * 2.0f, Offset(xu, yu))
        } else {
            texto(m, "Sin recorrido todavía", zona.center, estiloM(u * 2.6f, GRIS_T, d = this), alinear = 0.5f)
        }
        val v = estiloM(u * 3.8f, BLANCO_T, Fuentes.logo, FontWeight.Normal, d = this)
        texto(m, aire?.let { "$it°C" } ?: "--°C", Offset(r.right - u * 2.5f, r.top + r.height * 0.40f), v, alinear = 1f)
        texto(m, sensacion?.let { "$it°C" } ?: "--°C", Offset(r.right - u * 2.5f, r.top + r.height * 0.78f), v, alinear = 1f)
    }

    // ---- Franja de estado ----
    run {
        val y = g.volt.bottom + (g.viaje.bottom - g.volt.bottom) * 0.55f
        var x = g.volt.left + u * 1.5f
        val col = if (alerta) ALERTA else GRIS_T.copy(alpha = 0.5f)
        val triA = Path().apply { moveTo(x, y - u * 1.3f); lineTo(x + u * 1.4f, y + u * 1.1f); lineTo(x - u * 1.4f, y + u * 1.1f); close() }
        drawPath(triA, col)
        x += u * 4.5f
        termometro(Offset(x, y - u * 0.3f), u * 1.0f, if (temp >= 105f) ALERTA else GRIS_T.copy(alpha = 0.6f))
        x += u * 4f
        texto(m, if (gas >= 0) "GAS $gas%" else "GAS --", Offset(x, y), estiloM(u * 2.4f, if (gas in 0..15) ALERTA else Color(0xFFEDE4F8), d = this))
        x += u * 11f
        drawRoundRect(VERDE_T, Offset(x, y - u * 1.0f), Size(u * 4f * (batPct.coerceIn(0, 100) / 100f), u * 2f), CornerRadius(u * 0.4f))
        drawRoundRect(VERDE_T, Offset(x, y - u * 1.0f), Size(u * 4f, u * 2f), CornerRadius(u * 0.4f), style = Stroke(u * 0.25f))
        texto(m, if (batPct >= 0) "$batPct%" else "--", Offset(x + u * 5f, y), estiloM(u * 2.4f, VERDE_T, d = this))
    }
}

// ============================================================
// Vista para Ajustes
// ============================================================
internal fun DrawScope.vistaTelemetriaMorada() {
    drawRect(FONDO_T)
    val w = size.width; val h = size.height
    for (i in 0 until 40) {
        val f = i / 40f
        drawRect(if (f < 0.6f) PRI else if (f < 0.8f) ROSA else ALERTA.copy(alpha = 0.4f),
            Offset(w * (0.03f + f * 0.66f), h * (0.24f - 0.10f * f)), Size(w * 0.012f, h * (0.04f + 0.10f * f)))
    }
    val c = Offset(w * 0.26f, h * 0.52f); val r = h * 0.20f
    drawArc(Brush.sweepGradient(listOf(Color(0xFF39E07A), PRI, ALERTA), center = c), 135f, 200f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(h * 0.04f))
    numero7(5f, 1, 0, c.x - w * 0.02f, c.y - h * 0.09f, w * 0.035f, h * 0.18f, w * 0.05f, BLANCO_T, Color.Transparent, Path())
    numero7(168f, 3, 0, w * 0.40f, h * 0.38f, w * 0.04f, h * 0.22f, w * 0.06f, BLANCO_T, Color.Transparent, Path())
    val cg = Offset(w * 0.75f, h * 0.48f); val q = h * 0.18f
    val rombo = Path().apply { moveTo(cg.x, cg.y - q); lineTo(cg.x + q, cg.y); lineTo(cg.x, cg.y + q); lineTo(cg.x - q, cg.y); close() }
    drawPath(rombo, ROSA, style = Stroke(h * 0.012f))
    repeat(3) { i ->
        drawRoundRect(PRI, Offset(w * (0.03f + i * 0.32f), h * 0.76f), Size(w * 0.29f, h * 0.18f), CornerRadius(h * 0.02f), style = Stroke(h * 0.012f))
    }
    drawRoundRect(Color(0xFFD9CDE7), Offset(w * 0.90f, h * 0.30f), Size(w * 0.02f, h * 0.36f), CornerRadius(h * 0.01f))
}
