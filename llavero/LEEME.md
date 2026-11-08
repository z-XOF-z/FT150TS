# TamaXOF: llavero del FT150TS

Llavero con carita tipo Tamagotchi que también es la **llave de la moto**:

- **Libera la moto solo.** Al girar la llave, la moto se libera si el llavero está cerca y se movió o se tocó en los últimos 90 s. No hay que meter contraseña.
- **Va cifrado.** Usa ESP-NOW con AES y firma HMAC, y cada intercambio lleva un reto nuevo, así que grabar la señal y repetirla no sirve.
- **Botón de paro.** Apaga el motor (corta la chispa) manteniendo OK 2 segundos. Sirve como el switch rojo que tu moto no trae en los mandos.
- **Otras órdenes:** bloquear, permitir el motor y buscar la moto.
- **Control de música por Bluetooth** con iPhone, Android y PC (anterior, pausa, siguiente y volumen).
- **Extras:** reloj (toma la hora de la moto), cronómetro, linterna, dado y el juego "Brinca".
- **La mascota** come los kilómetros que rueda la moto. Si la sacudes se marea.
- **PIN opcional** (viene apagado).

## Las dos placas (para no confundirse)

| Dónde | Placa | Firmware |
|---|---|---|
| **Llavero** | **ESP32-C3 SuperMini** (la chiquita) | `firmware/tamaxof/tamaxof.ino` |
| **Moto** | **ESP32-S3 DevKit N16R8** (la de la placa B) | `../firmware/ft150ts_dashboard/` + `moto/tamaxof_moto.h` (falta meterlo) |
| Prueba en la mesa | cualquier **ESP32-S3** | `moto/probador_moto/` (hace de moto falsa) |

ESP-NOW funciona entre la C3 y la S3 sin problema.

## Lista de compras (AliExpress)

Casi todo se consigue también en AG Electrónica o UNIT. La SuperMini es más fácil de encontrar en AliExpress.

| Cant. | Pieza | Búscala como | Ojo |
|---|---|---|---|
| 1 | ESP32-C3 SuperMini | "ESP32-C3 SuperMini" | La de antena en la placa (sin conector IPEX) |
| 1 | Pantalla OLED 0.96" 128x64 I2C | "OLED 0.96 I2C SSD1306 4 pin" | Que diga **GND VCC SCL SDA** en ese orden (hay unas con VCC primero). Blanca o azul, no la de dos colores |
| 1 | Cargador TP4056 USB-C **con protección** | "TP4056 Type-C protection" | La de 6 pads (IN, B+/B−, OUT+/OUT−), unos 28 × 17 mm |
| 3 | Botón táctil 6×6×4.3 mm, 4 patas | "tactile switch 6x6x4.3" | De 4.3 mm de alto; con los de 5 mm no cierra la caja |
| 1 | Sensor de vibración SW-18010P | "SW-18010P" | |
| 1 | Switch deslizable SS12D00 | "SS12D00G3" | |
| 1 | Diodo Schottky 1N5819 | "1N5819" | Evita que el USB de la C3 le meta 5 V a la pila |
| 2 | Resistencia 220 kΩ 1206 (SMD) | "1206 220K" | |
| 1 | Placa de cobre de una cara, 5 × 7 cm o más | | Más cloruro férrico y papel couché para el planchado |
| — | Cable de silicón 28–30 AWG y alambre forrado delgado | | El alambre es para el puente de 3V3 |
| — | Filamento blanco y naranja (PLA o PETG) | | |
| — | Cinta doble cara de espuma | | Para la pila |
| 1 | Pila del control de PS4, 570 mAh (ya la tienes) | 57.7 × 35.7 × 5 mm | Revisa que no esté inflada y que dé más de 3.0 V |

## Placa casera de una cara (`pcb/`)

`python -I pcb/generar_placa.py` genera el planchado y revisa solo que no haya cortos y que cada señal tenga 0.45 mm de separación.

- **`cobre_1200dpi.pdf`:** imprímelo **al 100 %, sin espejear**, en impresora láser.
- **`barrenos.png`:** los barrenos son de 0.8, 0.9 y 1.0 mm.
- **`armado.png`:** dónde va cada pieza.

**Medidas que tienes que confirmar al llegar las piezas.** Si alguna no coincide, me dices y regenero la placa.

- **SuperMini:** largo 22.5 mm, separación entre filas 15.24 mm, primer pin a 3.0 mm del lado del USB.
- **OLED:** orden de pines y dónde queda la parte que se ilumina (para la ventana de la caja).
- **SW-18010P:** el largo total.

Piezas:

- **Al frente** (lado de la pantalla): la OLED (con su separador negro puesto) y los 3 botones.
- **Atrás** (lado del cobre): la SuperMini, acostada y con sus componentes hacia atrás. El estaño se mete por sus agujeros y agarra los pads de abajo. También van el sensor de vibración y las 2 resistencias.
- **Puente de 3V3:** un alambre forrado por el frente, del agujero junto al pin 3V3 al agujero junto al VCC de la pantalla.
- **Pines de la C3:**
  - pantalla: GPIO10 (SCL) y GPIO9 (SDA)
  - botones: GPIO1 (IZQ), GPIO2 (OK) y GPIO3 (DER)
  - sensor de vibración: GPIO5
  - lectura de la pila: GPIO0

## Conexiones de la pila

```
pila +  --->  TP4056 B+  ---> (cable) pad BAT+ de la placa
pila −  --->  TP4056 B−
TP4056 OUT+ ---> switch (pata del centro) ---> switch (pata de un lado) ---> 1N5819 (lado sin franja)
1N5819 (lado de la franja) ---> pad 5V de la placa
TP4056 OUT− ---> pad GND grande de la placa
```

- Se **carga** por el USB-C del TP4056 (el de abajo).
- Se **programa** por el USB-C de la SuperMini (el de arriba). Con el diodo es seguro aunque el switch esté prendido.
- **Opcional:** quitar el LED rojo de encendido de la SuperMini. Prendido todo el tiempo gasta más que el llavero dormido.

## Carcasa (`carcasa/`)

`..\carcasa\.venv\Scripts\python.exe -I carcasa/carcasa_tamaxof.py` genera las piezas y revisa que todo quepa sin chocar. `carcasa/vista.py` dibuja `vista.png`.

- **Medidas:** 39.9 × 61.9 × 21.3 mm, más el aro. Con una LiPo 602030 (`BATERIA` en el script) bajaría a unos 37 × 47 mm.
- **Qué imprimir:**
  - blanco: `tamaxof_frente` (con la cara al frente sobre la cama) y `tamaxof_tapa`;
  - naranja: `tamaxof_botones` (con la cabeza abajo), `tamaxof_orejas` y `tamaxof_bolita`.
- **Antes de pegar nada:** prueba que la ventana coincida con tu pantalla y que los botones hagan clic.
- **Montaje:** las orejas se pegan en sus agujeros. La pila va atrás con cinta de espuma. El switch se pega por dentro de la pared izquierda.

## Firmware

1. Corre `python -I generar_secreto.py`. Crea la clave secreta (`comun/secreto_tamaxof.h`, que no se sube a GitHub) y copia el protocolo a cada firmware.
2. **Llavero:** en Arduino IDE (core esp32 **3.2.1**) escoge **ESP32C3 Dev Module**, *USB CDC On Boot: Enabled* y *Partition Scheme: Minimal SPIFFS*.
3. **Probador:** cualquier ESP32-S3 con *USB CDC On Boot: Enabled*. Desde el monitor serie a 115200: `i` gira la llave, `b` bloquea, `c N` cambia de canal, `e` muestra el estado.
4. **Moto:** falta meter `moto/tamaxof_moto.h` al firmware del tablero. Se hace cuando conectes la ESP32-S3 de la moto y se revise junto con lo del bootloader XMC.

**Botones:** IZQ/DER cambian de pantalla o de opción, OK entra o elige, y mantener OK regresa.

**Atajos de 10 segundos** (sale una barra a los 1.5 s; si sueltas antes, no pasa nada):
- **Mantener DER 10 s: prestar moto.** Apaga la llave de proximidad de la moto, que arranca con la pura llave (para prestársela a alguien sin teléfono ni llavero). Otra vez: la vuelve a prender.
- **Mantener OK 10 s: bloquear moto** (el bloqueo antirrobo, solo con la moto parada). Otra vez: la desbloquea.
- **Mantener IZQ 10 s:** libre, para otra función.

**Las pantallas son:**
- Cara: OK la acaricia.
- Reloj.
- **Tablero:** velocidad grande, marcha, RPM con barra, temperatura, gasolina y batería de la moto. Solo cerca de la moto: con la pantalla prendida, el llavero le pide los datos y la moto se los manda 4 veces por segundo.
- **Avisos:** historial de las últimas 12 alertas de la moto (!) y avisos del teléfono (T) que le llegan a la moto. Una falla nueva de la moto sale en grande con un triángulo de advertencia; un aviso del teléfono sale en un recuadro. OK abre la lista; la última opción borra todo. Se guarda mientras el llavero duerme y se borra al apagarlo con el switch.
- Moto: con OK abres el menú: Prestar moto (SI/NO), Bloquear/Desbloquear, Estoy aquí, Apagar motor (paro), Permitir motor, Buscar moto y Actualizar.
- Música: OK para controlar; mantener IZQ o DER cambia el volumen.
- Mascota: comida, ánimo y energía.
- Extras.
- Ajustes: brillo, tiempo de pantalla, PIN, "Llave sola", emparejar Bluetooth e info.

## Seguridad

- **La moto nunca se corta sola rodando.** La llave de proximidad solo bloquea al girar la llave con la moto parada. Lo único que apaga el motor en marcha es la orden explícita "Apagar motor" (mantener OK 2 s).
- **Si alguien pone un repetidor junto a tu bolsa,** el llavero quieto no le contesta, y la moto rechaza respuestas que tardan más de 60 ms. No es infalible, pero junto con el candado del manubrio está bien.
- **La clave va en `secreto_tamaxof.h`,** que no se sube a GitHub. Si cambias la clave, hay que grabar el llavero y la moto otra vez.

## Falta (nada probado en físico todavía)

- Comprar las piezas y medirlas, y ajustar `generar_placa.py` y `carcasa_tamaxof.py` si algo no coincide.
- Probar el llavero con el probador en la mesa: que libere, el tiempo de respuesta, el alcance a 30 m y el Bluetooth con el iPhone.
- Meter el llavero al firmware de la moto.
