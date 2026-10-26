package com.xof.ft150ts

import kotlin.math.abs

/*
 * Parte pura de los gestos de CarPlay (sin Android, se prueba en GestosCarPlayTest).
 * CarPlay es un video del iPhone: FT150TS no sabe en qué página está. Se averigua mirando unos pocos pixeles
 * (medido en el POCO F4 con el CarPlay de Andrew, iOS 26):
 *  - Botón de la esquina del dock (abajo a la izquierda): CUADRO en las páginas de inicio (Widgets y Apps),
 *    CUADRÍCULA en el mapa o dentro de una app.
 *  - Puntitos de página (solo en las páginas de Apps): el activo es el de más contraste.
 * Widgets = cuadro y sin puntitos. Última página de Apps = cuadro y el último puntito activo.
 * Posiciones relativas al rectángulo donde se DIBUJA CarPlay (no dependen del tamaño elegido).
 */
enum class PaginaCarPlay { WIDGETS, APPS, ULTIMA_APPS, OTRA }

object GestosCarPlayLogica {
    // Zona donde se BUSCA (relativa al área de CarPlay). El tamaño de la interfaz cambia según el lienzo que pida
    // DiPlay (medido: al apagar su panel lateral todo creció ~30 %), así que no se usan posiciones fijas.
    const val BOTON_X0 = 0.006f; const val BOTON_X1 = 0.075f
    const val BOTON_Y0 = 0.82f;  const val BOTON_Y1 = 0.995f
    const val PUNTOS_Y0 = 0.68f; const val PUNTOS_Y1 = 0.93f
    const val PUNTOS_X0 = 0.38f; const val PUNTOS_X1 = 0.62f

    /** Filas de pantalla que hacen falta (para capturar solo esa franja). */
    fun filas(t: Int, alto: Int): IntRange = (t + (PUNTOS_Y0 - 0.01f) * alto).toInt()..(t + alto)

    /**
     * [lum] = luminancia 0..255 de un pixel de pantalla (x, y). [r] = área dibujada de CarPlay (izq, arriba, der, abajo).
     */
    fun clasificar(lum: (Int, Int) -> Int, r: IntArray): PaginaCarPlay = clasificarCon(lum, r, null).first

    /**
     * Como [clasificar], pero con [pista] = dónde estaba el botón la vez pasada (x, y, medio lado): se revisa ahí primero
     * (±2 px) y solo si no está se busca en toda la esquina. Regresa también dónde quedó el botón (para la próxima).
     */
    fun clasificarCon(lum: (Int, Int) -> Int, r: IntArray, pista: IntArray?): Pair<PaginaCarPlay, IntArray?> {
        val w = r[2] - r[0]; val h = r[3] - r[1]
        if (w < 200 || h < 100) return PaginaCarPlay.OTRA to null
        val boton = pista?.let { revisarPista(lum, it) } ?: buscarBotonCuadro(lum, r) ?: return PaginaCarPlay.OTRA to pista
        val p = buscarPuntos(lum, r, boton) ?: return PaginaCarPlay.WIDGETS to boton
        return (if (p.second == p.first - 1) PaginaCarPlay.ULTIMA_APPS else PaginaCarPlay.APPS) to boton
    }

    private fun revisarPista(lum: (Int, Int) -> Int, b: IntArray): IntArray? {
        for (dm in intArrayOf(0, -2, 2)) for (dy in -2..2 step 2) for (dx in -2..2 step 2)
            if (botonCuadro(lum, b[0] + dx, b[1] + dy, b[2] + dm)) return intArrayOf(b[0] + dx, b[1] + dy, b[2] + dm)
        return null
    }

    /** Filas que hacen falta si ya se sabe dónde está el botón (los puntitos + el botón): ~1/4 de la franja completa. */
    fun filasConPista(b: IntArray): IntRange = (b[1] - b[2] * 7)..(b[1] + b[2] * 2 + 4)

    /** Busca en la esquina del dock el botón "cuadro" (las páginas de inicio); en el mapa o una app es una cuadrícula. */
    fun hayBotonCuadro(lum: (Int, Int) -> Int, r: IntArray): Boolean = buscarBotonCuadro(lum, r) != null

    /** Igual que [hayBotonCuadro] pero regresa dónde está: (centro x, centro y, medio lado). */
    fun buscarBotonCuadro(lum: (Int, Int) -> Int, r: IntArray): IntArray? {
        val w = r[2] - r[0]; val h = r[3] - r[1]
        val x0 = r[0] + (BOTON_X0 * w).toInt(); val x1 = r[0] + (BOTON_X1 * w).toInt()
        val y0 = r[1] + (BOTON_Y0 * h).toInt(); val y1 = r[1] + (BOTON_Y1 * h).toInt()
        val mMin = maxOf(6, w * 5 / 1000); val mMax = maxOf(mMin + 2, w * 13 / 1000)
        var m = mMin
        while (m <= mMax) {
            var cy = y0 + m
            while (cy <= y1 - m) {
                var cx = x0 + m
                while (cx <= x1 - m) {
                    if (botonCuadro(lum, cx, cy, m)) return intArrayOf(cx, cy, m)
                    cx += 2
                }
                cy += 2
            }
            m += 2
        }
        return null
    }

    /**
     * ¿Hay un cuadro de contorno de medio lado [medio] centrado en (cx, cy)? Bordes continuos (sin las esquinas
     * redondeadas) y el interior vacío. La cuadrícula de 9 puntos no tiene bordes continuos.
     */
    fun botonCuadro(lum: (Int, Int) -> Int, cx: Int, cy: Int, medio: Int): Boolean {
        val fondo = mediana(listOf(lum(cx - medio * 8 / 5, cy), lum(cx + medio * 8 / 5, cy), lum(cx, cy - medio * 8 / 5), lum(cx, cy + medio * 8 / 5)))
        fun tinta(x: Int, y: Int) = abs(lum(x, y) - fondo) > 60
        // Interior vacío (lo más barato primero)
        val r = medio * 6 / 10
        var dx = -r
        while (dx <= r) { var dy = -r; while (dy <= r) { if (tinta(cx + dx, cy + dy)) return false; dy += 3 }; dx += 3 }
        // Cuatro bordes continuos (del 20 % al 80 % de cada lado, tolerancia ±2 px)
        fun bordeH(y: Int) = (2..8).all { k -> val x = cx - medio + 2 * medio * k / 10; (-2..2).any { tinta(x, y + it) } }
        fun bordeV(x: Int) = (2..8).all { k -> val y = cy - medio + 2 * medio * k / 10; (-2..2).any { tinta(x + it, y) } }
        return bordeH(cy - medio) && bordeH(cy + medio) && bordeV(cx - medio) && bordeV(cx + medio)
    }

    /**
     * Busca la fila de puntitos de página: (cuántos, índice del activo) o null. Los puntitos siempre van justo encima del
     * dock, a ~4.8 medios-lados del botón cuadro (medido con los dos tamaños de CarPlay): solo se busca ahí. Así el texto
     * de los widgets (que en modo noche parecía una fila de puntitos) no se confunde.
     */
    fun buscarPuntos(lum: (Int, Int) -> Int, r: IntArray, boton: IntArray): Pair<Int, Int>? {
        val w = r[2] - r[0]
        val (cy, m) = boton[1] to boton[2]
        var y = cy - m * 13 / 2
        val y1 = cy - m * 3
        while (y <= y1) {
            puntos(lum, r[0] + (PUNTOS_X0 * w).toInt(), r[0] + (PUNTOS_X1 * w).toInt(), y, w)?.let { return it }
            y += 2
        }
        return null
    }

    /** Puntitos de página: (cuántos, índice del activo) o null si no hay. */
    fun puntos(lum: (Int, Int) -> Int, x0: Int, x1: Int, y: Int, anchoCarPlay: Int): Pair<Int, Int>? {
        val fila = IntArray(x1 - x0 + 1) { lum(x0 + it, y) }
        val fondo = mediana(fila.toList())
        val minW = maxOf(3, anchoCarPlay * 3 / 1000)
        val maxW = maxOf(minW + 2, anchoCarPlay * 13 / 1000)
        data class Run(val ini: Int, val fin: Int, val contraste: Int)
        val runs = ArrayList<Run>()
        var i = 0
        while (i < fila.size) {
            if (abs(fila[i] - fondo) > 35) {
                var j = i; var huecos = 0; var maxC = 0
                while (j < fila.size && huecos <= 2) {
                    val c = abs(fila[j] - fondo)
                    if (c > 35) { huecos = 0; maxC = maxOf(maxC, c) } else huecos++
                    j++
                }
                val fin = j - huecos - 1
                if (fin - i + 1 in minW..maxW) runs.add(Run(i, fin, maxC))     // lo ancho (líneas del fondo, texto) se ignora
                i = j
            } else i++
        }
        if (runs.size < 2) return null
        // La cadena más larga de manchas igual de separadas (±30 %); lo que quede fuera es fondo
        val centros = runs.map { (it.ini + it.fin) / 2 }
        var mejor: IntRange? = null
        for (a in 0 until runs.size - 1) {
            val paso = centros[a + 1] - centros[a]
            if (paso < minW * 2 || paso > maxW * 4) continue
            var b = a + 1
            while (b + 1 < runs.size && abs(centros[b + 1] - centros[b] - paso) <= paso * 30 / 100) b++
            if (mejor == null || b - a > mejor.last - mejor.first) mejor = a..b
        }
        val cadena = mejor ?: return null
        val sel = runs.subList(cadena.first, cadena.last + 1)
        // Los puntitos de CarPlay van centrados
        val medio = (centros[cadena.first] + centros[cadena.last]) / 2
        if (abs(medio - fila.size / 2) > anchoCarPlay * 4 / 100) return null
        // El activo destaca claramente
        val orden = sel.sortedByDescending { it.contraste }
        if (orden[0].contraste - orden[1].contraste < 30) return null
        return sel.size to sel.indexOf(orden[0])
    }

    /**
     * ¿A qué pestaña de FT150TS cambiar? Solo si antes y después del gesto CarPlay estaba en la misma página de la
     * orilla (o sea, CarPlay no se movió): Widgets + deslizar a la derecha → Tablero; última de Apps + a la izquierda → Moto.
     */
    fun destino(antes: PaginaCarPlay, despues: PaginaCarPlay, dx: Int, dy: Int, anchoCarPlay: Int, duracionMs: Long): Int? {
        // Mínimo 1/8 del ancho (v3.9.1; con 1/5 = 480 px no contaban deslizamientos cortos de ~360 px)
        if (duracionMs > 1_200 || abs(dx) < anchoCarPlay / 8 || abs(dx) < 2 * abs(dy)) return null
        return when {
            dx > 0 && antes == PaginaCarPlay.WIDGETS && despues == PaginaCarPlay.WIDGETS -> PAGINA_TABLERO
            dx < 0 && antes == PaginaCarPlay.ULTIMA_APPS && despues == PaginaCarPlay.ULTIMA_APPS -> PAGINA_MOTO
            else -> null
        }
    }

    /** Toque crudo de la pantalla (fts_ts: 0..10799 × 0..23999, vertical) a pixeles de la pantalla girada. */
    fun aPantalla(rawX: Int, rawY: Int, rotacion: Int, anchoNat: Int = 1080, altoNat: Int = 2400, maxX: Int = 10799, maxY: Int = 23999): Pair<Int, Int> {
        val x = rawX.toLong() * anchoNat / (maxX + 1)
        val y = rawY.toLong() * altoNat / (maxY + 1)
        return when (rotacion) {
            1 -> y.toInt() to (anchoNat - 1 - x).toInt()            // horizontal (botones a la derecha)
            3 -> (altoNat - 1 - y).toInt() to x.toInt()             // horizontal al revés
            2 -> (anchoNat - 1 - x).toInt() to (altoNat - 1 - y).toInt()
            else -> x.toInt() to y.toInt()
        }
    }

    private fun mediana(xs: List<Int>): Int { val s = xs.sorted(); return if (s.isEmpty()) 0 else s[s.size / 2] }
}
