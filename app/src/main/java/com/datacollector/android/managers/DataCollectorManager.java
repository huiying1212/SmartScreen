package com.datacollector.android.managers;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.collectors.BaseDataCollector;
import com.datacollector.android.interfaces.DataCollector;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 数据收集器管理器
 * 负责注册、管理和协调所有数据收集器的工作
 */
public class DataCollectorManager {
    
    private static final String TAG = "DataCollectorManager";
    
    private final Context context;
    private final Map<String, DataCollector<?>> collectors;
    private final ExecutorService executorService;
    private boolean isStarted = false;
    
    // 数据收集回调接口
    public interface DataCollectionCallback {
        void onDataCollected(String collectorId, Object data);
        void onCollectionError(String collectorId, Exception error);
    }
    
    private DataCollectionCallback callback;
    
    public DataCollectorManager(Context context) {
        this.context = context;
        this.collectors = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * 注册数据收集器
     */
    public void registerCollector(DataCollector<?> collector) {
        if (collector == null) {
            Log.w(TAG, "Cannot register null collector");
            return;
        }
        
        String id = collector.getCollectorId();
        if (collectors.containsKey(id)) {
            Log.w(TAG, "Collector with id '" + id + "' already registered, replacing...");
        }
        
        collectors.put(id, collector);
        Log.i(TAG, "Registered collector: " + id);
        
        // 如果管理器已启动，自动启动新注册的收集器
        if (isStarted && collector.isAvailable()) {
            collector.startCollection();
        }
    }
    
    /**
     * 注销数据收集器
     */
    public void unregisterCollector(String collectorId) {
        DataCollector<?> collector = collectors.remove(collectorId);
        if (collector != null) {
            collector.stopCollection();
            Log.i(TAG, "Unregistered collector: " + collectorId);
        } else {
            Log.w(TAG, "Collector not found: " + collectorId);
        }
    }
    
    /**
     * 获取数据收集器
     */
    public DataCollector<?> getCollector(String collectorId) {
        return collectors.get(collectorId);
    }
    
    /**
     * 获取所有已注册的数据收集器ID
     */
    public List<String> getCollectorIds() {
        return new ArrayList<>(collectors.keySet());
    }
    
    /**
     * 启动所有数据收集器
     */
    public void startAllCollectors() {
        isStarted = true;
        for (Map.Entry<String, DataCollector<?>> entry : collectors.entrySet()) {
            DataCollector<?> collector = entry.getValue();
            if (collector.isAvailable()) {
                try {
                    collector.startCollection();
                    Log.d(TAG, "Started collector: " + entry.getKey());
                } catch (Exception e) {
                    Log.e(TAG, "Error starting collector: " + entry.getKey(), e);
                }
            } else {
                Log.w(TAG, "Collector not available: " + entry.getKey());
            }
        }
    }
    
    /**
     * 停止所有数据收集器
     */
    public void stopAllCollectors() {
        isStarted = false;
        for (Map.Entry<String, DataCollector<?>> entry : collectors.entrySet()) {
            try {
                entry.getValue().stopCollection();
                Log.d(TAG, "Stopped collector: " + entry.getKey());
            } catch (Exception e) {
                Log.e(TAG, "Error stopping collector: " + entry.getKey(), e);
            }
        }
    }
    
    /**
     * 启动特定的数据收集器
     */
    public void startCollector(String collectorId) {
        DataCollector<?> collector = collectors.get(collectorId);
        if (collector != null) {
            if (collector.isAvailable()) {
                collector.startCollection();
                Log.d(TAG, "Started collector: " + collectorId);
            } else {
                Log.w(TAG, "Collector not available: " + collectorId);
            }
        } else {
            Log.w(TAG, "Collector not found: " + collectorId);
        }
    }
    
    /**
     * 停止特定的数据收集器
     */
    public void stopCollector(String collectorId) {
        DataCollector<?> collector = collectors.get(collectorId);
        if (collector != null) {
            collector.stopCollection();
            Log.d(TAG, "Stopped collector: " + collectorId);
        } else {
            Log.w(TAG, "Collector not found: " + collectorId);
        }
    }
    
    /**
     * 从所有可用的收集器并发收集数据，阻塞直到全部完成或超时（30 秒）。
     */
    public JSONObject collectAllData() {
        JSONObject allData = new JSONObject();
        CountDownLatch latch = new CountDownLatch(collectors.size());

        for (Map.Entry<String, DataCollector<?>> entry : collectors.entrySet()) {
            String collectorId = entry.getKey();
            DataCollector<?> collector = entry.getValue();

            executorService.submit(() -> {
                try {
                    Object data = collector.collectData();
                    if (data != null) {
                        synchronized (allData) {
                            allData.put(collectorId, data);
                        }
                        if (callback != null) {
                            callback.onDataCollected(collectorId, data);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error collecting data from " + collectorId, e);
                    if (callback != null) {
                        callback.onCollectionError(collectorId, e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "collectAllData interrupted before all collectors finished");
            Thread.currentThread().interrupt();
        }

        return allData;
    }
    
    /**
     * 从特定收集器收集数据
     */
    public Object collectData(String collectorId) {
        DataCollector<?> collector = collectors.get(collectorId);
        if (collector != null) {
            return collector.collectData();
        }
        Log.w(TAG, "Collector not found: " + collectorId);
        return null;
    }

    /**
     * 按权重分级采集：仅运行匹配指定 weight 的采集器。
     * 用于分级轮询——轻量采集器高频调用，重量采集器低频调用。
     */
    public JSONObject collectByWeight(BaseDataCollector.CollectionWeight weight) {
        JSONObject result = new JSONObject();
        List<Map.Entry<String, DataCollector<?>>> targets = new ArrayList<>();

        for (Map.Entry<String, DataCollector<?>> entry : collectors.entrySet()) {
            DataCollector<?> c = entry.getValue();
            if (c instanceof BaseDataCollector) {
                if (((BaseDataCollector<?>) c).getWeight() == weight) {
                    targets.add(entry);
                }
            } else if (weight == BaseDataCollector.CollectionWeight.HEAVY) {
                // 非 BaseDataCollector 的实现默认归入重量级
                targets.add(entry);
            }
        }

        if (targets.isEmpty()) return result;

        CountDownLatch latch = new CountDownLatch(targets.size());
        for (Map.Entry<String, DataCollector<?>> entry : targets) {
            String collectorId = entry.getKey();
            DataCollector<?> collector = entry.getValue();
            executorService.submit(() -> {
                try {
                    Object data = collector.collectData();
                    if (data != null) {
                        synchronized (result) {
                            result.put(collectorId, data);
                        }
                        if (callback != null) callback.onDataCollected(collectorId, data);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error collecting data from " + collectorId, e);
                    if (callback != null) callback.onCollectionError(collectorId, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "collectByWeight interrupted");
            Thread.currentThread().interrupt();
        }
        return result;
    }
    
    /**
     * 获取所有收集器的状态信息
     */
    public JSONObject getCollectorsStatus() {
        JSONObject status = new JSONObject();
        
        try {
            status.put("total_collectors", collectors.size());
            status.put("manager_started", isStarted);
            
            JSONObject collectorsInfo = new JSONObject();
            for (Map.Entry<String, DataCollector<?>> entry : collectors.entrySet()) {
                String id = entry.getKey();
                DataCollector<?> collector = entry.getValue();
                
                JSONObject collectorStatus = new JSONObject();
                collectorStatus.put("available", collector.isAvailable());
                collectorStatus.put("last_collection_time", collector.getLastCollectionTime());
                collectorStatus.put("configuration", collector.getConfiguration());
                
                collectorsInfo.put(id, collectorStatus);
            }
            status.put("collectors", collectorsInfo);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error building status", e);
        }
        
        return status;
    }
    
    /**
     * 设置数据收集回调
     */
    public void setCallback(DataCollectionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 批量设置收集器配置
     */
    public void setCollectorConfiguration(String collectorId, JSONObject config) {
        DataCollector<?> collector = collectors.get(collectorId);
        if (collector != null) {
            collector.setConfiguration(config);
            Log.d(TAG, "Updated configuration for collector: " + collectorId);
        } else {
            Log.w(TAG, "Collector not found: " + collectorId);
        }
    }
    
    /**
     * 清理资源
     */
    public void shutdown() {
        stopAllCollectors();
        collectors.clear();
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        Log.i(TAG, "DataCollectorManager shutdown");
    }
} 