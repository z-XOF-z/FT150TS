# Reglas para el build release (R8)
# osmdroid (mapa) usa reflexión en algunas partes: se conserva completo
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
# Servicio de notificaciones (lo llama el sistema)
-keep class com.xof.ft150ts.NotifListener { *; }
