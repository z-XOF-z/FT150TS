package com.xof.ft150ts

import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Estado de las funciones avanzadas que guarda la tarjeta (respuesta del comando "av", firmware v1.5+). */
data class EstadoAvanzado(
    val hwCorte: Boolean = false,      // la tarjeta tiene el corte de chispa compilado
    val hwQs: Boolean = false,         // y el sensor de la palanca
    val limitador: Boolean = false,
    val limRpm: Int = 9500,
    val launch: Boolean = false,
    val launchRpm: Int = 6000,
    val quickshifter: Boolean = false,
    val qsMs: Int = 60,
    val qsRpm: Int = 4500,
    val diagnostico: Boolean = false,
)

/** Mensaje "d" (cada 3 s con el diagnóstico prendido). */
data class Diagnostico(
    val chispasFaltantes: Long = 0,
    val pulsos: Long = 0,
    val irregularidad: Int = 0,          // variación entre chispas seguidas, por mil
    val arranques: List<Float> = emptyList(),   // voltaje mínimo al dar marcha, del más viejo al más nuevo
    val arranqueDebil: Boolean = false,
    val carga: Int = 0,                  // 0 sin dato, 1 bien, 2 no carga, 3 sobrecarga
    val ralenti: Int = 0,
    val cortesLimitador: Long = 0,
    val cortesQs: Long = 0,
    val launchArmado: Boolean = false,
)

object ProtocoloAvanzado {
    const val LIM_MIN = 6000; const val LIM_MAX = 11000
    const val LC_MIN = 3000; const val LC_MAX = 8000
    const val QS_MS_MIN = 30; const val QS_MS_MAX = 100
    const val QS_RPM_MIN = 3000; const val QS_RPM_MAX = 9000
    /** Abajo de esto la batería está floja (se ve al dar marcha). */
    const val ARRANQUE_DEBIL_V = 9.6f

    fun esEstado(texto: String) = texto.startsWith("{") && texto.contains("\"av\"")

    fun parseEstado(texto: String): EstadoAvanzado? = try {
        val j = JSONObject(texto)
        EstadoAvanzado(
            hwCorte = j.optInt("hw") == 1, hwQs = j.optInt("qsh") == 1,
            limitador = j.optInt("lim") == 1, limRpm = j.optInt("lr", 9500),
            launch = j.optInt("lc") == 1, launchRpm = j.optInt("lcr", 6000),
            quickshifter = j.optInt("qs") == 1, qsMs = j.optInt("qm", 60), qsRpm = j.optInt("qr", 4500),
            diagnostico = j.optInt("dg") == 1,
        )
    } catch (_: Exception) { null }

    fun parseDiagnostico(j: JSONObject): Diagnostico {
        val a = j.optJSONArray("ar")
        val arr = if (a == null) emptyList() else (0 until a.length()).map { a.optDouble(it).toFloat() }.filter { !it.isNaN() }
        return Diagnostico(
            chispasFaltantes = j.optLong("cf"), pulsos = j.optLong("pu"), irregularidad = j.optInt("ir"),
            arranques = arr, arranqueDebil = j.optInt("ad") == 1, carga = j.optInt("ca"), ralenti = j.optInt("ra"),
            cortesLimitador = j.optLong("cl"), cortesQs = j.optLong("cq"), launchArmado = j.optInt("lc") == 1,
        )
    }

    fun cmdLimitador(on: Boolean, rpm: Int) =
        if (on) "av lim on ${rpm.coerceIn(LIM_MIN, LIM_MAX)} confirmar" else "av lim off"
    fun cmdLaunch(on: Boolean, rpm: Int) =
        if (on) "av lc on ${rpm.coerceIn(LC_MIN, LC_MAX)} confirmar" else "av lc off"
    fun cmdQuickshifter(on: Boolean, ms: Int, rpm: Int) =
        if (on) "av qs on ${ms.coerceIn(QS_MS_MIN, QS_MS_MAX)} ${rpm.coerceIn(QS_RPM_MIN, QS_RPM_MAX)} confirmar" else "av qs off"
    fun cmdDiagnostico(on: Boolean) = if (on) "av diag on" else "av diag off"

    /** Chispas que faltaron por cada mil (falla de encendido: bujía, cable, bobina, CDI). */
    fun faltasPorMil(d: Diagnostico): Float = if (d.pulsos <= 0) 0f else d.chispasFaltantes * 1000f / d.pulsos

    /** Cómo va la batería según los últimos arranques. */
    fun veredictoBateria(d: Diagnostico): String {
        val ult = d.arranques.lastOrNull() ?: return "Sin arranques medidos todavía"
        val tendencia = if (d.arranques.size >= 3) d.arranques.last() - d.arranques.first() else 0f
        return when {
            ult < ARRANQUE_DEBIL_V -> "Floja: bajó a %.1f V al dar marcha. Revísala o cárgala".format(ult)
            tendencia < -0.4f -> "Va bajando: %.1f V en el último arranque".format(ult)
            else -> "Bien: %.1f V en el último arranque".format(ult)
        }
    }

    fun veredictoEncendido(d: Diagnostico): String {
        if (d.pulsos < 3000) return "Juntando datos (rueda unos minutos)"
        val f = faltasPorMil(d)
        return when {
            f > 5f -> "Faltan chispas (%.1f por mil): revisa bujía, capuchón y bobina".format(f)
            d.irregularidad > 120 -> "Encendido disparejo: revisa bujía y carburación"
            else -> "Parejo (%.1f por mil)".format(f)
        }
    }
}

/** Lo que la app sabe de las funciones avanzadas de la tarjeta. Todo apagado mientras no se prenda. */
object FuncionesAvanzadas {
    /** null = la tarjeta no respondió "av" (firmware viejo o sin conexión). */
    val estado = MutableStateFlow<EstadoAvanzado?>(null)
    val diagnostico = MutableStateFlow<Diagnostico?>(null)

    fun pedirEstado() = Moto.comandoSilencioso("av")

    fun recibirEstado(texto: String): Boolean {
        val e = ProtocoloAvanzado.parseEstado(texto) ?: return false
        estado.value = e
        if (!e.diagnostico) diagnostico.value = null
        return true
    }

    fun recibirDiagnostico(j: JSONObject) { diagnostico.value = ProtocoloAvanzado.parseDiagnostico(j) }

    /** La respuesta (estado nuevo o el motivo del rechazo) se ve en la consola de Moto. */
    fun enviar(cmd: String) = Moto.comando(cmd)

    fun olvidar() { estado.value = null; diagnostico.value = null }
}
