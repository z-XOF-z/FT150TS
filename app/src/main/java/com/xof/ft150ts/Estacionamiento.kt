package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

data class Lugar(val lat: Double, val lon: Double, val hora: Long)

/**
 * "¿Dónde dejé la moto?". Se guarda solo cuando la moto se desconecta
 * (al apagarla) y también se puede guardar a mano desde el mapa.
 */
object Estacionamiento {
    val lugar = MutableStateFlow<Lugar?>(null)
    private var prefs: SharedPreferences? = null

    fun iniciar(c: Context) {
        val p = c.applicationContext.getSharedPreferences("estacionamiento", Context.MODE_PRIVATE)
        prefs = p
        val lat = p.getString("lat", null)?.toDoubleOrNull()
        val lon = p.getString("lon", null)?.toDoubleOrNull()
        if (lat != null && lon != null) lugar.value = Lugar(lat, lon, p.getLong("hora", 0L))
    }

    /** Guarda donde está el teléfono ahora. false si todavía no hay ubicación. */
    fun guardarAqui(): Boolean {
        val pos = UbicacionManager.posicionActual() ?: return false
        val l = Lugar(pos.first, pos.second, System.currentTimeMillis())
        lugar.value = l
        prefs?.edit()?.putString("lat", l.lat.toString())?.putString("lon", l.lon.toString())?.putLong("hora", l.hora)?.apply()
        return true
    }

    fun borrar() {
        lugar.value = null
        prefs?.edit()?.remove("lat")?.remove("lon")?.remove("hora")?.apply()
    }
}
