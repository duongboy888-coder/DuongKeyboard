package com.duong.keyboard;

import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KeyboardService extends InputMethodService {

    // ================= Giao diện (theme) =================
    static final class Theme {
        final int bg, key, special, pressed, accent, text;

        Theme(int bg, int key, int special, int pressed, int accent, int text) {
            this.bg = bg; this.key = key; this.special = special;
            this.pressed = pressed; this.accent = accent; this.text = text;
        }

        static Theme of(String name) {
            switch (name) {
                case "light":  return new Theme(0xFFE8EAED, 0xFFFFFFFF, 0xFFD3D7DE, 0xFFBFC4CC, 0xFF1A73E8, 0xFF202124);
                case "ocean":  return new Theme(0xFF0F1B2D, 0xFF1D3150, 0xFF15253D, 0xFF2C4870, 0xFF29B6F6, 0xFFE3F2FD);
                case "sakura": return new Theme(0xFFFCE4EC, 0xFFFFFFFF, 0xFFF8BBD0, 0xFFF48FB1, 0xFFE91E63, 0xFF4A2C35);
                default:       return new Theme(0xFF1E1F24, 0xFF3A3C43, 0xFF2A2C32, 0xFF5A5D66, 0xFF3D7BFD, 0xFFFFFFFF);
            }
        }
    }

    private static final int MODE_LETTERS = 0, MODE_SYM1 = 1, MODE_SYM2 = 2, MODE_EMOJI = 3;

    // ================= Bố cục phím =================
    private static final String[] NUM_ROW = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"},
            {"?123", "EMOJI", ",", "SPACE", ".", "ENTER"}
    };
    private static final String[][] SYM1 = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "_", "&", "-", "+", "(", ")", "/"},
            {"=\\<", "*", "\"", "'", ":", ";", "!", "?", "DEL"},
            {"ABC", "EMOJI", ",", "SPACE", ".", "ENTER"}
    };
    private static final String[][] SYM2 = {
            {"~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"},
            {"€", "£", "¥", "^", "°", "=", "[", "]", "<", ">"},
            {"?123", "\\", "©", "®", "™", "…", "¶", "§", "DEL"},
            {"ABC", "EMOJI", ",", "SPACE", ".", "ENTER"}
    };

    private SharedPreferences prefs;
    private Theme theme;
    private final Telex telex = new Telex();
    private boolean telexOn, telexAllowed, vibrateOn, soundOn, numberRow;
    private int keyHeightDp;

    private LinearLayout root;
    private TextView spaceKey;
    private int mode = MODE_LETTERS;
    private int shift = 0;            // 0 tắt · 1 hoa 1 lần · 2 caps lock
    private long lastShiftTap = 0;
    private int emojiCategory = 1;

    private Vibrator vibrator;
    private AudioManager audio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longPressFired = false;
    private Runnable pendingLongPress;

    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            deleteOne();
            handler.postDelayed(this, 50);
        }
    };

    // ================= Vòng đời =================

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        loadPrefs();
    }

    private void loadPrefs() {
        telexOn = prefs.getBoolean(Prefs.TELEX, true);
        telex.setModernStyle(prefs.getBoolean(Prefs.MODERN, false));
        vibrateOn = prefs.getBoolean(Prefs.VIBRATE, true);
        soundOn = prefs.getBoolean(Prefs.SOUND, false);
        numberRow = prefs.getBoolean(Prefs.NUMBER_ROW, true);
        keyHeightDp = prefs.getInt(Prefs.KEY_HEIGHT, 52);
        theme = Theme.of(prefs.getString(Prefs.THEME, "dark"));
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        render();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        loadPrefs();
        telex.reset();

        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        telexAllowed = cls == InputType.TYPE_CLASS_TEXT
                && var != InputType.TYPE_TEXT_VARIATION_PASSWORD
                && var != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                && var != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                && var != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                && var != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                && var != InputType.TYPE_TEXT_VARIATION_URI;

        boolean numeric = cls == InputType.TYPE_CLASS_NUMBER
                || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME;
        mode = numeric ? MODE_SYM1 : MODE_LETTERS;
        shift = 0;
        computeAutoCaps();
        render();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        stopTimers();
        finishComposing(getCurrentInputConnection());
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        // Người dùng chạm sang chỗ khác -> chốt từ đang gõ
        if (!telex.isEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            telex.reset();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
        }
        if (computeAutoCaps()) render();
    }

    private boolean useTelex() {
        return telexOn && telexAllowed;
    }

    // ================= Vẽ bàn phím =================

    private int numRowHeight() { return (int) (dp(keyHeightDp) * 0.82f); }

    private int keyboardHeight() {
        return dp(keyHeightDp) * 4 + (numberRow ? numRowHeight() : 0);
    }

    private void render() {
        if (root == null) return;
        root.removeAllViews();
        spaceKey = null;
        root.setBackgroundColor(theme.bg);
        root.setPadding(dp(3), dp(5), dp(3), dp(6));

        if (mode == MODE_EMOJI) {
            buildEmojiPanel();
            return;
        }

        List<String[]> rows = new ArrayList<>();
        if (mode == MODE_LETTERS) {
            if (numberRow) rows.add(NUM_ROW);
            rows.addAll(Arrays.asList(LETTERS));
        } else {
            rows.addAll(Arrays.asList(mode == MODE_SYM1 ? SYM1 : SYM2));
        }

        int kh = dp(keyHeightDp);
        int total = keyboardHeight();
        for (String[] keys : rows) {
            int h;
            if (mode == MODE_LETTERS) h = (keys == NUM_ROW) ? numRowHeight() : kh;
            else h = total / rows.size();   // giữ chiều cao bằng trang chữ

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            boolean indent = keys == LETTERS[1];
            if (indent) row.addView(spacer(0.5f));
            for (String k : keys) row.addView(makeKey(k, keys == NUM_ROW));
            if (indent) row.addView(spacer(0.5f));

            root.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        }
    }

    private View spacer(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, weight));
        return v;
    }

    private View makeKey(final String key) {
        return makeKey(key, false);
    }

    private View makeKey(final String key, boolean small) {
        final TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText(label(key));
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);

        float size = 22;
        if (small) size = 17;
        else if (key.equals("SPACE") || key.equals("?123") || key.equals("ABC") || key.equals("=\\<")) size = 14;
        else if (key.equals("DEL") || key.equals("SHIFT") || key.equals("ENTER") || key.equals("EMOJI")) size = 20;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);

        int color = isSpecial(key) ? theme.special : theme.key;
        int textColor = theme.text;
        if (key.equals("SHIFT") && shift == 1) color = theme.pressed;
        if ((key.equals("SHIFT") && shift == 2) || key.equals("ENTER")) {
            color = theme.accent;
            textColor = 0xFFFFFFFF;
        }
        tv.setTextColor(textColor);
        if (key.equals("SPACE")) {
            spaceKey = tv;
            tv.setAlpha(0.75f);
        }

        final int normalColor = color;
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(7));
        bg.setColor(normalColor);
        tv.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, weight(key));
        int m = dp(3);
        lp.setMargins(m, m, m, m);
        tv.setLayoutParams(lp);

        tv.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        bg.setColor(theme.pressed);
                        feedback(key);
                        longPressFired = false;
                        if (key.equals("DEL")) {
                            deleteOne();
                            handler.postDelayed(repeatDelete, 400);
                        } else if (key.equals("SPACE")) {
                            schedule(new Runnable() { public void run() { toggleLanguage(); } });
                        } else if (key.equals(",")) {
                            schedule(new Runnable() { public void run() { showPicker(); } });
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        bg.setColor(normalColor);
                        stopTimers();
                        if (!key.equals("DEL") && !longPressFired) onKey(key);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        bg.setColor(normalColor);
                        stopTimers();
                        return true;
                }
                return true;
            }
        });
        return tv;
    }

    private boolean isSpecial(String k) {
        return k.equals("SHIFT") || k.equals("DEL") || k.equals("ENTER") || k.equals("EMOJI")
                || k.equals("?123") || k.equals("ABC") || k.equals("=\\<");
    }

    private float weight(String k) {
        if (k.equals("SPACE")) return 4f;
        if (k.equals("EMOJI")) return 1f;
        if (isSpecial(k)) return 1.5f;
        return 1f;
    }

    private String label(String k) {
        switch (k) {
            case "SHIFT": return shift == 2 ? "⇪" : "⇧";
            case "DEL":   return "⌫";
            case "EMOJI": return "☺";
            case "SPACE": return useTelex() ? "Tiếng Việt" : "English";
            case "ENTER": return enterLabel();
            default:
                return (mode == MODE_LETTERS && shift > 0) ? k.toUpperCase() : k;
        }
    }

    private String enterLabel() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵";
        switch (ei.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH: return "⌕";
            case EditorInfo.IME_ACTION_SEND:   return "➤";
            case EditorInfo.IME_ACTION_GO:     return "→";
            case EditorInfo.IME_ACTION_NEXT:   return "⇥";
            case EditorInfo.IME_ACTION_DONE:   return "✓";
            default: return "↵";
        }
    }

    // ================= Bảng emoji =================

    private void buildEmojiPanel() {
        int kh = dp(keyHeightDp);
        int total = Math.max(keyboardHeight(), dp(240));
        int titleH = dp(24);
        final String[] items = emojiCategory == 0 ? recents() : EmojiData.get(emojiCategory);

        TextView title = new TextView(this);
        title.setText(EmojiData.NAMES[emojiCategory]);
        title.setTextColor(theme.text);
        title.setAlpha(0.65f);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setPadding(dp(10), dp(2), 0, 0);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, titleH));

        int gridH = total - kh - titleH;
        if (items.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có emoji gần đây");
            empty.setTextColor(theme.text);
            empty.setAlpha(0.6f);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, gridH));
        } else {
            GridView grid = new GridView(this);
            grid.setNumColumns(8);
            grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            grid.setSelector(new ColorDrawable(0x00000000));
            grid.setVerticalScrollBarEnabled(false);
            grid.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return items.length; }
                @Override public Object getItem(int p) { return items[p]; }
                @Override public long getItemId(int p) { return p; }
                @Override public View getView(int p, View cv, ViewGroup parent) {
                    TextView tv = cv instanceof TextView ? (TextView) cv : new TextView(KeyboardService.this);
                    tv.setGravity(Gravity.CENTER);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
                    tv.setLayoutParams(new AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
                    tv.setText(items[p]);
                    return tv;
                }
            });
            grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                    feedback("EMOJI");
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.commitText(items[pos], 1);
                    addRecent(items[pos]);
                }
            });
            root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, gridH));
        }

        // Thanh dưới: ABC · nhóm emoji · xoá
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(makeKey("ABC"));

        final HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < EmojiData.ICONS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(EmojiData.ICONS[i]);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            t.setGravity(Gravity.CENTER);
            if (i == emojiCategory) {
                GradientDrawable d = new GradientDrawable();
                d.setCornerRadius(dp(10));
                d.setColor(theme.pressed);
                t.setBackground(d);
            } else {
                t.setAlpha(0.6f);
            }
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    feedback("");
                    emojiCategory = idx;
                    render();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), LinearLayout.LayoutParams.MATCH_PARENT);
            lp.setMargins(dp(2), dp(5), dp(2), dp(5));
            tabs.addView(t, lp);
        }
        hs.addView(tabs);
        bar.addView(hs, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 7f));
        bar.addView(makeKey("DEL"));
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, kh));

        final int sel = emojiCategory;
        hs.post(new Runnable() {
            @Override public void run() { hs.scrollTo(Math.max(0, dp(46) * sel - dp(120)), 0); }
        });
    }

    private String[] recents() {
        String s = prefs.getString(Prefs.RECENT, "").trim();
        return s.isEmpty() ? new String[0] : s.split(" ");
    }

    private void addRecent(String emoji) {
        List<String> list = new ArrayList<>(Arrays.asList(recents()));
        list.remove(emoji);
        list.add(0, emoji);
        while (list.size() > 40) list.remove(list.size() - 1);
        prefs.edit().putString(Prefs.RECENT, TextUtils.join(" ", list)).apply();
    }

    // ================= Xử lý phím =================

    private void onKey(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (key) {
            case "SHIFT": {
                long now = System.currentTimeMillis();
                if (shift == 0) shift = 1;
                else if (shift == 1) shift = (now - lastShiftTap < 350) ? 2 : 0;
                else shift = 0;
                lastShiftTap = now;
                render();
                break;
            }
            case "?123":
                finishComposing(ic);
                mode = MODE_SYM1;
                render();
                break;
            case "=\\<":
                mode = MODE_SYM2;
                render();
                break;
            case "ABC":
                mode = MODE_LETTERS;
                computeAutoCaps();
                render();
                break;
            case "EMOJI":
                finishComposing(ic);
                mode = MODE_EMOJI;
                emojiCategory = recents().length > 0 ? 0 : 1;
                render();
                break;
            case "SPACE":
                finishComposing(ic);
                ic.commitText(" ", 1);
                break;
            case "ENTER":
                finishComposing(ic);
                handleEnter(ic);
                break;
            default: {
                String text = (mode == MODE_LETTERS && shift > 0) ? key.toUpperCase() : key;
                char ch = text.charAt(0);
                if (mode == MODE_LETTERS && useTelex() && Character.isLetter(ch)) {
                    telex.type(ch);
                    ic.setComposingText(telex.getText(), 1);
                } else {
                    finishComposing(ic);
                    ic.commitText(text, 1);
                }
                if (shift == 1) {
                    shift = 0;
                    render();
                }
            }
        }
    }

    private void finishComposing(InputConnection ic) {
        if (!telex.isEmpty()) {
            if (ic != null) ic.finishComposingText();
            telex.reset();
        }
    }

    private void deleteOne() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!telex.isEmpty()) {
            telex.backspace();
            if (telex.isEmpty()) ic.commitText("", 1);
            else ic.setComposingText(telex.getText(), 1);
            return;
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void handleEnter(InputConnection ic) {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noAction = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            boolean multiLine = (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
            if (!noAction && !multiLine
                    && action != EditorInfo.IME_ACTION_NONE
                    && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action);
                return;
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    private void toggleLanguage() {
        longPressFired = true;
        finishComposing(getCurrentInputConnection());
        telexOn = !telexOn;
        prefs.edit().putBoolean(Prefs.TELEX, telexOn).apply();
        if (spaceKey != null) spaceKey.setText(label("SPACE"));
        feedback("");
    }

    private void showPicker() {
        longPressFired = true;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    private void schedule(Runnable r) {
        pendingLongPress = r;
        handler.postDelayed(r, 500);
    }

    private void stopTimers() {
        handler.removeCallbacks(repeatDelete);
        if (pendingLongPress != null) handler.removeCallbacks(pendingLongPress);
        pendingLongPress = null;
    }

    /** Tự viết hoa đầu câu. Trả về true nếu trạng thái shift thay đổi. */
    private boolean computeAutoCaps() {
        if (shift == 2 || mode != MODE_LETTERS) return false;
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        int caps = 0;
        if (ei != null && ic != null && ei.inputType != InputType.TYPE_NULL) {
            caps = ic.getCursorCapsMode(ei.inputType);
        }
        int newShift = caps != 0 ? 1 : 0;
        if (newShift != shift) {
            shift = newShift;
            return true;
        }
        return false;
    }

    // ================= Rung & âm thanh =================

    @SuppressWarnings("deprecation")
    private void feedback(String key) {
        if (vibrateOn && vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(15);
            }
        }
        if (soundOn && audio != null) {
            int fx = AudioManager.FX_KEYPRESS_STANDARD;
            if ("DEL".equals(key)) fx = AudioManager.FX_KEYPRESS_DELETE;
            else if ("SPACE".equals(key)) fx = AudioManager.FX_KEYPRESS_SPACEBAR;
            else if ("ENTER".equals(key)) fx = AudioManager.FX_KEYPRESS_RETURN;
            audio.playSoundEffect(fx, 0.6f);
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
