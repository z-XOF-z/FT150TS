## FT150TS Dashboard v3.4.3 (estable) + módulo de Magisk "sin barra"

### Nuevo: módulo de Magisk para quitar la barra de las ventanas flotantes
`FT150TS-sin-barra-v1.1.zip` (adjunto): quita la barra de **tres puntos** de arriba y la **píldora** de abajo de las
ventanas flotantes de HyperOS 1. CarPlay ocupa toda la pestaña Auto.
- Es una superposición de recursos de SystemUI (dos medidas en 0). No toca código ni particiones.
- Se prende después del arranque y **se apaga sola** si SystemUI falla.
- Se instala desde Magisk → Módulos → Instalar desde almacenamiento, y se reinicia.
- Efecto secundario: las demás apps en ventana flotante tampoco tienen barra (se cierran desde Recientes).

### FT150TS
- Detecta el módulo. Si está activo, ya no alarga la ventana de CarPlay por debajo de la pantalla, para no cortar el
  dock de CarPlay.

Probado en un POCO F4 con HyperOS 1: barras en 0, SystemUI estable, CarPlay de la pestaña al borde de abajo.
69 pruebas automáticas pasan.
