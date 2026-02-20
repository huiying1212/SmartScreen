package com.datacollector.android.collectors;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.datacollector.android.models.ScreenContentData;
import com.datacollector.android.services.AccessibilityDataService;
import com.datacollector.android.utils.ScreenContentMonitor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 屏幕内容收集器
 * 基于 Android 无障碍服务收集屏幕文本内容，不包含 OCR 功能。
 */
public class ScreenContentCollector implements AccessibilityDataService.ScreenContentCallback {

    private static final String TAG = "ScreenContentCollector";
    private static final int QUEUE_MAX_SIZE = 20;
    private static final long SCREEN_STABILITY_THRESHOLD = 400;
    private static final int SCREEN_INTERVAL = 200;
    private static final float SIMILARITY_THRESHOLD = 0.8f;

    private Context context;
    private ConcurrentLinkedQueue<ScreenContentData> screenQueue;
    private Handler screenHandler;
    private Runnable screenMonitorRunnable;
    private boolean isCollecting = false;
    private ScreenContentMonitor contentMonitor;
    private String lastScreenContent = "";
    private long lastContentChangeTime = 0;
    private String currentAppPackage = "unknown.app";

    public ScreenContentCollector(Context context) {
        this.context = context;
        this.screenQueue = new ConcurrentLinkedQueue<>();
        this.screenHandler = new Handler(Looper.getMainLooper());
        this.contentMonitor = new ScreenContentMonitor(context);
        initializeScreenMonitor();
        AccessibilityDataService.setScreenContentCallback(this);
    }

    private void initializeScreenMonitor() {
        screenMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCollecting) {
                    captureScreenContent();
                    screenHandler.postDelayed(this, SCREEN_INTERVAL);
                }
            }
        };
    }

    public void startCollection() {
        if (!isCollecting) {
            isCollecting = true;
            if (!AccessibilityDataService.isServiceConnected()) {
                Log.w(TAG, "无障碍服务未连接，请在设置中启用");
            }
            screenHandler.post(screenMonitorRunnable);
            Log.d(TAG, "Started screen content collection (accessibility only)");
        }
    }

    public void stopCollection() {
        if (isCollecting) {
            isCollecting = false;
            screenHandler.removeCallbacks(screenMonitorRunnable);
            Log.d(TAG, "Stopped screen content collection");
        }
    }

    private void captureScreenContent() {
        try {
            if ("com.datacollector.android".equals(currentAppPackage)) {
                return;
            }
            String currentContent = extractScreenText();
            if (isScreenStable(currentContent)) {
                ScreenContentData screenData = createScreenContentData(currentContent);
                if (!isDuplicateContent(screenData)) {
                    addToQueue(screenData);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing screen content", e);
        }
    }

    private void processScreenData(ScreenContentData screenData) {
        if (!isDuplicateContent(screenData)) {
            addToQueue(screenData);
        }
    }

    private String extractScreenText() {
        StringBuilder textBuilder = new StringBuilder();
        try {
            AccessibilityNodeInfo rootNode = getRootNode();
            if (rootNode != null) {
                extractTextFromNode(rootNode, textBuilder);
                rootNode.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting screen text", e);
        }
        return textBuilder.toString().trim();
    }

    private AccessibilityNodeInfo getRootNode() {
        return AccessibilityDataService.getRootNodeInfo();
    }

    private void extractTextFromNode(AccessibilityNodeInfo node, StringBuilder textBuilder) {
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            textBuilder.append(text).append(" ");
        }
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.length() > 0) {
            textBuilder.append(contentDesc).append(" ");
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractTextFromNode(child, textBuilder);
                child.recycle();
            }
        }
    }

    private boolean isScreenStable(String currentContent) {
        long currentTime = System.currentTimeMillis();
        if (!currentContent.equals(lastScreenContent)) {
            lastScreenContent = currentContent;
            lastContentChangeTime = currentTime;
            return false;
        }
        return (currentTime - lastContentChangeTime) >= SCREEN_STABILITY_THRESHOLD;
    }

    private ScreenContentData createScreenContentData(String content) {
        ScreenContentData screenData = new ScreenContentData();
        screenData.timestamp = System.currentTimeMillis();
        screenData.content = content;
        screenData.type = detectScreenType(content);
        screenData.appPackage = getCurrentAppPackage();
        return screenData;
    }

    private String detectScreenType(String content) {
        if (content.contains("发送") || content.contains("聊天") ||
                content.contains("消息") || (content.contains(":") && content.contains("回复"))) {
            return "chat";
        }
        return "screen";
    }

    private String getCurrentAppPackage() {
        if (currentAppPackage != null && !currentAppPackage.equals("unknown.app") && !currentAppPackage.equals("unknown")) {
            return currentAppPackage;
        }
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
                if (tasks != null && !tasks.isEmpty() && tasks.get(0).topActivity != null) {
                    currentAppPackage = tasks.get(0).topActivity.getPackageName();
                    return currentAppPackage;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
                    if (processes != null) {
                        for (ActivityManager.RunningAppProcessInfo process : processes) {
                            if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                                currentAppPackage = process.processName;
                                return process.processName;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "获取当前应用包名失败: " + e.getMessage());
        }
        return currentAppPackage;
    }

    private boolean isDuplicateContent(ScreenContentData newData) {
        if (screenQueue.isEmpty()) return false;
        ScreenContentData lastData = null;
        for (ScreenContentData data : screenQueue) {
            lastData = data;
        }
        if (lastData == null) return false;
        float similarity = calculateSimilarity(newData.content, lastData.content);
        return similarity > SIMILARITY_THRESHOLD;
    }

    private float calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0f;
        if (text1.equals(text2)) return 1.0f;
        int maxLength = Math.max(text1.length(), text2.length());
        if (maxLength == 0) return 1.0f;
        int editDistance = calculateEditDistance(text1, text2);
        return 1.0f - (float) editDistance / maxLength;
    }

    private int calculateEditDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private void addToQueue(ScreenContentData screenData) {
        screenQueue.offer(screenData);
        while (screenQueue.size() > QUEUE_MAX_SIZE) {
            screenQueue.poll();
        }
    }

    public JSONArray getRecentScreenContent() {
        JSONArray contentArray = new JSONArray();
        try {
            for (ScreenContentData data : screenQueue) {
                JSONObject screenObj = new JSONObject();
                screenObj.put("timestamp", data.timestamp);
                screenObj.put("type", data.type);
                screenObj.put("content", data.content);
                screenObj.put("app_package", data.appPackage);
                contentArray.put(screenObj);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON array for screen content", e);
        }
        return contentArray;
    }

    public JSONArray getScreenContentInTimeRange(long startTime, long endTime) {
        JSONArray contentArray = new JSONArray();
        try {
            for (ScreenContentData data : screenQueue) {
                if (data.timestamp >= startTime && data.timestamp <= endTime) {
                    JSONObject screenObj = new JSONObject();
                    screenObj.put("timestamp", data.timestamp);
                    screenObj.put("type", data.type);
                    screenObj.put("content", data.content);
                    screenObj.put("app_package", data.appPackage);
                    contentArray.put(screenObj);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON array for time range", e);
        }
        return contentArray;
    }

    public void clearQueue() {
        screenQueue.clear();
    }

    public int getQueueSize() {
        return screenQueue.size();
    }

    /**
     * 返回屏幕内容统计（保留接口兼容，OCR 已移除）
     */
    public JSONObject getOcrStatistics() {
        JSONObject stats = new JSONObject();
        try {
            int totalItems = screenQueue.size();
            stats.put("total_items", totalItems);
            stats.put("ocr_items", 0);
            stats.put("ocr_coverage", 0.0f);
            stats.put("average_confidence", 0.0f);
            stats.put("ocr_enabled", false);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating statistics", e);
        }
        return stats;
    }

    public JSONObject getSystemPerformance() {
        return new JSONObject();
    }

    public JSONObject performScreenshotCleanup() {
        JSONObject result = new JSONObject();
        try {
            result.put("deleted_files", 0);
            result.put("freed_space_mb", 0.0);
            result.put("success", true);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating cleanup result", e);
        }
        return result;
    }

    public JSONObject getDiagnosticReport() {
        if (contentMonitor != null) {
            return contentMonitor.getDiagnosticReport();
        }
        JSONObject basicReport = new JSONObject();
        try {
            basicReport.put("monitor_available", false);
            basicReport.put("collecting", isCollecting);
            basicReport.put("queue_size", getQueueSize());
        } catch (JSONException e) {
            Log.e(TAG, "创建基本诊断报告时出错", e);
        }
        return basicReport;
    }

    public boolean isHealthy() {
        if (contentMonitor != null) {
            return contentMonitor.isHealthy();
        }
        return isCollecting && AccessibilityDataService.isServiceConnected();
    }

    public void resetStatistics() {
        if (contentMonitor != null) {
            contentMonitor.resetStatistics();
        }
    }

    @Override
    public void onScreenContentChanged(String content, String packageName) {
        if (!isCollecting) return;
        if (contentMonitor != null) {
            contentMonitor.recordAccessibilityEvent();
        }
        currentAppPackage = packageName;
        if (isScreenStable(content)) {
            ScreenContentData screenData = createScreenContentData(content);
            if (!isDuplicateContent(screenData)) {
                addToQueue(screenData);
                Log.d(TAG, "New screen content added from: " + packageName);
            }
        }
    }

    @Override
    public void onServiceError(String error) {
        Log.e(TAG, "无障碍服务错误: " + error);
        if (error != null && error.contains("权限不足")) {
            stopCollection();
        } else if (error != null && error.contains("内存不足")) {
            clearQueue();
        }
    }

    public void release() {
        stopCollection();
        screenQueue.clear();
        contentMonitor = null;
        Log.d(TAG, "ScreenContentCollector resources released");
    }
}
