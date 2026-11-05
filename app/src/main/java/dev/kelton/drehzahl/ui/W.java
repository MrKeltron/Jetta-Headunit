package dev.kelton.drehzahl.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Builders for the common pieces of every screen: text, cards, rows, buttons, dialogs. */
public final class W {
    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private W() { }

    public interface FloatCb { void on(float v); }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Theme.regular());
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(tf);
        return t;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams weight(float wgt) { return new LinearLayout.LayoutParams(0, MATCH, wgt); }

    public static LinearLayout.LayoutParams vweight(float wgt) { return new LinearLayout.LayoutParams(MATCH, 0, wgt); }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(Theme.dp(l), Theme.dp(t), Theme.dp(r), Theme.dp(b));
        return p;
    }

    public static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(dp), Theme.dp(dp)));
        return v;
    }

    public static View spring(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    public static ImageView icon(Context c, int kind, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new Icons(kind, color));
        iv.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(sizeDp), Theme.dp(sizeDp)));
        return iv;
    }

    /** A scrolling page with standard padding; add rows to the returned box. */
    public static LinearLayout page(Context c, ViewGroup host) {
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout box = vbox(c);
        box.setPadding(Theme.dp(28), Theme.dp(20), Theme.dp(28), Theme.dp(28));
        sv.addView(box, new FrameLayout.LayoutParams(MATCH, WRAP));
        host.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        return box;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(Theme.round(Theme.PANEL, 18));
        l.setPadding(Theme.dp(22), Theme.dp(20), Theme.dp(22), Theme.dp(20));
        return l;
    }

    public static TextView header(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 13, Theme.DIM, Theme.medium());
        t.setLetterSpacing(0.12f);
        t.setPadding(Theme.dp(4), Theme.dp(18), 0, Theme.dp(10));
        return t;
    }

    public static TextView note(Context c, String s) {
        TextView t = text(c, s, 15, Theme.DIM);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(Theme.dp(4), Theme.dp(8), Theme.dp(4), Theme.dp(8));
        return t;
    }

    /** Title + optional subtitle on the left, any view on the right. */
    public static LinearLayout row(Context c, String title, String sub, View right, View.OnClickListener click) {
        LinearLayout row = hbox(c);
        row.setBackground(Theme.round(Theme.PANEL, 14));
        row.setPadding(Theme.dp(20), Theme.dp(14), Theme.dp(16), Theme.dp(14));
        row.setMinimumHeight(Theme.dp(72));
        LinearLayout texts = vbox(c);
        TextView t = text(c, title, 19, Theme.TEXT);
        texts.addView(t);
        if (!TextUtils.isEmpty(sub)) {
            TextView s = text(c, sub, 14, Theme.DIM);
            s.setPadding(0, Theme.dp(5), 0, 0);
            s.setLineSpacing(0, 1.15f);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (right != null) {
            LinearLayout.LayoutParams p = lp(WRAP, WRAP);
            p.leftMargin = Theme.dp(12);
            row.addView(right, p);
        }
        if (click != null) row.setOnClickListener(click);
        LinearLayout.LayoutParams lp = lp(MATCH, WRAP);
        lp.bottomMargin = Theme.dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    public static LinearLayout chevronRow(Context c, String title, String sub, View.OnClickListener click) {
        ImageView chev = icon(c, Icons.BACK, Theme.DIM, 22);
        chev.setRotation(180);
        return row(c, title, sub, chev, click);
    }

    public static LinearLayout valueRow(Context c, String title, String value) {
        return row(c, title, null, text(c, value, 17, Theme.DIM), null);
    }

    public static LinearLayout toggleRow(Context c, String title, String sub, boolean on, Controls.BoolCb cb) {
        final Controls.Toggle t = new Controls.Toggle(c, on, cb);
        LinearLayout row = row(c, title, sub, t, null);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { t.set(!t.isOn(), true); }
        });
        return row;
    }

    /** Slider block: title and live value on top, slider underneath. */
    public static LinearLayout sliderRow(Context c, String title, int min, int max, int value, boolean centered,
                                         final Controls.Fmt fmt, final Controls.IntCb cb) {
        LinearLayout box = vbox(c);
        box.setBackground(Theme.round(Theme.PANEL, 14));
        box.setPadding(Theme.dp(20), Theme.dp(14), Theme.dp(12), Theme.dp(6));
        LinearLayout top = hbox(c);
        top.addView(text(c, title, 19, Theme.TEXT), new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView val = text(c, fmt.f(value), 17, Theme.accent, Theme.medium());
        val.setPadding(0, 0, Theme.dp(8), 0);
        top.addView(val);
        box.addView(top);
        Controls.Slider s = new Controls.Slider(c, min, max, value, centered, new Controls.IntCb() {
            @Override public void on(int v) { val.setText(fmt.f(v)); cb.on(v); }
        });
        box.addView(s, lp(MATCH, WRAP));
        LinearLayout.LayoutParams lp = lp(MATCH, WRAP);
        lp.bottomMargin = Theme.dp(8);
        box.setLayoutParams(lp);
        box.setTag(s);
        return box;
    }

    public static LinearLayout choiceRow(Context c, String title, String sub, String[] options, int sel, Controls.IntCb cb) {
        LinearLayout box = vbox(c);
        box.setBackground(Theme.round(Theme.PANEL, 14));
        box.setPadding(Theme.dp(20), Theme.dp(14), Theme.dp(16), Theme.dp(16));
        box.addView(text(c, title, 19, Theme.TEXT));
        if (!TextUtils.isEmpty(sub)) {
            TextView s = text(c, sub, 14, Theme.DIM);
            s.setPadding(0, Theme.dp(5), 0, 0);
            box.addView(s);
        }
        Controls.Segment seg = new Controls.Segment(c, options, sel, cb);
        LinearLayout.LayoutParams sp = lp(MATCH, WRAP);
        sp.topMargin = Theme.dp(12);
        box.addView(seg, sp);
        LinearLayout.LayoutParams lp = lp(MATCH, WRAP);
        lp.bottomMargin = Theme.dp(8);
        box.setLayoutParams(lp);
        box.setTag(seg);
        return box;
    }

    public static TextView button(Context c, String s, boolean primary, View.OnClickListener l) {
        TextView b = text(c, s, 17, primary ? 0xFFFFFFFF : Theme.TEXT, Theme.medium());
        boolean lightAccent = primary && (Theme.accent == Theme.ACCENTS[1] || Theme.accent == Theme.ACCENTS[2]);
        if (lightAccent) b.setTextColor(0xFF0E1217);
        b.setGravity(Gravity.CENTER);
        b.setBackground(primary ? Theme.round(Theme.accent, 12) : Theme.outline(Theme.PANEL2, Theme.LINE, 12));
        b.setPadding(Theme.dp(22), 0, Theme.dp(22), 0);
        b.setMinHeight(Theme.dp(56));
        b.setMinWidth(Theme.dp(120));
        b.setOnClickListener(l);
        return b;
    }

    public static void setEnabledDeep(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : 0.45f);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), on);
            g.setAlpha(on ? 1f : 0.45f);
        }
    }

    public static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }

    public static void confirm(Context c, String title, String msg, final Runnable yes) {
        new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title).setMessage(msg)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { yes.run(); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    public static void askNumber(Context c, String title, String unit, float current, final FloatCb cb) {
        final EditText et = new EditText(c);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setText(current == Math.round(current) ? String.valueOf(Math.round(current)) : String.valueOf(current));
        et.setSelectAllOnFocus(true);
        et.setHint(unit);
        FrameLayout f = new FrameLayout(c);
        f.setPadding(Theme.dp(24), Theme.dp(8), Theme.dp(24), 0);
        f.addView(et);
        new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title + (unit.length() > 0 ? " (" + unit + ")" : ""))
                .setView(f)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try { cb.on(Float.parseFloat(et.getText().toString().trim())); } catch (NumberFormatException ignored) { }
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    public static void pick(Context c, String title, String[] items, final Controls.IntCb cb) {
        new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { cb.on(w); }
                }).show();
    }
}
