package com.xof.ft150ts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xof.ft150ts.DecoEstilo
import com.xof.ft150ts.EstiloTema
import com.xof.ft150ts.FondoEstilo
import com.xof.ft150ts.FormaEstilo
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Motor de estilos: dibuja cualquier receta de Estilos.kt (formas, tarjetas, adornos y fondo). Todo se arma una vez por
 * tamaño (drawWithCache): actualizar velocidad o RPM no vuelve a calcular nada. Los colores salen de la receta; el
 * acento principal es Paleta.lima (el del tema o el que elijas).
 */

/** Las cuatro formas de un estilo. Se guardan por receta para no crear objetos en cada cuadro. */
class JuegoFormas(val tarjeta: CornerBasedShape, val chica: CornerBasedShape, val boton: CornerBasedShape, val chip: CornerBasedShape)

object FormasEstilo {
    private var clave: EstiloTema? = null
    private var juego: JuegoFormas? = null

    fun de(e: EstiloTema): JuegoFormas {
        if (e === clave) return juego!!
        val r = e.radio
        val j = when (e.forma) {
            FormaEstilo.REDONDA -> JuegoFormas(RoundedCornerShape(r.dp), RoundedCornerShape((r * 0.6f).dp),
                RoundedCornerShape((r * 0.5f).coerceAtLeast(8f).dp), RoundedCornerShape(50))
            FormaEstilo.RECTA -> JuegoFormas(RoundedCornerShape(0.dp), RoundedCornerShape(0.dp), RoundedCornerShape(0.dp), RoundedCornerShape(2.dp))
            FormaEstilo.CORTE -> JuegoFormas(
                CutCornerShape(topStart = r.dp, topEnd = 0.dp, bottomEnd = r.dp, bottomStart = 0.dp),
                CutCornerShape(topStart = (r * 0.6f).dp, topEnd = 0.dp, bottomEnd = (r * 0.6f).dp, bottomStart = 0.dp),
                CutCornerShape(topStart = 10.dp, topEnd = 0.dp, bottomEnd = 10.dp, bottomStart = 0.dp), CutCornerShape(4.dp))
            FormaEstilo.PILDORA -> JuegoFormas(RoundedCornerShape(r.dp), RoundedCornerShape((r * 0.7f).dp), RoundedCornerShape(50), RoundedCornerShape(50))
            FormaEstilo.HOJA -> JuegoFormas(
                RoundedCornerShape(topStart = r.dp, topEnd = (r * 0.25f).dp, bottomEnd = r.dp, bottomStart = (r * 0.25f).dp),
                RoundedCornerShape(topStart = (r * 0.6f).dp, topEnd = (r * 0.15f).dp, bottomEnd = (r * 0.6f).dp, bottomStart = (r * 0.15f).dp),
                RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 4.dp), RoundedCornerShape(50))
            FormaEstilo.ESCALON -> JuegoFormas(CutCornerShape(r.dp), CutCornerShape((r * 0.6f).dp), CutCornerShape(6.dp), CutCornerShape(3.dp))
        }
        clave = e
        juego = j
        return j
    }
}

// ---------------- Tarjetas ----------------

/** Superficie y adornos de una tarjeta con la receta [e] (reemplaza a panel() en los temas de estilo). */
fun Modifier.panelEstilo(e: EstiloTema, forma: Shape, conBorde: Boolean, neon: Boolean): Modifier {
    val acento = Paleta.lima
    val a2 = Color(e.acento2)
    var m = this
    if (neon) m = m.resplandor(forma, acento)
    if (conBorde && e.deco == DecoEstilo.NEON) m = m.resplandor(forma, a2)
    if (conBorde && e.deco == DecoEstilo.ARCILLA) m = m.sombraArcilla(forma)
    m = m.clip(forma).background(
        when (e.deco) {
            DecoEstilo.VIDRIO -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.04f)))
            DecoEstilo.ARCILLA -> Brush.verticalGradient(listOf(Color(e.superficie2), Color(e.superficie)))
            else -> Brush.verticalGradient(listOf(Color(e.superficie), Color(e.superficie)))
        }
    )
    if (!conBorde) return m
    val sinBordeComun = e.deco in setOf(DecoEstilo.GLITCH, DecoEstilo.PUNTEADO, DecoEstilo.DOBLE_FILETE)
    if (e.bordeDp > 0f && !sinBordeComun) {
        m = when (e.deco) {
            DecoEstilo.VIDRIO -> m.border(e.bordeDp.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))), forma)
            DecoEstilo.NEON -> m.border(e.bordeDp.dp, a2.copy(alpha = 0.85f), forma)
            else -> m.border(e.bordeDp.dp, Color(e.borde), forma)
        }
    }
    return m.decoEstilo(e, forma)
}

/** Sombra suave de arcilla por fuera (dos copias corridas y tenues: parece desenfoque y no cuesta). */
private fun Modifier.sombraArcilla(forma: Shape): Modifier = drawWithCache {
    val p = Path().apply { addOutline(forma.createOutline(size, layoutDirection, this@drawWithCache)) }
    val d1 = 4.dp.toPx()
    val d2 = 8.dp.toPx()
    onDrawBehind {
        translate(d2 * 0.4f, d2) { drawPath(p, Color.Black.copy(alpha = 0.22f)) }
        translate(d1 * 0.4f, d1) { drawPath(p, Color.Black.copy(alpha = 0.35f)) }
    }
}

/** Adorno de la tarjeta encima del contenido (o detrás, si estorbaría a los números). */
private fun Modifier.decoEstilo(e: EstiloTema, forma: Shape): Modifier {
    if (e.deco == DecoEstilo.NADA || e.deco == DecoEstilo.NEON) return this
    val acento = Paleta.lima
    return drawWithCache {
        val outline = Path().apply { addOutline(forma.createOutline(size, layoutDirection, this@drawWithCache)) }
        val pintar = construirDeco(e, outline, size, this, layoutDirection, forma, acento)
        onDrawWithContent { drawContent(); pintar(this) }
    }.graphicsLayer()
    // Rendimiento: el contenido de la tarjeta va en su propia capa. Si cambia un número adentro, solo se vuelve a
    // grabar esa capa; el adorno (que se pinta encima) ya no se repinta con cada cambio (era ~5 % del hilo principal).
}

/**
 * Prepara el adorno de una tarjeta de [tam] (se llama una vez por tamaño) y regresa cómo pintarlo. Se usa en la app
 * y en la vista previa de Ajustes (así lo que ves en la tarjeta es lo que obtienes).
 */
internal fun construirDeco(
    e: EstiloTema, outline: Path, tam: Size, d: Density, ld: LayoutDirection, forma: Shape, acento: Color,
): DrawScope.() -> Unit = with(d) {
    val w = tam.width
    val h = tam.height
    val a2 = Color(e.acento2)
    when (e.deco) {
        DecoEstilo.BANDA -> {
            val alto = 6.dp.toPx()
            return@with { drawRect(acento, Offset.Zero, Size(w, alto)) }
        }
        DecoEstilo.ARCILLA -> {
            val luz = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w * 0.18f, h * 0.08f), radius = maxOf(w, h) * 0.7f)
            val sombra = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), startY = h * 0.55f, endY = h)
            return@with { drawRect(luz); drawRect(sombra) }
        }
        DecoEstilo.GLITCH -> {
            val dx = 2.dp.toPx()
            val g = Stroke(e.bordeDp.dp.toPx().coerceAtLeast(1f))
            return@with {
                translate(-dx, 0f) { drawPath(outline, Color(0xFF00F0FF).copy(alpha = 0.85f), style = g) }
                translate(dx, 0f) { drawPath(outline, Color(0xFFFF2BD6).copy(alpha = 0.85f), style = g) }
                drawPath(outline, Color.White.copy(alpha = 0.35f), style = Stroke(1f))
            }
        }
        DecoEstilo.LINEA_LATERAL -> {
            val ancho = 4.dp.toPx()
            return@with { drawRect(acento, Offset.Zero, Size(ancho, h)) }
        }
        DecoEstilo.BRILLO -> {
            val r = e.radio.dp.toPx().coerceAtMost(w / 3)
            val brillo = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), endY = h * 0.35f)
            return@with {
                drawRect(brillo)
                drawLine(Color.White.copy(alpha = 0.12f), Offset(r, 0.5f), Offset(w - r, 0.5f), 1.dp.toPx())
            }
        }
        DecoEstilo.VIDRIO -> {
            val r = e.radio.dp.toPx().coerceAtMost(w / 3)
            return@with { drawLine(Color.White.copy(alpha = 0.30f), Offset(r, 1f), Offset(w * 0.55f, 1f), 1.dp.toPx()) }
        }
        DecoEstilo.PUNTEADO -> {
            val punto = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
            val g = Stroke(e.bordeDp.dp.toPx().coerceAtLeast(1.5f), pathEffect = punto, cap = StrokeCap.Round)
            // Garabato en zigzag arriba a la derecha
            val zz = Path()
            val x0 = w - 54.dp.toPx()
            val y0 = 10.dp.toPx()
            val paso = 6.dp.toPx()
            if (w > 110.dp.toPx()) {
                zz.moveTo(x0, y0)
                for (i in 1..6) zz.lineTo(x0 + i * paso, if (i % 2 == 1) y0 + paso else y0)
            }
            return@with {
                drawPath(outline, a2, style = g)
                drawPath(zz, acento.copy(alpha = 0.8f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        DecoEstilo.DOBLE_FILETE -> {
            val m = 4.dp.toPx()
            val interior = Path().apply {
                addOutline(forma.createOutline(Size((w - 2 * m).coerceAtLeast(1f), (h - 2 * m).coerceAtLeast(1f)), ld, d))
                translate(Offset(m, m))
            }
            // Abanico en las esquinas de arriba: 3 rayos cortos
            val abanico = Path()
            val c = e.radio.dp.toPx() + m
            for (k in 0..2) {
                val ang = Math.toRadians(20.0 + k * 25.0)
                val l = 14.dp.toPx()
                abanico.moveTo(c, c); abanico.lineTo(c + (l * cos(ang)).toFloat(), c + (l * sin(ang)).toFloat())
                abanico.moveTo(w - c, c); abanico.lineTo(w - c - (l * cos(ang)).toFloat(), c + (l * sin(ang)).toFloat())
            }
            return@with {
                drawPath(outline, acento, style = Stroke(1.dp.toPx()))
                drawPath(interior, acento.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
                if (w > 120.dp.toPx()) drawPath(abanico, acento.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
            }
        }
        DecoEstilo.LIBREA -> {
            // Dos franjas de carreras a la derecha y una fila de cuadros arriba
            val f1 = Path()
            val f2 = Path()
            val ancho1 = 10.dp.toPx()
            val ancho2 = 5.dp.toPx()
            val x = w - 46.dp.toPx()
            val inc = h * 0.35f
            f1.moveTo(x, 0f); f1.lineTo(x + ancho1, 0f); f1.lineTo(x + ancho1 - inc, h); f1.lineTo(x - inc, h); f1.close()
            val x2 = x + ancho1 + 4.dp.toPx()
            f2.moveTo(x2, 0f); f2.lineTo(x2 + ancho2, 0f); f2.lineTo(x2 + ancho2 - inc, h); f2.lineTo(x2 - inc, h); f2.close()
            val cuadro = 5.dp.toPx()
            val cuadros = ArrayList<Offset>()
            if (w > 140.dp.toPx()) for (i in 0 until 8) for (j in 0 until 2) if ((i + j) % 2 == 0) cuadros.add(Offset(16.dp.toPx() + i * cuadro, 6.dp.toPx() + j * cuadro))
            return@with {
                drawPath(f1, acento.copy(alpha = 0.22f))
                drawPath(f2, a2.copy(alpha = 0.16f))
                cuadros.forEach { drawRect(Color.White.copy(alpha = 0.55f), it, Size(cuadro, cuadro)) }
            }
        }
        else -> return@with {}
    }
}

// ---------------- Fondo ----------------

/**
 * Prepara el fondo de toda la app con la receta [e] para [tam] (una vez por tamaño) y regresa cómo pintarlo.
 * Lo usan FondoApp y la vista previa de Ajustes.
 */
internal fun construirFondo(e: EstiloTema, tam: Size, d: Density, acento: Color): DrawScope.() -> Unit = with(d) {
    val w = tam.width
    val h = tam.height
    val a2 = Color(e.acento2)
    when (e.fondo) {
        FondoEstilo.NEGRO -> {
            val v = Brush.radialGradient(listOf(acento.copy(alpha = 0.05f), Color.Transparent), center = Offset(w / 2, h * 1.1f), radius = maxOf(w, h) * 0.7f)
            return@with { drawRect(v) }
        }
        FondoEstilo.AURORA -> {
            val r = maxOf(w, h)
            val b1 = Brush.radialGradient(listOf(acento.copy(alpha = 0.22f), Color.Transparent), center = Offset(w * 0.12f, h * 0.08f), radius = r * 0.6f)
            val b2 = Brush.radialGradient(listOf(a2.copy(alpha = 0.20f), Color.Transparent), center = Offset(w * 0.88f, h * 0.38f), radius = r * 0.55f)
            val b3 = Brush.radialGradient(listOf(Color(0xFF2BFFB0).copy(alpha = 0.10f), Color.Transparent), center = Offset(w * 0.45f, h * 1.05f), radius = r * 0.6f)
            return@with { drawRect(b1); drawRect(b2); drawRect(b3) }
        }
        FondoEstilo.SYNTH -> {
            val horizonte = h * 0.62f
            val radioSol = min(w, h) * 0.22f
            val centroSol = Offset(w * 0.74f, horizonte)
            val sol = Brush.verticalGradient(listOf(acento.copy(alpha = 0.32f), Color(0xFFFF9F43).copy(alpha = 0.26f)),
                startY = horizonte - radioSol, endY = horizonte)
            val cortes = ArrayList<Pair<Float, Float>>()     // franjas negras sobre la mitad baja del sol
            var y = horizonte - radioSol * 0.45f
            var grueso = 2.dp.toPx()
            while (y < horizonte) { cortes.add(y to grueso); y += grueso * 3.2f; grueso *= 1.25f }
            val rejilla = Path()
            val vx = w * 0.5f
            val n = 14
            for (i in -n..n) { rejilla.moveTo(vx, horizonte); rejilla.lineTo(vx + i * (w / n) * 1.6f, h) }
            for (k in 1..9) { val t = (k / 9f); val yy = horizonte + (h - horizonte) * t * t; rejilla.moveTo(0f, yy); rejilla.lineTo(w, yy) }
            val cielo = Brush.verticalGradient(listOf(Color(0xFF12051F), Color.Transparent), endY = horizonte)
            return@with {
                drawRect(cielo)
                drawCircle(sol, radioSol, centroSol)
                cortes.forEach { (yy, g) -> drawRect(Color.Black, Offset(centroSol.x - radioSol, yy), Size(radioSol * 2, g)) }
                drawRect(Color.Black, Offset(0f, horizonte), Size(w, h - horizonte))
                drawPath(rejilla, a2.copy(alpha = 0.20f), style = Stroke(1.dp.toPx()))
                drawLine(acento.copy(alpha = 0.45f), Offset(0f, horizonte), Offset(w, horizonte), 1.5.dp.toPx())
            }
        }
        FondoEstilo.ESCANEO -> {
            val lineas = Path()
            val p = 3.dp.toPx()
            var y = 0f
            while (y < h) { lineas.moveTo(0f, y); lineas.lineTo(w, y); y += p }
            val vineta = Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = Offset(w / 2, h / 2), radius = maxOf(w, h) * 0.7f)
            val esquina = Path().apply { val l = 30.dp.toPx(); val m = 6.dp.toPx(); moveTo(m, m + l); lineTo(m, m); lineTo(m + l, m) }
            return@with {
                drawPath(lineas, Color.White.copy(alpha = 0.022f), style = Stroke(1f))
                drawRect(vineta)
                drawPath(esquina, acento.copy(alpha = 0.45f), style = Stroke(2.dp.toPx()))
            }
        }
        FondoEstilo.REJILLA -> {
            val r = Path()
            val col = w / 12f
            for (i in 1 until 12) { r.moveTo(i * col, 0f); r.lineTo(i * col, h) }
            val fila = 24.dp.toPx()
            val filas = Path()
            var y = fila
            while (y < h) { filas.moveTo(0f, y); filas.lineTo(w, y); y += fila }
            return@with {
                drawPath(r, Color.White.copy(alpha = 0.035f), style = Stroke(1f))
                drawPath(filas, Color.White.copy(alpha = 0.018f), style = Stroke(1f))
            }
        }
        FondoEstilo.ONDAS -> {
            val curvas = Path()
            for (k in 0 until 9) {
                val base = h * (0.08f + k * 0.115f)
                val amp = 10.dp.toPx() + k * 2.dp.toPx()
                val fase = k * 0.7
                curvas.moveTo(0f, base)
                var x = 0f
                while (x <= w) { curvas.lineTo(x, base + (amp * sin(x / w * 6.28 * 1.5 + fase)).toFloat()); x += 12.dp.toPx() }
            }
            return@with { drawPath(curvas, acento.copy(alpha = 0.07f), style = Stroke(1.5.dp.toPx())) }
        }
        FondoEstilo.MEMPHIS -> {
            val r = java.util.Random(80)
            val tri = Path()
            val zig = Path()
            val circulos = ArrayList<Triple<Offset, Float, Color>>()
            val colores = listOf(acento, a2, Color(e.borde))
            repeat(30) { i ->
                val x = r.nextFloat() * w
                val y = r.nextFloat() * h
                val t = (10 + r.nextInt(16)).dp.toPx()
                when (i % 3) {
                    0 -> { tri.moveTo(x, y - t); tri.lineTo(x + t, y + t * 0.7f); tri.lineTo(x - t, y + t * 0.7f); tri.close() }
                    1 -> circulos.add(Triple(Offset(x, y), t * 0.45f, colores[r.nextInt(3)]))
                    else -> { zig.moveTo(x, y); for (j in 1..4) zig.lineTo(x + j * t * 0.5f, if (j % 2 == 1) y + t * 0.4f else y) }
                }
            }
            return@with {
                drawPath(tri, a2.copy(alpha = 0.14f), style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
                drawPath(zig, acento.copy(alpha = 0.16f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                circulos.forEach { (c, rr, col) -> drawCircle(col.copy(alpha = 0.12f), rr, c) }
            }
        }
        FondoEstilo.ABANICO -> {
            val rayos = Path()
            val centro = Offset(w / 2, h * 1.05f)
            val largo = maxOf(w, h) * 1.3f
            for (i in 0..28) {
                val ang = Math.PI + i * Math.PI / 28
                rayos.moveTo(centro.x, centro.y)
                rayos.lineTo(centro.x + (largo * cos(ang)).toFloat(), centro.y + (largo * sin(ang)).toFloat())
            }
            return@with {
                drawPath(rayos, acento.copy(alpha = 0.05f), style = Stroke(1.dp.toPx()))
                for (k in 1..3) drawCircle(acento.copy(alpha = 0.07f), h * 0.22f * k, centro, style = Stroke(1.dp.toPx()))
            }
        }
        FondoEstilo.CUADROS -> {
            val cuadro = 14.dp.toPx()
            val cuadros = ArrayList<Offset>()
            for (i in 0 until 12) for (j in 0 until 3) if ((i + j) % 2 == 0) cuadros.add(Offset(w - (i + 1) * cuadro, j * cuadro))
            val franjas = Path()
            for (k in 0 until 3) { val x = w * (0.15f + k * 0.07f); franjas.moveTo(x, h); franjas.lineTo(x + h * 0.6f, 0f) }
            return@with {
                cuadros.forEach { drawRect(Color.White.copy(alpha = 0.05f), it, Size(cuadro, cuadro)) }
                drawPath(franjas, acento.copy(alpha = 0.07f), style = Stroke(6.dp.toPx()))
            }
        }
        FondoEstilo.DEGRADADO -> {
            val g = Brush.verticalGradient(listOf(Color(0xFF101014), Color.Black))
            return@with { drawRect(g) }
        }
    }
}

// ---------------- Vista previa para Ajustes ----------------

/** La receta [e] en miniatura: su fondo y tres tarjetas con su forma, borde y adorno. */
internal fun DrawScope.vistaEstilo(e: EstiloTema) {
    val acento = Color(e.acento)
    construirFondo(e, size, this, acento)(this)
    val w = size.width
    val h = size.height
    val forma = FormasEstilo.de(e).chica
    fun tarjeta(x: Float, y: Float, tw: Float, th: Float) {
        val t = Size(tw, th)
        val outline = Path().apply { addOutline(forma.createOutline(t, layoutDirection, this@vistaEstilo)) }
        translate(x, y) {
            drawPath(outline, Color(e.superficie).let { if (it.alpha < 0.5f) Color.White.copy(alpha = 0.10f) else it })
            if (e.bordeDp > 0f && e.deco !in setOf(DecoEstilo.GLITCH, DecoEstilo.PUNTEADO, DecoEstilo.DOBLE_FILETE)) {
                drawPath(outline, if (e.deco == DecoEstilo.NEON) Color(e.acento2) else Color(e.borde), style = Stroke(e.bordeDp.dp.toPx().coerceAtMost(2.5f)))
            }
            construirDeco(e, outline, t, this@vistaEstilo, layoutDirection, forma, acento)(this)
            // Un número grande y una barra del acento, para ver cómo se lee
            drawRect(Color.White.copy(alpha = 0.85f), Offset(tw * 0.14f, th * 0.42f), Size(tw * 0.32f, th * 0.18f))
            drawRect(acento, Offset(tw * 0.14f, th * 0.70f), Size(tw * 0.55f, th * 0.07f))
        }
    }
    tarjeta(w * 0.05f, h * 0.10f, w * 0.48f, h * 0.80f)
    tarjeta(w * 0.58f, h * 0.10f, w * 0.37f, h * 0.36f)
    tarjeta(w * 0.58f, h * 0.54f, w * 0.37f, h * 0.36f)
}
