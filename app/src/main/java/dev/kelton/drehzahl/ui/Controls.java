package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** The three touch controls used everywhere: toggle, slider and segmented picker. */
public final class Controls {
    private Controls() { }

    public interface IntCb { void on(int v); }
    public interface BoolCb { void on(boolean v); }
    public interface Fmt { String f(int v); }

    /** On/off pill. */
    public static class Toggle extends View {
        private boolean on;
        private final BoolCb cb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Toggle(Context c, boolean on, BoolCb cb) {
            super(c);
            this.on = on;
            this.cb = cb;
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { set(!Toggle.this.on, true); }
            });
        }

        public void set(boolean v, boolean notify) {
            on = v;
            invalidate();
            if (notify && cb != null) cb.on(v);
        }

        public boolean isOn() { return on; }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(Theme.dp(58), Theme.dp(34)); }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth();
            r.set(0, 0, w, h);
            p.setColor(on ? Theme.accent : Theme.LINE);
            if (!isEnabled()) p.setColor(Theme.alpha(Theme.LINE, 0.5f));
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setColor(on && Theme.accent == Theme.ACCENTS[1] ? Theme.BG : 0xFFFFFFFF);
            float cx = on ? w - h / 2 : h / 2;
            c.drawCircle(cx, h / 2, h / 2 - Theme.dp(4), p);
        }
    }

    /** Horizontal slider with integer steps. centered = fill grows from the middle (tone, balance). */
    public static class Slider extends View {
        private final int min, max;
        private int value;
        private final boolean centered;
        private final IntCb cb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Slider(Context c, int min, int max, int value, boolean centered, IntCb cb) {
            super(c);
            this.min = min; this.max = max; this.value = value; this.centered = centered; this.cb = cb;
        }

        public void set(int v) { value = Math.max(min, Math.min(max, v)); invalidate(); }
        public int get() { return value; }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Theme.dp(44));
        }

        private float xFor(int v) {
            float pad = Theme.dp(14);
            return pad + (getWidth() - 2 * pad) * (v - min) / (float) (max - min);
        }

        @Override protected void onDraw(Canvas c) {
            float cy = getHeight() / 2f, t = Theme.dp(3);
            float pad = Theme.dp(14);
            p.setColor(Theme.LINE);
            r.set(pad, cy - t, getWidth() - pad, cy + t);
            c.drawRoundRect(r, t, t, p);
            float x = xFor(value);
            float from = centered ? xFor(Math.max(min, Math.min(max, 0))) : pad;
            p.setColor(isEnabled() ? Theme.accent : Theme.FAINT);
            r.set(Math.min(from, x), cy - t, Math.max(from, x), cy + t);
            c.drawRoundRect(r, t, t, p);
            if (centered) {
                p.setColor(Theme.DIM);
                c.drawRect(from - Theme.dp(1), cy - Theme.dp(9), from + Theme.dp(1), cy + Theme.dp(9), p);
            }
            p.setColor(0xFFFFFFFF);
            c.drawCircle(x, cy, Theme.dp(11), p);
            p.setColor(isEnabled() ? Theme.accent : Theme.FAINT);
            c.drawCircle(x, cy, Theme.dp(5), p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!isEnabled()) return false;
            if (e.getAction() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            float pad = Theme.dp(14);
            float frac = (e.getX() - pad) / (getWidth() - 2 * pad);
            int v = Math.round(min + Math.max(0, Math.min(1, frac)) * (max - min));
            if (v != value) {
                value = v;
                invalidate();
                if (cb != null) cb.on(v);
            }
            return true;
        }
    }

    /** Row of options, one selected. */
    public static class Segment extends View {
        private final String[] items;
        private int sel;
        private final IntCb cb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Segment(Context c, String[] items, int sel, IntCb cb) {
            super(c);
            this.items = items; this.sel = sel; this.cb = cb;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Theme.medium());
        }

        public void set(int i) { sel = i; invalidate(); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Theme.dp(46));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = Theme.dp(10);
            p.setColor(Theme.PANEL2);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, p);
            float cw = w / items.length;
            p.setTextSize(Theme.dpf(items.length > 4 ? 13 : 15));
            for (int i = 0; i < items.length; i++) {
                if (i == sel) {
                    p.setColor(Theme.accent);
                    r.set(i * cw + Theme.dp(3), Theme.dp(3), (i + 1) * cw - Theme.dp(3), h - Theme.dp(3));
                    c.drawRoundRect(r, rad - 2, rad - 2, p);
                }
                boolean lightFill = i == sel && (Theme.accent == Theme.ACCENTS[1] || Theme.accent == Theme.ACCENTS[2]);
                p.setColor(i == sel ? (lightFill ? 0xFF0E1217 : 0xFFFFFFFF) : (isEnabled() ? Theme.DIM : Theme.FAINT));
                c.drawText(items[i], i * cw + cw / 2, h / 2 - (p.descent() + p.ascent()) / 2, p);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!isEnabled()) return false;
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int i = (int) (e.getX() / (getWidth() / (float) items.length));
                i = Math.max(0, Math.min(items.length - 1, i));
                if (i != sel) {
                    sel = i;
                    invalidate();
                    if (cb != null) cb.on(i);
                }
            }
            return true;
        }
    }
}
