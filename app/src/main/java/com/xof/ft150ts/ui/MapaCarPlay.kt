package com.xof.ft150ts.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.SurfaceControlViewHost
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.BundleCompat
import com.xof.ft150ts.CarPlay
import com.xof.ft150ts.CarPlayPestana
import com.xof.ft150ts.EstadoCarPlay
import com.xof.ft150ts.EventoCarPlay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import com.xof.ft150ts.Lanzador
import com.xof.ft150ts.ModoCarPlay
import com.xof.ft150ts.TamanoCarPlay
import com.xof.ft150ts.Moto
import com.xof.ft150ts.Nocturno
import com.xof.ft150ts.Root
import kotlinx.coroutines.launch
import com.xof.ft150ts.ProtocoloCarPlay
import com.xof.ft150ts.UbicacionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "FT150-CarPlay"
/** Tiempo para que Android conecte con DiPlay, y para que DiPlay conteste un pedido del mapa. */
private const val LIMITE_MS = 8_000L

/**
 * Enlace con el servicio del mapa de DiPlay para un SurfaceView. Todo en el hilo principal.
 * Sigue el protocolo de DiPlay: ATTACH cuando hay servicio y superficie, RESIZE al cambiar de tamaño,
 * DETACH y desvincular al quitarlo. Cada mapa pegado es un decodificador en DiPlay: se pide uno solo.
 */
@RequiresApi(30)
private class EnlaceCarPlay(
    private val ctx: Context,
    private val diplay: CarPlay.InfoDiPlay,
    private val alEvento: (EventoCarPlay) -> Unit,
) {
    val vista = SurfaceView(ctx)
    private var servicio: Messenger? = null
    private var vinculado = false
    private var superficieLista = false
    private var pegado = false
    private var pedido = false
    /** false después de detener(): una respuesta tardía de DiPlay ya no cambia el estado de la pantalla. */
    private var activo = false
    private val reloj = Handler(Looper.getMainLooper())
    /** Se vinculó pero Android nunca conectó (MIUI bloquea así cuando DiPlay no tiene "Inicio automático"). */
    private val sinConexion = Runnable {
        if (activo && servicio == null) { soltarVinculo(); alEvento(EventoCarPlay.SinRespuesta) }
    }
    /** Se pidió el mapa y DiPlay no contestó: se suelta el pedido para poder repetirlo. */
    private val sinAdjuntar = Runnable {
        if (activo && pedido && !pegado) { pedido = false; alEvento(EventoCarPlay.SinRespuesta) }
    }

    /** Respuestas de DiPlay. Solo se aceptan si las manda el uid de DiPlay (no otra app con nuestro Messenger). */
    private val respuestas = Messenger(Handler(Looper.getMainLooper()) { m ->
        when {
            !activo -> {
                // Llegó después de soltar el mapa: si traía una superficie, se libera
                if (m.what == CarPlay.MSG_ATTACHED) runCatching {
                    BundleCompat.getParcelable(m.data, CarPlay.K_SURFACE_PACKAGE, SurfaceControlViewHost.SurfacePackage::class.java)?.release()
                }
            }
            m.sendingUid == diplay.uid -> alRespuesta(m)
            else -> Log.w(TAG, "respuesta ignorada de uid ${m.sendingUid}")
        }
        true
    })

    private val conexion = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            // Android conecta por nombre de componente: confirma que sea el de DiPlay que buscamos
            if (!activo || name.packageName != diplay.paquete) return
            reloj.removeCallbacks(sinConexion)
            servicio = Messenger(binder)
            alEvento(EventoCarPlay.Vinculado)
            pedirMapa()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // DiPlay se cerró; Android lo vuelve a vincular solo y onServiceConnected pide el mapa otra vez
            servicio = null
            pegado = false
            pedido = false
            if (activo) alEvento(EventoCarPlay.Desvinculado)
        }

        override fun onBindingDied(name: ComponentName) {
            servicio = null
            pegado = false
            pedido = false
            soltarVinculo()
            if (activo) alEvento(EventoCarPlay.Desvinculado)
        }
    }

    init {
        vista.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                superficieLista = true
                pedirMapa()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (pegado) mandar(CarPlay.MSG_RESIZE) {
                    putInt(CarPlay.K_ANCHO, ProtocoloCarPlay.medida(width))
                    putInt(CarPlay.K_ALTO, ProtocoloCarPlay.medida(height))
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                superficieLista = false
                pegado = false
                pedido = false
            }
        })
    }

    fun iniciar() {
        activo = true
        if (vinculado) return
        val servicioDiPlay = diplay.servicio ?: run { alEvento(EventoCarPlay.NoSeVinculo); return }
        alEvento(EventoCarPlay.Vinculando)
        val i = Intent(CarPlay.ACCION_MAPA).setClassName(diplay.paquete, servicioDiPlay)
        vinculado = try { ctx.bindService(i, conexion, Context.BIND_AUTO_CREATE) } catch (_: SecurityException) { false }
        if (!vinculado) {
            soltarVinculo()
            alEvento(EventoCarPlay.NoSeVinculo)
        } else {
            reloj.postDelayed(sinConexion, LIMITE_MS)
        }
    }

    /**
     * Vuelve a pedir el mapa (DiPlay lo negó, se reinició o prendiste la opción). No duplica: si hay un pedido en
     * camino, si el mapa ya está pegado o si Android está por reconectar el servicio, no hace nada.
     */
    fun reintentar() {
        if (!activo || pegado || pedido) return
        when {
            !vinculado -> iniciar()
            servicio != null -> pedirMapa()
            // Vinculado sin servicio: Android lo reconecta solo; si no llega, sinConexion lo suelta y se reintenta
            else -> if (!reloj.hasCallbacks(sinConexion)) reloj.postDelayed(sinConexion, LIMITE_MS)
        }
    }

    fun detener() {
        activo = false
        reloj.removeCallbacks(sinConexion)
        reloj.removeCallbacks(sinAdjuntar)
        if (pegado || pedido) mandar(CarPlay.MSG_DETACH) {}
        pegado = false
        pedido = false
        soltarVinculo()
        servicio = null
    }

    private fun soltarVinculo() {
        if (vinculado) runCatching { ctx.unbindService(conexion) }
        vinculado = false
    }

    // hostToken: obsoleto desde Android 15 pero sigue funcionando, y es lo que pide el protocolo de DiPlay
    @Suppress("DEPRECATION")
    private fun pedirMapa() {
        if (servicio == null || !superficieLista || pegado || pedido) return
        val token = vista.hostToken ?: return
        val pantalla = vista.display ?: return
        pedido = true
        reloj.removeCallbacks(sinAdjuntar)
        reloj.postDelayed(sinAdjuntar, LIMITE_MS)
        mandar(CarPlay.MSG_ATTACH) {
            putBinder(CarPlay.K_HOST_TOKEN, token)
            putInt(CarPlay.K_DISPLAY_ID, pantalla.displayId)
            putInt(CarPlay.K_ANCHO, ProtocoloCarPlay.medida(vista.width))
            putInt(CarPlay.K_ALTO, ProtocoloCarPlay.medida(vista.height))
        }
    }

    private fun alRespuesta(m: Message) {
        val datos = m.data ?: Bundle.EMPTY
        when (m.what) {
            CarPlay.MSG_ATTACHED -> {
                pedido = false
                reloj.removeCallbacks(sinAdjuntar)
                val paquete: SurfaceControlViewHost.SurfacePackage? = try {
                    BundleCompat.getParcelable(datos, CarPlay.K_SURFACE_PACKAGE, SurfaceControlViewHost.SurfacePackage::class.java)
                } catch (_: Exception) { null }
                if (paquete == null || !superficieLista) {
                    paquete?.release()
                    alEvento(EventoCarPlay.Error(ProtocoloCarPlay.ERROR_PEDIDO))
                    return
                }
                vista.setChildSurfacePackage(paquete)
                pegado = true
                alEvento(EventoCarPlay.Entregado(datos.getBoolean(CarPlay.K_STREAM_ACTIVO)))
            }
            CarPlay.MSG_STREAM_STATE -> if (pegado) alEvento(EventoCarPlay.Flujo(datos.getBoolean(CarPlay.K_STREAM_ACTIVO)))
            CarPlay.MSG_ERROR -> {
                // DiPlay suelta el mapa al mandar un error (p. ej. apagaste compartir): se trata como DETACH
                pegado = false
                pedido = false
                reloj.removeCallbacks(sinAdjuntar)
                alEvento(EventoCarPlay.Error(datos.getString(CarPlay.K_ERROR)))
            }
        }
    }

    private fun mandar(que: Int, llenar: Bundle.() -> Unit) {
        val s = servicio ?: return
        try {
            s.send(Message.obtain(null, que).apply {
                data = Bundle().apply(llenar)
                replyTo = respuestas
            })
        } catch (_: RemoteException) {
            servicio = null
            pegado = false
            pedido = false
        }
    }
}

/**
 * La superficie donde DiPlay dibuja el mapa de CarPlay. [alEstado] recibe el estado real que manda DiPlay.
 * Al salir de la pantalla (página oculta, pantalla apagada, OSM) suelta el mapa y DiPlay apaga ese decodificador.
 */
@RequiresApi(30)
@Composable
internal fun SuperficieCarPlay(
    diplay: CarPlay.InfoDiPlay,
    estado: EstadoCarPlay,
    alEvento: (EventoCarPlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val evento by rememberUpdatedState(alEvento)
    val enlace = remember(diplay.paquete, diplay.servicio) { EnlaceCarPlay(ctx, diplay) { evento(it) } }
    DisposableEffect(enlace) {
        enlace.iniciar()
        onDispose { enlace.detener() }
    }
    // Si DiPlay negó el mapa, no contestó o se cerró, se vuelve a pedir solo (5 s, 10 s, 20 s y luego cada 30 s)
    // hasta que salga de ese estado. Va con la llave "hay que reintentar": un error igual al anterior no lo corta.
    val reintentar = ProtocoloCarPlay.reintentar(estado)
    LaunchedEffect(enlace, reintentar) {
        if (!reintentar) return@LaunchedEffect
        var intento = 0
        while (true) {
            delay(ProtocoloCarPlay.espera(intento++))
            enlace.reintentar()
        }
    }
    AndroidView(factory = { enlace.vista }, modifier = modifier)
}

/** Resultado de buscar DiPlay ([info] null = no está instalado). null afuera = todavía buscando. */
internal class BusquedaDiPlay(val info: CarPlay.InfoDiPlay?)

/** Busca DiPlay fuera del hilo de la pantalla (PackageManager y firmas tardan unos milisegundos). */
internal suspend fun buscarDiPlay(ctx: Context): BusquedaDiPlay =
    withContext(Dispatchers.IO) { BusquedaDiPlay(CarPlay.buscar(ctx)) }

/**
 * Abre la pantalla de CarPlay de DiPlay según el modo. [rect] = dónde está el mapa (pixeles de pantalla), para la
 * ventana. Regresa el motivo si no se pudo (para mostrarlo), o null.
 */
internal suspend fun abrirSegunModo(ctx: Context, modo: ModoCarPlay, rect: android.graphics.Rect?): String? = when (modo) {
    ModoCarPlay.PESTANA -> if (CarPlayPestana.disponible()) { CarPlayPestana.mostrar(ctx); null }
        else if (CarPlay.abrirCarPlay(ctx)) "Sin root se abre en pantalla completa (la pestaña completa necesita root)"
        else "DiPlay no está instalado o su firma no es de fiar"
    ModoCarPlay.VENTANA -> when {
        rect == null -> "Todavía no se sabe dónde está el mapa"
        // Con root: ventana a la medida del mapa aunque CarPlay ya estuviera abierto (probado en HyperOS 3)
        CarPlay.rootListo() -> CarPlay.forzarVentana(ctx, rect)
        else -> CarPlay.abrirEnVentana(ctx, rect)
    }
    ModoCarPlay.DIVIDIDA -> CarPlay.abrirDividida(ctx)
    ModoCarPlay.COMPLETA, ModoCarPlay.MAPA -> if (CarPlay.abrirCarPlay(ctx)) null else "DiPlay no está instalado o su firma no es de fiar"
}

/**
 * Franja de CarPlay arriba del mapa: estado real, velocidad, abrir CarPlay en el modo elegido y volver al mapa OSM.
 * [rectMapa] = el mapa en la pantalla (para poner ahí la ventana de CarPlay).
 */
@Composable
internal fun BarraCarPlay(estado: EstadoCarPlay, rectMapa: android.graphics.Rect?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val modo by CarPlay.modo.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var detalle by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val vivo = estado == EstadoCarPlay.EnVivo
    val color = when (estado) {
        EstadoCarPlay.EnVivo, EstadoCarPlay.Listo -> Paleta.verde
        EstadoCarPlay.Conectando, EstadoCarPlay.Esperando, EstadoCarPlay.Perdido -> Paleta.ambar
        else -> Paleta.rojo
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Estado: tócalo para ver qué hacer y cambiar el modo
        val forma = Formas.chip
        Row(
            Modifier.weight(1f).clip(forma).background(Paleta.fondo.copy(alpha = 0.82f))
                .border(1.dp, color.copy(alpha = 0.7f), forma)
                .clickable { detalle = true }.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EtiquetaPixel("carplay", color, 6.sp)
            Spacer(Modifier.width(6.dp))
            Text(ProtocoloCarPlay.texto(estado) + " · " + modo.nombre.lowercase(), color = Paleta.texto, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (vivo) VelocidadCompacta()
        when (estado) {
            EstadoCarPlay.SinDiPlay -> BotonDeportivo("Instalar", { CarPlay.abrirDescarga(ctx) }, tamanoTexto = 11.sp)
            EstadoCarPlay.FirmaDesconocida -> BotonDeportivo("Ver", { detalle = true }, tamanoTexto = 11.sp)
            else -> BotonDeportivo("CarPlay", {
                scope.launch {
                    aviso = abrirSegunModo(ctx, modo, rectMapa)
                    if (aviso != null) detalle = true
                }
            }, primario = vivo || estado == EstadoCarPlay.Listo, tamanoTexto = 11.sp)
        }
        BotonDeportivo("OSM", { CarPlay.ponerEnMapa(ctx, false) }, primario = false, tamanoTexto = 11.sp)
    }
    if (detalle) DialogoCarPlay(estado, rectMapa, aviso) { detalle = false; aviso = null }
}

/** Velocidad chica en la franja (el mapa de CarPlay ocupa el resto). */
@Composable
private fun VelocidadCompacta() {
    val rap by Moto.rapida.enVivo()
    val exceso by Moto.excesoVel.enVivo()
    val velTel by UbicacionManager.velocidad.enVivo()
    val vel = if (rap.vel >= 0) rap.vel else velTel
    Row(verticalAlignment = Alignment.Bottom) {
        Text(if (vel >= 0) "${(vel + 0.5f).toInt()}" else "--", color = if (exceso) Paleta.rojo else Paleta.texto,
            fontSize = 22.sp, fontFamily = Fuentes.logo)
        Spacer(Modifier.width(3.dp))
        EtiquetaPixel("km/h", if (exceso) Paleta.rojo else Paleta.lima, 6.sp, Modifier.padding(bottom = 4.dp))
    }
}

/** Qué pasa con CarPlay, el modo de pantalla y qué hacer. */
@Composable
private fun DialogoCarPlay(estado: EstadoCarPlay, rectMapa: android.graphics.Rect?, aviso: String?, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val modo by CarPlay.modo.collectAsStateWithLifecycle()
    val abrirSolo by CarPlay.abrirSolo.collectAsStateWithLifecycle()
    val ventanaIgnorada by CarPlay.ventanaIgnorada.collectAsStateWithLifecycle()
    val nocturno by Nocturno.activo.collectAsStateWithLifecycle()
    val rootActivo by Root.activo.collectAsStateWithLifecycle()
    val rootDisponible by Root.disponible.collectAsStateWithLifecycle()
    val rootOk = rootActivo && rootDisponible == true
    var mensaje by remember { mutableStateOf(aviso) }
    var ventanas by remember { mutableStateOf(CarPlay.ventanasDisponibles(ctx)) }
    // Se lee fuera del hilo de la pantalla (firmas y versión vienen de PackageManager)
    var busqueda by remember { mutableStateOf<BusquedaDiPlay?>(null) }
    LaunchedEffect(estado) { busqueda = buscarDiPlay(ctx) }
    val b = busqueda ?: return     // un instante, mientras busca (así no dice "no instalado" de más)
    val diplay = b.info
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { EtiquetaPixel("carplay (diplay)", Paleta.lima, 9.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mensaje?.let { Text(it, color = Paleta.ambar, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                Dato("Estado", ProtocoloCarPlay.texto(estado))
                Dato("DiPlay", diplay?.let { d -> d.paquete + if (d.version.isNotBlank()) " · ${d.version}" else "" }
                    ?: "No instalado", if (diplay == null) Paleta.ambar else Paleta.texto)
                if (diplay != null) {
                    Dato("Firma", when {
                        diplay.firmas.any { it in CarPlay.FIRMAS_OFICIALES } -> "Release oficial"
                        diplay.confiable -> "Tu copia (confiable)"
                        else -> "Desconocida"
                    }, if (diplay.confiable) Paleta.texto else Paleta.rojo)
                }

                // ---- Modo de pantalla ----
                Text("Cómo se ve CarPlay", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                // Dos filas (cinco opciones no caben en una en vertical)
                Selector(ModoCarPlay.entries.take(3).map { it.nombre }, ModoCarPlay.entries.indexOf(modo).takeIf { it < 3 } ?: -1,
                    { CarPlay.ponerModo(ctx, ModoCarPlay.entries[it]) }, Modifier.fillMaxWidth())
                Selector(ModoCarPlay.entries.drop(3).map { it.nombre }, (ModoCarPlay.entries.indexOf(modo) - 3).takeIf { it >= 0 } ?: -1,
                    { CarPlay.ponerModo(ctx, ModoCarPlay.entries[it + 3]) }, Modifier.fillMaxWidth())
                Text(modo.explicacion, color = Paleta.textoSec, fontSize = 11.sp)
                if (modo == ModoCarPlay.VENTANA) {
                    Dato("Ventanas flotantes de Android", if (ventanas) "Prendidas" else "Apagadas",
                        if (ventanas) Paleta.verde else Paleta.ambar)
                    if (ventanaIgnorada) {
                        Text("La última vez Android abrió CarPlay en pantalla completa: este sistema no hace ventanas " +
                            "flotantes de otras apps aquí. Por eso ya no se abre solo en este modo. Usa Dividida (CarPlay y " +
                            "FT150TS juntos), o con root \"Forzar ventana\".", color = Paleta.ambar, fontSize = 11.sp)
                        BotonDeportivo("Usar Dividida", { CarPlay.ponerModo(ctx, ModoCarPlay.DIVIDIDA) }, Modifier.fillMaxWidth(),
                            tamanoTexto = 11.sp)
                    }
                    if (!ventanas) {
                        Text("Prende Opciones de desarrollador → \"Habilitar ventanas de forma libre\" y reinicia el " +
                            "teléfono. Con root se hace aquí:", color = Paleta.textoSec, fontSize = 11.sp)
                        BotonDeportivo(if (rootOk) "Prender ventanas flotantes (root)" else "Pide root en Ajustes → Root", {
                            if (rootOk) scope.launch {
                                val ok = Root.prepararVentanas()
                                ventanas = CarPlay.ventanasDisponibles(ctx)
                                mensaje = if (ok) "Listo. Si la ventana no sale flotante, reinicia el teléfono una vez." else "Magisk no dejó"
                            }
                        }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 11.sp)
                    } else if (rootOk) {
                        BotonDeportivo("Forzar ventana flotante (root)", {
                            scope.launch { mensaje = CarPlay.forzarVentana(ctx, rectMapa) ?: "CarPlay quedó como ventana sobre el mapa" }
                        }, Modifier.fillMaxWidth(), primario = false, tamanoTexto = 11.sp)
                    }
                    Text("Si CarPlay sale en pantalla completa es porque ya estaba abierto así: ciérralo en Recientes (la " +
                        "sesión sigue en DiPlay) y vuelve a tocar CarPlay.", color = Paleta.textoSec, fontSize = 11.sp)
                }
                if (modo == ModoCarPlay.PESTANA) {
                    val tam by CarPlay.tamano.collectAsStateWithLifecycle()
                    Text("Tamaño de CarPlay", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Selector(TamanoCarPlay.entries.map { it.nombre }, TamanoCarPlay.entries.indexOf(tam),
                        { CarPlay.ponerTamano(ctx, TamanoCarPlay.entries[it]) }, Modifier.fillMaxWidth())
                    Text(when (tam) {
                        TamanoCarPlay.MAXIMO -> "De orilla a orilla debajo de tus pestañas (no tapa el riel de Vértice). Sin la píldora de abajo."
                        TamanoCarPlay.AMPLIO -> "El área de las páginas de FT150TS."
                        TamanoCarPlay.AJUSTADO -> "Dentro del marco del tema, con los márgenes de las tarjetas."
                    } + " Se aplica la próxima vez que entres a Auto.", color = Paleta.textoSec, fontSize = 11.sp)
                    val gestos by com.xof.ft150ts.GestosCarPlay.activo.collectAsStateWithLifecycle()
                    FilaSwitch("Gestos: de CarPlay a Tablero y Moto",
                        "En Widgets desliza a la derecha → Tablero; en tu última página de apps, a la izquierda → Moto. Dentro de una app o del mapa no hace nada",
                        gestos) { com.xof.ft150ts.GestosCarPlay.ponerActivo(it) }
                }
                if (modo == ModoCarPlay.DIVIDIDA && remember { Lanzador.esInicioPredeterminado(ctx) }) {
                    Text("FT150TS es tu pantalla de inicio y Android no deja la pantalla de inicio en pantalla dividida: " +
                        "CarPlay saldría completo. Usa Ventana (con root queda a la medida del mapa), o quita FT150TS " +
                        "como pantalla de inicio.", color = Paleta.ambar, fontSize = 11.sp)
                    BotonDeportivo("Usar Ventana", { CarPlay.ponerModo(ctx, ModoCarPlay.VENTANA) }, Modifier.fillMaxWidth(),
                        tamanoTexto = 11.sp)
                }
                if (modo == ModoCarPlay.VENTANA || modo == ModoCarPlay.DIVIDIDA) {
                    FilaSwitch("Abrir CarPlay al entrar a Auto", "Al ver la página Auto se abre CarPlay solo (una vez por visita).",
                        abrirSolo) { CarPlay.ponerAbrirSolo(ctx, it) }
                }
                if (diplay != null && diplay.confiable) {
                    BotonDeportivo("Abrir CarPlay ahora", {
                        scope.launch {
                            mensaje = abrirSegunModo(ctx, modo, rectMapa)
                            if (mensaje == null) onCerrar()
                        }
                    }, Modifier.fillMaxWidth(), tamanoTexto = 12.sp)
                    BotonDeportivo("Abrir DiPlay (ajustes y conexión)", { CarPlay.abrirDiPlay(ctx) }, Modifier.fillMaxWidth(),
                        primario = false, tamanoTexto = 11.sp)
                }
                if (diplay == null) {
                    BotonDeportivo("Descargar DiPlay", { CarPlay.abrirDescarga(ctx) }, Modifier.fillMaxWidth(), tamanoTexto = 11.sp)
                }
                // Sesión protegida: el ahorro nocturno no entra (ni corta DiPlay) esta noche
                if (nocturno) {
                    BotonDeportivo("Proteger esta sesión (sin ahorro nocturno hoy)", { Nocturno.protegerEstaNoche(); onCerrar() },
                        Modifier.fillMaxWidth(), primario = false, tamanoTexto = 11.sp)
                }
                if (diplay != null && !diplay.confiable) {
                    Text("Solo confía si tú compilaste DiPlay. Una app ajena con ese nombre podría dibujar dentro de " +
                        "tu mapa.", color = Paleta.ambar, fontSize = 11.sp)
                    BotonDeportivo("Confiar en esta copia", {
                        CarPlay.confiarEnInstalada(ctx); onCerrar()
                    }, Modifier.fillMaxWidth(), primario = false, color = Paleta.ambar, tamanoTexto = 11.sp)
                }
                Text(ProtocoloCarPlay.ayuda(estado), color = Paleta.textoSec, fontSize = 11.sp)
                if (modo == ModoCarPlay.MAPA) {
                    Text("Modo Mapa: en DiPlay → Advanced prende \"CarPlay map on dashboard (experimental)\" y \"Share the " +
                        "live map with other launchers\", y abre su pantalla de CarPlay una vez. Si se queda en \"carplay sin " +
                        "mapa\", tu iPhone no manda ese mapa en un teléfono: usa Ventana.", color = Paleta.textoSec, fontSize = 11.sp)
                }
                Text("La conexión con el iPhone, el audio y Siri los maneja DiPlay; para terminar la sesión, desconéctala " +
                    "en DiPlay.", color = Paleta.textoSec, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}
