package com.duong.keyboard;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

/** Các chủ đề màu của bàn phím. */
final class Theme {

    static final String[] IDS = {"dark", "light", "amoled", "ocean", "sakura", "mint", "galaxy", "paw"};
    static final String[] LABELS = {"Tối", "Sáng", "AMOLED", "Đại dương", "Anh đào", "Bạc hà", "Thiên hà", "Chân mèo"};

    static final int PATTERN_NONE = 0, PATTERN_PAW = 1;

    final String id;
    final int bg, key, special, pressed, accent, text, shadow, radius, pattern, patternColor;

    private Theme(String id, int bg, int key, int special, int pressed, int accent, int text,
                  int shadow, int radius, int pattern, int patternColor) {
        this.id = id; this.bg = bg; this.key = key; this.special = special; this.pressed = pressed;
        this.accent = accent; this.text = text; this.shadow = shadow; this.radius = radius;
        this.pattern = pattern; this.patternColor = patternColor;
    }

    private static Theme t(String id, int bg, int key, int special, int pressed, int accent, int text,
                           int shadow, int radius) {
        return new Theme(id, bg, key, special, pressed, accent, text, shadow, radius, PATTERN_NONE, 0);
    }

    static Theme of(String id) {
        switch (id) {
            case "light":  return t(id, 0xFFE8EAED, 0xFFFFFFFF, 0xFFD3D7DE, 0xFFBFC4CC, 0xFF1A73E8, 0xFF202124, 0x2E000000, 7);
            case "amoled": return t(id, 0xFF000000, 0xFF1C1C1E, 0xFF111113, 0xFF3A3A3C, 0xFF0A84FF, 0xFFFFFFFF, 0x00000000, 8);
            case "ocean":  return t(id, 0xFF0F1B2D, 0xFF1D3150, 0xFF15253D, 0xFF2C4870, 0xFF29B6F6, 0xFFE3F2FD, 0x66000000, 8);
            case "sakura": return t(id, 0xFFFCE4EC, 0xFFFFFFFF, 0xFFF8BBD0, 0xFFF48FB1, 0xFFE91E63, 0xFF4A2C35, 0x26C2185B, 10);
            case "mint":   return t(id, 0xFFE6F4EF, 0xFFFFFFFF, 0xFFC5E6DA, 0xFFA8D5C4, 0xFF10A37F, 0xFF1F3B33, 0x2610A37F, 9);
            case "galaxy": return t(id, 0xFF1A1033, 0xFF2D1F55, 0xFF231845, 0xFF453377, 0xFFB388FF, 0xFFEDE7F6, 0x66000000, 9);
            case "paw":
                return new Theme(id, 0xFFFFF1E6, 0xEEFFFFFF, 0xFFFFD6E0, 0xFFFFB3C6, 0xFFFF7A9C, 0xFF5B3A29,
                        0x33C77B8C, 16, PATTERN_PAW, 0x2EE8879E);
            default:       return t("dark", 0xFF1E1F24, 0xFF3A3C43, 0xFF2A2C32, 0xFF5A5D66, 0xFF3D7BFD, 0xFFFFFFFF, 0x66000000, 7);
        }
    }

    /** Nền bàn phím (có hoạ tiết nếu chủ đề có). pawSize/pawGap tính bằng px. */
    Drawable background(float pawSize, float pawGap) {
        if (pattern == PATTERN_PAW) {
            return new LayerDrawable(new Drawable[]{
                    new ColorDrawable(bg), new PawDrawable(patternColor, pawSize, pawGap)});
        }
        return new ColorDrawable(bg);
    }

    /** Bọc nền phím với bóng đổ phía dưới (nếu bật). */
    Drawable keyBackground(android.graphics.drawable.GradientDrawable top, float radius,
                           boolean shadowOn, int shadowPx) {
        if (!shadowOn || (shadow >>> 24) == 0) return top;
        android.graphics.drawable.GradientDrawable sh = new android.graphics.drawable.GradientDrawable();
        sh.setCornerRadius(radius);
        sh.setColor(shadow);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{sh, top});
        ld.setLayerInset(1, 0, 0, 0, shadowPx);
        return ld;
    }
}
