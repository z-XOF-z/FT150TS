package com.xof.ft150ts

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Modo "Pestaña": la pantalla completa de CarPlay de DiPlay ocupa la pestaña Auto de FT150TS (debajo o al lado de las
 * pestañas, que siguen siendo de FT150TS para deslizar).
 *
 * Cómo (probado en el POCO F4, HyperOS 3, root):
 *  - `am start --windowingMode 5` vuelve ventana flotante la pantalla de CarPlay (aunque ya estuviera abierta).
 *  - `am task resize` la pone sobre el área elegida. HyperOS dibuja las ventanas flotantes a escala (0.6102): se piden
 *    límites más grandes que la pantalla (área / escala) y se ve exactamente del tamaño del área.
 *  - Para esconderla se MUEVE fuera de la pantalla con el MISMO tamaño: DiPlay no cambia de lienzo y CarPlay no se
 *    reconecta (probado: regresa en ~0.3 s).
 *
 * Un solo encargado (el bucle de [trabajar]) lleva la ventana al último estado pedido (visible / escondida): si cambias
 * de pestaña rápido, las órdenes no se cruzan. Después de mostrarla LEE dónde quedó de verdad y, si quedó mal (corrida,
 * en pantalla completa, DiPlay se reinició), la recoloca con el camino completo (hasta 3 intentos).
 * Todo con comandos fijos (paquete de la lista de DiPlay, números validados).
 */
object CarPlayPestana {
    /** Área de las páginas de FT150TS en la pantalla (izq, arriba, der, abajo, en pixeles). La pone AppFT150. */
    @Volatile var area: IntArray? = null
    /** Las páginas tienen un riel de navegación a la izquierda (Vértice en horizontal): Máximo no lo tapa. */
    @Volatile var conRiel = false
    /** La ventana de CarPlay está sobre la pestaña ahora (la página Auto deja de dibujar su mapa). */
    val visible = MutableStateFlow(false)
    /** Lo último que pasó (para el detalle de CarPlay). */
    val evento = MutableStateFlow("")

    /** Lo que se quiere: true = sobre la pestaña, false = escondida. Lo lee el encargado. */
    private val deseado = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var app: Context? = null
    @Volatile private var trabajando = false
    /** Escala con que el sistema dibuja la ventana (se mide la primera vez). */
    @Volatile private var escala = 0f
    @Volatile private var tarea = -1
    /** Límites con que se mostró (para esconderla con el mismo tamaño). */
    @Volatile private var mostrados: IntArray? = null

    fun disponible() = CarPlay.rootListo()

    /** Dónde se DIBUJA CarPlay ahora (límites × escala de HyperOS), o null si no se ve. Lo usan los gestos. */
    fun rectDibujado(): IntArray? {
        if (!visible.value) return null
        val b = mostrados ?: return null
        val e = escala.takeIf { it > 0f } ?: return null
        return intArrayOf(b[0], b[1], b[0] + ((b[2] - b[0]) * e).toInt(), b[1] + ((b[3] - b[1]) * e).toInt())
    }

    /** Registro en el log del sistema (logcat -s FT150-CP): qué paso se hizo y qué contestó. */
    private fun log(m: String) { android.util.Log.i("FT150-CP", m) }

    /**
     * Dónde va CarPlay según el tamaño elegido. Sale del contenedor de las páginas, que NO se mueve al deslizar
     * (la página Auto sí se anima: si se midiera ahí, la ventana quedaría corrida a media animación).
     */
    fun areaSegunTamano(
        a: IntArray, tamano: TamanoCarPlay, anchoP: Int, altoP: Int, densidad: Float, sinBarras: Boolean = false,
    ): IntArray {
        // HyperOS pone abajo de cada ventana flotante una franja con una píldora (mover / cerrar). Si la ventana llega
        // al borde de abajo, se alarga BAJO_PILDORA más allá de la pantalla: la franja queda fuera y no se puede
        // tocar sin querer al manejar (probado en el POCO F4: la franja mide ~38 px en pantalla).
        // Con el módulo de Magisk "FT150TS · Ventanas flotantes sin barra" ya no hay franja: no se alarga (si no, se
        // cortaría la parte de abajo de CarPlay).
        val bajo = ((if (sinBarras) BAJO_SIN_BARRAS_DP else BAJO_PILDORA_DP) * densidad).toInt()
        val abajo = { y: Int -> if (y >= altoP - 2) altoP + bajo else y }
        return when (tamano) {
            // Sube hasta el margen bajo la línea de las pestañas (8 dp en todas las barras), sin tapar las pestañas
            TamanoCarPlay.MAXIMO -> intArrayOf(
                if (conRiel) a[0] else 0,
                if (conRiel) a[1] else (a[1] - (SUBIR_DP * densidad).toInt()).coerceAtLeast(0),
                anchoP, altoP + bajo,
            )
            TamanoCarPlay.AMPLIO -> intArrayOf(a[0], a[1], a[2], abajo(a[3]))
            TamanoCarPlay.AJUSTADO -> (10 * densidad).toInt().let { m -> intArrayOf(a[0] + m, a[1] + m, a[2] - m, a[3] - m) }
        }
    }

    /** ¿Está activo el módulo de Magisk que quita las barras de las ventanas flotantes? (se revisa una vez) */
    @Volatile private var sinBarras: Boolean? = null

    private fun moduloSinBarras(): Boolean {
        val r = Root.ejecutarCada(listOf("cmd overlay list com.android.systemui | grep -c '\\[x\\] $OVERLAY_SIN_BARRAS'"))
        val activo = r?.firstOrNull()?.salida?.trim()?.lines()?.lastOrNull()?.trim()?.toIntOrNull()?.let { it > 0 } ?: false
        log("módulo sin barras: $activo")
        return activo
    }

    const val OVERLAY_SIN_BARRAS = "com.xof.ft150ts.sinbarra"

    private const val BAJO_PILDORA_DP = 15f
    /** Con el módulo la barra de abajo mide 1 dp (no 0: ver modulo-magisk/LEEME.md): se esconde bajo la orilla. */
    private const val BAJO_SIN_BARRAS_DP = 2f
    private const val SUBIR_DP = 8f

    /** Muestra CarPlay sobre la pestaña Auto. Sin root, abre CarPlay en pantalla completa. */
    fun mostrar(ctx: Context) {
        app = ctx.applicationContext
        if (!disponible()) { CarPlay.abrirCarPlay(ctx); return }
        deseado.value = true
        trabajar()
        vigilar()
    }

    /** La esconde (fuera de la pantalla, mismo tamaño: CarPlay sigue sin reconectarse). */
    fun esconder(ctx: Context) {
        app = ctx.applicationContext
        deseado.value = false
        trabajar()
        if (disponible()) vigilar()
    }

    /**
     * FT150TS perdió el foco de la ventana (alguien tocó otra ventana encima). Si CarPlay debía estar escondida, es que
     * HyperOS la regresó a la pantalla y la tocaste: se revisa YA (sin esperar al vigilante) y se vuelve a sacar.
     */
    fun alPerderFoco() {
        if (deseado.value || !disponible() || CarPlay.modo.value != ModoCarPlay.PESTANA) return
        val ctx = app ?: return
        scope.launch { if (!trabajando && !deseado.value) revisarEscondida(ctx) }
    }

    /** Se olvida el tamaño (cambió el área o el tamaño elegido). La escala del sistema se conserva. */
    fun reiniciarMedidas() { mostrados = null; sinBarras = null }

    // ---------------- El vigilante ----------------

    /** El vigilante pidió volver a colocar la ventana (la movió alguien más). */
    @Volatile private var recolocar = false
    private var vigilancia: kotlinx.coroutines.Job? = null

    /**
     * Mientras CarPlay debe verse, revisa cada [CADA_MS] que la ventana siga en su lugar. Hace falta en HyperOS 1:
     * cuando DiPlay reinicia su pantalla (al reconectar con el iPhone), el sistema regresa la ventana a su tamaño de
     * fábrica, en medio de la pantalla. Solo lee (un dumpsys) y, si quedó mal, se la pasa al encargado.
     *
     * Escondida también la revisa (cada [CADA_ESCONDIDA_MS], solo con FT150TS en pantalla): tras horas con el
     * teléfono bloqueado DiPlay se reinicia y HyperOS regresa su ventana encima del tablero.
     */
    private fun vigilar() {
        if (vigilancia?.isActive == true) return
        vigilancia = scope.launch {
            var escondidaDesde = 0L
            var escalaRevisada = android.os.SystemClock.elapsedRealtime()
            while (true) {
                delay(CADA_MS)
                if (!deseado.value) {
                    if (!Lanzador.enPantalla.value || CarPlay.modo.value != ModoCarPlay.PESTANA || !disponible()) break
                    val ahora = android.os.SystemClock.elapsedRealtime()
                    if (trabajando || ahora - escondidaDesde < CADA_ESCONDIDA_MS) continue
                    escondidaDesde = ahora
                    app?.let { revisarEscondida(it) }
                    continue
                }
                if (!visible.value || trabajando) continue
                val esperado = mostrados ?: continue
                // La escala se lee con `dumpsys SurfaceFlinger`, que en Android 14 congela al compositor ~40 ms (medido
                // con perfetto: un tirón cada 3 s en CarPlay y en el tablero). Solo se revisa cuando pudo cambiar: tocaste
                // cerca de una esquina de la ventana (así la encoge HyperOS) o, sin gestos que escuchen los toques, cada
                // minuto.
                val ahora = android.os.SystemClock.elapsedRealtime()
                val toqueEsquina = revisarEscala
                val porTiempo = !GestosCarPlay.escuchando() && ahora - escalaRevisada >= ESCALA_SIN_GESTOS_MS
                if (!quedoBien(esperado)) {
                    log("vigilante: la ventana se movió, se recoloca")
                    recolocar = true
                    trabajar()
                } else if ((toqueEsquina || porTiempo) && run { revisarEscala = false; escalaRevisada = ahora; escalaCambiada() }) {
                    // HyperOS deja escalar una ventana flotante arrastrando cerca de una esquina: la ventana conserva su
                    // tamaño pero se dibuja chiquita (escala 0.25). Volver a ponerla tal cual no la arregla; sacarla del
                    // modo flotante y volverla a meter sí (CarPlay se reconecta solo en unos segundos).
                    log("vigilante: HyperOS cambió la escala de la ventana, se restablece")
                    restablecerEscala()
                    recolocar = true
                    trabajar()
                }
            }
        }
    }

    private const val CADA_MS = 3_000L
    private const val CADA_ESCONDIDA_MS = 6_000L
    private const val ESCALA_SIN_GESTOS_MS = 60_000L
    /** Por debajo de esta escala la ventana está encogida (HyperOS 1 la dibuja normal a 0.61). */
    private const val ESCALA_ENCOGIDA = 0.4f
    /** Distancia (px) a una esquina de la ventana dibujada en la que un toque puede ser el gesto de encogerla. */
    private const val CERCA_ESQUINA_PX = 120

    /** Hubo un toque cerca de una esquina: el vigilante revisa la escala en su siguiente vuelta. */
    @Volatile private var revisarEscala = false

    /** Lo llama GestosCarPlay al soltar cada toque (posición donde empezó, en pixeles de la pantalla). */
    fun alSoltarToque(x: Int, y: Int) {
        val r = rectDibujado() ?: return
        if (ProtocoloCarPlay.cercaDeEsquina(x, y, r, CERCA_ESQUINA_PX)) revisarEscala = true
    }

    /** ¿La ventana se está dibujando a otra escala que la medida al colocarla? */
    private fun escalaCambiada(): Boolean {
        if (escala <= 0f) return false
        val r = Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdLeerEscala()))?.firstOrNull() ?: return false
        val e = ProtocoloCarPlay.leerEscala(r.salida) ?: return false
        return kotlin.math.abs(e - escala) > 0.03f
    }

    private fun restablecerEscala() {
        val ctx = app ?: return
        val d = CarPlay.buscar(ctx)?.takeIf { it.confiable } ?: return
        Root.ejecutarCada(listOf(
            "am start --windowingMode 1 -n ${d.paquete}/${CarPlay.ACTIVIDAD_CARPLAY_PUBLICA}",
            "sleep 0.6",
            "am start --windowingMode 5 -n ${d.paquete}/${CarPlay.ACTIVIDAD_CARPLAY_PUBLICA}",
            "sleep 0.6",
        ))
    }

    // ---------------- El encargado ----------------

    private fun trabajar() {
        synchronized(this) { if (trabajando) return; trabajando = true }
        scope.launch {
            var aplicado: Boolean? = null
            try {
                // Repite hasta que el último pedido quede aplicado (si cambiaste de pestaña a media orden, sigue).
                // Un pedido se intenta una vez (con sus reintentos internos): si falla no se repite en bucle.
                while (true) {
                    val quiero = deseado.value
                    if (quiero == aplicado && !(quiero && recolocar)) break
                    val ctx = app ?: break
                    recolocar = false
                    if (quiero) aplicarVisible(ctx) else aplicarEscondida(ctx)
                    aplicado = quiero
                }
            } finally {
                synchronized(this@CarPlayPestana) { trabajando = false }
                // Llegó un pedido distinto justo al salir: otra vuelta
                if (aplicado != null && (deseado.value != aplicado || (deseado.value && recolocar))) trabajar()
            }
        }
    }

    private suspend fun aplicarVisible(ctx: Context) {
        val (anchoP, altoP) = pantalla(ctx)
        // Todavía no se sabe dónde están las páginas (la app recién abrió): se espera hasta 3 s
        var base = area
        var espera = 0
        while (base == null && espera < 3000 && deseado.value) { delay(100); espera += 100; base = area }
        if (base == null || !deseado.value) return
        if (sinBarras == null) sinBarras = moduloSinBarras()
        val a = areaSegunTamano(base, CarPlay.tamano.value, anchoP, altoP, ctx.resources.displayMetrics.density, sinBarras == true)

        // Rápido: ya existe y se escondió con este tamaño → un solo comando la regresa
        val previo = mostrados
        if (previo != null && tarea >= 0 && escala > 0f && previo.contentEquals(ProtocoloCarPlay.aLaMedida(a, escala))) {
            Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdRedimensionar(tarea, previo)))
            visible.value = true
            if (quedoBien(previo)) return
            evento.value = "CarPlay no quedó en su lugar: se recoloca"
        }
        // Completo (y se recoloca hasta 3 veces si no quedó donde debía)
        for (intento in 1..3) {
            if (!deseado.value) return
            if (colocarCompleto(ctx, a) && quedoBien(mostrados)) {
                evento.value = "CarPlay en la pestaña" + if (intento > 1) " (recolocado, intento $intento)" else ""
                return
            }
            delay(300L * intento)
        }
        evento.value = "No pude colocar CarPlay (¿DiPlay abierto? ¿root?)"
        log(evento.value)
    }

    /** Ventana flotante + número de tarea + escala + límites a la medida. */
    private suspend fun colocarCompleto(ctx: Context, a: IntArray): Boolean {
        val d = CarPlay.buscar(ctx)?.takeIf { it.confiable } ?: run { evento.value = "DiPlay no está listo"; return false }
        val r = Root.ejecutarCada(listOf(
            "am start --windowingMode 5 -n ${d.paquete}/${CarPlay.ACTIVIDAD_CARPLAY_PUBLICA}",
            ProtocoloCarPlay.cmdBuscarTarea(d.paquete),
        )) ?: return false
        log("abrir flotante: ${r[0].salida.take(120)} | tarea: ${r[1].salida.take(160)}")
        val t = ProtocoloCarPlay.leerTarea(r[1].salida) ?: run { evento.value = "No encontré la ventana de CarPlay"; log(evento.value); return false }
        tarea = t
        // La escala se mide con la ventana ya flotante. En HyperOS 1 tarda más en pasar a flotante: se reintenta y,
        // si no se pudo leer, NO se guarda (se vuelve a medir la próxima vez en lugar de quedarse con un valor malo).
        if (escala <= 0f) {
            for (intento in 1..3) {
                val m = Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdRedimensionar(t, a), "sleep 0.5", ProtocoloCarPlay.cmdLeerEscala()))
                val e = m?.getOrNull(2)?.salida?.let { ProtocoloCarPlay.leerEscala(it) }
                log("escala intento $intento: [${m?.getOrNull(2)?.salida?.take(80)}] -> $e")
                // HyperOS deja la ventana "encogida" (0.25) si se arrastró una esquina: aceptarla daba una ventana de
                // 9600 px y DiPlay pedía video de ese tamaño. Se saca y se vuelve a meter al modo flotante y se mide otra vez.
                if (e != null && e < ESCALA_ENCOGIDA) { log("ventana encogida por HyperOS ($e): se restablece"); restablecerEscala(); continue }
                if (e != null) { escala = e; break }
            }
            if (escala <= 0f) evento.value = "No pude medir la escala de la ventana: se reintenta al volver"
        }
        val b = ProtocoloCarPlay.aLaMedida(a, if (escala > 0f) escala else 1f)
        val rr = Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdRedimensionar(t, b)))
        log("colocar ${a.joinToString()} -> ${b.joinToString()} (escala $escala): ${rr?.firstOrNull()?.let { "${it.ok} ${it.salida.take(80)}" }}")
        mostrados = b
        visible.value = true
        return true
    }

    private suspend fun aplicarEscondida(ctx: Context) {
        val anchoP = bordeFuera(ctx)
        val altoP = altoFuera(ctx)
        val b = mostrados
        val t = tarea
        if (b == null || t < 0) { esconderSuelta(ctx, anchoP, altoP); visible.value = false; return }
        Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdRedimensionar(t, ProtocoloCarPlay.fueraDePantalla(b, anchoP, escala, altoP))))
        visible.value = false
        // Al desbloquear (o a media animación del sistema) HyperOS puede regresarla a la pantalla al terminar su
        // transición: se revisa un momento después
        delay(VERIFICAR_MS)
        if (!deseado.value) asegurarFuera(t, anchoP, altoP)
    }

    private const val VERIFICAR_MS = 800L

    /** Escondida: ¿sigue fuera? Si alguien la regresó (DiPlay se reinició, desbloqueo), se vuelve a sacar. */
    private suspend fun revisarEscondida(ctx: Context) {
        val anchoP = bordeFuera(ctx)
        val altoP = altoFuera(ctx)
        val t = tarea
        if (t < 0 || !asegurarFuera(t, anchoP, altoP)) esconderSuelta(ctx, anchoP, altoP)
    }

    /**
     * Lee dónde está la ventana [t] y, mientras se vea en la pantalla, la saca con su mismo tamaño (hasta 3 veces,
     * esperando cada vez más a que el sistema termine lo que estaba haciendo). false = no se pudo leer la tarea.
     */
    private suspend fun asegurarFuera(t: Int, anchoP: Int, altoP: Int): Boolean {
        for (intento in 1..3) {
            if (deseado.value) return true
            val v = Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdLeerVentana(t)))?.firstOrNull() ?: return false
            // Sin salida: esa tarea ya no existe (DiPlay abrió otra). Existe pero no flotante: no es de este modo
            val b = ProtocoloCarPlay.leerVentana(v.salida) ?: return v.salida.contains("mode=")
            val destino = ProtocoloCarPlay.fueraDePantalla(b, anchoP, escala, altoP)
            // Fuera de lado Y sin pasarse por abajo (si se pasa, HyperOS la puede regresar al deslizar desde abajo)
            if (b[0] >= anchoP && b[1] == destino[1]) return true
            log("escondida pero a la vista o salida por abajo (${b.joinToString()}), se acomoda (intento $intento)")
            Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdRedimensionar(t, destino)))
            delay(VERIFICAR_MS * intento)
        }
        return true
    }

    /**
     * La app se reinició (o DiPlay abrió otra pantalla en su ventana y HyperOS la regresó a su lugar de fábrica) y ya
     * no se sabe con qué medidas se mostró: se busca la ventana flotante de CarPlay y, si se ve en la pantalla, se
     * saca con su mismo tamaño (y se revisa que se quede fuera). Solo en modo Pestaña (en Ventana la ventana es tuya).
     */
    private suspend fun esconderSuelta(ctx: Context, anchoP: Int, altoP: Int) {
        if (CarPlay.modo.value != ModoCarPlay.PESTANA) return
        val d = CarPlay.buscar(ctx)?.takeIf { it.confiable } ?: return
        val r = Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdBuscarTarea(d.paquete))) ?: return
        val t = ProtocoloCarPlay.leerTarea(r[0].salida) ?: return
        tarea = t
        asegurarFuera(t, anchoP, altoP)
    }

    /** Lee dónde quedó la ventana de verdad: ventana flotante y con los límites pedidos (±3 px). */
    private fun quedoBien(esperado: IntArray?): Boolean {
        val e = esperado ?: return false
        val t = tarea.takeIf { it >= 0 } ?: return false
        val r = Root.ejecutarCada(listOf(ProtocoloCarPlay.cmdLeerVentana(t)))?.firstOrNull() ?: return false
        val ok = ProtocoloCarPlay.ventanaEnSuLugar(r.salida, e)
        if (!ok) log("no quedó en su lugar: esperaba ${e.joinToString()}, hay [${r.salida.take(120)}]")
        return ok
    }

    /**
     * Desde dónde la ventana ya no se ve, en cualquier orientación: el lado MÁS LARGO de la pantalla. Con el teléfono
     * bloqueado el sistema se pone en vertical (1080 de ancho): esconderla en x=1080 la dejaba a la vista al volver
     * a horizontal.
     */
    private fun bordeFuera(ctx: Context): Int = pantalla(ctx).let { maxOf(it.first, it.second) }

    /** Alto de la pantalla en horizontal (el lado corto): la ventana escondida no debe dibujarse más abajo. */
    private fun altoFuera(ctx: Context): Int = pantalla(ctx).let { minOf(it.first, it.second) }

    private fun pantalla(ctx: Context): Pair<Int, Int> {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        return if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else ctx.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }
}
