# FT150TS — tablero digital de la moto

App Android (`app/`, Kotlin + Compose, paquete `com.xof.ft150ts`) + firmware ESP32-S3 (`firmware/ft150ts_dashboard/`) + hardware (`pcb/` placas KiCad, `carcasa/` piezas 3D CadQuery).

**Antes de explorar el código, lee `docs/MAPA.md`**: tiene qué hace cada carpeta y archivo, las secciones del firmware, el protocolo BLE y "si quiero X → toca Y". Cuando cambies algo grande, actualízalo con el skill `mapa-proyecto`.

## Comandos
- App: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; ./gradlew :app:testDebugUnitTest :app:assembleRelease`
- Instalar: `adb install -r app/build/outputs/apk/release/app-release.apk`
- Publicar: `docs/RELEASE.md`. Esta carpeta no es repo git: el código se sube a `z-XOF-z/FT150TS` con un clon aparte, y el APK como release.
- Firmware: core esp32 **3.2.1** (no 3.3), ESP32S3 Dev Module, Huge APP.
- Placas: Python de KiCad 10 → `pcb/generar_pcb.py B --rutear`, `pcb/extras/generar_extra.py E|F`.

## Reglas
- No cambiar `applicationId` ni la firma (`~/.android/debug.keystore`); `versionCode` siempre sube; nunca desinstalar la app del teléfono.
- No dejar el modo demo prendido; `MODO_SIMULADOR = 0` en la moto.
- Root solo con lo necesario, en lista permitida y reversible; no desactivar SELinux ni tocar el bootloader.
- Piezas de las placas: solo las que se consiguen en tiendas de México (nada exclusivo de AliExpress).
- Responder a Andrew en español.
