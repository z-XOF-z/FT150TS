package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TemaApp { CARBONO, VIDRIO, FILO, PIXEL, TACTICO }
enum class EstiloTablero { LCD, CLASICO }
/** Cuándo se queda prendida la pantalla del teléfono. */
enum class PantallaEncendida { SIEMPRE, CON_MOTO, NORMAL }
/** Barras de Android dentro de la app: normales, sin los botones de abajo, o pantalla completa. */
enum class BarrasSistema { NORMAL, SIN_BOTONES, COMPLETA }

data class Acento(val nombre: String, val argb: Long)
data class ColoresRpm(val nombre: String, val baja: Long, val media: Long, val alta: Long)

/**
 * Apariencia de la app (se guarda en el teléfono).
 * Son estados de Compose: al cambiarlos, la pantalla se redibuja sola.
 */
object Apariencia {
    val ACENTOS = listOf(
        Acento("Lima", 0xFF9BE22B),
        Acento("Cian", 0xFF00E5FF),
        Acento("Ámbar", 0xFFFFB300),
        Acento("Rojo", 0xFFFF3B3B),
        Acento("Morado", 0xFFB46CFF),
        Acento("Plata", 0xFFC9D3D8),
        Acento("Verde tóxico", 0xFF39FF14),
        Acento("Magenta neón", 0xFFFF2BD6),
        Acento("Naranja", 0xFFFF6A00),
        Acento("Azul eléctrico", 0xFF2E7BFF),
        Acento("Amarillo", 0xFFFFE600),
        Acento("Turquesa", 0xFF1DE9B6),
        Acento("Rosa", 0xFFFF5C93),
        Acento("Blanco", 0xFFF4F7F2),
    )
    /** Índice del acento "Personalizado" (va después de la lista). */
    val ACENTO_LIBRE: Int get() = ACENTOS.size
    /** Acento secreto "Oro XOF" (se desbloquea tocando 7 veces el logo). */
    val ACENTO_ORO: Int get() = ACENTOS.size + 1
    const val ORO_XOF = 0xFFFFC83DL

    val COLORES_RPM = listOf(
        ColoresRpm("Verde · Ámbar · Azul", 0xFF39FF14, 0xFFFFB300, 0xFF2979FF),
        ColoresRpm("Verde · Blanco · Azul", 0xFF39FF14, 0xFFF4F7F2, 0xFF2979FF),
        ColoresRpm("Verde · Cian · Azul", 0xFF39FF14, 0xFF00E5FF, 0xFF304FFE),
        ColoresRpm("Clásico: verde · blanco · rojo", 0xFF9BE22B, 0xFFF4F7F2, 0xFFFF3B3B),
    )

    var tema by mutableStateOf(TemaApp.CARBONO)
        private set
    var acento by mutableIntStateOf(0)
        private set
    var estiloTablero by mutableStateOf(EstiloTablero.LCD)
        private set
    var coloresRpm by mutableIntStateOf(0)
        private set
    var siempreHorizontal by mutableStateOf(false)
        private set
    var brilloMax by mutableStateOf(false)
        private set
    var pantalla by mutableStateOf(PantallaEncendida.SIEMPRE)
        private set
    /** Usar la tasa de refresco más alta de la pantalla (90/120 Hz). */
    var fluidez by mutableStateOf(true)
        private set
    /** Ocultar los botones (atrás / inicio / recientes) y la barra de estado dentro de la app. */
    var barras by mutableStateOf(BarrasSistema.NORMAL)
        private set
    /** Tarjetas del tablero más chicas (inclinación, perfil, viaje, batería, clima). */
    var datosCompactos by mutableStateOf(true)
        private set
    /** Aguja/barra de RPM movida por un control PID (con inercia, como un tacómetro de verdad). */
    var pidAguja by mutableStateOf(true)
        private set
    var pidKp by mutableFloatStateOf(400f)
        private set
    var pidKi by mutableFloatStateOf(10f)
        private set
    var pidKd by mutableFloatStateOf(35f)
        private set
    /** Cuánto se suben los 4 accesos directos del tablero (milímetros reales), para librar los ganchos del soporte. */
    var subirAccesosMm by mutableIntStateOf(5)
        private set
    /** Modo XOF desbloqueado (secreto del logo). */
    var secretoXof by mutableStateOf(false)
        private set
    /** Animación al entrar al tablero (barrido a 45° y las RPM suben al tope y regresan). */
    var animEntrada by mutableStateOf(true)
        private set
    /** Brillo neón en bordes, barra de RPM y barra de arriba. */
    var neon by mutableStateOf(false)
        private set
    /** Fondo: 0 puntos, 1 rejilla, 2 rayas a 45°, 3 negro puro. */
    var fondo by mutableIntStateOf(0)
        private set
    /** Barra de RPM del LCD: 0 según el tema, 1 curva, 2 diagonal a 45°. */
    var barraRpm by mutableIntStateOf(0)
        private set
    /** Acento personalizado: tono (0–360) y saturación (0–1). */
    var tonoLibre by mutableFloatStateOf(140f)
        private set
    var saturacionLibre by mutableFloatStateOf(1f)
        private set

    /** Color de acento actual (de la lista o el personalizado). */
    fun colorAcento(): Long = when {
        acento == ACENTO_ORO -> ORO_XOF
        acento >= ACENTOS.size -> hsv(tonoLibre, saturacionLibre, 1f)
        else -> ACENTOS[acento].argb
    }
    fun nombreAcento(): String = when {
        acento == ACENTO_ORO -> "Oro XOF"
        acento >= ACENTOS.size -> "Personalizado"
        else -> ACENTOS[acento].nombre
    }

    /** HSV → ARGB (sin depender de android.graphics). */
    fun hsv(h: Float, s: Float, v: Float): Long {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = v * s
        val x = c * (1f - kotlin.math.abs(hh % 2f - 1f))
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun canal(f: Float) = ((f + m) * 255f).toInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (canal(r) shl 16) or (canal(g) shl 8) or canal(b)
    }

    private var prefs: SharedPreferences? = null

    fun cargar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("apariencia", Context.MODE_PRIVATE)
        prefs = p
        tema = TemaApp.entries.firstOrNull { it.name == p.getString("tema", "CARBONO") } ?: TemaApp.CARBONO
        secretoXof = p.getBoolean("secretoXof", false)
        acento = p.getInt("acento", 0).coerceIn(0, if (secretoXof) ACENTO_ORO else ACENTOS.size)
        estiloTablero = if (p.getString("tablero", "LCD") == "CLASICO") EstiloTablero.CLASICO else EstiloTablero.LCD
        coloresRpm = p.getInt("coloresRpm", 0).coerceIn(0, COLORES_RPM.size - 1)
        siempreHorizontal = p.getBoolean("horizontal", false)
        brilloMax = p.getBoolean("brilloMax", false)
        fluidez = p.getBoolean("fluidez", true)
        pantalla = PantallaEncendida.entries.firstOrNull { it.name == p.getString("pantalla", "SIEMPRE") } ?: PantallaEncendida.SIEMPRE
        barras = BarrasSistema.entries.firstOrNull { it.name == p.getString("barras", "NORMAL") } ?: BarrasSistema.NORMAL
        datosCompactos = p.getBoolean("datosCompactos", true)
        pidAguja = p.getBoolean("pidAguja", true)
        pidKp = p.getFloat("pidKp", 400f)
        pidKi = p.getFloat("pidKi", 10f)
        pidKd = p.getFloat("pidKd", 35f)
        subirAccesosMm = p.getInt("subirAccesosMm", 5).coerceIn(0, 20)
        neon = p.getBoolean("neon", false)
        animEntrada = p.getBoolean("animEntrada", true)
        fondo = p.getInt("fondo", 0).coerceIn(0, 3)
        barraRpm = p.getInt("barraRpm", 0).coerceIn(0, 2)
        tonoLibre = p.getFloat("tonoLibre", 140f)
        saturacionLibre = p.getFloat("saturacionLibre", 1f)
    }

    private fun guardar() {
        prefs?.edit()
            ?.putString("tema", tema.name)
            ?.putInt("acento", acento)
            ?.putString("tablero", estiloTablero.name)
            ?.putInt("coloresRpm", coloresRpm)
            ?.putBoolean("horizontal", siempreHorizontal)
            ?.putBoolean("brilloMax", brilloMax)
            ?.putString("pantalla", pantalla.name)
            ?.putBoolean("fluidez", fluidez)
            ?.putString("barras", barras.name)
            ?.putBoolean("datosCompactos", datosCompactos)
            ?.putBoolean("pidAguja", pidAguja)
            ?.putFloat("pidKp", pidKp)
            ?.putFloat("pidKi", pidKi)
            ?.putFloat("pidKd", pidKd)
            ?.putInt("subirAccesosMm", subirAccesosMm)
            ?.putBoolean("neon", neon)
            ?.putBoolean("animEntrada", animEntrada)
            ?.putBoolean("secretoXof", secretoXof)
            ?.putInt("fondo", fondo)
            ?.putInt("barraRpm", barraRpm)
            ?.putFloat("tonoLibre", tonoLibre)
            ?.putFloat("saturacionLibre", saturacionLibre)
            ?.apply()
    }

    fun ponerTema(t: TemaApp) { tema = t; guardar() }
    fun ponerAcento(i: Int) { acento = i.coerceIn(0, if (secretoXof) ACENTO_ORO else ACENTOS.size); guardar() }
    /** ¡Secreto! Acento Oro XOF y logro. */
    fun desbloquearXof() { secretoXof = true; acento = ACENTO_ORO; guardar() }
    fun ponerAcentoLibre(tono: Float, sat: Float) { tonoLibre = tono; saturacionLibre = sat.coerceIn(0f, 1f); acento = ACENTOS.size; guardar() }
    fun ponerNeon(on: Boolean) { neon = on; guardar() }
    fun ponerAnimEntrada(on: Boolean) { animEntrada = on; guardar() }
    fun ponerFondo(i: Int) { fondo = i.coerceIn(0, 3); guardar() }
    fun ponerBarraRpm(i: Int) { barraRpm = i.coerceIn(0, 2); guardar() }
    fun ponerEstiloTablero(e: EstiloTablero) { estiloTablero = e; guardar() }
    fun ponerColoresRpm(i: Int) { coloresRpm = i.coerceIn(0, COLORES_RPM.size - 1); guardar() }
    fun ponerSiempreHorizontal(on: Boolean) { siempreHorizontal = on; guardar() }
    fun ponerBrilloMax(on: Boolean) { brilloMax = on; guardar() }
    fun ponerPantalla(m: PantallaEncendida) { pantalla = m; guardar() }
    fun ponerFluidez(on: Boolean) { fluidez = on; guardar() }
    fun ponerBarras(b: BarrasSistema) { barras = b; guardar() }
    fun ponerDatosCompactos(on: Boolean) { datosCompactos = on; guardar() }
    fun ponerPidAguja(on: Boolean) { pidAguja = on; guardar() }
    fun ponerPid(kp: Float, ki: Float, kd: Float) { pidKp = kp; pidKi = ki; pidKd = kd; guardar() }
    fun ponerSubirAccesos(mm: Int) { subirAccesosMm = mm.coerceIn(0, 20); guardar() }
    fun pidDeFabrica() { pidKp = 400f; pidKi = 10f; pidKd = 35f; guardar() }

    /** Regresa todo a como venía de fábrica. */
    fun restablecer() {
        tema = TemaApp.CARBONO; acento = 0; estiloTablero = EstiloTablero.LCD
        coloresRpm = 0; siempreHorizontal = false; brilloMax = false; pantalla = PantallaEncendida.SIEMPRE; fluidez = true
        barras = BarrasSistema.NORMAL; datosCompactos = true
        neon = false; fondo = 0; barraRpm = 0
        guardar()
    }
}
