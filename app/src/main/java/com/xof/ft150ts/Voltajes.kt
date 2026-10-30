package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Historial de voltajes para las gráficas (Moto > Baterías): batería de la moto y celda de respaldo de la tarjeta.
 * Una muestra cada 30 s con la tarjeta conectada, las últimas 24 h, y se guarda cada 10 min (sobrevive a cerrar la app).
 * -1 = sin dato en ese momento.
 */
object Voltajes {
    const val CADA_MS = 30_000L
    const val MUESTRAS = 24 * 60 * 2          // 24 h a 2 por minuto
    val moto = MutableStateFlow<List<Float>>(emptyList())
    val celda = MutableStateFlow<List<Float>>(emptyList())
    /** Hora (System.currentTimeMillis) de la última muestra: con eso se sabe qué hora es cada punto. */
    val ultima = MutableStateFlow(0L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private var prefs: SharedPreferences? = null

    fun iniciar(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("voltajes", Context.MODE_PRIVATE)
        prefs = p
        moto.value = leer(p.getString("moto", null))
        celda.value = leer(p.getString("celda", null))
        ultima.value = p.getLong("ultima", 0L)
        scope.launch {
            var n = 0
            while (isActive) {
                delay(CADA_MS)
                if (Moto.conexion.value != Conexion.CONECTADO) continue
                agregar(Moto.rapida.value.vMoto, Moto.estado.value.vLipo, System.currentTimeMillis())
                if (++n % 20 == 0) guardar()
            }
        }
    }

    fun agregar(vMoto: Float, vCelda: Float, ahora: Long) {
        moto.value = (moto.value + redondo(vMoto)).takeLast(MUESTRAS)
        celda.value = (celda.value + redondo(vCelda)).takeLast(MUESTRAS)
        ultima.value = ahora
    }

    fun guardar() {
        prefs?.edit()?.putString("moto", JSONArray(moto.value.map { it.toDouble() }).toString())
            ?.putString("celda", JSONArray(celda.value.map { it.toDouble() }).toString())
            ?.putLong("ultima", ultima.value)?.apply()
    }

    fun borrar() { moto.value = emptyList(); celda.value = emptyList(); guardar() }

    private fun redondo(v: Float) = if (v < 0f || v.isNaN()) -1f else Math.round(v * 100f) / 100f

    private fun leer(s: String?): List<Float> = try {
        val a = JSONArray(s ?: "[]")
        (0 until a.length()).map { a.getDouble(it).toFloat() }
    } catch (_: Exception) { emptyList() }

    /** Mínimo, máximo y promedio de lo que sí tiene dato (null si nada). */
    fun resumen(l: List<Float>): Triple<Float, Float, Float>? {
        val v = l.filter { it > 0f }
        return if (v.isEmpty()) null else Triple(v.min(), v.max(), v.average().toFloat())
    }
}
