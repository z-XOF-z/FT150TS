package com.xof.ft150ts

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * CarPlay en la página Auto, con DiPlay (github.com/shihabal3amri/DiPlay, GPL-3.0).
 *
 * DiPlay es el receptor de CarPlay (iAP2, autenticación MFi, AirPlay, video y audio) y se queda como app aparte:
 * su código no entra en esta app. Esta app le pide el mapa en vivo de CarPlay con el protocolo público de DiPlay
 * (docs/LAUNCHER_INTEGRATION.md): un servicio con Messenger que regresa un SurfacePackage. DiPlay lo dibuja dentro
 * de nuestro SurfaceView; el estado (en vivo / esperando) lo manda DiPlay, nada se simula aquí.
 *
 * Seguridad: solo se habla con DiPlay de verdad (paquete conocido + firma de los releases oficiales, o una copia que
 * tú marcaste como confiable), y las respuestas se aceptan solo si vienen del uid de esa app.
 */
object CarPlay {
    // ---- Protocolo de DiPlay (MapEmbedService). Copiado de su documentación, no de su código. ----
    const val ACCION_MAPA = "com.shihab.diplay.action.EMBED_MAP"
    const val MSG_ATTACH = 1
    const val MSG_RESIZE = 2
    const val MSG_DETACH = 3
    const val MSG_ATTACHED = 101
    const val MSG_STREAM_STATE = 102
    const val MSG_ERROR = 199
    const val K_HOST_TOKEN = "hostToken"
    const val K_DISPLAY_ID = "displayId"
    const val K_ANCHO = "width"
    const val K_ALTO = "height"
    const val K_SURFACE_PACKAGE = "surfacePackage"
    const val K_STREAM_ACTIVO = "streamActive"
    const val K_ERROR = "error"

    /** La pantalla de CarPlay de DiPlay (exportada, singleTask). */
    private const val ACTIVIDAD_CARPLAY = "com.shilapi.xcertplay.CarPlayHostActivity"
    const val ACTIVIDAD_CARPLAY_PUBLICA = ACTIVIDAD_CARPLAY

    /** Paquetes de DiPlay: release y versión de pruebas. Cualquier otra app con la misma acción se ignora. */
    val PAQUETES = listOf("com.shihab.diplay", "com.shihab.diplay.hudtest")

    /**
     * SHA-256 del certificado con que se firman los releases de DiPlay (CN=DiPlay, OU=Private Beta, O=DiPlay),
     * leído de DiPlay-0.2.18.apk con `apksigner verify --print-certs` el 2026-10-15.
     */
    val FIRMAS_OFICIALES = setOf("87b38b12788dcb202a961215f2572e30ec2dc9d8ef4bc070d05f77e49291a363")

    /** Mostrar CarPlay en el mapa de la página Auto (si no, el mapa de OpenStreetMap de siempre). */
    val enMapa = MutableStateFlow(false)
    /** Cómo se ve CarPlay en la página Auto (ver [ModoCarPlay]). */
    val modo = MutableStateFlow(ModoCarPlay.PESTANA)
    /** En Ventana / Dividida: abrir CarPlay solo al entrar a la página Auto. */
    val abrirSolo = MutableStateFlow(true)
    /** Modo Pestaña: cuánto espacio usa CarPlay. */
    val tamano = MutableStateFlow(TamanoCarPlay.MAXIMO)

    fun ponerTamano(ctx: Context, t: TamanoCarPlay) {
        tamano.value = t
        prefs(ctx).edit().putString("tamano", t.name).apply()
        CarPlayPestana.reiniciarMedidas()
    }
    /** Cuándo se abrió CarPlay solo por última vez (elapsedRealtime), para no abrirlo en ciclo. */
    @Volatile var ultimaApertura = -60_000L
    /**
     * Android ignoró la ventana flotante y abrió CarPlay en pantalla completa (FT150TS dejó de verse en seguida).
     * Pasa en Android de teléfono sin ventanas de forma libre. Mientras sea true, no se abre solo en modo Ventana.
     */
    val ventanaIgnorada = MutableStateFlow(false)
    private val vigilancia = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    /** Después de pedir la ventana: si FT150TS deja de verse en 6 s, la ventana salió en pantalla completa (6 s: en un teléfono cargado la otra app tarda en aparecer). */
    private fun vigilarVentana(ctx: Context) {
        vigilancia.launch {
            val tapada = kotlinx.coroutines.withTimeoutOrNull(6_000L) { Lanzador.enPantalla.first { !it } } != null
            ventanaIgnorada.value = tapada
            prefs(ctx).edit().putBoolean("ventanaIgnorada", tapada).apply()
        }
    }
    /** Última vez (reloj) que DiPlay reportó el mapa en vivo en la página Auto. Lo usa el horario nocturno. */
    val ultimoEnVivo = MutableStateFlow(0L)
    /** Firmas de copias de DiPlay compiladas por ti (otra llave) que marcaste como confiables en este teléfono. */
    val firmasConfiables = MutableStateFlow<Set<String>>(emptySet())

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("carplay", Context.MODE_PRIVATE)
    /** Aparte, y fuera de los respaldos (Respaldo.NO_RESTAURAR): un archivo ajeno no puede marcar otra app como DiPlay. */
    private fun prefsConfianza(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS_CONFIANZA, Context.MODE_PRIVATE)
    const val PREFS_CONFIANZA = "carplay_confianza"

    fun iniciar(ctx: Context) {
        enMapa.value = prefs(ctx).getBoolean("enMapa", false)
        modo.value = ModoCarPlay.entries.firstOrNull { it.name == prefs(ctx).getString("modo", null) } ?: ModoCarPlay.PESTANA
        abrirSolo.value = prefs(ctx).getBoolean("abrirSolo", true)
        ventanaIgnorada.value = prefs(ctx).getBoolean("ventanaIgnorada", false)
        tamano.value = TamanoCarPlay.entries.firstOrNull { it.name == prefs(ctx).getString("tamano", null) } ?: TamanoCarPlay.MAXIMO
        firmasConfiables.value = prefsConfianza(ctx).getStringSet("firmas", emptySet())
            ?.filter { ProtocoloCarPlay.esSha256(it) }?.toSet() ?: emptySet()
    }

    fun ponerEnMapa(ctx: Context, on: Boolean) {
        enMapa.value = on
        prefs(ctx).edit().putBoolean("enMapa", on).apply()
    }

    fun ponerModo(ctx: Context, m: ModoCarPlay) {
        modo.value = m
        prefs(ctx).edit().putString("modo", m.name).apply()
    }

    fun ponerAbrirSolo(ctx: Context, on: Boolean) {
        abrirSolo.value = on
        prefs(ctx).edit().putBoolean("abrirSolo", on).apply()
    }

    // ---------------- Pantalla completa de CarPlay en una ventana ----------------

    /**
     * ¿Android deja ventanas flotantes de otras apps? (Opciones de desarrollador → "Habilitar ventanas de forma
     * libre", o Ajustes → Root → "Manzanita en ventana flotante", que prende lo mismo.)
     */
    fun ventanasDisponibles(ctx: Context): Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            Settings.Global.getInt(ctx.contentResolver, "enable_freeform_support", 0) == 1

    private fun intentCarPlay(d: InfoDiPlay) =
        Intent().setClassName(d.paquete, ACTIVIDAD_CARPLAY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Abre la pantalla de CarPlay de DiPlay (la completa, con toque) en una ventana con los límites [r] (pixeles de
     * pantalla). Con las ventanas de forma libre prendidas, Android la pone flotante exactamente ahí. DiPlay la
     * redibuja a ese tamaño. Regresa el motivo si no se pudo, o null si se pidió.
     */
    fun abrirEnVentana(ctx: Context, r: Rect): String? {
        val d = buscar(ctx)?.takeIf { it.confiable } ?: return "DiPlay no está instalado o su firma no es de fiar"
        if (!ventanasDisponibles(ctx)) return "Tu Android tiene apagadas las ventanas flotantes"
        val op = ActivityOptions.makeBasic().setLaunchBounds(r)
        // Pedir modo "forma libre" (5) también; si este Android no lo deja, con los límites basta
        try { ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(op, 5) } catch (_: Exception) { }
        return try {
            ctx.startActivity(intentCarPlay(d), op.toBundle())
            vigilarVentana(ctx)
            null
        } catch (e: Exception) { "Android no la abrió: ${e.javaClass.simpleName}" }
    }

    /** Abre CarPlay al lado de FT150TS en pantalla dividida. null = se pidió; el sistema decide si la divide. */
    fun abrirDividida(ctx: Context): String? {
        val d = buscar(ctx)?.takeIf { it.confiable } ?: return "DiPlay no está instalado o su firma no es de fiar"
        return try {
            ctx.startActivity(intentCarPlay(d).addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
            null
        } catch (e: Exception) { "Android no la abrió: ${e.javaClass.simpleName}" }
    }

    /** ¿Se puede usar root para la ventana a la medida? */
    fun rootListo() = Root.activo.value && Root.disponible.value == true

    /**
     * Con root: CarPlay como ventana flotante **a la medida del mapa**, aunque ya estuviera abierto en pantalla
     * completa (Android ignora los límites al traer una tarea que ya existe). Probado en el POCO F4 (HyperOS 3):
     *  1. `am start --windowingMode 5` pasa la tarea de DiPlay a ventana flotante (sin -S: nunca la detiene).
     *  2. `am task resize` la pone sobre el mapa [r] (pixeles de pantalla).
     *  3. HyperOS dibuja sus ventanas flotantes a escala (~0.61): se lee la escala real del compositor y se agrandan
     *     los límites para compensarla, sin salirse de la pantalla [ancho]×[alto].
     * Todo son comandos fijos: el paquete sale de la lista de DiPlay y los números se validan.
     */
    suspend fun forzarVentana(ctx: Context, r: Rect? = null): String? {
        val d = buscar(ctx)?.takeIf { it.confiable } ?: return "DiPlay no está instalado o su firma no es de fiar"
        if (!ProtocoloCarPlay.paqueteSeguro(d.paquete)) return "Paquete inválido"
        val pantalla = (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).let {
            if (Build.VERSION.SDK_INT >= 30) it.currentWindowMetrics.bounds
            else Rect(0, 0, ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels)
        }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val inicio = Root.ejecutarCada(listOf(
                "am start --windowingMode 5 -n ${d.paquete}/$ACTIVIDAD_CARPLAY",
                ProtocoloCarPlay.cmdBuscarTarea(d.paquete),
            )) ?: return@withContext "Requiere root"
            if (!inicio[0].ok || inicio[0].salida.contains("Error", true)) {
                return@withContext "Android no aceptó la ventana: ${inicio[0].salida.take(80)}"
            }
            if (r == null) return@withContext null
            val tarea = ProtocoloCarPlay.leerTarea(inicio[1].salida) ?: return@withContext "No encontré la ventana de CarPlay"
            // Primera pasada: los límites del mapa. Luego se mide la escala y se compensa.
            val mapa = intArrayOf(r.left.coerceAtLeast(0), r.top.coerceAtLeast(0), r.right, r.bottom)
            val res1 = Root.ejecutarCada(listOf(
                ProtocoloCarPlay.cmdRedimensionar(tarea, mapa),
                "sleep 0.4",
                ProtocoloCarPlay.cmdLeerEscala(),
            )) ?: return@withContext "Requiere root"
            val escala = ProtocoloCarPlay.leerEscala(res1[2].salida)
            if (escala != null && escala < 0.98f) {
                val compensado = ProtocoloCarPlay.compensar(mapa, escala, pantalla.width(), pantalla.height())
                Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdRedimensionar(tarea, compensado)))
            }
            null
        }
    }

    /** Confía en la copia de DiPlay instalada (la que tú compilaste). Solo desde un botón en pantalla. */
    fun confiarEnInstalada(ctx: Context) {
        val d = buscar(ctx) ?: return
        val nuevas = firmasConfiables.value + d.firmas
        firmasConfiables.value = nuevas
        prefsConfianza(ctx).edit().putStringSet("firmas", nuevas).apply()
    }

    fun olvidarConfianza(ctx: Context) {
        firmasConfiables.value = emptySet()
        prefsConfianza(ctx).edit().clear().apply()
    }

    /** DiPlay instalado: paquete, servicio del mapa (si lo tiene), uid y si su firma es de fiar. */
    class InfoDiPlay(
        val paquete: String,
        val servicio: String?,
        val uid: Int,
        val firmas: Set<String>,
        val confiable: Boolean,
        /** versionName de DiPlay (el servicio del mapa llegó en la 0.2.9). */
        val version: String = "",
    )

    /** Busca DiPlay. null = no está instalado (o solo hay apps ajenas que dicen ser él). */
    fun buscar(ctx: Context): InfoDiPlay? {
        val pm = ctx.packageManager
        // El servicio se busca por su acción (cambia de paquete entre release y pruebas), pero solo en paquetes de DiPlay
        val servicios = try {
            pm.queryIntentServices(Intent(ACCION_MAPA), 0).mapNotNull { it.serviceInfo }
        } catch (_: Exception) { emptyList() }
        for (paquete in PAQUETES) {
            val app = try { pm.getApplicationInfo(paquete, 0) } catch (_: PackageManager.NameNotFoundException) { continue }
            val firmas = firmasDe(pm, paquete)
            val servicio = servicios.firstOrNull { it.packageName == paquete && it.exported }?.name
            val version = try { pm.getPackageInfo(paquete, 0).versionName ?: "" } catch (_: Exception) { "" }
            return InfoDiPlay(
                paquete, servicio, app.uid, firmas,
                ProtocoloCarPlay.firmaConfiable(firmas, FIRMAS_OFICIALES, firmasConfiables.value),
                version,
            )
        }
        return null
    }

    /** SHA-256 (hex en minúsculas) de los certificados con que está firmado [paquete]. */
    @Suppress("DEPRECATION")
    private fun firmasDe(pm: PackageManager, paquete: String): Set<String> = try {
        val firmas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(paquete, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            when {
                info == null -> emptyArray()
                info.hasMultipleSigners() -> info.apkContentsSigners
                else -> info.signingCertificateHistory   // incluye la llave anterior si DiPlay la rotó
            }
        } else {
            pm.getPackageInfo(paquete, PackageManager.GET_SIGNATURES).signatures
        }
        (firmas ?: emptyArray()).map { ProtocoloCarPlay.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    } catch (_: Exception) { emptySet() }

    /** ¿Este Android puede meter el mapa de otra app en la nuestra? (SurfaceControlViewHost: Android 11+) */
    val incrustarPosible: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Abre CarPlay en pantalla completa (DiPlay); si no se puede, DiPlay. false si no hay DiPlay de fiar. */
    fun abrirCarPlay(ctx: Context): Boolean {
        val d = buscar(ctx)?.takeIf { it.confiable } ?: return false
        val i = Intent().setClassName(d.paquete, ACTIVIDAD_CARPLAY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); true } catch (_: Exception) { abrirDiPlay(ctx) }
    }

    /** Abre DiPlay (ajustes, conexión del iPhone). */
    fun abrirDiPlay(ctx: Context): Boolean {
        val d = buscar(ctx)?.takeIf { it.confiable } ?: return false
        val i = ctx.packageManager.getLaunchIntentForPackage(d.paquete) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }

    /** Página de releases de DiPlay (para instalarlo). */
    fun abrirDescarga(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/shihabal3amri/DiPlay/releases"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { }
    }
}

/**
 * Cómo se ve CarPlay en la página Auto. Lo único que una app puede pedirle a DiPlay es su pantalla de CarPlay
 * (como ventana) o su mapa de tablero (incrustado): Android no deja meter la actividad de otra app dentro de la
 * nuestra sin permisos de sistema.
 */
enum class ModoCarPlay(val nombre: String, val explicacion: String) {
    PESTANA("Pestaña", "CarPlay completo ocupa TODA la pestaña Auto; arriba (o al lado) quedan tus pestañas de FT150TS para deslizar. Al cambiar de pestaña se esconde sin cortar la sesión. Necesita root (sin root abre CarPlay en pantalla completa)."),
    VENTANA("Ventana", "CarPlay completo (toque, música, Siri) en una ventana flotante justo encima del mapa. Necesita las ventanas flotantes de Android."),
    DIVIDIDA("Dividida", "CarPlay completo en la mitad de la pantalla y FT150TS en la otra. En DiPlay prende Display and performance → \"CarPlay in the head unit's split screen\" para que no quede con barras negras."),
    COMPLETA("Total", "Abre CarPlay en pantalla completa en DiPlay; regresas con Atrás o Recientes."),
    MAPA("Mapa", "Solo el mapa de tablero de CarPlay dentro de la página (experimental en DiPlay: el iPhone puede no mandarlo)."),
}

/** Modo Pestaña: cuánto espacio ocupa CarPlay. */
enum class TamanoCarPlay(val nombre: String) {
    MAXIMO("Máximo"),      // de orilla a orilla bajo las pestañas
    AMPLIO("Amplio"),      // el área de las páginas
    AJUSTADO("Ajustado"),  // dentro del marco, con los márgenes de las tarjetas
}

/** Estado real de CarPlay en el mapa. Solo cambia con lo que responde DiPlay (o con Android). */
sealed class EstadoCarPlay {
    /** DiPlay instalado y de fiar, en un modo de ventana (Ventana / Dividida / Completa). */
    data object Listo : EstadoCarPlay()
    /** DiPlay no está instalado. */
    data object SinDiPlay : EstadoCarPlay()
    /** Hay una app "DiPlay" pero no está firmada por DiPlay ni la marcaste como confiable: no se le habla. */
    data object FirmaDesconocida : EstadoCarPlay()
    /** Android 10 o menos: no se puede meter el mapa de otra app; queda el botón para abrir CarPlay. */
    data object SinSoporte : EstadoCarPlay()
    /** Su DiPlay es viejo y no trae el servicio del mapa. */
    data object SinServicio : EstadoCarPlay()
    /** Android no dejó conectar con DiPlay o no contestó a tiempo (en MIUI/HyperOS: "Inicio automático" apagado). */
    data object NoConecta : EstadoCarPlay()
    /** Conectando con DiPlay o pidiendo el mapa. */
    data object Conectando : EstadoCarPlay()
    /** DiPlay entregó la vista pero no hay sesión de CarPlay con mapa (iPhone sin conectar o mapa del tablero apagado). */
    data object Esperando : EstadoCarPlay()
    /** El iPhone está mandando el mapa: se ve CarPlay. */
    data object EnVivo : EstadoCarPlay()
    /** En DiPlay está apagado "Share the live map with other launchers". */
    data object Desactivado : EstadoCarPlay()
    /** DiPlay se cerró o se reinició; Android lo vuelve a conectar solo. */
    data object Perdido : EstadoCarPlay()
    /** Otro error que mandó DiPlay. */
    data class Falla(val codigo: String) : EstadoCarPlay()
}

/** Lo que pasa con DiPlay (para [ProtocoloCarPlay.reducir]). */
sealed class EventoCarPlay {
    data object Vinculando : EventoCarPlay()
    data object NoSeVinculo : EventoCarPlay()
    /** Se pidió la conexión o el mapa y DiPlay no contestó en el tiempo límite. */
    data object SinRespuesta : EventoCarPlay()
    data object Vinculado : EventoCarPlay()
    data object Desvinculado : EventoCarPlay()
    data class Entregado(val activo: Boolean) : EventoCarPlay()
    data class Flujo(val activo: Boolean) : EventoCarPlay()
    data class Error(val codigo: String?) : EventoCarPlay()
}

/** La parte pura del protocolo (sin Android): se prueba en LogicaCarPlayTest. */
object ProtocoloCarPlay {
    const val ERROR_DESACTIVADO = "disabled"
    const val ERROR_SIN_SOPORTE = "unsupported"
    const val ERROR_PEDIDO = "bad_request"
    /** Tope de los pixeles que se le piden a DiPlay (un lado). Más que eso es un error de medida, no una pantalla. */
    const val LADO_MAX = 8192

    /** Espera antes del reintento número [intento] (0, 1, 2...): 5 s, 10 s, 20 s y luego cada 30 s. */
    fun espera(intento: Int): Long = (5_000L shl intento.coerceIn(0, 3)).coerceAtMost(30_000L)

    fun reducir(estado: EstadoCarPlay, evento: EventoCarPlay): EstadoCarPlay = when (evento) {
        EventoCarPlay.Vinculando -> EstadoCarPlay.Conectando
        // Si el servicio existe pero Android no deja vincular, es el sistema (no "DiPlay viejo")
        EventoCarPlay.NoSeVinculo, EventoCarPlay.SinRespuesta -> EstadoCarPlay.NoConecta
        // Vinculado: falta pedir el mapa; se queda "conectando" hasta que DiPlay conteste
        EventoCarPlay.Vinculado -> EstadoCarPlay.Conectando
        EventoCarPlay.Desvinculado -> EstadoCarPlay.Perdido
        is EventoCarPlay.Entregado -> if (evento.activo) EstadoCarPlay.EnVivo else EstadoCarPlay.Esperando
        // Un cambio de flujo solo cuenta si ya hay mapa entregado (si no, es un mensaje viejo)
        is EventoCarPlay.Flujo -> when (estado) {
            EstadoCarPlay.EnVivo, EstadoCarPlay.Esperando -> if (evento.activo) EstadoCarPlay.EnVivo else EstadoCarPlay.Esperando
            else -> estado
        }
        is EventoCarPlay.Error -> when (evento.codigo) {
            ERROR_DESACTIVADO -> EstadoCarPlay.Desactivado
            ERROR_SIN_SOPORTE -> EstadoCarPlay.SinSoporte
            else -> EstadoCarPlay.Falla(limpiar(evento.codigo ?: "?"))
        }
    }

    /** ¿Se ve el mapa de CarPlay? (si no, se ve el de OpenStreetMap) */
    fun mostrarCarPlay(e: EstadoCarPlay) = e == EstadoCarPlay.EnVivo

    /** ¿Vale la pena volver a pedir el mapa sola cada rato? (por si prendes la opción en DiPlay o lo reinicias) */
    fun reintentar(e: EstadoCarPlay) = e == EstadoCarPlay.Desactivado || e == EstadoCarPlay.Perdido ||
        e == EstadoCarPlay.NoConecta || e is EstadoCarPlay.Falla

    /** Medida que se le manda a DiPlay: nunca 0 ni negativa, ni absurda. */
    fun medida(px: Int): Int = px.coerceIn(1, LADO_MAX)

    fun firmaConfiable(firmas: Set<String>, oficiales: Set<String>, propias: Set<String>): Boolean =
        firmas.isNotEmpty() && firmas.any { it in oficiales || it in propias }

    fun esSha256(s: String) = s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' }

    /** Paquete que se puede poner en un comando (solo los de DiPlay). */
    fun paqueteSeguro(p: String) = p in CarPlay.PAQUETES

    // ---- Ventana a la medida (root) ----

    /** Lista las tareas y deja la de DiPlay ("Task{… #601 type=standard A=10286:com.shihab.diplay.carplay"). */
    fun cmdBuscarTarea(paquete: String): String {
        require(paqueteSeguro(paquete))
        return "dumpsys activity activities | grep -m1 -o 'Task{[0-9a-f]* #[0-9]* type=standard A=[0-9]*:$paquete'"
    }

    /** Número de tarea de esa línea (solo dígitos, tope razonable). */
    fun leerTarea(salida: String): Int? =
        Regex("#(\\d{1,7}) type=standard").find(salida)?.groupValues?.get(1)?.toIntOrNull()

    /** [b] = izquierda, arriba, derecha, abajo (pixeles). */
    fun cmdRedimensionar(tarea: Int, b: IntArray): String {
        require(tarea in 0..9_999_999 && b.size == 4 && b[0] >= 0 && b[1] >= 0 && b[2] > b[0] && b[3] > b[1] && b[2] <= 20000 && b[3] <= 20000)
        return "am task resize $tarea ${b[0]} ${b[1]} ${b[2]} ${b[3]}"
    }

    /** La escala con que el compositor dibuja la ventana de CarPlay (HyperOS: ~0.61). */
    /**
     * HyperOS 3 / Android 16 lo escribe en una línea ("scale x=0.6102"); HyperOS 1 / Android 14 escribe la matriz
     * debajo del nombre de la capa ("    0.6102  0.0000  0.0000"). Se piden las dos formas y se toma la primera.
     */
    fun cmdLeerEscala() = "dumpsys SurfaceFlinger | grep -A4 'CarPlayHostActivity' | " +
        "grep -m1 -oE 'scale x=[0-9.]+|^ +[0-9]\\.[0-9]{4} +0\\.0000 +-?[0-9.]+\$'"

    fun leerEscala(salida: String): Float? {
        val v = Regex("scale x=([0-9.]+)").find(salida)?.groupValues?.get(1)
            ?: Regex("(?m)^\\s*([0-9]\\.[0-9]{4})\\s+0\\.0000\\s+-?[0-9.]+\\s*$").find(salida)?.groupValues?.get(1)
        return v?.toFloatOrNull()?.takeIf { it in 0.2f..1.5f }
    }

    /**
     * Límites que, dibujados a [escala], se ven del tamaño de [r]. Anclados arriba-izquierda en [r] (así los dibuja
     * HyperOS) y recortados a la pantalla [anchoPantalla]×[altoPantalla]: si no cabe, se ve lo más grande posible.
     */
    /**
     * Modo Pestaña: límites que, dibujados a [escala] desde su esquina, se ven exactamente sobre [a]. Pueden pasar de
     * la pantalla (HyperOS lo permite y la escala la regresa a su lugar).
     */
    fun aLaMedida(a: IntArray, escala: Float): IntArray {
        val e = escala.coerceIn(0.2f, 1.5f)
        return intArrayOf(a[0], a[1], a[0] + ((a[2] - a[0]) / e).toInt(), a[1] + ((a[3] - a[1]) / e).toInt())
    }

    /** ¿El punto ([x], [y]) está a [dist] px o menos de alguna esquina del rectángulo [r] (izq, arriba, der, abajo)? */
    fun cercaDeEsquina(x: Int, y: Int, r: IntArray, dist: Int): Boolean {
        if (r.size < 4) return false
        val cerca = { a: Int, b: Int -> kotlin.math.abs(a - b) <= dist }
        return (cerca(x, r[0]) || cerca(x, r[2])) && (cerca(y, r[1]) || cerca(y, r[3]))
    }

    /** Lee el modo y los límites de la tarea [tarea] ("mode=freeform", "mBounds=Rect(0, 219 - 3933, 1630)"). */
    fun cmdLeerVentana(tarea: Int): String {
        require(tarea in 0..9_999_999)
        // grep -E: el grep de HyperOS 1 (toybox viejo) no entiende "\|" en modo básico
        // La tarea sale dos veces en el reporte (HyperOS 1): se toman hasta 4 coincidencias, no 2
        return "dumpsys activity activities | grep -A2 'Task{[0-9a-f]* #$tarea type' | grep -oE 'mode=[a-z]+|mBounds=Rect\\([^)]*\\)' | head -4"
    }

    /** Límites de la ventana si está flotante (izq, arriba, der, abajo); null si no es flotante o no se pudo leer. */
    fun leerVentana(salida: String): IntArray? {
        if (!salida.contains("mode=freeform")) return null
        val m = Regex("mBounds=Rect\\((-?\\d+), (-?\\d+) - (-?\\d+), (-?\\d+)\\)").find(salida) ?: return null
        return m.groupValues.drop(1).map { it.toInt() }.toIntArray()
    }

    /** ¿La ventana está flotante y con los límites [esperado] (±3 px)? */
    fun ventanaEnSuLugar(salida: String, esperado: IntArray): Boolean {
        if (!salida.contains("mode=freeform")) return false
        val m = Regex("mBounds=Rect\\((-?\\d+), (-?\\d+) - (-?\\d+), (-?\\d+)\\)").find(salida) ?: return false
        val v = m.groupValues.drop(1).map { it.toInt() }
        return v.indices.all { kotlin.math.abs(v[it] - esperado[it]) <= 3 }
    }

    /**
     * Los mismos límites corridos a la derecha de la pantalla (mismo tamaño: DiPlay no cambia de lienzo).
     * Con [escala] y [altoPantalla], además se sube lo necesario para que lo que se DIBUJA no pase de la orilla de
     * abajo: HyperOS mete de vuelta a la pantalla las ventanas flotantes que se salen por abajo cuando cambia la zona
     * del gesto (deslizar desde abajo), y así la ventana escondida de CarPlay aparecía encima del tablero.
     */
    fun fueraDePantalla(b: IntArray, anchoPantalla: Int, escala: Float = 0f, altoPantalla: Int = 0): IntArray {
        val w = b[2] - b[0]
        val h = b[3] - b[1]
        var arriba = b[1]
        if (escala > 0f && altoPantalla > 0) {
            val dibujado = kotlin.math.ceil(h * escala).toInt()
            arriba = arriba.coerceAtMost(altoPantalla - dibujado - MARGEN_ABAJO_PX).coerceAtLeast(0)
        }
        return intArrayOf(anchoPantalla, arriba, anchoPantalla + w, arriba + h)
    }

    private const val MARGEN_ABAJO_PX = 8

    fun compensar(b: IntArray, escala: Float, anchoPantalla: Int, altoPantalla: Int): IntArray {
        val w = ((b[2] - b[0]) / escala).toInt()
        val h = ((b[3] - b[1]) / escala).toInt()
        return intArrayOf(b[0], b[1], (b[0] + w).coerceAtMost(anchoPantalla), (b[1] + h).coerceAtMost(altoPantalla))
    }

    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    /** Texto que manda la otra app: corto y sin caracteres raros antes de mostrarlo. */
    fun limpiar(s: String): String = s.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(24).ifEmpty { "?" }

    /** Texto corto para la etiqueta del mapa. */
    fun texto(e: EstadoCarPlay): String = when (e) {
        EstadoCarPlay.Listo -> "diplay listo"
        EstadoCarPlay.SinDiPlay -> "instala diplay"
        EstadoCarPlay.FirmaDesconocida -> "diplay con firma desconocida"
        EstadoCarPlay.SinSoporte -> "android 11+ para incrustar"
        EstadoCarPlay.SinServicio -> "actualiza diplay"
        EstadoCarPlay.NoConecta -> "diplay no contesta · reintentando"
        EstadoCarPlay.Conectando -> "conectando con diplay..."
        EstadoCarPlay.Esperando -> "carplay sin mapa"
        EstadoCarPlay.EnVivo -> "carplay en vivo"
        EstadoCarPlay.Desactivado -> "activa compartir mapa en diplay"
        EstadoCarPlay.Perdido -> "diplay se cerro · reconectando"
        is EstadoCarPlay.Falla -> "error de diplay: ${e.codigo}"
    }

    /** Qué hacer, en una frase (en el detalle). */
    fun ayuda(e: EstadoCarPlay): String = when (e) {
        EstadoCarPlay.Listo -> "Toca CarPlay para abrir la pantalla de CarPlay de DiPlay en el modo elegido. Si el iPhone " +
            "no está conectado, DiPlay muestra su pantalla de espera."
        EstadoCarPlay.SinDiPlay -> "DiPlay es el receptor de CarPlay. Instálalo desde sus releases de GitHub."
        EstadoCarPlay.FirmaDesconocida -> "La app instalada como DiPlay no tiene la firma de los releases oficiales. " +
            "Si la compilaste tú, márcala como confiable; si no, desinstálala."
        EstadoCarPlay.SinSoporte -> "Este Android no deja meter el mapa de otra app. Usa \"Abrir CarPlay\" (pantalla completa)."
        EstadoCarPlay.SinServicio -> "Tu DiPlay no trae el servicio del mapa (llegó en la 0.2.9). Actualízalo."
        EstadoCarPlay.NoConecta -> "Android no dejó conectar con DiPlay. Abre DiPlay una vez; en POCO/Xiaomi, en Ajustes > " +
            "Apps > DiPlay prende \"Inicio automático\" y en Ahorro de batería elige \"Sin restricciones\"."
        EstadoCarPlay.Conectando -> "Esperando la respuesta de DiPlay."
        EstadoCarPlay.Esperando -> "DiPlay está listo pero el iPhone no manda el mapa. Revisa en DiPlay: Advanced > " +
            "\"CarPlay map on dashboard (experimental)\" prendido, el iPhone conectado y la pantalla de CarPlay " +
            "abierta al menos una vez (no la cierres desde Recientes)."
        EstadoCarPlay.EnVivo -> "El mapa de CarPlay llega del iPhone. Tócalo para abrir CarPlay completo."
        EstadoCarPlay.Desactivado -> "En DiPlay: Advanced > \"Share the live map with other launchers\". Se vuelve a pedir solo."
        EstadoCarPlay.Perdido -> "DiPlay se cerró. Se vuelve a pedir el mapa solo."
        is EstadoCarPlay.Falla -> "DiPlay rechazó el pedido. Se vuelve a intentar solo."
    }
}
