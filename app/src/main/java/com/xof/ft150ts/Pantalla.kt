package com.xof.ft150ts

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import kotlin.math.roundToInt

/** Tasa de refresco: la app pide la más alta que tenga la pantalla (90 / 120 Hz). */
object Pantalla {
    /** Hz máximos de la pantalla del teléfono (ej. 120 en un Poco F4, 90 en un moto g power). */
    fun hzMaximos(ctx: Context): Int = try {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        d.supportedModes.maxOf { it.refreshRate }.roundToInt()
    } catch (_: Exception) { 60 }

    /** [alta] = usa el modo con más Hz (misma resolución); si no, deja que Android decida. */
    fun aplicar(a: Activity, alta: Boolean) {
        try {
            val d: Display = (if (Build.VERSION.SDK_INT >= 30) a.display else null)
                ?: @Suppress("DEPRECATION") a.windowManager.defaultDisplay
            val actual = d.mode
            val mejor = if (alta) {
                d.supportedModes
                    .filter { it.physicalWidth == actual.physicalWidth && it.physicalHeight == actual.physicalHeight }
                    .maxByOrNull { it.refreshRate }
            } else null
            val w = a.window
            w.attributes = w.attributes.apply {
                preferredDisplayModeId = mejor?.modeId ?: 0
                preferredRefreshRate = mejor?.refreshRate ?: 0f
            }
        } catch (_: Exception) { }
    }
}
