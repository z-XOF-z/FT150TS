# Release, instalación y actualización de FT150TS

Versión actual: **3.4** (versionCode 33, publicada en GitHub como estable el 2026-10-18) · paquete `com.xof.ft150ts` · firma SHA-256 `3efd04bd…ab65` (llave debug de
la PC de desarrollo: `C:\Users\xbox4\.android\debug.keystore`).

## 1. Abrir y correr desde Android Studio

1. **File → Open** → la carpeta del proyecto (`FT150TS-App-v3.0`, o la que sale de descomprimir `FT150TS-v3.2-proyecto.zip`).
2. Espera **Gradle sync**. La primera vez descarga dependencias y tarda unos minutos.
   - Gradle usa un JDK 21 aunque Android Studio traiga Java 25 (`gradle/gradle-daemon-jvm.properties`). Si dice que
     no encuentra un JDK 21: Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK** → uno 21
     (o *Download JDK* → versión 21).
   - En otra PC, Android Studio crea `local.properties` solo (ruta del SDK).
3. Conecta el teléfono con **Depuración USB** activada, elígelo arriba y pulsa **Run ▶**.

Run instala la variante *debug* encima de la que tengas, **sin borrar datos**, porque usa el mismo paquete y la misma
firma. La app se abre sola.

## 2. Compilar el APK de release

- **Android Studio:** Build → **Build App Bundle(s) / APK(s) → Build APK(s)** sobre la variante `release` (Build
  Variants → app → release).
- **Terminal (Git Bash):**
  ```bash
  cd /c/Users/xbox4/Downloads/FT150TS-App-v3.0
  ./gradlew :app:testDebugUnitTest :app:assembleRelease
  cp app/build/outputs/apk/release/app-release.apk releases/FT150TS-v3.2.apk
  ```
El release sale optimizado (R8) y firmado con la misma llave debug, así que se instala encima de cualquier versión
anterior de esta PC.

> **Ojo con la llave:** `debug.keystore` es la única llave que puede actualizar tus instalaciones. Si la pierdes (o
> compilas en otra PC sin copiarla), Android obliga a desinstalar y se pierden los datos. Haz una copia de
> `C:\Users\xbox4\.android\debug.keystore` en un lugar seguro. **No la subas a GitHub** (`.gitignore` ya la excluye).

## 3. Instalar o actualizar en el teléfono

| Cómo | Pasos | ¿Conserva datos? |
|---|---|---|
| **Android Studio** | Run ▶ con el teléfono conectado | Sí |
| **ADB** | `adb install -r releases/FT150TS-v3.2.apk` | Sí |
| **APK en el teléfono** | Copia el APK al teléfono → ábrelo → "Actualizar". La primera vez Android pide permitir "Instalar apps desconocidas" para el explorador de archivos | Sí |
| **Desde la app (v3.2+)** | Ajustes → Acerca de → **Buscar actualización** → **Descargar e instalar**; o **Instalar un APK** y eliges el archivo | Sí |

**La primera vez que pasas a la v3.2** usa Android Studio, ADB o el APK: las versiones 3.0 y 3.1 no traen el
actualizador. De la 3.2 en adelante ya puedes actualizar desde la app.

**Lo que revisa la app antes de instalar** (Ajustes → Acerca de):
- que el APK sea FT150TS (`com.xof.ft150ts`);
- que tenga **la misma firma** que la app instalada. Si no, ni lo intenta: Android obligaría a desinstalar;
- que sea **más nuevo** (versionCode mayor);
- guarda un respaldo en `Descargas/FT150TS` antes de instalar;
- descarga solo por https desde GitHub (`api.github.com`, `github.com`, `*.githubusercontent.com`);
- Android siempre muestra su confirmación ("¿Quieres actualizar esta app?"). La app nunca se instala sola sin que
  aceptes.

## 4. Publicar una versión nueva (para que "Buscar actualización" la encuentre)

La app busca en **https://github.com/z-XOF-z/FT150TS/releases/latest**: el release más nuevo, que no sea borrador ni
pre-release, con un archivo `.apk` adjunto. Hoy el último publicado es **v3.0**.

1. Sube el `versionCode` y el `versionName` en `app/build.gradle.kts` (p. ej. 27 / "3.3").
2. Compila el release (sección 2) y copia el APK a `releases/FT150TS-v3.3.apk`.
3. En GitHub: **Releases → Draft a new release**.
   - **Tag:** `v3.3`. El número tiene que ser igual al `versionName`, con una `v` adelante.
   - **Título:** `FT150TS v3.3`. **Descripción:** pega `releases/NOTAS-v3.3.md`. La app la muestra como "qué hay de nuevo".
   - **Adjunta** `FT150TS-v3.3.apk`.
   - Deja sin marcar *Set as a pre-release* y pulsa **Publish release**.
4. En el teléfono: Ajustes → Acerca de → Buscar actualización → aparece la v3.3.

### Betas (versiones de prueba)

- En `app/build.gradle.kts`: `versionName = "3.3-beta.1"` y **siempre** un `versionCode` mayor que el anterior.
- Tag `v3.3-beta.1` y marca **Set as a pre-release**. GitHub no la pone como "latest": quien baje la app normal sigue
  viendo la estable.
- En la app, con **Ajustes → Acerca de → Versiones de prueba (beta)** prendido, "Buscar actualización" también mira
  las pre-releases. Orden: `3.3-beta.1 < 3.3-beta.2 < 3.3-rc.1 < 3.3`. En una versión beta el interruptor viene
  prendido de fábrica.
- Para pasar la beta a estable: compila con `versionName = "3.3"` y un versionCode mayor, y publica el release sin
  marcar pre-release.

Así quedó publicada la estable actual: tag `v3.4`, release normal (latest), con `FT150TS-v3.4.apk` y
`releases/NOTAS-v3.4.md`. Las betas de la 3.3 y la 3.4 siguen en GitHub como pre-releases.

## 5. Si algo sale mal

- **"La firma no es la misma":** ese APK se compiló con otra llave (otra PC). No lo instales encima. Compílalo en la
  PC que tiene `debug.keystore`.
- **"Es más viejo que el instalado":** Android no deja bajar de versión encima. Para volver atrás: respaldo → compila
  la versión vieja con un versionCode mayor → instala (ver `docs/CARPLAY.md` y `docs/AHORRO_NOCTURNO.md`).
- **El instalador dice "App no instalada":** revisa espacio libre y que el APK se haya descargado completo. Vuelve a
  buscar la actualización.
- **Datos:** el respaldo automático queda en `Descargas/FT150TS/FT150TS-respaldo-*.json` (Ajustes → Respaldo →
  Restaurar).
