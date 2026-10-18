package com.lioo.autoclick;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private final Map<String, TextView> moduleCounts = new HashMap<>();
    private TextView overlayButton;
    private TextView overlayHint;
    private TextView accessState;
    private TextView accessHint;
    private TextView accessAction;
    private View accessDot;
    private LinearLayout accessStatus;
    private HorizontalScrollView permissionPager;
    private TextView permissionStepOne;
    private TextView permissionStepTwo;
    private int permissionCardWidth;
    private Boolean lastAccessibilityEnabled;
    private boolean waitingForOverlayPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean accessibilityEnabled = ClickAccessibilityService.isEnabled(this);
        boolean overlayPermission = OverlayAccess.hasPermission(this);
        if (accessibilityEnabled && waitingForOverlayPermission
                && overlayPermission) {
            waitingForOverlayPermission = false;
            OverlayService.show(this);
        }
        boolean shouldRestore = ClickSettings.shouldShowOverlay(this);
        if (shouldRestore && accessibilityEnabled && overlayPermission) {
            if (!OverlayService.isVisible()) {
                OverlayService.show(this);
            }
        } else if (shouldRestore && OverlayService.getInstance() != null) {
            OverlayService.getInstance().suspendForMissingPermission();
        }
        if (shouldRestore && permissionPager != null
                && (!accessibilityEnabled || !overlayPermission)) {
            int permissionStep = accessibilityEnabled ? 1 : 0;
            permissionPager.post(() -> scrollToPermissionStep(permissionStep));
        }
        refresh();
        overlayButton.postDelayed(this::refresh, 200);
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(Ui.gradient(this, 0xFFF8FBFF, 0xFFF2F6FD, 0));
        LinearLayout page = column(20, 10, 20, 28);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int safeTop = insets.getSystemWindowInsetTop();
            DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) {
                safeTop = Math.max(safeTop, cutout.getSafeInsetTop());
            }
            int safeBottom = Math.max(Ui.dp(this, 24),
                    insets.getSystemWindowInsetBottom());
            view.setPadding(0, safeTop, 0, safeBottom);
            return insets;
        });
        scroll.addView(page);

        page.addView(topBar());
        TextView pageTitle = Ui.text(this, "设置运行权限", 24, 0xFF111A38, true);
        Ui.gap(page, this, pageTitle, 28);
        TextView pageSubtitle = Ui.text(this, "按顺序完成授权，即可使用悬浮控制", 14,
                0xFF7180A0, false);
        Ui.gap(page, this, pageSubtitle, 5);
        Ui.gap(page, this, permissionCards(), 16);

        LinearLayout automationTitle = new LinearLayout(this);
        automationTitle.setOrientation(LinearLayout.HORIZONTAL);
        automationTitle.setGravity(Gravity.CENTER_VERTICAL);
        automationTitle.addView(Ui.text(this, "自动化工作台", 20, 0xFF111A38, true), weight());
        TextView allFeatures = Ui.text(this, "4 个模块", 12, Ui.MUTED, true);
        allFeatures.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        allFeatures.setBackground(Ui.round(this, 0xFFEAF2FF, 20));
        automationTitle.addView(allFeatures);
        Ui.gap(page, this, automationTitle, 28);

        LinearLayout moduleGrid = column(0, 0, 0, 0);
        moduleGrid.addView(modulePair(
                moduleTile(R.drawable.normal_script, "普通脚本", "点击、滑动与循环",
                        Library.SCRIPT, Ui.PRIMARY),
                moduleTile(R.drawable.record_script, "录制脚本", "录下操作并回放",
                        Library.RECORD, Ui.GREEN)));
        LinearLayout secondRow = modulePair(
                moduleTile(R.drawable.workflow, "自定义流程", "组合录制并循环执行",
                        Library.GROUP, Ui.PURPLE),
                moduleTile(R.drawable.quick_keys, "快捷按键", "悬浮按钮一键运行",
                        Library.KEY, 0xFFFF8A00));
        Ui.gap(moduleGrid, this, secondRow, 14);
        Ui.gap(page, this, moduleGrid, 14);

        LinearLayout tip = new LinearLayout(this);
        tip.setGravity(Gravity.CENTER_VERTICAL);
        tip.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                Ui.dp(this, 14), Ui.dp(this, 12));
        tip.setBackground(Ui.stroke(this, 0xFFF8FBFF, 16, 0xFFE3ECF8));
        ImageView info = image(R.drawable.info, 22, "提示");
        tip.addView(info, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        TextView tipText = Ui.text(this, "执行前请确认目标页面与点击位置", 13,
                0xFF7180A0, false);
        LinearLayout.LayoutParams tipTextParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tipTextParams.leftMargin = Ui.dp(this, 10);
        tip.addView(tipText, tipTextParams);
        Ui.gap(page, this, tip, 18);
        setContentView(scroll);

        page.setAlpha(0f);
        page.setTranslationY(Ui.dp(this, 12));
        page.animate().alpha(1f).translationY(0f).setDuration(360).start();
    }

    private View permissionCards() {
        LinearLayout section = column(0, 0, 0, 0);
        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.HORIZONTAL);
        steps.setGravity(Gravity.CENTER_VERTICAL);
        steps.setPadding(Ui.dp(this, 4), Ui.dp(this, 4),
                Ui.dp(this, 4), Ui.dp(this, 4));
        steps.setBackground(Ui.round(this, 0xFFFFFFFF, 24));
        steps.setElevation(Ui.dp(this, 1));
        permissionStepOne = permissionStep("1  无障碍");
        permissionStepTwo = permissionStep("2  悬浮窗");
        permissionStepOne.setOnClickListener(view -> scrollToPermissionStep(0));
        permissionStepTwo.setOnClickListener(view -> scrollToPermissionStep(1));
        steps.addView(permissionStepOne, new LinearLayout.LayoutParams(
                0, Ui.dp(this, 42), 1));
        TextView arrow = Ui.text(this, "›", 20, 0xFF9AABC5, true);
        arrow.setGravity(Gravity.CENTER);
        steps.addView(arrow, new LinearLayout.LayoutParams(
                Ui.dp(this, 34), Ui.dp(this, 42)));
        steps.addView(permissionStepTwo, new LinearLayout.LayoutParams(
                0, Ui.dp(this, 42), 1));
        section.addView(steps);

        permissionPager = new HorizontalScrollView(this);
        permissionPager.setHorizontalScrollBarEnabled(false);
        permissionPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        permissionPager.setClipToPadding(false);
        permissionPager.setPadding(0, 0, Ui.dp(this, 18), 0);
        permissionPager.setOnScrollChangeListener((view, x, y, oldX, oldY) ->
                updatePermissionStep(x > permissionCardWidth / 2 ? 1 : 0));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        permissionCardWidth = Math.min(Ui.dp(this, 390),
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 58));
        LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(
                permissionCardWidth, Ui.dp(this, 252));
        firstParams.rightMargin = Ui.dp(this, 14);
        row.addView(readinessCard(), firstParams);
        row.addView(overlayCard(), new LinearLayout.LayoutParams(
                permissionCardWidth, Ui.dp(this, 252)));
        permissionPager.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams pagerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pagerParams.topMargin = Ui.dp(this, 14);
        section.addView(permissionPager, pagerParams);
        updatePermissionStep(0);
        return section;
    }

    private TextView permissionStep(String label) {
        TextView step = Ui.text(this, label, 14, 0xFF647391, true);
        step.setGravity(Gravity.CENTER);
        step.setClickable(true);
        step.setFocusable(true);
        return step;
    }

    private void scrollToPermissionStep(int step) {
        if (permissionPager == null) {
            return;
        }
        permissionPager.smoothScrollTo(step == 0 ? 0 : permissionCardWidth + Ui.dp(this, 12), 0);
        updatePermissionStep(step);
    }

    private void updatePermissionStep(int step) {
        if (permissionStepOne == null || permissionStepTwo == null) {
            return;
        }
        permissionStepOne.setTextColor(step == 0 ? Ui.ON_PRIMARY : 0xFF647391);
        permissionStepTwo.setTextColor(step == 1 ? Ui.ON_PRIMARY : 0xFF647391);
        permissionStepOne.setBackground(step == 0
                ? Ui.gradient(this, 0xFF42ABFF, 0xFF1761FF, 22)
                : Ui.round(this, 0xFFF6F9FF, 22));
        permissionStepTwo.setBackground(step == 1
                ? Ui.gradient(this, 0xFF42ABFF, 0xFF1761FF, 22)
                : Ui.round(this, 0xFFF6F9FF, 22));
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = image(R.drawable.app_logo, 50, "连点器图标");
        bar.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 50)));

        LinearLayout titles = column(0, 0, 0, 0);
        titles.addView(Ui.text(this, "连点器", 24, 0xFF111A38, true));
        TextView subtitle = Ui.text(this, "轻松完成重复操作", 13, 0xFF7180A0, false);
        Ui.gap(titles, this, subtitle, 1);
        LinearLayout.LayoutParams titleParams = weight();
        titleParams.leftMargin = Ui.dp(this, 12);
        bar.addView(titles, titleParams);

        ImageView help = image(R.drawable.help, 42, "使用帮助");
        help.setContentDescription("使用帮助");
        help.setOnClickListener(view -> showHelp());
        pressFeedback(help);
        bar.addView(help, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        return bar;
    }

    private View readinessCard() {
        LinearLayout card = column(20, 18, 20, 18);
        card.setMinimumHeight(Ui.dp(this, 252));
        card.setBackground(Ui.stroke(this, 0xFFFFFFFF, 24, 0xFFE7EFFA));
        card.setElevation(Ui.dp(this, 3));
        accessStatus = new LinearLayout(this);
        accessStatus.setOrientation(LinearLayout.HORIZONTAL);
        accessStatus.setGravity(Gravity.CENTER_VERTICAL);
        accessStatus.setPadding(Ui.dp(this, 10), Ui.dp(this, 6),
                Ui.dp(this, 10), Ui.dp(this, 6));
        accessStatus.setBackground(Ui.round(this, 0xFFF6F9FF, 16));
        accessDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFF9AABC5);
        accessDot.setBackground(dot);
        accessStatus.addView(accessDot,
                new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8)));
        accessState = Ui.text(this, "等待开启", 12, 0xFF647391, true);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stateParams.leftMargin = Ui.dp(this, 8);
        accessStatus.addView(accessState, stateParams);
        card.addView(accessStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column(0, 0, 0, 0);
        TextView title = Ui.text(this, "开启无障碍服务", 21, 0xFF111A38, true);
        title.setLineSpacing(Ui.dp(this, 3), 1f);
        copy.addView(title);
        accessHint = Ui.text(this, "必须先开启点击服务，完成后才能设置悬浮控制。", 13,
                0xFF7180A0, false);
        accessHint.setLineSpacing(Ui.dp(this, 3), 1f);
        Ui.gap(copy, this, accessHint, 8);
        content.addView(copy, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ImageView illustration = image(R.drawable.permission_illustration, 118, "授权示意");
        LinearLayout.LayoutParams illustrationParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 118), Ui.dp(this, 94));
        illustrationParams.leftMargin = Ui.dp(this, 8);
        content.addView(illustration, illustrationParams);
        Ui.gap(card, this, content, 12);

        accessAction = Ui.button(this, "开启无障碍服务", Ui.PRIMARY, Ui.ON_PRIMARY);
        accessAction.setBackground(Ui.gradient(this, 0xFF43AFFF, 0xFF1766FF, 18));
        accessAction.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        pressFeedback(accessAction);
        Ui.gap(card, this, accessAction, 14);
        return card;
    }

    private View overlayCard() {
        LinearLayout card = column(20, 18, 20, 18);
        card.setMinimumHeight(Ui.dp(this, 252));
        card.setBackground(Ui.stroke(this, 0xFFFFFFFF, 24, 0xFFE7EFFA));
        card.setElevation(Ui.dp(this, 3));

        TextView step = Ui.text(this, "第 2 步", 12, 0xFF647391, true);
        step.setGravity(Gravity.CENTER);
        step.setPadding(Ui.dp(this, 10), Ui.dp(this, 6),
                Ui.dp(this, 10), Ui.dp(this, 6));
        step.setBackground(Ui.round(this, 0xFFF6F9FF, 16));
        card.addView(step, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column(0, 0, 0, 0);
        copy.addView(Ui.text(this, "开启悬浮控制", 21, 0xFF111A38, true));
        overlayHint = Ui.text(this,
                "完成第 1 步后，才能授权显示在其他应用上层。",
                13, 0xFF7180A0, false);
        overlayHint.setLineSpacing(Ui.dp(this, 3), 1f);
        Ui.gap(copy, this, overlayHint, 8);
        content.addView(copy, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ImageView illustration = image(R.drawable.permission_illustration, 118, "悬浮授权示意");
        LinearLayout.LayoutParams illustrationParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 118), Ui.dp(this, 94));
        illustrationParams.leftMargin = Ui.dp(this, 8);
        content.addView(illustration, illustrationParams);
        Ui.gap(card, this, content, 12);
        overlayButton = Ui.button(this, "请先完成第 1 步", Ui.INK, Ui.ON_PRIMARY);
        overlayButton.setOnClickListener(view -> onOverlayPressed());
        pressFeedback(overlayButton);
        LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(1, 0, 1);
        card.addView(new View(this), spacer);
        Ui.gap(card, this, overlayButton, 12);
        return card;
    }

    private LinearLayout modulePair(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, weight());
        LinearLayout.LayoutParams rightParams = weight();
        rightParams.leftMargin = Ui.dp(this, 12);
        row.addView(right, rightParams);
        return row;
    }

    private View moduleTile(int iconResource, String title, String subtitle, String kind,
            int accentColor) {
        LinearLayout tile = column(16, 16, 14, 14);
        tile.setMinimumHeight(Ui.dp(this, 184));
        tile.setBackground(Ui.stroke(this, 0xFFFFFFFF, 24, 0xFFE7EFFA));
        tile.setElevation(Ui.dp(this, 2));
        tile.setOnClickListener(view -> openLibrary(kind));
        pressFeedback(tile);
        ImageView icon = image(iconResource, 56, title);
        tile.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        TextView name = Ui.text(this, title, 16, 0xFF111A38, true);
        Ui.gap(tile, this, name, 13);
        TextView detail = Ui.text(this, subtitle, 12, 0xFF7180A0, false);
        Ui.gap(tile, this, detail, 4);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = Ui.text(this, "0 项", 12, Ui.MUTED, true);
        count.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        footer.addView(count, weight());
        moduleCounts.put(kind, count);
        ImageView arrow = image(R.drawable.chevron_right, 18, "进入");
        arrow.setColorFilter(accentColor);
        footer.addView(arrow, new LinearLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18)));
        Ui.gap(tile, this, footer, 13);
        return tile;
    }

    private void onOverlayPressed() {
        if (!ClickAccessibilityService.isEnabled(this)) {
            Toast.makeText(this, "请先开启连点器无障碍服务", Toast.LENGTH_SHORT).show();
            return;
        }
        if (OverlayService.isVisible()) {
            OverlayService.getInstance().hideOverlay();
        } else {
            if (!OverlayAccess.hasPermission(this)) {
                waitingForOverlayPermission = true;
                OverlayAccess.requestPermission(this);
                return;
            }
            OverlayService.show(this);
            if (!BackgroundAccess.isProtected(this)
                    && !ClickSettings.hasAskedBackgroundProtection(this)) {
                ClickSettings.setAskedBackgroundProtection(this);
                Ui.showMessage(this, "后台运行保护",
                        "允许忽略电池优化，可降低锁屏或切换应用后悬浮控制被系统清理的概率。"
                                + "部分 MIUI 设备还需要在系统设置中允许自启动。",
                        "前往设置", () -> BackgroundAccess.request(this));
            }
        }
        overlayButton.postDelayed(this::refresh, 200);
    }

    private void refresh() {
        if (accessHint == null) {
            return;
        }
        boolean enabled = ClickAccessibilityService.isEnabled(this);
        boolean visible = OverlayService.isVisible();
        boolean overlayPermission = OverlayAccess.hasPermission(this);
        boolean shouldRestore = ClickSettings.shouldShowOverlay(this);
        accessState.setText(enabled ? "授权已完成" : "等待开启");
        accessState.setTextColor(enabled ? 0xFF16875D : 0xFF647391);
        accessStatus.setBackground(Ui.round(this,
                enabled ? 0xFFEDF9F3 : 0xFFF6F9FF, 16));
        accessHint.setText(enabled
                ? "无障碍服务已开启，现在可以继续设置悬浮控制。"
                : "必须先开启点击服务，完成后才能设置悬浮控制。");
        ((GradientDrawable) accessDot.getBackground()).setColor(
                enabled ? 0xFF20BF7A : 0xFF9AABC5);
        accessAction.setText(enabled ? "管理无障碍服务" : "开启无障碍服务");
        overlayHint.setText(!enabled
                ? (shouldRestore
                        ? "恢复无障碍权限后，将自动重新显示悬浮控制。"
                        : "完成第 1 步后，才能授权显示在其他应用上层。")
                : (!overlayPermission && shouldRestore
                        ? "重新授予悬浮窗权限后，将自动恢复上次开启状态。"
                        : "授权显示在其他应用上层，然后打开悬浮工具箱。"));
        overlayButton.setEnabled(enabled);
        overlayButton.setAlpha(enabled ? 1f : 0.45f);
        overlayButton.setText(!enabled ? "请先完成第 1 步"
                : (visible ? "隐藏悬浮控制"
                        : (!overlayPermission && shouldRestore
                                ? "重新授权悬浮窗" : "开启悬浮控制")));
        overlayButton.setBackground(enabled
                ? Ui.gradient(this, visible ? 0xFF43AFFF : 0xFF263553,
                        visible ? 0xFF1766FF : 0xFF111A38, 18)
                : Ui.round(this, 0xFF111A38, 18));
        if (lastAccessibilityEnabled != null && !lastAccessibilityEnabled && enabled
                && permissionPager != null) {
            permissionPager.post(() -> permissionPager.smoothScrollTo(
                    permissionCardWidth + Ui.dp(this, 12), 0));
        }
        lastAccessibilityEnabled = enabled;
        for (Map.Entry<String, TextView> item : moduleCounts.entrySet()) {
            int count = Library.list(this, item.getKey()).size();
            item.getValue().setText(String.format(Locale.CHINA, "%d 项", count));
        }
    }

    private void showHelp() {
        Ui.showMessage(this, "如何开始",
                "1. 先开启连点器无障碍服务\n"
                        + "2. 返回应用，再授权悬浮窗权限\n"
                        + "3. 打开悬浮工具箱并点击连点器\n"
                        + "4. 编辑参数、拖动目标点并点击开始",
                "知道了", null);
    }

    private void openLibrary(String kind) {
        startActivity(new Intent(this, LibraryActivity.class).putExtra("kind", kind));
    }

    private ImageView image(int resource, int sizeDp, String description) {
        ImageView view = new ImageView(this);
        view.setImageResource(resource);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setContentDescription(description);
        view.setAdjustViewBounds(true);
        view.setMinimumWidth(Ui.dp(this, sizeDp));
        view.setMinimumHeight(Ui.dp(this, sizeDp));
        return view;
    }

    private void pressFeedback(View view) {
        view.setClickable(true);
        view.setOnTouchListener((target, event) -> {
            if (!target.isEnabled()) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                target.animate().scaleX(0.975f).scaleY(0.975f)
                        .setDuration(80).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                target.animate().scaleX(1f).scaleY(1f)
                        .setDuration(140).start();
            }
            return false;
        });
    }

    private LinearLayout.LayoutParams spacedParams(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, topDp);
        return params;
    }

    private LinearLayout column(int left, int top, int right, int bottom) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(Ui.dp(this, left), Ui.dp(this, top), Ui.dp(this, right), Ui.dp(this, bottom));
        return layout;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }
}
