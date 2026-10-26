# CarPlay en la página Auto (con DiPlay)

Desde la v3.1, la página **Auto** puede mostrar el **mapa en vivo de CarPlay** de tu iPhone en lugar del mapa
de OpenStreetMap. El receptor de CarPlay es **[DiPlay](https://github.com/shihabal3amri/DiPlay)**: la app que ya
tienes instalada en el teléfono. FT150TS no lo incluye ni lo compila. Lo encuentra en el teléfono y le pide el mapa.

## v3.3: CarPlay completo en la página Auto (modos de pantalla)

La v3.1 solo usaba el **mapa de tablero** de DiPlay (stream 111). DiPlay lo marca como experimental y lo hizo para la
pantalla del tablero de los BYD. En un teléfono el iPhone puede no mandarlo nunca, aunque CarPlay funcione en DiPlay;
por eso no funcionaba. Desde la v3.3, FT150TS usa también la **pantalla completa de CarPlay** de DiPlay (toque, música,
Siri) en una ventana de Android. DiPlay la redibuja al tamaño de esa ventana.

| Modo | Qué pasa | Probado |
|---|---|---|
| **Pestaña** (v3.3-beta.2, de fábrica) | CarPlay completo ocupa **toda la pestaña Auto**: debajo de las pestañas de FT150TS (o a la derecha del riel en Vértice). Al deslizar a otra pestaña, irte a otra app o entrar en reposo, la ventana se **mueve fuera de la pantalla con el mismo tamaño**, así que CarPlay no se reconecta, y regresa al volver a Auto. El mapa OSM de abajo deja de dibujarse mientras tanto. Necesita root | **Probado en el POCO F4 con el iPhone conectado**, con los mismos comandos que usa la app: CarPlay (Apple Maps, Ahora suena, dock) llenó todo el display bajo las pestañas. Se movió fuera y regresó al instante, a 42 FPS, sin reconectar. Falta confirmar el cambio de pestaña desde la app |
| **Ventana** | CarPlay en una ventana flotante sobre el mapa. **Con root** (recomendado): `am start --windowingMode 5` + `am task resize` + compensación de la escala de HyperOS. Sin root: `setLaunchBounds` | **Funcionó en el POCO F4** (HyperOS 3.0, Android 16, root): al entrar a Auto, FT150TS abrió CarPlay solo como ventana flotante anclada en el mapa. HyperOS dibuja sus ventanas a escala **0.6102**; FT150TS la lee del compositor y agranda los límites: la ventana ocupa **todo el ancho del mapa** y en alto lo que cabe en pantalla (unos 2/3 del mapa). Sin root, Android ignora los límites si CarPlay ya estaba abierto, y en Android puro de teléfono (emulador) sale en pantalla completa. FT150TS lo detecta y avisa |
| **Dividida** | CarPlay en media pantalla y FT150TS en la otra (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) | **Funcionó** en el emulador. **No sirve si FT150TS es tu pantalla de inicio** (como en tu POCO F4): Android no deja la pantalla de inicio en pantalla dividida. FT150TS lo detecta, no la abre sola y recomienda Ventana |
| **Total** | CarPlay en pantalla completa en DiPlay | Funcionó |
| **Mapa** | El mapa de tablero incrustado (v3.1) | Necesita que el iPhone mande el stream 111 |

- **Abrir CarPlay al entrar a Auto** (Ventana y Dividida): una vez por visita y no más de una vez por minuto, así que
  no hay ciclos. En Total nunca se abre solo.
- **Root (opcional):** "Prender ventanas flotantes" (`enable_freeform_support`, requiere reiniciar) y "Forzar ventana
  flotante" (`am start --windowingMode 5`, sin `-S`: nunca detiene DiPlay).
- **Imposible sin firma de sistema:** meter la actividad de DiPlay *dentro* de la vista de FT150TS (ActivityView /
  TaskView son solo para apps del sistema, y DiPlay no permite incrustarse). Por eso la integración es por ventana.
- En el emulador DiPlay 0.2.21 se cerró solo al arrancar CarPlay ("manualHotspotSsid is required in manual hotspot
  mode") porque ahí no está configurado. Es de DiPlay, no de FT150TS; en un DiPlay configurado no pasa.

**Recomendado en el POCO F4: Ventana con root** (Ajustes → Root → permitir en Magisk). Es lo que se probó: CarPlay
completo y tocable sobre el mapa, con la franja de estado, la velocidad y las demás páginas de FT150TS alrededor. La
ventana se puede mover o cerrar con la barra de HyperOS de arriba. La sesión sigue en DiPlay.

Datos del POCO F4 probado (2026-10-18): HyperOS OS3.0.304.0 (Android 16, API 36), kernel 4.19 "aptusitu", Magisk
30.7, SELinux Enforcing, ventanas flotantes soportadas y prendidas, DiPlay 0.2.16 oficial (firma correcta), FT150TS
como pantalla de inicio. Faltó tener el iPhone conectado: la ventana mostraba "Conexión interrumpida. Reintentando…"
de DiPlay.

## Qué hace y qué no

| | |
|---|---|
| **Sí** | Mapa de CarPlay dentro de la página Auto, con su estado real (conectando, sin mapa, en vivo, error). |
| **Sí** | Botón **CarPlay**: abre CarPlay completo (toque, música, Siri) en DiPlay. Tocar el mapa hace lo mismo. |
| **Sí** | Sin sesión de CarPlay, el mapa de OpenStreetMap de siempre con todos sus botones. |
| **Sí** | Botón **OSM** para volver al mapa de siempre; **CP** (en el mapa) para regresar a CarPlay. |
| **No** | FT150TS no habla con el iPhone: la conexión, la autenticación, el audio y Siri los maneja DiPlay. |
| **No** | No se puede terminar la sesión de CarPlay desde FT150TS (la API de DiPlay no lo permite). Se hace en DiPlay. |
| **No** | El mapa que se ve es el **mapa de tablero** que manda el iPhone (en un teléfono, 1280×720, 16:9). La pantalla completa de CarPlay solo está en DiPlay. |

## Requisitos de DiPlay (verificados en su código)

| Requisito | Por qué |
|---|---|
| DiPlay **0.2.9 o más nuevo** | El servicio del mapa (`MapEmbedService`) llegó en la 0.2.9. La 0.2.18 lo trae (revisado en el APK). |
| Release oficial (firma `87b38b12…a363`) | FT150TS solo le habla a esa firma. Si compilaste DiPlay tú, usa **Confiar en esta copia**. |
| Android 11+ | `SurfaceControlViewHost`. El POCO F4 trae Android 12 o más nuevo. |
| Advanced → **CarPlay map on dashboard (experimental)** (en español: "Mapa de CarPlay en el panel de instrumentos") | Le pide al iPhone el mapa de tablero (stream 111). En un aparato sin pantalla de tablero, como el teléfono, DiPlay pide el "virtual" de 1280×720. **Sin esto no hay mapa.** Cambiarla reconecta la sesión. |
| Advanced → **Share the live map with other launchers** ("Compartir el mapa en vivo con otros launchers") | Deja que FT150TS pida el mapa. Apagada, DiPlay responde `disabled`. |
| La **pantalla de CarPlay** de DiPlay abierta al menos una vez y sin cerrarla desde Recientes | El video del mapa pasa por la pantalla de CarPlay (`CarPlayHostActivity`). Sigue funcionando en segundo plano, pero si la cierras desde Recientes o MIUI la mata, el mapa se corta ("carplay sin mapa") hasta que la vuelvas a abrir con el botón **CarPlay**. |
| "Dashboard map on the centre screen" | **No hace falta.** Es la tarjeta flotante de DiPlay. Mientras FT150TS muestra el mapa, DiPlay la esconde sola. Si la dejas prendida, puede aparecer encima de otras páginas de FT150TS. |

En POCO / Xiaomi (MIUI o HyperOS), si la franja dice "diplay no contesta":
**Ajustes → Apps → Administrar apps → DiPlay → Inicio automático: prendido** y **Ahorro de batería: Sin restricciones**.
MIUI puede bloquear que otra app despierte a DiPlay.

## Cómo se arma

1. Conecta el iPhone en DiPlay como ya lo haces y deja que abra su pantalla de CarPlay.
2. En DiPlay → Settings → **Advanced**: prende **CarPlay map on dashboard (experimental)** y **Share the live map
   with other launchers**.
3. En FT150TS, página **Auto**, toca **CP**. Arriba del mapa aparece la franja de CarPlay con el estado.

Si la franja dice algo distinto de "carplay en vivo", tócala: explica qué falta, muestra la versión de DiPlay y su
firma.

## Arquitectura

```text
iPhone ──USB/BT+WiFi──▶ DiPlay (app aparte, GPL-3.0, ya instalada)
                          │ iAP2, MFi, AirPlay, video, audio, Siri — pantalla de CarPlay (CarPlayHostActivity)
                          │ MapEmbedService (exportado, acción com.shihab.diplay.action.EMBED_MAP)
                          ▼ Messenger: ATTACH / RESIZE / DETACH  ◀──▶  ATTACHED / STREAM_STATE / ERROR
FT150TS ── página Auto ── SurfaceView ◀── SurfaceControlViewHost.SurfacePackage (DiPlay dibuja aquí)
```

- `CarPlay.kt`: busca DiPlay (paquete, firma, versión), abre CarPlay o DiPlay, guarda la preferencia.
  `ProtocoloCarPlay` es la parte pura (estados, reintentos, validaciones) y se prueba en `LogicaCarPlayTest`.
- `ui/MapaCarPlay.kt`: el enlace con el servicio de DiPlay (`EnlaceCarPlay`), la superficie y la franja de estado.
- `ui/PantallaAuto.kt` (`MapaMoto`): pone la franja arriba del mapa y la superficie encima de OSM.

Decisiones:
- **DiPlay es una dependencia del teléfono, no de la compilación.** No hay nada de DiPlay en Gradle ni en el APK.
  Compilar FT150TS no descarga ni compila DiPlay.
- **Por qué no se copió el motor de DiPlay dentro de FT150TS.** DiPlay es GPL-3.0 (y partes AGPL-3.0): meter su
  código obligaría a publicar FT150TS bajo GPL. Además, sin una identidad MFi no conecta. Usar la app que ya funciona
  en el teléfono da la misma sesión real y se actualiza con sus releases.
- **La franja va fuera del mapa.** Lo que se toca encima de la superficie de DiPlay lo recibe DiPlay, así que los
  botones de FT150TS no pueden ir encima del mapa de CarPlay.
- **Sin sesión, la superficie se queda pegada en 1 dp.** Así DiPlay avisa cuando el iPhone empieza a mandar el mapa
  y el cambio a CarPlay es solo. Con la página oculta, la pantalla apagada o FT150TS atrás, se suelta: DiPlay apaga
  ese decodificador y FT150TS no deja nada corriendo.
- **Reintentos sin trabar nada.** Si DiPlay niega el mapa, no contesta en 8 s o se cierra, se vuelve a pedir a los
  5 s, 10 s, 20 s y luego cada 30 s, hasta que conteste. Nunca hay dos pedidos al mismo tiempo.

## Seguridad

- Solo se le habla a los paquetes `com.shihab.diplay` y `com.shihab.diplay.hudtest`, y solo si están firmados con
  el certificado de los releases de DiPlay
  (SHA-256 `87b38b12788dcb202a961215f2572e30ec2dc9d8ef4bc070d05f77e49291a363`, leído de DiPlay 0.2.18). Una app
  ajena con el mismo nombre o la misma acción no recibe nada. Para una copia que compilaste tú, el detalle de la
  franja trae **Confiar en esta copia**: nunca se acepta sola. Esa confianza se queda en `carplay_confianza`, que
  los respaldos no restauran.
- Las respuestas se aceptan solo si vienen del uid de DiPlay. Las que llegan después de soltar el mapa se ignoran
  y su superficie se libera.
- Los textos de error que manda DiPlay se limpian y se recortan antes de mostrarse. Las medidas que se mandan se
  limitan a 1–8192 px.
- **Sin permisos nuevos** y **sin componentes exportados nuevos** (comparado contra el APK v3.0 con `aapt2`).
  Solo se agregaron `<queries>` para ver a DiPlay. No usa root.
- FT150TS no incluye ninguna llave ni certificado MFi. Ojo: los releases de DiPlay traen una identidad de accesorio
  "recuperada del firmware público de Carlinkit" (su README y `docs/COMPATIBILITY.md`). No es una identidad MFi
  emitida para DiPlay, se puede extraer y Apple puede dejar de aceptarla. Eso es de DiPlay, no de FT150TS.

## Compatibilidad y límites conocidos

- **Solo el mapa de tablero, no la pantalla completa.** La API pública de DiPlay solo comparte ese mapa. La pantalla
  completa (apps, música, toque) se abre en DiPlay con el botón **CarPlay**. Para regresar, usa el botón de atrás o
  Recientes.
- **El mapa depende de la pantalla de CarPlay de DiPlay** (ver Requisitos). Es un límite de cómo está hecho DiPlay,
  no de Android.
- **Recorte:** el mapa es 16:9 y DiPlay lo recorta para llenar el panel (el carrito queda al centro). En vertical
  se cortan los lados.
- **Cable:** el teléfono es USB host (OTG) para el iPhone. Mientras tanto, casi ningún teléfono carga, y el modo moto
  usa la carga como "switch". Con cable hace falta un hub OTG con carga, o usar el modo inalámbrico.
- **Inalámbrico:** Bluetooth + Wi-Fi Direct o hotspot. El Wi-Fi del teléfono queda ocupado mientras dura CarPlay, y
  el mapa de OSM y el clima usan los datos móviles.
- **Gestos:** encima del mapa de CarPlay no se cambia de página deslizando (el toque es de DiPlay). Se usa la
  franja de arriba de la app, o las pestañas.
- **Terminar la sesión:** solo desde DiPlay. **OSM** solo quita el mapa de FT150TS.

## Compilar e instalar (sin compilar DiPlay)

**Android Studio:** File → Open → la carpeta `FT150TS-App-v3.0` → espera "Gradle sync" → elige el POCO F4 arriba →
**Run ▶**. Instala la variante debug encima de la que ya tienes, sin borrar datos (ver abajo).

- JDK: Android Studio trae Java 25, y Gradle 8.11 / Kotlin 2.1 no corren en él. `gradle/gradle-daemon-jvm.properties`
  le dice a Gradle que use un JDK 21, que encuentra solo (en esta PC: `C:\Users\xbox4\.jdks\jbr-21.0.11`). Probado
  con `JAVA_HOME` apuntando al Java 25 de Android Studio: compila. Si Android Studio dice que no encuentra un JDK 21:
  Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK** → `jbr-21`.
- No se cambió ninguna versión de Gradle, AGP, Kotlin ni SDK.

**Línea de comandos (Git Bash):**
```bash
cd /c/Users/xbox4/Downloads/FT150TS-App-v3.0
./gradlew :app:testDebugUnitTest :app:lintDebug          # pruebas y análisis
./gradlew :app:installDebug                               # compila e instala la variante debug (= Run)
# o la versión optimizada:
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```
`adb` está en `C:\Users\xbox4\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

## Actualizar sin perder datos

FT150TS se actualiza encima, sin desinstalar, porque todas sus compilaciones tienen:
- el mismo paquete: `com.xof.ft150ts` (debug y release, sin sufijo);
- la misma firma: la llave debug de esta PC, SHA-256 `3efd04bd…ab65`. Revisado con `apksigner` en v3.0, v3.1,
  release y debug.

Antes de instalar en el POCO F4, corre el diagnóstico (solo lee, no cambia nada):
```powershell
powershell -ExecutionPolicy Bypass -File scripts\diagnostico-carplay.ps1
```
Te dice la versión de Android y HyperOS, la versión y firma de FT150TS y DiPlay instaladas, si el servicio del mapa
está, si la pantalla de CarPlay está abierta y el registro de FT150TS/DiPlay.
- Firma de FT150TS **COINCIDE** → Run o `adb install -r` actualiza con tus ajustes, rodadas y respaldos intactos.
- **NO COINCIDE** (la instalaste desde otra PC) → **no la desinstales**. Haz un respaldo en Ajustes → Respaldo, guárdalo
  fuera del teléfono y decide después. Android no deja instalar encima una app con otra firma.
- Por si acaso, antes de cada instalación: Ajustes → Respaldo → Guardar.

## Pruebas

| Prueba | Resultado |
|---|---|
| `gradlew :app:testDebugUnitTest` | 25/25 (`LogicaCarPlayTest` 13, `RespaldoTest` 7, `LogicaTest` 5) |
| `gradlew :app:lintDebug` | 0 errores; ninguna advertencia en el código de CarPlay |
| `assembleDebug` y `assembleRelease` con `JAVA_HOME` = Java 25 de Android Studio | OK |
| Firma y paquete de debug, release, v3.0 y v3.1 | Iguales |
| Permisos y componentes exportados vs v3.0 | Idénticos |
| Script de diagnóstico | Sintaxis OK; sin teléfono responde "No hay teléfono conectado" |
| **En el POCO F4 con DiPlay + iPhone** | **Pendiente**: el teléfono no estaba conectado por ADB. |

Lista para el POCO F4 (con el diagnóstico corriendo antes):
1. **Instalar:** Run en Android Studio → FT150TS abre con tus datos (tablero, ajustes, rodadas).
2. **Sin DiPlay listo:** toca **CP** → "conectando…" y después el estado real. OSM sigue funcionando.
3. **Compartir apagado:** apaga "Share the live map…" → "activa compartir mapa en diplay". Préndelo → cambia solo
   en ≤ 30 s.
4. **Sin mapa:** apaga "CarPlay map on dashboard" → "carplay sin mapa" y se ve OSM.
5. **En vivo:** iPhone conectado, las dos opciones prendidas, pantalla de CarPlay abierta una vez → "carplay en
   vivo" y el mapa de CarPlay en lugar de OSM, con la velocidad en la franja.
6. **Toque:** toca el mapa → se abre CarPlay completo. Atrás → regresas a FT150TS y el mapa vuelve en ~1 s.
7. **Audio:** con música en CarPlay, ve a la página Auto y a otras: el audio sigue (lo maneja DiPlay).
8. **Desconectar el iPhone** → "carplay sin mapa" y OSM. Reconectarlo → vuelve "en vivo" solo.
9. **Cerrar la pantalla de CarPlay desde Recientes** → "carplay sin mapa". Botón **CarPlay** → vuelve.
10. **Forzar detención de DiPlay** (Ajustes → Apps) → "diplay se cerro · reconectando" o "diplay no contesta"; al
    abrir DiPlay se recupera solo.
11. **Recursos:** cambia de página, apaga la pantalla, gira el teléfono → no se traba. Con otra página a la vista,
    `adb shell dumpsys activity services com.shihab.diplay` no debe mostrar a `com.xof.ft150ts` conectado al
    servicio del mapa.
12. **Registro:** `adb logcat -s FT150-CarPlay DiPlay-MapEmbed`. DiPlay escribe "com.xof.ft150ts shows the map
    WxH" al pegarlo.

## Volver a la versión anterior

- **Sin reinstalar nada:** toca **OSM** y la página Auto queda exactamente como antes. DiPlay sigue igual.
- **En el teléfono, a la v3.0:** Android no deja bajar de versión encima de una app normal (`adb install -d` solo
  funciona con apps "debuggable"). Para no perder datos:
  1. Ajustes → Respaldo → Guardar (y copia el archivo fuera del teléfono).
  2. Compila los archivos de `../FT150TS-App-v3.0-respaldo-pre-carplay/` con un `versionCode` mayor que el instalado
     (27 si tienes la v3.2) en `app/build.gradle.kts` y Run: queda la v3.0 sin CarPlay, instalada encima, con tus
     datos. Si tienes la v3.2, primero desactiva el ahorro nocturno.
- **En el código:** los archivos de antes de CarPlay están en `../FT150TS-App-v3.0-respaldo-pre-carplay/`. Borra
  `CarPlay.kt`, `ui/MapaCarPlay.kt`, `LogicaCarPlayTest.kt`, `docs/CARPLAY.md`, `scripts/diagnostico-carplay.ps1` y
  `gradle/gradle-daemon-jvm.properties` (o déjalo: no afecta), y copia de vuelta `PantallaAuto.kt`, `Moto.kt`,
  `Respaldo.kt`, `RespaldoTest.kt`, `AndroidManifest.xml` y `app/build.gradle.kts`.

## Cambios después de la primera v3.1 (2026-10-15)

Arreglos sobre la v3.1 original, que nunca se probó en el teléfono. Misma versión: 3.1, code 25.
- **Instrucciones con la opción equivocada:** decían "Dashboard map on the centre screen" (la tarjeta flotante). La
  que le pide el mapa al iPhone es **"CarPlay map on dashboard (experimental)"**. Con la instrucción vieja el mapa
  nunca llegaba.
- **Reintentos que se paraban:** tras el primer reintento, si DiPlay contestaba el mismo error, no se volvía a
  intentar. Ahora sigue intentando, cada vez más espaciado, hasta 30 s.
- **Pedidos duplicados:** un reintento podía mandar un segundo ATTACH con otro en camino. Ahora no.
- **"Actualiza diplay" cuando era el sistema:** si Android no dejaba conectar (MIUI sin "Inicio automático"),
  decía que DiPlay era viejo. Ahora hay un estado aparte, con límite de 8 s y la ayuda para POCO/Xiaomi.
- **Búsqueda de DiPlay en el hilo de la pantalla:** se movió a segundo plano (la firma y la versión salen de
  PackageManager).
- **Versión de DiPlay** visible en el detalle.
- **Compilar desde Android Studio** con su Java 25 (`gradle-daemon-jvm.properties`).
- **Volver a la v3.0:** la v3.1 original decía que `adb install -r -d` bajaba de versión sin borrar datos. No
  funciona con la v3.0 release, que no es debuggable. Se corrigió el procedimiento.

## Licencias

- **DiPlay:** GPL-3.0 (basado en xcertplay; su inicio y ajustes adaptan DiAuto, AGPL-3.0). FT150TS **no incluye ni
  enlaza** código de DiPlay: se comunica con él como programa aparte por una interfaz pública de Android. Las
  constantes del protocolo (nombres de mensajes y claves) salen de su documentación `docs/LAUNCHER_INTEGRATION.md`.
- **Sin dependencias nuevas** en `build.gradle.kts`.
