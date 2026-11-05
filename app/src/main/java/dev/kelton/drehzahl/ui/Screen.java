package dev.kelton.drehzahl.ui;

import android.view.View;

import java.util.ArrayList;
import java.util.List;

import dev.kelton.drehzahl.MainActivity;

/** One full screen in the content area. Views are built lazily and rebuilt after a theme change. */
public abstract class Screen {
    protected MainActivity host;
    private View view;
    private final List<Runnable> cleanups = new ArrayList<>();

    public abstract String title();

    /** Which rail button lights up for this screen (MainActivity.SECTION_*). */
    public abstract int section();

    protected abstract View build();

    public final View view(MainActivity h) {
        host = h;
        if (view == null) view = build();
        return view;
    }

    public final void invalidateView() {
        runCleanups();
        view = null;
    }

    /** Register something to undo when this screen hides (listeners, timers). */
    protected final void onLeave(Runnable r) { cleanups.add(r); }

    protected final void runCleanups() {
        for (Runnable r : cleanups) {
            try { r.run(); } catch (RuntimeException ignored) { }
        }
        cleanups.clear();
    }

    public void onShow() { }

    public void onHide() { runCleanups(); }

    /** Return true if the screen handled Back itself. */
    public boolean onBack() { return false; }
}
