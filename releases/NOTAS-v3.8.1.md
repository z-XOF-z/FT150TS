## FT150TS Dashboard v3.8.1

### Gestos de CarPlay: arreglados en modo noche y más rápidos
- **Modo noche:** en Widgets, el texto del clima (blanco sobre negro) se confundía con los puntitos de página y la app creía que no estabas en la orilla. Ahora los puntitos solo se buscan justo encima del dock.
- **Ya no hace falta deslizar dos veces:** los toques pasaban por la salida de `su` de Magisk, que los entregaba tarde y por bloques. Ahora el proceso root los escribe en una tubería dentro de la app y llegan al instante.
- **Seguro:** antes de cambiar de pestaña se confirma con una captura nueva que sigues en la orilla. Una lectura a media animación ya no te puede mandar a la pestaña equivocada.
- **Optimización:** se recuerda dónde está el botón del dock y se captura solo 1/4 de la franja. Reconocer la página bajó de ~130 ms a 1–10 ms.

Pruebas con capturas reales en modo claro, modo noche y los dos tamaños de CarPlay. Probado en el POCO F4 con gestos de dedo (Widgets → Tablero y última página de apps → Moto).
