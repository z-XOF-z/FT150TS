package com.xof.ft150ts

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Lo que trae un respaldo (para preguntar antes de restaurar). */
data class InfoRespaldo(val fecha: Long, val version: String, val ajustes: Int, val rodadas: Int)

/**
 * Respaldo y restaurar: TODOS tus datos (odómetro, mantenimientos, gasolina, marchas aprendidas, calibración,
 * ajustes, apariencia, logros, récords y rodadas con su recorrido) en un solo archivo .json.
 * Se guarda donde elijas (Drive, Descargas...), se comparte (WhatsApp, correo) o se hace solo cada semana en
 * Descargas/FT150TS. Al restaurar, la app se reinicia para cargar todo.
 */
object Respaldo {
    val mensaje = MutableStateFlow("")
    val autoSemanal = MutableStateFlow(true)
    val ultimoAuto = MutableStateFlow(0L)
    val ultimoManual = MutableStateFlow(0L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ctx: Context? = null

    fun iniciar(c: Context) {
        if (ctx != null) return
        val app = c.applicationContext
        ctx = app
        val p = app.getSharedPreferences("respaldo", Context.MODE_PRIVATE)
        autoSemanal.value = p.getBoolean("auto", true)
        ultimoAuto.value = p.getLong("ultimoAuto", 0L)
        ultimoManual.value = p.getLong("ultimoManual", 0L)
        // Respaldo automático: una vez por semana, un minuto después de abrir la app
        scope.launch {
            delay(60_000)
            if (autoSemanal.value && System.currentTimeMillis() - ultimoAuto.value > 7L * 24 * 3600_000L) guardarEnDescargas(automatico = true)
        }
    }

    fun ponerAutoSemanal(on: Boolean) {
        autoSemanal.value = on
        ctx?.getSharedPreferences("respaldo", Context.MODE_PRIVATE)?.edit()?.putBoolean("auto", on)?.apply()
    }

    fun nombre(): String = "FT150TS-respaldo-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"

    /** Arma el respaldo completo. */
    fun crearJson(c: Context): String {
        val raiz = JSONObject()
        raiz.put("app", "FT150TS")
        raiz.put("version", versionApp(c))
        raiz.put("fecha", System.currentTimeMillis())
        val prefs = JSONObject()
        val carpeta = File(c.applicationInfo.dataDir, "shared_prefs")
        carpeta.listFiles { f -> f.name.endsWith(".xml") }?.forEach { f ->
            val nombre = f.name.removeSuffix(".xml")
            val datos = JSONObject()
            for ((k, v) in c.getSharedPreferences(nombre, Context.MODE_PRIVATE).all) {
                val par = JSONArray()
                when (v) {
                    is Boolean -> { par.put("b"); par.put(v) }
                    is Int -> { par.put("i"); par.put(v) }
                    is Long -> { par.put("l"); par.put(v) }
                    is Float -> { par.put("f"); par.put(v.toDouble()) }
                    is String -> { par.put("s"); par.put(v) }
                    is Set<*> -> { par.put("ss"); par.put(JSONArray().apply { v.forEach { x -> put(x.toString()) } }) }
                    else -> continue
                }
                datos.put(k, par)
            }
            prefs.put(nombre, datos)
        }
        raiz.put("prefs", prefs)
        // Rodadas con su recorrido
        val archivos = JSONObject()
        File(c.filesDir, "rodadas").listFiles { f -> f.name.endsWith(".json") && f.name != "pendiente.json" }?.forEach { f ->
            archivos.put("rodadas/" + f.name, f.readText())
        }
        raiz.put("archivos", archivos)
        return raiz.toString()
    }

    /** Guarda en el archivo que elegiste (selector de Android). */
    fun escribir(c: Context, uri: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val texto = crearJson(c)
                    c.contentResolver.openOutputStream(uri)?.use { it.write(texto.toByteArray()) } != null
                } catch (_: Exception) { false }
            }
            terminado(c, ok, "Respaldo guardado")
        }
    }

    /** Lo comparte (WhatsApp, Drive, correo…). */
    fun compartir(c: Context) {
        scope.launch {
            val archivo = withContext(Dispatchers.IO) {
                try {
                    val carpeta = File(c.cacheDir, "respaldos").apply { mkdirs() }
                    carpeta.listFiles()?.forEach { it.delete() }
                    File(carpeta, nombre()).apply { writeText(crearJson(c)) }
                } catch (_: Exception) { null }
            }
            if (archivo == null) { mensaje.value = "No se pudo crear el respaldo"; return@launch }
            val uri = FileProvider.getUriForFile(c, c.packageName + ".archivos", archivo)
            val enviar = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, archivo.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            c.startActivity(Intent.createChooser(enviar, "Compartir respaldo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            terminado(c, true, "Respaldo listo para compartir")
        }
    }

    /** Copia en Descargas/FT150TS (Android 10+, sin pedir permisos). */
    fun guardarEnDescargas(automatico: Boolean = false) {
        val c = ctx ?: return
        if (Build.VERSION.SDK_INT < 29) {
            if (!automatico) mensaje.value = "En este Android usa \"Guardar respaldo\" y elige la carpeta"
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val valores = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, nombre())
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FT150TS")
                    }
                    val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores) ?: return@withContext false
                    c.contentResolver.openOutputStream(uri)?.use { it.write(crearJson(c).toByteArray()) }
                    true
                } catch (_: Exception) { false }
            }
            if (automatico) {
                if (ok) {
                    ultimoAuto.value = System.currentTimeMillis()
                    c.getSharedPreferences("respaldo", Context.MODE_PRIVATE).edit().putLong("ultimoAuto", ultimoAuto.value).apply()
                }
            } else terminado(c, ok, "Respaldo guardado en Descargas/FT150TS")
        }
    }

    private fun terminado(c: Context, ok: Boolean, texto: String) {
        if (!ok) { mensaje.value = "No se pudo guardar el respaldo"; return }
        mensaje.value = texto
        ultimoManual.value = System.currentTimeMillis()
        c.getSharedPreferences("respaldo", Context.MODE_PRIVATE).edit().putLong("ultimoManual", ultimoManual.value).apply()
        Logros.desbloquear("respaldo")
    }

    /** Un respaldo real pesa unos cuantos MB (60 rodadas con su recorrido); más que esto no es un respaldo. */
    private const val MAX_BYTES = 32 * 1024 * 1024
    /** Nombres de archivo de ajustes que se aceptan (sin rutas ni caracteres raros). */
    private val NOMBRE_AJUSTES = Regex("^[A-Za-z0-9_]{1,40}$")
    /** Rodadas: solo "<número>.json". */
    private val NOMBRE_RODADA = Regex("^[0-9]{1,15}\\.json$")
    /**
     * Ajustes que un respaldo NO trae: los de root. Si no, un archivo ajeno (que te pasen por WhatsApp) podría
     * marcar el root como concedido y prender comandos root al abrir la app. Se quedan como estén en este teléfono.
     * Tampoco las firmas de DiPlay en que confías: si no, un archivo ajeno podría hacer pasar otra app por DiPlay.
     * Ni el registro del ahorro nocturno: dice qué regresar (datos, Wi-Fi, grupos de apps); uno ajeno o viejo
     * haría que "deshacer" cambiara cosas que este teléfono nunca cambió.
     */
    private val NO_RESTAURAR = setOf("root", CarPlay.PREFS_CONFIANZA, Nocturno.PREFS_REGISTRO)

    /** Lee un respaldo elegido con el selector; null si no es de esta app o es demasiado grande. */
    suspend fun leer(c: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = c.contentResolver.openInputStream(uri)?.use { leerConTope(it) } ?: return@withContext null
            val t = bytes.toString(Charsets.UTF_8)
            if (JSONObject(t).optString("app") == "FT150TS") t else null
        } catch (_: Exception) { null }
    }

    /** Lee como mucho [MAX_BYTES]; si el archivo es más grande, null (así un archivo enorme no tumba la app). */
    private fun leerConTope(entrada: java.io.InputStream): ByteArray? {
        val salida = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = entrada.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) return null
            salida.write(buf, 0, n)
        }
        return salida.toByteArray()
    }

    fun info(json: String): InfoRespaldo? = try {
        val o = JSONObject(json)
        val prefs = o.optJSONObject("prefs")
        var n = 0
        prefs?.keys()?.forEach { k -> n += prefs.optJSONObject(k)?.length() ?: 0 }
        InfoRespaldo(o.optLong("fecha"), o.optString("version"), n, o.optJSONObject("archivos")?.length() ?: 0)
    } catch (_: Exception) { null }

    /** Un valor de ajuste ya leído del respaldo (todavía sin escribir). */
    internal sealed class Valor {
        data class B(val v: Boolean) : Valor()
        data class I(val v: Int) : Valor()
        data class L(val v: Long) : Valor()
        data class F(val v: Float) : Valor()
        data class S(val v: String) : Valor()
        data class SS(val v: Set<String>) : Valor()
    }

    /**
     * Escribe todo y reinicia la app. Primero se lee y se revisa el respaldo COMPLETO; solo si todo está bien se
     * escribe (antes, un archivo dañado a la mitad dejaba los ajustes a medias). Todo en segundo plano.
     */
    fun restaurar(c: Context, json: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val (ajustes, rodadas) = revisarRespaldo(json)
                    escribirRespaldo(c, ajustes, rodadas)
                    true
                } catch (_: Exception) { false }
            }
            if (ok) {
                mensaje.value = "Restaurado: la app se reinicia…"
                reiniciarApp(c)
            } else mensaje.value = "Ese archivo no se pudo leer"
        }
    }

    /** Lee y valida todo el respaldo sin escribir nada. Lanza excepción si algo no cuadra. (internal: pruebas) */
    internal fun revisarRespaldo(json: String): Pair<Map<String, Map<String, Valor>>, Map<String, String>> {
        val o = JSONObject(json)
        require(o.optString("app") == "FT150TS")
        val prefs = o.getJSONObject("prefs")
        val ajustes = LinkedHashMap<String, Map<String, Valor>>()
        for (nombre in prefs.keys()) {
            if (!NOMBRE_AJUSTES.matches(nombre) || nombre in NO_RESTAURAR) continue
            val datos = prefs.getJSONObject(nombre)
            val valores = LinkedHashMap<String, Valor>()
            for (k in datos.keys()) {
                val par = datos.getJSONArray(k)
                valores[k] = when (par.getString(0)) {
                    "b" -> Valor.B(par.getBoolean(1))
                    "i" -> Valor.I(par.getInt(1))
                    "l" -> Valor.L(par.getLong(1))
                    "f" -> Valor.F(par.getDouble(1).toFloat())
                    "s" -> Valor.S(par.getString(1))
                    "ss" -> { val a = par.getJSONArray(1); Valor.SS((0 until a.length()).map { a.getString(it) }.toSet()) }
                    else -> continue
                }
            }
            ajustes[nombre] = valores
        }
        val rodadas = LinkedHashMap<String, String>()
        o.optJSONObject("archivos")?.let { archivos ->
            for (ruta in archivos.keys()) {
                val nombre = ruta.removePrefix("rodadas/")
                if (!ruta.startsWith("rodadas/") || !NOMBRE_RODADA.matches(nombre)) continue
                rodadas[nombre] = archivos.getString(ruta)
            }
        }
        return ajustes to rodadas
    }

    // commit() a propósito: la app se reinicia justo después y con apply() podría no alcanzar a guardarse.
    // Ya corre en segundo plano (Dispatchers.IO), así que no traba la pantalla.
    @android.annotation.SuppressLint("ApplySharedPref")
    internal fun escribirRespaldo(c: Context, ajustes: Map<String, Map<String, Valor>>, rodadas: Map<String, String>) {
        for ((nombre, valores) in ajustes) {
            val e = c.getSharedPreferences(nombre, Context.MODE_PRIVATE).edit().clear()
            for ((k, v) in valores) {
                when (v) {
                    is Valor.B -> e.putBoolean(k, v.v)
                    is Valor.I -> e.putInt(k, v.v)
                    is Valor.L -> e.putLong(k, v.v)
                    is Valor.F -> e.putFloat(k, v.v)
                    is Valor.S -> e.putString(k, v.v)
                    is Valor.SS -> e.putStringSet(k, v.v)
                }
            }
            e.commit()
        }
        val carpeta = File(c.filesDir, "rodadas").apply { mkdirs() }
        for ((nombre, texto) in rodadas) File(carpeta, nombre).writeText(texto)
    }

    /** Cierra y vuelve a abrir la app (así todo se carga desde lo restaurado, sin que nada lo sobrescriba). */
    private fun reiniciarApp(c: Context) {
        val i = c.packageManager.getLaunchIntentForPackage(c.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (i != null) c.startActivity(i)
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 350)
    }

    private fun versionApp(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }
}
