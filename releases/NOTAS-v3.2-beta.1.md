## FT150TS Dashboard v3.2-beta.1 (versión de prueba)

### Lo nuevo
- **Ahorro nocturno** (Ajustes → Ahorro nocturno): en tu horario (de fábrica 00:00–06:30, los días que elijas) el
  teléfono gasta lo mínimo y al terminar todo regresa como estaba.
  - Perfil **conservador**: duerme FT150TS, ahorro de batería de Android y Doze profundo con la pantalla apagada.
  - Perfil **extremo**: además, pausa la sincronización, restringe y cierra las apps que elijas, y cierra DiPlay si
    no hay CarPlay. Apagar datos o Wi-Fi es opcional y se prende a mano.
  - No entra si vas rodando, si hay CarPlay en uso o si estás usando el teléfono: lo pospone 15 min. "Proteger
    esta noche" lo salta.
  - Muestra qué se aplicó de verdad (✓/✗) y trae **Desactivar ya y deshacer todo**.
  - **Diagnóstico** de solo lectura: root, SELinux, kernel y si el teléfono podría encenderse solo.
- **FT150TS y DiPlay juntos:** quitar restricciones de batería a DiPlay con root (reversible) y acceso directo a
  "Inicio automático" de MIUI.
- **Actualizarse desde la app** (Ajustes → Acerca de): busca la versión nueva en GitHub, la descarga y la instala
  encima sin borrar tus datos, o instala un APK que elijas. Solo acepta un APK de FT150TS con la misma firma y más
  nuevo, y guarda un respaldo antes. Android siempre pide confirmar.

### Instalar
- **Desde la v3.0 o la v3.1:** descarga `FT150TS-v3.2-beta.1.apk`, ábrelo en el teléfono y elige "Actualizar". También
  sirve `adb install -r`. Tus datos se quedan.
- **De aquí en adelante:** Ajustes → Acerca de → prende "Versiones de prueba (beta)" (ya viene prendido en esta beta) → Buscar actualización.

### Importante
- **No apaga ni reinicia el teléfono.** Encenderse solo desde apagado no está comprobado en el POCO F4. El diagnóstico
  dice si vale la pena probarlo (`docs/AHORRO_NOCTURNO.md`).
- Las acciones de root necesitan Magisk. Sin root solo se duerme la app y se pausa la sincronización.
- Permisos nuevos: alarmas exactas, arranque del teléfono, ajustes de sincronización e instalar actualizaciones.

### Estado
- Compilado; 47 pruebas automáticas pasan. **Sin probar todavía en el POCO F4.**

> Firmado con la misma llave de desarrollo que la v3.0 y v3.1: se instala encima sin borrar tus datos.
