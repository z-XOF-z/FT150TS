## FT150TS Dashboard v3.4-beta.2 (versión de prueba)

### CarPlay en la pestaña Auto, mejor acomodado
- **Sin la píldora de abajo:** HyperOS pone una franja con una píldora debajo de cada ventana flotante (sirve para
  moverla o cerrarla). Ahora la ventana de CarPlay se alarga un poco por debajo de la pantalla y esa franja queda
  fuera: ya no se puede tocar sin querer al manejar, y el dock de CarPlay llega hasta abajo.
- **Más pegado a las pestañas:** en tamaño **Máximo** CarPlay sube 8 dp, hasta la línea de las pestañas (sin taparlas).
- **Amplio** también esconde la píldora. **Ajustado** queda igual (dentro del marco).

### Lo que no se puede quitar
- Los tres puntos (•••) arriba al centro son de HyperOS y no hay forma pública de quitarlos.
- La esquina redondeada de la izquierda se queda: Android no deja empezar una ventana antes del borde izquierdo.

### Probado en un POCO F4 (HyperOS 3, iPhone conectado)
- CarPlay en Auto sin la píldora; al ir a Tablero se esconde y al volver regresa en su lugar.
- 62 pruebas automáticas pasan.

Incluye todo lo de v3.4-beta.1 (12 estilos nuevos + Mezcla).
