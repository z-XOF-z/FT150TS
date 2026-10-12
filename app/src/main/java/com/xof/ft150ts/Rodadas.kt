package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.os.SystemClock
import java.io.File
import java.util.Locale
import kotlin.math.max
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

/** Un punto de la rodada: [t] ms desde que empezó. */
data class PuntoRodada(val t: Long, val lat: Double, val lon: Double, val vel: Float, val incl: Float)

/** Resumen de una rodada (lo que sale en la lista). */
data class Rodada(
    val id: Long,            // cuándo empezó (hora del reloj)
    val fin: Long,
    val km: Float,
    val minutos: Int,        // de principio a fin
    val minMov: Int,         // en movimiento
    val vmax: Float,
    val vprom: Float,        // promedio en movimiento
    val inclMax: Float,
    val puntos: Int,
)

/**
 * Bitácora de rodadas: se graba sola cuando la moto rueda (modo moto, motor o más de 15 km/h) y se cierra a los
 * 5 min parada. Cada rodada guarda su recorrido (para la repetición en el mapa) en files/rodadas/<id>.json.
 * También avisa si llevas 1.5 h rodando sin parar.
 */
object Rodadas {
    val lista = MutableStateFlow<List<Rodada>>(emptyList())
    /** Rodada que se está grabando (resumen en vivo), o null. */
    val enCurso = MutableStateFlow<Rodada?>(null)
    val avisoDescanso = MutableStateFlow(true)
    /** Al apagar el switch, decir por voz el resumen de la rodada (km, tiempo y máxima). */
    val resumenVoz = MutableStateFlow(true)

    private const val MAX = 60
    private var ctx: Context? = null
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // grabación en curso
    private val puntos = ArrayList<PuntoRodada>()
    private var inicio = 0L
    private var inicioReal = 0L          // elapsedRealtime al empezar
    private var km = 0.0
    private var vmax = 0f
    private var inclMax = 0f
    private var msMov = 0L
    private var ultimoFix: Location? = null
    private var ultimoMovimiento = 0L
    private var ultimoGuardado = 0L
    // descanso
    private var rodandoDesde = 0L
    private var avisoDado = false

    fun iniciar(c: Context) {
        if (ctx != null) return
        val app = c.applicationContext
        ctx = app
        val p = app.getSharedPreferences("rodadas", Context.MODE_PRIVATE)
        prefs = p
        avisoDescanso.value = p.getBoolean("avisoDescanso", true)
        resumenVoz.value = p.getBoolean("resumenVoz", true)
        lista.value = leerIndice(p.getString("indice", "[]") ?: "[]")
        // Apagaste el switch (la carga se cortó más de 8 s) con la moto parada: la rodada termina ya,
        // no 5 min después, y te dice el resumen mientras sigues junto a la moto
        scope.launch {
            var antes = Energia.enMoto.value
            Energia.enMoto.collect { ahora ->
                if (antes && !ahora && inicio != 0L && UbicacionManager.velocidadKmh() < 5f) terminar(conVoz = true)
                antes = ahora
            }
        }
        // Si la app se cerró a media rodada, se guarda lo que alcanzó a grabar
        scope.launch { withContext(Dispatchers.IO) { recuperarPendiente() } }
        scope.launch { UbicacionManager.ubicacion.collect { l -> if (l != null) nuevaPosicion(l) } }
        scope.launch {
            while (isActive) {
                delay(30_000)
                val ahora = SystemClock.elapsedRealtime()
                if (inicio != 0L && ahora - ultimoMovimiento > 5 * 60_000L) terminar()
                else if (inicio != 0L && ahora - ultimoGuardado > 2 * 60_000L) { ultimoGuardado = ahora; guardarPendiente() }
            }
        }
    }

    private fun carpeta(): File = File(ctx!!.filesDir, "rodadas").also { it.mkdirs() }

    private fun nuevaPosicion(l: Location) {
        if (UbicacionManager.edadMs() > 3_000) return                  // posición vieja
        val vel = if (l.hasSpeed()) l.speed * 3.6f else 0f
        val ahora = SystemClock.elapsedRealtime()
        val precisa = !l.hasAccuracy() || l.accuracy <= 30f
        if (inicio == 0L) {
            val enLaMoto = Energia.enMoto.value || TelefonoMoto.info.value.motor || Moto.conexion.value == Conexion.CONECTADO
            // Sin cable/tarjeta solo cuenta con la app en pantalla (así no graba viajes en camión o en carro)
            if (precisa && vel > 8f && (enLaMoto || (vel > 15f && BleManager.primerPlano))) empezar(l, ahora)
            return
        }
        val prev = ultimoFix
        if (prev != null && precisa) {
            val d = prev.distanceTo(l)
            val dt = (l.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9f
            // saltos imposibles del GPS (más de 200 km/h entre dos posiciones) no cuentan
            if (dt > 0f && d / dt < 56f) {
                km += d / 1000.0
                if (vel > 3f) msMov += (dt * 1000).toLong()
            }
        }
        if (precisa) {
            vmax = max(vmax, vel)
            ultimoFix = l
        }
        val incl = Moto.rapida.value.incl.let { if (it >= 0f) it else 0f }
        inclMax = max(inclMax, incl)
        if (vel > 3f) ultimoMovimiento = ahora
        // un punto cada 8 m o cada 5 s
        val ult = puntos.lastOrNull()
        val t = System.currentTimeMillis() - inicio
        if (ult == null || t - ult.t > 5_000 || distancia(ult.lat, ult.lon, l.latitude, l.longitude) > 8.0) {
            puntos += PuntoRodada(t, l.latitude, l.longitude, vel, incl)
        }
        revisarDescanso(vel, ahora)
        enCurso.value = resumen(System.currentTimeMillis())
    }

    private fun empezar(l: Location, ahora: Long) {
        inicio = System.currentTimeMillis()
        inicioReal = ahora
        km = 0.0; vmax = 0f; inclMax = 0f; msMov = 0L
        puntos.clear()
        ultimoFix = l
        ultimoMovimiento = ahora
        ultimoGuardado = ahora
        if (rodandoDesde == 0L) { rodandoDesde = ahora; avisoDado = false }
        puntos += PuntoRodada(0L, l.latitude, l.longitude, if (l.hasSpeed()) l.speed * 3.6f else 0f, 0f)
    }

    /** Aviso de descanso: 1.5 h rodando (se reinicia con una parada de 10 min). */
    private fun revisarDescanso(vel: Float, ahora: Long) {
        if (vel <= 3f) return
        if (rodandoDesde == 0L || ahora - ultimoMovimiento > 10 * 60_000L) { rodandoDesde = ahora; avisoDado = false }
        if (avisoDescanso.value && !avisoDado && ahora - rodandoDesde > 90 * 60_000L) {
            avisoDado = true
            Moto.aviso("Llevas 1 h 30 min rodando", "Llevas hora y media rodando. ¿Qué tal una pausa para estirarte y tomar agua?")
        }
    }

    private fun resumen(fin: Long): Rodada {
        val minMov = (msMov / 60_000L).toInt()
        val horasMov = msMov / 3_600_000.0
        return Rodada(
            id = inicio, fin = fin, km = km.toFloat(), minutos = ((fin - inicio) / 60_000L).toInt(), minMov = minMov,
            vmax = vmax, vprom = if (horasMov > 0.01) (km / horasMov).toFloat() else 0f, inclMax = inclMax, puntos = puntos.size,
        )
    }

    /** Cierra la rodada en curso (si fue de menos de 500 m, no se guarda). [conVoz] = decir el resumen. */
    fun terminar(conVoz: Boolean = false) {
        if (inicio == 0L) return
        // termina cuando se paró por última vez (no 5 min después)
        val fin = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - ultimoMovimiento)
        val r = resumen(fin)
        val pts = ArrayList(puntos)
        inicio = 0L
        puntos.clear()
        enCurso.value = null
        File(carpeta(), "pendiente.json").delete()
        if (r.km < 0.5f || pts.size < 3) return
        guardarRodada(r, pts)
        if (conVoz && resumenVoz.value) Voz.decir(fraseResumen(r))
    }

    /** "Rodada terminada: 12.4 kilómetros en 25 minutos, máxima de 78." */
    fun fraseResumen(r: Rodada): String {
        val km = String.format(Locale.US, "%.1f", r.km).replace(".0", "")
        val tiempo = if (r.minutos >= 60) "${r.minutos / 60} hora${if (r.minutos >= 120) "s" else ""} ${r.minutos % 60} minutos"
            else "${r.minutos} minutos"
        return "Rodada terminada: $km kilómetros en $tiempo, máxima de ${r.vmax.toInt()}."
    }

    fun ponerResumenVoz(on: Boolean) {
        resumenVoz.value = on
        prefs?.edit()?.putBoolean("resumenVoz", on)?.apply()
    }

    private fun guardarRodada(r: Rodada, pts: List<PuntoRodada>) {
        val c = ctx ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                File(File(c.filesDir, "rodadas").also { it.mkdirs() }, "${r.id}.json").writeText(puntosJson(r, pts).toString())
            }
            val nueva = (listOf(r) + lista.value.filter { it.id != r.id }).sortedByDescending { it.id }
            val sobran = nueva.drop(MAX)
            lista.value = nueva.take(MAX)
            sobran.forEach { File(carpeta(), "${it.id}.json").delete() }
            guardarIndice()
            Logros.revisar()
        }
    }

    /** Recorrido completo de una rodada guardada. */
    suspend fun cargarPuntos(id: Long): List<PuntoRodada> = withContext(Dispatchers.IO) {
        val f = File(File(ctx?.filesDir ?: return@withContext emptyList(), "rodadas"), "$id.json")
        if (!f.exists()) return@withContext emptyList()
        try { leerPuntos(JSONObject(f.readText()).getJSONArray("p")) } catch (_: Exception) { emptyList() }
    }

    fun borrar(id: Long) {
        lista.value = lista.value.filter { it.id != id }
        File(carpeta(), "$id.json").delete()
        guardarIndice()
    }

    fun ponerAvisoDescanso(on: Boolean) {
        avisoDescanso.value = on
        prefs?.edit()?.putBoolean("avisoDescanso", on)?.apply()
    }

    /** Para el respaldo: vuelve a leer el índice después de restaurar. */
    fun recargar() {
        prefs?.let { lista.value = leerIndice(it.getString("indice", "[]") ?: "[]") }
    }

    // ---------------- archivos ----------------

    private fun guardarPendiente() {
        if (inicio == 0L) return
        val r = resumen(System.currentTimeMillis())
        val pts = ArrayList(puntos)
        val c = ctx ?: return
        scope.launch(Dispatchers.IO) {
            try { File(File(c.filesDir, "rodadas").also { it.mkdirs() }, "pendiente.json").writeText(puntosJson(r, pts).toString()) } catch (_: Exception) { }
        }
    }

    private fun recuperarPendiente() {
        val f = File(carpeta(), "pendiente.json")
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText())
            val r = leerResumen(o.getJSONObject("r"))
            val pts = leerPuntos(o.getJSONArray("p"))
            f.delete()
            if (r.km >= 0.5f && pts.size >= 3) scope.launch { guardarRodada(r, pts) }
        } catch (_: Exception) { f.delete() }
    }

    private fun guardarIndice() {
        val a = JSONArray()
        lista.value.forEach { a.put(resumenJson(it)) }
        prefs?.edit()?.putString("indice", a.toString())?.apply()
    }

    private fun leerIndice(s: String): List<Rodada> = try {
        val a = JSONArray(s)
        List(a.length()) { leerResumen(a.getJSONObject(it)) }.sortedByDescending { it.id }
    } catch (_: Exception) { emptyList() }

    private fun resumenJson(r: Rodada) = JSONObject().apply {
        put("id", r.id); put("fin", r.fin); put("km", r.km.toDouble()); put("min", r.minutos); put("mov", r.minMov)
        put("vmax", r.vmax.toDouble()); put("vprom", r.vprom.toDouble()); put("incl", r.inclMax.toDouble()); put("n", r.puntos)
    }

    private fun leerResumen(o: JSONObject) = Rodada(
        o.getLong("id"), o.optLong("fin"), o.optDouble("km", 0.0).toFloat(), o.optInt("min"), o.optInt("mov"),
        o.optDouble("vmax", 0.0).toFloat(), o.optDouble("vprom", 0.0).toFloat(), o.optDouble("incl", 0.0).toFloat(), o.optInt("n"),
    )

    private fun puntosJson(r: Rodada, pts: List<PuntoRodada>): JSONObject {
        val a = JSONArray()
        for (p in pts) {
            a.put(JSONArray().apply {
                put(p.t); put(Math.round(p.lat * 1e6) / 1e6); put(Math.round(p.lon * 1e6) / 1e6)
                put(Math.round(p.vel * 10) / 10.0); put(Math.round(p.incl).toInt())
            })
        }
        return JSONObject().apply { put("r", resumenJson(r)); put("p", a) }
    }

    private fun leerPuntos(a: JSONArray): List<PuntoRodada> = List(a.length()) { i ->
        val x = a.getJSONArray(i)
        PuntoRodada(x.getLong(0), x.getDouble(1), x.getDouble(2), x.getDouble(3).toFloat(), x.getDouble(4).toFloat())
    }

    private fun distancia(la1: Double, lo1: Double, la2: Double, lo2: Double) = Baches.distanciaM(la1, lo1, la2, lo2)
}
