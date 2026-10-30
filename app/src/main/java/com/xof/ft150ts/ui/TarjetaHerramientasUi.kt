package com.xof.ft150ts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.CasaTarjeta
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.LogicaTelegram
import com.xof.ft150ts.Moto
import com.xof.ft150ts.OtaTarjeta
import com.xof.ft150ts.RegistrosTarjeta

// ------------------------------------------------------------
// Herramientas de la tarjeta (firmware v1.6+): actualizar su firmware y bajar sus registros
// ------------------------------------------------------------
@Composable
internal fun SeccionHerramientasTarjeta() {
    val ctx = LocalContext.current
    val cfg by Moto.config.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val otaActiva by OtaTarjeta.activa.collectAsStateWithLifecycle()
    val otaProgreso by OtaTarjeta.progreso.collectAsStateWithLifecycle()
    val otaMensaje by OtaTarjeta.mensaje.collectAsStateWithLifecycle()
    val regTrabajando by RegistrosTarjeta.trabajando.collectAsStateWithLifecycle()
    val regMensaje by RegistrosTarjeta.mensaje.collectAsStateWithLifecycle()
    var confirmar by remember { mutableStateOf<android.net.Uri?>(null) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) confirmar = uri }
    if (conexion != Conexion.CONECTADO || cfg == null) return

    Seccion("Herramientas de la tarjeta") {
        EtiquetaPixel("firmware", Paleta.lima, 7.sp)
        if (cfg?.ota == true) {
            Text("Actualiza la tarjeta sin desarmar nada: elige el .bin (en Arduino IDE: Sketch > Exportar binario compilado). " +
                "Con la moto parada y el motor apagado; tarda unos 2 minutos.", color = Paleta.textoSec, fontSize = 12.sp)
            BotonDeportivo(if (otaActiva) "Actualizando…" else "Elegir firmware (.bin)",
                { if (!otaActiva) elegir.launch(arrayOf("application/octet-stream", "*/*")) },
                Modifier.fillMaxWidth(), primario = !otaActiva, tamanoTexto = 12.sp)
            if (otaActiva) BarraPixel(otaProgreso, Paleta.lima, Modifier.fillMaxWidth(), bloques = 20)
            if (otaMensaje.isNotBlank()) Text(otaMensaje, color = if (otaActiva) Paleta.texto else Paleta.lima, fontSize = 12.sp)
        } else {
            Text("Para actualizarla desde aquí, la tarjeta necesita el firmware v1.6 grabado una vez por USB con el esquema " +
                "\"16M Flash (3MB APP/9.9MB FATFS)\".", color = Paleta.textoSec, fontSize = 12.sp)
        }

        EtiquetaPixel("registros", Paleta.lima, 7.sp)
        Text("Baja los viajes, eventos, batería y arrancones de la SD de la tarjeta a Descargas/FT150TS (para Excel).",
            color = Paleta.textoSec, fontSize = 12.sp)
        BotonDeportivo(if (regTrabajando) "Bajando…" else "Bajar registros", { if (!regTrabajando) RegistrosTarjeta.bajar(ctx) },
            Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        if (regMensaje.isNotBlank()) Text(regMensaje, color = Paleta.lima, fontSize = 12.sp)
    }

    confirmar?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text("¿Actualizar la tarjeta?") },
            text = { Text("No apagues la moto ni alejes el teléfono mientras se envía. Si algo falla, la tarjeta sigue con su " +
                "firmware de antes.") },
            confirmButton = { TextButton(onClick = { confirmar = null; OtaTarjeta.empezar(ctx, uri) }) { Text("Actualizar") } },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } },
        )
    }
}

// ------------------------------------------------------------
// Casa y Telegram: la moto te avisa y te obedece desde tu cuarto (firmware v1.6+)
// ------------------------------------------------------------
@Composable
internal fun SeccionCasa() {
    val cfg by Moto.config.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val estado by CasaTarjeta.estado.collectAsStateWithLifecycle()
    val mensaje by CasaTarjeta.mensaje.collectAsStateWithLifecycle()
    if (conexion != Conexion.CONECTADO || cfg?.casa != true) return
    LaunchedEffect(Unit) { CasaTarjeta.pedirEstado() }
    var ssid by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf("") }
    var buscando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf("") }
    var borrar by remember { mutableStateOf(false) }

    Seccion("Casa y Telegram") {
        Text("Con la moto estacionada en tu casa, la tarjeta se conecta a tu WiFi cada rato y te escribe por Telegram " +
            "(batería baja, si la mueven) y obedece /estado, /bloquear, /desbloquear y /ubicacion. Solo te hace caso a ti.",
            color = Paleta.textoSec, fontSize = 12.sp)
        estado?.let { e ->
            Dato("WiFi de casa", if (e.ssid.isBlank()) "sin poner" else e.ssid + if (e.clave) " · con clave" else " · sin clave")
            Dato("Telegram", if (e.telegram && e.chat.isNotBlank()) "listo · chat ${e.chat}" else "sin poner")
            Dato("Revisa cada", "${e.cadaMin} min (dormida)")
        }

        EtiquetaPixel("1 · wifi de tu casa", Paleta.lima, 7.sp)
        OutlinedTextField(ssid, { ssid = it.take(32) }, singleLine = true, label = { Text("Nombre de la red") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(clave, { clave = it.take(64) }, singleLine = true, label = { Text("Clave") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        BotonDeportivo("Guardar WiFi en la tarjeta", { CasaTarjeta.guardarWifi(ssid, clave); clave = "" },
            Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)

        EtiquetaPixel("2 · tu bot de telegram", Paleta.lima, 7.sp)
        Text("En Telegram abre @BotFather, escribe /newbot, ponle nombre y copia el token que te da. Luego ábrele chat a tu " +
            "bot, mándale /start y toca Buscar.", color = Paleta.textoSec, fontSize = 12.sp)
        OutlinedTextField(token, { token = it.trim().take(60) }, singleLine = true, label = { Text("Token del bot") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(chat, { chat = it.filter { c -> c.isDigit() || c == '-' }.take(16) }, singleLine = true,
                label = { Text("Id de tu chat") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            BotonDeportivo(if (buscando) "…" else "Buscar", {
                if (!LogicaTelegram.tokenValido(token)) { aviso = "Primero pega el token completo."; return@BotonDeportivo }
                buscando = true
                CasaTarjeta.buscarChat(token) { id, error ->
                    buscando = false
                    if (id != null) { chat = id; aviso = "Encontré tu chat." } else aviso = error ?: ""
                }
            }, primario = false, tamanoTexto = 12.sp)
        }
        BotonDeportivo("Guardar Telegram en la tarjeta", {
            when {
                !LogicaTelegram.tokenValido(token) -> aviso = "Ese token no tiene la forma correcta."
                chat.isBlank() -> aviso = "Falta el id de tu chat (toca Buscar)."
                else -> { CasaTarjeta.guardarTelegram(token, chat); token = ""; aviso = "" }
            }
        }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        if (aviso.isNotBlank()) Text(aviso, color = Paleta.ambar, fontSize = 12.sp)

        EtiquetaPixel("3 · cada cuánto revisa dormida", Paleta.lima, 7.sp)
        val opciones = listOf(10, 15, 30, 60)
        Selector(opciones.map { "$it min" }, opciones.indexOf(estado?.cadaMin ?: 15).coerceAtLeast(0),
            { CasaTarjeta.ponerCada(opciones[it]) }, Modifier.fillMaxWidth())
        Text("Más seguido = te contesta más rápido, gasta un poquito más de la batería de la moto.",
            color = Paleta.textoSec, fontSize = 11.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Mandar prueba", { CasaTarjeta.probar() }, Modifier.weight(1f), tamanoTexto = 12.sp)
            BotonDeportivo("Borrar", { borrar = true }, Modifier.weight(1f), primario = false, color = Paleta.rojo, tamanoTexto = 12.sp)
        }
        if (mensaje.isNotBlank()) Text("Moto: $mensaje", color = Paleta.lima, fontSize = 12.sp)
    }

    if (borrar) {
        AlertDialog(
            onDismissRequest = { borrar = false },
            title = { Text("¿Borrar WiFi y Telegram de la tarjeta?") },
            text = { Text("La tarjeta deja de conectarse a tu casa y de escribirte.") },
            confirmButton = { TextButton(onClick = { borrar = false; CasaTarjeta.borrar() }) { Text("Borrar", color = Paleta.rojo) } },
            dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar") } },
        )
    }
}
