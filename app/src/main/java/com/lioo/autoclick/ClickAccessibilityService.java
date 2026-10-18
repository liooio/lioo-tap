package com.lioo.autoclick;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ClickAccessibilityService extends AccessibilityService {
    private static ClickAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private int completed;
    private int generation;
    private int gestureToken;
    private long lastGestureUptime;
    private ClickSettings settings;

    static ClickAccessibilityService getInstance() {
        return instance;
    }

    static boolean isEnabled(Context context) {
        if (instance != null) {
            return true;
        }
        String enabledServices = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) {
            return false;
        }
        String expected = new ComponentName(context, ClickAccessibilityService.class)
                .flattenToString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            if (expected.equalsIgnoreCase(splitter.next())) {
                return true;
            }
        }
        return false;
    }

    boolean isRunning() {
        return running;
    }

    boolean shouldIgnoreOutsideTouch() {
        // Injected gestures can reach an overlay as ACTION_OUTSIDE even after completion.
        return running || (lastGestureUptime > 0 && settings != null
                && SystemClock.uptimeMillis() - lastGestureUptime < settings.durationMs + 300L);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This service does not inspect screen content or accessibility events.
    }

    @Override
    public void onInterrupt() {
        stopClicking();
    }

    @Override
    public void onDestroy() {
        stopClicking();
        instance = null;
        OverlayService overlay = OverlayService.getInstance();
        if (overlay != null) {
            overlay.hideOverlay();
        }
        super.onDestroy();
    }

    void toggleClicking() {
        if (running) {
            stopClicking();
            return;
        }
        OverlayService overlay = OverlayService.getInstance();
        if (overlay == null || !overlay.hasTarget()) {
            return;
        }
        settings = ClickSettings.load(this);
        completed = 0;
        running = true;
        generation++;
        overlay.setClickProgress(completed, settings.count);
        overlay.setRunning(true);
        performClick(generation);
    }

    void dispatchRecordedStep(JSONObject step, Runnable completed, Runnable cancelled) {
        stopClicking();
        String type = step.optString("type");
        if (!RecordedScript.TAP.equals(type) && !RecordedScript.DRAG.equals(type)) {
            cancelled.run();
            return;
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        Path path = new Path();
        if (RecordedScript.TAP.equals(type)) {
            path.moveTo(scale(step.optDouble("x"), width),
                    scale(step.optDouble("y"), height));
        } else {
            JSONArray points = step.optJSONArray("points");
            if (points == null || points.length() < 2) {
                cancelled.run();
                return;
            }
            JSONArray first = points.optJSONArray(0);
            if (first == null || first.length() < 2) {
                cancelled.run();
                return;
            }
            path.moveTo(scale(first.optDouble(0), width),
                    scale(first.optDouble(1), height));
            for (int i = 1; i < points.length(); i++) {
                JSONArray point = points.optJSONArray(i);
                if (point != null && point.length() >= 2) {
                    path.lineTo(scale(point.optDouble(0), width),
                            scale(point.optDouble(1), height));
                }
            }
        }
        boolean tap = RecordedScript.TAP.equals(type);
        long defaultDuration = tap ? 48L : 180L;
        long duration = Math.max(tap ? 32L : 80L, Math.min(60000L,
                step.optLong("duration", defaultDuration)));
        dispatchRecordedPath(path, tap, duration, completed, cancelled);
    }

    void dispatchRecordedPath(Path path, boolean tap, long duration,
            Runnable completed, Runnable cancelled) {
        stopClicking();
        lastGestureUptime = SystemClock.uptimeMillis();
        int token = ++gestureToken;
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription description) {
                if (token == gestureToken) {
                    completed.run();
                }
            }

            @Override
            public void onCancelled(GestureDescription description) {
                if (token == gestureToken) {
                    cancelled.run();
                }
            }
        }, handler);
        if (!accepted) {
            cancelled.run();
        }
    }

    void cancelPendingGestures() {
        gestureToken++;
        handler.removeCallbacksAndMessages(null);
    }

    private static float scale(double normalized, int size) {
        return (float) Math.max(0, Math.min(size - 1, normalized * size));
    }

    private void performClick(int currentGeneration) {
        if (!running || currentGeneration != generation) {
            return;
        }
        if (settings.count > 0 && completed >= settings.count) {
            stopClicking();
            return;
        }

        OverlayService overlay = OverlayService.getInstance();
        if (overlay == null || !overlay.hasTarget()) {
            stopClicking();
            return;
        }
        int[] point = overlay.getTargetCenter();
        overlay.showClickFeedback();
        Path path = new Path();
        path.moveTo(point[0], point[1]);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, settings.durationMs))
                .build();

        // Wait for the system callback so gestures never overlap on slower devices.
        lastGestureUptime = SystemClock.uptimeMillis();
        boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription description) {
                if (running && currentGeneration == generation) {
                    completed++;
                    OverlayService currentOverlay = OverlayService.getInstance();
                    if (currentOverlay != null) {
                        currentOverlay.setClickProgress(completed, settings.count);
                    }
                    handler.postDelayed(() -> performClick(currentGeneration), settings.intervalMs);
                }
            }

            @Override
            public void onCancelled(GestureDescription description) {
                if (currentGeneration == generation) {
                    stopClicking();
                }
            }
        }, handler);
        if (!dispatched) {
            stopClicking();
        }
    }

    void stopClicking() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        OverlayService overlay = OverlayService.getInstance();
        if (overlay != null) {
            overlay.setRunning(false);
        }
    }
}
