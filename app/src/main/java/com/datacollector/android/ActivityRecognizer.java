package com.datacollector.android;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动识别器
 * 基于加速度计和陀螺仪数据识别用户当前活动状态
 * 支持识别：静止、走路、跑步、骑车、其他活动
 */
public class ActivityRecognizer {
    
    private static final String TAG = "ActivityRecognizer";
    
    // 活动类型常量
    public static final String ACTIVITY_STILL = "still";
    public static final String ACTIVITY_WALKING = "walking";
    public static final String ACTIVITY_RUNNING = "running";
    public static final String ACTIVITY_CYCLING = "cycling";
    public static final String ACTIVITY_OTHERS = "others";
    
    // 数据窗口参数
    private static final int WINDOW_SIZE = 50; // 窗口大小
    private static final int SAMPLE_RATE = 20; // 采样率 (Hz)
    
    // 传感器数据缓存
    private List<Float> accelerometerX = new ArrayList<>();
    private List<Float> accelerometerY = new ArrayList<>();
    private List<Float> accelerometerZ = new ArrayList<>();
    private List<Float> gyroscopeX = new ArrayList<>();
    private List<Float> gyroscopeY = new ArrayList<>();
    private List<Float> gyroscopeZ = new ArrayList<>();
    
    // 当前识别的活动
    private String currentActivity = ACTIVITY_STILL;
    private float confidence = 0.0f;
    
    // 上下文
    private Context context;
    
    public ActivityRecognizer(Context context) {
        this.context = context;
    }
    
    /**
     * 处理传感器数据
     */
    public void processSensorData(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            processAccelerometerData(event.values);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            processGyroscopeData(event.values);
        }
        
        // 当数据窗口满时进行活动识别
        if (accelerometerX.size() >= WINDOW_SIZE) {
            recognizeActivity();
            clearOldData();
        }
    }
    
    /**
     * 处理加速度计数据
     */
    private void processAccelerometerData(float[] values) {
        accelerometerX.add(values[0]);
        accelerometerY.add(values[1]);
        accelerometerZ.add(values[2]);
        
        // 限制缓存大小
        if (accelerometerX.size() > WINDOW_SIZE * 2) {
            accelerometerX.remove(0);
            accelerometerY.remove(0);
            accelerometerZ.remove(0);
        }
    }
    
    /**
     * 处理陀螺仪数据
     */
    private void processGyroscopeData(float[] values) {
        gyroscopeX.add(values[0]);
        gyroscopeY.add(values[1]);
        gyroscopeZ.add(values[2]);
        
        // 限制缓存大小
        if (gyroscopeX.size() > WINDOW_SIZE * 2) {
            gyroscopeX.remove(0);
            gyroscopeY.remove(0);
            gyroscopeZ.remove(0);
        }
    }
    
    /**
     * 活动识别主函数
     */
    private void recognizeActivity() {
        if (accelerometerX.size() < WINDOW_SIZE) {
            return;
        }
        
        // 计算特征
        ActivityFeatures features = extractFeatures();
        
        // 基于规则的活动分类
        String recognizedActivity = classifyActivity(features);
        
        // 更新当前活动
        if (!recognizedActivity.equals(currentActivity)) {
            Log.d(TAG, "Activity changed from " + currentActivity + " to " + recognizedActivity);
            currentActivity = recognizedActivity;
        }
    }
    
    /**
     * 提取活动特征
     */
    private ActivityFeatures extractFeatures() {
        ActivityFeatures features = new ActivityFeatures();
        
        // 计算加速度计特征
        features.accMean = calculateMean(accelerometerX, accelerometerY, accelerometerZ);
        features.accStd = calculateStandardDeviation(accelerometerX, accelerometerY, accelerometerZ);
        features.accMax = calculateMax(accelerometerX, accelerometerY, accelerometerZ);
        features.accMin = calculateMin(accelerometerX, accelerometerY, accelerometerZ);
        
        // 计算陀螺仪特征（如果有数据）
        if (gyroscopeX.size() >= WINDOW_SIZE) {
            features.gyroMean = calculateMean(gyroscopeX, gyroscopeY, gyroscopeZ);
            features.gyroStd = calculateStandardDeviation(gyroscopeX, gyroscopeY, gyroscopeZ);
        }
        
        // 计算总加速度变化
        features.totalAcceleration = calculateTotalAcceleration();
        
        // 计算步频（用于区分走路和跑步）
        features.stepFrequency = calculateStepFrequency();
        
        // 计算方向变化（用于检测骑车等活动）
        features.orientationChange = calculateOrientationChange();
        
        return features;
    }
    
    /**
     * 活动分类
     */
    private String classifyActivity(ActivityFeatures features) {
        // 基于规则的简单分类器
        
        // 静止状态：加速度变化很小
        if (features.accStd < 0.5 && features.totalAcceleration < 2.0) {
            confidence = 0.9f;
            return ACTIVITY_STILL;
        }
        
        // 走路：中等加速度变化，低步频
        if (features.accStd > 1.0 && features.accStd < 4.0 && 
            features.stepFrequency > 0.5 && features.stepFrequency < 2.5) {
            confidence = 0.8f;
            return ACTIVITY_WALKING;
        }
        
        // 跑步：高加速度变化，高步频
        if (features.accStd > 3.0 && features.stepFrequency > 2.0) {
            confidence = 0.85f;
            return ACTIVITY_RUNNING;
        }
        
        // 骑车：中等加速度变化，低方向变化
        if (features.accStd > 1.0 && features.accStd < 3.0 && 
            features.orientationChange < 0.3 && features.gyroStd > 0.5) {
            confidence = 0.7f;
            return ACTIVITY_CYCLING;
        }
        
        // 其他活动
        confidence = 0.6f;
        return ACTIVITY_OTHERS;
    }
    
    /**
     * 计算平均值
     */
    private float calculateMean(List<Float> x, List<Float> y, List<Float> z) {
        float sum = 0;
        int size = Math.min(Math.min(x.size(), y.size()), z.size());
        
        for (int i = 0; i < size; i++) {
            sum += Math.sqrt(x.get(i) * x.get(i) + y.get(i) * y.get(i) + z.get(i) * z.get(i));
        }
        
        return sum / size;
    }
    
    /**
     * 计算标准差
     */
    private float calculateStandardDeviation(List<Float> x, List<Float> y, List<Float> z) {
        float mean = calculateMean(x, y, z);
        float sum = 0;
        int size = Math.min(Math.min(x.size(), y.size()), z.size());
        
        for (int i = 0; i < size; i++) {
            float magnitude = (float) Math.sqrt(x.get(i) * x.get(i) + y.get(i) * y.get(i) + z.get(i) * z.get(i));
            sum += (magnitude - mean) * (magnitude - mean);
        }
        
        return (float) Math.sqrt(sum / size);
    }
    
    /**
     * 计算最大值
     */
    private float calculateMax(List<Float> x, List<Float> y, List<Float> z) {
        float max = 0;
        int size = Math.min(Math.min(x.size(), y.size()), z.size());
        
        for (int i = 0; i < size; i++) {
            float magnitude = (float) Math.sqrt(x.get(i) * x.get(i) + y.get(i) * y.get(i) + z.get(i) * z.get(i));
            if (magnitude > max) {
                max = magnitude;
            }
        }
        
        return max;
    }
    
    /**
     * 计算最小值
     */
    private float calculateMin(List<Float> x, List<Float> y, List<Float> z) {
        float min = Float.MAX_VALUE;
        int size = Math.min(Math.min(x.size(), y.size()), z.size());
        
        for (int i = 0; i < size; i++) {
            float magnitude = (float) Math.sqrt(x.get(i) * x.get(i) + y.get(i) * y.get(i) + z.get(i) * z.get(i));
            if (magnitude < min) {
                min = magnitude;
            }
        }
        
        return min;
    }
    
    /**
     * 计算总加速度变化
     */
    private float calculateTotalAcceleration() {
        if (accelerometerX.size() < 2) return 0;
        
        float totalChange = 0;
        for (int i = 1; i < accelerometerX.size(); i++) {
            float prev = (float) Math.sqrt(
                accelerometerX.get(i-1) * accelerometerX.get(i-1) +
                accelerometerY.get(i-1) * accelerometerY.get(i-1) +
                accelerometerZ.get(i-1) * accelerometerZ.get(i-1)
            );
            float curr = (float) Math.sqrt(
                accelerometerX.get(i) * accelerometerX.get(i) +
                accelerometerY.get(i) * accelerometerY.get(i) +
                accelerometerZ.get(i) * accelerometerZ.get(i)
            );
            totalChange += Math.abs(curr - prev);
        }
        
        return totalChange / (accelerometerX.size() - 1);
    }
    
    /**
     * 计算步频
     */
    private float calculateStepFrequency() {
        if (accelerometerZ.size() < WINDOW_SIZE) return 0;
        
        // 简单的峰值检测算法来估计步频
        int peakCount = 0;
        float threshold = calculateMean(accelerometerX, accelerometerY, accelerometerZ) + 
                         calculateStandardDeviation(accelerometerX, accelerometerY, accelerometerZ) * 0.5f;
        
        boolean inPeak = false;
        for (int i = 1; i < accelerometerZ.size() - 1; i++) {
            float magnitude = (float) Math.sqrt(
                accelerometerX.get(i) * accelerometerX.get(i) +
                accelerometerY.get(i) * accelerometerY.get(i) +
                accelerometerZ.get(i) * accelerometerZ.get(i)
            );
            
            if (magnitude > threshold && !inPeak) {
                peakCount++;
                inPeak = true;
            } else if (magnitude < threshold) {
                inPeak = false;
            }
        }
        
        // 转换为Hz（每秒步数）
        float timeWindow = WINDOW_SIZE / (float) SAMPLE_RATE; // 秒
        return peakCount / timeWindow;
    }
    
    /**
     * 计算方向变化
     */
    private float calculateOrientationChange() {
        if (gyroscopeX.size() < WINDOW_SIZE) return 0;
        
        float totalChange = 0;
        for (int i = 1; i < gyroscopeX.size(); i++) {
            float prev = (float) Math.sqrt(
                gyroscopeX.get(i-1) * gyroscopeX.get(i-1) +
                gyroscopeY.get(i-1) * gyroscopeY.get(i-1) +
                gyroscopeZ.get(i-1) * gyroscopeZ.get(i-1)
            );
            float curr = (float) Math.sqrt(
                gyroscopeX.get(i) * gyroscopeX.get(i) +
                gyroscopeY.get(i) * gyroscopeY.get(i) +
                gyroscopeZ.get(i) * gyroscopeZ.get(i)
            );
            totalChange += Math.abs(curr - prev);
        }
        
        return totalChange / (gyroscopeX.size() - 1);
    }
    
    /**
     * 清理旧数据
     */
    private void clearOldData() {
        // 保留一半的数据以保持连续性
        int keepSize = WINDOW_SIZE / 2;
        
        while (accelerometerX.size() > keepSize) {
            accelerometerX.remove(0);
            accelerometerY.remove(0);
            accelerometerZ.remove(0);
        }
        
        while (gyroscopeX.size() > keepSize) {
            gyroscopeX.remove(0);
            gyroscopeY.remove(0);
            gyroscopeZ.remove(0);
        }
    }
    
    /**
     * 获取当前活动
     */
    public String getCurrentActivity() {
        return currentActivity;
    }
    
    /**
     * 获取识别置信度
     */
    public float getConfidence() {
        return confidence;
    }
    
    /**
     * 获取详细的活动信息（JSON格式）
     */
    public JSONObject getActivityInfo() {
        try {
            JSONObject activityInfo = new JSONObject();
            activityInfo.put("activity", currentActivity);
            activityInfo.put("confidence", confidence);
            activityInfo.put("timestamp", System.currentTimeMillis());
            return activityInfo;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 活动特征数据类
     */
    private static class ActivityFeatures {
        float accMean;
        float accStd;
        float accMax;
        float accMin;
        float gyroMean;
        float gyroStd;
        float totalAcceleration;
        float stepFrequency;
        float orientationChange;
    }
} 