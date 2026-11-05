package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Top view of a cabin with four seats. Drag the dot: left/right = balance, up/down = fader. Steps -9..+9. */
public class BalancePad extends View {
    public interface Cb { void on(int balance, int fader); }

    private int bal, fad;
    private final Cb cb;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path body = new Path();

    public BalancePad(Context c, int balance, int fader, Cb cb) {
        super(c);
        bal = balance; fad = fader; this.cb = cb;
    }

    public void set(int balance, int fader) { bal = balance; fad = fader; invalidate(); }

    private RectF cabin() {
        float h = getHeight() - Theme.dp(24), w = h * 0.62f;
        float cx = getWidth() / 2f, top = Theme.dp(12);
        r.set(cx - w / 2, top, cx + w / 2, top + h);
        return r;
    }

    @Override protected void onDraw(Canvas c) {
        RectF cab = new RectF(cabin());
        float rad = cab.width() * 0.32f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.PANEL);
        c.drawRoundRect(cab, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(2));
        p.setColor(Theme.LINE);
        c.drawRoundRect(cab, rad, rad, p);

        body.reset();
        float wsTop = cab.top + cab.height() * 0.18f;
        body.moveTo(cab.left + cab.width() * 0.12f, wsTop);
        body.quadTo(cab.centerX(), wsTop - cab.height() * 0.05f, cab.right - cab.width() * 0.12f, wsTop);
        c.drawPath(body, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.PANEL2);
        float sw = cab.width() * 0.27f, sh = cab.height() * 0.15f;
        float[] ys = { cab.top + cab.height() * 0.30f, cab.top + cab.height() * 0.60f };
        float[] xs = { cab.left + cab.width() * 0.13f, cab.right - cab.width() * 0.13f - sw };
        for (float y : ys) for (float x : xs) c.drawRoundRect(new RectF(x, y, x + sw, y + sh), Theme.dp(8), Theme.dp(8), p);

        float px = cab.centerX() + bal / 9f * cab.width() * 0.36f;
        float py = cab.centerY() - fad / 9f * cab.height() * 0.36f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1));
        p.setColor(Theme.alpha(Theme.accent, 0.6f));
        c.drawLine(cab.left + Theme.dp(10), py, cab.right - Theme.dp(10), py, p);
        c.drawLine(px, cab.top + Theme.dp(10), px, cab.bottom - Theme.dp(10), p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.alpha(Theme.accent, 0.25f));
        c.drawCircle(px, py, Theme.dpf(30), p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(px, py, Theme.dpf(14), p);
        p.setColor(Theme.accent);
        c.drawCircle(px, py, Theme.dpf(8), p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        getParent().requestDisallowInterceptTouchEvent(true);
        RectF cab = cabin();
        int b = Math.round((e.getX() - cab.centerX()) / (cab.width() * 0.36f) * 9f);
        int f = Math.round(-(e.getY() - cab.centerY()) / (cab.height() * 0.36f) * 9f);
        b = Math.max(-9, Math.min(9, b));
        f = Math.max(-9, Math.min(9, f));
        if (b != bal || f != fad) {
            bal = b; fad = f;
            invalidate();
            if (cb != null) cb.on(b, f);
        }
        return true;
    }
}
