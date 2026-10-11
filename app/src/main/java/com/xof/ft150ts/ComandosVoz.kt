package com.xof.ft150ts

import android.content.Context
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Comandos de voz de la app (botón de micrófono en el mapa).
 * Lo que no sea un comando se busca en Google.
 *
 * Ejemplos: "pon Peso Pluma", "siguiente", "pausa", "sube el volumen", "clima",
 * "llévame a la plaza", "gasolinera", "dónde está mi moto", "guarda la moto",
 * "comparte mi ubicación", "batería", "límite", "hora", "gasolina".
 */
object ComandosVoz {
    private fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¿?¡!.,]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.empiezaCon(vararg p: String): String? {
        for (x in p) if (startsWith("$x ")) return removePrefix("$x ").trim()
        return null
    }

    private fun String.tiene(vararg p: String): Boolean = p.any { contains(it) }

    /** "pon pausa", "pon la siguiente"... no son canciones. */
    private val CONTROLES = setOf("pausa", "la musica", "musica", "play", "la siguiente", "siguiente", "la anterior", "anterior")

    /** Ejecuta lo que dijiste. Contesta en voz alta. */
    fun ejecutar(ctx: Context, dicho: String) {
        val original = dicho.trim()
        val t = normalizar(original)
        // mismas palabras que [t] pero con mayúsculas y acentos (para buscar la canción o el lugar)
        val limpio = original.replace(Regex("[¿?¡!.,]"), " ").replace(Regex("\\s+"), " ").trim()
        fun resto(despues: String): String {
            val quitar = t.split(" ").size - despues.split(" ").size
            return limpio.split(" ").drop(quitar).joinToString(" ").ifBlank { despues }
        }
        if (t.isEmpty()) return
        // "pon X" / "llévame a X" primero (así "pon Lluvia de Coldplay" no pregunta el clima)
        val musica = t.empiezaCon("pon", "ponme", "reproduce", "toca", "quiero escuchar", "escuchar")
            ?.takeIf { it.isNotBlank() && it !in CONTROLES && !it.startsWith("pausa") }
        val destino = t.empiezaCon("llevame a", "navega a", "navegar a", "ir a", "vamos a", "como llego a")
            ?.takeIf { it.isNotBlank() }
        val respuesta: String? = when {
            musica != null -> {
                val q = resto(musica)
                if (MusicaManager.buscar(q, null)) "Poniendo $q" else "No encontré una app de música para buscar"
            }
            destino != null -> {
                val q = resto(destino)
                Acciones.navegarA(ctx, q); "Vamos a $q"
            }

            // ---------- Lugares cercanos ----------
            t.tiene("gasolinera", "gasolina cerca", "cargar gasolina") -> {
                Acciones.navegarA(ctx, "gasolinera", guardar = false); "Buscando la gasolinera más cercana"
            }
            t.tiene("taller", "mecanico") -> {
                Acciones.navegarA(ctx, "taller de motos", guardar = false); "Buscando un taller de motos"
            }
            t.tiene("llantera", "ponchadura", "vulcanizadora") -> {
                Acciones.navegarA(ctx, "llantera", guardar = false); "Buscando una llantera"
            }

            // ---------- Música ----------
            t.tiene("siguiente", "otra cancion", "cambiale", "la que sigue") -> { MusicaManager.siguiente(); null }
            t.tiene("anterior", "la de antes", "regresa la cancion") -> { MusicaManager.anterior(); null }
            t.tiene("pausa", "pausar", "deten", "para la musica", "quita la musica", "silencio") -> { MusicaManager.pausar(); null }
            t == "play" || t.tiene("dale play", "reanuda", "continua", "sigue la musica", "pon la musica", "pon musica") -> {
                MusicaManager.reproducir(); null
            }
            t.tiene("sube el volumen", "subele", "mas volumen", "mas fuerte") -> {
                repeat(3) { MusicaManager.volumen(true) }; null
            }
            t.tiene("baja el volumen", "bajale", "menos volumen", "mas bajo") -> {
                repeat(3) { MusicaManager.volumen(false) }; null
            }

            // ---------- Moto y ubicación ----------
            t.tiene("donde") && t.tiene("moto") -> dondeEstaLaMoto()
            t.tiene("guarda", "estacione", "aqui deje") && t.tiene("moto", "lugar", "aqui") -> {
                if (Estacionamiento.guardarAqui()) "Listo, guardé dónde quedó la moto" else "Todavía no tengo tu ubicación"
            }
            t.tiene("comparte", "manda") && t.tiene("ubicacion") -> {
                if (Acciones.compartirUbicacion(ctx)) null else "Todavía no tengo tu ubicación"
            }

            // ---------- Datos ----------
            t.tiene("clima", "tiempo hace", "va a llover", "lluvia", "temperatura afuera") -> { ClimaManager.decir(); null }
            t.tiene("limite") -> textoLimite()
            t.tiene("velocidad", "que tan rapido") -> textoVelocidad()
            t.tiene("bateria", "pila") -> textoBateria()
            t.tiene("gasolina", "tanque", "autonomia") -> textoGasolina()
            t.tiene("que hora", "la hora") -> "Son las " + SimpleDateFormat("H:mm", Locale.getDefault()).format(Date())

            // Lo demás, a Google
            else -> { Acciones.buscarEnGoogle(ctx, original); null }
        }
        if (respuesta != null) Voz.decir(respuesta)
    }

    private fun dondeEstaLaMoto(): String {
        val l = Estacionamiento.lugar.value ?: return "No tengo guardado dónde quedó la moto"
        val yo = UbicacionManager.posicionActual() ?: return "La moto está guardada, pero no tengo tu ubicación"
        val kx = 111_320.0 * kotlin.math.cos(yo.first * Math.PI / 180)
        val dx = (l.lon - yo.second) * kx
        val dy = (l.lat - yo.first) * 110_540.0
        val m = kotlin.math.sqrt(dx * dx + dy * dy)
        return if (m < 1000) "Tu moto está a ${m.roundToInt()} metros" else "Tu moto está a ${String.format(Locale.US, "%.1f", m / 1000)} kilómetros"
    }

    private fun textoLimite(): String {
        val l = LimitesManager.limite.value ?: return "No sé el límite de esta calle"
        return (if (l.estimado) "El límite aproximado es de " else "El límite es de ") + "${l.kmh} kilómetros por hora"
    }

    private fun textoVelocidad(): String {
        val r = Moto.rapida.value
        val v = if (r.vel >= 0) r.vel else UbicacionManager.velocidadKmh()
        return if (v < 0) "No tengo la velocidad" else "Vas a ${v.roundToInt()} kilómetros por hora"
    }

    private fun textoBateria(): String {
        val partes = mutableListOf<String>()
        val r = Moto.rapida.value
        if (r.vMoto >= 0) partes += "La batería de la moto tiene ${String.format(Locale.US, "%.1f", r.vMoto)} volts"
        AudioSalida.dispositivos.value.filter { it.bateria >= 0 }.forEach { partes += "${it.nombre} tiene ${it.bateria} por ciento" }
        return if (partes.isEmpty()) "No tengo datos de batería" else partes.joinToString(". ")
    }

    private fun textoGasolina(): String {
        val r = Moto.rapida.value
        val v = Moto.viaje.value
        if (r.gas < 0) return "No tengo el nivel de gasolina"
        return "Tienes ${r.gas} por ciento de gasolina" + if (v.autonomia >= 0) ", alcanza para unos ${v.autonomia} kilómetros" else ""
    }
}
