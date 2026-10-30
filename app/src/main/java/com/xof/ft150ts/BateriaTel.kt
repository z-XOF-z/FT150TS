package com.xof.ft150ts

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Batería del teléfono. */
data class EstadoBateria(
    val pct: Int = -1,
    val cargando: Boolean = false,
    val fuente: String = "",          // "Cable", "USB", "Inalámbrica" o ""
    val miliamperes: Int = 0,         // + entra, − sale
    val voltios: Float = 0f,
    val vatios: Float = 0f,           // lo que entra (cargando) o lo que gasta
    val temperatura: Float = -1f,
    val salud: String = "",
    val ciclos: Int = -1,             // Android 14+
    val minutosLlenar: Int = -1,      // cuánto falta para el 100 % (Android 9+, si el teléfono lo sabe)
)

/**
 * Batería del teléfono: porcentaje, si carga y con cuántos watts (útil para ver si el cable de la moto
 * sí está cargando), voltaje, corriente, temperatura, salud y ciclos. Guarda el porcentaje de las últimas
 * 2 horas para la gráfica y avisa si la moto está prendida pero el teléfono no carga (falso del cable).
 */
object BateriaTel {
    val estado = MutableStateFlow(EstadoBateria())
    /** Porcentaje cada minuto (últimas 2 h). */
    val historial = MutableStateFlow<List<Float>>(emptyList())

    private var ctx: Context? = null
    private var bm: BatteryManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private var avisoCable = 0L
    private var avisoBaja = 0L

    fun iniciar(c: Context) {
        if (ctx != null) return
        val app = c.applicationContext
        ctx = app
        bm = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        scope.launch {
            var n = 0
            while (isActive) {
                // La corriente se lee seguido solo con la app en pantalla
                if (BleManager.primerPlano) medirCorriente()
                if (n % 30 == 0) guardarHistorial()      // cada minuto
                if (n % 10 == 0) revisarAvisos()
                n++
                delay(2_000)
            }
        }
    }

    /** Lo llama Energia con cada ACTION_BATTERY_CHANGED. */
    fun desdeIntent(i: Intent) {
        val nivel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val escala = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val enchufe = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val salud = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Muerta"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobrevoltaje"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
            else -> ""
        }
        val ciclos = if (Build.VERSION.SDK_INT >= 34) i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) else -1
        val e = estado.value
        estado.value = e.copy(
            pct = if (nivel >= 0) nivel * 100 / escala else e.pct,
            cargando = enchufe != 0 && st != BatteryManager.BATTERY_STATUS_DISCHARGING,
            fuente = when (enchufe) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Cable"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Inalámbrica"
                else -> ""
            },
            voltios = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f,
            temperatura = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0).let { if (it > 0) it / 10f else e.temperatura },
            salud = salud.ifBlank { e.salud },
            ciclos = if (ciclos >= 0) ciclos else e.ciclos,
        )
        medirCorriente()
    }

    /** Corriente y watts. Cada teléfono reporta distinto (µA o mA, con signo o sin él): se acomoda. */
    private fun medirCorriente() {
        val b = bm ?: return
        val e = estado.value
        val crudo = try { b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Exception) { Int.MIN_VALUE }
        if (crudo == Int.MIN_VALUE || crudo == 0) return
        val ma = if (abs(crudo) > 20_000) crudo / 1000 else crudo       // µA → mA
        // Con el cable puesto lo normal es que entre corriente: el signo lo pone la carga
        val firmado = if (e.cargando) abs(ma) else -abs(ma)
        val minutos = if (Build.VERSION.SDK_INT >= 28 && e.cargando) {
            try { b.computeChargeTimeRemaining().let { if (it > 0) (it / 60_000L).toInt() else -1 } } catch (_: Exception) { -1 }
        } else -1
        estado.value = e.copy(
            miliamperes = firmado,
            vatios = abs(firmado) / 1000f * e.voltios,
            minutosLlenar = minutos,
        )
    }

    private fun guardarHistorial() {
        val p = estado.value.pct
        if (p < 0) return
        historial.value = (historial.value + p.toFloat()).takeLast(120)
    }

    private fun revisarAvisos() {
        val e = estado.value
        val ahora = System.currentTimeMillis()
        // Motor prendido (vibración) pero el teléfono no carga: el cable o el cargador de la moto fallan
        if (TelefonoMoto.info.value.motor && !e.cargando && ahora - avisoCable > 30 * 60_000L) {
            avisoCable = ahora
            Moto.aviso("Cel no carga", "Ojo: la moto está prendida pero el teléfono no está cargando. Revisa el cable")
        }
        if (e.pct in 0..15 && !e.cargando && Energia.enMoto.value && ahora - avisoBaja > 30 * 60_000L) {
            avisoBaja = ahora
            Moto.aviso("Cel ${e.pct}%", "Al teléfono le queda ${e.pct} por ciento de batería")
        }
    }
}
