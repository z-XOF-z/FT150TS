package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Una medición: el velocímetro de la moto marcaba [marca] y el GPS decía [real]. */
data class MedidaVel(val marca: Int, val real: Float, val fecha: Long)

/**
 * Error del velocímetro: rodando parejo, cuando la aguja de la moto marca un número, tocas ese número (o dices
 * "marca cuarenta" al asistente) y se toma el promedio del GPS de los últimos 5 segundos. Con dos o más medidas
 * se saca la relación entre lo que marca y la velocidad real (línea recta), para decirte a cuánto vas de verdad.
 */
object Velocimetro {
    val medidas = MutableStateFlow<List<MedidaVel>>(emptyList())
    val mensaje = MutableStateFlow("")

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private val ventana = ArrayDeque<Pair<Long, Float>>()
    private var iniciado = false

    fun iniciar(c: Context) {
        if (iniciado) return
        iniciado = true
        val p = c.applicationContext.getSharedPreferences("velocimetro", Context.MODE_PRIVATE)
        prefs = p
        medidas.value = try {
            val a = JSONArray(p.getString("medidas", "[]") ?: "[]")
            List(a.length()) { a.getJSONObject(it).let { o -> MedidaVel(o.getInt("m"), o.getDouble("r").toFloat(), o.optLong("f")) } }
        } catch (_: Exception) { emptyList() }
        scope.launch {
            UbicacionManager.ubicacion.collect { l ->
                if (l == null || !l.hasSpeed()) return@collect
                val ahora = SystemClock.elapsedRealtime()
                ventana.addLast(ahora to l.speed * 3.6f)
                while (ventana.isNotEmpty() && ahora - ventana.first().first > 6_000) ventana.removeFirst()
            }
        }
    }

    /** El velocímetro marca [marca] ahorita. Regresa el texto para decir/mostrar. */
    fun medir(marca: Int): String {
        val ahora = SystemClock.elapsedRealtime()
        val ult = ventana.filter { ahora - it.first <= 5_000 }.map { it.second }
        if (UbicacionManager.edadMs() > 3_000 || ult.size < 3) return decir("Sin GPS suficiente: espera unos segundos con buena señal")
        val prom = ult.average().toFloat()
        if (prom < 10f) return decir("Tienes que ir rodando (más de 10 km/h)")
        if (ult.any { abs(it - prom) > 2.5f }) return decir("Mantén la velocidad pareja unos 5 segundos (el GPS marca ${prom.roundToInt()} km/h)")
        val m = MedidaVel(marca, prom, System.currentTimeMillis())
        medidas.value = (medidas.value + m).takeLast(20)
        guardar()
        Logros.revisar()
        val e = (marca - prom) / prom * 100f
        return decir(
            "Marca $marca y vas a ${prom.roundToInt()} de verdad: " +
                (if (abs(e) < 1.5f) "está casi exacto" else if (e > 0) "marca ${e.roundToInt()} por ciento de más" else "marca ${(-e).roundToInt()} por ciento de menos")
        )
    }

    private fun decir(t: String): String { mensaje.value = t; return t }

    /** Error promedio en % (positivo = marca de más), o null sin medidas. */
    fun errorPromedio(): Float? {
        val l = medidas.value
        if (l.isEmpty()) return null
        return l.map { (it.marca - it.real) / it.real * 100f }.average().toFloat()
    }

    /** Velocidad real cuando el velocímetro marca [marca] (recta por mínimos cuadrados, o el % promedio). */
    fun realPara(marca: Int): Float? {
        val l = medidas.value
        if (l.isEmpty()) return null
        val distintas = l.map { it.marca }.distinct().size
        if (distintas >= 2) {
            val n = l.size
            val mx = l.map { it.marca.toDouble() }.average()
            val my = l.map { it.real.toDouble() }.average()
            var sxy = 0.0; var sxx = 0.0
            for (m in l) { sxy += (m.marca - mx) * (m.real - my); sxx += (m.marca - mx) * (m.marca - mx) }
            if (sxx > 1e-6 && n >= 2) {
                val a = sxy / sxx
                val b = my - a * mx
                return (a * marca + b).toFloat()
            }
        }
        val e = errorPromedio() ?: return null
        return marca / (1f + e / 100f)
    }

    fun borrar() {
        medidas.value = emptyList()
        mensaje.value = ""
        guardar()
    }

    private fun guardar() {
        val a = JSONArray()
        medidas.value.forEach { a.put(JSONObject().apply { put("m", it.marca); put("r", it.real.toDouble()); put("f", it.fecha) }) }
        prefs?.edit()?.putString("medidas", a.toString())?.apply()
    }
}
