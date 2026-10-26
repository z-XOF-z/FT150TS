## FT150TS Dashboard v3.6.1 (estable) — optimización medida

Todo medido en un POCO F4 con HyperOS 1 (Android 14).

### Gasta mucho menos batería con la app abierta y sin rodar
- **CPU con el tablero a la vista: de 37.8 % a 12.5 %.**
- Causa 1: el sensor de RPM por vibración (~400 lecturas por segundo) estaba prendido siempre que la app se veía, y
  como FT150TS es el launcher, eso era todo el día. Ahora solo trabaja en la moto (o si apagas "Motor solo con el
  cable").
- Causa 2: en esta ROM, cada lectura de sensor hacía que Android armara un objeto de pantalla nuevo, porque los
  sensores se pedían con el contexto de la aplicación. Ahora se piden con un contexto de pantalla: el hilo del
  sensor pasó de 40 % a 7 % de CPU. Esto aplica a todos los sensores: RPM, brújula, reposo, sueño, luz y arrancones.

### Código compilado de verdad
- La app trae un **Baseline Profile**: le dice a Android qué compilar por adelantado. HyperOS solo compila lo que
  está en ese perfil, y antes casi todo corría en el intérprete.
- Con root, después de cada actualización la app pide a Android que la compile en ese momento (una vez por versión).
  Antes había que esperar a que el teléfono estuviera inactivo cargando.

### Tableros más ligeros
- **LCD Sport:** la barra de RPM se dibuja en su propia capa. El resto del LCD y la columna de datos quedan en caché de
  GPU y solo se repintan cuando cambian. Tiempo por cuadro: armado de 7.9 a 3.6 ms, dibujo de 15 a 5 ms.
- El texto pixelado se dibuja en una sola llamada por texto (antes, una por cada pixel). Ayuda a todos los tableros.
- En los tableros nuevos, la velocidad y las RPM ya no reconstruyen todo el tablero en cada paso: solo se
  actualiza el número o la barra que cambia.
- La velocidad animada va a 30 pasos por segundo en vez de 120.

71 pruebas automáticas pasan.
