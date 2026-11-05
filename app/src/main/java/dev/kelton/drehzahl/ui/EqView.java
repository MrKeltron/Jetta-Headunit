package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** 10-band graphic equalizer. Drag any band; a smooth curve runs through all of them. */
public class EqView extends View {
    public interface BandCb { void on(int band, float db); }

    public static final String[] LABELS = { "31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k" };
    public static final float RANGE = 12f;

    private final float[] gains = new float[10];
    private final BandCb cb;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve = new Path();
    private final RectF r = new RectF();
    private int dragging = -1;

    public EqView(Context c, float[] start, BandCb cb) {
        super(c);
        System.arraycopy(start, 0, gains, 0, 10);
        this.cb = cb;
        p.setTextAlign(Paint.Align.CENTER);
    }

    public void setGains(float[] g) { System.arraycopy(g, 0, gains, 0, 10); invalidate(); }

    private float top() { return Theme.dp(34); }
    private float bottom() { return getHeight() - Theme.dp(40); }
    private float colX(int i) { float pad = Theme.dp(30); return pad + (getWidth() - 2 * pad) * (i + 0.5f) / 10f; }
    private float yFor(float db) { return top() + (bottom() - top()) * (RANGE - db) / (2 * RANGE); }

    @Override protected void onDraw(Canvas c) {
        float t = top(), b = bottom(), mid = yFor(0);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1));
        p.setColor(Theme.LINE);
        for (float db = -RANGE; db <= RANGE; db += 6) c.drawLine(Theme.dp(18), yFor(db), getWidth() - Theme.dp(18), yFor(db), p);
        p.setColor(Theme.FAINT);
        c.drawLine(Theme.dp(18), mid, getWidth() - Theme.dp(18), mid, p);

        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 10; i++) {
            float x = colX(i);
            p.setColor(Theme.PANEL2);
            r.set(x - Theme.dp(3), t, x + Theme.dp(3), b);
            c.drawRoundRect(r, Theme.dp(3), Theme.dp(3), p);
            p.setColor(Theme.alpha(Theme.accent, 0.85f));
            float y = yFor(gains[i]);
            r.set(x - Theme.dp(3), Math.min(y, mid), x + Theme.dp(3), Math.max(y, mid));
            c.drawRect(r, p);
        }

        curve.reset();
        for (int i = 0; i < 10; i++) {
            float x = colX(i), y = yFor(gains[i]);
            if (i == 0) curve.moveTo(x, y);
            else {
                float px = colX(i - 1), py = yFor(gains[i - 1]);
                float cxm = (px + x) / 2f;
                curve.cubicTo(cxm, py, cxm, y, x, y);
            }
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(2.5f));
        p.setColor(Theme.alpha(Theme.TEXT, 0.6f));
        c.drawPath(curve, p);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Theme.medium());
        for (int i = 0; i < 10; i++) {
            float x = colX(i), y = yFor(gains[i]);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(x, y, Theme.dpf(i == dragging ? 15 : 12), p);
            p.setColor(Theme.accent);
            c.drawCircle(x, y, Theme.dpf(6), p);
            p.setColor(Theme.DIM);
            p.setTextSize(Theme.dpf(14));
            c.drawText(LABELS[i], x, getHeight() - Theme.dp(12), p);
            p.setColor(i == dragging ? Theme.accent : Theme.TEXT);
            p.setTextSize(Theme.dpf(13));
            c.drawText(String.format(Locale.US, "%+.1f", gains[i]), x, Theme.dp(18), p);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float best = Float.MAX_VALUE;
            for (int i = 0; i < 10; i++) {
                float d = Math.abs(e.getX() - colX(i));
                if (d < best) { best = d; dragging = i; }
            }
        }
        if (dragging >= 0 && (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE)) {
            float db = RANGE - (e.getY() - top()) / (bottom() - top()) * 2 * RANGE;
            db = Math.round(Math.max(-RANGE, Math.min(RANGE, db)) * 2f) / 2f;
            if (db != gains[dragging]) {
                gains[dragging] = db;
                if (cb != null) cb.on(dragging, db);
            }
            invalidate();
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { dragging = -1; invalidate(); }
        return true;
    }
}
