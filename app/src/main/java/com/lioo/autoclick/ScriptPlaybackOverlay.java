package com.lioo.autoclick;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
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
import org.json.JSONObject;

/** 管理脚本回放的倒计时、运行控制和顺序执行。 */
final class ScriptPlaybackOverlay {
    interface Listener {
        void onStopped();
    }

    private final OverlayService service;
    private final WindowManager windowManager;
    private final JSONArray steps;
    private final String name;
    private final Listener listener;
    private final boolean autoStart;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable executionTimeTick = this::tickExecutionTime;

    private LinearLayout controls;
    private PlaybackTrailView playbackTrail;
    private TextView status;
    private TextView start;
    private TextView trailToggle;
    private WindowManager.LayoutParams params;
    private int index;
    private int loopIndex;
    private JSONArray repeatedSteps;
    private int repeatedIndex;
    private int repeatedTotal;
    private int repeatedRemaining;
    private int repeatedIntervalMs;
    private int remainingCountdown;
    private final int countdownSeconds;
    private final int repeatCount;
    private boolean active;
    private boolean started;
    private boolean paused;
    private long remainingDelay;
    private long delayResumeAt;
    private long accumulatedExecutionMs;
    private long activeSegmentStartedAt;
    private boolean executionClockStarted;
    private String currentStatusState = "准备执行";
    private JSONObject retryStep;
    private JSONObject inFlightStep;
    private long controlTouchUptime;
    private float dragDownX;
    private float dragDownY;
    private int dragStartX;
    private int dragStartY;

    ScriptPlaybackOverlay(OverlayService service, String body, String name, Listener listener) {
        this(service, body, name, false, listener);
    }

    ScriptPlaybackOverlay(OverlayService service, String body, String name,
            boolean autoStart, Listener listener) {
        this.service = service;
        this.name = name;
        this.autoStart = autoStart;
        this.listener = listener;
        this.countdownSeconds = ClickSettings.playbackCountdownSeconds(service);
        this.repeatCount = ClickSettings.playbackRepeatCount(service);
        windowManager = (WindowManager) service.getSystemService(
                android.content.Context.WINDOW_SERVICE);
        JSONArray parsed;
        try {
            parsed = new JSONArray(body == null ? "[]" : body);
        } catch (org.json.JSONException exception) {
            parsed = new JSONArray();
        }
        steps = parsed;
    }

    void start() {
        if (active) {
            return;
        }
        if (steps.length() == 0) {
            Toast.makeText(service, "录制脚本没有步骤", Toast.LENGTH_SHORT).show();
            listener.onStopped();
            return;
        }
        active = true;
        controls = new LinearLayout(service);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(14), dp(8), dp(8), dp(8));
        controls.setBackground(background(0xF21B1E2C, dp(18)));
        controls.setContentDescription("拖动回放操作栏");
        controls.setOnTouchListener(this::drag);
        status = text(autoStart
                ? executionLabel("准备执行")
                : "等待开始 · " + name, 13, Color.WHITE, true);
        controls.addView(status, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        start = text(autoStart ? "暂停" : "开始", 13, Color.WHITE, true);
        start.setGravity(Gravity.CENTER);
        start.setBackground(background(0xFF1769FF, dp(12)));
        start.setOnClickListener(view -> {
            if (!started) {
                started = true;
                paused = false;
                start.setText("暂停");
                beginRun();
            } else if (paused) {
                resume();
            } else {
                pause();
            }
        });
        protectControlTouch(start);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(64), dp(42));
        startParams.rightMargin = dp(6);
        controls.addView(start, startParams);
        TextView exit = text("停止", 13, Color.WHITE, true);
        exit.setGravity(Gravity.CENTER);
        exit.setBackground(background(0xFF3A4254, dp(12)));
        exit.setOnClickListener(view -> stop(false));
        protectControlTouch(exit);
        controls.addView(exit, new LinearLayout.LayoutParams(dp(64), dp(42)));
        trailToggle = text("轨迹", 13, Color.WHITE, true);
        trailToggle.setGravity(Gravity.CENTER);
        trailToggle.setOnClickListener(view -> togglePlaybackTrail());
        protectControlTouch(trailToggle);
        updateTrailToggleState();
        LinearLayout.LayoutParams trailParams = new LinearLayout.LayoutParams(dp(58), dp(42));
        trailParams.leftMargin = dp(6);
        controls.addView(trailToggle, trailParams);

        int width = Math.min(dp(390),
                service.getResources().getDisplayMetrics().widthPixels - dp(24));
        params = new WindowManager.LayoutParams(
                width, dp(58), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(24);
        showPlaybackTrail();
        windowManager.addView(controls, params);
        if (autoStart) {
            started = true;
            beginRun();
        } else {
            Toast.makeText(service, "请打开目标应用，再点击顶部操作栏的“开始”",
                    Toast.LENGTH_LONG).show();
        }
    }

    void cancel() {
        stop(false);
    }

    private void beginRun() {
        remainingCountdown = countdownSeconds;
        if (remainingCountdown <= 0) {
            startExecutionClock();
            runNext();
            return;
        }
        countdown(remainingCountdown);
    }

    private void countdown(int remaining) {
        if (!active || paused) {
            return;
        }
        remainingCountdown = remaining;
        if (remaining == 0) {
            startExecutionClock();
            runNext();
            return;
        }
        setStatus(remaining + " 秒后执行");
        handler.postDelayed(() -> countdown(remaining - 1), 1000);
    }

    private void runNext() {
        if (!active || paused) {
            return;
        }
        JSONObject step = retryStep;
        retryStep = null;
        if (step == null) {
            step = nextExecutableStep();
        }
        if (step == null) {
            if (repeatCount == 0 || loopIndex + 1 < repeatCount) {
                loopIndex++;
                resetPlanCursor();
                setStatus("继续执行");
                handler.post(this::runNext);
                return;
            }
            stop(true);
            return;
        }
        boolean repeatInterval = step.optBoolean("repeatInterval", false);
        setStatus(repeatInterval ? "等待下一次" : "正在执行");
        if (RecordedScript.DELAY.equals(step.optString("type"))) {
            remainingDelay = Math.max(0,
                    Math.min(600000, step.optLong("duration")));
            delayResumeAt = SystemClock.uptimeMillis() + remainingDelay;
            handler.postDelayed(this::finishDelay, remainingDelay);
            return;
        }
        ClickAccessibilityService accessibility = ClickAccessibilityService.getInstance();
        if (accessibility == null) {
            Toast.makeText(service, "无障碍服务已断开", Toast.LENGTH_LONG).show();
            stop(false);
            return;
        }
        if (playbackTrail != null) {
            playbackTrail.show(step);
        }
        inFlightStep = step;
        JSONObject dispatchedStep = step;
        accessibility.dispatchRecordedStep(step, () -> {
            if (inFlightStep == dispatchedStep) {
                inFlightStep = null;
            }
            runNext();
        }, () -> handleGestureCancellation(dispatchedStep));
    }

    private void handleGestureCancellation(JSONObject cancelledStep) {
        if (!active || inFlightStep != cancelledStep) {
            return;
        }
        inFlightStep = null;
        boolean causedByControls = paused
                || SystemClock.uptimeMillis() - controlTouchUptime < 1000L;
        if (causedByControls) {
            // 触摸悬浮控件会取消正在注入的无障碍手势，因此保留当前逻辑步骤，
            // 避免暂停、轨迹切换或拖动控制栏导致脚本意外终止。
            retryStep = cancelledStep;
            if (!paused) {
                handler.postDelayed(this::runNext, 80L);
            }
            return;
        }
        Toast.makeText(service, "本次执行失败", Toast.LENGTH_LONG).show();
        stop(false);
    }

    private void protectControlTouch(View control) {
        control.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                markControlTouch();
            }
            return false;
        });
    }

    private void markControlTouch() {
        controlTouchUptime = SystemClock.uptimeMillis();
    }

    private JSONObject nextExecutableStep() {
        while (active) {
            if (repeatedSteps != null) {
                if (repeatedIndex < repeatedSteps.length()) {
                    JSONObject nested = repeatedSteps.optJSONObject(repeatedIndex++);
                    if (nested != null) {
                        return nested;
                    }
                    continue;
                }
                if (repeatedRemaining > 1) {
                    repeatedRemaining--;
                    repeatedIndex = 0;
                    if (repeatedIntervalMs > 0) {
                        try {
                            return new JSONObject()
                                    .put("type", RecordedScript.DELAY)
                                    .put("duration", repeatedIntervalMs)
                                    .put("repeatInterval", true);
                        } catch (org.json.JSONException ignored) {
                            continue;
                        }
                    }
                    continue;
                }
                clearRepeatedBlock();
            }
            if (index >= steps.length()) {
                return null;
            }
            JSONObject candidate = steps.optJSONObject(index++);
            if (candidate == null) {
                continue;
            }
            if (!ExecutionPlan.REPEAT_BLOCK.equals(candidate.optString("type"))) {
                return candidate;
            }
            JSONArray nested = candidate.optJSONArray("steps");
            if (nested == null || nested.length() == 0) {
                continue;
            }
            repeatedSteps = nested;
            repeatedIndex = 0;
            repeatedTotal = Math.max(1,
                    Math.min(9999, candidate.optInt("repeat", 1)));
            repeatedRemaining = repeatedTotal;
            repeatedIntervalMs = Math.max(0,
                    Math.min(600000, candidate.optInt("interval", 0)));
        }
        return null;
    }

    private void resetPlanCursor() {
        index = 0;
        clearRepeatedBlock();
    }

    private void clearRepeatedBlock() {
        repeatedSteps = null;
        repeatedIndex = 0;
        repeatedTotal = 0;
        repeatedRemaining = 0;
        repeatedIntervalMs = 0;
    }

    private void pause() {
        pauseExecutionClock();
        paused = true;
        if (remainingDelay > 0) {
            remainingDelay = Math.max(0,
                    delayResumeAt - SystemClock.uptimeMillis());
        }
        handler.removeCallbacksAndMessages(null);
        start.setText("继续");
        start.setBackground(background(0xFF3A4254, dp(12)));
        setStatus("已暂停");
    }

    private void resume() {
        paused = false;
        resumeExecutionClock();
        start.setText("暂停");
        start.setBackground(background(0xFF1769FF, dp(12)));
        setStatus("继续执行");
        if (remainingCountdown > 0) {
            countdown(remainingCountdown);
        } else if (remainingDelay > 0) {
            delayResumeAt = SystemClock.uptimeMillis() + remainingDelay;
            handler.postDelayed(this::finishDelay, remainingDelay);
        } else {
            handler.post(this::runNext);
        }
    }

    private String executionLabel(String state) {
        String count;
        if (repeatedSteps != null && repeatedTotal > 0) {
            int current = repeatedTotal - repeatedRemaining + 1;
            count = current + "/" + repeatedTotal + " 次";
        } else {
            count = repeatCount == 0
                    ? "第 " + (loopIndex + 1) + " 次 · 无限循环"
                    : (loopIndex + 1) + "/" + repeatCount + " 次";
        }
        if (!executionClockStarted) {
            return state + " · " + count;
        }
        return state + " · " + count + " · " + elapsedExecutionMs() / 1000L + "s";
    }

    private void setStatus(String state) {
        currentStatusState = state;
        if (status != null) {
            status.setText(executionLabel(state));
        }
    }

    private void startExecutionClock() {
        if (executionClockStarted) {
            return;
        }
        executionClockStarted = true;
        activeSegmentStartedAt = SystemClock.uptimeMillis();
        scheduleExecutionTimeTick();
    }

    private void pauseExecutionClock() {
        if (!executionClockStarted || activeSegmentStartedAt == 0) {
            return;
        }
        accumulatedExecutionMs += SystemClock.uptimeMillis() - activeSegmentStartedAt;
        activeSegmentStartedAt = 0;
    }

    private void resumeExecutionClock() {
        if (!executionClockStarted) {
            return;
        }
        activeSegmentStartedAt = SystemClock.uptimeMillis();
        scheduleExecutionTimeTick();
    }

    private long elapsedExecutionMs() {
        if (activeSegmentStartedAt == 0) {
            return accumulatedExecutionMs;
        }
        return accumulatedExecutionMs + SystemClock.uptimeMillis() - activeSegmentStartedAt;
    }

    private void tickExecutionTime() {
        if (!active || paused || !executionClockStarted) {
            return;
        }
        setStatus(currentStatusState);
        scheduleExecutionTimeTick();
    }

    private void scheduleExecutionTimeTick() {
        handler.removeCallbacks(executionTimeTick);
        if (!active || paused || !executionClockStarted) {
            return;
        }
        long elapsed = elapsedExecutionMs();
        handler.postDelayed(executionTimeTick, 1000L - elapsed % 1000L);
    }

    private void finishDelay() {
        remainingDelay = 0;
        delayResumeAt = 0;
        runNext();
    }

    private void stop(boolean completed) {
        if (!active) {
            return;
        }
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (controls != null) {
            windowManager.removeView(controls);
            controls = null;
        }
        hidePlaybackTrail();
        Toast.makeText(service, completed ? "录制脚本回放完成" : "已停止回放",
                Toast.LENGTH_SHORT).show();
        listener.onStopped();
    }

    private boolean drag(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            markControlTouch();
            dragDownX = event.getRawX();
            dragDownY = event.getRawY();
            dragStartX = params.x;
            dragStartY = params.y;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            int screenWidth = service.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = service.getResources().getDisplayMetrics().heightPixels;
            int maxX = Math.max(0, (screenWidth - controls.getWidth()) / 2);
            params.x = Math.max(-maxX, Math.min(maxX,
                    dragStartX + Math.round(event.getRawX() - dragDownX)));
            params.y = Math.max(0, Math.min(screenHeight - controls.getHeight(),
                    dragStartY + Math.round(event.getRawY() - dragDownY)));
            windowManager.updateViewLayout(controls, params);
            return true;
        }
        return event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
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

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private void showPlaybackTrail() {
        if (playbackTrail != null || !ClickSettings.playbackShowsTrail(service)) {
            return;
        }
        playbackTrail = new PlaybackTrailView();
        WindowManager.LayoutParams trailParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        trailParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(playbackTrail, trailParams);
    }

    private void hidePlaybackTrail() {
        if (playbackTrail == null) {
            return;
        }
        playbackTrail.cancel();
        windowManager.removeView(playbackTrail);
        playbackTrail = null;
    }

    private void togglePlaybackTrail() {
        boolean show = !ClickSettings.playbackShowsTrail(service);
        ClickSettings.setPlaybackShowsTrail(service, show);
        if (show) {
            showPlaybackTrail();
        } else {
            hidePlaybackTrail();
        }
        updateTrailToggleState();
    }

    private void updateTrailToggleState() {
        if (trailToggle == null) {
            return;
        }
        boolean enabled = ClickSettings.playbackShowsTrail(service);
        trailToggle.setBackground(background(enabled ? 0xFF1769FF : 0xFF3A4254, dp(12)));
        trailToggle.setContentDescription("回放轨迹，当前已" + (enabled ? "开启" : "关闭"));
        trailToggle.setSelected(enabled);
    }

    /** 仅绘制已保存轨迹，不接收触摸，也不改变脚本的回放时序。 */
    private final class PlaybackTrailView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint touchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path source = new Path();
        private final Path visiblePath = new Path();
        private final PathMeasure measure = new PathMeasure();
        private final float[] position = new float[2];
        private ValueAnimator animator;
        private boolean tap;
        private boolean visible;
        private int animationGeneration;
        private float animationProgress;

        PlaybackTrailView() {
            super(service);
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

        void show(JSONObject step) {
            cancel();
            int currentGeneration = ++animationGeneration;
            int width = service.getResources().getDisplayMetrics().widthPixels;
            int height = service.getResources().getDisplayMetrics().heightPixels;
            tap = RecordedScript.TAP.equals(step.optString("type"));
            source.reset();
            if (tap) {
                position[0] = scale(step.optDouble("x"), width);
                position[1] = scale(step.optDouble("y"), height);
                source.moveTo(position[0], position[1]);
            } else {
                JSONArray points = step.optJSONArray("points");
                if (points == null || points.length() < 2) {
                    return;
                }
                for (int i = 0; i < points.length(); i++) {
                    JSONArray point = points.optJSONArray(i);
                    if (point == null || point.length() < 2) {
                        continue;
                    }
                    float x = scale(point.optDouble(0), width);
                    float y = scale(point.optDouble(1), height);
                    if (i == 0) {
                        source.moveTo(x, y);
                    } else {
                        source.lineTo(x, y);
                    }
                }
            }
            measure.setPath(source, false);
            visiblePath.reset();
            visible = true;
            long defaultDuration = tap ? 48L : 180L;
            long duration = Math.max(tap ? 32L : 80L, Math.min(60000L,
                    step.optLong("duration", defaultDuration)));
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(duration);
            animator.addUpdateListener(value -> {
                float progress = (float) value.getAnimatedValue();
                animationProgress = progress;
                visiblePath.reset();
                if (!tap) {
                    float distance = measure.getLength() * progress;
                    measure.getSegment(0, distance, visiblePath, true);
                    measure.getPosTan(distance, position, null);
                }
                invalidate();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    postDelayed(() -> {
                        if (currentGeneration == animationGeneration) {
                            visible = false;
                            invalidate();
                        }
                    }, 180L);
                }
            });
            animator.start();
        }

        void cancel() {
            animationGeneration++;
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            visible = false;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!visible) {
                return;
            }
            if (tap) {
                pulsePaint.setAlpha(Math.max(0,
                        Math.round(210 * (1f - animationProgress))));
                canvas.drawCircle(position[0], position[1],
                        dp(8) + dp(22) * animationProgress, pulsePaint);
                canvas.drawCircle(position[0], position[1], dp(5), touchPaint);
            } else {
                canvas.drawPath(visiblePath, linePaint);
                canvas.drawCircle(position[0], position[1], dp(11), touchPaint);
                canvas.drawCircle(position[0], position[1], dp(4), linePaint);
            }
        }
    }

    private static float scale(double normalized, int size) {
        return (float) Math.max(0, Math.min(size - 1, normalized * size));
    }
}
