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
 * on when the group ends. It needs root, or the helper the PC toolkit starts (sys.ShellHelper),
 * which runs as the shell user until the tablet restarts. A flag is saved before every pause, so a pause that was
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
            if (!Root.present() && !ShellLink.configured() && !Prefs.b(Prefs.SELF_ADB_OK, false)) state = "needs root or the helper";
            else if (Root.present() && !hasNearby()) grantNearby();
        }
        if (!Root.present() && (ShellLink.configured() || Prefs.b(Prefs.SELF_ADB_OK, false))) checkHelper();
        watchWifi();
        h.removeCallbacks(watchdog);
        h.postDelayed(watchdog, WATCHDOG_MS);
        h.removeCallbacks(poll);
        h.postDelayed(poll, POLL_MS);
        if (Prefs.b(Prefs.WIFI_GUARD_PAUSED, false)) EventLog.add("Wi-Fi guard: network search was left paused, checking");
        h.postDelayed(apply, DOWN_DELAY_MS);
    }

    public void stop() {
        if (!registered) return;
        registered = false;
        try { ctx.unregisterReceiver(rx); } catch (Exception ignored) { }
        h.removeCallbacks(poll);
        h.removeCallbacks(probe);
        if (lowLat) { lowLat = false; io.execute(new Runnable() { public void run() { ShellLink.call("lowlat off"); } }); }
        try { if (p2pChannel != null && Build.VERSION.SDK_INT >= 27) p2pChannel.close(); } catch (Exception ignored) { }
        p2pChannel = null;
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

    private static final long WATCHDOG_MS = 120000;

    /** Android can kill the helper when memory runs low; look for it every 2 minutes and bring it back. */
    private final Runnable watchdog = new Runnable() {
        public void run() {
            if (!registered) return;
            if (enabled() && !Root.present() && (ShellLink.configured() || Prefs.b(Prefs.SELF_ADB_OK, false))) checkHelper();
            h.postDelayed(this, WATCHDOG_MS);
        }
    };

    private boolean watching;

    /** When the tablet joins a Wi-Fi network and the helper is down, Drehzahl can restart it through wireless debugging. */
    private void watchWifi() {
        if (watching || Root.present()) return;
        watching = true;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.registerNetworkCallback(new android.net.NetworkRequest.Builder()
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).build(),
                    new android.net.ConnectivityManager.NetworkCallback() {
                        @Override public void onAvailable(android.net.Network n) {
                            if (registered && enabled() && !helperState.equals("running") && Prefs.b(Prefs.SELF_ADB_OK, false))
                                h.postDelayed(new Runnable() { public void run() { checkHelper(); } }, 10000);
                        }
                    });
        } catch (Exception e) {
            EventLog.add("Wi-Fi guard: cannot watch Wi-Fi, " + e.getMessage());
        }
    }

    /** The PC toolkit just started the helper. */
    public void helperArrived() {
        checkHelper();
        h.removeCallbacks(apply);
        h.post(apply);
    }

    private volatile String helperState = "";

    private void checkHelper() {
        io.execute(new Runnable() {
            public void run() {
                String r = ShellLink.call("ping");
                if (!r.startsWith("ok") && enabled() && !Root.present()) {
                    String st = HelperStarter.auto(ctx);
                    EventLog.add("Wi-Fi guard: helper restart on the tablet: " + st);
                    if (st.startsWith("ok")) r = ShellLink.call("ping");
                }
                boolean was = helperState.equals("running");
                helperState = r.startsWith("ok") ? "running" : "not running";
                if (!was || !r.startsWith("ok")) EventLog.add("Wi-Fi guard: helper " + (r.startsWith("ok") ? "answered (" + r + ")" : r));
                if (r.startsWith("ok")) { quickTries = 0; h.removeCallbacks(apply); h.post(apply); }
                else if (quickTries < 6) {
                    // Right after a restart wireless debugging takes a while to show up: try again soon
                    // instead of waiting for the 2-minute watchdog.
                    quickTries++;
                    h.removeCallbacks(quickRetry);
                    h.postDelayed(quickRetry, 20000);
                }
            }
        });
    }

    private int quickTries;
    private final Runnable quickRetry = new Runnable() { public void run() { if (registered && enabled()) checkHelper(); } };

    /** A short line for the Settings page. */
    public String describe() {
        if (!enabled()) return "Off. Android searches for Wi-Fi networks during wireless CarPlay as usual.";
        boolean root = Root.present();
        boolean self = Prefs.b(Prefs.SELF_ADB_OK, false);
        if (!root && !ShellLink.configured() && !self) return "Needs root, or the helper: run Install in the PC toolkit once, then tap \"Let Drehzahl restart it\" below.";
        if (!root && helperState.equals("not running")) return self
                ? "The helper is not running. Drehzahl restarts it by itself once the tablet is on a Wi-Fi network."
                : "The helper stopped (the tablet restarted). Run \"Start Wi-Fi helper\" in the PC toolkit, then tap \"Let Drehzahl restart it\" below so it is not needed again.";
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
        if (g != null && Build.VERSION.SDK_INT >= 29 && g.getFrequency() > 0) freq = g.getFrequency();
        groupChanged(formed && clients != 0, formed, clients);
    }

    private int freq;

    /** "5180 MHz, 5 GHz channel 36": which band the CarPlay link runs on (2.4 GHz shares airtime with Bluetooth). */
    private String band() {
        int f = freq;
        if (f <= 0) return "band unknown";
        if (f >= 2412 && f <= 2484) return f + " MHz, 2.4 GHz channel " + (f == 2484 ? 14 : (f - 2407) / 5);
        if (f >= 5150 && f <= 5895) return f + " MHz, 5 GHz channel " + ((f - 5000) / 5);
        return f + " MHz";
    }

    private WifiP2pManager p2p;
    private WifiP2pManager.Channel p2pChannel;
    private boolean askFailedLogged;

    /**
     * Asks the Wi-Fi Direct service directly. Some makers (TCL's AppBootManager) drop the change
     * broadcast for apps they count as background, so this also runs every few seconds (poll).
     */
    private void ask() {
        try {
            if (p2p == null) p2p = (WifiP2pManager) ctx.getSystemService(Context.WIFI_P2P_SERVICE);
            if (p2p == null) return;
            if (p2pChannel == null) p2pChannel = p2p.initialize(ctx, Looper.getMainLooper(), new WifiP2pManager.ChannelListener() {
                public void onChannelDisconnected() { p2pChannel = null; }
            });
            if (p2pChannel == null) return;
            p2p.requestGroupInfo(p2pChannel, new WifiP2pManager.GroupInfoListener() {
                public void onGroupInfoAvailable(WifiP2pGroup g) { groupChanged(g != null, g); }
            });
        } catch (Exception e) {
            if (!askFailedLogged) EventLog.add("Wi-Fi guard: could not ask about Wi-Fi Direct, " + e.getMessage());
            askFailedLogged = true;
        }
    }

    private static final long POLL_MS = 4000;

    private final Runnable poll = new Runnable() {
        public void run() {
            if (!registered) return;
            if (enabled()) ask();
            h.postDelayed(this, POLL_MS);
        }
    };

    private boolean formedSeen;

    private void groupChanged(boolean up, boolean formed, int clients) {
        if (formed != formedSeen) {
            formedSeen = formed;
            EventLog.add(formed ? "Wi-Fi Direct group up on " + band() + " (CarLink waiting for the phone)" : "Wi-Fi Direct group ended");
            if (!formed) freq = 0;
        }
        if (up == group) return;
        group = up;
        EventLog.add(up ? "phone joined the Wi-Fi Direct link (" + (clients < 0 ? "client list hidden" : clients + " client") + ", " + band() + ")"
                : "no phone on the Wi-Fi Direct link");
        h.removeCallbacks(apply);
        h.postDelayed(apply, up ? UP_DELAY_MS : DOWN_DELAY_MS);
        h.removeCallbacks(probe);
        resetProbe();
        if (up) h.postDelayed(probe, PROBE_MS);
        if (up != lowLat && enabled() && !Root.present()) {
            lowLat = up;
            final boolean want = up;
            io.execute(new Runnable() {
                public void run() {
                    String r = ShellLink.call("lowlat " + (want ? "on" : "off"));
                    EventLog.add("Wi-Fi guard: low-latency mode " + (want ? "on" : "off") + ", " + r);
                }
            });
        }
    }

    // ---------- recorder: every 30 s of wireless CarPlay, how much audio dropped out and whether Wi-Fi scanned ----------

    private static final long PROBE_MS = 10000;
    private static final int PROBES_PER_LINE = 3;
    private static final double SAMPLE_RATE = 44100;
    private boolean lowLat;
    private boolean olderHelperSeen;
    private long lastLost = -1;
    private long lostSum, worstGap;
    private int probes, scansSeen;

    private void resetProbe() { lastLost = -1; lostSum = 0; worstGap = 0; probes = 0; scansSeen = 0; }

    private final Runnable probe = new Runnable() {
        public void run() {
            if (!registered || !group) return;
            if (!Root.present()) io.execute(new Runnable() { public void run() { probeOnce(); } });
            h.postDelayed(this, PROBE_MS);
        }
    };

    /** Runs on the io thread. */
    private void probeOnce() {
        String r = ShellLink.call("probe");
        if (!r.startsWith("ok")) {
            if (r.contains("unknown command") && !olderHelperSeen) {
                olderHelperSeen = true;
                EventLog.add("Wi-Fi guard: the helper is from an older Drehzahl, restarting it: " + HelperStarter.auto(ctx));
            }
            return;
        }
        long lost = field(r, "lost="), gap = field(r, "gap="), scan = field(r, "scan=");
        if (lost >= 0) {
            if (lastLost >= 0 && lost >= lastLost) lostSum += lost - lastLost;
            lastLost = lost;
        }
        if (gap > worstGap) worstGap = gap;
        if (scan >= 0 && scan <= PROBE_MS / 1000 + 1) scansSeen++;
        if (++probes < PROBES_PER_LINE) return;
        String speed = "";
        try {
            dev.kelton.drehzahl.vehicle.VehicleData d = new dev.kelton.drehzahl.vehicle.VehicleData();
            dev.kelton.drehzahl.obd.ObdSource.get(ctx).copyTo(d);
            if (d.hasSpeed) speed = Prefs.i(Prefs.U_SPEED, 0) == 1 ? ", " + Math.round(d.speedKmh) + " km/h"
                    : ", " + Math.round(d.speedKmh * 0.621371) + " mph";
        } catch (Throwable ignored) { }
        EventLog.add(String.format(java.util.Locale.US, "carplay audio: %.1f s dropped in 30 s, worst gap %d ms, Wi-Fi scans %d of %d checks%s",
                lostSum / SAMPLE_RATE, worstGap, scansSeen, probes, speed));
        lostSum = 0; worstGap = 0; probes = 0; scansSeen = 0;
    }

    private static long field(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return -1;
        i += key.length();
        int j = i;
        if (j < s.length() && s.charAt(j) == '-') return -1;
        while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
        try { return j > i ? Long.parseLong(s.substring(i, j)) : -1; } catch (NumberFormatException e) { return -1; }
    }

    private void apply() {
        if (busy) { h.postDelayed(apply, 1000); return; }
        final boolean pause = group && enabled() && registered;
        boolean paused = Prefs.b(Prefs.WIFI_GUARD_PAUSED, false);
        if (pause == paused) return;
        if (!Root.present() && !ShellLink.configured() && !Prefs.b(Prefs.SELF_ADB_OK, false)) {
            if (pause) state = "needs root or the helper";
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
                        if (!ok) h.postDelayed(apply, RETRY_MS);
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

    /** Runs the helper as the shell user first (what Android expects for this call), then as root.
     *  Without root it asks the PC-started helper, which already is the shell user. */
    private String helper(boolean on) {
        if (!Root.present()) {
            String r = ShellLink.call("autojoin " + (on ? "on" : "off"));
            if (r.startsWith("error")) {
                String st = HelperStarter.auto(ctx);
                EventLog.add("Wi-Fi guard: helper restart on the tablet: " + st);
                if (st.startsWith("ok")) r = ShellLink.call("autojoin " + (on ? "on" : "off"));
            }
            helperState = r.startsWith("error PC helper not running") ? "not running" : "running";
            return r;
        }
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
