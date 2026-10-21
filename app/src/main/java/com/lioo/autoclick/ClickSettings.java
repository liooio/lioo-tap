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
        return Math.max(0, Math.min(9999,
                preferences(context).getInt("playback_repeat", 1)));
    }

    static boolean playbackShowsTrail(Context context) {
        return preferences(context).getBoolean("playback_show_trail", true);
    }

    static boolean recordingShowsTrail(Context context) {
        return preferences(context).getBoolean("recording_show_trail", true);
    }

    static void setPlaybackShowsTrail(Context context, boolean show) {
        preferences(context).edit().putBoolean("playback_show_trail", show).apply();
    }

    static void savePlayback(Context context, int countdownSeconds, int repeatCount) {
        savePlayback(context, countdownSeconds, repeatCount, playbackShowsTrail(context));
    }

    static void savePlayback(Context context, int countdownSeconds, int repeatCount,
            boolean showTrail) {
        savePlayback(context, countdownSeconds, repeatCount,
                recordingShowsTrail(context), showTrail);
    }

    static void savePlayback(Context context, int countdownSeconds, int repeatCount,
            boolean recordingShowTrail, boolean playbackShowTrail) {
        preferences(context).edit()
                .putInt("playback_countdown", Math.max(0, Math.min(10, countdownSeconds)))
                .putInt("playback_repeat", Math.max(0, Math.min(9999, repeatCount)))
                .putBoolean("recording_show_trail", recordingShowTrail)
                .putBoolean("playback_show_trail", playbackShowTrail)
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

    static boolean floatingBallOnRight(Context context) {
        return preferences(context).getBoolean("floating_ball_right", false);
    }

    static float floatingBallYRatio(Context context) {
        return Math.max(0f, Math.min(1f,
                preferences(context).getFloat("floating_ball_y_ratio", 0.96f)));
    }

    static void saveFloatingBallPosition(Context context, boolean onRight, float yRatio) {
        preferences(context).edit()
                .putBoolean("floating_ball_right", onRight)
                .putFloat("floating_ball_y_ratio", Math.max(0f, Math.min(1f, yRatio)))
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
