# Copies CarLink's logs (root). Run: sh carlink-logs.sh
p=com.carlink.app
d=/data/data/$p/files/logs
echo "== mode requests in every DiPlay log"
grep -h -e "mode request" -e "USB mode" -e "configuration selected" -e "cdc_ncm" -e "first-frame" -e "WIRED_TIMING" $d/previous-*.log $d/diplay.log 2>/dev/null | tail -40 | cut -c1-400
echo "== carlink.log"; tail -n 60 $d/carlink.log
echo "== diplay.log"; grep -v THEME_ $d/diplay.log | tail -n 120
echo "== kernel"
dmesg | grep -e "usb 1-" -e cdc_ncm -e "role_sx" | tail -40
