package com.lioo.autoclick;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class RunActivity extends Activity {
    private Library.Entry entry;
    private EditText intervalInput;
    private EditText countInput;
    private EditText playbackCountdownInput;
    private EditText playbackRepeatInput;
    private boolean waitingForOverlayPermission;
    private boolean waitingForPlayback;

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForOverlayPermission && OverlayAccess.hasPermission(this)) {
            waitingForOverlayPermission = false;
            if (waitingForPlayback) {
                waitingForPlayback = false;
                startRecordedPlayback();
            } else {
                OverlayService.show(this);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        entry = Library.find(this, getIntent().getStringExtra("id"));
        if (entry == null) {
            finish();
            return;
        }
        ClickSettings saved = ClickSettings.load(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.navigationBar(this, "运行", null,
                this::finish, null));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(page);

        LinearLayout hero = Ui.card(this);
        hero.addView(Ui.text(this, entry.name, 22, Ui.INK, true));
        boolean recorded = Library.RECORD.equals(entry.kind);
        boolean group = Library.GROUP.equals(entry.kind);
        TextView meta = Ui.text(this,
                entry.stepCount() + (group ? " 个执行项 · " : " 条步骤 · ")
                        + entry.timeLabel(), 13, Ui.HINT, false);
        Ui.gap(hero, this, meta, 4);
        Ui.gap(page, this, hero, 16);

        if (!recorded && !group) {
            LinearLayout fields = new LinearLayout(this);
            fields.setOrientation(LinearLayout.HORIZONTAL);
            intervalInput = number(String.valueOf(saved.intervalMs));
            countInput = number(String.valueOf(saved.count));
            fields.addView(labeled("间隔 (ms)", intervalInput), weight(false));
            fields.addView(labeled("次数", countInput), weight(true));
            Ui.gap(page, this, fields, 16);
        } else {
            LinearLayout playback = Ui.card(this);
            playback.addView(Ui.text(this, "回放设置", 16, Ui.INK, true));
            TextView hint = Ui.text(this,
                    "倒计时 0 秒立即开始，次数 0 表示无限循环。设置会一直记住。",
                    13, Ui.MUTED, false);
            Ui.gap(playback, this, hint, 6);
            LinearLayout fields = new LinearLayout(this);
            fields.setOrientation(LinearLayout.HORIZONTAL);
            playbackCountdownInput = number(String.valueOf(
                    ClickSettings.playbackCountdownSeconds(this)));
            playbackRepeatInput = number(String.valueOf(
                    ClickSettings.playbackRepeatCount(this)));
            fields.addView(labeled("倒计时 (秒)", playbackCountdownInput), weight(false));
            fields.addView(labeled("循环次数", playbackRepeatInput), weight(true));
            Ui.gap(playback, this, fields, 14);
            Ui.gap(page, this, playback, 16);
        }

        page.addView(section("步骤"));
        JSONArray commands;
        try {
            commands = new JSONArray(entry.body == null ? "[]" : entry.body);
        } catch (JSONException exception) {
            commands = new JSONArray();
        }
        if (commands.length() == 0) {
            page.addView(Ui.text(this, "这条还没有步骤。", 14, Ui.HINT, false));
        }
        for (int i = 0; i < commands.length(); i++) {
            JSONObject command = commands.optJSONObject(i);
            if (command == null) {
                continue;
            }
            LinearLayout row = Ui.card(this);
            Library.Entry groupRecording = group ? GroupScript.resolve(this, command) : null;
            row.addView(Ui.text(this, (i + 1) + "  " + (group
                    ? (groupRecording == null ? "录制已删除" : groupRecording.name)
                    : (recorded ? RecordedScript.label(command)
                            : command.optString("type"))), 15, Ui.INK, true));
            TextView detail = Ui.text(this, group
                    ? (groupRecording == null ? "无法执行"
                            : groupRecording.stepCount() + " 个步骤 · 循环 "
                                    + GroupScript.repeat(command) + " 次 · 间隔 "
                                    + GroupScript.intervalMs(command) + " ms")
                    : (recorded ? RecordedScript.detail(command)
                            : command.optString("detail")),
                    13, Ui.MUTED, false);
            Ui.gap(row, this, detail, 4);
            Ui.gap(page, this, row, 8);
        }

        TextView start = Ui.button(this,
                recorded || group ? "显示执行控制栏" : "打开悬浮窗",
                Ui.PRIMARY, Ui.ON_PRIMARY);
        start.setOnClickListener(view -> {
            if (recorded || group) {
                if (!savePlaybackSettings()) {
                    return;
                }
                prepareRecordedPlayback();
            } else {
                startOverlay();
            }
        });
        Ui.gap(page, this, start, 20);
        TextView note = Ui.text(this, recorded || group
                ? "点击后先选择目标应用，再从顶部操作栏点击“开始”执行回放。倒计时和次数会记住。"
                : "拖到目标上后，在悬浮窗里点开始。间隔和次数用上面这两项。",
                13, Ui.HINT, false);
        Ui.gap(page, this, note, 10);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void startOverlay() {
        try {
            int interval = Integer.parseInt(intervalInput.getText().toString().trim());
            int count = Integer.parseInt(countInput.getText().toString().trim());
            if (interval < 50 || interval > 60000 || count < 0) {
                throw new NumberFormatException();
            }
            new ClickSettings(interval, ClickSettings.load(this).durationMs, count).save(this);
        } catch (NumberFormatException exception) {
            Toast.makeText(this, "间隔 50–60000 毫秒，次数不能为负", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!OverlayAccess.hasPermission(this)) {
            waitingForOverlayPermission = true;
            OverlayAccess.requestPermission(this);
            return;
        }
        OverlayService.show(this);
        Toast.makeText(this, "悬浮窗已打开", Toast.LENGTH_SHORT).show();
    }

    private boolean savePlaybackSettings() {
        try {
            int countdown = Integer.parseInt(
                    playbackCountdownInput.getText().toString().trim());
            int repeat = Integer.parseInt(
                    playbackRepeatInput.getText().toString().trim());
            if (countdown < 0 || countdown > 10 || repeat < 0 || repeat > 9999) {
                throw new NumberFormatException();
            }
            ClickSettings.savePlayback(this, countdown, repeat);
            return true;
        } catch (NumberFormatException exception) {
            Toast.makeText(this, "倒计时 0–10 秒，次数 0–9999，0 表示无限循环",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void prepareRecordedPlayback() {
        if (!ClickAccessibilityService.isEnabled(this)) {
            Toast.makeText(this, "请先开启连点器无障碍服务", Toast.LENGTH_LONG).show();
            startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if (!OverlayAccess.hasPermission(this)) {
            waitingForOverlayPermission = true;
            waitingForPlayback = true;
            OverlayAccess.requestPermission(this);
            return;
        }
        startRecordedPlayback();
    }

    private void startRecordedPlayback() {
        String body = Library.GROUP.equals(entry.kind)
                ? ExecutionPlan.from(this, entry).toString() : entry.body;
        OverlayService.playRecording(this, body, entry.name);
        moveTaskToBack(true);
    }

    private LinearLayout labeled(String label, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.text(this, label, 12, Ui.MUTED, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 6);
        box.addView(input, params);
        return box;
    }

    private EditText number(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(16);
        input.setTextColor(Ui.INK);
        input.setBackground(Ui.stroke(this, Ui.SURFACE, 12, Ui.OUTLINE));
        input.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        input.setMinHeight(Ui.dp(this, 52));
        return input;
    }

    private TextView section(String value) {
        TextView title = Ui.text(this, value, 16, Ui.INK, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 22);
        params.bottomMargin = Ui.dp(this, 8);
        title.setLayoutParams(params);
        return title;
    }

    private LinearLayout.LayoutParams weight(boolean gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        if (gap) {
            params.leftMargin = Ui.dp(this, 12);
        }
        return params;
    }
}
