package com.xof.ft150ts

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvanzadasTest {
    @Test fun estadoDeFabricaTodoApagado() {
        // Lo que responde la tarjeta v1.5 recién flasheada, sin SSR
        val t = """{"av":1,"hw":0,"qsh":0,"lim":0,"lr":9500,"lc":0,"lcr":6000,"qs":0,"qm":60,"qr":4500,"dg":0}"""
        assertTrue(ProtocoloAvanzado.esEstado(t))
        val e = ProtocoloAvanzado.parseEstado(t)!!
        assertFalse(e.hwCorte); assertFalse(e.limitador); assertFalse(e.launch); assertFalse(e.quickshifter); assertFalse(e.diagnostico)
        assertEquals(9500, e.limRpm)
    }

    @Test fun noConfundeConOtrasRespuestas() {
        assertFalse(ProtocoloAvanzado.esEstado("""{"fw":"v1.5","up":30}"""))
        assertFalse(ProtocoloAvanzado.esEstado("No entendi: av"))
        assertNull(ProtocoloAvanzado.parseEstado("{roto"))
    }

    @Test fun comandosConConfirmarYLimites() {
        assertEquals("av lim on 11000 confirmar", ProtocoloAvanzado.cmdLimitador(true, 15000))
        assertEquals("av lim off", ProtocoloAvanzado.cmdLimitador(false, 9500))
        assertEquals("av lc on 3000 confirmar", ProtocoloAvanzado.cmdLaunch(true, 1000))
        assertEquals("av qs on 100 3000 confirmar", ProtocoloAvanzado.cmdQuickshifter(true, 500, 10))
        assertEquals("av diag on", ProtocoloAvanzado.cmdDiagnostico(true))
    }

    @Test fun diagnosticoYVeredictos() {
        val j = JSONObject("""{"k":"d","cf":40,"pu":4000,"ir":30,"ar":[11.2,10.6,9.4],"ad":1,"ca":1,"ra":1450,"cl":0,"cq":0,"lc":0}""")
        val d = ProtocoloAvanzado.parseDiagnostico(j)
        assertEquals(listOf(11.2f, 10.6f, 9.4f), d.arranques)
        assertEquals(10f, ProtocoloAvanzado.faltasPorMil(d), 0.01f)
        assertTrue(ProtocoloAvanzado.veredictoEncendido(d).startsWith("Faltan chispas"))
        assertTrue(ProtocoloAvanzado.veredictoBateria(d).startsWith("Floja"))
        val bien = d.copy(chispasFaltantes = 2, arranques = listOf(10.9f, 10.8f, 10.9f))
        assertTrue(ProtocoloAvanzado.veredictoEncendido(bien).startsWith("Parejo"))
        assertTrue(ProtocoloAvanzado.veredictoBateria(bien).startsWith("Bien"))
        assertTrue(ProtocoloAvanzado.veredictoEncendido(d.copy(pulsos = 100)).startsWith("Juntando"))
    }

    @Test fun subtituloSrt() {
        val r = Rapida(vel = 48.4f, rpm = 6200, marcha = 3, vMoto = 13.62f, incl = 12f)
        val b = DashcamTelemetria.bloque(61, r, 21.5 to -104.9, "14:32:05")
        val l = b.lines()
        assertEquals("62", l[0])
        assertEquals("00:01:01,000 --> 00:01:02,000", l[1])
        assertEquals("48 km/h  ·  6200 rpm  ·  3a  ·  13.6 V  ·  12°", l[2])
        assertEquals("14:32:05  ·  21.50000, -104.90000", l[3])
        assertTrue(b.endsWith("\n\n"))
        // Sin tarjeta ni GPS
        assertEquals("-- km/h  ·  -\n14:32:05", DashcamTelemetria.texto(Rapida(), null, "14:32:05"))
    }
}
