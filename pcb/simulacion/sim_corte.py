"""Simula el corte de chispa discreto (IRF840) contra un modelo simple de CDI de AC."""
import os
import sys

sys.argv = ["x"]
codigo = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "correr_sim.py"), encoding="utf-8").read()
exec(codigo.split("r = {}")[0])

ruta = os.path.join(AQUI, "netlist", "07_corte_chispa_ng.cir").replace(os.sep, "/")
cmd("source " + ruta)
cmd("run")
try:
    t = vec("time"); cap = vec("v(cap)"); g = vec("v(g)"); ip = vec("i(vexc)"); ic = vec("i(vsen)")
except KeyError:
    print("\n".join(SALIDA[-30:]))
    raise SystemExit(1)


def mx(t0, t1):
    return max(c for a, c in zip(t, cap) if t0 <= a <= t1)


def carga(t0, t1):
    return max(abs(c) for a, c in zip(t, ic) if t0 <= a <= t1)


print("(El capacitor del CDI se descarga en cada chispa; lo que importa es si se vuelve a cargar)")
print(f"Voltaje del capacitor sin corte (20-50 ms):   {mx(0.02, 0.05):.0f} V   corriente de carga {carga(0.02, 0.05) * 1e3:.0f} mA")
print(f"Voltaje del capacitor con corte (65-100 ms):  {mx(0.065, 0.1):.0f} V   corriente de carga {carga(0.065, 0.1) * 1e3:.1f} mA")
print(f"Voltaje del capacitor al soltar (115-150 ms): {mx(0.115, 0.15):.0f} V")
print(f"Compuerta del IRF840 prendido / apagado:    {max(g):.1f} V / {g[0]:.2f} V")
print(f"Corriente pico de la bobina de excitación:  {max(abs(x) for x in ip) * 1e3:.0f} mA")
