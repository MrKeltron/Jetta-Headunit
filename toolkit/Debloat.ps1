# Drehzahl tablet toolkit: debloat with preview, plus undo

$Script:RemovedFile = Join-Path $Script:Reports 'removed-packages.txt'

function Read-DebloatRules {
    $r = [pscustomobject]@{ Keep = @{}; KeepMatch = @(); Remove = @{}; RemoveMatch = @(); Ask = @{} }
    foreach ($raw in Get-Content (Join-Path $Script:Kit 'debloat-list.txt')) {
        $cut = $raw.IndexOf('#')
        $note = ''
        $line = $raw
        if ($cut -ge 0) { $note = $raw.Substring($cut + 1).Trim(); $line = $raw.Substring(0, $cut) }
        $line = $line.Trim()
        if ($line -eq '') { continue }
        $parts = $line -split '\s+'
        if ($parts.Count -lt 2) { continue }
        $what = $parts[1]
        switch ($parts[0].ToLower()) {
            'keep'         { $r.Keep[$what] = $note }
            'keep-match'   { $r.KeepMatch += $what }
            'remove'       { $r.Remove[$what] = $note }
            'remove-match' { $r.RemoveMatch += $what }
            'ask'          { $r.Ask[$what] = $note }
        }
    }
    return $r
}

function Get-InstalledPackages {
    $list = @()
    foreach ($line in ((Adb-Shell 'pm list packages --user 0') -split "`r?`n")) {
        if ($line -match '^package:(\S+)') { $list += $Matches[1] }
    }
    return ($list | Sort-Object)
}

function Get-LaunchablePackages {
    $set = @{}
    $text = Adb-Shell 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER'
    foreach ($line in ($text -split "`r?`n")) {
        if ($line -match '^\s*([A-Za-z0-9_\.]+)/') { $set[$Matches[1]] = $true }
    }
    return $set
}

function Test-Kept {
    param($Rules, [string]$Pkg)
    if ($Rules.Keep.ContainsKey($Pkg)) { return $true }
    foreach ($m in $Rules.KeepMatch) { if ($Pkg.Contains($m)) { return $true } }
    return $false
}

function Read-Numbers {
    param([string]$Prompt, [int]$Max)
    $picked = @()
    $v = Read-Host $Prompt
    foreach ($t in ($v -split '[\s,]+')) {
        $n = 0
        if ([int]::TryParse($t, [ref]$n) -and $n -ge 1 -and $n -le $Max) { $picked += $n }
    }
    return $picked
}

function Remove-Packages {
    param([string[]]$Packages)
    $stamp = Get-Stamp
    $log = Join-Path $Script:Reports ('debloat-' + $stamp + '.txt')
    $ok = 0
    foreach ($p in $Packages) {
        $res = (Adb-Shell ('pm uninstall -k --user 0 ' + $p)).Trim()
        if ($res -match 'Success') {
            $ok++
            Say ('  removed   ' + $p) Green
            Add-Content -Path $Script:RemovedFile -Value $p -Encoding ASCII
        } else {
            Say ('  skipped   ' + $p + '   (' + $res + ')') Yellow
        }
        Add-Content -Path $log -Value ($p + '  ' + $res) -Encoding ASCII
    }
    Say ''
    Say ('Removed ' + $ok + ' of ' + $Packages.Count + '. Log: ' + $log) Green
}

function Start-Debloat {
    Require-Tablet
    Step 'Debloat'
    $rules = Read-DebloatRules
    $installed = Get-InstalledPackages
    Set-Content -Path (Join-Path $Script:Reports ('packages-before-' + (Get-Stamp) + '.txt')) -Value $installed -Encoding ASCII
    $launchable = Get-LaunchablePackages

    $plan = @()
    $askList = @()
    $unknown = @()
    foreach ($p in $installed) {
        if (Test-Kept $rules $p) { continue }
        if ($rules.Remove.ContainsKey($p)) { $plan += [pscustomobject]@{ Pkg = $p; Why = $rules.Remove[$p] }; continue }
        if ($rules.Ask.ContainsKey($p)) { $askList += [pscustomobject]@{ Pkg = $p; Why = $rules.Ask[$p] }; continue }
        $hit = $null
        foreach ($m in $rules.RemoveMatch) { if ($p.Contains($m)) { $hit = $m; break } }
        if ($hit) { $plan += [pscustomobject]@{ Pkg = $p; Why = ('matches "' + $hit + '"') }; continue }
        if ($launchable.ContainsKey($p)) { $unknown += $p }
    }

    if ($plan.Count -gt 0) {
        Say ('These ' + $plan.Count + ' apps are on the remove list:') White
        for ($i = 0; $i -lt $plan.Count; $i++) {
            Say ('  {0,3}. {1,-55} {2}' -f ($i + 1), $plan[$i].Pkg, $plan[$i].Why)
        }
        $skip = Read-Numbers 'Type the numbers of any you want to KEEP (example: 3 7), or just press Enter' $plan.Count
        $final = @()
        for ($i = 0; $i -lt $plan.Count; $i++) { if ($skip -notcontains ($i + 1)) { $final += $plan[$i].Pkg } }
    } else {
        Say 'Nothing from the remove list is installed.' Green
        $final = @()
    }

    foreach ($a in $askList) {
        $label = $a.Pkg
        if ($a.Why) { $label = $a.Pkg + '  (' + $a.Why + ')' }
        if (Ask-YesNo ('Remove ' + $label + '?')) { $final += $a.Pkg }
    }

    if ($unknown.Count -gt 0) {
        Say ''
        Say 'These apps have icons but are not on any list, so they are left alone:' White
        for ($i = 0; $i -lt $unknown.Count; $i++) { Say ('  {0,3}. {1}' -f ($i + 1), $unknown[$i]) }
        $extra = Read-Numbers 'Type numbers to remove any of them too, or just press Enter' $unknown.Count
        foreach ($n in $extra) { $final += $unknown[$n - 1] }
    }

    if ($final.Count -eq 0) { Say 'Nothing to remove.' Green; return }
    Say ''
    Say ('Ready to remove ' + $final.Count + ' apps for user 0. Undo is in the menu.') White
    if (-not (Ask-YesNo 'Go ahead?')) { Say 'Cancelled. Nothing changed.' Yellow; return }
    Remove-Packages $final
}

function Start-Undo {
    Require-Tablet
    Step 'Undo debloat'
    if (-not (Test-Path $Script:RemovedFile)) { Say 'Nothing has been removed by this toolkit yet.' Green; return }
    $list = @(Get-Content $Script:RemovedFile | Where-Object { $_.Trim() -ne '' } | Sort-Object -Unique)
    if ($list.Count -eq 0) { Say 'Nothing to restore.' Green; return }
    for ($i = 0; $i -lt $list.Count; $i++) { Say ('  {0,3}. {1}' -f ($i + 1), $list[$i]) }
    $nums = Read-Numbers 'Type numbers to restore (example: 2 5), or just press Enter to restore all' $list.Count
    $raw = $null
    if ($nums.Count -eq 0) {
        $raw = Read-Host 'Restore all of them? Type ALL to confirm, or press Enter to cancel'
        if ($raw -ne 'ALL') { Say 'Cancelled.' Yellow; return }
        $nums = 1..$list.Count
    }
    $left = @()
    for ($i = 0; $i -lt $list.Count; $i++) {
        $p = $list[$i]
        if ($nums -contains ($i + 1)) {
            $res = (Adb-Shell ('cmd package install-existing --user 0 ' + $p)).Trim()
            if ($res -match 'installed|Success') { Say ('  restored  ' + $p) Green } else { Say ('  failed    ' + $p + '  (' + $res + ')') Yellow; $left += $p }
        } else { $left += $p }
    }
    Set-Content -Path $Script:RemovedFile -Value $left -Encoding ASCII
}
