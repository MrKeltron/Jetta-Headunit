package dev.kelton.drehzahl.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.sys.Root;
import dev.kelton.drehzahl.sys.WifiAutojoin;

/**
 * Wireless CarPlay (CarLink) runs over a Wi-Fi Direct group. While the tablet is not joined to a
 * Wi-Fi network, Android keeps searching for one, and every search takes the single radio off the
 * CarPlay channel for a moment, so audio packets are lost and music stutters.
 *
 * CarLink keeps a group up while it waits for the phone, so the guard waits until a phone has
 * joined the group. While one has, this turns Wi-Fi auto-join off (the searches stop with it) and turns it back
 * on when the group ends. It needs root. A flag is saved before every pause, so a pause that was
 * never undone (Drehzahl killed, power cut) is undone the next time this starts. Android also turns
 * auto-join back on by itself when the tablet restarts.
 */
public final class WifiGuard {
    private static final long UP_DELAY_MS = 2000;
    private static final long DOWN_DELAY_MS = 8000;
    private static final long RETRY_MS = 30000;
    private static final long HELPER_TIMEOUT_MS = 25000;
    private static final String NEARBY = "android.permission.NEARBY_WIFI_DEVICES";

    private static WifiGuard inst;

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean registered;
    private boolean group;
    private boolean busy;
    private volatile String state = "";

    private final Runnable apply = new Runnable() { public void run() { apply(); } };

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { fromIntent(i); }
    };

    private WifiGuard(Context c) { ctx = c.getApplicationContext(); }

    public static synchronized WifiGuard get(Context c) {
        if (inst == null) inst = new WifiGuard(c);
        return inst;
    }

    public static boolean enabled() { return Prefs.b(Prefs.WIFI_GUARD, true); }

    public void start() {
        if (registered) return;
        registered = true;
        IntentFilter f = new IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        Intent sticky;
        if (Build.VERSION.SDK_INT >= 33) sticky = ctx.registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED);
        else sticky = ctx.registerReceiver(rx, f);
        if (sticky != null) fromIntent(sticky);
        else ask();
        if (enabled()) {
            if (!Root.present()) state = "needs root";
            else if (!hasNearby()) grantNearby();
        }
        if (Prefs.b(Prefs.WIFI_GUARD_PAUSED, false)) EventLog.add("Wi-Fi guard: network search was left paused, checking");
        h.postDelayed(apply, DOWN_DELAY_MS);
    }

    public void stop() {
        if (!registered) return;
        registered = false;
        try { ctx.unregisterReceiver(rx); } catch (Exception ignored) { }
        h.removeCallbacks(apply);
        group = false;
        if (Prefs.b(Prefs.WIFI_GUARD_PAUSED, false)) io.execute(new Runnable() { public void run() { switchSearch(true); } });
    }

    /** Called when the setting changes. */
    public void settingChanged() {
        if (enabled() && Root.present() && !hasNearby()) grantNearby();
        h.removeCallbacks(apply);
        h.post(apply);
    }

    /** A short line for the Settings page. */
    public String describe() {
        if (!enabled()) return "Off. Android searches for Wi-Fi networks during wireless CarPlay as usual.";
        if (!Root.present()) return "Needs root (Magisk or similar). Without it nothing changes.";
        if (Build.VERSION.SDK_INT >= 33 && !hasNearby()) return "Waiting for the Nearby devices permission, used to see when wireless CarPlay starts.";
        if (Prefs.b(Prefs.WIFI_GUARD_PAUSED, false)) return "Wireless CarPlay is on. Network search is paused.";
        if (state.length() > 0) return "Ready. Last change: " + state + ".";
        return "Ready. Network search pauses when wireless CarPlay starts and comes back when it ends.";
    }

    private void fromIntent(Intent i) {
        WifiP2pInfo info = i.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
        WifiP2pGroup g = i.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
        groupChanged(info != null && info.groupFormed, g);
    }

    /** A phone is on the link: the group is formed and has a client, or the client list is hidden. */
    private void groupChanged(boolean formed, WifiP2pGroup g) {
        int clients = g == null ? -1 : g.getClientList().size();
        groupChanged(formed && clients != 0, formed, clients);
    }

    /** Asks the Wi-Fi Direct service once, for when the change broadcast is not sticky. */
    private void ask() {
        try {
            WifiP2pManager pm = (WifiP2pManager) ctx.getSystemService(Context.WIFI_P2P_SERVICE);
            if (pm == null) return;
            final WifiP2pManager.Channel ch = pm.initialize(ctx, Looper.getMainLooper(), null);
            if (ch == null) return;
            pm.requestGroupInfo(ch, new WifiP2pManager.GroupInfoListener() {
                public void onGroupInfoAvailable(WifiP2pGroup g) {
                    groupChanged(g != null, g);
                    if (Build.VERSION.SDK_INT >= 27) ch.close();
                }
            });
        } catch (Exception e) {
            EventLog.add("Wi-Fi guard: could not ask about Wi-Fi Direct, " + e.getMessage());
        }
    }

    private boolean formedSeen;

    private void groupChanged(boolean up, boolean formed, int clients) {
        if (formed != formedSeen) {
            formedSeen = formed;
            EventLog.add(formed ? "Wi-Fi Direct group up (CarLink waiting for the phone)" : "Wi-Fi Direct group ended");
        }
        if (up == group) return;
        group = up;
        EventLog.add(up ? "phone joined the Wi-Fi Direct link (" + (clients < 0 ? "client list hidden" : clients + " client") + ")"
                : "no phone on the Wi-Fi Direct link");
        h.removeCallbacks(apply);
        h.postDelayed(apply, up ? UP_DELAY_MS : DOWN_DELAY_MS);
    }

    private void apply() {
        if (busy) { h.postDelayed(apply, 1000); return; }
        final boolean pause = group && enabled() && registered;
        boolean paused = Prefs.b(Prefs.WIFI_GUARD_PAUSED, false);
        if (pause == paused) return;
        if (!Root.present()) {
            if (pause) state = "needs root";
            else Prefs.put(Prefs.WIFI_GUARD_PAUSED, false);
            return;
        }
        busy = true;
        io.execute(new Runnable() {
            public void run() {
                final boolean ok = switchSearch(!pause);
                h.post(new Runnable() {
                    public void run() {
                        busy = false;
                        if (!ok && !pause) h.postDelayed(apply, RETRY_MS);
                        else h.post(apply);
                    }
                });
            }
        });
    }

    /** Runs on the io thread. True when the change is known to be done. */
    private boolean switchSearch(boolean on) {
        if (!on) Prefs.put(Prefs.WIFI_GUARD_PAUSED, true);
        String r = helper(on);
        boolean ok = r.startsWith("ok");
        boolean nothingChanged = r.startsWith("unsupported") || r.startsWith("denied") || r.startsWith("error");
        if (on && ok) Prefs.put(Prefs.WIFI_GUARD_PAUSED, false);
        if (!on && nothingChanged) Prefs.put(Prefs.WIFI_GUARD_PAUSED, false);
        state = (on ? "search back on" : "search paused") + (ok ? "" : " failed (" + r + ")");
        EventLog.add("Wi-Fi guard: " + state + (ok ? ", " + r : ""));
        return ok;
    }

    /** Runs the helper as the shell user first (what Android expects for this call), then as root. */
    private String helper(boolean on) {
        String cmd = "CLASSPATH='" + ctx.getPackageCodePath() + "' /system/bin/app_process /system/bin "
                + WifiAutojoin.class.getName() + (on ? " on" : " off");
        String r = parse(Root.run("su 2000 -c \"" + cmd + "\"", HELPER_TIMEOUT_MS).out);
        if (r.startsWith("ok")) return r;
        String r2 = parse(Root.run(cmd, HELPER_TIMEOUT_MS).out);
        return r2.startsWith("ok") ? r2 : r + " / " + r2;
    }

    private static String parse(String out) {
        if (out == null) return "no answer";
        String last = null;
        for (String line : out.split("\n")) {
            line = line.trim();
            if (line.startsWith(WifiAutojoin.MARK + " ")) last = line.substring(WifiAutojoin.MARK.length() + 1);
        }
        if (last != null) return last;
        String t = out.trim();
        return "no answer" + (t.isEmpty() ? "" : ": " + (t.length() > 120 ? t.substring(0, 120) : t));
    }

    private boolean hasNearby() {
        return Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(NEARBY) == PackageManager.PERMISSION_GRANTED;
    }

    /** With root, Drehzahl can give itself the Nearby devices permission instead of asking. */
    private void grantNearby() {
        if (Build.VERSION.SDK_INT < 33) return;
        io.execute(new Runnable() {
            public void run() {
                Root.Result r = Root.run("pm grant " + ctx.getPackageName() + " " + NEARBY, 15000);
                EventLog.add("Wi-Fi guard: Nearby devices permission " + (r.ok ? "granted" : "not granted (" + r.out.trim() + ")"));
            }
        });
    }
}
