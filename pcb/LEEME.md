# Tarjeta base FT150TS v2 (ESP32-S3-DevKitC-1)

Dos placas con **el mismo circuito**, de 100 × 100 mm. La B va en la versión v2.3, para el ESP32-S3 **ancho** (filas a 25.40 mm):

| Placa | Para qué | Archivo |
|---|---|---|
| **A** | Hacerla en casa con plancha: cobre solo abajo, con unos puentes de alambre arriba | `ft150ts_A.kicad_pcb` |
| **B** | Mandarla a JLCPCB: dos caras, sin puentes | `ft150ts_B.kicad_pcb` |

Todas las piezas son de agujero (THT), así que las sueldas tú.

El circuito está en `diseno.py`. `generar_pcb.py` arma las placas y `revisar_circuito.py` / `revisar_acomodo.py` las revisan.

## Qué trae

- **Alimentación de la moto (10.5 a 16 V):**
  - fusible reseteable PTC de 2 A;
  - diodo contra polaridad invertida;
  - TVS P6KE22A contra picos;
  - regulador **LM2596T-5.0** (3 A, hasta 40 V) con su bobina de 33 µH, diodo 1N5822 y capacitores de 220 µF.

  Un 7805 no sirve aquí: con 12 V y más de 1 A (relevadores + LCD + cargador) se calienta demasiado.
- **Respaldo (BATERIA 3.7V):** de 1 a 3 celdas 18650 en paralelo, con su BMS.
  - Van **directo** al pin 5V del DevKit, por un diodo 1N5819. No hace falta elevador: el regulador del DevKit las baja a 3.3 V y el ESP32 trabaja hasta unos 3.0 V.
  - Con la moto prendida manda la moto (5 V); apagada, toman el relevo las celdas, sin cortes.
  - El voltaje de las celdas se mide en GPIO4: 4.2 V se leen como 2.1 V.
  - Con la moto apagada los relevadores no se activan, porque se alimentan solo de la moto.
- **Carga de las celdas (CARGADOR 5V):** el BMS de las celdas solo las protege; **no es cargador**, y si les metes 5 V directos se sobrecargan.
  - Usa un **módulo TP4056**, que se consigue en cualquier tienda de electrónica: **IN+/IN−** a la bornera CARGADOR 5V y **B+/B−** a las celdas.
  - El ESP32 (GPIO43) prende un BD140 que le da los 5 V de la moto. Así decide cuándo cargar (por ejemplo, celdas abajo de 3.9 V y moto prendida), y el TP4056 corta solo al llenarlas.
- **Medición de la batería de la moto:** GPIO1, divisor 100k/22k. 16 V llegan como 2.9 V.
- **3 relevadores de 5 V:**
  - cada uno lleva su optoacoplador, transistor, diodo de protección y LED rojo, que prende cuando el relevador está activado;
  - cada uno tiene una bornera NA/COM/NC;
  - el relevador 1 es el bloqueo anti-robo.
- **9 optoacopladores PC817 + 1 PhotoMOS:**
  - **BOBINA:** el cable de la bobina va directo. Un 1N4007 y una resistencia de 47k a 1 W bajan los cientos de volts a unos mA.
  - **LLAVE** y **ENTRADAS 1, 2 y 3:** leen solas 3.3, 5, 12 o 16 V, sin mover nada.
  - **3 relevadores** y **SALIDA AISLADA** libre (colector abierto, hasta 50 mA).
  - **CDI PARO:** PhotoMOS para el corte de chispa (ver `../firmware/HARDWARE-CORTE-CHISPA.md`).
- **Sensores de 3.3 o 5 V** (HALL, DS18B20, QUICKSHIFT):
  - cada tira trae **5V, 3V3, GND y señal**;
  - conectas el sensor al voltaje que use;
  - la señal pasa por una resistencia de 2.2k y un diodo 1N5819 al 3.3 V, así que el ESP32 la lee bien sea de 3.3 o de 5 V.
- **I2C:**
  - a 3.3 V para el RTC y el MPU6050;
  - a 5 V para el LCD, con traductor de nivel 2N7000.
  - **Para crecer:** en el I2C de 3.3 V puedes conectar un MCP23017 (16 entradas/salidas más) o un ADS1115 (4 entradas analógicas). Ya no quedan pines libres en el ESP32.

## Pines del ESP32 (cambios respecto al firmware v1.5)

| GPIO | Uso nuevo |
|---|---|
| 38, 45 | Relevadores 2 y 3 (alto = activado) |
| 43 | Carga de las celdas: alto = 5 V al TP4056 |
| 3, 46 | Entradas aisladas 2 y 3 (activa = LOW, `INPUT_PULLUP`) |
| 44 | Salida aislada libre |
| 18 | **Neutral, ahora por optoacoplador:** en neutral el cable está a tierra y el pin lee HIGH, así que va `NEUTRAL_ACTIVO_BAJO = false` |

Todo lo demás queda igual que en el firmware: 40 = relevador 1 / bloqueo, 47 = corte de chispa, 48 = quickshifter, 16 = RPM, 21 = llave, etc.

En el DevKitC-1 v1.1 el LED RGB está en GPIO38, así que va a cambiar de color cuando actives el relevador 2. No pasa nada.

## Cómo pedir la placa B en JLCPCB

1. Entra a **jlcpcb.com**, ve a **Order now** y luego a **Add gerber file**. Sube `fabricar/B_JLCPCB/FT150TS_B_v2.3_gerbers_JLCPCB.zip`. La página reconoce sola el tamaño (100 × 100 mm) y las 2 capas.
2. Deja todo como viene, que es lo más barato:

   | Opción | Valor |
   |---|---|
   | Base Material | FR-4 |
   | Layers | 2 |
   | PCB Qty | 5 (el mínimo, mismo precio) |
   | PCB Thickness | 1.6 |
   | PCB Color | Green (el verde sale más rápido y más barato) |
   | Surface Finish | HASL (with lead) |
   | Outer Copper Weight | 1 oz |

3. **PCB Assembly: apagado.** Las piezas las soldas tú.
4. En el envío, elige el más barato a México. Revisa el total antes de pagar.
5. Antes de soldar:
   - Revisa con el multímetro que **no haya continuidad** entre 12V–GND, 5V–GND y 3V3–GND.
   - Suelda primero la fuente (fusible, diodos, LM2596, bobina, capacitores) y **mide 5 V sin el ESP32 puesto**.
   - Después pon el DevKit **con el USB hacia el lado que dice "< USB"**.

## Placa C (módulos, una cara para plancha)

- **LM2596 y TP4056:** van sobre la placa con pads ovalados.
  - Cada marca cambia un poco las medidas. **Imprime a escala 1:1, pon el módulo encima y revisa** que cada agujerito caiga en su óvalo. Si no cae, pasa las medidas para ajustar la huella.
  - **Cómo soldarlos:** el módulo arriba, con un pedacito de pata de resistencia en cada agujero; atraviesa la placa y se suelda abajo, en el cobre.
- **Relevadores (4 canales), optoacopladores (4 canales) y convertidor de nivel:** van con cable dupont a su tira rotulada.
- **Firmware para la placa C:** los relevadores del módulo típico se activan con LOW.
