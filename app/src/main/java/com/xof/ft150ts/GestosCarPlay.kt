package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Gestos de CarPlay (v3.8, root). CarPlay en la pestaña Auto se siente como una pestaña más:
 *  - En la página de Widgets (la primera), desliza a la derecha → Tablero.
 *  - En la última página de Apps, desliza a la izquierda → Moto.
 * Cómo: mientras CarPlay se ve, se escucha la pantalla táctil con `getevent` (solo inicio/fin de cada toque, nada se
 * guarda) y, al soltar el dedo, se captura una franja de la pantalla para saber en qué página quedó CarPlay
 * (GestosCarPlayLogica). Solo cambia si CarPlay NO se movió (estabas en la orilla): dentro de una app o del mapa no hace
 * nada. Se apaga en cuanto CarPlay se esconde. Registro: adb logcat -s FT150-GC
 */
object GestosCarPlay {
    val activo = MutableStateFlow(true)
    /** Última página reconocida (para mostrar en Ajustes). */
    val pagina = MutableStateFlow(PaginaCarPlay.OTRA)

    private var prefs: SharedPreferences? = null
    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var escucha: Job? = null
    @Volatile private var proceso: Process? = null
    private val RUTA_FIFO = Regex("^/data/(user/0|data)/com\\.xof\\.ft150ts/no_backup/toques\\.fifo$")
    private val DISPOSITIVO = Regex("^/dev/input/event\\d{1,3}$")

    private fun log(m: String) = android.util.Log.i("FT150-GC", m)

    fun iniciar(ctx: Context) {
        app = ctx.applicationContext
        prefs = ctx.applicationContext.getSharedPreferences("carplay_gestos", Context.MODE_PRIVATE)
        activo.value = prefs?.getBoolean("activo", true) ?: true
        // Arranca y para con la ventana de CarPlay
        scope.launch { CarPlayPestana.visible.collect { v -> if (v && activo.value) arrancar() else parar() } }
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on; prefs?.edit()?.putBoolean("activo", on)?.apply()
        if (on && CarPlayPestana.visible.value) arrancar() else parar()
    }

    private data class Pantalla(val dev: String, val maxX: Int, val maxY: Int)

    /** Busca la pantalla táctil (la que tiene posiciones multitáctiles y no es virtual). */
    private fun buscarPantallaTactil(): Pantalla? {
        val r = Root.ejecutarCada(listOf("getevent -pl"))?.firstOrNull() ?: return null
        var dev: String? = null; var nombre = ""; var mx = -1; var my = -1
        var mejor: Pantalla? = null
        fun cerrar() { val d = dev; if (d != null && mx > 0 && my > 0 && !nombre.contains("uinput")) if (mejor == null) mejor = Pantalla(d, mx, my) }
        for (l in r.salida.lines()) {
            val t = l.trim()
            when {
                t.startsWith("add device") -> { cerrar(); dev = t.substringAfter(": ").trim(); nombre = ""; mx = -1; my = -1 }
                t.startsWith("name:") -> nombre = t.substringAfter("name:").trim().trim('"')
                t.startsWith("ABS_MT_POSITION_X") -> mx = Regex("max (\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                t.startsWith("ABS_MT_POSITION_Y") -> my = Regex("max (\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            }
        }
        cerrar()
        return mejor?.takeIf { DISPOSITIVO.matches(it.dev) }
    }

    @Synchronized private fun arrancar() {
        if (escucha?.isActive == true || !Root.listo()) return
        escucha = scope.launch {
            val p = buscarPantallaTactil() ?: run { log("no encontré la pantalla táctil"); return@launch }
            log("escuchando ${p.dev} (${p.maxX}×${p.maxY})")
            delay(1_200); clasificarYGuardar()                       // página inicial
            // Los toques NO pasan por la salida de `su`: el puente de Magisk la acumula por bloques y llegaban tarde y
            // amontonados (había que deslizar dos veces, o no llegaban). El proceso root escribe los eventos crudos en una
            // tubería (FIFO) dentro de la carpeta de la app y la app la lee directo, al instante.
            val fifo = File(app?.noBackupFilesDir ?: return@launch, "toques.fifo")
            try {
                fifo.delete()
                android.system.Os.mkfifo(fifo.absolutePath, 384)                       // 0600: solo la app y root
                if (!RUTA_FIFO.matches(fifo.absolutePath)) return@launch
                val pr = ProcessBuilder("su", "-c", "exec cat ${p.dev} > ${fifo.absolutePath}").start()
                proceso = pr
                val ent = withContext(Dispatchers.IO) { java.io.FileInputStream(fifo) }  // espera a que root la abra
                ent.use { leer(it, p) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e    // CarPlay se escondió: normal
            } catch (e: Exception) { log("lectura de toques falló: ${e.message}") } finally { parar(); fifo.delete() }
        }
    }

    @Synchronized private fun parar() {
        proceso?.destroy(); proceso = null
        if (Root.listo()) scope.launch { Root.ejecutarCada(listOf("pkill -f '^cat /dev/input/event[0-9]+$' || true")) }
        escucha?.cancel(); escucha = null
    }

    @Volatile private var antes = PaginaCarPlay.OTRA
    /** Dónde estaba el botón del dock la última vez (y con qué área de CarPlay): optimiza la siguiente captura. */
    @Volatile private var botonVisto: IntArray? = null
    @Volatile private var rectVisto: IntArray? = null
    private var fallosPista = 0
    @Volatile private var antesEn = 0L

    private suspend fun leer(entrada: java.io.InputStream, p: Pantalla) {
        // struct input_event (64 bits): timeval (16 bytes) + type (u16) + code (u16) + value (s32) = 24 bytes
        val TAM = 24
        val EV_SYN = 0; val EV_KEY = 1; val EV_ABS = 3
        val BTN_TOUCH = 0x14a; val MT_X = 0x35; val MT_Y = 0x36; val MT_ID = 0x39
        var rawX = -1; var rawY = -1
        var ini: Pair<Int, Int>? = null; var t0 = 0L
        var dedos = 0; var multi = false
        val ent = java.io.DataInputStream(entrada)
        val buf = ByteArray(TAM)
        val bb = java.nio.ByteBuffer.wrap(buf).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        while (scope.isActive && CarPlayPestana.visible.value) {
            val ok = withContext(Dispatchers.IO) { try { ent.readFully(buf); true } catch (_: java.io.IOException) { false } }
            if (!ok) break
            val tipo = bb.getShort(16).toInt() and 0xFFFF
            val cod = bb.getShort(18).toInt() and 0xFFFF
            val v = bb.getInt(20)
            when {
                tipo == EV_ABS && cod == MT_X -> if (dedos <= 1) rawX = v
                tipo == EV_ABS && cod == MT_Y -> if (dedos <= 1) rawY = v
                tipo == EV_ABS && cod == MT_ID -> if (v == -1) dedos = maxOf(0, dedos - 1) else { dedos++; if (dedos > 1) multi = true }
                tipo == EV_KEY && cod == BTN_TOUCH -> if (v == 1) { ini = null; t0 = System.currentTimeMillis(); multi = false } else {
                    // Soltaste: ¿fue un deslizamiento horizontal de un dedo dentro de CarPlay?
                    val a = ini; val fin = aPantalla(rawX, rawY, p)
                    val dur = System.currentTimeMillis() - t0
                    // No se borran rawX/rawY: el kernel no repite un eje que no cambió (si el siguiente toque empieza a la
                    // misma altura, solo llega la X) y hay que seguir usando la última posición conocida
                    ini = null; dedos = 0
                    if (a != null && fin != null && !multi) alSoltar(a, fin, dur)
                }
                tipo == EV_SYN && cod == 0 -> if (ini == null && rawX >= 0 && rawY >= 0) {
                    ini = aPantalla(rawX, rawY, p)
                    ini?.let { alBajar(it) }
                }
            }
        }
    }

    private fun aPantalla(rx: Int, ry: Int, p: Pantalla): Pair<Int, Int>? {
        if (rx < 0 || ry < 0) return null
        val c = app ?: return null
        val rot = (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        val m = android.util.DisplayMetrics().also {
            @Suppress("DEPRECATION") (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(it)
        }
        val corto = minOf(m.widthPixels, m.heightPixels); val largo = maxOf(m.widthPixels, m.heightPixels)
        return GestosCarPlayLogica.aPantalla(rx, ry, rot, corto, largo, p.maxX, p.maxY)
    }

    /** ¿Se están escuchando los toques ahora? (sin esto el vigilante de CarPlay revisa la escala por tiempo) */
    fun escuchando() = escucha?.isActive == true

    /** Lectura de la página tomada al PONER el dedo (la de este toque); null = no se tomó. */
    @Volatile private var bajada: kotlinx.coroutines.Deferred<PaginaCarPlay?>? = null

    /**
     * Pusiste el dedo en CarPlay. Si estabas en una página de inicio (o no se sabe), se lee la página YA: así el gesto
     * sabe dónde empezó aunque el anterior no haya terminado de leerse (v3.9.1: al llegar a Widgets y deslizar otra vez
     * enseguida, la lectura del gesto anterior iba 0.9 s atrás y había que deslizar 2 o 3 veces). En el mapa o dentro
     * de una app no se lee (no hay gesto posible y una captura por toque estorbaría).
     */
    private fun alBajar(p: Pair<Int, Int>) {
        val r = CarPlayPestana.rectDibujado() ?: return
        if (p.first !in r[0]..r[2] || p.second !in r[1]..r[3]) { bajada = null; return }
        val sabido = System.currentTimeMillis() - antesEn < 120_000
        if (sabido && antes == PaginaCarPlay.OTRA) { bajada = null; return }
        bajada = scope.async { clasificarYGuardar() }
    }

    private fun alSoltar(a: Pair<Int, Int>, b: Pair<Int, Int>, dur: Long) {
        CarPlayPestana.alSoltarToque(a.first, a.second)         // ¿arrastraste una esquina? (HyperOS la encoge así)
        val alBajarLeida = bajada.also { bajada = null }
        val r = CarPlayPestana.rectDibujado() ?: return
        if (a.first !in r[0]..r[2] || a.second !in r[1]..r[3]) return          // empezó fuera de CarPlay
        val dx = b.first - a.first; val dy = b.second - a.second
        scope.launch { decidir(alBajarLeida?.await(), r, dx, dy, dur) }
    }

    private suspend fun decidir(leidaAlBajar: PaginaCarPlay?, r: IntArray, dx: Int, dy: Int, dur: Long) {
        // Dónde empezó el gesto: lo leído al poner el dedo; si no, lo último que se supo (si es reciente)
        val pre = leidaAlBajar ?: antes
        val confiable = leidaAlBajar != null || System.currentTimeMillis() - antesEn < 120_000
        // Rápido: empezó en la orilla (Widgets o última de Apps) → se cambia al soltar, sin esperar
        val ya = if (confiable) GestosCarPlayLogica.destino(pre, pre, dx, dy, r[2] - r[0], dur) else null
        if (ya != null) {
            // Se confirma con una captura de AHORA: el video del iPhone llega ~0.3 s tarde, así que todavía muestra la
            // página de antes del gesto. Si no sigue en esa orilla, no se cambia de pestaña.
            val ahora = clasificarYGuardar()
            if (ahora == pre) {
                log("gesto rápido dx=$dx · $pre (confirmado${if (leidaAlBajar != null) ", leída al bajar" else ""}) · voy a la página $ya")
                withContext(Dispatchers.Main) { Lanzador.irA(ya) }
            } else {
                log("gesto rápido descartado: se creía $pre y está en $ahora")
                delay(900)
                clasificarYGuardar()
            }
            return
        }
        alSoltarLento(pre, r, dx, dy, dur)
    }

    /** Sin saber bien dónde empezó: se espera a que CarPlay se acomode y se revisa antes y después. */
    private suspend fun alSoltarLento(pre: PaginaCarPlay, r: IntArray, dx: Int, dy: Int, dur: Long) {
        delay(900)                     // que CarPlay termine de moverse (el video del iPhone llega ~0.3 s tarde)
        var post = clasificarYGuardar() ?: return
        // El botón del dock se anima (cuadrícula ↔ cuadro) al llegar a una página de inicio: si todavía no es claro,
        // una segunda lectura un momento después
        if (post == PaginaCarPlay.OTRA) { delay(450); post = clasificarYGuardar() ?: return }
        val d = GestosCarPlayLogica.destino(pre, post, dx, dy, r[2] - r[0], dur)
        log("gesto dx=$dx dy=$dy ${dur}ms · $pre → $post" + (d?.let { " · voy a la página $it" } ?: ""))
        if (d != null) withContext(Dispatchers.Main) { Lanzador.irA(d) }
    }

    /** Captura la franja de pantalla del dock y los puntitos y dice en qué página está CarPlay. */
    // Synchronized: la lectura al poner el dedo y la del gesto anterior pueden coincidir y usan el mismo archivo
    @Synchronized private fun clasificarYGuardar(): PaginaCarPlay? {
        val c = app ?: return null
        val r = CarPlayPestana.rectDibujado() ?: return null
        val m = android.util.DisplayMetrics().also {
            @Suppress("DEPRECATION") (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(it)
        }
        val ancho = m.widthPixels; val alto = m.heightPixels
        val pista = botonVisto?.takeIf { rectVisto.contentEquals(r) }
        val filas = pista?.let { GestosCarPlayLogica.filasConPista(it) } ?: GestosCarPlayLogica.filas(r[1], r[3] - r[1])
        val y0 = filas.first.coerceIn(0, alto - 1); val y1 = filas.last.coerceIn(y0, alto - 1)
        val archivo = File(c.cacheDir, "carplay_franja.raw")
        if (Root.capturaFranja(ancho, y0, y1 - y0 + 1, archivo.absolutePath) != true || !archivo.exists()) return null
        val fila = ancho * 4
        val datos = try { RandomAccessFile(archivo, "r").use { f -> ByteArray(f.length().toInt()).also { f.readFully(it) } } } catch (_: Exception) { return null }
        val lum = { x: Int, y: Int ->
            val i = (y - y0) * fila + x * 4
            if (x !in 0 until ancho || y !in y0..y1 || i + 2 >= datos.size) 128
            else ((datos[i].toInt() and 0xFF) * 30 + (datos[i + 1].toInt() and 0xFF) * 59 + (datos[i + 2].toInt() and 0xFF) * 11) / 100
        }
        val t0 = android.os.SystemClock.elapsedRealtime()
        val (pg, boton) = GestosCarPlayLogica.clasificarCon(lum, r, pista)
        // Con pista no se vio el botón: puede ser el mapa o una app (normal) o que CarPlay cambió de tamaño. Si falla
        // 2 veces seguidas, la siguiente captura vuelve a buscar en toda la esquina (sin capturar dos veces ahora).
        if (pista != null && pg == PaginaCarPlay.OTRA) { if (++fallosPista >= 2) { botonVisto = null; fallosPista = 0 } }
        else fallosPista = 0
        if (pg != PaginaCarPlay.OTRA && boton != null) { botonVisto = boton; rectVisto = r.copyOf() }
        log("página: $pg (${android.os.SystemClock.elapsedRealtime() - t0} ms${if (pista != null) ", con pista" else ""})")
        antes = pg
        antesEn = System.currentTimeMillis()
        pagina.value = pg
        return pg
    }
}
