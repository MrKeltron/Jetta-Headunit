# Drehzahl tablet toolkit: run the steps listed in toolkit\steps.txt, one per line, and save the results.
#   INSTALL            install the newest APK from the apk folder
#   SHELL <command>    run an adb shell command (use single quotes inside, not double)
#   WAIT <seconds>     pause
#   TAP <x> <y>        tap the tablet screen
#   SHOT <name>        screenshot into reports\steps\<name>.png
#   OBDLOG             copy Drehzahl's obd-log.txt
#   LOGCAT             save recent warnings and errors
# Output goes to reports\steps\output.txt.
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'Connect.ps1')
$Script:AutoYes = $true
$out = Join-Path $Script:Reports 'steps'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$log = Join-Path $out 'output.txt'
$done = Join-Path $out 'done.txt'

function Out-Log {
    param([string]$Text)
    Add-Content -Path $log -Value $Text -Encoding UTF8
    Say $Text
}

if (-not (Connect-Tablet)) { Set-Content $done 'NOT CONNECTED'; exit }
$pkg = (Get-Settings)['AppPackage']
foreach ($raw in Get-Content (Join-Path $Script:Kit 'steps.txt')) {
    $line = $raw.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { continue }
    $sp = $line.IndexOf(' ')
    $op = $line
    $arg = ''
    if ($sp -gt 0) { $op = $line.Substring(0, $sp); $arg = $line.Substring($sp + 1) }
    Out-Log ('### ' + $line)
    switch ($op.ToUpper()) {
        'INSTALL' {
            $apk = Get-ChildItem -Path $Script:ApkFolder -Filter 'Drehzahl*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
            Out-Log ('installing ' + $apk.Name)
            Out-Log (Invoke-Adb @('install', '-r', '-g', $apk.FullName)).Text
        }
        'SHELL' { Out-Log (Adb-Shell $arg) }
        'WAIT' { Start-Sleep -Milliseconds ([int]([double]$arg * 1000)) }
        'TAP' { Adb-Shell ('input tap ' + $arg) | Out-Null }
        'SHOT' {
            Adb-Shell 'screencap -p /data/local/tmp/step.png' | Out-Null
            Invoke-Adb @('pull', '/data/local/tmp/step.png', (Join-Path $out ($arg + '.png'))) | Out-Null
        }
        'OBDLOG' {
            Invoke-Adb @('pull', ('/sdcard/Android/data/' + $pkg + '/files/obd-log.txt'), (Join-Path $out 'obd-log.txt')) | Out-Null
        }
        'LOGCAT' { Out-Log (Invoke-Adb @('logcat', '-d', '-t', '500', '*:W')).Text }
        default { Out-Log ('unknown step ' + $op) }
    }
}
Set-Content -Path $done -Value 'OK' -Encoding ASCII
Say 'Steps finished. This window closes in 3 seconds.' Green
Start-Sleep -Seconds 3
