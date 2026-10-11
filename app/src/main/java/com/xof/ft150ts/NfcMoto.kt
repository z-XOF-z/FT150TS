package com.xof.ft150ts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Etiqueta NFC en el soporte: al poner el teléfono, la app sabe que está en la moto (aunque no cargue)
 * y al quitarlo lo nota (mientras la app está en pantalla revisa cada segundo si la etiqueta sigue ahí).
 * Funciona con cualquier sticker NFC barato (NTAG213/215/216): se "registra" una vez y la app le graba
 * un enlace propio para que también se abra sola si estaba en segundo plano.
 */
object NfcMoto {
    const val URI = "ft150ts://moto"

    val disponible = MutableStateFlow(false)     // el teléfono tiene NFC
    val encendido = MutableStateFlow(false)      // NFC prendido en los ajustes de Android
    val activo = MutableStateFlow(true)          // usar la etiqueta
    val etiqueta = MutableStateFlow("")          // id de la etiqueta registrada
    val presente = MutableStateFlow(false)       // la etiqueta está pegada al teléfono
    val registrando = MutableStateFlow(false)
    val mensaje = MutableStateFlow("")

    private var ctx: Context? = null
    @Volatile private var revisando: Thread? = null
    @Volatile private var ultimaVista = 0L
    private val principal = android.os.Handler(android.os.Looper.getMainLooper())

    fun iniciar(c: Context) {
        if (ctx != null) return
        val app = c.applicationContext
        ctx = app
        val p = app.getSharedPreferences("nfc", Context.MODE_PRIVATE)
        activo.value = p.getBoolean("activo", true)
        etiqueta.value = p.getString("etiqueta", "") ?: ""
        refrescar()
    }

    fun refrescar() {
        val a = ctx?.let { NfcAdapter.getDefaultAdapter(it) }
        disponible.value = a != null
        encendido.value = a?.isEnabled == true
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on
        ctx?.getSharedPreferences("nfc", Context.MODE_PRIVATE)?.edit()?.putBoolean("activo", on)?.apply()
        if (!on) presente.value = false
    }

    fun olvidar() {
        etiqueta.value = ""
        presente.value = false
        ctx?.getSharedPreferences("nfc", Context.MODE_PRIVATE)?.edit()?.remove("etiqueta")?.apply()
        mensaje.value = "Etiqueta olvidada"
    }

    /** Siguiente etiqueta que acerques se registra. */
    fun empezarRegistro() {
        registrando.value = true
        mensaje.value = "Acerca la etiqueta a la parte de atrás del teléfono (cerca de la cámara)"
    }

    fun cancelarRegistro() { registrando.value = false; mensaje.value = "" }

    /**
     * Lector NFC mientras la app está en pantalla. Ojo: mientras está prendido, pagar con el teléfono
     * no funciona con la app abierta (Android le da el NFC a la app que está al frente).
     */
    fun habilitar(a: Activity) {
        refrescar()
        val ad = NfcAdapter.getDefaultAdapter(a) ?: return
        if (!ad.isEnabled) return
        if (!registrando.value && !(activo.value && etiqueta.value.isNotBlank())) return
        val banderas = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000) }
        try { ad.enableReaderMode(a, { tag -> etiquetaVista(tag) }, banderas, extras) } catch (_: Exception) { }
        // Si estaba en el soporte y al volver ya no aparece la etiqueta en 4 s, ya lo quitaste
        if (presente.value) {
            val desde = System.currentTimeMillis()
            principal.postDelayed({ if (ultimaVista < desde) presente.value = false }, 4_000)
        }
    }

    /** Al salir de la app se deja de revisar (sin dar por quitado el teléfono: no se puede saber). */
    fun deshabilitar(a: Activity) {
        revisando?.interrupt()
        revisando = null
        principal.removeCallbacksAndMessages(null)
        try { NfcAdapter.getDefaultAdapter(a)?.disableReaderMode(a) } catch (_: Exception) { }
    }

    /** La app se abrió (o volvió) por la etiqueta, estando en segundo plano. */
    fun desdeIntent(i: Intent?) {
        if (i?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        if (i.data?.toString() != URI) return
        val tag: Tag? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null) etiquetaVista(tag) else if (activo.value) presente.value = true
    }

    private fun idTexto(t: Tag) = t.id.joinToString("") { "%02x".format(it) }

    /** Hilo del lector NFC (no es el principal). */
    private fun etiquetaVista(tag: Tag) {
        val id = idTexto(tag)
        if (registrando.value) {
            val ok = grabar(tag)
            registrando.value = false
            etiqueta.value = id
            ctx?.getSharedPreferences("nfc", Context.MODE_PRIVATE)?.edit()?.putString("etiqueta", id)?.apply()
            mensaje.value = if (ok) "Etiqueta lista. Pégala en el soporte, donde quede la parte de atrás del teléfono."
            else "Etiqueta registrada (no se pudo grabar el enlace: solo funcionará con la app abierta)."
            return
        }
        if (!activo.value || id != etiqueta.value) return
        ultimaVista = System.currentTimeMillis()
        presente.value = true
        vigilar(tag)
    }

    /** Graba el enlace ft150ts://moto en la etiqueta (para que Android abra la app al acercarla). */
    private fun grabar(tag: Tag): Boolean {
        val msg = NdefMessage(arrayOf(NdefRecord.createUri(URI), NdefRecord.createApplicationRecord("com.xof.ft150ts")))
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (ndef.isWritable && ndef.maxSize >= msg.toByteArray().size) ndef.writeNdefMessage(msg)
                val ok = ndef.isWritable
                ndef.close()
                ok
            } else {
                val f = NdefFormatable.get(tag) ?: return false
                f.connect(); f.format(msg); f.close()
                true
            }
        } catch (_: Exception) { false }
    }

    /** Cada segundo pregunta si la etiqueta sigue ahí; 2 fallas seguidas = quitaste el teléfono del soporte. */
    private fun vigilar(tag: Tag) {
        revisando?.interrupt()
        val t = Thread {
            val ndef = Ndef.get(tag)
            var fallas = 0
            try {
                while (!Thread.currentThread().isInterrupted && presente.value) {
                    Thread.sleep(1000)
                    val sigue = try {
                        if (ndef == null) false else {
                            if (!ndef.isConnected) ndef.connect()
                            ndef.ndefMessage
                            true
                        }
                    } catch (_: Exception) { false }
                    fallas = if (sigue) 0 else fallas + 1
                    if (sigue) ultimaVista = System.currentTimeMillis()
                    if (fallas >= 2 && !Thread.currentThread().isInterrupted) { presente.value = false; break }
                }
            } catch (_: InterruptedException) {
            } finally {
                try { ndef?.close() } catch (_: Exception) { }
            }
        }
        revisando = t
        t.start()
    }
}
