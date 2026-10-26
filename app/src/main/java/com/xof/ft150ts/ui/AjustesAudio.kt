package com.xof.ft150ts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.AlDetenerse
import com.xof.ft150ts.AudioSalida
import com.xof.ft150ts.BluetoothAdmin
import com.xof.ft150ts.DispositivoBt
import com.xof.ft150ts.VolumenAuto

/**
 * Ajustes > Bluetooth y audio (v3.8): tus dispositivos Bluetooth con su estado real, buscar y emparejar,
 * conectar / desconectar (root), el preferido que se reconecta al abrir el switch y el volumen progresivo.
 */
@Composable
fun SeccionBluetoothAudio() {
    val visible = LocalPaginaVisible.current
    val emparejados by BluetoothAdmin.emparejados.collectAsStateWithLifecycle()
    val encontrados by BluetoothAdmin.encontrados.collectAsStateWithLifecycle()
    val buscando by BluetoothAdmin.buscando.collectAsStateWithLifecycle()
    val evento by BluetoothAdmin.evento.collectAsStateWithLifecycle()
    val preferido by BluetoothAdmin.preferido.collectAsStateWithLifecycle()
    val reconectar by BluetoothAdmin.reconectarAlPrender.collectAsStateWithLifecycle()
    val audio by AudioSalida.dispositivos.collectAsStateWithLifecycle()
    LaunchedEffect(visible, audio) { if (visible) BluetoothAdmin.refrescar() }

    Seccion("Bluetooth y audio") {
        Subtitulo2("Tus dispositivos")
        if (emparejados.isEmpty()) Text("No hay dispositivos emparejados (o falta el permiso de dispositivos cercanos).",
            color = Paleta.textoSec, fontSize = 12.sp)
        emparejados.forEach { d -> FilaDispositivo(d, d.mac == preferido) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo(if (buscando) "Buscando…" else "Buscar nuevos", { BluetoothAdmin.buscar() }, Modifier.weight(1f),
                primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("Ajustes de Bluetooth", { AudioSalida.abrirAjustesBluetooth() }, Modifier.weight(1f),
                primario = false, tamanoTexto = 12.sp)
        }
        encontrados.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.nombre, color = Paleta.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
                BotonDeportivo("Emparejar", { BluetoothAdmin.emparejar(d.mac) }, primario = false, tamanoTexto = 12.sp)
            }
        }
        if (evento.isNotBlank()) Text(evento, color = Paleta.textoSec, fontSize = 12.sp)
        FilaSwitch("Reconectar mi preferido al abrir el switch",
            "Toca la estrella de un dispositivo para elegirlo (casco, audífonos)", reconectar) { BluetoothAdmin.ponerReconectar(it) }

        Subtitulo2("Volumen progresivo")
        val activo by VolumenAuto.activo.collectAsStateWithLifecycle()
        val a by VolumenAuto.ajustes.collectAsStateWithLifecycle()
        val nivel by VolumenAuto.nivel.collectAsStateWithLifecycle()
        FilaSwitch("Subir el volumen con la velocidad",
            "Tapa el viento del casco: sube poco a poco al acelerar y regresa a tu volumen al apagar la función o la moto",
            activo) { VolumenAuto.ponerActivo(it) }
        if (activo) {
            val velIni = listOf(10f, 20f, 30f, 40f); val velMax = listOf(60f, 80f, 100f, 120f)
            val volMin = listOf(30, 45, 60); val volMax = listOf(70, 85, 100)
            Text("Empieza a subir desde", color = Paleta.textoSec, fontSize = 12.sp)
            Selector(velIni.map { "${it.toInt()} km/h" }, velIni.indexOf(a.velIni).coerceAtLeast(0),
                { VolumenAuto.ponerAjustes(a.copy(velIni = velIni[it])) }, Modifier.fillMaxWidth())
            Text("Llega al máximo a", color = Paleta.textoSec, fontSize = 12.sp)
            Selector(velMax.map { "${it.toInt()} km/h" }, velMax.indexOf(a.velMax).coerceAtLeast(0),
                { VolumenAuto.ponerAjustes(a.copy(velMax = velMax[it])) }, Modifier.fillMaxWidth())
            Text("Volumen mínimo y máximo", color = Paleta.textoSec, fontSize = 12.sp)
            Selector(volMin.map { "mín $it %" }, volMin.indexOf(a.volMin).coerceAtLeast(0),
                { VolumenAuto.ponerAjustes(a.copy(volMin = volMin[it])) }, Modifier.fillMaxWidth())
            Selector(volMax.map { "máx $it %" }, volMax.indexOf(a.volMax).coerceAtLeast(0),
                { VolumenAuto.ponerAjustes(a.copy(volMax = volMax[it])) }, Modifier.fillMaxWidth())
            Text("Al detenerte", color = Paleta.textoSec, fontSize = 12.sp)
            Selector(listOf("Mantener", "Bajar al mínimo", "Mi volumen"), a.alDetenerse.ordinal,
                { VolumenAuto.ponerAjustes(a.copy(alDetenerse = AlDetenerse.entries[it])) }, Modifier.fillMaxWidth())
            Text("Sube o baja de un nivel a la vez (cada 1.5 s) y no se mueve con los altibajos del GPS (±4 km/h). " +
                "Si cambias el volumen a mano, se pausa 2 min." + if (nivel >= 0) " Ahora: nivel $nivel." else "",
                color = Paleta.textoSec, fontSize = 12.sp)
        }

        Subtitulo2("Sonoridad")
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val son by com.xof.ft150ts.Sonoridad.activo.collectAsStateWithLifecycle()
        val gan by com.xof.ft150ts.Sonoridad.ganancia.collectAsStateWithLifecycle()
        val ses by com.xof.ft150ts.Sonoridad.sesiones.collectAsStateWithLifecycle()
        val glob by com.xof.ft150ts.Sonoridad.global.collectAsStateWithLifecycle()
        FilaSwitch("Sonoridad +",
            "Más fuerte que el 100 % sin saturar (compresor con limitador de Android). Funciona también en el casco",
            son) { com.xof.ft150ts.Sonoridad.ponerActivo(it) }
        if (son) {
            val opciones = listOf(200, 350, 500, 600)
            Selector(listOf("+2 dB", "+3.5 dB (≈150 %)", "+5 dB", "+6 dB"), opciones.indexOf(gan).coerceAtLeast(1),
                { com.xof.ft150ts.Sonoridad.ponerGanancia(opciones[it]) }, Modifier.fillMaxWidth())
            Text(when {
                glob -> "Activa en todo el sonido del teléfono."
                ses > 0 -> "Activa en $ses reproductor(es)."
                else -> "Se activa cuando tu app de música avisa su sesión (Spotify, YouTube Music…). Pon play."
            }, color = Paleta.textoSec, fontSize = 12.sp)
        }
        BotonDeportivo("Dolby Atmos del sistema", {
            try {
                // "Efectos de sonido" de MiSound (ahí se elige Dolby Atmos); si no existe, su ecualizador Dolby
                try {
                    ctx.startActivity(android.content.Intent().setClassName("com.miui.misound", "com.miui.misound.HeadsetSettingsActivity")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    ctx.startActivity(android.content.Intent().setClassName("com.miui.misound", "com.miui.misound.dolby.DolbyEqActivity")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (_: Exception) {
                try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
            }
        }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        Text("El Dolby lo controla tu teléfono (MiSound): este botón abre Efectos de sonido, donde eliges Dolby Atmos y su ecualizador.",
            color = Paleta.textoSec, fontSize = 12.sp)
    }
}

@Composable
private fun FilaDispositivo(d: DispositivoBt, preferido: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (preferido) "★" else "☆", color = if (preferido) Paleta.ambar else Paleta.textoSec, fontSize = 20.sp,
            modifier = Modifier.clickable { BluetoothAdmin.ponerPreferido(if (preferido) "" else d.mac) }.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(d.nombre, color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(when { d.audio -> "Conectado · audio"; d.conectado -> "Conectado"; else -> "Desconectado" },
                color = if (d.conectado) Paleta.verde else Paleta.textoSec, fontSize = 12.sp)
        }
        BotonDeportivo(if (d.conectado) "Desconectar" else "Conectar", { BluetoothAdmin.conectar(d.mac, !d.conectado) },
            primario = false, tamanoTexto = 12.sp)
    }
}

@Composable
private fun Subtitulo2(texto: String) {
    EtiquetaPixel(texto, Paleta.lima, 9.sp, Modifier.padding(top = 6.dp))
}
