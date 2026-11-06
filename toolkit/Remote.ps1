# Drehzahl tablet toolkit: run the steps listed in toolkit\steps.txt, one per line, and save the results.
#   DEVICE wifi|usb|<serial>   pick the tablet for the following steps (default: wifi).
#                              Prefer the serial when more than one tablet is plugged in.
#   INSTALL                    install the newest Drehzahl APK from the apk folder
#   APK <file>                 install any APK from the apk folder (wildcards allowed)
#   SHELL <command>            run an adb shell command (use single quotes inside, not double)
#   SAVE <name> <command>      run an adb shell command, output into reports\steps\<name>.txt
#   ROOT <command>             run a command as root (su). No single quotes inside.
#   RSAVE <name> <command>     like SAVE, as root
#   PUSH <file> <tablet path>  copy a file from this folder to the tablet
#   RUN <toolkit function>     run a toolkit step, for example Install-CarLink
#   WAIT <seconds>             pause
#   TAP <x> <y>                tap the tablet screen
#   SHOT <name>                screenshot into reports\steps\<name>.png
#   TAPTEXT <text>             tap the on-screen item showing this text (scrolls to find it)
#   TOGGLE on|off <text>       set the switch next to this text, only tapping if it needs to change
#   PULL <tablet path> <name>  copy a file from the tablet into reports\steps
#   UNLESS <text> => <step>    run the step only when this text is not on screen
#   THEN <step>                run the step only if the last UNLESS ran its step
#   REQUIRE <text>             if this text is not on screen, skip the rest of the steps for this tablet
#   WAITBOOT <seconds>         wait for the tablet to finish restarting
#   REMOVE <pkg> [pkg ...]     remove apps for the main user (undo with START HERE.bat, debloat undo)
#   OBDLOG                     copy Drehzahl's obd-log.txt
#   LOGCAT                     save recent warnings and errors
# Output goes to reports\steps\output.txt.
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'Connect.ps1')
. (Join-Path $PSScriptRoot 'Report.ps1')
. (Join-Path $PSScriptRoot 'Install.ps1')
. (Join-Path $PSScriptRoot 'Debloat.ps1')
$Script:AutoYes = $true
$out = Join-Path $Script:Reports 'steps'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$log = Join-Path $out 'output.txt'
$done = Join-Path $out 'done.txt'

$Script:StepLog = $log
function Say {
    param([string]$Text, [string]$Color = 'Gray')
    Write-Host $Text -ForegroundColor $Color
    if ($Script:StepLog) { Add-Content -Path $Script:StepLog -Value $Text -Encoding UTF8 }
}

function Out-Log {
    param([string]$Text)
    Say $Text
}

function Save-Text {
    param([string]$Name, [string]$Text)
    Set-Content -Path (Join-Path $out ($Name + '.txt')) -Value $Text -Encoding UTF8
    Out-Log ('saved ' + $Name + '.txt (' + $Text.Length + ' characters)')
}

function Test-NetworkSerial {
    param([string]$Serial)
    return ($Serial -match ':' -or $Serial -match '_adb-tls')
}

function Select-Device {
    param([string]$Which)
    Out-Log (Invoke-Adb -NoSerial @('devices', '-l')).Text
    $Script:Serial = $null
    $w = $Which.Trim().ToLower()
    if ($w -eq '' -or $w -eq 'wifi') {
        return (Connect-Tablet)
    }
    $all = @(Get-ConnectedDevices)
    if ($w -eq 'usb') {
        $wired = @($all | Where-Object { -not (Test-NetworkSerial $_) })
        if ($wired.Count -gt 1) {
            Out-Log ('More than one tablet is plugged in (' + ($wired -join ', ') + '). Use DEVICE <serial>.')
            return $false
        }
        if ($wired.Count -eq 1) { $Script:Serial = $wired[0] }
    } else {
        foreach ($d in $all) { if ($d -eq $Which.Trim()) { $Script:Serial = $d } }
    }
    if (-not $Script:Serial) {
        Out-Log ('No device ready for ' + $Which + '. If the tablet shows "Allow USB debugging?", tap Allow.')
        return $false
    }
    $model = (Adb-Shell 'getprop ro.product.model').Trim()
    $ver = (Adb-Shell 'getprop ro.build.version.release').Trim()
    Out-Log ('Using ' + $Script:Serial + ': ' + $model + ' (Android ' + $ver + ')')
    return $true
}

function Root-Shell {
    param([string]$Command)
    return (Adb-Shell ("su -c '" + $Command + "'"))
}

function Split-First {
    param([string]$Text)
    $i = $Text.IndexOf(' ')
    if ($i -lt 0) { return @($Text, '') }
    return @($Text.Substring(0, $i), $Text.Substring($i + 1))
}


function Get-UiDoc {
    for ($i = 0; $i -lt 3; $i++) {
        $r = Adb-Shell 'uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; cat /data/local/tmp/ui.xml'
        $start = $r.IndexOf('<?xml')
        if ($start -ge 0) {
            $end = $r.LastIndexOf('</hierarchy>')
            if ($end -gt $start) {
                try { return [xml]$r.Substring($start, $end - $start + 12) } catch { }
            }
        }
        Start-Sleep -Milliseconds 900
    }
    return $null
}

function Find-UiNode {
    param($Doc, [string]$Text)
    $all = $Doc.SelectNodes('//node')
    $t = $Text.ToLower().Trim()
    foreach ($mode in 0..2) {
        foreach ($n in $all) {
            foreach ($v in @($n.GetAttribute('text'), $n.GetAttribute('content-desc'))) {
                if (-not $v) { continue }
                $lv = $v.ToLower().Trim()
                if (($mode -eq 0 -and $lv -eq $t) -or ($mode -eq 1 -and $lv.StartsWith($t)) -or ($mode -eq 2 -and $lv.Contains($t))) { return $n }
            }
        }
    }
    return $null
}

function Get-Box {
    param($Node)
    if ($Node.GetAttribute('bounds') -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        return @([int]$Matches[1], [int]$Matches[2], [int]$Matches[3], [int]$Matches[4])
    }
    return $null
}

function Get-Checked {
    param($Node)
    $n = $Node
    for ($i = 0; $i -lt 3 -and $n -ne $null -and $n.Name -eq 'node'; $i++) {
        if ($n.GetAttribute('checkable') -eq 'true') { return ($n.GetAttribute('checked') -eq 'true') }
        $kids = @($n.SelectNodes('./node | ./node/node') | Where-Object { $_.GetAttribute('checkable') -eq 'true' })
        if ($kids.Count -eq 1) { return ($kids[0].GetAttribute('checked') -eq 'true') }
        $n = $n.ParentNode
    }
    return $null
}

function Find-OnScreen {
    param([string]$Text, [int]$MaxScroll = 4)
    for ($scroll = 0; $scroll -le $MaxScroll; $scroll++) {
        $doc = Get-UiDoc
        if (-not $doc) { Out-Log 'could not read the screen layout'; return $null }
        $n = Find-UiNode $doc $Text
        if ($n) { return $n }
        if ($scroll -eq $MaxScroll) { break }
        $root = Get-Box ($doc.SelectSingleNode('//node'))
        if (-not $root) { return $null }
        $x = [int](($root[0] + $root[2]) / 2)
        $y1 = [int]($root[1] + ($root[3] - $root[1]) * 0.78)
        $y2 = [int]($root[1] + ($root[3] - $root[1]) * 0.30)
        Adb-Shell ('input swipe ' + $x + ' ' + $y1 + ' ' + $x + ' ' + $y2 + ' 350') | Out-Null
        Start-Sleep -Milliseconds 900
    }
    return $null
}

function Tap-Node {
    param($Node)
    $b = Get-Box $Node
    $x = [int](($b[0] + $b[2]) / 2); $y = [int](($b[1] + $b[3]) / 2)
    Adb-Shell ('input tap ' + $x + ' ' + $y) | Out-Null
    return ($x.ToString() + ',' + $y)
}

function Tap-Text {
    param([string]$Text)
    $n = Find-OnScreen $Text
    if (-not $n) { Out-Log ('not on screen: ' + $Text); return }
    $label = $n.GetAttribute('text'); if (-not $label) { $label = $n.GetAttribute('content-desc') }
    Out-Log ('tapped "' + $label + '" at ' + (Tap-Node $n))
}

function Set-Toggle {
    param([string]$Arg)
    $p = Split-First $Arg
    $want = ($p[0].ToLower() -eq 'on')
    $n = Find-OnScreen $p[1]
    if (-not $n) { Out-Log ('not on screen: ' + $p[1]); return }
    $now = Get-Checked $n
    if ($now -eq $null) { Out-Log ('no switch found next to "' + $p[1] + '"'); return }
    if ($now -eq $want) { Out-Log ('"' + $p[1] + '" already ' + $p[0]); return }
    Out-Log ('switching "' + $p[1] + '" ' + $p[0] + ' at ' + (Tap-Node $n))
    Start-Sleep -Milliseconds 1200
    $n2 = Find-UiNode (Get-UiDoc) $p[1]
    if ($n2) { Out-Log ('now ' + $(if (Get-Checked $n2) { 'on' } else { 'off' })) }
}

function Invoke-Step {
    param([string]$op, [string]$arg)
    switch ($op) {
        'INSTALL' {
            $apk = Get-ChildItem -Path $Script:ApkFolder -Filter 'Drehzahl*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
            Out-Log ('installing ' + $apk.Name)
            Out-Log (Invoke-Adb @('install', '-r', '-g', $apk.FullName)).Text
        }
        'APK' {
            $apk = Get-ChildItem -Path $Script:ApkFolder -Filter $arg | Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if (-not $apk) { Out-Log ('no APK matches ' + $arg); break }
            Out-Log ('installing ' + $apk.Name)
            Out-Log (Invoke-Adb @('install', '-r', '-g', $apk.FullName)).Text
        }
        'SHELL' { Out-Log (Adb-Shell $arg) }
        'SAVE' { $p = Split-First $arg; Save-Text $p[0] (Adb-Shell $p[1]) }
        'ROOT' { Out-Log (Root-Shell $arg) }
        'RSAVE' { $p = Split-First $arg; Save-Text $p[0] (Root-Shell $p[1]) }
        'PUSH' {
            $p = Split-First $arg
            Out-Log (Invoke-Adb @('push', (Join-Path $Script:Root $p[0]), $p[1])).Text
        }
        'RUN' { & $arg.Trim() }
        'WAIT' { Start-Sleep -Milliseconds ([int]([double]$arg * 1000)) }
        'TAP' { Adb-Shell ('input tap ' + $arg) | Out-Null }
        'SHOT' {
            Adb-Shell 'screencap -p /data/local/tmp/step.png' | Out-Null
            Invoke-Adb @('pull', '/data/local/tmp/step.png', (Join-Path $out ($arg + '.png'))) | Out-Null
        }
        'TAPTEXT' { Tap-Text $arg }
        'REQUIRE' {
            if (-not (Find-OnScreen $arg 1)) {
                Out-Log ('"' + $arg + '" is not on screen, skipping the rest of the steps for this tablet')
                $Script:SkipRest = $true
            }
        }
        'WAITBOOT' {
            $limit = [int]$arg
            $t0 = Get-Date
            Start-Sleep -Seconds 15
            $up = $false
            while (((Get-Date) - $t0).TotalSeconds -lt $limit) {
                if ($Script:Serial -and (Test-Serial $Script:Serial)) {
                    if ((Adb-Shell 'getprop sys.boot_completed').Trim() -eq '1') { $up = $true; break }
                }
                Start-Sleep -Seconds 3
            }
            Out-Log $(if ($up) { 'tablet is back up after ' + [int]((Get-Date) - $t0).TotalSeconds + ' s' } else { 'tablet did not come back in time' })
        }
        'THEN' {
            if (-not $Script:UnlessRan) { Out-Log 'skipped'; break }
            $inner = Split-First $arg
            Invoke-Step $inner[0].ToUpper() $inner[1]
        }
        'UNLESS' {
            $i = $arg.IndexOf(' => ')
            if ($i -lt 0) { Out-Log 'UNLESS needs "text => step"'; break }
            $what = $arg.Substring(0, $i)
            $inner = Split-First $arg.Substring($i + 4).Trim()
            $Script:UnlessRan = $false
            if (Find-OnScreen $what 2) { Out-Log ('"' + $what + '" is on screen, skipping'); break }
            $Script:UnlessRan = $true
            Out-Log ('"' + $what + '" not on screen, running: ' + $inner[0] + ' ' + $inner[1])
            Invoke-Step $inner[0].ToUpper() $inner[1]
        }
        'REMOVE' { Remove-Packages @($arg -split '\s+' | Where-Object { $_ -ne '' }) }
        'TOGGLE' { Set-Toggle $arg }
        'PULL' {
            $p = Split-First $arg
            Out-Log (Invoke-Adb @('pull', $p[0], (Join-Path $out $p[1]))).Text
        }
        'OBDLOG' {
            Invoke-Adb @('pull', ('/sdcard/Android/data/' + $pkg + '/files/obd-log.txt'), (Join-Path $out 'obd-log.txt')) | Out-Null
        }
        'LOGCAT' { Out-Log (Invoke-Adb @('logcat', '-d', '-t', '500', '*:W')).Text }
        default { Out-Log ('unknown step ' + $op) }
    }
}

$pkg = (Get-Settings)['AppPackage']
$ready = $false
$picked = $false
foreach ($raw in Get-Content (Join-Path $Script:Kit 'steps.txt')) {
    $line = $raw.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { continue }
    $parts = Split-First $line
    $op = $parts[0].ToUpper()
    $arg = $parts[1]
    Out-Log ('### ' + $line)
    if ($op -eq 'DEVICE') {
        $Script:SkipRest = $false
        $picked = $true
        try { $ready = Select-Device $arg } catch { $ready = $false; Out-Log ('device failed: ' + $_.Exception.Message) }
        continue
    }
    if (-not $picked) {
        $picked = $true
        try { $ready = Select-Device 'wifi' } catch { $ready = $false; Out-Log ('device failed: ' + $_.Exception.Message) }
    }
    if (-not $ready -and $op -ne 'WAIT') { Out-Log 'skipped: no tablet selected'; continue }
    if ($Script:SkipRest) { Out-Log 'skipped'; continue }
    try {
        Invoke-Step $op $arg
    } catch {
        Out-Log ('step failed: ' + $_.Exception.Message)
    }
}
Set-Content -Path $done -Value 'OK' -Encoding ASCII
Say 'Steps finished. This window closes in 3 seconds.' Green
Start-Sleep -Seconds 3
