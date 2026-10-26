# Placas extra FT150TS (v1)

Cada placa se pide en JLCPCB como un pedido aparte, en el mismo carrito: 2 capas, 1.6 mm, 1 oz, HASL.

| Placa | Zip para JLCPCB | Tamaño |
|---|---|---|
| **E: Expansión I2C** | `fabricar/E_JLCPCB/FT150TS_E_v1_gerbers_JLCPCB.zip` | 100 x 100 |
| **F: Fusibles** | `fabricar/F_JLCPCB/FT150TS_F_v1_gerbers_JLCPCB.zip` | 100 x 90 |
| **P: Perforada con rieles** | `fabricar/P_JLCPCB/FT150TS_P_v1_gerbers_JLCPCB.zip` | 100 x 100 |

DRC de las tres: 0 errores de cobre, 0 pistas sin conectar. Solo hay avisos de serigrafía (textos encimados).

## E: Expansión I2C
Se conecta al conector **I2C 3V3** de la placa base: 3V3, GND, SDA, SCL (INT es opcional).
Le das 12 V de la moto en la bornera **12V MOTO** (bobinas de los relevadores).

- **P0 = relevador 1 (BOMBA).** Bornera BAT+ / MOT+ / MOT- / BAT-. Pistas de 3 mm y diodo 1N5408 para el motor.
  BAT+ va a la batería **con fusible de 5 A** (puede ser una salida de la placa F).
- **P1, P2 = relevadores 2 y 3.** Bornera NA / COM / NC, pistas de 2 mm (hasta ~3 A).
- **P3 = salida aislada.** C / E, hasta 50 mA.
- **P4 a P7 = entradas aisladas.** De 3.3 a 16 V, más GND.
- **Lógica:** todo es activo en 0. Al arrancar todo queda en 1, con los relevadores apagados.
- **Dirección:** 0x20, sin puentes. **No pongas los 3 puentes**: 0x27 es la dirección del LCD.

| Pieza | Cant. |
|---|---|
| PCF8574P (DIP-16) + base de 16 pines | 1 |
| PC817 | 8 |
| BC547 | 3 |
| Relevador SRD-12VDC-SL-C (**12 V**) | 3 |
| 1N4007 | 4 |
| 1N5408 (3 A) | 1 |
| 1N4148 | 4 |
| LED rojo 3 mm | 3 |
| Capacitor electrolítico 100 uF 25 V | 1 |
| Capacitor cerámico 100 nF | 1 |
| Resistencia 330 | 4 |
| Resistencia 1k | 3 |
| Resistencia 1.5k | 4 |
| Resistencia 2.2k | 7 |
| Resistencia 10k | 9 |
| Tira de pines macho de 5, de 4, y 3 de 2 (+ jumpers) | 1 tira |
| Bornera 5 mm: 2 pines x1, 3 pines x2, 4 pines x1 | 4 |
| Bornera 3.5 mm: 2 pines x1, 5 pines x1 | 2 |

**Firmware:** falta agregar el soporte del PCF8574. Va en la siguiente versión del firmware, junto con los pines de la placa B.

## F: Fusibles
12 V de la batería entran por **BAT 12V** y salen por 6 salidas, cada una con su fusible de vidrio 5x20.
El LED de una salida prende si su fusible se fundió.

- Máximo **10 A en total.** Pon un fusible de línea de 10 A en el cable de la batería.

| Pieza | Cant. |
|---|---|
| Portafusible de clip 5x20 para circuito impreso (par de clips) | 6 pares |
| Fusibles 5x20 del valor que pida cada aparato (BOMBA 5 A) | 6 |
| LED rojo 3 mm | 6 |
| Resistencia 2.2k | 6 |
| Bornera 5 mm de 2 pines | 7 |

## P: Perforada con rieles
- **Agujeros:** 31 x 27 a 2.54 mm.
- **Rieles:** arriba +12V y GND; abajo +5V y GND.
- **Entrada:** bornera de 3 pines (12V, 5V, GND) a la izquierda. Solo lleva una bornera de 5 mm de 3 pines.

Generadores: `generar_extra.py E|F` y `generar_perforada.py`, con el Python de KiCad.
