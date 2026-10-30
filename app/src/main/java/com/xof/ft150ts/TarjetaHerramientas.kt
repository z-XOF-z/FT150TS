package com.xof.ft150ts

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Partes puras de la actualización del firmware (se prueban en LogicaTarjetaTest). */
object OtaLogica {
    const val MIN_BYTES = 100_000
    /** Tamaño de cada copia del firmware en el esquema "16M Flash (3MB APP/9.9MB FATFS)". */
    const val MAX_BYTES = 3 * 1024 * 1024
    /** Primer byte de toda imagen de firmware de ESP32. */
    const val MAGICO: Byte = 0xE9.toByte()

    /** null si el archivo parece un firmware de la tarjeta; si no, por qué no. */
    fun revisarImagen(bin: ByteArray): String? = when {
        bin.size < MIN_BYTES -> "Ese archivo es muy chico para ser el firmware (.bin de Arduino: Sketch > Exportar binario)."
        bin.size > MAX_BYTES -> "Ese archivo no cabe en la tarjeta (máximo 3 MB)."
        bin[0] != MAGICO -> "Ese archivo no es un firmware de ESP32 (usa el .bin, no el .ino ni el .elf)."
        else -> null
    }

    fun md5(bin: ByteArray): String = MessageDigest.getInstance("MD5").digest(bin).joinToString("") { "%02x".format(it) }

    /** Pedazo para la tarjeta: [desde: 4 bytes little-endian][datos]. */
    fun paquete(bin: ByteArray, desde: Int, n: Int): ByteArray {
        val p = ByteArray(4 + n)
        p[0] = desde.toByte(); p[1] = (desde shr 8).toByte(); p[2] = (desde shr 16).toByte(); p[3] = (desde shr 24).toByte()
        System.arraycopy(bin, desde, p, 4, n)
        return p
    }
}

/**
 * Actualizar el firmware de la tarjeta desde la app, por Bluetooth (firmware v1.6+): eliges el .bin, la app lo revisa,
 * le manda a la tarjeta el tamaño y el MD5, y luego el archivo en pedazos (cada uno confirmado). La tarjeta comprueba el
 * MD5 antes de cambiarse: si algo sale mal, sigue con su firmware de antes. Solo con el teléfono vinculado y la moto
 * parada con el motor apagado (lo revisa la tarjeta).
 */
object OtaTarjeta {
    val activa = MutableStateFlow(false)
    val progreso = MutableStateFlow(0f)
    val mensaje = MutableStateFlow("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    @Volatile private var respuesta: CompletableDeferred<String>? = null

    fun empezar(ctx: Context, uri: Uri) {
        if (activa.value) return
        val app = ctx.applicationContext
        scope.launch {
            activa.value = true
            progreso.value = 0f
            BleManager.ajustarPrioridad()          // conexión rápida mientras se envía
            try {
                correr(app, uri)
            } catch (e: Exception) {
                mensaje.value = "Error: ${e.message}"
            } finally {
                activa.value = false
                BleManager.alEscribirOta = null
                BleManager.ajustarPrioridad()
            }
        }
    }

    /** Respuestas de la tarjeta a "ota ..." (las manda Moto). true = era para la actualización. */
    fun recibir(t: String): Boolean {
        val esperando = respuesta
        if (t.startsWith("OTA") || t.startsWith("{\"ota\"") || (esperando != null && t.startsWith("Bloqueado"))) {
            esperando?.complete(t)
            return esperando != null || t.startsWith("OTA")
        }
        return false
    }

    private suspend fun correr(ctx: Context, uri: Uri) {
        mensaje.value = "Leyendo el archivo…"
        val bin = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
        if (bin == null) { mensaje.value = "No pude leer el archivo."; return }
        OtaLogica.revisarImagen(bin)?.let { mensaje.value = it; return }
        if (!BleManager.puedeOta()) {
            mensaje.value = "La tarjeta no acepta actualizaciones: conéctala (y vincúlala) con el firmware v1.6 o más nuevo."
            return
        }
        val md5 = withContext(Dispatchers.Default) { OtaLogica.md5(bin) }
        mensaje.value = "Preparando la tarjeta…"
        val r1 = pedir("ota inicio ${bin.size} $md5", 15_000)
        if (r1 == null || !r1.startsWith("OTA listo")) { mensaje.value = r1 ?: "La tarjeta no contestó."; return }

        val pedazo = (BleManager.cargaMaxima - 4).coerceAtLeast(16)
        val t0 = SystemClock.elapsedRealtime()
        var desde = 0
        while (desde < bin.size) {
            val n = minOf(pedazo, bin.size - desde)
            if (!escribir(OtaLogica.paquete(bin, desde, n))) {
                Moto.comandoSilencioso("ota cancelar")
                mensaje.value = "Se cortó la conexión a la mitad. La tarjeta sigue con su firmware de antes: inténtalo otra vez."
                return
            }
            desde += n
            progreso.value = desde.toFloat() / bin.size
            val seg = (SystemClock.elapsedRealtime() - t0) / 1000f
            val falta = if (desde > 0 && seg > 1f) ((bin.size - desde) / (desde / seg)).toInt() else 0
            mensaje.value = "Enviando ${desde / 1024} de ${bin.size / 1024} KB" + if (falta > 0) " · faltan ~$falta s" else ""
        }
        mensaje.value = "Revisando el firmware en la tarjeta…"
        val r2 = pedir("ota fin", 30_000)
        mensaje.value = when {
            r2 == null -> "La tarjeta no contestó al final. Si se reinició, ya tiene el firmware nuevo (revisa la versión)."
            r2.startsWith("OTA ok") -> "Listo: la tarjeta se reinicia con el firmware nuevo y la app se vuelve a conectar sola."
            else -> r2
        }
    }

    /** Manda un pedazo y espera a que la tarjeta lo grabe (o 5 s). */
    private suspend fun escribir(p: ByteArray): Boolean {
        val d = CompletableDeferred<Boolean>()
        BleManager.alEscribirOta = { ok -> d.complete(ok) }
        if (!BleManager.escribirOta(p)) return false
        return withTimeoutOrNull(5_000) { d.await() } ?: false
    }

    private suspend fun pedir(cmd: String, ms: Long): String? {
        val d = CompletableDeferred<String>()
        respuesta = d
        Moto.comandoSilencioso(cmd)
        return try { withTimeoutOrNull(ms) { d.await() } } finally { respuesta = null }
    }
}

/**
 * Baja los registros CSV de la SD de la tarjeta (viajes, eventos, batería y pista; firmware v1.6+) y los guarda en
 * Descargas/FT150TS del teléfono, para abrirlos en Excel o Sheets sin sacar la tarjeta ni usar la página WiFi.
 */
object RegistrosTarjeta {
    val trabajando = MutableStateFlow(false)
    val mensaje = MutableStateFlow("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    @Volatile private var respuesta: CompletableDeferred<String>? = null

    /** ¿Esperando una respuesta? Entonces la siguiente respuesta de texto de la tarjeta es para esto. */
    fun esperando() = respuesta != null

    fun recibir(t: String) { respuesta?.complete(t) }

    fun bajar(ctx: Context) {
        if (trabajando.value) return
        val app = ctx.applicationContext
        scope.launch {
            trabajando.value = true
            try { correr(app) } catch (e: Exception) { mensaje.value = "Error: ${e.message}" } finally { trabajando.value = false }
        }
    }

    private suspend fun correr(ctx: Context) {
        mensaje.value = "Pidiendo la lista…"
        val r = pedir("sd lista") ?: run { mensaje.value = "La tarjeta no contestó (necesita el firmware v1.6)."; return }
        val lista = Protocolo.parseListaSd(r) ?: run { mensaje.value = r.trim(); return }
        val conDatos = lista.filter { it.bytes > 0 }
        if (conDatos.isEmpty()) { mensaje.value = "La SD todavía no tiene registros."; return }
        val guardados = mutableListOf<String>()
        for (a in conDatos) {
            val texto = StringBuilder()
            var desde = 0L
            while (desde < a.bytes) {
                val p = pedir("sd leer ${a.nombre} $desde")?.let { Protocolo.parsePedazoSd(it) }
                if (p == null) { mensaje.value = "Se cortó al bajar ${a.nombre}."; return }
                if (p.texto.isEmpty()) break
                texto.append(p.texto)
                desde += p.texto.length
                mensaje.value = "Bajando ${a.nombre}: ${(desde * 100 / a.bytes).coerceAtMost(100)} %"
            }
            withContext(Dispatchers.IO) { guardar(ctx, a.nombre, texto.toString()) }
            guardados += a.nombre
        }
        mensaje.value = "Guardados en Descargas/FT150TS: ${guardados.joinToString(", ")}"
    }

    private suspend fun pedir(cmd: String): String? {
        val d = CompletableDeferred<String>()
        respuesta = d
        Moto.comandoSilencioso(cmd)
        return try { withTimeoutOrNull(10_000) { d.await() } } finally { respuesta = null }
    }

    /** Descargas/FT150TS/<nombre> (reemplaza el de la vez pasada). */
    private fun guardar(ctx: Context, nombre: String, texto: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val cr = ctx.contentResolver
            val ruta = Environment.DIRECTORY_DOWNLOADS + "/FT150TS/"
            val col = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            cr.delete(col, "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(ruta, nombre))
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, ruta)
            }
            val uri = cr.insert(col, v) ?: return
            cr.openOutputStream(uri)?.use { it.write(texto.toByteArray()) }
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FT150TS").apply { mkdirs() }
            File(dir, nombre).writeText(texto)
        }
    }
}

/**
 * WiFi de tu casa y tu bot de Telegram en la tarjeta (firmware v1.6+). Las claves se mandan a la tarjeta por el enlace
 * cifrado y se quedan ahí: la app no las guarda y la tarjeta nunca las regresa (solo dice si están puestas).
 */
object CasaTarjeta {
    val estado = MutableStateFlow<CasaEstado?>(null)
    val mensaje = MutableStateFlow("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    @Volatile private var esperando = 0

    /** Respuestas de texto a los comandos "casa ..." (las JSON van aparte). true = era para esto. */
    fun recibir(t: String): Boolean {
        if (esperando <= 0 || t.startsWith("{") || t.startsWith("[")) return false
        esperando--
        mensaje.value = t.lines().lastOrNull { it.isNotBlank() } ?: t
        return true
    }

    fun pedirEstado() = Moto.comandoSilencioso("casa")

    private fun enviar(cmd: String) {
        esperando++
        Moto.comandoSilencioso(cmd)
    }

    fun guardarWifi(ssid: String, clave: String) {
        if (ssid.isNotBlank()) enviar("casa ssid ${ssid.trim()}")
        if (clave.isNotEmpty()) enviar("casa clave $clave")
        pedirEstado()
    }

    fun guardarTelegram(token: String, chat: String) {
        enviar("casa tg ${token.trim()} ${chat.trim()}")
        pedirEstado()
    }

    fun ponerCada(min: Int) { enviar("casa cada $min"); pedirEstado() }
    fun probar() { mensaje.value = "Probando (hasta 20 s)…"; enviar("casa prueba") }
    fun borrar() { enviar("casa borrar"); pedirEstado() }

    /**
     * Busca el id de tu chat con el bot: le escribes /start a tu bot y la app le pregunta a Telegram (desde el
     * teléfono) quién le escribió. Regresa el id o un mensaje de error.
     */
    fun buscarChat(token: String, listo: (id: String?, error: String?) -> Unit) {
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val c = URL("https://api.telegram.org/bot${token.trim()}/getUpdates").openConnection() as HttpURLConnection
                    c.connectTimeout = 10_000; c.readTimeout = 10_000
                    val codigo = c.responseCode
                    if (codigo == 401 || codigo == 404) return@withContext null to "Ese token no es válido (cópialo completo de @BotFather)."
                    val cuerpo = c.inputStream.bufferedReader().use { it.readText() }
                    LogicaTelegram.ultimoChat(cuerpo)?.let { it to null }
                        ?: (null to "Todavía no le escribes a tu bot: mándale /start desde tu Telegram y vuelve a tocar Buscar.")
                } catch (e: Exception) {
                    null to "Sin internet o Telegram no respondió."
                }
            }
            listo(r.first, r.second)
        }
    }
}

/** Partes puras de Telegram (se prueban sin teléfono). */
object LogicaTelegram {
    /** El chat del mensaje más reciente de una respuesta de getUpdates, o null. */
    fun ultimoChat(json: String): String? = try {
        val a = JSONObject(json).optJSONArray("result")
        var id: String? = null
        if (a != null) for (i in 0 until a.length()) {
            val u = a.getJSONObject(i)
            val m = u.optJSONObject("message") ?: u.optJSONObject("edited_message") ?: continue
            m.optJSONObject("chat")?.let { id = it.optLong("id").toString() }
        }
        id
    } catch (e: Exception) {
        null
    }

    /** Un token de bot tiene la forma 123456789:AAH... (número, dos puntos y unos 35 caracteres). */
    fun tokenValido(t: String) = Regex("^\\d{6,12}:[A-Za-z0-9_-]{30,50}$").matches(t.trim())
}
