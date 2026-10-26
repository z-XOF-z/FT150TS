package com.xof.ft150ts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Arrancones
import com.xof.ft150ts.Brujula
import com.xof.ft150ts.FaseArr
import com.xof.ft150ts.Moto
import com.xof.ft150ts.UbicacionManager
import kotlin.math.max
import kotlin.math.min

/**
 * Tableros deportivos (v3.6): Superbike, F1, Arrancón, Rally y Arcade. Igual que los de Tableros.kt: usan el tema
 * activo (Paleta / Formas / panel()), dibujan en Canvas con las figuras armadas una vez por tamaño y solo redibujan
 * lo que cambia. Todos respetan la animación del switch y "quitar animaciones".
 */


// ============================================================
// Superbike: TFT con la curva de RPM que sube de izquierda a derecha
// ============================================================
@Composable
fun TableroSuperbike() {
    MarcoTablero { _, alto ->
        val r by Moto.rapida.enVivo()
        val rpm = rpmSuave()
        Box(Modifier.fillMaxSize()) {
            CurvaRpm(rpm, r.shift, Modifier.fillMaxSize())
            // Velocidad abajo a la izquierda, como en las TFT de superbike
            Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp)) {
                Vivo {
                    val rv by rpm
                    Text(if (rv > 0f) "${(rv / 10).toInt() * 10} rpm" else "-- rpm", color = colorRpm(rv.toInt(), r.shift),
                        fontSize = (alto * 0.07f).aSp(), fontFamily = Fuentes.logo)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Vivo { Text(textoVel(velocidadAnimada()), color = Paleta.texto, fontSize = (alto * 0.32f).aSp(), fontFamily = Fuentes.logo) }
                    Spacer(Modifier.width(8.dp))
                    EtiquetaPixel("km/h", Paleta.textoSec, 10.sp, Modifier.padding(bottom = 18.dp))
                }
            }
            // Marcha en caja y la inclinación con su mini medidor
            Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.width(alto * 0.36f).panel().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EtiquetaPixel("inclinación", Paleta.textoSec, 7.sp)
                    MedidorInclinacion(r.incl, Modifier.fillMaxWidth().height(alto * 0.17f))
                    Text(if (r.incl >= 0) "${r.incl.toInt()}°" else "--", color = if (r.incl > 45) Paleta.ambar else Paleta.texto,
                        fontSize = (alto * 0.07f).aSp(), fontFamily = Fuentes.logo)
                }
                Box(Modifier.width(alto * 0.36f).height(alto * 0.46f).panel(), contentAlignment = Alignment.Center) {
                    MarchaAnimada(r.marcha, (alto * 0.34f).aSp())
                    EtiquetaPixel("marcha", Paleta.textoSec, 7.sp, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
                }
            }
            FilaMini(Modifier.align(Alignment.TopEnd).padding(top = 2.dp))
        }
    }
}

/** RPM como una curva de segmentos que sube (de abajo-izquierda a arriba-derecha). */
@Composable
private fun CurvaRpm(rpm: State<Float>, shift: Int, modifier: Modifier) {
    val rojo = enCambio(rpm, shift)
    val destello = parpadeoEstado(rojo)
    val baja = Paleta.rpmBaja
    val media = Paleta.rpmMedia
    val alta = Paleta.rpmAlta
    val vacio = Paleta.superficie2
    val texto = Paleta.textoSec
    Box(modifier.graphicsLayer().drawWithCache {
        val n = 48
        val w = size.width
        val h = size.height
        // La curva va de (0, 62% alto) a (w, 6% alto) y se "acuesta" al final, como una TFT
        fun y(t: Float) = h * (0.40f - 0.30f * (1f - (1f - t) * (1f - t)))
        val grueso = h * 0.10f
        val paso = (w - h * 0.06f) / n
        val segs = List(n) { i ->
            val t0 = i / n.toFloat(); val t1 = (i + 0.78f) / n
            val x0 = i * paso; val x1 = x0 + paso * 0.78f
            Path().apply { moveTo(x0, y(t0)); lineTo(x1, y(t1)); lineTo(x1, y(t1) + grueso); lineTo(x0, y(t0) + grueso); close() }
        }
        val px = max(1f, h / 90f)
        onDrawBehind {
            val enc = (rpm.value / RPM_MAX * n).toInt().coerceIn(0, n)
            for (i in 0 until n) {
                val rs = (i + 1) * RPM_MAX / n
                val base = when { rs >= shift -> alta; rs >= shift * 0.7f -> media; else -> baja }
                drawPath(segs[i], when { rojo -> alta.copy(alpha = destello.value); i < enc -> base; else -> vacio })
            }
            for (k in 1..10) {
                val t = k / 10f
                val tk = "$k"
                textoPixel(tk, min(t * w - paso, w - anchoPixel(tk, px) - 2f), y(t) + grueso + 4f, px, if (k * 1000 >= shift) alta else texto)
            }
        }
    })
}

/** Semicírculo con la moto inclinada a [incl] grados (0 = derecha). */
@Composable
private fun MedidorInclinacion(incl: Float, modifier: Modifier) {
    val acento = Paleta.lima
    val pista = Paleta.superficie2
    val ambar = Paleta.ambar
    Canvas(modifier) {
        val r = min(size.width / 2, size.height) * 0.92f
        val c = Offset(size.width / 2, size.height)
        drawArc(pista, 180f, 180f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(4.dp.toPx()))
        // Marcas a 15°, 30° y 45° de cada lado
        for (a in listOf(-45, -30, -15, 15, 30, 45)) {
            val rad = Math.toRadians(270.0 + a)
            val p1 = Offset(c.x + (r * 0.80f * Math.cos(rad)).toFloat(), c.y + (r * 0.80f * Math.sin(rad)).toFloat())
            val p2 = Offset(c.x + (r * Math.cos(rad)).toFloat(), c.y + (r * Math.sin(rad)).toFloat())
            drawLine(pista, p1, p2, 2.dp.toPx())
        }
        // La aguja: la app solo sabe cuánto (no hacia qué lado), así que se dibuja simétrica
        val i = incl.coerceIn(0f, 60f)
        for (lado in listOf(-1f, 1f)) {
            val rad = Math.toRadians(270.0 + lado * i)
            drawLine(if (i > 45) ambar else acento, c,
                Offset(c.x + (r * 0.9f * Math.cos(rad)).toFloat(), c.y + (r * 0.9f * Math.sin(rad)).toFloat()),
                3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Viaje y temperatura del motor, chiquitos arriba a la derecha. */
@Composable
private fun FilaMini(modifier: Modifier) {
    val r by Moto.rapida.enVivo()
    val v by Moto.viaje.enVivo()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        EtiquetaPixel("trip ${fmt(v.km, 1)} km", Paleta.textoSec, 9.sp)
        EtiquetaPixel(if (r.temp > -90f) "motor ${r.temp.toInt()}°" else "motor --", Paleta.textoSec, 9.sp)
        EtiquetaPixel(if (r.vMoto >= 0) "${fmt(r.vMoto, 1)} v" else "-- v", Paleta.textoSec, 9.sp)
    }
}

// ============================================================
// F1: tira de 15 LEDs y pantalla de volante
// ============================================================
@Composable
fun TableroF1() {
    MarcoTablero { _, alto ->
        val r by Moto.rapida.enVivo()
        val rpm = rpmSuave()
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LedsF1(rpm, r.shift, Modifier.fillMaxWidth().height(alto * 0.13f))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CeldaF1("velocidad", "", "km/h", Paleta.texto, (alto * 0.17f).aSp(), Modifier.weight(1.4f))
                    CeldaF1("motor", if (r.temp > -90f) "${r.temp.toInt()}" else "--", "°C", Paleta.texto, (alto * 0.09f).aSp(), Modifier.weight(1f))
                }
                Box(Modifier.weight(0.9f).fillMaxHeight().panel(), contentAlignment = Alignment.Center) {
                    MarchaAnimada(r.marcha, (alto * 0.46f).aSp())
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CeldaF1("rpm", "", "", Paleta.texto, (alto * 0.13f).aSp(), Modifier.weight(1.4f), rpmEnVivo = rpm, shift = r.shift)
                    CeldaF1("batería", if (r.vMoto >= 0) fmt(r.vMoto, 1) else "--", "V", Paleta.texto, (alto * 0.09f).aSp(), Modifier.weight(1f))
                }
            }
            FilaResumen(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CeldaF1(
    etiqueta: String, valor: String, unidad: String, color: Color, tamano: androidx.compose.ui.unit.TextUnit, modifier: Modifier,
    rpmEnVivo: State<Float>? = null, shift: Int = 0,
) {
    Column(modifier.fillMaxWidth().panel().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
        EtiquetaPixel(etiqueta, Paleta.textoSec, 7.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            // Velocidad o RPM en vivo: se leen aquí adentro, así solo se vuelve a armar esta celda
            Vivo {
                when {
                    rpmEnVivo != null -> { val rv by rpmEnVivo
                        Text(if (rv > 0f) "${(rv / 10).toInt() * 10}" else "--", color = colorRpm(rv.toInt(), shift), fontSize = tamano, fontFamily = Fuentes.logo, maxLines = 1) }
                    etiqueta == "velocidad" -> Text(textoVel(velocidadAnimada()), color = color, fontSize = tamano, fontFamily = Fuentes.logo, maxLines = 1)
                    else -> Text(valor, color = color, fontSize = tamano, fontFamily = Fuentes.logo, maxLines = 1)
                }
            }
            if (unidad.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unidad, color = Paleta.textoSec, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

/** 15 LEDs: 5 verdes, 5 rojos, 5 azules (como un volante de F1); al cambio destellan todos en azul. */
@Composable
private fun LedsF1(rpm: State<Float>, shift: Int, modifier: Modifier) {
    val rojo = enCambio(rpm, shift)
    val destello = parpadeoEstado(rojo)
    val verde = Paleta.rpmBaja
    val roja = Paleta.rpmMedia
    val azul = Paleta.rpmAlta
    val apagado = Paleta.superficie2
    Canvas(modifier.graphicsLayer()) {
        val n = 15
        val hueco = size.width / n
        val radio = min(hueco * 0.36f, size.height * 0.45f)
        // Los LEDs se prenden del 40% al 100% del punto de cambio
        val inicio = shift * 0.40f
        val enc = if (rpm.value <= inicio) 0 else ((rpm.value - inicio) / (shift - inicio) * n).toInt().coerceIn(0, n)
        for (i in 0 until n) {
            val base = when { i < 5 -> verde; i < 10 -> roja; else -> azul }
            val c = when { rojo -> azul.copy(alpha = destello.value); i < enc -> base; else -> apagado }
            val centro = Offset(hueco * (i + 0.5f), size.height / 2)
            if (c != apagado) drawCircle(c.copy(alpha = c.alpha * 0.25f), radio * 1.5f, centro)
            drawCircle(c, radio, centro)
        }
    }
}

// ============================================================
// Arrancón: barra vertical de RPM, velocidad enorme y tus tiempos
// ============================================================
@Composable
fun TableroArrancon() {
    MarcoTablero { _, alto ->
        val r by Moto.rapida.enVivo()
        val rpm = rpmSuave()
        val mejores by Arrancones.mejores.collectAsStateWithLifecycle()
        val fase by Arrancones.fase.collectAsStateWithLifecycle()
        val tVivo by Arrancones.tiempo.collectAsStateWithLifecycle()
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RpmVertical(rpm, r.shift, Modifier.width(alto * 0.22f).fillMaxHeight().panel())
            Column(Modifier.weight(1.4f).fillMaxHeight().panel().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                EtiquetaPixel(if (fase == FaseArr.CORRIENDO) "corriendo" else "velocidad", if (fase == FaseArr.CORRIENDO) Paleta.ambar else Paleta.textoSec, 8.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Vivo { Text(textoVel(velocidadAnimada()), color = Paleta.texto, fontSize = (alto * 0.40f).aSp(), fontFamily = Fuentes.logo) }
                    Spacer(Modifier.width(6.dp))
                    EtiquetaPixel("km/h", Paleta.textoSec, 10.sp, Modifier.padding(bottom = 20.dp))
                }
                // Cronómetro en vivo de la prueba (Extras → Arrancones)
                Text(if (fase == FaseArr.CORRIENDO || fase == FaseArr.TERMINADO) "${fmt(tVivo, 2)} s" else "listo para arrancar",
                    color = if (fase == FaseArr.CORRIENDO) Paleta.ambar else Paleta.textoSec, fontSize = (alto * 0.06f).aSp(), fontFamily = Fuentes.logo)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().weight(1.2f).panel(), contentAlignment = Alignment.Center) {
                    MarchaAnimada(r.marcha, (alto * 0.28f).aSp())
                }
                val m = mejores
                CeldaF1("mejor 0-60", if (m != null && m.t60 > 0f) fmt(m.t60, 2) else "--", "s", Paleta.lima, (alto * 0.08f).aSp(), Modifier.weight(1f))
                CeldaF1("mejor 100 m", if (m != null && m.t100m > 0f) fmt(m.t100m, 2) else "--", "s", Paleta.lima, (alto * 0.08f).aSp(), Modifier.weight(1f))
            }
        }
    }
}

/** RPM en una barra vertical por bloques, con la línea del punto de cambio. */
@Composable
private fun RpmVertical(rpm: State<Float>, shift: Int, modifier: Modifier) {
    val rojo = enCambio(rpm, shift)
    val destello = parpadeoEstado(rojo)
    val baja = Paleta.rpmBaja
    val media = Paleta.rpmMedia
    val alta = Paleta.rpmAlta
    val vacio = Paleta.superficie2
    val acento = Paleta.texto
    Canvas(modifier.padding(10.dp).graphicsLayer()) {
        val n = 20
        val hueco = 3.dp.toPx()
        val alto = (size.height - hueco * (n - 1)) / n
        val enc = (rpm.value / RPM_MAX * n).toInt().coerceIn(0, n)
        for (i in 0 until n) {
            val rs = (i + 1) * RPM_MAX / n
            val base = when { rs >= shift -> alta; rs >= shift * 0.7f -> media; else -> baja }
            val y = size.height - (i + 1) * alto - i * hueco
            drawRect(when { rojo -> alta.copy(alpha = destello.value); i < enc -> base; else -> vacio }, Offset(0f, y), Size(size.width, alto))
        }
        val ys = size.height * (1f - shift.toFloat() / RPM_MAX)
        drawLine(acento, Offset(-6f, ys), Offset(size.width + 6f, ys), 2.dp.toPx())
    }
}

// ============================================================
// Rally: cinta de brújula, roadbook y altitud
// ============================================================
@Composable
fun TableroRally() {
    // La brújula se prende solo mientras este tablero se ve (la cuenta de usuarios la comparte con Auto)
    val visible = LocalPaginaVisible.current
    DisposableEffect(visible) {
        if (visible) Brujula.arrancar()
        onDispose { if (visible) Brujula.detener() }
    }
    MarcoTablero { _, alto ->
        val r by Moto.rapida.enVivo()
        val v by Moto.viaje.enVivo()
        val rumboBrujula by Brujula.rumbo.enVivo()
        val ubi by UbicacionManager.ubicacion.enVivo()
        val vel = velocidadEstable()
        // Rodando, el rumbo del GPS es más fiel; parado, la brújula
        val rumbo = ubi?.takeIf { it.hasBearing() && vel > 9f }?.bearing ?: rumboBrujula
        val rpm = rpmSuave()
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CintaBrujula(rumbo, Modifier.fillMaxWidth().height(alto * 0.17f).panel())
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.2f).fillMaxHeight().panel().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    EtiquetaPixel("velocidad", Paleta.textoSec, 8.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Vivo { Text(textoVel(velocidadAnimada()), color = Paleta.texto, fontSize = (alto * 0.30f).aSp(), fontFamily = Fuentes.logo) }
                        Spacer(Modifier.weight(1f))
                        MarchaAnimada(r.marcha, (alto * 0.16f).aSp())
                    }
                    BarraRpmDelgada(rpm, r.shift, Modifier.fillMaxWidth().height(10.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CeldaF1("viaje", fmt(v.km, 2), "km", Paleta.lima, (alto * 0.14f).aSp(), Modifier.weight(1.3f))
                    CeldaF1("altitud", ubi?.takeIf { it.hasAltitude() }?.altitude?.toInt()?.toString() ?: "--", "m", Paleta.texto, (alto * 0.08f).aSp(), Modifier.weight(1f))
                }
            }
        }
    }
}

/** Cinta con N, NE, E… que se recorre con el rumbo; el rumbo actual queda al centro. */
@Composable
private fun CintaBrujula(rumbo: Float, modifier: Modifier) {
    val texto = Paleta.texto
    val sec = Paleta.textoSec
    val acento = Paleta.lima
    val marcas = remember { listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO") }
    Box(modifier.drawWithCache {
        val px = max(1f, size.height / 16f)
        val porGrado = size.width / 120f            // se ven 120° de la cinta
        onDrawBehind {
            val r0 = if (rumbo < 0f) 0f else rumbo
            for (g in -60..60) {
                val ang = ((r0 + g) % 360f + 360f) % 360f
                val x = size.width / 2 + g * porGrado
                val ent = ang.toInt()
                if (ent % 45 == 0 && Math.abs(ang - ent) < 1f) {
                    val t = marcas[ent / 45]
                    textoPixel(t, x - anchoPixel(t, px * 1.3f) / 2, size.height * 0.18f, px * 1.3f, if (ent == 0) acento else texto)
                } else if (ent % 15 == 0 && Math.abs(ang - ent) < 1f) {
                    drawLine(sec, Offset(x, size.height * 0.62f), Offset(x, size.height * 0.85f), 2f)
                }
            }
            // Indicador central y el rumbo en grados
            val c = size.width / 2
            drawPath(Path().apply { moveTo(c - 10f, size.height); lineTo(c + 10f, size.height); lineTo(c, size.height - 16f); close() }, acento)
            val t = if (rumbo < 0f) "---" else "${rumbo.toInt()}"
            textoPixel(t, size.width - anchoPixel(t, px) - 12f, 8f, px, sec)
        }
    })
}

@Composable
private fun BarraRpmDelgada(rpm: State<Float>, shift: Int, modifier: Modifier) {
    val vacio = Paleta.superficie2
    val rojo = enCambio(rpm, shift)
    val destello = parpadeoEstado(rojo)
    Canvas(modifier.graphicsLayer()) {
        drawRect(vacio)
        val c = colorRpm(rpm.value.toInt(), shift)
        drawRect(if (rojo) c.copy(alpha = destello.value) else c, Offset.Zero, Size(size.width * (rpm.value / RPM_MAX).coerceIn(0f, 1f), size.height))
        drawRect(Color.White, Offset(size.width * shift / RPM_MAX - 1f, 0f), Size(2f, size.height))
    }
}

// ============================================================
// Arcade: dígitos de 7 segmentos con brillo y RPM tipo ecualizador
// ============================================================
@Composable
fun TableroArcade() {
    MarcoTablero { _, alto ->
        val r by Moto.rapida.enVivo()
        val rpm = rpmSuave()
        val velS = velocidadAnimadaEstado()
        val arrS = LocalArranque.current
        val acento = Paleta.lima
        val apagado = Paleta.superficie2
        val texto = Paleta.texto
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.5f).fillMaxHeight().panel().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                EtiquetaPixel("speed · km/h", Paleta.textoSec, 9.sp)
                Box(Modifier.fillMaxWidth().weight(1f).drawWithCache {
                    val p = Path()
                    val h = size.height * 0.86f
                    val w = h * 0.52f
                    val paso = anchoDigito(w, h) * 1.12f
                    val x0 = (size.width - paso * 3) / 2
                    onDrawBehind {
                        val vel = velS.value
                        val valor = if (pruebaSegmentos(arrS.value)) 888f else if (vel < 0f) null else vel
                        // Brillo: el número se dibuja dos veces, la de atrás un poco más grande y tenue
                        numero7(valor, 3, 0, x0 - 3f, size.height * 0.07f - 3f, w, h, paso, acento.copy(alpha = 0.25f), Color.Transparent, p)
                        numero7(valor, 3, 0, x0, size.height * 0.07f, w, h, paso, texto, apagado, p)
                    }
                })
                Ecualizador(rpm, r.shift, Modifier.fillMaxWidth().height(alto * 0.20f))
            }
            Column(Modifier.weight(0.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth().weight(1.4f).panel().padding(12.dp)) {
                    EtiquetaPixel("gear", Paleta.textoSec, 9.sp)
                    Box(Modifier.fillMaxSize().drawWithCache {
                        val p = Path()
                        val h = size.height * 0.85f
                        val w = h * 0.55f
                        onDrawBehind {
                            val c = when { pruebaSegmentos(arrS.value) -> '8'; r.marcha < 0 -> '-'; r.marcha == 0 -> 'N'; else -> ('0' + r.marcha) }
                            val x = (size.width - anchoDigito(w, h)) / 2
                            digito7(c, x - 3f, size.height * 0.08f - 3f, w, h, acento.copy(alpha = 0.3f), Color.Transparent, p)
                            digito7(c, x, size.height * 0.08f, w, h, if (r.marcha == 0) Paleta.verde else acento, apagado, p)
                        }
                    })
                }
                Column(Modifier.fillMaxWidth().weight(1f).panel().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    EtiquetaPixel("rpm", Paleta.textoSec, 9.sp)
                    Vivo {
                        val rv by rpm
                        Text(if (rv > 0f) "${(rv / 10).toInt() * 10}" else "----", color = colorRpm(rv.toInt(), r.shift),
                            fontSize = (alto * 0.09f).aSp(), fontFamily = Fuentes.logo)
                    }
                }
            }
        }
    }
}

/** RPM como barras que suben de izquierda a derecha (más altas al subir de vueltas). */
@Composable
private fun Ecualizador(rpm: State<Float>, shift: Int, modifier: Modifier) {
    val rojo = enCambio(rpm, shift)
    val destello = parpadeoEstado(rojo)
    val baja = Paleta.rpmBaja
    val media = Paleta.rpmMedia
    val alta = Paleta.rpmAlta
    val vacio = Paleta.superficie2
    Canvas(modifier.graphicsLayer()) {
        val n = 24
        val hueco = 4.dp.toPx()
        val ancho = (size.width - hueco * (n - 1)) / n
        val enc = (rpm.value / RPM_MAX * n).toInt().coerceIn(0, n)
        val bloques = 6
        val hb = (size.height - 2.dp.toPx() * (bloques - 1)) / bloques
        for (i in 0 until n) {
            val rs = (i + 1) * RPM_MAX / n
            val base = when { rs >= shift -> alta; rs >= shift * 0.7f -> media; else -> baja }
            val col = when { rojo -> alta.copy(alpha = destello.value); i < enc -> base; else -> vacio }
            val altos = 1 + (i * bloques / n)                 // la columna i tiene más bloques hacia la derecha
            for (b in 0 until altos) {
                val y = size.height - (b + 1) * hb - b * 2.dp.toPx()
                drawRect(col, Offset(i * (ancho + hueco), y), Size(ancho, hb))
            }
        }
    }
}

// ============================================================
// Vistas previas para Ajustes
// ============================================================
internal fun DrawScope.vistaSuperbike(acento: Color, alta: Color) {
    val w = size.width; val h = size.height
    for (i in 0 until 16) {
        val t = i / 16f
        val y = h * (0.55f - 0.45f * (1f - (1f - t) * (1f - t)))
        drawRect(if (i < 10) acento else alta.copy(alpha = 0.5f), Offset(w * t * 0.95f, y), Size(w * 0.045f, h * 0.10f))
    }
    drawRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.05f, h * 0.68f), Size(w * 0.28f, h * 0.22f))
    drawRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.78f, h * 0.55f), Size(w * 0.18f, h * 0.38f))
    drawRect(acento, Offset(w * 0.84f, h * 0.63f), Size(w * 0.06f, h * 0.22f))
}

internal fun DrawScope.vistaF1(verde: Color, roja: Color, azul: Color) {
    val w = size.width; val h = size.height
    for (i in 0 until 15) drawCircle(when { i < 5 -> verde; i < 10 -> roja; else -> azul.copy(alpha = 0.35f) }, h * 0.06f, Offset(w * (i + 0.5f) / 15, h * 0.14f))
    drawRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.04f, h * 0.30f), Size(w * 0.3f, h * 0.6f))
    drawRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.66f, h * 0.30f), Size(w * 0.3f, h * 0.6f))
    drawRect(Color.White.copy(alpha = 0.16f), Offset(w * 0.37f, h * 0.30f), Size(w * 0.26f, h * 0.6f))
    drawRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.45f, h * 0.42f), Size(w * 0.1f, h * 0.36f))
}

internal fun DrawScope.vistaArrancon(acento: Color, alta: Color) {
    val w = size.width; val h = size.height
    for (i in 0 until 8) drawRect(if (i < 5) acento else if (i < 7) alta.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
        Offset(w * 0.05f, h * (0.85f - i * 0.1f)), Size(w * 0.10f, h * 0.08f))
    drawRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.22f, h * 0.30f), Size(w * 0.36f, h * 0.36f))
    drawRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.68f, h * 0.10f), Size(w * 0.27f, h * 0.36f))
    drawRect(acento, Offset(w * 0.68f, h * 0.55f), Size(w * 0.20f, h * 0.10f))
    drawRect(acento, Offset(w * 0.68f, h * 0.75f), Size(w * 0.16f, h * 0.10f))
}

internal fun DrawScope.vistaRally(acento: Color) {
    val w = size.width; val h = size.height
    drawRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.04f, h * 0.06f), Size(w * 0.92f, h * 0.18f))
    for (i in 0..8) drawLine(Color.White.copy(alpha = 0.4f), Offset(w * (0.1f + i * 0.1f), h * 0.16f), Offset(w * (0.1f + i * 0.1f), h * 0.22f), 2f)
    drawRect(acento, Offset(w * 0.48f, h * 0.08f), Size(w * 0.04f, h * 0.08f))
    drawRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.08f, h * 0.38f), Size(w * 0.30f, h * 0.32f))
    drawRect(acento, Offset(w * 0.56f, h * 0.38f), Size(w * 0.34f, h * 0.18f))
    drawRect(Color.White.copy(alpha = 0.15f), Offset(w * 0.08f, h * 0.80f), Size(w * 0.40f, h * 0.06f))
}

internal fun DrawScope.vistaArcade(acento: Color, alta: Color) {
    val w = size.width; val h = size.height
    val p = Path()
    numero7(188f, 3, 0, w * 0.06f, h * 0.10f, w * 0.12f, h * 0.45f, w * 0.17f, Color.White, Color.White.copy(alpha = 0.1f), p)
    for (i in 0 until 12) {
        val altos = 1 + i / 3
        for (b in 0 until altos) drawRect(if (i < 8) acento else alta.copy(alpha = 0.5f), Offset(w * (0.06f + i * 0.045f), h * (0.88f - b * 0.07f)), Size(w * 0.035f, h * 0.05f))
    }
    digito7('3', w * 0.75f, h * 0.15f, w * 0.12f, h * 0.45f, acento, Color.White.copy(alpha = 0.1f), p)
}
