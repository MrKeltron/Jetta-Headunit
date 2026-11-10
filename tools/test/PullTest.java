package dev.kelton.drehzahl.obd;

/** Feeds the pull recorder a made-up kickdown with a torque cut, and checks the multi-reading parser. */
public class PullTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fails++;
    }

    public static void main(String[] a) {
        multi();
        pullWithCut();
        quietCruise();
        stab();
        System.out.println(fails == 0 ? "PullTest: all passed" : "PullTest: " + fails + " failed");
        if (fails > 0) System.exit(1);
    }

    static void multi() {
        int[] want = { 0x0C, 0x0B, 0x11, 0x4C, 0x49, 0x0E };
        String reply = "00A\n0:410C1AF80BC8\n1:11E64CE049D10E\n2:9CAAAAAAAAAAAA\n";
        int[][] d = ObdParse.multiPid(reply, want);
        check(d[0x0C] != null && ((d[0x0C][0] << 8) | d[0x0C][1]) == 0x1AF8, "multi: rpm bytes");
        check(d[0x0B] != null && d[0x0B][0] == 0xC8, "multi: MAP");
        check(d[0x11] != null && d[0x11][0] == 0xE6, "multi: throttle");
        check(d[0x4C] != null && d[0x4C][0] == 0xE0, "multi: commanded throttle");
        check(d[0x49] != null && d[0x49][0] == 0xD1, "multi: pedal");
        check(d[0x0E] != null && d[0x0E][0] == 0x9C, "multi: timing, padding ignored");
        int[][] single = ObdParse.multiPid("410C1AF80BC8", new int[] { 0x0C, 0x0B });
        check(single[0x0C] != null && single[0x0B] != null, "multi: single frame reply");
        check(ObdParse.canFrames(7) == 1 && ObdParse.canFrames(8) == 2 && ObdParse.canFrames(13) == 2 && ObdParse.canFrames(19) == 3, "CAN frame count");
    }

    /** 0 to 2 s cruising, pedal to the floor at 2 s, boost builds, ECU cuts throttle at 4.2 s with the pedal still down, lift at 6 s. */
    static void pullWithCut() {
        PullRecorder r = new PullRecorder();
        final PullRecorder.Pull[] got = new PullRecorder.Pull[1];
        r.setSink(new PullRecorder.Sink() { public void saved(PullRecorder.Pull p) { got[0] = p; } });
        r.setBaro(100f);
        for (long t = 0; t <= 12000; t += 50) {
            boolean floored = t >= 2000 && t < 6000;
            float pedal = floored ? 82f : 20f;
            float boostKpa = !floored ? 5f : t < 4200 ? (t - 2000) / 2200f * 150f : t < 4600 ? 90f : 140f;
            boolean cut = t >= 4200 && t < 4600;
            float thr = floored ? (cut ? 30f : 92f) : 20f;
            float rpm = floored ? 3000f + (t - 2000) * 0.6f : 2000f;
            r.sample(t, 0x49, pedal);
            r.sample(t, 0x4C, thr);
            r.sample(t, 0x11, thr);
            r.sample(t, 0x0C, rpm);
            r.sample(t, 0x0B, 100f + boostKpa);
            r.sample(t, 0x0D, 60f + t / 200f);
            r.sample(t, 0x0E, floored ? 8f : 20f);
            r.sample(t, 0x23, 18000f);
        }
        PullRecorder.Pull p = got[0];
        check(p != null, "pull: saved after the lift");
        if (p == null) return;
        check(p.hasPedal, "pull: used the pedal reading");
        check(Math.abs(p.start - 2000) <= 100, "pull: starts when the pedal went down (" + p.start + ")");
        check(Math.abs(p.end - 5950) <= 100, "pull: ends at the lift (" + p.end + ")");
        check(p.cuts.size() == 1 && Math.abs(p.cuts.get(0) - 4200) <= 100, "pull: one torque cut near 4.2 s " + p.cuts);
        check(Math.abs(p.peakBoostPsi - 21.26f) < 0.1f, "pull: peak boost " + p.peakBoostPsi);
        boolean overboost = false;
        for (String f : p.findings) if (f.contains("overboost")) overboost = true;
        check(overboost, "pull: blames overboost when boost peaked right before the cut");
        check(p.csv().split("\n").length == p.size() + 1, "pull: one CSV row per reading");
        check(p.t(0) >= p.start - PullRecorder.BEFORE_MS, "pull: keeps the 5 s before the pedal went down");
    }

    static void quietCruise() {
        PullRecorder r = new PullRecorder();
        final int[] saved = { 0 };
        r.setSink(new PullRecorder.Sink() { public void saved(PullRecorder.Pull p) { saved[0]++; } });
        for (long t = 0; t < 60000; t += 50) {
            r.sample(t, 0x49, 30f + (t % 5000) / 500f);
            r.sample(t, 0x0C, 2200f);
        }
        check(saved[0] == 0 && !r.active(), "cruise: no pull recorded at part throttle");
    }

    static void stab() {
        PullRecorder r = new PullRecorder();
        final int[] saved = { 0 };
        r.setSink(new PullRecorder.Sink() { public void saved(PullRecorder.Pull p) { saved[0]++; } });
        for (long t = 0; t < 10000; t += 50) {
            r.sample(t, 0x49, t >= 2000 && t < 2400 ? 82f : 18f);
            r.sample(t, 0x0C, 2500f);
        }
        check(saved[0] == 0, "stab: a 0.4 s blip is not a pull");
    }
}
