package com.duong.keyboard;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int PRIMARY = 0xFF3D5AFE;
    private static final int PRIMARY2 = 0xFF7C4DFF;
    private static final int BG = 0xFFF3F5FA;
    private static final int TEXT = 0xFF1F2330;
    private static final int SUB = 0xFF6B7280;
    private static final int OK = 0xFF22C55E;
    private static final int CHIP = 0xFFEEF1FF;
    private static final int DIV = 0xFFEEF0F4;


    private SharedPreferences prefs;
    private TextView dot1, dot2, sub1, sub2, btn1, btn2;
    private LinearLayout themeGrid, previewBox;
    private SeekBar radiusBar;
    private TextView radiusLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
        getWindow().setStatusBarColor(PRIMARY);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout content = vertical();
        content.setPadding(0, 0, 0, dp(28));
        sv.addView(content);

        content.addView(header());

        // ---- Thiết lập ----
        content.addView(sectionTitle("Thiết lập"));
        LinearLayout setup = card();
        setup.addView(stepRow(1, "Bật bàn phím", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        }));
        setup.addView(divider());
        setup.addView(stepRow(2, "Chọn làm mặc định", new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        }));
        content.addView(setup);

        // ---- Tiếng Việt ----
        content.addView(sectionTitle("Gõ tiếng Việt"));
        LinearLayout vi = card();
        vi.addView(switchRow("Gõ Telex", "Giữ phím cách để đổi nhanh Việt ⇄ Anh", Prefs.TELEX, true));
        vi.addView(divider());
        vi.addView(switchRow("Bỏ dấu kiểu mới", "hoà, thuý thay vì hòa, thúy", Prefs.MODERN, false));
        vi.addView(divider());
        vi.addView(telexGuide());
        content.addView(vi);

        // ---- Chủ đề ----
        content.addView(sectionTitle("Chủ đề"));
        LinearLayout look = card();
        TextView pvLabel = text("Xem trước", 16, TEXT, true);
        pvLabel.setPadding(0, dp(10), 0, dp(8));
        look.addView(pvLabel);
        previewBox = vertical();
        look.addView(previewBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView gridLabel = text("Chọn chủ đề", 16, TEXT, true);
        gridLabel.setPadding(0, dp(16), 0, dp(4));
        look.addView(gridLabel);
        themeGrid = vertical();
        look.addView(themeGrid);
        look.addView(spaceView(dp(10)));
        look.addView(divider());
        look.addView(radiusRow());
        look.addView(divider());
        look.addView(sliderRow("Cỡ chữ phím", "sp", Prefs.TEXT_SIZE, Prefs.DEF_TEXT, 16, 32));
        look.addView(divider());
        look.addView(switchRow("Bóng đổ dưới phím", "Phím nổi lên như phím thật", Prefs.SHADOW, true));
        content.addView(look);
        buildThemes();
        buildPreview();

        // ---- Bố cục ----
        content.addView(sectionTitle("Bố cục"));
        LinearLayout layout = card();
        layout.addView(switchRow("Hàng số", "Hàng 1–0 phía trên, có nút emoji ở góc phải", Prefs.NUMBER_ROW, true));
        layout.addView(divider());
        layout.addView(sliderRow("Chiều cao phím chữ", "dp", Prefs.KEY_HEIGHT, Prefs.DEF_HEIGHT, 44, 72));
        content.addView(layout);

        // ---- Phản hồi ----
        content.addView(sectionTitle("Phản hồi khi gõ"));
        LinearLayout fb = card();
        fb.addView(switchRow("Rung", "Rung nhẹ mỗi lần chạm phím", Prefs.VIBRATE, true));
        fb.addView(divider());
        fb.addView(sliderRow("Độ mạnh rung", "ms", Prefs.VIB_MS, Prefs.DEF_VIB, 10, 80));
        TextView testVib = text("Thử rung", 14, 0xFFFFFFFF, true);
        testVib.setGravity(Gravity.CENTER);
        testVib.setPadding(dp(18), dp(10), dp(18), dp(10));
        testVib.setBackground(round(PRIMARY, dp(20)));
        testVib.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.vibrate(MainActivity.this, v, prefs.getInt(Prefs.VIB_MS, Prefs.DEF_VIB));
            }
        });
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.setMargins(0, dp(4), 0, dp(12));
        fb.addView(testVib, tvLp);
        fb.addView(divider());
        fb.addView(switchRow("Âm thanh", "Phát tiếng click khi gõ", Prefs.SOUND, false));
        content.addView(fb);

        // ---- Mẹo ----
        content.addView(sectionTitle("Mẹo nhanh"));
        LinearLayout tips = card();
        tips.addView(tip("␣", "Giữ phím cách: đổi Tiếng Việt ⇄ English"));
        tips.addView(tip("☺", "Nút góc phải hàng số: mở bảng emoji"));
        tips.addView(tip("⌨", "Giữ nút emoji: chuyển sang bàn phím khác"));
        tips.addView(tip(",", "Giữ dấu phẩy: mở trang cài đặt này"));
        tips.addView(tip("⇧", "Chạm đúp Shift: bật Caps Lock"));
        tips.addView(tip("⌫", "Giữ nút xoá: xoá liên tục"));
        content.addView(tips);

        // ---- Gõ thử ----
        content.addView(sectionTitle("Gõ thử"));
        LinearLayout test = card();
        EditText et = new EditText(this);
        et.setHint("Thử: Vieejt Nam ddepj quas!");
        et.setTextSize(16);
        et.setTextColor(TEXT);
        et.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable etBg = round(0xFFF6F7FB, dp(12));
        etBg.setStroke(dp(1), DIV);
        et.setBackground(etBg);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etLp.setMargins(0, dp(8), 0, dp(8));
        test.addView(et, etLp);
        content.addView(test);

        TextView footer = text("DuongKB v1.3 · Bàn phím Telex mã nguồn mở", 12, SUB, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, 0);
        content.addView(footer);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshStatus(); // cập nhật sau khi đóng hộp chọn bàn phím
    }

    // ================= Trạng thái thiết lập =================

    private boolean isKeyboardEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (info.getPackageName().equals(getPackageName())) return true;
        }
        return false;
    }

    private boolean isKeyboardSelected() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return id != null && id.startsWith(getPackageName() + "/");
    }

    private void refreshStatus() {
        if (dot1 == null) return;
        styleStep(dot1, sub1, btn1, 1, isKeyboardEnabled(),
                "Đã bật trong cài đặt", "Bật DuongKB trong danh sách bàn phím", "Bật");
        styleStep(dot2, sub2, btn2, 2, isKeyboardSelected(),
                "Đang là bàn phím chính", "Chọn DuongKB để bắt đầu gõ", "Chọn");
    }

    private void styleStep(TextView dot, TextView sub, TextView btn, int n, boolean done,
                           String okText, String todo, String action) {
        dot.setText(done ? "✓" : String.valueOf(n));
        dot.setTextColor(done ? 0xFFFFFFFF : PRIMARY);
        dot.setBackground(round(done ? OK : CHIP, dp(17)));
        sub.setText(done ? okText : todo);
        sub.setTextColor(done ? OK : SUB);
        btn.setText(done ? "Mở" : action);
        btn.setTextColor(done ? PRIMARY : 0xFFFFFFFF);
        btn.setBackground(round(done ? CHIP : PRIMARY, dp(18)));
    }

    // ================= Các khối giao diện =================

    private View header() {
        LinearLayout h = vertical();
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{PRIMARY, PRIMARY2});
        float r = dp(28);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        h.setBackground(g);
        h.setPadding(dp(24), dp(28), dp(24), dp(30));

        TextView icon = text("⌨️", 40, 0xFFFFFFFF, false);
        h.addView(icon);
        TextView title = text("DuongKB", 30, 0xFFFFFFFF, true);
        title.setPadding(0, dp(6), 0, 0);
        h.addView(title);
        TextView sub = text("Bàn phím tiếng Việt · Telex · Emoji", 15, 0xDDFFFFFF, false);
        h.addView(sub);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        h.setLayoutParams(lp);
        return h;
    }

    private LinearLayout stepRow(int n, String title, View.OnClickListener onClick) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView dot = text("", 15, PRIMARY, true);
        dot.setGravity(Gravity.CENTER);
        row.addView(dot, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout texts = vertical();
        texts.setPadding(dp(12), 0, dp(8), 0);
        texts.addView(text(title, 16, TEXT, true));
        TextView sub = text("", 13, SUB, false);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView btn = text("", 14, 0xFFFFFFFF, true);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(18), dp(8), dp(18), dp(8));
        btn.setOnClickListener(onClick);
        row.addView(btn);

        if (n == 1) { dot1 = dot; sub1 = sub; btn1 = btn; }
        else { dot2 = dot; sub2 = sub; btn2 = btn; }
        return row;
    }

    private View switchRow(String title, String sub, final String key, boolean def) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout texts = vertical();
        texts.addView(text(title, 16, TEXT, true));
        texts.addView(text(sub, 13, SUB, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(key, checked).apply();
                buildPreview();
            }
        });
        row.addView(sw);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sw.toggle(); }
        });
        return row;
    }

    private View telexGuide() {
        LinearLayout box = vertical();
        box.setPadding(0, dp(12), 0, dp(10));
        TextView t = text("Bảng gõ Telex", 16, TEXT, true);
        t.setPadding(0, 0, 0, dp(8));
        box.addView(t);

        String[] chips = {"s → sắc", "f → huyền", "r → hỏi", "x → ngã", "j → nặng", "z → xoá dấu",
                "aa → â", "aw → ă", "ee → ê", "oo → ô", "ow → ơ", "uw → ư", "w → ư", "dd → đ", "ss → s"};
        LinearLayout row = null;
        for (int i = 0; i < chips.length; i++) {
            if (i % 3 == 0) {
                row = horizontal();
                box.addView(row);
            }
            TextView c = text(chips[i], 13, PRIMARY, true);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(4), dp(8), dp(4), dp(8));
            c.setBackground(round(CHIP, dp(10)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(c, lp);
        }
        TextView ex = text("Ví dụ: Vieejt Nam ddepj quas → Việt Nam đẹp quá", 13, SUB, false);
        ex.setPadding(dp(3), dp(10), 0, 0);
        box.addView(ex);
        return box;
    }

    private void buildThemes() {
        themeGrid.removeAllViews();
        String cur = prefs.getString(Prefs.THEME, "dark");
        LinearLayout row = null;
        for (int i = 0; i < Theme.IDS.length; i++) {
            if (i % 4 == 0) {
                row = horizontal();
                row.setPadding(0, dp(8), 0, 0);
                themeGrid.addView(row);
            }
            final String id = Theme.IDS[i];
            boolean selected = id.equals(cur);
            Theme t = Theme.of(id);

            LinearLayout item = vertical();
            item.setGravity(Gravity.CENTER_HORIZONTAL);

            LinearLayout preview = vertical();
            preview.setPadding(dp(6), dp(6), dp(6), dp(6));
            GradientDrawable frame = round(t.bg, dp(12));
            frame.setStroke(selected ? dp(3) : dp(1), selected ? PRIMARY : DIV);
            if (t.pattern == Theme.PATTERN_PAW) {
                preview.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                        frame, new PawDrawable(t.patternColor, dp(8), dp(24))}));
            } else {
                preview.setBackground(frame);
            }
            preview.setClipToOutline(true);
            for (int r = 0; r < 2; r++) {
                LinearLayout kr = horizontal();
                for (int k = 0; k < 3; k++) {
                    View key = new View(this);
                    key.setBackground(round(r == 1 && k == 2 ? t.accent : t.key, dp(Math.min(t.radius, 8) / 2f)));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                    lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                    kr.addView(key, lp);
                }
                preview.addView(kr, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            }
            item.addView(preview, new LinearLayout.LayoutParams(dp(68), dp(52)));

            String name = Theme.LABELS[i] + (t.pattern == Theme.PATTERN_PAW ? " 🐾" : "");
            TextView label = text(name, 12, selected ? PRIMARY : SUB, selected);
            label.setPadding(0, dp(6), 0, 0);
            item.addView(label);

            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    // đổi chủ đề -> bo góc trở về mặc định của chủ đề
                    prefs.edit().putString(Prefs.THEME, id).putInt(Prefs.RADIUS, -1).apply();
                    buildThemes();
                    syncRadius();
                    buildPreview();
                }
            });
            row.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    /** Bàn phím thu nhỏ hiển thị đúng chủ đề, bo góc, cỡ chữ, bóng đang chọn. */
    private void buildPreview() {
        if (previewBox == null) return;
        previewBox.removeAllViews();
        Theme t = Theme.of(prefs.getString(Prefs.THEME, "dark"));
        int r = prefs.getInt(Prefs.RADIUS, -1);
        int radius = r < 0 ? t.radius : r;
        int textSize = prefs.getInt(Prefs.TEXT_SIZE, Prefs.DEF_TEXT);
        boolean shadow = prefs.getBoolean(Prefs.SHADOW, true);

        LinearLayout kb = vertical();
        kb.setPadding(dp(3), dp(5), dp(3), dp(5));
        GradientDrawable frame = round(t.bg, dp(14));
        kb.setBackground(t.pattern == Theme.PATTERN_PAW
                ? new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                        frame, new PawDrawable(t.patternColor, dp(13), dp(50))})
                : frame);
        kb.setClipToOutline(true);

        String[][] rows = {
                {"1", "2", "3", "4", "5", "6", "7", "☺"},
                {"q", "w", "e", "r", "t", "y", "u", "i"},
                {"a", "s", "d", "f", "g", "h", "j", "k"},
                {"SHIFT", "?123", "SPACE", "ENTER"}
        };
        for (int ri = 0; ri < rows.length; ri++) {
            LinearLayout row = horizontal();
            for (String k : rows[ri]) {
                boolean special = k.equals("SHIFT") || k.equals("☺") || k.equals("?123");
                boolean enter = k.equals("ENTER");
                boolean space = k.equals("SPACE");
                boolean paw = t.pattern == Theme.PATTERN_PAW;
                String lbl = space ? (paw ? "🐾 Tiếng Việt" : "Tiếng Việt") : k;
                KeyView key = new KeyView(this);
                key.setText(lbl);
                key.setTextSize(ri == 0 ? 13 : (space || k.equals("?123") ? 11 : textSize * 0.7f));
                key.setTextColor(t.text);
                key.setGravity(Gravity.CENTER);
                key.setSingleLine(true);
                int icon = k.equals("☺") ? R.drawable.ic_emoji : KeyView.iconFor(t, k, 0, -1);
                if (icon != 0) {
                    android.graphics.drawable.Drawable d = getResources().getDrawable(icon, null).mutate();
                    d.setTint(enter ? 0xFFFFFFFF : t.text);
                    key.setText("");
                    key.setIcon(d, dp(ri == 0 ? 15 : 17));
                }
                if (k.equals("?123")) {
                    key.setTextColor(t.accent);
                    key.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    if (paw) {
                        android.graphics.drawable.Drawable pd = getResources().getDrawable(R.drawable.ic_paw, null).mutate();
                        pd.setTint(t.accent);
                        key.setText("");
                        key.setIconLabel("123");
                        key.setIcon(pd, dp(11));
                    }
                }
                GradientDrawable top = round(enter ? t.accent : (special ? t.special : t.key), dp(radius * 0.8f));
                key.setBackground(t.keyBackground(top, dp(radius * 0.8f), shadow, dp(1.2f)));
                if (shadow) key.setPadding(0, 0, 0, dp(1.2f));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT,
                        space ? 4.5f : (enter || k.equals("SHIFT") || k.equals("?123") ? 1.5f : 1f));
                lp.setMargins(dp(2), dp(2.5f), dp(2), dp(2.5f));
                row.addView(key, lp);
            }
            kb.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ri == 0 ? 30 : 38)));
        }
        previewBox.addView(kb, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private View radiusRow() {
        LinearLayout box = vertical();
        box.setPadding(0, dp(12), 0, dp(8));
        radiusLabel = text("", 16, TEXT, true);
        box.addView(radiusLabel);
        box.addView(text("0 = vuông · càng lớn càng tròn", 13, SUB, false));
        radiusBar = new SeekBar(this);
        radiusBar.setMax(20);
        radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                radiusLabel.setText("Bo góc phím: " + p + "dp");
                if (user) {
                    prefs.edit().putInt(Prefs.RADIUS, p).apply();
                    buildPreview();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        box.addView(radiusBar, lp);
        syncRadius();
        return box;
    }

    private void syncRadius() {
        if (radiusBar == null) return;
        Theme t = Theme.of(prefs.getString(Prefs.THEME, "dark"));
        int r = prefs.getInt(Prefs.RADIUS, -1);
        int v = r < 0 ? t.radius : r;
        radiusBar.setProgress(v);
        radiusLabel.setText("Bo góc phím: " + v + "dp");
    }

    private View sliderRow(final String title, final String unit, final String key, int def, final int min, int max) {
        LinearLayout box = vertical();
        box.setPadding(0, dp(12), 0, dp(8));
        final TextView label = text("", 16, TEXT, true);
        box.addView(label);

        int v = prefs.getInt(key, def);
        label.setText(title + ": " + v + unit);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(v - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                int val = p + min;
                label.setText(title + ": " + val + unit);
                if (user) {
                    prefs.edit().putInt(key, val).apply();
                    buildPreview();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        box.addView(sb, lp);
        return box;
    }

    private View tip(String icon, String textStr) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView i = text(icon, 16, PRIMARY, true);
        i.setGravity(Gravity.CENTER);
        i.setBackground(round(CHIP, dp(10)));
        row.addView(i, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView t = text(textStr, 14, TEXT, false);
        t.setPadding(dp(12), 0, 0, 0);
        row.addView(t);
        return row;
    }

    // ================= Tiện ích =================

    private LinearLayout card() {
        LinearLayout c = vertical();
        c.setBackground(round(0xFFFFFFFF, dp(18)));
        c.setElevation(dp(1.5f));
        c.setPadding(dp(16), dp(6), dp(16), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(4), dp(16), dp(8));
        c.setLayoutParams(lp);
        return c;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s.toUpperCase(), 12, SUB, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(24), dp(16), dp(24), dp(4));
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(DIV);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private View spaceView(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, h));
        return v;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
