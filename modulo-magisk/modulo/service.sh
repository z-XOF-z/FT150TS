#!/system/bin/sh
# FT150TS · Ventanas flotantes sin barra
# La superposición (overlay) de SystemUI se prende DESPUÉS de que el teléfono arrancó, así el arranque nunca
# depende de ella. Si SystemUI se reinicia más de 2 veces en el minuto siguiente, se apaga sola.
# Apagarla a mano:  touch /data/adb/ft150ts_sinbarra_off  (y reinicia)  o  cmd overlay disable com.xof.ft150ts.sinbarra
MODDIR=${0%/*}
LOG=$MODDIR/registro.log
OVL=com.xof.ft150ts.sinbarra
# OJO: la salida de "cmd" NO se manda directo a un archivo de /data/adb: el sistema (system_server) no puede escribir
# ahí y la llamada falla con "Failed transaction". Se captura por tubería y luego se anota.
ov() { r=$(cmd overlay "$@" --user 0 $OVL 2>&1); echo "$(date '+%F %T') overlay $1: ${r:-ok}" >> $LOG; }

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 10
echo "$(date '+%F %T') arranque" > $LOG

if [ -f /data/adb/ft150ts_sinbarra_off ]; then
  ov disable
  echo "$(date '+%F %T') apagada a mano (/data/adb/ft150ts_sinbarra_off)" >> $LOG
  exit 0
fi

antes=$(pidof com.android.systemui)
ov enable
echo "$(date '+%F %T') SystemUI pid $antes" >> $LOG

# Vigilar SystemUI un minuto: cambiar de overlay lo reinicia una vez (normal); más de 2 reinicios = problema
reinicios=0; ultimo=$antes
for i in $(seq 1 30); do
  sleep 2
  ahora=$(pidof com.android.systemui)
  if [ "$ahora" != "$ultimo" ]; then reinicios=$((reinicios + 1)); ultimo=$ahora; fi
  if [ $reinicios -gt 2 ]; then
    ov disable
    echo "$(date '+%F %T') SystemUI se reinició $reinicios veces: overlay APAGADA por seguridad" >> $LOG
    exit 0
  fi
done
echo "$(date '+%F %T') todo bien ($reinicios reinicio(s) de SystemUI)" >> $LOG
