package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class PruebaArr { ARRANCON, FRENADO }
enum class FaseArr { ESPERA, LISTO, SEMAFORO, CORRIENDO, TERMINADO }

/** Tiempos de un arrancón (segundos; 0 = no se alcanzó). */
data class ResultadoArr(
    val fecha: Long, val t40: Float, val t60: Float, val t80: Float,
    val t100m: Float, val v100m: Float, val t201m: Float, val v201m: Float,
    val reaccion: Float, val vmax: Float,
)

/** Frenado de 40 a 0 km/h. */
data class ResultadoFreno(val fecha: Long, val desde: Float, val metros: Float, val segundos: Float, val gMax: Float)

/**
 * Modo arrancones (solo en lugar cerrado y seguro):
 * - La salida se detecta con el acelerómetro (al instante); si no la nota, con el GPS.
 * - 0-40, 0-60, 0-80, 100 m y 1/8 de milla (201 m): el GPS da una velocidad por segundo y el momento exacto
 *   en que cruzaste cada marca se calcula entre dos lecturas (interpolación), y la distancia se va sumando.
 * - Semáforo opcional: 3 ámbar y verde; mide tu tiempo de reacción (o "salida en falso").
 * - Frenado: de 40 a 0 km/h, cuántos metros y segundos tardas y la desaceleración máxima en g.
 */
object Arrancones {
    val prueba = MutableStateFlow(PruebaArr.ARRANCON)
    val fase = MutableStateFlow(FaseArr.ESPERA)
    val mensaje = MutableStateFlow("")
    val tiempo = MutableStateFlow(0f)          // s desde la salida (en vivo)
    val velocidad = MutableStateFlow(0f)
    val distancia = MutableStateFlow(0f)       // m desde la salida
    /** Luces del semáforo: 0 apagado, 1-3 ámbar, 4 verde, -1 salida en falso. */
    val semaforo = MutableStateFlow(0)
    val resultado = MutableStateFlow<ResultadoArr?>(null)
    val freno = MutableStateFlow<ResultadoFreno?>(null)
    val mejores = MutableStateFlow<ResultadoArr?>(null)       // mejor de cada marca (combinado)
    val mejorFreno = MutableStateFlow<ResultadoFreno?>(null)
    val historial = MutableStateFlow<List<ResultadoArr>>(emptyList())
    /** Qué marcas fueron récord en el último intento. */
    val records = MutableStateFlow<Set<String>>(emptySet())

    private var ctx: Context? = null
    private var prefs: SharedPreferences? = null
    private var sm: SensorManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var trabajo: Job? = null
    private var abierto = false

    // acelerómetro
    private var aSuave = 0f
    private var fx = 0f; private var fy = 0f; private var fz = 0f
    private var tSensor = 0L
    private var gx = 0f; private var gy = 0f; private var gz = 0f
    private var lineal = false
    private var sobreDesde = 0L          // ns: desde cuándo pasa del umbral
    private var gMax = 0f
    // carrera
    private var t0 = 0L                  // ns (elapsedRealtimeNanos) de la salida
    private var tVerde = 0L
    private var paradoDesde = 0L
    private var vPrev = -1f
    private var tPrev = 0L
    private var dPrev = 0f
    private var t40 = 0f; private var t60 = 0f; private var t80 = 0f
    private var t100 = 0f; private var v100 = 0f; private var t201 = 0f; private var v201 = 0f
    private var vmax = 0f
    private var bajando = 0
    private var desdeFreno = 0f

    fun iniciar(c: Context) {
        if (ctx != null) return
        val app = c.applicationContext
        ctx = app
        val p = app.getSharedPreferences("arrancones", Context.MODE_PRIVATE)
        prefs = p
        historial.value = try {
            val a = JSONArray(p.getString("historial", "[]") ?: "[]")
            List(a.length()) { leerArr(a.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
        mejores.value = p.getString("mejores", null)?.let { try { leerArr(JSONObject(it)) } catch (_: Exception) { null } }
        mejorFreno.value = p.getString("mejorFreno", null)?.let { try { leerFreno(JSONObject(it)) } catch (_: Exception) { null } }
        sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        scope.launch { UbicacionManager.ubicacion.collect { l -> if (l != null && abierto) gps(l) } }
    }

    /** Al abrir la pantalla de arrancones. */
    fun abrir() {
        if (abierto) return
        abierto = true
        UbicacionManager.arrancar()
        val s = sm
        val sensor = s?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        lineal = sensor != null
        val usar = sensor ?: s?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // Lo más rápido que dé (con HIGH_SAMPLING_RATE_SENSORS pasa de 200 por segundo)
        if (usar != null) s?.registerListener(oyente, usar, 0)
        tSensor = 0L; fx = 0f; fy = 0f; fz = 0f
        reiniciar()
    }

    fun cerrar() {
        abierto = false
        sm?.unregisterListener(oyente)
        trabajo?.cancel()
        semaforo.value = 0
    }

    fun ponerPrueba(p: PruebaArr) { prueba.value = p; reiniciar() }

    /** Otra vez: regresa a esperar. */
    fun reiniciar() {
        trabajo?.cancel()
        fase.value = FaseArr.ESPERA
        semaforo.value = 0
        tiempo.value = 0f; distancia.value = 0f
        records.value = emptySet()
        t0 = 0L; tVerde = 0L; vPrev = -1f; dPrev = 0f; paradoDesde = 0L; sobreDesde = 0L; gMax = 0f; bajando = 0
        t40 = 0f; t60 = 0f; t80 = 0f; t100 = 0f; v100 = 0f; t201 = 0f; v201 = 0f; vmax = 0f
        mensaje.value = if (prueba.value == PruebaArr.ARRANCON) "Detén la moto por completo" else "Acelera a más de 40 km/h"
    }

    /** Semáforo: 3 ámbar cada medio segundo y luego verde. */
    fun arrancarSemaforo() {
        if (fase.value != FaseArr.LISTO) return
        fase.value = FaseArr.SEMAFORO
        trabajo?.cancel()
        trabajo = scope.launch {
            for (i in 1..3) { semaforo.value = i; mensaje.value = "Prepárate…"; delay(500) }
            if (fase.value != FaseArr.SEMAFORO) return@launch
            semaforo.value = 4
            tVerde = SystemClock.elapsedRealtimeNanos()
            mensaje.value = "¡VERDE!"
        }
    }

    // ---------------- acelerómetro: salida y frenada ----------------

    private val oyente = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            var x = e.values[0]; var y = e.values[1]; var z = e.values[2]
            val dt = if (tSensor == 0L) 0.0025f else ((e.timestamp - tSensor) / 1e9f).coerceIn(0.0005f, 0.05f)
            tSensor = e.timestamp
            if (!lineal) {
                // sin sensor de aceleración lineal: se le quita la gravedad (promedio muy lento, ~0.2 Hz)
                val ag = 1f - exp(-2f * PI.toFloat() * 0.2f * dt)
                gx += ag * (x - gx); gy += ag * (y - gy); gz += ag * (z - gz)
                x -= gx; y -= gy; z -= gz
            }
            // Filtro de paso bajo en VECTOR (~1.5 Hz): la vibración del motor (25-75 Hz) va y viene y se cancela;
            // el empujón de la salida (o la frenada) dura segundos y sí queda. Leído a la velocidad máxima del
            // sensor para que la vibración no se "doble" en algo lento.
            val a = 1f - exp(-2f * PI.toFloat() * 1.5f * dt)
            fx += a * (x - fx); fy += a * (y - fy); fz += a * (z - fz)
            aSuave = sqrt(fx * fx + fy * fy + fz * fz)
            val f = fase.value
            if (f == FaseArr.CORRIENDO) gMax = max(gMax, aSuave / 9.81f)
            if (prueba.value != PruebaArr.ARRANCON || (f != FaseArr.LISTO && f != FaseArr.SEMAFORO)) return
            // Salida: más de 1.2 m/s² (~0.12 g) sostenido 150 ms
            if (aSuave > 1.2f) {
                if (sobreDesde == 0L) sobreDesde = e.timestamp
                // el filtro tarda ~50 ms en pasar el umbral: se descuenta
                else if (e.timestamp - sobreDesde > 150_000_000L) salida(sobreDesde - 50_000_000L)
            } else sobreDesde = 0L
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun salida(tNs: Long) {
        // Con semáforo: salir antes del verde es salida en falso
        if (fase.value == FaseArr.SEMAFORO && tVerde == 0L) {
            trabajo?.cancel()
            semaforo.value = -1
            mensaje.value = "¡Salida en falso!"
            fase.value = FaseArr.TERMINADO
            return
        }
        t0 = tNs
        fase.value = FaseArr.CORRIENDO
        mensaje.value = "¡Dale!"
        vPrev = 0f; tPrev = tNs; dPrev = 0f
        trabajo?.cancel()
        trabajo = scope.launch {
            while (isActive && fase.value == FaseArr.CORRIENDO) {
                tiempo.value = (SystemClock.elapsedRealtimeNanos() - t0) / 1e9f
                if (tiempo.value > 30f) terminarArrancon()
                delay(40)
            }
        }
    }

    // ---------------- GPS: tiempos y distancia ----------------

    private fun gps(l: Location) {
        val v = if (l.hasSpeed()) l.speed * 3.6f else return
        val t = l.elapsedRealtimeNanos
        velocidad.value = v
        if (prueba.value == PruebaArr.ARRANCON) gpsArrancon(v, t) else gpsFreno(v, t)
    }

    private fun gpsArrancon(v: Float, t: Long) {
        when (fase.value) {
            FaseArr.ESPERA -> {
                if (v < 1f) {
                    if (paradoDesde == 0L) paradoDesde = t
                    else if (t - paradoDesde > 2_000_000_000L) {
                        fase.value = FaseArr.LISTO
                        mensaje.value = "Listo: acelera cuando quieras (o usa el semáforo)"
                    }
                } else paradoDesde = 0L
            }
            FaseArr.LISTO, FaseArr.SEMAFORO -> {
                // Si el acelerómetro no notó la salida, el GPS sí: se estima cuándo pasó de 0 a 0.5 km/h
                if (v > 3f) {
                    val tPrevio = if (tPrev != 0L && vPrev >= 0f) tPrev else t - 1_000_000_000L
                    val tSalida = tPrevio + ((0.5f / v) * (t - tPrevio)).toLong()
                    if (fase.value == FaseArr.SEMAFORO && tVerde == 0L) { salida(tSalida); return }
                    salida(tSalida)
                    gpsArrancon(v, t)
                    return
                }
                vPrev = v; tPrev = t
            }
            FaseArr.CORRIENDO -> {
                if (t <= tPrev) return
                val dt = (t - tPrev) / 1e9f
                val d = dPrev + (vPrev + v) / 2f / 3.6f * dt
                fun cruce(marca: Float): Float {
                    val f = ((marca - vPrev) / (v - vPrev)).coerceIn(0f, 1f)
                    return ((tPrev - t0) / 1e9f) + f * dt
                }
                if (t40 == 0f && v >= 40f && vPrev < 40f) t40 = cruce(40f)
                if (t60 == 0f && v >= 60f && vPrev < 60f) t60 = cruce(60f)
                if (t80 == 0f && v >= 80f && vPrev < 80f) t80 = cruce(80f)
                fun marcaDist(m: Float): Pair<Float, Float> {
                    val f = ((m - dPrev) / (d - dPrev)).coerceIn(0f, 1f)
                    return (((tPrev - t0) / 1e9f) + f * dt) to (vPrev + f * (v - vPrev))
                }
                if (t100 == 0f && d >= 100f) { val (tt, vv) = marcaDist(100f); t100 = tt; v100 = vv }
                if (t201 == 0f && d >= 201f) { val (tt, vv) = marcaDist(201f); t201 = tt; v201 = vv }
                vmax = max(vmax, v)
                distancia.value = d
                bajando = if (v < vPrev - 2f) bajando + 1 else 0
                vPrev = v; tPrev = t; dPrev = d
                if (t201 > 0f || bajando >= 2) terminarArrancon()
            }
            else -> {}
        }
    }

    private fun terminarArrancon() {
        if (fase.value != FaseArr.CORRIENDO) return
        fase.value = FaseArr.TERMINADO
        trabajo?.cancel()
        val reaccion = if (tVerde != 0L) ((t0 - tVerde) / 1e9f).coerceAtLeast(0f) else 0f
        val r = ResultadoArr(System.currentTimeMillis(), t40, t60, t80, t100, v100, t201, v201, reaccion, vmax)
        resultado.value = r
        tiempo.value = if (t201 > 0f) t201 else (tPrev - t0) / 1e9f
        mensaje.value = if (t40 == 0f && t100 == 0f) "Muy corto: llega por lo menos a 40 km/h o 100 m" else "Terminado"
        if (t40 == 0f && t100 == 0f) return
        // récords (menor tiempo gana; velocidad de salida en la 1/8: mayor gana)
        val m = mejores.value
        val nuevos = HashSet<String>()
        fun mejor(a: Float, b: Float?, clave: String): Float {
            if (a <= 0f) return b ?: 0f
            if (b == null || b <= 0f || a < b) { if (b != null && b > 0f) nuevos += clave; return a }
            return b
        }
        val combinado = ResultadoArr(
            r.fecha, mejor(t40, m?.t40, "t40"), mejor(t60, m?.t60, "t60"), mejor(t80, m?.t80, "t80"),
            mejor(t100, m?.t100m, "t100"), if (m == null || (t100 > 0f && t100 <= (m.t100m.takeIf { it > 0f } ?: 999f))) v100 else m.v100m,
            mejor(t201, m?.t201m, "t201"), if (m == null || (t201 > 0f && t201 <= (m.t201m.takeIf { it > 0f } ?: 999f))) v201 else m.v201m,
            mejor(reaccion, m?.reaccion, "reaccion"), max(vmax, m?.vmax ?: 0f),
        )
        records.value = nuevos
        mejores.value = combinado
        historial.value = (listOf(r) + historial.value).take(20)
        guardar()
        Logros.revisar()
    }

    private fun gpsFreno(v: Float, t: Long) {
        when (fase.value) {
            FaseArr.ESPERA -> if (v >= 42f) { fase.value = FaseArr.LISTO; mensaje.value = "Frena fuerte cuando quieras" }
            FaseArr.LISTO -> {
                if (v < 40f && vPrev >= 40f) {
                    val f = ((40f - vPrev) / (v - vPrev)).coerceIn(0f, 1f)
                    t0 = tPrev + (f * (t - tPrev)).toLong()
                    desdeFreno = 40f
                    // lo recorrido desde el cruce hasta esta lectura
                    dPrev = (40f + v) / 2f / 3.6f * ((t - t0) / 1e9f)
                    fase.value = FaseArr.CORRIENDO
                    gMax = 0f
                    mensaje.value = "Frenando…"
                    vPrev = v; tPrev = t
                    return
                }
                vPrev = v; tPrev = t
            }
            FaseArr.CORRIENDO -> {
                if (t <= tPrev) return
                val dt = (t - tPrev) / 1e9f
                val d = dPrev + (vPrev + v) / 2f / 3.6f * dt
                distancia.value = d
                tiempo.value = (t - t0) / 1e9f
                if (v < 1f) {
                    // cuándo llegó a 0 (entre las dos lecturas)
                    val f = (vPrev / (vPrev - v).coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                    val seg = ((tPrev - t0) / 1e9f) + f * dt
                    val r = ResultadoFreno(System.currentTimeMillis(), desdeFreno, d, seg, gMax)
                    freno.value = r
                    fase.value = FaseArr.TERMINADO
                    tiempo.value = seg
                    val m = mejorFreno.value
                    records.value = if (m != null && d < m.metros) setOf("freno") else emptySet()
                    if (m == null || d < m.metros) mejorFreno.value = r
                    mensaje.value = "Terminado"
                    guardar()
                    return
                }
                vPrev = v; tPrev = t; dPrev = d
            }
            else -> {}
        }
    }

    fun borrarRecords() {
        mejores.value = null; mejorFreno.value = null; historial.value = emptyList()
        guardar()
    }

    // ---------------- guardar ----------------

    private fun guardar() {
        val a = JSONArray()
        historial.value.forEach { a.put(arrJson(it)) }
        prefs?.edit()?.putString("historial", a.toString())
            ?.putString("mejores", mejores.value?.let { arrJson(it).toString() })
            ?.putString("mejorFreno", mejorFreno.value?.let { frenoJson(it).toString() })
            ?.apply()
    }

    private fun arrJson(r: ResultadoArr) = JSONObject().apply {
        put("f", r.fecha); put("t40", r.t40.toDouble()); put("t60", r.t60.toDouble()); put("t80", r.t80.toDouble())
        put("t100", r.t100m.toDouble()); put("v100", r.v100m.toDouble()); put("t201", r.t201m.toDouble()); put("v201", r.v201m.toDouble())
        put("reac", r.reaccion.toDouble()); put("vmax", r.vmax.toDouble())
    }

    private fun leerArr(o: JSONObject) = ResultadoArr(
        o.optLong("f"), o.optDouble("t40", 0.0).toFloat(), o.optDouble("t60", 0.0).toFloat(), o.optDouble("t80", 0.0).toFloat(),
        o.optDouble("t100", 0.0).toFloat(), o.optDouble("v100", 0.0).toFloat(), o.optDouble("t201", 0.0).toFloat(), o.optDouble("v201", 0.0).toFloat(),
        o.optDouble("reac", 0.0).toFloat(), o.optDouble("vmax", 0.0).toFloat(),
    )

    private fun frenoJson(r: ResultadoFreno) = JSONObject().apply {
        put("f", r.fecha); put("desde", r.desde.toDouble()); put("m", r.metros.toDouble()); put("s", r.segundos.toDouble()); put("g", r.gMax.toDouble())
    }

    private fun leerFreno(o: JSONObject) = ResultadoFreno(
        o.optLong("f"), o.optDouble("desde", 0.0).toFloat(), o.optDouble("m", 0.0).toFloat(), o.optDouble("s", 0.0).toFloat(), o.optDouble("g", 0.0).toFloat(),
    )
}
