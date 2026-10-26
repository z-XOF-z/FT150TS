## FT150TS Dashboard v3.6.2 (estable) + módulo "sin barra" v1.2

### Arreglo: los botones de DiPlay no respondían al tocarlos
Con el módulo v1.1, las barras de la ventana flotante medían 0. Así el sistema calculaba su zona táctil con escala 0,
y la franja invisible de la píldora se quedaba con los toques de casi toda la ventana de CarPlay. Por eso "Volver a
DiPlay" y otros botones no hacían nada.
- **Módulo v1.2** (`FT150TS-sin-barra-v1.2.zip`): las barras miden 1 dp en vez de 0. Casi no se ven y los toques
  llegan bien. Instálalo desde Magisk y reinicia.
- **FT150TS:** con el módulo activo, esconde la rayita de abajo bajo la orilla de la pantalla.

Probado en un POCO F4 con HyperOS 1: el toque en "Volver a DiPlay" llega a DiPlay y abre su pantalla.
