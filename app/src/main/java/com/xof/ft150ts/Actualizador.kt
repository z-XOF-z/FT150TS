package com.xof.ft150ts

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Una versión publicada en GitHub. */
data class Publicacion(val version: String, val apk: String, val tamano: Long, val notas: String, val pagina: String, val beta: Boolean = false)

sealed class EstadoActualizacion {
    data object Nada : EstadoActualizacion()
    data object Buscando : EstadoActualizacion()
    data class AlDia(val version: String) : EstadoActualizacion()
    data class Hay(val p: Publicacion) : EstadoActualizacion()
    data class Descargando(val pct: Int) : EstadoActualizacion()
    data object Instalando : EstadoActualizacion()
    data class Error(val texto: String) : EstadoActualizacion()
}

/**
 * Actualizar la app desde ella misma: busca la última versión en los releases de GitHub (z-XOF-z/FT150TS),
 * la descarga y la instala encima; o instala un APK que elijas. Android siempre pide confirmar.
 *
 * Antes de instalar revisa que el APK sea FT150TS (mismo paquete), con la MISMA firma que la app instalada y una
 * versión más nueva. Así se actualiza sin borrar datos, y un APK ajeno o viejo no pasa. Antes guarda un respaldo.
 */
@SuppressLint("StaticFieldLeak")   // solo guarda applicationContext
object Actualizador {
    const val REPO = "z-XOF-z/FT150TS"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    /** Con betas: la lista (latest de GitHub nunca incluye pre-releases). */
    private const val API_LISTA = "https://api.github.com/repos/$REPO/releases?per_page=20"

    /** Recibir versiones de prueba (pre-releases de GitHub). De fábrica: sí si la instalada ya es beta. */
    val betas = MutableStateFlow(false)

    fun ponerBetas(c: Context, on: Boolean) {
        betas.value = on
        c.applicationContext.getSharedPreferences("actualizar", Context.MODE_PRIVATE).edit().putBoolean("betas", on).apply()
        estado.value = EstadoActualizacion.Nada
    }

    /** Lee la preferencia (al abrir Ajustes). */
    fun cargar(c: Context) {
        val actual = versionActual(c)
        betas.value = c.applicationContext.getSharedPreferences("actualizar", Context.MODE_PRIVATE)
            .getBoolean("betas", ReglasActualizacion.prueba(actual) != null)
    }
    private const val ACCION_RESULTADO = "com.xof.ft150ts.INSTALAR_RESULTADO"
    private const val MAX_APK = 60L * 1024 * 1024

    val estado = MutableStateFlow<EstadoActualizacion>(EstadoActualizacion.Nada)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var app: Context? = null

    private val resultado = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // Android muestra "¿Actualizar esta app?"; al aceptar, la app se cierra y abre ya actualizada
                    @Suppress("DEPRECATION")
                    val confirmar = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    try { c.startActivity(confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {
                        estado.value = EstadoActualizacion.Error("No se pudo abrir la confirmación de Android")
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> estado.value = EstadoActualizacion.Nada
                else -> estado.value = EstadoActualizacion.Error(
                    "Android no la instaló: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "cancelado"))
            }
        }
    }

    private fun iniciar(c: Context): Context {
        app?.let { return it }
        val ctx = c.applicationContext
        app = ctx
        // No exportado en todas las versiones: otra app no puede mandar un "resultado" falso
        ContextCompat.registerReceiver(ctx, resultado, IntentFilter(ACCION_RESULTADO), ContextCompat.RECEIVER_NOT_EXPORTED)
        return ctx
    }

    fun versionActual(c: Context): String = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }

    // ---------------- Buscar ----------------

    fun buscar(c: Context) {
        val ctx = iniciar(c)
        if (ocupado()) return
        estado.value = EstadoActualizacion.Buscando
        scope.launch {
            estado.value = try {
                val conBetas = betas.value
                val p = (if (conBetas) ReglasActualizacion.elegirPublicacion(leerTexto(API_LISTA), true)
                    else ReglasActualizacion.leerPublicacion(leerTexto(API)))
                    ?: return@launch run { estado.value = EstadoActualizacion.Error("No hay un release con APK en GitHub") }
                val actual = versionActual(ctx)
                if (ReglasActualizacion.esMasNueva(p.version, actual)) EstadoActualizacion.Hay(p)
                else EstadoActualizacion.AlDia(actual)
            } catch (e: Exception) {
                EstadoActualizacion.Error("Sin conexión con GitHub (${e.javaClass.simpleName})")
            }
        }
    }

    private fun ocupado() = estado.value.let {
        it is EstadoActualizacion.Buscando || it is EstadoActualizacion.Descargando || it is EstadoActualizacion.Instalando
    }

    // ---------------- Descargar e instalar ----------------

    fun descargarEInstalar(c: Context, p: Publicacion) {
        val ctx = iniciar(c)
        if (ocupado()) return
        if (!puedeInstalar(ctx)) { pedirPermisoInstalar(ctx); return }
        estado.value = EstadoActualizacion.Descargando(0)
        scope.launch {
            try {
                val destino = File(File(ctx.cacheDir, "actualizacion").apply { mkdirs() }, "FT150TS-nueva.apk")
                descargar(p.apk, destino)
                instalarArchivo(ctx, destino)
            } catch (e: Exception) {
                estado.value = EstadoActualizacion.Error(e.message ?: "Falló la descarga")
            }
        }
    }

    /** Instala un APK elegido con el selector de archivos (content://). */
    fun instalarDesdeUri(c: Context, uri: Uri) {
        val ctx = iniciar(c)
        if (ocupado()) return
        if (!puedeInstalar(ctx)) { pedirPermisoInstalar(ctx); return }
        estado.value = EstadoActualizacion.Instalando
        scope.launch {
            try {
                val destino = File(File(ctx.cacheDir, "actualizacion").apply { mkdirs() }, "FT150TS-elegida.apk")
                ctx.contentResolver.openInputStream(uri)?.use { ent ->
                    destino.outputStream().use { sal -> copiarConTope(ent, sal) }
                } ?: throw IllegalStateException("No se pudo leer el archivo")
                instalarArchivo(ctx, destino)
            } catch (e: Exception) {
                estado.value = EstadoActualizacion.Error(e.message ?: "No se pudo leer el APK")
            }
        }
    }

    private suspend fun instalarArchivo(ctx: Context, apk: File) {
        estado.value = EstadoActualizacion.Instalando
        val motivo = withContext(Dispatchers.IO) { revisarApk(ctx, apk) }
        if (motivo != null) {
            apk.delete()
            estado.value = EstadoActualizacion.Error(motivo)
            return
        }
        // Respaldo antes de actualizar (por si acaso). Android conserva los datos al actualizar encima.
        Respaldo.guardarEnDescargas(automatico = true)
        withContext(Dispatchers.IO) {
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("ft150ts.apk", 0, apk.length()).use { sal -> apk.inputStream().use { it.copyTo(sal) }; s.fsync(sal) }
                val aviso = PendingIntent.getBroadcast(
                    ctx, id, Intent(ACCION_RESULTADO).setPackage(ctx.packageName),
                    // El instalador de Android agrega el estado al Intent: tiene que ser mutable (va solo a esta app)
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
                )
                s.commit(aviso.intentSender)
            }
        }
    }

    /** null si el APK se puede instalar encima; si no, el motivo. */
    private fun revisarApk(ctx: Context, apk: File): String? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val nuevo = pm.getPackageArchiveInfo(apk.path, flags) ?: return "El archivo no es un APK válido"
        val actual = pm.getPackageInfo(ctx.packageName, flags)
        return ReglasActualizacion.motivoRechazo(
            paqueteNuevo = nuevo.packageName, paquete = ctx.packageName,
            codigoNuevo = codigo(nuevo), codigoActual = codigo(actual),
            firmasNuevas = firmas(nuevo), firmasActuales = firmas(actual),
        )
    }

    @Suppress("DEPRECATION")
    private fun codigo(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun firmas(p: PackageInfo): Set<String> {
        val f = if (Build.VERSION.SDK_INT >= 28) {
            val s = p.signingInfo ?: return emptySet()
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else p.signatures
        return (f ?: emptyArray()).map { ProtocoloCarPlay.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }

    fun puedeInstalar(c: Context) = c.packageManager.canRequestPackageInstalls()

    /** Ajuste "Instalar apps desconocidas" para FT150TS (se da una vez). */
    fun pedirPermisoInstalar(c: Context) {
        estado.value = EstadoActualizacion.Error("Permite \"Instalar apps desconocidas\" para FT150TS y vuelve a intentar")
        try {
            c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }

    // ---------------- Red ----------------

    private fun conectar(url: String): HttpURLConnection {
        require(ReglasActualizacion.urlPermitida(url)) { "Dirección no permitida" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FT150TS")
            instanceFollowRedirects = false
        }
    }

    /** Sigue redirecciones a mano (máx. 5) y solo hacia GitHub por https. */
    private fun abrir(url: String): HttpURLConnection {
        var u = url
        repeat(6) {
            val c = conectar(u)
            when (c.responseCode) {
                in 200..299 -> return c
                301, 302, 303, 307, 308 -> {
                    u = c.getHeaderField("Location") ?: throw IllegalStateException("Redirección sin destino")
                    c.disconnect()
                }
                else -> { val code = c.responseCode; c.disconnect(); throw IllegalStateException("GitHub respondió $code") }
            }
        }
        throw IllegalStateException("Demasiadas redirecciones")
    }

    private fun leerTexto(url: String): String = abrir(url).let { c ->
        try { c.inputStream.bufferedReader().use { it.readText().take(1_000_000) } } finally { c.disconnect() }
    }

    private fun descargar(url: String, destino: File) {
        val c = abrir(url)
        try {
            val total = c.contentLengthLong
            if (total > MAX_APK) throw IllegalStateException("El APK es demasiado grande")
            c.inputStream.use { ent ->
                destino.outputStream().use { sal ->
                    val buf = ByteArray(64 * 1024)
                    var leido = 0L
                    while (true) {
                        val n = ent.read(buf)
                        if (n < 0) break
                        leido += n
                        if (leido > MAX_APK) throw IllegalStateException("El APK es demasiado grande")
                        sal.write(buf, 0, n)
                        if (total > 0) estado.value = EstadoActualizacion.Descargando((leido * 100 / total).toInt())
                    }
                }
            }
        } finally { c.disconnect() }
    }

    private fun copiarConTope(ent: java.io.InputStream, sal: java.io.OutputStream) {
        val buf = ByteArray(64 * 1024)
        var leido = 0L
        while (true) {
            val n = ent.read(buf)
            if (n < 0) break
            leido += n
            if (leido > MAX_APK) throw IllegalStateException("El archivo es demasiado grande para ser la app")
            sal.write(buf, 0, n)
        }
    }
}

/** Reglas puras de la actualización (se prueban en ActualizadorTest). */
object ReglasActualizacion {
    private val HOSTS = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    /** Solo https y solo GitHub. */
    fun urlPermitida(url: String): Boolean = try {
        val u = URL(url)
        u.protocol == "https" && u.host in HOSTS && u.userInfo == null && (u.port == -1 || u.port == 443)
    } catch (_: Exception) { false }

    /** "v3.2" / "3.2.1" / "v3.2-beta.1" → [3, 2, 1] (solo los números). null si no es una versión. */
    fun numeros(v: String): List<Int>? {
        val limpio = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore(' ')
        val partes = limpio.split('.')
        if (partes.isEmpty() || partes.size > 4) return null
        return partes.map { it.toIntOrNull()?.takeIf { n -> n in 0..9999 } ?: return null }
    }

    /** Parte de prueba: "v3.2-beta.1" → ("beta", 1); "3.2" → null (estable). */
    fun prueba(v: String): Pair<String, Int>? {
        val resto = v.trim().substringAfter('-', "").lowercase()
        if (resto.isEmpty()) return null
        val etiqueta = resto.takeWhile { it.isLetter() }.ifEmpty { "beta" }
        val n = resto.dropWhile { it.isLetter() }.trimStart('.').takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        return etiqueta to n
    }

    /** >0 si [a] es más nueva que [b]. Mismos números: la estable gana a la beta; entre betas, por etiqueta y número. */
    fun comparar(a: String, b: String): Int {
        val na = numeros(a) ?: return -1
        val nb = numeros(b) ?: return 1
        for (i in 0 until maxOf(na.size, nb.size)) {
            val x = na.getOrElse(i) { 0 }; val y = nb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        val pa = prueba(a); val pb = prueba(b)
        return when {
            pa == null && pb == null -> 0
            pa == null -> 1                  // 3.2 > 3.2-beta.N
            pb == null -> -1
            pa.first != pb.first -> pa.first.compareTo(pb.first)   // alpha < beta < rc
            else -> pa.second.compareTo(pb.second)
        }
    }

    fun esMasNueva(publicada: String, actual: String): Boolean = numeros(publicada) != null && comparar(publicada, actual) > 0

    /** Un release (objeto de la API) → Publicación, si trae un .apk de GitHub y se acepta su tipo. */
    private fun publicacionDe(o: JSONObject, conBetas: Boolean): Publicacion? {
        if (o.optBoolean("draft")) return null
        val beta = o.optBoolean("prerelease")
        if (beta && !conBetas) return null
        val tag = o.optString("tag_name")
        if (numeros(tag) == null) return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val url = a.optString("browser_download_url")
            if (a.optString("name").endsWith(".apk", ignoreCase = true) && urlPermitida(url)) {
                return Publicacion(tag, url, a.optLong("size"), o.optString("body").take(4000), o.optString("html_url"), beta)
            }
        }
        return null
    }

    /** Lee la respuesta de /releases/latest (solo estables). */
    fun leerPublicacion(json: String): Publicacion? = publicacionDe(JSONObject(json), conBetas = false)

    /** De la lista /releases, la versión más nueva que se acepte (con betas si [conBetas]). */
    fun elegirPublicacion(json: String, conBetas: Boolean): Publicacion? {
        val l = JSONArray(json)
        var mejor: Publicacion? = null
        for (i in 0 until l.length()) {
            val p = publicacionDe(l.optJSONObject(i) ?: continue, conBetas) ?: continue
            if (mejor == null || comparar(p.version, mejor.version) > 0) mejor = p
        }
        return mejor
    }

    /** null = se puede instalar encima; si no, por qué no. */
    fun motivoRechazo(
        paqueteNuevo: String?, paquete: String,
        codigoNuevo: Long, codigoActual: Long,
        firmasNuevas: Set<String>, firmasActuales: Set<String>,
    ): String? = when {
        paqueteNuevo != paquete -> "Ese APK no es FT150TS ($paqueteNuevo)"
        firmasNuevas.isEmpty() || firmasActuales.isEmpty() -> "No se pudo leer la firma del APK"
        firmasNuevas.intersect(firmasActuales).isEmpty() ->
            "La firma no es la misma que la de tu app: Android obligaría a desinstalar y perderías tus datos. No se instala."
        codigoNuevo < codigoActual -> "Ese APK es más viejo que el instalado ($codigoNuevo < $codigoActual)"
        codigoNuevo == codigoActual -> "Ya tienes esa versión"
        else -> null
    }
}
