package com.lioo.autoclick;

import android.app.Activity;
import android.content.ClipData;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class GroupEditorActivity extends Activity {
    private Library.Entry entry;
    private JSONArray sequence;
    private EditText nameInput;
    private LinearLayout sequenceList;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String entryId = getIntent().getStringExtra("id");
        boolean creating = entryId == null || entryId.isEmpty();
        entry = creating ? Library.create(Library.GROUP, "")
                : Library.find(this, entryId);
        if (entry == null || !Library.GROUP.equals(entry.kind)) {
            finish();
            return;
        }
        try {
            sequence = new JSONArray(entry.body == null ? "[]" : entry.body);
        } catch (JSONException exception) {
            sequence = new JSONArray();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.navigationBar(this,
                creating ? "新建自定义脚本" : "编辑自定义脚本", "保存",
                this::finish, this::save));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16),
                Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(page);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(Ui.dp(this, 18), Ui.dp(this, 18),
                Ui.dp(this, 18), Ui.dp(this, 18));
        hero.setBackground(Ui.round(this, Ui.SOFT_PURPLE, 18));
        hero.addView(Ui.text(this, "组合录制脚本", 20, Ui.INK, true));
        TextView description = Ui.text(this,
                "按顺序执行多个录制，可为每一项单独设置循环次数和间隔。",
                13, Ui.MUTED, false);
        Ui.gap(hero, this, description, 6);
        summary = Ui.text(this, "", 13, Ui.PURPLE, true);
        Ui.gap(hero, this, summary, 14);
        page.addView(hero);

        LinearLayout info = Ui.card(this);
        info.addView(Ui.text(this, "脚本名称", 13, Ui.MUTED, true));
        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText(entry.name);
        nameInput.setHint("例如：每日签到流程");
        nameInput.setHintTextColor(Ui.HINT);
        nameInput.setTextSize(16);
        nameInput.setTextColor(Ui.INK);
        nameInput.setGravity(Gravity.CENTER_VERTICAL);
        nameInput.setMinHeight(Ui.dp(this, 60));
        nameInput.setPadding(Ui.dp(this, 14), Ui.dp(this, 10),
                Ui.dp(this, 14), Ui.dp(this, 10));
        nameInput.setBackground(Ui.stroke(this, Ui.FIELD, 12, Ui.OUTLINE));
        Ui.gap(info, this, nameInput, 8);
        Ui.gap(page, this, info, 14);

        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        sectionHeader.addView(Ui.text(this, "执行序列", 18, Ui.INK, true),
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView add = Ui.text(this, "＋ 添加录制", 14, Ui.PURPLE, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(Ui.dp(this, 12), Ui.dp(this, 8),
                Ui.dp(this, 12), Ui.dp(this, 8));
        add.setBackground(Ui.round(this, Ui.SOFT_PURPLE, 12));
        add.setOnClickListener(view -> pickRecording());
        sectionHeader.addView(add);
        Ui.gap(page, this, sectionHeader, 20);

        sequenceList = new LinearLayout(this);
        sequenceList.setOrientation(LinearLayout.VERTICAL);
        sequenceList.setOnDragListener(this::onSequenceDrag);
        Ui.gap(page, this, sequenceList, 10);

        TextView addBottom = Ui.button(this, "添加录制脚本", Ui.PURPLE, Ui.ON_PRIMARY);
        addBottom.setOnClickListener(view -> pickRecording());
        Ui.gap(page, this, addBottom, 12);

        TextView hint = Ui.text(this,
                "长按右侧拖动柄调整顺序。运行时会依次展开每个录制的全部步骤。",
                12, Ui.HINT, false);
        hint.setGravity(Gravity.CENTER);
        Ui.gap(page, this, hint, 10);

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        render();
    }

    private void render() {
        sequenceList.removeAllViews();
        summary.setText(sequence.length() + " 个录制 · "
                + GroupScript.expandedStepCount(this, sequence) + " 个执行步骤");
        if (sequence.length() == 0) {
            LinearLayout empty = Ui.card(this);
            empty.setGravity(Gravity.CENTER);
            empty.setMinimumHeight(Ui.dp(this, 150));
            TextView message = Ui.text(this,
                    "还没有执行项\n点击“添加录制脚本”开始组合",
                    14, Ui.HINT, false);
            message.setGravity(Gravity.CENTER);
            empty.addView(message);
            sequenceList.addView(empty);
            return;
        }
        for (int i = 0; i < sequence.length(); i++) {
            JSONObject item = sequence.optJSONObject(i);
            if (item != null) {
                sequenceList.addView(sequenceRow(item, i));
            }
        }
    }

    private View sequenceRow(JSONObject item, int index) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = Ui.dp(this, 10);
        card.setLayoutParams(cardParams);

        Library.Entry recording = GroupScript.resolve(this, item);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView number = Ui.text(this, String.valueOf(index + 1),
                13, Ui.ON_PRIMARY, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(Ui.round(this, Ui.PURPLE, 9));
        header.addView(number, new LinearLayout.LayoutParams(
                Ui.dp(this, 30), Ui.dp(this, 30)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(Ui.text(this,
                recording == null ? "录制已删除" : recording.name,
                16, recording == null ? Ui.ERROR : Ui.INK, true));
        TextView meta = Ui.text(this,
                recording == null ? "无法执行，请删除此项"
                        : recording.stepCount() + " 个步骤 · 循环 "
                                + GroupScript.repeat(item) + " 次 · 间隔 "
                                + GroupScript.intervalMs(item) + " ms",
                12, Ui.MUTED, false);
        Ui.gap(titleBox, this, meta, 3);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = Ui.dp(this, 12);
        header.addView(titleBox, titleParams);

        TextView drag = Ui.text(this, "≡", 25, Ui.HINT, true);
        drag.setGravity(Gravity.CENTER);
        drag.setContentDescription("长按拖动调整顺序");
        drag.setOnLongClickListener(view -> {
            ClipData data = ClipData.newPlainText("sequence", String.valueOf(index));
            view.startDragAndDrop(data, new View.DragShadowBuilder(card), index, 0);
            return true;
        });
        header.addView(drag, new LinearLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44)));
        card.addView(header);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(Ui.text(this, "循环次数", 13, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        controls.addView(repeatButton("−", item, -1),
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        EditText repeat = repeatInput(item);
        controls.addView(repeat, new LinearLayout.LayoutParams(
                Ui.dp(this, 70), Ui.dp(this, 40)));
        controls.addView(repeatButton("+", item, 1),
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        TextView delete = Ui.text(this, "删除", 13, Ui.ERROR, true);
        delete.setGravity(Gravity.CENTER);
        delete.setOnClickListener(view -> Ui.showSlideDelete(this,
                "删除执行项", "确定从序列中移除「"
                        + (recording == null ? "失效录制" : recording.name) + "」？",
                () -> {
                    sequence.remove(index);
                    render();
                }));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 58), Ui.dp(this, 40));
        deleteParams.leftMargin = Ui.dp(this, 8);
        controls.addView(delete, deleteParams);
        Ui.gap(card, this, controls, 16);

        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setGravity(Gravity.CENTER_VERTICAL);
        intervalRow.addView(Ui.text(this, "循环间隔（毫秒）", 13, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        EditText interval = intervalInput(item);
        intervalRow.addView(interval, new LinearLayout.LayoutParams(
                Ui.dp(this, 132), Ui.dp(this, 46)));
        Ui.gap(card, this, intervalRow, 10);
        return card;
    }

    private EditText intervalInput(JSONObject item) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(String.valueOf(GroupScript.intervalMs(item)));
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(15);
        input.setTextColor(Ui.INK);
        input.setGravity(Gravity.CENTER);
        input.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        input.setBackground(Ui.stroke(this, Ui.FIELD, 10, Ui.OUTLINE));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (value.length() == 0) {
                    return;
                }
                try {
                    long parsed = Long.parseLong(value.toString());
                    item.put("interval", Math.max(0, Math.min(600000, parsed)));
                } catch (NumberFormatException | JSONException ignored) {
                    // 输入框失去焦点时会将无效值修正到允许范围。
                }
            }
        });
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                input.setText(String.valueOf(GroupScript.intervalMs(item)));
            }
        });
        return input;
    }

    private EditText repeatInput(JSONObject item) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(String.valueOf(GroupScript.repeat(item)));
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(15);
        input.setTextColor(Ui.INK);
        input.setGravity(Gravity.CENTER);
        input.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (value.length() == 0) {
                    return;
                }
                try {
                    long parsed = Long.parseLong(value.toString());
                    item.put("repeat", Math.max(1, Math.min(9999, parsed)));
                } catch (NumberFormatException | JSONException ignored) {
                    // 输入框失去焦点时会将无效值修正到允许范围。
                }
            }
        });
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                input.setText(String.valueOf(GroupScript.repeat(item)));
            }
        });
        return input;
    }

    private TextView repeatButton(String label, JSONObject item, int delta) {
        TextView button = Ui.text(this, label, 21, Ui.PURPLE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.round(this, Ui.SOFT_PURPLE, 10));
        button.setOnClickListener(view -> {
            try {
                item.put("repeat", Math.max(1,
                        Math.min(9999, GroupScript.repeat(item) + delta)));
            } catch (JSONException ignored) {
                return;
            }
            render();
        });
        return button;
    }

    private void pickRecording() {
        List<Library.Entry> recordings = Library.list(this, Library.RECORD);
        if (recordings.isEmpty()) {
            Ui.showMessage(this, "暂无录制脚本",
                    "请先从悬浮工具箱完成一次录制并保存，然后再回来组合。",
                    "知道了", null);
            return;
        }
        List<String> choices = new ArrayList<>();
        for (Library.Entry recording : recordings) {
            choices.add(recording.name + "  ·  " + recording.stepCount() + " 个步骤");
        }
        Ui.showChoices(this, "添加录制脚本", "选择一个录制加入执行序列",
                choices, index -> {
                    sequence.put(GroupScript.item(recordings.get(index)));
                    render();
                });
    }

    private boolean onSequenceDrag(View view, DragEvent event) {
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            return event.getLocalState() instanceof Integer;
        }
        if (event.getAction() == DragEvent.ACTION_DROP) {
            int from = (Integer) event.getLocalState();
            int target = sequence.length();
            for (int i = 0; i < sequenceList.getChildCount(); i++) {
                View child = sequenceList.getChildAt(i);
                if (event.getY() < child.getTop() + child.getHeight() / 2f) {
                    target = i;
                    break;
                }
            }
            move(from, target);
            return true;
        }
        return true;
    }

    private void move(int from, int target) {
        if (from < 0 || from >= sequence.length()) {
            return;
        }
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < sequence.length(); i++) {
            JSONObject item = sequence.optJSONObject(i);
            if (item != null) {
                items.add(item);
            }
        }
        JSONObject moved = items.remove(from);
        if (target > from) {
            target--;
        }
        items.add(Math.max(0, Math.min(items.size(), target)), moved);
        sequence = new JSONArray();
        for (JSONObject item : items) {
            sequence.put(item);
        }
        render();
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请填写脚本名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sequence.length() == 0) {
            Toast.makeText(this, "请至少添加一个录制脚本", Toast.LENGTH_SHORT).show();
            return;
        }
        entry.name = name;
        entry.body = sequence.toString();
        Library.save(this, entry);
        Toast.makeText(this, "自定义脚本已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
