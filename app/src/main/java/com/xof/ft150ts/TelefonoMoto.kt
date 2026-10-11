package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Una medición del ralentí (motor caliente, parado). [desv] = cuánto varía (rpm). */
data class MedidaRalenti(val fecha: Long, val rpm: Int, val desv: Int)

/** Lo que está midiendo el teléfono (para Ajustes y la página Moto). */
data class InfoTelefono(
    val fs: Float = 0f,             // muestras por segundo del acelerómetro
    val rms: Float = 0f,            // vibración en la banda del motor, m/s²
    val frecuencia: Float = 0f,     // línea más fuerte, Hz
    val confianza: Float = 0f,
    val rpm: Int = 0,
    val motor: Boolean = false,
    val rpmMaxConfiable: Int = 0,   // arriba de esto la línea calibrada ya no se alcanza a ver
    val inclinacion: Float = 0f,
)

/**
 * Sin la tarjeta (ESP32): el teléfono hace de sensor de la moto.
 * - RPM: vibración del motor en el acelerómetro (ver [EstimadorRpm]).
 * - Velocidad: GPS. Marcha: RPM ÷ velocidad (las relaciones se van ajustando solas).
 * - Inclinación: giroscopio + velocidad (asin(v·ω/g)). Aceleración y frenada: cambio de velocidad del GPS.
 * Solo trabaja con la tarjeta desconectada; en cuanto se conecta, mandan los datos de la tarjeta.
 * Todo entra a las mismas estadísticas y récords (no se borra nada).
 */
/** rpm por km/h de cada marcha de la FT150TS (aprox., de fábrica). */
val RATIOS_FABRICA = floatArrayOf(224f, 152f, 113f, 92f, 78f)

object TelefonoMoto {
    // Opciones (Ajustes > Sin la tarjeta)
    val activo = MutableStateFlow(true)
    val ralenti = MutableStateFlow(1500)
    val rpmCambio = MutableStateFlow(7000)
    /** El motor solo cuenta como prendido con el cable de la moto conectado (switch prendido). */
    val soloConCable = MutableStateFlow(true)
    /** Suavizado de las RPM: 0 rápido, 1 normal, 2 suave. */
    val suavizado = MutableStateFlow(1)
    val orden = MutableStateFlow(1f)
    val umbral = MutableStateFlow(0.12f)
    val calibrado = MutableStateFlow(false)

    // Estado
    val trabajando = MutableStateFlow(false)
    val info = MutableStateFlow(InfoTelefono())
    val calibrando = MutableStateFlow(false)
    val mensaje = MutableStateFlow("")

    // Salud del ralentí (motor caliente, parado)
    val ralentiMedido = MutableStateFlow(0)
    val ralentiDesv = MutableStateFlow(0)
    val historialRalenti = MutableStateFlow<List<MedidaRalenti>>(emptyList())
    /** Relaciones rpm por km/h de cada marcha (las de fábrica, ajustadas con lo que se va midiendo). */
    val relaciones = MutableStateFlow(RATIOS_FABRICA.toList())
    /** Qué tan segura está la marcha que se muestra (0–1). */
    val seguridadMarcha = MutableStateFlow(0f)

    private var ctx: Context? = null
    private var prefs: SharedPreferences? = null
    private var sm: SensorManager? = null
    private var hilo: HandlerThread? = null
    private var h: Handler? = null
    private val principal = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val estimador = EstimadorRpm()

    // ---- Todo lo de abajo solo se toca en el hilo "vibracion" ----
    private const val CAP = 4096
    private val bx = FloatArray(CAP)
    private val by = FloatArray(CAP)
    private val bz = FloatArray(CAP)
    private val bt = LongArray(CAP)
    private var pos = 0
    private var total = 0L
    private val wx = FloatArray(1024)
    private val wy = FloatArray(1024)
    private val wz = FloatArray(1024)
    private var gx = 0f; private var gy = 0f; private var gz = 9.81f      // gravedad (dirección "arriba" de la moto)
    private var rx = 0f; private var ry = 0f; private var rz = 0f         // giroscopio suavizado
    private var ratios = RATIOS_FABRICA.copyOf()                           // rpm por km/h de cada marcha (FT150TS aprox.)
    private val marchas = EstimadorMarcha(5)
    private val posiblesMarcha = FloatArray(5)
    private var rpmPrev = 0f            // RPM ya filtradas (las que se muestran)
    private var crudoPrev = 0f          // última lectura sin filtrar (para elegir la línea del espectro)
    private val filtro = FiltroRpm()
    private var ultimoProceso = 0L
    private var motor = false
    private var motorDudaDesde = 0L
    private var inclSuave = 0f
    private var aFwd = 0f
    private var vAntMs = -1f
    private var tVelAnt = 0L
    private var ultRatios = 0L
    private var motorDesde = 0L
    private val muestrasRalenti = ArrayList<Float>()
    private var ultimaMedidaRalenti = 0L
    // Baches e impactos: aceleración de arriba-abajo filtrada (< 8 Hz, sin la vibración del motor)
    private var vertFiltrada = 0f
    private var picoBache = 0f
    private var picoImpacto = 0f
    private var ultimoBache = 0L
    private var ultimoImpacto = 0L
    private var alfaVert = 0.12f
    private var necesitaRpm = false
    private val calFrec = ArrayList<Float>()
    private val calRms = ArrayList<Float>()
    private var calHasta = 0L

    fun iniciar(c: Context) {
        if (ctx != null) return
        val app = c.applicationContext
        ctx = app
        val p = app.getSharedPreferences("telefonoMoto", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", true)
        ralenti.value = p.getInt("ralenti", 1500)
        rpmCambio.value = p.getInt("rpmCambio", 7000)
        orden.value = p.getFloat("orden", 1f)
        umbral.value = p.getFloat("umbral", 0.12f)
        calibrado.value = p.getBoolean("calibrado", false)
        soloConCable.value = p.getBoolean("soloConCable", true)
        suavizado.value = p.getInt("suavizado", 1)
        filtro.suavizado = suavizado.value
        p.getString("ratios", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.let { if (it.size == 5) ratios = it.toFloatArray() }
        // Si lo aprendido se fue muy lejos de lo de fábrica (o quedó en desorden), se arregla
        if (!relacionesSanas(ratios)) { ratios = RATIOS_FABRICA.copyOf(); p.edit().remove("ratios").apply() }
        relaciones.value = ratios.toList()
        historialRalenti.value = (p.getString("ralentis", "") ?: "").split(';').mapNotNull { t ->
            val x = t.split(',')
            if (x.size == 3) MedidaRalenti(x[0].toLongOrNull() ?: 0L, x[1].toIntOrNull() ?: 0, x[2].toIntOrNull() ?: 0) else null
        }
        historialRalenti.value.lastOrNull()?.let { ralentiMedido.value = it.rpm; ralentiDesv.value = it.desv }
        sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        scope.launch { Moto.conexion.collect { revisar() } }
        scope.launch { Energia.enMoto.collect { revisar() } }
        scope.launch { ModoSueno.dormido.collect { revisar() } }
        scope.launch {
            while (isActive) { revisar(); delay(2_000) }
        }
    }

    private fun tarjetaActiva() = Moto.conexion.value == Conexion.CONECTADO || Moto.conexion.value == Conexion.DEMO

    /** ¿Los sensores están prendidos? (por las RPM o solo por el mapa de baches) */
    val sensores = MutableStateFlow(false)

    /**
     * Prende o apaga los sensores según haga falta: RPM sin la tarjeta, o detectar baches
     * (eso último también con la tarjeta conectada).
     */
    private fun revisar() {
        val enUso = (BleManager.primerPlano || Energia.enMoto.value) && !ModoSueno.dormido.value
        val rpm = activo.value && !tarjetaActiva() && (enUso || calibrando.value)
        val baches = Baches.activo.value && enUso
        necesitaRpm = rpm
        if (trabajando.value != rpm) {
            trabajando.value = rpm
            if (!rpm) {
                motor = false; h?.post { apagarMotor() }
                info.value = info.value.copy(motor = false, rpm = 0)
                if (!tarjetaActiva()) Moto.rapida.value = Rapida()    // que el tablero no se quede congelado
            } else UbicacionManager.arrancar()
        }
        val debe = rpm || baches
        if (debe && !sensores.value) arrancar()
        else if (!debe && sensores.value) detener()
    }

    private fun arrancar() {
        val s = sm ?: return
        val acel = s.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run { mensaje.value = "Este teléfono no tiene acelerómetro"; return }
        val giro = s.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val t = HandlerThread("vibracion").also { it.start() }
        val handler = Handler(t.looper)
        hilo = t
        h = handler
        // Lo más rápido que dé el sensor (con HIGH_SAMPLING_RATE_SENSORS pasa de 200/s)
        val periodo = max(acel.minDelay, 1000)
        val ok = try { s.registerListener(oyente, acel, periodo, handler) } catch (_: Exception) { false }
        if (!ok) s.registerListener(oyente, acel, 5_000, handler)     // respaldo: 200 por segundo
        if (giro != null) s.registerListener(oyente, giro, SensorManager.SENSOR_DELAY_GAME, handler)
        handler.postDelayed(ciclo, 500)
        sensores.value = true
        UbicacionManager.arrancar()
    }

    private fun detener() {
        sm?.unregisterListener(oyente)
        h?.removeCallbacksAndMessages(null)
        hilo?.quitSafely()
        hilo = null
        h = null
        sensores.value = false
        motor = false
        apagarMotor()
        pos = 0
        total = 0
    }

    private val oyente = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                    bx[pos] = x; by[pos] = y; bz[pos] = z; bt[pos] = e.timestamp
                    pos = (pos + 1) % CAP
                    total++
                    // gravedad: promedio lento (~1 s). En curva sigue apuntando al "arriba" de la moto
                    gx += 0.003f * (x - gx); gy += 0.003f * (y - gy); gz += 0.003f * (z - gz)
                    // arriba-abajo sin la gravedad, filtrado a < 8 Hz (los baches; la vibración del motor es más rápida)
                    val ng = sqrt(gx * gx + gy * gy + gz * gz)
                    if (ng > 1f) {
                        val vert = (x * gx + y * gy + z * gz) / ng - ng
                        vertFiltrada += alfaVert * (vert - vertFiltrada)
                        picoBache = max(picoBache, abs(vertFiltrada))
                        // golpe muy fuerte (más de 3.5 g de diferencia): posible caída o choque
                        picoImpacto = max(picoImpacto, abs(sqrt(x * x + y * y + z * z) - ng))
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    rx += 0.2f * (e.values[0] - rx); ry += 0.2f * (e.values[1] - ry); rz += 0.2f * (e.values[2] - rz)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val ciclo = object : Runnable {
        override fun run() {
            try { procesar() } catch (_: Exception) { }
            h?.postDelayed(this, 250)
        }
    }

    private fun procesar() {
        val disp = min(total, CAP.toLong()).toInt()
        if (disp < 160) return
        // Frecuencia real de muestreo con las últimas muestras
        val nfs = min(disp, 600)
        val tUlt = bt[(pos - 1 + CAP) % CAP]
        val tIni = bt[(pos - nfs + CAP) % CAP]
        if (tUlt <= tIni) return
        val fs = (nfs - 1) * 1e9f / (tUlt - tIni)
        alfaVert = (1f - kotlin.math.exp(-2f * PI.toFloat() * 8f / fs)).coerceIn(0.01f, 1f)
        val vel = UbicacionManager.velocidadKmh()
        val ahora = SystemClock.elapsedRealtime()
        revisarGolpes(vel, ahora)
        if (!necesitaRpm) return
        // Ventana de ~1.2 s (potencia de 2)
        var n = 128
        while (n < fs * 1.2f && n < 1024) n = n shl 1
        n = min(n, disp)
        for (i in 0 until n) {
            val j = (pos - n + i + CAP) % CAP
            wx[i] = bx[j]; wy[i] = by[j]; wz[i] = bz[j]
        }
        val an = estimador.analizar(wx, wy, wz, n, fs)

        val dtProc = if (ultimoProceso == 0L) 0.25f else ((ahora - ultimoProceso) / 1000f).coerceIn(0.05f, 1f)
        ultimoProceso = ahora

        // ¿Motor prendido? Con el cable de la moto como "switch": sin cable (moto apagada) no hay motor,
        // aunque el teléfono sienta vibración (en la mano, en la mesa, caminando...)
        val switchPrendido = !soloConCable.value || Energia.cargando.value || NfcMoto.presente.value
        val u = umbral.value
        if (!switchPrendido) {
            if (motor) { motor = false; apagarMotor() }
        } else if (!motor) {
            if (an.rms > u && an.confianza > 2.2f) { motor = true; motorDudaDesde = 0L; motorDesde = ahora }
        } else {
            val duda = an.rms < u * 0.7f || an.confianza < 1.6f
            if (!duda) motorDudaDesde = 0L
            else if (motorDudaDesde == 0L) motorDudaDesde = ahora
            else if (ahora - motorDudaDesde > 2_500) { motor = false; apagarMotor() }
        }

        // Aceleración/frenada con el cambio de velocidad del GPS
        UbicacionManager.ubicacion.value?.let { l ->
            if (l.hasSpeed() && l.time != tVelAnt) {
                val dt = (l.time - tVelAnt) / 1000f
                if (tVelAnt > 0 && vAntMs >= 0 && dt in 0.3f..3f) aFwd += 0.5f * ((l.speed - vAntMs) / dt / 9.81f - aFwd)
                vAntMs = l.speed
                tVelAnt = l.time
            }
        }
        if (vel < 0) aFwd = 0f

        var marcha = -1
        if (motor) {
            // Qué marchas son posibles a esta velocidad: ayuda a escoger bien la línea de las RPM
            if (vel > 3f) marchas.posibles(vel, ratios, ralenti.value, posiblesMarcha)
            val (rpm, _) = estimador.elegir(an, orden.value, vel, ratios, ralenti.value, crudoPrev,
                posibles = if (vel > 3f) posiblesMarcha else null)
            if (rpm > 0f) {
                crudoPrev = rpm
                // Filtro de Kalman: estable sin quedarse atrás; ignora brincos sueltos
                filtro.suavizado = suavizado.value
                rpmPrev = filtro.actualizar(rpm, an.confianza, dtProc)
            }
            // Marcha: velocidad + RPM + la marcha anterior (filtro de probabilidad, ver Marcha.kt)
            marcha = marchas.actualizar(vel, if (rpm > 0f) rpmPrev else 0f, an.confianza, ratios, ralenti.value, dtProc)
            if (rpm > 0f) aprenderMarcha(marcha, vel, an.confianza)
            val seg = (marchas.seguridad * 20f).roundToInt() / 20f
            if (seg != seguridadMarcha.value) seguridadMarcha.value = seg
            medirRalenti(vel, ahora)
        } else { muestrasRalenti.clear(); marchas.reiniciar() }

        // Inclinación: asin(v·ω/g), con ω = giro alrededor del "arriba" de la moto
        val ng = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(0.1f)
        val omega = (rx * gx + ry * gy + rz * gz) / ng
        val incl = if (vel > 10f) {
            val s = ((vel / 3.6f) * omega / 9.81f).coerceIn(-0.95f, 0.95f)
            abs(asin(s) * 180f / PI.toFloat())
        } else 0f
        inclSuave += 0.3f * (incl - inclSuave)

        if (calibrando.value) acumularCalibracion(an, ahora)

        val o = orden.value
        // Redondeado (10 rpm, medio km/h, 1°): si no cambia, la pantalla no se redibuja
        val rpmSalida = if (motor) ((rpmPrev / 10f).roundToInt() * 10) else 0
        info.value = InfoTelefono(
            fs = fs, rms = an.rms, frecuencia = an.picos.firstOrNull()?.frecuencia ?: 0f, confianza = an.confianza,
            rpm = rpmSalida, motor = motor, rpmMaxConfiable = (an.fMax * 60f / o).toInt(), inclinacion = inclSuave,
        )
        val r = Rapida(
            vel = if (vel >= 0) (vel * 2f).roundToInt() / 2f else -1f,
            rpm = rpmSalida,
            marcha = if (motor) marcha else -1,
            incl = if (vel >= 0) inclSuave.roundToInt().toFloat() else -1f,
            aFwd = (aFwd * 50f).roundToInt() / 50f,
            shift = rpmCambio.value,
            ignicion = switchPrendido,
            motor = motor,
        )
        principal.post { if (trabajando.value && !tarjetaActiva()) Moto.recibirTelefono(r) }
    }

    private fun apagarMotor() {
        rpmPrev = 0f
        crudoPrev = 0f
        filtro.reiniciar()
        marchas.reiniciar()
        muestrasRalenti.clear()
    }

    /** Golpes de arriba-abajo rodando = bache; golpe enorme = posible caída (guarda el video de la dashcam). */
    private fun revisarGolpes(vel: Float, ahora: Long) {
        val pico = picoBache
        val impacto = picoImpacto
        picoBache = 0f
        picoImpacto = 0f
        if (impacto > 35f && ahora - ultimoImpacto > 30_000) {
            ultimoImpacto = ahora
            principal.post { Dashcam.protegerPorImpacto() }
        }
        val u = Baches.umbral()
        if (Baches.activo.value && vel > 15f && pico > u && ahora - ultimoBache > 1_000) {
            ultimoBache = ahora
            principal.post { Baches.registrar(pico) }
        }
    }

    /**
     * Ralentí: con el motor prendido más de 4 min (caliente) y la moto parada 10 s, promedia las RPM.
     * Si varían mucho, el ralentí está irregular (bujía, carburador, filtro de aire...).
     */
    private fun medirRalenti(vel: Float, ahora: Long) {
        if (vel < 0f || vel > 2f || ahora - motorDesde < 4 * 60_000L) { muestrasRalenti.clear(); return }
        muestrasRalenti += rpmPrev
        if (muestrasRalenti.size < 40) return                       // 10 s (4 por segundo)
        val prom = muestrasRalenti.average().toFloat()
        val desv = sqrt(muestrasRalenti.map { (it - prom) * (it - prom) }.average()).toFloat()
        muestrasRalenti.clear()
        if (prom > 3000f || desv > 500f) return                     // estaban acelerando parados: no cuenta
        ralentiMedido.value = prom.toInt()
        ralentiDesv.value = desv.toInt()
        if (ahora - ultimaMedidaRalenti < 5 * 60_000L) return       // al historial, una cada 5 min
        ultimaMedidaRalenti = ahora
        val l = (historialRalenti.value + MedidaRalenti(System.currentTimeMillis(), prom.toInt(), desv.toInt())).takeLast(30)
        historialRalenti.value = l
        prefs?.edit()?.putString("ralentis", l.joinToString(";") { "${it.fecha},${it.rpm},${it.desv}" })?.apply()
    }

    /** Cómo está el ralentí respecto a lo normal (el que pusiste al calibrar). */
    fun estadoRalenti(): String {
        val r = ralentiMedido.value
        if (r <= 0) return "Sin medir (motor caliente, parado 10 s)"
        val ref = ralenti.value
        return when {
            r < ref - 250 -> "Bajo: se puede apagar en los altos"
            r > ref + 350 -> "Alto: revisa el ahogador o la carburación"
            ralentiDesv.value > 120 -> "Irregular: revisa bujía, filtro de aire o carburador"
            else -> "Bien"
        }
    }

    /**
     * Rodando parejo en una marcha que ya es segura: la relación rpm/km/h de esa marcha se acerca poco a poco
     * a lo medido. Con topes: no se aleja más de ±20 % de lo de fábrica y no se encima con las de junto.
     */
    private fun aprenderMarcha(g: Int, vel: Float, conf: Float) {
        if (g < 1 || vel < 15f || abs(aFwd) > 0.04f || conf < 3.5f || marchas.seguridad < 0.85f) return
        val q = rpmPrev / vel
        val i = g - 1
        val e = abs(q - ratios[i]) / ratios[i]
        if (e > 0.08f) return
        var nuevo = ratios[i] + 0.02f * (q - ratios[i])
        nuevo = nuevo.coerceIn(RATIOS_FABRICA[i] * 0.8f, RATIOS_FABRICA[i] * 1.2f)
        if (i > 0) nuevo = min(nuevo, ratios[i - 1] / 1.06f)
        if (i < ratios.size - 1) nuevo = max(nuevo, ratios[i + 1] * 1.06f)
        ratios[i] = nuevo
        val ahora = SystemClock.elapsedRealtime()
        if (ahora - ultRatios > 30_000) {
            ultRatios = ahora
            relaciones.value = ratios.toList()
            prefs?.edit()?.putString("ratios", ratios.joinToString(","))?.apply()
        }
    }

    private fun relacionesSanas(r: FloatArray): Boolean {
        if (r.size != RATIOS_FABRICA.size) return false
        for (i in r.indices) {
            if (r[i].isNaN() || r[i] < RATIOS_FABRICA[i] * 0.8f || r[i] > RATIOS_FABRICA[i] * 1.2f) return false
            if (i > 0 && r[i] >= r[i - 1]) return false
        }
        return true
    }

    // ---------------- Calibración en ralentí ----------------

    /** Moto en neutral, motor en ralentí y el teléfono en el soporte: mide 6 segundos. */
    fun calibrar() {
        if (calibrando.value) return
        mensaje.value = "Midiendo… deja la moto en neutral y en ralentí (6 s)"
        calibrando.value = true
        revisar()
        val handler = h
        if (handler == null) {
            calibrando.value = false
            if (tarjetaActiva()) mensaje.value = "Con la tarjeta conectada las RPM las da la tarjeta"
            return
        }
        handler.post {
            calFrec.clear(); calRms.clear()
            calHasta = SystemClock.elapsedRealtime() + 6_000
        }
    }

    private fun acumularCalibracion(an: Analisis, ahora: Long) {
        if (calHasta == 0L) return
        an.picos.firstOrNull()?.let { calFrec += it.frecuencia }
        calRms += an.rms
        if (ahora < calHasta) return
        calHasta = 0L
        calibrando.value = false
        if (calRms.isEmpty() || calFrec.size < 5) { mensaje.value = "No alcancé a medir; intenta otra vez"; return }
        val rms = calRms.sorted()[calRms.size / 2]
        val f = calFrec.sorted()[calFrec.size / 2]
        if (rms < 0.02f) {
            mensaje.value = "Casi no siento vibración: ¿está prendido el motor y el teléfono en el soporte?"
            return
        }
        val crudo = f * 60f / ralenti.value
        val cercano = EstimadorRpm.ORDENES.minByOrNull { abs(it - crudo) / it } ?: 1f
        val nuevo = if (abs(cercano - crudo) / cercano < 0.18f) cercano else crudo.coerceIn(0.4f, 3.5f)
        orden.value = nuevo
        umbral.value = (rms * 0.4f).coerceAtLeast(0.03f)
        calibrado.value = true
        apagarMotor()
        prefs?.edit()?.putFloat("orden", nuevo)?.putFloat("umbral", umbral.value)?.putBoolean("calibrado", true)?.apply()
        val nombre = if (nuevo == 1f) "1x (la del cigüeñal)" else "${fmtCorto(nuevo)}x"
        mensaje.value = "Listo: vibra a ${fmtCorto(f)} Hz en ralentí → línea $nombre"
    }

    private fun fmtCorto(v: Float) = if (v == v.toInt().toFloat()) "${v.toInt()}" else String.format(java.util.Locale.US, "%.1f", v)

    // ---------------- Ajustes ----------------

    fun ponerActivo(on: Boolean) {
        activo.value = on
        prefs?.edit()?.putBoolean("activo", on)?.apply()
        revisar()
    }

    fun ponerRalenti(rpm: Int) {
        ralenti.value = rpm.coerceIn(900, 2500)
        prefs?.edit()?.putInt("ralenti", ralenti.value)?.apply()
    }

    fun ponerSoloConCable(on: Boolean) {
        soloConCable.value = on
        prefs?.edit()?.putBoolean("soloConCable", on)?.apply()
    }

    fun ponerSuavizado(n: Int) {
        suavizado.value = n.coerceIn(0, 2)
        prefs?.edit()?.putInt("suavizado", suavizado.value)?.apply()
    }

    fun ponerRpmCambio(rpm: Int) {
        rpmCambio.value = rpm.coerceIn(3000, 10000)
        prefs?.edit()?.putInt("rpmCambio", rpmCambio.value)?.apply()
    }

    /** Regresa la calibración y las marchas aprendidas a como venían. */
    fun reiniciarCalibracion() {
        orden.value = 1f; umbral.value = 0.12f; calibrado.value = false
        h?.post { ratios = RATIOS_FABRICA.copyOf(); relaciones.value = ratios.toList(); apagarMotor() }
        prefs?.edit()?.remove("orden")?.remove("umbral")?.remove("calibrado")?.remove("ratios")?.apply()
        mensaje.value = "Calibración borrada"
    }
}
