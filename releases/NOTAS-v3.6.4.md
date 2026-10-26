## FT150TS Dashboard v3.6.4 (estable)

### Arreglo: CarPlay aparecía flotando encima del tablero después de horas bloqueado
Con el teléfono bloqueado varias horas, DiPlay se reinicia y HyperOS regresa su ventana a la posición de fábrica.
- Al esconderla, FT150TS ahora revisa que de verdad quedó fuera de la pantalla y lo reintenta (antes la orden podía
  caer a media animación del desbloqueo y HyperOS la volvía a meter).
- Mientras FT150TS está en pantalla, revisa cada 6 s que la ventana escondida siga fuera.
- Se esconde más allá del lado más largo de la pantalla: con el teléfono bloqueado el sistema se pone en vertical y
  antes la ventana quedaba a la vista al volver a horizontal.

### Cambio de pestaña más fluido (Tablero ↔ Moto)
- La página a la que vas despierta al terminar el deslizamiento, no a la mitad; las de en medio (el mapa de Auto) ya
  no despiertan al pasar.
- Al cambiar de pestaña ya no se recompone toda la pantalla, solo la fila de pestañas.
- Ajustes solo arma las secciones que se ven (antes las 19 estaban armadas siempre).
- Las tarjetas de los temas de estilo ya no repintan su adorno cada vez que cambia un número adentro.
- Las gráficas pixeladas de Moto se dibujan con una llamada por color en vez de ~900 rectángulos.
- El perfil de compilación (Baseline Profile) tenía mal el formato desde la v3.4 y no servía: ahora sí se compila
  por adelantado el código de la app y de Compose.

- Cada página va en su propia capa: al deslizar solo se mueve, sin volver a grabar su dibujo en cada cuadro.

Medido en un POCO F4 con HyperOS 1, misma prueba contra la v3.6.3 (5 idas y vueltas Tablero ↔ Moto, en frío): el peor
cuadro bajó de 35–83 ms a 36–47 ms y los cuadros de más de 20 ms de ~19 a ~17 por cada 120. Es una mejora moderada:
el límite ahora es el hilo de render, que tarda ~15 ms por cuadro en dibujar las dos páginas a 120 Hz. 71 pruebas pasan.
