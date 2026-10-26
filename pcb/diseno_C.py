"""
FT150TS - placa C: una cara, tamaño libre, para MÓDULOS ya armados (por si consigues módulos más
fácil que piezas sueltas). Mismo ESP32 y mismas funciones que las placas A/B.

Módulos (cada uno con su tira rotulada y el contorno dibujado en la placa):
  - LM2596 (el azul, 43 x 21 mm) o mini MP1584: 12 V -> 5 V
  - TP4056 USB-C: carga las celdas 18650
  - Relevadores de 4 canales con optoacoplador (con jumper JD-VCC)
  - Optoacoplador PC817 de 4 canales (entradas de 12 V: llave, neutral, libres)
  - Convertidor de nivel de 4 canales (I2C del LCD a 5 V)
En la placa solo quedan piezas comunes: resistencias, diodos, fusible, capacitores, un BD140, un
BC547, el PC817 de la bobina y el PhotoMOS del corte de chispa (no existe en módulo).
"""
from diseno import (F_SOCKET22, F_R, F_R_1W, F_C, F_CP_35, F_CP_10, F_D41, F_D15, F_D35, F_PTC,
                    F_DIP4, F_DIP6, F_TO92, F_TO126, F_BORNE35, F_BORNE5, F_TIRA, J1, J3, entrada_sensor)

ANCHO = 140.0
ALTO = 105.0
RADIO_ESQUINA = 3.0
BARRENOS = [(3.5, 3.5), (136.5, 3.5), (3.5, 101.5), (136.5, 101.5)]
PISTA = 0.6
PISTA_POTENCIA = 1.2
SEPARACION = 0.4
SEPARACION_AV = 1.5
REDES_POTENCIA = ["+12V_IN", "+12V_F", "+12V", "+5V_MOTO", "+5V", "CELDA", "GND", "CARGA5"]
REDES_ALTO_VOLTAJE = ["CDI_PARO", "CDI_TIERRA", "BOBINA", "RPM_A"]

PIEZAS = [
    ("U1A", F_SOCKET22, "DevKitC-1 J1", {i + 1: n for i, n in enumerate(J1) if n}),
    ("U1B", F_SOCKET22, "DevKitC-1 J3", {i + 1: n for i, n in enumerate(J3) if n}),

    # --- Entrada de la moto: fusible, polaridad invertida, picos -> módulo LM2596
    ("J1", F_BORNE5(2), "BAT MOTO 12V", {1: "+12V_IN", 2: "GND"}),
    ("F1", F_PTC, "MF-R110 1.1A", {1: "+12V_IN", 2: "+12V_F"}),
    ("D1", F_D41, "1N5819", {1: "+12V", 2: "+12V_F"}),
    ("D2", F_D15, "P6KE22A", {1: "+12V", 2: "GND"}),
    ("C1", F_CP_35, "100uF 35V", {1: "+12V", 2: "GND"}),
    ("M1", "FT150TS:Modulo_LM2596_43x21", "LM2596", {1: "+12V", 2: "GND", 3: "+5V_MOTO", 4: "GND"}),  # IN+ IN- OUT+ OUT-
    ("C3", F_CP_10, "100uF 10V", {1: "+5V_MOTO", 2: "GND"}),

    # --- Celdas 18650 (3.0-4.2 V, con BMS) directo al ESP32 + medición
    ("J2", F_BORNE35(2), "BATERIA 3.7V", {1: "CELDA", 2: "GND"}),
    ("D3", F_D41, "1N5819", {1: "+5V", 2: "+5V_MOTO"}),
    ("D4", F_D41, "1N5819", {1: "+5V", 2: "CELDA"}),
    ("C5", F_CP_10, "100uF 10V", {1: "+5V", 2: "GND"}),
    ("R1", F_R, "100k", {1: "+12V_IN", 2: "IO1"}),
    ("R2", F_R, "22k", {1: "IO1", 2: "GND"}),
    ("C6", F_C, "100nF", {1: "IO1", 2: "GND"}),
    ("R3", F_R, "100k", {1: "CELDA", 2: "IO4"}),
    ("R4", F_R, "100k", {1: "IO4", 2: "GND"}),
    ("C7", F_C, "100nF", {1: "IO4", 2: "GND"}),

    # --- Carga: GPIO45 -> BC547 -> BD140 -> módulo TP4056 (IN+/IN-), y TP4056 B+/B- a las celdas
    #     (GPIO45 debe estar en bajo al arrancar: R8 lo asegura)
    ("Q5", F_TO126, "BD140", {1: "+5V_MOTO", 2: "CARGA5", 3: "CARGA_BP"}),
    ("R5", F_R, "1k", {1: "+5V_MOTO", 2: "CARGA_BP"}),
    ("R6", F_R, "150", {1: "CARGA_BP", 2: "CARGA_C"}),
    ("Q6", F_TO92, "BC547", {1: "CARGA_C", 2: "CARGA_B", 3: "GND"}),
    ("R7", F_R, "1k", {1: "IO45", 2: "CARGA_B"}),
    ("R8", F_R, "10k", {1: "CARGA_B", 2: "GND"}),
    ("M2", "FT150TS:Modulo_TP4056_26x17", "TP4056", {1: "CARGA5", 2: "GND", 3: "CELDA", 4: "GND"}),  # IN+ IN- B+ B- (OUT sin usar)

    # --- RPM: la bobina da cientos de volts; los módulos de optos solo aguantan ~30 V
    ("J4", F_BORNE5(2), "BOBINA", {1: "BOBINA", 2: "GND"}),
    ("D5", F_D41, "1N4007", {1: "RPM_A", 2: "BOBINA"}),
    ("R9", F_R_1W, "47k 1W", {1: "RPM_A", 2: "OPT9_A"}),
    ("D6", F_D35, "1N4148", {1: "OPT9_A", 2: "GND"}),
    ("C8", F_C, "4.7nF", {1: "OPT9_A", 2: "GND"}),
    ("U9", F_DIP4, "PC817", {1: "OPT9_A", 2: "GND", 3: "GND", 4: "IO16"}),

    # --- Entradas de 12 V: borneras -> módulo optoacoplador de 4 canales -> ESP32
    ("J5", F_BORNE35(2), "LLAVE", {1: "LLAVE", 2: "GND"}),
    ("J6", F_BORNE35(4), "ENTRADAS 3-16V", {1: "ENT1", 2: "ENT2", 3: "ENT3", 4: "GND"}),
    ("M3", F_TIRA(5), "OPTO 4CH ENTRADA", {1: "LLAVE", 2: "ENT1", 3: "ENT2", 4: "ENT3", 5: "GND"}),
    ("M4", F_TIRA(6), "OPTO 4CH SALIDA", {1: "+3V3", 2: "GND", 3: "IO21", 4: "IO18", 5: "IO3", 6: "IO46"}),

    # --- Relevadores: módulo de 4 canales. VCC a 3.3 V (lado del ESP32) y JD-VCC a 5 V (bobinas):
    #     así un módulo "activo en bajo" de 5 V no se queda pegado con 3.3 V.
    ("M5", F_TIRA(7), "RELES 4CH", {1: "+5V_MOTO", 2: "GND", 3: "+3V3", 4: "IO40", 5: "IO38",
                                    6: "IO44", 7: "IO43"}),   # JD-VCC GND VCC IN1 IN2 IN3 IN4

    # --- Sensores de 3.3 o 5 V (se leen solos)
    ("J7", F_TIRA(4), "HALL", {1: "+5V", 2: "+3V3", 3: "GND", 4: "S20"}),
    *entrada_sensor(20, "IO17"),
    ("J8", F_BORNE35(2), "QUICKSHIFT", {1: "S21", 2: "GND"}),
    *entrada_sensor(21, "IO48"),
    ("C9", F_C, "100nF", {1: "IO48", 2: "GND"}),
    ("J9", F_TIRA(4), "DS18B20", {1: "+5V", 2: "+3V3", 3: "GND", 4: "IO14"}),
    ("R22", F_R, "4.7k", {1: "+3V3", 2: "IO14"}),
    ("J10", F_BORNE35(2), "GASOLINA", {1: "IO2", 2: "GND"}),
    ("R23", F_R, "1k", {1: "+3V3", 2: "IO2"}),
    ("C10", F_C, "100nF", {1: "IO2", 2: "GND"}),
    ("J11", F_TIRA(2), "LDR", {1: "+3V3", 2: "IO5"}),
    ("R24", F_R, "10k", {1: "IO5", 2: "GND"}),

    # --- I2C: 3.3 V directo y LCD a 5 V por el módulo convertidor de nivel
    ("J12", F_TIRA(4), "I2C 3V3", {1: "+3V3", 2: "GND", 3: "IO8", 4: "IO9"}),
    ("M6", F_TIRA(4), "NIVEL LV", {1: "+3V3", 2: "GND", 3: "IO8", 4: "IO9"}),        # LV GND LV1 LV2
    ("M7", F_TIRA(4), "NIVEL HV", {1: "+5V", 2: "GND", 3: "SDA5", 4: "SCL5"}),       # HV GND HV1 HV2
    ("J13", F_TIRA(4), "LCD 5V", {1: "+5V", 2: "GND", 3: "SDA5", 4: "SCL5"}),

    ("J14", F_TIRA(4), "BOTONES", {1: "IO6", 2: "IO7", 3: "IO15", 4: "GND"}),
    ("J15", F_TIRA(6), "SD", {1: "+5V", 2: "GND", 3: "IO10", 4: "IO11", 5: "IO12", 6: "IO13"}),
    ("J16", F_TIRA(4), "GPS", {1: "+5V", 2: "GND", 3: "IO41", 4: "IO42"}),
    ("J17", F_TIRA(3), "LEDS", {1: "+5V", 2: "GND", 3: "LED_DIN"}),
    ("R27", F_R, "330", {1: "IO39", 2: "LED_DIN"}),

    # --- Corte de chispa: PhotoMOS (no hay módulo de 600 V)
    ("R28", F_R, "330", {1: "IO47", 2: "SSR_A"}),
    ("R29", F_R, "10k", {1: "IO47", 2: "GND"}),
    ("U15", F_DIP6, "PhotoMOS 600V", {1: "SSR_A", 2: "GND", 4: "CDI_PARO", 6: "CDI_TIERRA"}),
    ("J18", F_BORNE5(2), "CDI PARO", {1: "CDI_PARO", 2: "CDI_TIERRA"}),
]

# Zonas de los módulos que van montados sobre la placa (x0, y0, x1, y1): ahí no se pone ninguna pieza
# y se dibuja el contorno. Los de relevadores y optos van fuera, con cable dupont a su tira.
ZONAS_MODULO = {}   # ahora los módulos son huellas con su contorno (pads ovalados)

# Firmware placa C: relevadores ACTIVOS EN BAJO (módulo típico): IO40, IO38, IO44, IO43.
# Carga de celdas en GPIO45 (alto = carga). Entradas del módulo opto: activa = LOW.
