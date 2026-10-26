package com.xof.ft150ts

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
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
    val usados: Int = 0,             // satélites que el chip marca "en uso" (engaña bajo techo, ver senalDebil)
    val visibles: Int = 0,           // satélites que alcanza a ver
    val precision: Float = -1f,      // ± metros
    val dobleFrecuencia: Boolean = false,   // está usando L5/E5a (más preciso entre edificios)
    val constelaciones: String = "", // GPS, Galileo, GLONASS, BeiDou...
    val edadS: Int = -1,             // hace cuántos segundos llegó la última posición buena (-1 = ninguna todavía)
    val aprox: Boolean = false,      // el mapa está usando la ubicación aproximada (wifi/antenas) mientras llega el GPS
    val precisionAprox: Float = -1f, // ± metros de la aproximada
    val fuertes: Int = 0,            // satélites con señal buena (≥ 25 dB-Hz): sin al menos 4 no sale posición
    val mejorSenal: Float = 0f,      // la señal más fuerte que llega (dB-Hz). Afuera: 35-45. Bajo techo: 10-25
    val atorado: Boolean = false,    // hay buena señal pero no llegan posiciones: la app está reconectando el GPS
    val respaldo: Boolean = false,   // la posición viene del GPS de Google (fusionado) porque el directo se calló
    val reconexiones: Int = 0,       // cuántas veces se reconectó el GPS sola desde que se abrió la app
) {
    val conSenal: Boolean get() = edadS in 0..3
    /** Todavía no llega la primera posición desde que se prendió el GPS. */
    val buscando: Boolean get() = edadS < 0
    /**
     * Ve satélites pero muy débiles (bajo techo, en la cochera, entre edificios). Ojo: "en uso" engaña,
     * algunos chips (Qualcomm, Xiaomi) marcan 20-30 satélites "en uso" aunque no les alcance para dar posición.
     */
    val senalDebil: Boolean get() = !conSenal && !atorado && fuertes < 4
}

/** Desde cuántos dB-Hz un satélite cuenta como "buena señal". */
private const val SENAL_BUENA = 25f

/** "45 s", "12 min", "1 h 59 min". */
fun duracionCorta(s: Int): String = when {
    s < 0 -> "--"
    s < 60 -> "$s s"
    s < 3600 -> "${s / 60} min"
    else -> "${s / 3600} h" + if (s % 3600 >= 60) " ${s % 3600 / 60} min" else ""
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

    /** Ubicación del respaldo (Google/wifi/antenas): para el mapa mientras el GPS agarra señal. */
    val aprox = MutableStateFlow<Location?>(null)

    private lateinit var ctx: Context
    private var lm: LocationManager? = null
    private var activo = false
    /** Cuándo se prendió el GPS (no se "reconecta" mientras busca la primera señal). */
    private var arrancadoEn = 0L
    private var ultimoRefresco = 0L
    private var ultimoRenovado = 0L
    private var refrescos = 0
    private var reconexiones = 0
    /** Cuándo llegó (reloj interno del teléfono) la última posición buena; 0 = ninguna desde que se prendió. */
    private var recibidoGps = 0L
    /** Cuándo entregó algo el GPS directo (proveedor "gps"); 0 = nada desde que se prendió. */
    private var recibidoDirecto = 0L
    private var nanosGps = 0L
    /** Cuándo dijo el chip que ya tenía posición (onFirstFix); 0 = todavía no. */
    private var primerFixChip = 0L
    /** Android 12+: el respaldo es el fusionado de Google en alta precisión (usa los mismos satélites). */
    private var respaldoFused = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val principal = Handler(Looper.getMainLooper())

    /*
     * Los listeners se pueden cambiar por unos NUEVOS sin soltar el GPS (ver renovar()). Si Android "congela" la
     * petición (pasa en algunos Xiaomi al apagar la pantalla o al mandar la app a segundo plano), una petición nueva
     * la revive, y como primero se pide la nueva y luego se suelta la vieja, el chip nunca se apaga.
     * Se implementan los 4 métodos para que no truene en Android 8 y 9.
     */
    private fun crearListenerGps() = object : LocationListener {
        override fun onLocationChanged(location: Location) = deGps(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) { velocidad.value = -1f }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun crearListenerAprox() = object : LocationListener {
        override fun onLocationChanged(location: Location) = nuevaAprox(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private var listener: LocationListener = crearListenerGps()
    private var listenerAprox: LocationListener = crearListenerAprox()

    /** Pasivo: lo que consigan otras apps (Maps, Waze...). No prende nada por su cuenta. */
    private val listenerPasivo = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.provider == LocationManager.GPS_PROVIDER) {
                // Solo si es de ahorita (no una guardada de hace rato)
                val edad = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
                if (edad in 0..10_000) deGps(location)
            } else nuevaAprox(location)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /** Satélites: cuántos ve, cuántos usa, cuántos llegan con buena señal y si usa doble frecuencia. */
    private val satelites = object : GnssStatus.Callback() {
        override fun onStarted() { primerFixChip = 0L }
        override fun onStopped() { primerFixChip = 0L }
        override fun onFirstFix(ttffMillis: Int) { primerFixChip = SystemClock.elapsedRealtime() }

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var usados = 0
            var fuertes = 0
            var mejor = 0f
            var l5 = false
            val tipos = HashSet<String>()
            for (i in 0 until status.satelliteCount) {
                val cn0 = status.getCn0DbHz(i)
                if (cn0 >= SENAL_BUENA) fuertes++
                if (cn0 > mejor) mejor = cn0
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
                if (status.hasCarrierFrequencyHz(i) && status.getCarrierFrequencyHz(i) < 1.2e9f) l5 = true
            }
            estado.value = estado.value.copy(
                usados = usados, visibles = status.satelliteCount, dobleFrecuencia = l5,
                constelaciones = tipos.sorted().joinToString(" · "), fuertes = fuertes, mejorSenal = mejor,
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
        if (!::ctx.isInitialized) return
        // Modo sueño: el GPS solo se prende en las revisiones de cada 30 min
        if (ModoSueno.dormido.value && !ModoSueno.revisando.value) return
        if (activo) {
            // Ya estaba prendido (p. ej. regresas a la app o prendes la pantalla): si las posiciones no están
            // llegando, Android pudo haber "congelado" la petición mientras estaba en segundo plano: se renueva
            if (edadMs() > 3_000) renovar(forzar = false)
            return
        }
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!ok) return
        val m = lm ?: return
        try {
            listener = crearListenerGps()
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            activo = true
            arrancadoEn = SystemClock.elapsedRealtime()
            recibidoGps = 0L
            recibidoDirecto = 0L
            primerFixChip = 0L
            refrescos = 0
            // Para que el mapa no arranque vacío (vieja: no cuenta como señal)
            m.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { if (ubicacion.value == null) ubicacion.value = it }
        } catch (e: Exception) {
            activo = false
        }
        if (!activo) return
        listenerAprox = crearListenerAprox()
        pedirRespaldo(m)
        try { m.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000L, 0f, listenerPasivo, Looper.getMainLooper()) } catch (_: Exception) { }
        try {
            if (Build.VERSION.SDK_INT >= 30) m.registerGnssStatusCallback(ContextCompat.getMainExecutor(ctx), satelites)
            else @Suppress("DEPRECATION") m.registerGnssStatusCallback(satelites, principal)
        } catch (_: Exception) { }
    }

    /**
     * Respaldo. Android 12+: el fusionado de Google en alta precisión, que usa los mismos satélites (más wifi y
     * antenas): si el GPS directo se atora, de ahí sale la posición buena. Antes de Android 12: wifi/antenas.
     */
    private fun pedirRespaldo(m: LocationManager) {
        respaldoFused = false
        if (Build.VERSION.SDK_INT >= 31 && m.allProviders.contains(LocationManager.FUSED_PROVIDER)) {
            try {
                val pedido = LocationRequest.Builder(1000L).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build()
                m.requestLocationUpdates(LocationManager.FUSED_PROVIDER, pedido, ContextCompat.getMainExecutor(ctx), listenerAprox)
                respaldoFused = true
                return
            } catch (_: Exception) { }
        }
        if (m.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
            try { m.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, listenerAprox, Looper.getMainLooper()) } catch (_: Exception) { }
        }
    }

    /**
     * Renueva la petición con listeners NUEVOS y después suelta los viejos (en ese orden: el chip nunca se apaga y
     * no pierde los satélites). Revive una petición que Android dejó "congelada".
     */
    private fun renovar(forzar: Boolean) {
        val m = lm ?: return
        if (!activo) return
        val ahora = SystemClock.elapsedRealtime()
        if (!forzar && ahora - ultimoRenovado < 10_000) return
        ultimoRenovado = ahora
        val viejoGps = listener
        val viejoAprox = listenerAprox
        val nuevoGps = crearListenerGps()
        try {
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, nuevoGps, Looper.getMainLooper())
        } catch (_: Exception) { return }
        listener = nuevoGps
        try { m.removeUpdates(viejoGps) } catch (_: Exception) { }
        listenerAprox = crearListenerAprox()
        pedirRespaldo(m)
        try { m.removeUpdates(viejoAprox) } catch (_: Exception) { }
        reconexiones++
        estado.value = estado.value.copy(reconexiones = reconexiones)
    }

    /** Último recurso (solo con la app en pantalla y señal fuerte de verdad): suelta todo y lo vuelve a pedir. */
    private fun reiniciar() {
        pausar()
        arrancar()
        reconexiones++
        estado.value = estado.value.copy(reconexiones = reconexiones)
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
        recibidoDirecto = 0L
        primerFixChip = 0L
        respaldoFused = false
        velocidad.value = -1f
        // Que no se queden números viejos de satélites
        estado.value = estado.value.copy(usados = 0, visibles = 0, dobleFrecuencia = false, constelaciones = "", edadS = -1,
            fuertes = 0, mejorSenal = 0f, atorado = false, respaldo = false)
    }

    /**
     * Vigilante: cada 2 s revisa qué tan vieja es la última posición. Si pasa de 3 s la velocidad se pone en "--"
     * (no se queda congelada).
     * Si hay buena señal (4 o más satélites fuertes, o el chip ya dijo que tiene posición) y aun así las posiciones
     * no llegan, reconecta el GPS sin apagarlo (renovar), con espera creciente: 10 s, 20 s, 40 s y luego cada minuto,
     * sin límite. Cada 4 intentos fallidos, con la app en pantalla y señal fuerte de verdad, lo reinicia completo.
     * Bajo techo (menos de 4 fuertes) NO hace nada: reiniciar ahí borra la búsqueda y nunca agarra (falla de la v2.4).
     */
    private fun vigilar() {
        scope.launch {
            while (isActive) {
                delay(2_000)
                val ahora = SystemClock.elapsedRealtime()
                val e = edadMs()
                val s = if (recibidoGps == 0L) -1 else (e / 1000).toInt().coerceAtMost(999_999)
                val usarAprox = aproxVigente()
                val enUso = BleManager.primerPlano || TelefonoMoto.trabajando.value
                val st = estado.value
                val buenaSenal = st.fuertes >= 4 || (primerFixChip != 0L && recibidoDirecto < primerFixChip)
                val atorado = activo && enUso && buenaSenal && ahora - arrancadoEn > 15_000 && e > 8_000
                if (s != st.edadS || usarAprox != st.aprox || atorado != st.atorado) {
                    estado.value = st.copy(edadS = s, aprox = usarAprox, atorado = atorado)
                }
                if (e > 3_000) velocidad.value = -1f
                // Va con el respaldo de Google porque el directo se calló: cada minuto intenta revivir el directo
                if (st.respaldo && e <= 3_000 && enUso && ahora - recibidoDirecto > 30_000 && ahora - ultimoRenovado > 60_000) {
                    renovar(forzar = true)
                }
                if (!atorado) { refrescos = 0; continue }
                val espera = if (refrescos >= 3) 60_000L else 10_000L shl refrescos
                if (ahora - ultimoRefresco < espera) continue
                ultimoRefresco = ahora
                refrescos++
                if (refrescos % 4 == 0 && BleManager.primerPlano && estado.value.fuertes >= 5) reiniciar()
                else renovar(forzar = true)
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

    /** Hace cuánto llegó la última posición buena (reloj interno del teléfono, a la hora en que llegó). */
    fun edadMs(): Long {
        if (recibidoGps != 0L) return SystemClock.elapsedRealtime() - recibidoGps
        // Todavía no llega ninguna desde que se prendió: la "última conocida" de Android, por su propia hora
        val l = ubicacion.value ?: return Long.MAX_VALUE
        val e = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000
        return if (e < 0) Long.MAX_VALUE else e
    }

    /** Del GPS directo (o de otra app por el pasivo). */
    private fun deGps(loc: Location) {
        recibidoDirecto = SystemClock.elapsedRealtime()
        nueva(loc, respaldo = false)
    }

    private fun nueva(loc: Location, respaldo: Boolean) {
        // La misma posición puede llegar por dos caminos (GPS y pasivo): solo cuenta una vez. Si alguna trae una
        // hora rara (del futuro), no se queda rechazando todas las que siguen: con 5 s de diferencia se corrige sola.
        val t = loc.elapsedRealtimeNanos
        if (t != 0L) {
            val atras = nanosGps - t
            if (atras in 0L..5_000_000_000L) return
            nanosGps = t
        }
        recibidoGps = SystemClock.elapsedRealtime()
        refrescos = 0
        ubicacion.value = loc
        velocidad.value = if (loc.hasSpeed()) loc.speed * 3.6f else -1f
        estado.value = estado.value.copy(precision = if (loc.hasAccuracy()) loc.accuracy else -1f, edadS = 0,
            aprox = false, atorado = false, respaldo = respaldo)
        agregarPunto(loc.latitude, loc.longitude)
        if (Moto.enviarGps.value && BleManager.conectado()) {
            val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else -1f
            val cmd = String.format(Locale.US, "gps %.6f %.6f %.1f", loc.latitude, loc.longitude, kmh)
            Moto.comandoSilencioso(cmd, esperaRespuesta = false)
        }
    }

    /** Del respaldo: solo para el mapa (no cuenta para velocidad, recorrido, odómetro ni baches)... */
    private fun nuevaAprox(loc: Location) {
        aprox.value = loc
        // ...salvo que sea del fusionado de Google, venga de satélites y el GPS directo esté callado: entonces es
        // tan buena como la del GPS y se usa para todo (así una falla del GPS directo no deja sin velocímetro)
        if (respaldoFused && loc.provider == LocationManager.FUSED_PROVIDER && deSatelite(loc) &&
            SystemClock.elapsedRealtime() - recibidoDirecto > 3_000
        ) {
            nueva(loc, respaldo = true)
            return
        }
        val usar = aproxVigente()
        estado.value = estado.value.copy(precisionAprox = if (loc.hasAccuracy()) loc.accuracy else -1f, aprox = usar)
    }

    /** ¿La del fusionado salió de satélites? Precisa, con velocidad, de ahorita y con 4 o más satélites fuertes. */
    private fun deSatelite(l: Location): Boolean {
        if (!l.hasAccuracy() || l.accuracy > 20f || !l.hasSpeed()) return false
        val edad = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000
        return edad in 0..3_000 && estado.value.fuertes >= 4
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

    /**
     * Velocidad solo si es creíble: bajo techo el GPS rebota y saca velocidades falsas (10–30 km/h con el teléfono
     * quieto). Se descarta si la posición viene mala (> 25 m), si el propio GPS dice que la velocidad tiene más de
     * ±2.5 m/s de error o si hay menos de 4 satélites con buena señal. -1 = no confiable.
     */
    fun velocidadConfiableKmh(): Float {
        val l = ubicacion.value ?: return -1f
        if (!l.hasSpeed() || edadMs() > 3_000) return -1f
        if (!SeguridadVel.confiable(l.hasAccuracy(), l.accuracy,
                android.os.Build.VERSION.SDK_INT >= 26 && l.hasSpeedAccuracy(),
                if (android.os.Build.VERSION.SDK_INT >= 26) l.speedAccuracyMetersPerSecond else 0f,
                estado.value.senalDebil)) return -1f
        return l.speed * 3.6f
    }
}

/** Reglas para creerle a la velocidad del GPS (sin Android: se prueban en SeguridadVelTest). */
object SeguridadVel {
    fun confiable(tienePrecision: Boolean, precisionM: Float, tieneErrorVel: Boolean, errorVelMs: Float, senalDebil: Boolean): Boolean {
        if (senalDebil) return false
        if (tienePrecision && precisionM > 25f) return false
        if (tieneErrorVel && errorVelMs > 2.5f) return false
        return true
    }

    /** El aviso entra solo si la velocidad pasó el límite [MUESTRAS] veces seguidas (cada 0.5 s = 2 s). */
    const val MUESTRAS = 4
}
