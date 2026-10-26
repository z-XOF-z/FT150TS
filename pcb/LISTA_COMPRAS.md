# Lista de compras: placa B FT150TS v2

Todas las piezas son de agujero (THT). Las resistencias son de 1/4 W, salvo la de 47k.
Todo se consigue en tiendas de electrónica en México (UNIT Electronics, AG Electrónica, Steren o la de tu ciudad).

## Resistencias 1/4 W (se montan paradas)

| Cant. | Valor | Dónde |
|---|---|---|
| 9 | 1k | R5, R7, R23, R712, R714, R722, R724, R732, R734 |
| 8 | 10k | R8, R24, R25, R26, R31, R713, R723, R733 |
| 4 | 1.5k | R110, R120, R130, R140 (entradas aisladas) |
| 5 | 470 | R28, R30, R711, R721, R731 |
| 3 | 100k | R1, R3, R4 |
| 6 | 2.2k | R20, R21 (entradas de sensor) y R115, R125, R135, R145 (bajan la sensibilidad de las entradas aisladas) |
| 1 | 330 | R27 |
| 1 | 22k | R2 |
| 1 | 4.7k | R22 |
| 1 | 150 | R6 |
| 1 | **47k a 1 W** | R9 (entrada de la bobina) |

## Capacitores

| Cant. | Valor | Dónde |
|---|---|---|
| 6 | 100nF cerámico | C2, C4, C6, C7, C9, C10 |
| 1 | 4.7nF cerámico | C8 |
| 1 | 220µF 35V electrolítico (Ø8 mm) | C1 |
| 1 | 220µF 10V o más, electrolítico (Ø8 mm) | C3 |
| 1 | 100µF 10V o más, electrolítico (Ø5 mm) | C5 |

## Diodos

| Cant. | Pieza | Dónde |
|---|---|---|
| 5 | 1N4148 | D6, D110, D120, D130, D140 |
| 5 | 1N4007 | D5, D8, D71, D72, D73 |
| 1 | Zener 1N4744 (15 V, 1 W) | DZ1 (protege la compuerta del corte de chispa) |
| 3 | 1N5819 (Schottky 1 A) | D4, D20, D21 |
| 3 | 1N5822 (Schottky 3 A) | D1, D3, D7 |
| 1 | P6KE22A (TVS) | D2 |
| 3 | LED rojo de 3 mm | LED1, LED2, LED3 |

## Semiconductores

| Cant. | Pieza | Dónde |
|---|---|---|
| 10 | PC817 (optoacoplador DIP-4) | U9, U11–U16, U71–U73 |
| 1 | MOSFET **IRF840** (500 V, TO-220; también sirve FQP8N60 de 600 V) | Q9, corte de chispa. **Solo si lo vas a usar** (junto con U15, R31, DZ1 y D8) |
| 1 | LM2596T-5.0 (TO-220 de 5 patas). Si no lo encuentras, sirve el **LM2576T-5.0** (mismas patas), pero entonces la bobina va de **100µH 3A** | U2 |
| 4 | BC547 (sirve BC548 o 2N3904 **con otro orden de patas**: BC547 es C-B-E) | Q6, Q71, Q72, Q73 |
| 2 | 2N7000 | Q7, Q8 |
| 1 | BD140 | Q5 |

## Otros

| Cant. | Pieza | Dónde |
|---|---|---|
| 1 | Bobina 33µH 3A radial (Ø10 mm, patas a 5 mm) | L1 |
| 1 | Fusible reseteable PTC de **2 A, 30 V o más**, radial (patas a 5 mm) | F1 |
| 3 | Relevador SRD-05VDC-SL-C o **JQC-3FF-S-Z 5VDC** (misma huella; tiene que decir **05VDC**) | K1, K2, K3 |
| 2 | Tira hembra 1×22 a 2.54 mm (o 2 de 1×40 cortadas) | Para el ESP32-S3 **N16R8 ancho** (27.94 mm de ancho, filas a 25.40 mm), el de AliExpress. El DevKitC-1 original (filas a 22.86 mm) **no entra** en esta versión |
| ~1 | Tira macho 1×40 a 2.54 mm (se corta) | J7, J9, J11–J17 |
| 5 | Bornera de 2 polos a 5 mm | J1, J4, J18 + 2 de repuesto |
| 3 | Bornera de 3 polos a 5 mm | J71, J72, J73 (relevadores) |
| 7 | Bornera de 2 polos a 3.5 mm | J2, J3, J5, J8, J10, J19 + 1 de repuesto |
| 1 | Bornera de 4 polos a 3.5 mm (o una de 2 + una de 2) | J6 (entradas) |
| 1 | Módulo TP4056 USB-C | Cargador de las celdas (va por cable a J3 y a las celdas) |

## Puntos de prueba (TP1 a TP4)

No hay que comprar nada: son pads marcados 12V, 5V, 3V3 y GND. La primera vez que conectes la placa, mide ahí con el multímetro antes de poner el ESP32.

## Recomendado para la instalación (de refaccionaria)

- **Portafusible de navaja en línea con fusible de 3 A**, en el cable de +12 V, lo más cerca posible de la batería. Protege el cable completo, no solo la placa.
