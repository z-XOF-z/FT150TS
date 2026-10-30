package com.xof.ft150ts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protocolo con la tarjeta v1.6: telemetría binaria, OTA, registros de la SD, "casa" y Telegram. */
class LogicaTarjetaTest {

    /** Arma el paquete igual que binRapido() del firmware. */
    private fun paquete(vel: Int, rpm: Int, marcha: Int, volt: Int, gas: Int, temp: Int, incl: Int, a: Int, shift: Int, banderas: Int): ByteArray {
        val b = ByteArray(18)
        fun i16(k: Int, v: Int) { b[k] = (v and 0xFF).toByte(); b[k + 1] = ((v shr 8) and 0xFF).toByte() }
        b[0] = 0xB1.toByte()
        i16(1, vel); i16(3, rpm); b[5] = marcha.toByte(); i16(6, volt); b[8] = gas.toByte()
        i16(9, temp); i16(11, incl); i16(13, a); i16(15, shift); b[17] = banderas.toByte()
        return b
    }

    @Test
    fun telemetriaBinariaConDatos() {
        val r = Protocolo.parseRapidaBin(paquete(653, 7420, 3, 1385, 64, 873, 125, -42, 7100, 3))!!
        assertEquals(65.3f, r.vel, 0.001f)
        assertEquals(7420, r.rpm)
        assertEquals(3, r.marcha)
        assertEquals(13.85f, r.vMoto, 0.001f)
        assertEquals(64, r.gas)
        assertEquals(87.3f, r.temp, 0.001f)
        assertEquals(12.5f, r.incl, 0.001f)
        assertEquals(-0.42f, r.aFwd, 0.001f)
        assertEquals(7100, r.shift)
        assertTrue(r.ignicion && r.motor)
    }

    @Test
    fun telemetriaBinariaSinDatosQuedaIgualQueElJson() {
        val r = Protocolo.parseRapidaBin(paquete(-10, 0xFFFF, -1, 0xFFFF, -1, -990, -10, 0, 7000, 0))!!
        assertEquals(-1f, r.vel)
        assertEquals(-1, r.rpm)
        assertEquals(-1, r.marcha)
        assertEquals(-1f, r.vMoto)
        assertEquals(-1, r.gas)
        assertEquals(-99f, r.temp)
        assertEquals(-1f, r.incl)
        assertFalse(r.ignicion || r.motor)
        // Corto o con otra marca: no es un paquete binario
        assertNull(Protocolo.parseRapidaBin(ByteArray(10)))
        assertNull(Protocolo.parseRapidaBin("{\"k\":\"t\",\"v\":1.0,\"r\":2}".toByteArray()))
    }

    @Test
    fun otaRevisaElArchivoYArmaLosPedazos() {
        assertNotNull(OtaLogica.revisarImagen(ByteArray(500)))                         // muy chico
        assertNotNull(OtaLogica.revisarImagen(ByteArray(200_000)))                     // no empieza con 0xE9
        assertNotNull(OtaLogica.revisarImagen(ByteArray(4 * 1024 * 1024) { 0xE9.toByte() }))   // no cabe
        assertNull(OtaLogica.revisarImagen(ByteArray(200_000).also { it[0] = 0xE9.toByte() }))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", OtaLogica.md5("abc".toByteArray()))
        val bin = ByteArray(70_000) { (it % 251).toByte() }
        val p = OtaLogica.paquete(bin, 65_540, 3)
        assertArrayEquals(byteArrayOf(0x04, 0x00, 0x01, 0x00, bin[65_540], bin[65_541], bin[65_542]), p)
    }

    @Test
    fun registrosDeLaSd() {
        val l = Protocolo.parseListaSd("@SDL [{\"n\":\"viajes.csv\",\"t\":1234},{\"n\":\"pista.csv\",\"t\":0}]\n")!!
        assertEquals(listOf(ArchivoSd("viajes.csv", 1234), ArchivoSd("pista.csv", 0)), l)
        val p = Protocolo.parsePedazoSd("@SD viajes.csv 1500 12 1534\ninicio,fin\n1,2\n")!!
        assertEquals("viajes.csv", p.archivo)
        assertEquals(1500L, p.desde)
        assertEquals(1534L, p.total)
        assertEquals("inicio,fin\n1", p.texto)
        assertNull(Protocolo.parsePedazoSd("No existe."))
    }

    @Test
    fun estadoDeCasa() {
        val c = Protocolo.parseCasa("{\"casa\":1,\"ssid\":\"Casa XOF\",\"clave\":1,\"tg\":1,\"chat\":\"987654321\",\"cada\":30,\"con\":0}")!!
        assertEquals("Casa XOF", c.ssid)
        assertTrue(c.clave && c.telegram)
        assertEquals("987654321", c.chat)
        assertEquals(30, c.cadaMin)
        assertEquals("", Protocolo.parseCasa("{\"casa\":1,\"ssid\":\"\",\"clave\":0,\"tg\":0,\"chat\":\"0\",\"cada\":15,\"con\":0}")!!.chat)
        assertNull(Protocolo.parseCasa("{\"fw\":\"v1.6\"}"))
    }

    @Test
    fun telegram() {
        assertTrue(LogicaTelegram.tokenValido("123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"))
        assertFalse(LogicaTelegram.tokenValido("123456789"))
        assertFalse(LogicaTelegram.tokenValido("abc:def"))
        val r = "{\"ok\":true,\"result\":[" +
            "{\"update_id\":1,\"message\":{\"chat\":{\"id\":111,\"type\":\"private\"},\"text\":\"hola\"}}," +
            "{\"update_id\":2,\"message\":{\"chat\":{\"id\":222,\"type\":\"private\"},\"text\":\"/start\"}}]}"
        assertEquals("222", LogicaTelegram.ultimoChat(r))
        assertNull(LogicaTelegram.ultimoChat("{\"ok\":true,\"result\":[]}"))
    }
}
