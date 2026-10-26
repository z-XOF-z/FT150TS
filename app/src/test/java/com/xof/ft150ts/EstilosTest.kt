package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstilosTest {
    @Test fun cadaEstiloTieneSuTema() {
        Estilos.TODOS.forEach { e ->
            assertNotNull("Falta TemaApp.${e.id}", TemaApp.entries.firstOrNull { it.name == e.id })
            assertEquals(e, Estilos.porId(e.id))
        }
        assertEquals(Estilos.TODOS.size, Estilos.TODOS.map { it.id }.toSet().size)
    }

    @Test fun elAcentoSeLeeSobreLaTarjeta() {
        // Superficies translúcidas (Aurora) se ven sobre negro
        Estilos.TODOS.forEach { e ->
            val sup = if ((e.superficie ushr 24) < 0x80) 0xFF1A1A1AL else e.superficie
            assertTrue("${e.nombre}: contraste bajo", Estilos.contraste(e.acento, sup) >= 3.0)
        }
        for (s in 0L until 500L) {
            val e = Estilos.mezcla(s)
            assertTrue("Mezcla $s", Estilos.contraste(e.acento, e.superficie) >= 3.0)
        }
    }

    @Test fun laMezclaSeRepiteConLaMismaSemilla() {
        assertEquals(Estilos.mezcla(42), Estilos.mezcla(42))
        val distintas = (0L until 200L).map { Estilos.mezcla(it).copy(nombre = "", detalle = "") }.toSet()
        assertTrue("Muy pocas combinaciones: ${distintas.size}", distintas.size > 150)
        assertNotEquals(Estilos.mezcla(1), Estilos.mezcla(2))
    }

    @Test fun laMezclaSiempreMarcaLaTarjeta() {
        for (s in 0L until 1000L) {
            val e = Estilos.mezcla(s)
            assertTrue(!(e.bordeDp == 0f && e.deco in setOf(DecoEstilo.NADA, DecoEstilo.PUNTEADO)))
            if (e.forma == FormaEstilo.RECTA) assertEquals(0f, e.radio)
        }
    }
}

class ArranqueTest {
    @Test fun alAzarEligeUnaDeVerdad() {
        val n = TIPOS_ARRANQUE.size - 1
        for (i in 0 until n) assertEquals(i, com.xof.ft150ts.ui.tipoParaCorrer(i, 7))
        for (a in 0 until 50) assertTrue(com.xof.ft150ts.ui.tipoParaCorrer(n, a) in 0 until n)
    }

    @Test fun lasRpmSubenYRegresan() {
        assertEquals(1234f, com.xof.ft150ts.ui.rpmArranque(-1f, 1234f))
        assertEquals(10_000f, com.xof.ft150ts.ui.rpmArranque(0.5f, 1234f))
        assertEquals(1234f, com.xof.ft150ts.ui.rpmArranque(0.9999f, 1234f), 5f)
    }
}
