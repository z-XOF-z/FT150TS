package com.xof.ft150ts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeguridadVelTest {
    @Test fun bajoTechoNoSeLeCree() {
        // Lo que se vio en el POCO bajo dos pisos: 16 satélites "en uso" pero señal débil
        assertFalse(SeguridadVel.confiable(true, 10.5f, true, 0.5f, senalDebil = true))
        assertFalse(SeguridadVel.confiable(true, 40f, true, 0.5f, senalDebil = false))
        assertFalse(SeguridadVel.confiable(true, 8f, true, 4f, senalDebil = false))
    }

    @Test fun afueraSiSeLeCree() {
        assertTrue(SeguridadVel.confiable(true, 5f, true, 0.4f, senalDebil = false))
        assertTrue(SeguridadVel.confiable(false, 0f, false, 0f, senalDebil = false))
    }
}
