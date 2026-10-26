## FT150TS Dashboard v3.9.1

### Fluidez de CarPlay y modo rendimiento

## CarPlay (DiPlay) más fluido
Medido con perfetto en el POCO F4, moviendo el mapa de CarPlay (3 rondas cada uno):

| | v3.8.1 | v3.9.1 |
|---|---|---|
| Cuadros con tirón | 25.6 % | 1.8 % |
| Cuadros mostrados por segundo | ~33 | ~53 |
| CPU de DiPlay | 67 % | 43 % |

- **Sin congelamientos cada 3 s.** El vigilante de la ventana leía la escala con `dumpsys SurfaceFlinger`, y en Android 14 eso detiene al compositor unos 40 ms. Ahora solo la revisa si tocas cerca de una esquina de la ventana, o cada minuto si los gestos están apagados.
- **Ventana "encogida" (escala 0.25):** antes la app la aceptaba y armaba una ventana de 9600 px. Ahora la restablece sola.
- **Modo rendimiento (root, Ajustes > Pantalla):** ver abajo.
- **DiPlay, ajuste aparte:** la resolución quedó al 61 % (`display_scale_percent`). La ventana se agranda 1/0.61 para compensar la escala de HyperOS, y DiPlay pedía al iPhone video de 3932×1456. Ahora pide 2400×888, justo lo que se ve. Con esto la interfaz de CarPlay se ve más grande. Respaldo en el teléfono: `/data/local/tmp/xcertplay_airplay.respaldo.xml`.

## Modo rendimiento (nuevo, root)
- **Tablero en pantalla:** `sched_boost = 1`, así el kernel manda la app a los núcleos grandes. Al cambiar de página, el cuadro típico bajó de 12 a 7 ms y el peor 1 % de 31 a 13 ms.
- **Con CarPlay:** además la GPU sube su mínimo de 305 a 525 MHz. HyperOS compone las ventanas flotantes con la GPU.
- **Cuándo se apaga solo:** con la pantalla apagada, en otra app, en reposo o con el teléfono a 43 °C o más.
- **Si la app se cierra de golpe:** un guardián root regresa todo a como estaba.
- Se puede apagar en Ajustes > Pantalla > Modo rendimiento.

## Lo que se probó y NO sirvió
- **Bajar a 60 Hz con CarPlay:** la capa de video de DiPlay no pide Hz y HyperOS pone 120 aunque la app pida 60 o se cambie el ajuste del sistema.
- **Subir solo la GPU para la app sin CarPlay:** no mejoró nada.

## Gestos de CarPlay más fáciles
- **Deslizamientos cortos:** ya cuentan. El mínimo bajó de 1/5 a 1/8 del ancho de CarPlay; antes no contaban los de ~360 px.
- **La página se lee al poner el dedo:** solo en las páginas de inicio de CarPlay. Al llegar a Widgets y deslizar otra vez enseguida, ya cambia al Tablero con el primer intento; antes había que deslizar 2 o 3 veces.

## Carcasa (aparte de la app)
- Los STEP para AutoCAD salen del script: cada pieza en su posición, y el ensamble con nombres y colores (perilla incluida).
