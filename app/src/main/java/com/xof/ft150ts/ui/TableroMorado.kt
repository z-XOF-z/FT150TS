package com.xof.ft150ts.ui

import android.graphics.BlurMaskFilter
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.Moto
import com.xof.ft150ts.MusicaManager
import com.xof.ft150ts.PAGINA_APPS
import com.xof.ft150ts.PAGINA_MOTO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Tablero "Racing Morado" (v3.10): recreación fiel del concepto de Andrew (el morado con "145 · 3 GEAR", junto con su
 * especificación: fondo #080510, primario #C42CFF, secundario #8D37FF, acento #FF45E8, texto #F7F2FF, apagado #A69AB8).
 *
 * Pantalla completa (sin el riel de accesos):
 *  - Arriba: la barra del concepto (FT150TS, hora, señal, batería) la pone la app con el tema Racing Morado; aquí no
 *    se repite y el tablero usa ese espacio.
 *  - Izquierda: gasolina, MODO (RACE en Sport, ECO en Eco) y la cruceta: ◀◀ / ▶▶ canción, ▲ / ▼ volumen, centro pausa.
 *  - Centro: tacómetro de barrido 0–12 (×1000) con marcas finas, la velocidad grande en itálica, la marcha gigante con
 *    "GEAR" y flechas de cambio, y el ecualizador (las RPM recientes).
 *  - Abajo: TEMP MOTOR, G-FORCE (máximo acelerando y frenando de este viaje), LAP DELTA y BEST LAP (todavía sin
 *    cronómetro de vueltas: "--"), y la franja con motor, voltaje y gasolina.
 *  - Derecha: mapa, moto, ajustes, cronómetro (arrancones), apps y estadísticas.
 *
 * Calidad "de revista" sin costo por cuadro: todo lo fijo (marcos, botones, íconos, números, brillos con desenfoque de
 * verdad) se pinta UNA vez por tamaño en una imagen; en cada cuadro solo se dibujan el arco encendido, la punta, los
 * números grandes y los datos.
 */
private val FONDO: Color get() = tinteMorado(Color(0xFF080510))
private val PRIMARIO: Color get() = tinteMorado(Color(0xFFC42CFF))
private val SECUNDARIO: Color get() = tinteMorado(Color(0xFF8D37FF))
private val ACENTO: Color get() = tinteMorado(Color(0xFFFF45E8))
private val TEXTO = Color(0xFFF7F2FF)
private val APAGADO: Color get() = tinteMorado(Color(0xFFA69AB8))
private val ROJO_FIN = Color(0xFFFF3B5C)
private val PANEL: Color get() = tinteMorado(Color(0xFF0E0819))
private val TECLA = Color(0xFF221D2B)
private val TECLA_BORDE = Color(0xFF3A3445)
private const val TOPE = 12_000f
private const val PAG_AUTO_M = 2
private const val PAG_AJUSTES_M = 4

/** Diseño en fracciones (x de 0 a 1 a lo ancho de la pantalla del concepto, y de 0 a 1 a lo alto). */
private class GeoMorado(val s: Size, margenInf: Float) {
    /**
     * Parte del concepto que se dibuja: desde debajo de su barra de arriba (esa la pone la app) hasta la franja de
     * estado. Llena todo el alto disponible (menos [margenInf]).
     */
    private val desde = 0.12f
    private val hasta = 0.875f
    val hd = min((s.height - margenInf) / (hasta - desde), s.width / 2.04f)
    val wd = hd * 2.04f
    val ox = 0f
    val oy = -desde * hd
    val extra = max(0f, s.width - wd)
    /** Los lados se quedan pegados a sus orillas y el centro se estira con lo que sobra de ancho. */
    fun x(f: Float): Float = when {
        f <= 0.16f -> ox + f * wd
        f >= 0.84f -> ox + f * wd + extra
        else -> ox + 0.16f * wd + (f - 0.16f) / 0.68f * (0.68f * wd + extra)
    }
    fun y(f: Float) = oy + f * hd
    fun p(fx: Float, fy: Float) = Offset(x(fx), y(fy))
    fun r(x0: Float, y0: Float, x1: Float, y1: Float) = Rect(x(x0), y(y0), x(x1), y(y1))
    val u = hd / 100f

    // ---- Tacómetro: cuarto de elipse (abajo-izquierda → arriba) y luego recto hasta el 12 ----
    val muestras: List<Offset>
    val normales: List<Offset>
    val largoAcum: FloatArray
    val banda = hd * 0.085f
    init {
        val pts = ArrayList<Offset>()
        val cx = 0.335f; val cy = 0.64f; val rx = 0.135f; val ry = 0.38f
        for (k in 0..90) {
            val a = Math.toRadians(180.0 + k).toFloat()
            pts += p(cx + rx * cos(a), cy + ry * sin(a))
        }
        val xa = cx; val xb = 0.775f
        for (k in 1..60) pts += p(xa + (xb - xa) * k / 60f, cy - ry)
        muestras = pts
        val acum = FloatArray(pts.size)
        for (i in 1 until pts.size) acum[i] = acum[i - 1] + (pts[i] - pts[i - 1]).getDistance()
        largoAcum = acum
        normales = pts.indices.map { i ->
            val a = pts[max(0, i - 1)]; val b = pts[min(pts.size - 1, i + 1)]
            val tx = b.x - a.x; val ty = b.y - a.y
            val l = hypot(tx, ty).coerceAtLeast(0.001f)
            Offset(ty / l, -tx / l) * -1f      // hacia adentro (abajo / a la derecha)
        }
    }
    val largo get() = largoAcum.last()

    /** Punto y normal a la fracción [t] (0..1) del largo del arco. */
    fun en(t: Float): Pair<Offset, Offset> {
        val d = t.coerceIn(0f, 1f) * largo
        // Primer tramo que llega a d (búsqueda binaria: se llama cientos de veces por cuadro)
        var i = 1
        var hi = largoAcum.size - 1
        while (i < hi) { val mid = (i + hi) ushr 1; if (largoAcum[mid] < d) i = mid + 1 else hi = mid }
        val d0 = largoAcum[i - 1]; val d1 = largoAcum[i]
        val f = if (d1 > d0) (d - d0) / (d1 - d0) else 0f
        val pt = muestras[i - 1] + (muestras[i] - muestras[i - 1]) * f
        val n = normales[i - 1] + (normales[i] - normales[i - 1]) * f
        val nl = n.getDistance().coerceAtLeast(0.001f)
        return pt to Offset(n.x / nl, n.y / nl)
    }

    // ---- Zonas ----
    val combustible = r(0.045f, 0.27f, 0.10f, 0.38f)
    val modo = r(0.045f, 0.48f, 0.135f, 0.58f)
    val cruceta = r(0.055f, 0.61f, 0.150f, 0.79f)
    val mapa = r(0.845f, 0.19f, 0.950f, 0.32f)
    val botones4 = listOf(r(0.845f, 0.35f, 0.893f, 0.455f), r(0.902f, 0.35f, 0.950f, 0.455f),
        r(0.845f, 0.47f, 0.893f, 0.575f), r(0.902f, 0.47f, 0.950f, 0.575f))
    val grafica = r(0.860f, 0.61f, 0.955f, 0.74f)
    val paneles = listOf(r(0.190f, 0.62f, 0.340f, 0.765f), r(0.348f, 0.62f, 0.510f, 0.765f),
        r(0.518f, 0.62f, 0.675f, 0.765f), r(0.683f, 0.62f, 0.840f, 0.765f))
    val ecualizador = r(0.600f, 0.365f, 0.752f, 0.555f)
}

@Composable
internal fun TableroMorado() {
    val rS = Moto.rapida.enVivo()
    val vjS = Moto.viaje.enVivo()
    val eS = Moto.estado.enVivo()
    val histS = Moto.historialRpm.enVivo()
    val rpm = rpmSuave()
    val velS = velocidadAnimadaEstado()
    val arrS = LocalArranque.current
    val medidor = rememberTextMeasurer(cacheSize = 48)
    val picos = remember { PicosG() }
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }
    val geoActual = remember { arrayOfNulls<GeoMorado>(1) }
    Box(
        Modifier.fillMaxSize().animacionSwitch(arrS, PRIMARIO)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { Apariencia.siguienteTablero() }) { pos ->
                    val g = geoActual[0] ?: return@detectTapGestures
                    tocar(g, pos, audio)
                }
            }
            .graphicsLayer()
            .drawWithCache {
                val g = GeoMorado(size, 0f)
                geoActual[0] = g
                val fijo = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(fijo), size) {
                    pintarFijo(g, medidor)
                }
                val p = Path()
                // Rendimiento: las 121 rayitas del arco siempre caen en el mismo lugar; se calculan una vez por tamaño y
                // se dibujan con una sola llamada (antes eran hasta 120 drawLine por cuadro)
                val rayas = FloatArray(121 * 4).also { a ->
                    for (i in 0..120) {
                        val (pt, nn) = g.en(i / 120f)
                        a[i * 4] = pt.x; a[i * 4 + 1] = pt.y; a[i * 4 + 2] = pt.x + nn.x * g.banda; a[i * 4 + 3] = pt.y + nn.y * g.banda
                    }
                }
                val pinturaRayas = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = FONDO.copy(alpha = 0.55f).toArgb(); strokeWidth = g.u * 0.22f
                }
                onDrawBehind {
                    drawImage(fijo)
                    val r = rS.value
                    val prueba = pruebaSegmentos(arrS.value)
                    picos.anotar(r.aFwd)
                    pintarDatos(
                        g, medidor, p, rayas, pinturaRayas, prueba,
                        vel = if (prueba) 888f else velS.value, rpm = rpm.value, shift = r.shift, marcha = r.marcha,
                        temp = r.temp, volt = r.vMoto, gas = r.gas, sport = eS.value.perfil == 1,
                        gMax = picos.max, gMin = picos.min,
                        historial = histS.value,
                    )
                }
            },
    )
}

/** Máximos de fuerza G del viaje (acelerando y frenando). */
private class PicosG {
    var max = 0f
    var min = 0f
    fun anotar(g: Float) { if (g > max) max = g; if (g < min) min = g }
}

private fun tocar(g: GeoMorado, pos: Offset, audio: AudioManager?) {
    fun en(r: Rect) = r.inflate(g.u * 1.5f).contains(pos)
    when {
        en(g.combustible) -> Lanzador.irA(PAGINA_MOTO)
        en(g.mapa) -> Lanzador.irA(PAG_AUTO_M)
        en(g.botones4[0]) -> Lanzador.irA(PAGINA_MOTO)
        en(g.botones4[1]) -> Lanzador.irA(PAG_AJUSTES_M)
        en(g.botones4[2]) -> Lanzador.irA(PAGINA_MOTO)
        en(g.botones4[3]) -> Lanzador.irA(PAGINA_APPS)
        en(g.grafica) -> Lanzador.irA(PAGINA_MOTO)
        en(g.cruceta) -> {
            val c = g.cruceta.center
            val dx = pos.x - c.x; val dy = pos.y - c.y
            val centro = abs(dx) < g.cruceta.width * 0.18f && abs(dy) < g.cruceta.height * 0.18f
            when {
                centro -> MusicaManager.playPausa()
                abs(dx) > abs(dy) -> if (dx < 0) MusicaManager.anterior() else MusicaManager.siguiente()
                else -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    if (dy < 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
        }
    }
}

// ============================================================
// Utilidades de dibujo
// ============================================================
internal fun estiloM(px: Float, color: Color, fuente: FontFamily = Fuentes.ui, peso: FontWeight = FontWeight.SemiBold,
                    espacio: Float = 0f, brillo: Color? = null, radio: Float = 0f, d: DrawScope) =
    TextStyle(color = color, fontSize = with(d) { px.toSp() }, fontFamily = fuente, fontWeight = peso, letterSpacing = espacio.sp,
        shadow = brillo?.let { Shadow(it, Offset.Zero, radio) })

/** Dibuja inclinado (itálica de carreras) alrededor de [c]. */
internal inline fun DrawScope.italica(c: Offset, k: Float = -0.20f, bloque: () -> Unit) {
    val lienzo = drawContext.canvas
    lienzo.save(); lienzo.translate(c.x, c.y); lienzo.skew(k, 0f); lienzo.translate(-c.x, -c.y)
    bloque()
    lienzo.restore()
}

/** Brillo con desenfoque de verdad (solo en la capa fija: se pinta una vez). */
internal fun DrawScope.halo(path: Path, color: Color, ancho: Float, radio: Float, relleno: Boolean = false) {
    drawIntoCanvas { c ->
        val pt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = if (relleno) android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE
            strokeWidth = ancho
            strokeJoin = android.graphics.Paint.Join.ROUND
            maskFilter = BlurMaskFilter(radio.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        }
        c.nativeCanvas.drawPath(path.asAndroidPath(), pt)
    }
}

/** Contorno neón (halo desenfocado + línea nítida). */
internal fun DrawScope.contorno(path: Path, color: Color, ancho: Float, u: Float, relleno: Color? = Color(0xFF0E0819)) {
    if (relleno != null) drawPath(path, relleno)
    halo(path, color.copy(alpha = 0.55f), ancho * 2.2f, u * 1.4f)
    drawPath(path, color, style = Stroke(ancho, join = StrokeJoin.Round))
}

internal fun Path.redondo(r: Rect, rad: Float) { reset(); addRoundRect(RoundRect(r, CornerRadius(rad))) }

internal fun DrawScope.texto(m: TextMeasurer, t: String, en: Offset, estilo: TextStyle, alinear: Float = 0f, alfa: Float = 1f) {
    val res = m.measure(t, estilo)
    drawText(res, topLeft = Offset(en.x - res.size.width * alinear, en.y - res.size.height / 2f), alpha = alfa)
}

// ============================================================
// Capa fija (una vez por tamaño)
// ============================================================
private fun DrawScope.pintarFijo(g: GeoMorado, m: TextMeasurer) {
    val u = g.u
    val linea = u * 0.32f
    // Sin rectángulo de fondo: el tablero se dibuja sobre el fondo de la app (antes se notaba el corte junto a la
    // cámara). La viñeta morada se desvanece por completo antes de llegar a las orillas.
    val cv = g.p(0.5f, 0.45f)
    val rv = min(g.s.width, g.s.height * 1.6f) * 0.5f
    drawCircle(Brush.radialGradient(0f to SECUNDARIO.copy(alpha = 0.10f), 0.6f to SECUNDARIO.copy(alpha = 0.04f), 1f to Color.Transparent,
        center = cv, radius = rv), radius = rv, center = cv)

    // ---- Marco superior con "pestaña" al centro ----
    val marco = Path().apply {
        moveTo(g.x(0.165f), g.y(0.27f)); lineTo(g.x(0.205f), g.y(0.165f)); lineTo(g.x(0.40f), g.y(0.165f))
        lineTo(g.x(0.42f), g.y(0.135f)); lineTo(g.x(0.58f), g.y(0.135f)); lineTo(g.x(0.60f), g.y(0.165f))
        lineTo(g.x(0.795f), g.y(0.165f)); lineTo(g.x(0.835f), g.y(0.27f))
    }
    halo(marco, PRIMARIO.copy(alpha = 0.6f), linea * 2.5f, u * 1.2f)
    drawPath(marco, PRIMARIO, style = Stroke(linea, join = StrokeJoin.Round))
    // muescas decorativas de la pestaña
    for (k in 0 until 5) {
        val x = g.x(0.47f + k * 0.015f)
        drawLine(SECUNDARIO.copy(alpha = 0.8f), Offset(x, g.y(0.145f)), Offset(x + u * 0.8f, g.y(0.155f)), u * 0.35f)
    }
    // marco inferior del centro
    val base = Path().apply {
        moveTo(g.x(0.17f), g.y(0.60f)); lineTo(g.x(0.17f), g.y(0.80f)); lineTo(g.x(0.83f), g.y(0.80f))
    }
    drawPath(base, SECUNDARIO.copy(alpha = 0.35f), style = Stroke(linea * 0.8f))

    // ---- Tacómetro: banda apagada, marcas y números ----
    val banda = Path()
    val n = 160
    for (i in 0..n) { val (pt, _) = g.en(i / n.toFloat()); if (i == 0) banda.moveTo(pt.x, pt.y) else banda.lineTo(pt.x, pt.y) }
    for (i in n downTo 0) { val (pt, nn) = g.en(i / n.toFloat()); banda.lineTo(pt.x + nn.x * g.banda, pt.y + nn.y * g.banda) }
    banda.close()
    drawPath(banda, PRIMARIO.copy(alpha = 0.07f))
    drawPath(banda, SECUNDARIO.copy(alpha = 0.35f), style = Stroke(u * 0.18f))
    // marcas finas por fuera (cada 250 rpm) y largas en cada mil
    for (i in 0..48) {
        val t = i / 48f
        val (pt, nn) = g.en(t)
        val largo = if (i % 4 == 0) u * 2.2f else u * 1.0f
        val col = if (t > 10f / 12f) ACENTO else TEXTO.copy(alpha = if (i % 4 == 0) 0.9f else 0.45f)
        drawLine(col, Offset(pt.x - nn.x * u * 0.6f, pt.y - nn.y * u * 0.6f),
            Offset(pt.x - nn.x * (u * 0.6f + largo), pt.y - nn.y * (u * 0.6f + largo)), u * if (i % 4 == 0) 0.35f else 0.2f)
    }
    for (k in 0..12) {
        val (pt, nn) = g.en(k / 12f)
        val pos = Offset(pt.x - nn.x * u * 6.0f, pt.y - nn.y * u * 6.0f)
        val col = when { k >= 12 -> ROJO_FIN; k >= 11 -> ACENTO; else -> TEXTO }
        val est = estiloM(u * 4.0f, col, Fuentes.logo, FontWeight.Normal, d = this)
        italica(pos, -0.15f) { texto(m, if (k in 1..2) "$k." else "$k", pos, est, alinear = 0.5f) }
    }
    // marca roja del final (inclinada)
    run {
        val (pt, nn) = g.en(1f)
        drawLine(ROJO_FIN, Offset(pt.x - u * 1.2f, pt.y - u * 1.0f), Offset(pt.x + nn.x * g.banda + u * 0.8f, pt.y + nn.y * g.banda), u * 0.8f, StrokeCap.Round)
    }
    texto(m, "x1000s", g.p(0.762f, 0.53f), estiloM(u * 3.0f, APAGADO, d = this), alinear = 0f)

    // ---- Marcha: chevrones y GEAR ----
    texto(m, "GEAR", g.p(0.505f, 0.585f), estiloM(u * 3.8f, PRIMARIO.copy(alpha = 0.85f), Fuentes.logo, FontWeight.Normal, espacio = 1f, d = this), alinear = 0.5f)
    texto(m, "KM/H", g.p(0.335f, 0.585f), estiloM(u * 3.4f, TEXTO, Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f)

    // ---- Paneles de abajo ----
    val titulos = listOf("TEMP MOTOR", "G-FORCE", "LAPDelta:", "BESTLAP:")
    val pp = Path()
    g.paneles.forEachIndexed { i, r ->
        pp.redondo(r, u * 2.2f)
        contorno(pp, if (i % 2 == 0) PRIMARIO else SECUNDARIO, u * 0.3f, u)
        val xT = if (i == 0) r.left + r.width * 0.30f else r.center.x
        texto(m, titulos[i], Offset(xT, r.top + r.height * 0.28f), estiloM(u * 3.4f, TEXTO, d = this), alinear = if (i == 0) 0f else 0.5f)
    }
    // termómetro del primer panel
    run {
        val r = g.paneles[0]
        val c = Offset(r.left + r.width * 0.15f, r.center.y)
        drawLine(TEXTO, Offset(c.x, c.y - u * 3.2f), Offset(c.x, c.y + u * 1.6f), u * 1.1f, StrokeCap.Round)
        drawCircle(TEXTO, u * 1.4f, Offset(c.x, c.y + u * 2.4f))
        drawLine(FONDO, Offset(c.x, c.y - u * 2.8f), Offset(c.x, c.y + u * 1.4f), u * 0.45f, StrokeCap.Round)
        drawCircle(ACENTO, u * 0.8f, Offset(c.x, c.y + u * 2.4f))
    }
    // "MAX" y la curvita del G-FORCE
    run {
        val r = g.paneles[1]
        texto(m, "MAX", Offset(r.left + r.width * 0.08f, r.top + r.height * 0.56f), estiloM(u * 2.4f, APAGADO, d = this))
        val curva = Path().apply {
            moveTo(r.left + r.width * 0.66f, r.top + r.height * 0.52f)
            for (k in 1..16) lineTo(r.left + r.width * (0.66f + k * 0.017f), r.top + r.height * (0.52f + 0.06f * sin(k * 1.3f)))
        }
        drawPath(curva, APAGADO, style = Stroke(u * 0.25f))
    }

    // ---- Barra izquierda ----
    val bp = Path()
    bp.redondo(g.combustible, u * 2f); contorno(bp, PRIMARIO, u * 0.35f, u, relleno = tinteMorado(Color(0xFF120A1E)))
    iconoGasolina(g.combustible.center, u * 2.8f, PRIMARIO)
    texto(m, "MODO:", g.p(0.048f, 0.44f), estiloM(u * 3.6f, TEXTO, d = this))
    bp.redondo(g.modo, u * 2f); contorno(bp, PRIMARIO, u * 0.35f, u, relleno = tinteMorado(Color(0xFF16091F)))
    // cruceta
    run {
        val r = g.cruceta
        bp.redondo(r, u * 3f); drawPath(bp, Color(0xFF141018)); drawPath(bp, TECLA_BORDE, style = Stroke(u * 0.3f))
        val c = r.center; val e = min(r.width, r.height) * 0.30f
        val dirs = listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))
        dirs.forEachIndexed { i, d ->
            val t = Offset(c.x + d.x * e, c.y + d.y * e)
            bp.redondo(Rect(t.x - e * 0.55f, t.y - e * 0.55f, t.x + e * 0.55f, t.y + e * 0.55f), u * 1.2f)
            drawPath(bp, TECLA); drawPath(bp, TECLA_BORDE, style = Stroke(u * 0.25f))
            val tri = Path()
            fun triangulo(cx: Float, cy: Float, dx: Float, dy: Float, s: Float) {
                tri.reset()
                tri.moveTo(cx + dx * s, cy + dy * s)
                tri.lineTo(cx - dx * s * 0.6f - dy * s * 0.8f, cy - dy * s * 0.6f - dx * s * 0.8f)
                tri.lineTo(cx - dx * s * 0.6f + dy * s * 0.8f, cy - dy * s * 0.6f + dx * s * 0.8f)
                tri.close(); drawPath(tri, Color(0xFFB9B2C6))
            }
            if (i < 2) triangulo(t.x, t.y, d.x, d.y, e * 0.28f)
            else { triangulo(t.x - d.x * e * 0.14f, t.y, d.x, 0f, e * 0.22f); triangulo(t.x + d.x * e * 0.14f, t.y, d.x, 0f, e * 0.22f) }
        }
        drawRect(Color(0xFF6E667C), Offset(c.x - e * 0.22f, c.y - u * 0.3f), Size(e * 0.44f, u * 0.6f))
    }

    // ---- Barra derecha ----
    bp.redondo(g.mapa, u * 2f); contorno(bp, PRIMARIO, u * 0.35f, u, relleno = tinteMorado(Color(0xFF120A1E)))
    iconoMapa(g.mapa.center, u * 3.2f, PRIMARIO)
    g.botones4.forEachIndexed { i, r ->
        bp.redondo(r, u * 1.8f); contorno(bp, PRIMARIO, u * 0.3f, u, relleno = tinteMorado(Color(0xFF120A1E)))
        when (i) { 0 -> iconoHerramientas(r.center, u * 2.3f, PRIMARIO); 1 -> iconoEngrane(r.center, u * 2.3f, PRIMARIO)
            2 -> iconoCronometro(r.center, u * 2.3f, PRIMARIO); else -> iconoCalculadora(r.center, u * 2.3f, PRIMARIO) }
    }
    bp.redondo(g.grafica, u * 2f); contorno(bp, PRIMARIO, u * 0.35f, u, relleno = tinteMorado(Color(0xFF120A1E)))
    iconoGrafica(g.grafica.center, u * 3.0f, PRIMARIO)

    // ---- Franja de estado y líneas de luz ----
    val fin = Path().apply { moveTo(g.x(0.01f), g.y(0.868f)); lineTo(g.x(0.15f), g.y(0.868f)); lineTo(g.x(0.175f), g.y(0.84f)) }
    halo(fin, PRIMARIO, u * 1.0f, u * 1.6f)
    drawPath(fin, tinteMorado(Color(0xFFE6B5FF)), style = Stroke(u * 0.35f))
    val finD = Path().apply { moveTo(g.x(0.85f), g.y(0.835f)); lineTo(g.x(0.97f), g.y(0.835f)) }
    halo(finD, PRIMARIO, u * 1.0f, u * 1.6f)
    drawPath(finD, tinteMorado(Color(0xFFE6B5FF)), style = Stroke(u * 0.35f))
    // Ecualizador apagado (las encendidas se pintan encima en cada cuadro; sus colores son opacos)
    for (b in 0 until EQ_BARRAS) for (f in 0 until EQ_FILAS) celdaEq(g, b, f, SECUNDARIO.copy(alpha = 0.10f))
}

// ============================================================
// Lo que cambia
// ============================================================
private fun DrawScope.pintarDatos(
    g: GeoMorado, m: TextMeasurer, p: Path, rayasArco: FloatArray, pinturaRayas: android.graphics.Paint, prueba: Boolean,
    vel: Float, rpm: Float, shift: Int, marcha: Int, temp: Float, volt: Float, gas: Int, sport: Boolean,
    gMax: Float, gMin: Float, historial: List<Float>,
) {
    val u = g.u
    val t = (rpm / TOPE).coerceIn(0f, 1f)

    // ---- Arco encendido: relleno con degradado + rayitas oscuras encima (marcas finas) ----
    if (t > 0.002f) {
        val pasos = max(2, (t * 160).toInt())
        val puntos = Array(pasos + 1) { g.en(t * it / pasos) }   // una vez por cuadro (antes se calculaban 3 veces)
        p.reset()
        for (i in 0..pasos) { val (pt, _) = puntos[i]; if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
        for (i in pasos downTo 0) { val (pt, nn) = puntos[i]; p.lineTo(pt.x + nn.x * g.banda, pt.y + nn.y * g.banda) }
        p.close()
        val inicio = g.en(0f).first; val final = g.en(1f).first
        val brocha = Brush.linearGradient(
            0f to SECUNDARIO, 0.55f to PRIMARIO, 0.80f to tinteMorado(Color(0xFFD94AFF)), 0.88f to ACENTO, 1f to ROJO_FIN,
            start = Offset(inicio.x, inicio.y), end = Offset(final.x, final.y),
        )
        drawPath(p, brocha)
        // rayitas finas (como un LCD de barras) solo sobre lo encendido
        val rayas = (t * 120).toInt()
        drawIntoCanvas { it.nativeCanvas.drawLines(rayasArco, 0, (rayas + 1) * 4, pinturaRayas) }
        // filo de luz por fuera (como el borde brillante del concepto)
        p.reset()
        for (i in 0..pasos) { val (pt, _) = puntos[i]; if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
        drawPath(p, ACENTO.copy(alpha = 0.25f), style = Stroke(u * 1.6f, cap = StrokeCap.Round))
        drawPath(p, tinteMorado(Color(0xFFF1D6FF)), style = Stroke(u * 0.35f, cap = StrokeCap.Round))
        // punta brillante
        val (pt, nn) = g.en(t)
        val c = Offset(pt.x + nn.x * g.banda / 2f, pt.y + nn.y * g.banda / 2f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), ACENTO.copy(alpha = 0.35f), Color.Transparent),
            center = c, radius = g.banda * 1.3f), radius = g.banda * 1.3f, center = c)
        drawLine(Color.White, pt, Offset(pt.x + nn.x * g.banda, pt.y + nn.y * g.banda), u * 0.5f)
    }

    // ---- Velocidad ----
    val txtV = when { prueba -> "888"; vel < 0f -> "--"; else -> "${vel.toInt()}" }
    val cV = g.p(0.335f, 0.475f)
    italica(cV) {
        texto(m, txtV, cV, estiloM(u * 17f, TEXTO, Fuentes.logo, FontWeight.Normal, brillo = PRIMARIO.copy(alpha = 0.5f), radio = u * 2.5f, d = this), alinear = 0.5f)
    }

    // ---- Marcha + flechas de cambio ----
    val txtM = when { prueba -> "8"; marcha < 0 -> "-"; marcha == 0 -> "N"; else -> "$marcha" }
    val cM = g.p(0.505f, 0.43f)
    italica(cM) {
        texto(m, txtM, cM, estiloM(u * 27f, tinteMorado(Color(0xFFD82DFF)), Fuentes.logo, FontWeight.Normal, brillo = PRIMARIO, radio = u * 5f, d = this), alinear = 0.5f)
    }
    val subir = shift > 0 && rpm >= shift
    val bajar = rpm in 1f..2500f && marcha > 1
    val cL = g.p(0.435f, 0.44f); val cR = g.p(0.575f, 0.44f)
    texto(m, "«", cL, estiloM(u * 9f, if (bajar) ACENTO else SECUNDARIO.copy(alpha = 0.75f), Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f)
    texto(m, "»", cR, estiloM(u * 9f, if (subir) ACENTO else SECUNDARIO.copy(alpha = 0.75f), Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f)
    flechita(Offset(cR.x, cR.y - u * 6.5f), u * 1.4f, true, if (subir) ACENTO else SECUNDARIO.copy(alpha = 0.6f))
    flechita(Offset(cR.x, cR.y + u * 6.5f), u * 1.4f, false, SECUNDARIO.copy(alpha = 0.6f))
    flechita(Offset(cL.x, cL.y - u * 6.5f), u * 1.4f, true, SECUNDARIO.copy(alpha = 0.6f))
    flechita(Offset(cL.x, cL.y + u * 6.5f), u * 1.4f, false, if (bajar) ACENTO else SECUNDARIO.copy(alpha = 0.6f))

    // ---- Ecualizador: las últimas RPM (más la actual) en barras de puntitos ----
    run {
        val datos = (historial.takeLast(EQ_BARRAS - 1) + rpm)
        for (b in 0 until EQ_BARRAS) {
            val v = datos.getOrNull(datos.size - EQ_BARRAS + b) ?: 0f
            // un poco de vida aunque esté quieta (como en el concepto)
            val nivel = ((v / TOPE) * EQ_FILAS + if (v <= 0f) (1 + (b * 7) % 3) else 0).toInt().coerceIn(1, EQ_FILAS)
            // Solo las encendidas: las apagadas ya están en la capa fija (antes eran 216 rectángulos por cuadro)
            for (f in 0 until nivel) {
                val col = when { f > EQ_FILAS * 0.8f -> ACENTO; f > EQ_FILAS * 0.5f -> PRIMARIO; else -> SECUNDARIO }
                celdaEq(g, b, f, col)
            }
        }
    }

    // ---- Paneles ----
    val pn = g.paneles
    val grande = u * 6.4f
    // 1 Motor
    run {
        val r = pn[0]
        val vT = if (temp > -90f) "${temp.toInt()}" else "--"
        val col = if (temp >= 105f) ROJO_FIN else TEXTO
        val res = m.measure(vT, estiloM(grande, col, Fuentes.logo, FontWeight.Normal, d = this))
        val x = r.left + r.width * 0.30f; val y = r.top + r.height * 0.70f
        drawText(res, topLeft = Offset(x, y - res.size.height / 2f))
        texto(m, "°C", Offset(x + res.size.width + u * 0.6f, y + u * 0.6f), estiloM(u * 3.4f, TEXTO, d = this))
    }
    // 2 Fuerza G
    run {
        val r = pn[1]
        val txt = "${if (gMax >= 0f) "+" else ""}${fmt(gMax, 2)}g, ${fmt(gMin, 1)}g"
        texto(m, txt, Offset(r.left + r.width * 0.24f, r.top + r.height * 0.70f), estiloM(u * 3.6f, TEXTO, Fuentes.logo, FontWeight.Normal, d = this))
    }
    // 3 y 4: vueltas (todavía sin cronómetro de vueltas)
    texto(m, "--.--s", Offset(pn[2].center.x, pn[2].top + pn[2].height * 0.68f), estiloM(grande, TEXTO, Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f)
    texto(m, "-:--.-", Offset(pn[3].center.x, pn[3].top + pn[3].height * 0.68f), estiloM(grande, TEXTO, Fuentes.logo, FontWeight.Normal, d = this), alinear = 0.5f)

    // ---- Franja de estado ----
    val y = g.y(0.835f)
    val e1 = estiloM(u * 3.4f, TEXTO, d = this)
    texto(m, "TEMP MOTOR " + (if (temp > -90f) "${temp.toInt()}°C" else "--°C"), Offset(g.x(0.205f), y), e1)
    texto(m, "VOLTAJE " + (if (volt >= 0f) "${fmt(volt, 1)} V" else "-- V"), Offset(g.x(0.47f), y), e1, alinear = 0.5f)
    texto(m, "FUEL", Offset(g.x(0.665f), y), e1)
    val bajo = gas in 0..15
    texto(m, if (gas < 0) "-" else if (bajo) "E" else "F", Offset(g.x(0.705f), y), estiloM(u * 3.6f, if (bajo) ACENTO else PRIMARIO, Fuentes.logo, FontWeight.Normal, d = this))
    run {
        val x0 = g.x(0.725f); val x1 = g.x(0.835f); val h = u * 1.4f
        val barra = Path().apply { moveTo(x0 + h, y - h / 2); lineTo(x1, y - h / 2); lineTo(x1 - h, y + h / 2); lineTo(x0, y + h / 2); close() }
        drawPath(barra, SECUNDARIO.copy(alpha = 0.18f))
        val f = if (gas < 0) 0f else (gas / 100f).coerceIn(0f, 1f)
        if (f > 0f) {
            val xf = x0 + (x1 - x0) * f
            val lleno = Path().apply { moveTo(x0 + h, y - h / 2); lineTo(xf, y - h / 2); lineTo(xf - h, y + h / 2); lineTo(x0, y + h / 2); close() }
            drawPath(lleno, Brush.horizontalGradient(listOf(SECUNDARIO, PRIMARIO, ACENTO), x0, x1))
        }
    }

    // ---- MODO y barra superior ----
    texto(m, if (sport) "RACE" else "ECO", g.modo.center, estiloM(u * 4.2f, PRIMARIO, Fuentes.logo, FontWeight.Normal, brillo = PRIMARIO, radio = u * 1.5f, d = this), alinear = 0.5f)
}

private const val EQ_BARRAS = 18
private const val EQ_FILAS = 12

/** Una celda del ecualizador (barra [b], fila [f] desde abajo). */
private fun DrawScope.celdaEq(g: GeoMorado, b: Int, f: Int, col: Color) {
    val r = g.ecualizador
    val bw = r.width / EQ_BARRAS
    val fh = r.height / EQ_FILAS
    drawRect(col, Offset(r.left + b * bw + bw * 0.15f, r.bottom - (f + 1) * fh + fh * 0.18f), Size(bw * 0.7f, fh * 0.64f))
}

internal fun DrawScope.flechita(c: Offset, s: Float, arriba: Boolean, color: Color) {
    val d = if (arriba) -1f else 1f
    val p = Path().apply { moveTo(c.x - s, c.y - d * s * 0.4f); lineTo(c.x, c.y + d * s * 0.5f); lineTo(c.x + s, c.y - d * s * 0.4f) }
    drawPath(p, color, style = Stroke(s * 0.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ============================================================
// Íconos (trazo, del color primario)
// ============================================================
private fun DrawScope.iconoGasolina(c: Offset, t: Float, col: Color) {
    val st = Stroke(t * 0.13f, join = StrokeJoin.Round)
    drawRoundRect(col, Offset(c.x - t * 0.75f, c.y - t * 0.9f), Size(t * 1.1f, t * 1.8f), CornerRadius(t * 0.12f), style = st)
    drawRect(col, Offset(c.x - t * 0.55f, c.y - t * 0.7f), Size(t * 0.7f, t * 0.5f), style = Stroke(t * 0.1f))
    val m = Path().apply { moveTo(c.x + t * 0.35f, c.y - t * 0.3f); lineTo(c.x + t * 0.75f, c.y - t * 0.05f); lineTo(c.x + t * 0.75f, c.y + t * 0.6f)
        quadraticTo(c.x + t * 0.75f, c.y + t * 0.85f, c.x + t * 0.55f, c.y + t * 0.75f); lineTo(c.x + t * 0.45f, c.y + t * 0.2f) }
    drawPath(m, col, style = st)
}

private fun DrawScope.iconoMapa(c: Offset, t: Float, col: Color) {
    val p = Path().apply {
        moveTo(c.x - t, c.y - t * 0.7f); lineTo(c.x - t * 0.33f, c.y - t * 0.9f); lineTo(c.x + t * 0.33f, c.y - t * 0.7f)
        lineTo(c.x + t, c.y - t * 0.9f); lineTo(c.x + t, c.y + t * 0.7f); lineTo(c.x + t * 0.33f, c.y + t * 0.9f)
        lineTo(c.x - t * 0.33f, c.y + t * 0.7f); lineTo(c.x - t, c.y + t * 0.9f); close()
        moveTo(c.x - t * 0.33f, c.y - t * 0.9f); lineTo(c.x - t * 0.33f, c.y + t * 0.7f)
        moveTo(c.x + t * 0.33f, c.y - t * 0.7f); lineTo(c.x + t * 0.33f, c.y + t * 0.9f)
    }
    drawPath(p, col, style = Stroke(t * 0.12f, join = StrokeJoin.Round))
}

private fun DrawScope.iconoHerramientas(c: Offset, t: Float, col: Color) {
    drawLine(col, Offset(c.x - t * 0.7f, c.y + t * 0.7f), Offset(c.x + t * 0.5f, c.y - t * 0.5f), t * 0.22f, StrokeCap.Round)
    drawCircle(col, t * 0.32f, Offset(c.x + t * 0.6f, c.y - t * 0.6f), style = Stroke(t * 0.16f))
    drawLine(col, Offset(c.x + t * 0.7f, c.y + t * 0.7f), Offset(c.x - t * 0.4f, c.y - t * 0.4f), t * 0.16f, StrokeCap.Round)
    drawLine(col, Offset(c.x - t * 0.4f, c.y - t * 0.4f), Offset(c.x - t * 0.7f, c.y - t * 0.7f), t * 0.3f, StrokeCap.Round)
}

private fun DrawScope.iconoEngrane(c: Offset, t: Float, col: Color) {
    drawCircle(col, t * 0.55f, c, style = Stroke(t * 0.16f))
    drawCircle(col, t * 0.2f, c, style = Stroke(t * 0.12f))
    for (k in 0 until 8) {
        val a = k * Math.PI / 4
        drawLine(col, Offset(c.x + t * 0.6f * cos(a).toFloat(), c.y + t * 0.6f * sin(a).toFloat()),
            Offset(c.x + t * 0.9f * cos(a).toFloat(), c.y + t * 0.9f * sin(a).toFloat()), t * 0.2f, StrokeCap.Round)
    }
}

private fun DrawScope.iconoCronometro(c: Offset, t: Float, col: Color) {
    drawArc(col, -60f, 300f, false, Offset(c.x - t * 0.8f, c.y - t * 0.7f), Size(t * 1.6f, t * 1.6f), style = Stroke(t * 0.14f, cap = StrokeCap.Round))
    drawLine(col, Offset(c.x, c.y + t * 0.1f), Offset(c.x + t * 0.35f, c.y - t * 0.25f), t * 0.14f, StrokeCap.Round)
    val f = Path().apply { moveTo(c.x + t * 0.55f, c.y - t * 0.85f); lineTo(c.x + t * 0.75f, c.y - t * 0.45f); lineTo(c.x + t * 0.3f, c.y - t * 0.5f); close() }
    drawPath(f, col)
}

private fun DrawScope.iconoCalculadora(c: Offset, t: Float, col: Color) {
    drawRoundRect(col, Offset(c.x - t * 0.7f, c.y - t * 0.85f), Size(t * 1.4f, t * 1.7f), CornerRadius(t * 0.15f), style = Stroke(t * 0.12f))
    drawRect(col, Offset(c.x - t * 0.45f, c.y - t * 0.6f), Size(t * 0.9f, t * 0.35f), style = Stroke(t * 0.08f))
    for (i in 0 until 3) for (j in 0 until 2) drawRect(col, Offset(c.x - t * 0.45f + i * t * 0.33f, c.y + j * t * 0.32f), Size(t * 0.2f, t * 0.18f))
}

private fun DrawScope.iconoGrafica(c: Offset, t: Float, col: Color) {
    for (k in 0 until 4) {
        val h = t * (0.4f + k * 0.35f)
        drawRect(col.copy(alpha = 0.55f), Offset(c.x - t * 0.9f + k * t * 0.48f, c.y + t * 0.8f - h), Size(t * 0.3f, h))
    }
    val l = Path().apply { moveTo(c.x - t, c.y + t * 0.2f); lineTo(c.x - t * 0.3f, c.y - t * 0.2f); lineTo(c.x + t * 0.1f, c.y + t * 0.05f); lineTo(c.x + t * 0.9f, c.y - t * 0.8f) }
    drawPath(l, col, style = Stroke(t * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val f = Path().apply { moveTo(c.x + t * 0.95f, c.y - t * 0.9f); lineTo(c.x + t * 0.5f, c.y - t * 0.8f); lineTo(c.x + t * 0.85f, c.y - t * 0.45f); close() }
    drawPath(f, col)
}

// ============================================================
// Vista para Ajustes
// ============================================================
internal fun DrawScope.vistaMorado() {
    drawRect(FONDO)
    val w = size.width; val h = size.height
    val p = Path()
    val pts = (0..40).map { i ->
        val t = i / 40f
        if (t < 0.4f) { val a = Math.toRadians(180.0 + 90.0 * (t / 0.4f)); Offset(w * (0.30f + 0.14f * cos(a).toFloat()), h * (0.70f + 0.50f * sin(a).toFloat())) }
        else Offset(w * (0.30f + 0.48f * (t - 0.4f) / 0.6f), h * 0.20f)
    }
    pts.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
    drawPath(p, Brush.horizontalGradient(listOf(SECUNDARIO, PRIMARIO, ACENTO)), style = Stroke(h * 0.09f))
    drawCircle(Brush.radialGradient(listOf(PRIMARIO.copy(alpha = 0.35f), Color.Transparent), center = Offset(w * 0.5f, h * 0.5f), radius = h * 0.4f),
        radius = h * 0.4f, center = Offset(w * 0.5f, h * 0.5f))
    numero7(3f, 1, 0, w * 0.46f, h * 0.32f, w * 0.06f, h * 0.36f, w * 0.08f, tinteMorado(Color(0xFFD82DFF)), Color.Transparent, Path())
    numero7(145f, 3, 0, w * 0.22f, h * 0.45f, w * 0.035f, h * 0.18f, w * 0.05f, TEXTO, Color.Transparent, Path())
    for (b in 0 until 8) drawRect(if (b % 3 == 0) ACENTO else PRIMARIO, Offset(w * (0.62f + b * 0.018f), h * (0.62f - (b % 4) * 0.06f)), Size(w * 0.012f, h * (0.08f + (b % 4) * 0.06f)))
    repeat(4) { i ->
        p.reset(); p.addRoundRect(RoundRect(Rect(w * (0.20f + i * 0.155f), h * 0.80f, w * (0.34f + i * 0.155f), h * 0.94f), CornerRadius(h * 0.03f)))
        drawPath(p, PRIMARIO, style = Stroke(h * 0.012f))
    }
    repeat(3) { i -> drawRoundRect(PRIMARIO, Offset(w * 0.88f, h * (0.12f + i * 0.25f)), Size(w * 0.09f, h * 0.18f), CornerRadius(h * 0.03f), style = Stroke(h * 0.012f)) }
}
