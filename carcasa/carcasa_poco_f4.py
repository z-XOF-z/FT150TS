"""
Carcasa de precisión para el POCO F4 en la moto (FT150TS) + socket para la bola del soporte que ya está instalado.

Piezas (cada una en su STL):
  1. bandeja      — el teléfono acostado en horizontal (cara hacia arriba). Se imprime con el piso en la cama.
  2. marco        — bisel delgado que pisa solo la orilla del vidrio; pernos que entran en la bandeja.
                    Se imprime boca abajo (la cara frontal en la cama: acabado liso).
  3. socket_fijo  — mitad fija del socket, se atornilla a la bandeja (patrón AMPS 30 x 38 mm).
  4. socket_movil — mitad móvil; al apretar la perilla abraza la bola.
  5. perilla      — perilla moleteada para el tornillo M5.
  + calibracion_XX — anillos de prueba de la holgura (imprimir ANTES de la carcasa completa).

Unión bandeja-marco: 4 tornillos M3 escondidos por atrás (avellanados, entran por la bandeja y roscan en insertos de
calor M3 dentro de los pernos del marco). Nada aprieta la pantalla, la batería ni los botones.

Ejes: X = largo del teléfono, Y = ancho, Z = grosor (Z = 0 es el piso de la bandeja, del lado de la cama).

IMPORTANTE: las medidas marcadas "MEDIR" no están publicadas por Xiaomi; son estimaciones para el primer prototipo.
"""
import os
import cadquery as cq

# ============================================================
# PARÁMETROS — POCO F4 (fuente: PhoneArena / Xiaomi: 163.2 x 76.0 x 7.7 mm, 195 g)
# ============================================================
tel_largo = 163.2        # mm
tel_ancho = 76.0         # mm
tel_grosor = 7.7         # mm (sin el saliente de la cámara)
tel_radio_esquina = 9.0  # mm — MEDIR (radio en planta de las esquinas)

# Cámara (vista por ATRÁS, en vertical: arriba a la izquierda). Medida sobre la vista trasera oficial (GSMArena, escala
# 163.2 mm = 611 px → 0.267 mm/px; el ancho de 76 mm da la misma escala). Ventana con 1.5 mm de margen: también ventila.
cam_largo = 54.0         # mm — isla 51.0 mm (medida en la vista oficial de GSMArena) + 1.5 por lado
cam_ancho = 31.3         # mm — isla 28.3 mm + 1.5 por lado
cam_desde_arriba = 4.4   # mm — la isla empieza a 5.9 mm del borde (foto oficial); comprobar
cam_desde_lado = 4.4     # mm — la isla empieza a 5.9 mm del borde (foto oficial); comprobar

# Botones: lado derecho (en vertical, viendo la pantalla). Una sola ventana larga para volumen + encendido/huella.
botones_desde_arriba = 36.0   # mm — volumen empieza a ~39 mm del borde superior (foto oficial) - 3 de margen
botones_largo = 46.0          # mm — encendido termina a ~79 mm (foto oficial) + 3 de margen
botones_alto = 5.0            # mm (alto de la ventana, centrada en el grosor)

# USB-C (abajo, al centro). Hueco del conector del cable (cabe un cable con funda de hasta 12.5 x 7 mm)
usb_ancho = 13.0         # mm
usb_alto = 7.0           # mm

# ============================================================
# PARÁMETROS — Ajuste (la parte delicada)
# ============================================================
# Holgura POR LADO entre el teléfono y la pared. El objetivo es ~0.05 mm, pero en FDM la tolerancia real de la
# impresora es de ±0.1–0.2 mm: se empieza en 0.15 y se ajusta con los anillos de calibración.
holgura_xy = 0.15        # mm por lado (total = 2 x este valor)
# Costillas de apriete: 3 por lado largo, sobresalen esto hacia el teléfono (se deforman al meterlo = cero juego)
costilla_alto = 0.20     # mm
costilla_ancho = 1.2     # mm
costilla_largo = 12.0    # mm
holgura_z = 0.10         # mm de juego en el grosor (el marco no debe aplastar el vidrio)
calibraciones = [0.05, 0.15, 0.25]   # holguras por lado de los anillos de prueba

# ============================================================
# PARÁMETROS — Bandeja y marco
# ============================================================
pared = 2.4              # mm (6 líneas de 0.4)
piso = 4.0               # mm — 4.6x más rígido que 2.4 y deja 2 mm bajo las cabezas M4 avellanadas (revisión)
extremo = 10.0           # mm de bloque en cada extremo (ahí van los tornillos)
radio_exterior = 6.0     # mm esquinas exteriores
marco_grosor = 2.0       # mm
marco_pisa = 1.6         # mm que el marco pisa sobre la orilla del vidrio (solo el borde negro)
perno_d = 7.0            # mm pernos del marco
perno_largo = 6.5        # mm
inserto_d = 4.0          # mm agujero para inserto de calor M3 (M3 x 5.7)
inserto_prof = 5.8       # mm
tornillo_d = 3.4         # mm paso de M3
avellanado_d = 6.4       # mm cabeza de M3 avellanado (DIN 7991)
tornillo_sep_y = 22.0    # mm entre los dos tornillos de cada extremo

# Nervaduras de refuerzo en la espalda (a lo largo): la bandeja queda en voladizo porque el socket va corrido.
# Por fuera de la placa del socket (±28 mm) y de la ventana de la cámara (lado -Y, hasta -34 mm)
# Desactivadas: con el voladizo real (102 mm) la flecha ya es 0.33 mm sin ellas, y por fuera de la espalda
# obligarían a imprimir el piso sobre soportes. Para activarlas: nervio_alto = 4.0, nervio_y = [31.5, -37.0]
nervio_alto = 0.0        # mm hacia atrás
nervio_ancho = 2.4       # mm
nervio_y = []            # mm

# Ventilación en el piso (ranuras)
vent_largo = 40.0
vent_ancho = 3.0
vent_sep = 7.0

# ============================================================
# PARÁMETROS — Socket para la bola existente
# ============================================================
# Bola del soporte instalado. Estándar RAM: B = 1" (25.4 mm, el más común en soportes de manubrio para
# teléfono), C = 1.5" (38.1 mm). MEDIR la tuya con vernier y cambiar aquí.
bola_d = 25.4            # mm — escaneo 3D de la moto: 25.5 mm (ajuste de esfera); vernier de Andrew: 24.5. Estándar RAM B = 25.4. El socket aprieta ±1 mm
bola_holgura = 0.2       # mm (radio del hueco = radio de la bola + esto)
cuello_d = 15.0          # mm hueco para el cuello de la bola (el cuello de un RAM B mide ~11–13 mm)
socket_pared = 4.0       # mm de material alrededor de la bola
socket_abertura = 2.0    # mm de separación entre las dos mitades (lo que cierra al apretar)
socket_ancho = 38.0      # mm (en Y) — ancho para los dos tornillos de bisagra
# Bisagra de la mordaza móvil: 2 tornillos M3 abajo (con el M5 de arriba = 3 puntos: la mordaza no gira ni se desliza)
bisagra_y = 15.5         # mm a cada lado del cuello
bisagra_z_sobre_fondo = 2.5
bisagra_d = 3.4          # paso M3
tuerca_m3 = 5.6          # entre caras (+0.1)
tuerca_m3_alto = 4.0     # nyloc M3

# Patrón AMPS (estándar de RAM, Garmin, etc.): 30 x 38 mm
amps_x = 38.0
amps_y = 30.0
amps_tornillo_d = 4.4    # mm paso de M4 (en la bandeja, avellanado)
amps_avellanado_d = 8.4  # mm cabeza M4 avellanada
amps_inserto_d = 5.6     # mm inserto de calor M4 (en el socket fijo)
amps_inserto_prof = 7.0
placa_grosor = 9.0       # mm placa del socket fijo

# Inclinación del socket respecto a la bandeja (escaneo 3D de la moto, 2026-10-22: la bola apunta casi vertical, 2.4°;
# para que la pantalla mire al piloto, ~25° sobre la horizontal, hacen falta ~65°). La bola da ±20° → cubre 40–80°.
# La bola está ~50 mm a la izquierda del centro de la moto (escaneo 3D): el socket se corre en la bandeja para que el
# teléfono quede centrado en la moto
socket_desplazamiento_x = -50.0   # mm a lo largo del teléfono
socket_inclinacion = -60.0   # grados, giro alrededor del eje largo (X). El signo pone el cuello hacia el borde de abajo
perno_m5_d = 5.5         # mm paso del tornillo de apriete M5
tuerca_m5 = 8.1          # mm entre caras (tuerca M5 = 8 mm)
tuerca_m5_alto = 5.2     # tuerca de seguridad con nylon (nyloc): no se afloja con la vibración
perilla_d = 30.0
perilla_alto = 14.0

# Machihembrado marco-bandeja en los lados largos (traba el marco en toda su longitud, no solo en las puntas)
lengueta_ancho = 1.2     # mm
lengueta_alto = 2.0      # mm
lengueta_holgura = 0.2   # mm por lado en la ranura

# Placa del socket (más grande = reparte el esfuerzo del voladizo)
placa_largo = 80.0       # mm
placa_ancho = 56.0       # mm

# Tapa removible (sol, polvo, y que no se note que hay un teléfono)
tapa_pared = 1.8         # mm
tapa_techo = 2.0         # mm
tapa_holgura = 0.25      # mm por lado contra la bandeja
tapa_aire = 3.0          # mm de aire sobre el marco (no toca la pantalla, deja circular)
tapa_falda = 6.0         # mm que baja por los lados de la bandeja
tapa_seguro_alto = 0.5   # mm del reborde que entra en la ranura de la bandeja (clic)
tapa_cable_ancho = 16.0  # mm muesca para el cable USB-C
# Tornillos de la tapa: 2 por extremo, entran por la falda y roscan en insertos M3 horizontales en los bloques de la bandeja
tapa_torn_y = 30.0       # mm a cada lado del centro
tapa_torn_bajo_tope = 3.0  # mm debajo del borde de la bandeja

eps = 0.01

# ============================================================
# Medidas derivadas
# ============================================================
hueco_largo = tel_largo + 2 * holgura_xy
hueco_ancho = tel_ancho + 2 * holgura_xy
hueco_radio = tel_radio_esquina + holgura_xy
ext_largo = hueco_largo + 2 * extremo
ext_ancho = hueco_ancho + 2 * pared
z_tope = piso + tel_grosor + holgura_z              # altura de la bandeja = tope del vidrio + juego
torn_x = hueco_largo / 2 + extremo / 2
torn_pts = [(sx * torn_x, sy * tornillo_sep_y / 2) for sx in (-1, 1) for sy in (-1, 1)]


def rect_redondeado(largo, ancho, radio, alto, z0=0.0):
    return (cq.Workplane("XY").workplane(offset=z0)
            .rect(largo, ancho).extrude(alto).edges("|Z").fillet(radio))


# ============================================================
# 1. BANDEJA
# ============================================================
def bandeja():
    cuerpo = rect_redondeado(ext_largo, ext_ancho, radio_exterior, z_tope)
    cuerpo = cuerpo.faces(">Z").edges().chamfer(0.6)                       # orilla superior suave
    hueco = rect_redondeado(hueco_largo, hueco_ancho, hueco_radio, z_tope, z0=piso)
    b = cuerpo.cut(hueco)

    # Ranura del machihembrado: arriba de las paredes largas, al centro del grosor de la pared
    for sy in (-1, 1):
        y = sy * (hueco_ancho / 2 + pared / 2)
        b = b.cut(cq.Workplane("XY").box(hueco_largo - 2 * hueco_radio, lengueta_ancho + 2 * lengueta_holgura,
                                          lengueta_alto + 0.3, centered=(True, True, False))
                  .translate((0, y, z_tope - lengueta_alto - 0.3)))
        # Ranura exterior donde engancha la tapa (clic)
        yo = sy * ext_ancho / 2
        b = b.cut(cq.Workplane("XY").box(ext_largo - 2 * radio_exterior - 10, 2 * tapa_seguro_alto, 1.4,
                                          centered=(True, True, False)).translate((0, yo, z_tope - 3.4)))
    # Costillas de apriete en los lados largos (3 por lado)
    for sy in (-1, 1):
        for xc in (-hueco_largo / 3, 0.0, hueco_largo / 3):
            c = (cq.Workplane("XY").box(costilla_largo, costilla_alto * 2, tel_grosor * 0.6)
                 .translate((xc, sy * hueco_ancho / 2, piso + tel_grosor / 2)))
            b = b.union(c)

    zc = piso + tel_grosor / 2                                             # mitad del grosor del teléfono
    # USB-C: extremo -X (abajo del teléfono). Hueco redondeado que atraviesa el bloque
    usb = (cq.Workplane("YZ").rect(usb_ancho, usb_alto).extrude(extremo + 4).edges("|X").fillet(usb_alto / 2 - 0.3)
           .translate((-hueco_largo / 2 - extremo - 2, 0, zc)))
    b = b.cut(usb)
    # Bocinas: ranuras en ambos extremos (no atraviesan donde están los tornillos)
    for sx in (-1, 1):
        for dy in (-18.0, 18.0):
            s = (cq.Workplane("YZ").slot2D(10.0, 3.0).extrude(extremo + 4)
                 .translate((sx * (hueco_largo / 2 + extremo / 2) - (extremo + 4) / 2, dy, zc)))
            if abs(dy) - 5 > tornillo_sep_y / 2 + perno_d / 2 or abs(dy) + 5 < tornillo_sep_y / 2 - perno_d / 2:
                b = b.cut(s)
    # Botones: lado derecho viendo la pantalla (arriba del teléfono = +X  →  derecha = -Y)
    bx0 = hueco_largo / 2 - botones_desde_arriba - botones_largo
    bot = (cq.Workplane("XY").slot2D(botones_largo, botones_alto).extrude(pared + 2)
           .rotate((0, 0, 0), (1, 0, 0), 90)
           .translate((bx0 + botones_largo / 2, -hueco_ancho / 2 + 1, zc)))
    b = b.cut(bot)

    # Cámara: arriba a la izquierda VISTA POR ATRÁS = arriba a la derecha vista de frente = lado -Y (mismo lado que
    # los botones)
    cx = hueco_largo / 2 - cam_desde_arriba - cam_largo / 2
    cy = -(hueco_ancho / 2 - cam_desde_lado - cam_ancho / 2)
    b = b.cut(rect_redondeado(cam_largo, cam_ancho, 5.0, piso + 2, z0=-1).translate((cx, cy, 0)))

    # Ventilación del piso (fuera de la cámara y del patrón AMPS)
    for i in range(-3, 4):
        x = -hueco_largo / 2 + 22 + i * 0
    xs = [-2.0, 12.0]                 # entre el socket (corrido a la izquierda) y la ventana de la cámara
    for x in xs:
        for j in range(-3, 4):
            y = j * vent_sep
            if abs(x - socket_desplazamiento_x) < placa_largo / 2 + 3 and abs(y) < amps_y / 2 + 8:
                continue
            b = b.cut(cq.Workplane("XY").slot2D(vent_largo * 0.5, vent_ancho).extrude(piso + 2)
                      .translate((x, y, -1)))

    # Patrón AMPS: 4 tornillos M4 avellanados desde ADENTRO (la cabeza queda al ras bajo el teléfono)
    for px in (socket_desplazamiento_x - amps_x / 2, socket_desplazamiento_x + amps_x / 2):
        for py in (-amps_y / 2, amps_y / 2):
            b = b.cut(cq.Workplane("XY").circle(amps_tornillo_d / 2).extrude(piso + 2).translate((px, py, -1)))
            avell = cq.Solid.makeCone(amps_tornillo_d / 2, amps_avellanado_d / 2, (amps_avellanado_d - amps_tornillo_d) / 2,
                                      cq.Vector(px, py, piso - (amps_avellanado_d - amps_tornillo_d) / 2 + eps))
            b = b.cut(cq.Workplane().add(avell))

    # Nervaduras de refuerzo en la espalda (opcionales, ver parámetros)
    for y in nervio_y:
        n_largo = ext_largo - 2 * radio_exterior - 4
        nerv = (cq.Workplane("XY").box(n_largo, nervio_ancho, nervio_alto, centered=(True, True, False))
                .translate((0, y, -nervio_alto + 0.01)))
        nerv = nerv.edges("|Y and <Z").chamfer(nervio_alto - 0.5)
        b = b.union(nerv)
    # Insertos M3 horizontales en los extremos para los tornillos de la tapa
    for sx in (-1, 1):
        for sy in (-1, 1):
            b = b.cut(cq.Workplane("YZ").circle(inserto_d / 2).extrude(inserto_prof + 0.5)
                      .translate((sx * ext_largo / 2 - (inserto_prof + 0.5 if sx > 0 else 0) + (0 if sx > 0 else -0.01),
                                  sy * tapa_torn_y, z_tope - tapa_torn_bajo_tope)))
    # Tornillos M3 escondidos: avellanado por abajo + paso + bolsillo para el perno del marco
    for (x, y) in torn_pts:
        b = b.cut(cq.Workplane("XY").circle(tornillo_d / 2).extrude(z_tope + 2).translate((x, y, -1)))
        av = cq.Solid.makeCone(avellanado_d / 2, tornillo_d / 2, (avellanado_d - tornillo_d) / 2, cq.Vector(x, y, -eps))
        b = b.cut(cq.Workplane().add(av))
        b = b.cut(cq.Workplane("XY").circle(perno_d / 2 + 0.2).extrude(perno_largo + 0.4)
                  .translate((x, y, z_tope - perno_largo - 0.4)))
    return b


# ============================================================
# 2. MARCO (bisel). Se modela en su posición (sobre la bandeja); el STL se exporta girado para imprimir.
# ============================================================
def marco():
    m = rect_redondeado(ext_largo, ext_ancho, radio_exterior, marco_grosor, z0=z_tope)
    m = m.faces(">Z").edges().fillet(1.2)
    ventana = rect_redondeado(tel_largo - 2 * marco_pisa, tel_ancho - 2 * marco_pisa,
                              max(1.0, tel_radio_esquina - marco_pisa), marco_grosor + 2, z0=z_tope - 1)
    m = m.cut(ventana)
    for sy in (-1, 1):
        y = sy * (hueco_ancho / 2 + pared / 2)
        m = m.union(cq.Workplane("XY").box(hueco_largo - 2 * hueco_radio - 2, lengueta_ancho, lengueta_alto,
                                            centered=(True, True, False)).translate((0, y, z_tope - lengueta_alto + eps)))
    for (x, y) in torn_pts:
        perno = cq.Workplane("XY").circle(perno_d / 2).extrude(perno_largo).translate((x, y, z_tope - perno_largo))
        m = m.union(perno)
        m = m.cut(cq.Workplane("XY").circle(inserto_d / 2).extrude(inserto_prof)
                  .translate((x, y, z_tope - perno_largo)))
    return m


# ============================================================
# 3-4. SOCKET (debajo de la bandeja). La bola queda centrada en Y; el apriete es a lo largo de X.
# ============================================================
R = bola_d / 2
Rc = R + bola_holgura
juego_placa = 0.6                                # la mitad móvil no toca la placa
perno_z = -placa_grosor - 7.0                    # tornillo de apriete, justo debajo de la placa
bola_z = perno_z - perno_m5_d / 2 - 2.5 - Rc     # centro de la bola
fondo_z = bola_z - R * 0.62                      # el socket abraza la bola por debajo del ecuador
fulcro_alto = 4.0                                # abajo, a los lados del cuello: la mitad móvil se apoya ahí
cuerpo_x = 2 * (Rc + socket_pared)
# Palanca: fulcro abajo, bola en medio, tornillo arriba → fuerza en la bola ≈ (perno - fulcro) / (bola - fulcro)
ventaja = (perno_z - (fondo_z + fulcro_alto / 2)) / (bola_z - (fondo_z + fulcro_alto / 2))


def socket_cuerpo():
    alto = -placa_grosor - fondo_z
    c = (cq.Workplane("XY").box(cuerpo_x, socket_ancho, alto, centered=(True, True, False))
         .translate((0, 0, fondo_z)))
    c = c.edges("|Y and <Z").fillet(5.0)
    c = c.cut(cq.Workplane().add(cq.Solid.makeSphere(Rc, cq.Vector(0, 0, bola_z))))
    # Salida del cuello, con cono para que la bola incline ±20°
    cono = cq.Solid.makeCone(cuello_d / 2, cuello_d / 2 + 6, -fondo_z + bola_z + 0.01 if False else (bola_z - fondo_z),
                             cq.Vector(0, 0, bola_z), cq.Vector(0, 0, -1))
    c = c.cut(cq.Workplane().add(cono))
    c = c.cut(cq.Workplane("XY").circle(cuello_d / 2).extrude(bola_z - fondo_z + 2).translate((0, 0, fondo_z - 1)))
    # Tornillo de apriete M5 a lo largo de X
    c = c.cut(cq.Workplane("YZ").circle(perno_m5_d / 2).extrude(cuerpo_x + 10)
              .translate((-cuerpo_x / 2 - 5, 0, perno_z)))
    # Tornillos de bisagra M3 (abajo, a los lados del cuello)
    for sy in (-1, 1):
        c = c.cut(cq.Workplane("YZ").circle(bisagra_d / 2).extrude(cuerpo_x + 10)
                  .translate((-cuerpo_x / 2 - 5, sy * bisagra_y, fondo_z + bisagra_z_sobre_fondo)))
    return c


def _lado(x0, x1, z0, z1):
    return cq.Workplane("XY").box(x1 - x0, socket_ancho + 2, z1 - z0, centered=False).translate((x0, -socket_ancho / 2 - 1, z0))


import math
_sin_inc = abs(math.sin(math.radians(socket_inclinacion)))
bajada = (socket_ancho / 2) * _sin_inc + 1.0      # baja el cuerpo girado para que no toque la placa


def pose_socket(w):
    """Gira el cuerpo del socket [socket_inclinacion] alrededor del eje X (por la cara de la placa) y lo baja."""
    return (w.rotate((0, 0, -placa_grosor), (1, 0, -placa_grosor), socket_inclinacion)
            .translate((0, 0, -bajada)))


def centro_bola():
    """Centro de la bola ya con la inclinación (para revisiones y el ensamble)."""
    a = math.radians(socket_inclinacion)
    y, z = 0.0, bola_z + placa_grosor
    return (socket_desplazamiento_x, y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a) - placa_grosor - bajada)


def socket_fijo():
    placa = (cq.Workplane("XY").box(placa_largo, placa_ancho, placa_grosor, centered=(True, True, False))
             .edges("|Z").fillet(8).faces("<Z").edges().chamfer(1.5).translate((0, 0, -placa_grosor)))
    for px in (-amps_x / 2, amps_x / 2):
        for py in (-amps_y / 2, amps_y / 2):
            placa = placa.cut(cq.Workplane("XY").circle(amps_inserto_d / 2).extrude(amps_inserto_prof)
                              .translate((px, py, -amps_inserto_prof)))
    mitad = socket_cuerpo().intersect(_lado(-cuerpo_x, -socket_abertura / 2, fondo_z - 1, 0))
    # Trampa para la tuerca M5 (cara exterior -X)
    trampa = (cq.Workplane("YZ").polygon(6, tuerca_m5 / 0.866).extrude(tuerca_m5_alto + 1)
              .translate((-cuerpo_x / 2 - 1, 0, perno_z)))
    for sy in (-1, 1):
        trampa = trampa.union(cq.Workplane("YZ").polygon(6, tuerca_m3 / 0.866).extrude(tuerca_m3_alto + 1)
                              .translate((-cuerpo_x / 2 - 1, sy * bisagra_y, fondo_z + bisagra_z_sobre_fondo)))
    mitad = pose_socket(mitad.cut(trampa))
    # Cuña: llena entre la placa y la cara de arriba del cuerpo girado (solo del lado fijo, la mitad móvil queda libre)
    alto_cuna = bajada + socket_ancho * _sin_inc + 2
    cuna = (cq.Workplane("XY").box(cuerpo_x / 2 - socket_abertura / 2, placa_ancho - 8, alto_cuna, centered=False)
            .translate((-cuerpo_x / 2, -(placa_ancho - 8) / 2, -placa_grosor - alto_cuna + 0.01)))
    debajo = pose_socket(cq.Workplane("XY").box(cuerpo_x * 3, socket_ancho * 6, 200, centered=(True, True, False))
                         .translate((0, 0, -placa_grosor - 200)))
    cuna = cuna.cut(debajo)
    # Solo sobre la proyección de la cara de arriba del cuerpo girado (si no, queda una aleta larga que no sirve)
    medio_y = (socket_ancho / 2) * abs(math.cos(math.radians(socket_inclinacion))) + 1.0
    cuna = cuna.intersect(cq.Workplane("XY").box(cuerpo_x, 2 * medio_y, 400).translate((0, 0, 0)))
    # La cuña solo sobre la huella del cuerpo (no un bloque entero): se recorta a lo ancho del socket proyectado
    return _hueco_bola(placa.union(cuna).union(mitad)).translate((socket_desplazamiento_x, 0, 0))


def _hueco_bola(w):
    """Vuelve a vaciar el lugar de la bola y del cuello (los topes del fulcro y la cuña no deben invadirlo)."""
    cb = (0.0,) + tuple(centro_bola()[1:])     # antes de correr el socket en X
    w = w.cut(cq.Workplane().add(cq.Solid.makeSphere(Rc, cq.Vector(*cb))))
    return w.cut(pose_socket(cq.Workplane("XY").circle(cuello_d / 2).extrude(bola_z - fondo_z + 2)
                             .translate((0, 0, fondo_z - 1))))


def socket_movil():
    c = socket_cuerpo()
    mitad = c.intersect(_lado(socket_abertura / 2, cuerpo_x, fondo_z - 1, -placa_grosor - juego_placa))
    # Fulcro: abajo, a los dos lados del cuello, la mitad móvil llena la abertura y se apoya en la fija
    for sy in (-1, 1):
        y0 = cuello_d / 2 + 1.5
        tope = (cq.Workplane("XY").box(socket_abertura - 0.2, socket_ancho / 2 - y0, fulcro_alto, centered=False)
                .translate((-socket_abertura / 2 + 0.2, y0 if sy > 0 else -socket_ancho / 2, fondo_z)))
        mitad = mitad.union(tope.intersect(socket_cuerpo().union(
            cq.Workplane("XY").box(socket_abertura, socket_ancho, fulcro_alto, centered=(True, True, False)).translate((0, 0, fondo_z)))))
    return _hueco_bola(pose_socket(mitad)).translate((socket_desplazamiento_x, 0, 0))


def perilla():
    p = cq.Workplane("XY").circle(perilla_d / 2).extrude(perilla_alto).edges(">Z").fillet(3)
    for i in range(12):                                   # moleteado
        p = p.cut(cq.Workplane("XY").circle(2.2).extrude(perilla_alto + 2)
                  .translate((0, 0, -1)).rotate((0, 0, 0), (0, 0, 1), 0)
                  .translate((perilla_d / 2 + 0.8, 0, 0)).rotate((0, 0, 0), (0, 0, 1), i * 30))
    p = p.cut(cq.Workplane("XY").polygon(6, 8.1 / 0.866).extrude(4.5).translate((0, 0, -eps)))   # cabeza/tuerca M5
    p = p.cut(cq.Workplane("XY").circle(perno_m5_d / 2).extrude(perilla_alto + 2).translate((0, 0, -1)))
    return p


def perilla_en_ensamble():
    """La perilla puesta en la punta del M5, del lado del socket móvil (solo para el ensamble de AutoCAD)."""
    p = perilla().rotate((0, 0, 0), (0, 1, 0), 90).translate((cuerpo_x / 2, 0, perno_z))
    return pose_socket(p).translate((socket_desplazamiento_x, 0, 0))


def tapa():
    """Tapa removible a presión: cubre la pantalla (sol/polvo) y parece una pieza más de la moto (nervaduras + FT150TS).
    Se modela en su posición; el STL sale con el techo en la cama."""
    il = ext_largo + 2 * tapa_holgura; ia = ext_ancho + 2 * tapa_holgura
    ol = il + 2 * tapa_pared; oa = ia + 2 * tapa_pared
    z0 = z_tope - tapa_falda
    alto_int = tapa_falda + marco_grosor + tapa_aire
    t = rect_redondeado(ol, oa, radio_exterior + tapa_pared, alto_int + tapa_techo, z0=z0)
    t = t.faces(">Z").edges().fillet(2.5)
    t = t.cut(rect_redondeado(il, ia, radio_exterior + tapa_holgura, alto_int, z0=z0 - eps))
    # Clic: reborde en la falda de los lados largos que entra en la ranura de la bandeja
    for sy in (-1, 1):
        t = t.union(cq.Workplane("XY").box(ext_largo - 2 * radio_exterior - 14, tapa_seguro_alto * 2 + 2 * tapa_holgura, 1.0,
                                            centered=(True, True, False))
                    .translate((0, sy * (ia / 2), z_tope - 3.2)))
    # Pasos de los 4 tornillos M3 de la tapa (con cajera para la cabeza, queda al ras)
    for sx in (-1, 1):
        for sy in (-1, 1):
            t = t.cut(cq.Workplane("YZ").circle(tornillo_d / 2).extrude(tapa_pared + 2 * tapa_holgura + 2)
                      .translate((sx * (il / 2 - 1) if sx < 0 else il / 2 - 1, sy * tapa_torn_y, z_tope - tapa_torn_bajo_tope)))
            t = t.cut(cq.Workplane("YZ").circle(3.2).extrude(1.2)
                      .translate((sx * ol / 2 - (1.2 if sx > 0 else 0) + (0.01 if sx > 0 else -0.01), sy * tapa_torn_y, z_tope - tapa_torn_bajo_tope)))
    # Muesca del cable USB-C (extremo -X) y muesca para el dedo (extremo +X)
    t = t.cut(cq.Workplane("XY").box(tapa_pared * 3, tapa_cable_ancho, tapa_falda + 0.5, centered=(True, True, False))
              .translate((-ol / 2, 0, z0 - 1)))
    t = t.cut(cq.Workplane("XZ").circle(7).extrude(tapa_pared * 3, both=True).translate((ol / 2, 0, z0)))
    # Aspecto de pieza de moto: nervaduras diagonales y FT150TS grabado en el techo
    techo = z0 + alto_int + tapa_techo
    for i in range(-6, 7):
        if abs(i) <= 1: continue
        t = t.cut(cq.Workplane("XY").rect(2.0, oa * 1.6).extrude(0.8).rotate((0, 0, 0), (0, 0, 1), 35)
                  .translate((i * 12.0, 0, techo - 0.8)).intersect(
                      rect_redondeado(ol - 16, oa - 16, radio_exterior, 2, z0=techo - 1)))
    t = t.cut(cq.Workplane("XY").text("FT150TS", 11, 0.8, kind="bold").translate((0, 0, techo - 0.8)))
    return t


def calibracion(h):
    """Anillo de prueba: solo el perímetro del hueco (con costillas), 4 mm de alto. ~10 g de filamento."""
    l = tel_largo + 2 * h; a = tel_ancho + 2 * h
    ext = rect_redondeado(l + 2 * pared, a + 2 * pared, tel_radio_esquina + h + pared, 4.0)
    inn = rect_redondeado(l, a, tel_radio_esquina + h, 6.0, z0=-1)
    anillo = ext.cut(inn)
    for sy in (-1, 1):
        for xc in (-l / 3, 0.0, l / 3):
            anillo = anillo.union(cq.Workplane("XY").box(costilla_largo, costilla_alto * 2, 3.0)
                                  .translate((xc, sy * a / 2, 2.0)))
    return anillo.union(cq.Workplane("XY").text(f"{h:.2f}", 5, 0.6).translate((0, -a / 2 - pared / 2, 4.0)))


# ============================================================
# EXPORTAR
# ============================================================
if __name__ == "__main__":
    os.makedirs("stl", exist_ok=True)
    piezas = {
        "bandeja": bandeja(),
        "marco": marco().rotate((0, 0, 0), (1, 0, 0), 180),                   # cara frontal en la cama
        "socket_fijo": socket_fijo().rotate((0, 0, 0), (1, 0, 0), 180),       # placa en la cama
        "socket_movil": socket_movil().rotate((0, 0, 0), (0, 1, 0), -90),     # cara de la abertura en la cama
        "perilla": perilla(),
        "tapa": tapa().rotate((0, 0, 0), (1, 0, 0), 180),                     # techo en la cama
    }
    for h in calibraciones:
        piezas[f"calibracion_{int(round(h * 100)):02d}"] = calibracion(h)
    for nombre, pieza in piezas.items():
        cq.exporters.export(pieza, f"stl/{nombre}.stl", tolerance=0.01, angularTolerance=0.1)
        bb = pieza.val().BoundingBox()
        print(f"{nombre}: {bb.xlen:.1f} x {bb.ylen:.1f} x {bb.zlen:.1f} mm")
    print(f"socket: palanca x{ventaja:.1f} (fuerza sobre la bola / fuerza del tornillo)")
    # AutoCAD (IMPORT): cada pieza en su POSICIÓN DE ENSAMBLE (insertadas todas en 0,0,0 quedan armadas; antes el
    # socket móvil salía en su pose de impresión, encima del marco) + el ensamble con cada pieza nombrada y de color
    os.makedirs("autocad", exist_ok=True)
    ensamble = {"bandeja": (bandeja(), "gray"), "marco": (marco(), "orange"), "socket_fijo": (socket_fijo(), "blue"),
                "socket_movil": (socket_movil(), "green"), "perilla": (perilla_en_ensamble(), "red"), "tapa": (tapa(), "yellow")}
    asm = cq.Assembly(name="carcasa_poco_f4")
    for nombre, (pieza, color) in ensamble.items():
        cq.exporters.export(pieza, f"autocad/{nombre}.step")
        asm.add(pieza, name=nombre, color=cq.Color(color))
    asm.save("autocad/ensamble_completo.step")
    # Anillos de calibración (para ver en AutoCAD cuál probar; se imprimen desde stl/)
    for h in calibraciones:
        cq.exporters.export(calibracion(h), f"autocad/calibracion_{int(round(h * 100)):02d}.step")
    # Ensamble (para ver cómo queda)
    ens = bandeja().union(marco()).union(socket_fijo()).union(socket_movil())
    cq.exporters.export(ens, "stl/ensamble_vista.stl", tolerance=0.05, angularTolerance=0.2)
