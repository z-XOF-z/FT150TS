# Diagnóstico de energía y root del teléfono conectado por USB (Fase A del ahorro nocturno).
# SOLO LEE: no apaga, no reinicia, no escribe en /sys, no cambia ajustes.
# Las líneas con "su" piden permiso en Magisk la primera vez (Shell / com.android.shell).
# Uso (PowerShell, desde la carpeta del proyecto):  powershell -ExecutionPolicy Bypass -File scripts\diagnostico-energia.ps1

$ErrorActionPreference = 'Continue'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
function Titulo($t) { Write-Host ''; Write-Host "== $t ==" }
function Sh($c) { (& $adb shell $c 2>$null) -join "`n" }
function Su($c) { (& $adb shell "su -c '$c'" 2>$null) -join "`n" }

$dispositivos = (& $adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (-not $dispositivos) { Write-Host 'No hay teléfono conectado (o no aceptaste la depuración USB).'; exit 1 }

Titulo 'Sistema'
Write-Host ("Modelo:          " + (Sh 'getprop ro.product.model') + ' (' + (Sh 'getprop ro.product.device') + ')')
Write-Host ("Android:         " + (Sh 'getprop ro.build.version.release') + ' (API ' + (Sh 'getprop ro.build.version.sdk') + ')')
Write-Host ("HyperOS / MIUI:  " + (Sh 'getprop ro.mi.os.version.name') + ' ' + (Sh 'getprop ro.miui.ui.version.name'))
Write-Host ("Build:           " + (Sh 'getprop ro.build.fingerprint'))
Write-Host ("Kernel:          " + (Sh 'uname -r'))
Write-Host ("Bootloader:      verifiedbootstate=" + (Sh 'getprop ro.boot.verifiedbootstate') + ' flash.locked=' + (Sh 'getprop ro.boot.flash.locked'))
Write-Host ("Último arranque: " + (Sh 'getprop ro.boot.bootreason') + ' / ' + (Sh 'getprop sys.boot.reason'))

Titulo 'Root'
$id = Su 'id'
if ($id -notmatch 'uid=0') { Write-Host 'su no respondió como root (¿Magisk negó a Shell?). Lo de abajo que dice "su" saldrá vacío.' }
else { Write-Host "su: $id" }
Write-Host ("Magisk:          " + (Su 'magisk -v; magisk -V'))
Write-Host ("SELinux:         " + (Sh 'getenforce') + '  (no se cambia)')

Titulo 'RTC y encendido desde apagado (solo lectura)'
Write-Host ("RTC:             " + (Su 'cat /sys/class/rtc/rtc0/name') + '  wakealarm=' + (Su '[ -e /sys/class/rtc/rtc0/wakealarm ] && cat /sys/class/rtc/rtc0/wakealarm; echo .'))
Write-Host ("poweron_alarm:   " + (Su 'cat /sys/module/qpnp_rtc/parameters/poweron_alarm 2>/dev/null || echo no-existe'))
Write-Host ("alarm-pwrup (DT):" + (Su 'f=$(find /proc/device-tree -name qcom,qpnp-rtc-alarm-pwrup 2>/dev/null | head -1); [ -n $f ] && od -An -tx1 $f || echo no-existe'))
Write-Host ("Drivers RTC:     " + (Su 'ls /sys/bus/platform/drivers 2>/dev/null | grep -i rtc | xargs'))
Write-Host ("Xiaomi:          " + (((Sh 'dumpsys package com.miui.securitycenter') | Select-String -AllMatches 'com\.miui\.[A-Za-z.]*(PowerOnOff|ScheduledPower|SchedulePower|AutoTask)[A-Za-z.]*' | ForEach-Object { $_.Matches.Value } | Sort-Object -Unique) -join ' '))

Titulo 'Ahorro de Android'
Write-Host ("Ahorro de batería (low_power): " + (Sh 'settings get global low_power'))
Write-Host ("Doze:            " + (((Sh 'dumpsys deviceidle') -split "`n" | Select-String -Pattern 'mState=|mLightState=|mForceIdle=' | ForEach-Object { $_.ToString().Trim() }) -join '  '))
Write-Host ("Lista blanca:    " + ((Sh 'dumpsys deviceidle whitelist') -split "`n" | Select-String 'ft150ts|diplay' | ForEach-Object { $_.ToString().Trim() }) -join ' ')
Write-Host ("Grupos de espera FT150TS / DiPlay: " + (Sh 'am get-standby-bucket com.xof.ft150ts') + ' / ' + (Sh 'am get-standby-bucket com.shihab.diplay'))
Write-Host ("Alarmas exactas FT150TS: " + (Sh 'cmd appops get com.xof.ft150ts SCHEDULE_EXACT_ALARM'))

Titulo 'Ahorro nocturno de FT150TS (lo que la app registró)'
& $adb logcat -d -t 400 2>$null | Select-String 'Nocturno|FT150' | Select-Object -Last 15 | ForEach-Object { Write-Host $_.ToString() }
Write-Host ''
Write-Host 'Para el veredicto completo: FT150TS → Ajustes → Ahorro nocturno → Diagnóstico.'
