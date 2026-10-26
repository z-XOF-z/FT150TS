# Simulador del tablero FT150TS (Wokwi / Velxio)

Archivos del proyecto:
- `sketch.ino`: copia del firmware, con `MODO_SIMULADOR 1` (sin WiFi ni Bluetooth, arranca en demo).
- `diagram.json`: el circuito.
- `libraries.txt`: las librerías.

## Qué trae el circuito

| Pieza | Pin | Qué simula |
|---|---|---|
| LCD 20x4 I2C | SDA 8 / SCL 9 | El tablero |
| Botón verde / rojo / azul | 6 / 7 / 15 | ARRIBA / ABAJO / OK (OK largo = mantener 1 s) |
| Perilla 1 | GPIO1 | Voltaje de la batería de la moto |
| Perilla 2 | GPIO2 | Gasolina |
| Perilla 3 | GPIO4 | Celda 18650 |
| Sensor de luz | GPIO5 | Brillo automático |
| DS18B20 | GPIO14 | Temperatura del motor (se cambia dándole clic) |
| Interruptor LLAVE | GPIO21 | Llave ON / OFF |
| Interruptor NEUTRAL | GPIO18 | Neutral |
| Relevador | GPIO40 | Bloqueo del motor |
| Anillo de 8 LEDs | GPIO39 | Shift light |

## Opción 1: wokwi.com (gratis, en el navegador)

1. Entra a **wokwi.com**, luego **+ New Project** y elige **ESP32-S3**.
2. En la pestaña **diagram.json**, borra todo y pega el contenido de `diagram.json`.
3. En **sketch.ino**, borra todo y pega el contenido de `sketch.ino`.
4. Con el botón **+** crea el archivo `libraries.txt` y pega el contenido de este `libraries.txt`.
5. Dale **▶ Play**. La primera vez tarda en compilar.

## Opción 2: Velxio

En Velxio, crea un proyecto **ESP32-S3** y usa **Import → Wokwi** (o pega el `diagram.json` y el `sketch.ino` igual que en Wokwi).

## Cómo usarlo

- **Arranca en modo DEMO:** la LCD muestra velocidad, RPM, marcha, etc. inventadas, para que veas todas las pantallas.
- **Para usar las perillas e interruptores:** abre el **Serial Monitor** (115200) y escribe `real`. Para regresar al demo escribe `demo`.
- **Otros comandos de consola:** `estado`, `ayuda`, `pantalla N`, `sim caida`, `sim frenada`.
- **Si cambias el firmware:** vuelve a copiar `ft150ts_dashboard/ft150ts_dashboard.ino` sobre `sketch.ino`. Este es una copia.
