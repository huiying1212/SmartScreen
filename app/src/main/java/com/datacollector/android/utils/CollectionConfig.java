package com.datacollector.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * 全局采集配置（SharedPreferences 存储，支持运行时修改）。
 * 涵盖数据采集、壁纸引擎、悬浮窗引擎、API 等全部可调参数。
 */
public class CollectionConfig {

    private static final String TAG = "CollectionConfig";
    private static final String PREFS_NAME = "collection_config";

    // ── 数据采集 ──────────────────────────────────────────────
    public static final String KEY_COLLECTION_INTERVAL_MS = "collection_interval_ms";
    public static final String KEY_LOCATION_INTERVAL = "location_update_interval_ms";
    public static final String KEY_LOCATION_MIN_DISTANCE = "location_min_distance_m";
    public static final String KEY_LOCATION_ENABLED = "location_enabled";
    public static final String KEY_ACTIVITY_ENABLED = "activity_recognition_enabled";
    public static final String KEY_SCREEN_USAGE_ENABLED = "screen_usage_enabled";
    public static final String KEY_SCREEN_USAGE_TOP_APPS = "screen_usage_top_apps_count";
    public static final String KEY_CALENDAR_ENABLED = "calendar_enabled";
    public static final String KEY_CALENDAR_PAST_DAYS = "calendar_past_days";
    public static final String KEY_CALENDAR_FUTURE_DAYS = "calendar_future_days";
    public static final String KEY_CALENDAR_MAX_EVENTS = "calendar_max_events";
    public static final String KEY_WIFI_ENABLED = "wifi_enabled";
    public static final String KEY_BLUETOOTH_ENABLED = "bluetooth_enabled";
    public static final String KEY_WEATHER_ENABLED = "weather_enabled";
    public static final String KEY_WEATHER_CACHE_DURATION_MS = "weather_cache_duration_ms";

    // ── 数据管理 ──────────────────────────────────────────────
    public static final String KEY_DATA_ENCRYPTION = "data_encryption_enabled";
    public static final String KEY_DATA_COMPRESSION = "data_compression_enabled";
    public static final String KEY_DATA_RETENTION_DAYS = "data_retention_days";
    public static final String KEY_ANALYSIS_RETENTION_DAYS = "analysis_retention_days";
    public static final String KEY_MAX_STORAGE_MB = "max_storage_mb";

    // ── API ───────────────────────────────────────────────────
    public static final String KEY_API_MAX_RETRIES = "api_max_retries";
    public static final String KEY_API_CONNECT_TIMEOUT = "api_connect_timeout_s";
    public static final String KEY_API_READ_TIMEOUT = "api_read_timeout_s";
    public static final String KEY_AGGREGATION_WINDOW_HOURS = "aggregation_window_hours";

    // ── 悬浮窗引擎 ───────────────────────────────────────────
    public static final String KEY_OVERLAY_ENABLED = "overlay_enabled";
    public static final String KEY_OVERLAY_UPDATE_INTERVAL_MS = "overlay_update_interval_ms";

    // ── 壁纸引擎 ─────────────────────────────────────────────
    public static final String KEY_WALLPAPER_GENERATION_ENABLED = "wallpaper_generation_enabled";
    public static final String KEY_LAST_WALLPAPER_GENERATION_TIME = "last_wallpaper_generation_time";
    public static final String KEY_WALLPAPER_SCHEDULE_SLOT_1 = "wallpaper_schedule_slot_1";
    public static final String KEY_WALLPAPER_SCHEDULE_SLOT_2 = "wallpaper_schedule_slot_2";
    public static final String KEY_WALLPAPER_SCHEDULE_SLOT_3 = "wallpaper_schedule_slot_3";

    // ── 个人设置 ──────────────────────────────────────────────
    public static final String KEY_WALLPAPER_STYLE = "wallpaper_style";
    public static final String KEY_SELECTED_ICON_INDEX = "selected_icon_index"; // deprecated
    public static final String KEY_FACE_STYLE = "face_style";
    public static final String KEY_USER_PERSONAL_GOAL = "user_personal_goal";

    private final SharedPreferences prefs;
    private static CollectionConfig instance;

    public static synchronized CollectionConfig getInstance(Context context) {
        if (instance == null) {
            instance = new CollectionConfig(context.getApplicationContext());
        }
        return instance;
    }

    private CollectionConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initializeDefaults();
    }

    private void initializeDefaults() {
        SharedPreferences.Editor editor = prefs.edit();

        // 数据采集
        putIfAbsent(editor, KEY_COLLECTION_INTERVAL_MS, 10 * 60_000L); // 10 min
        putIfAbsent(editor, KEY_LOCATION_INTERVAL, 60_000L);
        putIfAbsent(editor, KEY_LOCATION_MIN_DISTANCE, 10f);
        putIfAbsent(editor, KEY_LOCATION_ENABLED, true);
        putIfAbsent(editor, KEY_ACTIVITY_ENABLED, true);
        putIfAbsent(editor, KEY_SCREEN_USAGE_ENABLED, true);
        putIfAbsent(editor, KEY_SCREEN_USAGE_TOP_APPS, 10);
        putIfAbsent(editor, KEY_CALENDAR_ENABLED, true);
        putIfAbsent(editor, KEY_CALENDAR_PAST_DAYS, 1);
        putIfAbsent(editor, KEY_CALENDAR_FUTURE_DAYS, 1);
        putIfAbsent(editor, KEY_CALENDAR_MAX_EVENTS, 20);
        putIfAbsent(editor, KEY_WIFI_ENABLED, true);
        putIfAbsent(editor, KEY_BLUETOOTH_ENABLED, true);
        putIfAbsent(editor, KEY_WEATHER_ENABLED, true);
        putIfAbsent(editor, KEY_WEATHER_CACHE_DURATION_MS, 30 * 60_000L); // 30 min cache

        // 数据管理
        putIfAbsent(editor, KEY_DATA_ENCRYPTION, true);
        putIfAbsent(editor, KEY_DATA_COMPRESSION, true);
        putIfAbsent(editor, KEY_DATA_RETENTION_DAYS, 7);
        putIfAbsent(editor, KEY_ANALYSIS_RETENTION_DAYS, 3);
        putIfAbsent(editor, KEY_MAX_STORAGE_MB, 200);

        // API
        putIfAbsent(editor, KEY_API_MAX_RETRIES, 3);
        putIfAbsent(editor, KEY_API_CONNECT_TIMEOUT, 30);
        putIfAbsent(editor, KEY_API_READ_TIMEOUT, 60);
        putIfAbsent(editor, KEY_AGGREGATION_WINDOW_HOURS, 6);

        // 悬浮窗
        putIfAbsent(editor, KEY_OVERLAY_ENABLED, true);
        putIfAbsent(editor, KEY_OVERLAY_UPDATE_INTERVAL_MS, 30_000L); // 30s

        // 壁纸
        putIfAbsent(editor, KEY_WALLPAPER_GENERATION_ENABLED, true);
        putIfAbsent(editor, KEY_LAST_WALLPAPER_GENERATION_TIME, 0L);
        putIfAbsent(editor, KEY_WALLPAPER_SCHEDULE_SLOT_1, 8 * 60);  // 08:00
        putIfAbsent(editor, KEY_WALLPAPER_SCHEDULE_SLOT_2, 12 * 60); // 12:00
        putIfAbsent(editor, KEY_WALLPAPER_SCHEDULE_SLOT_3, 20 * 60); // 20:00

        // 个人设置
        putIfAbsent(editor, KEY_WALLPAPER_STYLE, "唯美艺术");
        putIfAbsent(editor, KEY_FACE_STYLE, "CLASSIC");
        putIfAbsent(editor, KEY_USER_PERSONAL_GOAL, "");

        editor.apply();
    }

    // ── putIfAbsent overloads ──
    private void putIfAbsent(SharedPreferences.Editor e, String k, long v) {
        if (!prefs.contains(k)) e.putLong(k, v);
    }
    private void putIfAbsent(SharedPreferences.Editor e, String k, float v) {
        if (!prefs.contains(k)) e.putFloat(k, v);
    }
    private void putIfAbsent(SharedPreferences.Editor e, String k, boolean v) {
        if (!prefs.contains(k)) e.putBoolean(k, v);
    }
    private void putIfAbsent(SharedPreferences.Editor e, String k, int v) {
        if (!prefs.contains(k)) e.putInt(k, v);
    }
    private void putIfAbsent(SharedPreferences.Editor e, String k, String v) {
        if (!prefs.contains(k)) e.putString(k, v);
    }

    // ── Getters ──
    public long getLong(String key, long def)       { return prefs.getLong(key, def); }
    public float getFloat(String key, float def)    { return prefs.getFloat(key, def); }
    public boolean getBoolean(String key, boolean d){ return prefs.getBoolean(key, d); }
    public int getInt(String key, int def)          { return prefs.getInt(key, def); }
    public String getString(String key, String def) { return prefs.getString(key, def); }

    // ── Setters ──
    public void setLong(String key, long v)       { prefs.edit().putLong(key, v).apply(); }
    public void setFloat(String key, float v)     { prefs.edit().putFloat(key, v).apply(); }
    public void setBoolean(String key, boolean v) { prefs.edit().putBoolean(key, v).apply(); }
    public void setInt(String key, int v)         { prefs.edit().putInt(key, v).apply(); }
    public void setString(String key, String v)   { prefs.edit().putString(key, v).apply(); }

    /**
     * 获取壁纸调度时间点数组（分钟表示，如 480 = 08:00）
     */
    public int[] getWallpaperScheduleSlots() {
        return new int[]{
                getInt(KEY_WALLPAPER_SCHEDULE_SLOT_1, 8 * 60),
                getInt(KEY_WALLPAPER_SCHEDULE_SLOT_2, 12 * 60),
                getInt(KEY_WALLPAPER_SCHEDULE_SLOT_3, 20 * 60)
        };
    }

    private static final String[][] WALLPAPER_STYLE_MAP = {
            {"唯美艺术", "风格唯美具有艺术感，色彩丰富细腻"},
            {"水墨国风", "中国水墨画风格，黑白灰为主调，留白意境深远"},
            {"印象派",   "印象派油画风格，笔触明显，光影交织变幻"},
            {"极简主义", "极简主义风格，简洁线条，大面积纯色留白"},
            {"自然风光", "写实自然风光摄影风格，高清细腻逼真"},
            {"赛博朋克", "赛博朋克风格，霓虹灯光，暗色调未来感"}
    };

    public String getWallpaperStyleDescription() {
        String styleName = getString(KEY_WALLPAPER_STYLE, "唯美艺术");
        return getStyleDescriptionByName(styleName);
    }

    public static String getStyleDescriptionByName(String styleName) {
        for (String[] pair : WALLPAPER_STYLE_MAP) {
            if (pair[0].equals(styleName)) return pair[1];
        }
        return WALLPAPER_STYLE_MAP[0][1];
    }

    /**
     * 获取用户个人偏好的完整描述，供 LLM Prompt 使用。
     * 包含壁纸风格偏好和个人目标。
     */
    public String getUserPreferenceDescription() {
        StringBuilder sb = new StringBuilder();
        String style = getString(KEY_WALLPAPER_STYLE, "唯美艺术");
        sb.append("壁纸风格偏好：").append(style);
        String goal = getString(KEY_USER_PERSONAL_GOAL, "");
        if (goal != null && !goal.trim().isEmpty()) {
            sb.append("\n用户个人目标：").append(goal.trim());
        }
        return sb.toString();
    }

    public void applyRemoteConfig(JSONObject config) {
        SharedPreferences.Editor editor = prefs.edit();
        Iterator<String> keys = config.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                Object value = config.get(key);
                if (value instanceof Boolean)  editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer)  editor.putInt(key, (Integer) value);
                else if (value instanceof Long)     editor.putLong(key, (Long) value);
                else if (value instanceof Float || value instanceof Double)
                    editor.putFloat(key, ((Number) value).floatValue());
                else if (value instanceof String) editor.putString(key, (String) value);
            } catch (Exception e) {
                Log.w(TAG, "Skipping invalid config key: " + key);
            }
        }
        editor.apply();
    }

    public JSONObject exportConfig() {
        JSONObject config = new JSONObject();
        try {
            for (String key : prefs.getAll().keySet()) {
                config.put(key, prefs.getAll().get(key));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error exporting config", e);
        }
        return config;
    }
}
