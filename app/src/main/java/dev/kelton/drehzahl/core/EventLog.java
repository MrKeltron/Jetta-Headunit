package dev.kelton.drehzahl.core;

import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Short in-memory log of what Drehzahl is doing. The Code theme shows it on the home screen.
 * Also kept in Android/data/dev.kelton.drehzahl/files/events.log (about 1 MB, then events.old.log),
 * so the PC toolkit can read what happened on a drive after logcat has rolled over.
 */
public final class EventLog {
    public interface Listener { void onEvent(); }

    private static final int MAX = 60;
    private static final List<String> lines = new ArrayList<>();
    private static final List<Listener> listeners = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private static final SimpleDateFormat day = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
    private static java.io.File file;
    private static final java.util.concurrent.ExecutorService disk = java.util.concurrent.Executors.newSingleThreadExecutor();

    private EventLog() { }

    public static void init(android.content.Context c) {
        java.io.File dir = c.getExternalFilesDir(null);
        if (dir != null) file = new java.io.File(dir, "events.log");
    }

    private static void save(final String line) {
        final java.io.File f = file;
        if (f == null) return;
        disk.execute(new Runnable() {
            public void run() {
                try {
                    if (f.length() > 1024 * 1024) f.renameTo(new java.io.File(f.getParentFile(), "events.old.log"));
                    try (java.io.FileWriter w = new java.io.FileWriter(f, true)) { w.write(line + "\n"); }
                } catch (Exception ignored) { }
            }
        });
    }

    public static void add(String msg) {
        android.util.Log.i("Drehzahl", msg);
        save(day.format(new Date()) + "  " + msg);
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
