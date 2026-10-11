package com.xof.ft150ts

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lee las notificaciones del teléfono (llamadas y mensajes) y las manda a la
 * pantalla de la moto. También es la "llave" que Android pide para controlar la música.
 */
class NotifListener : NotificationListenerService() {

    private var ultimoTexto = ""
    private var ultimaVez = 0L

    override fun onCreate() {
        super.onCreate()
        Moto.iniciar(applicationContext)
    }

    override fun onListenerConnected() {
        MusicaManager.refrescar()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val esLlamada = n.category == Notification.CATEGORY_CALL
        val esMensaje = n.category == Notification.CATEGORY_MESSAGE || sbn.packageName in APPS_MENSAJE
        if (!esLlamada && !esMensaje) return
        if (sbn.isOngoing && !esLlamada) return

        val titulo = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        if (titulo.isEmpty()) return
        val texto = if (esLlamada) "Llamada: $titulo" else "${nombreApp(this, sbn.packageName)}: $titulo"

        // Evita repetir la misma notificación (WhatsApp manda varias por mensaje)
        val ahora = System.currentTimeMillis()
        if (texto == ultimoTexto && ahora - ultimaVez < 15_000) return
        ultimoTexto = texto
        ultimaVez = ahora

        Moto.notificacionTelefono(texto)
    }

    companion object {
        val APPS_MENSAJE = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.facebook.orca",
            "com.instagram.android",
        )
    }
}
