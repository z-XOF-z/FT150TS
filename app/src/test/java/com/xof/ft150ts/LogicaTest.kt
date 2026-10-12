package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de lógica pura: resumen de rodada por voz y distancias (baches, reposo, sueño). */
class LogicaTest {

    private fun rodada(km: Float, minutos: Int, vmax: Float) =
        Rodada(id = 0L, fin = 0L, km = km, minutos = minutos, minMov = minutos, vmax = vmax, vprom = 0f, inclMax = 0f, puntos = 10)

    @Test
    fun resumenDeRodadaCorta() {
        assertEquals("Rodada terminada: 12.4 kilómetros en 25 minutos, máxima de 78.", Rodadas.fraseResumen(rodada(12.4f, 25, 78.6f)))
    }

    @Test
    fun resumenSinDecimalCero() {
        assertEquals("Rodada terminada: 10 kilómetros en 18 minutos, máxima de 60.", Rodadas.fraseResumen(rodada(10.0f, 18, 60f)))
    }

    @Test
    fun resumenDeMasDeUnaHora() {
        assertEquals("Rodada terminada: 85.2 kilómetros en 2 horas 5 minutos, máxima de 95.",
            Rodadas.fraseResumen(rodada(85.2f, 125, 95f)))
        assertEquals("Rodada terminada: 40.5 kilómetros en 1 hora 10 minutos, máxima de 80.",
            Rodadas.fraseResumen(rodada(40.5f, 70, 80f)))
    }

    @Test
    fun distanciaConocidaEnTepic() {
        // ~111 m por milésima de grado de latitud
        val d = Baches.distanciaM(21.5000, -104.8900, 21.5010, -104.8900)
        assertTrue("fue $d", d in 110.0..112.5)
    }

    @Test
    fun distanciaCeroEnElMismoPunto() {
        assertEquals(0.0, Baches.distanciaM(21.5, -104.89, 21.5, -104.89), 1e-6)
    }
}
