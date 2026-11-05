package dev.kelton.drehzahl.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Original line icons drawn on a 24 x 24 grid. Add a new one by adding a case to draw(). */
public final class Icons extends Drawable {
    public static final int HOME = 1, MEDIA = 2, CARPLAY = 3, CAR = 4, SOUND = 5, APPS = 6, SETTINGS = 7,
            BACK = 8, PLAY = 9, PAUSE = 10, PREV = 11, NEXT = 12, GAUGE = 13, GMETER = 14, TIMER = 15,
            TRIP = 16, SERVICE = 17, PROFILE = 18, LINK = 19, DISPLAY = 20, CLOCK = 21, UNITS = 22,
            BLUETOOTH = 23, WIFI = 24, INFO = 25, DOC = 26, STAR = 27, RESET = 28, EQ = 29, TONE = 30,
            BALANCE = 31, SPARK = 32, VOLUME = 33, CHIP = 34, GRID = 35, BATTERY = 36;

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    public Icons(int kind, int color) {
        this.kind = kind;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColor(int c) { p.setColor(c); invalidateSelf(); }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        c.scale(s, s);
        p.setStrokeWidth(1.8f);
        p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (kind) {
            case HOME:
                path.moveTo(3, 11); path.lineTo(12, 3.5f); path.lineTo(21, 11);
                path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20); path.lineTo(18.5f, 20); path.lineTo(18.5f, 9.5f);
                path.moveTo(10, 20); path.lineTo(10, 14); path.lineTo(14, 14); path.lineTo(14, 20);
                break;
            case MEDIA:
                path.moveTo(9, 18); path.lineTo(9, 5); path.lineTo(20, 3); path.lineTo(20, 16);
                c.drawCircle(6.5f, 18, 2.5f, p); c.drawCircle(17.5f, 16, 2.5f, p);
                break;
            case CARPLAY:
                rr(c, 3, 4, 21, 17, 3);
                rr(c, 9, 7, 15, 14, 1.5f);
                path.moveTo(8, 20.5f); path.lineTo(16, 20.5f); path.moveTo(12, 17); path.lineTo(12, 20.5f);
                break;
            case CAR:
                path.moveTo(3, 15); path.lineTo(3, 12); path.lineTo(6, 7.5f); path.lineTo(17, 7.5f); path.lineTo(21, 12); path.lineTo(21, 15);
                path.lineTo(3, 15); path.moveTo(4, 12); path.lineTo(20, 12);
                c.drawCircle(7, 17, 2, p); c.drawCircle(17, 17, 2, p);
                break;
            case SOUND: case EQ:
                for (int i = 0; i < 4; i++) {
                    float x = 5 + i * 4.7f; float k = new float[] { 9, 15, 7, 12 }[i];
                    path.moveTo(x, 4); path.lineTo(x, 20);
                    c.drawLine(x - 2, k, x + 2, k, p);
                }
                break;
            case APPS: case GRID:
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) rr(c, 3.5f + i * 6.5f, 3.5f + j * 6.5f, 7.5f + i * 6.5f, 7.5f + j * 6.5f, 1.2f);
                break;
            case SETTINGS:
                c.drawCircle(12, 12, 3, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine(12 + (float) Math.cos(a) * 6.5f, 12 + (float) Math.sin(a) * 6.5f,
                            12 + (float) Math.cos(a) * 9f, 12 + (float) Math.sin(a) * 9f, p);
                }
                c.drawCircle(12, 12, 6.5f, p);
                break;
            case BACK:
                path.moveTo(15, 4.5f); path.lineTo(7.5f, 12); path.lineTo(15, 19.5f);
                break;
            case PLAY:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(7, 4); path.lineTo(20, 12); path.lineTo(7, 20); path.close();
                break;
            case PAUSE:
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(6, 4, 10, 20), 1, 1, p); c.drawRoundRect(new RectF(14, 4, 18, 20), 1, 1, p);
                break;
            case PREV:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(19, 5); path.lineTo(9, 12); path.lineTo(19, 19); path.close();
                c.drawRect(5, 5, 7.5f, 19, p);
                break;
            case NEXT:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(5, 5); path.lineTo(15, 12); path.lineTo(5, 19); path.close();
                c.drawRect(16.5f, 5, 19, 19, p);
                break;
            case GAUGE:
                r.set(3, 4, 21, 22); c.drawArc(r, 150, 240, false, p);
                path.moveTo(12, 13); path.lineTo(16.5f, 8.5f);
                c.drawCircle(12, 13, 1.2f, p);
                break;
            case GMETER:
                c.drawCircle(12, 12, 9, p); c.drawCircle(12, 12, 4.5f, p);
                path.moveTo(12, 2.5f); path.lineTo(12, 21.5f); path.moveTo(2.5f, 12); path.lineTo(21.5f, 12);
                p.setStyle(Paint.Style.FILL); c.drawCircle(15.5f, 9.5f, 1.8f, p); p.setStyle(Paint.Style.STROKE);
                break;
            case TIMER:
                c.drawCircle(12, 13.5f, 7.5f, p);
                path.moveTo(12, 13.5f); path.lineTo(12, 9); path.moveTo(9.5f, 3); path.lineTo(14.5f, 3);
                path.moveTo(18.5f, 6.5f); path.lineTo(20, 5);
                break;
            case TRIP:
                c.drawCircle(6, 18, 2, p); c.drawCircle(18, 6, 2, p);
                path.moveTo(8, 18); path.lineTo(14, 18); path.cubicTo(18, 18, 18, 12, 14, 12); path.lineTo(10, 12);
                path.cubicTo(6, 12, 6, 6, 10, 6); path.lineTo(16, 6);
                break;
            case SERVICE:
                path.moveTo(14.5f, 4); path.cubicTo(11.5f, 3.5f, 9.5f, 6.5f, 10.8f, 9.3f); path.lineTo(4, 16.2f);
                path.lineTo(7.8f, 20); path.lineTo(14.7f, 13.2f); path.cubicTo(17.5f, 14.5f, 20.5f, 12.5f, 20, 9.5f);
                path.lineTo(17.2f, 12.2f); path.lineTo(14.5f, 11.5f); path.lineTo(13.8f, 8.8f); path.close();
                break;
            case PROFILE:
                path.moveTo(4, 7); path.lineTo(20, 7); path.moveTo(4, 12); path.lineTo(20, 12); path.moveTo(4, 17); path.lineTo(20, 17);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(9, 7, 2.3f, p); c.drawCircle(15, 12, 2.3f, p); c.drawCircle(7, 17, 2.3f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case LINK:
                rr(c, 3, 9, 12, 15, 3); rr(c, 12, 9, 21, 15, 3);
                break;
            case DISPLAY:
                c.drawCircle(12, 12, 4, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine(12 + (float) Math.cos(a) * 6.5f, 12 + (float) Math.sin(a) * 6.5f,
                            12 + (float) Math.cos(a) * 8.5f, 12 + (float) Math.sin(a) * 8.5f, p);
                }
                break;
            case CLOCK:
                c.drawCircle(12, 12, 9, p);
                path.moveTo(12, 7); path.lineTo(12, 12); path.lineTo(15.5f, 14);
                break;
            case UNITS:
                rr(c, 2.5f, 8, 21.5f, 16, 1.5f);
                for (int i = 0; i < 5; i++) { float x = 6 + i * 3; c.drawLine(x, 8, x, i % 2 == 0 ? 12.5f : 10.5f, p); }
                break;
            case BLUETOOTH:
                path.moveTo(7, 7.5f); path.lineTo(16.5f, 16); path.lineTo(12, 20); path.lineTo(12, 4); path.lineTo(16.5f, 8); path.lineTo(7, 16.5f);
                break;
            case WIFI:
                r.set(2.5f, 5, 21.5f, 24); c.drawArc(r, 225, 90, false, p);
                r.set(6, 9, 18, 21); c.drawArc(r, 225, 90, false, p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(12, 18, 1.6f, p); p.setStyle(Paint.Style.STROKE);
                break;
            case INFO:
                c.drawCircle(12, 12, 9, p);
                path.moveTo(12, 11); path.lineTo(12, 17);
                p.setStyle(Paint.Style.FILL); c.drawCircle(12, 7.7f, 1.2f, p); p.setStyle(Paint.Style.STROKE);
                break;
            case DOC:
                path.moveTo(6, 3); path.lineTo(14, 3); path.lineTo(19, 8); path.lineTo(19, 21); path.lineTo(6, 21); path.close();
                path.moveTo(14, 3); path.lineTo(14, 8); path.lineTo(19, 8);
                path.moveTo(9, 12.5f); path.lineTo(16, 12.5f); path.moveTo(9, 16.5f); path.lineTo(16, 16.5f);
                break;
            case STAR:
                for (int i = 0; i < 5; i++) {
                    double a = -Math.PI / 2 + i * 4 * Math.PI / 5;
                    float x = 12 + (float) Math.cos(a) * 9, y = 12.8f + (float) Math.sin(a) * 9;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
                break;
            case RESET:
                r.set(4, 4, 20, 20); c.drawArc(r, -60, 300, false, p);
                path.moveTo(18.5f, 3.5f); path.lineTo(18.5f, 8.5f); path.lineTo(13.5f, 8.5f);
                break;
            case TONE:
                path.moveTo(3, 12); path.cubicTo(6, 4, 9, 4, 12, 12); path.cubicTo(15, 20, 18, 20, 21, 12);
                break;
            case BALANCE:
                rr(c, 6, 3, 18, 21, 4);
                path.moveTo(12, 6); path.lineTo(12, 18); path.moveTo(8.5f, 12); path.lineTo(15.5f, 12);
                p.setStyle(Paint.Style.FILL); c.drawCircle(13.5f, 10, 1.8f, p); p.setStyle(Paint.Style.STROKE);
                break;
            case SPARK:
                path.moveTo(12, 3); path.lineTo(13.8f, 10.2f); path.lineTo(21, 12); path.lineTo(13.8f, 13.8f);
                path.lineTo(12, 21); path.lineTo(10.2f, 13.8f); path.lineTo(3, 12); path.lineTo(10.2f, 10.2f); path.close();
                break;
            case VOLUME:
                path.moveTo(4, 9.5f); path.lineTo(8, 9.5f); path.lineTo(13, 5); path.lineTo(13, 19); path.lineTo(8, 14.5f); path.lineTo(4, 14.5f); path.close();
                r.set(11, 8, 19, 16); c.drawArc(r, -50, 100, false, p);
                r.set(10, 5, 22, 19); c.drawArc(r, -50, 100, false, p);
                break;
            case CHIP:
                rr(c, 6, 6, 18, 18, 2); rr(c, 9.5f, 9.5f, 14.5f, 14.5f, 1);
                for (int i = 0; i < 3; i++) {
                    float k = 9 + i * 3;
                    c.drawLine(k, 3, k, 6, p); c.drawLine(k, 18, k, 21, p); c.drawLine(3, k, 6, k, p); c.drawLine(18, k, 21, k, p);
                }
                break;
            case BATTERY:
                rr(c, 2.5f, 7.5f, 19.5f, 16.5f, 2);
                path.moveTo(21.5f, 10.5f); path.lineTo(21.5f, 13.5f);
                break;
            default:
                c.drawCircle(12, 12, 8, p);
        }
        c.drawPath(path, p);
        c.restore();
    }

    private void rr(Canvas c, float l, float t, float rt, float b, float rad) {
        r.set(l, t, rt, b);
        c.drawRoundRect(r, rad, rad, p);
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
