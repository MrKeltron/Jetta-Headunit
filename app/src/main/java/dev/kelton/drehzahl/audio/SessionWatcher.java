package dev.kelton.drehzahl.audio;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.kelton.drehzahl.Prefs;

/**
 * Finds music players that never announce their audio session (most apps besides Spotify and a few others).
 * Needs the DUMP permission, which only ADB can grant (the toolkit's Install step does it).
 * It reads "dumpsys audio" right after Android reports a player starting or stopping (plus a slow
 * backup check) and hands every playing session to the SoundEngine.
 */
public final class SessionWatcher implements Runnable {
    private static final Pattern SESSION = Pattern.compile("session(?:Id)?:\\s*(\\d+)");
    private static final Pattern UID = Pattern.compile("u/pid:\\s*(\\d+)");
    private static final long BACKUP_MS = 15000;
    private static final long SETTLE_MS = 300;
    private static final long MIN_GAP_MS = 2000;
    private final Context ctx;
    private volatile boolean running;
    private Thread thread;
    private final Object wake = new Object();
    private boolean poked;
    private android.media.AudioManager.AudioPlaybackCallback playback;

    public SessionWatcher(Context c) { ctx = c.getApplicationContext(); }

    public void start() {
        if (running) return;
        running = true;
        android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            playback = new android.media.AudioManager.AudioPlaybackCallback() {
                @Override public void onPlaybackConfigChanged(java.util.List<android.media.AudioPlaybackConfiguration> configs) { poke(); }
            };
            am.registerAudioPlaybackCallback(playback, new android.os.Handler(android.os.Looper.getMainLooper()));
        }
        thread = new Thread(this, "drehzahl-sessions");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am != null && playback != null) am.unregisterAudioPlaybackCallback(playback);
        if (thread != null) thread.interrupt();
    }

    private void poke() {
        synchronized (wake) { poked = true; wake.notifyAll(); }
    }

    @Override public void run() {
        SoundEngine eng = SoundEngine.get(ctx);
        PackageManager pm = ctx.getPackageManager();
        while (running) {
            try {
                long scanned = android.os.SystemClock.elapsedRealtime();
                if (Prefs.i(Prefs.FX_MODE, 1) == 1 && eng.canDump()) eng.dumpRound(scan(pm));
                synchronized (wake) {
                    if (!poked) wake.wait(BACKUP_MS);
                    poked = false;
                }
                long since = android.os.SystemClock.elapsedRealtime() - scanned;
                Thread.sleep(Math.max(SETTLE_MS, MIN_GAP_MS - since));
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException ignored) { }
        }
    }

    private Map<Integer, String> scan(PackageManager pm) {
        Map<Integer, String> found = new HashMap<>();
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] { "dumpsys", "audio" });
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.contains("AudioPlaybackConfiguration") || !line.contains("state:started")) continue;
                Matcher m = SESSION.matcher(line);
                if (!m.find()) continue;
                int session = Integer.parseInt(m.group(1));
                if (session <= 0) continue;
                String owner = "app";
                Matcher u = UID.matcher(line);
                if (u.find()) {
                    String name = pm.getNameForUid(Integer.parseInt(u.group(1)));
                    if (name != null) {
                        if (name.equals(ctx.getPackageName())) continue;
                        owner = label(pm, name);
                    }
                }
                found.put(session, owner);
            }
        } catch (Exception ignored) {
        } finally {
            if (p != null) p.destroy();
        }
        return found;
    }

    static String label(PackageManager pm, String pkg) {
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg.split(":")[0], 0)).toString(); }
        catch (Exception e) { return pkg; }
    }
}
