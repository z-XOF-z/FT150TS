"""Carcasa del llavero TamaXOF (estilo "Keychain Buddy": caja blanca, detalles naranjas).

    ..\\..\\carcasa\\.venv\\Scripts\\python.exe -I carcasa_tamaxof.py

Piezas (STL + STEP en stl/):
  frente      blanco  caja con ventana de la pantalla, 3 botones al frente y aro arriba (imprimir con la cara al frente en la cama)
  tapa        blanco  tapa trasera a presión
  botones     naranja 3 botones (imprimir parados, cabeza abajo)
  orejas      naranja 2 orejas de adorno (se pegan en los costados)
  bolita      naranja va en el aro del llavero
Revisa solo que la pantalla, la placa, la ESP32-C3 SuperMini, el TP4056, el switch y la pila quepan sin chocar.
Medidas de la placa salen de ../pcb/generar_placa.py (33 x 43 mm). Todo en mm.
"""
import sys
from pathlib import Path

import cadquery as cq

AQUI = Path(__file__).resolve().parent
SALIDA = AQUI / "stl"

# ---------------- Piezas reales (MEDIR al llegar y ajustar aquí) ----------------
PCB_W, PCB_H, PCB_T = 35.0, 40.5, 1.6
PCB_R = 3.0                                      # esquinas redondas de la placa
OLED_W, OLED_H, OLED_T = 27.3, 27.8, 2.9        # módulo 0.96": placa 1.2 + vidrio 1.7
OLED_Y0 = 1.6                                    # borde de arriba del módulo sobre la placa
OLED_SEP = 2.5                                   # separador negro del header (se deja puesto)
OLED_CX = 17.63                                  # centro de la pantalla en X sobre la placa
ACTIVA = (23.0, 12.5, 13.4)                      # ventana: ancho, alto, centro en Y de la placa
MODULO = (12.08, 11.1, 34.6, 29.14, 4.0)         # ESP32-C3 SuperMini: x0, y0, x1, y1, alto sobre la placa (con USB)
USB = (20.12, 9.6, 3.8)                          # USB-C de la SuperMini (pared derecha): centro Y, ancho, alto
TP4056 = (28.0, 17.2, 4.4)                       # cargador TP4056 USB-C con protección (largo, ancho, alto con USB)
SWITCH = (8.6, 3.6, 3.6, 4.0)                    # switch SS12D00: largo, ancho, alto, palanca
SW_ALTO = 4.3                                    # botón 6x6x4.3 (alto total con el émbolo)
BOTONES_X, BOTON_Y = (8.5, 17.5, 26.5), 35.75
BATERIA = (35.7, 57.7, 5.0)                      # pila de control de PS4, 570 mAh (ancho X, alto Y, grueso)
# Con una LiPo 602030 (30.5, 20.5, 6.0) la caja baja a 37 x 47 mm
SW18010 = (11.5, 4.8)                            # sensor de vibración acostado (largo, diámetro)

# ---------------- Caja ----------------
HOLGURA = 0.3
PARED = 1.8
FRENTE = 1.2
TAPA = 1.2
LABIO = 2.2            # cuánto entra la tapa
RADIO = 5.0
RADIO_IN = 0.8         # esquinas por dentro (la pila es casi cuadrada)

IN_W, IN_H = max(PCB_W, BATERIA[0]) + 2 * HOLGURA, max(PCB_H, BATERIA[1]) + 2 * HOLGURA
EXT_W, EXT_H = IN_W + 2 * PARED, IN_H + 2 * PARED

# Profundidad (z = 0 cara del frente, crece hacia atrás)
Z_OLED = FRENTE                                  # el vidrio toca el frente por dentro
Z_PCB = Z_OLED + OLED_T + OLED_SEP               # cara de enfrente de la placa
Z_PCB_ATRAS = Z_PCB + PCB_T
Z_BAT = Z_PCB_ATRAS + MODULO[4] + 0.3
Z_TAPA = Z_BAT + BATERIA[2] + 0.4 + LABIO        # cara interior de la tapa (el labio queda detrás de la pila)
PROF = Z_TAPA + TAPA


def a_caja(x, y):
    """De coordenadas de la placa (origen arriba-izq) a la caja (origen al centro, Y hacia arriba)."""
    return x - PCB_W / 2, IN_H / 2 - HOLGURA - y      # la placa va centrada y pegada arriba


def tp4056_y():
    """Centro en Y (caja) del TP4056: abajo de la placa."""
    _, y_fin = a_caja(0, PCB_H)
    return (y_fin + (-IN_H / 2)) / 2


def caja_redonda(w, h, prof, r):
    return cq.Workplane("XY").rect(w, h).extrude(prof).edges("|Z").fillet(r)


def frente():
    c = caja_redonda(EXT_W, EXT_H, PROF, RADIO)
    c = c.faces("<Z").edges().fillet(1.2)
    hueco = caja_redonda(IN_W, IN_H, PROF, RADIO_IN).translate((0, 0, FRENTE))
    c = c.cut(hueco)
    # ventana de la pantalla con chaflán hacia afuera
    vx, vy = a_caja(OLED_CX, ACTIVA[2])
    ventana = (cq.Workplane("XY").rect(ACTIVA[0], ACTIVA[1]).workplane(offset=FRENTE)
               .rect(ACTIVA[0], ACTIVA[1]).loft().translate((vx, vy, 0)))
    bisel = (cq.Workplane("XY").rect(ACTIVA[0] + 2.0, ACTIVA[1] + 2.0).workplane(offset=0.6)
             .rect(ACTIVA[0], ACTIVA[1]).loft().translate((vx, vy, 0)))
    c = c.cut(ventana).cut(bisel)
    # 3 botones
    for bx in BOTONES_X:
        x, y = a_caja(bx, BOTON_Y)
        c = c.cut(cq.Workplane("XY").circle(3.2).extrude(FRENTE + 0.1).translate((x, y, -0.05)))
    # USB-C en la pared derecha: arriba el de la SuperMini (programar), abajo el del TP4056 (cargar)
    _, uy = a_caja(0, USB[0])
    ty = tp4056_y()
    for y, z in ((uy, Z_PCB_ATRAS + 1.0 + 1.6), (ty, FRENTE + 1.0 + 1.6)):
        c = c.cut(cq.Workplane("YZ").rect(USB[1] + 0.8, USB[2] + 0.8).extrude(PARED + 1).translate((EXT_W / 2 - PARED - 0.5, y, z)))
        c = c.cut(cq.Workplane("YZ").rect(12.5, 7.0).extrude(0.8).translate((EXT_W / 2 - 0.7, y, z)))
    # switch de encendido en la pared izquierda (se pega por dentro)
    c = c.cut(cq.Workplane("YZ").rect(SWITCH[0] - 3.0, 2.2).extrude(PARED + 1).translate((-EXT_W / 2 - 0.5, ty, FRENTE + SWITCH[2] / 2)))
    # postes que detienen la placa (esquinas libres, fuera de la pantalla y de los botones)
    for (px0, py0, w, h) in ((0.0, 0.0, 2.3, 1.4), (PCB_W - 2.3, 0.0, 2.3, 1.4), (0.0, PCB_H - 1.8, 3.5, 1.8),
                             (PCB_W - 3.5, PCB_H - 1.8, 3.5, 1.8), (0.0, 31.5, 1.8, 2.5), (PCB_W - 1.8, 31.5, 1.8, 2.5)):
        cx, cy = a_caja(px0 + w / 2, py0 + h / 2)
        c = c.union(cq.Workplane("XY").rect(w, h).extrude(Z_PCB - FRENTE).translate((cx, cy, FRENTE)))
    # guías a los lados de la placa (la caja es más ancha que la placa por la pila)
    hueco_lado = (IN_W - PCB_W) / 2 - 0.15
    if hueco_lado > 0.4:
        for s in (-1, 1):
            for py in (6.0, 36.0):
                _, gy = a_caja(0, py)
                gx = s * (IN_W / 2 - hueco_lado / 2)
                c = c.union(cq.Workplane("XY").rect(hueco_lado, 6.0).extrude(Z_PCB_ATRAS - FRENTE).translate((gx, gy, FRENTE)))
    # aro para el llavero arriba
    aro = (cq.Workplane("XZ").center(0, PROF / 2).rect(9.0, min(PROF, 8.0)).extrude(-6.5)
           .edges("|Y").fillet(2.5).translate((0, EXT_H / 2 - 1.0, 0)))
    aro = aro.cut(cq.Workplane("XZ").center(0, PROF / 2).circle(1.9).extrude(-10).translate((0, EXT_H / 2 + 1.5, 0)))
    c = c.union(aro)
    # ranuras de la tapa a presión (laterales)
    zr = PROF - TAPA - LABIO / 2
    for s in (-1, 1):
        c = c.cut(cq.Workplane("XY").box(0.6, 12, 1.0).translate((s * (IN_W / 2 + 0.3), 0, zr)))
    # agujeros para las orejas de adorno
    for s in (-1, 1):
        x0 = -EXT_W / 2 - 0.5 if s < 0 else EXT_W / 2 - PARED - 0.5
        c = c.cut(cq.Workplane("YZ").circle(1.6).extrude(PARED + 1).translate((x0, EXT_H / 2 - 13, PROF / 2)))
    return c


def tapa():
    t = caja_redonda(EXT_W, EXT_H, TAPA, RADIO).faces(">Z").edges().fillet(0.8)
    lw, lh = IN_W - 0.3, IN_H - 0.3
    labio = caja_redonda(lw, lh, LABIO, RADIO_IN).cut(caja_redonda(lw - 2.4, lh - 2.4, LABIO, 0.4))
    t = t.union(labio.translate((0, 0, -LABIO)))
    for s in (-1, 1):   # bolitas de la presión
        t = t.union(cq.Workplane("XY").box(0.5, 11, 0.9).edges("|Y").fillet(0.2).translate((s * (lw / 2 + 0.2), 0, -LABIO / 2)))
    # deja la tapa en z = PROF - TAPA .. PROF (posición armada)
    return t.translate((0, 0, PROF - TAPA))


def boton():
    b = cq.Workplane("XY").circle(2.95).extrude(1.2 + FRENTE + 0.5)          # sale 1.2 mm
    b = b.faces("<Z").edges().fillet(0.8)
    b = b.union(cq.Workplane("XY").circle(4.2).extrude(0.8).translate((0, 0, 1.2 + FRENTE)))   # pestaña por dentro
    return b


def oreja():
    o = cq.Workplane("XY").rect(4.0, 9.0).extrude(3.0).edges("|Z").fillet(1.8).faces(">Z").edges().fillet(1.2)
    return o.union(cq.Workplane("XY").circle(1.45).extrude(-2.0))


def bolita():
    return cq.Workplane("XY").sphere(4.0).cut(cq.Workplane("XY").circle(1.9).extrude(10, both=True))


# ---------------- Piezas fantasma para revisar que todo quepa ----------------
def fantasmas():
    f = {}
    ox, oy = a_caja(OLED_CX, OLED_Y0 + OLED_H / 2)
    f["pantalla"] = cq.Workplane("XY").box(OLED_W, OLED_H, OLED_T).translate((ox, oy, Z_OLED + OLED_T / 2))
    pcx, pcy = a_caja(PCB_W / 2, PCB_H / 2)
    f["placa"] = cq.Workplane("XY").box(PCB_W, PCB_H, PCB_T).edges("|Z").fillet(PCB_R).translate((pcx, pcy, Z_PCB + PCB_T / 2))
    x0, y0, x1, y1, alto = MODULO
    cx, cy = a_caja((x0 + x1) / 2, (y0 + y1) / 2)
    f["supermini"] = cq.Workplane("XY").box(x1 - x0, y1 - y0, alto).translate((cx, cy, Z_PCB_ATRAS + alto / 2))
    f["tp4056"] = cq.Workplane("XY").box(TP4056[0], TP4056[1], TP4056[2]).translate((IN_W / 2 - TP4056[0] / 2 - 0.2, tp4056_y(), FRENTE + TP4056[2] / 2))
    f["switch"] = cq.Workplane("XY").box(SWITCH[1], SWITCH[0], SWITCH[2]).translate((-IN_W / 2 + SWITCH[1] / 2, tp4056_y(), FRENTE + SWITCH[2] / 2))
    f["pila"] = cq.Workplane("XY").box(BATERIA[0], BATERIA[1], BATERIA[2]).translate((0, 0, Z_BAT + BATERIA[2] / 2))
    sx, sy = a_caja(22.8 + SW18010[0] / 2, 6.5)
    f["vibracion"] = cq.Workplane("XY").box(SW18010[0], SW18010[1], SW18010[1]).translate((sx, sy, Z_PCB_ATRAS + SW18010[1] / 2))
    for i, bx in enumerate(BOTONES_X):
        x, y = a_caja(bx, BOTON_Y)
        f[f"boton{i}"] = cq.Workplane("XY").box(6.0, 6.0, SW_ALTO - 0.15).translate((x, y, Z_PCB - (SW_ALTO - 0.15) / 2))
    return f


def volumen(a, b):
    try:
        return a.intersect(b).val().Volume()
    except Exception:
        return 0.0


def main():
    SALIDA.mkdir(exist_ok=True)
    fr, tp = frente(), tapa()
    errores = []
    for nombre, g in fantasmas().items():
        for parte, p in (("frente", fr), ("tapa", tp)):
            v = volumen(g, p)
            if v > 0.05:
                errores.append(f"{nombre} choca con {parte} ({v:.2f} mm3)")
    # el botón impreso no debe tocar el émbolo antes de tiempo ni quedar flojo
    hueco_boton = Z_PCB - SW_ALTO - (FRENTE + 0.8)   # el botón termina en FRENTE+0.8 (pestaña)
    if hueco_boton < 0 or hueco_boton > 0.6:
        errores.append(f"botón: holgura con el émbolo {hueco_boton:.2f} mm (debe ser 0-0.6)")
    print(f"Caja {EXT_W:.1f} x {EXT_H:.1f} x {PROF:.1f} mm (sin el aro)")
    if errores:
        print("ERRORES:")
        for e in errores:
            print("  -", e)
        sys.exit(1)
    # El modelo se arma "visto de frente" (x a la derecha); en CAD, mirando la cara del frente, x queda a la
    # izquierda. Se espejea para que el USB quede del lado izquierdo, igual que en la placa.
    fr, tp = fr.mirror("YZ"), tp.mirror("YZ")
    piezas = {"frente": fr, "tapa": tp,
              "botones": boton().union(boton().translate((9, 0, 0))).union(boton().translate((18, 0, 0))),
              "orejas": oreja().union(oreja().translate((8, 0, 0))), "bolita": bolita()}
    for n, p in piezas.items():
        cq.exporters.export(p, str(SALIDA / f"tamaxof_{n}.stl"), tolerance=0.02, angularTolerance=0.1)
        cq.exporters.export(p, str(SALIDA / f"tamaxof_{n}.step"))
    # ensamble para verlo
    ens = fr.union(tp)
    for nombre, g in fantasmas().items():
        ens = ens.union(g.mirror("YZ"))
    cq.exporters.export(ens, str(SALIDA / "tamaxof_ensamble_con_piezas.stl"), tolerance=0.05)
    print("OK, sin choques. STL y STEP en", SALIDA)


if __name__ == "__main__":
    main()
