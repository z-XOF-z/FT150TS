package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumenTest {
    private fun nuevo() = VolumenProgresivo(15).apply { ajustes = AjustesVolumen(velIni = 20f, velMax = 80f, volMin = 40, volMax = 100) }

    @Test
    fun nivelSegunVelocidad() {
        val v = nuevo()
        assertEquals(6, v.nivelPara(0f))      // 40 % de 15
        assertEquals(6, v.nivelPara(20f))
        assertEquals(11, v.nivelPara(50f))    // 70 %
        assertEquals(15, v.nivelPara(80f))
        assertEquals(15, v.nivelPara(130f))
    }

    @Test
    fun subeDeUnoEnUnoConPausa() {
        val v = nuevo()
        var nivel = 6
        var t = 10_000L
        // 80 km/h: la meta es 15, pero solo sube un nivel por paso y cada 1.5 s
        nivel = v.paso(t, 80f, nivel, 6) ?: nivel
        assertEquals(7, nivel)
        assertNull(v.paso(t + 500, 80f, nivel, 6))
        t += 1_500
        nivel = v.paso(t, 80f, nivel, 6) ?: nivel
        assertEquals(8, nivel)
    }

    @Test
    fun histeresisIgnoraAltibajosDelGps() {
        val v = nuevo()
        var nivel = 11
        nivel = v.paso(10_000, 50f, nivel, 11) ?: nivel          // ya está en su nivel
        assertEquals(11, nivel)
        // 52 y 47 km/h: menos de 4 km/h de diferencia → no se mueve
        assertNull(v.paso(20_000, 52f, nivel, 11))
        assertNull(v.paso(30_000, 47f, nivel, 11))
    }

    @Test
    fun alDetenerseRegresaATuVolumen() {
        val v = nuevo().apply { ajustes = ajustes.copy(alDetenerse = AlDetenerse.ORIGINAL) }
        var nivel = 12
        assertNull(v.paso(10_000, 0f, nivel, 9))                 // recién parado: espera
        nivel = v.paso(20_000, 0f, nivel, 9) ?: nivel             // después de 8 s: baja hacia el 9
        assertEquals(11, nivel)
    }

    @Test
    fun sinVelocidadConfiableNoToca() {
        assertNull(nuevo().paso(10_000, -1f, 8, 8))
    }
}
