package com.xof.ft150ts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.AppInstalada
import com.xof.ft150ts.Lanzador
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private fun horaYFecha(): Pair<String, String> {
    val d = Date()
    val fecha = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX")).format(d)
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(d) to fecha.replaceFirstChar { it.uppercase() }
}

/**
 * Página 0 (a la izquierda del tablero): tus apps, como un launcher.
 * Toca para abrir; mantén presionado para fijarla en favoritas o ponerla en la manzanita.
 */
@Composable
fun PantallaApps() {
    val ctx = LocalContext.current
    val visible = LocalPaginaVisible.current
    val apps by Lanzador.apps.collectAsStateWithLifecycle()
    val favs by Lanzador.favoritas.collectAsStateWithLifecycle()
    val manzana by Lanzador.appManzana.collectAsStateWithLifecycle()
    val cargando by Lanzador.cargando.collectAsStateWithLifecycle()
    var buscar by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf<AppInstalada?>(null) }
    var hora by remember { mutableStateOf(horaYFecha()) }

    // Solo trabaja con la página en pantalla
    LaunchedEffect(visible) {
        if (visible) Lanzador.cargar(ctx)
        while (visible) { hora = horaYFecha(); delay(10_000) }
    }

    val filtradas = remember(apps, buscar) {
        if (buscar.isBlank()) apps else apps.filter { it.nombre.contains(buscar.trim(), ignoreCase = true) }
    }
    val favApps = remember(apps, favs) { favs.mapNotNull { p -> apps.firstOrNull { it.paquete == p } } }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hora.first, color = Paleta.texto, fontSize = 44.sp, fontFamily = Fuentes.logo)
                Text(hora.second, color = Paleta.textoSec, fontSize = 13.sp)
            }
            ChipClima()
        }

        if (favApps.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().panel(Formas.chica).padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                favApps.forEach { a ->
                    IconoApp(a, marcada = a.paquete == manzana, conNombre = false, onClick = { Lanzador.abrir(ctx, a) }) { menu = a }
                }
            }
        }

        OutlinedTextField(
            value = buscar,
            onValueChange = { buscar = it },
            singleLine = true,
            placeholder = { Text("Buscar app") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (apps.isEmpty()) {
            Text(if (cargando) "Cargando apps…" else "Sin apps", color = Paleta.textoSec, fontSize = 13.sp)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(78.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(filtradas, key = { it.paquete + "/" + it.actividad }) { a ->
                IconoApp(
                    a, marcada = a.paquete == manzana || a.paquete in favs, conNombre = true,
                    onClick = { Lanzador.abrir(ctx, a) },
                ) { menu = a }
            }
        }
    }

    menu?.let { a -> DialogoApp(a, a.paquete in favs, a.paquete == manzana) { menu = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconoApp(
    app: AppInstalada,
    marcada: Boolean,
    conNombre: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier.clip(Formas.chica)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            IconoDeApp(app, 48.dp)
            if (marcada) Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(Paleta.lima))
        }
        if (conNombre) {
            Spacer(Modifier.height(5.dp))
            Text(
                app.nombre, color = Paleta.texto, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
        }
    }
}

@Composable
private fun DialogoApp(app: AppInstalada, favorita: Boolean, enManzana: Boolean, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(app.nombre, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonDeportivo(if (favorita) "Quitar de favoritas" else "Fijar en favoritas",
                    { Lanzador.alternarFavorita(ctx, app.paquete); onCerrar() }, Modifier.fillMaxWidth(), tamanoTexto = 13.sp)
                BotonDeportivo(if (enManzana) "Quitar de la manzanita" else "Usar en la manzanita",
                    { Lanzador.ponerManzana(ctx, if (enManzana) "" else app.paquete); onCerrar() },
                    Modifier.fillMaxWidth(), primario = false, tamanoTexto = 13.sp)
                BotonDeportivo("Información de la app", { Lanzador.abrirInfo(ctx, app.paquete); onCerrar() },
                    Modifier.fillMaxWidth(), primario = false, tamanoTexto = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

/** Lista de apps para elegir (botón de la manzanita). */
@Composable
internal fun DialogoElegirApp(titulo: String, onElegir: (AppInstalada) -> Unit, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val apps by Lanzador.apps.collectAsStateWithLifecycle()
    var buscar by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Lanzador.cargar(ctx) }
    val filtradas = remember(apps, buscar) {
        if (buscar.isBlank()) apps else apps.filter { it.nombre.contains(buscar.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = buscar, onValueChange = { buscar = it }, singleLine = true,
                    placeholder = { Text("Buscar app") }, modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (apps.isEmpty()) Text("Cargando apps…", color = Paleta.textoSec, fontSize = 13.sp)
                    filtradas.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(Formas.chica).clickable { onElegir(a) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconoDeApp(a, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(a.nombre, color = Paleta.texto, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
