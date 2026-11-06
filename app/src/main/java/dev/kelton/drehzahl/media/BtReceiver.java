package dev.kelton.drehzahl.media;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.core.EventLog;
import dev.kelton.drehzahl.sys.Root;

/**
 * Bluetooth receiver: lets a phone play music to this tablet like a car stereo (A2DP sink + AVRCP controller).
 * Android ships with these roles switched off on phones and tablets. On a rooted tablet a small
 * module switches them on at every boot; without root this class only reports that they are off.
 */
public final class BtReceiver {
    public static final int A2DP_SINK = 11;
    public static final String MODULE = "/data/adb/modules/drehzahl_btsink";
    public static final String COD_CAR_AUDIO = "36,4,32";
    private static final long PROBE_MS = 3500;
    private static final long NUDGE_DELAY_MS = 1500;

    public interface Listener { void onReceiverChanged(); }

    private static BtReceiver inst;
    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private BluetoothProfile sink;
    private boolean probing, probed;
    private boolean busy;
    private String lastOutput = "";

    public static BtReceiver get(Context c) {
        if (inst == null) inst = new BtReceiver(c.getApplicationContext());
        return inst;
    }

    private BtReceiver(Context c) { ctx = c; }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        h.post(new Runnable() { public void run() { for (Listener l : new ArrayList<>(listeners)) l.onReceiverChanged(); } });
    }

    public BluetoothAdapter adapter() {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    public boolean bluetoothOn() {
        BluetoothAdapter a = adapter();
        try { return a != null && a.isEnabled(); } catch (SecurityException e) { return false; }
    }

    /** True once the system answered for the receiver role. */
    public boolean available() { return sink != null; }
    public boolean checked() { return probed; }
    public boolean busy() { return busy; }
    public String lastOutput() { return lastOutput; }

    /** Asks the Bluetooth service for the receiver role. No answer within a few seconds means it is off. */
    public void probe() {
        if (probing) return;
        BluetoothAdapter a = adapter();
        if (a == null) { probed = true; changed(); return; }
        probing = true;
        boolean asked;
        try {
            asked = a.getProfileProxy(ctx, new BluetoothProfile.ServiceListener() {
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    sink = proxy; probing = false; probed = true; changed();
                }
                public void onServiceDisconnected(int profile) { sink = null; changed(); }
            }, A2DP_SINK);
        } catch (RuntimeException e) { asked = false; }
        if (!asked) { probing = false; probed = true; changed(); return; }
        h.postDelayed(new Runnable() {
            public void run() { if (probing) { probing = false; probed = true; changed(); } }
        }, PROBE_MS);
    }

    public void reprobe() {
        BluetoothAdapter a = adapter();
        if (sink != null && a != null) { try { a.closeProfileProxy(A2DP_SINK, sink); } catch (RuntimeException ignored) { } }
        sink = null; probed = false; probing = false;
        probe();
    }

    /** Names of phones connected to the receiver right now. */
    public List<String> connected() {
        List<String> out = new ArrayList<>();
        if (sink == null) return out;
        try {
            for (BluetoothDevice d : sink.getConnectedDevices()) {
                String n = d.getName();
                out.add(n == null || n.isEmpty() ? d.getAddress() : n);
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    public String tabletName() {
        BluetoothAdapter a = adapter();
        try { return a == null ? "" : a.getName(); } catch (SecurityException e) { return ""; }
    }

    public boolean rename(String name) {
        BluetoothAdapter a = adapter();
        try { return a != null && a.setName(name); } catch (SecurityException e) { return false; }
    }

    public boolean moduleInstalled() { return Prefs.b(Prefs.BT_MODULE, false); }

    /** Android 10 to 12 read the Bluetooth roles from resources, so a reboot with an overlay is needed. */
    public static boolean usesOverlay() { return android.os.Build.VERSION.SDK_INT < 33; }

    public boolean rebootPending() { return Prefs.b(Prefs.BT_REBOOT, false); }

    public void reboot() {
        new Thread(new Runnable() { public void run() { Root.run("svc power reboot || reboot", 15000); } }, "reboot").start();
    }

    // ---------- switching the roles on and off (root) ----------

    public interface Done { void on(boolean ok, String output); }

    public void enable(Done done) {
        if (usesOverlay()) {
            String apk = copyOverlay();
            if (apk == null) { if (done != null) done.on(false, "Could not unpack the overlay"); return; }
            runRoot(enableOverlayScript(apk), true, done);
        } else runRoot(enableScript(), true, done);
    }

    public void disable(Done done) { runRoot(usesOverlay() ? disableOverlayScript() : disableScript(), false, done); }

    private String copyOverlay() {
        java.io.File f = new java.io.File(ctx.getCacheDir(), "btsink-overlay.apk");
        try (java.io.InputStream in = ctx.getAssets().open("btsink-overlay.apk");
             java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        } catch (java.io.IOException e) { return null; }
        return f.getAbsolutePath();
    }

    private void runRoot(final String script, final boolean on, final Done done) {
        if (busy) return;
        busy = true;
        changed();
        new Thread(new Runnable() {
            public void run() {
                final Root.Result r = Root.run(script, 45000);
                lastOutput = r.out;
                final boolean ok = r.ok && r.out.contains("DONE");
                if (ok) {
                    Prefs.put(Prefs.BT_MODULE, on);
                    Prefs.put(Prefs.BT_REBOOT, usesOverlay());
                }
                EventLog.add("bt receiver " + (on ? "on" : "off") + ": " + (r.ok ? "ok" : "failed " + r.code));
                h.postDelayed(new Runnable() {
                    public void run() {
                        busy = false;
                        reprobe();
                        if (done != null) done.on(ok, r.out);
                    }
                }, 2500);
            }
        }, "bt-receiver").start();
    }

    private static String resetpropFinder() {
        return "RP=\n"
                + "for p in /data/adb/ksu/bin/resetprop /data/adb/ap/bin/resetprop; do [ -x $p ] && RP=$p; done\n"
                + "[ -z \"$RP\" ] && command -v resetprop >/dev/null 2>&1 && RP=resetprop\n"
                + "[ -z \"$RP\" ] && command -v magisk >/dev/null 2>&1 && RP='magisk resetprop'\n";
    }

    private static String restartBluetooth() {
        return "cmd bluetooth_manager disable\n"
                + "sleep 3\n"
                + "for n in com.android.bluetooth com.google.android.bluetooth; do pid=$(pidof $n); [ -n \"$pid\" ] && kill $pid; done\n"
                + "sleep 2\n"
                + "cmd bluetooth_manager enable\n";
    }

    static String enableScript() {
        return "M=" + MODULE + "\n"
                + "O=/data/adb/drehzahl-btsink.orig\n"
                + "[ -f $O ] || getprop bluetooth.device.class_of_device > $O\n"
                + "mkdir -p $M\n"
                + "rm -f $M/disable $M/remove\n"
                + "printf 'id=drehzahl_btsink\\nname=Drehzahl Bluetooth receiver\\nversion=1\\nversionCode=1\\n"
                + "author=Kelton Chelbian\\ndescription=Lets a phone play music to this tablet over Bluetooth\\n' > $M/module.prop\n"
                + "printf 'bluetooth.profile.a2dp.sink.enabled=true\\nbluetooth.profile.avrcp.controller.enabled=true\\n"
                + "bluetooth.a2dp.src_sink_coexist.enabled=true\\nbluetooth.device.class_of_device=" + COD_CAR_AUDIO + "\\n' > $M/system.prop\n"
                + resetpropFinder()
                + "if [ -n \"$RP\" ]; then\n"
                + "  while IFS='=' read -r k v; do [ -n \"$k\" ] && $RP $k $v; done < $M/system.prop\n"
                + "else\n"
                + "  while IFS='=' read -r k v; do [ -n \"$k\" ] && setprop $k $v; done < $M/system.prop\n"
                + "fi\n"
                + "getprop bluetooth.profile.a2dp.sink.enabled\n"
                + restartBluetooth()
                + "echo DONE\n";
    }

    static String enableOverlayScript(String apk) {
        String dir = "$M/system/product/overlay/DrehzahlBtReceiver";
        return "M=" + MODULE + "\n"
                + "mkdir -p " + dir + "\n"
                + "rm -f $M/disable $M/remove $M/system.prop\n"
                + "printf 'id=drehzahl_btsink\\nname=Drehzahl Bluetooth receiver\\nversion=1\\nversionCode=1\\n"
                + "author=Kelton Chelbian\\ndescription=Lets a phone play music to this tablet over Bluetooth\\n' > $M/module.prop\n"
                + "cp " + apk + " " + dir + "/DrehzahlBtReceiver.apk || exit 1\n"
                + "chmod 755 $M/system $M/system/product $M/system/product/overlay " + dir + "\n"
                + "chmod 644 " + dir + "/DrehzahlBtReceiver.apk\n"
                + "chcon -R u:object_r:system_file:s0 $M/system 2>/dev/null\n"
                + "ls -l " + dir + "\n"
                + "echo DONE\n";
    }

    static String disableOverlayScript() {
        return "rm -rf " + MODULE + "\n" + "echo DONE\n";
    }

    static String disableScript() {
        return "M=" + MODULE + "\n"
                + "O=/data/adb/drehzahl-btsink.orig\n"
                + "rm -rf $M\n"
                + resetpropFinder()
                + "C=$(cat $O 2>/dev/null)\n"
                + "for k in bluetooth.profile.a2dp.sink.enabled bluetooth.profile.avrcp.controller.enabled bluetooth.a2dp.src_sink_coexist.enabled; do\n"
                + "  if [ -n \"$RP\" ]; then $RP $k false; else setprop $k false; fi\n"
                + "done\n"
                + "if [ -n \"$C\" ]; then if [ -n \"$RP\" ]; then $RP bluetooth.device.class_of_device $C; else setprop bluetooth.device.class_of_device $C; fi; fi\n"
                + "rm -f $O\n"
                + restartBluetooth()
                + "echo DONE\n";
    }

    // ---------- making sure the music is heard ----------

    private MediaController watched;
    private boolean wasPlaying;
    private final MediaController.Callback btCb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { onBtState(s); }
        @Override public void onSessionDestroyed() { watched = null; }
    };

    public static boolean isBluetoothSession(MediaController c) {
        return c != null && c.getPackageName() != null && c.getPackageName().contains("bluetooth");
    }

    /** Called by MediaHub with every session list. Follows the Bluetooth player's session. */
    void sessions(List<MediaController> list) {
        MediaController found = null;
        if (list != null) for (MediaController c : list) if (isBluetoothSession(c)) { found = c; break; }
        if (found == watched || (found != null && watched != null && found.getSessionToken().equals(watched.getSessionToken()))) return;
        if (watched != null) watched.unregisterCallback(btCb);
        watched = found;
        wasPlaying = false;
        if (watched != null) { watched.registerCallback(btCb, h); onBtState(watched.getPlaybackState()); }
    }

    /**
     * Phone and tablet builds of Android only give the receiver the speaker when someone presses play
     * on the tablet. When the phone starts the music and nothing is heard, press play for the user once.
     */
    private void onBtState(PlaybackState s) {
        boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        if (playing && !wasPlaying && Prefs.b(Prefs.BT_NUDGE, true)) {
            h.postDelayed(new Runnable() {
                public void run() {
                    MediaController c = watched;
                    if (c == null) return;
                    PlaybackState now = c.getPlaybackState();
                    if (now == null || now.getState() != PlaybackState.STATE_PLAYING) return;
                    AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null && am.isMusicActive()) return;
                    EventLog.add("bt receiver: phone playing but silent, pressing play");
                    c.getTransportControls().play();
                }
            }, NUDGE_DELAY_MS);
        }
        wasPlaying = playing;
        changed();
    }
}
