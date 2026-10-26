package com.xof.ft150ts

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Qué dice el teléfono sobre encenderse solo estando apagado. */
enum class EncendidoProgramado(val texto: String) {
    DEL_SISTEMA("El sistema trae \"encendido y apagado programado\": úsalo desde los ajustes de Xiaomi (es el camino que soporta el fabricante)."),
    PROBAR("El kernel deja activa la alarma de encendido. Falta comprobar que el arranque la respete: haz la prueba manual de docs/AHORRO_NOCTURNO.md antes de confiar en ella."),
    NO("El kernel apaga la alarma RTC al apagar el teléfono: encenderse solo no es posible sin modificar el kernel o el arranque (no se hace). Se usa el ahorro profundo."),
    SIN_DATOS("No se pudo leer (hace falta root). Sin ese dato no se programa ningún apagado."),
}

/**
 * Diagnóstico de energía y root. Todo es de SOLO LECTURA: comandos fijos, sin escribir nada.
 * También la preparación (reversible) de DiPlay para que Android/MIUI no lo cierre.
 */
object DiagnosticoEnergia {
    val lineas = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val encendido = MutableStateFlow<EncendidoProgramado?>(null)
    val trabajando = MutableStateFlow(false)

    /** Comandos de solo lectura (en orden; los índices se usan abajo). */
    private val LECTURAS = listOf(
        "id -u",
        "magisk -v 2>/dev/null || echo ?",
        "getenforce",
        "uname -r",
        "cat /sys/class/rtc/rtc0/name 2>/dev/null || echo ?",
        "[ -e /sys/class/rtc/rtc0/wakealarm ] && echo si || echo no",
        "cat /sys/module/qpnp_rtc/parameters/poweron_alarm 2>/dev/null || echo ?",
        "f=\$(find /proc/device-tree -name 'qcom,qpnp-rtc-alarm-pwrup' 2>/dev/null | head -1); [ -n \"\$f\" ] && od -An -tx1 \"\$f\" | tr -d ' \\n' || echo ?",
        "dumpsys package com.miui.securitycenter 2>/dev/null | grep -i -o -E 'com\\.miui\\.[A-Za-z.]*(PowerOnOff|ScheduledPower|SchedulePower|AutoTask)[A-Za-z.]*' | sort -u | head -3; echo .",
        "echo \"\$(getprop ro.boot.bootreason) / \$(getprop sys.boot.reason)\"",
        "dumpsys deviceidle whitelist | grep -E 'com.xof.ft150ts|com.shihab.diplay' | tr '\\n' ' '; echo .",
        "am get-standby-bucket com.xof.ft150ts; am get-standby-bucket com.shihab.diplay 2>/dev/null",
        LogicaNoche.cmdCarPlayAbierto(),
    )

    /** Interpreta el RTC. [pwrup] = bytes del device tree (hex, p. ej. "00000001"), [param] = poweron_alarm. */
    fun veredicto(pwrup: String?, param: String?, miui: Boolean, hayRoot: Boolean): EncendidoProgramado {
        if (miui) return EncendidoProgramado.DEL_SISTEMA
        if (!hayRoot) return EncendidoProgramado.SIN_DATOS
        val p = pwrup?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "?" }
        val m = param?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it != "?" }
        val propOn = p != null && p.trimStart('0').isNotEmpty()      // cualquier valor distinto de 0
        val paramOn = m == "Y" || m == "1"
        return when {
            propOn || paramOn -> EncendidoProgramado.PROBAR
            p != null || m != null -> EncendidoProgramado.NO          // se pudo leer y está en 0 / N
            else -> EncendidoProgramado.SIN_DATOS                     // otro driver: no se sabe
        }
    }

    suspend fun correr(ctx: Context) = withContext(Dispatchers.IO) {
        trabajando.value = true
        val l = mutableListOf<Pair<String, String>>()
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        l += "Teléfono" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
        l += "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        l += "HyperOS / MIUI" to listOf(prop("ro.mi.os.version.name"), prop("ro.miui.ui.version.name")).filter { it.isNotBlank() }
            .joinToString(" · ").ifBlank { "no se detectó" }
        l += "Alarmas exactas" to if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) "permitidas" else "NO (la alarma puede llegar minutos tarde)"
        l += "FT150TS sin optimización de batería" to siNo(pm.isIgnoringBatteryOptimizations(ctx.packageName))
        val d = CarPlay.buscar(ctx)
        l += "DiPlay" to (d?.let { "${it.paquete} ${it.version}" } ?: "no instalado")
        if (d != null) l += "DiPlay sin optimización de batería" to siNo(pm.isIgnoringBatteryOptimizations(d.paquete))
        l += "Servicio en primer plano de FT150TS" to siNo(Energia.segundoPlano.value)

        val hayRoot = Root.activo.value && Root.disponible.value == true
        var ver = EncendidoProgramado.SIN_DATOS
        if (hayRoot) {
            val r = Root.ejecutarCada(LECTURAS)
            if (r != null) {
                fun s(i: Int) = r.getOrNull(i)?.salida?.trim()?.lineSequence()?.lastOrNull()?.trim().orEmpty()
                l += "Root (uid)" to s(0)
                l += "Magisk" to s(1)
                l += "SELinux" to s(2) + if (s(2).equals("Enforcing", true)) " (bien: no se toca)" else ""
                l += "Kernel" to s(3)
                l += "RTC" to "${s(4)} · wakealarm: ${s(5)}"
                l += "poweron_alarm (kernel)" to s(6)
                l += "qcom,qpnp-rtc-alarm-pwrup" to s(7)
                val miui = r.getOrNull(8)?.salida.orEmpty().lineSequence().map { it.trim() }.filter { it.startsWith("com.miui") }.toList()
                l += "Encendido programado de Xiaomi" to (miui.joinToString().ifBlank { "no se encontró" })
                l += "Motivo del último arranque" to s(9)
                l += "En la lista blanca de Doze" to (r.getOrNull(10)?.salida?.trim()?.removeSuffix(".")?.trim()?.ifBlank { "ninguna" } ?: "?")
                l += "Grupo de espera (FT150TS / DiPlay)" to (r.getOrNull(11)?.salida?.trim()?.replace('\n', ' ') ?: "?")
                l += "Pantalla de CarPlay de DiPlay" to if ((s(12).toIntOrNull() ?: 0) > 0) "abierta" else "cerrada"
                ver = veredicto(s(7), s(6), miui.isNotEmpty(), true)
            } else {
                l += "Root" to "no respondió"
            }
        } else {
            l += "Root" to "sin permiso (Ajustes → Root). Lo demás se puede leer igual."
        }
        lineas.value = l
        encendido.value = ver
        trabajando.value = false
    }

    private fun siNo(b: Boolean) = if (b) "sí" else "no"

    /** getprop sin root (propiedades públicas). */
    private fun prop(nombre: String): String = try {
        val p = ProcessBuilder("getprop", nombre).redirectErrorStream(true).start()
        val s = p.inputStream.bufferedReader().use { it.readText() }.trim()
        p.waitFor(3, TimeUnit.SECONDS)
        s
    } catch (_: Exception) { "" }

    // ---------------- FT150TS + DiPlay juntos ----------------

    /**
     * Quita a DiPlay de las restricciones de batería con root (lista blanca de Doze y "correr en segundo plano").
     * Antes guarda cómo estaba para poder regresarlo. No toca memoria, prioridades ni políticas térmicas.
     */
    suspend fun prepararDiPlay(ctx: Context): String = withContext(Dispatchers.IO) {
        val d = CarPlay.buscar(ctx)?.takeIf { it.confiable } ?: return@withContext "DiPlay no está instalado (o su firma no es de fiar)"
        if (!Root.activo.value || Root.disponible.value != true) return@withContext "Requiere root"
        val p = d.paquete
        if (!LogicaNoche.paqueteValido(p)) return@withContext "Paquete inválido"
        val leido = Root.ejecutarCada(listOf(
            "dumpsys deviceidle whitelist | grep -c ',$p,' ",
            "cmd appops get $p RUN_ANY_IN_BACKGROUND",
        )) ?: return@withContext "Root no respondió"
        val enLista = (leido[0].salida.trim().lineSequence().lastOrNull()?.trim()?.toIntOrNull() ?: 0) > 0
        val modo = Regex("RUN_ANY_IN_BACKGROUND: (\\w+)").find(leido[1].salida)?.groupValues?.get(1) ?: "default"
        val r = ctx.getSharedPreferences(Nocturno.PREFS_REGISTRO, Context.MODE_PRIVATE)
        // Solo la primera vez se guarda el "antes" (si ya se preparó, el antes real es el de la primera vez)
        if (!r.contains("diplayAntesLista")) {
            r.edit().putBoolean("diplayAntesLista", enLista).putString("diplayAntesModo", modo).putString("diplayPaquete", p).apply()
        }
        val res = Root.ejecutarCada(listOf(
            "dumpsys deviceidle whitelist +$p",
            "cmd appops set $p RUN_ANY_IN_BACKGROUND allow",
        )) ?: return@withContext "Root no respondió"
        if (res.all { it.ok }) "Listo: Android ya no restringe a DiPlay en segundo plano"
        else "Parcial: " + res.mapIndexedNotNull { i, x -> if (x.ok) null else "paso ${i + 1}: ${x.salida.take(60)}" }.joinToString()
    }

    /** Regresa a DiPlay como estaba antes de prepararDiPlay(). */
    suspend fun deshacerDiPlay(ctx: Context): String = withContext(Dispatchers.IO) {
        val r = ctx.getSharedPreferences(Nocturno.PREFS_REGISTRO, Context.MODE_PRIVATE)
        if (!r.contains("diplayAntesLista")) return@withContext "No hay nada que deshacer"
        if (!Root.activo.value || Root.disponible.value != true) return@withContext "Requiere root"
        val p = r.getString("diplayPaquete", "") ?: ""
        val modo = r.getString("diplayAntesModo", "default") ?: "default"
        if (!LogicaNoche.paqueteValido(p) || modo !in setOf("allow", "ignore", "deny", "default", "foreground")) {
            return@withContext "Registro dañado: revísalo a mano (docs/AHORRO_NOCTURNO.md)"
        }
        val cmds = mutableListOf<String>()
        if (!r.getBoolean("diplayAntesLista", false)) cmds += "dumpsys deviceidle whitelist -$p"
        cmds += "cmd appops set $p RUN_ANY_IN_BACKGROUND $modo"
        val res = Root.ejecutarCada(cmds) ?: return@withContext "Root no respondió"
        if (res.all { it.ok }) {
            r.edit().remove("diplayAntesLista").remove("diplayAntesModo").remove("diplayPaquete").apply()
            "DiPlay quedó como estaba"
        } else "No se pudo deshacer todo: " + res.filter { !it.ok }.joinToString { it.salida.take(60) }
    }

    fun diplayPreparado(ctx: Context) =
        ctx.getSharedPreferences(Nocturno.PREFS_REGISTRO, Context.MODE_PRIVATE).contains("diplayAntesLista")

    /** Pantalla de "Inicio automático" de MIUI/HyperOS; si no existe, la info de la app. */
    fun abrirInicioAutomatico(ctx: Context, paquete: String) {
        val miui = Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(miui); return } catch (_: Exception) { }
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", paquete, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }

    /** Ajuste de Android para permitir alarmas exactas (Android 12+). */
    fun abrirAlarmasExactas(ctx: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", ctx.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }
}
