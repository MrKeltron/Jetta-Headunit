package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.Locale;

/** Rolling line graph of the last few seconds of one value (boost on the Code home screen). */
public class SparkView extends View {
    private final float[] buf;
    private int head, count;
    private final float min, max;
    private final String label;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();

    public SparkView(Context c, int samples, float min, float max, String label) {
        super(c);
        buf = new float[samples];
        this.min = min;
        this.max = max;
        this.label = label;
    }

    public void push(float v) {
        buf[head] = v;
        head = (head + 1) % buf.length;
        count = Math.min(count + 1, buf.length);
        invalidate();
    }

    private float y(float v, float h) {
        float f = (v - min) / (max - min);
        return h - Math.max(0f, Math.min(1f, f)) * h;
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1));
        p.setColor(Theme.alpha(Theme.LINE, 0.9f));
        for (int i = 1; i < 4; i++) c.drawLine(0, h * i / 4f, w, h * i / 4f, p);
        if (min < 0 && max > 0) {
            p.setColor(Theme.FAINT);
            c.drawLine(0, y(0, h), w, y(0, h), p);
        }
        line.reset();
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + buf.length) % buf.length;
            float x = w * i / (float) (buf.length - 1);
            float yy = y(buf[idx], h);
            if (i == 0) line.moveTo(x, yy); else line.lineTo(x, yy);
        }
        p.setColor(Theme.accent);
        p.setStrokeWidth(Theme.dpf(2));
        c.drawPath(line, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.DIM);
        p.setTypeface(Theme.mono());
        p.setTextSize(Theme.dpf(12));
        float last = count == 0 ? 0 : buf[(head - 1 + buf.length) % buf.length];
        c.drawText(String.format(Locale.US, "%s  %.1f   range %.0f..%.0f", label, last, min, max), Theme.dpf(6), Theme.dpf(14), p);
    }
}
