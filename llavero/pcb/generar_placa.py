"""Placa casera de UNA cara del llavero TamaXOF (planchado + ácido).

    python -I generar_placa.py

Saca, en esta carpeta:
  cobre_1200dpi.png / cobre_1200dpi.pdf   lo que se imprime para planchar (escala 1:1, NO espejear)
  barrenos.png                            dónde va cada barreno y su medida
  armado.png                              vista de armado (piezas del frente y de atrás)
y revisa solo que cada señal esté unida y no toque otra (si algo falla, no guarda nada).

Coordenadas en mm, vistas DE FRENTE (del lado de la pantalla), origen arriba a la izquierda.
El cobre está ATRÁS. Visto de frente a través de la placa, el planchado se imprime tal cual.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

AQUI = Path(__file__).resolve().parent
DPI = 1200
PXMM = DPI / 25.4
ANCHO, ALTO = 35.0, 40.5
PISTA = 0.6        # ancho de pista
SEP = 0.45         # separación mínima entre cobres de distinta señal
BORDE = 0.4        # sin cobre a esta distancia del borde
RADIO = 3.0        # esquinas redondas (para que entre en la caja)

# --- ESP32-C3 SuperMini (atrás, con los componentes hacia atrás, USB-C hacia la DERECHA).
# Se suelda plana: el estaño se mete por los agujeros de la SuperMini y agarra los pads de abajo.
# MEDIR la tuya: largo 22.5, filas a 15.24, primer pin a 3.0 mm del lado del USB.
SM_X1 = 34.6                       # orilla del lado del USB
SM_LARGO, SM_FILAS, SM_PIN1 = 22.52, 15.24, 3.0
FILA_ARRIBA = 12.5
FILA_ABAJO = FILA_ARRIBA + SM_FILAS
SM_X0 = SM_X1 - SM_LARGO


def x_pin(i):
    """i = 0 es el pin junto al USB."""
    return SM_X1 - SM_PIN1 - 2.54 * i


# Visto de frente (la SuperMini boca abajo, USB a la derecha):
#   fila de arriba, del USB hacia la izquierda: GPIO5 6 7 8 9 10 20 21
#   fila de abajo,  del USB hacia la izquierda: 5V GND 3V3 GPIO4 3 2 1 0
PADS = []      # (red, forma, x, y, w, h, barreno)
PISTAS = []    # (red, [(x, y), ...])
PUENTES = []   # (red, a, b): alambre por el frente
INTERNOS = []  # (red, a, b): unidos por dentro de la pieza (las 2 patas de cada lado de un botón)


def pad(red, x, y, w, h, forma="rect", barreno=0.0):
    PADS.append((red, forma, x, y, w, h, barreno))


def pista(red, *pts):
    PISTAS.append((red, list(pts)))


PAD_SM = (1.6, 2.6)
superMini = {
    ("arriba", 0): "MOV", ("arriba", 4): "SDA", ("arriba", 5): "SCL",
    ("abajo", 0): "5V", ("abajo", 1): "GND", ("abajo", 2): "3V3",
    ("abajo", 4): "DER", ("abajo", 5): "OK", ("abajo", 6): "IZQ", ("abajo", 7): "BATS",
}
for (fila, i), red in superMini.items():
    pad(red, x_pin(i), FILA_ARRIBA if fila == "arriba" else FILA_ABAJO, *PAD_SM)
for fila, y in (("arriba", FILA_ARRIBA), ("abajo", FILA_ABAJO)):   # sin usar: solo para que quede firme
    for i in range(8):
        if (fila, i) not in superMini:
            pad(f"NC_{fila}{i}", x_pin(i), y, *PAD_SM)

# --- Pantalla OLED 0.96" (frente): GND VCC SCL SDA. Centrada para que SCL/SDA caigan sobre GPIO10/9
OLED_CX = x_pin(5) - 1.27
Y_OLED = 3.0
OLED_PINES = (("GND", OLED_CX - 3.81), ("3V3", OLED_CX - 1.27), ("SCL", OLED_CX + 1.27), ("SDA", OLED_CX + 3.81))
for red, x in OLED_PINES:
    pad(red, x, Y_OLED, 2.0, 2.0, "redondo", 0.9)
pista("SCL", (x_pin(5), FILA_ARRIBA), (x_pin(5), Y_OLED))
pista("SDA", (x_pin(4), FILA_ARRIBA), (x_pin(4), 6.0), (OLED_CX + 3.81, 4.2), (OLED_CX + 3.81, Y_OLED))

# --- 3V3: puente de alambre por el frente (A junto al pin 3V3, B junto al VCC de la pantalla)
A, B = (x_pin(2), 30.6), (OLED_CX - 1.27, 7.0)
pad("3V3", *A, 1.8, 1.8, "redondo", 0.8)
pad("3V3", *B, 1.8, 1.8, "redondo", 0.8)
pista("3V3", (x_pin(2), FILA_ABAJO), A)
pista("3V3", B, (OLED_CX - 1.27, Y_OLED))
PUENTES.append(("3V3", A, B))

# --- Sensor de vibración SW-18010P (acostado atrás, arriba a la derecha; patas dobladas sobre los pads)
Y_SW = 6.5
SW_X = (23.2, 33.8)
pad("GND", SW_X[0], Y_SW, 1.4, 2.4)
pad("MOV", SW_X[1], Y_SW, 1.4, 2.4)
pista("MOV", (SW_X[1], Y_SW), (SW_X[1], 8.6), (x_pin(0), 10.8), (x_pin(0), FILA_ARRIBA))

# --- Divisor de la pila (220k + 220k, 1206), a la izquierda de la SuperMini
pista("BATS", (x_pin(7), FILA_ABAJO), (x_pin(7), 29.8), (10.2, 29.8))
pad("BATS", 10.2, 29.8, 1.6, 1.0)        # R2 arriba-abajo... R2: BATS (10.2,29.8) - GND (7.2,29.8)
pad("GND", 7.2, 29.8, 1.6, 1.0)
pad("BATS", 10.2, 26.5, 1.6, 1.0)        # R1: BATS (10.2,26.5) - BAT+ (10.2,23.5)
pad("BAT+", 10.2, 23.5, 1.6, 1.0)
pista("BATS", (10.2, 29.8), (10.2, 26.5))
pad("BAT+", 10.2, 20.2, 2.4, 2.6)        # cable del + de la pila (del B+ del TP4056)
pista("BAT+", (10.2, 23.5), (10.2, 20.2))
pad("GND", 4.0, 21.0, 3.2, 3.2)          # cable del - (OUT- del TP4056)

# --- Alimentación: OUT+ del TP4056 -> switch -> diodo 1N5819 -> este pad (es el pin 5V de la SuperMini)
pista("5V", (x_pin(0), FILA_ABAJO), (x_pin(0), 30.2), (33.4, 31.4), (33.4, 35.5))
pad("5V", 33.4, 35.5, 2.2, 3.0)

# --- Botones 6x6 de 4 patas (frente). Patas de arriba = señal, de abajo = tierra
Y_BS, Y_BG = 33.5, 38.0
BOTONES = (("IZQ", 8.5), ("OK", 17.5), ("DER", 26.5))
for red, cx in BOTONES:
    for dx in (-3.25, 3.25):
        pad(red, cx + dx, Y_BS, 2.0, 2.0, "redondo", 1.0)
        pad("GND", cx + dx, Y_BG, 2.0, 2.0, "redondo", 1.0)
    INTERNOS.append((red, (cx - 3.25, Y_BS), (cx + 3.25, Y_BS)))
pista("IZQ", (x_pin(6), FILA_ABAJO), (x_pin(6), 30.0), (12.6, 32.4), (11.75, Y_BS))
pista("OK", (x_pin(5), FILA_ABAJO), (x_pin(5), 30.6), (20.75, 32.6), (20.75, Y_BS))
pista("DER", (x_pin(4), FILA_ABAJO), (x_pin(4), 30.2), (23.25, 32.2), (23.25, Y_BS))

# --- Sin cobre debajo de la SuperMini (entre sus filas de pines)
BAJO_MODULO = (SM_X0, FILA_ARRIBA + 1.3, SM_X1, FILA_ABAJO - 1.3)


# ======================================================================
def px(v):
    return int(round(v * PXMM))


def dibujar_pad(d, p, crece=0.0, color=0):
    red, forma, x, y, w, h, _ = p
    if forma == "redondo":
        r = w / 2 + crece
        d.ellipse([px(x - r), px(y - r), px(x + r), px(y + r)], fill=color)
    else:
        d.rectangle([px(x - w / 2 - crece), px(y - h / 2 - crece), px(x + w / 2 + crece), px(y + h / 2 + crece)], fill=color)


def dibujar_pista(d, pts, ancho, color=0):
    d.line([(px(x), px(y)) for x, y in pts], fill=color, width=max(1, px(ancho)), joint="curve")
    for x, y in pts:
        r = ancho / 2
        d.ellipse([px(x - r), px(y - r), px(x + r), px(y + r)], fill=color)


def cobre():
    """Imagen 'L': 0 = cobre (negro, lo que se imprime), 255 = sin cobre."""
    w, h = px(ANCHO), px(ALTO)
    im = Image.new("L", (w, h), 0)               # todo es tierra...
    d = ImageDraw.Draw(im)
    # ...menos el borde, debajo del XIAO y la ventana
    # fuera de la placa (esquinas redondas R3) y el borde: sin cobre
    fuera = Image.new("L", (w, h), 255)
    ImageDraw.Draw(fuera).rounded_rectangle([px(BORDE), px(BORDE), w - 1 - px(BORDE), h - 1 - px(BORDE)], radius=px(RADIO - BORDE), fill=0)
    im.paste(255, mask=fuera)
    d.rectangle([px(BAJO_MODULO[0]), px(BAJO_MODULO[1]), px(BAJO_MODULO[2]), px(BAJO_MODULO[3])], fill=255)
    # alrededor de todo lo que NO es tierra se abre la separación
    for p in PADS:
        if p[0] != "GND":
            dibujar_pad(d, p, SEP, 255)
    for red, pts in PISTAS:
        if red != "GND":
            dibujar_pista(d, pts, PISTA + 2 * SEP, 255)
    for p in PADS:
        dibujar_pad(d, p, 0, 0)
    for red, pts in PISTAS:
        dibujar_pista(d, pts, PISTA, 0)
    # barrenos: puntito blanco al centro (guía para la broca)
    for red, forma, x, y, w_, h_, bar in PADS:
        if bar:
            r = 0.25
            d.ellipse([px(x - r), px(y - r), px(x + r), px(y + r)], fill=255)
    return im


def revisar(im):
    """Cada red: todos sus pads unidos (contando puentes); dos redes nunca en la misma isla."""
    a = np.array(im) == 0
    etiquetas, n = ndimage.label(a)
    isla = {}
    errores = []
    for red, forma, x, y, w, h, bar in PADS:
        e = etiquetas[px(y + (h / 2 - 0.15 if bar else 0)), px(x)] if bar else etiquetas[px(y), px(x)]
        if e == 0:
            errores.append(f"pad de {red} en ({x},{y}) sin cobre")
            continue
        isla.setdefault(red, set()).add(e)
    # puentes: unen islas
    for red, pa, pb in PUENTES + INTERNOS:
        ea, eb = etiquetas[px(pa[1] + 0.8), px(pa[0])], etiquetas[px(pb[1] + 0.8), px(pb[0])]
        isla[red] = {min(ea, eb) if x in (ea, eb) else x for x in isla[red]}
    for red, s in isla.items():
        if not red.startswith("NC_") and len(s) > 1:
            errores.append(f"{red} quedó partida en {len(s)} pedazos")
    duenos = {}
    for red, s in isla.items():
        for e in s:
            if e in duenos and duenos[e] != red:
                errores.append(f"{red} y {duenos[e]} se tocan")
            duenos[e] = red
    # separación mínima entre señales distintas: cada red crecida SEP/2 no debe pisar a otra
    w, h = im.size
    cuenta = np.zeros((h, w), np.uint8)
    dueno = np.zeros((h, w), np.int16)
    for k, red in enumerate(sorted({p[0] for p in PADS} | {r for r, _ in PISTAS})):
        if red == "GND":
            continue
        capa = Image.new("L", (w, h), 0)
        d = ImageDraw.Draw(capa)
        for p in PADS:
            if p[0] == red:
                dibujar_pad(d, p, SEP / 2 - 0.02, 255)
        for r, pts in PISTAS:
            if r == red:
                dibujar_pista(d, pts, PISTA + SEP - 0.04, 255)
        m = np.array(capa) > 0
        choque = m & (cuenta > 0) & (dueno != k + 1)
        if choque.any():
            ys, xs = np.nonzero(choque)
            errores.append(f"{red} queda a menos de {SEP} mm de otra señal cerca de ({xs.mean() / PXMM:.1f}, {ys.mean() / PXMM:.1f})")
        cuenta[m] += 1
        dueno[m] = k + 1
    return errores, n


def barrenos_png():
    esc = 20
    im = Image.new("RGB", (int(ANCHO * esc) + 220, int(ALTO * esc) + 40), "white")
    d = ImageDraw.Draw(im)
    d.rectangle([10, 10, 10 + ANCHO * esc, 10 + ALTO * esc], outline="black", width=2)
    cuenta = {}
    for red, forma, x, y, w, h, bar in PADS:
        if bar:
            cuenta[bar] = cuenta.get(bar, 0) + 1
            r = bar / 2 * esc + 2
            d.ellipse([10 + x * esc - r, 10 + y * esc - r, 10 + x * esc + r, 10 + y * esc + r], outline="blue", width=2)
    f = ImageFont.load_default()
    y = 20
    d.text((ANCHO * esc + 25, y), "BARRENOS", fill="black", font=f); y += 18
    for bar, n in sorted(cuenta.items()):
        d.text((ANCHO * esc + 25, y), f"{n} x {bar:.1f} mm", fill="blue", font=f); y += 16
    im.save(AQUI / "barrenos.png")


def armado_png(cu):
    esc = 20
    base = cu.resize((int(ANCHO * esc), int(ALTO * esc)), Image.LANCZOS)
    cobre_rgb = Image.merge("RGB", [base.point(lambda v: 210 if v < 128 else 245), base.point(lambda v: 140 if v < 128 else 245),
                                   base.point(lambda v: 80 if v < 128 else 235)])
    im = Image.new("RGB", (int(ANCHO * esc) + 20, int(ALTO * esc) + 20), "white")
    im.paste(cobre_rgb, (10, 10))
    d = ImageDraw.Draw(im, "RGBA")
    o = lambda x, y: (10 + x * esc, 10 + y * esc)
    def caja(x0, y0, x1, y1, c, t=""):
        d.rectangle([o(x0, y0), o(x1, y1)], outline=c, width=2)
        if t:
            d.text((o(x0, y0)[0] + 4, o(x0, y0)[1] + 3), t, fill=c)
    caja(SM_X0, FILA_ARRIBA - 1.4, SM_X1, FILA_ABAJO + 1.4, (40, 90, 200, 255), "ESP32-C3 SuperMini (atras)  USB ->")
    caja(OLED_CX - 13.65, 1.6, OLED_CX + 13.65, 29.4, (0, 150, 70, 255), "")
    d.text(o(OLED_CX - 13.3, 27.6), "OLED 0.96 (frente)", fill=(0, 150, 70, 255))
    for red, cx in BOTONES:
        caja(cx - 3, (Y_BS + Y_BG) / 2 - 3, cx + 3, (Y_BS + Y_BG) / 2 + 3, (200, 60, 0, 255), red)
    caja(SW_X[0] - 0.4, Y_SW - 2.4, SW_X[1] + 0.4, Y_SW + 2.4, (150, 0, 150, 255), "SW-18010P")
    for t, x, y in (("BAT+", 8.6, 17.6), ("R1", 11.6, 24.4), ("R2", 7.4, 30.8), ("GND", 2.6, 23.0), ("5V", 32.4, 37.4)):
        d.text(o(x, y), t, fill=(150, 0, 150, 255))
    for red, pa, pb in PUENTES:
        d.line([o(*pa), o(*pb)], fill=(255, 0, 0, 200), width=3)
        d.text(o(pb[0] + 1.2, pb[1] + 2), "puente 3V3\n(frente)", fill=(255, 0, 0, 255))
    im.save(AQUI / "armado.png")


def main():
    im = cobre()
    errores, islas = revisar(im)
    if errores:
        print("ERRORES:")
        for e in errores:
            print("  -", e)
        sys.exit(1)
    print(f"OK: {len(PADS)} pads, {len(PISTAS)} pistas, {len(PUENTES)} puente(s), {islas} islas de cobre")
    # marco de corte (línea fina) para el planchado
    imp = im.copy()
    ImageDraw.Draw(imp).rounded_rectangle([0, 0, imp.width - 1, imp.height - 1], radius=px(RADIO), outline=0, width=2)
    imp.save(AQUI / "cobre_1200dpi.png", dpi=(DPI, DPI))
    imp.convert("1").save(AQUI / "cobre_1200dpi.pdf", resolution=DPI)
    barrenos_png()
    armado_png(im)


if __name__ == "__main__":
    main()
