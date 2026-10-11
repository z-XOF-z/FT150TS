package com.xof.ft150ts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.BANDAS_RPM
import com.xof.ft150ts.Brujula
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.Estadisticas
import com.xof.ft150ts.MantItem
import com.xof.ft150ts.Moto
import com.xof.ft150ts.TelefonoMoto
import com.xof.ft150ts.UbicacionManager
import kotlin.math.roundToInt

/** Pantallas del LCD de la moto (mismo orden que en el firmware). */
private val VISTAS_LCD = listOf("Principal", "iPhone", "Viaje", "Comparar", "Mantenim.", "Baterías", "Diagnóstico", "Pista", "Ajustes")

/**
 * Página 3: todo lo de la moto. En horizontal se acomoda en dos columnas.
 * Viaje, sensores en vivo, estadísticas de manejo, gráficas, mantenimiento,
 * control y calibración de la tarjeta, información de la tarjeta y consola.
 */
@Composable
fun PantallaMoto() {
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val visible = LocalPaginaVisible.current
    LaunchedEffect(conexion, visible) {
        if (visible && (conexion == Conexion.CONECTADO || conexion == Conexion.DEMO)) {
            Moto.pedirMantenimiento()
            Moto.pedirConfig()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().franjasCarreras()) {
        val dosColumnas = maxWidth > 720.dp
        if (dosColumnas) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Columna(Modifier.weight(1f)) {
                    SeccionViaje(); SeccionSensores(); SeccionManejo(); SeccionAltitud(); SeccionRalenti(); Graficas()
                    SeccionBateriaTel()
                }
                Columna(Modifier.weight(1f)) {
                    SeccionGasolina(); Mantenimientos(); SeccionControl(); SeccionTarjeta(); Consola()
                }
            }
        } else {
            Columna(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                SeccionViaje(); SeccionSensores(); SeccionManejo(); SeccionAltitud(); SeccionRalenti(); Graficas()
                SeccionBateriaTel()
                SeccionGasolina(); Mantenimientos(); SeccionControl(); SeccionTarjeta(); Consola()
            }
        }
    }
}

/** Con la tarjeta: su mantenimiento. Sin ella: el que lleva el teléfono. */
@Composable
private fun Mantenimientos() {
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    if (conectada(conexion)) SeccionMantenimiento() else SeccionMantTel()
}

@Composable
private fun Columna(modifier: Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        contenido()
        Spacer(Modifier.heightIn(min = 24.dp))
    }
}

private fun conectada(c: Conexion) = c == Conexion.CONECTADO || c == Conexion.DEMO

private fun tiempo(ms: Long): String {
    val min = ms / 60_000
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

// ------------------------------------------------------------
// Viaje (lo cuenta la tarjeta)
// ------------------------------------------------------------
@Composable
private fun SeccionViaje() {
    val v by Moto.viaje.enVivo()
    Seccion("Viaje") {
        Dato("Odómetro", "${fmt(v.odometro, 1)} km")
        Dato("Viaje actual", if (v.activo) "${fmt(v.km, 2)} km · máx ${v.velMax.toInt()} km/h" else "Sin viaje")
        Dato("Gasolina del viaje", "${fmt(v.litros, 2)} L")
        Dato("Hoy / Ayer", "${fmt(v.hoyKm, 1)} km / ${fmt(v.ayerKm, 1)} km")
        Dato("Rendimiento promedio", "${fmt(v.kmPorLitro, 1)} km/L")
        Dato("Autonomía", if (v.autonomia >= 0) "~${v.autonomia} km" else "--")
        Dato("Arranques del motor", "${v.arranques}")
        Dato("Ralentí", if (v.ralenti > 0) "${v.ralenti} rpm" else "midiendo…")
        Dato("Mejor 0-60 km/h (tarjeta)", if (v.pistaMejor > 0) "${fmt(v.pistaMejor, 2)} s" else "--")
    }
}

// ------------------------------------------------------------
// Sensores en vivo: todo lo que manda la tarjeta, de un vistazo
// ------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionSensores() {
    val r by Moto.rapida.enVivo()
    val e by Moto.estado.enVivo()
    val ubic by UbicacionManager.ubicacion.enVivo()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val telefono by TelefonoMoto.trabajando.collectAsStateWithLifecycle()
    val gps by UbicacionManager.estado.enVivo()
    val brujula by Brujula.rumbo.enVivo()
    // La brújula solo se prende con esta sección en pantalla
    val visible = LocalPaginaVisible.current
    DisposableEffect(visible) {
        if (visible) Brujula.arrancar()
        onDispose { if (visible) Brujula.detener() }
    }
    Seccion("Sensores en vivo") {
        Text(
            when {
                conectada(conexion) -> "Datos de la tarjeta de la moto."
                telefono -> "Sin la tarjeta: RPM por vibración, velocidad por GPS e inclinación con el giroscopio del teléfono (aproximado)."
                else -> "Sin datos: conecta la tarjeta o prende \"RPM por vibración\" en Ajustes."
            },
            color = if (telefono && !conectada(conexion)) Paleta.ambar else Paleta.textoSec, fontSize = 12.sp,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniDato("velocidad", if (r.vel >= 0) "${r.vel.toInt()} km/h" else "--")
            MiniDato("rpm", if (r.rpm >= 0) "${r.rpm}" else "--", if (r.rpm >= r.shift && r.rpm > 0) Paleta.rpmAlta else Paleta.texto)
            MiniDato("marcha", when { r.marcha < 0 -> "--"; r.marcha == 0 -> "N"; else -> "${r.marcha}" },
                if (r.marcha == 0) Paleta.verde else Paleta.texto)
            MiniDato("bateria moto", if (r.vMoto >= 0) "${fmt(r.vMoto, 2)} V" else "--", when {
                r.vMoto < 0 -> Paleta.texto; r.vMoto < 12f -> Paleta.rojo; r.vMoto > 15f -> Paleta.ambar; else -> Paleta.verde
            })
            MiniDato("gasolina", if (r.gas >= 0) "${r.gas}%" else "--", if (r.gas in 0..10) Paleta.rojo else Paleta.texto)
            MiniDato("motor", if (r.temp > -90) "${fmt(r.temp, 1)}°C" else "--", if (r.temp > 110) Paleta.rojo else Paleta.texto)
            MiniDato("inclinacion", if (r.incl >= 0) "${r.incl.toInt()}°" else "--")
            MiniDato("aceleracion", "${fmt(r.aFwd, 2)} g", if (r.aFwd < -0.5f) Paleta.ambar else Paleta.texto)
            MiniDato("cambio a", "${r.shift} rpm")
            MiniDato("ignicion", if (r.ignicion) "SÍ" else "NO", if (r.ignicion) Paleta.verde else Paleta.textoSec)
            MiniDato("motor", if (r.motor) "PRENDIDO" else "APAGADO", if (r.motor) Paleta.verde else Paleta.textoSec)
            MiniDato("li-ion", if (e.vLipo >= 0) "${fmt(e.vLipo, 2)} V" + (if (e.lipoPct >= 0) " ${e.lipoPct}%" else "") else "--")
            MiniDato("gps moto", if (e.gpsFix) "${e.sats} sat" else "sin señal", if (e.gpsFix) Paleta.verde else Paleta.textoSec)
            MiniDato("gps cel", when {
                gps.conSenal -> "${gps.usados} sat ±${gps.precision.roundToInt()}m"
                gps.buscando -> "buscando (${gps.usados} sat)"
                ubic != null -> "sin señal"
                else -> "--"
            }, if (gps.conSenal) Paleta.verde else Paleta.textoSec)
            MiniDato("brujula", if (brujula >= 0f) "${brujula.roundToInt()}°" else "--")
            MiniDato("estado bat", e.bateria.ifBlank { "--" })
        }
    }
}

@Composable
private fun MiniDato(etiqueta: String, valor: String, color: Color = Paleta.texto) {
    Column(
        Modifier.width(104.dp).clip(Formas.chica).background(Paleta.superficie2).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        EtiquetaPixel(etiqueta, Paleta.textoSec, 6.sp)
        Spacer(Modifier.height(3.dp))
        Text(valor, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ------------------------------------------------------------
// Estadísticas de manejo (las calcula el teléfono) y récords
// ------------------------------------------------------------
@Composable
private fun SeccionManejo() {
    val m by Estadisticas.manejo.enVivo()
    val rec by Estadisticas.records.collectAsStateWithLifecycle()
    var confirmarRecords by remember { mutableStateOf(false) }
    fun seg(s: Float) = if (s > 0f) "${fmt(s, 2)} s" else "--"
    Seccion("Estadísticas de manejo") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Rodando", tiempo(m.msMov), Modifier.weight(1f), extra = "Parado ${tiempo(m.msParado)}", compacta = true)
            Tarjeta("Distancia", fmt(m.km.toFloat(), 1), Modifier.weight(1f), unidad = "km",
                extra = "Prom ${m.velProm.toInt()} km/h", compacta = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Vel máx", "${m.velMax.toInt()}", Modifier.weight(1f), unidad = "km/h", compacta = true)
            Tarjeta("RPM máx", "${m.rpmMax}", Modifier.weight(1f), extra = "Prom ${m.rpmProm}", compacta = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Inclinación", "${m.inclMax.toInt()}", Modifier.weight(1f), unidad = "°", compacta = true)
            Tarjeta("Acel / Frenada", "${fmt(m.acelMax, 2)}", Modifier.weight(1f), unidad = "g",
                extra = "Frenada ${fmt(m.frenadaMax, 2)} g", color = if (m.frenadaMax > 0.6f) Paleta.ambar else Paleta.texto,
                compacta = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Temp máx", if (m.tempMax > -90) "${m.tempMax.toInt()}" else "--", Modifier.weight(1f), unidad = "°C",
                color = if (m.tempMax > 110) Paleta.rojo else Paleta.texto, compacta = true)
            Tarjeta("Volt mín", if (m.voltMin < 90) fmt(m.voltMin, 1) else "--", Modifier.weight(1f), unidad = "V",
                extra = "Batería con motor", color = if (m.voltMin < 12f) Paleta.ambar else Paleta.texto, compacta = true)
        }

        Subtitulo("Arranques (desde parado)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("0-40", seg(m.mejor0a40), Modifier.weight(1f), extra = "Récord ${seg(rec.mejor0a40)}", compacta = true)
            Tarjeta("0-60", seg(m.mejor0a60), Modifier.weight(1f), extra = "Récord ${seg(rec.mejor0a60)}", compacta = true)
            Tarjeta("0-80", seg(m.mejor0a80), Modifier.weight(1f), extra = "Récord ${seg(rec.mejor0a80)}", compacta = true)
        }
        if (m.ultimoArranque.isNotBlank()) Text("Último: ${m.ultimoArranque}", color = Paleta.lima, fontSize = 12.sp)

        Subtitulo("Tiempo en cada marcha · ${m.cambios} cambios")
        val totalMarcha = m.msMarcha.sum().coerceAtLeast(1L)
        m.msMarcha.forEachIndexed { i, t ->
            BarraReparto(if (i == 0) "N" else "$i", t.toFloat() / totalMarcha, if (i == 0) Paleta.verde else Paleta.lima)
        }
        Subtitulo("En qué RPM manejas")
        val totalRpm = m.msRpm.sum().coerceAtLeast(1L)
        val nombres = listOf("< ${BANDAS_RPM[0] / 1000}k") +
            (0 until BANDAS_RPM.size - 1).map { "${BANDAS_RPM[it] / 1000}-${BANDAS_RPM[it + 1] / 1000}k" } +
            listOf("> ${BANDAS_RPM.last() / 1000}k")
        val colores = listOf(Paleta.rpmBaja, Paleta.rpmBaja, Paleta.rpmMedia, Paleta.rpmAlta)
        m.msRpm.forEachIndexed { i, t -> BarraReparto(nombres[i], t.toFloat() / totalRpm, colores.getOrElse(i) { Paleta.lima }) }

        Subtitulo("Récords de siempre")
        Dato("Velocidad máxima", "${rec.velMax.toInt()} km/h")
        Dato("RPM máximas", "${rec.rpmMax}")
        Dato("Inclinación máxima", "${rec.inclMax.toInt()}°")
        Dato("Aceleración / frenada", "${fmt(rec.acelMax, 2)} g / ${fmt(rec.frenadaMax, 2)} g")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Viaje nuevo", { Estadisticas.nuevo() }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("Borrar récords", { confirmarRecords = true }, Modifier.weight(1f), primario = false,
                color = Paleta.rojo, tamanoTexto = 12.sp)
        }
        Text("Se reinicia solo si pasan más de 2 horas sin datos de la moto. Los arranques se miden desde parado " +
            "hasta 40, 60 y 80 km/h con la velocidad de la moto (o del GPS sin la tarjeta).", color = Paleta.textoSec, fontSize = 11.sp)
    }
    if (confirmarRecords) {
        AlertDialog(
            onDismissRequest = { confirmarRecords = false },
            title = { Text("¿Borrar récords?") },
            text = { Text("Se borran tus mejores marcas (velocidad, arranques, inclinación…).") },
            confirmButton = { TextButton(onClick = { confirmarRecords = false; Estadisticas.borrarRecords() }) { Text("Borrar", color = Paleta.rojo) } },
            dismissButton = { TextButton(onClick = { confirmarRecords = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Subtitulo(texto: String) {
    EtiquetaPixel(texto, Paleta.textoSec, 7.sp, Modifier.padding(top = 4.dp))
}

/** Fila con nombre, barra pixel y porcentaje. */
@Composable
private fun BarraReparto(nombre: String, fraccion: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, color = Paleta.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp))
        BarraPixel(fraccion.coerceIn(0f, 1f), color, Modifier.weight(1f), bloques = 20)
        Text("${(fraccion * 100).toInt()}%", color = Paleta.textoSec, fontSize = 11.sp, modifier = Modifier.width(40.dp).padding(start = 6.dp))
    }
}

// ------------------------------------------------------------
// Gráficas de velocidad y RPM (2, 10 o 30 minutos)
// ------------------------------------------------------------
@Composable
private fun Graficas() {
    val vel by Moto.historialVel.enVivo()
    val rpm by Moto.historialRpm.enVivo()
    val rS = Moto.rapida.enVivo()
    val shift by remember { derivedStateOf { rS.value.shift } }
    var ventana by remember { mutableIntStateOf(0) }
    val segundos = listOf(120, 600, 1800)[ventana]
    // Se queda con el máximo de cada grupo para que entren en 60 columnas
    val colVel = remember(vel, segundos) { columnas(vel.takeLast(segundos)) }
    val colRpm = remember(rpm, segundos) { columnas(rpm.takeLast(segundos)) }
    val enMovimiento = remember(vel, segundos) { vel.takeLast(segundos).filter { it > 2f } }

    Seccion("Gráficas") {
        Selector(listOf("2 min", "10 min", "30 min"), ventana, { ventana = it }, Modifier.fillMaxWidth())
        if (vel.isEmpty()) {
            Text("Se llena sola mientras manejas (o con el modo demo).", color = Paleta.textoSec, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtiquetaPixel("velocidad", Paleta.textoSec, 7.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "máx ${(colVel.maxOrNull() ?: 0f).toInt()} · prom ${if (enMovimiento.isEmpty()) 0 else enMovimiento.average().toInt()} km/h",
                color = Paleta.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        GraficaPixel(colVel, maxOf(60f, colVel.maxOrNull() ?: 0f), Paleta.lima)
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtiquetaPixel("rpm", Paleta.textoSec, 7.sp)
            Spacer(Modifier.weight(1f))
            Text("máx ${(colRpm.maxOrNull() ?: 0f).toInt()} rpm", color = Paleta.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        GraficaPixel(colRpm, RPM_MAX.toFloat(), Paleta.rpmBaja, colorPorValor = { colorRpm(it.toInt(), shift) })
        BotonDeportivo("Borrar gráficas", { Moto.borrarHistorial() }, primario = false, tamanoTexto = 12.sp)
    }
}

private fun columnas(l: List<Float>): List<Float> {
    if (l.size <= 60) return l
    val grupo = (l.size + 59) / 60
    return l.chunked(grupo) { it.max() }
}

// ------------------------------------------------------------
// Mantenimiento (toca un renglón para cambiar cada cuánto)
// ------------------------------------------------------------
@Composable
private fun SeccionMantenimiento() {
    val mant by Moto.mant.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    var confirmarServicio by remember { mutableStateOf<Int?>(null) }
    var editar by remember { mutableStateOf<Int?>(null) }
    Seccion("Mantenimiento") {
        if (mant.isEmpty()) {
            Text(if (conectada(conexion)) "Cargando…" else "Conecta la moto para ver los contadores.",
                color = Paleta.textoSec, fontSize = 13.sp)
        }
        mant.forEachIndexed { i, m -> FilaMant(m, onEditar = { editar = i }) { confirmarServicio = i } }
        if (conectada(conexion)) {
            BotonDeportivo("Actualizar", { Moto.pedirMantenimiento() }, primario = false)
            Text("Toca un renglón para cambiar cada cuántos km o días toca.", color = Paleta.textoSec, fontSize = 11.sp)
        }
    }
    confirmarServicio?.let { i ->
        val nombre = mant.getOrNull(i)?.nombre ?: ""
        AlertDialog(
            onDismissRequest = { confirmarServicio = null },
            title = { Text("¿Registrar servicio?") },
            text = { Text("Se reinicia el contador de \"$nombre\" a partir de hoy y del kilometraje actual.") },
            confirmButton = { TextButton(onClick = { confirmarServicio = null; Moto.registrarServicio(i) }) { Text("Registrar") } },
            dismissButton = { TextButton(onClick = { confirmarServicio = null }) { Text("Cancelar") } },
        )
    }
    editar?.let { i ->
        val m = mant.getOrNull(i)
        if (m == null) editar = null
        else DialogoIntervalo(i, m) { editar = null }
    }
}

@Composable
private fun DialogoIntervalo(i: Int, m: MantItem, onCerrar: () -> Unit) {
    var km by remember { mutableStateOf("${m.intKm}") }
    var dias by remember { mutableStateOf("${m.intDias}") }
    val kmN = km.toIntOrNull()
    val diasN = dias.toIntOrNull()
    val ok = kmN != null && kmN in 50..60000 && diasN != null && diasN in 1..3650
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Intervalo: ${m.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(km, { km = it.filter(Char::isDigit).take(5) }, singleLine = true, label = { Text("Cada cuántos km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(dias, { dias = it.filter(Char::isDigit).take(4) }, singleLine = true, label = { Text("Cada cuántos días") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { Moto.ajusteMoto("mant set $i $kmN $diasN"); Moto.pedirMantenimiento(); onCerrar() }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun FilaMant(m: MantItem, onEditar: () -> Unit, onHecho: () -> Unit) {
    val avance = if (m.intKm > 0) m.km.toFloat() / m.intKm else 0f
    val avanceDias = if (m.dias >= 0 && m.intDias > 0) m.dias.toFloat() / m.intDias else 0f
    val p = maxOf(avance, avanceDias)
    val color = when {
        m.vencido -> Paleta.rojo
        p > 0.8f -> Paleta.ambar
        else -> Paleta.verde
    }
    Column(Modifier.fillMaxWidth().clip(Formas.chica).background(Paleta.superficie2).clickable(onClick = onEditar).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.nombre + if (m.vencido) "  · TOCA" else "", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${m.km} / ${m.intKm} km" + if (m.dias >= 0) "  ·  ${m.dias} / ${m.intDias} días" else "",
                    color = Paleta.textoSec, fontSize = 12.sp,
                )
            }
            BotonDeportivo("Hecho", onHecho, primario = m.vencido, color = if (m.vencido) Paleta.rojo else Paleta.lima, tamanoTexto = 12.sp)
        }
        Spacer(Modifier.heightIn(min = 8.dp))
        BarraPixel(p, color, bloques = 16)
    }
}

// ------------------------------------------------------------
// Control y calibración de la tarjeta
// ------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionControl() {
    val e by Moto.estado.enVivo()
    val cfg by Moto.config.collectAsStateWithLifecycle()
    val respuesta by Moto.ultimaRespuesta.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    var confirmarCorte by remember { mutableStateOf(false) }
    var calibrar by remember { mutableStateOf(false) }
    var odometro by remember { mutableStateOf(false) }
    var peligro by remember { mutableStateOf<String?>(null) }   // comando que pide confirmación
    val v13 = cfg != null

    Seccion("Control de la moto") {
        if (!conectada(conexion)) Text("Conecta la moto para usar estos controles.", color = Paleta.ambar, fontSize = 12.sp)
        Text("Perfil de manejo", color = Paleta.textoSec, fontSize = 13.sp)
        Selector(listOf("ECO", "SPORT"), e.perfil, { Moto.ajusteMoto(if (it == 1) "perfil sport" else "perfil eco") }, Modifier.fillMaxWidth())
        FilaSwitch(
            titulo = "Corte de motor",
            detalle = if (e.corte) "BLOQUEADO: la moto no arranca" else "Libre",
            activo = e.corte,
            colorActivo = Paleta.rojo,
        ) { on -> if (on) confirmarCorte = true else Moto.ajusteMoto("corte off") }

        Text("Velocidad desde", color = Paleta.textoSec, fontSize = 13.sp)
        Selector(listOf("Auto", "Sensor rueda", "GPS"), cfg?.fuenteVel ?: 0,
            { Moto.ajusteMoto(when (it) { 1 -> "vel hall"; 2 -> "vel gps"; else -> "vel auto" }) }, Modifier.fillMaxWidth())
        if (v13) {
            FilaSwitch("Brillo automático", "El LCD y los LEDs se ajustan con la luz del día", cfg?.autoBrillo == true) {
                Moto.ajusteMoto(if (it) "brillo auto" else "brillo fijo")
            }
            FilaSwitch("WiFi de la tarjeta", "Página web y respaldos. Al prenderlo se aplica al reiniciar", cfg?.wifi == true) {
                Moto.ajusteMoto(if (it) "wifi on" else "wifi off")
            }
        } else if (conectada(conexion)) {
            Text("Con el firmware v1.3 aparecen aquí brillo, WiFi, reinicio e información de la tarjeta.",
                color = Paleta.textoSec, fontSize = 11.sp)
        }

        Text("Pantalla del LCD", color = Paleta.textoSec, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VISTAS_LCD.forEachIndexed { i, nombre ->
                Box(
                    Modifier.clip(Formas.chip).border(1.dp, Paleta.borde, Formas.chip)
                        .clickable { Moto.ajusteMoto("pantalla $i") }.padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(nombre, color = Paleta.texto, fontSize = 12.sp) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Calibrar…", { calibrar = true }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("Odómetro…", { odometro = true }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Estado", { Moto.ajusteMoto("estado") }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("Eventos", { Moto.ajusteMoto("eventos") }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("Respaldo", { Moto.ajusteMoto("backup") }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (v13) BotonDeportivo("Reiniciar", { peligro = "reiniciar" }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("Modo ahorro", { peligro = "dormir" }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("De fábrica", { peligro = "borrar todo confirmar" }, Modifier.weight(1f), primario = false,
                color = Paleta.rojo, tamanoTexto = 12.sp)
        }
        if (respuesta.isNotBlank()) {
            Text("Moto: $respuesta", color = Paleta.lima, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 3)
        }
    }

    if (confirmarCorte) {
        AlertDialog(
            onDismissRequest = { confirmarCorte = false },
            title = { Text("¿Bloquear el motor?") },
            text = { Text("La moto no va a arrancar hasta que lo desbloquees. Solo funciona con la moto parada.") },
            confirmButton = {
                TextButton(onClick = { confirmarCorte = false; Moto.ajusteMoto("corte on confirmar") }) { Text("Bloquear", color = Paleta.rojo) }
            },
            dismissButton = { TextButton(onClick = { confirmarCorte = false }) { Text("Cancelar") } },
        )
    }
    peligro?.let { cmd ->
        val (titulo, texto) = when (cmd) {
            "reiniciar" -> "¿Reiniciar la tarjeta?" to "Se apaga y prende en unos segundos; la app se vuelve a conectar sola."
            "dormir" -> "¿Modo ahorro?" to "La tarjeta se duerme para gastar menos batería. Despierta al prender la moto."
            else -> "¿Regresar a valores de fábrica?" to "Se borran odómetro, calibraciones, mantenimiento y récords de la tarjeta. No se puede deshacer."
        }
        AlertDialog(
            onDismissRequest = { peligro = null },
            title = { Text(titulo) },
            text = { Text(texto) },
            confirmButton = { TextButton(onClick = { peligro = null; Moto.ajusteMoto(cmd) }) { Text("Sí", color = Paleta.rojo) } },
            dismissButton = { TextButton(onClick = { peligro = null }) { Text("Cancelar") } },
        )
    }
    if (calibrar) DialogoCalibrar { calibrar = false }
    if (odometro) DialogoOdometro { odometro = false }
}

@Composable
private fun DialogoCalibrar(onCerrar: () -> Unit) {
    var volts by remember { mutableStateOf("") }
    val respuesta by Moto.ultimaRespuesta.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Calibrar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtiquetaPixel("inclinacion", Paleta.lima, 7.sp)
                Text("Con la moto derecha y parada.", color = Paleta.textoSec, fontSize = 12.sp)
                BotonDeportivo("Calibrar inclinación", { Moto.ajusteMoto("cal imu") }, Modifier.fillMaxWidth(), tamanoTexto = 12.sp)

                EtiquetaPixel("bateria", Paleta.lima, 7.sp)
                Text("Mide la batería con el multímetro y escribe el voltaje.", color = Paleta.textoSec, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(volts, { volts = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(5) },
                        singleLine = true, placeholder = { Text("12.65") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Spacer(Modifier.width(8.dp))
                    val v = volts.toFloatOrNull()
                    BotonDeportivo("Guardar", { if (v != null && v in 9f..16f) Moto.ajusteMoto("cal bat $v") }, tamanoTexto = 12.sp)
                }

                EtiquetaPixel("gasolina", Paleta.lima, 7.sp)
                Text("Con el tanque vacío (en reserva) y luego lleno.", color = Paleta.textoSec, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonDeportivo("Vacío", { Moto.ajusteMoto("cal gas vacio") }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                    BotonDeportivo("Lleno", { Moto.ajusteMoto("cal gas lleno") }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                }

                EtiquetaPixel("marchas", Paleta.lima, 7.sp)
                Text("Ve rodando en esa marcha a velocidad constante y toca su número.", color = Paleta.textoSec, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (n in 1..5) BotonDeportivo("$n", { Moto.ajusteMoto("cal marcha $n") }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                }
                if (respuesta.isNotBlank()) Text("Moto: $respuesta", color = Paleta.lima, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Listo") } },
    )
}

@Composable
private fun DialogoOdometro(onCerrar: () -> Unit) {
    val v by Moto.viaje.enVivo()
    var km by remember { mutableStateOf(fmt(v.odometro, 1)) }
    val n = km.replace(',', '.').toFloatOrNull()
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Fijar odómetro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ponle lo que marca el odómetro de la moto.", color = Paleta.textoSec, fontSize = 12.sp)
                OutlinedTextField(km, { km = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(9) }, singleLine = true,
                    label = { Text("Kilómetros") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(enabled = n != null && n in 0f..999999f, onClick = { Moto.ajusteMoto("odo ${fmt(n ?: 0f, 1)}"); onCerrar() }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ------------------------------------------------------------
// Información de la tarjeta (firmware v1.3+)
// ------------------------------------------------------------
@Composable
private fun SeccionTarjeta() {
    val cfg by Moto.config.collectAsStateWithLifecycle()
    val c = cfg ?: return
    fun ok(b: Boolean) = if (b) "OK" else "no"
    Seccion("Tarjeta ESP32") {
        Dato("Firmware", c.fw)
        Dato("Encendida", tiempo(c.encendidaS * 1000))
        Dato("Memoria libre", "${c.memoriaKb} KB")
        Dato("Temperatura del chip", "${fmt(c.tempChip, 1)} °C", if (c.tempChip > 75) Paleta.ambar else Paleta.texto)
        Dato("Reloj · IMU · SD · Temp", "${ok(c.rtc)} · ${ok(c.imu)} · ${ok(c.sd)} · ${ok(c.sensorTemp)}")
        Dato("Rendimiento aprendido", "${fmt(c.kmPorLitro, 1)} km/L")
        Dato("Factor de batería", fmt(c.factorBat, 3))
        Dato("Gasolina vacío / lleno", "${c.gasVacioMv} / ${c.gasLlenoMv} mV")
        if (c.shift.isNotEmpty()) Dato("Cambio por marcha (rpm)", c.shift.joinToString(" · "))
        if (c.relacion.isNotEmpty()) Dato("rpm por km/h", c.relacion.joinToString(" · ") { fmt(it, 0) })
        BotonDeportivo("Actualizar", { Moto.pedirConfig() }, primario = false, tamanoTexto = 12.sp)
    }
}

// ------------------------------------------------------------
// Consola
// ------------------------------------------------------------
@Composable
private fun Consola() {
    val texto by Moto.consola.collectAsStateWithLifecycle()
    var cmd by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(texto) { scroll.animateScrollTo(scroll.maxValue) }

    fun enviar() {
        Moto.comando(cmd)
        cmd = ""
    }

    Seccion("Consola") {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp)
                .clip(Formas.chica).background(Color(0xFF020302)).border(1.dp, Paleta.limaOscuro, Formas.chica).padding(10.dp)
                .verticalScroll(scroll),
        ) {
            SelectionContainer {
                Text(texto, color = Paleta.lima, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                singleLine = true,
                placeholder = { Text("comando…") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { enviar() }),
            )
            Spacer(Modifier.width(8.dp))
            BotonDeportivo("Enviar", { enviar() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ayuda", "estado", "eventos", "mant").forEach { c ->
                BotonDeportivo(c, { Moto.comando(c) }, Modifier.weight(1f), primario = false, tamanoTexto = 11.sp)
            }
        }
    }
}
