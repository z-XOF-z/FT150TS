## FT150TS Dashboard v3.1

### Lo nuevo
- **CarPlay en la página Auto** (botón **CP** en el mapa): con [DiPlay](https://github.com/shihabal3amri/DiPlay)
  instalado y tu iPhone conectado en él, el mapa en vivo de CarPlay toma el lugar del mapa. Arriba ves el estado
  real (conectando, sin mapa, en vivo, error). **CarPlay** abre CarPlay completo y **OSM** regresa al mapa de siempre.
  Sin sesión de CarPlay, el mapa de OpenStreetMap sigue igual.
- Necesita Android 11+ y DiPlay 0.2.9 o más nuevo. En DiPlay → Advanced prende "CarPlay map on dashboard
  (experimental)" y "Share the live map with other launchers", y abre su pantalla de CarPlay al menos una vez.
  Guía completa en [docs/CARPLAY.md](https://github.com/z-XOF-z/FT150TS/blob/main/docs/CARPLAY.md).
- Se reconecta sola si DiPlay se cierra o niega el mapa (5 s, 10 s, 20 s y luego cada 30 s), sin trabar la pantalla.

### Seguridad
- La app solo le habla a DiPlay de verdad (revisa su firma) y no pide permisos nuevos.

### Estado
- Compilado y probado con pruebas automáticas. **Sin probar todavía en el teléfono con un iPhone.** DiPlay está
  hecho para estéreos BYD y en teléfonos es experimental.

> Firmado con la llave de desarrollo, igual que la v3.0: se instala encima sin borrar tus datos.
