# Diagnóstico de CarPlay (FT150TS + DiPlay) en el teléfono conectado por USB.
# SOLO LEE: no instala, no desinstala, no borra datos ni cambia ajustes.
# Uso (PowerShell, desde la carpeta del proyecto):  powershell -ExecutionPolicy Bypass -File scripts\diagnostico-carplay.ps1

$ErrorActionPreference = 'Continue'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$apksigner = Join-Path $sdk 'build-tools\36.0.0\apksigner.bat'
if (-not $env:JAVA_HOME) {
    foreach ($j in @("$env:USERPROFILE\.jdks\jbr-21.0.11", 'C:\Program Files\Android\Android Studio\jbr')) {
        if (Test-Path "$j\bin\java.exe") { $env:JAVA_HOME = $j; break }
    }
}
$tmp = Join-Path $env:TEMP 'ft150ts-diag'
New-Item -ItemType Directory -Force $tmp | Out-Null

# Firma de este PC (la llave debug con la que se compila FT150TS) y la de los releases de DiPlay
$FIRMA_FT150TS = '3efd04bd89e6560d5fa9b34c9dd63e19e59c705de157a9ec382fc3df02e4ab65'
$FIRMA_DIPLAY = '87b38b12788dcb202a961215f2572e30ec2dc9d8ef4bc070d05f77e49291a363'

function Titulo($t) { Write-Host ''; Write-Host "== $t ==" }
function Sh($c) { (& $adb shell $c 2>$null) -join "`n" }

Titulo 'Teléfono'
$dispositivos = (& $adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (-not $dispositivos) { Write-Host 'No hay teléfono conectado (o no aceptaste la depuración USB en el teléfono).'; exit 1 }
Write-Host ("Modelo:            " + (Sh 'getprop ro.product.model') + ' (' + (Sh 'getprop ro.product.device') + ')')
Write-Host ("Android:           " + (Sh 'getprop ro.build.version.release') + ' (API ' + (Sh 'getprop ro.build.version.sdk') + ')')
Write-Host ("HyperOS / MIUI:    " + (Sh 'getprop ro.mi.os.version.name') + ' ' + (Sh 'getprop ro.miui.ui.version.name'))
Write-Host ("USB host (OTG):    " + (Sh 'pm has-feature android.hardware.usb.host'))
Write-Host ("Wi-Fi Direct:      " + (Sh 'pm has-feature android.hardware.wifi.direct'))

function Paquete($pkg, $firmaEsperada, $nombre) {
    Titulo $nombre
    $ruta = (Sh "pm path $pkg") -split "`n" | Where-Object { $_ -like 'package:*base.apk' } | Select-Object -First 1
    if (-not $ruta) { Write-Host "$pkg no está instalado."; return }
    $info = Sh "dumpsys package $pkg"
    $ver = ([regex]::Match($info, 'versionName=(\S+)')).Groups[1].Value
    $code = ([regex]::Match($info, 'versionCode=(\d+)')).Groups[1].Value
    Write-Host "Paquete:           $pkg  versión $ver (code $code)"
    $local = Join-Path $tmp "$pkg.apk"
    & $adb pull ($ruta -replace '^package:', '') $local 2>$null | Out-Null
    $firma = ((& $apksigner verify --print-certs $local 2>$null) | Select-String 'SHA-256 digest' | Select-Object -First 1) -replace '.*: ', ''
    $ok = if ($firma.Trim() -eq $firmaEsperada) { 'COINCIDE' } else { 'NO COINCIDE' }
    Write-Host "Firma (SHA-256):   $firma  -> $ok"
    return $code
}

$codeFt = Paquete 'com.xof.ft150ts' $FIRMA_FT150TS 'FT150TS instalado'
if ($codeFt) {
    if ([int]$codeFt -gt 25) { Write-Host 'OJO: el teléfono tiene una versión MÁS NUEVA (code > 25). Instalar esta encima sería bajar de versión: no lo hagas sin respaldo.' }
    Write-Host 'Si la firma COINCIDE, Run en Android Studio o "adb install -r" actualiza sin borrar datos.'
    Write-Host 'Si NO COINCIDE, NO desinstales: haz un respaldo en Ajustes > Respaldo y avísame antes de seguir.'
}
Paquete 'com.shihab.diplay' $FIRMA_DIPLAY 'DiPlay instalado' | Out-Null
Paquete 'com.shihab.diplay.hudtest' $FIRMA_DIPLAY 'DiPlay (versión de pruebas)' | Out-Null

Titulo 'Servicio del mapa de DiPlay (necesario para el mapa dentro de FT150TS)'
Write-Host (Sh 'cmd package query-services --brief -a com.shihab.diplay.action.EMBED_MAP')

Titulo 'Pantalla de CarPlay de DiPlay (el mapa solo llega mientras está abierta)'
$act = (Sh 'dumpsys activity activities') -split "`n" | Select-String 'CarPlayHostActivity' | Select-Object -First 3
if ($act) { $act | ForEach-Object { Write-Host $_.ToString().Trim() } } else { Write-Host 'No está abierta. Ábrela con el botón CarPlay de FT150TS o desde DiPlay.' }

Titulo 'Registro reciente (FT150TS y DiPlay)'
& $adb logcat -d -t 300 -s FT150-CarPlay DiPlay-MapEmbed 2>$null | Select-Object -Last 40

Write-Host ''
Write-Host "APK copiados para revisar la firma: $tmp (puedes borrarlos)."
