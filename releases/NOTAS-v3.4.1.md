## FT150TS Dashboard v3.4.1 (estable)

### CarPlay en la pestaña Auto, también en HyperOS 1 (Android 14)
Probado en un POCO F4 con HyperOS 1.0 (Android 14), después de cambiar de ROM:
- **Escala de la ventana:** HyperOS 1 reporta la escala de las ventanas flotantes en otro formato. FT150TS no la
  encontraba y ponía CarPlay chiquito en una esquina. Ahora lee los dos formatos (HyperOS 1 y HyperOS 3).
- **Medición con reintentos:** la escala se vuelve a medir hasta 3 veces y nunca se queda con un valor malo.
- **Comprobación de la ventana:** el comando que revisa dónde quedó la ventana no funcionaba con el `grep` de
  HyperOS 1. Corregido.
- **Ventana suelta:** si la app se reinicia o DiPlay abre otra pantalla dentro de su ventana, CarPlay ya no se queda
  flotando encima del tablero. FT150TS lo detecta y lo esconde.
- La app anota cada paso de CarPlay en el log del sistema (`adb logcat -s FT150-CP`) para diagnosticar.

Incluye todo lo de la v3.4. 69 pruebas automáticas pasan.
