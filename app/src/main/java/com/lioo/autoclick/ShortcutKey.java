package com.lioo.autoclick;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONException;
import org.json.JSONObject;

final class ShortcutKey {
    static final String FLOATING = "floating";
    static final String TOOLBOX = "toolbox";

    private ShortcutKey() {
    }

    static JSONObject config(Library.Entry entry) {
        try {
            return new JSONObject(entry.body == null ? "{}" : entry.body);
        } catch (JSONException exception) {
            return new JSONObject();
        }
    }

    static String displayMode(Library.Entry entry) {
        return config(entry).optString("display", FLOATING);
    }

    static int color(Library.Entry entry) {
        try {
            return Color.parseColor(config(entry).optString("color", "#006A60"));
        } catch (IllegalArgumentException exception) {
            return 0xFF006A60;
        }
    }

    static int textSize(Library.Entry entry) {
        return Math.max(12, Math.min(32, config(entry).optInt("size", 18)));
    }

    static boolean isLocked(Library.Entry entry) {
        return config(entry).optBoolean("locked", false);
    }

    static float xRatio(Library.Entry entry, float fallback) {
        return (float) Math.max(0, Math.min(1,
                config(entry).optDouble("xRatio", fallback)));
    }

    static float yRatio(Library.Entry entry, float fallback) {
        return (float) Math.max(0, Math.min(1,
                config(entry).optDouble("yRatio", fallback)));
    }

    static void savePosition(Context context, Library.Entry entry, float xRatio, float yRatio) {
        JSONObject config = config(entry);
        try {
            config.put("xRatio", Math.max(0, Math.min(1, xRatio)));
            config.put("yRatio", Math.max(0, Math.min(1, yRatio)));
        } catch (JSONException ignored) {
            return;
        }
        entry.body = config.toString();
        Library.save(context, entry);
    }

    static Library.Entry resolveTarget(Context context, Library.Entry shortcut) {
        String target = config(shortcut).optString("target", "未绑定");
        String kind;
        String name;
        if (target.startsWith("录制 · ")) {
            kind = Library.RECORD;
            name = target.substring("录制 · ".length());
        } else if (target.startsWith("脚本 · ")) {
            kind = Library.SCRIPT;
            name = target.substring("脚本 · ".length());
        } else if (target.startsWith("流程 · ")) {
            kind = Library.GROUP;
            name = target.substring("流程 · ".length());
        } else {
            return null;
        }
        for (Library.Entry entry : Library.list(context, kind)) {
            if (entry.name.equals(name)) {
                return entry;
            }
        }
        return null;
    }
}
