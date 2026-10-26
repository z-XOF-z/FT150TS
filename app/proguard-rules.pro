# Reglas para el build release (R8)
# osmdroid (mapa) usa reflexión en algunas partes: se conserva completo
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
# Servicio de notificaciones (lo llama el sistema)
-keep class com.xof.ft150ts.NotifListener { *; }

# Ayudante que corre como root con app_process (Root.cmdAyudante): no renombrar
-keep class com.xof.ft150ts.AyudanteRoot { public static void main(java.lang.String[]); }
