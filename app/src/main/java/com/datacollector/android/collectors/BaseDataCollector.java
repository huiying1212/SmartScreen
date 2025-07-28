package com.datacollector.android.collectors;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.interfaces.DataCollector;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 数据收集器抽象基类
 * 提供通用的实现和模板方法
 * 
 * @param <T> 收集的数据类型
 */
public abstract class BaseDataCollector<T> implements DataCollector<T> {
    
    protected final String TAG;
    protected final Context context;
    protected final String collectorId;
    
    protected JSONObject configuration;
    protected long lastCollectionTime = 0;
    protected boolean isCollecting = false;
    
    public BaseDataCollector(Context context, String collectorId) {
        this.context = context;
        this.collectorId = collectorId;
        this.TAG = this.getClass().getSimpleName();
        this.configuration = new JSONObject();
        
        // 设置默认配置
        initializeDefaultConfiguration();
    }
    
    /**
     * 初始化默认配置
     * 子类可以重写此方法来设置特定的默认配置
     */
    protected void initializeDefaultConfiguration() {
        try {
            configuration.put("enabled", true);
            configuration.put("collection_interval", 30000); // 默认30秒
            configuration.put("auto_start", true);
        } catch (JSONException e) {
            Log.e(TAG, "Error initializing default configuration", e);
        }
    }
    
    @Override
    public final T collectData() {
        if (!isAvailable()) {
            Log.w(TAG, "Data collector " + collectorId + " is not available");
            return null;
        }
        
        try {
            T data = doCollectData();
            lastCollectionTime = System.currentTimeMillis();
            
            if (data != null) {
                Log.d(TAG, "Successfully collected data from " + collectorId);
            } else {
                Log.w(TAG, "No data collected from " + collectorId);
            }
            
            return data;
        } catch (Exception e) {
            Log.e(TAG, "Error collecting data from " + collectorId, e);
            return null;
        }
    }
    
    /**
     * 实际的数据收集实现
     * 子类必须实现此方法
     */
    protected abstract T doCollectData();
    
    @Override
    public final void startCollection() {
        if (isCollecting) {
            Log.w(TAG, "Data collector " + collectorId + " is already collecting");
            return;
        }
        
        if (!isAvailable()) {
            Log.w(TAG, "Cannot start collection for " + collectorId + " - not available");
            return;
        }
        
        try {
            doStartCollection();
            isCollecting = true;
            Log.i(TAG, "Started data collection for " + collectorId);
        } catch (Exception e) {
            Log.e(TAG, "Error starting collection for " + collectorId, e);
        }
    }
    
    /**
     * 实际的启动收集实现
     * 子类可以重写此方法
     */
    protected void doStartCollection() {
        // 默认空实现
    }
    
    @Override
    public final void stopCollection() {
        if (!isCollecting) {
            Log.w(TAG, "Data collector " + collectorId + " is not collecting");
            return;
        }
        
        try {
            doStopCollection();
            isCollecting = false;
            Log.i(TAG, "Stopped data collection for " + collectorId);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping collection for " + collectorId, e);
        }
    }
    
    /**
     * 实际的停止收集实现
     * 子类可以重写此方法
     */
    protected void doStopCollection() {
        // 默认空实现
    }
    
    @Override
    public String getCollectorId() {
        return collectorId;
    }
    
    @Override
    public JSONObject getConfiguration() {
        try {
            return new JSONObject(configuration.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error copying configuration", e);
            return new JSONObject();
        }
    }
    
    @Override
    public void setConfiguration(JSONObject config) {
        if (config != null) {
            try {
                this.configuration = new JSONObject(config.toString());
                onConfigurationChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error setting configuration", e);
            }
        }
    }
    
    /**
     * 配置变更时的回调
     * 子类可以重写此方法来响应配置变更
     */
    protected void onConfigurationChanged() {
        // 默认空实现
    }
    
    @Override
    public long getLastCollectionTime() {
        return lastCollectionTime;
    }
    
    /**
     * 检查配置中的启用状态
     */
    protected boolean isEnabled() {
        try {
            return configuration.optBoolean("enabled", true);
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 获取配置中的收集间隔
     */
    protected long getCollectionInterval() {
        try {
            return configuration.optLong("collection_interval", 30000);
        } catch (Exception e) {
            return 30000;
        }
    }
    
    /**
     * 检查是否正在收集数据
     */
    public boolean isCollecting() {
        return isCollecting;
    }
} 