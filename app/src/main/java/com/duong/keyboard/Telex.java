package com.duong.keyboard;

import java.util.Locale;

/**
 * Bộ gõ Telex: giữ từ đang gõ dưới dạng (chữ gốc + dấu thanh),
 * mỗi lần gõ phím sẽ biến đổi rồi tự đặt lại vị trí dấu cho đúng.
 *
 *  s sắc · f huyền · r hỏi · x ngã · j nặng · z xoá dấu
 *  aa â · aw ă · ee ê · oo ô · ow ơ · uw ư · w ư · dd đ
 */
public class Telex {

    // cột 0 = không dấu, 1 huyền, 2 sắc, 3 hỏi, 4 ngã, 5 nặng
    private static final String[] TABLE = {
            "aàáảãạ", "ăằắẳẵặ", "âầấẩẫậ", "eèéẻẽẹ", "êềếểễệ", "iìíỉĩị",
            "oòóỏõọ", "ôồốổỗộ", "ơờớởỡợ", "uùúủũụ", "ưừứửữự", "yỳýỷỹỵ"
    };
    private static final String VOWELS = "aăâeêioôơuưy";
    private static final String TONE_KEYS = "zfsrxj"; // index = mã dấu (z = 0 = xoá)

    private final StringBuilder base = new StringBuilder();
    private int tone = 0;
    private boolean literal = false; // sau khi "hoàn tác" (vd: ss), gõ thẳng phần còn lại
    private boolean modern = false;  // true: oà, uý · false: òa, úy

    public void setModernStyle(boolean m) { modern = m; }
    public boolean isEmpty() { return base.length() == 0; }

    public void reset() {
        base.setLength(0);
        tone = 0;
        literal = false;
    }

    public String getText() {
        StringBuilder out = new StringBuilder(base);
        if (tone > 0) {
            int p = tonePos();
            if (p >= 0) out.setCharAt(p, withTone(out.charAt(p), tone));
        }
        return out.toString();
    }

    public void type(char c) {
        char lc = Character.toLowerCase(c);
        if (!literal) {
            int t = TONE_KEYS.indexOf(lc);
            if (t >= 0 && applyTone(t, c)) return;
            if (lc == 'w' && applyW(c)) return;
            if ((lc == 'a' || lc == 'e' || lc == 'o') && applyHat(lc, c)) return;
            if (lc == 'd' && applyD(c)) return;
        }
        base.append(c);
    }

    public void backspace() {
        String s = getText();
        if (s.isEmpty()) return;
        load(s.substring(0, s.length() - 1));
    }

    // ================= Biến đổi =================

    private boolean applyTone(int t, char c) {
        int[] cl = cluster();
        if (cl == null || !validEnding(cl[1])) return false;
        if (t == 0) {
            if (tone > 0) { tone = 0; return true; }
            return false;
        }
        if (tone == t) {          // gõ lại cùng dấu -> bỏ dấu, in ra chữ
            tone = 0;
            base.append(c);
            literal = true;
            return true;
        }
        tone = t;
        return true;
    }

    private boolean applyW(char c) {
        int[] cl = cluster();
        if (cl == null) {         // chưa có nguyên âm: w -> ư
            base.append(Character.isUpperCase(c) ? 'Ư' : 'ư');
            return true;
        }
        if (!validEnding(cl[1])) return false;
        String s = lower();

        // uo -> ươ, ưo -> ươ
        for (int i = cl[0]; i < cl[1] - 1; i++) {
            char a = s.charAt(i), b = s.charAt(i + 1);
            if ((a == 'u' || a == 'ư') && b == 'o') {
                set(i, 'ư');
                set(i + 1, 'ơ');
                return true;
            }
        }
        // nguyên âm a/o/u gần nhất
        for (int i = cl[1] - 1; i >= cl[0]; i--) {
            char ch = s.charAt(i);
            if (ch == 'a') { set(i, 'ă'); return true; }
            if (ch == 'o') { set(i, 'ơ'); return true; }
            if (ch == 'u') { set(i, 'ư'); return true; }
        }
        // đã có ă/ơ/ư -> hoàn tác
        boolean undone = false;
        for (int i = cl[0]; i < cl[1]; i++) {
            char ch = s.charAt(i);
            if (ch == 'ă') { set(i, 'a'); undone = true; }
            else if (ch == 'ơ') { set(i, 'o'); undone = true; }
            else if (ch == 'ư') { set(i, 'u'); undone = true; }
        }
        if (undone) {
            base.append(c);
            literal = true;
            return true;
        }
        return false;
    }

    private boolean applyHat(char lc, char c) {
        int[] cl = cluster();
        if (cl == null || !validEnding(cl[1])) return false;
        char hat = lc == 'a' ? 'â' : (lc == 'e' ? 'ê' : 'ô');
        String s = lower();
        for (int i = cl[1] - 1; i >= cl[0]; i--) {
            char ch = s.charAt(i);
            if (ch == hat) {      // aaa -> aa
                set(i, lc);
                base.append(c);
                literal = true;
                return true;
            }
            if (ch == lc || (lc == 'a' && ch == 'ă') || (lc == 'o' && ch == 'ơ')) {
                set(i, hat);
                return true;
            }
        }
        return false;
    }

    private boolean applyD(char c) {
        if (base.length() == 0) return false;
        char f = base.charAt(0);
        if (f == 'd' || f == 'D') { set(0, 'đ'); return true; }
        if (f == 'đ' || f == 'Đ') {
            set(0, 'd');
            base.append(c);
            literal = true;
            return true;
        }
        return false;
    }

    // ================= Quy tắc đặt dấu =================

    /** Cụm nguyên âm cuối cùng: {start, end) — bỏ qua "qu", "gi". */
    private int[] cluster() {
        String s = lower();
        int end = -1;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (isVowel(s.charAt(i))) { end = i + 1; break; }
        }
        if (end < 0) return null;
        int start = end - 1;
        while (start > 0 && isVowel(s.charAt(start - 1))) start--;
        if (end - start > 1 && start > 0) {
            char first = s.charAt(start), prev = s.charAt(start - 1);
            if ((first == 'u' && prev == 'q') || (first == 'i' && prev == 'g')) start++;
        }
        return new int[]{start, end};
    }

    private boolean validEnding(int end) {
        switch (lower().substring(end)) {
            case "": case "c": case "ch": case "m": case "n":
            case "ng": case "nh": case "p": case "t":
                return true;
            default:
                return false;
        }
    }

    private int tonePos() {
        int[] c = cluster();
        if (c == null) return -1;
        String s = lower();
        int st = c[0], en = c[1];

        for (int i = en - 1; i >= st; i--) {
            if ("ăâêôơ".indexOf(s.charAt(i)) >= 0) return i;
        }
        for (int i = st; i < en; i++) {
            if (s.charAt(i) == 'ư') return i;
        }
        int len = en - st;
        if (len == 1) return st;
        if (en < s.length()) return en - 1;     // có phụ âm cuối: hoàn, tuấn
        if (len >= 3) return st + 1;            // ngoài, khuya
        String pair = s.substring(st, en);
        if (modern && (pair.equals("oa") || pair.equals("oe") || pair.equals("uy"))) return st + 1;
        return st;                               // mùa, kìa, hòa, thúy
    }

    // ================= Tiện ích =================

    private void load(String s) {
        reset();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char lc = Character.toLowerCase(c);
            boolean found = false;
            for (String row : TABLE) {
                int k = row.indexOf(lc);
                if (k > 0) {
                    char b = row.charAt(0);
                    base.append(c != lc ? Character.toUpperCase(b) : b);
                    tone = k;
                    found = true;
                    break;
                }
            }
            if (!found) base.append(c);
        }
    }

    private static char withTone(char c, int t) {
        char lc = Character.toLowerCase(c);
        for (String row : TABLE) {
            if (row.charAt(0) == lc) {
                char r = row.charAt(t);
                return c != lc ? Character.toUpperCase(r) : r;
            }
        }
        return c;
    }

    private void set(int i, char lowerChar) {
        boolean up = Character.isUpperCase(base.charAt(i));
        base.setCharAt(i, up ? Character.toUpperCase(lowerChar) : lowerChar);
    }

    private String lower() { return base.toString().toLowerCase(Locale.ROOT); }
    private static boolean isVowel(char c) { return VOWELS.indexOf(c) >= 0; }
}
