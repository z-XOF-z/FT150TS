"""
Placa E: EXPANSIÓN I2C para la tarjeta base FT150TS (100 x 100 mm, 2 caras, JLCPCB).

Se conecta al conector "I2C 3V3" de la placa base con 4 cables (3V3, GND, SDA, SCL) y le agrega:
  - 3 relevadores de 12 V (el 1 marcado BOMBA, con pistas anchas y diodo para el motor del inflador)
  - 1 salida aislada (colector abierto, hasta 50 mA)
  - 4 entradas aisladas de 3.3 a 16 V
Todo con un PCF8574 (DIP-16, dirección 0x20 de fábrica; con los puentes A0-A2 cambia de 0x20 a 0x27).
El LCD de la placa base usa 0x27: NO pongas los tres puentes a la vez.

Lógica (el PCF8574 solo jala bien a tierra, así que todo es activo en bajo):
  P0, P1, P2 = relevadores 1 (BOMBA), 2, 3      -> escribir 0 = prende
  P3         = salida aislada                     -> escribir 0 = conduce
  P4..P7     = entradas 1..4                      -> se lee 0 cuando hay voltaje en la entrada
Al arrancar el PCF8574 deja todo en 1: relevadores apagados.

Relevadores SRD-12VDC-SL-C: la bobina va directo a los 12 V de la moto (30 mA cada una), así no hace
falta regulador. Contactos: 10 A, pero la pista aguanta ~5 A en la BOMBA y ~3 A en los otros.
"""
ANCHO, ALTO, RADIO_ESQUINA = 100.0, 100.0, 3.0
BARRENOS = [(3.5, 3.5), (96.5, 3.5), (3.5, 96.5), (96.5, 96.5)]
TITULO = "FT150TS EXPANSION I2C v1  (PCF8574 0x20)"

F_R = "Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P2.54mm_Vertical"
F_C = "Capacitor_THT:C_Disc_D5.0mm_W2.5mm_P5.00mm"
F_CP = "Capacitor_THT:CP_Radial_D6.3mm_P2.50mm"
F_D41 = "Diode_THT:D_DO-41_SOD81_P7.62mm_Horizontal"
F_D35 = "Diode_THT:D_DO-35_SOD27_P7.62mm_Horizontal"
F_D201 = "Diode_THT:D_DO-201AD_P15.24mm_Horizontal"
F_LED = "LED_THT:LED_D3.0mm"
F_DIP4 = "Package_DIP:DIP-4_W7.62mm"
F_DIP16 = "Package_DIP:DIP-16_W7.62mm"
F_TO92 = "Package_TO_SOT_THT:TO-92_Inline_Wide"
F_RELE = "Relay_THT:Relay_SPDT_SANYOU_SRD_Series_Form_C"
def F_BORNE5(n): return f"TerminalBlock_Phoenix:TerminalBlock_Phoenix_PT-1,5-{n}-5.0-H_1x{n:02d}_P5.00mm_Horizontal"
def F_BORNE35(n): return f"TerminalBlock_Phoenix:TerminalBlock_Phoenix_PT-1,5-{n}-3.5-H_1x{n:02d}_P3.50mm_Horizontal"
def F_TIRA(n): return f"Connector_PinHeader_2.54mm:PinHeader_1x{n:02d}_P2.54mm_Vertical"


def rele(k, pin_pcf):
    """GPIO del PCF (activo en bajo) -> PC817 -> BC547 -> bobina 12 V. LED rojo = activado."""
    n = 70 + k
    return [
        (f"R{n}1", F_R, "330", {1: "+3V3", 2: f"RL{k}_A"}),
        (f"U{n}", F_DIP4, "PC817", {1: f"RL{k}_A", 2: pin_pcf, 3: f"RL{k}_E", 4: "+12V"}),
        (f"R{n}2", F_R, "1k", {1: f"RL{k}_E", 2: f"RL{k}_B"}),
        (f"R{n}3", F_R, "10k", {1: f"RL{k}_B", 2: "GND"}),
        (f"Q{n}", F_TO92, "BC547", {1: f"K{k}_BOB", 2: f"RL{k}_B", 3: "GND"}),     # 1=C 2=B 3=E
        (f"D{n}", F_D41, "1N4007", {1: "+12V", 2: f"K{k}_BOB"}),                   # rueda libre
        (f"R{n}4", F_R, "2.2k", {1: "+12V", 2: f"LED{k}_A"}),
        (f"LED{k}", F_LED, "rojo", {1: f"K{k}_BOB", 2: f"LED{k}_A"}),
    ]


def entrada(i, pin_pcf):
    """Entrada aislada 3.3 a 16 V (igual que en la placa base). Activa = se lee 0."""
    n = 80 + i
    a = f"IN{i}_A"
    return [
        (f"R{n}0", F_R, "1.5k", {1: f"IN{i}", 2: a}),
        (f"D{n}", F_D35, "1N4148", {1: a, 2: "GND"}),
        (f"R{n}5", F_R, "2.2k", {1: a, 2: "GND"}),
        (f"U{n}", F_DIP4, "PC817", {1: a, 2: "GND", 3: "GND", 4: pin_pcf}),
    ]


PIEZAS = [
    # --- I2C desde la placa base (y salida para encadenar otro módulo)
    ("J1", F_TIRA(5), "I2C ENTRADA", {1: "+3V3", 2: "GND", 3: "SDA", 4: "SCL", 5: "INT"}),
    ("J2", F_TIRA(4), "I2C SALIDA", {1: "+3V3", 2: "GND", 3: "SDA", 4: "SCL"}),
    ("R1", F_R, "10k", {1: "+3V3", 2: "SDA"}),
    ("R2", F_R, "10k", {1: "+3V3", 2: "SCL"}),
    ("R3", F_R, "10k", {1: "+3V3", 2: "INT"}),
    # --- PCF8574: 1 A0, 2 A1, 3 A2, 4-7 P0-P3, 8 GND, 9-12 P4-P7, 13 INT, 14 SCL, 15 SDA, 16 VDD
    ("U1", F_DIP16, "PCF8574P", {1: "A0", 2: "A1", 3: "A2", 4: "P0", 5: "P1", 6: "P2", 7: "P3", 8: "GND",
                                 9: "P4", 10: "P5", 11: "P6", 12: "P7", 13: "INT", 14: "SCL", 15: "SDA", 16: "+3V3"}),
    ("C2", F_C, "100nF", {1: "+3V3", 2: "GND"}),
    ("R4", F_R, "10k", {1: "A0", 2: "GND"}),
    ("R5", F_R, "10k", {1: "A1", 2: "GND"}),
    ("R6", F_R, "10k", {1: "A2", 2: "GND"}),
    ("JP1", F_TIRA(2), "A0", {1: "A0", 2: "+3V3"}),        # con puente (jumper) = 1
    ("JP2", F_TIRA(2), "A1", {1: "A1", 2: "+3V3"}),
    ("JP3", F_TIRA(2), "A2", {1: "A2", 2: "+3V3"}),
    # --- 12 V de la moto para las bobinas de los relevadores
    ("J3", F_BORNE5(2), "12V", {1: "+12V_IN", 2: "GND"}),
    ("D1", F_D41, "1N4007", {1: "+12V", 2: "+12V_IN"}),     # polaridad invertida
    ("C1", F_CP, "100uF 25V", {1: "+12V", 2: "GND"}),
    # --- Relevador 1: BOMBA (inflador, ~3 A). Bornera: BAT+ (fusible 5 A), MOTOR+, MOTOR-, BAT-
    *rele(1, "P0"),
    ("K1", F_RELE, "SRD-12VDC-SL-C", {1: "BOMBA_12V", 2: "+12V", 5: "K1_BOB", 3: "K1_NC", 4: "MOTOR_P"}),
    ("J71", F_BORNE5(4), "BOMBA", {1: "BOMBA_12V", 2: "MOTOR_P", 3: "GND", 4: "GND"}),
    ("D79", F_D201, "1N5408", {1: "MOTOR_P", 2: "GND"}),    # 1=K: chispa del motor al soltar
    # --- Relevadores 2 y 3: contacto seco NA / COM / NC
    *rele(2, "P1"),
    ("K2", F_RELE, "SRD-12VDC-SL-C", {1: "K2_COM", 2: "+12V", 5: "K2_BOB", 3: "K2_NC", 4: "K2_NA"}),
    ("J72", F_BORNE5(3), "RELE 2", {1: "K2_NA", 2: "K2_COM", 3: "K2_NC"}),
    *rele(3, "P2"),
    ("K3", F_RELE, "SRD-12VDC-SL-C", {1: "K3_COM", 2: "+12V", 5: "K3_BOB", 3: "K3_NC", 4: "K3_NA"}),
    ("J73", F_BORNE5(3), "RELE 3", {1: "K3_NA", 2: "K3_COM", 3: "K3_NC"}),
    # --- Salida aislada <- P3
    ("R90", F_R, "330", {1: "+3V3", 2: "SAL_A"}),
    ("U90", F_DIP4, "PC817", {1: "SAL_A", 2: "P3", 3: "SAL_E", 4: "SAL_C"}),
    ("J90", F_BORNE35(2), "SALIDA", {1: "SAL_C", 2: "SAL_E"}),
    # --- 4 entradas aisladas -> P4..P7
    ("J80", F_BORNE35(5), "ENTRADAS", {1: "IN1", 2: "IN2", 3: "IN3", 4: "IN4", 5: "GND"}),
    *entrada(1, "P4"), *entrada(2, "P5"), *entrada(3, "P6"), *entrada(4, "P7"),
]

# Piezas grandes en lugar fijo: (x, y, rotación) del pad 1. Borneras: 0 = cable por abajo,
# 90 = por la derecha, 180 = por arriba.
ANCLAS = {
    "J1": (10.0, 4.0, 90), "J2": (34.0, 4.0, 90), "J3": (66.0, 6.0, 180),
    "U1": (40.0, 16.0, 0),
    "JP1": (29.0, 17.0, 90), "JP2": (29.0, 21.0, 90), "JP3": (29.0, 25.0, 90),
    "K1": (6.0, 70.0, 0), "J71": (10.0, 93.0, 0), "D79": (8.0, 84.0, 0),
    "K2": (35.0, 70.0, 0), "J72": (37.0, 93.0, 0),
    "K3": (62.0, 70.0, 0), "J73": (64.0, 93.0, 0),
    "U71": (8.0, 50.0, 0), "U72": (37.0, 50.0, 0), "U73": (64.0, 50.0, 0),
    "J80": (94.0, 62.0, 90), "J90": (94.0, 38.0, 90),
    "U81": (74.0, 26.0, 0), "U82": (74.0, 34.0, 0), "U83": (74.0, 42.0, 0), "U84": (74.0, 50.0, 0),
    "U90": (74.0, 18.0, 0),
}

# Clases de pista: nombre -> (ancho mm, separación mm, redes)
CLASES = {
    "Bomba": (3.0, 0.4, ["BOMBA_12V", "MOTOR_P"]),
    "Contactos": (2.0, 0.4, ["K2_COM", "K2_NA", "K2_NC", "K3_COM", "K3_NA", "K3_NC", "K1_NC"]),
    "Potencia": (1.0, 0.3, ["+12V_IN", "+12V", "GND"]),
}
PISTA, SEPARACION = 0.4, 0.25

ETIQUETAS = {
    "J1": "IN 3V3 G SDA SCL INT", "J2": "OUT 3V3 G SDA SCL", "J3": "12V MOTO: + -",
    "J71": "BOMBA: BAT+ MOT+ MOT- BAT-", "J72": "RELE2 NA COM NC", "J73": "RELE3 NA COM NC",
    "J80": "IN1 IN2 IN3 IN4 GND", "J90": "SAL C E",
}
TEXTOS = [
    (TITULO, 44.0, 40.0, 1.2),
    ("P0=BOMBA P1=R2 P2=R3 P3=SAL P4-7=IN (activo en 0)", 44.0, 42.6, 0.9),
    ("Puentes A0-A2: no poner los 3 (0x27 = LCD)", 44.0, 44.8, 0.9),
    ("A0", 25.5, 17.0, 1.0), ("A1", 25.5, 21.0, 1.0), ("A2", 25.5, 25.0, 1.0), ("I2C", 6.0, 8.5, 1.0),
]
# Zonas donde el acomodo automático no pone piezas (aquí van los textos)
RESERVADO = [(17.0, 38.5, 71.0, 46.0)]
