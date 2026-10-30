package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Modo sueño: el teléfono está cargando pero no se mueve (lo quitaste de la moto y lo pusiste a cargar en la
 * mesa, o la moto se quedó con el switch prendido). Después de un rato quieto (1 h de fábrica) la app se duerme:
 * apaga el GPS, los sensores de vibración, la dashcam, la búsqueda rápida de la tarjeta y deja que la pantalla
 * se apague. Cada 30 min prende la ubicación un momento para ver si el teléfono se movió.
 *
 * Despierta si: se movió (revisión de ubicación o sensor de movimiento del teléfono), tocas la pantalla,
 * se desconecta el cargador o apagas el modo sueño.
 */
object ModoSueno {
    // Ajustes
    val activo = MutableStateFlow(true)
    val minutosDormir = MutableStateFlow(60)
    val minutosRevisar = MutableStateFlow(30)

    // Estado
    val dormido = MutableStateFlow(false)
    val revisando = MutableStateFlow(false)
    /** Desde cuándo (hora del reloj) cumple: cargando y sin moverse. 0 = no cumple. */
    val quietoDesde = MutableStateFlow(0L)
    val dormidoDesde = MutableStateFlow(0L)
    val proximaRevision = MutableStateFlow(0L)
    /** Lo último que pasó (para Ajustes). */
    val ultimoEvento = MutableStateFlow("")
    /**
     * Dormida por el horario nocturno (Nocturno): no hace revisiones de ubicación y no despierta por no estar
     * cargando. Sí despierta si tocas la pantalla, se conecta la moto o el sensor siente que lo llevas.
     */
    val porNoche = MutableStateFlow(false)
    /** Ya arrancó (si la app no está corriendo, no hay nada que dormir). */
    val listo: Boolean get() = iniciado

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private val principal = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private var sm: SensorManager? = null
    private var sensorMov: Sensor? = null
    private var iniciado = false
    private var trabajo: Job? = null
    private var despertoEn = 0L
    /** Dónde estaba cuando se quedó quieto / se durmió: lat, lon, ± metros. */
    private var ancla: Triple<Double, Double, Float>? = null

    private val movimiento = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            principal.post { if (dormido.value) despertar("El teléfono se movió (sensor de movimiento)") }
        }
    }

    fun iniciar(c: Context) {
        if (iniciado) return
        iniciado = true
        val app = c.applicationContext
        val p = app.getSharedPreferences("sueno", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", true)
        minutosDormir.value = p.getInt("minDormir", 60)
        minutosRevisar.value = p.getInt("minRevisar", 30)
        ultimoEvento.value = p.getString("evento", "") ?: ""
        sm = sensores(app)
        sensorMov = sm?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        // Si Android reinició la app mientras dormía y sigue cargando, se vuelve a dormir donde estaba
        if (p.getBoolean("dormido", false) && activo.value && Energia.cargando.value) {
            val la = p.getString("anclaLat", null)?.toDoubleOrNull()
            val lo = p.getString("anclaLon", null)?.toDoubleOrNull()
            if (la != null && lo != null) ancla = Triple(la, lo, p.getFloat("anclaAcc", 50f))
            dormir(p.getLong("desde", System.currentTimeMillis()), "Sigue dormida (la app se reinició)")
        } else if (p.getBoolean("dormido", false)) {
            p.edit().putBoolean("dormido", false).apply()
        }
        scope.launch {
            while (isActive) {
                delay(20_000)
                vigilar()
            }
        }
        // También despierta si lo pones en el soporte con NFC o si se conecta la tarjeta de la moto
        scope.launch { NfcMoto.presente.collect { if (it && dormido.value) despertar("Lo pusiste en el soporte (NFC)") } }
        // Se cortó la corriente (quitaste el cable, o al dar marcha baja el voltaje): despierta al momento
        scope.launch { Energia.cargando.collect { if (!it && dormido.value && !porNoche.value) despertar("Se desconectó el cargador") } }
        scope.launch { Moto.conexion.collect { if (it == Conexion.CONECTADO && dormido.value) despertar("Se conectó la tarjeta de la moto") } }
    }

    // ---------------- ¿Cuándo dormir? ----------------

    private fun vigilar() {
        if (dormido.value) {
            if (porNoche.value) return                   // la noche la maneja Nocturno
            when {
                !activo.value -> despertar("Apagaste el modo sueño")
                !Energia.cargando.value -> despertar("Se desconectó el cargador")
            }
            return
        }
        if (!activo.value || !Energia.cargando.value || moviendose()) {
            quietoDesde.value = 0L
            ancla = null
            return
        }
        val ahora = System.currentTimeMillis()
        val aqui = posicion()
        if (quietoDesde.value == 0L) {
            quietoDesde.value = ahora
            ancla = aqui
            return
        }
        // Se fue a otro lado sin que se notara la velocidad: vuelve a contar
        val a = ancla
        if (a == null) ancla = aqui
        else if (aqui != null && distancia(a, aqui) > umbral(a, aqui)) {
            quietoDesde.value = ahora
            ancla = aqui
            return
        }
        if (ahora - quietoDesde.value >= minutosDormir.value * 60_000L) {
            dormir(ahora, "Cargando y sin moverse ${textoMin(minutosDormir.value)}")
        }
    }

    /** Algo dice que está en uso o rodando (también lo usa el reposo). */
    fun moviendose(): Boolean {
        if (UbicacionManager.velocidad.value > 5f) return true
        if (TelefonoMoto.info.value.motor) return true
        val tarjeta = Moto.conexion.value == Conexion.CONECTADO
        if (tarjeta && (Moto.rapida.value.motor || Moto.rapida.value.rpm > 0)) return true
        return false
    }

    /** Tocaste la pantalla (MainActivity.onUserInteraction): estás usándolo. */
    /** Última vez que tocaste la pantalla de la app (reloj). 0 = no desde que arrancó. */
    @Volatile var ultimoUso = 0L
        private set

    fun actividad() {
        ultimoUso = System.currentTimeMillis()
        if (dormido.value) { despertar("Tocaste la pantalla"); return }
        if (quietoDesde.value != 0L) quietoDesde.value = System.currentTimeMillis()
    }

    // ---------------- Dormir / despertar ----------------

    fun dormirAhora() {
        if (dormido.value) return
        // El mismo toque que la acaba de despertar no cuenta como "dormir ahora"
        if (SystemClock.elapsedRealtime() - despertoEn < 1_500L) return
        ancla = posicion() ?: ancla
        dormir(System.currentTimeMillis(), "Dormida a mano")
    }

    private fun dormir(desde: Long, motivo: String) {
        dormido.value = true
        dormidoDesde.value = desde
        quietoDesde.value = 0L
        UbicacionManager.pausar()
        try { sensorMov?.let { sm?.requestTriggerSensor(movimiento, it) } } catch (_: Exception) { }
        evento("${hora(desde)} · se durmió: $motivo")
        guardar()
        if (porNoche.value) { trabajo?.cancel(); trabajo = null; proximaRevision.value = 0L } else programarRevisiones()
    }

    /** Dormir por el horario nocturno (sin revisiones de ubicación). Si ya dormía normal, pasa a dormir por la noche. */
    fun dormirPorNoche() {
        if (!iniciado || (dormido.value && porNoche.value)) return
        porNoche.value = true
        if (dormido.value) {
            trabajo?.cancel(); trabajo = null; proximaRevision.value = 0L
            evento("${hora(System.currentTimeMillis())} · sigue dormida: horario nocturno")
        } else {
            ancla = posicion() ?: ancla
            dormir(System.currentTimeMillis(), "Horario nocturno")
        }
    }

    /** Termina el sueño de la noche (fin del horario o lo apagaste). No toca un sueño normal. */
    fun salirDeNoche(motivo: String) {
        if (!porNoche.value) return
        if (dormido.value) despertar(motivo) else porNoche.value = false
    }

    private fun programarRevisiones() {
        trabajo?.cancel()
        trabajo = scope.launch {
            while (isActive && dormido.value) {
                val espera = minutosRevisar.value * 60_000L
                proximaRevision.value = System.currentTimeMillis() + espera
                delay(espera)
                if (dormido.value) revisar()
            }
        }
    }

    fun despertar(motivo: String) {
        if (!dormido.value) return
        trabajo?.cancel()
        trabajo = null
        try { sm?.cancelTriggerSensor(movimiento, sensorMov) } catch (_: Exception) { }
        revisando.value = false
        dormido.value = false
        porNoche.value = false
        despertoEn = SystemClock.elapsedRealtime()
        proximaRevision.value = 0L
        quietoDesde.value = 0L
        ancla = null
        evento("${hora(System.currentTimeMillis())} · despertó: $motivo")
        guardar()
        // Todo regresa a como estaba (si despertó a media revisión y nadie usa el GPS, se apaga)
        if (BleManager.primerPlano || TelefonoMoto.trabajando.value) UbicacionManager.arrancar()
        else if (!(BleManager.conectado() && Moto.enviarGps.value)) UbicacionManager.pausar()
        BleManager.buscarYa()
    }

    // ---------------- Revisión con la ubicación ----------------

    /**
     * Prende la ubicación hasta 2 min: espera una posición nueva del GPS (buena) o, si adentro no agarra,
     * la aproximada (wifi). Si se alejó de donde se durmió, despierta.
     */
    private suspend fun revisar() {
        revisando.value = true
        val inicio = SystemClock.elapsedRealtimeNanos()
        val t0 = SystemClock.elapsedRealtime()
        UbicacionManager.arrancar()
        var gps: Location? = null
        var aprox: Location? = null
        while (dormido.value && SystemClock.elapsedRealtime() - t0 < 120_000L) {
            delay(2_000)
            val g = UbicacionManager.ubicacion.value
            if (g != null && g.elapsedRealtimeNanos > inicio && UbicacionManager.edadMs() < 5_000) {
                gps = g
                // Rodando: no hace falta esperar más
                if (g.hasSpeed() && g.speed * 3.6f > 8f) break
                if (!g.hasAccuracy() || g.accuracy <= 30f || SystemClock.elapsedRealtime() - t0 > 45_000L) break
            }
            val a = UbicacionManager.aprox.value
            if (a != null && a.elapsedRealtimeNanos > inicio) aprox = a
            // Sin GPS pero ya con la aproximada y un buen rato esperando: con eso basta
            if (gps == null && aprox != null && SystemClock.elapsedRealtime() - t0 > 60_000L) break
        }
        if (!dormido.value) return                       // despertó mientras revisaba
        UbicacionManager.pausar()
        revisando.value = false
        val pos = gps ?: aprox
        val ahora = System.currentTimeMillis()
        if (pos == null) {
            evento("${hora(ahora)} · revisé: sin señal (sigue dormida)")
            return
        }
        if (pos.hasSpeed() && pos.speed * 3.6f > 8f) {
            despertar("Va rodando (${(pos.speed * 3.6f).roundToInt()} km/h)")
            return
        }
        val aqui = Triple(pos.latitude, pos.longitude, if (pos.hasAccuracy()) pos.accuracy else 50f)
        val a = ancla
        if (a == null) {
            ancla = aqui
            guardar()
            evento("${hora(ahora)} · revisé: guardé dónde está")
            return
        }
        val d = distancia(a, aqui)
        if (d > umbral(a, aqui)) {
            despertar("Se movió ${if (d < 1000) "${d.roundToInt()} m" else String.format(Locale.US, "%.1f km", d / 1000)}")
        } else {
            // Si esta posición es más precisa, se queda como referencia
            if (aqui.third < a.third) { ancla = aqui; guardar() }
            evento("${hora(ahora)} · revisé: no se ha movido (${d.roundToInt()} m)")
        }
    }

    // ---------------- Ajustes ----------------

    fun ponerActivo(on: Boolean) {
        activo.value = on
        prefs?.edit()?.putBoolean("activo", on)?.apply()
        if (!on) { despertar("Apagaste el modo sueño"); quietoDesde.value = 0L }
    }

    fun ponerMinutosDormir(m: Int) {
        minutosDormir.value = m
        prefs?.edit()?.putInt("minDormir", m)?.apply()
    }

    fun ponerMinutosRevisar(m: Int) {
        minutosRevisar.value = m
        prefs?.edit()?.putInt("minRevisar", m)?.apply()
        // Que la siguiente revisión ya use el nuevo tiempo
        if (dormido.value && !revisando.value) programarRevisiones()
    }

    // ---------------- Ayudantes ----------------

    private fun posicion(): Triple<Double, Double, Float>? {
        val l = UbicacionManager.posicionMapa() ?: return null
        return Triple(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else 50f)
    }

    private fun distancia(a: Triple<Double, Double, Float>, b: Triple<Double, Double, Float>) =
        Baches.distanciaM(a.first, a.second, b.first, b.second)

    /** Cuánto tiene que alejarse para contar como "se movió": 150 m o lo que sumen las precisiones (máx. 500 m). */
    private fun umbral(a: Triple<Double, Double, Float>, b: Triple<Double, Double, Float>): Double =
        min(500.0, max(150.0, (a.third + b.third).toDouble()))

    private fun evento(t: String) {
        ultimoEvento.value = t
        prefs?.edit()?.putString("evento", t)?.apply()
    }

    private fun guardar() {
        val e = prefs?.edit() ?: return
        e.putBoolean("dormido", dormido.value).putLong("desde", dormidoDesde.value)
        val a = ancla
        if (a != null) e.putString("anclaLat", a.first.toString()).putString("anclaLon", a.second.toString()).putFloat("anclaAcc", a.third)
        else e.remove("anclaLat").remove("anclaLon").remove("anclaAcc")
        e.apply()
    }

    fun hora(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

    fun textoMin(m: Int): String = if (m % 60 == 0) "${m / 60} h" else "$m min"
}
