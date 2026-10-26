package com.xof.ft150ts

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.PowerManager
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** En qué está el ahorro nocturno. */
enum class EstadoNoche(val texto: String) {
    APAGADO("Apagado"),
    FUERA("Fuera del horario"),
    POSPUESTO("Pospuesto"),
    PROTEGIDO("Noche protegida (sin ahorro)"),
    ACTIVO("Ahorrando"),
}

/**
 * Ahorro nocturno: en el horario (00:00–06:30 de fábrica, hora de Android) aplica las acciones elegidas y al
 * terminar deshace exactamente lo que cambió (se guarda qué había antes de cada cambio).
 *
 * NO apaga ni reinicia el teléfono: el encendido desde apagado depende del kernel y del bootloader y no está
 * comprobado en el POCO F4 (ver DiagnosticoEnergia y docs/AHORRO_NOCTURNO.md).
 *
 * Se revisa con una alarma de Android (cambio de estado), al arrancar el teléfono, al cambiar la hora o la zona,
 * al actualizar la app, al abrir la app y al prender/apagar la pantalla. Cada revisión decide desde cero qué toca
 * según la hora actual, así que repetirla no duplica nada y un reinicio no la deja trabada en el ahorro.
 */
@SuppressLint("StaticFieldLeak")   // solo guarda applicationContext (vive lo mismo que la app)
object Nocturno {
    // ---- Ajustes (viajan en el respaldo) ----
    val activo = MutableStateFlow(false)
    val horario = MutableStateFlow(Horario())
    val perfil = MutableStateFlow(PerfilNoche.CONSERVADOR)
    val acciones = MutableStateFlow(PerfilNoche.CONSERVADOR.acciones)
    /** Apps que se restringen/cierran en el perfil extremo (las protegidas nunca). */
    val apps = MutableStateFlow<Set<String>>(emptySet())
    /** Apps que tú marcaste para no tocar nunca (además de las que siempre se protegen). */
    val protegidas = MutableStateFlow<Set<String>>(emptySet())
    val respetarCarPlay = MutableStateFlow(true)
    /** Posponer si tocaste el teléfono en los últimos N min (0 = no posponer por uso). */
    val posponerSiUso = MutableStateFlow(10)

    // ---- Estado (no viaja en el respaldo) ----
    val estado = MutableStateFlow(EstadoNoche.APAGADO)
    val cambios = MutableStateFlow<List<CambioNoche>>(emptyList())
    val evento = MutableStateFlow("")
    /** Próxima revisión programada (reloj, ms). 0 = ninguna. */
    val proxima = MutableStateFlow(0L)
    /** Hasta cuándo esta noche está protegida (no se ahorra). */
    val protegidaHasta = MutableStateFlow(0L)
    /** Android deja alarmas exactas (si no, la alarma puede llegar algunos minutos tarde). */
    val exactas = MutableStateFlow(true)
    /** Las últimas noches con su gasto de batería (la más nueva al final). */
    val historial = MutableStateFlow<List<NocheRegistro>>(emptyList())
    /** "Probar ahora": hasta cuándo dura la noche de prueba (reloj, ms). 0 = no hay prueba. */
    val pruebaHasta = MutableStateFlow(0L)

    const val ACCION_REVISAR = "com.xof.ft150ts.NOCTURNO_REVISAR"
    /** Archivo del registro de cambios: fuera de los respaldos (Respaldo.NO_RESTAURAR). */
    const val PREFS_REGISTRO = "nocturno_registro"
    /** Con la pantalla apagada, cuánto esperar antes de volver a mandar a Doze y dormir la app. */
    private const val VOLVER_A_DORMIR_MS = 5 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val candado = Mutex()
    private var app: Context? = null
    private var prefs: SharedPreferences? = null
    private var registro: SharedPreferences? = null
    private var redormir: Job? = null
    private var reloj: Job? = null
    private var aplicada = false
    private var finVentana = 0L

    /** Al abrir la app (Moto.iniciar). Si una alarma ya la había cargado, solo revisa: así la app se duerme si toca. */
    fun iniciar(c: Context) {
        cargar(c)
        revisarLuego("la app arrancó")
    }

    private fun cargar(c: Context) {
        if (app != null) return
        val ctx = c.applicationContext
        app = ctx
        Root.iniciar(ctx)
        val p = ctx.getSharedPreferences("nocturno", Context.MODE_PRIVATE)
        prefs = p
        activo.value = p.getBoolean("activo", false)
        horario.value = Horario(
            p.getInt("inicio", 0), p.getInt("fin", 6 * 60 + 30),
            (p.getString("dias", "1234567") ?: "1234567").mapNotNull { it.digitToIntOrNull() }.filter { it in 1..7 }.toSet(),
        ).let { if (it.valido) it else Horario() }
        perfil.value = PerfilNoche.entries.firstOrNull { it.name == p.getString("perfil", null) } ?: PerfilNoche.CONSERVADOR
        acciones.value = p.getString("acciones", null)?.split(',')
            ?.mapNotNull { n -> AccionNoche.entries.firstOrNull { it.name == n } }?.toSet() ?: perfil.value.acciones
        apps.value = paquetes(p.getString("apps", ""))
        protegidas.value = paquetes(p.getString("protegidas", ""))
        respetarCarPlay.value = p.getBoolean("respetarCarPlay", true)
        posponerSiUso.value = p.getInt("posponerSiUso", 10).coerceIn(0, 120)

        val r = ctx.getSharedPreferences(PREFS_REGISTRO, Context.MODE_PRIVATE)
        registro = r
        cambios.value = LogicaNoche.deJson(r.getString("cambios", null))
        aplicada = r.getBoolean("aplicada", false)
        finVentana = r.getLong("fin", 0L)
        protegidaHasta.value = r.getLong("protegidaHasta", 0L)
        evento.value = r.getString("evento", "") ?: ""
        historial.value = LogicaNoche.nochesDeJson(r.getString("noches", null))
        pruebaHasta.value = r.getLong("pruebaHasta", 0L)

        // Pantalla: prenderla quita el Doze forzado; apagarla lo regresa a los 5 min (solo mientras la app vive)
        val filtro = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(cx: Context?, i: Intent?) {
                when (i?.action) {
                    Intent.ACTION_SCREEN_ON -> scope.launch { pantallaPrendida() }
                    Intent.ACTION_SCREEN_OFF -> pantallaApagada()
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receptor, filtro, Context.RECEIVER_NOT_EXPORTED)
            else ctx.registerReceiver(receptor, filtro)
        } catch (_: Exception) { }
    }

    private fun paquetes(s: String?) = (s ?: "").split(',').filter { LogicaNoche.paqueteValido(it) }.toSet()

    // ---------------- Ajustes ----------------

    private fun guardarAjustes() {
        val h = horario.value
        prefs?.edit()
            ?.putBoolean("activo", activo.value)
            ?.putInt("inicio", h.inicio)?.putInt("fin", h.fin)?.putString("dias", h.dias.sorted().joinToString(""))
            ?.putString("perfil", perfil.value.name)
            ?.putString("acciones", acciones.value.joinToString(",") { it.name })
            ?.putString("apps", apps.value.joinToString(","))
            ?.putString("protegidas", protegidas.value.joinToString(","))
            ?.putBoolean("respetarCarPlay", respetarCarPlay.value)
            ?.putInt("posponerSiUso", posponerSiUso.value)
            ?.apply()
    }

    fun ponerActivo(on: Boolean) {
        activo.value = on
        guardarAjustes()
        // Con root: alarmas exactas para esta app (solo esta app; Android 14+ las niega de fábrica)
        if (on && Root.activo.value && Root.disponible.value == true) scope.launch {
            app?.let { Root.ejecutarCada(listOf("cmd appops set ${it.packageName} SCHEDULE_EXACT_ALARM allow")) }
        }
        revisarLuego(if (on) "lo prendiste" else "lo apagaste")
    }
    fun ponerHorario(h: Horario) { if (!h.valido) return; horario.value = h; guardarAjustes(); revisarLuego("cambiaste el horario") }
    fun ponerPerfil(p: PerfilNoche) {
        perfil.value = p
        // Se conservan DATOS/WIFI si los prendiste a mano (ningún perfil los trae)
        acciones.value = p.acciones + (acciones.value intersect setOf(AccionNoche.DATOS, AccionNoche.WIFI))
        guardarAjustes()
    }
    fun ponerAccion(a: AccionNoche, on: Boolean) { acciones.value = if (on) acciones.value + a else acciones.value - a; guardarAjustes() }
    fun ponerApp(p: String, on: Boolean) {
        if (!LogicaNoche.paqueteValido(p)) return
        apps.value = if (on) apps.value + p else apps.value - p; guardarAjustes()
    }
    fun ponerProtegida(p: String, on: Boolean) {
        if (!LogicaNoche.paqueteValido(p)) return
        protegidas.value = if (on) protegidas.value + p else protegidas.value - p
        if (on) apps.value = apps.value - p
        guardarAjustes()
    }
    fun ponerRespetarCarPlay(on: Boolean) { respetarCarPlay.value = on; guardarAjustes() }
    fun ponerPosponerSiUso(m: Int) { posponerSiUso.value = m.coerceIn(0, 120); guardarAjustes() }

    /** Regresa todos los ajustes a los de fábrica (deshace primero lo que esté aplicado). */
    fun predeterminados() {
        scope.launch {
            candado.withLock { revertirTodo("Ajustes de fábrica") }
            activo.value = false; horario.value = Horario(); perfil.value = PerfilNoche.CONSERVADOR
            acciones.value = PerfilNoche.CONSERVADOR.acciones; apps.value = emptySet(); protegidas.value = emptySet()
            respetarCarPlay.value = true; posponerSiUso.value = 10
            guardarAjustes()
            revisar("ajustes de fábrica")
        }
    }

    /** Esta noche no se ahorra (sesión que quieres mantener). Deshace lo aplicado. */
    fun protegerEstaNoche() {
        scope.launch {
            val ahora = ZonedDateTime.now()
            val fin = horario.value.ventanaActual(ahora)?.second ?: horario.value.proximoInicio(ahora)?.plusMinutes(horario.value.minutos.toLong())
            protegidaHasta.value = fin?.toInstant()?.toEpochMilli() ?: 0L
            registro?.edit()?.putLong("protegidaHasta", protegidaHasta.value)?.apply()
            revisar("protegiste esta noche")
        }
    }

    fun quitarProteccion() {
        protegidaHasta.value = 0L
        registro?.edit()?.putLong("protegidaHasta", 0L)?.apply()
        revisarLuego("quitaste la protección")
    }

    /** Desactivar ya: deshace todo lo aplicado y apaga el ahorro nocturno (también termina una prueba). */
    fun desactivarYa() {
        scope.launch {
            activo.value = false
            guardarAjustes()
            ponerPrueba(0L)
            candado.withLock { revertirTodo("Lo desactivaste") }
            revisar("desactivado")
        }
    }

    /** "Probar ahora": una noche de [minutos] desde ya, con las acciones elegidas (sin posponer). */
    fun probarAhora(minutos: Int = 15) {
        ponerPrueba(System.currentTimeMillis() + minutos.coerceIn(5, 60) * 60_000L)
        revisarLuego("prueba de $minutos min")
    }

    fun terminarPrueba() {
        ponerPrueba(0L)
        revisarLuego("terminaste la prueba")
    }

    private fun ponerPrueba(hasta: Long) {
        pruebaHasta.value = hasta
        registro?.edit()?.putLong("pruebaHasta", hasta)?.apply()
    }

    fun borrarHistorial() {
        historial.value = emptyList()
        registro?.edit()?.remove("noches")?.apply()
    }

    /** % de batería y si está cargando, ahora. */
    private fun bateria(ctx: Context): Pair<Int, Boolean> {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val pct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val carga = (i?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return pct to carga
    }

    /** Al terminar una noche aplicada: guarda cuánto gastó. */
    private fun cerrarNoche() {
        val ctx = app ?: return
        val r = registro ?: return
        val desde = r.getLong("nocheDesde", 0L)
        if (desde <= 0L) return
        val (pct, carga) = bateria(ctx)
        val n = NocheRegistro(desde, System.currentTimeMillis(), r.getInt("nochePct", -1), pct, r.getString("nochePerfil", "") ?: "",
            carga || r.getBoolean("nocheCarga", false), r.getBoolean("nochePrueba", false))
        val l = LogicaNoche.agregarNoche(historial.value, n)
        historial.value = l
        r.edit().putString("noches", LogicaNoche.nochesAJson(l)).remove("nocheDesde").apply()
    }

    // ---------------- Revisión ----------------

    fun revisarLuego(motivo: String) { scope.launch { revisar(motivo) } }

    /** Desde el receptor de alarmas / arranque: hace la revisión y avisa al terminar. */
    fun revisarDesdeAfuera(c: Context, motivo: String, listo: () -> Unit) {
        cargar(c)
        val trabajo = scope.launch { revisar(motivo) }
        scope.launch {
            // El receptor tiene poco tiempo: si root tarda, se suelta el receptor y la revisión sigue sola
            withTimeoutOrNull(8_000L) { trabajo.join() }
            listo()
        }
    }

    suspend fun revisar(motivo: String) = candado.withLock {
        val ctx = app ?: return@withLock
        val ahora = ZonedDateTime.now(ZoneId.systemDefault())
        val ms = ahora.toInstant().toEpochMilli()
        // Una prueba manda sobre el horario (aunque el ahorro esté apagado); al terminar se deshace como una noche
        val prueba = pruebaHasta.value > ms
        if (!prueba && pruebaHasta.value != 0L) ponerPrueba(0L)
        val ventana = when {
            prueba -> ahora to Instant.ofEpochMilli(pruebaHasta.value).atZone(ahora.zone)
            activo.value -> horario.value.ventanaActual(ahora)
            else -> null
        }

        // Fuera del horario (o apagado): deshacer lo que haya quedado y esperar la siguiente noche
        if (ventana == null) {
            if (aplicada || LogicaNoche.pendientes(cambios.value).isNotEmpty()) revertirTodo("Terminó el horario ($motivo)")
            estado.value = if (activo.value) EstadoNoche.FUERA else EstadoNoche.APAGADO
            val pendientes = LogicaNoche.pendientes(cambios.value).isNotEmpty()
            val siguiente = when {
                pendientes -> ms + 10 * 60_000L       // no se pudo deshacer (sin root): se reintenta
                activo.value -> horario.value.proximoInicio(ahora)?.toInstant()?.toEpochMilli() ?: 0L
                else -> 0L
            }
            programar(ctx, siguiente)
            return@withLock
        }
        val fin = ventana.second.toInstant().toEpochMilli()

        if (!prueba && protegidaHasta.value >= fin) {
            if (aplicada) revertirTodo("Noche protegida")
            estado.value = EstadoNoche.PROTEGIDO
            programar(ctx, fin)
            return@withLock
        }

        if (!aplicada) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val usoMin = if (pm.isInteractive) 0L
                else ModoSueno.ultimoUso.takeIf { it > 0 }?.let { (ms - it) / 60_000L }
            val carPlay = ms - CarPlay.ultimoEnVivo.value < 5 * 60_000L
            // En una prueba que pediste no se pospone por uso ni por CarPlay (sí si vas rodando)
            val porque = if (prueba) (if (ModoSueno.moviendose()) "vas rodando" else null)
                else LogicaNoche.motivoPosponer(ModoSueno.moviendose() || Energia.enMoto.value, carPlay,
                    false, usoMin, posponerSiUso.value, respetarCarPlay.value)
            if (porque != null) {
                estado.value = EstadoNoche.POSPUESTO
                anotar("Pospuesto ${LogicaNoche.POSPONER_MIN} min: $porque")
                programar(ctx, minOf(fin, ms + LogicaNoche.POSPONER_MIN * 60_000L))
                return@withLock
            }
            aplicar(ctx, fin)
        } else {
            // Ya estaba aplicado (la app se reinició o es la alarma de en medio): solo lo ligero
            if (AccionNoche.APP_SUENO in acciones.value) ModoSueno.dormirPorNoche()
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) quitarDoze("pantalla prendida")
        }
        estado.value = EstadoNoche.ACTIVO
        programar(ctx, fin)
    }

    // ---------------- Aplicar / deshacer ----------------

    private fun aplicar(ctx: Context, fin: Long) {
        val elegidas = acciones.value
        val hechos = mutableListOf<CambioNoche>()

        if (AccionNoche.APP_SUENO in elegidas) {
            val ok = ModoSueno.listo
            if (ok) ModoSueno.dormirPorNoche()
            hechos += CambioNoche(AccionNoche.APP_SUENO, ok = ok, detalle = if (ok) "app dormida" else "la app no estaba corriendo")
        }
        if (AccionNoche.SINCRONIZAR in elegidas) {
            hechos += try {
                val antes = ContentResolver.getMasterSyncAutomatically()
                if (antes) ContentResolver.setMasterSyncAutomatically(false)
                CambioNoche(AccionNoche.SINCRONIZAR, anterior = if (antes) "1" else "0", ok = true,
                    detalle = if (antes) "pausada" else "ya estaba pausada")
            } catch (e: Exception) {
                CambioNoche(AccionNoche.SINCRONIZAR, ok = false, detalle = "Android no dejó: ${e.javaClass.simpleName}")
            }
        }
        hechos += aplicarRoot(ctx, elegidas)
        guardarCambios(hechos)
        aplicada = true
        finVentana = fin
        val (pct, carga) = bateria(ctx)
        registro?.edit()?.putBoolean("aplicada", true)?.putLong("fin", fin)
            ?.putLong("nocheDesde", System.currentTimeMillis())?.putInt("nochePct", pct)?.putBoolean("nocheCarga", carga)
            ?.putString("nochePerfil", perfil.value.nombre)?.putBoolean("nochePrueba", pruebaHasta.value > System.currentTimeMillis())
            ?.apply()
        val bien = hechos.count { it.ok }
        anotar("Entró al ahorro: $bien de ${hechos.size} acciones aplicadas")
    }

    /** Las acciones de root, en dos pasos: leer cómo está todo y luego cambiar solo lo que hace falta. */
    private fun aplicarRoot(ctx: Context, elegidas: Set<AccionNoche>): List<CambioNoche> {
        val deRoot = elegidas.filter { it.root }
        if (deRoot.isEmpty()) return emptyList()
        if (!Root.activo.value || Root.disponible.value != true) {
            return deRoot.map { CambioNoche(it, ok = false, detalle = "requiere root (no permitido en Magisk o apagado)") }
        }
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val propio = ctx.packageName
        val elegibles = if (AccionNoche.RESTRINGIR in elegidas || AccionNoche.CERRAR in elegidas)
            apps.value.filter { esElegible(ctx, it, propio) } else emptyList()

        // 1) Leer
        val lecturas = mutableListOf<String>()
        val iAhorro = lecturas.size.also { lecturas += LogicaNoche.cmdLeerAhorro() }
        val iDatos = lecturas.size.also { lecturas += LogicaNoche.cmdLeerDatos() }
        val iWifi = lecturas.size.also { lecturas += LogicaNoche.cmdLeerWifi() }
        val iCarPlay = lecturas.size.also { lecturas += LogicaNoche.cmdCarPlayAbierto() }
        val iGrupos = lecturas.size
        if (AccionNoche.RESTRINGIR in elegidas) elegibles.forEach { lecturas += LogicaNoche.cmdLeerGrupo(it) }
        val leido = Root.ejecutarCada(lecturas)
            ?: return deRoot.map { CambioNoche(it, ok = false, detalle = "root no respondió") }

        // 2) Decidir y cambiar
        data class Plan(val cambio: CambioNoche, val cmd: String?)
        val planes = mutableListOf<Plan>()
        if (AccionNoche.AHORRO in elegidas) {
            val antes = LogicaNoche.leerBool(leido[iAhorro].salida)
            planes += if (antes == true) Plan(CambioNoche(AccionNoche.AHORRO, ok = true, detalle = "ya estaba prendido", revertido = true), null)
            else Plan(CambioNoche(AccionNoche.AHORRO, anterior = "0", ok = true, detalle = "prendido"), LogicaNoche.cmdAhorro(true))
        }
        if (AccionNoche.DOZE in elegidas) {
            planes += when {
                pm.isInteractive -> Plan(CambioNoche(AccionNoche.DOZE, ok = false, detalle = "pantalla prendida: se aplica al apagarla"), null)
                !Energia.segundoPlano.value -> Plan(CambioNoche(AccionNoche.DOZE, ok = false,
                    detalle = "requiere \"Mantener activa en segundo plano\" (para quitarlo al prender la pantalla)"), null)
                else -> Plan(CambioNoche(AccionNoche.DOZE, ok = true, detalle = "forzado"), LogicaNoche.cmdDoze(true))
            }
        }
        if (AccionNoche.RESTRINGIR in elegidas) {
            elegibles.forEachIndexed { k, p ->
                val grupo = LogicaNoche.leerGrupo(leido[iGrupos + k].salida)
                val nombre = grupo?.let { LogicaNoche.GRUPOS[it] }
                planes += when {
                    !leido[iGrupos + k].ok || nombre == null ->
                        Plan(CambioNoche(AccionNoche.RESTRINGIR, p, ok = false, detalle = "grupo desconocido ($grupo): no se toca"), null)
                    nombre == "restricted" ->
                        Plan(CambioNoche(AccionNoche.RESTRINGIR, p, ok = true, detalle = "ya estaba restringida", revertido = true), null)
                    else -> Plan(CambioNoche(AccionNoche.RESTRINGIR, p, nombre, ok = true, detalle = "$nombre → restricted"),
                        LogicaNoche.cmdGrupo(p, "restricted"))
                }
            }
        }
        if (AccionNoche.CERRAR in elegidas) {
            elegibles.forEach { p -> planes += Plan(CambioNoche(AccionNoche.CERRAR, p, ok = true, detalle = "cerrada", revertido = true), LogicaNoche.cmdCerrar(p)) }
        }
        if (AccionNoche.CERRAR_DIPLAY in elegidas) {
            val abierta = (leido[iCarPlay].salida.trim().lineSequence().lastOrNull()?.trim()?.toIntOrNull() ?: 1) > 0
            val d = CarPlay.buscar(ctx)
            planes += when {
                d == null -> Plan(CambioNoche(AccionNoche.CERRAR_DIPLAY, ok = true, detalle = "DiPlay no está instalado", revertido = true), null)
                abierta -> Plan(CambioNoche(AccionNoche.CERRAR_DIPLAY, ok = false, detalle = "la pantalla de CarPlay está abierta: no se cierra"), null)
                else -> Plan(CambioNoche(AccionNoche.CERRAR_DIPLAY, d.paquete, ok = true, detalle = "cerrado", revertido = true), LogicaNoche.cmdCerrar(d.paquete))
            }
        }
        if (AccionNoche.DATOS in elegidas) {
            planes += if (LogicaNoche.leerBool(leido[iDatos].salida) == true)
                Plan(CambioNoche(AccionNoche.DATOS, anterior = "1", ok = true, detalle = "apagados"), LogicaNoche.cmdDatos(false))
            else Plan(CambioNoche(AccionNoche.DATOS, ok = true, detalle = "ya estaban apagados", revertido = true), null)
        }
        if (AccionNoche.WIFI in elegidas) {
            planes += if (LogicaNoche.leerBool(leido[iWifi].salida) == true)
                Plan(CambioNoche(AccionNoche.WIFI, anterior = "1", ok = true, detalle = "apagado"), LogicaNoche.cmdWifi(false))
            else Plan(CambioNoche(AccionNoche.WIFI, ok = true, detalle = "ya estaba apagado", revertido = true), null)
        }
        val conCmd = planes.filter { it.cmd != null }
        val res = if (conCmd.isEmpty()) emptyList() else Root.ejecutarCada(conCmd.map { it.cmd!! })
        var j = 0
        return planes.map { pl ->
            if (pl.cmd == null) pl.cambio
            else {
                val r = res?.getOrNull(j++)
                if (r != null && r.ok) pl.cambio
                else pl.cambio.copy(ok = false, detalle = "falló: " + (r?.salida?.take(80) ?: "root no respondió"), revertido = true)
            }
        }
    }

    /** ¿Se puede restringir/cerrar esta app? Instalada, no del sistema y no protegida. */
    private fun esElegible(ctx: Context, p: String, propio: String): Boolean {
        if (!LogicaNoche.paqueteValido(p) || LogicaNoche.protegidoSiempre(p, propio, protegidas.value + extrasPredeterminadas(ctx))) return false
        return try {
            val ai = ctx.packageManager.getApplicationInfo(p, 0)
            ai.flags and ApplicationInfo.FLAG_SYSTEM == 0
        } catch (_: Exception) { false }
    }

    /** Teléfono, SMS y launcher que usas: nunca se tocan. */
    private fun extrasPredeterminadas(ctx: Context): Set<String> {
        val s = mutableSetOf<String>()
        try { (ctx.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.defaultDialerPackage?.let { s += it } } catch (_: Exception) { }
        try { android.provider.Telephony.Sms.getDefaultSmsPackage(ctx)?.let { s += it } } catch (_: Exception) { }
        try {
            ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName?.let { s += it }
        } catch (_: Exception) { }
        return s
    }

    /** Deshace todo lo pendiente del registro. Lo que no se pueda deshacer se queda pendiente y se reintenta. */
    private fun revertirTodo(motivo: String) {
        val lista = cambios.value
        val pendientes = LogicaNoche.pendientes(lista)
        val nuevos = lista.toMutableList()
        fun marcar(c: CambioNoche, ok: Boolean, det: String) {
            val i = nuevos.indexOf(c)
            if (i >= 0) nuevos[i] = c.copy(revertido = ok, detalle = if (ok) c.detalle else "${c.detalle} · no se pudo deshacer: $det")
        }
        // Sin root
        for (c in pendientes) when (c.accion) {
            AccionNoche.APP_SUENO -> { ModoSueno.salirDeNoche("Terminó el horario nocturno"); marcar(c, true, "") }
            AccionNoche.SINCRONIZAR -> {
                val ok = try { if (c.anterior == "1") ContentResolver.setMasterSyncAutomatically(true); true } catch (_: Exception) { false }
                marcar(c, ok, "Android no dejó")
            }
            AccionNoche.CERRAR, AccionNoche.CERRAR_DIPLAY -> marcar(c, true, "")
            else -> Unit
        }
        // Con root (en una sola sesión)
        val conRoot = pendientes.mapNotNull { c ->
            val cmd = when (c.accion) {
                AccionNoche.AHORRO -> LogicaNoche.cmdAhorro(false)
                AccionNoche.DOZE -> LogicaNoche.cmdDoze(false)
                AccionNoche.RESTRINGIR -> if (c.anterior in LogicaNoche.GRUPOS.values && LogicaNoche.paqueteValido(c.objetivo))
                    LogicaNoche.cmdGrupo(c.objetivo, c.anterior) else null
                AccionNoche.DATOS -> if (c.anterior == "1") LogicaNoche.cmdDatos(true) else null
                AccionNoche.WIFI -> if (c.anterior == "1") LogicaNoche.cmdWifi(true) else null
                else -> null
            }
            if (cmd == null) { if (c.accion.root) marcar(c, true, ""); null } else c to cmd
        }
        if (conRoot.isNotEmpty()) {
            val res = Root.ejecutarCada(conRoot.map { it.second })
            conRoot.forEachIndexed { k, (c, _) ->
                val r = res?.getOrNull(k)
                marcar(c, r?.ok == true, r?.salida?.take(80) ?: "sin root")
            }
        }
        cambios.value = nuevos
        registro?.edit()?.putString("cambios", LogicaNoche.aJson(nuevos))?.apply()
        val faltan = LogicaNoche.pendientes(nuevos)
        // La noche terminó (aunque algo quede por deshacer): se guarda cuánto gastó
        if (aplicada) cerrarNoche()
        if (faltan.isEmpty()) {
            aplicada = false
            registro?.edit()?.putBoolean("aplicada", false)?.apply()
            anotar("Deshecho: $motivo")
        } else {
            anotar("$motivo · faltan ${faltan.size} por deshacer (se reintenta): " + faltan.joinToString { it.accion.nombre })
        }
    }

    /**
     * [nuevaNoche] = true al entrar: el historial empieza de cero, pero se conservan los pendientes de antes (lo que
     * no se pudo deshacer). false = se agrega a la noche en curso sin perder su historial.
     */
    private fun guardarCambios(nuevos: List<CambioNoche>, nuevaNoche: Boolean = true) {
        val todo = (if (nuevaNoche) LogicaNoche.pendientes(cambios.value) else cambios.value) + nuevos
        cambios.value = todo
        registro?.edit()?.putString("cambios", LogicaNoche.aJson(todo))?.apply()
    }

    // ---------------- Pantalla y Doze ----------------

    private suspend fun pantallaPrendida() {
        redormir?.cancel()
        candado.withLock { if (aplicada) quitarDoze("prendiste la pantalla") }
    }

    private fun quitarDoze(motivo: String) {
        val doze = LogicaNoche.pendientes(cambios.value).filter { it.accion == AccionNoche.DOZE }
        if (doze.isEmpty()) return
        val r = Root.ejecutarCada(listOf(LogicaNoche.cmdDoze(false)))?.firstOrNull()
        if (r?.ok == true) {
            val nuevos = cambios.value.map { if (it in doze) it.copy(revertido = true, detalle = "quitado: $motivo") else it }
            cambios.value = nuevos
            registro?.edit()?.putString("cambios", LogicaNoche.aJson(nuevos))?.apply()
        }
    }

    private fun pantallaApagada() {
        redormir?.cancel()
        if (!aplicada) return
        redormir = scope.launch {
            delay(VOLVER_A_DORMIR_MS)
            candado.withLock {
                val ctx = app ?: return@withLock
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!aplicada || pm.isInteractive || estado.value != EstadoNoche.ACTIVO) return@withLock
                if (AccionNoche.APP_SUENO in acciones.value) ModoSueno.dormirPorNoche()
                if (AccionNoche.DOZE in acciones.value && Energia.segundoPlano.value &&
                    LogicaNoche.pendientes(cambios.value).none { it.accion == AccionNoche.DOZE } &&
                    Root.activo.value && Root.disponible.value == true
                ) {
                    val r = Root.ejecutarCada(listOf(LogicaNoche.cmdDoze(true)))?.firstOrNull()
                    guardarCambios(listOf(CambioNoche(AccionNoche.DOZE, ok = r?.ok == true,
                        detalle = if (r?.ok == true) "forzado otra vez (pantalla apagada)" else "falló al volver a forzar",
                        revertido = r?.ok != true)), nuevaNoche = false)
                }
            }
        }
    }

    // ---------------- Alarma ----------------

    private fun programar(ctx: Context, cuando: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 7, Intent(ctx, NocturnoReceiver::class.java).setAction(ACCION_REVISAR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        reloj?.cancel()
        proxima.value = cuando
        if (cuando <= 0L) return
        // Reloj interno mientras la app vive (servicio en primer plano): exacto aunque Android no deje alarmas
        // exactas. La alarma de abajo es el respaldo para cuando Android cierra la app.
        reloj = scope.launch {
            delay((cuando + 1_000L - System.currentTimeMillis()).coerceAtLeast(0L))
            revisar("hora del horario")
        }
        // Un segundo después del cambio, para que la revisión ya caiga del otro lado
        val t = cuando + 1_000L
        val exacta = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        exactas.value = exacta
        try {
            if (exacta) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        } catch (_: SecurityException) {
            exactas.value = false
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    private fun anotar(t: String) {
        val txt = ModoSueno.hora(System.currentTimeMillis()) + " · " + t
        evento.value = txt
        registro?.edit()?.putString("evento", txt)?.apply()
    }

    fun horaTexto(ms: Long): String = if (ms <= 0L) "--" else
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { "%02d:%02d".format(it.hour, it.minute) +
            if (it.toLocalDate() != java.time.LocalDate.now()) " (${Horario.NOMBRES_DIAS[it.dayOfWeek.value - 1]})" else "" }
}

/**
 * Alarmas del horario nocturno y eventos del sistema que obligan a recalcular: arranque, actualización de la app,
 * cambio de hora o de zona. No está exportado: solo Android (o esta app) le puede mandar algo.
 */
class NocturnoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val motivo = when (i.action) {
            Nocturno.ACCION_REVISAR -> "alarma del horario"
            Intent.ACTION_BOOT_COMPLETED -> "el teléfono arrancó"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "la app se actualizó"
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> "cambió la hora o la zona"
            else -> return
        }
        val pendiente = goAsync()
        Nocturno.revisarDesdeAfuera(c, motivo) { pendiente.finish() }
    }
}
