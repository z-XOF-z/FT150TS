## FT150TS Dashboard v3.11.0-beta.1

> ⚠️ **Versión BETA (de prueba).** La app ya funciona en el teléfono, pero el firmware v1.6 de la tarjeta y la placa B todavía **no se han probado con el hardware real** (la placa viene en camino). Puede haber fallas; si quieres lo estable, quédate en la v3.10.0. Para recibir betas: Ajustes > Acerca de > "Recibir versiones de prueba".

### Nuevo con la tarjeta (firmware v1.6)
- **Actualiza la tarjeta desde la app:** en Moto > Herramientas de la tarjeta eliges el .bin y se manda por Bluetooth (~2 min), sin desarmar la caja del asiento. La tarjeta revisa el archivo (MD5) antes de cambiarse: si algo falla, sigue con el firmware de antes. Solo con el teléfono vinculado y la moto parada con el motor apagado.
- **Telemetría más rápida:** la velocidad y las RPM llegan 10 veces por segundo (antes 4) en un paquete 10 veces más chico.
- **Bajar los registros:** los viajes, eventos, batería y arrancones de la SD de la tarjeta se guardan en Descargas/FT150TS para abrirlos en Excel.
- **Casa y Telegram:** pones el WiFi de tu casa y tu bot de Telegram (la app te ayuda a encontrar tu chat). Con la moto estacionada en tu casa, la tarjeta te avisa si la batería está baja o si la mueven, y obedece /estado, /bloquear, /desbloquear y /ubicacion. Solo te hace caso a ti. Las claves se quedan en la tarjeta.
- **Protector de batería:** si la batería de la moto baja de 11.8 V, la tarjeta despierta solo cada 4 h.
- **Bluetooth en ahorro:** con el teléfono fuera de la moto y la app cerrada, la conexión con la tarjeta gasta menos.
- **Placa de la moto (nueva sección en Moto):**
  - **Freno:** ves si está pisado y cuántas frenadas llevas. Las "frenadas fuertes" ya no cuentan baches.
  - **Direccional olvidada:** si llevas 20 s rodando con ella prendida, el LCD avisa, la tira de LEDs parpadea en ámbar y la app te dice (con voz si tienes los avisos de voz).
  - **Relevadores 2 y 3 y la salida aislada:** los prendes desde la app con el nombre que quieras ("Luces", "Claxon"...).
  - **Alarma de movimiento:** si alguien mueve la moto estacionada y no giras la llave en 20 s, te escribe por Telegram y suena la sirena que elijas. Funciona aunque la tarjeta esté dormida.
  - **IA de vibraciones:** la tarjeta aprende cómo vibra tu moto sana en cada rango de RPM y te avisa si algo cambia (cadena, rueda, balero, tornillo suelto). Opcional: guarda las lecturas para entrenar después una red neuronal.
  - **Brillo automático** con el sensor de luz del teléfono (el lugar de la fotorresistencia ahora lo usa la alarma).
- **Antirrobo del teléfono:** si alguien mueve el teléfono con la moto estacionada (o la tarjeta siente que movieron la moto) y no eres tú en 20 s, te manda por Telegram la ubicación, una foto de cada cámara y un audio. Se arma solo al apagar la moto. Se desarma con la llave, la carga o desbloqueando el teléfono.
- **Estado de la moto:** batería, cadena, encendido, frenos y motor de 0 a 100, con qué hacer. También por Telegram con /salud.
- **IA de baches:** la tarjeta detecta los baches con su acelerómetro y los agrega al mapa de baches de la app.
- **Gráficas de voltaje:** batería de la moto y celda de respaldo, últimas 24 h.
- **Llave de proximidad (apagada de fábrica):** la moto no arranca hasta que se conecte tu POCO o tu iPhone, o escribas tu código con los botones.
- **Sorpresas:** resumen semanal por Telegram (lunes: km, viajes, gasolina, máxima y baches) y aviso de cuánto cargaste de gasolina con tu rendimiento.
- **Más estable:** si algo falla en segundo plano, la app ya no se cierra (antes un error de CarPlay podía cerrar el launcher).
- **Importante la primera vez:** el firmware v1.6 se graba una vez por USB con el esquema de particiones "16M Flash (3MB APP/9.9MB FATFS)". Desde ahí, las siguientes actualizaciones ya van por la app.

### El teléfono ya se apaga solo cuando no estás en la moto
- **CarPlay ya no deja prendida la pantalla:** la pantalla de CarPlay de DiPlay pide quedarse siempre encendida. En el modo Pestaña, al salir de Auto se escondía fuera de la pantalla pero seguía viva, y Android le hacía caso: el teléfono no se apagaba ni en tus apps y se descargaba solo.
  - Ahora, fuera de la moto y sin que lo uses, CarPlay se cierra: con la app a la vista, cuando pasa el tiempo de apagado de tu teléfono (nunca antes de 30 s); en otra app o con la pantalla apagada, al minuto.
  - En la moto no cambia nada: CarPlay sigue escondido y regresa al instante.
  - Al volver a Auto se abre otra vez solo.
- **"Pantalla encendida: Siempre" ya no aplica en tus apps:** fuera de la moto, el cajón de apps se apaga como siempre y las demás páginas se apagan después de 10 min sin tocarlas. En la moto se queda prendida igual que antes.

### Conectado a la computadora ya no cuenta como "en la moto"
- Antes, al conectar el teléfono a la PC la app creía que habías prendido el switch: entraba al modo moto (tablero, saludo, zona WiFi, búsqueda rápida de la tarjeta).
- Ahora distingue la computadora (te reconoce como dispositivo USB) de un cargador (el de la moto o uno de pared). Con la PC sigue cargando y el reposo y el modo sueño funcionan igual, pero el modo moto no se prende.
- En Ajustes > Modo moto aparece "Conectado a la computadora".

### Menos batería con la app como pantalla de inicio
- **GPS:** en tus apps y fuera de la moto ya no se queda prendido. Antes pedía tu ubicación cada segundo en alta precisión cada vez que veías tus apps. Sigue prendido si estás en la moto, grabando una rodada o con el teléfono haciendo de sensor. El clima usa la última ubicación conocida.
- **Bluetooth:** con la app en pantalla y la moto apagada, la búsqueda de la tarjeta pasa a modo ahorro después de un minuto (antes buscaba en modo rápido sin parar). Con el teléfono cargando, o al volver a la app, busca rápido otra vez.
- **Tablero Morado:** el ecualizador apagado y las rayitas del arco se dibujan una sola vez (antes eran cientos de figuras por cuadro).
- **"Siempre":** después de soltarse por no tocarla, al volver a tocar la pantalla se queda prendida otra vez.

### Firmware de la tarjeta v1.5.1 (en el código, por si lo grabas)
- La tarjeta ya se duerme con la llave apagada aunque el teléfono esté cerca: la ubicación que manda la app cada segundo ya no cuenta como uso.
- "Borrar todo" y "dormir" ahora solo se aceptan desde el teléfono vinculado o por USB.
- El iPhone ya no deja de conectarse después de que otros aparatos se conectan a la tarjeta.
- Entrar al modo demo ya no borra los kilómetros que faltaba guardar.
- El GPS de la tarjeta pierde menos datos cuando está ocupada.
- La tira de LEDs ya no se actualiza 33 veces por segundo cuando está apagada.
