# Drehzahl tablet toolkit: tablet report, screenshot and crash log

function New-TabletReport {
    Require-Tablet
    Step 'Collecting a tablet report'
    $file = Join-Path $Script:Reports 'device-report.txt'
    $sections = [ordered]@{
        'Device'            = 'getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.product.device; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.display.id; getprop ro.soc.manufacturer; getprop ro.soc.model; getprop ro.hardware; getprop ro.product.cpu.abilist; getprop ro.config.low_ram'
        'Screen'            = 'wm size; wm density; dumpsys display | grep -iE ''mBaseDisplayInfo|refreshRate'' | head -5'
        'Memory and storage'= 'cat /proc/meminfo | head -3; df -h /data | tail -1'
        'Battery'           = 'dumpsys battery | head -15'
        'Features'          = 'pm list features | grep -iE ''usb|bluetooth|wifi.direct|location|sensor|touchscreen|telephony|nfc|audio'' | sort'
        'Sensors'           = 'dumpsys sensorservice | grep -iE ''^0x|handle|accel|light|gyro|magnet'' | head -40'
        'Audio effects'     = 'dumpsys media.audio_flinger | grep -iA3 effect | head -60'
        'Audio outputs'     = 'dumpsys audio | grep -iE ''Devices:|mUsbDevice|connected'' | head -20'
        'Home app'          = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
        'Settings'          = 'settings get global stay_on_while_plugged_in; settings get system screen_off_timeout; settings get global window_animation_scale'
    }
    $out = New-Object System.Collections.Generic.List[string]
    $out.Add('Drehzahl tablet report  ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '  serial ' + $Script:Serial)
    foreach ($k in $sections.Keys) {
        Say ('  ' + $k)
        $out.Add('')
        $out.Add('### ' + $k)
        $out.Add((Adb-Shell $sections[$k]))
    }
    Say '  Package list'
    $out.Add('')
    $out.Add('### Packages (path=package)')
    $out.Add((Adb-Shell 'pm list packages -f --user 0 | sort'))
    $out.Add('')
    $out.Add('### Packages removed for user 0 but still on the system')
    $out.Add((Adb-Shell 'pm list packages -u --user 0 | sort > /data/local/tmp/a; pm list packages --user 0 | sort > /data/local/tmp/b; comm -23 /data/local/tmp/a /data/local/tmp/b; rm /data/local/tmp/a /data/local/tmp/b'))
    $out.Add('')
    $out.Add('### Disabled packages')
    $out.Add((Adb-Shell 'pm list packages -d --user 0 | sort'))
    $out.Add('')
    $out.Add('### Launchable apps')
    $out.Add((Adb-Shell 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | grep /'))
    Set-Content -Path $file -Value $out -Encoding UTF8
    Say ('Saved: ' + $file) Green
    Say 'Share reports\device-report.txt when asking for help with this tablet.'
}

function Save-Screenshot {
    Require-Tablet
    Step 'Taking a screenshot of the tablet'
    $file = Join-Path $Script:Reports ('screen-' + (Get-Stamp) + '.png')
    Adb-Shell 'screencap -p /data/local/tmp/drehzahl-screen.png' | Out-Null
    Invoke-Adb @('pull', '/data/local/tmp/drehzahl-screen.png', $file) | Out-Null
    Adb-Shell 'rm /data/local/tmp/drehzahl-screen.png' | Out-Null
    if (Test-Path $file) { Say ('Saved: ' + $file) Green } else { Say 'Screenshot failed.' Red }
}

function Save-CrashLog {
    Require-Tablet
    Step 'Saving the tablet log'
    $file = Join-Path $Script:Reports ('log-' + (Get-Stamp) + '.txt')
    $text = (Invoke-Adb @('logcat', '-d', '-v', 'time', '-t', '3000')).Text
    $crash = (Invoke-Adb @('logcat', '-d', '-b', 'crash')).Text
    Set-Content -Path $file -Value @('### crash buffer', $crash, '', '### last 3000 lines', $text) -Encoding UTF8
    Say ('Saved: ' + $file) Green
}
