package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Un bache: dónde, qué tan fuerte se sintió (m/s²) y cuántas veces lo has pisado. */
data class Bache(val lat: Double, val lon: Double, val fuerza: Float, val golpes: Int, val ultimo: Long)

/**
 * Mapa de baches: el acelerómetro siente los golpes de arriba-abajo (filtrando la vibración del motor)
 * y los marca en el mapa con el GPS. Si vuelves a pasar, te avisa antes de llegar.
 */
object Baches {
    val lista = MutableStateFlow<List<Bache>>(emptyList())
    val activo = MutableStateFlow(true)            // detectar
    val avisar = MutableStateFlow(true)            // avisar al acercarte
    val mostrar = MutableStateFlow(true)           // dibujar en el mapa
    val sensibilidad = MutableStateFlow(1)         // 0 baja, 1 media, 2 alta

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val avisados = HashMap<Int, Long>()    // índice -> cuándo se avisó
    private var ultimoGuardado = 0L
    private const val MAX = 800

    /** Golpe mínimo (m/s², ya sin la gravedad) según la sensibilidad. */
    fun umbral(): Float = when (sensibilidad.value) { 0 -> 7f; 2 -> 3.5f; else -> 5f }

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("baches", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", true)
        avisar.value = p.getBoolean("avisar", true)
        mostrar.value = p.getBoolean("mostrar", true)
        sensibilidad.value = p.getInt("sensibilidad", 1)
        try {
            val a = JSONArray(p.getString("lista", "[]") ?: "[]")
            lista.value = (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Bache(o.optDouble("la", 0.0), o.optDouble("lo", 0.0), o.optDouble("f", 0.0).toFloat(), o.optInt("g", 1), o.optLong("u", 0L))
            }
        } catch (_: Exception) { }
        scope.launch {
            while (isActive) {
                delay(1_000)
                if (avisar.value) revisarAdelante()
            }
        }
    }

    /** Lo llama TelefonoMoto (en el hilo principal) cuando siente un golpe rodando. */
    fun registrar(fuerza: Float) {
        val l = UbicacionManager.ubicacion.value ?: return
        if (UbicacionManager.edadMs() > 3_000) return
        if (l.hasAccuracy() && l.accuracy > 20f) return
        val actual = lista.value
        val i = actual.indexOfFirst { distanciaM(it.lat, it.lon, l.latitude, l.longitude) < 20.0 }
        val ahora = System.currentTimeMillis()
        lista.value = if (i >= 0) {
            actual.mapIndexed { k, b -> if (k == i) b.copy(fuerza = max(b.fuerza, fuerza), golpes = b.golpes + 1, ultimo = ahora) else b }
        } else {
            (actual + Bache(l.latitude, l.longitude, fuerza, 1, ahora)).takeLast(MAX)
        }
        if (ahora - ultimoGuardado > 30_000) guardar()
    }

    /** Baches conocidos a menos de 120 m, adelante en tu camino (±25°): aviso por voz una vez. */
    private fun revisarAdelante() {
        val l = UbicacionManager.ubicacion.value ?: return
        val v = UbicacionManager.velocidadKmh()
        if (v < 20f || !l.hasBearing()) return
        val ahora = System.currentTimeMillis()
        val rumbo = l.bearing
        var cerca = -1
        var dMin = Double.MAX_VALUE
        lista.value.forEachIndexed { i, b ->
            val d = distanciaM(l.latitude, l.longitude, b.lat, b.lon)
            if (d in 15.0..120.0 && d < dMin) {
                val hacia = rumbo(l.latitude, l.longitude, b.lat, b.lon)
                val dif = abs(((hacia - rumbo + 540f) % 360f) - 180f)
                if (dif < 25f && ahora - (avisados[i] ?: 0L) > 90_000L) { cerca = i; dMin = d }
            }
        }
        if (cerca < 0) return
        avisados[cerca] = ahora
        val b = lista.value[cerca]
        val fuerte = b.fuerza > umbral() * 1.6f
        Voz.decir(if (fuerte) "Bache fuerte adelante" else "Bache adelante")
        Moto.ultimaNotif.value = "Bache adelante (${dMin.toInt()} m)"
    }

    fun guardar() {
        ultimoGuardado = System.currentTimeMillis()
        val a = JSONArray()
        lista.value.forEach { b ->
            a.put(JSONObject().apply { put("la", b.lat); put("lo", b.lon); put("f", b.fuerza.toDouble()); put("g", b.golpes); put("u", b.ultimo) })
        }
        prefs?.edit()?.putString("lista", a.toString())?.apply()
    }

    fun borrarTodos() { lista.value = emptyList(); avisados.clear(); guardar() }

    fun ponerActivo(on: Boolean) { activo.value = on; prefs?.edit()?.putBoolean("activo", on)?.apply() }
    fun ponerAvisar(on: Boolean) { avisar.value = on; prefs?.edit()?.putBoolean("avisar", on)?.apply() }
    fun ponerMostrar(on: Boolean) { mostrar.value = on; prefs?.edit()?.putBoolean("mostrar", on)?.apply() }
    fun ponerSensibilidad(s: Int) { sensibilidad.value = s.coerceIn(0, 2); prefs?.edit()?.putInt("sensibilidad", sensibilidad.value)?.apply() }

    private fun rumbo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val f1 = lat1 * PI / 180
        val f2 = lat2 * PI / 180
        val dl = (lon2 - lon1) * PI / 180
        val y = sin(dl) * cos(f2)
        val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
        return ((atan2(y, x) * 180 / PI + 360) % 360).toFloat()
    }

    fun distanciaM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
