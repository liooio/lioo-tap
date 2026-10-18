package com.lioo.autoclick;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

final class Ui {
    interface ValueAction {
        boolean run(String value);
    }

    interface ChoiceAction {
        void run(int index);
    }

    static final int BG = 0xFFF4F8FF;
    static final int INK = 0xFF101A33;
    static final int MUTED = 0xFF596985;
    static final int HINT = 0xFF8795AD;
    static final int PRIMARY = 0xFF1769FF;
    static final int PRIMARY_DARK = 0xFF0A42C5;
    static final int ON_PRIMARY = Color.WHITE;
    static final int PRIMARY_CONTAINER = 0xFFDDE9FF;
    static final int ON_PRIMARY_CONTAINER = 0xFF06245F;
    static final int SECONDARY_CONTAINER = 0xFFD8F2FF;
    static final int SURFACE = Color.WHITE;
    static final int VARIANT = 0xFFE7EEFA;
    static final int OUTLINE = 0xFFD8E1F0;
    static final int DIVIDER = 0xFFE6ECF5;
    static final int FIELD = 0xFFF9FBFF;
    static final int ERROR = 0xFFBA1A1A;
    static final int GREEN = 0xFF198754;
    static final int BLUE = 0xFF168ED1;
    static final int PURPLE = 0xFF7651C8;
    static final int SOFT_ORANGE = 0xFFDDE9FF;
    static final int SOFT_BLUE = 0xFFD8F2FF;
    static final int SOFT_PURPLE = 0xFFF0E9FF;
    static final int SOFT_GREEN = 0xFFE2F5EA;

    private Ui() {
    }

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static TextView text(Activity activity, String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    static GradientDrawable round(Activity activity, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, radiusDp));
        return drawable;
    }

    static GradientDrawable stroke(Activity activity, int fill, int radiusDp, int strokeColor) {
        GradientDrawable drawable = round(activity, fill, radiusDp);
        drawable.setStroke(Math.max(1, dp(activity, 1)), strokeColor);
        return drawable;
    }

    static GradientDrawable gradient(Activity activity, int startColor, int endColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[] {startColor, endColor});
        drawable.setCornerRadius(dp(activity, radiusDp));
        return drawable;
    }

    static LinearLayout card(Activity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(round(activity, SURFACE, 18));
        card.setElevation(dp(activity, 2));
        return card;
    }

    static void gap(LinearLayout parent, Activity activity, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, topDp);
        parent.addView(child, params);
    }

    static TextView button(Activity activity, String label, int fill, int textColor) {
        TextView view = text(activity, label, 15, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(activity, 48));
        view.setBackground(round(activity, fill, 14));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    static LinearLayout navigationBar(Activity activity, String title, String actionLabel,
            Runnable backAction, Runnable action) {
        activity.getWindow().setStatusBarColor(SURFACE);
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(SURFACE);
        bar.setElevation(dp(activity, 1));
        applyTopInset(activity, bar, statusBarHeight(activity));
        bar.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            if (insets.getDisplayCutout() != null) {
                topInset = Math.max(topInset, insets.getDisplayCutout().getSafeInsetTop());
            }
            applyTopInset(activity, bar, topInset);
            return insets;
        });

        ImageView back = new ImageView(activity);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("返回");
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(view -> backAction.run());
        bar.addView(back, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 56)));

        TextView heading = text(activity, title, 20, INK, true);
        heading.setGravity(Gravity.CENTER);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                0, dp(activity, 56), 1);
        bar.addView(heading, headingParams);

        if (actionLabel == null || action == null) {
            View spacer = new View(activity);
            bar.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 56)));
        } else {
            TextView actionView = text(activity, actionLabel, 15, PRIMARY, true);
            actionView.setGravity(Gravity.CENTER);
            actionView.setClickable(true);
            actionView.setFocusable(true);
            actionView.setOnClickListener(view -> action.run());
            bar.addView(actionView, new LinearLayout.LayoutParams(
                    dp(activity, 64), dp(activity, 56)));
        }
        return bar;
    }

    static void showMessage(Activity activity, String title, String message,
            String actionLabel, Runnable action) {
        Modal modal = modal(activity, title, message);
        TextView confirm = button(activity, actionLabel, PRIMARY, ON_PRIMARY);
        confirm.setOnClickListener(view -> {
            modal.dialog.dismiss();
            if (action != null) {
                action.run();
            }
        });
        gap(modal.panel, activity, confirm, 20);
        present(activity, modal);
    }

    static void showConfirm(Activity activity, String title, String message,
            String confirmLabel, boolean destructive, Runnable action) {
        if (destructive) {
            showSlideDelete(activity, title, message, action);
            return;
        }
        Modal modal = modal(activity, title, message);
        LinearLayout buttons = new LinearLayout(activity);
        TextView cancel = button(activity, "取消", SURFACE, MUTED);
        cancel.setBackground(stroke(activity, SURFACE, 12, OUTLINE));
        cancel.setOnClickListener(view -> modal.dialog.dismiss());
        buttons.addView(cancel, new LinearLayout.LayoutParams(
                0, dp(activity, 48), 1));
        TextView confirm = button(activity, confirmLabel,
                destructive ? ERROR : PRIMARY, ON_PRIMARY);
        confirm.setOnClickListener(view -> {
            modal.dialog.dismiss();
            action.run();
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, dp(activity, 48), 1);
        confirmParams.leftMargin = dp(activity, 10);
        buttons.addView(confirm, confirmParams);
        gap(modal.panel, activity, buttons, 20);
        present(activity, modal);
    }

    static void showSlideDelete(Activity activity, String title, String message,
            Runnable action) {
        Modal modal = modal(activity, title, message);
        SlideToDeleteView slider = new SlideToDeleteView(activity, () -> {
            modal.dialog.dismiss();
            action.run();
        });
        gap(modal.panel, activity, slider, 20);
        slider.getLayoutParams().height = dp(activity, 58);
        present(activity, modal);
    }

    static void showInput(Activity activity, String title, String message, String hint,
            String initialValue, int inputType, String confirmLabel, ValueAction action) {
        Modal modal = modal(activity, title, message);
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setHint(hint);
        input.setText(initialValue == null ? "" : initialValue);
        input.setSelectAllOnFocus(true);
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setHintTextColor(HINT);
        input.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        input.setBackground(stroke(activity, FIELD, 12, OUTLINE));
        gap(modal.panel, activity, input, 18);
        LinearLayout buttons = new LinearLayout(activity);
        TextView cancel = button(activity, "取消", SURFACE, MUTED);
        cancel.setBackground(stroke(activity, SURFACE, 12, OUTLINE));
        cancel.setOnClickListener(view -> modal.dialog.dismiss());
        buttons.addView(cancel, new LinearLayout.LayoutParams(
                0, dp(activity, 48), 1));
        TextView confirm = button(activity, confirmLabel, PRIMARY, ON_PRIMARY);
        confirm.setOnClickListener(view -> {
            if (action.run(input.getText().toString().trim())) {
                modal.dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, dp(activity, 48), 1);
        confirmParams.leftMargin = dp(activity, 10);
        buttons.addView(confirm, confirmParams);
        gap(modal.panel, activity, buttons, 16);
        present(activity, modal);
    }

    static void showChoices(Activity activity, String title, String message,
            List<String> choices, ChoiceAction action) {
        Modal modal = modal(activity, title, message);
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < choices.size(); i++) {
            int index = i;
            TextView row = text(activity, choices.get(i), 15, INK, false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
            row.setBackground(round(activity, FIELD, 12));
            row.setOnClickListener(view -> {
                modal.dialog.dismiss();
                action.run(index);
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 50));
            if (i > 0) {
                rowParams.topMargin = dp(activity, 8);
            }
            list.addView(row, rowParams);
        }
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(dp(activity, 300), dp(activity, choices.size() * 58)));
        scrollParams.topMargin = dp(activity, 16);
        modal.panel.addView(scroll, scrollParams);
        TextView cancel = button(activity, "取消", SURFACE, MUTED);
        cancel.setBackground(stroke(activity, SURFACE, 12, OUTLINE));
        cancel.setOnClickListener(view -> modal.dialog.dismiss());
        gap(modal.panel, activity, cancel, 14);
        present(activity, modal);
    }

    private static Modal modal(Activity activity, String title, String message) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 20), dp(activity, 18),
                dp(activity, 20), dp(activity, 20));
        panel.setBackground(round(activity, SURFACE, 22));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(activity, title, 20, INK, true),
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView close = text(activity, "×", 26, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭");
        close.setOnClickListener(view -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(
                dp(activity, 40), dp(activity, 40)));
        panel.addView(header);
        if (message != null && !message.isEmpty()) {
            TextView detail = text(activity, message, 13, MUTED, false);
            detail.setLineSpacing(0, 1.15f);
            gap(panel, activity, detail, 4);
        }
        dialog.setContentView(panel);
        return new Modal(dialog, panel, header);
    }

    private static void present(Activity activity, Modal modal) {
        modal.dialog.show();
        Window window = modal.dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = Math.min(dp(activity, 360),
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32));
        attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attributes.dimAmount = 0.32f;
        window.setAttributes(attributes);
        makeDraggable(modal.header, window);
    }

    private static void makeDraggable(View handle, Window window) {
        float[] down = new float[2];
        int[] start = new int[2];
        handle.setOnTouchListener((view, event) -> {
            WindowManager.LayoutParams attributes = window.getAttributes();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                down[0] = event.getRawX();
                down[1] = event.getRawY();
                start[0] = attributes.x;
                start[1] = attributes.y;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                attributes.x = start[0] + Math.round(event.getRawX() - down[0]);
                attributes.y = start[1] + Math.round(event.getRawY() - down[1]);
                window.setAttributes(attributes);
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_UP;
        });
    }

    private static final class Modal {
        final Dialog dialog;
        final LinearLayout panel;
        final View header;

        Modal(Dialog dialog, LinearLayout panel, View header) {
            this.dialog = dialog;
            this.panel = panel;
            this.header = header;
        }
    }

    private static int statusBarHeight(Activity activity) {
        int resourceId = activity.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        return resourceId > 0
                ? activity.getResources().getDimensionPixelSize(resourceId)
                : dp(activity, 24);
    }

    private static void applyTopInset(Activity activity, LinearLayout bar, int topInset) {
        bar.setPadding(0, topInset, 0, 0);
        bar.setMinimumHeight(dp(activity, 56) + topInset);
    }
}
