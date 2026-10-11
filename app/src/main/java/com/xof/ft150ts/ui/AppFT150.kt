package com.xof.ft150ts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.Moto
import com.xof.ft150ts.PAGINA_TABLERO
import kotlinx.coroutines.launch

// 0 = apps (launcher, a la izquierda del tablero), 1 tablero, 2 auto, 3 moto, 4 ajustes (engrane)
private val PAGINAS = listOf("", "Tablero", "Auto", "Moto", "")
private const val PAG_TABLERO = PAGINA_TABLERO

/**
 * Raíz de la app: primero la animación de arranque y luego las cinco páginas,
 * que se cambian deslizando el dedo o tocando las pestañas.
 */
@Composable
fun AppFT150(onAccesoNotif: () -> Unit, onActivarBt: () -> Unit, onPermisos: () -> Unit) {
    // rememberSaveable: si giras el teléfono no se repite la animación
    var inicio by rememberSaveable { mutableStateOf(true) }
    // Las páginas ya se armaron una vez: de ahí en adelante se quedan armadas debajo de la animación
    var listo by rememberSaveable { mutableStateOf(false) }
    // Abriste el switch con la pantalla apagada: la animación sale en cuanto se ve la app
    val pendiente by Lanzador.inicioPendiente.collectAsStateWithLifecycle()
    LaunchedEffect(pendiente) {
        if (pendiente) { Lanzador.inicioPendiente.value = false; inicio = true }
    }
    var capa by remember { mutableStateOf(inicio) }
    val alfa = remember { Animatable(if (inicio) 1f else 0f) }
    LaunchedEffect(inicio) {
        if (inicio) {
            capa = true
            alfa.snapTo(1f)              // de golpe: la pantalla se acaba de prender
        } else if (capa) {
            alfa.animateTo(0f, tween(450))
            capa = false
            listo = true
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Arranque en frío: primero la animación sola (para que vaya fluida); luego las páginas
        // (con la animación encima, las páginas no se actualizan: no gastan mientras no se ven)
        if (listo || !inicio) Principal(onAccesoNotif, onActivarBt, onPermisos, tapado = inicio) { inicio = true }
        if (capa) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = alfa.value }) {
                PantallaInicio { inicio = false }
            }
        }
    }
}

@Composable
private fun Principal(
    onAccesoNotif: () -> Unit,
    onActivarBt: () -> Unit,
    onPermisos: () -> Unit,
    tapado: Boolean = false,
    onVerInicio: () -> Unit,
) {
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = PAG_TABLERO, pageCount = { PAGINAS.size })
    val scope = rememberCoroutineScope()
    val pedido by Lanzador.irAPagina.collectAsStateWithLifecycle()

    // Saltos pedidos desde fuera: botón Inicio (modo launcher) y modo moto (montar → tablero, quitar → apps).
    // Cada pedido se atiende una sola vez (al girar la pantalla no se repite).
    LaunchedEffect(pedido) {
        val (pagina, cuando) = pedido
        if (pagina in PAGINAS.indices && cuando > Lanzador.paginaAtendida) {
            Lanzador.paginaAtendida = cuando
            pager.animateScrollToPage(pagina)
        }
    }
    // Atrás: regresa al tablero; si la app es la pantalla de inicio, en el tablero no hace nada
    val soyInicio = remember(pager.currentPage) { Lanzador.esInicioPredeterminado(ctx) }
    BackHandler(enabled = pager.currentPage != PAG_TABLERO || soyInicio) {
        if (pager.currentPage != PAG_TABLERO) scope.launch { pager.animateScrollToPage(PAG_TABLERO) }
    }
    // Franja de arriba: deslizar aquí cambia de página aunque el mapa ocupe todo lo demás
    val deslizar = Modifier.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                val umbral = 48.dp.toPx()
                val destino = when {
                    total < -umbral -> (pager.currentPage + 1).coerceAtMost(PAGINAS.size - 1)
                    total > umbral -> (pager.currentPage - 1).coerceAtLeast(0)
                    else -> pager.currentPage
                }
                if (destino != pager.currentPage) scope.launch { pager.animateScrollToPage(destino) }
            },
        ) { cambio, dx -> total += dx; cambio.consume() }
    }
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val datosMalos by Moto.datosMalos.collectAsStateWithLifecycle()
    val llamada by Moto.iphoneLlamada.collectAsStateWithLifecycle()

    // Fondo AMOLED con cuadrícula pixel (y brillos en el tema Vidrio)
    Box(Modifier.fillMaxSize()) {
        FondoApp()
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            val irA: (Int) -> Unit = { i -> scope.launch { pager.animateScrollToPage(i) } }
            val alChip: () -> Unit = {
                when (conexion) {
                    Conexion.BT_APAGADO -> onActivarBt()
                    Conexion.SIN_PERMISO -> onPermisos()
                    else -> {}
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val ancho = maxWidth
                if (temaIntegrado) {
                    // ---- Filo / Pixel / Táctico: barra integrada (segmentos a 45° con resaltado que se desliza) ----
                    val finNav = remember { mutableFloatStateOf(0f) }
                    Column {
                        Row(
                            Modifier.fillMaxWidth().then(deslizar).padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LogoChico()
                            Spacer(Modifier.width(12.dp))
                            // Ocupa lo que sobra (la tira de estado queda pegada a la derecha); si no cabe, se desliza
                            Box(Modifier.weight(1f)) {
                                Box(Modifier.horizontalScroll(rememberScrollState()).onGloballyPositioned { finNav.floatValue = it.boundsInWindow().right }) {
                                    NavegacionFilo(pager, PAGINAS, irA)
                                }
                            }
                            if (ancho >= 700.dp) TiraEstado(Modifier.padding(horizontal = 10.dp), compacta = ancho < 880.dp)
                            ChipConexion(conexion, compacto = true, onClick = alChip)
                        }
                        LineaFilo(Modifier.then(deslizar).padding(bottom = 8.dp)) { finNav.floatValue }
                    }
                } else {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().then(deslizar).padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LogoChico()
                            Spacer(Modifier.width(14.dp))
                            // Si la pantalla es angosta, las pestañas se deslizan en vez de cortarse
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                PAGINAS.forEachIndexed { i, nombre ->
                                    val icono = when (i) { 0 -> Iconos.Apps; PAGINAS.size - 1 -> Iconos.Ajustes; else -> null }
                                    Pestana(nombre, pager.currentPage == i, icono) { irA(i) }
                                }
                            }
                            // El espacio libre de la derecha: hora, GPS, batería del cel y clima
                            if (ancho >= 760.dp) TiraEstado(Modifier.padding(end = 10.dp), compacta = ancho < 900.dp)
                            ChipConexion(conexion, onClick = alChip)
                        }
                        // línea lima con corte diagonal bajo la barra (con margen para deslizar entre páginas)
                        Canvas(Modifier.fillMaxWidth().height(14.dp).then(deslizar).padding(bottom = 11.dp)) {
                            val p = Path().apply {
                                moveTo(0f, 0f); lineTo(size.width * 0.32f, 0f)
                                lineTo(size.width * 0.32f - size.height, size.height); lineTo(0f, size.height); close()
                            }
                            drawPath(p, Paleta.lima)
                            drawRect(Paleta.borde, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.33f, size.height - 1f),
                                size = androidx.compose.ui.geometry.Size(size.width * 0.67f, 1f))
                        }
                    }
                }
            }
            if (datosMalos) {
                Text(
                    "Llegan datos incompletos de la moto. Reinicia el Bluetooth del teléfono.",
                    color = Paleta.ambar, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            // Con la pantalla apagada (o en otra app) ninguna página cuenta como visible: mapa, brújula y relojes descansan.
            // collectAsState (no ...WithLifecycle) a propósito: tiene que enterarse justo cuando se deja de ver.
            val enPantalla by Lanzador.enPantalla.collectAsState()
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = PAGINAS.size,   // todas armadas: el mapa no se recarga y no hay tirones
            ) { pagina ->
                // Las páginas ocultas siguen vivas pero apagan sus relojes y animaciones
                CompositionLocalProvider(LocalPaginaVisible provides (pager.currentPage == pagina && enPantalla && !tapado)) {
                    // Todas las páginas se quedan armadas (cambiar de pestaña es instantáneo);
                    // las que no se ven dejan de actualizarse (ver enVivo()).
                    when (pagina) {
                        0 -> PantallaApps()
                        1 -> PantallaTablero()
                        2 -> PantallaAuto(onAccesoNotif)
                        3 -> PantallaMoto()
                        else -> PantallaAjustes(onAccesoNotif, onVerInicio)
                    }
                }
            }
        }
    }

    // Llamada en el iPhone: aparece encima de cualquier pantalla
    var ocultarLlamada by remember { mutableStateOf("") }
    val llave = llamada.de + llamada.activa
    if (llamada.activa && ocultarLlamada != llave) {
        DialogoLlamada(llamada.de, llamada.acciones) { ocultarLlamada = llave }
    }
}

/** Llamada entrante en el iPhone: contestar o rechazar desde la pantalla de la moto. */
@Composable
private fun DialogoLlamada(de: String, acciones: Boolean, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { EtiquetaPixel("llamada en tu iphone", Paleta.lima, 9.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(de.ifBlank { "Número desconocido" }, color = Paleta.texto, fontSize = 26.sp, fontFamily = Fuentes.logo)
                if (!acciones) {
                    Text("Contesta desde el iPhone o el casco.", color = Paleta.textoSec, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            if (acciones) {
                BotonDeportivo("Contestar", { Moto.iphoneLlamadaCmd(true); onCerrar() }, color = Paleta.verde)
            } else {
                TextButton(onClick = onCerrar) { Text("Ocultar") }
            }
        },
        dismissButton = {
            if (acciones) BotonDeportivo("Rechazar", { Moto.iphoneLlamadaCmd(false); onCerrar() }, primario = false, color = Paleta.rojo)
        },
    )
}

@Composable
private fun LogoChico() {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("FT150", color = Paleta.texto, fontSize = 18.sp, fontFamily = Fuentes.logo,
                modifier = Modifier.alignByBaseline())
            Text("TS", color = Paleta.lima, fontSize = 8.sp, fontFamily = Fuentes.logo,
                modifier = Modifier.alignByBaseline().padding(start = 2.dp))
        }
        EtiquetaPixel("xof", Paleta.textoSec, 6.sp)
    }
}

@Composable
private fun Pestana(texto: String, activa: Boolean, icono: ImageVector?, onClick: () -> Unit) {
    val color = if (activa) Paleta.texto else Paleta.textoSec
    Column(
        Modifier.clip(Formas.chip).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = "Ajustes", tint = if (activa) Paleta.lima else color, modifier = Modifier.size(18.dp))
        } else {
            // Ícono pixel + nombre de la página
            val arte = when (texto) { "Tablero" -> PIXEL_TABLERO; "Auto" -> PIXEL_MAPA; else -> PIXEL_MOTO }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoPixel(arte, if (activa) Paleta.lima else color, Modifier.size(width = 14.dp, height = 12.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    texto.uppercase(),
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = if (activa) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.width(if (icono != null) 18.dp else 40.dp).height(4.dp)) {
            if (activa) {
                val p = Path().apply {
                    moveTo(size.height, 0f); lineTo(size.width, 0f)
                    lineTo(size.width - size.height, size.height); lineTo(0f, size.height); close()
                }
                drawPath(p, Paleta.lima)
            }
        }
    }
}

@Composable
private fun ChipConexion(c: Conexion, compacto: Boolean = false, onClick: () -> Unit) {
    val (texto, color) = when (c) {
        Conexion.CONECTADO -> "ON" to Paleta.lima
        Conexion.DEMO -> "DEMO" to Paleta.ambar
        Conexion.BUSCANDO -> (if (compacto) "BUSCA" else "BUSCANDO") to Paleta.textoSec
        Conexion.CONECTANDO -> "CONECTA" to Paleta.plata
        Conexion.BT_APAGADO -> "BT OFF" to Paleta.rojo
        Conexion.SIN_PERMISO -> (if (compacto) "PERM" else "PERMISOS") to Paleta.rojo
        Conexion.APAGADO -> "OFF" to Paleta.textoSec
    }
    Row(
        Modifier.clip(Formas.chip).background(Paleta.fondo.copy(alpha = 0.6f))
            .border(1.dp, color.copy(alpha = 0.7f), Formas.chip)
            .clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color))   // punto cuadrado, estilo pixel
        Spacer(Modifier.width(6.dp))
        EtiquetaPixel(texto, Paleta.texto, 7.sp)
    }
}
