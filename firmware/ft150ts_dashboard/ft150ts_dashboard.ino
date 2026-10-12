/*
  ================================================================
   DASHBOARD FT150TS  -  firmware completo en un solo archivo
   ESP32-S3 DevKitC-1  +  LCD 20x4 I2C
   Versión 1.2 (con app Android e iPhone por Bluetooth)
  ================================================================
   Placa en Arduino IDE: "ESP32S3 Dev Module"  (core esp32 3.x)
   Partition Scheme:     "Huge APP (3MB No OTA/1MB SPIFFS)"
                         (WiFi + Bluetooth no caben en la partición normal)
   USB CDC On Boot:      Enabled (para ver el Serial por el USB nativo)

   Librerías (Library Manager):
     LiquidCrystal I2C   (Frank de Brabander)
     TinyGPSPlus         (Mikal Hart)
     RTClib              (Adafruit)  -> instala también Adafruit BusIO
     OneWire             (Paul Stoffregen)
     DallasTemperature   (Miles Burton)
     Adafruit NeoPixel   (Adafruit)

   Todo valor marcado con   // <-- X   es de ejemplo: cámbialo por
   el real de tu moto o de tus piezas cuando lo tengas.

   Controles:
     ARRIBA / ABAJO = cambiar de pantalla
     OK corto       = seleccionar dentro de la pantalla
     OK largo (1s)  = ejecutar / cambiar lo seleccionado
                      (en pantallas sin acción, regresa a la principal)
   Consola Serial (115200): escribe "ayuda".

   IPHONE (sin instalar nada en el iPhone):
     iPhone > Ajustes > Bluetooth > "FT150TS" > Vincular, y acepta
     "Compartir notificaciones del sistema". Desde ahí la moto recibe
     tus notificaciones (ANCS), la canción que suena y la controla (AMS)
     y toma la hora del iPhone (CTS). Puede estar conectado al mismo
     tiempo que la app de Android.
     Pantalla "IPHONE": OK = play/pausa, OK largo = siguiente canción.
     Llamada entrante: OK = contestar, OK largo = colgar/rechazar.

   MAPA DE CONEXIONES (ESP32-S3 DevKitC-1)
   GPIO1  <- batería 12V por divisor 100k/27k (+100nF a GND)
   GPIO2  <- sensor de gasolina (divisor con resistencia fija a 3.3V)
   GPIO4  <- 18650 por divisor 100k/100k
   GPIO5  <- LDR a 3.3V + 10k a GND
   GPIO6  <- botón ARRIBA (a GND)
   GPIO7  <- botón ABAJO  (a GND)
   GPIO15 <- botón OK     (a GND)
   GPIO8  <> SDA: LCD 20x4, RTC DS3231, MPU6050 (AD0 a 3.3V -> 0x69)
   GPIO9  <> SCL: mismos tres
   GPIO10 -> SD CS     GPIO11 -> SD MOSI
   GPIO12 -> SD SCK    GPIO13 <- SD MISO
   GPIO14 <> DS18B20 DATA (4.7k a 3.3V)
   GPIO16 <- RPM: salida del optoacoplador PC817 (bobina)
   GPIO17 <- sensor Hall A3144 de velocidad
   GPIO18 <- switch de neutral (por diodo 1N4148, cátodo al cable)
   GPIO21 <- ignición/llave: salida de otro PC817 (LOW = llave ON)
   GPIO39 -> tira WS2812B DIN (resistencia 330 ohm en serie)
   GPIO40 -> módulo relevador (corte de motor)
   GPIO41 <- TX del GPS NEO-6M
   GPIO42 -> RX del GPS NEO-6M
  ================================================================
*/

// ================================================================
// 0) INTERRUPTORES GENERALES
// ================================================================
#define MODO_SIMULADOR 1   // 1 = Velxio / Wokwi   0 = placa real en la moto
// En modo simulador: sin íconos personalizados (el simulador no los dibuja),
// sin WiFi, sin Bluetooth, sin dormir, y arranca directo en modo demo.

#if MODO_SIMULADOR
  #define USAR_WIFI       0
  #define USAR_BLE        0
  #define USAR_DORMIR     0
  #define USAR_ICONOS     0
  #define DEMO_AL_INICIAR 1
#else
  #define USAR_WIFI       1
  #define USAR_BLE        1
  #define USAR_DORMIR     1
  #define USAR_ICONOS     1
  #define DEMO_AL_INICIAR 0
#endif

#define USAR_IGNICION 1    // 1 = hay cable de ignición (llave) conectado al ESP32

#include <Wire.h>
#include <SPI.h>
#include <SD.h>
#include <Preferences.h>
#include <LiquidCrystal_I2C.h>
#include <TinyGPSPlus.h>
#include <RTClib.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <Adafruit_NeoPixel.h>
#include <esp_sleep.h>
#include <driver/rtc_io.h>
#if USAR_WIFI
  #include <WiFi.h>
  #include <WebServer.h>
  #include <HTTPClient.h>
#endif
#if USAR_BLE
  #include <BLEDevice.h>
  #include <BLEServer.h>
  #include <BLEUtils.h>
  #include <BLE2902.h>
  #include <BLEClient.h>
  #include <BLESecurity.h>
#endif

#define FW_VERSION "v1.4"

// ================================================================
// 1) TAMAÑOS Y TIPOS
//    (van hasta arriba para que el Arduino IDE los reconozca al
//     generar los prototipos automáticos de las funciones)
// ================================================================
const uint8_t NUM_MARCHAS = 5;
const uint8_t NUM_MANT    = 5;
const uint8_t NUM_RUTAS   = 8;
const uint8_t NUM_LEDS    = 8;
const uint8_t BINS_RPM    = 26;   // bloques de 500 rpm (0 - 13000)
const uint8_t NUM_EVT_RAM = 6;

enum Evento : uint8_t { EV_NINGUNO, EV_ARRIBA, EV_ABAJO, EV_OK, EV_OK_LARGO };

enum Vista : uint8_t {
  V_PRINCIPAL, V_IPHONE, V_VIAJE, V_COMPARAR, V_MANT, V_BATERIAS, V_DIAG, V_PISTA, V_AJUSTES, V_TOTAL
};

// Conexiones Bluetooth: la app de Android y/o el iPhone al mismo tiempo
const uint8_t MAX_CONEX = 3;
struct ConexionBle {
  bool     usada = false;
  uint16_t id = 0;          // conn_id de Bluedroid
  uint8_t  bda[6] = {0};    // dirección del teléfono
  uint8_t  tipoDir = 0;     // pública / aleatoria
  uint32_t desde = 0;       // millis() al conectarse
  bool     esApp = false;   // ya escribió en nuestros comandos (es la app Android)
  bool     esIphone = false;
  bool     revisada = false;
  bool     cifrada = false; // el enlace ya está cifrado (iPhone vinculado que se reconecta)
};

// Paquetes del iPhone que llegan por Bluetooth (se procesan fuera del hilo de BT)
struct PaqueteIos {
  uint8_t origen;     // 1 ANCS aviso, 2 ANCS datos, 3 AMS entidad, 4 AMS comandos, 10 orden local
  uint8_t len;
  uint8_t datos[200];
};

struct EstadoIphone {
  bool  conectado = false;     // vinculado y suscrito
  bool  ancs = false, ams = false;
  // música (AMS)
  char  titulo[64] = "", artista[56] = "", album[48] = "", app[20] = "";
  bool  sonando = false;
  float velocidad = 0, transcurrido = 0, duracion = 0, volumen = -1;
  uint32_t msInfo = 0;         // millis() en que llegó 'transcurrido'
  // llamada entrante (ANCS)
  bool  llamada = false;
  uint32_t llamadaUid = 0, llamadaMs = 0;
  char  llamadaDe[40] = "";
  bool  llamadaAcciones = false; // el iPhone permite contestar/colgar desde aquí
};

// Prototipos de lo nuevo (por si el IDE no los genera solo)
void aAscii(const char* src, char* dst, size_t n);
void cortarUtf8(char* dst, size_t n, const char* src);
bool iosMusica(uint8_t comando);
bool iosLlamada(uint8_t accion);
void iosOlvidar();
void filaTexto(uint8_t r, const char* txt);

enum Icono : uint8_t { ICO_BAT = 1, ICO_GAS, ICO_TEMP, ICO_ALERTA, ICO_BT, ICO_LLAVE, ICO_GPS };

enum TipoEvento : uint8_t {
  EVT_CAIDA, EVT_FRENADA, EVT_ACELERON, EVT_ARRANQUE, EVT_CORTE,
  EVT_SOBRE_RPM, EVT_BAT_BAJA, EVT_GAS_CRITICA, EVT_TEMP_ALTA, EVT_NO_CARGA, EVT_TOTAL
};

struct DatosMoto {
  float vMoto   = NAN;   // V batería de la moto
  float vLipo   = NAN;   // V batería 18650 del sistema
  int   gasPct  = -1;    // 0-100
  float gasMv   = NAN;   // lectura cruda del sensor (para calibrar)
  float vel     = NAN;   // km/h elegida (Hall o GPS)
  float velHall = NAN;
  float velGps  = NAN;
  int   rpm     = -1;
  int   marcha  = -1;    // -1 desconocida, 0 neutral, 1..5
  bool  neutral = false;
  float tempC   = NAN;
  float incl    = NAN;   // grados respecto a la moto parada derecha
  float aFwd    = 0;     // g hacia adelante (+ acelera, - frena)
  bool  gpsFix  = false;
  double lat = 0, lon = 0;
  int   sats    = 0;
  int   luz     = -1;    // 0-100
  bool  ignicion = false;
};

struct Boton {
  uint8_t  pin;
  bool     estable, ultima;
  uint32_t tCambio, tPresion;
  bool     largoEmitido;
};

struct Glifo { char c; uint8_t ancho; uint8_t f[4]; };

struct Mant {
  char     nombre[10];
  uint16_t intKm;
  uint16_t intDias;
  float    kmUltimo;
  uint32_t epochUltimo;
};

struct Ruta {
  float    latI, lonI, latF, lonF;
  uint16_t veces;
  float    minProm, litProm;
};

struct Resumen {
  float    km;
  float    litros;
  uint32_t segMov;
  float    velMax;
  uint16_t viajes;
};

struct Viaje {
  bool     activo;
  uint32_t epochInicio;
  float    km;
  uint32_t msMov, msParado;
  float    velMax;
  float    gasRef, gasMin, litrosAcum;
  bool     tieneInicioGps;
  double   latI, lonI;
};

struct EventoReg { uint32_t epoch; uint8_t tipo; float valor; };

// Todo lo que se guarda en la memoria interna (sobrevive apagones)
struct Persistente {
  uint32_t magic;
  uint8_t  perfil;          // 0 ECO, 1 SPORT
  uint8_t  fuenteVel;       // 0 AUTO, 1 HALL, 2 GPS
  bool     autoBrillo;
  bool     wifiOn;
  bool     corteActivo;     // corte de motor (anti robo) persistente
  float    gasMvVacio, gasMvLleno;
  float    batFactor;
  float    ratioMarcha[NUM_MARCHAS];
  float    imuRef[3];
  float    odometro;
  uint32_t arranques;
  float    kmPorLitro;
  float    shiftAprendido[NUM_MARCHAS];
  Mant     mant[NUM_MANT];
  Ruta     rutas[NUM_RUTAS];
  Resumen  hoy, ayer, prom;
  uint32_t diaHoy;
  float    vReposo[7];
  uint32_t diaReposo;
  uint8_t  idxReposo;
  float    descMotoBase, descLipoBase;
  float    vMotoDormir, vLipoDormir;
  uint32_t epochDormir;
  uint32_t diaBackup;
  float    pistaMejor;
};

// Para que la consola funcione igual desde Serial o desde la página web
class TextoPrint : public Print {
 public:
  String s;
  size_t write(uint8_t c) override { s += (char)c; return 1; }
};

// ================================================================
// 2) CONFIGURACIÓN
// ================================================================

// ---------- Pines ESP32-S3 DevKitC-1 ----------
const uint8_t PIN_ADC_BATERIA  = 1;    // divisor 100k/27k de la batería 12V
const uint8_t PIN_ADC_GASOLINA = 2;    // sensor de flotador (divisor)
const uint8_t PIN_ADC_LIPO     = 4;    // divisor 100k/100k de la 18650
const uint8_t PIN_ADC_LUZ      = 5;    // LDR + resistencia 10k
const uint8_t PIN_BTN_ARRIBA   = 6;
const uint8_t PIN_BTN_ABAJO    = 7;
const uint8_t PIN_BTN_OK       = 15;
const uint8_t PIN_I2C_SDA      = 8;    // LCD + RTC DS3231 + MPU6050
const uint8_t PIN_I2C_SCL      = 9;
const uint8_t PIN_SD_CS        = 10;
const uint8_t PIN_SD_MOSI      = 11;
const uint8_t PIN_SD_SCK       = 12;
const uint8_t PIN_SD_MISO      = 13;
const uint8_t PIN_DS18B20      = 14;   // temperatura motor (pull-up 4.7k a 3.3V)
const uint8_t PIN_RPM          = 16;   // salida del optoacoplador de la bobina
const uint8_t PIN_HALL         = 17;   // sensor Hall de velocidad
const uint8_t PIN_NEUTRAL      = 18;   // switch de neutral (con diodo)
const uint8_t PIN_IGNICION     = 21;   // llave ON (optoacoplador)
const uint8_t PIN_LEDS         = 39;   // tira WS2812B (shift light)
const uint8_t PIN_RELE         = 40;   // relevador de corte de motor
const uint8_t PIN_GPS_RX       = 41;   // aquí llega el TX del GPS
const uint8_t PIN_GPS_TX       = 42;   // va al RX del GPS
const int8_t  PIN_BACKLIGHT_PWM = -1;  // 47 si quitas el jumper del backpack y usas transistor
const int8_t  PIN_PERIF_EN      = -1;  // 3 si pones un MOSFET que apague GPS y LEDs al dormir

const bool RELE_ACTIVO_ALTO     = true;  // true = HIGH energiza el relevador
const bool IGNICION_ACTIVA_BAJO = true;  // con optoacoplador: LOW = llave ON
const bool NEUTRAL_ACTIVO_BAJO  = true;  // el switch manda a tierra en neutral

// ---------- Direcciones I2C ----------
const uint8_t LCD_DIR   = 0x27;        // <-- X si no prende prueba 0x3F
const uint8_t MPU_DIR   = 0x69;        // MPU6050 con AD0 a 3.3V (0x68 lo usa el DS3231)
const uint8_t LCD_COLS  = 20;
const uint8_t LCD_FILAS = 4;

// ---------- Datos de la moto ----------
const float   TANQUE_LITROS        = 14.0f;    // <-- X capacidad del tanque
const float   CIRCUNF_RUEDA_MM     = 1880.0f;  // <-- X mide la llanta con el imán
const uint8_t IMANES_RUEDA         = 1;        // imanes en la rueda
const float   PULSOS_POR_REV       = 1.0f;     // <-- X chispas por vuelta del CDI
const int     RPM_ROJO             = 9000;     // <-- X zona roja
const int     RPM_MAX_BARRA        = 10000;
const int     RPM_MOTOR_ENCENDIDO  = 600;
const int     RALENTI_MIN          = 1300;     // <-- X
const int     RALENTI_MAX          = 1700;     // <-- X
const float   TEMP_MAX_C           = 110.0f;   // <-- X depende de dónde pegues el sensor
const float   RATIO_MARCHA_DEF[NUM_MARCHAS] = {160, 100, 74, 60, 51}; // <-- X rpm por km/h, calibra con "cal marcha N"
const float   KMPL_DEF             = 35.0f;    // <-- X rendimiento inicial km/l
const int     ZONA_HORARIA         = -7;       // Nayarit
const float   PISTA_OBJETIVO_KMH   = 60.0f;    // 0 a 60 km/h
const float   VEL_BLOQUEO_MENU     = 5.0f;     // arriba de esto no se ejecutan acciones
const float   VEL_MAX_CORTE        = 3.0f;     // el corte solo se activa casi parado
const bool    CORTE_POR_CAIDA      = false;    // true = cortar motor si detecta caída

// ---------- Shift light ----------
const float SHIFT_MIN = 5000, SHIFT_MAX = 8500, SHIFT_DEF = 7000; // <-- X
const float PERFIL_FACTOR[2] = {0.75f, 1.0f};  // ECO cambia antes, SPORT al punto óptimo
const float PID_KP = 0.8f, PID_KI = 0.05f, PID_KD = 0.1f;

// ---------- Baterías ----------
const float BAT_R1 = 100000.0f, BAT_R2 = 27000.0f;
const float LIPO_R1 = 100000.0f, LIPO_R2 = 100000.0f;
const float LIPO_FACTOR = 1.0f;               // <-- X ajusta contra multímetro
const float BAT_BAJA_V = 12.0f;
const float BAT_REPOSO_BUENA = 12.6f, BAT_REPOSO_DEBIL = 12.2f;
const float CARGA_MIN_V = 13.2f, CARGA_MAX_V = 15.0f;
const float ARRANQUE_DEBIL_V = 9.6f;
const float LIPO_MIN_V = 3.3f, LIPO_MAX_V = 4.2f;

// ---------- Gasolina ----------
const float GAS_MV_VACIO_DEF = 500.0f;        // <-- X calibra con "cal gas vacio"
const float GAS_MV_LLENO_DEF = 2800.0f;       // <-- X calibra con "cal gas lleno"
const float GAS_MV_FALLA_BAJO = 80.0f, GAS_MV_FALLA_ALTO = 3200.0f;
const int   GAS_CRITICA_PCT = 10;
const float AUTONOMIA_AVISO_KM = 25.0f;

// ---------- IMU ----------
const uint8_t EJE_ADELANTE = 0;              // <-- X 0=X 1=Y 2=Z según cómo montes el MPU
const float   SIGNO_ADELANTE = 1.0f;         // <-- X -1 si sale al revés
const float   CAIDA_GRADOS = 60.0f;
const uint32_t CAIDA_MS = 2000;
const float   FRENADA_G = 0.5f, ACELERON_G = 0.4f;

// ---------- Tiempos ----------
const uint32_t BTN_ANTIREBOTE_MS   = 30;
const uint32_t BTN_LARGO_MS        = 1000;
const uint32_t SPLASH_PASO_MS      = 250;
const uint32_t SPLASH_FINAL_MS     = 1500;
const uint32_t REFRESCO_LCD_MS     = 250;
const uint32_t TICK_MS             = 100;
const uint32_t FIN_VIAJE_MS        = 120000;   // llave apagada 2 min = fin de viaje
const uint32_t TIEMPO_A_DORMIR_MS  = 300000;   // 5 min sin llave ni botones
const uint32_t DESPERTAR_CADA_S    = 3600;     // revisa baterías cada hora dormido
const uint32_t LOG_BAT_MS          = 600000;
const uint32_t NOTIF_MS            = 10000;
const uint32_t AVISO_MS            = 2000;

// ---------- Red ----------
const char* AP_SSID     = "FT150TS-Dash";
const char* AP_PASS     = "12345678";          // <-- X déjala así y cada tarjeta genera su propia clave al azar
                                               //     (verla: comando "wifi clave" por USB o Bluetooth vinculado),
                                               //     o pon aquí tu clave (8 a 15 caracteres)
const char* STA_SSID    = "X";                 // <-- X WiFi de tu casa (para respaldo)
const char* STA_PASS    = "X";                 // <-- X
const char* BACKUP_URL  = "X";                 // <-- X ej. http://tuservidor/subir
#define BLE_NOMBRE   "FT150TS"
#define BLE_SERVICIO "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define BLE_NOTIF    "6e400002-b5a3-f393-e0a9-e50e24dcca9e"   // app -> ESP32: texto de notificación
#define BLE_TELEM    "6e400003-b5a3-f393-e0a9-e50e24dcca9e"   // ESP32 -> app: datos en JSON
#define BLE_CMD      "6e400004-b5a3-f393-e0a9-e50e24dcca9e"   // app -> ESP32: comando de consola
#define BLE_RESP     "6e400005-b5a3-f393-e0a9-e50e24dcca9e"   // ESP32 -> app: respuesta del comando

const uint32_t MAGIC_NVS = 0xF150A001;
const float RADIO_RUTA_M = 300.0f;

// ================================================================
// 3) OBJETOS Y VARIABLES GLOBALES
// ================================================================
LiquidCrystal_I2C lcd(LCD_DIR, LCD_COLS, LCD_FILAS);
TinyGPSPlus gps;
HardwareSerial GPSSerial(1);
RTC_DS3231 rtc;
OneWire oneWire(PIN_DS18B20);
DallasTemperature dsTemp(&oneWire);
Adafruit_NeoPixel leds(NUM_LEDS, PIN_LEDS, NEO_GRB + NEO_KHZ800);
Preferences prefs;
#if USAR_WIFI
WebServer server(80);
#endif

Persistente P;
DatosMoto   datos;
Viaje       g_viaje;

bool g_modoDemo = false;
bool g_rtcOk = false, g_imuOk = false, g_sdOk = false, g_tempOk = false, g_lcdOk = false;
bool g_nvsSucio = false;
uint32_t g_ultimoGuardado = 0;

// ADC filtrado
float g_mvBat = NAN, g_mvGas = NAN, g_mvLipo = NAN, g_mvLuz = NAN;
uint32_t g_ultimoAdc = 0;

// RPM y velocidad (interrupciones)
volatile uint32_t g_rpmUltUs = 0, g_rpmPerUs = 0;
volatile uint32_t g_hallUltUs = 0, g_hallPerUs = 0;
volatile bool     g_hallVisto = false;
float g_rpmFilt = 0;

// GPS
uint32_t g_gpsChars = 0, g_gpsUltChar = 0;
bool g_gpsRtcSync = false;
// GPS del teléfono (lo manda la app por Bluetooth si no hay módulo GPS con señal)
double g_gpsExtLat = 0, g_gpsExtLon = 0;
float  g_gpsExtVel = NAN;
uint32_t g_gpsExtHasta = 0;
bool g_gpsExterno = false;

// IMU
float g_acc[3] = {0, 0, 1}, g_accLento[3] = {0, 0, 1};
uint32_t g_ultImu = 0, g_inclDesde = 0, g_frenDesde = 0, g_aceDesde = 0, g_rectoDesde = 0;
bool g_caida = false, g_corteCaida = false;

// Temperatura
bool g_tempPendiente = false;
uint32_t g_tempT0 = 0, g_tempUlt = 0;
float g_tempHist[7]; uint8_t g_tempIdx = 0; uint8_t g_tempN = 0; uint32_t g_tempHistUlt = 0;

// Botones
Boton g_btn[3] = {
  {PIN_BTN_ARRIBA, HIGH, HIGH, 0, 0, false},
  {PIN_BTN_ABAJO,  HIGH, HIGH, 0, 0, false},
  {PIN_BTN_OK,     HIGH, HIGH, 0, 0, false},
};

// Pantalla
char  g_cache[LCD_FILAS][LCD_COLS + 1];
Vista g_vista = V_PRINCIPAL;
uint8_t g_selMant = 0, g_selAjuste = 0, g_pagDiag = 0;
char  g_aviso[24] = "";
uint32_t g_avisoHasta = 0;
uint32_t g_ultLcd = 0;

// Estado motor y viaje
bool g_motorEnc = false;
uint32_t g_rpmAltoDesde = 0, g_rpmBajoDesde = 0;
uint32_t g_ultIgnOn = 0, g_ultActividad = 0, g_inicioViajeMs = 0;
float g_vMinArranque = 99;
uint32_t g_vMinArranqueT = 0;
bool g_arranqueDebil = false;
float g_velPrev = 0, g_accelKmhs = 0;
uint32_t g_ultTick = 0;

// Ralentí
float g_ralenti = 0; uint32_t g_ralentiDesde = 0; bool g_ralentiMedido = false;

// Alternador
uint32_t g_cargaMalaDesde = 0; uint8_t g_estadoCarga = 0; // 0 ? 1 ok 2 no carga 3 sobrecarga

// Shift light / aprendizaje
float g_accBin[NUM_MARCHAS][BINS_RPM];
uint8_t g_accCnt[NUM_MARCHAS][BINS_RPM];
float g_shiftActual[NUM_MARCHAS], g_pidInt[NUM_MARCHAS], g_pidErrPrev[NUM_MARCHAS];
uint32_t g_ultLeds = 0;

// Pista
bool g_pistaActiva = false, g_pistaCorriendo = false;
uint32_t g_pistaT0 = 0;
float g_pistaUltimo = 0, g_pistaVmax = 0, g_pistaGmax = 0; int g_pistaRpmMax = 0;

// Eventos
EventoReg g_evt[NUM_EVT_RAM]; uint8_t g_evtIdx = 0;
uint32_t g_evtUltimo[EVT_TOTAL];

// Notificaciones
char g_notif[21] = "";
uint32_t g_notifHasta = 0;
volatile bool g_bleConectado = false;
volatile bool g_notifNueva = false;
char g_notifBuf[21] = "";

// iPhone
EstadoIphone g_iphone;

// Baterías
uint32_t g_ultLogBat = 0;
bool g_alertaDescMoto = false, g_alertaDescLipo = false;

// Red
uint32_t g_ultBackupIntento = 0;
bool g_wifiIniciado = false;

// Simulaciones de demo
uint32_t g_simCaidaHasta = 0, g_simFrenadaHasta = 0;

// Íconos 5x8
uint8_t BMP_BAT[8]    = {0b01110, 0b11111, 0b10001, 0b10001, 0b11111, 0b11111, 0b11111, 0b11111};
uint8_t BMP_GAS[8]    = {0b00100, 0b00100, 0b01110, 0b01110, 0b11111, 0b11111, 0b01110, 0b00000};
uint8_t BMP_TEMP[8]   = {0b00100, 0b01010, 0b01010, 0b01010, 0b01110, 0b11111, 0b11111, 0b01110};
uint8_t BMP_ALERTA[8] = {0b00100, 0b00100, 0b01010, 0b01010, 0b10001, 0b10101, 0b11111, 0b00000};
uint8_t BMP_BT[8]     = {0b00100, 0b10110, 0b01101, 0b00110, 0b01101, 0b10110, 0b00100, 0b00000};
uint8_t BMP_LLAVE[8]  = {0b10001, 0b10001, 0b11111, 0b01110, 0b00100, 0b00100, 0b00100, 0b00100};
uint8_t BMP_GPS[8]    = {0b01110, 0b10001, 0b10101, 0b10001, 0b01010, 0b00100, 0b00000, 0b00000};

// Fuente grande del splash: solo bloques llenos (0xFF), así se ve
// igual en el simulador y en el LCD real. 4 renglones de alto.
Glifo FUENTE[] = {
  {'F', 3, {0b111, 0b100, 0b110, 0b100}},
  {'T', 3, {0b111, 0b010, 0b010, 0b010}},
  {'1', 2, {0b11,  0b01,  0b01,  0b01}},
  {'5', 3, {0b111, 0b110, 0b001, 0b111}},
  {'0', 3, {0b111, 0b101, 0b101, 0b111}},
};
const char* TEXTO_SPLASH = "FT150";

const char* NOMBRE_EVT[EVT_TOTAL] = {
  "caida", "frenada", "aceleron", "arranque", "corte",
  "sobre_rpm", "bat_baja", "gas_critica", "temp_alta", "no_carga"
};
const char* NOMBRE_VISTA[V_TOTAL] = {
  "principal", "iphone", "viaje", "comparar", "mantenimiento", "baterias", "diagnostico", "pista", "ajustes"
};

// ================================================================
// 4) UTILIDADES
// ================================================================
char ico(Icono i) {
#if USAR_ICONOS
  return (char)i;
#else
  switch (i) {
    case ICO_BAT:    return 'B';
    case ICO_GAS:    return 'G';
    case ICO_TEMP:   return 'T';
    case ICO_ALERTA: return '!';
    case ICO_BT:     return 'N';
    case ICO_LLAVE:  return 'M';
    case ICO_GPS:    return 'S';
  }
  return '?';
#endif
}

float ema(float previo, float nuevo, float alfa) {
  return isnan(previo) ? nuevo : previo + alfa * (nuevo - previo);
}

float distanciaM(double la1, double lo1, double la2, double lo2) {
  const double R = 6371000.0;
  double dLa = radians(la2 - la1), dLo = radians(lo2 - lo1);
  double a = sin(dLa / 2) * sin(dLa / 2) + cos(radians(la1)) * cos(radians(la2)) * sin(dLo / 2) * sin(dLo / 2);
  return (float)(R * 2 * atan2(sqrt(a), sqrt(1 - a)));
}

DateTime ahora() {
  if (g_rtcOk) return rtc.now();
  return DateTime(2026, 1, 1, 0, 0, 0) + TimeSpan((int32_t)(millis() / 1000));
}

uint32_t ymd(const DateTime& d) {
  return d.year() * 10000UL + d.month() * 100UL + d.day();
}

uint32_t ahoraEpoch() { return ahora().unixtime(); }

void aviso(const char* txt) {
  strncpy(g_aviso, txt, sizeof(g_aviso) - 1);
  g_aviso[sizeof(g_aviso) - 1] = '\0';
  g_avisoHasta = millis() + AVISO_MS;
}

// ================================================================
// 5) MEMORIA PERSISTENTE (NVS)
// ================================================================
void nvsDefaults() {
  memset(&P, 0, sizeof(P));
  P.magic = MAGIC_NVS;
  P.perfil = 0;
  P.fuenteVel = 0;
  P.autoBrillo = true;
  P.wifiOn = true;
  P.corteActivo = false;
  P.gasMvVacio = GAS_MV_VACIO_DEF;
  P.gasMvLleno = GAS_MV_LLENO_DEF;
  P.batFactor = 1.0f;
  for (uint8_t i = 0; i < NUM_MARCHAS; i++) {
    P.ratioMarcha[i] = RATIO_MARCHA_DEF[i];
    P.shiftAprendido[i] = SHIFT_DEF;
  }
  P.imuRef[0] = 0; P.imuRef[1] = 0; P.imuRef[2] = 1;
  P.kmPorLitro = KMPL_DEF;
  // Mantenimiento: nombre, cada cuántos km, cada cuántos días   <-- X
  const char* nombres[NUM_MANT] = {"Cadena", "Aceite", "Filtro", "Bujia", "Frenos"};
  const uint16_t km[NUM_MANT]    = {500, 2000, 4000, 6000, 4000};
  const uint16_t dias[NUM_MANT]  = {30, 90, 180, 365, 180};
  for (uint8_t i = 0; i < NUM_MANT; i++) {
    strncpy(P.mant[i].nombre, nombres[i], sizeof(P.mant[i].nombre) - 1);
    P.mant[i].intKm = km[i];
    P.mant[i].intDias = dias[i];
    P.mant[i].kmUltimo = 0;
    P.mant[i].epochUltimo = 0;
  }
}

void nvsCargar() {
  prefs.begin("ft150", false);
  size_t n = prefs.getBytesLength("p");
  bool ok = false;
  if (n == sizeof(Persistente)) {
    prefs.getBytes("p", &P, sizeof(P));
    ok = (P.magic == MAGIC_NVS);
  }
  if (!ok) {
    nvsDefaults();
    prefs.putBytes("p", &P, sizeof(P));
  }
}

void nvsGuardar() {
  if (g_modoDemo) return;   // el demo nunca ensucia tus datos reales
  prefs.putBytes("p", &P, sizeof(P));
  g_nvsSucio = false;
  g_ultimoGuardado = millis();
}

// ================================================================
// 6) TARJETA SD (bitácoras CSV)
// ================================================================
void sdBegin() {
  SPI.begin(PIN_SD_SCK, PIN_SD_MISO, PIN_SD_MOSI, PIN_SD_CS);
  g_sdOk = SD.begin(PIN_SD_CS);
}

void sdAgregar(const char* archivo, const char* encabezado, const char* linea) {
  if (!g_sdOk || g_modoDemo) return;
  bool nuevo = !SD.exists(archivo);
  File f = SD.open(archivo, FILE_APPEND);
  if (!f) { g_sdOk = false; return; }
  if (nuevo) f.println(encabezado);
  f.println(linea);
  f.close();
}

void fechaTexto(char* b, size_t n, const DateTime& d) {
  snprintf(b, n, "%04d-%02d-%02d %02d:%02d:%02d", d.year(), d.month(), d.day(), d.hour(), d.minute(), d.second());
}

void registrarEvento(TipoEvento t, float valor) {
  uint32_t ms = millis();
  // Eventos instantáneos: máx 1 cada 10 s. Estados que duran (gasolina
  // crítica, motor caliente...): máx 1 cada 10 min para no llenar la SD.
  bool estado = (t == EVT_GAS_CRITICA || t == EVT_TEMP_ALTA || t == EVT_BAT_BAJA || t == EVT_SOBRE_RPM || t == EVT_NO_CARGA);
  uint32_t espera = estado ? 600000UL : 10000UL;
  if (g_evtUltimo[t] != 0 && ms - g_evtUltimo[t] < espera) return;
  g_evtUltimo[t] = ms;
  g_evt[g_evtIdx].epoch = ahoraEpoch();
  g_evt[g_evtIdx].tipo = t;
  g_evt[g_evtIdx].valor = valor;
  g_evtIdx = (g_evtIdx + 1) % NUM_EVT_RAM;

  char f[24], l[120];
  fechaTexto(f, sizeof f, ahora());
  snprintf(l, sizeof l, "%s,%s,%.2f,%.6f,%.6f", f, NOMBRE_EVT[t], valor, datos.lat, datos.lon);
  sdAgregar("/eventos.csv", "fecha,tipo,valor,lat,lon", l);
  Serial.printf("[evento] %s %.2f\n", NOMBRE_EVT[t], valor);
}

void logBaterias() {
  char f[24], l[96];
  fechaTexto(f, sizeof f, ahora());
  uint8_t estado = g_motorEnc ? 2 : (datos.ignicion ? 1 : 0);
  snprintf(l, sizeof l, "%s,%.2f,%.2f,%d,%.1f", f, datos.vMoto, datos.vLipo, estado, datos.tempC);
  sdAgregar("/bateria.csv", "fecha,v_moto,v_lipo,estado(0off 1llave 2motor),temp", l);
}

// ================================================================
// 7) SENSORES ANALÓGICOS
// ================================================================
float leerMv(uint8_t pin, uint8_t n) {
  uint32_t s = 0;
  for (uint8_t i = 0; i < n; i++) s += analogReadMilliVolts(pin);
  return s / (float)n;
}

void adcBegin() {
  analogReadResolution(12);
  analogSetPinAttenuation(PIN_ADC_BATERIA, ADC_11db);
  analogSetPinAttenuation(PIN_ADC_GASOLINA, ADC_11db);
  analogSetPinAttenuation(PIN_ADC_LIPO, ADC_11db);
  analogSetPinAttenuation(PIN_ADC_LUZ, ADC_11db);
}

void adcActualizar() {
  uint32_t ms = millis();
  if (ms - g_ultimoAdc < 20) return;
  g_ultimoAdc = ms;
  g_mvBat  = ema(g_mvBat,  leerMv(PIN_ADC_BATERIA, 8), 0.05f);
  g_mvGas  = ema(g_mvGas,  leerMv(PIN_ADC_GASOLINA, 8), 0.01f);
  g_mvLipo = ema(g_mvLipo, leerMv(PIN_ADC_LIPO, 4), 0.05f);
  g_mvLuz  = ema(g_mvLuz,  leerMv(PIN_ADC_LUZ, 4), 0.05f);
}

float voltajeMoto() {
  if (isnan(g_mvBat)) return NAN;
  return g_mvBat / 1000.0f * (BAT_R1 + BAT_R2) / BAT_R2 * P.batFactor;
}

float voltajeLipo() {
  if (isnan(g_mvLipo)) return NAN;
  return g_mvLipo / 1000.0f * (LIPO_R1 + LIPO_R2) / LIPO_R2 * LIPO_FACTOR;
}

int porcentajeGas() {
  if (isnan(g_mvGas) || g_mvGas < GAS_MV_FALLA_BAJO || g_mvGas > GAS_MV_FALLA_ALTO) return -1;
  float rango = P.gasMvLleno - P.gasMvVacio;
  if (fabs(rango) < 50) return -1;
  float pct = (g_mvGas - P.gasMvVacio) * 100.0f / rango;
  return (int)constrain(pct + 0.5f, 0.0f, 100.0f);
}

int porcentajeLuz() {
  if (isnan(g_mvLuz)) return -1;
  return (int)constrain(g_mvLuz * 100.0f / 3300.0f, 0.0f, 100.0f);
}

int porcentajeLipo(float v) {
  if (isnan(v)) return -1;
  return (int)constrain((v - LIPO_MIN_V) * 100.0f / (LIPO_MAX_V - LIPO_MIN_V), 0.0f, 100.0f);
}

// ================================================================
// 8) RPM Y VELOCIDAD (interrupciones)
// ================================================================
void IRAM_ATTR isrRpm() {
  uint32_t t = micros();
  uint32_t d = t - g_rpmUltUs;
  if (d < 4000) return;          // filtro de rebote / ruido (> 15000 rpm)
  g_rpmPerUs = d;
  g_rpmUltUs = t;
}

void IRAM_ATTR isrHall() {
  uint32_t t = micros();
  uint32_t d = t - g_hallUltUs;
  if (d < 15000) return;         // filtro (> ~450 km/h)
  g_hallPerUs = d;
  g_hallUltUs = t;
  g_hallVisto = true;
}

int rpmInstantanea() {
  noInterrupts();
  uint32_t per = g_rpmPerUs, ult = g_rpmUltUs;
  interrupts();
  if (per == 0 || micros() - ult > 300000) return 0;
  return (int)(60000000.0f / (per * PULSOS_POR_REV));
}

float velocidadHall() {
  if (!g_hallVisto) return NAN;  // sin sensor instalado
  noInterrupts();
  uint32_t per = g_hallPerUs, ult = g_hallUltUs;
  interrupts();
  if (per == 0 || micros() - ult > 2500000) return 0;
  float metros = CIRCUNF_RUEDA_MM / 1000.0f / IMANES_RUEDA;
  return metros / (per / 1000000.0f) * 3.6f;
}

// ================================================================
// 9) GPS
// ================================================================
void gpsActualizar() {
  while (GPSSerial.available()) {
    gps.encode(GPSSerial.read());
    g_gpsUltChar = millis();
  }
  if (g_modoDemo) return;
  datos.gpsFix = gps.location.isValid() && gps.location.age() < 3000;
  if (datos.gpsFix) {
    datos.lat = gps.location.lat();
    datos.lon = gps.location.lng();
  }
  datos.sats = gps.satellites.isValid() ? gps.satellites.value() : 0;
  datos.velGps = (gps.speed.isValid() && gps.speed.age() < 3000) ? gps.speed.kmph() : NAN;

  // Sincroniza el reloj con la hora del GPS una vez por encendido
  if (!g_gpsRtcSync && g_rtcOk && datos.gpsFix && gps.date.isValid() && gps.time.isValid() && gps.date.year() > 2024) {
    DateTime utc(gps.date.year(), gps.date.month(), gps.date.day(), gps.time.hour(), gps.time.minute(), gps.time.second());
    rtc.adjust(utc + TimeSpan(ZONA_HORARIA * 3600L));
    g_gpsRtcSync = true;
  }

  // Sin señal del módulo: usa la ubicación que manda el teléfono
  g_gpsExterno = false;
  if (!datos.gpsFix && millis() < g_gpsExtHasta) {
    datos.gpsFix = true;
    datos.lat = g_gpsExtLat;
    datos.lon = g_gpsExtLon;
    datos.velGps = g_gpsExtVel;
    g_gpsExterno = true;
  }
}

bool gpsFuncionando() { return g_gpsUltChar != 0 && millis() - g_gpsUltChar < 5000; }

bool gpsVelBuena() {
  if (!datos.gpsFix || isnan(datos.velGps)) return false;
  if (g_modoDemo || g_gpsExterno) return true;
  return gps.hdop.isValid() && gps.hdop.hdop() < 2.5;
}

float elegirVelocidad() {
  bool g = gpsVelBuena();
  float h = datos.velHall;
  switch (P.fuenteVel) {
    case 1: return isnan(h) ? NAN : h;
    case 2: return g ? datos.velGps : h;
    default:
      if (g && datos.velGps > 5) return datos.velGps;
      if (!isnan(h)) return h;
      if (g) return datos.velGps;
      return NAN;
  }
}

// ================================================================
// 10) ACELERÓMETRO MPU6050 (sin librería)
// ================================================================
bool imuEscribir(uint8_t reg, uint8_t val) {
  Wire.beginTransmission(MPU_DIR);
  Wire.write(reg);
  Wire.write(val);
  return Wire.endTransmission() == 0;
}

bool imuBegin() {
  Wire.beginTransmission(MPU_DIR);
  Wire.write(0x75);                       // WHO_AM_I
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom((uint8_t)MPU_DIR, (uint8_t)1) != 1) return false;
  Wire.read();
  imuEscribir(0x6B, 0x00);                // despertar
  imuEscribir(0x1C, 0x08);                // ±4 g
  imuEscribir(0x1A, 0x03);                // filtro pasa bajas ~44 Hz
  return true;
}

void imuActualizar() {
  if (!g_imuOk || g_modoDemo) return;
  uint32_t ms = millis();
  if (ms - g_ultImu < 20) return;
  g_ultImu = ms;
  Wire.beginTransmission(MPU_DIR);
  Wire.write(0x3B);
  if (Wire.endTransmission(false) != 0) return;
  if (Wire.requestFrom((uint8_t)MPU_DIR, (uint8_t)6) != 6) return;
  for (uint8_t i = 0; i < 3; i++) {
    int16_t raw = (Wire.read() << 8) | Wire.read();
    float g = raw / 8192.0f;
    g_acc[i] = g_acc[i] + 0.3f * (g - g_acc[i]);
    g_accLento[i] = g_accLento[i] + 0.05f * (g - g_accLento[i]);
  }
  // Inclinación = ángulo entre la gravedad actual y la de referencia (moto derecha)
  float dot = 0, n1 = 0, n2 = 0;
  for (uint8_t i = 0; i < 3; i++) {
    dot += g_accLento[i] * P.imuRef[i];
    n1 += g_accLento[i] * g_accLento[i];
    n2 += P.imuRef[i] * P.imuRef[i];
  }
  if (n1 > 0.01f && n2 > 0.01f) {
    float c = constrain(dot / sqrt(n1 * n2), -1.0f, 1.0f);
    datos.incl = degrees(acos(c));
  }
  datos.aFwd = (g_acc[EJE_ADELANTE] - P.imuRef[EJE_ADELANTE]) * SIGNO_ADELANTE;
}

void imuCalibrar() {
  for (uint8_t i = 0; i < 3; i++) P.imuRef[i] = g_accLento[i];
  nvsGuardar();
}

void revisarCaidasYFrenadas() {
  uint32_t ms = millis();
  float v = isnan(datos.vel) ? 0 : datos.vel;

  // Caída: muy inclinada, casi sin velocidad, durante CAIDA_MS
  if (!isnan(datos.incl) && datos.incl > CAIDA_GRADOS && v < 5) {
    if (g_inclDesde == 0) g_inclDesde = ms;
    if (!g_caida && ms - g_inclDesde > CAIDA_MS) {
      g_caida = true;
      registrarEvento(EVT_CAIDA, datos.incl);
      if (CORTE_POR_CAIDA) { g_corteCaida = true; registrarEvento(EVT_CORTE, 1); }
    }
    g_rectoDesde = 0;
  } else {
    g_inclDesde = 0;
    if (g_caida && !isnan(datos.incl) && datos.incl < 30) {
      if (g_rectoDesde == 0) g_rectoDesde = ms;
      if (ms - g_rectoDesde > 3000) g_caida = false;   // ya se levantó
    }
  }

  // Frenada brusca / acelerón
  if (datos.aFwd < -FRENADA_G && v > 15) {
    if (g_frenDesde == 0) g_frenDesde = ms;
    if (ms - g_frenDesde > 250) registrarEvento(EVT_FRENADA, datos.aFwd);
  } else g_frenDesde = 0;

  if (datos.aFwd > ACELERON_G) {
    if (g_aceDesde == 0) g_aceDesde = ms;
    if (ms - g_aceDesde > 250) registrarEvento(EVT_ACELERON, datos.aFwd);
  } else g_aceDesde = 0;
}

// ================================================================
// 11) TEMPERATURA DEL MOTOR (DS18B20, sin bloquear)
// ================================================================
void tempBegin() {
  dsTemp.begin();
  g_tempOk = dsTemp.getDeviceCount() > 0;
  dsTemp.setWaitForConversion(false);
}

void tempActualizar() {
  if (!g_tempOk || g_modoDemo) return;
  uint32_t ms = millis();
  if (!g_tempPendiente && ms - g_tempUlt > 1000) {
    dsTemp.requestTemperatures();
    g_tempPendiente = true;
    g_tempT0 = ms;
  } else if (g_tempPendiente && ms - g_tempT0 > 800) {
    float c = dsTemp.getTempCByIndex(0);
    if (c > -50 && c < 200 && c != 85.0f) datos.tempC = c;
    g_tempPendiente = false;
    g_tempUlt = ms;
  }
}

void tempHistorial() {
  if (isnan(datos.tempC) || millis() - g_tempHistUlt < 10000) return;
  g_tempHistUlt = millis();
  g_tempHist[g_tempIdx] = datos.tempC;
  g_tempIdx = (g_tempIdx + 1) % 7;
  if (g_tempN < 7) g_tempN++;
}

// °C por minuto según los últimos 60 s
float tempPendiente() {
  if (g_tempN < 7) return 0;
  float viejo = g_tempHist[g_tempIdx];                      // el más antiguo
  float nuevo = g_tempHist[(g_tempIdx + 6) % 7];
  return nuevo - viejo;
}

// ================================================================
// 12) MARCHA E IGNICIÓN
// ================================================================
bool leerNeutral() {
  int v = digitalRead(PIN_NEUTRAL);
  return NEUTRAL_ACTIVO_BAJO ? v == LOW : v == HIGH;
}

bool leerIgnicion() {
#if USAR_IGNICION
  int v = digitalRead(PIN_IGNICION);
  return IGNICION_ACTIVA_BAJO ? v == LOW : v == HIGH;
#else
  return g_motorEnc;
#endif
}

int calcularMarcha(float v, int rpm, bool neutral) {
  if (neutral) return 0;
  if (isnan(v) || v < 3 || rpm < RPM_MOTOR_ENCENDIDO) return -1;
  float r = rpm / v;
  int mejor = -1;
  float mejorErr = 1e9;
  for (uint8_t i = 0; i < NUM_MARCHAS; i++) {
    float e = fabs(r - P.ratioMarcha[i]) / P.ratioMarcha[i];
    if (e < mejorErr) { mejorErr = e; mejor = i; }
  }
  return mejorErr < 0.15f ? mejor + 1 : -1;
}

// ================================================================
// 13) BOTONES
// ================================================================
void botonesBegin() {
  for (uint8_t i = 0; i < 3; i++) pinMode(g_btn[i].pin, INPUT_PULLUP);
}

Evento botonesLeer() {
  uint32_t ms = millis();
  for (uint8_t i = 0; i < 3; i++) {
    Boton& b = g_btn[i];
    bool lectura = digitalRead(b.pin);
    if (lectura != b.ultima) { b.ultima = lectura; b.tCambio = ms; }
    if (ms - b.tCambio >= BTN_ANTIREBOTE_MS && lectura != b.estable) {
      b.estable = lectura;
      if (b.estable == LOW) {
        b.tPresion = ms;
        b.largoEmitido = false;
        if (i == 0) return EV_ARRIBA;
        if (i == 1) return EV_ABAJO;
      } else if (i == 2 && !b.largoEmitido) {
        return EV_OK;
      }
    }
    if (i == 2 && b.estable == LOW && !b.largoEmitido && ms - b.tPresion >= BTN_LARGO_MS) {
      b.largoEmitido = true;
      return EV_OK_LARGO;
    }
  }
  return EV_NINGUNO;
}

// ================================================================
// 14) PANTALLA: base, íconos y splash
// ================================================================
void cargarIconos() {
#if USAR_ICONOS
  lcd.createChar(ICO_BAT, BMP_BAT);
  lcd.createChar(ICO_GAS, BMP_GAS);
  lcd.createChar(ICO_TEMP, BMP_TEMP);
  lcd.createChar(ICO_ALERTA, BMP_ALERTA);
  lcd.createChar(ICO_BT, BMP_BT);
  lcd.createChar(ICO_LLAVE, BMP_LLAVE);
  lcd.createChar(ICO_GPS, BMP_GPS);
#endif
}

void limpiarCache() { memset(g_cache, 0, sizeof(g_cache)); }

void escribirFila(uint8_t r, char* buf) {
  size_t n = strlen(buf);
  for (; n < LCD_COLS; n++) buf[n] = ' ';
  buf[LCD_COLS] = '\0';
  if (memcmp(g_cache[r], buf, LCD_COLS) == 0) return;
  memcpy(g_cache[r], buf, LCD_COLS);
  lcd.setCursor(0, r);
  for (uint8_t i = 0; i < LCD_COLS; i++) lcd.write((uint8_t)buf[i]);
}

void pantallaBegin() {
  Wire.beginTransmission(LCD_DIR);
  g_lcdOk = (Wire.endTransmission() == 0);
  lcd.init();
  lcd.backlight();
  lcd.clear();
  if (PIN_BACKLIGHT_PWM >= 0) ledcAttach(PIN_BACKLIGHT_PWM, 5000, 8);
}

const Glifo* buscarGlifo(char c) {
  for (uint8_t k = 0; k < sizeof(FUENTE) / sizeof(FUENTE[0]); k++)
    if (FUENTE[k].c == c) return &FUENTE[k];
  return nullptr;
}

void pantallaSplash() {
  lcd.clear();
  uint8_t x = 0;
  for (uint8_t i = 0; TEXTO_SPLASH[i]; i++) {
    const Glifo* g = buscarGlifo(TEXTO_SPLASH[i]);
    if (!g) continue;
    for (uint8_t fila = 0; fila < 4; fila++) {
      for (uint8_t col = 0; col < g->ancho; col++) {
        bool on = (g->f[fila] >> (g->ancho - 1 - col)) & 1;
        lcd.setCursor(x + col, fila);
        lcd.write(on ? (uint8_t)0xFF : (uint8_t)' ');
      }
    }
    x += g->ancho + 1;
    delay(SPLASH_PASO_MS);
  }
  lcd.setCursor(LCD_COLS - 2, 3);
  lcd.print("TS");
  delay(SPLASH_FINAL_MS);
  lcd.clear();
  cargarIconos();
  limpiarCache();
}

void pantallaIrA(Vista v) {
  g_vista = v;
  lcd.clear();
  limpiarCache();
  g_ultLcd = 0;
}

void brilloActualizar() {
  datos.luz = porcentajeLuz();
  if (PIN_BACKLIGHT_PWM < 0) return;
  int duty = 255;
  if (P.autoBrillo && datos.luz >= 0) duty = map(datos.luz, 0, 100, 40, 255);
  ledcWrite(PIN_BACKLIGHT_PWM, duty);
}

// ================================================================
// 15) MANTENIMIENTO
// ================================================================
float mantKmDesde(uint8_t i) { return P.odometro - P.mant[i].kmUltimo; }

int mantDiasDesde(uint8_t i) {
  if (P.mant[i].epochUltimo == 0 || !g_rtcOk) return -1;
  uint32_t a = ahoraEpoch();
  if (a < P.mant[i].epochUltimo) return 0;
  return (a - P.mant[i].epochUltimo) / 86400UL;
}

bool mantVencido(uint8_t i) {
  if (mantKmDesde(i) >= P.mant[i].intKm) return true;
  int d = mantDiasDesde(i);
  return d >= 0 && d >= P.mant[i].intDias;
}

int mantPrimeroVencido() {
  for (uint8_t i = 0; i < NUM_MANT; i++) if (mantVencido(i)) return i;
  return -1;
}

void mantRegistrar(uint8_t i) {
  P.mant[i].kmUltimo = P.odometro;
  P.mant[i].epochUltimo = g_rtcOk ? ahoraEpoch() : 0;
  nvsGuardar();
}

// ================================================================
// 16) VIAJES, RESUMEN DIARIO Y RUTAS
// ================================================================
void revisarCambioDia() {
  uint32_t d = ymd(ahora());
  if (!g_rtcOk) return;
  if (P.diaHoy == 0) { P.diaHoy = d; g_nvsSucio = true; return; }
  if (d == P.diaHoy) return;
  if (P.hoy.km > 0.5f) {
    if (P.prom.km < 0.01f) P.prom = P.hoy;
    else {
      P.prom.km = P.prom.km * 0.8f + P.hoy.km * 0.2f;
      P.prom.litros = P.prom.litros * 0.8f + P.hoy.litros * 0.2f;
      P.prom.segMov = (uint32_t)(P.prom.segMov * 0.8f + P.hoy.segMov * 0.2f);
      P.prom.velMax = P.prom.velMax * 0.8f + P.hoy.velMax * 0.2f;
    }
  }
  P.ayer = P.hoy;
  memset(&P.hoy, 0, sizeof(P.hoy));
  P.diaHoy = d;
  nvsGuardar();
}

void viajeIniciar() {
  memset(&g_viaje, 0, sizeof(g_viaje));
  g_viaje.activo = true;
  g_viaje.epochInicio = ahoraEpoch();
  g_viaje.gasRef = datos.gasPct >= 0 ? datos.gasPct : -1;
  g_viaje.gasMin = g_viaje.gasRef;
  g_inicioViajeMs = millis();
}

float viajeLitros() {
  float l = g_viaje.litrosAcum;
  if (g_viaje.gasRef >= 0 && g_viaje.gasMin >= 0)
    l += (g_viaje.gasRef - g_viaje.gasMin) / 100.0f * TANQUE_LITROS;
  return max(l, 0.0f);
}

int rutaBuscarDesde(double la, double lo) {
  int mejor = -1;
  for (uint8_t i = 0; i < NUM_RUTAS; i++) {
    Ruta& r = P.rutas[i];
    if (r.veces < 2) continue;
    if (distanciaM(la, lo, r.latI, r.lonI) > RADIO_RUTA_M) continue;
    if (mejor < 0 || r.veces > P.rutas[mejor].veces) mejor = i;
  }
  return mejor;
}

void rutaRegistrar(double laI, double loI, double laF, double loF, float minutos, float litros) {
  for (uint8_t i = 0; i < NUM_RUTAS; i++) {
    Ruta& r = P.rutas[i];
    if (r.veces == 0) continue;
    if (distanciaM(laI, loI, r.latI, r.lonI) < RADIO_RUTA_M && distanciaM(laF, loF, r.latF, r.lonF) < RADIO_RUTA_M) {
      r.minProm = (r.minProm * r.veces + minutos) / (r.veces + 1);
      r.litProm = (r.litProm * r.veces + litros) / (r.veces + 1);
      if (r.veces < 60000) r.veces++;
      return;
    }
  }
  int lugar = 0;  // vacío, o el menos usado
  for (uint8_t i = 0; i < NUM_RUTAS; i++) {
    if (P.rutas[i].veces == 0) { lugar = i; break; }
    if (P.rutas[i].veces < P.rutas[lugar].veces) lugar = i;
  }
  Ruta& r = P.rutas[lugar];
  r.latI = laI; r.lonI = loI; r.latF = laF; r.lonF = loF;
  r.veces = 1; r.minProm = minutos; r.litProm = litros;
}

void viajeTerminar() {
  if (!g_viaje.activo) return;
  g_viaje.activo = false;
  float litros = viajeLitros();
  float minMov = g_viaje.msMov / 60000.0f;
  float kmpl = (litros > 0.1f) ? g_viaje.km / litros : 0;

  if (g_modoDemo) return;
  if (g_viaje.km < 0.2f) return;   // movimientos de estacionamiento no cuentan

  revisarCambioDia();
  P.hoy.km += g_viaje.km;
  P.hoy.litros += litros;
  P.hoy.segMov += g_viaje.msMov / 1000;
  P.hoy.velMax = max(P.hoy.velMax, g_viaje.velMax);
  P.hoy.viajes++;
  if (g_viaje.km > 5 && litros > 0.1f && kmpl > 5 && kmpl < 120)
    P.kmPorLitro = P.kmPorLitro * 0.8f + kmpl * 0.2f;

  if (g_viaje.tieneInicioGps && datos.gpsFix)
    rutaRegistrar(g_viaje.latI, g_viaje.lonI, datos.lat, datos.lon,
                  (g_viaje.msMov + g_viaje.msParado) / 60000.0f, litros);

  char fi[24], ff[24], l[200];
  fechaTexto(fi, sizeof fi, DateTime(g_viaje.epochInicio));
  fechaTexto(ff, sizeof ff, ahora());
  snprintf(l, sizeof l, "%s,%s,%.2f,%.1f,%.1f,%.0f,%.2f,%.1f,%.6f,%.6f,%.6f,%.6f",
           fi, ff, g_viaje.km, minMov, g_viaje.msParado / 60000.0f, g_viaje.velMax, litros, kmpl,
           g_viaje.latI, g_viaje.lonI, datos.lat, datos.lon);
  sdAgregar("/viajes.csv", "inicio,fin,km,min_mov,min_parado,vmax,litros,km_l,lat_i,lon_i,lat_f,lon_f", l);

  for (uint8_t i = 0; i < NUM_MARCHAS; i++) P.shiftAprendido[i] = g_shiftActual[i] / PERFIL_FACTOR[P.perfil];
  nvsGuardar();
}

void viajeActualizar(uint32_t dt) {
  float v = isnan(datos.vel) ? 0 : datos.vel;
  float dkm = v * dt / 3600000.0f;
  if (v > 1) { P.odometro += dkm; g_nvsSucio = true; }

  bool ign = datos.ignicion;
  if (ign) g_ultIgnOn = millis();

  if (!g_viaje.activo && ign && v > 3) viajeIniciar();
  if (!g_viaje.activo) return;

  if (!g_pistaActiva) g_viaje.km += dkm;
  if (v > 3) g_viaje.msMov += dt; else g_viaje.msParado += dt;
  g_viaje.velMax = max(g_viaje.velMax, v);

  // Gasolina: detecta recargas para no contarlas como consumo negativo
  if (datos.gasPct >= 0) {
    if (g_viaje.gasRef < 0) { g_viaje.gasRef = datos.gasPct; g_viaje.gasMin = datos.gasPct; }
    if (datos.gasPct < g_viaje.gasMin) g_viaje.gasMin = datos.gasPct;
    if (datos.gasPct > g_viaje.gasMin + 15) {
      g_viaje.litrosAcum += (g_viaje.gasRef - g_viaje.gasMin) / 100.0f * TANQUE_LITROS;
      g_viaje.gasRef = datos.gasPct;
      g_viaje.gasMin = datos.gasPct;
    }
  }

  if (!g_viaje.tieneInicioGps && datos.gpsFix && millis() - g_inicioViajeMs < 180000) {
    g_viaje.tieneInicioGps = true;
    g_viaje.latI = datos.lat;
    g_viaje.lonI = datos.lon;
  }

  if (!ign && millis() - g_ultIgnOn > FIN_VIAJE_MS) viajeTerminar();
}

float autonomiaKm() {
  if (datos.gasPct < 0) return NAN;
  return datos.gasPct / 100.0f * TANQUE_LITROS * P.kmPorLitro;
}

// ================================================================
// 17) MOTOR: arranques, ralentí, alternador
// ================================================================
void motorActualizar() {
  uint32_t ms = millis();
  int rpm = datos.rpm < 0 ? 0 : datos.rpm;

  // Caída de voltaje mientras se da marcha (antes de que prenda)
  if (!g_motorEnc && datos.ignicion && !isnan(datos.vMoto)) {
    if (ms - g_vMinArranqueT > 5000) g_vMinArranque = 99;
    if (datos.vMoto < g_vMinArranque) { g_vMinArranque = datos.vMoto; g_vMinArranqueT = ms; }
  }

  if (rpm > RPM_MOTOR_ENCENDIDO) {
    g_rpmBajoDesde = 0;
    if (g_rpmAltoDesde == 0) g_rpmAltoDesde = ms;
    if (!g_motorEnc && ms - g_rpmAltoDesde > 1000) {
      g_motorEnc = true;
      if (!g_modoDemo) { P.arranques++; g_nvsSucio = true; }
      float dip = (g_vMinArranque < 90) ? g_vMinArranque : NAN;
      registrarEvento(EVT_ARRANQUE, isnan(dip) ? 0 : dip);
      g_arranqueDebil = !isnan(dip) && dip < ARRANQUE_DEBIL_V;
      g_vMinArranque = 99;
    }
  } else {
    g_rpmAltoDesde = 0;
    if (g_rpmBajoDesde == 0) g_rpmBajoDesde = ms;
    if (g_motorEnc && ms - g_rpmBajoDesde > 2000) { g_motorEnc = false; g_ralentiDesde = 0; }
  }

  if (rpm > RPM_ROJO) registrarEvento(EVT_SOBRE_RPM, rpm);

  // Ralentí: motor caliente (o 3 min encendido), parada y en neutral
  float v = isnan(datos.vel) ? 0 : datos.vel;
  bool caliente = !isnan(datos.tempC) ? datos.tempC > 60 : (g_rpmAltoDesde && ms - g_rpmAltoDesde > 180000);
  if (g_motorEnc && v < 1 && datos.marcha == 0 && caliente) {
    if (g_ralentiDesde == 0) { g_ralentiDesde = ms; g_ralenti = rpm; }
    g_ralenti = g_ralenti * 0.98f + rpm * 0.02f;
    if (ms - g_ralentiDesde > 10000) g_ralentiMedido = true;
  } else g_ralentiDesde = 0;

  // Alternador: con el motor arriba de 2500 rpm debe cargar
  if (g_motorEnc && rpm > 2500 && !isnan(datos.vMoto)) {
    uint8_t estado = (datos.vMoto < CARGA_MIN_V) ? 2 : (datos.vMoto > CARGA_MAX_V) ? 3 : 1;
    if (estado == 1) { g_cargaMalaDesde = 0; g_estadoCarga = 1; }
    else {
      if (g_cargaMalaDesde == 0) g_cargaMalaDesde = ms;
      if (ms - g_cargaMalaDesde > 20000) {
        if (g_estadoCarga != estado) registrarEvento(EVT_NO_CARGA, datos.vMoto);
        g_estadoCarga = estado;
      }
    }
  } else if (!g_motorEnc) g_estadoCarga = 0;
}

bool ralentiFuera() {
  return g_ralentiMedido && (g_ralenti < RALENTI_MIN || g_ralenti > RALENTI_MAX);
}

// ================================================================
// 18) BATERÍAS: salud y predicción
// ================================================================
void registrarReposoDiario(float v) {
  uint32_t d = ymd(ahora());
  if (!g_rtcOk || isnan(v) || P.diaReposo == d) return;
  P.vReposo[P.idxReposo] = v;
  P.idxReposo = (P.idxReposo + 1) % 7;
  P.diaReposo = d;
  g_nvsSucio = true;
}

uint8_t reposoCuantos() {
  uint8_t n = 0;
  for (uint8_t i = 0; i < 7; i++) if (P.vReposo[i] > 5) n++;
  return n;
}

// Pendiente en V/día con regresión lineal sobre los últimos 7 días
float reposoTendencia() {
  if (reposoCuantos() < 3) return 0;
  float sx = 0, sy = 0, sxx = 0, sxy = 0; uint8_t n = 0;
  for (uint8_t k = 0; k < 7; k++) {
    uint8_t i = (P.idxReposo + k) % 7;   // del más viejo al más nuevo
    if (P.vReposo[i] < 5) continue;
    float x = k, y = P.vReposo[i];
    sx += x; sy += y; sxx += x * x; sxy += x * y; n++;
  }
  float den = n * sxx - sx * sx;
  return fabs(den) < 1e-6 ? 0 : (n * sxy - sx * sy) / den;
}

float reposoUltimo() {
  uint8_t i = (P.idxReposo + 6) % 7;
  return P.vReposo[i];
}

// Días estimados hasta que la batería quede débil (<12.2V en reposo)
int diasHastaBateriaDebil() {
  float m = reposoTendencia();
  float u = reposoUltimo();
  if (m > -0.005f || u < 5) return -1;
  float d = (u - BAT_REPOSO_DEBIL) / -m;
  return d < 0 ? 0 : (int)d;
}

const char* estadoBateriaMoto() {
  float v = datos.vMoto;
  if (isnan(v)) return "SIN DATO";
  if (g_motorEnc) {
    if (g_estadoCarga == 2) return "NO CARGA";
    if (g_estadoCarga == 3) return "SOBRECARGA";
    return "CARGANDO";
  }
  if (v >= BAT_REPOSO_BUENA) return "BUENA";
  if (v >= BAT_REPOSO_DEBIL) return "MEDIA";
  return "DEBIL";
}

// Al despertar: ¿cuánto bajó el voltaje por hora mientras estuvo dormida?
void revisarAutodescarga() {
  if (P.epochDormir == 0 || !g_rtcOk) return;
  uint32_t a = ahoraEpoch();
  if (a <= P.epochDormir) return;
  float horas = (a - P.epochDormir) / 3600.0f;
  if (horas < 6) return;
  float vm = voltajeMoto(), vl = voltajeLipo();
  if (!isnan(vm) && P.vMotoDormir > 5) {
    float tasa = max((P.vMotoDormir - vm) / horas, 0.0f);
    if (P.descMotoBase <= 0) P.descMotoBase = max(tasa, 0.002f);
    else {
      g_alertaDescMoto = tasa > 2.5f * P.descMotoBase && tasa > 0.01f;
      P.descMotoBase = P.descMotoBase * 0.9f + tasa * 0.1f;
    }
  }
  if (!isnan(vl) && P.vLipoDormir > 2) {
    float tasa = max((P.vLipoDormir - vl) / horas, 0.0f);
    if (P.descLipoBase <= 0) P.descLipoBase = max(tasa, 0.001f);
    else {
      g_alertaDescLipo = tasa > 2.5f * P.descLipoBase && tasa > 0.005f;
      P.descLipoBase = P.descLipoBase * 0.9f + tasa * 0.1f;
    }
  }
  P.epochDormir = 0;
  g_nvsSucio = true;
}

// ================================================================
// 19) SHIFT LIGHT CON APRENDIZAJE Y PID
// ================================================================
// Por cada marcha aprende en qué rpm la moto acelera más. El punto de
// cambio óptimo es donde la aceleración cae al 85 % del pico. Un PID
// lleva suavemente el punto de cambio actual hacia ese objetivo, para
// que no brinque de golpe con cada lectura.
void shiftInit() {
  for (uint8_t m = 0; m < NUM_MARCHAS; m++) {
    g_shiftActual[m] = constrain(P.shiftAprendido[m] * PERFIL_FACTOR[P.perfil], SHIFT_MIN, SHIFT_MAX);
    g_pidInt[m] = 0;
    g_pidErrPrev[m] = 0;
    for (uint8_t b = 0; b < BINS_RPM; b++) { g_accBin[m][b] = 0; g_accCnt[m][b] = 0; }
  }
}

float shiftObjetivo(uint8_t m) {
  int pico = -1;
  for (uint8_t b = 0; b < BINS_RPM; b++)
    if (g_accCnt[m][b] >= 3 && (pico < 0 || g_accBin[m][b] > g_accBin[m][pico])) pico = b;
  if (pico < 0) return P.shiftAprendido[m];
  float limite = g_accBin[m][pico] * 0.85f;
  for (uint8_t b = pico + 1; b < BINS_RPM; b++)
    if (g_accCnt[m][b] >= 3 && g_accBin[m][b] < limite) return b * 500.0f;
  int ultimo = pico;
  for (uint8_t b = pico; b < BINS_RPM; b++) if (g_accCnt[m][b] >= 3) ultimo = b;
  return (ultimo + 1) * 500.0f;
}

void shiftAprender(float dtS) {
  float v = isnan(datos.vel) ? 0 : datos.vel;
  float a = (v - g_velPrev) / dtS;
  g_velPrev = v;
  g_accelKmhs = g_accelKmhs * 0.7f + a * 0.3f;

  int m = datos.marcha;
  if (m >= 1 && m <= NUM_MARCHAS && datos.rpm > 2500 && g_accelKmhs > 0.8f) {
    uint8_t b = constrain(datos.rpm / 500, 0, BINS_RPM - 1);
    uint8_t i = m - 1;
    g_accBin[i][b] = (g_accCnt[i][b] == 0) ? g_accelKmhs : g_accBin[i][b] * 0.8f + g_accelKmhs * 0.2f;
    if (g_accCnt[i][b] < 250) g_accCnt[i][b]++;
  }

  for (uint8_t i = 0; i < NUM_MARCHAS; i++) {
    float obj = constrain(shiftObjetivo(i) * PERFIL_FACTOR[P.perfil], SHIFT_MIN, SHIFT_MAX);
    float err = obj - g_shiftActual[i];
    g_pidInt[i] = constrain(g_pidInt[i] + err * dtS, -5000.0f, 5000.0f);
    float der = (err - g_pidErrPrev[i]) / dtS;
    g_pidErrPrev[i] = err;
    float salida = PID_KP * err + PID_KI * g_pidInt[i] + PID_KD * der;
    g_shiftActual[i] = constrain(g_shiftActual[i] + salida * dtS, SHIFT_MIN, SHIFT_MAX);
  }
}

float puntoCambioActual() {
  int m = datos.marcha;
  if (m >= 1 && m <= NUM_MARCHAS) return g_shiftActual[m - 1];
  return SHIFT_DEF * PERFIL_FACTOR[P.perfil];
}

void ledsActualizar() {
  uint32_t ms = millis();
  if (ms - g_ultLeds < 30) return;
  g_ultLeds = ms;
  leds.clear();
  int rpm = datos.rpm;
  if (rpm > 0 && (datos.ignicion || g_modoDemo)) {
    uint8_t brillo = 80;
    if (P.autoBrillo && datos.luz >= 0) brillo = map(datos.luz, 0, 100, 10, 120);
    leds.setBrightness(brillo);
    float sp = puntoCambioActual();
    float inicio = sp * 0.6f;
    if (rpm >= sp) {
      if ((ms / 62) % 2) for (uint8_t i = 0; i < NUM_LEDS; i++) leds.setPixelColor(i, leds.Color(255, 0, 0));
    } else if (rpm > inicio) {
      int n = (int)((rpm - inicio) / (sp - inicio) * NUM_LEDS + 0.5f);
      for (int i = 0; i < n && i < NUM_LEDS; i++) {
        uint32_t c;
        if (i < NUM_LEDS * 0.5f)        c = leds.Color(0, 255, 0);       // verde
        else if (i < NUM_LEDS * 0.875f) c = leds.Color(255, 255, 255);   // blanco
        else                            c = leds.Color(255, 0, 0);       // rojo
        leds.setPixelColor(i, c);
      }
    }
  }
  leds.show();
}

// ================================================================
// 20) MODO PISTA
// ================================================================
void pistaActualizar() {
  if (!g_pistaActiva) return;
  float v = isnan(datos.vel) ? 0 : datos.vel;
  g_pistaVmax = max(g_pistaVmax, v);
  g_pistaRpmMax = max(g_pistaRpmMax, datos.rpm);
  g_pistaGmax = max(g_pistaGmax, fabs(datos.aFwd));
  if (!g_pistaCorriendo && v < 1) g_pistaT0 = 0;
  if (!g_pistaCorriendo && g_pistaT0 == 0 && v < 1) g_pistaT0 = 1;  // armado
  if (!g_pistaCorriendo && g_pistaT0 == 1 && v >= 1) { g_pistaCorriendo = true; g_pistaT0 = millis(); }
  if (g_pistaCorriendo && v >= PISTA_OBJETIVO_KMH) {
    g_pistaCorriendo = false;
    g_pistaUltimo = (millis() - g_pistaT0) / 1000.0f;
    g_pistaT0 = 2;   // terminado, espera a volver a 0
    if (!g_modoDemo && (P.pistaMejor <= 0 || g_pistaUltimo < P.pistaMejor)) { P.pistaMejor = g_pistaUltimo; nvsGuardar(); }
    char f[24], l[96];
    fechaTexto(f, sizeof f, ahora());
    snprintf(l, sizeof l, "%s,%.2f,%.0f,%d,%.2f", f, g_pistaUltimo, g_pistaVmax, g_pistaRpmMax, g_pistaGmax);
    sdAgregar("/pista.csv", "fecha,t_0_60,vmax,rpm_max,g_max", l);
  }
  if (g_pistaCorriendo && millis() - g_pistaT0 > 30000) { g_pistaCorriendo = false; g_pistaT0 = 2; }  // abortado
}

void pistaReset() {
  g_pistaUltimo = 0; g_pistaVmax = 0; g_pistaGmax = 0; g_pistaRpmMax = 0;
  g_pistaCorriendo = false; g_pistaT0 = 0;
}

// ================================================================
// 21) CORTE DE MOTOR (relevador)
// ================================================================
void releAplicar() {
  bool cortar = P.corteActivo || g_corteCaida;
  digitalWrite(PIN_RELE, (cortar == RELE_ACTIVO_ALTO) ? HIGH : LOW);
}

bool corteCambiar(bool activar, Print& out) {
  float v = isnan(datos.vel) ? 0 : datos.vel;
  if (activar && v > VEL_MAX_CORTE) { out.println(F("No se puede cortar en movimiento.")); return false; }
  P.corteActivo = activar;
  if (!activar) g_corteCaida = false;
  releAplicar();
  nvsGuardar();
  registrarEvento(EVT_CORTE, activar ? 1 : 0);
  out.println(activar ? F("Motor BLOQUEADO.") : F("Motor desbloqueado."));
  return true;
}

// ================================================================
// 22) ALERTAS (fila 4 de la pantalla principal)
// ================================================================
// Devuelve true si hay una alerta. El texto va sin ícono (para mandarlo
// también al teléfono) y el ícono que le toca se regresa en 'icono'.
bool construirAlerta(char* txt, size_t n, Icono& icono) {
  uint32_t ms = millis();
  float v = isnan(datos.vel) ? 0 : datos.vel;
  icono = ICO_ALERTA;

  if (P.corteActivo || g_corteCaida) { snprintf(txt, n, "MOTOR BLOQUEADO"); return true; }
  if (g_caida) { snprintf(txt, n, "CAIDA DETECTADA"); return true; }
  if (g_iphone.llamada) {
    char de[24];
    aAscii(g_iphone.llamadaDe, de, sizeof de);
    icono = ICO_BT;
    snprintf(txt, n, "Tel %s", de[0] ? de : "entrante");
    return true;
  }
  if (ms < g_notifHasta) { icono = ICO_BT; snprintf(txt, n, "%s", g_notif); return true; }
  if (datos.gasPct >= 0 && datos.gasPct <= GAS_CRITICA_PCT) {
    registrarEvento(EVT_GAS_CRITICA, datos.gasPct);
    snprintf(txt, n, "GASOLINA CRITICA"); return true;
  }
  if (!isnan(datos.tempC) && datos.tempC > TEMP_MAX_C) {
    registrarEvento(EVT_TEMP_ALTA, datos.tempC);
    snprintf(txt, n, "MOTOR CALIENTE"); return true;
  }
  if (g_estadoCarga == 2) { snprintf(txt, n, "NO CARGA ALTERNAD"); return true; }
  if (g_estadoCarga == 3) { snprintf(txt, n, "SOBRECARGA REGUL"); return true; }
  if (!isnan(datos.vMoto) && datos.vMoto < BAT_BAJA_V && !g_motorEnc && datos.ignicion) {
    registrarEvento(EVT_BAT_BAJA, datos.vMoto);
    snprintf(txt, n, "BATERIA BAJA"); return true;
  }
  // Alertas predictivas
  if (!isnan(datos.tempC) && datos.tempC + tempPendiente() * 2 > TEMP_MAX_C && tempPendiente() > 1) {
    snprintf(txt, n, "TEMP SUBIENDO"); return true;
  }
  float aut = autonomiaKm();
  if (!isnan(aut) && aut < AUTONOMIA_AVISO_KM && datos.gasPct > GAS_CRITICA_PCT) {
    icono = ICO_GAS; snprintf(txt, n, "Gas p/ ~%dkm", (int)aut); return true;
  }
  if (g_arranqueDebil) { snprintf(txt, n, "Bat debil arranque"); return true; }
  if (g_alertaDescMoto) { snprintf(txt, n, "Bat moto se descarga"); return true; }
  if (g_alertaDescLipo) { snprintf(txt, n, "Li-ion retiene poco"); return true; }
  int dias = diasHastaBateriaDebil();
  if (dias >= 0 && dias < 30) { snprintf(txt, n, "Bat moto ~%dd vida", dias); return true; }
  int mv = mantPrimeroVencido();
  if (mv >= 0 && v < 5) { icono = ICO_LLAVE; snprintf(txt, n, "Toca: %s", P.mant[mv].nombre); return true; }
  if (ralentiFuera() && v < 1) { snprintf(txt, n, "Ralenti %d rpm", (int)g_ralenti); return true; }
  return false;
}

void construirFila3(char* b, size_t n) {
  char t[32];
  Icono ic;
  if (construirAlerta(t, sizeof t, ic)) {
    if (ic == ICO_BT) snprintf(b, n, "%c%s", ico(ic), t);
    else snprintf(b, n, "%c %s", ico(ic), t);
    return;
  }

  // Ruta frecuente al inicio del viaje
  if (g_viaje.activo && g_viaje.tieneInicioGps && millis() - g_inicioViajeMs < 90000) {
    int r = rutaBuscarDesde(g_viaje.latI, g_viaje.lonI);
    if (r >= 0) { snprintf(b, n, "Ruta ~%dmin %.1fL", (int)P.rutas[r].minProm, P.rutas[r].litProm); return; }
  }

  // Normal: autonomía + estado GPS/BT + hora
  float aut = autonomiaKm();
  char a[8];
  if (!isnan(aut)) snprintf(a, sizeof a, "%3dkm", (int)aut); else strcpy(a, "---km");
  DateTime t2 = ahora();
  snprintf(b, n, "~%s %c%c    %02d:%02d", a,
           datos.gpsFix ? ico(ICO_GPS) : ' ',
           g_bleConectado ? ico(ICO_BT) : ' ',
           t2.hour(), t2.minute());
}

// ================================================================
// 23) VISTAS DE LA PANTALLA
// ================================================================
void vistaPrincipal() {
  char b[48];
  char vel[12];
  if (!isnan(datos.vel)) snprintf(vel, sizeof vel, "%3dkm/h", (int)(datos.vel + 0.5f));
  else strcpy(vel, "---km/h");
  char m = datos.marcha < 0 ? '-' : datos.marcha == 0 ? 'N' : (char)('0' + datos.marcha);
  snprintf(b, sizeof b, "%s %s MARCHA %c", vel, g_modoDemo ? "DEM" : (P.perfil ? "SPT" : "ECO"), m);
  escribirFila(0, b);

  char rp[12];
  if (datos.rpm >= 0) snprintf(rp, sizeof rp, "%5d", constrain(datos.rpm, 0, 99999)); else strcpy(rp, "-----");
  int lleno = datos.rpm <= 0 ? 0 : constrain(datos.rpm * 9 / RPM_MAX_BARRA, 0, 9);
  char barra[10];
  for (int i = 0; i < 9; i++) barra[i] = i < lleno ? (char)0xFF : '.';
  barra[9] = '\0';
  snprintf(b, sizeof b, "RPM%s [%s]", rp, barra);
  escribirFila(1, b);

  char v[16], g[16], t[16];
  if (!isnan(datos.vMoto)) snprintf(v, sizeof v, "%4.1fV", datos.vMoto); else strcpy(v, "--.-V");
  if (datos.gasPct >= 0) snprintf(g, sizeof g, "%3d%%", datos.gasPct); else strcpy(g, " --%");
  if (!isnan(datos.tempC)) snprintf(t, sizeof t, "%3dC", (int)datos.tempC); else strcpy(t, " --C");
  snprintf(b, sizeof b, "%c%s %c%s %c%s", ico(ICO_BAT), v, ico(ICO_GAS), g, ico(ICO_TEMP), t);
  escribirFila(2, b);

  construirFila3(b, sizeof b);
  escribirFila(3, b);
}

void vistaViaje() {
  char b[48];
  DateTime t = ahora();
  bool act = g_viaje.activo;
  snprintf(b, sizeof b, "VIAJE %s %02d/%02d", act ? "EN CURSO" : "HOY     ", t.day(), t.month());
  escribirFila(0, b);

  float km = act ? g_viaje.km : P.hoy.km;
  uint32_t seg = act ? g_viaje.msMov / 1000 : P.hoy.segMov;
  snprintf(b, sizeof b, "Dist%6.1fkm  %02lu:%02luh", km, (unsigned long)(seg / 3600), (unsigned long)((seg / 60) % 60));
  escribirFila(1, b);

  float lit = act ? viajeLitros() : P.hoy.litros;
  float vmax = act ? g_viaje.velMax : P.hoy.velMax;
  char c[8];
  if (lit > 0.05f) snprintf(c, sizeof c, "%4.1f", km / lit); else strcpy(c, " -.-");
  snprintf(b, sizeof b, "%skm/l  Vmax%4d", c, (int)vmax);
  escribirFila(2, b);

  float aut = autonomiaKm();
  if (act && g_viaje.msParado > 0) {
    uint32_t sp = g_viaje.msParado / 60000;
    snprintf(b, sizeof b, "Parado %lumin ~%dkm", (unsigned long)sp, isnan(aut) ? 0 : (int)aut);
  } else if (!isnan(aut)) snprintf(b, sizeof b, "Autonomia ~%dkm", (int)aut);
  else snprintf(b, sizeof b, "Autonomia ---km");
  escribirFila(3, b);
}

void columnaKmpl(char* out, size_t n, const Resumen& r) {
  if (r.litros > 0.05f) snprintf(out, n, "%5.1f", r.km / r.litros); else snprintf(out, n, "  -.-");
}

void vistaComparar() {
  char b[48];
  snprintf(b, sizeof b, "COMPAR  HOY AYER PRM");
  escribirFila(0, b);
  snprintf(b, sizeof b, "km   %5.1f%5.1f%5.1f", P.hoy.km, P.ayer.km, P.prom.km);
  escribirFila(1, b);
  char c1[8], c2[8], c3[8];
  columnaKmpl(c1, sizeof c1, P.hoy); columnaKmpl(c2, sizeof c2, P.ayer); columnaKmpl(c3, sizeof c3, P.prom);
  snprintf(b, sizeof b, "km/l %s%s%s", c1, c2, c3);
  escribirFila(2, b);
  snprintf(b, sizeof b, "Vmax %5d%5d%5d", (int)P.hoy.velMax, (int)P.ayer.velMax, (int)P.prom.velMax);
  escribirFila(3, b);
}

void vistaMant() {
  char b[48];
  snprintf(b, sizeof b, "MANTENIMIENTO   %d/%d", g_selMant + 1, NUM_MANT);
  escribirFila(0, b);
  uint8_t ini = (g_selMant >= 2) ? min((int)g_selMant - 1, NUM_MANT - 3) : 0;
  bool verDias = (millis() / 2000) % 2;
  for (uint8_t f = 0; f < 3; f++) {
    uint8_t i = ini + f;
    if (i >= NUM_MANT) { b[0] = '\0'; escribirFila(f + 1, b); continue; }
    char sel = (i == g_selMant) ? '>' : ' ';
    char flag = mantVencido(i) ? '!' : ' ';
    int d = mantDiasDesde(i);
    if (verDias && d >= 0)
      snprintf(b, sizeof b, "%c%-8s%4dd/%3dd%c", sel, P.mant[i].nombre, d, P.mant[i].intDias, flag);
    else
      snprintf(b, sizeof b, "%c%-8s%4d/%4d%c", sel, P.mant[i].nombre, (int)mantKmDesde(i), P.mant[i].intKm, flag);
    escribirFila(f + 1, b);
  }
}

void vistaBaterias() {
  char b[48];
  float m = reposoTendencia();
  const char* tend = reposoCuantos() < 3 ? "" : (m < -0.02f ? " BAJANDO" : " ESTABLE");
  snprintf(b, sizeof b, "BATERIAS%s", tend);
  escribirFila(0, b);
  if (!isnan(datos.vMoto)) snprintf(b, sizeof b, "Moto %5.2fV %s", datos.vMoto, estadoBateriaMoto());
  else snprintf(b, sizeof b, "Moto  --.--V");
  escribirFila(1, b);
  if (!isnan(datos.vLipo)) snprintf(b, sizeof b, "Li-ion %4.2fV %3d%%", datos.vLipo, porcentajeLipo(datos.vLipo));
  else snprintf(b, sizeof b, "Li-ion -.--V");
  escribirFila(2, b);
  int dias = diasHastaBateriaDebil();
  if (dias >= 0) snprintf(b, sizeof b, "Cambio aprox ~%dd", dias);
  else if (reposoCuantos() > 0) snprintf(b, sizeof b, "Reposo %5.2fV (%dd)", reposoUltimo(), reposoCuantos());
  else snprintf(b, sizeof b, "Reposo: sin datos");
  escribirFila(3, b);
}

const char* okTxt(bool ok) { return ok ? "OK" : "--"; }

void vistaDiag() {
  char b[48];
  snprintf(b, sizeof b, "DIAGNOSTICO     %d/3", g_pagDiag + 1);
  escribirFila(0, b);
  if (g_pagDiag == 0) {
    snprintf(b, sizeof b, "RTC %s    IMU %s", okTxt(g_rtcOk), okTxt(g_imuOk)); escribirFila(1, b);
    snprintf(b, sizeof b, "GPS %s    SD  %s", okTxt(gpsFuncionando() || g_modoDemo), okTxt(g_sdOk)); escribirFila(2, b);
    snprintf(b, sizeof b, "Temp %s   Gas %s", okTxt(g_tempOk || g_modoDemo), okTxt(datos.gasPct >= 0)); escribirFila(3, b);
  } else if (g_pagDiag == 1) {
    bool bOk = !isnan(datos.vMoto) && datos.vMoto > 9 && datos.vMoto < 16;
    bool lOk = !isnan(datos.vLipo) && datos.vLipo > 3.0f && datos.vLipo < 4.35f;
    snprintf(b, sizeof b, "Bat moto %5.2fV %s", isnan(datos.vMoto) ? 0 : datos.vMoto, okTxt(bOk)); escribirFila(1, b);
    snprintf(b, sizeof b, "Li-ion   %5.2fV %s", isnan(datos.vLipo) ? 0 : datos.vLipo, okTxt(lOk)); escribirFila(2, b);
    snprintf(b, sizeof b, "LCD %s  Luz %3d%%", okTxt(g_lcdOk), datos.luz < 0 ? 0 : datos.luz); escribirFila(3, b);
  } else {
    if (g_ralentiMedido)
      snprintf(b, sizeof b, "Ralenti %4drpm %s", (int)g_ralenti, ralentiFuera() ? "AJ" : "OK");
    else snprintf(b, sizeof b, "Ralenti: midiendo");
    escribirFila(1, b);
    snprintf(b, sizeof b, "Arranques %8lu", (unsigned long)P.arranques); escribirFila(2, b);
    snprintf(b, sizeof b, "Odometro %7.0fkm", P.odometro); escribirFila(3, b);
  }
}

void vistaPista() {
  char b[48];
  const char* est = !g_pistaActiva ? "APAGADO" : g_pistaCorriendo ? "CORRIENDO" : (g_pistaT0 == 1 ? "LISTO" : "PARA EN 0");
  snprintf(b, sizeof b, "PISTA %s", est);
  escribirFila(0, b);
  float t = g_pistaCorriendo ? (millis() - g_pistaT0) / 1000.0f : g_pistaUltimo;
  snprintf(b, sizeof b, "0-%d %5.2fs Mj%5.2f", (int)PISTA_OBJETIVO_KMH, t, P.pistaMejor);
  escribirFila(1, b);
  snprintf(b, sizeof b, "Vmax%4d RPMmx%5d", (int)g_pistaVmax, g_pistaRpmMax);
  escribirFila(2, b);
  snprintf(b, sizeof b, "Gmax %4.2f %s", g_pistaGmax, g_pistaActiva ? "OK=salir" : "OK=activar");
  escribirFila(3, b);
}

const uint8_t NUM_AJUSTES = 6;

void textoAjuste(uint8_t i, char* out, size_t n) {
  switch (i) {
    case 0: snprintf(out, n, "Perfil     %s", P.perfil ? "SPORT" : "ECO"); break;
    case 1: snprintf(out, n, "Velocidad  %s", P.fuenteVel == 0 ? "AUTO" : P.fuenteVel == 1 ? "HALL" : "GPS"); break;
    case 2: snprintf(out, n, "Auto brillo %s", P.autoBrillo ? "SI" : "NO"); break;
    case 3: snprintf(out, n, "WiFi       %s", P.wifiOn ? "SI" : "NO"); break;
    case 4: snprintf(out, n, "Corte motor %s", P.corteActivo ? "SI" : "NO"); break;
    case 5: snprintf(out, n, "Modo demo  %s", g_modoDemo ? "SI" : "NO"); break;
  }
}

void vistaAjustes() {
  char b[48], t[32];
  snprintf(b, sizeof b, "AJUSTES  OKlargo=cam");
  escribirFila(0, b);
  uint8_t ini = (g_selAjuste >= 2) ? min((int)g_selAjuste - 1, NUM_AJUSTES - 3) : 0;
  for (uint8_t f = 0; f < 3; f++) {
    uint8_t i = ini + f;
    textoAjuste(i, t, sizeof t);
    snprintf(b, sizeof b, "%c%s", i == g_selAjuste ? '>' : ' ', t);
    escribirFila(f + 1, b);
  }
}

// escribirFila rellena el texto con espacios: necesita una copia escribible
void filaTexto(uint8_t r, const char* txt) {
  char b[LCD_COLS + 4];
  strncpy(b, txt, LCD_COLS);
  b[LCD_COLS] = '\0';
  escribirFila(r, b);
}

// Pantalla del iPhone: canción que suena y controles
void vistaIphone() {
  char b[48], t[64];
  if (!g_iphone.conectado) {
    filaTexto(0, "IPHONE  sin conexion");
    filaTexto(1, "iPhone > Ajustes >");
    filaTexto(2, "Bluetooth > FT150TS");
    filaTexto(3, "> Vincular");
    return;
  }
  float el = g_iphone.transcurrido + (g_iphone.sonando ? (millis() - g_iphone.msInfo) / 1000.0f * g_iphone.velocidad : 0);
  if (g_iphone.duracion > 0 && el > g_iphone.duracion) el = g_iphone.duracion;
  int e = (int)el, d = (int)g_iphone.duracion;
  if (d > 0) snprintf(b, sizeof b, "%s %d:%02d/%d:%02d", g_iphone.sonando ? ">" : "||", e / 60, e % 60, d / 60, d % 60);
  else snprintf(b, sizeof b, "%s IPHONE", g_iphone.sonando ? ">" : "||");
  escribirFila(0, b);
  aAscii(g_iphone.titulo[0] ? g_iphone.titulo : "Sin musica", t, sizeof t);
  // títulos largos: se recorren poco a poco
  size_t largo = strlen(t);
  if (largo > LCD_COLS) {
    size_t paso = (millis() / 700) % (largo - LCD_COLS + 4);
    if (paso > largo - LCD_COLS) paso = largo - LCD_COLS;
    memmove(t, t + paso, largo - paso + 1);
  }
  t[LCD_COLS] = '\0';
  escribirFila(1, t);
  aAscii(g_iphone.artista, t, sizeof t);
  t[LCD_COLS] = '\0';
  escribirFila(2, t);
  filaTexto(3, "OK=play  Largo=sig");
}

void pantallaMostrar() {
  switch (g_vista) {
    case V_IPHONE:   vistaIphone(); break;
    case V_VIAJE:    vistaViaje(); break;
    case V_COMPARAR: vistaComparar(); break;
    case V_MANT:     vistaMant(); break;
    case V_BATERIAS: vistaBaterias(); break;
    case V_DIAG:     vistaDiag(); break;
    case V_PISTA:    vistaPista(); break;
    case V_AJUSTES:  vistaAjustes(); break;
    default:         vistaPrincipal(); break;
  }
  if (millis() < g_avisoHasta) {
    char b[48];
    snprintf(b, sizeof b, ">> %s", g_aviso);
    escribirFila(3, b);
  }
}

// Diagnóstico al encender: muestra qué sensores respondieron
void diagnosticoInicial() {
  char b[48];
  uint8_t fallas = 0;
  bool gas = porcentajeGas() >= 0;
  float vm = voltajeMoto();
  bool bat = !isnan(vm) && vm > 9 && vm < 16;
  if (!g_rtcOk) fallas++;
  if (!g_imuOk) fallas++;
  if (!g_sdOk) fallas++;
  if (!g_tempOk) fallas++;
  if (!gas) fallas++;
  if (!bat) fallas++;
  lcd.clear();
  snprintf(b, sizeof b, "REVISION %s", fallas ? "CON AVISOS" : "OK");
  escribirFila(0, b);
  snprintf(b, sizeof b, "RTC %s IMU %s SD %s", okTxt(g_rtcOk), okTxt(g_imuOk), okTxt(g_sdOk)); escribirFila(1, b);
  snprintf(b, sizeof b, "TMP %s GAS %s BAT %s", okTxt(g_tempOk), okTxt(gas), okTxt(bat)); escribirFila(2, b);
  snprintf(b, sizeof b, "GPS: buscando senal"); escribirFila(3, b);
  Serial.printf("Diagnostico: RTC=%d IMU=%d SD=%d TEMP=%d GAS=%d BAT=%d LCD=%d\n",
                g_rtcOk, g_imuOk, g_sdOk, g_tempOk, gas, bat, g_lcdOk);
  delay(fallas ? 3000 : 1500);
  lcd.clear();
  limpiarCache();
}

// ================================================================
// 24) MENÚ CON 3 BOTONES
// ================================================================
void accionAjuste(uint8_t i) {
  TextoPrint tp;
  switch (i) {
    case 0: P.perfil = !P.perfil; shiftInit(); aviso(P.perfil ? "Perfil SPORT" : "Perfil ECO"); break;
    case 1: P.fuenteVel = (P.fuenteVel + 1) % 3; aviso("Fuente cambiada"); break;
    case 2: P.autoBrillo = !P.autoBrillo; aviso("Brillo cambiado"); break;
    case 3: P.wifiOn = !P.wifiOn; aviso(P.wifiOn ? "WiFi al reiniciar" : "WiFi apagado");
#if USAR_WIFI
      if (!P.wifiOn) WiFi.mode(WIFI_OFF);
#endif
      break;
    case 4: corteCambiar(!P.corteActivo, tp); aviso(P.corteActivo ? "Motor bloqueado" : "Motor libre"); break;
    case 5: g_modoDemo = !g_modoDemo; if (!g_modoDemo) { nvsCargar(); memset(&g_viaje, 0, sizeof(g_viaje)); }
      aviso(g_modoDemo ? "Demo activado" : "Demo apagado"); break;
  }
  nvsGuardar();
}

void manejarBotones() {
  Evento e = botonesLeer();
  if (e == EV_NINGUNO) return;
  g_ultActividad = millis();
  float v = isnan(datos.vel) ? 0 : datos.vel;
  bool moviendo = v > VEL_BLOQUEO_MENU && !g_modoDemo;

  // Llamada del iPhone sonando: OK contesta, OK largo cuelga (en cualquier pantalla)
  if (g_iphone.llamada && (e == EV_OK || e == EV_OK_LARGO)) {
    if (iosLlamada(e == EV_OK ? 0 : 1)) aviso(e == EV_OK ? "Contestando" : "Colgando");
    return;
  }
  // Pantalla IPHONE: música aunque vayas en movimiento
  if (g_vista == V_IPHONE && (e == EV_OK || e == EV_OK_LARGO)) {
    if (!iosMusica(e == EV_OK ? 2 : 3)) aviso("iPhone no conectado");
    return;
  }

  if (e == EV_ARRIBA) { pantallaIrA((Vista)((g_vista + V_TOTAL - 1) % V_TOTAL)); return; }
  if (e == EV_ABAJO)  { pantallaIrA((Vista)((g_vista + 1) % V_TOTAL)); return; }

  if (e == EV_OK) {
    switch (g_vista) {
      case V_MANT:    g_selMant = (g_selMant + 1) % NUM_MANT; break;
      case V_AJUSTES: g_selAjuste = (g_selAjuste + 1) % NUM_AJUSTES; break;
      case V_DIAG:    g_pagDiag = (g_pagDiag + 1) % 3; break;
      case V_PISTA:   g_pistaActiva = !g_pistaActiva; if (g_pistaActiva) pistaReset(); break;
      default: break;
    }
    limpiarCache();
    return;
  }

  // OK largo
  if (moviendo && g_vista != V_PISTA) { aviso("Detente primero"); return; }
  switch (g_vista) {
    case V_MANT:
      mantRegistrar(g_selMant);
      aviso("Servicio registrado");
      break;
    case V_AJUSTES:
      accionAjuste(g_selAjuste);
      break;
    case V_PISTA:
      pistaReset();
      aviso("Pista reiniciada");
      break;
    case V_PRINCIPAL:
      if (g_caida || g_corteCaida) { g_caida = false; g_corteCaida = false; releAplicar(); aviso("Alerta borrada"); }
      g_arranqueDebil = false; g_alertaDescMoto = false; g_alertaDescLipo = false;
      break;
    default:
      pantallaIrA(V_PRINCIPAL);
      break;
  }
  limpiarCache();
}

// ================================================================
// 25) MODO DEMO (datos simulados)
// ================================================================
void demoGenerar() {
  float t = millis() / 1000.0f;
  float v = 45 + 40 * sin(t / 6.0f);
  if (v < 0) v = 0;
  int m;
  if (v < 3) m = 0; else if (v < 15) m = 1; else if (v < 30) m = 2; else if (v < 50) m = 3; else if (v < 70) m = 4; else m = 5;
  int rpm = (m == 0) ? 1450 + (int)(40 * sin(t * 3)) : (int)(v * P.ratioMarcha[m - 1] * (1 + 0.03f * sin(t * 2)));

  datos.vel = v; datos.velHall = v; datos.velGps = v;
  datos.rpm = rpm;
  datos.neutral = (m == 0);
  datos.marcha = calcularMarcha(v, rpm, datos.neutral);
  float fase = fmod(t, 20.0f);
  datos.vMoto = (fase < 0.5f) ? 10.9f : 13.9f + 0.2f * sin(t / 4.0f);
  datos.vLipo = 3.9f + 0.1f * sin(t / 30.0f);
  datos.gasPct = 100 - ((int)(t * 2)) % 100;
  datos.gasMv = NAN;
  datos.tempC = 70 + 25 * (1 - exp(-t / 30.0f));
  datos.gpsFix = true;
  datos.lat = 21.5042 + 0.001 * sin(t / 50.0f);
  datos.lon = -104.8946 + 0.001 * cos(t / 50.0f);
  datos.sats = 9;
  datos.ignicion = true;
  datos.incl = 3 + 2 * sin(t);
  datos.aFwd = 0.1f * cos(t / 6.0f);
  if (millis() < g_simCaidaHasta) { datos.incl = 80; datos.vel = 0; }
  if (millis() < g_simFrenadaHasta) { datos.aFwd = -0.7f; datos.vel = max(datos.vel, 30.0f); }
  datos.luz = porcentajeLuz();
}

// ================================================================
// 26) LECTURA DE SENSORES REALES
// ================================================================
void actualizarDatosReales() {
  datos.vMoto = voltajeMoto();
  datos.vLipo = voltajeLipo();
  datos.gasMv = g_mvGas;
  datos.gasPct = porcentajeGas();
  int r = rpmInstantanea();
  g_rpmFilt = (r == 0) ? 0 : g_rpmFilt * 0.6f + r * 0.4f;
  datos.rpm = (int)g_rpmFilt;
  datos.velHall = velocidadHall();
  datos.vel = elegirVelocidad();
  datos.neutral = leerNeutral();
  datos.marcha = calcularMarcha(datos.vel, datos.rpm, datos.neutral);
  datos.ignicion = leerIgnicion();
}

// ================================================================
// 27) NOTIFICACIONES (Bluetooth o WiFi)
// ================================================================
// El LCD solo tiene letras sin acentos: "José" -> "Jose", emojis fuera.
void aAscii(const char* src, char* dst, size_t n) {
  // Latin-1 0xC0..0xFF  ->  letra sin acento
  static const char TABLA[] = "AAAAAAACEEEEIIIIDNOOOOOxOUUUUYPsaaaaaaaceeeeiiiidnooooo/ouuuuypy";
  size_t j = 0;
  const uint8_t* s = (const uint8_t*)src;
  for (size_t i = 0; s[i] && j < n - 1; ) {
    uint8_t c = s[i];
    if (c < 0x80) { if (c >= 32 && c != '~') dst[j++] = (char)c; i++; continue; }
    uint8_t c2 = s[i + 1];   // si el texto se acaba aquí, c2 = 0
    if (c == 0xC3 && c2 >= 0x80 && c2 <= 0xBF) dst[j++] = TABLA[c2 - 0x80];
    else if (c == 0xC2 && c2 == 0xA1) dst[j++] = '!';
    else if (c == 0xC2 && c2 == 0xBF) dst[j++] = '?';
    // salta la letra completa (2, 3 o 4 bytes: acentos raros, símbolos, emojis) sin pasarse del final
    uint8_t largo = ((c & 0xE0) == 0xC0) ? 2 : ((c & 0xF0) == 0xE0) ? 3 : ((c & 0xF8) == 0xF0) ? 4 : 1;
    while (largo-- > 0 && s[i]) i++;
  }
  dst[j] = '\0';
}

void notifMostrar(const char* txt) {
  aAscii(txt, g_notif, sizeof(g_notif));
  g_notifHasta = millis() + NOTIF_MS;
}

// Corta un texto UTF-8 sin partir una letra a la mitad (para el JSON de la app)
void cortarUtf8(char* dst, size_t n, const char* src) {
  size_t len = strlen(src);
  if (len >= n) {
    len = n - 1;
    while (len > 0 && ((uint8_t)src[len] & 0xC0) == 0x80) len--;
  }
  memcpy(dst, src, len);
  dst[len] = '\0';
}

#if USAR_BLE
BLEServer* g_servidor = nullptr;
volatile uint16_t g_gattsIf = ESP_GATT_IF_NONE;   // para mandar datos a una sola conexión

// Se guarda el "gatts_if" del servidor (la librería no lo deja leer)
void capturarGattsIf(esp_gatts_cb_event_t e, esp_gatt_if_t gatts_if, esp_ble_gatts_cb_param_t* p) {
  if (e == ESP_GATTS_REG_EVT || e == ESP_GATTS_CONNECT_EVT) g_gattsIf = gatts_if;
}
BLECharacteristic* g_chTel = nullptr;
BLECharacteristic* g_chResp = nullptr;
// Cola de comandos recibidos por Bluetooth (se ejecutan en loop)
const uint8_t COLA_CMD = 6;
char g_cmdCola[COLA_CMD][100];
bool g_cmdCifrado[COLA_CMD];     // el comando llegó por un enlace cifrado (teléfono vinculado)
volatile uint8_t g_cmdIni = 0, g_cmdFin = 0;

// ---------- Seguridad Bluetooth (v1.4) ----------
// Cualquiera con una app BLE genérica podía conectarse y mandar "corte on confirmar". Ahora:
//  1) Los comandos delicados solo se aceptan por un enlace cifrado (teléfono vinculado).
//  2) Vincular un teléfono NUEVO solo se puede en una ventana: los primeros 2 min después de encender
//     la tarjeta, o 2 min después del comando "vincular" (mandado desde un teléfono ya vinculado o por USB).
//  3) A un teléfono ya vinculado se le cifra el enlace al conectarse.
// Por USB (Serial) todo sigue permitido: requiere tener la tarjeta en la mano.
uint32_t g_ventanaVincularHasta = 120000;
bool ventanaVincular() { return (int32_t)(g_ventanaVincularHasta - millis()) > 0; }

bool estaVinculado(const uint8_t* bda) {
  int n = esp_ble_get_bond_device_num();
  if (n <= 0) return false;
  esp_ble_bond_dev_t* lista = (esp_ble_bond_dev_t*)malloc(sizeof(esp_ble_bond_dev_t) * n);
  if (!lista) return false;
  esp_ble_get_bond_device_list(&n, lista);
  bool si = false;
  for (int i = 0; i < n; i++) if (memcmp(lista[i].bd_addr, bda, 6) == 0) si = true;
  free(lista);
  return si;
}
uint32_t g_ultTelRapida = 0, g_ultTelLenta = 0;
uint8_t g_turnoLenta = 0;

ConexionBle g_conex[MAX_CONEX];

// La app de Android cuenta como conectada; el iPhone no (no recibe telemetría)
void recalcularConexiones() {
  bool app = false;
  for (uint8_t i = 0; i < MAX_CONEX; i++) if (g_conex[i].usada && !g_conex[i].esIphone) app = true;
  g_bleConectado = app;
}

void marcarApp(uint16_t connId) {
  for (uint8_t i = 0; i < MAX_CONEX; i++) if (g_conex[i].usada && g_conex[i].id == connId) g_conex[i].esApp = true;
}

void iosDesconectado();   // abajo, en la parte del iPhone

class BleServidorCb : public BLEServerCallbacks {
  void onConnect(BLEServer* s, esp_ble_gatts_cb_param_t* p) override {
    for (uint8_t i = 0; i < MAX_CONEX; i++) {
      if (g_conex[i].usada) continue;
      g_conex[i] = ConexionBle();
      g_conex[i].usada = true;
      g_conex[i].id = p->connect.conn_id;
      memcpy(g_conex[i].bda, p->connect.remote_bda, 6);
      g_conex[i].tipoDir = p->connect.ble_addr_type;
      g_conex[i].desde = millis();
      break;
    }
    // Teléfono ya vinculado: se cifra el enlace de una vez (así sus comandos delicados valen)
    if (estaVinculado(p->connect.remote_bda)) esp_ble_set_encryption(p->connect.remote_bda, ESP_BLE_SEC_ENCRYPT);
    recalcularConexiones();
    BLEDevice::startAdvertising();   // sigue visible para el otro teléfono
  }
  void onDisconnect(BLEServer* s, esp_ble_gatts_cb_param_t* p) override {
    for (uint8_t i = 0; i < MAX_CONEX; i++) {
      if (!g_conex[i].usada || g_conex[i].id != p->disconnect.conn_id) continue;
      if (g_conex[i].esIphone) iosDesconectado();
      g_conex[i].usada = false;
    }
    recalcularConexiones();
    BLEDevice::startAdvertising();
  }
};

class BleNotifCb : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c, esp_ble_gatts_cb_param_t* p) override {
    marcarApp(p->write.conn_id);
    String v = String(c->getValue().c_str());
    strncpy(g_notifBuf, v.c_str(), sizeof(g_notifBuf) - 1);
    g_notifBuf[sizeof(g_notifBuf) - 1] = '\0';
    g_notifNueva = true;
  }
};

// Los comandos se guardan aquí y se ejecutan en loop() (no dentro del
// hilo de Bluetooth, para no chocar con el resto del programa)
class BleCmdCb : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c, esp_ble_gatts_cb_param_t* p) override {
    marcarApp(p->write.conn_id);
    uint8_t sig = (g_cmdFin + 1) % COLA_CMD;
    if (sig == g_cmdIni) return;   // cola llena: se descarta
    String v = String(c->getValue().c_str());
    strncpy(g_cmdCola[g_cmdFin], v.c_str(), 99);
    g_cmdCola[g_cmdFin][99] = '\0';
    bool cif = false;
    for (uint8_t i = 0; i < MAX_CONEX; i++)
      if (g_conex[i].usada && g_conex[i].id == p->write.conn_id) cif = g_conex[i].cifrada;
    g_cmdCifrado[g_cmdFin] = cif;
    g_cmdFin = sig;
  }
};

// ================================================================
// 27b) IPHONE: notificaciones (ANCS), música (AMS) y hora (CTS)
// ================================================================
// Son servicios que iOS trae para accesorios (así reciben avisos los
// relojes que no son de Apple). No hay que instalar nada en el iPhone.
// Cómo se reconoce: la app de Android escribe un comando apenas se
// conecta; si una conexión no escribe nada en 3.5 s, se revisa si
// tiene el servicio ANCS/AMS (eso solo lo tiene un iPhone).
static const char* ANCS_SVC   = "7905F431-B5CE-4E99-A40F-4B1E122D00D0";
static const char* ANCS_AVISO = "9FBF120D-6301-42D9-8C58-25E699A21DBD";
static const char* ANCS_CTRL  = "69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9";
static const char* ANCS_DATOS = "22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB";
static const char* AMS_SVC    = "89D3502B-0F36-433A-8EF4-C502AD55F8DC";
static const char* AMS_CMD    = "9B3C81D8-57B1-4A8A-B8DF-0E56F7CA51C2";
static const char* AMS_ENT    = "2F7CABCE-808D-411F-9A0C-BB92BA96C102";

QueueHandle_t g_colaIos = nullptr;
BLEClient* g_iosCliente = nullptr;
BLERemoteCharacteristic* g_ancsCtrl = nullptr;
BLERemoteCharacteristic* g_amsCmd = nullptr;
volatile int8_t g_iosRevisar = -1;          // conexión que loop pide revisar
volatile bool g_iosAuthOk = false, g_iosAuthFallo = false;
volatile bool g_iosSeFue = false;           // el iPhone se desconectó
portMUX_TYPE g_iosMux = portMUX_INITIALIZER_UNLOCKED;

// Notificación lista (la arma la tarea del iPhone y la muestra loop)
volatile bool g_iosNotifNueva = false;
char g_iosLcd[48] = "";
char g_iosApp[24] = "", g_iosTitulo[48] = "", g_iosMensaje[104] = "";
uint8_t g_iosCategoria = 0;
volatile bool g_iosMusicaCambio = false, g_iosLlamadaCambio = false;

// Respuesta de ANCS en armado (puede llegar en varios pedazos)
uint8_t  g_dsBuf[420];
uint16_t g_dsLen = 0;
uint32_t g_dsUid = 0;
uint8_t  g_dsCat = 0;
bool     g_dsAcciones = false;

class SeguridadCb : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override { return 123456; }
  void onPassKeyNotify(uint32_t) override {}
  // Vincular un teléfono nuevo solo dentro de la ventana (ver "Seguridad Bluetooth"); los ya vinculados
  // se reconectan cifrados sin pasar por aquí
  bool onConfirmPIN(uint32_t) override { return ventanaVincular(); }
  bool onSecurityRequest() override { return ventanaVincular(); }
  void onAuthenticationComplete(esp_ble_auth_cmpl_t r) override {
    if (r.success) g_iosAuthOk = true; else g_iosAuthFallo = true;
    // Un iPhone ya vinculado cifra el enlace él solo al reconectarse
    for (uint8_t i = 0; i < MAX_CONEX; i++)
      if (g_conex[i].usada && memcmp(g_conex[i].bda, r.bd_addr, 6) == 0) g_conex[i].cifrada = r.success;
  }
};

void iosEncolar(uint8_t origen, const uint8_t* d, size_t n) {
  if (!g_colaIos) return;
  PaqueteIos p;
  p.origen = origen;
  p.len = (uint8_t)min(n, sizeof(p.datos));
  memcpy(p.datos, d, p.len);
  xQueueSend(g_colaIos, &p, 0);   // si la cola está llena, se pierde (no bloquea el Bluetooth)
}

void cbAncsAviso(BLERemoteCharacteristic*, uint8_t* d, size_t n, bool) { iosEncolar(1, d, n); }
void cbAncsDatos(BLERemoteCharacteristic*, uint8_t* d, size_t n, bool) { iosEncolar(2, d, n); }
void cbAmsEnt(BLERemoteCharacteristic*, uint8_t* d, size_t n, bool) { iosEncolar(3, d, n); }
void cbAmsCmd(BLERemoteCharacteristic*, uint8_t* d, size_t n, bool) { iosEncolar(4, d, n); }

void iosDesconectado() {
  g_iosSeFue = true;
  portENTER_CRITICAL(&g_iosMux);
  g_iphone.conectado = false;
  g_iphone.ancs = g_iphone.ams = false;
  g_iphone.sonando = false;
  g_iphone.llamada = false;
  portEXIT_CRITICAL(&g_iosMux);
  g_iosMusicaCambio = true;
  g_iosLlamadaCambio = true;
}

// Nombre corto de la app que mandó la notificación
const char* nombreAppIos(const char* id) {
  struct Par { const char* id; const char* nombre; };
  static const Par APPS[] = {
    {"net.whatsapp.WhatsApp", "WhatsApp"}, {"net.whatsapp.WhatsAppSMB", "WhatsApp"},
    {"com.apple.MobileSMS", "Mensajes"}, {"com.apple.mobilephone", "Tel"},
    {"com.apple.facetime", "FaceTime"}, {"ph.telegra.Telegraph", "Telegram"},
    {"com.burbn.instagram", "Instagram"}, {"com.facebook.Messenger", "Messenger"},
    {"com.facebook.Facebook", "Facebook"}, {"com.google.Gmail", "Gmail"},
    {"com.apple.mobilemail", "Mail"}, {"com.atebits.Tweetie2", "X"},
    {"com.zhiliaoapp.musically", "TikTok"}, {"com.apple.reminders", "Recordat"},
    {"com.apple.mobilecal", "Calendario"}, {"com.ubercab.UberClient", "Uber"},
    {"com.google.Maps", "Maps"}, {"com.apple.Maps", "Mapas"},
  };
  for (const Par& a : APPS) if (strcmp(id, a.id) == 0) return a.nombre;
  const char* punto = strrchr(id, '.');   // com.empresa.App -> App
  return punto ? punto + 1 : id;
}

// Pide al iPhone el detalle de una notificación (app, título y mensaje)
void ancsPedirDetalle(uint32_t uid) {
  if (!g_ancsCtrl || !g_iosCliente || !g_iosCliente->isConnected()) return;
  uint8_t cmd[] = {
    0,                                                       // GetNotificationAttributes
    (uint8_t)uid, (uint8_t)(uid >> 8), (uint8_t)(uid >> 16), (uint8_t)(uid >> 24),
    0,                                                       // AppIdentifier
    1, 40, 0,                                                // Title (40 bytes)
    3, 100, 0,                                               // Message (100 bytes)
  };
  g_ancsCtrl->writeValue(cmd, sizeof cmd, true);
}

// Contestar (0) o colgar/rechazar (1) la llamada
void ancsAccion(uint32_t uid, uint8_t accion) {
  if (!g_ancsCtrl || !g_iosCliente || !g_iosCliente->isConnected()) return;
  uint8_t cmd[] = { 2, (uint8_t)uid, (uint8_t)(uid >> 8), (uint8_t)(uid >> 16), (uint8_t)(uid >> 24), accion };
  g_ancsCtrl->writeValue(cmd, sizeof cmd, true);
}

void ancsProcesarAviso(const uint8_t* d, uint8_t n) {
  if (n < 8) return;
  uint8_t evento = d[0], flags = d[1], cat = d[2];
  uint32_t uid = d[4] | (d[5] << 8) | ((uint32_t)d[6] << 16) | ((uint32_t)d[7] << 24);
  bool previa = flags & 0x04;     // ya estaba antes de conectar
  bool silenciosa = flags & 0x01;

  if (cat == 1) {                 // llamada entrante
    if (evento == 0 || evento == 1) {
      portENTER_CRITICAL(&g_iosMux);
      g_iphone.llamada = true;
      g_iphone.llamadaUid = uid;
      g_iphone.llamadaMs = millis();
      g_iphone.llamadaAcciones = (flags & 0x18) != 0;
      portEXIT_CRITICAL(&g_iosMux);
      g_iosLlamadaCambio = true;
    } else if (evento == 2 && uid == g_iphone.llamadaUid) {
      portENTER_CRITICAL(&g_iosMux);
      g_iphone.llamada = false;
      portEXIT_CRITICAL(&g_iosMux);
      g_iosLlamadaCambio = true;
      return;
    }
  }
  if (evento != 0) return;                        // solo las nuevas
  if ((previa || silenciosa) && cat != 1) return; // no repite las viejas al conectar
  g_dsLen = 0;
  g_dsUid = uid;
  g_dsCat = cat;
  g_dsAcciones = (flags & 0x18) != 0;
  ancsPedirDetalle(uid);
}

// Arma la respuesta: [0][uid x4] y luego (id, largo x2, texto) por cada dato
void ancsProcesarDatos(const uint8_t* d, uint8_t n) {
  if (g_dsLen + n > sizeof(g_dsBuf)) g_dsLen = 0;
  memcpy(g_dsBuf + g_dsLen, d, n);
  g_dsLen += n;
  if (g_dsLen < 5) return;
  if (g_dsBuf[0] != 0) { g_dsLen = 0; return; }

  char appId[48] = "", titulo[48] = "", mensaje[104] = "";
  uint16_t pos = 5;
  uint8_t listos = 0;
  while (listos < 3) {
    if (pos + 3 > g_dsLen) return;                // falta un pedazo: esperar
    uint8_t id = g_dsBuf[pos];
    uint16_t largo = g_dsBuf[pos + 1] | (g_dsBuf[pos + 2] << 8);
    if (pos + 3 + largo > g_dsLen) return;        // falta un pedazo
    char* dst = id == 0 ? appId : id == 1 ? titulo : mensaje;
    size_t cap = id == 0 ? sizeof appId : id == 1 ? sizeof titulo : sizeof mensaje;
    size_t k = min((size_t)largo, cap - 1);
    memcpy(dst, g_dsBuf + pos + 3, k);
    dst[k] = '\0';
    pos += 3 + largo;
    listos++;
  }
  g_dsLen = 0;

  // Texto listo
  const char* app = nombreAppIos(appId);
  char lcd[48];
  if (g_dsCat == 1) snprintf(lcd, sizeof lcd, "Llamada: %s", titulo);
  else if (titulo[0]) snprintf(lcd, sizeof lcd, "%s: %s", app, titulo);
  else snprintf(lcd, sizeof lcd, "%s: %s", app, mensaje);

  portENTER_CRITICAL(&g_iosMux);
  strncpy(g_iosLcd, lcd, sizeof(g_iosLcd) - 1); g_iosLcd[sizeof(g_iosLcd) - 1] = '\0';
  cortarUtf8(g_iosApp, sizeof g_iosApp, app);
  cortarUtf8(g_iosTitulo, sizeof g_iosTitulo, titulo);
  cortarUtf8(g_iosMensaje, sizeof g_iosMensaje, mensaje);
  g_iosCategoria = g_dsCat;
  if (g_dsCat == 1 && g_dsUid == g_iphone.llamadaUid) cortarUtf8(g_iphone.llamadaDe, sizeof g_iphone.llamadaDe, titulo);
  portEXIT_CRITICAL(&g_iosMux);
  g_iosNotifNueva = true;
  if (g_dsCat == 1) g_iosLlamadaCambio = true;
}

// AMS: [entidad][atributo][banderas][texto...]
void amsProcesar(const uint8_t* d, uint8_t n) {
  if (n < 3) return;
  uint8_t ent = d[0], atr = d[1];
  char v[64];
  size_t k = min((size_t)(n - 3), sizeof(v) - 1);
  memcpy(v, d + 3, k);
  v[k] = '\0';
  portENTER_CRITICAL(&g_iosMux);
  if (ent == 2) {                                 // canción
    if (atr == 0) cortarUtf8(g_iphone.artista, sizeof g_iphone.artista, v);
    else if (atr == 1) cortarUtf8(g_iphone.album, sizeof g_iphone.album, v);
    else if (atr == 2) cortarUtf8(g_iphone.titulo, sizeof g_iphone.titulo, v);
    else if (atr == 3) g_iphone.duracion = atof(v);
  } else if (ent == 0) {                          // reproductor
    if (atr == 0) cortarUtf8(g_iphone.app, sizeof g_iphone.app, v);
    else if (atr == 1) {                          // "estado,velocidad,transcurrido"
      int estado = atoi(v);
      const char* c1 = strchr(v, ',');
      const char* c2 = c1 ? strchr(c1 + 1, ',') : nullptr;
      g_iphone.sonando = estado == 1;
      g_iphone.velocidad = c1 ? atof(c1 + 1) : 0;
      g_iphone.transcurrido = c2 ? atof(c2 + 1) : 0;
      g_iphone.msInfo = millis();
    } else if (atr == 2) g_iphone.volumen = atof(v);
  }
  portEXIT_CRITICAL(&g_iosMux);
  g_iosMusicaCambio = true;
}

// Hora del iPhone (Current Time Service): año x2, mes, día, h, min, s
void iosAjustarHora(const uint8_t* d, size_t n) {
  if (n < 7 || !g_rtcOk) return;
  int y = d[0] | (d[1] << 8);
  if (y < 2024 || d[2] < 1 || d[2] > 12 || d[3] < 1) return;
  rtc.adjust(DateTime(y, d[2], d[3], d[4], d[5], d[6]));
  Serial.println(F("iPhone: reloj ajustado."));
}

void iosLimpiar() {
  g_ancsCtrl = nullptr;
  g_amsCmd = nullptr;
  if (g_iosCliente) {
    delete g_iosCliente;      // ya no hay conexión: se libera
    g_iosCliente = nullptr;
  }
}

// Revisa una conexión: si es un iPhone, se vincula y se suscribe
void iosRevisar(uint8_t i) {
  ConexionBle c = g_conex[i];
  if (!c.usada || c.esApp) return;
  if (g_iosCliente) return;                         // ya hay un iPhone
  BLEClient* cli = BLEDevice::createClient();
  if (!cli->connect(BLEAddress(c.bda), (esp_ble_addr_type_t)c.tipoDir, 5000)) { delete cli; return; }
  BLERemoteService* ancs = cli->getService(BLEUUID(ANCS_SVC));
  BLERemoteService* ams = cli->getService(BLEUUID(AMS_SVC));
  if (!ancs && !ams) {                              // no es un iPhone
    cli->disconnect();
    return;
  }
  g_conex[i].esIphone = true;
  recalcularConexiones();
  g_iosCliente = cli;
  g_iosSeFue = false;
  Serial.println(F("iPhone conectado: vinculando..."));

  // Cifrado: la primera vez el iPhone pide "Vincular"; después se reconecta solo
  if (!g_conex[i].cifrada) {
    g_iosAuthOk = g_iosAuthFallo = false;
    esp_ble_set_encryption(c.bda, ESP_BLE_SEC_ENCRYPT);
    uint32_t t0 = millis();
    while (!g_iosAuthOk && !g_iosAuthFallo && !g_conex[i].cifrada && millis() - t0 < 25000 && cli->isConnected()) delay(50);
  }
  if (!cli->isConnected()) return;
  if (g_iosAuthFallo) Serial.println(F("iPhone: no se pudo vincular (se intenta igual)"));

  if (ancs) {
    BLERemoteCharacteristic* aviso = ancs->getCharacteristic(BLEUUID(ANCS_AVISO));
    BLERemoteCharacteristic* datos = ancs->getCharacteristic(BLEUUID(ANCS_DATOS));
    g_ancsCtrl = ancs->getCharacteristic(BLEUUID(ANCS_CTRL));
    if (datos) datos->registerForNotify(cbAncsDatos);   // primero los datos (lo pide Apple)
    if (aviso) aviso->registerForNotify(cbAncsAviso);
  }
  if (ams) {
    g_amsCmd = ams->getCharacteristic(BLEUUID(AMS_CMD));
    BLERemoteCharacteristic* ent = ams->getCharacteristic(BLEUUID(AMS_ENT));
    if (g_amsCmd && g_amsCmd->canNotify()) g_amsCmd->registerForNotify(cbAmsCmd);
    if (ent) {
      ent->registerForNotify(cbAmsEnt);
      uint8_t cancion[] = {2, 0, 1, 2, 3};     // canción: artista, álbum, título, duración
      ent->writeValue(cancion, sizeof cancion, true);
      uint8_t repro[] = {0, 0, 1, 2};          // reproductor: app, estado, volumen
      ent->writeValue(repro, sizeof repro, true);
    }
  }
  BLERemoteService* cts = cli->getService(BLEUUID((uint16_t)0x1805));
  if (cts) {
    BLERemoteCharacteristic* hora = cts->getCharacteristic(BLEUUID((uint16_t)0x2A2B));
    if (hora && hora->canRead()) {
      String v = hora->readValue();
      iosAjustarHora((const uint8_t*)v.c_str(), v.length());
    }
  }
  portENTER_CRITICAL(&g_iosMux);
  g_iphone.conectado = true;
  g_iphone.ancs = ancs != nullptr;
  g_iphone.ams = ams != nullptr;
  portEXIT_CRITICAL(&g_iosMux);
  g_iosMusicaCambio = true;
  Serial.println(F("iPhone listo: notificaciones y musica."));
}

// Todo lo del iPhone corre en su propia tarea: así la pantalla y los
// sensores no se traban mientras se vincula o se piden los datos.
void tareaIphone(void*) {
  PaqueteIos p;
  for (;;) {
    if (g_iosSeFue) { g_iosSeFue = false; vTaskDelay(pdMS_TO_TICKS(300)); iosLimpiar(); }
    int8_t r = g_iosRevisar;
    if (r >= 0) { iosRevisar(r); g_iosRevisar = -1; }
    if (xQueueReceive(g_colaIos, &p, pdMS_TO_TICKS(100)) != pdTRUE) continue;
    if (!g_iosCliente || !g_iosCliente->isConnected()) continue;
    switch (p.origen) {
      case 1: ancsProcesarAviso(p.datos, p.len); break;
      case 2: ancsProcesarDatos(p.datos, p.len); break;
      case 3: amsProcesar(p.datos, p.len); break;
      case 10:                                     // orden desde loop / app
        if (p.datos[0] == 1 && g_amsCmd) g_amsCmd->writeValue(&p.datos[1], 1, true);
        else if (p.datos[0] == 2 && g_iphone.llamada) ancsAccion(g_iphone.llamadaUid, p.datos[1]);
        break;
      default: break;
    }
  }
}

// Comandos de música del iPhone (AMS): 0 play, 1 pausa, 2 play/pausa,
// 3 siguiente, 4 anterior, 5 volumen+, 6 volumen-
bool iosMusica(uint8_t comando) {
  if (!g_iphone.conectado || !g_iphone.ams) return false;
  uint8_t d[2] = {1, comando};
  iosEncolar(10, d, 2);
  return true;
}

// Llamada: 0 contestar, 1 colgar/rechazar
bool iosLlamada(uint8_t accion) {
  if (!g_iphone.conectado || !g_iphone.llamada) return false;
  uint8_t d[2] = {2, accion};
  iosEncolar(10, d, 2);
  return true;
}

void iosOlvidar() {
  int n = esp_ble_get_bond_device_num();
  if (n <= 0) return;
  esp_ble_bond_dev_t* lista = (esp_ble_bond_dev_t*)malloc(sizeof(esp_ble_bond_dev_t) * n);
  if (!lista) return;
  esp_ble_get_bond_device_list(&n, lista);
  for (int i = 0; i < n; i++) esp_ble_remove_bond_device(lista[i].bd_addr);
  free(lista);
}

void bleBegin() {
  BLEDevice::init(BLE_NOMBRE);
  BLEDevice::setMTU(247);    // paquetes grandes para mandar el JSON completo

  // Vínculo con el iPhone: "Just Works" (sin PIN), guardado en la memoria
  BLEDevice::setSecurityCallbacks(new SeguridadCb());
  BLESecurity* seg = new BLESecurity();
  seg->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND);
  seg->setCapability(ESP_IO_CAP_NONE);
  seg->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
  seg->setRespEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);

  BLEDevice::setCustomGattsHandler(capturarGattsIf);
  g_servidor = BLEDevice::createServer();
  g_servidor->setCallbacks(new BleServidorCb());
  BLEService* svc = g_servidor->createService(BLEUUID(BLE_SERVICIO), 20);

  BLECharacteristic* chN = svc->createCharacteristic(BLE_NOTIF, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  chN->setCallbacks(new BleNotifCb());

  g_chTel = svc->createCharacteristic(BLE_TELEM, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  g_chTel->addDescriptor(new BLE2902());

  BLECharacteristic* chC = svc->createCharacteristic(BLE_CMD, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  chC->setCallbacks(new BleCmdCb());

  g_chResp = svc->createCharacteristic(BLE_RESP, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  g_chResp->addDescriptor(new BLE2902());

  svc->start();

  // Anuncio: el servicio de la app en el paquete principal; el nombre y la
  // "solicitud de ANCS" en la respuesta (así aparece en Ajustes > Bluetooth del iPhone)
  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(BLE_SERVICIO);
  adv->setScanResponse(true);
  BLEAdvertisementData resp;
  resp.setName(BLE_NOMBRE);
  static const uint8_t ANCS_LE[16] = {   // 7905F431-B5CE-4E99-A40F-4B1E122D00D0 al revés
    0xD0, 0x00, 0x2D, 0x12, 0x1E, 0x4B, 0x0F, 0xA4, 0x99, 0x4E, 0xCE, 0xB5, 0x31, 0xF4, 0x05, 0x79 };
  String solicitud;
  solicitud.concat((char)17);       // largo
  solicitud.concat((char)0x15);     // tipo: solicitud de servicio de 128 bits
  for (uint8_t i = 0; i < 16; i++) solicitud.concat((char)ANCS_LE[i]);
  resp.addData(solicitud);
  adv->setScanResponseData(resp);
  BLEDevice::startAdvertising();

  g_colaIos = xQueueCreate(20, sizeof(PaqueteIos));
  xTaskCreatePinnedToCore(tareaIphone, "iphone", 8192, nullptr, 1, nullptr, 0);
}
#endif

#if !USAR_BLE
bool iosMusica(uint8_t comando) { return false; }
bool iosLlamada(uint8_t accion) { return false; }
void iosOlvidar() {}
#endif

void notifRevisar() {
  if (!g_notifNueva) return;
  g_notifNueva = false;
  notifMostrar(g_notifBuf);
}

// ================================================================
// 28) CONSOLA (Serial y página web usan los mismos comandos)
// ================================================================
void mostrarAyuda(Print& out) {
  out.println(F("=== Comandos ==="));
  out.println(F(" estado                  datos actuales"));
  out.println(F(" demo / real             modo demo o sensores reales"));
  out.println(F(" pantalla N              0..8 (principal, iphone .. ajustes)"));
  out.println(F(" perfil eco|sport"));
  out.println(F(" vel auto|hall|gps       fuente de velocidad"));
  out.println(F(" corte on confirmar      bloquear motor (parado)"));
  out.println(F(" corte off               desbloquear"));
  out.println(F(" cal gas vacio|lleno     guarda la lectura actual"));
  out.println(F(" cal bat 12.65           voltaje real del multimetro"));
  out.println(F(" cal imu                 con la moto derecha"));
  out.println(F(" cal marcha N            rodando en esa marcha"));
  out.println(F(" odo 12345               fija el odometro"));
  out.println(F(" mant                    lista mantenimientos"));
  out.println(F(" mant reset N            registra servicio N"));
  out.println(F(" mant set N km dias      cambia intervalos"));
  out.println(F(" hora 2026-10-02 14:30   ajusta el reloj"));
  out.println(F(" eventos                 ultimos eventos"));
  out.println(F(" notif texto             prueba una notificacion"));
  out.println(F(" mantjson                mantenimiento en JSON (app)"));
  out.println(F(" cfgjson                 ajustes e info de la tarjeta en JSON (app)"));
  out.println(F(" brillo auto|fijo        brillo del LCD y LEDs segun la luz"));
  out.println(F(" wifi on|off             pagina WiFi (on aplica al reiniciar)"));
  out.println(F(" reiniciar               reinicia la tarjeta"));
  out.println(F(" ios                     estado del iPhone"));
  out.println(F(" ios play|pausa|toggle|sig|ant|vol+|vol-   musica del iPhone"));
  out.println(F(" ios contestar|colgar    llamada entrante del iPhone"));
  out.println(F(" ios olvidar             borra el vinculo (volver a vincular)"));
  out.println(F(" vincular                abre 2 min para vincular un telefono nuevo"));
  out.println(F(" wifi clave              muestra la clave del WiFi de la tarjeta"));
  out.println(F(" sim caida|frenada       solo en demo"));
  out.println(F(" backup                  respaldo ahora (WiFi casa)"));
  out.println(F(" dormir                  prueba el modo ahorro"));
  out.println(F(" borrar todo confirmar   regresa a valores de fabrica"));
}

void mostrarEstado(Print& out) {
  out.printf("Modo: %s | Pantalla: %s\n", g_modoDemo ? "DEMO" : "REAL", NOMBRE_VISTA[g_vista]);
  out.printf("Vel %.1f km/h (hall %.1f gps %.1f) | RPM %d | Marcha %d | Neutral %d\n",
             datos.vel, datos.velHall, datos.velGps, datos.rpm, datos.marcha, datos.neutral);
  out.printf("Bat moto %.2f V (%s) | Li-ion %.2f V | Gas %d%% (%.0f mV)\n",
             datos.vMoto, estadoBateriaMoto(), datos.vLipo, datos.gasPct, datos.gasMv);
  out.printf("Temp %.1f C | Incl %.1f deg | aFwd %.2f g | Luz %d%%\n", datos.tempC, datos.incl, datos.aFwd, datos.luz);
  out.printf("GPS fix %d sats %d %.6f,%.6f | Ignicion %d | Motor %d\n",
             datos.gpsFix, datos.sats, datos.lat, datos.lon, datos.ignicion, g_motorEnc);
  out.printf("Odometro %.1f km | Arranques %lu | km/l prom %.1f | Shift %.0f rpm\n",
             P.odometro, (unsigned long)P.arranques, P.kmPorLitro, puntoCambioActual());
  out.printf("Viaje activo %d km %.2f | Hoy %.1f km | Ayer %.1f km\n", g_viaje.activo, g_viaje.km, P.hoy.km, P.ayer.km);
  out.printf("Corte %d | Caida %d | Perfil %s\n", P.corteActivo, g_caida, P.perfil ? "SPORT" : "ECO");
}

String palabra(const String& s, int n) {
  int idx = 0, i = 0;
  while (i < (int)s.length()) {
    while (i < (int)s.length() && s[i] == ' ') i++;
    int ini = i;
    while (i < (int)s.length() && s[i] != ' ') i++;
    if (ini < i) { if (idx == n) return s.substring(ini, i); idx++; }
  }
  return String("");
}

void hacerBackup(Print& out);
void irADormir(bool porTimer);

uint32_t g_reiniciarEn = 0;     // "reiniciar": se hace un momento después para alcanzar a responder

// Ajustes e información de la tarjeta (lo usa la app para mostrar los interruptores bien puestos)
void cfgJson(Print& out) {
  out.printf("{\"fw\":\"%s\",\"up\":%lu,\"hp\":%u,\"tc\":%.1f,\"pf\":%d,\"fv\":%d,\"ab\":%d,\"wf\":%d,"
             "\"kl\":%.1f,\"bf\":%.3f,\"gv\":%.0f,\"gl\":%.0f,\"dm\":%d,\"rtc\":%d,\"imu\":%d,\"sd\":%d,\"tp\":%d,"
             "\"pm\":%.2f,\"sh\":[",
             FW_VERSION, (unsigned long)(millis() / 1000), (unsigned)(ESP.getFreeHeap() / 1024), temperatureRead(),
             P.perfil, P.fuenteVel, P.autoBrillo, P.wifiOn, P.kmPorLitro, P.batFactor, P.gasMvVacio, P.gasMvLleno,
             g_modoDemo, g_rtcOk, g_imuOk, g_sdOk, g_tempOk, P.pistaMejor);
  for (uint8_t i = 0; i < NUM_MARCHAS; i++) out.printf("%s%.0f", i ? "," : "", g_shiftActual[i]);
  out.print(F("],\"rm\":["));
  for (uint8_t i = 0; i < NUM_MARCHAS; i++) out.printf("%s%.1f", i ? "," : "", P.ratioMarcha[i]);
  out.println(F("]}"));
}

// Comandos que cambian algo delicado (motor, calibración, datos, red, llamadas). Solo se aceptan por
// Bluetooth con enlace cifrado (teléfono vinculado) o por USB; nunca por la página WiFi.
bool comandoDelicado(const String& low) {
  String c0 = palabra(low, 0), c1 = palabra(low, 1);
  if (c0 == "corte" || c0 == "cal" || c0 == "odo" || c0 == "real" || c0 == "demo" || c0 == "reiniciar" ||
      c0 == "wifi" || c0 == "vel" || c0 == "vincular") return true;
  if (c0 == "mant" && (c1 == "reset" || c1 == "set")) return true;
  if (c0 == "ios" && (c1 == "olvidar" || c1 == "contestar" || c1 == "colgar")) return true;
  return false;
}

#if USAR_WIFI
// Clave del WiFi de la tarjeta: única por tarjeta (se genera al azar la primera vez y se guarda).
// Antes era "12345678" para todas, y está publicada en el repositorio.
char g_apClave[16] = "";
void apClavePreparar() {
  if (strcmp(AP_PASS, "12345678") != 0 && strlen(AP_PASS) >= 8 && strlen(AP_PASS) < sizeof(g_apClave)) {
    strncpy(g_apClave, AP_PASS, sizeof(g_apClave) - 1);   // pusiste tu propia clave en AP_PASS
    return;
  }
  Preferences s;
  s.begin("seguridad", false);
  String c = s.getString("apclave", "");
  if (c.length() < 8) {
    static const char ABC[] = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // sin 0/O ni 1/I para no confundirse
    c = "";
    for (int i = 0; i < 10; i++) c += ABC[esp_random() % 32];
    s.putString("apclave", c);
  }
  s.end();
  strncpy(g_apClave, c.c_str(), sizeof(g_apClave) - 1);
}
#endif

void ejecutarComando(String linea, Print& out) {
  linea.trim();
  String low = linea;
  low.toLowerCase();
  if (low.length() == 0) return;
  String c0 = palabra(low, 0), c1 = palabra(low, 1), c2 = palabra(low, 2), c3 = palabra(low, 3);
  g_ultActividad = millis();

  if (c0 == "ayuda" || c0 == "help") mostrarAyuda(out);
  else if (c0 == "estado") mostrarEstado(out);
  else if (c0 == "demo") { g_modoDemo = true; out.println(F("Modo demo activado.")); }
  else if (c0 == "real") { g_modoDemo = false; nvsCargar(); memset(&g_viaje, 0, sizeof(g_viaje)); out.println(F("Sensores reales.")); }
  else if (c0 == "pantalla") { int n = c1.toInt(); if (n >= 0 && n < V_TOTAL) { pantallaIrA((Vista)n); out.println(NOMBRE_VISTA[n]); } }
  else if (c0 == "perfil") { P.perfil = (c1 == "sport") ? 1 : 0; shiftInit(); nvsGuardar(); out.println(P.perfil ? "SPORT" : "ECO"); }
  else if (c0 == "vel") { P.fuenteVel = (c1 == "hall") ? 1 : (c1 == "gps") ? 2 : 0; nvsGuardar(); out.println(F("Fuente de velocidad cambiada.")); }
  else if (c0 == "corte") {
    if (c1 == "on" && c2 == "confirmar") corteCambiar(true, out);
    else if (c1 == "off") corteCambiar(false, out);
    else out.println(F("Usa: corte on confirmar  /  corte off"));
  }
  else if (c0 == "cal" && c1 == "gas") {
    if (c2 == "vacio") P.gasMvVacio = g_mvGas; else if (c2 == "lleno") P.gasMvLleno = g_mvGas;
    nvsGuardar();
    out.printf("Gas: vacio=%.0f mV lleno=%.0f mV\n", P.gasMvVacio, P.gasMvLleno);
  }
  else if (c0 == "cal" && c1 == "bat") {
    float real = c2.toFloat();
    float leido = isnan(g_mvBat) ? 0 : g_mvBat / 1000.0f * (BAT_R1 + BAT_R2) / BAT_R2;
    if (real > 5 && leido > 1) { P.batFactor = real / leido; nvsGuardar(); out.printf("Factor bateria = %.4f\n", P.batFactor); }
    else out.println(F("Usa: cal bat 12.65"));
  }
  else if (c0 == "cal" && c1 == "imu") { imuCalibrar(); out.println(F("IMU calibrado (moto derecha = 0 grados).")); }
  else if (c0 == "cal" && c1 == "marcha") {
    int n = c2.toInt();
    if (n >= 1 && n <= NUM_MARCHAS && !isnan(datos.vel) && datos.vel > 5 && datos.rpm > 0) {
      P.ratioMarcha[n - 1] = datos.rpm / datos.vel; nvsGuardar();
      out.printf("Marcha %d = %.1f rpm por km/h\n", n, P.ratioMarcha[n - 1]);
    } else out.println(F("Ve rodando en esa marcha a velocidad constante."));
  }
  else if (c0 == "odo") { P.odometro = c1.toFloat(); nvsGuardar(); out.printf("Odometro = %.1f km\n", P.odometro); }
  else if (c0 == "mant" && c1 == "") {
    for (uint8_t i = 0; i < NUM_MANT; i++)
      out.printf("%d %-8s %5.0f/%u km  %d/%u dias %s\n", i, P.mant[i].nombre, mantKmDesde(i), P.mant[i].intKm,
                 mantDiasDesde(i), P.mant[i].intDias, mantVencido(i) ? "VENCIDO" : "");
  }
  else if (c0 == "mant" && c1 == "reset") { int n = c2.toInt(); if (n >= 0 && n < NUM_MANT) { mantRegistrar(n); out.println(F("Servicio registrado.")); } }
  else if (c0 == "mant" && c1 == "set") {
    int n = c2.toInt();
    if (n >= 0 && n < NUM_MANT) {
      P.mant[n].intKm = c3.toInt();
      P.mant[n].intDias = palabra(low, 4).toInt();
      nvsGuardar(); out.println(F("Intervalo cambiado."));
    }
  }
  else if (c0 == "ios") {
    if (c1 == "" || c1 == "estado") {
      out.printf("iPhone: %s | notif %d | musica %d\n", g_iphone.conectado ? "conectado" : "no", g_iphone.ancs, g_iphone.ams);
      if (g_iphone.conectado) out.printf("%s %s - %s\n", g_iphone.sonando ? "Suena" : "Pausa", g_iphone.titulo, g_iphone.artista);
    }
    else if (c1 == "olvidar") { iosOlvidar(); out.println(F("Vinculos borrados. En el iPhone: Ajustes > Bluetooth > FT150TS > Omitir.")); }
    else if (c1 == "contestar" || c1 == "colgar") out.println(iosLlamada(c1 == "contestar" ? 0 : 1) ? F("OK") : F("No hay llamada."));
    else {
      int cmd = c1 == "play" ? 0 : c1 == "pausa" ? 1 : c1 == "toggle" ? 2 : c1 == "sig" ? 3 : c1 == "ant" ? 4 :
                c1 == "vol+" ? 5 : c1 == "vol-" ? 6 : -1;
      if (cmd < 0) out.println(F("Usa: ios play|pausa|toggle|sig|ant|vol+|vol-"));
      else out.println(iosMusica(cmd) ? F("OK") : F("iPhone no conectado."));
    }
  }
  else if (c0 == "hora") {
    int y, mo, d, h, mi;
    String f = palabra(linea, 1) + " " + palabra(linea, 2);
    if (g_rtcOk && sscanf(f.c_str(), "%d-%d-%d %d:%d", &y, &mo, &d, &h, &mi) == 5) {
      rtc.adjust(DateTime(y, mo, d, h, mi, 0)); out.println(F("Reloj ajustado."));
    } else out.println(F("Usa: hora 2026-10-02 14:30  (requiere RTC)"));
  }
  else if (c0 == "eventos") {
    for (uint8_t k = 0; k < NUM_EVT_RAM; k++) {
      EventoReg& e = g_evt[(g_evtIdx + k) % NUM_EVT_RAM];
      if (e.epoch == 0) continue;
      char f[24]; fechaTexto(f, sizeof f, DateTime(e.epoch));
      out.printf("%s %s %.2f\n", f, NOMBRE_EVT[e.tipo], e.valor);
    }
  }
  else if (c0 == "gps") {        // lo usa la app: gps <lat> <lon> <kmh>  (sin respuesta)
    g_gpsExtLat = strtod(c1.c_str(), nullptr);
    g_gpsExtLon = strtod(c2.c_str(), nullptr);
    g_gpsExtVel = (c3.length() && c3.toFloat() >= 0) ? c3.toFloat() : NAN;
    if (fabs(g_gpsExtLat) > 0.001 || fabs(g_gpsExtLon) > 0.001) g_gpsExtHasta = millis() + 3000;
  }
  else if (c0 == "mantjson") {   // lo usa la app para dibujar el mantenimiento
    out.print('[');
    for (uint8_t i = 0; i < NUM_MANT; i++) {
      out.printf("%s{\"n\":\"%s\",\"k\":%d,\"ik\":%u,\"d\":%d,\"id\":%u,\"v\":%d}", i ? "," : "",
                 P.mant[i].nombre, (int)mantKmDesde(i), P.mant[i].intKm, mantDiasDesde(i), P.mant[i].intDias, mantVencido(i));
    }
    out.println(']');
  }
  else if (c0 == "notif") { notifMostrar(linea.substring(6).c_str()); out.println(F("Notificacion mostrada.")); }
  else if (c0 == "cfgjson") cfgJson(out);
  else if (c0 == "brillo") {
    if (c1 == "auto") P.autoBrillo = true;
    else if (c1 == "fijo") P.autoBrillo = false;
    nvsGuardar();
    out.println(P.autoBrillo ? F("Brillo automatico.") : F("Brillo fijo."));
  }
#if USAR_WIFI
  else if (c0 == "wifi" && c1 == "clave") out.printf("Red %s  clave %s\n", AP_SSID, g_apClave);
#endif
  else if (c0 == "wifi") {
    if (c1 == "on") P.wifiOn = true;
    else if (c1 == "off") {
      P.wifiOn = false;
#if USAR_WIFI
      WiFi.mode(WIFI_OFF);
#endif
    }
    nvsGuardar();
    out.println(P.wifiOn ? F("WiFi encendido (al reiniciar).") : F("WiFi apagado."));
  }
  else if (c0 == "reiniciar") { nvsGuardar(); g_reiniciarEn = millis() + 1200; out.println(F("Reiniciando...")); }
#if USAR_BLE
  else if (c0 == "vincular") { g_ventanaVincularHasta = millis() + 120000; out.println(F("Vinculacion abierta 2 min.")); }
#endif
  else if (c0 == "sim" && g_modoDemo) {
    if (c1 == "caida") g_simCaidaHasta = millis() + 6000;
    else if (c1 == "frenada") g_simFrenadaHasta = millis() + 1500;
    out.println(F("Simulando."));
  }
  else if (c0 == "backup") hacerBackup(out);
  else if (c0 == "dormir") { out.println(F("Durmiendo...")); irADormir(false); }
  else if (c0 == "borrar" && c1 == "todo" && c2 == "confirmar") {
    nvsDefaults(); prefs.putBytes("p", &P, sizeof(P)); shiftInit(); out.println(F("Valores de fabrica."));
  }
  else { out.print(F("No entendi: ")); out.println(linea); out.println(F("Escribe 'ayuda'.")); }
}

void leerConsola() {
  static String buf;
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (buf.length()) { ejecutarComando(buf, Serial); buf = ""; }
    } else if (buf.length() < 80) buf += c;
  }
}

// ================================================================
// 29) WIFI: página para el celular, descargas CSV y respaldo
// ================================================================
#if USAR_WIFI
String jsonDatos() {
  char b[700];
  DateTime t = ahora();
  snprintf(b, sizeof b,
    "{\"vel\":%.1f,\"rpm\":%d,\"marcha\":%d,\"vMoto\":%.2f,\"vLipo\":%.2f,\"gas\":%d,\"temp\":%.1f,"
    "\"incl\":%.1f,\"fix\":%d,\"lat\":%.6f,\"lon\":%.6f,\"sats\":%d,\"odo\":%.1f,\"arranques\":%lu,"
    "\"viajeKm\":%.2f,\"hoyKm\":%.1f,\"ayerKm\":%.1f,\"kmpl\":%.1f,\"autonomia\":%.0f,\"bat\":\"%s\","
    "\"corte\":%d,\"demo\":%d,\"hora\":\"%02d:%02d\"}",
    isnan(datos.vel) ? 0 : datos.vel, datos.rpm, datos.marcha, isnan(datos.vMoto) ? 0 : datos.vMoto,
    isnan(datos.vLipo) ? 0 : datos.vLipo, datos.gasPct, isnan(datos.tempC) ? 0 : datos.tempC,
    isnan(datos.incl) ? 0 : datos.incl, datos.gpsFix, datos.lat, datos.lon, datos.sats, P.odometro,
    (unsigned long)P.arranques, g_viaje.km, P.hoy.km, P.ayer.km, P.kmPorLitro,
    isnan(autonomiaKm()) ? 0 : autonomiaKm(), estadoBateriaMoto(), P.corteActivo, g_modoDemo, t.hour(), t.minute());
  return String(b);
}

const char PAGINA[] PROGMEM =
  "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
  "<title>FT150TS</title><style>body{font-family:sans-serif;background:#111;color:#eee;margin:0;padding:12px}"
  "h1{font-size:20px;margin:4px 0 12px}.g{display:grid;grid-template-columns:1fr 1fr;gap:8px}"
  ".c{background:#222;border-radius:10px;padding:10px}.l{font-size:12px;color:#999}.v{font-size:24px}"
  "a,button{color:#7cf}input{width:70%;padding:6px}pre{white-space:pre-wrap;font-size:12px}</style></head><body>"
  "<h1>FT150TS Dashboard</h1><div class='g' id='g'></div>"
  "<h3>Descargas (Excel)</h3><a href='/archivo?f=viajes.csv'>viajes.csv</a> | "
  "<a href='/archivo?f=eventos.csv'>eventos.csv</a> | <a href='/archivo?f=bateria.csv'>bateria.csv</a> | "
  "<a href='/archivo?f=pista.csv'>pista.csv</a>"
  "<h3>Consola</h3><input id='c' placeholder='ayuda'><button onclick='env()'>Enviar</button><pre id='r'></pre>"
  "<script>"
  "const n={vel:'km/h',rpm:'RPM',marcha:'Marcha',vMoto:'Bat moto V',vLipo:'Li-ion V',gas:'Gasolina %',"
  "temp:'Temp C',autonomia:'Autonomia km',viajeKm:'Viaje km',hoyKm:'Hoy km',ayerKm:'Ayer km',kmpl:'km/l',"
  "odo:'Odometro',arranques:'Arranques',bat:'Bateria',sats:'Satelites',hora:'Hora'};"
  "async function t(){try{const d=await (await fetch('/api')).json();let h='';"
  "for(const k in n){h+=`<div class='c'><div class='l'>${n[k]}</div><div class='v'>${d[k]}</div></div>`}"
  "document.getElementById('g').innerHTML=h}catch(e){}}"
  "async function env(){const c=document.getElementById('c').value;"
  "const r=await fetch('/cmd?c='+encodeURIComponent(c));document.getElementById('r').textContent=await r.text()}"
  "setInterval(t,1000);t();</script></body></html>";

void webBegin() {
  if (!P.wifiOn) return;
  bool sta = strcmp(STA_SSID, "X") != 0;
  WiFi.mode(sta ? WIFI_AP_STA : WIFI_AP);
  apClavePreparar();                       // ya con la radio prendida: el azar es de verdad
  WiFi.softAP(AP_SSID, g_apClave);
  if (sta) WiFi.begin(STA_SSID, STA_PASS);

  server.on("/", []() { server.send_P(200, "text/html", PAGINA); });
  server.on("/api", []() { server.send(200, "application/json", jsonDatos()); });
  server.on("/cmd", []() {
    String c = server.arg("c");
    String low = c;
    low.trim();
    low.toLowerCase();
    // Por la red WiFi no pasan comandos delicados (corte, calibración, borrar…): solo Bluetooth vinculado o USB
    if (comandoDelicado(low)) { server.send(403, "text/plain", "Ese comando solo por Bluetooth vinculado o USB."); return; }
    TextoPrint tp;
    ejecutarComando(c, tp);
    server.send(200, "text/plain", tp.s);
  });
  server.on("/notif", []() {
    notifMostrar(server.arg("t").c_str());
    server.send(200, "text/plain", "ok");
  });
  server.on("/archivo", []() {
    String f = server.arg("f");
    if (f != "viajes.csv" && f != "eventos.csv" && f != "bateria.csv" && f != "pista.csv") {
      server.send(400, "text/plain", "archivo no permitido"); return;
    }
    if (!g_sdOk || !SD.exists("/" + f)) { server.send(404, "text/plain", "sin datos"); return; }
    File a = SD.open("/" + f);
    server.sendHeader("Content-Disposition", "attachment; filename=" + f);
    server.streamFile(a, "text/csv");
    a.close();
  });
  server.begin();
  g_wifiIniciado = true;
  Serial.printf("WiFi: conectate a '%s' (clave %s) y abre http://%s\n", AP_SSID, g_apClave, WiFi.softAPIP().toString().c_str());
}
#endif

// Sube los CSV a BACKUP_URL una vez al día cuando hay WiFi de casa
void hacerBackup(Print& out) {
#if USAR_WIFI
  if (strcmp(BACKUP_URL, "X") == 0) { out.println(F("Configura BACKUP_URL primero.")); return; }
  if (WiFi.status() != WL_CONNECTED) { out.println(F("Sin WiFi de casa.")); return; }
  if (!g_sdOk) { out.println(F("Sin tarjeta SD.")); return; }
  const char* archivos[4] = {"/viajes.csv", "/eventos.csv", "/bateria.csv", "/pista.csv"};
  bool todoOk = true;
  for (uint8_t i = 0; i < 4; i++) {
    if (!SD.exists(archivos[i])) continue;
    File f = SD.open(archivos[i]);
    HTTPClient http;
    http.begin(BACKUP_URL);
    http.addHeader("Content-Type", "text/csv");
    http.addHeader("X-Archivo", archivos[i] + 1);
    int code = http.sendRequest("POST", &f, f.size());
    http.end();
    f.close();
    out.printf("%s -> %d\n", archivos[i], code);
    if (code < 200 || code >= 300) todoOk = false;
  }
  if (todoOk) { P.diaBackup = ymd(ahora()); nvsGuardar(); }
#else
  out.println(F("WiFi desactivado en esta compilacion."));
#endif
}

void backupAutomatico() {
#if USAR_WIFI
  if (millis() - g_ultBackupIntento < 600000 || g_viaje.activo || g_modoDemo) return;
  if (WiFi.status() != WL_CONNECTED || strcmp(BACKUP_URL, "X") == 0) return;
  if (P.diaBackup == ymd(ahora())) return;
  g_ultBackupIntento = millis();
  hacerBackup(Serial);
#endif
}

// ================================================================
// 29b) TELEMETRÍA PARA LA APP ANDROID (Bluetooth)
// ================================================================
// Tres mensajes JSON cortos (caben en un paquete BLE de 244 bytes):
//   "t" rápido (cada 250 ms): velocidad, rpm, marcha, batería, gasolina...
//   "s" estado (cada 1 s):    Li-ion, GPS, alerta, corte, perfil...
//   "x" viaje  (cada 2 s):    odómetro, viaje, hoy/ayer, autonomía...
void textoSeguro(char* dst, size_t n, const char* src) {
  size_t j = 0;
  for (size_t i = 0; src[i] && j < n - 1; i++) {
    char c = src[i];
    if ((uint8_t)c < 32 || c == '"' || c == '\\') continue;
    dst[j++] = c;
  }
  dst[j] = '\0';
}

int jsonRapido(char* b, size_t n) {
  return snprintf(b, n,
    "{\"k\":\"t\",\"v\":%.1f,\"r\":%d,\"m\":%d,\"b\":%.2f,\"g\":%d,\"t\":%.1f,"
    "\"i\":%.1f,\"a\":%.2f,\"sp\":%d,\"ig\":%d,\"me\":%d}",
    isnan(datos.vel) ? -1.0f : datos.vel, datos.rpm, datos.marcha,
    isnan(datos.vMoto) ? -1.0f : datos.vMoto, datos.gasPct,
    isnan(datos.tempC) ? -99.0f : datos.tempC, isnan(datos.incl) ? -1.0f : datos.incl,
    datos.aFwd, (int)puntoCambioActual(), datos.ignicion ? 1 : 0, g_motorEnc ? 1 : 0);
}

int jsonEstado(char* b, size_t n) {
  char al[32] = "", alSeg[32] = "";
  Icono ic;
  if (construirAlerta(al, sizeof al, ic)) textoSeguro(alSeg, sizeof alSeg, al);
  return snprintf(b, n,
    "{\"k\":\"s\",\"l\":%.2f,\"lp\":%d,\"f\":%d,\"la\":%.6f,\"lo\":%.6f,\"sa\":%d,"
    "\"es\":\"%s\",\"c\":%d,\"d\":%d,\"pf\":%d,\"ge\":%d,\"ip\":%d,\"al\":\"%s\"}",
    isnan(datos.vLipo) ? -1.0f : datos.vLipo, porcentajeLipo(datos.vLipo), datos.gpsFix ? 1 : 0,
    datos.lat, datos.lon, datos.sats, estadoBateriaMoto(),
    (P.corteActivo || g_corteCaida) ? 1 : 0, g_modoDemo ? 1 : 0, P.perfil, g_gpsExterno ? 1 : 0,
    g_iphone.conectado ? 1 : 0, alSeg);
}

int jsonViaje(char* b, size_t n) {
  float aut = autonomiaKm();
  return snprintf(b, n,
    "{\"k\":\"x\",\"o\":%.1f,\"ar\":%lu,\"va\":%d,\"vk\":%.2f,\"vm\":%.0f,\"vl\":%.2f,"
    "\"hk\":%.1f,\"yk\":%.1f,\"kl\":%.1f,\"au\":%d,\"mv\":%d,\"ra\":%d,\"pm\":%.2f}",
    P.odometro, (unsigned long)P.arranques, g_viaje.activo ? 1 : 0, g_viaje.km, g_viaje.velMax,
    viajeLitros(), P.hoy.km, P.ayer.km, P.kmPorLitro, isnan(aut) ? -1 : (int)aut,
    mantPrimeroVencido(), g_ralentiMedido ? (int)g_ralenti : 0, P.pistaMejor);
}

#if USAR_BLE
// Manda a la app (o apps) conectadas, pero no al iPhone: él no entiende estos datos
void bleNotificar(BLECharacteristic* ch, const char* txt, size_t len) {
  if (!ch || !g_bleConectado || g_gattsIf == ESP_GATT_IF_NONE) return;
  ch->setValue((uint8_t*)txt, len);
  for (uint8_t i = 0; i < MAX_CONEX; i++) {
    if (!g_conex[i].usada || g_conex[i].esIphone) continue;
    esp_ble_gatts_send_indicate(g_gattsIf, g_conex[i].id, ch->getHandle(), len, (uint8_t*)txt, false);
  }
}

// Mensajes del iPhone para la app:
//   "i" notificación (app, título, mensaje)
//   "a" música (título, artista, si suena, tiempo)
//   "c" llamada entrante (de quién)
int jsonIosNotif(char* b, size_t n) {
  char app[24], ti[48], tx[104];
  portENTER_CRITICAL(&g_iosMux);
  textoSeguro(app, sizeof app, g_iosApp);
  textoSeguro(ti, sizeof ti, g_iosTitulo);
  textoSeguro(tx, sizeof tx, g_iosMensaje);
  uint8_t cat = g_iosCategoria;
  portEXIT_CRITICAL(&g_iosMux);
  return snprintf(b, n, "{\"k\":\"i\",\"ap\":\"%s\",\"ti\":\"%s\",\"tx\":\"%s\",\"ca\":%d}", app, ti, tx, cat);
}

int jsonIosMusica(char* b, size_t n) {
  char ti[64], ar[56], ap[20];
  portENTER_CRITICAL(&g_iosMux);
  textoSeguro(ti, sizeof ti, g_iphone.titulo);
  textoSeguro(ar, sizeof ar, g_iphone.artista);
  textoSeguro(ap, sizeof ap, g_iphone.app);
  bool on = g_iphone.conectado && g_iphone.ams, pl = g_iphone.sonando;
  float du = g_iphone.duracion, ra = g_iphone.velocidad;
  float el = g_iphone.transcurrido + (pl ? (millis() - g_iphone.msInfo) / 1000.0f * ra : 0);
  float vo = g_iphone.volumen;
  portEXIT_CRITICAL(&g_iosMux);
  return snprintf(b, n,
    "{\"k\":\"a\",\"on\":%d,\"pl\":%d,\"ti\":\"%s\",\"ar\":\"%s\",\"ap\":\"%s\",\"du\":%.1f,\"el\":%.1f,\"vo\":%.2f}",
    on ? 1 : 0, pl ? 1 : 0, ti, ar, ap, du, el, vo);
}

int jsonIosLlamada(char* b, size_t n) {
  char de[40];
  portENTER_CRITICAL(&g_iosMux);
  textoSeguro(de, sizeof de, g_iphone.llamadaDe);
  bool on = g_iphone.llamada, acc = g_iphone.llamadaAcciones;
  portEXIT_CRITICAL(&g_iosMux);
  return snprintf(b, n, "{\"k\":\"c\",\"on\":%d,\"de\":\"%s\",\"ac\":%d}", on ? 1 : 0, de, acc ? 1 : 0);
}

void iosActualizar() {
  uint32_t ms = millis();
  // ¿Una conexión nueva que no es la app? Puede ser el iPhone
  if (g_iosRevisar < 0 && !g_iosCliente) {
    for (uint8_t i = 0; i < MAX_CONEX; i++) {
      ConexionBle& c = g_conex[i];
      if (c.usada && !c.esApp && !c.revisada && ms - c.desde > 3500) { c.revisada = true; g_iosRevisar = i; break; }
    }
  }
  char b[244];
  // Notificación nueva del iPhone: al LCD y a la app
  if (g_iosNotifNueva) {
    g_iosNotifNueva = false;
    char lcd[48];
    portENTER_CRITICAL(&g_iosMux);
    strncpy(lcd, g_iosLcd, sizeof lcd);
    portEXIT_CRITICAL(&g_iosMux);
    lcd[sizeof(lcd) - 1] = '\0';
    notifMostrar(lcd);
    int n = jsonIosNotif(b, sizeof b);
    if (n > 0) bleNotificar(g_chTel, b, min(n, (int)sizeof(b) - 1));
  }
  // Llamada: avisa al instante y cada 2 s mientras suena
  static uint32_t ultLlamada = 0;
  if (g_iosLlamadaCambio || (g_iphone.llamada && ms - ultLlamada > 2000)) {
    if (g_iosLlamadaCambio && g_iphone.llamada) aviso("OK=si Largo=no");
    g_iosLlamadaCambio = false;
    ultLlamada = ms;
    int n = jsonIosLlamada(b, sizeof b);
    if (n > 0) bleNotificar(g_chTel, b, min(n, (int)sizeof(b) - 1));
  }
  // Se acabó de sonar sin respuesta (el iPhone a veces no avisa): 45 s máximo
  if (g_iphone.llamada && ms - g_iphone.llamadaMs > 45000) { g_iphone.llamada = false; g_iosLlamadaCambio = true; }
  // Música: al cambiar (máx. 2 veces por segundo) y cada 5 s para sincronizar el tiempo
  static uint32_t ultMusica = 0;
  if ((g_iosMusicaCambio && ms - ultMusica > 500) || (g_iphone.conectado && ms - ultMusica > 5000)) {
    g_iosMusicaCambio = false;
    ultMusica = ms;
    int n = jsonIosMusica(b, sizeof b);
    if (n > 0) bleNotificar(g_chTel, b, min(n, (int)sizeof(b) - 1));
  }
}

void bleActualizar() {
  iosActualizar();

  // 1) Comando recibido desde la app
  if (g_cmdIni != g_cmdFin) {
    String c = String(g_cmdCola[g_cmdIni]);
    bool cifrado = g_cmdCifrado[g_cmdIni];
    g_cmdIni = (g_cmdIni + 1) % COLA_CMD;
    TextoPrint tp;
    String low = c;
    low.trim();
    low.toLowerCase();
    if (!cifrado && comandoDelicado(low)) tp.println(F("Bloqueado: vincula el telefono con la moto (enlace cifrado)."));
    else ejecutarComando(c, tp);
    if (tp.s.length() && g_bleConectado) {
      for (size_t i = 0; i < tp.s.length(); i += 180) {
        String parte = tp.s.substring(i, min(i + 180, (size_t)tp.s.length()));
        bleNotificar(g_chResp, parte.c_str(), parte.length());
        delay(10);
      }
      const char fin = 4;     // marca de fin de respuesta
      bleNotificar(g_chResp, &fin, 1);
    }
  }

  // 2) Telemetría
  if (!g_bleConectado) return;
  uint32_t ms = millis();
  char b[244];
  if (ms - g_ultTelRapida >= 250) {
    g_ultTelRapida = ms;
    int n = jsonRapido(b, sizeof b);
    if (n > 0) bleNotificar(g_chTel, b, min(n, (int)sizeof(b) - 1));
  }
  if (ms - g_ultTelLenta >= 1000) {
    g_ultTelLenta = ms;
    int n = (g_turnoLenta++ % 2 == 0) ? jsonEstado(b, sizeof b) : jsonViaje(b, sizeof b);
    if (n > 0) bleNotificar(g_chTel, b, min(n, (int)sizeof(b) - 1));
  }
}
#endif

// ================================================================
// 30) AHORRO DE ENERGÍA (sueño profundo)
// ================================================================
void periferico(bool encendido) {
  if (PIN_PERIF_EN >= 0) digitalWrite(PIN_PERIF_EN, encendido ? HIGH : LOW);
}

void irADormir(bool porTimer) {
#if USAR_DORMIR
  if (g_modoDemo) return;
  if (!porTimer) {
    viajeTerminar();
    P.vMotoDormir = voltajeMoto();
    P.vLipoDormir = voltajeLipo();
    P.epochDormir = g_rtcOk ? ahoraEpoch() : 0;
    nvsGuardar();
    leds.clear(); leds.show();
    lcd.clear();
    lcd.noBacklight();
#if USAR_WIFI
    WiFi.mode(WIFI_OFF);
#endif
    periferico(false);
  }
  esp_sleep_enable_timer_wakeup((uint64_t)DESPERTAR_CADA_S * 1000000ULL);
  uint64_t mascara = (1ULL << PIN_BTN_OK) | (1ULL << PIN_BTN_ARRIBA) | (1ULL << PIN_BTN_ABAJO);
#if USAR_IGNICION
  mascara |= (1ULL << PIN_IGNICION);
#endif
  const uint8_t pines[4] = {PIN_BTN_OK, PIN_BTN_ARRIBA, PIN_BTN_ABAJO, PIN_IGNICION};
  for (uint8_t i = 0; i < 4; i++) {
    rtc_gpio_pullup_en((gpio_num_t)pines[i]);
    rtc_gpio_pulldown_dis((gpio_num_t)pines[i]);
  }
  esp_sleep_enable_ext1_wakeup(mascara, ESP_EXT1_WAKEUP_ANY_LOW);
  Serial.flush();
  esp_deep_sleep_start();
#endif
}

void revisarDormir() {
#if USAR_DORMIR
  if (g_modoDemo) return;
  if (datos.ignicion || g_motorEnc) { g_ultActividad = millis(); return; }
  if (millis() - g_ultActividad > TIEMPO_A_DORMIR_MS) irADormir(false);
#endif
}

// Despertó solo para revisar baterías: mide, guarda y vuelve a dormir
void despertarPorTimer() {
  for (uint8_t i = 0; i < 40; i++) { adcActualizar(); delay(25); }
  datos.vMoto = voltajeMoto();
  datos.vLipo = voltajeLipo();
  datos.ignicion = false;
  if (g_rtcOk && P.epochDormir > 0 && ahoraEpoch() - P.epochDormir > 7200) registrarReposoDiario(datos.vMoto);
  logBaterias();
  if (g_nvsSucio) nvsGuardar();
  irADormir(true);
}

// ================================================================
// 31) SETUP / LOOP
// ================================================================
void setup() {
  Serial.begin(115200);
  pinMode(PIN_RPM, INPUT_PULLUP);
  pinMode(PIN_HALL, INPUT_PULLUP);
  pinMode(PIN_NEUTRAL, INPUT_PULLUP);
  pinMode(PIN_IGNICION, INPUT_PULLUP);
  pinMode(PIN_RELE, OUTPUT);
  if (PIN_PERIF_EN >= 0) pinMode(PIN_PERIF_EN, OUTPUT);
  for (uint8_t i = 0; i < 4; i++) {
    const uint8_t p[4] = {PIN_BTN_OK, PIN_BTN_ARRIBA, PIN_BTN_ABAJO, PIN_IGNICION};
    rtc_gpio_deinit((gpio_num_t)p[i]);   // regresa los pines a modo normal tras dormir
  }
  botonesBegin();
  adcBegin();
  nvsCargar();
  releAplicar();   // primero que nada: respeta el bloqueo guardado
  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
  Wire.setClock(100000);
  g_rtcOk = rtc.begin(&Wire);
  if (g_rtcOk && rtc.lostPower()) rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
  sdBegin();

#if USAR_DORMIR
  if (esp_sleep_get_wakeup_cause() == ESP_SLEEP_WAKEUP_TIMER) despertarPorTimer();
#endif

  periferico(true);
  leds.begin();
  leds.clear();
  leds.show();
  pantallaBegin();
  pantallaSplash();
  g_imuOk = imuBegin();
  tempBegin();
  GPSSerial.begin(9600, SERIAL_8N1, PIN_GPS_RX, PIN_GPS_TX);
  attachInterrupt(digitalPinToInterrupt(PIN_RPM), isrRpm, FALLING);
  attachInterrupt(digitalPinToInterrupt(PIN_HALL), isrHall, FALLING);
  shiftInit();
  for (uint8_t i = 0; i < 25; i++) { adcActualizar(); delay(20); }
  revisarAutodescarga();
  revisarCambioDia();
  diagnosticoInicial();

  g_modoDemo = DEMO_AL_INICIAR;
#if USAR_WIFI
  webBegin();
#endif
#if USAR_BLE
  bleBegin();
#endif
  g_ultActividad = millis();
  g_ultIgnOn = millis();
  g_ultTick = millis();
  Serial.printf("FT150TS %s listo. Escribe 'ayuda'.\n", FW_VERSION);
  if (g_modoDemo) Serial.println(F("Arrancó en MODO DEMO (escribe 'real' para sensores)."));
}

void loop() {
  uint32_t ms = millis();

  adcActualizar();
  gpsActualizar();
  imuActualizar();
  tempActualizar();
  manejarBotones();
  leerConsola();
  notifRevisar();
#if USAR_BLE
  bleActualizar();
#endif
#if USAR_WIFI
  if (g_wifiIniciado) server.handleClient();
#endif

  if (ms - g_ultTick >= TICK_MS) {
    uint32_t dt = ms - g_ultTick;
    g_ultTick = ms;
    if (g_modoDemo) demoGenerar(); else actualizarDatosReales();
    brilloActualizar();
    revisarCaidasYFrenadas();
    motorActualizar();
    viajeActualizar(dt);
    pistaActualizar();
    tempHistorial();
    static uint8_t cuenta = 0;
    if (++cuenta >= 2) { cuenta = 0; shiftAprender(dt * 2 / 1000.0f); }
    releAplicar();
  }

  ledsActualizar();

  if (g_reiniciarEn && (int32_t)(ms - g_reiniciarEn) >= 0) ESP.restart();

  if (ms - g_ultLcd >= REFRESCO_LCD_MS) {
    g_ultLcd = ms;
    pantallaMostrar();
  }

  static uint32_t ultMin = 0;
  if (ms - ultMin > 60000) {
    ultMin = ms;
    revisarCambioDia();
    if (!datos.ignicion && !g_motorEnc && P.epochDormir == 0 && ms - g_ultIgnOn > 7200000UL)
      registrarReposoDiario(datos.vMoto);
    backupAutomatico();
  }
  if (ms - g_ultLogBat > LOG_BAT_MS) { g_ultLogBat = ms; logBaterias(); }
  if (g_nvsSucio && ms - g_ultimoGuardado > 300000) nvsGuardar();

  revisarDormir();
}
