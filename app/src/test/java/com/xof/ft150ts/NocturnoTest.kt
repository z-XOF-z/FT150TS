package com.xof.ft150ts

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Horario nocturno, posponer, comandos permitidos y registro de cambios. */
class NocturnoTest {
    private val mx = ZoneId.of("America/Mexico_City")      // sin horario de verano desde 2022
    private val ny = ZoneId.of("America/New_York")         // con horario de verano
    private fun t(s: String, z: ZoneId = mx) = ZonedDateTime.of(LocalDateTime.parse(s), z)

    // 2026-10-15 es jueves (4); 2026-10-18 domingo (7)

    @Test
    fun horarioDeFabricaEsMedianocheASeisYMedia() {
        val h = Horario()
        assertEquals(0, h.inicio); assertEquals(6 * 60 + 30, h.fin); assertEquals((1..7).toSet(), h.dias)
        assertTrue(h.valido); assertFalse(h.cruzaMedianoche); assertEquals(390, h.minutos)
        assertTrue(h.contiene(t("2026-10-15T00:00")))
        assertTrue(h.contiene(t("2026-10-15T06:29:59")))
        assertFalse(h.contiene(t("2026-10-15T06:30")))           // el fin no cuenta
        assertFalse(h.contiene(t("2026-10-14T23:59:59")))
    }

    @Test
    fun cruzaMedianocheYCuentaParaElDiaQueEmpieza() {
        val h = Horario(23 * 60, 6 * 60 + 30, setOf(4))           // solo jueves en la noche
        assertTrue(h.cruzaMedianoche); assertEquals(450, h.minutos)
        assertTrue(h.contiene(t("2026-10-15T23:30")))             // jueves
        assertTrue(h.contiene(t("2026-10-16T05:00")))             // viernes en la madrugada = noche del jueves
        assertFalse(h.contiene(t("2026-10-16T23:30")))            // viernes en la noche no
        assertFalse(h.contiene(t("2026-10-15T05:00")))            // madrugada del jueves = noche del miércoles
        val (i, f) = h.ventanaActual(t("2026-10-16T01:00"))!!
        assertEquals(t("2026-10-15T23:00"), i); assertEquals(t("2026-10-16T06:30"), f)
    }

    @Test
    fun soloLosDiasElegidos() {
        val h = Horario(dias = setOf(7))                          // solo domingo
        assertFalse(h.contiene(t("2026-10-15T01:00")))
        assertTrue(h.contiene(t("2026-10-18T01:00")))
        assertEquals(t("2026-10-18T00:00"), h.proximoInicio(t("2026-10-15T12:00")))
    }

    @Test
    fun proximoInicio() {
        val h = Horario()
        assertEquals(t("2026-10-16T00:00"), h.proximoInicio(t("2026-10-15T00:10")))   // ya empezó la de hoy
        assertEquals(t("2026-10-16T00:00"), h.proximoInicio(t("2026-10-15T23:59")))
        assertNull(Horario(dias = emptySet()).proximoInicio(t("2026-10-15T12:00")))
    }

    @Test
    fun horarioInvalidoNoAhorraNunca() {
        assertFalse(Horario(300, 300).valido)                     // inicio = fin
        assertFalse(Horario(-1, 30).valido)
        assertFalse(Horario(0, 1440).valido)
        assertFalse(Horario(dias = setOf(0, 8)).valido)
        assertNull(Horario(300, 300).ventanaActual(t("2026-10-15T05:00")))
    }

    @Test
    fun cambioDeHorarioDeVerano() {
        // 2026-03-08 en Nueva York: a las 02:00 se brinca a las 03:00. La noche 00:00–06:30 dura una hora menos real.
        val h = Horario()
        assertTrue(h.contiene(t("2026-03-08T03:30", ny)))
        val (i, f) = h.ventanaActual(t("2026-03-08T01:30", ny))!!
        assertEquals(5.5, java.time.Duration.between(i, f).toMinutes() / 60.0, 0.01)
        // Inicio dentro del hueco (02:30 no existe ese día): java.time lo corre a 03:30 en vez de fallar
        val hueco = Horario(2 * 60 + 30, 6 * 60, setOf(7))
        assertEquals(t("2026-03-08T03:30", ny), hueco.ventanaActual(t("2026-03-08T04:00", ny))!!.first)
    }

    @Test
    fun cuandoSePospone() {
        fun m(rodando: Boolean = false, cp: Boolean = false, prot: Boolean = false, uso: Long? = null, pos: Int = 10, resp: Boolean = true) =
            LogicaNoche.motivoPosponer(rodando, cp, prot, uso, pos, resp)
        assertNull(m())
        assertNull(m(uso = 45))
        assertTrue(m(uso = 3)!!.contains("usaste"))
        assertNull(m(uso = 3, pos = 0))                          // "No posponer por uso"
        assertTrue(m(rodando = true)!!.contains("rodando"))
        assertTrue(m(cp = true)!!.contains("CarPlay"))
        assertNull(m(cp = true, resp = false))
        assertTrue(m(prot = true)!!.contains("protegida"))
    }

    @Test
    fun comandosSoloConParametrosValidos() {
        assertEquals("am set-standby-bucket com.whatsapp restricted", LogicaNoche.cmdGrupo("com.whatsapp", "restricted"))
        assertEquals("am kill com.whatsapp", LogicaNoche.cmdCerrar("com.whatsapp"))
        for (malo in listOf("com.x; reboot", "com.x && rm -rf /", "com x", "\$(id)", "com.x`id`", "x", "", "com.x'")) {
            assertFalse("aceptó: $malo", LogicaNoche.paqueteValido(malo))
            try { LogicaNoche.cmdCerrar(malo); fail("armó un comando con: $malo") } catch (_: IllegalArgumentException) { }
        }
        try { LogicaNoche.cmdGrupo("com.whatsapp", "never; reboot"); fail() } catch (_: IllegalArgumentException) { }
        // Nada de apagar, reiniciar ni tocar SELinux en la lista de comandos
        val todos = listOf(LogicaNoche.cmdAhorro(true), LogicaNoche.cmdAhorro(false), LogicaNoche.cmdDoze(true), LogicaNoche.cmdDoze(false),
            LogicaNoche.cmdDatos(true), LogicaNoche.cmdDatos(false), LogicaNoche.cmdWifi(true), LogicaNoche.cmdWifi(false))
        assertTrue(todos.none { c -> listOf("reboot", "shutdown", "setenforce", "svc power", "rm ").any { c.contains(it) } })
    }

    @Test
    fun appsQueNuncaSeTocan() {
        val propio = "com.xof.ft150ts"
        assertTrue(LogicaNoche.protegidoSiempre(propio, propio, emptySet()))
        CarPlay.PAQUETES.forEach { assertTrue(LogicaNoche.protegidoSiempre(it, propio, emptySet())) }
        assertTrue(LogicaNoche.protegidoSiempre("com.android.phone", propio, emptySet()))
        assertTrue(LogicaNoche.protegidoSiempre("com.google.android.deskclock", propio, emptySet()))
        assertTrue(LogicaNoche.protegidoSiempre("com.android.providers.contacts", propio, emptySet()))
        assertTrue(LogicaNoche.protegidoSiempre("com.mi.banco", propio, setOf("com.mi.banco")))
        assertFalse(LogicaNoche.protegidoSiempre("com.whatsapp", propio, emptySet()))
    }

    @Test
    fun leerSalidasDeAndroid() {
        assertEquals(10, LogicaNoche.leerGrupo("10"))
        assertEquals(45, LogicaNoche.leerGrupo("Bucket: 45\n"))
        assertNull(LogicaNoche.leerGrupo("Unknown package"))
        assertEquals(true, LogicaNoche.leerBool("1\n"))
        assertEquals(false, LogicaNoche.leerBool("0"))
        assertNull(LogicaNoche.leerBool("null"))
        assertEquals("rare", LogicaNoche.GRUPOS[40])
    }

    @Test
    fun registroIdaYVuelta() {
        val l = listOf(
            CambioNoche(AccionNoche.AHORRO, anterior = "0", ok = true, detalle = "prendido"),
            CambioNoche(AccionNoche.RESTRINGIR, "com.whatsapp", "active", ok = true, detalle = "active → restricted"),
            CambioNoche(AccionNoche.DOZE, ok = false, detalle = "pantalla prendida"),
            CambioNoche(AccionNoche.CERRAR, "com.x.y", ok = true, revertido = true),
        )
        val vuelta = LogicaNoche.deJson(LogicaNoche.aJson(l))
        assertEquals(l, vuelta)
        // Pendientes: solo lo aplicado con éxito y sin deshacer
        assertEquals(listOf(AccionNoche.AHORRO, AccionNoche.RESTRINGIR), LogicaNoche.pendientes(vuelta).map { it.accion })
    }

    @Test
    fun registroDanadoOAjenoNoTrabaNiInyecta() {
        assertEquals(emptyList<CambioNoche>(), LogicaNoche.deJson("no es json"))
        assertEquals(emptyList<CambioNoche>(), LogicaNoche.deJson(null))
        // Acción desconocida y paquete con comando: se descartan
        val malo = """[{"a":"REBOOT","ok":true},{"a":"RESTRINGIR","o":"com.x; reboot","p":"active","ok":true},
            {"a":"AHORRO","p":"0","ok":true}]"""
        val l = LogicaNoche.deJson(malo)
        assertEquals(listOf(AccionNoche.AHORRO), l.map { it.accion })
    }

    @Test
    fun perfiles() {
        assertTrue(AccionNoche.DATOS !in PerfilNoche.EXTREMO.acciones)   // cortar mensajes nunca viene de fábrica
        assertTrue(AccionNoche.WIFI !in PerfilNoche.EXTREMO.acciones)
        assertTrue(PerfilNoche.EXTREMO.acciones.containsAll(PerfilNoche.CONSERVADOR.acciones))
        assertTrue(PerfilNoche.CONSERVADOR.acciones.none { it == AccionNoche.RESTRINGIR || it == AccionNoche.CERRAR })
    }

    @Test
    fun gastoDeBateriaPorNoche() {
        val h = 3_600_000L
        assertEquals(1.0f, LogicaNoche.gastoPorHora(92, 86, 6 * h, cargo = false)!!, 0.001f)
        assertEquals(null, LogicaNoche.gastoPorHora(92, 86, 6 * h, cargo = true))       // cargando: no cuenta
        assertEquals(null, LogicaNoche.gastoPorHora(92, 91, 10 * 60_000L, cargo = false)) // muy corta
        assertEquals(null, LogicaNoche.gastoPorHora(-1, 86, 6 * h, cargo = false))
        // Historial: ida y vuelta, y solo las últimas 14
        var l = emptyList<NocheRegistro>()
        repeat(20) { i -> l = LogicaNoche.agregarNoche(l, NocheRegistro(i * h, i * h + 6 * h, 90, 84, "Extremo", false)) }
        assertEquals(14, l.size)
        assertEquals(6 * h, l.first().desde)
        assertEquals(l, LogicaNoche.nochesDeJson(LogicaNoche.nochesAJson(l)))
        assertEquals(emptyList<NocheRegistro>(), LogicaNoche.nochesDeJson("basura"))
    }

    @Test
    fun veredictoDelEncendidoProgramado() {
        val v = DiagnosticoEnergia::veredicto
        assertEquals(EncendidoProgramado.DEL_SISTEMA, v("00000000", "N", true, true))
        assertEquals(EncendidoProgramado.PROBAR, v("00000001", "N", false, true))
        assertEquals(EncendidoProgramado.PROBAR, v("?", "Y", false, true))
        assertEquals(EncendidoProgramado.NO, v("00000000", "N", false, true))
        assertEquals(EncendidoProgramado.NO, v("00000000", "?", false, true))
        assertEquals(EncendidoProgramado.SIN_DATOS, v("?", "?", false, true))      // otro driver: no se adivina
        assertEquals(EncendidoProgramado.SIN_DATOS, v(null, null, false, false))   // sin root
    }
}
