## FT150TS Dashboard v3.10.0

### Racing Morado: tu concepto "145 · 3 GEAR", recreado fiel
- **Colores exactos de tu especificación:** #080510, #C42CFF, #8D37FF, #FF45E8, #F7F2FF y #A69AB8.
- **Pantalla:** tacómetro de barrido 0–12 con marcas finas y punta brillante, velocidad en itálica, marcha gigante con GEAR y flechas de cambio, ecualizador con las RPM recientes, 4 paneles, franja de estado y botones laterales.
- **Botones que funcionan:**
  - **Cruceta:** ◀◀/▶▶ cambian de canción, ▲/▼ suben y bajan el volumen, y el centro pausa.
  - **Mapa:** lleva a la pestaña Auto.
  - **Herramientas, cronómetro y gráfica:** llevan a Moto.
  - **Engrane:** abre Ajustes.
  - **Calculadora:** abre tus apps.
- **Brillo calculado una sola vez:** el brillo con desenfoque real se pinta una vez por tamaño, y en cada cuadro solo se dibujan los datos.
- **Paneles LAP DELTA y BEST LAP:** muestran "--" hasta que exista el cronómetro de vueltas.

### Telemetría Morada: tu concepto "168 · 5"
- **Barra de RPM horizontal:** sube en rampa y luego sigue recta, con segmentos densos, la parte apagada en lavanda, el bloque rojo desde 10 000 y el triángulo en el punto de cambio.
- **Centro:** tacómetro circular con la marcha adentro y la velocidad gigante sin tarjeta.
- **Derecha:** radar de fuerza G en rombo y el medidor vertical.
- **Abajo:** la fila tiene el escalón a 45° (los lados abajo, el centro arriba) y la franja de iconos debajo del centro.
- **Solo datos reales, con el mismo diseño:** la moto no tiene sensores de aceite, llantas ni coeficiente de arrastre, así que cada panel muestra el dato real más cercano:

| En el concepto | En la app |
|---|---|
| OIL T. / TANK TEMP | MOTOR T. (moto), CEL T. (batería del teléfono), ESP T. (chip del ESP32) |
| Radar G | aceleración y frenado (ESP32) y lateral calculada con la inclinación; máximos del viaje |
| TIRE SLIP | inclinación (0–60°) |
| TIRE TEMPERATURE MAP | TRIP, ODO, velocidad máxima y km/L, con el esquema de la moto |
| DRAG COEFFICIENT | voltaje de la moto |
| SECTOR 3 + pista | tu ruta de hoy dibujada como circuito, la temperatura del aire y la sensación térmica |

### Dual Neón: tu concepto cian y rojo
- **Centro:** velocímetro hexagonal cian con la marcha en su pestaña.
- **RPM:** arco a la izquierda, de verde a amarillo, naranja y rojo, con toques cian.
- **Gasolina:** arco rojo de F a E.
- **Abajo:** cuatro paneles rojos y la línea roja del borde.
- **Botones:** casa (abre tus apps), MODO RACE/ECO, PRCXE y LAUNCH (página Moto). Las dos crucetas controlan la música y el volumen.
- **Datos reales:** la presión de aceite y el TPS no existen en la FT150TS, así que esos dos paneles muestran TRIP e INCLINACIÓN.

### Tema "Racing Morado" en toda la app
- **Colores del tablero morado en toda la app:** el fondo #080510, los textos #F7F2FF / #A69AB8 y el acento #C42CFF. Se elige en Ajustes > Apariencia > Tema.
- **La barra de arriba ahora es la del concepto:** "FT150TS" en itálica con el TS morado brillante, la hora, MX, la señal y la batería verde con %.
- **El tablero Racing Morado ya no repite esa barra:** usa ese espacio para crecer.
- **En el Tablero la barra se baja** y el tablero usa toda la pantalla de arriba. Al ir a Auto (CarPlay) o a cualquier otra pestaña, la barra sube sola con una animación. Las páginas solo se recorren, sin cambiar de tamaño, y CarPlay se acomoda como siempre.
- **Sin orilla junto a la cámara:** el tablero ya no pinta su propio rectángulo de fondo.

### Pantallas de inicio
- **Potencia** (la de "POTENCIA EN CADA KILÓMETRO"), morada con el tema Racing. Siguen disponibles Ride mode, Sistema, la clásica y "Al azar" (Ajustes > Apariencia > Pantalla de inicio).

### Funciones avanzadas (todas apagadas de fábrica)
Están en **Ajustes > Funciones avanzadas**. Necesitan el firmware **v1.5** de la tarjeta.
- **Limitador de RPM, launch control y quickshifter:**
  - Cortan la chispa unos milisegundos con el circuito de corte (PC817 + MOSFET IRF840) en el cable de paro del CDI (ver `firmware/HARDWARE-CORTE-CHISPA.md`).
  - Sin ese hardware, la tarjeta no deja prenderlos.
  - Para prenderlos: la moto parada, el teléfono vinculado y una confirmación.
  - Si la tarjeta falla, la moto sigue con chispa normal.
- **Diagnóstico de motor y batería:** solo mide, no necesita hardware extra.
  - Chispas que faltan y encendido disparejo.
  - El voltaje al dar marcha en los últimos 5 arranques, con aviso abajo de 9.6 V.
  - El alternador y el ralentí.
- **Telemetría en la dashcam:** cada video lleva un subtítulo `.srt` con velocidad, RPM, marcha, voltaje, inclinación, hora y ubicación.
  - VLC lo muestra solo.
  - Al guardar un clip, el subtítulo se copia junto al video.
