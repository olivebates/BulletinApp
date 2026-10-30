package com.bulletin.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;

/** Shared motion policy: decorative effects never delay a game action. */
final class Feedback {
    private Feedback() { }
    static void vibrate(Context context, boolean penalty) {
        if (Settings.System.getInt(context.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return;
        android.os.Vibrator vibrator = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        vibrator.vibrate(penalty
            ? android.os.VibrationEffect.createWaveform(new long[]{0, 180, 70, 260}, new int[]{0, 200, 0, 255}, -1)
            : android.os.VibrationEffect.createOneShot(45, 110));
    }
    static boolean motion(Context context) {
        return ValueAnimator.areAnimatorsEnabled()
            && Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
    }
    static String completion(String name, long earned, boolean required, boolean allRequired, boolean clear) {
        return "✓ " + name + " completed · +" + Money.format(earned)
            + (allRequired ? "\nAll required quests completed!" : required ? "\nRequired quest finished." : "")
            + (clear ? "\nBoard cleared!" : "");
    }
    static long interpolate(long from, long to, float fraction) {
        if (fraction >= 1) return to;
        if (fraction <= 0) return from;
        return from + Math.round((to - from) * (double) fraction);
    }
}
