package com.xof.ft150ts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Modo rendimiento (root): mientras ves el tablero, el kernel manda la app a los núcleos grandes del procesador y, con
 * CarPlay en la pestaña, además sube el piso de la GPU (ver ModoRendimientoLogica para lo medido). Con la pantalla
 * apagada, en otra app, en reposo o con el teléfono caliente, todo regresa a como lo deja Android.
 * Registro: adb logcat -s FT150-REND
 */
object ModoRendimiento {
    val activo = MutableStateFlow(true)
    /** Nivel aplicado ahora (lo muestra Ajustes). */
    val nivel = MutableStateFlow(NivelRendimiento.NORMAL)
    /** false = este teléfono no deja cambiarlo (no hay sched_boost): ya no se intenta. */
    val soportado = MutableStateFlow(true)

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + Seguro.manejador)
    /** (mínima, piso para CarPlay) de la GPU; null = sin control de la GPU. Se lee una vez. */
    @Volatile private var gpu: Pair<Long, Long>? = null
    @Volatile private var gpuLeida = false
    @Volatile private var guardian = false

    private fun log(m: String) = android.util.Log.i("FT150-REND", m)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun iniciar(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("rendimiento", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", true)
        scope.launch {
            val raiz = combine(Root.disponible, Root.activo) { d, a -> d == true && a }
            val estado = combine(Lanzador.enPantalla, Reposo.enReposo, Termico.nivel, CarPlayPestana.visible) { pantalla, reposo, calor, cp ->
                arrayOf<Any>(pantalla, reposo, calor, cp)
            }
            combine(activo, raiz, soportado, estado) { on, rootOk, sop, e ->
                ModoRendimientoLogica.nivel(
                    activo = on && sop, rootListo = rootOk, enPantalla = e[0] as Boolean, reposo = e[1] as Boolean,
                    calor = e[2] as NivelCalor, carplay = e[3] as Boolean,
                )
            }.distinctUntilChanged().collectLatest { n ->
                delay(250)          // al cambiar de pestañas rápido solo se aplica donde te quedaste
                aplicar(n)
            }
        }
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on
        prefs?.edit()?.putBoolean("activo", on)?.apply()
    }

    private fun aplicar(n: NivelRendimiento) {
        if (n == nivel.value) return
        // Sin root ya no se puede tocar nada (lo que se haya puesto lo regresa el guardián al cerrar la app)
        if (!Root.listo()) { nivel.value = NivelRendimiento.NORMAL; return }
        if (n != NivelRendimiento.NORMAL) prepararUnaVez()
        val r = Root.ejecutarCada(ModoRendimientoLogica.comandos(n, gpu)) ?: return
        if (r.firstOrNull()?.ok != true) {
            log("este teléfono no deja cambiar sched_boost: ${r.firstOrNull()?.salida?.take(80)}")
            soportado.value = false
            nivel.value = NivelRendimiento.NORMAL
            return
        }
        nivel.value = n
        log("nivel $n" + (gpu?.let { " (GPU mín. ${(if (n == NivelRendimiento.CARPLAY) it.second else it.first) / 1_000_000} MHz)" } ?: ""))
    }

    /**
     * Apagaste el root de la app: antes de soltarlo se regresa todo a la normalidad (con una sesión de un solo uso,
     * porque la de siempre se cierra).
     */
    fun soltar() {
        if (nivel.value == NivelRendimiento.NORMAL) return
        val c = ModoRendimientoLogica.comandos(NivelRendimiento.NORMAL, gpu)
        nivel.value = NivelRendimiento.NORMAL
        scope.launch { Root.ejecutar(*c.toTypedArray()) }
    }

    /** Lee las frecuencias de la GPU y deja corriendo al guardián (una vez por proceso de la app). */
    private fun prepararUnaVez() {
        if (!gpuLeida) {
            gpuLeida = true
            val s = Root.ejecutarCada(listOf("cat ${ModoRendimientoLogica.RUTA_GPU_FRECUENCIAS}"))?.firstOrNull()
            gpu = s?.takeIf { it.ok }?.let { ModoRendimientoLogica.frecuenciasGpu(it.salida) }
            log("GPU: ${gpu?.let { "${it.first / 1_000_000}–${it.second / 1_000_000} MHz" } ?: "sin control"}")
        }
        if (!guardian) {
            guardian = true
            Root.ejecutarCada(listOf(ModoRendimientoLogica.cmdGuardian(android.os.Process.myPid(), gpu)))
        }
    }
}
