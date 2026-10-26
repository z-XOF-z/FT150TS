package com.xof.ft150ts

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parte pura del ahorro nocturno (sin Android): horario, cuándo posponer, la lista cerrada de comandos root y el
 * registro de lo que se cambió. Se prueba en NocturnoTest.
 */

/**
 * Horario de ahorro. [inicio] y [fin] en minutos desde las 00:00 (0..1439). Si [fin] <= [inicio] cruza la
 * medianoche (p. ej. 23:00 → 06:30). La noche "pertenece" al día en que empieza: [dias] usa 1 = lunes … 7 = domingo.
 */
data class Horario(val inicio: Int = 0, val fin: Int = 6 * 60 + 30, val dias: Set<Int> = (1..7).toSet()) {
    val valido: Boolean get() = inicio in 0..1439 && fin in 0..1439 && inicio != fin && dias.isNotEmpty() && dias.all { it in 1..7 }
    val cruzaMedianoche: Boolean get() = fin <= inicio
    /** Duración en minutos. */
    val minutos: Int get() = if (cruzaMedianoche) 1440 - inicio + fin else fin - inicio

    private fun hora(min: Int) = LocalTime.of(min / 60, min % 60)

    /** Inicio y fin de la noche que empieza el día [d] (con la zona de [ref]). Los cambios de horario los ajusta java.time. */
    private fun ventana(d: LocalDate, ref: ZonedDateTime): Pair<ZonedDateTime, ZonedDateTime> {
        val ini = ZonedDateTime.of(d, hora(inicio), ref.zone)
        val fin = ZonedDateTime.of(if (cruzaMedianoche) d.plusDays(1) else d, hora(fin), ref.zone)
        return ini to fin
    }

    /** La noche en curso a [ahora], o null si no toca ahorrar. */
    fun ventanaActual(ahora: ZonedDateTime): Pair<ZonedDateTime, ZonedDateTime>? {
        if (!valido) return null
        val hoy = ahora.toLocalDate()
        for (d in listOf(hoy.minusDays(1), hoy)) {
            if (d.dayOfWeek.value !in dias) continue
            val (i, f) = ventana(d, ahora)
            if (!ahora.isBefore(i) && ahora.isBefore(f)) return i to f
        }
        return null
    }

    fun contiene(ahora: ZonedDateTime) = ventanaActual(ahora) != null

    /** Siguiente inicio después de [ahora] (null si el horario no es válido). */
    fun proximoInicio(ahora: ZonedDateTime): ZonedDateTime? {
        if (!valido) return null
        val hoy = ahora.toLocalDate()
        for (k in 0..8) {
            val d = hoy.plusDays(k.toLong())
            if (d.dayOfWeek.value !in dias) continue
            val (i, _) = ventana(d, ahora)
            if (i.isAfter(ahora)) return i
        }
        return null
    }

    companion object {
        val NOMBRES_DIAS = listOf("L", "M", "M", "J", "V", "S", "D")
        fun texto(min: Int) = "%02d:%02d".format(min / 60, min % 60)
        fun diaDe(d: DayOfWeek) = d.value
    }
}

/** Qué se hace en la noche. Cada una se puede prender o apagar; las de root se marcan. */
enum class AccionNoche(val nombre: String, val root: Boolean, val explicacion: String) {
    APP_SUENO("Dormir FT150TS", false,
        "GPS, sensores, dashcam y la búsqueda rápida de la tarjeta se apagan toda la noche (sin revisiones de ubicación)."),
    SINCRONIZAR("Pausar sincronización automática", false,
        "Las cuentas (Google, correo, contactos) no sincronizan solas. Llamadas, SMS y alarmas siguen igual. Al terminar se regresa como estaba."),
    AHORRO("Ahorro de batería de Android", true,
        "El modo ahorro del sistema (menos procesos en segundo plano, sin animaciones). Si ya lo tenías prendido, no se toca."),
    DOZE("Doze profundo con pantalla apagada", true,
        "Manda el teléfono directo al reposo profundo de Android cuando la pantalla está apagada. Las alarmas del reloj " +
            "y las llamadas siguen llegando; las notificaciones de apps pueden llegar con retraso. Si prendes la pantalla se quita."),
    RESTRINGIR("Restringir apps elegidas", true,
        "Pone las apps que elijas en el grupo \"restringido\" de Android: casi no corren en segundo plano. Al terminar regresan a su grupo de antes."),
    CERRAR("Cerrar apps elegidas", true,
        "Termina los procesos en segundo plano de las apps que elijas (como lo haría Android al faltar memoria). No borra datos ni quita sus alarmas."),
    CERRAR_DIPLAY("Cerrar DiPlay si no hay CarPlay", true,
        "Solo si la pantalla de CarPlay de DiPlay no está abierta. Su receptor de Bluetooth sigue: al conectar el iPhone puede arrancar solo."),
    DATOS("Apagar datos móviles", true,
        "Sin internet por datos: no llegan WhatsApp ni correos hasta que termine el horario. Llamadas y SMS sí llegan. Se regresa como estaba."),
    WIFI("Apagar Wi-Fi", true,
        "Sin internet por Wi-Fi. Ojo: CarPlay inalámbrico usa Wi-Fi. Se regresa como estaba."),
}

/** Perfiles: qué acciones trae cada uno de fábrica (luego se pueden cambiar una por una). */
enum class PerfilNoche(val nombre: String, val acciones: Set<AccionNoche>) {
    CONSERVADOR("Conservador", setOf(AccionNoche.APP_SUENO, AccionNoche.AHORRO, AccionNoche.DOZE)),
    EXTREMO("Extremo", setOf(AccionNoche.APP_SUENO, AccionNoche.SINCRONIZAR, AccionNoche.AHORRO, AccionNoche.DOZE,
        AccionNoche.RESTRINGIR, AccionNoche.CERRAR, AccionNoche.CERRAR_DIPLAY)),
    // DATOS y WIFI nunca vienen prendidas de fábrica: cortan mensajes; se prenden a mano
}

/** Un cambio hecho (o intentado) en la noche: con qué se deshace. */
data class CambioNoche(
    val accion: AccionNoche,
    /** Paquete, si la acción es por app. */
    val objetivo: String = "",
    /** Valor de antes (para regresar). Vacío si no hay nada que regresar. */
    val anterior: String = "",
    val ok: Boolean,
    val detalle: String = "",
    /** Ya se deshizo (o no hacía falta). */
    val revertido: Boolean = false,
)

/** Una noche de ahorro: cuánto gastó la batería (para comparar perfiles). */
data class NocheRegistro(
    val desde: Long, val hasta: Long, val pctIni: Int, val pctFin: Int,
    val perfil: String, val cargo: Boolean, val prueba: Boolean = false,
) {
    /** % por hora (positivo = gastó). null si estuvo cargando, duró muy poco o faltan datos. */
    val gastoHora: Float? get() = LogicaNoche.gastoPorHora(pctIni, pctFin, hasta - desde, cargo)
}

object LogicaNoche {
    /** % de batería por hora; null si no se puede comparar (cargando, menos de 20 min o porcentajes inválidos). */
    fun gastoPorHora(pctIni: Int, pctFin: Int, ms: Long, cargo: Boolean): Float? {
        if (cargo || ms < 20 * 60_000L || pctIni !in 0..100 || pctFin !in 0..100) return null
        return (pctIni - pctFin) / (ms / 3_600_000f)
    }

    fun nochesAJson(l: List<NocheRegistro>): String = JSONArray().apply {
        l.forEach { n -> put(JSONObject().put("d", n.desde).put("h", n.hasta).put("i", n.pctIni).put("f", n.pctFin)
            .put("p", n.perfil).put("c", n.cargo).put("t", n.prueba)) }
    }.toString()

    fun nochesDeJson(s: String?): List<NocheRegistro> {
        if (s.isNullOrBlank()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                NocheRegistro(o.optLong("d"), o.optLong("h"), o.optInt("i", -1), o.optInt("f", -1),
                    o.optString("p").take(20), o.optBoolean("c"), o.optBoolean("t")).takeIf { it.hasta > it.desde }
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Agrega una noche y se queda con las últimas [max]. */
    fun agregarNoche(l: List<NocheRegistro>, n: NocheRegistro, max: Int = 14): List<NocheRegistro> = (l + n).takeLast(max)

    /** Mismo filtro de paquetes que Root: nada de espacios, comillas ni ; */
    private val PAQUETE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    /** Grupos de espera de Android que se aceptan (am set-standby-bucket). */
    val GRUPOS = mapOf(10 to "active", 20 to "working_set", 30 to "frequent", 40 to "rare", 45 to "restricted")

    fun paqueteValido(p: String) = p.length <= 200 && PAQUETE.matches(p)

    /** Paquetes que nunca se restringen ni se cierran, elija lo que elija el usuario. */
    fun protegidoSiempre(p: String, propio: String, extra: Set<String>): Boolean =
        p == propio || p in CarPlay.PAQUETES || p in extra || p in SISTEMA_PROTEGIDO ||
            SISTEMA_PREFIJOS.any { p.startsWith(it) }

    private val SISTEMA_PROTEGIDO = setOf(
        "android", "com.android.phone", "com.android.systemui", "com.android.settings", "com.android.server.telecom",
        "com.android.deskclock", "com.google.android.deskclock", "com.android.alarmclock", "com.miui.securitycenter",
        "com.topjohnwu.magisk", "io.github.huskydg.magisk", "me.weishu.kernelsu", "com.google.android.gms",
    )
    private val SISTEMA_PREFIJOS = listOf("com.android.providers.", "com.qualcomm.", "com.qti.", "vendor.")

    // ---- Comandos (lista cerrada; los parámetros se validan antes) ----

    fun cmdLeerAhorro() = "settings get global low_power"
    fun cmdAhorro(on: Boolean) = "cmd power set-mode ${if (on) 1 else 0}"
    fun cmdDoze(on: Boolean) = if (on) "dumpsys deviceidle force-idle" else "dumpsys deviceidle unforce"
    fun cmdLeerGrupo(p: String): String { require(paqueteValido(p)); return "am get-standby-bucket $p" }
    fun cmdGrupo(p: String, grupo: String): String {
        require(paqueteValido(p) && grupo in GRUPOS.values)
        return "am set-standby-bucket $p $grupo"
    }
    fun cmdCerrar(p: String): String { require(paqueteValido(p)); return "am kill $p" }
    fun cmdLeerDatos() = "settings get global mobile_data"
    fun cmdDatos(on: Boolean) = "svc data ${if (on) "enable" else "disable"}"
    fun cmdLeerWifi() = "settings get global wifi_on"
    fun cmdWifi(on: Boolean) = "svc wifi ${if (on) "enable" else "disable"}"
    /** ¿Está abierta la pantalla de CarPlay de DiPlay? (solo lee) */
    fun cmdCarPlayAbierto() = "dumpsys activity activities | grep -c 'com.shilapi.xcertplay.CarPlayHostActivity'"

    /** Lee el número de grupo de "am get-standby-bucket" (p. ej. "10" o "Bucket: 10"). */
    fun leerGrupo(salida: String): Int? = Regex("(\\d+)").find(salida.trim())?.value?.toIntOrNull()

    /** "1"/"0" de settings (también "null" o vacío = no se sabe). */
    fun leerBool(salida: String): Boolean? = when (salida.trim().lineSequence().lastOrNull()?.trim()) {
        "1" -> true
        "0" -> false
        else -> null
    }

    /**
     * ¿Hay que posponer la entrada? Devuelve el motivo, o null si se puede entrar.
     * [usoMin] = minutos desde que tocaste la pantalla (null = no se sabe).
     */
    fun motivoPosponer(
        rodando: Boolean,
        carPlayReciente: Boolean,
        protegidaHoy: Boolean,
        usoMin: Long?,
        posponerSiUso: Int,
        respetarCarPlay: Boolean,
    ): String? = when {
        protegidaHoy -> "marcaste esta noche como protegida"
        rodando -> "vas rodando o la moto está conectada"
        respetarCarPlay && carPlayReciente -> "hay una sesión de CarPlay en uso"
        posponerSiUso > 0 && usoMin != null && usoMin < posponerSiUso -> "usaste el teléfono hace $usoMin min"
        else -> null
    }

    /** Cuánto se pospone cada vez (sin pasarse del fin de la noche). */
    const val POSPONER_MIN = 15

    // ---- Registro de cambios (se guarda como JSON) ----

    fun aJson(l: List<CambioNoche>): String = JSONArray().apply {
        l.forEach { c ->
            put(JSONObject().put("a", c.accion.name).put("o", c.objetivo).put("p", c.anterior).put("ok", c.ok)
                .put("d", c.detalle).put("r", c.revertido))
        }
    }.toString()

    /** Lee el registro. Lo que no se entienda se descarta (un registro dañado no debe trabar la app). */
    fun deJson(s: String?): List<CambioNoche> {
        if (s.isNullOrBlank()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val acc = AccionNoche.entries.firstOrNull { it.name == o.optString("a") } ?: return@mapNotNull null
                val obj = o.optString("o")
                if (obj.isNotEmpty() && !paqueteValido(obj)) return@mapNotNull null
                CambioNoche(acc, obj, o.optString("p").take(40), o.optBoolean("ok"), o.optString("d").take(200), o.optBoolean("r"))
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Lo que falta deshacer: cambios hechos con éxito y no revertidos. */
    fun pendientes(l: List<CambioNoche>) = l.filter { it.ok && !it.revertido }
}
