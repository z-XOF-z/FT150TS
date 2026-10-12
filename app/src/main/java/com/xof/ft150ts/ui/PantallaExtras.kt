package com.xof.ft150ts.ui

import android.graphics.drawable.GradientDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Arrancones
import com.xof.ft150ts.FaseArr
import com.xof.ft150ts.Gpx
import com.xof.ft150ts.Logros
import com.xof.ft150ts.LluviaRuta
import com.xof.ft150ts.Mapas
import com.xof.ft150ts.Moto
import com.xof.ft150ts.PruebaArr
import com.xof.ft150ts.PuntoRodada
import com.xof.ft150ts.Respaldo
import com.xof.ft150ts.Rodada
import com.xof.ft150ts.Rodadas
import com.xof.ft150ts.Velocimetro
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val FECHA = SimpleDateFormat("EEE d MMM · HH:mm", Locale("es", "MX"))
private fun fecha(ms: Long) = FECHA.format(Date(ms)).replaceFirstChar { it.uppercase() }
private fun duracion(min: Int) = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

/** Colores del recorrido por velocidad (fijos: no dependen del acento). */
private val COLORES_VEL = listOf(Color(0xFF39FF14), Color(0xFFFFE600), Color(0xFFFFB020), Color(0xFFFF3B3B))
private fun rangoVel(v: Float) = when { v < 25f -> 0; v < 50f -> 1; v < 75f -> 2; else -> 3 }

// ============================================================
// Rodadas: lista y repetición en el mapa
// ============================================================

@Composable
fun SeccionRodadas() {
    val lista by Rodadas.lista.collectAsStateWithLifecycle()
    val enCurso by Rodadas.enCurso.collectAsStateWithLifecycle()
    val aviso by Rodadas.avisoDescanso.collectAsStateWithLifecycle()
    val resumen by Rodadas.resumenVoz.collectAsStateWithLifecycle()
    var verTodas by remember { mutableStateOf(false) }
    var abierta by remember { mutableStateOf<Rodada?>(null) }
    Seccion("Rodadas") {
        enCurso?.let { r ->
            Dato("Grabando ahora", "${fmt(r.km, 1)} km · ${duracion(r.minutos)} · máx ${r.vmax.roundToInt()}", Paleta.verde)
        }
        if (lista.isEmpty()) {
            Text("Todavía no hay rodadas. Se graban solas cuando ruedas con el teléfono en la moto y se cierran a los 5 min " +
                "de estar parado. Luego puedes ver cada una en el mapa y repetirla.", color = Paleta.textoSec, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tarjeta("Rodadas", "${lista.size}", Modifier.weight(1f), compacta = true)
                Tarjeta("Total", fmt(lista.sumOf { it.km.toDouble() }.toFloat(), 0), Modifier.weight(1f), unidad = "km", compacta = true)
                Tarjeta("Rodando", "${lista.sumOf { it.minMov } / 60}", Modifier.weight(1f), unidad = "h", compacta = true)
            }
            (if (verTodas) lista else lista.take(4)).forEach { r -> FilaRodada(r) { abierta = r } }
            if (lista.size > 4) {
                TextButton(onClick = { verTodas = !verTodas }) {
                    Text(if (verTodas) "Ver menos" else "Ver todas (${lista.size})", color = Paleta.lima)
                }
            }
        }
        FilaSwitch("Aviso de descanso", "Si llevas 1.5 h rodando sin parar, te sugiere una pausa (por voz)", aviso) {
            Rodadas.ponerAvisoDescanso(it)
        }
        FilaSwitch("Resumen al apagar la moto",
            "Al apagar el switch con la moto parada, la rodada se cierra en ese momento y te dice por voz los km, " +
                "el tiempo y la máxima (sin el cable de la moto se sigue cerrando a los 5 min parada)", resumen) {
            Rodadas.ponerResumenVoz(it)
        }
    }
    abierta?.let { DialogoRodada(it) { abierta = null } }
}

@Composable
private fun FilaRodada(r: Rodada, onClick: () -> Unit) {
    val forma = Formas.chica
    Row(
        Modifier.fillMaxWidth().clip(forma).background(Paleta.superficie2).border(1.dp, Paleta.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoPixel(PIXEL_MAPA, Paleta.lima, Modifier.size(width = 15.dp, height = 13.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(fecha(r.id), color = Paleta.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${duracion(r.minutos)} · máx ${r.vmax.roundToInt()} km/h · ${r.inclMax.roundToInt()}°", color = Paleta.textoSec, fontSize = 11.sp)
        }
        Text(fmt(r.km, 1), color = Paleta.lima, fontSize = 18.sp, fontFamily = Fuentes.logo)
        Text(" km", color = Paleta.textoSec, fontSize = 11.sp)
    }
}

/** Posición interpolada en el tiempo [ms] de la rodada. */
private fun puntoEn(p: List<PuntoRodada>, ms: Float): PuntoRodada? {
    if (p.isEmpty()) return null
    if (ms <= p.first().t) return p.first()
    if (ms >= p.last().t) return p.last()
    var lo = 0
    var hi = p.size - 1
    while (hi - lo > 1) { val m = (lo + hi) / 2; if (p[m].t <= ms) lo = m else hi = m }
    val a = p[lo]; val b = p[hi]
    val f = ((ms - a.t) / (b.t - a.t).coerceAtLeast(1L)).coerceIn(0f, 1f)
    return PuntoRodada(ms.toLong(), a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f, a.vel + (b.vel - a.vel) * f, a.incl + (b.incl - a.incl) * f)
}

@Composable
private fun DialogoRodada(r: Rodada, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var puntos by remember { mutableStateOf<List<PuntoRodada>>(emptyList()) }
    LaunchedEffect(r.id) { puntos = Rodadas.cargarPuntos(r.id) }
    val t = remember { mutableFloatStateOf(0f) }
    var reproduciendo by remember { mutableStateOf(false) }
    var factor by remember { mutableIntStateOf(1) }
    var borrar by remember { mutableStateOf(false) }
    val duracionMs = puntos.lastOrNull()?.t?.toFloat() ?: 0f
    // Reproducción: avanza con los cuadros de la pantalla (x10, x30 o x60)
    LaunchedEffect(reproduciendo, factor, duracionMs) {
        if (!reproduciendo || duracionMs <= 0f) return@LaunchedEffect
        if (t.floatValue >= duracionMs) t.floatValue = 0f
        var antes = 0L
        while (reproduciendo) {
            val ahora = withFrameNanos { it }
            if (antes != 0L) {
                val mult = listOf(10, 30, 60)[factor]
                t.floatValue = (t.floatValue + (ahora - antes) / 1e6f * mult).coerceAtMost(duracionMs)
                if (t.floatValue >= duracionMs) reproduciendo = false
            }
            antes = ahora
        }
    }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Paleta.fondo).padding(10.dp)) {
            val ancho = maxWidth > maxHeight
            val panel: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EtiquetaPixel(fecha(r.id), Paleta.lima, 8.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tarjeta("Distancia", fmt(r.km, 1), Modifier.weight(1f), unidad = "km", compacta = true)
                        Tarjeta("Tiempo", duracion(r.minutos), Modifier.weight(1f), compacta = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tarjeta("Vel. máx", "${r.vmax.roundToInt()}", Modifier.weight(1f), unidad = "km/h", compacta = true)
                        Tarjeta("Promedio", "${r.vprom.roundToInt()}", Modifier.weight(1f), unidad = "km/h", compacta = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tarjeta("En movimiento", duracion(r.minMov), Modifier.weight(1f), compacta = true)
                        Tarjeta("Inclinación", "${r.inclMax.roundToInt()}", Modifier.weight(1f), unidad = "°", compacta = true)
                    }
                    val p = puntoEn(puntos, t.floatValue)
                    if (p != null) {
                        Dato("En la repetición", "${p.vel.roundToInt()} km/h · ${p.incl.roundToInt()}° · ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(r.id + p.t))}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BotonDeportivo(if (reproduciendo) "Pausa" else "Repetir", { reproduciendo = !reproduciendo }, Modifier.weight(1f), tamanoTexto = 13.sp)
                        Selector(listOf("x10", "x30", "x60"), factor, { factor = it }, Modifier.weight(1.3f))
                    }
                    Slider(
                        t.floatValue, { t.floatValue = it; reproduciendo = false }, valueRange = 0f..duracionMs.coerceAtLeast(1f),
                        colors = SliderDefaults.colors(thumbColor = Paleta.lima, activeTrackColor = Paleta.lima, inactiveTrackColor = Paleta.superficie2),
                    )
                    // leyenda de colores
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("<25", "25-50", "50-75", ">75").forEachIndexed { i, txt ->
                            Box(Modifier.size(10.dp).background(COLORES_VEL[i]))
                            Text(txt, color = Paleta.textoSec, fontSize = 10.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonDeportivo("GPX", {
                            Gpx.compartirLista(ctx, "FT150TS_rodada_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(r.id)),
                                puntos.map { GeoPoint(it.lat, it.lon) })
                        }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                        BotonDeportivo("Borrar", { borrar = true }, Modifier.weight(1f), primario = false, color = Paleta.rojo, tamanoTexto = 12.sp)
                        BotonDeportivo("Cerrar", onCerrar, Modifier.weight(1f), tamanoTexto = 12.sp)
                    }
                }
            }
            if (ancho) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().panel()) { MapaRodada(puntos, t) }
                    panel(Modifier.width(280.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).panel()) { MapaRodada(puntos, t) }
                    panel(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
    if (borrar) {
        AlertDialog(
            onDismissRequest = { borrar = false },
            title = { Text("¿Borrar esta rodada?") },
            text = { Text("${fecha(r.id)} · ${fmt(r.km, 1)} km") },
            confirmButton = { TextButton(onClick = { Rodadas.borrar(r.id); borrar = false; onCerrar() }) { Text("Borrar", color = Paleta.rojo) } },
            dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar") } },
        )
    }
}

/** Mapa de la rodada: el recorrido pintado por velocidad y un rombo que se mueve con la repetición. */
@Composable
private fun MapaRodada(puntos: List<PuntoRodada>, t: State<Float>) {
    val ctx = LocalContext.current
    val acento = Paleta.lima
    val mapa = remember {
        crearMapa(ctx).apply {
            setMultiTouchControls(true)
            val estilo = Moto.estiloMapa.value
            setTileSource(Mapas.fuente(estilo))
            overlayManager.tilesOverlay.setColorFilter(Mapas.filtro(estilo))
        }
    }
    val marcador = remember {
        Marker(mapa).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setStroke(5, 0xFF000000.toInt()); setSize(36, 36); setColor(acento.toArgb()) }
            rotation = 45f
            setOnMarkerClickListener { _, _ -> true }
        }
    }
    DisposableEffect(mapa) { onDispose { mapa.onPause(); mapa.onDetach() } }
    LaunchedEffect(puntos) {
        if (puntos.size < 2) return@LaunchedEffect
        mapa.overlays.clear()
        // tramos del mismo color de velocidad en una sola línea
        var tramo = ArrayList<GeoPoint>()
        var rango = rangoVel(puntos[0].vel)
        for ((i, p) in puntos.withIndex()) {
            val g = GeoPoint(p.lat, p.lon)
            tramo.add(g)
            val r = rangoVel(p.vel)
            if (r != rango || i == puntos.size - 1) {
                val color = COLORES_VEL[rango]
                val pts = tramo
                mapa.overlays.add(Polyline(mapa).apply {
                    outlinePaint.color = color.toArgb()
                    outlinePaint.strokeWidth = 11f
                    setPoints(pts)
                })
                tramo = arrayListOf(g)
                rango = r
            }
        }
        marcador.position = GeoPoint(puntos[0].lat, puntos[0].lon)
        mapa.overlays.add(marcador)
        val caja = BoundingBox.fromGeoPoints(puntos.map { GeoPoint(it.lat, it.lon) })
        if (mapa.width > 0) mapa.zoomToBoundingBox(caja, false, 80)
        else mapa.addOnFirstLayoutListener { _, _, _, _, _ -> mapa.zoomToBoundingBox(caja, false, 80) }
        mapa.invalidate()
    }
    LaunchedEffect(puntos) {
        snapshotFlow { t.value }.collect { ms ->
            val p = puntoEn(puntos, ms) ?: return@collect
            marcador.position = GeoPoint(p.lat, p.lon)
            mapa.invalidate()
        }
    }
    AndroidView(factory = { mapa }, modifier = Modifier.fillMaxSize())
}

// ============================================================
// Arrancones y frenado
// ============================================================

@Composable
fun SeccionArrancones() {
    val mejores by Arrancones.mejores.collectAsStateWithLifecycle()
    val freno by Arrancones.mejorFreno.collectAsStateWithLifecycle()
    var abierto by remember { mutableStateOf(false) }
    Seccion("Arrancones y frenado") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tarjeta("0-60", mejores?.t60?.takeIf { it > 0f }?.let { fmt(it, 2) } ?: "--", Modifier.weight(1f), unidad = "s", compacta = true)
            Tarjeta("1/8 milla", mejores?.t201m?.takeIf { it > 0f }?.let { fmt(it, 2) } ?: "--", Modifier.weight(1f), unidad = "s", compacta = true)
            Tarjeta("Frenado 40-0", freno?.metros?.let { fmt(it, 1) } ?: "--", Modifier.weight(1f), unidad = "m", compacta = true)
        }
        BotonDeportivo("Abrir modo arrancones", { abierto = true }, Modifier.fillMaxWidth(), tamanoTexto = 13.sp)
        Text("Cronómetro de 0-40/60/80, 100 m y 1/8 de milla con semáforo y tiempo de reacción, y prueba de frenado. " +
            "Solo en un lugar cerrado y seguro.", color = Paleta.textoSec, fontSize = 11.sp)
    }
    if (abierto) DialogoArrancones { abierto = false }
}

@Composable
private fun DialogoArrancones(onCerrar: () -> Unit) {
    DisposableEffect(Unit) {
        Arrancones.abrir()
        onDispose { Arrancones.cerrar() }
    }
    val prueba by Arrancones.prueba.collectAsStateWithLifecycle()
    val fase by Arrancones.fase.collectAsStateWithLifecycle()
    val mensaje by Arrancones.mensaje.collectAsStateWithLifecycle()
    val tiempo by Arrancones.tiempo.collectAsStateWithLifecycle()
    val vel by Arrancones.velocidad.collectAsStateWithLifecycle()
    val dist by Arrancones.distancia.collectAsStateWithLifecycle()
    val luces by Arrancones.semaforo.collectAsStateWithLifecycle()
    val res by Arrancones.resultado.collectAsStateWithLifecycle()
    val fr by Arrancones.freno.collectAsStateWithLifecycle()
    val mejores by Arrancones.mejores.collectAsStateWithLifecycle()
    val mejorFreno by Arrancones.mejorFreno.collectAsStateWithLifecycle()
    val records by Arrancones.records.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Paleta.fondo).franjasCarreras().padding(12.dp)) {
            val ancho = maxWidth > maxHeight
            val reloj: @Composable (Modifier) -> Unit = { m ->
                Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Selector(listOf("Arrancón", "Frenado 40→0"), prueba.ordinal, { Arrancones.ponerPrueba(PruebaArr.entries[it]) }, Modifier.fillMaxWidth())
                    Semaforo(luces)
                    val colorReloj = when (fase) {
                        FaseArr.CORRIENDO -> Paleta.lima
                        FaseArr.TERMINADO -> if (luces == -1) Paleta.rojo else Paleta.texto
                        else -> Paleta.textoSec
                    }
                    Text(fmt(tiempo, 2), color = colorReloj, fontSize = 76.sp, fontFamily = Fuentes.logo)
                    EtiquetaPixel("segundos", Paleta.textoSec, 7.sp)
                    Text("${vel.roundToInt()} km/h · ${dist.roundToInt()} m", color = Paleta.texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(mensaje, color = if (fase == FaseArr.LISTO) Paleta.verde else Paleta.lima, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (prueba == PruebaArr.ARRANCON && fase == FaseArr.LISTO) {
                            BotonDeportivo("Semáforo", { Arrancones.arrancarSemaforo() }, tamanoTexto = 13.sp)
                        }
                        if (fase == FaseArr.TERMINADO || fase == FaseArr.CORRIENDO) {
                            BotonDeportivo("Otra vez", { Arrancones.reiniciar() }, tamanoTexto = 13.sp)
                        }
                        BotonDeportivo("Cerrar", onCerrar, primario = false, tamanoTexto = 13.sp)
                    }
                }
            }
            val tabla: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (prueba == PruebaArr.ARRANCON) {
                        EtiquetaPixel("tiempos", Paleta.lima, 8.sp)
                        FilaTiempo("0 - 40 km/h", res?.t40, mejores?.t40, "t40" in records)
                        FilaTiempo("0 - 60 km/h", res?.t60, mejores?.t60, "t60" in records)
                        FilaTiempo("0 - 80 km/h", res?.t80, mejores?.t80, "t80" in records)
                        FilaTiempo("100 m", res?.t100m, mejores?.t100m, "t100" in records, extra = res?.v100m)
                        FilaTiempo("1/8 milla (201 m)", res?.t201m, mejores?.t201m, "t201" in records, extra = res?.v201m)
                        FilaTiempo("Reacción", res?.reaccion, mejores?.reaccion, "reaccion" in records)
                        Dato("Vel. máxima", res?.vmax?.takeIf { it > 0f }?.let { "${it.roundToInt()} km/h" } ?: "--")
                    } else {
                        EtiquetaPixel("frenado de 40 a 0", Paleta.lima, 8.sp)
                        Dato("Distancia", fr?.let { "${fmt(it.metros, 1)} m" } ?: "--", if ("freno" in records) Paleta.lima else Paleta.texto)
                        Dato("Tiempo", fr?.let { "${fmt(it.segundos, 2)} s" } ?: "--")
                        Dato("Frenada máx.", fr?.let { "${fmt(it.gMax, 2)} g" } ?: "--")
                        Dato("Tu mejor", mejorFreno?.let { "${fmt(it.metros, 1)} m" } ?: "--", Paleta.lima)
                        Text("Una buena frenada de 40 a 0 en una moto como la tuya anda en 8 a 10 m con llantas y balatas en buen estado.",
                            color = Paleta.textoSec, fontSize = 11.sp)
                    }
                    Text("⚠ Solo en un lugar cerrado y seguro, con casco. Nunca en la calle.", color = Paleta.ambar, fontSize = 12.sp)
                    TextButton(onClick = { Arrancones.borrarRecords() }) { Text("Borrar récords", color = Paleta.textoSec) }
                }
            }
            if (ancho) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    reloj(Modifier.weight(1.1f).fillMaxHeight())
                    tabla(Modifier.weight(1f).fillMaxHeight().panel().padding(12.dp))
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    reloj(Modifier.fillMaxWidth())
                    tabla(Modifier.fillMaxWidth().weight(1f).panel().padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun FilaTiempo(nombre: String, t: Float?, mejor: Float?, record: Boolean, extra: Float? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, color = Paleta.textoSec, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (record) { EtiquetaPixel("récord", Paleta.lima, 7.sp); Spacer(Modifier.width(6.dp)) }
        Text(
            (t?.takeIf { it > 0f }?.let { fmt(it, 2) + " s" } ?: "--") + (extra?.takeIf { it > 0f && (t ?: 0f) > 0f }?.let { " @ ${it.roundToInt()}" } ?: ""),
            color = if (record) Paleta.lima else Paleta.texto, fontSize = 15.sp, fontFamily = Fuentes.logo,
        )
        Text("  " + (mejor?.takeIf { it > 0f }?.let { fmt(it, 2) } ?: "--"), color = Paleta.textoSec, fontSize = 11.sp, modifier = Modifier.width(48.dp))
    }
}

/** Semáforo pixel: 3 ámbar, verde y rojo (salida en falso). */
@Composable
private fun Semaforo(luces: Int) {
    Canvas(Modifier.width(230.dp).height(40.dp)) {
        val n = 5
        val lado = size.height * 0.8f
        val hueco = (size.width - lado * n) / (n - 1)
        for (i in 0 until n) {
            val color = when (i) { in 0..2 -> Color(0xFFFFB020); 3 -> Color(0xFF39FF14); else -> Color(0xFFFF3B3B) }
            val prendida = when (i) { in 0..2 -> luces in (i + 1)..3; 3 -> luces == 4; else -> luces == -1 }
            val x = i * (lado + hueco)
            if (prendida) drawRect(color.copy(alpha = 0.25f), Offset(x - 5f, 0f), Size(lado + 10f, size.height))
            drawRect(if (prendida) color else color.copy(alpha = 0.12f), Offset(x, size.height * 0.1f), Size(lado, lado))
        }
    }
}

// ============================================================
// Velocímetro contra el GPS
// ============================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeccionVelocimetro() {
    val medidas by Velocimetro.medidas.collectAsStateWithLifecycle()
    val mensaje by Velocimetro.mensaje.collectAsStateWithLifecycle()
    Seccion("Velocímetro vs GPS") {
        Text("Rodando parejo, cuando la aguja de la moto marque un número, tócalo (mejor si te ayuda quien va atrás) o dile al " +
            "asistente \"marca cuarenta\". Se compara con el GPS de los últimos 5 segundos.", color = Paleta.textoSec, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20, 30, 40, 50, 60, 70, 80).forEach { n ->
                BotonDeportivo("$n", { Velocimetro.medir(n) }, Modifier.width(64.dp), tamanoTexto = 16.sp)
            }
        }
        if (mensaje.isNotBlank()) Text(mensaje, color = Paleta.lima, fontSize = 12.sp)
        val e = Velocimetro.errorPromedio()
        if (e != null) {
            Dato("Tu velocímetro", when {
                kotlin.math.abs(e) < 1.5f -> "casi exacto"
                e > 0 -> "marca ${fmt(e, 1)} % de más"
                else -> "marca ${fmt(-e, 1)} % de menos"
            }, Paleta.lima)
            listOf(30, 40, 60, 80).forEach { m ->
                Velocimetro.realPara(m)?.let { Dato("Si marca $m", "vas a ${it.roundToInt()} km/h") }
            }
            Text("${medidas.size} medidas", color = Paleta.textoSec, fontSize = 11.sp)
            TextButton(onClick = { Velocimetro.borrar() }) { Text("Borrar medidas", color = Paleta.textoSec) }
        }
    }
}

// ============================================================
// Logros
// ============================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeccionLogros() {
    val ganados by Logros.ganados.collectAsStateWithLifecycle()
    var visto by remember { mutableStateOf<String?>(null) }
    Seccion("Logros · ${ganados.size}/${Logros.TODOS.size}") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Logros.TODOS.forEach { l ->
                val ok = ganados.containsKey(l.id)
                Column(
                    Modifier.width(70.dp).clickable { visto = if (visto == l.id) null else l.id },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(52.dp).panel(Formas.chica),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconoPixel(if (l.secreto && !ok) PIXEL_INCOGNITA else l.arte,
                            if (ok) Paleta.lima else Paleta.textoSec.copy(alpha = 0.25f), Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(if (l.secreto && !ok) "???" else l.nombre, color = if (ok) Paleta.texto else Paleta.textoSec,
                        fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        val l = Logros.TODOS.firstOrNull { it.id == visto }
        if (l != null) {
            val cuando = ganados[l.id]
            Text(
                (if (l.secreto && cuando == null) "Es secreto… pista: el logo de arriba." else l.detalle) +
                    (cuando?.let { " · ganado el " + SimpleDateFormat("d MMM yyyy", Locale("es", "MX")).format(Date(it)) } ?: ""),
                color = Paleta.lima, fontSize = 12.sp,
            )
        }
    }
}

private val PIXEL_INCOGNITA = listOf(".XXXXX.", "X.....X", ".....X.", "...XX..", "...X...", ".......", "...X...")

/** Aviso que baja desde arriba al ganar un logro. */
@Composable
fun BannerLogro(modifier: Modifier = Modifier) {
    val nuevo by Logros.nuevo.collectAsStateWithLifecycle()
    val l = nuevo ?: return
    Row(
        modifier.panel(Formas.chica).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoPixel(l.arte, Paleta.lima, Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            EtiquetaPixel("logro desbloqueado", Paleta.lima, 7.sp)
            Text(l.nombre, color = Paleta.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Confeti pixel (modo XOF): cuadritos que caen 3 segundos. */
@Composable
fun ConfetiPixel(desde: Long, modifier: Modifier = Modifier) {
    if (desde == 0L) return
    val t = remember(desde) { mutableFloatStateOf(0f) }
    LaunchedEffect(desde) {
        val t0 = withFrameNanos { it }
        while (t.floatValue < 3.2f) t.floatValue = (withFrameNanos { it } - t0) / 1e9f
    }
    if (t.floatValue >= 3.2f) return
    val colores = listOf(Color(0xFFFFC83D), Paleta.lima, Color.White, Color(0xFFFF2BD6), Color(0xFF00E5FF))
    Canvas(modifier.fillMaxSize()) {
        val s = t.floatValue
        val lado = 7.dp.toPx()
        for (i in 0 until 90) {
            // posición "aleatoria" fija por cuadrito (sin Random: no cambia entre cuadros)
            val x0 = ((i * 7919) % 1000) / 1000f * size.width
            val vel = 0.35f + ((i * 104729) % 100) / 100f * 0.5f
            val y = (-0.1f + s * vel) * size.height
            val x = x0 + kotlin.math.sin(s * 3f + i) * 18.dp.toPx()
            if (y < size.height) drawRect(colores[i % colores.size].copy(alpha = (1f - s / 3.2f).coerceIn(0f, 1f)), Offset(x, y), Size(lado, lado))
        }
    }
}

// ============================================================
// Ajustes: respaldo y lluvia en tu ruta
// ============================================================

@Composable
fun SeccionRespaldo() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mensaje by Respaldo.mensaje.collectAsStateWithLifecycle()
    val auto by Respaldo.autoSemanal.collectAsStateWithLifecycle()
    val ultimoAuto by Respaldo.ultimoAuto.collectAsStateWithLifecycle()
    val ultimoManual by Respaldo.ultimoManual.collectAsStateWithLifecycle()
    var porRestaurar by remember { mutableStateOf<String?>(null) }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) Respaldo.escribir(ctx, uri)
    }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val t = Respaldo.leer(ctx, uri)
            if (t == null) Respaldo.mensaje.value = "Ese archivo no es un respaldo de FT150TS" else porRestaurar = t
        }
    }
    Seccion("Respaldo de tus datos") {
        Text("Todo en un archivo: odómetro, mantenimientos, gasolina, marchas aprendidas, calibración, ajustes, apariencia, " +
            "logros, récords y rodadas. Sirve para cambiar de teléfono o por si algo falla.", color = Paleta.textoSec, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Guardar", { guardar.launch(Respaldo.nombre()) }, Modifier.weight(1f), tamanoTexto = 13.sp)
            BotonDeportivo("Compartir", { Respaldo.compartir(ctx) }, Modifier.weight(1f), primario = false, tamanoTexto = 13.sp)
            BotonDeportivo("Restaurar", { abrir.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }, Modifier.weight(1f),
                primario = false, color = Paleta.ambar, tamanoTexto = 13.sp)
        }
        if (mensaje.isNotBlank()) Text(mensaje, color = Paleta.lima, fontSize = 12.sp)
        Dato("Último respaldo", if (ultimoManual > 0) SimpleDateFormat("d MMM yyyy HH:mm", Locale("es", "MX")).format(Date(ultimoManual)) else "Nunca",
            if (ultimoManual > 0) Paleta.texto else Paleta.ambar)
        FilaSwitch("Respaldo automático cada semana",
            "Guarda una copia en Descargas/FT150TS" + if (ultimoAuto > 0) " (último: " + SimpleDateFormat("d MMM", Locale("es", "MX")).format(Date(ultimoAuto)) + ")" else "",
            auto) { Respaldo.ponerAutoSemanal(it) }
        TextButton(onClick = { Respaldo.guardarEnDescargas() }) { Text("Guardar copia en Descargas ahora", color = Paleta.lima) }
    }
    val pendiente = porRestaurar
    if (pendiente != null) {
        val info = Respaldo.info(pendiente)
        AlertDialog(
            onDismissRequest = { porRestaurar = null },
            title = { Text("¿Restaurar este respaldo?") },
            text = {
                Text(
                    (info?.let { "Del " + SimpleDateFormat("d MMM yyyy HH:mm", Locale("es", "MX")).format(Date(it.fecha)) +
                        " (versión ${it.version}) · ${it.ajustes} datos · ${it.rodadas} rodadas.\n\n" } ?: "") +
                        "Reemplaza lo que tienes ahora en el teléfono y la app se reinicia."
                )
            },
            confirmButton = { TextButton(onClick = { porRestaurar = null; Respaldo.restaurar(ctx, pendiente) }) { Text("Restaurar", color = Paleta.ambar) } },
            dismissButton = { TextButton(onClick = { porRestaurar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun SeccionLluviaRuta() {
    val activo by LluviaRuta.activo.collectAsStateWithLifecycle()
    val aviso by LluviaRuta.aviso.collectAsStateWithLifecycle()
    val ultima by LluviaRuta.ultimaRevision.collectAsStateWithLifecycle()
    Seccion("Lluvia en tu ruta") {
        FilaSwitch("Avisar si va a llover adelante",
            "Rodando, cada 10 min revisa el pronóstico 4, 8, 12 y 16 km adelante en la dirección en que vas y, con tu " +
                "velocidad, si va a llover cuando pases por ahí", activo) { LluviaRuta.ponerActivo(it) }
        Dato("Ahora", when {
            aviso == null && ultima == 0L -> "Sin revisar todavía"
            aviso == null -> "Sin lluvia adelante (" + SimpleDateFormat("HH:mm", Locale.US).format(Date(ultima)) + ")"
            aviso!!.km == 0 -> "Lluvia donde estás"
            else -> "Lluvia a ~${aviso!!.km} km (en ~${aviso!!.minutos} min)"
        }, if (aviso != null) Paleta.ambar else Paleta.texto)
        TextButton(onClick = { LluviaRuta.revisar(true) }) { Text("Revisar ahora", color = Paleta.lima) }
    }
}
