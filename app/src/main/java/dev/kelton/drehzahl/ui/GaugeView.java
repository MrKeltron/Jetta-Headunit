package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import dev.kelton.drehzahl.vehicle.Metric;
import dev.kelton.drehzahl.vehicle.Units;
import dev.kelton.drehzahl.vehicle.VehicleData;

/** Round gauge: 240 degree arc, live value in the middle, peak marker on the arc. */
public class GaugeView extends View {
    private Metric metric;
    private float value = Float.NaN, peak = Float.NaN, shown;
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean compact;

    public GaugeView(Context c, Metric m, boolean compact) {
        super(c);
        this.metric = m;
        this.compact = compact;
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        txt.setTextAlign(Paint.Align.CENTER);
    }

    public Metric metric() { return metric; }

    public void setMetric(Metric m) { metric = m; resetPeak(); invalidate(); }

    public void resetPeak() { peak = Float.NaN; }

    public void update(VehicleData d) {
        value = metric.value(d);
        if (Float.isNaN(peak) || value > peak) peak = value;
        if (Float.isNaN(shown)) shown = value;
        shown += (value - shown) * 0.35f;
        invalidate();
    }

    public void clear() { value = Float.NaN; shown = Float.NaN; invalidate(); }

    private float frac(float v) {
        float f = (v - metric.min()) / (metric.max() - metric.min());
        return Math.max(0f, Math.min(1f, f));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h * 1.12f);
        float stroke = size * (compact ? 0.075f : 0.06f);
        float rad = size / 2f - stroke;
        float cx = w / 2f, cy = h / 2f + size * 0.06f;
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);

        arc.setStrokeWidth(stroke);
        arc.setColor(Theme.LINE);
        c.drawArc(r, 150, 240, false, arc);

        if (!Float.isNaN(metric.warn())) {
            arc.setColor(Theme.alpha(Theme.BAD, 0.35f));
            float wf = frac(metric.warn());
            c.drawArc(r, 150 + 240 * wf, 240 * (1 - wf), false, arc);
        }

        boolean hot = !Float.isNaN(value) && !Float.isNaN(metric.warn()) && value >= metric.warn();
        if (!Float.isNaN(shown)) {
            float zero = metric.min() < 0 ? frac(0f) : 0f;
            float f = frac(shown);
            arc.setColor(hot ? Theme.BAD : Theme.accent);
            c.drawArc(r, 150 + 240 * Math.min(zero, f), 240 * Math.abs(f - zero), false, arc);
        }

        arc.setStrokeWidth(Theme.dpf(1.5f));
        arc.setColor(Theme.FAINT);
        float inner = rad - stroke * 1.1f;
        for (int i = 0; i <= 8; i++) {
            double a = Math.toRadians(150 + 30 * i);
            float len = i % 2 == 0 ? stroke * 0.9f : stroke * 0.5f;
            c.drawLine(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * (inner - len), cy + (float) Math.sin(a) * (inner - len), arc);
        }

        if (!Float.isNaN(peak)) {
            double a = Math.toRadians(150 + 240 * frac(peak));
            arc.setColor(Theme.TEXT);
            arc.setStrokeWidth(Theme.dpf(3));
            c.drawLine(cx + (float) Math.cos(a) * (rad - stroke / 2), cy + (float) Math.sin(a) * (rad - stroke / 2),
                    cx + (float) Math.cos(a) * (rad + stroke / 2), cy + (float) Math.sin(a) * (rad + stroke / 2), arc);
        }

        txt.setTypeface(Theme.light());
        txt.setColor(hot ? Theme.BAD : Theme.TEXT);
        txt.setTextSize(size * (compact ? 0.24f : 0.22f));
        c.drawText(Float.isNaN(value) ? "--" : Units.fmt(value, metric.decimals()), cx, cy + size * 0.06f, txt);

        txt.setTypeface(Theme.medium());
        txt.setColor(Theme.DIM);
        txt.setTextSize(size * 0.075f);
        c.drawText(metric.unit(), cx, cy + size * 0.17f, txt);

        txt.setColor(Theme.TEXT);
        txt.setTextSize(size * (compact ? 0.09f : 0.08f));
        c.drawText(metric.label.toUpperCase(), cx, cy + rad * 0.86f, txt);

        if (!compact && !Float.isNaN(peak)) {
            txt.setTypeface(Theme.regular());
            txt.setColor(Theme.DIM);
            txt.setTextSize(size * 0.06f);
            c.drawText("max " + Units.fmt(peak, metric.decimals()), cx, cy - size * 0.18f, txt);
        }
    }
}
