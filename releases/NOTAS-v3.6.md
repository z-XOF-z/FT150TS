## FT150TS Dashboard v3.6 (estable) — 6 tableros deportivos

### Tableros nuevos (Ajustes → Apariencia → Estilo del tablero, o mantén presionado el tablero)
- **Universal:** réplica del tablero digital genérico para Italika (125z/150z/FT). Tacómetro redondo de 0 a 12 con
  la zona alta amarillo→naranja→rojo, anillo azul con la marcha (N verde, número rojo), gasolina en arco (F/E),
  pantalla inclinada con la velocidad en 7 segmentos, testigos, columna de temperatura, barra de batería L–H y
  odómetro.
- **Superbike:** TFT de moto deportiva con la curva de RPM que sube, medidor de inclinación y la marcha en caja.
- **F1:** volante con 15 LEDs de cambio (verde · rojo · azul) que destellan en el punto de cambio.
- **Arrancón:** barra vertical de RPM, velocidad enorme, cronómetro en vivo y tus mejores 0-60 y 100 m.
- **Rally:** cinta de brújula (N, NE, E…), roadbook de km y altitud.
- **Arcade:** dígitos de 7 segmentos con brillo y RPM tipo ecualizador.

Todos usan los colores de tu tema (menos Universal, que lleva los del tablero original) y la animación del switch.
En total son 12 tableros.

### Arreglo: avisos de velocidad falsos bajo techo
Con el teléfono quieto dentro de casa, el GPS rebota y calculaba velocidades falsas. Con el aviso "según la calle"
prendido, te decía "Cuidado, vas a más de…". Ahora:
- Solo se usa la velocidad del GPS si es creíble: señal buena, posición con menos de ±25 m y error de velocidad
  menor a ±2.5 m/s.
- El aviso entra solo si pasas el límite **2 segundos seguidos**.
- Los tableros nuevos marcan 0 si la velocidad del GPS no es confiable.

71 pruebas automáticas pasan.
