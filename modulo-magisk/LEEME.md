# Módulo de Magisk: FT150TS · Ventanas flotantes sin barra

Quita la barra de tres puntos (arriba) y la píldora (abajo) de las ventanas flotantes de HyperOS 1 (Android 14), para
que CarPlay ocupe toda la pestaña Auto de FT150TS.

- **Qué cambia:** una superposición (RRO) de SystemUI pone en 1 dp `decor_caption_freeform_height_phone` (26 dp) y
  `bottom_caption_height_phone` (16 dp). **No 0 dp:** con 0, la zona táctil de esas barras se calcula con escala 0 y
  se queda con los toques de la ventana (en v1.1 los botones de DiPlay no respondían). Corregido en v1.2. No toca código ni particiones (Magisk la monta en `/product/overlay`).
- **Seguridad:** se prende después del arranque (`service.sh`). Si SystemUI se reinicia más de 2 veces en el minuto
  siguiente, se apaga sola. Registro: `/data/adb/modules/ft150ts_sin_barra/registro.log`.
- **Efecto secundario:** las ventanas flotantes de cualquier app tampoco tienen barra (no se pueden arrastrar desde
  ahí). Se cierran desde Recientes.
- **Apagarla sin desinstalar:** `su -c cmd overlay disable com.xof.ft150ts.sinbarra` (vuelve al instante). Para que
  no se prenda al arrancar: `su -c touch /data/adb/ft150ts_sinbarra_off`.
- **Quitarla:** Magisk → Módulos → desinstalar y reiniciar.
- FT150TS (v3.4.3+) detecta el módulo y deja de alargar la ventana por debajo de la pantalla.

Compilar: `overlay/` (manifiesto + `res/values/dimens.xml`) → aapt2 compile/link (min 29, target 34) → zipalign →
apksigner (llave debug) → `modulo/system/product/overlay/FT150TSSinBarra/` → zip de `modulo/`.
Probado en POCO F4, HyperOS OS1.0 (V816), Magisk 31.0.

**v1.3:** también pone en 1 dp `freeform_resize_corner` y `freeform_resize_handle` (44 dp). Un deslizamiento dentro de
CarPlay que empezaba cerca de una esquina hacía que HyperOS **escalara** la ventana a 0.25 (se veía chiquita). Si aun
así pasa, FT150TS 3.6.3+ lo detecta y la restablece (CarPlay se reconecta solo).
