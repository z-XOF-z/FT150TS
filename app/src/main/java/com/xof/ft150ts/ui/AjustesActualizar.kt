package com.xof.ft150ts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xof.ft150ts.Actualizador
import com.xof.ft150ts.EstadoActualizacion

/** Bloque "Actualizar" (dentro de Acerca de): desde GitHub o desde un APK que elijas. */
@Composable
fun BloqueActualizar() {
    val ctx = LocalContext.current
    val estado by Actualizador.estado.collectAsStateWithLifecycle()
    val actual = remember { Actualizador.versionActual(ctx) }
    LaunchedEffect(Unit) { Actualizador.cargar(ctx) }
    val betas by Actualizador.betas.collectAsStateWithLifecycle()
    val elegirApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Actualizador.instalarDesdeUri(ctx, uri)
    }

    Text("Actualizar", color = Paleta.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    FilaSwitch("Versiones de prueba (beta)",
        "También busca las versiones en pruebas de GitHub. Pueden traer errores; tus datos se conservan igual.",
        betas, Paleta.ambar) { Actualizador.ponerBetas(ctx, it) }
    when (val e = estado) {
        EstadoActualizacion.Nada -> Unit
        EstadoActualizacion.Buscando -> Text("Buscando en GitHub…", color = Paleta.textoSec, fontSize = 12.sp)
        is EstadoActualizacion.AlDia -> Text("Tienes la más nueva (${e.version}).", color = Paleta.verde, fontSize = 12.sp)
        is EstadoActualizacion.Hay -> {
            Dato("Nueva versión", e.p.version + if (e.p.beta) " (beta)" else "", if (e.p.beta) Paleta.ambar else Paleta.lima)
            if (e.p.tamano > 0) Dato("Tamaño", "%.1f MB".format(e.p.tamano / 1_048_576.0))
            if (e.p.notas.isNotBlank()) Text(e.p.notas.take(600), color = Paleta.textoSec, fontSize = 11.sp)
            BotonDeportivo("Descargar e instalar ${e.p.version}", { Actualizador.descargarEInstalar(ctx, e.p) },
                Modifier.fillMaxWidth(), tamanoTexto = 12.sp)
        }
        is EstadoActualizacion.Descargando -> Text("Descargando… ${e.pct}%", color = Paleta.lima, fontSize = 12.sp)
        EstadoActualizacion.Instalando -> Text("Revisando el APK y abriendo el instalador de Android…", color = Paleta.lima, fontSize = 12.sp)
        is EstadoActualizacion.Error -> Text(e.texto, color = Paleta.ambar, fontSize = 12.sp)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonDeportivo("Buscar actualización", { Actualizador.buscar(ctx) }, Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
        BotonDeportivo("Instalar un APK", { elegirApk.launch(arrayOf("application/vnd.android.package-archive")) },
            Modifier.weight(1f), primario = false, tamanoTexto = 12.sp)
    }
    Text("Versión instalada: $actual. Se instala encima: tus ajustes, rodadas y respaldos se quedan. Solo acepta un APK de " +
        "FT150TS con la misma firma que esta app y más nuevo; antes guarda un respaldo en Descargas/FT150TS. Android " +
        "te pide confirmar (y la primera vez, permitir \"Instalar apps desconocidas\").",
        color = Paleta.textoSec, fontSize = 11.sp)
}
