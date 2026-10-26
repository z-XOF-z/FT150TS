package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncendidoTest {
    @Test fun saludoCompleto() {
        assertEquals("Buenas tardes. 27 grados, puede llover en unas 2 horas. Te quedan unos 40 kilómetros de gasolina.",
            Encendido.frase(15, 27, 2, 40))
        assertEquals("Buenos días. 18 grados.", Encendido.frase(7, 18, -1, 200))
        assertEquals("Buenas noches. 20 grados y está lloviendo, maneja con cuidado.", Encendido.frase(22, 20, 0, -1))
        assertEquals("Buenas noches.", Encendido.frase(2, null, 5, -1))          // sin clima no inventa nada
        assertEquals("Buenos días. 24 grados, puede llover en una hora.", Encendido.frase(9, 24, 1, -1))
    }

    @Test fun musicaSoloLoQueLaAppPauso() {
        val t = 10_000_000L
        assertFalse(Encendido.debeReanudar(0L, t))                                  // no la pausó la app
        assertTrue(Encendido.debeReanudar(t, t + 5 * 60_000))                         // gasolina: 5 min
        assertFalse(Encendido.debeReanudar(t, t + Encendido.VENTANA_MUSICA_MS + 1))    // llegaste a tu casa
    }

    @Test fun saludoSoloDespuesDeUnRato() {
        val t = 50_000_000L
        assertTrue(Encendido.tocaSaludo(0L, t))                                      // primera vez
        assertFalse(Encendido.tocaSaludo(t, t + 60_000))                             // se apagó en el semáforo
        assertTrue(Encendido.tocaSaludo(t, t + Encendido.APAGADA_PARA_SALUDO_MS))
    }
}
