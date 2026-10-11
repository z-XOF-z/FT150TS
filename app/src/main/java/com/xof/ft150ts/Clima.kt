package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
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

data class HoraClima(val hora: Int, val temp: Int, val lluvia: Int, val codigo: Int)

data class Clima(
    val temp: Int,
    val sensacion: Int,
    val humedad: Int,
    val viento: Int,          // km/h
    val codigo: Int,          // código WMO (0 despejado ... 99 tormenta)
    val esDia: Boolean,
    val max: Int,
    val min: Int,
    val lluviaProx: Int,      // % máximo de lluvia en las próximas 3 horas
    val lluviaEnHoras: Int,   // en cuántas horas es probable que llueva (>= 50 %); -1 = no
    val horas: List<HoraClima>,
    val ciudad: String,
    val hora: Long,           // cuándo se descargó
    // Para las alertas de clima severo (próximas 6 horas)
    val rachas: Int = 0,          // rachas de viento ahora, km/h
    val rachaMax: Int = 0,        // racha máxima, km/h
    val lluviaMm: Float = 0f,     // lluvia máxima en una hora, mm
    val tormentaEn: Int = -1,     // en cuántas horas hay tormenta eléctrica; -1 = no
    val granizo: Boolean = false,
    val nieblaEn: Int = -1,
    val uvMax: Int = 0,           // índice UV máximo de hoy
    val sensMax: Int = 0,         // sensación térmica máxima de hoy
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

enum class IconoClima { SOL, LUNA, SOL_NUBE, LUNA_NUBE, NUBE, NIEBLA, LLUVIA, TORMENTA, NIEVE }

/**
 * Clima con la ubicación del teléfono. Usa Open-Meteo (gratis, sin llave).
 * Se actualiza cada 15 min solo con la app en pantalla, y guarda el último
 * para mostrarlo al instante aunque no haya internet.
 */
object ClimaManager {
    val clima = MutableStateFlow<Clima?>(null)
    val cargando = MutableStateFlow(false)
    val error = MutableStateFlow("")

    private lateinit var ctx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var latCarga = 0.0
    private var lonCarga = 0.0
    private var avisoLluvia = 0L
    private const val CADA_MS = 15 * 60_000L

    private fun prefs(): SharedPreferences = ctx.getSharedPreferences("clima", Context.MODE_PRIVATE)

    fun iniciar(c: Context) {
        ctx = c.applicationContext
        val p = prefs()
        latCarga = p.getString("lat", null)?.toDoubleOrNull() ?: 0.0
        lonCarga = p.getString("lon", null)?.toDoubleOrNull() ?: 0.0
        val json = p.getString("json", null)
        if (json != null) {
            try { clima.value = interpretar(JSONObject(json), p.getString("ciudad", "") ?: "", p.getLong("hora", 0L)) } catch (_: Exception) { }
        }
        scope.launch {
            delay(4_000)
            while (isActive) {
                revisar(false)
                delay(60_000)
            }
        }
    }

    /** Botón "Actualizar". */
    fun actualizar() = revisar(true)

    private fun revisar(forzar: Boolean) {
        if (!::ctx.isInitialized || cargando.value) return
        if (!forzar && !BleManager.primerPlano && !(Energia.segundoPlano.value && AlertasManager.activo.value)) return
        val pos = UbicacionManager.posicionActual()
        if (pos == null) {
            if (forzar) error.value = "Esperando la ubicación del teléfono…"
            return
        }
        val c = clima.value
        val edad = if (c == null) Long.MAX_VALUE else System.currentTimeMillis() - c.hora
        // En segundo plano solo se actualiza (cada 30 min) si la app está activa y las alertas prendidas
        if (!forzar && !BleManager.primerPlano && edad < 2 * CADA_MS) return
        val viejo = edad > CADA_MS
        val lejos = distanciaKm(pos.first, pos.second, latCarga, lonCarga) > 8.0
        if (forzar || viejo || lejos) cargar(pos.first, pos.second)
    }

    private fun cargar(lat: Double, lon: Double) {
        cargando.value = true
        scope.launch {
            try {
                val texto = withContext(Dispatchers.IO) { descargar(lat, lon) }
                val anterior = clima.value?.ciudad ?: ""
                val ciudad = if (anterior.isNotBlank() && distanciaKm(lat, lon, latCarga, lonCarga) < 5.0) anterior
                else withContext(Dispatchers.IO) { buscarCiudad(lat, lon) }.ifBlank { anterior }
                val ahora = System.currentTimeMillis()
                val nuevo = interpretar(JSONObject(texto), ciudad, ahora)
                clima.value = nuevo
                error.value = ""
                latCarga = lat
                lonCarga = lon
                prefs().edit().putString("json", texto).putString("ciudad", ciudad).putLong("hora", ahora)
                    .putString("lat", lat.toString()).putString("lon", lon.toString()).apply()
                avisarLluvia(nuevo)
            } catch (e: Exception) {
                error.value = "Sin conexión para el clima"
            } finally {
                cargando.value = false
            }
        }
    }

    private fun descargar(lat: Double, lon: Double): String {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,wind_gusts_10m" +
                    "&hourly=temperature_2m,precipitation_probability,weather_code,wind_gusts_10m,precipitation" +
                    "&daily=temperature_2m_max,temperature_2m_min,apparent_temperature_max,uv_index_max" +
                    "&timezone=auto&forecast_days=2",
                lat, lon,
            )
        )
        val con = url.openConnection() as HttpURLConnection
        con.connectTimeout = 10_000
        con.readTimeout = 10_000
        con.setRequestProperty("User-Agent", "FT150TS-XOF")
        try {
            if (con.responseCode != 200) throw IOException("HTTP ${con.responseCode}")
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun buscarCiudad(lat: Double, lon: Double): String = try {
        if (!Geocoder.isPresent()) ""
        else {
            val a = Geocoder(ctx, Locale("es", "MX")).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: ""
        }
    } catch (_: Exception) { "" }

    private fun JSONArray?.num(i: Int): Double {
        if (this == null || i < 0 || i >= length()) return 0.0
        val d = optDouble(i, 0.0)
        return if (d.isNaN()) 0.0 else d
    }

    private fun interpretar(j: JSONObject, ciudad: String, hora: Long): Clima {
        val cur = j.getJSONObject("current")
        val h = j.getJSONObject("hourly")
        val tiempos = h.getJSONArray("time")
        val temps = h.optJSONArray("temperature_2m")
        val probs = h.optJSONArray("precipitation_probability")
        val codigos = h.optJSONArray("weather_code")

        // Hora actual en la zona horaria del lugar (así las próximas horas se recorren solas)
        val zona = SimpleTimeZone(j.optInt("utc_offset_seconds", 0) * 1000, "lugar")
        val ahora = SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.US).apply { timeZone = zona }.format(Date())
        var idx = -1
        for (i in 0 until tiempos.length()) if (tiempos.optString(i).startsWith(ahora)) { idx = i; break }
        if (idx < 0) {
            val pref = cur.optString("time").take(13)
            for (i in 0 until tiempos.length()) if (tiempos.optString(i).startsWith(pref)) { idx = i; break }
        }
        if (idx < 0) idx = 0

        // Si el dato guardado ya es viejo, usa el pronóstico de esta hora
        val viejo = System.currentTimeMillis() - hora > 90 * 60_000L
        val temp = if (viejo) temps.num(idx) else cur.optDouble("temperature_2m", temps.num(idx))
        val codigo = if (viejo) codigos.num(idx).toInt() else cur.optInt("weather_code", codigos.num(idx).toInt())
        val horaLocal = ahora.takeLast(2).toIntOrNull() ?: 12
        val esDia = if (viejo) horaLocal in 7..18 else cur.optInt("is_day", 1) == 1

        val horas = (1..6).mapNotNull { k ->
            val i = idx + k
            if (i >= tiempos.length()) null
            else HoraClima(
                hora = tiempos.optString(i).drop(11).take(2).toIntOrNull() ?: 0,
                temp = temps.num(i).roundToInt(),
                lluvia = probs.num(i).roundToInt(),
                codigo = codigos.num(i).toInt(),
            )
        }
        var lluviaProx = 0
        for (k in 0..3) lluviaProx = maxOf(lluviaProx, probs.num(idx + k).roundToInt())
        var lluviaEn = -1
        for (k in 0..6) if (probs.num(idx + k) >= 50.0) { lluviaEn = k; break }

        // Clima severo en las próximas 6 horas
        val rachasH = h.optJSONArray("wind_gusts_10m")
        val lluviaH = h.optJSONArray("precipitation")
        var rachaMax = 0.0
        var lluviaMm = 0.0
        var tormentaEn = -1
        var granizo = false
        var nieblaEn = -1
        for (k in 0..6) {
            val i = idx + k
            if (i >= tiempos.length()) break
            rachaMax = maxOf(rachaMax, rachasH.num(i))
            lluviaMm = maxOf(lluviaMm, lluviaH.num(i))
            val cod = codigos.num(i).toInt()
            if (cod in 95..99 && tormentaEn < 0) tormentaEn = k
            if (cod == 96 || cod == 99) granizo = true
            if ((cod == 45 || cod == 48) && nieblaEn < 0 && k <= 3) nieblaEn = k
        }
        val rachasAhora = if (viejo) rachasH.num(idx) else cur.optDouble("wind_gusts_10m", rachasH.num(idx))

        val d = j.optJSONObject("daily")
        val dias = d?.optJSONArray("time")
        var di = 0
        if (dias != null) for (i in 0 until dias.length()) if (ahora.startsWith(dias.optString(i))) { di = i; break }

        return Clima(
            temp = temp.roundToInt(),
            sensacion = cur.optDouble("apparent_temperature", temp).let { if (it.isNaN()) temp else it }.roundToInt(),
            humedad = cur.optInt("relative_humidity_2m", 0),
            viento = cur.optDouble("wind_speed_10m", 0.0).let { if (it.isNaN()) 0.0 else it }.roundToInt(),
            codigo = codigo,
            esDia = esDia,
            max = d?.optJSONArray("temperature_2m_max").num(di).roundToInt(),
            min = d?.optJSONArray("temperature_2m_min").num(di).roundToInt(),
            lluviaProx = lluviaProx,
            lluviaEnHoras = lluviaEn,
            horas = horas,
            ciudad = ciudad,
            hora = hora,
            rachas = rachasAhora.let { if (it.isNaN()) 0.0 else it }.roundToInt(),
            rachaMax = rachaMax.roundToInt(),
            lluviaMm = lluviaMm.toFloat(),
            tormentaEn = tormentaEn,
            granizo = granizo,
            nieblaEn = nieblaEn,
            uvMax = d?.optJSONArray("uv_index_max").num(di).roundToInt(),
            sensMax = d?.optJSONArray("apparent_temperature_max").num(di).roundToInt(),
            lat = j.optDouble("latitude", 0.0),
            lon = j.optDouble("longitude", 0.0),
        )
    }

    private fun avisarLluvia(c: Clima) {
        val ahora = System.currentTimeMillis()
        if (c.lluviaEnHoras !in 0..2 || c.lluviaProx < 60 || ahora - avisoLluvia < 2 * 3600_000L) return
        avisoLluvia = ahora
        val (pantalla, voz) = if (c.lluviaEnHoras == 0) "Lluvia probable ya" to "Cuidado, es probable que llueva pronto"
        else "Lluvia en ~${c.lluviaEnHoras} h" to "Podría llover en unas ${c.lluviaEnHoras} horas"
        Moto.aviso(pantalla, voz)
    }

    /** Lo dice en voz alta con la voz del teléfono. */
    fun decir() {
        val c = clima.value
        if (c == null) { Voz.decir("Todavía no tengo el clima"); return }
        val donde = if (c.ciudad.isNotBlank()) "En ${c.ciudad}" else "Aquí"
        val sb = StringBuilder("$donde hay ${c.temp} grados y ${describir(c.codigo).lowercase()}. ")
        if (c.sensacion != c.temp) sb.append("Se siente como ${c.sensacion}. ")
        sb.append("Máxima de ${c.max} y mínima de ${c.min}. ")
        sb.append("Probabilidad de lluvia, ${c.lluviaProx} por ciento.")
        when {
            c.lluviaEnHoras == 0 -> sb.append(" Es probable que llueva pronto.")
            c.lluviaEnHoras > 0 -> sb.append(" Podría llover en unas ${c.lluviaEnHoras} horas.")
        }
        Voz.decir(sb.toString())
    }

    /** Lo manda a la fila de avisos del LCD de la moto (20 caracteres). */
    fun mandarAMoto(): Boolean {
        val c = clima.value ?: return false
        return BleManager.enviarNotif("${c.temp}C ${describirCorto(c.codigo)} ${c.lluviaProx}%".take(20))
    }

    private fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** Descripción en español del código WMO. */
fun describir(codigo: Int): String = when (codigo) {
    0 -> "Despejado"
    1 -> "Casi despejado"
    2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Niebla"
    51, 53, 55 -> "Llovizna"
    56, 57 -> "Llovizna helada"
    61 -> "Lluvia ligera"
    63 -> "Lluvia"
    65 -> "Lluvia fuerte"
    66, 67 -> "Lluvia helada"
    71, 73, 75, 77, 85, 86 -> "Nieve"
    80 -> "Chubascos ligeros"
    81 -> "Chubascos"
    82 -> "Chubascos fuertes"
    95 -> "Tormenta"
    96, 99 -> "Tormenta con granizo"
    else -> "—"
}

fun describirCorto(codigo: Int): String = when (codigo) {
    0, 1 -> "Despejado"
    2 -> "Medio nub"
    3 -> "Nublado"
    45, 48 -> "Niebla"
    in 51..67, in 80..82 -> "Lluvia"
    in 71..77, 85, 86 -> "Nieve"
    in 95..99 -> "Tormenta"
    else -> ""
}

fun iconoClima(codigo: Int, esDia: Boolean): IconoClima = when (codigo) {
    0, 1 -> if (esDia) IconoClima.SOL else IconoClima.LUNA
    2 -> if (esDia) IconoClima.SOL_NUBE else IconoClima.LUNA_NUBE
    3 -> IconoClima.NUBE
    45, 48 -> IconoClima.NIEBLA
    in 51..67, in 80..82 -> IconoClima.LLUVIA
    in 71..77, 85, 86 -> IconoClima.NIEVE
    in 95..99 -> IconoClima.TORMENTA
    else -> IconoClima.NUBE
}
