package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Friction circle. The dot goes where your body is pushed:
 * braking moves it up, accelerating down, a left turn moves it right.
 */
public class GMeterView extends View {
    private static final int TRAIL = 60;
    private final float[] tx = new float[TRAIL], ty = new float[TRAIL];
    private int head;
    private float x, y;
    public float peakTurnLeft, peakTurnRight, peakAcc, peakBrake;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float range = 1.25f;

    public GMeterView(Context c) {
        super(c);
        p.setTextAlign(Paint.Align.CENTER);
    }

    /** latG: + = turning right. lonG: + = accelerating. */
    public void update(float latG, float lonG) {
        x = -latG;
        y = lonG;
        tx[head] = x; ty[head] = y;
        head = (head + 1) % TRAIL;
        if (latG < 0) peakTurnLeft = Math.max(peakTurnLeft, -latG); else peakTurnRight = Math.max(peakTurnRight, latG);
        if (lonG > 0) peakAcc = Math.max(peakAcc, lonG); else peakBrake = Math.max(peakBrake, -lonG);
        invalidate();
    }

    public void resetPeaks() { peakTurnLeft = peakTurnRight = peakAcc = peakBrake = 0; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float rad = Math.min(cx, cy) - Theme.dp(18);
        float k = rad / range;

        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.PANEL);
        c.drawCircle(cx, cy, rad, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1.5f));
        p.setColor(Theme.LINE);
        for (float g = 0.25f; g <= range + 0.01f; g += 0.25f) {
            p.setColor(Math.abs(g - 1f) < 0.01f ? Theme.FAINT : Theme.LINE);
            c.drawCircle(cx, cy, g * k, p);
        }
        p.setColor(Theme.LINE);
        c.drawLine(cx - rad, cy, cx + rad, cy, p);
        c.drawLine(cx, cy - rad, cx, cy + rad, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.DIM);
        p.setTypeface(Theme.medium());
        p.setTextSize(Theme.dpf(13));
        c.drawText("0.5", cx + Theme.dp(14), cy - 0.5f * k - Theme.dp(4), p);
        c.drawText("1.0 g", cx + Theme.dp(20), cy - 1f * k - Theme.dp(4), p);

        p.setColor(Theme.accent);
        drawPeak(c, cx, cy - peakBrake * k);
        drawPeak(c, cx, cy + peakAcc * k);
        drawPeak(c, cx + peakTurnLeft * k, cy);
        drawPeak(c, cx - peakTurnRight * k, cy);

        for (int i = 0; i < TRAIL; i++) {
            int idx = (head + i) % TRAIL;
            float a = i / (float) TRAIL;
            p.setColor(Theme.alpha(Theme.accent, a * 0.5f));
            c.drawCircle(cx + tx[idx] * k, cy + ty[idx] * k, Theme.dpf(3 + 3 * a), p);
        }
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx + x * k, cy + y * k, Theme.dpf(13), p);
        p.setColor(Theme.accent);
        c.drawCircle(cx + x * k, cy + y * k, Theme.dpf(8), p);
    }

    private void drawPeak(Canvas c, float px, float py) {
        c.drawCircle(px, py, Theme.dpf(4), p);
    }
}
