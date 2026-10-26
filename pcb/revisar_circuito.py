"""Revisión del circuito (antes de la placa): referencias repetidas, redes con un solo pin,
GPIO usados dos veces, pines que no existen en la huella y tierra/alimentaciones cruzadas."""
import collections, os, re, sys
import pcbnew
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import importlib
D = importlib.import_module(sys.argv[1] if len(sys.argv) > 1 else "diseno")
HUELLAS = os.path.join(os.path.dirname(os.path.dirname(sys.executable)), "share", "kicad", "footprints")
errores = 0
def mal(t):
    global errores
    errores += 1
    print("ERROR:", t)
refs = collections.Counter(p[0] for p in D.PIEZAS)
for r, n in refs.items():
    if n > 1: mal(f"referencia repetida {r}")
pines_red = collections.defaultdict(list)
cache = {}
for ref, huella, valor, pines in D.PIEZAS:
    if huella not in cache:
        lib, nom = huella.split(":")
        carpeta = os.path.join(os.path.dirname(os.path.abspath(__file__)), lib + ".pretty") if lib == "FT150TS" else os.path.join(HUELLAS, lib + ".pretty")
        fp = pcbnew.FootprintLoad(carpeta, nom)
        if fp is None: mal(f"{ref}: no existe la huella {huella}"); continue
        cache[huella] = {p.GetNumber() for p in fp.Pads()}
    for pin, red in pines.items():
        if str(pin) not in cache[huella]: mal(f"{ref}: la huella no tiene pin {pin}")
        pines_red[red].append(f"{ref}.{pin}")
for red, ps in sorted(pines_red.items()):
    if len(ps) < 2: mal(f"red {red} solo tiene {ps}")
# Cada GPIO: exactamente un pin en el DevKit
for red, ps in pines_red.items():
    if re.fullmatch(r"IO\d+", red):
        dk = [p for p in ps if p.split(".")[0] in ("U1A", "U1B")]
        if len(dk) != 1: mal(f"{red} aparece {len(dk)} veces en el DevKit")
# Ningún componente con dos pines en la misma red (salvo los que deben: DevKit GND/3V3, optos E-K a GND)
permitido = {"GND", "+3V3"}
for ref, huella, valor, pines in D.PIEZAS:
    c = collections.Counter(pines.values())
    for red, n in c.items():
        if n > 1 and red not in permitido:
            mal(f"{ref} tiene {n} pines en {red}")
# Diodos y capacitores: que no estén en corto (los dos pines en la misma red)
for ref, huella, valor, pines in D.PIEZAS:
    if len(pines) == 2 and len(set(pines.values())) == 1:
        mal(f"{ref} en corto: los dos pines en {list(pines.values())[0]}")
print(f"Piezas: {len(D.PIEZAS)}  Redes: {len(pines_red)}  Errores: {errores}")
