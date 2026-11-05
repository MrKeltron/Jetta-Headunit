package dev.kelton.drehzahl.vehicle;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;

import dev.kelton.drehzahl.Prefs;

/**
 * Uses the tablet's own hardware: accelerometer for g-forces, GPS for speed when the tablet has one.
 * Mount calibration: park on level ground, tap Calibrate, and the current gravity direction becomes "up".
 * The very first start calibrates itself from about one second of readings.
 */
public final class MotionSource implements SensorEventListener, LocationListener {
    private final SensorManager sm;
    private final LocationManager lm;
    private final Sensor accel;
    private final Context ctx;
    private final float[] raw = new float[3];
    private final float[] up = { 0f, 1f, 0f };
    private final float[] fwd = { 0f, 0f, -1f };
    private final float[] right = { 1f, 0f, 0f };
    private float g0 = 9.81f;
    private float lat, lon;
    private float gpsKmh = -1f;
    private long gpsAt;
    private boolean running;

    public MotionSource(Context c) {
        ctx = c.getApplicationContext();
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        accel = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        loadCalibration();
    }

    public boolean hasAccel() { return accel != null; }

    public boolean hasGps() {
        return lm != null && ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    public void start() {
        if (running) return;
        running = true;
        if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        if (hasGps() && ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper()); } catch (RuntimeException ignored) { }
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        if (sm != null) sm.unregisterListener(this);
        try { lm.removeUpdates(this); } catch (RuntimeException ignored) { }
    }

    public float latG() { return lat; }
    public float lonG() { return lon; }

    /** Speed from GPS in km/h, or -1 when there is no recent fix. */
    public float gpsSpeed() { return System.currentTimeMillis() - gpsAt < 3000 ? gpsKmh : -1f; }

    public void calibrate() {
        float n = (float) Math.sqrt(raw[0] * raw[0] + raw[1] * raw[1] + raw[2] * raw[2]);
        if (n < 5f) return;
        Prefs.put(Prefs.G_CAL, raw[0] / n + "," + raw[1] / n + "," + raw[2] / n + "," + n);
        loadCalibration();
    }

    private void loadCalibration() {
        String s = Prefs.s(Prefs.G_CAL, "");
        String[] parts = s.split(",");
        if (parts.length == 4) {
            try {
                for (int i = 0; i < 3; i++) up[i] = Float.parseFloat(parts[i]);
                g0 = Float.parseFloat(parts[3]);
            } catch (NumberFormatException ignored) { }
        }
        // forward = screen normal pointing away from the driver, flattened against "up"
        float[] z = { 0f, 0f, -1f };
        float d = z[0] * up[0] + z[1] * up[1] + z[2] * up[2];
        for (int i = 0; i < 3; i++) fwd[i] = z[i] - d * up[i];
        normalize(fwd);
        right[0] = fwd[1] * up[2] - fwd[2] * up[1];
        right[1] = fwd[2] * up[0] - fwd[0] * up[2];
        right[2] = fwd[0] * up[1] - fwd[1] * up[0];
        normalize(right);
    }

    private static void normalize(float[] v) {
        float n = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (n > 1e-4f) for (int i = 0; i < 3; i++) v[i] /= n;
    }

    private int autoSamples;
    private final float[] autoSum = new float[3];

    @Override public void onSensorChanged(SensorEvent e) {
        System.arraycopy(e.values, 0, raw, 0, 3);
        if (!Prefs.has(Prefs.G_CAL) && autoSamples < 60) {
            for (int i = 0; i < 3; i++) autoSum[i] += e.values[i];
            if (++autoSamples == 60) {
                for (int i = 0; i < 3; i++) raw[i] = autoSum[i] / 60f;
                calibrate();
            }
        }
        float ax = e.values[0] - up[0] * g0, ay = e.values[1] - up[1] * g0, az = e.values[2] - up[2] * g0;
        float l = (ax * right[0] + ay * right[1] + az * right[2]) / 9.81f;
        float f = (ax * fwd[0] + ay * fwd[1] + az * fwd[2]) / 9.81f;
        lat += (l - lat) * 0.15f;
        lon += (f - lon) * 0.15f;
    }

    @Override public void onAccuracyChanged(Sensor s, int a) { }

    @Override public void onLocationChanged(Location loc) {
        if (loc.hasSpeed()) {
            gpsKmh = loc.getSpeed() * 3.6f;
            gpsAt = System.currentTimeMillis();
        }
    }

    @Override public void onStatusChanged(String p, int s, Bundle b) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }
}
