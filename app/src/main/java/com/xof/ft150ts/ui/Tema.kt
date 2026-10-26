package com.xof.ft150ts.ui

import android.content.Context
import android.provider.Settings
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
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
/** v3.10: tema Racing Morado (los colores del tablero morado en toda la app). */
val temaRacing: Boolean get() = Apariencia.tema == TemaApp.RACING
/** Tema Pixel: los cortes a 45° hechos de escalones de pixel. */
val temaPixel: Boolean get() = Apariencia.tema == TemaApp.PIXEL
/** Tema Táctico: visor militar, esquinas con corchetes y retícula. */
val temaTactico: Boolean get() = Apariencia.tema == TemaApp.TACTICO
/** Tema Holo: visor holográfico, las cuatro esquinas a 45°, doble contorno y superficies teñidas del acento. */
val temaHolo: Boolean get() = Apariencia.tema == TemaApp.HOLO
/** Tema Ácido: cinta de peligro a 45°, corte grande arriba-izquierda y brillo tóxico (va con verde tóxico o fluorescentes). */
val temaAcido: Boolean get() = Apariencia.tema == TemaApp.ACIDO
/** Tema Circuito: pistas de PCB con dobleces a 45°, pads y vías; tarjetas con los cortes en las otras esquinas. */
val temaCircuito: Boolean get() = Apariencia.tema == TemaApp.CIRCUITO
/**
 * Tema Vértice: la línea de carrera. Toma el peso sólido de Carbono y la línea continua de Filo, y cambia la
 * organización: en horizontal la navegación es un riel a la izquierda (con la velocidad siempre visible), las
 * tarjetas son "losas" sin contorno y una sola línea del acento une el riel con el contenido.
 */
val temaVertice: Boolean get() = Apariencia.tema == TemaApp.VERTICE
/**
 * Tema Tinta: viñetas de cómic de moto. Tarjetas como cuadros de historieta (un poco inclinados), contorno de tinta
 * grueso, sombra sólida del acento desplazada (como impresión fuera de registro), trama de puntos en una esquina y,
 * de fondo, líneas de velocidad que salen de un punto de fuga. Todo se dibuja una vez por tamaño.
 */
val temaTinta: Boolean get() = Apariencia.tema == TemaApp.TINTA
/** Temas con la barra de arriba integrada (segmentos) y marco de visor. */
/** v3.4: receta de estilo activa (Aurora, Synth, Mezcla…), o null en los temas de siempre. */
val estiloTema: com.xof.ft150ts.EstiloTema? get() = Apariencia.estiloActual()
val temaIntegrado: Boolean get() = temaFilo || temaPixel || temaTactico || temaHolo || temaAcido || temaCircuito || temaVertice

/**
 * El sistema tiene las animaciones apagadas (Accesibilidad > Quitar animaciones, o escala de animación en 0).
 * Entonces la app tampoco anima: muestra el cuadro final directo.
 */
fun animacionesReducidas(ctx: Context): Boolean =
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Colores. El fondo es negro puro (pantalla AMOLED: se ve mejor y gasta menos).
 * El acento y los colores del shift light salen de Ajustes > Apariencia.
 */
object Paleta {
    val fondo: Color get() = if (temaRacing) Color(0xFF080510) else Color(0xFF000000)
    val texto: Color get() = if (temaRacing) Color(0xFFF7F2FF) else Color(0xFFEEF3EF)
    val textoSec: Color get() = if (temaRacing) Color(0xFFA69AB8) else Color(0xFF8D989C)
    val plata = Color(0xFFB8C2C6)
    val blanco = Color(0xFFF4F7F2)
    val ambar = Color(0xFFFFB020)
    val rojo = Color(0xFFFF3B3B)
    /** Verde fijo para "todo bien" (batería, neutral), no cambia con el acento. */
    val verde = Color(0xFF39E55A)

    // Superficies: sólidas en Carbono, translúcidas en Vidrio, casi negras en Filo (el acento manda);
    // en Holo, teñidas con un poquito del acento (se siente como luz proyectada)
    val superficie: Color get() = when {
        estiloTema != null -> Color(estiloTema!!.superficie)
        esVidrio -> Color(0x17FFFFFF); temaFilo -> Color(0xFF07090A); temaPixel -> Color(0xFF090B09)
        temaTactico -> Color(0xFF050706); temaHolo -> lima.copy(alpha = 0.05f).compositeOver(fondo)
        temaAcido -> Color(0xFF080905); temaCircuito -> Color(0xFF05090A); temaVertice -> Color(0xFF0C0F11)
        temaTinta -> Color(0xFF111111)
        else -> Color(0xFF0E1113)
    }
    val superficie2: Color get() = when {
        estiloTema != null -> Color(estiloTema!!.superficie2)
        esVidrio -> Color(0x24FFFFFF); temaFilo -> Color(0xFF12171A); temaPixel -> Color(0xFF141814)
        temaTactico -> Color(0xFF0F1412); temaHolo -> lima.copy(alpha = 0.10f).compositeOver(fondo)
        temaAcido -> Color(0xFF13160C); temaCircuito -> Color(0xFF0E1618); temaVertice -> Color(0xFF161B1E)
        temaTinta -> Color(0xFF1D1D1D)
        else -> Color(0xFF181D20)
    }
    val borde: Color get() = when {
        estiloTema != null -> Color(estiloTema!!.borde)
        esVidrio -> Color(0x33FFFFFF); temaFilo -> Color(0xFF222A2E); temaPixel -> Color(0xFF2A332A)
        temaTactico -> Color(0xFF1C2420); temaHolo -> lima.copy(alpha = 0.30f).compositeOver(fondo)
        temaAcido -> lima.copy(alpha = 0.22f).compositeOver(Color(0xFF15180D)); temaCircuito -> Color(0xFF1B2A2D)
        temaVertice -> Color(0xFF242B2F)
        temaTinta -> Color(0xFF8A8A8A)          // la tinta fuerte va en las tarjetas; lo demás, gris de lápiz
        else -> Color(0xFF262D31)
    }

    /** Tinta: el contorno de las viñetas (blanco papel sobre negro). */
    val tinta = Color(0xFFF2F0EA)

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
    const val CORTE_HOLO_TARJETA = 12
    const val CORTE_HOLO_CHICA = 7
    private val holoTarjeta = CutCornerShape(CORTE_HOLO_TARJETA.dp)
    private val holoChica = CutCornerShape(CORTE_HOLO_CHICA.dp)
    private val holoBoton = CutCornerShape(topStart = 12.dp, topEnd = 4.dp, bottomEnd = 12.dp, bottomStart = 4.dp)
    private val holoChip = CutCornerShape(5.dp)
    const val CORTE_ACIDO_TARJETA = 22
    const val CORTE_ACIDO_CHICA = 12
    private val acidoTarjeta = CutCornerShape(topStart = CORTE_ACIDO_TARJETA.dp, topEnd = 0.dp, bottomEnd = 6.dp, bottomStart = 0.dp)
    private val acidoChica = CutCornerShape(topStart = CORTE_ACIDO_CHICA.dp, topEnd = 0.dp, bottomEnd = 4.dp, bottomStart = 0.dp)
    private val acidoBoton = CutCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 6.dp, bottomStart = 0.dp)
    private val acidoChip = CutCornerShape(topStart = 7.dp, topEnd = 0.dp, bottomEnd = 3.dp, bottomStart = 0.dp)
    const val CORTE_CIRCUITO_TARJETA = 14
    const val CORTE_CIRCUITO_CHICA = 8
    private val circTarjeta = CutCornerShape(topStart = 0.dp, topEnd = CORTE_CIRCUITO_TARJETA.dp, bottomEnd = 0.dp, bottomStart = CORTE_CIRCUITO_TARJETA.dp)
    private val circChica = CutCornerShape(topStart = 0.dp, topEnd = CORTE_CIRCUITO_CHICA.dp, bottomEnd = 0.dp, bottomStart = CORTE_CIRCUITO_CHICA.dp)
    private val circBoton = CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp)
    private val circChip = CutCornerShape(4.dp)
    // Vértice: un vértice dominante arriba-izquierda (el de la línea de carrera) y un remate corto abajo-derecha
    const val CORTE_VERTICE_TARJETA = 18
    const val CORTE_VERTICE_CHICA = 11
    private val vertTarjeta = CutCornerShape(topStart = CORTE_VERTICE_TARJETA.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp)
    private val vertChica = CutCornerShape(topStart = CORTE_VERTICE_CHICA.dp, topEnd = 0.dp, bottomEnd = 5.dp, bottomStart = 0.dp)
    private val vertBoton = CutCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 12.dp, bottomStart = 0.dp)
    private val vertChip = CutCornerShape(topStart = 7.dp, topEnd = 0.dp, bottomEnd = 7.dp, bottomStart = 0.dp)
    // Tinta: cuadros de historieta con la orilla de arriba un poco inclinada (como dibujados a mano)
    private val tintaTarjeta = Vineta(5.dp, 3.dp)
    private val tintaChica = Vineta(3.dp, 2.dp)
    private val tintaBoton = Vineta(2.dp, 2.dp)
    private val tintaChip = CutCornerShape(2.dp)
    private val corteTarjeta = CutCornerShape(topStart = 14.dp, topEnd = 0.dp, bottomEnd = 14.dp, bottomStart = 0.dp)
    private val corteChica = CutCornerShape(topStart = 8.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp)
    private val corteBoton = CutCornerShape(topStart = 10.dp, topEnd = 0.dp, bottomEnd = 10.dp, bottomStart = 0.dp)
    private val corteChip = CutCornerShape(6.dp)
    private val redTarjeta = RoundedCornerShape(24.dp)
    private val redChica = RoundedCornerShape(14.dp)
    private val redBoton = RoundedCornerShape(50)
    private val redChip = RoundedCornerShape(50)

    val tarjeta: Shape get() = when {
        estiloTema != null -> FormasEstilo.de(estiloTema!!).tarjeta
        esVidrio -> redTarjeta; temaFilo -> filoTarjeta; temaPixel -> pixTarjeta; temaTactico -> tacTarjeta; temaHolo -> holoTarjeta
        temaAcido -> acidoTarjeta; temaCircuito -> circTarjeta; temaVertice -> vertTarjeta; temaTinta -> tintaTarjeta; else -> corteTarjeta
    }
    val chica: Shape get() = when {
        estiloTema != null -> FormasEstilo.de(estiloTema!!).chica
        esVidrio -> redChica; temaFilo -> filoChica; temaPixel -> pixChica; temaTactico -> tacChica; temaHolo -> holoChica
        temaAcido -> acidoChica; temaCircuito -> circChica; temaVertice -> vertChica; temaTinta -> tintaChica; else -> corteChica
    }
    val boton: Shape get() = when {
        estiloTema != null -> FormasEstilo.de(estiloTema!!).boton
        esVidrio -> redBoton; temaFilo -> filoBoton; temaPixel -> pixBoton; temaTactico -> tacBoton; temaHolo -> holoBoton
        temaAcido -> acidoBoton; temaCircuito -> circBoton; temaVertice -> vertBoton; temaTinta -> tintaBoton; else -> corteBoton
    }
    val chip: Shape get() = when {
        estiloTema != null -> FormasEstilo.de(estiloTema!!).chip
        esVidrio -> redChip; temaFilo -> filoChip; temaPixel -> pixChip; temaTactico -> tacChip; temaHolo -> holoChip
        temaAcido -> acidoChip; temaCircuito -> circChip; temaVertice -> vertChip; temaTinta -> tintaChip; else -> corteChip
    }

    /** ¿Es una viñeta grande de Tinta? (lleva trama de puntos en la esquina) */
    fun esVinetaGrande(forma: Shape) = forma === tintaTarjeta

    /** Corte del vértice (arriba-izquierda) en Vértice; 0 si no aplica. */
    fun corteVertice(forma: Shape): Int = when (forma) {
        vertTarjeta -> CORTE_VERTICE_TARJETA
        vertChica -> CORTE_VERTICE_CHICA
        else -> 0
    }

    /** Corte grande (arriba-izquierda) en Ácido, para la cinta de peligro; 0 si no aplica. */
    fun corteAcido(forma: Shape): Int = when (forma) {
        acidoTarjeta -> CORTE_ACIDO_TARJETA
        acidoChica -> CORTE_ACIDO_CHICA
        else -> 0
    }

    /** Corte de las esquinas en Circuito (arriba-derecha y abajo-izquierda); 0 si no aplica. */
    fun corteCircuito(forma: Shape): Int = when (forma) {
        circTarjeta -> CORTE_CIRCUITO_TARJETA
        circChica -> CORTE_CIRCUITO_CHICA
        else -> 0
    }

    /** Corte de las esquinas en Holo (para el contorno interior); 0 si no aplica. */
    fun corteHolo(forma: Shape): Int = when (forma) {
        holoTarjeta -> CORTE_HOLO_TARJETA
        holoChica -> CORTE_HOLO_CHICA
        else -> 0
    }

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

/**
 * Viñeta de historieta (tema Tinta): cuadro con la orilla de arriba inclinada [caida] (baja hacia la derecha) y la de
 * abajo inclinada [subida] al revés, para que no se vea como caja de computadora sino como cuadro dibujado.
 */
class Vineta(val caida: Dp, val subida: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = with(density) { caida.toPx() }.coerceAtMost(size.height / 6f)
        val b = with(density) { subida.toPx() }.coerceAtMost(size.height / 6f)
        val p = Path().apply {
            moveTo(0f, 0f); lineTo(size.width, a); lineTo(size.width, size.height); lineTo(0f, size.height - b); close()
        }
        return Outline.Generic(p)
    }
}

/**
 * Tinta: sombra sólida del acento desplazada abajo-derecha (va ANTES del clip, por fuera de la viñeta), como impresión
 * de cómic fuera de registro. Se arma una vez por tamaño.
 */
fun Modifier.sombraTinta(forma: Shape, color: Color): Modifier = drawWithCache {
    val camino = Path().apply { addOutline(forma.createOutline(size, layoutDirection, this@drawWithCache)) }
    val d = 4.dp.toPx()
    onDrawBehind {
        drawContext.transform.translate(d, d)
        drawPath(camino, color)
        drawContext.transform.translate(-d, -d)
    }
}

/**
 * Tinta: trama de puntos (halftone) en la esquina de arriba-derecha, que se hace más chica hacia el centro. Va DETRÁS
 * del contenido (no tapa números ni textos) y en una zona chica. Se dibuja una vez por tamaño.
 */
fun Modifier.tramaTinta(conTrama: Boolean, color: Color): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val paso = 6.dp.toPx()
    val radioMax = 1.7.dp.toPx()
    val zona = minOf(w * 0.24f, 84.dp.toPx())
    val altoZona = minOf(h * 0.4f, 44.dp.toPx())
    val puntos = ArrayList<Triple<Float, Float, Float>>()
    if (conTrama && w > 120.dp.toPx()) {
        var y = paso
        while (y < altoZona) {
            var x = w - paso
            while (x > w - zona) {
                // Más grande en la esquina, más chico hacia adentro (distancia normalizada)
                val k = 1f - ((w - x) / zona * 0.6f + y / altoZona * 0.4f)
                if (k > 0.08f) puntos.add(Triple(x, y, radioMax * k))
                x -= paso
            }
            y += paso
        }
    }
    onDrawWithContent {
        puntos.forEach { (x, y, r) -> drawCircle(color.copy(alpha = 0.22f), r, Offset(x, y)) }
        drawContent()
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
 * Holo: un segundo contorno por dentro (paralelo, tenue), la esquina de arriba-izquierda iluminada
 * y una regla de marcas arriba a la derecha, como un visor que mide. Se dibuja una vez por tamaño.
 */
fun Modifier.lineasHolo(corteDp: Int, color: Color): Modifier = if (corteDp <= 0) this else drawWithCache {
    val c = corteDp.dp.toPx()
    val d = 3.dp.toPx()
    val w = size.width
    val h = size.height
    // Octágono interior: metido d hacia adentro; el corte a 45° se acorta d·(2−√2)
    val ci = (c - d * 0.586f).coerceAtLeast(1f)
    val interior = Path().apply {
        moveTo(d + ci, d); lineTo(w - d - ci, d); lineTo(w - d, d + ci); lineTo(w - d, h - d - ci)
        lineTo(w - d - ci, h - d); lineTo(d + ci, h - d); lineTo(d, h - d - ci); lineTo(d, d + ci); close()
    }
    val esquina = Path().apply { moveTo(0f, c * 1.8f); lineTo(0f, c); lineTo(c, 0f); lineTo(c * 2.6f, 0f) }
    val marcas = Path()
    val paso = 6.dp.toPx()
    val xIni = w - c - 4.dp.toPx()
    val y0 = d + 3.dp.toPx()
    val n = ((w * 0.25f) / paso).toInt().coerceIn(0, 10)
    for (i in 0 until n) {
        val x = xIni - i * paso
        marcas.moveTo(x, y0); marcas.lineTo(x, y0 + if (i % 3 == 0) 5.dp.toPx() else 2.5.dp.toPx())
    }
    val fino = Stroke(1.dp.toPx())
    val grueso = Stroke(2.5.dp.toPx(), join = StrokeJoin.Miter)
    onDrawWithContent {
        drawContent()
        drawPath(interior, color.copy(alpha = 0.16f), style = fino)
        drawPath(esquina, color, style = grueso)
        if (w > c * 6) drawPath(marcas, color.copy(alpha = 0.45f), style = fino)
    }
}

/**
 * Ácido: el corte grande de arriba-izquierda en el acento y, detrás, tres franjas paralelas a 45° (como cinta de
 * peligro que se va apagando); abajo a la derecha un pedazo de cinta con rayas a 45°. Se dibuja una vez por tamaño.
 */
fun Modifier.cintaAcido(corteDp: Int, color: Color): Modifier = if (corteDp <= 0) this else drawWithCache {
    val c = corteDp.dp.toPx()
    val w = size.width
    val h = size.height
    val g = 3.dp.toPx()
    val sep = 6.dp.toPx()
    val filo = Path().apply { moveTo(0f, c); lineTo(c, 0f) }
    val franjas = (1..3).map { i -> Path().apply { val k = c + i * sep; moveTo(0f, k); lineTo(k, 0f) } }
    // Cinta abajo a la derecha: rayas a 45° de 4 dp con huecos de 4 dp
    val cinta = Path()
    val alto = 6.dp.toPx()
    val raya = 4.dp.toPx()
    val largo = minOf(56.dp.toPx(), w * 0.3f)
    var x = w - 10.dp.toPx() - largo
    while (x < w - 10.dp.toPx()) {
        cinta.moveTo(x, h); cinta.lineTo(x + raya, h); cinta.lineTo(x + raya + alto, h - alto); cinta.lineTo(x + alto, h - alto); cinta.close()
        x += raya * 2
    }
    onDrawWithContent {
        drawContent()
        franjas.forEachIndexed { i, p -> drawPath(p, color.copy(alpha = 0.55f - i * 0.17f), style = Stroke(g)) }
        drawPath(filo, color, style = Stroke(g * 1.2f))
        if (w > c * 5) drawPath(cinta, color.copy(alpha = 0.55f))
    }
}

/**
 * Circuito: los dos cortes marcados (arriba-derecha fuerte, abajo-izquierda tenue), una pista que sale de un pad
 * arriba a la izquierda, dobla a 45° y acaba en una vía, y una fila de pines abajo a la derecha.
 */
fun Modifier.pistasCircuito(corteDp: Int, color: Color): Modifier = if (corteDp <= 0) this else drawWithCache {
    val c = corteDp.dp.toPx()
    val w = size.width
    val h = size.height
    val g = 2.5.dp.toPx()
    val pad = 5.dp.toPx()
    val m = 7.dp.toPx()
    val x1 = minOf(w * 0.3f, 90.dp.toPx())
    val baja = 6.dp.toPx()
    val pista = Path().apply { moveTo(m + pad, m + pad / 2); lineTo(x1, m + pad / 2); lineTo(x1 + baja, m + pad / 2 + baja) }
    val via = Offset(x1 + baja + 2.dp.toPx(), m + pad / 2 + baja + 2.dp.toPx())
    val corteA = Path().apply { moveTo(w - c, 0f); lineTo(w, c) }
    val corteB = Path().apply { moveTo(0f, h - c); lineTo(c, h) }
    val pin = 2.5.dp.toPx()
    val fino = Stroke(1.2.dp.toPx())
    onDrawWithContent {
        drawContent()
        drawPath(corteA, color, style = Stroke(g))
        drawPath(corteB, color.copy(alpha = 0.45f), style = Stroke(g))
        if (w > c * 5) {
            drawRect(color.copy(alpha = 0.7f), Offset(m, m), Size(pad, pad), style = fino)
            drawPath(pista, color.copy(alpha = 0.45f), style = fino)
            drawCircle(color.copy(alpha = 0.6f), 2.2.dp.toPx(), via, style = fino)
            for (i in 0 until 4) drawRect(color.copy(alpha = 0.45f), Offset(w - 12.dp.toPx() - i * 7.dp.toPx(), h - 7.dp.toPx()), Size(pin, pin))
        }
    }
}

/**
 * Vértice — losa: sin contorno. El corte de arriba-izquierda (el vértice) se marca grueso en el acento y sigue un
 * tramo por arriba; detrás corre una luz de borde que se desvanece hacia la derecha. Así las tarjetas pesan como
 * Carbono pero no se ven como cajas. Se dibuja una vez por tamaño.
 */
fun Modifier.losaVertice(corteDp: Int, color: Color): Modifier = drawWithCache {
    val c = corteDp.dp.toPx()
    val w = size.width
    val g = 3.dp.toPx()
    val vertice = Path().apply {
        if (c > 0f) { moveTo(0f, c * 1.6f); lineTo(0f, c); lineTo(c, 0f); lineTo(c * 2.4f, 0f) }
    }
    val inicioLuz = if (c > 0f) c * 2.4f else 0f
    val luz = Brush.horizontalGradient(
        listOf(color.copy(alpha = 0.45f), Color.Transparent),
        startX = inicioLuz, endX = inicioLuz + (w - inicioLuz) * 0.7f,
    )
    val alto = 1.dp.toPx()
    onDrawWithContent {
        drawContent()
        drawRect(luz, Offset(inicioLuz, 0f), Size(w - inicioLuz, alto))
        if (c > 0f) drawPath(vertice, color, style = Stroke(g, join = StrokeJoin.Miter))
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

/** Adornos del tema en una tarjeta (Filo: acentos HUD, Pixel: escalones, Táctico: corchetes, Holo: doble contorno). */
fun Modifier.decoracionTema(forma: Shape, color: Color): Modifier = when {
    temaFilo -> acentosHud(Formas.corteFilo(forma), color)
    temaPixel -> acentosEscalon(forma, color)
    temaTactico -> corchetes(Formas.corteTactico(forma).coerceAtLeast(3), color)
    temaHolo -> lineasHolo(Formas.corteHolo(forma), color)
    temaAcido -> cintaAcido(Formas.corteAcido(forma), color)
    temaCircuito -> pistasCircuito(Formas.corteCircuito(forma), color)
    temaVertice -> losaVertice(Formas.corteVertice(forma), color)
    temaTinta -> tramaTinta(Formas.esVinetaGrande(forma), Paleta.tinta)
    else -> this
}

/**
 * Superficie de tarjeta según el tema. En Vidrio: fondo translúcido con un
 * borde de luz arriba (como el vidrio de iOS), sin desenfoque para no gastar batería.
 */
fun Modifier.panel(forma: Shape = Formas.tarjeta, conBorde: Boolean = true): Modifier {
    val neon = Apariencia.neon && conBorde
    val acento = Paleta.lima
    estiloTema?.let { return this.panelEstilo(it, forma, conBorde, neon) }
    if (temaTinta) {
        // Viñeta: sombra sólida del acento por fuera, papel negro, contorno de tinta grueso y trama en la esquina
        if (!conBorde) return this.clip(forma).background(Paleta.superficie)
        val conSombra = (if (neon) this.resplandor(forma, acento) else this).sombraTinta(forma, acento.copy(alpha = 0.85f))
        return conSombra.clip(forma).background(Paleta.superficie)
            .border(2.5.dp, Paleta.tinta, forma)
            .tramaTinta(Formas.esVinetaGrande(forma), Paleta.tinta)
    }
    if (temaVertice) {
        // Losa: degradado grafito de arriba hacia abajo y, en lugar de contorno, el vértice y la luz de borde
        val losa = (if (neon) this.resplandor(forma, acento) else this).clip(forma)
            .background(Brush.verticalGradient(listOf(Paleta.superficie2, Paleta.superficie)))
        return if (conBorde) losa.losaVertice(Formas.corteVertice(forma), acento) else losa
    }
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
                temaHolo -> acento.copy(alpha = 0.55f)
                temaAcido -> acento.copy(alpha = 0.4f)
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
    estiloTema?.let { e ->
        val acentoE = Paleta.lima
        Box(modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).background(Paleta.fondo).drawWithCache {
            val pintar = construirFondo(e, size, this, acentoE)
            onDrawBehind { pintar() }
        })
        return
    }
    val vidrio = esVidrio
    val filo = temaFilo
    val pixel = temaPixel
    val tactico = temaTactico
    val holo = temaHolo
    val acido = temaAcido
    val circuito = temaCircuito
    val vertice = temaVertice
    val tinta = temaTinta
    val neon = Apariencia.neon
    // Vértice (fibra de carbono) y Tinta (líneas de velocidad) traen su propia textura: no se les encima la de Ajustes
    val textura = if (vertice || tinta) 3 else Apariencia.fondo
    val acento = Paleta.lima
    val segundo = Paleta.rpmAlta
    Box(
        modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).background(Paleta.fondo).drawWithCache {
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
            // Holo: marco con las cuatro esquinas a 45°, esquinas iluminadas y una regla de marcas abajo
            val brilloHolo = Brush.radialGradient(
                listOf(acento.copy(alpha = 0.11f), Color.Transparent),
                center = Offset(w * 0.08f, h * 0.06f), radius = size.minDimension * 0.9f,
            )
            if (holo) {
                marco.moveTo(m + cm, m); marco.lineTo(w - m - cm, m); marco.lineTo(w - m, m + cm); marco.lineTo(w - m, h - m - cm)
                marco.lineTo(w - m - cm, h - m); marco.lineTo(m + cm, h - m); marco.lineTo(m, h - m - cm); marco.lineTo(m, m + cm); marco.close()
                esquinaA.moveTo(m, m + cm * 2f); esquinaA.lineTo(m, m + cm); esquinaA.lineTo(m + cm, m); esquinaA.lineTo(m + cm * 3f, m)
                esquinaB.moveTo(w - m, h - m - cm * 2f); esquinaB.lineTo(w - m, h - m - cm)
                esquinaB.lineTo(w - m - cm, h - m); esquinaB.lineTo(w - m - cm * 3f, h - m)
                val paso = 10.dp.toPx()
                var x = w * 0.35f
                var i = 0
                while (x < w * 0.65f) {
                    val largo = if (i % 5 == 0) 7.dp.toPx() else 3.dp.toPx()
                    advertencia.moveTo(x, h - m); advertencia.lineTo(x, h - m - largo)
                    x += paso; i++
                }
            }
            // Ácido: marco con el corte grande arriba-izquierda, franjas de peligro en dos esquinas y brillo tóxico abajo
            val franjasAcido = Path()
            val brilloAcido = Brush.radialGradient(
                listOf(acento.copy(alpha = 0.13f), Color.Transparent),
                center = Offset(w * 0.05f, h * 0.95f), radius = size.minDimension * 0.95f,
            )
            if (acido) {
                val ca = cm * 1.5f
                marco.moveTo(m + ca, m); marco.lineTo(w - m, m); marco.lineTo(w - m, h - m - cm * 0.4f)
                marco.lineTo(w - m - cm * 0.4f, h - m); marco.lineTo(m, h - m); marco.lineTo(m, m + ca); marco.close()
                esquinaA.moveTo(m, m + ca * 1.8f); esquinaA.lineTo(m, m + ca); esquinaA.lineTo(m + ca, m); esquinaA.lineTo(m + ca * 2.8f, m)
                // franjas rellenas a 45° (cinta de peligro): abajo a la izquierda y arriba a la derecha
                val alto = 9.dp.toPx()
                val raya = 7.dp.toPx()
                fun cinta(x0: Float, y: Float, n: Int, abajo: Boolean) {
                    for (i in 0 until n) {
                        val x = x0 + i * raya * 2
                        val s = if (abajo) -1f else 1f
                        franjasAcido.moveTo(x, y); franjasAcido.lineTo(x + raya, y)
                        franjasAcido.lineTo(x + raya + alto, y + s * alto); franjasAcido.lineTo(x + alto, y + s * alto); franjasAcido.close()
                    }
                }
                cinta(m + 8.dp.toPx(), h - m, 8, abajo = true)
                cinta(w - m - 8.dp.toPx() - 8 * raya * 2, m, 8, abajo = false)
            }
            // Circuito: pistas de PCB (rectas, un doblez a 45° y otra recta) con pad al inicio y vía al final.
            // Mismas pistas siempre (semilla fija), acomodadas a una rejilla de 16 dp.
            val pistas = Path()
            val vias = ArrayList<Offset>()
            val pads = ArrayList<Offset>()
            if (circuito) {
                val r = java.util.Random(150)
                val celda = 16.dp.toPx()
                val cols = (w / celda).toInt().coerceAtLeast(4)
                val filas = (h / celda).toInt().coerceAtLeast(4)
                repeat(18) {
                    var x = (1 + r.nextInt(cols - 2)) * celda
                    var y = (1 + r.nextInt(filas - 2)) * celda
                    pads.add(Offset(x, y))
                    pistas.moveTo(x, y)
                    val dx = if (r.nextBoolean()) 1f else -1f
                    val dy = if (r.nextBoolean()) 1f else -1f
                    x += dx * (2 + r.nextInt(6)) * celda; pistas.lineTo(x, y)
                    val d = (1 + r.nextInt(3)) * celda
                    x += dx * d; y += dy * d; pistas.lineTo(x, y)
                    if (r.nextBoolean()) { y += dy * (1 + r.nextInt(4)) * celda } else { x += dx * (1 + r.nextInt(4)) * celda }
                    pistas.lineTo(x, y)
                    vias.add(Offset(x, y))
                }
                // Marco con los cortes arriba-derecha y abajo-izquierda
                marco.moveTo(m, m); marco.lineTo(w - m - cm, m); marco.lineTo(w - m, m + cm); marco.lineTo(w - m, h - m)
                marco.lineTo(m + cm, h - m); marco.lineTo(m, h - m - cm); marco.close()
                esquinaA.moveTo(w - m - cm * 3f, m); esquinaA.lineTo(w - m - cm, m); esquinaA.lineTo(w - m, m + cm); esquinaA.lineTo(w - m, m + cm * 2f)
                esquinaB.moveTo(m + cm * 3f, h - m); esquinaB.lineTo(m + cm, h - m); esquinaB.lineTo(m, h - m - cm); esquinaB.lineTo(m, h - m - cm * 2f)
            }
            // Vértice: tejido de fibra de carbono (dos familias de líneas a ±45°) y un chevrón gigante casi invisible
            // a la derecha, apuntando hacia adelante (el vértice de la curva)
            val tejidoA = Path()
            val tejidoB = Path()
            val chevron = Path()
            if (vertice) {
                val paso = 6.dp.toPx()
                var x = -h
                while (x < w) { tejidoA.moveTo(x, h); tejidoA.lineTo(x + h, 0f); x += paso }
                x = 0f
                while (x < w + h) { tejidoB.moveTo(x, h); tejidoB.lineTo(x - h, 0f); x += paso }
                val alto = h * 0.62f
                val cx = w * 0.80f
                val cy = h * 0.5f
                val grueso = h * 0.07f
                chevron.moveTo(cx - alto * 0.5f, cy - alto / 2); chevron.lineTo(cx - alto * 0.5f + grueso, cy - alto / 2)
                chevron.lineTo(cx + grueso, cy); chevron.lineTo(cx - alto * 0.5f + grueso, cy + alto / 2)
                chevron.lineTo(cx - alto * 0.5f, cy + alto / 2); chevron.lineTo(cx, cy); chevron.close()
            }
            // Tinta: líneas de velocidad (cuñas finas) que salen de un punto de fuga a la izquierda, como viñeta de
            // persecución, y una trama de puntos que crece hacia abajo a la derecha. Semilla fija: siempre iguales.
            val velocidad = Path()
            val trama = ArrayList<Triple<Float, Float, Float>>()
            if (tinta) {
                val fx = -w * 0.15f
                val fy = h * 0.42f
                val largo = (w + h) * 1.4f
                val r = java.util.Random(150)
                repeat(56) {
                    val ang = Math.toRadians((-38.0 + r.nextDouble() * 76.0))
                    val ancho = Math.toRadians(0.25 + r.nextDouble() * 0.6)
                    val ini = w * (0.25f + r.nextFloat() * 0.35f)          // empiezan lejos del punto: centro limpio
                    fun punto(d: Float, a: Double) = Offset(fx + d * Math.cos(a).toFloat(), fy + d * Math.sin(a).toFloat())
                    val p0 = punto(ini, ang)
                    val p1 = punto(largo, ang - ancho)
                    val p2 = punto(largo, ang + ancho)
                    velocidad.moveTo(p0.x, p0.y); velocidad.lineTo(p1.x, p1.y); velocidad.lineTo(p2.x, p2.y); velocidad.close()
                }
                val paso = 11.dp.toPx()
                val rMax = 3.2.dp.toPx()
                var y = h * 0.55f
                while (y < h) {
                    var x = w * 0.55f
                    while (x < w) {
                        val k = ((x - w * 0.55f) / (w * 0.45f)) * 0.55f + ((y - h * 0.55f) / (h * 0.45f)) * 0.45f
                        if (k > 0.1f) trama.add(Triple(x, y, rMax * k))
                        x += paso
                    }
                    y += paso
                }
            }
            val g1 = 1.dp.toPx()
            val g2 = 2.dp.toPx()
            onDrawBehind {
                if (tinta) {
                    drawPath(velocidad, Color.White.copy(alpha = 0.045f))
                    trama.forEach { (x, y, rr) -> drawCircle(acento.copy(alpha = 0.10f), rr, Offset(x, y)) }
                }
                if (vertice) {
                    drawPath(tejidoA, Color.White.copy(alpha = 0.022f), style = Stroke(g1))
                    drawPath(tejidoB, Color.White.copy(alpha = 0.014f), style = Stroke(g1))
                    drawPath(chevron, acento.copy(alpha = 0.035f))
                }
                if (holo) drawRect(brilloHolo)
                if (acido) drawRect(brilloAcido)
                if (circuito) {
                    drawPath(pistas, acento.copy(alpha = 0.09f), style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Miter))
                    val lado = 5.dp.toPx()
                    pads.forEach { drawRect(acento.copy(alpha = 0.14f), Offset(it.x - lado / 2, it.y - lado / 2), Size(lado, lado)) }
                    vias.forEach { drawCircle(acento.copy(alpha = 0.18f), 3.dp.toPx(), it, style = Stroke(1.5.dp.toPx())) }
                }
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
                if (holo) {
                    if (neon) drawPath(marco, acento.copy(alpha = 0.10f), style = Stroke(6.dp.toPx()))
                    drawPath(marco, acento.copy(alpha = if (neon) 0.40f else 0.22f), style = Stroke(g1))
                    drawPath(esquinaA, acento.copy(alpha = 0.85f), style = Stroke(g2))
                    drawPath(esquinaB, acento.copy(alpha = 0.5f), style = Stroke(g2))
                    drawPath(advertencia, acento.copy(alpha = 0.28f), style = Stroke(g1))
                }
                if (acido || circuito) {
                    if (neon) drawPath(marco, acento.copy(alpha = 0.10f), style = Stroke(6.dp.toPx()))
                    drawPath(marco, if (neon) acento.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f), style = Stroke(g1))
                    drawPath(esquinaA, acento.copy(alpha = 0.85f), style = Stroke(if (acido) 3.dp.toPx() else g2))
                    if (circuito) drawPath(esquinaB, acento.copy(alpha = 0.5f), style = Stroke(g2))
                }
                if (acido) drawPath(franjasAcido, acento.copy(alpha = 0.16f))
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
