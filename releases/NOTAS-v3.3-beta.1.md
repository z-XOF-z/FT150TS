## FT150TS Dashboard v3.3-beta.1 (versión de prueba)

### CarPlay, ahora completo
- La v3.1 solo pedía el "mapa de tablero" de DiPlay, que el iPhone puede no mandar en un teléfono: por eso no funcionaba.
- Ahora la página Auto abre la **pantalla completa de CarPlay** (toque, música, Siri) de DiPlay en el modo que elijas:
  - **Ventana:** flotante justo sobre el mapa.
  - **Dividida:** CarPlay y FT150TS juntos en la pantalla.
  - **Total:** pantalla completa.
  - **Mapa:** el modo anterior.
- Se puede abrir solo al entrar a Auto, sin ciclos.
- Si Android no deja la ventana flotante, FT150TS lo detecta, te avisa y te ofrece **Dividida**.
- Con root: prender las ventanas flotantes o forzar la ventana.
- Toca la franja de CarPlay para elegir el modo.

### Nuevo
- **Tema Tinta:** viñetas de cómic con contorno de tinta, sombra sólida del color de acento, trama de puntos y líneas
  de velocidad. Ajustes → Apariencia.
- **SOS por caída:** si te caes rodando y el teléfono queda quieto, cuenta 30 s con un botón grande "ESTOY BIEN". Si
  no lo tocas, manda tu ubicación por SMS a tu contacto. Apagado de fábrica; trae modo de prueba.
- **Ahorro nocturno:** gasto real de batería por noche (%/h y promedio) y "Probar ahora 15 min".

### Arreglos
- El mapa (con rumbo arriba) ya no se dibuja encima de la franja de CarPlay.
- En vertical, los botones del mapa ya no chocan con el cuadro de velocidad.
- La pantalla de arranque mostraba "v2.9.2": ahora muestra la versión real.

### Estado
- 55 pruebas automáticas pasan.
- **Probado en un POCO F4** (HyperOS 3, root): CarPlay se abre solo como ventana sobre el mapa, a lo ancho del mapa.
- Probado en el emulador: pantalla dividida, el tema Tinta y un ciclo completo de "Probar ahora" (entra, se deshace
  solo y guarda la noche).
- **Falta probar con el iPhone conectado.**
- Con root, "Dar permisos" ahora también da alarmas exactas, y el ahorro nocturno usa un reloj interno para entrar y
  salir a la hora.

> Misma firma que las versiones anteriores: se instala encima sin borrar tus datos.
> Con "Versiones de prueba (beta)" prendido, la app la encuentra en Ajustes → Acerca de → Buscar actualización.
