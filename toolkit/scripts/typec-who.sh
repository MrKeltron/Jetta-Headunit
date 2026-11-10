# Who changes the USB-C port type back to dual. Run as root.
P=/sys/class/typec/port0
ls -l $P/port_type $P/power_role $P/data_role
readlink -f $P
ls -Z $P/port_type
echo "== usb hal processes"
ps -A -o USER,PID,NAME | grep -i -e usb -e typec
echo "== logcat around the plug events"
logcat -d -b all -v threadtime | grep -i -e "port_type" -e "switchToDrp" -e "UsbPortManager" -e "usb.*hal" -e "typec" -e "role" | grep -v -i -e "WindowManager" -e "ActivityTask" | tail -120
echo "== kernel"
dmesg | grep -e TCPC -e typec -e "port_type" -e "role" | grep -v -e bat_update -e healthd | tail -120
