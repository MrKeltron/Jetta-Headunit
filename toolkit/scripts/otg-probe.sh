# What could switch off the tablet's 5 V output (names and permissions only). Run as root.
ls -l /dev/i2c* 2>&1 | head
toybox 2>/dev/null | tr ' ' '\n' | grep -i i2c
which i2cget i2cset i2cdump 2>&1
echo "== writable power supply files"
for s in primary_chg mtk-master-charger; do ls -l /sys/class/power_supply/$s/ | grep -e "rw" ; done
echo "== cx2560x kernel lines"
dmesg | grep -i -e cx2560 -e otg -e boost | grep -v -e "Power/swap" | tail -40
echo "== otg related files"
find /sys/devices/platform -maxdepth 4 -iname '*otg*' 2>/dev/null | head -20
find /sys/class/regulator/ -maxdepth 1 2>/dev/null | while read r; do [ -f $r/name ] && echo "$r $(cat $r/name) $(cat $r/state 2>/dev/null)"; done | head -60
