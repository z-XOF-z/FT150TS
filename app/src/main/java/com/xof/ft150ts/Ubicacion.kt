package com.xof.ft150ts

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/** Cómo está la señal del GPS (para el chip del mapa y la página Moto). */
data class EstadoGps(
    val usados: Int = 0,             // satélites usados para la posición
    val visibles: Int = 0,           // satélites que alcanza a ver
    val precision: Float = -1f,      // ± metros
    val dobleFrecuencia: Boolean = false,   // está usando L5/E5a (más preciso entre edificios)
    val constelaciones: String = "", // GPS, Galileo, GLONASS, BeiDou...
    val edadS: Int = -1,             // hace cuántos segundos llegó la última posición del GPS (-1 = ninguna todavía)
    val aprox: Boolean = false,      // el mapa está usando la ubicación aproximada (wifi/antenas) mientras llega el GPS
    val precisionAprox: Float = -1f, // ± metros de la aproximada
) {
    val conSenal: Boolean get() = edadS in 0..3
    /** Todavía no llega la primera posición del GPS desde que se prendió. */
    val buscando: Boolean get() = edadS < 0
}

/** GPS del teléfono: mueve el mapa, da la velocidad y, si la moto no tiene GPS, se lo presta. */
@SuppressLint("MissingPermission")
object UbicacionManager {
    val ubicacion = MutableStateFlow<Location?>(null)
    val recorrido = MutableStateFlow<List<GeoPoint>>(emptyList())
    /** Velocidad del GPS en km/h; -1 si no hay señal reciente (así no se queda "trabada" la última). */
    val velocidad = MutableStateFlow(-1f)
    val estado = MutableStateFlow(EstadoGps())
    /** Casa (para "Llévame a casa"). */
    val casa = MutableStateFlow<Pair<Double, Double>?>(null)

    /** Ubicación aproximada (wifi/antenas): solo para el mapa mientras el GPS agarra señal (adentro, entre edificios). */
    val aprox = MutableStateFlow<Location?>(null)

    private lateinit var ctx: Context
    private var lm: LocationManager? = null
    private var activo = false
    private var ultimoRefresco = 0L
    private var refrescos = 0
    /** Cuándo llegó (reloj interno del teléfono) la última posición del GPS; 0 = ninguna desde que se prendió. */
    private var recibidoGps = 0L
    private var nanosGps = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val principal = Handler(Looper.getMainLooper())

    // Se implementan los 4 métodos para que no truene en Android 8 y 9
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = nueva(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) { velocidad.value = -1f }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /** Respaldo con wifi/antenas (casi no gasta batería). */
    private val listenerAprox = object : LocationListener {
        override fun onLocationChanged(location: Location) = nuevaAprox(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /** Pasivo: lo que consigan otras apps (Maps, Waze...). No prende nada por su cuenta. */
    private val listenerPasivo = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.provider == LocationManager.GPS_PROVIDER) {
                // Solo si es de ahorita (no una guardada de hace rato)
                val edad = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
                if (edad in 0..10_000) nueva(location)
            } else nuevaAprox(location)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /** Satélites: cuántos ve, cuántos usa y si usa doble frecuencia. */
    private val satelites = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var usados = 0
            var l5 = false
            val tipos = HashSet<String>()
            for (i in 0 until status.satelliteCount) {
                if (!status.usedInFix(i)) continue
                usados++
                tipos += when (status.getConstellationType(i)) {
                    GnssStatus.CONSTELLATION_GPS -> "GPS"
                    GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
                    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
                    GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
                    GnssStatus.CONSTELLATION_QZSS -> "QZSS"
                    else -> "Otro"
                }
                if (Build.VERSION.SDK_INT >= 26 && status.hasCarrierFrequencyHz(i) && status.getCarrierFrequencyHz(i) < 1.2e9f) l5 = true
            }
            estado.value = estado.value.copy(
                usados = usados, visibles = status.satelliteCount, dobleFrecuencia = l5,
                constelaciones = tipos.sorted().joinToString(" · "),
            )
        }
    }

    fun iniciar(c: Context) {
        ctx = c.applicationContext
        lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val p = ctx.getSharedPreferences("ubicacion", Context.MODE_PRIVATE)
        val la = p.getString("casaLat", null)?.toDoubleOrNull()
        val lo = p.getString("casaLon", null)?.toDoubleOrNull()
        if (la != null && lo != null) casa.value = la to lo
        arrancar()
        vigilar()
    }

    fun arrancar() {
        if (activo || !::ctx.isInitialized) return
        // Modo sueño: el GPS solo se prende en las revisiones de cada 30 min
        if (ModoSueno.dormido.value && !ModoSueno.revisando.value) return
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!ok) return
        val m = lm ?: return
        try {
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            activo = true
            recibidoGps = 0L
            refrescos = 0
            // Para que el mapa no arranque vacío (vieja: no cuenta como señal)
            m.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { if (ubicacion.value == null) ubicacion.value = it }
        } catch (e: Exception) {
            activo = false
        }
        if (!activo) return
        // Respaldo: ubicación aproximada para el mapa mientras llega el GPS
        val respaldo = when {
            Build.VERSION.SDK_INT >= 31 && m.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            m.allProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (respaldo != null) {
            try { m.requestLocationUpdates(respaldo, 3000L, 0f, listenerAprox, Looper.getMainLooper()) } catch (_: Exception) { }
        }
        try { m.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000L, 0f, listenerPasivo, Looper.getMainLooper()) } catch (_: Exception) { }
        try {
            if (Build.VERSION.SDK_INT >= 30) m.registerGnssStatusCallback(ContextCompat.getMainExecutor(ctx), satelites)
            else @Suppress("DEPRECATION") m.registerGnssStatusCallback(satelites, principal)
        } catch (_: Exception) { }
    }

    /** Apaga el GPS (ahorra batería cuando la app no está en pantalla). */
    fun pausar() {
        if (!activo) return
        try { lm?.removeUpdates(listener) } catch (_: Exception) { }
        try { lm?.removeUpdates(listenerAprox) } catch (_: Exception) { }
        try { lm?.removeUpdates(listenerPasivo) } catch (_: Exception) { }
        try { lm?.unregisterGnssStatusCallback(satelites) } catch (_: Exception) { }
        activo = false
        recibidoGps = 0L
        velocidad.value = -1f
        // Que no se queden números viejos de satélites
        estado.value = estado.value.copy(usados = 0, visibles = 0, dobleFrecuencia = false, constelaciones = "", edadS = -1)
    }

    /**
     * Vigilante: cada 2 s revisa qué tan vieja es la última posición. Si pasa de 3 s la velocidad
     * se pone en "--" (no se queda congelada).
     * Ojo: NUNCA apaga y prende el GPS. Eso reinicia la búsqueda de satélites, y adentro de la casa o entre
     * edificios (donde la primera señal tarda más de 20 s) no la dejaba agarrar nunca. Si ya llegaban
     * posiciones, los satélites dicen que hay señal y aun así dejan de llegar, solo renueva la petición
     * (sin soltar el GPS), máximo una vez cada 30 s.
     */
    private fun vigilar() {
        scope.launch {
            while (isActive) {
                delay(2_000)
                val e = edadMs()
                val s = if (recibidoGps == 0L) -1 else (e / 1000).toInt().coerceAtMost(99_999)
                val usarAprox = aproxVigente()
                if (s != estado.value.edadS || usarAprox != estado.value.aprox) {
                    estado.value = estado.value.copy(edadS = s, aprox = usarAprox)
                }
                if (e > 3_000) velocidad.value = -1f
                val enUso = BleManager.primerPlano || TelefonoMoto.trabajando.value
                val ahora = SystemClock.elapsedRealtime()
                if (activo && enUso && recibidoGps != 0L && e > 12_000 && estado.value.usados >= 4 &&
                    ahora - ultimoRefresco > 30_000 && refrescos < 5
                ) {
                    ultimoRefresco = ahora
                    refrescos++
                    try {
                        lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
                    } catch (_: Exception) { }
                }
            }
        }
    }

    /** ¿El mapa debe usar la aproximada? Solo si el GPS no tiene señal reciente y la aproximada es de hace poco. */
    fun aproxVigente(): Boolean {
        val a = aprox.value ?: return false
        if (edadMs() <= 5_000) return false
        val edadA = (SystemClock.elapsedRealtimeNanos() - a.elapsedRealtimeNanos) / 1_000_000
        return edadA in 0..60_000
    }

    /** Lo que debe mostrar el mapa: el GPS si tiene señal; si no, la aproximada (si es reciente); si no, lo último. */
    fun posicionMapa(): Location? = if (aproxVigente()) aprox.value else ubicacion.value

    /** Hace cuánto llegó la última posición del GPS (reloj interno del teléfono, a la hora en que llegó). */
    fun edadMs(): Long {
        if (recibidoGps != 0L) return SystemClock.elapsedRealtime() - recibidoGps
        // Todavía no llega ninguna desde que se prendió: la "última conocida" de Android, por su propia hora
        val l = ubicacion.value ?: return Long.MAX_VALUE
        val e = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000
        return if (e < 0) Long.MAX_VALUE else e
    }

    private fun nueva(loc: Location) {
        // La misma posición puede llegar por el GPS y por el pasivo: solo cuenta una vez
        if (loc.elapsedRealtimeNanos != 0L && loc.elapsedRealtimeNanos <= nanosGps) return
        nanosGps = loc.elapsedRealtimeNanos
        recibidoGps = SystemClock.elapsedRealtime()
        refrescos = 0
        ubicacion.value = loc
        velocidad.value = if (loc.hasSpeed()) loc.speed * 3.6f else -1f
        estado.value = estado.value.copy(precision = if (loc.hasAccuracy()) loc.accuracy else -1f, edadS = 0, aprox = false)
        agregarPunto(loc.latitude, loc.longitude)
        if (Moto.enviarGps.value && BleManager.conectado()) {
            val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else -1f
            val cmd = String.format(Locale.US, "gps %.6f %.6f %.1f", loc.latitude, loc.longitude, kmh)
            Moto.comandoSilencioso(cmd, esperaRespuesta = false)
        }
    }

    /** Ubicación aproximada: solo para el mapa (no cuenta para velocidad, recorrido, odómetro ni baches). */
    private fun nuevaAprox(loc: Location) {
        aprox.value = loc
        val usar = aproxVigente()
        estado.value = estado.value.copy(precisionAprox = if (loc.hasAccuracy()) loc.accuracy else -1f, aprox = usar)
    }

    fun agregarPunto(lat: Double, lon: Double) {
        val p = GeoPoint(lat, lon)
        val lista = recorrido.value
        if (lista.isEmpty() || lista.last().distanceToAsDouble(p) > 8.0) {
            recorrido.value = if (lista.size >= 2000) lista.drop(500) + p else lista + p
        }
    }

    fun borrarRecorrido() {
        recorrido.value = emptyList()
    }

    /** Guarda dónde estás como "casa". */
    fun guardarCasa(): Boolean {
        val p = posicionActual() ?: return false
        casa.value = p
        ctx.getSharedPreferences("ubicacion", Context.MODE_PRIVATE).edit()
            .putString("casaLat", p.first.toString()).putString("casaLon", p.second.toString()).apply()
        return true
    }

    /**
     * Mejor posición disponible: GPS del teléfono, GPS de la moto o la última
     * ubicación conocida de Android (sirve para el clima y para guardar dónde quedó la moto).
     */
    fun posicionActual(): Pair<Double, Double>? {
        posicionMapa()?.let { return it.latitude to it.longitude }
        val e = Moto.estado.value
        if (e.gpsFix) return e.lat to e.lon
        if (!::ctx.isInitialized) return null
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!ok) return null
        for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                lm?.getLastKnownLocation(prov)?.let { return it.latitude to it.longitude }
            } catch (_: Exception) { }
        }
        return null
    }

    /** Velocidad del teléfono en km/h, o -1 si no hay señal reciente (3 s). */
    fun velocidadKmh(): Float {
        val l = ubicacion.value ?: return -1f
        if (!l.hasSpeed() || edadMs() > 3_000) return -1f
        return l.speed * 3.6f
    }
}
