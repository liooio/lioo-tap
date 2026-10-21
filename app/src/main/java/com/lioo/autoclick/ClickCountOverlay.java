package com.lioo.autoclick;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 连点器开始运行前显示的可拖动确认面板。 */
final class ClickCountOverlay {
    private final OverlayService service;
    private final WindowManager windowManager;
    private final DisplayMetrics displayMetrics;
    private final OutsideTouchLayout.Listener outsideTouchListener;
    private OutsideTouchLayout root;
    private EditText countInput;
    private WindowManager.LayoutParams params;

    ClickCountOverlay(OverlayService service, WindowManager windowManager,
            DisplayMetrics displayMetrics, OutsideTouchLayout.Listener outsideTouchListener) {
        this.service = service;
        this.windowManager = windowManager;
        this.displayMetrics = displayMetrics;
        this.outsideTouchListener = outsideTouchListener;
    }

    boolean isVisible() {
        return root != null;
    }

    void show(Runnable executeAction) {
        if (isVisible()) {
            return;
        }
        ClickSettings settings = ClickSettings.load(service);
        root = new OutsideTouchLayout(service, outsideTouchListener);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackground(background(0xF21B1F27, dp(18)));
        root.setElevation(dp(8));

        LinearLayout header = new LinearLayout(service);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setContentDescription("拖动次数确认弹窗");
        TextView title = text("确认点击次数", 17, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView close = text("×", 25, 0xFFD0D8E8, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(view -> hide());
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        root.addView(header);

        TextView hint = text("0 表示持续点击，运行后可随时停止", 12, 0xFFD0D8E8, false);
        addWithTopMargin(root, hint, dp(4));
        countInput = numberField(String.valueOf(settings.count));
        addWithTopMargin(root, stepper(), dp(16));

        TextView execute = text("执行点击", 15, Color.WHITE, true);
        execute.setGravity(Gravity.CENTER);
        execute.setMinHeight(dp(46));
        execute.setBackground(background(0xFF1769FF, dp(12)));
        execute.setOnClickListener(view -> {
            if (saveCount()) {
                hide();
                executeAction.run();
            }
        });
        addWithTopMargin(root, execute, dp(18));

        int width = Math.min(dp(300), displayMetrics.widthPixels - dp(32));
        params = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        FloatingWindowDragController.restorePosition(service, root, params,
                "click_count", 0.5f, 0.5f);
        windowManager.addView(root, params);
        FloatingWindowDragController.attach(service, windowManager, root, header, params,
                "click_count", 0.5f, 0.5f);
    }

    void hide() {
        if (!isVisible()) {
            return;
        }
        windowManager.removeView(root);
        root = null;
        countInput = null;
        params = null;
    }

    boolean contains(float x, float y) {
        return OutsideTouchLayout.contains(root, x, y);
    }

    private boolean saveCount() {
        try {
            int count = Integer.parseInt(countInput.getText().toString().trim());
            if (count < 0 || count > 1000000) {
                throw new NumberFormatException();
            }
            ClickSettings old = ClickSettings.load(service);
            new ClickSettings(old.intervalMs, old.durationMs, count).save(service);
            return true;
        } catch (NumberFormatException exception) {
            Toast.makeText(service, "次数范围为 0–1000000", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private LinearLayout stepper() {
        LinearLayout row = new LinearLayout(service);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(stepButton("−", -1), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, dp(48), 1);
        inputParams.leftMargin = dp(8);
        inputParams.rightMargin = dp(8);
        row.addView(countInput, inputParams);
        row.addView(stepButton("+", 1), new LinearLayout.LayoutParams(dp(48), dp(48)));
        return row;
    }

    private TextView stepButton(String label, int delta) {
        TextView button = text(label, 22, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(background(0xFF354156, dp(10)));
        Runnable adjust = () -> {
            int current;
            try {
                current = Integer.parseInt(countInput.getText().toString().trim());
            } catch (NumberFormatException exception) {
                current = 0;
            }
            int next = Math.max(0, Math.min(1000000, current + delta));
            countInput.setText(String.valueOf(next));
            countInput.setSelection(countInput.length());
        };
        button.setOnClickListener(view -> adjust.run());
        LongPressStepper.attach(button, adjust);
        return button;
    }

    private EditText numberField(String value) {
        EditText input = new EditText(service);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(value);
        input.setTextSize(17);
        input.setTextColor(Color.WHITE);
        input.setGravity(Gravity.CENTER);
        input.setBackground(background(0xFF303A4A, dp(10)));
        return input;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(service);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private void addWithTopMargin(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams childParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        childParams.topMargin = top;
        parent.addView(child, childParams);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * displayMetrics.density);
    }
}
