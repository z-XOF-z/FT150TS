"""Segunda pasada de ruteo: carga la placa ya ruteada, quita el plano de tierra, vuelve a mandar
a Freerouting (que respeta las pistas que ya hay y solo termina lo que falta) y rellena el plano."""
import sys
import pcbnew
sys.argv = [sys.argv[0], sys.argv[1] if len(sys.argv) > 1 else "B"]
import generar_pcb as G

variante = G.VARIANTE
ruta = f"ft150ts_{variante}.kicad_pcb"
b = pcbnew.LoadBoard(ruta)
for z in list(b.Zones()):
    b.Remove(z)
G.rutear(b, variante, 60.0)
G.plano_tierra(b, [pcbnew.B_Cu, pcbnew.F_Cu] if variante == "B" else [pcbnew.B_Cu])
b.Save(ruta)
print("Guardado:", ruta)
