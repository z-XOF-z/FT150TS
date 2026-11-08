#pragma once
// Control de música por Bluetooth: el llavero se presenta como un control remoto de medios (BLE HID),
// igual que unos audífonos. Funciona con iPhone, Android y PC sin instalar nada.
// Solo se prende al entrar a la pantalla Música (gasta pila).
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEHIDDevice.h>
#include <BLESecurity.h>
#include <esp_gap_ble_api.h>

namespace musica {

enum Tecla : uint8_t { SIGUIENTE = 0x01, ANTERIOR = 0x02, ALTO = 0x04, PAUSA = 0x08, MUDO = 0x10, VOL_MAS = 0x20, VOL_MENOS = 0x40 };

// Reporte "consumer control" de 8 bits, uno por tecla
static const uint8_t MAPA[] = {
  0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, 0x01, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08,
  0x09, 0xB5, 0x09, 0xB6, 0x09, 0xB7, 0x09, 0xCD, 0x09, 0xE2, 0x09, 0xE9, 0x09, 0xEA, 0x09, 0xB0,
  0x81, 0x02, 0xC0,
};

bool iniciado = false;
volatile bool conectado = false;
BLEHIDDevice* hid = nullptr;
BLECharacteristic* entrada = nullptr;
BLEServer* servidor = nullptr;

class Avisos : public BLEServerCallbacks {
  void onConnect(BLEServer*) override { conectado = true; }
  void onDisconnect(BLEServer* s) override { conectado = false; s->getAdvertising()->start(); }
};

void iniciar(const char* nombre, uint8_t bateria) {
  if (iniciado) return;
  BLEDevice::init(nombre);
  servidor = BLEDevice::createServer();
  servidor->setCallbacks(new Avisos());
  hid = new BLEHIDDevice(servidor);
  entrada = hid->inputReport(1);
  hid->manufacturer()->setValue("XOF");
  hid->pnp(0x02, 0xE502, 0xA111, 0x0210);
  hid->hidInfo(0x00, 0x01);
  BLESecurity* seg = new BLESecurity();
  seg->setAuthenticationMode(ESP_LE_AUTH_BOND);
  seg->setCapability(ESP_IO_CAP_NONE);
  hid->reportMap((uint8_t*)MAPA, sizeof MAPA);
  hid->startServices();
  hid->setBatteryLevel(bateria);
  BLEAdvertising* a = servidor->getAdvertising();
  a->setAppearance(0x0180);   // control remoto genérico
  a->addServiceUUID(hid->hidService()->getUUID());
  a->start();
  iniciado = true;
}

void tecla(uint8_t t) {
  if (!conectado || !entrada) return;
  entrada->setValue(&t, 1);
  entrada->notify();
  delay(15);
  uint8_t cero = 0;
  entrada->setValue(&cero, 1);
  entrada->notify();
}

void bateria(uint8_t pct) { if (hid) hid->setBatteryLevel(pct); }

// Olvida los teléfonos/PC emparejados (para emparejar otro)
void olvidarTodos() {
  int n = esp_ble_get_bond_device_num();
  if (n <= 0) return;
  esp_ble_bond_dev_t* l = (esp_ble_bond_dev_t*)malloc(sizeof(esp_ble_bond_dev_t) * n);
  if (!l) return;
  esp_ble_get_bond_device_list(&n, l);
  for (int i = 0; i < n; i++) esp_ble_remove_bond_device(l[i].bd_addr);
  free(l);
}

}  // namespace musica
