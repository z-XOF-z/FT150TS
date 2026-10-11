package com.xof.ft150ts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Carga
import com.xof.ft150ts.Gasolina
import com.xof.ft150ts.MantTel
import com.xof.ft150ts.OdometroTel
import com.xof.ft150ts.TelefonoMoto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// Secciones de la página Moto que lleva el teléfono (sirven sin la tarjeta)

// ------------------------------------------------------------
// Odómetro y mantenimiento del teléfono
// ------------------------------------------------------------
@Composable
fun SeccionMantTel() {
    val km by OdometroTel.km.enVivo()
    val hoy by OdometroTel.kmHoy.enVivo()
    val ayer by OdometroTel.kmAyer.collectAsStateWithLifecycle()
    val mant by OdometroTel.mant.collectAsStateWithLifecycle()
    var fijar by remember { mutableStateOf(false) }
    var servicio by remember { mutableStateOf<Int?>(null) }
    var editar by remember { mutableStateOf<Int?>(null) }

    Seccion("Odómetro y mantenimiento (teléfono)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Odómetro", fmt(km.toFloat(), 1), Modifier.weight(1f), unidad = "km", compacta = true)
            Tarjeta("Hoy / Ayer", fmt(hoy.toFloat(), 1), Modifier.weight(1f), unidad = "km",
                extra = "Ayer ${fmt(ayer.toFloat(), 1)} km", compacta = true)
        }
        BotonDeportivo("Igualar con el odómetro de la moto", { fijar = true }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        Text("Suma los km por GPS solo cuando está en la moto (modo moto o motor por vibración).",
            color = Paleta.textoSec, fontSize = 11.sp)
        mant.forEachIndexed { i, m -> FilaMantTel(m, { editar = i }) { servicio = i } }
        Text("Toca un renglón para cambiar cada cuánto. \"Hecho\" cuando le hagas el servicio (empieza a contar desde hoy).",
            color = Paleta.textoSec, fontSize = 11.sp)
    }
    if (fijar) DialogoNumero("Odómetro de la moto", "Kilómetros", fmt(km.toFloat(), 1), decimal = true,
        valido = { it in 0.0..999_999.0 }, onCerrar = { fijar = false }) { OdometroTel.ponerOdometro(it) }
    servicio?.let { i ->
        AlertDialog(
            onDismissRequest = { servicio = null },
            title = { Text("¿Registrar servicio?") },
            text = { Text("\"${mant.getOrNull(i)?.nombre}\" empieza a contar desde hoy y desde ${fmt(km.toFloat(), 0)} km.") },
            confirmButton = { TextButton(onClick = { OdometroTel.registrarServicio(i); servicio = null }) { Text("Registrar") } },
            dismissButton = { TextButton(onClick = { servicio = null }) { Text("Cancelar") } },
        )
    }
    editar?.let { i ->
        val m = mant.getOrNull(i)
        if (m != null) DialogoIntervaloTel(i, m) { editar = null }
    }
}

@Composable
private fun FilaMantTel(m: MantTel, onEditar: () -> Unit, onHecho: () -> Unit) {
    val nuevo = m.fechaUltimo <= 0L
    val kmD = OdometroTel.kmDesde(m)
    val dias = OdometroTel.diasDesde(m)
    val p = if (nuevo) 0f else maxOf((kmD / m.intKm).toFloat(), if (dias >= 0) dias.toFloat() / m.intDias else 0f)
    val vencido = !nuevo && OdometroTel.vencido(m)
    val color = when { nuevo -> Paleta.textoSec; vencido -> Paleta.rojo; p > 0.8f -> Paleta.ambar; else -> Paleta.verde }
    Column(Modifier.fillMaxWidth().clip(Formas.chica).background(Paleta.superficie2).clickable(onClick = onEditar).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.nombre + if (vencido) "  · TOCA" else "", color = if (nuevo) Paleta.texto else color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (nuevo) "Cada ${m.intKm} km o ${m.intDias} días · sin registrar"
                    else "${kmD.toInt()} / ${m.intKm} km  ·  $dias / ${m.intDias} días",
                    color = Paleta.textoSec, fontSize = 12.sp,
                )
            }
            BotonDeportivo("Hecho", onHecho, primario = vencido, color = if (vencido) Paleta.rojo else Paleta.lima, tamanoTexto = 12.sp)
        }
        if (!nuevo) {
            Spacer(Modifier.heightIn(min = 8.dp))
            BarraPixel(p.coerceIn(0f, 1f), color, bloques = 16)
        }
    }
}

@Composable
private fun DialogoIntervaloTel(i: Int, m: MantTel, onCerrar: () -> Unit) {
    var km by remember { mutableStateOf("${m.intKm}") }
    var dias by remember { mutableStateOf("${m.intDias}") }
    val kmN = km.toIntOrNull()
    val diasN = dias.toIntOrNull()
    val ok = kmN != null && kmN in 50..60000 && diasN != null && diasN in 1..3650
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(m.nombre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(km, { km = it.filter(Char::isDigit).take(5) }, singleLine = true, label = { Text("Cada cuántos km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(dias, { dias = it.filter(Char::isDigit).take(4) }, singleLine = true, label = { Text("Cada cuántos días") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { OdometroTel.cambiarIntervalo(i, kmN ?: m.intKm, diasN ?: m.intDias); onCerrar() }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ------------------------------------------------------------
// Bitácora de gasolina
// ------------------------------------------------------------
@Composable
fun SeccionGasolina() {
    val cargas by Gasolina.cargas.collectAsStateWithLifecycle()
    val r by Gasolina.resumen.collectAsStateWithLifecycle()
    val cap by Gasolina.capacidad.collectAsStateWithLifecycle()
    var nueva by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf<Carga?>(null) }
    var capacidad by remember { mutableStateOf(false) }
    val fecha = remember { SimpleDateFormat("d MMM", Locale("es", "MX")) }

    Seccion("Gasolina") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Rendimiento", if (r.rendimiento > 0) fmt(r.rendimiento, 1) else "--", Modifier.weight(1f), unidad = "km/L",
                extra = if (r.ultimoRendimiento > 0) "Último ${fmt(r.ultimoRendimiento, 1)}" else "Necesita 2 llenos", compacta = true)
            Tarjeta("Te quedan", if (r.autonomia >= 0) "~${r.autonomia}" else "--", Modifier.weight(1f), unidad = "km",
                extra = if (r.litrosRestantes >= 0) "~${fmt(r.litrosRestantes, 1)} L" else "Carga y marca lleno",
                color = if (r.autonomia in 0..30) Paleta.rojo else Paleta.texto,
                progreso = if (r.litrosRestantes >= 0) r.litrosRestantes / cap else null, compacta = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Costo por km", if (r.costoKm > 0) "$${fmt(r.costoKm, 2)}" else "--", Modifier.weight(1f), compacta = true)
            Tarjeta("Este mes", "$${fmt(r.gastoMes, 0)}", Modifier.weight(1f),
                extra = if (r.precioLitro > 0) "$${fmt(r.precioLitro, 2)}/L" else "", compacta = true)
        }
        BotonDeportivo("Cargué gasolina", { nueva = true }, Modifier.fillMaxWidth(), tamanoTexto = 13.sp)
        cargas.sortedByDescending { it.km }.take(8).forEach { c ->
            Row(
                Modifier.fillMaxWidth().clip(Formas.chica).background(Paleta.superficie2).clickable { borrar = c }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fecha.format(Date(c.fecha)), color = Paleta.textoSec, fontSize = 12.sp, modifier = Modifier.width(56.dp))
                Text("${fmt(c.litros, 2)} L · $${fmt(c.precio, 0)}", color = Paleta.texto, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${c.km.toInt()} km" + if (c.lleno) " · lleno" else "", color = Paleta.textoSec, fontSize = 11.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tanque: ${fmt(cap, 1)} L (FT150TS de fábrica 13.3 L)", color = Paleta.textoSec, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("Cambiar", color = Paleta.lima, fontSize = 12.sp, modifier = Modifier.clickable { capacidad = true }.padding(4.dp))
        }
        Text("Para el rendimiento llena el tanque y marca \"lleno\" dos veces seguidas. Toca una carga para borrarla.",
            color = Paleta.textoSec, fontSize = 11.sp)
    }
    if (nueva) DialogoCarga { nueva = false }
    borrar?.let { c ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text("¿Borrar esta carga?") },
            text = { Text("${fmt(c.litros, 2)} L · $${fmt(c.precio, 0)} · ${c.km.toInt()} km") },
            confirmButton = { TextButton(onClick = { Gasolina.borrar(c); borrar = null }) { Text("Borrar", color = Paleta.rojo) } },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } },
        )
    }
    if (capacidad) DialogoNumero("Capacidad del tanque", "Litros", fmt(cap, 1), decimal = true,
        valido = { it in 3.0..30.0 }, onCerrar = { capacidad = false }) { Gasolina.ponerCapacidad(it.toFloat()) }
}

@Composable
private fun DialogoCarga(onCerrar: () -> Unit) {
    var litros by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var km by remember { mutableStateOf(fmt(OdometroTel.odometroActual().toFloat(), 1)) }
    var lleno by remember { mutableStateOf(true) }
    val l = litros.replace(',', '.').toFloatOrNull()
    val p = precio.replace(',', '.').toFloatOrNull()
    val k = km.replace(',', '.').toDoubleOrNull()
    val ok = l != null && l in 0.2f..30f && p != null && p >= 0f && k != null && k >= 0
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Cargué gasolina") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(litros, { litros = filtroNumero(it) }, singleLine = true, label = { Text("Litros") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(precio, { precio = filtroNumero(it) }, singleLine = true, label = { Text("Pagaste ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(km, { km = filtroNumero(it) }, singleLine = true, label = { Text("Odómetro (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                FilaSwitch("Llené el tanque", "Así se calcula el rendimiento real", lleno) { lleno = it }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                Gasolina.agregar(Carga(System.currentTimeMillis(), l ?: 0f, p ?: 0f, k ?: 0.0, lleno))
                // Si el odómetro que pusiste difiere del del teléfono, se iguala
                if (k != null && abs(k - OdometroTel.km.value) > 0.5) OdometroTel.ponerOdometro(k)
                onCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

private fun filtroNumero(t: String) = t.filter { it.isDigit() || it == '.' || it == ',' }.take(9)

@Composable
private fun DialogoNumero(
    titulo: String, etiqueta: String, inicial: String, decimal: Boolean,
    valido: (Double) -> Boolean, onCerrar: () -> Unit, onGuardar: (Double) -> Unit,
) {
    var t by remember { mutableStateOf(inicial) }
    val n = t.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(t, { t = filtroNumero(it) }, singleLine = true, label = { Text(etiqueta) },
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
        },
        confirmButton = { TextButton(enabled = n != null && valido(n), onClick = { onGuardar(n ?: 0.0); onCerrar() }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ------------------------------------------------------------
// Salud del ralentí
// ------------------------------------------------------------
@Composable
fun SeccionRalenti() {
    val rpm by TelefonoMoto.ralentiMedido.collectAsStateWithLifecycle()
    val desv by TelefonoMoto.ralentiDesv.collectAsStateWithLifecycle()
    val historial by TelefonoMoto.historialRalenti.collectAsStateWithLifecycle()
    val ref by TelefonoMoto.ralenti.collectAsStateWithLifecycle()
    val estado = TelefonoMoto.estadoRalenti()
    val color = when {
        rpm <= 0 -> Paleta.textoSec
        estado == "Bien" -> Paleta.verde
        else -> Paleta.ambar
    }
    Seccion("Ralentí (por vibración)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Ralentí", if (rpm > 0) "$rpm" else "--", Modifier.weight(1f), unidad = "rpm", color = color,
                extra = "Normal ~$ref", compacta = true)
            Tarjeta("Estabilidad", if (rpm > 0) "±$desv" else "--", Modifier.weight(1f), unidad = "rpm",
                color = if (desv > 120) Paleta.ambar else Paleta.texto, compacta = true)
        }
        Text(estado, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (historial.size >= 2) {
            EtiquetaPixel("ultimas mediciones", Paleta.textoSec, 7.sp)
            GraficaPixel(historial.map { it.rpm.toFloat() }, maxOf(2500f, historial.maxOf { it.rpm }.toFloat()), Paleta.lima)
        }
        Text("Se mide solo: motor prendido más de 4 min (caliente) y la moto parada 10 s, sin acelerar. Compara contra el ralentí " +
            "que pusiste al calibrar; sirve para ver si cambia con el tiempo (bujía, filtro, carburación).",
            color = Paleta.textoSec, fontSize = 11.sp)
    }
}

// ------------------------------------------------------------
// Altitud y pendiente
// ------------------------------------------------------------
@Composable
fun SeccionAltitud() {
    val h by OdometroTel.altura.enVivo()
    fun m(v: Float) = if (v.isNaN()) "--" else "${v.toInt()}"
    val p = h.pendiente
    Seccion("Altitud y pendiente") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Altitud", m(h.actual), Modifier.weight(1f), unidad = "m", extra = "Máx ${m(h.maxima)} · mín ${m(h.minima)}", compacta = true)
            Tarjeta("Pendiente", if (h.actual.isNaN()) "--" else "${if (p > 0) "+" else ""}${p.toInt()}", Modifier.weight(1f), unidad = "%",
                color = when { abs(p) >= 10 -> Paleta.ambar; else -> Paleta.texto },
                extra = when { p >= 2 -> "Subida"; p <= -2 -> "Bajada"; else -> "Plano" }, compacta = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Subiste", "${h.subida.toInt()}", Modifier.weight(1f), unidad = "m", compacta = true)
            Tarjeta("Bajaste", "${h.bajada.toInt()}", Modifier.weight(1f), unidad = "m", compacta = true)
        }
        Text("Con el GPS (el Poco F4 no trae barómetro): la altitud puede variar ±10 m y la pendiente se calcula en los últimos ~150 m.",
            color = Paleta.textoSec, fontSize = 11.sp)
    }
}

// ------------------------------------------------------------
// Batería del teléfono
// ------------------------------------------------------------
@Composable
fun SeccionBateriaTel() {
    val b by com.xof.ft150ts.BateriaTel.estado.enVivo()
    val historial by com.xof.ft150ts.BateriaTel.historial.enVivo()
    val colorPct = when { b.pct < 0 -> Paleta.texto; b.pct <= 15 -> Paleta.rojo; b.pct <= 30 -> Paleta.ambar; else -> Paleta.verde }
    Seccion("Batería del teléfono") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Batería", if (b.pct >= 0) "${b.pct}" else "--", Modifier.weight(1f), unidad = "%", color = colorPct,
                extra = when {
                    b.cargando && b.minutosLlenar > 0 -> "Llena en ~${b.minutosLlenar} min"
                    b.cargando -> "Cargando (${b.fuente.lowercase()})"
                    else -> "Sin cargar"
                },
                progreso = if (b.pct >= 0) b.pct / 100f else null, compacta = true)
            Tarjeta(if (b.cargando) "Entra" else "Gasta", if (b.vatios > 0f) fmt(b.vatios, 1) else "--", Modifier.weight(1f), unidad = "W",
                color = if (b.cargando) Paleta.lima else Paleta.texto,
                extra = if (b.miliamperes != 0) "${abs(b.miliamperes)} mA" else "", compacta = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("Voltaje", if (b.voltios > 0f) fmt(b.voltios, 2) else "--", Modifier.weight(1f), unidad = "V", compacta = true)
            Tarjeta("Temperatura", if (b.temperatura > 0f) fmt(b.temperatura, 1) else "--", Modifier.weight(1f), unidad = "°C",
                color = when { b.temperatura >= 43f -> Paleta.rojo; b.temperatura >= 40f -> Paleta.ambar; else -> Paleta.texto }, compacta = true)
        }
        Dato("Salud", b.salud.ifBlank { "--" }, if (b.salud == "Buena") Paleta.verde else Paleta.texto)
        if (b.ciclos >= 0) Dato("Ciclos de carga", "${b.ciclos}")
        if (historial.size >= 2) {
            EtiquetaPixel("porcentaje (ultimas 2 h)", Paleta.textoSec, 7.sp)
            GraficaPixel(historial, 100f, Paleta.lima)
        }
        Text("Con la moto prendida deberían entrar varios watts; si marca 0 o \"Sin cargar\", revisa el cable o el cargador USB de la moto.",
            color = Paleta.textoSec, fontSize = 11.sp)
    }
}
