package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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

/** Mantenimiento llevado en el teléfono. */
data class MantTel(val nombre: String, val intKm: Int, val intDias: Int, val kmUltimo: Double, val fechaUltimo: Long)

/** Altitud y pendiente (con el GPS; el Poco F4 no trae barómetro). */
data class Altura(
    val actual: Float = Float.NaN,     // m sobre el nivel del mar (aprox.)
    val pendiente: Float = 0f,         // % (+ subida, − bajada), de los últimos ~150 m
    val subida: Float = 0f,            // m que subiste en el viaje
    val bajada: Float = 0f,
    val maxima: Float = Float.NaN,
    val minima: Float = Float.NaN,
)

/**
 * Lo que el teléfono lleva por su cuenta, sin la tarjeta:
 * - Odómetro por GPS (solo cuenta en la moto: modo moto, motor por vibración o tarjeta conectada).
 * - Mantenimiento por km y días (aceite, cadena...).
 * - Altitud, pendiente, subida y bajada del viaje.
 * - Dónde dejé la moto: al apagarla o quitar el teléfono (deja de cargar / se quita de la etiqueta NFC).
 * - Llena la fila de abajo del LCD (TRIP/ODO/RANGO/PROM) cuando la tarjeta no está.
 */
object OdometroTel {
    val km = MutableStateFlow(0.0)            // odómetro (lo que pusiste + lo recorrido)
    val kmHoy = MutableStateFlow(0.0)
    val kmAyer = MutableStateFlow(0.0)
    val mant = MutableStateFlow<List<MantTel>>(emptyList())
    val altura = MutableStateFlow(Altura())

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private var diaHoy = 0
    private var ant: Location? = null
    private var ultimoGuardado = 0L
    private var ultimoAvisoMant = 0L
    // altitud
    private var altSuave = Float.NaN
    private var altRef = Float.NaN
    private var distAcum = 0.0
    private val muestrasAlt = ArrayDeque<Pair<Double, Float>>()   // (distancia acumulada, altitud)
    private var ultHoraAlt = 0L

    private val MANT_FABRICA = listOf(
        MantTel("Cadena (lubricar/ajustar)", 500, 30, 0.0, 0L),
        MantTel("Aceite de motor", 2000, 90, 0.0, 0L),
        MantTel("Filtro de aire", 4000, 180, 0.0, 0L),
        MantTel("Bujía", 6000, 365, 0.0, 0L),
        MantTel("Frenos (balatas)", 4000, 180, 0.0, 0L),
    )

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("odometroTel", Context.MODE_PRIVATE)
        prefs = p
        km.value = p.getString("km", "0")?.toDoubleOrNull() ?: 0.0
        kmHoy.value = p.getString("hoy", "0")?.toDoubleOrNull() ?: 0.0
        kmAyer.value = p.getString("ayer", "0")?.toDoubleOrNull() ?: 0.0
        diaHoy = p.getInt("dia", 0)
        mant.value = leerMant(p.getString("mant", null)) ?: MANT_FABRICA
        cambioDeDia()
        scope.launch { UbicacionManager.ubicacion.collect { l -> if (l != null) nuevaUbicacion(l) } }
        // Dónde dejé la moto: al dejar de estar en la moto, quieto
        scope.launch {
            var antes = Energia.enMoto.value
            Energia.enMoto.collect { ahora ->
                if (antes && !ahora && !ModoSueno.dormido.value) {
                    val v = UbicacionManager.velocidadKmh()
                    if (v < 8f && !tarjetaActiva()) Estacionamiento.guardarAqui()
                }
                if (!antes && ahora) avisarMantenimiento()
                antes = ahora
            }
        }
        // Fila de abajo del LCD sin la tarjeta
        scope.launch {
            while (isActive) {
                delay(2_000)
                if (!tarjetaActiva()) llenarViaje()
            }
        }
    }

    private fun tarjetaActiva() = Moto.conexion.value == Conexion.CONECTADO || Moto.conexion.value == Conexion.DEMO

    /** El odómetro que manda: el de la tarjeta si está, si no el del teléfono. */
    fun odometroActual(): Double = if (tarjetaActiva() && Moto.viaje.value.odometro > 0f) Moto.viaje.value.odometro.toDouble() else km.value

    private fun enLaMoto() = Energia.enMoto.value || TelefonoMoto.info.value.motor || tarjetaActiva()

    private fun nuevaUbicacion(l: Location) {
        val a = ant
        ant = l
        if (a == null) return
        val dt = (l.time - a.time) / 1000.0
        if (dt <= 0.3 || dt > 15) return
        val precisa = !l.hasAccuracy() || l.accuracy <= 25f
        val d = distanciaM(a.latitude, a.longitude, l.latitude, l.longitude)
        val v = if (l.hasSpeed()) l.speed * 3.6f else (d / dt * 3.6).toFloat()
        // Solo cuenta rodando en la moto, con buena señal y sin brincos imposibles (> 200 km/h)
        if (precisa && enLaMoto() && v > 3f && d / dt < 55.0) {
            val k = d / 1000.0
            cambioDeDia()
            km.value = km.value + k
            kmHoy.value = kmHoy.value + k
            distAcum += d
            guardarCada()
            Gasolina.recalcular(odometroActual())
        }
        altitud(l, precisa)
    }

    private fun altitud(l: Location, precisa: Boolean) {
        if (!l.hasAltitude() || !precisa) return
        // Android 14+: altura sobre el nivel del mar si el GPS la da; si no, la del elipsoide (difiere unos metros)
        val alt = (if (Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude()) l.mslAltitudeMeters else l.altitude).toFloat()
        val ahora = System.currentTimeMillis()
        // Más de 2 h sin datos: viaje nuevo de subida/bajada
        if (ahora - ultHoraAlt > 2 * 3600_000L) { altura.value = Altura(); altRef = Float.NaN; muestrasAlt.clear() }
        ultHoraAlt = ahora
        altSuave = if (altSuave.isNaN()) alt else altSuave + 0.3f * (alt - altSuave)
        var h = altura.value
        if (altRef.isNaN()) altRef = altSuave
        // Subida/bajada con margen de 4 m (el GPS brinca unos metros)
        if (altSuave > altRef + 4f) { h = h.copy(subida = h.subida + (altSuave - altRef)); altRef = altSuave }
        else if (altSuave < altRef - 4f) { h = h.copy(bajada = h.bajada + (altRef - altSuave)); altRef = altSuave }
        // Pendiente de los últimos ~150 m recorridos
        muestrasAlt.addLast(distAcum to altSuave)
        while (muestrasAlt.size > 2 && distAcum - muestrasAlt.first().first > 160) muestrasAlt.removeFirst()
        val primero = muestrasAlt.first()
        val tramo = distAcum - primero.first
        val pend = if (tramo >= 80) ((altSuave - primero.second) / tramo * 100).toFloat().coerceIn(-30f, 30f) else 0f
        h = h.copy(
            actual = altSuave,
            pendiente = if (h.pendiente == 0f) pend else h.pendiente + 0.4f * (pend - h.pendiente),
            maxima = if (h.maxima.isNaN()) altSuave else max(h.maxima, altSuave),
            minima = if (h.minima.isNaN()) altSuave else min(h.minima, altSuave),
        )
        altura.value = h
    }

    private fun cambioDeDia() {
        val c = Calendar.getInstance()
        val d = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
        if (diaHoy == 0) { diaHoy = d; return }
        if (d != diaHoy) {
            kmAyer.value = kmHoy.value
            kmHoy.value = 0.0
            diaHoy = d
            guardar()
        }
    }

    private fun guardarCada() {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoGuardado > 20_000) guardar()
    }

    private fun guardar() {
        ultimoGuardado = System.currentTimeMillis()
        prefs?.edit()
            ?.putString("km", km.value.toString())
            ?.putString("hoy", kmHoy.value.toString())
            ?.putString("ayer", kmAyer.value.toString())
            ?.putInt("dia", diaHoy)
            ?.apply()
    }

    /** Pones lo que marca el odómetro de la moto; desde ahí suma el GPS. */
    fun ponerOdometro(valor: Double) {
        km.value = valor.coerceIn(0.0, 999_999.0)
        guardar()
        Gasolina.recalcular(odometroActual())
    }

    // ---------------- Mantenimiento ----------------

    fun kmDesde(m: MantTel) = (odometroActual() - m.kmUltimo).coerceAtLeast(0.0)
    fun diasDesde(m: MantTel) = if (m.fechaUltimo <= 0L) -1 else ((System.currentTimeMillis() - m.fechaUltimo) / 86_400_000L).toInt()
    fun vencido(m: MantTel) = kmDesde(m) >= m.intKm || (diasDesde(m) >= 0 && diasDesde(m) >= m.intDias)

    fun registrarServicio(i: Int) {
        mant.value = mant.value.mapIndexed { k, m -> if (k == i) m.copy(kmUltimo = odometroActual(), fechaUltimo = System.currentTimeMillis()) else m }
        guardarMant()
    }

    fun cambiarIntervalo(i: Int, kmInt: Int, dias: Int) {
        mant.value = mant.value.mapIndexed { k, m -> if (k == i) m.copy(intKm = kmInt, intDias = dias) else m }
        guardarMant()
    }

    private fun avisarMantenimiento() {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoAvisoMant < 20 * 3600_000L) return
        val toca = mant.value.filter { vencido(it) && it.fechaUltimo > 0L }
        if (toca.isEmpty()) return
        ultimoAvisoMant = ahora
        Moto.aviso("Mant: ${toca.first().nombre}".take(20), "Te toca: " + toca.joinToString(", ") { it.nombre })
    }

    private fun guardarMant() {
        val a = JSONArray()
        mant.value.forEach { m ->
            a.put(JSONObject().apply {
                put("n", m.nombre); put("k", m.intKm); put("d", m.intDias); put("ku", m.kmUltimo); put("fu", m.fechaUltimo)
            })
        }
        prefs?.edit()?.putString("mant", a.toString())?.apply()
    }

    private fun leerMant(texto: String?): List<MantTel>? = try {
        if (texto == null) null else {
            val a = JSONArray(texto)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                MantTel(o.optString("n", "?"), o.optInt("k", 1000), o.optInt("d", 90), o.optDouble("ku", 0.0), o.optLong("fu", 0L))
            }
        }
    } catch (_: Exception) { null }

    // ---------------- LCD sin la tarjeta ----------------

    private fun llenarViaje() {
        val m = Estadisticas.manejo.value
        val g = Gasolina.resumen.value
        val rec = Estadisticas.records.value
        val vencido = mant.value.indexOfFirst { vencido(it) && it.fechaUltimo > 0L }
        Moto.viaje.value = ViajeInfo(
            odometro = km.value.toFloat(),
            arranques = 0,
            activo = m.km > 0.05,
            km = m.km.toFloat(),
            velMax = m.velMax,
            litros = if (g.rendimiento > 0f) (m.km / g.rendimiento).toFloat() else 0f,
            hoyKm = kmHoy.value.toFloat(),
            ayerKm = kmAyer.value.toFloat(),
            kmPorLitro = g.rendimiento,
            autonomia = g.autonomia,
            mantVencido = vencido,
            ralenti = TelefonoMoto.ralentiMedido.value,
            pistaMejor = rec.mejor0a60,
        )
    }

    private fun distanciaM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
