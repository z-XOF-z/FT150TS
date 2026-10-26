package com.xof.ft150ts.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlinx.coroutines.delay

private const val DURACION = 3200f

/** Progreso 0..1 de una parte de la animación que empieza en [ini] ms y dura [dur] ms. */
private fun fase(t: Float, ini: Float, dur: Float, suave: Boolean = true): Float {
    val x = ((t - ini) / dur).coerceIn(0f, 1f)
    return if (suave) FastOutSlowInEasing.transform(x) else x
}

private val ESTADOS = listOf("INICIANDO", "BLUETOOTH", "GPS", "SENSORES", "TABLERO LISTO")

/**
 * Animación de arranque:
 * 1) se abre una cortina de franjas verde lima,
 * 2) aparece "FT150" con un barrido y el "TS" chiquito,
 * 3) ITALIKA y la firma de XOF,
 * 4) barra de carga pixelada.
 * Tocar la pantalla la salta.
 */
@Composable
fun PantallaInicio(onTerminar: () -> Unit) {
    val t = remember { Animatable(0f) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        if (animacionesReducidas(ctx)) {
            // Animaciones del sistema apagadas: el logo ya armado, un momento, y listo
            t.snapTo(DURACION)
            delay(700)
        } else {
            t.animateTo(DURACION, tween(DURACION.toInt(), easing = LinearEasing))
            delay(120)
        }
        onTerminar()
    }
    val ms = t.value

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Paleta.fondo)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTerminar() },
        contentAlignment = Alignment.Center,
    ) {
        // v3.10: pantallas neón (Ajustes > Apariencia > Pantalla de inicio); "Al azar" elige una al abrir
        val variante = remember {
            val e = com.xof.ft150ts.Apariencia.estiloInicio
            if (e == com.xof.ft150ts.INICIOS.size - 1) 1 + (Math.random() * 3).toInt() else e
        }
        if (variante != 0) {
            val v = remember { com.xof.ft150ts.Actualizador.versionActual(ctx) }
            InicioNeon(variante, ms, v)
            return@BoxWithConstraints
        }
        val ancho = min(maxWidth.value, maxHeight.value * 1.3f)
        val tamLogo = (ancho / 4.3f).sp

        // Franjas diagonales de fondo (como las calcomanías del tanque)
        val pFondo = fase(ms, 900f, 900f)
        Box(Modifier.fillMaxSize().franjasCarreras(0.10f * pFondo))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ---- FT150 + TS ----
            val pLogo = fase(ms, 450f, 650f)
            val pTs = fase(ms, 1000f, 350f)
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .graphicsLayer { translationX = (1f - pLogo) * -60f }
                    .drawWithContent {
                        // Barrido de izquierda a derecha
                        val contenido = this
                        clipRect(right = size.width * pLogo) { contenido.drawContent() }
                    },
            ) {
                Text("FT150", color = Paleta.texto, fontSize = tamLogo, fontFamily = Fuentes.logo,
                    modifier = Modifier.alignByBaseline())
                Text(
                    "TS", color = Paleta.lima, fontSize = tamLogo * 0.34f, fontFamily = Fuentes.logo,
                    modifier = Modifier.alignByBaseline().padding(start = 4.dp)
                        .graphicsLayer { scaleX = pTs; scaleY = pTs; alpha = pTs },
                )
            }

            // ---- raya lima que se estira ----
            val pRaya = fase(ms, 950f, 450f)
            Canvas(Modifier.width((ancho * 0.8f).dp).height(7.dp)) {
                val w = size.width * pRaya
                val incl = size.height
                val p = Path().apply {
                    moveTo(incl, 0f); lineTo(w, 0f); lineTo(w - incl, size.height); lineTo(0f, size.height); close()
                }
                if (w > incl) drawPath(p, Paleta.lima)
            }

            Spacer(Modifier.height(18.dp))

            // ---- ITALIKA ----
            val pMarca = fase(ms, 1250f, 450f)
            Text(
                "ITALIKA", color = Paleta.plata, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 9.sp,
                modifier = Modifier.graphicsLayer { alpha = pMarca; translationY = (1f - pMarca) * 24f },
            )

            Spacer(Modifier.height(10.dp))

            // ---- firma XOF (máquina de escribir) ----
            val firma = "XOF CUSTOM DASH"
            val letras = (fase(ms, 1550f, 650f, suave = false) * firma.length).toInt()
            val cursor = if (((ms / 160f).toInt() % 2) == 0) "_" else " "
            Row {
                Text("< ", color = Paleta.lima, fontSize = 10.sp, fontFamily = Fuentes.pixel)
                Text(firma.take(letras) + cursor, color = Paleta.texto, fontSize = 10.sp, fontFamily = Fuentes.pixel)
                Text(" >", color = Paleta.lima, fontSize = 10.sp, fontFamily = Fuentes.pixel)
            }

            Spacer(Modifier.height(40.dp))

            // ---- barra de carga pixelada ----
            val pCarga = fase(ms, 1500f, 1500f, suave = false)
            val bloques = 16
            Canvas(Modifier.width((ancho * 0.7f).dp).height(12.dp)) {
                val hueco = 4.dp.toPx()
                val bw = (size.width - hueco * (bloques - 1)) / bloques
                val llenos = (pCarga * bloques).toInt()
                for (i in 0 until bloques) {
                    drawRect(
                        if (i < llenos) Paleta.lima else Paleta.superficie2,
                        Offset(i * (bw + hueco), 0f), Size(bw, size.height),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.width((ancho * 0.7f).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val estado = ESTADOS[(pCarga * (ESTADOS.size - 1)).toInt().coerceIn(0, ESTADOS.size - 1)]
                Text(estado, color = Paleta.textoSec, fontSize = 8.sp, fontFamily = Fuentes.pixel)
                Text("${(pCarga * 100).toInt()}%", color = Paleta.lima, fontSize = 8.sp, fontFamily = Fuentes.pixel)
            }
        }

        // ---- la cortina (va encima de todo y se abre) ----
        Cortina(ms, Modifier.fillMaxSize())

        // versión abajo (la real del APK, no un texto fijo)
        val version = remember { com.xof.ft150ts.Actualizador.versionActual(ctx) }
        Text(
            "v$version", color =Paleta.textoSec.copy(alpha = fase(ms, 1500f, 500f)), fontSize = 8.sp,
            fontFamily = Fuentes.pixel,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
        )
    }
}

/** Persiana de franjas verticales: cada franja se encoge hacia la derecha, una tras otra. */
@Composable
private fun Cortina(ms: Float, modifier: Modifier) {
    if (ms > 1300f) return   // ya se abrió: no dibuja nada
    Canvas(modifier) {
        val franjas = 9
        val fw = size.width / franjas
        for (i in 0 until franjas) {
            val p = fase(ms, 80f + i * 55f, 420f)
            val w = fw * (1f - p)
            if (w <= 0.5f) continue
            val x = i * fw + (fw - w)
            val color = if (i % 2 == 0) Paleta.lima else Paleta.limaOscuro
            drawRect(color, Offset(x, 0f), Size(w + 1f, size.height))
            // filo negro para que se vea el corte
            drawRect(Color.Black.copy(alpha = 0.35f), Offset(x, 0f), Size(3f, size.height))
        }
    }
}
