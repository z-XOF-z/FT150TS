package com.xof.ft150ts

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Audífonos, bocina o casco con intercomunicador conectado por Bluetooth. */
data class DispositivoAudio(val nombre: String, val direccion: String, val bateria: Int)   // bateria -1 = no la reporta

/**
 * Audio por Bluetooth: qué audífonos/casco están conectados, su batería (si la reportan)
 * y por dónde sale el sonido. También enciende el micrófono del casco para la voz.
 */
@SuppressLint("MissingPermission")
object AudioSalida {
    val dispositivos = MutableStateFlow<List<DispositivoAudio>>(emptyList())
    /** Por dónde suena ahora: nombre del dispositivo o "Bocina del teléfono". */
    val salida = MutableStateFlow("Bocina del teléfono")

    private lateinit var ctx: Context
    private var adapter: BluetoothAdapter? = null
    private var a2dp: BluetoothProfile? = null
    private var hfp: BluetoothProfile? = null
    private var registrado = false
    private var micBtEncendido = false
    private val am: AudioManager by lazy { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private const val ACCION_BATERIA = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refrescar()
    }

    private val perfiles = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.A2DP) a2dp = proxy else if (profile == BluetoothProfile.HEADSET) hfp = proxy
            refrescar()
        }
        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) a2dp = null else if (profile == BluetoothProfile.HEADSET) hfp = null
            refrescar()
        }
    }

    fun iniciar(c: Context) {
        ctx = c.applicationContext
        adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        conectarPerfiles()
    }

    private fun permiso(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Se llama al iniciar y otra vez cuando el usuario da el permiso. */
    fun conectarPerfiles() {
        if (!::ctx.isInitialized || !permiso()) return
        val a = adapter ?: return
        try {
            if (a2dp == null) a.getProfileProxy(ctx, perfiles, BluetoothProfile.A2DP)
            if (hfp == null) a.getProfileProxy(ctx, perfiles, BluetoothProfile.HEADSET)
        } catch (_: Exception) { }
        if (!registrado) {
            val f = IntentFilter().apply {
                addAction(ACCION_BATERIA)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
            }
            try {
                ContextCompat.registerReceiver(ctx, receptor, f, ContextCompat.RECEIVER_EXPORTED)
                registrado = true
            } catch (_: Exception) { }
        }
        refrescar()
    }

    fun refrescar() {
        if (!::ctx.isInitialized) return
        val lista = if (permiso()) {
            try {
                ((a2dp?.connectedDevices ?: emptyList()) + (hfp?.connectedDevices ?: emptyList()))
                    .distinctBy { it.address }
                    .map { DispositivoAudio(nombreDe(it), it.address, bateriaDe(it)) }
            } catch (_: Exception) { emptyList() }
        } else emptyList()
        dispositivos.value = lista
        salida.value = calcularSalida(lista)
    }

    private fun nombreDe(d: BluetoothDevice): String = try {
        (if (Build.VERSION.SDK_INT >= 30) d.alias else null) ?: d.name ?: "Bluetooth"
    } catch (_: Exception) { "Bluetooth" }

    /**
     * Batería que reporta el audífono/casco (0-100). Android la recibe por HFP; no es
     * API pública, así que se lee por reflexión. -1 si el dispositivo no la manda.
     */
    private fun bateriaDe(d: BluetoothDevice): Int = try {
        (d.javaClass.getMethod("getBatteryLevel").invoke(d) as? Int)?.takeIf { it in 0..100 } ?: -1
    } catch (_: Exception) { -1 }

    private fun calcularSalida(bt: List<DispositivoAudio>): String {
        return try {
            val salidas = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val a2 = salidas.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            val cable = salidas.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            when {
                a2 != null -> bt.firstOrNull()?.nombre ?: a2.productName?.toString() ?: "Bluetooth"
                cable != null -> "Audífonos con cable"
                else -> "Bocina del teléfono"
            }
        } catch (_: Exception) { "Bocina del teléfono" }
    }

    /**
     * Abre el selector de salida de audio de Android (el mismo de la barra de notificaciones).
     * Si el teléfono no lo tiene, abre los ajustes de Bluetooth.
     */
    fun abrirSelectorSalida(paqueteMusica: String?): Boolean {
        if (!::ctx.isInitialized) return false
        // Android 12 o más: diálogo de SystemUI (es un broadcast)
        val s = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
            .setPackage("com.android.systemui")
            .putExtra("package_name", paqueteMusica ?: ctx.packageName)
        try {
            if (tieneReceptor(s)) { ctx.sendBroadcast(s); return true }
        } catch (_: Exception) { }
        // Android 11: panel de Ajustes
        val r = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
            .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", paqueteMusica ?: ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(r); return true } catch (_: Exception) { }
        return abrirAjustesBluetooth()
    }

    @Suppress("DEPRECATION")
    private fun tieneReceptor(i: Intent): Boolean = ctx.packageManager.queryBroadcastReceivers(i, 0).isNotEmpty()

    fun abrirAjustesBluetooth(): Boolean = try {
        ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) { false }

    // ---------------- Micrófono del casco (Bluetooth SCO) ----------------

    fun hayAudioBt(): Boolean = dispositivos.value.isNotEmpty()

    /** Manda el micrófono al casco/audífonos. true si se pudo pedir. */
    fun encenderMicBt(): Boolean {
        if (!::ctx.isInitialized || !hayAudioBt()) return false
        return try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                val sco = am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                if (sco == null) { am.mode = AudioManager.MODE_NORMAL; return false }
                am.setCommunicationDevice(sco)
            } else {
                @Suppress("DEPRECATION")
                am.startBluetoothSco()
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = true
            }
            micBtEncendido = true
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Regresa el micrófono y el audio a la normalidad. */
    fun apagarMicBt() {
        if (!micBtEncendido || !::ctx.isInitialized) return
        micBtEncendido = false
        try {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
            else {
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = false
                @Suppress("DEPRECATION")
                am.stopBluetoothSco()
            }
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) { }
    }
}
