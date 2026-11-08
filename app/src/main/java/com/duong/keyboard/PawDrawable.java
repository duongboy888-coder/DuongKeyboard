package com.duong.keyboard;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Hoạ tiết dấu chân mèo rải đều, xoay nhẹ ngẫu nhiên — dùng làm nền chủ đề "Chân mèo". */
public class PawDrawable extends Drawable {

    private static final float[][] TOES = {
            {-0.48f, -0.30f}, {-0.17f, -0.52f}, {0.17f, -0.52f}, {0.48f, -0.30f}
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float size, gap;

    public PawDrawable(int color, float size, float gap) {
        paint.setColor(color);
        this.size = size;
        this.gap = gap;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        int row = 0;
        for (float y = b.top + gap * 0.35f; y < b.bottom + size; y += gap * 0.78f, row++) {
            float off = (row % 2 == 0) ? 0 : gap / 2f;
            int col = 0;
            for (float x = b.left + off + gap * 0.3f; x < b.right + size; x += gap, col++) {
                float angle = (((row * 7 + col * 13) % 5) - 2) * 14f;
                drawPaw(c, x, y, angle);
            }
        }
    }

    private void drawPaw(Canvas c, float cx, float cy, float angle) {
        float s = size;
        c.save();
        c.rotate(angle, cx, cy);
        // đệm chính
        r.set(cx - s * 0.42f, cy - s * 0.08f, cx + s * 0.42f, cy + s * 0.55f);
        c.drawOval(r, paint);
        // 4 ngón
        float tw = s * 0.24f, th = s * 0.30f;
        for (float[] t : TOES) {
            float tx = cx + t[0] * s, ty = cy + t[1] * s;
            r.set(tx - tw / 2, ty - th / 2, tx + tw / 2, ty + th / 2);
            c.drawOval(r, paint);
        }
        c.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
