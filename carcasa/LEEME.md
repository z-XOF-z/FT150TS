# Carcasa POCO F4 para la FT150TS — prototipo 1

Todo sale de `carcasa_poco_f4.py` (CadQuery, paramétrico). Las medidas están arriba del script; cambias una y vuelves a
correr `.venv/Scripts/python carcasa_poco_f4.py` → regenera `stl/` (impresión) y `stl/*.step` (para editar en
AutoCAD/Fusion). Vistas en `vistas/`, planos en `planos/` (SVG con aristas ocultas).

> **Estado:** diseño de primer prototipo, **no validado**. Ni el ajuste, ni la sujeción, ni la resistencia en la moto
> están probados. No es impermeable ni antirrobo.

## Piezas

| STL | Qué es | Medidas | Material aprox. |
|---|---|---|---|
| `bandeja` | El teléfono acostado; paredes al contorno con costillas de apriete | 183.5 × 81.1 × 11.8 mm | ~75 g |
| `marco` | Bisel que pisa 1.6 mm de la orilla del vidrio; 4 pernos con insertos | 183.5 × 81.1 × 8.5 mm | ~25 g |
| `socket_fijo` | Placa AMPS + mitad fija del socket + trampa de tuerca M5 | 80 × 56 × 41 mm | ~55 g |
| `socket_movil` | Mitad móvil (palanca ×4.1 sobre la bola) | 32 × 30 × 18 mm | ~15 g |
| `perilla` | Perilla moleteada Ø30 para el M5 | Ø30 × 14 mm | ~8 g |
| `tapa` | Tapa removible a presión (sol, polvo; parece pieza de la moto) | 187.6 × 85.2 × 13 mm | ~45 g |
| `calibracion_05/15/25` | Anillos de prueba con holgura 0.05 / 0.15 / 0.25 mm por lado | 168 × 81 × 4.6 mm | ~10 g c/u |

Conjunto armado: **183.5 × 81.1 × 12.2 mm** (sin el socket); el centro de la bola queda 34 mm detrás de la bandeja.

## Tornillería

| Cant. | Pieza | Para qué |
|---|---|---|
| 4 | Tornillo M3 × 8 avellanado (DIN 7991) | bandeja → marco (por atrás, escondidos) |
| 8 | Inserto de calor M3 × 5.7 (Ø ext. 4.0–4.2) | 4 en los pernos del marco + 4 en los extremos de la bandeja (tapa) |
| 4 | Tornillo M4 × 10 avellanado | bandeja → socket fijo (por dentro de la bandeja, al ras bajo el teléfono) |
| 4 | Inserto de calor M4 × 8 (Ø ext. 5.6–5.8) | en la placa del socket fijo |
| 1 | Tornillo M5 × 40 cabeza hexagonal + **tuerca M5 de seguridad con nylon (nyloc)** | apriete del socket (la cabeza va en la perilla); la nyloc no se afloja con la vibración |
| 2 | Tornillo M3 × 50 + **tuerca M3 nyloc** | bisagra de la mordaza móvil (abajo, a los lados del cuello): con el M5 forman 3 puntos, la mordaza no gira ni se desliza |
| 4 | Tornillo M3 × 8 cabeza botón + 4 insertos M3 | tapa → bandeja (2 por extremo; los insertos van horizontales en los bloques de las puntas) |
| opc. | M5 y M3 **Torx de seguridad (con pin)** en lugar de los normales | anti-robo: se necesita una punta especial para quitarlos |

Alternativa a imprimir el socket: el patrón AMPS (30 × 38 mm) de la bandeja acepta cualquier base RAM con bola
(p. ej. RAM-B-347U) + un brazo de doble socket RAM del tamaño de tu bola.

## La bola que ya tienes

Por la foto es una base de manubrio con U-bolt tipo RAM (como RAM-B-231U / RAM-231U). Los tamaños estándar
([RAM](https://www.ram-mount.co.uk/products/ram-231-1u)): **B = 25.4 mm (1")**, C = 38.1 mm (1.5"). El script viene en
**25.4 mm**. **Medida de Andrew: 24.5 mm** → `bola_d = 24.5`. Puede ser un clon de 25 mm o una B de 1" medida con
la goma algo aplastada: el socket aguanta ±1 mm (las mitades tienen 2 mm de recorrido). Si el cuello mide más de 13 mm,
sube `cuello_d`.

## Medidas de cámara y botones
Sacadas de la vista trasera y frontal oficial (GSMArena), con el largo de 163.2 mm como escala (0.267 mm/px; el ancho
de 76 mm da la misma escala): isla de cámara 28.3 × 51.0 mm a 5.9 mm del borde superior y del lateral; volumen desde
~39 mm del borde superior, encendido hasta ~79 mm. En el script van con margen. Error esperado de una foto: ±0.5 mm:
compruébalo en el prototipo.

## Tolerancias (por qué no 0.05 mm directo)

- El objetivo de 0.05 mm es **por lado** respecto al teléfono, pero una impresora FDM normal tiene ±0.1–0.2 mm de error
  en X/Y. Con 0.05 directo lo más probable es que el teléfono no entre o quede forzado.
- Por eso: holgura nominal `holgura_xy` = 0.15 mm por lado **+ costillas de apriete** de 0.2 mm (3 por lado largo) que se
  aplastan al meter el teléfono → sin juego aunque la holgura real salga mayor.
- El grosor (Z) lleva 0.10 mm de juego para que el marco **no presione la pantalla**.
- Largo, ancho, grosor, esquinas y costillas son parámetros independientes.

### Calibración (antes de la carcasa completa)
1. Imprime los 3 anillos (`calibracion_05`, `_15`, `_25`) en el mismo material y con el mismo perfil que la carcasa.
2. Mete el teléfono en cada uno: el bueno es el que entra con presión suave, sin forzar, y no se mueve al sacudirlo.
3. Pon ese valor en `holgura_xy` y regenera. Si ninguno queda: ajusta en pasos de 0.05 o cambia `costilla_alto`.
4. Mide con vernier tu teléfono (largo, ancho, grosor, radio de esquina), la cámara y los botones, y corrige los
   parámetros marcados **MEDIR** en el script.

Si se necesitara más precisión que la del FDM: resina (SLA, ±0.05 mm) o imprimir 0.2 mm más chico y lijar a la medida.

## Material: PETG vs ASA

| | PETG | ASA |
|---|---|---|
| Sol (UV) | Se pone amarillento y quebradizo con meses de sol | **Resiste UV** (hecho para exteriores) |
| Temperatura | Se ablanda ~75–80 °C | Se ablanda ~95–100 °C |
| Vibración | Más tenaz (menos frágil) | Buena |
| Impresión | Fácil, sin encierro | Necesita encierro y ventilación (olor, se tuerce) |
| Para la moto | Bien para probar | **Recomendado para la versión final** (estacionada al sol en Nayarit) |

## Tu impresora: Creality Ender-3 V3 SE
- Cama de 220 × 220 mm: la pieza más grande (bandeja, 183.5 × 81.1) cabe sin problema.
- Es abierta (sin encierro): **PETG** es lo que mejor le va. ASA se tuerce sin encierro; si quieres ASA para la final,
  hace falta una caja/encierro (un cartón grueso o una carpa de impresora sirve) y ventilar por el olor.
- Calibra primero el flujo y los e-steps (y el "Flow rate" del PETG) antes de los anillos: de eso depende la holgura.

## Cómo imprimir

- **Bandeja:** ASA/PETG, capa 0.2, 4 paredes, 30 % gyroid, piso en la cama, sin soportes (las ventanas laterales son
  ranuras pequeñas).
- **Marco:** cara frontal en la cama (ya viene girado), 4 paredes, sin soportes.
- **Socket fijo:** placa en la cama, 5 paredes, 50 % infill; soportes **solo** dentro del hueco de la bola.
- **Socket móvil:** cara de la abertura en la cama (ya viene girado), 5 paredes, 50 %.
- **Perilla:** plana en la cama, 3 paredes, 30 %.

## Armado

1. Inserta en caliente los 4 insertos M3 en los pernos del marco y los 4 M4 en la placa del socket fijo.
2. Atornilla el socket fijo a la bandeja con los M4 avellanados **desde adentro** (las cabezas quedan al ras del piso).
3. Pon el teléfono en la bandeja (USB-C hacia el lado del hueco grande), coloca el marco y atornilla los 4 M3 por atrás.
4. Mete la tuerca M5 en su trampa, junta las dos mitades del socket alrededor de la bola de la moto, pasa el M5 por la
   perilla y la mitad móvil, y aprieta.

## Escaneo 3D de la moto (2026-10-22)

`referencias/escaneo-22-10/22_10_2026.glb` (fotogrametría con textura, en metros). Lo que se sacó de ahí:
- **Bola:** ajuste de esfera con 529 puntos → **25.5 mm** (vernier: 24.5). Estándar RAM B = 25.4 → `bola_d = 25.4`.
- **Dirección del brazo de la bola:** casi vertical (2.4°). Con el socket recto la pantalla habría quedado mirando al
  cielo → el socket ahora va **inclinado 60°** con una cuña en la placa (`socket_inclinacion`); con los ±20° de la bola
  cubre 40–80° y la pantalla queda hacia el piloto a ~32° sobre la horizontal.
- **La bola está ~50 mm a la izquierda del centro de la moto** → el socket se corre en la bandeja
  (`socket_desplazamiento_x = -50`) y el teléfono queda centrado (a ~2.5 mm del centro).
- **Choques con la moto (colocando el ensamble sobre la bola escaneada):** bandeja, marco y tapa: 0. El socket solo
  toca los cables sueltos que pasan sobre la bola → acomodarlos con cinchos.
- La pantalla tapa el tablero original (velocímetro de aguja y LCD): la app muestra la velocidad.
- Vista: `vistas/escaneo_con_carcasa.png`.
- Impresión del socket fijo: placa en la cama; el cuerpo inclinado necesita soportes (árbol) debajo.

## Revisión estructural (2026-10-22)

Revisado con cálculos de interferencia en CadQuery (todo en 0 mm³ salvo lo intencional) y una cuenta de viga:

| Revisión | Resultado |
|---|---|
| Marco ↔ bandeja, tapa ↔ bandeja/marco, mitades del socket | No chocan (0 mm³) |
| Marco ↔ teléfono | **No toca la pantalla** (0 mm³; 0.10 mm de juego en Z) |
| Bandeja ↔ teléfono | Solo las costillas de apriete (15 mm³ = el apriete buscado) |
| Bola 24.5 mm ↔ socket | Entra (0 mm³) |
| Rigidez a 5 g de vibración (PETG, voladizo de 92 mm) | Flecha ≈ 0.17 mm en la punta |

Cambios por la revisión:
- **Piso 2.4 → 4.0 mm**: con 2.4 quedaban 0.4 mm bajo las cabezas M4 avellanadas (se iba a romper ahí). Ahora 2 mm, y el
  piso es ~4.6× más rígido.
- **Machihembrado en los lados largos**: la lengüeta del marco entra en una ranura de la bandeja → el marco no se puede
  levantar a media pantalla (antes solo lo sostenían las 4 puntas en 163 mm).
- **Placa del socket 80 × 56 mm** (antes 52 × 44): reparte el esfuerzo del voladizo.
- **Tuerca nyloc** en el socket.

### Que no salga volando / no se lo lleven
- El teléfono solo puede salir por arriba y ahí lo detiene el marco (pisa 1.6 mm del vidrio en todo el contorno, trabado
  con machihembrado y 4 tornillos). Para sacarlo hay que quitar la unidad de la bola y destornillar por atrás.
- Anti-robo realista: ningún plástico impreso aguanta un robo con herramienta. Lo que sí hace: tornillos escondidos
  por atrás + (opcional) **Torx de seguridad** en el M5 y los M3 → no se puede quitar con una llave cualquiera ni a
  mano. Con la tapa puesta no se nota que hay un teléfono. Lo más seguro sigue siendo llevarte el teléfono al estacionar.

### Tapa
- A presión con un "clic" en los lados largos; se quita con el dedo (muesca en un extremo) y tiene muesca para el
  cable USB-C. 3 mm de aire sobre la pantalla (no la toca).
- Imprímela en **negro** para que parezca pieza de la moto (techo en la cama: nervaduras y FT150TS grabados salen
  limpios). Ojo: negro al sol se calienta; con la tapa puesta y la moto estacionada no dejes el teléfono cargando.

## Validación pendiente

- [ ] Anillos de calibración: elegir holgura
- [ ] Medidas reales del teléfono, cámara y botones (parámetros MEDIR)
- [ ] Diámetro real de la bola
- [ ] Prototipo: el teléfono entra sin forzar, no se mueve, botones y USB-C accesibles, cámara libre
- [ ] Marco: no toca la zona táctil útil de la pantalla
- [ ] Socket: no se gira con la mano al estar apretado; prueba de vibración en la moto a poca velocidad
- [ ] Temperatura: con el teléfono cargando al sol, que no se caliente de más (ventilación del piso)
