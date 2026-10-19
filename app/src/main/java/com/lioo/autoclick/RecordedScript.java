package com.lioo.autoclick;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** 处理录制脚本中点击、拖动和延时等结构化步骤。 */
final class RecordedScript {
    static final String TAP = "tap";
    static final String DRAG = "drag";
    static final String DELAY = "delay";

    private RecordedScript() {
    }

    static String label(JSONObject step) {
        String type = step.optString("type");
        if (TAP.equals(type)) {
            return "点击";
        }
        if (DRAG.equals(type)) {
            return "拖动";
        }
        if (DELAY.equals(type)) {
            return "延时";
        }
        return type.isEmpty() ? "未知步骤" : type;
    }

    static String detail(JSONObject step) {
        String legacy = step.optString("detail");
        if (!legacy.isEmpty()) {
            return legacy;
        }
        String type = step.optString("type");
        if (TAP.equals(type)) {
            return String.format(Locale.CHINA, "位置 %.1f%%, %.1f%% · %d ms",
                    step.optDouble("x") * 100,
                    step.optDouble("y") * 100,
                    Math.max(1, step.optLong("duration", 50)));
        }
        if (DRAG.equals(type)) {
            JSONArray points = step.optJSONArray("points");
            return (points == null ? 0 : points.length()) + " 个轨迹点 · "
                    + Math.max(1, step.optLong("duration", 300)) + " ms";
        }
        if (DELAY.equals(type)) {
            return Math.max(0, step.optLong("duration")) + " ms";
        }
        return "无法识别的步骤";
    }

    static int count(JSONArray steps, String type) {
        int result = 0;
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step != null && type.equals(step.optString("type"))) {
                result++;
            }
        }
        return result;
    }
}
