package com.xof.ft150ts.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.CasaTarjeta
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.EstadoVigilancia
import com.xof.ft150ts.LogicaTelegram
import com.xof.ft150ts.Moto
import com.xof.ft150ts.PlacaB
import com.xof.ft150ts.Vigilancia
import com.xof.ft150ts.Voltajes

// ------------------------------------------------------------
// Antirrobo del teléfono (funciona con o sin la tarjeta)
// ------------------------------------------------------------
@Composable
internal fun SeccionAntirrobo() {
    val activa by Vigilancia.activa.collectAsStateWithLifecycle()
    val auto by Vigilancia.autoArmar.collectAsStateWithLifecycle()
    val estado by Vigilancia.estado.collectAsStateWithLifecycle()
    val mensaje by Vigilancia.mensaje.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf(Vigilancia.token.value) }
    var chat by remember { mutableStateOf(Vigilancia.chat.value) }
    var aviso by remember { mutableStateOf("") }
    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        Vigilancia.ponerActiva(true)
        if (r.values.any { !it }) aviso = "Sin permiso de cámara o micrófono solo manda la ubicación y lo que sí se pueda."
    }

    Seccion("Antirrobo del teléfono") {
        Text("Con la moto estacionada, si alguien mueve el teléfono (o la tarjeta siente que movieron la moto) y no eres " +
            "tú en 20 s, te manda por Telegram la ubicación, una foto de cada cámara y ~20 s de audio. Lo repite cada 2 min " +
            "mientras lo sigan moviendo (máximo 3).", color = Paleta.textoSec, fontSize = 12.sp)
        FilaSwitch("Antirrobo", if (activa) "Prendido" else "Apagado", activa) { on ->
            if (on) permisos.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) else Vigilancia.ponerActiva(false)
        }
        FilaSwitch("Armarse solo al apagar la moto", "2 min después de apagar el switch, si nadie mueve el teléfono", auto) {
            Vigilancia.ponerAutoArmar(it)
        }
        Dato("Ahora", when (estado.e) {
            EstadoVigilancia.APAGADA -> "desarmada"
            EstadoVigilancia.POR_ARMAR -> "armándose (2 min sin moverlo)"
            EstadoVigilancia.ARMADA -> "ARMADA: vigilando"
            EstadoVigilancia.SOSPECHA -> "¡Se movió! 20 s para que sea el dueño"
            EstadoVigilancia.ALARMA -> "ALARMA: ${estado.envios} envío(s)"
        }, if (estado.e == EstadoVigilancia.ALARMA || estado.e == EstadoVigilancia.SOSPECHA) Paleta.rojo else Paleta.texto)
        Text("Se desarma solo si giras la llave, si vuelve a cargar en la moto o si desbloqueas el teléfono (ponle PIN o " +
            "huella: así un ladrón no puede desarmarlo).", color = Paleta.textoSec, fontSize = 11.sp)

        EtiquetaPixel("tu bot de telegram", Paleta.lima, 7.sp)
        OutlinedTextField(token, { token = it.trim().take(60) }, singleLine = true, label = { Text("Token del bot (el mismo de la tarjeta)") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(chat, { chat = it.filter { c -> c.isDigit() || c == '-' }.take(16) }, singleLine = true,
                label = { Text("Id de tu chat") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            BotonDeportivo("Buscar", {
                if (!LogicaTelegram.tokenValido(token)) { aviso = "Primero pega el token completo."; return@BotonDeportivo }
                CasaTarjeta.buscarChat(token) { id, error -> if (id != null) { chat = id; aviso = "Encontré tu chat." } else aviso = error ?: "" }
            }, primario = false, tamanoTexto = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Guardar", {
                if (!LogicaTelegram.tokenValido(token) || chat.isBlank()) aviso = "Falta el token o el chat."
                else { Vigilancia.ponerTelegram(token, chat); aviso = "Guardado en el teléfono." }
            }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("Armar ahora", { Vigilancia.armar() }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("Probar", { Vigilancia.enviar(true) }, Modifier.weight(1f), tamanoTexto = 12.sp)
        }
        Text("El POCO necesita internet: sin SIM, solo funciona con WiFi.", color = Paleta.textoSec, fontSize = 11.sp)
        if (aviso.isNotBlank()) Text(aviso, color = Paleta.ambar, fontSize = 12.sp)
        if (mensaje.isNotBlank()) Text(mensaje, color = Paleta.lima, fontSize = 12.sp)
    }
}

// ------------------------------------------------------------
// Estado de la moto (IA de la tarjeta): batería, cadena, encendido, frenos y motor
// ------------------------------------------------------------
@Composable
internal fun SeccionEstadoMoto() {
    val cfg by Moto.config.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val salud by PlacaB.salud.collectAsStateWithLifecycle()
    if (conexion != Conexion.CONECTADO || cfg?.placaB != true) return
    LaunchedEffect(Unit) { PlacaB.pedirSalud() }
    Seccion("Estado de la moto") {
        if (salud.isEmpty()) Text("Preguntándole a la tarjeta…", color = Paleta.textoSec, fontSize = 12.sp)
        for (c in salud) {
            val color = when { c.puntos >= 80 -> Paleta.lima; c.puntos >= 50 -> Paleta.ambar; else -> Paleta.rojo }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.nombre, color = Paleta.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${c.puntos}/100", color = color, fontSize = 14.sp)
            }
            BarraPixel(c.puntos / 100f, color, Modifier.fillMaxWidth(), bloques = 20)
            Text(c.consejo, color = Paleta.textoSec, fontSize = 12.sp)
        }
        Text("Sale de lo que la tarjeta mide: voltajes al arrancar y en reposo, chispas que faltan, vibración de la rueda " +
            "(con la IA de vibraciones prendida) y los km desde cada servicio. También por Telegram con /salud.",
            color = Paleta.textoSec, fontSize = 11.sp)
        BotonDeportivo("Actualizar", { PlacaB.pedirSalud() }, primario = false, tamanoTexto = 12.sp)
    }
}

// ------------------------------------------------------------
// Baterías: gráficas de las últimas 24 h
// ------------------------------------------------------------
/** Junta [l] en [n] columnas (promedio de lo que sí tiene dato; 0 = sin dato). */
private fun columnasVolt(l: List<Float>, n: Int, base: Float): List<Float> {
    if (l.isEmpty()) return emptyList()
    val tam = maxOf(1, (l.size + n - 1) / n)
    return l.chunked(tam).map { g -> g.filter { it > 0f }.let { v -> if (v.isEmpty()) 0f else (v.average().toFloat() - base).coerceAtLeast(0.01f) } }
}

@Composable
internal fun SeccionBaterias() {
    val moto by Voltajes.moto.collectAsStateWithLifecycle()
    val celda by Voltajes.celda.collectAsStateWithLifecycle()
    if (moto.none { it > 0f } && celda.none { it > 0f }) return
    Seccion("Baterías · últimas 24 h") {
        Voltajes.resumen(moto)?.let { (mn, mx, pr) ->
            Dato("Moto", "mín ${fmt(mn, 2)} · máx ${fmt(mx, 2)} · prom ${fmt(pr, 2)} V")
            GraficaPixel(columnasVolt(moto, 72, 10f), 5f, Paleta.lima, Modifier.fillMaxWidth().height(70.dp)) { v ->
                val volt = v + 10f
                when { volt < 12.0f -> Paleta.rojo; volt > 15.0f -> Paleta.ambar; else -> Paleta.lima }
            }
            Text("De 10 a 15 V. Arriba de 13.2 V = el alternador está cargando; en reposo, abajo de 12.2 V = débil.",
                color = Paleta.textoSec, fontSize = 11.sp)
        }
        Voltajes.resumen(celda)?.let { (mn, mx, pr) ->
            Dato("Celda de respaldo", "mín ${fmt(mn, 2)} · máx ${fmt(mx, 2)} · prom ${fmt(pr, 2)} V")
            GraficaPixel(columnasVolt(celda, 72, 3f), 1.3f, Color(0xFF59B7FF), Modifier.fillMaxWidth().height(50.dp))
            Text("De 3.0 a 4.3 V (llena = 4.2 V).", color = Paleta.textoSec, fontSize = 11.sp)
        }
        BotonDeportivo("Borrar historial", { Voltajes.borrar() }, primario = false, tamanoTexto = 12.sp)
    }
}
