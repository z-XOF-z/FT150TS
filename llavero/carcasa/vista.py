"""Dibuja vistas de la carcasa del TamaXOF (vista.png). Correr después de carcasa_tamaxof.py."""
from pathlib import Path

import numpy as np
import trimesh
from PIL import Image, ImageDraw

S = Path(__file__).resolve().parent / "stl"
BLANCO, NARANJA, AZUL, VERDE, NEGRO = (240, 240, 236), (255, 122, 26), (90, 150, 255), (40, 170, 90), (30, 30, 30)


def malla(n, mover=(0, 0, 0)):
    m = trimesh.load(S / f"tamaxof_{n}.stl")
    m.apply_translation(mover)
    return m


def rot(yaw, pitch):
    y, p = np.radians(yaw), np.radians(pitch)
    ry = np.array([[np.cos(y), 0, np.sin(y)], [0, 1, 0], [-np.sin(y), 0, np.cos(y)]])
    rx = np.array([[1, 0, 0], [0, np.cos(p), -np.sin(p)], [0, np.sin(p), np.cos(p)]])
    return rx @ ry


def render(piezas, yaw, pitch, tam=420, esc=5.0):
    """Pintor: triángulos de atrás hacia adelante; la cámara mira hacia +z (la cara del frente de la caja es z=0)."""
    R = rot(yaw, pitch)
    tris, cols = [], []
    luz = np.array([-0.4, 0.6, -0.7]); luz /= np.linalg.norm(luz)
    for m, color in piezas:
        v = m.vertices @ R.T
        f = m.faces
        t = v[f]
        n = np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0])
        n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
        vis = n[:, 2] < 0                      # de cara a la cámara
        s = 0.4 + 0.6 * np.clip(n @ luz, 0, 1)
        tris.append(t[vis]); cols.append(np.clip(np.array(color)[None] * s[vis, None], 0, 255))
    t = np.concatenate(tris); c = np.concatenate(cols)
    orden = np.argsort(-t[:, :, 2].mean(axis=1))
    im = Image.new("RGB", (tam, tam), "white")
    d = ImageDraw.Draw(im)
    for i in orden:
        p = [(tam / 2 - x * esc, tam / 2 - y * esc) for x, y, _ in t[i]]   # mirando hacia +z, la x crece a la izquierda
        d.polygon(p, fill=tuple(int(k) for k in c[i]))
    return im


fr, tp = malla("frente"), malla("tapa")
bts = malla("botones", (-9.0, 29.15 - 0.3 - 35.75, -1.2))
bolita = malla("bolita", (0, 35.6, 9.35))
orejas = []
for s in (-1, 1):
    o = trimesh.load(S / "tamaxof_orejas.stl")
    o = o.split()[0] if len(o.split()) else o
    o.apply_translation(-o.bounds.mean(axis=0))
    o.apply_transform(trimesh.transformations.rotation_matrix(np.radians(90 * -s), (0, 1, 0)))
    o.apply_translation((s * (19.95 + 1.5), 30.95 - 13, 9.35))
    orejas.append((o, NARANJA))
dentro = malla("ensamble_con_piezas")

vistas = [
    ("Frente", render([(fr, BLANCO), (bts, NARANJA), (bolita, NARANJA)] + orejas, 0, 0)),
    ("3/4", render([(fr, BLANCO), (tp, BLANCO), (bts, NARANJA), (bolita, NARANJA)] + orejas, -35, 20)),
    ("Atras", render([(fr, BLANCO), (tp, (225, 225, 220))] + orejas, 180, 15)),
]
W = 420
lienzo = Image.new("RGB", (W * len(vistas), W + 30), "white")
d = ImageDraw.Draw(lienzo)
for i, (titulo, im) in enumerate(vistas):
    lienzo.paste(im, (i * W, 30))
    d.text((i * W + 10, 8), titulo, fill=NEGRO)
lienzo.save(S.parent / "vista.png")
print("vista.png")
