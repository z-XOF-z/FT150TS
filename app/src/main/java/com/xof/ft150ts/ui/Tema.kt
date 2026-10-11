package com.xof.ft150ts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.R
import com.xof.ft150ts.TemaApp
import java.text.Normalizer
import java.util.Locale

private val esVidrio: Boolean get() = Apariencia.tema == TemaApp.VIDRIO
/** Tema Filo: HUD con cortes a 45° más bruscos y todo integrado en un marco. */
val temaFilo: Boolean get() = Apariencia.tema == TemaApp.FILO
/** Tema Pixel: los cortes a 45° hechos de escalones de pixel. */
val temaPixel: Boolean get() = Apariencia.tema == TemaApp.PIXEL
/** Tema Táctico: visor militar, esquinas con corchetes y retícula. */
val temaTactico: Boolean get() = Apariencia.tema == TemaApp.TACTICO
/** Temas con la barra de arriba integrada (segmentos) y marco de visor. */
val temaIntegrado: Boolean get() = temaFilo || temaPixel || temaTactico

/**
 * Colores. El fondo es negro puro (pantalla AMOLED: se ve mejor y gasta menos).
 * El acento y los colores del shift light salen de Ajustes > Apariencia.
 */
object Paleta {
    val fondo = Color(0xFF000000)
    val texto = Color(0xFFEEF3EF)
    val textoSec = Color(0xFF8D989C)
    val plata = Color(0xFFB8C2C6)
    val blanco = Color(0xFFF4F7F2)
    val ambar = Color(0xFFFFB020)
    val rojo = Color(0xFFFF3B3B)
    /** Verde fijo para "todo bien" (batería, neutral), no cambia con el acento. */
    val verde = Color(0xFF39E55A)

    // Superficies: sólidas en Carbono, translúcidas en Vidrio, casi negras en Filo (el acento manda)
    val superficie: Color get() = when {
        esVidrio -> Color(0x17FFFFFF); temaFilo -> Color(0xFF07090A); temaPixel -> Color(0xFF090B09)
        temaTactico -> Color(0xFF050706); else -> Color(0xFF0E1113)
    }
    val superficie2: Color get() = when {
        esVidrio -> Color(0x24FFFFFF); temaFilo -> Color(0xFF12171A); temaPixel -> Color(0xFF141814)
        temaTactico -> Color(0xFF0F1412); else -> Color(0xFF181D20)
    }
    val borde: Color get() = when {
        esVidrio -> Color(0x33FFFFFF); temaFilo -> Color(0xFF222A2E); temaPixel -> Color(0xFF2A332A)
        temaTactico -> Color(0xFF1C2420); else -> Color(0xFF262D31)
    }

    /** Color de acento elegido (por defecto verde lima de la FT150TS). */
    val lima: Color get() = Color(Apariencia.colorAcento())
    val limaOscuro: Color get() = lima.let { Color(it.red * 0.38f, it.green * 0.38f, it.blue * 0.38f) }
    val acento: Color get() = lima

    // Shift light / zonas de RPM
    val rpmBaja: Color get() = Color(Apariencia.COLORES_RPM[Apariencia.coloresRpm].baja)
    val rpmMedia: Color get() = Color(Apariencia.COLORES_RPM[Apariencia.coloresRpm].media)
    val rpmAlta: Color get() = Color(Apariencia.COLORES_RPM[Apariencia.coloresRpm].alta)
}

/** Tipografías incluidas en res/font (licencia libre OFL). */
object Fuentes {
    /** Chakra Petch: letras con esquinas cortadas, para toda la interfaz. */
    val ui = FontFamily(
        Font(R.font.chakra_petch_regular, FontWeight.Normal),
        Font(R.font.chakra_petch_medium, FontWeight.Medium),
        Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
        Font(R.font.chakra_petch_bold, FontWeight.Bold),
    )

    /** Russo One: logo y números grandes. */
    val logo = FontFamily(Font(R.font.russo_one, FontWeight.Normal))

    /** Press Start 2P: etiquetas pixeladas. */
    val pixel = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))
}

/**
 * Formas: en Carbono, esquinas cortadas como el carenado;
 * en Vidrio, redondeadas estilo iOS; en Filo, cortes a 45° grandes y las cuatro esquinas mordidas.
 */
object Formas {
    const val CORTE_TARJETA_FILO = 20
    const val CORTE_CHICA_FILO = 10
    private val filoTarjeta = CutCornerShape(topStart = CORTE_TARJETA_FILO.dp, topEnd = 5.dp, bottomEnd = CORTE_TARJETA_FILO.dp, bottomStart = 5.dp)
    private val filoChica = CutCornerShape(topStart = CORTE_CHICA_FILO.dp, topEnd = 3.dp, bottomEnd = CORTE_CHICA_FILO.dp, bottomStart = 3.dp)
    private val filoBoton = CutCornerShape(topStart = 14.dp, topEnd = 0.dp, bottomEnd = 14.dp, bottomStart = 0.dp)
    private val filoChip = CutCornerShape(topStart = 8.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp)
    private val pixTarjeta = CorteEscalonado(16.dp, 4)
    private val pixChica = CorteEscalonado(8.dp, 2)
    private val pixBoton = CorteEscalonado(10.dp, 2)
    private val pixChip = CorteEscalonado(6.dp, 2)
    const val CORTE_TACTICO_TARJETA = 6
    const val CORTE_TACTICO_CHICA = 4
    private val tacTarjeta = CutCornerShape(CORTE_TACTICO_TARJETA.dp)
    private val tacChica = CutCornerShape(CORTE_TACTICO_CHICA.dp)
    private val tacBoton = CutCornerShape(5.dp)
    private val tacChip = CutCornerShape(4.dp)
    private val corteTarjeta = CutCornerShape(topStart = 14.dp, topEnd = 0.dp, bottomEnd = 14.dp, bottomStart = 0.dp)
    private val corteChica = CutCornerShape(topStart = 8.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp)
    private val corteBoton = CutCornerShape(topStart = 10.dp, topEnd = 0.dp, bottomEnd = 10.dp, bottomStart = 0.dp)
    private val corteChip = CutCornerShape(6.dp)
    private val redTarjeta = RoundedCornerShape(24.dp)
    private val redChica = RoundedCornerShape(14.dp)
    private val redBoton = RoundedCornerShape(50)
    private val redChip = RoundedCornerShape(50)

    val tarjeta: Shape get() = when { esVidrio -> redTarjeta; temaFilo -> filoTarjeta; temaPixel -> pixTarjeta; temaTactico -> tacTarjeta; else -> corteTarjeta }
    val chica: Shape get() = when { esVidrio -> redChica; temaFilo -> filoChica; temaPixel -> pixChica; temaTactico -> tacChica; else -> corteChica }
    val boton: Shape get() = when { esVidrio -> redBoton; temaFilo -> filoBoton; temaPixel -> pixBoton; temaTactico -> tacBoton; else -> corteBoton }
    val chip: Shape get() = when { esVidrio -> redChip; temaFilo -> filoChip; temaPixel -> pixChip; temaTactico -> tacChip; else -> corteChip }

    /** Corte de las esquinas en Táctico (para los corchetes). */
    fun corteTactico(forma: Shape): Int = when (forma) {
        tacTarjeta -> CORTE_TACTICO_TARJETA
        tacChica -> CORTE_TACTICO_CHICA
        else -> 0
    }

    /** Tamaño del corte de arriba-izquierda de una forma de Filo (para los acentos HUD); 0 si no aplica. */
    fun corteFilo(forma: Shape): Int = when (forma) {
        filoTarjeta -> CORTE_TARJETA_FILO
        filoChica -> CORTE_CHICA_FILO
        else -> 0
    }
}

/**
 * Acentos HUD del tema Filo: el corte de arriba-izquierda se marca con el acento y se alarga un poco por las
 * orillas; el de abajo-derecha, más tenue; y tres pixeles en la esquina de arriba-derecha.
 * Se dibuja una sola vez por tamaño (drawWithCache): no cuesta al actualizar datos.
 */
fun Modifier.acentosHud(cortePx: Int, color: Color, pixeles: Boolean = true): Modifier = if (cortePx <= 0) this else drawWithCache {
    val c = cortePx.dp.toPx()
    val grueso = 3.dp.toPx()
    val largo = c * 0.9f
    val cuadro = 3.dp.toPx()
    val w = size.width
    val h = size.height
    val arriba = Path().apply {
        moveTo(0f, c + largo * 0.6f); lineTo(0f, c); lineTo(c, 0f); lineTo(c + largo, 0f)
    }
    val abajo = Path().apply {
        moveTo(w, h - c - largo * 0.4f); lineTo(w, h - c); lineTo(w - c, h); lineTo(w - c - largo * 0.6f, h)
    }
    val trazo = Stroke(grueso, join = StrokeJoin.Miter)
    onDrawWithContent {
        drawContent()
        drawPath(arriba, color, style = trazo)
        drawPath(abajo, color.copy(alpha = 0.45f), style = trazo)
        if (pixeles && w > c * 4) {
            for (i in 0..2) {
                drawRect(color.copy(alpha = 0.55f - i * 0.15f), Offset(w - 7.dp.toPx() - i * (cuadro + 2.dp.toPx()), 4.dp.toPx()), Size(cuadro, cuadro))
            }
        }
    }
}

/**
 * Esquinas de arriba-izquierda y abajo-derecha cortadas a 45° pero en escalones de pixel (tema Pixel).
 * [tam] = cuánto mide el corte; [pasos] = cuántos escalones.
 */
class CorteEscalonado(val tam: Dp, val pasos: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { tam.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val s = t / pasos
        val w = size.width
        val h = size.height
        val p = Path()
        p.moveTo(0f, t)
        for (i in 1..pasos) { p.lineTo((i - 1) * s, t - i * s); p.lineTo(i * s, t - i * s) }
        p.lineTo(w, 0f)
        p.lineTo(w, h - t)
        for (i in 1..pasos) { p.lineTo(w - (i - 1) * s, h - t + i * s); p.lineTo(w - i * s, h - t + i * s) }
        p.lineTo(0f, h)
        p.close()
        return Outline.Generic(p)
    }
}

/** Pixel: cada escalón del corte se marca con un cuadrito del acento (el de abajo, más tenue). */
fun Modifier.acentosEscalon(forma: Shape, color: Color): Modifier {
    val e = forma as? CorteEscalonado ?: return this
    return drawWithCache {
        val t = e.tam.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
        val s = t / e.pasos
        val lado = s * 0.9f
        val w = size.width
        val h = size.height
        onDrawWithContent {
            drawContent()
            for (i in 0 until e.pasos) {
                drawRect(color, Offset(i * s, t - (i + 1) * s), Size(lado, lado))
                drawRect(color.copy(alpha = 0.45f), Offset(w - (i + 1) * s + (s - lado), h - t + i * s + (s - lado)), Size(lado, lado))
            }
            // y una fila de pixeles sobre la orilla de arriba
            for (i in 0..2) drawRect(color.copy(alpha = 0.6f - i * 0.18f), Offset(t + s * (1.5f + i * 1.6f), 0f), Size(lado, lado * 0.6f))
        }
    }
}

/** Táctico: corchetes en las cuatro esquinas (el de arriba-izquierda completo, los demás más tenues). */
fun Modifier.corchetes(corteDp: Int, color: Color): Modifier = drawWithCache {
    val c = corteDp.dp.toPx()
    val l = 14.dp.toPx()
    val g = 2.dp.toPx()
    val w = size.width
    val h = size.height
    fun esquina(p: Path, x: Float, y: Float, sx: Float, sy: Float) {
        p.moveTo(x, y + sy * (c + l)); p.lineTo(x, y + sy * c); p.lineTo(x + sx * c, y); p.lineTo(x + sx * (c + l), y)
    }
    val a = Path().apply { esquina(this, 0f, 0f, 1f, 1f) }
    val resto = Path().apply { esquina(this, w, 0f, -1f, 1f); esquina(this, 0f, h, 1f, -1f); esquina(this, w, h, -1f, -1f) }
    val trazo = Stroke(g * 1.5f, join = StrokeJoin.Miter)
    onDrawWithContent {
        drawContent()
        drawPath(a, color, style = trazo)
        drawPath(resto, color.copy(alpha = 0.55f), style = trazo)
        // marca de mira chiquita al centro de la orilla de arriba
        drawRect(color.copy(alpha = 0.5f), Offset(w / 2f - 6.dp.toPx(), 0f), Size(12.dp.toPx(), g))
    }
}

/**
 * Brillo neón: el contorno se dibuja tres veces cada vez más ancho y más tenue, por fuera de la tarjeta
 * (va antes del clip). Se dibuja una vez por tamaño.
 */
fun Modifier.resplandor(forma: Shape, color: Color): Modifier = drawWithCache {
    val camino = Path().apply { addOutline(forma.createOutline(size, layoutDirection, this@drawWithCache)) }
    val anchos = floatArrayOf(10.dp.toPx(), 6.dp.toPx(), 3.dp.toPx())
    val alfas = floatArrayOf(0.07f, 0.12f, 0.22f)
    onDrawBehind {
        for (i in anchos.indices) drawPath(camino, color.copy(alpha = alfas[i]), style = Stroke(anchos[i]))
    }
}

/** Adornos del tema en una tarjeta (Filo: acentos HUD, Pixel: escalones, Táctico: corchetes). */
fun Modifier.decoracionTema(forma: Shape, color: Color): Modifier = when {
    temaFilo -> acentosHud(Formas.corteFilo(forma), color)
    temaPixel -> acentosEscalon(forma, color)
    temaTactico -> corchetes(Formas.corteTactico(forma).coerceAtLeast(3), color)
    else -> this
}

/**
 * Superficie de tarjeta según el tema. En Vidrio: fondo translúcido con un
 * borde de luz arriba (como el vidrio de iOS), sin desenfoque para no gastar batería.
 */
fun Modifier.panel(forma: Shape = Formas.tarjeta, conBorde: Boolean = true): Modifier {
    val neon = Apariencia.neon && conBorde
    val acento = Paleta.lima
    val base = (if (neon) this.resplandor(forma, acento) else this).clip(forma).background(Paleta.superficie)
    if (!conBorde) return base
    return when {
        esVidrio -> base.border(
            1.dp,
            if (neon) Brush.verticalGradient(listOf(acento.copy(alpha = 0.9f), acento.copy(alpha = 0.25f)))
            else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.06f))),
            forma,
        )
        else -> {
            val ancho = if (temaPixel) 2.dp else 1.dp
            val color = when {
                neon -> acento.copy(alpha = 0.75f)
                temaTactico -> Paleta.borde.copy(alpha = 0.6f)
                else -> Paleta.borde
            }
            base.border(ancho, color, forma).decoracionTema(forma, acento)
        }
    }
}

/**
 * Fondo de toda la app: negro AMOLED con textura (puntos, rejilla, rayas a 45° o nada) y, según el tema:
 * Vidrio con brillos de color, Filo con marco de visor a 45°, Pixel con marco escalonado y líneas de barrido,
 * Táctico con retícula y corchetes. Va en su propia capa y se dibuja una vez por tamaño (no cuesta al actualizar).
 */
@Composable
fun FondoApp(modifier: Modifier = Modifier) {
    val vidrio = esVidrio
    val filo = temaFilo
    val pixel = temaPixel
    val tactico = temaTactico
    val neon = Apariencia.neon
    val textura = Apariencia.fondo
    val acento = Paleta.lima
    val segundo = Paleta.rpmAlta
    Box(
        modifier.fillMaxSize().graphicsLayer().background(Paleta.fondo).drawWithCache {
            val w = size.width
            val h = size.height
            // ---- textura ----
            val paso = (if (pixel) 9.dp else 7.dp).toPx()
            val puntos = ArrayList<Offset>()
            if (textura == 0) {
                var y = paso / 2
                while (y < h) {
                    var x = paso / 2
                    while (x < w) { puntos.add(Offset(x, y)); x += paso }
                    y += paso
                }
            }
            val tamPunto = (if (pixel) 2.4.dp else 1.4.dp).toPx()
            val rejilla = Path()
            if (textura == 1) {
                val p2 = 24.dp.toPx()
                var x = 0f
                while (x < w) { rejilla.moveTo(x, 0f); rejilla.lineTo(x, h); x += p2 }
                var y = 0f
                while (y < h) { rejilla.moveTo(0f, y); rejilla.lineTo(w, y); y += p2 }
            }
            val rayas = Path()
            if (textura == 2) {
                val p3 = 14.dp.toPx()
                var x = -h
                while (x < w) { rayas.moveTo(x, h); rayas.lineTo(x + h, 0f); x += p3 }
            }
            // líneas de barrido tipo pantalla vieja (Pixel y Táctico)
            val barrido = Path()
            if (pixel || tactico) {
                val p4 = 3.dp.toPx()
                var y = 0f
                while (y < h) { barrido.moveTo(0f, y); barrido.lineTo(w, y); y += p4 }
            }
            val brillo1 = Brush.radialGradient(
                listOf(acento.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(w * 0.12f, h * 0.10f), radius = size.minDimension * 0.85f,
            )
            val brillo2 = Brush.radialGradient(
                listOf(segundo.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(w * 0.95f, h * 0.88f), radius = size.minDimension * 0.9f,
            )
            // ---- marco del visor ----
            val m = 3.dp.toPx()
            val cm = 26.dp.toPx()
            val marco = Path()
            val esquinaA = Path()
            val esquinaB = Path()
            val advertencia = Path()
            if (filo) {
                marco.moveTo(m + cm, m); marco.lineTo(w - m, m); marco.lineTo(w - m, h - m - cm)
                marco.lineTo(w - m - cm, h - m); marco.lineTo(m, h - m); marco.lineTo(m, m + cm); marco.close()
                esquinaA.moveTo(m, m + cm * 2.2f); esquinaA.lineTo(m, m + cm); esquinaA.lineTo(m + cm, m); esquinaA.lineTo(m + cm * 3.5f, m)
                esquinaB.moveTo(w - m, h - m - cm * 2.2f); esquinaB.lineTo(w - m, h - m - cm)
                esquinaB.lineTo(w - m - cm, h - m); esquinaB.lineTo(w - m - cm * 3.5f, h - m)
                val p5 = 9.dp.toPx()
                val largo = 70.dp.toPx()
                for (i in 0 until 7) {
                    val x = m + 6.dp.toPx() + i * p5
                    advertencia.moveTo(x, h - m); advertencia.lineTo(x + largo * 0.35f, h - m - largo * 0.35f)
                }
            }
            if (pixel) {
                // marco con escalones en dos esquinas
                val pasos = 5
                val s = cm / pasos
                marco.moveTo(m, m + cm)
                for (i in 1..pasos) { marco.lineTo(m + (i - 1) * s, m + cm - i * s); marco.lineTo(m + i * s, m + cm - i * s) }
                marco.lineTo(w - m, m); marco.lineTo(w - m, h - m - cm)
                for (i in 1..pasos) { marco.lineTo(w - m - (i - 1) * s, h - m - cm + i * s); marco.lineTo(w - m - i * s, h - m - cm + i * s) }
                marco.lineTo(m, h - m); marco.close()
            }
            if (tactico) {
                val l = 34.dp.toPx()
                fun esq(p: Path, x: Float, y: Float, sx: Float, sy: Float) { p.moveTo(x, y + sy * l); p.lineTo(x, y); p.lineTo(x + sx * l, y) }
                esq(esquinaA, m, m, 1f, 1f); esq(esquinaB, w - m, h - m, -1f, -1f)
                esq(esquinaB, w - m, m, -1f, 1f); esq(esquinaB, m, h - m, 1f, -1f)
                // retícula: cruces cada 64 dp
                val p6 = 64.dp.toPx()
                val c = 4.dp.toPx()
                var y = p6
                while (y < h - m) {
                    var x = p6
                    while (x < w - m) { advertencia.moveTo(x - c, y); advertencia.lineTo(x + c, y); advertencia.moveTo(x, y - c); advertencia.lineTo(x, y + c); x += p6 }
                    y += p6
                }
            }
            val g1 = 1.dp.toPx()
            val g2 = 2.dp.toPx()
            onDrawBehind {
                if (vidrio) { drawRect(brillo1); drawRect(brillo2) }
                if (puntos.isNotEmpty()) {
                    drawPoints(puntos, PointMode.Points, Color.White.copy(alpha = if (vidrio) 0.05f else if (pixel) 0.03f else 0.035f),
                        strokeWidth = tamPunto, cap = StrokeCap.Square)
                }
                if (textura == 1) drawPath(rejilla, Color.White.copy(alpha = 0.035f), style = Stroke(g1))
                if (textura == 2) drawPath(rayas, Color.White.copy(alpha = 0.03f), style = Stroke(g1))
                if (pixel || tactico) drawPath(barrido, Color.White.copy(alpha = 0.018f), style = Stroke(g1))
                if (filo || pixel) {
                    if (neon) drawPath(marco, acento.copy(alpha = 0.10f), style = Stroke(6.dp.toPx()))
                    drawPath(marco, if (neon) acento.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f), style = Stroke(if (pixel) g2 else g1))
                }
                if (filo || tactico) {
                    drawPath(esquinaA, acento.copy(alpha = 0.8f), style = Stroke(g2))
                    drawPath(esquinaB, acento.copy(alpha = 0.5f), style = Stroke(g2))
                }
                if (filo) drawPath(advertencia, acento.copy(alpha = 0.10f), style = Stroke(g2))
                if (tactico) drawPath(advertencia, acento.copy(alpha = 0.12f), style = Stroke(g1))
            }
        }
    )
}

private fun tipografia(): Typography {
    val b = Typography()
    fun TextStyle.f() = copy(fontFamily = Fuentes.ui)
    return Typography(
        displayLarge = b.displayLarge.f(),
        displayMedium = b.displayMedium.f(),
        displaySmall = b.displaySmall.f(),
        headlineLarge = b.headlineLarge.f(),
        headlineMedium = b.headlineMedium.f(),
        headlineSmall = b.headlineSmall.f(),
        titleLarge = b.titleLarge.f(),
        titleMedium = b.titleMedium.f(),
        titleSmall = b.titleSmall.f(),
        bodyLarge = b.bodyLarge.f(),
        bodyMedium = b.bodyMedium.f(),
        bodySmall = b.bodySmall.f(),
        labelLarge = b.labelLarge.f(),
        labelMedium = b.labelMedium.f(),
        labelSmall = b.labelSmall.f(),
    )
}

private val tipografiaApp = tipografia()

@Composable
fun TemaFT150(content: @Composable () -> Unit) {
    val acento = Paleta.lima
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = acento,
            onPrimary = Color.Black,
            primaryContainer = Paleta.limaOscuro,
            onPrimaryContainer = Paleta.texto,
            secondary = Paleta.plata,
            onSecondary = Color.Black,
            background = Paleta.fondo,
            onBackground = Paleta.texto,
            surface = Color(0xFF101315),
            onSurface = Paleta.texto,
            surfaceVariant = Color(0xFF1A1F22),
            onSurfaceVariant = Paleta.textoSec,
            surfaceContainerLowest = Paleta.fondo,
            surfaceContainerLow = Color(0xFF0E1113),
            surfaceContainer = Color(0xFF121619),
            surfaceContainerHigh = Color(0xFF1A1F22),
            surfaceContainerHighest = Color(0xFF262D31),
            outline = Color(0xFF2E363B),
            outlineVariant = Color(0xFF262D31),
            error = Paleta.rojo,
        ),
        // Material solo acepta esquinas "normales": en Pixel (escalones) usa cortes chicos
        shapes = Shapes(
            extraSmall = (Formas.chica as? CornerBasedShape) ?: CutCornerShape(4.dp),
            small = (Formas.chica as? CornerBasedShape) ?: CutCornerShape(4.dp),
            medium = (Formas.tarjeta as? CornerBasedShape) ?: CutCornerShape(8.dp),
            large = (Formas.tarjeta as? CornerBasedShape) ?: CutCornerShape(8.dp),
            extraLarge = (Formas.tarjeta as? CornerBasedShape) ?: CutCornerShape(8.dp),
        ),
        typography = tipografiaApp,
        content = content,
    )
}

/** Formatea un número con punto decimal. */
fun fmt(x: Float, dec: Int): String = String.format(Locale.US, "%.${dec}f", x)

/** Mayúsculas sin acentos (la letra pixelada no trae acentos). */
fun pixelTexto(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").uppercase(Locale.ROOT)

/** Íconos propios (así no hace falta la librería grande de íconos). */
object Iconos {
    private fun icono(nombre: String, bloque: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f)
            .apply { path(fill = SolidColor(Color.White), pathBuilder = bloque) }
            .build()

    val Play: ImageVector by lazy {
        icono("play") { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }
    }
    val Pausa: ImageVector by lazy {
        icono("pausa") {
            moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
            moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
        }
    }
    val Siguiente: ImageVector by lazy {
        icono("siguiente") {
            moveTo(5f, 6f); lineTo(14f, 12f); lineTo(5f, 18f); close()
            moveTo(16f, 6f); lineTo(18.5f, 6f); lineTo(18.5f, 18f); lineTo(16f, 18f); close()
        }
    }
    val Anterior: ImageVector by lazy {
        icono("anterior") {
            moveTo(5.5f, 6f); lineTo(8f, 6f); lineTo(8f, 18f); lineTo(5.5f, 18f); close()
            moveTo(19f, 6f); lineTo(10f, 12f); lineTo(19f, 18f); close()
        }
    }
    /** Tres deslizadores (ajustes), hecho con cuadros para que se vea pixel. */
    val Ajustes: ImageVector by lazy {
        icono("ajustes") {
            moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 7f); lineTo(3f, 7f); close()
            moveTo(14f, 3f); lineTo(18f, 3f); lineTo(18f, 9f); lineTo(14f, 9f); close()
            moveTo(3f, 11f); lineTo(21f, 11f); lineTo(21f, 13f); lineTo(3f, 13f); close()
            moveTo(6f, 9f); lineTo(10f, 9f); lineTo(10f, 15f); lineTo(6f, 15f); close()
            moveTo(3f, 17f); lineTo(21f, 17f); lineTo(21f, 19f); lineTo(3f, 19f); close()
            moveTo(12f, 15f); lineTo(16f, 15f); lineTo(16f, 21f); lineTo(12f, 21f); close()
        }
    }
    /** Cuadrícula de apps (launcher). */
    val Apps: ImageVector by lazy {
        icono("apps") {
            for (f in 0..2) for (c in 0..2) {
                val x = 3f + c * 7f
                val y = 3f + f * 7f
                moveTo(x, y); lineTo(x + 4f, y); lineTo(x + 4f, y + 4f); lineTo(x, y + 4f); close()
            }
        }
    }
    /** Manzana (fruta) hecha con cuadros: tallo, hoja y cuerpo redondo. */
    val Manzana: ImageVector by lazy {
        icono("manzana") {
            moveTo(11f, 2f); lineTo(13f, 2f); lineTo(13f, 6f); lineTo(11f, 6f); close()              // tallo
            moveTo(14f, 3f); lineTo(18f, 3f); lineTo(18f, 5f); lineTo(14f, 5f); close()              // hoja
            moveTo(7f, 6f); lineTo(17f, 6f); lineTo(17f, 7f); lineTo(19f, 7f); lineTo(19f, 9f)
            lineTo(20f, 9f); lineTo(20f, 17f); lineTo(19f, 17f); lineTo(19f, 19f); lineTo(17f, 19f)
            lineTo(17f, 21f); lineTo(7f, 21f); lineTo(7f, 19f); lineTo(5f, 19f); lineTo(5f, 17f)
            lineTo(4f, 17f); lineTo(4f, 9f); lineTo(5f, 9f); lineTo(5f, 7f); lineTo(7f, 7f); close() // cuerpo
        }
    }
    /** Micrófono hecho con cuadros (pixel). */
    val Microfono: ImageVector by lazy {
        icono("microfono") {
            moveTo(9f, 2f); lineTo(15f, 2f); lineTo(15f, 13f); lineTo(9f, 13f); close()
            moveTo(5f, 9f); lineTo(7f, 9f); lineTo(7f, 14f); lineTo(17f, 14f); lineTo(17f, 9f); lineTo(19f, 9f)
            lineTo(19f, 16f); lineTo(13f, 16f); lineTo(13f, 19f); lineTo(17f, 19f); lineTo(17f, 22f); lineTo(7f, 22f)
            lineTo(7f, 19f); lineTo(11f, 19f); lineTo(11f, 16f); lineTo(5f, 16f); close()
        }
    }
    /** Audífonos / casco (pixel). */
    val Auriculares: ImageVector by lazy {
        icono("auriculares") {
            moveTo(4f, 10f); lineTo(6f, 10f); lineTo(6f, 7f); lineTo(8f, 5f); lineTo(16f, 5f); lineTo(18f, 7f); lineTo(18f, 10f)
            lineTo(20f, 10f); lineTo(20f, 6f); lineTo(17f, 3f); lineTo(7f, 3f); lineTo(4f, 6f); close()
            moveTo(3f, 12f); lineTo(9f, 12f); lineTo(9f, 21f); lineTo(3f, 21f); close()
            moveTo(15f, 12f); lineTo(21f, 12f); lineTo(21f, 21f); lineTo(15f, 21f); close()
        }
    }
    val Nota: ImageVector by lazy {
        icono("nota") {
            moveTo(9f, 4f); lineTo(20f, 2f); lineTo(20f, 16f)
            curveTo(20f, 17.7f, 18.4f, 19f, 16.5f, 19f)
            curveTo(14.6f, 19f, 13f, 17.9f, 13f, 16.5f)
            curveTo(13f, 15.1f, 14.6f, 14f, 16.5f, 14f)
            curveTo(17.1f, 14f, 17.6f, 14.1f, 18f, 14.3f)
            lineTo(18f, 6.4f); lineTo(11f, 7.7f); lineTo(11f, 18f)
            curveTo(11f, 19.7f, 9.4f, 21f, 7.5f, 21f)
            curveTo(5.6f, 21f, 4f, 19.9f, 4f, 18.5f)
            curveTo(4f, 17.1f, 5.6f, 16f, 7.5f, 16f)
            curveTo(8.1f, 16f, 8.6f, 16.1f, 9f, 16.3f)
            close()
        }
    }
}
