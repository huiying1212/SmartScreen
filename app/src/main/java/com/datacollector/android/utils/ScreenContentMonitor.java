package com.datacollector.android.utils;

import android.content.Context;
import android.util.Log;
import com.datacollector.android.services.AccessibilityDataService;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 屏幕内容收集监控工具
 * 用于诊断和监控屏幕信息收集的状态和问题
 */
public class ScreenContentMonitor {
    
    private static final String TAG = "ScreenContentMonitor";
    
    private Context context;
    private long lastAccessibilityEventTime = 0;
    private long lastOcrProcessTime = 0;
    private int accessibilityEventCount = 0;
    private int ocrSuccessCount = 0;
    private int ocrFailureCount = 0;
    private String lastError = null;
    
    public ScreenContentMonitor(Context context) {
        this.context = context;
    }
    
    /**
     * 记录无障碍事件
     */
    public void recordAccessibilityEvent() {
        lastAccessibilityEventTime = System.currentTimeMillis();
        accessibilityEventCount++;
    }
    
    /**
     * 记录OCR成功
     */
    public void recordOcrSuccess() {
        lastOcrProcessTime = System.currentTimeMillis();
        ocrSuccessCount++;
    }
    
    /**
     * 记录OCR失败
     */
    public void recordOcrFailure(String error) {
        lastOcrProcessTime = System.currentTimeMillis();
        ocrFailureCount++;
        lastError = error;
    }
    
    /**
     * 获取诊断报告
     */
    public JSONObject getDiagnosticReport() {
        JSONObject report = new JSONObject();
        long currentTime = System.currentTimeMillis();
        
        try {
            // 基本状态信息
            report.put("accessibility_service_connected", AccessibilityDataService.isServiceConnected());
            report.put("accessibility_service_status", AccessibilityDataService.getServiceStatus());
            
            // 事件统计
            report.put("accessibility_event_count", accessibilityEventCount);
            report.put("last_accessibility_event_time", lastAccessibilityEventTime);
            report.put("time_since_last_event_ms", currentTime - lastAccessibilityEventTime);
            
            // OCR统计
            report.put("ocr_success_count", ocrSuccessCount);
            report.put("ocr_failure_count", ocrFailureCount);
            report.put("ocr_success_rate", calculateOcrSuccessRate());
            report.put("last_ocr_process_time", lastOcrProcessTime);
            report.put("time_since_last_ocr_ms", currentTime - lastOcrProcessTime);
            
            // 错误信息
            report.put("last_error", lastError);
            
            // 系统资源状态
            addSystemResourceInfo(report);
            
            // 问题诊断
            addDiagnosticSuggestions(report);
            
        } catch (JSONException e) {
            Log.e(TAG, "创建诊断报告时出错", e);
        }
        
        return report;
    }
    
    /**
     * 计算OCR成功率
     */
    private double calculateOcrSuccessRate() {
        int total = ocrSuccessCount + ocrFailureCount;
        if (total == 0) return 0.0;
        return (double) ocrSuccessCount / total * 100.0;
    }
    
    /**
     * 添加系统资源信息
     */
    private void addSystemResourceInfo(JSONObject report) throws JSONException {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        JSONObject memoryInfo = new JSONObject();
        memoryInfo.put("used_memory_mb", usedMemory / 1024 / 1024);
        memoryInfo.put("total_memory_mb", totalMemory / 1024 / 1024);
        memoryInfo.put("max_memory_mb", maxMemory / 1024 / 1024);
        memoryInfo.put("memory_usage_percent", (double) usedMemory / maxMemory * 100);
        
        report.put("memory_info", memoryInfo);
    }
    
    /**
     * 添加诊断建议
     */
    private void addDiagnosticSuggestions(JSONObject report) throws JSONException {
        JSONObject suggestions = new JSONObject();
        long currentTime = System.currentTimeMillis();
        
        // 检查无障碍服务状态
        if (!AccessibilityDataService.isServiceConnected()) {
            suggestions.put("accessibility_service", "无障碍服务未连接，请在设置中启用");
        }
        
        // 检查事件频率
        if (currentTime - lastAccessibilityEventTime > 60000) { // 超过1分钟没有事件
            suggestions.put("accessibility_events", "长时间未接收到无障碍事件，可能服务异常或权限不足");
        }
        
        // 检查OCR性能
        if (ocrFailureCount > ocrSuccessCount && (ocrSuccessCount + ocrFailureCount) > 5) {
            suggestions.put("ocr_performance", "OCR失败率过高，建议检查图像质量或降低处理频率");
        }
        
        // 检查内存使用
        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100;
        if (memoryUsage > 85) {
            suggestions.put("memory_usage", "内存使用过高，建议清理缓存或减少处理频率");
        }
        
        report.put("suggestions", suggestions);
    }
    
    /**
     * 重置统计数据
     */
    public void resetStatistics() {
        accessibilityEventCount = 0;
        ocrSuccessCount = 0;
        ocrFailureCount = 0;
        lastError = null;
        Log.i(TAG, "统计数据已重置");
    }
    
    /**
     * 检查服务健康状态
     */
    public boolean isHealthy() {
        long currentTime = System.currentTimeMillis();
        
        // 检查无障碍服务连接
        if (!AccessibilityDataService.isServiceConnected()) {
            Log.w(TAG, "健康检查失败: 无障碍服务未连接");
            return false;
        }
        
        // 检查最近是否有事件
        if (currentTime - lastAccessibilityEventTime > 300000) { // 5分钟
            Log.w(TAG, "健康检查失败: 长时间未接收到无障碍事件");
            return false;
        }
        
        // 检查内存使用
        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100;
        if (memoryUsage > 90) {
            Log.w(TAG, "健康检查警告: 内存使用过高(" + String.format("%.1f%%", memoryUsage) + ")");
            return false;
        }
        
        return true;
    }
} 