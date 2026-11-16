package dev.kelton.drehzahl.core;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.provider.Settings;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.sys.AdbClient;
import dev.kelton.drehzahl.sys.AdbKey;
import dev.kelton.drehzahl.sys.ShellHelper;

/**
 * Starts sys.ShellHelper without a PC: Drehzahl logs in to the tablet's own adb with its own key
 * and runs the same command the PC toolkit runs. Two ways in:
 *  1. port 5555 on 127.0.0.1, open from "adb tcpip" until the tablet restarts (no Wi-Fi needed);
 *  2. wireless debugging, which Drehzahl switches on itself (WRITE_SECURE_SETTINGS) and finds by
 *     mDNS. Android only allows that while the tablet is on a Wi-Fi network.
 * The key has to be allowed once ("Allow USB debugging?", Always allow), which only works over way 1.
 * Never call on the main thread.
 */
public final class HelperStarter {
    private static final String TLS_SERVICE = "_adb-tls-connect._tcp";
    private static final String ADB_WIFI = "adb_wifi_enabled";
    private static long lastAuto;

    private HelperStarter() { }

    /** Automatic restart: never shows a prompt, at most every 15 seconds. */
    public static String auto(Context c) {
        synchronized (HelperStarter.class) {
            long now = System.currentTimeMillis();
            if (now - lastAuto < 15000) return "error tried a moment ago";
            lastAuto = now;
        }
        if (!Prefs.b(Prefs.SELF_ADB_OK, false)) return "error Drehzahl's adb key is not allowed yet (Settings > CarPlay)";
        return start(c, null, false);
    }

    /** via: null tries 5555 then wireless debugging; "local" or "tls" forces one. */
    public static synchronized String start(Context c, String via, boolean mayAsk) {
        try { return tryStart(c, via, mayAsk); }
        catch (Throwable t) { return "error " + t; }
    }

    private static String tryStart(Context c, String via, boolean mayAsk) {
        Prefs.init(c);
        AdbKey key;
        try { key = AdbKey.get(c); } catch (Exception e) { return "error no adb key: " + e; }
        String first = null;
        if (via == null || via.equals("local")) {
            try (AdbClient a = AdbClient.connect(key, "127.0.0.1", 5555, mayAsk)) {
                return launch(c, a, "port 5555");
            } catch (AdbClient.NeedsApproval e) {
                return "error " + e.getMessage() + ". Tap \"Let Drehzahl restart it\" in Settings > CarPlay.";
            } catch (Exception e) {
                first = "port 5555: " + e.getMessage();
                if ("local".equals(via)) return "error " + first;
            }
        }
        String r = viaWirelessDebugging(c, key);
        return r.startsWith("ok") || first == null ? r : r + " (" + first + ")";
    }

    private static String viaWirelessDebugging(Context c, AdbKey key) {
        if (!onWifi(c)) return "error no Wi-Fi network, which wireless debugging needs";
        ContentResolver cr = c.getContentResolver();
        int before = Settings.Global.getInt(cr, ADB_WIFI, 0);
        try {
            if (before != 1 && !Settings.Global.putInt(cr, ADB_WIFI, 1)) return "error could not switch on wireless debugging";
            Object[] found = findPort(c, 12000);
            if (found == null) return "error wireless debugging did not show up on the network";
            int port = (Integer) found[0];
            AdbClient a;
            try { a = AdbClient.connect(key, "127.0.0.1", port, false); }
            catch (AdbClient.NeedsApproval e) { throw e; }
            catch (Exception e) { a = AdbClient.connect(key, ((InetAddress) found[1]).getHostAddress(), port, false); }
            try { return launch(c, a, "wireless debugging"); } finally { a.close(); }
        } catch (AdbClient.NeedsApproval e) {
            return "error " + e.getMessage();
        } catch (SecurityException e) {
            return "error Drehzahl may not change settings (run Install in the PC toolkit once): " + e.getMessage();
        } catch (Exception e) {
            return "error wireless debugging: " + e;
        } finally {
            if (before != 1) try { Settings.Global.putInt(cr, ADB_WIFI, before); } catch (Exception ignored) { }
        }
    }

    private static String launch(Context c, AdbClient a, String how) throws Exception {
        Prefs.put(Prefs.SELF_ADB_OK, true);
        SecureRandom rnd = new SecureRandom();
        int port = 20000 + rnd.nextInt(40000);
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 32; i++) token.append(String.format("%02x", rnd.nextInt(256)));
        String cmd = "for p in $(pidof " + ShellHelper.NAME + "); do kill $p; done; mv /data/local/tmp/drehzahl_helper.log /data/local/tmp/drehzahl_helper.old.log 2>/dev/null; "
                + "CLASSPATH=" + c.getPackageCodePath() + " setsid nohup app_process /system/bin --nice-name=" + ShellHelper.NAME
                + " " + ShellHelper.class.getName() + " " + port + " " + token + " >/dev/null 2>&1 </dev/null & "
                + "sleep 2; pidof " + ShellHelper.NAME;
        String pid = a.shell(cmd).trim();
        if (pid.isEmpty()) return "error the helper did not start";
        ShellLink.set(port, token.toString());
        String ping = ShellLink.call("ping");
        return ping.startsWith("ok") ? "ok started through " + how : "error started but did not answer: " + ping;
    }

    private static boolean onWifi(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
        }
        return false;
    }

    /** Finds this tablet's wireless debugging port by mDNS, skipping other devices on the network. */
    private static Object[] findPort(Context c, long waitMs) throws Exception {
        final NsdManager nsd = (NsdManager) c.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return null;
        final java.util.Set<InetAddress> mine = new java.util.HashSet<>();
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
            mine.addAll(Collections.list(ni.getInetAddresses()));
        final Object[][] port = { null };
        final CountDownLatch done = new CountDownLatch(1);
        final NsdManager.DiscoveryListener[] self = new NsdManager.DiscoveryListener[1];
        self[0] = new NsdManager.DiscoveryListener() {
            public void onStartDiscoveryFailed(String t, int e) { done.countDown(); }
            public void onStopDiscoveryFailed(String t, int e) { }
            public void onDiscoveryStarted(String t) { }
            public void onDiscoveryStopped(String t) { }
            public void onServiceLost(NsdServiceInfo s) { }
            public void onServiceFound(NsdServiceInfo s) {
                nsd.resolveService(s, new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo s, int e) { }
                    public void onServiceResolved(NsdServiceInfo s) {
                        if (s.getHost() != null && mine.contains(s.getHost())) {
                            port[0] = new Object[] { s.getPort(), s.getHost() };
                            done.countDown();
                        }
                    }
                });
            }
        };
        nsd.discoverServices(TLS_SERVICE, NsdManager.PROTOCOL_DNS_SD, self[0]);
        try {
            done.await(waitMs, TimeUnit.MILLISECONDS);
        } finally {
            try { nsd.stopServiceDiscovery(self[0]); } catch (Exception ignored) { }
        }
        return port[0];
    }
}
