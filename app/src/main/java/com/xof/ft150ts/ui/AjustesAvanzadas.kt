package com.xof.ft150ts.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.Dashcam
import com.xof.ft150ts.FuncionesAvanzadas
import com.xof.ft150ts.Moto
import com.xof.ft150ts.ProtocoloAvanzado
import kotlin.math.roundToInt

/**
 * Ajustes > Funciones avanzadas (v3.10): limitador de RPM, launch control, quickshifter, telemetría en la
 * dashcam y diagnóstico de motor y batería. Todo viene apagado. Lo que corta la chispa vive en la tarjeta
 * (firmware v1.5) y necesita el corte de chispa armado (placa base v2.1: PC817 + IRF840); sin él la tarjeta no deja prenderlo.
 */
@Composable
fun SeccionFuncionesAvanzadas() {
    val est by FuncionesAvanzadas.estado.collectAsStateWithLifecycle()
    val diag by FuncionesAvanzadas.diagnostico.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val respuesta by Moto.ultimaRespuesta.collectAsStateWithLifecycle()
    val telemetria by Dashcam.telemetria.collectAsStateWithLifecycle()
    // Comando que espera la confirmación del diálogo
    var porConfirmar by remember { mutableStateOf<Pair<String, String>?>(null) }
    val conectada = conexion == Conexion.CONECTADO

    Seccion("Funciones avanzadas") {
        Text("Todo viene apagado. El limitador, el launch control y el quickshifter cortan la chispa unos milisegundos: " +
            "necesitan el corte de chispa en el cable de paro del CDI (firmware/HARDWARE-CORTE-CHISPA.md). Solo se prenden con la " +
            "moto parada y el teléfono vinculado; si la tarjeta falla, la moto sigue con chispa normal.",
            color = Paleta.textoSec, fontSize = 11.sp)

        val e = est
        if (!conectada || e == null) {
            Dato("Tarjeta", if (!conectada) "Sin conexión" else "Firmware anterior a v1.5", Paleta.textoSec)
        } else {
            Dato("Corte de chispa", if (e.hwCorte) "Instalado" else "Falta el corte de chispa", if (e.hwCorte) Paleta.lima else Paleta.textoSec)

            // ---- Limitador ----
            FilaSwitch("Limitador de RPM",
                if (e.hwCorte) "Corta la chispa 20 ms cada vez que pasas de ${e.limRpm} rpm" else "Requiere el corte de chispa en la placa",
                e.limitador, Paleta.rojo) { on ->
                val cmd = ProtocoloAvanzado.cmdLimitador(on, e.limRpm)
                if (on) porConfirmar = cmd to "Limitar el motor a ${e.limRpm} rpm." else FuncionesAvanzadas.enviar(cmd)
            }
            DeslizadorRpm("Tope", e.limRpm, ProtocoloAvanzado.LIM_MIN, ProtocoloAvanzado.LIM_MAX, 250) { rpm ->
                if (e.limitador) porConfirmar = ProtocoloAvanzado.cmdLimitador(true, rpm) to "Cambiar el tope a $rpm rpm."
                else FuncionesAvanzadas.estado.value = e.copy(limRpm = rpm)
            }

            // ---- Launch control ----
            FilaSwitch("Launch control",
                if (e.hwCorte) "Parado y en marcha: sostiene ${e.launchRpm} rpm para salir; se suelta al pasar 15 km/h"
                else "Requiere el corte de chispa en la placa",
                e.launch, Paleta.rojo) { on ->
                val cmd = ProtocoloAvanzado.cmdLaunch(on, e.launchRpm)
                if (on) porConfirmar = cmd to "Sostener ${e.launchRpm} rpm al salir desde parado." else FuncionesAvanzadas.enviar(cmd)
            }
            DeslizadorRpm("Salida", e.launchRpm, ProtocoloAvanzado.LC_MIN, ProtocoloAvanzado.LC_MAX, 250) { rpm ->
                if (e.launch) porConfirmar = ProtocoloAvanzado.cmdLaunch(true, rpm) to "Cambiar la salida a $rpm rpm."
                else FuncionesAvanzadas.estado.value = e.copy(launchRpm = rpm)
            }

            // ---- Quickshifter ----
            FilaSwitch("Quickshifter",
                when {
                    !e.hwCorte -> "Requiere el corte de chispa y el sensor de la palanca"
                    !e.hwQs -> "Falta el sensor de la palanca (PIN_QS_SENSOR)"
                    else -> "Subir sin clutch: corta ${e.qsMs} ms al pisar la palanca, arriba de ${e.qsRpm} rpm, de 1a a 4a"
                },
                e.quickshifter, Paleta.rojo) { on ->
                val cmd = ProtocoloAvanzado.cmdQuickshifter(on, e.qsMs, e.qsRpm)
                if (on) porConfirmar = cmd to "Cortar ${e.qsMs} ms al subir cambio (arriba de ${e.qsRpm} rpm)." else FuncionesAvanzadas.enviar(cmd)
            }
            DeslizadorRpm("Corte (ms)", e.qsMs, ProtocoloAvanzado.QS_MS_MIN, ProtocoloAvanzado.QS_MS_MAX, 5, unidad = " ms") { ms ->
                if (e.quickshifter) porConfirmar = ProtocoloAvanzado.cmdQuickshifter(true, ms, e.qsRpm) to "Cambiar el corte a $ms ms."
                else FuncionesAvanzadas.estado.value = e.copy(qsMs = ms)
            }
            DeslizadorRpm("Desde", e.qsRpm, ProtocoloAvanzado.QS_RPM_MIN, ProtocoloAvanzado.QS_RPM_MAX, 250) { rpm ->
                if (e.quickshifter) porConfirmar = ProtocoloAvanzado.cmdQuickshifter(true, e.qsMs, rpm) to "Usarlo arriba de $rpm rpm."
                else FuncionesAvanzadas.estado.value = e.copy(qsRpm = rpm)
            }

            // ---- Diagnóstico ----
            FilaSwitch("Diagnóstico de motor y batería",
                "Solo mide: chispas que faltan, encendido disparejo, el voltaje al dar marcha (últimos 5) y si el alternador carga",
                e.diagnostico) { FuncionesAvanzadas.enviar(ProtocoloAvanzado.cmdDiagnostico(it)) }
            val d = diag
            if (e.diagnostico) {
                if (d == null) Text("Esperando datos de la tarjeta…", color = Paleta.textoSec, fontSize = 12.sp)
                else {
                    Dato("Encendido", ProtocoloAvanzado.veredictoEncendido(d))
                    Dato("Batería", ProtocoloAvanzado.veredictoBateria(d), if (d.arranqueDebil) Paleta.rojo else Paleta.texto)
                    if (d.arranques.isNotEmpty())
                        Dato("Últimos arranques", d.arranques.joinToString("  ") { "%.1f".format(it) } + " V")
                    Dato("Alternador", when (d.carga) { 1 -> "Carga bien"; 2 -> "No carga"; 3 -> "Sobrecarga"; else -> "Sin dato (acelera arriba de 2500)" },
                        if (d.carga >= 2) Paleta.rojo else Paleta.texto)
                    if (d.ralenti > 0) Dato("Ralentí", "${d.ralenti} rpm")
                    if (e.limitador || e.quickshifter) Dato("Cortes", "limitador ${d.cortesLimitador} · quickshifter ${d.cortesQs}")
                    if (e.launch) Dato("Launch", if (d.launchArmado) "ARMADO" else "En espera", if (d.launchArmado) Paleta.rojo else Paleta.texto)
                }
            }
            if (respuesta.isNotBlank()) Text(respuesta, color = Paleta.lima, fontSize = 12.sp)
        }

        // ---- Dashcam (vive en el teléfono) ----
        FilaSwitch("Telemetría en la dashcam",
            "Cada video lleva un subtítulo .srt con velocidad, RPM, marcha, voltaje, inclinación, hora y ubicación. " +
                "VLC lo muestra solo; no calienta más el teléfono",
            telemetria) { Dashcam.ponerTelemetria(it) }
    }

    porConfirmar?.let { (cmd, texto) ->
        AlertDialog(
            onDismissRequest = { porConfirmar = null },
            title = { EtiquetaPixel("corte de chispa", Paleta.rojo, 9.sp) },
            text = {
                Text("$texto\n\nPruébalo primero en un lugar seguro y sin pasajero. Si algo no se siente bien, apágalo aquí " +
                    "o desde la consola con \"${cmd.substringBefore(" on")} off\".", color = Paleta.texto, fontSize = 13.sp)
            },
            confirmButton = { TextButton(onClick = { FuncionesAvanzadas.enviar(cmd); porConfirmar = null }) { Text("Prender") } },
            dismissButton = { TextButton(onClick = { porConfirmar = null }) { Text("Cancelar") } },
        )
    }
}

/** Deslizador con pasos; solo avisa al soltar (para no mandar un comando por cada movimiento). */
@Composable
private fun DeslizadorRpm(nombre: String, valor: Int, min: Int, max: Int, paso: Int, unidad: String = " rpm", onListo: (Int) -> Unit) {
    var v by remember(valor) { mutableFloatStateOf(valor.toFloat()) }
    val redondo = ((v / paso).roundToInt() * paso).coerceIn(min, max)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, color = Paleta.textoSec, fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Slider(v, { v = it }, valueRange = min.toFloat()..max.toFloat(), onValueChangeFinished = { onListo(redondo) },
            colors = SliderDefaults.colors(thumbColor = Paleta.lima, activeTrackColor = Paleta.lima, inactiveTrackColor = Paleta.superficie2),
            modifier = Modifier.weight(1f))
        Text("$redondo$unidad", color = Paleta.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(76.dp))
    }
}
