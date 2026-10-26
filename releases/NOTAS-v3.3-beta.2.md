## FT150TS Dashboard v3.3-beta.2 (versión de prueba)

### CarPlay en toda la pestaña Auto
- Modo nuevo **Pestaña** (viene de fábrica): CarPlay completo (Apple Maps, música, mensajes, todo) ocupa **toda la
  pestaña Auto** y tus pestañas de FT150TS quedan arriba (o al lado en Vértice) para deslizar.
- Al deslizar o tocar otra pestaña, CarPlay se esconde **sin cortarse**, y regresa al instante al volver a Auto.
  También se esconde si te vas a otra app o en reposo.
- Necesita root (Magisk). Sin root se abre en pantalla completa.
- Toca la franja "carplay" para elegir el modo: Pestaña, Ventana, Dividida, Total o Mapa.

### Arreglos
- **Deslizar sobre las pestañas ya cambia de pestaña.** Antes la fila de pestañas se comía el gesto y solo funcionaba
  sobre el logo o el reloj.
- Mientras CarPlay tapa la pestaña Auto, el mapa de abajo deja de dibujarse y de girar (ahorra batería).

### Probado en un POCO F4 (HyperOS 3, Magisk, iPhone conectado)
- CarPlay llenó la pestaña Auto debajo de las pestañas.
- 3 de 3 ciclos de deslizar a otra pestaña y regresar: se escondió y volvió sin reconectar.
- Atrás regresa al Tablero y esconde CarPlay.
- 56 pruebas automáticas pasan.

> Misma firma: se instala encima sin borrar tus datos. Con "Versiones de prueba (beta)" prendido, la app la
> encuentra en Ajustes → Acerca de → Buscar actualización.
