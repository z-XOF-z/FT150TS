"""
Genera 6 esquemáticos de KiCad, uno por bloque delicado de la placa, listos para simular.
Ábrelos en KiCad (Editor de esquemáticos) y dale a Inspeccionar > Simulador > Ejecutar.

Las piezas se conectan con etiquetas: mismo nombre = mismo cable.
Se corre con el Python de KiCad:  python generar_sim.py
"""
import os
import re
import sys
import uuid

AQUI = os.path.dirname(os.path.abspath(__file__))
KICAD = os.path.dirname(os.path.dirname(sys.executable))
SIMBOLOS = os.path.join(KICAD, "share", "kicad", "symbols")
_cache = {}


def u():
    return str(uuid.uuid4())


def simbolo_lib(lib, nombre):
    """Texto del símbolo en la librería de KiCad (con nombre "Lib:Nombre") y sus pines."""
    clave = (lib, nombre)
    if clave in _cache:
        return _cache[clave]
    t = open(os.path.join(SIMBOLOS, lib + ".kicad_sym"), encoding="utf-8").read()
    i = t.find(f'(symbol "{nombre}"')
    d = 0
    for j in range(i, len(t)):
        if t[j] == "(":
            d += 1
        elif t[j] == ")":
            d -= 1
            if d == 0:
                break
    s = t[i:j + 1]
    s = s.replace(f'(symbol "{nombre}"', f'(symbol "{lib}:{nombre}"', 1)
    pines = {num: (float(x), float(y)) for x, y, num in
             re.findall(r'\(pin \w+ \w+\s*\(at ([-\d.]+) ([-\d.]+) \d+\).*?\(number "([^"]*)"', s, re.S)}
    _cache[clave] = (s, pines)
    return _cache[clave]


def prop(nombre, valor, x, y, oculto=False):
    h = " (hide yes)" if oculto else ""
    return (f'    (property "{nombre}" "{valor}" (at {x:.2f} {y:.2f} 0)\n'
            f'      (effects (font (size 1.27 1.27)){h}))\n')


class Hoja:
    def __init__(self, nombre, titulo):
        self.nombre, self.titulo = nombre, titulo
        self.raiz = u()
        self.usados, self.items, self.npwr = set(), [], 0

    def pieza(self, lib, simbolo, ref, valor, x, y, rot, redes, sim=None):
        """redes: {pin: red}. 'GND' pone el símbolo de tierra."""
        texto, pines = simbolo_lib(lib, simbolo)
        self.usados.add((lib, simbolo))
        props = prop("Reference", ref, x + 3, y - 4) + prop("Value", valor, x + 3, y + 4)
        props += prop("Footprint", "", x, y, True) + prop("Datasheet", "", x, y, True)
        for k, v in (sim or {}).items():
            props += prop(k, v, x, y, True)
        pinlist = "".join(f'    (pin "{n}" (uuid "{u()}"))\n' for n in pines)
        self.items.append(
            f'  (symbol (lib_id "{lib}:{simbolo}") (at {x:.2f} {y:.2f} {rot}) (unit 1)\n'
            f'    (exclude_from_sim no) (in_bom yes) (on_board yes) (dnp no) (uuid "{u()}")\n'
            f'{props}{pinlist}'
            f'    (instances (project "{self.nombre}" (path "/{self.raiz}" (reference "{ref}") (unit 1)))))\n')
        import math
        c, s_ = round(math.cos(math.radians(rot)), 6), round(math.sin(math.radians(rot)), 6)
        for num, red in redes.items():
            px, py = pines[str(num)]
            # Símbolo: y hacia arriba; esquemático: y hacia abajo. Rotación antihoraria.
            rx, ry = px * c - py * s_, px * s_ + py * c
            ex, ey = round(x + rx, 2), round(y - ry, 2)
            if red == "GND":
                self.tierra(ex, ey)
            else:
                self.items.append(
                    f'  (label "{red}" (at {ex:.2f} {ey:.2f} 0) (fields_autoplaced yes)\n'
                    f'    (effects (font (size 1.27 1.27)) (justify left bottom)) (uuid "{u()}"))\n')

    def tierra(self, x, y):
        self.npwr += 1
        texto, _ = simbolo_lib("power", "GND")
        self.usados.add(("power", "GND"))
        ref = f"#PWR{self.npwr:02d}"
        self.items.append(
            f'  (symbol (lib_id "power:GND") (at {x:.2f} {y:.2f} 0) (unit 1)\n'
            f'    (exclude_from_sim no) (in_bom yes) (on_board yes) (dnp no) (uuid "{u()}")\n'
            + prop("Reference", ref, x, y + 6, True) + prop("Value", "GND", x, y + 3.5)
            + prop("Footprint", "", x, y, True) + prop("Datasheet", "", x, y, True)
            + f'    (pin "1" (uuid "{u()}"))\n'
            f'    (instances (project "{self.nombre}" (path "/{self.raiz}" (reference "{ref}") (unit 1)))))\n')

    def texto(self, t, x, y, alto=1.27):
        t = t.replace('"', "'")
        self.items.append(f'  (text "{t}" (exclude_from_sim no) (at {x:.2f} {y:.2f} 0)\n'
                          f'    (effects (font (size {alto} {alto})) (justify left bottom)) (uuid "{u()}"))\n')

    def guardar(self):
        libs = "".join("    " + simbolo_lib(l, s)[0].replace("\n", "\n    ") + "\n" for l, s in sorted(self.usados))
        sch = (f'(kicad_sch (version 20231120) (generator "eeschema") (generator_version "8.0")\n'
               f'  (uuid "{self.raiz}")\n  (paper "A4")\n'
               f'  (title_block (title "{self.titulo}") (company "FT150TS"))\n'
               f'  (lib_symbols\n{libs}  )\n' + "".join(self.items) +
               '  (sheet_instances (path "/" (page "1")))\n)\n')
        ruta = os.path.join(AQUI, self.nombre + ".kicad_sch")
        open(ruta, "w", encoding="utf-8").write(sch)
        # Proyecto mínimo para que KiCad lo abra como proyecto
        pro = os.path.join(AQUI, self.nombre + ".kicad_pro")
        if not os.path.exists(pro):
            open(pro, "w", encoding="utf-8").write('{"meta": {"filename": "%s.kicad_pro", "version": 1}}\n' % self.nombre)
        return ruta


LIB = "modelos.lib"
def diodo(modelo):
    return {"Sim.Device": "D", "Sim.Library": LIB, "Sim.Name": modelo, "Sim.Pins": "1=K 2=A"}
NPN = {"Sim.Device": "NPN", "Sim.Library": LIB, "Sim.Name": "QBC547B", "Sim.Pins": "1=C 2=B 3=E"}
PNP = {"Sim.Device": "PNP", "Sim.Library": LIB, "Sim.Name": "QBD140", "Sim.Pins": "1=E 2=C 3=B"}
OPTO = {"Sim.Device": "SUBCKT", "Sim.Library": LIB, "Sim.Name": "PC817", "Sim.Pins": "1=A 2=K 3=E 4=C"}
def pulso(y1, y2, td, tr, tf, tw, per):
    return {"Sim.Device": "V", "Sim.Type": "PULSE", "Sim.Pins": "1=+ 2=-",
            "Sim.Params": f"y1={y1} y2={y2} td={td} tr={tr} tf={tf} tw={tw} per={per}"}
def cd(v):
    return {"Sim.Device": "V", "Sim.Type": "DC", "Sim.Pins": "1=+ 2=-", "Sim.Params": f"dc={v}"}


def bloques():
    hojas = []

    # 1) Entrada de la bobina (RPM)
    h = Hoja("01_entrada_bobina", "RPM: pulso de la bobina (350 V) -> PC817 -> GPIO16")
    h.pieza("Simulation_SPICE", "VPULSE", "V1", "Bobina 350V", 30, 60, 0, {1: "BOBINA", 2: "GND"},
            pulso(0, 350, "1m", "5u", "20u", "30u", "10m"))
    h.pieza("Device", "D", "D5", "1N4007", 50, 40, 0, {2: "BOBINA", 1: "RPM_A"}, diodo("D1N4007"))
    h.pieza("Device", "R", "R9", "47k", 70, 50, 90, {1: "RPM_A", 2: "OPT_A"})
    h.pieza("Device", "D", "D6", "1N4148", 90, 70, 90, {1: "OPT_A", 2: "GND"}, diodo("D1N4148"))
    h.pieza("Device", "C", "C8", "4.7n", 100, 70, 0, {1: "OPT_A", 2: "GND"})
    h.pieza("Isolator", "PC817", "U9", "PC817", 130, 60, 0, {1: "OPT_A", 2: "GND", 3: "GND", 4: "GPIO16"}, OPTO)
    h.pieza("Device", "R", "R99", "45k", 160, 45, 0, {1: "V33", 2: "GPIO16"})
    h.pieza("Simulation_SPICE", "VDC", "V2", "3.3V", 180, 60, 0, {1: "V33", 2: "GND"}, cd(3.3))
    h.texto(".tran 1u 25m", 30, 110, 2)
    h.texto("R9 es de 1 W en la placa. R99 = pull-up interno del ESP32 (45k).", 30, 128)
    h.texto("Grafica: V(BOBINA), V(GPIO16) y la corriente del LED I(U9:A)... GPIO16 debe bajar a ~0 V en cada pulso.", 30, 120)
    hojas.append(h)

    # 2) Entrada aislada universal 3.3 a 16 V
    h = Hoja("02_entrada_aislada", "Entrada aislada: 0 a 16 V -> 1.5k -> PC817 -> GPIO")
    h.pieza("Simulation_SPICE", "VPWL", "V1", "rampa 0-16V", 30, 60, 0, {1: "ENT", 2: "GND"},
            {"Sim.Device": "V", "Sim.Type": "PWL", "Sim.Pins": "1=+ 2=-", "Sim.Params": 'pwl=\\"0 0 20m 16\\"'})
    h.pieza("Device", "R", "R120", "1.5k", 60, 50, 90, {1: "ENT", 2: "OPT_A"})
    h.pieza("Device", "D", "D120", "1N4148", 85, 70, 90, {1: "OPT_A", 2: "GND"}, diodo("D1N4148"))
    h.pieza("Isolator", "PC817", "U12", "PC817", 120, 60, 0, {1: "OPT_A", 2: "GND", 3: "GND", 4: "GPIO"}, OPTO)
    h.pieza("Device", "R", "R99", "45k", 150, 45, 0, {1: "V33", 2: "GPIO"})
    h.pieza("Simulation_SPICE", "VDC", "V2", "3.3V", 170, 60, 0, {1: "V33", 2: "GND"}, cd(3.3))
    h.texto(".tran 10u 20m", 30, 110, 2)
    h.texto("La rampa sube de 0 a 16 V. GPIO debe pasar a LOW desde ~2.5-3 V de entrada; el LED no pasa de 10 mA a 16 V.", 30, 120)
    hojas.append(h)

    # 3) Entrada de sensor 3.3/5 V
    h = Hoja("03_entrada_sensor", "Sensor de 5 V -> 2.2k + BAT85 al 3.3 V -> GPIO")
    h.pieza("Simulation_SPICE", "VPULSE", "V1", "sensor 5V", 30, 60, 0, {1: "SENSOR", 2: "GND"},
            pulso(0, 5, "0.2m", "10u", "10u", "0.5m", "1m"))
    h.pieza("Device", "R", "R20", "2.2k", 60, 50, 90, {1: "SENSOR", 2: "GPIO"})
    h.pieza("Device", "D", "D20", "BAT85", 85, 40, 0, {2: "GPIO", 1: "V33"}, diodo("DBAT85"))
    h.pieza("Device", "R", "R99", "45k", 110, 45, 0, {1: "V33", 2: "GPIO"})
    h.pieza("Simulation_SPICE", "VDC", "V2", "3.3V", 140, 60, 0, {1: "V33", 2: "GND"}, cd(3.3))
    h.texto(".tran 1u 3m", 30, 110, 2)
    h.texto("Con 5 V en el sensor, GPIO no debe pasar de ~3.6 V (el ESP32 aguanta 3.6 V).", 30, 120)
    hojas.append(h)

    # 4) Divisores de batería
    h = Hoja("04_divisores", "Divisores: moto 16 V -> GPIO1 y celdas 4.2 V -> GPIO4")
    h.pieza("Simulation_SPICE", "VDC", "V1", "moto 16V", 30, 60, 0, {1: "BAT", 2: "GND"}, cd(16))
    h.pieza("Device", "R", "R1", "100k", 55, 50, 0, {1: "BAT", 2: "ADC1"})
    h.pieza("Device", "R", "R2", "22k", 55, 70, 0, {1: "ADC1", 2: "GND"})
    h.pieza("Simulation_SPICE", "VDC", "V2", "celdas 4.2V", 100, 60, 0, {1: "CELDA", 2: "GND"}, cd(4.2))
    h.pieza("Device", "R", "R3", "100k", 125, 50, 0, {1: "CELDA", 2: "ADC4"})
    h.pieza("Device", "R", "R4", "100k", 125, 70, 0, {1: "ADC4", 2: "GND"})
    h.texto(".op", 30, 110, 2)
    h.texto("Punto de operación: ADC1 = 2.89 V con 16 V y ADC4 = 2.1 V con 4.2 V (el ADC del ESP32 llega a ~3.1 V).", 30, 120)
    hojas.append(h)

    # 5) Relevador
    h = Hoja("05_rele", "Relevador: GPIO -> 470 -> PC817 -> BC547 -> bobina 70 ohm + LED")
    h.pieza("Simulation_SPICE", "VPULSE", "V1", "GPIO 3.3V", 25, 70, 0, {1: "GPIO", 2: "GND"},
            pulso(0, 3.3, "1m", "1u", "1u", "10m", "25m"))
    h.pieza("Device", "R", "R711", "470", 45, 60, 90, {1: "GPIO", 2: "RL_A"})
    h.pieza("Isolator", "PC817", "U71", "PC817", 75, 70, 0, {1: "RL_A", 2: "GND", 3: "RL_E", 4: "V5"}, OPTO)
    h.pieza("Device", "R", "R712", "1k", 100, 80, 90, {1: "RL_E", 2: "RL_B"})
    h.pieza("Device", "R", "R713", "10k", 112, 95, 0, {1: "RL_B", 2: "GND"})
    h.pieza("Transistor_BJT", "Q_NPN_CBE", "Q71", "BC547", 130, 85, 0, {1: "BOB", 2: "RL_B", 3: "GND"}, NPN)
    h.pieza("Device", "R", "R98", "70", 145, 45, 0, {1: "V5", 2: "BOBX"})
    h.pieza("Device", "L", "L1", "150m", 145, 62, 0, {1: "BOBX", 2: "BOB"})
    h.pieza("Device", "D", "D71", "1N4007", 165, 55, 90, {1: "V5", 2: "BOB"}, diodo("D1N4007"))
    h.pieza("Device", "R", "R714", "1k", 185, 45, 0, {1: "V5", 2: "LED_A"})
    h.pieza("Device", "LED", "LED1", "rojo", 185, 65, 90, {2: "LED_A", 1: "BOB"},
            {"Sim.Device": "D", "Sim.Library": LIB, "Sim.Name": "DLEDROJO", "Sim.Pins": "1=K 2=A"})
    h.pieza("Simulation_SPICE", "VDC", "V2", "5V", 210, 60, 0, {1: "V5", 2: "GND"}, cd(5))
    h.texto(".tran 5u 25m", 25, 125, 2)
    h.texto("Grafica V(BOB) e I(L1): al prender, ~70 mA en la bobina; al apagar, el diodo D71 limita el pico a ~5.7 V.", 25, 135)
    hojas.append(h)

    # 6) Carga de celdas con BD140
    h = Hoja("06_carga_bd140", "Carga: GPIO43 -> BC547 -> BD140 -> 5 V al TP4056 (1 A)")
    h.pieza("Simulation_SPICE", "VPULSE", "V1", "GPIO43 3.3V", 25, 70, 0, {1: "GPIO", 2: "GND"},
            pulso(0, 3.3, "1m", "1u", "1u", "5m", "10m"))
    h.pieza("Device", "R", "R7", "1k", 45, 60, 90, {1: "GPIO", 2: "CB"})
    h.pieza("Device", "R", "R8", "10k", 58, 80, 0, {1: "CB", 2: "GND"})
    h.pieza("Transistor_BJT", "Q_NPN_CBE", "Q6", "BC547", 75, 70, 0, {1: "CC", 2: "CB", 3: "GND"}, NPN)
    h.pieza("Device", "R", "R6", "150", 90, 50, 0, {1: "BP", 2: "CC"})
    h.pieza("Device", "R", "R5", "1k", 110, 35, 90, {1: "V5", 2: "BP"})
    h.pieza("Transistor_BJT", "Q_PNP_ECB", "Q5", "BD140", 125, 50, 0, {1: "V5", 2: "CARGA", 3: "BP"}, PNP)
    h.pieza("Device", "R", "R97", "4.7", 140, 75, 0, {1: "CARGA", 2: "GND"})
    h.pieza("Simulation_SPICE", "VDC", "V2", "5V moto", 165, 60, 0, {1: "V5", 2: "GND"}, cd(5))
    h.texto(".tran 5u 10m", 25, 115, 2)
    h.texto("Grafica V(CARGA): con GPIO en alto debe quedar arriba de 4.5 V (el TP4056 necesita >4.5 V para llenar a 4.2 V).", 25, 125)
    hojas.append(h)
    return hojas


if __name__ == "__main__":
    for h in bloques():
        print("Escrito:", h.guardar())
