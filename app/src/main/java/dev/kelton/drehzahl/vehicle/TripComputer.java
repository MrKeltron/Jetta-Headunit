package dev.kelton.drehzahl.vehicle;

import dev.kelton.drehzahl.Prefs;

/** Three trip memories like a factory cluster: since start, since refuel, long-term. */
public final class TripComputer {
    public static final String[] NAMES = { "Since start", "Since refuel", "Long-term" };

    public static final class Trip {
        public float km, litres, seconds;
        final String key;
        Trip(String key) {
            this.key = key;
            if (key != null) {
                km = Prefs.f(key + "_km", 0f);
                litres = Prefs.f(key + "_l", 0f);
                seconds = Prefs.f(key + "_s", 0f);
            }
        }
        void save() {
            if (key == null) return;
            Prefs.put(key + "_km", km);
            Prefs.put(key + "_l", litres);
            Prefs.put(key + "_s", seconds);
        }
        public void reset() { km = 0; litres = 0; seconds = 0; save(); }
        public float avgSpeedKmh() { return seconds < 1 ? Float.NaN : km / (seconds / 3600f); }
    }

    public final Trip[] trips = { new Trip(null), new Trip("trip_refuel"), new Trip("trip_long") };
    private float sinceSave;

    void update(VehicleData d, float dt) {
        if (d.speedKmh < 1f && d.rpm < 100f) return;
        float km = d.speedKmh * dt / 3600f;
        float l = d.fuelLph * dt / 3600f;
        for (Trip t : trips) {
            t.km += km;
            t.litres += l;
            if (d.speedKmh > 1f) t.seconds += dt;
        }
        if (d.live) Prefs.put(Prefs.ODO_KM, Prefs.f(Prefs.ODO_KM, 0f) + km);
        sinceSave += dt;
        if (sinceSave > 30f) {
            sinceSave = 0;
            trips[1].save();
            trips[2].save();
        }
    }
}
