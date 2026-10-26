## FT150TS Dashboard v3.7.0 (estable)

### Tres tableros nuevos de "instrumento" (la familia del LCD Sport)
Todo dibujado como un display de verdad: segmentos, puntos y fósforo, con los elementos apagados a la vista, prueba de
segmentos al arrancar y negro AMOLED. Ocupan el lugar del LCD (la columna de la derecha se queda igual) y rinden
igual que él (medido con datos de demo: ~34 cuadros/s, mediana 26 ms, igual que el LCD).
- **Race LCD**: superbike japonesa 2008-2015 (ZX-10R, R1, CBR1000RR). Tacómetro de escalera de lado a lado, memoria de
  RPM pico (3 s), 5 luces de cambio y modo **CARRERA**: toca el centro y la marcha se vuelve gigante.
- **VFD 85**: fluorescente de vacío de los 80 (Corvette C4, 300ZX). Fósforo del color de tu tema con malla, tacómetro en
  abanico, barra de velocidad 0-140 y una ventana de mensajes que rota TRIP / RANGO / BATT / LI-ION / ODO (toca para pasar).
- **Anillo**: bisel de 60 segmentos tipo reloj Casio / Lexus LFA, inclinación en el hueco de abajo y cuatro ventanas
  (hora, distancia, voltajes, temperatura y gasolina).
Se eligen en Ajustes > Estilo del tablero (primera fila) o manteniendo presionado el tablero.

### Arreglos
- **CarPlay aparecía encima del tablero al deslizar desde abajo:** HyperOS regresaba a la pantalla la ventana escondida
  porque se dibujaba unos pixeles más allá de la orilla de abajo. Ahora queda completa dentro del alto (mismo tamaño:
  CarPlay no se reconecta). Si aun así aparece y la tocas, FT150TS la vuelve a esconder al instante.
- **Mantener presionado el LCD no cambiaba de tablero** (el LCD se quedaba con el toque). Ya funciona.
- **El modo demo ya no cuenta en tu viaje:** antes sumaba sus km y máximas inventadas a las estadísticas reales.

72 pruebas pasan. Probado en un POCO F4 con HyperOS 1.
