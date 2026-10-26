package com.xof.ft150ts

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Parte pura de las rutinas (sin Android, se prueba en RutinasTest). Con las rodadas guardadas aprende:
 *  - LUGARES: donde terminan (y empiezan) tus rodadas, juntando los puntos a menos de [RADIO_LUGAR_M].
 *  - PATRONES: por día de la semana, hacia qué lugar sales, a qué hora (mediana) y cuánto tardas (mediana).
 * Con eso y el horario (entrada por día) predice: "Escuela · 18 min · llegas 7:42 (8 min antes)".
 * Todo se calcula en el teléfono; nada sale a internet.
 */

/** Un viaje ya terminado. Horas en minutos desde las 00:00; [dia] 1 = lunes … 7 = domingo. */
data class ViajeRutina(
    val dia: Int, val salidaMin: Int, val duracionMin: Int,
    val oLat: Double, val oLon: Double, val dLat: Double, val dLon: Double,
)

/** Un lugar aprendido. [nombre] lo pone la app (Casa / Escuela / Lugar N) o tú. */
data class LugarRutina(val id: Int, val lat: Double, val lon: Double, val llegadas: Int, val salidas: Int, val nombre: String)

/** Lo que haces normalmente un [dia]: salir hacia [destino] a eso de [salidaMin], tardando [duracionMin]. */
data class PatronRutina(val dia: Int, val origen: Int, val destino: Int, val salidaMin: Int, val duracionMin: Int, val veces: Int)

/** Hora de entrada en un lugar (p. ej. la escuela) un [dia]. */
data class EntradaHorario(val dia: Int, val minuto: Int)

/** La predicción para ahorita. [margenMin] > 0 = llegas antes de la entrada; < 0 = vas tarde; null = sin horario. */
data class PrediccionRutina(
    val destino: LugarRutina, val duracionMin: Int, val llegadaMin: Int, val entradaMin: Int?, val margenMin: Int?,
)

object RutinasLogica {
    const val RADIO_LUGAR_M = 180.0
    /** Un patrón cuenta si se repitió al menos tantas veces en ese día de la semana. */
    const val MIN_VECES = 2
    /** La predicción se muestra desde tantos minutos antes de tu salida típica… */
    const val ANTES_MIN = 45
    /** …y hasta tantos después (por si sales tarde). */
    const val DESPUES_MIN = 40

    /** Junta los puntos (origen y destino de cada viaje) en lugares. */
    fun agruparLugares(viajes: List<ViajeRutina>): List<LugarRutina> {
        data class Grupo(var lat: Double, var lon: Double, var n: Int, var llegadas: Int, var salidas: Int)
        val grupos = ArrayList<Grupo>()
        fun agregar(lat: Double, lon: Double, llegada: Boolean) {
            val g = grupos.minByOrNull { distanciaM(it.lat, it.lon, lat, lon) }
            if (g != null && distanciaM(g.lat, g.lon, lat, lon) <= RADIO_LUGAR_M) {
                // centro = promedio de sus puntos
                g.lat = (g.lat * g.n + lat) / (g.n + 1); g.lon = (g.lon * g.n + lon) / (g.n + 1); g.n++
                if (llegada) g.llegadas++ else g.salidas++
            } else grupos.add(Grupo(lat, lon, 1, if (llegada) 1 else 0, if (llegada) 0 else 1))
        }
        for (v in viajes) { agregar(v.oLat, v.oLon, false); agregar(v.dLat, v.dLon, true) }
        val utiles = grupos.filter { it.n >= MIN_VECES }.sortedByDescending { it.n }
        return utiles.mapIndexed { i, g -> LugarRutina(i, g.lat, g.lon, g.llegadas, g.salidas, "") }
            .let { nombrar(it, viajes) }
    }

    /**
     * Nombres automáticos: Casa = el lugar donde más terminas de noche (después de las 18:00) o del que más sales temprano;
     * Escuela = el destino más frecuente entre semana llegando de 6:00 a 9:30 (que no sea Casa); los demás, Lugar N.
     */
    private fun nombrar(lugares: List<LugarRutina>, viajes: List<ViajeRutina>): List<LugarRutina> {
        if (lugares.isEmpty()) return lugares
        fun cerca(lat: Double, lon: Double) = lugares.firstOrNull { distanciaM(it.lat, it.lon, lat, lon) <= RADIO_LUGAR_M }
        val puntosCasa = HashMap<Int, Int>()
        val puntosEscuela = HashMap<Int, Int>()
        for (v in viajes) {
            val llegada = v.salidaMin + v.duracionMin
            if (llegada >= 18 * 60) cerca(v.dLat, v.dLon)?.let { puntosCasa.merge(it.id, 1, Int::plus) }
            if (v.salidaMin < 9 * 60) cerca(v.oLat, v.oLon)?.let { puntosCasa.merge(it.id, 1, Int::plus) }
            if (v.dia in 1..5 && llegada in 6 * 60..(9 * 60 + 30)) cerca(v.dLat, v.dLon)?.let { puntosEscuela.merge(it.id, 1, Int::plus) }
        }
        val casa = puntosCasa.maxByOrNull { it.value }?.key
        val escuela = puntosEscuela.filterKeys { it != casa }.maxByOrNull { it.value }?.takeIf { it.value >= MIN_VECES }?.key
        var n = 0
        return lugares.map {
            it.copy(nombre = when (it.id) { casa -> "Casa"; escuela -> "Escuela"; else -> "Lugar ${++n}" })
        }
    }

    /** Patrones por día de la semana (origen → destino), con la mediana de la hora de salida y de la duración. */
    fun patrones(viajes: List<ViajeRutina>, lugares: List<LugarRutina>): List<PatronRutina> {
        fun lugar(lat: Double, lon: Double) = lugares.firstOrNull { distanciaM(it.lat, it.lon, lat, lon) <= RADIO_LUGAR_M }?.id
        val porClave = viajes.mapNotNull { v ->
            val o = lugar(v.oLat, v.oLon) ?: return@mapNotNull null
            val d = lugar(v.dLat, v.dLon) ?: return@mapNotNull null
            if (o == d) null else Triple(v.dia, o, d) to v
        }.groupBy({ it.first }, { it.second })
        val res = ArrayList<PatronRutina>()
        for ((k, lista) in porClave) {
            // Un mismo día y trayecto puede tener dos horas distintas (p. ej. ida y vuelta al mismo lugar en otro
            // horario): se separan los que salen a más de 90 min de diferencia
            for (grupo in separarPorHora(lista)) {
                if (grupo.size < MIN_VECES) continue
                res.add(PatronRutina(k.first, k.second, k.third, mediana(grupo.map { it.salidaMin }),
                    mediana(grupo.map { it.duracionMin }), grupo.size))
            }
        }
        // Entre semana (dia = 0): el mismo trayecto a la misma hora en días DISTINTOS de lunes a viernes también es
        // rutina. Así aprende en días y no en semanas; solo se agrega si ese día no tiene ya su propia rutina.
        val semana = viajes.filter { it.dia in 1..5 }.mapNotNull { v ->
            val o = lugar(v.oLat, v.oLon) ?: return@mapNotNull null
            val d = lugar(v.dLat, v.dLon) ?: return@mapNotNull null
            if (o == d) null else (o to d) to v
        }.groupBy({ it.first }, { it.second })
        for ((k, lista) in semana) {
            for (grupo in separarPorHora(lista)) {
                if (grupo.size < MIN_VECES || grupo.map { it.dia }.distinct().size < 2) continue
                val salida = mediana(grupo.map { it.salidaMin })
                // Sobra si CADA día del grupo ya tiene su propia rutina de ese trayecto a esa hora
                val yaHay = grupo.map { it.dia }.distinct().all { d ->
                    res.any { it.dia == d && it.origen == k.first && it.destino == k.second && abs(it.salidaMin - salida) <= 90 }
                }
                if (!yaHay) res.add(PatronRutina(0, k.first, k.second, salida, mediana(grupo.map { it.duracionMin }), grupo.size))
            }
        }
        return res.sortedWith(compareBy({ it.dia }, { it.salidaMin }))
    }

    private fun separarPorHora(lista: List<ViajeRutina>): List<List<ViajeRutina>> {
        val orden = lista.sortedBy { it.salidaMin }
        val grupos = ArrayList<ArrayList<ViajeRutina>>()
        for (v in orden) {
            val ult = grupos.lastOrNull()
            if (ult != null && v.salidaMin - ult.last().salidaMin <= 90) ult.add(v) else grupos.add(arrayListOf(v))
        }
        return grupos
    }

    /**
     * Qué toca ahorita: el patrón de hoy cuya salida típica está cerca de [ahoraMin] (desde [ANTES_MIN] antes hasta
     * [DESPUES_MIN] después) y que sale de donde estás ([aquiLat]/[aquiLon], si se sabe). La llegada se calcula como
     * max(ahora, salida típica) + duración; si ya vas rodando ([rodando]), como ahora + duración.
     */
    fun predecir(
        dia: Int, ahoraMin: Int, aquiLat: Double?, aquiLon: Double?, rodando: Boolean,
        lugares: List<LugarRutina>, patrones: List<PatronRutina>, horario: List<EntradaHorario>,
    ): PrediccionRutina? {
        val aqui = if (aquiLat != null && aquiLon != null)
            lugares.firstOrNull { distanciaM(it.lat, it.lon, aquiLat, aquiLon) <= RADIO_LUGAR_M }?.id else null
        val candidatos = patrones.filter { p ->
            (p.dia == dia || (p.dia == 0 && dia in 1..5)) && ahoraMin in (p.salidaMin - ANTES_MIN)..(p.salidaMin + DESPUES_MIN) &&
                (rodando || aqui == null || aqui == p.origen)
        }
        val p = candidatos.minWithOrNull(compareBy({ if (it.dia == 0) 1 else 0 }, { abs(it.salidaMin - ahoraMin) })) ?: return null
        val destino = lugares.firstOrNull { it.id == p.destino } ?: return null
        val llegada = (if (rodando) ahoraMin else maxOf(ahoraMin, p.salidaMin)) + p.duracionMin
        // La entrada que toca: la del día a la que llegarías, solo si el destino es la Escuela
        val entrada = if (destino.nombre == "Escuela") horario.firstOrNull { it.dia == dia }?.minuto else null
        return PrediccionRutina(destino, p.duracionMin, llegada, entrada, entrada?.let { it - llegada })
    }

    /** Horario de la escuela de Andrew (grupo IE-4-2, sep–dic 2026): lunes 7:00, martes a viernes 7:50. */
    val HORARIO_ESCUELA = listOf(EntradaHorario(1, 7 * 60)) + (2..5).map { EntradaHorario(it, 7 * 60 + 50) }

    fun hhmm(min: Int): String { val m = ((min % 1440) + 1440) % 1440; return "%d:%02d".format(m / 60, m % 60) }

    fun mediana(xs: List<Int>): Int { val s = xs.sorted(); return if (s.isEmpty()) 0 else s[s.size / 2] }

    fun distanciaM(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val r = 6_371_000.0
        val dLa = Math.toRadians(la2 - la1); val dLo = Math.toRadians(lo2 - lo1)
        val a = sin(dLa / 2) * sin(dLa / 2) + cos(Math.toRadians(la1)) * cos(Math.toRadians(la2)) * sin(dLo / 2) * sin(dLo / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
