package com.lioo.autoclick;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将已保存的脚本命令转换为回放引擎使用的标准步骤。 */
final class ExecutionPlan {
    static final String REPEAT_BLOCK = "repeat_block";
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private ExecutionPlan() {
    }

    static JSONArray from(Context context, Library.Entry entry) {
        return from(context, entry, 0);
    }

    private static JSONArray from(Context context, Library.Entry entry, int depth) {
        if (entry == null || depth > 4) {
            return new JSONArray();
        }
        if (Library.RECORD.equals(entry.kind)) {
            try {
                return new JSONArray(entry.body == null ? "[]" : entry.body);
            } catch (JSONException exception) {
                return new JSONArray();
            }
        }
        JSONArray source;
        try {
            source = new JSONArray(entry.body == null ? "[]" : entry.body);
        } catch (JSONException exception) {
            return new JSONArray();
        }
        JSONArray result = new JSONArray();
        if (Library.GROUP.equals(entry.kind)) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item == null || !GroupScript.isRecording(item)) {
                    continue;
                }
                Library.Entry recording = GroupScript.resolve(context, item);
                JSONArray recordingPlan = from(context, recording, depth + 1);
                int repeats = GroupScript.repeat(item);
                int intervalMs = GroupScript.intervalMs(item);
                if (recordingPlan.length() == 0) {
                    continue;
                }
                try {
                    result.put(new JSONObject()
                            .put("type", REPEAT_BLOCK)
                            .put("repeat", repeats)
                            .put("interval", intervalMs)
                            .put("steps", recordingPlan));
                } catch (JSONException ignored) {
                    // 基础配置值和内存中的 JSONArray 均为有效 JSON 数据。
                }
            }
            return result;
        }
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        for (int i = 0; i < source.length(); i++) {
            JSONObject command = source.optJSONObject(i);
            if (command == null) {
                continue;
            }
            String type = command.optString("type");
            String detail = command.optString("detail");
            List<Double> numbers = numbers(detail);
            try {
                if ("点击".equals(type) && numbers.size() >= 2) {
                    JSONObject tap = new JSONObject();
                    tap.put("type", RecordedScript.TAP);
                    tap.put("x", ratio(numbers.get(0), width));
                    tap.put("y", ratio(numbers.get(1), height));
                    tap.put("duration", numbers.size() >= 3
                            ? Math.round(numbers.get(numbers.size() - 1)) : 50);
                    result.put(tap);
                } else if ("滑动".equals(type) && numbers.size() >= 4) {
                    JSONObject drag = new JSONObject();
                    drag.put("type", RecordedScript.DRAG);
                    drag.put("duration", 300);
                    JSONArray points = new JSONArray();
                    points.put(new JSONArray()
                            .put(ratio(numbers.get(0), width))
                            .put(ratio(numbers.get(1), height)));
                    points.put(new JSONArray()
                            .put(ratio(numbers.get(2), width))
                            .put(ratio(numbers.get(3), height)));
                    drag.put("points", points);
                    result.put(drag);
                } else if ("延迟".equals(type) && !numbers.isEmpty()) {
                    result.put(new JSONObject()
                            .put("type", RecordedScript.DELAY)
                            .put("duration", Math.round(numbers.get(0))));
                } else if ("随机".equals(type) && numbers.size() >= 4) {
                    result.put(new JSONObject()
                            .put("type", RecordedScript.TAP)
                            .put("x", ratio((numbers.get(0) + numbers.get(2)) / 2, width))
                            .put("y", ratio((numbers.get(1) + numbers.get(3)) / 2, height))
                            .put("duration", 50));
                } else if ("循环".equals(type) && !numbers.isEmpty()) {
                    int repeats = Math.max(1, Math.min(20, numbers.get(0).intValue()));
                    JSONArray snapshot = new JSONArray(result.toString());
                    for (int repeat = 1; repeat < repeats; repeat++) {
                        append(result, snapshot);
                    }
                } else if ("脚本".equals(type)) {
                    Library.Entry nested = findByName(context, detail);
                    append(result, from(context, nested, depth + 1));
                }
            } catch (JSONException ignored) {
                // 保留此前已经成功生成的可执行步骤。
            }
        }
        return result;
    }

    private static Library.Entry findByName(Context context, String name) {
        for (String kind : new String[] {Library.SCRIPT, Library.RECORD, Library.GROUP}) {
            for (Library.Entry entry : Library.list(context, kind)) {
                if (entry.name.equals(name)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static void append(JSONArray target, JSONArray source) {
        for (int i = 0; i < source.length(); i++) {
            JSONObject step = source.optJSONObject(i);
            if (step != null) {
                target.put(step);
            }
        }
    }

    private static List<Double> numbers(String value) {
        List<Double> result = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        while (matcher.find()) {
            result.add(Double.parseDouble(matcher.group()));
        }
        return result;
    }

    private static double ratio(double coordinate, int size) {
        return Math.max(0, Math.min(1, coordinate / Math.max(1, size)));
    }
}
