## FT150TS Dashboard v3.4 (estable)

Todo lo que se probó en las betas 3.1 a 3.4, junto. Se instala encima de la v3.0 sin borrar tus datos.

### CarPlay en la pestaña Auto
- Con [DiPlay](https://github.com/shihabal3amri/DiPlay) instalado y root, la pantalla de CarPlay ocupa la pestaña
  Auto, debajo de tus pestañas (que siguen sirviendo para deslizar).
- Se esconde al cambiar de pestaña sin cortar la sesión y regresa en ~0.3 s. Si quedó mal puesta, se recoloca sola.
- Sin la píldora de HyperOS abajo (no se puede tocar sin querer al manejar). Tamaños: Máximo, Amplio y Ajustado.
- No se salta la autenticación de Apple: DiPlay hace la conexión con el iPhone y FT150TS solo acomoda su ventana.

### Temas: 12 estilos nuevos + Mezcla
- Aurora, Bloque, Arcilla, Cyber, Synth, Suizo, Bento, Noche roja, Orgánico, Memphis, Art Déco y Pista, además
  del tema Tinta (viñetas de cómic).
- **Mezcla:** cada toque a "Otra mezcla" arma un tema nuevo (paleta, forma, adorno y fondo) con su propio nombre.
- "Usar el color del tema" o tu propio color de acento.

### Animación al abrir el switch
- Sale una vez cada que abres el switch, no al cambiar de pestaña.
- 5 animaciones: Barrido 45°, CRT, Escáner, Pulso y Píxeles, o una al azar. Botón "Probar animación".

### Nuevo con el switch
- **Cable flojo:** si se corta la carga mientras vas rodando, el tablero ya no se va a tus apps a media calle. Te
  avisa ("Sin carga: revisa el cable") y espera a que te detengas.
- **Música con el switch:** al apagar la moto se pausa; si la prendes en menos de 30 min, sigue donde iba.
- **Saludo por voz (opcional):** al prender después de un rato, te dice la temperatura, si va a llover y si te queda
  poca gasolina.

### Seguridad y energía
- **SOS por caída:** si detecta una caída fuerte rodando, muestra una cuenta regresiva (20, 30 o 60 s) y manda un SMS
  con tu ubicación a tus contactos. Lo cancelas con un toque.
- **Ahorro nocturno con root (00:00–06:30):** perfiles conservador o extremo. Todo se deshace solo al terminar.
  No apaga el teléfono.

### Más rápida (medido en un POCO F4)
- −32 % de CPU con la app quieta. El acelerómetro de las RPM entrega sus muestras por lotes (mismos datos, muchos
  menos despertares).
- La página Auto deja de dibujarse mientras CarPlay la tapa (de 74 a 14 cuadros por segundo).
- El fondo queda guardado en la GPU (37 % menos trabajo) y la barra de arriba solo se actualiza cuando cambia lo
  que se ve.

### Actualizar
Ajustes → Acerca de → **Buscar actualización**. Para recibir las betas, prende "Versiones de prueba".

67 pruebas automáticas pasan.
