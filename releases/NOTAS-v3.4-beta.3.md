## FT150TS Dashboard v3.4-beta.3 (versión de prueba)

### Animación solo al abrir el switch
- La animación del tablero ya **no sale al cambiar de pestaña**. Sale una vez cada vez que abres el switch de la moto
  (el cable empieza a cargar el teléfono). Si en ese momento estás en otra pestaña o con la pantalla apagada, sale en
  cuanto veas el tablero.
- **5 animaciones nuevas para elegir** (Ajustes → Apariencia → Animación al abrir el switch):
  - **Barrido 45°:** la de siempre.
  - **CRT:** una línea brillante al centro que se abre como tele vieja.
  - **Escáner:** un haz revela el tablero de izquierda a derecha.
  - **Pulso:** anillos del color de acento salen del centro.
  - **Píxeles:** el tablero se prende en bloques, como un LCD despertando.
  - **Al azar:** una distinta cada vez.
- Botón **Probar animación** para verla sin la moto.
- En todas, las RPM suben al tope y regresan y los dígitos marcan 888.

### Más rápida y gasta menos (medido en el POCO F4)
- **CPU de la app:** −32 % con la app quieta (de 922 a 625 ms de CPU cada 4 s).
- **Acelerómetro por lotes:** el sensor de las RPM por vibración entrega sus muestras cada 100 ms en lugar de despertar
  a la app cientos de veces por segundo. Lee lo mismo (416 muestras por segundo).
- **Auto con CarPlay encima:** la página de abajo ya no se dibuja (de 74 a 14 cuadros por segundo) y la brújula descansa.
- **Fondo guardado en la GPU:** solo se vuelve a pintar si cambia (37 % menos trabajo de la GPU).
- **Barra de arriba:** solo se actualiza cuando cambia lo que se ve (barritas del GPS, satélites, batería).
- Las animaciones del switch arman sus figuras una sola vez; mientras no corren, no cuestan nada.

64 pruebas automáticas pasan.
