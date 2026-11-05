package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/** One home screen button. How it looks depends on the theme: square (GLI), round metal (Carbon), flat (Simple). */
public class TileView extends View {
    private final Icons icon;
    private final String label;
    private final Runnable action;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean down;

    public TileView(Context c, int iconKind, String label, Runnable action) {
        super(c);
        this.icon = new Icons(iconKind, Theme.TEXT);
        this.label = label;
        this.action = action;
        p.setTextAlign(Paint.Align.CENTER);
        setContentDescription(label);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: down = true; invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                boolean in = e.getX() >= 0 && e.getY() >= 0 && e.getX() <= getWidth() && e.getY() <= getHeight();
                if (in != down) { down = in; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                if (down) { down = false; invalidate(); performClick(); if (action != null) action.run(); }
                return true;
            case MotionEvent.ACTION_CANCEL: down = false; invalidate(); return true;
            default: return true;
        }
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override protected void onDraw(Canvas c) {
        switch (Theme.style) {
            case Theme.CARBON: drawCarbon(c); break;
            case Theme.SIMPLE: drawSimple(c); break;
            default: drawGli(c);
        }
    }

    private void drawGli(Canvas c) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, h, down ? Theme.PANEL2 : Theme.PANEL, Theme.alpha(Theme.PANEL2, 0.95f), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, Theme.radius(14), Theme.radius(14), p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1));
        p.setColor(down ? Theme.accent : Theme.LINE);
        c.drawRoundRect(r, Theme.radius(14), Theme.radius(14), p);
        // corner notch, top right
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.alpha(Theme.accent, down ? 1f : 0.85f));
        float n = Theme.dpf(12);
        android.graphics.Path tri = new android.graphics.Path();
        tri.moveTo(w - n * 1.6f, 0); tri.lineTo(w, 0); tri.lineTo(w, n * 1.6f); tri.close();
        c.drawPath(tri, p);
        float s = Math.min(w, h) * 0.34f;
        icon.setColor(down ? Theme.accent : Theme.TEXT);
        icon.setBounds((int) (w / 2 - s / 2), (int) (h * 0.40f - s / 2), (int) (w / 2 + s / 2), (int) (h * 0.40f + s / 2));
        icon.draw(c);
        p.setColor(Theme.TEXT);
        p.setTypeface(Theme.medium());
        p.setTextSize(Theme.dpf(15) * Theme.textScale);
        p.setLetterSpacing(0.1f);
        c.drawText(Theme.label(label), w / 2, h * 0.80f, p);
        p.setLetterSpacing(0f);
        float bar = down ? w * 0.7f : w * 0.22f;
        p.setColor(Theme.accent);
        c.drawRect(w / 2 - bar / 2, h - Theme.dpf(4), w / 2 + bar / 2, h - Theme.dpf(1), p);
    }

    private void drawCarbon(Canvas c) {
        float w = getWidth(), h = getHeight();
        float d = Math.min(w, h * 0.74f);
        float cx = w / 2, cy = d / 2 + Theme.dpf(4);
        float rad = d / 2 - Theme.dpf(6);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new SweepGradient(cx, cy, new int[] { 0xFF8A8A8A, 0xFF2B2B2B, 0xFFC9C9C9, 0xFF3A3A3A, 0xFF8A8A8A }, null));
        c.drawCircle(cx, cy, rad, p);
        p.setShader(new LinearGradient(0, cy - rad, 0, cy + rad, down ? 0xFF2A2A2A : 0xFF1C1C1C, 0xFF0A0A0A, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rad - Theme.dpf(5), p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(2));
        p.setColor(Theme.alpha(Theme.accent, down ? 1f : 0.55f));
        c.drawCircle(cx, cy, rad - Theme.dpf(9), p);
        float s = rad * 0.85f;
        icon.setColor(down ? Theme.accent : 0xFFEDEDED);
        icon.setBounds((int) (cx - s / 2), (int) (cy - s / 2), (int) (cx + s / 2), (int) (cy + s / 2));
        icon.draw(c);
        p.setStyle(Paint.Style.FILL);
        p.setColor(down ? Theme.accent : Theme.TEXT);
        p.setTypeface(Theme.medium());
        p.setTextSize(Theme.dpf(15) * Theme.textScale);
        p.setLetterSpacing(0.12f);
        c.drawText(Theme.label(label), cx, Math.min(h - Theme.dpf(6), cy + rad + Theme.dpf(26)), p);
        p.setLetterSpacing(0f);
    }

    private void drawSimple(Canvas c) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(down ? Theme.PANEL2 : Theme.PANEL);
        c.drawRoundRect(r, Theme.radius(18), Theme.radius(18), p);
        float s = Theme.dpf(34);
        icon.setColor(down ? Theme.accent : Theme.DIM);
        icon.setBounds((int) Theme.dpf(20), (int) Theme.dpf(20), (int) (Theme.dpf(20) + s), (int) (Theme.dpf(20) + s));
        icon.draw(c);
        p.setColor(Theme.TEXT);
        p.setTypeface(Theme.medium());
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(Theme.dpf(24) * Theme.textScale);
        c.drawText(label, Theme.dpf(20), h - Theme.dpf(22), p);
        p.setTextAlign(Paint.Align.CENTER);
    }
}
