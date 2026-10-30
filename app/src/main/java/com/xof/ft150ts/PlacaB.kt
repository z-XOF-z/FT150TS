package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlin.math.log10

/**
 * Lo de la placa base B (firmware v1.6+): relevadores 2 y 3 y la salida aislada (con el nombre que les pongas),
 * la alarma de movimiento, la IA de vibraciones y la luz del teléfono (la tarjeta ya no tiene LDR: J11 lleva la
 * INT del MPU).
 */
object PlacaB {
    private val NOMBRES_FABRICA = listOf("Relevador 2", "Relevador 3", "Salida aislada")

    /** Nombre de cada salida: relevador 2, relevador 3 y salida aislada (lo eliges tú: "Luces", "Claxon"...). */
    val nombres = MutableStateFlow(NOMBRES_FABRICA)
    val vibra = MutableStateFlow<VibraEstado?>(null)
    val salud = MutableStateFlow<List<ComponenteSalud>>(emptyList())

    private var prefs: SharedPreferences? = null
    private val CODIGO = listOf("2", "3", "s")

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("placa", Context.MODE_PRIVATE)
        prefs = p
        nombres.value = NOMBRES_FABRICA.mapIndexed { i, n -> p.getString("nombre$i", n) ?: n }
    }

    fun ponerNombre(i: Int, nombre: String) {
        val n = nombre.trim().take(16).ifBlank { NOMBRES_FABRICA[i] }
        nombres.value = nombres.value.toMutableList().also { it[i] = n }
        prefs?.edit()?.putString("nombre$i", n)?.apply()
    }

    /** [i]: 0 = relevador 2, 1 = relevador 3, 2 = salida. [segundos] > 0 = se apaga solo. */
    fun salida(i: Int, on: Boolean, segundos: Int = 0) {
        val extra = if (on && segundos > 0) " $segundos" else ""
        Moto.comandoSilencioso("rele ${CODIGO[i]} ${if (on) "on" else "off"}$extra")
    }

    fun alarma(activa: Boolean) = Moto.comandoSilencioso("alarma ${if (activa) "on" else "off"}")
    fun alarmaSens(n: Int) = Moto.comandoSilencioso("alarma sens ${n.coerceIn(1, 5)}")
    /** 0 nada, 2/3 relevador, 4 salida. */
    fun alarmaSirena(s: Int) = Moto.comandoSilencioso("alarma sirena ${when (s) { 2 -> "2"; 3 -> "3"; 4 -> "s"; else -> "0" }}")
    fun alarmaPrueba() = Moto.comandoSilencioso("alarma prueba")

    fun vibraActiva(on: Boolean) = Moto.comandoSilencioso("vibra ${if (on) "on" else "off"}")
    fun vibraCaptura(on: Boolean) = Moto.comandoSilencioso("vibra captura ${if (on) "on" else "off"}")
    fun vibraOlvidar() = Moto.comandoSilencioso("vibra olvidar")
    fun pedirVibra() = Moto.comandoSilencioso("vibra")
    fun pedirSalud() = Moto.comandoSilencioso("salud")
    fun llave(on: Boolean) = Moto.comandoSilencioso("llave ${if (on) "on" else "off"}")

    /** Respuestas JSON de "rele", "alarma" y "vibra" (las manda Moto). true = era de aquí. */
    fun recibir(t: String): Boolean {
        when {
            t.startsWith("{\"rl\"") -> Protocolo.parseReles(t)?.let { r -> Moto.config.value = Moto.config.value?.copy(reles = r) }
            t.startsWith("{\"alm\"") -> try {
                val o = JSONObject(t)
                Moto.config.value = Moto.config.value?.copy(
                    alarma = o.optInt("alm", 0) == 1, alarmaSens = o.optInt("as", 3), alarmaSirena = o.optInt("ar", 0),
                    alarmaPosible = o.optInt("ok", 0) == 1,
                )
            } catch (_: Exception) { }
            t.startsWith("{\"salud\"") -> Protocolo.parseSalud(t)?.let { salud.value = it }
            t.startsWith("{\"ll\"") -> try {
                Moto.config.value = Moto.config.value?.copy(llave = JSONObject(t).optInt("ll", 0) == 1)
            } catch (_: Exception) { }
            t.startsWith("{\"vb\"") -> Protocolo.parseVibra(t)?.let { v ->
                vibra.value = v
                Moto.config.value = Moto.config.value?.copy(vibra = v.activa, vibraCaptura = v.captura, vibraMaduros = v.maduros)
            }
            else -> return false
        }
        return true
    }

    /** Lux del teléfono → 0-100 como lo leía la LDR (escala logarítmica: 10 000 lux, pleno sol = 100). */
    fun luzPorciento(lux: Float): Int = if (lux < 0f) -1 else (log10(lux + 1f) / 4f * 100f).toInt().coerceIn(0, 100)

    /** Avisos al cambiar la telemetría: direccional olvidada y vibración rara (una vez cada que empiezan). */
    fun alRecibir(antes: Rapida, ahora: Rapida) {
        if (ahora.dirOlvidada && !antes.dirOlvidada) Moto.avisoLocal("Direccional prendida", "Ojo: se te quedó prendida la direccional")
        if (ahora.vibraAlerta && !antes.vibraAlerta) {
            Moto.avisoLocal("Vibración rara en la moto", "La moto está vibrando diferente. Cuando te detengas revisa la cadena, las llantas y los tornillos.")
        }
    }
}
