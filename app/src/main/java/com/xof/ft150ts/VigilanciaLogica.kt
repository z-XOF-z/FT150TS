package com.xof.ft150ts

/** Estados de la vigilancia antirrobo del teléfono. */
enum class EstadoVigilancia { APAGADA, POR_ARMAR, ARMADA, SOSPECHA, ALARMA }

/**
 * Lógica pura del antirrobo del teléfono (se prueba en VigilanciaTest).
 *  - Se arma sola 2 min después de apagar la moto, si nadie mueve el teléfono (si lo mueves, vuelve a contar).
 *  - Armada: un movimiento = sospecha. Si en 20 s no aparece el dueño (llave, carga, desbloqueo) = alarma: envío.
 *  - En alarma, si se sigue moviendo, otro envío cada 2 min, máximo 3.
 *  - El dueño desarma todo en cualquier momento.
 */
object VigilanciaLogica {
    const val ARMAR_MS = 120_000L
    const val GRACIA_MS = 20_000L
    const val REPETIR_MS = 120_000L
    const val MAX_ENVIOS = 3

    data class Estado(val e: EstadoVigilancia = EstadoVigilancia.APAGADA, val desde: Long = 0L, val envios: Int = 0, val ultimoEnvio: Long = 0L)

    /** Resultado de un paso: el estado nuevo y si hay que mandar fotos/audio ahora. */
    data class Paso(val estado: Estado, val enviar: Boolean = false)

    fun armar(ahora: Long) = Estado(EstadoVigilancia.POR_ARMAR, ahora)

    /** Llave, carga de la moto o desbloqueaste el teléfono: eres tú. */
    fun dueno() = Estado()

    fun movimiento(s: Estado, ahora: Long): Paso = when (s.e) {
        EstadoVigilancia.APAGADA, EstadoVigilancia.SOSPECHA -> Paso(s)
        EstadoVigilancia.POR_ARMAR -> Paso(s.copy(desde = ahora))                  // todavía lo traes tú: vuelve a contar
        EstadoVigilancia.ARMADA -> Paso(s.copy(e = EstadoVigilancia.SOSPECHA, desde = ahora))
        EstadoVigilancia.ALARMA ->
            if (s.envios < MAX_ENVIOS && ahora - s.ultimoEnvio >= REPETIR_MS) Paso(s.copy(envios = s.envios + 1, ultimoEnvio = ahora), true)
            else Paso(s)
    }

    fun tick(s: Estado, ahora: Long): Paso = when {
        s.e == EstadoVigilancia.POR_ARMAR && ahora - s.desde >= ARMAR_MS -> Paso(s.copy(e = EstadoVigilancia.ARMADA, desde = ahora))
        s.e == EstadoVigilancia.SOSPECHA && ahora - s.desde >= GRACIA_MS ->
            Paso(s.copy(e = EstadoVigilancia.ALARMA, desde = ahora, envios = s.envios + 1, ultimoEnvio = ahora), true)
        else -> Paso(s)
    }

    /** ¿El acelerómetro dice que lo movieron? [cambio] en m/s² respecto a la gravedad en reposo. */
    fun seMovio(cambio: Float, seguidas: Int) = cambio > 1.2f && seguidas >= 2
}
