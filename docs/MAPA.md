# Mapa del proyecto FT150TS

Tablero digital para la moto **Italika FT150TS**, en tres partes que trabajan juntas:

1. **App Android** (`app/`): Kotlin + Jetpack Compose. Paquete `com.xof.ft150ts`. Convierte el POCO F4 en tablero y launcher (HOME) de la moto: velocímetro, RPM, marcha, mapas, CarPlay (vía DiPlay), música, SOS, ahorro nocturno con root y temas.
2. **Firmware ESP32-S3** (`firmware/ft150ts_dashboard/`): lee los sensores de la moto, muestra una LCD 20x4 y le manda la telemetría a la app por Bluetooth (BLE).
3. **Hardware** (`pcb/`, `carcasa/`): la placa base para el ESP32 (KiCad → JLCPCB) y las piezas impresas en 3D (soporte del teléfono y caja bajo el asiento).

## Compilar, probar y publicar

| Qué | Comando o pasos |
|---|---|
| App: pruebas + APK | Git Bash: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; ./gradlew :app:testDebugUnitTest :app:assembleRelease` (sin JAVA_HOME falla al arrancar; Gradle usa JDK 21 por `gradle/gradle-daemon-jvm.properties`) |
| App: instalar | `adb install -r app/build/outputs/apk/release/app-release.apk`. Misma firma (`debug.keystore` de esta PC); **nunca desinstalar** |
| App: publicar | Sube `versionCode`/`versionName` en `app/build.gradle.kts` → APK a `releases/FT150TS-vX.apk` + `releases/NOTAS-vX.md` → release `vX` en `z-XOF-z/FT150TS` con el APK adjunto. Guía: `docs/RELEASE.md`. Esta carpeta **no es repo git**: el código se sube con un clon aparte |
| Firmware | Arduino IDE o arduino-cli, placa "ESP32S3 Dev Module", **core esp32 3.2.1** (el 3.3 rompe el BLE), Huge APP. Flash 16MB y PSRAM OPI para la N16R8 |
| Placas | Python de KiCad 10: `pcb/generar_pcb.py B --rutear` (placa principal), `pcb/extras/generar_extra.py E|F`, `pcb/extras/generar_perforada.py`. Freerouting necesita Java 25 (JBR) |
| Piezas 3D | `carcasa/.venv` (CadQuery): `python carcasa/caja_tablero/caja_tablero.py`, `caja_bateria.py` |

## Carpetas

| Carpeta | Qué hay |
|---|---|
| `app/src/main/java/com/xof/ft150ts/` | Lógica de la app (≈70 archivos, uno por función) |
| `app/src/main/java/com/xof/ft150ts/ui/` | Pantallas y tableros en Compose (≈36 archivos) |
| `app/src/test/` | Pruebas unitarias (104 a la v3.10.0) de las partes `*Logica.kt` y protocolos |
| `firmware/ft150ts_dashboard/` | El `.ino` único (~3 660 líneas) |
| `firmware/simulador_wokwi/` | Copia del sketch + `diagram.json` para simular en Wokwi/Velxio |
| `firmware/HARDWARE-CORTE-CHISPA.md` | Circuito del corte de chispa (PC817 + IRF840) |
| `docs/` | `CARPLAY.md`, `AHORRO_NOCTURNO.md`, `SOS.md`, `RELEASE.md`, este mapa |
| `releases/` | APK de cada versión + `NOTAS-v*.md` (la app muestra las notas como "qué hay de nuevo") |
| `modulo-magisk/` | Módulo "ft150ts_sin_barra": quita la barra de 3 puntos de las ventanas libres (CarPlay) |
| `scripts/` | Diagnósticos de solo lectura por ADB (`diagnostico-carplay.ps1`, `diagnostico-energia.ps1`) |
| `pcb/` | Diseño de la placa base (ver la sección Placas) |
| `carcasa/` | Piezas 3D (ver la sección Piezas 3D) |

## App: archivos por área

| Área | Archivos | Qué hacen |
|---|---|---|
| Arranque y navegación | `MainActivity.kt`, `ui/AppFT150.kt`, `ui/Barra.kt`, `ui/Arranque.kt`, `ui/PantallaInicio.kt`, `ui/InicioNeon.kt` | Launcher HOME; pager Tablero · Auto · Moto · Ajustes; pantallas de inicio |
| Conexión con el ESP32 | `BleManager.kt`, `Moto.kt`, `ServicioMoto.kt`, `Modelo.kt` (`Estado`, `Rapida`) | BLE, comandos a la tarjeta, servicio en segundo plano, modelo de datos |
| Tableros | `ui/PantallaTablero.kt`, `ui/Tableros*.kt`, `ui/Tablero*.kt`, `ui/TinteTableros.kt`, `ui/TableroUniversal.kt` | LCD, deportivos, instrumento, neón, morado, telemetría, dual neón |
| Datos de manejo | `Velocimetro.kt`, `Marcha.kt`, `RpmVibracion.kt`, `Sensores.kt`, `Termico.kt`, `Odometro.kt`, `Gasolina.kt`, `Estadisticas.kt`, `Arrancones.kt`, `Baches.kt`, `LimitesVia.kt` | Velocidad, marcha estimada, RPM por vibración, mantenimiento, consumo |
| Auto, mapas y CarPlay | `ui/PantallaAuto.kt`, `CarPlay.kt`, `CarPlayPestana.kt`, `ui/MapaCarPlay.kt`, `GestosCarPlay*.kt`, `Mapas.kt`, `Ubicacion.kt`, `Gpx.kt`, `Rodadas.kt`, `Rutinas*.kt`, `LluviaRuta.kt`, `Clima.kt` | CarPlay de DiPlay dentro de la pestaña, mapas, rodadas y rutinas |
| Música y audio | `Musica.kt`, `AudioSalida.kt`, `VolumenAuto.kt`, `VolumenLogica.kt`, `Sonoridad.kt`, `ui/AjustesAudio.kt` | Control de música y volumen según la velocidad |
| Teléfono y apps | `Lanzador.kt`, `ui/PantallaApps.kt`, `Acciones.kt`, `NotifListener.kt`, `TelefonoMoto.kt`, `ComandosVoz.kt`, `Voz.kt` | Lista de apps, notificaciones, voz |
| Root y energía | `Root.kt`, `AyudanteRoot.kt`, `Energia.kt`, `Nocturno*.kt`, `Reposo.kt`, `Encendido.kt`, `DiagnosticoEnergia.kt`, `ModoRendimiento*.kt`, `Pantalla.kt`, `Fluidez.kt` | Sesión `su` con lista permitida, ahorro nocturno, modo rendimiento, 90/120 Hz |
| Seguridad | `Emergencia.kt`, `ui/SosUi.kt`, `Dashcam.kt`, `Estacionamiento.kt`, `ZonaWifi.kt`, `NfcMoto.kt` | SOS con cuenta regresiva, dashcam (con `.srt`), dónde quedó la moto |
| Funciones avanzadas | `FuncionesAvanzadas.kt`, `ui/AjustesAvanzadas.kt` | Limitador, launch, quickshifter y diagnóstico (todo apagado; protocolo `av`) |
| Apariencia | `Apariencia.kt`, `Estilos.kt`, `ui/Tema.kt`, `ui/MotorEstilos.kt`, `ui/Vertice.kt`, `ui/IconosApp.kt` | Temas, estilos de tablero, íconos |
| Ajustes y respaldo | `ui/PantallaAjustes.kt`, `ui/Ajustes*.kt`, `Respaldo.kt`, `Actualizador.kt` | Configuración, respaldo y actualizador desde GitHub |
| Otros | `Logros.kt`, `Alertas.kt`, `ui/AlertasUi.kt`, `BateriaTel.kt`, `Brujula.kt`, `Luz.kt`, `Pid.kt`, `Sueno.kt`, `BluetoothAdmin.kt` | Logros, alertas, sensores del teléfono |

Archivos `*Logica.kt`: lógica pura sin Android, para que se pueda probar en `app/src/test/`.

## Firmware: secciones del `.ino`

| Línea aprox. | Sección |
|---|---|
| 64 | 0) Interruptores generales (`MODO_SIMULADOR`, `USAR_*`) |
| 292 | 2) Configuración: pines, valores `<-- X` que hay que llenar |
| 620 / 674 | 5) Memoria NVS · 6) Tarjeta SD (CSV) |
| 724–1044 | 7) Analógicos · 8) RPM y velocidad (interrupciones) · 9) GPS · 10) MPU6050 · 11) DS18B20 · 12) Marcha e ignición · 13) Botones |
| 1076–1617 | 14) Pantalla · 15) Mantenimiento · 16) Viajes · 17) Motor · 18) Baterías · 19) Shift light · 20) Modo pista · 21) Corte de motor |
| 1617 | 21b) Funciones avanzadas (`USAR_CORTE_CHISPA 0`, tarea FreeRTOS `tareaChispa`) |
| 1851–2310 | 22) Alertas · 23) Vistas · 24) Menú · 25) Modo demo · 26) Lectura real |
| 2328 / 2478 | 27) Notificaciones · 27b) iPhone |
| 2903 | 28) Consola (Serial y web, mismos comandos) |
| 3175 / 3296 | 29) WiFi · 29b) Telemetría BLE para la app |
| 3488 / 3548 | 30) Sueño profundo · 31) setup/loop |

## Cómo fluye la información

- Sensores (bobina → RPM, Hall → velocidad, GPS, MPU6050, temperatura, voltajes) → **ESP32** → LCD 20x4.
- ESP32 → **BLE** (sección 29b) → `BleManager` → `Moto` → `Estado` → tableros de la app.
- App → comandos de texto por BLE (`Moto.comando`, protocolo de la consola, sección 28): calibración y funciones avanzadas (`av ...`).
- Respuestas especiales: JSON `{"av":1,...}` (estado avanzado) y `"d"` cada 3 s (diagnóstico).

## Placas (`pcb/`)

| Archivo | Qué es |
|---|---|
| `diseno.py` | Netlist de la placa A/B en texto: pieza, huella, valor y red de cada pin. **Se edita aquí, no en KiCad** |
| `generar_pcb.py` | Acomoda (ANCLAS + acomodo automático), rutea con Freerouting y pone el plano de tierra |
| `esquematico/generar_esquematico.py` | Esquemático de la B a partir de `diseno.py` |
| `simulacion/` | 6 circuitos para simular con ngspice + `modelos.lib` |
| `extras/` | Placas E (expansión I2C), F (fusibles) y P (perforada) |
| `fabricar/<X>_JLCPCB/` | Gerbers en zip, listos para JLCPCB |
| `LISTA_COMPRAS.md`, `LEEME.md` | Piezas (tiendas de México) y guía |

**Pines de la placa B para el firmware (pendiente de aplicar):** relevadores en GPIO40/38/45, carga en GPIO43, entradas aisladas en GPIO21/18/3/46, salida aislada en GPIO44, corte de chispa en GPIO47, `NEUTRAL_ACTIVO_BAJO=false`.

## Piezas 3D (`carcasa/`)

- `*.py` en la raíz + `stl/`: soporte del POCO F4 con rótula de 24.5 mm (bandeja, marco, tapa, perilla, sockets y anillos de calibración).
- `caja_tablero/`: caja de la placa (`caja_tablero.py`), que se atornilla a los 4 birlos M6 del asiento, y porta-batería (`caja_bateria.py`).

## Si quiero… → toca aquí

| Quiero | Archivo |
|---|---|
| Un tablero nuevo | Composable en `ui/Tableros*.kt` → valor en `enum EstiloTablero` (`Apariencia.kt`) → nombre en `ui/Tableros.kt` → caso en `ui/PantallaTablero.kt` → botón en `ui/PantallaAjustes.kt` |
| Un ajuste nuevo | `ui/PantallaAjustes.kt` (o su `ui/Ajustes*.kt`) |
| Un dato nuevo de la moto en la app | Firmware 29b (enviarlo) → `Modelo.kt` (`Estado`) → `Moto.kt` (leerlo) |
| Un comando nuevo para la tarjeta | Firmware sección 28 (consola) → `Moto.comando(...)` en la app |
| Cambiar un pin | Firmware sección 2 (y `pcb/diseno.py` si es de la placa) |
| Cambiar la placa | `pcb/diseno.py` → `generar_pcb.py B --rutear` → revisar DRC → exportar gerbers |
| Publicar versión | `docs/RELEASE.md` |
| Algo de CarPlay | `CarPlay.kt` / `CarPlayPestana.kt` + `docs/CARPLAY.md` |

## Trampas conocidas

- **App:**
  - No cambiar `applicationId` ni la firma: se perderían los datos del teléfono.
  - El `versionCode` siempre debe subir.
  - El modo demo nunca se deja prendido.
  - Las apps que se abren desde VS Code heredan `ELECTRON_RUN_AS_NODE=1`, y Arduino IDE y Code se cierran al instante: hay que quitar esa variable antes de abrirlas.
- **HyperOS (POCO F4):**
  - `grep` no soporta `\|`: usa `grep -E`.
  - SurfaceFlinger imprime la escala como fila de matriz.
  - DiPlay reinicia las medidas de su ventana.
  - Más detalles en `docs/CARPLAY.md`.
- **Firmware:**
  - No actualizar el core del ESP32 a 3.3 (NimBLE rompe el BLE).
  - Con la N16R8 no usar GPIO35–37 (los ocupa la PSRAM).
- **Placas:**
  - El archivo de reglas de Freerouting debe llamarse igual que el DSN.
  - Una pieza del lado equivocado del DevKit hace que Freerouting reporte "0 sin rutear" sin haberla ruteado: siempre revisar el DRC.

Última revisión: 2026-10-25 (v3.10.0, firmware v1.5, placa B v2.3)
