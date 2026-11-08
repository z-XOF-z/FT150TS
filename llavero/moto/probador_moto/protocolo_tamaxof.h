#pragma once
// Protocolo TamaXOF <-> moto por ESP-NOW. EL MISMO ARCHIVO va en el llavero y en la moto
// (generar_secreto.py lo copia a las dos carpetas; edita solo llavero/comun/).
//
// Cada mensaje: cabecera (4) + iv (8) + cuerpo cifrado AES-128-CTR + firma HMAC-SHA256 (16).
// La firma cubre todo y la MAC de quien lo manda, así que no se puede copiar ni cambiar.
// Para liberar o mandar una orden, la moto manda un reto nuevo (16 bytes al azar) y el llavero
// lo regresa firmado: grabar la señal y repetirla después no sirve.
//
//   llavero  --HOLA(orden, cont)-->  moto
//   llavero  <--RETO(orden, nonce)--  moto      (la moto toma el tiempo)
//   llavero  --RESP(orden, nonce)-->  moto      (si tarda > TX_RTT_MAX_US, se ignora: anti-relevo)
//   llavero  <--ESTADO(cont, ...)--   moto
//   llavero  <--TELE(cont, ...)--     moto      (solo tras un OR_ESTADO: 4 veces por segundo durante 30 s,
//                                               lleva RPM, velocidad, alertas y avisos del teléfono)
#include <Arduino.h>
#include <esp_random.h>
#include <mbedtls/md.h>
#include <mbedtls/aes.h>

#define TX_VER 1
#define TX_RTT_MAX_US 60000UL   // reto -> respuesta; con un repetidor en medio tarda más

enum TxTipo : uint8_t { TX_HOLA = 1, TX_RETO = 2, TX_RESP = 3, TX_ESTADO = 4, TX_TELE = 5 };
enum TxOrden : uint8_t {
  OR_ESTADO = 0,     // solo pide cómo está la moto (y suscribe al llavero a la telemetría 30 s)
  OR_PRESENCIA = 1,  // "aquí estoy": libera la llave de proximidad
  OR_BLOQUEAR = 2,   // pone el bloqueo (solo con la moto parada)
  OR_APAGAR = 3,     // botón de paro: corta el motor (como el switch rojo de otras motos)
  OR_PERMITIR = 4,   // quita el paro y el bloqueo
  OR_BUSCAR = 5,     // la moto avisa dónde está (sirena si la alarma tiene una)
  OR_PRESTAR = 6,    // apaga la llave de proximidad: la moto arranca con la pura llave
  OR_DEVOLVER = 7,   // vuelve a prender la llave de proximidad
};
const uint8_t OR_ULTIMA = OR_DEVOLVER;
enum : uint8_t { EF_BLOQ = 1, EF_APAGADO = 2, EF_IGN = 4, EF_RODANDO = 8, EF_OK = 16, EF_LLAVE = 32 };
enum : uint8_t { TA_NADA = 0, TA_MOTO = 1, TA_TEL = 2 };   // tipo de alerta en TxTele

struct __attribute__((packed)) TxCab { uint8_t m1, m2, ver, tipo; uint8_t iv[8]; };
struct __attribute__((packed)) TxHola { uint8_t orden; uint32_t cont; };
struct __attribute__((packed)) TxReto { uint8_t orden; uint32_t cont; uint8_t nonce[16]; };
struct __attribute__((packed)) TxResp { uint8_t orden; uint8_t nonce[16]; };
struct __attribute__((packed)) TxEstado {
  uint8_t flags;      // EF_*
  uint8_t orden;      // a qué orden responde
  uint32_t cont;      // eco del contador del HOLA (el llavero descarta lo viejo)
  int16_t vMoto_cV;   // batería de la moto x100, -1 = no se sabe
  int8_t gas;         // %, -1 = no se sabe
  uint8_t vel;        // km/h
  uint32_t km10;      // odómetro x10
  uint32_t epoch;     // hora LOCAL en segundos (0 = la moto no la sabe)
};
struct __attribute__((packed)) TxTele {
  uint32_t cont;      // eco del HOLA con OR_ESTADO que suscribió al llavero (lo viejo no cuenta)
  uint32_t seq;       // sube en cada envío (el llavero tira lo repetido)
  uint8_t flags;      // EF_*
  int16_t rpm;        // -1 = no se sabe
  uint8_t vel;        // km/h
  int8_t marcha;      // -1 = no se sabe, 0 = neutral
  int16_t temp10;     // °C x10, INT16_MIN = no se sabe
  int8_t gas;         // %, -1 = no se sabe
  int16_t vMoto_cV;   // V x100, -1 = no se sabe
  uint32_t km10;
  uint32_t epoch;
  uint8_t tipoAlerta; // TA_*
  char alerta[22];    // lo mismo que la fila de alertas de la pantalla de la moto
};

const size_t TX_TAG = 16;
const size_t TX_CUERPO_MAX = 64;
const size_t TX_MAX = sizeof(TxCab) + TX_CUERPO_MAX + TX_TAG;
static_assert(sizeof(TxTele) <= TX_CUERPO_MAX, "TxTele no cabe");

class TxCripto {
 public:
  void iniciar(const uint8_t* secreto32) {
    uint8_t t[32];
    hmac(secreto32, 32, (const uint8_t*)"TamaXOF-enc", 11, t);
    memcpy(_kEnc, t, 16);
    hmac(secreto32, 32, (const uint8_t*)"TamaXOF-mac", 11, _kMac);
  }

  // Arma un mensaje listo para esp_now_send. Devuelve su largo.
  size_t armar(uint8_t tipo, const void* cuerpo, size_t n, const uint8_t* macOrigen, uint8_t* out) {
    TxCab* c = (TxCab*)out;
    c->m1 = 'T'; c->m2 = 'X'; c->ver = TX_VER; c->tipo = tipo;
    esp_fill_random(c->iv, sizeof c->iv);
    ctr(c->iv, (const uint8_t*)cuerpo, out + sizeof(TxCab), n);
    size_t largo = sizeof(TxCab) + n;
    firma(out, largo, macOrigen, out + largo);
    return largo + TX_TAG;
  }

  // Revisa la firma y descifra. Devuelve el largo del cuerpo, o -1 si no es nuestro / está alterado.
  int abrir(const uint8_t* in, size_t len, const uint8_t* macOrigen, uint8_t& tipo, void* cuerpo, size_t max) {
    if (len < sizeof(TxCab) + TX_TAG) return -1;
    const TxCab* c = (const TxCab*)in;
    if (c->m1 != 'T' || c->m2 != 'X' || c->ver != TX_VER) return -1;
    size_t n = len - sizeof(TxCab) - TX_TAG;
    if (n > max) return -1;
    uint8_t t[TX_TAG];
    firma(in, len - TX_TAG, macOrigen, t);
    uint8_t dif = 0;
    for (size_t i = 0; i < TX_TAG; i++) dif |= t[i] ^ in[len - TX_TAG + i];
    if (dif) return -1;
    tipo = c->tipo;
    ctr(c->iv, in + sizeof(TxCab), (uint8_t*)cuerpo, n);
    return (int)n;
  }

 private:
  uint8_t _kEnc[16], _kMac[32];

  static void hmac(const uint8_t* k, size_t kn, const uint8_t* d, size_t dn, uint8_t* out32) {
    mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), k, kn, d, dn, out32);
  }

  void firma(const uint8_t* d, size_t n, const uint8_t* mac, uint8_t* out16) {
    uint8_t tmp[TX_MAX + 6], h[32];
    memcpy(tmp, d, n);
    memcpy(tmp + n, mac, 6);
    hmac(_kMac, sizeof _kMac, tmp, n + 6, h);
    memcpy(out16, h, TX_TAG);
  }

  void ctr(const uint8_t* iv, const uint8_t* in, uint8_t* out, size_t n) {
    mbedtls_aes_context a;
    mbedtls_aes_init(&a);
    mbedtls_aes_setkey_enc(&a, _kEnc, 128);
    uint8_t nc[16] = {0}, bloque[16];
    size_t off = 0;
    memcpy(nc, iv, 8);
    mbedtls_aes_crypt_ctr(&a, n, &off, nc, bloque, in, out);
    mbedtls_aes_free(&a);
  }
};
