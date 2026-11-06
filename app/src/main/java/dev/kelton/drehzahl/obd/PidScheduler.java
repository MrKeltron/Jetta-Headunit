package dev.kelton.drehzahl.obd;

/**
 * Decides which readings to ask the car for. A reading the car stops answering is rested for a few
 * seconds (2, 4, 8, then 16) and asked again, instead of being dropped until the next reconnect.
 * Plain Java so the PC tests can check it.
 */
final class PidScheduler {
    static final int MISS_LIMIT = 7;
    static final long[] REST_MS = { 2000, 4000, 8000, 16000 };

    private final boolean[] has = new boolean[256];
    private final boolean[] keep = new boolean[256];
    private final int[] misses = new int[256];
    private final int[] rounds = new int[256];
    private final long[] restUntil = new long[256];

    /** Starts a session with the readings the car says it supports. Readings in alwaysAsk are never rested. */
    void reset(boolean[] supported, int... alwaysAsk) {
        for (int i = 0; i < 256; i++) {
            has[i] = supported[i];
            keep[i] = false;
            misses[i] = 0;
            rounds[i] = 0;
            restUntil[i] = 0;
        }
        for (int p : alwaysAsk) keep[p & 0xFF] = true;
    }

    boolean has(int pid) { return has[pid & 0xFF]; }

    /** True when the reading is supported and not resting. */
    boolean due(int pid, long now) { return has[pid & 0xFF] && now >= restUntil[pid & 0xFF]; }

    boolean resting(int pid, long now) { return has[pid & 0xFF] && now < restUntil[pid & 0xFF]; }

    /** Counts a reply that did not contain the reading. Returns the rest in ms when it starts resting, else 0. */
    long miss(int pid, long now) {
        int p = pid & 0xFF;
        if (++misses[p] < MISS_LIMIT || keep[p]) return 0;
        misses[p] = 0;
        long rest = REST_MS[Math.min(rounds[p], REST_MS.length - 1)];
        rounds[p]++;
        restUntil[p] = now + rest;
        return rest;
    }

    /** Counts a good reply. Returns true when the reading had been resting and is back. */
    boolean hit(int pid) {
        int p = pid & 0xFF;
        misses[p] = 0;
        boolean back = rounds[p] > 0;
        rounds[p] = 0;
        restUntil[p] = 0;
        return back;
    }
}
