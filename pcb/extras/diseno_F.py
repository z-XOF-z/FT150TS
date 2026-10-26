"""
Placa F: DISTRIBUCIÓN CON FUSIBLES para accesorios de la moto (100 x 90 mm, 2 caras, JLCPCB).

12 V de la batería entran por J1 y salen por 6 salidas, cada una con su fusible de vidrio 5x20
(portafusible de clip para circuito impreso: se consigue en cualquier tienda de electrónica).
El LED de cada salida prende cuando su fusible se fundió (y hay algo conectado a esa salida).

- Total por la placa: máximo 10 A (las borneras de 5 mm aguantan ~13 A). Pon un fusible de línea
  de 10 A en el cable de la batería a J1.
- Fusible por salida: el que pida el aparato (ej. bomba/inflador 5 A, USB 2 A, cámara 1 A).
- Pistas: bus de 12 V de 4 mm por las dos caras y tierra en plano por las dos caras.
"""
ANCHO, ALTO, RADIO_ESQUINA = 100.0, 90.0, 3.0
BARRENOS = [(3.5, 3.5), (96.5, 3.5), (3.5, 86.5), (96.5, 86.5)]
TITULO = "FT150TS FUSIBLES v1  max 10A"

F_R = "Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P2.54mm_Vertical"
F_LED = "LED_THT:LED_D3.0mm"
F_FUS = "Fuse:Fuseholder_Clip-5x20mm_Littelfuse_445-030_Inline_P20.50x5.20mm_D1.30mm_Horizontal"
def F_BORNE5(n): return f"TerminalBlock_Phoenix:TerminalBlock_Phoenix_PT-1,5-{n}-5.0-H_1x{n:02d}_P5.00mm_Horizontal"

N = 6
PASO = 11.5
Y0 = 16.0

PIEZAS = [("J1", F_BORNE5(2), "BATERIA 12V", {1: "BUS12", 2: "GND"})]
ANCLAS = {"J1": (6.0, 42.0, 270)}
ETIQUETAS = {"J1": "BAT 12V: + -"}
for i in range(1, N + 1):
    y = Y0 + (i - 1) * PASO
    PIEZAS += [
        (f"F{i}", F_FUS, "5x20", {1: "BUS12", 2: f"OUT{i}"}),
        (f"R{i}", F_R, "2.2k", {1: "BUS12", 2: f"LED{i}_A"}),
        (f"LED{i}", F_LED, "rojo", {1: f"OUT{i}", 2: f"LED{i}_A"}),     # 1=K 2=A: prende si F se funde
        (f"J{i + 1}", F_BORNE5(2), f"SALIDA {i}", {1: f"OUT{i}", 2: "GND"}),
    ]
    ANCLAS[f"F{i}"] = (30.0, y, 0)
    ANCLAS[f"LED{i}"] = (62.0, y, 0)
    ANCLAS[f"R{i}"] = (25.0, y + 4.0, 90)
    ANCLAS[f"J{i + 1}"] = (93.0, y + 3.0, 90)
    ETIQUETAS[f"J{i + 1}"] = f"SAL{i} + -"

CLASES = {
    "Bus": (4.0, 0.5, ["BUS12", "GND"]),
    "Salidas": (2.5, 0.5, [f"OUT{i}" for i in range(1, N + 1)]),
}
PISTA, SEPARACION = 0.5, 0.3
TEXTOS = [(TITULO, 50.0, 86.0, 1.2)]
RESERVADO = []
