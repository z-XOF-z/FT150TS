package com.xof.ft150ts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xof.ft150ts.AgujaPid
import com.xof.ft150ts.Apariencia
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

const val RPM_MAX = 10000

/**
 * true si la página del pager está en pantalla. Las páginas ocultas siguen vivas
 * (para que el mapa no se recargue), pero apagan sus relojes y animaciones.
 */
val LocalPaginaVisible = compositionLocalOf { true }

/** Zona de RPM: baja / media / alta, respecto al punto de cambio. */
fun colorRpm(rpm: Int, shift: Int): Color = when {
    rpm >= shift * 0.9f -> Paleta.rpmAlta
    rpm >= shift * 0.65f -> Paleta.rpmMedia
    else -> Paleta.rpmBaja
}

/**
 * Parpadeo para alertas. Solo crea la animación cuando [activo] es true;
 * así la pantalla no se redibuja 60 veces por segundo sin necesidad.
 */
/**
 * Igual que [parpadeo] pero devuelve el estado: si se lee dentro de un Canvas,
 * solo se redibuja el dibujo, sin recomponer la pantalla.
 */
@Composable
fun parpadeoEstado(activo: Boolean): State<Float> {
    // Fluidez: parpadeo a saltos (encendido/apagado ~5 veces por segundo) en vez de una animación
    // a 120 cuadros por segundo; se ve igual y la pantalla casi no trabaja.
    val alfa = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(activo) {
        if (!activo) { alfa.floatValue = 1f; return@LaunchedEffect }
        var prendido = true
        while (true) {
            alfa.floatValue = if (prendido) 1f else 0.15f
            prendido = !prendido
            delay(180)
        }
    }
    return alfa
}

@Composable
fun parpadeo(activo: Boolean): Float = parpadeoEstado(activo).value

/**
 * Valor que sigue a [objetivo] movido por una aguja con PID (Ajustes > Control PID).
 * Solo trabaja mientras la aguja se mueve y la página está en pantalla; quieta no gasta nada.
 * [hz]: pasos por segundo (30 basta para las barras del LCD).
 */
@Composable
fun pidAnimado(objetivo: () -> Float, hz: Int = 30): State<Float> {
    val visible = LocalPaginaVisible.current
    val activo = Apariencia.pidAguja
    val kp = Apariencia.pidKp
    val ki = Apariencia.pidKi
    val kd = Apariencia.pidKd
    val aguja = remember { AgujaPid() }
    aguja.pid.kp = kp; aguja.pid.ki = ki; aguja.pid.kd = kd
    val salida = remember { mutableFloatStateOf(objetivo()) }
    val obj = rememberUpdatedState(objetivo)
    LaunchedEffect(activo, visible, hz) {
        if (!activo || !visible) {
            // Sin PID: el valor pasa directo
            snapshotFlow { obj.value() }.collect { salida.floatValue = it; aguja.colocar(it) }
            return@LaunchedEffect
        }
        // Pegado a los cuadros de la pantalla (vsync): a 120 Hz da un paso cada 2 cuadros (60/s) sin tirones
        val minimo = (1_000_000_000L / hz) * 9 / 10
        var tAnt = 0L
        while (true) {
            val o = obj.value()
            if (aguja.quieta(o)) {
                aguja.colocar(o)
                salida.floatValue = o
                // Quieta: espera a que cambie el objetivo (no redibuja nada mientras)
                snapshotFlow { obj.value() }.first { kotlin.math.abs(it - o) >= 4f }
                tAnt = 0L
                continue
            }
            val t = withFrameNanos { it }
            if (tAnt != 0L && t - tAnt < minimo) continue
            val dt = if (tAnt == 0L) 1f / hz else ((t - tAnt) / 1e9f).coerceIn(0.004f, 0.034f)
            tAnt = t
            salida.floatValue = aguja.paso(o, dt)
        }
    }
    return salida
}

/**
 * Como collectAsStateWithLifecycle, pero solo se actualiza con la página en pantalla.
 * Las páginas de al lado siguen armadas (cambiar de pestaña es instantáneo) pero no se
 * redibujan 4 veces por segundo sin verse.
 */
@Composable
fun <T> StateFlow<T>.enVivo(): State<T> {
    val visible = LocalPaginaVisible.current
    val estado = remember(this) { mutableStateOf(value) }
    LaunchedEffect(this, visible) {
        if (visible) collect { estado.value = it }
    }
    return estado
}

/** Texto con la letra pixelada (sin acentos, en mayúsculas). */
@Composable
fun EtiquetaPixel(texto: String, color: Color = Paleta.textoSec, tamano: TextUnit = 8.sp, modifier: Modifier = Modifier) {
    Text(
        pixelTexto(texto), color = color, fontSize = tamano, fontFamily = Fuentes.pixel,
        maxLines = 1, overflow = TextOverflow.Clip, modifier = modifier,
    )
}

/** Franjas diagonales de fondo, como las calcomanías del tanque. */
fun Modifier.franjasCarreras(alfa: Float = 0.07f): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val c = Paleta.lima
    for (i in 0 until 3) {
        val x0 = w * 0.55f + i * w * 0.09f
        val p = Path().apply {
            moveTo(x0, 0f)
            lineTo(x0 + w * 0.05f, 0f)
            lineTo(x0 + w * 0.05f - h * 0.35f, h * 0.35f)
            lineTo(x0 - h * 0.35f, h * 0.35f)
            close()
        }
        drawPath(p, c.copy(alpha = alfa * (1f - i * 0.25f)))
    }
}

/** Barra de progreso hecha de bloques (estilo pixel). */
@Composable
fun BarraPixel(progreso: Float, color: Color, modifier: Modifier = Modifier, bloques: Int = 12) {
    val fondoBloque = Paleta.superficie2
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val hueco = 3.dp.toPx()
        val ancho = (size.width - hueco * (bloques - 1)) / bloques
        val llenos = (progreso.coerceIn(0f, 1f) * bloques + 0.001f).toInt()
        for (i in 0 until bloques) {
            drawRect(
                color = if (i < llenos) color else fondoBloque,
                topLeft = Offset(i * (ancho + hueco), 0f),
                size = Size(ancho, size.height),
            )
        }
    }
}

/** Gráfica de barras pixelada (para velocidad o RPM de los últimos minutos). */
@Composable
fun GraficaPixel(
    valores: List<Float>,
    maximo: Float,
    color: Color,
    modifier: Modifier = Modifier,
    colorPorValor: ((Float) -> Color)? = null,   // ej. RPM: verde / ámbar / azul según la zona
) {
    val vacio = Paleta.superficie2.copy(alpha = 0.5f)
    // Rendimiento: antes eran ~900 drawRect por gráfica (60 columnas x 15 filas) y un color por celda. Ahora las celdas
    // se agrupan por color y cada grupo es UNA llamada (drawPoints en modo líneas: cada celda es un trazo horizontal
    // grueso, del alto del bloque). La geometría se arma solo si cambian los datos o el tamaño.
    Spacer(modifier.fillMaxWidth().height(70.dp).drawWithCache {
        val n = 60
        val hueco = 1.5.dp.toPx()
        val ancho = (size.width - hueco * (n - 1)) / n
        val bloque = 3.dp.toPx()
        val filas = (size.height / (bloque + hueco)).toInt().coerceAtLeast(1)
        val datos = valores.takeLast(n)
        val inicio = n - datos.size
        val grupos = LinkedHashMap<Color, ArrayList<Offset>>()
        val vacias = ArrayList<Offset>()
        for (i in 0 until n) {
            val v = if (i >= inicio) datos[i - inicio] else 0f
            val llenas = if (maximo > 0f) ((v / maximo).coerceIn(0f, 1f) * filas + 0.5f).toInt() else 0
            val x = i * (ancho + hueco)
            val lleno = if (llenas > 0) colorPorValor?.invoke(v) ?: color else color
            for (f in 0 until filas) {
                val yc = size.height - (f + 1) * (bloque + hueco) + hueco + bloque / 2
                val lista = if (f < llenas) grupos.getOrPut(lleno) { ArrayList() } else vacias
                lista.add(Offset(x, yc)); lista.add(Offset(x + ancho, yc))
            }
        }
        onDrawBehind {
            if (vacias.isNotEmpty()) drawPoints(vacias, PointMode.Lines, vacio, strokeWidth = bloque, cap = StrokeCap.Butt)
            for ((c, l) in grupos) drawPoints(l, PointMode.Lines, c, strokeWidth = bloque, cap = StrokeCap.Butt)
        }
    })
}

// ------------------------------------------------------------
// Tacómetro clásico (circular, segmentado)
// ------------------------------------------------------------
private const val SEGMENTOS = 48

@Composable
fun MedidorRpm(rpm: Int, shift: Int, vel: Float, marcha: Int, modifier: Modifier = Modifier) {
    val medidor = rememberTextMeasurer()
    val enRojo = rpm > 0 && rpm >= shift
    val alfaRojo = parpadeo(enRojo)
    val estiloNum = remember { TextStyle(color = Paleta.textoSec, fontSize = 13.sp, fontFamily = Fuentes.ui, fontWeight = FontWeight.SemiBold) }
    // Los números 0..10 se miden una sola vez (optimización)
    val numeros = remember(medidor) { (0..10).map { medidor.measure("$it", estiloNum) } }
    val vacio = Paleta.superficie2
    val aro = Paleta.borde
    val zonaAlta = Paleta.rpmAlta

    Box(modifier.aspectRatio(1f).graphicsLayer(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val grosor = size.minDimension * 0.075f
            val radio = size.minDimension / 2f - grosor
            val centro = Offset(size.width / 2f, size.height / 2f)
            val esquina = Offset(centro.x - radio, centro.y - radio)
            val tam = Size(radio * 2, radio * 2)
            val inicio = 135f
            val barrido = 270f
            val paso = barrido / SEGMENTOS
            val hueco = 1.6f
            val fShift = (shift.toFloat() / RPM_MAX).coerceIn(0f, 1f)
            val fRpm = (rpm.toFloat() / RPM_MAX).coerceIn(0f, 1f)
            val encendidos = if (rpm > 0) (fRpm * SEGMENTOS + 0.5f).toInt() else 0

            for (i in 0 until SEGMENTOS) {
                val fSeg = (i + 0.5f) / SEGMENTOS
                val rpmSeg = (fSeg * RPM_MAX).toInt()
                val color = when {
                    i < encendidos -> colorRpm(rpmSeg, shift).let { if (enRojo) it.copy(alpha = alfaRojo) else it }
                    fSeg >= fShift -> zonaAlta.copy(alpha = 0.22f)
                    else -> vacio
                }
                drawArc(color, inicio + i * paso, paso - hueco, false, esquina, tam, style = Stroke(grosor, cap = StrokeCap.Butt))
            }

            drawCircle(aro, radio - grosor * 0.9f, centro, style = Stroke(2f))

            for (i in 0..10) {
                val ang = Math.toRadians((inicio + barrido * i / 10f).toDouble())
                val r = radio - grosor * 1.75f
                val p = Offset(centro.x + (r * cos(ang)).toFloat(), centro.y + (r * sin(ang)).toFloat())
                val res = numeros[i]
                drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
            }

            marcaCambio(centro, radio + grosor * 0.5f, inicio + barrido * fShift, grosor, zonaAlta)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (vel >= 0) "${(vel + 0.5f).toInt()}" else "--",
                color = Paleta.texto, fontSize = 68.sp, fontFamily = Fuentes.logo,
            )
            EtiquetaPixel("km/h", Paleta.lima, 9.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CajaMarcha(marcha)
                Column {
                    Text(if (rpm >= 0) "$rpm" else "--", color = Paleta.texto, fontSize = 18.sp, fontFamily = Fuentes.logo)
                    EtiquetaPixel("rpm", tamano = 7.sp)
                }
            }
        }
    }
}

private fun DrawScope.marcaCambio(centro: Offset, r: Float, anguloGrados: Float, grosor: Float, color: Color) {
    val a = Math.toRadians(anguloGrados.toDouble())
    val ux = cos(a).toFloat()
    val uy = sin(a).toFloat()
    val punta = Offset(centro.x + ux * (r - grosor * 0.9f), centro.y + uy * (r - grosor * 0.9f))
    val base = Offset(centro.x + ux * (r + grosor * 0.3f), centro.y + uy * (r + grosor * 0.3f))
    val px = -uy * grosor * 0.45f
    val py = ux * grosor * 0.45f
    val p = Path().apply {
        moveTo(punta.x, punta.y)
        lineTo(base.x + px, base.y + py)
        lineTo(base.x - px, base.y - py)
        close()
    }
    drawPath(p, color)
}

@Composable
fun CajaMarcha(marcha: Int, tamano: Int = 40) {
    val txt = when { marcha < 0 -> "-"; marcha == 0 -> "N"; else -> "$marcha" }
    val neutral = marcha == 0
    val color = if (neutral) Paleta.verde else Paleta.lima
    Box(
        Modifier.size(tamano.dp).clip(Formas.chica)
            .background(if (neutral) color else Paleta.superficie2)
            .border(2.dp, color, Formas.chica),
        contentAlignment = Alignment.Center,
    ) {
        Text(txt, color = if (neutral) Color.Black else color, fontSize = (tamano * 0.52f).sp, fontFamily = Fuentes.logo)
    }
}

/** Réplica de la tira de 8 LEDs del shift light, con bloques inclinados. */
@Composable
fun BarraShift(rpm: Int, shift: Int, modifier: Modifier = Modifier) {
    val enRojo = rpm > 0 && rpm >= shift
    val alfaRojo = parpadeo(enRojo)
    val baja = Paleta.rpmBaja
    val media = Paleta.rpmMedia
    val alta = Paleta.rpmAlta
    val vacio = Paleta.superficie2
    val inicio = shift * 0.6f
    val n = when {
        rpm <= 0 || rpm <= inicio -> 0
        enRojo -> 8
        else -> ((rpm - inicio) / (shift - inicio) * 8f + 0.5f).toInt().coerceIn(0, 8)
    }
    Canvas(modifier.fillMaxWidth().height(14.dp).graphicsLayer()) {
        val hueco = 6.dp.toPx()
        val incl = size.height * 0.6f
        val ancho = (size.width - hueco * 7 - incl) / 8
        for (i in 0 until 8) {
            val base = when { i < 3 -> baja; i < 6 -> media; else -> alta }
            val color = when {
                enRojo -> alta.copy(alpha = alfaRojo)
                i < n -> base
                else -> vacio
            }
            val x = i * (ancho + hueco)
            val p = Path().apply {
                moveTo(x + incl, 0f)
                lineTo(x + incl + ancho, 0f)
                lineTo(x + ancho, size.height)
                lineTo(x, size.height)
                close()
            }
            drawPath(p, color)
        }
    }
}

/** Tarjeta de dato: etiqueta pixelada, valor grande y línea extra. */
@Composable
fun Tarjeta(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    unidad: String = "",
    color: Color = Paleta.texto,
    extra: String = "",
    progreso: Float? = null,
    compacta: Boolean = false,
) {
    // Compacta: misma información en menos espacio (deja crecer al tablero LCD)
    val pad = if (compacta) PaddingValues(start = 11.dp, end = 9.dp, top = 7.dp, bottom = 7.dp)
    else PaddingValues(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 11.dp)
    Column(
        modifier.panel()
            .drawBehind { drawRect(color.copy(alpha = 0.9f), Offset.Zero, Size(3.dp.toPx(), size.height)) }
            .padding(pad),
    ) {
        EtiquetaPixel(etiqueta, tamano = if (compacta) 6.5.sp else 8.sp)
        Spacer(Modifier.height(if (compacta) 3.dp else 6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, color = color, fontSize = if (compacta) 19.sp else 26.sp, fontFamily = Fuentes.logo, maxLines = 1)
            if (unidad.isNotEmpty()) {
                Text(" $unidad", color = Paleta.textoSec, fontSize = if (compacta) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = if (compacta) 2.dp else 3.dp))
            }
        }
        if (progreso != null) {
            Spacer(Modifier.height(if (compacta) 4.dp else 7.dp))
            BarraPixel(progreso, color)
        }
        if (extra.isNotEmpty()) {
            Spacer(Modifier.height(if (compacta) 2.dp else 5.dp))
            Text(extra, color = Paleta.textoSec, fontSize = if (compacta) 10.5.sp else 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bloque con título. */
@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    val acento = Paleta.lima
    Column(
        modifier.fillMaxWidth().panel().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.35f, 0f); lineTo(size.width, 0f)
                    lineTo(size.width * 0.65f, size.height); lineTo(0f, size.height); close()
                }
                drawPath(p, acento)
            }
            Spacer(Modifier.width(8.dp))
            EtiquetaPixel(titulo, acento, 10.sp)
        }
        contenido()
    }
}

/** Fila "nombre ........ valor". */
@Composable
fun Dato(nombre: String, valor: String, color: Color = Paleta.texto) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(nombre, color = Paleta.textoSec, fontSize = 14.sp)
        Text(valor, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Fila con interruptor. */
@Composable
fun FilaSwitch(
    titulo: String,
    detalle: String,
    activo: Boolean,
    colorActivo: Color = Paleta.lima,
    onCambio: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detalle, color = if (activo && colorActivo == Paleta.rojo) Paleta.rojo else Paleta.textoSec, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = activo,
            onCheckedChange = onCambio,
            colors = SwitchDefaults.colors(checkedTrackColor = colorActivo, checkedThumbColor = Color.Black),
        )
    }
}

/** Selector de dos o más opciones (botones segmentados). */
@Composable
fun Selector(opciones: List<String>, elegido: Int, onElegir: (Int) -> Unit, modifier: Modifier = Modifier) {
    val acento = Paleta.lima
    Row(
        modifier.panel(Formas.boton, conBorde = false).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        opciones.forEachIndexed { i, txt ->
            val sel = i == elegido
            Box(
                Modifier.weight(1f)
                    .clip(Formas.boton)
                    .background(if (sel) acento else Color.Transparent)
                    .clickable { onElegir(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(txt.uppercase(), color = if (sel) Color.Black else Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Botón. [primario] = relleno del color de acento; si no, solo el contorno. */
@Composable
fun BotonDeportivo(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primario: Boolean = true,
    color: Color = Paleta.lima,
    tamanoTexto: TextUnit = 14.sp,
) {
    val forma = Formas.boton
    Box(
        modifier.heightIn(min = 44.dp).clip(forma)
            .background(if (primario) color else Color.Transparent)
            .border(1.5.dp, color, forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto.uppercase(), color = if (primario) Color.Black else color,
            fontSize = tamanoTexto, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botón chico (zoom del mapa, etc.). */
@Composable
fun BotonRedondo(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val forma = Formas.chica
    Box(
        modifier.size(44.dp).clip(forma).background(Paleta.fondo.copy(alpha = 0.85f))
            .border(1.dp, Paleta.lima.copy(alpha = 0.6f), forma).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = Paleta.lima, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Franja roja con la alerta que manda la moto, con rayas de peligro. */
@Composable
fun BannerAlerta(texto: String, modifier: Modifier = Modifier) {
    if (texto.isBlank()) return
    val alfa = parpadeo(true)
    val forma = Formas.chica
    Row(
        modifier.fillMaxWidth().clip(forma).background(Paleta.rojo.copy(alpha = 0.16f))
            .border(1.dp, Paleta.rojo.copy(alpha = 0.7f), forma),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(26.dp).height(44.dp)) {
            val paso = 9.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                val p = Path().apply {
                    moveTo(x, size.height); lineTo(x + size.height, 0f)
                    lineTo(x + size.height + paso / 2, 0f); lineTo(x + paso / 2, size.height); close()
                }
                drawPath(p, Paleta.rojo.copy(alpha = 0.35f + 0.65f * alfa))
                x += paso
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.padding(vertical = 6.dp)) {
            EtiquetaPixel("alerta", Paleta.rojo, 7.sp)
            Spacer(Modifier.height(3.dp))
            Text(texto, color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Pila pixelada con porcentaje (audífonos, casco). */
@Composable
fun BateriaPixel(pct: Int, modifier: Modifier = Modifier) {
    val color = when {
        pct > 50 -> Paleta.verde
        pct > 20 -> Paleta.ambar
        else -> Paleta.rojo
    }
    val vacio = Paleta.superficie2
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(24.dp).height(12.dp)) {
            val borde = 1.5.dp.toPx()
            val punta = 2.dp.toPx()
            drawRect(color, Offset.Zero, Size(size.width - punta, size.height), style = Stroke(borde))
            drawRect(color, Offset(size.width - punta, size.height * 0.3f), Size(punta, size.height * 0.4f))
            val n = 4
            val hueco = 1.dp.toPx()
            val dentro = size.width - punta - borde * 2 - hueco * 2
            val w = (dentro - hueco * (n - 1)) / n
            val llenos = ((pct / 100f) * n + 0.5f).toInt().coerceIn(if (pct > 0) 1 else 0, n)
            for (i in 0 until n) {
                drawRect(
                    if (i < llenos) color else vacio,
                    Offset(borde + hueco + i * (w + hueco), borde + hueco),
                    Size(w, size.height - (borde + hueco) * 2),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text("$pct%", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Testigo pequeño estilo pixel (N, BT, GPS...). */
@Composable
fun Testigo(texto: String, encendido: Boolean, color: Color, modifier: Modifier = Modifier) {
    val forma = Formas.chip
    Box(
        modifier.clip(forma)
            .background(if (encendido) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (encendido) color else Paleta.borde, forma)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        EtiquetaPixel(texto, if (encendido) color else Paleta.textoSec.copy(alpha = 0.5f), 7.sp)
    }
}
