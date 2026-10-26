## FT150TS Dashboard v3.4.2 (estable)

### CarPlay más estable en HyperOS 1 (Android 14)
- **Vigilante de la ventana:** mientras CarPlay está en la pestaña Auto, FT150TS revisa cada 3 s que la ventana siga
  en su lugar. Si DiPlay reinicia su pantalla al reconectar con el iPhone y HyperOS la regresa a su tamaño de fábrica,
  se recoloca sola.
- **Lectura de la ventana:** en HyperOS 1 la tarea aparece dos veces en el reporte del sistema y a veces no se leían
  las medidas. Corregido.

### Por qué CarPlay no conectaba (y cómo quedó)
El iPhone sí se conectaba, pero cortaba la sesión a los ~100 ms. DiPlay le pedía una pantalla de 3932 × 2400 (más
píxeles que 4K) porque en ventana flotante usa el tamaño más grande que ha visto, y en HyperOS 1 ve la ventana de la
pestaña sin la escala del sistema. Se arregla en DiPlay con su opción **Adaptar resolución en multiventana**
(activada). Así pide el tamaño real de su ventana (3932 × 1360) y el iPhone lo acepta.

### Probado en un POCO F4 con HyperOS 1 y un iPhone 13 Pro
- CarPlay conecta y llena la pestaña Auto.
- 6 cambios de pestaña seguidos sin reconectar; regreso a Auto en 160–215 ms.
- 3 minutos en reposo sin cortar la sesión.
- El vigilante recolocó la ventana cuando DiPlay la movió.
- Una llamada en el iPhone cortó la sesión: DiPlay se reconectó solo cuando el iPhone volvió al punto de acceso.

69 pruebas automáticas pasan.
