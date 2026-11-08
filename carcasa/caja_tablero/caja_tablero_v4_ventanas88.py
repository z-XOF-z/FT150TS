"""
Caja para la tarjeta base FT150TS v2.3 (placa de 100 x 100 mm), para ir debajo del asiento.

- Charola (abajo) + tapa (arriba). La TAPA se atornilla a los 4 birlos que trae el asiento por
  debajo (~106 mm entre centros a lo ancho, 49.8 mm a lo largo, rosca M6): los birlos entran por ranuras de la
  tapa y se aprietan por dentro con su tuerca y una roldana de hasta 18 mm (una más ancha choca con la pared). Por eso la caja es más ancha
  (134 mm) que la placa: las tuercas quedan a los lados de la placa.
- Montaje: 1) tapa sola al asiento con las 4 tuercas; 2) placa en la charola; 3) charola a la tapa
  con 4 tornillos M3 por abajo. Para darle servicio solo se quitan esos 4 tornillos.
- La placa se atornilla con 4 tornillos M3x20: entran por abajo (cabeza avellanada en el piso),
  pasan por el poste y la placa, y muerden la columna de la tapa. Así un solo juego de
  tornillos cierra todo y aprieta la placa.
- Ventanas en las 4 paredes a la altura de las borneras y tiras de pines, y una para el USB del ESP32.
- Alto total 28.1 mm: entra en los 32 mm libres bajo el asiento con 3.9 mm de margen.
- Imprimir en PETG (aguanta el calor bajo el asiento; el PLA se deforma al sol).
"""
import cadquery as cq

# ============================================================
# PARÁMETROS (mm)
# ============================================================
# Placa
pcb = 100.0               # lado de la placa
pcb_t = 1.6               # grosor
agujeros = 46.5           # agujeros M3 a 3.5 mm de las esquinas -> +-46.5 desde el centro
holgura = 0.6             # holgura placa-pared por lado

# Caja
pared = 2.0
piso = 2.0
poste_alto = 3.0          # separación piso-placa (patitas soldadas por abajo)
libre_sobre_pcb = 19.0    # relevador SRD de 15.5 mm + margen; deja 1.5 mm entre tuercas y borneras
tapa_t = 2.5
radio_esquina = 4.0

# Tornillos M3
m3_paso = 3.4             # agujero pasante
m3_piloto = 2.6           # agujero piloto para que el tornillo muerda el plástico
avellanado_d = 6.4        # cabeza avellanada M3
poste_d = 7.0             # poste y columna (el agujero de la placa tiene 3.5 mm libres alrededor)

# Ventanas para cables (altura medida desde arriba del piso)
ventana_z0 = poste_alto + pcb_t + 0.8     # justo arriba de la placa
ventana_alto = 9.5
ventana_largo = 88.0
usb_y = (-13.0, 7.0)                      # USB del ESP32 en la pared izquierda (coordenadas de la caja)
usb_z1 = poste_alto + pcb_t + 15.0

# Birlos del asiento M6 (medidos por Andrew: a lo ancho 110 con cinta y 108.1 con vernier (puntas entre centro y orilla) -> ~105-106 entre centros; a lo largo 44.1 mm por dentro y 55.4 por fuera con vernier -> 49.8 entre centros)
birlo_x = 53.0            # +-53 mm (106 entre centros); con la ranura de +-3 entran de 100 a 112
birlo_y = 24.9            # +-24.9 mm a lo largo (eje Y), medido con vernier
ranura = (6.6 + 6.0, 6.6 + 3.0)    # M6: +-3 mm a lo ancho (la medida ancha no quedó exacta) y +-1.5 mm a lo largo
ancho_extra_x = 134.0     # ancho de la caja (X): cubre los birlos y sus tuercas por dentro

# ============================================================
# MEDIDAS DERIVADAS
# ============================================================
interior = pcb + 2 * holgura                  # a lo largo (Y)
exterior = interior + 2 * pared
exterior_x = ancho_extra_x                    # a lo ancho (X), donde caen los birlos
interior_x = exterior_x - 2 * pared
alto_charola = piso + poste_alto + pcb_t + libre_sobre_pcb
columna_largo = libre_sobre_pcb           # de la tapa hasta encima de la placa
esquinas = [(sx * agujeros, sy * agujeros) for sx in (-1, 1) for sy in (-1, 1)]

# ============================================================
# CHAROLA
# ============================================================
charola = (
    cq.Workplane("XY")
    .box(exterior_x, exterior, alto_charola, centered=(True, True, False))
    .edges("|Z").fillet(radio_esquina)
)
hueco = (
    cq.Workplane("XY").workplane(offset=piso)
    .box(interior_x, interior, alto_charola, centered=(True, True, False))
    .edges("|Z").fillet(radio_esquina - pared)
)
charola = charola.cut(hueco)

# Postes de la placa
postes = (
    cq.Workplane("XY").workplane(offset=piso)
    .pushPoints(esquinas).circle(poste_d / 2).extrude(poste_alto)
)
charola = charola.union(postes)
# Agujero pasante con avellanado por abajo
charola = (
    charola.faces("<Z").workplane(centerOption="CenterOfBoundBox")
    .pushPoints([(x, -y) for x, y in esquinas])
    .cskHole(m3_paso, avellanado_d, 90)
)

# Ventanas para cables en las 4 paredes
z_centro = piso + ventana_z0 + ventana_alto / 2
for eje, signo in (("X", 1), ("X", -1), ("Y", 1), ("Y", -1)):
    if eje == "X":
        corte = cq.Workplane("XY").box(pared * 3, ventana_largo, ventana_alto).translate((signo * (exterior_x / 2 - pared / 2), 0, z_centro))
    else:
        corte = cq.Workplane("XY").box(ventana_largo, pared * 3, ventana_alto).translate((0, signo * (exterior / 2 - pared / 2), z_centro))
    charola = charola.cut(corte)
# USB del ESP32 (pared izquierda, -X), más alta
usb = cq.Workplane("XY").box(pared * 3, usb_y[1] - usb_y[0], usb_z1 - ventana_z0).translate(
    (-(exterior_x / 2 - pared / 2), (usb_y[0] + usb_y[1]) / 2, piso + (ventana_z0 + usb_z1) / 2))
charola = charola.cut(usb)
# 2 agujeros en la pared trasera (+Y) para atornillar el porta-batería (opcional): M3x12 + tuerca por dentro.
# Van fuera de la ventana y de la placa (x = +-56), donde por dentro no hay nada.
bateria_x, bateria_z = 56.0, 12.0
for sx in (-1, 1):
    charola = charola.cut(
        cq.Workplane("XZ").circle(m3_paso / 2).extrude(-pared * 3)
        .translate((sx * bateria_x, exterior / 2 - pared * 1.5, bateria_z)))

# ============================================================
# TAPA
# ============================================================
tapa = (
    cq.Workplane("XY")
    .box(exterior_x, exterior, tapa_t, centered=(True, True, False))
    .edges("|Z").fillet(radio_esquina)
)
# Ceja interior que encaja en la charola (alinea y tapa salpicaduras)
ceja = (
    cq.Workplane("XY")
    .box(interior_x - 0.4, interior - 0.4, 3.0, centered=(True, True, False))
    .edges("|Z").fillet(radio_esquina - pared)
    .cut(cq.Workplane("XY").box(interior_x - 3.6, interior - 3.6, 3.0, centered=(True, True, False)))
    .translate((0, 0, -3.0))
)
tapa = tapa.union(ceja)
# Columnas que bajan hasta la placa y reciben los tornillos
columnas = (
    cq.Workplane("XY").pushPoints(esquinas).circle(poste_d / 2).extrude(-columna_largo)
)
tapa = tapa.union(columnas)
tapa = tapa.cut(
    cq.Workplane("XY").workplane(offset=-columna_largo).pushPoints(esquinas)
    .circle(m3_piloto / 2).extrude(columna_largo + tapa_t - 0.8)       # ciego: no sale por arriba
)
# Ventilación sobre el LM2596 (arriba queda el asiento: no le cae agua directo)
for i in range(6):
    tapa = tapa.cut(cq.Workplane("XY").box(2.0, 22.0, tapa_t * 3).translate((-40 + i * 4.5, 14.0, 0)))
# Ranuras para los 4 birlos del asiento (la tuerca y la roldana normal van por dentro)
for sx in (-1, 1):
    for sy in (-1, 1):
        tapa = tapa.cut(
            cq.Workplane("XY").box(ranura[0], ranura[1], tapa_t * 4).edges("|Z").fillet(4.2)
            .translate((sx * birlo_x, sy * birlo_y, 0)))

# Texto en la tapa (cara de afuera, va pegada al asiento: solo para identificar)
tapa = tapa.cut(
    cq.Workplane("XY").workplane(offset=tapa_t - 0.6)
    .text("FT150TS  base v2.3", 6, 0.6, halign="center", valign="center").translate((0, -25, 0))
)

# ============================================================
# EXPORTAR
# ============================================================
import os
aqui = os.path.dirname(os.path.abspath(__file__))
cq.exporters.export(charola, os.path.join(aqui, "caja_charola.stl"), tolerance=0.01, angularTolerance=0.1)
# La tapa se imprime al revés (cara plana en la cama)
tapa_imprimir = tapa.rotate((0, 0, 0), (1, 0, 0), 180)
cq.exporters.export(tapa_imprimir, os.path.join(aqui, "caja_tapa.stl"), tolerance=0.01, angularTolerance=0.1)
# Ensamble para ver cómo queda
ensamble = charola.union(tapa.translate((0, 0, alto_charola)))
cq.exporters.export(ensamble, os.path.join(aqui, "caja_ensamble.stl"), tolerance=0.01, angularTolerance=0.1)
cq.exporters.export(charola, os.path.join(aqui, "caja_charola.step"))
cq.exporters.export(tapa, os.path.join(aqui, "caja_tapa.step"))
print(f"Exterior {exterior_x:.1f} x {exterior:.1f} mm, alto total {alto_charola + tapa_t:.1f} mm")
