# Ahorro nocturno (v3.2, mejorado en v3.3)

**Nuevo en v3.3:**
- **Gasto de batería por noche:** guarda el % al entrar y al salir y calcula el gasto en **%/h**. Muestra las últimas
  7 noches y el promedio, para comparar perfiles con datos reales. Si estuvo cargando se marca "cargando" y no cuenta
  como gasto; si duró menos de 20 min, "muy corta".
- **Probar ahora 15 min:** una noche de prueba desde ya, aunque el ahorro esté apagado, con las acciones elegidas y
  sin posponer (salvo que vayas rodando). Al terminar se deshace igual que una noche normal. Hay botón para
  terminarla antes. Probado en el emulador: entró, marcó con ✓ "Dormir FT150TS" y con ✗ "requiere root" el ahorro y
  el Doze (el emulador no tiene root), y anotó que estaba cargando.

**Ajustes → Ahorro nocturno.** En un horario (de fábrica 00:00–06:30, todos los días, con la hora de Android) el
teléfono gasta lo mínimo. Al terminar, todo regresa exactamente como estaba. **FT150TS no apaga ni reinicia el
teléfono.** Abajo se explica por qué y cómo comprobar si tu POCO F4 podría encenderse solo.

## Estados del teléfono (no son lo mismo)

| Estado | Qué es | ¿Lo hace FT150TS? |
|---|---|---|
| Pantalla apagada | Android sigue corriendo | Ya lo hace Android |
| Suspensión | El procesador duerme entre eventos | Android, solo |
| Ahorro profundo (Doze) | Casi sin red ni trabajos en segundo plano; las alarmas del reloj y las llamadas sí llegan | **Sí**: perfiles de abajo |
| Reiniciar | Se apaga y vuelve a prender | **No** |
| Apagar | Teléfono apagado | **No** |
| Encenderse solo estando apagado | Una alarma del RTC del PMIC lo prende | **No comprobado** en el POCO F4 (ver abajo) |

## Perfiles y acciones

Cada acción se puede prender o apagar una por una. Las de **root** necesitan Ajustes → Root (Magisk) → "Pedir
acceso root". Sin root no se aplican y se marcan con ✗: **nunca se muestra como aplicado algo que no se aplicó**.

| Acción | Conservador | Extremo | Root | Cómo se deshace |
|---|:-:|:-:|:-:|---|
| Dormir FT150TS (GPS, sensores, dashcam, búsqueda de la tarjeta; sin revisiones de ubicación) | ✓ | ✓ | No | Se despierta al terminar, al tocar la pantalla, al conectar la moto o si el teléfono se mueve |
| Pausar sincronización automática de cuentas | | ✓ | No | Se regresa como estaba |
| Ahorro de batería de Android (`cmd power set-mode 1`) | ✓ | ✓ | Sí | `set-mode 0`, solo si estaba apagado antes |
| Doze profundo con pantalla apagada (`dumpsys deviceidle force-idle`) | ✓ | ✓ | Sí | `unforce`. Se quita al prender la pantalla y vuelve 5 min después de apagarla |
| Restringir apps elegidas (`am set-standby-bucket <app> restricted`) | | ✓ | Sí | Cada app regresa al grupo que tenía (se guarda antes) |
| Cerrar apps elegidas (`am kill`) | | ✓ | Sí | No hay nada que deshacer: no borra datos ni quita alarmas, la app vuelve a abrir normal |
| Cerrar DiPlay si no hay CarPlay (`am kill`) | | ✓ | Sí | Solo si su pantalla de CarPlay no está abierta. Su receptor de Bluetooth sigue funcionando |
| Apagar datos móviles (`svc data`) | | a mano | Sí | Se prenden si estaban prendidos. **Corta WhatsApp y correo** (llamadas y SMS sí llegan) |
| Apagar Wi-Fi (`svc wifi`) | | a mano | Sí | Igual. **CarPlay inalámbrico usa Wi-Fi** |

**Nunca se tocan:** FT150TS, DiPlay, teléfono, SMS, reloj y alarmas, el launcher que usas, Ajustes, Magisk, Google
Play Services, apps del sistema, ni las que marques como protegidas. No se desactivan telefonía ni alarmas.

## Cuándo entra, cuándo no

Al llegar la hora revisa y **pospone 15 min** (sin pasarse del fin) si:
- vas rodando o la moto está conectada;
- hubo CarPlay en vivo en la página Auto en los últimos 5 min ("Respetar CarPlay", prendido de fábrica);
- la pantalla está prendida o tocaste FT150TS hace menos de 10 min (configurable: no / 10 / 30 min);
- marcaste **Proteger esta noche**, en Ajustes o en el detalle de CarPlay ("Proteger esta sesión"). Si ya había
  entrado, se deshace.

## Cómo se programa y cómo se recupera

- **Alarma de Android** (`setExactAndAllowWhileIdle`) para el siguiente cambio: entrar, salir o reintentar. Sin
  permiso de alarmas exactas usa una inexacta (puede llegar minutos tarde). El aviso sale en Ajustes, con el botón
  para permitirlas.
- **Se recalcula desde cero** con la hora actual al arrancar el teléfono (`BOOT_COMPLETED`), al actualizar la app,
  al cambiar la hora o la zona, al abrir la app y con cada alarma. Revisar dos veces no duplica nada: si ya estaba
  aplicado, solo hace lo ligero.
- **Registro de cambios** (`nocturno_registro`): cada cambio guarda su valor anterior. Al terminar, al apagar la
  opción o con **Desactivar ya y deshacer todo**, se regresa exactamente eso. Si algo no se pudo deshacer (p. ej.
  Magisk negó root), queda pendiente y **se reintenta cada 10 min**, también al abrir la app o reiniciar.
- **No se queda trabado:** fuera del horario, cualquier revisión deshace lo pendiente. El Doze forzado se borra solo
  al reiniciar. Cambiar el horario a uno que ya terminó también deshace.
- **Sin ciclos:** ninguna acción reinicia ni apaga, así que no puede haber ciclos de apagado ni de reinicio.
- El registro **no se restaura** desde un respaldo (probado). El horario y las opciones sí.
- **Doze forzado necesita "Mantener activa en segundo plano"** (Modo moto). Sin el servicio, FT150TS no se enteraría
  de que prendiste la pantalla para quitarlo, así que no lo aplica.

## Encenderse solo desde apagado: viabilidad

**No está comprobado en tu POCO F4, y FT150TS no lo implementa.** Lo que se sabe:

- En los kernels de Qualcomm (`qpnp-rtc`), la alarma del RTC solo prende el teléfono si el device tree trae
  `qcom,qpnp-rtc-alarm-pwrup = 1` o si el parámetro `poweron_alarm` está activo. Si no, **el kernel apaga la alarma al
  apagar el teléfono**.
- Además, el bootloader tiene que aceptar el arranque por alarma. Algunos arrancan en un modo especial de "alarma"
  en vez de en Android.
- No se encontró documentación de que HyperOS ofrezca "encendido y apagado programado" en el POCO F4.
- Modificar el kernel o el arranque para forzarlo está fuera de alcance: es riesgoso y difícil de revertir.

**Cómo saberlo:** FT150TS → Ajustes → Ahorro nocturno → **Diagnóstico** (solo lectura). El veredicto puede ser:
- **Del sistema:** HyperOS trae encendido programado. Úsalo desde sus ajustes: es lo que soporta Xiaomi.
- **Probar:** el kernel deja la alarma activa. Haz la prueba manual de abajo.
- **No:** el kernel la apaga. No hay encendido programado posible sin modificar el kernel. Usa el perfil extremo.
- **Sin datos:** falta root, o el driver es otro. No se adivina.

**Prueba manual (solo si el veredicto es "Probar"; tú decides hacerla):** con el teléfono cargado, conectado a la PC y
con la depuración USB activada:
```bash
adb shell su -c 'echo 0 > /sys/class/rtc/rtc0/wakealarm; echo +300 > /sys/class/rtc/rtc0/wakealarm; cat /proc/driver/rtc'
adb shell su -c 'reboot -p'      # apaga el teléfono
```
Espera 6 minutos sin tocarlo:
- **Si se prende solo**, el hardware y el firmware lo admiten. Dime y se diseña el apagado programado, con
  cancelación y límites.
- **Si no se prende**, mantén el botón de encendido como siempre: no pasa nada malo. Significa que no es viable.

FT150TS no hace esta prueba sola, para no dejar el teléfono apagado esperando algo que quizá nunca pase.

## FT150TS y DiPlay juntos

| Mecanismo | ¿Qué hace? | Cómo |
|---|---|---|
| Servicio en primer plano de FT150TS | La app no se cierra en segundo plano | Ya existía (Modo moto → "Mantener activa en segundo plano") |
| Ahorro de batería "Sin restricciones" + lista blanca de Doze para FT150TS | Android/MIUI no la congela | Root → "Dar permisos y quitar ahorro de batería" (ya existía) |
| Lo mismo para DiPlay | DiPlay no se congela en segundo plano | Ahorro nocturno → "Quitar restricciones de batería a DiPlay (root)". Guarda cómo estaba; "Regresar DiPlay como estaba" lo deshace |
| Inicio automático de MIUI | MIUI deja que las apps se despierten entre ellas y al arrancar | Botón "Inicio automático (MIUI)". Es un ajuste de Xiaomi: se prende a mano para las dos |
| Reconexión del mapa tras apagar/prender la pantalla | FT150TS vuelve a pedir el mapa solo | v3.1 (`docs/CARPLAY.md`) |

**Lo que no se hizo, a propósito:** no se tocan `oom_score_adj`, el límite de procesos, `lmkd`, las políticas
térmicas ni los gobernadores de CPU. No hay evidencia de que hagan falta y cambiarlos afecta a todo el teléfono.
Mantener un proceso vivo **no** garantiza que DiPlay siga mostrando video o reproduciendo audio: la sesión de CarPlay
depende de DiPlay, del iPhone y de la conexión.

## Permisos nuevos (v3.2)

| Permiso | Para qué |
|---|---|
| `SCHEDULE_EXACT_ALARM` | Entrar y salir a la hora. En Android 14+ se da a mano (Ajustes avisa) |
| `RECEIVE_BOOT_COMPLETED` | Recalcular el horario después de reiniciar (y deshacer lo que haya quedado) |
| `READ_SYNC_SETTINGS`, `WRITE_SYNC_SETTINGS` | Pausar y regresar la sincronización automática |

El receptor `NocturnoReceiver` **no está exportado**. Ninguna otra app puede activarlo ni pedirle comandos.

## Seguridad de las operaciones root

- **Lista cerrada:** los comandos salen de `LogicaNoche` (ahorro, Doze, grupos de espera, `am kill`, `svc data/wifi` y
  lecturas). Los paquetes se validan con una expresión estricta (sin espacios, `;`, comillas ni `$`). Los grupos solo
  pueden ser `active|working_set|frequent|rare|restricted`. Hay pruebas que intentan inyectar comandos.
- **Nada de** apagar, reiniciar, `setenforce`, escribir en particiones, tocar el bootloader o el kernel.
- **Una sola sesión de `su` por paso**, con el código de salida de cada comando revisado. Magisk solo pregunta la
  primera vez. Si te molesta su aviso en cada uso: Magisk → Superusuario → FT150TS → notificaciones apagadas.
- Cada cambio se registra con su valor anterior y se deshace con el comando inverso.

## Desactivar y revertir

1. **En la app:** Ajustes → Ahorro nocturno → **Desactivar ya y deshacer todo**.
2. **Sin la app (por ADB)**, si algo quedó raro:
   ```bash
   adb shell su -c 'dumpsys deviceidle unforce'
   adb shell su -c 'cmd power set-mode 0'
   adb shell su -c 'svc data enable'; adb shell su -c 'svc wifi enable'   # solo si los usas
   adb shell su -c 'am set-standby-bucket <paquete> active'                # para una app restringida
   ```
   Un reinicio normal también quita el Doze forzado.
3. **Volver a la v3.1 sin perder datos:** Android no deja bajar de versión encima. Haz un respaldo (Ajustes →
   Respaldo) y compila los archivos de `../FT150TS-App-v3.1-respaldo-pre-nocturno/` con `versionCode = 27`. Antes,
   desactiva el ahorro nocturno para que no quede nada aplicado.

## Pruebas

| Prueba | Resultado |
|---|---|
| Unitarias: `gradlew :app:testDebugUnitTest` | 40/40 (`NocturnoTest` 14: horario, medianoche, días, horario de verano, posponer, comandos, inyección, apps protegidas, registro dañado, veredicto RTC; `RespaldoTest` +1) |
| `lintDebug` | 0 errores; sin advertencias en el código nuevo |
| `assembleDebug` / `assembleRelease` con el Java 25 de Android Studio | OK |
| Permisos vs v3.1 | Solo los 4 de arriba |
| Componentes exportados vs v3.1 | Igual (3) |
| Paquete y firma | `com.xof.ft150ts`, `3efd04bd…ab65` (igual) |
| Scripts `diagnostico-energia.ps1` y `diagnostico-carplay.ps1` | Sintaxis OK; sin teléfono, avisan y salen |
| **En el POCO F4** | **Pendiente**: no hubo teléfono conectado |

Lista para el POCO F4:
1. `scripts\diagnostico-energia.ps1` y en la app **Diagnóstico**: anota Android/HyperOS, root, SELinux y el veredicto RTC.
2. Pon un horario de prueba que empiece en 3 min y dure 15. Apaga la pantalla. Al empezar, "Acciones de la última
   noche" debe mostrar ✓/✗ reales. `adb shell dumpsys deviceidle | findstr mForceIdle` → `true`.
3. Prende la pantalla → `mForceIdle=false` en segundos. Apágala → a los 5 min, `true` otra vez.
4. Al terminar el horario: todo con ↺, `settings get global low_power` como estaba, las apps restringidas en su grupo
   de antes (`am get-standby-bucket <app>`).
5. Reinicia el teléfono en medio del horario → al arrancar vuelve a entrar. Reinícialo después del fin → deshace.
6. Con CarPlay en vivo en la página Auto al empezar el horario → "Pospuesto 15 min: hay una sesión de CarPlay en uso".
7. Cambia la zona horaria a mano → el horario se recalcula.
8. **Desactivar ya** a media noche → todo ↺ al instante.
9. Una noche completa con el perfil elegido: compara el % de batería gastado con una noche sin ahorro.
