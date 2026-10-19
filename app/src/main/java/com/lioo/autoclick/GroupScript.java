package com.lioo.autoclick;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class GroupScript {
    static final String TYPE_RECORD = "record";

    private GroupScript() {
    }

    static JSONObject item(Library.Entry recording) {
        JSONObject item = new JSONObject();
        try {
            item.put("type", TYPE_RECORD);
            item.put("recordId", recording.id);
            item.put("name", recording.name);
            item.put("repeat", 1);
            item.put("interval", 0);
        } catch (JSONException ignored) {
            // JSONObject backed by memory should not reject these values.
        }
        return item;
    }

    static boolean isRecording(JSONObject item) {
        return TYPE_RECORD.equals(item.optString("type"))
                || "脚本".equals(item.optString("type"));
    }

    static int repeat(JSONObject item) {
        return Math.max(1, Math.min(9999, item.optInt("repeat", 1)));
    }

    static int intervalMs(JSONObject item) {
        return Math.max(0, Math.min(600000, item.optInt("interval", 0)));
    }

    static Library.Entry resolve(Context context, JSONObject item) {
        String id = item.optString("recordId");
        if (!id.isEmpty()) {
            Library.Entry byId = Library.find(context, id);
            if (byId != null && Library.RECORD.equals(byId.kind)) {
                return byId;
            }
        }
        String name = item.optString("name", item.optString("detail"));
        for (Library.Entry recording : Library.list(context, Library.RECORD)) {
            if (recording.name.equals(name)) {
                return recording;
            }
        }
        return null;
    }

    static JSONArray items(String body) {
        if (body == null || body.isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(body);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    static int totalExpandedSteps(Context context, String body) {
        return expandedStepCount(context, items(body));
    }

    static int expandedStepCount(Context context, JSONArray sequence) {
        long total = 0;
        for (int i = 0; i < sequence.length(); i++) {
            JSONObject item = sequence.optJSONObject(i);
            Library.Entry recording = item == null ? null : resolve(context, item);
            if (recording != null) {
                total += (long) recording.stepCount() * repeat(item);
                if (total >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) total;
    }
}
