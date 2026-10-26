# Simulación de los bloques de la placa FT150TS

Seis esquemáticos de KiCad, uno por cada bloque delicado de la placa, ya listos para simular. Usan los modelos de `modelos.lib`.

| Archivo | Bloque |
|---|---|
| `01_entrada_bobina` | Pulso de 350 V de la bobina → 1N4007 + 47k → PC817 → GPIO16 |
| `02_entrada_aislada` | Rampa de 0 a 16 V → 1.5k → PC817 → GPIO |
| `03_entrada_sensor` | Sensor de 5 V → 2.2k + BAT85 → GPIO |
| `04_divisores` | 16 V de la moto → GPIO1, y 4.2 V de las celdas → GPIO4 |
| `05_rele` | GPIO → PC817 → BC547 → bobina del relevador, con diodo y LED |
| `06_carga_bd140` | GPIO43 → BC547 → BD140 → 5 V al TP4056 (1 A) |

## Cómo simular en KiCad

1. Abre el `.kicad_sch` con el Editor de esquemáticos de KiCad.
2. Menú **Inspeccionar → Simulador**.
3. Dale a **Ejecutar** (el triángulo verde). El tipo de análisis (`.tran` u `.op`) ya viene escrito en la hoja.
4. Para ver una gráfica:
   - **Voltaje:** con la herramienta **Sonda**, dale clic a un cable. También puedes agregar la señal por nombre, por ejemplo `V(/GPIO16)`.
   - **Corriente:** dale clic a la pata de una pieza.

## Resultados

Los sacó `correr_sim.py` con el ngspice de KiCad.

| Bloque | Resultado | ¿Bien? |
|---|---|---|
| Bobina: corriente pico en el LED del PC817 | 8.5 mA (máximo 50 mA) | ✅ |
| Bobina: GPIO16 con pulso / sin pulso | 0.04 V / 3.30 V | ✅ detecta cada chispa |
| Bobina: potencia promedio en la 47k a 6000 rpm | 12 mW (es de 1 W) | ✅ ni se entibia |
| Entrada aislada: corriente del LED a 3.3 V / 16 V | 1.4 mA / 9.8 mA | ✅ |
| Entrada aislada: potencia en la 1.5k a 16 V | 144 mW (es de 250 mW) | ✅ |
| Entrada aislada: voltaje desde el que detecta | ~1.4 V | ⚠️ muy sensible; ver nota |
| Sensor de 5 V: máximo en el GPIO | 3.52 V (límite 3.6 V) | ✅ |
| Divisor de la moto, 16 V → GPIO1 | 2.885 V | ✅ |
| Divisor de las celdas, 4.2 V → GPIO4 | 2.100 V | ✅ |
| Relevador: corriente en la bobina | 69 mA (SRD-05: ~71 mA) | ✅ |
| Relevador: colector prendido | 0.13 V | ✅ saturado |
| Relevador: pico al apagar | 5.75 V | ✅ el diodo lo frena |
| Carga: voltaje al TP4056 con 1 A | 4.75 V (necesita > 4.5 V) | ✅ |

**Nota sobre la sensibilidad:** las entradas aisladas detectan desde ~1.4 V. Para señales de 3.3 V está perfecto, pero ruido o fugas en el cable de la moto podrían dar lecturas falsas. Si pasa, se corrige poniendo una resistencia de 2.2k en paralelo con el LED de cada PC817: el umbral sube a ~1.9 V y con 3.3 V sigue detectando bien.
