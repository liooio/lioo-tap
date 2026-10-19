package com.lioo.autoclick;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 单点连点模式使用的居中悬浮编辑器。 */
final class ClickEditorOverlay {
    private final OverlayService service;
    private final WindowManager windowManager;
    private final DisplayMetrics displayMetrics;
    private final OutsideTouchLayout.Listener outsideTouchListener;
    private final Runnable savedAction;
    private OutsideTouchLayout editor;
    private EditText intervalInput;
    private WindowManager.LayoutParams editorParams;
    private float dragDownX;
    private float dragDownY;
    private int dragStartX;
    private int dragStartY;

    ClickEditorOverlay(OverlayService service, WindowManager windowManager,
            DisplayMetrics displayMetrics, OutsideTouchLayout.Listener outsideTouchListener,
            Runnable savedAction) {
        this.service = service;
        this.windowManager = windowManager;
        this.displayMetrics = displayMetrics;
        this.outsideTouchListener = outsideTouchListener;
        this.savedAction = savedAction;
    }

    boolean isVisible() {
        return editor != null;
    }

    void show() {
        if (isVisible()) {
            return;
        }
        ClickSettings settings = ClickSettings.load(service);
        editor = new OutsideTouchLayout(service, outsideTouchListener);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(16), dp(16), dp(16), dp(16));
        editor.setBackground(background(0xE61B1F27, dp(18)));
        editor.setElevation(dp(8));
        editor.setFocusableInTouchMode(true);

        LinearLayout header = new LinearLayout(service);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("点击间隔", 17, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView close = text("×", 25, 0xFFD0D8E8, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭连点器编辑");
        close.setOnClickListener(view -> hide());
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        header.setContentDescription("拖动设置弹窗");
        header.setOnTouchListener(this::dragEditor);
        editor.addView(header);

        TextView hint = text("设置每次点击之间的时间间隔", 12, 0xFFD0D8E8, false);
        addWithTopMargin(editor, hint, dp(4));
        intervalInput = numberField(String.valueOf(settings.intervalMs), "50–60000 毫秒");
        addWithTopMargin(editor, text("点击间隔（毫秒）", 13, Color.WHITE, true), dp(16));
        addWithTopMargin(editor, stepper(intervalInput, 100, 50, 60000), dp(6));

        TextView save = text("保存间隔", 15, Color.WHITE, true);
        save.setGravity(Gravity.CENTER);
        save.setMinHeight(dp(46));
        save.setBackground(background(0xFF1769FF, dp(12)));
        save.setOnClickListener(view -> {
            if (saveSettings()) {
                hide();
                savedAction.run();
            }
        });
        addWithTopMargin(editor, save, dp(18));

        int width = Math.min(dp(300), displayMetrics.widthPixels - dp(32));
        editorParams = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT);
        editorParams.gravity = Gravity.CENTER;
        editorParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        windowManager.addView(editor, editorParams);
        editor.requestFocus();
    }

    boolean saveSettings() {
        if (!isVisible()) {
            return true;
        }
        try {
            int interval = Integer.parseInt(intervalInput.getText().toString().trim());
            if (interval < 50 || interval > 60000) {
                throw new NumberFormatException();
            }
            ClickSettings old = ClickSettings.load(service);
            new ClickSettings(interval, old.durationMs, old.count).save(service);
            Toast.makeText(service, "点击间隔已保存", Toast.LENGTH_SHORT).show();
            return true;
        } catch (NumberFormatException exception) {
            Toast.makeText(service, "间隔范围为 50–60000 毫秒", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    void hide() {
        if (!isVisible()) {
            return;
        }
        InputMethodManager keyboard = (InputMethodManager) service.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        windowManager.removeView(editor);
        editor = null;
        intervalInput = null;
        editorParams = null;
    }

    boolean contains(float screenX, float screenY) {
        return OutsideTouchLayout.contains(editor, screenX, screenY);
    }

    private EditText numberField(String value, String hint) {
        EditText input = new EditText(service);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(value);
        input.setHint(hint);
        input.setTextSize(16);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFFAEBBD0);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setMinHeight(dp(48));
        input.setBackground(background(0xFF303A4A, dp(10)));
        return input;
    }

    private LinearLayout stepper(EditText input, int step, int min, int max) {
        LinearLayout row = new LinearLayout(service);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(stepButton("−", input, -step, min, max),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, dp(48), 1);
        inputParams.leftMargin = dp(8);
        inputParams.rightMargin = dp(8);
        row.addView(input, inputParams);
        row.addView(stepButton("+", input, step, min, max),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        return row;
    }

    private TextView stepButton(String label, EditText input, int delta, int min, int max) {
        TextView button = text(label, 22, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(background(0xFF354156, dp(10)));
        button.setContentDescription((delta < 0 ? "减少" : "增加")
                + Math.abs(delta));
        button.setOnClickListener(view -> {
            int current;
            try {
                current = Integer.parseInt(input.getText().toString().trim());
            } catch (NumberFormatException exception) {
                current = min;
            }
            int next = Math.max(min, Math.min(max, current + delta));
            input.setText(String.valueOf(next));
            input.setSelection(input.length());
        });
        return button;
    }

    private boolean dragEditor(View view, MotionEvent event) {
        if (editorParams == null) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            dragDownX = event.getRawX();
            dragDownY = event.getRawY();
            dragStartX = editorParams.x;
            dragStartY = editorParams.y;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            int maxX = Math.max(0, (displayMetrics.widthPixels - editor.getWidth()) / 2);
            int maxY = Math.max(0, (displayMetrics.heightPixels - editor.getHeight()) / 2);
            editorParams.x = Math.max(-maxX, Math.min(maxX,
                    dragStartX + Math.round(event.getRawX() - dragDownX)));
            editorParams.y = Math.max(-maxY, Math.min(maxY,
                    dragStartY + Math.round(event.getRawY() - dragDownY)));
            windowManager.updateViewLayout(editor, editorParams);
            return true;
        }
        return event.getActionMasked() == MotionEvent.ACTION_UP;
    }

    private TextView text(String label, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(service);
        view.setText(label);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private void addWithTopMargin(LinearLayout parent, View child, int topPx) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topPx;
        parent.addView(child, params);
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
