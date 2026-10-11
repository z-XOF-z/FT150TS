package com.xof.ft150ts

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exporta el recorrido dibujado en el mapa como archivo GPX
 * (lo abren Google Earth, Strava, OsmAnd, Wikiloc...) y abre el menú de compartir.
 */
object Gpx {
    /** Devuelve false si todavía no hay recorrido. */
    fun compartir(ctx: Context): Boolean {
        val puntos = UbicacionManager.recorrido.value
        if (puntos.size < 2) return false

        val ahora = Date()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val nombre = "FT150TS_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(ahora)

        val sb = StringBuilder(puntos.size * 64 + 512)
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="FT150TS XOF" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        sb.append("  <metadata><name>").append(nombre).append("</name><time>").append(iso.format(ahora)).append("</time></metadata>\n")
        sb.append("  <trk><name>").append(nombre).append("</name><trkseg>\n")
        for (p in puntos) {
            sb.append("    <trkpt lat=\"").append(String.format(Locale.US, "%.6f", p.latitude))
                .append("\" lon=\"").append(String.format(Locale.US, "%.6f", p.longitude)).append("\"/>\n")
        }
        sb.append("  </trkseg></trk>\n</gpx>\n")

        val carpeta = File(ctx.cacheDir, "rutas").apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() }   // solo se guarda la última
        val archivo = File(carpeta, "$nombre.gpx")
        archivo.writeText(sb.toString())

        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", archivo)
        val enviar = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, nombre)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val menu = Intent.createChooser(enviar, "Compartir ruta").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(menu)
        return true
    }

    /** Distancia del recorrido en km. */
    fun distanciaKm(): Float {
        val p = UbicacionManager.recorrido.value
        var d = 0.0
        for (i in 1 until p.size) d += p[i - 1].distanceToAsDouble(p[i])
        return (d / 1000.0).toFloat()
    }
}
