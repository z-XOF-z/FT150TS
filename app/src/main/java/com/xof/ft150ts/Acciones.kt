package com.xof.ft150ts

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/** Acciones que abren otras apps: navegar, caminar a la moto, compartir ubicación, Asistente. */
object Acciones {
    /** Últimos destinos (se guardan en el teléfono). */
    val recientes = MutableStateFlow<List<String>>(emptyList())

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("acciones", Context.MODE_PRIVATE)

    fun iniciar(ctx: Context) {
        recientes.value = prefs(ctx).getString("recientes", "")?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    private fun recordar(ctx: Context, destino: String) {
        val d = destino.trim()
        if (d.isEmpty()) return
        val lista = (listOf(d) + recientes.value.filter { !it.equals(d, ignoreCase = true) }).take(6)
        recientes.value = lista
        prefs(ctx).edit().putString("recientes", lista.joinToString("\n")).apply()
    }

    fun borrarRecientes(ctx: Context) {
        recientes.value = emptyList()
        prefs(ctx).edit().remove("recientes").apply()
    }

    private fun abrir(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) { false } catch (_: Exception) { false }

    /** Abre Google Maps en modo navegación hacia [destino] (dirección, lugar o "gasolinera"). */
    fun navegarA(ctx: Context, destino: String, guardar: Boolean = true): Boolean {
        if (destino.isBlank()) return abrir(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))
        if (guardar) recordar(ctx, destino)
        val q = Uri.encode(destino)
        return abrir(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q"))) ||
            abrir(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
    }

    fun caminarA(ctx: Context, lat: Double, lon: Double): Boolean {
        val c = String.format(Locale.US, "%.6f,%.6f", lat, lon)
        return abrir(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$c&mode=w"))) ||
            abrir(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$c?q=$c(Moto)")))
    }

    /** Manda tu ubicación (link de Google Maps) por WhatsApp, Telegram, SMS... */
    fun compartirUbicacion(ctx: Context): Boolean {
        val p = UbicacionManager.posicionActual() ?: return false
        val link = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", p.first, p.second)
        val enviar = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Aquí voy en la moto: $link")
        }
        return abrir(ctx, Intent.createChooser(enviar, "Compartir ubicación"))
    }

    /** Abre el Asistente de Google (o el asistente que tenga el teléfono). */
    fun abrirAsistente(ctx: Context): Boolean =
        abrir(ctx, Intent(Intent.ACTION_VOICE_COMMAND)) || abrir(ctx, Intent(Intent.ACTION_ASSIST))

    /** Busca algo en Google (si no es un comando de la app). */
    fun buscarEnGoogle(ctx: Context, texto: String): Boolean =
        abrir(ctx, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, texto))
}
