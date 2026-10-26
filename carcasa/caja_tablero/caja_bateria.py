"""
Porta-batería desmontable para la caja FT150TS (accesorio aparte).

Batería medida por Andrew: pila LiPo "103465" de 55.0 x 32.2 x 20.8 mm, con cables y conector en un extremo.
- Se atornilla a la pared trasera (+Y) de la charola con 2 tornillos M3x12 + tuerca (agujeros a +-56 mm).
- El cable entra a la caja por la ventana de cables de la charola (el paso del cable queda enfrente).
- Si no cabe junto a la caja, se monta en otro lado: la pestaña trae 2 agujeros (para pared vertical)
  y el piso trae 2 agujeros avellanados (para superficie plana), como abrazadera omega.
- Tapa con ceja + 2 tornillos M3x8 en las columnas de las esquinas.
- Alto total 25.8 mm (la caja mide 28.1): no sobresale de la caja hacia el asiento.
- Imprimir en PETG, piso en la cama. La tapa, cara plana en la cama.
"""
import cadquery as cq
import os

# ============================================================
# PARÁMETROS (mm)
# ============================================================
bat = (55.0, 32.2, 20.8)       # largo (X), ancho (Y), grueso (Z) medidos con vernier
holgura = 0.6                  # por lado
zona_cable = 10.0              # espacio para el doblez del cable en el extremo -X
pared = 2.0
piso = 2.0
pestana_t = 4.0                # pared que se pega a la charola (más gruesa: lleva los tornillos)
pestana_x = 62.0               # la pestaña llega a +-62 mm
pestana_aguj_x = 56.0          # agujeros M3 (igual que en la charola)
pestana_aguj_z = 12.0
tapa_t = 2.0
columna_d = 7.0
m3_paso = 3.4
m3_piloto = 2.6
avellanado_d = 6.4
ranura_cable = (10.0, 7.0)     # ancho (X) x alto (Z) del paso del cable
ranura_cable_z = 9.0           # base: cae dentro de la ventana de la charola (7.4 a 16.9 mm)

# ============================================================
# DERIVADAS (y = 0 es la cara que toca la charola)
# ============================================================
in_x = bat[0] + 2 * holgura + zona_cable + 2 * columna_d     # columnas en las esquinas lejanas
in_y = bat[1] + 2 * holgura
in_z = bat[2] + 1.0
ext_x = in_x + 2 * pared
ext_y = pestana_t + in_y + pared
alto = piso + in_z
col_x = in_x / 2 - columna_d / 2 + 1.0     # se mete 1 mm en la pared (si solo la toca, la malla sale abierta)
col_y = pestana_t + in_y - columna_d / 2 + 1.0
columnas = [(-col_x, col_y), (col_x, col_y)]

# ============================================================
# CUERPO
# ============================================================
cuerpo = (cq.Workplane("XY").box(ext_x, ext_y, alto, centered=(True, False, False))
          .edges("|Z").fillet(3.0))
cuerpo = cuerpo.cut(
    cq.Workplane("XY").workplane(offset=piso)
    .box(in_x, in_y, alto, centered=(True, False, False)).translate((0, pestana_t, 0)))
# Pestaña ancha (la misma cara que toca la charola)
pestana = (cq.Workplane("XY").box(2 * pestana_x, pestana_t, alto, centered=(True, False, False))
           .edges("|Y").fillet(3.0))
cuerpo = cuerpo.union(pestana)
triangulo = cq.Workplane("XY").polyline([(0, 0), (8, 0), (0, 8)]).close().extrude(alto)
for sx in (-1, 1):
    cuerpo = cuerpo.cut(cq.Workplane("XZ").circle(m3_paso / 2).extrude(-pestana_t * 3)
                        .translate((sx * pestana_aguj_x, -pestana_t, pestana_aguj_z)))
    # refuerzo entre pestaña y cuerpo
    ref = triangulo.mirror("YZ") if sx < 0 else triangulo
    cuerpo = cuerpo.union(ref.translate((sx * (ext_x / 2 - 1.0), pestana_t - 0.5, 0)))
# Columnas para la tapa
cuerpo = cuerpo.union(cq.Workplane("XY").workplane(offset=piso).pushPoints(columnas)
                      .circle(columna_d / 2).extrude(in_z))
cuerpo = cuerpo.cut(cq.Workplane("XY").workplane(offset=alto - 10).pushPoints(columnas)
                    .circle(m3_piloto / 2).extrude(10))
# Paso del cable (extremo -X, enfrente de la ventana de la charola)
cx = -in_x / 2 + ranura_cable[0] / 2 + 1.0
cuerpo = cuerpo.cut(cq.Workplane("XY").box(ranura_cable[0], pestana_t * 3, ranura_cable[1], centered=(True, True, False))
                    .translate((cx, pestana_t / 2, ranura_cable_z)))
# Agujeros avellanados en el piso (para montarlo en otro lado sobre una superficie plana)
for x in (-15.0, 25.0):
    y = pestana_t + in_y / 2
    cuerpo = cuerpo.cut(cq.Workplane("XY").circle(m3_paso / 2).extrude(piso * 3).translate((x, y, -piso)))
    cono = cq.Solid.makeCone(0, avellanado_d / 2 + 0.01, avellanado_d / 2 + 0.01,
                             pnt=cq.Vector(x, y, piso - avellanado_d / 2), dir=cq.Vector(0, 0, 1))
    cuerpo = cuerpo.cut(cq.Workplane("XY").add(cono))

# ============================================================
# TAPA
# ============================================================
tapa = cq.Workplane("XY").box(ext_x, ext_y, tapa_t, centered=(True, False, False)).edges("|Z").fillet(3.0)
ceja = (cq.Workplane("XY").box(in_x - 0.4, in_y - 0.4, 2.0, centered=(True, False, False))
        .cut(cq.Workplane("XY").box(in_x - 3.6, in_y - 3.6, 2.0, centered=(True, False, False)).translate((0, 1.6, 0)))
        .translate((0, pestana_t + 0.2, -2.0)))
for x, y in columnas:      # la ceja no choca con las columnas
    ceja = ceja.cut(cq.Workplane("XY").circle(columna_d / 2 + 0.6).extrude(5).translate((x, y, -3)))
tapa = tapa.union(ceja)
for x, y in columnas:
    tapa = tapa.cut(cq.Workplane("XY").circle(m3_paso / 2).extrude(10).translate((x, y, -4)))
tapa = tapa.cut(cq.Workplane("XY").workplane(offset=tapa_t - 0.5)
                .text("BATERIA", 6, 0.5, halign="center", valign="center").translate((0, ext_y / 2 + 2, 0)))

# ============================================================
# EXPORTAR
# ============================================================
aqui = os.path.dirname(os.path.abspath(__file__))
cq.exporters.export(cuerpo, os.path.join(aqui, "bateria_cuerpo.stl"), tolerance=0.01, angularTolerance=0.1)
cq.exporters.export(tapa.rotate((0, 0, 0), (1, 0, 0), 180), os.path.join(aqui, "bateria_tapa.stl"),
                    tolerance=0.01, angularTolerance=0.1)
cq.exporters.export(cuerpo, os.path.join(aqui, "bateria_cuerpo.step"))
cq.exporters.export(tapa, os.path.join(aqui, "bateria_tapa.step"))
cq.exporters.export(cuerpo.union(tapa.translate((0, 0, alto))), os.path.join(aqui, "bateria_ensamble.stl"),
                    tolerance=0.01, angularTolerance=0.1)
print(f"Porta-bateria {ext_x:.1f} x {ext_y:.1f} (pestana {2 * pestana_x:.0f}) x {alto + tapa_t:.1f} mm; "
      f"hueco {in_x:.1f} x {in_y:.1f} x {in_z:.1f}")
