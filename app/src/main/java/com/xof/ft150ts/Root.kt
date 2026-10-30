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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + Seguro.manejador)
    private var prefs: SharedPreferences? = null
    private val PAQUETE_VALIDO = Regex("^[A-Za-z0-9_.]+$")

    data class Resultado(val ok: Boolean, val salida: String)

    /** Root listo para usarse: el interruptor general prendido y Magisk ya lo concedió. */
    fun listo() = activo.value && disponible.value == true

    /** Ruta del APK instalado (el ayudante root se carga desde aquí). */
    @Volatile private var apk: String? = null
    private val RUTA_VALIDA = Regex("^[A-Za-z0-9/._=~+-]+$")
    /** Órdenes que acepta el ayudante (AyudanteRoot). Nada de texto libre. */
    private val ORDENES_AYUDANTE = setOf("zona-on", "zona-off", "zona-estado", "bt-lista", "bt-conectar", "bt-desconectar")
    /** El único parámetro que se acepta: una dirección Bluetooth (AA:BB:CC:DD:EE:FF). */
    private val MAC_VALIDA = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    /**
     * Corre una orden del ayudante root (proceso aparte con app_process, ver AyudanteRoot) y regresa su respuesta
     * ("OK", "ON", "OFF", "ERR …") o null si no hay root. Bloquea: llamar desde Dispatchers.IO.
     */
    fun ayudante(orden: String, mac: String? = null): String? {
        require(orden in ORDENES_AYUDANTE) { "orden no permitida" }
        require(mac == null || MAC_VALIDA.matches(mac)) { "MAC no válida" }
        val a = apk?.takeIf { RUTA_VALIDA.matches(it) } ?: return null
        val r = ejecutarCada(listOf("CLASSPATH=$a app_process /system/bin com.xof.ft150ts.AyudanteRoot $orden" + (mac?.let { " $it" } ?: "")))
            ?.firstOrNull() ?: return null
        return r.salida.lines().lastOrNull { it.isNotBlank() }?.trim()
    }

    /**
     * Captura solo [n] filas de la pantalla desde la fila [y0] (RGBA crudo, sin el encabezado de 16 bytes) en [ruta]
     * (archivo de la app) y la deja legible. Para los gestos de CarPlay. Números validados; ruta propia.
     */
    fun capturaFranja(ancho: Int, y0: Int, n: Int, ruta: String): Boolean? {
        require(ancho in 100..8000 && y0 in 0..8000 && n in 1..400)
        require(RUTA_VALIDA.matches(ruta) && ruta.contains("/com.xof.ft150ts/"))
        val fila = ancho * 4
        val r = ejecutarCada(listOf("screencap | tail -c +17 | dd bs=$fila skip=$y0 count=$n 2>/dev/null > $ruta && chmod 644 $ruta"))
            ?.firstOrNull() ?: return null
        return r.ok
    }

    fun iniciar(ctx: Context) {
        apk = ctx.applicationInfo.sourceDir
        if (prefs != null) return      // ya iniciado (también lo llama el horario nocturno desde una alarma)
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
        compilarTrasActualizar(ctx)
        permisosHyperOS(ctx)
    }

    /**
     * HyperOS regresa a "ignore" sus permisos propios cada vez que se actualiza la app (medido en el POCO F4):
     * 10008 inicio automático (sin él no deja revivir el servicio tras un cierre), 10020 mostrarse sobre la pantalla
     * de bloqueo y 10021 abrir pantallas desde segundo plano. Con root se vuelven a dar en cada arranque (comandos
     * fijos, paquete propio; si ya estaban, no cambia nada). Se quitan con "cmd appops set <paquete> <op> ignore".
     */
    fun permisosHyperOS(ctx: Context) {
        if (!listo()) return
        val ahora = android.os.SystemClock.elapsedRealtime()
        if (ahora - ultimosPermisos < 60_000L && ultimosPermisos != 0L) return
        ultimosPermisos = ahora
        val pk = ctx.packageName
        if (!PAQUETE_VALIDO.matches(pk)) return
        scope.launch {
            ejecutarCada(listOf(10008, 10020, 10021).map { "cmd appops set $pk $it allow" })
            // HyperOS (Security Center) los vuelve a quitar unos segundos DESPUÉS de instalar: otra pasada
            kotlinx.coroutines.delay(40_000)
            ejecutarCada(listOf(10008, 10020, 10021).map { "cmd appops set $pk $it allow" })
        }
    }
    @Volatile private var ultimosPermisos = 0L

    /**
     * Rendimiento: después de instalar o actualizar la app, HyperOS no la compila hasta que el teléfono pasa un rato
     * inactivo cargando; mientras tanto corre en el intérprete (medido: ~20 % más de CPU con el tablero andando).
     * Con root se le pide a Android que la compile YA con su Baseline Profile, una sola vez por versión, en segundo
     * plano. Comando fijo (paquete propio), sin texto de afuera. Tarda unos segundos; aplica al siguiente arranque.
     */
    private fun compilarTrasActualizar(ctx: Context) {
        val p = prefs ?: return
        if (!activo.value || disponible.value != true) return
        val version = try {
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
        } catch (_: Exception) { return }
        if (p.getLong("compilada", 0L) == version) return
        val paquete = ctx.packageName
        if (!PAQUETE_VALIDO.matches(paquete)) return
        scope.launch {
            kotlinx.coroutines.delay(20_000)        // que termine de arrancar (y el perfil de uso se registre)
            val r = ejecutar("cmd package compile -m speed-profile -f $paquete")
            if (r.ok) p.edit().putLong("compilada", version).apply()
        }
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

    /**
     * Corre cada comando en una sola sesión de root y regresa la salida y el código de cada uno (en orden).
     * null si no hay root o el interruptor general está apagado. Bloquea: llamar desde Dispatchers.IO.
     * Los comandos tienen que venir de una lista fija (LogicaNoche); aquí no se arma nada con texto de afuera.
     */
    fun ejecutarCada(comandos: List<String>): List<Resultado>? {
        if (!activo.value || disponible.value != true || comandos.isEmpty()) return null
        val script = comandos.flatMap { listOf("echo '$MARCA_INI'", "$it 2>&1", "echo \"$MARCA_FIN\$?\"") }
        // Una sola sesión de root abierta mientras la app vive: rápido (CarPlay aparece al instante) y Magisk solo
        // pide/avisa una vez. Si la sesión falla, se cae a una de un solo uso.
        val salida = enSesion(script) ?: ejecutar(*script.toTypedArray()).salida
        if (!salida.contains(MARCA_INI)) return null
        val partes = salida.split(MARCA_INI).drop(1)
        return comandos.indices.map { i ->
            val parte = partes.getOrNull(i) ?: return@map Resultado(false, "sin respuesta")
            val fin = parte.lastIndexOf(MARCA_FIN)
            if (fin < 0) Resultado(false, parte.trim())
            else Resultado(parte.substring(fin + MARCA_FIN.length).trim().startsWith("0"), parte.substring(0, fin).trim())
        }
    }

    private const val MARCA_INI = "@@FT150-INI@@"
    private const val MARCA_FIN = "@@FT150-FIN="

    // ---- Sesión de root persistente ----
    private val bloqueo = Any()
    private var sesion: Process? = null
    private var escritor: java.io.BufferedWriter? = null
    private var lector: java.io.BufferedReader? = null

    /** Corre [lineas] en la sesión abierta y regresa todo lo que imprimieron; null si la sesión no sirve. */
    private fun enSesion(lineas: List<String>): String? = synchronized(bloqueo) {
        try {
            if (sesion?.isAlive != true) {
                val p = ProcessBuilder("su").redirectErrorStream(true).start()
                sesion = p
                escritor = p.outputStream.bufferedWriter()
                lector = p.inputStream.bufferedReader()
            }
            val p = sesion!!
            val fin = "@@FT150-LISTO-${System.nanoTime()}@@"
            val w = escritor!!
            for (l in lineas) { w.write(l); w.write("\n") }
            w.write("echo '$fin'\n")
            w.flush()
            // Vigilante: si algo se cuelga más de 20 s, se cierra la sesión (la siguiente vez se abre otra)
            val listo = java.util.concurrent.atomic.AtomicBoolean(false)
            val vigilante = Thread { try { Thread.sleep(20_000) } catch (_: InterruptedException) { }; if (!listo.get()) p.destroy() }
            vigilante.isDaemon = true
            vigilante.start()
            val sb = StringBuilder()
            val r = lector!!
            while (true) {
                val linea = r.readLine() ?: break
                if (linea == fin) break
                sb.append(linea).append('\n')
            }
            listo.set(true)
            vigilante.interrupt()
            // Se murió a medio camino: lo que alcanzó a salir (no se repite el guion: algún comando ya pudo correr)
            if (!p.isAlive) { cerrarSesion(); return if (sb.isEmpty()) null else sb.toString() }
            sb.toString()
        } catch (_: Exception) {
            cerrarSesion()
            null
        }
    }

    private fun cerrarSesion() {
        try { sesion?.destroy() } catch (_: Exception) { }
        sesion = null; escritor = null; lector = null
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
            // Alarmas exactas (Android 14+ las niega de fábrica): el ahorro nocturno entra y sale a la hora
            "cmd appops set $pk SCHEDULE_EXACT_ALARM allow",
            // Permisos propios de HyperOS (v3.8, medido en el POCO F4: venían en "ignore" y rechazaba al tablero):
            // 10008 abrirse desde segundo plano (al abrir el switch), 10020 mostrarse sobre la pantalla de bloqueo
            // (el tablero sale sin deslizar; las demás apps siguen bloqueadas), 10021 ventanas en segundo plano.
            // Se revierten con "cmd appops set <paquete> <op> ignore".
            "cmd appops set $pk 10008 allow",
            "cmd appops set $pk 10020 allow",
            "cmd appops set $pk 10021 allow",
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
    fun ponerActivo(on: Boolean) {
        if (!on) ModoRendimiento.soltar()     // regresa el procesador a la normalidad mientras todavía hay root
        activo.value = on
        guardar("activo", on)
        if (!on) synchronized(bloqueo) { cerrarSesion() }       // apagado: no queda ninguna sesión de root abierta
    }
}
