"""Revisa que las piezas no se encimen (patio de cada huella) ni se salgan del borde."""
import sys
import pcbnew
b = pcbnew.LoadBoard(sys.argv[1] if len(sys.argv) > 1 else "ft150ts_A.kicad_pcb")
_bb = b.GetBoardEdgesBoundingBox()
W, H = pcbnew.ToMM(_bb.GetRight()), pcbnew.ToMM(_bb.GetBottom())
cajas = []
for fp in b.GetFootprints():
    r = fp.GetCourtyard(pcbnew.F_CrtYd).BBox() if not fp.GetCourtyard(pcbnew.F_CrtYd).IsEmpty() else fp.GetBoundingBox(False)
    x0, y0, x1, y1 = (pcbnew.ToMM(v) for v in (r.GetLeft(), r.GetTop(), r.GetRight(), r.GetBottom()))
    cajas.append((fp.GetReference(), x0, y0, x1, y1))
    if x0 < 0 or y0 < 0 or x1 > W or y1 > H:
        print(f"BORDE  {fp.GetReference():5s} {x0:6.1f},{y0:6.1f} - {x1:6.1f},{y1:6.1f}")
n = 0
for i, a in enumerate(cajas):
    for c in cajas[i + 1:]:
        ox = min(a[3], c[3]) - max(a[1], c[1])
        oy = min(a[4], c[4]) - max(a[2], c[2])
        if {a[0], c[0]} <= {"U1A", "U1B", "U1C"}:
            continue          # posiciones alternativas del DevKit
        if ox > 0.05 and oy > 0.05:
            n += 1
            print(f"ENCIMA {a[0]:5s} {c[0]:5s} ({ox:.1f} x {oy:.1f} mm)")
print("encimadas:", n)
