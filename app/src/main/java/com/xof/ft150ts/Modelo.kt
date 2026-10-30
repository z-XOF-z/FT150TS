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
    // Placa B (firmware v1.6+)
    val freno: Boolean = false,          // luz de freno prendida
    val direccional: Boolean = false,    // alguna direccional parpadeando
    val dirOlvidada: Boolean = false,    // lleva rato prendida rodando
    val vibraAlerta: Boolean = false,    // la IA de vibraciones vio algo raro
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
    // Placa B (firmware v1.6+)
    val cargandoCelda: Boolean = false,
    val alarmaArmada: Boolean = false,
    val vibraScore: Float = 0f,   // qué tan raro vibra (desviaciones de lo normal; > 3.5 = raro)
    val frenadas: Long = 0,
)

/** IA de vibraciones de la tarjeta ("vibra", firmware v1.6+). */
data class VibraEstado(
    val activa: Boolean = false,
    val captura: Boolean = false,
    val maduros: Int = 0,      // rangos de 500 rpm que ya conocen tu moto (de 20)
    val ventanas: Long = 0,    // lecturas analizadas desde que prendió
    val score: Float = 0f,
    val alerta: Boolean = false,
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
    val ota: Boolean = false,      // firmware v1.6+ con dos particiones: se puede actualizar desde la app
    val casa: Boolean = false,     // firmware v1.6+: WiFi de casa y Telegram
    // Placa B (firmware v1.6+)
    val placaB: Boolean = false,
    val reles: List<Boolean> = listOf(false, false, false),   // relevador 2, relevador 3, salida aislada
    val alarma: Boolean = false,
    val alarmaSens: Int = 3,       // 1 poca .. 5 mucha
    val alarmaSirena: Int = 0,     // 0 nada, 2/3 relevador, 4 salida
    val alarmaPosible: Boolean = false,   // el MPU y su pin INT están
    val vibra: Boolean = false,
    val vibraCaptura: Boolean = false,
    val vibraMaduros: Int = 0,
    val llave: Boolean = false,    // llave de proximidad prendida (apagada de fábrica)
)

/** Un componente del "estado de la moto" (comando "salud", firmware v1.6+): 0-100 y qué hacer. */
data class ComponenteSalud(val nombre: String, val puntos: Int, val consejo: String)

/** Respuesta del comando "casa" (firmware v1.6+). Las claves nunca salen de la tarjeta: solo si están puestas. */
data class CasaEstado(
    val ssid: String = "",
    val clave: Boolean = false,
    val telegram: Boolean = false,
    val chat: String = "",
    val cadaMin: Int = 15,
    val conectada: Boolean = false,
)

/** Un archivo de registros en la SD de la tarjeta ("sd lista"). */
data class ArchivoSd(val nombre: String, val bytes: Long)

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
        freno = j.optInt("fr", 0) == 1,
        direccional = j.optInt("di", 0) == 1,
        dirOlvidada = j.optInt("do", 0) == 1,
        vibraAlerta = j.optInt("va", 0) == 1,
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
        cargandoCelda = j.optInt("cg", 0) == 1,
        alarmaArmada = j.optInt("aa", 0) == 1,
        vibraScore = j.optDouble("vs", 0.0).toFloat(),
        frenadas = j.optLong("fn", 0),
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
            ota = o.optInt("ota", 0) == 1,
            casa = o.optInt("casa", 0) == 1,
            placaB = o.optString("placa", "") == "B",
            reles = listOf(o.optInt("r2", 0) == 1, o.optInt("r3", 0) == 1, o.optInt("so", 0) == 1),
            alarma = o.optInt("alm", 0) == 1,
            alarmaSens = o.optInt("as", 3),
            alarmaSirena = o.optInt("ar", 0),
            alarmaPosible = o.optInt("aok", 0) == 1,
            vibra = o.optInt("vb", 0) == 1,
            vibraCaptura = o.optInt("vc", 0) == 1,
            vibraMaduros = o.optInt("vm", 0),
            llave = o.optInt("ll", 0) == 1,
        )
    } catch (e: Exception) {
        null
    }

    /** Primer byte del mensaje rápido en binario (firmware v1.6+, después de que la app pide "tel bin"). */
    const val BIN_RAPIDA: Byte = 0xB1.toByte()

    /**
     * Mensaje rápido en binario (18 bytes, little-endian; ver binRapido en el firmware). null si no es uno válido.
     * Mismos valores que el JSON "t": -1 / -99 = sin dato.
     */
    fun parseRapidaBin(b: ByteArray): Rapida? {
        if (b.size < 18 || b[0] != BIN_RAPIDA) return null
        fun i16(k: Int) = ((b[k].toInt() and 0xFF) or (b[k + 1].toInt() shl 8)).toShort().toInt()
        fun u16(k: Int) = (b[k].toInt() and 0xFF) or ((b[k + 1].toInt() and 0xFF) shl 8)
        val vel = i16(1); val rpm = u16(3); val volt = u16(6); val temp = i16(9); val incl = i16(11)
        return Rapida(
            vel = if (vel < 0) -1f else vel / 10f,
            rpm = if (rpm == 0xFFFF) -1 else rpm,
            marcha = b[5].toInt(),
            vMoto = if (volt == 0xFFFF) -1f else volt / 100f,
            gas = b[8].toInt(),
            temp = if (temp <= -990) -99f else temp / 10f,
            incl = if (incl < 0) -1f else incl / 10f,
            aFwd = i16(13) / 100f,
            shift = u16(15),
            ignicion = (b[17].toInt() and 1) != 0,
            motor = (b[17].toInt() and 2) != 0,
            freno = (b[17].toInt() and 4) != 0,
            direccional = (b[17].toInt() and 8) != 0,
            dirOlvidada = (b[17].toInt() and 16) != 0,
            vibraAlerta = (b[17].toInt() and 32) != 0,
        )
    }

    /** Respuesta de "salud": {"salud":[{"c":"Batería","p":85,"t":"..."},...]} */
    fun parseSalud(texto: String): List<ComponenteSalud>? = try {
        val a = JSONObject(texto.trim()).getJSONArray("salud")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { ComponenteSalud(it.optString("c"), it.optInt("p", 0), it.optString("t")) } }
    } catch (e: Exception) {
        null
    }

    /** Respuesta de "rele": {"rl":[relevador2, relevador3, salida]} */
    fun parseReles(texto: String): List<Boolean>? = try {
        val a = JSONObject(texto.trim()).getJSONArray("rl")
        (0 until 3).map { a.optInt(it, 0) == 1 }
    } catch (e: Exception) {
        null
    }

    /** Respuesta de "vibra": {"vb":1,"vc":0,"vm":5,"vn":1234,"vs":1.2,"va":0} */
    fun parseVibra(texto: String): VibraEstado? = try {
        val o = JSONObject(texto.trim())
        if (!o.has("vb")) null else VibraEstado(
            activa = o.optInt("vb", 0) == 1, captura = o.optInt("vc", 0) == 1, maduros = o.optInt("vm", 0),
            ventanas = o.optLong("vn", 0), score = o.optDouble("vs", 0.0).toFloat(), alerta = o.optInt("va", 0) == 1,
        )
    } catch (e: Exception) {
        null
    }

    fun parseCasa(texto: String): CasaEstado? = try {
        val o = JSONObject(texto.trim())
        if (!o.has("casa")) null else CasaEstado(
            ssid = o.optString("ssid", ""),
            clave = o.optInt("clave", 0) == 1,
            telegram = o.optInt("tg", 0) == 1,
            chat = o.optString("chat", "").takeIf { it != "0" } ?: "",
            cadaMin = o.optInt("cada", 15),
            conectada = o.optInt("con", 0) == 1,
        )
    } catch (e: Exception) {
        null
    }

    /** "@SDL [{"n":"viajes.csv","t":1234},...]" */
    fun parseListaSd(texto: String): List<ArchivoSd>? = try {
        val a = JSONArray(texto.removePrefix("@SDL").trim())
        (0 until a.length()).map { i -> a.getJSONObject(i).let { ArchivoSd(it.optString("n"), it.optLong("t", 0)) } }
    } catch (e: Exception) {
        null
    }

    /** Encabezado de un pedazo de archivo: "@SD <archivo> <desde> <bytes> <total>" + "\n" + texto. */
    data class PedazoSd(val archivo: String, val desde: Long, val total: Long, val texto: String)

    fun parsePedazoSd(texto: String): PedazoSd? {
        val salto = texto.indexOf('\n')
        if (!texto.startsWith("@SD ") || salto < 0) return null
        val p = texto.substring(4, salto).trim().split(' ')
        if (p.size < 4) return null
        val n = p[2].toIntOrNull() ?: return null
        val cuerpo = texto.substring(salto + 1)
        return PedazoSd(p[0], p[1].toLongOrNull() ?: return null, p[3].toLongOrNull() ?: return null, cuerpo.take(n))
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
