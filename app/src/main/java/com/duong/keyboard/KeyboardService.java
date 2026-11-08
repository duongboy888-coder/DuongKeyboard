package com.duong.keyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
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

    private static final int MODE_LETTERS = 0, MODE_SYM1 = 1, MODE_SYM2 = 2, MODE_EMOJI = 3;

    // ================= Bố cục phím =================
    // Hàng số có nút emoji ở góc cuối
    private static final String[] NUM_ROW = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "EMOJI"};
    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"}
    };
    private static final String[] BOTTOM = {"?123", ",", "SPACE", ".", "ENTER"};
    // khi tắt hàng số, emoji quay về hàng dưới cùng
    private static final String[] BOTTOM_EMOJI = {"?123", "EMOJI", ",", "SPACE", ".", "ENTER"};
    private static final String[][] SYM1 = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "EMOJI"},
            {"@", "#", "$", "_", "&", "-", "+", "(", ")", "/"},
            {"=\\<", "*", "\"", "'", ":", ";", "!", "?", "DEL"},
            {"ABC", ",", "SPACE", ".", "ENTER"}
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
    private boolean telexOn, telexAllowed, vibrateOn, soundOn, numberRow, shadowOn;
    private int keyHeightDp, radiusDp, textSizeSp, vibrateMs;

    private LinearLayout root;
    private TextView spaceKey;
    private int mode = MODE_LETTERS;
    private int shift = 0;            // 0 tắt · 1 hoa 1 lần · 2 caps lock
    private long lastShiftTap = 0;
    private int emojiCategory = 1;

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
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        loadPrefs();
    }

    private void loadPrefs() {
        telexOn = prefs.getBoolean(Prefs.TELEX, true);
        telex.setModernStyle(prefs.getBoolean(Prefs.MODERN, false));
        vibrateOn = prefs.getBoolean(Prefs.VIBRATE, true);
        soundOn = prefs.getBoolean(Prefs.SOUND, false);
        vibrateMs = prefs.getInt(Prefs.VIB_MS, Prefs.DEF_VIB);
        numberRow = prefs.getBoolean(Prefs.NUMBER_ROW, true);
        keyHeightDp = prefs.getInt(Prefs.KEY_HEIGHT, Prefs.DEF_HEIGHT);
        textSizeSp = prefs.getInt(Prefs.TEXT_SIZE, Prefs.DEF_TEXT);
        shadowOn = prefs.getBoolean(Prefs.SHADOW, true);
        theme = Theme.of(prefs.getString(Prefs.THEME, "dark"));
        int r = prefs.getInt(Prefs.RADIUS, -1);
        radiusDp = r < 0 ? theme.radius : r;
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

    private int numRowHeight() { return dp(Prefs.NUM_ROW_HEIGHT); }

    private int keyboardHeight() {
        return dp(keyHeightDp) * 4 + (numberRow ? numRowHeight() : 0);
    }

    private void render() {
        if (root == null) return;
        root.removeAllViews();
        spaceKey = null;
        root.setBackground(theme.background(dp(17), dp(64)));
        root.setPadding(dp(2), dp(5), dp(2), dp(6));

        if (mode == MODE_EMOJI) {
            buildEmojiPanel();
            return;
        }

        List<String[]> rows = new ArrayList<>();
        if (mode == MODE_LETTERS) {
            if (numberRow) rows.add(NUM_ROW);
            rows.addAll(Arrays.asList(LETTERS));
            rows.add(numberRow ? BOTTOM : BOTTOM_EMOJI);
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

            boolean hasEmoji = Arrays.asList(keys).contains("EMOJI");
            boolean indent = keys == LETTERS[1];
            if (indent) row.addView(spacer(0.5f));
            for (String k : keys) row.addView(makeKey(k, keys == NUM_ROW, hasEmoji));
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
        return makeKey(key, false, false);
    }

    private View makeKey(final String key, boolean numRow, boolean rowHasEmoji) {
        final KeyView tv = new KeyView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText(label(key));
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setIncludeFontPadding(false);

        float size = textSizeSp;
        if (numRow) size = 17;                                   // hàng số giữ cỡ cũ
        else if (key.equals("SPACE") || key.equals("?123") || key.equals("ABC") || key.equals("=\\<")) size = 14;
        else if (key.equals("DEL") || key.equals("SHIFT") || key.equals("ENTER") || key.equals("EMOJI")) size = 20;
        else if (mode != MODE_LETTERS) size = textSizeSp - 2;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);

        int color = isSpecial(key) ? theme.special : theme.key;
        int textColor = theme.text;
        int iconColor = theme.text;
        if (key.equals("SHIFT") && shift == 1) iconColor = theme.accent;       // hoa 1 lần: icon đổi màu
        if ((key.equals("SHIFT") && shift == 2) || key.equals("ENTER")) {       // caps lock / enter: nền nổi bật
            color = theme.accent;
            textColor = 0xFFFFFFFF;
            iconColor = 0xFFFFFFFF;
        }
        tv.setTextColor(textColor);
        if (key.equals("SPACE")) {
            spaceKey = tv;
            tv.setAlpha(0.8f);
        }

        // Icon theo chủ đề (Enter, Shift/Caps, Xoá, Emoji)
        int res = KeyView.iconFor(theme, key, shift, enterAction());
        if (res != 0) {
            Drawable d = getResources().getDrawable(res, null).mutate();
            d.setTint(iconColor);
            tv.setText("");
            tv.setIcon(d, dp(numRow ? 20 : (key.equals("DEL") ? 24 : 23)));
        }
        // Phím chuyển trang (?123 / ABC / =\<) dùng màu nhấn của chủ đề
        if (key.equals("?123") || key.equals("ABC") || key.equals("=\\<")) {
            tv.setTextColor(theme.accent);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            if (theme.pattern == Theme.PATTERN_PAW && !key.equals("=\\<")) {
                Drawable paw = getResources().getDrawable(R.drawable.ic_paw, null).mutate();
                paw.setTint(theme.accent);
                tv.setText("");
                tv.setIconLabel(key.equals("?123") ? "123" : "ABC");
                tv.setIcon(paw, dp(15));
            }
        }
        if (shadowOn) tv.setPadding(0, 0, 0, dp(1.5f));

        final int normalColor = color;
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(radiusDp));
        bg.setColor(normalColor);
        tv.setBackground(theme.keyBackground(bg, dp(radiusDp), shadowOn, dp(1.5f)));

        float w = weight(key);
        if (key.equals("SPACE") && rowHasEmoji) w = 4f;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, w);
        int mh = dp(2.5f), mv = dp(3);                            // khe hẹp -> phím rộng hơn
        lp.setMargins(mh, mv, mh, mv);
        tv.setLayoutParams(lp);

        tv.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        bg.setColor(theme.pressed);
                        feedback(v, key);
                        longPressFired = false;
                        if (key.equals("DEL")) {
                            deleteOne();
                            handler.postDelayed(repeatDelete, 400);
                        } else if (key.equals("SPACE")) {
                            schedule(new Runnable() { public void run() { toggleLanguage(); } });
                        } else if (key.equals(",")) {
                            schedule(new Runnable() { public void run() { openSettings(); } });
                        } else if (key.equals("EMOJI")) {
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
        if (k.equals("SPACE")) return 5f;
        if (k.equals("EMOJI")) return 1f;
        if (k.equals("SHIFT") || k.equals("DEL")) return 1.4f;   // nhường chỗ cho phím chữ
        if (isSpecial(k)) return 1.5f;
        return 1f;
    }

    private String label(String k) {
        switch (k) {
            case "SHIFT": case "DEL": case "EMOJI": case "ENTER": return "";
            case "SPACE": {
                String s = useTelex() ? "Tiếng Việt" : "English";
                return theme.pattern == Theme.PATTERN_PAW ? "🐾 " + s : s;
            }
            default:
                return (mode == MODE_LETTERS && shift > 0) ? k.toUpperCase() : k;
        }
    }

    /** Hành động của phím Enter trong ô nhập hiện tại, -1 = xuống dòng. */
    private int enterAction() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                || (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) return -1;
        return ei.imeOptions & EditorInfo.IME_MASK_ACTION;
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
                    feedback(v, "EMOJI");
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
                    feedback(v, "");
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
        feedback(spaceKey, "");
    }

    private void openSettings() {
        longPressFired = true;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        requestHideSelf(0);
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

    private void feedback(View v, String key) {
        if (vibrateOn) Haptics.vibrate(this, v, vibrateMs);
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
