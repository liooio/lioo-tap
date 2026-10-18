package com.lioo.autoclick;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class Library {
    static final String SCRIPT = "script";
    static final String RECORD = "record";
    static final String GROUP = "group";
    static final String KEY = "key";

    static final class Entry {
        String id;
        String kind;
        String name;
        String body;
        long updated;

        String timeLabel() {
            return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(updated));
        }

        int stepCount() {
            try {
                return new JSONArray(body == null || body.isEmpty() ? "[]" : body).length();
            } catch (JSONException exception) {
                return 0;
            }
        }
    }

    private Library() {
    }

    static List<Entry> list(Context context, String kind) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all(context)) {
            if (kind.equals(entry.kind)) {
                result.add(entry);
            }
        }
        return result;
    }

    static Entry find(Context context, String id) {
        for (Entry entry : all(context)) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    static Entry create(String kind, String name) {
        Entry entry = new Entry();
        entry.id = UUID.randomUUID().toString();
        entry.kind = kind;
        entry.name = name;
        entry.body = KEY.equals(kind)
                ? "{\"color\":\"#006A60\",\"size\":18,\"target\":\"未绑定\","
                        + "\"interval\":500,\"count\":0,\"display\":\"floating\","
                        + "\"locked\":false}"
                : "[]";
        entry.updated = System.currentTimeMillis();
        return entry;
    }

    static void save(Context context, Entry entry) {
        entry.updated = System.currentTimeMillis();
        List<Entry> entries = all(context);
        boolean replaced = false;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(entry.id)) {
                entries.set(i, entry);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            entries.add(0, entry);
        }
        write(context, entries);
    }

    static void delete(Context context, String id) {
        List<Entry> entries = all(context);
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).id.equals(id)) {
                entries.remove(i);
            }
        }
        write(context, entries);
    }

    private static List<Entry> all(Context context) {
        List<Entry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString("items", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Entry entry = new Entry();
                entry.id = object.getString("id");
                entry.kind = object.getString("kind");
                entry.name = object.optString("name", "未命名");
                entry.body = object.optString("body", "[]");
                entry.updated = object.optLong("updated");
                result.add(entry);
            }
        } catch (JSONException exception) {
            return result;
        }
        return result;
    }

    private static void write(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", entry.id);
                object.put("kind", entry.kind);
                object.put("name", entry.name);
                object.put("body", entry.body);
                object.put("updated", entry.updated);
            } catch (JSONException exception) {
                continue;
            }
            array.put(object);
        }
        prefs(context).edit().putString("items", array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("library", Context.MODE_PRIVATE);
    }
}
