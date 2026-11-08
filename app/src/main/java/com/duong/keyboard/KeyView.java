package com.duong.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

/** Phím có thể vẽ icon (căn giữa), hoặc icon + chữ nằm cạnh nhau. */
public class KeyView extends TextView {

    private Drawable icon;
    private int iconSize;
    private String iconLabel;

    public KeyView(Context c) {
        super(c);
    }

    public void setIcon(Drawable d, int sizePx) {
        icon = d;
        iconSize = sizePx;
        setHorizontallyScrolling(false);
        scrollTo(0, 0);
        invalidate();
    }

    /** Chữ vẽ bên phải icon (vd: 🐾 123). */
    public void setIconLabel(String s) {
        iconLabel = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (icon == null) return;
        // TextView 1 dòng + căn giữa sẽ tự cuộn ngang (scrollX rất lớn),
        // canvas bị dịch theo -> phải dịch ngược lại, nếu không icon bị vẽ ra ngoài phím.
        canvas.save();
        canvas.translate(getScrollX(), getScrollY());
        drawIcon(canvas);
        canvas.restore();
    }

    private void drawIcon(Canvas canvas) {
        float cy = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
        float cx = getWidth() / 2f;
        float half = iconSize / 2f;

        if (iconLabel != null) {
            Paint p = getPaint();
            p.setColor(getCurrentTextColor());
            float tw = p.measureText(iconLabel);
            float gap = iconSize * 0.18f;
            float x0 = cx - (iconSize + gap + tw) / 2f;
            icon.setBounds((int) x0, (int) (cy - half), (int) (x0 + iconSize), (int) (cy + half));
            icon.draw(canvas);
            Paint.FontMetrics fm = p.getFontMetrics();
            canvas.drawText(iconLabel, x0 + iconSize + gap, cy - (fm.ascent + fm.descent) / 2f, p);
        } else {
            icon.setBounds((int) (cx - half), (int) (cy - half), (int) (cx + half), (int) (cy + half));
            icon.draw(canvas);
        }
    }

    /** Chọn icon theo chủ đề. imeAction = -1 nếu Enter là xuống dòng. */
    static int iconFor(Theme t, String key, int shift, int imeAction) {
        boolean paw = t.pattern == Theme.PATTERN_PAW;
        switch (key) {
            case "SHIFT":
                if (shift == 2) return paw ? R.drawable.ic_paw_caps : R.drawable.ic_caps;
                if (shift == 1) return paw ? R.drawable.ic_paw : R.drawable.ic_shift_on;
                return paw ? R.drawable.ic_paw_outline : R.drawable.ic_shift;
            case "DEL":
                return R.drawable.ic_backspace;
            case "EMOJI":
                return R.drawable.ic_emoji;
            case "ENTER":
                switch (imeAction) {
                    case EditorInfo.IME_ACTION_SEARCH: return R.drawable.ic_search;
                    case EditorInfo.IME_ACTION_SEND:   return R.drawable.ic_send;
                    case EditorInfo.IME_ACTION_GO:
                    case EditorInfo.IME_ACTION_NEXT:   return R.drawable.ic_next;
                    case EditorInfo.IME_ACTION_DONE:   return R.drawable.ic_done;
                    default: return paw ? R.drawable.ic_paw : R.drawable.ic_enter;
                }
            default:
                return 0;
        }
    }
}
