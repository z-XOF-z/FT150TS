package com.xof.ft150ts

import android.content.Context
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * SensorManager pedido con un contexto de PANTALLA, no con el de la aplicación.
 *
 * Medido en el POCO F4 (HyperOS 1 / Android 14): con el contexto de la aplicación, el sistema arma un objeto
 * Display nuevo en CADA lectura de sensor (ContextImpl.getDisplayNoVerify → ResourcesManager.getAdjustedDisplay).
 * Con el acelerómetro a ~400 Hz eso era el 89 % del trabajo del hilo de las RPM (~30 % de CPU). Un contexto de
 * pantalla guarda su Display y el sistema lo reutiliza.
 */
fun sensores(ctx: Context): SensorManager? {
    val app = ctx.applicationContext
    val conPantalla = try {
        val pantalla = (app.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
        if (pantalla != null) app.createDisplayContext(pantalla) else app
    } catch (_: Exception) { app }
    return conPantalla.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
}
