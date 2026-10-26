## FT150TS Dashboard v3.6.3 (estable) + módulo "sin barra" v1.3

### Arreglo: CarPlay se encogía a una ventana chiquita al deslizar
HyperOS deja escalar una ventana flotante arrastrando cerca de sus esquinas (zonas de 44 dp). Un deslizamiento dentro
de CarPlay que empezaba ahí la encogía a escala 0.25.
- **Módulo v1.3:** esas zonas de las esquinas pasan a 1 dp. Instálalo desde Magisk y reinicia.
- **FT150TS:** el vigilante de CarPlay ahora también revisa la escala. Si HyperOS la cambió, saca la ventana del modo
  flotante y la vuelve a meter, con lo que regresa a su tamaño (CarPlay se reconecta solo en unos segundos).

Probado en un POCO F4 con HyperOS 1: arrastres desde tres esquinas de CarPlay, sin cambio de escala.
