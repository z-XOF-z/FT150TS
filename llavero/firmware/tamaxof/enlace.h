#pragma once
// Enlace del llavero con la moto por ESP-NOW (lado llavero).
// La moto puede estar en cualquier canal (si se conecta al WiFi de casa, usa el del módem),
// así que el llavero recorre los canales 1-13 hasta que la moto contesta y se queda en ese.
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <esp_mac.h>
#include "protocolo_tamaxof.h"
#include "secreto_tamaxof.h"

namespace enlace {

RTC_DATA_ATTR uint8_t canal = 1;      // sobrevive al sueño profundo
RTC_DATA_ATTR uint32_t cont = 0;

TxCripto cripto;
uint8_t miMac[6], macMoto[6];
bool macMotoOk = false;
const uint8_t DIFUSION[6] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

// Lo que pidió la pantalla
uint8_t ordenPend = 0xFF;             // 0xFF = nada
uint32_t tOrden = 0, tEnvio = 0;
uint8_t intentosCanal = 0, canalesProbados = 0;
uint32_t contEnviado = 0;

// Lo último que dijo la moto
volatile bool hayEstado = false;
TxEstado estado;
uint32_t tEstado = 0;                 // millis de la última respuesta (0 = nunca)
int8_t rssi = 0;
uint8_t ultimaOrden = 0xFF;
bool ultimaOk = false;
volatile bool respuestaNueva = false;

// La presencia solo se contesta si el llavero se movió o se tocó hace poco: así, si alguien
// pone un repetidor junto a tu bolsa mientras está quieta, no le contesta.
bool (*presenciaPermitida)() = nullptr;

portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;
TxEstado estadoRx;
volatile bool estadoRxNuevo = false;

// Telemetría (pantalla Tablero): llega sola 4 veces por segundo durante 30 s después de un OR_ESTADO
uint32_t contSub = 0;                 // cont del último OR_ESTADO que la moto contestó
TxTele tele, teleRx;
uint32_t tTele = 0, seqTele = 0;      // millis de la última (0 = nunca)
volatile bool teleRxNueva = false;
volatile bool teleNueva = false;      // para la pantalla: hay telemetría nueva
bool teleViva(uint32_t ms = 3000) { return tTele && millis() - tTele < ms; }

void agregarPar(const uint8_t* mac) {
  if (esp_now_is_peer_exist(mac)) return;
  esp_now_peer_info_t p = {};
  memcpy(p.peer_addr, mac, 6);
  p.channel = 0;          // el canal actual
  p.encrypt = false;      // el cifrado lo hacemos nosotros (AES + HMAC)
  p.ifidx = WIFI_IF_STA;
  esp_now_add_peer(&p);
}

void mandar(const uint8_t* dest, uint8_t tipo, const void* cuerpo, size_t n) {
  uint8_t m[TX_MAX];
  size_t largo = cripto.armar(tipo, cuerpo, n, miMac, m);
  esp_now_send(dest, m, largo);
}

void alRecibir(const esp_now_recv_info_t* info, const uint8_t* d, int len) {
  uint8_t tipo, cuerpo[TX_CUERPO_MAX];
  int n = cripto.abrir(d, len, info->src_addr, tipo, cuerpo, sizeof cuerpo);
  if (n < 0) return;
  if (tipo == TX_RETO && n == sizeof(TxReto)) {
    TxReto r;
    memcpy(&r, cuerpo, sizeof r);
    bool mia = r.orden == ordenPend && r.cont == contEnviado;
    bool pres = r.orden == OR_PRESENCIA && presenciaPermitida && presenciaPermitida();
    if (!mia && !pres && r.orden != OR_ESTADO) return;
    memcpy(macMoto, info->src_addr, 6);
    macMotoOk = true;
    agregarPar(macMoto);
    TxResp s;
    s.orden = r.orden;
    memcpy(s.nonce, r.nonce, 16);
    mandar(macMoto, TX_RESP, &s, sizeof s);   // de inmediato: la moto mide cuánto tardamos
    rssi = info->rx_ctrl ? info->rx_ctrl->rssi : 0;
  } else if (tipo == TX_ESTADO && n == sizeof(TxEstado)) {
    portENTER_CRITICAL(&mux);
    memcpy(&estadoRx, cuerpo, sizeof estadoRx);
    estadoRxNuevo = true;
    portEXIT_CRITICAL(&mux);
    rssi = info->rx_ctrl ? info->rx_ctrl->rssi : 0;
  } else if (tipo == TX_TELE && n == sizeof(TxTele)) {
    portENTER_CRITICAL(&mux);
    memcpy(&teleRx, cuerpo, sizeof teleRx);
    teleRxNueva = true;
    portEXIT_CRITICAL(&mux);
    rssi = info->rx_ctrl ? info->rx_ctrl->rssi : 0;
  }
}

void ponerCanal(uint8_t c) {
  canal = c;
  esp_wifi_set_channel(canal, WIFI_SECOND_CHAN_NONE);
}

bool iniciar() {
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  esp_wifi_set_ps(WIFI_PS_NONE);
  WiFi.setTxPower(WIFI_POWER_19_5dBm);   // la moto se queda a unos 30 m
  esp_read_mac(miMac, ESP_MAC_WIFI_STA);
  cripto.iniciar(TX_SECRETO);
  if (esp_now_init() != ESP_OK) return false;
  esp_now_register_recv_cb(alRecibir);
  agregarPar(DIFUSION);
  ponerCanal(canal ? canal : 1);
  return true;
}

void parar() {
  esp_now_deinit();
  WiFi.mode(WIFI_OFF);
}

// Pide algo a la moto (la respuesta llega sola y se ve en estado/ultimaOk)
void pedir(uint8_t orden) {
  ordenPend = orden;
  tOrden = millis();
  tEnvio = 0;
  intentosCanal = 0;
  canalesProbados = 0;
}

bool ocupado() { return ordenPend != 0xFF; }
bool conectada(uint32_t ms = 20000) { return tEstado && millis() - tEstado < ms; }

void tick() {
  if (estadoRxNuevo) {
    TxEstado e;
    portENTER_CRITICAL(&mux);
    memcpy(&e, &estadoRx, sizeof e);
    estadoRxNuevo = false;
    portEXIT_CRITICAL(&mux);
    if (e.cont == contEnviado) {   // lo viejo o repetido no cuenta
      estado = e;
      tEstado = millis();
      hayEstado = true;
      respuestaNueva = true;
      if (e.orden == OR_ESTADO) { contSub = e.cont; seqTele = 0; }
      if (e.orden == ordenPend) {
        ultimaOrden = e.orden;
        ultimaOk = e.flags & EF_OK;
        ordenPend = 0xFF;
      }
    }
  }
  if (teleRxNueva) {
    TxTele t;
    portENTER_CRITICAL(&mux);
    memcpy(&t, &teleRx, sizeof t);
    teleRxNueva = false;
    portEXIT_CRITICAL(&mux);
    if (contSub && t.cont == contSub && t.seq > seqTele) {   // de esta suscripción y más nueva
      seqTele = t.seq;
      t.alerta[sizeof t.alerta - 1] = 0;
      tele = t;
      tTele = millis();
      teleNueva = true;
    }
  }
  if (ordenPend == 0xFF) return;
  uint32_t ahora = millis();
  if (ahora - tOrden > 8000) {             // nadie contestó en ningún canal
    ultimaOrden = ordenPend;
    ultimaOk = false;
    ordenPend = 0xFF;
    respuestaNueva = true;
    return;
  }
  if (tEnvio && ahora - tEnvio < 120) return;
  if (tEnvio && ++intentosCanal >= 2) {    // dos intentos sin respuesta: siguiente canal
    intentosCanal = 0;
    ponerCanal(canal % 13 + 1);
    canalesProbados++;
  }
  TxHola h;
  h.orden = ordenPend;
  h.cont = contEnviado = ++cont;
  mandar(macMotoOk && canalesProbados == 0 ? macMoto : DIFUSION, TX_HOLA, &h, sizeof h);
  tEnvio = ahora;
}

}  // namespace enlace
