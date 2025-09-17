package com.datacollector.android.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import com.datacollector.android.services.DataCollectionService;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Launcher稳定性管理器
 * 负责保持launcher应用的稳定运行，防止被系统杀死
 */
public class LauncherStabilityManager {
    
    private static final String TAG = "LauncherStabilityManager";
    private static final String PREFS_NAME = "launcher_stability_prefs";
    private static final String KEY_LAST_HEARTBEAT = "last_heartbeat";
    private static final String KEY_CRASH_COUNT = "crash_count";
    private static final String KEY_LAST_CRASH_TIME = "last_crash_time";
    
    // 心跳检测间隔（30秒）
    private static final long HEARTBEAT_INTERVAL = 30 * 1000;
    // 崩溃检测阈值（5分钟内重启超过3次认为是频繁崩溃）
    private static final long CRASH_DETECTION_WINDOW = 5 * 60 * 1000;
    private static final int MAX_CRASHES_IN_WINDOW = 3;
    
    private Context context;
    private SharedPreferences prefs;
    private Timer heartbeatTimer;
    private Handler mainHandler;
    private boolean isActive = false;
    
    public LauncherStabilityManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        checkForUnexpectedRestart();
    }
    
    /**
     * 启动稳定性监控
     */
    public void startStabilityMonitoring() {
        if (isActive) {
            Log.d(TAG, "Stability monitoring already active");
            return;
        }
        
        isActive = true;
        Log.i(TAG, "Starting launcher stability monitoring");
        
        // 提升进程优先级
        boostProcessPriority();
        
        // 启动心跳检测
        startHeartbeat();
        
        // 确保数据收集服务运行
        ensureDataCollectionServiceRunning();
        
        // 记录启动时间
        recordHeartbeat();
    }
    
    /**
     * 停止稳定性监控
     */
    public void stopStabilityMonitoring() {
        if (!isActive) {
            return;
        }
        
        isActive = false;
        Log.i(TAG, "Stopping launcher stability monitoring");
        
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }
    
    /**
     * 提升进程优先级
     */
    private void boostProcessPriority() {
        try {
            // 设置进程优先级为前台进程
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
            
            // 尝试设置进程为重要进程
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 通过启动前台服务来提升进程重要性
                Intent serviceIntent = new Intent(context, DataCollectionService.class);
                context.startForegroundService(serviceIntent);
            }
            
            Log.i(TAG, "Process priority boosted");
        } catch (Exception e) {
            Log.e(TAG, "Failed to boost process priority", e);
        }
    }
    
    /**
     * 启动心跳检测
     */
    private void startHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
        
        heartbeatTimer = new Timer("LauncherHeartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isActive) {
                    recordHeartbeat();
                    ensureDataCollectionServiceRunning();
                }
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL);
        
        Log.d(TAG, "Heartbeat timer started");
    }
    
    /**
     * 记录心跳
     */
    private void recordHeartbeat() {
        long currentTime = System.currentTimeMillis();
        prefs.edit().putLong(KEY_LAST_HEARTBEAT, currentTime).apply();
        Log.d(TAG, "Heartbeat recorded at " + currentTime);
    }
    
    /**
     * 确保数据收集服务正在运行
     */
    private void ensureDataCollectionServiceRunning() {
        try {
            if (!isServiceRunning(DataCollectionService.class)) {
                Log.w(TAG, "DataCollectionService not running, restarting...");
                Intent serviceIntent = new Intent(context, DataCollectionService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to ensure service running", e);
        }
    }
    
    /**
     * 检查服务是否正在运行
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        
        try {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking service status", e);
        }
        return false;
    }
    
    /**
     * 检查是否发生了意外重启
     */
    private void checkForUnexpectedRestart() {
        long lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0);
        long currentTime = System.currentTimeMillis();
        
        if (lastHeartbeat > 0) {
            long timeSinceLastHeartbeat = currentTime - lastHeartbeat;
            
            // 如果距离上次心跳超过2分钟，可能发生了意外重启
            if (timeSinceLastHeartbeat > 2 * 60 * 1000) {
                Log.w(TAG, "Detected potential unexpected restart. Time since last heartbeat: " + 
                      (timeSinceLastHeartbeat / 1000) + " seconds");
                
                recordCrash();
                
                // 检查是否频繁崩溃
                if (isFrequentlyCrashing()) {
                    Log.e(TAG, "Frequent crashes detected, taking recovery actions");
                    performRecoveryActions();
                }
            }
        }
    }
    
    /**
     * 记录崩溃事件
     */
    private void recordCrash() {
        long currentTime = System.currentTimeMillis();
        int crashCount = prefs.getInt(KEY_CRASH_COUNT, 0) + 1;
        
        prefs.edit()
             .putInt(KEY_CRASH_COUNT, crashCount)
             .putLong(KEY_LAST_CRASH_TIME, currentTime)
             .apply();
        
        Log.w(TAG, "Crash recorded. Total crashes: " + crashCount);
    }
    
    /**
     * 检查是否频繁崩溃
     */
    private boolean isFrequentlyCrashing() {
        long lastCrashTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0);
        int crashCount = prefs.getInt(KEY_CRASH_COUNT, 0);
        long currentTime = System.currentTimeMillis();
        
        // 如果崩溃时间超过检测窗口，重置计数
        if (currentTime - lastCrashTime > CRASH_DETECTION_WINDOW) {
            prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply();
            return false;
        }
        
        return crashCount >= MAX_CRASHES_IN_WINDOW;
    }
    
    /**
     * 执行恢复操作
     */
    private void performRecoveryActions() {
        Log.i(TAG, "Performing recovery actions for frequent crashes");
        
        // 清理可能导致问题的缓存数据
        clearProblemCache();
        
        // 重置崩溃计数
        prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply();
        
        // 延迟重新检查电池优化设置
        mainHandler.postDelayed(() -> {
            if (context instanceof Activity) {
                BatteryOptimizationHelper.checkAndRequestBatteryOptimization((Activity) context);
            }
        }, 5000);
    }
    
    /**
     * 清理可能导致问题的缓存
     */
    private void clearProblemCache() {
        try {
            // 清理SharedPreferences中的临时数据
            SharedPreferences tempPrefs = context.getSharedPreferences("temp_data", Context.MODE_PRIVATE);
            tempPrefs.edit().clear().apply();
            
            // 清理可能过大的数据文件
            clearOversizedDataFiles();
            
            Log.i(TAG, "Problem cache cleared");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear problem cache", e);
        }
    }
    
    /**
     * 清理过大的数据文件
     */
    private void clearOversizedDataFiles() {
        try {
            java.io.File dataDir = context.getExternalFilesDir(null);
            if (dataDir != null && dataDir.exists()) {
                java.io.File[] files = dataDir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        // 删除超过10MB的单个文件
                        if (file.length() > 10 * 1024 * 1024) {
                            if (file.delete()) {
                                Log.w(TAG, "删除过大文件: " + file.getName() + " (大小: " + file.length() + " bytes)");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "清理过大文件时出错", e);
        }
    }
    
    /**
     * 获取稳定性统计信息
     */
    public String getStabilityStats() {
        long lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0);
        int crashCount = prefs.getInt(KEY_CRASH_COUNT, 0);
        long lastCrashTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0);
        
        StringBuilder stats = new StringBuilder();
        stats.append("稳定性统计:\n");
        stats.append("上次心跳: ").append(lastHeartbeat > 0 ? new java.util.Date(lastHeartbeat).toString() : "无").append("\n");
        stats.append("崩溃次数: ").append(crashCount).append("\n");
        stats.append("上次崩溃: ").append(lastCrashTime > 0 ? new java.util.Date(lastCrashTime).toString() : "无").append("\n");
        stats.append("监控状态: ").append(isActive ? "活跃" : "非活跃");
        
        return stats.toString();
    }
} 