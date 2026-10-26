# Hardware extra: corte de chispa (limitador, launch control y quickshifter)

El firmware v1.5 ya trae el limitador de RPM, el launch control y el quickshifter, **apagados**.
Para que funcionen hace falta el hardware de esta hoja; sin él, la tarjeta no deja prenderlos.
El diagnóstico de motor y batería y la telemetría de la dashcam **no** necesitan nada extra.

## Cómo corta la chispa (placa base v2.1, piezas de tienda)

El CDI de AC de la FT150 deja de dar chispa cuando su **cable de paro** se pone a tierra, que es lo mismo que hace el interruptor de paro del manubrio. En la placa eso lo hace un MOSFET de alto voltaje, en el bloque Q9 / U15 / D8 / DZ1 / R28 / R29 / R31:

```
Cable de paro (J18) ──►|── D8 1N4007 ── Drenaje   Q9 IRF840 (500 V)   Fuente ── GND (chasis)
                                          Compuerta
GPIO47 ── R28 470 ── LED de U15 (PC817)      │
                     U15: colector a +12V, emisor ┴── R31 10k a GND  +  DZ1 zener 15 V
```

- **D8 solo deja pasar la mitad positiva del pulso**, que es la que carga el capacitor del CDI. Sin esa mitad no hay chispa; la mitad negativa no se toca.
- **Falla abierto:** sin señal (ESP32 apagado, reiniciado o colgado), R31 deja la compuerta en 0 V, el MOSFET no conduce y la moto sigue con chispa normal.
- **Simulación** (`pcb/simulacion/sim_corte.py`, con un modelo simple de CDI):

  | | Capacitor del CDI |
  |---|---|
  | Sin corte | 270 V (hay chispa) |
  | Con corte | 1 V (sin chispa) |

  Al soltar el corte, la chispa vuelve de inmediato.
- **Piezas, todas de tienda:** IRF840 (o FQP8N60), 1N4007, PC817, zener 1N4744 de 15 V y resistencias de 470 y 10k.

No uses SSR comunes en su lugar:
- Los **Fotek "DA" o "AA"** tienen triac con cruce por cero y no responden a pulsos.
- Los **"DD"** solo aguantan ~220 V y su diodo interno deja pasar los pulsos negativos.

### Antes de conectar: confirma el cable con el multímetro

1. Moto apagada y llave quitada. Busca el cable que va del interruptor de paro o de la llave al CDI.
2. **Continuidad:** con el interruptor de paro en "OFF" ese cable debe dar continuidad a tierra; en
   "RUN", no.
3. **Con el motor prendido:** mide **DC**. Debe dar ~0 V DC (en AC verás voltaje, es normal).
   - Si mide **+12 V DC**, tu CDI es de DC y ese cable es la **alimentación**, no un cable de paro.
     **No lo pongas a tierra** (quemarías el fusible o el CDI). En ese caso no instales esto y avísame.
4. Conecta la bornera **CDI PARO** de la placa: "PARO" al cable de paro y "CHASIS" a la tierra de la moto.
   No cortes el interruptor original; la placa queda en paralelo con él.

## Sensor del quickshifter

- **Sensor de palanca para quickshifter** (de presión o microswitch), normalmente abierto, que mande
  a tierra al pisar.
- En la FT150 el patrón es 1-N-2-3-4-5: para subir se **levanta** la palanca, así que el sensor
  tiene que detectar el jalón hacia arriba (o un microswitch con soporte que lo toque al subir).
- Conexión: un cable a **GPIO48**, el otro a GND, y un capacitor de 100 nF entre los dos (antirrebote).
  El ESP32 pone su pull-up interno.
- Si tu DevKitC-1 trae el LED RGB en el 48 (las v1.0), usa el **GPIO38** y cambia `PIN_QS_SENSOR`.

## Activarlo en el firmware

En `ft150ts_dashboard.ino`:

```cpp
#define USAR_CORTE_CHISPA 1
const int8_t  PIN_CORTE_CHISPA  = 47;
const int8_t  PIN_QS_SENSOR     = 48;   // o 38, o -1 si no pones quickshifter
```

Compila y sube. Luego, en la app: **Ajustes > Funciones avanzadas**. Para prenderlas, la moto tiene
que estar parada, el teléfono vinculado y hay que confirmar en un aviso.

## Reglas fijas del firmware (la app no las puede cambiar)

| Regla | Valor |
|---|---|
| No corta en modo demo, con la llave apagada ni con el motor apagado | siempre |
| Un corte seguido dura como máximo | 120 ms |
| Chispa obligada entre dos cortes | 40 ms |
| Golpe del limitador | 20 ms |
| Tope del limitador | 6 000–11 000 rpm (9 500 de fábrica) |
| Launch control: se arma parado y fuera de neutral, se suelta al pasar | 15 km/h |
| Rango del launch control | 3 000–8 000 rpm |
| Quickshifter: corte / rpm mínimas | 30–100 ms / 3 000–9 000 rpm (60 ms y 4 500 de fábrica) |
| Quickshifter: solo rodando arriba de 15 km/h, de 1a a 4a, y entre cambios | 400 ms |

Mientras la chispa está cortada no llegan pulsos de RPM, así que el limitador espera dos chispas
nuevas antes de volver a medir.

## Primera prueba

1. Moto en el caballete, en neutral: prende el **limitador a 6 000** y acelera poco a poco. Debe
   "tartamudear" al llegar a 6 000. Apágalo.
2. Rodando en un lugar vacío: **quickshifter a 60 ms**. Sube de 2a a 3a arriba de 5 000 sin clutch,
   con el acelerador fijo. Si entra duro, súbelo a 70–80 ms; si se siente un jalón largo, bájalo.
3. **Launch control:** úsalo solo en una recta vacía.
