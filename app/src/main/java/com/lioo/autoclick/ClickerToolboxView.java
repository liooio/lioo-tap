package com.lioo.autoclick;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 两级悬浮工具箱：第一级选择工具，第二级显示连点器操作。 */
final class ClickerToolboxView {
    interface ShortcutAction {
        void run(Library.Entry entry);
    }

    private static final int PANEL_COLOR = 0xF21B1E2C;
    private static final int MUTED_COLOR = 0xFFABB1C2;

    private final Context context;
    private final DisplayMetrics displayMetrics;
    private final OutsideTouchLayout root;
    private final LinearLayout toolPicker;
    private final LinearLayout actionBar;
    private final View pickerDragHandle;
    private final View actionDragHandle;
    private final TextView runIcon;
    private final TextView runLabel;

    ClickerToolboxView(Context context, DisplayMetrics displayMetrics,
            OutsideTouchLayout.Listener outsideTouchListener, Runnable selectAction,
            Runnable recordAction, Runnable editAction, Runnable runAction,
            Runnable exitAction, List<Library.Entry> workflows,
            ShortcutAction workflowAction, List<Library.Entry> shortcuts,
            ShortcutAction shortcutAction) {
        this.context = context;
        this.displayMetrics = displayMetrics;
        root = new OutsideTouchLayout(context, outsideTouchListener);
        root.setOrientation(LinearLayout.VERTICAL);

        toolPicker = panel();
        pickerDragHandle = addGrip(toolPicker);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(sectionLabel("基础功能"));
        LinearLayout basics = gridRow();
        basics.addView(tool(R.drawable.normal_script_glyph, "连点器", false, selectAction),
                gridItemParams());
        basics.addView(tool(R.drawable.record_script_glyph, "录制脚本", false, recordAction),
                gridItemParams());
        basics.addView(tool(R.drawable.info, "敬请期待", true,
                () -> comingSoon("更多工具")), gridItemParams());
        content.addView(basics);

        TextView workflowTitle = sectionLabel("自定义脚本");
        LinearLayout.LayoutParams workflowTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        workflowTitleParams.topMargin = dp(8);
        content.addView(workflowTitle, workflowTitleParams);
        if (workflows.isEmpty()) {
            content.addView(emptyState("暂无自定义脚本"));
        } else {
            for (int start = 0; start < workflows.size(); start += 3) {
                LinearLayout row = gridRow();
                for (int column = 0; column < 3; column++) {
                    int index = start + column;
                    if (index < workflows.size()) {
                        Library.Entry workflow = workflows.get(index);
                        row.addView(tool(R.drawable.workflow_glyph, workflow.name, false,
                                        () -> workflowAction.run(workflow)),
                                gridItemParams());
                    } else {
                        row.addView(new View(context), gridItemParams());
                    }
                }
                content.addView(row);
            }
        }

        TextView shortcutTitle = sectionLabel("快捷键");
        LinearLayout.LayoutParams shortcutTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        shortcutTitleParams.topMargin = dp(8);
        content.addView(shortcutTitle, shortcutTitleParams);
        if (shortcuts.isEmpty()) {
            content.addView(emptyState("暂无工具箱快捷键"));
        } else {
            for (int start = 0; start < shortcuts.size(); start += 3) {
                LinearLayout row = gridRow();
                for (int column = 0; column < 3; column++) {
                    int index = start + column;
                    if (index < shortcuts.size()) {
                        Library.Entry shortcut = shortcuts.get(index);
                        String symbol = shortcut.name.isEmpty()
                                ? "键" : shortcut.name.substring(0, 1);
                        row.addView(tool(symbol, shortcut.name,
                                        ShortcutKey.color(shortcut),
                                        () -> shortcutAction.run(shortcut)),
                                gridItemParams());
                    } else {
                        row.addView(new View(context), gridItemParams());
                    }
                }
                content.addView(row);
            }
        }
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        toolPicker.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        actionBar = panel();
        actionDragHandle = addGrip(actionBar);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout run = action("▶", "开始", 0xFF57B4F6, runAction);
        runIcon = (TextView) run.getChildAt(0);
        runLabel = (TextView) run.getChildAt(1);
        actions.addView(run, weightedActionParams());
        actions.addView(action("✎", "编辑", Color.WHITE, editAction),
                weightedActionParams());
        actions.addView(action("↩", "退出", Color.WHITE, exitAction),
                weightedActionParams());

        root.addView(toolPicker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
    }

    OutsideTouchLayout view() {
        return root;
    }

    void showClicker() {
        root.removeAllViews();
        root.addView(actionBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
    }

    void setDragListener(View.OnTouchListener listener) {
        pickerDragHandle.setOnTouchListener(listener);
        actionDragHandle.setOnTouchListener(listener);
    }

    void setRunning(boolean running) {
        runIcon.setText(running ? "■" : "▶");
        runLabel.setText(running ? "停止" : "开始");
        int color = running ? 0xFFFF7777 : 0xFF57B4F6;
        runIcon.setTextColor(color);
        runLabel.setTextColor(color);
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(12));
        panel.setBackground(background(PANEL_COLOR, dp(22)));
        panel.setElevation(dp(8));
        return panel;
    }

    private View addGrip(LinearLayout parent) {
        LinearLayout gripRow = new LinearLayout(context);
        gripRow.setGravity(Gravity.CENTER);
        gripRow.setContentDescription("拖动工具栏");
        View grip = new View(context);
        grip.setBackground(background(0xFF687084, dp(2)));
        gripRow.addView(grip, new LinearLayout.LayoutParams(dp(36), dp(4)));
        parent.addView(gripRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(18)));
        return gripRow;
    }

    private LinearLayout tool(String symbol, String title, int color, Runnable callback) {
        return item(symbol, title, color, 28, dp(40), callback);
    }

    private LinearLayout tool(int iconResource, String title, boolean muted,
            Runnable callback) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(title);
        item.setOnClickListener(view -> callback.run());

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconResource);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAlpha(muted ? 0.42f : 1f);
        item.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        TextView caption = label(title, 12,
                muted ? 0xFF73798A : MUTED_COLOR, false);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = dp(3);
        item.addView(caption, captionParams);
        return item;
    }

    private TextView emptyState(String message) {
        TextView empty = label(message, 12, MUTED_COLOR, false);
        empty.setGravity(Gravity.CENTER);
        empty.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        return empty;
    }

    private LinearLayout toolRow(String symbol, String title, int color, Runnable callback) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(8), dp(2), dp(8));
        item.setMinimumHeight(dp(90));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(title);
        item.setOnClickListener(view -> callback.run());

        TextView icon = label(symbol, 31, color, false);
        icon.setGravity(Gravity.CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        TextView caption = label(title, 13,
                color == 0xFF73798A ? color : MUTED_COLOR, false);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = dp(4);
        item.addView(caption, captionParams);
        return item;
    }

    private LinearLayout action(String symbol, String title, int color, Runnable callback) {
        return item(symbol, title, color, 21, dp(27), callback);
    }

    private LinearLayout item(String symbol, String title, int color, int iconSizeSp,
            int iconHeight, Runnable callback) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(title);
        item.setOnClickListener(view -> callback.run());

        TextView icon = label(symbol, iconSizeSp, color, false);
        icon.setGravity(Gravity.CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, iconHeight));
        TextView caption = label(title, 12, color == 0xFF73798A ? color : MUTED_COLOR, false);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        item.addView(caption, captionParams);
        return item;
    }

    private TextView sectionLabel(String value) {
        TextView title = label(value, 12, 0xFFD5DBE8, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(4), 0, dp(4), 0);
        return title;
    }

    private LinearLayout gridRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams gridItemParams() {
        return new LinearLayout.LayoutParams(0, dp(82), 1);
    }

    private void comingSoon(String feature) {
        Toast.makeText(context, feature + "敬请期待", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams weightedActionParams() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
    }

    private TextView label(String value, int sizeSp, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return text;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(radius);
        return result;
    }

    private int dp(int value) {
        return Math.round(value * displayMetrics.density);
    }
}
