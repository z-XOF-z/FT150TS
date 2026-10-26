package com.xof.ft150ts.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.TIPOS_ARRANQUE
import kotlin.math.hypot

/**
 * Animación del switch (como una moto de verdad al dar switch). Sale UNA vez por encendido: cuando la app detecta que
 * abriste el switch (el cable de la moto empieza a cargar, ver Energia) o con "Probar" en Ajustes. Si en ese momento
 * estabas en otra página o con la pantalla apagada, sale en cuanto veas el tablero. Cambiar de página ya no la repite.
 *
 * En todas: la barra / aguja de RPM sube hasta el tope y regresa, y los dígitos hacen la prueba de segmentos (888).
 * Encima, una de estas (Ajustes > Apariencia):
 *  0 Barrido 45°: una franja del acento cortada a 45° barre la pantalla.
 *  1 Encendido CRT: una línea brillante al centro que se abre como tele vieja.
 *  2 Escáner: una línea vertical revela el tablero de izquierda a derecha.
 *  3 Pulso: anillos del acento salen del centro mientras el tablero aparece.
 *  4 Píxeles: el tablero aparece en bloques, como una pantalla LCD despertando.
 *  5 Al azar: una distinta cada vez.
 * Todo se dibuja encima del contenido leyendo el progreso al dibujar (no recompone) y las figuras se arman una vez
 * por tamaño (drawWithCache): mientras no hay animación no cuesta nada.
 */
const val DURACION_ARRANQUE = 1700f   // ms

private val SIN_ARRANQUE: State<Float> = mutableFloatStateOf(-1f)

/** Progreso de la entrada: -1 = no hay animación; 0..1 = corriendo. Se lee al dibujar (no recompone). */
val LocalArranque = compositionLocalOf { SIN_ARRANQUE }

/** Animación que está corriendo (ya resuelto "Al azar"). */
@Volatile private var tipoEnCurso = 0

@Composable
fun rememberArranque(): State<Float> {
    val visible = LocalPaginaVisible.current
    val ctx = LocalContext.current
    val reducidas = remember { animacionesReducidas(ctx) }
    val activo = Apariencia.animEntrada && !reducidas
    val pedido by Lanzador.switchAbierto.collectAsStateWithLifecycle()
    // Toca si hay un encendido que todavía no se mostró y el tablero se está viendo
    val toca = activo && visible && pedido > Lanzador.arranqueVisto
    // Nuevo estado por encendido (ya arranca en 0: sin un cuadro "de más")
    val p = remember(toca, pedido) { mutableFloatStateOf(if (toca) 0f else -1f) }
    LaunchedEffect(p) {
        if (p.floatValue < 0f) return@LaunchedEffect
        Lanzador.arranqueVisto = pedido
        tipoEnCurso = tipoParaCorrer(Apariencia.tipoArranque)
        val t0 = withFrameNanos { it }
        while (true) {
            val x = (withFrameNanos { it } - t0) / 1e6f / DURACION_ARRANQUE
            if (x >= 1f) break
            p.floatValue = x
        }
        p.floatValue = -1f
    }
    return p
}

/** "Al azar" (el último) elige una de las otras. */
fun tipoParaCorrer(elegido: Int, azar: Int = (Math.random() * 1000).toInt()): Int {
    val n = TIPOS_ARRANQUE.size - 1
    return if (elegido in 0 until n) elegido else azar.mod(n)
}

/** RPM que se muestran durante la entrada: suben al tope, se quedan un instante y bajan a las reales. */
fun rpmArranque(p: Float, real: Float, tope: Float = 10_000f): Float = when {
    p < 0f -> real
    p < 0.10f -> 0f
    p < 0.42f -> tope * FastOutSlowInEasing.transform((p - 0.10f) / 0.32f)
    p < 0.56f -> tope
    else -> tope + (real - tope) * FastOutSlowInEasing.transform(((p - 0.56f) / 0.44f).coerceIn(0f, 1f))
}

/** Prueba de segmentos: todos los dígitos en 8 mientras la aguja va arriba. */
fun pruebaSegmentos(p: Float): Boolean = p in 0.14f..0.60f

/** Compatibilidad: el nombre de siempre; dibuja la animación elegida. */
fun Modifier.barrido45(p: State<Float>, color: Color): Modifier = animacionSwitch(p, color)

/**
 * La animación del switch encima del contenido. Solo dibuja algo mientras corre; las figuras se arman una vez por
 * tamaño y en cada cuadro solo se mueven (translate) o cambian de opacidad.
 */
fun Modifier.animacionSwitch(p: State<Float>, color: Color): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    // ---- Barrido 45°: figuras en x = 0, se mueven con translate ----
    val g = 12.dp.toPx()
    val cola = 90.dp.toPx()
    val estela = Path().apply { moveTo(-cola, 0f); lineTo(0f, 0f); lineTo(-h, h); lineTo(-h - cola, h); close() }
    val franja = Path().apply { moveTo(0f, 0f); lineTo(g, 0f); lineTo(g - h, h); lineTo(-h, h); close() }
    val delgada = Path().apply {
        val d = g * 1.8f
        moveTo(-d, 0f); lineTo(-d + g * 0.35f, 0f); lineTo(-d + g * 0.35f - h, h); lineTo(-d - h, h); close()
    }
    val lado = 4.dp.toPx()
    // ---- CRT ----
    val brilloCrt = Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f), Color.Transparent))
    val linea = 2.dp.toPx()
    // ---- Escáner ----
    val haz = 26.dp.toPx()
    val brilloHaz = Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.55f), color), startX = -haz, endX = 0f)
    val rejilla = Path().apply {
        val paso = 24.dp.toPx()
        var x = 0f
        while (x < w) { moveTo(x, 0f); lineTo(x, h); x += paso }
        var y = 0f
        while (y < h) { moveTo(0f, y); lineTo(w, y); y += paso }
    }
    // ---- Pulso ----
    val centro = Offset(w / 2, h / 2)
    val radioMax = hypot(w / 2, h / 2)
    val anillo = Stroke(3.dp.toPx())
    // ---- Píxeles: cada bloque se prende en su momento (orden fijo, desde el centro con un poco de azar) ----
    val celda = 28.dp.toPx()
    val cols = (w / celda).toInt() + 1
    val filas = (h / celda).toInt() + 1
    val azar = java.util.Random(150)
    val umbral = FloatArray(cols * filas) { i ->
        val cx = (i % cols + 0.5f) * celda
        val cy = (i / cols + 0.5f) * celda
        (hypot(cx - centro.x, cy - centro.y) / radioMax) * 0.7f + azar.nextFloat() * 0.3f
    }
    val negro = Color.Black

    onDrawWithContent {
        drawContent()
        val x = p.value
        if (x < 0f) return@onDrawWithContent
        when (tipoEnCurso) {
            1 -> { // Encendido CRT: 0–0.12 la línea crece desde el centro; 0.12–0.40 se abre hacia arriba y abajo
                if (x > 0.40f) return@onDrawWithContent
                val abre = if (x < 0.12f) 0f else FastOutSlowInEasing.transform((x - 0.12f) / 0.28f)
                val mitad = (h / 2) * abre
                drawRect(negro, Offset.Zero, Size(w, (h / 2 - mitad).coerceAtLeast(0f)))
                drawRect(negro, Offset(0f, h / 2 + mitad), Size(w, (h / 2 - mitad).coerceAtLeast(0f)))
                val largo = if (x < 0.12f) w * FastOutSlowInEasing.transform(x / 0.12f) else w
                val alfa = if (x < 0.12f) 1f else (1f - abre)
                // la línea brillante (orilla de la imagen que se abre)
                for (y in floatArrayOf(h / 2 - mitad, h / 2 + mitad)) {
                    drawRect(color.copy(alpha = alfa), Offset((w - largo) / 2, y - linea / 2), Size(largo, linea))
                    if (alfa > 0.05f) translate(0f, y - 10.dp.toPx()) {
                        drawRect(brilloCrt, Offset((w - largo) / 2, 0f), Size(largo, 20.dp.toPx()), alpha = alfa * 0.5f)
                    }
                }
            }
            2 -> { // Escáner: el haz va de izquierda a derecha; adelante todavía está oscuro con la rejilla
                if (x > 0.45f) return@onDrawWithContent
                val bx = FastOutSlowInEasing.transform(x / 0.45f) * (w + haz)
                drawRect(negro.copy(alpha = 0.92f), Offset(bx, 0f), Size((w - bx).coerceAtLeast(0f), h))
                drawPath(rejilla, color.copy(alpha = 0.10f * (1f - x / 0.45f)), style = Stroke(1f))
                translate(bx, 0f) { drawRect(brilloHaz, Offset(-haz, 0f), Size(haz, h)) }
                drawRect(Color.White.copy(alpha = 0.9f), Offset(bx - 1.dp.toPx(), 0f), Size(2.dp.toPx(), h))
            }
            3 -> { // Pulso: el tablero aparece (0–0.25) y tres anillos salen del centro, uno tras otro
                if (x > 0.60f) return@onDrawWithContent
                if (x < 0.25f) drawRect(negro.copy(alpha = 1f - x / 0.25f))
                for (k in 0..2) {
                    val t = (x - k * 0.08f) / 0.45f
                    if (t <= 0f || t >= 1f) continue
                    val r = radioMax * FastOutSlowInEasing.transform(t)
                    drawCircle(color.copy(alpha = (1f - t) * 0.8f), r, centro, style = anillo)
                }
                drawCircle(color.copy(alpha = (1f - x / 0.6f) * 0.5f), 10.dp.toPx() * (1f + x * 4f), centro)
            }
            4 -> { // Píxeles: cada bloque se prende cuando le toca; recién prendido brilla del acento
                if (x > 0.45f) return@onDrawWithContent
                val t = x / 0.40f
                for (i in umbral.indices) {
                    val u = umbral[i]
                    val o = Offset((i % cols) * celda, (i / cols) * celda)
                    when {
                        t < u -> drawRect(negro, o, Size(celda, celda))
                        t < u + 0.08f -> drawRect(color.copy(alpha = 0.55f * (1f - (t - u) / 0.08f)), o, Size(celda - 1f, celda - 1f))
                    }
                }
            }
            else -> { // Barrido 45°
                if (x > 0.40f) return@onDrawWithContent
                val q = FastOutSlowInEasing.transform(x / 0.40f)
                val borde = -h + q * (w + 2f * h + g)
                translate(borde, 0f) {
                    drawPath(estela, color.copy(alpha = 0.10f))
                    drawPath(franja, color.copy(alpha = 0.85f))
                    drawPath(delgada, color.copy(alpha = 0.5f))
                    for (i in 0..3) drawRect(color.copy(alpha = 0.7f - i * 0.15f), Offset(g + lado * (1.5f + i * 2f), lado), Size(lado, lado))
                }
            }
        }
    }
}
