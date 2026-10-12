package com.xof.ft150ts.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.BateriaTel
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.Moto
import com.xof.ft150ts.PAGINA_TABLERO
import com.xof.ft150ts.UbicacionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Ancho del riel de Vértice (horizontal). */
val ANCHO_RIEL = 78.dp

/**
 * Riel de Vértice: la navegación vive a la izquierda, centrada (lejos de los ganchos del soporte, que en horizontal
 * agarran las orillas de arriba y abajo) y libera la franja de arriba para el contenido.
 *
 * - Cabeza: en el tablero, el logo; en las demás secciones, la **Estela** (velocidad, marcha y RPM): el dato vital
 *   nunca desaparece al cambiar de sección.
 * - Centro: las cinco secciones. El resaltado (cortado a 45°) sigue al dedo mientras deslizas; se dibuja leyendo
 *   el pager en la fase de dibujo, así que no recompone nada.
 * - Pie: hora, GPS, batería del cel y el chip de conexión.
 * - La línea de carrera corre por la orilla derecha del riel y se engrosa en la sección activa.
 */
@Composable
internal fun RielVertice(
    pager: PagerState,
    paginas: List<String>,
    conexion: Conexion,
    onIr: (Int) -> Unit,
    onChip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val acento = Paleta.lima
    val borde = Paleta.borde
    val neon = com.xof.ft150ts.Apariencia.neon
    val vista = LocalView.current
    val enTablero by remember { derivedStateOf { pager.currentPage == PAGINA_TABLERO } }

    Column(modifier.width(ANCHO_RIEL).fillMaxHeight().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
            if (enTablero) LogoRiel() else Estela()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Línea de carrera + resaltado que sigue al dedo
            Canvas(Modifier.fillMaxSize()) {
                val n = paginas.size
                val alto = size.height / n
                val xLinea = size.width - 2.dp.toPx()
                drawRect(borde, Offset(xLinea, 0f), Size(1.dp.toPx(), size.height))
                val f = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (n - 1).toFloat())
                val y0 = f * alto
                val m = 5.dp.toPx()
                val c = 10.dp.toPx()
                val hoja = Path().apply {
                    val x0 = m; val x1 = size.width - 7.dp.toPx(); val ya = y0 + m; val yb = y0 + alto - m
                    moveTo(x0 + c, ya); lineTo(x1, ya); lineTo(x1, yb - c); lineTo(x1 - c, yb); lineTo(x0, yb); lineTo(x0, ya + c); close()
                }
                if (neon) drawPath(hoja, acento.copy(alpha = 0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx()))
                drawPath(hoja, acento)
                // la línea se engrosa a la altura de la sección y apunta al contenido
                drawRect(acento, Offset(xLinea - 1.dp.toPx(), y0 + m), Size(3.dp.toPx(), alto - 2 * m))
                val cy = y0 + alto / 2
                val punta = Path().apply {
                    moveTo(xLinea + 2.dp.toPx(), cy - 5.dp.toPx()); lineTo(size.width, cy); lineTo(xLinea + 2.dp.toPx(), cy + 5.dp.toPx()); close()
                }
                drawPath(punta, acento)
            }
            Column(Modifier.fillMaxSize()) {
                paginas.forEachIndexed { i, nombre ->
                    val activo = pager.currentPage == i
                    val etiqueta = when (i) { 0 -> "Apps"; paginas.size - 1 -> "Ajustes"; else -> nombre }
                    val color = if (activo) Color.Black else Paleta.textoSec
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(end = 7.dp)
                            .semantics { selected = activo; contentDescription = etiqueta }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab,
                            ) {
                                // Tic háptico corto solo al cambiar de sección (confirmación, no en cada toque)
                                if (!activo) vista.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onIr(i)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when (i) {
                            0 -> Icon(Iconos.Apps, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                            paginas.size - 1 -> Icon(Iconos.Ajustes, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
                            else -> {
                                val arte = when (nombre) { "Tablero" -> PIXEL_TABLERO; "Auto" -> PIXEL_MAPA; else -> PIXEL_MOTO }
                                IconoPixel(arte, color, Modifier.size(width = 17.dp, height = 15.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        EtiquetaPixel(etiqueta, color, 6.5.sp)
                    }
                }
            }
        }
        PieRiel(conexion, onChip)
    }
}

/** Logo en la cabeza del riel (en el tablero). Siete toques: el secreto de siempre. */
@Composable
private fun LogoRiel() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LogoChico()
    }
}

/**
 * Estela: velocidad, marcha y una barra de RPM en el color de su zona. Sale en todas las secciones menos el
 * tablero (ahí ya está el instrumento completo). Solo esto se recompone con la telemetría.
 */
@Composable
private fun Estela() {
    val r by Moto.rapida.enVivo()
    val vel = if (r.vel >= 0f) "${r.vel.roundToInt()}" else "--"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(vel, color = Paleta.texto, fontSize = if (vel.length > 2) 24.sp else 28.sp, fontFamily = Fuentes.logo, maxLines = 1)
        }
        EtiquetaPixel("km/h", Paleta.textoSec, 6.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val m = r.marcha
            val txt = when { m < 0 -> "-"; m == 0 -> "N"; else -> "$m" }
            Text(txt, color = if (m == 0) Paleta.verde else Paleta.lima, fontSize = 13.sp, fontFamily = Fuentes.logo)
            Spacer(Modifier.width(5.dp))
            val color = colorRpm(r.rpm, r.shift)
            val frac = (r.rpm / 10_000f).coerceIn(0f, 1f)
            Canvas(Modifier.size(width = 38.dp, height = 6.dp)) {
                val inc = size.height
                val fondo = Path().apply { moveTo(inc, 0f); lineTo(size.width, 0f); lineTo(size.width - inc, size.height); lineTo(0f, size.height); close() }
                drawPath(fondo, Color.White.copy(alpha = 0.08f))
                if (frac > 0f) {
                    val w = size.width * frac
                    val llena = Path().apply { moveTo(inc, 0f); lineTo(w, 0f); lineTo(w - inc, size.height); lineTo(0f, size.height); close() }
                    drawPath(llena, color)
                }
            }
        }
    }
}

private fun horaRiel(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

/** Pie del riel: hora, GPS y batería del cel en dos renglones pixel, y el chip de conexión. */
@Composable
private fun PieRiel(conexion: Conexion, onChip: () -> Unit) {
    val enPantalla by Lanzador.enPantalla.collectAsState()
    var hora by remember { mutableStateOf(horaRiel()) }
    LaunchedEffect(enPantalla) { while (enPantalla) { hora = horaRiel(); delay(10_000) } }
    val bat by BateriaTel.estado.collectAsStateWithLifecycle()
    val gps by UbicacionManager.estado.collectAsStateWithLifecycle()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(hora, color = Paleta.texto, fontSize = 14.sp, fontFamily = Fuentes.logo)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val colorGps = when {
                gps.conSenal -> Paleta.lima
                gps.buscando || gps.aprox -> Paleta.ambar
                else -> Paleta.textoSec
            }
            EtiquetaPixel(if (gps.conSenal) "gps" else "--", colorGps, 6.sp)
            val pct = bat.pct
            val colorBat = when {
                pct < 0 -> Paleta.textoSec
                bat.cargando -> Paleta.lima
                pct <= 15 -> Paleta.rojo
                pct <= 30 -> Paleta.ambar
                else -> Paleta.texto
            }
            EtiquetaPixel(if (pct >= 0) "$pct%" else "--", colorBat, 6.sp)
        }
        ChipConexion(conexion, compacto = true, onClick = onChip)
    }
}

/**
 * Línea de carrera sobre el contenido: sale del riel a 45° y corre hacia la derecha desvaneciéndose. Une
 * la navegación con la sección (es la misma línea, no un separador más). Se dibuja una vez por tamaño.
 */
@Composable
internal fun LineaCarrera(modifier: Modifier = Modifier) {
    val acento = Paleta.lima
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val h = size.height
        val g = 2.dp.toPx()
        val tramo = size.width * 0.55f
        drawLine(acento, Offset(0f, h), Offset(h, h - h + g / 2), strokeWidth = g)
        drawRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(acento, acento.copy(alpha = 0f)), startX = h, endX = h + tramo),
            Offset(h, 0f), Size(tramo, g),
        )
    }
}
