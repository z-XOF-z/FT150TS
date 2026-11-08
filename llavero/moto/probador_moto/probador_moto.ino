// Probador del llavero TamaXOF: cualquier ESP32-S3 hace de "moto falsa" por el monitor serie (115200).
// Sirve para probar el llavero en la mesa antes de tocar el firmware del tablero.
//   i  = girar/quitar la llave        b = bloquear/desbloquear (llave de proximidad)
//   c N = cambiar al canal N (1-13)   r N = liberar solo con señal mayor a N dBm (ej. r -70)
//   e  = ver estado               a TEXTO = alerta de la moto     t TEXTO = aviso del teléfono   a = quitar
#include <WiFi.h>
#include <esp_wifi.h>
#include "tamaxof_moto.h"

bool ign = false, bloq = true, apagado = false, llaveProx = true;
String alerta = "";
bool alertaTel = false;
float km = 1234.5f;

bool txMotoEjecutar(uint8_t orden) {
  switch (orden) {
    case OR_PRESENCIA: if (ign) bloq = false; Serial.println("> Llavero: AQUI ESTOY (firma y tiempo OK)"); return true;
    case OR_BLOQUEAR: bloq = true; Serial.println("> Llavero: BLOQUEAR"); return true;
    case OR_APAGAR: apagado = true; Serial.println("> Llavero: APAGAR MOTOR (corte de chispa)"); return true;
    case OR_PERMITIR: apagado = false; Serial.println("> Llavero: PERMITIR MOTOR"); return true;
    case OR_BUSCAR: Serial.println("> Llavero: BUSCAR MOTO (parpadear)"); return true;
    case OR_PRESTAR: llaveProx = false; bloq = false; Serial.println("> Llavero: PRESTAR MOTO (llave de proximidad OFF)"); return true;
    case OR_DEVOLVER: llaveProx = true; Serial.println("> Llavero: DEVOLVER (llave de proximidad ON)"); return true;
  }
  return false;
}

void txMotoLlenar(TxEstado& e) {
  e.flags = (bloq ? EF_BLOQ : 0) | (apagado ? EF_APAGADO : 0) | (ign ? EF_IGN : 0) | (llaveProx ? EF_LLAVE : 0);
  e.vMoto_cV = 1265;
  e.gas = 62;
  e.vel = 0;
  e.km10 = (uint32_t)(km * 10);
  e.epoch = 1794009600UL + millis() / 1000;   // hora fija de prueba (7 nov 2026 00:00)
}

// Datos de mentira que se mueven, para ver la pantalla "Tablero" del llavero
void txMotoTele(TxTele& t) {
  TxEstado e = {};
  txMotoLlenar(e);
  t.flags = e.flags; t.gas = e.gas; t.vMoto_cV = e.vMoto_cV; t.km10 = e.km10; t.epoch = e.epoch;
  bool anda = ign && !bloq && !apagado;
  float f = (millis() % 8000) / 8000.0f;
  t.rpm = anda ? 1500 + (int)(7000 * f) : (ign ? 0 : -1);
  t.vel = anda ? (uint8_t)(10 + 70 * f) : 0;
  t.marcha = anda ? 1 + (int)(f * 5) : 0;
  t.temp10 = 785;
  if (alerta.length()) { t.tipoAlerta = alertaTel ? TA_TEL : TA_MOTO; strncpy(t.alerta, alerta.c_str(), sizeof t.alerta - 1); }
}

void setup() {
  Serial.begin(115200);
  delay(500);
  WiFi.mode(WIFI_STA);
  esp_wifi_set_channel(1, WIFI_SECOND_CHAN_NONE);
  Serial.println(txMotoIniciar() ? "Moto falsa lista (canal 1). i/b/c N/r N/e" : "ESP-NOW no arranco");
}

void loop() {
  txMotoTick();
  static uint32_t tKm = 0;
  if (ign && !bloq && !apagado && millis() - tKm > 5000) { tKm = millis(); km += 0.1f; }
  if (Serial.available()) {
    String s = Serial.readStringUntil('\n');
    s.trim();
    if (s == "i") { ign = !ign; if (ign) bloq = true; Serial.printf("Llave %s, moto %s\n", ign ? "PUESTA" : "QUITADA", bloq ? "bloqueada" : "libre"); }
    else if (s == "b") { bloq = !bloq; Serial.printf("Moto %s\n", bloq ? "bloqueada" : "libre"); }
    else if (s.startsWith("c ")) { int c = s.substring(2).toInt(); esp_wifi_set_channel(c, WIFI_SECOND_CHAN_NONE); Serial.printf("Canal %d\n", c); }
    else if (s.startsWith("r ")) { txmoto::rssiMin = s.substring(2).toInt(); Serial.printf("RSSI minimo %d\n", txmoto::rssiMin); }
    else if (s == "a" || s.startsWith("a ") || s.startsWith("t ")) {
      alertaTel = s.startsWith("t ");
      alerta = s.length() > 2 ? s.substring(2) : "";
      Serial.printf("Alerta: '%s' (%s)\n", alerta.c_str(), alertaTel ? "telefono" : "moto");
    }
    else if (s == "e") Serial.printf("ign=%d bloq=%d apagado=%d llaveroCerca=%d rssi=%d rechazados=%lu tarde=%lu\n", ign, bloq, apagado,
                                     llaveroCerca(), txmoto::rssi, (unsigned long)txmoto::rechazados, (unsigned long)txmoto::tarde);
  }
}
