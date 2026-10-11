package com.xof.ft150ts

import org.json.JSONArray
import org.json.JSONObject

// ------------------------------------------------------------
// Datos que manda el ESP32 por Bluetooth (ver firmware, sección 29b)
// ------------------------------------------------------------

/** Mensaje "t": llega cada 250 ms. -1 (o -99 en temperatura) = sin dato. */
data class Rapida(
    val vel: Float = -1f,
    val rpm: Int = -1,
    val marcha: Int = -1,        // -1 desconocida, 0 neutral, 1..5
    val vMoto: Float = -1f,
    val gas: Int = -1,
    val temp: Float = -99f,
    val incl: Float = -1f,
    val aFwd: Float = 0f,
    val shift: Int = 7000,       // punto de cambio que calculó el PID
    val ignicion: Boolean = false,
    val motor: Boolean = false,
)

/** Mensaje "s": llega cada 2 s. */
data class Estado(
    val vLipo: Float = -1f,
    val lipoPct: Int = -1,
    val gpsFix: Boolean = false,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val sats: Int = 0,
    val bateria: String = "",
    val corte: Boolean = false,
    val demoEsp: Boolean = false,
    val perfil: Int = 0,         // 0 ECO, 1 SPORT
    val gpsTelefono: Boolean = false,
    val alerta: String = "",
    val iphone: Boolean = false,  // hay un iPhone vinculado a la moto
)

/** Mensaje "a": música del iPhone (por AMS, a través de la moto). */
data class IphoneMusica(
    val on: Boolean = false,
    val sonando: Boolean = false,
    val titulo: String = "",
    val artista: String = "",
    val app: String = "",
    val duracion: Float = 0f,       // s
    val transcurrido: Float = 0f,   // s, al momento de [recibido]
    val volumen: Float = -1f,       // 0..1
    val recibido: Long = 0L,        // SystemClock.elapsedRealtime()
)

/** Mensaje "c": llamada entrante en el iPhone. */
data class IphoneLlamada(val activa: Boolean = false, val de: String = "", val acciones: Boolean = false)

/** Mensaje "x": llega cada 2 s. */
data class ViajeInfo(
    val odometro: Float = 0f,
    val arranques: Long = 0,
    val activo: Boolean = false,
    val km: Float = 0f,
    val velMax: Float = 0f,
    val litros: Float = 0f,
    val hoyKm: Float = 0f,
    val ayerKm: Float = 0f,
    val kmPorLitro: Float = 0f,
    val autonomia: Int = -1,
    val mantVencido: Int = -1,
    val ralenti: Int = 0,
    val pistaMejor: Float = 0f,
)

/** Respuesta del comando "cfgjson" (firmware v1.3+): ajustes e información de la tarjeta. */
data class ConfigMoto(
    val fw: String = "",
    val encendidaS: Long = 0,     // segundos desde que prendió la tarjeta
    val memoriaKb: Int = 0,
    val tempChip: Float = 0f,
    val perfil: Int = 0,
    val fuenteVel: Int = 0,       // 0 auto, 1 sensor (hall), 2 GPS
    val autoBrillo: Boolean = true,
    val wifi: Boolean = true,
    val kmPorLitro: Float = 0f,
    val factorBat: Float = 1f,
    val gasVacioMv: Int = 0,
    val gasLlenoMv: Int = 0,
    val demo: Boolean = false,
    val rtc: Boolean = false,
    val imu: Boolean = false,
    val sd: Boolean = false,
    val sensorTemp: Boolean = false,
    val pistaMejor: Float = 0f,
    val shift: List<Int> = emptyList(),     // punto de cambio aprendido por marcha
    val relacion: List<Float> = emptyList(), // rpm por km/h en cada marcha
)

/** Respuesta del comando "mantjson". */
data class MantItem(
    val nombre: String,
    val km: Int,
    val intKm: Int,
    val dias: Int,
    val intDias: Int,
    val vencido: Boolean,
)

object Protocolo {

    fun parseRapida(j: JSONObject) = Rapida(
        vel = j.optDouble("v", -1.0).toFloat(),
        rpm = j.optInt("r", -1),
        marcha = j.optInt("m", -1),
        vMoto = j.optDouble("b", -1.0).toFloat(),
        gas = j.optInt("g", -1),
        temp = j.optDouble("t", -99.0).toFloat(),
        incl = j.optDouble("i", -1.0).toFloat(),
        aFwd = j.optDouble("a", 0.0).toFloat(),
        shift = j.optInt("sp", 7000),
        ignicion = j.optInt("ig", 0) == 1,
        motor = j.optInt("me", 0) == 1,
    )

    fun parseEstado(j: JSONObject) = Estado(
        vLipo = j.optDouble("l", -1.0).toFloat(),
        lipoPct = j.optInt("lp", -1),
        gpsFix = j.optInt("f", 0) == 1,
        lat = j.optDouble("la", 0.0),
        lon = j.optDouble("lo", 0.0),
        sats = j.optInt("sa", 0),
        bateria = j.optString("es", ""),
        corte = j.optInt("c", 0) == 1,
        demoEsp = j.optInt("d", 0) == 1,
        perfil = j.optInt("pf", 0),
        gpsTelefono = j.optInt("ge", 0) == 1,
        alerta = j.optString("al", ""),
        iphone = j.optInt("ip", 0) == 1,
    )

    fun parseIphoneMusica(j: JSONObject, ahora: Long) = IphoneMusica(
        on = j.optInt("on", 0) == 1,
        sonando = j.optInt("pl", 0) == 1,
        titulo = j.optString("ti", ""),
        artista = j.optString("ar", ""),
        app = j.optString("ap", ""),
        duracion = j.optDouble("du", 0.0).toFloat(),
        transcurrido = j.optDouble("el", 0.0).toFloat(),
        volumen = j.optDouble("vo", -1.0).toFloat(),
        recibido = ahora,
    )

    fun parseIphoneLlamada(j: JSONObject) = IphoneLlamada(
        activa = j.optInt("on", 0) == 1,
        de = j.optString("de", ""),
        acciones = j.optInt("ac", 0) == 1,
    )

    fun parseViaje(j: JSONObject) = ViajeInfo(
        odometro = j.optDouble("o", 0.0).toFloat(),
        arranques = j.optLong("ar", 0),
        activo = j.optInt("va", 0) == 1,
        km = j.optDouble("vk", 0.0).toFloat(),
        velMax = j.optDouble("vm", 0.0).toFloat(),
        litros = j.optDouble("vl", 0.0).toFloat(),
        hoyKm = j.optDouble("hk", 0.0).toFloat(),
        ayerKm = j.optDouble("yk", 0.0).toFloat(),
        kmPorLitro = j.optDouble("kl", 0.0).toFloat(),
        autonomia = j.optInt("au", -1),
        mantVencido = j.optInt("mv", -1),
        ralenti = j.optInt("ra", 0),
        pistaMejor = j.optDouble("pm", 0.0).toFloat(),
    )

    fun parseConfig(texto: String): ConfigMoto? = try {
        val o = JSONObject(texto.trim())
        val sh = o.optJSONArray("sh")
        val rm = o.optJSONArray("rm")
        ConfigMoto(
            fw = o.optString("fw", ""),
            encendidaS = o.optLong("up", 0L),
            memoriaKb = o.optInt("hp", 0),
            tempChip = o.optDouble("tc", 0.0).toFloat(),
            perfil = o.optInt("pf", 0),
            fuenteVel = o.optInt("fv", 0),
            autoBrillo = o.optInt("ab", 1) == 1,
            wifi = o.optInt("wf", 1) == 1,
            kmPorLitro = o.optDouble("kl", 0.0).toFloat(),
            factorBat = o.optDouble("bf", 1.0).toFloat(),
            gasVacioMv = o.optInt("gv", 0),
            gasLlenoMv = o.optInt("gl", 0),
            demo = o.optInt("dm", 0) == 1,
            rtc = o.optInt("rtc", 0) == 1,
            imu = o.optInt("imu", 0) == 1,
            sd = o.optInt("sd", 0) == 1,
            sensorTemp = o.optInt("tp", 0) == 1,
            pistaMejor = o.optDouble("pm", 0.0).toFloat(),
            shift = if (sh == null) emptyList() else (0 until sh.length()).map { sh.optInt(it, 0) },
            relacion = if (rm == null) emptyList() else (0 until rm.length()).map { rm.optDouble(it, 0.0).toFloat() },
        )
    } catch (e: Exception) {
        null
    }

    fun parseMant(texto: String): List<MantItem>? = try {
        val a = JSONArray(texto.trim())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            MantItem(
                nombre = o.optString("n", "?"),
                km = o.optInt("k", 0),
                intKm = o.optInt("ik", 1),
                dias = o.optInt("d", -1),
                intDias = o.optInt("id", 1),
                vencido = o.optInt("v", 0) == 1,
            )
        }
    } catch (e: Exception) {
        null
    }
}
