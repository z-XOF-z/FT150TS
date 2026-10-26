package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicaRendimientoTest {
    private fun nivel(
        activo: Boolean = true, root: Boolean = true, pantalla: Boolean = true, reposo: Boolean = false,
        calor: NivelCalor = NivelCalor.NORMAL, carplay: Boolean = false,
    ) = ModoRendimientoLogica.nivel(activo, root, pantalla, reposo, calor, carplay)

    @Test fun tableroEnPantallaEmpuja() = assertEquals(NivelRendimiento.TABLERO, nivel())

    @Test fun conCarPlaySubeLaGpu() = assertEquals(NivelRendimiento.CARPLAY, nivel(carplay = true))

    @Test fun tibioSigueEmpujando() = assertEquals(NivelRendimiento.TABLERO, nivel(calor = NivelCalor.TIBIO))

    @Test fun nadaDeEmpujeSiAlgoLoImpide() {
        assertEquals(NivelRendimiento.NORMAL, nivel(activo = false, carplay = true))
        assertEquals(NivelRendimiento.NORMAL, nivel(root = false))
        assertEquals(NivelRendimiento.NORMAL, nivel(pantalla = false, carplay = true))
        assertEquals(NivelRendimiento.NORMAL, nivel(reposo = true))
        assertEquals(NivelRendimiento.NORMAL, nivel(calor = NivelCalor.CALIENTE, carplay = true))
        assertEquals(NivelRendimiento.NORMAL, nivel(calor = NivelCalor.MUY_CALIENTE))
    }

    @Test fun frecuenciasDelPocoF4() {
        // Tal cual las da el kernel del POCO F4 (Adreno 650)
        val g = ModoRendimientoLogica.frecuenciasGpu("670000000 587000000 525000000 490000000 441600000 400000000 305000000 \n")
        assertEquals(305_000_000L to 525_000_000L, g)
    }

    @Test fun frecuenciasRaras() {
        assertNull(ModoRendimientoLogica.frecuenciasGpu(""))
        assertNull(ModoRendimientoLogica.frecuenciasGpu("Permission denied"))
        assertNull(ModoRendimientoLogica.frecuenciasGpu("500000000"))
        // Solo números razonables: nada de texto que se cuele en un comando
        assertNull(ModoRendimientoLogica.frecuenciasGpu("800000000; rm 300000000 x"))
        assertEquals(300_000_000L to 800_000_000L, ModoRendimientoLogica.frecuenciasGpu("800000000 x 300000000 9"))
    }

    @Test fun comandosPorNivel() {
        val g = 305_000_000L to 525_000_000L
        assertEquals(listOf("echo 0 > /proc/sys/kernel/sched_boost", "echo 305000000 > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq"),
            ModoRendimientoLogica.comandos(NivelRendimiento.NORMAL, g))
        assertEquals(listOf("echo 1 > /proc/sys/kernel/sched_boost", "echo 305000000 > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq"),
            ModoRendimientoLogica.comandos(NivelRendimiento.TABLERO, g))
        assertEquals(listOf("echo 1 > /proc/sys/kernel/sched_boost", "echo 525000000 > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq"),
            ModoRendimientoLogica.comandos(NivelRendimiento.CARPLAY, g))
        // Sin control de la GPU solo se toca el planificador
        assertEquals(listOf("echo 1 > /proc/sys/kernel/sched_boost"), ModoRendimientoLogica.comandos(NivelRendimiento.CARPLAY, null))
    }

    @Test fun guardianRegresaTodoAlCerrarse() {
        val c = ModoRendimientoLogica.cmdGuardian(12869, 305_000_000L to 525_000_000L)
        assertTrue(c.startsWith("setsid sh -c 'while [ -d /proc/12869 ]"))
        assertTrue(c.contains("echo 0 > /proc/sys/kernel/sched_boost; echo 305000000 >"))
        assertFalse(c.contains("525000000"))
    }

    @Test(expected = IllegalArgumentException::class) fun guardianPidInvalido() {
        ModoRendimientoLogica.cmdGuardian(0, null)
    }

    @Test fun esquinasDeCarPlay() {
        val r = intArrayOf(0, 178, 2400, 1080)
        assertTrue(ProtocoloCarPlay.cercaDeEsquina(30, 200, r, 120))      // arriba a la izquierda
        assertTrue(ProtocoloCarPlay.cercaDeEsquina(2350, 1050, r, 120))   // abajo a la derecha
        assertFalse(ProtocoloCarPlay.cercaDeEsquina(1200, 600, r, 120))   // en medio del mapa
        assertFalse(ProtocoloCarPlay.cercaDeEsquina(30, 600, r, 120))     // orilla, pero no esquina
    }
}
