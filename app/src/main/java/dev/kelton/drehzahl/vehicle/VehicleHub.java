package dev.kelton.drehzahl.vehicle;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

import dev.kelton.drehzahl.Prefs;

/**
 * Collects vehicle data from the selected source 20 times a second and hands it to whichever screens are open.
 * Sources: 0 demo, 1 tablet sensors, 2 OBD2 adapter (USB or Bluetooth ELM327), 3 tuner link (planned).
 */
public final class VehicleHub {
    public interface Listener { void onData(VehicleData d); }

    public static final String[] SOURCES = { "Demo drive", "Tablet sensors", "OBD2 adapter", "Tuner link" };

    private static VehicleHub inst;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final DemoSource demo = new DemoSource();
    public final MotionSource motion;
    public final TripComputer trips = new TripComputer();
    public final VehicleData data = new VehicleData();
    public final LapTimer laps = new LapTimer();
    private long last;
    private boolean ticking;
    private int keepAlive;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            float dt = Math.min(0.25f, (now - last) / 1000f);
            last = now;
            update(dt);
            for (Listener l : new ArrayList<>(listeners)) l.onData(data);
            if (ticking) h.postDelayed(this, 50);
        }
    };

    public static VehicleHub get(Context c) {
        if (inst == null) inst = new VehicleHub(c.getApplicationContext());
        return inst;
    }

    private final Context ctx;

    private VehicleHub(Context c) { ctx = c; motion = new MotionSource(c); }

    public int source() { return Prefs.i(Prefs.SOURCE, 0); }

    private void update(float dt) {
        int src = source();
        if (src == 0) {
            demo.step(dt, data);
            data.live = false;
        } else if (src == 2) {
            dev.kelton.drehzahl.obd.ObdSource.get(ctx).copyTo(data);
            data.live = true;
            float gps = motion.gpsSpeed();
            if (!data.hasSpeed && gps >= 0) { data.speedKmh = gps; data.hasSpeed = true; }
        } else {
            data.live = true;
            data.hasEngine = false;
            float gps = motion.gpsSpeed();
            data.hasSpeed = gps >= 0;
            data.speedKmh = Math.max(0f, gps);
            data.rpm = 0; data.boostKpa = 0; data.powerKw = 0; data.torqueNm = 0; data.throttle = 0; data.load = 0;
            data.fuelLph = 0;
        }
        if (motion.hasAccel() && (src != 0 || Prefs.b("demo_real_g", true))) {
            data.latG = motion.latG();
            data.lonG = motion.lonG();
            data.hasG = true;
        }
        trips.update(data, dt);
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
        startTicking();
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
        if (listeners.isEmpty() && keepAlive == 0) stopTicking();
    }

    /** Background users (speed-dependent volume) that need data without a screen open. */
    public void hold(boolean on) {
        keepAlive += on ? 1 : -1;
        if (keepAlive < 0) keepAlive = 0;
        if (keepAlive > 0) startTicking(); else if (listeners.isEmpty()) stopTicking();
    }

    private void startTicking() {
        if (ticking) return;
        ticking = true;
        motion.start();
        last = SystemClock.elapsedRealtime();
        h.post(tick);
    }

    private void stopTicking() {
        ticking = false;
        h.removeCallbacks(tick);
        motion.stop();
    }
}
