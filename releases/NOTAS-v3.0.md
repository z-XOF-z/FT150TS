## FT150TS Dashboard v3.0

Tablero digital + launcher para la **Italika FT150TS**. Funciona solo con el teléfono (GPS, RPM por vibración, sensores) y está listo para la tarjeta ESP32.

### Instalar
1. Descarga **FT150TS-v3.0.apk** en tu teléfono Android (8.0 o más nuevo).
2. Ábrelo y permite "Instalar apps desconocidas" para tu navegador o administrador de archivos.
3. Dale los permisos que pide (ubicación, Bluetooth, notificaciones).

### Lo nuevo en la v3.0
- **Tema Vértice**: riel lateral con cortes a 45° en horizontal (el tablero crece), velocidad/marcha/RPM visibles en todas las secciones, losas sin contorno sobre fibra de carbono.
- **Temas Holo, Ácido y Circuito** y 4 acentos fluorescentes nuevos.
- **Modo reposo**: con la moto parada y cargando, reloj atenuado o pantalla apagada y GPS en pausa; despierta al dar marcha o al tocar.
- **Medidor de fluidez** (Ajustes > Pantalla): FPS y % de tirones reales medidos por Android.
- **Atajos del ícono**: mantén presionado para ir directo a Tablero, Mapa o Moto.
- **Baches**: se avisan una o dos veces por cuadra, no en cada bache.
- **Resumen de rodada por voz** al apagar la moto.

### Seguridad
- La app solo se conecta a **tu** tarjeta (verifica sus características, la fija y pide vincular). Antes se podía conectar a cualquier aparato Bluetooth compatible y mandarle notificaciones y GPS.
- Restaurar un respaldo ya no puede activar los ajustes de root ni escribir fuera de su carpeta.
- Firmware 1.4: clave de WiFi propia por tarjeta y comandos delicados (corte de motor, calibración, odómetro) solo desde un teléfono vinculado.
- Informe completo en [SEGURIDAD.md](https://github.com/z-XOF-z/FT150TS/blob/main/SEGURIDAD.md).

> Firmado con la llave de desarrollo: si ya tenías la app instalada desde Android Studio en la misma PC, se instala encima sin borrar tus datos.
