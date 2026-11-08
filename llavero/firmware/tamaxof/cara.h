#pragma once
// La carita de TamaXOF y su "vida" (comida, ánimo, energía).
// Come kilómetros: cada km que rueda la moto con el llavero cerca lo alimenta.
#include "oled.h"

enum Expr : uint8_t { EX_NORMAL, EX_FELIZ, EX_DORMIDO, EX_ENOJADO, EX_TRISTE, EX_MAREADO, EX_SORPRESA, EX_AMOR };

struct Mascota {
  uint32_t magic;
  float comida, animo, energia;  // 0..100
  uint32_t vida;                 // segundos vividos (no depende de la hora)
  uint32_t km10Visto;            // último odómetro de la moto que vio (x10)
  float kmJuntos;
  uint32_t tComio, tJugo;        // en "vida"
  uint16_t record;               // del juego
};
const uint32_t MAGIC_MASCOTA = 0x7A3A0F01;

class Cara {
 public:
  // Ojos: posición y tamaño actuales y a dónde van (se mueven suave)
  float mx = 0, my = 0, objX = 0, objY = 0;
  uint32_t tParpadeo = 0, tMirada = 0, tExpr = 0;
  Expr expr = EX_NORMAL, exprTemp = EX_NORMAL;

  // Una expresión por unos segundos (feliz al liberar la moto, mareado al agitarlo...)
  void momento(Expr e, uint32_t ms) { exprTemp = e; tExpr = millis() + ms; }
  Expr actual() { return (int32_t)(millis() - tExpr) < 0 ? exprTemp : expr; }

  void dibujar(Oled& o) {
    uint32_t t = millis();
    Expr e = actual();
    if (t > tMirada) {                       // mira a otro lado cada tanto
      bool centro = random(3) == 0;
      objX = centro ? 0 : random(-10, 11);
      objY = centro ? 0 : random(-5, 6);
      tMirada = t + random(1200, 4000);
    }
    mx += (objX - mx) * 0.25f;
    my += (objY - my) * 0.25f;
    float abre = 1;
    if (t > tParpadeo) {                     // parpadeo de ~150 ms
      uint32_t d = t - tParpadeo;
      if (d < 150) abre = fabsf(d - 75.0f) / 75.0f;
      else tParpadeo = t + random(2000, 6000);
    }
    const int W = 30, H = 34, Y = 28;
    int cx[2] = {38 + (int)mx, 90 + (int)mx};
    int cy = Y + (int)my;
    for (int i = 0; i < 2; i++) {
      int x0 = cx[i] - W / 2;
      int y0 = cy - H / 2;
      switch (e) {
        case EX_DORMIDO:
          o.redondo(x0, cy + 6, W, 4, 2);
          break;
        case EX_SORPRESA:
          o.circulo(cx[i], cy, 15);
          o.circulo(cx[i], cy, 5, true, false);
          break;
        case EX_AMOR:
          corazon(o, cx[i], cy, 1 + 0.12f * sinf(t / 120.0f));
          break;
        case EX_MAREADO:
          espiral(o, cx[i], cy, t / 90.0f * (i ? -1 : 1));
          break;
        default: {
          int h = max(3, (int)(H * abre));
          int yy = cy - h / 2;
          o.redondo(x0, yy, W, h, 8);
          if (e == EX_FELIZ) o.redondo(x0 - 2, yy + h / 2 + 2, W + 4, h, 10, false);   // queda la curva de arriba ^ ^
          if (e == EX_ENOJADO) {                 // ceja hacia el centro
            if (i == 0) o.triangulo(x0 - 1, yy - 1, x0 + W + 1, yy - 1, x0 + W + 1, yy + 14, false);
            else o.triangulo(x0 - 1, yy - 1, x0 + W + 1, yy - 1, x0 - 1, yy + 14, false);
          }
          if (e == EX_TRISTE) {                  // ceja hacia afuera
            if (i == 0) o.triangulo(x0 - 1, yy - 1, x0 + W + 1, yy - 1, x0 - 1, yy + 12, false);
            else o.triangulo(x0 - 1, yy - 1, x0 + W + 1, yy - 1, x0 + W + 1, yy + 12, false);
          }
        }
      }
    }
    if (e == EX_DORMIDO) {                       // zZz que sube
      int k = (t / 400) % 3;
      o.texto(104, 14 - k * 3, "z", 1);
      if (k > 0) o.texto(110, 8 - k * 2, "Z", 1);
    }
    if (e == EX_FELIZ || e == EX_AMOR) o.redondo(58 + (int)mx, 52 + (int)my, 12, 3, 1);   // boquita
    if (e == EX_TRISTE) o.redondo(60 + (int)mx, 54 + (int)my, 8, 2, 1);
  }

 private:
  static void corazon(Oled& o, int cx, int cy, float s) {
    int r = 7 * s;
    o.circulo(cx - 6 * s, cy - 4 * s, r);
    o.circulo(cx + 6 * s, cy - 4 * s, r);
    o.triangulo(cx - 13 * s, cy - 1 * s, cx + 13 * s, cy - 1 * s, cx, cy + 14 * s);
  }
  static void espiral(Oled& o, int cx, int cy, float giro) {
    for (float a = 0; a < 4 * PI; a += 0.12f) {
      float r = 1.1f * a;
      int x = cx + r * cosf(a + giro), y = cy + r * sinf(a + giro);
      o.rellenar(x, y, 2, 2);
    }
  }
};

// --- La vida de la mascota ---
namespace vida {

// Cuánto baja por hora
const float BAJA_COMIDA = 3.0f, BAJA_ANIMO = 2.0f, BAJA_ENERGIA = 4.0f, SUBE_ENERGIA_DORMIDO = 12.0f;

void pasar(Mascota& m, uint32_t seg, bool dormido) {
  float h = seg / 3600.0f;
  m.vida += seg;
  m.comida = max(0.0f, m.comida - BAJA_COMIDA * h);
  m.animo = max(0.0f, m.animo - BAJA_ANIMO * h);
  if (dormido) m.energia = min(100.0f, m.energia + SUBE_ENERGIA_DORMIDO * h);
  else m.energia = max(0.0f, m.energia - BAJA_ENERGIA * h);
}

// La moto rodó: 1 km = +4 comida, +2 ánimo
void kilometros(Mascota& m, uint32_t km10) {
  if (m.km10Visto == 0 || km10 < m.km10Visto || km10 - m.km10Visto > 5000) { m.km10Visto = km10; return; }
  float km = (km10 - m.km10Visto) / 10.0f;
  m.km10Visto = km10;
  m.kmJuntos += km;
  m.comida = min(100.0f, m.comida + 4 * km);
  m.animo = min(100.0f, m.animo + 2 * km);
}

// Cara según cómo está
Expr animo(const Mascota& m, bool deNoche, uint8_t bateriaPct) {
  if (deNoche || m.energia < 10) return EX_DORMIDO;
  if (m.comida < 20 || bateriaPct < 10) return EX_TRISTE;
  if (m.animo > 80 && m.comida > 60) return EX_FELIZ;
  return EX_NORMAL;
}

}  // namespace vida
