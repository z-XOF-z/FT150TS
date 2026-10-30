package com.xof.ft150ts

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Un dispositivo Bluetooth emparejado o encontrado. [conectado] es el estado REAL que avisó el sistema. */
data class DispositivoBt(val nombre: String, val mac: String, val conectado: Boolean, val audio: Boolean, val emparejado: Boolean)

/**
 * Administración Bluetooth (v3.8) dentro de FT150TS: emparejados con su estado real, buscar y emparejar nuevos,
 * conectar / desconectar y un dispositivo preferido que se reconecta al abrir el switch.
 *  - Buscar, emparejar y leer el estado: API normal de Android (permisos BLUETOOTH_SCAN / CONNECT).
 *  - Conectar y desconectar uno ya emparejado: Android lo reserva al sistema (BLUETOOTH_PRIVILEGED); se hace con el
 *    ayudante root (BluetoothDevice.connect/disconnect, como al tocarlo en Ajustes). Sin root, abre Ajustes de Bluetooth.
 *  - Nunca se da por hecho: después de cada orden se revisa el estado real (avisos ACL y perfiles de audio).
 */
@SuppressLint("MissingPermission")
object BluetoothAdmin {
    val emparejados = MutableStateFlow<List<DispositivoBt>>(emptyList())
    val encontrados = MutableStateFlow<List<DispositivoBt>>(emptyList())
    val buscando = MutableStateFlow(false)
    val evento = MutableStateFlow("")
    /** Dispositivo que se reconecta solo al abrir el switch ("" = ninguno). */
    val preferido = MutableStateFlow("")
    val reconectarAlPrender = MutableStateFlow(true)

    private var ctx: Context? = null
    private var prefs: SharedPreferences? = null
    private var adapter: BluetoothAdapter? = null
    private val conectadosAcl = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + Seguro.manejador)
    private var registrado = false

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) i?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") i?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            when (i?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> d?.let { synchronized(conectadosAcl) { conectadosAcl.add(it.address) } }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> d?.let { synchronized(conectadosAcl) { conectadosAcl.remove(it.address) } }
                BluetoothDevice.ACTION_FOUND -> d?.let { agregarEncontrado(it) }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> buscando.value = false
            }
            refrescar()
        }
    }

    fun iniciar(c: Context) {
        ctx = c.applicationContext
        prefs = c.applicationContext.getSharedPreferences("bluetooth", Context.MODE_PRIVATE)
        preferido.value = prefs?.getString("preferido", "") ?: ""
        reconectarAlPrender.value = prefs?.getBoolean("reconectar", true) ?: true
        adapter = (c.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        registrar()
        refrescar()
    }

    private fun permiso(p: String): Boolean {
        val c = ctx ?: return false
        return Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    }

    /** Se llama al iniciar y otra vez cuando el usuario da los permisos. */
    fun registrar() {
        val c = ctx ?: return
        if (registrado || !permiso(Manifest.permission.BLUETOOTH_CONNECT)) return
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        try { ContextCompat.registerReceiver(c, receptor, f, ContextCompat.RECEIVER_NOT_EXPORTED); registrado = true } catch (_: Exception) { }
    }

    fun ponerPreferido(mac: String) { preferido.value = mac; prefs?.edit()?.putString("preferido", mac)?.apply() }
    fun ponerReconectar(on: Boolean) { reconectarAlPrender.value = on; prefs?.edit()?.putBoolean("reconectar", on)?.apply() }

    fun refrescar() {
        val a = adapter ?: return
        if (!permiso(Manifest.permission.BLUETOOTH_CONNECT)) return
        val audio = AudioSalida.dispositivos.value.map { it.direccion }.toSet()
        val acl = synchronized(conectadosAcl) { conectadosAcl.toSet() }
        emparejados.value = try {
            a.bondedDevices.map { d ->
                DispositivoBt(nombre(d), d.address, d.address in acl || d.address in audio, d.address in audio, true)
            }.sortedWith(compareByDescending<DispositivoBt> { it.conectado }.thenBy { it.nombre })
        } catch (_: Exception) { emptyList() }
    }

    private fun nombre(d: BluetoothDevice) = try { (if (Build.VERSION.SDK_INT >= 30) d.alias else null) ?: d.name ?: d.address } catch (_: Exception) { d.address }

    private fun agregarEncontrado(d: BluetoothDevice) {
        if (emparejados.value.any { it.mac == d.address }) return
        val n = try { d.name } catch (_: Exception) { null } ?: return       // sin nombre: no se puede reconocer
        encontrados.value = (encontrados.value.filter { it.mac != d.address } + DispositivoBt(n, d.address, false, false, false)).sortedBy { it.nombre }
    }

    /** Busca dispositivos nuevos (~12 s). */
    fun buscar() {
        val a = adapter ?: return
        if (!permiso(Manifest.permission.BLUETOOTH_SCAN)) { evento.value = "Falta el permiso de dispositivos cercanos"; return }
        encontrados.value = emptyList()
        try { a.cancelDiscovery(); buscando.value = a.startDiscovery() } catch (_: Exception) { buscando.value = false }
        if (!buscando.value) evento.value = "Android no dejó buscar ahora (¿Bluetooth apagado?)"
    }

    fun emparejar(mac: String) {
        val a = adapter ?: return
        try { a.cancelDiscovery() } catch (_: Exception) { }
        val ok = try { a.getRemoteDevice(mac).createBond() } catch (_: Exception) { false }
        evento.value = if (ok) "Emparejando… acepta en el otro dispositivo si lo pide" else "No se pudo empezar a emparejar"
    }

    /** Conecta o desconecta (root) y luego revisa el estado real. */
    fun conectar(mac: String, on: Boolean) {
        if (!Root.listo()) { evento.value = "Sin root: abre Ajustes de Bluetooth para conectarlo"; AudioSalida.abrirAjustesBluetooth(); return }
        scope.launch {
            evento.value = if (on) "Conectando…" else "Desconectando…"
            val r = Root.ayudante(if (on) "bt-conectar" else "bt-desconectar", mac)
            if (r != "OK") { evento.value = "El sistema no aceptó la orden: $r"; return@launch }
            // El sistema avisa al conectar; se espera hasta 10 s a que de verdad cambie
            repeat(10) {
                delay(1_000)
                AudioSalida.refrescar(); refrescar()
                val ahora = emparejados.value.firstOrNull { it.mac == mac }?.conectado ?: false
                if (ahora == on) { evento.value = if (on) "Conectado" else "Desconectado"; return@launch }
            }
            evento.value = if (on) "No se conectó: ¿está prendido, cerca y libre?" else "Sigue conectado"
        }
    }

    /** Al abrir el switch: reconecta tu preferido si no está conectado. */
    fun alPrender() {
        val mac = preferido.value
        if (!reconectarAlPrender.value || mac.isBlank()) return
        refrescar()
        if (emparejados.value.firstOrNull { it.mac == mac }?.conectado == true) return
        conectar(mac, true)
    }
}
