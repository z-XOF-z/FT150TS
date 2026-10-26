package com.xof.ft150ts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Con recortes de capturas reales del CarPlay de Andrew en el POCO F4 (filas 870–1079 de la pantalla). */
class GestosCarPlayTest {
    private val area = intArrayOf(0, 191, 2400, 1085)      // donde se dibuja CarPlay (Máximo, escala 0.6102)

    private fun pagina(nombre: String): PaginaCarPlay {
        // Grises crudos de 2400 de ancho: filas 870–1079 (las "x_", de modo noche, desde la 790 para incluir texto)
        val px = javaClass.getResourceAsStream("/carplay/$nombre.gris")!!.readBytes()
        val y0 = if (nombre.startsWith("x_")) 790 else 870
        val filas = px.size / 2400
        val lum = { x: Int, y: Int ->
            val yy = y - y0
            if (x !in 0 until 2400 || yy !in 0 until filas) 128 else px[yy * 2400 + x].toInt() and 0xFF
        }
        return GestosCarPlayLogica.clasificar(lum, area)
    }

    @Test fun reconoceLasCuatroPaginas() {
        assertEquals(PaginaCarPlay.WIDGETS, pagina("widgets"))
        assertEquals(PaginaCarPlay.OTRA, pagina("mapa"))
        assertEquals(PaginaCarPlay.APPS, pagina("apps1"))
        assertEquals(PaginaCarPlay.ULTIMA_APPS, pagina("apps2"))
    }

    @Test fun reconoceConElLienzoNuevo() {
        // Después de apagar el panel lateral de DiPlay la interfaz de CarPlay salió ~30 % más grande
        assertEquals(PaginaCarPlay.WIDGETS, pagina("n_widgets"))
        assertEquals(PaginaCarPlay.OTRA, pagina("n_mapa"))
        assertEquals(PaginaCarPlay.APPS, pagina("n_apps1"))
        assertEquals(PaginaCarPlay.ULTIMA_APPS, pagina("n_apps2"))
    }

    @Test fun reconoceEnModoNoche() {
        // CarPlay oscuro: puntitos blancos, y en Widgets el texto del clima ya no se confunde con puntitos
        assertEquals(PaginaCarPlay.WIDGETS, pagina("x_widgets"))
        assertEquals(PaginaCarPlay.OTRA, pagina("x_mapa"))
        assertEquals(PaginaCarPlay.APPS, pagina("x_apps1"))
        assertEquals(PaginaCarPlay.ULTIMA_APPS, pagina("x_apps2"))
    }

    @Test fun soloCambiaSiCarPlayNoSeMovio() {
        val w = 2400
        // En Widgets, deslizar a la derecha y CarPlay sigue en Widgets → Tablero
        assertEquals(PAGINA_TABLERO, GestosCarPlayLogica.destino(PaginaCarPlay.WIDGETS, PaginaCarPlay.WIDGETS, 900, 40, w, 300))
        // En la última de Apps, a la izquierda → Moto
        assertEquals(PAGINA_MOTO, GestosCarPlayLogica.destino(PaginaCarPlay.ULTIMA_APPS, PaginaCarPlay.ULTIMA_APPS, -900, 10, w, 300))
        // Del mapa a Widgets (CarPlay sí se movió): nada
        assertNull(GestosCarPlayLogica.destino(PaginaCarPlay.OTRA, PaginaCarPlay.WIDGETS, 900, 0, w, 300))
        // Un deslizamiento corto de verdad (357 px, sacado del registro de Andrew en v3.9.0) ya cuenta
        assertEquals(PAGINA_TABLERO, GestosCarPlayLogica.destino(PaginaCarPlay.WIDGETS, PaginaCarPlay.WIDGETS, 357, 63, w, 75))
        // Corto, vertical o lento: nada
        assertNull(GestosCarPlayLogica.destino(PaginaCarPlay.WIDGETS, PaginaCarPlay.WIDGETS, 200, 0, w, 300))
        assertNull(GestosCarPlayLogica.destino(PaginaCarPlay.WIDGETS, PaginaCarPlay.WIDGETS, 700, 600, w, 300))
        assertNull(GestosCarPlayLogica.destino(PaginaCarPlay.WIDGETS, PaginaCarPlay.WIDGETS, 900, 0, w, 3000))
    }

    @Test fun toqueCrudoAPantallaHorizontal() {
        // Rotación 1: el eje largo crudo (Y) es la X de la pantalla
        assertEquals(1200 to 539, GestosCarPlayLogica.aPantalla(5400, 12000, 1))
        assertEquals(0 to 1079, GestosCarPlayLogica.aPantalla(0, 0, 1))
    }
}
