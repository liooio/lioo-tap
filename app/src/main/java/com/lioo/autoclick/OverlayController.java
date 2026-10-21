package com.lioo.autoclick;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class OverlayController {
    private static final long BALL_COLLAPSE_DELAY_MS = 1800L;
    private static final String LOGO_POSITION_KEY = "logo_ball_center";

    private final OverlayService service;
    private final WindowManager windowManager;
    private final DisplayMetrics displayMetrics;
    private final ClickEditorOverlay clickEditor;
    private final ClickCountOverlay clickCount;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable executionTimeTick = this::updateExecutionTime;
    private final Runnable collapseBallTask = this::collapseFloatingBall;
    private ClickerToolboxView clickerPanel;
    private View toolboxMask;
    private OutsideTouchLayout controls;
    private LogoBallView toolbox;
    private DragHandle target;
    private TextView counter;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams controlsParams;
    private WindowManager.LayoutParams targetParams;
    private WindowManager.LayoutParams counterParams;
    private float targetDefaultXRatio;
    private float targetDefaultYRatio;
    private final List<FloatingShortcut> floatingShortcuts = new ArrayList<>();
    private boolean ballOnRight = false;
    private float ballYRatio = 0.5f;
    private boolean ballCollapsed;
    private boolean ballWasCollapsedOnTouch;
    private int ballDragStartY;
    private boolean clickerRunning;
    private long clickerStartedAt;
    private int completedClicks;
    private int totalClicks;

    OverlayController(OverlayService service) {
        this.service = service;
        windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        displayMetrics = service.getResources().getDisplayMetrics();
        clickEditor = new ClickEditorOverlay(service, windowManager, displayMetrics,
                this::onOutsideTouch, this::returnToClicker);
        clickCount = new ClickCountOverlay(service, windowManager, displayMetrics,
                this::onOutsideTouch);
    }

    boolean isVisible() {
        return toolbox != null || controls != null;
    }

    boolean hasTarget() {
        return target != null && target.getVisibility() == View.VISIBLE;
    }

    List<Rect> getInteractiveFloatingBounds() {
        List<Rect> bounds = new ArrayList<>();
        addViewBounds(bounds, toolbox);
        addViewBounds(bounds, controls);
        addViewBounds(bounds, target);
        for (FloatingShortcut shortcut : floatingShortcuts) {
            addViewBounds(bounds, shortcut.view);
        }
        return bounds;
    }

    private void addViewBounds(List<Rect> bounds, View view) {
        if (view == null || !view.isAttachedToWindow()
                || view.getVisibility() != View.VISIBLE) {
            return;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        bounds.add(new Rect(location[0], location[1],
                location[0] + view.getWidth(), location[1] + view.getHeight()));
    }

    void show() {
        if (isVisible()) {
            return;
        }
        int targetSize = dp(40);
        ballOnRight = FloatingWindowDragController.edgeOnRight(
                service, LOGO_POSITION_KEY, false);
        ballYRatio = FloatingWindowDragController.edgeYRatio(
                service, LOGO_POSITION_KEY, 0.5f);
        toolbox = new LogoBallView(service);
        toolbox.setContentDescription("悬浮窗，点击打开工具箱");
        toolbox.setOnClickListener(view -> {
            if (controls == null) {
                showPanel();
            } else {
                hidePanel();
            }
        });
        ballParams = params(targetSize, targetSize,
                collapsedEdgeX(targetSize),
                ballY(targetSize));
        // 半隐藏需要允许窗口的一部分位于屏幕边界外。
        ballParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        ballParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        toolbox.setOnTouchListener(FloatingWindowDragController.createWithStrategy(
                service, windowManager, toolbox, ballParams,
                () -> {
                    ballWasCollapsedOnTouch = ballCollapsed;
                    expandFloatingBall();
                    ballDragStartY = ballParams.y;
                }, new FloatingWindowDragController.PositionStrategy() {
                    @Override
                    public void move(float rawX, float deltaX, float deltaY) {
                        ballOnRight = rawX >= screenWidth() / 2f;
                        ballParams.x = expandedEdgeX(toolbox.getWidth());
                        ballParams.y = Math.max(0, Math.min(
                                screenHeight() - toolbox.getHeight(),
                                ballDragStartY + Math.round(deltaY)));
                    }

                    @Override
                    public void finish(boolean moved) {
                        if (controls == null) {
                            saveBallPosition();
                            scheduleBallCollapse();
                        }
                        service.refreshRecordingCapture();
                    }
                }, () -> {
                    if (!ballWasCollapsedOnTouch) {
                        toolbox.performClick();
                    }
                }));
        windowManager.addView(toolbox, ballParams);
        ballCollapsed = true;
        toolbox.post(this::snapFloatingBallToEdge);
        refreshShortcutKeys();
    }

    void snapFloatingBallToEdge() {
        if (toolbox == null) {
            return;
        }
        // 屏幕旋转后重新贴边，避免原来的右边缘位置落到新屏幕中央。
        ballParams.x = ballCollapsed
                ? collapsedEdgeX(ballParams.width) : expandedEdgeX(ballParams.width);
        ballParams.y = ballY(ballParams.height);
        windowManager.updateViewLayout(toolbox, ballParams);
        if (controls != null) {
            if (target == null) {
                windowManager.removeView(controls);
                removeToolboxMask();
                controls = null;
                clickerPanel = null;
                controlsParams = null;
                showPanel();
                return;
            }
            controlsParams.width = Math.min(dp(300), screenWidth() - dp(32));
            controlsParams.x = (screenWidth() - controlsParams.width) / 2;
            controlsParams.y = dp(32);
            windowManager.updateViewLayout(controls, controlsParams);
        }
        refreshShortcutKeys();
    }

    private void showPanel() {
        if (controls != null) {
            return;
        }
        expandFloatingBall();
        int panelWidth = pickerWidth();
        int panelHeight = pickerHeight();
        List<Library.Entry> workflows = Library.list(service, Library.GROUP);
        List<Library.Entry> toolboxShortcuts = new ArrayList<>();
        for (Library.Entry entry : Library.list(service, Library.KEY)) {
            if (ShortcutKey.TOOLBOX.equals(ShortcutKey.displayMode(entry))) {
                toolboxShortcuts.add(entry);
            }
        }
        clickerPanel = new ClickerToolboxView(service, displayMetrics,
                this::onOutsideTouch, this::showClicker, service::startRecording,
                this::toggleClickEditor, this::onRunPressed, this::hidePanel,
                this::resetAllFloatingPositions,
                workflows, service::runEntry, toolboxShortcuts, service::runShortcut);
        controls = clickerPanel.view();
        controlsParams = params(panelWidth, panelHeight,
                pickerX(panelWidth), pickerY(panelHeight));
        controlsParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        FloatingWindowDragController.restorePosition(service, controls, controlsParams,
                "toolbox_panel", 0.5f, 0.5f);
        showToolboxMask();
        windowManager.addView(controls, controlsParams);
        clickerPanel.setDragListener(FloatingWindowDragController.create(service, windowManager,
                controls, controlsParams, "toolbox_panel", 0.5f, 0.5f,
                null, null, clickerPanel.dragHandles()));
        updateToolboxState();
    }

    private void showClicker() {
        if (controls == null || target != null) {
            return;
        }
        // 连点器需要操作下层应用，进入配置后不再拦截屏幕其余区域。
        removeToolboxMask();
        int panelWidth = Math.min(dp(ClickerToolboxView.CLICKER_PANEL_WIDTH_DP),
                screenWidth() - dp(32));
        int panelHeight = dp(ClickerToolboxView.CLICKER_PANEL_HEIGHT_DP);
        controlsParams.width = panelWidth;
        controlsParams.height = panelHeight;
        controlsParams.x = dp(ClickerToolboxView.CLICKER_PANEL_LEFT_MARGIN_DP);
        controlsParams.y = dp(32);
        clickerPanel.showClicker();
        float defaultY = dp(32)
                / (float) Math.max(1, screenHeight() - panelHeight);
        FloatingWindowDragController.restorePosition(service, controls, controlsParams,
                ClickerToolboxView.CLICKER_POSITION_KEY,
                ClickerToolboxView.CLICKER_PANEL_LEFT_MARGIN_DP
                        / (float) Math.max(1, screenWidth() - panelWidth),
                defaultY);
        windowManager.updateViewLayout(controls, controlsParams);
        clickerPanel.setActionDragListener(FloatingWindowDragController.create(
                service, windowManager, controls, controlsParams,
                ClickerToolboxView.CLICKER_POSITION_KEY,
                ClickerToolboxView.CLICKER_PANEL_LEFT_MARGIN_DP
                        / (float) Math.max(1, screenWidth() - panelWidth),
                defaultY, null, null));

        int targetSize = dp(56);
        target = makeChip("◎", 23, Color.WHITE, 0xDD2563EB);
        target.setBackground(circleBackground(0xDD2563EB));
        target.setContentDescription("点击位置，拖动可调整");
        target.setVisibility(View.VISIBLE);
        target.setGravity(Gravity.CENTER);
        int[] targetPosition = initialTargetPosition(targetSize, controlsParams);
        targetParams = params(targetSize, targetSize, targetPosition[0], targetPosition[1]);
        targetDefaultXRatio = targetParams.x
                / (float) Math.max(1, screenWidth() - targetSize);
        targetDefaultYRatio = targetParams.y
                / (float) Math.max(1, screenHeight() - targetSize);
        FloatingWindowDragController.restorePosition(service, target, targetParams,
                "click_target", targetDefaultXRatio, targetDefaultYRatio);
        windowManager.addView(target, targetParams);
        target.setOnTouchListener(FloatingWindowDragController.createWithPositionListener(
                service, windowManager,
                target, targetParams, "click_target", targetDefaultXRatio, targetDefaultYRatio,
                null, this::updateCounterDuringTargetDrag, null, target));

        counter = makeChip("0/0", 12, Color.WHITE, 0xE61B1F27);
        counter.setGravity(Gravity.CENTER);
        counter.setVisibility(View.GONE);
        counterParams = params(dp(112), dp(32), 0, 0);
        counterParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        updateCounterPosition();
        windowManager.addView(counter, counterParams);
    }

    private void resetAllFloatingPositions() {
        FloatingWindowDragController.resetAllPositions(service);

        if (toolbox != null && ballParams != null) {
            handler.removeCallbacks(collapseBallTask);
            ballOnRight = false;
            ballYRatio = 0.5f;
            ballCollapsed = true;
            FloatingWindowDragController.resetEdgePosition(
                    service, LOGO_POSITION_KEY, false, 0.5f);
            ballParams.x = collapsedEdgeX(ballParams.width);
            ballParams.y = ballY(ballParams.height);
            windowManager.updateViewLayout(toolbox, ballParams);
        }

        if (controls != null && controlsParams != null) {
            if (target == null) {
                FloatingWindowDragController.resetPosition(service, windowManager,
                        controls, controlsParams, "toolbox_panel", 0.5f, 0.5f);
            } else {
                float defaultY = dp(32)
                        / (float) Math.max(1, screenHeight() - controlsParams.height);
                FloatingWindowDragController.resetPosition(service, windowManager,
                        controls, controlsParams, ClickerToolboxView.CLICKER_POSITION_KEY,
                        ClickerToolboxView.CLICKER_PANEL_LEFT_MARGIN_DP
                                / (float) Math.max(1, screenWidth() - controlsParams.width),
                        defaultY);
                if (targetParams != null) {
                    FloatingWindowDragController.resetPosition(service, windowManager,
                            target, targetParams, "click_target",
                            targetDefaultXRatio, targetDefaultYRatio);
                    updateCounterDuringTargetDrag();
                }
            }
        }

        resetShortcutPositions();
        scheduleBallCollapse();
    }

    private void resetShortcutPositions() {
        int index = 0;
        for (Library.Entry entry : Library.list(service, Library.KEY)) {
            if (!ShortcutKey.FLOATING.equals(ShortcutKey.displayMode(entry))) {
                continue;
            }
            float defaultY = Math.min(0.82f, 0.5f + index * 0.09f);
            ShortcutKey.savePosition(service, entry, 0.72f, defaultY);
            for (FloatingShortcut shortcut : floatingShortcuts) {
                if (shortcut.entry.id.equals(entry.id)) {
                    FloatingWindowDragController.resetPosition(service, windowManager,
                            shortcut.view, shortcut.params, "shortcut_" + entry.id,
                            0.72f, defaultY);
                    break;
                }
            }
            index++;
        }
    }

    private void toggleClickEditor() {
        if (clickEditor.isVisible()) {
            clickEditor.hide();
        } else {
            hidePanel();
            clickEditor.show();
        }
    }

    private void returnToClicker() {
        if (toolbox == null || controls != null) {
            return;
        }
        showPanel();
        showClicker();
    }

    private void onRunPressed() {
        if (service.isClicking()) {
            service.stopClicking();
        } else {
            clickCount.show(service::toggleClicking);
        }
    }

    private void hidePanel() {
        if (controls == null) {
            removeToolboxMask();
            return;
        }
        // 收起工具箱时停止点击，避免目标点已移除后手势继续运行。
        service.stopClicking();
        clickEditor.hide();
        clickCount.hide();
        windowManager.removeView(controls);
        removeToolboxMask();
        if (target != null) {
            windowManager.removeView(target);
        }
        if (counter != null) {
            windowManager.removeView(counter);
        }
        controls = null;
        clickerPanel = null;
        target = null;
        counter = null;
        targetParams = null;
        counterParams = null;
        updateToolboxState();
        scheduleBallCollapse();
    }

    private void showToolboxMask() {
        if (toolboxMask != null) {
            return;
        }
        toolboxMask = new View(service);
        toolboxMask.setBackgroundColor(Color.TRANSPARENT);
        toolboxMask.setContentDescription("点击关闭工具箱");
        toolboxMask.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                // 当前触摸分发完成后再移除窗口，避免部分系统出现事件状态异常。
                view.post(this::hidePanel);
            }
            return true;
        });
        WindowManager.LayoutParams maskParams = params(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, 0, 0);
        windowManager.addView(toolboxMask, maskParams);
    }

    private void removeToolboxMask() {
        if (toolboxMask == null) {
            return;
        }
        windowManager.removeView(toolboxMask);
        toolboxMask = null;
    }

    private void onOutsideTouch(float screenX, float screenY) {
        // 连点器需要允许用户直接操作下层应用，只能通过操作栏的“退出”结束。
        if (controls == null || target != null || service.shouldIgnoreOutsideTouch()
                || OutsideTouchLayout.contains(controls, screenX, screenY)
                || OutsideTouchLayout.contains(toolbox, screenX, screenY)
                || OutsideTouchLayout.contains(target, screenX, screenY)
                || OutsideTouchLayout.contains(counter, screenX, screenY)
                || containsFloatingShortcut(screenX, screenY)
                || clickEditor.contains(screenX, screenY)
                || clickCount.contains(screenX, screenY)) {
            return;
        }
        // 等待当前外部触摸事件分发完成后再移除悬浮面板。
        controls.post(() -> {
            if (!service.shouldIgnoreOutsideTouch()) {
                hidePanel();
            }
        });
    }

    private void updateToolboxState() {
        if (toolbox == null) {
            return;
        }
        boolean expanded = controls != null;
        toolbox.setContentDescription(expanded ? "收起工具箱" : "打开工具箱");
        toolbox.setSelected(expanded);
        toolbox.invalidate();
    }

    private void expandFloatingBall() {
        handler.removeCallbacks(collapseBallTask);
        if (toolbox == null || ballParams == null) {
            return;
        }
        ballCollapsed = false;
        ballParams.x = expandedEdgeX(ballParams.width);
        windowManager.updateViewLayout(toolbox, ballParams);
    }

    private void scheduleBallCollapse() {
        handler.removeCallbacks(collapseBallTask);
        if (toolbox != null && controls == null) {
            handler.postDelayed(collapseBallTask, BALL_COLLAPSE_DELAY_MS);
        }
    }

    private void collapseFloatingBall() {
        if (toolbox == null || ballParams == null || controls != null) {
            return;
        }
        ballCollapsed = true;
        ballParams.x = collapsedEdgeX(ballParams.width);
        windowManager.updateViewLayout(toolbox, ballParams);
    }

    void hide() {
        if (!isVisible()) {
            removeToolboxMask();
            return;
        }
        hidePanel();
        if (toolbox != null) {
            handler.removeCallbacks(collapseBallTask);
            windowManager.removeView(toolbox);
            toolbox = null;
            ballParams = null;
        }
        removeFloatingShortcuts();
    }

    void refreshShortcutKeys() {
        removeFloatingShortcuts();
        if (toolbox == null) {
            return;
        }
        List<Library.Entry> entries = Library.list(service, Library.KEY);
        int floatingIndex = 0;
        for (Library.Entry entry : entries) {
            if (!ShortcutKey.FLOATING.equals(ShortcutKey.displayMode(entry))) {
                continue;
            }
            addFloatingShortcut(entry, floatingIndex++);
        }
        if (controls != null && target == null) {
            windowManager.removeView(controls);
            controls = null;
            clickerPanel = null;
            controlsParams = null;
            showPanel();
        }
    }

    void collapsePanel() {
        hidePanel();
    }

    boolean saveClickSettings() {
        return clickEditor.saveSettings();
    }

    void hideClickEditor() {
        clickEditor.hide();
    }

    void setRunning(boolean running) {
        if (target == null || clickerPanel == null) {
            return;
        }
        clickerRunning = running;
        handler.removeCallbacks(executionTimeTick);
        if (running) {
            clickerStartedAt = SystemClock.uptimeMillis();
            updateExecutionTime();
        }
        clickerPanel.setRunning(running);
        if (counter != null) {
            counter.setVisibility(running ? View.VISIBLE : View.GONE);
            windowManager.updateViewLayout(counter, counterParams);
        }
        target.animate().cancel();
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setBackground(circleBackground(running ? 0xEE1769FF : 0xDD2563EB));
        // Android 12 及以上版本仅在悬浮窗透明度足够低时允许触摸穿透。
        if (running) {
            targetParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            targetParams.alpha = 0.5f;
            target.setAlpha(0.55f);
        } else {
            targetParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            targetParams.alpha = 1f;
            target.setAlpha(1f);
        }
        windowManager.updateViewLayout(target, targetParams);
    }

    void setClickProgress(int completed, int total) {
        if (counter == null) {
            return;
        }
        completedClicks = completed;
        totalClicks = total;
        updateCounterText();
    }

    private void updateExecutionTime() {
        if (!clickerRunning || counter == null) {
            return;
        }
        updateCounterText();
        long elapsed = SystemClock.uptimeMillis() - clickerStartedAt;
        handler.postDelayed(executionTimeTick, 1000L - elapsed % 1000L);
    }

    private void updateCounterText() {
        long elapsedSeconds = clickerRunning
                ? (SystemClock.uptimeMillis() - clickerStartedAt) / 1000L
                : 0L;
        counter.setText(completedClicks + "/" + (totalClicks == 0 ? "∞" : totalClicks)
                + " · " + elapsedSeconds + "s");
        updateCounterPosition();
        windowManager.updateViewLayout(counter, counterParams);
    }

    void showClickFeedback() {
        if (target == null) {
            return;
        }
        // 将脉冲限制在悬浮窗口内，避免 Android 将圆形动画裁剪成方形。
        target.animate().cancel();
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setAlpha(1f);
        target.animate()
                .scaleX(0.65f)
                .scaleY(0.65f)
                .alpha(0.4f)
                .setDuration(180L)
                .start();
    }

    int[] getTargetCenter() {
        int[] location = new int[2];
        target.getLocationOnScreen(location);
        return new int[] {location[0] + target.getWidth() / 2,
                location[1] + target.getHeight() / 2};
    }

    private void addFloatingShortcut(Library.Entry entry, int index) {
        TextView view = makeChip(entry.name, ShortcutKey.textSize(entry),
                Color.WHITE, ShortcutKey.color(entry));
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setElevation(dp(6));
        view.setContentDescription("快捷键 " + entry.name
                + (ShortcutKey.isLocked(entry) ? "，位置已锁定" : "，拖动可调整位置"));
        int width = Math.max(dp(56), Math.min(dp(160),
                dp(28) + Math.round(view.getTextSize() * Math.max(1, entry.name.length()))));
        int height = dp(48);
        float fallbackY = Math.min(0.82f, 0.5f + index * 0.09f);
        int x = Math.round((screenWidth() - width) * ShortcutKey.xRatio(entry, 0.72f));
        int y = Math.round((screenHeight() - height) * ShortcutKey.yRatio(entry, fallbackY));
        WindowManager.LayoutParams params = params(width, height, x, y);
        FloatingShortcut shortcut = new FloatingShortcut(entry, view, params);
        view.setOnClickListener(ignored -> service.runShortcut(entry));
        String positionKey = "shortcut_" + entry.id;
        view.setOnTouchListener(FloatingWindowDragController.createShortcut(service,
                windowManager, view, params, positionKey,
                ShortcutKey.xRatio(entry, 0.72f), ShortcutKey.yRatio(entry, fallbackY),
                !ShortcutKey.isLocked(entry), service::refreshRecordingCapture));
        windowManager.addView(view, params);
        floatingShortcuts.add(shortcut);
    }

    private void removeFloatingShortcuts() {
        for (FloatingShortcut shortcut : floatingShortcuts) {
            windowManager.removeView(shortcut.view);
        }
        floatingShortcuts.clear();
    }

    private boolean containsFloatingShortcut(float screenX, float screenY) {
        for (FloatingShortcut shortcut : floatingShortcuts) {
            if (OutsideTouchLayout.contains(shortcut.view, screenX, screenY)) {
                return true;
            }
        }
        return false;
    }

    private WindowManager.LayoutParams params(int width, int height, int x, int y) {
        WindowManager.LayoutParams result = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        result.gravity = Gravity.TOP | Gravity.START;
        result.x = x;
        result.y = y;
        return result;
    }

    private static final class FloatingShortcut {
        final Library.Entry entry;
        final TextView view;
        final WindowManager.LayoutParams params;

        FloatingShortcut(Library.Entry entry, TextView view,
                WindowManager.LayoutParams params) {
            this.entry = entry;
            this.view = view;
            this.params = params;
        }
    }

    private DragHandle makeChip(String label, int textSp, int color, int fill) {
        DragHandle view = new DragHandle(service);
        view.setText(label);
        view.setTextSize(textSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackground(background(fill, dp(12)));
        return view;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(radius);
        return result;
    }

    private GradientDrawable circleBackground(int color) {
        GradientDrawable result = new GradientDrawable();
        result.setShape(GradientDrawable.OVAL);
        result.setColor(color);
        return result;
    }

    private int dp(int value) {
        return Math.round(value * displayMetrics.density);
    }

    private int expandedEdgeX(int ballWidth) {
        return ballOnRight ? Math.max(0, screenWidth() - ballWidth) : 0;
    }

    private int collapsedEdgeX(int ballWidth) {
        int visibleWidth = Math.max(dp(18), ballWidth / 2);
        return ballOnRight ? screenWidth() - visibleWidth : visibleWidth - ballWidth;
    }

    private int ballY(int ballHeight) {
        return Math.round(Math.max(0, screenHeight() - ballHeight) * ballYRatio);
    }

    private void saveBallPosition() {
        if (ballParams == null) {
            return;
        }
        ballYRatio = ballParams.y
                / (float) Math.max(1, screenHeight() - ballParams.height);
        FloatingWindowDragController.saveEdgePosition(
                service, LOGO_POSITION_KEY, ballOnRight, ballYRatio);
    }

    private int pickerWidth() {
        return Math.max(dp(1), screenWidth() - dp(32));
    }

    private int pickerHeight() {
        return Math.min(dp(220), screenHeight() - dp(32));
    }

    private int pickerX(int width) {
        return Math.max(0, (screenWidth() - width) / 2);
    }

    private int pickerY(int height) {
        return Math.max(0, (screenHeight() - height) / 2);
    }

    private int[] initialTargetPosition(int size, WindowManager.LayoutParams panel) {
        int x = (screenWidth() - size) / 2;
        int y = (screenHeight() - size) / 2;
        boolean overlaps = x < panel.x + panel.width && x + size > panel.x
                && y < panel.y + panel.height && y + size > panel.y;
        if (overlaps) {
            // 竖屏时将目标点放在面板下方，横屏时放在面板侧边。
            if (screenHeight() - panel.y - panel.height >= size + dp(24)) {
                y = panel.y + panel.height + dp(12);
            } else {
                x = ballOnRight ? Math.max(dp(12), (panel.x - size) / 2)
                        : panel.x + panel.width
                                + Math.max(dp(12), (screenWidth() - panel.x - panel.width
                                        - size) / 2);
            }
        }
        return new int[] {x, y};
    }

    private void updateCounterPosition() {
        if (targetParams == null || counterParams == null) {
            return;
        }
        int gap = dp(8);
        int right = targetParams.x + targetParams.width + gap;
        counterParams.x = right + counterParams.width <= screenWidth()
                ? right : Math.max(0, targetParams.x - counterParams.width - gap);
        counterParams.y = Math.max(0, Math.min(screenHeight() - counterParams.height,
                targetParams.y + (targetParams.height - counterParams.height) / 2));
    }

    private void updateCounterDuringTargetDrag() {
        if (counter == null) {
            return;
        }
        updateCounterPosition();
        windowManager.updateViewLayout(counter, counterParams);
    }

    private int screenWidth() {
        return service.getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        return service.getResources().getDisplayMetrics().heightPixels;
    }

    private static final class DragHandle extends TextView {
        DragHandle(Context context) {
            super(context);
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }
}
