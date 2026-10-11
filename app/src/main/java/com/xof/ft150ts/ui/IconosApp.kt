package com.xof.ft150ts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.AppInstalada
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.NUM_ACCESOS

/**
 * Ícono de una app. Con "íconos pixelados" se pinta en cuadritos de 16x16
 * con un huequito entre ellos, igual que los pixeles del tablero LCD.
 */
@Composable
fun IconoDeApp(app: AppInstalada?, tam: Dp, modifier: Modifier = Modifier) {
    val estilo by Lanzador.estiloIconos.collectAsStateWithLifecycle()
    val px = if (estilo == 2) app?.pixeladoLcd else app?.pixelado
    if (estilo != 0 && px != null) {
        // Fluidez: el ícono pixelado ya viene pintado (se hizo al leer las apps); aquí solo se copia
        val imagen = remember(px) { px.asImageBitmap() }
        Image(
            imagen, contentDescription = app?.nombre, modifier = modifier.size(tam), filterQuality = FilterQuality.None,
            colorFilter = if (estilo == 2) ColorFilter.tint(Paleta.lima, BlendMode.Modulate) else null,
        )
    } else {
        val img = remember(app?.icono) { app?.icono?.asImageBitmap() }
        if (img != null) Image(img, contentDescription = app?.nombre, modifier = modifier.size(tam))
        else Box(modifier.size(tam).clip(Formas.chica).background(Paleta.superficie2))
    }
}

/** Signo "+" pixelado para un acceso vacío. */
@Composable
private fun MasPixel(tam: Dp, color: Color) {
    Canvas(Modifier.size(tam)) {
        val n = 7
        val cel = size.minDimension / n
        val lado = cel * 0.84f
        for (i in 1..5) {
            drawRect(color, Offset(3 * cel, i * cel), Size(lado, lado))
            drawRect(color, Offset(i * cel, 3 * cel), Size(lado, lado))
        }
    }
}

/**
 * Accesos directos del tablero: 4 lugares que asignas tú.
 * Toca para abrir (o elegir si está vacío); mantén presionado para cambiar o quitar.
 */
/** Ancho de la columna de accesos para un alto dado (los cuadros se achican si no caben). */
fun anchoAccesos(alto: Dp): Dp = minOf(56.dp, (alto - 6.dp * (NUM_ACCESOS - 1)) / NUM_ACCESOS).coerceAtLeast(36.dp)

@Composable
fun AccesosDirectos(vertical: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val visible = LocalPaginaVisible.current
    val accesos by Lanzador.accesos.collectAsStateWithLifecycle()
    val apps by Lanzador.apps.collectAsStateWithLifecycle()
    var editar by remember { mutableIntStateOf(-1) }     // lugar que se está eligiendo
    var menu by remember { mutableIntStateOf(-1) }       // lugar con el menú abierto
    LaunchedEffect(visible) { if (visible) Lanzador.cargar(ctx) }

    val contenido: @Composable (Dp) -> Unit = { tam ->
        for (i in 0 until NUM_ACCESOS) {
            val pk = accesos.getOrElse(i) { "" }
            val app = apps.firstOrNull { it.paquete == pk }
            AccesoDirecto(
                app = app,
                tam = tam,
                vacio = pk.isBlank(),
                onClick = {
                    when {
                        pk.isBlank() -> editar = i
                        !Lanzador.abrirPaquete(ctx, pk) -> editar = i
                    }
                },
                onLongClick = { if (pk.isBlank()) editar = i else menu = i },
            )
        }
    }
    if (vertical) {
        // Subidos unos milímetros (reales, con los dpi de la pantalla) para librar los ganchos del soporte
        val dm = ctx.resources.displayMetrics
        val subir = (Apariencia.subirAccesosMm / 25.4f * dm.ydpi / dm.density.coerceAtLeast(0.5f)).dp
        BoxWithConstraints(modifier) {
            val tam = anchoAccesos(maxHeight)
            val alto = tam * NUM_ACCESOS + 6.dp * (NUM_ACCESOS - 1)
            val arriba = ((maxHeight - alto) / 2 - subir).coerceAtLeast(0.dp)
            Column(Modifier.width(tam).fillMaxHeight().padding(top = arriba), verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) { contenido(tam) }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) { contenido(56.dp) }
    }

    if (editar >= 0) {
        val lugar = editar
        DialogoElegirApp(
            "Acceso directo ${lugar + 1}",
            onElegir = { a -> Lanzador.ponerAcceso(ctx, lugar, a.paquete); editar = -1 },
            onCerrar = { editar = -1 },
        )
    }
    if (menu >= 0) {
        val lugar = menu
        val nombre = apps.firstOrNull { it.paquete == accesos.getOrElse(lugar) { "" } }?.nombre ?: "Acceso ${lugar + 1}"
        AlertDialog(
            onDismissRequest = { menu = -1 },
            title = { Text(nombre, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonDeportivo("Cambiar app", { menu = -1; editar = lugar }, Modifier.fillMaxWidth(), tamanoTexto = 13.sp)
                    BotonDeportivo("Quitar", { Lanzador.ponerAcceso(ctx, lugar, ""); menu = -1 }, Modifier.fillMaxWidth(),
                        primario = false, color = Paleta.rojo, tamanoTexto = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { menu = -1 }) { Text("Cerrar") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccesoDirecto(app: AppInstalada?, tam: Dp, vacio: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val forma = Formas.chica
    Box(
        Modifier.size(tam).panel(forma)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            vacio -> MasPixel(tam * 0.4f, Paleta.textoSec.copy(alpha = 0.6f))
            app != null -> IconoDeApp(app, tam * 0.64f)
            else -> MasPixel(tam * 0.4f, Paleta.ambar)      // la app ya no está instalada
        }
    }
}


// ------------------------------------------------------------
// Íconos pixel chiquitos (pestañas, carga). Cada letra que no sea "." es un cuadrito.
// ------------------------------------------------------------
val PIXEL_TABLERO = listOf(
    "..XXXXX..", ".X.....X.", "X...X...X", "X....X..X", "X.....X.X", "X.......X", ".XXXXXXX.",
)
val PIXEL_MAPA = listOf(
    "...XXX...", "..X...X..", "..X.X.X..", "..X...X..", "...X.X...", "....X....", ".XXXXXXX.",
)
val PIXEL_MOTO = listOf(
    ".....XX..", "...XXX...", "XXXXXXXX.", ".XX..X.X.", "X..X.X..X", "X..X..X.X", ".XX....X.",
)
val PIXEL_RAYO = listOf(
    "...XX", "..XX.", ".XXXX", "XXXX.", "..XX.", ".XX..", ".X...",
)

@Composable
fun IconoPixel(arte: List<String>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val filas = arte.size
        val cols = arte.maxOf { it.length }
        val cel = minOf(size.width / cols, size.height / filas)
        val lado = cel * 0.86f
        val x0 = (size.width - cel * cols) / 2
        val y0 = (size.height - cel * filas) / 2
        for (f in arte.indices) for (c in arte[f].indices) {
            if (arte[f][c] != '.') drawRect(color, Offset(x0 + c * cel, y0 + f * cel), Size(lado, lado))
        }
    }
}
