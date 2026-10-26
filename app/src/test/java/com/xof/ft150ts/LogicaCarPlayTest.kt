package com.xof.ft150ts

import com.xof.ft150ts.EstadoCarPlay.Conectando
import com.xof.ft150ts.EstadoCarPlay.Desactivado
import com.xof.ft150ts.EstadoCarPlay.EnVivo
import com.xof.ft150ts.EstadoCarPlay.Esperando
import com.xof.ft150ts.EstadoCarPlay.Perdido
import com.xof.ft150ts.EstadoCarPlay.SinServicio
import com.xof.ft150ts.EstadoCarPlay.SinSoporte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El estado de CarPlay en el mapa sale solo de lo que responde DiPlay; nada se da por conectado. */
class LogicaCarPlayTest {

    private fun correr(vararg eventos: EventoCarPlay): EstadoCarPlay =
        eventos.fold(Conectando as EstadoCarPlay) { e, ev -> ProtocoloCarPlay.reducir(e, ev) }

    @Test
    fun sesionNormal() {
        // Vincular, recibir el mapa sin sesión, empezar y terminar CarPlay
        assertEquals(Conectando, correr(EventoCarPlay.Vinculando, EventoCarPlay.Vinculado))
        assertEquals(Esperando, correr(EventoCarPlay.Vinculado, EventoCarPlay.Entregado(false)))
        assertEquals(EnVivo, correr(EventoCarPlay.Vinculado, EventoCarPlay.Entregado(false), EventoCarPlay.Flujo(true)))
        assertEquals(Esperando, correr(EventoCarPlay.Entregado(true), EventoCarPlay.Flujo(false)))
    }

    @Test
    fun soloEnVivoMuestraCarPlay() {
        assertTrue(ProtocoloCarPlay.mostrarCarPlay(EnVivo))
        for (e in listOf(Conectando, Esperando, Desactivado, Perdido, SinSoporte, SinServicio, EstadoCarPlay.Falla("x"))) {
            assertFalse("$e no debe tapar el mapa OSM", ProtocoloCarPlay.mostrarCarPlay(e))
        }
    }

    @Test
    fun unFlujoSinMapaEntregadoSeIgnora() {
        // Un STREAM_STATE viejo (antes de ATTACHED o tras un error) no puede poner "en vivo"
        assertEquals(Conectando, correr(EventoCarPlay.Vinculado, EventoCarPlay.Flujo(true)))
        assertEquals(Desactivado, correr(EventoCarPlay.Error("disabled"), EventoCarPlay.Flujo(true)))
        assertEquals(Perdido, correr(EventoCarPlay.Entregado(true), EventoCarPlay.Desvinculado, EventoCarPlay.Flujo(true)))
    }

    @Test
    fun erroresDeDiPlay() {
        assertEquals(Desactivado, correr(EventoCarPlay.Entregado(true), EventoCarPlay.Error("disabled")))
        assertEquals(SinSoporte, correr(EventoCarPlay.Error("unsupported")))
        assertEquals(EstadoCarPlay.Falla("bad_request"), correr(EventoCarPlay.Error("bad_request")))
        assertEquals(EstadoCarPlay.Falla("?"), correr(EventoCarPlay.Error(null)))
        // "DiPlay viejo" (SinServicio) solo sale de buscar(); un bind negado es del sistema (ver abajo)
        assertEquals(EstadoCarPlay.NoConecta, correr(EventoCarPlay.Vinculando, EventoCarPlay.NoSeVinculo))
    }

    @Test
    fun seReintentaSoloLoQuePuedeCambiarSolo() {
        assertTrue(ProtocoloCarPlay.reintentar(Desactivado))      // prendes compartir en DiPlay
        assertTrue(ProtocoloCarPlay.reintentar(Perdido))          // DiPlay se reinició
        assertTrue(ProtocoloCarPlay.reintentar(EstadoCarPlay.Falla("bad_request")))
        assertFalse(ProtocoloCarPlay.reintentar(EnVivo))
        assertFalse(ProtocoloCarPlay.reintentar(Esperando))       // ya está pegado: DiPlay avisa cuando haya mapa
        assertFalse(ProtocoloCarPlay.reintentar(SinSoporte))      // Android no va a cambiar
    }

    @Test
    fun siAndroidNoConectaNoSeConfundeConDiPlayViejo() {
        // bindService negado (p. ej. MIUI sin "Inicio automático") o sin respuesta: es el sistema, y se reintenta
        assertEquals(EstadoCarPlay.NoConecta, correr(EventoCarPlay.Vinculando, EventoCarPlay.NoSeVinculo))
        assertEquals(EstadoCarPlay.NoConecta, correr(EventoCarPlay.Vinculado, EventoCarPlay.SinRespuesta))
        assertTrue(ProtocoloCarPlay.reintentar(EstadoCarPlay.NoConecta))
        // Y se recupera en cuanto DiPlay contesta
        assertEquals(EnVivo, correr(EventoCarPlay.SinRespuesta, EventoCarPlay.Vinculado, EventoCarPlay.Entregado(true)))
    }

    @Test
    fun losReintentosSeEspacianSinParar() {
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 30_000L, 30_000L, 30_000L), (0..5).map { ProtocoloCarPlay.espera(it) })
        assertEquals(30_000L, ProtocoloCarPlay.espera(Int.MAX_VALUE))   // sin desbordarse
        assertEquals(5_000L, ProtocoloCarPlay.espera(-1))
    }

    @Test
    fun unErrorRepetidoSigueSiendoReintentable() {
        // El ciclo de reintentos va con la llave "reintentar"; el mismo error dos veces no debe apagarla
        val e1 = correr(EventoCarPlay.Error("disabled"))
        val e2 = ProtocoloCarPlay.reducir(e1, EventoCarPlay.Error("disabled"))
        assertEquals(e1, e2)
        assertTrue(ProtocoloCarPlay.reintentar(e2))
    }

    @Test
    fun todosLosEstadosTienenTextoYAyuda() {
        val todos = listOf(EstadoCarPlay.Listo, EstadoCarPlay.SinDiPlay, EstadoCarPlay.FirmaDesconocida, SinSoporte, SinServicio,
            EstadoCarPlay.NoConecta, Conectando, Esperando, EnVivo, Desactivado, Perdido, EstadoCarPlay.Falla("x"))
        for (e in todos) {
            assertTrue(ProtocoloCarPlay.texto(e).isNotBlank())
            assertTrue(ProtocoloCarPlay.ayuda(e).isNotBlank())
        }
        // La ayuda de "sin mapa" nombra la opción de DiPlay que de verdad pide el mapa al iPhone
        assertTrue(ProtocoloCarPlay.ayuda(Esperando).contains("CarPlay map on dashboard"))
    }

    @Test
    fun ventanaLeeTareaYEscalaDelPocoF4() {
        // Salidas reales del POCO F4 (HyperOS 3)
        assertEquals(601, ProtocoloCarPlay.leerTarea("Task{d0aa4b1 #601 type=standard A=10286:com.shihab.diplay"))
        assertEquals(null, ProtocoloCarPlay.leerTarea(""))
        assertEquals(0.6102f, ProtocoloCarPlay.leerEscala("scale x=0.6102")!!, 0.0001f)
        assertEquals(null, ProtocoloCarPlay.leerEscala("scale x=9"))          // absurda: no se usa
        assertEquals(null, ProtocoloCarPlay.leerEscala("nada"))
    }

    @Test
    fun compensaLaEscalaSinSalirseDeLaPantalla() {
        // Mapa 1238x846 en una pantalla 2400x1080 dibujada a 0.6102
        val b = ProtocoloCarPlay.compensar(intArrayOf(250, 210, 1488, 1056), 0.6102f, 2400, 1080)
        assertEquals(250, b[0]); assertEquals(210, b[1])
        assertEquals(250 + (1238 / 0.6102f).toInt(), b[2])                  // a lo ancho cabe completo (≈2278)
        assertEquals(1080, b[3])                                             // a lo alto se corta en la pantalla
        val sinEscala = ProtocoloCarPlay.compensar(intArrayOf(0, 0, 100, 100), 1f, 2400, 1080)
        assertEquals(100, sinEscala[2]); assertEquals(100, sinEscala[3])
    }

    @Test
    fun pestanaALaMedidaYEscondidaSinCambiarDeTamano() {
        // Área real del POCO F4 debajo de las pestañas: 0,150 - 2400,1080 a escala 0.6102 → 0,150 - 3933,1674
        val b = ProtocoloCarPlay.aLaMedida(intArrayOf(0, 150, 2400, 1080), 0.6102f)
        assertEquals(0, b[0]); assertEquals(150, b[1])
        assertEquals((2400 / 0.6102f).toInt(), b[2]); assertEquals(150 + (930 / 0.6102f).toInt(), b[3])
        // Fuera de pantalla: mismo ancho y alto (DiPlay no cambia de lienzo → CarPlay no se reconecta)
        val f = ProtocoloCarPlay.fueraDePantalla(b, 2400)
        assertEquals(2400, f[0]); assertEquals(b[2] - b[0], f[2] - f[0]); assertEquals(b[1], f[1]); assertEquals(b[3], f[3])
        // El comando lo acepta (cabe en el tope de 20000)
        assertTrue(ProtocoloCarPlay.cmdRedimensionar(601, f).startsWith("am task resize 601 2400 150"))
    }

    @Test
    fun escondidaNoSeSalePorAbajo() {
        // Máximo en el POCO F4: 0,191 - 3933,1656 a escala 0.6102 → se dibuja hasta y≈1085 (más allá de 1080)
        val b = intArrayOf(0, 191, 3933, 1656)
        val f = ProtocoloCarPlay.fueraDePantalla(b, 2400, 0.6102f, 1080)
        assertEquals(2400, f[0]); assertEquals(b[2] - b[0], f[2] - f[0]); assertEquals(b[3] - b[1], f[3] - f[1])
        assertTrue(f[1] + kotlin.math.ceil((f[3] - f[1]) * 0.6102f) <= 1080 - 8)
        // Si ya cabe, no se mueve
        val chica = intArrayOf(0, 100, 1080, 1000)
        assertEquals(100, ProtocoloCarPlay.fueraDePantalla(chica, 2400, 0.6102f, 1080)[1])
    }

    @Test
    fun detectaSiLaVentanaQuedoMal() {
        val e = intArrayOf(0, 219, 3933, 1630)
        assertTrue(ProtocoloCarPlay.ventanaEnSuLugar("mode=freeform\nmBounds=Rect(0, 219 - 3933, 1630)", e))
        assertTrue(ProtocoloCarPlay.ventanaEnSuLugar("mode=freeform\nmBounds=Rect(2, 220 - 3931, 1630)", e))   // ±3 px
        // El caso del POCO F4: corrida a la derecha
        assertFalse(ProtocoloCarPlay.ventanaEnSuLugar("mode=freeform\nmBounds=Rect(2129, 247 - 5839, 1566)", e))
        // DiPlay volvió a pantalla completa
        assertFalse(ProtocoloCarPlay.ventanaEnSuLugar("mode=fullscreen\nmBounds=Rect(0, 219 - 3933, 1630)", e))
        assertFalse(ProtocoloCarPlay.ventanaEnSuLugar("", e))
    }

    @Test
    fun comandosDeVentanaSoloConNumerosValidos() {
        assertEquals("am task resize 601 250 210 1488 1056", ProtocoloCarPlay.cmdRedimensionar(601, intArrayOf(250, 210, 1488, 1056)))
        for (malo in listOf(intArrayOf(-1, 0, 10, 10), intArrayOf(10, 10, 5, 20), intArrayOf(0, 0, 99999, 10), intArrayOf(1, 2, 3))) {
            try { ProtocoloCarPlay.cmdRedimensionar(601, malo); org.junit.Assert.fail("aceptó ${malo.toList()}") } catch (_: IllegalArgumentException) { }
        }
        try { ProtocoloCarPlay.cmdBuscarTarea("com.otra.app"); org.junit.Assert.fail() } catch (_: IllegalArgumentException) { }
        assertTrue(ProtocoloCarPlay.cmdBuscarTarea("com.shihab.diplay").contains("com.shihab.diplay"))
    }

    @Test
    fun textoDeOtraAppSeLimpia() {
        assertEquals("bad_request", ProtocoloCarPlay.limpiar("bad_request"))
        assertEquals("abc", ProtocoloCarPlay.limpiar("<a>b\nc‮"))
        assertEquals(24, ProtocoloCarPlay.limpiar("x".repeat(500)).length)
        assertEquals("?", ProtocoloCarPlay.limpiar("<>"))
    }

    @Test
    fun medidasValidas() {
        assertEquals(1, ProtocoloCarPlay.medida(0))
        assertEquals(1, ProtocoloCarPlay.medida(-5))
        assertEquals(1280, ProtocoloCarPlay.medida(1280))
        assertEquals(ProtocoloCarPlay.LADO_MAX, ProtocoloCarPlay.medida(Int.MAX_VALUE))
    }

    @Test
    fun soloSeConfiaEnLaFirmaDeDiPlayOLaQueTuMarcaste() {
        val oficial = CarPlay.FIRMAS_OFICIALES.first()
        val propia = "b".repeat(64)
        val ajena = "c".repeat(64)
        assertTrue(ProtocoloCarPlay.firmaConfiable(setOf(oficial), CarPlay.FIRMAS_OFICIALES, emptySet()))
        assertTrue(ProtocoloCarPlay.firmaConfiable(setOf(propia), CarPlay.FIRMAS_OFICIALES, setOf(propia)))
        assertFalse(ProtocoloCarPlay.firmaConfiable(setOf(ajena), CarPlay.FIRMAS_OFICIALES, setOf(propia)))
        assertFalse(ProtocoloCarPlay.firmaConfiable(emptySet(), CarPlay.FIRMAS_OFICIALES, setOf(propia)))
    }

    @Test
    fun firmasGuardadasSeValidan() {
        assertTrue(CarPlay.FIRMAS_OFICIALES.all { ProtocoloCarPlay.esSha256(it) })
        assertFalse(ProtocoloCarPlay.esSha256("87B38B"))
        assertFalse(ProtocoloCarPlay.esSha256("z".repeat(64)))
        assertEquals("00ff10", ProtocoloCarPlay.hex(byteArrayOf(0, -1, 16)))
    }

    @Test fun maximoEscondeLaPildoraDeAbajo() {
        // POCO F4 horizontal: 2400x1080, densidad 2.75, páginas desde y=219
        CarPlayPestana.conRiel = false
        val a = intArrayOf(0, 219, 2400, 1080)
        val m = CarPlayPestana.areaSegunTamano(a, TamanoCarPlay.MAXIMO, 2400, 1080, 2.75f)
        assertEquals(listOf(0, 197, 2400, 1080 + 41), m.toList())          // sube 8 dp, baja 15 dp fuera de la pantalla
        val am = CarPlayPestana.areaSegunTamano(a, TamanoCarPlay.AMPLIO, 2400, 1080, 2.75f)
        assertEquals(1080 + 41, am[3])
        // Con el módulo de Magisk sin barras no hay píldora: no se alarga (no se corta el dock de CarPlay)
        val sb = CarPlayPestana.areaSegunTamano(a, TamanoCarPlay.MAXIMO, 2400, 1080, 2.75f, sinBarras = true)
        assertEquals(listOf(0, 197, 2400, 1080 + 5), sb.toList())   // solo esconde la barra de 1 dp
        val aj = CarPlayPestana.areaSegunTamano(a, TamanoCarPlay.AJUSTADO, 2400, 1080, 2.75f)
        assertTrue(aj[3] < 1080)                                           // Ajustado sigue dentro del marco
        CarPlayPestana.conRiel = true
        val r = CarPlayPestana.areaSegunTamano(intArrayOf(300, 40, 2400, 1080), TamanoCarPlay.MAXIMO, 2400, 1080, 2.75f)
        assertEquals(listOf(300, 40, 2400, 1121), r.toList())             // con riel no se tapa nada de arriba
        CarPlayPestana.conRiel = false
    }

    @Test fun leeLaEscalaEnLasDosVersionesDeHyperOs() {
        // HyperOS 3 / Android 16
        assertEquals(0.6102f, ProtocoloCarPlay.leerEscala("scale x=0.6102")!!, 0.0001f)
        // HyperOS 1 / Android 14: la matriz de la capa (lo que deja pasar el grep del comando)
        assertEquals(0.6102f, ProtocoloCarPlay.leerEscala("    0.6102  0.0000  0.0000")!!, 0.0001f)
        assertEquals(0.6102f, ProtocoloCarPlay.leerEscala("    0.6102  0.0000  2400.0000")!!, 0.0001f)    // ventana escondida
        // Root.ejecutarCada regresa la salida con trim(): sin los espacios del principio
        assertEquals(0.6102f, ProtocoloCarPlay.leerEscala("0.6102  0.0000  352.0854")!!, 0.0001f)
        assertEquals(null, ProtocoloCarPlay.leerEscala(""))
        assertEquals(null, ProtocoloCarPlay.leerEscala("    0.0500  0.0000  0.0000"))   // fuera de rango
    }

    @Test fun leeDondeEstaLaVentana() {
        assertEquals(listOf(400, 80, 1480, 1700),
            ProtocoloCarPlay.leerVentana("mode=freeform mBounds=Rect(400, 80 - 1480, 1700)")!!.toList())
        assertEquals(null, ProtocoloCarPlay.leerVentana("mode=fullscreen mBounds=Rect(0, 0 - 2400, 1080)"))
        assertEquals(null, ProtocoloCarPlay.leerVentana(""))
    }
}
