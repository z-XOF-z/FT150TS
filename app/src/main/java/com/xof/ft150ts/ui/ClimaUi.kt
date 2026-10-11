package com.xof.ft150ts.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.AlertasManager
import com.xof.ft150ts.Clima
import com.xof.ft150ts.ClimaManager
import com.xof.ft150ts.IconoClima
import com.xof.ft150ts.describir
import com.xof.ft150ts.iconoClima

// ------------------------------------------------------------
// Íconos del clima en pixel art (12x12). Cada letra es un color.
// ------------------------------------------------------------
private val ARTE: Map<IconoClima, List<String>> = mapOf(
    IconoClima.SOL to listOf(
        ".....SS.....", ".S...SS...S.", "..S......S..", "....SSSS....",
        "...SSSSSS...", "SS.SSSSSS.SS", "SS.SSSSSS.SS", "...SSSSSS...",
        "....SSSS....", "..S......S..", ".S...SS...S.", ".....SS.....",
    ),
    IconoClima.LUNA to listOf(
        ".....MMM....", "...MMMM.....", "..MMMM......", ".MMMM.......",
        ".MMM........", ".MMM........", ".MMM........", ".MMMM.......",
        "..MMMM...MM.", "...MMMMMMM..", ".....MMM....", "............",
    ),
    IconoClima.SOL_NUBE to listOf(
        "..S.S.......", "...SSS......", ".SSSSSS.....", "..SSSSCCC...",
        ".SSSSCCCCC..", "..SSCCCCCCC.", "...CCCCCCCCC", "..CCCCCCCCCC",
        "..CCCCCCCCCC", "...ccccccccc", "............", "............",
    ),
    IconoClima.LUNA_NUBE to listOf(
        "...MMM......", "..MMM.......", ".MMM........", ".MMM..CCC...",
        ".MMM.CCCCC..", "..MMCCCCCCC.", "...CCCCCCCCC", "..CCCCCCCCCC",
        "..CCCCCCCCCC", "...ccccccccc", "............", "............",
    ),
    IconoClima.NUBE to listOf(
        "............", "............", ".....CCC....", "....CCCCC...",
        "..CCCCCCCC..", ".CCCCCCCCCC.", "CCCCCCCCCCCC", "CCCCCCCCCCCC",
        ".cccccccccc.", "............", "............", "............",
    ),
    IconoClima.NIEBLA to listOf(
        "............", ".....CCC....", "....CCCCC...", "..CCCCCCCC..",
        ".cccccccccc.", "............", "FFFFFFFFFF..", "............",
        "..FFFFFFFFFF", "............", "FFFFFFFFFF..", "............",
    ),
    IconoClima.LLUVIA to listOf(
        ".....CCC....", "....CCCCC...", "..CCCCCCCC..", ".CCCCCCCCCC.",
        "CCCCCCCCCCCC", ".cccccccccc.", "............", "..R...R...R.",
        ".R...R...R..", "............", "...R...R....", "..R...R.....",
    ),
    IconoClima.TORMENTA to listOf(
        ".....CCC....", "....CCCCC...", "..CCCCCCCC..", ".CCCCCCCCCC.",
        "CCCCCCCCCCCC", ".cccccccccc.", ".....BB.....", ".R..BB...R..",
        "...BBBBB....", ".R...BB..R..", "....BB......", "....B.......",
    ),
    IconoClima.NIEVE to listOf(
        ".....CCC....", "....CCCCC...", "..CCCCCCCC..", ".CCCCCCCCCC.",
        "CCCCCCCCCCCC", ".cccccccccc.", "............", "..N...N...N.",
        ".NNN.NNN.NNN", "..N...N...N.", "............", "............",
    ),
)

private fun colorPixel(c: Char): Color = when (c) {
    'S' -> Color(0xFFFFC107)
    'M' -> Color(0xFFFFE9A8)
    'C' -> Color(0xFFF4F7F2)
    'c' -> Color(0xFF8D989C)
    'R' -> Color(0xFF40C4FF)
    'B' -> Color(0xFFFFD600)
    'N' -> Color(0xFFFFFFFF)
    'F' -> Color(0xFFB8C2C6)
    else -> Color.Transparent
}

@Composable
fun IconoClimaPixel(icono: IconoClima, modifier: Modifier = Modifier) {
    val arte = ARTE[icono] ?: return
    Canvas(modifier) {
        val cel = size.minDimension / 12f
        val lado = cel * 0.86f
        for (f in arte.indices) {
            val fila = arte[f]
            for (col in fila.indices) {
                val ch = fila[col]
                if (ch != '.') drawRect(colorPixel(ch), Offset(col * cel, f * cel), Size(lado, lado))
            }
        }
    }
}

internal fun haceCuanto(hora: Long): String {
    val min = ((System.currentTimeMillis() - hora) / 60_000L).toInt()
    return when {
        min < 1 -> "ahora"
        min < 60 -> "hace $min min"
        min < 60 * 24 -> "hace ${min / 60} h"
        else -> "hace ${min / (60 * 24)} d"
    }
}

/** ¿Hay que avisar de lluvia? (probable en las próximas 2 horas). */
fun Clima.lluviaCerca(): Boolean = lluviaEnHoras in 0..2 && lluviaProx >= 50

// ------------------------------------------------------------
// Chip del clima (va encima del mapa)
// ------------------------------------------------------------
@Composable
fun ChipClima(modifier: Modifier = Modifier) {
    val c by ClimaManager.clima.collectAsStateWithLifecycle()
    val alertas by AlertasManager.alertas.collectAsStateWithLifecycle()
    var abierto by remember { mutableStateOf(false) }
    val clima = c
    val alerta = clima?.lluviaCerca() == true
    val nivel = alertas.maxOfOrNull { it.nivel } ?: 0
    val forma = Formas.chip
    Row(
        modifier.clip(forma).background(Paleta.fondo.copy(alpha = 0.82f))
            .border(1.dp, when {
                nivel >= 2 -> colorNivel(nivel)
                alerta -> Paleta.ambar
                else -> Paleta.lima.copy(alpha = 0.5f)
            }, forma)
            .clickable { if (clima == null) ClimaManager.actualizar(); abierto = true }
            .padding(start = 7.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (clima == null) {
            EtiquetaPixel("clima --", Paleta.textoSec, 7.sp)
        } else {
            IconoClimaPixel(iconoClima(clima.codigo, clima.esDia), Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("${clima.temp}°", color = Paleta.texto, fontSize = 16.sp, fontFamily = Fuentes.logo)
            if (nivel >= 2) {
                Spacer(Modifier.width(6.dp))
                EtiquetaPixel("alerta", colorNivel(nivel), 6.sp)
            } else if (alerta) {
                Spacer(Modifier.width(6.dp))
                EtiquetaPixel(if (clima.lluviaEnHoras == 0) "lluvia" else "lluvia ${clima.lluviaEnHoras}h", Paleta.ambar, 6.sp)
            }
        }
    }
    if (abierto) DialogoClima { abierto = false }
}

// ------------------------------------------------------------
// Tarjeta del clima (tablero)
// ------------------------------------------------------------
@Composable
fun TarjetaClima(modifier: Modifier = Modifier, compacta: Boolean = false) {
    val c by ClimaManager.clima.collectAsStateWithLifecycle()
    var abierto by remember { mutableStateOf(false) }
    val clima = c
    val acento = if (clima?.lluviaCerca() == true) Paleta.ambar else Paleta.lima
    Row(
        modifier.fillMaxWidth().panel()
            .drawBehind { drawRect(acento.copy(alpha = 0.9f), Offset.Zero, Size(3.dp.toPx(), size.height)) }
            .clickable { if (clima == null) ClimaManager.actualizar(); abierto = true }
            .padding(start = if (compacta) 11.dp else 14.dp, end = if (compacta) 9.dp else 12.dp,
                top = if (compacta) 6.dp else 10.dp, bottom = if (compacta) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (clima == null) {
            Column {
                EtiquetaPixel("clima")
                Spacer(Modifier.height(6.dp))
                Text("Toca para cargar (usa la ubicación del teléfono)", color = Paleta.textoSec, fontSize = 12.sp)
            }
        } else {
            IconoClimaPixel(iconoClima(clima.codigo, clima.esDia), Modifier.size(if (compacta) 28.dp else 40.dp))
            Spacer(Modifier.width(if (compacta) 9.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                EtiquetaPixel(if (clima.ciudad.isNotBlank()) "clima  ${clima.ciudad}" else "clima",
                    tamano = if (compacta) 6.5.sp else 8.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${clima.temp}°", color = Paleta.texto, fontSize = if (compacta) 19.sp else 26.sp, fontFamily = Fuentes.logo)
                    Spacer(Modifier.width(8.dp))
                    Text(describir(clima.codigo), color = Paleta.textoSec, fontSize = if (compacta) 11.sp else 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = if (compacta) 2.dp else 4.dp))
                }
                Text(
                    if (clima.lluviaCerca()) {
                        if (clima.lluviaEnHoras == 0) "Lluvia probable ya · ${clima.lluviaProx}%"
                        else "Lluvia en ~${clima.lluviaEnHoras} h · ${clima.lluviaProx}%"
                    } else "Lluvia ${clima.lluviaProx}% · Viento ${clima.viento} km/h",
                    color = if (clima.lluviaCerca()) Paleta.ambar else Paleta.textoSec, fontSize = if (compacta) 10.5.sp else 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
    if (abierto) DialogoClima { abierto = false }
}

// ------------------------------------------------------------
// Detalle del clima
// ------------------------------------------------------------
@Composable
fun DialogoClima(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val c by ClimaManager.clima.collectAsStateWithLifecycle()
    val cargando by ClimaManager.cargando.collectAsStateWithLifecycle()
    val error by ClimaManager.error.collectAsStateWithLifecycle()
    val alertas by AlertasManager.alertas.collectAsStateWithLifecycle()
    val alertasOn by AlertasManager.activo.collectAsStateWithLifecycle()
    var verAlertas by remember { mutableStateOf(false) }
    val clima = c

    Dialog(onDismissRequest = onCerrar) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(Formas.tarjeta).background(Color(0xFF0B0E10))
                .panel().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (clima == null) {
                EtiquetaPixel("clima", Paleta.lima, 10.sp)
                Text(
                    when {
                        cargando -> "Cargando…"
                        error.isNotBlank() -> error
                        else -> "Todavía no hay datos. Revisa que el teléfono tenga internet y ubicación."
                    },
                    color = Paleta.textoSec, fontSize = 14.sp,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoClimaPixel(iconoClima(clima.codigo, clima.esDia), Modifier.size(64.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("${clima.temp}°", color = Paleta.texto, fontSize = 44.sp, fontFamily = Fuentes.logo)
                        Text(describir(clima.codigo), color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (clima.ciudad.isNotBlank()) clima.ciudad + " · " else "") + haceCuanto(clima.hora),
                            color = Paleta.textoSec, fontSize = 12.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatoClima("sensacion", "${clima.sensacion}°", Modifier.weight(1f))
                    DatoClima("max / min", "${clima.max}° / ${clima.min}°", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatoClima("humedad", "${clima.humedad}%", Modifier.weight(1f))
                    DatoClima("viento", "${clima.viento} km/h", Modifier.weight(1f))
                }
                if (clima.lluviaCerca()) {
                    BannerAlerta(
                        if (clima.lluviaEnHoras == 0) "Es probable que llueva pronto (${clima.lluviaProx}%)"
                        else "Podría llover en ~${clima.lluviaEnHoras} h (${clima.lluviaProx}%)"
                    )
                }
                if (alertasOn) {
                    // Alertas: huracanes, tormentas, rachas, sismos... (toca para ver el detalle)
                    val nivel = alertas.maxOfOrNull { it.nivel } ?: 0
                    val color = if (nivel > 0) colorNivel(nivel) else Paleta.verde
                    Row(
                        Modifier.fillMaxWidth().clip(Formas.chica).background(color.copy(alpha = 0.12f))
                            .clickable { verAlertas = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        alertas.firstOrNull()?.let { IconoAlertaPixel(it.tipo, color, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
                        Text(
                            when (alertas.size) {
                                0 -> "Sin alertas ni huracanes cerca"
                                1 -> alertas[0].titulo
                                else -> "${alertas[0].titulo} y ${alertas.size - 1} más"
                            },
                            color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        )
                        EtiquetaPixel("ver", color, 7.sp)
                    }
                }
                if (clima.horas.isNotEmpty()) {
                    EtiquetaPixel("proximas horas")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        clima.horas.forEach { h ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${h.hora}h", color = Paleta.textoSec, fontSize = 11.sp)
                                Spacer(Modifier.height(3.dp))
                                IconoClimaPixel(iconoClima(h.codigo, h.hora in 7..18), Modifier.size(24.dp))
                                Spacer(Modifier.height(3.dp))
                                Text("${h.temp}°", color = Paleta.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${h.lluvia}%", color = if (h.lluvia >= 50) Color(0xFF40C4FF) else Paleta.textoSec, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonDeportivo("Escuchar", { ClimaManager.decir() }, Modifier.weight(1f), tamanoTexto = 12.sp)
                BotonDeportivo("A la moto", {
                    if (!ClimaManager.mandarAMoto()) Toast.makeText(ctx, "Conecta la moto para mandarlo al LCD", Toast.LENGTH_SHORT).show()
                }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                BotonDeportivo(if (cargando) "…" else "Actualizar", { ClimaManager.actualizar() }, Modifier.weight(1f),
                    primario = false, tamanoTexto = 12.sp)
            }
            Text("Datos del clima: Open-Meteo.com", color = Paleta.textoSec.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
    if (verAlertas) DialogoAlertas { verAlertas = false }
}

@Composable
private fun DatoClima(etiqueta: String, valor: String, modifier: Modifier) {
    Column(modifier.clip(Formas.chica).background(Paleta.superficie2).padding(horizontal = 10.dp, vertical = 8.dp)) {
        EtiquetaPixel(etiqueta, Paleta.textoSec, 6.sp)
        Spacer(Modifier.height(4.dp))
        Text(valor, color = Paleta.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
