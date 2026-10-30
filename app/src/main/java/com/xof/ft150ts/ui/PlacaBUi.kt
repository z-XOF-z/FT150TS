package com.xof.ft150ts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.Moto
import com.xof.ft150ts.PlacaB

// ------------------------------------------------------------
// Placa base B (firmware v1.6+): entradas, relevadores, alarma de movimiento e IA de vibraciones
// ------------------------------------------------------------
@Composable
internal fun SeccionPlacaB() {
    val cfg by Moto.config.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val r by Moto.rapida.enVivo()
    val e by Moto.estado.enVivo()
    val nombres by PlacaB.nombres.collectAsStateWithLifecycle()
    val vibra by PlacaB.vibra.collectAsStateWithLifecycle()
    var renombrar by remember { mutableStateOf(false) }
    var olvidar by remember { mutableStateOf(false) }
    val c = cfg
    if (conexion != Conexion.CONECTADO || c == null || !c.placaB) return
    LaunchedEffect(Unit) { PlacaB.pedirVibra() }

    Seccion("Placa de la moto") {
        EtiquetaPixel("entradas", Paleta.lima, 7.sp)
        Dato("Freno", if (r.freno) "PISADO" else "suelto", if (r.freno) Paleta.rojo else Paleta.texto)
        Dato("Direccional", when {
            r.dirOlvidada -> "¡PRENDIDA HACE RATO!"
            r.direccional -> "parpadeando"
            else -> "apagada"
        }, if (r.dirOlvidada) Paleta.ambar else Paleta.texto)
        Dato("Frenadas (desde que prendió)", "${e.frenadas}")
        Dato("Celda de respaldo", if (e.cargandoCelda) "cargando" else "sin cargar")

        EtiquetaPixel("tus salidas", Paleta.lima, 7.sp)
        for (i in 0..2) {
            FilaSwitch(nombres[i], if (i < 2) "Relevador ${i + 2} (J7${i + 2}) · hasta ~3 A" else "Salida aislada (J19) · hasta 50 mA",
                c.reles.getOrElse(i) { false }) { PlacaB.salida(i, it) }
        }
        BotonDeportivo("Ponerles nombre…", { renombrar = true }, primario = false, tamanoTexto = 12.sp)

        EtiquetaPixel("alarma de movimiento", Paleta.lima, 7.sp)
        if (!c.alarmaPosible) {
            Text("Conecta la INT del MPU6050 al pin S del conector J11 (el de la LDR) para que la tarjeta sienta si mueven " +
                "la moto, aunque esté dormida.", color = Paleta.ambar, fontSize = 12.sp)
        }
        FilaSwitch("Alarma", if (e.alarmaArmada) "Armada: la moto está estacionada" else "Se arma sola 2 min después de quitar la llave",
            c.alarma) { PlacaB.alarma(it) }
        Text("Sensibilidad", color = Paleta.textoSec, fontSize = 13.sp)
        Selector(listOf("1", "2", "3", "4", "5"), (c.alarmaSens - 1).coerceIn(0, 4), { PlacaB.alarmaSens(it + 1) }, Modifier.fillMaxWidth())
        Text("Sirena", color = Paleta.textoSec, fontSize = 13.sp)
        val sirenas = listOf(0, 2, 3, 4)
        Selector(listOf("Ninguna") + nombres, sirenas.indexOf(c.alarmaSirena).coerceAtLeast(0),
            { PlacaB.alarmaSirena(sirenas[it]) }, Modifier.fillMaxWidth())
        Text("Si alguien la mueve y no giras la llave en 20 s: te escribe por Telegram (si lo pusiste) y suena la sirena " +
            "6 s. Sin Telegram ni sirena no vigila (no gasta).", color = Paleta.textoSec, fontSize = 11.sp)
        BotonDeportivo("Probar alarma", { PlacaB.alarmaPrueba() }, primario = false, tamanoTexto = 12.sp)

        EtiquetaPixel("llave de proximidad", Paleta.lima, 7.sp)
        FilaSwitch("Llave de proximidad", "Al girar la llave, la moto no arranca hasta que se conecte tu teléfono vinculado " +
            "(el POCO o tu iPhone) o escribas el código con los botones: arriba, arriba, abajo, OK. Nunca bloquea rodando.",
            c.llave, Paleta.ambar) { PlacaB.llave(it) }

        EtiquetaPixel("ia de vibraciones", Paleta.lima, 7.sp)
        FilaSwitch("Aprender cómo vibra tu moto", "Mientras ruedas aprende lo normal en cada rango de RPM y te avisa si algo " +
            "cambia (cadena, rueda, balero, tornillo suelto, motor).", c.vibra) { PlacaB.vibraActiva(it) }
        if (c.vibra) {
            val maduros = vibra?.maduros ?: c.vibraMaduros
            Dato("Rangos que ya conoce", "$maduros de 20" + if (maduros == 0) " (rueda unos minutos)" else "")
            Dato("Ahora", vibra?.let { v ->
                when {
                    v.alerta -> "VIBRA RARO (${fmt(v.score, 1)})"
                    v.score > 0f -> "normal (${fmt(v.score, 1)}; raro = más de 3.5)"
                    else -> "esperando a que ruedes"
                }
            } ?: "—", if (vibra?.alerta == true) Paleta.ambar else Paleta.texto)
            FilaSwitch("Guardar las lecturas", "En vibra.csv de la SD (bájalo con \"Bajar registros\"): sirve para entrenar " +
                "una red neuronal más fina después.", c.vibraCaptura) { PlacaB.vibraCaptura(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonDeportivo("Actualizar", { PlacaB.pedirVibra() }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                BotonDeportivo("Olvidar lo aprendido", { olvidar = true }, Modifier.weight(1f), primario = false,
                    color = Paleta.rojo, tamanoTexto = 12.sp)
            }
        }
    }

    if (renombrar) DialogoNombres { renombrar = false }
    if (olvidar) {
        AlertDialog(
            onDismissRequest = { olvidar = false },
            title = { Text("¿Olvidar lo aprendido?") },
            text = { Text("Úsalo después de arreglar algo (cadena nueva, llantas): vuelve a aprender desde cero cómo vibra tu moto.") },
            confirmButton = { TextButton(onClick = { olvidar = false; PlacaB.vibraOlvidar() }) { Text("Olvidar", color = Paleta.rojo) } },
            dismissButton = { TextButton(onClick = { olvidar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DialogoNombres(onCerrar: () -> Unit) {
    val actuales = PlacaB.nombres.value
    var n0 by remember { mutableStateOf(actuales[0]) }
    var n1 by remember { mutableStateOf(actuales[1]) }
    var n2 by remember { mutableStateOf(actuales[2]) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Nombre de cada salida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n0, { n0 = it.take(16) }, singleLine = true, label = { Text("Relevador 2") })
                OutlinedTextField(n1, { n1 = it.take(16) }, singleLine = true, label = { Text("Relevador 3") })
                OutlinedTextField(n2, { n2 = it.take(16) }, singleLine = true, label = { Text("Salida aislada") })
            }
        },
        confirmButton = {
            TextButton(onClick = { PlacaB.ponerNombre(0, n0); PlacaB.ponerNombre(1, n1); PlacaB.ponerNombre(2, n2); onCerrar() }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
