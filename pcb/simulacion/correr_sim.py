"""Corre los netlists exportados de KiCad con el ngspice de KiCad (ngspice.dll) y saca los números clave."""
import ctypes
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
KBIN = os.path.dirname(sys.executable)
os.add_dll_directory(KBIN)
ng = ctypes.CDLL(os.path.join(KBIN, "ngspice.dll"))

SALIDA = []
PF = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_void_p)
EX = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_int, ctypes.c_bool, ctypes.c_bool, ctypes.c_int, ctypes.c_void_p)
BG = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_bool, ctypes.c_int, ctypes.c_void_p)
_imp = PF(lambda s, i, v: (SALIDA.append(s.decode(errors="ignore")), 0)[1])
_est = PF(lambda s, i, v: 0)
_sal = EX(lambda *a: 0)
_bg = BG(lambda *a: 0)
ng.ngSpice_Init(_imp, _est, _sal, None, None, _bg, None)


class VecInfo(ctypes.Structure):
    _fields_ = [("v_name", ctypes.c_char_p), ("v_type", ctypes.c_int), ("v_flags", ctypes.c_short),
                ("v_realdata", ctypes.POINTER(ctypes.c_double)), ("v_compdata", ctypes.c_void_p),
                ("v_length", ctypes.c_int)]


ng.ngGet_Vec_Info.restype = ctypes.POINTER(VecInfo)


def cmd(c):
    ng.ngSpice_Command(c.encode())


def vec(nombre):
    p = ng.ngGet_Vec_Info(nombre.encode())
    if not p:
        raise KeyError(nombre)
    v = p.contents
    return [v.v_realdata[i] for i in range(v.v_length)]


def correr(nombre):
    src = open(os.path.join(AQUI, "netlist", nombre + ".cir"), encoding="utf-8").read()
    src = src.replace(" GND ", " 0 ").replace(" GND\n", " 0\n").replace("/", "")
    src = src.replace('include "C:', 'include "C:/').replace("C://", "C:/")
    # .include con la ruta real (quitamos las diagonales de los nombres de red, no de la ruta)
    src = "\n".join(l if not l.startswith(".include") else f'.include "{os.path.join(AQUI, "modelos.lib")}"'
                    for l in src.splitlines())
    tmp = os.path.join(AQUI, "netlist", nombre + "_ng.cir")
    open(tmp, "w", encoding="utf-8").write(src)
    cmd("destroy all")
    cmd(f"source {tmp}")
    cmd("run")


def promedio(v, t, t0, t1):
    pares = [(a, b) for a, b in zip(t, v) if t0 <= a <= t1]
    if len(pares) < 2:
        return 0.0
    area = sum((pares[i + 1][0] - pares[i][0]) * (pares[i][1] + pares[i + 1][1]) / 2 for i in range(len(pares) - 1))
    return area / (pares[-1][0] - pares[0][0])


r = {}
# 1) Bobina
correr("01_entrada_bobina")
t = vec("time"); g = vec("v(gpio16)"); ra = vec("v(rpm_a)"); oa = vec("v(opt_a)")
iled = vec("i(v.xu9.vsensor)")
pr9 = [(a - b) ** 2 / 47e3 for a, b in zip(ra, oa)]
r["1 Bobina: corriente pico LED PC817"] = f"{max(iled) * 1e3:.1f} mA (máx. 50 mA)"
r["1 Bobina: GPIO16 durante el pulso"] = f"{min(g):.2f} V (baja = pulso detectado)"
r["1 Bobina: GPIO16 en reposo"] = f"{g[-1]:.2f} V"
r["1 Bobina: potencia promedio en R9 a 6000 rpm"] = f"{promedio(pr9, t, 0.0, t[-1]) * 1e3:.1f} mW (es de 1 W)"
r["1 Bobina: pico de potencia en R9"] = f"{max(pr9):.2f} W (solo 30 us)"
# 2) Entrada aislada
correr("02_entrada_aislada")
t = vec("time"); e = vec("v(ent)"); g = vec("v(gpio)"); iled = vec("i(v.xu12.vsensor)")
umbral = next((ev for ev, gv in zip(e, g) if gv < 0.8), None)
r["2 Entrada: voltaje de entrada donde el GPIO lee LOW"] = f"{umbral:.2f} V" if umbral else "nunca"
r["2 Entrada: corriente LED a 3.3 V"] = f"{next(i for ev, i in zip(e, iled) if ev >= 3.3) * 1e3:.2f} mA"
r["2 Entrada: corriente LED a 16 V"] = f"{iled[-1] * 1e3:.2f} mA (máx. 50 mA)"
r["2 Entrada: potencia en R120 a 16 V"] = f"{(iled[-1] ** 2) * 1500 * 1e3:.0f} mW (es de 250 mW)"
# 3) Sensor
correr("03_entrada_sensor")
g = vec("v(gpio)")
r["3 Sensor 5V: voltaje máximo en el GPIO"] = f"{max(g):.2f} V (límite 3.6 V)"
r["3 Sensor 5V: voltaje mínimo en el GPIO"] = f"{min(g):.2f} V"
# 4) Divisores
correr("04_divisores")
r["4 Divisor moto 16 V -> GPIO1"] = f"{vec('v(adc1)')[0]:.3f} V"
r["4 Divisor celdas 4.2 V -> GPIO4"] = f"{vec('v(adc4)')[0]:.3f} V"
# 5) Relevador
correr("05_rele")
t = vec("time"); bob = vec("v(bob)"); il = vec("i(l1)")
on = [i for a, i in zip(t, il) if 9e-3 < a < 10.5e-3]
r["5 Rele: corriente en la bobina (prendido)"] = f"{max(on) * 1e3:.1f} mA (SRD-05: ~71 mA)"
r["5 Rele: voltaje en el colector prendido"] = f"{min(b for a, b in zip(t, bob) if 9e-3 < a < 10.5e-3):.2f} V (saturado < 0.3)"
r["5 Rele: pico al apagar (con diodo)"] = f"{max(b for a, b in zip(t, bob) if a > 11e-3):.2f} V (sin diodo serían cientos)"
# 6) Carga
correr("06_carga_bd140")
t = vec("time"); vc = vec("v(carga)")
r["6 Carga: voltaje al TP4056 con 1 A"] = f"{min(v for a, v in zip(t, vc) if 3e-3 < a < 5.5e-3):.2f} V (necesita > 4.5 V)"
r["6 Carga: voltaje con GPIO apagado"] = f"{vc[-1]:.3f} V"

for k, v in r.items():
    print(f"{k:50s} {v}")
errs = [s for s in SALIDA if "rror" in s]
if errs:
    print("\nErrores de ngspice:", *errs[:10], sep="\n")
