"""Vistas de la caja v5 con borneras fantasma (vista_caja_v5.png). Correr después de caja_tablero.py."""
import os
import numpy as np
import trimesh
from PIL import Image, ImageDraw

aqui = os.path.dirname(os.path.abspath(__file__))
import importlib.util
spec = importlib.util.spec_from_file_location("ct", os.path.join(aqui, "caja_tablero.py"))
# (se leen solo los números, sin volver a generar la caja)
src = open(os.path.join(aqui, "caja_tablero.py"), encoding="utf-8").read().split("cortes, cejas, cortes_anillo")[0]
ns = {}
exec(src.replace("import cadquery as cq", "cq = None"), ns)


def rot(yaw, pitch):
    y, p = np.radians(yaw), np.radians(pitch)
    rz = np.array([[np.cos(y), -np.sin(y), 0], [np.sin(y), np.cos(y), 0], [0, 0, 1]])
    rx = np.array([[1, 0, 0], [0, np.cos(p), -np.sin(p)], [0, np.sin(p), np.cos(p)]])
    return rx @ rz


def render(piezas, yaw, pitch, tam=700, esc=3.6):
    R = rot(yaw, pitch); tris, cols = [], []
    luz = np.array([0.3, -0.5, 0.8]); luz /= np.linalg.norm(luz)
    for m, color in piezas:
        v = m.vertices @ R.T; t = v[m.faces]
        n = np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0]); n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
        n_mundo = n @ R
        vis = n[:, 1] < 0
        s = 0.45 + 0.55 * np.clip(n_mundo @ luz, 0, 1)
        tris.append(t[vis]); cols.append(np.clip(np.array(color)[None] * s[vis, None], 0, 255))
    t = np.concatenate(tris); c = np.concatenate(cols)
    orden = np.argsort(-t[:, :, 1].mean(axis=1))
    im = Image.new("RGB", (tam, tam), "white"); d = ImageDraw.Draw(im)
    for i in orden:
        d.polygon([(tam / 2 + x * esc, tam / 2 - z * esc) for x, _, z in t[i]], fill=tuple(int(k) for k in c[i]))
    return im


charola = trimesh.load(os.path.join(aqui, "caja_charola.stl"))
tapa_imp = trimesh.load(os.path.join(aqui, "caja_tapa.stl"))
zp = ns["z_placa"]
fant = []
placa = trimesh.creation.box((100, 100, 1.6)); placa.apply_translation((0, 0, zp - 0.8)); fant.append((placa, (30, 110, 60)))
for pared_, nombre, a, b, tipo, alto in ns["aberturas"]:
    if tipo != "bornera":
        continue
    c, w = ns["a_caja"](pared_, a, b)
    prof = 8.0
    if pared_ in ("-X", "+X"):
        s = 1 if pared_ == "+X" else -1
        m = trimesh.creation.box((prof, w, alto)); m.apply_translation((s * (50 - prof / 2), c, zp + alto / 2))
    else:
        s = 1 if pared_ == "+Y" else -1
        m = trimesh.creation.box((w, prof, alto)); m.apply_translation((c, s * (50 - prof / 2), zp + alto / 2))
    fant.append((m, (60, 200, 120)))
for x0, x1 in ((33.5, 53.7), (54.5, 74.7), (75.5, 95.7)):
    m = trimesh.creation.box((x1 - x0, 15.9, 15.6)); m.apply_translation(((x0 + x1) / 2 - 50, 50 - 79.05, zp + 7.8)); fant.append((m, (60, 120, 230)))
tapa = tapa_imp.copy(); tapa.apply_transform(trimesh.transformations.rotation_matrix(np.pi, (1, 0, 0)))
tapa.apply_translation((0, 0, ns["alto_charola"] + ns["tapa_t"] - tapa.bounds[1][2]))
BL, GR = (235, 235, 230), (170, 170, 175)
vistas = [
    ("Charola con placa (frente: RELES, GASOLINA, QUICKSHIFT)", render([(charola, BL)] + fant, 20, 35)),
    ("Atras: BOBINA, ENTRADAS, ranura dupont", render([(charola, BL)] + fant, 200, 35)),
    ("Cerrada: las cejas bajan hasta cada bornera", render([(charola, BL), (tapa, GR)] + fant, 20, 15)),
    ("Tapa volteada (como se imprime)", render([(tapa_imp, GR)], 25, 35)),
]
W = 700
lienzo = Image.new("RGB", (W * 2, (W + 30) * 2), "white"); d = ImageDraw.Draw(lienzo)
for i, (tit, im) in enumerate(vistas):
    x, y = (i % 2) * W, (i // 2) * (W + 30)
    lienzo.paste(im, (x, y + 30)); d.text((x + 10, y + 8), tit, fill=(0, 0, 0))
lienzo.save(os.path.join(aqui, "vista_caja_v5.png")); print("ok")
