package com.lioo.autoclick;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class OverlayController {
    private final OverlayService service;
    private final WindowManager windowManager;
    private final DisplayMetrics displayMetrics;
    private final ClickEditorOverlay clickEditor;
    private final ClickCountOverlay clickCount;
    private ClickerToolboxView clickerPanel;
    private OutsideTouchLayout controls;
    private LogoBallView toolbox;
    private DragHandle target;
    private TextView counter;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams controlsParams;
    private WindowManager.LayoutParams targetParams;
    private WindowManager.LayoutParams counterParams;
    private final List<FloatingShortcut> floatingShortcuts = new ArrayList<>();
    private boolean ballOnRight = false;

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

    void show() {
        if (isVisible()) {
            return;
        }
        int targetSize = dp(48);
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
                edgeX(targetSize),
                Math.max(0, screenHeight() - targetSize - dp(24)));
        ballParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        toolbox.setOnTouchListener(new DragListener(ballParams, false, true));
        windowManager.addView(toolbox, ballParams);
        toolbox.post(this::snapFloatingBallToEdge);
        refreshShortcutKeys();
    }

    void snapFloatingBallToEdge() {
        if (toolbox == null) {
            return;
        }
        // Re-anchor after rotation because the old right edge may now be in the screen center.
        ballParams.x = edgeX(ballParams.width);
        ballParams.y = Math.max(0, Math.min(screenHeight() - ballParams.height, ballParams.y));
        windowManager.updateViewLayout(toolbox, ballParams);
        if (controls != null) {
            if (target == null) {
                windowManager.removeView(controls);
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
                workflows, service::runEntry, toolboxShortcuts, service::runShortcut);
        controls = clickerPanel.view();
        controlsParams = params(panelWidth, panelHeight,
                pickerX(panelWidth), pickerY(panelHeight));
        controlsParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        clickerPanel.setDragListener(new DragListener(controlsParams, false, false));
        windowManager.addView(controls, controlsParams);
        updateToolboxState();
    }

    private void showClicker() {
        if (controls == null || target != null) {
            return;
        }
        int panelWidth = Math.min(dp(300), screenWidth() - dp(32));
        int panelHeight = dp(96);
        controlsParams.width = panelWidth;
        controlsParams.height = panelHeight;
        controlsParams.x = (screenWidth() - panelWidth) / 2;
        controlsParams.y = dp(32);
        clickerPanel.showClicker();
        windowManager.updateViewLayout(controls, controlsParams);

        int targetSize = dp(56);
        target = makeChip("◎", 23, Color.WHITE, 0xDD2563EB);
        target.setBackground(circleBackground(0xDD2563EB));
        target.setContentDescription("点击位置，拖动可调整");
        target.setVisibility(View.VISIBLE);
        target.setGravity(Gravity.CENTER);
        int[] targetPosition = initialTargetPosition(targetSize, controlsParams);
        targetParams = params(targetSize, targetSize, targetPosition[0], targetPosition[1]);
        target.setOnTouchListener(new DragListener(targetParams, true, false));
        windowManager.addView(target, targetParams);

        counter = makeChip("0/0", 12, Color.WHITE, 0xE61B1F27);
        counter.setGravity(Gravity.CENTER);
        counter.setVisibility(View.GONE);
        counterParams = params(dp(72), dp(32), 0, 0);
        counterParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        updateCounterPosition();
        windowManager.addView(counter, counterParams);
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
            return;
        }
        // 收起工具箱时停止点击，避免目标点已移除后手势继续运行。
        service.stopClicking();
        clickEditor.hide();
        clickCount.hide();
        windowManager.removeView(controls);
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
    }

    private void onOutsideTouch(float screenX, float screenY) {
        if (controls == null || service.shouldIgnoreOutsideTouch()
                || OutsideTouchLayout.contains(controls, screenX, screenY)
                || OutsideTouchLayout.contains(toolbox, screenX, screenY)
                || OutsideTouchLayout.contains(target, screenX, screenY)
                || OutsideTouchLayout.contains(counter, screenX, screenY)
                || containsFloatingShortcut(screenX, screenY)
                || clickEditor.contains(screenX, screenY)
                || clickCount.contains(screenX, screenY)) {
            return;
        }
        // Defer removal until the current outside-touch dispatch has completed.
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

    void hide() {
        if (!isVisible()) {
            return;
        }
        hidePanel();
        if (toolbox != null) {
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
        clickerPanel.setRunning(running);
        if (counter != null) {
            counter.setVisibility(running ? View.VISIBLE : View.GONE);
            windowManager.updateViewLayout(counter, counterParams);
        }
        target.animate().cancel();
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setBackground(circleBackground(running ? 0xEE1769FF : 0xDD2563EB));
        // Android 12+ only passes touches through an app overlay when its window alpha is low enough.
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
        counter.setText(completed + "/" + (total == 0 ? "∞" : total));
        updateCounterPosition();
        windowManager.updateViewLayout(counter, counterParams);
    }

    void showClickFeedback() {
        if (target == null) {
            return;
        }
        // Keep the pulse inside the overlay window so Android cannot clip its circle to a square.
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
        view.setOnTouchListener(new ShortcutDragListener(shortcut));
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

    private int edgeX(int ballWidth) {
        return ballOnRight ? Math.max(0, screenWidth() - ballWidth) : 0;
    }

    private int panelX(int width) {
        return ballOnRight ? screenWidth() - ballParams.width - width - dp(12)
                : ballParams.x + ballParams.width + dp(12);
    }

    private int panelY(int height) {
        int gap = dp(12);
        int aboveBall = ballParams.y - height - gap;
        if (aboveBall >= dp(12)) {
            return aboveBall;
        }
        return Math.max(dp(12), Math.min(screenHeight() - height - dp(12),
                ballParams.y + ballParams.height + gap));
    }

    private int pickerWidth() {
        return isPortrait()
                ? Math.round(screenWidth() * 0.9f)
                : Math.min(dp(360), screenWidth() - dp(32));
    }

    private int pickerHeight() {
        int shortcutCount = 0;
        for (Library.Entry entry : Library.list(service, Library.KEY)) {
            if (ShortcutKey.TOOLBOX.equals(ShortcutKey.displayMode(entry))) {
                shortcutCount++;
            }
        }
        int shortcutRows = Math.max(1, (shortcutCount + 2) / 3);
        int workflowCount = Library.list(service, Library.GROUP).size();
        int workflowRows = Math.max(1, (workflowCount + 2) / 3);
        int desired = dp(208 + workflowRows * 82 + shortcutRows * 82);
        int maximum = isPortrait()
                ? Math.round(screenHeight() * 0.68f)
                : Math.round(screenHeight() * 0.9f);
        return Math.min(desired, maximum);
    }

    private int pickerX(int width) {
        return isPortrait() ? (screenWidth() - width) / 2 : panelX(width);
    }

    private int pickerY(int height) {
        return isPortrait() ? panelY(height) : (screenHeight() - height) / 2;
    }

    private boolean isPortrait() {
        return screenHeight() >= screenWidth();
    }

    private int[] initialTargetPosition(int size, WindowManager.LayoutParams panel) {
        int x = (screenWidth() - size) / 2;
        int y = (screenHeight() - size) / 2;
        boolean overlaps = x < panel.x + panel.width && x + size > panel.x
                && y < panel.y + panel.height && y + size > panel.y;
        if (overlaps) {
            // Place the marker below the panel on tall screens and beside it in landscape.
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

    private int screenWidth() {
        return service.getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        return service.getResources().getDisplayMetrics().heightPixels;
    }

    private final class ShortcutDragListener implements View.OnTouchListener {
        private final FloatingShortcut shortcut;
        private float downX;
        private float downY;
        private int startX;
        private int startY;

        ShortcutDragListener(FloatingShortcut shortcut) {
            this.shortcut = shortcut;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX();
                downY = event.getRawY();
                startX = shortcut.params.x;
                startY = shortcut.params.y;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (ShortcutKey.isLocked(shortcut.entry)) {
                    return true;
                }
                shortcut.params.x = Math.max(0, Math.min(
                        screenWidth() - shortcut.view.getWidth(),
                        startX + Math.round(event.getRawX() - downX)));
                shortcut.params.y = Math.max(0, Math.min(
                        screenHeight() - shortcut.view.getHeight(),
                        startY + Math.round(event.getRawY() - downY)));
                windowManager.updateViewLayout(shortcut.view, shortcut.params);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                boolean click = ShortcutKey.isLocked(shortcut.entry)
                        || Math.abs(event.getRawX() - downX) < dp(8)
                        && Math.abs(event.getRawY() - downY) < dp(8);
                if (click) {
                    view.performClick();
                } else {
                    float xRatio = shortcut.params.x
                            / (float) Math.max(1, screenWidth() - shortcut.view.getWidth());
                    float yRatio = shortcut.params.y
                            / (float) Math.max(1, screenHeight() - shortcut.view.getHeight());
                    ShortcutKey.savePosition(service, shortcut.entry, xRatio, yRatio);
                }
                return true;
            }
            return false;
        }
    }

    private final class DragListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private final boolean marker;
        private final boolean toolboxHandle;
        private float downX;
        private float downY;
        private int startX;
        private int startY;

        DragListener(WindowManager.LayoutParams params, boolean marker, boolean toolboxHandle) {
            this.params = params;
            this.marker = marker;
            this.toolboxHandle = toolboxHandle;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            WindowManager.LayoutParams current = marker ? params : (toolboxHandle ? params : controlsParams);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX();
                downY = event.getRawY();
                startX = current.x;
                startY = current.y;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                View draggedView = marker ? target : (toolboxHandle ? toolbox : controls);
                if (toolboxHandle) {
                    // The floating ball changes sides at the midpoint and never rests in the center.
                    ballOnRight = event.getRawX() >= screenWidth() / 2f;
                    current.x = edgeX(draggedView.getWidth());
                } else {
                    current.x = Math.max(0, Math.min(screenWidth() - draggedView.getWidth(),
                            startX + Math.round(event.getRawX() - downX)));
                }
                current.y = Math.max(0, Math.min(screenHeight() - draggedView.getHeight(),
                        startY + Math.round(event.getRawY() - downY)));
                windowManager.updateViewLayout(marker ? target : (toolboxHandle ? toolbox : controls), current);
                if (marker && counter != null) {
                    updateCounterPosition();
                    windowManager.updateViewLayout(counter, counterParams);
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (Math.abs(event.getRawX() - downX) < dp(8)
                        && Math.abs(event.getRawY() - downY) < dp(8)) {
                    view.performClick();
                }
                return true;
            }
            return false;
        }

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
