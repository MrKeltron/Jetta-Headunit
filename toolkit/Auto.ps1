# Drehzahl tablet toolkit: install and set up everything without asking questions.
# Used by "Install (no questions).bat". Pairing must already be done (START HERE.bat, option 1).
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'Connect.ps1')
. (Join-Path $PSScriptRoot 'Report.ps1')
. (Join-Path $PSScriptRoot 'Install.ps1')
. (Join-Path $PSScriptRoot 'Debloat.ps1')

$Script:AutoYes = $true
$done = Join-Path $Script:Reports 'auto-done.txt'
if (Test-Path $done) { Remove-Item $done -Force }
try { Start-Transcript -Path (Join-Path $Script:Reports 'auto-log.txt') -Force | Out-Null } catch { }
$result = 'FAILED'
try {
    if (Connect-Tablet) {
        $result = 'OK'
        foreach ($stepName in @('Restore-KeptPackages', 'Install-Drehzahl', 'Install-CarLink', 'Open-IconPack')) {
            try { & $stepName } catch { Say ($stepName + ' failed: ' + $_.Exception.Message) Red; $result = 'PARTIAL' }
        }
        Adb-Shell 'input keyevent KEYCODE_HOME' | Out-Null
        Start-Sleep -Seconds 4
        Save-Screenshot
    }
} catch {
    Say ('Something went wrong: ' + $_.Exception.Message) Red
}
try { Stop-Transcript | Out-Null } catch { }
Set-Content -Path $done -Value $result -Encoding ASCII
Say ''
Say ('Finished: ' + $result + '. This window closes in 10 seconds.') Green
Start-Sleep -Seconds 10
