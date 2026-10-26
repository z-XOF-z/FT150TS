package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de la actualización: solo GitHub por https, solo versiones más nuevas y solo con la misma firma. */
class ActualizadorTest {
    private val firma = setOf("3efd04bd89e6560d5fa9b34c9dd63e19e59c705de157a9ec382fc3df02e4ab65")

    @Test
    fun comparaVersiones() {
        assertTrue(ReglasActualizacion.esMasNueva("v3.2", "3.1"))
        assertTrue(ReglasActualizacion.esMasNueva("v3.10", "3.9"))      // numérico, no texto
        assertTrue(ReglasActualizacion.esMasNueva("v3.2.1", "3.2"))
        assertFalse(ReglasActualizacion.esMasNueva("v3.2", "3.2"))
        assertFalse(ReglasActualizacion.esMasNueva("v3.0", "3.2"))
        assertFalse(ReglasActualizacion.esMasNueva("v3.2", "3.2.0"))
        assertFalse(ReglasActualizacion.esMasNueva("latest", "3.2"))    // etiqueta rara: no se ofrece
        assertEquals(listOf(3, 2), ReglasActualizacion.numeros("v3.2-beta"))
    }

    @Test
    fun ordenConBetas() {
        assertTrue(ReglasActualizacion.esMasNueva("v3.2-beta.1", "3.1"))
        assertTrue(ReglasActualizacion.esMasNueva("v3.2-beta.2", "3.2-beta.1"))
        assertTrue(ReglasActualizacion.esMasNueva("v3.2", "3.2-beta.2"))          // la estable gana a su beta
        assertTrue(ReglasActualizacion.esMasNueva("v3.2-rc.1", "3.2-beta.5"))
        assertTrue(ReglasActualizacion.esMasNueva("v3.2-beta.10", "3.2-beta.9"))   // número, no texto
        assertFalse(ReglasActualizacion.esMasNueva("v3.2-beta.1", "3.2"))
        assertFalse(ReglasActualizacion.esMasNueva("v3.2-beta.1", "3.2-beta.1"))
        assertEquals("beta" to 1, ReglasActualizacion.prueba("v3.2-beta.1"))
        assertNull(ReglasActualizacion.prueba("3.2"))
    }

    @Test
    fun conBetasEligeLaMasNuevaYSinBetasLasIgnora() {
        fun rel(tag: String, pre: Boolean) = """{"tag_name":"$tag","prerelease":$pre,"draft":false,"assets":[
            {"name":"FT150TS-$tag.apk","browser_download_url":"https://github.com/z-XOF-z/FT150TS/releases/download/$tag/FT150TS-$tag.apk","size":1}]}"""
        val lista = "[" + listOf(rel("v3.0", false), rel("v3.2-beta.1", true), rel("v3.1", false),
            """{"tag_name":"v9.9","draft":true,"assets":[]}""").joinToString(",") + "]"
        val conBetas = ReglasActualizacion.elegirPublicacion(lista, conBetas = true)!!
        assertEquals("v3.2-beta.1", conBetas.version); assertTrue(conBetas.beta)
        val sinBetas = ReglasActualizacion.elegirPublicacion(lista, conBetas = false)!!
        assertEquals("v3.1", sinBetas.version); assertFalse(sinBetas.beta)
    }

    @Test
    fun soloGitHubPorHttps() {
        assertTrue(ReglasActualizacion.urlPermitida("https://github.com/z-XOF-z/FT150TS/releases/download/v3.0/FT150TS-v3.0.apk"))
        assertTrue(ReglasActualizacion.urlPermitida("https://release-assets.githubusercontent.com/github-production-release-asset/1/2"))
        assertTrue(ReglasActualizacion.urlPermitida("https://api.github.com/repos/z-XOF-z/FT150TS/releases/latest"))
        assertFalse(ReglasActualizacion.urlPermitida("http://github.com/x.apk"))                 // sin cifrar
        assertFalse(ReglasActualizacion.urlPermitida("https://github.com.evil.com/x.apk"))
        assertFalse(ReglasActualizacion.urlPermitida("https://evil.com/github.com/x.apk"))
        assertFalse(ReglasActualizacion.urlPermitida("https://user@github.com/x.apk"))
        assertFalse(ReglasActualizacion.urlPermitida("https://github.com:8443/x.apk"))
        assertFalse(ReglasActualizacion.urlPermitida("file:///sdcard/x.apk"))
        assertFalse(ReglasActualizacion.urlPermitida("no es url"))
    }

    @Test
    fun leeElReleaseDeGitHub() {
        val json = """{"tag_name":"v3.2","draft":false,"prerelease":false,"html_url":"https://github.com/z-XOF-z/FT150TS/releases/tag/v3.2",
            "body":"Ahorro nocturno","assets":[
              {"name":"NOTAS.md","browser_download_url":"https://github.com/z-XOF-z/FT150TS/releases/download/v3.2/NOTAS.md","size":10},
              {"name":"FT150TS-v3.2.apk","browser_download_url":"https://github.com/z-XOF-z/FT150TS/releases/download/v3.2/FT150TS-v3.2.apk","size":2700000}]}"""
        val p = ReglasActualizacion.leerPublicacion(json)
        assertNotNull(p)
        assertEquals("v3.2", p!!.version)
        assertTrue(p.apk.endsWith("FT150TS-v3.2.apk"))
        assertEquals(2700000L, p.tamano)
    }

    @Test
    fun releaseSinApkBorradorOConApkAjenoNoSeOfrece() {
        assertNull(ReglasActualizacion.leerPublicacion("""{"tag_name":"v3.2","assets":[]}"""))
        assertNull(ReglasActualizacion.leerPublicacion("""{"tag_name":"v3.2","prerelease":true,"assets":[
            {"name":"a.apk","browser_download_url":"https://github.com/a.apk"}]}"""))
        assertNull(ReglasActualizacion.leerPublicacion("""{"tag_name":"v3.2","assets":[
            {"name":"a.apk","browser_download_url":"https://otro-sitio.com/a.apk"}]}"""))
    }

    @Test
    fun soloSeInstalaFt150tsMasNuevaYConLaMismaFirma() {
        val p = "com.xof.ft150ts"
        assertNull(ReglasActualizacion.motivoRechazo(p, p, 27, 26, firma, firma))
        assertTrue(ReglasActualizacion.motivoRechazo("com.otra.app", p, 27, 26, firma, firma)!!.contains("no es FT150TS"))
        assertTrue(ReglasActualizacion.motivoRechazo(p, p, 27, 26, setOf("a".repeat(64)), firma)!!.contains("firma"))
        assertTrue(ReglasActualizacion.motivoRechazo(p, p, 25, 26, firma, firma)!!.contains("más viejo"))
        assertTrue(ReglasActualizacion.motivoRechazo(p, p, 26, 26, firma, firma)!!.contains("Ya tienes"))
        assertTrue(ReglasActualizacion.motivoRechazo(p, p, 27, 26, emptySet(), firma)!!.contains("No se pudo leer"))
        assertTrue(ReglasActualizacion.motivoRechazo(null, p, 27, 26, firma, firma)!!.contains("no es FT150TS"))
    }
}
