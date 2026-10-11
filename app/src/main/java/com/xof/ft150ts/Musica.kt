package com.xof.ft150ts

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow

data class ElementoCola(val id: Long, val titulo: String, val subtitulo: String)

/** Una app de música (sonando ahora o instalada). */
data class Reproductor(val paquete: String, val nombre: String, val sonando: Boolean)

data class Musica(
    val permiso: Boolean = false,
    val hayReproductor: Boolean = false,
    val titulo: String = "",
    val artista: String = "",
    val app: String = "",
    val paquete: String = "",
    val reproduciendo: Boolean = false,
    val portada: Bitmap? = null,
    val posicion: Long = -1,          // ms; -1 = la app no la reporta
    val duracion: Long = 0,           // ms; 0 = desconocida
    val velocidad: Float = 1f,
    val marcaTiempo: Long = 0,        // elapsedRealtime en que se midió [posicion]
    val puedeBuscar: Boolean = false, // la app acepta "reproducir lo que busque"
    val puedeAdelantar: Boolean = false,
    val cola: List<ElementoCola> = emptyList(),
    val enCola: Long = -1,            // id de la canción que suena dentro de la cola
)

/** Posición estimada ahora mismo (avanza sola mientras suena). */
fun Musica.posicionAhora(ahora: Long): Long {
    if (posicion < 0) return -1
    val p = if (reproduciendo) posicion + ((ahora - marcaTiempo) * velocidad).toLong() else posicion
    return if (duracion > 0) p.coerceIn(0, duracion) else p.coerceAtLeast(0)
}

/**
 * Controla la música que suene en el teléfono (Spotify, YouTube Music, etc.).
 * Necesita el "acceso a notificaciones" que se da en los ajustes de Android.
 */
object MusicaManager {
    val musica = MutableStateFlow(Musica())
    /** Apps con sesión de música abierta (para elegir cuál controlar). */
    val reproductores = MutableStateFlow<List<Reproductor>>(emptyList())
    /** Volumen de música: actual y máximo. */
    val volumen = MutableStateFlow(0 to 15)

    private lateinit var ctx: Context
    private var msm: MediaSessionManager? = null
    private var controlador: MediaController? = null
    private var activas: List<MediaController> = emptyList()
    private var fijado: String? = null            // app que elegiste a mano
    private var registrado = false
    private val handler = Handler(Looper.getMainLooper())
    private val am: AudioManager by lazy { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Lo último que mandó la app de música (se guarda aquí para no pedirlo otra vez por Binder)
    private var md: MediaMetadata? = null
    private var estado: PlaybackState? = null
    private var cola: List<ElementoCola> = emptyList()
    private var portadaOriginal: Bitmap? = null
    private var portadaChica: Bitmap? = null

    private val componente: ComponentName by lazy { ComponentName(ctx, NotifListener::class.java) }

    private val cambioSesiones = MediaSessionManager.OnActiveSessionsChangedListener { lista ->
        handler.post { elegir(lista) }
    }

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { estado = state; publicar() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { md = metadata; publicar() }
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) { cola = mapearCola(queue); publicar() }
        override fun onSessionDestroyed() = refrescar()
    }

    fun iniciar(c: Context) {
        ctx = c.applicationContext
        msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        refrescar()
    }

    fun tienePermiso(): Boolean =
        ::ctx.isInitialized && NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun refrescar() {
        if (!::ctx.isInitialized) return
        leerVolumen()
        if (!tienePermiso()) {
            musica.value = Musica(permiso = false)
            return
        }
        try {
            val m = msm ?: return
            if (!registrado) {
                m.addOnActiveSessionsChangedListener(cambioSesiones, componente)
                registrado = true
            }
            elegir(m.getActiveSessions(componente))
        } catch (e: SecurityException) {
            musica.value = Musica(permiso = false)
        }
    }

    private fun elegir(lista: List<MediaController>?) {
        activas = lista ?: emptyList()
        reproductores.value = activas
            .map { Reproductor(it.packageName, nombreApp(ctx, it.packageName), it.playbackState?.state == PlaybackState.STATE_PLAYING) }
            .distinctBy { it.paquete }

        val sonando = activas.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val elegida = activas.firstOrNull { it.packageName == fijado }
        val nuevo = when {
            elegida != null && (sonando == null || elegida.playbackState?.state == PlaybackState.STATE_PLAYING) -> elegida
            sonando != null -> sonando
            else -> elegida ?: activas.firstOrNull()
        }
        if (nuevo?.sessionToken != controlador?.sessionToken) {
            controlador?.unregisterCallback(cb)
            controlador = nuevo
            md = nuevo?.metadata
            estado = nuevo?.playbackState
            cola = mapearCola(try { nuevo?.queue } catch (_: Exception) { null })
            nuevo?.registerCallback(cb, handler)
        }
        publicar()
    }

    private fun mapearCola(q: List<MediaSession.QueueItem>?): List<ElementoCola> =
        q?.take(50)?.map {
            ElementoCola(it.queueId, it.description.title?.toString() ?: "", it.description.subtitle?.toString() ?: "")
        } ?: emptyList()

    /** Las portadas llegan grandes (hasta 1000 px); se reducen una vez a 256 px. */
    private fun portada(b: Bitmap?): Bitmap? {
        if (b == null) return null
        if (b === portadaOriginal) return portadaChica
        val max = 256
        val chica = if (b.width > max || b.height > max) {
            val k = max.toFloat() / maxOf(b.width, b.height)
            try {
                Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
            } catch (_: Exception) { b }
        } else b
        portadaOriginal = b
        portadaChica = chica
        return chica
    }

    private fun publicar() {
        val c = controlador
        if (c == null) {
            musica.value = Musica(permiso = true)
            return
        }
        val m = md
        val st = estado
        val acciones = st?.actions ?: 0L
        val titulo = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: m?.description?.title?.toString() ?: ""
        val artista = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.description?.subtitle?.toString() ?: ""
        val dur = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        musica.value = Musica(
            permiso = true,
            hayReproductor = true,
            titulo = titulo,
            artista = artista,
            app = nombreApp(ctx, c.packageName),
            paquete = c.packageName,
            reproduciendo = st?.state == PlaybackState.STATE_PLAYING,
            portada = portada(m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)),
            posicion = st?.position ?: -1L,
            duracion = if (dur > 0) dur else 0L,
            velocidad = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            marcaTiempo = st?.lastPositionUpdateTime?.takeIf { it > 0L } ?: SystemClock.elapsedRealtime(),
            puedeBuscar = acciones and PlaybackState.ACTION_PLAY_FROM_SEARCH != 0L,
            puedeAdelantar = acciones and PlaybackState.ACTION_SEEK_TO != 0L,
            cola = cola,
            enCola = st?.activeQueueItemId ?: -1L,
        )
    }

    // ---------------- Controles ----------------

    fun playPausa() {
        val c = controlador ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun pausar() { controlador?.transportControls?.pause() }
    fun reproducir() { controlador?.transportControls?.play() }
    fun siguiente() { controlador?.transportControls?.skipToNext() }
    fun anterior() { controlador?.transportControls?.skipToPrevious() }
    fun adelantar(ms: Long) { controlador?.transportControls?.seekTo(ms.coerceAtLeast(0)) }
    fun irACancion(id: Long) { controlador?.transportControls?.skipToQueueItem(id) }

    /** Eliges qué app controlar cuando hay varias abiertas. */
    fun elegirReproductor(paquete: String) {
        fijado = paquete
        elegir(activas)
    }

    fun volumen(subir: Boolean) {
        try {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (subir) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0,
            )
        } catch (_: Exception) { }
        leerVolumen()
    }

    fun leerVolumen() {
        if (!::ctx.isInitialized) return
        try { volumen.value = am.getStreamVolume(AudioManager.STREAM_MUSIC) to am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } catch (_: Exception) { }
    }

    /**
     * Busca y reproduce una canción, artista o playlist.
     * Si la app que suena lo permite, se lo pide directo; si no, abre la app elegida con la búsqueda.
     */
    fun buscar(texto: String, paquete: String?): Boolean {
        val q = texto.trim()
        if (q.isEmpty() || !::ctx.isInitialized) return false
        val c = controlador
        if (c != null && (paquete == null || paquete == c.packageName) && musica.value.puedeBuscar) {
            c.transportControls.playFromSearch(q, Bundle())
            return true
        }
        // Sin app elegida: la que esté sonando (Apple Music, Spotify...)
        val app = paquete ?: c?.packageName
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, q)
            if (app != null) setPackage(app)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                intent.setPackage(null)
                ctx.startActivity(intent)
                true
            } catch (_: Exception) { false }
        } catch (_: Exception) { false }
    }

    /** Apps de música instaladas (las que Android anuncia como reproductores). */
    @Suppress("DEPRECATION")
    fun appsInstaladas(): List<Reproductor> {
        if (!::ctx.isInitialized) return emptyList()
        val pm = ctx.packageManager
        val servicios = try {
            pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0)
        } catch (_: Exception) { emptyList() }
        return servicios.mapNotNull { it.serviceInfo?.packageName }
            .distinct()
            .filter { it != ctx.packageName && pm.getLaunchIntentForPackage(it) != null }
            .map { Reproductor(it, nombreApp(ctx, it), false) }
            .sortedBy { it.nombre.lowercase() }
    }

    fun abrirApp(paquete: String): Boolean {
        if (!::ctx.isInitialized) return false
        val i = ctx.packageManager.getLaunchIntentForPackage(paquete) ?: return false
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fijado = paquete
            true
        } catch (_: Exception) { false }
    }
}

private val NOMBRES_CONOCIDOS = mapOf(
    "com.spotify.music" to "Spotify",
    "com.google.android.apps.youtube.music" to "YouTube Music",
    "com.google.android.youtube" to "YouTube",
    "com.amazon.mp3" to "Amazon Music",
    "deezer.android.app" to "Deezer",
    "com.apple.android.music" to "Apple Music",
    "com.soundcloud.android" to "SoundCloud",
    "com.whatsapp" to "WhatsApp",
    "com.whatsapp.w4b" to "WhatsApp",
    "org.telegram.messenger" to "Telegram",
    "com.google.android.apps.messaging" to "Mensajes",
    "com.samsung.android.messaging" to "Mensajes",
    "com.facebook.orca" to "Messenger",
    "com.instagram.android" to "Instagram",
)

fun nombreApp(ctx: Context, paquete: String): String {
    NOMBRES_CONOCIDOS[paquete]?.let { return it }
    return try {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(paquete, 0)).toString()
    } catch (e: Exception) {
        paquete.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
