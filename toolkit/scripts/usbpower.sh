# Records every change in the USB-C port and charging state to /data/local/tmp/usbpower.log,
# every 3 s, until the tablet restarts. Start as root: setsid sh usbpower.sh &
L=/data/local/tmp/usbpower.log
snap() {
  for p in /sys/class/typec/port*; do
    case "$p" in *partner*|*cable*|*plug*) continue;; esac
    [ -d "$p" ] || continue
    printf '%s pr=%s dr=%s mode=%s' "${p##*/}" "$(cat $p/power_role 2>/dev/null)" "$(cat $p/data_role 2>/dev/null)" "$(cat $p/power_operation_mode 2>/dev/null)"
    [ -d "$p-partner" ] && printf ' partner'
    printf ' | '
  done
  for s in /sys/class/power_supply/*; do
    [ -f "$s/online" ] || continue
    printf '%s=%s ' "${s##*/}" "$(cat $s/online 2>/dev/null)"
  done
  dumpsys battery | grep -E "AC powered|USB powered|status:" | tr -s ' ' | tr '\n' ' '
}
last=""
echo "$(date '+%m-%d %T') recorder started" >> $L
while true; do
  s=$(snap)
  if [ "$s" != "$last" ]; then
    echo "$(date '+%m-%d %T') $s" >> $L
    dmesg 2>/dev/null | grep -i -E "tcpc|typec|type_c|pd_|vbus|pr_swap|power role|snk_|src_" | tail -12 | sed 's/^/    /' >> $L
    last=$s
  fi
  [ "$(stat -c %s $L 2>/dev/null || echo 0)" -gt 3000000 ] && mv $L $L.old
  sleep 3
done
