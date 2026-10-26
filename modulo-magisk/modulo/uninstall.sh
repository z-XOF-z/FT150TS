#!/system/bin/sh
# Al desinstalar el módulo: apagar la overlay (el archivo desaparece al reiniciar)
cmd overlay disable --user 0 com.xof.ft150ts.sinbarra 2>/dev/null
rm -f /data/adb/ft150ts_sinbarra_off
