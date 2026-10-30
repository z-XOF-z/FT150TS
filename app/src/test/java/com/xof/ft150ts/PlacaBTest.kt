package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placa B: banderas nuevas de la telemetría, relevadores, IA de vibraciones y luz del teléfono. */
class PlacaBTest {

    private fun paquete(banderas: Int): ByteArray = ByteArray(18).also {
        it[0] = 0xB1.toByte(); it[3] = 0xFF.toByte(); it[4] = 0xFF.toByte(); it[17] = banderas.toByte()
    }

    @Test
    fun banderasDeLaTelemetriaBinaria() {
        val r = Protocolo.parseRapidaBin(paquete(4 or 16 or 32))!!
        assertTrue(r.freno && r.dirOlvidada && r.vibraAlerta)
        assertFalse(r.direccional || r.ignicion || r.motor)
        assertTrue(Protocolo.parseRapidaBin(paquete(8))!!.direccional)
    }

    @Test
    fun banderasDelJson() {
        val r = Protocolo.parseRapida(org.json.JSONObject("{\"k\":\"t\",\"fr\":1,\"di\":1,\"do\":0,\"va\":1}"))
        assertTrue(r.freno && r.direccional && r.vibraAlerta)
        assertFalse(r.dirOlvidada)
        // Un firmware viejo no manda esos campos: todo apagado
        val viejo = Protocolo.parseRapida(org.json.JSONObject("{\"k\":\"t\",\"v\":10}"))
        assertFalse(viejo.freno || viejo.direccional || viejo.dirOlvidada || viejo.vibraAlerta)
    }

    @Test
    fun relevadoresYVibra() {
        assertEquals(listOf(true, false, true), Protocolo.parseReles("{\"rl\":[1,0,1]}\n"))
        assertNull(Protocolo.parseReles("No entendi"))
        val v = Protocolo.parseVibra("{\"vb\":1,\"vc\":0,\"vm\":5,\"vn\":1234,\"vs\":3.80,\"va\":1}")!!
        assertTrue(v.activa && v.alerta)
        assertEquals(5, v.maduros)
        assertEquals(3.8f, v.score, 0.001f)
        assertNull(Protocolo.parseVibra("{\"rl\":[0,0,0]}"))
    }

    @Test
    fun configDeLaPlacaB() {
        val c = Protocolo.parseConfig("{\"fw\":\"v1.6\",\"placa\":\"B\",\"r2\":1,\"r3\":0,\"so\":1,\"alm\":1,\"as\":4,\"ar\":3,\"aok\":1,\"vb\":1,\"vc\":1,\"vm\":7}")!!
        assertTrue(c.placaB && c.alarma && c.alarmaPosible && c.vibra && c.vibraCaptura)
        assertEquals(listOf(true, false, true), c.reles)
        assertEquals(4, c.alarmaSens)
        assertEquals(3, c.alarmaSirena)
        assertEquals(7, c.vibraMaduros)
        assertFalse(Protocolo.parseConfig("{\"fw\":\"v1.5\"}")!!.placaB)
    }

    @Test
    fun luzDelTelefono() {
        assertEquals(-1, PlacaB.luzPorciento(-1f))
        assertEquals(0, PlacaB.luzPorciento(0f))
        assertEquals(100, PlacaB.luzPorciento(10_000f))
        assertEquals(100, PlacaB.luzPorciento(80_000f))
        assertEquals(50, PlacaB.luzPorciento(99f))      // log10(100)/4 = 0.5
    }
}
