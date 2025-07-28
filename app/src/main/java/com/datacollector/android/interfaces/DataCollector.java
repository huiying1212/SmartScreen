package com.datacollector.android.interfaces;

import org.json.JSONObject;

/**
 * 数据收集器接口
 * 定义所有数据收集器的基本行为和契约
 * 
 * @param <T> 收集的数据类型（通常是JSONObject或JSONArray）
 */
public interface DataCollector<T> {
    
    /**
     * 收集数据
     * @return 收集到的数据，如果收集失败或无可用数据则返回null
     */
    T collectData();
    
    /**
     * 开始数据收集
     * 启动相关的监听器、传感器或其他数据源
     */
    void startCollection();
    
    /**
     * 停止数据收集
     * 清理资源，注销监听器
     */
    void stopCollection();
    
    /**
     * 检查数据收集器是否可用
     * @return true 如果数据收集器可以正常工作，false 否则
     */
    boolean isAvailable();
    
    /**
     * 获取数据收集器的名称/标识
     * @return 数据收集器的唯一标识符
     */
    String getCollectorId();
    
    /**
     * 获取数据收集器的配置信息
     * @return 包含配置信息的JSONObject
     */
    JSONObject getConfiguration();
    
    /**
     * 设置数据收集器的配置
     * @param config 配置信息
     */
    void setConfiguration(JSONObject config);
    
    /**
     * 获取上次收集数据的时间戳
     * @return 时间戳（毫秒）
     */
    long getLastCollectionTime();
} 