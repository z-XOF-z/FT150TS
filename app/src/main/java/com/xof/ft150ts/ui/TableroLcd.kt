package com.xof.ft150ts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.Moto
import com.xof.ft150ts.UbicacionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

// ============================================================
// Letra pixel 5x7 (cada número es una fila; bit 4 = columna izquierda)
// ============================================================
private const val ICO_TERM = '\u0001'
private const val ICO_GAS = '\u0002'
private const val ICO_BAT = '\u0003'

private val FUENTE_5X7: Map<Char, IntArray> = mapOf(
    '0' to intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110),
    '1' to intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
    '2' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111),
    '3' to intArrayOf(0b11111, 0b00010, 0b00100, 0b00010, 0b00001, 0b10001, 0b01110),
    '4' to intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010),
    '5' to intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110),
    '6' to intArrayOf(0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110),
    '7' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000),
    '8' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110),
    '9' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100),
    'A' to intArrayOf(0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
    'B' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110),
    'C' to intArrayOf(0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110),
    'D' to intArrayOf(0b11100, 0b10010, 0b10001, 0b10001, 0b10001, 0b10010, 0b11100),
    'E' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111),
    'F' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000),
    'G' to intArrayOf(0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01111),
    'H' to intArrayOf(0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
    'I' to intArrayOf(0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
    'J' to intArrayOf(0b00111, 0b00010, 0b00010, 0b00010, 0b00010, 0b10010, 0b01100),
    'K' to intArrayOf(0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001),
    'L' to intArrayOf(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111),
    'M' to intArrayOf(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001),
    'N' to intArrayOf(0b10001, 0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001),
    'O' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
    'P' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000),
    'Q' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101),
    'R' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001),
    'S' to intArrayOf(0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110),
    'T' to intArrayOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100),
    'U' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
    'V' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100),
    'W' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b10101, 0b01010),
    'X' to intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001),
    'Y' to intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100),
    'Z' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111),
    '/' to intArrayOf(0b00001, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b10000),
    '.' to intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b01100, 0b01100),
    ':' to intArrayOf(0b00000, 0b01100, 0b01100, 0b00000, 0b01100, 0b01100, 0b00000),
    '%' to intArrayOf(0b11000, 0b11001, 0b00010, 0b00100, 0b01000, 0b10011, 0b00011),
    '°' to intArrayOf(0b01100, 0b10010, 0b10010, 0b01100, 0b00000, 0b00000, 0b00000),
    '-' to intArrayOf(0b00000, 0b00000, 0b00000, 0b11111, 0b00000, 0b00000, 0b00000),
    '+' to intArrayOf(0b00000, 0b00100, 0b00100, 0b11111, 0b00100, 0b00100, 0b00000),
    '!' to intArrayOf(0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00000, 0b00100),
    ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
    ICO_TERM to intArrayOf(0b00100, 0b01010, 0b01010, 0b01010, 0b01110, 0b11111, 0b01110),
    ICO_GAS to intArrayOf(0b11100, 0b10101, 0b11101, 0b11101, 0b11101, 0b11110, 0b11100),
    ICO_BAT to intArrayOf(0b01110, 0b11111, 0b10001, 0b10001, 0b11111, 0b11111, 0b11111),
)

/** Ancho que ocupa un texto pixel con pixel de tamaño [px]. */
internal fun anchoPixel(texto: String, px: Float): Float = if (texto.isEmpty()) 0f else texto.length * 6 * px - px

/** Dibuja texto con la letra pixel 5x7. Cada "pixel" es un cuadrito con un hueco, como LCD. */
internal fun DrawScope.textoPixel(texto: String, x: Float, y: Float, px: Float, color: Color) {
    var cx = x
    val lado = px * 0.84f
    val vacio = FUENTE_5X7.getValue(' ')
    for (ch in texto) {
        val g = FUENTE_5X7[ch.uppercaseChar()] ?: vacio
        for (fila in 0 until 7) {
            val bits = g[fila]
            for (col in 0 until 5) {
                if ((bits shr (4 - col)) and 1 == 1) {
                    drawRect(color, Offset(cx + col * px, y + fila * px), Size(lado, lado))
                }
            }
        }
        cx += 6 * px
    }
}

// ============================================================
// Dígitos de 7 segmentos (inclinados, como LCD de moto)
// ============================================================
private val SEG7 = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
    '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
    '-' to "g", ' ' to "",
)
private const val INCLINACION = 0.13f

/** Ancho real de un dígito de ancho [w] y alto [h] (incluye la inclinación). */
internal fun anchoDigito(w: Float, h: Float) = w + h * INCLINACION

internal fun DrawScope.digito7(c: Char, x: Float, y: Float, w: Float, h: Float, on: Color, off: Color, p: Path) {
    val t = w * 0.21f
    val g = t * 0.22f
    val activos = SEG7[c] ?: ""
    val mitad = h / 2f
    for (s in "abcdefg") {
        p.reset()
        when (s) {
            'a' -> segH(p, x, y, h, t / 2f, t / 2f + g, w - t / 2f - g, t)
            'g' -> segH(p, x, y, h, mitad, t / 2f + g, w - t / 2f - g, t)
            'd' -> segH(p, x, y, h, h - t / 2f, t / 2f + g, w - t / 2f - g, t)
            'f' -> segV(p, x, y, h, t / 2f, t / 2f + g, mitad - g, t)
            'e' -> segV(p, x, y, h, t / 2f, mitad + g, h - t / 2f - g, t)
            'b' -> segV(p, x, y, h, w - t / 2f, t / 2f + g, mitad - g, t)
            'c' -> segV(p, x, y, h, w - t / 2f, mitad + g, h - t / 2f - g, t)
        }
        drawPath(p, if (s in activos) on else off)
    }
}

private fun segH(p: Path, ox: Float, oy: Float, h: Float, yc: Float, x0: Float, x1: Float, t: Float) {
    val e = t / 2f
    p.moveTo(ox + x0 + (h - yc) * INCLINACION, oy + yc)
    p.lineTo(ox + x0 + e + (h - (yc - e)) * INCLINACION, oy + yc - e)
    p.lineTo(ox + x1 - e + (h - (yc - e)) * INCLINACION, oy + yc - e)
    p.lineTo(ox + x1 + (h - yc) * INCLINACION, oy + yc)
    p.lineTo(ox + x1 - e + (h - (yc + e)) * INCLINACION, oy + yc + e)
    p.lineTo(ox + x0 + e + (h - (yc + e)) * INCLINACION, oy + yc + e)
    p.close()
}

private fun segV(p: Path, ox: Float, oy: Float, h: Float, xc: Float, y0: Float, y1: Float, t: Float) {
    val e = t / 2f
    p.moveTo(ox + xc + (h - y0) * INCLINACION, oy + y0)
    p.lineTo(ox + xc + e + (h - (y0 + e)) * INCLINACION, oy + y0 + e)
    p.lineTo(ox + xc + e + (h - (y1 - e)) * INCLINACION, oy + y1 - e)
    p.lineTo(ox + xc + (h - y1) * INCLINACION, oy + y1)
    p.lineTo(ox + xc - e + (h - (y1 - e)) * INCLINACION, oy + y1 - e)
    p.lineTo(ox + xc - e + (h - (y0 + e)) * INCLINACION, oy + y0 + e)
    p.close()
}

/**
 * Dibuja un número con [digitos] dígitos y [decimales] decimales, alineado a la derecha.
 * Los dígitos sin usar se ven "apagados" (fantasma), como en un LCD real.
 */
internal fun DrawScope.numero7(
    valor: Float?, digitos: Int, decimales: Int,
    x: Float, y: Float, w: Float, h: Float, paso: Float,
    on: Color, off: Color, p: Path,
) {
    val txt: String = if (valor == null || valor < 0f) "-".repeat(digitos)
    else {
        val escala = Math.pow(10.0, decimales.toDouble()).toFloat()
        val maxVal = Math.pow(10.0, digitos.toDouble()).toFloat() - 1f
        val entero = min(valor * escala + 0.5f, maxVal).toLong()
        var s = entero.toString()
        // asegura al menos un dígito antes del punto (ej. 0.5)
        while (s.length < decimales + 1) s = "0$s"
        s.padStart(digitos, ' ')
    }
    // los dígitos después del punto se recorren un poco para dejarle su hueco
    val iPunto = digitos - decimales
    val hueco = if (decimales > 0) w * 0.42f else 0f
    for (i in 0 until digitos) {
        digito7(txt[i], x + i * paso + if (i >= iPunto) hueco else 0f, y, w, h, on, off, p)
    }
    if (decimales > 0) {
        val lado = w * 0.22f
        val xPunto = x + (iPunto - 1) * paso + w + (paso - w + hueco) / 2f - lado / 2f
        drawRect(on, Offset(xPunto, y + h - lado), Size(lado, lado))
    }
}

private fun horaActual(): String = SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())

private val MODOS = listOf("TRIP" to "KM", "ODO" to "KM", "RANGO" to "KM", "PROM" to "KM/L")

// ============================================================
// Tablero LCD Sport
// ============================================================
@Composable
fun TableroLcd(modifier: Modifier = Modifier) {
    // Optimización: los datos se leen DENTRO del Canvas. Así, 4 veces por segundo
    // solo se redibuja el dibujo y no se recompone la pantalla.
    val rS = Moto.rapida.enVivo()
    val eS = Moto.estado.enVivo()
    val vS = Moto.viaje.enVivo()
    val velS = UbicacionManager.velocidad.enVivo()
    // Entrada al tablero: la barra sube al tope y regresa (con la inercia del PID) y los dígitos marcan 888
    val arranqueS = LocalArranque.current
    val agujaS = pidAnimado({ rpmArranque(arranqueS.value, max(0, rS.value.rpm).toFloat()) })
    val excesoS = Moto.excesoVel.enVivo()
    val visible = LocalPaginaVisible.current

    val modoS = rememberSaveable { mutableIntStateOf(0) }
    val horaS = remember { mutableStateOf(horaActual()) }
    LaunchedEffect(visible) {
        while (visible) { horaS.value = horaActual(); delay(5_000) }
    }

    // Solo esto recompone: entrar o salir de la zona de cambio / del exceso de velocidad
    val enCambio by remember { derivedStateOf { val r = rS.value; r.rpm > 0 && r.rpm >= r.shift } }
    val exceso by excesoS
    val alfaS = parpadeoEstado((enCambio || exceso) && visible)
    val acento = Paleta.lima
    val alta = Paleta.rpmAlta
    val forma = Formas.tarjeta
    val ruta = remember { Path() }
    // Barra de RPM: curva (la original) o diagonal a 45° con bloques inclinados (más agresiva)
    val diagonal = when (Apariencia.barraRpm) { 1 -> false; 2 -> true; else -> temaIntegrado }
    val neon = Apariencia.neon
    val cacheBarra = remember { CacheBarra() }

    Box(
        modifier.aspectRatio(1.7f)
            .then(if (Apariencia.neon) Modifier.resplandor(forma, acento) else Modifier)
            .clip(forma).background(Color.Black)
            .border(if (temaPixel) 2.dp else 1.dp, acento.copy(alpha = if (Apariencia.neon) 0.8f else 0.35f), forma)
            .decoracionTema(forma, acento)
            .pointerInput(Unit) {
                // Toca la fila de abajo para cambiar TRIP / ODO / RANGO / PROMEDIO
                detectTapGestures { pos -> if (pos.y > size.height * 0.84f) modoS.intValue = (modoS.intValue + 1) % MODOS.size }
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val r = rS.value
            val e = eS.value
            val v = vS.value
            val velTel = velS.value
            val prueba = pruebaSegmentos(arranqueS.value)
            val vel = if (prueba) 888f else if (r.vel >= 0) r.vel else velTel
            val modo = modoS.intValue
            val hora = horaS.value
            val alfa = alfaS.value
            val enRojo = excesoS.value
            val u = size.height / 100f
            val w = size.width
            val apagado = Color.White.copy(alpha = 0.06f)
            val sec = Paleta.textoSec

            // ---------------- Barra de RPM curva (bloques) ----------------
            val n = 56
            val x0 = 1f * u
            val x1 = w - 1f * u
            val paso = (x1 - x0) / n
            val anchoBarra = paso * 0.62f
            val yPlano = 13f * u
            val yIni = 66f * u
            // Diagonal: sube exacto a 45° (lo que sube = lo que avanza) y luego recta
            val xPlano = if (diagonal) x0 + (yIni - yPlano) else w * 0.40f
            fun curva(x: Float): Float =
                if (x >= xPlano) yPlano
                else if (diagonal) yIni - (x - x0)
                else {
                    val t = ((x - x0) / (xPlano - x0)).coerceIn(0f, 1f)
                    yPlano + (yIni - yPlano) * (1f - sin(t * PI.toFloat() / 2f))
                }
            val bloqueH = 2.4f * u
            val huecoB = 0.9f * u
            val rpmAguja = agujaS.value
            val encendidas = if (rpmAguja > 1f) ((rpmAguja / RPM_MAX) * n).toInt().coerceIn(0, n) else 0
            // Diagonal: cada columna ya armada como un solo camino de bloques inclinados (se arma una vez por tamaño)
            if (diagonal && (cacheBarra.w != w || cacheBarra.h != size.height || cacheBarra.columnas.size != n)) {
                cacheBarra.w = w; cacheBarra.h = size.height
                val inclina = bloqueH * 0.6f
                cacheBarra.columnas = Array(n) { i ->
                    val xb = x0 + i * paso
                    val top = curva(xb + anchoBarra / 2f)
                    val largo = 10f * u + (15f * u) * (i / (n - 1f))
                    val bloques = max(1, (largo / (bloqueH + huecoB)).toInt())
                    Path().apply {
                        for (b in 0 until bloques) {
                            val y = top + b * (bloqueH + huecoB)
                            moveTo(xb + inclina, y); lineTo(xb + inclina + anchoBarra, y)
                            lineTo(xb + anchoBarra, y + bloqueH); lineTo(xb, y + bloqueH); close()
                        }
                    }
                }
                // riel: línea delgada encima que sube a 45° y sigue recta, con un corte a 45° al final
                cacheBarra.riel = Path().apply {
                    val d = 2.2f * u
                    moveTo(x0, yIni - d); lineTo(xPlano, yPlano - d); lineTo(x1 - 3f * u, yPlano - d); lineTo(x1, yPlano - d + 3f * u)
                }
            }
            if (diagonal) drawPath(cacheBarra.riel, acento.copy(alpha = 0.45f), style = Stroke(0.35f * u))
            for (i in 0 until n) {
                val xb = x0 + i * paso
                val rpmBarra = ((i + 0.5f) / n * RPM_MAX).toInt()
                val zona = colorRpm(rpmBarra, r.shift)
                val prendida = i < encendidas
                val color = when {
                    prendida -> if (r.rpm >= r.shift) zona.copy(alpha = alfa) else zona
                    rpmBarra >= r.shift -> alta.copy(alpha = 0.16f)
                    else -> apagado
                }
                if (diagonal) {
                    val col = cacheBarra.columnas[i]
                    // neón: halo alrededor de los bloques prendidos
                    if (neon && prendida) drawPath(col, color.copy(alpha = 0.28f * color.alpha), style = Stroke(1.1f * u))
                    drawPath(col, color)
                } else {
                    val top = curva(xb + anchoBarra / 2f)
                    val largo = 10f * u + (15f * u) * (i / (n - 1f))
                    val bloques = max(1, (largo / (bloqueH + huecoB)).toInt())
                    if (neon && prendida) {
                        val altoTotal = bloques * (bloqueH + huecoB) - huecoB
                        drawRect(color.copy(alpha = 0.22f * color.alpha), Offset(xb - 0.5f * u, top - 0.5f * u), Size(anchoBarra + u, altoTotal + u))
                    }
                    for (b in 0 until bloques) {
                        drawRect(color, Offset(xb, top + b * (bloqueH + huecoB)), Size(anchoBarra, bloqueH))
                    }
                }
            }
            // números x1000
            // el 0 y el 10 quedan pegados a las orillas sin salirse; los demás, repartidos
            val pxN = 1.05f * u
            val c0 = x0 + anchoPixel("0", pxN) / 2f
            val c10 = x1 - anchoPixel("10", pxN) / 2f
            for (k in 0..10) {
                val txt = "$k"
                val xk = c0 + (k / 10f) * (c10 - c0)
                val yk = curva(xk) - 9.5f * u
                textoPixel(txt, xk - anchoPixel(txt, pxN) / 2f, yk, pxN, if (k * 1000 >= r.shift) alta else sec)
            }
            textoPixel("X1000 R/MIN", 1f * u, 1f * u, 0.72f * u, sec)

            // ---------------- Marcha ----------------
            val gx0 = w * 0.19f
            val gx1 = w * 0.31f
            val gy0 = 46f * u
            val gy1 = 76f * u
            val borde = 0.5f * u
            drawRect(acento, Offset(gx0, gy0), Size(gx1 - gx0, 8f * u))
            drawRect(acento, Offset(gx0, gy0), Size(borde, gy1 - gy0))
            drawRect(acento, Offset(gx1 - borde, gy0), Size(borde, gy1 - gy0))
            drawRect(acento, Offset(gx0, gy1 - borde), Size(gx1 - gx0, borde))
            val pxG = 0.85f * u
            textoPixel("GEAR", (gx0 + gx1) / 2f - anchoPixel("GEAR", pxG) / 2f, gy0 + 1f * u, pxG, Color.Black)
            val marcha = when { prueba -> "8"; r.marcha < 0 -> "-"; r.marcha == 0 -> "N"; else -> "${r.marcha}" }
            val pxM = 2.3f * u
            val colorMarcha = if (r.marcha == 0) Paleta.verde else Paleta.texto
            textoPixel(marcha, (gx0 + gx1) / 2f - anchoPixel(marcha, pxM) / 2f, gy0 + 10.5f * u, pxM, colorMarcha)

            // ---------------- Velocidad (7 segmentos) ----------------
            val dh = 36f * u
            val dw = 24f * u
            val dPaso = dw + 4f * u
            val sx = w * 0.34f
            val sy = 39f * u
            // con exceso de velocidad los números se ponen rojos y parpadean
            val colorVel = if (enRojo) Paleta.rojo.copy(alpha = 0.35f + 0.65f * alfa) else Paleta.texto
            numero7(if (vel >= 0) vel else null, 3, 0, sx, sy, dw, dh, dPaso, colorVel, apagado, ruta)
            // líneas de barrido: textura de pixel/LCD sobre los números
            var ly = sy
            while (ly < sy + dh) {
                drawRect(Color.Black.copy(alpha = 0.32f), Offset(sx, ly), Size(3 * dPaso + dh * INCLINACION, 0.45f * u))
                ly += 1.6f * u
            }
            val xUnidad = sx + 3 * dPaso + dh * INCLINACION - 2f * u
            textoPixel("KM/H", xUnidad, 69f * u, 0.9f * u, acento)
            textoPixel(if (e.perfil == 1) "SPT" else "ECO", xUnidad + 4f * u, 41f * u, 0.9f * u,
                if (e.perfil == 1) alta else Paleta.verde)

            // ---------------- Fila: temperatura · batería · gasolina ----------------
            val fy = 80f * u
            val bh = 6f * u
            // temperatura
            textoPixel(ICO_TERM.toString(), 1f * u, fy, 1f * u, sec)
            val tLlenos = if (r.temp > -90) (((r.temp - 40f) / 80f) * 6f).toInt().coerceIn(0, 6) else 0
            bloquesInclinados(8f * u, fy + 0.5f * u, 44f * u, bh, 6, tLlenos, acento, apagado, ultimoRojo = r.temp > 110f)
            val tTxt = if (r.temp > -90) "${r.temp.toInt()}°" else "--°"
            textoPixel(tTxt, 46f * u, fy + 0.8f * u, 0.9f * u, if (r.temp > 110f) Paleta.rojo else Paleta.texto)
            // batería de la moto (al centro)
            val colorBat = when {
                r.vMoto < 0 -> sec
                r.vMoto < 12f -> Paleta.rojo
                r.vMoto > 15f -> Paleta.ambar
                else -> Paleta.verde
            }
            val bTxt = if (r.vMoto >= 0) fmt(r.vMoto, 1) + "V" else "--.-V"
            val pxB = 0.95f * u
            val anchoChip = 6f * u + anchoPixel(bTxt, pxB) + 4f * u
            val cx0 = w / 2f - anchoChip / 2f
            drawRect(colorBat.copy(alpha = 0.14f), Offset(cx0, fy - 0.5f * u), Size(anchoChip, bh + 1.5f * u))
            textoPixel(ICO_BAT.toString(), cx0 + 1.5f * u, fy + 0.2f * u, 0.85f * u, colorBat)
            textoPixel(bTxt, cx0 + 8f * u, fy + 0.6f * u, pxB, colorBat)
            // gasolina
            val gLlenos = if (r.gas >= 0) ((r.gas / 100f) * 6f + 0.5f).toInt().coerceIn(0, 6) else 0
            val colorGas = if (r.gas in 0..10) Paleta.rojo else acento
            textoPixel(ICO_GAS.toString(), w - 62f * u, fy, 1f * u, sec)
            bloquesInclinados(w - 55f * u, fy + 0.5f * u, w - 21f * u, bh, 6, gLlenos, colorGas, apagado, ultimoRojo = false)
            val gTxt = if (r.gas >= 0) "${r.gas}%" else "--%"
            textoPixel(gTxt, w - 19f * u, fy + 0.8f * u, 0.8f * u, colorGas)

            // ---------------- Fila de abajo: reloj · trip/odo · 18650 ----------------
            val by = 90.5f * u
            val sh = 8.5f * u
            val sw = 5.2f * u
            val sp = 6.6f * u
            // reloj HH:MM
            for (i in 0 until 4) {
                val xi = 1f * u + i * sp + if (i >= 2) 2.4f * u else 0f
                digito7(hora.getOrElse(i) { ' ' }, xi, by, sw, sh, acento, apagado, ruta)
            }
            val dosPuntos = 1f * u
            drawRect(acento, Offset(1f * u + 2 * sp + 0.2f * u, by + 2f * u), Size(dosPuntos, dosPuntos))
            drawRect(acento, Offset(1f * u + 2 * sp - 0.2f * u, by + 5.5f * u), Size(dosPuntos, dosPuntos))

            // modo (toca para cambiar)
            val (nombreModo, unidadModo) = MODOS[modo]
            textoPixel(nombreModo, 34f * u, by, 0.7f * u, acento)
            textoPixel(unidadModo, 34f * u, by + 5f * u, 0.6f * u, sec)
            val (valor, dec) = when (modo) {
                0 -> v.km to 1
                1 -> v.odometro to 1
                2 -> (if (v.autonomia >= 0) v.autonomia.toFloat() else -1f) to 0
                else -> v.kmPorLitro to 1
            }
            numero7(valor, 6, dec, 54f * u, by, sw, sh, sp, Paleta.texto, apagado, ruta)

            // batería 18650 del sistema
            val lx = w - 44f * u
            textoPixel("LI", lx, by, 0.7f * u, sec)
            textoPixel("ION", lx, by + 5f * u, 0.6f * u, sec)
            numero7(if (e.vLipo >= 0) e.vLipo else null, 3, 2, lx + 13f * u, by, sw, sh, sp, acento, apagado, ruta)
            textoPixel("V", w - 4f * u, by + 3f * u, 0.7f * u, sec)
        }
    }
}

/** Fila de bloques inclinados (barras de temperatura y gasolina). */
private fun DrawScope.bloquesInclinados(
    xIni: Float, y: Float, xFin: Float, h: Float, cuantos: Int, llenos: Int,
    color: Color, apagado: Color, ultimoRojo: Boolean,
) {
    val hueco = h * 0.25f
    val incl = h * 0.45f
    val ancho = (xFin - xIni - incl - hueco * (cuantos - 1)) / cuantos
    val p = Path()
    for (i in 0 until cuantos) {
        val x = xIni + i * (ancho + hueco)
        p.reset()
        p.moveTo(x + incl, y)
        p.lineTo(x + incl + ancho, y)
        p.lineTo(x + ancho, y + h)
        p.lineTo(x, y + h)
        p.close()
        val c = when {
            i >= llenos -> apagado
            ultimoRojo && i >= cuantos - 2 -> Paleta.rojo
            else -> color
        }
        drawPath(p, c)
    }
}

/** Columnas de la barra diagonal ya armadas (se rehacen solo si cambia el tamaño). */
private class CacheBarra {
    var w = 0f
    var h = 0f
    var columnas: Array<Path> = emptyArray()
    var riel = Path()
}
