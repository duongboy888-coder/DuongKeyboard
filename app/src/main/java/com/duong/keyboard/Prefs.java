package com.duong.keyboard;

/** Tên các cài đặt dùng chung giữa bàn phím và màn hình cài đặt. */
final class Prefs {
    static final String FILE = "settings";
    static final String TELEX = "telex";
    static final String MODERN = "modern_tone";
    static final String VIBRATE = "vibrate";
    static final String SOUND = "sound";
    static final String NUMBER_ROW = "number_row";
    static final String THEME = "theme";
    static final String KEY_HEIGHT = "key_height_v2";
    static final String RADIUS = "key_radius";      // -1 = theo chủ đề
    static final String TEXT_SIZE = "key_text_size";
    static final String SHADOW = "key_shadow";
    static final String RECENT = "recent_emoji";
    static final String VIB_MS = "vibrate_ms";

    static final int DEF_VIB = 30;      // ms

    static final int DEF_HEIGHT = 58;   // dp
    static final int DEF_TEXT = 24;     // sp
    static final int NUM_ROW_HEIGHT = 44; // dp — cố định, không đổi theo chiều cao phím

    private Prefs() {}
}
