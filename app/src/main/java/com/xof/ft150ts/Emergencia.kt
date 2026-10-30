package com.xof.ft150ts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Una cuenta regresiva de SOS en curso. [fin] en elapsedRealtime. [prueba] = no se manda nada de verdad. */
data class CuentaSos(val fin: Long, val prueba: Boolean, val motivo: String)

/**
 * SOS por caída. Si vas rodando, hay un golpe muy fuerte (posible caída o choque) y después el teléfono se queda
 * quieto, cuenta 30 s en voz alta con un botón grande "ESTOY BIEN". Si no lo tocas, manda un SMS a tu contacto de
 * emergencia con tu ubicación. Apagado de fábrica; nada se manda sin contacto configurado.
 *
 * Con el permiso de SMS se manda solo; sin él, se abre tu app de mensajes ya escrita (tienes que tocar enviar).
 */
@android.annotation.SuppressLint("StaticFieldLeak")   // solo guarda applicationContext
object Emergencia {
    val activo = MutableStateFlow(false)
    val numero = MutableStateFlow("")
    val nombre = MutableStateFlow("")
    val segundos = MutableStateFlow(30)
    /** Cuenta regresiva en curso (null = nada). */
    val cuenta = MutableStateFlow<CuentaSos?>(null)
    /** Lo último que pasó (para Ajustes). */
    val evento = MutableStateFlow("")

    /** Vas rodando: más de esto en los últimos 15 s. */
    const val VEL_RODANDO = 15f
    /** Después del golpe, cuánto esperar para ver si sigues quieto. */
    private const val ESPERA_QUIETO_MS = 8_000L
    /** No dos avisos seguidos. */
    private const val PAUSA_MS = 5 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private var app: Context? = null
    private var prefs: SharedPreferences? = null
    private var ultimaVelAlta = 0L
    private var ultimoAviso = -PAUSA_MS
    private var trabajo: Job? = null

    fun iniciar(c: Context) {
        if (app != null) return
        val ctx = c.applicationContext
        app = ctx
        val p = ctx.getSharedPreferences("sos", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", false)
        numero.value = ReglasSos.limpiarNumero(p.getString("numero", "") ?: "")
        nombre.value = (p.getString("nombre", "") ?: "").take(40)
        segundos.value = p.getInt("segundos", 30).coerceIn(10, 120)
        evento.value = p.getString("evento", "") ?: ""
        // Se acuerda de cuándo ibas rápido (después del golpe la velocidad ya es 0)
        scope.launch { UbicacionManager.velocidad.collect { if (it > VEL_RODANDO) ultimaVelAlta = SystemClock.elapsedRealtime() } }
    }

    fun ponerActivo(on: Boolean) { activo.value = on; prefs?.edit()?.putBoolean("activo", on)?.apply() }
    fun ponerNumero(n: String) { numero.value = ReglasSos.limpiarNumero(n); prefs?.edit()?.putString("numero", numero.value)?.apply() }
    fun ponerNombre(n: String) { nombre.value = n.take(40); prefs?.edit()?.putString("nombre", nombre.value)?.apply() }
    fun ponerSegundos(s: Int) { segundos.value = s.coerceIn(10, 120); prefs?.edit()?.putInt("segundos", segundos.value)?.apply() }

    fun puedeMandarSolo(c: Context) =
        c.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Lo llama TelefonoMoto (hilo principal) con un golpe muy fuerte. [vel] = km/h en ese momento. */
    fun impacto(vel: Float) {
        if (!activo.value || cuenta.value != null) return
        val ahora = SystemClock.elapsedRealtime()
        val rodando = vel > VEL_RODANDO || ahora - ultimaVelAlta < 15_000L
        if (!ReglasSos.debeVigilar(rodando, ReglasSos.numeroValido(numero.value), ahora - ultimoAviso, PAUSA_MS)) return
        trabajo?.cancel()
        trabajo = scope.launch {
            delay(ESPERA_QUIETO_MS)
            // ¿Sigues rodando? Entonces fue un bache enorme, no una caída
            if (UbicacionManager.velocidad.value > 5f) return@launch
            empezarCuenta(false, "golpe fuerte y el teléfono quedó quieto")
        }
    }

    /** Prueba: la misma cuenta y voz, pero al final solo muestra el mensaje (no manda nada). */
    fun probar() { if (cuenta.value == null) empezarCuenta(true, "prueba") }

    private fun empezarCuenta(prueba: Boolean, motivo: String) {
        ultimoAviso = SystemClock.elapsedRealtime()
        val s = segundos.value
        cuenta.value = CuentaSos(SystemClock.elapsedRealtime() + s * 1000L, prueba, motivo)
        anotar((if (prueba) "Prueba: " else "") + "cuenta de SOS ($motivo)")
        Voz.decir("¿Estás bien? Si no tocas la pantalla en $s segundos, aviso a tu contacto de emergencia.")
        vibrar(longArrayOf(0, 600, 300, 600, 300, 600))
        trabajo?.cancel()
        trabajo = scope.launch {
            var aviso = s
            while (true) {
                val c = cuenta.value ?: return@launch
                val falta = ((c.fin - SystemClock.elapsedRealtime()) / 1000L).toInt()
                if (falta <= 0) break
                // Recordatorio en voz cada 10 s
                if (falta <= aviso - 10) { aviso = falta; Voz.decir("$falta segundos"); vibrar(longArrayOf(0, 400)) }
                delay(500)
            }
            val c = cuenta.value ?: return@launch
            cuenta.value = null
            mandar(c)
        }
    }

    /** "ESTOY BIEN". */
    fun cancelar() {
        if (cuenta.value == null) return
        trabajo?.cancel()
        cuenta.value = null
        anotar("Cancelado: estás bien")
        Voz.decir("Listo, no aviso a nadie.")
    }

    /** "Pedir ayuda ya" (no espera la cuenta). */
    fun mandarYa() {
        val c = cuenta.value ?: return
        trabajo?.cancel()
        cuenta.value = null
        mandar(c)
    }

    private fun mandar(c: CuentaSos) {
        val ctx = app ?: return
        val texto = ReglasSos.mensaje(nombre.value, UbicacionManager.posicionMapa()?.let { Triple(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else -1f) },
            System.currentTimeMillis(), c.prueba)
        val num = numero.value
        if (c.prueba) { anotar("Prueba terminada. Se mandaría a $num: $texto"); Voz.decir("Prueba terminada."); return }
        if (!ReglasSos.numeroValido(num)) { anotar("No se mandó: falta el número de emergencia"); return }
        if (puedeMandarSolo(ctx)) {
            try {
                val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
                sms.sendMultipartTextMessage(num, null, sms.divideMessage(texto), null, null)
                anotar("SMS de emergencia mandado a $num")
                Voz.decir("Mandé tu ubicación a tu contacto de emergencia.")
                return
            } catch (e: Exception) {
                anotar("No se pudo mandar el SMS solo (${e.javaClass.simpleName}); se abre Mensajes")
            }
        }
        try {
            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", texto)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Voz.decir("Abrí tus mensajes con tu ubicación. Toca enviar.")
        } catch (_: Exception) { anotar("No hay app de mensajes") }
    }

    private fun vibrar(patron: LongArray) {
        val ctx = app ?: return
        try {
            val v = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(VibratorManager::class.java))?.defaultVibrator
                else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            v?.vibrate(VibrationEffect.createWaveform(patron, -1))
        } catch (_: Exception) { }
    }

    private fun anotar(t: String) {
        val txt = ModoSueno.hora(System.currentTimeMillis()) + " · " + t
        evento.value = txt
        prefs?.edit()?.putString("evento", txt)?.apply()
    }
}

/** Reglas puras del SOS (se prueban en EmergenciaTest). */
object ReglasSos {
    /** Solo dígitos y un + al inicio; 7 a 15 dígitos (E.164). Nada de letras ni códigos USSD (* #). */
    fun limpiarNumero(n: String): String {
        val t = n.trim()
        val mas = t.startsWith("+")
        val d = t.filter { it.isDigit() }.take(15)
        return if (mas) "+$d" else d
    }

    fun numeroValido(n: String): Boolean = Regex("^\\+?[0-9]{7,15}$").matches(n)

    /** ¿Vale la pena vigilar este golpe? */
    fun debeVigilar(rodando: Boolean, hayNumero: Boolean, desdeUltimoMs: Long, pausaMs: Long) =
        rodando && hayNumero && desdeUltimoMs >= pausaMs

    /** El SMS: quién, dónde (enlace de mapa y precisión) y a qué hora. Sin ubicación lo dice. */
    fun mensaje(nombre: String, pos: Triple<Double, Double, Float>?, cuando: Long, prueba: Boolean): String {
        val quien = nombre.trim().ifEmpty { "el conductor" }
        val hora = java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(cuando))
        val donde = if (pos == null) "Sin ubicación todavía."
            else "Ubicación: https://maps.google.com/?q=" + String.format(Locale.US, "%.6f,%.6f", pos.first, pos.second) +
                if (pos.third >= 0) " (±${pos.third.toInt()} m)" else ""
        return (if (prueba) "[PRUEBA] " else "") +
            "FT150TS: posible caída de $quien a las $hora. $donde No respondió en la moto."
    }
}
