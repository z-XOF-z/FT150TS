# Auditoría de seguridad — FT150TS v3.0 (app) y firmware v1.4

Fecha: 2026-10-11. Alcance: código de la app Android (Kotlin/Compose), firmware del ESP32 (`firmware/`),
configuración de compilación, permisos y comunicaciones. Revisión de código + pruebas automatizadas; **sin**
pruebas en el teléfono ni en la tarjeta (no había hardware conectado).

> Una auditoría reduce riesgos, no demuestra que no existan vulnerabilidades. Lo de abajo distingue lo
> **confirmado**, lo **corregido y verificado**, y lo **pendiente**.

## Resumen

| # | Gravedad | Hallazgo | Estado |
|---|---|---|---|
| 1 | Crítica (latente) | WiFi de la tarjeta con clave fija `12345678` (publicada en el repo) y página `/cmd` que ejecutaba cualquier comando, incluido el corte de motor | Corregido (firmware 1.4) |
| 2 | Alta | La app se conectaba sola a cualquier aparato BLE con el servicio "Nordic UART" y le mandaba notificaciones, GPS y hora | Corregido (app 3.0) |
| 3 | Alta (latente) | Firmware: cualquiera podía conectarse por BLE sin vincular y mandar `corte on confirmar`, calibrar o borrar datos | Mitigado (firmware 1.4); queda riesgo residual |
| 4 | Media | Restaurar un respaldo aceptaba cualquier ajuste (incluidos los de root), sin tope de tamaño y podía dejar los ajustes a medias | Corregido y probado |
| 5 | Media | El APK publicado está firmado con la llave de desarrollo (debug) | Pendiente (requiere decisión) |
| 6 | Media | El firmware no compila en modo placa real con el core ESP32 3.3.11 (el S3 trae NimBLE; el firmware usa Bluedroid) | Pendiente (no es de seguridad pero bloquea instalar los arreglos 1 y 3) |
| 7 | Baja | Llamada a "prender Bluetooth" sin revisar el permiso (lint `MissingPermission`) | Corregido |
| 8 | Baja | `allowBackup=true`: el respaldo en la nube de Android incluye rodadas (ubicaciones) | Aceptado, documentado |
| 9 | Baja | Página WiFi: `/notif` y comandos no delicados sin más control que la clave del WiFi | Aceptado, documentado |
| 10 | Baja | `BACKUP_URL` del firmware permite `http://` (sin cifrar) | Documentado (la función está sin configurar) |

## Detalle

### 1. WiFi de la tarjeta (crítica, latente)
- **Evidencia:** `AP_PASS = "12345678"` igual para todas las tarjetas y visible en GitHub; `P.wifiOn = true` de
  fábrica; `server.on("/cmd")` llamaba `ejecutarComando()` sin ningún control, por GET (también expuesto a CSRF desde
  cualquier página web abierta en un teléfono conectado a esa red).
- **Impacto:** cualquiera cerca podía entrar a la red y activar/desactivar el corte de motor, recalibrar, cambiar el
  odómetro o reiniciar la tarjeta.
- **Corrección:** cada tarjeta genera su clave al azar (10 caracteres, `esp_random()` con la radio encendida) y la
  guarda en NVS; se respeta una clave propia en `AP_PASS`. `/cmd` rechaza con 403 todo comando delicado. La clave se
  ve por USB al encender o con `wifi clave` (solo USB o BLE vinculado).
- **Verificación:** compila (ver "Pruebas"). No probado en la tarjeta.

### 2. Conexión BLE a cualquier aparato (alta)
- **Evidencia:** `BleManager` usaba los UUID del Nordic UART Service (`6e400001-…`), estándar en miles de aparatos, y se
  conectaba al primer resultado del escaneo. Al conectar mandaba la hora y después notificaciones (texto) y GPS.
- **Impacto:** fuga de notificaciones y ubicación a un aparato ajeno, sin intervención del usuario. Era real hoy
  (la tarjeta aún no existe y la app escanea).
- **Corrección:** verificación de las 4 características propias de la tarjeta (un NUS genérico solo tiene 2) antes de
  marcar la conexión y mandar nada; los aparatos que fallan se ignoran en la sesión; la primera tarjeta verificada se
  **fija** por dirección (solo se escanea esa); "Olvidar tarjeta" en Ajustes > Conexión; se pide **vincular** con la app
  en pantalla.
- **Verificación:** compila; no probado con hardware.

### 3. Comandos BLE sin autenticación en el firmware (alta, latente)
- **Evidencia:** las características de escritura no exigían cifrado; vinculación "Just Works" aceptada siempre.
- **Corrección:** los comandos delicados (`corte`, `cal`, `odo`, `real`, `demo`, `reiniciar`, `wifi`, `vel`,
  `vincular`, `mant reset|set`, `ios olvidar|contestar|colgar`) solo se ejecutan si llegan por un enlace **cifrado**;
  vincular un teléfono nuevo solo en los **2 min después de encender** o tras `vincular` desde un teléfono ya vinculado
  o USB; a los teléfonos ya vinculados se les cifra el enlace al conectar. USB sigue sin restricción (acceso físico).
- **Riesgo residual:** "Just Works" no protege contra un atacante presente justo durante la ventana de vinculación
  (MITM). Mejora futura: clave numérica mostrada en el LCD (`ESP_IO_CAP_OUT`). Comandos no delicados (`gps`, `hora`,
  `notif`, `pantalla`, `perfil`, `brillo`) siguen abiertos: alguien podría falsear la velocidad de GPS que muestra el LCD.
- **Pendiente de probar en la tarjeta:** que Android cifre el enlace al reconectar y que la app (que pide la
  vinculación) quede con los comandos delicados habilitados.

### 4. Restaurar respaldo (media)
- **Evidencia:** `Respaldo.restaurar()` escribía cualquier archivo de ajustes del JSON, incluido `root`
  (`concedido=true`, `gestos=true` → al abrir la app se corrían comandos root automáticamente); leía el archivo
  completo sin tope; validaba mientras escribía (un error a la mitad dejaba los ajustes a medias); corría en el hilo
  principal.
- **Corrección:** se valida todo antes de escribir; nombres de ajustes y de rodadas con lista blanca; los ajustes de
  root nunca se restauran; tope de 32 MB; escritura en `Dispatchers.IO`.
- **Verificación:** 6 pruebas unitarias (`RespaldoTest`) pasan: respaldo legítimo, root excluido, nombres con `../`
  ignorados, rutas de rodadas peligrosas ignoradas, respaldo dañado rechazado completo, archivo de otra app rechazado.

### 5. Firma con la llave de desarrollo (media, pendiente)
El build release se firma con `signingConfigs.debug`. La llave debug vive en `~/.android/debug.keystore` con clave
pública conocida; si ese archivo se filtra, alguien podría firmar una "actualización" que se instale encima de la app.
**No se cambió** porque cambiar la firma obliga a desinstalar (se borran los datos). Para hacerlo: Ajustes > Respaldo >
Guardar, crear una llave release propia (`keytool`), firmar con ella, desinstalar, instalar y restaurar el respaldo.

### 6. Firmware y core ESP32 3.3.11 (media, pendiente)
Con el core 3.3.11 el ESP32-S3 viene con NimBLE (`CONFIG_BT_NIMBLE_ENABLED`) y el firmware usa APIs de Bluedroid
(`esp_gatts_cb_event_t`, `BLESecurityCallbacks`…). El firmware **original 1.3 tampoco compila** en modo placa real con
este core: no lo causó esta revisión. Opciones: instalar una versión anterior del core que use Bluedroid en el S3, o
portar el BLE a NimBLE.

## Investigación de puertas traseras
- **Sin hallazgos.** No hay carga dinámica de código (`DexClassLoader`, `Class.forName`), reflexión, Base64 ni código
  ofuscado. La app no tiene cuentas ni autenticación, así que no hay controles que saltarse.
- **Root:** comandos fijos con el propio nombre de paquete (no se arma texto del usuario); `Root.ejecutar` solo se llama
  desde `Root.kt`.
- **Red (todo HTTPS):** Open-Meteo (clima, lluvia en ruta), NOAA (huracanes), USGS (sismos), Overpass/OpenStreetMap
  (límites de velocidad), Carto/OpenTopoMap/Esri (mapas). Se manda la ubicación aproximada a esos servicios porque
  la función lo requiere; nada más.
- **"Secreto" del logo:** easter egg cosmético (acento Oro XOF), sin efectos de seguridad.
- **Firmware:** `onPassKeyRequest()` devuelve `123456`, pero con `ESP_IO_CAP_NONE` no se usa.
- **Compilación:** plugins solo de google()/mavenCentral(); el wrapper descarga Gradle de `services.gradle.org`.
  No se verificó el checksum de `gradle-wrapper.jar` contra el oficial.

## Dependencias revisadas
AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2025.01.00, Material3, coroutines 1.9.0, CameraX 1.4.1, osmdroid 6.1.18,
core-ktx 1.15.0, activity-compose 1.9.3, lifecycle 2.8.7. Solo de prueba: JUnit 4.13.2, org.json 20240303.
**No se corrió un escáner de vulnerabilidades (CVE)** porque no hay uno instalado; no se actualizó nada a ciegas.

## Pruebas ejecutadas (resultados reales)
- `testDebugUnitTest`: **11/11 pasan** (`RespaldoTest` 6, `LogicaTest` 5).
- `lintDebug`: **0 errores** (antes 1). 38 advertencias revisadas: 36 `StaticFieldLeak` son falsos positivos (todos
  guardan `applicationContext`; Dashcam suelta la Activity en `onStop`), `HighSamplingRate` es necesaria (RPM por
  vibración) y `ObsoleteSdkInt` en `mipmap-anydpi-v26` la exige AAPT (quitarla rompe el ícono).
- `assembleDebug` y `assembleRelease` (R8): compilan; APK release 2.5 MB.
- Firmware: modo simulador para ESP32-S3 compila. Modo placa real compilado para ESP32 clásico (Bluedroid) llega al
  enlazado; solo falla por memoria IRAM del chip clásico (igual que el original), así que el código BLE/WiFi con los
  cambios es válido. Modo placa real para ESP32-S3: no compila con el core 3.3.11 (hallazgo 6).
- **No realizadas:** pruebas en el teléfono y en la tarjeta, pruebas dinámicas de BLE/WiFi, medición de fluidez
  (usa Ajustes > Pantalla > Medidor de fluidez en el teléfono).
