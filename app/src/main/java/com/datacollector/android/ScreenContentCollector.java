package com.datacollector.android;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;

/**
 * 屏幕内容收集器
 * 基于Android Accessibility Service收集屏幕文本内容
 * 实现类似CATIA论文中描述的屏幕内容队列管理
 */
public class ScreenContentCollector implements AccessibilityDataService.ScreenContentCallback {
    
    private static final String TAG = "ScreenContentCollector";
    private static final int QUEUE_MAX_SIZE = 20; // 最大保留的屏幕数量
    private static final long SCREEN_STABILITY_THRESHOLD = 400; // 屏幕稳定阈值(ms)
    private static final int SCREENSHOT_INTERVAL = 200; // 截图间隔(ms)
    private static final float SIMILARITY_THRESHOLD = 0.8f; // 相似度阈值
    
    private Context context;
    private ConcurrentLinkedQueue<ScreenContentData> screenQueue;
    private Handler screenHandler;
    private Runnable screenMonitorRunnable;
    private boolean isCollecting = false;
    
    // 屏幕稳定性检测
    private String lastScreenContent = "";
    private long lastContentChangeTime = 0;
    private String currentAppPackage = "unknown.app";
    
    public ScreenContentCollector(Context context) {
        this.context = context;
        this.screenQueue = new ConcurrentLinkedQueue<>();
        this.screenHandler = new Handler(Looper.getMainLooper());
        initializeScreenMonitor();
        
        // 设置回调
        AccessibilityDataService.setScreenContentCallback(this);
    }
    
    /**
     * 初始化屏幕监控
     */
    private void initializeScreenMonitor() {
        screenMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCollecting) {
                    captureScreenContent();
                    screenHandler.postDelayed(this, SCREENSHOT_INTERVAL);
                }
            }
        };
    }
    
    /**
     * 开始收集屏幕内容
     */
    public void startCollection() {
        if (!isCollecting) {
            isCollecting = true;
            
            // 检查无障碍服务是否可用
            if (!AccessibilityDataService.isServiceConnected()) {
                Log.w(TAG, "无障碍服务未连接，请在设置中启用");
            }
            
            // 启动备用的轮询监控（防止回调失效）
            screenHandler.post(screenMonitorRunnable);
            Log.d(TAG, "Started screen content collection");
        }
    }
    
    /**
     * 停止收集屏幕内容
     */
    public void stopCollection() {
        if (isCollecting) {
            isCollecting = false;
            screenHandler.removeCallbacks(screenMonitorRunnable);
            Log.d(TAG, "Stopped screen content collection");
        }
    }
    
    /**
     * 捕获当前屏幕内容
     */
    private void captureScreenContent() {
        try {
            // 获取屏幕文本内容
            String currentContent = extractScreenText();
            
            // 检查屏幕稳定性
            if (isScreenStable(currentContent)) {
                // 创建屏幕内容数据
                ScreenContentData screenData = createScreenContentData(currentContent);
                
                // 检查是否与队列中最新内容相似
                if (!isDuplicateContent(screenData)) {
                    addToQueue(screenData);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error capturing screen content", e);
        }
    }
    
    /**
     * 提取屏幕文本内容
     */
    private String extractScreenText() {
        StringBuilder textBuilder = new StringBuilder();
        
        try {
            // 这里需要通过Accessibility Service获取屏幕内容
            // 由于这是一个示例，我们模拟屏幕文本提取
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
    
    /**
     * 获取根节点（需要Accessibility Service支持）
     */
    private AccessibilityNodeInfo getRootNode() {
        // 从AccessibilityDataService获取根节点
        return AccessibilityDataService.getRootNodeInfo();
    }
    
    /**
     * 从节点提取文本
     */
    private void extractTextFromNode(AccessibilityNodeInfo node, StringBuilder textBuilder) {
        if (node == null) return;
        
        // 获取节点文本
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            textBuilder.append(text).append(" ");
        }
        
        // 获取内容描述
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.length() > 0) {
            textBuilder.append(contentDesc).append(" ");
        }
        
        // 递归遍历子节点
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractTextFromNode(child, textBuilder);
                child.recycle();
            }
        }
    }
    
    /**
     * 检查屏幕是否稳定
     */
    private boolean isScreenStable(String currentContent) {
        long currentTime = System.currentTimeMillis();
        
        // 如果内容发生变化，更新时间戳
        if (!currentContent.equals(lastScreenContent)) {
            lastScreenContent = currentContent;
            lastContentChangeTime = currentTime;
            return false;
        }
        
        // 检查内容是否稳定了足够长的时间
        return (currentTime - lastContentChangeTime) >= SCREEN_STABILITY_THRESHOLD;
    }
    
    /**
     * 创建屏幕内容数据对象
     */
    private ScreenContentData createScreenContentData(String content) {
        ScreenContentData screenData = new ScreenContentData();
        screenData.timestamp = System.currentTimeMillis();
        screenData.content = content;
        screenData.type = detectScreenType(content);
        screenData.appPackage = getCurrentAppPackage();
        
        return screenData;
    }
    
    /**
     * 检测屏幕类型（聊天或普通屏幕）
     */
    private String detectScreenType(String content) {
        // 简单的聊天界面检测逻辑
        if (content.contains("发送") || content.contains("聊天") || 
            content.contains("消息") || content.contains(":") && content.contains("回复")) {
            return "chat";
        }
        return "screen";
    }
    
    /**
     * 获取当前应用包名
     */
    private String getCurrentAppPackage() {
        // 优先使用回调中获取的包名
        if (currentAppPackage != null && !currentAppPackage.equals("unknown.app") && !currentAppPackage.equals("unknown")) {
            return currentAppPackage;
        }
        
        try {
            // 尝试通过ActivityManager获取前台应用
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
                if (tasks != null && !tasks.isEmpty()) {
                    ActivityManager.RunningTaskInfo task = tasks.get(0);
                    if (task.topActivity != null) {
                        String packageName = task.topActivity.getPackageName();
                        currentAppPackage = packageName; // 缓存结果
                        return packageName;
                    }
                }
                
                // Android 5.0+ 使用UsageStats API（需要权限）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
                    if (processes != null && !processes.isEmpty()) {
                        for (ActivityManager.RunningAppProcessInfo process : processes) {
                            if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                                currentAppPackage = process.processName; // 缓存结果
                                return process.processName;
                            }
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "无法获取当前应用包名，权限不足: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "获取当前应用包名时出错: " + e.getMessage());
        }
        
        return currentAppPackage; // 返回缓存的值或默认值
    }
    
    /**
     * 检查是否为重复内容
     */
    private boolean isDuplicateContent(ScreenContentData newData) {
        if (screenQueue.isEmpty()) {
            return false;
        }
        
        // 获取队列中最新的内容
        ScreenContentData lastData = null;
        for (ScreenContentData data : screenQueue) {
            lastData = data; // 获取最后一个元素
        }
        
        if (lastData == null) {
            return false;
        }
        
        // 计算内容相似度
        float similarity = calculateSimilarity(newData.content, lastData.content);
        return similarity > SIMILARITY_THRESHOLD;
    }
    
    /**
     * 计算文本相似度
     */
    private float calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0f;
        }
        
        if (text1.equals(text2)) {
            return 1.0f;
        }
        
        // 简单的相似度计算（基于编辑距离）
        int maxLength = Math.max(text1.length(), text2.length());
        if (maxLength == 0) {
            return 1.0f;
        }
        
        int editDistance = calculateEditDistance(text1, text2);
        return 1.0f - (float) editDistance / maxLength;
    }
    
    /**
     * 计算编辑距离
     */
    private int calculateEditDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
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
    
    /**
     * 添加到队列
     */
    private void addToQueue(ScreenContentData screenData) {
        screenQueue.offer(screenData);
        
        // 维护队列大小
        while (screenQueue.size() > QUEUE_MAX_SIZE) {
            screenQueue.poll();
        }
        
        Log.d(TAG, "Added screen content to queue. Queue size: " + screenQueue.size());
    }
    
    /**
     * 获取最近的屏幕内容（JSON格式）
     */
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
    
    /**
     * 获取指定时间范围内的屏幕内容
     */
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
    
    /**
     * 清空屏幕内容队列
     */
    public void clearQueue() {
        screenQueue.clear();
        Log.d(TAG, "Screen content queue cleared");
    }
    
    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return screenQueue.size();
    }
    
    /**
     * 处理聊天内容（特殊处理）
     */
    private String processChatContent(String content) {
        // 对聊天内容进行特殊处理，识别发送者和消息
        // 这里可以实现论文中提到的chat recognition algorithm
        return content;
    }

    /**
     * 实现AccessibilityDataService回调接口
     */
    @Override
    public void onScreenContentChanged(String content, String packageName) {
        if (!isCollecting) return;
        
        currentAppPackage = packageName;
        
        // 检查屏幕稳定性
        if (isScreenStable(content)) {
            // 创建屏幕内容数据
            ScreenContentData screenData = createScreenContentData(content);
            
            // 检查是否与队列中最新内容相似
            if (!isDuplicateContent(screenData)) {
                addToQueue(screenData);
                Log.d(TAG, "New screen content added from: " + packageName);
            }
        }
    }
} 