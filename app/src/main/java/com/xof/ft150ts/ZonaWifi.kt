package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Zona Wi-Fi con el switch (v3.8, root). CarPlay inalámbrico necesita la zona "POCO F4" prendida:
 *  - Abres el switch → la prende (si estaba apagada) y cancela cualquier apagado pendiente.
 *  - Cierras el switch → espera [espera] s. Si lo vuelves a abrir antes, sigue prendida; si no, la apaga.
 *  - Un corte por cable flojo rodando o de < 8 s al dar marcha no cuenta (ya lo filtra Energia).
 *  - Con [soloLaMia] (de fábrica) solo apaga la zona que prendió la app: si la prendiste tú, no la toca.
 * Prende tu zona GUARDADA (mismo nombre y contraseña que conoce el iPhone) con el ayudante root (AyudanteRoot).
 * Registro: adb logcat -s FT150-ZW
 */
object ZonaWifi {
    val activo = MutableStateFlow(false)
    val soloLaMia = MutableStateFlow(true)
    /** Segundos de espera al cerrar el switch antes de apagarla. */
    val espera = MutableStateFlow(60)
    /** Último estado real leído (null = no se sabe). */
    val prendida = MutableStateFlow<Boolean?>(null)
    val evento = MutableStateFlow("")

    private var prefs: SharedPreferences? = null
    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + Seguro.manejador)
    private var apagado: Job? = null

    fun iniciar(ctx: Context) {
        app = ctx.applicationContext
        val p = ctx.applicationContext.getSharedPreferences("modoMoto", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("zonaSwitch", false)
        soloLaMia.value = p.getBoolean("zonaSoloMia", true)
        espera.value = p.getInt("zonaEspera", 60)
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on; prefs?.edit()?.putBoolean("zonaSwitch", on)?.apply()
        if (!on) apagado?.cancel()
    }
    fun ponerSoloLaMia(on: Boolean) { soloLaMia.value = on; prefs?.edit()?.putBoolean("zonaSoloMia", on)?.apply() }
    fun ponerEspera(s: Int) { espera.value = s.coerceIn(15, 600); prefs?.edit()?.putInt("zonaEspera", espera.value)?.apply() }

    /** La app la prendió (se guarda: si la app se reinicia, sigue sabiendo que la zona es "suya"). */
    private var laPrendiYo: Boolean
        get() = prefs?.getBoolean("zonaMia", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("zonaMia", v)?.apply() }

    private fun log(m: String) { android.util.Log.i("FT150-ZW", m); evento.value = m }

    /** Abriste el switch. */
    fun alPrender() {
        apagado?.cancel(); apagado = null
        if (!activo.value || !Root.listo()) { android.util.Log.i("FT150-ZW", "switch abierto: nada (activo=${activo.value}, root=${Root.listo()})"); return }
        scope.launch {
            val estado = leer()
            if (estado == true) { log("switch abierto: la zona ya estaba prendida"); return@launch }
            val r = Root.ayudante("zona-on")
            if (r == "OK") { laPrendiYo = true; prendida.value = true; log("switch abierto: zona prendida") }
            else log("no pude prender la zona: $r")
        }
    }

    /** Cerraste el switch (de verdad). Apaga después de la espera si no lo vuelves a abrir. */
    fun alApagar() {
        if (!activo.value || !Root.listo()) { android.util.Log.i("FT150-ZW", "switch cerrado: nada (activo=${activo.value}, root=${Root.listo()})"); return }
        apagado?.cancel()
        apagado = scope.launch {
            log("switch cerrado: apago la zona en ${espera.value} s si no lo vuelves a abrir")
            delay(espera.value * 1000L)
            if (!debeApagar(Energia.enMoto.value, soloLaMia.value, laPrendiYo)) {
                log("la zona se queda (switch abierto otra vez o la prendiste tú)")
                return@launch
            }
            val r = Root.ayudante("zona-off")
            if (r == "OK") { laPrendiYo = false; prendida.value = false; log("zona apagada") } else log("no pude apagar la zona: $r")
        }
    }

    /** ¿Toca apagar al terminar la espera? Solo si el switch sigue cerrado y la zona es nuestra (o no importa). */
    fun debeApagar(switchAbierto: Boolean, soloLaMia: Boolean, laPrendiYo: Boolean) = !switchAbierto && (!soloLaMia || laPrendiYo)

    /** Estado real (ON/OFF) o null si no se pudo leer. */
    suspend fun leer(): Boolean? {
        val r = Root.ayudante("zona-estado")
        val e = when (r) { "ON", "STARTING" -> true; "OFF", "STOPPING" -> false; else -> null }
        prendida.value = e
        return e
    }

    /** Botón de prueba en Ajustes. */
    fun probar(prender: Boolean) {
        if (!Root.listo()) { log("sin root"); return }
        scope.launch {
            val r = Root.ayudante(if (prender) "zona-on" else "zona-off")
            if (r == "OK") laPrendiYo = prender
            leer()
            log(if (r == "OK") (if (prender) "zona prendida (prueba)" else "zona apagada (prueba)") else "falló: $r")
        }
    }
}
