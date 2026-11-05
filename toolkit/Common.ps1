# Drehzahl tablet toolkit: shared helpers (settings, logging, ADB)
# Works in Windows PowerShell 5.1 and PowerShell 7. ASCII only on purpose.

$ErrorActionPreference = 'Stop'
$Script:Kit          = $PSScriptRoot
$Script:Root         = Split-Path -Parent $PSScriptRoot
$Script:Reports      = Join-Path $Script:Root 'reports'
$Script:ApkFolder    = Join-Path $Script:Root 'apk'
$Script:SettingsFile = Join-Path $Script:Kit 'settings.ini'
$Script:Adb          = $null
$Script:Serial       = $null
$Script:AutoYes      = $false   # true = answer Y to every question (used by Install-NoQuestions)

foreach ($dir in @($Script:Reports, $Script:ApkFolder)) {
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
}

function Say {
    param([string]$Text, [string]$Color = 'Gray')
    Write-Host $Text -ForegroundColor $Color
}

function Step {
    param([string]$Text)
    Write-Host ''
    Write-Host ('== ' + $Text) -ForegroundColor Cyan
}

function Ask-YesNo {
    param([string]$Question)
    if ($Script:AutoYes) { Say ($Question + ' [Y/N]: Y (automatic)'); return $true }
    while ($true) {
        $a = Read-Host ($Question + ' [Y/N]')
        if ($a -match '^[Yy]') { return $true }
        if ($a -match '^[Nn]') { return $false }
    }
}

function Pause-Key {
    Write-Host ''
    Read-Host 'Press Enter to go back to the menu' | Out-Null
}

function Get-Settings {
    $s = @{}
    if (Test-Path $Script:SettingsFile) {
        foreach ($line in Get-Content $Script:SettingsFile) {
            if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
            $i = $line.IndexOf('=')
            $s[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
        }
    }
    return $s
}

function Save-Setting {
    param([string]$Key, [string]$Value)
    $lines = @()
    $found = $false
    if (Test-Path $Script:SettingsFile) { $lines = @(Get-Content $Script:SettingsFile) }
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ('^\s*' + [regex]::Escape($Key) + '\s*=')) {
            $lines[$i] = $Key + '=' + $Value
            $found = $true
        }
    }
    if (-not $found) { $lines += ($Key + '=' + $Value) }
    Set-Content -Path $Script:SettingsFile -Value $lines -Encoding ASCII
}

function Get-Stamp { return (Get-Date -Format 'yyyyMMdd-HHmmss') }

# ---------- ADB ----------

function Get-AdbPath {
    $local = Join-Path $Script:Kit 'platform-tools\adb.exe'
    if (Test-Path $local) { return $local }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Install-Adb {
    Say 'ADB is needed and was not found on this PC.' Yellow
    Say 'ADB is part of Google''s Android "platform-tools" (about 7 MB).'
    Say ('It goes into: ' + (Join-Path $Script:Kit 'platform-tools'))
    Say 'Nothing is installed system-wide and no admin rights are needed.'
    if (-not (Ask-YesNo 'Download platform-tools from dl.google.com now?')) {
        throw 'ADB is required for this step. Nothing was changed.'
    }
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $zip = Join-Path $env:TEMP 'platform-tools-latest-windows.zip'
    Say 'Downloading...'
    $old = $ProgressPreference; $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
    $ProgressPreference = $old
    Say 'Unpacking...'
    Expand-Archive -Path $zip -DestinationPath $Script:Kit -Force
    Remove-Item $zip -Force
    Say 'ADB is ready.' Green
}

function Ensure-Adb {
    if ($Script:Adb) { return }
    $p = Get-AdbPath
    if (-not $p) { Install-Adb; $p = Get-AdbPath }
    if (-not $p) { throw 'ADB still not found after download.' }
    $Script:Adb = $p
}

# Runs adb and returns an object with .Text (all output) and .Code (exit code).
function Invoke-Adb {
    param([string[]]$Arguments, [switch]$NoSerial)
    Ensure-Adb
    $all = @()
    if ((-not $NoSerial) -and $Script:Serial) { $all += @('-s', $Script:Serial) }
    $all += $Arguments
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $out = & $Script:Adb @all 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $old
    return [pscustomobject]@{ Text = (($out | Where-Object { $_ -ne $null }) -join "`n"); Code = $code }
}

function Adb-Shell {
    param([string]$Command)
    return (Invoke-Adb @('shell', $Command)).Text
}

function Require-Tablet {
    if ($Script:Serial -and (Test-Serial $Script:Serial)) { return }
    if (-not (Connect-Tablet)) { throw 'Not connected to the tablet. Use option 1 first.' }
}
