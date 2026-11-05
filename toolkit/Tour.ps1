# Drehzahl tablet toolkit: install the newest local APK, then capture every theme and its main screens.
# Uses Drehzahl's own launch extras (style, open, page, intro), so it works on any screen size.
# Screenshots go to reports\tour\.
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'Connect.ps1')
$Script:AutoYes = $true
$out = Join-Path $Script:Reports 'tour'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$done = Join-Path $out 'done.txt'

function Shot {
    param([string]$Name)
    Adb-Shell 'screencap -p /data/local/tmp/tour.png' | Out-Null
    Invoke-Adb @('pull', '/data/local/tmp/tour.png', (Join-Path $out ($Name + '.png'))) | Out-Null
    Say ('  ' + $Name)
}
function Open {
    param([string]$Extras, [double]$Wait = 2)
    Adb-Shell ('am start -n ' + $Script:Pkg + '/.MainActivity ' + $Extras) | Out-Null
    Start-Sleep -Milliseconds ([int]($Wait * 1000))
}

if (-not (Connect-Tablet)) { Set-Content $done 'NOT CONNECTED'; exit }
$s = Get-Settings
$Script:Pkg = $s['AppPackage']
$apk = Get-ChildItem -Path $Script:ApkFolder -Filter 'Drehzahl*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($apk) { Say ('Installing ' + $apk.Name); Say ('  ' + (Invoke-Adb @('install', '-r', '-g', $apk.FullName)).Text.Trim()) }
Adb-Shell ('logcat -c') | Out-Null
Adb-Shell ('am force-stop ' + $Script:Pkg) | Out-Null
Adb-Shell ('am start -n ' + $Script:Pkg + '/.MainActivity') | Out-Null
Start-Sleep -Milliseconds 1600
Shot '00-cold-start-intro'
Start-Sleep -Seconds 3

$names = @('gli', 'carbon', 'simple', 'code', 'classic')
for ($i = 0; $i -lt $names.Count; $i++) {
    $n = $names[$i]
    Step ('Theme ' + $n)
    Open ('--ei style ' + $i) 2.5
    Shot ($n + '-1-home')
    Open '--ez intro true' 1.3
    Shot ($n + '-2-intro')
    Start-Sleep -Seconds 3
    Open '--ei open 1 --ei page 0'
    Shot ($n + '-3-media')
    Open '--ei open 1 --ei page 3'
    Shot ($n + '-4-output')
    Open '--ei open 7 --ei page 0' 2.5
    Shot ($n + '-5-performance')
    Open '--ei open 8' 2.5
    Shot ($n + '-6-widgets')
    Open '--ei open 4 --ei page 0'
    Shot ($n + '-7-sound')
    Open '--ei open 6 --ei page 0'
    Shot ($n + '-8-settings')
}
Step 'OBD2 screens (GLI theme)'
Open '--ei style 0 --ei source 2' 3
Open '--ei open 3 --ei page 4' 3
Shot 'obd-1-data-source'
Open '--ei open 3 --ei page 1' 2
Shot 'obd-2-trouble-codes'
Open '--ei open 7 --ei page 0' 2
Shot 'obd-3-performance-no-adapter'
Open '--ei source 0' 1
Open '--ei style 0' 2
$crash = (Invoke-Adb @('logcat', '-d', '-b', 'crash')).Text
Set-Content -Path (Join-Path $out 'crash-log.txt') -Value $crash -Encoding UTF8
$errs = (Invoke-Adb @('logcat', '-d', '-s', 'AndroidRuntime:E', 'System.err:W')).Text
Set-Content -Path (Join-Path $out 'errors.txt') -Value $errs -Encoding UTF8
Set-Content -Path $done -Value 'OK' -Encoding ASCII
Say 'Tour finished. This window closes in 5 seconds.' Green
Start-Sleep -Seconds 5
