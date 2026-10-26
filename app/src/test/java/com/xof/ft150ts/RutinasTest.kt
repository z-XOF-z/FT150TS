package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RutinasTest {
    // Dos puntos de ejemplo a ~5 km (no son lugares reales)
    private val casa = 21.5000 to -104.9000
    private val escuela = 21.5400 to -104.8800

    /** Dos semanas: lunes sale 6:35 (entra 7:00), martes a viernes 7:25 (entra 7:50); regresa 14:15 todos. */
    private fun dosSemanas(): List<ViajeRutina> {
        val v = ArrayList<ViajeRutina>()
        repeat(2) { semana ->
            for (dia in 1..5) {
                val salida = if (dia == 1) 6 * 60 + 35 else 7 * 60 + 25
                val ruido = semana * 0.0003   // no siempre se estaciona en el mismo metro
                v.add(ViajeRutina(dia, salida + semana * 2, 17 + semana, casa.first + ruido, casa.second, escuela.first + ruido, escuela.second))
                v.add(ViajeRutina(dia, 14 * 60 + 15, 19, escuela.first, escuela.second + ruido, casa.first, casa.second - ruido))
            }
        }
        return v
    }

    @Test
    fun aprendeCasaYEscuela() {
        val lugares = RutinasLogica.agruparLugares(dosSemanas())
        assertEquals(2, lugares.size)
        val c = lugares.first { it.nombre == "Casa" }
        val e = lugares.first { it.nombre == "Escuela" }
        assertTrue(RutinasLogica.distanciaM(c.lat, c.lon, casa.first, casa.second) < 100)
        assertTrue(RutinasLogica.distanciaM(e.lat, e.lon, escuela.first, escuela.second) < 100)
    }

    @Test
    fun patronesPorDia() {
        val viajes = dosSemanas()
        val lugares = RutinasLogica.agruparLugares(viajes)
        val p = RutinasLogica.patrones(viajes, lugares)
        // 5 días × (ida + vuelta)
        assertEquals(10, p.size)
        val lunesIda = p.first { it.dia == 1 && it.salidaMin < 12 * 60 }
        assertEquals(6 * 60 + 37, lunesIda.salidaMin)   // mediana de 6:35 y 6:37
        assertEquals(18, lunesIda.duracionMin)
    }

    @Test
    fun prediceLlegadaContraElHorario() {
        val viajes = dosSemanas()
        val lugares = RutinasLogica.agruparLugares(viajes)
        val p = RutinasLogica.patrones(viajes, lugares)
        // Martes 7:10 en casa, parado: sale a su hora típica (7:27) + 18 min = 7:45 → 5 min antes de las 7:50
        val pr = RutinasLogica.predecir(2, 7 * 60 + 10, casa.first, casa.second, false, lugares, p, RutinasLogica.HORARIO_ESCUELA)
        assertNotNull(pr)
        assertEquals("Escuela", pr!!.destino.nombre)
        assertEquals(7 * 60 + 45, pr.llegadaMin)
        assertEquals(5, pr.margenMin)
        // Martes 7:40 ya rodando: 7:40 + 18 = 7:58 → 8 min tarde
        val tarde = RutinasLogica.predecir(2, 7 * 60 + 40, null, null, true, lugares, p, RutinasLogica.HORARIO_ESCUELA)
        assertEquals(-8, tarde!!.margenMin)
        // De regreso a casa no hay horario de entrada
        val vuelta = RutinasLogica.predecir(3, 14 * 60 + 12, escuela.first, escuela.second, false, lugares, p, RutinasLogica.HORARIO_ESCUELA)
        assertEquals("Casa", vuelta!!.destino.nombre)
        assertNull(vuelta.margenMin)
    }

    @Test
    fun fueraDeHoraOFinDeSemanaNoPredice() {
        val viajes = dosSemanas()
        val lugares = RutinasLogica.agruparLugares(viajes)
        val p = RutinasLogica.patrones(viajes, lugares)
        assertNull(RutinasLogica.predecir(2, 11 * 60, casa.first, casa.second, false, lugares, p, RutinasLogica.HORARIO_ESCUELA))
        assertNull(RutinasLogica.predecir(6, 7 * 60 + 20, casa.first, casa.second, false, lugares, p, RutinasLogica.HORARIO_ESCUELA))
    }

    @Test
    fun entreSemanaAprendeConDiasDistintos() {
        // Una sola semana: martes y jueves sale 7:25 a la escuela. Por día no hay 2 veces, pero entre semana sí
        val v = listOf(
            ViajeRutina(2, 7 * 60 + 25, 18, casa.first, casa.second, escuela.first, escuela.second),
            ViajeRutina(4, 7 * 60 + 27, 17, casa.first, casa.second, escuela.first, escuela.second),
            ViajeRutina(2, 14 * 60 + 15, 19, escuela.first, escuela.second, casa.first, casa.second),
            ViajeRutina(4, 14 * 60 + 16, 19, escuela.first, escuela.second, casa.first, casa.second),
        )
        val lugares = RutinasLogica.agruparLugares(v)
        val p = RutinasLogica.patrones(v, lugares)
        assertTrue(p.any { it.dia == 0 })
        // El viernes (sin viajes propios) ya predice la escuela
        val pr = RutinasLogica.predecir(5, 7 * 60 + 10, casa.first, casa.second, false, lugares, p, RutinasLogica.HORARIO_ESCUELA)
        assertEquals("Escuela", pr!!.destino.nombre)
    }

    @Test
    fun conUnSoloViajeNoInventaRutina() {
        val uno = dosSemanas().take(1)
        assertTrue(RutinasLogica.patrones(uno, RutinasLogica.agruparLugares(uno)).isEmpty())
    }
}
