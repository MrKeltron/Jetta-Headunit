# Allows one app in Magisk without the popup. Usage: sh grant-root.sh <package>
P=$1
U=$(stat -c %u /data/data/$P 2>/dev/null)
[ -z "$U" ] && echo "no app data for $P" && exit 1
magisk --sqlite "REPLACE INTO policies (uid,policy,until,logging,notification) VALUES($U,2,0,1,0)"
magisk --sqlite "SELECT uid,policy FROM policies WHERE uid=$U"
echo "granted $P (uid $U)"
