#pragma once
// Lado MOTO del llavero TamaXOF (ESP-NOW). Va en el firmware del tablero (y en el probador).
// Uso:
//   txMotoIniciar();          // en setup, después de configurar el WiFi (si está apagado, lo pone en STA sin conectarse)
//   txMotoTick();             // en loop
//   llaveroCerca()            // true si el llavero se presentó con la clave hace menos de 15 s
// Y el firmware tiene que definir:
//   bool txMotoEjecutar(uint8_t orden);   // hace la orden (OR_*), true si se pudo
//   void txMotoLlenar(TxEstado& e);       // flags EF_*, voltaje, gasolina, km, hora local
//   void txMotoTele(TxTele& t);           // RPM, velocidad, marcha, temp, alerta... (sin cont ni seq)
//
// Todo lo pesado (revisar la firma, sacar el reto) pasa en la llegada del mensaje, para medir bien
// cuánto tarda el llavero en contestar. Las órdenes se hacen en txMotoTick(), en el loop.
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <esp_mac.h>
#include <esp_timer.h>
#include "protocolo_tamaxof.h"
#include "secreto_tamaxof.h"

bool txMotoEjecutar(uint8_t orden);
void txMotoLlenar(TxEstado& e);
void txMotoTele(TxTele& t);

namespace txmoto {

TxCripto cripto;
uint8_t miMac[6];
wifi_interface_t interfaz = WIFI_IF_STA;
bool listo = false;
portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;

struct Reto { bool vivo; uint8_t mac[6]; uint8_t orden; uint32_t cont; uint8_t nonce[16]; int64_t tUs; };
struct Pend { bool hay; uint8_t mac[6]; uint8_t orden; uint32_t cont; int8_t rssi; };
struct Sub { uint8_t mac[6]; uint32_t cont; uint32_t t; };   // llavero que pidió la telemetría
Reto reto = {};
Pend pend = {};
Sub sub = {};
uint32_t seqTele = 0, tTele = 0;
const uint32_t SUB_MS = 30000, TELE_CADA_MS = 250;
volatile uint32_t tPresencia = 0;
volatile int8_t rssi = 0;
volatile uint32_t rechazados = 0, tarde = 0;
int8_t rssiMin = -127;   // para liberar solo de cerca (p. ej. -70). De fábrica: cualquier distancia

void agregarPar(const uint8_t* mac) {
  if (esp_now_is_peer_exist(mac)) return;
  esp_now_peer_info_t p = {};
  memcpy(p.peer_addr, mac, 6);
  p.channel = 0;
  p.encrypt = false;   // cifra el protocolo (AES + HMAC)
  p.ifidx = interfaz;
  esp_now_add_peer(&p);
}

void mandar(const uint8_t* dest, uint8_t tipo, const void* cuerpo, size_t n) {
  uint8_t m[TX_MAX];
  size_t largo = cripto.armar(tipo, cuerpo, n, miMac, m);
  esp_now_send(dest, m, largo);
}

void alRecibir(const esp_now_recv_info_t* info, const uint8_t* d, int len) {
  int64_t llegada = esp_timer_get_time();
  uint8_t tipo, c[TX_CUERPO_MAX];
  int n = cripto.abrir(d, len, info->src_addr, tipo, c, sizeof c);
  if (n < 0) { rechazados++; return; }
  int8_t r = info->rx_ctrl ? info->rx_ctrl->rssi : 0;
  if (tipo == TX_HOLA && n == sizeof(TxHola)) {
    TxHola h;
    memcpy(&h, c, sizeof h);
    if (h.orden > OR_ULTIMA) return;
    TxReto rt;
    rt.orden = h.orden;
    rt.cont = h.cont;
    esp_fill_random(rt.nonce, sizeof rt.nonce);
    agregarPar(info->src_addr);
    portENTER_CRITICAL(&mux);
    reto.vivo = true;
    memcpy(reto.mac, info->src_addr, 6);
    reto.orden = h.orden;
    reto.cont = h.cont;
    memcpy(reto.nonce, rt.nonce, 16);
    reto.tUs = esp_timer_get_time();
    portEXIT_CRITICAL(&mux);
    mandar(info->src_addr, TX_RETO, &rt, sizeof rt);
  } else if (tipo == TX_RESP && n == sizeof(TxResp)) {
    TxResp s;
    memcpy(&s, c, sizeof s);
    bool ok, aTiempo;
    portENTER_CRITICAL(&mux);
    ok = reto.vivo && memcmp(reto.mac, info->src_addr, 6) == 0 && s.orden == reto.orden && memcmp(s.nonce, reto.nonce, 16) == 0;
    aTiempo = llegada - reto.tUs < (int64_t)TX_RTT_MAX_US;
    if (ok && aTiempo) {
      reto.vivo = false;   // un reto sirve una sola vez
      pend.hay = true;
      memcpy(pend.mac, reto.mac, 6);
      pend.orden = reto.orden;
      pend.cont = reto.cont;
      pend.rssi = r;
      if (reto.orden == OR_ESTADO) {          // pidió datos: le mandamos la telemetría un rato
        memcpy(sub.mac, reto.mac, 6);
        sub.cont = reto.cont;
        sub.t = millis();
      }
    }
    portEXIT_CRITICAL(&mux);
    if (!ok) rechazados++;
    else if (!aTiempo) tarde++;
    else rssi = r;
  }
}

void atender(const Pend& p);
}  // namespace txmoto

bool txMotoIniciar() {
  using namespace txmoto;
  wifi_mode_t modo = WiFi.getMode();
  if (modo == WIFI_OFF) { WiFi.mode(WIFI_STA); modo = WIFI_STA; }
  interfaz = modo == WIFI_AP ? WIFI_IF_AP : WIFI_IF_STA;
  esp_read_mac(miMac, interfaz == WIFI_IF_AP ? ESP_MAC_WIFI_SOFTAP : ESP_MAC_WIFI_STA);
  cripto.iniciar(TX_SECRETO);
  if (esp_now_init() != ESP_OK) return false;
  esp_now_register_recv_cb(alRecibir);
  listo = true;
  return true;
}

void txMotoParar() {
  if (!txmoto::listo) return;
  esp_now_deinit();
  txmoto::listo = false;
}

bool llaveroCerca(uint32_t ms = 15000) { return txmoto::tPresencia && millis() - txmoto::tPresencia < ms; }

void txMotoTick() {
  using namespace txmoto;
  if (!listo) return;
  Pend p;
  bool hay = false;
  portENTER_CRITICAL(&mux);
  if (pend.hay) { p = pend; pend.hay = false; hay = true; }
  portEXIT_CRITICAL(&mux);
  if (hay) atender(p);
  // Telemetría al llavero suscrito
  uint32_t ms = millis();
  Sub s;
  portENTER_CRITICAL(&mux);
  s = sub;
  portEXIT_CRITICAL(&mux);
  if (s.t && ms - s.t < SUB_MS && ms - tTele >= TELE_CADA_MS) {
    tTele = ms;
    TxTele t = {};
    t.rpm = -1; t.marcha = -1; t.temp10 = INT16_MIN; t.gas = -1; t.vMoto_cV = -1;
    txMotoTele(t);
    t.cont = s.cont;
    t.seq = ++seqTele;
    mandar(s.mac, TX_TELE, &t, sizeof t);
  }
}

void txmoto::atender(const Pend& p) {
  bool ok = true;
  if (p.orden == OR_PRESENCIA) {
    ok = p.rssi >= rssiMin;
    if (ok) tPresencia = millis();
  }
  if (ok && p.orden != OR_ESTADO) ok = txMotoEjecutar(p.orden);
  TxEstado e = {};
  e.vMoto_cV = -1;
  e.gas = -1;
  txMotoLlenar(e);
  e.orden = p.orden;
  e.cont = p.cont;
  if (ok) e.flags |= EF_OK;
  mandar(p.mac, TX_ESTADO, &e, sizeof e);
}
