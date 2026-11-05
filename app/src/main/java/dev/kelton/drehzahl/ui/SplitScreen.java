package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Menu list on the left, the selected page on the right. Sound, Car and Settings use this. */
public abstract class SplitScreen extends Screen {
    protected int selected = 0;
    private FrameLayout pageHost;
    private final List<LinearLayout> menuRows = new ArrayList<>();
    private final List<Runnable> pageCleanups = new ArrayList<>();

    protected abstract String[] items();

    protected abstract int icon(int i);

    /** Build page i into host. Use onPageLeave() for listeners that must stop when the page changes. */
    protected abstract void page(int i, FrameLayout host);

    protected final void onPageLeave(Runnable r) { pageCleanups.add(r); }

    @Override protected View build() {
        return Theme.grid() ? buildTabs() : buildSide();
    }

    /** Head unit themes: a row of tab buttons across the top instead of a side menu. */
    private View buildTabs() {
        Context c = host;
        LinearLayout root = W.vbox(c);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = W.hbox(c);
        tabs.setPadding(Theme.dp(20), Theme.dp(4), Theme.dp(20), Theme.dp(10));
        menuRows.clear();
        String[] it = items();
        for (int i = 0; i < it.length; i++) {
            final int idx = i;
            LinearLayout tab = W.hbox(c);
            tab.setPadding(Theme.dp(18), 0, Theme.dp(22), 0);
            tab.setMinimumHeight(Theme.dp(58));
            tab.addView(W.icon(c, icon(i), Theme.DIM, 24));
            TextView t = W.text(c, Theme.label(it[i]), 17, Theme.TEXT, Theme.medium());
            if (Theme.upper) t.setLetterSpacing(0.06f);
            t.setPadding(Theme.dp(10), 0, 0, 0);
            t.setMaxLines(1);
            tab.addView(t);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { select(idx); }
            });
            LinearLayout.LayoutParams lp = W.lp(W.WRAP, W.WRAP);
            lp.rightMargin = Theme.dp(8);
            tabs.addView(tab, lp);
            menuRows.add(tab);
        }
        hs.addView(tabs);
        root.addView(hs, W.lp(W.MATCH, W.WRAP));
        View line = new View(c);
        line.setBackgroundColor(Theme.LINE);
        root.addView(line, W.lp(W.MATCH, Theme.dp(1)));
        pageHost = new FrameLayout(c);
        root.addView(pageHost, W.vweight(1));
        select(selected);
        return root;
    }

    private View buildSide() {
        Context c = host;
        LinearLayout root = W.hbox(c);
        root.setGravity(Gravity.TOP);

        ScrollView menuScroll = new ScrollView(c);
        menuScroll.setVerticalScrollBarEnabled(false);
        LinearLayout menu = W.vbox(c);
        menu.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(8), Theme.dp(16));
        menuRows.clear();
        String[] it = items();
        for (int i = 0; i < it.length; i++) {
            final int idx = i;
            LinearLayout row = W.hbox(c);
            row.setPadding(Theme.dp(16), 0, Theme.dp(12), 0);
            row.setMinimumHeight(Theme.dp(64));
            row.addView(W.icon(c, icon(i), Theme.DIM, 26));
            TextView t = W.text(c, it[i], 18, Theme.TEXT);
            t.setPadding(Theme.dp(14), 0, 0, 0);
            t.setMaxLines(1);
            row.addView(t, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { select(idx); }
            });
            LinearLayout.LayoutParams lp = W.lp(W.MATCH, W.WRAP);
            lp.bottomMargin = Theme.dp(4);
            menu.addView(row, lp);
            menuRows.add(row);
        }
        menuScroll.addView(menu);
        root.addView(menuScroll, new LinearLayout.LayoutParams(Theme.dp(300), W.MATCH));

        View line = new View(c);
        line.setBackgroundColor(Theme.LINE);
        root.addView(line, new LinearLayout.LayoutParams(Theme.dp(1), W.MATCH));

        pageHost = new FrameLayout(c);
        root.addView(pageHost, new LinearLayout.LayoutParams(0, W.MATCH, 1f));
        select(selected);
        return root;
    }

    public void select(int i) {
        selected = i;
        for (Runnable r : pageCleanups) {
            try { r.run(); } catch (RuntimeException ignored) { }
        }
        pageCleanups.clear();
        for (int k = 0; k < menuRows.size(); k++) {
            LinearLayout row = menuRows.get(k);
            boolean on = k == i;
            if (Theme.grid()) row.setBackground(on ? Theme.outline(Theme.alpha(Theme.accent, 0.16f), Theme.alpha(Theme.accent, 0.7f), 12)
                    : Theme.outline(Theme.alpha(Theme.PANEL, 0.9f), Theme.LINE, 12));
            else row.setBackground(on ? Theme.round(Theme.PANEL2, 12) : null);
            ((Icons) ((ImageView) row.getChildAt(0)).getDrawable()).setColor(on ? Theme.accent : Theme.DIM);
            ((TextView) row.getChildAt(1)).setTextColor(on ? Theme.TEXT : Theme.DIM);
        }
        if (pageHost != null) {
            pageHost.removeAllViews();
            page(i, pageHost);
        }
    }

    /** Rebuild the current page in place, for example after a setting changes what is shown. */
    public void refreshPage() { select(selected); }

    private boolean hidden;

    @Override public void onHide() {
        for (Runnable r : pageCleanups) {
            try { r.run(); } catch (RuntimeException ignored) { }
        }
        pageCleanups.clear();
        hidden = true;
        super.onHide();
    }

    @Override public void onShow() {
        if (hidden && pageHost != null) select(selected);
        hidden = false;
    }
}
