# Drehzahl tablet toolkit: find the tablet on the network, pair and connect

function Test-Serial {
    param([string]$Target)
    $r = Invoke-Adb -NoSerial @('-s', $Target, 'get-state')
    return ($r.Text.Trim() -eq 'device')
}

function Get-ConnectedDevices {
    $list = @()
    $r = Invoke-Adb -NoSerial @('devices')
    foreach ($line in ($r.Text -split "`r?`n")) {
        if ($line -match '^(\S+)\s+device\s*$') { $list += $Matches[1] }
    }
    return $list
}

function Find-MdnsServices {
    $found = @()
    $r = Invoke-Adb -NoSerial @('mdns', 'services')
    foreach ($line in ($r.Text -split "`r?`n")) {
        if ($line -match '_adb-tls-(connect|pairing)\._tcp\.?\s+([0-9\.]+):([0-9]+)') {
            $found += [pscustomobject]@{ Kind = $Matches[1]; IP = $Matches[2]; Port = [int]$Matches[3] }
        }
    }
    return $found
}

# Wireless debugging picks a random port between 32768 and 60999.
# This knocks on every one of them at once in batches and returns the open ones.
function Find-OpenPorts {
    param([string]$IP, [int]$From = 32768, [int]$To = 60999, [int]$Batch = 1000)
    $open = New-Object System.Collections.Generic.List[int]
    for ($start = $From; $start -le $To; $start += $Batch) {
        $end = [Math]::Min($start + $Batch - 1, $To)
        $pct = [int](100 * ($start - $From) / ($To - $From))
        Write-Progress -Activity ('Scanning ' + $IP + ' for the wireless debugging port') -Status ('ports ' + $start + '-' + $end) -PercentComplete $pct
        $jobs = @()
        for ($p = $start; $p -le $end; $p++) {
            $c = New-Object System.Net.Sockets.TcpClient
            $jobs += ,@($p, $c, $c.ConnectAsync($IP, $p))
        }
        Start-Sleep -Milliseconds 450
        foreach ($j in $jobs) {
            $t = $j[2]
            if ($t.Status -eq 'RanToCompletion' -and $j[1].Connected) { $open.Add($j[0]) }
            try { $j[1].Close() } catch { }
        }
    }
    Write-Progress -Activity 'Scanning' -Completed
    return $open
}

function Try-Connect {
    param([string]$Target)
    $r = Invoke-Adb -NoSerial @('connect', $Target)
    $msg = $r.Text.Trim()
    Say ('  adb connect ' + $Target + '  ->  ' + $msg) DarkGray
    if ($msg -match 'failed to authenticate|unauthorized') { $Script:NeedsPairing = $true }
    if ($msg -match 'connected to|already connected') {
        Start-Sleep -Milliseconds 700
        if (Test-Serial $Target) {
            $Script:Serial = $Target
            $parts = $Target.Split(':')
            Save-Setting 'TabletIP' $parts[0]
            Save-Setting 'TabletPort' $parts[1]
            return $true
        }
        $Script:NeedsPairing = $true
        Invoke-Adb -NoSerial @('disconnect', $Target) | Out-Null
    }
    return $false
}

function Read-IpPort {
    param([string]$Prompt)
    while ($true) {
        $v = (Read-Host $Prompt).Trim()
        if ($v -eq '') { return $null }
        if ($v -match '^[0-9]{1,3}(\.[0-9]{1,3}){3}:[0-9]{2,5}$') { return $v }
        Say 'That does not look right. It should look like 192.168.18.88:37123 (or press Enter to cancel).' Yellow
    }
}

function Pair-Tablet {
    if ($Script:AutoYes) { Say 'Pairing needs you at the keyboard. Run START HERE.bat and pick 1.' Red; return $false }
    Step 'Pairing (only needed once per PC)'
    Say 'On the tablet:'
    Say '  1. Settings > System > Developer options > Wireless debugging (turn it ON)'
    Say '  2. Tap "Pair device with pairing code"'
    Say '  3. Leave that popup open. It shows a 6-digit code and an IP address & Port.'
    Read-Host 'When the popup with the code is showing, press Enter' | Out-Null

    $target = $null
    $svc = @(Find-MdnsServices | Where-Object { $_.Kind -eq 'pairing' })
    if ($svc.Count -gt 0) {
        $target = $svc[0].IP + ':' + $svc[0].Port
        Say ('Found the tablet''s pairing service at ' + $target) Green
    } else {
        $target = Read-IpPort 'Type the IP address & Port shown in the popup (example 192.168.18.88:37123)'
        if (-not $target) { return $false }
    }
    $code = ''
    while ($code -notmatch '^[0-9]{6}$') { $code = (Read-Host 'Type the 6-digit Wi-Fi pairing code').Trim() }
    $r = Invoke-Adb -NoSerial @('pair', $target, $code)
    Say ('  ' + $r.Text.Trim()) DarkGray
    if ($r.Text -match 'Successfully paired') {
        Say 'Paired. You can close the popup on the tablet.' Green
        return $true
    }
    Say 'Pairing failed. The code expires quickly, so try again with a fresh popup.' Red
    return $false
}

function Connect-Tablet {
    Step 'Connecting to the tablet'
    Ensure-Adb
    $s = Get-Settings
    $ip = $s['TabletIP']
    $port = $s['TabletPort']
    $Script:NeedsPairing = $false

    $ready = @(Get-ConnectedDevices)
    foreach ($d in $ready) {
        if ($ip -and $d -like ($ip + ':*')) { $Script:Serial = $d; Say ('Already connected: ' + $d) Green; return $true }
    }
    foreach ($d in $ready) {
        if ($d -match '_adb-tls-connect') { $Script:Serial = $d; Say ('Already connected through the network name: ' + $d) Green; return $true }
    }

    for ($attempt = 1; $attempt -le 2; $attempt++) {
        if ($ip -and $port) {
            Say ('Trying the saved address ' + $ip + ':' + $port)
            if (Try-Connect ($ip + ':' + $port)) { break }
        }

        Say 'Looking for the tablet on the network (mDNS)...'
        $svc = @(Find-MdnsServices | Where-Object { $_.Kind -eq 'connect' })
        $ok = $false
        foreach ($x in $svc) {
            if (Try-Connect ($x.IP + ':' + $x.Port)) { $ok = $true; break }
        }
        if ($ok) { break }

        if ($ip -and -not $Script:NeedsPairing) {
            $ping = Test-Connection -ComputerName $ip -Count 1 -Quiet -ErrorAction SilentlyContinue
            if ($ping) {
                Say ('The tablet answers at ' + $ip + '. Scanning its ports (about 15 seconds)...')
                $ports = Find-OpenPorts -IP $ip
                foreach ($p in $ports) {
                    if (Try-Connect ($ip + ':' + $p)) { $ok = $true; break }
                }
                if ($ok) { break }
            } else {
                Say ('Nothing answered at ' + $ip + '. The tablet may have a new IP address.') Yellow
            }
        }

        if ($attempt -eq 2) { break }

        if ($Script:NeedsPairing -or (Ask-YesNo 'Could not connect. Pair this PC with the tablet now?')) {
            if (-not (Pair-Tablet)) { break }
            Start-Sleep -Seconds 2
            Say ''
            Say 'Now look at the main Wireless debugging screen on the tablet.'
            Say 'It shows "IP address & Port" (this is a different port than the pairing one).'
            $t = Read-IpPort 'Type it here, or just press Enter to let me find it'
            if ($t) {
                if (Try-Connect $t) { break }
            }
            $s = Get-Settings
            $ip = $s['TabletIP']
            if ($t) { $ip = $t.Split(':')[0]; $port = $t.Split(':')[1] }
        } else { break }
    }

    if ($Script:Serial -and (Test-Serial $Script:Serial)) {
        $model = (Adb-Shell 'getprop ro.product.model').Trim()
        $ver = (Adb-Shell 'getprop ro.build.version.release').Trim()
        Say ('Connected to ' + $model + ' (Android ' + $ver + ') at ' + $Script:Serial) Green
        return $true
    }
    Say 'Could not connect.' Red
    Say 'Check: the tablet and this PC are on the same Wi-Fi, and Wireless debugging is ON.'
    return $false
}
