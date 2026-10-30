package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Un logro con su ícono pixel (cada letra que no es "." es un cuadrito). */
data class Logro(val id: String, val nombre: String, val detalle: String, val arte: List<String>, val secreto: Boolean = false)

/**
 * Logros pixel: se desbloquean solos con lo que haces con la moto y la app (ninguno premia ir rápido en la
 * calle). Al ganar uno sale un aviso arriba de la pantalla.
 */
object Logros {
    private val MOTO = listOf("....XX.", "..XXX..", "XXXXXXX", ".X...X.", "X.X.X.X", ".X...X.", ".......")
    private val RUTA = listOf("X.....X", "X..X..X", "X.....X", "X..X..X", "X.....X", "X..X..X", "X.....X")
    private val MONTE = listOf("......X", ".....XX", "..X.XXX", ".XXXXXX", "XXXXXXX", "XXXXXXX", ".......")
    private val SOL = listOf("...X...", ".X.X.X.", "..XXX..", "XXXXXXX", "..XXX..", ".X.X.X.", "...X...")
    private val LUNA = listOf("..XXX..", ".XX....", "XX.....", "XX.....", "XX.....", ".XX....", "..XXX..")
    private val CURVA = listOf("XX.....", "..X....", "...X...", "...X...", "....X..", ".....X.", "......X")
    private val LLAVE = listOf(".X...X.", ".XX.XX.", "..XXX..", "...X...", "...X...", "...X...", "...X...")
    private val GOTA = listOf("...X...", "..XXX..", "..XXX..", ".XXXXX.", ".XXXXX.", ".XXXXX.", "..XXX..")
    private val BANDERA = listOf("XX.X.X.", "X.X.X..", "XX.X.X.", "X.X.X..", "X......", "X......", "X......")
    private val AGUJA = listOf("..XXX..", ".X...X.", "X..X..X", "X...X.X", "X.....X", ".X...X.", "..XXX..")
    private val ESCUDO = listOf("XXXXXXX", "X.....X", "X..X..X", "X.XXX.X", ".X.X.X.", "..X.X..", "...X...")
    private val PALETA = listOf("..XXX..", ".X.X.X.", "X.....X", "X.X...X", "X....X.", ".X.XX..", "..X....")
    private val ESTRELLA = listOf("...X...", "...X...", "XXXXXXX", ".XXXXX.", "..XXX..", ".XX.XX.", "X.....X")

    val TODOS = listOf(
        Logro("primera", "Primera rodada", "Graba tu primera rodada", MOTO),
        Logro("km100", "100 km", "Recorre 100 km con la app", RUTA),
        Logro("km1000", "1,000 km", "Recorre 1,000 km con la app", RUTA),
        Logro("larga", "Carretera", "Una rodada de 50 km o más", MONTE),
        Logro("madrugador", "Madrugador", "Sal a rodar antes de las 6 am", SOL),
        Logro("nocturno", "Nocturno", "Rueda después de las 10 pm", LUNA),
        Logro("curvero", "Curvero", "Inclínate 30° en una curva", CURVA),
        Logro("calibrado", "Afinado", "Calibra las RPM en ralentí", LLAVE),
        Logro("bitacora", "Bitácora", "Anota 5 cargas de gasolina", GOTA),
        Logro("arrancon", "Luz verde", "Haz tu primer arrancón (en lugar cerrado)", BANDERA),
        Logro("velocimetro", "Sin trampa", "Mide el error del velocímetro", AGUJA),
        Logro("respaldo", "A salvo", "Haz un respaldo de tus datos", ESCUDO),
        Logro("explorador", "Explorador", "Prueba 3 temas distintos", PALETA),
        Logro("xof", "Modo XOF", "Encuentra el secreto del logo", ESTRELLA, secreto = true),
    )

    /** id → cuándo se ganó. */
    val ganados = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** El que se acaba de ganar (para el aviso de arriba); se limpia solo. */
    val nuevo = MutableStateFlow<Logro?>(null)

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + Seguro.manejador)
    private var temasVistos = mutableSetOf<String>()
    private var iniciado = false

    fun iniciar(c: Context) {
        if (iniciado) return
        iniciado = true
        val p = c.applicationContext.getSharedPreferences("logros", Context.MODE_PRIVATE)
        prefs = p
        ganados.value = TODOS.mapNotNull { l -> p.getLong(l.id, 0L).takeIf { it > 0L }?.let { l.id to it } }.toMap()
        temasVistos = (p.getStringSet("temas", emptySet()) ?: emptySet()).toMutableSet()
        scope.launch {
            delay(5_000)
            while (isActive) { revisar(); delay(60_000) }
        }
    }

    fun revisar() {
        val rodadas = Rodadas.lista.value
        val hora = { ms: Long -> Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY) }
        if (rodadas.isNotEmpty()) desbloquear("primera")
        val kmTotal = rodadas.sumOf { it.km.toDouble() }
        if (kmTotal >= 100) desbloquear("km100")
        if (kmTotal >= 1000) desbloquear("km1000")
        if (rodadas.any { it.km >= 50f }) desbloquear("larga")
        if (rodadas.any { hora(it.id) in 4..5 }) desbloquear("madrugador")
        if (rodadas.any { hora(it.id) >= 22 }) desbloquear("nocturno")
        if (rodadas.any { it.inclMax >= 30f }) desbloquear("curvero")
        if (TelefonoMoto.calibrado.value) desbloquear("calibrado")
        if (Gasolina.cargas.value.size >= 5) desbloquear("bitacora")
        if (Arrancones.historial.value.isNotEmpty()) desbloquear("arrancon")
        if (Velocimetro.medidas.value.isNotEmpty()) desbloquear("velocimetro")
        if (temasVistos.add(Apariencia.tema.name)) prefs?.edit()?.putStringSet("temas", temasVistos.toSet())?.apply()
        if (temasVistos.size >= 3) desbloquear("explorador")
    }

    fun desbloquear(id: String) {
        if (ganados.value.containsKey(id)) return
        val l = TODOS.firstOrNull { it.id == id } ?: return
        val ahora = System.currentTimeMillis()
        ganados.value = ganados.value + (id to ahora)
        prefs?.edit()?.putLong(id, ahora)?.apply()
        nuevo.value = l
        scope.launch { delay(4_000); if (nuevo.value == l) nuevo.value = null }
    }
}
