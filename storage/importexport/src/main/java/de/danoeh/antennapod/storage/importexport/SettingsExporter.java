package de.danoeh.antennapod.storage.importexport;

import android.content.Context;
import android.content.SharedPreferences;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsExporter {
    private static final String KEY_VERSION = "antennapodSettingsVersion";
    private static final String KEY_FILES = "files";
    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";
    private static final int VERSION = 1;
    private static final String DEFAULT_FILE = "default";
    private static final List<String> FILES = Arrays.asList(DEFAULT_FILE, "SleepTimerDialog", "PrefHomeFragment",
            "SwipeActionsPrefs", "StatisticsActivityPrefs", "NavDrawerPrefs", "PrefAllEpisodesFragment",
            "PrefNewEpisodesFragment", "ShareDialog");
    private static final Set<String> EXCLUDED_KEYS = new HashSet<>(Arrays.asList("prefDataFolder",
            "prefAutomaticExportFolder", "prefProxyUser", "prefProxyPassword", "PendingTimerValue"));
    private static final String EXCLUDED_PREFIX = "de.danoeh.antennapod.preferences.";

    private SettingsExporter() {
    }

    public static void exportToStream(Context context, OutputStream outputStream) throws IOException {
        try {
            JSONObject files = new JSONObject();
            for (String name : FILES) {
                JSONObject entries = new JSONObject();
                for (Map.Entry<String, ?> entry : getPreferences(context, name).getAll().entrySet()) {
                    if (isExcluded(entry.getKey()) || entry.getValue() == null) {
                        continue;
                    }
                    entries.put(entry.getKey(), toJson(entry.getValue()));
                }
                files.put(name, entries);
            }
            JSONObject root = new JSONObject();
            root.put(KEY_VERSION, VERSION);
            root.put(KEY_FILES, files);
            outputStream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public static void importFromStream(Context context, InputStream inputStream) throws IOException {
        Map<String, Map<String, Object>> imported = new HashMap<>();
        try {
            JSONObject root = new JSONObject(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
            if (root.optInt(KEY_VERSION, 0) != VERSION) {
                throw new IOException(context.getString(R.string.settings_import_invalid));
            }
            JSONObject files = root.getJSONObject(KEY_FILES);
            for (String name : FILES) {
                JSONObject entries = files.optJSONObject(name);
                if (entries == null) {
                    continue;
                }
                Map<String, Object> values = new HashMap<>();
                Iterator<String> keys = entries.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!isExcluded(key)) {
                        values.put(key, fromJson(entries.getJSONObject(key)));
                    }
                }
                imported.put(name, values);
            }
        } catch (JSONException e) {
            throw new IOException(context.getString(R.string.settings_import_invalid), e);
        }

        for (Map.Entry<String, Map<String, Object>> file : imported.entrySet()) {
            SharedPreferences preferences = getPreferences(context, file.getKey());
            SharedPreferences.Editor editor = preferences.edit();
            for (String key : preferences.getAll().keySet()) {
                if (!isExcluded(key)) {
                    editor.remove(key);
                }
            }
            for (Map.Entry<String, Object> entry : file.getValue().entrySet()) {
                putValue(editor, entry.getKey(), entry.getValue());
            }
            editor.commit();
        }
    }

    private static SharedPreferences getPreferences(Context context, String name) {
        if (DEFAULT_FILE.equals(name)) {
            name = context.getPackageName() + "_preferences";
        }
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private static boolean isExcluded(String key) {
        return EXCLUDED_KEYS.contains(key) || key.startsWith(EXCLUDED_PREFIX);
    }

    private static JSONObject toJson(Object value) throws JSONException {
        JSONObject json = new JSONObject();
        if (value instanceof Set) {
            json.put(KEY_TYPE, "StringSet");
            json.put(KEY_VALUE, new JSONArray((Set<?>) value));
        } else {
            json.put(KEY_TYPE, value.getClass().getSimpleName());
            json.put(KEY_VALUE, value);
        }
        return json;
    }

    private static Object fromJson(JSONObject json) throws JSONException {
        String type = json.getString(KEY_TYPE);
        switch (type) {
            case "Boolean":
                return json.getBoolean(KEY_VALUE);
            case "Integer":
                return json.getInt(KEY_VALUE);
            case "Long":
                return json.getLong(KEY_VALUE);
            case "Float":
                return (float) json.getDouble(KEY_VALUE);
            case "String":
                return json.getString(KEY_VALUE);
            case "StringSet":
                JSONArray array = json.getJSONArray(KEY_VALUE);
                Set<String> set = new HashSet<>();
                for (int i = 0; i < array.length(); i++) {
                    set.add(array.getString(i));
                }
                return set;
            default:
                throw new JSONException("Unknown type " + type);
        }
    }

    @SuppressWarnings("unchecked")
    private static void putValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Set) {
            editor.putStringSet(key, (Set<String>) value);
        }
    }
}
