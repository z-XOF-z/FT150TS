package com.xof.ft150ts.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Acciones
import com.xof.ft150ts.AudioSalida
import com.xof.ft150ts.Bache
import com.xof.ft150ts.Baches
import com.xof.ft150ts.Brujula
import com.xof.ft150ts.ClimaManager
import com.xof.ft150ts.ComandosVoz
import com.xof.ft150ts.Estacionamiento
import com.xof.ft150ts.EstiloMapa
import com.xof.ft150ts.GiroPid
import com.xof.ft150ts.IphoneMusica
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.LimitesManager
import com.xof.ft150ts.Lugar
import com.xof.ft150ts.Luz
import com.xof.ft150ts.Mapas
import com.xof.ft150ts.Moto
import com.xof.ft150ts.Musica
import com.xof.ft150ts.MusicaManager
import com.xof.ft150ts.Root
import com.xof.ft150ts.UbicacionManager
import com.xof.ft150ts.posicionAhora
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

/** Página 2: modo tipo Android Auto. Mapa, clima, música y lo esencial de la moto. */
@Composable
fun PantallaAuto(onAccesoNotif: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
        val horizontal = maxWidth > maxHeight
        if (horizontal) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MapaMoto(Modifier.weight(1.6f).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MiniTablero(Modifier.fillMaxWidth())
                    TarjetaMusica(Modifier.fillMaxWidth(), onAccesoNotif)
                }
            }
        } else {
            // El mapa se queda con lo que sobra; si la pantalla es chica, se desliza
            val altoMapa = max(220f, maxHeight.value - 420f).dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapaMoto(Modifier.fillMaxWidth().height(altoMapa))
                MiniTablero(Modifier.fillMaxWidth())
                TarjetaMusica(Modifier.fillMaxWidth(), onAccesoNotif)
            }
        }
    }
}

// ------------------------------------------------------------
// Mapa (OpenStreetMap)
// ------------------------------------------------------------
@Composable
private fun MapaMoto(modifier: Modifier) {
    val ctx = LocalContext.current
    val visible = LocalPaginaVisible.current
    val ubic by UbicacionManager.ubicacion.enVivo()
    val recorrido by UbicacionManager.recorrido.collectAsStateWithLifecycle()
    val est by Moto.estado.enVivo()
    val estilo by Moto.estiloMapa.collectAsStateWithLifecycle()
    val nocheAuto by Moto.nocheAuto.collectAsStateWithLifecycle()
    val clima by ClimaManager.clima.collectAsStateWithLifecycle()
    val rumbo by Moto.rumboArriba.collectAsStateWithLifecycle()
    val lugar by Estacionamiento.lugar.collectAsStateWithLifecycle()
    var pedirDestino by remember { mutableStateOf(false) }
    var verMoto by remember { mutableStateOf(false) }
    var seguir by remember { mutableStateOf(true) }

    val acento = Paleta.lima
    val mapa = remember { crearMapa(ctx) }
    // Rombo del color de acento con borde negro (estilo pixel)
    val rombo = remember {
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(6, 0xFF000000.toInt())
            setSize(44, 44)
        }
    }
    val marcador = remember {
        Marker(mapa).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = rombo
            rotation = 45f
            setOnMarkerClickListener { _, _ -> true }   // sin globito al tocarlo
        }
    }
    val marcadorMoto = remember {
        Marker(mapa).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = iconoMoto(ctx)
            setOnMarkerClickListener { _, _ -> verMoto = true; true }
        }
    }
    val linea = remember {
        Polyline(mapa).apply { outlinePaint.strokeWidth = 12f }
    }
    // Baches que ha sentido el teléfono
    val capaBaches = remember { CapaBaches(ctx.resources.displayMetrics.density) }
    val baches by Baches.lista.collectAsStateWithLifecycle()
    val mostrarBaches by Baches.mostrar.collectAsStateWithLifecycle()
    LaunchedEffect(baches, mostrarBaches) {
        capaBaches.poner(if (mostrarBaches) baches else emptyList(), Baches.umbral() * 1.6f)
        if (visible) mapa.invalidate()
    }
    // Si cambias el color de acento en Ajustes, el mapa también cambia
    LaunchedEffect(acento) {
        rombo.setColor(acento.toArgb())
        linea.outlinePaint.color = acento.copy(alpha = 0.87f).toArgb()
        mapa.invalidate()
    }

    DisposableEffect(mapa) {
        mapa.overlays.add(capaBaches)
        mapa.overlays.add(linea)
        mapa.overlays.add(marcador)
        onDispose {
            mapa.onPause()
            mapa.onDetach()
        }
    }

    // Posición del mapa: GPS del teléfono con señal → GPS de la moto → aproximada (wifi/antenas, mientras agarra
    // satélites adentro) → lo último que se supo
    val gps by UbicacionManager.estado.enVivo()
    val aproxLoc by UbicacionManager.aprox.enVivo()
    val punto: GeoPoint? = run {
        val u = ubic
        val a = aproxLoc
        when {
            u != null && gps.conSenal -> GeoPoint(u.latitude, u.longitude)
            est.gpsFix && !est.gpsTelefono -> GeoPoint(est.lat, est.lon)
            a != null && gps.aprox -> GeoPoint(a.latitude, a.longitude)
            u != null -> GeoPoint(u.latitude, u.longitude)
            est.gpsFix -> GeoPoint(est.lat, est.lon)
            else -> null
        }
    }

    // Brújula del teléfono: prendida solo con el mapa en pantalla
    DisposableEffect(visible) {
        if (visible) Brujula.arrancar()
        onDispose { if (visible) Brujula.detener() }
    }
    // Posición: solo cuando llega otra (el giro va aparte, así no se reinicia la animación del centro)
    LaunchedEffect(punto?.latitude, punto?.longitude, seguir, visible) {
        if (punto != null) {
            marcador.position = punto
            // Con la página oculta no anima nada (ahorra batería)
            if (seguir && visible) mapa.controller.animateTo(punto)
            if (visible) mapa.invalidate()
        }
    }
    // Giro del mapa: un PID (como el de la aguja) lo lleva suave al rumbo a 60 cuadros por segundo, sin escalones.
    // Rumbo: el del GPS rodando; parado o despacio, la brújula del teléfono. Sin recomponer la pantalla.
    val giro = remember { GiroPid() }
    val rumboFino = remember { mutableFloatStateOf(-1f) }     // hacia dónde vas (flecha de la brújula)
    val giroMapa = remember { mutableFloatStateOf(0f) }       // cuánto está girado el mapa en la pantalla
    LaunchedEffect(visible, rumbo, seguir) {
        if (!visible) return@LaunchedEffect
        var usarGps = false
        var tAnt = 0L
        var aplicado = Float.NaN
        while (true) {
            val t = withFrameNanos { it }
            if (tAnt != 0L && t - tAnt < 15_000_000L) continue           // hasta 60 por segundo (la pantalla va a 120)
            val dt = if (tAnt == 0L) 1f / 60f else ((t - tAnt) / 1e9f).coerceIn(0.005f, 0.05f)
            tAnt = t
            val u = UbicacionManager.ubicacion.value
            val v = UbicacionManager.velocidad.value
            usarGps = if (usarGps) v > 6f else v > 9f                      // con margen: no brinca de uno a otro en 8 km/h
            val hacia = when {
                u != null && u.hasBearing() && usarGps -> u.bearing
                Brujula.fino >= 0f -> Brujula.fino
                else -> -1f
            }
            rumboFino.floatValue = hacia
            val objetivo = if (rumbo && seguir && hacia >= 0f) hacia else 0f
            if (giro.quieta(objetivo) && !aplicado.isNaN()) {
                // Ya llegó: descansa un poco (no redibuja el mapa por ruido de medio grado)
                delay(60)
                tAnt = 0L
                continue
            }
            val g = giro.paso(objetivo, dt)
            val pantalla = (360f - g) % 360f
            if (aplicado.isNaN() || abs(GiroPid.diferencia(pantalla, aplicado)) > 0.1f) {
                mapa.mapOrientation = pantalla
                aplicado = pantalla
                giroMapa.floatValue = pantalla
            }
        }
    }
    // Fluidez: con la página oculta el mapa no se dibuja (osmdroid dibujaría fuera de la pantalla)
    LaunchedEffect(visible) {
        mapa.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
        if (visible) { mapa.onResume(); mapa.invalidate() } else mapa.onPause()
    }
    LaunchedEffect(recorrido) {
        linea.setPoints(recorrido)
        if (visible) mapa.invalidate()
    }
    // Estilo del mapa. De noche (según el clima o la hora) cambia solo a "Noche" si así lo quieres
    var horaDia by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(visible) {
        while (visible) { horaDia = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); delay(300_000) }
    }
    val c = clima
    // Con el sensor de luz manda la luz real (túneles, nublado); si no hay, el clima o la hora
    val oscuroLuz by Luz.oscuro.collectAsStateWithLifecycle()
    val usarLuz by Luz.usar.collectAsStateWithLifecycle()
    val porLuz = oscuroLuz
    val esNoche = if (usarLuz && porLuz != null) porLuz
    else if (c != null && System.currentTimeMillis() - c.hora < 3 * 3600_000L) !c.esDia else (horaDia >= 19 || horaDia < 7)
    val efectivo = if (nocheAuto && esNoche && !estilo.oscuro) EstiloMapa.NOCHE else estilo
    val llaveCarto by Mapas.llaveCarto.collectAsStateWithLifecycle()
    LaunchedEffect(efectivo, llaveCarto) {
        val fuente = Mapas.fuente(efectivo)
        mapa.setTileSource(fuente)
        if (mapa.zoomLevelDouble > fuente.maximumZoomLevel) mapa.controller.setZoom(fuente.maximumZoomLevel.toDouble())
        val capa = mapa.overlayManager.tilesOverlay
        capa.setColorFilter(Mapas.filtro(efectivo))
        // mientras cargan los cuadros: negro en estilos oscuros (en AMOLED no se ve la rejilla gris)
        capa.setLoadingBackgroundColor(if (efectivo.oscuro) android.graphics.Color.BLACK else android.graphics.Color.rgb(226, 226, 226))
        capa.setLoadingLineColor(if (efectivo.oscuro) android.graphics.Color.rgb(18, 18, 18) else android.graphics.Color.rgb(205, 205, 205))
        mapa.invalidate()
    }
    LaunchedEffect(lugar) {
        val l = lugar
        if (l != null) {
            marcadorMoto.position = GeoPoint(l.lat, l.lon)
            if (!mapa.overlays.contains(marcadorMoto)) {
                mapa.overlays.add(mapa.overlays.indexOf(marcador).coerceAtLeast(0), marcadorMoto)
            }
        } else {
            mapa.overlays.remove(marcadorMoto)
        }
        mapa.invalidate()
    }
    // Después de ver dónde quedó la moto, a los 20 s vuelve a seguirte
    LaunchedEffect(seguir) {
        if (!seguir) { delay(20_000); seguir = true }
    }

    // Lugar del mapa en la pantalla (ahí se pone la ventana de la manzanita)
    var rectMapa by remember { mutableStateOf<android.graphics.Rect?>(null) }
    Box(
        modifier.panel().onGloballyPositioned { c ->
            val b = c.boundsInWindow()
            val m = 8
            rectMapa = android.graphics.Rect(b.left.toInt() + m, b.top.toInt() + m, b.right.toInt() - m, b.bottom.toInt() - m)
        }
    ) {
        AndroidView(factory = { mapa }, modifier = Modifier.fillMaxSize())

        // Capa invisible encima del mapa: así el mapa no "se roba" el deslizar
        // entre pantallas. El mapa siempre te sigue; usa + y − para el zoom.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }
        )

        val avisoGps = when {
            punto == null -> "buscando gps..."
            gps.buscando -> when {
                gps.atorado -> "el gps tiene posicion pero no llega"
                gps.senalDebil -> "señal debil (bajo techo) · ${gps.fuertes} sat buenos"
                else -> "buscando satelites (${gps.fuertes} con buena señal)"
            } + if (gps.aprox) " - mapa aprox." else ""
            gps.edadS > 5 -> "sin señal gps (${gps.edadS} s)" + (if (gps.senalDebil) " · señal debil" else "") +
                if (gps.aprox) " - mapa aprox." else ""
            else -> null
        }
        if (avisoGps != null) {
            EtiquetaPixel(avisoGps, if (punto == null) Paleta.lima else Paleta.ambar, 8.sp,
                Modifier.align(Alignment.Center).background(Paleta.fondo.copy(alpha = 0.8f)).padding(6.dp))
        }

        Column(
            Modifier.align(Alignment.TopStart).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipClima()
            ChipGps()
            BotonRedondo("P", { verMoto = true })
            BotonMicrofono()
        }

        NombreCalle(Modifier.align(Alignment.TopCenter).padding(top = 12.dp))

        Column(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotonRedondo("+", { mapa.controller.zoomIn() })
            BotonRedondo("−", { mapa.controller.zoomOut() })
            // Brújula: la "N" marca el norte y la flecha hacia dónde vas; tócala para girar el mapa con tu rumbo
            BotonBrujula({ rumboFino.floatValue }, { giroMapa.floatValue }, rumbo) { Moto.ponerRumboArriba(!rumbo) }
            BotonManzana(rectMapa)
        }

        Row(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VelocidadMapa(Modifier)
            SenalLimite()
        }

        BotonDeportivo(
            "Navegar", { pedirDestino = true },
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
        )

        // Crédito obligatorio del mapa
        Text(
            Mapas.credito(efectivo),
            color = Paleta.textoSec, fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                .background(Paleta.fondo.copy(alpha = 0.7f)).padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }

    if (pedirDestino) {
        DialogoDestino(
            onIr = { destino, guardar -> pedirDestino = false; Acciones.navegarA(ctx, destino, guardar) },
            onCerrar = { pedirDestino = false },
        )
    }

    if (verMoto) {
        DialogoMoto(
            lugar = lugar,
            punto = punto,
            onVer = {
                val l = lugar
                if (l != null) {
                    seguir = false
                    val moto = GeoPoint(l.lat, l.lon)
                    val yo = punto
                    if (yo != null && yo.distanceToAsDouble(moto) > 60.0) {
                        giro.colocar(0f); giroMapa.floatValue = 0f
                        mapa.mapOrientation = 0f
                        mapa.zoomToBoundingBox(BoundingBox.fromGeoPoints(listOf(yo, moto)), true, 140)
                    } else {
                        mapa.controller.animateTo(moto)
                    }
                }
            },
            onCerrar = { verMoto = false },
        )
    }
}

/** Velocidad, rumbo y altitud encima del mapa (se redibuja solo, sin tocar el mapa). */
@Composable
private fun VelocidadMapa(modifier: Modifier) {
    val ubic by UbicacionManager.ubicacion.enVivo()
    val rap by Moto.rapida.enVivo()
    val exceso by Moto.excesoVel.enVivo()
    val velTel by UbicacionManager.velocidad.enVivo()
    val brujula by Brujula.rumbo.enVivo()
    val u = ubic
    val vel = if (rap.vel >= 0) rap.vel else velTel
    // Rodando: rumbo del GPS. Parado o despacio (< 8 km/h): brújula del teléfono
    val rumbo = when {
        u != null && u.hasBearing() && velTel > 8f -> puntoCardinal(u.bearing)
        brujula >= 0f -> puntoCardinal(brujula)
        else -> ""
    }
    val alt = if (u != null && u.hasAltitude()) "${u.altitude.roundToInt()} m" else ""
    val extra = listOf(rumbo, alt).filter { it.isNotEmpty() }.joinToString("  ·  ")
    val color = if (exceso) Paleta.rojo else Paleta.lima

    Column(
        modifier.clip(Formas.boton).background(Paleta.fondo.copy(alpha = 0.9f))
            .border(1.5.dp, color, Formas.boton)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (vel >= 0) "${(vel + 0.5f).toInt()}" else "--", color = if (exceso) Paleta.rojo else Paleta.texto,
            fontSize = 34.sp, fontFamily = Fuentes.logo)
        EtiquetaPixel("km/h", color, 7.sp)
        if (extra.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(extra, color = Paleta.textoSec, fontSize = 11.sp, maxLines = 1)
        }
    }
}

private fun puntoCardinal(grados: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    val g = ((grados % 360f) + 360f) % 360f
    return dirs[((g / 45f) + 0.5f).toInt() % 8] + " ${g.roundToInt()}°"
}

/** Ícono "P" pixelado para el lugar donde quedó la moto. */
private fun iconoMoto(ctx: Context): Drawable {
    val tam = 52
    val bmp = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = Paint()
    p.color = 0xFF000000.toInt()
    c.drawRect(0f, 0f, tam.toFloat(), tam.toFloat(), p)
    p.color = 0xFFF4F7F2.toInt()
    c.drawRect(5f, 5f, tam - 5f, tam - 5f, p)
    p.color = 0xFF000000.toInt()
    val letra = intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000)
    val cel = 5f
    val x0 = (tam - 5 * cel) / 2f
    val y0 = (tam - 7 * cel) / 2f
    for (f in 0 until 7) for (col in 0 until 5) {
        if ((letra[f] shr (4 - col)) and 1 == 1) {
            c.drawRect(x0 + col * cel, y0 + f * cel, x0 + (col + 1) * cel - 1f, y0 + (f + 1) * cel - 1f, p)
        }
    }
    return BitmapDrawable(ctx.resources, bmp)
}

internal fun crearMapa(ctx: Context): MapView {
    val conf = Configuration.getInstance()
    conf.userAgentValue = ctx.packageName
    conf.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
    conf.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
    // Caché de mapas en disco: hasta 2 GB (lo que ya viste carga al instante y sirve sin señal)
    conf.tileFileSystemCacheMaxBytes = 2048L * 1024 * 1024
    conf.tileFileSystemCacheTrimBytes = 1700L * 1024 * 1024
    return MapView(ctx).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(false)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        controller.setZoom(17.0)
        controller.setCenter(GeoPoint(21.5042, -104.8946))
    }
}

private val RAPIDOS = listOf("Gasolinera", "Taller de motos", "Llantera", "Hospital", "Cajero")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoDestino(onIr: (String, Boolean) -> Unit, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var texto by remember { mutableStateOf("") }
    val recientes by Acciones.recientes.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("¿A dónde vas?", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    singleLine = true,
                    label = { Text("Dirección o lugar") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onIr(texto, true) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Lo más cercano:", color = Paleta.textoSec, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RAPIDOS.forEach { r -> ChipOpcion(r, false) { onIr(r, false) } }
                }
                if (recientes.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recientes:", color = Paleta.textoSec, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("Borrar", color = Paleta.textoSec, fontSize = 12.sp,
                            modifier = Modifier.clickable { Acciones.borrarRecientes(ctx) }.padding(4.dp))
                    }
                    recientes.forEach { d -> FilaApp(d, "", false) { onIr(d, true) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onIr(texto, true) }) { Text("IR", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ------------------------------------------------------------
// ¿Dónde dejé la moto?
// ------------------------------------------------------------
@Composable
private fun DialogoMoto(lugar: Lugar?, punto: GeoPoint?, onVer: () -> Unit, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("¿Dónde dejé la moto?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (lugar == null) {
                    Text(
                        "Se guarda sola cuando apagas la moto (al desconectarse estando parada). " +
                            "También puedes guardar aquí mismo.",
                        color = Paleta.textoSec, fontSize = 14.sp,
                    )
                } else {
                    val dist = punto?.distanceToAsDouble(GeoPoint(lugar.lat, lugar.lon))
                    Text(
                        "Guardada " + haceCuanto(lugar.hora) + if (dist != null) " · a ${distanciaTexto(dist)}" else "",
                        color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    BotonDeportivo("Ver en el mapa", { onVer(); onCerrar() }, Modifier.fillMaxWidth())
                    BotonDeportivo("Llegar caminando", { Acciones.caminarA(ctx, lugar.lat, lugar.lon); onCerrar() }, Modifier.fillMaxWidth(), primario = false)
                }
                BotonDeportivo("Guardar aquí", {
                    if (!Estacionamiento.guardarAqui()) Toast.makeText(ctx, "Todavía no hay ubicación", Toast.LENGTH_SHORT).show()
                    onCerrar()
                }, Modifier.fillMaxWidth(), primario = lugar == null)
                BotonDeportivo("Compartir mi ubicación", {
                    if (!Acciones.compartirUbicacion(ctx)) Toast.makeText(ctx, "Todavía no hay ubicación", Toast.LENGTH_SHORT).show()
                    onCerrar()
                }, Modifier.fillMaxWidth(), primario = false)
                if (lugar != null) {
                    BotonDeportivo("Borrar", { Estacionamiento.borrar(); onCerrar() }, Modifier.fillMaxWidth(),
                        primario = false, color = Paleta.rojo)
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

private fun distanciaTexto(m: Double): String =
    if (m < 1000) "${m.roundToInt()} m" else "${fmt((m / 1000.0).toFloat(), 1)} km"

// ------------------------------------------------------------
// Lo esencial de la moto (versión compacta)
// ------------------------------------------------------------
@Composable
private fun MiniTablero(modifier: Modifier) {
    val r by Moto.rapida.enVivo()
    val e by Moto.estado.enVivo()
    val v by Moto.viaje.enVivo()
    var hora by remember { mutableStateOf(horaTexto()) }
    LaunchedEffect(Unit) {
        while (true) { hora = horaTexto(); delay(10_000) }
    }

    Column(
        modifier.panel().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CajaMarcha(r.marcha, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (r.rpm >= 0) "${r.rpm}" else "--", color = Paleta.texto, fontSize = 20.sp, fontFamily = Fuentes.logo)
                    Spacer(Modifier.width(4.dp))
                    EtiquetaPixel("rpm", tamano = 7.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    "${if (r.vMoto >= 0) fmt(r.vMoto, 1) + " V" else "-- V"}  ·  " +
                        "${if (r.gas >= 0) "${r.gas}%" else "--%"}  ·  " +
                        "${if (r.temp > -90) "${r.temp.toInt()}°C" else "--°C"}",
                    color = Paleta.textoSec, fontSize = 13.sp,
                )
            }
            Text(hora, color = Paleta.texto, fontSize = 22.sp, fontFamily = Fuentes.logo)
        }
        BarraShift(r.rpm, r.shift, Modifier.fillMaxWidth())
        if (e.alerta.isNotBlank()) {
            Text("⚠ ${e.alerta}", color = Paleta.rojo, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        } else if (v.autonomia >= 0) {
            Text("Autonomía ~${v.autonomia} km  ·  Viaje ${fmt(v.km, 1)} km", color = Paleta.textoSec, fontSize = 13.sp)
        }
    }
}

private fun horaTexto(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

// ------------------------------------------------------------
// Música
// ------------------------------------------------------------
/**
 * Música: la de este teléfono o la del iPhone (si está vinculado a la moto).
 * Si hay las dos, se elige con los botones de arriba.
 */
@Composable
private fun TarjetaMusica(modifier: Modifier, onAccesoNotif: () -> Unit) {
    val ip by Moto.iphoneMusica.collectAsStateWithLifecycle()
    val m by MusicaManager.musica.collectAsStateWithLifecycle()
    var elegida by rememberSaveable { mutableIntStateOf(-1) }   // -1 automático, 0 este teléfono, 1 iPhone
    val usarIphone = ip.on && (elegida == 1 || (elegida == -1 && (ip.titulo.isNotBlank() || ip.sonando || !m.reproduciendo)))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ip.on) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipOpcion("Este teléfono", !usarIphone) { elegida = 0 }
                ChipOpcion("iPhone", usarIphone) { elegida = 1 }
            }
        }
        if (usarIphone) TarjetaMusicaIphone(Modifier.fillMaxWidth(), ip)
        else TarjetaMusicaTelefono(Modifier.fillMaxWidth(), onAccesoNotif)
    }
}

/** Música del iPhone: llega por la moto (AMS) y se controla desde aquí. */
@Composable
private fun TarjetaMusicaIphone(modifier: Modifier, ip: IphoneMusica) {
    Column(
        modifier.panel().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(Formas.chica).background(Paleta.superficie2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Iconos.Nota, contentDescription = null, tint = Paleta.lima, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                EtiquetaPixel("iphone  " + ip.app.ifBlank { "musica" }, Paleta.lima, 7.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    ip.titulo.ifBlank { "Nada sonando" },
                    color = Paleta.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ip.artista.ifBlank { "Pon música en tu iPhone" },
                    color = Paleta.textoSec, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BarraTiempoIphone(ip)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotonMusica(Iconos.Anterior, 52) { Moto.iphoneMusicaCmd("ant") }
            BotonMusica(if (ip.sonando) Iconos.Pausa else Iconos.Play, 64, principal = true) { Moto.iphoneMusicaCmd("toggle") }
            BotonMusica(Iconos.Siguiente, 52) { Moto.iphoneMusicaCmd("sig") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Volumen del iPhone", color = Paleta.textoSec, fontSize = 12.sp, modifier = Modifier.weight(1f))
            ChipMusica("−") { Moto.iphoneMusicaCmd("vol-") }
            if (ip.volumen >= 0f) BarraVolumen((ip.volumen * 16).roundToInt(), 16)
            ChipMusica("+") { Moto.iphoneMusicaCmd("vol+") }
        }
    }
}

@Composable
private fun BarraTiempoIphone(ip: IphoneMusica) {
    if (ip.duracion <= 0f) return
    val visible = LocalPaginaVisible.current
    var ahora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(ip.sonando, ip.recibido, visible) {
        ahora = SystemClock.elapsedRealtime()
        while (ip.sonando && visible) { delay(500); ahora = SystemClock.elapsedRealtime() }
    }
    val segundos = (ip.transcurrido + if (ip.sonando) (ahora - ip.recibido) / 1000f else 0f).coerceIn(0f, ip.duracion)
    val acento = Paleta.lima
    val vacio = Paleta.superficie2
    Column {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val n = 32
            val hueco = 2.dp.toPx()
            val w = (size.width - hueco * (n - 1)) / n
            val llenos = (segundos / ip.duracion * n).toInt()
            for (i in 0 until n) drawRect(if (i < llenos) acento else vacio, Offset(i * (w + hueco), 0f), Size(w, size.height))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(tiempo((segundos * 1000).toLong()), color = Paleta.textoSec, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(tiempo((ip.duracion * 1000).toLong()), color = Paleta.textoSec, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TarjetaMusicaTelefono(modifier: Modifier, onAccesoNotif: () -> Unit) {
    val m by MusicaManager.musica.collectAsStateWithLifecycle()
    val vol by MusicaManager.volumen.collectAsStateWithLifecycle()
    var dialogo by remember { mutableIntStateOf(0) }   // 1 buscar, 2 cola, 3 apps, 4 audio
    LaunchedEffect(Unit) { MusicaManager.leerVolumen() }

    Column(
        modifier.panel().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilaAudio { dialogo = 4 }
        if (!m.permiso) {
            EtiquetaPixel("musica", Paleta.lima, 10.sp)
            Text(
                "Para controlar la música (y mandar tus notificaciones a la moto) " +
                    "Android pide el permiso de acceso a notificaciones.",
                color = Paleta.textoSec, fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonDeportivo("Dar acceso", onAccesoNotif, Modifier.weight(1f))
                BotonDeportivo("Buscar", { dialogo = 1 }, Modifier.weight(1f), primario = false)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Portada(m)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (m.app.isNotBlank()) EtiquetaPixel(m.app, Paleta.lima, 7.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (m.hayReproductor) m.titulo.ifBlank { "Sin título" } else "Nada sonando",
                        color = Paleta.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (m.hayReproductor) m.artista else "Toca BUSCAR o APPS para elegir música",
                        color = Paleta.textoSec, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            BarraProgreso(m)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotonMusica(Iconos.Anterior, 52) { MusicaManager.anterior() }
                BotonMusica(if (m.reproduciendo) Iconos.Pausa else Iconos.Play, 64, principal = true) { MusicaManager.playPausa() }
                BotonMusica(Iconos.Siguiente, 52) { MusicaManager.siguiente() }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipMusica("Buscar") { dialogo = 1 }
                if (m.cola.isNotEmpty()) ChipMusica("Cola") { dialogo = 2 }
                ChipMusica("Apps") { dialogo = 3 }
                Spacer(Modifier.weight(1f))
                ChipMusica("−") { MusicaManager.volumen(false) }
                BarraVolumen(vol.first, vol.second)
                ChipMusica("+") { MusicaManager.volumen(true) }
            }
        }
    }

    when (dialogo) {
        1 -> DialogoBuscar(m) { dialogo = 0 }
        2 -> DialogoCola(m) { dialogo = 0 }
        3 -> DialogoApps(m) { dialogo = 0 }
        4 -> DialogoAudio(m.paquete.ifBlank { null }) { dialogo = 0 }
    }
}

@Composable
private fun Portada(m: Musica) {
    val portada = m.portada
    val imagen = remember(portada) { portada?.asImageBitmap() }
    Box(
        Modifier.size(64.dp).clip(Formas.chica).background(Paleta.superficie2),
        contentAlignment = Alignment.Center,
    ) {
        if (imagen != null) {
            Image(bitmap = imagen, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Iconos.Nota, contentDescription = null, tint = Paleta.lima, modifier = Modifier.size(30.dp))
        }
    }
}

/** Barra de la canción en bloques pixel. Tócala para adelantar o regresar. */
@Composable
private fun BarraProgreso(m: Musica) {
    if (m.posicion < 0 || m.duracion <= 0) return    // la app no dice el tiempo
    val visible = LocalPaginaVisible.current
    var ahora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(m.reproduciendo, m.marcaTiempo, visible) {
        ahora = SystemClock.elapsedRealtime()
        // Solo avanza mientras suena y la página está en pantalla
        while (m.reproduciendo && visible) {
            delay(500)
            ahora = SystemClock.elapsedRealtime()
        }
    }
    val pos = m.posicionAhora(ahora)
    val acento = Paleta.lima
    val vacio = Paleta.superficie2
    val duracion = m.duracion
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(14.dp).then(
                if (m.puedeAdelantar) Modifier.pointerInput(duracion) {
                    detectTapGestures { o -> MusicaManager.adelantar((o.x / size.width * duracion).toLong()) }
                } else Modifier
            )
        ) {
            val n = 32
            val hueco = 2.dp.toPx()
            val w = (size.width - hueco * (n - 1)) / n
            val llenos = (pos.toFloat() / duracion * n).toInt()
            for (i in 0 until n) {
                drawRect(if (i < llenos) acento else vacio, Offset(i * (w + hueco), 0f), Size(w, size.height))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(tiempo(pos), color = Paleta.textoSec, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(tiempo(duracion), color = Paleta.textoSec, fontSize = 11.sp)
        }
    }
}

private fun tiempo(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
private fun BarraVolumen(actual: Int, maximo: Int) {
    val acento = Paleta.lima
    val vacio = Paleta.superficie2
    Canvas(Modifier.width(34.dp).height(14.dp)) {
        val n = 6
        val hueco = 1.5.dp.toPx()
        val w = (size.width - hueco * (n - 1)) / n
        val llenos = if (maximo > 0) ((actual.toFloat() / maximo) * n + 0.5f).toInt() else 0
        for (i in 0 until n) {
            val h = size.height * (0.35f + 0.65f * i / (n - 1))
            drawRect(if (i < llenos) acento else vacio, Offset(i * (w + hueco), size.height - h), Size(w, h))
        }
    }
}

@Composable
private fun ChipMusica(texto: String, onClick: () -> Unit) {
    val forma = Formas.chip
    Box(
        Modifier.heightIn(min = 36.dp).clip(forma).background(Paleta.superficie2)
            .border(1.dp, Paleta.borde, forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto.uppercase(), color = Paleta.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BotonMusica(
    icono: ImageVector,
    tamano: Int,
    principal: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(tamano.dp).clip(Formas.boton)
            .background(if (principal) Paleta.lima else Paleta.superficie2)
            .border(1.dp, if (principal) Paleta.lima else Paleta.borde, Formas.boton)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icono, contentDescription = null,
            tint = if (principal) Color.Black else Paleta.texto,
            modifier = Modifier.size((tamano * 0.5f).dp),
        )
    }
}

/** Buscar y reproducir: escribe o di la canción, artista o playlist, y elige en qué app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoBuscar(m: Musica, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var texto by remember { mutableStateOf("") }
    val apps = remember { MusicaManager.appsInstaladas() }
    var app by remember { mutableStateOf(m.paquete.ifBlank { apps.firstOrNull()?.paquete ?: "" }) }

    fun reproducir(q: String) {
        if (q.isBlank()) return
        if (MusicaManager.buscar(q, app.ifBlank { null })) onCerrar()
        else Toast.makeText(ctx, "No encontré una app que pueda buscar música", Toast.LENGTH_SHORT).show()
    }

    val dictado = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val dicho = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (dicho.isNotBlank()) { texto = dicho; reproducir(dicho) }
        }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("¿Qué quieres escuchar?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    singleLine = true,
                    label = { Text("Canción, artista o playlist") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { reproducir(texto) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                BotonDeportivo("Decirlo por voz", {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué quieres escuchar?")
                    try { dictado.launch(i) } catch (_: ActivityNotFoundException) {
                        Toast.makeText(ctx, "Este teléfono no tiene dictado por voz", Toast.LENGTH_SHORT).show()
                    }
                }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 13.sp)
                if (apps.isNotEmpty()) {
                    Text("Reproducir en:", color = Paleta.textoSec, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        apps.forEach { a -> ChipOpcion(a.nombre, a.paquete == app) { app = a.paquete } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { reproducir(texto) }) { Text("REPRODUCIR", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

/** Lista de lo que sigue (si la app de música la comparte). */
@Composable
private fun DialogoCola(m: Musica, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Cola de reproducción", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                m.cola.forEach { e ->
                    val actual = e.id == m.enCola
                    Row(
                        Modifier.fillMaxWidth().clip(Formas.chica)
                            .background(if (actual) Paleta.lima.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { MusicaManager.irACancion(e.id); onCerrar() }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.titulo.ifBlank { "Sin título" }, color = if (actual) Paleta.lima else Paleta.texto,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (e.subtitulo.isNotBlank()) {
                                Text(e.subtitulo, color = Paleta.textoSec, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (actual) EtiquetaPixel("ahora", Paleta.lima, 6.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

/** Elegir qué app controlar (si hay varias abiertas) o abrir otra app de música. */
@Composable
private fun DialogoApps(m: Musica, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val abiertas by MusicaManager.reproductores.collectAsStateWithLifecycle()
    val instaladas = remember { MusicaManager.appsInstaladas() }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Apps de música", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (abiertas.isNotEmpty()) {
                    EtiquetaPixel("controlar", Paleta.lima, 7.sp)
                    abiertas.forEach { r ->
                        FilaApp(r.nombre, if (r.sonando) "Sonando" else "En pausa", r.paquete == m.paquete) {
                            MusicaManager.elegirReproductor(r.paquete); onCerrar()
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                EtiquetaPixel("abrir", Paleta.lima, 7.sp)
                if (instaladas.isEmpty()) {
                    Text("No encontré apps de música instaladas.", color = Paleta.textoSec, fontSize = 13.sp)
                }
                instaladas.forEach { r ->
                    FilaApp(r.nombre, "", false) {
                        if (!MusicaManager.abrirApp(r.paquete)) Toast.makeText(ctx, "No se pudo abrir ${r.nombre}", Toast.LENGTH_SHORT).show()
                        onCerrar()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

@Composable
private fun FilaApp(nombre: String, detalle: String, elegida: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Formas.chica)
            .background(if (elegida) Paleta.lima.copy(alpha = 0.14f) else Paleta.superficie2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(nombre, color = if (elegida) Paleta.lima else Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        if (detalle.isNotEmpty()) Text(detalle, color = Paleta.textoSec, fontSize = 12.sp)
    }
}

@Composable
private fun ChipOpcion(texto: String, elegido: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(Formas.chip)
            .background(if (elegido) Paleta.lima else Paleta.superficie2)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(texto, color = if (elegido) Color.Black else Paleta.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------
// Audio Bluetooth (audífonos / casco con intercomunicador)
// ------------------------------------------------------------
@Composable
private fun FilaAudio(onClick: () -> Unit) {
    val disp by AudioSalida.dispositivos.collectAsStateWithLifecycle()
    val salida by AudioSalida.salida.collectAsStateWithLifecycle()
    val bateria = disp.firstOrNull { it.bateria >= 0 }?.bateria
    Row(
        Modifier.fillMaxWidth().clip(Formas.chip).background(Paleta.superficie2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Iconos.Auriculares, contentDescription = null, tint = Paleta.lima, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(salida, color = Paleta.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (bateria != null) {
            BateriaPixel(bateria)
            Spacer(Modifier.width(8.dp))
        }
        EtiquetaPixel("cambiar", Paleta.lima, 6.sp)
    }
}

@Composable
private fun DialogoAudio(paqueteMusica: String?, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val disp by AudioSalida.dispositivos.collectAsStateWithLifecycle()
    val salida by AudioSalida.salida.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AudioSalida.refrescar() }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Audio y Bluetooth", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Dato("Sonando en", salida)
                if (disp.isEmpty()) {
                    Text("No hay audífonos ni casco Bluetooth conectados.", color = Paleta.textoSec, fontSize = 13.sp)
                }
                disp.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clip(Formas.chica).background(Paleta.superficie2).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Iconos.Auriculares, contentDescription = null, tint = Paleta.lima, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(d.nombre, color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (d.bateria >= 0) BateriaPixel(d.bateria)
                        else Text("sin dato", color = Paleta.textoSec, fontSize = 12.sp)
                    }
                }
                BotonDeportivo("Cambiar salida de audio", {
                    if (!AudioSalida.abrirSelectorSalida(paqueteMusica)) {
                        Toast.makeText(ctx, "No se pudo abrir el selector de audio", Toast.LENGTH_SHORT).show()
                    }
                    onCerrar()
                }, Modifier.fillMaxWidth(), tamanoTexto = 13.sp)
                BotonDeportivo("Ajustes de Bluetooth", { AudioSalida.abrirAjustesBluetooth(); onCerrar() },
                    Modifier.fillMaxWidth(), primario = false, tamanoTexto = 13.sp)
                Text(
                    "La batería aparece si tu casco o audífonos la mandan al teléfono (los que tienen manos libres casi siempre lo hacen).",
                    color = Paleta.textoSec, fontSize = 11.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

// ------------------------------------------------------------
// Límite de velocidad y calle
// ------------------------------------------------------------
@Composable
private fun NombreCalle(modifier: Modifier) {
    val mostrar by Moto.mostrarLimite.collectAsStateWithLifecycle()
    val l by LimitesManager.limite.collectAsStateWithLifecycle()
    val lim = l ?: return
    if (!mostrar) return
    Text(
        lim.nombre.ifBlank { LimitesManager.nombreTipo(lim.tipo) },
        color = Paleta.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.widthIn(max = 180.dp).clip(Formas.chip).background(Paleta.fondo.copy(alpha = 0.82f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Señal redonda con el límite de la calle. Parpadea si vas más rápido. */
@Composable
private fun SenalLimite() {
    val mostrar by Moto.mostrarLimite.collectAsStateWithLifecycle()
    if (!mostrar) return
    val l by LimitesManager.limite.collectAsStateWithLifecycle()
    val rS = Moto.rapida.enVivo()
    val uS = UbicacionManager.ubicacion.enVivo()
    val lim = l
    val excedido by remember(lim) {
        derivedStateOf {
            if (lim == null) false
            else {
                val r = rS.value
                val u = uS.value
                val v = if (r.vel >= 0) r.vel else if (u != null && UbicacionManager.edadMs() < 3_000 && u.hasSpeed()) u.speed * 3.6f else -1f
                v > lim.kmh + 2
            }
        }
    }
    val alfaS = parpadeoEstado(excedido)
    val rojo = Paleta.rojo
    if (lim == null) return      // sin dato de la calle no estorba
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).drawBehind {
                val r = size.minDimension / 2f
                drawCircle(Color.White, r)
                drawCircle(rojo, r - r * 0.09f, style = Stroke(r * 0.18f))
                if (excedido) drawCircle(rojo.copy(alpha = 0.55f * (1f - alfaS.value)), r * 0.78f)
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(lim.kmh.toString(), color = Color.Black, fontSize = if (lim.kmh >= 100) 18.sp else 21.sp,
                fontFamily = Fuentes.logo)
        }
        if (lim.estimado) {
            Spacer(Modifier.height(2.dp))
            EtiquetaPixel("aprox", Paleta.textoSec, 6.sp, Modifier.background(Paleta.fondo.copy(alpha = 0.7f)).padding(2.dp))
        }
    }
}

// ------------------------------------------------------------
// Micrófono: toca para un comando, mantén para el Asistente de Google
// ------------------------------------------------------------
@Composable
private fun BotonMicrofono() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val micBt by Moto.micBt.collectAsStateWithLifecycle()
    var escuchando by remember { mutableStateOf(false) }
    val dictado = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        AudioSalida.apagarMicBt()
        escuchando = false
        if (res.resultCode == Activity.RESULT_OK) {
            val dicho = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (dicho.isNotBlank()) ComandosVoz.ejecutar(ctx, dicho)
        }
    }
    val forma = Formas.chica
    Box(
        Modifier.size(44.dp).clip(forma)
            .background(if (escuchando) Paleta.lima else Paleta.fondo.copy(alpha = 0.85f))
            .border(1.dp, Paleta.lima.copy(alpha = 0.6f), forma)
            .pointerInput(micBt) {
                detectTapGestures(
                    onTap = {
                        escuchando = true
                        scope.launch {
                            // con el micrófono del casco: se enciende y se espera a que conecte
                            if (micBt && AudioSalida.encenderMicBt()) delay(900)
                            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Di un comando: pon…, clima, llévame a…")
                            try {
                                dictado.launch(i)
                            } catch (_: ActivityNotFoundException) {
                                escuchando = false
                                AudioSalida.apagarMicBt()
                                Toast.makeText(ctx, "Este teléfono no tiene dictado por voz", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onLongPress = {
                        scope.launch {
                            if (micBt && AudioSalida.encenderMicBt()) delay(900)
                            if (!Acciones.abrirAsistente(ctx)) {
                                AudioSalida.apagarMicBt()
                                Toast.makeText(ctx, "No encontré el Asistente de Google", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Iconos.Microfono, contentDescription = "Voz", tint = if (escuchando) Color.Black else Paleta.lima,
            modifier = Modifier.size(22.dp))
    }
}

// ------------------------------------------------------------
// Manzanita: abre la app que elijas (toca); mantén para cambiarla
// ------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BotonManzana(rectMapa: android.graphics.Rect?) {
    val ctx = LocalContext.current
    val ventanaOn by Root.ventanaManzana.collectAsStateWithLifecycle()
    val rootActivo by Root.activo.collectAsStateWithLifecycle()
    val ventana = ventanaOn && rootActivo
    var elegir by remember { mutableStateOf(false) }
    val forma = Formas.chica
    Box(
        Modifier.size(44.dp).clip(forma).background(Paleta.fondo.copy(alpha = 0.85f))
            .border(1.dp, Paleta.lima.copy(alpha = 0.6f), forma)
            .combinedClickable(
                onClick = {
                    val pk = Lanzador.appManzana.value
                    when {
                        pk.isBlank() -> elegir = true
                        // root: ventana flotante encima del mapa
                        ventana && rectMapa != null -> if (!Lanzador.abrirEnVentana(ctx, pk, rectMapa)) Lanzador.abrirManzana(ctx)
                        !Lanzador.abrirManzana(ctx) -> elegir = true
                    }
                },
                onLongClick = { elegir = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Iconos.Manzana, contentDescription = "Abrir app", tint = Paleta.lima, modifier = Modifier.size(22.dp))
    }
    if (elegir) {
        DialogoElegirApp(
            "¿Qué app abre la manzanita?",
            onElegir = { a -> Lanzador.ponerManzana(ctx, a.paquete); elegir = false; Lanzador.abrir(ctx, a) },
            onCerrar = { elegir = false },
        )
    }
}


/** Cuadritos de los baches en el mapa: ámbar = bache, rojo = bache fuerte. */
private class CapaBaches(densidad: Float) : Overlay() {
    private var puntos: List<GeoPoint> = emptyList()
    private var fuertes: BooleanArray = BooleanArray(0)
    private val lado = 5f * densidad
    private val ambar = Paint().apply { color = 0xFFFFB300.toInt(); style = Paint.Style.FILL }
    private val rojo = Paint().apply { color = 0xFFFF3B3B.toInt(); style = Paint.Style.FILL }
    private val borde = Paint().apply { color = 0xFF000000.toInt(); style = Paint.Style.STROKE; strokeWidth = densidad }
    private val p = android.graphics.Point()

    fun poner(l: List<Bache>, umbralFuerte: Float) {
        puntos = l.map { GeoPoint(it.lat, it.lon) }
        fuertes = BooleanArray(l.size) { l[it].fuerza > umbralFuerte }
    }

    override fun draw(c: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || puntos.isEmpty() || mapView.zoomLevelDouble < 13.0) return
        val pr = mapView.projection
        for (i in puntos.indices) {
            pr.toPixels(puntos[i], p)
            val x = p.x.toFloat(); val y = p.y.toFloat()
            c.drawRect(x - lado, y - lado, x + lado, y + lado, if (fuertes[i]) rojo else ambar)
            c.drawRect(x - lado, y - lado, x + lado, y + lado, borde)
        }
    }
}


/** Brújula pixel: "N" donde está el norte en la pantalla y flecha hacia donde mira la moto. */
@Composable
private fun BotonBrujula(rumboDe: () -> Float, giroDe: () -> Float, rumboArriba: Boolean, onClick: () -> Unit) {
    val acento = Paleta.lima
    val texto = Paleta.texto
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Paleta.fondo.copy(alpha = 0.88f))
            .border(1.5.dp, if (rumboArriba) acento else Paleta.borde, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Los ángulos se leen al dibujar: gira a 60 cuadros sin recomponer el botón
        Canvas(Modifier.size(36.dp)) {
            val giroMapa = giroDe()
            val rumbo = rumboDe().takeIf { it >= 0f }
            val c = Offset(size.width / 2, size.height / 2)
            val radio = size.minDimension / 2
            val cel = radio / 6f
            // N: en el ángulo del norte en la pantalla (si el mapa gira, el norte se mueve)
            val aN = Math.toRadians(giroMapa.toDouble())
            val nx = c.x + (radio - cel * 1.5f) * kotlin.math.sin(aN).toFloat()
            val ny = c.y - (radio - cel * 1.5f) * kotlin.math.cos(aN).toFloat()
            drawRect(Paleta.rojo, Offset(nx - cel, ny - cel), Size(cel * 2, cel * 2))
            // Flecha de la moto
            if (rumbo != null) {
                val a = Math.toRadians((rumbo + giroMapa).toDouble())
                val dx = kotlin.math.sin(a).toFloat()
                val dy = -kotlin.math.cos(a).toFloat()
                for (k in -3..3) {
                    val px = c.x + dx * k * cel
                    val py = c.y + dy * k * cel
                    drawRect(if (k > 0) acento else texto.copy(alpha = 0.5f), Offset(px - cel * 0.6f, py - cel * 0.6f), Size(cel * 1.2f, cel * 1.2f))
                }
            } else {
                drawRect(texto.copy(alpha = 0.5f), Offset(c.x - cel * 0.6f, c.y - cel * 0.6f), Size(cel * 1.2f, cel * 1.2f))
            }
        }
    }
}

/** Chip con la señal del GPS: satélites y precisión. Tócalo para ver el detalle. */
@Composable
private fun ChipGps() {
    val g by UbicacionManager.estado.enVivo()
    var abierto by remember { mutableStateOf(false) }
    val color = when {
        !g.conSenal && (g.aprox || g.buscando) -> Paleta.ambar
        !g.conSenal -> Paleta.rojo
        g.precision in 0f..8f -> Paleta.verde
        g.precision in 0f..20f -> Paleta.ambar
        else -> Paleta.rojo
    }
    val forma = Formas.chip
    Row(
        Modifier.clip(forma).background(Paleta.fondo.copy(alpha = 0.82f)).border(1.dp, color.copy(alpha = 0.7f), forma)
            .clickable { abierto = true }.padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtiquetaPixel("gps", color, 6.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            when {
                g.conSenal -> "${g.usados} sat" + if (g.precision >= 0) " ±${g.precision.roundToInt()} m" else ""
                g.buscando && g.senalDebil -> "señal débil · ${g.fuertes}/${g.visibles}"
                g.buscando -> "buscando · ${g.fuertes} buenos"
                else -> "sin señal"
            } + if (!g.conSenal && g.aprox) " · aprox" else "",
            color = Paleta.texto, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        )
    }
    if (abierto) DialogoGps { abierto = false }
}

/** Detalle del GPS, casa y compartir. */
@Composable
private fun DialogoGps(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val g by UbicacionManager.estado.collectAsStateWithLifecycle()
    val u by UbicacionManager.ubicacion.collectAsStateWithLifecycle()
    val ap by UbicacionManager.aprox.collectAsStateWithLifecycle()
    val casa by UbicacionManager.casa.collectAsStateWithLifecycle()
    val brujula by Brujula.rumbo.collectAsStateWithLifecycle()
    val calidad by Brujula.calidad.collectAsStateWithLifecycle()
    val l = if (g.aprox && ap != null) ap else u
    val aCasa = casa?.let { c -> l?.let { Baches.distanciaM(it.latitude, it.longitude, c.first, c.second) } }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { EtiquetaPixel("gps del telefono", Paleta.lima, 9.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Dato("Señal", when {
                    g.conSenal -> "Sí"
                    g.atorado -> "Sin entregar"
                    g.buscando && g.senalDebil -> "Muy débil (bajo techo)"
                    g.buscando -> "Buscando satélites…"
                    else -> "Perdida hace ${g.edadS} s"
                }, if (g.conSenal) Paleta.verde else if (g.atorado) Paleta.rojo else Paleta.ambar)
                if (!g.conSenal) {
                    Dato("Mapa", if (g.aprox) "Aproximado (wifi/antenas)" + if (g.precisionAprox >= 0) " ±${g.precisionAprox.roundToInt()} m" else ""
                        else "Última posición del GPS", Paleta.ambar)
                }
                Dato("Con buena señal", "${g.fuertes} de ${g.visibles}", if (g.fuertes >= 4) Paleta.texto else Paleta.ambar)
                Dato("Marcados en uso", "${g.usados}")
                Dato("Señal más fuerte", if (g.mejorSenal > 0f) "${g.mejorSenal.roundToInt()} dB-Hz" else "--",
                    if (g.mejorSenal >= 30f) Paleta.texto else Paleta.ambar)
                Dato("Precisión", if (g.precision >= 0) "±${g.precision.roundToInt()} m" else "--")
                Dato("Doble frecuencia (L5)", if (g.dobleFrecuencia) "Sí (más preciso)" else "No por ahora")
                if (g.constelaciones.isNotBlank()) Dato("Sistemas", g.constelaciones)
                if (l != null) Dato("Coordenadas", String.format(java.util.Locale.US, "%.5f, %.5f", l.latitude, l.longitude))
                Dato("Brújula", if (brujula >= 0) "${brujula.roundToInt()}°" + if (calidad < 2) " (mueve el teléfono en 8)" else "" else "--",
                    if (calidad < 2) Paleta.ambar else Paleta.texto)
                Dato("Casa", if (casa == null) "Sin guardar" else if (aCasa != null) "a ${fmtDist(aCasa)}" else "Guardada")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonDeportivo("Aquí es mi casa", {
                        if (UbicacionManager.guardarCasa()) android.widget.Toast.makeText(ctx, "Casa guardada", android.widget.Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f), primario = false, tamanoTexto = 11.sp)
                    BotonDeportivo("Llévame a casa", {
                        val c = casa
                        if (c != null) Acciones.navegarA(ctx, String.format(java.util.Locale.US, "%.6f,%.6f", c.first, c.second), guardar = false)
                        else android.widget.Toast.makeText(ctx, "Primero guarda tu casa", android.widget.Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f), tamanoTexto = 11.sp)
                }
                BotonDeportivo("Compartir mi ubicación", { Acciones.compartirUbicacion(ctx) }, Modifier.fillMaxWidth(),
                    primario = false, tamanoTexto = 11.sp)
                Text(when {
                    g.atorado -> "El chip del GPS ya calculó tu posición pero Android no se la está pasando a la app (la app ya " +
                        "volvió a pedirla). Revisa en los ajustes del teléfono: Apps > FT150TS > Ahorro de batería = Sin " +
                        "restricciones, y Ubicación > Ubicación precisa encendida."
                    !g.conSenal && g.senalDebil -> "Es normal bajo techo: el techo y las paredes tapan los satélites. \"Marcados " +
                        "en uso\" engaña: el chip los está siguiendo, pero con menos de 4 con buena señal (25 dB-Hz o más) no " +
                        "le alcanza para dar posición. Afuera o junto a una ventana, en 1 o 2 minutos cambia a \"N sat ±X m\". " +
                        "Mientras, el mapa usa la ubicación aproximada y la velocidad marca \"--\"."
                    else -> "Adentro de la casa o bajo techo la primera señal puede tardar de 1 a 2 minutos (cerca de una ventana " +
                        "o afuera, segundos); mientras, el mapa usa la ubicación aproximada. Sin señal, la velocidad marca \"--\" " +
                        "en vez de quedarse congelada."
                }, color = Paleta.textoSec, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

private fun fmtDist(m: Double): String = if (m < 1000) "${m.roundToInt()} m" else String.format(java.util.Locale.US, "%.1f km", m / 1000)
