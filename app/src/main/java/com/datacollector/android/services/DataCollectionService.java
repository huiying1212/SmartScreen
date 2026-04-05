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
import com.datacollector.android.processing.DataPersistenceManager;
import com.datacollector.android.processing.LocationContextInferrer;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.CollectionStats;
import com.datacollector.android.utils.DataCleanupManager;
import com.datacollector.android.utils.ErrorCollector;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 数据收集服务 —— 分级定期采集：
 *   轻量（screen_usage / activity / calendar）：每 2 分钟更新内存缓存
 *   重量（location / weather / wifi / bluetooth）：每 10 分钟全量采集并落盘
 *
 * 采集完成后触发壁纸引擎检查。
 *
 * 职责：底层采集调度 + 通过中层组件完成持久化和位置推断。
 */
public class DataCollectionService extends Service implements DataCollectorManager.DataCollectionCallback {

    private static final String TAG = "DataCollectionService";
    private static final String CHANNEL_ID = "DataCollectionChannel";
    private static final int NOTIFICATION_ID = 1001;

    // ── 底层：采集器管理 ──
    private DataCollectorManager collectorManager;

    // ── 中层：处理组件 ──
    private DataPersistenceManager persistenceManager;
    private LocationContextInferrer locationInferrer;
    private WallpaperGenerationManager wallpaperGenerationManager;
    private DeepSeekApiClient deepSeekApiClient;

    // ── 工具 ──
    private DataCleanupManager dataCleanupManager;
    private CollectionConfig collectionConfig;
    private CollectionStats collectionStats;

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
        collectionHandler = new Handler(Looper.getMainLooper());

        // 初始化中层处理组件
        persistenceManager = new DataPersistenceManager(this);
        locationInferrer = new LocationContextInferrer(this);

        initializeCollectors();
        startPeriodicCollection();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();

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
     */
    private void collectLightData() {
        try {
            JSONObject lightData = collectorManager.collectByWeight(
                    BaseDataCollector.CollectionWeight.LIGHT);
            if (lightData.length() > 0) {
                latestLightData = lightData;

                // 构造与全量落盘相同结构的 contextData
                JSONObject contextData = new JSONObject();
                contextData.put("timestamp", System.currentTimeMillis());
                contextData.put("date_time", getCurrentDateTime());
                contextData.put("day_of_week", getCurrentDayOfWeek());
                contextData.put("trigger_reason", "light");
                mergeCollectorData(contextData, lightData);

                // 通过中层持久化管理器保存
                persistenceManager.save(contextData);

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

            // 轻量采集器：同时也做一次实时采集以保证落盘数据最新
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

            // 通过中层位置推断器处理 WiFi 指纹和位置上下文
            locationInferrer.recordWifiFingerprint(contextData);
            String locCtx = locationInferrer.infer(contextData);
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

            // 通过中层持久化管理器保存
            JSONObject outputData = persistenceManager.save(contextData);
            if (outputData != null) {
                this.currentContextData = outputData;
            }
            collectionStats.recordCollectionAttempt(true);

            // 壁纸引擎检查
            triggerWallpaperGenerationIfNeeded();

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
                    CHANNEL_ID, "RI4SU 上下文采集", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("RI4SU 后台上下文采集");
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
                .setContentTitle("RI4SU 上下文采集")
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
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RI4SU::DataCollectionWakeLock");
            wakeLock.acquire(); // 前台 Service 生命周期内持有，onDestroy 中释放
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
