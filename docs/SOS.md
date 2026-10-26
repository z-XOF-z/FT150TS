# SOS por caída (v3.3)

**Ajustes → SOS por caída.** Apagado de fábrica.

## Cómo funciona

1. Usa el mismo detector de golpes que la dashcam: un golpe de más de ~3.5 g en el teléfono del manubrio.
2. Solo cuenta si ibas rodando: más de 15 km/h en ese momento o en los 15 s anteriores. Un teléfono que se cae de
   la mesa no lo dispara.
3. Espera 8 s. Si sigues rodando (más de 5 km/h), fue un bache enorme y no pasa nada.
4. Si el teléfono quedó quieto, sale una pantalla roja con una **cuenta regresiva** (20, 30 o 60 s) y un botón verde
   gigante **ESTOY BIEN**, que se puede tocar con guantes. La voz avisa al empezar y cada 10 s, y el teléfono vibra.
5. Si no lo tocas, manda un SMS a tu contacto de emergencia con tu nombre, la hora y un enlace de Google Maps con tu
   ubicación y su precisión.
   - Con el permiso de SMS se manda solo.
   - Sin el permiso se abre tu app de Mensajes ya escrita, y hay que tocar enviar.
6. **Pedir ayuda ya** lo manda sin esperar. Después de un aviso no hay otro en 5 min.

## Seguridad y privacidad

- El número se limpia: solo dígitos y un `+`, de 7 a 15 dígitos. No pasan códigos `*#…#` ni letras. Hay pruebas.
- Nada se manda sin un número válido configurado.
- **Probar** hace la misma cuenta y la misma voz, pero al final solo muestra el mensaje: no se manda nada.
- Permiso nuevo: `SEND_SMS`. Es opcional y se pide solo desde esta sección.

## Límites

- Depende del acelerómetro del teléfono en el soporte. Una caída sin golpe fuerte, o con el teléfono suelto, puede
  no detectarse.
- La app tiene que estar corriendo ("Mantener activa en segundo plano").
- Sin señal celular el SMS no sale hasta que haya señal (lo maneja Android).
- No sustituye a un servicio de emergencias.
- **No se probó una caída real.** Las reglas tienen pruebas automáticas (`EmergenciaTest`); la cuenta y la voz se
  prueban con **Probar**.
