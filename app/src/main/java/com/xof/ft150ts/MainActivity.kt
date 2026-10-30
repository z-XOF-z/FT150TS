package com.xof.ft150ts

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.xof.ft150ts.ui.AppFT150
import com.xof.ft150ts.ui.TemaFT150

class MainActivity : ComponentActivity() {

    private val pedirPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        UbicacionManager.arrancar()
        BleManager.conectar()
        AudioSalida.conectarPerfiles()
        BluetoothAdmin.registrar()
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
        abrirPaginaPedida(intent)         // se abrió desde un atajo del ícono

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
            val reposo by Reposo.enReposo.collectAsStateWithLifecycle()
            val pantallaReposo by Reposo.pantalla.collectAsStateWithLifecycle()
            val pagina by Lanzador.paginaActual.collectAsStateWithLifecycle()
            // "En la moto": conectada por Bluetooth o montado con el switch prendido (carga)
            val enLaMoto = conexion == Conexion.CONECTADO || conexion == Conexion.DEMO || enMoto
            LaunchedEffect(modoPantalla, enLaMoto, dormida, reposo, pantallaReposo, pagina) {
                // En reposo con "apagar pantalla": se deja que Android la apague como siempre
                val apagada = dormida || (reposo && pantallaReposo == 1)
                // Fuera de la moto "Siempre" se suelta tras un rato sin tocarla: se vuelve a revisar al cumplirse
                while (true) {
                    val toque = Lanzador.ultimoToque.value
                    val sinTocar = SystemClock.elapsedRealtime() - toque
                    val prendida = PantallaEncendidaLogica.mantener(modoPantalla, enLaMoto, pagina, sinTocar, apagada)
                    if (prendida) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val espera = PantallaEncendidaLogica.revisarEn(modoPantalla, enLaMoto, pagina, sinTocar, apagada)
                    if (espera != null) { delay(espera + 500); continue }
                    // Se soltó por no tocarla: al siguiente toque vuelve a quedarse prendida
                    if (!prendida && modoPantalla == PantallaEncendida.SIEMPRE && !apagada && pagina != PAGINA_APPS) {
                        Lanzador.ultimoToque.first { it != toque }
                        continue
                    }
                    break
                }
            }
            // GPS: en el cajón de apps y fuera de la moto no hace falta (el clima usa la última ubicación conocida).
            // Como la app es el launcher, antes el GPS en alta precisión corría cada vez que veías tus apps.
            val rodada by Rodadas.enCurso.collectAsStateWithLifecycle()
            LaunchedEffect(pagina, enLaMoto, rodada) {
                if (gpsNecesario()) UbicacionManager.arrancar()
                else {
                    delay(5_000)                      // por si solo pasas por el cajón
                    if (!gpsNecesario()) UbicacionManager.pausar()
                }
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
            // En reposo también baja a 60 Hz: nada se mueve en pantalla, no hace falta más
            val fluidez = Apariencia.fluidez && !protegiendo && !reposo
            // (Probado en v3.9: pedir 60 Hz con CarPlay en la pestaña no sirve. La capa del video de DiPlay no pide Hz
            // y HyperOS entonces pone el máximo, aunque la app pida 60 y aunque se cambie el ajuste del sistema.)
            LaunchedEffect(fluidez) { Pantalla.aplicar(this@MainActivity, fluidez) }
            val brillo = brilloMax && !(protegiendo && nivelCalor == NivelCalor.MUY_CALIENTE)
            // Reloj de reposo: brillo mínimo (en AMOLED casi todo el panel queda apagado)
            val atenuar = reposo && pantallaReposo == 0
            LaunchedEffect(brillo, atenuar) {
                window.attributes = window.attributes.apply {
                    screenBrightness = when {
                        atenuar -> 0.04f
                        brillo -> 1f
                        else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }
            // Medidor de fluidez (Ajustes > Pantalla): tiempos reales de cada cuadro
            val medidor = Apariencia.medidorFluidez
            LaunchedEffect(medidor) { if (medidor) Fluidez.iniciar(this@MainActivity) else Fluidez.detener() }
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
        abrirPaginaPedida(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            val enMano = Energia.detectarCarga.value && Energia.alQuitarApps.value && !Energia.enMoto.value
            Lanzador.irA(if (enMano) PAGINA_APPS else PAGINA_TABLERO)
        }
    }

    /** Cualquier toque: estás usando el teléfono (despierta del modo sueño). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        Lanzador.ultimoToque.value = SystemClock.elapsedRealtime()
        ModoSueno.actividad()
        Reposo.actividad()
    }

    override fun onStart() {
        super.onStart()
        Lanzador.ultimoToque.value = SystemClock.elapsedRealtime()   // al regresar a la app cuenta como uso
        Lanzador.enPantalla.value = true
        Luz.arrancar()
        Dashcam.adjuntar(this)
        BleManager.primerPlano = true
        Lanzador.actividadVisible = true
        Root.permisosHyperOS(this)          // HyperOS los quita tras actualizar: se revisan al mostrarse
        BleManager.conectar()
        ServicioMoto.arrancar(this)    // vuelve a poner el servicio con permiso de ubicación (GPS con pantalla apagada)
    }

    override fun onResume() {
        super.onResume()
        quitarBloqueoSiSePuede()
        aplicarBarras(Apariencia.barras)   // al regresar de otra app Android las vuelve a mostrar
        NfcMoto.habilitar(this)
        MusicaManager.refrescar()
        if (gpsNecesario()) UbicacionManager.arrancar()
        AudioSalida.apagarMicBt()      // por si se quedó encendido el micrófono del casco
        AudioSalida.refrescar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) CarPlayPestana.alPerderFoco() else quitarBloqueoSiSePuede()
    }

    /**
     * Con el tablero encima de la pantalla de bloqueo, el bloqueo sigue debajo: CarPlay (DiPlay) no aparece y no se
     * puede deslizar. Si el bloqueo es solo "deslizar" (sin PIN, patrón ni huella) se le pide a Android que lo quite
     * (API oficial requestDismissKeyguard). Con PIN no se salta nada: queda como está y Android lo pide al abrir otra app.
     */
    private fun quitarBloqueoSiSePuede() {
        if (Build.VERSION.SDK_INT < 26) return
        val km = getSystemService(android.app.KeyguardManager::class.java) ?: return
        if (!km.isKeyguardLocked || km.isKeyguardSecure) return
        try { km.requestDismissKeyguard(this, null) } catch (_: Exception) { }
    }

    /** ¿Hace falta el GPS con la app a la vista? En el cajón de apps solo si estás en la moto, grabando una rodada,
     *  prestándole la ubicación a la tarjeta o con el teléfono haciendo de sensor. */
    private fun gpsNecesario(): Boolean =
        Lanzador.paginaActual.value != PAGINA_APPS || Energia.enMoto.value ||
            Moto.conexion.value == Conexion.CONECTADO || Moto.conexion.value == Conexion.DEMO ||
            Rodadas.enCurso.value != null || TelefonoMoto.trabajando.value

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
        Lanzador.actividadVisible = false
        // Sin pantalla el GPS solo sigue prendido si le está prestando ubicación a la moto
        // (o si el teléfono está haciendo de sensor de la moto: necesita la velocidad)
        if (!(BleManager.conectado() && Moto.enviarGps.value) && !TelefonoMoto.trabajando.value) UbicacionManager.pausar()
    }

    /**
     * Atajo del ícono: el extra "pagina" dice a qué sección ir. Cualquier app puede abrir esta pantalla, así que
     * solo se acepta un número de página válido y lo único que hace es cambiar de sección.
     */
    private fun abrirPaginaPedida(i: Intent?) {
        val p = i?.getStringExtra("pagina")?.toIntOrNull() ?: return
        if (p in PAGINA_APPS..4) Lanzador.irA(p)
    }

    override fun onDestroy() {
        Fluidez.detener()     // suelta la ventana (si no, el medidor la retendría)
        super.onDestroy()
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
        // Android 12+: pedir prender el Bluetooth necesita el permiso de conexión; si falta, primero se pide
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            revisarPermisos()
            return
        }
        try {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) { }
        }
    }
}
