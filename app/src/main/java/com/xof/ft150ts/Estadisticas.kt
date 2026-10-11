package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Bandas de RPM para la gráfica de "en qué RPM manejas". */
val BANDAS_RPM = listOf(3000, 5000, 7000)          // <3k, 3-5k, 5-7k, >7k

/** Estadísticas de manejo calculadas en el teléfono con los datos de la moto (4 por segundo). */
data class Manejo(
    val inicio: Long = 0L,            // cuándo empezó (System.currentTimeMillis)
    val msMov: Long = 0L,             // tiempo rodando (> 3 km/h)
    val msParado: Long = 0L,          // motor prendido y parado
    val km: Double = 0.0,
    val velMax: Float = 0f,
    val rpmMax: Int = 0,
    val sumaRpm: Double = 0.0,
    val muestrasRpm: Long = 0L,
    val inclMax: Float = 0f,
    val acelMax: Float = 0f,          // g hacia adelante
    val frenadaMax: Float = 0f,       // g (positivo)
    val tempMax: Float = -99f,
    val voltMin: Float = 99f,         // batería de la moto con motor prendido
    val msMarcha: List<Long> = List(6) { 0L },   // N, 1..5
    val msRpm: List<Long> = List(BANDAS_RPM.size + 1) { 0L },
    val mejor0a40: Float = 0f,        // s
    val mejor0a60: Float = 0f,
    val mejor0a80: Float = 0f,
    val ultimoArranque: String = "",  // texto del último arranque medido
    val cambios: Int = 0,             // cambios de marcha
) {
    val velProm: Float get() = if (msMov > 0) (km / (msMov / 3_600_000.0)).toFloat() else 0f
    val rpmProm: Int get() = if (muestrasRpm > 0) (sumaRpm / muestrasRpm).toInt() else 0
}

/** Mejores marcas de siempre. */
data class Records(
    val velMax: Float = 0f,
    val rpmMax: Int = 0,
    val inclMax: Float = 0f,
    val acelMax: Float = 0f,
    val frenadaMax: Float = 0f,
    val mejor0a40: Float = 0f,
    val mejor0a60: Float = 0f,
    val mejor0a80: Float = 0f,
)

object Estadisticas {
    val manejo = MutableStateFlow(Manejo())
    val records = MutableStateFlow(Records())

    private var prefs: SharedPreferences? = null
    private var ultMuestra = 0L            // elapsedRealtime de la muestra anterior
    private var ultHora = 0L               // currentTimeMillis de la última muestra (para "viaje nuevo")
    private var velAnt = -1f
    private var marchaAnt = -1
    private var ultGuardado = 0L

    // Arranque (0-40/60/80): empieza al salir de parado y se cancela si frena
    private var midiendo = false
    private var tArranque = 0L
    private var velMaxArranque = 0f
    private val metas = floatArrayOf(40f, 60f, 80f)
    private val tiempos = FloatArray(3)

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("estadisticas", Context.MODE_PRIVATE)
        prefs = p
        try { p.getString("manejo", null)?.let { manejo.value = leerManejo(JSONObject(it)) } } catch (_: Exception) { }
        try { p.getString("records", null)?.let { records.value = leerRecords(JSONObject(it)) } } catch (_: Exception) { }
        ultHora = p.getLong("ultHora", 0L)
    }

    /** Empieza un viaje nuevo (los récords se quedan). */
    fun nuevo() {
        manejo.value = Manejo(inicio = System.currentTimeMillis())
        guardar(true)
    }

    fun borrarRecords() {
        records.value = Records()
        guardar(true)
    }

    /** Cada dato rápido de la moto (o del modo demo). */
    fun muestra(r: Rapida) {
        val ahoraRt = SystemClock.elapsedRealtime()
        val ahora = System.currentTimeMillis()
        // Más de 2 h sin datos: viaje nuevo
        if (ultHora > 0 && ahora - ultHora > 2 * 3600_000L && manejo.value.inicio > 0) manejo.value = Manejo(inicio = ahora)
        if (manejo.value.inicio == 0L) manejo.value = manejo.value.copy(inicio = ahora)
        ultHora = ahora
        val dt = if (ultMuestra == 0L) 0L else min(1_000L, ahoraRt - ultMuestra)
        ultMuestra = ahoraRt
        if (r.vel < 0 && r.rpm < 0) return

        var m = manejo.value
        val vel = if (r.vel >= 0) r.vel else 0f
        val rodando = vel > 3f
        if (dt > 0) {
            if (rodando) m = m.copy(msMov = m.msMov + dt, km = m.km + vel * dt / 3_600_000.0)
            else if (r.motor) m = m.copy(msParado = m.msParado + dt)
            if (r.motor && r.marcha in 0..5) m = m.copy(msMarcha = m.msMarcha.mapIndexed { i, t -> if (i == r.marcha) t + dt else t })
            if (r.motor && r.rpm > 0) {
                val banda = BANDAS_RPM.indexOfFirst { r.rpm < it }.let { if (it < 0) BANDAS_RPM.size else it }
                m = m.copy(msRpm = m.msRpm.mapIndexed { i, t -> if (i == banda) t + dt else t })
            }
        }
        if (r.motor && r.rpm > 0) m = m.copy(rpmMax = max(m.rpmMax, r.rpm), sumaRpm = m.sumaRpm + r.rpm, muestrasRpm = m.muestrasRpm + 1)
        m = m.copy(velMax = max(m.velMax, vel))
        if (r.incl >= 0 && rodando) m = m.copy(inclMax = max(m.inclMax, r.incl))
        if (rodando) {
            if (r.aFwd > 0) m = m.copy(acelMax = max(m.acelMax, r.aFwd)) else m = m.copy(frenadaMax = max(m.frenadaMax, -r.aFwd))
        }
        if (r.temp > -90) m = m.copy(tempMax = max(m.tempMax, r.temp))
        if (r.motor && r.vMoto > 5f) m = m.copy(voltMin = min(m.voltMin, r.vMoto))
        if (r.marcha in 1..5 && marchaAnt in 1..5 && r.marcha != marchaAnt) m = m.copy(cambios = m.cambios + 1)
        if (r.marcha >= 0) marchaAnt = r.marcha

        m = medirArranque(m, vel, ahoraRt, dt)
        velAnt = vel
        manejo.value = m
        actualizarRecords(m)
        guardar(false)
    }

    private fun medirArranque(m0: Manejo, vel: Float, ahora: Long, dt: Long): Manejo {
        var m = m0
        val antes = velAnt
        if (antes < 0) return m
        if (!midiendo && antes < 1.5f && vel >= 1.5f && dt > 0) {
            // salió de parado: el inicio se calcula entre las dos muestras
            val f = ((1.5f - antes) / (vel - antes)).coerceIn(0f, 1f)
            tArranque = ahora - dt + (f * dt).toLong()
            midiendo = true
            velMaxArranque = vel
            tiempos.fill(0f)
        } else if (midiendo) {
            val dur = (ahora - tArranque) / 1000f
            if (vel < velMaxArranque - 5f || dur > 30f || vel < 1f) {
                midiendo = false                    // frenó o tardó demasiado
            } else {
                velMaxArranque = max(velMaxArranque, vel)
                for (k in metas.indices) {
                    if (tiempos[k] == 0f && antes < metas[k] && vel >= metas[k] && dt > 0) {
                        val f = ((metas[k] - antes) / (vel - antes)).coerceIn(0f, 1f)
                        val t = (ahora - dt + (f * dt).toLong() - tArranque) / 1000f
                        tiempos[k] = t
                        m = when (k) {
                            0 -> m.copy(mejor0a40 = if (m.mejor0a40 == 0f) t else min(m.mejor0a40, t))
                            1 -> m.copy(mejor0a60 = if (m.mejor0a60 == 0f) t else min(m.mejor0a60, t))
                            else -> m.copy(mejor0a80 = if (m.mejor0a80 == 0f) t else min(m.mejor0a80, t))
                        }
                        m = m.copy(ultimoArranque = "0-${metas[k].toInt()} en ${String.format(java.util.Locale.US, "%.2f", t)} s")
                    }
                }
                if (tiempos[2] > 0f) midiendo = false
            }
        }
        return m
    }

    private fun mejorTiempo(a: Float, b: Float) = when { a == 0f -> b; b == 0f -> a; else -> min(a, b) }

    private fun actualizarRecords(m: Manejo) {
        val r = records.value
        val n = r.copy(
            velMax = max(r.velMax, m.velMax),
            rpmMax = max(r.rpmMax, m.rpmMax),
            inclMax = max(r.inclMax, m.inclMax),
            acelMax = max(r.acelMax, m.acelMax),
            frenadaMax = max(r.frenadaMax, m.frenadaMax),
            mejor0a40 = mejorTiempo(r.mejor0a40, m.mejor0a40),
            mejor0a60 = mejorTiempo(r.mejor0a60, m.mejor0a60),
            mejor0a80 = mejorTiempo(r.mejor0a80, m.mejor0a80),
        )
        if (n != r) records.value = n
    }

    private fun guardar(ya: Boolean) {
        val ahora = SystemClock.elapsedRealtime()
        if (!ya && ahora - ultGuardado < 15_000L) return
        ultGuardado = ahora
        val m = manejo.value
        val r = records.value
        try {
            prefs?.edit()
                ?.putString("manejo", JSONObject().apply {
                    put("in", m.inicio); put("mm", m.msMov); put("mp", m.msParado); put("km", m.km)
                    put("vm", m.velMax.toDouble()); put("rm", m.rpmMax); put("sr", m.sumaRpm); put("nr", m.muestrasRpm)
                    put("im", m.inclMax.toDouble()); put("am", m.acelMax.toDouble()); put("fm", m.frenadaMax.toDouble())
                    put("tm", m.tempMax.toDouble()); put("vn", m.voltMin.toDouble())
                    put("ma", JSONArray(m.msMarcha)); put("br", JSONArray(m.msRpm))
                    put("a4", m.mejor0a40.toDouble()); put("a6", m.mejor0a60.toDouble()); put("a8", m.mejor0a80.toDouble())
                    put("ua", m.ultimoArranque); put("ca", m.cambios)
                }.toString())
                ?.putString("records", JSONObject().apply {
                    put("vm", r.velMax.toDouble()); put("rm", r.rpmMax); put("im", r.inclMax.toDouble())
                    put("am", r.acelMax.toDouble()); put("fm", r.frenadaMax.toDouble())
                    put("a4", r.mejor0a40.toDouble()); put("a6", r.mejor0a60.toDouble()); put("a8", r.mejor0a80.toDouble())
                }.toString())
                ?.putLong("ultHora", ultHora)
                ?.apply()
        } catch (_: Exception) { }
    }

    private fun lista(a: JSONArray?, n: Int): List<Long> = List(n) { i -> a?.optLong(i, 0L) ?: 0L }

    private fun leerManejo(o: JSONObject) = Manejo(
        inicio = o.optLong("in", 0L), msMov = o.optLong("mm", 0L), msParado = o.optLong("mp", 0L), km = o.optDouble("km", 0.0),
        velMax = o.optDouble("vm", 0.0).toFloat(), rpmMax = o.optInt("rm", 0), sumaRpm = o.optDouble("sr", 0.0),
        muestrasRpm = o.optLong("nr", 0L), inclMax = o.optDouble("im", 0.0).toFloat(), acelMax = o.optDouble("am", 0.0).toFloat(),
        frenadaMax = o.optDouble("fm", 0.0).toFloat(), tempMax = o.optDouble("tm", -99.0).toFloat(),
        voltMin = o.optDouble("vn", 99.0).toFloat(), msMarcha = lista(o.optJSONArray("ma"), 6),
        msRpm = lista(o.optJSONArray("br"), BANDAS_RPM.size + 1), mejor0a40 = o.optDouble("a4", 0.0).toFloat(),
        mejor0a60 = o.optDouble("a6", 0.0).toFloat(), mejor0a80 = o.optDouble("a8", 0.0).toFloat(),
        ultimoArranque = o.optString("ua", ""), cambios = o.optInt("ca", 0),
    )

    private fun leerRecords(o: JSONObject) = Records(
        velMax = o.optDouble("vm", 0.0).toFloat(), rpmMax = o.optInt("rm", 0), inclMax = o.optDouble("im", 0.0).toFloat(),
        acelMax = o.optDouble("am", 0.0).toFloat(), frenadaMax = o.optDouble("fm", 0.0).toFloat(),
        mejor0a40 = o.optDouble("a4", 0.0).toFloat(), mejor0a60 = o.optDouble("a6", 0.0).toFloat(),
        mejor0a80 = o.optDouble("a8", 0.0).toFloat(),
    )
}
