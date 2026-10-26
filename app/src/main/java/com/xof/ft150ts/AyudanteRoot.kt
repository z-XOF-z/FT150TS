package com.xof.ft150ts

import android.content.Context
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Ayudante que corre COMO ROOT en su propio proceso (no dentro de la app):
 *   su -c 'CLASSPATH=<apk> app_process /system/bin com.xof.ft150ts.AyudanteRoot zona-on'
 * Sirve para lo que Android no deja hacer a una app normal y que no tiene comando de shell. Hoy: prender y apagar
 * la zona Wi-Fi con TU configuración guardada (TetheringManager, el mismo camino que el botón de Ajustes).
 *
 * Seguridad: solo acepta las órdenes fijas de abajo (sin texto libre), contesta una línea y termina. Lo lanza
 * Root.ejecutarCada con un comando fijo (ver Root.cmdAyudante).
 */
object AyudanteRoot {
    private const val TETHERING_WIFI = 0
    private var modulos = false

    @JvmStatic
    fun main(args: Array<String>) {
        val r = try {
            when (args.firstOrNull()) {
                "zona-on" -> zona(true)
                "zona-off" -> zona(false)
                "zona-estado" -> estadoZona()
                "bt-lista" -> listaBt()
                "bt-conectar" -> bt(args.getOrNull(1), true)
                "bt-desconectar" -> bt(args.getOrNull(1), false)
                else -> "ERR orden desconocida"
            }
        } catch (t: Throwable) {
            "ERR ${t.javaClass.simpleName}: ${t.message}"
        }
        println(r)
        System.exit(0)
    }

    /**
     * Contexto del sistema dentro de app_process (como scrcpy): sin él no hay TetheringManager.
     * Las APIs internas de aquí se usan SOLO en este proceso root aparte (app_process no aplica las restricciones de
     * APIs ocultas de las apps); nunca dentro de la app.
     */
    @android.annotation.SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi", "PrivateApi")
    private fun contexto(): Context {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        val at = Class.forName("android.app.ActivityThread")
        // Módulos del sistema (Bluetooth, etc.): en una app los registra el arranque; aquí hay que pedirlo (una vez)
        if (!modulos) { try { at.getDeclaredMethod("initializeMainlineModules").invoke(null) } catch (_: Throwable) { }; modulos = true }
        val ctor = at.getDeclaredConstructor().apply { isAccessible = true }
        val hilo = ctor.newInstance()
        at.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }.set(null, hilo)
        return at.getDeclaredMethod("getSystemContext").apply { isAccessible = true }.invoke(hilo) as Context
    }

    private fun zona(prender: Boolean): String {
        val ctx = contexto()
        val tm = ctx.getSystemService("tethering") ?: return "ERR sin TetheringManager"
        val tmC = tm.javaClass
        if (!prender) {
            tmC.getMethod("stopTethering", Int::class.javaPrimitiveType).invoke(tm, TETHERING_WIFI)
            return "OK"
        }
        // TetheringRequest.Builder(TETHERING_WIFI).setShouldShowEntitlementUi(false).build()
        val bC = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
        val b = bC.getConstructor(Int::class.javaPrimitiveType).newInstance(TETHERING_WIFI)
        bC.getMethod("setShouldShowEntitlementUi", Boolean::class.javaPrimitiveType).invoke(b, false)
        val pedido = bC.getMethod("build").invoke(b)
        // El resultado llega por un callback (interfaz con métodos default): se espera hasta 10 s
        val cbC = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        val listo = CountDownLatch(1)
        var resultado = "ERR sin respuesta"
        val cb = java.lang.reflect.Proxy.newProxyInstance(cbC.classLoader, arrayOf(cbC)) { _, m, a ->
            when (m.name) {
                "onTetheringStarted" -> { resultado = "OK"; listo.countDown() }
                "onTetheringFailed" -> { resultado = "ERR tethering ${a?.getOrNull(0)}"; listo.countDown() }
            }
            null
        }
        val directo = Executor { it.run() }
        tmC.getMethod("startTethering", Class.forName("android.net.TetheringManager\$TetheringRequest"), Executor::class.java, cbC)
            .invoke(tm, pedido, directo, cb)
        listo.await(10, TimeUnit.SECONDS)
        return resultado
    }

    private val MAC = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    private fun adaptador(): android.bluetooth.BluetoothAdapter {
        contexto()
        // En app_process el BluetoothManager del contexto del sistema no trae adaptador: se pide directo al servicio
        @Suppress("DEPRECATION")
        return android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: error("sin Bluetooth")
    }

    /** Emparejados: MAC|nombre|conectado (una línea cada uno) y al final "OK". */
    @android.annotation.SuppressLint("MissingPermission")
    private fun listaBt(): String {
        val a = adaptador()
        val sb = StringBuilder()
        for (d in a.bondedDevices) {
            val conectado = d.javaClass.getMethod("isConnected").invoke(d) as Boolean
            sb.append(d.address).append('|').append(d.name ?: "").append('|').append(conectado).append('\n')
        }
        return sb.append("OK").toString()
    }

    /**
     * Conecta o desconecta TODOS los perfiles de un dispositivo ya emparejado (como tocarlo en Ajustes > Bluetooth).
     * BluetoothDevice.connect()/disconnect() son del sistema (BLUETOOTH_PRIVILEGED): como root sí se permiten.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun bt(mac: String?, conectar: Boolean): String {
        if (mac == null || !MAC.matches(mac)) return "ERR MAC inválida"
        val a = adaptador()
        val d = a.bondedDevices.firstOrNull { it.address == mac } ?: return "ERR no está emparejado"
        val r = d.javaClass.getMethod(if (conectar) "connect" else "disconnect").invoke(d) as Int
        return if (r == 0) "OK" else "ERR bluetooth $r"
    }

    /** ON / OFF según el estado real del punto de acceso (WifiManager.getWifiApState: 13 = prendido, 12 = prendiendo). */
    private fun estadoZona(): String {
        val ctx = contexto()
        val wm = ctx.getSystemService(Context.WIFI_SERVICE) ?: return "ERR sin WifiManager"
        val e = wm.javaClass.getMethod("getWifiApState").invoke(wm) as Int
        return when (e) { 13 -> "ON"; 12 -> "STARTING"; 10 -> "STOPPING"; else -> "OFF" }
    }
}
