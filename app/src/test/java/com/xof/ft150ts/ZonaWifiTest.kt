package com.xof.ft150ts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZonaWifiTest {
    @Test
    fun apagaSoloConElSwitchCerradoYSiEsNuestra() {
        // Switch cerrado y la prendió la app → se apaga
        assertTrue(ZonaWifi.debeApagar(switchAbierto = false, soloLaMia = true, laPrendiYo = true))
        // Lo volviste a abrir dentro de la espera → se queda
        assertFalse(ZonaWifi.debeApagar(switchAbierto = true, soloLaMia = true, laPrendiYo = true))
        // La prendiste tú a mano y "solo la mía" está activo → no se toca
        assertFalse(ZonaWifi.debeApagar(switchAbierto = false, soloLaMia = true, laPrendiYo = false))
        // Sin "solo la mía": se apaga aunque la hayas prendido tú
        assertTrue(ZonaWifi.debeApagar(switchAbierto = false, soloLaMia = false, laPrendiYo = false))
    }
}
