package com.xof.ft150ts

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * App instalada que se puede abrir. [pixeles] = su ícono reducido a 16x16
 * (para dibujarlo pixelado, al estilo del tablero LCD).
 */
class AppInstalada(
    val nombre: String, val paquete: String, val actividad: String, val icono: Bitmap?, val pixeles: IntArray?,
    /** El ícono ya pintado en cuadritos (se hace al leer las apps, fuera de la pantalla: así no se traba). */
    val pixelado: Bitmap? = null,
    /** Igual pero en tonos de gris: se pinta del color del acento, como un LCD de un solo color. */
    val pixeladoLcd: Bitmap? = null,
)

const val PIXELES_ICONO = 16
const val NUM_ACCESOS = 4

/**
 * La app como launcher: lista de apps instaladas, favoritas, el botón
 * de la manzanita (abre la app que elijas) y la opción de ser la pantalla de inicio.
 */
object Lanzador {
    val apps = MutableStateFlow<List<AppInstalada>>(emptyList())
    val favoritas = MutableStateFlow<List<String>>(emptyList())     // paquetes, en orden
    val appManzana = MutableStateFlow("")                          // paquete del botón de la manzanita
    /** Accesos directos del tablero (paquetes; "" = vacío). */
    val accesos = MutableStateFlow(List(NUM_ACCESOS) { "" })
    /** La app se está viendo (entre onStart y onStop). Con la pantalla apagada las páginas apagan sus relojes. */
    val enPantalla = MutableStateFlow(true)
    /** Mostrar la animación de arranque la próxima vez que se vea la app (abriste el switch con la pantalla apagada). */
    val inicioPendiente = MutableStateFlow(false)
    /** Cuándo se desbloqueó el modo XOF (para el confeti); 0 = nada. */
    val confeti = MutableStateFlow(0L)
    /** Íconos pixelados al estilo del tablero. */
    val iconosPixel = MutableStateFlow(true)
    /** Estilo de los íconos: 0 normales, 1 pixel a color, 2 pixel LCD (de un solo color, el del acento). */
    val estiloIconos = MutableStateFlow(1)
    /** Mostrar la columna de accesos directos a la izquierda del tablero. */
    val mostrarAccesos = MutableStateFlow(true)
    /** Pedido de ir a una página: (página, momento). Lo usan el botón Inicio y el modo moto. */
    val irAPagina = MutableStateFlow(-1 to 0L)
    /** Último pedido de página ya atendido (sobrevive a que se recree la pantalla al girar). */
    var paginaAtendida = 0L
    val cargando = MutableStateFlow(false)

    private var cargadoEn = 0L
    private const val ALIAS = ".Inicio"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("lanzador", Context.MODE_PRIVATE)

    fun iniciar(ctx: Context) {
        val p = prefs(ctx)
        favoritas.value = p.getString("favoritas", "")?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        appManzana.value = p.getString("manzana", "") ?: ""
        val guardados = (p.getString("accesos", "") ?: "").split('\n')
        accesos.value = List(NUM_ACCESOS) { i -> guardados.getOrNull(i) ?: "" }
        iconosPixel.value = p.getBoolean("pixel", true)
        estiloIconos.value = p.getInt("estiloIconos", if (iconosPixel.value) 1 else 0)
        mostrarAccesos.value = p.getBoolean("mostrarAccesos", true)
    }

    fun ponerAcceso(ctx: Context, lugar: Int, paquete: String) {
        if (lugar !in 0 until NUM_ACCESOS) return
        val l = accesos.value.toMutableList()
        l[lugar] = paquete
        accesos.value = l
        prefs(ctx).edit().putString("accesos", l.joinToString("\n")).apply()
    }

    fun ponerEstiloIconos(ctx: Context, e: Int) {
        estiloIconos.value = e.coerceIn(0, 2)
        iconosPixel.value = estiloIconos.value != 0
        prefs(ctx).edit().putInt("estiloIconos", estiloIconos.value).putBoolean("pixel", iconosPixel.value).apply()
    }

    fun ponerIconosPixel(ctx: Context, on: Boolean) {
        iconosPixel.value = on
        prefs(ctx).edit().putBoolean("pixel", on).apply()
    }

    fun ponerMostrarAccesos(ctx: Context, on: Boolean) {
        mostrarAccesos.value = on
        prefs(ctx).edit().putBoolean("mostrarAccesos", on).apply()
    }

    fun irA(pagina: Int) { irAPagina.value = pagina to System.currentTimeMillis() }

    /** Lee las apps instaladas (con su ícono). Si se leyeron hace poco, no repite. */
    suspend fun cargar(ctx: Context, forzar: Boolean = false) {
        if (!forzar && apps.value.isNotEmpty() && System.currentTimeMillis() - cargadoEn < 60_000) return
        if (cargando.value) return
        cargando.value = true
        try {
            val lista = withContext(Dispatchers.IO) { leerApps(ctx.applicationContext) }
            apps.value = lista
            cargadoEn = System.currentTimeMillis()
        } catch (_: Exception) {
        } finally {
            cargando.value = false
        }
    }

    @Suppress("DEPRECATION")
    private fun leerApps(ctx: Context): List<AppInstalada> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val tam = (48 * ctx.resources.displayMetrics.density).toInt().coerceIn(48, 192)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { r ->
                val dibujo = try { r.loadIcon(pm) } catch (_: Exception) { null }
                val icono = try { dibujo?.toBitmap(tam, tam) } catch (_: Exception) { null }
                // 16x16: el dibujo se reduce promediando colores; queda listo para pintarlo en cuadritos
                val pixeles = try {
                    dibujo?.toBitmap(PIXELES_ICONO, PIXELES_ICONO)?.let { b ->
                        IntArray(PIXELES_ICONO * PIXELES_ICONO).also { b.getPixels(it, 0, PIXELES_ICONO, 0, 0, PIXELES_ICONO, PIXELES_ICONO) }
                    }
                } catch (_: Exception) { null }
                AppInstalada(r.loadLabel(pm).toString(), r.activityInfo.packageName, r.activityInfo.name, icono, pixeles,
                    pixeles?.let { pintarPixelado(it, false) }, pixeles?.let { pintarPixelado(it, true) })
            }
            .distinctBy { it.paquete + "/" + it.actividad }
            .sortedBy { it.nombre.lowercase() }
    }

    /** 16x16 cuadritos con un huequito entre ellos (5 px por cuadrito: 80x80). */
    private fun pintarPixelado(px: IntArray, lcd: Boolean): Bitmap? = try {
        val n = PIXELES_ICONO
        val cel = 5
        val b = Bitmap.createBitmap(n * cel, n * cel, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint()
        for (y in 0 until n) for (x in 0 until n) {
            val col = px[y * n + x]
            if ((col ushr 24) < 70) continue            // transparente: no se pinta
            p.color = if (!lcd) col or (0xFF shl 24) else {
                // LCD: 4 tonos de gris según qué tan claro es el pixel (luego se tiñe del acento)
                val y = (((col shr 16) and 0xFF) * 299 + ((col shr 8) and 0xFF) * 587 + (col and 0xFF) * 114) / 1000
                val g = when { y < 64 -> 90; y < 128 -> 140; y < 192 -> 200; else -> 255 }
                (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
            c.drawRect(x * cel.toFloat(), y * cel.toFloat(), x * cel + 4f, y * cel + 4f, p)
        }
        b
    } catch (_: Exception) { null }

    fun abrir(ctx: Context, app: AppInstalada): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.paquete, app.actividad))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        ctx.startActivity(i)
        true
    } catch (_: Exception) {
        abrirPaquete(ctx, app.paquete)
    }

    fun abrirPaquete(ctx: Context, paquete: String): Boolean = try {
        val i = ctx.packageManager.getLaunchIntentForPackage(paquete)
        if (i == null) false else { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
    } catch (_: Exception) { false }

    fun alternarFavorita(ctx: Context, paquete: String) {
        val l = favoritas.value
        val nueva = if (paquete in l) l - paquete else (l + paquete).take(8)
        favoritas.value = nueva
        prefs(ctx).edit().putString("favoritas", nueva.joinToString("\n")).apply()
    }

    fun ponerManzana(ctx: Context, paquete: String) {
        appManzana.value = paquete
        prefs(ctx).edit().putString("manzana", paquete).apply()
    }

    /**
     * Abre una app en ventana flotante dentro de [r] (en pixeles de pantalla).
     * Necesita las ventanas flotantes activas (Root.prepararVentanas). Si la app ya estaba
     * abierta en pantalla completa, Android la puede traer así: ciérrala en recientes y vuelve a probar.
     */
    fun abrirEnVentana(ctx: Context, paquete: String, r: Rect): Boolean = try {
        val i = ctx.packageManager.getLaunchIntentForPackage(paquete)
        if (i == null) false
        else {
            val opciones = ActivityOptions.makeBasic().setLaunchBounds(r)
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opciones.toBundle())
            true
        }
    } catch (_: Exception) { false }

    /** Abre la app de la manzanita. false si no hay una elegida o ya no está instalada. */
    fun abrirManzana(ctx: Context): Boolean {
        val p = appManzana.value
        return p.isNotBlank() && abrirPaquete(ctx, p)
    }

    fun abrirInfo(ctx: Context, paquete: String) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", paquete, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { }
    }

    // ---------------- Pantalla de inicio (launcher) ----------------

    /** ¿Está prendida la opción de ser launcher? (el "alias" con categoría HOME) */
    fun launcherDisponible(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx.packageName, ctx.packageName + ALIAS)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** ¿Es ya la pantalla de inicio predeterminada? */
    @Suppress("DEPRECATION")
    fun esInicioPredeterminado(ctx: Context): Boolean = try {
        val r = ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )
        r?.activityInfo?.packageName == ctx.packageName
    } catch (_: Exception) { false }

    /**
     * Prende o apaga la opción de launcher. Android no deja que una app se ponga sola
     * como inicio: al prenderla se abre el ajuste donde la eliges.
     */
    fun ponerLauncher(ctx: Context, on: Boolean) {
        ponerAlias(ctx, on)
        abrirAjusteInicio(ctx)
    }

    /** Prende/apaga el "alias" HOME sin abrir nada. */
    fun ponerAlias(ctx: Context, on: Boolean) {
        try {
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx.packageName, ctx.packageName + ALIAS),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        } catch (_: Exception) { }
    }

    fun abrirAjusteInicio(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
        }
    }
}
