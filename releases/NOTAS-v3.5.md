## FT150TS Dashboard v3.5 (estable) — 4 tableros nuevos

El diseño del tablero ahora se cambia **sin tocar el tema**: todos los tableros usan los colores, formas y adornos
de tu tema actual.

### Tableros nuevos (Ajustes → Apariencia → Estilo del tablero)
- **Pista:** barra de RPM de 40 segmentos tipo MotoGP con números y marca del punto de cambio. Marcha gigante al
  centro, velocidad, RPM, temperatura del motor y batería, y una fila con viaje, rango, máxima y clima.
- **Minimal:** velocidad enorme con un arco fino de RPM y la marcha. Lo más legible para manejar.
- **Telemetría:** gráfica en vivo de los últimos 60 s de velocidad y RPM (con la línea del punto de cambio), más
  barras de inclinación y de acelerar/frenar en g.
- **Bento:** mosaico de tarjetas. La de RPM es un indicador contra la meta (zonas y punto de cambio).
- Siguen **LCD Sport** y **Clásico**.

### Atajo
**Mantén presionado el tablero** para pasar al siguiente diseño; un aviso dice cuál quedó.

### Animaciones y estabilidad
- La marcha entra deslizándose: hacia arriba si subes, hacia abajo si bajas.
- La velocidad cuenta suave en vez de brincar. Por debajo de 2 km/h marca 0, para que el GPS no la haga temblar
  estando parado.
- Las RPM se mueven con el PID; la barra o el arco destellan al pasar el punto de cambio.
- La animación del switch (Barrido, CRT, Escáner, Pulso, Píxeles) funciona en todos los tableros. La telemetría no
  grafica esos segundos.
- Con "quitar animaciones" del sistema, todo cambia sin animar.

Diseño guiado por UI UX Pro Max: el valor actual grande y con su texto (no solo color), las RPM contra la meta y una
gráfica en vivo. Probado en un POCO F4 con HyperOS 1 (con el Modo demo). 69 pruebas automáticas pasan.
