package com.xof.ft150ts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Voz del teléfono (texto a voz de Android). Dice el clima y los avisos.
 * Baja un momento el volumen de la música mientras habla, como el GPS.
 */
object Voz {
    private lateinit var ctx: Context
    private var tts: TextToSpeech? = null
    private var lista = false
    private var pendiente: String? = null

    private val atributos: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val am: AudioManager by lazy { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val foco: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(atributos).build()
    }

    fun iniciar(c: Context) { ctx = c.applicationContext }

    fun decir(texto: String) {
        if (!::ctx.isInitialized || texto.isBlank()) return
        val t = tts
        if (t != null && lista) {
            hablar(t, texto)
            return
        }
        pendiente = texto
        if (t == null) {
            tts = TextToSpeech(ctx) { status -> listo(status) }
        }
    }

    private fun listo(status: Int) {
        val motor = tts
        if (status != TextToSpeech.SUCCESS || motor == null) {
            tts = null
            pendiente = null
            return
        }
        val r = motor.setLanguage(Locale("es", "MX"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) motor.setLanguage(Locale("es"))
        motor.setAudioAttributes(atributos)
        motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { soltarFoco() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { soltarFoco() }
        })
        lista = true
        pendiente?.let { hablar(motor, it) }
        pendiente = null
    }

    private fun hablar(motor: TextToSpeech, texto: String) {
        try { am.requestAudioFocus(foco) } catch (_: Exception) { }
        motor.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "ft150ts")
    }

    private fun soltarFoco() {
        try { am.abandonAudioFocusRequest(foco) } catch (_: Exception) { }
    }
}
