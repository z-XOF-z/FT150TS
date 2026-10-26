"""
FT150TS - tarjeta base v2 para el ESP32-S3-DevKitC-1 (circuito común a las dos placas).

  Placa A: una cara, para hacerla en casa con plancha (100 x 100 mm, puentes de alambre).
  Placa B: dos caras, para mandar a JLCPCB (100 x 100 mm, la tarifa más barata).

Este archivo es el "esquemático en texto": cada pieza con su huella de KiCad, su valor y a qué
red va cada pin. generar_pcb.py lo convierte en placa. Todas las piezas son de agujero (THT)
para que las puedas soldar tú.

Alimentación: moto de 10.5 a 16 V (con picos). Respaldo: 1 a 3 celdas 18650 en paralelo (3.0 a
4.2 V, con su BMS) que alimentan al ESP32 directo, sin elevador. Las carga un módulo TP4056 que el
ESP32 prende cuando hace falta.
"""

# ---------------------------------------------------------------- placa
ANCHO = 100.0
ALTO = 100.0
RADIO_ESQUINA = 3.0
BARRENOS = [(3.5, 3.5), (96.5, 3.5), (3.5, 96.5), (96.5, 96.5)]   # M3

PISTA = 0.6            # señales (sale bien con plancha)
PISTA_POTENCIA = 1.2   # 12 V, 5 V, GND y relevadores
SEPARACION = 0.4
SEPARACION_AV = 1.5    # bobina y cable de paro del CDI (cientos de volts)
REDES_POTENCIA = ["+12V_IN", "+12V_F", "+12V", "+5V_MOTO", "+5V", "CELDA", "GND", "CARGA5", "LM_SW",
                  "K1_COM", "K1_NA", "K1_NC", "K2_COM", "K2_NA", "K2_NC", "K3_COM", "K3_NA", "K3_NC"]
REDES_ALTO_VOLTAJE = ["CDI_PARO", "BOBINA", "RPM_A"]
# El drenaje del IRF840 también lleva alto voltaje, pero sus patas vienen a 2.54 mm (así es el
# encapsulado TO-220, hecho para 500 V): su clase pide 0.6 mm, lo que permite el encapsulado.
REDES_DRENAJE = ["CORTE_D"]

# ---------------------------------------------------------------- huellas
F_SOCKET22 = "Connector_PinSocket_2.54mm:PinSocket_1x22_P2.54mm_Vertical"
F_R = "Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P2.54mm_Vertical"   # paradas: ocupan menos
F_R_1W = "Resistor_THT:R_Axial_DIN0414_L11.9mm_D4.5mm_P15.24mm_Horizontal"
F_C = "Capacitor_THT:C_Disc_D5.0mm_W2.5mm_P5.00mm"
F_CP_35 = "Capacitor_THT:CP_Radial_D6.3mm_P2.50mm"
F_CP_10 = "Capacitor_THT:CP_Radial_D5.0mm_P2.00mm"
F_D41 = "Diode_THT:D_DO-41_SOD81_P7.62mm_Horizontal"
F_D15 = "Diode_THT:D_DO-15_P10.16mm_Horizontal"
F_D35 = "Diode_THT:D_DO-35_SOD27_P7.62mm_Horizontal"
F_LED = "LED_THT:LED_D3.0mm"
F_PTC = "Fuse:Fuse_Bourns_MF-RG300"            # 2 patas a 5.1 mm: cualquier PTC radial de 2 A
F_DIP4 = "Package_DIP:DIP-4_W7.62mm"
F_DIP6 = "FT150TS:DIP-6_W7.62mm_SinPin5"      # PhotoMOS: sin pin 5 (no se usa) = más separación
F_TO92 = "Package_TO_SOT_THT:TO-92_Inline_Wide"      # patas a 2.54 mm: más fácil de soldar
F_TO220 = "Package_TO_SOT_THT:TO-220-3_Vertical"
F_TO220_5 = "Package_TO_SOT_THT:TO-220-5_P3.4x3.7mm_StaggerOdd_Lead3.8mm_Vertical"   # LM2596T
F_TO126 = "Package_TO_SOT_THT:TO-126-3_Vertical"
F_BOBINA = "Inductor_THT:L_Radial_D10.0mm_P5.00mm_Fastron_07M"
F_D201 = "Diode_THT:D_DO-201AD_P5.08mm_Vertical_AnodeUp"   # parado: ocupa menos
F_CP_220 = "Capacitor_THT:CP_Radial_D8.0mm_P3.50mm"
F_RELE = "Relay_THT:Relay_SPDT_SANYOU_SRD_Series_Form_C"
def F_BORNE35(n): return f"TerminalBlock_Phoenix:TerminalBlock_Phoenix_PT-1,5-{n}-3.5-H_1x{n:02d}_P3.50mm_Horizontal"
def F_BORNE5(n): return f"TerminalBlock_Phoenix:TerminalBlock_Phoenix_PT-1,5-{n}-5.0-H_1x{n:02d}_P5.00mm_Horizontal"
def F_TIRA(n): return f"Connector_PinHeader_2.54mm:PinHeader_1x{n:02d}_P2.54mm_Vertical"

# ---------------------------------------------------------------- DevKitC-1
# Espressif ESP32-S3-DevKitC-1 v1.1 (tablas J1/J3 y plano DXF 2022-04-29): 25.40 x 62.74 mm,
# filas a 22.86 mm, pin 1 de las dos filas del lado de la antena.
J1 = ["+3V3", "+3V3", None, "IO4", "IO5", "IO6", "IO7", "IO15", "IO16", "IO17", "IO18", "IO8",
      "IO3", "IO46", "IO9", "IO10", "IO11", "IO12", "IO13", "IO14", "+5V", "GND"]
J3 = ["GND", "IO43", "IO44", "IO1", "IO2", "IO42", "IO41", "IO40", "IO39", "IO38", None, None,
      None, None, "IO45", "IO48", "IO47", "IO21", None, None, "GND", "GND"]


def entrada_opto(n, borne_red, gpio, r_val="1.5k"):
    """Entrada aislada universal: acepta 3.3, 5, 12 o 16 V sin mover nada. Detecta desde ~1.9 V
    (la 2.2k en paralelo con el LED evita lecturas falsas por ruido o fugas en el cable).
    La salida del PC817 jala el GPIO a tierra (pull-up interno del ESP32): activa = LOW."""
    a = f"OPT{n}_A"
    return [
        (f"R{n}0", F_R, r_val, {1: borne_red, 2: a}),
        (f"D{n}0", F_D35, "1N4148", {1: a, 2: "GND"}),          # 1=K: protege el LED en inversa
        (f"R{n}5", F_R, "2.2k", {1: a, 2: "GND"}),              # sube el umbral a ~1.9 V (menos ruido)
        (f"U{n}", F_DIP4, "PC817", {1: a, 2: "GND", 3: "GND", 4: gpio}),
    ]


def rele(n, k, gpio):
    """Relevador 5 V aislado: GPIO -> PC817 -> BC547 -> bobina. LED rojo = activado."""
    return [
        (f"R{n}1", F_R, "470", {1: gpio, 2: f"RL{k}_A"}),
        (f"U{n}", F_DIP4, "PC817", {1: f"RL{k}_A", 2: "GND", 3: f"RL{k}_E", 4: "+5V_MOTO"}),
        (f"R{n}2", F_R, "1k", {1: f"RL{k}_E", 2: f"RL{k}_B"}),
        (f"R{n}3", F_R, "10k", {1: f"RL{k}_B", 2: "GND"}),
        (f"Q{n}", F_TO92, "BC547", {1: f"K{k}_BOB", 2: f"RL{k}_B", 3: "GND"}),     # 1=C 2=B 3=E
        (f"D{n}", F_D41, "1N4007", {1: "+5V_MOTO", 2: f"K{k}_BOB"}),               # rueda libre
        (f"R{n}4", F_R, "1k", {1: "+5V_MOTO", 2: f"LED{k}_A"}),
        (f"LED{k}", F_LED, "rojo", {1: f"K{k}_BOB", 2: f"LED{k}_A"}),             # 1=K 2=A
        (f"K{k}", F_RELE, "SRD-05VDC-SL-C", {1: f"K{k}_COM", 2: "+5V_MOTO", 5: f"K{k}_BOB",
                                             3: f"K{k}_NC", 4: f"K{k}_NA"}),
        (f"J{n}", F_BORNE5(3), f"RELE {k}", {1: f"K{k}_NA", 2: f"K{k}_COM", 3: f"K{k}_NC"}),
    ]


def entrada_sensor(n, gpio):
    """Entrada de sensor 3.3 o 5 V: 2.2k en serie y diodo Schottky al 3.3 V. Con 5 V el diodo
    recorta y el ESP32 lee bien; con 3.3 V pasa igual. También sirve para colector abierto.
    Diodo: 1N5819 (o BAT85 / 1N5817 si los tienes)."""
    return [
        (f"R{n}", F_R, "2.2k", {1: f"S{n}", 2: gpio}),
        (f"D{n}", F_D41, "1N5819", {1: "+3V3", 2: gpio}),         # 1=K a 3.3 V (Schottky común)
    ]


# ---------------------------------------------------------------- piezas: (ref, huella, valor, {pin: red})
PIEZAS = [
    # --- ESP32-S3-DevKitC-1 en dos tiras hembra
    ("U1A", F_SOCKET22, "DevKitC-1 J1", {i + 1: n for i, n in enumerate(J1) if n}),
    # Fila J3 a 25.40 mm de J1: para la copia ancha de AliExpress (ESP32-S3 N16R8, 27.94 mm de
    # ancho). El DevKitC-1 original tiene las filas a 22.86 mm y NO entra (cambiar FILA_J3).
    ("U1B", F_SOCKET22, "DevKit ancho J3", {i + 1: n for i, n in enumerate(J3) if n}),

    # --- Entrada de la moto: fusible reseteable, polaridad invertida, picos y regulador LM2596
    #     (buck de 3 A, hasta 40 V de entrada): con 12 V y más de 1 A un 7805 se cocinaría.
    ("J1", F_BORNE5(2), "BAT MOTO 12V", {1: "+12V_IN", 2: "GND"}),
    ("F1", F_PTC, "PTC 2A 30V", {1: "+12V_IN", 2: "+12V_F"}),         # fusible reseteable radial
    ("D1", F_D201, "1N5822", {1: "+12V", 2: "+12V_F"}),              # 1=K 2=A (3 A)
    ("D2", F_D15, "P6KE22A", {1: "+12V", 2: "GND"}),                 # TVS (18.8 V sin conducir)
    ("C1", F_CP_220, "220uF 35V", {1: "+12V", 2: "GND"}),
    ("C2", F_C, "100nF", {1: "+12V", 2: "GND"}),
    ("U2", F_TO220_5, "LM2596T-5.0", {1: "+12V", 2: "LM_SW", 3: "GND", 4: "+5V_MOTO", 5: "GND"}),  # 4=FB 5=ON/OFF
    ("L1", F_BOBINA, "33uH 3A", {1: "LM_SW", 2: "+5V_MOTO"}),
    ("D7", F_D201, "1N5822", {1: "LM_SW", 2: "GND"}),                # 1=K 2=A
    ("C3", F_CP_220, "220uF 10V", {1: "+5V_MOTO", 2: "GND"}),
    ("C4", F_C, "100nF", {1: "+5V_MOTO", 2: "GND"}),

    # --- Respaldo: celdas 18650 en paralelo (3.0 a 4.2 V, con su BMS). Con la moto prendida manda
    #     la moto; apagada, las celdas alimentan al ESP32 por el pin 5V (su regulador las baja a
    #     3.3 V). Sin elevador. Los relevadores solo funcionan con la moto (+5V_MOTO).
    ("J2", F_BORNE35(2), "BATERIA 3.7V", {1: "CELDA", 2: "GND"}),
    ("D3", F_D201, "1N5822", {1: "+5V", 2: "+5V_MOTO"}),             # 3 A: ESP32, LCD, GPS, LEDs
    ("D4", F_D41, "1N5819", {1: "+5V", 2: "CELDA"}),
    ("C5", F_CP_10, "100uF 10V", {1: "+5V", 2: "GND"}),

    # --- Mediciones: batería de la moto -> GPIO1 (100k/22k: 16 V = 2.9 V) y celdas -> GPIO4
    #     (100k/100k: 4.2 V = 2.1 V)
    ("R1", F_R, "100k", {1: "+12V_IN", 2: "IO1"}),
    ("R2", F_R, "22k", {1: "IO1", 2: "GND"}),
    ("C6", F_C, "100nF", {1: "IO1", 2: "GND"}),
    ("R3", F_R, "100k", {1: "CELDA", 2: "IO4"}),
    ("R4", F_R, "100k", {1: "IO4", 2: "GND"}),
    ("C7", F_C, "100nF", {1: "IO4", 2: "GND"}),

    # --- Carga: el ESP32 (GPIO43) prende el BD140 y le da 5 V de la moto al módulo TP4056, que
    #     carga las celdas y se corta solo al llenarlas. El BMS de las celdas no es cargador.
    ("Q5", F_TO126, "BD140", {1: "+5V_MOTO", 2: "CARGA5", 3: "CARGA_BP"}),        # 1=E 2=C 3=B
    ("R5", F_R, "1k", {1: "+5V_MOTO", 2: "CARGA_BP"}),
    ("R6", F_R, "150", {1: "CARGA_BP", 2: "CARGA_C"}),
    ("Q6", F_TO92, "BC547", {1: "CARGA_C", 2: "CARGA_B", 3: "GND"}),
    ("R7", F_R, "1k", {1: "IO43", 2: "CARGA_B"}),
    ("R8", F_R, "10k", {1: "CARGA_B", 2: "GND"}),
    ("J3", F_BORNE35(2), "CARGADOR 5V", {1: "CARGA5", 2: "GND"}),                # a IN+/IN- del TP4056

    # --- RPM: cable directo de la bobina. 1N4007 + 47k 1 W bajan los cientos de volts a unos mA
    #     para el PC817; el 4.7nF quita los rebotes del pulso. Salida -> GPIO16
    ("J4", F_BORNE5(2), "BOBINA", {1: "BOBINA", 2: "GND"}),
    ("D5", F_D41, "1N4007", {1: "RPM_A", 2: "BOBINA"}),
    ("R9", F_R_1W, "47k 1W", {1: "RPM_A", 2: "OPT9_A"}),
    ("D6", F_D35, "1N4148", {1: "OPT9_A", 2: "GND"}),
    ("C8", F_C, "4.7nF", {1: "OPT9_A", 2: "GND"}),
    ("U9", F_DIP4, "PC817", {1: "OPT9_A", 2: "GND", 3: "GND", 4: "IO16"}),

    # --- Entradas aisladas universales (3.3 a 16 V)
    ("J5", F_BORNE35(2), "LLAVE", {1: "LLAVE", 2: "GND"}),
    *entrada_opto(11, "LLAVE", "IO21"),
    ("J6", F_BORNE35(4), "ENTRADAS 3-16V", {1: "ENT1", 2: "ENT2", 3: "ENT3", 4: "GND"}),
    *entrada_opto(12, "ENT1", "IO18"),    # neutral
    *entrada_opto(13, "ENT2", "IO3"),     # libre (freno, direccional...)
    *entrada_opto(14, "ENT3", "IO46"),    # libre

    # --- Sensores de 3.3 o 5 V (se leen solos): cada tira trae 5V, 3V3, GND y señal
    ("J7", F_TIRA(4), "HALL", {1: "+5V", 2: "+3V3", 3: "GND", 4: "S20"}),
    *entrada_sensor(20, "IO17"),
    ("J8", F_BORNE35(2), "QUICKSHIFT", {1: "S21", 2: "GND"}),
    *entrada_sensor(21, "IO48"),
    ("C9", F_C, "100nF", {1: "IO48", 2: "GND"}),
    ("J9", F_TIRA(4), "DS18B20", {1: "+5V", 2: "+3V3", 3: "GND", 4: "IO14"}),
    ("R22", F_R, "4.7k", {1: "+3V3", 2: "IO14"}),                          # 1-Wire a 3.3 V

    # --- Gasolina (flotador a tierra) -> GPIO2 y LDR -> GPIO5
    ("J10", F_BORNE35(2), "GASOLINA", {1: "IO2", 2: "GND"}),
    ("R23", F_R, "1k", {1: "+3V3", 2: "IO2"}),                             # <-- X según tu flotador
    ("C10", F_C, "100nF", {1: "IO2", 2: "GND"}),
    ("J11", F_TIRA(2), "LDR", {1: "+3V3", 2: "IO5"}),
    ("R24", F_R, "10k", {1: "IO5", 2: "GND"}),

    # --- I2C a 3.3 V (RTC, MPU6050, y para crecer: MCP23017/ADS1115) y a 5 V para el LCD (2N7000)
    ("J12", F_TIRA(4), "I2C 3V3", {1: "+3V3", 2: "GND", 3: "IO8", 4: "IO9"}),
    ("Q7", F_TO92, "2N7000", {1: "IO8", 2: "+3V3", 3: "SDA5"}),            # 1=S 2=G 3=D
    ("Q8", F_TO92, "2N7000", {1: "IO9", 2: "+3V3", 3: "SCL5"}),
    ("R25", F_R, "10k", {1: "+5V", 2: "SDA5"}),
    ("R26", F_R, "10k", {1: "+5V", 2: "SCL5"}),
    ("J13", F_TIRA(4), "LCD 5V", {1: "+5V", 2: "GND", 3: "SDA5", 4: "SCL5"}),

    # --- Botones, SD, GPS y tira de LEDs
    ("J14", F_TIRA(4), "BOTONES", {1: "IO6", 2: "IO7", 3: "IO15", 4: "GND"}),
    ("J15", F_TIRA(6), "SD", {1: "+5V", 2: "GND", 3: "IO10", 4: "IO11", 5: "IO12", 6: "IO13"}),
    ("J16", F_TIRA(4), "GPS", {1: "+5V", 2: "GND", 3: "IO41", 4: "IO42"}),
    ("J17", F_TIRA(3), "LEDS", {1: "+5V", 2: "GND", 3: "LED_DIN"}),
    ("R27", F_R, "330", {1: "IO39", 2: "LED_DIN"}),

    # --- Corte de chispa <- GPIO47, con piezas de tienda (ver HARDWARE-CORTE-CHISPA.md).
    #     El cable de paro del CDI (AC) se pone a tierra para cortar: un IRF840 (500 V) a tierra con
    #     un 1N4007 en serie que solo deja pasar la mitad positiva (la que carga el capacitor del CDI).
    #     La compuerta la prende un PC817 desde +12 V; sin señal, la 10k la deja en 0 V y la moto
    #     sigue con chispa normal (falla abierto). El zener de 15 V protege la compuerta.
    ("R28", F_R, "470", {1: "IO47", 2: "SSR_A"}),
    ("U15", F_DIP4, "PC817", {1: "SSR_A", 2: "GND", 3: "CORTE_G", 4: "+12V"}),
    ("R31", F_R, "10k", {1: "CORTE_G", 2: "GND"}),
    ("DZ1", F_D41, "1N4744 (15V)", {1: "CORTE_G", 2: "GND"}),      # 1=K
    ("Q9", F_TO220, "IRF840", {1: "CORTE_G", 2: "CORTE_D", 3: "GND"}),   # 1=G 2=D 3=S
    ("D8", F_D41, "1N4007", {1: "CORTE_D", 2: "CDI_PARO"}),        # 1=K 2=A
    ("J18", F_BORNE5(2), "CDI PARO", {1: "CDI_PARO", 2: "GND"}),

    # --- Salida aislada libre (colector abierto, hasta 50 mA) <- GPIO44 (RX0: el arranque no lo mueve)
    ("R30", F_R, "470", {1: "IO44", 2: "SAL_A"}),
    ("U16", F_DIP4, "PC817", {1: "SAL_A", 2: "GND", 3: "SAL_E", 4: "SAL_C"}),
    ("J19", F_BORNE35(2), "SALIDA AISLADA", {1: "SAL_C", 2: "SAL_E"}),

    # --- 3 relevadores: 1 = bloqueo anti-robo (GPIO40), 2 (GPIO38), 3 (GPIO45). GPIO45 debe estar
    #     en bajo al arrancar: el LED del PC817 a tierra lo asegura.
    *rele(71, 1, "IO40"),
    *rele(72, 2, "IO38"),
    *rele(73, 3, "IO45"),
    # --- Puntos de prueba para el multímetro
    ("TP1", "TestPoint:TestPoint_THTPad_D1.5mm_Drill0.7mm", "+12V", {1: "+12V"}),
    ("TP2", "TestPoint:TestPoint_THTPad_D1.5mm_Drill0.7mm", "+5V", {1: "+5V"}),
    ("TP3", "TestPoint:TestPoint_THTPad_D1.5mm_Drill0.7mm", "+3V3", {1: "+3V3"}),
    ("TP4", "TestPoint:TestPoint_THTPad_D1.5mm_Drill0.7mm", "GND", {1: "GND"}),
]

# Mapa para el firmware (lo que cambia respecto a v1.5):
#   GPIO38, GPIO45   relevadores 2 y 3 (activo en alto)
#   GPIO43           carga de las celdas (alto = 5 V al TP4056)
#   GPIO4            voltaje de las celdas (divisor 100k/100k, ya existía como Li-ion)
#   GPIO3, GPIO46    entradas aisladas 2 y 3 (activa = LOW, usa INPUT_PULLUP)
#   GPIO44           salida aislada libre
#   GPIO18 neutral   ahora por optoacoplador: en neutral el cable está a tierra, el LED se apaga y
#                    el pin lee HIGH  ->  NEUTRAL_ACTIVO_BAJO = false

# Rótulos de serigrafía de cada conector, en el orden de sus pines (pin 1 primero)
ETIQUETAS = {
    "J1": "12V MOTO: + -", "J2": "CELDAS 3.7V: + -", "J3": "A TP4056: + -", "J4": "BOBINA  GND",
    "J5": "LLAVE  GND", "J6": "ENT1 ENT2 ENT3 GND", "J7": "HALL 5V 3V3 G S", "J8": "QS  GND",
    "J9": "DS18B20 5V 3V3 G D", "J10": "GAS  GND", "J11": "LDR 3V3 S", "J12": "I2C 3V3 G SDA SCL",
    "J13": "LCD 5V G SDA SCL", "J14": "BOTONES ARR ABA OK G", "J15": "SD 5V G CS MO CK MI",
    "J16": "GPS 5V G TX RX", "J17": "LEDS 5V G D", "J18": "CDI PARO  CHASIS", "J19": "SALIDA C E",
    "J71": "RELE1 NA COM NC", "J72": "RELE2 NA COM NC", "J73": "RELE3 NA COM NC",
    "TP1": "12V", "TP2": "5V", "TP3": "3V3", "TP4": "GND",
}
