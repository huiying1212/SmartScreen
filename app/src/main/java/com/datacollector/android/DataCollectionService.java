package com.datacollector.android;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.datacollector.android.collectors.ActivityRecognitionCollector;
import com.datacollector.android.collectors.BluetoothDataCollector;
import com.datacollector.android.collectors.LocationDataCollector;
import com.datacollector.android.collectors.WiFiDataCollector;
import com.datacollector.android.managers.DataCollectorManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 重构后的数据收集服务
 * 使用新的接口抽象架构，通过DataCollectorManager管理所有数据收集器
 */
public class DataCollectionService extends Service implements DataCollectorManager.DataCollectionCallback {
    
    private static final String TAG = "DataCollectionService";
    
    // 数据收集器管理器
    private DataCollectorManager collectorManager;
    
    // 屏幕内容收集器（暂时保持原有实现）
    private ScreenContentCollector screenCollector;
    
    // 数据存储
    private JSONObject currentContextData;
    private Timer dataCollectionTimer;
    
    // Binder类用于与Activity通信
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
        initializeCollectors();
        startDataCollection();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // 服务被杀死后会自动重启
    }
    
    /**
     * 初始化数据收集器
     */
    private void initializeCollectors() {
        // 创建数据收集器管理器
        collectorManager = new DataCollectorManager(this);
        collectorManager.setCallback(this);
        
        // 注册各种数据收集器
        collectorManager.registerCollector(new LocationDataCollector(this));
        collectorManager.registerCollector(new BluetoothDataCollector(this));
        collectorManager.registerCollector(new WiFiDataCollector(this));
        collectorManager.registerCollector(new ActivityRecognitionCollector(this));
        
        // 初始化屏幕内容收集器（保持原有实现）
        screenCollector = new ScreenContentCollector(this);
        
        // 初始化数据结构
        currentContextData = new JSONObject();
        
        Log.i(TAG, "Initialized " + collectorManager.getCollectorIds().size() + " data collectors");
    }
    
    /**
     * 开始数据收集
     */
    private void startDataCollection() {
        // 启动所有收集器
        collectorManager.startAllCollectors();
        
        // 启动屏幕内容收集
        if (screenCollector != null) {
            screenCollector.startCollection();
        }
        
        // 启动定时数据收集
        startPeriodicDataCollection();
        
        Log.i(TAG, "Started data collection");
    }
    
    /**
     * 启动周期性数据收集
     */
    private void startPeriodicDataCollection() {
        dataCollectionTimer = new Timer();
        dataCollectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                collectCurrentContextData();
            }
        }, 0, 30000); // 每30秒收集一次完整的上下文数据
    }
    
    /**
     * 收集当前上下文数据
     */
    private void collectCurrentContextData() {
        try {
            JSONObject contextData = new JSONObject();
            
            // 添加时间信息
            contextData.put("timestamp", System.currentTimeMillis());
            contextData.put("date_time", getCurrentDateTime());
            contextData.put("day_of_week", getCurrentDayOfWeek());
            
            // 从所有数据收集器收集数据
            JSONObject collectorData = collectorManager.collectAllData();
            
            // 合并收集器数据到上下文数据
            if (collectorData != null) {
                // 将各个收集器的数据按原有格式放置
                mergeCollectorData(contextData, collectorData);
            }
            
            // 添加屏幕内容（保持原有实现）
            if (screenCollector != null) {
                contextData.put("screen_content", screenCollector.getRecentScreenContent());
            }
            
            // 添加当前应用信息
            contextData.put("current_app", getCurrentAppInfo());
            
            // 保存数据
            saveContextData(contextData);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error collecting context data", e);
        }
    }
    
    /**
     * 合并收集器数据到上下文数据中
     */
    private void mergeCollectorData(JSONObject contextData, JSONObject collectorData) throws JSONException {
        // 映射新的收集器数据到原有的数据格式
        if (collectorData.has("location")) {
            contextData.put("location", collectorData.get("location"));
        }
        
        if (collectorData.has("bluetooth")) {
            contextData.put("bluetooth_devices", collectorData.get("bluetooth"));
        }
        
        if (collectorData.has("wifi")) {
            contextData.put("wifi_info", collectorData.get("wifi"));
        }
        
        if (collectorData.has("activity_recognition")) {
            contextData.put("activity", collectorData.get("activity_recognition"));
        }
        
        // 添加收集器状态信息
        contextData.put("collectors_status", collectorManager.getCollectorsStatus());
    }
    
    /**
     * 获取当前日期时间
     */
    private String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    /**
     * 获取当前星期几
     */
    private String getCurrentDayOfWeek() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    /**
     * 获取当前应用信息
     */
    private JSONObject getCurrentAppInfo() {
        try {
            JSONObject appInfo = new JSONObject();
            appInfo.put("package_name", getPackageName());
            appInfo.put("service_name", TAG);
            return appInfo;
        } catch (JSONException e) {
            Log.e(TAG, "Error getting app info", e);
            return null;
        }
    }
    
    /**
     * 保存上下文数据到JSON文件
     */
    private void saveContextData(JSONObject contextData) {
        try {
            // 创建输出JSON对象
            JSONObject outputData = new JSONObject();
            outputData.put("context_data", contextData);
            outputData.put("collection_time", System.currentTimeMillis());
            
            // 保存到文件
            String fileName = "context_data_" + System.currentTimeMillis() + ".json";
            FileWriter fileWriter = new FileWriter(getExternalFilesDir(null) + "/" + fileName);
            fileWriter.write(outputData.toString(4)); // 4 spaces for pretty printing
            fileWriter.close();
            
            // 同时保存到实时数据结构
            this.currentContextData = outputData;
            
            Log.d(TAG, "Saved context data to: " + fileName);
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error saving context data", e);
        }
    }
    
    /**
     * 获取完整的上下文数据（外部调用接口）
     */
    public JSONObject getCompleteContextData() {
        collectCurrentContextData();
        return currentContextData;
    }
    
    /**
     * 获取数据收集器管理器（用于外部配置）
     */
    public DataCollectorManager getCollectorManager() {
        return collectorManager;
    }
    
    // DataCollectionCallback 接口实现
    @Override
    public void onDataCollected(String collectorId, Object data) {
        Log.d(TAG, "Data collected from: " + collectorId);
        // 可以在这里处理实时数据回调
    }
    
    @Override
    public void onCollectionError(String collectorId, Exception error) {
        Log.e(TAG, "Collection error from: " + collectorId, error);
        // 可以在这里处理收集错误
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // 清理资源
        if (dataCollectionTimer != null) {
            dataCollectionTimer.cancel();
        }
        
        if (collectorManager != null) {
            collectorManager.shutdown();
        }
        
        if (screenCollector != null) {
            screenCollector.stopCollection();
        }
        
        Log.i(TAG, "DataCollectionService destroyed");
    }
} 