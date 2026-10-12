package com.xof.ft150ts

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Pruebas de la validación del respaldo: lo legítimo pasa; lo peligroso o dañado no se escribe. */
class RespaldoTest {

    private fun par(tipo: String, v: Any) = JSONArray().put(tipo).put(v)

    private fun respaldo(prefs: JSONObject, archivos: JSONObject = JSONObject(), app: String = "FT150TS") =
        JSONObject().put("app", app).put("version", "3.0").put("fecha", 1L).put("prefs", prefs).put("archivos", archivos).toString()

    @Test
    fun respaldoLegitimoSeLeeCompleto() {
        val prefs = JSONObject()
            .put("apariencia", JSONObject().put("tema", par("s", "VERTICE")).put("neon", par("b", true)).put("acento", par("i", 3)))
            .put("rodadas", JSONObject().put("km", par("f", 12.5)).put("desde", par("l", 1790000000000L)))
        val archivos = JSONObject().put("rodadas/1790000000000.json", "{\"p\":[]}")
        val (ajustes, rodadas) = Respaldo.revisarRespaldo(respaldo(prefs, archivos))
        assertEquals(setOf("apariencia", "rodadas"), ajustes.keys)
        assertEquals(Respaldo.Valor.S("VERTICE"), ajustes["apariencia"]!!["tema"])
        assertEquals(Respaldo.Valor.B(true), ajustes["apariencia"]!!["neon"])
        assertEquals(Respaldo.Valor.I(3), ajustes["apariencia"]!!["acento"])
        assertEquals(setOf("1790000000000.json"), rodadas.keys)
    }

    @Test
    fun losAjustesDeRootNoSeRestauran() {
        // Un respaldo ajeno no debe poder marcar el root como concedido ni prender comandos root
        val prefs = JSONObject()
            .put("root", JSONObject().put("concedido", par("b", true)).put("gestos", par("b", true)))
            .put("apariencia", JSONObject().put("tema", par("s", "FILO")))
        val (ajustes, _) = Respaldo.revisarRespaldo(respaldo(prefs))
        assertFalse(ajustes.containsKey("root"))
        assertTrue(ajustes.containsKey("apariencia"))
    }

    @Test
    fun nombresDeAjustesRarosSeIgnoran() {
        val prefs = JSONObject()
            .put("../../shared_prefs/otro", JSONObject().put("x", par("b", true)))
            .put("con espacio", JSONObject().put("x", par("b", true)))
            .put("bien_escrito", JSONObject().put("x", par("b", true)))
        val (ajustes, _) = Respaldo.revisarRespaldo(respaldo(prefs))
        assertEquals(setOf("bien_escrito"), ajustes.keys)
    }

    @Test
    fun rutasDeRodadasPeligrosasSeIgnoran() {
        val archivos = JSONObject()
            .put("rodadas/../../databases/x.json", "malo")
            .put("rodadas/sub/1.json", "malo")
            .put("otra/1.json", "malo")
            .put("rodadas/pendiente.json", "malo")
            .put("rodadas/123.json", "bueno")
        val (_, rodadas) = Respaldo.revisarRespaldo(respaldo(JSONObject(), archivos))
        assertEquals(mapOf("123.json" to "bueno"), rodadas)
    }

    @Test
    fun unRespaldoDanadoNoSeEscribeNadaALaMitad() {
        // El segundo valor está mal (un booleano que es texto): todo el respaldo se rechaza antes de escribir
        val prefs = JSONObject()
            .put("apariencia", JSONObject().put("tema", par("s", "FILO")))
            .put("modoMoto", JSONObject().put("detectarCarga", par("b", "no-soy-booleano")))
        try {
            Respaldo.revisarRespaldo(respaldo(prefs))
            fail("Debió rechazar el respaldo dañado")
        } catch (_: Exception) { /* correcto: se rechaza completo */ }
    }

    @Test
    fun unArchivoQueNoEsDeLaAppSeRechaza() {
        try {
            Respaldo.revisarRespaldo(respaldo(JSONObject(), app = "OtraApp"))
            fail("Debió rechazar un archivo de otra app")
        } catch (_: Exception) { }
    }
}
