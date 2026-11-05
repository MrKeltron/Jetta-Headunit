# Drehzahl tablet toolkit: install the newest local APK, then tap through every screen and screenshot each one.
# Screenshots go to reports\tour\. Coordinates assume a 1280 x 800 landscape screen at 160 dpi.
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'Connect.ps1')
$Script:AutoYes = $true
$out = Join-Path $Script:Reports 'tour'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$done = Join-Path $out 'done.txt'

function Tap { param([int]$X, [int]$Y, [double]$Wait = 1.2) Adb-Shell ('input tap ' + $X + ' ' + $Y) | Out-Null; Start-Sleep -Milliseconds ([int]($Wait * 1000)) }
function Shot {
    param([string]$Name)
    Adb-Shell 'screencap -p /data/local/tmp/tour.png' | Out-Null
    Invoke-Adb @('pull', '/data/local/tmp/tour.png', (Join-Path $out ($Name + '.png'))) | Out-Null
    Say ('  ' + $Name)
}
function Rail { param([int]$I) Tap 58 (66 + [int](111.4 * $I)) 1.5 }
function Menu { param([int]$I) Tap 266 (120 + 68 * $I) 1.2 }

if (-not (Connect-Tablet)) { Set-Content $done 'NOT CONNECTED'; exit }
$s = Get-Settings
$pkg = $s['AppPackage']
$apk = Get-ChildItem -Path $Script:ApkFolder -Filter 'Drehzahl*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($apk) { Say ('Installing ' + $apk.Name); Say ('  ' + (Invoke-Adb @('install', '-r', '-g', $apk.FullName)).Text.Trim()) }
Adb-Shell ('am force-stop ' + $pkg) | Out-Null
Adb-Shell ('am start -n ' + $pkg + '/.MainActivity') | Out-Null
Start-Sleep -Seconds 3

Step 'Screens'
Rail 0; Start-Sleep 2; Shot '01-home'
Rail 1; Shot '02-media'
Rail 2; Shot '03-carplay'
Rail 3; Start-Sleep 2; Shot '04-car-performance'
Menu 1; Start-Sleep 1; Shot '05-car-gmeter'
Menu 2; Shot '06-car-laptimer'
Menu 3; Start-Sleep 1; Shot '07-car-trips'
Menu 4; Shot '08-car-service'
Menu 5; Shot '09-car-profile'
Menu 6; Shot '10-car-source'
Rail 4; Shot '11-sound-eq'
Menu 1; Shot '12-sound-tone'
Menu 2; Shot '13-sound-balance'
Menu 3; Shot '14-sound-enhancements'
Menu 4; Shot '15-sound-volume'
Menu 5; Shot '16-sound-engine'
Rail 5; Start-Sleep 2; Shot '17-apps'
Rail 6; Shot '18-settings-display'
Menu 2; Shot '19-settings-units'
Menu 4; Shot '20-settings-home'
Menu 6; Shot '21-settings-info'
Menu 7; Shot '22-settings-terms'
Menu 8; Shot '23-settings-credits'
Rail 0
$crash = (Invoke-Adb @('logcat', '-d', '-b', 'crash')).Text
Set-Content -Path (Join-Path $out 'crash-log.txt') -Value $crash -Encoding UTF8
Set-Content -Path $done -Value 'OK' -Encoding ASCII
Say 'Tour finished. This window closes in 5 seconds.' Green
Start-Sleep -Seconds 5
