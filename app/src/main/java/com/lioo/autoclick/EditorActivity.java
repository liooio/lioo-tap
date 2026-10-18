package com.lioo.autoclick;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public final class EditorActivity extends Activity {
    private Library.Entry entry;
    private EditText nameInput;
    private LinearLayout steps;
    private JSONArray commands;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        entry = Library.find(this, getIntent().getStringExtra("id"));
        if (entry == null) {
            finish();
            return;
        }
        try {
            commands = new JSONArray(entry.body == null || entry.body.isEmpty() ? "[]" : entry.body);
        } catch (JSONException exception) {
            commands = new JSONArray();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.navigationBar(this, editorTitle(), "保存",
                this::finish, this::saveEntry));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(page);

        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText(entry.name);
        nameInput.setTextSize(17);
        nameInput.setTextColor(Ui.INK);
        nameInput.setBackground(Ui.stroke(this, Ui.SURFACE, 12, Ui.OUTLINE));
        nameInput.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        nameInput.setMinHeight(Ui.dp(this, 52));
        Ui.gap(page, this, label("名称"), 20);
        Ui.gap(page, this, nameInput, 6);

        TextView heading = Ui.text(this, "步骤", 16, Ui.INK, true);
        Ui.gap(page, this, heading, 22);
        steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        Ui.gap(page, this, steps, 8);

        if (!Library.RECORD.equals(entry.kind)) {
            LinearLayout adds = new LinearLayout(this);
            adds.setOrientation(LinearLayout.HORIZONTAL);
            adds.addView(chip("点击", "点击"), chipParams(false));
            adds.addView(chip("滑动", "滑动"), chipParams(true));
            adds.addView(chip("延迟", "延迟"), chipParams(true));
            Ui.gap(page, this, adds, 16);
            LinearLayout adds2 = new LinearLayout(this);
            adds2.setOrientation(LinearLayout.HORIZONTAL);
            adds2.addView(chip("循环", "循环"), chipParams(false));
            adds2.addView(chip("随机区域", "随机"), chipParams(true));
            if (Library.GROUP.equals(entry.kind)) {
                adds2.addView(chip("插入脚本", "脚本"), chipParams(true));
            }
            Ui.gap(page, this, adds2, 8);
        }
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        renderSteps();
    }

    private void renderSteps() {
        steps.removeAllViews();
        if (commands.length() == 0) {
            steps.addView(Ui.text(this, "还没有步骤。从下面的类型里添加。", 14, Ui.HINT, false));
            return;
        }
        for (int i = 0; i < commands.length(); i++) {
            JSONObject command = commands.optJSONObject(i);
            if (command == null) {
                continue;
            }
            int index = i;
            LinearLayout row = Ui.card(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = Ui.dp(this, 8);
            row.setLayoutParams(params);
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView badge = Ui.text(this, String.valueOf(i + 1), 13, Ui.ON_PRIMARY, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.round(this, Ui.PRIMARY, 8));
            head.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
            TextView kind = Ui.text(this,
                    Library.RECORD.equals(entry.kind)
                            ? RecordedScript.label(command) : command.optString("type"),
                    15, Ui.INK, true);
            LinearLayout.LayoutParams kindParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            kindParams.leftMargin = Ui.dp(this, 10);
            head.addView(kind, kindParams);
            if (Library.RECORD.equals(entry.kind)
                    && RecordedScript.DELAY.equals(command.optString("type"))) {
                TextView editDelay = Ui.text(this, "修改", 13, Ui.PRIMARY, true);
                editDelay.setPadding(Ui.dp(this, 10), Ui.dp(this, 8),
                        Ui.dp(this, 10), Ui.dp(this, 8));
                editDelay.setOnClickListener(view -> editRecordedDelay(command));
                head.addView(editDelay);
            }
            TextView remove = Ui.text(this, "删除", 13, Ui.ERROR, true);
            remove.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 8));
            remove.setOnClickListener(view -> Ui.showSlideDelete(
                    this, "删除步骤",
                    "确定删除第 " + (index + 1) + " 步？此操作会在保存后生效。",
                    () -> {
                        commands.remove(index);
                        renderSteps();
                    }));
            head.addView(remove);
            row.addView(head);
            TextView detail = Ui.text(this,
                    Library.RECORD.equals(entry.kind)
                            ? RecordedScript.detail(command) : command.optString("detail"),
                    13, Ui.MUTED, false);
            Ui.gap(row, this, detail, 8);
            steps.addView(row);
        }
    }

    private void editRecordedDelay(JSONObject command) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 20), Ui.dp(this, 18),
                Ui.dp(this, 20), Ui.dp(this, 20));
        panel.setBackground(Ui.round(this, Ui.SURFACE, 22));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "修改延时", 20, Ui.INK, true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView close = Ui.text(this, "×", 26, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭");
        close.setOnClickListener(view -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40)));
        panel.addView(header);

        TextView hint = Ui.text(this, "设置执行下一步前的等待时间", 13, Ui.MUTED, false);
        Ui.gap(panel, this, hint, 2);
        TextView label = Ui.text(this, "延时时间（毫秒）", 13, Ui.INK, true);
        Ui.gap(panel, this, label, 20);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(Math.max(0, command.optLong("duration"))));
        input.setSelectAllOnFocus(true);
        input.setTextSize(17);
        input.setTextColor(Ui.INK);
        input.setGravity(Gravity.CENTER);
        input.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        input.setBackground(Ui.stroke(this, Ui.FIELD, 12, Ui.OUTLINE));

        LinearLayout stepper = new LinearLayout(this);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.addView(delayStepButton("−", input, -100),
                new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 52), 1);
        inputParams.leftMargin = Ui.dp(this, 10);
        inputParams.rightMargin = Ui.dp(this, 10);
        stepper.addView(input, inputParams);
        stepper.addView(delayStepButton("+", input, 100),
                new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        Ui.gap(panel, this, stepper, 7);

        TextView stepHint = Ui.text(this, "每次调整 100 ms · 范围 0–600000 ms",
                12, Ui.HINT, false);
        stepHint.setGravity(Gravity.CENTER);
        Ui.gap(panel, this, stepHint, 8);

        LinearLayout actions = new LinearLayout(this);
        TextView cancel = Ui.button(this, "取消", Ui.SURFACE, Ui.MUTED);
        cancel.setBackground(Ui.stroke(this, Ui.SURFACE, 12, Ui.OUTLINE));
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(
                0, Ui.dp(this, 48), 1));
        TextView confirm = Ui.button(this, "保存延时", Ui.PRIMARY, Ui.ON_PRIMARY);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 48), 1);
        confirmParams.leftMargin = Ui.dp(this, 10);
        actions.addView(confirm, confirmParams);
        Ui.gap(panel, this, actions, 20);

        confirm.setOnClickListener(view -> {
            try {
                long duration = Long.parseLong(input.getText().toString().trim());
                if (duration < 0 || duration > 600000) {
                    throw new NumberFormatException();
                }
                command.put("duration", duration);
                command.remove("detail");
                renderSteps();
                dialog.dismiss();
            } catch (NumberFormatException | JSONException exception) {
                Toast.makeText(this, "请输入 0–600000 毫秒",
                        Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = Math.min(Ui.dp(this, 360),
                    getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32));
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.dimAmount = 0.32f;
            window.setAttributes(attributes);
            makeDialogDraggable(header, window);
        }
    }

    private TextView delayStepButton(String label, EditText input, int delta) {
        TextView button = Ui.text(this, label, 24, Ui.PRIMARY, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.round(this, Ui.PRIMARY_CONTAINER, 12));
        button.setContentDescription((delta < 0 ? "减少" : "增加")
                + Math.abs(delta) + "毫秒");
        button.setOnClickListener(view -> {
            long current;
            try {
                current = Long.parseLong(input.getText().toString().trim());
            } catch (NumberFormatException exception) {
                current = 0;
            }
            long next = Math.max(0, Math.min(600000, current + delta));
            input.setText(String.valueOf(next));
            input.setSelection(input.length());
        });
        return button;
    }

    private void makeDialogDraggable(android.view.View handle, Window window) {
        final float[] down = new float[2];
        final int[] start = new int[2];
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

    private TextView chip(String label, String type) {
        TextView chip = Ui.button(this, label, Ui.PRIMARY_CONTAINER, Ui.ON_PRIMARY_CONTAINER);
        chip.setMinHeight(Ui.dp(this, 40));
        chip.setOnClickListener(view -> askStep(type));
        return chip;
    }

    private void askStep(String type) {
        if ("脚本".equals(type)) {
            pickScript();
            return;
        }
        int inputType = "延迟".equals(type) || "循环".equals(type)
                ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT;
        Ui.showInput(this, "添加" + type, "填写这一步的执行参数",
                hintFor(type), "", inputType, "添加", value -> {
                    if (value.isEmpty()) {
                        Toast.makeText(this, "请填写内容", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    addCommand(type, detailFor(type, value));
                    return true;
                });
    }

    private void pickScript() {
        java.util.List<Library.Entry> scripts = Library.list(this, Library.SCRIPT);
        if (scripts.isEmpty()) {
            Toast.makeText(this, "先去普通脚本里创建一条", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[scripts.size()];
        for (int i = 0; i < scripts.size(); i++) {
            names[i] = scripts.get(i).name;
        }
        Ui.showChoices(this, "插入脚本", "选择要串联执行的普通脚本",
                Arrays.asList(names), which -> addCommand("脚本", names[which]));
    }

    private void addCommand(String type, String detail) {
        JSONObject command = new JSONObject();
        try {
            command.put("type", type);
            command.put("detail", detail);
        } catch (JSONException exception) {
            return;
        }
        commands.put(command);
        renderSteps();
    }

    private void saveEntry() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请填写名称", Toast.LENGTH_SHORT).show();
            return;
        }
        entry.name = name;
        entry.body = commands.toString();
        Library.save(this, entry);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String editorTitle() {
        if (Library.RECORD.equals(entry.kind)) {
            return "编辑录制";
        }
        if (Library.GROUP.equals(entry.kind)) {
            return "编辑流程";
        }
        return "编辑脚本";
    }

    private TextView label(String value) {
        return Ui.text(this, value, 12, Ui.MUTED, true);
    }

    private LinearLayout.LayoutParams chipParams(boolean gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        if (gap) {
            params.leftMargin = Ui.dp(this, 8);
        }
        return params;
    }

    private static String hintFor(String type) {
        if ("点击".equals(type)) {
            return "坐标，例如 540,1200";
        }
        if ("滑动".equals(type)) {
            return "例如 540,1600 → 540,400";
        }
        if ("延迟".equals(type)) {
            return "毫秒";
        }
        if ("循环".equals(type)) {
            return "重复次数";
        }
        return "例如 200,400-800,900";
    }

    private static String detailFor(String type, String value) {
        if ("点击".equals(type)) {
            return value + " · 按下 50ms";
        }
        if ("延迟".equals(type)) {
            return value + " ms";
        }
        if ("循环".equals(type)) {
            return "重复 " + value + " 次";
        }
        return value;
    }
}
