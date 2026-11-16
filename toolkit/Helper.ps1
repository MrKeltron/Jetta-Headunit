# Drehzahl tablet toolkit: start the Wi-Fi helper on the tablet (needed again after every tablet restart).
# Used by "Start Wi-Fi helper.bat".
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'Connect.ps1')
. (Join-Path $PSScriptRoot 'Install.ps1')
try {
    if (Connect-Tablet) { Start-WifiHelper | Out-Null }
} catch {
    Say ('Something went wrong: ' + $_.Exception.Message) Red
}
Say ''
Say 'This window closes in 10 seconds.'
Start-Sleep -Seconds 10
