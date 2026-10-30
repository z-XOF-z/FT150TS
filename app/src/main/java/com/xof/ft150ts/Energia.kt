package com.xof.ft150ts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Páginas a las que se puede saltar desde fuera de la pantalla (modo moto, botón Inicio). */
const val PAGINA_APPS = 0
const val PAGINA_TABLERO = 1
const val PAGINA_MOTO = 3

/**
 * Modo moto: sabe si el teléfono está montado en la moto.
 *
 * El cable de la moto carga el teléfono solo con el switch prendido. Entonces:
 * carga + (opcional) moto conectada por Bluetooth = está en la moto → tablero.
 * Deja de cargar (apagaste el switch o lo quitaste del soporte) → tus apps.
 *
 * Al dar marcha el voltaje baja y la carga se corta un momento: por eso
 * un corte de menos de 8 segundos no cuenta.
 */
private data class SenalMoto(val detectar: Boolean, val listo: Boolean, val carga: Boolean, val dormido: Boolean)

object Energia {
    /** El teléfono está conectado a la corriente. */
    val cargando = MutableStateFlow(false)
    /**
     * Conectado a una computadora: el USB está "configurado" (una computadora lo reconoció como dispositivo). Carga,
     * pero NO es la moto: antes conectarlo a la PC prendía el modo moto (tablero, saludo, zona WiFi...). Un cargador
     * (el de la moto o uno de pared) nunca configura el USB.
     */
    val enComputadora = MutableStateFlow(false)
    /** Carga que cuenta como "switch de la moto prendido": cualquier cargador, menos una computadora. */
    fun cargaDeMoto() = cargando.value && !enComputadora.value

    /** Resultado: el teléfono está en la moto (con el switch prendido). */
    val enMoto = MutableStateFlow(false)
    /**
     * Se cortó la carga pero vas rodando: no apagaste la moto, se aflojó el cable (o falló el cargador). El tablero se
     * queda y avisa; sale hasta que te detienes.
     */
    val cableSuelto = MutableStateFlow(false)

    // Opciones (Ajustes > Modo moto)
    val detectarCarga = MutableStateFlow(true)    // usar la carga para saber si está en la moto
    val requiereBle = MutableStateFlow(false)     // además, que la moto esté conectada por Bluetooth
    val alQuitarApps = MutableStateFlow(true)     // al quitarlo, mostrar tus apps
    val abrirAlMontar = MutableStateFlow(true)    // al montarlo, traer el tablero al frente
    val segundoPlano = MutableStateFlow(true)     // mantener la app viva (servicio con notificación)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private var prefs: SharedPreferences? = null
    private var appCtx: Context? = null
    private var iniciado = false

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val antes = cargando.value
            if (i?.action == Intent.ACTION_BATTERY_CHANGED) {
                Termico.bateria(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0))
                BateriaTel.desdeIntent(i)
            }
            cargando.value = when (i?.action) {
                Intent.ACTION_POWER_CONNECTED -> true
                Intent.ACTION_POWER_DISCONNECTED -> false
                else -> enchufado(i)
            }
            // Recién conectado el cable: busca la moto ya, sin esperar el siguiente intento
            if (cargando.value && !antes) BleManager.buscarYa()
        }
    }

    private val receptorUsb = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            enComputadora.value = i?.getBooleanExtra("configured", false) == true
        }
    }

    private fun enchufado(i: Intent?): Boolean = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    fun iniciar(ctx: Context) {
        if (iniciado) return
        iniciado = true
        val app = ctx.applicationContext
        appCtx = app
        val p = app.getSharedPreferences("modoMoto", Context.MODE_PRIVATE)
        prefs = p
        detectarCarga.value = p.getBoolean("detectarCarga", true)
        requiereBle.value = p.getBoolean("requiereBle", false)
        alQuitarApps.value = p.getBoolean("alQuitarApps", true)
        abrirAlMontar.value = p.getBoolean("abrirAlMontar", true)
        segundoPlano.value = p.getBoolean("segundoPlano", true)

        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        try {
            // ACTION_BATTERY_CHANGED es "pegajoso": al registrarse regresa el estado actual
            val actual = ContextCompat.registerReceiver(app, receptor, filtro, ContextCompat.RECEIVER_NOT_EXPORTED)
            cargando.value = enchufado(actual)
            actual?.let { Termico.bateria(it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)); BateriaTel.desdeIntent(it) }
        } catch (_: Exception) { }
        try {
            // Aviso del sistema "pegajoso" (al registrarse regresa el estado actual del USB)
            val usb = ContextCompat.registerReceiver(app, receptorUsb, IntentFilter(USB_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
            enComputadora.value = usb?.getBooleanExtra("configured", false) == true
        } catch (_: Exception) { }
        vigilar()
    }

    private fun vigilar() {
        scope.launch {
            val nfcYSueno = combine(NfcMoto.presente, ModoSueno.dormido) { nfc, dormido -> nfc to dormido }
            // La carga de una computadora no cuenta (al conectarla, el USB se configura ~1 s después de que empieza a
            // cargar: cabe en la espera de 2.5 s de abajo, así que no alcanza a prender el modo moto)
            val cargaMoto = combine(cargando, enComputadora) { c, pc -> c && !pc }
            combine(cargaMoto, Moto.conexion, detectarCarga, requiereBle, nfcYSueno) { carga, con, detectar, ble, (nfc, dormido) ->
                val motoBle = con == Conexion.CONECTADO || con == Conexion.DEMO
                // La etiqueta NFC del soporte cuenta sola (aunque no esté cargando). Dormida (modo sueño) no cuenta nada.
                SenalMoto(detectar || nfc, !dormido && ((detectar && carga && (!ble || motoBle)) || nfc), carga && detectar, dormido)
            }.distinctUntilChanged().collectLatest { s ->
                android.util.Log.i("FT150-EN", "señal $s · enMoto=${enMoto.value}")
                when {
                    !s.detectar -> ponerEnMoto(false)
                    s.listo -> {
                        delay(2_500)                         // que la carga se estabilice
                        ponerEnMoto(true)
                    }
                    enMoto.value -> {
                        // Corte corto al dar marcha: no cuenta. Si sigue cargando y solo se perdió
                        // el Bluetooth (la tarjeta se reinició), espera más. Al dormirse, de inmediato.
                        delay(if (s.dormido) 0 else if (s.carga) 20_000 else 8_000)
                        // Sin carga pero rodando: el tablero NO se va a tus apps a media calle
                        if (!s.dormido && !s.carga) esperarAQueTeDetengas()
                        ponerEnMoto(false, apagoSwitch = !s.dormido && !s.carga)
                    }
                }
            }
        }
    }

    /**
     * La carga se cortó: si vas rodando (GPS > [VEL_RODANDO] km/h) no fue el switch. Avisa una vez y espera a que
     * estés detenido 20 s seguidos (o a que vuelva la carga: entonces esto se cancela solo).
     */
    private suspend fun esperarAQueTeDetengas() {
        if (UbicacionManager.velocidadKmh() < VEL_RODANDO) return
        cableSuelto.value = true
        Voz.decir("Ojo: el teléfono dejó de cargar. Revisa el cable.")
        try {
            var quieto = 0
            while (quieto < 20) {
                delay(1_000)
                quieto = if (UbicacionManager.velocidadKmh() < 3f) quieto + 1 else 0
            }
        } finally {
            cableSuelto.value = false
        }
    }

    private const val VEL_RODANDO = 8f
    private const val USB_STATE = "android.hardware.usb.action.USB_STATE"

    private fun ponerEnMoto(v: Boolean, apagoSwitch: Boolean = false) {
        if (enMoto.value == v) return
        android.util.Log.i("FT150-EN", "modo moto: $v (apagó switch: $apagoSwitch)")
        enMoto.value = v
        BleManager.ajustarPrioridad()
        if (v) Encendido.alPrender() else if (apagoSwitch) Encendido.alApagar()
        val ctx = appCtx ?: return
        if (v) {
            // Abriste el switch con la pantalla apagada (o en otra app): al verla, sale la animación FT150
            if (!Lanzador.enPantalla.value) Lanzador.inicioPendiente.value = true
            Lanzador.pedirArranque()                 // la animación del tablero sale solo al abrir el switch
            Lanzador.irA(PAGINA_TABLERO)
            if (abrirAlMontar.value && !BleManager.primerPlano) traerAlFrente(ctx)
        } else if (alQuitarApps.value && !ModoSueno.dormido.value) {
            // Al dormirse no cambia de página (al despertar sigues donde estabas)
            Lanzador.irA(PAGINA_APPS)
        }
    }

    /**
     * Abre la app aunque esté en segundo plano. Android solo lo permite con
     * "Mostrar sobre otras apps" o con root (que además prende la pantalla).
     */
    fun traerAlFrente(ctx: Context) {
        if (Root.activo.value && Root.disponible.value == true) {
            Root.despertarYAbrir(ctx)
            return
        }
        if (!puedeAbrirseSola(ctx)) return
        try {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (_: Exception) { }
    }

    /** ¿Tiene el permiso "Mostrar sobre otras apps"? (deja abrirse sola desde segundo plano) */
    fun puedeAbrirseSola(ctx: Context): Boolean = try { Settings.canDrawOverlays(ctx) } catch (_: Exception) { false }

    fun pedirAbrirseSola(ctx: Context) {
        abrir(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
    }

    /** ¿Android ya no la duerme por ahorro de batería? */
    fun sinAhorroBateria(ctx: Context): Boolean = try {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (_: Exception) { false }

    @android.annotation.SuppressLint("BatteryLife")
    fun pedirSinAhorroBateria(ctx: Context) {
        val ok = abrir(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
        if (!ok) abrir(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun abrir(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }

    private fun guardar(clave: String, v: Boolean) { prefs?.edit()?.putBoolean(clave, v)?.apply() }

    fun ponerDetectarCarga(on: Boolean) { detectarCarga.value = on; guardar("detectarCarga", on) }
    fun ponerRequiereBle(on: Boolean) { requiereBle.value = on; guardar("requiereBle", on) }
    fun ponerAlQuitarApps(on: Boolean) { alQuitarApps.value = on; guardar("alQuitarApps", on) }
    fun ponerAbrirAlMontar(on: Boolean) { abrirAlMontar.value = on; guardar("abrirAlMontar", on) }

    fun ponerSegundoPlano(ctx: Context, on: Boolean) {
        segundoPlano.value = on
        guardar("segundoPlano", on)
        if (on) ServicioMoto.arrancar(ctx) else ServicioMoto.detener(ctx)
    }
}
