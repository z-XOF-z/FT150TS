package com.xof.ft150ts

/**
 * Cuándo se fuerza la pantalla prendida (FLAG_KEEP_SCREEN_ON). Lógica pura para poder probarla.
 *
 * Antes "Siempre" la dejaba prendida mientras la app estuviera al frente. Como la app es el launcher, el cajón de
 * apps también es la app: el teléfono se quedaba prendido en la casa o en la bolsa hasta descargarse.
 * Ahora, fuera de la moto, "Siempre" no aplica en el cajón de apps y en las demás páginas se suelta tras
 * [SIN_TOCAR_MS] sin tocarla (de ahí Android la apaga con su tiempo normal).
 */
object PantallaEncendidaLogica {
    /** Fuera de la moto, con "Siempre": cuánto aguanta prendida sin que la toques. */
    const val SIN_TOCAR_MS = 10 * 60_000L

    /**
     * @param enLaMoto conectada a la tarjeta (o demo) o montada con el switch prendido (Modo moto)
     * @param pagina página del launcher que se está viendo
     * @param sinTocarMs cuánto lleva sin que toques la pantalla
     * @param apagadaPorSueno modo sueño, o reposo con "dejar que se apague"
     */
    fun mantener(modo: PantallaEncendida, enLaMoto: Boolean, pagina: Int, sinTocarMs: Long, apagadaPorSueno: Boolean): Boolean {
        if (apagadaPorSueno) return false
        return when (modo) {
            PantallaEncendida.NORMAL -> false
            PantallaEncendida.CON_MOTO -> enLaMoto
            PantallaEncendida.SIEMPRE -> enLaMoto || (pagina != PAGINA_APPS && sinTocarMs < SIN_TOCAR_MS)
        }
    }

    /** Fuera de FT150TS (otra app o pantalla apagada): CarPlay escondido ya no sirve; se cierra tras este rato. */
    const val CARPLAY_FUERA_MS = 60_000L
    /** Con FT150TS a la vista, nunca antes de esto (aunque el teléfono tenga un tiempo de apagado más corto). */
    const val CARPLAY_MIN_MS = 30_000L

    /**
     * ¿Se cierra la ventana de CarPlay escondida? La pantalla de CarPlay de DiPlay pide "pantalla siempre encendida"
     * (FLAG_KEEP_SCREEN_ON) y en el modo Pestaña, escondida, se queda viva fuera de la pantalla: Android la sigue
     * respetando y el teléfono no se apagaba nunca, ni en el cajón de apps (medido: mHoldScreenWindow = CarPlayHostActivity).
     * En la moto se deja (ahí la pantalla va prendida de todos modos y CarPlay regresa al instante).
     * @param apagadoSistemaMs el tiempo de apagado de pantalla del teléfono
     */
    fun cerrarCarPlay(enLaMoto: Boolean, escondida: Boolean, ftVisible: Boolean, sinTocarMs: Long, apagadoSistemaMs: Long): Boolean {
        if (enLaMoto || !escondida) return false
        val limite = if (ftVisible) maxOf(apagadoSistemaMs, CARPLAY_MIN_MS) else CARPLAY_FUERA_MS
        return sinTocarMs >= limite
    }

    /** Si la respuesta puede cambiar sola con el tiempo, en cuántos ms hay que volver a preguntar; si no, null. */
    fun revisarEn(modo: PantallaEncendida, enLaMoto: Boolean, pagina: Int, sinTocarMs: Long, apagadaPorSueno: Boolean): Long? =
        if (!apagadaPorSueno && modo == PantallaEncendida.SIEMPRE && !enLaMoto && pagina != PAGINA_APPS && sinTocarMs < SIN_TOCAR_MS)
            SIN_TOCAR_MS - sinTocarMs
        else null
}
