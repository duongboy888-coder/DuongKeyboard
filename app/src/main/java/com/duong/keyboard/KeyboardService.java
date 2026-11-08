package com.duong.keyboard;

import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KeyboardService extends InputMethodService {

    // ===== Màu sắc (dark theme) =====
    private static final int BG          = 0xFF1E1F24;
    private static final int KEY         = 0xFF3A3C43;
    private static final int KEY_SPECIAL = 0xFF2A2C32;
    private static final int KEY_PRESSED = 0xFF5A5D66;
    private static final int KEY_ACTIVE  = 0xFF5A5D66;
    private static final int ACCENT      = 0xFF3D7BFD;
    private static final int TEXT        = 0xFFFFFFFF;

    private static final int MODE_LETTERS = 0, MODE_SYM1 = 1, MODE_SYM2 = 2;

    // ===== Bố cục phím =====
    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"},
            {"?123", ",", "SPACE", ".", "ENTER"}
    };
    private static final String[][] SYM1 = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "_", "&", "-", "+", "(", ")", "/"},
            {"=\\<", "*", "\"", "'", ":", ";", "!", "?", "DEL"},
            {"ABC", ",", "SPACE", ".", "ENTER"}
    };
    private static final String[][] SYM2 = {
            {"~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"},
            {"€", "£", "¥", "^", "°", "=", "[", "]", "<", ">"},
            {"?123", "\\", "©", "®", "™", "…", "¶", "§", "DEL"},
            {"ABC", ",", "SPACE", ".", "ENTER"}
    };

    private LinearLayout root;
    private int mode = MODE_LETTERS;
    private int shift = 0;            // 0 = tắt, 1 = viết hoa 1 lần, 2 = caps lock
    private long lastShiftTap = 0;
    private boolean longPressFired = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            deleteOne();
            handler.postDelayed(this, 50);
        }
    };

    private final Runnable spaceLongPress = new Runnable() {
        @Override public void run() {
            longPressFired = true;
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }
    };

    // ================= Vòng đời IME =================

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(3), dp(6), dp(3), dp(8));
        render();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
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
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        if (computeAutoCaps()) render();
    }

    // ================= Vẽ bàn phím =================

    private void render() {
        if (root == null) return;
        root.removeAllViews();
        String[][] rows = mode == MODE_LETTERS ? LETTERS : (mode == MODE_SYM1 ? SYM1 : SYM2);

        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            boolean indent = mode == MODE_LETTERS && r == 1; // hàng a-l lùi vào nửa phím
            if (indent) row.addView(spacer(0.5f));
            for (String k : rows[r]) row.addView(makeKey(k));
            if (indent) row.addView(spacer(0.5f));

            root.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        }
    }

    private View spacer(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, weight));
        return v;
    }

    private View makeKey(final String key) {
        final TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(TEXT);
        tv.setText(label(key));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, isSpecial(key) && !key.equals("DEL") ? 15 : 21);

        int color = isSpecial(key) ? KEY_SPECIAL : KEY;
        if (key.equals("SHIFT") && shift == 1) color = KEY_ACTIVE;
        if (key.equals("SHIFT") && shift == 2) color = ACCENT;
        if (key.equals("ENTER")) color = ACCENT;
        final int normalColor = color;

        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
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
                        bg.setColor(KEY_PRESSED);
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        if (key.equals("DEL")) {
                            deleteOne();
                            handler.postDelayed(repeatDelete, 400);
                        } else if (key.equals("SPACE")) {
                            longPressFired = false;
                            handler.postDelayed(spaceLongPress, 600);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        bg.setColor(normalColor);
                        stopTimers();
                        if (!key.equals("DEL") && !(key.equals("SPACE") && longPressFired)) {
                            onKey(key);
                        }
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
        return k.equals("SHIFT") || k.equals("DEL") || k.equals("ENTER")
                || k.equals("?123") || k.equals("ABC") || k.equals("=\\<");
    }

    private float weight(String k) {
        if (k.equals("SPACE")) return 5f;
        if (isSpecial(k)) return 1.5f;
        return 1f;
    }

    private String label(String k) {
        switch (k) {
            case "SHIFT": return shift == 2 ? "⇪" : "⇧";
            case "DEL":   return "⌫";
            case "ENTER": return "↵";
            case "SPACE": return "DuongKB";
            default:
                return (mode == MODE_LETTERS && shift > 0) ? k.toUpperCase() : k;
        }
    }

    // ================= Xử lý phím =================

    private void onKey(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (key) {
            case "SHIFT": {
                long now = System.currentTimeMillis();
                if (shift == 0) shift = 1;
                else if (shift == 1) shift = (now - lastShiftTap < 350) ? 2 : 0; // chạm đúp = caps lock
                else shift = 0;
                lastShiftTap = now;
                render();
                break;
            }
            case "?123":
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
            case "SPACE":
                ic.commitText(" ", 1);
                break;
            case "ENTER":
                handleEnter(ic);
                break;
            default: {
                String text = (mode == MODE_LETTERS && shift > 0) ? key.toUpperCase() : key;
                ic.commitText(text, 1);
                if (shift == 1) {
                    shift = 0;
                    render();
                }
            }
        }
    }

    private void deleteOne() {
        // Gửi phím DEL thật -> xử lý đúng cả vùng chọn và emoji
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
                ic.performEditorAction(action); // Gửi / Tìm / Tiếp / Xong...
                return;
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
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

    private void stopTimers() {
        handler.removeCallbacks(repeatDelete);
        handler.removeCallbacks(spaceLongPress);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
