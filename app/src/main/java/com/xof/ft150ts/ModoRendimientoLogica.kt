package com.xof.ft150ts

/** Qué tanto empuje le da el modo rendimiento al teléfono. */
enum class NivelRendimiento { NORMAL, TABLERO, CARPLAY }

/**
 * Lógica pura del modo rendimiento (sin Android: se prueba en LogicaRendimientoTest).
 *
 * Medido en el POCO F4 (Snapdragon 870, HyperOS 1, kernel 4.19 con WALT), 2026-10-22:
 *  - `sched_boost = 1` (el planificador manda las tareas a los núcleos grandes): al cambiar de página el cuadro típico
 *    bajó de 12 a 7 ms y el peor 1 % de 31 a 13 ms; con CarPlay, DiPlay usó 36 % de CPU en vez de 67 %.
 *  - Piso de la GPU (min_freq 525 MHz en vez de 305): con CarPlay el compositor arma la imagen con la GPU (HyperOS pone
 *    una capa de color bajo las ventanas flotantes) y a 305 MHz se le pasaba el tiempo. Con los dos ajustes los cuadros
 *    con tirón de CarPlay bajaron de 25.6 % a 9.2 % (3 rondas). A la app sola la GPU no le ayudó: solo con CarPlay.
 * HyperOS no los regresa solo (probado abriendo apps y cambiando de pestañas).
 */
object ModoRendimientoLogica {
    const val RUTA_BOOST = "/proc/sys/kernel/sched_boost"
    const val RUTA_GPU_MIN = "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"
    const val RUTA_GPU_FRECUENCIAS = "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies"

    /**
     * Nivel según lo que pasa: nada de empuje si está apagado, sin root, con la pantalla apagada (o en otra app), en
     * reposo o con el teléfono caliente (≥ 43 °C o el sistema avisando): ahí manda enfriarse.
     */
    fun nivel(
        activo: Boolean, rootListo: Boolean, enPantalla: Boolean, reposo: Boolean, calor: NivelCalor, carplay: Boolean,
    ): NivelRendimiento = when {
        !activo || !rootListo || !enPantalla || reposo || calor >= NivelCalor.CALIENTE -> NivelRendimiento.NORMAL
        carplay -> NivelRendimiento.CARPLAY
        else -> NivelRendimiento.TABLERO
    }

    /**
     * De la lista de frecuencias de la GPU (Hz, como la da el kernel): (la mínima, el piso para CarPlay = la más baja
     * que llegue al 75 % de la máxima). En el Poco F4: 305 y 525 MHz. null si no se entiende la lista.
     */
    fun frecuenciasGpu(salida: String): Pair<Long, Long>? {
        val f = salida.trim().split(Regex("\\s+"))
            .mapNotNull { it.toLongOrNull() }
            .filter { it in 50_000_000L..3_000_000_000L }
        if (f.size < 2) return null
        val max = f.max()
        val piso = f.filter { it >= max * 3 / 4 }.min()
        return f.min() to piso
    }

    /** Comandos root (fijos, solo números validados) para dejar el teléfono en el nivel [n]. */
    fun comandos(n: NivelRendimiento, gpu: Pair<Long, Long>?): List<String> {
        val l = mutableListOf("echo ${if (n == NivelRendimiento.NORMAL) 0 else 1} > $RUTA_BOOST")
        if (gpu != null) l += "echo ${if (n == NivelRendimiento.CARPLAY) gpu.second else gpu.first} > $RUTA_GPU_MIN"
        return l
    }

    /**
     * Guardián: un proceso root aparte que espera a que la app [pid] deje de existir y entonces regresa todo a la
     * normalidad. Así, si Android cierra la app de golpe, el teléfono no se queda empujado gastando pila.
     */
    fun cmdGuardian(pid: Int, gpu: Pair<Long, Long>?): String {
        require(pid in 1..4_194_304)
        val restaurar = comandos(NivelRendimiento.NORMAL, gpu).joinToString("; ")
        return "setsid sh -c 'while [ -d /proc/$pid ]; do sleep 5; done; $restaurar' </dev/null >/dev/null 2>&1 &"
    }
}
