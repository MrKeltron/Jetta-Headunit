# Lists the charger controls by name only (reading unknown vendor files can crash this tablet).
for d in /sys/devices/platform/charger /sys/devices/platform/mtk-charger /sys/class/power_supply/mtk-master-charger /sys/class/power_supply/primary_chg; do
  echo "== $d"; ls $d 2>&1 | tr '\n' ' '; echo
done
for f in input_current_limit constant_charge_current_max constant_charge_current input_voltage_limit; do
  for s in mtk-master-charger primary_chg battery; do
    [ -f /sys/class/power_supply/$s/$f ] && echo "$s/$f = $(cat /sys/class/power_supply/$s/$f)"
  done
done
find /sys/devices/platform -maxdepth 3 -name 'usb_unlimited' -o -maxdepth 3 -name 'input_current' 2>/dev/null
dmesg | grep -e "chg1:" -e "Vbat=" | tail -4
