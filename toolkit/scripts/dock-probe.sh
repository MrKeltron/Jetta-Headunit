# USB-C dock probe. Run as root: sh dock-probe.sh state|dt|swap|hosttest|watch <seconds>
# Never read the files under /sys/class/tcpc: some of them crash the kernel on this tablet.
P=/sys/class/typec/port0

short() {
  pw=""
  for s in /sys/class/power_supply/*; do
    [ -f $s/online ] && pw="$pw ${s##*/}=$(cat $s/online 2>/dev/null)"
  done
  hubs=""
  for d in /sys/bus/usb/devices/[0-9]*-*; do
    [ -f $d/bDeviceClass ] || continue
    hubs="$hubs ${d##*/}:$(cat $d/idVendor):$(cat $d/idProduct)/c$(cat $d/bDeviceClass)"
  done
  echo "$(date +%T) pr=[$(cat $P/power_role 2>&1)] dr=[$(cat $P/data_role 2>&1)] mode=$(cat $P/power_operation_mode 2>&1) partner=$([ -d $P-partner ] && echo yes || echo no) |$pw | bat=$(cat /sys/class/power_supply/battery/status 2>/dev/null) $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)uA | usb:$hubs | udc=$(cat /sys/class/udc/*/state 2>/dev/null)"
}

state() {
  echo "== typec port"
  ls /sys/class/typec/
  for f in $P/*; do [ -f $f ] && echo "${f##*/} = $(cat $f 2>&1)"; done
  if [ -d $P-partner ]; then
    echo "== partner"
    for f in $P-partner/*; do [ -f $f ] && echo "${f##*/} = $(cat $f 2>&1)"; done
    for f in $P-partner/identity/*; do echo "identity ${f##*/} = $(cat $f 2>&1)"; done
  fi
  echo "== power supplies"
  for s in /sys/class/power_supply/*; do
    echo "${s##*/}: online=$(cat $s/online 2>/dev/null) type=$(cat $s/type 2>/dev/null) usb_type=$(cat $s/usb_type 2>/dev/null) V=$(cat $s/voltage_now 2>/dev/null) Imax=$(cat $s/current_max 2>/dev/null) I=$(cat $s/current_now 2>/dev/null) status=$(cat $s/status 2>/dev/null)"
  done
  dumpsys battery
  echo "== usb devices"
  for d in /sys/bus/usb/devices/*; do
    [ -f $d/idVendor ] && echo "${d##*/} $(cat $d/idVendor):$(cat $d/idProduct) class=$(cat $d/bDeviceClass) speed=$(cat $d/speed) $(cat $d/manufacturer 2>/dev/null) $(cat $d/product 2>/dev/null)"
  done
  echo "udc: $(ls /sys/class/udc 2>/dev/null) state=$(cat /sys/class/udc/*/state 2>/dev/null)"
  getprop | grep -i usb
  echo "== usb_power_delivery"
  ls -R /sys/class/usb_power_delivery 2>&1 | head -80
  echo "== android usb service"
  dumpsys usb | grep -i -A30 "port_manager\|UsbPortManager" | head -80
  echo "== usb role switches"
  for r in /sys/class/usb_role/*; do echo "${r##*/} role=$(cat $r/role 2>&1)"; done
  ls /sys/devices/platform/mt_usb/ 2>&1 | tr '\n' ' '; echo
  ls /sys/devices/platform/mt_usb/musb-hdrc/ 2>&1 | tr '\n' ' '; echo
  echo "== kernel log"
  dmesg | grep -i -e tcpc -e typec -e type_c -e pd_ -e "pe_" -e vbus -e swap -e dpm -e "chr_type" -e bc12 -e upm -e musb -e otg -e role -e mt_usb | tail -150
  echo "== short"
  short
}

dt() {
  for d in $(find /proc/device-tree -iname '*tcpc*' -o -iname '*pd-data*' -o -iname '*dpm*' -o -iname '*type_c*' -o -iname '*typec*' -o -iname '*rt17*' -o -iname '*mt636*' -o -iname '*pd_adapter*' 2>/dev/null | head -20); do
    echo "== $d"
    if [ -d $d ]; then
      for f in $d/*; do
        [ -f $f ] || continue
        n=$(stat -c %s $f)
        if [ $n -le 8 ]; then echo "${f##*/} = $(od -An -tx1 $f | tr -s ' ')"; else echo "${f##*/} = $(tr '\0' ' ' < $f | head -c 200)"; fi
      done
    fi
  done
}

watch() {
  n=${1:-30}
  i=0
  last=""
  while [ $i -lt $n ]; do
    s=$(short | cut -d' ' -f2-)
    [ "$s" != "$last" ] && short
    last=$s
    sleep 1
    i=$((i + 1))
  done
}

swap() {
  echo "== before"
  short
  case "$(cat $P/power_role)" in
    *"[source]"*)
      echo "== tablet is powering the dock: asking the dock to take over power (PR_Swap to sink)"
      echo sink > $P/power_role; echo "write result: $?"
      ;;
    *)
      echo "== tablet is already the sink"
      ;;
  esac
  watch 8
  case "$(cat $P/data_role)" in
    *"[device]"*)
      echo "== tablet is the USB device: asking to become host (DR_Swap)"
      echo host > $P/data_role; echo "write result: $?"
      watch 8
      ;;
    *)
      echo "== tablet is already host"
      ;;
  esac
  echo "== after"
  short
  dmesg | grep -i -e tcpc -e typec -e pd_ -e "pe_" -e swap -e dpm | tail -60
}

# Leaves the USB-C port as it is (charging) and switches only the USB controller to host.
hosttest() {
  echo "== before"
  short
  sw=""
  for r in /sys/class/usb_role/*; do [ -f $r/role ] && sw=$r; done
  if [ -z "$sw" ]; then echo "no usb role switch on this tablet"; return; fi
  echo "== switch $sw role=$(cat $sw/role)"
  echo host > $sw/role; echo "write result: $?"
  watch 12
  echo "role now: $(cat $sw/role)"
  hub=no
  for d in /sys/bus/usb/devices/[0-9]*-*; do [ -f $d/idVendor ] && hub=yes; done
  online=$(cat /sys/class/power_supply/primary_chg/online 2>/dev/null)
  echo "devices seen: $hub  charger online: $online"
  if [ "$hub" = no ] || [ "$online" != 1 ]; then
    echo "== did not work, putting the controller back to device"
    echo device > $sw/role; echo "write result: $?"
    watch 5
  fi
  for d in /sys/bus/usb/devices/*; do
    [ -f $d/idVendor ] && echo "${d##*/} $(cat $d/idVendor):$(cat $d/idProduct) class=$(cat $d/bDeviceClass) speed=$(cat $d/speed) $(cat $d/manufacturer 2>/dev/null) $(cat $d/product 2>/dev/null)"
  done
  dmesg | grep -i -e musb -e otg -e role -e mt_usb -e "usb 1-" -e hub -e vbus -e tcpc | tail -60
}

# Makes the USB-C port take power only (never feed the dock), then puts the USB controller in host mode.
sinkhost() {
  SW=/sys/class/usb_role/mt_usb-role-switch
  echo "== before"
  short
  echo "== port to sink only"
  echo sink > $P/port_type; echo "write result: $?"
  watch 10
  echo "== controller to host"
  echo host > $SW/role; echo "write result: $?"
  watch 15
  echo "role now: $(cat $SW/role)"
  online=$(cat /sys/class/power_supply/primary_chg/online 2>/dev/null)
  echo "charger online: $online"
  for d in /sys/bus/usb/devices/*; do
    [ -f $d/idVendor ] && echo "${d##*/} $(cat $d/idVendor):$(cat $d/idProduct) class=$(cat $d/bDeviceClass) $(cat $d/product 2>/dev/null)"
  done
  dumpsys battery | grep -e powered -e status -e level -e "Max charging"
  dmesg | grep -i -e musb -e otg -e role -e vbus -e "usb 1-" -e tcpc -e chg -e charger | tail -90
}

# Waits for the tablet to be unplugged and plugged back into the dock, then puts the controller in host mode.
replug() {
  SW=/sys/class/usb_role/mt_usb-role-switch
  echo "port_type: $(cat $P/port_type)"
  short
  echo "== waiting up to 120 s for the unplug and replug"
  i=0; gone=no; back=no
  while [ $i -lt 240 ]; do
    if [ -d $P-partner ]; then
      [ $gone = yes ] && { back=yes; break; }
    else
      [ $gone = no ] && { gone=yes; echo "$(date +%T) unplugged"; }
    fi
    sleep 0.5; i=$((i + 1))
  done
  [ $back = yes ] || { echo "no replug seen"; short; return; }
  echo "$(date +%T) plugged back in"
  watch 6
  echo "role before: $(cat $SW/role)"
  echo host > $SW/role; echo "host write result: $?"
  watch 20
  echo "role now: $(cat $SW/role)  port_type: $(cat $P/port_type)"
  for d in /sys/bus/usb/devices/*; do
    [ -f $d/idVendor ] && echo "${d##*/} $(cat $d/idVendor):$(cat $d/idProduct) $(cat $d/product 2>/dev/null)"
  done
  dumpsys battery | grep -e powered -e status -e level
  dmesg | grep -i -e otg -e role_sx -e vbus -e "mt_usb_host" -e "do_host_work" -e "TCPC-TYPEC" -e "TCPC-PE" -e boost -e "chg" | tail -70
}

# Restarts host mode on the USB controller (device, then host) and shows what CarLink does next.
rehost() {
  SW=/sys/class/usb_role/mt_usb-role-switch
  echo none > $SW/role; echo "none: $?"; sleep 2
  echo host > $SW/role; echo "host: $?"
  watch 25
  dmesg | grep -e MUSB -e "usb 1-" -e cdc_ncm -e usbfs | grep -v -e musb_h_disable -e spm_resource -e hub_control -e "RX end" -e STALL | tail -80
}

musblog() {
  dmesg | grep -e MUSB -e role_sx -e "usb 1-" -e cdc_ncm -e usbfs -e "TCPC-TYPEC" -e otg -e OTG | grep -v -e musb_h_disable -e spm_resource -e hub_control -e "RX end" -e STALL -e "New USB device" -e "Product:" -e "Manufacturer:" -e "SerialNumber:" | head -400
}

# Asks the port to become the sink while attached (Type-C role swap), then lists the charger chip controls by name.
tosink() {
  echo "== before"; short
  echo "port_type: $(cat $P/port_type)  owner: $(stat -c %U $P/port_type)"
  echo sink > $P/power_role; echo "power_role write result: $?"
  watch 8
  echo "== after"; short
  echo "== charger chip and regulators (names only)"
  for d in /sys/bus/i2c/devices/*-006b /sys/bus/i2c/devices/*-006a; do [ -d $d ] && { echo "$d: $(cat $d/name 2>/dev/null)"; ls $d | tr '\n' ' '; echo; }; done
  for r in /sys/class/regulator/*; do n=$(cat $r/name 2>/dev/null); case "$n" in *otg*|*OTG*|*vbus*|*VBUS*|*boost*) echo "$r $n state=$(cat $r/state 2>/dev/null)";; esac; done
  dmesg | grep -i -e swap -e "role" -e otg -e TCPC | grep -v -e bat_update -e healthd | tail -30
}

# Tablet feeding a dock: turn the charger's power path off for a few seconds and see if the dock takes over.
pathkick() {
  C=/sys/devices/platform/charger/enable_power_path
  echo "power path now: $(cat $C 2>&1)"
  echo "== before"; short
  echo 0 > $C; echo "off: $?"
  watch 4
  echo 1 > $C; echo "on: $?"
  echo "power path now: $(cat $C 2>&1)"
  watch 10
  echo "== after"; short
  echo "== i2c modules"
  for d in /vendor_dlkm/lib/modules /vendor/lib/modules /system_dlkm/lib/modules /lib/modules; do ls $d 2>/dev/null | grep -i i2c | sed "s|^|$d/|"; done
  grep -i i2c /proc/modules | head
}

# Pauses dock mode, asks Android's USB service (vendor HAL) for sink + device, then restarts dock mode.
halsink() {
  M=/data/adb/modules/drehzahl_dock
  touch $M/disable; sleep 3
  echo "port_type: $(cat $P/port_type) owner: $(stat -c %U $P/port_type)"
  echo "== before"; short
  dumpsys usb set-port-roles port0 sink device; echo "set-port-roles result: $?"
  watch 10
  echo "== after"; short
  dumpsys usb | grep -i -A12 "port0" | head -40
  logcat -d -v time | grep -i -e UsbPortManager -e "usb.*hal" -e switchRole -e "port_type" | tail -25
  rm -f $M/disable
  setsid sh $M/service.sh </dev/null >/dev/null 2>&1 &
  sleep 1; echo "dock mode running again: $(pgrep -f 'drehzahl_dock/servic[e]' | head -1)"
}

# MediaTek's own USB-C role file: 1 = take power only. Written, never read (some files in that folder crash the kernel when read).
tcpcsink() {
  echo "== before"; short
  echo 1 > /sys/class/tcpc/type_c_port0/typec_role; echo "typec_role write result: $?"
  watch 12
  echo "== after"; short
  tail -n 5 /data/adb/drehzahl_dock.log
}

# Tablet set to take power only, dock powered but not supplying: re-apply the role (a fresh plug-in for the dock).
replugsoft() {
  R=/sys/class/tcpc/type_c_port0/typec_role
  echo "== before"; short
  echo 1 > $R; echo "write 1 again: $?"
  watch 6
  if [ ! -d $P-partner ]; then
    echo "== still nothing: try 5 (prefer taking power) then 1"
    echo 5 > $R; echo "write 5: $?"; watch 6
    echo 1 > $R; echo "write 1: $?"; watch 6
  fi
  echo "== after"; short
  tail -n 5 /data/adb/drehzahl_dock.log
}

case "$1" in
  state) state ;;
  dt) dt ;;
  watch) watch $2 ;;
  swap) swap ;;
  hosttest) hosttest ;;
  sinkhost) sinkhost ;;
  replug) replug ;;
  rehost) rehost ;;
  tosink) tosink ;;
  pathkick) pathkick ;;
  halsink) halsink ;;
  tcpcsink) tcpcsink ;;
  replugsoft) replugsoft ;;
  musblog) musblog ;;
  *) echo "use: state | dt | watch <s> | swap" ;;
esac
