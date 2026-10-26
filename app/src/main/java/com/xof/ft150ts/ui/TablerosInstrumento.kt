package com.xof.ft150ts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.Estado
import com.xof.ft150ts.Moto
import com.xof.ft150ts.Rapida
import com.xof.ft150ts.UbicacionManager
import com.xof.ft150ts.ViajeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay

/*
 * Tableros "de instrumento": la misma idea que el LCD Sport (que es el que gusta). Nada de tarjetas ni texto de app:
 * todo dibujado como un display de verdad (segmentos, puntos, fósforo), con los elementos apagados a la vista, prueba
 * de segmentos al arrancar y negro AMOLED. Ocupan el lugar del LCD en la página (misma columna de la derecha).
 *
 * Rendimiento (igual que el LCD): lo de las RPM va en su propia capa con la geometría armada una vez por tamaño; el
 * resto en una capa en caché de GPU que solo se redibuja cuando cambian sus datos (4 veces por segundo).
 *
 *  - RACE LCD: superbike japonesa 2008-2015 (ZX-10R, R1, CBR1000RR). Tacómetro de escalera, memoria de RPM pico,
 *    5 luces de cambio y modo CARRERA (toca el centro: la marcha se vuelve gigante).
 *  - VFD 85: fluorescente de vacío ochentero (Corvette C4, 300ZX). Fósforo del color del tema con malla, tacómetro en
 *    abanico, barra de velocidad y ventana de mensajes que rota.
 *  - ANILLO: bisel de 60 segmentos tipo reloj Casio / Lexus LFA, con la inclinación en el hueco de abajo.
 */

// ============================================================
// Lo que comparten
// ============================================================

/** Los datos del tablero, leídos solo dentro del dibujo (así no se recompone la pantalla). */
private class DatosInstrumento(
    val rS: State<Rapida>,
    val eS: State<Estado>,
    val vS: State<ViajeInfo>,
    val velS: State<Float>,
    val arranqueS: State<Float>,
    val agujaS: State<Float>,
    val alfaS: State<Float>,
    val excesoS: State<Boolean>,
    val horaS: State<String>,
) {
    fun prueba() = pruebaSegmentos(arranqueS.value)
    fun velocidad(): Float {
        if (prueba()) return 888f
        val r = rS.value
        return if (r.vel >= 0) r.vel else velS.value
    }
}

@Composable
private fun rememberDatosInstrumento(): DatosInstrumento {
    val rS = Moto.rapida.enVivo()
    val eS = Moto.estado.enVivo()
    val vS = Moto.viaje.enVivo()
    val velS = UbicacionManager.velocidad.enVivo()
    val arranqueS = LocalArranque.current
    val agujaS = pidAnimado({ rpmArranque(arranqueS.value, max(0, rS.value.rpm).toFloat()) })
    val excesoS = Moto.excesoVel.enVivo()
    val visible = LocalPaginaVisible.current
    val horaS = remember { mutableStateOf(horaHHMM()) }
    LaunchedEffect(visible) { while (visible) { horaS.value = horaHHMM(); delay(5_000) } }
    // Solo esto recompone: entrar o salir de la zona de cambio / del exceso de velocidad
    val enCambio by remember { derivedStateOf { val r = rS.value; r.rpm > 0 && r.rpm >= r.shift } }
    val exceso by excesoS
    val alfaS = parpadeoEstado((enCambio || exceso) && visible)
    return remember(rS, eS, vS, velS, arranqueS, agujaS, alfaS, excesoS) {
        DatosInstrumento(rS, eS, vS, velS, arranqueS, agujaS, alfaS, excesoS, horaS)
    }
}

private fun horaHHMM(): String = SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())

/** Marco del tablero (mismo que el LCD): proporción 1.7, negro, borde y adornos del tema. */
@Composable
private fun Modifier.marcoInstrumento(): Modifier {
    val acento = Paleta.lima
    val forma = Formas.tarjeta
    val neon = Apariencia.neon
    return this.aspectRatio(1.7f)
        .then(if (neon) Modifier.resplandor(forma, acento) else Modifier)
        .clip(forma).background(Color.Black)
        .border(if (temaPixel) 2.dp else 1.dp, acento.copy(alpha = if (neon) 0.8f else 0.35f), forma)
        .decoracionTema(forma, acento)
}

private val MODOS_DIST = listOf("TRIP", "ODO", "RANGO")

private fun valorDist(v: ViajeInfo, modo: Int): Float = when (modo) {
    0 -> v.km
    1 -> v.odometro
    else -> if (v.autonomia >= 0) v.autonomia.toFloat() else -1f
}

/** Reloj HH:MM de 7 segmentos con sus dos puntos. */
private fun DrawScope.reloj7(hora: String, x: Float, y: Float, sw: Float, sh: Float, sp: Float, on: Color, off: Color, p: Path) {
    for (i in 0 until 4) digito7(hora.getOrElse(i) { ' ' }, x + i * sp + if (i >= 2) sw * 0.46f else 0f, y, sw, sh, on, off, p)
    val lado = sh * 0.12f
    drawRect(on, Offset(x + 2 * sp + sw * 0.04f, y + sh * 0.24f), Size(lado, lado))
    drawRect(on, Offset(x + 2 * sp - sw * 0.04f, y + sh * 0.65f), Size(lado, lado))
}

/** Bloques en columna (de abajo hacia arriba), para temperatura y gasolina. */
private fun DrawScope.columnaBloques(x: Float, yAbajo: Float, ancho: Float, alto: Float, cuantos: Int, llenos: Int, color: Color, apagado: Color, rojoArriba: Boolean) {
    val hueco = alto / cuantos * 0.22f
    val bloque = (alto - hueco * (cuantos - 1)) / cuantos
    for (i in 0 until cuantos) {
        val y = yAbajo - (i + 1) * bloque - i * hueco
        val c = when {
            i >= llenos -> apagado
            rojoArriba && i >= cuantos - 2 -> Paleta.rojo
            else -> color
        }
        drawRect(c, Offset(x, y), Size(ancho, bloque))
    }
}

private fun bloquesTemp(r: Rapida, cuantos: Int) = if (r.temp > -90) (((r.temp - 40f) / 80f) * cuantos).toInt().coerceIn(0, cuantos) else 0
private fun bloquesGas(r: Rapida, cuantos: Int) = if (r.gas >= 0) ((r.gas / 100f) * cuantos + 0.5f).toInt().coerceIn(0, cuantos) else 0

private fun colorBateria(v: Float): Color = when {
    v < 0 -> Paleta.textoSec
    v < 12f -> Paleta.rojo
    v > 15f -> Paleta.ambar
    else -> Paleta.verde
}

// ============================================================
// RACE LCD
// ============================================================

/** Tacómetro de escalera: 40 columnas que crecen de izquierda a derecha, de lado a lado, arriba del tablero. */
private class GeomEscalera(val w: Float, val h: Float) {
    val u = h / 100f
    val n = 40
    val x0 = 1f * u
    val x1 = w - 1f * u
    val paso = (x1 - x0) / n
    val ancho = paso * 0.72f
    val base = 30f * u
    fun alto(i: Int) = 7f * u + 21f * u * (i / (n - 1f)).pow(1.15f)
    fun xRpm(rpm: Float) = x0 + (rpm / RPM_MAX).coerceIn(0f, 1f) * (x1 - x0)
}

@Composable
fun TableroRaceLcd(modifier: Modifier = Modifier) {
    val d = rememberDatosInstrumento()
    val carreraS = rememberSaveable { mutableIntStateOf(0) }   // 0 calle, 1 carrera
    val distS = rememberSaveable { mutableIntStateOf(0) }
    val acento = Paleta.lima
    val ruta = remember { Path() }
    Box(
        modifier.marcoInstrumento().pointerInput(Unit) {
            detectTapGestures(onLongPress = { Apariencia.siguienteTablero() }) { pos ->
                when {
                    pos.y > size.height * 0.84f -> distS.intValue = (distS.intValue + 1) % MODOS_DIST.size
                    pos.y > size.height * 0.34f -> carreraS.intValue = 1 - carreraS.intValue
                }
            }
        },
    ) {
        EscaleraRpm(d, Modifier.fillMaxSize().padding(8.dp))
        Canvas(Modifier.fillMaxSize().padding(8.dp).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val r = d.rS.value
            val e = d.eS.value
            val v = d.vS.value
            val prueba = d.prueba()
            val vel = d.velocidad()
            val carrera = carreraS.intValue == 1
            val u = size.height / 100f
            val w = size.width
            val apagado = Color.White.copy(alpha = 0.06f)
            val sec = Paleta.textoSec
            val blanco = Color(0xFFF2F2EA)
            val g = GeomEscalera(w, size.height)

            // escala x1000 debajo de la escalera
            val pxN = 1.0f * u
            for (k in 0..10) {
                val t = "$k"
                val xc = g.xRpm(k * 1000f).coerceIn(g.x0 + anchoPixel(t, pxN) / 2f, g.x1 - anchoPixel(t, pxN) / 2f)
                textoPixel(t, xc - anchoPixel(t, pxN) / 2f, g.base + 1.6f * u, pxN, if (k * 1000 >= r.shift) Paleta.rpmAlta else sec)
            }
            textoPixel("X1000 R/MIN", 1f * u, 1f * u, 0.72f * u, sec)

            // ---------------- marcha y velocidad ----------------
            val marcha = when { prueba -> "8"; r.marcha < 0 -> "-"; r.marcha == 0 -> "N"; else -> "${r.marcha}" }
            val colorMarcha = if (r.marcha == 0 && !prueba) Paleta.verde else blanco
            val enRojo = d.excesoS.value
            val colorVel = if (enRojo) Paleta.rojo.copy(alpha = 0.35f + 0.65f * d.alfaS.value) else blanco
            if (!carrera) {
                // recuadro GEAR a la izquierda
                val gx0 = 2f * u; val gx1 = 31f * u; val gy0 = 40f * u; val gy1 = 80f * u
                drawRect(acento, Offset(gx0, gy0), Size(gx1 - gx0, 7f * u))
                drawRect(acento, Offset(gx0, gy0), Size(gx1 - gx0, gy1 - gy0), style = Stroke(0.5f * u))
                val pxG = 0.85f * u
                textoPixel("GEAR", (gx0 + gx1) / 2f - anchoPixel("GEAR", pxG) / 2f, gy0 + 1f * u, pxG, Color.Black)
                if (marcha == "N" || marcha == "-") {
                    val px = 3.4f * u
                    textoPixel(marcha, (gx0 + gx1) / 2f - anchoPixel(marcha, px) / 2f, gy0 + 10f * u, px, colorMarcha)
                } else {
                    val mh = 26f * u; val mw = 15f * u
                    digito7(marcha[0], (gx0 + gx1) / 2f - anchoDigito(mw, mh) / 2f, gy0 + 9.5f * u, mw, mh, colorMarcha, apagado, ruta)
                }
                // velocidad grande
                val dh = 40f * u; val dw = 23f * u; val dPaso = dw + 4f * u
                val sx = 37f * u; val sy = 39f * u
                numero7(if (vel >= 0) vel else null, 3, 0, sx, sy, dw, dh, dPaso, colorVel, apagado, ruta)
                var ly = sy
                while (ly < sy + dh) {
                    drawRect(Color.Black.copy(alpha = 0.30f), Offset(sx, ly), Size(3 * dPaso + dh * 0.13f, 0.45f * u))
                    ly += 1.6f * u
                }
                textoPixel(if (e.perfil == 1) "SPT" else "ECO", 127f * u, 40f * u, 0.9f * u, if (e.perfil == 1) Paleta.rpmAlta else Paleta.verde)
                textoPixel("CALLE", 127f * u, 52f * u, 0.55f * u, sec)
                textoPixel("KM/H", 127f * u, 72f * u, 0.85f * u, acento)
            } else {
                // CARRERA: la marcha gigante al centro, la velocidad chica a la izquierda
                val mh = 41f * u; val mw = 25f * u
                val mx = w / 2f - anchoDigito(mw, mh) / 2f
                if (marcha == "N" || marcha == "-") {
                    val px = 5.4f * u
                    textoPixel(marcha, w / 2f - anchoPixel(marcha, px) / 2f, 42f * u, px, colorMarcha)
                } else digito7(marcha[0], mx, 41.5f * u, mw, mh, colorMarcha, apagado, ruta)
                drawRect(acento.copy(alpha = 0.55f), Offset(mx - 6f * u, 40f * u), Size(anchoDigito(mw, mh) + 12f * u, 44.5f * u), style = Stroke(0.4f * u))
                val sh = 18f * u; val sw = 11f * u
                numero7(if (vel >= 0) vel else null, 3, 0, 3f * u, 42f * u, sw, sh, sw + 2.2f * u, colorVel, apagado, ruta)
                textoPixel("KM/H", 3f * u, 63f * u, 0.85f * u, acento)
                textoPixel("CARRERA", 3f * u, 72f * u, 0.62f * u, Paleta.rpmAlta)
                textoPixel(if (e.perfil == 1) "SPT" else "ECO", 3f * u, 77f * u, 0.75f * u, if (e.perfil == 1) Paleta.rpmAlta else Paleta.verde)
            }

            // ---------------- temperatura y gasolina (columnas de bloques a la derecha) ----------------
            val tL = if (prueba) 8 else bloquesTemp(r, 8)
            val gL = if (prueba) 8 else bloquesGas(r, 8)
            columnaBloques(151f * u, 76f * u, 6f * u, 36f * u, 8, tL, acento, apagado, rojoArriba = r.temp > 110f)
            columnaBloques(160f * u, 76f * u, 6f * u, 36f * u, 8, gL, if (r.gas in 0..10) Paleta.rojo else acento, apagado, rojoArriba = false)
            textoPixel("T", 152.5f * u, 77.5f * u, 0.7f * u, sec)
            textoPixel("F", 161.5f * u, 77.5f * u, 0.7f * u, sec)

            // ---------------- fila de abajo: reloj · trip · batería · Li-Ion ----------------
            val by = 88f * u; val sh = 9f * u; val sw = 5.4f * u; val sp = 6.8f * u
            reloj7(if (prueba) "8888" else d.horaS.value, 1f * u, by, sw, sh, sp, acento, apagado, ruta)
            val modo = distS.intValue
            textoPixel(MODOS_DIST[modo], 33f * u, by, 0.7f * u, acento)
            textoPixel("KM", 33f * u, by + 5f * u, 0.6f * u, sec)
            numero7(valorDist(v, modo), 5, if (modo == 2) 0 else 1, 46f * u, by, sw, sh, sp, Paleta.texto, apagado, ruta)
            val cb = colorBateria(r.vMoto)
            textoPixel("BAT", 87f * u, by, 0.6f * u, sec)
            textoPixel("V", 87f * u, by + 5f * u, 0.6f * u, sec)
            numero7(if (r.vMoto >= 0) r.vMoto else null, 3, 1, 98f * u, by, sw, sh, sp, cb, apagado, ruta)
            textoPixel("LI", w - 41f * u, by, 0.6f * u, sec)
            textoPixel("ION", w - 41f * u, by + 5f * u, 0.6f * u, sec)
            numero7(if (e.vLipo >= 0) e.vLipo else null, 3, 2, w - 28f * u, by, sw, sh, sp, acento, apagado, ruta)
        }
    }
}

/** Escalera de RPM + luces de cambio + memoria de pico, en su propia capa (lo único que se mueve 30 veces por segundo). */
@Composable
private fun EscaleraRpm(d: DatosInstrumento, modifier: Modifier) {
    val pico = remember { floatArrayOf(0f, 0f) }   // valor, cuándo (ms)
    Box(modifier.graphicsLayer().drawWithCache {
        val g = GeomEscalera(size.width, size.height)
        val u = g.u
        val bloque = 1.7f * u
        val hueco = 0.6f * u
        val columnas = Array(g.n) { i ->
            val xb = g.x0 + i * g.paso
            val cuantos = max(1, (g.alto(i) / (bloque + hueco)).toInt())
            Path().apply {
                for (b in 0 until cuantos) {
                    val y = g.base - (b + 1) * bloque - b * hueco
                    addRect(androidx.compose.ui.geometry.Rect(xb, y, xb + g.ancho, y + bloque))
                }
            }
        }
        val rpmCol = IntArray(g.n) { i -> ((i + 0.5f) / g.n * RPM_MAX).toInt() }
        val apagado = Color.White.copy(alpha = 0.06f)
        val baja = Paleta.rpmBaja
        val media = Paleta.rpmMedia
        val alta = Paleta.rpmAlta
        val verde = Paleta.verde
        val ambar = Paleta.ambar
        val rojo = Paleta.rojo
        val blanco = Color(0xFFF2F2EA)
        val ledW = 7f * u
        val ledH = 4.2f * u
        onDrawBehind {
            val r = d.rS.value
            val shift = r.shift.toFloat()
            val aguja = d.agujaS.value
            val enRojo = aguja >= shift
            val alfa = if (enRojo) d.alfaS.value else 1f
            val encendidas = if (aguja > 1f) ((aguja / RPM_MAX) * g.n).toInt().coerceIn(0, g.n) else 0
            // memoria de pico: se queda 3 s y luego baja
            val ahora = android.os.SystemClock.uptimeMillis().toFloat()
            if (aguja >= pico[0] || aguja < 1f || pruebaSegmentos(d.arranqueS.value)) { pico[0] = aguja; pico[1] = ahora }
            else if (ahora - pico[1] > 3000f) pico[0] = aguja
            val colPico = if (ahora - pico[1] <= 3000f) ((pico[0] / RPM_MAX) * g.n).toInt() - 1 else -1
            for (i in 0 until g.n) {
                val rc = rpmCol[i]
                val zona = when { rc >= shift * 0.9f -> alta; rc >= shift * 0.65f -> media; else -> baja }
                when {
                    i < encendidas -> drawPath(columnas[i], zona, alpha = alfa)
                    i == colPico && colPico >= encendidas + 1 -> drawPath(columnas[i], blanco, alpha = 0.85f)
                    else -> drawPath(columnas[i], if (rc >= shift) alta.copy(alpha = 0.16f) else apagado)
                }
            }
            // 5 luces de cambio (como el CBR): verde, verde, ámbar, ámbar, rojo
            val umbrales = floatArrayOf(0.80f, 0.85f, 0.90f, 0.95f, 1.0f)
            for (k in 0 until 5) {
                val c = when (k) { 0, 1 -> verde; 2, 3 -> ambar; else -> rojo }
                val x = 1f * u + k * (ledW + 1.4f * u)
                val prendido = aguja > 1f && aguja >= shift * umbrales[k]
                drawRect(if (prendido) c.copy(alpha = alfa) else c.copy(alpha = 0.10f), Offset(x, 6f * u), Size(ledW, ledH))
            }
        }
    })
}

// ============================================================
// VFD 85
// ============================================================

private const val AMBAR_VFD = 0xFFFFB000

/** Dígito de 7 segmentos recto (sin inclinación) con puntas biseladas, como los VFD de los 80. */
private fun DrawScope.digitoVfd(c: Char, x: Float, y: Float, w: Float, h: Float, on: Color, off: Color, p: Path) {
    // Reutiliza la forma del LCD pero sin inclinar: se dibuja con una transformación que deshace la inclinación
    val t = w * 0.19f
    val g = t * 0.25f
    val mitad = h / 2f
    val activos = SEG7_VFD[c] ?: ""
    for (s in "abcdefg") {
        p.reset()
        when (s) {
            'a' -> hexH(p, x + t / 2f + g, x + w - t / 2f - g, y + t / 2f, t)
            'g' -> hexH(p, x + t / 2f + g, x + w - t / 2f - g, y + mitad, t)
            'd' -> hexH(p, x + t / 2f + g, x + w - t / 2f - g, y + h - t / 2f, t)
            'f' -> hexV(p, x + t / 2f, y + t / 2f + g, y + mitad - g, t)
            'e' -> hexV(p, x + t / 2f, y + mitad + g, y + h - t / 2f - g, t)
            'b' -> hexV(p, x + w - t / 2f, y + t / 2f + g, y + mitad - g, t)
            'c' -> hexV(p, x + w - t / 2f, y + mitad + g, y + h - t / 2f - g, t)
        }
        drawPath(p, if (s in activos) on else off)
    }
}

private val SEG7_VFD = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
    '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
    '-' to "g", ' ' to "",
)

private fun hexH(p: Path, x0: Float, x1: Float, yc: Float, t: Float) {
    val e = t / 2f
    p.moveTo(x0, yc); p.lineTo(x0 + e, yc - e); p.lineTo(x1 - e, yc - e); p.lineTo(x1, yc); p.lineTo(x1 - e, yc + e); p.lineTo(x0 + e, yc + e); p.close()
}

private fun hexV(p: Path, xc: Float, y0: Float, y1: Float, t: Float) {
    val e = t / 2f
    p.moveTo(xc, y0); p.lineTo(xc + e, y0 + e); p.lineTo(xc + e, y1 - e); p.lineTo(xc, y1); p.lineTo(xc - e, y1 - e); p.lineTo(xc - e, y0 + e); p.close()
}

private fun DrawScope.numeroVfd(valor: Float?, digitos: Int, decimales: Int, x: Float, y: Float, w: Float, h: Float, paso: Float, on: Color, off: Color, p: Path) {
    val txt = if (valor == null || valor < 0f) "-".repeat(digitos) else {
        val esc = 10.0.pow(decimales).toFloat()
        val tope = 10.0.pow(digitos).toFloat() - 1f
        var s = minOf(valor * esc + 0.5f, tope).toLong().toString()
        while (s.length < decimales + 1) s = "0$s"
        s.padStart(digitos, ' ')
    }
    val iPunto = digitos - decimales
    val hueco = if (decimales > 0) w * 0.40f else 0f
    for (i in 0 until digitos) digitoVfd(txt[i], x + i * paso + if (i >= iPunto) hueco else 0f, y, w, h, on, off, p)
    if (decimales > 0) {
        val lado = w * 0.2f
        drawRect(on, Offset(x + (iPunto - 1) * paso + w + (paso - w + hueco) / 2f - lado / 2f, y + h - lado), Size(lado, lado))
    }
}

/** Mensajes de la ventana alfanumérica (rota cada 4 s; toca para pasar al siguiente). */
private fun mensajeVfd(i: Int, r: Rapida, e: Estado, v: ViajeInfo): String = when (i) {
    0 -> "TRIP" + fmt(v.km, 1).padStart(8)
    1 -> "RANGO" + (if (v.autonomia >= 0) "${v.autonomia}KM" else "--KM").padStart(7)
    2 -> "BATT" + (if (r.vMoto >= 0) fmt(r.vMoto, 1) + "V" else "--.-V").padStart(8)
    3 -> "LIION" + (if (e.vLipo >= 0) fmt(e.vLipo, 2) + "V" else "-.--V").padStart(7)
    else -> "ODO" + fmt(v.odometro, 1).padStart(9)
}

@Composable
fun TableroVfd(modifier: Modifier = Modifier) {
    val d = rememberDatosInstrumento()
    val visible = LocalPaginaVisible.current
    val msgS = rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(visible) { while (visible) { delay(4_000); msgS.intValue = (msgS.intValue + 1) % 5 } }
    val ruta = remember { Path() }
    Box(
        modifier.marcoInstrumento().pointerInput(Unit) {
            detectTapGestures(onLongPress = { Apariencia.siguienteTablero() }) { pos -> if (pos.y > size.height * 0.70f) msgS.intValue = (msgS.intValue + 1) % 5 }
        },
    ) {
        AbanicoRpm(d, Modifier.fillMaxSize().padding(8.dp))
        Canvas(Modifier.fillMaxSize().padding(8.dp).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val r = d.rS.value
            val e = d.eS.value
            val v = d.vS.value
            val prueba = d.prueba()
            val vel = d.velocidad()
            val u = size.height / 100f
            val w = size.width
            val fosforo = Paleta.lima
            val apagado = fosforo.copy(alpha = 0.10f)
            val ambar = Color(AMBAR_VFD)

            // escala del tacómetro (impresa en ámbar)
            val g = GeomAbanico(w, size.height)
            for (k in 0..10 step 2) {
                val t = "$k"
                val px = 0.9f * u
                val xc = g.xRpm(k * 1000f).coerceIn(g.x0 + anchoPixel(t, px) / 2f, g.x1 - anchoPixel(t, px) / 2f)
                textoPixel(t, xc - anchoPixel(t, px) / 2f, g.base + 1.5f * u, px, if (k * 1000 >= r.shift) Paleta.rojo else ambar)
            }
            textoPixel("RPM X1000", 2f * u, 2f * u, 0.7f * u, ambar)

            // barra de velocidad 0-140 (60 segmentos finos)
            val bx0 = 3f * u; val bx1 = 96f * u; val by = 43f * u; val bh = 5f * u
            val nb = 56
            val pb = (bx1 - bx0) / nb
            val llenos = if (vel > 0) ((vel / 140f) * nb).toInt().coerceIn(0, nb) else 0
            for (i in 0 until nb) drawRect(if (i < llenos) fosforo else apagado, Offset(bx0 + i * pb, by), Size(pb * 0.55f, bh))
            for (k in 0..140 step 20) {
                val t = "$k"
                textoPixel(t, bx0 + (k / 140f) * (bx1 - bx0) - anchoPixel(t, 0.6f * u) / 2f, by + bh + 1f * u, 0.6f * u, ambar)
            }

            // velocidad grande en segmentos rectos
            val dh = 30f * u; val dw = 19f * u; val dp = dw + 4f * u
            val enRojo = d.excesoS.value
            val colorVel = if (enRojo) Paleta.rojo.copy(alpha = 0.35f + 0.65f * d.alfaS.value) else fosforo
            numeroVfd(if (vel >= 0) vel else null, 3, 0, 8f * u, 57f * u, dw, dh, dp, colorVel, apagado, ruta)
            textoPixel("KM/H", 80f * u, 82f * u, 0.85f * u, ambar)

            // marcha en su marco impreso
            val gx0 = 104f * u; val gx1 = 128f * u; val gy0 = 41f * u; val gy1 = 73f * u
            drawRect(ambar.copy(alpha = 0.8f), Offset(gx0, gy0), Size(gx1 - gx0, gy1 - gy0), style = Stroke(0.4f * u))
            textoPixel("GEAR", gx0 + 1.2f * u, gy0 + 1.2f * u, 0.6f * u, ambar)
            val marcha = when { prueba -> "8"; r.marcha < 0 -> "-"; r.marcha == 0 -> "N"; else -> "${r.marcha}" }
            if (marcha == "N" || marcha == "-") {
                val px = 3f * u
                textoPixel(marcha, (gx0 + gx1) / 2f - anchoPixel(marcha, px) / 2f, gy0 + 7f * u, px, if (marcha == "N") Paleta.verde else fosforo)
            } else digitoVfd(marcha[0], (gx0 + gx1) / 2f - 6.5f * u, gy0 + 6.5f * u, 13f * u, 22f * u, fosforo, apagado, ruta)

            // temperatura y gasolina: columnas cortas
            val tL = if (prueba) 8 else bloquesTemp(r, 8)
            val gL = if (prueba) 8 else bloquesGas(r, 8)
            columnaBloques(136f * u, 73f * u, 9f * u, 25f * u, 8, tL, fosforo, apagado, rojoArriba = r.temp > 110f)
            columnaBloques(152f * u, 73f * u, 9f * u, 25f * u, 8, gL, if (r.gas in 0..10) Paleta.rojo else fosforo, apagado, rojoArriba = false)
            textoPixel("TEMP", 133.5f * u, 42f * u, 0.6f * u, ambar)
            textoPixel("FUEL", 149.5f * u, 42f * u, 0.6f * u, ambar)

            // ventana de mensajes de 16 caracteres + reloj
            val mx0 = 104f * u; val my0 = 77f * u
            drawRect(ambar.copy(alpha = 0.8f), Offset(mx0, my0), Size(w - 2f * u - mx0, 11f * u), style = Stroke(0.35f * u))
            val msg = if (prueba) "888888888888" else mensajeVfd(msgS.intValue, r, e, v)
            val pxM = 0.85f * u
            textoPixel(msg.take(12), mx0 + 1.6f * u, my0 + 2.6f * u, pxM, fosforo)
            textoPixel(if (e.perfil == 1) "SPORT" else "ECO", 104f * u, 92f * u, 0.7f * u, if (e.perfil == 1) Paleta.rojo else ambar)
            val hora = if (prueba) "8888" else d.horaS.value
            for (i in 0 until 4) digitoVfd(hora.getOrElse(i) { ' ' }, w - 30f * u + i * 6.6f * u + if (i >= 2) 2f * u else 0f, 90f * u, 5f * u, 8.5f * u, fosforo, apagado, ruta)
        }
        // malla del VFD encima de todo: una textura en mosaico, un solo dibujo
        MallaVfd(Modifier.fillMaxSize())
    }
}

/** Tacómetro en abanico (C4 / 300ZX): barras que crecen y se abren hacia los lados. */
private class GeomAbanico(val w: Float, val h: Float) {
    val u = h / 100f
    val n = 32
    val x0 = 3f * u
    val x1 = w - 3f * u
    val paso = (x1 - x0) / n
    val ancho = paso * 0.62f
    val base = 32f * u
    val centro = (x0 + x1) / 2f
    fun alto(i: Int) = 6f * u + 18f * u * (i / (n - 1f))
    fun xRpm(rpm: Float) = x0 + (rpm / RPM_MAX).coerceIn(0f, 1f) * (x1 - x0)
}

@Composable
private fun AbanicoRpm(d: DatosInstrumento, modifier: Modifier) {
    Box(modifier.graphicsLayer().drawWithCache {
        val g = GeomAbanico(size.width, size.height)
        val barras = Array(g.n) { i ->
            val xb = g.x0 + i * g.paso
            val alto = g.alto(i)
            val abre = (xb + g.ancho / 2f - g.centro) * 0.05f   // se inclinan hacia afuera
            Path().apply {
                moveTo(xb, g.base); lineTo(xb + g.ancho, g.base)
                lineTo(xb + g.ancho + abre, g.base - alto); lineTo(xb + abre, g.base - alto); close()
            }
        }
        val rpmCol = IntArray(g.n) { i -> ((i + 0.5f) / g.n * RPM_MAX).toInt() }
        val fosforo = Paleta.lima
        val apagado = fosforo.copy(alpha = 0.10f)
        val rojo = Paleta.rojo
        val ambar = Color(AMBAR_VFD)
        val halo = Stroke(0.9f * g.u)
        onDrawBehind {
            val shift = d.rS.value.shift.toFloat()
            val aguja = d.agujaS.value
            val alfa = if (aguja >= shift) d.alfaS.value else 1f
            val encendidas = if (aguja > 1f) ((aguja / RPM_MAX) * g.n).toInt().coerceIn(0, g.n) else 0
            for (i in 0 until g.n) {
                val rc = rpmCol[i]
                val c = when { rc >= shift -> rojo; rc >= shift * 0.85f -> ambar; else -> fosforo }
                if (i < encendidas) {
                    drawPath(barras[i], c, alpha = 0.25f * alfa, style = halo)   // brillo del fósforo (sin desenfoque)
                    drawPath(barras[i], c, alpha = alfa)
                } else drawPath(barras[i], if (rc >= shift) rojo.copy(alpha = 0.14f) else apagado)
            }
        }
    })
}

/** Malla fina del VFD: mosaico de 3x3 px con una línea oscura, dibujado una vez por tamaño. */
@Composable
private fun MallaVfd(modifier: Modifier) {
    Box(modifier.graphicsLayer().drawWithCache {
        val bmp = android.graphics.Bitmap.createBitmap(3, 3, android.graphics.Bitmap.Config.ARGB_8888)
        val oscuro = android.graphics.Color.argb(70, 0, 0, 0)
        for (i in 0 until 3) { bmp.setPixel(i, 2, oscuro); bmp.setPixel(2, i, oscuro) }
        val pincel = ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
        onDrawBehind { drawRect(pincel) }
    })
}

// ============================================================
// ANILLO
// ============================================================

private const val SEG_ANILLO = 60
private const val INICIO_ANILLO = 135f
private const val BARRIDO_ANILLO = 270f

private class GeomAnillo(val w: Float, val h: Float) {
    val u = h / 100f
    val cx = 50f * u
    val cy = 51f * u
    val rExt = 47f * u
    val rInt = 38f * u
}

private fun trapecio(cx: Float, cy: Float, r0: Float, r1: Float, a0: Float, a1: Float): Path {
    fun pt(r: Float, a: Float): Offset { val rad = a * PI.toFloat() / 180f; return Offset(cx + r * cos(rad), cy + r * sin(rad)) }
    val p0 = pt(r0, a0); val p1 = pt(r1, a0); val p2 = pt(r1, a1); val p3 = pt(r0, a1)
    return Path().apply { moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close() }
}

@Composable
fun TableroAnillo(modifier: Modifier = Modifier) {
    val d = rememberDatosInstrumento()
    val distS = rememberSaveable { mutableIntStateOf(0) }
    val acento = Paleta.lima
    val ruta = remember { Path() }
    Box(
        modifier.marcoInstrumento().pointerInput(Unit) {
            detectTapGestures(onLongPress = { Apariencia.siguienteTablero() }) { pos -> if (pos.x > size.width * 0.6f) distS.intValue = (distS.intValue + 1) % MODOS_DIST.size }
        },
    ) {
        AnilloRpm(d, Modifier.fillMaxSize().padding(8.dp))
        Canvas(Modifier.fillMaxSize().padding(8.dp).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val r = d.rS.value
            val e = d.eS.value
            val v = d.vS.value
            val prueba = d.prueba()
            val vel = d.velocidad()
            val u = size.height / 100f
            val w = size.width
            val g = GeomAnillo(w, size.height)
            val apagado = Color.White.copy(alpha = 0.06f)
            val sec = Paleta.textoSec
            val blanco = Color(0xFFF2F2EA)

            // números de la escala por dentro del anillo
            for (k in 0..10) {
                val a = (INICIO_ANILLO + BARRIDO_ANILLO * k / 10f) * PI.toFloat() / 180f
                val t = "$k"
                val rr = g.rInt - 4f * u
                val px = 0.85f * u
                textoPixel(t, g.cx + rr * cos(a) - anchoPixel(t, px) / 2f, g.cy + rr * sin(a) - 3.5f * px, px,
                    if (k * 1000 >= r.shift) Paleta.rpmAlta else sec)
            }
            // marcha arriba, velocidad al centro
            val marcha = when { prueba -> "8"; r.marcha < 0 -> "-"; r.marcha == 0 -> "N"; else -> "${r.marcha}" }
            if (marcha == "N" || marcha == "-") {
                val px = 1.6f * u
                textoPixel(marcha, g.cx - anchoPixel(marcha, px) / 2f, g.cy - 25f * u, px, if (marcha == "N") Paleta.verde else blanco)
            } else digito7(marcha[0], g.cx - anchoDigito(7f * u, 12f * u) / 2f, g.cy - 26f * u, 7f * u, 12f * u, acento, apagado, ruta)
            val dh = 21f * u; val dw = 12.5f * u; val dp = dw + 2.6f * u
            val ancho = 2 * dp + anchoDigito(dw, dh)
            val enRojo = d.excesoS.value
            val colorVel = if (enRojo) Paleta.rojo.copy(alpha = 0.35f + 0.65f * d.alfaS.value) else blanco
            numero7(if (vel >= 0) vel else null, 3, 0, g.cx - ancho / 2f, g.cy - 9f * u, dw, dh, dp, colorVel, apagado, ruta)
            textoPixel("KM/H", g.cx - anchoPixel("KM/H", 0.8f * u) / 2f, g.cy + 14f * u, 0.8f * u, acento)
            textoPixel(if (e.perfil == 1) "SPT" else "ECO", g.cx - anchoPixel("ECO", 0.7f * u) / 2f, g.cy + 21f * u, 0.7f * u,
                if (e.perfil == 1) Paleta.rpmAlta else Paleta.verde)

            // ---------------- columna Casio: cuatro ventanas ----------------
            val wx0 = 104f * u; val wx1 = w - 1.5f * u
            val altoV = 21f * u
            fun ventana(i: Int, titulo: String): Float {
                val y = 3f * u + i * (altoV + 2.6f * u)
                drawRect(acento.copy(alpha = 0.55f), Offset(wx0, y), Size(wx1 - wx0, altoV), style = Stroke(0.35f * u))
                textoPixel(titulo, wx0 + 1.6f * u, y + 1.6f * u, 0.75f * u, acento)
                return y
            }
            val sw = 6.6f * u; val sh = 11f * u; val sp = 8.2f * u
            // 1 hora
            val y1 = ventana(0, "HORA")
            reloj7(if (prueba) "8888" else d.horaS.value, wx1 - 4 * sp - 4f * u, y1 + 8f * u, sw, sh, sp, blanco, apagado, ruta)
            // 2 distancia (toca para TRIP / ODO / RANGO)
            val modo = distS.intValue
            val y2 = ventana(1, MODOS_DIST[modo] + " KM")
            numero7(valorDist(v, modo), 5, if (modo == 2) 0 else 1, wx1 - 5 * sp - 4.5f * u, y2 + 8f * u, sw, sh, sp, blanco, apagado, ruta)
            // 3 voltajes
            val y3 = ventana(2, "BAT V")
            numero7(if (r.vMoto >= 0) r.vMoto else null, 3, 1, wx0 + 2f * u, y3 + 8f * u, sw, sh, sp, colorBateria(r.vMoto), apagado, ruta)
            textoPixel("LI-ION", wx1 - anchoPixel("LI-ION", 0.6f * u) - 1.6f * u, y3 + 1.6f * u, 0.6f * u, sec)
            numero7(if (e.vLipo >= 0) e.vLipo else null, 3, 2, wx1 - 3 * 5.6f * u - 5f * u, y3 + 10f * u, 4.4f * u, 8f * u, 5.6f * u, acento, apagado, ruta)
            // 4 temperatura y gasolina
            val y4 = ventana(3, "TEMP   FUEL")
            val tL = if (prueba) 8 else bloquesTemp(r, 8)
            val gL = if (prueba) 8 else bloquesGas(r, 8)
            filaBloques(wx0 + 2f * u, y4 + 9.5f * u, (wx1 - wx0) / 2f - 4f * u, 7f * u, 8, tL, acento, apagado, r.temp > 110f)
            filaBloques((wx0 + wx1) / 2f + 1f * u, y4 + 9.5f * u, (wx1 - wx0) / 2f - 4f * u, 7f * u, 8, gL,
                if (r.gas in 0..10) Paleta.rojo else acento, apagado, false)
        }
    }
}

private fun DrawScope.filaBloques(x: Float, y: Float, ancho: Float, alto: Float, cuantos: Int, llenos: Int, color: Color, apagado: Color, rojoFinal: Boolean) {
    val hueco = ancho / cuantos * 0.25f
    val b = (ancho - hueco * (cuantos - 1)) / cuantos
    for (i in 0 until cuantos) {
        val c = when { i >= llenos -> apagado; rojoFinal && i >= cuantos - 2 -> Paleta.rojo; else -> color }
        drawRect(c, Offset(x + i * (b + hueco), y), Size(b, alto))
    }
}

/** El anillo de 60 segmentos y la inclinación (en el hueco de abajo), en su propia capa. */
@Composable
private fun AnilloRpm(d: DatosInstrumento, modifier: Modifier) {
    Box(modifier.graphicsLayer().drawWithCache {
        val g = GeomAnillo(size.width, size.height)
        val u = g.u
        val paso = BARRIDO_ANILLO / SEG_ANILLO
        val hueco = paso * 0.22f
        val segmentos = Array(SEG_ANILLO) { i ->
            val a0 = INICIO_ANILLO + i * paso + hueco / 2f
            trapecio(g.cx, g.cy, g.rInt, g.rExt, a0, a0 + paso - hueco)
        }
        // inclinación: 6 segmentos por lado en el hueco de abajo (90° = abajo al centro), de 0 a 50°
        val nIncl = 6
        val pasoI = 40f / nIncl
        val inclDer = Array(nIncl) { k -> trapecio(g.cx, g.cy, g.rInt + 2f * u, g.rExt - 2f * u, 88f - (k + 1) * pasoI + 0.8f, 88f - k * pasoI) }
        val inclIzq = Array(nIncl) { k -> trapecio(g.cx, g.cy, g.rInt + 2f * u, g.rExt - 2f * u, 92f + k * pasoI, 92f + (k + 1) * pasoI - 0.8f) }
        val rpmSeg = IntArray(SEG_ANILLO) { i -> ((i + 0.5f) / SEG_ANILLO * RPM_MAX).toInt() }
        val apagado = Color.White.copy(alpha = 0.07f)
        val acento = Paleta.lima
        val media = Paleta.rpmMedia
        val alta = Paleta.rpmAlta
        val ambar = Paleta.ambar
        onDrawBehind {
            val r = d.rS.value
            val shift = r.shift.toFloat()
            val aguja = d.agujaS.value
            val alfa = if (aguja >= shift) d.alfaS.value else 1f
            val encendidos = if (aguja > 1f) ((aguja / RPM_MAX) * SEG_ANILLO).toInt().coerceIn(0, SEG_ANILLO) else 0
            for (i in 0 until SEG_ANILLO) {
                val rc = rpmSeg[i]
                val c = when { rc >= shift * 0.9f -> alta; rc >= shift * 0.7f -> media; else -> acento }
                if (i < encendidos) drawPath(segmentos[i], c, alpha = alfa)
                else drawPath(segmentos[i], if (rc >= shift) alta.copy(alpha = 0.16f) else apagado)
            }
            val prueba = pruebaSegmentos(d.arranqueS.value)
            val incl = if (prueba) 50f else r.incl
            val llenos = if (incl > 0f) ((incl / 50f) * nIncl + 0.5f).toInt().coerceIn(0, nIncl) else 0
            for (k in 0 until nIncl) {
                val c = if (k < llenos) (if (k >= nIncl - 2) Paleta.rojo else ambar) else apagado
                drawPath(inclDer[k], c); drawPath(inclIzq[k], c)
            }
        }
    })
}

// ============================================================
// Vistas previas (Ajustes)
// ============================================================

internal fun DrawScope.vistaRaceLcd(acento: Color, alta: Color) {
    val w = size.width; val h = size.height
    val n = 16
    val paso = w * 0.9f / n
    for (i in 0 until n) {
        val alto = h * (0.06f + 0.22f * i / (n - 1))
        val c = if (i < 11) (if (i > 9) alta else acento) else Color.White.copy(alpha = 0.08f)
        drawRect(c, Offset(w * 0.05f + i * paso, h * 0.32f - alto), Size(paso * 0.7f, alto))
    }
    val colores = listOf(Paleta.verde, Paleta.verde, Paleta.ambar, Paleta.ambar, Paleta.rojo)
    for (k in 0 until 5) drawRect(colores[k].copy(alpha = if (k < 3) 1f else 0.15f), Offset(w * 0.05f + k * w * 0.05f, h * 0.04f), Size(w * 0.04f, h * 0.05f))
    val p = Path()
    val fantasma = Color.White.copy(alpha = 0.07f)
    digito7('4', w * 0.40f, h * 0.42f, h * 0.24f, h * 0.42f, Color.White, fantasma, p)
    drawRect(acento, Offset(w * 0.34f, h * 0.40f), Size(w * 0.30f, h * 0.48f), style = Stroke(2f))
    digito7('9', w * 0.06f, h * 0.48f, h * 0.12f, h * 0.22f, Color.White, fantasma, p)
    digito7('8', w * 0.06f + h * 0.16f, h * 0.48f, h * 0.12f, h * 0.22f, Color.White, fantasma, p)
}

internal fun DrawScope.vistaVfd(acento: Color) {
    val w = size.width; val h = size.height
    val n = 16
    val paso = w * 0.9f / n
    val ambar = Color(AMBAR_VFD)
    for (i in 0 until n) {
        val alto = h * (0.06f + 0.18f * i / (n - 1))
        drawRect(if (i < 10) acento else acento.copy(alpha = 0.12f), Offset(w * 0.05f + i * paso, h * 0.30f - alto), Size(paso * 0.6f, alto))
    }
    for (i in 0 until 24) drawRect(if (i < 14) acento else acento.copy(alpha = 0.12f), Offset(w * 0.05f + i * w * 0.022f, h * 0.38f), Size(w * 0.012f, h * 0.06f))
    val p = Path()
    digitoVfd('7', w * 0.06f, h * 0.52f, h * 0.20f, h * 0.36f, acento, acento.copy(alpha = 0.12f), p)
    digitoVfd('2', w * 0.06f + h * 0.26f, h * 0.52f, h * 0.20f, h * 0.36f, acento, acento.copy(alpha = 0.12f), p)
    drawRect(ambar, Offset(w * 0.58f, h * 0.70f), Size(w * 0.38f, h * 0.14f), style = Stroke(2f))
    textoPixel("TRIP 12.4", w * 0.60f, h * 0.73f, h * 0.012f, acento)
    drawRect(ambar, Offset(w * 0.62f, h * 0.38f), Size(w * 0.14f, h * 0.26f), style = Stroke(2f))
}

internal fun DrawScope.vistaAnillo(acento: Color, alta: Color) {
    val w = size.width; val h = size.height
    val cx = w * 0.30f; val cy = h * 0.52f
    val rExt = h * 0.44f; val rInt = h * 0.34f
    val paso = 270f / 30
    for (i in 0 until 30) {
        val a0 = 135f + i * paso
        val c = when { i < 18 -> acento; i < 22 -> alta; else -> Color.White.copy(alpha = 0.08f) }
        drawPath(trapecio(cx, cy, rInt, rExt, a0 + 1f, a0 + paso - 1f), c)
    }
    val p = Path()
    val fantasma = Color.White.copy(alpha = 0.07f)
    digito7('6', cx - h * 0.13f, cy - h * 0.10f, h * 0.10f, h * 0.20f, Color.White, fantasma, p)
    digito7('2', cx + h * 0.01f, cy - h * 0.10f, h * 0.10f, h * 0.20f, Color.White, fantasma, p)
    for (k in 0 until 4) drawRect(acento.copy(alpha = 0.6f), Offset(w * 0.62f, h * (0.06f + k * 0.23f)), Size(w * 0.34f, h * 0.19f), style = Stroke(2f))
}
