package com.xof.ft150ts

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Red de seguridad de las tareas de fondo. La app es el launcher: un error raro en segundo plano (una medida extraña
 * de HyperOS, un archivo que no se pudo leer) cerraba TODA la pantalla de inicio. Con este manejador en cada
 * CoroutineScope el error se anota (logcat -s FT150-ERR, y el último queda en [ultimo]) y la app sigue.
 * Ojo: solo atrapa lo que se escapa de una tarea; cada parte sigue revisando sus propios errores.
 */
object Seguro {
    val ultimo = MutableStateFlow("")

    val manejador = CoroutineExceptionHandler { ctx, e ->
        val donde = ctx[kotlinx.coroutines.CoroutineName]?.name ?: ""
        android.util.Log.e("FT150-ERR", "Tarea de fondo $donde: ${e.javaClass.simpleName}: ${e.message}", e)
        ultimo.value = "${e.javaClass.simpleName}: ${e.message}".take(200)
    }
}
