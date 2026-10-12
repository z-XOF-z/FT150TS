package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reposo: la moto está estacionada, el teléfono cargando y nada se mueve. Es el paso antes del modo sueño
 * (que llega mucho después): a los pocos minutos parado, la app apaga lo que más gasta y ahorita no sirve:
 *  - los sensores de vibración (RPM sin la tarjeta), que leen cientos de veces por segundo,
 *  - el GPS (si no se lo está prestando a la tarjeta),
 *  - los 120 Hz de la pantalla (baja a 60) y, si quieres, cambia el tablero por un reloj negro atenuado (AMOLED).
 *
 * Despierta al instante si das marcha (al arrancar el voltaje baja y la carga se corta un momento),
 * si el sensor de movimiento siente que llevas el teléfono, si tocas la pantalla o si se conecta la tarjeta.
 */
object Reposo {
    // Ajustes
    val activo = MutableStateFlow(true)
    val minutos = MutableStateFlow(3)
    /** Qué hace la pantalla en reposo: 0 = reloj negro atenuado, 1 = dejar que se apague. */
    val pantalla = MutableStateFlow(0)

    // Estado
    val enReposo = MutableStateFlow(false)
    /** Desde cuándo (hora del reloj) cumple: cargando y sin moverse. 0 = no cumple. */
    val quietoDesde = MutableStateFlow(0L)
    val ultimoEvento = MutableStateFlow("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val principal = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private var sm: SensorManager? = null
    private var sensorMov: Sensor? = null
    private var iniciado = false

    private val movimiento = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            principal.post { salir("El teléfono se movió") }
        }
    }

    fun iniciar(c: Context) {
        if (iniciado) return
        iniciado = true
        val app = c.applicationContext
        val p = app.getSharedPreferences("reposo", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", true)
        minutos.value = p.getInt("minutos", 3)
        pantalla.value = p.getInt("pantalla", 0).coerceIn(0, 1)
        ultimoEvento.value = p.getString("evento", "") ?: ""
        sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorMov = sm?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        scope.launch {
            while (isActive) {
                delay(10_000)
                vigilar()
            }
        }
        // Al dar marcha la carga se corta y regresa: cualquier cambio en la carga despierta
        scope.launch {
            Energia.cargando.collect { carga ->
                if (enReposo.value) salir(if (carga) "Regresó la carga (¿diste marcha?)" else "Se cortó la carga")
            }
        }
        scope.launch { Moto.conexion.collect { if (it == Conexion.CONECTADO) salir("Se conectó la tarjeta de la moto") } }
        // El modo sueño apaga todavía más cosas: el reposo le cede el paso
        scope.launch { ModoSueno.dormido.collect { if (it) salir("Pasó a modo sueño", reanudar = false) } }
    }

    private fun vigilar() {
        if (ModoSueno.dormido.value) { quietoDesde.value = 0L; return }
        val cumple = activo.value && Energia.cargando.value && !ModoSueno.moviendose()
        if (enReposo.value) {
            if (!cumple) salir(if (!activo.value) "Apagaste el reposo" else "En uso o moviéndose")
            return
        }
        if (!cumple) { quietoDesde.value = 0L; return }
        val ahora = System.currentTimeMillis()
        if (quietoDesde.value == 0L) { quietoDesde.value = ahora; return }
        if (ahora - quietoDesde.value >= minutos.value * 60_000L) entrar()
    }

    private fun entrar() {
        if (enReposo.value) return
        enReposo.value = true
        quietoDesde.value = 0L
        try { sensorMov?.let { sm?.requestTriggerSensor(movimiento, it) } } catch (_: Exception) { }
        // El GPS se apaga, salvo que la tarjeta lo esté usando
        if (!(BleManager.conectado() && Moto.enviarGps.value)) UbicacionManager.pausar()
        evento("${ModoSueno.hora(System.currentTimeMillis())} · reposo: parada y cargando ${minutos.value} min")
    }

    /** Sale del reposo. [reanudar] = vuelve a prender el GPS si alguien lo usa (no al pasar a modo sueño). */
    fun salir(motivo: String, reanudar: Boolean = true) {
        if (!enReposo.value) return
        enReposo.value = false
        quietoDesde.value = 0L
        try { sm?.cancelTriggerSensor(movimiento, sensorMov) } catch (_: Exception) { }
        evento("${ModoSueno.hora(System.currentTimeMillis())} · despertó: $motivo")
        if (reanudar && !ModoSueno.dormido.value && (BleManager.primerPlano || TelefonoMoto.trabajando.value)) UbicacionManager.arrancar()
    }

    /** Tocaste la pantalla: despierta, o vuelve a contar desde cero. */
    fun actividad() {
        if (enReposo.value) { salir("Tocaste la pantalla"); return }
        if (quietoDesde.value != 0L) quietoDesde.value = System.currentTimeMillis()
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on
        prefs?.edit()?.putBoolean("activo", on)?.apply()
        if (!on) { salir("Apagaste el reposo"); quietoDesde.value = 0L }
    }

    fun ponerMinutos(m: Int) {
        minutos.value = m
        prefs?.edit()?.putInt("minutos", m)?.apply()
    }

    fun ponerPantalla(p: Int) {
        pantalla.value = p.coerceIn(0, 1)
        prefs?.edit()?.putInt("pantalla", pantalla.value)?.apply()
    }

    private fun evento(t: String) {
        ultimoEvento.value = t
        prefs?.edit()?.putString("evento", t)?.apply()
    }
}
