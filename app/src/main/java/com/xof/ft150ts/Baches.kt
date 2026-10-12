package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
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
    private val avisados = HashMap<Long, Long>()   // llave del bache (su lugar) -> cuándo se avisó
    /** Dónde y cuándo se dio cada aviso reciente (para no repetir en la misma cuadra). */
    private val avisosRecientes = ArrayDeque<Triple<Double, Double, Long>>()
    private var ultimoGuardado = 0L
    private const val MAX = 800
    /** Una cuadra, más o menos: dentro de esto solo hay un aviso. */
    private const val CUADRA_M = 100.0
    /** En un tramo así de largo, máximo dos avisos. */
    private const val TRAMO_M = 250.0
    /** Qué tan lejos se buscan baches adelante. */
    private const val ALCANCE_M = 150.0

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
                // Parada (reposo) o dormida no hay nada que avisar
                if (avisar.value && !Reposo.enReposo.value && !ModoSueno.dormido.value) revisarAdelante()
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

    /** Llave de un bache por su lugar (no por su posición en la lista, que se recorre al borrar los viejos). */
    private fun llave(b: Bache): Long = ((b.lat * 1e5).roundToLong() shl 32) xor ((b.lon * 1e5).roundToLong() and 0xFFFFFFFFL)

    /**
     * Baches conocidos adelante en tu camino (±25°, hasta 150 m). Se avisan **por cuadra**, no uno por uno:
     * un solo aviso que dice cuántos hay ("Cuadra con baches, 4 adelante"), máximo uno cada ~100 m
     * y máximo dos en un tramo de 250 m. Los que ya se contaron no se vuelven a avisar en 5 min.
     */
    private fun revisarAdelante() {
        val l = UbicacionManager.ubicacion.value ?: return
        val v = UbicacionManager.velocidadKmh()
        if (v < 20f || !l.hasBearing()) return
        val ahora = System.currentTimeMillis()
        val lat = l.latitude
        val lon = l.longitude
        // Se olvidan los avisos de hace más de 3 min
        while (avisosRecientes.isNotEmpty() && ahora - avisosRecientes.first().third > 180_000L) avisosRecientes.removeFirst()
        // En esta cuadra ya se avisó (a menos de 100 m o hace menos de 12 s): nada
        avisosRecientes.lastOrNull()?.let { u ->
            if (ahora - u.third < 12_000L || distanciaM(u.first, u.second, lat, lon) < CUADRA_M) return
        }
        // Ya van dos avisos en este tramo
        if (avisosRecientes.count { distanciaM(it.first, it.second, lat, lon) < TRAMO_M } >= 2) return

        val rumbo = l.bearing
        // Recuadro rápido (sin trigonometría) antes de la distancia exacta: así revisar 800 baches cuesta poco
        val dLat = ALCANCE_M / 111_000.0
        val dLon = dLat / max(0.2, cos(lat * PI / 180))
        var n = 0
        var fuertes = 0
        var dMin = Double.MAX_VALUE
        val grupo = ArrayList<Long>()
        for (b in lista.value) {
            if (abs(b.lat - lat) > dLat || abs(b.lon - lon) > dLon) continue
            val d = distanciaM(lat, lon, b.lat, b.lon)
            if (d < 15.0 || d > ALCANCE_M) continue
            val dif = abs(((rumbo(lat, lon, b.lat, b.lon) - rumbo + 540f) % 360f) - 180f)
            if (dif >= 25f) continue
            val k = llave(b)
            if (ahora - (avisados[k] ?: 0L) < 300_000L) continue
            n++
            grupo += k
            if (b.fuerza > umbral() * 1.6f) fuertes++
            if (d < dMin) dMin = d
        }
        if (n == 0) return
        grupo.forEach { avisados[it] = ahora }
        avisosRecientes.addLast(Triple(lat, lon, ahora))
        val fuerte = when (fuertes) { 0 -> ""; 1 -> ", uno fuerte"; else -> ", $fuertes fuertes" }
        val frase = when {
            n >= 3 -> "Cuadra con baches, $n adelante$fuerte"
            n == 2 -> "Dos baches adelante$fuerte"
            fuertes > 0 -> "Bache fuerte adelante"
            else -> "Bache adelante"
        }
        Voz.decir(frase)
        Moto.ultimaNotif.value = "$frase (${dMin.toInt()} m)"
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
