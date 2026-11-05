package dev.kelton.drehzahl.vehicle;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Start / lap / stop timer. Keeps running while you switch screens. */
public final class LapTimer {
    public final List<Long> laps = new ArrayList<>();
    private long start, lapStart, stoppedAt;
    private boolean running;

    public boolean running() { return running; }

    public void startStop() {
        long now = SystemClock.elapsedRealtime();
        if (running) {
            stoppedAt = now;
            running = false;
        } else {
            if (start == 0) { start = now; lapStart = now; }
            else { long pause = now - stoppedAt; start += pause; lapStart += pause; }
            running = true;
        }
    }

    public void lap() {
        if (!running) return;
        long now = SystemClock.elapsedRealtime();
        laps.add(0, now - lapStart);
        lapStart = now;
    }

    public void reset() { running = false; start = 0; lapStart = 0; stoppedAt = 0; laps.clear(); }

    private long now() { return running ? SystemClock.elapsedRealtime() : stoppedAt; }

    public long total() { return start == 0 ? 0 : now() - start; }
    public long currentLap() { return start == 0 ? 0 : now() - lapStart; }

    public long best() {
        long b = Long.MAX_VALUE;
        for (long l : laps) b = Math.min(b, l);
        return laps.isEmpty() ? 0 : b;
    }

    public static String fmt(long ms) {
        return String.format(Locale.US, "%d:%02d.%02d", ms / 60000, (ms / 1000) % 60, (ms / 10) % 100);
    }
}
