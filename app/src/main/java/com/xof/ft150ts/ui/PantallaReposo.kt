package com.xof.ft150ts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.BateriaTel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private fun horaReposo(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

/**
 * Reloj de reposo (moto parada y cargando): negro AMOLED, la hora en el acento atenuado y la batería del cel
 * en una franja a 45°. Cada 30 s todo se corre unos dp para que la pantalla no se marque. Tocar despierta.
 */
@Composable
fun PantallaReposo(onDespertar: () -> Unit) {
    var hora by remember { mutableStateOf(horaReposo()) }
    var paso by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { hora = horaReposo(); delay(30_000); paso++ }
    }
    val bat by BateriaTel.estado.collectAsStateWithLifecycle()
    val acento = Paleta.lima
    // Desplazamiento que recorre una rejilla de ±20 x ±14 dp sin repetirse seguido
    val dx = ((paso * 37) % 41 - 20).dp
    val dy = ((paso * 23) % 29 - 14).dp

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDespertar() },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.offset(dx, dy), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("FT150", color = Paleta.textoSec.copy(alpha = 0.55f), fontSize = 14.sp, fontFamily = Fuentes.logo,
                    modifier = Modifier.alignByBaseline())
                Text("TS", color = acento.copy(alpha = 0.55f), fontSize = 7.sp, fontFamily = Fuentes.logo,
                    modifier = Modifier.alignByBaseline().padding(start = 2.dp))
            }
            Text(hora, color = acento.copy(alpha = 0.6f), fontSize = 72.sp, fontFamily = Fuentes.logo)
            // Batería del cel: franja a 45° que se llena (bloques), como la barra de RPM
            val pct = bat.pct.coerceIn(0, 100)
            Canvas(Modifier.width(180.dp).height(10.dp)) {
                val n = 12
                val hueco = 3.dp.toPx()
                val incl = size.height
                val ancho = (size.width - incl - hueco * (n - 1)) / n
                val llenos = if (bat.pct < 0) 0 else ((pct / 100f) * n + 0.5f).toInt()
                for (i in 0 until n) {
                    val x = i * (ancho + hueco)
                    val p = Path().apply {
                        moveTo(x + incl, 0f); lineTo(x + incl + ancho, 0f); lineTo(x + ancho, size.height); lineTo(x, size.height); close()
                    }
                    drawPath(p, if (i < llenos) acento.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f))
                }
                // marca de inicio a 45°
                drawLine(acento.copy(alpha = 0.5f), Offset(0f, size.height), Offset(incl, 0f), 1.dp.toPx())
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EtiquetaPixel("reposo", acento.copy(alpha = 0.5f), 8.sp)
                EtiquetaPixel(
                    if (bat.pct >= 0) "${bat.pct}%" + if (bat.cargando) " cargando" else "" else "--",
                    Paleta.textoSec.copy(alpha = 0.55f), 8.sp,
                )
            }
            EtiquetaPixel("toca para despertar", Paleta.textoSec.copy(alpha = 0.3f), 7.sp, Modifier.padding(top = 16.dp))
        }
    }
}
