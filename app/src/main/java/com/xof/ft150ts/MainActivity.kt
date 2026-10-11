package com.xof.ft150ts

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.ui.AppFT150
import com.xof.ft150ts.ui.TemaFT150

class MainActivity : ComponentActivity() {

    private val pedirPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        UbicacionManager.arrancar()
        BleManager.conectar()
        AudioSalida.conectarPerfiles()
        ServicioMoto.arrancar(this)       // ya con permiso de Bluetooth/ubicación
    }

    // Permiso de la cámara para la dashcam (lo pide Ajustes)
    private val pedirCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Dashcam.pedirPermiso.value = false
        if (!ok) Dashcam.ponerActivo(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // luego se ajusta según Ajustes > Pantalla

        Moto.iniciar(this)
        revisarPermisos()
        ServicioMoto.arrancar(this)       // mantener viva en segundo plano (si está prendido en Ajustes)
        NfcMoto.desdeIntent(intent)       // se abrió acercando la etiqueta NFC del soporte

        setContent {
            // Ajustes > Pantalla: girar a lo ancho y brillo al máximo
            val horizontal = Apariencia.siempreHorizontal
            val brilloMax = Apariencia.brilloMax
            LaunchedEffect(horizontal) {
                requestedOrientation = if (horizontal) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            // Pantalla encendida: siempre, solo con la moto conectada, o normal
            val modoPantalla = Apariencia.pantalla
            val conexion by Moto.conexion.collectAsStateWithLifecycle()
            val enMoto by Energia.enMoto.collectAsStateWithLifecycle()
            val dormida by ModoSueno.dormido.collectAsStateWithLifecycle()
            LaunchedEffect(modoPantalla, conexion, enMoto, dormida) {
                val prendida = !dormida && when (modoPantalla) {
                    PantallaEncendida.SIEMPRE -> true
                    // "En la moto": conectada por Bluetooth o montado con el switch prendido (carga)
                    PantallaEncendida.CON_MOTO -> conexion == Conexion.CONECTADO || conexion == Conexion.DEMO || enMoto
                    PantallaEncendida.NORMAL -> false
                }
                if (prendida) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // Tablero encima de la pantalla de bloqueo (como la navegación de Maps)
            val sobreBloqueo by Root.sobreBloqueo.collectAsStateWithLifecycle()
            LaunchedEffect(sobreBloqueo) {
                if (Build.VERSION.SDK_INT >= 27) {
                    setShowWhenLocked(sobreBloqueo)
                    setTurnScreenOn(sobreBloqueo)
                } else {
                    @Suppress("DEPRECATION")
                    val banderas = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    if (sobreBloqueo) window.addFlags(banderas) else window.clearFlags(banderas)
                }
            }
            // Pantalla completa: oculta los botones de Android (y la barra de estado) dentro de la app.
            // Deslizando desde la orilla salen un momento.
            val barras = Apariencia.barras
            LaunchedEffect(barras) { aplicarBarras(barras) }
            // Fluidez: 90/120 Hz si la pantalla los tiene. Con el teléfono caliente baja a 60 Hz y suelta el brillo máximo.
            val protegiendo by Termico.protegiendo.collectAsStateWithLifecycle()
            val nivelCalor by Termico.nivel.collectAsStateWithLifecycle()
            val fluidez = Apariencia.fluidez && !protegiendo
            LaunchedEffect(fluidez) { Pantalla.aplicar(this@MainActivity, fluidez) }
            val brillo = brilloMax && !(protegiendo && nivelCalor == NivelCalor.MUY_CALIENTE)
            LaunchedEffect(brillo) {
                window.attributes = window.attributes.apply {
                    screenBrightness = if (brillo) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
            // La dashcam pide el permiso de la cámara
            val pideCamara by Dashcam.pedirPermiso.collectAsStateWithLifecycle()
            LaunchedEffect(pideCamara) { if (pideCamara) pedirCamara.launch(Manifest.permission.CAMERA) }
            TemaFT150 {
                AppFT150(
                    onAccesoNotif = { abrirAccesoNotificaciones() },
                    onActivarBt = { activarBluetooth() },
                    onPermisos = { revisarPermisos() },
                )
            }
        }
    }

    // Con el modo launcher, el botón Inicio del teléfono: en la moto al tablero; en la mano (modo moto) a tus apps
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        NfcMoto.desdeIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            val enMano = Energia.detectarCarga.value && Energia.alQuitarApps.value && !Energia.enMoto.value
            Lanzador.irA(if (enMano) PAGINA_APPS else PAGINA_TABLERO)
        }
    }

    /** Cualquier toque: estás usando el teléfono (despierta del modo sueño). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        ModoSueno.actividad()
    }

    override fun onStart() {
        super.onStart()
        Lanzador.enPantalla.value = true
        Luz.arrancar()
        Dashcam.adjuntar(this)
        BleManager.primerPlano = true
        BleManager.conectar()
        ServicioMoto.arrancar(this)    // vuelve a poner el servicio con permiso de ubicación (GPS con pantalla apagada)
    }

    override fun onResume() {
        super.onResume()
        aplicarBarras(Apariencia.barras)   // al regresar de otra app Android las vuelve a mostrar
        NfcMoto.habilitar(this)
        MusicaManager.refrescar()
        UbicacionManager.arrancar()
        AudioSalida.apagarMicBt()      // por si se quedó encendido el micrófono del casco
        AudioSalida.refrescar()
    }

    override fun onPause() {
        super.onPause()
        NfcMoto.deshabilitar(this)
    }

    override fun onStop() {
        super.onStop()
        Lanzador.enPantalla.value = false
        Luz.detener()
        Dashcam.soltar()
        BleManager.primerPlano = false
        // Sin pantalla el GPS solo sigue prendido si le está prestando ubicación a la moto
        // (o si el teléfono está haciendo de sensor de la moto: necesita la velocidad)
        if (!(BleManager.conectado() && Moto.enviarGps.value) && !TelefonoMoto.trabajando.value) UbicacionManager.pausar()
    }

    private fun aplicarBarras(b: BarrasSistema) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        when (b) {
            BarrasSistema.NORMAL -> c.show(WindowInsetsCompat.Type.systemBars())
            BarrasSistema.SIN_BOTONES -> {
                c.show(WindowInsetsCompat.Type.statusBars())
                c.hide(WindowInsetsCompat.Type.navigationBars())
            }
            BarrasSistema.COMPLETA -> c.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun revisarPermisos() {
        val lista = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 31) {
            lista += Manifest.permission.BLUETOOTH_SCAN
            lista += Manifest.permission.BLUETOOTH_CONNECT
        }
        // Para que se vea la notificación de "app activa" (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) lista += Manifest.permission.POST_NOTIFICATIONS
        val faltan = lista.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltan.isNotEmpty()) pedirPermisos.launch(faltan.toTypedArray())
    }

    private fun abrirAccesoNotificaciones() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun activarBluetooth() {
        try {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }
}
