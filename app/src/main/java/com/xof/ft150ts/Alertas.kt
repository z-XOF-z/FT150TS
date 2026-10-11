package com.xof.ft150ts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
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
import org.json.JSONObject

enum class TipoAlerta { HURACAN, TORMENTA, GRANIZO, VIENTO, LLUVIA, CALOR, UV, NIEBLA, SISMO }

/** [nivel]: 1 = para que lo sepas, 2 = precaución, 3 = peligro. */
data class Alerta(
    val tipo: TipoAlerta,
    val nivel: Int,
    val titulo: String,
    val detalle: String,
    val corto: String,        // para el LCD de la moto (20 caracteres)
    val voz: String,
    val clave: String,        // identifica la alerta (para no repetir el aviso)
)

/** Ciclón tropical activo (Centro Nacional de Huracanes de EE. UU., cubre Atlántico y Pacífico mexicano). */
data class Ciclon(
    val id: String,
    val nombre: String,
    val clase: String,        // TD, TS, HU, STS, STD, PTC, PC
    val vientoKt: Int,
    val presion: Int,
    val lat: Double,
    val lon: Double,
    val rumbo: Int,           // hacia dónde va, grados
    val velMph: Int,
    val actualizado: Long,
)

/** Sismo reportado por el USGS (ya ocurrió: no es alerta temprana). */
data class Sismo(val mag: Double, val lugar: String, val lat: Double, val lon: Double, val tiempo: Long)

/**
 * Alertas: huracanes y tormentas tropicales cerca (NHC/NOAA), clima severo de las próximas
 * horas (Open-Meteo: tormenta eléctrica, granizo, rachas, lluvia intensa, calor, UV, niebla)
 * y sismos reportados cerca (USGS). Avisa en el tablero, en el LCD, por voz y con notificación.
 * No sustituye los avisos oficiales de Protección Civil o del SMN.
 */
object AlertasManager {
    val alertas = MutableStateFlow<List<Alerta>>(emptyList())
    val ciclones = MutableStateFlow<List<Ciclon>>(emptyList())
    val sismos = MutableStateFlow<List<Sismo>>(emptyList())
    val revisado = MutableStateFlow(0L)

    // Ajustes (Ajustes > Avisos)
    val activo = MutableStateFlow(true)
    val conSismos = MutableStateFlow(true)

    private lateinit var ctx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var descargando = false
    private val avisadas = HashMap<String, Pair<Int, Long>>()   // clave -> (nivel, cuándo)
    private const val CANAL = "alertas"

    private fun prefs(): SharedPreferences = ctx.getSharedPreferences("alertas", Context.MODE_PRIVATE)

    fun iniciar(c: Context) {
        ctx = c.applicationContext
        val p = prefs()
        activo.value = p.getBoolean("activo", true)
        conSismos.value = p.getBoolean("sismos", true)
        // Lo último descargado, para mostrarlo al instante
        try { p.getString("nhc", null)?.let { ciclones.value = leerCiclones(it) } } catch (_: Exception) { }
        try { p.getString("usgs", null)?.let { sismos.value = leerSismos(it) } } catch (_: Exception) { }
        revisado.value = p.getLong("revisado", 0L)
        // Avisos ya dados (para no repetirlos cada vez que abres la app)
        (p.getString("avisadas", "") ?: "").split(';').forEach { t ->
            val partes = t.split('|')
            if (partes.size == 3) avisadas[partes[0]] = (partes[1].toIntOrNull() ?: 0) to (partes[2].toLongOrNull() ?: 0L)
        }
        scope.launch {
            delay(6_000)
            while (isActive) {
                revisar(false)
                recalcular()
                delay(60_000)
            }
        }
        scope.launch { ClimaManager.clima.collect { recalcular() } }
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on
        prefs().edit().putBoolean("activo", on).apply()
        if (on) revisar(true)
        recalcular()
    }

    fun ponerSismos(on: Boolean) {
        conSismos.value = on
        prefs().edit().putBoolean("sismos", on).apply()
        recalcular()
    }

    /** Botón "Actualizar" (también lo usa el clima). */
    fun actualizar() = revisar(true)

    /** En pantalla cada 30 min; en segundo plano (con "App activa") cada hora. */
    private fun revisar(forzar: Boolean) {
        if (!::ctx.isInitialized || !activo.value || descargando) return
        val edad = System.currentTimeMillis() - revisado.value
        val cada = if (BleManager.primerPlano) 30 * 60_000L else 60 * 60_000L
        if (!forzar && edad < cada) return
        if (!forzar && !BleManager.primerPlano && !Energia.segundoPlano.value) return
        descargando = true
        scope.launch {
            try {
                val nhc = withContext(Dispatchers.IO) { descargar("https://www.nhc.noaa.gov/CurrentStorms.json") }
                ciclones.value = leerCiclones(nhc)
                prefs().edit().putString("nhc", nhc).apply()
                if (conSismos.value) {
                    try {
                        val usgs = withContext(Dispatchers.IO) {
                            descargar("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_day.geojson")
                        }
                        sismos.value = leerSismos(usgs)
                        prefs().edit().putString("usgs", usgs).apply()
                    } catch (_: Exception) { }
                }
                val ahora = System.currentTimeMillis()
                revisado.value = ahora
                prefs().edit().putLong("revisado", ahora).apply()
            } catch (_: Exception) {
            } finally {
                descargando = false
                recalcular()
            }
        }
    }

    private fun descargar(url: String): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 12_000
        con.readTimeout = 15_000
        con.setRequestProperty("User-Agent", "FT150TS-XOF (app personal)")
        try {
            if (con.responseCode != 200) throw IOException("HTTP ${con.responseCode}")
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }

    private fun leerCiclones(texto: String): List<Ciclon> {
        val a = JSONObject(texto).optJSONArray("activeStorms") ?: return emptyList()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optDouble("latitudeNumeric", Double.NaN)
            val lon = o.optDouble("longitudeNumeric", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
            Ciclon(
                id = o.optString("id"),
                nombre = o.optString("name"),
                clase = o.optString("classification"),
                vientoKt = o.optString("intensity").toIntOrNull() ?: o.optInt("intensity", 0),
                presion = o.optString("pressure").toIntOrNull() ?: o.optInt("pressure", 0),
                lat = lat, lon = lon,
                rumbo = o.optInt("movementDir", -1),
                velMph = o.optInt("movementSpeed", -1),
                actualizado = try { iso.parse(o.optString("lastUpdate").take(19))?.time ?: 0L } catch (_: Exception) { 0L },
            )
        }
    }

    private fun leerSismos(texto: String): List<Sismo> {
        val f = JSONObject(texto).optJSONArray("features") ?: return emptyList()
        return (0 until f.length()).mapNotNull { i ->
            val o = f.optJSONObject(i) ?: return@mapNotNull null
            val p = o.optJSONObject("properties") ?: return@mapNotNull null
            val c = o.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            Sismo(p.optDouble("mag", 0.0), p.optString("place"), c.optDouble(1, 0.0), c.optDouble(0, 0.0), p.optLong("time", 0L))
        }
    }

    /** Dónde está el teléfono (o donde se pidió el clima la última vez). */
    private fun posicion(): Pair<Double, Double>? =
        UbicacionManager.posicionActual() ?: ClimaManager.clima.value?.let { c -> if (c.lat != 0.0) c.lat to c.lon else null }

    /** Arma la lista de alertas con lo último que se sabe y avisa de las nuevas. */
    fun recalcular() {
        if (!::ctx.isInitialized) return
        if (!activo.value) { alertas.value = emptyList(); return }
        val lista = mutableListOf<Alerta>()
        val pos = posicion()
        if (pos != null) {
            ciclones.value.forEach { c -> alertaCiclon(c, pos)?.let { lista += it } }
            if (conSismos.value) {
                val hace12h = System.currentTimeMillis() - 12 * 3600_000L
                sismos.value.filter { it.tiempo > hace12h }.forEach { s ->
                    val d = distanciaKm(pos.first, pos.second, s.lat, s.lon)
                    if (d <= 400) {
                        val m = fmtUno(s.mag)
                        val hace = ((System.currentTimeMillis() - s.tiempo) / 60_000L).toInt()
                        val cuando = if (hace < 60) "hace $hace min" else "hace ${hace / 60} h"
                        lista += Alerta(
                            TipoAlerta.SISMO, if (s.mag >= 6.0) 2 else 1, "Sismo M$m reportado",
                            "A ${d.roundToInt()} km · $cuando · ${s.lugar}", "Sismo M$m ${d.roundToInt()}km",
                            "Se reportó un sismo de magnitud $m a ${d.roundToInt()} kilómetros", "sismo-${s.tiempo}",
                        )
                    }
                }
            }
        }
        ClimaManager.clima.value?.let { c ->
            // Solo si el pronóstico es reciente (menos de 3 h)
            if (System.currentTimeMillis() - c.hora < 3 * 3600_000L) lista += alertasClima(c)
        }
        val ordenada = lista.sortedWith(compareByDescending<Alerta> { it.nivel }.thenBy { it.tipo.ordinal })
        alertas.value = ordenada
        avisarNuevas(ordenada)
    }

    private fun alertaCiclon(c: Ciclon, pos: Pair<Double, Double>): Alerta? {
        val d = distanciaKm(pos.first, pos.second, c.lat, c.lon)
        val fuerte = c.clase == "HU" || c.clase == "TS" || c.clase == "STS" || c.clase == "TY"
        val nivel = when {
            d <= 300 && fuerte -> 3
            d <= 300 -> 2
            d <= 800 && fuerte -> 2
            d <= 800 -> 1
            d <= 1500 && fuerte -> 1
            else -> return null
        }
        val tipo = nombreClase(c)
        val viento = (c.vientoKt * 1.852).roundToInt()
        val haciaMi = rumboGrados(c.lat, c.lon, pos.first, pos.second)
        val mov = if (c.rumbo >= 0 && c.velMph >= 0) {
            val dif = abs(((c.rumbo - haciaMi + 540) % 360) - 180)
            val tendencia = when { dif <= 60 -> " · se acerca"; dif >= 120 -> " · se aleja"; else -> " · pasa de lado" }
            " · va al ${cardinal(c.rumbo.toDouble())} a ${(c.velMph * 1.609).roundToInt()} km/h$tendencia"
        } else ""
        val dondeEsta = cardinal(rumboGrados(pos.first, pos.second, c.lat, c.lon).toDouble())
        return Alerta(
            TipoAlerta.HURACAN, nivel, "$tipo ${c.nombre}",
            "A ${d.roundToInt()} km al $dondeEsta · vientos de $viento km/h$mov",
            "${tipoCorto(c)} ${c.nombre} ${d.roundToInt()}km",
            "$tipo ${c.nombre} a ${d.roundToInt()} kilómetros, con vientos de $viento kilómetros por hora",
            "ciclon-${c.id}",
        )
    }

    private fun alertasClima(c: Clima): List<Alerta> {
        val l = mutableListOf<Alerta>()
        fun cuando(h: Int) = if (h <= 0) "ahora" else "en ~$h h"
        if (c.granizo && c.tormentaEn >= 0) {
            l += Alerta(TipoAlerta.GRANIZO, 3, "Tormenta con granizo", "Pronosticada ${cuando(c.tormentaEn)}. Mejor no salgas en la moto.",
                "Granizo ${cuando(c.tormentaEn)}", "Cuidado, tormenta con granizo ${cuando(c.tormentaEn)}", "granizo")
        } else if (c.tormentaEn >= 0) {
            l += Alerta(TipoAlerta.TORMENTA, 2, "Tormenta eléctrica", "Pronosticada ${cuando(c.tormentaEn)}.",
                "Tormenta ${cuando(c.tormentaEn)}", "Tormenta eléctrica ${cuando(c.tormentaEn)}", "tormenta")
        }
        val racha = maxOf(c.rachas, c.rachaMax)
        if (racha >= 50) {
            val n = if (racha >= 70) 3 else 2
            l += Alerta(TipoAlerta.VIENTO, n, if (n == 3) "Rachas muy fuertes" else "Rachas fuertes",
                "Hasta $racha km/h en las próximas horas. Cuidado en puentes y carretera abierta.",
                "Rachas $racha km/h", "Rachas de viento de hasta $racha kilómetros por hora", "viento")
        }
        if (c.lluviaMm >= 7.6f) {
            val n = if (c.lluviaMm >= 20f) 3 else 2
            val mm = c.lluviaMm.roundToInt()
            l += Alerta(TipoAlerta.LLUVIA, n, if (n == 3) "Lluvia torrencial" else "Lluvia intensa",
                "Hasta $mm mm en una hora: charcos y poca visibilidad.", "Lluvia fuerte ${mm}mm",
                "Lluvia ${if (n == 3) "torrencial" else "intensa"} en las próximas horas", "lluvia")
        }
        if (c.sensMax >= 36) {
            val n = if (c.sensMax >= 40) 2 else 1
            l += Alerta(TipoAlerta.CALOR, n, if (n == 2) "Calor extremo" else "Mucho calor",
                "Sensación de hasta ${c.sensMax}° hoy. Hidrátate; el motor también se calienta más.",
                "Calor ${c.sensMax}C", "Calor ${if (n == 2) "extremo" else "fuerte"}, sensación de ${c.sensMax} grados", "calor")
        }
        if (c.uvMax >= 8) {
            l += Alerta(TipoAlerta.UV, 1, if (c.uvMax >= 11) "UV extremo" else "UV muy alto",
                "Índice ${c.uvMax}: usa manga larga o bloqueador.", "UV ${c.uvMax}", "Índice ultravioleta de ${c.uvMax}", "uv")
        }
        if (c.nieblaEn >= 0) {
            l += Alerta(TipoAlerta.NIEBLA, 1, "Niebla", "Baja visibilidad ${cuando(c.nieblaEn)}. Prende las luces.",
                "Niebla ${cuando(c.nieblaEn)}", "Niebla ${cuando(c.nieblaEn)}, prende las luces", "niebla")
        }
        return l
    }

    /** Nivel 2 o más: aviso en el tablero/LCD/voz y notificación. Se repite si sube de nivel o pasan 6 h. */
    private fun avisarNuevas(lista: List<Alerta>) {
        val ahora = System.currentTimeMillis()
        for (a in lista) {
            if (a.nivel < 2) continue
            val antes = avisadas[a.clave]
            if (antes != null && antes.first >= a.nivel && ahora - antes.second < 6 * 3600_000L) continue
            avisadas[a.clave] = a.nivel to ahora
            Moto.aviso(a.corto, a.voz)
            notificar(a)
            guardarAvisadas()
        }
    }

    private fun guardarAvisadas() {
        val limite = System.currentTimeMillis() - 2 * 24 * 3600_000L
        avisadas.entries.removeAll { it.value.second < limite }
        prefs().edit().putString("avisadas", avisadas.entries.joinToString(";") { "${it.key}|${it.value.first}|${it.value.second}" }).apply()
    }

    private fun notificar(a: Alerta) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Alertas de clima y huracanes", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Huracanes cerca, tormentas, rachas fuertes y sismos reportados"
                }
            )
            val abrir = PendingIntent.getActivity(
                ctx, 2,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle((if (a.nivel >= 3) "PELIGRO · " else "Alerta · ") + a.titulo)
                .setContentText(a.detalle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.detalle))
                .setPriority(if (a.nivel >= 3) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(abrir)
                .build()
            nm.notify(1000 + (a.clave.hashCode() and 0xFFF), n)
        } catch (_: Exception) { }
    }

    // ---------------- Utilidades ----------------

    private fun nombreClase(c: Ciclon): String = when (c.clase) {
        "HU" -> "Huracán cat ${categoria(c.vientoKt)}"
        "TS" -> "Tormenta tropical"
        "TD" -> "Depresión tropical"
        "STS" -> "Tormenta subtropical"
        "STD" -> "Depresión subtropical"
        "PTC" -> "Ciclón postropical"
        "PC" -> "Posible ciclón tropical"
        "TY" -> "Tifón"
        else -> "Ciclón"
    }

    private fun tipoCorto(c: Ciclon): String = when (c.clase) {
        "HU" -> "Huracan${categoria(c.vientoKt)}"
        "TS", "STS" -> "T.trop"
        "TD", "STD" -> "D.trop"
        else -> "Ciclon"
    }

    /** Escala Saffir-Simpson (vientos en nudos). */
    fun categoria(kt: Int): Int = when {
        kt >= 137 -> 5
        kt >= 113 -> 4
        kt >= 96 -> 3
        kt >= 83 -> 2
        else -> 1
    }

    /** Distancia y rumbo de un ciclón desde donde estás (para la lista de "ciclones activos"). */
    fun distanciaA(c: Ciclon): Int? = posicion()?.let { distanciaKm(it.first, it.second, c.lat, c.lon).roundToInt() }
    fun descripcion(c: Ciclon): String = nombreClase(c)

    private fun fmtUno(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Rumbo inicial de 1 a 2, en grados (0 = norte). */
    private fun rumboGrados(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val f1 = lat1 * PI / 180
        val f2 = lat2 * PI / 180
        val dl = (lon2 - lon1) * PI / 180
        val y = sin(dl) * cos(f2)
        val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
        return ((atan2(y, x) * 180 / PI + 360) % 360).roundToInt()
    }

    private val PUNTOS = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO")
    private fun cardinal(grados: Double): String = PUNTOS[(((grados % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]
}
