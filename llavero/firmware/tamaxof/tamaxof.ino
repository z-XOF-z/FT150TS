// =====================================================================
// TamaXOF: llavero del FT150TS (ESP32-C3 SuperMini + OLED 0.96" I2C + cargador TP4056)
// - Mascota con caritas que come los km de la moto
// - Llave de la moto por ESP-NOW cifrado: al acercarte con el llavero, la moto se libera sola
// - Botón de paro (corta la chispa), bloquear, buscar la moto
// - Control de música por Bluetooth (iPhone, Android y PC)
// - Reloj, cronómetro, linterna, dado y un juego
// - Tablero (RPM, velocidad, marcha, temperatura...), alertas de la moto en grande y avisos del teléfono
//   con historial, como un reloj inteligente (solo cerca de la moto)
// - Mantener DER 10 s = prestar moto / devolver; mantener OK 10 s = bloquear / desbloquear
//
// Placa: "ESP32C3 Dev Module", USB CDC On Boot: Enabled, Partition: Minimal SPIFFS (core esp32 3.2.1).
// Antes de compilar: python -I ../../generar_secreto.py
// Botones: IZQ / OK / DER. Corto = mover/elegir, mantener OK = regresar.
// =====================================================================
#include <Arduino.h>
#include <Wire.h>
#include <Preferences.h>
#include <esp_sleep.h>
#include <driver/gpio.h>
#include <sys/time.h>
#include <time.h>
#include "oled.h"
#include "cara.h"
#include "enlace.h"
#include "musica.h"
#include "avisos.h"

#define VERSION "1.1"

// --- Pines de la ESP32-C3 SuperMini (ver llavero/pcb). En la C3 solo GPIO0-5 despiertan del sueño
// profundo y solo GPIO0-4 leen voltaje: por eso los botones, la vibración y la pila van ahí.
#define PIN_SDA 9   // también es el botón BOOT de la placa (las resistencias de la pantalla lo dejan en alto)
#define PIN_SCL 10
#define PIN_BAT 0   // divisor 220k/220k desde el + de la pila
#define PIN_IZQ 1   // botones a GND
#define PIN_OK 2
#define PIN_DER 3
#define PIN_MOV 5   // sensor de vibración SW-18010P a GND
const float DIVISOR_BAT = 2.0f;

const uint32_t VENTANA_MS = 90000;      // tras moverlo o tocarlo, contesta a la moto este tiempo
const uint32_t PIN_CADUCA_S = 1800;     // con PIN: dormido más de 30 min, lo vuelve a pedir
const uint32_t IGNORAR_MOV_S = 900;     // rodando: la vibración no lo despierta por 15 min

// ---------------------------------------------------------------------
Oled oled;
Cara cara;
Preferences prefs;

RTC_DATA_ATTR Mascota m;
RTC_DATA_ATTR time_t tDormir = 0;
RTC_DATA_ATTR bool rtcOk = false;
RTC_DATA_ATTR bool pinOk = false;
RTC_DATA_ATTR time_t ignorarMovHasta = 0;

struct Ajustes {
  uint32_t magic;
  uint8_t brillo;        // 0..2
  uint8_t pantalla;      // 0..2 -> 10/20/40 s
  bool moverPrende;      // al despertar por movimiento, enseña la cara unos segundos
  bool llaveAuto;        // contesta solo a la moto (sin PIN ni botones)
  bool pinOn;
  uint8_t pin[4];
} aj;
const uint32_t MAGIC_AJ = 0x7A3A0A02;
const uint8_t BRILLOS[] = {20, 90, 255};
const uint8_t PANTALLA_S[] = {10, 20, 40};

// --- Botones
enum Ev : uint8_t { EV_NADA, EV_IZQ, EV_OK, EV_DER, EV_IZQ_L, EV_OK_L, EV_DER_L };
struct Boton { uint8_t pin; bool abajo; uint32_t t0; bool largo; };
Boton bts[3] = {{PIN_IZQ}, {PIN_OK}, {PIN_DER}};
const uint32_t LARGO_MS = 600;
const uint32_t DOBLE_MS = 400;   // dos toques seguidos del mismo botón

volatile uint32_t tMov = 0;
volatile uint16_t nMov = 0;
uint32_t tBoton = 0, tPantalla = 0, tUltimoDibujo = 0, tPresencia = 0;
bool pantallaOn = true;
uint16_t mvBat = 0;
uint8_t pctBat = 0;

// --- Pantallas
enum Pag : uint8_t { PG_CARA, PG_RELOJ, PG_TABLERO, PG_MOTO, PG_AVISOS, PG_MUSICA, PG_MASCOTA, PG_EXTRAS, PG_AJUSTES, N_PAG };
enum Modo : uint8_t { M_PAGINA, M_LISTA, M_MUSICA, M_CRONO, M_LINTERNA, M_DADO, M_JUEGO, M_PIN, M_CONFIRMA, M_INFO, M_ALERTA };
uint8_t pag = PG_CARA, modo = M_PAGINA;

struct Lista { const char* titulo; uint8_t n, sel; const char* (*etq)(uint8_t); void (*elegir)(uint8_t); } lista;
char msg[40];
uint32_t tMsgFin = 0;
char tmp[40];

void IRAM_ATTR alMover() { tMov = millis(); nMov++; }

void mensaje(const char* s, uint32_t ms = 1800) {
  strncpy(msg, s, sizeof msg - 1);
  msg[sizeof msg - 1] = 0;
  tMsgFin = millis() + ms;
}

uint32_t ahoraS() { return (uint32_t)time(nullptr); }
bool movIgnorado() { return ignorarMovHasta && (time_t)ahoraS() < ignorarMovHasta; }
uint32_t tActivo() { return movIgnorado() ? tBoton : max(tBoton, (uint32_t)tMov); }

// ---------------------------------------------------------------------
// Guardado
void ajustesCargar() {
  prefs.begin("tamaxof", true);
  bool ok = prefs.getBytes("aj", &aj, sizeof aj) == sizeof aj && aj.magic == MAGIC_AJ;
  prefs.end();
  if (!ok) aj = {MAGIC_AJ, 1, 1, true, true, false, {1, 2, 3, 4}};
}
void ajustesGuardar() { prefs.begin("tamaxof", false); prefs.putBytes("aj", &aj, sizeof aj); prefs.end(); }

void mascotaCargar() {
  prefs.begin("tamaxof", true);
  bool ok = prefs.getBytes("m", &m, sizeof m) == sizeof m && m.magic == MAGIC_MASCOTA;
  prefs.end();
  if (!ok) { memset(&m, 0, sizeof m); m.magic = MAGIC_MASCOTA; m.comida = 70; m.animo = 70; m.energia = 80; }
}
void mascotaGuardar() { prefs.begin("tamaxof", false); prefs.putBytes("m", &m, sizeof m); prefs.end(); }

// ---------------------------------------------------------------------
// Batería (LiPo 1S)
void leerBateria() {
  uint32_t s = 0;
  for (int i = 0; i < 8; i++) s += analogReadMilliVolts(PIN_BAT);
  mvBat = s / 8 * DIVISOR_BAT;
  static const uint16_t MV[] = {3300, 3500, 3600, 3700, 3800, 3900, 4000, 4100, 4200};
  static const uint8_t PC[] = {0, 5, 15, 30, 50, 65, 80, 90, 100};
  pctBat = 0;
  if (mvBat >= MV[8]) pctBat = 100;
  else for (int i = 1; i < 9; i++)
    if (mvBat < MV[i]) { pctBat = PC[i - 1] + (PC[i] - PC[i - 1]) * (mvBat - MV[i - 1]) / (MV[i] - MV[i - 1]); break; }
}

void iconoBateria(int x, int y) {
  oled.marco(x, y, 14, 7);
  oled.rellenar(x + 14, y + 2, 1, 3);
  oled.rellenar(x + 2, y + 2, (pctBat * 10 + 50) / 100, 3);
}

// ---------------------------------------------------------------------
// Botones: corto al soltar, largo al pasar 600 ms
Ev leerBotones() {
  uint32_t t = millis();
  for (int i = 0; i < 3; i++) {
    Boton& b = bts[i];
    bool ab = digitalRead(b.pin) == LOW;
    if (ab && !b.abajo) { b.abajo = true; b.t0 = t; b.largo = false; tBoton = t; }
    else if (ab && b.abajo && !b.largo && t - b.t0 > LARGO_MS) { b.largo = true; tBoton = t; return (Ev)(EV_IZQ_L + i); }
    else if (!ab && b.abajo) {
      b.abajo = false;
      tBoton = t;
      if (!b.largo && t - b.t0 > 30) return (Ev)(EV_IZQ + i);
    }
  }
  return EV_NADA;
}
uint32_t mantenido(int i) { return bts[i].abajo ? millis() - bts[i].t0 : 0; }

// ---------------------------------------------------------------------
// Moto: lo que pide el usuario
void (*despuesPin)() = nullptr;
uint8_t pinDig[4], pinPos = 0, pinFallos = 0;
bool pinCambiando = false;
uint32_t pinBloqueoHasta = 0;

void pedirPin(void (*luego)(), bool cambiar = false) {
  despuesPin = luego;
  pinCambiando = cambiar;
  memset(pinDig, 0, 4);
  pinPos = 0;
  modo = M_PIN;
}

void ordenMoto(uint8_t o) {
  enlace::pedir(o);
  mensaje("Llamando a la moto...", 1500);
}

uint8_t ordenPendientePin = 0;
void ordenTrasPin() { ordenMoto(ordenPendientePin); modo = M_PAGINA; }

void ordenConPin(uint8_t o) {
  if (aj.pinOn && !pinOk) { ordenPendientePin = o; pedirPin(ordenTrasPin); }
  else { ordenMoto(o); modo = M_PAGINA; }
}

// Lo que sabemos de la moto (de la última respuesta o de la telemetría)
bool motoLlaveOn() { return !enlace::hayEstado || (enlace::estado.flags & EF_LLAVE); }   // sin datos: como si estuviera
bool motoBloqueada() { return enlace::hayEstado && (enlace::estado.flags & (EF_BLOQ | EF_APAGADO)); }

// Prestar moto: apaga la llave de proximidad de la moto (arranca con la pura llave). Otra vez = la regresa.
void prestarAlternar() { ordenConPin(motoLlaveOn() ? OR_PRESTAR : OR_DEVOLVER); }
// Bloquear corriente: pone el bloqueo antirrobo (solo con la moto parada). Otra vez = la libera.
void bloqueoAlternar() { ordenConPin(motoBloqueada() ? OR_PERMITIR : OR_BLOQUEAR); }

const uint8_t N_MOTO = 7;
const char* etqMoto(uint8_t i) {
  switch (i) {
    case 0:
      if (!enlace::hayEstado) return "Prestar moto: ?";
      return motoLlaveOn() ? "Prestar moto: NO" : "Prestar moto: SI";
    case 1: return motoBloqueada() ? "Desbloquear" : "Bloquear";
    case 2: return "Estoy aqui (liberar)";
    case 3: return "Apagar motor (paro)";
    case 4: return "Permitir motor";
    case 5: return "Buscar moto";
    case 6: return "Actualizar";
  }
  return "";
}
void elegirMoto(uint8_t i) {
  switch (i) {
    case 0: prestarAlternar(); break;
    case 1: bloqueoAlternar(); break;
    case 2: ordenConPin(OR_PRESENCIA); break;
    case 3: modo = M_CONFIRMA; break;
    case 4: ordenConPin(OR_PERMITIR); break;
    case 5: ordenMoto(OR_BUSCAR); modo = M_PAGINA; break;
    case 6: ordenMoto(OR_ESTADO); modo = M_PAGINA; break;
  }
}

// Historial de avisos como lista
const char* etqAvisos(uint8_t i) {
  if (i >= nAvisos) return "Borrar todo";
  const Aviso& a = avisoN(i);
  if (a.epoch > 1700000000UL) {
    time_t t = a.epoch;
    struct tm h;
    gmtime_r(&t, &h);
    snprintf(tmp, sizeof tmp, "%02d:%02d %c %s", h.tm_hour, h.tm_min, a.tipo == TA_TEL ? 'T' : '!', a.txt);
  } else snprintf(tmp, sizeof tmp, "%c %s", a.tipo == TA_TEL ? 'T' : '!', a.txt);
  tmp[21] = 0;   // cabe en la pantalla
  return tmp;
}
void elegirAvisos(uint8_t i) {
  if (i >= nAvisos) { nAvisos = 0; sigAviso = 0; mensaje("Avisos borrados"); modo = M_PAGINA; return; }
  avisoVer = i;
  modo = M_ALERTA;
}


bool estadoAuto = false;   // la consulta automática (telemetría) no enseña "Datos al dia"

// La moto contestó
void alResponderMoto() {
  if (!enlace::respuestaNueva) return;
  enlace::respuestaNueva = false;
  static bool bloqAntes = true;
  if (enlace::hayEstado && enlace::conectada(2000)) {
    const TxEstado& e = enlace::estado;
    if (e.epoch > 1700000000UL) {
      timeval tv = {(time_t)e.epoch, 0};
      settimeofday(&tv, nullptr);
      rtcOk = true;
    }
    vida::kilometros(m, e.km10);
    bool bloq = e.flags & EF_BLOQ;
    if (bloqAntes && !bloq && (e.flags & EF_IGN)) cara.momento(EX_AMOR, 3000);   // ¡me abrió!
    bloqAntes = bloq;
    if ((e.flags & EF_IGN) && !bloq) ignorarMovHasta = ahoraS() + IGNORAR_MOV_S;
  }
  if (enlace::ultimaOrden != 0xFF) {
    uint8_t o = enlace::ultimaOrden;
    enlace::ultimaOrden = 0xFF;
    if (o == OR_PRESENCIA && enlace::ultimaOk) return;   // la automática no avisa
    if (o == OR_ESTADO && estadoAuto) { estadoAuto = false; return; }
    if (!enlace::ultimaOk) { mensaje(enlace::conectada(2000) ? "La moto dijo que no" : "La moto no contesto"); cara.momento(EX_TRISTE, 2000); }
    else {
      static const char* OK_TXT[] = {"Datos al dia", "Moto liberada", "Moto bloqueada", "Motor APAGADO", "Motor permitido",
                                     "Buscando...", "Moto prestada", "Llave prox. activa"};
      mensaje(o <= OR_ULTIMA ? OK_TXT[o] : "Listo", o >= OR_PRESTAR ? 3000 : 1800);
      cara.momento(o == OR_APAGAR ? EX_SORPRESA : EX_FELIZ, 2000);
    }
  }
}

// Telemetría nueva: hora, km, estado y alertas (las de la moto salen en grande)
void alTelemetria() {
  if (!enlace::teleNueva) return;
  enlace::teleNueva = false;
  const TxTele& t = enlace::tele;
  enlace::estado.flags = t.flags;                 // así el menú sabe si está prestada o bloqueada
  if (t.epoch > 1700000000UL && !rtcOk) { timeval tv = {(time_t)t.epoch, 0}; settimeofday(&tv, nullptr); rtcOk = true; }
  static char ultima[22] = "";
  if (t.tipoAlerta == TA_NADA) { ultima[0] = 0; return; }
  if (strcmp(ultima, t.alerta) == 0) return;
  strcpy(ultima, t.alerta);
  uint8_t antes = nAvisos, sigAntes = sigAviso;
  agregarAviso(t.tipoAlerta, t.alerta, t.epoch ? t.epoch : (rtcOk ? ahoraS() : 0));
  if (nAvisos == antes && sigAviso == sigAntes) return;    // repetida
  tPantalla = millis();
  if (t.tipoAlerta == TA_MOTO && modo != M_PIN && modo != M_JUEGO && modo != M_CONFIRMA) {
    avisoVer = 0;
    modo = M_ALERTA;
    cara.momento(EX_SORPRESA, 2000);
  } else if (t.tipoAlerta == TA_TEL) {
    snprintf(msg, sizeof msg, "Tel: %s", t.alerta);
    tMsgFin = millis() + 4000;
  }
}

// Con la pantalla prendida pide la telemetría (la moto la manda 30 s por cada pedido)
void suscribirTele() {
  static uint32_t tPedido = 0;
  if (!pantallaOn || enlace::ocupado()) return;
  uint32_t cada = enlace::teleViva() || enlace::conectada(60000) ? 12000 : 30000;   // sin moto cerca, menos seguido
  if (tPedido && millis() - tPedido < cada) return;
  tPedido = millis();
  estadoAuto = true;
  enlace::pedir(OR_ESTADO);
}

bool presenciaOk() {
  return aj.llaveAuto && (!aj.pinOn || pinOk) && millis() - tActivo() < VENTANA_MS;
}

// "Aquí estoy" solo, mientras el llavero se mueve y la moto sigue bloqueada o no contesta
void presenciaAuto() {
  if (!presenciaOk() || enlace::ocupado()) return;
  bool hace = !enlace::conectada(30000) || (enlace::estado.flags & EF_BLOQ);
  if (hace && millis() - tPresencia > 4000) {
    tPresencia = millis();
    enlace::pedir(OR_PRESENCIA);
  }
}

// ---------------------------------------------------------------------
// Ajustes
const char* etqAjustes(uint8_t i) {
  static const char* B[] = {"Bajo", "Medio", "Alto"};
  switch (i) {
    case 0: snprintf(tmp, sizeof tmp, "Brillo: %s", B[aj.brillo]); break;
    case 1: snprintf(tmp, sizeof tmp, "Pantalla: %d s", PANTALLA_S[aj.pantalla]); break;
    case 2: snprintf(tmp, sizeof tmp, "Mover prende: %s", aj.moverPrende ? "Si" : "No"); break;
    case 3: snprintf(tmp, sizeof tmp, "Llave sola: %s", aj.llaveAuto ? "Si" : "No"); break;
    case 4: snprintf(tmp, sizeof tmp, "PIN: %s", aj.pinOn ? "Si" : "No"); break;
    case 5: return "Cambiar PIN";
    case 6: return "Emparejar Bluetooth";
    case 7: return "Info";
  }
  return tmp;
}
void pinCambiado() { modo = M_LISTA; }
void pinPrendido() { aj.pinOn = !aj.pinOn; ajustesGuardar(); modo = M_LISTA; }
void elegirAjustes(uint8_t i) {
  switch (i) {
    case 0: aj.brillo = (aj.brillo + 1) % 3; oled.brillo(BRILLOS[aj.brillo]); break;
    case 1: aj.pantalla = (aj.pantalla + 1) % 3; break;
    case 2: aj.moverPrende = !aj.moverPrende; break;
    case 3: aj.llaveAuto = !aj.llaveAuto; break;
    case 4: pedirPin(pinPrendido); return;   // para prender o quitar el PIN hay que saberlo
    case 5: pedirPin(nullptr, true); return;
    case 6:
      musica::iniciar("TamaXOF", pctBat);
      musica::olvidarTodos();
      mensaje("Busca TamaXOF en tu Bluetooth", 4000);
      modo = M_MUSICA;
      return;
    case 7: modo = M_INFO; return;
  }
  ajustesGuardar();
}

// Mascota y extras
const char* EST_MASCOTA[] = {"Dar de comer", "Acariciar", "Jugar (Brinca)"};
const char* etqMascota(uint8_t i) { return EST_MASCOTA[i]; }
void abrirJuego();
void elegirMascota(uint8_t i) {
  modo = M_PAGINA;
  if (i == 0) {
    if (m.tComio && m.vida - m.tComio < 7200) { mensaje("Ya comi. Rueda la moto!"); cara.momento(EX_NORMAL, 1); }
    else { m.comida = min(100.0f, m.comida + 20); m.tComio = m.vida; cara.momento(EX_FELIZ, 2500); mensaje("Nam nam!"); }
    pag = PG_CARA;
  } else if (i == 1) {
    m.animo = min(100.0f, m.animo + 3);
    cara.momento(EX_AMOR, 2500);
    pag = PG_CARA;
  } else abrirJuego();
}

const char* EST_EXTRAS[] = {"Cronometro", "Linterna", "Dado", "Brinca (juego)"};
const char* etqExtras(uint8_t i) { return EST_EXTRAS[i]; }
uint32_t cronoT0 = 0, cronoAcum = 0;
bool cronoCorre = false;
uint8_t dado = 1;
uint32_t dadoHasta = 0;
void elegirExtras(uint8_t i) {
  if (i == 0) modo = M_CRONO;
  else if (i == 1) { modo = M_LINTERNA; oled.brillo(255); }
  else if (i == 2) { modo = M_DADO; dadoHasta = millis() + 600; }
  else abrirJuego();
}

void abrirLista(const char* titulo, uint8_t n, const char* (*etq)(uint8_t), void (*elegir)(uint8_t)) {
  lista = {titulo, n, 0, etq, elegir};
  modo = M_LISTA;
}

// ---------------------------------------------------------------------
// Juego "Brinca": la carita salta los obstáculos
struct Juego { float y, vy; int ox[2], oh[2]; float vel; uint16_t pts; bool vivo; uint32_t t; } jg;
void abrirJuego() {
  jg = {0, 0, {128, 200}, {10, 14}, 1.6f, 0, true, millis()};
  modo = M_JUEGO;
}
void juegoTick(Ev ev) {
  if (!jg.vivo) {
    if (ev == EV_OK || ev == EV_IZQ || ev == EV_DER) abrirJuego();
    return;
  }
  if ((ev == EV_OK || ev == EV_IZQ || ev == EV_DER) && jg.y == 0) jg.vy = 5.2f;
  jg.vy -= 0.45f;
  jg.y = max(0.0f, jg.y + jg.vy);
  for (int i = 0; i < 2; i++) {
    jg.ox[i] -= (int)jg.vel;
    if (jg.ox[i] < -8) { jg.ox[i] = 128 + random(20, 90); jg.oh[i] = random(8, 18); jg.pts++; jg.vel = min(4.5f, jg.vel + 0.08f); }
    if (jg.ox[i] < 26 && jg.ox[i] + 6 > 14 && jg.y < jg.oh[i]) {
      jg.vivo = false;
      if (jg.pts > m.record) m.record = jg.pts;
      m.animo = min(100.0f, m.animo + 5);
      m.energia = max(0.0f, m.energia - 2);
    }
  }
}
void juegoDibujar() {
  oled.rellenar(0, 58, 128, 1);
  int y = 58 - 12 - (int)jg.y;
  oled.redondo(14, y, 12, 12, 3);
  oled.rellenar(17, y + 3, 2, 3, false);
  oled.rellenar(21, y + 3, 2, 3, false);
  for (int i = 0; i < 2; i++) oled.rellenar(jg.ox[i], 58 - jg.oh[i], 6, jg.oh[i]);
  snprintf(tmp, sizeof tmp, "%u", jg.pts);
  oled.texto(100, 2, tmp);
  if (!jg.vivo) {
    oled.rellenar(14, 14, 100, 28, false);
    oled.marco(14, 14, 100, 28);
    oled.centrado(18, "PERDISTE");
    snprintf(tmp, sizeof tmp, "Record %u", m.record);
    oled.centrado(30, tmp);
  }
}

// ---------------------------------------------------------------------
// Dibujo de cada pantalla
void puntosPagina() {
  for (int i = 0; i < N_PAG; i++) {
    int x = 64 - N_PAG * 4 + i * 8;
    if (i == pag) oled.rellenar(x, 61, 4, 3);
    else oled.px(x + 1, 62);
  }
}

void titulo(const char* t) {
  oled.rellenar(0, 0, 128, 10);
  oled.texto(2, 1, t, 1, false);
}

void dibujarReloj() {
  if (!rtcOk) {
    oled.centrado(16, "--:--", 3);
    oled.centrado(44, "Sin hora: acercate");
    oled.centrado(52, "a la moto");
    return;
  }
  time_t t = time(nullptr);
  struct tm h;
  gmtime_r(&t, &h);   // la moto ya manda la hora local
  snprintf(tmp, sizeof tmp, "%02d:%02d", h.tm_hour, h.tm_min);
  oled.centrado(12, tmp, 3);
  static const char* D[] = {"Dom", "Lun", "Mar", "Mie", "Jue", "Vie", "Sab"};
  static const char* M[] = {"Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"};
  snprintf(tmp, sizeof tmp, "%s %d %s  :%02d", D[h.tm_wday], h.tm_mday, M[h.tm_mon], h.tm_sec);
  oled.centrado(40, tmp);
  iconoBateria(2, 50);
  snprintf(tmp, sizeof tmp, "%d%%", pctBat);
  oled.texto(20, 50, tmp);
}

void dibujarMoto() {
  titulo("MOTO");
  if (enlace::ocupado()) {
    snprintf(tmp, sizeof tmp, "Buscando... canal %d", enlace::canal);
    oled.texto(2, 14, tmp);
  }
  if (!enlace::hayEstado) {
    if (!enlace::ocupado()) { oled.texto(2, 16, "Sin datos todavia"); oled.texto(2, 28, "OK: ordenes"); }
    return;
  }
  const TxEstado& e = enlace::estado;
  const char* est = (e.flags & EF_APAGADO) ? "MOTOR APAGADO" : (e.flags & EF_BLOQ) ? "BLOQUEADA" : "LIBRE";
  oled.texto(2, 24, est, 1);
  if (e.vMoto_cV >= 0) { snprintf(tmp, sizeof tmp, "Bat %.1fV", e.vMoto_cV / 100.0f); oled.texto(2, 34, tmp); }
  if (e.gas >= 0) { snprintf(tmp, sizeof tmp, "Gas %d%%", e.gas); oled.texto(70, 34, tmp); }
  snprintf(tmp, sizeof tmp, "Km %.1f", e.km10 / 10.0f);
  oled.texto(2, 43, tmp);
  snprintf(tmp, sizeof tmp, "hace %lus %ddBm", (unsigned long)((millis() - enlace::tEstado) / 1000), enlace::rssi);
  oled.texto(2, 52, tmp);
  if (!enlace::ocupado()) oled.texto(2, 13, (e.flags & EF_IGN) ? "Llave puesta" : "Llave quitada");
  oled.texto(84, 24, (e.flags & EF_LLAVE) ? "Prox ON" : "PRESTADA");
}

void dibujarTablero() {
  if (!enlace::teleViva()) {
    titulo("TABLERO");
    oled.centrado(22, enlace::ocupado() ? "Buscando la moto..." : "Acercate a la moto");
    oled.centrado(34, "(prende la pantalla");
    oled.centrado(43, "cerca de ella)");
    return;
  }
  const TxTele& t = enlace::tele;
  // Fila de arriba: temperatura, gasolina, batería
  char a[10], b[8], c[10];
  if (t.temp10 != INT16_MIN) snprintf(a, sizeof a, "%dC", t.temp10 / 10); else strcpy(a, "--C");
  if (t.gas >= 0) snprintf(b, sizeof b, "G%d%%", t.gas); else strcpy(b, "G--");
  if (t.vMoto_cV >= 0) snprintf(c, sizeof c, "%.1fV", t.vMoto_cV / 100.0f); else strcpy(c, "--V");
  oled.texto(0, 0, a);
  oled.centrado(0, b);
  oled.texto(128 - Oled::ancho(c), 0, c);
  // Velocidad grande y marcha
  snprintf(tmp, sizeof tmp, "%u", t.vel);
  oled.texto(2, 11, tmp, 3);
  oled.texto(4 + Oled::ancho(tmp, 3) + 2, 25, "km/h");
  oled.marco(98, 9, 29, 29);
  if (t.marcha == 0) strcpy(tmp, "N"); else if (t.marcha > 0) snprintf(tmp, sizeof tmp, "%d", t.marcha); else strcpy(tmp, "-");
  oled.texto(113 - Oled::ancho(tmp, 3) / 2, 13, tmp, 3);
  // RPM
  float f = t.rpm > 0 ? min(1.0f, t.rpm / 11000.0f) : 0;
  oled.barra(0, 41, 128, 9, f);
  if (t.rpm >= 0) snprintf(tmp, sizeof tmp, "%d rpm", t.rpm); else strcpy(tmp, "-- rpm");
  oled.texto(0, 54, tmp);
  // Estado o alerta
  const char* est = t.tipoAlerta == TA_MOTO ? "! ALERTA" : (t.flags & EF_APAGADO) ? "PARO" : (t.flags & EF_BLOQ) ? "BLOQ" : !(t.flags & EF_LLAVE) ? "PRESTADA" : "";
  oled.texto(128 - Oled::ancho(est), 54, est);
}

void dibujarAvisos() {
  snprintf(tmp, sizeof tmp, "AVISOS (%u)", nAvisos);
  titulo(tmp);
  if (!nAvisos) { oled.centrado(26, "Sin avisos"); oled.centrado(38, "(llegan cerca de la moto)"); return; }
  for (uint8_t k = 0; k < min<uint8_t>(nAvisos, 4); k++) {
    const Aviso& a = avisoN(k);
    oled.texto(0, 13 + k * 12, a.tipo == TA_TEL ? "T" : "!");
    oled.texto(8, 13 + k * 12, a.txt);
  }
}

// Alerta grande (triángulo con "!")
void dibujarAlerta() {
  const Aviso& a = avisoN(avisoVer);
  bool tel = a.tipo == TA_TEL;
  if (!tel) {
    oled.triangulo(64, 1, 40, 32, 88, 32);
    oled.texto(61, 9, "!", 3, false);
  } else {
    oled.redondo(44, 2, 40, 30, 5);
    oled.texto(52, 9, "TEL", 2, false);
  }
  oled.centrado(36, tel ? "AVISO DEL TELEFONO" : "FALLA EN LA MOTO");
  oled.centrado(46, a.txt);
  if (a.epoch > 1700000000UL) {
    time_t t = a.epoch;
    struct tm h;
    gmtime_r(&t, &h);
    snprintf(tmp, sizeof tmp, "%02d:%02d  OK: cerrar", h.tm_hour, h.tm_min);
  } else strcpy(tmp, "OK: cerrar");
  oled.centrado(56, tmp);
}

// Gestos de 10 s (mantener DER = prestar moto, mantener OK = bloquear)
const uint32_t GESTO_MS = 10000, GESTO_AVISO_MS = 1500;
uint32_t gestoHecho[3] = {0, 0, 0};   // t0 de la pulsación que ya se usó
bool gestosPermitidos() { return pantallaOn && modo != M_JUEGO && modo != M_PIN && modo != M_CONFIRMA; }
int gestoEnCurso() {
  if (!gestosPermitidos()) return -1;
  for (int i = 2; i >= 1; i--)
    if (bts[i].abajo && gestoHecho[i] != bts[i].t0 && mantenido(i) > GESTO_AVISO_MS) return i;
  return -1;
}
void dibujarGesto(int i) {
  bool der = i == 2;
  const char* que = der ? (motoLlaveOn() ? "PRESTAR MOTO" : "DEVOLVER LLAVE") : (motoBloqueada() ? "DESBLOQUEAR" : "BLOQUEAR MOTO");
  oled.rellenar(4, 10, 120, 44, false);
  oled.marco(4, 10, 120, 44);
  oled.centrado(14, que);
  uint32_t m = mantenido(i);
  oled.barra(12, 26, 104, 10, min(1.0f, (m - GESTO_AVISO_MS) / float(GESTO_MS - GESTO_AVISO_MS)));
  snprintf(tmp, sizeof tmp, "Sigue %lu s / suelta", (unsigned long)((GESTO_MS - min(m, GESTO_MS)) / 1000 + 1));
  oled.centrado(42, tmp);
}

void iconosMusica(int y) {
  oled.triangulo(30, y, 30, y + 10, 22, y + 5);  oled.rellenar(20, y, 2, 11);           // anterior
  oled.rellenar(58, y, 3, 11); oled.rellenar(65, y, 3, 11);                           // pausa
  oled.triangulo(96, y, 96, y + 10, 104, y + 5); oled.rellenar(105, y, 2, 11);        // siguiente
}

void dibujarMusica(bool control) {
  titulo("MUSICA");
  if (!control) {
    iconosMusica(20);
    oled.centrado(40, "OK: controlar");
    oled.centrado(50, "iPhone/Android/PC");
    return;
  }
  oled.centrado(14, musica::conectado ? "Conectado" : "Esperando Bluetooth");
  iconosMusica(28);
  oled.centrado(44, "Manten IZQ/DER: vol");
  oled.centrado(53, "Manten OK: salir");
}

void dibujarMascota() {
  titulo("TAMAXOF");
  const char* N[] = {"Comida", "Animo", "Energia"};
  float V[] = {m.comida, m.animo, m.energia};
  for (int i = 0; i < 3; i++) {
    oled.texto(2, 13 + i * 11, N[i]);
    oled.barra(50, 12 + i * 11, 76, 9, V[i] / 100);
  }
  snprintf(tmp, sizeof tmp, "%lu dias  %.0f km", (unsigned long)(m.vida / 86400), m.kmJuntos);
  oled.texto(2, 47, tmp);
}

void dibujarLista() {
  titulo(lista.titulo);
  int primero = lista.sel > 3 ? lista.sel - 3 : 0;
  for (int i = 0; i < 4 && primero + i < lista.n; i++) {
    int k = primero + i, y = 13 + i * 12;
    bool s = k == lista.sel;
    if (s) oled.rellenar(0, y - 1, 128, 11);
    oled.texto(4, y + 1, lista.etq(k), 1, !s);
  }
}

void dibujarPin() {
  titulo(pinCambiando ? "PIN NUEVO" : "PIN");
  if (millis() < pinBloqueoHasta) { oled.centrado(28, "Espera 1 minuto"); return; }
  for (int i = 0; i < 4; i++) {
    int x = 22 + i * 22;
    oled.marco(x, 20, 18, 22);
    if (i < pinPos) oled.circulo(x + 9, 31, 3);
    else if (i == pinPos) { tmp[0] = '0' + pinDig[i]; tmp[1] = 0; oled.texto(x + 4, 24, tmp, 2); }
  }
  oled.centrado(50, "IZQ - / DER + / OK");
}

void dibujarConfirma() {
  titulo("APAGAR MOTOR");
  oled.centrado(16, "Corta la chispa");
  oled.centrado(26, "Manten OK 2 seg");
  oled.barra(14, 40, 100, 10, min(1.0f, mantenido(1) / 2000.0f));
  oled.centrado(54, "IZQ/DER: cancelar");
}

void dibujarInfo() {
  titulo("INFO");
  snprintf(tmp, sizeof tmp, "TamaXOF v%s", VERSION);
  oled.texto(2, 13, tmp);
  snprintf(tmp, sizeof tmp, "%02X%02X%02X%02X%02X%02X", enlace::miMac[0], enlace::miMac[1], enlace::miMac[2], enlace::miMac[3], enlace::miMac[4], enlace::miMac[5]);
  oled.texto(2, 22, tmp);
  snprintf(tmp, sizeof tmp, "Pila %umV %d%%", mvBat, pctBat);
  oled.texto(2, 31, tmp);
  snprintf(tmp, sizeof tmp, "Canal %d  %ddBm", enlace::canal, enlace::rssi);
  oled.texto(2, 40, tmp);
  snprintf(tmp, sizeof tmp, "Record %u", m.record);
  oled.texto(2, 49, tmp);
}

void dibujarDado() {
  if (millis() < dadoHasta) dado = random(1, 7);
  int x = 44, y = 12, s = 40;
  oled.redondo(x, y, s, s, 6);
  static const uint8_t P[7][7][2] = {{}, {{2, 2}}, {{1, 1}, {3, 3}}, {{1, 1}, {2, 2}, {3, 3}}, {{1, 1}, {3, 1}, {1, 3}, {3, 3}},
                                     {{1, 1}, {3, 1}, {2, 2}, {1, 3}, {3, 3}}, {{1, 1}, {3, 1}, {1, 2}, {3, 2}, {1, 3}, {3, 3}}};
  for (int i = 0; i < dado; i++) oled.circulo(x + P[dado][i][0] * 10, y + P[dado][i][1] * 10, 3, true, false);
  oled.centrado(56, "OK: tirar");
}

void dibujarCrono() {
  titulo("CRONOMETRO");
  uint32_t ms = cronoAcum + (cronoCorre ? millis() - cronoT0 : 0);
  snprintf(tmp, sizeof tmp, "%02lu:%02lu", (unsigned long)(ms / 60000) % 100, (unsigned long)(ms / 1000) % 60);
  oled.centrado(18, tmp, 3);
  snprintf(tmp, sizeof tmp, ".%02lu", (unsigned long)(ms / 10) % 100);
  oled.centrado(43, tmp);
  oled.centrado(54, cronoCorre ? "OK: parar" : "OK: correr  DER: 0");
}

void dibujar() {
  oled.limpiar();
  switch (modo) {
    case M_LISTA: dibujarLista(); break;
    case M_MUSICA: dibujarMusica(true); break;
    case M_CRONO: dibujarCrono(); break;
    case M_LINTERNA: oled.rellenar(0, 0, 128, 64); break;
    case M_DADO: dibujarDado(); break;
    case M_JUEGO: juegoDibujar(); break;
    case M_PIN: dibujarPin(); break;
    case M_CONFIRMA: dibujarConfirma(); break;
    case M_INFO: dibujarInfo(); break;
    case M_ALERTA: dibujarAlerta(); break;
    default:
      switch (pag) {
        case PG_CARA:
          cara.dibujar(oled);
          if (pctBat < 15) iconoBateria(112, 1);
          break;
        case PG_RELOJ: dibujarReloj(); break;
        case PG_TABLERO: dibujarTablero(); break;
        case PG_MOTO: dibujarMoto(); break;
        case PG_AVISOS: dibujarAvisos(); break;
        case PG_MUSICA: dibujarMusica(false); break;
        case PG_MASCOTA: dibujarMascota(); break;
        case PG_EXTRAS: titulo("EXTRAS"); oled.centrado(28, "OK: cronometro,"); oled.centrado(38, "linterna, dado, juego"); break;
        case PG_AJUSTES: titulo("AJUSTES"); oled.centrado(30, "OK: abrir"); break;
      }
      if (pag != PG_CARA && pag != PG_TABLERO) puntosPagina();
  }
  int g = gestoEnCurso();
  if (g >= 0) dibujarGesto(g);
  if ((int32_t)(millis() - tMsgFin) < 0 && modo != M_LINTERNA) {
    int w = min(124, Oled::ancho(msg) + 8);
    int x = (128 - w) / 2;
    oled.rellenar(x, 24, w, 15, false);
    oled.marco(x, 24, w, 15);
    oled.texto(x + 4, 28, msg);
  }
  oled.mostrar();
}

// ---------------------------------------------------------------------
// Qué hace cada botón
void manejar(Ev ev) {
  if (ev == EV_NADA) return;
  if (!pantallaOn) return;   // el primer toque solo prende la pantalla
  switch (modo) {
    case M_PAGINA: {
      // Doble toque: DER DER = Tablero de la moto, IZQ IZQ = la carita (el primer toque ya movió una página)
      static Ev evAntes = EV_NADA;
      static uint32_t tAntes = 0;
      bool doble = (ev == EV_IZQ || ev == EV_DER) && ev == evAntes && millis() - tAntes < DOBLE_MS;
      evAntes = doble ? EV_NADA : ev;
      tAntes = millis();
      if (doble) pag = ev == EV_DER ? PG_TABLERO : PG_CARA;
      else if (ev == EV_IZQ) pag = (pag + N_PAG - 1) % N_PAG;
      else if (ev == EV_DER) pag = (pag + 1) % N_PAG;
      else if (ev == EV_OK_L) pag = PG_CARA;
      else if (ev == EV_OK) {
        switch (pag) {
          case PG_CARA: m.animo = min(100.0f, m.animo + 1); cara.momento(EX_FELIZ, 1500); break;
          case PG_RELOJ: pag = PG_CARA; break;
          case PG_TABLERO: pag = PG_AVISOS; break;
          case PG_MOTO: abrirLista("MOTO", N_MOTO, etqMoto, elegirMoto); break;
          case PG_AVISOS: abrirLista("AVISOS", nAvisos + 1, etqAvisos, elegirAvisos); break;
          case PG_MUSICA: musica::iniciar("TamaXOF", pctBat); modo = M_MUSICA; break;
          case PG_MASCOTA: abrirLista("MASCOTA", 3, etqMascota, elegirMascota); break;
          case PG_EXTRAS: abrirLista("EXTRAS", 4, etqExtras, elegirExtras); break;
          case PG_AJUSTES: abrirLista("AJUSTES", 8, etqAjustes, elegirAjustes); break;
        }
      }
      break;
    }
    case M_LISTA:
      if (ev == EV_IZQ) lista.sel = (lista.sel + lista.n - 1) % lista.n;
      else if (ev == EV_DER) lista.sel = (lista.sel + 1) % lista.n;
      else if (ev == EV_OK) lista.elegir(lista.sel);
      else if (ev == EV_OK_L) modo = M_PAGINA;
      break;
    case M_MUSICA:
      if (ev == EV_IZQ) musica::tecla(musica::ANTERIOR);
      else if (ev == EV_DER) musica::tecla(musica::SIGUIENTE);
      else if (ev == EV_OK) musica::tecla(musica::PAUSA);
      else if (ev == EV_IZQ_L) musica::tecla(musica::VOL_MENOS);
      else if (ev == EV_DER_L) musica::tecla(musica::VOL_MAS);
      else if (ev == EV_OK_L) modo = M_PAGINA;
      break;
    case M_CRONO:
      if (ev == EV_OK) {
        if (cronoCorre) cronoAcum += millis() - cronoT0;
        else cronoT0 = millis();
        cronoCorre = !cronoCorre;
      } else if (ev == EV_DER && !cronoCorre) cronoAcum = 0;
      else if (ev == EV_OK_L) modo = M_PAGINA;
      break;
    case M_LINTERNA:
      modo = M_PAGINA;
      oled.brillo(BRILLOS[aj.brillo]);
      break;
    case M_DADO:
      if (ev == EV_OK) dadoHasta = millis() + 600;
      else if (ev == EV_OK_L) modo = M_PAGINA;
      break;
    case M_JUEGO:
      if (ev == EV_OK_L) modo = M_PAGINA;
      else juegoTick(ev);
      break;
    case M_INFO:
      modo = M_LISTA;
      break;
    case M_ALERTA:
      if (ev == EV_IZQ && avisoVer + 1 < nAvisos) avisoVer++;          // IZQ/DER: aviso anterior/siguiente
      else if (ev == EV_DER && avisoVer > 0) avisoVer--;
      else if (ev == EV_OK || ev == EV_OK_L) modo = M_PAGINA;
      break;
    case M_CONFIRMA:
      if (ev == EV_IZQ || ev == EV_DER || ev == EV_OK) modo = M_PAGINA;
      break;
    case M_PIN:
      if (millis() < pinBloqueoHasta) { if (ev == EV_OK_L) modo = M_PAGINA; break; }
      if (ev == EV_IZQ) pinDig[pinPos] = (pinDig[pinPos] + 9) % 10;
      else if (ev == EV_DER) pinDig[pinPos] = (pinDig[pinPos] + 1) % 10;
      else if (ev == EV_OK_L) modo = M_PAGINA;
      else if (ev == EV_OK && ++pinPos >= 4) {
        if (pinCambiando) {
          memcpy(aj.pin, pinDig, 4);
          ajustesGuardar();
          pinOk = true;
          mensaje("PIN guardado");
          modo = M_LISTA;
        } else if (memcmp(pinDig, aj.pin, 4) == 0) {
          pinOk = true;
          pinFallos = 0;
          if (despuesPin) despuesPin(); else modo = M_PAGINA;
        } else {
          mensaje("PIN incorrecto");
          cara.momento(EX_ENOJADO, 2000);
          if (++pinFallos >= 3) { pinBloqueoHasta = millis() + 60000; pinFallos = 0; }
          memset(pinDig, 0, 4);
          pinPos = 0;
        }
      }
      break;
  }
}

// ---------------------------------------------------------------------
// Sueño profundo: despierta con cualquier botón o al moverlo (salvo rodando)
const uint64_t MASCARA_BOTONES = (1ULL << PIN_IZQ) | (1ULL << PIN_OK) | (1ULL << PIN_DER);
void configurarDespertar(bool conMovimiento) {
  uint64_t mascara = MASCARA_BOTONES;
  if (conMovimiento) mascara |= 1ULL << PIN_MOV;
  const uint8_t pines[] = {PIN_IZQ, PIN_OK, PIN_DER, PIN_MOV};
  for (uint8_t p : pines) {
    gpio_pullup_en((gpio_num_t)p);
    gpio_pulldown_dis((gpio_num_t)p);
  }
  esp_deep_sleep_enable_gpio_wakeup(mascara, ESP_GPIO_WAKEUP_GPIO_LOW);
  if (!conMovimiento && ignorarMovHasta > (time_t)ahoraS())
    esp_sleep_enable_timer_wakeup((uint64_t)(ignorarMovHasta - ahoraS()) * 1000000ULL);
}

void dormir() {
  // Si el botón sigue apretado o el sensor sigue cerrado, despertaría luego luego
  while (digitalRead(PIN_IZQ) == LOW || digitalRead(PIN_OK) == LOW || digitalRead(PIN_DER) == LOW) delay(10);
  bool conMov = !movIgnorado();
  if (conMov && digitalRead(PIN_MOV) == LOW) { delay(300); if (digitalRead(PIN_MOV) == LOW) conMov = false; }
  tDormir = ahoraS();
  mascotaGuardar();
  oled.dormir();
  enlace::parar();
  configurarDespertar(conMov);
  esp_deep_sleep_start();
}

// ---------------------------------------------------------------------
void setup() {
  esp_sleep_wakeup_cause_t causa = esp_sleep_get_wakeup_cause();
  const uint8_t pines[] = {PIN_IZQ, PIN_OK, PIN_DER, PIN_MOV};
  for (uint8_t p : pines) pinMode(p, INPUT_PULLUP);

  // Despertó solo para volver a escuchar el movimiento (se acabó el rato de rodar): a dormir otra vez
  if (causa == ESP_SLEEP_WAKEUP_TIMER) {
    ignorarMovHasta = 0;
    configurarDespertar(true);
    esp_deep_sleep_start();
  }

  bool porBoton = false;
  if (causa == ESP_SLEEP_WAKEUP_GPIO) porBoton = esp_sleep_get_gpio_wakeup_status() & MASCARA_BOTONES;
  bool encendido = causa == ESP_SLEEP_WAKEUP_UNDEFINED;

  ajustesCargar();
  if (encendido) { mascotaCargar(); pinOk = false; }
  else if (tDormir) {
    uint32_t dormido = ahoraS() - tDormir;
    vida::pasar(m, dormido, true);
    if (dormido > PIN_CADUCA_S) pinOk = false;
  }
  randomSeed(esp_random());

  Wire.begin(PIN_SDA, PIN_SCL, 400000);
  oled.begin();
  oled.brillo(BRILLOS[aj.brillo]);
  analogReadResolution(12);
  leerBateria();

  attachInterrupt(PIN_MOV, alMover, FALLING);
  enlace::presenciaPermitida = presenciaOk;
  enlace::iniciar();

  uint32_t t = millis();
  tBoton = (porBoton || encendido) ? t : 0;
  tMov = porBoton ? 0 : t;
  pantallaOn = porBoton || encendido || aj.moverPrende;
  tPantalla = t;
  if (!pantallaOn) oled.dormir();

  if (encendido) {
    oled.limpiar();
    oled.centrado(18, "TamaXOF", 2);
    oled.centrado(42, "FT150TS");
    oled.mostrar();
    delay(1200);
    cara.momento(EX_SORPRESA, 1500);
  } else if (porBoton) cara.momento(EX_SORPRESA, 800);
}

void loop() {
  uint32_t t = millis();
  Ev ev = leerBotones();
  if (ev != EV_NADA && !pantallaOn) {        // prende la pantalla
    pantallaOn = true;
    oled.despertar();
    tPantalla = t;
    ev = EV_NADA;
  }
  if (ev != EV_NADA) tPantalla = t;
  manejar(ev);
  if (modo == M_CONFIRMA && mantenido(1) > 2000) {
    bts[1].largo = true;
    modo = M_PAGINA;
    pag = PG_MOTO;
    ordenConPin(OR_APAGAR);
  }
  if (modo == M_JUEGO) juegoTick(EV_NADA);

  // Gestos: mantener DER 10 s = prestar moto / devolver; mantener OK 10 s = bloquear / desbloquear
  if (gestosPermitidos())
    for (int i = 1; i <= 2; i++)
      if (bts[i].abajo && gestoHecho[i] != bts[i].t0 && mantenido(i) > GESTO_MS) {
        gestoHecho[i] = bts[i].t0;
        bts[i].largo = true;
        if (i == 2) prestarAlternar(); else bloqueoAlternar();
      }

  // ¿Lo están sacudiendo con la pantalla prendida? se marea
  static uint16_t movAntes = 0;
  static uint32_t tVentanaMov = 0;
  if (t - tVentanaMov > 1500) {
    if ((uint16_t)(nMov - movAntes) > 15 && pantallaOn && !movIgnorado()) { cara.momento(EX_MAREADO, 2500); m.animo = max(0.0f, m.animo - 1); }
    movAntes = nMov;
    tVentanaMov = t;
  }

  enlace::tick();
  alResponderMoto();
  presenciaAuto();
  alTelemetria();
  suscribirTele();

  // Cada minuto: batería, vida y la cara que toca
  static uint32_t tMin = 0;
  if (t - tMin > 60000 || tMin == 0) {
    uint32_t seg = tMin ? (t - tMin) / 1000 : 0;
    tMin = t;
    leerBateria();
    musica::bateria(pctBat);
    bool noche = false;
    if (rtcOk) { time_t a = time(nullptr); struct tm h; gmtime_r(&a, &h); noche = h.tm_hour >= 23 || h.tm_hour < 7; }
    vida::pasar(m, seg, false);
    cara.expr = vida::animo(m, noche && t - tBoton > 15000, pctBat);
  }

  // Apagar la pantalla
  uint32_t limite = (modo == M_MUSICA || modo == M_LINTERNA || modo == M_JUEGO || (modo == M_CRONO && cronoCorre) ||
                     (modo == M_PAGINA && pag == PG_TABLERO))
                        ? 120000 : (tBoton ? PANTALLA_S[aj.pantalla] * 1000UL : 5000UL);
  if (pantallaOn && t - tPantalla > limite) {
    pantallaOn = false;
    if (modo == M_LINTERNA) oled.brillo(BRILLOS[aj.brillo]);
    modo = M_PAGINA;
    pag = PG_CARA;
    oled.dormir();
  }
  if (pantallaOn && t - tUltimoDibujo > 33) { tUltimoDibujo = t; dibujar(); }

  // Dormir del todo
  bool musicaViva = musica::conectado && modo == M_MUSICA;
  if (!pantallaOn && !enlace::ocupado() && !musicaViva && t - tActivo() > VENTANA_MS) dormir();
  delay(2);
}
