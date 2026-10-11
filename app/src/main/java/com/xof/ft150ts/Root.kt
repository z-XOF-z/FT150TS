package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Funciones con root (Magisk). Todo es opcional: la app pide root solo
 * cuando usas una de estas opciones, y Magisk te pregunta si lo permites.
 * Los comandos son fijos (no se arma nada con texto que escribas).
 */
object Root {
    /** null = no se ha revisado; true/false = resultado de pedir root. */
    val disponible = MutableStateFlow<Boolean?>(null)
    val ventanaManzana = MutableStateFlow(false)   // manzanita en ventana flotante sobre el mapa
    val despertar = MutableStateFlow(false)        // despertar y abrir el tablero al conectar la moto
    val sobreBloqueo = MutableStateFlow(false)     // tablero encima de la pantalla de bloqueo (no requiere root)
    /** Interruptor general: apagado, la app no usa nada de root (aunque Magisk lo permita). */
    val activo = MutableStateFlow(true)
    /** Gestos de pantalla completa de Xiaomi aunque esta app sea el launcher (experimental). */
    val gestos = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null
    private val PAQUETE_VALIDO = Regex("^[A-Za-z0-9_.]+$")

    data class Resultado(val ok: Boolean, val salida: String)

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("root", Context.MODE_PRIVATE)
        prefs = p
        ventanaManzana.value = p.getBoolean("ventana", false)
        despertar.value = p.getBoolean("despertar", false)
        sobreBloqueo.value = p.getBoolean("sobreBloqueo", false)
        activo.value = p.getBoolean("activo", true)
        gestos.value = p.getBoolean("gestos", false)
        if (p.getBoolean("concedido", false)) disponible.value = true
        // HyperOS regresa a los 3 botones al reiniciar el teléfono: se vuelven a poner al abrir la app
        if (gestos.value && activo.value && disponible.value == true) scope.launch { ejecutar(*CMD_GESTOS) }
    }

    private fun guardar(clave: String, v: Boolean) { prefs?.edit()?.putBoolean(clave, v)?.apply() }

    /** Corre comandos como root. Bloquea: llamar desde Dispatchers.IO. */
    fun ejecutar(vararg comandos: String): Resultado = try {
        val p = ProcessBuilder("su").redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { w ->
            for (c in comandos) { w.write(c); w.write("\n") }
            w.write("exit\n")
        }
        val salida = p.inputStream.bufferedReader().use { it.readText() }
        val termino = p.waitFor(20, TimeUnit.SECONDS)
        Resultado(termino && p.exitValue() == 0, salida)
    } catch (e: Exception) {
        Resultado(false, e.message ?: "")
    }

    /** Pide root (Magisk muestra su ventana la primera vez). */
    suspend fun pedir(): Boolean = withContext(Dispatchers.IO) {
        val ok = ejecutar("id").let { it.ok && it.salida.contains("uid=0") }
        disponible.value = ok
        guardar("concedido", ok)
        ok
    }

    private suspend fun conRoot(vararg comandos: String): Boolean {
        if (!activo.value) return false
        if (disponible.value != true && !pedir()) return false
        return withContext(Dispatchers.IO) { ejecutar(*comandos).ok }
    }

    /**
     * Da a la app lo que normalmente pides a mano: ubicación, Bluetooth,
     * acceso a notificaciones y que MIUI/Android no la cierre por ahorro de batería.
     */
    suspend fun darPermisos(ctx: Context): Boolean {
        val pk = ctx.packageName
        return conRoot(
            "pm grant $pk android.permission.ACCESS_FINE_LOCATION",
            "pm grant $pk android.permission.ACCESS_COARSE_LOCATION",
            "pm grant $pk android.permission.BLUETOOTH_SCAN",
            "pm grant $pk android.permission.BLUETOOTH_CONNECT",
            "cmd notification allow_listener $pk/$pk.NotifListener",
            "dumpsys deviceidle whitelist +$pk",
            "cmd appops set $pk RUN_IN_BACKGROUND allow",
            "cmd appops set $pk RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $pk SYSTEM_ALERT_WINDOW allow",
            "pm grant $pk android.permission.POST_NOTIFICATIONS",
            "true",
        )
    }

    /** La pone como pantalla de inicio sin pasar por los ajustes de Android. */
    suspend fun ponerComoInicio(ctx: Context): Boolean {
        val pk = ctx.packageName
        return conRoot(
            "pm enable $pk/$pk.Inicio",
            "cmd package set-home-activity $pk/$pk.Inicio",
        )
    }

    /** Deja de ser la pantalla de inicio (Android regresa al launcher de siempre). No necesita root. */
    fun quitarComoInicio(ctx: Context) {
        Lanzador.ponerAlias(ctx, false)
    }

    private val CMD_GESTOS = arrayOf(
        "settings put global force_fsg_nav_bar 1",
        "cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural",
        "true",
    )
    private val CMD_BOTONES = arrayOf(
        "settings put global force_fsg_nav_bar 0",
        "cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton",
        "true",
    )

    /**
     * Gestos de Xiaomi con esta app como launcher. MIUI/HyperOS pone los 3 botones cuando
     * el launcher no es el suyo; esto lo regresa a gestos. Deslizar para ir a Inicio y para atrás
     * funcionan; el de "recientes" (deslizar y mantener) puede no abrir en algunos teléfonos.
     */
    suspend fun ponerGestos(on: Boolean): Boolean {
        gestos.value = on
        guardar("gestos", on)
        return conRoot(*(if (on) CMD_GESTOS else CMD_BOTONES))
    }

    /** Activa las ventanas flotantes de Android (algunos teléfonos piden reiniciar la primera vez). */
    suspend fun prepararVentanas(): Boolean = conRoot(
        "settings put global enable_freeform_support 1",
        "settings put global force_resizable_activities 1",
    )

    /**
     * Despierta la pantalla y trae el tablero al frente (al conectar la moto).
     * Si el teléfono tiene bloqueo, el tablero sale encima solo con "Tablero sobre la pantalla de bloqueo".
     */
    fun despertarYAbrir(ctx: Context) {
        val pk = ctx.packageName
        if (!PAQUETE_VALIDO.matches(pk)) return
        scope.launch {
            if (!activo.value || disponible.value != true) return@launch
            ejecutar("input keyevent KEYCODE_WAKEUP", "am start -n $pk/$pk.MainActivity")
        }
    }

    fun ponerVentanaManzana(on: Boolean) { ventanaManzana.value = on; guardar("ventana", on) }
    fun ponerDespertar(on: Boolean) { despertar.value = on; guardar("despertar", on) }
    fun ponerSobreBloqueo(on: Boolean) { sobreBloqueo.value = on; guardar("sobreBloqueo", on) }
    fun ponerActivo(on: Boolean) { activo.value = on; guardar("activo", on) }
}
