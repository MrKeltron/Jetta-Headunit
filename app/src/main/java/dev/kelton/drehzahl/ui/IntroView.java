package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Startup animation, one per theme. Plays once when the app starts (or after picking a theme), tap to skip.
 * GLI: hex cells light up, the tach needle sweeps to redline and back, a red stripe crosses the screen, then the text.
 */
public class IntroView extends View {
    private final int style;
    private final String text;
    private final String[] bootLines;
    private final Runnable onDone;
    private final long start = SystemClock.uptimeMillis();
    private final long length;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private long skipAt = -1;
    private boolean done;

    public IntroView(Context c, int style, String text, String[] bootLines, Runnable onDone) {
        super(c);
        this.style = style;
        this.text = text == null || text.trim().isEmpty() ? "GLI" : text.trim();
        this.bootLines = bootLines;
        this.onDone = onDone;
        switch (style) {
            case Theme.GLI: length = 3000; break;
            case Theme.CARBON: length = 2600; break;
            case Theme.CODE: length = 2800; break;
            case Theme.SIMPLE: length = 1500; break;
            default: length = 2000;
        }
        p.setTextAlign(Paint.Align.CENTER);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && skipAt < 0) skipAt = SystemClock.uptimeMillis();
        return true;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float seg(float t, float a, float b) { return clamp((t - a) / (b - a)); }
    private static float ease(float x) { return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2; }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float t = (now - start) / (float) length;
        float fadeOut = seg(t, 0.86f, 1f);
        if (skipAt >= 0) fadeOut = Math.max(fadeOut, clamp((now - skipAt) / 250f));
        if ((t >= 1f || fadeOut >= 1f) && !done) {
            done = true;
            post(onDone);
            return;
        }
        setAlpha(1f - ease(fadeOut));
        c.drawColor(0xFF000000);
        switch (style) {
            case Theme.GLI: gli(c, t); break;
            case Theme.CARBON: carbon(c, t); break;
            case Theme.CODE: code(c, t); break;
            case Theme.SIMPLE: simple(c, t); break;
            default: tach(c, t, getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) * 0.3f, 0f, 0.8f);
        }
        postInvalidateOnAnimation();
    }

    // ---------- GLI ----------
    private void gli(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h * 0.46f;
        // 1. hex cells light up in a ring wave from the middle
        float hr = Theme.dpf(30), hw = (float) (Math.sqrt(3) * hr);
        float wave = seg(t, 0f, 0.45f) * Math.max(w, h) * 0.75f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1.4f));
        for (int row = -1; row * hr * 1.5f < h + hr; row++) {
            for (int col = -1; col * hw < w + hw; col++) {
                float x = col * hw + (row % 2 == 0 ? 0 : hw / 2f), y = row * hr * 1.5f;
                float d = (float) Math.hypot(x - cx, y - cy);
                float k = clamp((wave - d) / Theme.dpf(160));
                if (k <= 0) continue;
                float edge = clamp(1f - Math.abs(wave - d - Theme.dpf(40)) / Theme.dpf(60));
                float fade = 1f - seg(t, 0.45f, 0.8f) * 0.6f;
                int col1 = blend(0xFF2B2E35, 0xFFE3192B, edge);
                p.setColor(Theme.alpha(col1, (0.25f + 0.75f * edge) * k * fade));
                hex(c, x, y, hr - Theme.dpf(3));
            }
        }
        // 2. tach sweep to redline and back
        float sweepIn = ease(seg(t, 0.12f, 0.42f)), sweepOut = ease(seg(t, 0.42f, 0.66f));
        tach(c, t, cx, cy, Math.min(w, h) * 0.27f, seg(t, 0.05f, 0.2f), sweepIn - sweepOut);
        // 3. red stripe crossing the screen
        float s = ease(seg(t, 0.5f, 0.78f));
        if (s > 0f && s < 1f) {
            float y = cy + Math.min(w, h) * 0.36f;
            float x = -w * 0.3f + s * w * 1.6f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(x - w * 0.5f, 0, x, 0, 0x00E3192B, 0xFFE3192B, Shader.TileMode.CLAMP));
            c.drawRect(x - w * 0.5f, y - Theme.dpf(2), x, y + Theme.dpf(2), p);
            p.setShader(new RadialGradient(x, y, Theme.dpf(40), 0xAAFF4A55, 0x00FF4A55, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, Theme.dpf(40), p);
            p.setShader(null);
        }
        if (s >= 1f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFE3192B);
            float y = cy + Math.min(w, h) * 0.36f;
            float half = w * 0.18f * (1f - seg(t, 0.8f, 0.9f) * 0.3f);
            c.drawRect(cx - half, y - Theme.dpf(1.5f), cx + half, y + Theme.dpf(1.5f), p);
        }
        // 4. the text
        float tx = ease(seg(t, 0.62f, 0.8f));
        if (tx > 0f) {
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Theme.medium());
            p.setColor(Theme.alpha(0xFFFFFFFF, tx));
            p.setTextSize(Math.min(w, h) * 0.13f);
            p.setLetterSpacing(0.6f - 0.42f * tx);
            c.drawText(text.toUpperCase(), cx, cy + Math.min(w, h) * 0.06f, p);
            p.setLetterSpacing(0f);
        }
    }

    private void hex(Canvas c, float x, float y, float r) {
        path.reset();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i - 30);
            float px = x + (float) Math.cos(a) * r, py = y + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        c.drawPath(path, p);
    }

    /** Arc gauge with ticks, a red zone and a needle at position pos (0..1). */
    private void tach(Canvas c, float t, float cx, float cy, float rad, float appear, float pos) {
        if (appear <= 0f) return;
        float start = 135f, sweep = 270f;
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Theme.dpf(3));
        p.setColor(Theme.alpha(0xFF3A3D45, appear));
        c.drawArc(r, start, sweep * appear, false, p);
        p.setColor(Theme.alpha(0xFFE3192B, appear));
        p.setStrokeWidth(Theme.dpf(6));
        c.drawArc(r, start + sweep * 0.8f, sweep * 0.2f * appear, false, p);
        p.setStrokeWidth(Theme.dpf(2));
        for (int i = 0; i <= 8; i++) {
            float f = i / 8f;
            if (f > appear) break;
            double a = Math.toRadians(start + sweep * f);
            float in = rad - Theme.dpf(i % 2 == 0 ? 16 : 9);
            p.setColor(Theme.alpha(f >= 0.8f ? 0xFFE3192B : 0xFFE6E6E6, appear));
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                    cx + (float) Math.cos(a) * (rad - Theme.dpf(3)), cy + (float) Math.sin(a) * (rad - Theme.dpf(3)), p);
        }
        double na = Math.toRadians(start + sweep * clamp(pos));
        p.setStrokeWidth(Theme.dpf(4));
        p.setColor(Theme.alpha(pos > 0.78f ? 0xFFFF2B38 : 0xFFFFFFFF, appear));
        c.drawLine(cx, cy, cx + (float) Math.cos(na) * (rad - Theme.dpf(22)), cy + (float) Math.sin(na) * (rad - Theme.dpf(22)), p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.alpha(0xFFE3192B, appear));
        c.drawCircle(cx, cy, Theme.dpf(7), p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    private static int blend(int a, int b, float f) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
    }

    // ---------- Carbon ----------
    private void carbon(Canvas c, float t) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float in = ease(seg(t, 0f, 0.35f));
        float cell = Theme.dpf(8);
        p.setStyle(Paint.Style.FILL);
        for (float y = 0; y < h; y += cell) {
            for (float x = 0; x < w; x += cell) {
                int i = (int) (x / cell), j = (int) (y / cell);
                boolean horiz = ((i / 1) + (j / 1)) % 2 == 0;
                p.setColor(Theme.alpha(horiz ? 0xFF262626 : 0xFF151515, in));
                c.drawRect(x, y, x + cell - 1, y + cell - 1, p);
            }
        }
        float sh = seg(t, 0.25f, 0.7f);
        if (sh > 0f && sh < 1f) {
            float x = -w * 0.4f + sh * w * 1.8f;
            p.setShader(new LinearGradient(x - w * 0.25f, 0, x + w * 0.25f, h * 0.3f,
                    new int[] { 0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF }, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }
        float ring = ease(seg(t, 0.3f, 0.65f));
        float rad = Math.min(w, h) * 0.24f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(4));
        p.setColor(0xFFFF9F1A);
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        c.drawArc(r, -90, 360 * ring, false, p);
        float tx = ease(seg(t, 0.55f, 0.75f));
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Theme.medium());
        p.setColor(Theme.alpha(0xFFEDEDED, tx));
        p.setTextSize(Math.min(w, h) * 0.12f);
        p.setLetterSpacing(0.2f);
        c.drawText(text.toUpperCase(), cx, cy + Math.min(w, h) * 0.04f, p);
        p.setLetterSpacing(0f);
    }

    // ---------- Code ----------
    private void code(Canvas c, float t) {
        float w = getWidth(), h = getHeight();
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Theme.mono());
        float size = Theme.dpf(19);
        p.setTextSize(size);
        float x = Theme.dpf(48), y = Theme.dpf(70);
        int total = 0;
        for (String l : bootLines) total += l.length();
        int shown = (int) (seg(t, 0.02f, 0.72f) * total);
        for (String l : bootLines) {
            if (shown <= 0) break;
            String part = l.substring(0, Math.min(l.length(), shown));
            shown -= l.length();
            p.setColor(part.startsWith("[ OK ]") ? 0xFF39FF8A : 0xFFC9F5D9);
            c.drawText(part, x, y, p);
            y += size * 1.55f;
        }
        if ((SystemClock.uptimeMillis() / 400) % 2 == 0) {
            p.setColor(0xFF39FF8A);
            c.drawRect(x, y - size * 0.8f, x + size * 0.55f, y + size * 0.15f, p);
        }
        float bar = seg(t, 0.05f, 0.8f);
        p.setColor(0xFF173A2E);
        c.drawRect(x, h - Theme.dpf(80), w - x, h - Theme.dpf(70), p);
        p.setColor(0xFF39FF8A);
        c.drawRect(x, h - Theme.dpf(80), x + (w - 2 * x) * bar, h - Theme.dpf(70), p);
        p.setTextAlign(Paint.Align.CENTER);
    }

    // ---------- Simple ----------
    private void simple(Canvas c, float t) {
        float in = ease(seg(t, 0f, 0.4f));
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Theme.light());
        p.setColor(Theme.alpha(0xFFFFFFFF, in));
        p.setTextSize(Math.min(getWidth(), getHeight()) * 0.12f);
        c.drawText(text, getWidth() / 2f, getHeight() / 2f + p.getTextSize() * 0.35f, p);
    }
}
