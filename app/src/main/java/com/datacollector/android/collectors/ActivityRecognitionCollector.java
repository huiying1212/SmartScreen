package com.datacollector.android.collectors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.datacollector.android.ActivityRecognizer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 活动识别数据收集器
 * 包装现有的ActivityRecognizer类，使其符合新的接口规范
 */
public class ActivityRecognitionCollector extends BaseDataCollector<JSONObject> implements SensorEventListener {
    
    private static final String COLLECTOR_ID = "activity_recognition";
    
    private SensorManager sensorManager;
    private ActivityRecognizer activityRecognizer;
    private Sensor accelerometer;
    private Sensor gyroscope;
    
    public ActivityRecognitionCollector(Context context) {
        super(context, COLLECTOR_ID);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        activityRecognizer = new ActivityRecognizer(context);
        
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }
    
    @Override
    protected void initializeDefaultConfiguration() {
        super.initializeDefaultConfiguration();
        try {
            configuration.put("sensor_delay", SensorManager.SENSOR_DELAY_NORMAL);
            configuration.put("use_accelerometer", true);
            configuration.put("use_gyroscope", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public boolean isAvailable() {
        if (!isEnabled()) {
            return false;
        }
        
        // 检查传感器管理器是否可用
        if (sensorManager == null) {
            return false;
        }
        
        // 至少需要加速度计
        return accelerometer != null;
    }
    
    @Override
    protected void doStartCollection() {
        if (!isAvailable()) {
            return;
        }
        
        int sensorDelay = configuration.optInt("sensor_delay", SensorManager.SENSOR_DELAY_NORMAL);
        boolean useAccelerometer = configuration.optBoolean("use_accelerometer", true);
        boolean useGyroscope = configuration.optBoolean("use_gyroscope", true);
        
        // 注册传感器监听器
        if (useAccelerometer && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, sensorDelay);
        }
        
        if (useGyroscope && gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, sensorDelay);
        }
    }
    
    @Override
    protected void doStopCollection() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
    
    @Override
    protected JSONObject doCollectData() {
        if (activityRecognizer == null) {
            return null;
        }
        
        // 获取活动识别结果
        JSONObject activityInfo = activityRecognizer.getActivityInfo();
        
        if (activityInfo != null) {
            try {
                // 添加收集器特定的信息
                activityInfo.put("collector_id", getCollectorId());
                activityInfo.put("data_collection_time", System.currentTimeMillis());
                
                // 添加传感器可用性信息
                JSONObject sensorStatus = new JSONObject();
                sensorStatus.put("accelerometer_available", accelerometer != null);
                sensorStatus.put("gyroscope_available", gyroscope != null);
                activityInfo.put("sensor_status", sensorStatus);
                
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        
        return activityInfo;
    }
    
    // SensorEventListener接口实现
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (activityRecognizer != null) {
            // 将传感器事件传递给ActivityRecognizer
            activityRecognizer.processSensorData(event);
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 传感器精度变化处理
        // 可以记录日志或更新配置
    }
    
    /**
     * 获取当前活动字符串（便捷方法）
     */
    public String getCurrentActivity() {
        return activityRecognizer != null ? activityRecognizer.getCurrentActivity() : "unknown";
    }
    
    /**
     * 获取识别置信度（便捷方法）
     */
    public float getConfidence() {
        return activityRecognizer != null ? activityRecognizer.getConfidence() : 0.0f;
    }
} 