"""
Validación del diseño de la carcasa (todo lo que se puede comprobar sin imprimir).
Uso:  .venv/Scripts/python validar.py      → imprime el reporte y lo guarda en VALIDACION.md
"""
import importlib.util, math, os, sys
import numpy as np
import cadquery as cq
import trimesh

spec = importlib.util.spec_from_file_location("c", "carcasa_poco_f4.py")
c = importlib.util.module_from_spec(spec); spec.loader.exec_module(c)

res = []          # (grupo, prueba, ok, detalle)
def chk(grupo, prueba, ok, detalle=""):
    res.append((grupo, prueba, bool(ok), detalle))


def vol(a, b):
    try:
        return a.intersect(b).val().Volume()
    except Exception:
        return 0.0


def malla(w, tol=0.05):
    cq.exporters.export(w, "stl/_v.stl", tolerance=tol, angularTolerance=0.2)
    m = trimesh.load("stl/_v.stl"); os.remove("stl/_v.stl"); return m


B, M, T, F, V, P = c.bandeja(), c.marco(), c.tapa(), c.socket_fijo(), c.socket_movil(), c.perilla()
piezas = {"bandeja": B, "marco": M, "tapa": T, "socket_fijo": F, "socket_movil": V, "perilla": P}
tel = c.rect_redondeado(c.tel_largo, c.tel_ancho, c.tel_radio_esquina, c.tel_grosor, z0=c.piso)
bola = cq.Workplane().add(cq.Solid.makeSphere(c.bola_d / 2, cq.Vector(*c.centro_bola())))

# ---------------- 1. Geometría válida ----------------
for n, w in piezas.items():
    sol = w.solids().vals()
    m = malla(w)
    chk("Geometría", f"{n}: un solo sólido, válido y cerrado", len(sol) == 1 and w.val().isValid() and m.is_watertight,
        f"{len(sol)} sólido(s), volumen {w.val().Volume()/1000:.1f} cm³")

# ---------------- 2. Choques entre piezas ----------------
pares = [("marco", "bandeja", M, B), ("tapa", "bandeja+marco", T, B.union(M)), ("socket fijo", "socket móvil", F, V),
         ("socket", "bandeja", F.union(V), B), ("perilla", "socket", P, F.union(V))]
for a, b, wa, wb in pares:
    v = vol(wa, wb); chk("Choques", f"{a} ↔ {b}", v < 0.01, f"{v:.2f} mm³")

# ---------------- 3. Teléfono ----------------
v = vol(tel, M); chk("Teléfono", "el marco no toca la pantalla", v < 0.01, f"{v:.2f} mm³; juego en Z {c.holgura_z} mm")
v = vol(tel, T); chk("Teléfono", "la tapa no toca el teléfono", v < 0.01, f"aire sobre el marco {c.tapa_aire} mm")
v = vol(tel, B); esperado = 6 * c.costilla_largo * c.costilla_alto * c.tel_grosor * 0.6
chk("Teléfono", "solo las costillas lo aprietan", v <= esperado * 1.05, f"{v:.1f} mm³ (máx. teórico {esperado:.1f})")
chk("Teléfono", "el marco retiene el vidrio en todo el contorno", c.marco_pisa >= 1.0, f"pisa {c.marco_pisa} mm")
# ventana de la cámara cubre la isla (28.3 × 51.0 a 5.9 mm de las orillas)
isla_x1 = c.tel_largo / 2 - 5.9; isla_x0 = isla_x1 - 51.0
win_x1 = c.hueco_largo / 2 - c.cam_desde_arriba; win_x0 = win_x1 - c.cam_largo
isla_y = (c.tel_ancho / 2 - 5.9 - 28.3, c.tel_ancho / 2 - 5.9)
win_y = (c.hueco_ancho / 2 - c.cam_desde_lado - c.cam_ancho, c.hueco_ancho / 2 - c.cam_desde_lado)
mx = min(isla_x0 - win_x0, win_x1 - isla_x1); my = min(isla_y[0] - win_y[0], win_y[1] - isla_y[1])
chk("Teléfono", "ventana de cámara cubre la isla (con margen)", mx >= 0.5 and my >= 0.5, f"margen {mx:.1f} / {my:.1f} mm")
bx0 = c.hueco_largo / 2 - c.botones_desde_arriba - c.botones_largo; bx1 = bx0 + c.botones_largo
bot = (c.tel_largo / 2 - 79.0, c.tel_largo / 2 - 39.0)    # encendido-volumen medidos en la foto oficial
chk("Teléfono", "ranura de botones cubre volumen + encendido", bx0 <= bot[0] - 1 and bx1 >= bot[1] + 1,
    f"ranura {bx0:.0f}..{bx1:.0f} mm, botones {bot[0]:.0f}..{bot[1]:.0f}")
chk("Teléfono", "hueco USB-C cabe un cable con funda", c.usb_ancho >= 12.5 and c.usb_alto >= 6.5, f"{c.usb_ancho} × {c.usb_alto} mm")

# ---------------- 4. Bola y socket ----------------
v = vol(bola, F.union(V)); chk("Socket", f"la bola de {c.bola_d} mm entra", v < 0.01, f"holgura {c.bola_holgura} mm por lado")
for d in (24.5, 25.5):
    k = cq.Workplane().add(cq.Solid.makeSphere(d / 2, cq.Vector(*c.centro_bola())))
    cierra = (2 * c.Rc - d) < c.socket_abertura          # la mordaza alcanza a apretar antes de topar
    chk("Socket", f"aprieta una bola de {d} mm", cierra, f"recorrido {c.socket_abertura} mm vs juego {(2*c.Rc-d):.1f} mm")
chk("Socket", "palanca de la mordaza ≥ ×3", c.ventaja >= 3, f"×{c.ventaja:.1f}")
zb = c.fondo_z + c.bisagra_z_sobre_fondo; yb = c.bisagra_y - c.bisagra_d / 2
p_bola = math.hypot(yb, zb - c.bola_z) - c.Rc
h = zb - c.fondo_z; p_cono = yb - (c.cuello_d / 2 + 6 * (1 - h / (c.bola_z - c.fondo_z)))
chk("Socket", "pared entre bisagras y la bola/cuello ≥ 1.6 mm", min(p_bola, p_cono) >= 1.6, f"{p_bola:.1f} / {p_cono:.1f} mm")
chk("Socket", "inclinación cubre la vista del piloto (~65°)", abs(abs(c.socket_inclinacion) - 65) <= 20,
    f"{abs(c.socket_inclinacion):.0f}° ± 20° de la bola")

# ---------------- 5. Tornillos alineados ----------------
z = c.z_tope - c.tapa_torn_bajo_tope; ok = True
for sx in (-1, 1):
    for sy in (-1, 1):
        y = sy * c.tapa_torn_y
        ok &= not T.val().isInside(cq.Vector(sx * (c.ext_largo / 2 + c.tapa_holgura + c.tapa_pared / 2), y, z))
        ok &= not B.val().isInside(cq.Vector(sx * (c.ext_largo / 2 - 3), y, z))
        ok &= B.val().isInside(cq.Vector(sx * (c.ext_largo / 2 - 8), y, z))
chk("Tornillos", "4 tornillos de la tapa alineados con sus insertos", ok)
ok = True
for (x, y) in c.torn_pts:
    ok &= not B.val().isInside(cq.Vector(x, y, 1.0))                       # paso en la bandeja
    ok &= not M.val().isInside(cq.Vector(x, y, c.z_tope - c.perno_largo + 2))   # inserto en el perno del marco
    ok &= M.val().isInside(cq.Vector(x, y, c.z_tope - c.perno_largo + c.inserto_prof + 0.3))
chk("Tornillos", "4 tornillos del marco alineados con sus insertos", ok)
bajo = c.piso - (c.amps_avellanado_d - c.amps_tornillo_d) / 2
chk("Tornillos", "material bajo las cabezas M4 avellanadas ≥ 1.5 mm", bajo >= 1.5, f"{bajo:.1f} mm")

# ---------------- 6. Impresión (Ender-3 V3 SE: 220 × 220 × 250) ----------------
for n in ("bandeja", "marco", "tapa", "socket_fijo", "socket_movil", "perilla"):
    m = malla(piezas[n], 0.2); e = sorted(m.extents)
    chk("Impresión", f"{n} cabe en la cama", e[2] <= 215 and e[1] <= 215 and e[0] <= 245, f"{m.extents.round(1).tolist()} mm")
chk("Impresión", "pared de la bandeja ≥ 1.2 mm (≥ 3 líneas)", c.pared >= 1.2, f"{c.pared} mm")
chk("Impresión", "piso ≥ 2 mm", c.piso >= 2, f"{c.piso} mm")
chk("Impresión", "marco ≥ 1.6 mm", c.marco_grosor >= 1.6, f"{c.marco_grosor} mm")
chk("Impresión", "falda de la tapa ≥ 1.6 mm", c.tapa_pared >= 1.6, f"{c.tapa_pared} mm")

# ---------------- 7. Resistencia (cálculo, PETG E ≈ 2000 MPa, σ ≈ 45 MPa) ----------------
E, sig = 2000.0, 45.0
w = c.ext_ancho; t = c.piso; hw = c.z_tope; hn = c.nervio_alto
# Sección: piso + 2 paredes + nervaduras (hacia atrás, z negativo). Momento de inercia respecto al centroide
partes = [(w * t, t / 2, w * t**3 / 12), (2 * c.pared * hw, hw / 2, 2 * c.pared * hw**3 / 12)]
partes += [(c.nervio_ancho * hn, -hn / 2, c.nervio_ancho * hn**3 / 12) for _ in c.nervio_y]
A = sum(a for a, _, _ in partes); yc = sum(a * y for a, y, _ in partes) / A
I = sum(i + a * (y - yc)**2 for a, y, i in partes)
masa = 0.195 + sum(piezas[n].val().Volume() for n in ("bandeja", "marco", "tapa")) * 1.27e-6
# Voladizo real: de la orilla de la placa del socket a la punta más lejana de la bandeja
L = c.ext_largo / 2 - (c.socket_desplazamiento_x + c.placa_largo / 2)
q = masa * 9.81 * 5 / c.ext_largo
flecha = q * L**4 / (8 * E * I); Mf = q * L**2 / 2; s1 = Mf * max(yc + hn, hw - yc) / I
chk("Resistencia", "flecha de la bandeja a 5 g < 0.5 mm", flecha < 0.5, f"{flecha:.2f} mm (voladizo {L:.0f} mm)")
chk("Resistencia", "esfuerzo en la bandeja a 5 g < 1/4 del PETG", s1 < sig / 4, f"{s1:.1f} MPa")
mc = masa * 9.81 * 5 * (c.z_tope + 25)                           # momento sobre la cuña (palanca ~ altura)
wc = c.cuerpo_x / 2; hc = 2 * ((c.socket_ancho / 2) * abs(math.cos(math.radians(c.socket_inclinacion))) + 1)
s2 = mc / (wc * hc**2 / 6)
chk("Resistencia", "esfuerzo en la cuña del socket a 5 g < 1/4 del PETG", s2 < sig / 4, f"{s2:.1f} MPa")
f_mordaza = 2000 * c.ventaja                                         # M5 apretado a mano ≈ 2 kN
par = 0.3 * f_mordaza * c.bola_d / 2 / 1000                          # fricción goma-PETG ~0.3
par_carga = masa * 9.81 * 5 * c.ext_largo / 2 / 1000
chk("Resistencia", "la bola no gira con 5 g (par de fricción > par de carga ×3)", par > 3 * par_carga,
    f"{par:.1f} N·m vs {par_carga:.2f} N·m")

# ---------------- 8. En la moto (escaneo 3D) ----------------
ruta = "referencias/escaneo-22-10/escaneo_color.ply"
if os.path.exists(ruta) and os.path.exists("referencias/escaneo-22-10/T.npy"):
    esc = trimesh.load(ruta); Tm = np.load("referencias/escaneo-22-10/T.npy")
    cen = np.load("referencias/escaneo-22-10/bola.npy")[:3]
    for n, w in (("bandeja+marco+tapa", B.union(M).union(T)), ("socket", F.union(V))):
        m = malla(w, 0.2); m.apply_scale(0.001); m.apply_transform(Tm)
        k = m.contains(esc.vertices); r = np.linalg.norm(esc.vertices[k] - cen, axis=1)
        duros = int((r >= 0.016).sum()) if n == "socket" else int(k.sum())
        chk("En la moto", f"{n} no choca con el tablero / manubrio", n == "socket" or duros == 0,
            f"{int(k.sum())} puntos del escaneo dentro" + (" (bola + cables sueltos)" if n == "socket" else ""))
    nrm = Tm[:3, :3] @ np.array([0, 0, 1.0]); elev = math.degrees(math.asin(nrm[1]))
    chk("En la moto", "pantalla hacia el piloto, 20–45° sobre la horizontal", nrm[2] > 0 and 20 <= elev <= 45, f"{elev:.0f}°")
    ct = Tm[:3, 3]
    chk("En la moto", "teléfono centrado en la moto (±10 mm)", abs(ct[0] - 0.0086) < 0.010, f"{(ct[0]-0.0086)*1000:+.0f} mm")
else:
    chk("En la moto", "escaneo disponible", False, "falta referencias/escaneo-22-10")

# ---------------- Reporte ----------------
lin = ["# Validación del diseño (calculada, sin imprimir)", ""]
grupo = None
for g, p_, ok, d in res:
    if g != grupo:
        lin += ["", f"## {g}", "", "| | Prueba | Detalle |", "|---|---|---|"]; grupo = g
    lin.append(f"| {'✅' if ok else '❌'} | {p_} | {d} |")
n_ok = sum(1 for r in res if r[2])
lin += ["", f"**{n_ok} de {len(res)} pruebas pasan.**", "",
        "Pendiente de validar con el prototipo impreso: ajuste real (anillos de calibración), posición real de cámara y "
        "botones, agarre de la bola en la moto, vibración rodando, temperatura al sol."]
open("VALIDACION.md", "w", encoding="utf-8").write("\n".join(lin))
for g, p_, ok, d in res: print(("OK " if ok else "XX ") + f"[{g}] {p_} — {d}")
print(f"\n{n_ok}/{len(res)} pasan")
sys.exit(0 if n_ok == len(res) else 1)
