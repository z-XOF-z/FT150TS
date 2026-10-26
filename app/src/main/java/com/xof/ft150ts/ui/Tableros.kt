package com.xof.ft150ts.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.ClimaManager
import com.xof.ft150ts.EstiloTablero
import com.xof.ft150ts.Gasolina
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.Moto
import com.xof.ft150ts.UbicacionManager
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Tableros nuevos (v3.5): Pista, Minimal, Telemetría y Bento. Se cambian sin tocar el tema: todos usan los colores,
 * formas y adornos del tema activo (Paleta / Formas / panel()). Diseño guiado por UI UX Pro Max: el valor actual
 * grande y con su texto (no solo color), las RPM contra la meta (punto de cambio) y una gráfica en vivo de 60 s.
 *
 * Rendimiento: cada tablero lee los datos con enVivo() (solo con la página a la vista) y dibuja en Canvas; la aguja
 * de RPM va con el PID, la velocidad con una animación corta y solo cuando cambia. Respeta "quitar animaciones".
 */

/** Nombre de cada tablero (Ajustes y aviso al cambiar). */
val NOMBRES_TABLERO = mapOf(
    EstiloTablero.LCD to "LCD Sport", EstiloTablero.RACE to "Race LCD", EstiloTablero.VFD to "VFD 85",
    EstiloTablero.ANILLO to "Anillo", EstiloTablero.CLASICO to "Clásico", EstiloTablero.PISTA to "Pista",
    EstiloTablero.MINIMAL to "Minimal", EstiloTablero.TELEMETRIA to "Telemetría", EstiloTablero.BENTO to "Bento",
    EstiloTablero.SUPERBIKE to "Superbike", EstiloTablero.F1 to "F1", EstiloTablero.ARRANCON to "Arrancón",
    EstiloTablero.RALLY to "Rally", EstiloTablero.ARCADE to "Arcade", EstiloTablero.UNIVERSAL to "Universal",
    EstiloTablero.MORADO to "Racing Morado", EstiloTablero.TELEMETRIA_MORADA to "Telemetría Morada", EstiloTablero.DUAL_NEON to "Dual Neón",
)

// ---------------- Datos compartidos ----------------

/** Velocidad a mostrar: la de la moto o la del GPS; por debajo de 2 km/h es 0 (el GPS tiembla estando parado). */
@Composable
internal fun velocidadEstable(): Float {
    val r by Moto.rapida.enVivo()
    val velTel by UbicacionManager.velocidad.enVivo()
    // Sin la moto: la del GPS solo si es creíble (bajo techo rebota y marcaría 10–30 km/h parado)
    val v = if (r.vel >= 0) r.vel else if (velTel >= 0f && com.xof.ft150ts.UbicacionManager.velocidadConfiableKmh() < 0f) 0f else velTel
    return when { v < 0f -> -1f; v < 2f -> 0f; else -> v }
}

/** Velocidad que cuenta suave hasta el valor nuevo (no brinca); sin animación si el sistema las quitó. */
@Composable
internal fun velocidadAnimada(): Float {
    val v = velocidadEstable()
    val ctx = LocalContext.current
    val reducidas = remember { animacionesReducidas(ctx) }
    // Cuenta suave hacia el valor nuevo en ~0.25 s, a 30 pasos por segundo: se ve igual y la pantalla no tiene que
    // dibujar 120 cuadros por segundo mientras cambia (gasta mucho menos batería rodando)
    val mostrado = remember { androidx.compose.runtime.mutableFloatStateOf(max(v, 0f)) }
    LaunchedEffect(v, reducidas) {
        val meta = max(v, 0f)
        if (reducidas) { mostrado.floatValue = meta; return@LaunchedEffect }
        val desde = mostrado.floatValue
        for (paso in 1..8) {
            delay(33)
            mostrado.floatValue = desde + (meta - desde) * paso / 8f
        }
    }
    return if (v < 0f) -1f else mostrado.floatValue
}

/** Igual que velocidadAnimada, pero como estado: se lee dentro de un dibujo y no rearma la composición. */
@Composable
internal fun velocidadAnimadaEstado(): State<Float> {
    val v = velocidadEstable()
    val ctx = LocalContext.current
    val reducidas = remember { animacionesReducidas(ctx) }
    val mostrado = remember { androidx.compose.runtime.mutableFloatStateOf(max(v, 0f)) }
    val signo = remember { androidx.compose.runtime.mutableFloatStateOf(if (v < 0f) -1f else 1f) }
    signo.floatValue = if (v < 0f) -1f else 1f
    LaunchedEffect(v, reducidas) {
        val meta = max(v, 0f)
        if (reducidas) { mostrado.floatValue = meta; return@LaunchedEffect }
        val desde = mostrado.floatValue
        for (paso in 1..8) { delay(33); mostrado.floatValue = desde + (meta - desde) * paso / 8f }
    }
    return remember { androidx.compose.runtime.derivedStateOf { if (signo.floatValue < 0f) -1f else mostrado.floatValue } }
}

/** RPM con la inercia del PID; en la animación del switch suben al tope y regresan. */
@Composable
internal fun rpmSuave(): State<Float> {
    val r by Moto.rapida.enVivo()
    val arranque = LocalArranque.current
    return pidAnimado({ rpmArranque(arranque.value, max(0, r.rpm).toFloat()) }, hz = 30)
}

/**
 * Aísla lo que cambia muy seguido (velocidad animada, RPM con el PID): solo esta pieza se vuelve a armar, no todo el
 * tablero. Medido en el POCO F4: sin esto, cada paso de la aguja (30 por segundo) reconstruía el tablero completo.
 */
@Composable
internal fun Vivo(contenido: @Composable () -> Unit) = contenido()

/** true al pasar el punto de cambio; solo avisa cuando cruza (no con cada paso de la aguja). */
@Composable
internal fun enCambio(rpm: State<Float>, shift: Int): Boolean {
    val d = remember(rpm, shift) { androidx.compose.runtime.derivedStateOf { rpm.value >= shift && shift > 0 } }
    return d.value
}

/** Texto de la velocidad: "888" en la prueba de segmentos del switch. */
@Composable
internal fun textoVel(v: Float): String =
    if (pruebaSegmentos(LocalArranque.current.value)) "888" else if (v < 0f) "--" else "${v.toInt()}"

/** Marcha que entra deslizándose: hacia arriba si subes, hacia abajo si bajas. */
@Composable
internal fun MarchaAnimada(marcha: Int, tamano: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val reducidas = remember { animacionesReducidas(ctx) }
    AnimatedContent(
        targetState = marcha, modifier = modifier, label = "marcha",
        transitionSpec = {
            if (reducidas) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else {
                val sube = targetState > initialState
                (slideInVertically(tween(220)) { if (sube) it else -it } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically(tween(180)) { if (sube) -it else it } + fadeOut(tween(180)))
            }
        },
    ) { m ->
        val txt = when { m < 0 -> "-"; m == 0 -> "N"; else -> "$m" }
        Text(txt, color = if (m == 0) Paleta.verde else Paleta.lima, fontSize = tamano, fontFamily = Fuentes.logo,
            textAlign = TextAlign.Center)
    }
}

/** Contenedor común: animación del switch, márgenes y accesos directos a la izquierda (si están prendidos). */
@Composable
internal fun MarcoTablero(contenido: @Composable (Dp, Dp) -> Unit) {
    val accesos by Lanzador.mostrarAccesos.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().animacionSwitch(LocalArranque.current, Paleta.lima).padding(horizontal = 10.dp, vertical = 8.dp)) {
        val ancho = maxWidth
        val alto = maxHeight
        if (ancho > alto && accesos) {
            val rail = anchoAccesos(alto)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccesosDirectos(vertical = true, Modifier.fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) { contenido(ancho - rail - 10.dp, alto) }
            }
        } else contenido(ancho, alto)
    }
}

/** Tamaño de letra a partir de un alto disponible. */
@Composable
internal fun Dp.aSp() = with(LocalDensity.current) { this@aSp.toSp() }

// ============================================================
// Pista: barra de RPM tipo MotoGP, marcha gigante al centro
// ============================================================
@Composable
fun TableroPista() {
    MarcoTablero { _, alto ->
        val r by Moto.rapida.enVivo()
        val rpm = rpmSuave()
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BarraRpmPista(rpm, r.shift, Modifier.fillMaxWidth().height(alto * 0.24f))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Velocidad
                Column(Modifier.weight(1.2f).fillMaxHeight().panel(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Vivo { Text(textoVel(velocidadAnimada()), color = Paleta.texto, fontSize = (alto * 0.30f).aSp(), fontFamily = Fuentes.logo) }
                    EtiquetaPixel("km/h", Paleta.textoSec, 9.sp)
                }
                // Marcha
                Box(Modifier.weight(0.8f).fillMaxHeight().panel(), contentAlignment = Alignment.Center) {
                    MarchaAnimada(r.marcha, (alto * 0.40f).aSp())
                    EtiquetaPixel("marcha", Paleta.textoSec, 7.sp, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                }
                // RPM y motor
                Column(Modifier.weight(1.2f).fillMaxHeight().panel().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    Vivo {
                        val rv by rpm
                        DatoGrande("rpm", if (rv > 0f) "${(rv / 10).toInt() * 10}" else "--", colorRpm(rv.toInt(), r.shift), (alto * 0.085f).aSp())
                    }
                    Row {
                        Box(Modifier.weight(1f)) { DatoGrande("motor", if (r.temp > -90f) "${r.temp.toInt()}°" else "--", Paleta.texto, (alto * 0.06f).aSp()) }
                        Box(Modifier.weight(1f)) { DatoGrande("batería", if (r.vMoto >= 0) "${fmt(r.vMoto, 1)} V" else "--", Paleta.texto, (alto * 0.06f).aSp()) }
                    }
                }
            }
            FilaResumen(Modifier.fillMaxWidth())
        }
    }
}

/** Barra de 40 segmentos inclinados, números cada 1000 y marca del punto de cambio; destella al pasarlo. */
@Composable
private fun BarraRpmPista(rpm: State<Float>, shift: Int, modifier: Modifier) {
    val rojo = enCambio(rpm, shift)
    val destello = parpadeoEstado(rojo)
    val baja = Paleta.rpmBaja
    val media = Paleta.rpmMedia
    val alta = Paleta.rpmAlta
    val vacio = Paleta.superficie2
    val texto = Paleta.textoSec
    val acento = Paleta.lima
    Box(modifier.graphicsLayer().drawWithCache {
        val n = 40
        val altoNum = size.height * 0.26f
        val altoBarra = size.height - altoNum
        val hueco = 3.dp.toPx()
        val incl = altoBarra * 0.35f
        val ancho = (size.width - incl - hueco * (n - 1)) / n
        val segs = List(n) { i ->
            val x = i * (ancho + hueco)
            // de abajo hacia arriba: los segmentos crecen un poco hacia la derecha (efecto de velocidad)
            val h = altoBarra * (0.55f + 0.45f * i / (n - 1))
            Path().apply { moveTo(x + incl, size.height - h); lineTo(x + incl + ancho, size.height - h); lineTo(x + ancho, size.height); lineTo(x, size.height); close() }
        }
        val pxNum = max(1f, altoNum / 9f)
        onDrawBehind {
            val v = rpm.value
            val encendidos = (v / RPM_MAX * n).toInt().coerceIn(0, n)
            for (i in 0 until n) {
                val rpmSeg = (i + 1) * RPM_MAX / n
                val base = when { rpmSeg >= shift -> alta; rpmSeg >= shift * 0.7f -> media; else -> baja }
                val color = when {
                    rojo -> alta.copy(alpha = destello.value)
                    i < encendidos -> base
                    else -> vacio
                }
                drawPath(segs[i], color)
            }
            // números 1..10
            for (k in 1..10) {
                val x = (k * n / 10 - 1) * (ancho + hueco)
                textoPixel("$k", x, 0f, pxNum, if (k * 1000 >= shift) alta else texto)
            }
            // marca del punto de cambio
            val xs = shift.toFloat() / RPM_MAX * (n * (ancho + hueco)) + incl
            drawLine(acento, Offset(xs, altoNum), Offset(xs, size.height), 2.dp.toPx())
        }
    })
}

@Composable
internal fun DatoGrande(etiqueta: String, valor: String, color: Color, tamano: androidx.compose.ui.unit.TextUnit) {
    Column {
        EtiquetaPixel(etiqueta, Paleta.textoSec, 7.sp)
        Text(valor, color = color, fontSize = tamano, fontFamily = Fuentes.logo, maxLines = 1)
    }
}

/** Viaje, autonomía, hora y clima en una fila de chips. */
@Composable
internal fun FilaResumen(modifier: Modifier) {
    val v by Moto.viaje.enVivo()
    val g by Gasolina.resumen.collectAsStateWithLifecycle()
    val c by ClimaManager.clima.collectAsStateWithLifecycle()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("viaje", "${fmt(v.km, 1)} km", Modifier.weight(1f))
        Chip("rango", if (g.autonomia >= 0) "${g.autonomia} km" else "--", Modifier.weight(1f),
            if (g.autonomia in 0..30) Paleta.ambar else Paleta.texto)
        Chip("máx", "${v.velMax.toInt()} km/h", Modifier.weight(1f))
        Chip("clima", c?.let { "${it.temp}°" } ?: "--", Modifier.weight(1f))
    }
}

@Composable
internal fun Chip(etiqueta: String, valor: String, modifier: Modifier, color: Color = Paleta.texto) {
    Row(modifier.panel(Formas.chica).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        EtiquetaPixel(etiqueta, Paleta.textoSec, 7.sp)
        Spacer(Modifier.weight(1f))
        Text(valor, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ============================================================
// Minimal: velocidad enorme y un arco fino de RPM
// ============================================================
@Composable
fun TableroMinimal() {
    MarcoTablero { _, alto ->
        val r by Moto.rapida.enVivo()
        val rpm = rpmSuave()
        val rojo = enCambio(rpm, r.shift)
        val destello = parpadeoEstado(rojo)
        val pista = Paleta.superficie2
        val acento = Paleta.lima
        val alta = Paleta.rpmAlta
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Arco de 240°: se dibuja una vez por tamaño y solo cambia cuánto se llena
            Box(Modifier.fillMaxHeight().width(alto * 1.25f).graphicsLayer().drawWithCache {
                val grosor = 12.dp.toPx()
                val lado = min(size.width, size.height * 1.25f) - grosor
                val tl = Offset((size.width - lado) / 2, grosor / 2 + (size.height - lado * 0.8f) / 2)
                val caja = Size(lado, lado)
                val trazo = Stroke(grosor, cap = StrokeCap.Round)
                onDrawBehind {
                    drawArc(pista, 150f, 240f, false, tl, caja, style = trazo)
                    val f = (rpm.value / RPM_MAX).coerceIn(0f, 1f)
                    val color = if (rojo) alta.copy(alpha = destello.value) else colorRpm(rpm.value.toInt(), r.shift)
                    if (f > 0f) drawArc(color, 150f, 240f * f, false, tl, caja, style = trazo)
                    // marca del punto de cambio
                    val a = 150f + 240f * (r.shift.toFloat() / RPM_MAX).coerceIn(0f, 1f)
                    drawArc(acento, a - 0.8f, 1.6f, false, Offset(tl.x - grosor * 0.6f, tl.y - grosor * 0.6f),
                        Size(lado + grosor * 1.2f, lado + grosor * 1.2f), style = Stroke(grosor * 0.5f))
                }
            })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Vivo { Text(textoVel(velocidadAnimada()), color = Paleta.texto, fontSize = (alto * 0.42f).aSp(), fontFamily = Fuentes.logo) }
                EtiquetaPixel("km/h", Paleta.textoSec, 10.sp)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.panel(Formas.chica).padding(horizontal = 18.dp, vertical = 2.dp)) {
                    MarchaAnimada(r.marcha, (alto * 0.11f).aSp())
                }
            }
            val reloj = horaMinimal()
            EtiquetaPixel(reloj, Paleta.textoSec, 9.sp, Modifier.align(Alignment.TopStart))
            Box(Modifier.align(Alignment.TopEnd)) {
                Vivo {
                    val rv by rpm
                    EtiquetaPixel(if (rv > 0f) "${(rv / 100).toInt() * 100} rpm" else "-- rpm", Paleta.textoSec, 9.sp)
                }
            }
        }
    }
}

@Composable
private fun horaMinimal(): String {
    val visible = LocalPaginaVisible.current
    val h = remember { androidx.compose.runtime.mutableStateOf("") }
    LaunchedEffect(visible) {
        while (visible) {
            h.value = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
            delay(10_000)
        }
    }
    return h.value
}

// ============================================================
// Telemetría: gráfica en vivo de 60 s + inclinación y fuerza G
// ============================================================

/** Últimos 60 s de velocidad y RPM (4 muestras por segundo), en arreglos fijos: no crea objetos al muestrear. */
private class Historial(val n: Int = 240) {
    val vel = FloatArray(n)
    val rpm = FloatArray(n)
    var pos = 0
    var lleno = 0
    fun agregar(v: Float, r: Float) { vel[pos] = v; rpm[pos] = r; pos = (pos + 1) % n; lleno = min(lleno + 1, n) }
    /** i-ésima muestra desde la más vieja. */
    fun velEn(i: Int) = vel[(pos - lleno + i + n) % n]
    fun rpmEn(i: Int) = rpm[(pos - lleno + i + n) % n]
}

@Composable
fun TableroTelemetria() {
    MarcoTablero { _, alto ->
        val r by Moto.rapida.enVivo()
        val rpm = rpmSuave()
        val vel = velocidadEstable()
        val hist = remember { Historial() }
        val tick = remember { mutableIntStateOf(0) }
        val visible = LocalPaginaVisible.current
        // Muestreo cada 250 ms solo con la página a la vista: la gráfica se recorre sola
        val velS = androidx.compose.runtime.rememberUpdatedState(vel)
        val arranque = LocalArranque.current
        LaunchedEffect(visible) {
            while (visible) {
                // Durante la animación del switch las RPM suben al tope de mentiras: eso no se grafica
                if (arranque.value >= 0f) { delay(250); continue }
                hist.agregar(max(velS.value, 0f), max(rpm.value, 0f))
                tick.intValue++
                delay(250)
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth().weight(1f).panel().padding(12.dp), verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Vivo { Text(textoVel(velocidadAnimada()), color = Paleta.texto, fontSize = (alto * 0.24f).aSp(), fontFamily = Fuentes.logo) }
                        Spacer(Modifier.width(6.dp))
                        EtiquetaPixel("km/h", Paleta.textoSec, 8.sp, Modifier.padding(bottom = 10.dp))
                        Spacer(Modifier.weight(1f))
                        MarchaAnimada(r.marcha, (alto * 0.18f).aSp())
                    }
                    Vivo {
                        val rv by rpm
                        Text(if (rv > 0f) "${(rv / 10).toInt() * 10} rpm" else "-- rpm", color = colorRpm(rv.toInt(), r.shift),
                            fontSize = (alto * 0.07f).aSp(), fontFamily = Fuentes.logo)
                    }
                }
                BarraCentrada("inclinación", r.incl.coerceAtLeast(0f), 60f, "${max(0f, r.incl).toInt()}°", Modifier.fillMaxWidth())
                BarraCentrada("acelera / frena", r.aFwd, 1f, "${fmt(r.aFwd, 2)} g", Modifier.fillMaxWidth(), centrada = true)
            }
            GraficaEnVivo(hist, tick, r.shift, Modifier.weight(1.6f).fillMaxHeight().panel())
        }
    }
}

/** Gráfica de los últimos 60 s: velocidad (acento) y RPM (color de RPM alta), con rejilla tenue. */
@Composable
private fun GraficaEnVivo(hist: Historial, tick: State<Int>, shift: Int, modifier: Modifier) {
    val cVel = Paleta.lima
    val cRpm = Paleta.rpmAlta
    val rejilla = Paleta.borde.copy(alpha = 0.5f)
    val texto = Paleta.textoSec
    Box(modifier.graphicsLayer().drawWithCache {
        val m = 14.dp.toPx()
        val w = size.width - 2 * m
        val h = size.height - 2 * m - 12.dp.toPx()
        val y0 = m + 12.dp.toPx()
        val lineaVel = Path()
        val lineaRpm = Path()
        val px = max(1f, 1.4.dp.toPx())
        onDrawBehind {
            tick.value // se redibuja con cada muestra
            for (k in 0..4) drawLine(rejilla, Offset(m, y0 + h * k / 4), Offset(m + w, y0 + h * k / 4), 1f)
            val yShift = y0 + h * (1f - shift.toFloat() / RPM_MAX)
            drawLine(cRpm.copy(alpha = 0.35f), Offset(m, yShift), Offset(m + w, yShift), 1.dp.toPx())
            textoPixel("60 s", m, m - 2f, px, texto)
            textoPixel("km/h", m + w - anchoPixel("km/h rpm", px), m - 2f, px, cVel)
            textoPixel("rpm", m + w - anchoPixel("rpm", px), m - 2f, px, cRpm)
            val n = hist.lleno
            if (n >= 2) {
                lineaVel.reset(); lineaRpm.reset()
                val paso = w / (hist.n - 1)
                val x0 = m + w - (n - 1) * paso
                for (i in 0 until n) {
                    val x = x0 + i * paso
                    val yv = y0 + h * (1f - (hist.velEn(i) / 140f).coerceIn(0f, 1f))
                    val yr = y0 + h * (1f - (hist.rpmEn(i) / RPM_MAX).coerceIn(0f, 1f))
                    if (i == 0) { lineaVel.moveTo(x, yv); lineaRpm.moveTo(x, yr) } else { lineaVel.lineTo(x, yv); lineaRpm.lineTo(x, yr) }
                }
                drawPath(lineaRpm, cRpm, style = Stroke(2.dp.toPx()))
                drawPath(lineaVel, cVel, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    })
}

/** Barra con etiqueta y valor en texto (no solo color). [centrada]: crece a los dos lados del centro (frena / acelera). */
@Composable
private fun BarraCentrada(etiqueta: String, valor: Float, maximo: Float, texto: String, modifier: Modifier, centrada: Boolean = false) {
    val acento = Paleta.lima
    val freno = Paleta.rpmAlta
    val vacio = Paleta.superficie2
    Column(modifier.panel(Formas.chica).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtiquetaPixel(etiqueta, Paleta.textoSec, 7.sp)
            Spacer(Modifier.weight(1f))
            Text(texto, color = Paleta.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRect(vacio)
            val f = (valor / maximo).coerceIn(-1f, 1f)
            if (centrada) {
                val c = size.width / 2
                if (f >= 0) drawRect(acento, Offset(c, 0f), Size(c * f, size.height))
                else drawRect(freno, Offset(c + c * f, 0f), Size(-c * f, size.height))
                drawRect(Color.White.copy(alpha = 0.6f), Offset(c - 1f, 0f), Size(2f, size.height))
            } else drawRect(acento, Offset.Zero, Size(size.width * f.coerceAtLeast(0f), size.height))
        }
    }
}

// ============================================================
// Bento: mosaico de tarjetas
// ============================================================
@Composable
fun TableroBento() {
    MarcoTablero { _, alto ->
        val r by Moto.rapida.enVivo()
        val e by Moto.estado.enVivo()
        val vj by Moto.viaje.enVivo()
        val g by Gasolina.resumen.collectAsStateWithLifecycle()
        val rpm = rpmSuave()
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().weight(1.5f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.1f).fillMaxHeight().panel().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    EtiquetaPixel("velocidad", Paleta.textoSec, 8.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Vivo { Text(textoVel(velocidadAnimada()), color = Paleta.texto, fontSize = (alto * 0.26f).aSp(), fontFamily = Fuentes.logo) }
                        Spacer(Modifier.width(6.dp))
                        EtiquetaPixel("km/h", Paleta.textoSec, 8.sp, Modifier.padding(bottom = 12.dp))
                    }
                }
                Column(Modifier.weight(1.9f).fillMaxHeight().panel().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EtiquetaPixel("rpm · cambio a ${r.shift}", Paleta.textoSec, 8.sp)
                        Spacer(Modifier.weight(1f))
                        Vivo { val rv by rpm; Text(if (rv > 0f) "${(rv / 10).toInt() * 10}" else "--", color = colorRpm(rv.toInt(), r.shift),
                            fontSize = (alto * 0.10f).aSp(), fontFamily = Fuentes.logo) }
                    }
                    BulletRpm(rpm, r.shift, Modifier.fillMaxWidth().height(alto * 0.13f))
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(0.8f).fillMaxHeight().panel(), contentAlignment = Alignment.Center) {
                    MarchaAnimada(r.marcha, (alto * 0.22f).aSp())
                    EtiquetaPixel("marcha", Paleta.textoSec, 7.sp, Modifier.align(Alignment.TopStart).padding(10.dp))
                }
                Tarjeta("Inclinación", if (r.incl >= 0) "${r.incl.toInt()}" else "--", Modifier.weight(1f).fillMaxHeight(), unidad = "°",
                    extra = "${fmt(r.aFwd, 2)} g", compacta = true)
                Tarjeta("Batería", if (r.vMoto >= 0) fmt(r.vMoto, 1) else e.bateria.ifBlank { "--" }, Modifier.weight(1f).fillMaxHeight(),
                    unidad = if (r.vMoto >= 0) "V" else "", extra = if (r.temp > -90f) "Motor ${r.temp.toInt()}°" else "", compacta = true)
                Tarjeta("Viaje", fmt(vj.km, 1), Modifier.weight(1f).fillMaxHeight(), unidad = "km",
                    extra = if (g.autonomia >= 0) "Rango ${g.autonomia} km" else "Máx ${vj.velMax.toInt()} km/h", compacta = true)
            }
        }
    }
}

/** Bullet chart: zonas baja/media/alta de fondo, barra de RPM y la meta (punto de cambio) como línea. */
@Composable
private fun BulletRpm(rpm: State<Float>, shift: Int, modifier: Modifier) {
    val baja = Paleta.rpmBaja.copy(alpha = 0.18f)
    val media = Paleta.rpmMedia.copy(alpha = 0.18f)
    val alta = Paleta.rpmAlta.copy(alpha = 0.22f)
    val acento = Paleta.texto
    val rojo = enCambio(rpm, shift)
    val destello = parpadeoEstado(rojo)
    Canvas(modifier.graphicsLayer()) {
        val w = size.width
        val h = size.height
        drawRect(baja, Offset.Zero, Size(w * shift * 0.65f / RPM_MAX, h))
        drawRect(media, Offset(w * shift * 0.65f / RPM_MAX, 0f), Size(w * shift * 0.25f / RPM_MAX, h))
        drawRect(alta, Offset(w * shift * 0.9f / RPM_MAX, 0f), Size(w - w * shift * 0.9f / RPM_MAX, h))
        val v = rpm.value
        val c = colorRpm(v.toInt(), shift)
        drawRect(if (rojo) c.copy(alpha = destello.value) else c, Offset(0f, h * 0.3f), Size(w * (v / RPM_MAX).coerceIn(0f, 1f), h * 0.4f))
        val xs = w * shift / RPM_MAX
        drawLine(acento, Offset(xs, 0f), Offset(xs, h), 3.dp.toPx())
    }
}

// ============================================================
// Vistas previas para Ajustes
// ============================================================
internal fun DrawScope.vistaPista(acento: Color, alta: Color) {
    val w = size.width; val h = size.height
    for (i in 0 until 20) {
        val x = w * 0.05f + i * w * 0.045f
        val c = if (i < 13) acento else if (i < 16) alta.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
        drawRect(c, Offset(x, h * (0.30f - 0.15f * i / 19)), Size(w * 0.035f, h * (0.12f + 0.15f * i / 19)))
    }
    drawRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.05f, h * 0.45f), Size(w * 0.32f, h * 0.38f))
    drawRect(acento.copy(alpha = 0.25f), Offset(w * 0.40f, h * 0.45f), Size(w * 0.20f, h * 0.38f))
    drawRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.63f, h * 0.45f), Size(w * 0.32f, h * 0.38f))
    drawRect(Color.White.copy(alpha = 0.8f), Offset(w * 0.12f, h * 0.55f), Size(w * 0.18f, h * 0.16f))
    drawRect(acento, Offset(w * 0.46f, h * 0.52f), Size(w * 0.08f, h * 0.24f))
}

internal fun DrawScope.vistaMinimal(acento: Color) {
    val w = size.width; val h = size.height
    val lado = h * 0.8f
    drawArc(Color.White.copy(alpha = 0.15f), 150f, 240f, false, Offset(w / 2 - lado / 2, h * 0.12f), Size(lado, lado), style = Stroke(h * 0.06f, cap = StrokeCap.Round))
    drawArc(acento, 150f, 150f, false, Offset(w / 2 - lado / 2, h * 0.12f), Size(lado, lado), style = Stroke(h * 0.06f, cap = StrokeCap.Round))
    drawRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.41f, h * 0.38f), Size(w * 0.18f, h * 0.22f))
}

internal fun DrawScope.vistaTelemetria(acento: Color, alta: Color) {
    val w = size.width; val h = size.height
    drawRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.04f, h * 0.1f), Size(w * 0.32f, h * 0.45f))
    drawRect(Color.White.copy(alpha = 0.8f), Offset(w * 0.08f, h * 0.2f), Size(w * 0.16f, h * 0.18f))
    drawRect(acento, Offset(w * 0.04f, h * 0.65f), Size(w * 0.22f, h * 0.07f))
    drawRect(alta, Offset(w * 0.04f, h * 0.80f), Size(w * 0.14f, h * 0.07f))
    drawRect(Color.White.copy(alpha = 0.08f), Offset(w * 0.40f, h * 0.1f), Size(w * 0.56f, h * 0.8f))
    val p = Path(); val q = Path()
    for (i in 0..20) {
        val x = w * 0.42f + i * w * 0.026f
        val y1 = h * (0.6f - 0.3f * kotlin.math.sin(i / 3f).toFloat() * 0.8f)
        val y2 = h * (0.5f + 0.25f * kotlin.math.cos(i / 2.5f).toFloat())
        if (i == 0) { p.moveTo(x, y1); q.moveTo(x, y2) } else { p.lineTo(x, y1); q.lineTo(x, y2) }
    }
    drawPath(q, alta, style = Stroke(h * 0.02f))
    drawPath(p, acento, style = Stroke(h * 0.03f))
}

internal fun DrawScope.vistaBento(acento: Color) {
    val w = size.width; val h = size.height
    val c = Color.White.copy(alpha = 0.10f)
    drawRect(c, Offset(w * 0.04f, h * 0.08f), Size(w * 0.34f, h * 0.50f))
    drawRect(c, Offset(w * 0.41f, h * 0.08f), Size(w * 0.55f, h * 0.50f))
    for (i in 0..3) drawRect(c, Offset(w * (0.04f + i * 0.235f), h * 0.64f), Size(w * 0.215f, h * 0.28f))
    drawRect(Color.White.copy(alpha = 0.8f), Offset(w * 0.08f, h * 0.25f), Size(w * 0.18f, h * 0.2f))
    drawRect(acento, Offset(w * 0.45f, h * 0.38f), Size(w * 0.33f, h * 0.08f))
    drawRect(Color.White, Offset(w * 0.82f, h * 0.32f), Size(w * 0.01f, h * 0.2f))
}
