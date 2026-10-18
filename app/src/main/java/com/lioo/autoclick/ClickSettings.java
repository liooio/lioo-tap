package com.lioo.autoclick;

import android.content.Context;
import android.content.SharedPreferences;

final class ClickSettings {
    private static final String PREFS = "click_settings";
    static final int DEFAULT_INTERVAL_MS = 500;
    static final int DEFAULT_DURATION_MS = 50;

    final int intervalMs;
    final int durationMs;
    final int count;

    ClickSettings(int intervalMs, int durationMs, int count) {
        this.intervalMs = intervalMs;
        this.durationMs = durationMs;
        this.count = count;
    }

    static ClickSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new ClickSettings(
                prefs.getInt("interval", DEFAULT_INTERVAL_MS),
                prefs.getInt("duration", DEFAULT_DURATION_MS),
                prefs.getInt("count", 0));
    }

    void save(Context context) {
        preferences(context).edit()
                .putInt("interval", intervalMs)
                .putInt("duration", durationMs)
                .putInt("count", count)
                .apply();
    }

    static boolean shouldShowOverlay(Context context) {
        return preferences(context).getBoolean("show_overlay", false);
    }

    static void setShowOverlay(Context context, boolean show) {
        preferences(context).edit().putBoolean("show_overlay", show).commit();
    }

    static int playbackCountdownSeconds(Context context) {
        return Math.max(0, Math.min(10,
                preferences(context).getInt("playback_countdown", 3)));
    }

    static int playbackRepeatCount(Context context) {
        return Math.max(0, Math.min(999,
                preferences(context).getInt("playback_repeat", 1)));
    }

    static void savePlayback(Context context, int countdownSeconds, int repeatCount) {
        preferences(context).edit()
                .putInt("playback_countdown", Math.max(0, Math.min(10, countdownSeconds)))
                .putInt("playback_repeat", Math.max(0, Math.min(999, repeatCount)))
                .apply();
    }

    static boolean hasAskedBackgroundProtection(Context context) {
        return preferences(context).getBoolean("asked_background_protection", false);
    }

    static void setAskedBackgroundProtection(Context context) {
        preferences(context).edit()
                .putBoolean("asked_background_protection", true)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
