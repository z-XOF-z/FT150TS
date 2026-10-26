package com.xof.ft150ts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.BateriaTel
import com.xof.ft150ts.ClimaManager
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.UbicacionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

// ------------------------------------------------------------
// Tira de estado (el espacio libre de arriba a la derecha): hora, batería del cel, GPS y clima
// ------------------------------------------------------------

/** Lo que la tira muestra del GPS: estado (0 sin señal, 1 buscando, 2 con señal), barritas y satélites. */
@androidx.compose.runtime.Immutable
private data class ResumenGps(val estado: Int, val llenas: Int, val texto: String)

@androidx.compose.runtime.Immutable
private data class ResumenBat(val pct: Int, val cargando: Boolean)

private fun resumenGps(g: com.xof.ft150ts.EstadoGps) = ResumenGps(
    estado = when { g.conSenal -> 2; g.buscando || g.aprox -> 1; else -> 0 },
    llenas = when { !g.conSenal -> 0; g.precision in 0f..6f -> 4; g.precision in 0f..12f -> 3; g.precision in 0f..25f -> 2; else -> 1 },
    texto = when {
        g.conSenal -> "${g.usados}"
        g.visibles > 0 -> "${g.fuertes}"   // sin posición: los que llegan con buena señal, no los "en uso"
        else -> "--"
    },
)

private fun horaCorta(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

@Composable
fun TiraEstado(modifier: Modifier = Modifier, compacta: Boolean = false) {
    val enPantalla by Lanzador.enPantalla.collectAsState()
    var hora by remember { mutableStateOf(horaCorta()) }
    // La hora se revisa cada 10 s y solo con la app a la vista
    LaunchedEffect(enPantalla) { while (enPantalla) { hora = horaCorta(); delay(10_000) } }
    // El GPS y la batería cambian varias veces por segundo (precisión, señal, watts…), pero aquí solo se ve un
    // resumen: la tira se vuelve a armar solo cuando cambia ese resumen (derivedStateOf), no con cada dato.
    val batE = BateriaTel.estado.collectAsStateWithLifecycle()
    val gpsE = UbicacionManager.estado.collectAsStateWithLifecycle()
    val bat by remember { derivedStateOf { batE.value.let { ResumenBat(it.pct, it.cargando) } } }
    val gps by remember { derivedStateOf { resumenGps(gpsE.value) } }
    val clima by ClimaManager.clima.collectAsStateWithLifecycle()

    if (temaRacing) { TiraRacing(modifier, hora, bat, gps, compacta); return }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(hora, color = Paleta.texto, fontSize = 15.sp, fontFamily = Fuentes.logo)
        Diagonal()
        // GPS: barritas pixel (llenas con señal) + satélites
        val colorGps = when (gps.estado) {
            2 -> Paleta.lima
            1 -> Paleta.ambar
            else -> Paleta.textoSec.copy(alpha = 0.6f)
        }
        val llenas = gps.llenas
        Canvas(Modifier.size(width = 13.dp, height = 11.dp)) {
            val n = 4
            val hueco = 1.5.dp.toPx()
            val w = (size.width - hueco * (n - 1)) / n
            for (i in 0 until n) {
                val alto = size.height * (0.4f + 0.6f * i / (n - 1))
                drawRect(if (i < llenas) colorGps else colorGps.copy(alpha = 0.22f),
                    Offset(i * (w + hueco), size.height - alto), Size(w, alto))
            }
        }
        if (!compacta) EtiquetaPixel(gps.texto, colorGps, 8.sp)
        Diagonal()
        // Batería del cel: pila pixel + % (+ rayo si carga)
        val pct = bat.pct
        val colorBat = when {
            pct < 0 -> Paleta.textoSec
            bat.cargando -> Paleta.lima
            pct <= 15 -> Paleta.rojo
            pct <= 30 -> Paleta.ambar
            else -> Paleta.texto
        }
        Canvas(Modifier.size(width = 19.dp, height = 10.dp)) {
            val g = 1.3.dp.toPx()
            val cuerpo = size.width - 2.5.dp.toPx()
            drawRect(colorBat, Offset.Zero, Size(cuerpo, size.height), style = Stroke(g))
            drawRect(colorBat, Offset(cuerpo, size.height * 0.3f), Size(size.width - cuerpo, size.height * 0.4f))
            val lleno = ((pct.coerceIn(0, 100)) / 100f) * (cuerpo - g * 3)
            if (lleno > 0f) drawRect(colorBat, Offset(g * 1.5f, g * 1.5f), Size(lleno, size.height - g * 3))
        }
        if (bat.cargando) IconoPixel(PIXEL_RAYO, Paleta.lima, Modifier.size(width = 7.dp, height = 10.dp))
        EtiquetaPixel(if (pct >= 0) "$pct%" else "--", colorBat, 8.sp)
        val c = clima
        if (c != null && !compacta) {
            Diagonal()
            Text("${c.temp}°", color = Paleta.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Tira del tema Racing Morado (como la barra del tablero morado): hora grande, "MX", barras de señal (del GPS),
 * batería verde redondeada con el porcentaje. Letra limpia, sin pixeles.
 */
@Composable
private fun TiraRacing(modifier: Modifier, hora: String, bat: ResumenBat, gps: ResumenGps, compacta: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(hora, color = Paleta.texto, fontSize = 17.sp, fontFamily = Fuentes.ui, fontWeight = FontWeight.SemiBold)
        if (!compacta) Text("MX", color = Paleta.textoSec, fontSize = 9.sp, fontFamily = Fuentes.ui, fontWeight = FontWeight.SemiBold)
        val colorGps = if (gps.estado == 2) Paleta.texto else Paleta.textoSec.copy(alpha = 0.7f)
        val llenas = if (gps.estado == 0) 0 else gps.llenas
        Canvas(Modifier.size(width = 14.dp, height = 12.dp)) {
            val n = 4
            val hueco = 1.6.dp.toPx()
            val w = (size.width - hueco * (n - 1)) / n
            for (i in 0 until n) {
                val alto = size.height * (0.35f + 0.65f * i / (n - 1))
                drawRoundRect(if (i < llenas) colorGps else colorGps.copy(alpha = 0.25f), Offset(i * (w + hueco), size.height - alto), Size(w, alto),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.3f))
            }
        }
        val pct = bat.pct
        val relleno = when { pct in 0..15 -> Paleta.rojo; else -> Color(0xFF39FF14) }
        Canvas(Modifier.size(width = 24.dp, height = 12.dp)) {
            val g = 1.4.dp.toPx()
            val cuerpo = size.width - 2.5.dp.toPx()
            val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            drawRoundRect(Paleta.texto, Offset.Zero, Size(cuerpo, size.height), r, style = Stroke(g))
            drawRoundRect(Paleta.texto, Offset(cuerpo + 0.5.dp.toPx(), size.height * 0.3f), Size(size.width - cuerpo - 0.5.dp.toPx(), size.height * 0.4f),
                androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            val lleno = ((pct.coerceIn(0, 100)) / 100f) * (cuerpo - g * 3)
            if (lleno > 0f) drawRoundRect(relleno, Offset(g * 1.5f, g * 1.5f), Size(lleno, size.height - g * 3), androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()))
        }
        if (bat.cargando) IconoPixel(PIXEL_RAYO, Color(0xFF39FF14), Modifier.size(width = 7.dp, height = 10.dp))
        Text(if (pct >= 0) "$pct%" else "--", color = Paleta.texto, fontSize = 14.sp, fontFamily = Fuentes.ui, fontWeight = FontWeight.SemiBold)
    }
}

/** Separador inclinado a 45° (estilo HUD). */
@Composable
private fun Diagonal() {
    val color = Paleta.borde
    Canvas(Modifier.size(width = 7.dp, height = 14.dp)) {
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5.dp.toPx())
    }
}

// ------------------------------------------------------------
// Barra integrada del tema Filo: segmentos con cortes a 45° y un resaltado que se desliza con el dedo
// ------------------------------------------------------------

/** Ancho de cada segmento: los de solo ícono (apps / ajustes) son angostos. */
private fun anchoSegmento(nombre: String): Dp = if (nombre.isBlank()) 44.dp else 104.dp
private val HUECO = 4.dp
private val ALTO_SEG = 34.dp
private val CORTE_SEG = 10.dp

/**
 * Segmento con las esquinas de arriba-izquierda y abajo-derecha cortadas a 45° (Filo), en escalones de pixel
 * (Pixel), casi rectas con cortes chiquitos (Táctico) o las cuatro a 45° (Holo).
 */
private fun caminoSegmento(x: Float, w: Float, h: Float, corte: Float, p: Path): Path {
    p.reset()
    when {
        temaPixel -> {
            val pasos = 3
            val s = corte / pasos
            p.moveTo(x, corte)
            for (i in 1..pasos) { p.lineTo(x + (i - 1) * s, corte - i * s); p.lineTo(x + i * s, corte - i * s) }
            p.lineTo(x + w, 0f); p.lineTo(x + w, h - corte)
            for (i in 1..pasos) { p.lineTo(x + w - (i - 1) * s, h - corte + i * s); p.lineTo(x + w - i * s, h - corte + i * s) }
            p.lineTo(x, h); p.close()
        }
        temaTactico || temaHolo -> {
            // Táctico: cortes chiquitos; Holo: las cuatro esquinas a 45° más marcadas
            val c = corte * if (temaHolo) 0.7f else 0.35f
            p.moveTo(x + c, 0f); p.lineTo(x + w - c, 0f); p.lineTo(x + w, c); p.lineTo(x + w, h - c)
            p.lineTo(x + w - c, h); p.lineTo(x + c, h); p.lineTo(x, h - c); p.lineTo(x, c); p.close()
        }
        temaCircuito -> {
            // Circuito: cortes en las otras dos esquinas (arriba-derecha y abajo-izquierda)
            p.moveTo(x, 0f); p.lineTo(x + w - corte, 0f); p.lineTo(x + w, corte); p.lineTo(x + w, h)
            p.lineTo(x + corte, h); p.lineTo(x, h - corte); p.close()
        }
        else -> {
            p.moveTo(x + corte, 0f); p.lineTo(x + w, 0f); p.lineTo(x + w, h - corte)
            p.lineTo(x + w - corte, h); p.lineTo(x, h); p.lineTo(x, corte); p.close()
        }
    }
    return p
}

@Composable
fun NavegacionFilo(pager: PagerState, paginas: List<String>, onIr: (Int) -> Unit) {
    val anchos = paginas.map { anchoSegmento(it) }
    val total = anchos.fold(0.dp) { a, b -> a + b } + HUECO * (paginas.size - 1)
    val acento = Paleta.lima
    val borde = Paleta.borde
    val fondoSeg = Paleta.superficie
    val neon = Apariencia.neon
    val tactico = temaTactico
    val acido = temaAcido
    val camino = remember { Path() }
    val actual = pager.currentPage

    Box(Modifier.width(total).height(ALTO_SEG)) {
        // Fondo de los segmentos + resaltado que sigue al dedo (se lee el pager al dibujar: no recompone)
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            val corte = CORTE_SEG.toPx()
            val xs = FloatArray(paginas.size)
            val ws = FloatArray(paginas.size)
            var x = 0f
            for (i in paginas.indices) {
                xs[i] = x; ws[i] = anchos[i].toPx()
                x += ws[i] + HUECO.toPx()
            }
            for (i in paginas.indices) {
                val p = caminoSegmento(xs[i], ws[i], h, corte, camino)
                drawPath(p, fondoSeg)
                drawPath(p, borde, style = Stroke(1.dp.toPx()))
            }
            val f = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (paginas.size - 1).toFloat())
            val i0 = f.toInt().coerceAtMost(paginas.size - 1)
            val i1 = (i0 + 1).coerceAtMost(paginas.size - 1)
            val t = f - i0
            val xr = xs[i0] + (xs[i1] - xs[i0]) * t
            val wr = ws[i0] + (ws[i1] - ws[i0]) * t
            val resaltado = caminoSegmento(xr, wr, h, corte, camino)
            if (neon) {
                drawPath(resaltado, acento.copy(alpha = 0.10f), style = Stroke(10.dp.toPx()))
                drawPath(resaltado, acento.copy(alpha = 0.20f), style = Stroke(5.dp.toPx()))
            }
            drawPath(resaltado, acento)
            if (tactico) {
                // Táctico: marcas de mira a los lados del activo
                drawRect(acento, Offset(xr - 4.dp.toPx(), h / 2f - 1.dp.toPx()), Size(2.5.dp.toPx(), 2.dp.toPx()))
                drawRect(acento, Offset(xr + wr + 1.5.dp.toPx(), h / 2f - 1.dp.toPx()), Size(2.5.dp.toPx(), 2.dp.toPx()))
            }
            // rayita de luz bajo el resaltado
            drawRect(acento.copy(alpha = 0.35f), Offset(xr + corte, h + 2.dp.toPx()), Size(wr - corte, 1.5.dp.toPx()))
            if (acido) {
                // Ácido: tres rayitas a 45° a la izquierda del activo, como cinta de peligro
                val a = 4.dp.toPx()
                for (k in 0 until 3) {
                    val x0 = xr - 5.dp.toPx() - k * (a + 2.dp.toPx())
                    drawPath(Path().apply {
                        moveTo(x0 + h * 0.35f, h * 0.3f); lineTo(x0 + h * 0.35f + 1.5.dp.toPx(), h * 0.3f)
                        lineTo(x0 + 1.5.dp.toPx(), h * 0.7f); lineTo(x0, h * 0.7f); close()
                    }, acento.copy(alpha = 0.8f - k * 0.25f))
                }
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(HUECO)) {
            paginas.forEachIndexed { i, nombre ->
                val activo = i == actual
                val color = if (activo) Color.Black else Paleta.textoSec
                Box(
                    Modifier.width(anchos[i]).fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onIr(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        i == 0 -> Icon(Iconos.Apps, contentDescription = "Apps", tint = color, modifier = Modifier.size(17.dp))
                        i == paginas.size - 1 -> Icon(Iconos.Ajustes, contentDescription = "Ajustes", tint = color, modifier = Modifier.size(18.dp))
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            val arte = when (nombre) { "Tablero" -> PIXEL_TABLERO; "Auto" -> PIXEL_MAPA; else -> PIXEL_MOTO }
                            IconoPixel(arte, if (activo) Color.Black else color, Modifier.size(width = 15.dp, height = 13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(nombre.uppercase(), color = color, fontSize = 13.sp,
                                fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Línea de abajo de la barra en Filo: gruesa con el acento bajo el logo y los segmentos, baja a 45°,
 * sigue delgada y vuelve a subir a 45° bajo la tira de estado. Así la barra y las páginas quedan unidas.
 */
@Composable
fun LineaFilo(modifier: Modifier = Modifier, finAcentoVentana: () -> Float) {
    val acento = Paleta.lima
    val borde = Paleta.borde
    val pixel = temaPixel
    val neon = Apariencia.neon
    val izq = remember { mutableFloatStateOf(0f) }
    Canvas(modifier.fillMaxWidth().height(10.dp).onGloballyPositioned { izq.floatValue = it.boundsInWindow().left }) {
        val h = size.height
        val g = 3.dp.toPx()
        // termina justo donde acaban los segmentos (medido; si aún no se mide, a la mitad)
        val x1 = (finAcentoVentana() - izq.floatValue).takeIf { it > 0f && it < size.width * 0.75f } ?: (size.width * 0.5f)
        val baja = h - g
        if (neon) drawRect(acento.copy(alpha = 0.15f), Offset(0f, -2.dp.toPx()), Size(x1, g + 4.dp.toPx()))
        // gruesa (acento) a la izquierda
        drawRect(acento, Offset(0f, 0f), Size(x1, g))
        if (pixel) {
            // Pixel: baja en escalones
            val pasos = 3
            val s = baja / pasos
            for (i in 0 until pasos) drawRect(acento, Offset(x1 + i * s, (i + 1) * s - g * 0.2f), Size(s + 0.5f, g))
        } else {
            drawPath(Path().apply { moveTo(x1, 0f); lineTo(x1 + baja, baja); lineTo(x1 + baja, h); lineTo(x1, g); close() }, acento)
        }
        // delgada al centro
        val xDer = size.width * 0.78f
        drawRect(borde, Offset(x1 + baja, h - 1.dp.toPx()), Size(xDer - x1 - baja, 1.dp.toPx()))
        // sube a 45° y remata en acento tenue bajo la tira de estado
        drawLine(acento.copy(alpha = 0.6f), Offset(xDer, h - 0.5.dp.toPx()), Offset(xDer + baja, h - baja), strokeWidth = 1.5.dp.toPx())
        drawRect(acento.copy(alpha = 0.6f), Offset(xDer + baja, h - baja - 0.75.dp.toPx()), Size(size.width - xDer - baja, 1.5.dp.toPx()))
    }
}
