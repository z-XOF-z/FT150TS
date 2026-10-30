package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Rutinas (v3.8): el mapa aprende a dónde vas y a qué hora con tus rodadas guardadas (Rodadas.kt) y, con el horario
 * de la escuela, te dice "Escuela · 18 min · llegas 7:42 (8 min antes)". Lógica en RutinasLogica (con pruebas).
 * Todo se queda en el teléfono. Se recalcula cuando cambia la lista de rodadas y revisa la predicción cada 30 s.
 */
object Rutinas {
    val activo = MutableStateFlow(true)
    /** Usar el horario de la escuela (lunes 7:00, martes a viernes 7:50) para decir si vas a tiempo. */
    val usarHorario = MutableStateFlow(true)
    /** Avisar por voz si vas tarde (una vez por viaje). */
    val vozTarde = MutableStateFlow(false)

    val lugares = MutableStateFlow<List<LugarRutina>>(emptyList())
    val patrones = MutableStateFlow<List<PatronRutina>>(emptyList())
    val prediccion = MutableStateFlow<PrediccionRutina?>(null)
    /** Cuántas rodadas se usaron para aprender. */
    val viajes = MutableStateFlow(0)

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + Seguro.manejador)
    private var ciclo: Job? = null
    /** Para avisar "vas tarde" una sola vez por destino y día. */
    private var avisado = ""

    fun iniciar(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("rutinas", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", true)
        usarHorario.value = p.getBoolean("horario", true)
        vozTarde.value = p.getBoolean("vozTarde", false)
        // Aprende otra vez cada que cambia la lista de rodadas
        scope.launch {
            Rodadas.lista.map { l -> l.map { it.id } }.distinctUntilChanged().collect { aprender() }
        }
        arrancarCiclo()
    }

    fun ponerActivo(on: Boolean) { activo.value = on; prefs?.edit()?.putBoolean("activo", on)?.apply(); if (!on) prediccion.value = null }
    fun ponerHorario(on: Boolean) { usarHorario.value = on; prefs?.edit()?.putBoolean("horario", on)?.apply() }
    fun ponerVozTarde(on: Boolean) { vozTarde.value = on; prefs?.edit()?.putBoolean("vozTarde", on)?.apply() }

    private suspend fun aprender() {
        val lista = Rodadas.lista.value
        val vs = ArrayList<ViajeRutina>()
        for (r in lista) {
            val pts = Rodadas.cargarPuntos(r.id)
            if (pts.size < 2 || r.km < 0.3f) continue
            val c = Calendar.getInstance().apply { timeInMillis = r.id }
            val dia = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1          // domingo=1 → lunes=1 … domingo=7
            val a = pts.first(); val b = pts.last()
            vs.add(ViajeRutina(dia, c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), r.minutos.coerceAtLeast(1),
                a.lat, a.lon, b.lat, b.lon))
        }
        val l = RutinasLogica.agruparLugares(vs)
        lugares.value = l
        patrones.value = RutinasLogica.patrones(vs, l)
        viajes.value = vs.size
        revisar()
    }

    private fun arrancarCiclo() {
        ciclo?.cancel()
        ciclo = scope.launch {
            while (isActive) { revisar(); delay(30_000) }
        }
    }

    /** Recalcula la predicción de ahorita (barato: sin archivos). */
    fun revisar() {
        if (!activo.value) { prediccion.value = null; return }
        val c = Calendar.getInstance()
        val dia = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        val ahora = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val u = UbicacionManager.ubicacion.value
        val rodando = Rodadas.enCurso.value != null || UbicacionManager.velocidadKmh() > 10f
        val p = RutinasLogica.predecir(dia, ahora, u?.latitude, u?.longitude, rodando, lugares.value, patrones.value,
            if (usarHorario.value) RutinasLogica.HORARIO_ESCUELA else emptyList())
        prediccion.value = p
        // Voz: vas tarde (una vez por destino y día, solo rodando)
        val m = p?.margenMin
        if (p != null && m != null && m < 0 && rodando && vozTarde.value) {
            val clave = "$dia-${p.destino.id}-${c.get(Calendar.DAY_OF_YEAR)}"
            if (clave != avisado) {
                avisado = clave
                Voz.decir("Vas unos ${-m} minutos tarde para la entrada de las ${RutinasLogica.hhmm(p.entradaMin!!)}.")
            }
        }
    }

    /** Texto corto para las tarjetas: "Escuela · 18 min · llegas 7:42 (8 min antes)". */
    fun texto(p: PrediccionRutina): String {
        val base = "${p.destino.nombre} · ${p.duracionMin} min · llegas ${RutinasLogica.hhmm(p.llegadaMin)}"
        val m = p.margenMin ?: return base
        return base + when {
            m > 0 -> " ($m min antes)"
            m == 0 -> " (justo a tiempo)"
            else -> " (${-m} min tarde)"
        }
    }
}
