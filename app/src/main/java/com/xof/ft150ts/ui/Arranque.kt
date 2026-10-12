package com.xof.ft150ts.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xof.ft150ts.Apariencia

/**
 * Animación de entrada al tablero (como una moto de verdad al dar switch):
 * 1) una franja del acento cortada a 45° barre la pantalla de izquierda a derecha,
 * 2) la barra / aguja de RPM sube hasta el tope (con la inercia del PID), se queda un instante y regresa,
 * 3) mientras, los dígitos hacen la prueba de segmentos (888).
 * Corre cada vez que entras al tablero (al cambiar de página, al prender la pantalla o después de la animación FT150).
 */
const val DURACION_ARRANQUE = 1700f   // ms

private val SIN_ARRANQUE: State<Float> = mutableFloatStateOf(-1f)

/** Progreso de la entrada: -1 = no hay animación; 0..1 = corriendo. Se lee al dibujar (no recompone). */
val LocalArranque = compositionLocalOf { SIN_ARRANQUE }

@Composable
fun rememberArranque(): State<Float> {
    val visible = LocalPaginaVisible.current
    val ctx = LocalContext.current
    val reducidas = remember { animacionesReducidas(ctx) }
    val activo = Apariencia.animEntrada && !reducidas
    // Nuevo estado cada vez que la página se vuelve visible (ya arranca en 0: sin un cuadro "de más")
    val p = remember(visible, activo) { mutableFloatStateOf(if (visible && activo) 0f else -1f) }
    LaunchedEffect(p) {
        if (p.floatValue < 0f) return@LaunchedEffect
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

/**
 * Franja de barrido a 45° encima del contenido (no lo tapa: solo pasa por encima, con una estela tenue).
 * Solo dibuja algo mientras la animación corre.
 */
fun Modifier.barrido45(p: State<Float>, color: Color): Modifier = drawWithContent {
    drawContent()
    val x = p.value
    if (x < 0f || x > 0.40f) return@drawWithContent
    val q = FastOutSlowInEasing.transform(x / 0.40f)
    val w = size.width
    val h = size.height
    val g = 12.dp.toPx()
    val borde = -h + q * (w + 2f * h + g)
    // estela: lo que ya pasó brilla un poco
    val estela = Path().apply {
        moveTo(borde - 90.dp.toPx(), 0f); lineTo(borde, 0f); lineTo(borde - h, h); lineTo(borde - h - 90.dp.toPx(), h); close()
    }
    drawPath(estela, color.copy(alpha = 0.10f))
    // franja principal + una delgada detrás
    val franja = Path().apply { moveTo(borde, 0f); lineTo(borde + g, 0f); lineTo(borde + g - h, h); lineTo(borde - h, h); close() }
    drawPath(franja, color.copy(alpha = 0.85f))
    val delgada = Path().apply {
        val d = g * 1.8f
        moveTo(borde - d, 0f); lineTo(borde - d + g * 0.35f, 0f); lineTo(borde - d + g * 0.35f - h, h); lineTo(borde - d - h, h); close()
    }
    drawPath(delgada, color.copy(alpha = 0.5f))
    // pixeles sueltos sobre la orilla de arriba
    val lado = 4.dp.toPx()
    for (i in 0..3) drawRect(color.copy(alpha = 0.7f - i * 0.15f), Offset(borde + g + lado * (1.5f + i * 2f), lado), Size(lado, lado))
}
