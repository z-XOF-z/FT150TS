package com.xof.ft150ts

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow

/** Lo que mide el medidor de fluidez en el último segundo. */
data class MedidaFluidez(val fps: Int = 0, val hz: Int = 0, val tirones: Float = 0f, val peorMs: Float = 0f)

/**
 * Medidor de fluidez: Android reporta cuánto tardó cada cuadro en dibujarse (FrameMetrics). Cada segundo se cuentan
 * los cuadros, los que se pasaron del tiempo de la pantalla (tirones: a 120 Hz son más de 8.3 ms) y el peor.
 * Corre en su propio hilo (no estorba al dibujo) y solo mientras está prendido en Ajustes > Pantalla.
 *
 * Ojo: con la pantalla quieta Android no dibuja cuadros nuevos; entonces los cuadros por segundo bajan y eso es
 * bueno (no gasta). Lo que importa son los tirones al deslizar, abrir pantallas o con el tablero en movimiento.
 */
object Fluidez {
    val medida = MutableStateFlow(MedidaFluidez())

    private var hilo: HandlerThread? = null
    private var ventana: Window? = null
    private var cuadros = 0
    private var tarde = 0
    private var peorNs = 0L
    private var desde = 0L
    private var presupuestoNs = 16_666_667L
    private var hz = 60
    private var pantalla: android.view.Display? = null

    private val oyente = Window.OnFrameMetricsAvailableListener { _, m, _ ->
        // Solo cuadros "normales" (el primero tras abrir la ventana mide la carga, no la fluidez)
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val total = m.getMetric(FrameMetrics.TOTAL_DURATION)
        cuadros++
        if (total > presupuestoNs) tarde++
        if (total > peorNs) peorNs = total
        val ahora = SystemClock.elapsedRealtime()
        if (desde == 0L) desde = ahora
        val ms = ahora - desde
        if (ms >= 1_000L) {
            // La pantalla puede cambiar de Hz (reposo, teléfono caliente): el presupuesto se vuelve a leer cada segundo
            pantalla?.let { d -> hz = d.refreshRate.toInt().coerceAtLeast(30); presupuestoNs = 1_000_000_000L / hz }
            medida.value = MedidaFluidez(
                fps = (cuadros * 1000L / ms).toInt(), hz = hz,
                tirones = if (cuadros > 0) tarde * 100f / cuadros else 0f, peorMs = peorNs / 1e6f,
            )
            cuadros = 0; tarde = 0; peorNs = 0L; desde = ahora
        }
    }

    fun iniciar(a: Activity) {
        if (ventana != null) return
        val t = HandlerThread("fluidez").also { it.start() }
        hilo = t
        val w = a.window
        ventana = w
        hz = Pantalla.hzActuales(a)
        pantalla = try {
            if (android.os.Build.VERSION.SDK_INT >= 30) a.display else @Suppress("DEPRECATION") a.windowManager.defaultDisplay
        } catch (_: Exception) { null }
        presupuestoNs = 1_000_000_000L / hz.coerceAtLeast(30)
        desde = 0L; cuadros = 0; tarde = 0; peorNs = 0L
        try { w.addOnFrameMetricsAvailableListener(oyente, Handler(t.looper)) } catch (_: Exception) { detener() }
    }

    fun detener() {
        try { ventana?.removeOnFrameMetricsAvailableListener(oyente) } catch (_: Exception) { }
        ventana = null
        pantalla = null
        hilo?.quitSafely()
        hilo = null
        medida.value = MedidaFluidez()
    }
}
