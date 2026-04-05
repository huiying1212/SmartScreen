package com.datacollector.android.processing;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.collectors.ActivityRecognitionCollector;
import com.datacollector.android.collectors.BluetoothDataCollector;
import com.datacollector.android.collectors.CalendarDataCollector;
import com.datacollector.android.collectors.LocationDataCollector;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.collectors.WeatherDataCollector;
import com.datacollector.android.collectors.WifiDataCollector;
import com.datacollector.android.utils.AppForegroundTracker;

import org.json.JSONObject;

/**
 * 统一的上下文快照构建器。
 *
 * 从所有可用采集器收集一次完整快照，供中层处理（LLM 评分、气泡生成等）使用。
 * 消除了 FloatingOverlayService / MainActivity 中重复的采集代码。
 */
public class ContextSnapshotCollector {

    private static final String TAG = "CtxSnapshotCollector";

    private final Context context;
    private final ScreenUsageCollector screenUsageCollector;
    private final CalendarDataCollector calendarCollector;
    private final WeatherDataCollector weatherCollector;
    private final LocationDataCollector locationCollector;
    private final ActivityRecognitionCollector activityCollector;
    private final WifiDataCollector wifiCollector;
    private final BluetoothDataCollector bluetoothCollector;

    public ContextSnapshotCollector(Context context,
                                    ScreenUsageCollector screenUsageCollector,
                                    CalendarDataCollector calendarCollector,
                                    WeatherDataCollector weatherCollector,
                                    LocationDataCollector locationCollector,
                                    ActivityRecognitionCollector activityCollector,
                                    WifiDataCollector wifiCollector,
                                    BluetoothDataCollector bluetoothCollector) {
        this.context = context.getApplicationContext();
        this.screenUsageCollector = screenUsageCollector;
        this.calendarCollector = calendarCollector;
        this.weatherCollector = weatherCollector;
        this.locationCollector = locationCollector;
        this.activityCollector = activityCollector;
        this.wifiCollector = wifiCollector;
        this.bluetoothCollector = bluetoothCollector;
    }

    /**
     * 构建完整的上下文快照。同步调用，需在后台线程执行。
     *
     * @return 包含所有可用采集器数据的 JSONObject
     */
    public JSONObject collectFullSnapshot() {
        JSONObject snapshot = new JSONObject();
        try {
            snapshot.put("timestamp", System.currentTimeMillis());
        } catch (Exception ignored) {}

        // Screen usage
        collectSafely(snapshot, "screen_usage", () -> {
            if (screenUsageCollector == null) return;
            JSONObject data = screenUsageCollector.collectData();
            if (data != null) {
                snapshot.put("screen_usage", data);
                snapshot.put("foreground_app_package",
                        data.optString("foreground_app_package", "unknown"));
            }
        });

        // Fallback foreground app from tracker
        try {
            if (!snapshot.has("foreground_app_package")
                    || "unknown".equals(snapshot.optString("foreground_app_package"))) {
                String pkg = AppForegroundTracker.getInstance(context).getCurrentPackage();
                if (pkg != null && !pkg.isEmpty()) {
                    snapshot.put("foreground_app_package", pkg);
                }
            }
        } catch (Exception ignored) {}

        // Activity recognition
        collectSafely(snapshot, "user_activity", () -> {
            if (activityCollector != null && activityCollector.isAvailable()) {
                JSONObject data = activityCollector.collectData();
                if (data != null) snapshot.put("user_activity", data);
            }
        });

        // Calendar
        collectSafely(snapshot, "calendar", () -> {
            if (calendarCollector != null && calendarCollector.isAvailable()) {
                JSONObject data = calendarCollector.collectData();
                if (data != null) snapshot.put("calendar", data);
            }
        });

        // Weather
        collectSafely(snapshot, "weather", () -> {
            if (weatherCollector != null && weatherCollector.isAvailable()) {
                JSONObject data = weatherCollector.collectData();
                if (data != null) snapshot.put("weather", data);
            }
        });

        // Location
        collectSafely(snapshot, "location", () -> {
            if (locationCollector != null && locationCollector.isAvailable()) {
                JSONObject data = locationCollector.collectData();
                if (data != null) snapshot.put("location", data);
            }
        });

        // WiFi
        collectSafely(snapshot, "wifi_info", () -> {
            if (wifiCollector != null && wifiCollector.isAvailable()) {
                JSONObject data = wifiCollector.collectData();
                if (data != null) snapshot.put("wifi_info", data);
            }
        });

        // Bluetooth
        collectSafely(snapshot, "bluetooth_devices", () -> {
            if (bluetoothCollector != null && bluetoothCollector.isAvailable()) {
                JSONObject data = bluetoothCollector.collectData();
                if (data != null) snapshot.put("bluetooth_devices", data);
            }
        });

        return snapshot;
    }

    /**
     * 构建轻量快照（仅 screen_usage + screen_on + timestamp），
     * 用于 LLM 定期评分时的最小数据集。
     */
    public JSONObject collectLightSnapshot() {
        JSONObject snapshot = new JSONObject();
        try {
            snapshot.put("screen_on", true);
            snapshot.put("timestamp", System.currentTimeMillis());
        } catch (Exception ignored) {}

        collectSafely(snapshot, "screen_usage", () -> {
            if (screenUsageCollector != null && screenUsageCollector.isAvailable()) {
                JSONObject data = screenUsageCollector.collectData();
                if (data != null) {
                    snapshot.put("screen_usage", data);
                    String pkg = data.optString("foreground_app_package", null);
                    AppForegroundTracker.getInstance(context).update(pkg);
                }
            }
        });

        collectSafely(snapshot, "user_activity", () -> {
            if (activityCollector != null && activityCollector.isAvailable()) {
                JSONObject data = activityCollector.collectData();
                if (data != null) snapshot.put("user_activity", data);
            }
        });

        collectSafely(snapshot, "calendar", () -> {
            if (calendarCollector != null && calendarCollector.isAvailable()) {
                JSONObject data = calendarCollector.collectData();
                if (data != null) snapshot.put("calendar", data);
            }
        });

        collectSafely(snapshot, "weather", () -> {
            if (weatherCollector != null && weatherCollector.isAvailable()) {
                JSONObject data = weatherCollector.collectData();
                if (data != null) snapshot.put("weather", data);
            }
        });

        collectSafely(snapshot, "location", () -> {
            if (locationCollector != null && locationCollector.isAvailable()) {
                JSONObject data = locationCollector.collectData();
                if (data != null) snapshot.put("location", data);
            }
        });

        collectSafely(snapshot, "wifi_info", () -> {
            if (wifiCollector != null && wifiCollector.isAvailable()) {
                JSONObject data = wifiCollector.collectData();
                if (data != null) snapshot.put("wifi_info", data);
            }
        });

        collectSafely(snapshot, "bluetooth_devices", () -> {
            if (bluetoothCollector != null && bluetoothCollector.isAvailable()) {
                JSONObject data = bluetoothCollector.collectData();
                if (data != null) snapshot.put("bluetooth_devices", data);
            }
        });

        return snapshot;
    }

    private void collectSafely(JSONObject snapshot, String label, CollectAction action) {
        try {
            action.run();
        } catch (Exception e) {
            Log.w(TAG, "Failed to collect " + label, e);
        }
    }

    @FunctionalInterface
    private interface CollectAction {
        void run() throws Exception;
    }
}
