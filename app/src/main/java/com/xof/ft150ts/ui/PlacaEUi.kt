package com.xof.ft150ts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ------------------------------------------------------------
// Placa E (expansión I2C, firmware v1.7+): BOMBA, relevadores 2 y 3, salida aislada y 4 entradas
// ------------------------------------------------------------
@Composable
internal fun SeccionPlacaE() {
    val cfg by Moto.config.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val pe by PlacaB.placaE.collectAsStateWithLifecycle()
    val c = cfg
    if (conexion != Conexion.CONECTADO || c == null || !c.placaE) return
    // Mientras la BOMBA está prendida se pide el estado cada segundo (cuenta regresiva); si no, cada 5 s
    LaunchedEffect(Unit) {
        while (true) {
            PlacaB.pedirE()
            delay(if (PlacaB.placaE.value?.salidas?.firstOrNull() == true) 1_000 else 5_000)
        }
    }
    var minutos by remember { mutableIntStateOf(1) }
    val tiempos = listOf(1, 3, 5, 10)
    val e = pe

    Seccion("Placa E (expansión)") {
        if (e != null && !e.hay) Text("La placa E dejó de contestar: revisa el cable I2C (J12).", color = Paleta.rojo, fontSize = 12.sp)

        EtiquetaPixel("bomba", Paleta.lima, 7.sp)
        val bomba = e?.salidas?.getOrNull(0) == true
        Dato("Estado", when {
            bomba && (e?.bombaResta ?: -1) >= 0 -> "INFLANDO · quedan ${e!!.bombaResta / 60}:${"%02d".format(e.bombaResta % 60)}"
            bomba -> "INFLANDO"
            (e?.descanso ?: 0) > 0 -> "descansando ${e!!.descanso / 60}:${"%02d".format(e.descanso % 60)}"
            else -> "apagada"
        }, if (bomba) Paleta.ambar else Paleta.texto)
        if (!bomba && !e?.motivo.isNullOrBlank()) Text("Se apagó: ${e!!.motivo}", color = Paleta.textoSec, fontSize = 12.sp)
        Text("Tiempo", color = Paleta.textoSec, fontSize = 13.sp)
        Selector(tiempos.map { "$it min" }, tiempos.indexOf(minutos).coerceAtLeast(0), { minutos = tiempos[it] }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Inflar", { PlacaB.salidaE(0, true, minutos * 60) }, Modifier.weight(1f), tamanoTexto = 13.sp)
            BotonDeportivo("Parar", { PlacaB.salidaE(0, false) }, Modifier.weight(1f), primario = false, color = Paleta.rojo, tamanoTexto = 13.sp)
        }
        Text("Se apaga sola al terminar el tiempo, a los 10 min (y descansa 5), si la moto se mueve o si la batería cae " +
            "de 10.5 V. Con el motor apagado y la batería abajo de 11.8 V no arranca: prende el motor.",
            color = Paleta.textoSec, fontSize = 11.sp)

        EtiquetaPixel("relevadores y salida", Paleta.lima, 7.sp)
        val nombres = listOf("Relevador 2 (E)", "Relevador 3 (E)", "Salida aislada (E)")
        val detalles = listOf("Bornera RELE2 · contacto seco hasta ~3 A", "Bornera RELE3 · contacto seco hasta ~3 A", "Bornera SAL · hasta 50 mA")
        for (i in 1..3) {
            FilaSwitch(nombres[i - 1], detalles[i - 1], e?.salidas?.getOrNull(i) == true) { PlacaB.salidaE(i, it) }
        }
        if ((e?.corto ?: 0) != 0) Text("Una salida se lee al revés: revisa si hay un corto en la placa E.", color = Paleta.rojo, fontSize = 12.sp)

        EtiquetaPixel("entradas", Paleta.lima, 7.sp)
        for (i in 0..3) {
            val v = e?.entradas?.getOrNull(i) == true
            Dato("Entrada ${i + 1}", if (v) "CON VOLTAJE" else "sin voltaje", if (v) Paleta.lima else Paleta.textoSec)
        }
    }
}

// ------------------------------------------------------------
// Guardián de la tarjeta (firmware v1.7+): fallas, reinicios, modo seguro
// ------------------------------------------------------------
@Composable
internal fun SeccionGuardian() {
    val cfg by Moto.config.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val err by PlacaB.errores.collectAsStateWithLifecycle()
    var borrar by remember { mutableStateOf(false) }
    val c = cfg
    if (conexion != Conexion.CONECTADO || c == null || versionFw(c.fw) < 107) return
    LaunchedEffect(Unit) { PlacaB.pedirErrores() }

    Seccion("Guardián de la tarjeta") {
        if (c.modoSeguro || err?.modoSeguro == true) {
            Text("MODO SEGURO: la tarjeta se reinició 3 veces seguidas por una falla. Apagó la IA de vibraciones, los baches y " +
                "los LEDs; lo demás sigue. Revisa la lista y, si hace falta, actualiza el firmware.", color = Paleta.ambar, fontSize = 12.sp)
        }
        err?.let { x ->
            Dato("Chip", "${x.tempChip} °C · ${x.memoriaKb} KB libres", if (x.tempChip > 80) Paleta.ambar else Paleta.texto)
            if (x.lista.isEmpty()) Text("Sin fallas anotadas.", color = Paleta.lima, fontSize = 13.sp)
            val f = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
            for (r in x.lista) {
                // El reloj de la tarjeta guarda la hora local: se muestra tal cual (sin zona)
                val cuando = if (r.epoch > 0) f.format(Date(r.epoch * 1000)) else "--"
                Text("$cuando · ${r.texto}", color = Paleta.texto, fontSize = 12.sp)
            }
        } ?: Text("Pidiendo la bitácora…", color = Paleta.textoSec, fontSize = 12.sp)
        Text("Vigila que el programa no se trabe (se reinicia solo), la temperatura del chip, la memoria, el regulador de la " +
            "moto, los sensores y la placa E. Lo nuevo también llega por Telegram (/errores).", color = Paleta.textoSec, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Actualizar", { PlacaB.pedirErrores() }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("Borrar lista", { borrar = true }, Modifier.weight(1f), primario = false, color = Paleta.rojo, tamanoTexto = 12.sp)
        }
    }
    if (borrar) {
        AlertDialog(
            onDismissRequest = { borrar = false },
            title = { Text("¿Borrar la bitácora?") },
            text = { Text("Solo borra la lista de fallas de la tarjeta; no cambia nada más.") },
            confirmButton = { TextButton(onClick = { borrar = false; PlacaB.borrarErrores() }) { Text("Borrar", color = Paleta.rojo) } },
            dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar") } },
        )
    }
}

/** "v1.7" -> 107, "v1.10" -> 110 (para comparar versiones del firmware). */
internal fun versionFw(fw: String): Int {
    val p = fw.trim().removePrefix("v").split(".")
    return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 100 + (p.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0)
}
