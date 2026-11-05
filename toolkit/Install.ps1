# Drehzahl tablet toolkit: install Drehzahl + CarLink, permissions, icon pack, car tweaks

function Install-Apk {
    param([string]$Apk, [string]$Package)
    Say ('Installing ' + (Split-Path $Apk -Leaf) + '...')
    $r = Invoke-Adb @('install', '-r', '-g', $Apk)
    Say ('  ' + ($r.Text.Trim() -split "`n" | Select-Object -Last 1)) DarkGray
    if ($r.Text -match 'Success') { return $true }
    if ($r.Text -match 'UPDATE_INCOMPATIBLE|signatures do not match') {
        Say 'The installed copy was signed with a different key, so it cannot be updated in place.' Yellow
        if (Ask-YesNo ('Uninstall the old ' + $Package + ' first? (its settings will be reset)')) {
            Invoke-Adb @('uninstall', $Package) | Out-Null
            $r = Invoke-Adb @('install', '-g', $Apk)
            Say ('  ' + ($r.Text.Trim() -split "`n" | Select-Object -Last 1)) DarkGray
            return ($r.Text -match 'Success')
        }
    }
    return $false
}

function Get-GitHubRelease {
    param([string]$Repo, [string]$Tag = 'latest')
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $url = 'https://api.github.com/repos/' + $Repo + '/releases/' + $Tag
    if ($Tag -ne 'latest') { $url = 'https://api.github.com/repos/' + $Repo + '/releases/tags/' + $Tag }
    return Invoke-RestMethod -UseBasicParsing -Uri $url -Headers @{ 'User-Agent' = 'drehzahl-toolkit' }
}

function Save-Asset {
    param($Asset, [string]$Folder)
    $file = Join-Path $Folder $Asset.name
    $old = $ProgressPreference; $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -UseBasicParsing -Uri $Asset.browser_download_url -OutFile $file -Headers @{ 'User-Agent' = 'drehzahl-toolkit' }
    $ProgressPreference = $old
    return $file
}

function Install-Drehzahl {
    Require-Tablet
    Step 'Drehzahl (the head unit app)'
    $s = Get-Settings
    $pkg = $s['AppPackage']
    $apk = Get-ChildItem -Path $Script:ApkFolder -Filter 'Drehzahl*.apk' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($s['GitHubRepo']) {
        try {
            $rel = Get-GitHubRelease $s['GitHubRepo'] 'latest-build'
            $asset = $rel.assets | Where-Object { $_.name -like 'Drehzahl*.apk' } | Select-Object -First 1
            if ($asset -and ((-not $apk) -or ([datetime]$asset.updated_at -gt $apk.LastWriteTime))) {
                Say ('Downloading ' + $asset.name + ' from github.com/' + $s['GitHubRepo'])
                $apk = Get-Item (Save-Asset $asset $Script:ApkFolder)
            }
        } catch { Say ('Could not check GitHub (' + $_.Exception.Message + '). Using the local APK.') Yellow }
    }
    if (-not $apk) { Say ('No Drehzahl APK found in ' + $Script:ApkFolder) Red; return }
    if (-not (Install-Apk $apk.FullName $pkg)) { Say 'Install failed.' Red; return }

    Say 'Giving Drehzahl the permissions it needs...'
    $cmds = @(
        ('cmd notification allow_listener ' + $pkg + '/' + $pkg + '.media.MediaListener'),
        ('pm grant ' + $pkg + ' android.permission.DUMP'),
        ('pm grant ' + $pkg + ' android.permission.WRITE_SECURE_SETTINGS'),
        ('appops set ' + $pkg + ' WRITE_SETTINGS allow'),
        ('appops set ' + $pkg + ' RUN_ANY_IN_BACKGROUND allow'),
        ('dumpsys deviceidle whitelist +' + $pkg)
    )
    foreach ($c in $cmds) {
        $res = (Adb-Shell $c).Trim()
        if ($res -eq '') { $res = 'ok' }
        Say ('  ' + $c + '  ->  ' + $res) DarkGray
    }
    if (Ask-YesNo 'Make Drehzahl the home screen? (the stock home screen stays installed as a backup)') {
        $res = (Adb-Shell ('cmd role add-role-holder --user 0 android.app.role.HOME ' + $pkg)).Trim()
        if ($res -match 'rror|xception') { $res = (Adb-Shell ('cmd package set-home-activity --user 0 ' + $pkg + '/.MainActivity')).Trim() }
        Say ('  home screen -> ' + $(if ($res) { $res } else { 'ok' })) DarkGray
    }
    Adb-Shell ('am start -n ' + $pkg + '/.MainActivity') | Out-Null
    Say 'Drehzahl is installed and running on the tablet.' Green
}

function Install-CarLink {
    Require-Tablet
    Step 'CarLink (CarPlay engine, github.com/F-e-n-y-x/CarLink)'
    $s = Get-Settings
    $repo = $s['CarLinkRepo']
    $rel = Get-GitHubRelease $repo
    $apkAsset = $rel.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
    $shaAsset = $rel.assets | Where-Object { $_.name -like '*.apk.sha256' } | Select-Object -First 1
    if (-not $apkAsset) { Say ('No APK in the latest CarLink release (' + $rel.tag_name + ').') Red; return }
    Say ('Latest CarLink release: ' + $rel.tag_name)
    $apk = Save-Asset $apkAsset $Script:ApkFolder
    if ($shaAsset) {
        $shaFile = Save-Asset $shaAsset $Script:ApkFolder
        $want = ((Get-Content $shaFile -Raw).Trim() -split '\s+')[0].ToLower()
        $have = (Get-FileHash -Algorithm SHA256 $apk).Hash.ToLower()
        if ($want -ne $have) { Say 'Checksum does NOT match. Not installing. Try again later.' Red; return }
        Say 'Checksum matches the release.' Green
    } else { Say 'This release has no checksum file, skipping the check.' Yellow }
    if (-not (Install-Apk $apk 'com.carlink.app')) { Say 'CarLink install failed.' Red; return }
    foreach ($c in @('appops set com.carlink.app SYSTEM_ALERT_WINDOW allow', 'dumpsys deviceidle whitelist +com.carlink.app', 'appops set com.carlink.app RUN_ANY_IN_BACKGROUND allow')) {
        Adb-Shell $c | Out-Null
    }
    Say 'CarLink installed. Open it once on the tablet to finish its first-time setup.' Green
}

function Open-IconPack {
    Require-Tablet
    Step 'OnePro (Dark) icon pack'
    $s = Get-Settings
    $id = $s['IconPack']
    $have = Adb-Shell ('pm list packages ' + $id)
    if ($have -match [regex]::Escape($id)) { Say 'OnePro (Dark) is already installed. Pick it in Drehzahl: Settings > Home & apps > Icon pack.' Green; return }
    Adb-Shell ('am start -a android.intent.action.VIEW -d market://details?id=' + $id) | Out-Null
    Say 'The Play Store page is open on the tablet. Buy/install it there.'
    Say 'Then in Drehzahl: Settings > Home & apps > Icon pack > OnePro (Dark).'
}

function Set-CarTweaks {
    Require-Tablet
    Step 'Car tweaks'
    if (Ask-YesNo 'Keep the screen on whenever the tablet is charging?') { Adb-Shell 'settings put global stay_on_while_plugged_in 7' | Out-Null; Say '  done' Green }
    if (Ask-YesNo 'Make animations faster (0.5x)?') {
        Adb-Shell 'settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5' | Out-Null
        Say '  done' Green
    }
    if (Ask-YesNo 'Turn off the swipe lock screen? (only works if no PIN/pattern is set)') {
        Say ('  ' + (Adb-Shell 'locksettings set-disabled true').Trim()) DarkGray
    }
}

function Start-InstallMenu {
    Require-Tablet
    if (Ask-YesNo 'Install or update Drehzahl?') { Install-Drehzahl }
    if (Ask-YesNo 'Install or update CarLink (CarPlay)?') { Install-CarLink }
    if (Ask-YesNo 'Get the OnePro (Dark) icon pack?') { Open-IconPack }
    if (Ask-YesNo 'Apply car tweaks (screen on while charging, faster animations, no lock screen)?') { Set-CarTweaks }
}
