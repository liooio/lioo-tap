package com.lioo.autoclick;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

public final class LibraryActivity extends Activity {
    private String kind;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kind = getIntent().getStringExtra("kind");
        if (kind == null) {
            kind = Library.SCRIPT;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        boolean recordingLibrary = Library.RECORD.equals(kind);
        root.addView(Ui.navigationBar(this, titleFor(kind),
                recordingLibrary ? null : "新建",
                this::finish, recordingLibrary ? null : this::createNew));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(page);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        page.addView(list, params);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        list.removeAllViews();
        List<Library.Entry> entries = Library.list(this, kind);
        if (entries.isEmpty()) {
            FrameLayout empty = new FrameLayout(this);
            empty.setMinimumHeight(Ui.dp(this, 360));
            TextView message = Ui.text(this, emptyText(), 15, Ui.HINT, false);
            message.setGravity(Gravity.CENTER);
            empty.addView(message, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            list.addView(empty);
            return;
        }
        if (Library.RECORD.equals(kind) || Library.KEY.equals(kind)
                || Library.GROUP.equals(kind)) {
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setBackground(Ui.round(this, Ui.SURFACE, 18));
            group.setElevation(Ui.dp(this, 2));
            for (int i = 0; i < entries.size(); i++) {
                group.addView(Library.RECORD.equals(kind)
                        ? recordingRow(entries.get(i))
                        : (Library.GROUP.equals(kind)
                                ? groupRow(entries.get(i)) : keyRow(entries.get(i))));
                if (i < entries.size() - 1) {
                    View divider = new View(this);
                    divider.setBackgroundColor(Ui.DIVIDER);
                    LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            Math.max(1, Ui.dp(this, 1)));
                    dividerParams.leftMargin = Ui.dp(this, 68);
                    dividerParams.rightMargin = Ui.dp(this, 14);
                    group.addView(divider, dividerParams);
                }
            }
            list.addView(group);
            return;
        }
        for (Library.Entry entry : entries) {
            list.addView(card(entry));
        }
    }

    private LinearLayout card(Library.Entry entry) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 10);
        card.setLayoutParams(params);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = Ui.text(this, markFor(kind), 14, Ui.PRIMARY, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.round(this, Library.KEY.equals(kind) ? Ui.SECONDARY_CONTAINER : Ui.PRIMARY_CONTAINER, 10));
        head.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Ui.text(this, entry.name, 16, Ui.INK, true));
        TextView meta = Ui.text(this, metaFor(entry), 12, Ui.HINT, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Ui.dp(this, 2);
        names.addView(meta, metaParams);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameParams.leftMargin = Ui.dp(this, 12);
        head.addView(names, nameParams);
        card.addView(head);

        View divider = new android.view.View(this);
        divider.setBackgroundColor(Ui.OUTLINE);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1)));
        line.topMargin = Ui.dp(this, 12);
        card.addView(divider, line);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = Ui.dp(this, 12);
        if (!Library.KEY.equals(kind)) {
            actions.addView(action("运行", Ui.PRIMARY_CONTAINER, Ui.ON_PRIMARY_CONTAINER, view -> openRun(entry)), weight());
        }
        actions.addView(action("编辑", Ui.BG, Ui.INK, view -> openEdit(entry)), weightGap(!Library.KEY.equals(kind)));
        actions.addView(action("删除", 0xFFFFDAD6, Ui.ERROR, view -> confirmDelete(entry)), weightGap(true));
        card.addView(actions, actionParams);
        if (!Library.KEY.equals(kind)) {
            card.setOnClickListener(view -> openRun(entry));
        }
        return card;
    }

    private LinearLayout recordingRow(Library.Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                Ui.dp(this, 8), Ui.dp(this, 12));
        row.setMinimumHeight(Ui.dp(this, 76));
        row.setOnClickListener(view -> openRun(entry));

        TextView mark = Ui.text(this, "录", 14, Ui.BLUE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.round(this, Ui.SOFT_BLUE, 12));
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Ui.text(this, entry.name, 16, Ui.INK, true));
        TextView meta = Ui.text(this,
                entry.stepCount() + " 个操作  ·  " + entry.timeLabel(),
                12, Ui.MUTED, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Ui.dp(this, 3);
        names.addView(meta, metaParams);
        LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        namesParams.leftMargin = Ui.dp(this, 12);
        row.addView(names, namesParams);

        ImageView replay = new ImageView(this);
        replay.setImageResource(R.drawable.ic_play_arrow);
        replay.setScaleType(ImageView.ScaleType.CENTER);
        replay.setBackground(Ui.round(this, Ui.BLUE, 20));
        replay.setContentDescription("回放");
        replay.setOnClickListener(view -> openRun(entry));
        row.addView(replay, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more_vert);
        more.setScaleType(ImageView.ScaleType.CENTER);
        more.setContentDescription("更多操作");
        more.setOnClickListener(view -> showItemMenu(entry));
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40));
        moreParams.leftMargin = Ui.dp(this, 2);
        row.addView(more, moreParams);
        return row;
    }

    private LinearLayout groupRow(Library.Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                Ui.dp(this, 8), Ui.dp(this, 12));
        row.setMinimumHeight(Ui.dp(this, 76));
        row.setOnClickListener(view -> openRun(entry));

        TextView mark = Ui.text(this, "组", 14, Ui.PURPLE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.round(this, Ui.SOFT_PURPLE, 12));
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Ui.text(this, entry.name, 16, Ui.INK, true));
        TextView meta = Ui.text(this, GroupScript.items(entry.body).length()
                + " 个录制  ·  " + GroupScript.totalExpandedSteps(this, entry.body)
                + " 个执行步骤", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Ui.dp(this, 3);
        names.addView(meta, metaParams);
        LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        namesParams.leftMargin = Ui.dp(this, 12);
        row.addView(names, namesParams);

        ImageView play = new ImageView(this);
        play.setImageResource(R.drawable.ic_play_arrow);
        play.setScaleType(ImageView.ScaleType.CENTER);
        play.setBackground(Ui.round(this, Ui.PURPLE, 20));
        play.setContentDescription("执行");
        play.setOnClickListener(view -> openRun(entry));
        row.addView(play, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more_vert);
        more.setScaleType(ImageView.ScaleType.CENTER);
        more.setContentDescription("更多操作");
        more.setOnClickListener(view -> showItemMenu(entry));
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40));
        moreParams.leftMargin = Ui.dp(this, 2);
        row.addView(more, moreParams);
        return row;
    }

    private LinearLayout keyRow(Library.Entry entry) {
        JSONObject config;
        try {
            config = new JSONObject(entry.body);
        } catch (JSONException exception) {
            config = new JSONObject();
        }
        int keyColor;
        try {
            keyColor = android.graphics.Color.parseColor(
                    config.optString("color", "#006A60"));
        } catch (IllegalArgumentException exception) {
            keyColor = Ui.GREEN;
        }

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                Ui.dp(this, 8), Ui.dp(this, 12));
        row.setMinimumHeight(Ui.dp(this, 76));
        row.setOnClickListener(view -> openEdit(entry));

        TextView mark = Ui.text(this, "键", 14, Ui.ON_PRIMARY, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.round(this, keyColor, 12));
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Ui.text(this, entry.name, 16, Ui.INK, true));
        String target = config.optString("target", "未绑定");
        boolean floating = ShortcutKey.FLOATING.equals(ShortcutKey.displayMode(entry));
        String displayState = floating
                ? "悬浮显示 · " + (ShortcutKey.isLocked(entry) ? "已锁定" : "可拖动")
                : "工具箱快捷栏";
        TextView meta = Ui.text(this,
                ("未绑定".equals(target) ? "未绑定脚本" : target) + "  ·  " + displayState,
                12, Ui.MUTED, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Ui.dp(this, 3);
        names.addView(meta, metaParams);
        LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        namesParams.leftMargin = Ui.dp(this, 12);
        row.addView(names, namesParams);

        TextView edit = Ui.text(this, "编辑", 14, Ui.GREEN, true);
        edit.setGravity(Gravity.CENTER);
        edit.setOnClickListener(view -> openEdit(entry));
        row.addView(edit, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 40)));

        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more_vert);
        more.setScaleType(ImageView.ScaleType.CENTER);
        more.setContentDescription("更多操作");
        more.setOnClickListener(view -> showItemMenu(entry));
        row.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        return row;
    }

    private void showItemMenu(Library.Entry entry) {
        Ui.showChoices(this, entry.name, "选择要执行的操作",
                Arrays.asList("编辑", "删除"), index -> {
            if (index == 0) {
                openEdit(entry);
            } else {
                confirmDelete(entry);
            }
        });
    }

    private TextView action(String label, int fill, int color, android.view.View.OnClickListener listener) {
        TextView button = Ui.button(this, label, fill, color);
        button.setMinHeight(Ui.dp(this, 40));
        if (fill == Ui.BG) {
            button.setBackground(Ui.stroke(this, Ui.BG, 10, Ui.OUTLINE));
        } else {
            button.setBackground(Ui.round(this, fill, 10));
        }
        button.setOnClickListener(listener);
        return button;
    }

    private void createNew() {
        if (Library.RECORD.equals(kind)) {
            Toast.makeText(this, "请从悬浮工具箱点击“录制脚本”开始录制",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (Library.KEY.equals(kind) && Library.list(this, Library.KEY).size() >= 5) {
            Toast.makeText(this, "最多 5 个悬浮按键", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Library.GROUP.equals(kind)) {
            startActivity(new Intent(this, GroupEditorActivity.class));
            return;
        }
        Ui.showInput(this, createTitle(), "创建后可以继续编辑详细设置",
                nameHint(), "", InputType.TYPE_CLASS_TEXT, "创建", name -> {
                    if (name.isEmpty()) {
                        Toast.makeText(this, "请填写名称", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    Library.Entry entry = Library.create(kind, name);
                    Library.save(this, entry);
                    openEdit(entry);
                    return true;
                });
    }

    private void confirmDelete(Library.Entry entry) {
        Ui.showConfirm(this, "删除项目",
                "确定删除「" + entry.name + "」？此操作无法撤销。",
                "删除", true, () -> {
                    Library.delete(this, entry.id);
                    render();
                });
    }

    private void openEdit(Library.Entry entry) {
        Class<?> target = Library.KEY.equals(kind)
                ? KeyEditActivity.class
                : (Library.GROUP.equals(kind)
                        ? GroupEditorActivity.class : EditorActivity.class);
        startActivity(new Intent(this, target).putExtra("id", entry.id));
    }

    private void openRun(Library.Entry entry) {
        startActivity(new Intent(this, RunActivity.class).putExtra("id", entry.id));
    }

    private static String titleFor(String kind) {
        if (Library.RECORD.equals(kind)) {
            return "录制脚本";
        }
        if (Library.GROUP.equals(kind)) {
            return "自定义脚本";
        }
        if (Library.KEY.equals(kind)) {
            return "按键设置";
        }
        return "普通脚本";
    }

    private String emptyText() {
        if (Library.RECORD.equals(kind)) {
            return "还没有录制。请从悬浮工具箱开始录制。";
        }
        if (Library.GROUP.equals(kind)) {
            return "还没有自定义流程。点右上角 ＋ 新建。";
        }
        if (Library.KEY.equals(kind)) {
            return "还没有悬浮按键。最多可以加 5 个。";
        }
        return "还没有脚本。点右上角 ＋ 创建。";
    }

    private String nameHint() {
        if (Library.KEY.equals(kind)) {
            return "按键名称";
        }
        return "名称";
    }

    private String createTitle() {
        if (Library.RECORD.equals(kind)) {
            return "新建录制";
        }
        if (Library.GROUP.equals(kind)) {
            return "新建自定义脚本";
        }
        if (Library.KEY.equals(kind)) {
            return "新建按键";
        }
        return "新建脚本";
    }

    private static String markFor(String kind) {
        if (Library.RECORD.equals(kind)) {
            return "录";
        }
        if (Library.GROUP.equals(kind)) {
            return "组";
        }
        if (Library.KEY.equals(kind)) {
            return "键";
        }
        return "脚";
    }

    private static String metaFor(Library.Entry entry) {
        if (Library.KEY.equals(entry.kind)) {
            return "悬浮快捷键 · " + entry.timeLabel();
        }
        return entry.stepCount() + " 条步骤 · " + entry.timeLabel();
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams weightGap(boolean gap) {
        LinearLayout.LayoutParams params = weight();
        if (gap) {
            params.leftMargin = Ui.dp(this, 8);
        }
        return params;
    }
}
