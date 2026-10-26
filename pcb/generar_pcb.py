"""
Genera las dos placas FT150TS a partir de diseno.py (se corre con el Python de KiCad 10):

  python generar_pcb.py A     placa de una cara para plancha (pistas abajo + puentes de alambre)
  python generar_pcb.py B     placa de dos caras para JLCPCB

Pasos: piezas grandes en su lugar fijo (ANCLAS) -> piezas chicas acomodadas solas junto a lo que
conectan, sin encimarse -> contorno, barrenos y serigrafía -> Freerouting -> plano de tierra.
"""
import math
import os
import re
import subprocess
import sys

import importlib

import pcbnew

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
VARIANTE = (sys.argv[1] if len(sys.argv) > 1 else "A").upper()
D = importlib.import_module("diseno_C" if VARIANTE == "C" else "diseno")

KICAD = os.path.dirname(os.path.dirname(sys.executable))
HUELLAS = os.path.join(KICAD, "share", "kicad", "footprints")
JAVA25 = r"C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
FREEROUTING = os.path.expanduser(r"~\tools\freerouting\freerouting-2.5.0.jar")
mm = pcbnew.FromMM

# ---------------------------------------------------------------- piezas fijas (x, y, rotación)
# Origen = pad 1. Rotación 90: los pines van hacia +x; 270: hacia -x. Borneras con rotación 0
# reciben el cable por abajo, 180 por arriba, 270 por la izquierda y 90 por la derecha.
# DevKit horizontal: USB a la izquierda (x~21), antena a la derecha. J1 arriba, J3 abajo.
FILA_J1, FILA_J3, PIN1_X = 33.0, 58.40, 82.0      # filas a 25.40 mm (copia ancha del DevKit)
ANCLAS = {
    "U1A": (PIN1_X, FILA_J1, 270), "U1B": (PIN1_X, FILA_J3, 270),
    # Orilla de arriba: tiras de módulos y borneras de señales de la fila J1
    "J15": (9.0, 3.5, 90), "J12": (25.3, 3.5, 90), "J13": (36.6, 3.5, 90), "J9": (2.5, 9.0, 0),
    "J6": (66.5, 6.0, 180), "J4": (78.0, 6.0, 180), "J7": (83.0, 3.5, 90),
    "J14": (97.0, 10.0, 0), "J11": (97.0, 21.5, 0),
    # Derecha, junto a la antena: GPS, LEDs y salida aislada
    "J16": (97.0, 30.0, 0), "J17": (97.0, 42.0, 0), "J19": (94.5, 56.0, 90),
    # Abajo a la derecha: los 3 relevadores con su bornera en la orilla y su optoacoplador arriba
    "K3": (35.0, 79.0, 0), "K2": (56.0, 79.0, 0), "K1": (77.0, 79.0, 0),
    "J73": (36.0, 94.0, 0), "J72": (57.0, 94.0, 0), "J71": (78.0, 94.0, 0),
    "U73": (36.0, 62.0, 0), "U72": (57.0, 62.0, 0), "U71": (78.0, 62.0, 0),
    "Q73": (46.5, 63.0, 0), "Q72": (67.5, 63.0, 0), "Q71": (88.5, 63.0, 0),
    "D73": (45.0, 68.5, 0), "D72": (66.0, 68.5, 0), "D71": (87.0, 68.5, 0),
    "LED3": (38.0, 68.5, 0), "LED2": (59.0, 68.5, 0), "LED1": (80.0, 68.5, 0),
    # Abajo a la izquierda: fuente y borneras de la moto (fuera del paso del cable USB, y=37..54)
    "J1": (6.0, 58.0, 270), "J2": (5.5, 69.0, 270), "J3": (5.5, 81.0, 270),
    "J8": (9.8, 94.5, 0), "J10": (19.0, 94.5, 0), "J18": (6.0, 30.1, 270),
    "J5": (5.5, 21.0, 270),
    # Corte de chispa junto a su bornera J18 (orilla izquierda): MOSFET y diodo pegados
    "Q9": (14.1, 28.0, 90), "D8": (18.0, 26.0, 90),
    # Fuente LM2596 junto a la entrada de 12 V
    "U2": (13.0, 61.0, 0), "L1": (16.6, 79.0, 0), "D7": (16.0, 70.6, 0), "C1": (27.0, 64.9, 0), "C3": (27.0, 74.4, 0),
    # RPM junto a su bornera (arriba a la derecha)
    "R9": (91.0, 12.5, 270), "U9": (79.5, 15.0, 0),
}
if VARIANTE == "C":
    # Placa C (140 x 105): DevKit arriba al centro, módulos LM2596 y TP4056 abajo a la izquierda
    FILA_J1, FILA_J3, PIN1_X = 35.0, 57.86, 100.0
    ANCLAS = {
        "U1A": (PIN1_X, FILA_J1, 270), "U1B": (PIN1_X, FILA_J3, 270),
        "J15": (10.0, 3.5, 90), "J12": (28.0, 3.5, 90), "J13": (41.0, 3.5, 90),
        "M6": (54.0, 3.5, 90), "M7": (67.0, 3.5, 90),
        "J6": (104.0, 6.0, 180), "J4": (116.5, 6.0, 180), "J7": (121.5, 3.5, 90),
        "J9": (2.5, 9.0, 0), "J5": (5.5, 21.0, 270), "J18": (6.0, 30.1, 270), "U15": (20.6, 34.08, 180),
        "J14": (137.5, 12.0, 0), "J11": (137.5, 24.0, 0), "J16": (137.5, 32.0, 0), "J17": (137.5, 44.0, 0),
        "M3": (78.5, 3.5, 90), "M4": (120.0, 18.0, 0),
        "M5": (110.0, 101.5, 90),
        "M1": (3.0, 70.0, 0), "M2": (50.0, 76.0, 0),      # esquina superior izquierda del módulo
        "J1": (10.5, 99.5, 0), "J2": (78.0, 100.0, 0), "J10": (88.0, 100.0, 0), "J8": (98.0, 100.0, 0),
    }

# Zonas prohibidas: el paso del cable USB (para todo) y bajo el DevKit solo para piezas altas
# (el DevKit queda a ~8.5 mm sobre la placa por las tiras hembra: caben resistencias, diodos,
# optos DIP, TO-92 y capacitores de disco, no electrolíticos ni relevadores)
PROHIBIDO = [(0.0, FILA_J1 + 4.0, PIN1_X - 61.0, FILA_J3 - 1.8)] + list(getattr(D, "ZONAS_MODULO", {}).values())
BAJO_DEVKIT = (PIN1_X - 62.5, FILA_J1 + 1.3, PIN1_X + 5.0, FILA_J3 - 1.3)
ALTAS = ("CP_Radial", "TO-220", "Relay", "P2.54mm_Vertical", "TerminalBlock", "PinHeader", "PinSocket", "Fuse", "L_Radial",
         "TO-126", "DO-201")
MARGEN = 0.35           # separación mínima entre patios (mm)
PESO_RED = {"GND": 0.0, "+3V3": 0.25, "+5V": 0.4, "+5V_MOTO": 0.8, "+12V": 1.0}


def cargar(huella):
    lib, nombre = huella.split(":")
    carpeta = os.path.join(AQUI, lib + ".pretty") if lib == "FT150TS" else os.path.join(HUELLAS, lib + ".pretty")
    fp = pcbnew.FootprintLoad(carpeta, nombre)
    if fp is None:
        raise SystemExit("No encontré la huella " + huella)
    return fp


def linea(board, capa, a, b, ancho=0.15):
    s = pcbnew.PCB_SHAPE(board)
    s.SetShape(pcbnew.SHAPE_T_SEGMENT)
    s.SetStart(pcbnew.VECTOR2I_MM(*a)); s.SetEnd(pcbnew.VECTOR2I_MM(*b))
    s.SetLayer(capa); s.SetWidth(mm(ancho))
    board.Add(s)


def texto(board, capa, t, x, y, alto=1.2, espejo=False):
    tx = pcbnew.PCB_TEXT(board)
    tx.SetText(t); tx.SetPosition(pcbnew.VECTOR2I_MM(x, y)); tx.SetLayer(capa)
    tx.SetTextSize(pcbnew.VECTOR2I_MM(alto, alto)); tx.SetTextThickness(mm(alto * 0.15))
    tx.SetMirrored(espejo)
    board.Add(tx)


def geometria(fp, rot):
    """Patio (caja) y pads relativos al origen para una rotación."""
    fp.SetPosition(pcbnew.VECTOR2I_MM(0, 0))
    fp.SetOrientationDegrees(rot)
    pat = fp.GetCourtyard(pcbnew.F_CrtYd)
    r = pat.BBox() if not pat.IsEmpty() else fp.GetBoundingBox(False)
    caja = tuple(pcbnew.ToMM(v) for v in (r.GetLeft(), r.GetTop(), r.GetRight(), r.GetBottom()))
    pads = [(p.GetNumber(), pcbnew.ToMM(p.GetPosition().x), pcbnew.ToMM(p.GetPosition().y)) for p in fp.Pads()]
    return caja, pads


def acomodar(board, fps, pines_de):
    """Piezas chicas: cada una donde quede más cerca de lo que conecta, sin encimarse."""
    W, H = D.ANCHO, D.ALTO
    cajas = []                     # (x0, y0, x1, y1) ya ocupadas
    pads_red = {}                  # red -> [(x, y)]

    def ocupar(ref, x, y, rot):
        caja, pads = geometria(fps[ref], rot)
        cajas.append((x + caja[0], y + caja[1], x + caja[2], y + caja[3]))
        for num, px, py in pads:
            red = pines_de[ref].get(int(num)) if num.isdigit() else None
            if red:
                pads_red.setdefault(red, []).append((x + px, y + py))
        fps[ref].SetPosition(pcbnew.VECTOR2I_MM(x, y))
        fps[ref].SetOrientationDegrees(rot)

    for b in board.GetFootprints():
        if b.GetReference().startswith("H"):
            r = b.GetBoundingBox(False)
            cajas.append(tuple(pcbnew.ToMM(v) for v in (r.GetLeft(), r.GetTop(), r.GetRight(), r.GetBottom())))
    for ref, (x, y, rot) in ANCLAS.items():
        ocupar(ref, x, y, rot)
    cajas.extend(PROHIBIDO)

    pendientes = [r for r in fps if r not in ANCLAS]
    geo = {r: {rot: geometria(fps[r], rot) for rot in (0, 90, 180, 270)} for r in pendientes}

    # Lado de cada pieza: lo que cuelga de un GPIO de la fila de arriba (J1) va arriba o bajo el
    # DevKit; lo de la fila de abajo (J3) va abajo. Entre pines del DevKit no cabe una pista, así
    # que una pieza del lado equivocado queda imposible de rutear. Se propaga por las cadenas
    # (GPIO -> opto -> transistor -> relevador), sin pasar por las redes de alimentación.
    alim = {"GND", "+3V3", "+5V", "+5V_MOTO", "+12V", "+12V_IN", "+12V_F", "CELDA"}
    lado_red = {n: "J1" for n in D.J1 if n and n not in alim}
    lado_red.update({n: "J3" for n in D.J3 if n and n not in alim})
    lado = {}
    cambio = True
    while cambio:
        cambio = False
        for r in pendientes:
            redes = [n for n in pines_de[r].values() if n not in alim]
            ls = {lado_red[n] for n in redes if n in lado_red}
            if len(ls) == 1 and r not in lado:
                lado[r] = ls.pop()
                cambio = True
            if r in lado:
                for n in redes:
                    if n not in lado_red:
                        lado_red[n] = lado[r]
                        cambio = True
    fila_j3_max = max(y for ref, (x, y, rot) in ANCLAS.items() if ref in ("U1B", "U1C"))

    def libre(c, alta=False):
        if alta:
            o = BAJO_DEVKIT
            if c[0] < o[2] and c[2] > o[0] and c[1] < o[3] and c[3] > o[1]:
                return False
        if c[0] < 0.5 or c[1] < 0.5 or c[2] > W - 0.5 or c[3] > H - 0.5:
            return False
        for o in cajas:
            if c[0] < o[2] + MARGEN and c[2] > o[0] - MARGEN and c[1] < o[3] + MARGEN and c[3] > o[1] - MARGEN:
                return False
        return True

    def costo(ref, x, y, rot):
        total = 0.0
        for num, px, py in geo[ref][rot][1]:
            red = pines_de[ref].get(int(num)) if num.isdigit() else None
            if not red or red not in pads_red:
                continue
            w = PESO_RED.get(red, 1.0)
            if w == 0:
                continue
            ax, ay = x + px, y + py
            total += w * min(math.hypot(ax - bx, ay - by) for bx, by in pads_red[red])
        return total

    def enganche(r):
        return sum(1 for red in pines_de[r].values() if red in pads_red and PESO_RED.get(red, 1) > 0)

    faltan = []
    while pendientes:
        # La pieza con más conexiones a lo ya puesto va primero
        pendientes.sort(key=lambda r: (-enganche(r), r))
        ref = pendientes.pop(0)
        objetivo = [p for red in pines_de[ref].values() if PESO_RED.get(red, 1) > 0 for p in pads_red.get(red, [])]
        cx = sum(p[0] for p in objetivo) / len(objetivo) if objetivo else W / 2
        cy = sum(p[1] for p in objetivo) / len(objetivo) if objetivo else H / 2
        mejor = None
        for radio in (12, 22, 35, 60, 140):
            paso = 1.27
            n = int(radio / paso)
            for i in range(-n, n + 1):
                for j in range(-n, n + 1):
                    x, y = round(cx + i * paso, 2), round(cy + j * paso, 2)
                    for rot in (0, 90, 180, 270):
                        c = geo[ref][rot][0]
                        caja = (x + c[0], y + c[1], x + c[2], y + c[3])
                        if not libre(caja, any(a in fps[ref].GetFPIDAsString() for a in ALTAS)):
                            continue
                        en_punta = caja[2] < PIN1_X - 62.5 or caja[0] > PIN1_X + 3.0   # a los lados del DevKit
                        if lado.get(ref) == "J3" and caja[1] < FILA_J1 + 1.3 and not en_punta:
                            continue          # arriba de la fila J1: inalcanzable desde J3
                        if lado.get(ref) == "J1" and caja[3] > fila_j3_max - 1.3 and not en_punta:
                            continue          # abajo de las filas J3: inalcanzable desde J1
                        k = costo(ref, x, y, rot) + 0.02 * math.hypot(x - cx, y - cy)
                        if mejor is None or k < mejor[0]:
                            mejor = (k, x, y, rot)
            if mejor:
                break
        if not mejor:
            faltan.append(ref)
            continue
        ocupar(ref, mejor[1], mejor[2], mejor[3])
    if faltan:
        print("NO CUPIERON:", " ".join(faltan))
    return faltan


def rotular(board, fps):
    """Nombre de cada conector en la serigrafía, del lado contrario a la orilla más cercana."""
    W, H = D.ANCHO, D.ALTO
    for ref, txt in getattr(D, "ETIQUETAS", {}).items():
        if ref not in fps:
            continue
        r = fps[ref].GetCourtyard(pcbnew.F_CrtYd).BBox()
        x0, y0, x1, y1 = (pcbnew.ToMM(v) for v in (r.GetLeft(), r.GetTop(), r.GetRight(), r.GetBottom()))
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        d = {"arriba": y0, "abajo": H - y1, "izq": x0, "der": W - x1}
        cerca = min(d, key=d.get)
        t = pcbnew.PCB_TEXT(board)
        t.SetText(txt)
        t.SetLayer(pcbnew.F_SilkS)
        t.SetTextSize(pcbnew.VECTOR2I_MM(0.9, 0.9))
        t.SetTextThickness(mm(0.14))
        if cerca == "arriba":
            t.SetPosition(pcbnew.VECTOR2I_MM(cx, y1 + 0.9))
        elif cerca == "abajo":
            t.SetPosition(pcbnew.VECTOR2I_MM(cx, y0 - 0.9))
        elif cerca == "izq":
            t.SetPosition(pcbnew.VECTOR2I_MM(x1 + 0.9, cy)); t.SetTextAngleDegrees(90)
        else:
            t.SetPosition(pcbnew.VECTOR2I_MM(x0 - 0.9, cy)); t.SetTextAngleDegrees(90)
        board.Add(t)


def construir(variante):
    board = pcbnew.CreateEmptyBoard()
    board.SetCopperLayerCount(2)
    redes, fps, pines_de = {}, {}, {}

    def red(n):
        if n not in redes:
            ni = pcbnew.NETINFO_ITEM(board, n)
            board.Add(ni)
            redes[n] = ni
        return redes[n]

    for i, (bx, by) in enumerate(D.BARRENOS):
        fp = cargar("MountingHole:MountingHole_3.2mm_M3")
        fp.SetReference(f"H{i + 1}")
        fp.SetPosition(pcbnew.VECTOR2I_MM(bx, by))
        board.Add(fp)

    for ref, huella, valor, pines in D.PIEZAS:
        fp = cargar(huella)
        fp.SetReference(ref)
        fp.SetValue(valor)
        board.Add(fp)
        fps[ref] = fp
        pines_de[ref] = pines
    acomodar(board, fps, pines_de)
    rotular(board, fps)
    # Referencias chicas para que no se encimen en la serigrafía
    for fp in fps.values():
        t = fp.Reference()
        t.SetTextSize(pcbnew.VECTOR2I_MM(0.8, 0.8))
        t.SetTextThickness(mm(0.12))
        if fp.GetReference() in getattr(D, "ETIQUETAS", {}):
            t.SetVisible(False)      # el conector ya lleva su nombre en la serigrafía
    for ref, fp in fps.items():
        for pad in fp.Pads():
            num = pad.GetNumber()
            if num.isdigit() and int(num) in pines_de[ref]:
                pad.SetNet(red(pines_de[ref][int(num)]))

    # Contorno con esquinas redondeadas
    w, h, r = D.ANCHO, D.ALTO, D.RADIO_ESQUINA
    E = pcbnew.Edge_Cuts
    linea(board, E, (r, 0), (w - r, 0)); linea(board, E, (w, r), (w, h - r))
    linea(board, E, (w - r, h), (r, h)); linea(board, E, (0, h - r), (0, r))
    for cx, cy, a0 in ((r, r, 180), (w - r, r, 270), (w - r, h - r, 0), (r, h - r, 90)):
        arco = pcbnew.PCB_SHAPE(board)
        arco.SetShape(pcbnew.SHAPE_T_ARC)
        arco.SetCenter(pcbnew.VECTOR2I_MM(cx, cy))
        arco.SetStart(pcbnew.VECTOR2I_MM(cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0))))
        arco.SetArcAngleAndEnd(pcbnew.EDA_ANGLE(90, pcbnew.DEGREES_T), True)
        arco.SetLayer(E); arco.SetWidth(mm(0.15))
        board.Add(arco)

    # Serigrafía: orientación del DevKit (al revés, los 5 V llegarían a otros pines)
    S = pcbnew.F_SilkS
    centro = (FILA_J1 + FILA_J3) / 2
    texto(board, S, "< USB   ESP32-S3-DevKitC-1", PIN1_X - 37.0, centro - 3, 1.5)
    texto(board, S, "3V3", PIN1_X, FILA_J1 - 2.6, 1.0)
    texto(board, S, "ANTENA >", PIN1_X - 6.0, centro, 1.2)
    texto(board, S, "FT150TS base v2 - " + {"A": "A una cara", "B": "B JLCPCB", "C": "C modulos"}[variante],
          PIN1_X - 37.0, centro + 3, 1.2)
    for nombre, (x0, y0, x1, y1) in getattr(D, "ZONAS_MODULO", {}).items():
        for a_, b_ in (((x0, y0), (x1, y0)), ((x1, y0), (x1, y1)), ((x1, y1), (x0, y1)), ((x0, y1), (x0, y0))):
            linea(board, S, a_, b_, 0.2)
        texto(board, S, "MODULO " + nombre, (x0 + x1) / 2, (y0 + y1) / 2, 1.2)

    # Clases de red
    ds = board.GetDesignSettings()
    ns = ds.m_NetSettings
    a = variante in ("A", "C")
    defecto = ns.GetDefaultNetclass()
    defecto.SetTrackWidth(mm(D.PISTA if a else 0.4))
    defecto.SetClearance(mm(D.SEPARACION if a else 0.25))
    defecto.SetViaDiameter(mm(1.2 if a else 0.8)); defecto.SetViaDrill(mm(0.6 if a else 0.4))
    for nombre, ancho, sep, nets in (("Potencia", D.PISTA_POTENCIA, D.SEPARACION if a else 0.3, D.REDES_POTENCIA),
                                     ("AltoVoltaje", D.PISTA, D.SEPARACION_AV, D.REDES_ALTO_VOLTAJE),
                                     ("Drenaje", D.PISTA, 0.6, getattr(D, "REDES_DRENAJE", []))):
        nc = pcbnew.NETCLASS(nombre)
        nc.SetTrackWidth(mm(ancho)); nc.SetClearance(mm(sep))
        nc.SetViaDiameter(mm(1.2 if a else 0.8)); nc.SetViaDrill(mm(0.6 if a else 0.4))
        ns.SetNetclass(nombre, nc)
        for n in nets:
            ns.SetNetclassPatternAssignment(n, nombre)
    ds.m_CopperEdgeClearance = mm(0.5)
    board.SynchronizeNetsAndNetClasses(True)
    return board


REGLAS_A = """(rules PCB ft150ts_{v}
  (snap_angle fortyfive_degree)
  (autoroute_settings
    (fanout off) (autoroute on) (postroute on) (vias on)
    (via_costs {via}) (plane_via_costs 5) (start_ripup_costs 100) (start_pass_no 1)
    (layer_rule F.Cu (active on) (preferred_direction horizontal)
      (preferred_direction_trace_costs {arriba}) (against_preferred_direction_trace_costs {arriba}))
    (layer_rule B.Cu (active on) (preferred_direction vertical)
      (preferred_direction_trace_costs 1.0) (against_preferred_direction_trace_costs 1.2))
  )
)
"""


def rutear(board, variante, arriba=60.0):
    """A: abajo, y arriba (F.Cu) solo donde no hay salida, con costo alto: cada tramo de arriba
    es un puente de alambre. B: dos caras normales."""
    base = os.path.join(AQUI, "ruteo", f"ft150ts_{variante}")
    os.makedirs(os.path.dirname(base), exist_ok=True)
    if not pcbnew.ExportSpecctraDSN(board, base + ".dsn"):
        raise SystemExit("No se pudo exportar el DSN")
    # Freerouting no sabe del margen al borde: le doy un contorno 0.6 mm más chico
    t = open(base + ".dsn", encoding="utf-8").read()
    m = re.search(r"\(boundary\s*\(path pcb 0 ([^)]*)\)\s*\)", t)
    pts = [float(v) for v in m.group(1).split()]
    xs, ys = pts[0::2], pts[1::2]
    a = 600.0      # um
    x0, x1, y0, y1 = min(xs) + a, max(xs) - a, min(ys) + a, max(ys) - a
    rect = f"(boundary (path pcb 0 {x0} {y0} {x1} {y0} {x1} {y1} {x0} {y1} {x0} {y0}))"
    t = t[:m.start()] + rect + t[m.end():]
    open(base + ".dsn", "w", encoding="utf-8").write(t)
    cmd = [JAVA25, "-jar", FREEROUTING, "-de", base + ".dsn", "-do", base + ".ses", "-mp", "25", "-l", "en"]
    if variante in ("A", "C"):          # una cara: con límite de tiempo (si no, se atora)
        cmd[-2:-2] = ["--router.job_timeout=00:12:00"]
    else:                               # dos caras: más pasadas para cerrar todo
        cmd[cmd.index("-mp") + 1] = "60"
    if variante in ("A", "C"):
        open(base + ".rules", "w", encoding="utf-8").write(REGLAS_A.format(v=variante, via=int(arriba * 8), arriba=arriba))
        cmd[5:5] = ["-dr", base + ".rules"]
    print(f"Ruteando placa {variante}...", flush=True)
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)
    res = re.findall(r"Auto-routing stage completed.*?\((\d+) unrouted", r.stdout + r.stderr)
    print("Sin rutear:", res[-1] if res else "?")
    if not os.path.exists(base + ".ses"):
        print(r.stdout[-3000:], r.stderr[-3000:])
        raise SystemExit("Freerouting no generó la sesión")
    if not pcbnew.ImportSpecctraSES(board, base + ".ses"):
        raise SystemExit("No se pudo importar la sesión")


def puentes(board):
    """Placa A: cada cadena de pistas en F.Cu es un puente de alambre (P1, P2...). Se dibuja en la
    serigrafía de arriba para saber dónde va."""
    tramos = [t for t in board.GetTracks() if t.GetLayer() == pcbnew.F_Cu and t.GetClass() == "PCB_TRACK"]
    clave = lambda v: (round(pcbnew.ToMM(v.x), 2), round(pcbnew.ToMM(v.y), 2))
    padre = {}

    def raiz(k):
        while padre.setdefault(k, k) != k:
            k = padre[k]
        return k

    for t in tramos:
        padre[raiz(clave(t.GetStart()))] = raiz(clave(t.GetEnd()))
    grupos = {}
    for t in tramos:
        grupos.setdefault(raiz(clave(t.GetStart())), []).append(t)
    lista = []
    for n, (_, ts) in enumerate(sorted(grupos.items()), start=1):
        largo = 0.0
        for t in ts:
            linea(board, pcbnew.F_SilkS, clave(t.GetStart()), clave(t.GetEnd()), 0.3)
            largo += pcbnew.ToMM(t.GetLength())
        mx, my = clave(ts[len(ts) // 2].GetStart())
        texto(board, pcbnew.F_SilkS, f"P{n}", mx, my - 1.2, 1.0)
        lista.append((f"P{n}", ts[0].GetNetname(), round(largo, 1)))
    return lista


def plano_tierra(board, capas):
    for capa in capas:
        z = pcbnew.ZONE(board)
        z.SetLayer(capa)
        z.SetNet(board.FindNet("GND"))
        z.SetLocalClearance(mm(0.6))
        z.SetMinThickness(mm(0.5))
        z.SetPadConnection(pcbnew.ZONE_CONNECTION_THERMAL)
        z.SetThermalReliefGap(mm(0.5))
        z.SetThermalReliefSpokeWidth(mm(0.6))
        o = z.Outline()
        o.NewOutline()
        for x, y in ((0.5, 0.5), (D.ANCHO - 0.5, 0.5), (D.ANCHO - 0.5, D.ALTO - 0.5), (0.5, D.ALTO - 0.5)):
            o.Append(mm(x), mm(y))
        board.Add(z)
    pcbnew.ZONE_FILLER(board).Fill(board.Zones())


if __name__ == "__main__":
    variante = VARIANTE
    salida = os.path.join(AQUI, f"ft150ts_{variante}.kicad_pcb")
    b = construir(variante)
    if "--rutear" in sys.argv:
        costo = float(sys.argv[sys.argv.index("--costo") + 1]) if "--costo" in sys.argv else 60.0
        rutear(b, variante, costo)
        if variante in ("A", "C"):
            lista = puentes(b)
            print(f"Puentes de alambre: {len(lista)}")
            for p, red_, largo in lista:
                print(f"  {p}: {red_} ({largo} mm)")
            plano_tierra(b, [pcbnew.B_Cu])
        else:
            plano_tierra(b, [pcbnew.B_Cu, pcbnew.F_Cu])
    b.Save(salida)
    print("Guardado:", salida)
