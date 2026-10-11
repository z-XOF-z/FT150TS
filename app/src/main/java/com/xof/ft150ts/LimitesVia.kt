package com.xof.ft150ts

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Límite de la calle donde vas. [estimado] = la calle no lo tiene en el mapa y se calcula por su tipo. */
data class LimiteVia(val kmh: Int, val estimado: Boolean, val nombre: String, val tipo: String, val lat: Double, val lon: Double)

/**
 * Límite de velocidad por calle con datos de OpenStreetMap (Overpass, gratis).
 * Si la calle no tiene el límite apuntado, lo estima por el tipo de vía:
 * autopista, carretera, avenida, calle...
 */
object LimitesManager {
    val limite = MutableStateFlow<LimiteVia?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var iniciado = false
    private var consultando = false
    private var latC = 0.0
    private var lonC = 0.0
    private var tC = 0L
    private var fallos = 0

    private val SERVIDORES = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    /** Límite aproximado en México según el tipo de vía de OpenStreetMap. */
    private fun estimado(tipo: String): Int = when (tipo) {
        "motorway" -> 110
        "trunk" -> 80
        "primary" -> 60
        "secondary" -> 50
        "tertiary", "unclassified", "road" -> 40
        "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link" -> 40
        "residential" -> 30
        "living_street", "service" -> 20
        else -> 40
    }

    fun nombreTipo(tipo: String): String = when (tipo) {
        "motorway" -> "Autopista"
        "trunk" -> "Carretera"
        "primary" -> "Avenida principal"
        "secondary" -> "Avenida"
        "tertiary" -> "Calle principal"
        "residential" -> "Calle"
        "living_street" -> "Calle peatonal"
        "service" -> "Acceso"
        else -> if (tipo.endsWith("_link")) "Entronque" else "Calle"
    }

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        scope.launch {
            while (isActive) {
                delay(4_000)
                revisar()
            }
        }
    }

    private fun activo(): Boolean = Moto.mostrarLimite.value || (Moto.avisoVel.value && Moto.modoAvisoVel.value == 1)

    private fun revisar() {
        if (consultando) return
        if (!activo() || !BleManager.primerPlano) return
        val u = UbicacionManager.ubicacion.value ?: return
        if (UbicacionManager.edadMs() > 15_000) return          // GPS viejo
        val d = metros(u.latitude, u.longitude, latC, lonC)
        val dt = System.currentTimeMillis() - tC
        val espera = if (fallos > 0) 20_000L * minOf(fallos, 6) else 0L    // si el servidor falla, espera más
        if (dt < espera) return
        // Consulta al moverte ~80 m, o cada minuto si sigues en la misma calle
        if (d > 80.0 || (dt > 60_000 && d > 15.0) || (limite.value == null && dt > 20_000)) {
            val rumbo = if (u.hasBearing() && u.hasSpeed() && u.speed > 2.5f) u.bearing else -1f
            consultar(u.latitude, u.longitude, rumbo)
        }
        // Si te alejaste mucho de donde se midió, ya no es válido
        val l = limite.value
        if (l != null && metros(u.latitude, u.longitude, l.lat, l.lon) > 400.0) limite.value = null
    }

    private fun consultar(lat: Double, lon: Double, rumbo: Float) {
        consultando = true
        latC = lat; lonC = lon; tC = System.currentTimeMillis()
        scope.launch {
            try {
                val texto = withContext(Dispatchers.IO) { descargar(lat, lon) }
                val nuevo = elegir(JSONObject(texto), lat, lon, rumbo)
                if (nuevo != null) limite.value = nuevo
                fallos = 0
            } catch (_: Exception) {
                fallos++
            } finally {
                consultando = false
            }
        }
    }

    private fun descargar(lat: Double, lon: Double): String {
        val q = String.format(
            Locale.US,
            "[out:json][timeout:8];way(around:22,%.6f,%.6f)[highway~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|" +
                "residential|living_street|service|road|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$\"];out tags geom;",
            lat, lon,
        )
        var ultimo: Exception = IOException("sin servidor")
        for (servidor in SERVIDORES) {
            try {
                val con = URL(servidor + "?data=" + URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection
                con.connectTimeout = 8_000
                con.readTimeout = 10_000
                con.setRequestProperty("User-Agent", "FT150TS-XOF (app personal de moto)")
                try {
                    if (con.responseCode != 200) throw IOException("HTTP ${con.responseCode}")
                    return con.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    con.disconnect()
                }
            } catch (e: Exception) {
                ultimo = e
            }
        }
        throw ultimo
    }

    /** Escoge la calle más cercana (y que vaya en tu dirección, si te estás moviendo). */
    private fun elegir(j: JSONObject, lat: Double, lon: Double, rumbo: Float): LimiteVia? {
        val elementos = j.optJSONArray("elements") ?: return null
        val kx = 111_320.0 * cos(lat * PI / 180)
        val ky = 110_540.0
        var mejor: JSONObject? = null
        var mejorPuntaje = Double.MAX_VALUE
        for (i in 0 until elementos.length()) {
            val w = elementos.optJSONObject(i) ?: continue
            val geo = w.optJSONArray("geometry") ?: continue
            var puntaje = Double.MAX_VALUE
            for (k in 1 until geo.length()) {
                val a = geo.optJSONObject(k - 1) ?: continue
                val b = geo.optJSONObject(k) ?: continue
                val ax = (a.optDouble("lon", 0.0) - lon) * kx
                val ay = (a.optDouble("lat", 0.0) - lat) * ky
                val bx = (b.optDouble("lon", 0.0) - lon) * kx
                val by = (b.optDouble("lat", 0.0) - lat) * ky
                var dist = distanciaASegmento(ax, ay, bx, by)
                if (rumbo >= 0f) {
                    // Penaliza las calles que cruzan (en una esquina no agarra la de al lado)
                    val angSeg = Math.toDegrees(atan2(bx - ax, by - ay))
                    val dif = Math.toRadians(angSeg - rumbo)
                    dist += 18.0 * (1.0 - abs(cos(dif)))
                }
                if (dist < puntaje) puntaje = dist
            }
            if (puntaje < mejorPuntaje) { mejorPuntaje = puntaje; mejor = w }
        }
        val w = mejor ?: return null
        val tags = w.optJSONObject("tags") ?: return null
        val tipo = tags.optString("highway", "")
        val nombre = tags.optString("name", "").ifBlank { tags.optString("ref", "") }
        val real = interpretarMaxspeed(tags.optString("maxspeed", ""))
        return LimiteVia(real ?: estimado(tipo), real == null, nombre, tipo, lat, lon)
    }

    /** "60", "60 km/h", "40 mph" -> km/h. Códigos como "MX:urban" no traen número. */
    private fun interpretarMaxspeed(v: String): Int? {
        val m = Regex("(\\d+)\\s*(mph)?").find(v) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val kmh = if (m.groupValues[2].isNotEmpty()) (n * 1.609).roundToInt() else n
        return kmh.takeIf { it in 5..150 }
    }

    private fun distanciaASegmento(ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val largo2 = dx * dx + dy * dy
        val t = if (largo2 <= 0.0) 0.0 else (-(ax * dx + ay * dy) / largo2).coerceIn(0.0, 1.0)
        val px = ax + t * dx
        val py = ay + t * dy
        return sqrt(px * px + py * py)
    }

    private fun metros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val kx = 111_320.0 * cos(lat1 * PI / 180)
        val dx = (lon2 - lon1) * kx
        val dy = (lat2 - lat1) * 110_540.0
        return sqrt(dx * dx + dy * dy)
    }
}
