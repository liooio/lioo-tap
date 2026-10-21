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
    static final int CLICKER_PANEL_HEIGHT_DP = 184;
    static final int CLICKER_PANEL_WIDTH_DP = 64;
    static final String CLICKER_POSITION_KEY = "clicker_controls_left";
    static final int CLICKER_PANEL_LEFT_MARGIN_DP = 12;

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
    private final ImageView resetButton;
    private final ImageView runIcon;
    private final LinearLayout categoryMenu;
    private final LinearLayout categoryContent;
    private final TextView[] categoryItems = new TextView[3];
    private final LinearLayout[] categoryGroups = new LinearLayout[3];

    ClickerToolboxView(Context context, DisplayMetrics displayMetrics,
            OutsideTouchLayout.Listener outsideTouchListener, Runnable selectAction,
            Runnable recordAction, Runnable editAction, Runnable runAction,
            Runnable exitAction, Runnable resetAllPositionsAction,
            List<Library.Entry> workflows,
            ShortcutAction workflowAction, List<Library.Entry> shortcuts,
            ShortcutAction shortcutAction) {
        this.context = context;
        this.displayMetrics = displayMetrics;
        root = new OutsideTouchLayout(context, outsideTouchListener);
        root.setOrientation(LinearLayout.VERTICAL);

        toolPicker = panel();
        pickerDragHandle = addGrip(toolPicker);
        resetButton = FloatingWindowDragController.createResetButton(
                context, () -> {
                    resetAllPositionsAction.run();
                    Toast.makeText(context, "已恢复所有悬浮窗默认位置",
                            Toast.LENGTH_SHORT).show();
                });
        resetButton.setContentDescription("重置所有悬浮窗位置");
        addResetButton(pickerDragHandle);
        categoryMenu = new LinearLayout(context);
        categoryMenu.setOrientation(LinearLayout.VERTICAL);
        categoryContent = new LinearLayout(context);
        categoryContent.setOrientation(LinearLayout.VERTICAL);

        addToolGroup("基础功能",
                compactTool(R.drawable.normal_script_glyph, "连点器", false, selectAction),
                compactTool(R.drawable.record_script_glyph, "录制脚本", false, recordAction),
                compactTool(R.drawable.info, "更多", true, () -> comingSoon("更多工具")));

        List<View> workflowTools = new java.util.ArrayList<>();
        for (Library.Entry workflow : workflows) {
            workflowTools.add(compactTool(R.drawable.workflow_glyph, workflow.name, false,
                    () -> workflowAction.run(workflow)));
        }
        addToolGroup("脚本", workflowTools.isEmpty()
                ? new View[] {compactEmpty("暂无脚本")}
                : workflowTools.toArray(new View[0]));

        List<View> shortcutTools = new java.util.ArrayList<>();
        for (Library.Entry shortcut : shortcuts) {
            String symbol = shortcut.name.isEmpty()
                    ? "键" : shortcut.name.substring(0, 1);
            shortcutTools.add(compactTool(symbol, shortcut.name,
                    ShortcutKey.color(shortcut), () -> shortcutAction.run(shortcut)));
        }
        addToolGroup("快捷键", shortcutTools.isEmpty()
                ? new View[] {compactEmpty("暂无快捷键")}
                : shortcutTools.toArray(new View[0]));

        LinearLayout navigation = new LinearLayout(context);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        ScrollView menuScroll = new ScrollView(context);
        menuScroll.setVerticalScrollBarEnabled(false);
        menuScroll.addView(categoryMenu, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        navigation.addView(menuScroll, new LinearLayout.LayoutParams(
                dp(92), LinearLayout.LayoutParams.MATCH_PARENT));
        View divider = new View(context);
        divider.setBackgroundColor(0xFF343B4B);
        navigation.addView(divider, new LinearLayout.LayoutParams(dp(1),
                LinearLayout.LayoutParams.MATCH_PARENT));
        ScrollView contentScroll = new ScrollView(context);
        contentScroll.setVerticalScrollBarEnabled(false);
        contentScroll.addView(categoryContent, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        contentParams.leftMargin = dp(8);
        navigation.addView(contentScroll, contentParams);
        toolPicker.addView(navigation, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        selectCategory(0);

        actionBar = panel();
        actionDragHandle = addGrip(actionBar);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.VERTICAL);
        actionBar.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout run = action(R.drawable.ic_clicker_play, "开始", runAction);
        runIcon = (ImageView) run.getChildAt(0);
        runIcon.setContentDescription("开始点击");
        actions.addView(run, clickerActionParams());
        actions.addView(action(R.drawable.ic_record_settings, "设置", editAction),
                clickerActionParams());
        actions.addView(action(R.drawable.ic_clicker_exit, "关闭工具箱", exitAction),
                clickerActionParams());

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

    View[] dragHandles() {
        return new View[] {pickerDragHandle, actionDragHandle};
    }

    void setActionDragListener(View.OnTouchListener listener) {
        actionDragHandle.setOnTouchListener(listener);
    }

    void setRunning(boolean running) {
        runIcon.setImageResource(running
                ? R.drawable.ic_clicker_stop : R.drawable.ic_clicker_play);
        runIcon.setColorFilter(Color.WHITE);
        runIcon.setContentDescription(running ? "停止点击" : "开始点击");
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(6), dp(10), dp(8));
        panel.setBackground(background(PANEL_COLOR, dp(22)));
        panel.setElevation(dp(8));
        return panel;
    }

    private View addGrip(LinearLayout parent) {
        LinearLayout gripRow = new LinearLayout(context);
        gripRow.setOrientation(LinearLayout.HORIZONTAL);
        gripRow.setGravity(Gravity.CENTER_VERTICAL);
        gripRow.setContentDescription("拖动工具栏");
        View grip = new View(context);
        grip.setBackground(background(0xFF687084, dp(2)));
        LinearLayout gripContainer = new LinearLayout(context);
        gripContainer.setGravity(Gravity.CENTER);
        gripContainer.addView(grip, new LinearLayout.LayoutParams(dp(36), dp(4)));
        gripRow.addView(gripContainer, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        parent.addView(gripRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        return gripRow;
    }

    private void addResetButton(View handle) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(28), dp(28));
        params.leftMargin = dp(8);
        ((LinearLayout) handle).addView(resetButton, params);
    }

    private View compactTool(int iconResource, String title, boolean muted,
            Runnable callback) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(8), 0, dp(10), 0);
        item.setBackground(background(0xFF252D3E, dp(12)));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(title);
        item.setOnClickListener(view -> callback.run());

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconResource);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAlpha(muted ? 0.42f : 1f);
        item.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView caption = label(title, 12,
                muted ? 0xFF73798A : MUTED_COLOR, false);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                dp(66), LinearLayout.LayoutParams.WRAP_CONTENT);
        captionParams.leftMargin = dp(6);
        item.addView(caption, captionParams);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        return item;
    }

    private View compactTool(String symbol, String title, int color, Runnable callback) {
        LinearLayout item = (LinearLayout) compactTool(R.drawable.workflow_glyph,
                title, false, callback);
        ImageView icon = (ImageView) item.getChildAt(0);
        item.removeView(icon);
        TextView symbolView = label(symbol, 16, color, true);
        symbolView.setGravity(Gravity.CENTER);
        symbolView.setBackground(background(0xFF354156, dp(9)));
        item.addView(symbolView, 0, new LinearLayout.LayoutParams(dp(26), dp(26)));
        return item;
    }

    private View compactEmpty(String title) {
        TextView empty = label(title, 12, MUTED_COLOR, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(10), 0, dp(10), 0);
        empty.setBackground(background(0xFF252D3E, dp(12)));
        empty.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        return empty;
    }

    private void addToolGroup(String title, View... tools) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        int index = categoryMenu.getChildCount();

        TextView menuItem = label(title, 12, MUTED_COLOR, false);
        menuItem.setGravity(Gravity.CENTER_VERTICAL);
        menuItem.setPadding(dp(10), 0, dp(6), 0);
        menuItem.setMinHeight(dp(42));
        menuItem.setClickable(true);
        menuItem.setFocusable(true);
        menuItem.setOnClickListener(view -> selectCategory(index));
        categoryItems[index] = menuItem;
        categoryMenu.addView(menuItem, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        for (int i = 0; i < tools.length; i += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int column = 0; column < 2 && i + column < tools.length; column++) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, dp(48), 1);
                if (column == 0) {
                    params.rightMargin = dp(4);
                } else {
                    params.leftMargin = dp(4);
                }
                row.addView(tools[i + column], params);
            }
            if (i + 1 >= tools.length) {
                row.addView(new View(context), new LinearLayout.LayoutParams(
                        0, dp(48), 1));
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            rowParams.bottomMargin = dp(6);
            group.addView(row, rowParams);
        }
        categoryGroups[index] = group;
    }

    private void selectCategory(int index) {
        categoryContent.removeAllViews();
        categoryContent.addView(categoryGroups[index], new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < categoryItems.length; i++) {
            categoryItems[i].setTextColor(i == index ? Color.WHITE : MUTED_COLOR);
            categoryItems[i].setBackground(i == index
                    ? background(0xFF354156, dp(10)) : null);
        }
    }

    private LinearLayout action(int iconResource, String title, Runnable callback) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(title);
        item.setBackground(background(0xFF252D3E, dp(14)));
        item.setOnClickListener(view -> callback.run());

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconResource);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        icon.setContentDescription(title);
        item.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        return item;
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

    private void comingSoon(String feature) {
        Toast.makeText(context, feature + "敬请期待", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams clickerActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
        params.bottomMargin = dp(4);
        return params;
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
