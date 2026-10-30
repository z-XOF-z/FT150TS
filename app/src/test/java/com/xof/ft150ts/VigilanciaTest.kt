package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Antirrobo del teléfono, estado de la moto y voltajes. */
class VigilanciaTest {
    private val L = VigilanciaLogica

    @Test
    fun seArmaSoloSiNadieLoMueve() {
        var s = L.armar(0)
        s = L.tick(s, 60_000).estado
        assertEquals(EstadoVigilancia.POR_ARMAR, s.e)
        s = L.movimiento(s, 100_000).estado          // todavía lo traes tú: vuelve a contar
        assertEquals(EstadoVigilancia.POR_ARMAR, L.tick(s, 200_000).estado.e)
        assertEquals(EstadoVigilancia.ARMADA, L.tick(s, 220_000).estado.e)
    }

    @Test
    fun sospechaYDespuesAlarmaConEnvio() {
        var s = L.tick(L.armar(0), L.ARMAR_MS).estado
        s = L.movimiento(s, 300_000).estado
        assertEquals(EstadoVigilancia.SOSPECHA, s.e)
        assertFalse(L.tick(s, 310_000).enviar)       // 20 s para que seas tú
        val p = L.tick(s, 320_000)
        assertTrue(p.enviar)
        assertEquals(EstadoVigilancia.ALARMA, p.estado.e)
        assertEquals(1, p.estado.envios)
    }

    @Test
    fun repiteCadaDosMinutosMaximoTres() {
        var s = VigilanciaLogica.Estado(EstadoVigilancia.ALARMA, 0, 1, 0)
        assertFalse(L.movimiento(s, 60_000).enviar)
        var p = L.movimiento(s, 120_000); assertTrue(p.enviar); s = p.estado
        p = L.movimiento(s, 240_000); assertTrue(p.enviar); s = p.estado
        assertEquals(3, s.envios)
        assertFalse(L.movimiento(s, 400_000).enviar)
    }

    @Test
    fun elDuenoDesarmaTodo() {
        assertEquals(EstadoVigilancia.APAGADA, L.dueno().e)
        assertFalse(L.movimiento(L.dueno(), 1000).enviar)
        assertTrue(L.seMovio(2f, 2))
        assertFalse(L.seMovio(2f, 1))
        assertFalse(L.seMovio(0.5f, 5))
    }

    @Test
    fun estadoDeLaMoto() {
        val l = Protocolo.parseSalud("{\"salud\":[{\"c\":\"Batería\",\"p\":85,\"t\":\"Bien.\"},{\"c\":\"Cadena\",\"p\":30,\"t\":\"Ya toca lubricarla.\"}]}")!!
        assertEquals(2, l.size)
        assertEquals(ComponenteSalud("Cadena", 30, "Ya toca lubricarla."), l[1])
        assertEquals(null, Protocolo.parseSalud("No entendi"))
    }

    @Test
    fun voltajes() {
        assertEquals(null, Voltajes.resumen(listOf(-1f, -1f)))
        val (mn, mx, pr) = Voltajes.resumen(listOf(12.4f, -1f, 13.8f, 14.0f))!!
        assertEquals(12.4f, mn); assertEquals(14.0f, mx); assertEquals(13.4f, pr, 0.01f)
    }
}
