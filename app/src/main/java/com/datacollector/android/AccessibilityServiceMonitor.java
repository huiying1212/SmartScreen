package com.datacollector.android;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 无障碍服务监控器
 * 用于监控服务状态、诊断问题并提供恢复建议
 */
public class AccessibilityServiceMonitor {
    
    private static final String TAG = "AccessibilityMonitor";
    private static final String SERVICE_NAME = "com.datacollector.android/.AccessibilityDataService";
    private static final long HEALTH_CHECK_INTERVAL = 30000; // 30秒检查一次
    private static final long SERVICE_TIMEOUT = 10000; // 10秒超时
    
    private Context context;
    private Timer healthCheckTimer;
    private Handler mainHandler;
    private AtomicBoolean isMonitoring = new AtomicBoolean(false);
    private ServiceStatusCallback statusCallback;
    
    // 服务状态回调接口
    public interface ServiceStatusCallback {
        void onServiceHealthy();
        void onServiceUnhealthy(String reason, String suggestion);
        void onServiceRecovered();
        void onServiceError(String error);
    }
    
    public AccessibilityServiceMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 设置状态回调
     */
    public void setStatusCallback(ServiceStatusCallback callback) {
        this.statusCallback = callback;
    }
    
    /**
     * 开始监控服务
     */
    public void startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            Log.i(TAG, "开始监控无障碍服务");
            
            healthCheckTimer = new Timer("AccessibilityServiceHealthCheck");
            healthCheckTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    performHealthCheck();
                }
            }, 0, HEALTH_CHECK_INTERVAL);
        }
    }
    
    /**
     * 停止监控服务
     */
    public void stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            Log.i(TAG, "停止监控无障碍服务");
            
            if (healthCheckTimer != null) {
                healthCheckTimer.cancel();
                healthCheckTimer = null;
            }
        }
    }
    
    /**
     * 执行健康检查
     */
    public void performHealthCheck() {
        try {
            ServiceHealthStatus status = checkServiceHealth();
            
            mainHandler.post(() -> {
                if (statusCallback != null) {
                    switch (status.status) {
                        case HEALTHY:
                            statusCallback.onServiceHealthy();
                            break;
                        case UNHEALTHY:
                            statusCallback.onServiceUnhealthy(status.reason, status.suggestion);
                            break;
                        case RECOVERED:
                            statusCallback.onServiceRecovered();
                            break;
                        case ERROR:
                            statusCallback.onServiceError(status.reason);
                            break;
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "健康检查时发生错误", e);
            mainHandler.post(() -> {
                if (statusCallback != null) {
                    statusCallback.onServiceError("健康检查异常: " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * 检查服务健康状态
     */
    private ServiceHealthStatus checkServiceHealth() {
        ServiceHealthStatus status = new ServiceHealthStatus();
        
        // 1. 检查服务是否在系统中启用
        if (!isAccessibilityServiceEnabled()) {
            status.status = HealthStatus.UNHEALTHY;
            status.reason = "无障碍服务未启用";
            status.suggestion = "请前往 设置 → 辅助功能 → 已安装的服务 → 数据收集器，开启服务";
            return status;
        }
        
        // 2. 检查服务实例是否存在
        if (!AccessibilityDataService.isServiceConnected()) {
            status.status = HealthStatus.UNHEALTHY;
            status.reason = "服务实例未连接";
            status.suggestion = "请重启应用或重新启用无障碍服务";
            return status;
        }
        
        // 3. 检查服务是否响应
        String serviceStatus = AccessibilityDataService.getServiceStatus();
        if (serviceStatus.contains("未连接")) {
            status.status = HealthStatus.UNHEALTHY;
            status.reason = "服务连接丢失";
            status.suggestion = "请检查系统设置，可能需要重新启用服务";
            return status;
        }
        
        // 4. 检查内存使用情况
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        if (usedMemory > maxMemory * 0.9) {
            status.status = HealthStatus.UNHEALTHY;
            status.reason = "内存使用过高 (" + (usedMemory * 100 / maxMemory) + "%)";
            status.suggestion = "建议重启应用以释放内存";
            return status;
        }
        
        // 5. 服务正常
        status.status = HealthStatus.HEALTHY;
        status.reason = "服务运行正常";
        status.suggestion = "";
        
        return status;
    }
    
    /**
     * 检查无障碍服务是否已启用
     */
    public boolean isAccessibilityServiceEnabled() {
        try {
            String settingValue = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            
            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(settingValue);
                
                while (splitter.hasNext()) {
                    String service = splitter.next();
                    if (service.equalsIgnoreCase(SERVICE_NAME)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查无障碍服务状态时出错", e);
        }
        
        return false;
    }
    
    /**
     * 打开无障碍服务设置页面
     */
    public void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "无法打开无障碍设置页面", e);
        }
    }
    
    /**
     * 获取服务诊断信息
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        
        info.append("=== 无障碍服务诊断信息 ===\n");
        info.append("服务名称: ").append(SERVICE_NAME).append("\n");
        info.append("系统启用状态: ").append(isAccessibilityServiceEnabled() ? "已启用" : "未启用").append("\n");
        info.append("服务连接状态: ").append(AccessibilityDataService.isServiceConnected() ? "已连接" : "未连接").append("\n");
        info.append("服务详细状态: ").append(AccessibilityDataService.getServiceStatus()).append("\n");
        
        // 内存信息
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        info.append("内存使用: ").append(usedMemory / 1024 / 1024).append("MB / ")
            .append(maxMemory / 1024 / 1024).append("MB (")
            .append(usedMemory * 100 / maxMemory).append("%)\n");
        
        // 系统信息
        info.append("Android版本: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        info.append("API级别: ").append(android.os.Build.VERSION.SDK_INT).append("\n");
        
        return info.toString();
    }
    
    /**
     * 尝试自动恢复服务
     */
    public void attemptServiceRecovery() {
        Log.i(TAG, "尝试自动恢复无障碍服务");
        
        new Thread(() -> {
            try {
                // 方法1: 强制垃圾回收
                System.gc();
                Thread.sleep(1000);
                
                // 方法2: 检查并等待服务恢复
                for (int i = 0; i < 5; i++) {
                    if (AccessibilityDataService.isServiceConnected()) {
                        mainHandler.post(() -> {
                            if (statusCallback != null) {
                                statusCallback.onServiceRecovered();
                            }
                        });
                        return;
                    }
                    Thread.sleep(2000);
                }
                
                // 恢复失败，需要用户手动操作
                mainHandler.post(() -> {
                    if (statusCallback != null) {
                        statusCallback.onServiceUnhealthy(
                            "自动恢复失败", 
                            "请手动重启应用或重新启用无障碍服务"
                        );
                    }
                });
                
            } catch (InterruptedException e) {
                Log.w(TAG, "服务恢复被中断", e);
            }
        }).start();
    }
    
    /**
     * 服务健康状态类
     */
    private static class ServiceHealthStatus {
        HealthStatus status;
        String reason;
        String suggestion;
    }
    
    /**
     * 健康状态枚举
     */
    private enum HealthStatus {
        HEALTHY,    // 健康
        UNHEALTHY,  // 不健康
        RECOVERED,  // 已恢复
        ERROR       // 错误
    }
} 