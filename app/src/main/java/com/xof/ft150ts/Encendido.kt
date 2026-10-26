package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Lo que pasa al abrir y cerrar el switch (v3.4). Lo llama Energia cuando el teléfono entra o sale del "modo moto".
 *  - Música: al apagar la moto se pausa; si la vuelves a prender en menos de [VENTANA_MUSICA_MS] (gasolina, un
 *    mandado), sigue donde iba. Solo reanuda lo que la app pausó (no prende música que tú dejaste parada).
 *  - Saludo por voz (opcional): "Buenas tardes. 27 grados, puede llover en unas 2 horas. Te quedan unos 40 km de
 *    gasolina." Solo si la moto estuvo apagada un buen rato (no cada vez que se apaga en un semáforo).
 * La parte que arma la frase no usa Android: se prueba en EncendidoTest.
 */
object Encendido {
    val musica = MutableStateFlow(true)
    val saludo = MutableStateFlow(false)
    /** Segundos que la música suena bajita al cerrar el switch antes de pausarse (0 = pausa al instante). */
    val atenuarSeg = MutableStateFlow(30)
    /** La música se está atenuando ahora (VolumenAuto no toca el volumen mientras). */
    @Volatile var atenuando = false
        private set

    const val VENTANA_MUSICA_MS = 30 * 60_000L
    const val APAGADA_PARA_SALUDO_MS = 20 * 60_000L

    private var prefs: SharedPreferences? = null
    /** Cuándo pausó la música la app al apagar la moto (0 = no la pausó). */
    private var pausadaPorMi = 0L
    /** Cuándo se apagó la moto por última vez (0 = no se sabe: la app recién arrancó). */
    private var apagadaDesde = 0L

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("modoMoto", Context.MODE_PRIVATE)
        prefs = p
        am = ctx.applicationContext.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        atenuarSeg.value = p.getInt("musicaAtenuar", 30)
        musica.value = p.getBoolean("musicaSwitch", true)
        saludo.value = p.getBoolean("saludoVoz", false)
    }

    fun ponerMusica(on: Boolean) { musica.value = on; prefs?.edit()?.putBoolean("musicaSwitch", on)?.apply() }
    fun ponerAtenuar(seg: Int) { atenuarSeg.value = seg.coerceIn(0, 120); prefs?.edit()?.putInt("musicaAtenuar", atenuarSeg.value)?.apply() }
    fun ponerSaludo(on: Boolean) { saludo.value = on; prefs?.edit()?.putBoolean("saludoVoz", on)?.apply() }

    /** Apagaste la moto (de verdad: no el modo sueño ni un corte corto al dar marcha). */
    fun alApagar(ahora: Long = System.currentTimeMillis()) {
        apagadaDesde = ahora
        ZonaWifi.alApagar()
        if (musica.value && MusicaManager.musica.value.reproduciendo) {
            if (atenuarSeg.value <= 0 || am == null) { MusicaManager.pausar(); pausadaPorMi = ahora }
            else atenuarYPausar()
        } else pausadaPorMi = 0L
    }

    /** Abriste el switch. */
    fun alPrender(ahora: Long = System.currentTimeMillis()) {
        // Abriste antes de que terminara la atenuación: regresa el volumen y la música sigue
        if (atenuando) { atenuacion?.cancel(); restaurarVolumen(); pausadaPorMi = 0L }
        ZonaWifi.alPrender()
        BluetoothAdmin.alPrender()
        if (musica.value && debeReanudar(pausadaPorMi, ahora)) MusicaManager.reproducir()
        pausadaPorMi = 0L
        if (saludo.value && tocaSaludo(apagadaDesde, ahora)) {
            val c = ClimaManager.clima.value
            // El clima se usa solo si es reciente (menos de 3 h); si no, el saludo no inventa la temperatura
            val climaOk = c != null && ahora - c.hora < 3 * 3_600_000L
            val hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            Voz.decir(frase(hora, if (climaOk) c!!.temp else null, if (climaOk) c!!.lluviaEnHoras else -1,
                Gasolina.resumen.value.autonomia))
        }
    }

    // ---- Atenuar al cerrar el switch (v3.8) ----
    private var am: android.media.AudioManager? = null
    private var atenuacion: kotlinx.coroutines.Job? = null
    private var volAntes = -1
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    /**
     * Baja la música suave (en ~3 s) a un 30 % de su volumen, la deja así [atenuarSeg] s y luego la pausa y regresa
     * el volumen como estaba (para que al volver a darle play no suene bajito). Si abres el switch antes, regresa.
     */
    private fun atenuarYPausar() {
        val a = am ?: return
        atenuacion?.cancel()
        atenuando = true
        volAntes = a.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val bajo = maxOf(1, (volAntes * 0.3f).toInt())
        atenuacion = scope.launch {
            try {
                var v = volAntes
                while (v > bajo) { v--; poner(v); kotlinx.coroutines.delay(3_000L / maxOf(1, volAntes - bajo)) }
                kotlinx.coroutines.delay(atenuarSeg.value * 1000L)
                MusicaManager.pausar()
                pausadaPorMi = System.currentTimeMillis()
                kotlinx.coroutines.delay(600)                 // ya en pausa: regresa el volumen sin que se oiga
                restaurarVolumen()
            } finally { atenuando = false }
        }
    }

    private fun poner(v: Int) { try { am?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, v, 0) } catch (_: Exception) { } }

    private fun restaurarVolumen() {
        if (volAntes >= 0) poner(volAntes)
        volAntes = -1
        atenuando = false
    }

    fun debeReanudar(pausada: Long, ahora: Long) = pausada > 0L && ahora - pausada in 0..VENTANA_MUSICA_MS

    /** Saluda si la moto estuvo apagada un buen rato, o la primera vez desde que arrancó la app. */
    fun tocaSaludo(apagada: Long, ahora: Long) = apagada == 0L || ahora - apagada >= APAGADA_PARA_SALUDO_MS

    /** El saludo: hora del día, temperatura, lluvia que viene y gasolina (solo si te queda poca). */
    fun frase(hora: Int, temp: Int?, lluviaEnHoras: Int, autonomiaKm: Int): String {
        val partes = ArrayList<String>()
        partes += when (hora) { in 5..11 -> "Buenos días."; in 12..18 -> "Buenas tardes."; else -> "Buenas noches." }
        if (temp != null) {
            partes += when {
                lluviaEnHoras == 0 -> "$temp grados y está lloviendo, maneja con cuidado."
                lluviaEnHoras in 1..3 -> "$temp grados, puede llover en ${horas(lluviaEnHoras)}."
                else -> "$temp grados."
            }
        }
        if (autonomiaKm in 0..60) partes += "Te quedan unos $autonomiaKm kilómetros de gasolina."
        return partes.joinToString(" ")
    }

    private fun horas(n: Int) = if (n == 1) "una hora" else "unas $n horas"
}
