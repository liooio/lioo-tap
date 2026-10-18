package com.lioo.autoclick;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public final class KeyEditActivity extends Activity {
    private static final int[] COLORS = {
            0xFF006A60, 0xFFBA1A1A, 0xFF446278, 0xFF7A5900, 0xFF191C1B
    };

    private Library.Entry entry;
    private JSONObject config;
    private EditText nameInput;
    private EditText sizeInput;
    private EditText intervalInput;
    private EditText countInput;
    private TextView targetView;
    private LinearLayout swatches;
    private LinearLayout displayOptions;
    private TextView lockOption;
    private int color = 0xFF006A60;
    private String displayMode = ShortcutKey.FLOATING;
    private boolean floatingLocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        entry = Library.find(this, getIntent().getStringExtra("id"));
        if (entry == null) {
            finish();
            return;
        }
        try {
            config = new JSONObject(entry.body);
        } catch (JSONException exception) {
            config = new JSONObject();
        }
        color = parseColor(config.optString("color", "#006A60"));
        displayMode = config.optString("display", ShortcutKey.FLOATING);
        floatingLocked = config.optBoolean("locked", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.navigationBar(this, "编辑按键", "保存",
                this::finish, this::saveKey));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(page);

        LinearLayout appearance = formSection("外观", "设置悬浮按键的名称、颜色和字号");
        nameInput = field(appearance, "按键名称", entry.name, InputType.TYPE_CLASS_TEXT);
        Ui.gap(appearance, this, caption("按钮颜色"), 18);
        swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        Ui.gap(appearance, this, swatches, 8);
        paintSwatches();
        sizeInput = numberStepper(appearance, "文字大小", config.optInt("size", 18),
                1, 12, 32);
        page.addView(appearance);

        LinearLayout display = formSection("显示方式", "选择快捷键出现的位置");
        displayOptions = new LinearLayout(this);
        displayOptions.setOrientation(LinearLayout.HORIZONTAL);
        Ui.gap(display, this, displayOptions, 18);
        paintDisplayOptions();
        lockOption = Ui.button(this, "", Ui.FIELD, Ui.INK);
        lockOption.setBackground(Ui.stroke(this, Ui.FIELD, 12, Ui.OUTLINE));
        lockOption.setOnClickListener(view -> {
            if (ShortcutKey.FLOATING.equals(displayMode)) {
                floatingLocked = !floatingLocked;
                updateLockOption();
            }
        });
        Ui.gap(display, this, lockOption, 10);
        updateLockOption();
        Ui.gap(page, this, display, 14);

        LinearLayout behavior = formSection("执行设置", "选择脚本，并设置连续执行参数");
        targetView = Ui.button(this, "绑定：" + config.optString("target", "未绑定"), Ui.SURFACE, Ui.INK);
        targetView.setBackground(Ui.stroke(this, Ui.FIELD, 12, Ui.OUTLINE));
        targetView.setOnClickListener(view -> pickTarget());
        Ui.gap(behavior, this, caption("绑定脚本"), 18);
        Ui.gap(behavior, this, targetView, 8);
        intervalInput = numberStepper(behavior, "执行间隔（毫秒）",
                config.optInt("interval", 500), 100, 50, 60000);
        countInput = numberStepper(behavior, "执行次数（0 为无限）",
                config.optInt("count", 0), 1, 0, 99999);
        Ui.gap(page, this, behavior, 14);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void paintSwatches() {
        swatches.removeAllViews();
        for (int candidate : COLORS) {
            TextView swatch = new TextView(this);
            swatch.setGravity(Gravity.CENTER);
            swatch.setText(candidate == color ? "✓" : "");
            swatch.setTextColor(Color.WHITE);
            swatch.setTextSize(16);
            swatch.setBackground(Ui.round(this, candidate, 18));
            int chosen = candidate;
            swatch.setOnClickListener(view -> {
                color = chosen;
                paintSwatches();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
            params.rightMargin = Ui.dp(this, 10);
            swatches.addView(swatch, params);
        }
    }

    private void paintDisplayOptions() {
        displayOptions.removeAllViews();
        displayOptions.addView(displayOption("悬浮显示", ShortcutKey.FLOATING),
                optionParams(false));
        displayOptions.addView(displayOption("工具箱快捷栏", ShortcutKey.TOOLBOX),
                optionParams(true));
    }

    private TextView displayOption(String label, String value) {
        boolean selected = value.equals(displayMode);
        TextView option = Ui.text(this, label, 14,
                selected ? Ui.ON_PRIMARY : Ui.MUTED, true);
        option.setGravity(Gravity.CENTER);
        option.setBackground(Ui.round(this,
                selected ? Ui.PRIMARY : Ui.FIELD, 12));
        option.setOnClickListener(view -> {
            displayMode = value;
            paintDisplayOptions();
            updateLockOption();
        });
        return option;
    }

    private void updateLockOption() {
        if (lockOption == null) {
            return;
        }
        boolean floating = ShortcutKey.FLOATING.equals(displayMode);
        lockOption.setEnabled(floating);
        lockOption.setAlpha(floating ? 1f : 0.45f);
        lockOption.setText(!floating
                ? "工具箱快捷栏无需设置拖动"
                : (floatingLocked ? "拖动位置：已锁定" : "拖动位置：可调整"));
        lockOption.setTextColor(floatingLocked && floating ? Ui.PRIMARY : Ui.MUTED);
    }

    private LinearLayout.LayoutParams optionParams(boolean gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 48), 1);
        if (gap) {
            params.leftMargin = Ui.dp(this, 10);
        }
        return params;
    }

    private void pickTarget() {
        java.util.List<Library.Entry> scripts = Library.list(this, Library.SCRIPT);
        java.util.List<Library.Entry> records = Library.list(this, Library.RECORD);
        java.util.List<Library.Entry> groups = Library.list(this, Library.GROUP);
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add("未绑定");
        for (Library.Entry script : scripts) {
            names.add("脚本 · " + script.name);
        }
        for (Library.Entry record : records) {
            names.add("录制 · " + record.name);
        }
        for (Library.Entry group : groups) {
            names.add("流程 · " + group.name);
        }
        Ui.showChoices(this, "绑定脚本", "选择点击快捷键时直接执行的内容",
                names, which ->
                        targetView.setText(getString(
                                R.string.bound_target, names.get(which))));
    }

    private void saveKey() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请填写名称", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int size = Integer.parseInt(sizeInput.getText().toString().trim());
            int interval = Integer.parseInt(intervalInput.getText().toString().trim());
            int count = Integer.parseInt(countInput.getText().toString().trim());
            if (size < 12 || size > 32 || interval < 50 || count < 0) {
                throw new NumberFormatException();
            }
            String target = targetView.getText().toString().replaceFirst("^绑定：", "");
            config.put("color", String.format("#%06X", color & 0xFFFFFF));
            config.put("size", size);
            config.put("interval", interval);
            config.put("count", count);
            config.put("target", target);
            config.put("display", displayMode);
            config.put("locked", floatingLocked);
        } catch (NumberFormatException | JSONException exception) {
            Toast.makeText(this, "字号 12–32，间隔至少 50 毫秒", Toast.LENGTH_SHORT).show();
            return;
        }
        entry.name = name;
        entry.body = config.toString();
        Library.save(this, entry);
        OverlayService.refreshShortcutKeys();
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private EditText field(LinearLayout page, String label, String value, int inputType) {
        Ui.gap(page, this, caption(label), 16);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setInputType(inputType);
        input.setTextSize(16);
        input.setTextColor(Ui.INK);
        input.setBackground(Ui.stroke(this, Ui.FIELD, 12, Ui.OUTLINE));
        input.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        input.setMinHeight(Ui.dp(this, 52));
        Ui.gap(page, this, input, 8);
        return input;
    }

    private LinearLayout formSection(String title, String description) {
        LinearLayout section = Ui.card(this);
        section.setPadding(Ui.dp(this, 18), Ui.dp(this, 18),
                Ui.dp(this, 18), Ui.dp(this, 20));
        section.addView(Ui.text(this, title, 18, Ui.INK, true));
        TextView detail = Ui.text(this, description, 13, Ui.MUTED, false);
        Ui.gap(section, this, detail, 4);
        return section;
    }

    private EditText numberStepper(LinearLayout parent, String label, int value,
            int step, int min, int max) {
        Ui.gap(parent, this, caption(label), 18);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        input.setTextSize(16);
        input.setTextColor(Ui.INK);
        input.setGravity(Gravity.CENTER);
        input.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        input.setBackground(Ui.stroke(this, Ui.FIELD, 12, Ui.OUTLINE));

        row.addView(stepButton("−", input, -step, min, max),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 48), 1);
        inputParams.leftMargin = Ui.dp(this, 10);
        inputParams.rightMargin = Ui.dp(this, 10);
        row.addView(input, inputParams);
        row.addView(stepButton("+", input, step, min, max),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        Ui.gap(parent, this, row, 8);
        return input;
    }

    private TextView stepButton(String label, EditText input, int delta, int min, int max) {
        TextView button = Ui.text(this, label, 22, Ui.PRIMARY, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.round(this, Ui.PRIMARY_CONTAINER, 12));
        button.setContentDescription((delta < 0 ? "减少" : "增加") + Math.abs(delta));
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

    private TextView caption(String value) {
        return Ui.text(this, value, 12, Ui.MUTED, true);
    }

    private static int parseColor(String value) {
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException exception) {
            return 0xFF006A60;
        }
    }
}
