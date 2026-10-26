# Validación del diseño (calculada, sin imprimir)


## Geometría

| | Prueba | Detalle |
|---|---|---|
| ✅ | bandeja: un solo sólido, válido y cerrado | 1 sólido(s), volumen 64.3 cm³ |
| ✅ | marco: un solo sólido, válido y cerrado | 1 sólido(s), volumen 7.7 cm³ |
| ✅ | tapa: un solo sólido, válido y cerrado | 1 sólido(s), volumen 40.1 cm³ |
| ✅ | socket_fijo: un solo sólido, válido y cerrado | 1 sólido(s), volumen 60.0 cm³ |
| ✅ | socket_movil: un solo sólido, válido y cerrado | 1 sólido(s), volumen 15.4 cm³ |
| ✅ | perilla: un solo sólido, válido y cerrado | 1 sólido(s), volumen 8.6 cm³ |

## Choques

| | Prueba | Detalle |
|---|---|---|
| ✅ | marco ↔ bandeja | 0.00 mm³ |
| ✅ | tapa ↔ bandeja+marco | 0.00 mm³ |
| ✅ | socket fijo ↔ socket móvil | 0.00 mm³ |
| ✅ | socket ↔ bandeja | 0.00 mm³ |
| ✅ | perilla ↔ socket | 0.00 mm³ |

## Teléfono

| | Prueba | Detalle |
|---|---|---|
| ✅ | el marco no toca la pantalla | 0.00 mm³; juego en Z 0.1 mm |
| ✅ | la tapa no toca el teléfono | aire sobre el marco 3.0 mm |
| ✅ | solo las costillas lo aprietan | 15.3 mm³ (máx. teórico 66.5) |
| ✅ | el marco retiene el vidrio en todo el contorno | pisa 1.6 mm |
| ✅ | ventana de cámara cubre la isla (con margen) | margen 1.3 / 1.4 mm |
| ✅ | ranura de botones cubre volumen + encendido | ranura -0..46 mm, botones 3..43 |
| ✅ | hueco USB-C cabe un cable con funda | 13.0 × 7.0 mm |

## Socket

| | Prueba | Detalle |
|---|---|---|
| ✅ | la bola de 25.4 mm entra | holgura 0.2 mm por lado |
| ✅ | aprieta una bola de 24.5 mm | recorrido 2.0 mm vs juego 1.3 mm |
| ✅ | aprieta una bola de 25.5 mm | recorrido 2.0 mm vs juego 0.3 mm |
| ✅ | palanca de la mordaza ≥ ×3 | ×4.1 |
| ✅ | pared entre bisagras y la bola/cuello ≥ 1.6 mm | 1.9 / 2.2 mm |
| ✅ | inclinación cubre la vista del piloto (~65°) | 60° ± 20° de la bola |

## Tornillos

| | Prueba | Detalle |
|---|---|---|
| ✅ | 4 tornillos de la tapa alineados con sus insertos |  |
| ✅ | 4 tornillos del marco alineados con sus insertos |  |
| ✅ | material bajo las cabezas M4 avellanadas ≥ 1.5 mm | 2.0 mm |

## Impresión

| | Prueba | Detalle |
|---|---|---|
| ✅ | bandeja cabe en la cama | [183.5, 81.1, 11.8] mm |
| ✅ | marco cabe en la cama | [183.5, 81.1, 8.5] mm |
| ✅ | tapa cabe en la cama | [187.6, 85.2, 13.0] mm |
| ✅ | socket_fijo cabe en la cama | [80.0, 66.1, 59.4] mm |
| ✅ | socket_movil cabe en la cama | [17.7, 47.1, 49.1] mm |
| ✅ | perilla cabe en la cama | [29.7, 29.7, 14.0] mm |
| ✅ | pared de la bandeja ≥ 1.2 mm (≥ 3 líneas) | 2.4 mm |
| ✅ | piso ≥ 2 mm | 4.0 mm |
| ✅ | marco ≥ 1.6 mm | 2.0 mm |
| ✅ | falda de la tapa ≥ 1.6 mm | 1.8 mm |

## Resistencia

| | Prueba | Detalle |
|---|---|---|
| ✅ | flecha de la bandeja a 5 g < 0.5 mm | 0.33 mm (voladizo 102 mm) |
| ✅ | esfuerzo en la bandeja a 5 g < 1/4 del PETG | 2.4 MPa |
| ✅ | esfuerzo en la cuña del socket a 5 g < 1/4 del PETG | 0.5 MPa |
| ✅ | la bola no gira con 5 g (par de fricción > par de carga ×3) | 31.2 N·m vs 1.52 N·m |

## En la moto

| | Prueba | Detalle |
|---|---|---|
| ✅ | bandeja+marco+tapa no choca con el tablero / manubrio | 0 puntos del escaneo dentro |
| ✅ | socket no choca con el tablero / manubrio | 151 puntos del escaneo dentro (bola + cables sueltos) |
| ✅ | pantalla hacia el piloto, 20–45° sobre la horizontal | 32° |
| ✅ | teléfono centrado en la moto (±10 mm) | -2 mm |

**45 de 45 pruebas pasan.**

Pendiente de validar con el prototipo impreso: ajuste real (anillos de calibración), posición real de cámara y botones, agarre de la bola en la moto, vibración rodando, temperatura al sol.