package com.xof.ft150ts.ui

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.AccionNoche
import com.xof.ft150ts.CambioNoche
import com.xof.ft150ts.CarPlay
import com.xof.ft150ts.DiagnosticoEnergia
import com.xof.ft150ts.EstadoNoche
import com.xof.ft150ts.Horario
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.LogicaNoche
import com.xof.ft150ts.NocheRegistro
import com.xof.ft150ts.Nocturno
import com.xof.ft150ts.PerfilNoche
import com.xof.ft150ts.Root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ajustes → Ahorro nocturno: horario, perfil, acciones, apps, excepciones, estado real y diagnóstico. */
@Composable
fun SeccionAhorroNocturno() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val activo by Nocturno.activo.collectAsStateWithLifecycle()
    val horario by Nocturno.horario.collectAsStateWithLifecycle()
    val perfil by Nocturno.perfil.collectAsStateWithLifecycle()
    val acciones by Nocturno.acciones.collectAsStateWithLifecycle()
    val estado by Nocturno.estado.collectAsStateWithLifecycle()
    val cambios by Nocturno.cambios.collectAsStateWithLifecycle()
    val evento by Nocturno.evento.collectAsStateWithLifecycle()
    val proxima by Nocturno.proxima.collectAsStateWithLifecycle()
    val exactas by Nocturno.exactas.collectAsStateWithLifecycle()
    val protegidaHasta by Nocturno.protegidaHasta.collectAsStateWithLifecycle()
    val respetarCp by Nocturno.respetarCarPlay.collectAsStateWithLifecycle()
    val historial by Nocturno.historial.collectAsStateWithLifecycle()
    val pruebaHasta by Nocturno.pruebaHasta.collectAsStateWithLifecycle()
    val posponer by Nocturno.posponerSiUso.collectAsStateWithLifecycle()
    val rootActivo by Root.activo.collectAsStateWithLifecycle()
    val rootDisponible by Root.disponible.collectAsStateWithLifecycle()
    val nApps by Nocturno.apps.collectAsStateWithLifecycle()
    val rootOk = rootActivo && rootDisponible == true
    var editar by remember { mutableStateOf<Boolean?>(null) }   // true = inicio, false = fin
    var elegirApps by remember { mutableStateOf(false) }
    var verDiag by remember { mutableStateOf(false) }

    Seccion("Ahorro nocturno") {
        FilaSwitch("Ahorro nocturno",
            "En el horario el teléfono gasta lo mínimo y al terminar todo regresa como estaba. No apaga el teléfono.",
            activo) { Nocturno.ponerActivo(it) }

        Dato("Ahora", estado.texto + if (estado == EstadoNoche.ACTIVO || estado == EstadoNoche.POSPUESTO || estado == EstadoNoche.FUERA)
            " · revisa a las ${Nocturno.horaTexto(proxima)}" else "",
            when (estado) { EstadoNoche.ACTIVO -> Paleta.ambar; EstadoNoche.POSPUESTO, EstadoNoche.PROTEGIDO -> Paleta.lima; else -> Paleta.texto })
        if (evento.isNotBlank()) Text(evento, color = Paleta.lima, fontSize = 12.sp)

        // Horario
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Inicio ${Horario.texto(horario.inicio)}", { editar = true }, Modifier.weight(1f), primario = false, tamanoTexto = 13.sp)
            BotonDeportivo("Fin ${Horario.texto(horario.fin)}", { editar = false }, Modifier.weight(1f), primario = false, tamanoTexto = 13.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in 1..7) {
                val on = d in horario.dias
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).clip(Formas.chica)
                        .background(if (on) Paleta.lima else Color.Transparent)
                        .border(1.dp, Paleta.lima.copy(alpha = 0.6f), Formas.chica)
                        .clickable {
                            val nuevos = if (on) horario.dias - d else horario.dias + d
                            if (nuevos.isNotEmpty()) Nocturno.ponerHorario(horario.copy(dias = nuevos))
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(Horario.NOMBRES_DIAS[d - 1], color = if (on) Color.Black else Paleta.texto, fontWeight = FontWeight.Bold) }
            }
        }
        Text("Hora de Android (${java.time.ZoneId.systemDefault().id}) · ${horario.minutos / 60} h ${horario.minutos % 60} min. " +
            "La noche cuenta para el día en que empieza.", color = Paleta.textoSec, fontSize = 11.sp)

        // Perfil y acciones
        Selector(PerfilNoche.entries.map { it.nombre }, PerfilNoche.entries.indexOf(perfil), { Nocturno.ponerPerfil(PerfilNoche.entries[it]) },
            Modifier.fillMaxWidth())
        for (a in AccionNoche.entries) {
            val sinRoot = a.root && !rootOk
            FilaSwitch(a.nombre + if (a.root) " (root)" else "",
                a.explicacion + if (sinRoot) " Sin root no se aplica." else "",
                a in acciones, if (a == AccionNoche.DATOS || a == AccionNoche.WIFI) Paleta.rojo else Paleta.lima) { Nocturno.ponerAccion(a, it) }
        }
        if (AccionNoche.RESTRINGIR in acciones || AccionNoche.CERRAR in acciones) {
            BotonDeportivo("Elegir apps a restringir (${nApps.size})", { elegirApps = true }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        }
        if (!rootOk) {
            Text("Las acciones con (root) necesitan Ajustes → Root (Magisk) → \"Pedir acceso root\". Sin root solo se duerme FT150TS " +
                "y se pausa la sincronización; nada se muestra como aplicado si no se aplicó.", color = Paleta.ambar, fontSize = 11.sp)
        }

        // Excepciones
        FilaSwitch("Respetar CarPlay", "No entra si hay una sesión de CarPlay en uso en la página Auto (se revisa cada 15 min).",
            respetarCp) { Nocturno.ponerRespetarCarPlay(it) }
        Text("Posponer si usaste el teléfono hace menos de", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val opc = listOf(0, 10, 30)
        Selector(listOf("No", "10 min", "30 min"), opc.indexOf(posponer).coerceAtLeast(0), { Nocturno.ponerPosponerSiUso(opc[it]) }, Modifier.fillMaxWidth())
        Text("Si vas rodando o la moto está conectada tampoco entra.", color = Paleta.textoSec, fontSize = 11.sp)
        if (protegidaHasta > System.currentTimeMillis()) {
            BotonDeportivo("Quitar protección de esta noche", { Nocturno.quitarProteccion() }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        } else {
            BotonDeportivo("Proteger esta noche (no ahorrar)", { Nocturno.protegerEstaNoche() }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        }

        if (!exactas) {
            Text("Android no deja alarmas exactas a esta app: el ahorro puede empezar o terminar unos minutos tarde.",
                color = Paleta.ambar, fontSize = 11.sp)
            BotonDeportivo("Permitir alarmas exactas", { DiagnosticoEnergia.abrirAlarmasExactas(ctx) }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        }

        // Prueba: una noche de 15 min ya, para ver que todo se aplica y se deshace
        if (pruebaHasta > System.currentTimeMillis()) {
            Dato("Prueba en curso", "hasta las ${Nocturno.horaTexto(pruebaHasta)}", Paleta.ambar)
            BotonDeportivo("Terminar la prueba (deshacer)", { Nocturno.terminarPrueba() }, Modifier.fillMaxWidth(),
                primario = false, tamanoTexto = 12.sp)
        } else {
            BotonDeportivo("Probar ahora 15 min", { Nocturno.probarAhora(15) }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        }

        // Cuánto gastó cada noche (para ver si el perfil sirve)
        if (historial.isNotEmpty()) {
            Text("Gasto de batería por noche", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            historial.takeLast(7).reversed().forEach { FilaNoche(it) }
            val conDatos = historial.mapNotNull { it.gastoHora }
            if (conDatos.isNotEmpty()) Dato("Promedio", "%.2f %%/h".format(conDatos.average()))
            TextButton({ Nocturno.borrarHistorial() }) { Text("Borrar historial", fontSize = 11.sp) }
        }

        // Lo que de verdad se aplicó
        if (cambios.isNotEmpty()) {
            Text("Acciones de la última noche", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            cambios.forEach { FilaCambio(it) }
        }

        BotonDeportivo("Desactivar ya y deshacer todo", { Nocturno.desactivarYa() }, Modifier.fillMaxWidth(),
            color = Paleta.rojo, tamanoTexto = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Diagnóstico", {
                verDiag = true
                scope.launch { DiagnosticoEnergia.correr(ctx) }
            }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            BotonDeportivo("De fábrica", { Nocturno.predeterminados() }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
        }

        BloqueJuntos()
    }

    editar?.let { inicio ->
        DialogoHora(if (inicio) "Empieza a las" else "Termina a las", if (inicio) horario.inicio else horario.fin,
            onListo = { m ->
                val h = if (inicio) horario.copy(inicio = m) else horario.copy(fin = m)
                if (h.valido) Nocturno.ponerHorario(h)
                else Toast.makeText(ctx, "El inicio y el fin no pueden ser iguales", Toast.LENGTH_SHORT).show()
                editar = null
            }, onCerrar = { editar = null })
    }
    if (elegirApps) DialogoAppsNoche { elegirApps = false }
    if (verDiag) DialogoDiagnostico { verDiag = false }
}

/** "jue 00:00–06:30 · 92 → 86 % · 0.92 %/h". */
@Composable
private fun FilaNoche(n: NocheRegistro) {
    val z = java.time.ZoneId.systemDefault()
    val d = java.time.Instant.ofEpochMilli(n.desde).atZone(z)
    val h = java.time.Instant.ofEpochMilli(n.hasta).atZone(z)
    val dia = Horario.NOMBRES_DIAS[d.dayOfWeek.value - 1]
    val g = n.gastoHora
    Dato(
        "$dia ${"%02d:%02d".format(d.hour, d.minute)}–${"%02d:%02d".format(h.hour, h.minute)}" + if (n.prueba) " (prueba)" else "",
        "${n.pctIni} → ${n.pctFin} % · " + when {
            n.cargo -> "cargando"
            g == null -> "muy corta"
            else -> "%.2f %%/h".format(g)
        } + " · ${n.perfil.lowercase()}",
        when { g == null -> Paleta.textoSec; g <= 1f -> Paleta.verde; g <= 2.5f -> Paleta.texto; else -> Paleta.ambar },
    )
}

@Composable
private fun FilaCambio(c: CambioNoche) {
    val (marca, color) = when {
        !c.ok -> "✗" to Paleta.rojo
        c.revertido -> "↺" to Paleta.textoSec
        else -> "✓" to Paleta.verde
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(marca, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(c.accion.nombre + (if (c.objetivo.isNotEmpty()) " · ${c.objetivo}" else "") + " — " + c.detalle,
            color = Paleta.texto, fontSize = 12.sp)
    }
}

/** FT150TS y DiPlay juntos: lo convencional primero; root solo para lo que Android no deja hacer a mano. */
@Composable
private fun BloqueJuntos() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparado by remember { mutableStateOf(DiagnosticoEnergia.diplayPreparado(ctx)) }
    var trabajando by remember { mutableStateOf(false) }
    val diplay = remember { CarPlay.buscar(ctx) }
    Text("FT150TS y DiPlay juntos", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp))
    Text("Para que MIUI no cierre a ninguna de las dos: \"Inicio automático\" prendido y ahorro de batería \"Sin restricciones\" " +
        "en las dos. FT150TS lo hace con Root → \"Dar permisos y quitar ahorro de batería\". Esto no garantiza que siga la " +
        "sesión de CarPlay: eso depende de DiPlay y del iPhone.", color = Paleta.textoSec, fontSize = 11.sp)
    if (diplay == null) {
        Text("DiPlay no está instalado.", color = Paleta.ambar, fontSize = 12.sp)
        return
    }
    BotonDeportivo("Inicio automático (MIUI)", { DiagnosticoEnergia.abrirInicioAutomatico(ctx, diplay.paquete) },
        Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
    fun correr(f: suspend () -> String) {
        if (trabajando) return
        trabajando = true
        scope.launch {
            val t = f()
            preparado = DiagnosticoEnergia.diplayPreparado(ctx)
            trabajando = false
            Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
        }
    }
    if (!preparado) {
        BotonDeportivo(if (trabajando) "Esperando a Magisk…" else "Quitar restricciones de batería a DiPlay (root)",
            { correr { DiagnosticoEnergia.prepararDiPlay(ctx) } }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
    } else {
        BotonDeportivo(if (trabajando) "Esperando a Magisk…" else "Regresar DiPlay como estaba (root)",
            { correr { DiagnosticoEnergia.deshacerDiPlay(ctx) } }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHora(titulo: String, minutos: Int, onListo: (Int) -> Unit, onCerrar: () -> Unit) {
    val st = rememberTimePickerState(minutos / 60, minutos % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { EtiquetaPixel(titulo, Paleta.lima, 9.sp) },
        text = { TimePicker(st) },
        confirmButton = { TextButton(onClick = { onListo(st.hour * 60 + st.minute) }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

/** Elegir qué apps se restringen/cierran de noche. Las protegidas y las del sistema no aparecen. */
@Composable
private fun DialogoAppsNoche(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val todas by Lanzador.apps.collectAsStateWithLifecycle()
    val elegidas by Nocturno.apps.collectAsStateWithLifecycle()
    val protegidas by Nocturno.protegidas.collectAsStateWithLifecycle()
    var lista by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(todas, protegidas) {
        lista = withContext(Dispatchers.IO) {
            todas.filter { a ->
                !LogicaNoche.protegidoSiempre(a.paquete, ctx.packageName, protegidas) &&
                    try { ctx.packageManager.getApplicationInfo(a.paquete, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0 } catch (_: Exception) { false }
            }.map { it.paquete to it.nombre }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { EtiquetaPixel("apps a restringir de noche", Paleta.lima, 9.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("No aparecen FT150TS, DiPlay, teléfono, SMS, reloj, launcher ni apps del sistema: esas nunca se tocan. " +
                    "\"Proteger\" la quita de aquí para siempre.", color = Paleta.textoSec, fontSize = 11.sp)
                val l = lista
                if (l == null) Text("Cargando…", color = Paleta.textoSec)
                else if (l.isEmpty()) Text("No hay apps para elegir (abre la página Apps una vez para cargarlas).", color = Paleta.textoSec)
                else l.forEach { (p, n) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(p in elegidas, { Nocturno.ponerApp(p, it) },
                            colors = CheckboxDefaults.colors(checkedColor = Paleta.lima, checkmarkColor = Color.Black))
                        Text(n, color = Paleta.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton({ Nocturno.ponerProtegida(p, true) }) { Text("Proteger", fontSize = 11.sp) }
                    }
                }
                if (protegidas.isNotEmpty()) {
                    Text("Protegidas por ti: ${protegidas.size}", color = Paleta.textoSec, fontSize = 11.sp)
                    BotonDeportivo("Quitar todas mis protecciones", { protegidas.forEach { Nocturno.ponerProtegida(it, false) } },
                        Modifier.fillMaxWidth(), primario = false, tamanoTexto = 11.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Listo") } },
    )
}

@Composable
private fun DialogoDiagnostico(onCerrar: () -> Unit) {
    val lineas by DiagnosticoEnergia.lineas.collectAsStateWithLifecycle()
    val encendido by DiagnosticoEnergia.encendido.collectAsStateWithLifecycle()
    val trabajando by DiagnosticoEnergia.trabajando.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { EtiquetaPixel("diagnostico de energia", Paleta.lima, 9.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (trabajando) Text("Leyendo (si Magisk pregunta, permite)…", color = Paleta.textoSec, fontSize = 12.sp)
                lineas.forEach { (k, v) -> Dato(k, v) }
                encendido?.let {
                    Text("Encenderse solo desde apagado", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(it.texto, color = Paleta.ambar, fontSize = 12.sp)
                }
                Text("Solo lectura: el diagnóstico no cambia nada en el teléfono.", color = Paleta.textoSec, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}
