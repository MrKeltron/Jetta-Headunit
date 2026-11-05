# Drehzahl tablet toolkit: main menu
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'Connect.ps1')
. (Join-Path $PSScriptRoot 'Report.ps1')
. (Join-Path $PSScriptRoot 'Debloat.ps1')
. (Join-Path $PSScriptRoot 'Install.ps1')

try { Start-Transcript -Path (Join-Path $Script:Reports 'toolkit-log.txt') -Append | Out-Null } catch { }

$menu = [ordered]@{
    '1' = @('Connect to the tablet',                  { Connect-Tablet | Out-Null })
    '2' = @('Tablet report (specs + app list)',       { New-TabletReport })
    '3' = @('Debloat the tablet',                     { Start-Debloat })
    '4' = @('Install / update apps and permissions',  { Start-InstallMenu })
    '5' = @('Undo debloat (restore apps)',            { Start-Undo })
    '6' = @('Screenshot the tablet',                  { Save-Screenshot })
    '7' = @('Save the tablet log (after a crash)',    { Save-CrashLog })
}

while ($true) {
    Clear-Host
    Say '  DREHZAHL  tablet toolkit' Red
    Say '  ------------------------' DarkGray
    $state = 'not connected'
    if ($Script:Serial) { $state = 'connected to ' + $Script:Serial }
    Say ('  Tablet: ' + $state) DarkGray
    Say ''
    foreach ($k in $menu.Keys) { Say ('  ' + $k + '   ' + $menu[$k][0]) White }
    Say '  Q   Quit' White
    Say ''
    $pick = (Read-Host '  Pick a number').Trim().ToUpper()
    if ($pick -eq 'Q') { break }
    if (-not $menu.Contains($pick)) { continue }
    try {
        & $menu[$pick][1]
    } catch {
        Say ''
        Say ('Something went wrong: ' + $_.Exception.Message) Red
        Say 'The full details are in reports\toolkit-log.txt. Share that file if it keeps happening.' DarkGray
        $_ | Out-String | Write-Host -ForegroundColor DarkGray
    }
    Pause-Key
}

try { Stop-Transcript | Out-Null } catch { }
