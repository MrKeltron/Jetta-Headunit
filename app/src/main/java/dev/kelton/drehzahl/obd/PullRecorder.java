package dev.kelton.drehzahl.obd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Records full-throttle pulls. Every reading goes into a 20 s ring, so a saved pull starts a few
 * seconds before the pedal went down. A pull starts when the pedal (or the throttle, when the car
 * does not report the pedal) passes 80% of its travel above 1200 rpm, and ends 1.5 s after it drops
 * below 45%. While a pull runs, ObdSource asks for the pull readings as fast as it can.
 * Plain Java so the PC tests can feed it a pull.
 */
public final class PullRecorder {
    public static final int RPM = 0x0C, SPEED = 0x0D, MAP = 0x0B, THROTTLE = 0x11, THROTTLE_CMD = 0x4C, PEDAL = 0x49,
            TIMING = 0x0E, RAIL = 0x23, LAMBDA_CMD = 0x44, LAMBDA = 0x34, LOAD = 0x04;
    /** The readings asked for during a pull, most important first. 0x24 is used when 0x34 is missing. */
    public static final int[] PULL_SET = { RPM, MAP, THROTTLE, THROTTLE_CMD, PEDAL, TIMING, RAIL, LAMBDA_CMD, LAMBDA, SPEED, LOAD };
    static final int[] COLUMNS = { RPM, SPEED, MAP, THROTTLE, THROTTLE_CMD, PEDAL, TIMING, RAIL, LAMBDA_CMD, LAMBDA, LOAD };

    static final long RING_MS = 20000, BEFORE_MS = 5000, AFTER_MS = 1500, MIN_PULL_MS = 1000, MAX_PULL_MS = 45000;
    static final float START = 0.80f, STOP = 0.45f;

    public interface Sink { void saved(Pull p); }

    private static final int CAP = 8192;
    private final long[] rt = new long[CAP];
    private final int[] rp = new int[CAP];
    private final float[] rv = new float[CAP];
    private int head, count;

    private final float[] last = new float[256];
    private final float[] lo = new float[256], hi = new float[256];
    private boolean pedalSeen;
    private int state; // 0 idle, 1 pulling, 2 tail
    private long start, lastHigh, end;
    private float baro = 101f;
    private Sink sink;
    private boolean enabled = true;

    public PullRecorder() {
        for (int i = 0; i < 256; i++) { last[i] = Float.NaN; lo[i] = Float.NaN; hi[i] = Float.NaN; }
        lo[PEDAL] = 16f; hi[PEDAL] = 80f;
        lo[THROTTLE] = 15f; hi[THROTTLE] = 85f;
    }

    public void setSink(Sink s) { sink = s; }
    public void setEnabled(boolean on) { enabled = on; if (!on) { state = 0; } }
    public void setBaro(float kpa) { if (kpa > 50) baro = kpa; }
    public synchronized boolean active() { return state != 0; }

    /** One reading in natural units: rpm, km/h, kPa absolute, %, degrees, kPa (rail), lambda. */
    public synchronized void sample(long t, int pid, float v) {
        if (Float.isNaN(v)) return;
        pid &= 0xFF;
        if (pid == 0x24) pid = LAMBDA;
        int i = (head + count) % CAP;
        if (count == CAP) { head = (head + 1) % CAP; count--; i = (head + count) % CAP; }
        rt[i] = t; rp[i] = pid; rv[i] = v;
        count++;
        while (count > 0 && t - rt[head] > RING_MS && state == 0) { head = (head + 1) % CAP; count--; }
        last[pid] = v;
        if (pid == PEDAL) pedalSeen = true;
        if (pid == PEDAL || pid == THROTTLE) {
            if (Float.isNaN(lo[pid]) || v < lo[pid]) lo[pid] = v;
            if (Float.isNaN(hi[pid]) || v > hi[pid]) hi[pid] = v;
        }
        if (!enabled) return;
        if (pid != PEDAL && pid != THROTTLE && pid != RPM) return;
        float demand = demand();
        if (Float.isNaN(demand)) return;
        if (state == 0) {
            if (demand >= START && last[RPM] >= 1200) { state = 1; start = t; lastHigh = t; }
        } else if (state == 1) {
            if (demand >= STOP) lastHigh = t;
            if (t - lastHigh >= AFTER_MS || t - start > MAX_PULL_MS) {
                end = lastHigh;
                if (end - start < MIN_PULL_MS) state = 0;
                else state = 2;
            }
        }
        if (state == 2 && t >= end + AFTER_MS) finish();
    }

    /** How far down the driver's foot is, 0 to 1, from the pedal when the car reports it. */
    private float demand() {
        int p = pedalSeen ? PEDAL : THROTTLE;
        float v = last[p];
        if (Float.isNaN(v)) return Float.NaN;
        float span = hi[p] - lo[p];
        if (span < 20f) span = 20f;
        return (v - lo[p]) / span;
    }

    private void finish() {
        state = 0;
        Pull p = new Pull(start, end, baro, pedalSeen);
        for (int k = 0; k < count; k++) {
            int i = (head + k) % CAP;
            if (rt[i] >= start - BEFORE_MS && rt[i] <= end + AFTER_MS) p.add(rt[i], rp[i], rv[i]);
        }
        p.analyze();
        if (sink != null) sink.saved(p);
    }

    /** One recorded pull: the readings in order, and what stood out. */
    public static final class Pull {
        public final long start, end;
        public final float baro;
        public final boolean hasPedal;
        private long[] T = new long[512];
        private int[] P = new int[512];
        private float[] V = new float[512];
        private int n;
        private final long[][] st = new long[256][];
        private final float[][] sv = new float[256][];
        public final List<String> findings = new ArrayList<>();
        public float peakBoostPsi = Float.NaN, maxRpm = Float.NaN, minTiming = Float.NaN, minRailBar = Float.NaN, maxLean = Float.NaN;
        public final List<Long> cuts = new ArrayList<>();

        Pull(long start, long end, float baro, boolean hasPedal) {
            this.start = start; this.end = end; this.baro = baro; this.hasPedal = hasPedal;
        }

        void add(long t, int pid, float v) {
            if (n == T.length) {
                T = java.util.Arrays.copyOf(T, n * 2);
                P = java.util.Arrays.copyOf(P, n * 2);
                V = java.util.Arrays.copyOf(V, n * 2);
            }
            T[n] = t; P[n] = pid & 0xFF; V[n] = v; n++;
        }

        /** Splits the readings per kind so at() can look one up quickly. */
        void index() {
            int[] cnt = new int[256];
            for (int i = 0; i < n; i++) cnt[P[i]]++;
            for (int p = 0; p < 256; p++) if (cnt[p] > 0) { st[p] = new long[cnt[p]]; sv[p] = new float[cnt[p]]; cnt[p] = 0; }
            for (int i = 0; i < n; i++) { int p = P[i]; st[p][cnt[p]] = T[i]; sv[p][cnt[p]] = V[i]; cnt[p]++; }
        }

        public int size() { return n; }
        public long t(int i) { return T[i]; }
        public int pid(int i) { return P[i]; }
        public float v(int i) { return V[i]; }

        /** Value of a reading at time t: the last one at or before t, NaN if none yet. */
        public float at(int pid, long t) {
            long[] ts = st[pid & 0xFF];
            if (ts == null || ts[0] > t) return Float.NaN;
            int lo = 0, hi = ts.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (ts[mid] <= t) lo = mid; else hi = mid - 1;
            }
            return sv[pid & 0xFF][lo];
        }

        public float boostPsi(float mapKpa) { return (mapKpa - baro) * 0.145038f; }

        public String csv() {
            StringBuilder s = new StringBuilder("t_s,rpm,speed_kmh,boost_psi,throttle_pct,throttle_cmd_pct,pedal_pct,timing_deg,rail_bar,lambda_cmd,lambda,load_pct\n");
            float[] row = new float[COLUMNS.length];
            for (int c = 0; c < row.length; c++) row[c] = Float.NaN;
            for (int i = 0; i < size(); i++) {
                for (int c = 0; c < COLUMNS.length; c++) if (COLUMNS[c] == pid(i)) row[c] = v(i);
                s.append(String.format(Locale.US, "%.3f", (t(i) - start) / 1000f));
                for (int c = 0; c < row.length; c++) {
                    float x = row[c];
                    if (COLUMNS[c] == MAP) x = Float.isNaN(x) ? x : boostPsi(x);
                    if (COLUMNS[c] == RAIL) x = Float.isNaN(x) ? x : x / 100f;
                    s.append(',').append(Float.isNaN(x) ? "" : String.format(Locale.US, "%.2f", x));
                }
                s.append('\n');
            }
            return s.toString();
        }

        void analyze() {
            index();
            float rails = 0; int nRail = 0;
            for (int i = 0; i < size(); i++) {
                long t = t(i);
                boolean in = t >= start && t <= end;
                float v = v(i);
                switch (pid(i)) {
                    case MAP: { float b = boostPsi(v); if (Float.isNaN(peakBoostPsi) || b > peakBoostPsi) peakBoostPsi = b; break; }
                    case RPM: if (Float.isNaN(maxRpm) || v > maxRpm) maxRpm = v; break;
                    case TIMING: if (in && (Float.isNaN(minTiming) || v < minTiming)) minTiming = v; break;
                    case RAIL: if (in) { float bar = v / 100f; rails += bar; nRail++; if (Float.isNaN(minRailBar) || bar < minRailBar) minRailBar = bar; } break;
                    case LAMBDA: if (in) { float cmd = at(LAMBDA_CMD, t); if (!Float.isNaN(cmd)) { float lean = v - cmd; if (Float.isNaN(maxLean) || lean > maxLean) maxLean = lean; } } break;
                    default: break;
                }
            }
            findCuts();
            findFlares();
            if (nRail > 2 && minRailBar < 0.8f * rails / nRail)
                findings.add(String.format(Locale.US, "Fuel rail pressure sagged to %.0f bar (average %.0f bar during the pull). Points to the high or low pressure fuel pump.", minRailBar, rails / nRail));
            if (!Float.isNaN(minTiming) && minTiming < 0)
                findings.add(String.format(Locale.US, "Ignition timing went down to %.1f degrees. Large timing pulls mean knock (fuel quality, heat, or the tune).", minTiming));
            if (!Float.isNaN(maxLean) && maxLean > 0.08f)
                findings.add(String.format(Locale.US, "Ran leaner than asked for: lambda %.2f above the target. Fuel delivery could not keep up.", maxLean));
            if (findings.isEmpty()) findings.add("Nothing unusual: no throttle cut, no timing pull below zero, no rail pressure sag.");
        }

        /** The ECU closing the throttle while the foot stays down is a torque cut, the "buck" a driver feels. */
        private void findCuts() {
            int th = has(THROTTLE_CMD) ? THROTTLE_CMD : THROTTLE;
            long lastCut = -10000;
            for (int i = 0; i < size(); i++) {
                if (pid(i) != th) continue;
                long t = t(i);
                if (t < start || t > end || t - lastCut < 1500) continue;
                float now = v(i), peak = Float.NaN;
                for (int j = i - 1; j >= 0 && t - t(j) <= 800; j--) if (pid(j) == th && (Float.isNaN(peak) || v(j) > peak)) peak = v(j);
                if (Float.isNaN(peak) || peak - now < 25f) continue;
                boolean footDown;
                if (hasPedal) {
                    float pedal = at(PEDAL, t);
                    footDown = !Float.isNaN(pedal) && pedal >= 60f;
                } else {
                    footDown = false;
                    for (int j = i + 1; j < size() && t(j) - t <= 2000; j++) if (pid(j) == th && v(j) - now >= 20f) { footDown = true; break; }
                }
                if (!footDown) continue;
                lastCut = t;
                cuts.add(t);
                long before = t - 300;
                float boost = boostPsi(at(MAP, before)), rpm = at(RPM, before), tim = at(TIMING, before), rail = at(RAIL, before);
                StringBuilder s = new StringBuilder(String.format(Locale.US,
                        "Torque cut at %.1f s: the ECU closed the throttle from %.0f%% to %.0f%% with your foot down. Just before: %,.0f rpm, boost %.1f psi",
                        (t - start) / 1000f, peak, now, rpm, boost));
                if (!Float.isNaN(tim)) s.append(String.format(Locale.US, ", timing %.1f deg", tim));
                if (!Float.isNaN(rail)) s.append(String.format(Locale.US, ", rail %.0f bar", rail / 100f));
                s.append('.');
                float lead = maxIn(MAP, t - 1000, t);
                if (!Float.isNaN(peakBoostPsi) && !Float.isNaN(lead) && boostPsi(lead) >= peakBoostPsi - 1.0f)
                    s.append(" Boost was at its peak for the pull right before the cut, which points to overboost (wastegate control).");
                findings.add(s.toString());
            }
        }

        /** RPM jumping while speed does not follow, then settling back: the clutch slipping. */
        private void findFlares() {
            for (int i = 0; i < size(); i++) {
                if (pid(i) != RPM || t(i) < start || t(i) > end) continue;
                long t = t(i);
                float r0 = v(i), s0 = at(SPEED, t);
                if (Float.isNaN(s0) || s0 < 20) continue;
                for (int j = i + 1; j < size() && t(j) - t <= 500; j++) {
                    if (pid(j) != RPM) continue;
                    float s1 = at(SPEED, t(j));
                    if (v(j) - r0 < 600 || Float.isNaN(s1) || s1 - s0 > 2) continue;
                    for (int k = j + 1; k < size() && t(k) - t(j) <= 1500; k++) {
                        if (pid(k) == RPM && v(j) - v(k) >= 400 && at(SPEED, t(k)) >= s1) {
                            findings.add(String.format(Locale.US, "RPM flared %,.0f rpm at %.1f s without the car speeding up, then dropped back. Points to the DSG clutch slipping.",
                                    v(j) - r0, (t - start) / 1000f));
                            return;
                        }
                    }
                }
            }
        }

        public boolean has(int pid) { return st[pid & 0xFF] != null; }

        /** Highest value of one reading between two times, NaN if none. */
        float maxIn(int pid, long from, long to) {
            long[] ts = st[pid & 0xFF];
            if (ts == null) return Float.NaN;
            float m = Float.NaN;
            for (int i = 0; i < ts.length; i++) if (ts[i] >= from && ts[i] <= to && (Float.isNaN(m) || sv[pid & 0xFF][i] > m)) m = sv[pid & 0xFF][i];
            return m;
        }

        /** All readings of one kind, for the chart. */
        public long[] times(int pid) { return st[pid & 0xFF]; }
        public float[] values(int pid) { return sv[pid & 0xFF]; }

        public String summary() {
            StringBuilder s = new StringBuilder(String.format(Locale.US, "%.1f s", (end - start) / 1000f));
            if (!Float.isNaN(peakBoostPsi)) s.append(String.format(Locale.US, ", peak %.1f psi", peakBoostPsi));
            if (!Float.isNaN(maxRpm)) s.append(String.format(Locale.US, ", %,.0f rpm", maxRpm));
            if (!cuts.isEmpty()) s.append(", ").append(cuts.size()).append(cuts.size() == 1 ? " torque cut" : " torque cuts");
            return s.toString();
        }
    }
}
