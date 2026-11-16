package dev.kelton.drehzahl.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which home app the "Stock launcher" button opens. Plain Java so the PC tests can check it.
 * Automatic means the launcher the tablet came with: a preinstalled (system) home app, highest priority first.
 * A saved choice (Settings > Home and apps) wins while that app is still installed.
 */
public final class LauncherPick {
    public static final class Home {
        public final String pkg, cls, label;
        public final boolean system;
        public final int priority;

        public Home(String pkg, String cls, String label, boolean system, int priority) {
            this.pkg = pkg; this.cls = cls; this.label = label; this.system = system; this.priority = priority;
        }

        /** "package/class", the form saved in settings. */
        public String id() { return pkg + "/" + cls; }
    }

    private LauncherPick() { }

    /** Home apps the button may open: not Drehzahl itself, and not Android's placeholder shown before unlock. */
    public static List<Home> usable(List<Home> all, String self) {
        List<Home> out = new ArrayList<>();
        if (all == null) return out;
        for (Home h : all) {
            if (h == null || h.pkg == null || h.cls == null) continue;
            if (h.pkg.equals(self) || h.priority < 0 || h.pkg.equals("com.android.settings")) continue;
            out.add(h);
        }
        return out;
    }

    /** The home app to open, or null when the tablet has no other launcher. */
    public static Home choose(List<Home> all, String self, String saved) {
        List<Home> list = usable(all, self);
        if (saved != null && !saved.isEmpty()) for (Home h : list) if (h.id().equals(saved)) return h;
        Home best = null;
        for (Home h : list) {
            if (best == null) { best = h; continue; }
            if (h.system != best.system) { if (h.system) best = h; continue; }
            if (h.priority > best.priority) best = h;
        }
        return best;
    }
}
