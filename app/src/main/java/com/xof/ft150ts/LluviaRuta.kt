package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Lluvia adelante en tu camino: a cuántos km y en cuántos minutos llegarías ahí. */
data class AvisoRuta(val km: Int, val minutos: Int, val mm: Float, val probabilidad: Int, val hora: Long)

/**
 * Lluvia en tu ruta: rodando, cada 10 min pregunta a Open-Meteo (gratis, sin llave) por 5 puntos en la dirección
 * en que vas (aquí, 4, 8, 12 y 16 km adelante). Con tu velocidad calcula cuándo pasarías por cada uno y revisa si
 * a esa hora va a llover ahí (pronóstico cada 15 min + probabilidad por hora). Si sí, avisa en el tablero y por voz.
 */
object LluviaRuta {
    val activo = MutableStateFlow(true)
    val aviso = MutableStateFlow<AvisoRuta?>(null)
    val ultimaRevision = MutableStateFlow(0L)

    private val DISTANCIAS = intArrayOf(0, 4, 8, 12, 16)
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private var iniciado = false
    private var ultimaConsulta = 0L
    private var ultimoAviso = 0L

    fun iniciar(c: Context) {
        if (iniciado) return
        iniciado = true
        val p = c.applicationContext.getSharedPreferences("lluviaRuta", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", true)
        scope.launch {
            while (isActive) {
                delay(60_000)
                revisar(false)
            }
        }
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on
        prefs?.edit()?.putBoolean("activo", on)?.apply()
        if (!on) aviso.value = null
    }

    /** [forzar]: botón "Revisar ahora" (también parado: solo revisa donde estás). */
    fun revisar(forzar: Boolean) {
        if (!activo.value && !forzar) return
        val ahora = System.currentTimeMillis()
        if (!forzar && ahora - ultimaConsulta < 10 * 60_000L) return
        val l = UbicacionManager.ubicacion.value ?: return
        val vel = UbicacionManager.velocidadKmh()
        val rodando = vel > 15f && l.hasBearing()
        if (!forzar && !(rodando && (Energia.enMoto.value || BleManager.primerPlano))) return
        ultimaConsulta = ahora
        val rumbo = l.bearing.toDouble()
        val dist = if (rodando) DISTANCIAS else intArrayOf(0)
        val puntos = dist.map { km -> destino(l.latitude, l.longitude, rumbo, km.toDouble()) }
        scope.launch {
            try {
                val texto = withContext(Dispatchers.IO) { descargar(puntos) }
                interpretar(texto, dist, if (rodando) vel else 0f)
                ultimaRevision.value = System.currentTimeMillis()
            } catch (_: Exception) { }
        }
    }

    private fun interpretar(texto: String, dist: IntArray, vel: Float) {
        val raiz = texto.trim()
        val lugares: List<JSONObject> = if (raiz.startsWith("[")) {
            val a = JSONArray(raiz); List(a.length()) { a.getJSONObject(it) }
        } else listOf(JSONObject(raiz))
        var encontrado: AvisoRuta? = null
        for ((i, o) in lugares.withIndex()) {
            val km = dist.getOrElse(i) { 0 }
            // minutos para llegar a ese punto con tu velocidad
            val llegar = if (vel > 1f) (km / vel * 60f).roundToInt() else 0
            val mm = o.optJSONObject("minutely_15")?.optJSONArray("precipitation")
            val prob = o.optJSONObject("hourly")?.optJSONArray("precipitation_probability")
            val ranura = (llegar / 15).coerceAtMost(7)
            val lluvia = maxOf(mm.num(ranura), mm.num(ranura + 1))
            val p = prob.num(llegar / 60).roundToInt()
            if (lluvia >= 0.2 || p >= 70) {
                encontrado = AvisoRuta(km, llegar, lluvia.toFloat(), p, System.currentTimeMillis())
                break
            }
        }
        aviso.value = encontrado
        val a = encontrado ?: return
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoAviso < 30 * 60_000L) return
        ultimoAviso = ahora
        if (a.km == 0) Moto.aviso("Lluvia aquí", "Ojo, está lloviendo o va a llover donde estás")
        else Moto.aviso("Lluvia adelante ~${a.km} km", "Ojo, adelante a unos ${a.km} kilómetros va a llover. Llegarías en unos ${a.minutos} minutos")
    }

    private fun JSONArray?.num(i: Int): Double {
        if (this == null || i < 0 || i >= length()) return 0.0
        val v = optDouble(i, 0.0)
        return if (v.isNaN()) 0.0 else v
    }

    private fun descargar(puntos: List<Pair<Double, Double>>): String {
        val lats = puntos.joinToString(",") { String.format(Locale.US, "%.4f", it.first) }
        val lons = puntos.joinToString(",") { String.format(Locale.US, "%.4f", it.second) }
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons" +
                "&minutely_15=precipitation&forecast_minutely_15=8&hourly=precipitation_probability&forecast_hours=3&timezone=auto"
        )
        val con = url.openConnection() as HttpURLConnection
        con.connectTimeout = 10_000
        con.readTimeout = 10_000
        con.setRequestProperty("User-Agent", "FT150TS-XOF")
        try {
            if (con.responseCode != 200) throw java.io.IOException("HTTP ${con.responseCode}")
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }

    /** Punto a [km] kilómetros en la dirección [rumbo] (grados). */
    private fun destino(lat: Double, lon: Double, rumbo: Double, km: Double): Pair<Double, Double> {
        if (km <= 0.0) return lat to lon
        val r = 6371.0
        val d = km / r
        val b = rumbo * PI / 180.0
        val la1 = lat * PI / 180.0
        val lo1 = lon * PI / 180.0
        val la2 = asin(sin(la1) * cos(d) + cos(la1) * sin(d) * cos(b))
        val lo2 = lo1 + atan2(sin(b) * sin(d) * cos(la1), cos(d) - sin(la1) * sin(la2))
        return la2 * 180.0 / PI to lo2 * 180.0 / PI
    }
}
