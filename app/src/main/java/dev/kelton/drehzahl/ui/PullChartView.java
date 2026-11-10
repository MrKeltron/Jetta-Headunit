package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A recorded pull: one line per reading, each on its own scale, time along the bottom.
 * Touch and slide to read every value at that moment. Red marks are torque cuts.
 */
public class PullChartView extends View {
    /** Column name in the pull CSV, label, unit, color, fixed range (NaN = from the data). */
    private static final Object[][] SERIES = {
        { "boost_psi", "Boost", "psi", 0xFFFF3B3B, Float.NaN, Float.NaN },
        { "rpm", "RPM", "", 0xFFFFFFFF, 0f, 7000f },
        { "pedal_pct", "Pedal", "%", 0xFF35D07F, 0f, 100f },
        { "throttle_cmd_pct", "Throttle asked", "%", 0xFFFFB020, 0f, 100f },
        { "throttle_pct", "Throttle", "%", 0xFF4FA3FF, 0f, 100f },
        { "timing_deg", "Timing", "deg", 0xFFB57BFF, Float.NaN, Float.NaN },
        { "rail_bar", "Rail", "bar", 0xFF4FE0D0, Float.NaN, Float.NaN },
        { "lambda", "Lambda", "", 0xFFFF8AD8, 0.6f, 1.3f },
    };

    private final float[] t;
    private final List<float[]> data = new ArrayList<>();
    private final List<Object[]> shown = new ArrayList<>();
    private final List<float[]> ranges = new ArrayList<>();
    private final float[] cuts;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float cursor = Float.NaN;

    public PullChartView(Context c, String[] head, float[][] cols, float[] cutSeconds) {
        super(c);
        int ti = find(head, "t_s");
        t = ti >= 0 ? cols[ti] : new float[0];
        cuts = cutSeconds;
        for (Object[] s : SERIES) {
            int k = find(head, (String) s[0]);
            if (k < 0) continue;
            float[] v = cols[k];
            float lo = (Float) s[4], hi = (Float) s[5];
            if (Float.isNaN(lo)) {
                float mn = Float.NaN, mx = Float.NaN;
                for (float x : v) if (!Float.isNaN(x)) { mn = Float.isNaN(mn) ? x : Math.min(mn, x); mx = Float.isNaN(mx) ? x : Math.max(mx, x); }
                if (Float.isNaN(mn)) continue;
                float pad = Math.max(1f, (mx - mn) * 0.1f);
                lo = mn - pad; hi = mx + pad;
            }
            boolean any = false;
            for (float x : v) if (!Float.isNaN(x)) { any = true; break; }
            if (!any) continue;
            shown.add(s);
            data.add(v);
            ranges.add(new float[] { lo, hi });
        }
        setMinimumHeight(Theme.dp(320));
    }

    private static int find(String[] head, String name) {
        for (int i = 0; i < head.length; i++) if (head[i].trim().equals(name)) return i;
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
            cursor = Float.NaN;
        } else {
            cursor = e.getX();
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        invalidate();
        return true;
    }

    @Override protected void onDraw(Canvas c) {
        if (t.length < 2) return;
        float left = Theme.dpf(8), right = getWidth() - Theme.dpf(8);
        float top = Theme.dpf(30), bottom = getHeight() - Theme.dpf(26);
        float t0 = t[0], t1 = t[t.length - 1];
        if (t1 <= t0) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1));
        p.setColor(Theme.alpha(Theme.LINE, 0.9f));
        for (int i = 0; i <= 4; i++) { float y = top + (bottom - top) * i / 4f; c.drawLine(left, y, right, y, p); }
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Theme.mono());
        p.setTextSize(Theme.dpf(12));
        p.setColor(Theme.DIM);
        for (float s = (float) Math.ceil(t0); s <= t1; s += 1f) {
            float x = left + (right - left) * (s - t0) / (t1 - t0);
            c.drawText(String.format(Locale.US, "%.0fs", s), x - Theme.dpf(8), getHeight() - Theme.dpf(6), p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(2));
        p.setColor(Theme.BAD);
        for (float cs : cuts) {
            float x = left + (right - left) * (cs - t0) / (t1 - t0);
            c.drawLine(x, top, x, bottom, p);
        }
        for (int s = 0; s < shown.size(); s++) {
            float[] v = data.get(s), r = ranges.get(s);
            path.reset();
            boolean started = false;
            for (int i = 0; i < t.length; i++) {
                if (Float.isNaN(v[i])) continue;
                float x = left + (right - left) * (t[i] - t0) / (t1 - t0);
                float f = Math.max(0f, Math.min(1f, (v[i] - r[0]) / (r[1] - r[0])));
                float y = bottom - f * (bottom - top);
                if (!started) { path.moveTo(x, y); started = true; } else path.lineTo(x, y);
            }
            p.setColor((Integer) shown.get(s)[3]);
            p.setStrokeWidth(Theme.dpf(s == 0 ? 2.5f : 1.6f));
            c.drawPath(path, p);
        }
        int at = -1;
        if (!Float.isNaN(cursor)) {
            float ct = t0 + (t1 - t0) * Math.max(0f, Math.min(1f, (cursor - left) / (right - left)));
            for (int i = 0; i < t.length; i++) if (t[i] <= ct) at = i;
            p.setColor(Theme.TEXT);
            p.setStrokeWidth(Theme.dpf(1));
            c.drawLine(cursor, top, cursor, bottom, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Theme.medium());
        p.setTextSize(Theme.dpf(13));
        float x = left;
        for (int s = 0; s < shown.size(); s++) {
            Object[] info = shown.get(s);
            String label = (String) info[1];
            if (at >= 0) {
                float val = Float.NaN;
                for (int i = at; i >= 0; i--) if (!Float.isNaN(data.get(s)[i])) { val = data.get(s)[i]; break; }
                if (!Float.isNaN(val)) label += " " + fmt(val) + (((String) info[2]).isEmpty() ? "" : " " + info[2]);
            }
            p.setColor((Integer) info[3]);
            c.drawRect(x, Theme.dpf(10), x + Theme.dpf(10), Theme.dpf(20), p);
            p.setColor(Theme.TEXT);
            c.drawText(label, x + Theme.dpf(14), Theme.dpf(20), p);
            x += p.measureText(label) + Theme.dpf(30);
        }
        if (at >= 0) {
            p.setColor(Theme.DIM);
            p.setTypeface(Theme.mono());
            c.drawText(String.format(Locale.US, "%.2f s", t[at]), Math.min(cursor + Theme.dpf(6), right - Theme.dpf(60)), bottom - Theme.dpf(6), p);
        }
    }

    private static String fmt(float v) {
        if (Math.abs(v) >= 100) return String.format(Locale.US, "%,.0f", v);
        if (Math.abs(v) >= 10) return String.format(Locale.US, "%.1f", v);
        return String.format(Locale.US, "%.2f", v);
    }
}
