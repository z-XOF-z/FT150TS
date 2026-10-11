package com.xof.ft150ts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Alerta
import com.xof.ft150ts.AlertasManager
import com.xof.ft150ts.IconoClima
import com.xof.ft150ts.TipoAlerta
import com.xof.ft150ts.Voz

/** Color según el nivel: 3 peligro (rojo), 2 precaución (ámbar), 1 informativo. */
fun colorNivel(nivel: Int): Color = when {
    nivel >= 3 -> Paleta.rojo
    nivel == 2 -> Paleta.ambar
    else -> Paleta.plata
}

// Íconos pixel (12x12) que no tiene el clima
private val ARTE_ALERTA: Map<TipoAlerta, List<String>> = mapOf(
    TipoAlerta.HURACAN to listOf(
        "....XXXX....", "..XX........", ".X....XXX...", "X...XX...X..",
        "X..X..XX..X.", "X..X.X..X.X.", ".X.X..X..X.X", ".X..XX...X..",
        "..X.....X...", "...XXXX.....", "........XX..", "....XXXX....",
    ),
    TipoAlerta.VIENTO to listOf(
        "............", "......XXX...", ".........X..", "XXXXXXXXX...",
        "............", "XXXXXXXXXXX.", "...........X", "..........X.",
        "XXXXXX......", "......X.....", ".....X......", "............",
    ),
    TipoAlerta.SISMO to listOf(
        "............", "............", "....X.......", "....X....X..",
        "...X.X...X..", "X..X.X..X.X.", ".XX...X.X..X", "......X.X...",
        ".......X....", ".......X....", "............", "XXXXXXXXXXXX",
    ),
)

@Composable
fun IconoAlertaPixel(tipo: TipoAlerta, color: Color, modifier: Modifier = Modifier) {
    when (tipo) {
        TipoAlerta.TORMENTA, TipoAlerta.GRANIZO -> IconoClimaPixel(IconoClima.TORMENTA, modifier)
        TipoAlerta.LLUVIA -> IconoClimaPixel(IconoClima.LLUVIA, modifier)
        TipoAlerta.NIEBLA -> IconoClimaPixel(IconoClima.NIEBLA, modifier)
        TipoAlerta.CALOR, TipoAlerta.UV -> IconoClimaPixel(IconoClima.SOL, modifier)
        else -> {
            val arte = ARTE_ALERTA[tipo] ?: return
            Canvas(modifier) {
                val cel = size.minDimension / 12f
                val lado = cel * 0.86f
                for (f in arte.indices) for (c in arte[f].indices) {
                    if (arte[f][c] != '.') drawRect(color, Offset(c * cel, f * cel), Size(lado, lado))
                }
            }
        }
    }
}

/**
 * Franja en el tablero con la alerta más importante (nivel 2 o 3).
 * Las de nivel 1 (UV, niebla...) solo salen en el detalle del clima.
 */
@Composable
fun AlertaClimaTablero(modifier: Modifier = Modifier) {
    val lista by AlertasManager.alertas.collectAsStateWithLifecycle()
    var abierto by remember { mutableStateOf(false) }
    // Lluvia en tu ruta (adelante, en la dirección en que vas)
    val ruta by com.xof.ft150ts.LluviaRuta.aviso.collectAsStateWithLifecycle()
    ruta?.let { r ->
        val color = Paleta.ambar
        Row(
            modifier.fillMaxWidth().clip(Formas.chica).background(color.copy(alpha = 0.14f))
                .drawBehind { drawRect(color, Offset.Zero, Size(4.dp.toPx(), size.height)) }
                .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoClimaPixel(IconoClima.LLUVIA, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(if (r.km == 0) "Lluvia aquí" else "Lluvia adelante · ~${r.km} km", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(if (r.km == 0) "Va a llover donde estás" else "Llegarías en ~${r.minutos} min por donde vas", color = Paleta.textoSec, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
    val a = lista.firstOrNull { it.nivel >= 2 }
    if (a != null) {
        val color = colorNivel(a.nivel)
        val alfa = parpadeo(a.nivel >= 3 && LocalPaginaVisible.current)
        val extra = lista.count { it.nivel >= 2 } - 1
        Row(
            modifier.fillMaxWidth().clip(Formas.chica).background(color.copy(alpha = 0.14f))
                .drawBehind { drawRect(color.copy(alpha = 0.4f + 0.6f * alfa), Offset.Zero, Size(4.dp.toPx(), size.height)) }
                .clickable { abierto = true }
                .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoAlertaPixel(a.tipo, color, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(a.titulo, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.detalle, color = Paleta.textoSec, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (extra > 0) EtiquetaPixel("+$extra", color, 7.sp)
        }
    }
    if (abierto) DialogoAlertas { abierto = false }
}

@Composable
private fun FilaAlerta(a: Alerta) {
    val color = colorNivel(a.nivel)
    Row(
        Modifier.fillMaxWidth().clip(Formas.chica).background(Paleta.superficie2)
            .drawBehind { drawRect(color, Offset.Zero, Size(3.dp.toPx(), size.height)) }
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoAlertaPixel(a.tipo, color, Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.titulo, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                EtiquetaPixel(when (a.nivel) { 3 -> "peligro"; 2 -> "precaucion"; else -> "info" }, color, 6.sp)
            }
            Text(a.detalle, color = Paleta.textoSec, fontSize = 12.sp)
        }
    }
}

/** Todas las alertas, los ciclones activos y de dónde salen los datos. */
@Composable
fun DialogoAlertas(onCerrar: () -> Unit) {
    val lista by AlertasManager.alertas.collectAsStateWithLifecycle()
    val ciclones by AlertasManager.ciclones.collectAsStateWithLifecycle()
    val revisado by AlertasManager.revisado.collectAsStateWithLifecycle()
    val activo by AlertasManager.activo.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onCerrar) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().clip(Formas.tarjeta).background(Color(0xFF0B0E10))
                .panel().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EtiquetaPixel("alertas", Paleta.lima, 10.sp)
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !activo -> Text("Las alertas están apagadas (Ajustes > Avisos).", color = Paleta.textoSec, fontSize = 13.sp)
                    lista.isEmpty() -> Text("Sin alertas cerca de ti.", color = Paleta.verde, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    else -> lista.forEach { FilaAlerta(it) }
                }
                if (activo) {
                    Spacer(Modifier.heightIn(min = 4.dp))
                    EtiquetaPixel("ciclones activos", Paleta.textoSec, 7.sp)
                    if (ciclones.isEmpty()) {
                        Text("No hay ciclones activos en el Atlántico ni en el Pacífico.", color = Paleta.textoSec, fontSize = 12.sp)
                    }
                    ciclones.sortedBy { AlertasManager.distanciaA(it) ?: Int.MAX_VALUE }.forEach { c ->
                        val d = AlertasManager.distanciaA(c)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${AlertasManager.descripcion(c)} ${c.nombre}", color = Paleta.texto, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(if (d != null) "$d km" else "--", color = Paleta.textoSec, fontSize = 12.sp)
                        }
                    }
                }
            }
            Text(
                "Actualizado ${if (revisado > 0) haceCuanto(revisado) else "—"}. Ciclones: NHC/NOAA · Sismos: USGS · " +
                    "Clima: Open-Meteo. No sustituye los avisos oficiales de Protección Civil ni del SMN.",
                color = Paleta.textoSec.copy(alpha = 0.75f), fontSize = 10.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonDeportivo("Escuchar", {
                    Voz.decir(
                        if (lista.isEmpty()) "Sin alertas cerca"
                        else lista.take(3).joinToString(". ") { it.voz }
                    )
                }, Modifier.weight(1f), tamanoTexto = 12.sp)
                BotonDeportivo("Actualizar", { AlertasManager.actualizar() }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                BotonDeportivo("Cerrar", onCerrar, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
            }
        }
    }
}
