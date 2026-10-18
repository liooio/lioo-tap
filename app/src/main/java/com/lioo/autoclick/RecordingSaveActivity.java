package com.lioo.autoclick;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

public final class RecordingSaveActivity extends Activity {
    static final String EXTRA_STEPS = "steps";

    private JSONArray steps;
    private EditText nameInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            steps = new JSONArray(getIntent().getStringExtra(EXTRA_STEPS));
        } catch (JSONException | NullPointerException exception) {
            steps = new JSONArray();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.navigationBar(this, "保存录制脚本", null,
                this::finish, null));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 20), Ui.dp(this, 20),
                Ui.dp(this, 20), Ui.dp(this, 24));
        page.setBackgroundColor(Ui.BG);

        TextView title = Ui.text(this, "本次录制", 20, Ui.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        page.addView(title, titleParams);
        String summary = "点击 " + RecordedScript.count(steps, RecordedScript.TAP)
                + " 次 · 拖动 " + RecordedScript.count(steps, RecordedScript.DRAG)
                + " 次 · 延时 " + RecordedScript.count(steps, RecordedScript.DELAY) + " 段";
        TextView detail = Ui.text(this, summary, 13, Ui.MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = Ui.dp(this, 8);
        page.addView(detail, detailParams);

        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("录制名称");
        nameInput.setText("录制 " + new java.text.SimpleDateFormat(
                "MM-dd HH:mm", java.util.Locale.CHINA).format(new java.util.Date()));
        nameInput.setSelectAllOnFocus(true);
        nameInput.setTextSize(16);
        nameInput.setTextColor(Ui.INK);
        nameInput.setGravity(Gravity.CENTER_VERTICAL);
        nameInput.setBackground(Ui.stroke(this, Ui.SURFACE, 12, Ui.OUTLINE));
        nameInput.setPadding(Ui.dp(this, 14), Ui.dp(this, 10),
                Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 60));
        nameParams.topMargin = Ui.dp(this, 24);
        page.addView(nameInput, nameParams);

        TextView save = Ui.button(this, "保存录制", Ui.PRIMARY, Ui.ON_PRIMARY);
        save.setGravity(Gravity.CENTER);
        save.setOnClickListener(view -> save());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        saveParams.topMargin = Ui.dp(this, 18);
        page.addView(save, saveParams);

        TextView cancel = Ui.button(this, "放弃录制", Ui.SURFACE, Ui.ERROR);
        cancel.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        cancelParams.topMargin = Ui.dp(this, 10);
        page.addView(cancel, cancelParams);
        root.addView(page, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请填写录制名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (steps.length() == 0) {
            Toast.makeText(this, "没有可保存的操作", Toast.LENGTH_SHORT).show();
            return;
        }
        Library.Entry entry = Library.create(Library.RECORD, name);
        entry.body = steps.toString();
        Library.save(this, entry);
        Toast.makeText(this, "录制脚本已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
