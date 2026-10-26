"""
Genera las placas extra (sin ESP32) a partir de su diseno_X.py, las rutea y saca los archivos de JLCPCB.
Se corre con el Python de KiCad 10:

  python generar_extra.py E      expansión I2C (3 relevadores, 4 entradas, 1 salida)
  python generar_extra.py F      distribución con fusibles

Pasos: piezas grandes en su lugar (ANCLAS) -> piezas chicas junto a lo que conectan -> contorno,
barrenos y serigrafía -> Freerouting (2 caras) -> plano de tierra -> DRC -> gerbers + taladros (zip).
"""
import importlib
import math
import os
import re
import shutil
import subprocess
import sys
import zipfile

import pcbnew

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
X = (sys.argv[1] if len(sys.argv) > 1 else "E").upper()
D = importlib.import_module("diseno_" + X)
NOMBRE = f"ft150ts_{X}"

KICAD = os.path.dirname(os.path.dirname(sys.executable))
HUELLAS = os.path.join(KICAD, "share", "kicad", "footprints")
CLI = os.path.join(KICAD, "bin", "kicad-cli.exe")
JAVA25 = r"C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
FREEROUTING = os.path.expanduser(r"~\tools\freerouting\freerouting-2.5.0.jar")
mm = pcbnew.FromMM
MARGEN = 0.4


def cargar(huella):
    lib, nombre = huella.split(":")
    fp = pcbnew.FootprintLoad(os.path.join(HUELLAS, lib + ".pretty"), nombre)
    if fp is None:
        raise SystemExit("No encontré la huella " + huella)
    return fp


def linea(board, capa, a, b, ancho=0.15):
    s = pcbnew.PCB_SHAPE(board)
    s.SetShape(pcbnew.SHAPE_T_SEGMENT)
    s.SetStart(pcbnew.VECTOR2I_MM(*a)); s.SetEnd(pcbnew.VECTOR2I_MM(*b))
    s.SetLayer(capa); s.SetWidth(mm(ancho))
    board.Add(s)


def texto(board, capa, t, x, y, alto=1.2, ang=0):
    s = pcbnew.PCB_TEXT(board)
    s.SetText(t); s.SetLayer(capa)
    s.SetTextSize(pcbnew.VECTOR2I_MM(alto, alto)); s.SetTextThickness(mm(max(0.15, alto * 0.15)))
    s.SetPosition(pcbnew.VECTOR2I_MM(x, y)); s.SetTextAngleDegrees(ang)
    board.Add(s)


def geometria(fp, rot):
    """Caja del patio y pads relativos al pad 1 con la rotación dada."""
    fp.SetPosition(pcbnew.VECTOR2I_MM(0, 0))
    fp.SetOrientationDegrees(rot)
    r = fp.GetCourtyard(pcbnew.F_CrtYd).BBox()
    caja = tuple(pcbnew.ToMM(v) for v in (r.GetLeft(), r.GetTop(), r.GetRight(), r.GetBottom()))
    pads = [(p.GetNumber(), pcbnew.ToMM(p.GetPosition().x), pcbnew.ToMM(p.GetPosition().y)) for p in fp.Pads()]
    return caja, pads


def acomodar(fps, pines_de):
    W, H = D.ANCHO, D.ALTO
    cajas = [(bx - 3.6, by - 3.6, bx + 3.6, by + 3.6) for bx, by in D.BARRENOS] + list(getattr(D, "RESERVADO", []))
    pads_red = {}

    def ocupar(ref, x, y, rot):
        caja, pads = geometria(fps[ref], rot)
        cajas.append((x + caja[0], y + caja[1], x + caja[2], y + caja[3]))
        for num, px, py in pads:
            red = pines_de[ref].get(int(num)) if num.isdigit() else None
            if red:
                pads_red.setdefault(red, []).append((x + px, y + py))
        fps[ref].SetPosition(pcbnew.VECTOR2I_MM(x, y))
        fps[ref].SetOrientationDegrees(rot)

    for ref, (x, y, rot) in D.ANCLAS.items():
        ocupar(ref, x, y, rot)
    pendientes = [r for r in fps if r not in D.ANCLAS]
    geo = {r: {rot: geometria(fps[r], rot) for rot in (0, 90, 180, 270)} for r in pendientes}

    def libre(c):
        if c[0] < 2.5 or c[1] < 2.5 or c[2] > W - 2.5 or c[3] > H - 2.5:
            return False
        return not any(c[0] < o[2] + MARGEN and c[2] > o[0] - MARGEN and c[1] < o[3] + MARGEN and c[3] > o[1] - MARGEN
                       for o in cajas)

    def costo(ref, x, y, rot):
        total = 0.0
        for num, px, py in geo[ref][rot][1]:
            red = pines_de[ref].get(int(num)) if num.isdigit() else None
            if not red or red not in pads_red or red == "GND":
                continue
            w = 0.4 if red.startswith("+") else 1.0
            total += w * min(math.hypot(x + px - bx, y + py - by) for bx, by in pads_red[red])
        return total

    faltan = []
    while pendientes:
        pendientes.sort(key=lambda r: (-sum(1 for n in pines_de[r].values() if n in pads_red and n != "GND"), r))
        ref = pendientes.pop(0)
        obj = [p for n in pines_de[ref].values() if n != "GND" and not n.startswith("+") for p in pads_red.get(n, [])]
        obj = obj or [p for n in pines_de[ref].values() if n != "GND" for p in pads_red.get(n, [])]
        cx = sum(p[0] for p in obj) / len(obj) if obj else W / 2
        cy = sum(p[1] for p in obj) / len(obj) if obj else H / 2
        mejor = None
        for radio in (10, 20, 35, 60, 120):
            n = int(radio / 1.27)
            for i in range(-n, n + 1):
                for j in range(-n, n + 1):
                    x, y = round(cx + i * 1.27, 2), round(cy + j * 1.27, 2)
                    for rot in (0, 90, 180, 270):
                        c = geo[ref][rot][0]
                        if not libre((x + c[0], y + c[1], x + c[2], y + c[3])):
                            continue
                        k = costo(ref, x, y, rot) + 0.05 * math.hypot(x - cx, y - cy)
                        if mejor is None or k < mejor[0]:
                            mejor = (k, x, y, rot)
            if mejor:
                break
        if not mejor:
            faltan.append(ref)
            continue
        ocupar(ref, *mejor[1:])
    if faltan:
        raise SystemExit("NO CUPIERON: " + " ".join(faltan))


def rotular(board, fps):
    W, H = D.ANCHO, D.ALTO
    for ref, txt in D.ETIQUETAS.items():
        r = fps[ref].GetCourtyard(pcbnew.F_CrtYd).BBox()
        x0, y0, x1, y1 = (pcbnew.ToMM(v) for v in (r.GetLeft(), r.GetTop(), r.GetRight(), r.GetBottom()))
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        d = {"arriba": y0, "abajo": H - y1, "izq": x0, "der": W - x1}
        cerca = min(d, key=d.get)
        if cerca == "arriba":
            texto(board, pcbnew.F_SilkS, txt, cx, y1 + 0.9, 0.9)
        elif cerca == "abajo":
            texto(board, pcbnew.F_SilkS, txt, cx, y0 - 0.9, 0.9)
        elif cerca == "izq":
            texto(board, pcbnew.F_SilkS, txt, x1 + 0.9, cy, 0.9, 90)
        else:
            texto(board, pcbnew.F_SilkS, txt, x0 - 0.9, cy, 0.9, 90)


def construir():
    board = pcbnew.CreateEmptyBoard()
    board.SetCopperLayerCount(2)
    redes, fps, pines_de = {}, {}, {}

    def red(n):
        if n not in redes:
            redes[n] = pcbnew.NETINFO_ITEM(board, n)
            board.Add(redes[n])
        return redes[n]

    for i, (bx, by) in enumerate(D.BARRENOS):
        fp = cargar("MountingHole:MountingHole_3.2mm_M3")
        fp.SetReference(f"H{i + 1}")
        fp.Reference().SetVisible(False)
        fp.SetPosition(pcbnew.VECTOR2I_MM(bx, by))
        board.Add(fp)
    for ref, huella, valor, pines in D.PIEZAS:
        fp = cargar(huella)
        fp.SetReference(ref); fp.SetValue(valor)
        board.Add(fp)
        fps[ref], pines_de[ref] = fp, pines
    acomodar(fps, pines_de)
    rotular(board, fps)
    for ref, fp in fps.items():
        t = fp.Reference()
        t.SetTextSize(pcbnew.VECTOR2I_MM(0.8, 0.8)); t.SetTextThickness(mm(0.12))
        if ref in D.ETIQUETAS:
            t.SetVisible(False)
        for pad in fp.Pads():
            num = pad.GetNumber()
            if num.isdigit() and int(num) in pines_de[ref]:
                pad.SetNet(red(pines_de[ref][int(num)]))

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
    for t, x, y, alto in D.TEXTOS:
        texto(board, pcbnew.F_SilkS, t, x, y, alto)

    ds = board.GetDesignSettings()
    ns = ds.m_NetSettings
    defecto = ns.GetDefaultNetclass()
    defecto.SetTrackWidth(mm(D.PISTA)); defecto.SetClearance(mm(D.SEPARACION))
    defecto.SetViaDiameter(mm(0.8)); defecto.SetViaDrill(mm(0.4))
    for nombre, (ancho, sep, nets) in D.CLASES.items():
        nc = pcbnew.NETCLASS(nombre)
        nc.SetTrackWidth(mm(ancho)); nc.SetClearance(mm(sep))
        nc.SetViaDiameter(mm(1.6 if ancho >= 2 else 0.8)); nc.SetViaDrill(mm(0.8 if ancho >= 2 else 0.4))
        ns.SetNetclass(nombre, nc)
        for n in nets:
            ns.SetNetclassPatternAssignment(n, nombre)
    ds.m_CopperEdgeClearance = mm(0.5)
    board.SynchronizeNetsAndNetClasses(True)
    return board


def rutear(board):
    base = os.path.join(AQUI, "ruteo", NOMBRE)
    os.makedirs(os.path.dirname(base), exist_ok=True)
    if not pcbnew.ExportSpecctraDSN(board, base + ".dsn"):
        raise SystemExit("No se pudo exportar el DSN")
    t = open(base + ".dsn", encoding="utf-8").read()
    m = re.search(r"\(boundary\s*\(path pcb 0 ([^)]*)\)\s*\)", t)
    pts = [float(v) for v in m.group(1).split()]
    xs, ys = pts[0::2], pts[1::2]
    a = 600.0
    x0, x1, y0, y1 = min(xs) + a, max(xs) - a, min(ys) + a, max(ys) - a
    t = t[:m.start()] + f"(boundary (path pcb 0 {x0} {y0} {x1} {y0} {x1} {y1} {x0} {y1} {x0} {y0}))" + t[m.end():]
    open(base + ".dsn", "w", encoding="utf-8").write(t)
    cmd = [JAVA25, "-jar", FREEROUTING, "-de", base + ".dsn", "-do", base + ".ses", "-mp", "40", "-l", "en"]
    print("Ruteando...", flush=True)
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)
    res = re.findall(r"Auto-routing stage completed.*?\((\d+) unrouted", r.stdout + r.stderr)
    print("Sin rutear:", res[-1] if res else "?")
    if not os.path.exists(base + ".ses") or not pcbnew.ImportSpecctraSES(board, base + ".ses"):
        print(r.stdout[-2000:], r.stderr[-2000:])
        raise SystemExit("Freerouting falló")


def plano_tierra(board):
    for capa in (pcbnew.F_Cu, pcbnew.B_Cu):
        z = pcbnew.ZONE(board)
        z.SetLayer(capa)
        z.SetNet(board.FindNet("GND"))
        z.SetLocalClearance(mm(0.6))
        z.SetMinThickness(mm(0.5))
        z.SetPadConnection(pcbnew.ZONE_CONNECTION_THERMAL)
        z.SetThermalReliefGap(mm(0.5))
        z.SetThermalReliefSpokeWidth(mm(0.8))
        o = z.Outline()
        o.NewOutline()
        for x, y in ((0.5, 0.5), (D.ANCHO - 0.5, 0.5), (D.ANCHO - 0.5, D.ALTO - 0.5), (0.5, D.ALTO - 0.5)):
            o.Append(mm(x), mm(y))
        board.Add(z)
    pcbnew.ZONE_FILLER(board).Fill(board.Zones())


def fabricar(pcb):
    """DRC + gerbers + taladros + zip + vistas, en ../fabricar/<X>_JLCPCB."""
    sal = os.path.join(os.path.dirname(AQUI), "fabricar", f"{X}_JLCPCB")
    ger = os.path.join(sal, "gerbers")
    shutil.rmtree(ger, ignore_errors=True)
    os.makedirs(ger)
    rep = os.path.join(sal, "drc.rpt")
    subprocess.run([CLI, "pcb", "drc", "--refill-zones", "-o", rep, pcb], capture_output=True)
    print(open(rep, encoding="utf-8").read().split("***** Sheet")[0][-600:] if os.path.exists(rep) else "sin DRC")
    capas = "F.Cu,B.Cu,F.Mask,B.Mask,F.Silkscreen,B.Silkscreen,Edge.Cuts"
    subprocess.run([CLI, "pcb", "export", "gerbers", "-l", capas, "--subtract-soldermask", "-o", ger + os.sep, pcb], check=True,
                   capture_output=True)
    subprocess.run([CLI, "pcb", "export", "drill", "--excellon-separate-th", "--generate-map", "--map-format", "pdf",
                    "-o", ger + os.sep, pcb], check=True, capture_output=True)
    z = os.path.join(sal, f"FT150TS_{X}_v1_gerbers_JLCPCB.zip")
    with zipfile.ZipFile(z, "w", zipfile.ZIP_DEFLATED) as f:
        for n in sorted(os.listdir(ger)):
            if not n.endswith(".pdf"):
                f.write(os.path.join(ger, n), n)
    for lado in ("top", "bottom"):
        subprocess.run([CLI, "pcb", "render", "--side", lado, "-w", "1600", "-h", "1600", "--quality", "high",
                        "-o", os.path.join(sal, f"{X}_{lado}.png"), pcb], capture_output=True)
    print("Zip:", z)


if __name__ == "__main__":
    salida = os.path.join(AQUI, NOMBRE + ".kicad_pcb")
    b = construir()
    if "--sin-rutear" not in sys.argv:
        rutear(b)
        plano_tierra(b)
    b.Save(salida)
    print("Guardado:", salida)
    if "--sin-rutear" not in sys.argv:
        fabricar(salida)
