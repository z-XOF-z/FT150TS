## FT150TS Dashboard v3.3-beta.3 (versión de prueba)

### CarPlay en la pestaña Auto, pulido
- **Más grande:** nuevo "Tamaño de CarPlay" en el detalle de CarPlay. **Máximo** (de fábrica) va de orilla a orilla
  debajo de tus pestañas; también hay **Amplio** y **Ajustado** (dentro del marco).
- **Más rápido:** al volver a Auto, CarPlay aparece en ~0.3 s (antes ~1 s). Sale en cuanto sueltas el deslizamiento
  y usa una sola sesión de root.
- **Se corrige solo:** después de mostrarlo, la app revisa dónde quedó la ventana. Si quedó corrida, en pantalla
  completa o DiPlay se reinició, la recoloca (hasta 3 intentos).
- **Sin cruces:** si cambias de pestaña muy rápido, siempre termina en el estado correcto.
- **Menos avisos de Magisk:** la app usa una sola sesión de root, así que Magisk avisa como mucho una vez. Para no
  verlo nunca: Magisk → Superusuario → FT150TS → notificaciones apagadas.

### Probado en un POCO F4 (HyperOS 3, iPhone conectado)
- Con la ventana puesta mal a propósito, se recolocó sola.
- 3 ciclos normales: regreso en ~330 ms.
- 4 ciclos rápidos (0.25 s): siempre en su lugar.
- 57 pruebas automáticas pasan.
