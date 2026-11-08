"""Junta en un solo STL todo lo de la caja FT150TS para imprimirlo de una vez en la cama de 220 x 220 mm
(Ender-3 V3 SE): charola, tapa de la caja, porta-batería y su tapa. Correr después de caja_tablero.py
y caja_bateria.py.  Sale: cama_220_caja_ft150ts.stl
"""
import os

import numpy as np
import trimesh

aqui = os.path.dirname(os.path.abspath(__file__))
CAMA, SEP = 220.0, 4.0


def pieza(nombre, girar_z=0):
    m = trimesh.load(os.path.join(aqui, nombre))
    if girar_z:
        m.apply_transform(trimesh.transformations.rotation_matrix(np.radians(girar_z), (0, 0, 1)))
    m.apply_translation(-m.bounds[0])           # esquina en (0, 0, 0): apoyada en la cama
    return m


charola = pieza("caja_charola.stl")
tapa = pieza("caja_tapa.stl")
bat = pieza("bateria_cuerpo.stl", 90)
bat_tapa = pieza("bateria_tapa.stl", 90)

# Columna 1: charola y tapa de la caja. Columna 2: porta-batería y su tapa (girados 90°).
x2 = charola.extents[0] + SEP
tapa.apply_translation((0, charola.extents[1] + SEP, 0))
bat.apply_translation((x2, 0, 0))
bat_tapa.apply_translation((x2, bat.extents[1] + SEP, 0))
piezas = [charola, tapa, bat, bat_tapa]

todo = trimesh.util.concatenate(piezas)
tam = todo.extents
todo.apply_translation((CAMA / 2 - tam[0] / 2, CAMA / 2 - tam[1] / 2, 0) - todo.bounds[0] * (1, 1, 1))
# revisar que nada se encime (en planta) y que quepa en la cama con margen
cajas = [p.bounds[:, :2] for p in piezas]
for i in range(len(cajas)):
    for j in range(i + 1, len(cajas)):
        a, b = cajas[i], cajas[j]
        assert (a[1] <= b[0]).any() or (b[1] <= a[0]).any(), f"piezas {i} y {j} se enciman"
margen = (CAMA - tam[:2]) / 2
assert (margen >= 2.5).all(), f"no cabe con margen: {tam[:2]}"
todo.export(os.path.join(aqui, "cama_220_caja_ft150ts.stl"))
print(f"Cama: {tam[0]:.1f} x {tam[1]:.1f} x {tam[2]:.1f} mm, margen a la orilla {margen[0]:.1f} / {margen[1]:.1f} mm")
