package com.lioo.autoclick;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Full-screen touch recorder that mirrors completed gestures through accessibility. */
final class GestureRecordingOverlay {
    private static final int MAX_DRAG_POINTS = 120;
    private static final long MIN_POINT_INTERVAL_MS = 32L;
    private static final long INPUT_WINDOW_SETTLE_MS = 32L;

    interface Listener {
        void onClosed(JSONArray steps, boolean save);
    }

    private final OverlayService service;
    private final WindowManager windowManager;
    private final WindowManager captureWindowManager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusUpdate = this::updateStatus;
    private final List<RecordedGesture> recordedGestures = new ArrayList<>();
    private final List<float[]> points = new ArrayList<>();
    private final List<WindowManager.LayoutParams> captureParams = new ArrayList<>();

    private final List<View> captures = new ArrayList<>();
    private LinearLayout controls;
    private TextView status;
    private TextView primary;
    private TextView secondary;
    private TextView tertiary;
    private WindowManager.LayoutParams controlParams;
    private float dragDownX;
    private float dragDownY;
    private int dragStartX;
    private int dragStartY;
    private long downTime;
    private long lastPointTime;
    private long lastInjectedAt;
    private long pendingDelayMs;
    private int mirrorGeneration;
    private boolean injecting;
    private boolean active;
    private boolean recording;
    private boolean captureAdded;
    private boolean finishRequested;
    private boolean ignoreTouchSequence;
    private boolean replaying;
    private int recordedActionCount;
    private JSONArray steps;
    private int replayIndex;
    private int replayLoop;

    GestureRecordingOverlay(OverlayService service, ClickAccessibilityService accessibility,
            Listener listener) {
        this.service = service;
        this.listener = listener;
        windowManager = (WindowManager) service.getSystemService(
                Context.WINDOW_SERVICE);
        // Accessibility overlays are trusted for touch pass-through on Android 12+.
        captureWindowManager = (WindowManager) accessibility.getSystemService(
                Context.WINDOW_SERVICE);
    }

    void start() {
        if (active) {
            return;
        }
        active = true;
        controls = new LinearLayout(service);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(14), dp(8), dp(8), dp(8));
        controls.setBackground(background(0xF21B1E2C, dp(18)));
        controls.setContentDescription("拖动录制操作栏");
        controls.setOnTouchListener(this::dragControls);
        status = text("录制准备 · 0 步", 13, Color.WHITE, true);
        controls.addView(status, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        primary = action("开始", 0xFF1769FF);
        primary.setOnClickListener(view -> {
            if (recording) {
                pauseRecording();
            } else {
                startOrResumeRecording();
            }
        });
        secondary = action("取消", 0xFF3A4254);
        secondary.setOnClickListener(view -> stop(false));
        tertiary = action("完成", 0xFF3A4254);
        tertiary.setOnClickListener(view -> completeRecording());
        addAction(primary);
        addAction(secondary);
        addAction(tertiary);

        int width = Math.min(dp(390),
                service.getResources().getDisplayMetrics().widthPixels - dp(24));
        controlParams = new WindowManager.LayoutParams(
                width, dp(58), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        controlParams.y = dp(24);
        windowManager.addView(controls, controlParams);
        Toast.makeText(service, "请打开目标应用，再点击顶部操作栏的“开始”",
                Toast.LENGTH_LONG).show();
    }

    void cancel() {
        stop(false);
    }

    private void startOrResumeRecording() {
        if (!active || recording || replaying) {
            return;
        }
        recording = true;
        lastInjectedAt = SystemClock.uptimeMillis();
        pendingDelayMs = 0;
        primary.setText("暂停");
        primary.setAlpha(1f);
        updateStatus();
        // Wait until the start button has finished receiving its touch sequence.
        handler.post(() -> {
            if (active && recording) {
                ensureCapture();
            }
        });
    }

    private void pauseRecording() {
        if (!recording || replaying) {
            return;
        }
        recording = false;
        removeCapture();
        primary.setText("开始");
        primary.setAlpha(1f);
        status.setText("已暂停 · " + actionCount() + " 步");
    }

    private void completeRecording() {
        if (replaying) {
            return;
        }
        if (injecting) {
            finishRequested = true;
            recording = false;
            status.setText("正在完成最后一步…");
            primary.setEnabled(false);
            primary.setAlpha(0.45f);
            return;
        }
        if (recordedGestures.isEmpty()) {
            Toast.makeText(service, "还没有录制任何操作", Toast.LENGTH_SHORT).show();
            return;
        }
        recording = false;
        removeCapture();
        showCompletedActions();
    }

    private void showCompletedActions() {
        replaying = false;
        status.setText("录制完成 · " + actionCount() + " 步");
        primary.setText("回放");
        primary.setEnabled(true);
        primary.setAlpha(1f);
        primary.setBackground(background(0xFF1769FF, dp(12)));
        primary.setOnClickListener(view -> startReplay());
        secondary.setText("取消");
        secondary.setEnabled(true);
        secondary.setAlpha(1f);
        secondary.setBackground(background(0xFF3A4254, dp(12)));
        secondary.setOnClickListener(view -> stop(false));
        tertiary.setText("保存");
        tertiary.setEnabled(true);
        tertiary.setAlpha(1f);
        tertiary.setBackground(background(0xFF2F7D55, dp(12)));
        tertiary.setOnClickListener(view -> stop(true));
    }

    private void startReplay() {
        if (replaying) {
            return;
        }
        if (steps == null && !recordedGestures.isEmpty()) {
            steps = serializeRecordedGestures();
        }
        if (steps == null || steps.length() == 0) {
            return;
        }
        replaying = true;
        replayIndex = 0;
        replayLoop = 0;
        primary.setText("停止");
        primary.setBackground(background(0xFFE24B4B, dp(12)));
        primary.setOnClickListener(view -> stopReplay());
        secondary.setAlpha(0.45f);
        secondary.setEnabled(false);
        tertiary.setAlpha(0.45f);
        tertiary.setEnabled(false);
        countdownReplay(ClickSettings.playbackCountdownSeconds(service));
    }

    private void countdownReplay(int seconds) {
        if (!active || !replaying || steps == null) {
            return;
        }
        if (seconds == 0) {
            replayNext();
            return;
        }
        status.setText(seconds + " 秒后回放 · " + actionCount() + " 步");
        handler.postDelayed(() -> countdownReplay(seconds - 1), 1000);
    }

    private void replayNext() {
        if (!active || !replaying) {
            return;
        }
        if (replayIndex >= steps.length()) {
            int repeat = ClickSettings.playbackRepeatCount(service);
            if (repeat == 0 || replayLoop + 1 < repeat) {
                replayLoop++;
                replayIndex = 0;
                status.setText("第 " + (replayLoop + 1)
                        + (repeat == 0 ? " 轮" : "/" + repeat + " 轮")
                        + " · " + actionCount() + " 步");
                handler.post(this::replayNext);
                return;
            }
            Toast.makeText(service, "试播完成", Toast.LENGTH_SHORT).show();
            showCompletedActions();
            return;
        }
        JSONObject step = steps.optJSONObject(replayIndex);
        replayIndex++;
        if (step == null) {
            replayNext();
            return;
        }
        status.setText("回放 " + replayIndex + "/" + steps.length() + " · "
                + RecordedScript.label(step));
        if (RecordedScript.DELAY.equals(step.optString("type"))) {
            handler.postDelayed(this::replayNext,
                    Math.max(0, Math.min(600000, step.optLong("duration"))));
            return;
        }
        ClickAccessibilityService accessibility = ClickAccessibilityService.getInstance();
        if (accessibility == null) {
            Toast.makeText(service, "无障碍服务已断开", Toast.LENGTH_LONG).show();
            stopReplay();
            return;
        }
        accessibility.dispatchRecordedStep(step, this::replayNext, () -> {
            Toast.makeText(service, "第 " + replayIndex + " 步回放失败",
                    Toast.LENGTH_LONG).show();
            stopReplay();
        });
    }

    private void stopReplay() {
        if (!replaying) {
            return;
        }
        replaying = false;
        handler.removeCallbacksAndMessages(null);
        showCompletedActions();
    }

    private void ensureCapture() {
        if (captureAdded || !active || controls == null) {
            return;
        }
        int[] position = new int[2];
        controls.getLocationOnScreen(position);
        int screenWidth = service.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = service.getResources().getDisplayMetrics().heightPixels;
        int left = Math.max(0, Math.min(screenWidth, position[0]));
        int top = Math.max(0, Math.min(screenHeight, position[1]));
        int right = Math.max(left, Math.min(screenWidth, position[0] + controls.getWidth()));
        int bottom = Math.max(top, Math.min(screenHeight, position[1] + controls.getHeight()));

        addCaptureRegion(0, 0, screenWidth, top);
        addCaptureRegion(0, bottom, screenWidth, screenHeight - bottom);
        addCaptureRegion(0, top, left, bottom - top);
        addCaptureRegion(right, top, screenWidth - right, bottom - top);
        captureAdded = !captures.isEmpty();
    }

    private void addCaptureRegion(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        View region = new View(service);
        region.setBackgroundColor(Color.argb(1, 0, 0, 0));
        region.setOnTouchListener(this::onTouch);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        captureWindowManager.addView(region, params);
        captures.add(region);
        captureParams.add(params);
    }

    private void removeCapture() {
        for (View region : captures) {
            captureWindowManager.removeViewImmediate(region);
        }
        captures.clear();
        captureParams.clear();
        captureAdded = false;
    }

    private boolean onTouch(View view, MotionEvent event) {
        if (!active || !recording || injecting) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            ignoreTouchSequence = isInsideControls(event.getRawX(), event.getRawY())
                    || event.getPointerCount() != 1;
            if (ignoreTouchSequence) {
                points.clear();
                return true;
            }
            long now = SystemClock.uptimeMillis();
            pendingDelayMs = lastInjectedAt > 0 ? Math.max(0, now - lastInjectedAt) : 0;
            points.clear();
            points.add(new float[] {event.getRawX(), event.getRawY()});
            downTime = event.getEventTime();
            lastPointTime = downTime;
            return true;
        }
        if (ignoreTouchSequence) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                ignoreTouchSequence = false;
            }
            return true;
        }
        if (event.getPointerCount() != 1) {
            ignoreTouchSequence = true;
            points.clear();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            addPoint(event.getRawX(), event.getRawY(), event.getEventTime(), false);
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            addPoint(event.getRawX(), event.getRawY(), event.getEventTime(), true);
            recordGesture(Math.max(1L, event.getEventTime() - downTime));
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            points.clear();
            return true;
        }
        return true;
    }

    private boolean isInsideControls(float screenX, float screenY) {
        if (controls == null || !controls.isAttachedToWindow()) {
            return false;
        }
        int[] position = new int[2];
        controls.getLocationOnScreen(position);
        return screenX >= position[0] && screenX < position[0] + controls.getWidth()
                && screenY >= position[1] && screenY < position[1] + controls.getHeight();
    }

    private void addPoint(float x, float y, long eventTime, boolean finalPoint) {
        if (points.isEmpty()) {
            points.add(new float[] {x, y});
            lastPointTime = eventTime;
            return;
        }
        float[] previous = points.get(points.size() - 1);
        float dx = x - previous[0];
        float dy = y - previous[1];
        boolean movedEnough = dx * dx + dy * dy >= dp(5) * dp(5);
        boolean waitedEnough = eventTime - lastPointTime >= MIN_POINT_INTERVAL_MS;
        if (finalPoint && (dx != 0 || dy != 0)) {
            points.add(new float[] {x, y});
            lastPointTime = eventTime;
        } else if (movedEnough && waitedEnough) {
            if (points.size() < MAX_DRAG_POINTS) {
                points.add(new float[] {x, y});
            } else {
                // Keep the endpoint current while bounding memory for very long drags.
                previous[0] = x;
                previous[1] = y;
            }
            lastPointTime = eventTime;
        }
    }

    private void recordGesture(long duration) {
        if (points.isEmpty()) {
            return;
        }
        ClickAccessibilityService accessibility = ClickAccessibilityService.getInstance();
        if (accessibility == null) {
            Toast.makeText(service, "无障碍服务已断开，录制停止", Toast.LENGTH_LONG).show();
            completeRecording();
            return;
        }
        injecting = true;
        // Remove the input windows immediately. Changing touchability on four windows
        // leaves a transition where the next tap can still be delivered to this listener.
        removeCapture();
        int currentMirror = ++mirrorGeneration;
        long delayBefore = pendingDelayMs;
        pendingDelayMs = 0;
        float[][] rawPoints = new float[points.size()][2];
        for (int i = 0; i < points.size(); i++) {
            rawPoints[i][0] = points.get(i)[0];
            rawPoints[i][1] = points.get(i)[1];
        }
        int screenWidth = service.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = service.getResources().getDisplayMetrics().heightPixels;
        float tapThreshold = dp(8);
        try {
            RecordedGesture gesture = RecordedGesture.create(rawPoints, duration,
                    delayBefore, screenWidth, screenHeight, tapThreshold);
            // Two display frames are enough for InputDispatcher to observe window removal
            // without imposing the former fixed 100 ms dead period on every operation.
            handler.postDelayed(() -> dispatchPreparedGesture(
                    accessibility, gesture, currentMirror), INPUT_WINDOW_SETTLE_MS);
        } catch (RuntimeException exception) {
            if (currentMirror == mirrorGeneration) {
                restoreCapture(currentMirror);
                Toast.makeText(service, "这次操作未能记录", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void dispatchPreparedGesture(ClickAccessibilityService accessibility,
            RecordedGesture gesture, int currentMirror) {
        if (!active || (!recording && !finishRequested)
                || currentMirror != mirrorGeneration) {
            return;
        }
        long timeoutMs = Math.min(65000L,
                Math.max(2000L, gesture.duration + 2000L));
        handler.postDelayed(() -> {
            if (active && injecting && currentMirror == mirrorGeneration) {
                restoreCapture(currentMirror);
                Toast.makeText(service, "手势回放超时，已恢复录制", Toast.LENGTH_SHORT).show();
            }
        }, timeoutMs);
        accessibility.dispatchRecordedPath(gesture.path, gesture.tap, gesture.duration,
                () -> {
                    if (currentMirror != mirrorGeneration) {
                        return;
                    }
                    if (active) {
                        recordedGestures.add(gesture);
                        recordedActionCount++;
                    }
                    restoreCapture(currentMirror);
                    if (active && finishRequested) {
                        finishRequested = false;
                        showCompletedActions();
                        return;
                    }
                    if (active) {
                        handler.removeCallbacks(statusUpdate);
                        handler.postDelayed(statusUpdate, 250L);
                    }
                }, () -> {
                    if (currentMirror != mirrorGeneration) {
                        return;
                    }
                    restoreCapture(currentMirror);
                    if (active && finishRequested) {
                        finishRequested = false;
                        if (recordedGestures.isEmpty()) {
                            recording = true;
                            primary.setEnabled(true);
                            primary.setAlpha(1f);
                            ensureCapture();
                        } else {
                            showCompletedActions();
                            return;
                        }
                    }
                    if (active && recording) {
                        Toast.makeText(service, "这次点击未落到目标界面，已跳过",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private JSONArray serializeRecordedGestures() {
        JSONArray result = new JSONArray();
        for (RecordedGesture gesture : recordedGestures) {
            try {
                if (gesture.delayBefore >= 40) {
                    JSONObject delay = new JSONObject();
                    delay.put("type", RecordedScript.DELAY);
                    delay.put("duration", Math.min(gesture.delayBefore, 600000L));
                    result.put(delay);
                }
                JSONObject step = new JSONObject();
                step.put("type", gesture.tap ? RecordedScript.TAP : RecordedScript.DRAG);
                step.put("duration", gesture.duration);
                if (gesture.tap) {
                    step.put("x", gesture.normalizedPoints[0]);
                    step.put("y", gesture.normalizedPoints[1]);
                } else {
                    JSONArray path = new JSONArray();
                    for (int i = 0; i < gesture.normalizedPoints.length; i += 2) {
                        path.put(new JSONArray().put(gesture.normalizedPoints[i])
                                .put(gesture.normalizedPoints[i + 1]));
                    }
                    step.put("points", path);
                }
                result.put(step);
            } catch (JSONException ignored) {
                // Fixed keys and primitive values cannot fail in practice.
            }
        }
        return result;
    }

    private void restoreCapture(int completedMirror) {
        if (!active || completedMirror != mirrorGeneration) {
            return;
        }
        handler.removeCallbacksAndMessages(null);
        lastInjectedAt = SystemClock.uptimeMillis();
        injecting = false;
        if (recording) {
            ensureCapture();
        }
    }

    private void updateStatus() {
        if (status != null) {
            status.setText("录制中 · " + actionCount() + " 步");
        }
    }

    private int actionCount() {
        return recordedActionCount;
    }

    private boolean dragControls(View view, MotionEvent event) {
        if (controlParams == null || controls == null) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            dragDownX = event.getRawX();
            dragDownY = event.getRawY();
            dragStartX = controlParams.x;
            dragStartY = controlParams.y;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            int screenWidth = service.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = service.getResources().getDisplayMetrics().heightPixels;
            int maxX = Math.max(0, (screenWidth - controls.getWidth()) / 2);
            controlParams.x = Math.max(-maxX, Math.min(maxX,
                    dragStartX + Math.round(event.getRawX() - dragDownX)));
            controlParams.y = Math.max(0, Math.min(screenHeight - controls.getHeight(),
                    dragStartY + Math.round(event.getRawY() - dragDownY)));
            windowManager.updateViewLayout(controls, controlParams);
            return true;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (recording) {
                handler.post(() -> {
                    if (active && recording) {
                        removeCapture();
                        ensureCapture();
                    }
                });
            }
            return true;
        }
        return false;
    }

    private void stop(boolean save) {
        if (!active) {
            return;
        }
        active = false;
        recording = false;
        replaying = false;
        injecting = false;
        finishRequested = false;
        pendingDelayMs = 0;
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(statusUpdate);
        mirrorGeneration++;
        ClickAccessibilityService accessibility = ClickAccessibilityService.getInstance();
        if (accessibility != null) {
            accessibility.cancelPendingGestures();
        }
        removeCapture();
        if (controls != null) {
            windowManager.removeView(controls);
        }
        captures.clear();
        controls = null;
        controlParams = null;
        if (save && steps == null) {
            steps = serializeRecordedGestures();
        }
        listener.onClosed(save && steps != null ? steps : new JSONArray(), save);
    }

    private static final class RecordedGesture {
        final boolean tap;
        final long duration;
        final long delayBefore;
        final float[] normalizedPoints;
        final Path path;

        private RecordedGesture(boolean tap, long duration, long delayBefore,
                float[] normalizedPoints, Path path) {
            this.tap = tap;
            this.duration = duration;
            this.delayBefore = delayBefore;
            this.normalizedPoints = normalizedPoints;
            this.path = path;
        }

        static RecordedGesture create(float[][] rawPoints, long rawDuration,
                long delayBefore, int width, int height, float tapThreshold) {
            float[] first = rawPoints[0];
            double farthestSquared = 0;
            for (float[] point : rawPoints) {
                float dx = point[0] - first[0];
                float dy = point[1] - first[1];
                farthestSquared = Math.max(farthestSquared, dx * dx + dy * dy);
            }
            boolean tap = farthestSquared < tapThreshold * tapThreshold;
            long duration = tap
                    ? Math.max(32L, Math.min(rawDuration, 180L))
                    : Math.max(80L, Math.min(rawDuration, 60000L));
            Path path = new Path();
            path.moveTo(first[0], first[1]);
            int pointLimit = tap ? 1 : rawPoints.length;
            float[] normalized = new float[pointLimit * 2];
            for (int i = 0; i < pointLimit; i++) {
                float[] point = rawPoints[i];
                normalized[i * 2] = (float) normalize(point[0], width);
                normalized[i * 2 + 1] = (float) normalize(point[1], height);
                if (i > 0) {
                    path.lineTo(point[0], point[1]);
                }
            }
            return new RecordedGesture(tap, duration, delayBefore, normalized, path);
        }
    }

    private static double normalize(float coordinate, int size) {
        return Math.max(0d, Math.min(1d, coordinate / Math.max(1d, size)));
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(service);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.DEFAULT,
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return view;
    }

    private TextView action(String label, int color) {
        TextView view = text(label, 13, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(background(color, dp(12)));
        return view;
    }

    private void addAction(TextView action) {
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(64), dp(42));
        actionParams.leftMargin = dp(6);
        controls.addView(action, actionParams);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
