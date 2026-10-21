package com.lioo.autoclick;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

/** 统一处理悬浮窗口拖动、屏幕边界限制和位置持久化。 */
final class FloatingWindowDragController implements View.OnTouchListener {
    private static final String PREFS = "floating_window_positions";
    private static final String EDGE_POSITION_SUFFIX = "_edge";

    /** 用于贴边、半隐藏等特殊窗口；触摸分发仍由公共组件统一处理。 */
    interface PositionStrategy {
        void move(float rawX, float deltaX, float deltaY);

        void finish(boolean moved);
    }

    private final Context context;
    private final WindowManager windowManager;
    private final View window;
    private final WindowManager.LayoutParams params;
    private final String positionKey;
    private final float defaultXRatio;
    private final float defaultYRatio;
    private final Runnable touchStarted;
    private final Runnable positionChanged;
    private final Runnable dragFinished;
    private final boolean draggable;
    private final PositionStrategy positionStrategy;
    private final Runnable clickAction;
    private float downX;
    private float downY;
    private int startX;
    private int startY;

    private FloatingWindowDragController(Context context, WindowManager windowManager,
            View window, WindowManager.LayoutParams params, String positionKey,
            float defaultXRatio, float defaultYRatio, Runnable touchStarted,
            Runnable positionChanged, Runnable dragFinished, boolean draggable,
            PositionStrategy positionStrategy, Runnable clickAction) {
        this.context = context;
        this.windowManager = windowManager;
        this.window = window;
        this.params = params;
        this.positionKey = positionKey;
        this.defaultXRatio = defaultXRatio;
        this.defaultYRatio = defaultYRatio;
        this.touchStarted = touchStarted;
        this.positionChanged = positionChanged;
        this.dragFinished = dragFinished;
        this.draggable = draggable;
        this.positionStrategy = positionStrategy;
        this.clickAction = clickAction;
    }

    static void attach(Context context, WindowManager windowManager, View window,
            View dragHandle, WindowManager.LayoutParams params, String positionKey,
            float defaultXRatio, float defaultYRatio) {
        create(context, windowManager, window, params, positionKey,
                defaultXRatio, defaultYRatio, null, null, null, dragHandle);
    }

    /** 在添加到 WindowManager 前恢复坐标，避免窗口先出现在默认位置再跳动。 */
    static void restorePosition(Context context, View window,
            WindowManager.LayoutParams params, String positionKey,
            float defaultXRatio, float defaultYRatio) {
        Bounds bounds = bounds(context, window, params);
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
        params.x = fromRatio(preferences.getFloat(positionKey + "_x", defaultXRatio),
                bounds.minX, bounds.maxX);
        params.y = fromRatio(preferences.getFloat(positionKey + "_y", defaultYRatio),
                bounds.minY, bounds.maxY);
    }

    static void resetPosition(Context context, WindowManager windowManager, View window,
            WindowManager.LayoutParams params, String positionKey,
            float defaultXRatio, float defaultYRatio) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
        preferences.edit()
                .remove(positionKey + "_x")
                .remove(positionKey + "_y")
                .apply();
        restorePosition(context, window, params, positionKey, defaultXRatio, defaultYRatio);
        windowManager.updateViewLayout(window, params);
        preferences.edit()
                .putFloat(positionKey + "_x", defaultXRatio)
                .putFloat(positionKey + "_y", defaultYRatio)
                .apply();
    }

    static void resetAllPositions(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    static ImageView createResetButton(Context context, Runnable resetAction) {
        float density = context.getResources().getDisplayMetrics().density;
        ImageView reset = new ImageView(context);
        reset.setImageResource(R.drawable.ic_clicker_reset);
        reset.setColorFilter(0xFFABB1C2);
        int padding = Math.round(5 * density);
        reset.setPadding(padding, padding, padding, padding);
        reset.setBackground(background(0x332D3A52, Math.round(14 * density)));
        reset.setContentDescription("恢复悬浮窗默认位置");
        reset.setClickable(true);
        reset.setFocusable(true);
        reset.setOnClickListener(view -> resetAction.run());
        return reset;
    }

    static boolean edgeOnRight(Context context, String positionKey, boolean fallback) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(positionKey + EDGE_POSITION_SUFFIX + "_right", fallback);
    }

    static float edgeYRatio(Context context, String positionKey, float fallback) {
        return Math.max(0f, Math.min(1f, context.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE).getFloat(
                        positionKey + EDGE_POSITION_SUFFIX + "_y", fallback)));
    }

    static void saveEdgePosition(Context context, String positionKey,
            boolean onRight, float yRatio) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(positionKey + EDGE_POSITION_SUFFIX + "_right", onRight)
                .putFloat(positionKey + EDGE_POSITION_SUFFIX + "_y",
                        Math.max(0f, Math.min(1f, yRatio)))
                .apply();
    }

    static void resetEdgePosition(Context context, String positionKey,
            boolean defaultRight, float defaultYRatio) {
        saveEdgePosition(context, positionKey, defaultRight, defaultYRatio);
    }

    static View.OnTouchListener create(Context context, WindowManager windowManager,
            View window, WindowManager.LayoutParams params, String positionKey,
            float defaultXRatio, float defaultYRatio, Runnable touchStarted,
            Runnable dragFinished, View... dragHandles) {
        return createWithPositionListener(context, windowManager, window, params, positionKey,
                defaultXRatio, defaultYRatio, touchStarted, null, dragFinished, dragHandles);
    }

    static View.OnTouchListener createWithPositionListener(Context context,
            WindowManager windowManager,
            View window, WindowManager.LayoutParams params, String positionKey,
            float defaultXRatio, float defaultYRatio, Runnable touchStarted,
            Runnable positionChanged, Runnable dragFinished, View... dragHandles) {
        FloatingWindowDragController controller = new FloatingWindowDragController(
                context, windowManager, window, params, positionKey,
                defaultXRatio, defaultYRatio, touchStarted, positionChanged, dragFinished,
                true, null, null);
        for (View dragHandle : dragHandles) {
            // 部分浮窗会按状态动态提供拖动把手；空引用不应阻断整个浮窗的显示。
            if (dragHandle != null) {
                dragHandle.setOnTouchListener(controller);
            }
        }
        window.post(controller::restore);
        return controller;
    }

    static View.OnTouchListener createShortcut(Context context, WindowManager windowManager,
            View window, WindowManager.LayoutParams params, String positionKey,
            float defaultXRatio, float defaultYRatio, boolean draggable,
            Runnable dragFinished) {
        FloatingWindowDragController controller = new FloatingWindowDragController(
                context, windowManager, window, params, positionKey,
                defaultXRatio, defaultYRatio, null, null, dragFinished,
                draggable, null, null);
        window.post(controller::restore);
        return controller;
    }

    static View.OnTouchListener createWithStrategy(Context context,
            WindowManager windowManager, View window, WindowManager.LayoutParams params,
            Runnable touchStarted, PositionStrategy positionStrategy,
            Runnable clickAction) {
        return new FloatingWindowDragController(context, windowManager, window, params,
                "", 0.5f, 0.5f, touchStarted, null, null,
                true, positionStrategy, clickAction);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (touchStarted != null) {
                touchStarted.run();
            }
            downX = event.getRawX();
            downY = event.getRawY();
            startX = params.x;
            startY = params.y;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (positionStrategy != null) {
                positionStrategy.move(event.getRawX(), event.getRawX() - downX,
                        event.getRawY() - downY);
                windowManager.updateViewLayout(window, params);
                return true;
            }
            if (!draggable) {
                return true;
            }
            Bounds bounds = bounds();
            params.x = clamp(startX + Math.round(event.getRawX() - downX),
                    bounds.minX, bounds.maxX);
            params.y = clamp(startY + Math.round(event.getRawY() - downY),
                    bounds.minY, bounds.maxY);
            windowManager.updateViewLayout(window, params);
            if (positionChanged != null) {
                positionChanged.run();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean moved = Math.abs(event.getRawX() - downX) >= dp(8)
                    || Math.abs(event.getRawY() - downY) >= dp(8);
            if (positionStrategy != null) {
                if (action == MotionEvent.ACTION_UP) {
                    positionStrategy.finish(moved);
                    if (!moved && clickAction != null) {
                        clickAction.run();
                    }
                }
            } else if (moved && draggable) {
                save();
            } else if (action == MotionEvent.ACTION_UP) {
                if (clickAction != null) {
                    clickAction.run();
                } else {
                    window.performClick();
                }
            }
            if (dragFinished != null) {
                dragFinished.run();
            }
            return true;
        }
        return false;
    }

    private void restore() {
        if (!window.isAttachedToWindow()) {
            return;
        }
        restorePosition(context, window, params, positionKey,
                defaultXRatio, defaultYRatio);
        windowManager.updateViewLayout(window, params);
        if (positionChanged != null) {
            positionChanged.run();
        }
    }

    private void save() {
        Bounds bounds = bounds();
        preferences().edit()
                .putFloat(positionKey + "_x", toRatio(params.x, bounds.minX, bounds.maxX))
                .putFloat(positionKey + "_y", toRatio(params.y, bounds.minY, bounds.maxY))
                .apply();
    }

    private Bounds bounds() {
        return bounds(context, window, params);
    }

    private static Bounds bounds(Context context, View window,
            WindowManager.LayoutParams params) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int widthSpec = measureSpec(params.width, screenWidth);
        int heightSpec = measureSpec(params.height, screenHeight);
        window.measure(widthSpec, heightSpec);
        int windowWidth = params.width >= 0 ? params.width : window.getMeasuredWidth();
        int windowHeight = params.height >= 0 ? params.height : window.getMeasuredHeight();
        int horizontalSpace = Math.max(0, screenWidth - windowWidth);
        int verticalSpace = Math.max(0, screenHeight - windowHeight);
        int horizontalGravity = params.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        int verticalGravity = params.gravity & Gravity.VERTICAL_GRAVITY_MASK;
        int minX = horizontalGravity == Gravity.CENTER_HORIZONTAL ? -horizontalSpace / 2 : 0;
        int maxX = horizontalGravity == Gravity.CENTER_HORIZONTAL
                ? horizontalSpace - horizontalSpace / 2 : horizontalSpace;
        int minY = verticalGravity == Gravity.CENTER_VERTICAL ? -verticalSpace / 2 : 0;
        int maxY = verticalGravity == Gravity.CENTER_VERTICAL
                ? verticalSpace - verticalSpace / 2 : verticalSpace;
        return new Bounds(minX, maxX, minY, maxY);
    }

    private static int measureSpec(int dimension, int maximum) {
        if (dimension >= 0) {
            return View.MeasureSpec.makeMeasureSpec(dimension, View.MeasureSpec.EXACTLY);
        }
        int mode = dimension == WindowManager.LayoutParams.MATCH_PARENT
                ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST;
        return View.MeasureSpec.makeMeasureSpec(maximum, mode);
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static android.graphics.drawable.GradientDrawable background(int color, int radius) {
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private static int fromRatio(float ratio, int min, int max) {
        return min + Math.round(Math.max(0f, Math.min(1f, ratio)) * (max - min));
    }

    private static float toRatio(int value, int min, int max) {
        return max == min ? 0.5f
                : Math.max(0f, Math.min(1f, (value - min) / (float) (max - min)));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Bounds {
        final int minX;
        final int maxX;
        final int minY;
        final int maxY;

        Bounds(int minX, int maxX, int minY, int maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }
}
