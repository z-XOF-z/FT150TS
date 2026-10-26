"""
Genera el esquemático de la tarjeta base FT150TS (placa B v2.3) a partir de diseno.py.

Es solo para ver y revisar el circuito: cada pieza con su símbolo y, en cada pata, una etiqueta con
el nombre de su red (mismo nombre = mismo cable). La placa se sigue generando con generar_pcb.py.
Se corre con el Python de KiCad:  python generar_esquematico.py
Salida: ft150ts_B_esquema.kicad_sch (+ .kicad_pro) en esta carpeta.
"""
import math
import os
import re
import sys
import uuid

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(AQUI))
import diseno as D  # noqa: E402

KICAD = os.path.dirname(os.path.dirname(sys.executable))
SIMBOLOS = os.path.join(KICAD, "share", "kicad", "symbols")
NOMBRE = "ft150ts_B_esquema"
_cache = {}


def u():
    return str(uuid.uuid4())


def _bloque(t, nombre):
    i = t.find(f'(symbol "{nombre}"')
    if i < 0:
        raise KeyError(nombre)
    d = 0
    for j in range(i, len(t)):
        if t[j] == "(":
            d += 1
        elif t[j] == ")":
            d -= 1
            if d == 0:
                return t[i:j + 1]


def simbolo_lib(lib, nombre):
    """Texto del símbolo ("Lib:Nombre") y sus pines {num: (x, y, ángulo)}. Resuelve los 'extends'."""
    if (lib, nombre) in _cache:
        return _cache[(lib, nombre)]
    t = open(os.path.join(SIMBOLOS, lib + ".kicad_sym"), encoding="utf-8").read()
    base = nombre
    s = _bloque(t, base)
    while "(extends " in s:
        base = re.search(r'\(extends "([^"]+)"\)', s).group(1)
        s = _bloque(t, base)
    # renombra el símbolo y sus unidades internas ("Base_0_1" -> "Nombre_0_1")
    s = s.replace(f'(symbol "{base}"', f'(symbol "{lib}:{nombre}"', 1)
    s = s.replace(f'(symbol "{base}_', f'(symbol "{nombre}_')
    pines = {num: (float(x), float(y), int(a)) for x, y, a, num in
             re.findall(r'\(pin \w+ \w+\s*\(at ([-\d.]+) ([-\d.]+) (\d+)\).*?\(number "([^"]*)"', s, re.S)}
    _cache[(lib, nombre)] = (s, pines)
    return _cache[(lib, nombre)]


def simbolo_de(ref, huella, valor, redes):
    v = valor.upper()
    if "PinSocket" in huella or "PinHeader" in huella or "TerminalBlock" in huella:
        return "Connector_Generic", f"Conn_01x{max(redes) if 'PinSocket' not in huella else 22:02d}"
    if "TestPoint" in huella:
        return "Connector", "TestPoint"
    if ref.startswith("K"):
        return "Relay", "SANYOU_SRD_Form_C"
    if v == "PC817":
        return "Isolator", "PC817"
    if v == "BC547":
        return "Transistor_BJT", "BC547"
    if v == "BD140":
        return "Transistor_BJT", "BD140"
    if v == "2N7000":
        return "Transistor_FET", "2N7000"
    if v == "IRF840":
        return "Transistor_FET", "IRF740"        # mismo encapsulado y patas (G D S)
    if v.startswith("LM2596"):
        return "Regulator_Switching", "LM2596T-5"
    if ref.startswith("LED"):
        return "Device", "LED"
    if ref.startswith("DZ"):
        return "Device", "D_Zener"
    if v.startswith("P6KE"):
        return "Device", "D_TVS"
    if ref.startswith("D"):
        return "Device", "D"
    if ref.startswith("F"):
        return "Device", "Polyfuse"
    if ref.startswith("L"):
        return "Device", "L"
    if ref.startswith("C"):
        return "Device", "C_Polarized" if "CP_" in huella else "C"
    if ref.startswith("R"):
        return "Device", "R"
    raise ValueError(ref)


# Bloques: la primera pieza de cada bloque y su título
BLOQUES = {
    "U1A": "ESP32-S3 (copia ancha N16R8) en dos tiras hembra",
    "J1": "Entrada de la moto 12 V: PTC, diodo, TVS y regulador LM2596 a 5 V",
    "J2": "Respaldo: celdas 3.7 V con su BMS",
    "R1": "Mediciones: batería de la moto -> GPIO1, celdas -> GPIO4",
    "Q5": "Carga de las celdas: GPIO43 -> BC547 -> BD140 -> 5 V al TP4056",
    "J4": "RPM: cable de la bobina -> PC817 -> GPIO16",
    "J5": "Entradas aisladas 3.3 a 16 V (llave, neutral, 2 libres)",
    "J7": "Sensores de 3.3 o 5 V (Hall, quickshifter, DS18B20)",
    "J10": "Gasolina y LDR",
    "J12": "I2C a 3.3 V y a 5 V para el LCD (2N7000)",
    "J14": "Botones, SD, GPS y tira de LEDs",
    "R28": "Corte de chispa: GPIO47 -> PC817 -> IRF840 -> cable de paro del CDI",
    "R30": "Salida aislada libre <- GPIO44",
    "R711": "Relevador 1 (anti-robo) <- GPIO40",
    "R721": "Relevador 2 <- GPIO38",
    "R731": "Relevador 3 <- GPIO45",
    "TP1": "Puntos de prueba",
}


def prop(nombre, valor, x, y, oculto=False):
    h = " (hide yes)" if oculto else ""
    return (f'    (property "{nombre}" "{valor}" (at {x:.2f} {y:.2f} 0)\n'
            f'      (effects (font (size 1.27 1.27)){h}))\n')


def generar():
    raiz = u()
    items, usados = [], set()
    npwr = 0

    def texto(t, x, y, alto=1.27, negrita=False):
        b = " bold" if negrita else ""
        items.append(f'  (text "{t}" (exclude_from_sim no) (at {x:.2f} {y:.2f} 0)\n'
                     f'    (effects (font (size {alto} {alto}){b}) (justify left bottom)) (uuid "{u()}"))\n')

    def tierra(x, y, ang):
        nonlocal npwr
        npwr += 1
        usados.add(("power", "GND"))
        ref = f"#PWR{npwr:03d}"
        # el símbolo de tierra apunta hacia abajo; se gira para que quede del lado contrario al cuerpo
        rot = (ang - 90) % 360
        items.append(
            f'  (symbol (lib_id "power:GND") (at {x:.2f} {y:.2f} {rot}) (unit 1)\n'
            f'    (exclude_from_sim no) (in_bom yes) (on_board yes) (dnp no) (uuid "{u()}")\n'
            + prop("Reference", ref, x, y + 6, True) + prop("Value", "GND", x, y + 3.5, True)
            + prop("Footprint", "", x, y, True) + prop("Datasheet", "", x, y, True)
            + f'    (pin "1" (uuid "{u()}"))\n'
            f'    (instances (project "{NOMBRE}" (path "/{raiz}" (reference "{ref}") (unit 1)))))\n')

    def pieza(ref, huella, valor, redes, x, y):
        lib, sim = simbolo_de(ref, huella, valor, redes)
        _, pines = simbolo_lib(lib, sim)
        usados.add((lib, sim))
        ys = [p[1] for p in pines.values()]
        alto = max(ys) - min(ys) if ys else 0
        props = (prop("Reference", ref, x + 4, y - alto / 2 - 4) + prop("Value", valor, x + 4, y - alto / 2 - 1.5)
                 + prop("Footprint", huella, x, y, True) + prop("Datasheet", "", x, y, True))
        pinlist = "".join(f'    (pin "{n}" (uuid "{u()}"))\n' for n in pines)
        items.append(
            f'  (symbol (lib_id "{lib}:{sim}") (at {x:.2f} {y:.2f} 0) (unit 1)\n'
            f'    (exclude_from_sim no) (in_bom yes) (on_board yes) (dnp no) (uuid "{u()}")\n'
            f'{props}{pinlist}'
            f'    (instances (project "{NOMBRE}" (path "/{raiz}" (reference "{ref}") (unit 1)))))\n')
        for num, red in redes.items():
            if str(num) not in pines or not red:
                continue
            px, py, a = pines[str(num)]
            ex, ey = round(x + px, 2), round(y - py, 2)
            # 'a' es hacia dónde apunta la pata desde su punta hacia el cuerpo: la etiqueta va al revés
            if red == "GND":
                tierra(ex, ey, a)
                continue
            ang = {0: 180, 180: 0, 90: 270, 270: 90}[a]
            just = "right" if ang in (180, 270) else "left"
            items.append(
                f'  (label "{red}" (at {ex:.2f} {ey:.2f} {ang}) (fields_autoplaced yes)\n'
                f'    (effects (font (size 1.27 1.27)) (justify {just} bottom)) (uuid "{u()}"))\n')
        return alto

    # Acomodo: cada bloque en su propio renglón, piezas de izquierda a derecha
    x0, y, ancho_hoja = 25.4, 30.0, 560.0
    texto("FT150TS  tarjeta base v2.3 (placa B, 10 x 10 cm)  -  esquemático generado desde diseno.py", x0, 18, 3, True)
    texto("Mismo nombre de etiqueta = mismo cable. El IRF840 se dibuja con el símbolo del IRF740 (mismas patas G D S).",
          x0, 23, 1.6)
    x, alto_renglon = x0, 0
    for ref, huella, valor, redes in D.PIEZAS:
        if ref in BLOQUES:
            y += alto_renglon + 22 if alto_renglon or ref != "U1A" else 0
            x, alto_renglon = x0, 0
            texto(BLOQUES[ref], x0, y, 2, True)
            y += 10
        lib, sim = simbolo_de(ref, huella, valor, redes)
        pines = simbolo_lib(lib, sim)[1]
        xs = [p[0] for p in pines.values()]
        ys = [p[1] for p in pines.values()]
        izq, der = -min(xs + [0]), max(xs + [0])
        alto = max(ys) - min(ys)
        paso = izq + der + 32
        if x + paso > ancho_hoja:
            y += alto_renglon + 18
            x, alto_renglon = x0, 0
        cx = round((x + izq + 14) / 2.54) * 2.54
        cy = round((y + max(ys) + 4) / 2.54) * 2.54
        pieza(ref, huella, valor, redes, cx, cy)
        x += paso
        alto_renglon = max(alto_renglon, alto + 8)

    libs = "".join("    " + simbolo_lib(l, s)[0].replace("\n", "\n    ") + "\n" for l, s in sorted(usados))
    sch = (f'(kicad_sch (version 20231120) (generator "eeschema") (generator_version "8.0")\n'
           f'  (uuid "{raiz}")\n  (paper "User" 600 {max(420, y + 80):.0f})\n'
           f'  (title_block (title "FT150TS tarjeta base v2.3 (placa B)") (company "FT150TS"))\n'
           f'  (lib_symbols\n{libs}  )\n' + "".join(items) +
           '  (sheet_instances (path "/" (page "1")))\n)\n')
    ruta = os.path.join(AQUI, NOMBRE + ".kicad_sch")
    open(ruta, "w", encoding="utf-8").write(sch)
    pro = os.path.join(AQUI, NOMBRE + ".kicad_pro")
    if not os.path.exists(pro):
        open(pro, "w", encoding="utf-8").write('{"meta": {"filename": "%s.kicad_pro", "version": 1}}\n' % NOMBRE)
    print("Escrito:", ruta, f"({len(D.PIEZAS)} piezas)")


if __name__ == "__main__":
    generar()
