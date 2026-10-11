package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Una carga de gasolina. [precio] = lo que pagaste en total ($). [km] = odómetro al cargar. */
data class Carga(val fecha: Long, val litros: Float, val precio: Float, val km: Double, val lleno: Boolean)

/** Lo que sale de la bitácora. */
data class ResumenGasolina(
    val rendimiento: Float = 0f,        // km/L promedio (de lleno a lleno, últimas 5)
    val ultimoRendimiento: Float = 0f,  // km/L de la última vez que llenaste
    val autonomia: Int = -1,            // km que te quedan (aprox.)
    val litrosRestantes: Float = -1f,
    val costoKm: Float = 0f,            // $ por km
    val gastoMes: Float = 0f,           // $ este mes
    val precioLitro: Float = 0f,        // de la última carga
)

/**
 * Bitácora de gasolina: apuntas cuántos litros y cuánto pagaste; con el odómetro saca tus km/L reales,
 * cuánto te cuesta cada km y cuántos km te quedan en el tanque (FT150TS: 13.3 L).
 */
object Gasolina {
    val cargas = MutableStateFlow<List<Carga>>(emptyList())
    val capacidad = MutableStateFlow(13.3f)
    val resumen = MutableStateFlow(ResumenGasolina())

    /** Rendimiento de fábrica de la FT150TS, mientras no haya datos tuyos. */
    private const val KMPL_FABRICA = 32f
    private var prefs: SharedPreferences? = null
    private var avisoReserva = 0L

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("gasolina", Context.MODE_PRIVATE)
        prefs = p
        capacidad.value = p.getFloat("capacidad", 13.3f)
        try {
            val a = JSONArray(p.getString("cargas", "[]") ?: "[]")
            cargas.value = (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Carga(o.optLong("f", 0L), o.optDouble("l", 0.0).toFloat(), o.optDouble("p", 0.0).toFloat(),
                    o.optDouble("k", 0.0), o.optInt("ll", 1) == 1)
            }
        } catch (_: Exception) { }
        recalcular()
    }

    fun agregar(c: Carga) {
        cargas.value = (cargas.value + c).sortedBy { it.km }
        guardar()
    }

    fun borrar(c: Carga) {
        cargas.value = cargas.value - c
        guardar()
    }

    fun ponerCapacidad(l: Float) {
        capacidad.value = l.coerceIn(3f, 30f)
        prefs?.edit()?.putFloat("capacidad", capacidad.value)?.apply()
        recalcular()
    }

    private fun guardar() {
        val a = JSONArray()
        cargas.value.forEach { c ->
            a.put(JSONObject().apply {
                put("f", c.fecha); put("l", c.litros.toDouble()); put("p", c.precio.toDouble()); put("k", c.km); put("ll", if (c.lleno) 1 else 0)
            })
        }
        prefs?.edit()?.putString("cargas", a.toString())?.apply()
        recalcular()
    }

    /** Con el odómetro actual (teléfono o tarjeta). Se llama al cargar y cada vez que cambia el odómetro. */
    fun recalcular(odometro: Double = OdometroTel.odometroActual()) {
        val l = cargas.value.sortedBy { it.km }
        // Rendimiento: de un lleno al siguiente, sumando los litros de las cargas de en medio
        val tramos = ArrayList<Pair<Double, Float>>()        // (km, litros)
        var desde: Carga? = null
        var litros = 0f
        for (c in l) {
            if (desde != null) litros += c.litros
            if (c.lleno) {
                val d = desde
                if (d != null && c.km - d.km > 5 && litros > 0.2f) tramos += (c.km - d.km) to litros
                desde = c
                litros = 0f
            }
        }
        val ultimos = tramos.takeLast(5)
        val kmSum = ultimos.sumOf { it.first }
        val lSum = ultimos.sumOf { it.second.toDouble() }
        val rend = if (lSum > 0) (kmSum / lSum).toFloat() else 0f
        val ultimo = tramos.lastOrNull()?.let { (it.first / it.second).toFloat() } ?: 0f

        // Lo que queda: desde el último lleno, tanque lleno + cargas parciales − lo gastado
        val usar = if (rend > 0f) rend else KMPL_FABRICA
        val ultLleno = l.lastOrNull { it.lleno }
        var restantes = -1f
        var autonomia = -1
        if (ultLleno != null && odometro > 0) {
            val parciales = l.filter { it.km > ultLleno.km && !it.lleno }.sumOf { it.litros.toDouble() }.toFloat()
            val gastado = ((odometro - ultLleno.km).coerceAtLeast(0.0) / usar).toFloat()
            restantes = (capacidad.value + parciales - gastado).coerceIn(0f, capacidad.value)
            autonomia = (restantes * usar).toInt()
        }
        // $ por km: todo lo pagado entre el primer lleno y el último, entre los km
        val primero = l.firstOrNull { it.lleno }
        val costoKm = if (primero != null && ultLleno != null && ultLleno.km - primero.km > 20) {
            val pagado = l.filter { it.km > primero.km && it.km <= ultLleno.km }.sumOf { it.precio.toDouble() }
            (pagado / (ultLleno.km - primero.km)).toFloat()
        } else 0f
        val cal = Calendar.getInstance()
        val mes = cal.get(Calendar.MONTH)
        val anio = cal.get(Calendar.YEAR)
        val gastoMes = l.filter {
            cal.timeInMillis = it.fecha
            cal.get(Calendar.MONTH) == mes && cal.get(Calendar.YEAR) == anio
        }.sumOf { it.precio.toDouble() }.toFloat()
        val ult = l.lastOrNull()
        resumen.value = ResumenGasolina(
            rendimiento = rend, ultimoRendimiento = ultimo, autonomia = autonomia, litrosRestantes = restantes,
            costoKm = costoKm, gastoMes = gastoMes,
            precioLitro = if (ult != null && ult.litros > 0) ult.precio / ult.litros else 0f,
        )
        // Aviso de reserva (una vez cada 2 horas)
        val ahora = System.currentTimeMillis()
        if (autonomia in 0..30 && ahora - avisoReserva > 2 * 3600_000L) {
            avisoReserva = ahora
            Moto.aviso("Gasolina ~$autonomia km", "Te quedan unos $autonomia kilómetros de gasolina")
        }
    }
}
