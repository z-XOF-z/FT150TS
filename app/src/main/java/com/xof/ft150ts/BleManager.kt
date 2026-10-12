package com.xof.ft150ts

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Conexión Bluetooth Low Energy con el ESP32.
 * Busca el servicio de la moto, se conecta solo y se reconecta si se pierde.
 */
@SuppressLint("MissingPermission")
object BleManager {
    private const val NOMBRE = "FT150TS"
    private val SERVICIO: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val NOTIF: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    private val TELEM: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    private val CMD: UUID = UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e")
    private val RESP: UUID = UUID.fromString("6e400005-b5a3-f393-e0a9-e50e24dcca9e")
    private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** true mientras la app está en pantalla (busca más rápido). */
    @Volatile var primerPlano = true

    /**
     * Seguridad: el servicio de la moto usa los UUID del "Nordic UART", que traen miles de aparatos BLE.
     * Por eso la app ya no se queda con el primero que aparezca:
     * 1) Verifica que tenga las 4 características de la tarjeta FT150TS (un aparato genérico solo trae 2);
     *    si no, se desconecta sin mandarle nada y lo ignora por el resto de la sesión.
     * 2) Se queda con la primera tarjeta verificada ("fijada"): de ahí en adelante solo se conecta a esa
     *    dirección. "Olvidar tarjeta" (Ajustes > Conexión) la suelta para vincular otra.
     * 3) Pide vincularse (enlace cifrado) con la app en pantalla; el firmware 1.4 exige ese enlace para los
     *    comandos delicados (corte de motor, calibraciones, borrar datos).
     */
    val tarjetaFijada = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val ignorados = HashSet<String>()
    private var prefs: android.content.SharedPreferences? = null

    private lateinit var ctx: Context
    private var adapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private var chNotif: BluetoothGattCharacteristic? = null
    private var chCmd: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())
    private val cola = ArrayDeque<() -> Boolean>()
    private var ocupado = false
    private var quiereConectar = false
    private var escaneando = false
    private val respuesta = StringBuilder()

    fun iniciar(c: Context) {
        ctx = c.applicationContext
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = bm.adapter
        val p = ctx.getSharedPreferences("ble", Context.MODE_PRIVATE)
        prefs = p
        tarjetaFijada.value = p.getString("tarjeta", null)?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) }
    }

    /** Suelta la tarjeta fijada: la próxima tarjeta FT150TS verificada que aparezca se fija. */
    fun olvidarTarjeta() {
        tarjetaFijada.value = null
        prefs?.edit()?.remove("tarjeta")?.apply()
        ignorados.clear()
        desconectar()
        conectar()
    }

    private fun permiso(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun tienePermisos(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) permiso(Manifest.permission.BLUETOOTH_SCAN) && permiso(Manifest.permission.BLUETOOTH_CONNECT)
        else permiso(Manifest.permission.ACCESS_FINE_LOCATION)

    // ---------------- Conexión ----------------

    fun conectar() {
        if (!::ctx.isInitialized || Moto.demo.value) return
        quiereConectar = true
        if (!tienePermisos()) { Moto.conexion.value = Conexion.SIN_PERMISO; return }
        val a = adapter
        if (a == null || !a.isEnabled) {
            Moto.conexion.value = Conexion.BT_APAGADO
            reintentar()
            return
        }
        if (gatt != null || escaneando) return
        escanear()
    }

    /** Busca la moto en este momento (p. ej. al conectar el cable de la moto), sin esperar el reintento. */
    fun buscarYa() {
        if (!::ctx.isInitialized) return
        handler.removeCallbacks(reintento)
        conectar()
    }

    /** Busca rápido con la app en pantalla o con el teléfono cargando (probablemente en la moto). */
    private fun rapido() = (primerPlano || Energia.cargando.value) && !ModoSueno.dormido.value

    fun desconectar() {
        quiereConectar = false
        handler.removeCallbacks(reintento)
        detenerEscaneo()
        gatt?.let { it.disconnect(); it.close() }
        limpiar()
        Moto.conexion.value = if (Moto.demo.value) Conexion.DEMO else Conexion.APAGADO
    }

    private fun limpiar() {
        gatt = null
        chNotif = null
        chCmd = null
        cola.clear()
        ocupado = false
        respuesta.clear()
        Moto.limpiarPendientes()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!escaneando) return
            val dir = result.device.address
            // Un aparato que ya resultó no ser la tarjeta, o una tarjeta distinta a la fijada: se sigue buscando
            if (dir in ignorados) return
            val fijada = tarjetaFijada.value
            if (fijada != null && !fijada.equals(dir, ignoreCase = true)) return
            detenerEscaneo()
            conectarA(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            escaneando = false
            reintentar()
        }
    }

    private fun escanear() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) { reintentar(); return }
        Moto.conexion.value = Conexion.BUSCANDO
        // Con una tarjeta fijada solo se busca esa dirección (el radio filtra solo: gasta menos)
        val fijada = tarjetaFijada.value
        val filtros = if (fijada != null) listOf(ScanFilter.Builder().setDeviceAddress(fijada).build())
        else listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICIO)).build(),
            ScanFilter.Builder().setDeviceName(NOMBRE).build(),
        )
        val modo = if (rapido()) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_LOW_POWER
        val ajustes = ScanSettings.Builder().setScanMode(modo).build()
        try {
            scanner.startScan(filtros, ajustes, scanCallback)
            escaneando = true
        } catch (e: Exception) {
            escaneando = false
            reintentar()
            return
        }
        handler.postDelayed(finEscaneo, 15_000)
    }

    private val finEscaneo = Runnable {
        if (escaneando) {
            detenerEscaneo()
            reintentar()
        }
    }

    private fun detenerEscaneo() {
        handler.removeCallbacks(finEscaneo)
        if (!escaneando) return
        escaneando = false
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) { }
    }

    private fun conectarA(d: BluetoothDevice) {
        Moto.conexion.value = Conexion.CONECTANDO
        gatt = d.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private val reintento = Runnable {
        if (quiereConectar && gatt == null && !Moto.demo.value) conectar()
    }

    private fun reintentar() {
        if (!quiereConectar) return
        handler.removeCallbacks(reintento)
        // En segundo plano espera más para no gastar batería (y dormida, mucho más)
        handler.postDelayed(reintento, when {
            ModoSueno.dormido.value -> 120_000L
            rapido() -> 3_000L
            else -> 30_000L
        })
    }

    // ---------------- Callbacks GATT ----------------

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    if (!g.requestMtu(247)) g.discoverServices()
                } else {
                    g.close()
                    if (gatt == g || gatt == null) limpiar()
                    if (!Moto.demo.value && quiereConectar) Moto.conexion.value = Conexion.BUSCANDO
                    reintentar()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            handler.post { g.discoverServices() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            handler.post {
                val s = g.getService(SERVICIO)
                val notif = s?.getCharacteristic(NOTIF)
                val cmd = s?.getCharacteristic(CMD)
                val tel = s?.getCharacteristic(TELEM)
                val resp = s?.getCharacteristic(RESP)
                if (notif == null || cmd == null || tel == null || resp == null) {
                    // No es la tarjeta FT150TS (p. ej. otro aparato con Nordic UART): no se le manda nada
                    ignorados += g.device.address
                    g.disconnect()
                    return@post
                }
                chNotif = notif
                chCmd = cmd
                encolar { activarNotificaciones(g, tel) }
                encolar { activarNotificaciones(g, resp) }
                // Primera tarjeta verificada: se fija
                if (tarjetaFijada.value == null) {
                    tarjetaFijada.value = g.device.address
                    prefs?.edit()?.putString("tarjeta", g.device.address)?.apply()
                }
                // Vincular (enlace cifrado), solo con la app a la vista: Android muestra su ventana de confirmación
                if (primerPlano && g.device.bondState == BluetoothDevice.BOND_NONE) {
                    try { g.device.createBond() } catch (_: Exception) { }
                }
                Moto.conexion.value = Conexion.CONECTADO
                // Al conectar: pone la hora del teléfono en el reloj de la moto y pide el mantenimiento
                val hora = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                Moto.comandoSilencioso("hora $hora")
                Moto.pedirMantenimiento()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            handler.post { siguiente() }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            handler.post { siguiente() }
        }

        // Android 13 o más nuevo
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            recibir(c.uuid, value)
        }

        // Android 12 o más viejo
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION")
                val v = c.value ?: return
                recibir(c.uuid, v.copyOf())
            }
        }
    }

    private fun recibir(uuid: UUID, v: ByteArray) {
        handler.post {
            when (uuid) {
                TELEM -> Moto.recibirTelemetria(String(v, Charsets.UTF_8))
                RESP -> {
                    if (v.size == 1 && v[0] == 4.toByte()) {          // fin de respuesta
                        Moto.recibirRespuesta(respuesta.toString())
                        respuesta.clear()
                    } else {
                        respuesta.append(String(v, Charsets.UTF_8))
                    }
                }
            }
        }
    }

    // ---------------- Escrituras (una a la vez) ----------------

    private fun encolar(op: () -> Boolean) {
        cola.addLast(op)
        if (!ocupado) siguiente()
    }

    private val vigilante = Runnable { ocupado = false; siguiente() }   // por si no llega el callback

    private fun siguiente() {
        handler.removeCallbacks(vigilante)
        while (cola.isNotEmpty()) {
            val op = cola.removeFirst()
            if (op()) {
                ocupado = true
                handler.postDelayed(vigilante, 1500)
                return
            }
        }
        ocupado = false
    }

    @Suppress("DEPRECATION")
    private fun activarNotificaciones(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(CCCD) ?: return false
        val valor = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(d, valor) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = valor
            g.writeDescriptor(d)
        }
    }

    @Suppress("DEPRECATION")
    private fun escribir(c: BluetoothGattCharacteristic, datos: ByteArray): Boolean {
        val g = gatt ?: return false
        val tipo = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, datos, tipo) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = tipo
            c.value = datos
            g.writeCharacteristic(c)
        }
    }

    fun conectado() = Moto.conexion.value == Conexion.CONECTADO && gatt != null

    /** Manda un comando de consola. Los "opcionales" (GPS) se descartan si hay fila. */
    fun enviarComando(texto: String, opcional: Boolean = false): Boolean {
        if (!conectado() || chCmd == null) return false
        if (opcional && cola.size > 3) return false
        val datos = texto.take(96).toByteArray(Charsets.UTF_8)
        encolar { chCmd?.let { escribir(it, datos) } ?: false }
        return true
    }

    /** Texto que aparece en la fila de alertas del LCD (máx. 20 caracteres). */
    fun enviarNotif(texto: String): Boolean {
        if (!conectado() || chNotif == null) return false
        val datos = texto.take(20).toByteArray(Charsets.UTF_8)
        encolar { chNotif?.let { escribir(it, datos) } ?: false }
        return true
    }
}
