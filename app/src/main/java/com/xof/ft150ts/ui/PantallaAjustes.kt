package com.xof.ft150ts.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Acciones
import com.xof.ft150ts.AgujaPid
import com.xof.ft150ts.AlertasManager
import com.xof.ft150ts.Apariencia
import com.xof.ft150ts.AudioSalida
import com.xof.ft150ts.Baches
import com.xof.ft150ts.BarrasSistema
import com.xof.ft150ts.Conexion
import com.xof.ft150ts.Dashcam
import com.xof.ft150ts.Energia
import com.xof.ft150ts.EstimadorMarcha
import com.xof.ft150ts.EstiloMapa
import com.xof.ft150ts.EstiloTablero
import com.xof.ft150ts.Estilos
import com.xof.ft150ts.Gpx
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.Luz
import com.xof.ft150ts.Mapas
import com.xof.ft150ts.ModoSueno
import com.xof.ft150ts.Moto
import com.xof.ft150ts.MusicaManager
import com.xof.ft150ts.NfcMoto
import com.xof.ft150ts.NivelCalor
import com.xof.ft150ts.ModoRendimiento
import com.xof.ft150ts.NivelRendimiento
import com.xof.ft150ts.Pantalla
import com.xof.ft150ts.PantallaEncendida
import com.xof.ft150ts.Reposo
import com.xof.ft150ts.Root
import com.xof.ft150ts.TelefonoMoto
import com.xof.ft150ts.TemaApp
import com.xof.ft150ts.TIPOS_ARRANQUE
import com.xof.ft150ts.Termico
import com.xof.ft150ts.UbicacionManager
import com.xof.ft150ts.Voz
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Página 4: Ajustes. Todo lo de la app en un solo lugar, ordenado por secciones:
 * Apariencia, Pantalla, Avisos, Conexión, Ruta y Acerca de.
 */
@Composable
fun PantallaAjustes(onAccesoNotif: () -> Unit, onVerInicio: () -> Unit) {
    // Rendimiento: LazyColumn, solo se arman las secciones que se ven. Antes las 19 secciones estaban armadas siempre y
    // Compose recorría ese árbol enorme en cada cuadro de cualquier cambio de pestaña (era parte del tirón al deslizar).
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Ancho { SeccionApariencia() } }
        item { Ancho { SeccionPantalla() } }
        item { Ancho { SeccionPid() } }
        item { Ancho { SeccionCalor() } }
        item { Ancho { SeccionMapa() } }
        item { Ancho { SeccionAvisos() } }
        item { Ancho { SeccionLluviaRuta() } }
        item { Ancho { SeccionRutinas() } }
        item { Ancho { SeccionBluetoothAudio() } }
        item { Ancho { SeccionVoz() } }
        item { Ancho { SeccionLauncher() } }
        item { Ancho { SeccionModoMoto() } }
        item { Ancho { SeccionAhorroNocturno() } }
        item { Ancho { SeccionSos() } }
        item { Ancho { SeccionSinTarjeta() } }
        item { Ancho { SeccionDashcam() } }
        item { Ancho { SeccionFuncionesAvanzadas() } }
        item { Ancho { SeccionRoot() } }
        item { Ancho { SeccionConexion(onAccesoNotif) } }
        item { Ancho { SeccionRuta() } }
        item { Ancho { SeccionRespaldo() } }
        item { Ancho { SeccionAcercaDe(onVerInicio) } }
        item { Spacer(Modifier.heightIn(min = 24.dp)) }
    }
}

/** En horizontal no se estira de lado a lado: queda una columna cómoda al centro. */
@Composable
private fun Ancho(contenido: @Composable () -> Unit) {
    Box(Modifier.widthIn(max = 600.dp).fillMaxWidth()) { contenido() }
}

// ------------------------------------------------------------
// Apariencia
// ------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionApariencia() {
    var confirmarReset by remember { mutableStateOf(false) }

    Seccion("Apariencia") {
        Subtitulo("Tema de la app")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionVisual(
                "Carbono", "Sólido, esquinas cortadas",
                Apariencia.tema == TemaApp.CARBONO, { Apariencia.ponerTema(TemaApp.CARBONO) },
                Modifier.weight(1f),
            ) { vistaCarbono(Paleta.lima) }
            OpcionVisual(
                "Vidrio", "Translúcido y redondo",
                Apariencia.tema == TemaApp.VIDRIO, { Apariencia.ponerTema(TemaApp.VIDRIO) },
                Modifier.weight(1f),
            ) { vistaVidrio(Paleta.lima, Paleta.rpmAlta) }
            OpcionVisual(
                "Filo", "HUD integrado, cortes a 45°",
                Apariencia.tema == TemaApp.FILO, { Apariencia.ponerTema(TemaApp.FILO) },
                Modifier.weight(1f),
            ) { vistaFilo(Paleta.lima) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionVisual(
                "Pixel", "Cortes a 45° en escalones",
                Apariencia.tema == TemaApp.PIXEL, { Apariencia.ponerTema(TemaApp.PIXEL) },
                Modifier.weight(1f),
            ) { vistaPixel(Paleta.lima) }
            OpcionVisual(
                "Táctico", "Visor militar con corchetes",
                Apariencia.tema == TemaApp.TACTICO, { Apariencia.ponerTema(TemaApp.TACTICO) },
                Modifier.weight(1f),
            ) { vistaTactico(Paleta.lima) }
            OpcionVisual(
                "Holo", "Holográfico, 4 cortes a 45°",
                Apariencia.tema == TemaApp.HOLO, { Apariencia.ponerTema(TemaApp.HOLO) },
                Modifier.weight(1f),
            ) { vistaHolo(Paleta.lima) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionVisual(
                "Ácido", "Cinta de peligro a 45°",
                Apariencia.tema == TemaApp.ACIDO, { Apariencia.ponerTema(TemaApp.ACIDO) },
                Modifier.weight(1f),
            ) { vistaAcido(Paleta.lima) }
            OpcionVisual(
                "Circuito", "Pistas de PCB a 45°",
                Apariencia.tema == TemaApp.CIRCUITO, { Apariencia.ponerTema(TemaApp.CIRCUITO) },
                Modifier.weight(1f),
            ) { vistaCircuito(Paleta.lima) }
            OpcionVisual(
                "Vértice", "Riel lateral y losas",
                Apariencia.tema == TemaApp.VERTICE, { Apariencia.ponerTema(TemaApp.VERTICE) },
                Modifier.weight(1f),
            ) { vistaVertice(Paleta.lima) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionVisual(
                "Tinta", "Viñetas de cómic · nuevo",
                Apariencia.tema == TemaApp.TINTA, { Apariencia.ponerTema(TemaApp.TINTA) },
                Modifier.weight(1f),
            ) { vistaTinta(Paleta.lima) }
            Spacer(Modifier.weight(2f).padding(5.dp))
        }

        Subtitulo("Estilos · nuevo (v3.4)")
        Estilos.TODOS.chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { e ->
                    val t = TemaApp.valueOf(e.id)
                    OpcionVisual(e.nombre, e.detalle, Apariencia.tema == t, { Apariencia.ponerTema(t) }, Modifier.weight(1f)) { vistaEstilo(e) }
                }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val mezcla = Estilos.mezcla(Apariencia.semillaMezcla)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OpcionVisual(
                "Mezcla · ${mezcla.nombre}", "Un tema distinto cada vez",
                Apariencia.tema == TemaApp.MEZCLA, { Apariencia.ponerTema(TemaApp.MEZCLA) }, Modifier.weight(1f),
            ) { vistaEstilo(mezcla) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BotonDeportivo("Otra mezcla", { Apariencia.otraMezcla() }, Modifier.fillMaxWidth())
                Text("Combina paleta, forma, adorno y fondo al azar: hay miles de combinaciones. Si te gusta una, quédate en ella.",
                    color = Paleta.textoSec, fontSize = 11.sp)
            }
        }
        if (estiloTema != null) {
            Text(estiloTema!!.origen, color = Paleta.textoSec, fontSize = 11.sp)
            FilaSwitch("Usar el color del tema", "Cada estilo trae su acento; apágalo para usar el color de acento que elijas abajo",
                Apariencia.acentoDelTema) { Apariencia.ponerAcentoDelTema(it) }
        }
        if (Apariencia.tema == TemaApp.TINTA) {
            Text(
                "Tinta: cada tarjeta es una viñeta de historieta con contorno de tinta, sombra sólida del color de acento " +
                    "y trama de puntos; de fondo, líneas de velocidad. Se ve mejor con acentos fuertes (rojo, amarillo, " +
                    "cian) y con el efecto neón apagado.",
                color = Paleta.textoSec, fontSize = 11.sp,
            )
        }
        if (Apariencia.tema == TemaApp.VERTICE) {
            Text(
                "Vértice cambia la organización: en horizontal la navegación pasa a un riel a la izquierda (lejos de los " +
                    "ganchos del soporte), arriba queda libre para el tablero y la velocidad, marcha y RPM se ven en " +
                    "todas las secciones. En vertical usa la barra integrada.",
                color = Paleta.textoSec, fontSize = 11.sp,
            )
        }

        FilaSwitch("Efecto neón", "Brillo alrededor de las tarjetas, la barra de arriba, el marco y la barra de RPM",
            Apariencia.neon) { Apariencia.ponerNeon(it) }
        Subtitulo("Pantalla de inicio")
        Selector(com.xof.ft150ts.INICIOS, Apariencia.estiloInicio, { Apariencia.ponerEstiloInicio(it) }, Modifier.fillMaxWidth())
        Text("La que sale al abrir la app. Las neón toman el color del tablero Neón que elijas (verde si usas otro).",
            color = Paleta.textoSec, fontSize = 11.sp)
        FilaSwitch("Animación al abrir el switch",
            "Sale en el tablero solo cuando abres el switch de la moto (el cable empieza a cargar), no al cambiar de pestaña. " +
                "Las RPM suben al tope y regresan y los dígitos marcan 888",
            Apariencia.animEntrada) { Apariencia.ponerAnimEntrada(it) }
        if (Apariencia.animEntrada) {
            Selector(TIPOS_ARRANQUE, Apariencia.tipoArranque, { Apariencia.ponerTipoArranque(it) }, Modifier.fillMaxWidth())
            BotonDeportivo("Probar animación", {
                Lanzador.pedirArranque()
                Lanzador.irA(com.xof.ft150ts.PAGINA_TABLERO)
            }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 13.sp)
        }

        Subtitulo("Fondo")
        Selector(listOf("Puntos", "Rejilla", "Rayas 45°", "Negro"), Apariencia.fondo, { Apariencia.ponerFondo(it) }, Modifier.fillMaxWidth())

        Subtitulo("Color de acento · ${Apariencia.nombreAcento()}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Apariencia.ACENTOS.forEachIndexed { i, a ->
                Muestra(Color(a.argb), i == Apariencia.acento) { Apariencia.ponerAcento(i) }
            }
            MuestraArcoiris(Apariencia.acento == Apariencia.ACENTO_LIBRE) {
                Apariencia.ponerAcentoLibre(Apariencia.tonoLibre, Apariencia.saturacionLibre)
            }
            // Secreto del logo: acento Oro XOF
            if (Apariencia.secretoXof) {
                Muestra(Color(Apariencia.ORO_XOF), Apariencia.acento == Apariencia.ACENTO_ORO) { Apariencia.ponerAcento(Apariencia.ACENTO_ORO) }
            }
        }
        if (Apariencia.acento == Apariencia.ACENTO_LIBRE) {
            val colores = SliderDefaults.colors(thumbColor = Paleta.lima, activeTrackColor = Paleta.lima, inactiveTrackColor = Paleta.superficie2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tono", color = Paleta.textoSec, fontSize = 13.sp, modifier = Modifier.width(80.dp))
                Slider(Apariencia.tonoLibre, { Apariencia.ponerAcentoLibre(it, Apariencia.saturacionLibre) }, valueRange = 0f..359f,
                    colors = colores, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Intensidad", color = Paleta.textoSec, fontSize = 13.sp, modifier = Modifier.width(80.dp))
                Slider(Apariencia.saturacionLibre, { Apariencia.ponerAcentoLibre(Apariencia.tonoLibre, it) }, valueRange = 0.15f..1f,
                    colors = colores, modifier = Modifier.weight(1f))
            }
        }

        Subtitulo("Estilo del tablero")
        FilaSwitch("Tableros con mi color de acento",
            "Racing Morado, Telemetría Morada y Dual Neón toman el tono de tu color de acento (con sus mismos brillos). " +
                "Las alertas siguen en rojo y la batería en verde. Apagado: exactos como tus conceptos.",
            Apariencia.tablerosConAcento) { Apariencia.ponerTablerosConAcento(it) }
        // v3.10: recreación fiel del concepto morado de Andrew
        OpcionVisual(
            "Racing Morado", "Tu concepto: 145 · 3 GEAR · pantalla completa · nuevo",
            Apariencia.estiloTablero == EstiloTablero.MORADO, { Apariencia.ponerEstiloTablero(EstiloTablero.MORADO) },
            Modifier.fillMaxWidth(),
        ) { vistaMorado() }
        OpcionVisual(
            "Telemetría Morada", "Tu concepto: 168 · 5 · radar G y tu ruta · nuevo",
            Apariencia.estiloTablero == EstiloTablero.TELEMETRIA_MORADA, { Apariencia.ponerEstiloTablero(EstiloTablero.TELEMETRIA_MORADA) },
            Modifier.fillMaxWidth(),
        ) { vistaTelemetriaMorada() }
        OpcionVisual(
            "Dual Neón", "Tu concepto: hexágono cian, gasolina roja, PRCXE / LAUNCH · nuevo",
            Apariencia.estiloTablero == EstiloTablero.DUAL_NEON, { Apariencia.ponerEstiloTablero(EstiloTablero.DUAL_NEON) },
            Modifier.fillMaxWidth(),
        ) { vistaDualNeon() }
        // Familia "instrumento": como el LCD Sport, todo dibujado como un display de verdad
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionVisual(
                "Race LCD", "Superbike: escalera y modo carrera",
                Apariencia.estiloTablero == EstiloTablero.RACE, { Apariencia.ponerEstiloTablero(EstiloTablero.RACE) },
                Modifier.weight(1f),
            ) { vistaRaceLcd(Paleta.lima, Paleta.rpmAlta) }
            OpcionVisual(
                "VFD 85", "Fluorescente ochentero",
                Apariencia.estiloTablero == EstiloTablero.VFD, { Apariencia.ponerEstiloTablero(EstiloTablero.VFD) },
                Modifier.weight(1f),
            ) { vistaVfd(Paleta.lima) }
            OpcionVisual(
                "Anillo", "Bisel de 60 segmentos",
                Apariencia.estiloTablero == EstiloTablero.ANILLO, { Apariencia.ponerEstiloTablero(EstiloTablero.ANILLO) },
                Modifier.weight(1f),
            ) { vistaAnillo(Paleta.lima, Paleta.rpmAlta) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionVisual(
                "LCD Sport", "Segmentos, negro AMOLED",
                Apariencia.estiloTablero == EstiloTablero.LCD, { Apariencia.ponerEstiloTablero(EstiloTablero.LCD) },
                Modifier.weight(1f),
            ) { vistaLcd(Paleta.rpmBaja, Paleta.rpmMedia, Paleta.rpmAlta, Paleta.lima) }
            OpcionVisual(
                "Clásico", "Tacómetro circular",
                Apariencia.estiloTablero == EstiloTablero.CLASICO, { Apariencia.ponerEstiloTablero(EstiloTablero.CLASICO) },
                Modifier.weight(1f),
            ) { vistaClasico(Paleta.lima, Paleta.rpmAlta) }
            OpcionVisual(
                "Pista", "RPM tipo MotoGP · nuevo",
                Apariencia.estiloTablero == EstiloTablero.PISTA, { Apariencia.ponerEstiloTablero(EstiloTablero.PISTA) },
                Modifier.weight(1f),
            ) { vistaPista(Paleta.lima, Paleta.rpmAlta) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionVisual(
                "Minimal", "Solo lo esencial · nuevo",
                Apariencia.estiloTablero == EstiloTablero.MINIMAL, { Apariencia.ponerEstiloTablero(EstiloTablero.MINIMAL) },
                Modifier.weight(1f),
            ) { vistaMinimal(Paleta.lima) }
            OpcionVisual(
                "Telemetría", "Gráfica de 60 s · nuevo",
                Apariencia.estiloTablero == EstiloTablero.TELEMETRIA, { Apariencia.ponerEstiloTablero(EstiloTablero.TELEMETRIA) },
                Modifier.weight(1f),
            ) { vistaTelemetria(Paleta.lima, Paleta.rpmAlta) }
            OpcionVisual(
                "Bento", "Mosaico de tarjetas · nuevo",
                Apariencia.estiloTablero == EstiloTablero.BENTO, { Apariencia.ponerEstiloTablero(EstiloTablero.BENTO) },
                Modifier.weight(1f),
            ) { vistaBento(Paleta.lima) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionVisual(
                "Superbike", "TFT de moto deportiva · nuevo",
                Apariencia.estiloTablero == EstiloTablero.SUPERBIKE, { Apariencia.ponerEstiloTablero(EstiloTablero.SUPERBIKE) },
                Modifier.weight(1f),
            ) { vistaSuperbike(Paleta.lima, Paleta.rpmAlta) }
            OpcionVisual(
                "F1", "Volante con 15 LEDs · nuevo",
                Apariencia.estiloTablero == EstiloTablero.F1, { Apariencia.ponerEstiloTablero(EstiloTablero.F1) },
                Modifier.weight(1f),
            ) { vistaF1(Paleta.rpmBaja, Paleta.rpmMedia, Paleta.rpmAlta) }
            OpcionVisual(
                "Arrancón", "RPM vertical y tus tiempos · nuevo",
                Apariencia.estiloTablero == EstiloTablero.ARRANCON, { Apariencia.ponerEstiloTablero(EstiloTablero.ARRANCON) },
                Modifier.weight(1f),
            ) { vistaArrancon(Paleta.lima, Paleta.rpmAlta) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionVisual(
                "Rally", "Brújula, roadbook y altitud · nuevo",
                Apariencia.estiloTablero == EstiloTablero.RALLY, { Apariencia.ponerEstiloTablero(EstiloTablero.RALLY) },
                Modifier.weight(1f),
            ) { vistaRally(Paleta.lima) }
            OpcionVisual(
                "Arcade", "Dígitos de 7 segmentos · nuevo",
                Apariencia.estiloTablero == EstiloTablero.ARCADE, { Apariencia.ponerEstiloTablero(EstiloTablero.ARCADE) },
                Modifier.weight(1f),
            ) { vistaArcade(Paleta.lima, Paleta.rpmAlta) }
            OpcionVisual(
                "Universal", "Como el digital de Italika · nuevo",
                Apariencia.estiloTablero == EstiloTablero.UNIVERSAL, { Apariencia.ponerEstiloTablero(EstiloTablero.UNIVERSAL) },
                Modifier.weight(1f),
            ) { vistaUniversal() }
        }
        Text("El tablero es independiente del tema: cambia solo el diseño, tus colores se quedan. " +
            "Atajo: mantén presionado el tablero para pasar al siguiente.", color = Paleta.textoSec, fontSize = 11.sp)
        Subtitulo("Barra de RPM del LCD")
        Selector(listOf("Según el tema", "Curva", "Diagonal 45°"), Apariencia.barraRpm, { Apariencia.ponerBarraRpm(it) }, Modifier.fillMaxWidth())
        Text("Curva: sube redondeado y sigue recta. Diagonal 45°: sube en línea recta a 45°, con bloques inclinados y un riel " +
            "encima (más agresiva). \"Según el tema\": diagonal en Filo, Pixel y Táctico; curva en Carbono y Vidrio.",
            color = Paleta.textoSec, fontSize = 11.sp)

        Subtitulo("Shift light (RPM bajas · medias · altas)")
        Apariencia.COLORES_RPM.forEachIndexed { i, c ->
            FilaColoresRpm(
                c.nombre, Color(c.baja), Color(c.media), Color(c.alta),
                i == Apariencia.coloresRpm,
            ) { Apariencia.ponerColoresRpm(i) }
        }
        ProbarShift()

        BotonDeportivo(
            "Restablecer apariencia", { confirmarReset = true }, Modifier.fillMaxWidth(),
            primario = false, color = Paleta.textoSec, tamanoTexto = 12.sp,
        )
    }

    if (confirmarReset) {
        AlertDialog(
            onDismissRequest = { confirmarReset = false },
            title = { Text("¿Restablecer apariencia?") },
            text = { Text("Vuelve al tema Carbono, acento lima, tablero LCD Sport y shift light verde · ámbar · azul.") },
            confirmButton = {
                TextButton(onClick = { confirmarReset = false; Apariencia.restablecer() }) { Text("Restablecer") }
            },
            dismissButton = { TextButton(onClick = { confirmarReset = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Subtitulo(texto: String) {
    Text(texto, color = Paleta.textoSec, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp))
}

/** Tarjeta elegible con una vista previa dibujada. */
@Composable
private fun OpcionVisual(
    titulo: String,
    detalle: String,
    elegida: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    vista: DrawScope.() -> Unit,
) {
    val forma = Formas.chica
    Column(
        modifier.clip(forma)
            .background(if (elegida) Paleta.lima.copy(alpha = 0.10f) else Paleta.superficie2)
            .border(if (elegida) 2.dp else 1.dp, if (elegida) Paleta.lima else Paleta.borde, forma)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(6.dp)).background(Color.Black)) { vista() }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, color = if (elegida) Paleta.lima else Paleta.texto, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (elegida) Palomita(Paleta.lima)
        }
        Text(detalle, color = Paleta.textoSec, fontSize = 11.sp, maxLines = 1)
    }
}

/** Palomita pixelada (5x4 bloques). */
@Composable
private fun Palomita(color: Color, tam: Int = 12) {
    Canvas(Modifier.size(tam.dp)) {
        val b = size.width / 6f
        val celdas = listOf(4 to 1, 5 to 0, 3 to 2, 2 to 3, 1 to 2, 0 to 1)
        for ((x, y) in celdas) drawRect(color, Offset(x * b, (y + 1) * b), Size(b, b))
    }
}

/** Cuadrito de color de acento. */
@Composable
private fun Muestra(color: Color, elegida: Boolean, onClick: () -> Unit) {
    val forma = Formas.chip
    Box(
        Modifier.size(40.dp).clip(forma)
            .background(color)
            .then(if (elegida) Modifier.border(3.dp, Color.White, forma) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (elegida) Palomita(Color.Black, 14)
    }
}

/** Acento personalizado: muestra con todos los colores. */
@Composable
private fun MuestraArcoiris(elegida: Boolean, onClick: () -> Unit) {
    val forma = Formas.chip
    Box(
        Modifier.size(40.dp).clip(forma)
            .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
            .then(if (elegida) Modifier.border(3.dp, Color.White, forma) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (elegida) Palomita(Color.White, 14) else Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Fila con los tres colores del shift light en bloques pixel. */
@Composable
private fun FilaColoresRpm(nombre: String, baja: Color, media: Color, alta: Color, elegida: Boolean, onClick: () -> Unit) {
    val forma = Formas.chica
    Row(
        Modifier.fillMaxWidth().clip(forma)
            .background(if (elegida) Paleta.lima.copy(alpha = 0.10f) else Color.Transparent)
            .border(if (elegida) 2.dp else 1.dp, if (elegida) Paleta.lima else Paleta.borde, forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(108.dp).height(14.dp)) {
            val n = 9
            val hueco = 2.dp.toPx()
            val w = (size.width - hueco * (n - 1)) / n
            for (i in 0 until n) {
                val c = when { i < 3 -> baja; i < 6 -> media; else -> alta }
                drawRect(c, Offset(i * (w + hueco), 0f), Size(w, size.height))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(nombre, color = if (elegida) Paleta.texto else Paleta.textoSec, fontSize = 13.sp,
            fontWeight = if (elegida) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
        if (elegida) Palomita(Paleta.lima)
    }
}

/** Barrido de prueba del shift light (solo se anima cuando lo tocas). */
@Composable
private fun ProbarShift() {
    val scope = rememberCoroutineScope()
    val rpm = remember { Animatable(0f) }
    val shift = 7000
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BarraShift(rpm.value.toInt(), shift, Modifier.weight(1f))
        BotonDeportivo("Probar", {
            scope.launch {
                rpm.snapTo(1200f)
                rpm.animateTo(shift * 1.04f, tween(2200, easing = LinearEasing))
                kotlinx.coroutines.delay(900)
                rpm.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
            }
        }, primario = false, tamanoTexto = 12.sp)
    }
}

// ---------- Vistas previas dibujadas ----------

private fun DrawScope.vistaCarbono(acento: Color) {
    val w = size.width; val h = size.height
    val m = w * 0.08f
    val corte = h * 0.18f
    fun tarjeta(x: Float, y: Float, tw: Float, th: Float) {
        val p = Path().apply {
            moveTo(x + corte, y); lineTo(x + tw, y); lineTo(x + tw, y + th - corte)
            lineTo(x + tw - corte, y + th); lineTo(x, y + th); lineTo(x, y + corte); close()
        }
        drawPath(p, Color(0xFF15191C))
        drawPath(p, Color(0xFF2C3438), style = Stroke(1.5f))
        drawRect(acento, Offset(x, y + corte), Size(3f, th - corte * 1.5f))
    }
    tarjeta(m, h * 0.14f, w * 0.40f, h * 0.72f)
    tarjeta(w * 0.52f, h * 0.14f, w * 0.40f, h * 0.32f)
    tarjeta(w * 0.52f, h * 0.54f, w * 0.40f, h * 0.32f)
    // franjas diagonales tipo carenado
    for (i in 0..2) {
        val x = m + w * 0.07f + i * w * 0.05f
        val p = Path().apply {
            moveTo(x, h * 0.76f); lineTo(x + w * 0.05f, h * 0.30f)
            lineTo(x + w * 0.075f, h * 0.30f); lineTo(x + w * 0.025f, h * 0.76f); close()
        }
        drawPath(p, acento.copy(alpha = 0.6f - i * 0.18f))
    }
}

/** Filo: barra de segmentos con el activo en acento, línea que baja a 45° y tarjetas muy cortadas con acentos HUD. */
private fun DrawScope.vistaFilo(acento: Color) {
    val w = size.width; val h = size.height
    val m = w * 0.06f
    val gris = Color(0xFF2A3236)
    val oscuro = Color(0xFF0B0E10)
    fun cortada(x: Float, y: Float, cw: Float, ch: Float, c1: Float, c2: Float) = Path().apply {
        moveTo(x + c1, y); lineTo(x + cw - c2, y); lineTo(x + cw, y + c2); lineTo(x + cw, y + ch - c1)
        lineTo(x + cw - c1, y + ch); lineTo(x + c2, y + ch); lineTo(x, y + ch - c2); lineTo(x, y + c1); close()
    }
    // barra de segmentos
    val y0 = h * 0.08f
    val segH = h * 0.15f
    var x = m
    listOf(0.09f, 0.21f, 0.21f, 0.21f, 0.09f).forEachIndexed { i, a ->
        val sw = w * a
        val p = cortada(x, y0, sw, segH, segH * 0.4f, 0f)
        drawPath(p, if (i == 1) acento else oscuro)
        drawPath(p, gris, style = Stroke(1.2f))
        x += sw + w * 0.012f
    }
    // línea gruesa que baja a 45° y sigue delgada
    val yl = y0 + segH + h * 0.04f
    val fin = m + w * 0.52f
    drawRect(acento, Offset(m, yl), Size(fin - m, h * 0.025f))
    drawLine(acento, Offset(fin, yl), Offset(fin + h * 0.05f, yl + h * 0.05f), strokeWidth = 2.5f)
    drawLine(gris, Offset(fin + h * 0.05f, yl + h * 0.05f), Offset(w - m, yl + h * 0.05f), strokeWidth = 1.2f)
    // tarjetas con cortes grandes y el corte marcado con el acento
    fun tarjeta(tx: Float, ty: Float, tw: Float, th: Float) {
        val c = minOf(tw, th) * 0.30f
        drawPath(cortada(tx, ty, tw, th, c, c * 0.25f), oscuro)
        drawPath(cortada(tx, ty, tw, th, c, c * 0.25f), gris, style = Stroke(1.2f))
        drawPath(Path().apply { moveTo(tx, ty + c * 1.6f); lineTo(tx, ty + c); lineTo(tx + c, ty); lineTo(tx + c * 1.9f, ty) },
            acento, style = Stroke(2.5f))
    }
    tarjeta(m, h * 0.38f, w * 0.42f, h * 0.54f)
    tarjeta(w * 0.52f, h * 0.38f, w * 0.42f, h * 0.24f)
    tarjeta(w * 0.52f, h * 0.68f, w * 0.42f, h * 0.24f)
}

/** Pixel: tarjetas con los cortes en escalones y cuadritos del acento. */
private fun DrawScope.vistaPixel(acento: Color) {
    val w = size.width; val h = size.height
    val gris = Color(0xFF2A332A)
    val oscuro = Color(0xFF0B0E0B)
    fun escalones(x: Float, y: Float, cw: Float, ch: Float, t: Float, pasos: Int) = Path().apply {
        val s = t / pasos
        moveTo(x, y + t)
        for (i in 1..pasos) { lineTo(x + (i - 1) * s, y + t - i * s); lineTo(x + i * s, y + t - i * s) }
        lineTo(x + cw, y); lineTo(x + cw, y + ch - t)
        for (i in 1..pasos) { lineTo(x + cw - (i - 1) * s, y + ch - t + i * s); lineTo(x + cw - i * s, y + ch - t + i * s) }
        lineTo(x, y + ch); close()
    }
    // puntos grandes de fondo
    var yy = h * 0.05f
    while (yy < h) { var xx = w * 0.03f; while (xx < w) { drawRect(Color.White.copy(alpha = 0.05f), Offset(xx, yy), Size(2f, 2f)); xx += w * 0.04f }; yy += h * 0.09f }
    var x = w * 0.06f
    listOf(0.09f, 0.21f, 0.21f, 0.21f, 0.09f).forEachIndexed { i, a ->
        val sw = w * a
        val p = escalones(x, h * 0.08f, sw, h * 0.15f, h * 0.09f, 3)
        drawPath(p, if (i == 1) acento else oscuro); drawPath(p, gris, style = Stroke(2f))
        x += sw + w * 0.012f
    }
    fun tarjeta(tx: Float, ty: Float, tw: Float, th: Float) {
        val t = minOf(tw, th) * 0.28f
        val p = escalones(tx, ty, tw, th, t, 4)
        drawPath(p, oscuro); drawPath(p, gris, style = Stroke(2.5f))
        val s = t / 4
        for (i in 0 until 4) drawRect(acento, Offset(tx + i * s, ty + t - (i + 1) * s), Size(s * 0.9f, s * 0.9f))
    }
    tarjeta(w * 0.06f, h * 0.36f, w * 0.42f, h * 0.56f)
    tarjeta(w * 0.52f, h * 0.36f, w * 0.42f, h * 0.25f)
    tarjeta(w * 0.52f, h * 0.67f, w * 0.42f, h * 0.25f)
}

/** Táctico: tarjetas casi rectas con corchetes, retícula de cruces y barrido. */
private fun DrawScope.vistaTactico(acento: Color) {
    val w = size.width; val h = size.height
    var yy = 0f
    while (yy < h) { drawLine(Color.White.copy(alpha = 0.03f), Offset(0f, yy), Offset(w, yy), 1f); yy += 3f }
    var cy = h * 0.2f
    while (cy < h) {
        var cx = w * 0.1f
        while (cx < w) {
            drawLine(acento.copy(alpha = 0.25f), Offset(cx - 3f, cy), Offset(cx + 3f, cy), 1f)
            drawLine(acento.copy(alpha = 0.25f), Offset(cx, cy - 3f), Offset(cx, cy + 3f), 1f)
            cx += w * 0.16f
        }
        cy += h * 0.3f
    }
    fun corchetes(x: Float, y: Float, cw: Float, ch: Float) {
        drawRect(Color(0xFF070908), Offset(x, y), Size(cw, ch))
        drawRect(Color(0xFF1C2420), Offset(x, y), Size(cw, ch), style = Stroke(1f))
        val l = minOf(cw, ch) * 0.25f
        listOf(Triple(x, y, 1f to 1f), Triple(x + cw, y, -1f to 1f), Triple(x, y + ch, 1f to -1f), Triple(x + cw, y + ch, -1f to -1f))
            .forEachIndexed { i, (px, py, sxy) ->
                val (sx, sy) = sxy
                val c = if (i == 0) acento else acento.copy(alpha = 0.55f)
                drawLine(c, Offset(px, py), Offset(px + sx * l, py), 2.5f)
                drawLine(c, Offset(px, py), Offset(px, py + sy * l), 2.5f)
            }
    }
    var x = w * 0.06f
    listOf(0.09f, 0.21f, 0.21f, 0.21f, 0.09f).forEachIndexed { i, a ->
        val sw = w * a
        if (i == 1) drawRect(acento, Offset(x, h * 0.08f), Size(sw, h * 0.15f))
        else drawRect(Color(0xFF1C2420), Offset(x, h * 0.08f), Size(sw, h * 0.15f), style = Stroke(1.2f))
        x += sw + w * 0.012f
    }
    corchetes(w * 0.06f, h * 0.36f, w * 0.42f, h * 0.56f)
    corchetes(w * 0.52f, h * 0.36f, w * 0.42f, h * 0.25f)
    corchetes(w * 0.52f, h * 0.67f, w * 0.42f, h * 0.25f)
}

/** Holo: tarjetas octagonales con doble contorno teñido del acento, esquina iluminada y regla de marcas. */
private fun DrawScope.vistaHolo(acento: Color) {
    val w = size.width; val h = size.height
    drawRect(Brush.radialGradient(listOf(acento.copy(alpha = 0.22f), Color.Transparent), Offset(w * 0.1f, h * 0.1f), w * 0.6f))
    fun octagono(x: Float, y: Float, ow: Float, oh: Float, c: Float) = Path().apply {
        moveTo(x + c, y); lineTo(x + ow - c, y); lineTo(x + ow, y + c); lineTo(x + ow, y + oh - c)
        lineTo(x + ow - c, y + oh); lineTo(x + c, y + oh); lineTo(x, y + oh - c); lineTo(x, y + c); close()
    }
    fun tarjeta(x: Float, y: Float, tw: Float, th: Float) {
        val c = minOf(tw, th) * 0.16f
        val p = octagono(x, y, tw, th, c)
        drawPath(p, acento.copy(alpha = 0.07f))
        drawPath(p, acento.copy(alpha = 0.6f), style = Stroke(1.2f))
        drawPath(octagono(x + 3f, y + 3f, tw - 6f, th - 6f, c * 0.8f), acento.copy(alpha = 0.2f), style = Stroke(1f))
        drawLine(acento, Offset(x, y + c * 1.8f), Offset(x, y + c), 2.5f)
        drawLine(acento, Offset(x, y + c), Offset(x + c, y), 2.5f)
        drawLine(acento, Offset(x + c, y), Offset(x + c * 2.6f, y), 2.5f)
        for (i in 0 until 4) {
            val mx = x + tw - c - 4f - i * 5f
            drawLine(acento.copy(alpha = 0.5f), Offset(mx, y + 5f), Offset(mx, y + if (i % 3 == 0) 10f else 8f), 1f)
        }
    }
    var x = w * 0.06f
    listOf(0.09f, 0.21f, 0.21f, 0.21f, 0.09f).forEachIndexed { i, a ->
        val sw = w * a
        val p = octagono(x, h * 0.08f, sw, h * 0.15f, h * 0.05f)
        if (i == 1) drawPath(p, acento) else drawPath(p, acento.copy(alpha = 0.35f), style = Stroke(1.2f))
        x += sw + w * 0.012f
    }
    tarjeta(w * 0.06f, h * 0.36f, w * 0.42f, h * 0.56f)
    tarjeta(w * 0.52f, h * 0.36f, w * 0.42f, h * 0.25f)
    tarjeta(w * 0.52f, h * 0.67f, w * 0.42f, h * 0.25f)
}

/** Ácido: corte grande arriba-izquierda con franjas paralelas, cinta de rayas a 45° y brillo tóxico abajo. */
private fun DrawScope.vistaAcido(acento: Color) {
    val w = size.width; val h = size.height
    drawRect(Brush.radialGradient(listOf(acento.copy(alpha = 0.28f), Color.Transparent), Offset(0f, h), w * 0.6f))
    fun tarjeta(x: Float, y: Float, tw: Float, th: Float) {
        val c = minOf(tw, th) * 0.32f
        val p = Path().apply { moveTo(x + c, y); lineTo(x + tw, y); lineTo(x + tw, y + th - 3f); lineTo(x + tw - 3f, y + th); lineTo(x, y + th); lineTo(x, y + c); close() }
        drawPath(p, Color(0xFF13160C))
        drawPath(p, acento.copy(alpha = 0.4f), style = Stroke(1.2f))
        for (i in 1..3) {
            val k = c + i * 4f
            drawLine(acento.copy(alpha = 0.55f - i * 0.15f), Offset(x, y + k), Offset(x + k, y), 2f)
        }
        drawLine(acento, Offset(x, y + c), Offset(x + c, y), 2.5f)
    }
    // cinta de peligro abajo
    var cx = w * 0.06f
    while (cx < w * 0.45f) {
        drawPath(Path().apply { moveTo(cx, h); lineTo(cx + 4f, h); lineTo(cx + 9f, h - 5f); lineTo(cx + 5f, h - 5f); close() }, acento.copy(alpha = 0.35f))
        cx += 8f
    }
    var x = w * 0.06f
    listOf(0.09f, 0.21f, 0.21f, 0.21f, 0.09f).forEachIndexed { i, a ->
        val sw = w * a
        val c = h * 0.07f
        val p = Path().apply { moveTo(x + c, h * 0.08f); lineTo(x + sw, h * 0.08f); lineTo(x + sw, h * 0.23f); lineTo(x, h * 0.23f); lineTo(x, h * 0.08f + c); close() }
        if (i == 1) drawPath(p, acento) else drawPath(p, acento.copy(alpha = 0.3f), style = Stroke(1.2f))
        x += sw + w * 0.012f
    }
    tarjeta(w * 0.06f, h * 0.34f, w * 0.42f, h * 0.52f)
    tarjeta(w * 0.52f, h * 0.34f, w * 0.42f, h * 0.24f)
    tarjeta(w * 0.52f, h * 0.62f, w * 0.42f, h * 0.24f)
}

/** Circuito: pistas a 45° con pads y vías al fondo; tarjetas con cortes arriba-derecha y abajo-izquierda. */
private fun DrawScope.vistaCircuito(acento: Color) {
    val w = size.width; val h = size.height
    val r = java.util.Random(150)
    repeat(9) {
        var x = w * (0.05f + r.nextFloat() * 0.9f)
        var y = h * (0.1f + r.nextFloat() * 0.8f)
        val p = Path().apply { moveTo(x, y) }
        drawRect(acento.copy(alpha = 0.3f), Offset(x - 2f, y - 2f), Size(4f, 4f))
        x += w * 0.12f; p.lineTo(x, y)
        x += h * 0.12f; y += h * 0.12f; p.lineTo(x, y)
        drawPath(p, acento.copy(alpha = 0.2f), style = Stroke(1f))
        drawCircle(acento.copy(alpha = 0.35f), 2.5f, Offset(x, y), style = Stroke(1f))
    }
    fun tarjeta(x: Float, y: Float, tw: Float, th: Float) {
        val c = minOf(tw, th) * 0.2f
        val p = Path().apply { moveTo(x, y); lineTo(x + tw - c, y); lineTo(x + tw, y + c); lineTo(x + tw, y + th); lineTo(x + c, y + th); lineTo(x, y + th - c); close() }
        drawPath(p, Color(0xFF0E1618))
        drawPath(p, Color(0xFF1B2A2D), style = Stroke(1.2f))
        drawLine(acento, Offset(x + tw - c, y), Offset(x + tw, y + c), 2.5f)
        drawLine(acento.copy(alpha = 0.45f), Offset(x, y + th - c), Offset(x + c, y + th), 2.5f)
        drawRect(acento.copy(alpha = 0.7f), Offset(x + 4f, y + 4f), Size(4f, 4f), style = Stroke(1f))
        drawLine(acento.copy(alpha = 0.45f), Offset(x + 8f, y + 6f), Offset(x + tw * 0.35f, y + 6f), 1f)
    }
    var x = w * 0.06f
    listOf(0.09f, 0.21f, 0.21f, 0.21f, 0.09f).forEachIndexed { i, a ->
        val sw = w * a
        val c = h * 0.05f
        val p = Path().apply { moveTo(x, h * 0.08f); lineTo(x + sw - c, h * 0.08f); lineTo(x + sw, h * 0.08f + c); lineTo(x + sw, h * 0.23f); lineTo(x + c, h * 0.23f); lineTo(x, h * 0.23f - c); close() }
        if (i == 1) drawPath(p, acento) else drawPath(p, Color(0xFF1B2A2D), style = Stroke(1.2f))
        x += sw + w * 0.012f
    }
    tarjeta(w * 0.06f, h * 0.36f, w * 0.42f, h * 0.56f)
    tarjeta(w * 0.52f, h * 0.36f, w * 0.42f, h * 0.25f)
    tarjeta(w * 0.52f, h * 0.67f, w * 0.42f, h * 0.25f)
}

/** Vértice: riel a la izquierda con la hoja activa, la línea de carrera y losas sin contorno sobre fibra de carbono. */
private fun DrawScope.vistaVertice(acento: Color) {
    val w = size.width; val h = size.height
    var x = -h
    while (x < w) { drawLine(Color.White.copy(alpha = 0.05f), Offset(x, h), Offset(x + h, 0f), 1f); x += 6f }
    // riel
    val rw = w * 0.13f
    drawRect(Color(0xFF242B2F), Offset(rw, h * 0.05f), Size(1.2f, h * 0.9f))
    for (i in 0 until 4) {
        val y = h * (0.22f + i * 0.18f)
        if (i == 1) {
            val c = h * 0.05f
            drawPath(Path().apply { moveTo(w * 0.02f + c, y); lineTo(rw - 3f, y); lineTo(rw - 3f, y + h * 0.13f - c); lineTo(rw - 3f - c, y + h * 0.13f); lineTo(w * 0.02f, y + h * 0.13f); lineTo(w * 0.02f, y + c); close() }, acento)
            drawRect(acento, Offset(rw - 1f, y), Size(2.5f, h * 0.13f))
        } else drawRect(Color.White.copy(alpha = 0.25f), Offset(w * 0.045f, y + h * 0.04f), Size(rw * 0.45f, h * 0.05f))
    }
    // velocidad arriba del riel
    drawRect(Color.White.copy(alpha = 0.7f), Offset(w * 0.03f, h * 0.06f), Size(rw * 0.6f, h * 0.08f))
    // línea de carrera sobre el contenido
    drawLine(acento, Offset(rw + 4f, h * 0.09f), Offset(rw + 4f + h * 0.04f, h * 0.05f), 2f)
    drawRect(Brush.horizontalGradient(listOf(acento, Color.Transparent), startX = rw + 8f, endX = w * 0.7f), Offset(rw + 8f, h * 0.05f), Size(w * 0.6f, 2f))
    // losas: degradado, vértice iluminado, luz de borde (sin contorno)
    fun losa(lx: Float, ly: Float, lw: Float, lh: Float) {
        val c = minOf(lw, lh) * 0.22f
        val p = Path().apply { moveTo(lx + c, ly); lineTo(lx + lw, ly); lineTo(lx + lw, ly + lh - 4f); lineTo(lx + lw - 4f, ly + lh); lineTo(lx, ly + lh); lineTo(lx, ly + c); close() }
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFF161B1E), Color(0xFF0C0F11)), startY = ly, endY = ly + lh))
        drawLine(acento, Offset(lx, ly + c * 1.6f), Offset(lx, ly + c), 2.5f)
        drawLine(acento, Offset(lx, ly + c), Offset(lx + c, ly), 2.5f)
        drawLine(acento, Offset(lx + c, ly), Offset(lx + c * 2.4f, ly), 2.5f)
        drawRect(Brush.horizontalGradient(listOf(acento.copy(alpha = 0.5f), Color.Transparent), startX = lx + c * 2.4f, endX = lx + lw * 0.8f), Offset(lx + c * 2.4f, ly), Size(lw * 0.6f, 1f))
    }
    losa(w * 0.2f, h * 0.16f, w * 0.44f, h * 0.76f)
    losa(w * 0.68f, h * 0.16f, w * 0.28f, h * 0.36f)
    losa(w * 0.68f, h * 0.56f, w * 0.28f, h * 0.36f)
}

/** Tinta: líneas de velocidad, trama de puntos y tres viñetas con contorno de tinta y sombra del acento. */
private fun DrawScope.vistaTinta(acento: Color) {
    val w = size.width; val h = size.height
    val r = java.util.Random(7)
    val fx = -w * 0.1f; val fy = h * 0.45f
    repeat(22) {
        val a = Math.toRadians(-35.0 + r.nextDouble() * 70.0)
        val d0 = w * (0.3f + r.nextFloat() * 0.3f)
        drawLine(Color.White.copy(alpha = 0.10f), Offset(fx + d0 * Math.cos(a).toFloat(), fy + d0 * Math.sin(a).toFloat()),
            Offset(fx + w * 1.6f * Math.cos(a).toFloat(), fy + w * 1.6f * Math.sin(a).toFloat()), 1.2f)
    }
    var y = h * 0.6f
    while (y < h) { var x = w * 0.6f; while (x < w) { drawCircle(acento.copy(alpha = 0.18f), 2.2f * ((x - w * 0.6f) / (w * 0.4f) + 0.2f), Offset(x, y)); x += 7f }; y += 7f }
    val tintaC = Color(0xFFF2F0EA)
    fun vineta(lx: Float, ly: Float, lw: Float, lh: Float) {
        val caida = lh * 0.06f
        val p = Path().apply { moveTo(lx, ly); lineTo(lx + lw, ly + caida); lineTo(lx + lw, ly + lh); lineTo(lx, ly + lh - caida * 0.6f); close() }
        val s = Path().apply { addPath(p, Offset(4f, 4f)) }
        drawPath(s, acento)
        drawPath(p, Color(0xFF111111))
        drawPath(p, tintaC, style = Stroke(2.5f, join = StrokeJoin.Miter))
        for (i in 0 until 4) for (j in 0 until 3) {
            val k = 1f - (i * 0.2f + j * 0.25f)
            if (k > 0.1f) drawCircle(tintaC.copy(alpha = 0.4f), 1.6f * k, Offset(lx + lw - 6f - i * 5f, ly + 7f + j * 5f))
        }
    }
    vineta(w * 0.05f, h * 0.10f, w * 0.48f, h * 0.78f)
    vineta(w * 0.58f, h * 0.10f, w * 0.36f, h * 0.36f)
    vineta(w * 0.58f, h * 0.54f, w * 0.36f, h * 0.34f)
}

private fun DrawScope.vistaVidrio(acento: Color, segundo: Color) {
    val w = size.width; val h = size.height
    drawRect(Brush.radialGradient(listOf(acento.copy(alpha = 0.55f), Color.Transparent), Offset(w * 0.15f, h * 0.1f), w * 0.55f))
    drawRect(Brush.radialGradient(listOf(segundo.copy(alpha = 0.45f), Color.Transparent), Offset(w * 0.95f, h * 0.95f), w * 0.55f))
    val r = CornerRadius(h * 0.16f)
    val borde = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f)))
    fun tarjeta(x: Float, y: Float, tw: Float, th: Float) {
        drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(x, y), Size(tw, th), r)
        drawRoundRect(borde, Offset(x, y), Size(tw, th), r, style = Stroke(1.5f))
    }
    tarjeta(w * 0.08f, h * 0.14f, w * 0.40f, h * 0.72f)
    tarjeta(w * 0.52f, h * 0.14f, w * 0.40f, h * 0.32f)
    tarjeta(w * 0.52f, h * 0.54f, w * 0.40f, h * 0.32f)
}

private fun DrawScope.vistaLcd(baja: Color, media: Color, alta: Color, acento: Color) {
    val w = size.width; val h = size.height
    // barra de RPM en bloques que sube
    val n = 14
    val x0 = w * 0.06f
    val ancho = w * 0.88f
    val paso = ancho / n
    for (i in 0 until n) {
        val alto = h * (0.10f + 0.16f * i / (n - 1))
        val c = when {
            i < n * 0.55f -> baja
            i < n * 0.8f -> media
            else -> alta
        }
        val encendido = i < 11
        drawRect(if (encendido) c else Color.White.copy(alpha = 0.08f),
            Offset(x0 + i * paso, h * 0.36f - alto), Size(paso * 0.7f, alto))
    }
    // velocidad en 7 segmentos
    val p = Path()
    val dh = h * 0.40f
    val dw = dh * 0.55f
    val sx = w * 0.30f
    val fantasma = Color.White.copy(alpha = 0.07f)
    digito7('6', sx, h * 0.46f, dw, dh, Color.White, fantasma, p)
    digito7('4', sx + dw * 1.35f, h * 0.46f, dw, dh, Color.White, fantasma, p)
    // marcha
    drawRect(acento, Offset(w * 0.07f, h * 0.48f), Size(w * 0.15f, h * 0.36f), style = Stroke(2f))
    textoPixel("3", w * 0.07f + w * 0.05f, h * 0.55f, h * 0.035f, acento)
    textoPixel("KM/H", sx + dw * 2.6f, h * 0.76f, h * 0.022f, acento)
}

private fun DrawScope.vistaClasico(acento: Color, alta: Color) {
    val w = size.width; val h = size.height
    val r = h * 0.40f
    val c = Offset(w * 0.5f, h * 0.55f)
    val seg = 24
    for (i in 0 until seg) {
        val ang = Math.toRadians((150.0 + 240.0 * i / (seg - 1)))
        val cos = kotlin.math.cos(ang).toFloat(); val sin = kotlin.math.sin(ang).toFloat()
        val col = when {
            i >= seg - 4 -> alta
            i < 15 -> acento
            else -> Color.White.copy(alpha = 0.12f)
        }
        drawLine(col, Offset(c.x + cos * r * 0.78f, c.y + sin * r * 0.78f), Offset(c.x + cos * r, c.y + sin * r), strokeWidth = 4f)
    }
    textoPixel("64", c.x - anchoPixel("64", h * 0.05f) / 2, c.y - h * 0.15f, h * 0.05f, Color.White)
    textoPixel("KM/H", c.x - anchoPixel("KM/H", h * 0.022f) / 2, c.y + h * 0.10f, h * 0.022f, acento)
}

// ------------------------------------------------------------
// Pantalla
// ------------------------------------------------------------
@Composable
private fun SeccionPantalla() {
    val ctx = LocalContext.current
    val hz = remember { Pantalla.hzMaximos(ctx) }
    Seccion("Pantalla") {
        FilaSwitch("Medidor de fluidez",
            "Muestra en una esquina los cuadros por segundo, el % de tirones (cuadros que tardaron más que la pantalla) " +
                "y el peor cuadro, medidos por Android. Para comprobar que todo va fluido; apágalo al terminar.",
            Apariencia.medidorFluidez) { Apariencia.ponerMedidorFluidez(it) }
        Text("Pantalla encendida", color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Selector(
            opciones = listOf("Siempre", "Con la moto", "Normal"),
            elegido = Apariencia.pantalla.ordinal,
            onElegir = { Apariencia.ponerPantalla(PantallaEncendida.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when (Apariencia.pantalla) {
                PantallaEncendida.SIEMPRE -> "En la moto no se apaga. Fuera de ella, en tus apps se apaga como siempre " +
                    "y en las demás páginas después de 10 min sin tocarla (para que no se descargue en la casa)."
                PantallaEncendida.CON_MOTO -> "No se apaga mientras esté en la moto: conectada por Bluetooth o cargando " +
                    "con el switch prendido (Modo moto). Al apagar la moto se apaga sola."
                PantallaEncendida.NORMAL -> "Se apaga sola como siempre, según tu teléfono."
            },
            color = Paleta.textoSec, fontSize = 12.sp,
        )
        Text("Botones de Android", color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Selector(
            opciones = listOf("Normal", "Sin botones", "Completa"),
            elegido = Apariencia.barras.ordinal,
            onElegir = { Apariencia.ponerBarras(BarrasSistema.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when (Apariencia.barras) {
                BarrasSistema.NORMAL -> "Se ven los botones de atrás, inicio y recientes."
                BarrasSistema.SIN_BOTONES -> "Dentro de la app se esconden los botones de abajo; desliza desde la orilla para verlos."
                BarrasSistema.COMPLETA -> "Sin botones ni barra de estado: el tablero usa toda la pantalla. Desliza desde la orilla para verlos."
            },
            color = Paleta.textoSec, fontSize = 12.sp,
        )
        FilaSwitch("Datos compactos en el tablero",
            "Inclinación, perfil, viaje, batería y clima más chicos, para que el tablero LCD salga más grande",
            Apariencia.datosCompactos) { Apariencia.ponerDatosCompactos(it) }
        FilaSwitch("Siempre horizontal", "La app se queda a lo ancho, ideal en el soporte del manubrio",
            Apariencia.siempreHorizontal) { Apariencia.ponerSiempreHorizontal(it) }
        FilaSwitch("Brillo al máximo", "Para leer el tablero a pleno sol (solo con la app abierta)",
            Apariencia.brilloMax) { Apariencia.ponerBrilloMax(it) }
        FilaSwitch(
            "Fluidez máxima ($hz Hz)",
            if (hz > 60) "Animaciones y mapa a $hz cuadros por segundo (gasta un poco más de pila)"
            else "Tu pantalla trabaja a 60 Hz",
            Apariencia.fluidez,
        ) { Apariencia.ponerFluidez(it) }
        val rendActivo by ModoRendimiento.activo.collectAsStateWithLifecycle()
        val rendNivel by ModoRendimiento.nivel.collectAsStateWithLifecycle()
        val rendSoportado by ModoRendimiento.soportado.collectAsStateWithLifecycle()
        FilaSwitch(
            "Modo rendimiento (root)",
            when {
                !rendSoportado -> "Este teléfono no deja cambiar cómo reparte el trabajo el procesador"
                rendNivel == NivelRendimiento.CARPLAY -> "Activo: núcleos grandes y GPU más alta con CarPlay"
                rendNivel == NivelRendimiento.TABLERO -> "Activo: la app corre en los núcleos grandes del procesador"
                else -> "Con el tablero en pantalla la app corre en los núcleos grandes y, con CarPlay, la GPU sube " +
                    "un escalón: cuadros a la mitad del tiempo. Se apaga solo con la pantalla apagada, en reposo o " +
                    "con el teléfono caliente (43 °C)"
            },
            rendActivo,
        ) { ModoRendimiento.ponerActivo(it) }
    }
}

// ------------------------------------------------------------
// Mapa
// ------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionMapa() {
    val ctx = LocalContext.current
    val hayLuz by Luz.hay.collectAsStateWithLifecycle()
    val usarLuz by Luz.usar.collectAsStateWithLifecycle()
    val lux by Luz.lux.enVivo()
    val baches by Baches.lista.collectAsStateWithLifecycle()
    val detectarBaches by Baches.activo.collectAsStateWithLifecycle()
    val mostrarBaches by Baches.mostrar.collectAsStateWithLifecycle()
    val avisarBaches by Baches.avisar.collectAsStateWithLifecycle()
    val sensBaches by Baches.sensibilidad.collectAsStateWithLifecycle()
    val llaveGuardada by Mapas.llaveCarto.collectAsStateWithLifecycle()
    var llave by remember(llaveGuardada) { mutableStateOf(llaveGuardada) }
    val estilo by Moto.estiloMapa.collectAsStateWithLifecycle()
    val nocheAuto by Moto.nocheAuto.collectAsStateWithLifecycle()
    val rumbo by Moto.rumboArriba.collectAsStateWithLifecycle()
    val limite by Moto.mostrarLimite.collectAsStateWithLifecycle()
    Seccion("Mapa") {
        Subtitulo("Estilo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EstiloMapa.entries.forEach { e ->
                val sel = e == estilo
                Row(
                    Modifier.clip(Formas.chip)
                        .background(if (sel) Paleta.lima else Paleta.superficie2)
                        .border(1.dp, if (sel) Paleta.lima else Paleta.borde, Formas.chip)
                        .clickable { Moto.ponerEstiloMapa(e) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // muestra del color del mapa
                    Box(Modifier.size(10.dp).background(muestraMapa(e)).border(1.dp, Color.Black.copy(alpha = 0.4f)))
                    Spacer(Modifier.width(7.dp))
                    Text(e.nombre, color = if (sel) Color.Black else Paleta.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        FilaSwitch("Noche automática", "Al oscurecer cambia solo al estilo Noche (si elegiste uno claro)", nocheAuto) {
            Moto.ponerNocheAuto(it)
        }
        if (nocheAuto && hayLuz) {
            FilaSwitch("Con el sensor de luz", "Usa la luz real (de noche, en túneles o con nublado fuerte), no la hora. " +
                "Ahora: ${if (lux >= 0) "${lux.toInt()} lux" else "--"}", usarLuz) { Luz.ponerUsar(it) }
        }
        Subtitulo("Baches")
        FilaSwitch("Detectar baches", "El teléfono siente los golpes de arriba-abajo al rodar y los marca en el mapa " +
            "(${baches.size} guardados)", detectarBaches) { Baches.ponerActivo(it) }
        if (detectarBaches) {
            Selector(listOf("Sensibilidad baja", "Media", "Alta"), sensBaches, { Baches.ponerSensibilidad(it) }, Modifier.fillMaxWidth())
            FilaSwitch("Mostrar en el mapa", "Cuadritos ámbar (bache) y rojos (bache fuerte)", mostrarBaches) { Baches.ponerMostrar(it) }
            FilaSwitch("Avisar antes de llegar", "Por voz, si vas a pasar por uno que ya conoces (a más de 20 km/h)", avisarBaches) {
                Baches.ponerAvisar(it)
            }
            BotonDeportivo("Borrar baches", { Baches.borrarTodos() }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        }
        FilaSwitch("Mapa gira con el rumbo", "Arriba siempre es hacia donde vas", rumbo) { Moto.ponerRumboArriba(it) }
        FilaSwitch("Límite de velocidad y nombre de la calle",
            "Señal redonda con el límite (de OpenStreetMap o aproximado por tipo de calle)", limite) {
            Moto.ponerMostrarLimite(it)
        }
        Text(
            "Los mapas que ya viste se guardan (hasta 2 GB) para que carguen rápido y sirvan sin señal.",
            color = Paleta.textoSec, fontSize = 12.sp,
        )
        // CARTO ahora pide llave: sin ella, Noche y Claro usan OpenStreetMap con filtro de color
        Subtitulo("Llave de CARTO (opcional)")
        OutlinedTextField(
            value = llave,
            onValueChange = { llave = it.trim() },
            singleLine = true,
            placeholder = { Text("Pega tu llave") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Guardar", {
                Mapas.ponerLlaveCarto(ctx, llave)
                Toast.makeText(ctx, if (llave.isBlank()) "Sin llave: mapas de OpenStreetMap" else "Llave guardada", Toast.LENGTH_SHORT).show()
            }, Modifier.weight(1f), tamanoTexto = 12.sp)
            BotonDeportivo("Conseguir llave", {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://carto.com/basemaps/apikey/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) { }
            }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
        }
        Text(
            if (llaveGuardada.isBlank()) "Sin llave, Noche y Claro se dibujan con OpenStreetMap (ya no sale \"API key required\")."
            else "Con tu llave, Noche y Claro usan los mapas de CARTO. Es gratis para uso personal.",
            color = Paleta.textoSec, fontSize = 12.sp,
        )
    }
}

private fun muestraMapa(e: EstiloMapa): Color = when (e) {
    EstiloMapa.NOCHE -> Color(0xFF1B1D20)
    EstiloMapa.OSCURO -> Color(0xFF3A3F45)
    EstiloMapa.ESTANDAR -> Color(0xFFF2EFE9)
    EstiloMapa.CLARO -> Color(0xFFFAF8F3)
    EstiloMapa.TERRENO -> Color(0xFFB9D79B)
    EstiloMapa.SATELITE -> Color(0xFF3E5A3A)
}

// ------------------------------------------------------------
// Avisos
// ------------------------------------------------------------
@Composable
private fun SeccionAvisos() {
    val aviso by Moto.avisoVel.collectAsStateWithLifecycle()
    val limite by Moto.limiteVel.collectAsStateWithLifecycle()
    val modo by Moto.modoAvisoVel.collectAsStateWithLifecycle()
    val alertas by AlertasManager.activo.collectAsStateWithLifecycle()
    val sismos by AlertasManager.conSismos.collectAsStateWithLifecycle()
    var verAlertas by remember { mutableStateOf(false) }
    Seccion("Avisos") {
        FilaSwitch("Alertas de huracanes y clima severo",
            "Huracanes y tormentas tropicales cerca, tormenta eléctrica, granizo, rachas fuertes, lluvia intensa y calor. " +
                "Avisa en el tablero, en la moto, por voz y con notificación", alertas, Paleta.ambar) {
            AlertasManager.ponerActivo(it)
        }
        if (alertas) {
            FilaSwitch("Sismos reportados cerca", "Sismos de magnitud 4.5 o más a menos de 400 km (USGS). Es informativo: no es alerta sísmica temprana",
                sismos) { AlertasManager.ponerSismos(it) }
            BotonDeportivo("Ver alertas y ciclones activos", { verAlertas = true }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        }
        FilaSwitch("Aviso de velocidad", "Vibra y la velocidad parpadea en rojo si pasas el límite", aviso, Paleta.rojo) {
            Moto.ponerAvisoVel(it)
        }
        if (aviso) {
            Selector(
                opciones = listOf("Límite fijo", "Según la calle"),
                elegido = modo,
                onElegir = { Moto.ponerModoAvisoVel(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (modo == 1) {
                Text(
                    "Avisa si vas 5 km/h arriba del límite de la calle. Donde no se sepa, usa el límite fijo de abajo.",
                    color = Paleta.textoSec, fontSize = 12.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (modo == 1) "Límite fijo (respaldo)" else "Límite", color = Paleta.textoSec, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("$limite km/h", color = Paleta.lima, fontSize = 18.sp, fontFamily = Fuentes.logo)
            }
            Slider(
                value = limite.toFloat(),
                onValueChange = { Moto.ponerLimiteVel(it.roundToInt()) },
                valueRange = 30f..130f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = Paleta.lima,
                    activeTrackColor = Paleta.lima,
                    inactiveTrackColor = Paleta.superficie2,
                    activeTickColor = Color.Black.copy(alpha = 0.4f),
                    inactiveTickColor = Paleta.textoSec.copy(alpha = 0.4f),
                ),
            )
        }
    }
    if (verAlertas) DialogoAlertas { verAlertas = false }
}

// ------------------------------------------------------------
// Voz y asistente
// ------------------------------------------------------------
@Composable
private fun SeccionVoz() {
    val ctx = LocalContext.current
    val voz by Moto.avisosVoz.collectAsStateWithLifecycle()
    val micBt by Moto.micBt.collectAsStateWithLifecycle()
    val audio by AudioSalida.dispositivos.collectAsStateWithLifecycle()
    var verComandos by remember { mutableStateOf(false) }
    Seccion("Voz y asistente") {
        FilaSwitch("Avisos por voz", "El teléfono dice en voz alta: lluvia cerca y exceso de velocidad", voz) {
            Moto.ponerAvisosVoz(it)
        }
        Text("Micrófono para los comandos", color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Selector(
            opciones = listOf("Teléfono", "Casco / audífonos"),
            elegido = if (micBt) 1 else 0,
            onElegir = { Moto.ponerMicBt(it == 1) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (micBt) {
                if (audio.isEmpty()) "Conecta tu casco o audífonos por Bluetooth; mientras tanto usa el del teléfono."
                else "Usará el micrófono de ${audio.first().nombre}."
            } else "Usa el micrófono del teléfono (el que Android elija).",
            color = Paleta.textoSec, fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Asistente Google", {
                if (micBt) AudioSalida.encenderMicBt()
                if (!Acciones.abrirAsistente(ctx)) Toast.makeText(ctx, "No encontré el Asistente", Toast.LENGTH_SHORT).show()
            }, Modifier.weight(1f), tamanoTexto = 12.sp)
            BotonDeportivo("Probar voz", { Voz.decir("FT150 TS lista para rodar") }, Modifier.weight(1f),
                primario = false, tamanoTexto = 12.sp)
        }
        BotonDeportivo(if (verComandos) "Ocultar comandos" else "Ver comandos de voz", { verComandos = !verComandos },
            Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        if (verComandos) {
            Text(
                "Toca el micrófono del mapa y di:\n" +
                    "• \"Pon …\" (canción, artista o playlist)\n" +
                    "• \"Siguiente\", \"anterior\", \"pausa\", \"sube / baja el volumen\"\n" +
                    "• \"Llévame a …\", \"gasolinera\", \"taller\", \"llantera\"\n" +
                    "• \"Clima\", \"¿va a llover?\"\n" +
                    "• \"¿Dónde está mi moto?\", \"guarda la moto\"\n" +
                    "• \"Comparte mi ubicación\"\n" +
                    "• \"Límite\", \"velocidad\", \"batería\", \"gasolina\", \"hora\"\n" +
                    "Lo demás se busca en Google. Mantén presionado el micrófono para abrir el Asistente de Google.",
                color = Paleta.textoSec, fontSize = 12.sp,
            )
        }
    }
}

// ------------------------------------------------------------
// Launcher
// ------------------------------------------------------------
@Composable
private fun SeccionLauncher() {
    val ctx = LocalContext.current
    val visible = LocalPaginaVisible.current
    val manzana by Lanzador.appManzana.collectAsStateWithLifecycle()
    val apps by Lanzador.apps.collectAsStateWithLifecycle()
    var launcher by remember { mutableStateOf(Lanzador.launcherDisponible(ctx)) }
    var soyInicio by remember { mutableStateOf(Lanzador.esInicioPredeterminado(ctx)) }
    var elegir by remember { mutableStateOf(false) }
    // al regresar de los ajustes de Android, vuelve a revisar
    LaunchedEffect(visible) {
        if (visible) { launcher = Lanzador.launcherDisponible(ctx); soyInicio = Lanzador.esInicioPredeterminado(ctx) }
    }
    val nombreManzana = apps.firstOrNull { it.paquete == manzana }?.nombre ?: if (manzana.isBlank()) "Sin elegir" else manzana
    val estiloIconos by Lanzador.estiloIconos.collectAsStateWithLifecycle()
    val accesos by Lanzador.mostrarAccesos.collectAsStateWithLifecycle()

    Seccion("Launcher") {
        FilaSwitch(
            "Usar como pantalla de inicio",
            "El botón Inicio del teléfono te regresa al tablero y tus apps quedan a la izquierda",
            launcher,
        ) { on -> launcher = on; Lanzador.ponerLauncher(ctx, on) }
        if (launcher) {
            Dato("Pantalla de inicio", if (soyInicio) "Esta app" else "Otra (elígela)", if (soyInicio) Paleta.verde else Paleta.ambar)
            if (!soyInicio) {
                BotonDeportivo("Elegir pantalla de inicio", { Lanzador.abrirAjusteInicio(ctx) }, Modifier.fillMaxWidth(),
                    tamanoTexto = 13.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Subir los 4 accesos del tablero", color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(if (Apariencia.subirAccesosMm == 0) "Centrados" else "${fmt(Apariencia.subirAccesosMm / 10f, 1)} cm",
                color = Paleta.lima, fontSize = 15.sp, fontFamily = Fuentes.logo)
        }
        Slider(Apariencia.subirAccesosMm.toFloat(), { Apariencia.ponerSubirAccesos(it.roundToInt()) }, valueRange = 0f..15f, steps = 14,
            colors = SliderDefaults.colors(thumbColor = Paleta.lima, activeTrackColor = Paleta.lima, inactiveTrackColor = Paleta.superficie2))
        Text("Para que los ganchos del soporte no estorben al picarles (medido en centímetros reales de la pantalla).",
            color = Paleta.textoSec, fontSize = 11.sp)
        Text("Íconos de las apps", color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Selector(listOf("Normales", "Pixel color", "Pixel LCD"), estiloIconos, { Lanzador.ponerEstiloIconos(ctx, it) },
            Modifier.fillMaxWidth())
        Text(
            when (estiloIconos) {
                0 -> "Los íconos de siempre."
                1 -> "En cuadritos de 16x16, a color, al estilo del tablero."
                else -> "En cuadritos y de un solo color (el acento), como un LCD de moto."
            },
            color = Paleta.textoSec, fontSize = 12.sp,
        )
        FilaSwitch("Accesos directos en el tablero",
            "4 apps a la mano, a la izquierda del tablero (abajo si el teléfono está vertical)", accesos) {
            Lanzador.ponerMostrarAccesos(ctx, it)
        }
        Dato("Botón manzanita (mapa)", nombreManzana)
        BotonDeportivo("Elegir app de la manzanita", { elegir = true }, Modifier.fillMaxWidth(),
            primario = false, tamanoTexto = 13.sp)
        Text(
            "Desliza a la izquierda desde el tablero para ver todas tus apps. Mantén presionada una app para " +
                "fijarla en favoritas o ponerla en la manzanita. En los accesos del tablero: toca un + para asignar " +
                "una app y mantén presionado para cambiarla o quitarla.",
            color = Paleta.textoSec, fontSize = 12.sp,
        )
    }
    if (elegir) {
        DialogoElegirApp(
            "¿Qué app abre la manzanita?",
            onElegir = { a -> Lanzador.ponerManzana(ctx, a.paquete); elegir = false },
            onCerrar = { elegir = false },
        )
    }
}

// ------------------------------------------------------------
// Modo moto (carga = está en la moto) y segundo plano
// ------------------------------------------------------------
@Composable
private fun SeccionModoMoto() {
    val ctx = LocalContext.current
    val visible = LocalPaginaVisible.current
    val detectar by Energia.detectarCarga.collectAsStateWithLifecycle()
    val ble by Energia.requiereBle.collectAsStateWithLifecycle()
    val alQuitar by Energia.alQuitarApps.collectAsStateWithLifecycle()
    val alMontar by Energia.abrirAlMontar.collectAsStateWithLifecycle()
    val fondo by Energia.segundoPlano.collectAsStateWithLifecycle()
    val cargando by Energia.cargando.collectAsStateWithLifecycle()
    val pc by Energia.enComputadora.collectAsStateWithLifecycle()
    val enMoto by Energia.enMoto.collectAsStateWithLifecycle()
    val dormida by ModoSueno.dormido.collectAsStateWithLifecycle()
    val rootActivo by Root.activo.collectAsStateWithLifecycle()
    val root by Root.disponible.collectAsStateWithLifecycle()
    var abreSola by remember { mutableStateOf(Energia.puedeAbrirseSola(ctx)) }
    var sinAhorro by remember { mutableStateOf(Energia.sinAhorroBateria(ctx)) }
    // al regresar de los ajustes de Android, vuelve a revisar
    LaunchedEffect(visible) {
        if (visible) { abreSola = Energia.puedeAbrirseSola(ctx); sinAhorro = Energia.sinAhorroBateria(ctx) }
    }

    val nfcHay by NfcMoto.disponible.collectAsStateWithLifecycle()
    val nfcPrendido by NfcMoto.encendido.collectAsStateWithLifecycle()
    val nfcActivo by NfcMoto.activo.collectAsStateWithLifecycle()
    val nfcEtiqueta by NfcMoto.etiqueta.collectAsStateWithLifecycle()
    val nfcPresente by NfcMoto.presente.collectAsStateWithLifecycle()
    val nfcRegistrando by NfcMoto.registrando.collectAsStateWithLifecycle()
    val nfcMensaje by NfcMoto.mensaje.collectAsStateWithLifecycle()
    LaunchedEffect(visible) { if (visible) NfcMoto.refrescar() }
    Seccion("Modo moto") {
        Dato(
            "Ahora",
            when {
                dormida -> "Modo sueño (cargando quieto)"
                enMoto -> "En la moto"
                cargando && pc -> "Conectado a la computadora (no cuenta como la moto)"
                cargando -> "Cargando (no en la moto)"
                else -> "En la mano"
            },
            if (enMoto) Paleta.verde else Paleta.texto,
        )
        FilaSwitch("Detectar la moto por la carga",
            "El cable de la moto carga el teléfono solo con el switch prendido: carga = tablero, sin carga = tus apps",
            detectar) { Energia.ponerDetectarCarga(it) }
        if (detectar) {
            FilaSwitch("Solo con la moto conectada",
                "Además de la carga, que la tarjeta de la moto esté conectada por Bluetooth (así no se activa con el cargador de tu casa)",
                ble) { Energia.ponerRequiereBle(it) }
            FilaSwitch("Al montarlo, abrir el tablero",
                "Si estabas en otra app, la app regresa sola al tablero", alMontar) { Energia.ponerAbrirAlMontar(it) }
            if (alMontar && !(rootActivo && root == true)) {
                Text("En Xiaomi/POCO también activa: Ajustes > Apps > FT150TS > Otros permisos > " +
                    "\"Mostrar ventanas emergentes mientras se ejecuta en segundo plano\". Con root no hace falta.",
                    color = Paleta.textoSec, fontSize = 12.sp)
            }
            if (alMontar && !abreSola && !(rootActivo && root == true)) {
                Text("Para que se abra sola desde otra app, Android pide el permiso \"Mostrar sobre otras apps\" (o root).",
                    color = Paleta.ambar, fontSize = 12.sp)
                BotonDeportivo("Dar permiso \"Mostrar sobre otras apps\"", { Energia.pedirAbrirseSola(ctx) },
                    Modifier.fillMaxWidth(), tamanoTexto = 12.sp)
            }
            val musicaSw by com.xof.ft150ts.Encendido.musica.collectAsStateWithLifecycle()
            val saludoSw by com.xof.ft150ts.Encendido.saludo.collectAsStateWithLifecycle()
            FilaSwitch("Música con el switch",
                "Al apagar la moto se atenúa y luego se pausa; si la prendes en menos de 30 min, sigue donde iba",
                musicaSw) { com.xof.ft150ts.Encendido.ponerMusica(it) }
            if (musicaSw) {
                val aten by com.xof.ft150ts.Encendido.atenuarSeg.collectAsStateWithLifecycle()
                val opcionesAt = listOf(0, 15, 30, 60)
                Selector(listOf("Pausar ya", "15 s bajita", "30 s bajita", "1 min bajita"), opcionesAt.indexOf(aten).coerceAtLeast(0),
                    { com.xof.ft150ts.Encendido.ponerAtenuar(opcionesAt[it]) }, Modifier.fillMaxWidth())
                Text("Al cerrar el switch la música baja suave y sigue bajita ese tiempo; luego se pausa. Si abres antes, regresa.",
                    color = Paleta.textoSec, fontSize = 12.sp)
            }
            FilaSwitch("Saludo por voz al prender",
                "Si la moto estuvo apagada un rato: la temperatura, si va a llover y si te queda poca gasolina",
                saludoSw) { com.xof.ft150ts.Encendido.ponerSaludo(it) }
            // Zona Wi-Fi con el switch (root): CarPlay inalámbrico necesita la zona del teléfono prendida
            val zonaSw by com.xof.ft150ts.ZonaWifi.activo.collectAsStateWithLifecycle()
            val zonaMia by com.xof.ft150ts.ZonaWifi.soloLaMia.collectAsStateWithLifecycle()
            val zonaEspera by com.xof.ft150ts.ZonaWifi.espera.collectAsStateWithLifecycle()
            val zonaEstado by com.xof.ft150ts.ZonaWifi.prendida.collectAsStateWithLifecycle()
            val zonaEvento by com.xof.ft150ts.ZonaWifi.evento.collectAsStateWithLifecycle()
            val rootOk = com.xof.ft150ts.Root.disponible.collectAsStateWithLifecycle().value == true
            FilaSwitch("Zona Wi-Fi con el switch",
                "Al abrir el switch prende tu zona Wi-Fi (para CarPlay); al cerrarlo la apaga después de la espera. Requiere root",
                zonaSw) { com.xof.ft150ts.ZonaWifi.ponerActivo(it) }
            if (zonaSw) {
                if (!rootOk) Text("Sin root no se puede prender la zona sola: activa root en Ajustes > Root.", color = Paleta.ambar, fontSize = 12.sp)
                val esperas = listOf(30, 60, 120, 300)
                Selector(listOf("30 s", "1 min", "2 min", "5 min"), esperas.indexOf(zonaEspera).coerceAtLeast(0),
                    { com.xof.ft150ts.ZonaWifi.ponerEspera(esperas[it]) }, Modifier.fillMaxWidth())
                Text("Espera al cerrar el switch: si lo vuelves a abrir antes, la zona sigue prendida.", color = Paleta.textoSec, fontSize = 12.sp)
                FilaSwitch("Solo apagar la que prendió la app",
                    "Si tú la prendiste a mano (para compartir internet), el switch no la apaga", zonaMia) {
                    com.xof.ft150ts.ZonaWifi.ponerSoloLaMia(it)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonDeportivo("Prender", { com.xof.ft150ts.ZonaWifi.probar(true) }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                    BotonDeportivo("Apagar", { com.xof.ft150ts.ZonaWifi.probar(false) }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                }
                Text("Zona: " + when (zonaEstado) { true -> "prendida"; false -> "apagada"; null -> "sin revisar" } +
                    if (zonaEvento.isNotBlank()) " · $zonaEvento" else "", color = Paleta.textoSec, fontSize = 12.sp)
            }
            Text("Si se corta la carga mientras vas rodando (cable flojo), el tablero no se va a tus apps: te avisa y " +
                "espera a que te detengas.", color = Paleta.textoSec, fontSize = 12.sp)
            FilaSwitch("Al quitarlo, mostrar tus apps",
                "Al apagar la moto o quitar el teléfono del soporte pasa a la página de apps", alQuitar) {
                Energia.ponerAlQuitarApps(it)
            }
        }
        // Etiqueta NFC en el soporte
        Subtitulo("Etiqueta NFC en el soporte")
        when {
            !nfcHay -> Text("Este teléfono no tiene NFC.", color = Paleta.textoSec, fontSize = 12.sp)
            !nfcPrendido -> {
                Text("El NFC está apagado en el teléfono.", color = Paleta.ambar, fontSize = 12.sp)
                BotonDeportivo("Prender NFC", {
                    try { ctx.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
            }
            else -> {
                FilaSwitch("Usar etiqueta NFC",
                    "Pega un sticker NFC en el soporte: al poner el teléfono entra al modo moto (aunque no cargue) y al quitarlo pasa a tus apps",
                    nfcActivo) { NfcMoto.ponerActivo(it) }
                if (nfcActivo) {
                    Dato("Etiqueta", when {
                        nfcEtiqueta.isBlank() -> "Sin registrar"
                        nfcPresente -> "En el soporte"
                        else -> "Registrada"
                    }, if (nfcPresente) Paleta.verde else Paleta.texto)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonDeportivo(if (nfcRegistrando) "Acércala…" else "Registrar etiqueta", {
                            if (nfcRegistrando) NfcMoto.cancelarRegistro() else NfcMoto.empezarRegistro()
                            (ctx as? android.app.Activity)?.let { NfcMoto.deshabilitar(it); NfcMoto.habilitar(it) }
                        }, Modifier.weight(1f), tamanoTexto = 12.sp)
                        if (nfcEtiqueta.isNotBlank()) {
                            BotonDeportivo("Olvidar", { NfcMoto.olvidar() }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
                        }
                    }
                    if (nfcMensaje.isNotBlank()) Text(nfcMensaje, color = Paleta.lima, fontSize = 12.sp)
                    Text("Sirve cualquier sticker NFC (NTAG213/215). En el Poco F4 la antena está atrás, cerca de la cámara. " +
                        "Con la app en pantalla y esto prendido, pagar con el teléfono no funciona (Android le da el NFC a la app).",
                        color = Paleta.textoSec, fontSize = 11.sp)
                }
            }
        }
        BloqueReposo()
        BloqueSueno(fondo)
        FilaSwitch("Mantener activa en segundo plano",
            "Notificación fija \"App activa\": Android no la cierra y sigue conectada a la moto", fondo) {
            Energia.ponerSegundoPlano(ctx, it)
        }
        if (fondo) {
            Dato("Ahorro de batería", if (sinAhorro) "Sin restricción" else "Puede dormirla",
                if (sinAhorro) Paleta.verde else Paleta.ambar)
            if (!sinAhorro) {
                BotonDeportivo("Quitar ahorro de batería", { Energia.pedirSinAhorroBateria(ctx) }, Modifier.fillMaxWidth(),
                    primario = false, tamanoTexto = 12.sp)
                Text("En Xiaomi/POCO también pon: Ajustes > Apps > FT150TS > Ahorro de batería > Sin restricciones, " +
                    "y activa Inicio automático.", color = Paleta.textoSec, fontSize = 12.sp)
            }
        }
    }
}

/** Reposo: parada y cargando unos minutos → sensores, GPS y 120 Hz descansan; reloj atenuado o pantalla apagada. */
@Composable
private fun BloqueReposo() {
    val activo by Reposo.activo.collectAsStateWithLifecycle()
    val enReposo by Reposo.enReposo.collectAsStateWithLifecycle()
    val quieto by Reposo.quietoDesde.collectAsStateWithLifecycle()
    val minutos by Reposo.minutos.collectAsStateWithLifecycle()
    val modoPantalla by Reposo.pantalla.collectAsStateWithLifecycle()
    val evento by Reposo.ultimoEvento.collectAsStateWithLifecycle()
    val cargando by Energia.cargando.collectAsStateWithLifecycle()
    val visible = LocalPaginaVisible.current
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(visible, quieto) {
        while (visible && quieto > 0L) { ahora = System.currentTimeMillis(); delay(15_000) }
    }
    val opciones = listOf(2, 3, 5, 10)

    Subtitulo("Reposo (ahorro de energía)")
    FilaSwitch("Reposo al estacionar",
        "Con la moto parada y el teléfono cargando, a los pocos minutos descansan los sensores de vibración, el GPS y " +
            "los 120 Hz. Al dar marcha (la carga se corta y regresa), al mover el teléfono o al tocar la pantalla, " +
            "todo vuelve al instante. Es el paso antes del modo sueño.",
        activo) { Reposo.ponerActivo(it) }
    if (activo) {
        Dato(
            "Ahora",
            when {
                enReposo -> "En reposo"
                !cargando -> "Activa (no está cargando)"
                quieto > 0L -> {
                    val falta = ((minutos * 60_000L - (ahora - quieto)) / 60_000L).coerceAtLeast(0L)
                    "Parada · reposo en ${if (falta == 0L) "menos de 1" else falta} min"
                }
                else -> "Activa (en uso o moviéndose)"
            },
            if (enReposo) Paleta.lima else Paleta.texto,
        )
        Text("Entrar en reposo después de", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Selector(opciones.map { "$it min" }, opciones.indexOf(minutos).coerceAtLeast(0),
            { Reposo.ponerMinutos(opciones[it]) }, Modifier.fillMaxWidth())
        Text("Pantalla en reposo", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Selector(listOf("Reloj atenuado", "Apagar pantalla"), modoPantalla, { Reposo.ponerPantalla(it) }, Modifier.fillMaxWidth())
        Text(
            "Reloj atenuado: negro AMOLED con la hora y la batería, al brillo mínimo y moviéndose un poco para no marcar " +
                "la pantalla. Apagar pantalla: gasta todavía menos; el tablero regresa al prender el teléfono.",
            color = Paleta.textoSec, fontSize = 11.sp,
        )
        if (evento.isNotBlank()) Text(evento, color = Paleta.lima, fontSize = 12.sp)
    }
}

/** Modo sueño: cargando y sin moverse → la app se duerme y revisa cada rato si el teléfono se movió. */
@Composable
private fun BloqueSueno(fondo: Boolean) {
    val activo by ModoSueno.activo.collectAsStateWithLifecycle()
    val dormido by ModoSueno.dormido.collectAsStateWithLifecycle()
    val revisando by ModoSueno.revisando.collectAsStateWithLifecycle()
    val quieto by ModoSueno.quietoDesde.collectAsStateWithLifecycle()
    val desde by ModoSueno.dormidoDesde.collectAsStateWithLifecycle()
    val prox by ModoSueno.proximaRevision.collectAsStateWithLifecycle()
    val evento by ModoSueno.ultimoEvento.collectAsStateWithLifecycle()
    val minDormir by ModoSueno.minutosDormir.collectAsStateWithLifecycle()
    val minRevisar by ModoSueno.minutosRevisar.collectAsStateWithLifecycle()
    val cargando by Energia.cargando.collectAsStateWithLifecycle()
    val visible = LocalPaginaVisible.current
    // Reloj para "se duerme en X min" (cada 30 s, solo con Ajustes en pantalla)
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(visible, quieto) {
        while (visible && quieto > 0L) { ahora = System.currentTimeMillis(); delay(30_000) }
    }
    val opcDormir = listOf(30, 60, 120)
    val opcRevisar = listOf(15, 30, 60)

    Subtitulo("Modo sueño")
    FilaSwitch("Modo sueño",
        "Si está cargando pero no se mueve (lo pusiste a cargar en la mesa o la moto se quedó con el switch prendido), " +
            "después de un rato la app se duerme: apaga GPS, sensores y dashcam, busca la tarjeta muy de vez en cuando " +
            "y deja que se apague la pantalla. Cada cierto tiempo prende la ubicación para ver si el teléfono se movió.",
        activo) { ModoSueno.ponerActivo(it) }
    if (activo) {
        Dato(
            "Ahora",
            when {
                dormido && revisando -> "Dormida · revisando ubicación…"
                dormido -> "Dormida desde ${ModoSueno.hora(desde)}" + if (prox > 0L) " · revisa a las ${ModoSueno.hora(prox)}" else ""
                !cargando -> "Despierta (no está cargando)"
                quieto > 0L -> {
                    val falta = ((minDormir * 60_000L - (ahora - quieto)) / 60_000L).coerceAtLeast(0L)
                    "Quieta · se duerme en $falta min"
                }
                else -> "Despierta (en uso o moviéndose)"
            },
            if (dormido) Paleta.ambar else Paleta.texto,
        )
        Text("Dormir después de (cargando y quieto)", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Selector(listOf("30 min", "1 h", "2 h"), opcDormir.indexOf(minDormir).coerceAtLeast(0),
            { ModoSueno.ponerMinutosDormir(opcDormir[it]) }, Modifier.fillMaxWidth())
        Text("Revisar si se movió cada", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Selector(listOf("15 min", "30 min", "1 h"), opcRevisar.indexOf(minRevisar).coerceAtLeast(0),
            { ModoSueno.ponerMinutosRevisar(opcRevisar[it]) }, Modifier.fillMaxWidth())
        if (!dormido) {
            BotonDeportivo("Dormir ahora", { ModoSueno.dormirAhora() }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
        }
        if (evento.isNotBlank()) Text(evento, color = Paleta.lima, fontSize = 12.sp)
        Text(
            "Despierta sola si en una revisión se alejó más de 150 m, si el sensor de movimiento siente que lo llevas, " +
                "si tocas la pantalla o si desconectas el cargador. \"Dormir ahora\" sirve para probar: el siguiente toque la despierta." +
                if (!fondo) " Ojo: con \"Mantener activa en segundo plano\" apagado, Android puede cerrar la app y no revisará." else "",
            color = Paleta.textoSec, fontSize = 11.sp,
        )
    }
}

// ------------------------------------------------------------
// Control PID de la aguja de RPM
// ------------------------------------------------------------
@Composable
private fun SeccionPid() {
    val activo = Apariencia.pidAguja
    val kp = Apariencia.pidKp
    val ki = Apariencia.pidKi
    val kd = Apariencia.pidKd
    // Respuesta a un escalón de 1500 a 7000 rpm con estas ganancias (se recalcula al mover los controles)
    val respuesta = remember(kp, ki, kd) { AgujaPid.escalon(kp, ki, kd, 1500f, 7000f) }
    val (sobrepaso, asienta) = remember(respuesta) { AgujaPid.metricas(respuesta, 1500f, 7000f) }
    val wn = sqrt(kp)
    val zeta = (AgujaPid.FRICCION + kd) / (2f * wn)
    val colores = SliderDefaults.colors(
        thumbColor = Paleta.lima, activeTrackColor = Paleta.lima, inactiveTrackColor = Paleta.superficie2,
    )
    Seccion("Control PID (aguja de RPM)") {
        FilaSwitch("Aguja con PID", "La barra de RPM y la aguja del tacómetro se mueven con inercia, empujadas por un " +
            "control PID, como un tacómetro de verdad", activo) { Apariencia.ponerPidAguja(it) }
        if (activo) {
            GraficaEscalon(respuesta, 1500f, 7000f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tarjeta("Sobrepaso", fmt(sobrepaso, 1), Modifier.weight(1f), unidad = "%",
                    color = if (sobrepaso > 10f) Paleta.ambar else Paleta.texto, compacta = true)
                Tarjeta("Se asienta", fmt(asienta, 2), Modifier.weight(1f), unidad = "s", compacta = true)
                Tarjeta("Amortig. ζ", fmt(zeta, 2), Modifier.weight(1f),
                    extra = when { zeta < 0.7f -> "Rebota"; zeta > 1.3f -> "Lenta"; else -> "Bien" }, compacta = true)
            }
            FilaGanancia("Kp (proporcional)", kp, 50f..800f, colores) { Apariencia.ponerPid(it, ki, kd) }
            FilaGanancia("Ki (integral)", ki, 0f..200f, colores) { Apariencia.ponerPid(kp, it, kd) }
            FilaGanancia("Kd (derivativo)", kd, 0f..80f, colores) { Apariencia.ponerPid(kp, ki, it) }
            BotonDeportivo("Valores de fábrica", { Apariencia.pidDeFabrica() }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
            Text("La aguja tiene masa y fricción: a = u − ${AgujaPid.FRICCION.toInt()}·v. Con PD queda un sistema de 2.º orden con " +
                "ωn = √Kp = ${fmt(wn, 1)} rad/s y ζ = (fricción + Kd) / (2·ωn). Más Kp = más rápida; más Kd = menos rebote; Ki quita el " +
                "error que quede. Las RPM que mide la app no cambian: solo cómo se mueve la aguja.",
                color = Paleta.textoSec, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FilaGanancia(nombre: String, valor: Float, rango: ClosedFloatingPointRange<Float>, colores: androidx.compose.material3.SliderColors, onCambio: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, color = Paleta.textoSec, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(fmt(valor, 0), color = Paleta.lima, fontSize = 15.sp, fontFamily = Fuentes.logo)
    }
    Slider(valor, { onCambio(it.roundToInt().toFloat()) }, valueRange = rango, colors = colores)
}

/** Gráfica: objetivo (línea gris) y cómo llega la aguja (lima). */
@Composable
private fun GraficaEscalon(r: List<Float>, desde: Float, hasta: Float) {
    val lima = Paleta.lima
    val gris = Paleta.textoSec.copy(alpha = 0.6f)
    val fondo = Paleta.superficie2
    Canvas(Modifier.fillMaxWidth().height(110.dp).clip(Formas.chica).background(fondo)) {
        if (r.isEmpty()) return@Canvas
        val maxV = maxOf(hasta * 1.35f, r.max())
        fun y(v: Float) = size.height - (v / maxV) * size.height * 0.92f - size.height * 0.04f
        val xPaso = size.width / r.size
        // objetivo: sube de golpe al inicio
        drawLine(gris, Offset(0f, y(desde)), Offset(xPaso, y(desde)), strokeWidth = 2f)
        drawLine(gris, Offset(xPaso, y(desde)), Offset(xPaso, y(hasta)), strokeWidth = 2f)
        drawLine(gris, Offset(xPaso, y(hasta)), Offset(size.width, y(hasta)), strokeWidth = 2f)
        // aguja: cuadritos (estilo pixel)
        val lado = 3.dp.toPx()
        r.forEachIndexed { i, v ->
            if (i % 2 == 0) drawRect(lima, Offset(i * xPaso, y(v) - lado / 2), Size(lado, lado))
        }
    }
}

// ------------------------------------------------------------
// Teléfono caliente
// ------------------------------------------------------------
@Composable
private fun SeccionCalor() {
    val temp by Termico.tempBateria.collectAsStateWithLifecycle()
    val nivel by Termico.nivel.collectAsStateWithLifecycle()
    val proteger by Termico.proteger.collectAsStateWithLifecycle()
    val protegiendo by Termico.protegiendo.collectAsStateWithLifecycle()
    val color = when (nivel) {
        NivelCalor.NORMAL -> Paleta.verde
        NivelCalor.TIBIO -> Paleta.texto
        NivelCalor.CALIENTE -> Paleta.ambar
        NivelCalor.MUY_CALIENTE -> Paleta.rojo
    }
    Seccion("Teléfono caliente") {
        Dato("Batería del teléfono", if (temp > 0) "${fmt(temp, 1)} °C · ${nivel.nombre}" else "--", color)
        FilaSwitch("Proteger del calor",
            "Al calentarse (43 °C o más) baja a 60 Hz, deja de forzar el brillo máximo y pausa la dashcam; " +
                "te avisa en el tablero y por voz", proteger) { Termico.ponerProteger(it) }
        if (protegiendo) Text("Protegiendo ahora: pantalla a 60 Hz${if (nivel == NivelCalor.MUY_CALIENTE) " y sin brillo máximo" else ""}.",
            color = Paleta.ambar, fontSize = 12.sp)
        Text("Ayuda: un soporte con aire por detrás, sin funda gruesa, y cargar con el cable de la moto (no carga rápida) " +
            "calientan menos. Arriba de 45 °C Android baja la velocidad y puede dejar de cargar solo.",
            color = Paleta.textoSec, fontSize = 11.sp)
    }
}

// ------------------------------------------------------------
// Dashcam
// ------------------------------------------------------------
@Composable
private fun SeccionDashcam() {
    val activo by Dashcam.activo.collectAsStateWithLifecycle()
    val grabando by Dashcam.grabando.collectAsStateWithLifecycle()
    val soloEnMoto by Dashcam.soloEnMoto.collectAsStateWithLifecycle()
    val minutos by Dashcam.minutos.collectAsStateWithLifecycle()
    val alta by Dashcam.calidadAlta.collectAsStateWithLifecycle()
    val uso by Dashcam.usoMb.collectAsStateWithLifecycle()
    val mensaje by Dashcam.mensaje.collectAsStateWithLifecycle()
    Seccion("Dashcam") {
        FilaSwitch("Grabar el camino", "Con la cámara de atrás, en bucle: se guardan los últimos minutos y lo viejo se borra solo. " +
            "Solo graba con la app en pantalla", activo, Paleta.rojo) { Dashcam.ponerActivo(it) }
        if (activo) {
            Dato("Ahora", if (grabando) "Grabando" else "En pausa", if (grabando) Paleta.rojo else Paleta.texto)
            FilaSwitch("Solo en la moto", "Graba solo con el modo moto (montado y con el switch prendido)", soloEnMoto) {
                Dashcam.ponerSoloEnMoto(it)
            }
            Selector(listOf("720p (calienta menos)", "1080p"), if (alta) 1 else 0, { Dashcam.ponerCalidad(it == 1) }, Modifier.fillMaxWidth())
            Selector(listOf("5 min", "10 min", "20 min"), when (minutos) { 5 -> 0; 20 -> 2; else -> 1 },
                { Dashcam.ponerMinutos(listOf(5, 10, 20)[it]) }, Modifier.fillMaxWidth())
            Dato("Espacio usado", "$uso MB")
            BotonDeportivo("Guardar clip (últimos 3 min)", { Dashcam.guardarClip() }, Modifier.fillMaxWidth(), tamanoTexto = 12.sp)
            if (mensaje.isNotBlank()) Text(mensaje, color = Paleta.lima, fontSize = 12.sp)
            Text("Un golpe muy fuerte (posible caída) guarda el clip solo. Los clips quedan en la galería, en Películas/FT150TS. " +
                "En el tablero, toca el testigo REC para guardar. Grabar calienta el teléfono: si se calienta, se pausa sola.",
                color = Paleta.textoSec, fontSize = 11.sp)
        }
    }
}

// ------------------------------------------------------------
// Sin la tarjeta: el teléfono calcula RPM (vibración), velocidad (GPS) e inclinación
// ------------------------------------------------------------
@Composable
private fun SeccionSinTarjeta() {
    val activo by TelefonoMoto.activo.collectAsStateWithLifecycle()
    val trabajando by TelefonoMoto.trabajando.collectAsStateWithLifecycle()
    val info by TelefonoMoto.info.enVivo()
    val ralenti by TelefonoMoto.ralenti.collectAsStateWithLifecycle()
    val cambio by TelefonoMoto.rpmCambio.collectAsStateWithLifecycle()
    val calibrado by TelefonoMoto.calibrado.collectAsStateWithLifecycle()
    val calibrando by TelefonoMoto.calibrando.collectAsStateWithLifecycle()
    val orden by TelefonoMoto.orden.collectAsStateWithLifecycle()
    val mensaje by TelefonoMoto.mensaje.collectAsStateWithLifecycle()
    val soloConCable by TelefonoMoto.soloConCable.collectAsStateWithLifecycle()
    val suavizado by TelefonoMoto.suavizado.collectAsStateWithLifecycle()
    val cargando by Energia.cargando.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()
    val tarjeta = conexion == Conexion.CONECTADO || conexion == Conexion.DEMO
    val colores = SliderDefaults.colors(
        thumbColor = Paleta.lima, activeTrackColor = Paleta.lima, inactiveTrackColor = Paleta.superficie2,
        activeTickColor = Color.Black.copy(alpha = 0.4f), inactiveTickColor = Paleta.textoSec.copy(alpha = 0.4f),
    )

    Seccion("Sin la tarjeta (sensores del teléfono)") {
        FilaSwitch("RPM por vibración",
            "Mientras la tarjeta no esté conectada, el teléfono calcula las RPM con la vibración del motor, " +
                "la velocidad con el GPS, la marcha y la inclinación. Todo entra a tus mismas estadísticas", activo) {
            TelefonoMoto.ponerActivo(it)
        }
        if (activo) {
            Dato(
                "Ahora",
                when {
                    tarjeta -> "Usando la tarjeta"
                    !trabajando -> "En pausa (abre el tablero o monta el teléfono)"
                    soloConCable && !cargando -> "Moto apagada (sin cable)"
                    info.motor -> "Motor prendido · ${info.rpm} rpm"
                    else -> "Switch prendido · motor apagado"
                },
                if (trabajando && info.motor) Paleta.verde else Paleta.texto,
            )
            if (trabajando) {
                Dato("Vibración", "${fmt(info.rms, 2)} m/s² · ${fmt(info.frecuencia, 1)} Hz · claridad ${fmt(info.confianza, 1)}")
                Dato("Muestras por segundo", "${info.fs.toInt()}", if (info.fs >= 300f) Paleta.verde else Paleta.ambar)
                if (info.fs in 1f..299f) {
                    Text("Tu acelerómetro da ${info.fs.toInt()} lecturas por segundo: arriba de ~${info.rpmMaxConfiable} rpm " +
                        "la lectura puede fallar.", color = Paleta.ambar, fontSize = 12.sp)
                }
            }
            FilaSwitch("Motor solo con el cable conectado",
                "El cable de la moto solo da corriente con el switch prendido: sin cable = moto apagada, aunque el " +
                    "teléfono sienta vibración (en la mano, caminando, en la mesa)", soloConCable) { TelefonoMoto.ponerSoloConCable(it) }
            Text("Suavizado de las RPM", color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Selector(listOf("Rápido", "Normal", "Suave"), suavizado, { TelefonoMoto.ponerSuavizado(it) }, Modifier.fillMaxWidth())
            Text(
                when (suavizado) {
                    0 -> "Responde más rápido a los acelerones; puede moverse un poco más."
                    2 -> "Muy estable, tarda un poco más en subir y bajar."
                    else -> "Filtro de Kalman: estable sin quedarse atrás e ignora brincos sueltos."
                },
                color = Paleta.textoSec, fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ralentí de tu moto", color = Paleta.textoSec, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("$ralenti rpm", color = Paleta.lima, fontSize = 16.sp, fontFamily = Fuentes.logo)
            }
            Slider(ralenti.toFloat(), { TelefonoMoto.ponerRalenti((it / 50f).roundToInt() * 50) }, valueRange = 1100f..2000f,
                steps = 17, colors = colores)
            BotonDeportivo(if (calibrando) "Midiendo…" else "Calibrar en ralentí", { TelefonoMoto.calibrar() },
                Modifier.fillMaxWidth(), tamanoTexto = 13.sp)
            Text(
                "Para calibrar: moto en neutral, motor en ralentí (caliente) y el teléfono en el soporte. Tarda 6 s. " +
                    "Hazlo una vez; si alguna vez marca el doble o la mitad, vuelve a calibrar.",
                color = Paleta.textoSec, fontSize = 12.sp,
            )
            Dato("Calibración", if (calibrado) "Lista · línea ${if (orden == orden.toInt().toFloat()) orden.toInt() else orden}x" else "Sin calibrar",
                if (calibrado) Paleta.verde else Paleta.ambar)
            if (mensaje.isNotBlank()) Text(mensaje, color = Paleta.lima, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shift light (cambio a)", color = Paleta.textoSec, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("$cambio rpm", color = Paleta.lima, fontSize = 16.sp, fontFamily = Fuentes.logo)
            }
            Slider(cambio.toFloat(), { TelefonoMoto.ponerRpmCambio((it / 250f).roundToInt() * 250) }, valueRange = 4000f..9500f,
                steps = 21, colors = colores)
            TablaMarchas(ralenti, trabajando && info.motor)
            BotonDeportivo("Borrar calibración y marchas aprendidas", { TelefonoMoto.reiniciarCalibracion() }, Modifier.fillMaxWidth(),
                primario = false, tamanoTexto = 12.sp)
            Text(
                "Es aproximado: baches, soltar el clutch o el viento pueden mover la lectura un momento. La marcha se aprende " +
                    "sola mientras ruedas parejo. Cuando conectes la tarjeta, ella manda y esto se pausa.",
                color = Paleta.textoSec, fontSize = 11.sp,
            )
        }
    }
}

/** Marchas aprendidas: relación de cada una y entre qué velocidades puede ir. */
@Composable
private fun TablaMarchas(ralenti: Int, mostrarAhora: Boolean) {
    val relaciones by TelefonoMoto.relaciones.collectAsStateWithLifecycle()
    val seguridad by TelefonoMoto.seguridadMarcha.collectAsStateWithLifecycle()
    val rap by Moto.rapida.enVivo()
    val minimo = EstimadorMarcha.rpmMinima(ralenti)
    Text("Marchas (velocidad + RPM)", color = Paleta.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    if (mostrarAhora) {
        Dato(
            "Marcha ahora",
            when {
                rap.marcha > 0 -> "${rap.marcha}ª · ${(seguridad * 100).roundToInt()} % segura"
                rap.marcha == 0 -> "Parado"
                else -> "—"
            },
            if (rap.marcha > 0 && seguridad >= 0.8f) Paleta.verde else Paleta.texto,
        )
    }
    relaciones.forEachIndexed { i, r ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}ª", color = Paleta.lima, fontSize = 15.sp, fontFamily = Fuentes.logo, modifier = Modifier.width(34.dp))
            Text("${r.roundToInt()} rpm por km/h", color = Paleta.textoSec, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${(minimo / r).roundToInt()}–${(EstimadorMarcha.RPM_TOPE / r).roundToInt()} km/h", color = Paleta.texto, fontSize = 12.sp)
        }
    }
    Text(
        "La marcha junta la velocidad, las RPM y la marcha que traías: solo cuenta una marcha si a esa velocidad el motor " +
            "andaría entre ${minimo.roundToInt()} y ${EstimadorMarcha.RPM_TOPE.roundToInt()} rpm (a 17 km/h no puede ser 4ª), " +
            "con el clutch apretado sostiene la que traías y solo cambia cuando la nueva es clara medio segundo.",
        color = Paleta.textoSec, fontSize = 11.sp,
    )
}

// ------------------------------------------------------------
// Root (Magisk)
// ------------------------------------------------------------
@Composable
private fun SeccionRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val visible = LocalPaginaVisible.current
    val activo by Root.activo.collectAsStateWithLifecycle()
    val disponible by Root.disponible.collectAsStateWithLifecycle()
    val ventana by Root.ventanaManzana.collectAsStateWithLifecycle()
    val despertar by Root.despertar.collectAsStateWithLifecycle()
    val sobreBloqueo by Root.sobreBloqueo.collectAsStateWithLifecycle()
    val gestos by Root.gestos.collectAsStateWithLifecycle()
    var trabajando by remember { mutableStateOf(false) }
    var soyInicio by remember { mutableStateOf(Lanzador.esInicioPredeterminado(ctx)) }
    LaunchedEffect(visible) { if (visible) soyInicio = Lanzador.esInicioPredeterminado(ctx) }

    fun conRoot(texto: String, accion: suspend () -> Boolean) {
        if (trabajando) return
        trabajando = true
        scope.launch {
            val ok = accion()
            trabajando = false
            soyInicio = Lanzador.esInicioPredeterminado(ctx)
            Toast.makeText(ctx, if (ok) texto else "No se pudo (¿diste permiso root en Magisk?)", Toast.LENGTH_SHORT).show()
        }
    }

    Seccion("Root (Magisk)") {
        FilaSwitch("Usar funciones root",
            "Interruptor general. Apagado, la app no usa nada de root aunque Magisk lo permita", activo) { on ->
            Root.ponerActivo(on)
            if (on && disponible != true) conRoot("Root listo") { Root.pedir() }
        }
        if (activo) {
            Dato(
                "Acceso root",
                when (disponible) { true -> "Permitido"; false -> "Negado / no hay"; null -> "Sin pedir" },
                when (disponible) { true -> Paleta.verde; false -> Paleta.rojo; null -> Paleta.texto },
            )
            if (disponible != true) {
                BotonDeportivo(if (trabajando) "Esperando a Magisk…" else "Pedir acceso root",
                    { conRoot("Root listo") { Root.pedir() } }, Modifier.fillMaxWidth(), tamanoTexto = 13.sp)
            }
            BotonDeportivo("Dar permisos y quitar ahorro de batería",
                { conRoot("Permisos listos") { Root.darPermisos(ctx) } }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 12.sp)
            FilaSwitch("Pantalla de inicio (root)",
                "La pone como launcher sin pasar por los ajustes. Apagado, regresa el launcher de siempre", soyInicio) { on ->
                if (on) conRoot("Ahora es tu pantalla de inicio") { Root.ponerComoInicio(ctx) }
                else { Root.quitarComoInicio(ctx); soyInicio = false }
            }
            FilaSwitch("Gestos de Xiaomi con este launcher",
                "HyperOS pone los 3 botones cuando el launcher no es el suyo; esto regresa los gestos (experimental)",
                gestos) { on ->
                conRoot(if (on) "Gestos activados" else "Botones de vuelta") { Root.ponerGestos(on) }
            }
            FilaSwitch("Manzanita en ventana flotante",
                "La app de la manzanita se abre en un recuadro encima del mapa (experimental)", ventana) { on ->
                Root.ponerVentanaManzana(on)
                if (on) conRoot("Ventanas flotantes activas (si no salen, reinicia el teléfono)") { Root.prepararVentanas() }
            }
            FilaSwitch("Despertar al prender la moto",
                "Cuando la moto se conecta, se prende la pantalla y se abre el tablero", despertar) { on ->
                Root.ponerDespertar(on)
                if (on) conRoot("Listo: al prender la moto se abre el tablero") { Root.pedir() }
            }
        }
        FilaSwitch("Tablero sobre la pantalla de bloqueo",
            "Se ve sin desbloquear, como la navegación de Maps (no necesita root)", sobreBloqueo) { Root.ponerSobreBloqueo(it) }
        if (!activo || disponible != true) {
            Text("Gestos sin root: conecta el teléfono a la compu y corre\n" +
                "adb shell settings put global force_fsg_nav_bar 1\n(con 0 regresan los botones).",
                color = Paleta.textoSec, fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------
// Conexión
// ------------------------------------------------------------
@Composable
private fun SeccionConexion(onAccesoNotif: () -> Unit) {
    val demo by Moto.demo.collectAsStateWithLifecycle()
    val reenviar by Moto.reenviarNotif.collectAsStateWithLifecycle()
    val gps by Moto.enviarGps.collectAsStateWithLifecycle()
    val musica by MusicaManager.musica.collectAsStateWithLifecycle()
    val conexion by Moto.conexion.collectAsStateWithLifecycle()

    Seccion("Conexión") {
        IphoneAjustes(conexion == Conexion.CONECTADO)
        Dato("Moto", textoConexion(conexion), if (conexion == Conexion.CONECTADO) Paleta.verde else Paleta.texto)
        val fijada by com.xof.ft150ts.BleManager.tarjetaFijada.collectAsStateWithLifecycle()
        Dato("Tarjeta fijada", fijada ?: "Ninguna (se fija la primera)", if (fijada != null) Paleta.verde else Paleta.textoSec)
        Text(
            "Por seguridad la app solo se conecta a una tarjeta FT150TS verificada y, una vez fijada, solo a esa. " +
                "La primera vez Android te pedirá vincularla: acepta (el enlace queda cifrado).",
            color = Paleta.textoSec, fontSize = 11.sp,
        )
        if (fijada != null) {
            BotonDeportivo("Olvidar tarjeta", { com.xof.ft150ts.BleManager.olvidarTarjeta() }, Modifier.fillMaxWidth(),
                primario = false, color = Paleta.ambar, tamanoTexto = 12.sp)
        }
        FilaSwitch("Modo demo", "Datos de prueba, sin la moto", demo, Paleta.ambar) { Moto.ponerDemo(it) }
        FilaSwitch("Mandar notificaciones a la moto", "Llamadas y mensajes en la pantalla del tablero", reenviar) { Moto.ponerReenviarNotif(it) }
        FilaSwitch("Prestar el GPS del teléfono", "La moto usa tu ubicación y velocidad (no tiene módulo GPS)", gps) { Moto.ponerEnviarGps(it) }
        Dato(
            "Acceso a notificaciones y música",
            if (musica.permiso) "Activo" else "Falta",
            if (musica.permiso) Paleta.verde else Paleta.ambar,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!musica.permiso) {
                BotonDeportivo("Dar acceso", onAccesoNotif, Modifier.weight(1f), tamanoTexto = 13.sp)
            }
            BotonDeportivo("Probar aviso", { Moto.notificacionTelefono("Prueba FT150TS") }, Modifier.weight(1f),
                primario = false, tamanoTexto = 13.sp)
        }
    }
}

/** iPhone vinculado a la moto: notificaciones y música (ANCS/AMS), sin instalar nada en el iPhone. */
@Composable
private fun IphoneAjustes(motoConectada: Boolean) {
    val e by Moto.estado.enVivo()
    Dato("iPhone", if (e.iphone) "Vinculado a la moto" else "No conectado", if (e.iphone) Paleta.verde else Paleta.texto)
    Text(
        "Para ver sus notificaciones y controlar su música: en el iPhone abre Ajustes > Bluetooth, " +
            "toca FT150TS > Vincular y acepta \"Compartir notificaciones\". No hay que instalar nada en el iPhone.",
        color = Paleta.textoSec, fontSize = 12.sp,
    )
    if (motoConectada && e.iphone) {
        BotonDeportivo("Olvidar iPhone", { Moto.iphoneOlvidar() }, Modifier.fillMaxWidth(),
            primario = false, tamanoTexto = 12.sp)
    }
}

private fun textoConexion(c: Conexion): String = when (c) {
    Conexion.CONECTADO -> "Conectada"
    Conexion.DEMO -> "Modo demo"
    Conexion.BUSCANDO -> "Buscando…"
    Conexion.CONECTANDO -> "Conectando…"
    Conexion.BT_APAGADO -> "Bluetooth apagado"
    Conexion.SIN_PERMISO -> "Faltan permisos"
    Conexion.APAGADO -> "Desconectada"
}

// ------------------------------------------------------------
// Ruta (GPX)
// ------------------------------------------------------------
@Composable
private fun SeccionRuta() {
    val ctx = LocalContext.current
    val ruta by UbicacionManager.recorrido.collectAsStateWithLifecycle()
    var confirmarBorrar by remember { mutableStateOf(false) }
    val km = remember(ruta) { Gpx.distanciaKm() }

    Seccion("Ruta") {
        Dato("Recorrido en el mapa", if (ruta.size < 2) "Sin ruta" else "${fmt(km, 2)} km · ${ruta.size} puntos")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonDeportivo("Exportar GPX", {
                val ok = try { Gpx.compartir(ctx) } catch (e: Exception) { false }
                if (!ok) Toast.makeText(ctx, "Todavía no hay ruta para exportar", Toast.LENGTH_SHORT).show()
            }, Modifier.weight(1f), tamanoTexto = 13.sp)
            BotonDeportivo("Borrar ruta", { confirmarBorrar = true }, Modifier.weight(1f),
                primario = false, color = Paleta.rojo, tamanoTexto = 13.sp)
        }
        Text("El GPX se abre en Google Earth, Strava, OsmAnd, Wikiloc…", color = Paleta.textoSec, fontSize = 12.sp)
    }

    if (confirmarBorrar) {
        AlertDialog(
            onDismissRequest = { confirmarBorrar = false },
            title = { Text("¿Borrar la ruta?") },
            text = { Text("Se borra la línea del mapa. Si no la exportaste, se pierde.") },
            confirmButton = {
                TextButton(onClick = { confirmarBorrar = false; UbicacionManager.borrarRecorrido() }) {
                    Text("Borrar", color = Paleta.rojo)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrar = false }) { Text("Cancelar") } },
        )
    }
}

// ------------------------------------------------------------
// Acerca de
// ------------------------------------------------------------
@Composable
private fun SeccionAcercaDe(onVerInicio: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { versionApp(ctx) }

    Seccion("Acerca de") {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("FT150", color = Paleta.texto, fontSize = 40.sp, fontFamily = Fuentes.logo,
                    modifier = Modifier.alignByBaseline())
                Text("TS", color = Paleta.lima, fontSize = 16.sp, fontFamily = Fuentes.logo,
                    modifier = Modifier.alignByBaseline().padding(start = 3.dp))
            }
            Canvas(Modifier.width(150.dp).height(5.dp)) {
                val p = Path().apply {
                    moveTo(size.height, 0f); lineTo(size.width, 0f)
                    lineTo(size.width - size.height, size.height); lineTo(0f, size.height); close()
                }
                drawPath(p, Paleta.lima)
            }
            Spacer(Modifier.height(10.dp))
            EtiquetaPixel("xof custom dash", Paleta.lima, 9.sp)
            Spacer(Modifier.height(6.dp))
            Text("Versión $version", color = Paleta.textoSec, fontSize = 13.sp)
        }
        Dato("Hecho por", "XOF")
        Dato("Para", "Italika FT150TS")
        Dato("Tablero", "ESP32-S3 + LCD 20x4")
        Dato("Conexión", "Bluetooth LE")
        Dato("GPS", "Del teléfono")
        Text(
            "Mapa © colaboradores de OpenStreetMap (ODbL).\n" +
                "Estilos de mapa: CARTO, OpenTopoMap, Esri. Límites: OpenStreetMap (Overpass).\n" +
                "Clima: Open-Meteo.com (CC BY 4.0).\n" +
                "Letras Chakra Petch, Russo One y Press Start 2P (SIL Open Font License).",
            color = Paleta.textoSec, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        BotonDeportivo("Ver animación de inicio", onVerInicio, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 13.sp)
        BloqueActualizar()
    }
}

@Suppress("DEPRECATION")
private fun versionApp(ctx: Context): String =
    try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
