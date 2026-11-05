package dev.kelton.drehzahl.core;

import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Short in-memory log of what Drehzahl is doing. The Code theme shows it on the home screen. */
public final class EventLog {
    public interface Listener { void onEvent(); }

    private static final int MAX = 60;
    private static final List<String> lines = new ArrayList<>();
    private static final List<Listener> listeners = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private EventLog() { }

    public static void add(String msg) {
        synchronized (lines) {
            lines.add(fmt.format(new Date()) + "  " + msg);
            while (lines.size() > MAX) lines.remove(0);
        }
        main.post(new Runnable() {
            public void run() { for (Listener l : new ArrayList<>(listeners)) l.onEvent(); }
        });
    }

    /** Newest last. */
    public static List<String> last(int n) {
        synchronized (lines) {
            return new ArrayList<>(lines.subList(Math.max(0, lines.size() - n), lines.size()));
        }
    }

    public static void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }
}
