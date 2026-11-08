## FT150TS Dashboard v3.12.0

Versión estable. Junta todo lo de las betas 3.11.0 y agrega el llavero TamaXOF. Para la tarjeta, usa el firmware v1.8, que se compila desde `firmware/ft150ts_dashboard` (ver abajo).

### Nuevo en 3.12.0
- **Llavero TamaXOF:** en Moto > Placa B hay un switch "Llavero TamaXOF" y su estado: apagado, cerca, no se ha presentado o PARO puesto.
- **Prestar moto:** es el switch "Llave de proximidad". Apagado, la moto arranca con la pura llave. También se cambia desde el llavero manteniendo DER 10 s.

### De las betas 3.11.0
- **Actualizar la tarjeta desde la app** por Bluetooth (OTA), telemetría 10 veces por segundo y bajar los registros de la SD.
- **Casa y Telegram:** WiFi de casa y tu propio bot. La moto te avisa y obedece /estado, /bloquear, /desbloquear, /ubicacion, /salud y /errores.
- **Placa B:** freno y direccional olvidada, relevadores 2 y 3, salida aislada, alarma de movimiento con el MPU6050, IA de vibraciones y brillo con el sensor de luz del teléfono.
- **Placa E (opcional):** BOMBA del inflador con protecciones, relevadores y entradas extra.
- **Guardián de la tarjeta:** vigilante, modo seguro tras 3 fallas seguidas y bitácora de errores.
- **Antirrobo del teléfono, estado de la moto, IA de baches y gráficas de voltaje.**
- **CarPlay:** ya no deja la pantalla prendida fuera de la moto y se reabre solo si prendes la zona WiFi después.
- **Menos batería** con la app como pantalla de inicio (GPS y Bluetooth en ahorro fuera de la moto).

### Firmware de la tarjeta v1.8 (en el código)
- Escucha al llavero TamaXOF por ESP-NOW cifrado (AES + HMAC, reto nuevo en cada orden): libera la llave de proximidad, paro de motor, bloquear, permitir, buscar y prestar moto. Le manda al llavero RPM, velocidad, marcha, temperatura, gasolina y alertas.
- La moto nunca se corta sola rodando. El paro solo ocurre si lo pides desde el llavero, y se quita al quitar la llave.
- **Antes de compilar** corre `python -I llavero/generar_secreto.py`. Crea tu propia clave en `secreto_tamaxof.h`, que no se sube a GitHub, y la copia a la tarjeta y al llavero.
- Si tu ESP32-S3 trae flash XMC y no arranca, lee `docs/` y no uses "Subir" ni imágenes completas.

### Llavero TamaXOF (nuevo, en `llavero/`)
- Firmware para ESP32-C3 SuperMini con OLED 0.96": carita, tablero de la moto, avisos con historial, música por Bluetooth y extras.
- Placa casera de una cara y carcasa 3D. Nada probado en físico todavía.
