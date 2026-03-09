package com.datacollector.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * 借鉴Beiwe的远程可配置采集策略：所有采集参数可动态调整，
 * 无需重新编译APK。Beiwe通过 device_settings 端点下发配置，
 * 我们通过 SharedPreferences 存储可在运行时修改的配置。
 */
public class CollectionConfig {

    private static final String TAG = "CollectionConfig";
    private static final String PREFS_NAME = "collection_config";

    // Location collector defaults
    public static final String KEY_LOCATION_INTERVAL = "location_update_interval_ms";
    public static final String KEY_LOCATION_MIN_DISTANCE = "location_min_distance_m";
    public static final String KEY_LOCATION_ENABLED = "location_enabled";

    // WiFi collector defaults
    public static final String KEY_WIFI_ENABLED = "wifi_enabled";

    // Bluetooth collector defaults
    public static final String KEY_BLUETOOTH_ENABLED = "bluetooth_enabled";

    // Activity recognition defaults
    public static final String KEY_ACTIVITY_ENABLED = "activity_recognition_enabled";

    // Screen content defaults
    public static final String KEY_SCREEN_CONTENT_ENABLED = "screen_content_enabled";

    // Calendar collector defaults
    public static final String KEY_CALENDAR_ENABLED = "calendar_enabled";
    public static final String KEY_CALENDAR_PAST_DAYS = "calendar_past_days";
    public static final String KEY_CALENDAR_FUTURE_DAYS = "calendar_future_days";
    public static final String KEY_CALENDAR_MAX_EVENTS = "calendar_max_events";

    // Reminder collector defaults
    public static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    public static final String KEY_REMINDER_PAST_DAYS = "reminder_past_days";
    public static final String KEY_REMINDER_FUTURE_DAYS = "reminder_future_days";

    // Screen usage collector defaults
    public static final String KEY_SCREEN_USAGE_ENABLED = "screen_usage_enabled";
    public static final String KEY_SCREEN_USAGE_TOP_APPS = "screen_usage_top_apps_count";

    // Data management
    public static final String KEY_AUTO_ANALYSIS = "auto_analysis_enabled";
    public static final String KEY_DATA_ENCRYPTION = "data_encryption_enabled";
    public static final String KEY_DATA_COMPRESSION = "data_compression_enabled";
    public static final String KEY_DATA_RETENTION_DAYS = "data_retention_days";
    public static final String KEY_ANALYSIS_RETENTION_DAYS = "analysis_retention_days";
    public static final String KEY_MAX_STORAGE_MB = "max_storage_mb";

    // API settings
    public static final String KEY_API_MAX_RETRIES = "api_max_retries";
    public static final String KEY_API_CONNECT_TIMEOUT = "api_connect_timeout_s";
    public static final String KEY_API_READ_TIMEOUT = "api_read_timeout_s";

    // Aggregation settings
    public static final String KEY_AGGREGATION_WINDOW_HOURS = "aggregation_window_hours";

    // Floating overlay settings
    public static final String KEY_OVERLAY_ENABLED = "overlay_enabled";

    // Wallpaper generation settings
    public static final String KEY_WALLPAPER_GENERATION_ENABLED = "wallpaper_generation_enabled";
    public static final String KEY_WALLPAPER_GENERATION_INTERVAL_MS = "wallpaper_generation_interval_ms";
    public static final String KEY_LAST_WALLPAPER_GENERATION_TIME = "last_wallpaper_generation_time";

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
        putIfAbsent(editor, KEY_LOCATION_INTERVAL, 60000L);
        putIfAbsent(editor, KEY_LOCATION_MIN_DISTANCE, 10f);
        putIfAbsent(editor, KEY_LOCATION_ENABLED, true);
        putIfAbsent(editor, KEY_WIFI_ENABLED, true);
        putIfAbsent(editor, KEY_BLUETOOTH_ENABLED, true);
        putIfAbsent(editor, KEY_ACTIVITY_ENABLED, true);
        putIfAbsent(editor, KEY_SCREEN_CONTENT_ENABLED, true);
        putIfAbsent(editor, KEY_CALENDAR_ENABLED, true);
        putIfAbsent(editor, KEY_CALENDAR_PAST_DAYS, 7);
        putIfAbsent(editor, KEY_CALENDAR_FUTURE_DAYS, 30);
        putIfAbsent(editor, KEY_CALENDAR_MAX_EVENTS, 50);
        putIfAbsent(editor, KEY_REMINDER_ENABLED, true);
        putIfAbsent(editor, KEY_REMINDER_PAST_DAYS, 3);
        putIfAbsent(editor, KEY_REMINDER_FUTURE_DAYS, 30);
        putIfAbsent(editor, KEY_SCREEN_USAGE_ENABLED, true);
        putIfAbsent(editor, KEY_SCREEN_USAGE_TOP_APPS, 10);
        putIfAbsent(editor, KEY_AUTO_ANALYSIS, true);
        putIfAbsent(editor, KEY_DATA_ENCRYPTION, true);
        putIfAbsent(editor, KEY_DATA_COMPRESSION, true);
        putIfAbsent(editor, KEY_DATA_RETENTION_DAYS, 7);
        putIfAbsent(editor, KEY_ANALYSIS_RETENTION_DAYS, 3);
        putIfAbsent(editor, KEY_MAX_STORAGE_MB, 200);
        putIfAbsent(editor, KEY_API_MAX_RETRIES, 3);
        putIfAbsent(editor, KEY_API_CONNECT_TIMEOUT, 30);
        putIfAbsent(editor, KEY_API_READ_TIMEOUT, 60);
        putIfAbsent(editor, KEY_AGGREGATION_WINDOW_HOURS, 24);
        putIfAbsent(editor, KEY_OVERLAY_ENABLED, true);
        putIfAbsent(editor, KEY_WALLPAPER_GENERATION_ENABLED, true);
        putIfAbsent(editor, KEY_WALLPAPER_GENERATION_INTERVAL_MS, 3600_000L); // 1 hour
        putIfAbsent(editor, KEY_LAST_WALLPAPER_GENERATION_TIME, 0L);
        editor.apply();
    }

    private void putIfAbsent(SharedPreferences.Editor editor, String key, long value) {
        if (!prefs.contains(key)) editor.putLong(key, value);
    }

    private void putIfAbsent(SharedPreferences.Editor editor, String key, float value) {
        if (!prefs.contains(key)) editor.putFloat(key, value);
    }

    private void putIfAbsent(SharedPreferences.Editor editor, String key, boolean value) {
        if (!prefs.contains(key)) editor.putBoolean(key, value);
    }

    private void putIfAbsent(SharedPreferences.Editor editor, String key, int value) {
        if (!prefs.contains(key)) editor.putInt(key, value);
    }

    // Typed getters
    public long getLong(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }

    public float getFloat(String key, float defaultValue) {
        return prefs.getFloat(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    // Setters (for runtime updates or remote configuration)
    public void setLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public void setInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    /**
     * 批量应用JSON格式的配置（可用于远程配置下发）
     */
    public void applyRemoteConfig(JSONObject config) {
        SharedPreferences.Editor editor = prefs.edit();
        Iterator<String> keys = config.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                Object value = config.get(key);
                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float || value instanceof Double) {
                    editor.putFloat(key, ((Number) value).floatValue());
                }
                Log.d(TAG, "Applied config: " + key + " = " + value);
            } catch (JSONException e) {
                Log.w(TAG, "Skipping invalid config key: " + key);
            }
        }
        editor.apply();
    }

    /**
     * 导出当前配置为JSON（便于远程同步或调试）
     */
    public JSONObject exportConfig() {
        JSONObject config = new JSONObject();
        try {
            for (String key : prefs.getAll().keySet()) {
                Object value = prefs.getAll().get(key);
                config.put(key, value);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error exporting config", e);
        }
        return config;
    }
}
