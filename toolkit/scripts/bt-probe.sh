# Bluetooth probe. Run as root: sh bt-probe.sh
echo "== props"
getprop | grep -i -e bluetooth -e a2dp -e avrcp -e class_of
echo "== modules"
ls /data/adb/modules
cat /data/adb/modules/drehzahl_btsink/system.prop 2>/dev/null
cat /data/adb/drehzahl-btsink.orig 2>/dev/null
echo "== features"
pm list features | grep -i -e bluetooth -e telephony
echo "== bluetooth_manager"
dumpsys bluetooth_manager
echo "== recent log"
logcat -d -b all -v threadtime | grep -i -e a2dp -e avrcp -e headset -e hfp -e bt_btif -e bt_stack -e "acl" -e bluetooth | tail -3000
