package com.xof.ft150ts.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.AudioSalida
import com.xof.ft150ts.BateriaTel
import com.xof.ft150ts.BleManager
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.Dashcam
import com.xof.ft150ts.EstiloTablero
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.Moto
import com.xof.ft150ts.NfcMoto
import com.xof.ft150ts.NivelCalor
import com.xof.ft150ts.TelefonoMoto
import com.xof.ft150ts.Termico
import com.xof.ft150ts.UbicacionManager

/** Proporción del tablero LCD (ancho / alto), igual que en TableroLcd. */
private const val ASPECTO_LCD = 1.7f

/**
 * Página 1: el tablero. Su estilo (LCD Sport o Clásico) se elige en Ajustes.
 *
 * Optimización: cada pieza lee solo los datos que usa. Así, cuando cambian las
 * RPM (4 veces por segundo) se redibuja la barra y no toda la pantalla.
 */
@Composable
fun PantallaTablero() {
    // Al entrar: barrido a 45° y las RPM suben al tope y regresan (ver Arranque.kt)
    val arranque = rememberArranque()
    CompositionLocalProvider(LocalArranque provides arranque) {
        // Mantener presionado el tablero cambia al siguiente diseño (el tema no cambia); un aviso dice cuál quedó
        var aviso by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(aviso) { if (aviso != null) { delay(1400); aviso = null } }
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                Apariencia.siguienteTablero()
                aviso = NOMBRES_TABLERO[Apariencia.estiloTablero]
            })
        }) {
            AnimatedContent(Apariencia.estiloTablero, label = "tablero",
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }) { estilo ->
                when (estilo) {
                    EstiloTablero.LCD -> TableroEstiloLcd()
                    EstiloTablero.RACE -> TableroEstiloLcd { TableroRaceLcd(it) }
                    EstiloTablero.VFD -> TableroEstiloLcd { TableroVfd(it) }
                    EstiloTablero.ANILLO -> TableroEstiloLcd { TableroAnillo(it) }
                    EstiloTablero.CLASICO -> TableroClasico()
                    EstiloTablero.PISTA -> TableroPista()
                    EstiloTablero.MINIMAL -> TableroMinimal()
                    EstiloTablero.TELEMETRIA -> TableroTelemetria()
                    EstiloTablero.BENTO -> TableroBento()
                    EstiloTablero.SUPERBIKE -> TableroSuperbike()
                    EstiloTablero.F1 -> TableroF1()
                    EstiloTablero.ARRANCON -> TableroArrancon()
                    EstiloTablero.RALLY -> TableroRally()
                    EstiloTablero.ARCADE -> TableroArcade()
                    EstiloTablero.UNIVERSAL -> TableroUniversal()
                    EstiloTablero.MORADO -> TableroMorado()
                    EstiloTablero.TELEMETRIA_MORADA -> TableroTelemetriaMorada()
                    EstiloTablero.DUAL_NEON -> TableroDualNeon()
                }
            }
            AnimatedVisibility(aviso != null, Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
                Text("Tablero · ${aviso ?: ""}", color = Paleta.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.panel(Formas.chica).padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
    }
}

// ------------------------------------------------------------
// Estilo LCD Sport
// ------------------------------------------------------------
@Composable
private fun TableroEstiloLcd(cuadro: @Composable (Modifier) -> Unit = { TableroLcd(it) }) {
    val accesos by Lanzador.mostrarAccesos.collectAsStateWithLifecycle()
    val compacto = Apariencia.datosCompactos
    BoxWithConstraints(Modifier.fillMaxSize().barrido45(LocalArranque.current, Paleta.lima).padding(horizontal = 10.dp, vertical = 8.dp)) {
        val horizontal = maxWidth > maxHeight
        if (horizontal) {
            // El LCD crece hasta llenar el alto; los datos de al lado se quedan con lo que sobra
            val hueco = 10.dp
            val rail = if (accesos) anchoAccesos(maxHeight) + hueco else 0.dp
            val altoShift = 14.dp + 6.dp
            val ladoMin = if (compacto) 200.dp else 260.dp
            val porAlto = (maxHeight - altoShift) * ASPECTO_LCD
            val porAncho = maxWidth - rail - hueco - ladoMin
            val anchoLcd = minOf(porAlto, porAncho).coerceAtLeast(220.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(hueco)) {
                if (accesos) AccesosDirectos(vertical = true, Modifier.fillMaxHeight())
                Column(
                    Modifier.width(anchoLcd).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    ShiftEnVivo(Modifier.fillMaxWidth())
                    cuadro(Modifier.fillMaxWidth())
                }
                Column(
                    // Rendimiento: en caché de GPU; la pantalla no la vuelve a pintar con cada paso de la aguja
                    Modifier.weight(1f).fillMaxHeight().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (compacto) 7.dp else 10.dp),
                ) {
                    AlertaEnVivo()
                    AlertaClimaTablero()
                    TarjetaRutina()
                    Testigos()
                    DatosExtra(compacto)
                    TarjetaClima(compacta = compacto)
                    UltimaNotif()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (compacto) 9.dp else 12.dp),
            ) {
                AlertaEnVivo()
                AlertaClimaTablero()
                TarjetaRutina()
                ShiftEnVivo(Modifier.fillMaxWidth())
                cuadro(Modifier.fillMaxWidth())
                if (accesos) AccesosDirectos(vertical = false)
                Testigos()
                DatosExtra(compacto)
                TarjetaClima(compacta = compacto)
                UltimaNotif()
            }
        }
    }
}

@Composable
private fun ShiftEnVivo(modifier: Modifier) {
    val r by Moto.rapida.enVivo()
    // En la entrada, el shift light se llena con el barrido y destella arriba
    val arranque = LocalArranque.current.value
    BarraShift(if (arranque >= 0f) rpmArranque(arranque, r.rpm.toFloat()).toInt() else r.rpm, r.shift, modifier)
}

/** Alerta de la moto o aviso de velocidad. */
@Composable
private fun AlertaEnVivo() {
    val e by Moto.estado.enVivo()
    val exceso by Moto.excesoVel.enVivo()
    val limite by Moto.limiteActivo.collectAsStateWithLifecycle()
    val cable by com.xof.ft150ts.Energia.cableSuelto.collectAsStateWithLifecycle()
    BannerAlerta(when {
        exceso -> "Vas a más de $limite km/h"
        cable -> "Sin carga: revisa el cable del teléfono"
        else -> e.alerta
    })
}

/** Testigos pixelados: neutral, Bluetooth, GPS, alerta, corte, motor, demo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Testigos() {
    val rS = Moto.rapida.enVivo()
    val neutral by remember { derivedStateOf { rS.value.marcha == 0 } }
    val motor by remember { derivedStateOf { rS.value.motor } }
    val e by Moto.estado.enVivo()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val uS = UbicacionManager.ubicacion.enVivo()
    val gpsTel by remember { derivedStateOf { uS.value != null } }
    val audio by AudioSalida.dispositivos.collectAsStateWithLifecycle()
    val telefono by TelefonoMoto.trabajando.collectAsStateWithLifecycle()
    val nfc by NfcMoto.presente.collectAsStateWithLifecycle()
    val rec by Dashcam.grabando.collectAsStateWithLifecycle()
    val calor by Termico.nivel.collectAsStateWithLifecycle()
    val temp by Termico.tempBateria.collectAsStateWithLifecycle()
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Testigo("N", neutral, Paleta.verde)
        Testigo("BT", conexion == Conexion.CONECTADO, Paleta.rpmAlta)
        Testigo("GPS", e.gpsFix || gpsTel, Paleta.lima)
        Testigo("!", e.alerta.isNotBlank(), Paleta.rojo)
        Testigo("Lock", e.corte, Paleta.rojo)
        Testigo("Motor", motor, Paleta.lima)
        if (conexion == Conexion.DEMO) Testigo("Demo", true, Paleta.ambar)
        if (BleManager.conectado() && e.gpsTelefono) Testigo("GPS cel", true, Paleta.plata)
        if (e.iphone) Testigo("iPhone", true, Paleta.plata)
        if (telefono) Testigo("RPM cel", true, Paleta.plata)
        BateriaCel()
        if (nfc) Testigo("NFC", true, Paleta.plata)
        // Dashcam: toca REC para guardar los últimos 3 minutos
        if (rec) Testigo("REC", parpadeo(true) > 0.5f, Paleta.rojo, Modifier.clickable { Dashcam.guardarClip() })
        // Teléfono caliente
        if (calor >= NivelCalor.TIBIO) {
            Testigo("Cel ${temp.toInt()}°", true, when (calor) {
                NivelCalor.MUY_CALIENTE -> Paleta.rojo
                NivelCalor.CALIENTE -> Paleta.ambar
                else -> Paleta.plata
            })
        }
        // audífonos / casco Bluetooth con su batería
        audio.firstOrNull()?.let { a ->
            val color = when {
                a.bateria < 0 || a.bateria > 50 -> Paleta.lima
                a.bateria > 20 -> Paleta.ambar
                else -> Paleta.rojo
            }
            Testigo(if (a.bateria >= 0) "Aud ${a.bateria}%" else "Aud", true, color)
        }
    }
}

@Composable
private fun DatosExtra(compacto: Boolean) {
    val r by Moto.rapida.enVivo()
    val e by Moto.estado.enVivo()
    val v by Moto.viaje.enVivo()
    val g = if (compacto) 7.dp else 10.dp
    Column(verticalArrangement = Arrangement.spacedBy(g)) {
        Row(horizontalArrangement = Arrangement.spacedBy(g)) {
            Tarjeta(
                "Inclinación", if (r.incl >= 0) "${r.incl.toInt()}" else "--", Modifier.weight(1f),
                unidad = "°", color = if (r.incl > 45) Paleta.ambar else Paleta.texto,
                extra = "Aceleración ${fmt(r.aFwd, 2)} g", compacta = compacto,
            )
            Tarjeta(
                "Perfil", if (e.perfil == 1) "SPORT" else "ECO", Modifier.weight(1f),
                color = if (e.perfil == 1) Paleta.rpmAlta else Paleta.verde,
                extra = "Cambio a ${r.shift} rpm", compacta = compacto,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(g)) {
            Tarjeta(
                "Viaje", fmt(v.km, 1), Modifier.weight(1f), unidad = "km",
                extra = "Máx ${v.velMax.toInt()} km/h · ${fmt(v.litros, 2)} L", compacta = compacto,
            )
            Tarjeta(
                "Batería moto", e.bateria.ifBlank { "--" }, Modifier.weight(1f),
                color = when (e.bateria) {
                    "BUENA", "CARGANDO" -> Paleta.verde
                    "MEDIA" -> Paleta.ambar
                    "", "SIN DATO" -> Paleta.texto
                    else -> Paleta.rojo
                },
                extra = if (r.vMoto >= 0) "${fmt(r.vMoto, 2)} V" else "", compacta = compacto,
            )
        }
    }
}

// ------------------------------------------------------------
// Estilo Clásico (tacómetro circular)
// ------------------------------------------------------------
@Composable
private fun TableroClasico() {
    val accesos by Lanzador.mostrarAccesos.collectAsStateWithLifecycle()
    val compacto = Apariencia.datosCompactos
    BoxWithConstraints(Modifier.fillMaxSize().franjasCarreras().barrido45(LocalArranque.current, Paleta.lima).padding(horizontal = 10.dp, vertical = 8.dp)) {
        val horizontal = maxWidth > maxHeight
        if (horizontal) {
            val hueco = 12.dp
            val rail = if (accesos) anchoAccesos(maxHeight) + hueco else 0.dp
            val altoShift = 14.dp + 8.dp
            val ladoMin = if (compacto) 220.dp else 280.dp
            val lado = minOf(maxHeight - altoShift, maxWidth - rail - hueco - ladoMin).coerceAtLeast(180.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(hueco)) {
                if (accesos) AccesosDirectos(vertical = true, Modifier.fillMaxHeight())
                Column(
                    Modifier.width(lado).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    ShiftEnVivo(Modifier.fillMaxWidth())
                    MedidorEnVivo(Modifier.fillMaxWidth())
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (compacto) 7.dp else 10.dp),
                ) {
                    AlertaEnVivo()
                    AlertaClimaTablero()
                    TarjetaRutina()
                    GrillaDatos(compacto)
                    TarjetaClima(compacta = compacto)
                    UltimaNotif()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compacto) 9.dp else 12.dp),
            ) {
                AlertaEnVivo()
                AlertaClimaTablero()
                TarjetaRutina()
                ShiftEnVivo(Modifier.fillMaxWidth())
                MedidorEnVivo(Modifier.fillMaxWidth().widthIn(max = 420.dp))
                if (accesos) AccesosDirectos(vertical = false)
                GrillaDatos(compacto)
                TarjetaClima(compacta = compacto)
                UltimaNotif()
            }
        }
    }
}

@Composable
private fun MedidorEnVivo(modifier: Modifier) {
    val r by Moto.rapida.enVivo()
    // Si la moto no manda velocidad, usa la del GPS del teléfono
    val velTel by UbicacionManager.velocidad.enVivo()
    val vel = if (r.vel >= 0) r.vel else velTel
    // La aguja del tacómetro se mueve con el PID (60 pasos por segundo para que se vea suave).
    // En la entrada sube al tope y regresa.
    val arranque = LocalArranque.current
    val aguja by pidAnimado({ rpmArranque(arranque.value, kotlin.math.max(0, r.rpm).toFloat()) }, hz = 60)
    MedidorRpm(aguja.toInt(), r.shift, vel, r.marcha, modifier)
}

@Composable
private fun GrillaDatos(compacto: Boolean) {
    val r by Moto.rapida.enVivo()
    val e by Moto.estado.enVivo()
    val v by Moto.viaje.enVivo()
    val colorBat = when {
        r.vMoto < 0 -> Paleta.texto
        r.vMoto < 12.0f -> Paleta.rojo
        r.vMoto > 15.0f -> Paleta.ambar
        else -> Paleta.verde
    }
    val colorGas = when {
        r.gas < 0 -> Paleta.texto
        r.gas <= 10 -> Paleta.rojo
        r.gas <= 25 -> Paleta.ambar
        else -> Paleta.acento
    }
    val colorTemp = when {
        r.temp <= -90 -> Paleta.texto
        r.temp > 110 -> Paleta.rojo
        r.temp > 95 -> Paleta.ambar
        else -> Paleta.texto
    }
    val g = if (compacto) 7.dp else 10.dp
    Column(verticalArrangement = Arrangement.spacedBy(g)) {
        Row(horizontalArrangement = Arrangement.spacedBy(g)) {
            Tarjeta(
                "Batería moto", if (r.vMoto >= 0) fmt(r.vMoto, 1) else "--", Modifier.weight(1f),
                unidad = "V", color = colorBat, extra = e.bateria, compacta = compacto,
            )
            Tarjeta(
                "Gasolina", if (r.gas >= 0) "${r.gas}" else "--", Modifier.weight(1f),
                unidad = "%", color = colorGas,
                extra = if (v.autonomia >= 0) "Autonomía ~${v.autonomia} km" else "",
                progreso = if (r.gas >= 0) r.gas / 100f else null, compacta = compacto,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(g)) {
            Tarjeta(
                "Motor", if (r.temp > -90) "${r.temp.toInt()}" else "--", Modifier.weight(1f),
                unidad = "°C", color = colorTemp, extra = if (r.motor) "Encendido" else "Apagado", compacta = compacto,
            )
            Tarjeta(
                "Batería 18650", if (e.vLipo >= 0) fmt(e.vLipo, 2) else "--", Modifier.weight(1f),
                unidad = "V", extra = if (e.lipoPct >= 0) "${e.lipoPct}%" else "",
                progreso = if (e.lipoPct >= 0) e.lipoPct / 100f else null, compacta = compacto,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(g)) {
            Tarjeta(
                "Viaje", fmt(v.km, 1), Modifier.weight(1f), unidad = "km",
                extra = "Máx ${v.velMax.toInt()} km/h", compacta = compacto,
            )
            Tarjeta(
                "Perfil", if (e.perfil == 1) "SPORT" else "ECO", Modifier.weight(1f),
                color = if (e.perfil == 1) Paleta.rpmAlta else Paleta.verde,
                extra = "Cambio a ${r.shift} rpm", compacta = compacto,
            )
        }
    }
}

@Composable
private fun UltimaNotif() {
    val texto by Moto.ultimaNotif.collectAsStateWithLifecycle()
    if (texto.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        EtiquetaPixel("cel", Paleta.lima, 7.sp)
        Spacer(Modifier.padding(start = 8.dp))
        Text(texto, color = Paleta.textoSec, fontSize = 13.sp, maxLines = 1)
    }
}


/** Batería del teléfono en el tablero: pila pixel, rayito si carga y los watts que entran. */
@Composable
private fun BateriaCel() {
    val b by BateriaTel.estado.enVivo()
    if (b.pct < 0) return
    val forma = Formas.chip
    Row(
        Modifier.clip(forma).border(1.dp, if (b.cargando) Paleta.lima else Paleta.borde, forma).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtiquetaPixel("cel", Paleta.textoSec, 6.sp)
        Spacer(Modifier.width(4.dp))
        BateriaPixel(b.pct)
        if (b.cargando) {
            Spacer(Modifier.width(3.dp))
            IconoPixel(PIXEL_RAYO, Paleta.lima, Modifier.size(width = 8.dp, height = 11.dp))
            if (b.vatios >= 0.5f) {
                Spacer(Modifier.width(2.dp))
                Text("${fmt(b.vatios, 1)}W", color = Paleta.lima, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
