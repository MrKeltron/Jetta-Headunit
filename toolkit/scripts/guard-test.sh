# Tests Drehzahl's Wi-Fi auto-join helper by hand. Run as root: sh guard-test.sh
APK=$(pm path dev.kelton.drehzahl | head -1 | cut -d: -f2)
H="CLASSPATH=$APK /system/bin/app_process /system/bin dev.kelton.drehzahl.sys.WifiAutojoin"
scans() { dumpsys wifi | grep -i -E "periodic|single scan|startConnectivityScan|scan.*schedule" | tail -6; }
echo "apk: $APK"
echo "== state as shell user"; su 2000 -c "$H state"
echo "== state as root"; sh -c "$H state"
echo "== wifi"; cmd wifi status 2>&1 | head -4
echo "== scan log before $(date +%T)"; scans
echo "== off as shell user"; su 2000 -c "$H off"
sleep 45
echo "== scan log after 45 s paused $(date +%T)"; scans
echo "== on as shell user"; su 2000 -c "$H on"
echo "== state"; su 2000 -c "$H state"
