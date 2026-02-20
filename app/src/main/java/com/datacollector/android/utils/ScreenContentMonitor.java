package com.datacollector.android.utils;

import android.content.Context;
import android.util.Log;
import com.datacollector.android.services.AccessibilityDataService;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 屏幕内容收集监控工具
 * 用于诊断和监控屏幕信息收集的状态和问题（仅无障碍文本）
 */
public class ScreenContentMonitor {

    private static final String TAG = "ScreenContentMonitor";

    private Context context;
    private long lastAccessibilityEventTime = 0;
    private int accessibilityEventCount = 0;
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
     * 获取诊断报告
     */
    public JSONObject getDiagnosticReport() {
        JSONObject report = new JSONObject();
        long currentTime = System.currentTimeMillis();

        try {
            report.put("accessibility_service_connected", AccessibilityDataService.isServiceConnected());
            report.put("accessibility_service_status", AccessibilityDataService.getServiceStatus());
            report.put("accessibility_event_count", accessibilityEventCount);
            report.put("last_accessibility_event_time", lastAccessibilityEventTime);
            report.put("time_since_last_event_ms", currentTime - lastAccessibilityEventTime);
            report.put("last_error", lastError);
            addSystemResourceInfo(report);
            addDiagnosticSuggestions(report);
        } catch (JSONException e) {
            Log.e(TAG, "创建诊断报告时出错", e);
        }

        return report;
    }

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

    private void addDiagnosticSuggestions(JSONObject report) throws JSONException {
        JSONObject suggestions = new JSONObject();
        long currentTime = System.currentTimeMillis();

        if (!AccessibilityDataService.isServiceConnected()) {
            suggestions.put("accessibility_service", "无障碍服务未连接，请在设置中启用");
        }

        if (currentTime - lastAccessibilityEventTime > 60000) {
            suggestions.put("accessibility_events", "长时间未接收到无障碍事件，可能服务异常或权限不足");
        }

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
        lastError = null;
        Log.i(TAG, "统计数据已重置");
    }

    /**
     * 检查服务健康状态
     */
    public boolean isHealthy() {
        long currentTime = System.currentTimeMillis();

        if (!AccessibilityDataService.isServiceConnected()) {
            Log.w(TAG, "健康检查失败: 无障碍服务未连接");
            return false;
        }

        if (currentTime - lastAccessibilityEventTime > 300000) {
            Log.w(TAG, "健康检查失败: 长时间未接收到无障碍事件");
            return false;
        }

        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100;
        if (memoryUsage > 90) {
            Log.w(TAG, "健康检查警告: 内存使用过高(" + String.format("%.1f%%", memoryUsage) + ")");
            return false;
        }

        return true;
    }
}
