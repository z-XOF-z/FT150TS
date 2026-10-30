package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La pantalla no se queda prendida en la casa (cajón de apps o sin tocarla), pero en la moto sí. */
class PantallaEncendidaTest {
    private val L = PantallaEncendidaLogica
    private val S = PantallaEncendida.SIEMPRE
    private val diezMin = PantallaEncendidaLogica.SIN_TOCAR_MS

    @Test
    fun siempreEnElCajonDeAppsFueraDeLaMotoSeApaga() {
        assertFalse(L.mantener(S, enLaMoto = false, pagina = PAGINA_APPS, sinTocarMs = 0, apagadaPorSueno = false))
        assertNull(L.revisarEn(S, false, PAGINA_APPS, 0, false))
    }

    @Test
    fun siempreEnElTableroFueraDeLaMotoAguantaDiezMinutosSinTocar() {
        assertTrue(L.mantener(S, false, PAGINA_TABLERO, 0, false))
        assertEquals(diezMin - 60_000, L.revisarEn(S, false, PAGINA_TABLERO, 60_000, false))
        assertFalse(L.mantener(S, false, PAGINA_TABLERO, diezMin, false))
        assertNull(L.revisarEn(S, false, PAGINA_TABLERO, diezMin, false))
    }

    @Test
    fun enLaMotoSiempreSeQuedaPrendidaAunqueNoLaToques() {
        assertTrue(L.mantener(S, true, PAGINA_TABLERO, 3 * diezMin, false))
        assertTrue(L.mantener(S, true, PAGINA_APPS, 3 * diezMin, false))
        assertTrue(L.mantener(PantallaEncendida.CON_MOTO, true, PAGINA_APPS, 3 * diezMin, false))
        assertNull(L.revisarEn(S, true, PAGINA_TABLERO, 0, false))
    }

    @Test
    fun conMotoYNormal() {
        assertFalse(L.mantener(PantallaEncendida.CON_MOTO, false, PAGINA_TABLERO, 0, false))
        assertFalse(L.mantener(PantallaEncendida.NORMAL, true, PAGINA_TABLERO, 0, false))
    }

    @Test
    fun carPlayEscondidoSeCierraFueraDeLaMotoSinUso() {
        val quince = 15_000L
        // En el cajón, sin tocar: espera el apagado del teléfono, pero nunca menos de 30 s
        assertFalse(L.cerrarCarPlay(false, escondida = true, ftVisible = true, sinTocarMs = 20_000, apagadoSistemaMs = quince))
        assertTrue(L.cerrarCarPlay(false, true, true, 30_000, quince))
        assertFalse(L.cerrarCarPlay(false, true, true, 60_000, apagadoSistemaMs = 120_000))
        // En otra app o con la pantalla apagada: al minuto
        assertFalse(L.cerrarCarPlay(false, true, ftVisible = false, sinTocarMs = 59_000, apagadoSistemaMs = quince))
        assertTrue(L.cerrarCarPlay(false, true, false, 60_000, quince))
    }

    @Test
    fun carPlayEnLaMotoOVisibleNoSeCierra() {
        assertFalse(L.cerrarCarPlay(enLaMoto = true, escondida = true, ftVisible = false, sinTocarMs = 3_600_000, apagadoSistemaMs = 15_000))
        assertFalse(L.cerrarCarPlay(false, escondida = false, ftVisible = true, sinTocarMs = 3_600_000, apagadoSistemaMs = 15_000))
    }

    @Test
    fun suenoYReposoMandan() {
        assertFalse(L.mantener(S, true, PAGINA_TABLERO, 0, apagadaPorSueno = true))
        assertNull(L.revisarEn(S, false, PAGINA_TABLERO, 0, true))
    }
}
