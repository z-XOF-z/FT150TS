package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class Conexion { APAGADO, SIN_PERMISO, BT_APAGADO, BUSCANDO, CONECTANDO, CONECTADO, DEMO }

/**
 * Estado central de la app. Las pantallas solo leen de aquí.
 * Los datos llegan del ESP32 (BleManager) o del modo demo.
 */
object Moto {
    // Datos de la moto
    val rapida = MutableStateFlow(Rapida())
    val estado = MutableStateFlow(Estado())
    val viaje = MutableStateFlow(ViajeInfo())
    val mant = MutableStateFlow<List<MantItem>>(emptyList())
    /** Ajustes e info de la tarjeta (firmware v1.3+; null = no ha llegado o firmware viejo). */
    val config = MutableStateFlow<ConfigMoto?>(null)
    /** Última respuesta de la moto a un comando de los botones (calibraciones, etc.). */
    val ultimaRespuesta = MutableStateFlow("")
    val conexion = MutableStateFlow(Conexion.APAGADO)
    val consola = MutableStateFlow("Escribe 'ayuda' para ver los comandos.\n")
    val ultimaNotif = MutableStateFlow("")
    val datosMalos = MutableStateFlow(false)   // paquetes cortados (MTU bajo)

    // iPhone vinculado a la moto (notificaciones y música por ANCS/AMS)
    val iphoneMusica = MutableStateFlow(IphoneMusica())
    val iphoneLlamada = MutableStateFlow(IphoneLlamada())

    // Historial para las gráficas de Estadísticas (una muestra por segundo, 2 minutos)
    val historialVel = MutableStateFlow<List<Float>>(emptyList())
    val historialRpm = MutableStateFlow<List<Float>>(emptyList())

    // Ajustes de la app (se guardan en el teléfono)
    val demo = MutableStateFlow(false)
    val reenviarNotif = MutableStateFlow(true)
    val enviarGps = MutableStateFlow(true)
    val estiloMapa = MutableStateFlow(EstiloMapa.NOCHE)
    val nocheAuto = MutableStateFlow(true)      // de noche cambia solo al estilo Noche
    val mostrarLimite = MutableStateFlow(true)  // señal con el límite de la calle en el mapa
    val modoAvisoVel = MutableStateFlow(0)      // 0 = límite fijo, 1 = según la calle
    val micBt = MutableStateFlow(false)         // usar el micrófono del casco/audífonos para la voz
    val rumboArriba = MutableStateFlow(false)
    val avisoVel = MutableStateFlow(false)      // aviso de velocidad encendido
    val limiteVel = MutableStateFlow(80)        // km/h
    val avisosVoz = MutableStateFlow(false)     // el teléfono dice los avisos en voz alta

    /** true mientras vas más rápido que el límite (el tablero parpadea en rojo). */
    val excesoVel = MutableStateFlow(false)
    /** Límite con el que se está comparando ahora (fijo o el de la calle). */
    val limiteActivo = MutableStateFlow(80)

    private lateinit var prefs: SharedPreferences
    private var appCtx: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var demoJob: Job? = null
    private var iniciado = false
    private var errores = 0
    private val pendientes = ArrayDeque<Boolean>()   // ¿mostrar la respuesta en la consola?

    fun iniciar(ctx: Context) {
        if (iniciado) return
        iniciado = true
        val app = ctx.applicationContext
        appCtx = app
        Apariencia.cargar(app)   // tema, acento, tablero... antes de dibujar nada
        prefs = app.getSharedPreferences("ft150ts", Context.MODE_PRIVATE)
        demo.value = prefs.getBoolean("demo", false)
        reenviarNotif.value = prefs.getBoolean("reenviarNotif", true)
        enviarGps.value = prefs.getBoolean("enviarGps", true)
        // Antes había un interruptor "mapa oscuro": se respeta lo que tenías
        val estiloGuardado = prefs.getString("estiloMapa", null)
        estiloMapa.value = EstiloMapa.entries.firstOrNull { it.name == estiloGuardado }
            ?: if (prefs.getBoolean("mapaOscuro", true)) EstiloMapa.NOCHE else EstiloMapa.ESTANDAR
        nocheAuto.value = prefs.getBoolean("nocheAuto", true)
        mostrarLimite.value = prefs.getBoolean("mostrarLimite", true)
        modoAvisoVel.value = prefs.getInt("modoAvisoVel", 0)
        micBt.value = prefs.getBoolean("micBt", false)
        rumboArriba.value = prefs.getBoolean("rumboArriba", false)
        avisoVel.value = prefs.getBoolean("avisoVel", false)
        limiteVel.value = prefs.getInt("limiteVel", 80)
        avisosVoz.value = prefs.getBoolean("avisosVoz", false)

        BleManager.iniciar(app)
        UbicacionManager.iniciar(app)
        MusicaManager.iniciar(app)
        Voz.iniciar(app)
        Estacionamiento.iniciar(app)
        Estadisticas.iniciar(app)
        ClimaManager.iniciar(app)
        AlertasManager.iniciar(app)
        AudioSalida.iniciar(app)
        Acciones.iniciar(app)
        Lanzador.iniciar(app)
        Mapas.iniciar(app)
        CarPlay.iniciar(app)
        Root.iniciar(app)
        Termico.iniciar(app)
        BateriaTel.iniciar(app)
        Luz.iniciar(app)
        Brujula.iniciar(app)
        NfcMoto.iniciar(app)
        Encendido.iniciar(app)
        ZonaWifi.iniciar(app)
        Rutinas.iniciar(app)
        BluetoothAdmin.iniciar(app)
        VolumenAuto.iniciar(app)
        Sonoridad.iniciar(app)
        GestosCarPlay.iniciar(app)
        Energia.iniciar(app)
        ModoSueno.iniciar(app)          // después de Energia: necesita saber si está cargando
        Reposo.iniciar(app)             // el paso antes del modo sueño (parada y cargando unos minutos)
        ModoRendimiento.iniciar(app)    // después de Root, Termico y Reposo: los usa para decidir
        Nocturno.iniciar(app)           // después del modo sueño: lo usa para dormir la app de noche
        Emergencia.iniciar(app)
        Baches.iniciar(app)
        TelefonoMoto.iniciar(app)
        OdometroTel.iniciar(app)
        Gasolina.iniciar(app)
        Dashcam.iniciar(app)
        Rodadas.iniciar(app)
        Arrancones.iniciar(app)
        Velocimetro.iniciar(app)
        LluviaRuta.iniciar(app)
        Respaldo.iniciar(app)
        Logros.iniciar(app)
        LimitesManager.iniciar()
        if (demo.value) iniciarDemo() else BleManager.conectar()
        iniciarVigilancia()
        vigilarConexion()
    }

    /** 30 minutos a una muestra por segundo (las gráficas eligen 2, 10 o 30 min). */
    const val MUESTRAS = 1800

    /**
     * Cada medio segundo revisa el aviso de velocidad y cada segundo guarda
     * velocidad y RPM para las gráficas (sin datos de la moto usa el GPS del teléfono).
     */
    private fun iniciarVigilancia() {
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                // Con la tarjeta, o con el teléfono haciendo de sensor (RPM por vibración)
                val conectada = conexion.value == Conexion.CONECTADO || conexion.value == Conexion.DEMO ||
                    TelefonoMoto.trabajando.value
                val r = rapida.value
                val vel = if (conectada && r.vel >= 0) r.vel else UbicacionManager.velocidadKmh()
                // El aviso usa la velocidad de la moto o una del GPS que sea creíble (no la de rebote bajo techo)
                revisarVelocidad(if (conectada && r.vel >= 0) r.vel else UbicacionManager.velocidadConfiableKmh())
                tick++
                if (tick % 2 != 0) continue
                if (vel < 0 && !conectada) continue   // nada que guardar
                historialVel.value = (historialVel.value + max(0f, vel)).takeLast(MUESTRAS)
                historialRpm.value = (historialRpm.value + if (conectada) max(0, r.rpm).toFloat() else 0f).takeLast(MUESTRAS)
            }
        }
    }

    private var seguidasSobreLimite = 0

    /** Entra al pasar el límite y sale al bajar 3 km/h de él (así no parpadea de más). */
    private fun revisarVelocidad(vel: Float) {
        // "Según la calle": límite de la calle + 5 km/h de margen; si no se sabe, el fijo
        val calle = LimitesManager.limite.value
        val limite = if (modoAvisoVel.value == 1 && calle != null) calle.kmh + 5 else limiteVel.value
        limiteActivo.value = limite
        val antes = excesoVel.value
        val pasado = avisoVel.value && vel >= 0f && (if (antes) vel > limite - 3 else vel > limite)
        // Tiene que sostenerse 2 s: un pico suelto (bache del GPS) no cuenta
        seguidasSobreLimite = if (pasado) seguidasSobreLimite + 1 else 0
        val ahora = if (antes) pasado else seguidasSobreLimite >= SeguridadVel.MUESTRAS
        if (ahora && !antes) {
            vibrar()
            if (avisosVoz.value) Voz.decir("Cuidado, vas a más de $limite")
        }
        excesoVel.value = ahora
    }

    @Suppress("DEPRECATION")
    private fun vibrar() {
        try {
            val v = appCtx?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
        } catch (_: Exception) { }
    }

    /** Al apagar la moto (se desconecta estando parada) guarda dónde quedó. */
    private fun vigilarConexion() {
        scope.launch {
            var anterior = conexion.value
            conexion.collect { nueva ->
                // Al prender la moto: despierta el teléfono con el tablero (opción root)
                if (nueva == Conexion.CONECTADO && anterior != Conexion.CONECTADO && Root.activo.value && Root.despertar.value && !BleManager.primerPlano) {
                    appCtx?.let { Root.despertarYAbrir(it) }
                }
                // Conectada en segundo plano: prende el GPS para prestárselo (lo permite el servicio "App activa")
                if (nueva == Conexion.CONECTADO && enviarGps.value) UbicacionManager.arrancar()
                if (nueva != Conexion.CONECTADO) {          // sin moto no hay iPhone (llega a través de ella)
                    iphoneMusica.value = IphoneMusica()
                    iphoneLlamada.value = IphoneLlamada()
                }
                if (anterior == Conexion.CONECTADO && nueva != Conexion.CONECTADO && !demo.value) {
                    val vel = UbicacionManager.velocidadKmh()
                    if (vel < 8f) Estacionamiento.guardarAqui()
                }
                anterior = nueva
            }
        }
    }

    /** Aviso de la app (lluvia, etc.): se ve en el tablero, va al LCD y, si quieres, se dice en voz alta. */
    fun aviso(textoPantalla: String, textoVoz: String = textoPantalla) {
        ultimaNotif.value = textoPantalla
        if (!demo.value && reenviarNotif.value) BleManager.enviarNotif(textoPantalla)
        if (avisosVoz.value) Voz.decir(textoVoz)
    }

    fun borrarHistorial() {
        historialVel.value = emptyList()
        historialRpm.value = emptyList()
    }

    private fun guardar(clave: String, valor: Boolean) {
        if (::prefs.isInitialized) prefs.edit().putBoolean(clave, valor).apply()
    }

    fun ponerDemo(on: Boolean) {
        demo.value = on
        guardar("demo", on)
        if (on) {
            BleManager.desconectar()
            iniciarDemo()
        } else {
            demoJob?.cancel()
            rapida.value = Rapida(); estado.value = Estado(); viaje.value = ViajeInfo(); mant.value = emptyList()
            BleManager.conectar()
        }
    }

    fun ponerReenviarNotif(on: Boolean) { reenviarNotif.value = on; guardar("reenviarNotif", on) }
    fun ponerEnviarGps(on: Boolean) { enviarGps.value = on; guardar("enviarGps", on) }
    fun ponerEstiloMapa(e: EstiloMapa) {
        estiloMapa.value = e
        if (::prefs.isInitialized) prefs.edit().putString("estiloMapa", e.name).apply()
    }
    fun ponerNocheAuto(on: Boolean) { nocheAuto.value = on; guardar("nocheAuto", on) }
    fun ponerMostrarLimite(on: Boolean) { mostrarLimite.value = on; guardar("mostrarLimite", on) }
    fun ponerMicBt(on: Boolean) { micBt.value = on; guardar("micBt", on) }
    fun ponerModoAvisoVel(m: Int) {
        modoAvisoVel.value = m
        if (::prefs.isInitialized) prefs.edit().putInt("modoAvisoVel", m).apply()
    }
    fun ponerRumboArriba(on: Boolean) { rumboArriba.value = on; guardar("rumboArriba", on) }
    fun ponerAvisoVel(on: Boolean) { avisoVel.value = on; guardar("avisoVel", on); if (!on) excesoVel.value = false }
    fun ponerAvisosVoz(on: Boolean) { avisosVoz.value = on; guardar("avisosVoz", on) }
    fun ponerLimiteVel(kmh: Int) {
        limiteVel.value = kmh.coerceIn(20, 200)
        if (::prefs.isInitialized) prefs.edit().putInt("limiteVel", limiteVel.value).apply()
    }

    // ---------------- Entrada de datos ----------------

    fun recibirTelemetria(texto: String) {
        try {
            val j = JSONObject(texto)
            when (j.optString("k")) {
                "t" -> {
                    val r = Protocolo.parseRapida(j)
                    rapida.value = r
                    Estadisticas.muestra(r)
                }
                "s" -> {
                    val e = Protocolo.parseEstado(j)
                    estado.value = e
                    // Sin GPS en el teléfono: dibuja el recorrido con el GPS de la moto
                    if (UbicacionManager.ubicacion.value == null && e.gpsFix && !e.gpsTelefono)
                        UbicacionManager.agregarPunto(e.lat, e.lon)
                }
                "x" -> viaje.value = Protocolo.parseViaje(j)
                "d" -> FuncionesAvanzadas.recibirDiagnostico(j)
                "a" -> iphoneMusica.value = Protocolo.parseIphoneMusica(j, SystemClock.elapsedRealtime())
                "c" -> iphoneLlamada.value = Protocolo.parseIphoneLlamada(j)
                "i" -> {
                    // Notificación del iPhone (la moto ya la puso en su LCD)
                    val app = j.optString("ap", "")
                    val titulo = j.optString("ti", "")
                    val texto = j.optString("tx", "")
                    ultimaNotif.value = "iPhone · " + listOf(app, titulo.ifBlank { texto }).filter { it.isNotBlank() }.joinToString(": ")
                }
            }
            errores = 0
            datosMalos.value = false
        } catch (e: Exception) {
            errores++
            if (errores > 5) datosMalos.value = true
        }
    }

    /** Datos calculados por el teléfono (sin la tarjeta). Solo cuentan para estadísticas si el motor va prendido o rodando. */
    fun recibirTelefono(r: Rapida) {
        rapida.value = r
        if (r.motor || r.vel > 3f) Estadisticas.muestra(r)
    }

    fun recibirRespuesta(texto: String) {
        val mostrar = pendientes.removeFirstOrNull() ?: true
        val limpio = texto.trimEnd()
        if (limpio.startsWith("[")) {
            Protocolo.parseMant(limpio)?.let { mant.value = it }
            return
        }
        if (ProtocoloAvanzado.esEstado(limpio)) {
            FuncionesAvanzadas.recibirEstado(limpio)
            return
        }
        if (limpio.startsWith("{") && limpio.contains("\"fw\"")) {
            Protocolo.parseConfig(limpio)?.let { config.value = it }
            return
        }
        if (mostrar && limpio.isNotEmpty()) {
            agregarConsola(limpio)
            ultimaRespuesta.value = limpio.lines().lastOrNull { it.isNotBlank() } ?: limpio
        }
    }

    private fun agregarConsola(t: String) {
        consola.value = (consola.value + t + "\n").takeLast(6000)
    }

    // ---------------- Salida hacia la moto ----------------

    /** Comando escrito por el usuario (se muestra en la consola). */
    fun comando(texto: String) {
        val t = texto.trim()
        if (t.isEmpty()) return
        agregarConsola("> $t")
        if (demo.value) {
            responderDemo(t)
            ultimaRespuesta.value = consola.value.trimEnd().lines().lastOrNull() ?: ""
            return
        }
        if (BleManager.enviarComando(t)) pendientes.addLast(true)
        else agregarConsola("(sin conexión con la moto)")
    }

    /** Comando automático de la app (no ensucia la consola). */
    fun comandoSilencioso(texto: String, esperaRespuesta: Boolean = true) {
        if (demo.value) return
        if (BleManager.enviarComando(texto, opcional = !esperaRespuesta) && esperaRespuesta) pendientes.addLast(false)
    }

    fun limpiarPendientes() = pendientes.clear()

    /** Música del iPhone: "play", "pausa", "toggle", "sig", "ant", "vol+", "vol-". */
    fun iphoneMusicaCmd(cmd: String) = comandoSilencioso("ios $cmd")

    /** Llamada del iPhone: contestar o colgar/rechazar. */
    fun iphoneLlamadaCmd(contestar: Boolean) {
        comandoSilencioso(if (contestar) "ios contestar" else "ios colgar")
        iphoneLlamada.value = iphoneLlamada.value.copy(activa = false)
    }

    fun iphoneOlvidar() = comando("ios olvidar")

    /** Pide los ajustes de la tarjeta (firmware v1.3+). */
    fun pedirConfig() {
        if (demo.value) {
            config.value = (config.value ?: ConfigMoto(
                fw = "demo", encendidaS = 3600, memoriaKb = 182, tempChip = 41.5f, kmPorLitro = 35.2f, rtc = true, imu = true,
                sd = true, sensorTemp = true, pistaMejor = 5.82f, gasVacioMv = 310, gasLlenoMv = 2650,
                shift = listOf(6200, 6600, 6900, 7100, 7300), relacion = listOf(160f, 100f, 74f, 60f, 51f),
            )).copy(perfil = estado.value.perfil)
            return
        }
        comandoSilencioso("cfgjson")
    }

    /** Ajuste desde los botones de la página Moto: se ve en la consola y luego se refrescan los ajustes. */
    fun ajusteMoto(cmd: String) {
        ultimaRespuesta.value = ""
        comando(cmd)
        pedirConfig()
    }

    fun pedirMantenimiento() {
        if (demo.value) return
        comandoSilencioso("mantjson")
    }

    fun registrarServicio(i: Int) {
        if (demo.value) {
            mant.value = mant.value.mapIndexed { k, m -> if (k == i) m.copy(km = 0, dias = 0, vencido = false) else m }
            return
        }
        comandoSilencioso("mant reset $i")
        pedirMantenimiento()
    }

    /** Notificación leída del teléfono: se muestra en la app y se manda a la pantalla de la moto. */
    fun notificacionTelefono(texto: String) {
        ultimaNotif.value = texto
        if (!demo.value && reenviarNotif.value) BleManager.enviarNotif(texto)
    }

    // ---------------- Modo demo (sin moto) ----------------

    private fun responderDemo(t: String) {
        val c = t.lowercase()
        when {
            c.startsWith("perfil") -> {
                estado.value = estado.value.copy(perfil = if (c.contains("sport")) 1 else 0)
                agregarConsola("(demo) Perfil cambiado.")
            }
            c.startsWith("corte on") -> { estado.value = estado.value.copy(corte = true); agregarConsola("(demo) Motor BLOQUEADO.") }
            c.startsWith("corte off") -> { estado.value = estado.value.copy(corte = false); agregarConsola("(demo) Motor desbloqueado.") }
            c.startsWith("vel ") -> {
                config.value = config.value?.copy(fuenteVel = when { c.endsWith("hall") -> 1; c.endsWith("gps") -> 2; else -> 0 })
                agregarConsola("(demo) Fuente de velocidad cambiada.")
            }
            c.startsWith("brillo ") -> { config.value = config.value?.copy(autoBrillo = c.endsWith("auto")); agregarConsola("(demo) Brillo cambiado.") }
            c.startsWith("wifi ") -> { config.value = config.value?.copy(wifi = c.endsWith("on")); agregarConsola("(demo) WiFi cambiado.") }
            c.startsWith("mant set") -> {
                val p = c.split(" ")
                val i = p.getOrNull(2)?.toIntOrNull()
                val km = p.getOrNull(3)?.toIntOrNull()
                val dias = p.getOrNull(4)?.toIntOrNull()
                if (i != null && km != null && dias != null) mant.value = mant.value.mapIndexed { k, m -> if (k == i) m.copy(intKm = km, intDias = dias) else m }
                agregarConsola("(demo) Intervalo cambiado.")
            }
            else -> agregarConsola("(demo) Conecta la moto para ejecutar '$t'.")
        }
    }

    private fun iniciarDemo() {
        demoJob?.cancel()
        conexion.value = Conexion.DEMO
        mant.value = listOf(
            MantItem("Cadena", 120, 500, 12, 30, false),
            MantItem("Aceite", 1850, 2000, 80, 90, false),
            MantItem("Filtro", 4100, 4000, 170, 180, true),
            MantItem("Bujia", 2300, 6000, 140, 365, false),
            MantItem("Frenos", 900, 4000, 60, 180, false),
        )
        val ratios = floatArrayOf(160f, 100f, 74f, 60f, 51f)
        demoJob = scope.launch {
            val t0 = SystemClock.elapsedRealtime()
            var odo = 12345.6f
            var km = 0f
            var vmax = 0f
            var tick = 0
            while (isActive) {
                val t = (SystemClock.elapsedRealtime() - t0) / 1000f
                val v = max(0f, 45f + 40f * sin(t / 6f))
                val m = when {
                    v < 3 -> 0; v < 15 -> 1; v < 30 -> 2; v < 50 -> 3; v < 70 -> 4; else -> 5
                }
                val rpm = if (m == 0) 1450 + (40 * sin(t * 3)).toInt()
                else (v * ratios[m - 1] * (1 + 0.03f * sin(t * 2))).toInt()
                val gas = 100 - ((t * 2).toInt() % 100)
                val anterior = estado.value
                rapida.value = Rapida(
                    vel = v, rpm = rpm, marcha = m,
                    vMoto = if (t % 20f < 0.5f) 10.9f else 13.9f + 0.2f * sin(t / 4f),
                    gas = gas, temp = 70f + 25f * (1 - exp(-t / 30f)),
                    incl = 3f + 2f * sin(t), aFwd = 0.1f * cos(t / 6f),
                    shift = if (anterior.perfil == 1) 7400 else 5600,
                    ignicion = true, motor = true,
                )
                // El demo NO alimenta Estadisticas: antes sumaba km y máximas inventadas a tu viaje real
                km += v * 0.25f / 3600f
                odo += v * 0.25f / 3600f
                vmax = max(vmax, v)
                if (tick % 4 == 0) {
                    val lat = 21.5042 + 0.003 * sin(t / 40.0)
                    val lon = -104.8946 + 0.003 * cos(t / 40.0)
                    estado.value = Estado(
                        vLipo = 3.9f + 0.1f * sin(t / 30f), lipoPct = 67, gpsFix = true,
                        lat = lat, lon = lon, sats = 9, bateria = "CARGANDO",
                        corte = anterior.corte, demoEsp = true, perfil = anterior.perfil,
                        alerta = if (gas <= 10) "GASOLINA CRITICA" else "",
                    )
                    if (UbicacionManager.ubicacion.value == null) UbicacionManager.agregarPunto(lat, lon)
                    viaje.value = ViajeInfo(
                        odometro = odo, arranques = 1234, activo = true, km = km, velMax = vmax,
                        litros = km / 35f, hoyKm = 24.3f + km, ayerKm = 31.0f, kmPorLitro = 35.2f,
                        autonomia = (gas / 100f * 14f * 35.2f).toInt(), mantVencido = 2,
                        ralenti = 1450, pistaMejor = 5.82f,
                    )
                }
                tick++
                delay(250)
            }
        }
    }
}
