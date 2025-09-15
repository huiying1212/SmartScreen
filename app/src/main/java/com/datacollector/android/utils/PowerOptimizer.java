package com.datacollector.android.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

/**
 * 电量优化器
 * 根据设备状态动态调整OCR处理频率和策略
 */
public class PowerOptimizer {
    
    private static final String TAG = "PowerOptimizer";
    
    // 电量阈值配置
    private static final int LOW_BATTERY_THRESHOLD = 20;      // 低电量阈值20%
    private static final int CRITICAL_BATTERY_THRESHOLD = 10; // 极低电量阈值10%
    
    // 内存阈值配置
    private static final long LOW_MEMORY_THRESHOLD_MB = 200;     // 低内存阈值200MB
    private static final long CRITICAL_MEMORY_THRESHOLD_MB = 100; // 极低内存阈值100MB
    
    private Context context;
    private PowerManager powerManager;
    private ActivityManager activityManager;
    
    // 当前优化级别
    private OptimizationLevel currentLevel = OptimizationLevel.NORMAL;
    
    public enum OptimizationLevel {
        NORMAL,      // 正常模式
        POWER_SAVE,  // 省电模式
        AGGRESSIVE   // 激进省电模式
    }
    
    public PowerOptimizer(Context context) {
        this.context = context;
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }
    
    /**
     * 获取当前电池状态
     */
    public BatteryStatus getBatteryStatus() {
        BatteryStatus status = new BatteryStatus();
        
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, filter);
            
            if (batteryStatus != null) {
                // 电量百分比
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                status.batteryPercent = (int) ((level / (float) scale) * 100);
                
                // 充电状态
                int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                status.isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                                  plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                                  plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
                
                // 电池温度
                int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                status.temperatureCelsius = temperature / 10.0f; // 温度单位是0.1度
                
                // 电池健康状态
                int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                status.isHealthy = health == BatteryManager.BATTERY_HEALTH_GOOD;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting battery status", e);
        }
        
        return status;
    }
    
    /**
     * 获取内存状态
     */
    public MemoryStatus getMemoryStatus() {
        MemoryStatus status = new MemoryStatus();
        
        try {
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memInfo);
            
            status.availableMemoryMB = memInfo.availMem / (1024 * 1024);
            status.totalMemoryMB = memInfo.totalMem / (1024 * 1024);
            status.usedMemoryMB = status.totalMemoryMB - status.availableMemoryMB;
            status.memoryUsagePercent = (int) ((status.usedMemoryMB / (float) status.totalMemoryMB) * 100);
            status.isLowMemory = memInfo.lowMemory;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting memory status", e);
        }
        
        return status;
    }
    
    /**
     * 检查是否启用省电模式
     */
    public boolean isPowerSaveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return powerManager.isPowerSaveMode();
        }
        return false;
    }
    
    /**
     * 分析当前设备状态并返回推荐的优化级别
     */
    public OptimizationLevel analyzeOptimizationLevel() {
        BatteryStatus battery = getBatteryStatus();
        MemoryStatus memory = getMemoryStatus();
        boolean systemPowerSave = isPowerSaveMode();
        
        // 激进省电模式条件
        if (battery.batteryPercent <= CRITICAL_BATTERY_THRESHOLD ||
            memory.availableMemoryMB <= CRITICAL_MEMORY_THRESHOLD_MB ||
            (systemPowerSave && battery.batteryPercent <= LOW_BATTERY_THRESHOLD)) {
            currentLevel = OptimizationLevel.AGGRESSIVE;
            Log.i(TAG, "Switched to AGGRESSIVE power optimization");
            return currentLevel;
        }
        
        // 省电模式条件
        if (battery.batteryPercent <= LOW_BATTERY_THRESHOLD ||
            memory.availableMemoryMB <= LOW_MEMORY_THRESHOLD_MB ||
            systemPowerSave ||
            !battery.isCharging ||
            memory.isLowMemory) {
            currentLevel = OptimizationLevel.POWER_SAVE;
            Log.i(TAG, "Switched to POWER_SAVE optimization");
            return currentLevel;
        }
        
        // 正常模式
        currentLevel = OptimizationLevel.NORMAL;
        return currentLevel;
    }
    
    /**
     * 根据优化级别获取OCR配置
     */
    public OcrConfig getOptimizedOcrConfig() {
        OptimizationLevel level = analyzeOptimizationLevel();
        OcrConfig config = new OcrConfig();
        
        switch (level) {
            case NORMAL:
                config.ocrInterval = 2000;          // 2秒间隔
                config.screenshotQuality = 90;      // 90%质量
                config.maxBitmapSize = 1920 * 1080; // 全分辨率
                config.enableOcr = true;
                config.batchProcessing = false;
                break;
                
            case POWER_SAVE:
                config.ocrInterval = 5000;          // 5秒间隔
                config.screenshotQuality = 70;      // 70%质量
                config.maxBitmapSize = 1280 * 720;  // 降低分辨率
                config.enableOcr = true;
                config.batchProcessing = true;       // 启用批处理
                break;
                
            case AGGRESSIVE:
                config.ocrInterval = 10000;         // 10秒间隔
                config.screenshotQuality = 50;      // 50%质量
                config.maxBitmapSize = 854 * 480;   // 大幅降低分辨率
                config.enableOcr = false;           // 暂停OCR
                config.batchProcessing = true;
                break;
        }
        
        return config;
    }
    
    /**
     * 获取系统性能统计
     */
    public SystemPerformance getSystemPerformance() {
        SystemPerformance perf = new SystemPerformance();
        
        BatteryStatus battery = getBatteryStatus();
        MemoryStatus memory = getMemoryStatus();
        
        perf.batteryStatus = battery;
        perf.memoryStatus = memory;
        perf.optimizationLevel = currentLevel;
        perf.powerSaveMode = isPowerSaveMode();
        
        // CPU使用率（简化估算）
        try {
            perf.estimatedCpuUsage = estimateCpuUsage();
        } catch (Exception e) {
            perf.estimatedCpuUsage = 0;
        }
        
        return perf;
    }
    
    /**
     * 估算CPU使用率（简化方法）
     */
    private int estimateCpuUsage() {
        // 基于内存使用率和电池状态的简化估算
        MemoryStatus memory = getMemoryStatus();
        BatteryStatus battery = getBatteryStatus();
        
        int baseCpu = memory.memoryUsagePercent;
        
        // 如果电池温度过高，可能CPU使用率也高
        if (battery.temperatureCelsius > 40) {
            baseCpu += 20;
        }
        
        return Math.min(100, Math.max(0, baseCpu));
    }
    
    /**
     * 电池状态数据类
     */
    public static class BatteryStatus {
        public int batteryPercent = 100;
        public boolean isCharging = false;
        public float temperatureCelsius = 25.0f;
        public boolean isHealthy = true;
    }
    
    /**
     * 内存状态数据类
     */
    public static class MemoryStatus {
        public long availableMemoryMB = 0;
        public long totalMemoryMB = 0;
        public long usedMemoryMB = 0;
        public int memoryUsagePercent = 0;
        public boolean isLowMemory = false;
    }
    
    /**
     * OCR配置数据类
     */
    public static class OcrConfig {
        public int ocrInterval = 2000;           // OCR间隔时间(ms)
        public int screenshotQuality = 90;       // 截图质量(1-100)
        public int maxBitmapSize = 1920 * 1080;  // 最大Bitmap尺寸
        public boolean enableOcr = true;         // 是否启用OCR
        public boolean batchProcessing = false;  // 是否启用批处理
    }
    
    /**
     * 系统性能数据类
     */
    public static class SystemPerformance {
        public BatteryStatus batteryStatus;
        public MemoryStatus memoryStatus;
        public OptimizationLevel optimizationLevel;
        public boolean powerSaveMode;
        public int estimatedCpuUsage;
    }
} 