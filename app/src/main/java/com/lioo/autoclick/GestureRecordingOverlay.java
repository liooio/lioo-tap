package com.lioo.autoclick;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 全屏触摸录制器，通过无障碍服务将采集完成的手势同步到目标应用。 */
final class GestureRecordingOverlay {
    private static final int MAX_DRAG_POINTS = 120;
    private static final long MIN_POINT_INTERVAL_MS = 32L;
    private static final long INPUT_WINDOW_SETTLE_MS = 32L;

    interface Listener {
        void onClosed(JSONArray steps, boolean save);
    }

    interface FloatingWindowBoundsProvider {
        List<Rect> getBounds();
    }

    private final OverlayService service;
    private final WindowManager windowManager;
    private final WindowManager captureWindowManager;
    private final Listener listener;
    private final FloatingWindowBoundsProvider floatingWindowBoundsProvider;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusUpdate = this::updateStatus;
    private final List<RecordedGesture> recordedGestures = new ArrayList<>();
    private final List<float[]> points = new ArrayList<>();
    private final List<WindowManager.LayoutParams> captureParams = new ArrayList<>();

    private final List<View> captures = new ArrayList<>();
    private GestureTrailView gestureTrail;
    private LinearLayout controls;
    private TextView status;
    private RecordingActionButton primary;
    private RecordingActionButton secondary;
    private RecordingActionButton tertiary;
    private LinearLayout settingsPanel;
    private WindowManager.LayoutParams settingsParams;
    private WindowManager.LayoutParams controlParams;
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
            Listener listener, FloatingWindowBoundsProvider floatingWindowBoundsProvider) {
        this.service = service;
        this.listener = listener;
        this.floatingWindowBoundsProvider = floatingWindowBoundsProvider;
        windowManager = (WindowManager) service.getSystemService(
                Context.WINDOW_SERVICE);
        // Android 12 及以上版本允许受信任的无障碍悬浮窗传递触摸。
        captureWindowManager = (WindowManager) accessibility.getSystemService(
                Context.WINDOW_SERVICE);
    }

    void start() {
        if (active) {
            return;
        }
        active = true;
        controls = new LinearLayout(service);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        controls.setPadding(dp(8), dp(6), dp(8), dp(8));
        controls.setBackground(background(0xF21B1E2C, dp(20)));
        controls.setContentDescription("拖动录制操作栏");
        View dragHandle = recordingDragHandle();
        controls.addView(dragHandle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(18)));
        status = text("准备 · 0 步", 10, Color.WHITE, true);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(2);
        status.setIncludeFontPadding(false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(30));
        statusParams.bottomMargin = dp(4);
        controls.addView(status, statusParams);
        primary = action(R.drawable.ic_clicker_play, "开始", "开始录制");
        primary.view.setOnClickListener(view -> {
            if (recording) {
                pauseRecording();
            } else {
                startOrResumeRecording();
            }
        });
        secondary = action(R.drawable.ic_clicker_exit, "取消", "取消录制");
        secondary.view.setOnClickListener(view -> stop(false));
        tertiary = action(R.drawable.ic_record_done, "完成", "完成录制");
        tertiary.view.setVisibility(View.GONE);
        tertiary.view.setOnClickListener(view -> completeRecording());
        addAction(primary);
        addAction(secondary);
        addAction(tertiary);
        RecordingActionButton settings = action(
                R.drawable.ic_record_settings, "设置", "录制设置");
        settings.view.setOnClickListener(view -> showPlaybackSettings());
        addAction(settings);

        controlParams = new WindowManager.LayoutParams(
                dp(64), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        controlParams.y = dp(24);
        float defaultY = dp(24)
                / (float) Math.max(1, service.getResources().getDisplayMetrics().heightPixels
                        - dp(260));
        FloatingWindowDragController.restorePosition(service, controls, controlParams,
                "recording_controls", 0.5f, defaultY);
        windowManager.addView(controls, controlParams);
        FloatingWindowDragController.create(service, windowManager,
                controls, controlParams, "recording_controls", 0.5f, defaultY,
                null, this::onControlsDragFinished, dragHandle);
        Toast.makeText(service, "请打开目标应用，再点击顶部操作栏的“开始”",
                Toast.LENGTH_LONG).show();
    }

    void cancel() {
        stop(false);
    }

    void refreshCaptureRegions() {
        if (!active || !recording) {
            return;
        }
        handler.post(() -> {
            if (active && recording) {
                removeCapture();
                ensureCapture();
            }
        });
    }

    private void startOrResumeRecording() {
        if (!active || recording || replaying) {
            return;
        }
        recording = true;
        lastInjectedAt = SystemClock.uptimeMillis();
        pendingDelayMs = 0;
        setAction(primary, R.drawable.ic_record_pause, "暂停", "暂停录制");
        primary.view.setAlpha(1f);
        updateStatus();
        // 等待“开始”按钮完成当前触摸序列后再创建捕获窗口。
        handler.post(() -> {
            if (active && recording) {
                if (ClickSettings.recordingShowsTrail(service)) {
                    showGestureTrail();
                }
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
        hideGestureTrail();
        setAction(primary, R.drawable.ic_clicker_play, "开始", "继续录制");
        primary.view.setAlpha(1f);
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
            primary.view.setEnabled(false);
            primary.view.setAlpha(0.45f);
            return;
        }
        if (recordedGestures.isEmpty()) {
            Toast.makeText(service, "还没有录制任何操作", Toast.LENGTH_SHORT).show();
            return;
        }
        recording = false;
        removeCapture();
        hideGestureTrail();
        showCompletedActions();
    }

    private void showCompletedActions() {
        replaying = false;
        hideGestureTrail();
        status.setText("录制完成 · " + actionCount() + " 步");
        setAction(primary, R.drawable.ic_clicker_play, "回放", "回放录制");
        primary.view.setEnabled(true);
        primary.view.setAlpha(1f);
        primary.view.setOnClickListener(view -> startReplay());
        setAction(secondary, R.drawable.ic_clicker_exit, "取消", "取消录制");
        secondary.view.setEnabled(true);
        secondary.view.setAlpha(1f);
        secondary.view.setOnClickListener(view -> stop(false));
        setAction(tertiary, R.drawable.ic_record_save, "保存", "保存录制");
        tertiary.view.setVisibility(View.VISIBLE);
        tertiary.view.setEnabled(true);
        tertiary.view.setAlpha(1f);
        tertiary.view.setOnClickListener(view -> stop(true));
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
        setAction(primary, R.drawable.ic_clicker_stop, "停止", "停止回放");
        primary.view.setOnClickListener(view -> stopReplay());
        secondary.view.setAlpha(0.45f);
        secondary.view.setEnabled(false);
        tertiary.view.setAlpha(0.45f);
        tertiary.view.setEnabled(false);
        if (ClickSettings.playbackShowsTrail(service)) {
            showGestureTrail();
        }
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
        if (gestureTrail != null) {
            gestureTrail.play(step);
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
        int screenWidth = service.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = service.getResources().getDisplayMetrics().heightPixels;
        List<Rect> exclusions = new ArrayList<>();
        addExclusion(exclusions, controls, screenWidth, screenHeight);
        if (floatingWindowBoundsProvider != null) {
            for (Rect bounds : floatingWindowBoundsProvider.getBounds()) {
                addExclusion(exclusions, bounds, screenWidth, screenHeight);
            }
        }

        List<Rect> regions = new ArrayList<>();
        regions.add(new Rect(0, 0, screenWidth, screenHeight));
        for (Rect exclusion : exclusions) {
            subtractRect(regions, exclusion);
        }
        for (Rect region : regions) {
            addCaptureRegion(region.left, region.top, region.width(), region.height());
        }
        captureAdded = !captures.isEmpty();
    }

    private void addExclusion(List<Rect> exclusions, View view,
            int screenWidth, int screenHeight) {
        if (view == null || !view.isAttachedToWindow()
                || view.getVisibility() != View.VISIBLE) {
            return;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        addExclusion(exclusions, new Rect(location[0], location[1],
                location[0] + view.getWidth(), location[1] + view.getHeight()),
                screenWidth, screenHeight);
    }

    private void addExclusion(List<Rect> exclusions, Rect bounds,
            int screenWidth, int screenHeight) {
        Rect safeBounds = new Rect(bounds);
        safeBounds.inset(-dp(2), -dp(2));
        if (safeBounds.intersect(0, 0, screenWidth, screenHeight)) {
            exclusions.add(safeBounds);
        }
    }

    /** 把窗口触摸区域从屏幕捕获区切除，点按和拖动都交回悬浮窗本身处理。 */
    private void subtractRect(List<Rect> regions, Rect exclusion) {
        List<Rect> remaining = new ArrayList<>();
        for (Rect region : regions) {
            Rect intersection = new Rect(region);
            if (!intersection.intersect(exclusion)) {
                remaining.add(region);
                continue;
            }
            if (region.top < intersection.top) {
                remaining.add(new Rect(region.left, region.top,
                        region.right, intersection.top));
            }
            if (intersection.bottom < region.bottom) {
                remaining.add(new Rect(region.left, intersection.bottom,
                        region.right, region.bottom));
            }
            if (region.left < intersection.left) {
                remaining.add(new Rect(region.left, intersection.top,
                        intersection.left, intersection.bottom));
            }
            if (intersection.right < region.right) {
                remaining.add(new Rect(intersection.right, intersection.top,
                        region.right, intersection.bottom));
            }
        }
        regions.clear();
        regions.addAll(remaining);
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

    private void showPlaybackSettings() {
        if (settingsPanel != null || replaying) {
            return;
        }
        removeCapture();
        if (gestureTrail != null) {
            gestureTrail.clear();
        }
        settingsPanel = new LinearLayout(service);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(18), dp(16), dp(18), dp(16));
        settingsPanel.setBackground(background(0xFA1B1E2C, dp(18)));
        LinearLayout settingsHeader = new LinearLayout(service);
        settingsHeader.setGravity(Gravity.CENTER_VERTICAL);
        settingsHeader.setContentDescription("拖动回放设置弹窗");
        settingsHeader.addView(text("回放基础设置", 16, Color.WHITE, true));
        settingsPanel.addView(settingsHeader);
        TextView hint = text("延时 0 秒立即开始，次数 0 表示无限循环", 12,
                0xFFBCC6D8, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(4);
        hintParams.bottomMargin = dp(10);
        settingsPanel.addView(hint, hintParams);

        TextView delayValue = valueText(ClickSettings.playbackCountdownSeconds(service));
        TextView repeatValue = valueText(ClickSettings.playbackRepeatCount(service));
        TextView recordingTrailValue = toggleValue(
                ClickSettings.recordingShowsTrail(service));
        TextView playbackTrailValue = toggleValue(
                ClickSettings.playbackShowsTrail(service));
        settingsPanel.addView(settingRow("开始延时（秒）", delayValue, 0, 10));
        settingsPanel.addView(settingRow("回放次数", repeatValue, 0, 9999));
        settingsPanel.addView(toggleRow("录制显示轨迹", recordingTrailValue));
        settingsPanel.addView(toggleRow("回放显示轨迹", playbackTrailValue));

        LinearLayout actions = new LinearLayout(service);
        actions.setGravity(Gravity.END);
        TextView cancel = action("取消", 0xFF3A4254);
        cancel.setOnClickListener(view -> closePlaybackSettings(
                false, delayValue, repeatValue, recordingTrailValue, playbackTrailValue));
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(72), dp(42)));
        TextView save = action("保存", 0xFF1769FF);
        save.setOnClickListener(view -> closePlaybackSettings(
                true, delayValue, repeatValue, recordingTrailValue, playbackTrailValue));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(dp(72), dp(42));
        saveParams.leftMargin = dp(8);
        actions.addView(save, saveParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(12);
        settingsPanel.addView(actions, actionsParams);

        settingsParams = new WindowManager.LayoutParams(
                Math.min(dp(330), service.getResources().getDisplayMetrics().widthPixels - dp(32)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        settingsParams.gravity = Gravity.CENTER;
        FloatingWindowDragController.restorePosition(service, settingsPanel, settingsParams,
                "recording_settings", 0.5f, 0.5f);
        windowManager.addView(settingsPanel, settingsParams);
        FloatingWindowDragController.attach(service, windowManager, settingsPanel,
                settingsHeader, settingsParams, "recording_settings", 0.5f, 0.5f);
    }

    private LinearLayout settingRow(String label, TextView value, int min, int max) {
        LinearLayout row = new LinearLayout(service);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(text(label, 13, Color.WHITE, true), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView minus = action("−", 0xFF3A4254);
        Runnable decrement = () -> adjustValue(value, -1, min, max);
        minus.setOnClickListener(view -> decrement.run());
        LongPressStepper.attach(minus, decrement);
        row.addView(minus, new LinearLayout.LayoutParams(dp(42), dp(38)));
        row.addView(value, new LinearLayout.LayoutParams(dp(64), dp(38)));
        TextView plus = action("+", 0xFF3A4254);
        Runnable increment = () -> adjustValue(value, 1, min, max);
        plus.setOnClickListener(view -> increment.run());
        LongPressStepper.attach(plus, increment);
        row.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(38)));
        return row;
    }

    private TextView valueText(int value) {
        TextView view = text(String.valueOf(value), 15, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView toggleValue(boolean enabled) {
        TextView view = action(enabled ? "开启" : "关闭",
                enabled ? 0xFF1769FF : 0xFF3A4254);
        view.setTag(enabled);
        view.setOnClickListener(ignored -> {
            boolean next = !Boolean.TRUE.equals(view.getTag());
            view.setTag(next);
            view.setText(next ? "开启" : "关闭");
            view.setBackground(background(next ? 0xFF1769FF : 0xFF3A4254, dp(12)));
        });
        return view;
    }

    private LinearLayout toggleRow(String label, TextView value) {
        LinearLayout row = new LinearLayout(service);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(text(label, 13, Color.WHITE, true), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(value, new LinearLayout.LayoutParams(dp(72), dp(38)));
        return row;
    }

    private void adjustValue(TextView value, int delta, int min, int max) {
        int current;
        try {
            current = Integer.parseInt(value.getText().toString());
        } catch (NumberFormatException ignored) {
            current = min;
        }
        value.setText(String.valueOf(Math.max(min, Math.min(max, current + delta))));
    }

    private void closePlaybackSettings(boolean save, TextView delay, TextView repeat,
            TextView recordingTrail, TextView playbackTrail) {
        if (save) {
            ClickSettings.savePlayback(service,
                    Integer.parseInt(delay.getText().toString()),
                    Integer.parseInt(repeat.getText().toString()),
                    Boolean.TRUE.equals(recordingTrail.getTag()),
                    Boolean.TRUE.equals(playbackTrail.getTag()));
            Toast.makeText(service, "回放设置已保存", Toast.LENGTH_SHORT).show();
        }
        if (settingsPanel != null) {
            windowManager.removeView(settingsPanel);
            settingsPanel = null;
            settingsParams = null;
        }
        if (active && recording) {
            if (ClickSettings.recordingShowsTrail(service)) {
                showGestureTrail();
            } else {
                hideGestureTrail();
            }
            ensureCapture();
        }
    }

    private void showGestureTrail() {
        if (gestureTrail != null) {
            return;
        }
        gestureTrail = new GestureTrailView(service);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        captureWindowManager.addView(gestureTrail, params);
    }

    private void hideGestureTrail() {
        if (gestureTrail == null) {
            return;
        }
        captureWindowManager.removeViewImmediate(gestureTrail);
        gestureTrail = null;
    }

    private boolean onTouch(View view, MotionEvent event) {
        if (!active || !recording || injecting) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            ignoreTouchSequence = isInsideFloatingWindow(event.getRawX(), event.getRawY())
                    || event.getPointerCount() != 1;
            if (ignoreTouchSequence) {
                points.clear();
                if (gestureTrail != null) {
                    gestureTrail.clear();
                }
                return true;
            }
            long now = SystemClock.uptimeMillis();
            pendingDelayMs = lastInjectedAt > 0 ? Math.max(0, now - lastInjectedAt) : 0;
            points.clear();
            points.add(new float[] {event.getRawX(), event.getRawY()});
            if (gestureTrail != null) {
                gestureTrail.start(event.getRawX(), event.getRawY());
            }
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
            if (gestureTrail != null) {
                gestureTrail.clear();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            addPoint(event.getRawX(), event.getRawY(), event.getEventTime(), false);
            if (gestureTrail != null) {
                gestureTrail.add(event.getRawX(), event.getRawY());
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            addPoint(event.getRawX(), event.getRawY(), event.getEventTime(), true);
            if (gestureTrail != null) {
                gestureTrail.add(event.getRawX(), event.getRawY());
            }
            recordGesture(Math.max(1L, event.getEventTime() - downTime));
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            points.clear();
            if (gestureTrail != null) {
                gestureTrail.clear();
            }
            return true;
        }
        return true;
    }

    private boolean isInsideFloatingWindow(float screenX, float screenY) {
        if (contains(controls, screenX, screenY)) {
            return true;
        }
        if (floatingWindowBoundsProvider == null) {
            return false;
        }
        for (Rect bounds : floatingWindowBoundsProvider.getBounds()) {
            if (bounds.contains((int) screenX, (int) screenY)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(View view, float screenX, float screenY) {
        if (view == null || !view.isAttachedToWindow()
                || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return screenX >= location[0] && screenX < location[0] + view.getWidth()
                && screenY >= location[1] && screenY < location[1] + view.getHeight();
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
                // 长距离拖动达到采样上限后持续更新终点，限制内存占用。
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
        // 立即移除输入窗口。仅修改四个窗口的可触摸状态会产生过渡期，
        // 下一次点击仍可能被发送到当前监听器。
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
            if (gestureTrail != null) {
                gestureTrail.finish(gesture.tap);
            }
            // 以捕获到的手势作为录制结果。目标应用可能取消无障碍镜像，
            // 长距离拖动尤其常见，但不能因此丢弃已经完整采集的步骤。
            recordedGestures.add(gesture);
            recordedActionCount++;
            updateStatus();
            // 等待约两个显示帧，让 InputDispatcher 感知窗口移除，
            // 同时避免每次操作都产生原先固定的 100 毫秒停顿。
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
        // 录制阶段已经消耗了用户真实操作的时间，因此压缩即时镜像时长，
        // 避免慢速拖动继续阻塞输入；保存的步骤仍保留原始时长用于正式回放。
        long mirrorDuration = gesture.tap ? gesture.duration
                : Math.min(gesture.duration, 120L);
        long timeoutMs = Math.max(2000L, mirrorDuration + 2000L);
        handler.postDelayed(() -> {
            if (active && injecting && currentMirror == mirrorGeneration) {
                restoreCapture(currentMirror);
                Toast.makeText(service, "手势回放超时，已恢复录制", Toast.LENGTH_SHORT).show();
            }
        }, timeoutMs);
        accessibility.dispatchRecordedPath(gesture.path, gesture.tap, mirrorDuration,
                () -> {
                    if (currentMirror != mirrorGeneration) {
                        return;
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
                            primary.view.setEnabled(true);
                            primary.view.setAlpha(1f);
                            ensureCapture();
                        } else {
                            showCompletedActions();
                            return;
                        }
                    }
                    if (active && recording) {
                        Toast.makeText(service, gesture.tap
                                        ? "点击已记录，但目标界面未响应"
                                        : "滑动已记录，但目标界面未响应",
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
                // 固定字段名和基础类型在正常情况下不会写入失败。
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
        if (tertiary != null && !replaying) {
            tertiary.view.setVisibility(actionCount() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private int actionCount() {
        return recordedActionCount;
    }

    private void onControlsDragFinished() {
        if (recording) {
            handler.post(() -> {
                if (active && recording) {
                    removeCapture();
                    ensureCapture();
                }
            });
        }
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
        hideGestureTrail();
        if (settingsPanel != null) {
            windowManager.removeView(settingsPanel);
            settingsPanel = null;
            settingsParams = null;
        }
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

    private static float scale(double normalized, int size) {
        return (float) Math.max(0, Math.min(size - 1, normalized * size));
    }

    /** 仅提供手势视觉反馈，不接收也不阻挡触摸输入。 */
    private final class GestureTrailView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint touchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path trail = new Path();
        private final Path playbackSource = new Path();
        private final PathMeasure playbackMeasure = new PathMeasure();
        private final float[] playbackPosition = new float[2];
        private final Runnable clearTrail = this::clear;
        private float touchX;
        private float touchY;
        private boolean visible;
        private boolean tapPulse;
        private float pulseProgress;
        private ValueAnimator pulseAnimator;
        private ValueAnimator playbackAnimator;

        GestureTrailView(Context context) {
            super(context);
            linePaint.setColor(Color.argb(170, 255, 45, 45));
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(dp(5));
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            touchPaint.setColor(Color.argb(105, 255, 35, 35));
            touchPaint.setStyle(Paint.Style.FILL);
            pulsePaint.setColor(Color.rgb(255, 45, 45));
            pulsePaint.setStyle(Paint.Style.STROKE);
            pulsePaint.setStrokeWidth(dp(3));
        }

        void start(float x, float y) {
            removeCallbacks(clearTrail);
            cancelAnimations();
            trail.reset();
            trail.moveTo(x, y);
            touchX = x;
            touchY = y;
            visible = true;
            tapPulse = false;
            invalidate();
        }

        void add(float x, float y) {
            if (!visible) {
                start(x, y);
                return;
            }
            trail.lineTo(x, y);
            touchX = x;
            touchY = y;
            invalidate();
        }

        void finish(boolean tap) {
            removeCallbacks(clearTrail);
            if (!tap) {
                postDelayed(clearTrail, 180L);
                return;
            }
            tapPulse = true;
            trail.reset();
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
            }
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
            pulseAnimator.setDuration(320L);
            pulseAnimator.addUpdateListener(value -> {
                pulseProgress = (float) value.getAnimatedValue();
                invalidate();
            });
            pulseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    clear();
                }
            });
            pulseAnimator.start();
        }

        void clear() {
            visible = false;
            tapPulse = false;
            trail.reset();
            invalidate();
        }

        void play(JSONObject step) {
            removeCallbacks(clearTrail);
            cancelAnimations();
            int width = service.getResources().getDisplayMetrics().widthPixels;
            int height = service.getResources().getDisplayMetrics().heightPixels;
            boolean playbackTap = RecordedScript.TAP.equals(step.optString("type"));
            playbackSource.reset();
            if (playbackTap) {
                playbackPosition[0] = scale(step.optDouble("x"), width);
                playbackPosition[1] = scale(step.optDouble("y"), height);
                playbackSource.moveTo(playbackPosition[0], playbackPosition[1]);
            } else {
                JSONArray playbackPoints = step.optJSONArray("points");
                if (playbackPoints == null || playbackPoints.length() < 2) {
                    return;
                }
                for (int i = 0; i < playbackPoints.length(); i++) {
                    JSONArray point = playbackPoints.optJSONArray(i);
                    if (point == null || point.length() < 2) {
                        continue;
                    }
                    float x = scale(point.optDouble(0), width);
                    float y = scale(point.optDouble(1), height);
                    if (i == 0) {
                        playbackSource.moveTo(x, y);
                    } else {
                        playbackSource.lineTo(x, y);
                    }
                }
            }
            playbackMeasure.setPath(playbackSource, false);
            trail.reset();
            visible = true;
            tapPulse = playbackTap;
            long defaultDuration = playbackTap ? 48L : 180L;
            long duration = Math.max(playbackTap ? 32L : 80L, Math.min(60000L,
                    step.optLong("duration", defaultDuration)));
            playbackAnimator = ValueAnimator.ofFloat(0f, 1f);
            playbackAnimator.setDuration(duration);
            playbackAnimator.addUpdateListener(value -> {
                float progress = (float) value.getAnimatedValue();
                pulseProgress = progress;
                if (!playbackTap) {
                    float distance = playbackMeasure.getLength() * progress;
                    trail.reset();
                    playbackMeasure.getSegment(0, distance, trail, true);
                    playbackMeasure.getPosTan(distance, playbackPosition, null);
                    touchX = playbackPosition[0];
                    touchY = playbackPosition[1];
                } else {
                    touchX = playbackPosition[0];
                    touchY = playbackPosition[1];
                }
                invalidate();
            });
            playbackAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    postDelayed(clearTrail, 180L);
                }
            });
            playbackAnimator.start();
        }

        private void cancelAnimations() {
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
                pulseAnimator = null;
            }
            if (playbackAnimator != null) {
                playbackAnimator.cancel();
                playbackAnimator = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!visible) {
                return;
            }
            if (tapPulse) {
                pulsePaint.setAlpha(Math.max(0, Math.round(210 * (1f - pulseProgress))));
                canvas.drawCircle(touchX, touchY,
                        dp(8) + dp(22) * pulseProgress, pulsePaint);
                canvas.drawCircle(touchX, touchY, dp(5), touchPaint);
            } else {
                canvas.drawPath(trail, linePaint);
                canvas.drawCircle(touchX, touchY, dp(11), touchPaint);
                canvas.drawCircle(touchX, touchY, dp(4), linePaint);
            }
        }
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

    private LinearLayout recordingDragHandle() {
        LinearLayout handle = new LinearLayout(service);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("拖动录制操作栏");
        View grip = new View(service);
        grip.setBackground(background(0xFF687084, dp(2)));
        handle.addView(grip, new LinearLayout.LayoutParams(dp(28), dp(4)));
        return handle;
    }

    private RecordingActionButton action(int iconResource, String label,
            String description) {
        LinearLayout container = new LinearLayout(service);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setBackground(background(0xFF252D3E, dp(12)));
        container.setContentDescription(description);
        container.setClickable(true);
        container.setFocusable(true);

        ImageView icon = new ImageView(service);
        icon.setImageResource(iconResource);
        icon.setColorFilter(Color.WHITE);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        container.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView labelView = text(label, 9, Color.WHITE, true);
        labelView.setGravity(Gravity.CENTER);
        labelView.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(1);
        container.addView(labelView, labelParams);
        return new RecordingActionButton(container, icon, labelView);
    }

    private TextView action(String label, int color) {
        TextView view = text(label, 13, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(background(color, dp(12)));
        return view;
    }

    private void setAction(RecordingActionButton action, int iconResource, String label,
            String description) {
        action.icon.setImageResource(iconResource);
        action.label.setText(label);
        action.view.setContentDescription(description);
    }

    private void addAction(RecordingActionButton action) {
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        actionParams.topMargin = dp(4);
        controls.addView(action.view, actionParams);
    }

    private static final class RecordingActionButton {
        final LinearLayout view;
        final ImageView icon;
        final TextView label;

        RecordingActionButton(LinearLayout view, ImageView icon, TextView label) {
            this.view = view;
            this.icon = icon;
            this.label = label;
        }
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
