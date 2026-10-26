package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SOS por caída: solo números de teléfono reales, solo rodando y nunca dos avisos seguidos. */
class EmergenciaTest {

    @Test
    fun numeroSeLimpia() {
        assertEquals("+525512345678", ReglasSos.limpiarNumero(" +52 55 1234 5678 "))
        assertEquals("5512345678", ReglasSos.limpiarNumero("(55) 1234-5678"))
        // Códigos USSD o letras no pasan: se quedan solo los dígitos
        assertEquals("21", ReglasSos.limpiarNumero("*#21#"))
        assertEquals("+123456789012345", ReglasSos.limpiarNumero("+1234567890123456789"))   // máx. 15 dígitos
    }

    @Test
    fun numeroValido() {
        assertTrue(ReglasSos.numeroValido("+525512345678"))
        assertTrue(ReglasSos.numeroValido("5512345678"))
        assertFalse(ReglasSos.numeroValido("911"))            // muy corto: no es un contacto
        assertFalse(ReglasSos.numeroValido("*#06#"))
        assertFalse(ReglasSos.numeroValido(""))
        assertFalse(ReglasSos.numeroValido("55 1234 5678"))  // sin limpiar
    }

    @Test
    fun soloRodandoConNumeroYSinRepetir() {
        val pausa = 5 * 60_000L
        assertTrue(ReglasSos.debeVigilar(rodando = true, hayNumero = true, desdeUltimoMs = pausa, pausaMs = pausa))
        assertFalse(ReglasSos.debeVigilar(rodando = false, hayNumero = true, desdeUltimoMs = pausa, pausaMs = pausa))   // el teléfono se cayó de la mesa
        assertFalse(ReglasSos.debeVigilar(rodando = true, hayNumero = false, desdeUltimoMs = pausa, pausaMs = pausa))
        assertFalse(ReglasSos.debeVigilar(rodando = true, hayNumero = true, desdeUltimoMs = 60_000L, pausaMs = pausa))
    }

    @Test
    fun mensaje() {
        val m = ReglasSos.mensaje("Andrew", Triple(19.432608, -99.133209, 12f), 0L, prueba = false)
        assertTrue(m.contains("Andrew"))
        assertTrue(m.contains("https://maps.google.com/?q=19.432608,-99.133209"))
        assertTrue(m.contains("±12 m"))
        assertFalse(m.contains("PRUEBA"))
        val sin = ReglasSos.mensaje("", null, 0L, prueba = true)
        assertTrue(sin.startsWith("[PRUEBA]"))
        assertTrue(sin.contains("el conductor"))
        assertTrue(sin.contains("Sin ubicación"))
        assertTrue(m.length < 300)                            // cabe en 2 SMS como mucho
    }
}
