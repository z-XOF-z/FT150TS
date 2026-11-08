#pragma once
// Historial de avisos del llavero (alertas de la moto y avisos del teléfono que reenvía la moto).
// Va en memoria RTC: sobrevive al sueño profundo, se borra al apagar con el switch.
#include <Arduino.h>
#include "protocolo_tamaxof.h"

struct Aviso { uint32_t epoch; uint8_t tipo; char txt[22]; };
const uint8_t N_AVISOS = 12;
RTC_DATA_ATTR Aviso avisos[N_AVISOS];
RTC_DATA_ATTR uint8_t nAvisos = 0, sigAviso = 0;
uint8_t avisoVer = 0;                       // el que enseña la pantalla de alerta (0 = el más nuevo)

inline const Aviso& avisoN(uint8_t k) { return avisos[(sigAviso + N_AVISOS - 1 - k) % N_AVISOS]; }

inline void agregarAviso(uint8_t tipo, const char* txt, uint32_t epoch) {
  for (uint8_t k = 0; k < min<uint8_t>(nAvisos, 3); k++) {      // el mismo hace menos de 10 min: no se repite
    const Aviso& a = avisoN(k);
    if (strcmp(a.txt, txt) == 0 && (epoch == 0 || a.epoch == 0 || epoch - a.epoch < 600)) return;
  }
  Aviso& a = avisos[sigAviso];
  a.epoch = epoch;
  a.tipo = tipo;
  strncpy(a.txt, txt, sizeof a.txt - 1);
  a.txt[sizeof a.txt - 1] = 0;
  sigAviso = (sigAviso + 1) % N_AVISOS;
  if (nAvisos < N_AVISOS) nAvisos++;
}
