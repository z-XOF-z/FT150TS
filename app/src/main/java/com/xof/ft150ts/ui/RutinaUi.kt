package com.xof.ft150ts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.RutinasLogica
import com.xof.ft150ts.Rutinas

/**
 * Tarjeta de la rutina (Tablero y Auto): a dónde vas a esta hora y si llegas a tiempo. Solo aparece cuando hay una
 * predicción (cerca de tu hora de salida de siempre); el resto del tiempo no ocupa lugar.
 */
@Composable
fun TarjetaRutina(modifier: Modifier = Modifier) {
    val p by Rutinas.prediccion.collectAsStateWithLifecycle()
    val pr = p ?: return
    val m = pr.margenMin
    val color = when {
        m == null -> Paleta.lima
        m < 0 -> Paleta.rojo
        m < 5 -> Paleta.ambar
        else -> Paleta.verde
    }
    Column(modifier.fillMaxWidth().panel(Formas.chica).padding(horizontal = 14.dp, vertical = 10.dp)) {
        EtiquetaPixel("rutina", color, 8.sp)
        Text(Rutinas.texto(pr), color = Paleta.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (pr.entradaMin != null)
            Text("Entrada a las ${RutinasLogica.hhmm(pr.entradaMin)}", color = Paleta.textoSec, fontSize = 12.sp)
    }
}

/** Ajustes > Rutinas: qué aprendió el mapa y cómo avisa. */
@Composable
fun SeccionRutinas() {
    val activo by Rutinas.activo.collectAsStateWithLifecycle()
    val horario by Rutinas.usarHorario.collectAsStateWithLifecycle()
    val voz by Rutinas.vozTarde.collectAsStateWithLifecycle()
    val lugares by Rutinas.lugares.collectAsStateWithLifecycle()
    val patrones by Rutinas.patrones.collectAsStateWithLifecycle()
    val viajes by Rutinas.viajes.collectAsStateWithLifecycle()
    Seccion("Rutinas") {
        FilaSwitch("Aprender mis rutinas",
            "Con tus rodadas sabe a dónde vas a cada hora y te dice cuánto tardas y a qué hora llegas. Todo se queda en el teléfono",
            activo) { Rutinas.ponerActivo(it) }
        if (!activo) return@Seccion
        FilaSwitch("Horario de la escuela",
            "Lunes 7:00, martes a viernes 7:50: dice si llegas a tiempo", horario) { Rutinas.ponerHorario(it) }
        FilaSwitch("Avisar por voz si voy tarde", "Una vez por viaje, mientras vas rodando", voz) { Rutinas.ponerVozTarde(it) }
        Text("Aprendido de $viajes rodadas: ${lugares.size} lugares, ${patrones.size} rutinas.", color = Paleta.textoSec, fontSize = 12.sp)
        val dias = listOf("L-V", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        patrones.take(10).forEach { p ->
            val o = lugares.firstOrNull { it.id == p.origen }?.nombre ?: "?"
            val d = lugares.firstOrNull { it.id == p.destino }?.nombre ?: "?"
            Text("${dias[p.dia]} ${RutinasLogica.hhmm(p.salidaMin)} · $o → $d · ${p.duracionMin} min (${p.veces} veces)",
                color = Paleta.texto, fontSize = 13.sp)
        }
        if (patrones.isEmpty())
            Text("Todavía no hay rutinas: hacen falta al menos 2 viajes iguales el mismo día de la semana.", color = Paleta.textoSec, fontSize = 12.sp)
    }
}
