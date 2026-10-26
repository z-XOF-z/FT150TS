"""
Placa P: PERFORADA CON RIELES (100 x 100 mm, 2 caras, JLCPCB). Se corre con el Python de KiCad 10.

- Zona de prototipos: 31 x 27 agujeros a 2.54 mm, cada uno con su pad (sueltos, como placa perforada).
- Rieles: arriba +12V y GND, abajo +5V y GND (las dos tierras unidas por la orilla derecha).
- Bornera de 3 (12V, 5V, GND) a la izquierda que alimenta los rieles.
Salida: ft150ts_P.kicad_pcb y ../fabricar/P_JLCPCB (gerbers + zip + vistas).
"""
import os
import shutil
import subprocess
import sys
import zipfile

import pcbnew

AQUI = os.path.dirname(os.path.abspath(__file__))
KICAD = os.path.dirname(os.path.dirname(sys.executable))
HUELLAS = os.path.join(KICAD, "share", "kicad", "footprints")
CLI = os.path.join(KICAD, "bin", "kicad-cli.exe")
mm = pcbnew.FromMM
P = 2.54
W = H = 100.0
X0, NCOL = 14.0, 31                       # columnas de 14.0 a 90.2
Y0, NFIL = 16.0, 27                       # filas de 16.0 a 82.04
Y_12V, Y_GND1 = Y0 - 3 * P, Y0 - 2 * P    # rieles de arriba
Y_5V, Y_GND2 = Y0 + (NFIL + 1) * P, Y0 + (NFIL + 2) * P   # rieles de abajo
X_FIN = X0 + (NCOL - 1) * P


def pista(board, capa, red, a, b, ancho):
    t = pcbnew.PCB_TRACK(board)
    t.SetStart(pcbnew.VECTOR2I_MM(*a)); t.SetEnd(pcbnew.VECTOR2I_MM(*b))
    t.SetLayer(capa); t.SetWidth(mm(ancho)); t.SetNet(red)
    board.Add(t)


def texto(board, t, x, y, alto=1.2):
    s = pcbnew.PCB_TEXT(board)
    s.SetText(t); s.SetLayer(pcbnew.F_SilkS)
    s.SetTextSize(pcbnew.VECTOR2I_MM(alto, alto)); s.SetTextThickness(mm(alto * 0.15))
    s.SetPosition(pcbnew.VECTOR2I_MM(x, y))
    board.Add(s)


def linea(board, a, b):
    s = pcbnew.PCB_SHAPE(board)
    s.SetShape(pcbnew.SHAPE_T_SEGMENT)
    s.SetStart(pcbnew.VECTOR2I_MM(*a)); s.SetEnd(pcbnew.VECTOR2I_MM(*b))
    s.SetLayer(pcbnew.Edge_Cuts); s.SetWidth(mm(0.15))
    board.Add(s)


def construir():
    b = pcbnew.CreateEmptyBoard()
    b.SetCopperLayerCount(2)
    redes = {}
    for n in ("+12V", "+5V", "GND"):
        redes[n] = pcbnew.NETINFO_ITEM(b, n)
        b.Add(redes[n])

    # Contorno recto (esquinas a 90°: más fácil de cortar si la quieres partir) y barrenos M3
    for a, c in (((0, 0), (W, 0)), ((W, 0), (W, H)), ((W, H), (0, H)), ((0, H), (0, 0))):
        linea(b, a, c)
    for i, (x, y) in enumerate(((3.5, 3.5), (96.5, 3.5), (3.5, 96.5), (96.5, 96.5))):
        fp = pcbnew.FootprintLoad(os.path.join(HUELLAS, "MountingHole.pretty"), "MountingHole_3.2mm_M3")
        fp.SetReference(f"H{i + 1}"); fp.Reference().SetVisible(False)
        fp.SetPosition(pcbnew.VECTOR2I_MM(x, y))
        b.Add(fp)

    # Todos los agujeros en una sola huella
    perf = pcbnew.FOOTPRINT(b)
    perf.SetReference("PERF"); perf.Reference().SetVisible(False); perf.Value().SetVisible(False)
    perf.SetPosition(pcbnew.VECTOR2I_MM(0, 0))
    b.Add(perf)
    num = 0

    def pad(x, y, red=None):
        nonlocal num
        num += 1
        p = pcbnew.PAD(perf)
        p.SetAttribute(pcbnew.PAD_ATTRIB_PTH)
        p.SetShape(pcbnew.PAD_SHAPE_CIRCLE)
        p.SetSize(pcbnew.VECTOR2I_MM(1.8, 1.8))
        p.SetDrillSize(pcbnew.VECTOR2I_MM(1.0, 1.0))
        p.SetLayerSet(pcbnew.PAD.PTHMask())
        p.SetNumber(str(num))
        p.SetPosition(pcbnew.VECTOR2I_MM(x, y))
        perf.Add(p)
        if red:
            p.SetNet(redes[red])

    for c in range(NCOL):
        x = X0 + c * P
        for f in range(NFIL):
            pad(x, Y0 + f * P)
        for y, red in ((Y_12V, "+12V"), (Y_GND1, "GND"), (Y_5V, "+5V"), (Y_GND2, "GND")):
            pad(x, y, red)

    # Rieles: pista por las dos caras a lo largo de cada fila de riel
    for y, red in ((Y_12V, "+12V"), (Y_GND1, "GND"), (Y_5V, "+5V"), (Y_GND2, "GND")):
        for capa in (pcbnew.F_Cu, pcbnew.B_Cu):
            pista(b, capa, redes[red], (X0, y), (X_FIN, y), 1.6)

    # Bornera de entrada: 1 = 12V, 2 = 5V, 3 = GND (cable por la izquierda)
    j = pcbnew.FootprintLoad(os.path.join(HUELLAS, "TerminalBlock_Phoenix.pretty"),
                             "TerminalBlock_Phoenix_PT-1,5-3-5.0-H_1x03_P5.00mm_Horizontal")
    j.SetReference("J1"); j.Reference().SetVisible(False)
    j.SetPosition(pcbnew.VECTOR2I_MM(6.0, 44.0)); j.SetOrientationDegrees(270)
    b.Add(j)
    for p in j.Pads():
        p.SetNet(redes[{"1": "+12V", "2": "+5V", "3": "GND"}[p.GetNumber()]])
    pos = {p.GetNumber(): (pcbnew.ToMM(p.GetPosition().x), pcbnew.ToMM(p.GetPosition().y)) for p in j.Pads()}
    x12, y12 = pos["1"]; x5, y5 = pos["2"]; xg, yg = pos["3"]
    B, F = pcbnew.B_Cu, pcbnew.F_Cu
    # 12 V: abajo de la placa, sube por la orilla y entra al riel de arriba
    pista(b, B, redes["+12V"], (x12, y12), (x12, Y_12V), 1.6)
    pista(b, B, redes["+12V"], (x12, Y_12V), (X0, Y_12V), 1.6)
    # GND: abajo, baja al riel de abajo; el riel de arriba se une por la orilla derecha
    pista(b, B, redes["GND"], (xg, yg), (xg, Y_GND2), 1.6)
    pista(b, B, redes["GND"], (xg, Y_GND2), (X0, Y_GND2), 1.6)
    xd = X_FIN + 4.0
    pista(b, B, redes["GND"], (X_FIN, Y_GND1), (xd, Y_GND1), 1.6)
    pista(b, B, redes["GND"], (xd, Y_GND1), (xd, Y_GND2), 1.6)
    pista(b, B, redes["GND"], (xd, Y_GND2), (X_FIN, Y_GND2), 1.6)
    # 5 V: por arriba de la placa, baja al riel de abajo
    xm = 10.0
    pista(b, F, redes["+5V"], (x5, y5), (xm, y5), 1.6)
    pista(b, F, redes["+5V"], (xm, y5), (xm, Y_5V), 1.6)
    pista(b, F, redes["+5V"], (xm, Y_5V), (X0, Y_5V), 1.6)

    # Serigrafía
    t = pcbnew.PCB_TEXT(b)
    t.SetText("12V   5V   GND"); t.SetLayer(pcbnew.F_SilkS); t.SetTextAngleDegrees(-90)
    t.SetTextSize(pcbnew.VECTOR2I_MM(1.0, 1.0)); t.SetTextThickness(mm(0.15))
    t.SetPosition(pcbnew.VECTOR2I_MM(11.8, (y12 + yg) / 2))
    b.Add(t)
    for y, t in ((Y_12V, "+12V"), (Y_GND1, "GND"), (Y_5V, "+5V"), (Y_GND2, "GND")):
        texto(b, t, X_FIN + 6.5 if t != "GND" else X_FIN + 7.5, y, 1.0)
    texto(b, "FT150TS PERFORADA v1  2.54 mm", 50.0, 97.5, 1.2)
    ds = b.GetDesignSettings()
    ds.m_CopperEdgeClearance = mm(0.5)
    ns = ds.m_NetSettings
    nc = ns.GetDefaultNetclass(); nc.SetClearance(mm(0.25)); nc.SetTrackWidth(mm(1.6))
    return b


def fabricar(pcb):
    sal = os.path.join(os.path.dirname(AQUI), "fabricar", "P_JLCPCB")
    ger = os.path.join(sal, "gerbers")
    shutil.rmtree(ger, ignore_errors=True)
    os.makedirs(ger)
    rep = os.path.join(sal, "drc.rpt")
    subprocess.run([CLI, "pcb", "drc", "-o", rep, pcb], capture_output=True)
    print(open(rep, encoding="utf-8").read().split("***** Sheet")[0][-500:])
    subprocess.run([CLI, "pcb", "export", "gerbers", "-l", "F.Cu,B.Cu,F.Mask,B.Mask,F.Silkscreen,B.Silkscreen,Edge.Cuts",
                    "--subtract-soldermask", "-o", ger + os.sep, pcb], check=True, capture_output=True)
    subprocess.run([CLI, "pcb", "export", "drill", "--excellon-separate-th", "-o", ger + os.sep, pcb],
                   check=True, capture_output=True)
    z = os.path.join(sal, "FT150TS_P_v1_gerbers_JLCPCB.zip")
    with zipfile.ZipFile(z, "w", zipfile.ZIP_DEFLATED) as f:
        for n in sorted(os.listdir(ger)):
            f.write(os.path.join(ger, n), n)
    for lado in ("top", "bottom"):
        subprocess.run([CLI, "pcb", "render", "--side", lado, "-w", "1600", "-h", "1600", "--quality", "high",
                        "-o", os.path.join(sal, f"P_{lado}.png"), pcb], capture_output=True)
    print("Zip:", z)


if __name__ == "__main__":
    salida = os.path.join(AQUI, "ft150ts_P.kicad_pcb")
    construir().Save(salida)
    print("Guardado:", salida)
    fabricar(salida)
