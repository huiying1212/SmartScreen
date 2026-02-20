package com.datacollector.android.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.datacollector.android.collectors.ActivityRecognitionCollector;
import com.datacollector.android.collectors.BluetoothDataCollector;
import com.datacollector.android.collectors.LocationDataCollector;
import com.datacollector.android.collectors.ScreenContentCollector;
import com.datacollector.android.collectors.WiFiDataCollector;
import com.datacollector.android.managers.DataCollectorManager;
import com.datacollector.android.activities.LauncherActivity;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.CollectionStats;
import com.datacollector.android.utils.DataCleanupManager;
import com.datacollector.android.utils.DataEncryptor;
import com.datacollector.android.utils.ErrorCollector;
import com.datacollector.android.R;

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
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.GZIPOutputStream;

/**
 * 数据收集服务（升级版）
 * 借鉴Beiwe的改进：
 * - 数据加密存储（AES-GCM）
 * - GZIP压缩减少存储占用
 * - ErrorCollector独立错误处理（单个collector失败不影响整体）
 * - 采集统计追踪
 * - 可配置的采集参数
 */
public class DataCollectionService extends Service implements DataCollectorManager.DataCollectionCallback {

    private static final String TAG = "DataCollectionService";
    private static final String CHANNEL_ID = "DataCollectionChannel";
    private static final int NOTIFICATION_ID = 1001;

    private DataCollectorManager collectorManager;
    private ScreenContentCollector screenCollector;
    private DeepSeekApiClient deepSeekApiClient;
    private DataCleanupManager dataCleanupManager;
    private DataEncryptor dataEncryptor;
    private CollectionConfig collectionConfig;
    private CollectionStats collectionStats;

    private JSONObject currentContextData;
    private Timer dataCollectionTimer;
    private String lastTriggerReason = "unknown";
    private boolean autoAnalysisEnabled = true;
    private PowerManager.WakeLock wakeLock;

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

        initializeCollectors();
        startDataCollection();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 1000L);
        } else {
            acquireWakeLock();
        }

        if (intent != null && intent.hasExtra("action")) {
            String action = intent.getStringExtra("action");
            if ("trigger_collection".equals(action)) {
                String triggerReason = intent.getStringExtra("trigger_reason");
                Log.i(TAG, "Manual data collection triggered: " + triggerReason);
                collectCurrentContextData(triggerReason);
            }
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "数据收集服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("CATIA3 后台数据收集服务");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, LauncherActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CATIA3 数据收集")
                .setContentText("正在后台收集用户行为数据")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "CATIA3::DataCollectionWakeLock");
            wakeLock.acquire(10 * 60 * 1000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void initializeCollectors() {
        collectorManager = new DataCollectorManager(this);
        collectorManager.setCallback(this);

        // Use ErrorCollector so one failed collector doesn't block others
        ErrorCollector initErrors = new ErrorCollector("collector_init");

        initErrors.runSafely("location", () ->
                collectorManager.registerCollector(new LocationDataCollector(this)));
        initErrors.runSafely("bluetooth", () ->
                collectorManager.registerCollector(new BluetoothDataCollector(this)));
        initErrors.runSafely("wifi", () ->
                collectorManager.registerCollector(new WiFiDataCollector(this)));
        initErrors.runSafely("activity", () ->
                collectorManager.registerCollector(new ActivityRecognitionCollector(this)));

        initErrors.runSafely("screen_content", () -> {
            screenCollector = new ScreenContentCollector(this);
        });

        if (initErrors.hasErrors()) {
            Log.w(TAG, initErrors.getSummary());
        }

        deepSeekApiClient = new DeepSeekApiClient(this);
        dataCleanupManager = new DataCleanupManager(this);
        currentContextData = new JSONObject();

        Log.i(TAG, "Initialized " + collectorManager.getCollectorIds().size() + " data collectors");
    }

    private void startDataCollection() {
        collectorManager.startAllCollectors();
        if (screenCollector != null) {
            screenCollector.startCollection();
        }
        Log.i(TAG, "Started data collection (manual trigger mode)");
    }

    @Deprecated
    private void startPeriodicDataCollection() {
        Log.i(TAG, "Periodic data collection disabled - using manual trigger mode");
    }

    private void collectCurrentContextData() {
        collectCurrentContextData("manual_call");
    }

    /**
     * 使用ErrorCollector确保单个采集器失败不会影响整体流程
     */
    private void collectCurrentContextData(String triggerReason) {
        ErrorCollector errors = new ErrorCollector("data_collection");

        try {
            JSONObject contextData = new JSONObject();
            contextData.put("timestamp", System.currentTimeMillis());
            contextData.put("date_time", getCurrentDateTime());
            contextData.put("day_of_week", getCurrentDayOfWeek());
            contextData.put("trigger_reason", triggerReason);
            contextData.put("collection_mode", "manual_trigger");

            // Each collector runs independently - failure in one doesn't block others
            JSONObject collectorData = new JSONObject();

            for (String collectorId : collectorManager.getCollectorIds()) {
                errors.runSafely("collect_" + collectorId, () -> {
                    Object data = collectorManager.collectData(collectorId);
                    if (data != null) {
                        try {
                            collectorData.put(collectorId, data);
                            collectionStats.recordCollectorResult(collectorId, true);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        collectionStats.recordCollectorResult(collectorId, false);
                    }
                });
            }

            mergeCollectorData(contextData, collectorData);

            errors.runSafely("screen_content", () -> {
                if (screenCollector != null) {
                    try {
                        contextData.put("screen_content", screenCollector.getRecentScreenContent());
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            contextData.put("current_app", getCurrentAppInfo());

            // Record collection errors as metadata for transparency
            if (errors.hasErrors()) {
                contextData.put("collection_errors", errors.getErrorCount());
                Log.w(TAG, errors.getSummary());
            }

            saveContextData(contextData);
            collectionStats.recordCollectionAttempt(true);
            lastTriggerReason = triggerReason;

        } catch (JSONException e) {
            Log.e(TAG, "Error collecting context data", e);
            collectionStats.recordCollectionAttempt(false);
        }
    }

    private void mergeCollectorData(JSONObject contextData, JSONObject collectorData) throws JSONException {
        if (collectorData.has("location"))
            contextData.put("location", collectorData.get("location"));
        if (collectorData.has("bluetooth"))
            contextData.put("bluetooth_devices", collectorData.get("bluetooth"));
        if (collectorData.has("wifi"))
            contextData.put("wifi_info", collectorData.get("wifi"));
        if (collectorData.has("activity_recognition"))
            contextData.put("activity", collectorData.get("activity_recognition"));
        contextData.put("collectors_status", collectorManager.getCollectorsStatus());
    }

    private String getCurrentDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private String getCurrentDayOfWeek() {
        return new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date());
    }

    private JSONObject getCurrentAppInfo() {
        try {
            JSONObject appInfo = new JSONObject();
            appInfo.put("package_name", getPackageName());
            appInfo.put("service_name", TAG);
            return appInfo;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * 保存数据：支持可选的加密和GZIP压缩
     */
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
                // Compress then encrypt
                byte[] data = jsonString.getBytes("UTF-8");
                if (compressionEnabled) {
                    data = compressGzip(data);
                }
                byte[] encrypted = dataEncryptor.encryptBytes(data);
                if (encrypted != null) {
                    dataFile = new File(dataDir, fileName + ".enc");
                    try (FileOutputStream fos = new FileOutputStream(dataFile)) {
                        fos.write(encrypted);
                    }
                } else {
                    // Encryption failed, fall back to plaintext
                    dataFile = new File(dataDir, fileName + ".json");
                    try (FileWriter fw = new FileWriter(dataFile)) {
                        fw.write(jsonString);
                    }
                }
            } else if (compressionEnabled) {
                dataFile = new File(dataDir, fileName + ".json.gz");
                try (FileOutputStream fos = new FileOutputStream(dataFile);
                     GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
                    gzos.write(jsonString.getBytes("UTF-8"));
                }
            } else {
                dataFile = new File(dataDir, fileName + ".json");
                try (FileWriter fw = new FileWriter(dataFile)) {
                    fw.write(jsonString);
                }
            }

            this.currentContextData = outputData;
            collectionStats.recordFileSaved(dataFile.length());

            Log.d(TAG, "Saved context data to: " + dataFile.getName() +
                    " (encrypted=" + encryptionEnabled + ", compressed=" + compressionEnabled + ")");

            // Also save a plaintext copy for LLM analysis (auto-cleaned quickly)
            File plainFile = new File(dataDir, "context_data_" + System.currentTimeMillis() + ".json");
            try (FileWriter fw = new FileWriter(plainFile)) {
                fw.write(jsonString);
            }

            if (autoAnalysisEnabled && deepSeekApiClient != null) {
                try {
                    deepSeekApiClient.analyzeContextData(outputData);
                } catch (Exception e) {
                    Log.e(TAG, "Error performing LLM analysis", e);
                }
            }

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error saving context data", e);
        }
    }

    private byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            gzos.write(data);
        }
        return bos.toByteArray();
    }

    public JSONObject getCompleteContextData() {
        collectCurrentContextData("external_api_call");
        return currentContextData;
    }

    public DataCollectorManager getCollectorManager() {
        return collectorManager;
    }

    public void setAutoAnalysisEnabled(boolean enabled) {
        this.autoAnalysisEnabled = enabled;
        collectionConfig.setBoolean(CollectionConfig.KEY_AUTO_ANALYSIS, enabled);
    }

    public boolean isAutoAnalysisEnabled() {
        return autoAnalysisEnabled;
    }

    public String getDataCleanupStats() {
        return dataCleanupManager != null ? dataCleanupManager.getCleanupStats() : "数据清理管理器未初始化";
    }

    public void performManualCleanup() {
        if (dataCleanupManager != null) {
            dataCleanupManager.performImmediateCleanup();
        }
    }

    public void triggerManualAnalysis() {
        if (deepSeekApiClient != null && currentContextData != null) {
            deepSeekApiClient.analyzeContextData(currentContextData);
        }
    }

    /**
     * 获取采集统计摘要
     */
    public String getCollectionStatsSummary() {
        return collectionStats.getStatsSummary();
    }

    /**
     * 获取当前配置JSON
     */
    public JSONObject getCurrentConfig() {
        return collectionConfig.exportConfig();
    }

    /** 屏幕内容统计（OCR 已移除，仅返回无障碍文本条数） */
    public JSONObject getOcrStatistics() {
        if (screenCollector != null) {
            return screenCollector.getOcrStatistics();
        }
        JSONObject emptyStats = new JSONObject();
        try {
            emptyStats.put("total_items", 0);
            emptyStats.put("ocr_enabled", false);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating empty statistics", e);
        }
        return emptyStats;
    }

    @Override
    public void onDataCollected(String collectorId, Object data) {
        Log.d(TAG, "Data collected from: " + collectorId);
    }

    @Override
    public void onCollectionError(String collectorId, Exception error) {
        Log.e(TAG, "Collection error from: " + collectorId, error);
        collectionStats.recordCollectorResult(collectorId, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (collectorManager != null) collectorManager.stopAllCollectors();
        if (screenCollector != null) screenCollector.stopCollection();
        if (dataCollectionTimer != null) {
            dataCollectionTimer.cancel();
            dataCollectionTimer = null;
        }
        if (deepSeekApiClient != null) deepSeekApiClient.shutdown();
        if (dataCleanupManager != null) dataCleanupManager.stopCleanup();
        releaseWakeLock();
        Log.i(TAG, "DataCollectionService destroyed");
    }
}
