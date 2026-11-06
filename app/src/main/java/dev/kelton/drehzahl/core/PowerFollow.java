package dev.kelton.drehzahl.core;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.media.MediaHub;

/**
 * Follow car power: when the tablet loses power (ignition off) the screen turns off after a short wait,
 * and when power comes back the screen wakes with the startup animation and the home screen.
 * The wait covers the moment the car cuts accessory power while the engine cranks.
 */
public final class PowerFollow {
    public static final int[] WAIT_S = { 0, 5, 10, 30, 60 };
    private static final long WAKE_HOLD_MS = 6000;
    private static final long PLUG_WAKE_MS = 5000;

    private static PowerFollow inst;
    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean registered;
    private boolean pending;
    private boolean asleep;
    private int plugState = -1;
    private long screenOnAt;

    private final Runnable sleep = new Runnable() { public void run() { pending = false; sleepNow(); } };

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(a)) { screenOnAt = SystemClock.elapsedRealtime(); return; }
            boolean now;
            if (Intent.ACTION_POWER_CONNECTED.equals(a)) now = true;
            else if (Intent.ACTION_POWER_DISCONNECTED.equals(a)) now = false;
            else if (Intent.ACTION_BATTERY_CHANGED.equals(a)) now = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            else return;
            int s = now ? 1 : 0;
            if (plugState == s) return;
            boolean first = plugState == -1;
            plugState = s;
            if (first) {
                if (Prefs.b(Prefs.POWER_FOLLOW, false)) EventLog.add("power follow ready, " + (now ? "on car power" : "on battery"));
                return;
            }
            if (!Prefs.b(Prefs.POWER_FOLLOW, false)) return;
            if (now) back(); else lost();
        }
    };

    public static PowerFollow get(Context c) {
        if (inst == null) inst = new PowerFollow(c.getApplicationContext());
        return inst;
    }

    private PowerFollow(Context c) { ctx = c; }

    public void start() {
        if (registered) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED);
        else ctx.registerReceiver(rx, f);
        registered = true;
    }

    public void stop() {
        h.removeCallbacks(sleep);
        pending = false;
        if (!registered) return;
        try { ctx.unregisterReceiver(rx); } catch (RuntimeException ignored) { }
        registered = false;
        plugState = -1;
    }

    private void lost() {
        int wait = Prefs.i(Prefs.POWER_WAIT, 10);
        EventLog.add("power lost, screen off in " + wait + " s");
        h.removeCallbacks(sleep);
        pending = true;
        h.postDelayed(sleep, wait * 1000L);
    }

    /**
     * Many tablets light the screen by themselves when power is plugged in, sometimes before this runs.
     * So if Drehzahl turned the screen off and it came on in the last few seconds, it still counts as a wake.
     */
    private void back() {
        if (pending) {
            h.removeCallbacks(sleep);
            pending = false;
            EventLog.add("power back before the screen turned off");
            return;
        }
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        boolean on = pm != null && pm.isInteractive();
        boolean justLit = screenOnAt == 0 || SystemClock.elapsedRealtime() - screenOnAt < PLUG_WAKE_MS;
        if (on && !(asleep && justLit)) { asleep = false; EventLog.add("power back, screen already on"); return; }
        EventLog.add("power back, waking the screen");
        wake();
    }

    private void sleepNow() {
        if (!Prefs.b(Prefs.POWER_FOLLOW, false) || plugged()) return;
        if (Prefs.b(Prefs.POWER_PAUSE, true)) {
            MediaHub m = MediaHub.get(ctx);
            if (m.playing()) m.playPause();
        }
        if (!lockNow(ctx)) { EventLog.add("screen off skipped: Drehzahl is not allowed to lock the screen"); return; }
        asleep = true;
        screenOnAt = 0;
        EventLog.add("screen off");
    }

    @SuppressWarnings("deprecation")
    private void wake() {
        asleep = false;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "drehzahl:power");
            wl.acquire(WAKE_HOLD_MS);
        }
        Intent i = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("wake", true)
                .putExtra("open", 0)
                .putExtra("intro", true);
        try { ctx.startActivity(i); } catch (RuntimeException e) { EventLog.add("could not open Drehzahl: " + e.getMessage()); }
    }

    // ---------- helpers used by the Settings screen ----------

    public boolean plugged() {
        Intent b = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    public static ComponentName admin(Context c) { return new ComponentName(c, ScreenAdmin.class); }

    public static boolean canLock(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(admin(c));
    }

    public static boolean lockNow(Context c) {
        if (!canLock(c)) return false;
        try {
            ((DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE)).lockNow();
            return true;
        } catch (SecurityException e) { return false; }
    }

    public static void dropAdmin(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isAdminActive(admin(c))) dpm.removeActiveAdmin(admin(c));
    }

    /** True when the tablet has a PIN, pattern or password; then it wakes to the lock screen. */
    public static boolean secureLock(Context c) {
        KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }
}
