## FT150TS Dashboard v3.4-beta.1 (versión de prueba)

### 12 estilos nuevos + Mezcla (temas sin fin)
Temas nuevos basados en estilos de UI UX Pro Max, adaptados a un tablero de moto en pantalla AMOLED. Cada uno trae
su propia paleta, forma de tarjetas, adorno y fondo. Están en Ajustes → Apariencia → **Estilos**.

| Estilo | Qué es |
|---|---|
| **Aurora** | Vidrio translúcido sobre manchas de luz de color |
| **Bloque** | Neubrutal: bloques sólidos, borde grueso y banda amarilla |
| **Arcilla** | Suave y esponjoso, sin bordes, con luz y sombra |
| **Cyber** | Glitch cian y magenta, líneas de escaneo |
| **Synth** | Atardecer neón de los 80 con rejilla en perspectiva |
| **Suizo** | Minimal, una barra roja a la izquierda y rejilla tenue |
| **Bento** | Losas redondeadas estilo Apple |
| **Noche roja** | Todo rojo y oscuro para manejar de noche |
| **Orgánico** | Tarjetas en forma de hoja y curvas de nivel |
| **Memphis** | Bordes punteados, figuras y garabatos de los 80 |
| **Art Déco** | Oro, doble filete y abanicos |
| **Pista** | Franjas de carreras y bandera a cuadros |

- **Mezcla:** combina al azar paleta, forma, adorno y fondo. Cada toque a **Otra mezcla** crea un tema nuevo con
  nombre propio ("Volcán eléctrico", "Asfalto nocturno"…). Hay cientos de miles de combinaciones y se guarda la
  que te guste.
- **Usar el color del tema:** cada estilo trae su color de acento. Apágalo para usar el acento que elijas.
- Los temas de siempre (Carbono, Vidrio, Filo, Holo, Tinta…) siguen igual.

### Rendimiento
Todo el dibujo de los estilos se arma una vez por tamaño de pantalla (en caché). Cambiar la velocidad o las RPM no
vuelve a dibujar el fondo ni los adornos.

### Probado en un POCO F4 (HyperOS 3)
- Aurora, Bloque, Cyber, Synth, Pista y Mezcla en el tablero; las vistas previas de los 12 estilos en Ajustes; el botón
  "Otra mezcla" cambia el tema al instante.
- 61 pruebas automáticas pasan (4 nuevas: cada estilo tiene su tema, el acento contrasta al menos 3:1 con la tarjeta,
  la misma semilla da la misma mezcla y ninguna mezcla deja una tarjeta sin borde ni adorno).
