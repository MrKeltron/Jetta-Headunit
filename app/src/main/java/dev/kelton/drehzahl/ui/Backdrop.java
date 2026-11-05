package dev.kelton.drehzahl.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Full-screen background for each theme: hex pattern (GLI), carbon weave (Carbon), grid (Code), flat otherwise. */
public final class Backdrop extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hex = new Path();
    private Bitmap cache;
    private final int style;

    public Backdrop(int style) { this.style = style; }

    @Override protected void onBoundsChange(Rect b) {
        cache = null;
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        if (cache == null) {
            cache = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);
            paint(new Canvas(cache), b.width(), b.height());
        }
        c.drawBitmap(cache, b.left, b.top, null);
    }

    private void paint(Canvas c, int w, int h) {
        c.drawColor(Theme.BG);
        p.setShader(null);
        switch (style) {
            case Theme.GLI: honeycomb(c, w, h); break;
            case Theme.CARBON: carbon(c, w, h); break;
            case Theme.CODE: grid(c, w, h); break;
            default: break;
        }
    }

    /** Hex outlines that fade in toward the lower right, plus a faint accent glow along the bottom. */
    private void honeycomb(Canvas c, int w, int h) {
        float r = Theme.dpf(26);
        float hw = (float) (Math.sqrt(3) * r);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1.2f));
        for (int row = -1; row * r * 1.5f < h + r; row++) {
            for (int col = -1; col * hw < w + hw; col++) {
                float cx = col * hw + (row % 2 == 0 ? 0 : hw / 2f);
                float cy = row * r * 1.5f;
                float fx = cx / w, fy = cy / h;
                float k = Math.max(0f, Math.min(1f, (fx * 0.55f + fy * 0.75f) - 0.45f));
                if (k <= 0.02f) continue;
                p.setColor(Theme.alpha(Theme.LINE, 0.85f * k));
                hex.reset();
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(60 * i - 30);
                    float x = cx + (float) Math.cos(a) * (r - Theme.dpf(2.5f));
                    float y = cy + (float) Math.sin(a) * (r - Theme.dpf(2.5f));
                    if (i == 0) hex.moveTo(x, y); else hex.lineTo(x, y);
                }
                hex.close();
                c.drawPath(hex, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(w * 0.5f, h * 1.15f, w * 0.6f,
                Theme.alpha(Theme.accent, Theme.night ? 0.16f : 0.08f), 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    /** 2 x 2 twill weave tile, repeated, with a soft diagonal sheen on top. */
    private void carbon(Canvas c, int w, int h) {
        int cell = Math.max(6, Theme.dp(7));
        Bitmap tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888);
        Canvas tc = new Canvas(tile);
        int base = Theme.night ? 0xFF101010 : 0xFFBDBDBD;
        int lit = Theme.night ? 0xFF2A2A2A : 0xFFE0E0E0;
        int dark = Theme.night ? 0xFF070707 : 0xFF9A9A9A;
        tc.drawColor(base);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                boolean horiz = (i + j) % 2 == 0;
                float l = i * cell + 0.5f, t = j * cell + 0.5f;
                tp.setShader(horiz
                        ? new LinearGradient(l, t, l, t + cell, lit, dark, Shader.TileMode.CLAMP)
                        : new LinearGradient(l, t, l + cell, t, lit, dark, Shader.TileMode.CLAMP));
                tc.drawRect(l, t, l + cell - 1, t + cell - 1, tp);
            }
        }
        p.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, w, h,
                new int[] { 0x00FFFFFF, Theme.night ? 0x14FFFFFF : 0x22FFFFFF, 0x00FFFFFF, 0x66000000 },
                new float[] { 0.2f, 0.45f, 0.7f, 1f }, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    /** Engineering grid: thin lines every 32 dp, a brighter line every fourth. */
    private void grid(Canvas c, int w, int h) {
        float step = Theme.dpf(32);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1));
        int i = 0;
        for (float x = 0; x < w; x += step, i++) {
            p.setColor(Theme.alpha(Theme.LINE, i % 4 == 0 ? 0.55f : 0.22f));
            c.drawLine(x, 0, x, h, p);
        }
        i = 0;
        for (float y = 0; y < h; y += step, i++) {
            p.setColor(Theme.alpha(Theme.LINE, i % 4 == 0 ? 0.55f : 0.22f));
            c.drawLine(0, y, w, y, p);
        }
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
