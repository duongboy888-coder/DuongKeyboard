package com.duong.keyboard;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Rung khi gõ. Dùng motor rung trực tiếp với thuộc tính "media" để không bị
 * chặn bởi tuỳ chọn "rung khi chạm" của hệ thống (hay bị tắt sẵn trên Oppo/Realme/Xiaomi).
 * Nếu máy không có motor -> dùng haptic của View.
 */
final class Haptics {

    private static Vibrator vibrator;

    @SuppressWarnings("deprecation")
    static void vibrate(Context ctx, View v, int ms) {
        try {
            Vibrator vib = get(ctx);
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    int amp = vib.hasAmplitudeControl()
                            ? Math.min(255, 70 + ms * 4)
                            : VibrationEffect.DEFAULT_AMPLITUDE;
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    vib.vibrate(VibrationEffect.createOneShot(ms, amp), attrs);
                } else {
                    vib.vibrate(ms);
                }
                return;
            }
        } catch (Exception ignored) {
        }
        if (v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            | HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }

    private static Vibrator get(Context ctx) {
        if (vibrator == null) {
            Context app = ctx.getApplicationContext();
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vibrator = vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            }
        }
        return vibrator;
    }

    private Haptics() {}
}
