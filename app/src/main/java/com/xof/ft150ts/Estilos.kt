package com.xof.ft150ts

/**
 * Estilos (v3.4): temas hechos como "recetas" (paleta + forma + decoración + fondo) que dibuja un solo motor
 * (ui/MotorEstilos.kt). Los estilos salen del catálogo de UI UX Pro Max (aurora-ui, neubrutalism, claymorphism,
 * cyberpunk-ui, retro-futurism/vaporwave, minimalism-and-swiss-style, bento-box-grid, dark-mode-oled,
 * organic-biophilic, memphis-design) adaptados a un tablero de moto en pantalla AMOLED: fondo negro, contraste alto
 * y dibujo cacheado. "Mezcla" genera combinaciones nuevas sin fin a partir de una semilla.
 *
 * Aquí no hay nada de Android: se prueba en EstilosTest.
 */

/** Forma de las tarjetas y botones. */
enum class FormaEstilo { REDONDA, RECTA, CORTE, PILDORA, HOJA, ESCALON }

/** Adorno de cada tarjeta. */
enum class DecoEstilo {
    NADA,             // solo el borde
    VIDRIO,           // superficie translúcida con luz arriba (aurora)
    BANDA,            // banda sólida del acento arriba (neubrutalismo)
    ARCILLA,          // luz arriba-izquierda y sombra abajo-derecha (claymorphism)
    GLITCH,           // borde repetido en cian y magenta, desfasado (cyberpunk)
    NEON,             // resplandor del acento secundario (synthwave)
    LINEA_LATERAL,    // una barra del acento a la izquierda, sin borde (suizo)
    BRILLO,           // un filo de luz arriba, sin borde (bento)
    PUNTEADO,         // borde punteado y un garabato en la esquina (memphis)
    DOBLE_FILETE,     // doble línea dorada y abanico en las esquinas (art déco)
    LIBREA,           // franjas de carreras y cuadros (pista)
}

/** Fondo de toda la app. */
enum class FondoEstilo {
    NEGRO,            // negro puro (OLED)
    AURORA,           // tres manchas de luz de color
    SYNTH,            // sol de atardecer con rejilla en perspectiva
    ESCANEO,          // líneas de escaneo y viñeta
    REJILLA,          // rejilla suiza muy tenue
    ONDAS,            // curvas de nivel orgánicas
    MEMPHIS,          // triángulos, círculos y garabatos
    ABANICO,          // abanico art déco desde abajo
    CUADROS,          // bandera a cuadros y franjas de velocidad
    DEGRADADO,        // degradado muy suave (bento)
}

/**
 * Una receta de tema. Los colores van en ARGB (Long) y las medidas en dp. [acento] es el color principal del tema
 * (se usa si "Usar el color del tema" está prendido); [acento2] es el secundario de los adornos.
 */
data class EstiloTema(
    val id: String,
    val nombre: String,
    val detalle: String,
    val acento: Long,
    val acento2: Long,
    val superficie: Long,
    val superficie2: Long,
    val borde: Long,
    val forma: FormaEstilo,
    /** Radio o corte de las tarjetas (dp). */
    val radio: Float,
    /** Grosor del borde (dp); 0 = sin borde. */
    val bordeDp: Float,
    val deco: DecoEstilo,
    val fondo: FondoEstilo,
    /** Estilo de UI UX Pro Max del que sale (para la ficha). */
    val origen: String = "",
)

object Estilos {
    val AURORA = EstiloTema("AURORA", "Aurora", "Auroras y vidrio",
        0xFF4FE3FF, 0xFFC86BFF, 0x1AFFFFFF, 0x26FFFFFF, 0x40FFFFFF,
        FormaEstilo.REDONDA, 22f, 1f, DecoEstilo.VIDRIO, FondoEstilo.AURORA, "aurora-ui + glassmorphism")
    val BLOQUE = EstiloTema("BLOQUE", "Bloque", "Neubrutal, bloques sólidos",
        0xFFFFE03A, 0xFFFF5252, 0xFF141414, 0xFF1E1E1E, 0xFFF5F5F5,
        FormaEstilo.RECTA, 0f, 2.5f, DecoEstilo.BANDA, FondoEstilo.NEGRO, "neubrutalism")
    val ARCILLA = EstiloTema("ARCILLA", "Arcilla", "Suave y esponjoso",
        0xFFFFB4A2, 0xFF9BF6C8, 0xFF23202A, 0xFF2E2A36, 0x00000000,
        FormaEstilo.REDONDA, 28f, 0f, DecoEstilo.ARCILLA, FondoEstilo.DEGRADADO, "claymorphism (oscuro)")
    val CYBER = EstiloTema("CYBER", "Cyber", "Glitch cian y magenta",
        0xFF00F0FF, 0xFFFF2BD6, 0xFF0A0A12, 0xFF141424, 0xFF00F0FF,
        FormaEstilo.CORTE, 14f, 1.5f, DecoEstilo.GLITCH, FondoEstilo.ESCANEO, "cyberpunk-ui")
    val SYNTH = EstiloTema("SYNTH", "Synth", "Atardecer neón de los 80",
        0xFFFF4FD8, 0xFF22D3FF, 0xFF140A24, 0xFF1F1035, 0xFFFF4FD8,
        FormaEstilo.REDONDA, 10f, 1.5f, DecoEstilo.NEON, FondoEstilo.SYNTH, "retro-futurism + vaporwave")
    val SUIZO = EstiloTema("SUIZO", "Suizo", "Minimal, una barra roja",
        0xFFFF3B30, 0xFFFFFFFF, 0xFF0B0B0B, 0xFF161616, 0x00000000,
        FormaEstilo.RECTA, 0f, 0f, DecoEstilo.LINEA_LATERAL, FondoEstilo.REJILLA, "minimalism-and-swiss-style")
    val BENTO = EstiloTema("BENTO", "Bento", "Losas estilo Apple",
        0xFF0A84FF, 0xFF30D158, 0xFF1C1C1E, 0xFF2C2C2E, 0x00000000,
        FormaEstilo.REDONDA, 20f, 0f, DecoEstilo.BRILLO, FondoEstilo.DEGRADADO, "bento-box-grid")
    val NOCHE_ROJA = EstiloTema("NOCHE_ROJA", "Noche roja", "Para manejar de noche",
        0xFFE0352B, 0xFF7A1C17, 0xFF050505, 0xFF0C0909, 0xFF3A1210,
        FormaEstilo.REDONDA, 12f, 1f, DecoEstilo.NADA, FondoEstilo.NEGRO, "dark-mode-oled (visión nocturna)")
    val ORGANICO = EstiloTema("ORGANICO", "Orgánico", "Hojas y curvas de nivel",
        0xFF7DDB6A, 0xFFE0B26B, 0xFF0F1A12, 0xFF17261A, 0xFF2E4A32,
        FormaEstilo.HOJA, 26f, 1f, DecoEstilo.NADA, FondoEstilo.ONDAS, "organic-biophilic")
    val MEMPHIS = EstiloTema("MEMPHIS", "Memphis", "Figuras y garabatos 80s",
        0xFFFF71CE, 0xFFFFCE5C, 0xFF16121C, 0xFF211A2A, 0xFF86CCCA,
        FormaEstilo.REDONDA, 6f, 2f, DecoEstilo.PUNTEADO, FondoEstilo.MEMPHIS, "memphis-design")
    val DECO = EstiloTema("DECO", "Art Déco", "Oro y abanicos",
        0xFFD4AF37, 0xFFF4E3A1, 0xFF0B0A08, 0xFF15130E, 0xFFD4AF37,
        FormaEstilo.ESCALON, 10f, 1f, DecoEstilo.DOBLE_FILETE, FondoEstilo.ABANICO, "dark luxury / art déco")
    val PISTA = EstiloTema("PISTA", "Pista", "Franjas de carreras",
        0xFFFF2D2D, 0xFFFFFFFF, 0xFF0E0E10, 0xFF19191C, 0xFF2C2C30,
        FormaEstilo.CORTE, 12f, 1f, DecoEstilo.LIBREA, FondoEstilo.CUADROS, "livery de carreras (moto)")

    /** v3.10: los colores del tablero Racing Morado (concepto de Andrew) en toda la app. */
    val RACING = EstiloTema("RACING", "Racing Morado", "El morado de tu tablero",
        0xFFC42CFF, 0xFFFF45E8, 0xFF0E0819, 0xFF170D26, 0xFF8D37FF,
        FormaEstilo.REDONDA, 14f, 1.5f, DecoEstilo.NEON, FondoEstilo.NEGRO, "concepto racing HUD de Andrew")

    /** Los temas fijos, en el orden de Ajustes. */
    val TODOS = listOf(RACING, AURORA, BLOQUE, ARCILLA, CYBER, SYNTH, SUIZO, BENTO, NOCHE_ROJA, ORGANICO, MEMPHIS, DECO, PISTA)

    /** Busca un tema fijo por su id (el nombre del TemaApp). */
    fun porId(id: String): EstiloTema? = TODOS.firstOrNull { it.id == id }

    // ---------------- Mezcla: temas infinitos ----------------

    /** Paletas para la mezcla: acento, acento2, superficie, superficie2, borde (todas oscuras, contraste alto). */
    private val PALETAS = listOf(
        longArrayOf(0xFF4FE3FF, 0xFFC86BFF, 0xFF0D1220, 0xFF162034, 0xFF2A3A5C),
        longArrayOf(0xFFFFE03A, 0xFFFF5252, 0xFF141414, 0xFF1E1E1E, 0xFF3A3A3A),
        longArrayOf(0xFF00F0FF, 0xFFFF2BD6, 0xFF0A0A12, 0xFF141424, 0xFF262640),
        longArrayOf(0xFFFF4FD8, 0xFF22D3FF, 0xFF140A24, 0xFF1F1035, 0xFF3A1F5C),
        longArrayOf(0xFF7DDB6A, 0xFFE0B26B, 0xFF0F1A12, 0xFF17261A, 0xFF2E4A32),
        longArrayOf(0xFFD4AF37, 0xFFF4E3A1, 0xFF0B0A08, 0xFF15130E, 0xFF3A3220),
        longArrayOf(0xFFFF8A3D, 0xFF3DDCFF, 0xFF120D0A, 0xFF1F1610, 0xFF3D2A1C),
        longArrayOf(0xFFB6FF3B, 0xFF3BFFD8, 0xFF0B100A, 0xFF141C12, 0xFF2A3A24),
        longArrayOf(0xFF9D7BFF, 0xFFFFB86B, 0xFF0F0B1A, 0xFF1A1430, 0xFF33285A),
        longArrayOf(0xFFFF3B30, 0xFFFFFFFF, 0xFF0B0B0B, 0xFF161616, 0xFF2A2A2A),
        longArrayOf(0xFF30D158, 0xFF0A84FF, 0xFF101412, 0xFF1A201C, 0xFF2C3A30),
        longArrayOf(0xFFFFD60A, 0xFF64D2FF, 0xFF121008, 0xFF1E1A0E, 0xFF3A3418),
    )
    private val RADIOS = floatArrayOf(0f, 6f, 10f, 14f, 20f, 26f)
    private val NOMBRES_A = listOf("Turbo", "Nébula", "Asfalto", "Cromo", "Volcán", "Brisa", "Rayo", "Eclipse", "Óxido", "Ámbar", "Prisma", "Trueno")
    private val NOMBRES_B = listOf("nocturno", "salvaje", "eléctrico", "fantasma", "solar", "lunar", "de pista", "urbano", "boreal", "dorado", "neón", "polar")

    /**
     * Un tema nuevo a partir de [semilla]: siempre el mismo para la misma semilla (se guarda solo el número).
     * Se combinan paleta, forma, radio, decoración, fondo y grosor de borde: 12·6·6·11·10·3 ≈ 285 mil combinaciones
     * y con el nombre, más. Se evitan combinaciones que se ven mal (sin borde y sin decoración, p. ej.).
     */
    fun mezcla(semilla: Long): EstiloTema {
        val r = java.util.Random(semilla)
        val p = PALETAS[r.nextInt(PALETAS.size)]
        val forma = FormaEstilo.entries[r.nextInt(FormaEstilo.entries.size)]
        val radio = if (forma == FormaEstilo.RECTA) 0f else RADIOS[1 + r.nextInt(RADIOS.size - 1)]
        val deco = DecoEstilo.entries[r.nextInt(DecoEstilo.entries.size)]
        val fondo = FondoEstilo.entries[r.nextInt(FondoEstilo.entries.size)]
        var borde = floatArrayOf(0f, 1f, 2f)[r.nextInt(3)]
        // Reglas de buen gusto: algo tiene que marcar la tarjeta
        if (borde == 0f && deco in setOf(DecoEstilo.NADA, DecoEstilo.PUNTEADO)) borde = 1f
        val nombre = NOMBRES_A[r.nextInt(NOMBRES_A.size)] + " " + NOMBRES_B[r.nextInt(NOMBRES_B.size)]
        return EstiloTema("MEZCLA", nombre, "Mezcla #${(semilla and 0xFFFF).toString(16).uppercase()}",
            p[0], p[1], p[2], p[3], p[4], forma, radio, borde, deco, fondo, "mezcla de estilos")
    }

    /** Contraste (WCAG) entre dos colores ARGB: el acento sobre la superficie tiene que leerse en la moto. */
    fun contraste(a: Long, b: Long): Double {
        fun lum(c: Long): Double {
            fun canal(v: Long): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
            return 0.2126 * canal((c shr 16) and 0xFF) + 0.7152 * canal((c shr 8) and 0xFF) + 0.0722 * canal(c and 0xFF)
        }
        val (l1, l2) = lum(a) to lum(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }
}
