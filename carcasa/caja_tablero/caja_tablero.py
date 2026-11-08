"""
Caja para la tarjeta base FT150TS v2.3 (placa de 100 x 100 mm), para ir debajo del asiento.  v5

- Charola (abajo) + tapa (arriba). La TAPA se atornilla a los 4 birlos que trae el asiento por
  debajo (~106 mm entre centros a lo ancho, 49.8 mm a lo largo, rosca M6): los birlos entran por ranuras de la
  tapa y se aprietan por dentro con su tuerca y una roldana de hasta 18 mm. Por eso la caja es más ancha
  (134 mm) que la placa: las tuercas quedan a los lados de la placa.
- Montaje: 1) tapa sola al asiento con las 4 tuercas; 2) placa en la charola y cables conectados;
  3) charola a la tapa con 4 tornillos M3x20 por abajo (pasan poste, placa y muerden la columna de la tapa).
- v5: en vez de ventanas corridas de 88 mm, cada bornera tiene su abertura del tamaño exacto de su cara
  (por ahí entra el cable) y la tapa trae una CEJA que baja por la pared hasta tocar la bornera, así no queda
  hueco arriba. Las tiras de pines (dupont) salen por una ranura de 5 mm pegada a la tapa. Al final todo se
  sella por fuera con silicón negro.
- v5: más alta (22 mm libres sobre la placa): la pieza más alta soldada mide 21 mm.
- Coordenadas: la placa se ve desde arriba igual que en KiCad (KiCad y hacia abajo -> caja y hacia arriba).
  Pared +Y = orilla de arriba de la placa (BOBINA, ENTRADAS, SD, I2C, LCD, HALL).
- Imprimir en PETG (aguanta el calor bajo el asiento; el PLA se deforma al sol).
"""
import os

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
poste_alto = 2.5          # separación piso-placa (patitas soldadas y cortadas por abajo)
libre_sobre_pcb = 22.0    # pieza más alta medida por Andrew: 21 mm + 1 mm
tapa_t = 2.5
radio_esquina = 4.0

# Borneras (altura sobre la placa). Valores de los modelos 3D de KiCad: MEDIR las reales con vernier.
alto_bornera_5mm = 11.4   # las de 5.0 mm de paso (BAT MOTO, CDI PARO, BOBINA, RELE 1-3)
alto_bornera_35mm = 9.2   # las de 3.5 mm de paso (LLAVE, BATERIA, CARGADOR, ENTRADAS, QUICKSHIFT, GASOLINA, SALIDA AISLADA)
holgura_ceja = 0.4        # entre la ceja y la bornera (arriba) y a cada lado de la abertura
ranura_pines = 5.0        # alto de la ranura para los cables dupont (pegada a la tapa)

# Tornillos M3
m3_paso = 3.4             # agujero pasante
m3_piloto = 2.6           # agujero piloto para que el tornillo muerda el plástico
avellanado_d = 6.4        # cabeza avellanada M3
poste_d = 7.0             # poste y columna

# Birlos del asiento M6 (medidos por Andrew con vernier)
birlo_x = 53.0            # +-53 mm (106 entre centros); con la ranura de +-3 entran de 100 a 112
birlo_y = 24.9            # +-24.9 mm a lo largo
ranura = (6.6 + 6.0, 6.6 + 3.0)
ancho_extra_x = 134.0     # ancho de la caja (X): cubre los birlos y sus tuercas por dentro

# ============================================================
# MEDIDAS DERIVADAS
# ============================================================
interior = pcb + 2 * holgura                  # a lo largo (Y)
exterior = interior + 2 * pared
exterior_x = ancho_extra_x
interior_x = exterior_x - 2 * pared
alto_charola = piso + poste_alto + pcb_t + libre_sobre_pcb
columna_largo = libre_sobre_pcb
z_placa = piso + poste_alto + pcb_t           # cara de arriba de la placa
esquinas = [(sx * agujeros, sy * agujeros) for sx in (-1, 1) for sy in (-1, 1)]


def bx(x):   # x de KiCad -> caja
    return x - 50.0


def by(y):   # y de KiCad (hacia abajo) -> caja (hacia arriba)
    return 50.0 - y


# ============================================================
# ABERTURAS (de la placa B v2.3, coordenadas KiCad del contorno de cada pieza)
# tipo "bornera": abertura del tamaño de su cara + ceja de la tapa que baja hasta ella
# tipo "pines":   ranura pegada a la tapa para los cables dupont
# tipo "usb":     paso del cable USB del ESP32 con ceja
# ============================================================
B5, B35 = alto_bornera_5mm, alto_bornera_35mm
aberturas = [
    # pared, nombre, desde, hasta (a lo largo de la pared, coordenada KiCad), tipo, alto
    ("-X", "LLAVE",          18.8, 26.8, "bornera", B35),
    ("-X", "CDI PARO",       27.1, 38.2, "bornera", B5),
    ("-X", "BAT MOTO 12V",   55.1, 66.1, "bornera", B5),
    ("-X", "BATERIA 3.7V",   66.8, 74.9, "bornera", B35),
    ("-X", "CARGADOR 5V",    78.8, 86.9, "bornera", B35),
    ("-X", "DS18B20",         7.3, 18.5, "pines", 0),
    ("-X", "USB ESP32",      37.0, 53.0, "usb", 0),
    ("+X", "BOTONES-LDR-GPS-LEDS", 8.0, 48.0, "pines", 0),
    ("+X", "SALIDA AISLADA", 50.3, 58.4, "bornera", B35),
    ("+Y", "SD-I2C-LCD",      7.3, 46.1, "pines", 0),
    ("+Y", "ENTRADAS 3-16V", 53.8, 68.9, "bornera", B35),
    ("+Y", "BOBINA",         70.0, 81.1, "bornera", B5),
    ("+Y", "HALL",           81.3, 92.5, "pines", 0),
    ("-Y", "QUICKSHIFT",      7.6, 15.6, "bornera", B35),
    ("-Y", "GASOLINA",       16.8, 24.8, "bornera", B35),
    ("-Y", "RELE 3",         33.1, 49.1, "bornera", B5),
    ("-Y", "RELE 2",         54.1, 70.1, "bornera", B5),
    ("-Y", "RELE 1",         75.0, 91.1, "bornera", B5),
]
usb_z = (z_placa + 8.0, z_placa + 15.0)      # el USB-C del DevKit queda ~11-14 mm sobre la placa


def a_caja(pared_, a, b):
    """Convierte el tramo KiCad a (centro, ancho) en la coordenada de la caja a lo largo de esa pared."""
    if pared_ in ("-X", "+X"):
        c1, c2 = by(a), by(b)
    else:
        c1, c2 = bx(a), bx(b)
    return (c1 + c2) / 2, abs(c2 - c1)


def caja_en_pared(pared_, centro, ancho, z0, z1, grueso, extra=0.0):
    """Caja que atraviesa la pared indicada, entre z0 y z1 (z de la charola)."""
    alto = z1 - z0
    zc = (z0 + z1) / 2
    if pared_ in ("-X", "+X"):
        s = 1 if pared_ == "+X" else -1
        return cq.Workplane("XY").box(grueso, ancho, alto).translate((s * (exterior_x / 2 - pared / 2 + extra), centro, zc))
    s = 1 if pared_ == "+Y" else -1
    return cq.Workplane("XY").box(ancho, grueso, alto).translate((centro, s * (exterior / 2 - pared / 2 + extra), zc))


cortes, cejas, cortes_anillo = [], [], []
for pared_, nombre, a, b, tipo, alto in aberturas:
    c, w = a_caja(pared_, a, b)
    w_hueco = w + 2 * holgura_ceja
    if tipo == "bornera":
        z_ceja = z_placa + alto + holgura_ceja
        cortes.append(caja_en_pared(pared_, c, w_hueco, z_placa, alto_charola + 1, pared * 3))
        cejas.append((pared_, c, w_hueco - 2 * 0.2, z_ceja))
    elif tipo == "usb":
        cortes.append(caja_en_pared(pared_, c, w_hueco, usb_z[0], alto_charola + 1, pared * 3))
        cejas.append((pared_, c, w_hueco - 2 * 0.2, usb_z[1]))
    else:   # pines: ranura bajo la tapa, sin ceja
        cortes.append(caja_en_pared(pared_, c, w_hueco, alto_charola - ranura_pines, alto_charola + 1, pared * 3))
        cortes_anillo.append((pared_, c, w_hueco))

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
postes = (
    cq.Workplane("XY").workplane(offset=piso)
    .pushPoints(esquinas).circle(poste_d / 2).extrude(poste_alto)
)
charola = charola.union(postes)
charola = (
    charola.faces("<Z").workplane(centerOption="CenterOfBoundBox")
    .pushPoints([(x, -y) for x, y in esquinas])
    .cskHole(m3_paso, avellanado_d, 90)
)
for k in cortes:
    charola = charola.cut(k)

# 2 agujeros en la pared trasera (+Y) para el porta-batería (opcional) y el paso de su cable.
# OJO: si el porta-batería va en esta pared tapa BOBINA y ENTRADAS; mejor montarlo aparte.
bateria_x, bateria_z = 56.0, 12.0
for sx in (-1, 1):
    charola = charola.cut(
        cq.Workplane("XZ").circle(m3_paso / 2).extrude(-pared * 3)
        .translate((sx * bateria_x, exterior / 2 - pared * 1.5, bateria_z)))
charola = charola.cut(caja_en_pared("+Y", -34.1, 10.0, 9.0, 16.0, pared * 3))

# ============================================================
# TAPA (z = 0 es su cara de abajo; en el ensamble va encima de la charola)
# ============================================================
tapa = (
    cq.Workplane("XY")
    .box(exterior_x, exterior, tapa_t, centered=(True, True, False))
    .edges("|Z").fillet(radio_esquina)
)
anillo = (
    cq.Workplane("XY")
    .box(interior_x - 0.4, interior - 0.4, 3.0, centered=(True, True, False))
    .edges("|Z").fillet(radio_esquina - pared)
    .cut(cq.Workplane("XY").box(interior_x - 3.6, interior - 3.6, 3.0, centered=(True, True, False)))
    .translate((0, 0, -3.0))
)
for pared_, c, w in cortes_anillo:      # el anillo no tapa las ranuras de los dupont
    anillo = anillo.cut(caja_en_pared(pared_, c, w, -alto_charola, alto_charola, 8.0, extra=-2.0).translate((0, 0, 0)))
tapa = tapa.union(anillo)
# Cejas: llenan la abertura de la pared desde arriba hasta tocar la bornera
for pared_, c, w, z_ceja in cejas:
    largo = alto_charola - z_ceja
    tapa = tapa.union(caja_en_pared(pared_, c, w, alto_charola - largo, alto_charola, pared)
                      .translate((0, 0, -alto_charola)))
columnas = cq.Workplane("XY").pushPoints(esquinas).circle(poste_d / 2).extrude(-columna_largo)
tapa = tapa.union(columnas)
tapa = tapa.cut(
    cq.Workplane("XY").workplane(offset=-columna_largo).pushPoints(esquinas)
    .circle(m3_piloto / 2).extrude(columna_largo + tapa_t - 0.8)       # ciego: no sale por arriba
)
# Ventilación sobre el LM2596 (KiCad x 11-22, y 53-62)
lm_x, lm_y = bx((11.2 + 21.8) / 2), by((52.6 + 62.2) / 2)
for i in range(6):
    tapa = tapa.cut(cq.Workplane("XY").box(2.0, 22.0, tapa_t * 3).translate((lm_x - 11.25 + i * 4.5, lm_y, 0)))
for sx in (-1, 1):
    for sy in (-1, 1):
        tapa = tapa.cut(
            cq.Workplane("XY").box(ranura[0], ranura[1], tapa_t * 4).edges("|Z").fillet(4.2)
            .translate((sx * birlo_x, sy * birlo_y, 0)))
tapa = tapa.cut(
    cq.Workplane("XY").workplane(offset=tapa_t - 0.6)
    .text("FT150TS  base v2.3", 6, 0.6, halign="center", valign="center").translate((0, 30, 0))
)

# ============================================================
# REVISIÓN: las cejas no chocan con la charola y caben en sus aberturas
# ============================================================
ens_tapa = tapa.translate((0, 0, alto_charola))
choque = charola.intersect(ens_tapa).val().Volume() if True else 0
print(f"Choque charola-tapa: {choque:.3f} mm3", "OK" if choque < 0.01 else "REVISAR")

# ============================================================
# EXPORTAR
# ============================================================
aqui = os.path.dirname(os.path.abspath(__file__))
cq.exporters.export(charola, os.path.join(aqui, "caja_charola.stl"), tolerance=0.01, angularTolerance=0.1)
tapa_imprimir = tapa.rotate((0, 0, 0), (1, 0, 0), 180)      # cara plana en la cama
cq.exporters.export(tapa_imprimir, os.path.join(aqui, "caja_tapa.stl"), tolerance=0.01, angularTolerance=0.1)
cq.exporters.export(charola.union(ens_tapa), os.path.join(aqui, "caja_ensamble.stl"), tolerance=0.01, angularTolerance=0.1)
cq.exporters.export(charola, os.path.join(aqui, "caja_charola.step"))
cq.exporters.export(tapa, os.path.join(aqui, "caja_tapa.step"))
print(f"Exterior {exterior_x:.1f} x {exterior:.1f} mm, alto total {alto_charola + tapa_t:.1f} mm, "
      f"interior {alto_charola - piso:.1f} mm (libre sobre la placa {libre_sobre_pcb:.1f})")
for pared_, nombre, a, b, tipo, alto in aberturas:
    c, w = a_caja(pared_, a, b)
    print(f"  {pared_} {nombre:22} centro {c:6.1f} ancho {w + 2 * holgura_ceja:5.1f} {tipo}")
