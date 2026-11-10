package dev.kelton.drehzahl.core;

import android.os.Handler;
import android.os.Looper;

import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.sys.Root;

/**
 * Dock mode: a powered USB-C dock charges the tablet and wired CarPlay runs through the same dock.
 *
 * Cheap docks give the tablet plain 5 V without USB-PD, so Android cannot be both charging and USB
 * host the normal way. Without help the tablet either charges and cannot see the phone, or sees the
 * phone and feeds the dock and phone from its own battery, even with the car off.
 *
 * With dock mode on, a small root module asks the USB-C port to take power when the dock regains
 * power (Android's USB service resets that setting on every unplug, so the module takes the file
 * away from it), and every time
 * power arrives it restarts the USB controller in host mode, so the dock's hub and the phone show up
 * while the tablet charges. If nothing answers on USB (a plain charger or a computer) it puts the
 * controller back to normal, so file transfer and USB debugging still work.
 */
public final class DockMode {
    public static final String MODULE = "/data/adb/modules/drehzahl_dock";
    public static final String LOG = "/data/adb/drehzahl_dock.log";

    public interface Done { void on(boolean ok, String output); }

    private static final Handler H = new Handler(Looper.getMainLooper());

    private DockMode() { }

    public static boolean on() { return Prefs.b(Prefs.DOCK_MODE, false); }

    public static void set(final boolean on, final Done done) {
        run(on ? enableScript() : disableScript(), new Done() {
            public void on(boolean ok, String out) {
                boolean good = ok && out.contains("DONE");
                if (good) Prefs.put(Prefs.DOCK_MODE, on);
                EventLog.add("dock mode " + (on ? "on" : "off") + (good ? "" : ": failed"));
                if (done != null) done.on(good, out);
            }
        });
    }

    /** The last lines of the module's log, newest last. */
    public static void log(final int lines, Done done) {
        run("tail -n " + lines + " " + LOG + " 2>/dev/null; echo; echo running=$(pgrep -f 'drehzahl_dock/servic[e]' | head -1)", done);
    }

    /** Starts the module again if it is installed but not running (after an app update, for example). */
    public static void ensureRunning() {
        if (!on() || !Root.present()) return;
        run("M=" + MODULE + "\n"
                + "[ -f $M/service.sh ] || exit 0\n"
                + "pgrep -f 'drehzahl_dock/servic[e]' >/dev/null || setsid sh $M/service.sh </dev/null >/dev/null 2>&1 &\n"
                + "echo DONE\n", null);
    }

    private static void run(final String script, final Done done) {
        new Thread(new Runnable() {
            public void run() {
                final Root.Result r = Root.run(script, 20000);
                if (done == null) return;
                H.post(new Runnable() { public void run() { done.on(r.ok, r.out); } });
            }
        }, "dock-mode").start();
    }

    static String enableScript() {
        return "M=" + MODULE + "\n"
                + "pkill -f 'drehzahl_dock/servic[e]'\n"
                + "mkdir -p $M\n"
                + "rm -f $M/disable $M/remove\n"
                + "printf 'id=drehzahl_dock\\nname=Drehzahl dock mode\\nversion=1\\nversionCode=1\\n"
                + "author=Kelton Chelbian\\ndescription=Charge from a powered USB-C dock and keep wired CarPlay through it\\n' > $M/module.prop\n"
                + "cat > $M/post-fs-data.sh <<'EOF'\n" + POST_FS + "EOF\n"
                + "cat > $M/service.sh <<'EOF'\n" + SERVICE + "EOF\n"
                + "chmod 755 $M/service.sh $M/post-fs-data.sh\n"
                + "setsid sh $M/service.sh </dev/null >/dev/null 2>&1 &\n"
                + "sleep 1\n"
                + "pgrep -f 'drehzahl_dock/servic[e]' >/dev/null && echo DONE\n";
    }

    static String disableScript() {
        return "rm -rf " + MODULE + "\n"
                + "sleep 2\n"
                + "pkill -f 'drehzahl_dock/servic[e]'\n"
                + "echo dual > /sys/class/typec/port0/port_type 2>/dev/null\n"
                + "chown system:system /sys/class/typec/port0/port_type; chmod 0664 /sys/class/typec/port0/port_type\n"
                + "[ -f " + TCPC_ROLE + " ] && echo 5 > " + TCPC_ROLE + "\n"
                + "echo DONE\n";
    }

    /**
     * MediaTek's own Type-C role: 1 takes power only, 5 (try to take power, else give it) is how the tablet ships.
     * Never read it: some files in that folder crash the kernel when read. 1 was tried on Nov 9: the tablet stopped
     * feeding the Razer dock, but the dock then never supplied power either (it seems to need the tablet's power to
     * pick a role), leaving no charging and no CarPlay. So the module only puts the shipped value back.
     */
    static final String TCPC_ROLE = "/sys/class/tcpc/type_c_port0/typec_role";

    static final String POST_FS = "T=/sys/class/typec/port0/port_type\n"
            + "[ -f $T ] && { chown root:root $T; chmod 0644 $T; echo sink > $T; }\n";

    /** The module's loop. Only reads the standard Type-C and USB role files: some vendor USB-C debug files crash this tablet. */
    static final String SERVICE = ""
            + "M=${0%/*}\n"
            + "P=/sys/class/typec/port0\n"
            + "L=" + LOG + "\n"
            + "SW=\n"
            + "for r in /sys/class/usb_role/*; do [ -f $r/role ] && SW=$r; done\n"
            + "log() {\n"
            + "  echo \"$(date '+%m-%d %T') $*\" >> $L\n"
            + "  [ \"$(stat -c %s $L 2>/dev/null || echo 0)\" -gt 200000 ] && mv $L $L.old\n"
            + "}\n"
            + "R=/sys/class/tcpc/type_c_port0/typec_role\n"
            + "shipped() { [ -f $R ] && echo 5 > $R 2>/dev/null; }\n"
            + "sinkonly() {\n"
            + "  [ \"$(stat -c %U $P/port_type 2>/dev/null)\" = root ] || { chown root:root $P/port_type; chmod 0644 $P/port_type; }\n"
            + "  case \"$(cat $P/port_type 2>/dev/null)\" in *'[sink]'*) ;; *) echo sink > $P/port_type 2>/dev/null && log 'port set to take power only' ;; esac\n"
            + "}\n"
            + "found() {\n"
            + "  for d in /sys/bus/usb/devices/[0-9]*-*; do [ -f $d/idVendor ] && return 0; done\n"
            + "  return 1\n"
            + "}\n"
            + "names() {\n"
            + "  n=''\n"
            + "  for d in /sys/bus/usb/devices/[0-9]*-*; do [ -f $d/product ] && n=\"$n$(cat $d/product), \"; done\n"
            + "  echo \"${n%, }\"\n"
            + "}\n"
            + "host() {\n"
            + "  [ -n \"$SW\" ] || { log 'no USB role switch on this tablet'; return; }\n"
            + "  for try in 1 2; do\n"
            + "    echo none > $SW/role; sleep 2\n"
            + "    echo host > $SW/role\n"
            + "    i=0; while [ $i -lt 12 ] && ! found; do sleep 1; i=$((i + 1)); done\n"
            + "    if found; then sleep 2; log \"USB host on: $(names)\"; return; fi\n"
            + "  done\n"
            + "  echo device > $SW/role\n"
            + "  log 'nothing answered on USB (charger or computer), USB left as normal'\n"
            + "}\n"
            + "role() { case \"$(cat $P/power_role 2>/dev/null)\" in *'[sink]'*) echo sink ;; *) echo source ;; esac; }\n"
            + "log 'dock mode started'\n"
            + "shipped\n"
            + "was=''; pr=''\n"
            + "while [ -f $M/service.sh ] && [ ! -f $M/disable ]; do\n"
            + "  if [ -d $P-partner ]; then now=in; else now=out; fi\n"
            + "  if [ \"$now\" != \"$was\" ]; then\n"
            + "    if [ $now = out ]; then\n"
            + "      sleep 1\n"
            + "      [ -n \"$was\" ] && log 'unplugged or power gone'\n"
            + "      pr=''\n"
            + "    else\n"
            + "      sleep 2\n"
            + "      pr=$(role)\n"
            + "      if [ $pr = sink ]; then log 'power in, charging'; host\n"
            + "      else log 'the tablet is powering the USB-C device (dock without power)'; fi\n"
            + "    fi\n"
            + "    was=$now\n"
            + "  elif [ $now = in ]; then\n"
            + "    r=$(role)\n"
            + "    if [ \"$r\" != \"$pr\" ]; then\n"
            + "      if [ $r = sink ]; then log 'power in, charging'; host\n"
            + "      else log 'the dock lost power, the tablet is powering it now'; fi\n"
            + "      pr=$r\n"
            + "    fi\n"
            + "  fi\n"
            + "  sinkonly\n"
            + "  sleep 1\n"
            + "done\n"
            + "echo dual > $P/port_type 2>/dev/null\n"
            + "chown system:system $P/port_type; chmod 0664 $P/port_type\n"
            + "[ -f $R ] && echo 5 > $R 2>/dev/null\n"
            + "log 'dock mode stopped'\n";
}
