package com.xof.ft150ts

import com.xof.ft150ts.ui.versionFw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placa E y guardián (firmware v1.7): respuestas de "e", "errores" y las banderas nuevas de cfgjson. */
class PlacaETest {

    @Test
    fun estadoDeLaPlacaE() {
        val e = Protocolo.parsePlacaE("{\"e\":{\"hay\":1,\"s\":[1,0,1,0],\"in\":[0,1,0,0],\"bt\":95,\"des\":0,\"corto\":0,\"mot\":\"\"}}")!!
        assertTrue(e.hay)
        assertEquals(listOf(true, false, true, false), e.salidas)
        assertEquals(listOf(false, true, false, false), e.entradas)
        assertEquals(95, e.bombaResta)
        val apagada = Protocolo.parsePlacaE("{\"e\":{\"hay\":0,\"s\":[0,0,0,0],\"in\":[0,0,0,0],\"bt\":-1,\"des\":120,\"corto\":2,\"mot\":\"la apagaste\"}}")!!
        assertFalse(apagada.hay)
        assertEquals(120, apagada.descanso)
        assertEquals(2, apagada.corto)
        assertEquals("la apagaste", apagada.motivo)
        assertNull(Protocolo.parsePlacaE("{\"rl\":[0,0,0]}"))
    }

    @Test
    fun bitacoraDelGuardian() {
        val x = Protocolo.parseErrores("{\"err\":[{\"t\":100,\"c\":1,\"x\":\"viejo\"},{\"t\":200,\"c\":10,\"x\":\"nuevo\"}],\"seguro\":1,\"tc\":52,\"hp\":180}")!!
        assertEquals(listOf("nuevo", "viejo"), x.lista.map { it.texto })   // lo más reciente primero
        assertTrue(x.modoSeguro)
        assertEquals(52, x.tempChip)
        assertEquals(0, Protocolo.parseErrores("{\"err\":[],\"seguro\":0,\"tc\":40,\"hp\":200}")!!.lista.size)
    }

    @Test
    fun banderasDeConfig() {
        val c = Protocolo.parseConfig("{\"fw\":\"v1.7\",\"placa\":\"B\",\"pe\":1,\"seg\":0}")!!
        assertTrue(c.placaE)
        assertFalse(c.modoSeguro)
        assertFalse(Protocolo.parseConfig("{\"fw\":\"v1.6\",\"placa\":\"B\"}")!!.placaE)
    }

    @Test
    fun versionesDelFirmware() {
        assertEquals(107, versionFw("v1.7"))
        assertTrue(versionFw("v1.10") > versionFw("v1.7"))
        assertTrue(versionFw("v1.6") < 107)
        assertEquals(0, versionFw(""))
    }
}
