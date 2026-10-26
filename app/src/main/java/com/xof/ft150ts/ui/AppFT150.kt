package com.xof.ft150ts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.CarPlay
import com.xof.ft150ts.CarPlayPestana
import com.xof.ft150ts.ModoCarPlay
import com.xof.ft150ts.Root
import com.xof.ft150ts.Logros
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.Moto
import com.xof.ft150ts.PAGINA_TABLERO
import com.xof.ft150ts.Reposo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 0 = apps (launcher, a la izquierda del tablero), 1 tablero, 2 auto, 3 moto, 4 ajustes (engrane)
private val PAGINAS = listOf("", "Tablero", "Auto", "Moto", "")
private const val PAG_TABLERO = PAGINA_TABLERO
private const val PAG_AUTO = 2

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
    // Reposo con el reloj: tapa todo y las páginas dejan de actualizarse (mapa, relojes y animaciones descansan)
    val reposo by Reposo.enReposo.collectAsStateWithLifecycle()
    val pantallaReposo by Reposo.pantalla.collectAsStateWithLifecycle()
    val reloj = reposo && pantallaReposo == 0
    Box(Modifier.fillMaxSize()) {
        // Arranque en frío: primero la animación sola (para que vaya fluida); luego las páginas
        // (con la animación encima, las páginas no se actualizan: no gastan mientras no se ven)
        if (listo || !inicio) Principal(onAccesoNotif, onActivarBt, onPermisos, tapado = inicio || reposo) { inicio = true }
        if (reloj && !capa) PantallaReposo { Reposo.salir("Tocaste la pantalla") }
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
    // derivedStateOf + settledPage: esta pantalla entera no se recompone (ni pregunta al sistema quién es el inicio)
    // a media animación de cada cambio de pestaña, solo al llegar o salir del tablero
    val enTablero by remember { derivedStateOf { pager.settledPage == PAG_TABLERO } }
    val soyInicio = remember(enTablero) { Lanzador.esInicioPredeterminado(ctx) }
    BackHandler(enabled = !enTablero || soyInicio) {
        if (pager.currentPage != PAG_TABLERO) scope.launch { pager.animateScrollToPage(PAG_TABLERO) }
    }
    // Franja de arriba: deslizar aquí cambia de página aunque el mapa (o CarPlay en modo Pestaña) ocupe todo lo demás.
    // Se OBSERVA el gesto en la pasada inicial, antes que los hijos: antes la fila de pestañas (que se desplaza de lado)
    // se comía el deslizar y solo funcionaba sobre el logo o el reloj. No consume nada: un toque sigue siendo un toque.
    val deslizar = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val abajo = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = 0f
            var x = abajo.position.x
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == abajo.id } ?: break
                total += c.position.x - x
                x = c.position.x
                if (!c.pressed) break
            }
            val umbral = 48.dp.toPx()
            val destino = when {
                total < -umbral -> (pager.currentPage + 1).coerceAtMost(PAGINAS.size - 1)
                total > umbral -> (pager.currentPage - 1).coerceAtLeast(0)
                else -> pager.currentPage
            }
            if (destino != pager.currentPage) scope.launch { pager.animateScrollToPage(destino) }
        }
    }
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val datosMalos by Moto.datosMalos.collectAsStateWithLifecycle()
    val llamada by Moto.iphoneLlamada.collectAsStateWithLifecycle()

    // Con la pantalla apagada (o en otra app) ninguna página cuenta como visible: mapa, brújula y relojes descansan.
    // collectAsState (no ...WithLifecycle) a propósito: tiene que enterarse justo cuando se deja de ver.
    val enPantalla by Lanzador.enPantalla.collectAsState()
    val cpTapaAuto by CarPlayPestana.visible.collectAsStateWithLifecycle()
    val irA: (Int) -> Unit = { i -> scope.launch { pager.animateScrollToPage(i) } }

    // CarPlay en modo Pestaña (root): aparece al estar en Auto y se esconde (sin cortar la sesión) al deslizar a otra
    // pestaña, al irte a otra app o en reposo. Sin root no se abre solo (en pantalla completa taparía FT150TS en ciclo).
    val cpOn by CarPlay.enMapa.collectAsStateWithLifecycle()
    val cpModo by CarPlay.modo.collectAsStateWithLifecycle()
    val rootOkCp = Root.activo.collectAsStateWithLifecycle().value
    val rootDispCp by Root.disponible.collectAsStateWithLifecycle()
    // Destino del pager: CarPlay aparece (o se va) en cuanto sueltas el deslizamiento, sin esperar la animación
    val enAuto = pager.targetPage == PAG_AUTO
    val cpPestana = cpOn && cpModo == ModoCarPlay.PESTANA && rootOkCp && rootDispCp == true
    LaunchedEffect(cpPestana, enAuto, enPantalla, tapado) {
        if (cpPestana && enAuto && enPantalla && !tapado) {
            delay(16)                                        // un cuadro
            CarPlayPestana.mostrar(ctx)
        } else CarPlayPestana.esconder(ctx)
    }
    val alChip: () -> Unit = {
        when (conexion) {
            Conexion.BT_APAGADO -> onActivarBt()
            Conexion.SIN_PERMISO -> onPermisos()
            else -> {}
        }
    }
    // Las cinco páginas (las mismas en todos los temas; lo que cambia es el marco alrededor)
    // Racing Morado: cuánto está bajada la barra (px que hay que sumar para saber dónde quedarían las páginas con la
    // barra arriba, que es como se ve CarPlay). 0 en los demás temas.
    val ajusteYRef = remember { intArrayOf(0) }
    val paginas: @Composable (Modifier) -> Unit = { mod ->
        HorizontalPager(
            state = pager,
            // Dónde quedan las páginas en la pantalla: ahí se pone CarPlay en el modo Pestaña
            modifier = mod.onGloballyPositioned { c ->
                val p = c.positionInWindow()
                val y = p.y.toInt() + ajusteYRef[0]
                val nueva = intArrayOf(p.x.toInt(), y, p.x.toInt() + c.size.width, y + c.size.height)
                val vieja = CarPlayPestana.area
                if (vieja == null || !vieja.contentEquals(nueva)) {
                    if (vieja != null) CarPlayPestana.reiniciarMedidas()      // giró la pantalla o cambió el marco
                    CarPlayPestana.area = nueva
                }
            },
            beyondViewportPageCount = PAGINAS.size,   // todas armadas: el mapa no se recarga y no hay tirones
        ) { pagina ->
            // Las páginas ocultas siguen vivas pero apagan sus relojes y animaciones
            // Con CarPlay (modo Pestaña) encima de Auto, la página de abajo no se ve: descansa como si estuviera oculta
            val tapadaPorCarPlay = pagina == PAG_AUTO && cpTapaAuto
            // Rendimiento: mientras se desliza sigue viva la página de donde sales y la de destino despierta al llegar.
            // Con currentPage despertaba a media animación (y la de en medio, p. ej. el mapa de Auto al ir de Tablero a
            // Moto, despertaba y se volvía a dormir): eso era el tirón al cambiar de pestaña.
            val viva = if (pager.isScrollInProgress) pager.settledPage else pager.currentPage
            CompositionLocalProvider(LocalPaginaVisible provides (viva == pagina && enPantalla && !tapado && !tapadaPorCarPlay)) {
                // Todas las páginas se quedan armadas (cambiar de pestaña es instantáneo);
                // las que no se ven dejan de actualizarse (ver enVivo()).
                // Rendimiento: cada página en su propia capa. Al deslizar, el pager solo mueve la capa; sin esto el hilo
                // principal volvía a grabar el dibujo completo de las páginas en cada cuadro del deslizamiento.
                Box(Modifier.fillMaxSize().graphicsLayer()) {
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
    val avisoDatos: @Composable () -> Unit = {
        if (datosMalos) {
            Text(
                "Llegan datos incompletos de la moto. Reinicia el Bluetooth del teléfono.",
                color = Paleta.ambar, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
    }

    // Barra de pestañas de siempre (logo, pestañas, tira de estado y chip) con su línea; [lineaArriba] la voltea
    // (Racing Morado con la barra abajo del tablero)
    val lineaBarra: @Composable (Boolean) -> Unit = { arriba ->
        // línea lima con corte diagonal junto a la barra (con margen para deslizar entre páginas)
        Canvas(Modifier.fillMaxWidth().height(14.dp).then(deslizar).padding(top = if (arriba) 11.dp else 0.dp, bottom = if (arriba) 0.dp else 11.dp)) {
            val p = Path().apply {
                moveTo(0f, 0f); lineTo(size.width * 0.32f, 0f)
                lineTo(size.width * 0.32f - size.height, size.height); lineTo(0f, size.height); close()
            }
            drawPath(p, Paleta.lima)
            drawRect(Paleta.borde, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.33f, if (arriba) 0f else size.height - 1f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.67f, 1f))
        }
    }
    val barraNormal: @Composable (Dp, Boolean) -> Unit = { ancho, lineaArriba ->
        Column {
            if (lineaArriba) lineaBarra(true)
            Row(
                Modifier.fillMaxWidth().then(deslizar).padding(start = 14.dp, end = 12.dp, top = if (lineaArriba) 4.dp else 8.dp, bottom = if (lineaArriba) 8.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoChico()
                Spacer(Modifier.width(14.dp))
                // Si la pantalla es angosta, las pestañas se deslizan en vez de cortarse
                FilaPestanas(pager, irA, Modifier.weight(1f))
                // El espacio libre de la derecha: hora, GPS, batería del cel y clima
                if (ancho >= 760.dp) TiraEstado(Modifier.padding(end = 10.dp), compacta = ancho < 900.dp)
                ChipConexion(conexion, onClick = alChip)
            }
            if (!lineaArriba) lineaBarra(false)
        }
    }

    // Fondo AMOLED con cuadrícula pixel (y brillos en el tema Vidrio)
    Box(Modifier.fillMaxSize()) {
        FondoApp()
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // CarPlay (modo Pestaña, Máximo) no debe tapar el riel de Vértice en horizontal
            val riel = temaVertice && maxWidth > maxHeight
            SideEffect { CarPlayPestana.conRiel = riel }
            if (riel) {
                // ---- Vértice en horizontal: cabina con el riel a la izquierda; arriba no hay barra ----
                Row(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalPaginaVisible provides (enPantalla && !tapado)) {
                        RielVertice(pager, PAGINAS, conexion, irA, alChip)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        LineaCarrera(Modifier.then(deslizar))
                        avisoDatos()
                        paginas(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            } else if (temaRacing) {
                // ---- Racing Morado: en el Tablero la barra baja y el tablero usa toda la pantalla de arriba; al ir a
                // cualquier otra pestaña (Auto con CarPlay) sube sola. Las páginas no cambian de tamaño: solo se
                // recorren (sin tirones) y CarPlay se coloca como si la barra estuviera arriba.
                val alto = constraints.maxHeight
                val ancho = maxWidth
                val abajo by animateFloatAsState(if (pager.targetPage == PAG_TABLERO) 1f else 0f, tween(320), label = "barra")
                var hBarra by remember { mutableIntStateOf(0) }
                val hBarraDp = with(LocalDensity.current) { hBarra.toDp() }
                Box(Modifier.fillMaxSize()) {
                    paginas(Modifier.fillMaxSize().padding(bottom = hBarraDp).offset {
                        ajusteYRef[0] = (hBarra * abajo).roundToInt()
                        IntOffset(0, (hBarra * (1f - abajo)).roundToInt())
                    })
                    Column(Modifier.fillMaxWidth().onSizeChanged { hBarra = it.height }
                        .offset { IntOffset(0, ((alto - hBarra) * abajo).roundToInt()) }) {
                        if (abajo > 0.5f) avisoDatos()
                        barraNormal(ancho, abajo > 0.5f)
                        if (abajo <= 0.5f) avisoDatos()
                    }
                }
            } else Column(Modifier.fillMaxSize()) {
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
                } else barraNormal(ancho, false)
            }
            avisoDatos()
            paginas(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    // Aviso de logro (baja desde arriba) y confeti del modo XOF
    val confeti by Lanzador.confeti.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        BannerLogro(Modifier.align(Alignment.TopCenter).padding(top = 64.dp))
        ConfetiPixel(confeti)
        if (Apariencia.medidorFluidez) MedidorFluidez(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(6.dp))
    }

    // Llamada en el iPhone: aparece encima de cualquier pantalla
    var ocultarLlamada by remember { mutableStateOf("") }
    val llave = llamada.de + llamada.activa
    if (llamada.activa && ocultarLlamada != llave) {
        DialogoLlamada(llamada.de, llamada.acciones) { ocultarLlamada = llave }
    }

    // SOS por caída: tapa todo (encima de cualquier página y del reposo)
    CuentaSosPantalla()
}

/**
 * Medidor de fluidez: cuadros por segundo, tirones (cuadros que se pasaron del tiempo de la pantalla) y el peor
 * cuadro del último segundo. Verde: menos de 1 % de tirones; ámbar: hasta 5 %; rojo: más.
 */
@Composable
private fun MedidorFluidez(modifier: Modifier = Modifier) {
    val m by com.xof.ft150ts.Fluidez.medida.collectAsStateWithLifecycle()
    val color = when {
        m.fps == 0 -> Paleta.textoSec
        m.tirones < 1f -> Paleta.verde
        m.tirones < 5f -> Paleta.ambar
        else -> Paleta.rojo
    }
    Column(
        modifier.background(Color.Black.copy(alpha = 0.75f)).border(1.dp, color.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        EtiquetaPixel(if (m.fps > 0) "${m.fps} fps · ${m.hz} hz" else "quieta", color, 7.sp)
        EtiquetaPixel("tirones ${fmt(m.tirones, 1)}% · peor ${fmt(m.peorMs, 1)} ms", Paleta.textoSec, 6.sp)
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
internal fun LogoChico() {
    // Secreto: 7 toques seguidos al logo → MODO XOF (acento Oro XOF, logro secreto y confeti)
    var toques by remember { mutableStateOf(0) }
    var ultimo by remember { mutableStateOf(0L) }
    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            val ahora = System.currentTimeMillis()
            toques = if (ahora - ultimo < 700) toques + 1 else 1
            ultimo = ahora
            if (toques >= 7) {
                toques = 0
                Apariencia.desbloquearXof()
                Logros.desbloquear("xof")
                Lanzador.confeti.value = ahora
            }
        }
    ) {
        if (temaRacing) {
            // Racing Morado: "FT150TS" en itálica de carreras con el TS morado y brillo, como el tablero
            Row(verticalAlignment = Alignment.Bottom) {
                val brillo = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Paleta.lima, blurRadius = 14f))
                Text("FT150", color = Paleta.texto, fontSize = 21.sp, fontFamily = Fuentes.logo,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                Text("TS", color = Paleta.lima, fontSize = 21.sp, fontFamily = Fuentes.logo,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, style = brillo)
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("FT150", color = Paleta.texto, fontSize = 18.sp, fontFamily = Fuentes.logo,
                    modifier = Modifier.alignByBaseline())
                Text("TS", color = Paleta.lima, fontSize = 8.sp, fontFamily = Fuentes.logo,
                    modifier = Modifier.alignByBaseline().padding(start = 2.dp))
            }
            EtiquetaPixel("xof", Paleta.textoSec, 6.sp)
        }
    }
}

@Composable
private fun Pestana(texto: String, activa: Boolean, icono: ImageVector?, onClick: () -> Unit) {
    val color = if (activa) Paleta.texto else Paleta.textoSec
    Column(
        // 48 dp de alto como mínimo: se atina fácil aun con guantes
        Modifier.heightIn(min = 48.dp).clip(Formas.chip).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = if (icono == Iconos.Apps) "Apps" else "Ajustes",
                tint = if (activa) Paleta.lima else color, modifier = Modifier.size(18.dp))
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
internal fun ChipConexion(c: Conexion, compacto: Boolean = false, onClick: () -> Unit) {
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
        EtiquetaPixel(texto, Paleta.texto, 8.sp)
    }
}

/** Las pestañas de la barra clásica: en su propia función para que al cambiar de página solo se recomponga esta fila. */
@Composable
private fun FilaPestanas(pager: PagerState, irA: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        PAGINAS.forEachIndexed { i, nombre ->
            val icono = when (i) { 0 -> Iconos.Apps; PAGINAS.size - 1 -> Iconos.Ajustes; else -> null }
            Pestana(nombre, pager.currentPage == i, icono) { irA(i) }
        }
    }
}
