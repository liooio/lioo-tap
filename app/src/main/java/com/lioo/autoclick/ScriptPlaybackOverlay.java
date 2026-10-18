package com.lioo.autoclick;

import android.graphics.Color;
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

/** Countdown, stop control, and sequential execution for a recorded script. */
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

    private LinearLayout controls;
    private TextView status;
    private TextView start;
    private WindowManager.LayoutParams params;
    private int index;
    private int loopIndex;
    private int remainingCountdown;
    private final int countdownSeconds;
    private final int repeatCount;
    private boolean active;
    private boolean started;
    private boolean paused;
    private long remainingDelay;
    private long delayResumeAt;
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
                ? loopLabel("准备执行")
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
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(64), dp(42));
        startParams.rightMargin = dp(6);
        controls.addView(start, startParams);
        TextView exit = text("停止", 13, Color.WHITE, true);
        exit.setGravity(Gravity.CENTER);
        exit.setBackground(background(0xFF3A4254, dp(12)));
        exit.setOnClickListener(view -> stop(false));
        controls.addView(exit, new LinearLayout.LayoutParams(dp(64), dp(42)));

        int width = Math.min(dp(360),
                service.getResources().getDisplayMetrics().widthPixels - dp(24));
        params = new WindowManager.LayoutParams(
                width, dp(58), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(24);
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
            runNext();
            return;
        }
        status.setText(remaining + " 秒后回放 · " + loopLabel(name));
        handler.postDelayed(() -> countdown(remaining - 1), 1000);
    }

    private void runNext() {
        if (!active || paused) {
            return;
        }
        if (index >= steps.length()) {
            if (repeatCount == 0 || loopIndex + 1 < repeatCount) {
                loopIndex++;
                index = 0;
                status.setText(loopLabel("开始下一轮"));
                handler.post(this::runNext);
                return;
            }
            stop(true);
            return;
        }
        JSONObject step = steps.optJSONObject(index);
        index++;
        if (step == null) {
            runNext();
            return;
        }
        status.setText(loopLabel("执行 " + index + "/" + steps.length()
                + " · " + RecordedScript.label(step)));
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
        accessibility.dispatchRecordedStep(step, this::runNext, () -> {
            Toast.makeText(service, "第 " + index + " 步执行失败", Toast.LENGTH_LONG).show();
            stop(false);
        });
    }

    private void pause() {
        paused = true;
        if (remainingDelay > 0) {
            remainingDelay = Math.max(0,
                    delayResumeAt - SystemClock.uptimeMillis());
        }
        handler.removeCallbacksAndMessages(null);
        start.setText("继续");
        start.setBackground(background(0xFF3A4254, dp(12)));
        status.setText("已暂停 · " + loopLabel(index + "/" + steps.length()));
    }

    private void resume() {
        paused = false;
        start.setText("暂停");
        start.setBackground(background(0xFF1769FF, dp(12)));
        status.setText("继续执行 · " + loopLabel(index + "/" + steps.length()));
        if (remainingCountdown > 0) {
            countdown(remainingCountdown);
        } else if (remainingDelay > 0) {
            delayResumeAt = SystemClock.uptimeMillis() + remainingDelay;
            handler.postDelayed(this::finishDelay, remainingDelay);
        } else {
            handler.post(this::runNext);
        }
    }

    private String loopLabel(String detail) {
        String loops = repeatCount == 0
                ? "无限循环 · 第 " + (loopIndex + 1) + " 轮"
                : "第 " + (loopIndex + 1) + "/" + repeatCount + " 轮";
        return loops + " · " + detail;
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
        Toast.makeText(service, completed ? "录制脚本回放完成" : "已停止回放",
                Toast.LENGTH_SHORT).show();
        listener.onStopped();
    }

    private boolean drag(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
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
}
