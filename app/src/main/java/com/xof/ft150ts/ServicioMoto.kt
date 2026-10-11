package com.xof.ft150ts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Mantiene viva la app en segundo plano (con una notificación fija, como Maps o Spotify).
 * Así Android/MIUI no la cierra: sigue conectada a la moto, mandando el GPS,
 * reenviando notificaciones y vigilando la carga para el modo moto.
 */
class ServicioMoto : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var enPrimerPlano = false
    private var textoActual = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Moto.iniciar(this)          // si Android reinició la app desde aquí, arranca todo
        crearCanal()
        textoActual = texto(Moto.conexion.value, Energia.enMoto.value)
        enPrimerPlano = ponerEnPrimerPlano(textoActual)
        if (!enPrimerPlano) { stopSelf(); return }
        scope.launch {
            combine(Moto.conexion, Energia.enMoto, ModoSueno.dormido, ModoSueno.proximaRevision, ModoSueno.revisando) { c, m, d, prox, rev ->
                when {
                    d && rev -> "Modo sueño · revisando si se movió…"
                    d -> "Modo sueño · cargando quieto" + if (prox > 0) " · revisa a las ${ModoSueno.hora(prox)}" else ""
                    else -> texto(c, m)
                }
            }
                .distinctUntilChanged()
                .collect { t -> actualizar(t) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_DETENER) {
            Energia.ponerSegundoPlano(this, false)
            return START_NOT_STICKY
        }
        if (!enPrimerPlano) return START_NOT_STICKY
        // Con la app en pantalla Android sí deja agregar "ubicación": si el servicio arrancó solo desde segundo plano
        // (reinicio de Android) se quedó sin ella y el GPS se callaba con la pantalla apagada
        if (BleManager.primerPlano) ponerEnPrimerPlano(textoActual)
        BleManager.conectar()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun texto(c: Conexion, enMoto: Boolean): String = when {
        c == Conexion.CONECTADO && enMoto -> "En la moto · tablero listo"
        c == Conexion.CONECTADO -> "Moto conectada"
        c == Conexion.DEMO -> "Modo demo"
        enMoto -> "En la moto · buscando la tarjeta…"
        c == Conexion.BUSCANDO || c == Conexion.CONECTANDO -> "Buscando la moto…"
        c == Conexion.BT_APAGADO -> "Bluetooth apagado"
        else -> "Esperando a la moto"
    }

    private fun permiso(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    /** Tipos de servicio que Android deja usar según los permisos que ya diste. */
    private fun tipos(conUbicacion: Boolean): Int {
        if (Build.VERSION.SDK_INT < 29) return 0
        var t = 0
        val bt = if (Build.VERSION.SDK_INT >= 31) permiso(Manifest.permission.BLUETOOTH_CONNECT) else true
        if (bt) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (conUbicacion && permiso(Manifest.permission.ACCESS_FINE_LOCATION)) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return t
    }

    /** Con ubicación si se puede; si Android no deja (p. ej. arrancando desde segundo plano), solo Bluetooth. */
    private fun ponerEnPrimerPlano(t: String): Boolean {
        val n = notificacion(t)
        for (conUbicacion in listOf(true, false)) {
            val tipo = tipos(conUbicacion)
            if (Build.VERSION.SDK_INT >= 34 && tipo == 0) continue
            try {
                ServiceCompat.startForeground(this, ID, n, tipo)
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    private fun actualizar(t: String) {
        textoActual = t
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, notificacion(t))
        } catch (_: Exception) { }
    }

    private fun crearCanal() {
        val canal = NotificationChannel(CANAL, "App activa", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mantiene la conexión con la moto en segundo plano"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(canal)
    }

    private fun notificacion(t: String) = NotificationCompat.Builder(this, CANAL)
        .setSmallIcon(R.drawable.ic_notif)
        .setContentTitle("FT150TS")
        .setContentText(t)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .addAction(
            0, "Detener",
            PendingIntent.getService(
                this, 1,
                Intent(this, ServicioMoto::class.java).setAction(ACCION_DETENER),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()

    companion object {
        private const val CANAL = "moto"
        private const val ID = 150
        private const val ACCION_DETENER = "com.xof.ft150ts.DETENER"

        /** Arranca el servicio si la opción está prendida y ya hay permiso de Bluetooth. */
        fun arrancar(ctx: Context) {
            if (!Energia.segundoPlano.value) return
            if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ServicioMoto::class.java))
            } catch (_: Exception) { }
        }

        fun detener(ctx: Context) {
            try { ctx.stopService(Intent(ctx, ServicioMoto::class.java)) } catch (_: Exception) { }
        }
    }
}
