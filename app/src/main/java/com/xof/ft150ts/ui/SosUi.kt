package com.xof.ft150ts.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Emergencia
import com.xof.ft150ts.ReglasSos
import kotlinx.coroutines.delay

/**
 * Cuenta regresiva del SOS: roja, a pantalla completa, con el botón grande "ESTOY BIEN" (casi toda la pantalla,
 * para tocarlo con guantes). Se dibuja solo mientras hay una cuenta.
 */
@Composable
fun CuentaSosPantalla() {
    val cuenta by Emergencia.cuenta.collectAsStateWithLifecycle()
    val c = cuenta ?: return
    var ahora by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(c) { while (true) { ahora = android.os.SystemClock.elapsedRealtime(); delay(250) } }
    val falta = ((c.fin - ahora + 999) / 1000).coerceAtLeast(0)
    Box(
        Modifier.fillMaxSize().background(Color(0xF2140000)).clickable(enabled = false) {}.safeDrawingPadding().padding(18.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EtiquetaPixel(if (c.prueba) "prueba de sos" else "posible caida", Paleta.rojo, 12.sp)
            Text("$falta", color = Paleta.blanco, fontSize = 96.sp, fontFamily = Fuentes.logo)
            Text(if (c.prueba) "Al terminar solo te muestro el mensaje (no se manda)." else "Si no tocas el botón, aviso a tu contacto de emergencia con tu ubicación.",
                color = Paleta.texto, fontSize = 15.sp, textAlign = TextAlign.Center)
            Box(
                Modifier.fillMaxWidth().weight(1f).heightIn(min = 160.dp).background(Paleta.verde)
                    .border(3.dp, Color.Black).clickable { Emergencia.cancelar() },
                contentAlignment = Alignment.Center,
            ) {
                Text("ESTOY BIEN", color = Color.Black, fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = Fuentes.logo)
            }
            BotonDeportivo("Pedir ayuda ya", { Emergencia.mandarYa() }, Modifier.fillMaxWidth(), color = Paleta.rojo, tamanoTexto = 14.sp)
        }
    }
}

/** Ajustes → SOS por caída. */
@Composable
fun SeccionSos() {
    val ctx = LocalContext.current
    val activo by Emergencia.activo.collectAsStateWithLifecycle()
    val numero by Emergencia.numero.collectAsStateWithLifecycle()
    val nombre by Emergencia.nombre.collectAsStateWithLifecycle()
    val segundos by Emergencia.segundos.collectAsStateWithLifecycle()
    val evento by Emergencia.evento.collectAsStateWithLifecycle()
    var smsSolo by remember { mutableStateOf(Emergencia.puedeMandarSolo(ctx)) }
    val pedirSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsSolo = it }

    Seccion("SOS por caída") {
        FilaSwitch("Avisar si me caigo",
            "Si vas rodando, hay un golpe muy fuerte y el teléfono se queda quieto, cuenta $segundos s en voz alta. " +
                "Si no tocas \"ESTOY BIEN\", manda tu ubicación por SMS a tu contacto.",
            activo, Paleta.rojo) { Emergencia.ponerActivo(it) }
        OutlinedTextField(numero, { Emergencia.ponerNumero(it) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Número de emergencia (con lada, p. ej. +52…)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = numero.isNotEmpty() && !ReglasSos.numeroValido(numero))
        OutlinedTextField(nombre, { Emergencia.ponerNombre(it) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Tu nombre (va en el mensaje)") })
        Text("Tiempo para cancelar", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val opc = listOf(20, 30, 60)
        Selector(listOf("20 s", "30 s", "60 s"), opc.indexOf(segundos).coerceAtLeast(1), { Emergencia.ponerSegundos(opc[it]) }, Modifier.fillMaxWidth())
        Dato("Mandar el SMS solo", if (smsSolo) "Sí" else "No (se abre Mensajes y tocas enviar)", if (smsSolo) Paleta.verde else Paleta.ambar)
        if (!smsSolo) {
            BotonDeportivo("Permitir mandar SMS", { pedirSms.launch(Manifest.permission.SEND_SMS) }, Modifier.fillMaxWidth(),
                primario = false, tamanoTexto = 12.sp)
        }
        BotonDeportivo("Probar (no manda nada)", { Emergencia.probar() }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        if (activo && !ReglasSos.numeroValido(numero)) {
            Text("Falta un número válido: sin él no se avisa a nadie.", color = Paleta.ambar, fontSize = 12.sp)
        }
        if (evento.isNotBlank()) Text(evento, color = Paleta.lima, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text("Usa el mismo detector de golpes que la dashcam. Un bache enorme no lo dispara si sigues rodando. " +
            "No sustituye a un servicio de emergencias.", color = Paleta.textoSec, fontSize = 11.sp)
    }
}
