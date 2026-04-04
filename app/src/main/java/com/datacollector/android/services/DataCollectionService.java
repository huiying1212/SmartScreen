package com.datacollector.android.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.datacollector.android.R;
import com.datacollector.android.activities.MainActivity;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.collectors.ActivityRecognitionCollector;
import com.datacollector.android.collectors.BluetoothDataCollector;
import com.datacollector.android.collectors.BaseDataCollector;
import com.datacollector.android.collectors.CalendarDataCollector;
import com.datacollector.android.collectors.LocationDataCollector;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.collectors.WeatherDataCollector;
import com.datacollector.android.collectors.WifiDataCollector;
import com.datacollector.android.managers.DataCollectorManager;
import com.datacollector.android.managers.WallpaperGenerationManager;
import com.datacollector.android.utils.AppCategoryClassifier;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.CollectionStats;
import com.datacollector.android.utils.DataCleanupManager;
import com.datacollector.android.utils.DataEncryptor;
import com.datacollector.android.utils.ErrorCollector;
import com.datacollector.android.utils.WifiFingerprint;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * 数据收集服务 —— 分级定期采集：
 *   轻量（screen_usage / activity / calendar）：每 2 分钟更新内存缓存
 *   重量（location / weather / wifi / bluetooth）：每 10 分钟全量采集并落盘
 *
 * 采集完成后触发壁纸引擎检查。
 */
public class DataCollectionService extends Service implements DataCollectorManager.DataCollectionCallback {

    private static final String TAG = "DataCollectionService";
    private static final String CHANNEL_ID = "DataCollectionChannel";
    private static final int NOTIFICATION_ID = 1001;

    private DataCollectorManager collectorManager;
    private DeepSeekApiClient deepSeekApiClient;
    private DataCleanupManager dataCleanupManager;
    private DataEncryptor dataEncryptor;
    private CollectionConfig collectionConfig;
    private CollectionStats collectionStats;
    private WallpaperGenerationManager wallpaperGenerationManager;

    private JSONObject currentContextData;
    private Handler collectionHandler;
    private Runnable periodicCollectionRunnable;
    private Runnable lightCollectionRunnable;
    private PowerManager.WakeLock wakeLock;

    /** 轻量采集器最新数据缓存，每次全量落盘时合并 */
    private volatile JSONObject latestLightData = new JSONObject();

    public class DataCollectionBinder extends Binder {
        public DataCollectionService getService() {
            return DataCollectionService.this;
        }
    }

    private final IBinder binder = new DataCollectionBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundService();
        acquireWakeLock();

        collectionConfig = CollectionConfig.getInstance(this);
        collectionStats = CollectionStats.getInstance(this);
        dataEncryptor = new DataEncryptor(this);
        collectionHandler = new Handler(Looper.getMainLooper());

        initializeCollectors();
        startPeriodicCollection();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();

        if (wakeLock == null || !wakeLock.isHeld()) {
            acquireWakeLock();
        }

        if (intent != null && intent.hasExtra("action")) {
            String action = intent.getStringExtra("action");
            if ("trigger_collection".equals(action)) {
                String reason = intent.getStringExtra("trigger_reason");
                Log.i(TAG, "Manual collection triggered: " + reason);
                collectCurrentContextData(reason != null ? reason : "manual");
            }
        }

        return START_STICKY;
    }

    // ── 初始化 ───────────────────────────────────────────────

    private void initializeCollectors() {
        collectorManager = new DataCollectorManager(this);
        collectorManager.setCallback(this);

        ErrorCollector initErrors = new ErrorCollector("collector_init");

        initErrors.runSafely("location", () ->
                collectorManager.registerCollector(new LocationDataCollector(this)));
        initErrors.runSafely("activity", () ->
                collectorManager.registerCollector(new ActivityRecognitionCollector(this)));
        initErrors.runSafely("screen_usage", () ->
                collectorManager.registerCollector(new ScreenUsageCollector(this)));
        initErrors.runSafely("calendar", () ->
                collectorManager.registerCollector(new CalendarDataCollector(this)));
        initErrors.runSafely("wifi", () ->
                collectorManager.registerCollector(new WifiDataCollector(this)));
        initErrors.runSafely("bluetooth", () ->
                collectorManager.registerCollector(new BluetoothDataCollector(this)));
        initErrors.runSafely("weather", () ->
                collectorManager.registerCollector(new WeatherDataCollector(this)));

        if (initErrors.hasErrors()) {
            Log.w(TAG, initErrors.getSummary());
        }

        deepSeekApiClient = new DeepSeekApiClient(this);
        dataCleanupManager = new DataCleanupManager(this);
        wallpaperGenerationManager = new WallpaperGenerationManager(this);
        currentContextData = new JSONObject();

        collectorManager.startAllCollectors();
        Log.i(TAG, "Initialized " + collectorManager.getCollectorIds().size() + " collectors");
    }

    // ── 分级定期采集 ─────────────────────────────────────────
    //   轻量（screen_usage / activity / calendar）：每 2 分钟
    //   重量（location / weather / wifi / bluetooth）：每 10 分钟
    //   全量落盘仅在重量轮询时执行，轻量轮询只更新内存缓存。

    private void startPeriodicCollection() {
        // ── 轻量轮询 ──
        lightCollectionRunnable = new Runnable() {
            @Override
            public void run() {
                collectLightData();
                long interval = collectionConfig.getLong(
                        CollectionConfig.KEY_LIGHT_COLLECTION_INTERVAL_MS, 2 * 60_000L);
                collectionHandler.postDelayed(this, interval);
            }
        };
        collectionHandler.postDelayed(lightCollectionRunnable, 15_000L); // 首次 15s 后
        Log.i(TAG, "Light collection started (interval=" + collectionConfig.getLong(
                CollectionConfig.KEY_LIGHT_COLLECTION_INTERVAL_MS, 2 * 60_000L) / 1000 + "s)");

        // ── 重量轮询（含全量落盘）──
        periodicCollectionRunnable = new Runnable() {
            @Override
            public void run() {
                collectCurrentContextData("periodic");
                long interval = collectionConfig.getLong(
                        CollectionConfig.KEY_COLLECTION_INTERVAL_MS, 10 * 60_000L);
                collectionHandler.postDelayed(this, interval);
            }
        };
        collectionHandler.postDelayed(periodicCollectionRunnable, 30_000L);
        Log.i(TAG, "Heavy collection started (interval=" + collectionConfig.getLong(
                CollectionConfig.KEY_COLLECTION_INTERVAL_MS, 10 * 60_000L) / 60000 + "min)");
    }

    /**
     * 轻量采集：仅运行 LIGHT 权重的采集器，结果缓存到内存并落盘。
     * 落盘格式与全量采集一致（context_data_*.json/enc/gz），
     * DataAggregator 无需修改即可读取。
     */
    private void collectLightData() {
        try {
            JSONObject lightData = collectorManager.collectByWeight(
                    BaseDataCollector.CollectionWeight.LIGHT);
            if (lightData.length() > 0) {
                latestLightData = lightData;

                // 构造与全量落盘相同结构的 contextData，便于 DataAggregator 统一读取
                JSONObject contextData = new JSONObject();
                contextData.put("timestamp", System.currentTimeMillis());
                contextData.put("date_time", getCurrentDateTime());
                contextData.put("day_of_week", getCurrentDayOfWeek());
                contextData.put("trigger_reason", "light");
                mergeCollectorData(contextData, lightData);
                saveContextData(contextData);

                Log.d(TAG, "Light collection done & saved (" + lightData.length() + " collectors)");
            }
        } catch (Exception e) {
            Log.w(TAG, "Light collection error", e);
        }
    }

    // ── 数据采集（全量落盘）────────────────────────────────

    private void collectCurrentContextData(String triggerReason) {
        ErrorCollector errors = new ErrorCollector("data_collection");

        try {
            JSONObject contextData = new JSONObject();
            contextData.put("timestamp", System.currentTimeMillis());
            contextData.put("date_time", getCurrentDateTime());
            contextData.put("day_of_week", getCurrentDayOfWeek());
            contextData.put("trigger_reason", triggerReason);

            // 重量采集器：实时采集
            JSONObject heavyData = collectorManager.collectByWeight(
                    BaseDataCollector.CollectionWeight.HEAVY);

            // 轻量采集器：使用最新缓存（刚在 2 分钟内更新过），
            // 同时也做一次实时采集以保证落盘数据最新
            JSONObject freshLightData = collectorManager.collectByWeight(
                    BaseDataCollector.CollectionWeight.LIGHT);
            if (freshLightData.length() > 0) {
                latestLightData = freshLightData;
            }

            // 合并两级数据
            JSONObject collectorData = new JSONObject();
            copyKeys(heavyData, collectorData);
            copyKeys(latestLightData, collectorData);

            // 记录采集统计
            java.util.Iterator<String> keys = collectorData.keys();
            while (keys.hasNext()) {
                collectionStats.recordCollectorResult(keys.next(), true);
            }

            mergeCollectorData(contextData, collectorData);

            // WiFi 指纹学习 & 推断位置上下文（写入 location 对象内部）
            recordWifiFingerprint(contextData);
            String locCtx = inferLocationContext(contextData);
            if (locCtx != null) {
                JSONObject loc = contextData.optJSONObject("location");
                if (loc != null) {
                    loc.put("location_context", locCtx);
                } else {
                    contextData.put("location_context", locCtx);
                }
            }

            if (errors.hasErrors()) {
                contextData.put("collection_errors", errors.getErrorCount());
                Log.w(TAG, errors.getSummary());
            }

            saveContextData(contextData);
            collectionStats.recordCollectionAttempt(true);

        } catch (JSONException e) {
            Log.e(TAG, "Error collecting context data", e);
            collectionStats.recordCollectionAttempt(false);
        }
    }

    private static void copyKeys(JSONObject src, JSONObject dst) throws JSONException {
        java.util.Iterator<String> it = src.keys();
        while (it.hasNext()) {
            String k = it.next();
            dst.put(k, src.get(k));
        }
    }

    private void mergeCollectorData(JSONObject ctx, JSONObject raw) throws JSONException {
        if (raw.has("location"))
            ctx.put("location", raw.get("location"));
        if (raw.has("activity_recognition"))
            ctx.put("user_activity", raw.get("activity_recognition"));
        if (raw.has("screen_usage"))
            ctx.put("screen_usage", raw.get("screen_usage"));
        if (raw.has("calendar"))
            ctx.put("calendar", raw.get("calendar"));
        if (raw.has("wifi_info"))
            ctx.put("wifi_info", raw.get("wifi_info"));
        if (raw.has("bluetooth_devices"))
            ctx.put("bluetooth_devices", raw.get("bluetooth_devices"));
        if (raw.has("weather"))
            ctx.put("weather", raw.get("weather"));
        ctx.put("collectors_status", collectorManager.getCollectorsStatus());
    }

    /**
     * 从 contextData 中提取当前 WiFi BSSID，记录到指纹学习器。
     */
    private void recordWifiFingerprint(JSONObject contextData) {
        String bssid = extractBssid(contextData);
        if (bssid != null) {
            WifiFingerprint.getInstance(this).recordObservation(bssid);
        }
    }

    private String extractBssid(JSONObject contextData) {
        JSONObject wifi = contextData.optJSONObject("wifi_info");
        if (wifi == null) return null;
        JSONObject ap = wifi.optJSONObject("connected_ap");
        if (ap == null) return null;
        String bssid = ap.optString("bssid", "");
        return bssid.isEmpty() ? null : bssid;
    }

    /**
     * 多信号加权评分推断位置上下文。
     *
     * 对于"家"和"公司/学校"，优先使用 WiFi BSSID 指纹（自动学习）；
     * 指纹未知时仅判断通勤/户外/室内三类，不猜测家或公司。
     * 如果连基本场景都无法判定，返回 null（不写入数据）。
     */
    private String inferLocationContext(JSONObject contextData) {
        try {
            double sCommute = 0;
            double sOutdoor = 0;
            double sIndoor  = 0;

            // ── 提取原始数据 ──
            JSONObject activity = contextData.optJSONObject("user_activity");
            String actType = activity != null ? activity.optString("activity_type", "unknown") : "unknown";
            int actConf = activity != null ? activity.optInt("confidence", 50) : 0;
            double confWeight = actConf / 100.0;

            JSONObject location = contextData.optJSONObject("location");
            double accuracy = 999;
            float speed = 0;
            boolean hasGps = false;
            boolean isStale = false;
            if (location != null && location.has("latitude")) {
                accuracy = location.optDouble("accuracy", 999);
                speed = (float) location.optDouble("speed", 0);
                isStale = location.optBoolean("is_stale", false);
                hasGps = true;
            }

            JSONObject wifi = contextData.optJSONObject("wifi_info");
            JSONObject ap = wifi != null ? wifi.optJSONObject("connected_ap") : null;
            boolean connectedToWifi = ap != null
                    && !ap.optString("ssid", "").isEmpty();
            String bssid = ap != null ? ap.optString("bssid", "") : "";

            JSONObject bt = contextData.optJSONObject("bluetooth_devices");
            int btCount = bt != null ? bt.optInt("device_count", 0) : 0;

            // ── WiFi 指纹优先判定家/公司 ──
            if (connectedToWifi && !bssid.isEmpty()) {
                String place = WifiFingerprint.getInstance(this).classify(bssid);
                if (place != null) {
                    return place; // 指纹已学会，直接返回
                }
            }

            // ── 指纹未知，回退到通勤/户外/室内三分类 ──

            // 信号 1: 活动识别
            if ("driving".equals(actType) || "cycling".equals(actType)) {
                sCommute += 5.0 * confWeight;
            } else if ("running".equals(actType)) {
                sOutdoor += 4.0 * confWeight;
            } else if ("walking".equals(actType)) {
                sOutdoor += 2.5 * confWeight;
                sCommute += 0.5 * confWeight;
            } else if ("still".equals(actType)) {
                sIndoor += 1.5 * confWeight;
            }

            // 信号 2: GPS 速度
            if (hasGps && !isStale) {
                if (speed > 5.0f)      sCommute += 4.0;
                else if (speed > 3.0f) sCommute += 2.5;
                else if (speed > 1.2f) sOutdoor += 1.5;
                else                   sIndoor  += 0.5;
            }

            // 信号 3: GPS 精度
            if (hasGps && !isStale) {
                if (accuracy < 15)       sOutdoor += 3.0;
                else if (accuracy < 30)  sOutdoor += 1.5;
                else if (accuracy < 60)  { /* 模糊地带 */ }
                else if (accuracy < 150) sIndoor  += 2.0;
                else                     sIndoor  += 3.0;
            }

            // 信号 4: WiFi 连接
            if (connectedToWifi) {
                sIndoor += 2.5;
            } else {
                sOutdoor += 0.8;
                sCommute += 0.5;
            }

            // 信号 5: 蓝牙设备数
            if (btCount >= 5) {
                sIndoor += 1.5;
            } else if (btCount >= 2) {
                sIndoor += 0.5;
            }

            // ── 选出得分最高的标签（仅三类）──
            double bestScore = 1.0; // 最低门槛
            String best = null;

            if (sCommute > bestScore) { best = "通勤中"; bestScore = sCommute; }
            if (sOutdoor > bestScore) { best = "户外";   bestScore = sOutdoor; }
            if (sIndoor  > bestScore) { best = "室内";   bestScore = sIndoor;  }

            return best; // null 表示判断不出来，不写入

        } catch (Exception e) {
            return null;
        }
    }

    // ── 数据保存 ─────────────────────────────────────────────

    private void saveContextData(JSONObject contextData) {
        try {
            JSONObject outputData = new JSONObject();
            outputData.put("context_data", contextData);
            outputData.put("collection_time", System.currentTimeMillis());

            File dataDir = new File(getExternalFilesDir(null), "data");
            if (!dataDir.exists()) dataDir.mkdirs();

            String jsonString = outputData.toString(4);
            boolean encryptionEnabled = collectionConfig.getBoolean(
                    CollectionConfig.KEY_DATA_ENCRYPTION, true);
            boolean compressionEnabled = collectionConfig.getBoolean(
                    CollectionConfig.KEY_DATA_COMPRESSION, true);

            String fileName = "context_data_" + System.currentTimeMillis();
            File dataFile;

            if (encryptionEnabled) {
                byte[] data = jsonString.getBytes("UTF-8");
                if (compressionEnabled) data = compressGzip(data);
                byte[] encrypted = dataEncryptor.encryptBytes(data);
                if (encrypted != null) {
                    dataFile = new File(dataDir, fileName + ".enc");
                    try (FileOutputStream fos = new FileOutputStream(dataFile)) {
                        fos.write(encrypted);
                    }
                } else {
                    dataFile = new File(dataDir, fileName + ".json");
                    try (FileWriter fw = new FileWriter(dataFile)) { fw.write(jsonString); }
                }
            } else if (compressionEnabled) {
                dataFile = new File(dataDir, fileName + ".json.gz");
                try (FileOutputStream fos = new FileOutputStream(dataFile);
                     GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
                    gzos.write(jsonString.getBytes("UTF-8"));
                }
            } else {
                dataFile = new File(dataDir, fileName + ".json");
                try (FileWriter fw = new FileWriter(dataFile)) { fw.write(jsonString); }
            }

            this.currentContextData = outputData;
            collectionStats.recordFileSaved(dataFile.length());
            Log.d(TAG, "Saved: " + dataFile.getName());

            // 仅在未加密时才写明文副本（加密模式下不再泄漏明文）
            if (!encryptionEnabled) {
                File plainFile = new File(dataDir, "context_data_latest.json");
                try (FileWriter fw = new FileWriter(plainFile)) { fw.write(jsonString); }
            }

            // 壁纸引擎检查
            triggerWallpaperGenerationIfNeeded();

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error saving context data", e);
        }
    }

    private void triggerWallpaperGenerationIfNeeded() {
        if (wallpaperGenerationManager == null || !wallpaperGenerationManager.shouldGenerate()) return;

        Log.i(TAG, "Triggering wallpaper generation");
        wallpaperGenerationManager.generateAndSetWallpaper(
                new WallpaperGenerationManager.WallpaperGenerationCallback() {
                    @Override public void onSuccess(String msg) { Log.i(TAG, "Wallpaper: " + msg); }
                    @Override public void onError(String err)   { Log.w(TAG, "Wallpaper error: " + err); }
                    @Override public void onProgress(String s)  { Log.d(TAG, "Wallpaper: " + s); }
                });
    }

    // ── 工具方法 ─────────────────────────────────────────────

    private byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) { gzos.write(data); }
        return bos.toByteArray();
    }

    private String getCurrentDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private String getCurrentDayOfWeek() {
        return new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date());
    }

    // ── 通知与电源 ───────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "数据收集服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("CATIA3 后台数据收集");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CATIA3 数据收集")
                .setContentText("轻量采集 2 分钟 / 全量采集 10 分钟")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CATIA3::DataCollectionWakeLock");
            wakeLock.acquire(10 * 60 * 1000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    // ── Binder 暴露的 API ────────────────────────────────────

    public JSONObject getCompleteContextData() {
        collectCurrentContextData("external_api_call");
        return currentContextData;
    }

    public DataCollectorManager getCollectorManager() { return collectorManager; }

    public String getDataCleanupStats() {
        return dataCleanupManager != null ? dataCleanupManager.getCleanupStats() : "未初始化";
    }

    public void performManualCleanup() {
        if (dataCleanupManager != null) dataCleanupManager.performImmediateCleanup();
    }

    public String getCollectionStatsSummary() { return collectionStats.getStatsSummary(); }

    public JSONObject getCurrentConfig() { return collectionConfig.exportConfig(); }

    // ── Callbacks ────────────────────────────────────────────

    @Override
    public void onDataCollected(String collectorId, Object data) {
        Log.d(TAG, "Data from: " + collectorId);
    }

    @Override
    public void onCollectionError(String collectorId, Exception error) {
        Log.e(TAG, "Error from: " + collectorId, error);
        collectionStats.recordCollectorResult(collectorId, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (collectionHandler != null) collectionHandler.removeCallbacksAndMessages(null);
        if (collectorManager != null) collectorManager.shutdown();
        if (deepSeekApiClient != null) deepSeekApiClient.shutdown();
        if (wallpaperGenerationManager != null) wallpaperGenerationManager.shutdown();
        if (dataCleanupManager != null) dataCleanupManager.stopCleanup();
        releaseWakeLock();
        Log.i(TAG, "DataCollectionService destroyed");
    }
}
