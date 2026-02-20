package com.datacollector.android.collectors;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
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

import com.datacollector.android.models.ScreenContentData;
import com.datacollector.android.services.AccessibilityDataService;
import com.datacollector.android.utils.OcrProcessor;
import com.datacollector.android.utils.ScreenshotCapture;
import com.datacollector.android.utils.ScreenshotManager;
import com.datacollector.android.utils.PowerOptimizer;
import com.datacollector.android.utils.ScreenContentMonitor;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

/**
 * 屏幕内容收集器（增强版）
 * 基于Android Accessibility Service收集屏幕文本内容
 * 新增OCR识别功能，可以识别图片中的文字
 * 实现类似CATIA论文中描述的屏幕内容队列管理
 */
public class ScreenContentCollector implements AccessibilityDataService.ScreenContentCallback {
    
    private static final String TAG = "ScreenContentCollector";
    private static final int QUEUE_MAX_SIZE = 20; // 最大保留的屏幕数量
    private static final long SCREEN_STABILITY_THRESHOLD = 400; // 屏幕稳定阈值(ms)
    private static final int SCREENSHOT_INTERVAL = 200; // 截图间隔(ms)
    private static final float SIMILARITY_THRESHOLD = 0.8f; // 相似度阈值
    
    // OCR相关常量
    private static final boolean ENABLE_OCR = true; // OCR功能开关
    private static final float OCR_CONFIDENCE_THRESHOLD = 0.3f; // OCR置信度阈值
    private static final int OCR_INTERVAL = 2000; // OCR处理间隔(ms)
    
    private Context context;
    private ConcurrentLinkedQueue<ScreenContentData> screenQueue;
    private Handler screenHandler;
    private Handler backgroundHandler;
    private Runnable screenMonitorRunnable;
    private boolean isCollecting = false;
    
    // OCR相关组件
    private OcrProcessor ocrProcessor;
    private ScreenshotCapture screenshotCapture;
    private ScreenshotManager screenshotManager;
    private PowerOptimizer powerOptimizer;
    private ScreenContentMonitor contentMonitor;
    private long lastOcrTime = 0;
    private boolean isOcrProcessing = false;
    
    // 后台线程池用于OCR处理
    private ExecutorService ocrExecutor;
    
    // 动态配置参数
    private int currentOcrInterval = OCR_INTERVAL;
    private int currentScreenshotQuality = 90;
    private boolean dynamicOptimizationEnabled = true;
    
    // 屏幕稳定性检测
    private String lastScreenContent = "";
    private long lastContentChangeTime = 0;
    private String currentAppPackage = "unknown.app";
    
    public ScreenContentCollector(Context context) {
        this.context = context;
        this.screenQueue = new ConcurrentLinkedQueue<>();
        this.screenHandler = new Handler(Looper.getMainLooper());
        
        // 创建后台线程Handler用于OCR处理
        android.os.HandlerThread backgroundThread = new android.os.HandlerThread("ScreenContentCollector-Background");
        backgroundThread.start();
        this.backgroundHandler = new Handler(backgroundThread.getLooper());
        
        // 创建OCR处理线程池
        this.ocrExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OCR-Processor");
            t.setDaemon(true);
            return t;
        });
        
        // 初始化监控工具
        this.contentMonitor = new ScreenContentMonitor(context);
        
        // 初始化OCR处理器
        if (ENABLE_OCR) {
            try {
                this.ocrProcessor = new OcrProcessor(context);
                this.screenshotCapture = new ScreenshotCapture(context);
                this.screenshotManager = new ScreenshotManager(context);
                this.powerOptimizer = new PowerOptimizer(context);
                
                // 检查OCR模型是否可用
                if (this.ocrProcessor != null && this.ocrProcessor.isOcrModelAvailable()) {
                    Log.d(TAG, "OCR功能已启用，模型状态正常");
                    Log.d(TAG, "OCR模型状态:\n" + this.ocrProcessor.getOcrModelStatus());
                } else {
                    Log.w(TAG, "OCR功能已启用，但模型可能未完全加载");
                    if (contentMonitor != null) {
                        contentMonitor.recordOcrFailure("OCR模型可能未完全加载");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "OCR初始化失败", e);
                
                // 记录具体的初始化失败原因
                String errorMsg = "OCR初始化失败: " + e.getMessage();
                if (e.getMessage() != null) {
                    if (e.getMessage().contains("network") || e.getMessage().contains("download")) {
                        errorMsg = "OCR模型下载失败，网络连接问题: " + e.getMessage();
                        Log.e(TAG, "OCR模型下载失败，请检查网络连接");
                    } else if (e.getMessage().contains("storage") || e.getMessage().contains("space")) {
                        errorMsg = "OCR模型下载失败，存储空间不足: " + e.getMessage();
                        Log.e(TAG, "OCR模型下载失败，请清理存储空间");
                    } else if (e.getMessage().contains("permission")) {
                        errorMsg = "OCR模型下载失败，权限问题: " + e.getMessage();
                        Log.e(TAG, "OCR模型下载失败，请检查应用权限");
                    }
                }
                
                if (contentMonitor != null) {
                    contentMonitor.recordOcrFailure(errorMsg);
                }
            }
        }
        
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
            
            // 启动截图自动清理
            if (screenshotManager != null) {
                screenshotManager.startAutoCleanup();
            }
            
            // 启动备用的轮询监控（防止回调失效）
            screenHandler.post(screenMonitorRunnable);
            Log.d(TAG, "Started screen content collection with OCR support and optimization");
        }
    }
    
    /**
     * 停止收集屏幕内容
     */
    public void stopCollection() {
        if (isCollecting) {
            isCollecting = false;
            screenHandler.removeCallbacks(screenMonitorRunnable);
            
            // 停止截图自动清理
            if (screenshotManager != null) {
                screenshotManager.stopAutoCleanup();
            }
            
            Log.d(TAG, "Stopped screen content collection");
        }
    }
    
    /**
     * 捕获当前屏幕内容（增强版，支持OCR）
     */
    private void captureScreenContent() {
        try {
            // 过滤掉launcher自身的数据，避免干扰分析
            if ("com.datacollector.android".equals(currentAppPackage)) {
                Log.d(TAG, "跳过launcher自身屏幕内容收集，避免分析干扰");
                return;
            }
            
            // 获取屏幕文本内容（无障碍服务）
            String currentContent = extractScreenText();
            
            // 检查屏幕稳定性
            if (isScreenStable(currentContent)) {
                // 创建屏幕内容数据
                ScreenContentData screenData = createScreenContentData(currentContent);
                
                // 检查是否需要进行OCR处理
                if (shouldPerformOcr()) {
                    performOcrOnScreen(screenData);
                } else {
                    // 不进行OCR处理，直接添加到队列
                    processScreenData(screenData);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error capturing screen content", e);
        }
    }
    
    /**
     * 判断是否需要进行OCR处理（增强版，包含电量和内存优化）
     */
    private boolean shouldPerformOcr() {
        if (!ENABLE_OCR || ocrProcessor == null || screenshotCapture == null) {
            return false;
        }
        
        if (isOcrProcessing) {
            return false; // 正在处理OCR，跳过
        }
        
        // 获取优化配置
        if (dynamicOptimizationEnabled && powerOptimizer != null) {
            PowerOptimizer.OcrConfig config = powerOptimizer.getOptimizedOcrConfig();
            if (!config.enableOcr) {
                Log.d(TAG, "OCR disabled due to power optimization");
                return false; // 激进省电模式下禁用OCR
            }
            // 更新动态间隔
            currentOcrInterval = config.ocrInterval;
            currentScreenshotQuality = config.screenshotQuality;
        }
        
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastOcrTime) >= currentOcrInterval;
    }
    
    /**
     * 对屏幕进行OCR处理（改进版，使用后台线程）
     */
    private void performOcrOnScreen(ScreenContentData screenData) {
        isOcrProcessing = true;
        lastOcrTime = System.currentTimeMillis();
        
        // 在后台线程中执行OCR处理，避免阻塞主线程
        ocrExecutor.execute(() -> {
            try {
                // 尝试截图并进行OCR
                if (context instanceof Activity) {
                    // 如果是Activity上下文，可以截图
                    Activity activity = (Activity) context;
                    android.view.View rootView = activity.findViewById(android.R.id.content);
                    
                    // 在主线程中获取View截图
                    screenHandler.post(() -> {
                        screenshotCapture.captureView(rootView, new ScreenshotCapture.ScreenshotCallback() {
                            @Override
                            public void onSuccess(Bitmap bitmap, String filePath) {
                                // 在后台线程中进行OCR识别
                                ocrExecutor.execute(() -> {
                                    try {
                                        ocrProcessor.recognizeTextFromBitmap(bitmap, new OcrProcessor.OcrCallback() {
                                            @Override
                                            public void onSuccess(String recognizedText, float confidence) {
                                                // OCR成功，更新屏幕数据
                                                screenData.ocrText = recognizedText;
                                                screenData.ocrConfidence = confidence;
                                                screenData.hasOcrData = true;
                                                screenData.screenshotPath = filePath;
                                                
                                                // 记录OCR成功
                                                if (contentMonitor != null) {
                                                    contentMonitor.recordOcrSuccess();
                                                }
                                                
                                                // 及时回收Bitmap，避免内存泄漏
                                                if (bitmap != null && !bitmap.isRecycled()) {
                                                    bitmap.recycle();
                                                }
                                                
                                                Log.d(TAG, String.format("OCR识别成功: 置信度=%.2f, 文本长度=%d", 
                                                    confidence, recognizedText.length()));
                                                
                                                // 在主线程中处理完成的屏幕数据
                                                screenHandler.post(() -> {
                                                    processScreenData(screenData);
                                                    isOcrProcessing = false;
                                                });
                                            }
                                            
                                            @Override
                                            public void onError(String error) {
                                                Log.w(TAG, "OCR识别失败: " + error);
                                                
                                                // 记录OCR失败
                                                if (contentMonitor != null) {
                                                    contentMonitor.recordOcrFailure(error);
                                                }
                                                
                                                // 及时回收Bitmap
                                                if (bitmap != null && !bitmap.isRecycled()) {
                                                    bitmap.recycle();
                                                }
                                                
                                                // OCR失败，仍然保存无障碍服务的数据
                                                screenHandler.post(() -> {
                                                    processScreenData(screenData);
                                                    isOcrProcessing = false;
                                                });
                                            }
                                        });
                                    } catch (Exception e) {
                                        Log.e(TAG, "OCR处理异常", e);
                                        if (bitmap != null && !bitmap.isRecycled()) {
                                            bitmap.recycle();
                                        }
                                        screenHandler.post(() -> {
                                            processScreenData(screenData);
                                            isOcrProcessing = false;
                                        });
                                    }
                                });
                            }
                            
                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "截图失败: " + error);
                                // 截图失败，直接处理无障碍服务的数据
                                screenHandler.post(() -> {
                                    processScreenData(screenData);
                                    isOcrProcessing = false;
                                });
                            }
                        });
                    });
                } else {
                    // 非Activity上下文，无法截图，直接处理数据
                    screenHandler.post(() -> {
                        processScreenData(screenData);
                        isOcrProcessing = false;
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "OCR处理流程异常", e);
                screenHandler.post(() -> {
                    processScreenData(screenData);
                    isOcrProcessing = false;
                });
            }
        });
    }
    
    /**
     * 处理屏幕数据（统一入口）
     */
    private void processScreenData(ScreenContentData screenData) {
        // 检查是否与队列中最新内容相似
        if (!isDuplicateContent(screenData)) {
            addToQueue(screenData);
            
            // 记录OCR结果日志
            if (screenData.hasOcrData) {
                Log.d(TAG, String.format("添加带OCR数据的屏幕内容: 无障碍文本长度=%d, OCR文本长度=%d", 
                    screenData.content != null ? screenData.content.length() : 0,
                    screenData.ocrText != null ? screenData.ocrText.length() : 0));
            }
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
     * 检查是否为重复内容（增强版，考虑OCR内容）
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
        
        // 计算无障碍服务内容相似度
        float accessibilitySimilarity = calculateSimilarity(newData.content, lastData.content);
        
        // 计算OCR内容相似度
        float ocrSimilarity = 0.0f;
        if (newData.hasOcrData && lastData.hasOcrData) {
            ocrSimilarity = calculateSimilarity(newData.ocrText, lastData.ocrText);
        }
        
        // 综合判断相似度
        float overallSimilarity = Math.max(accessibilitySimilarity, ocrSimilarity);
        
        return overallSimilarity > SIMILARITY_THRESHOLD;
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
     * 获取最近的屏幕内容（JSON格式，包含OCR数据）
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
                
                // 添加OCR相关字段
                if (data.hasOcrData) {
                    screenObj.put("ocr_text", data.ocrText);
                    screenObj.put("ocr_confidence", data.ocrConfidence);
                    screenObj.put("screenshot_path", data.screenshotPath);
                    screenObj.put("full_text_content", data.getFullTextContent());
                }
                
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
                    
                    // 添加OCR相关字段
                    if (data.hasOcrData) {
                        screenObj.put("ocr_text", data.ocrText);
                        screenObj.put("ocr_confidence", data.ocrConfidence);
                        screenObj.put("screenshot_path", data.screenshotPath);
                        screenObj.put("full_text_content", data.getFullTextContent());
                    }
                    
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
     * 获取OCR统计信息（增强版，包含优化信息）
     */
    public JSONObject getOcrStatistics() {
        JSONObject stats = new JSONObject();
        try {
            int totalItems = 0;
            int ocrItems = 0;
            float totalConfidence = 0.0f;
            
            for (ScreenContentData data : screenQueue) {
                totalItems++;
                if (data.hasOcrData) {
                    ocrItems++;
                    totalConfidence += data.ocrConfidence;
                }
            }
            
            stats.put("total_items", totalItems);
            stats.put("ocr_items", ocrItems);
            stats.put("ocr_coverage", totalItems > 0 ? (float) ocrItems / totalItems : 0.0f);
            stats.put("average_confidence", ocrItems > 0 ? totalConfidence / ocrItems : 0.0f);
            stats.put("ocr_enabled", ENABLE_OCR);
            
            // 添加优化信息
            if (powerOptimizer != null) {
                PowerOptimizer.SystemPerformance perf = powerOptimizer.getSystemPerformance();
                stats.put("battery_percent", perf.batteryStatus.batteryPercent);
                stats.put("available_memory_mb", perf.memoryStatus.availableMemoryMB);
                stats.put("optimization_level", perf.optimizationLevel.toString());
                stats.put("power_save_mode", perf.powerSaveMode);
                stats.put("current_ocr_interval", currentOcrInterval);
                stats.put("current_screenshot_quality", currentScreenshotQuality);
            }
            
            // 添加存储信息
            if (screenshotManager != null) {
                ScreenshotManager.StorageStats storage = screenshotManager.getStorageStats();
                stats.put("screenshot_count", storage.fileCount);
                stats.put("screenshot_storage_mb", Math.round(storage.totalSizeMB * 100) / 100.0);
                stats.put("oldest_screenshot_days", storage.oldestFileAgeDays);
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating OCR statistics", e);
        }
        
        return stats;
    }
    
    /**
     * 获取系统性能信息
     */
    public JSONObject getSystemPerformance() {
        JSONObject perfData = new JSONObject();
        
        if (powerOptimizer != null) {
            try {
                PowerOptimizer.SystemPerformance perf = powerOptimizer.getSystemPerformance();
                
                // 电池信息
                JSONObject battery = new JSONObject();
                battery.put("percent", perf.batteryStatus.batteryPercent);
                battery.put("charging", perf.batteryStatus.isCharging);
                battery.put("temperature", perf.batteryStatus.temperatureCelsius);
                battery.put("healthy", perf.batteryStatus.isHealthy);
                
                // 内存信息
                JSONObject memory = new JSONObject();
                memory.put("available_mb", perf.memoryStatus.availableMemoryMB);
                memory.put("total_mb", perf.memoryStatus.totalMemoryMB);
                memory.put("used_mb", perf.memoryStatus.usedMemoryMB);
                memory.put("usage_percent", perf.memoryStatus.memoryUsagePercent);
                memory.put("low_memory", perf.memoryStatus.isLowMemory);
                
                perfData.put("battery", battery);
                perfData.put("memory", memory);
                perfData.put("optimization_level", perf.optimizationLevel.toString());
                perfData.put("power_save_mode", perf.powerSaveMode);
                perfData.put("estimated_cpu_usage", perf.estimatedCpuUsage);
                
            } catch (JSONException e) {
                Log.e(TAG, "Error creating performance data", e);
            }
        }
        
        return perfData;
    }
    
    /**
     * 手动触发截图清理
     */
    public JSONObject performScreenshotCleanup() {
        JSONObject result = new JSONObject();
        
        if (screenshotManager != null) {
            try {
                ScreenshotManager.CleanupResult cleanup = screenshotManager.clearAllScreenshots();
                result.put("deleted_files", cleanup.deletedCount);
                result.put("freed_space_mb", Math.round(cleanup.freedSpaceMB * 100) / 100.0);
                result.put("success", true);
                
                Log.i(TAG, String.format("Manual cleanup: %d files, %.2f MB freed", 
                    cleanup.deletedCount, cleanup.freedSpaceMB));
            } catch (JSONException e) {
                Log.e(TAG, "Error creating cleanup result", e);
            }
        } else {
            try {
                result.put("success", false);
                result.put("error", "Screenshot manager not available");
            } catch (JSONException e) {
                Log.e(TAG, "Error creating error result", e);
            }
        }
        
        return result;
    }
    
    /**
     * 设置动态优化开关
     */
    public void setDynamicOptimizationEnabled(boolean enabled) {
        this.dynamicOptimizationEnabled = enabled;
        Log.i(TAG, "Dynamic optimization " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * 获取动态优化状态
     */
    public boolean isDynamicOptimizationEnabled() {
        return dynamicOptimizationEnabled;
    }
    
    /**
     * 获取屏幕内容收集诊断报告
     */
    public JSONObject getDiagnosticReport() {
        if (contentMonitor != null) {
            return contentMonitor.getDiagnosticReport();
        }
        
        // 返回基本状态信息
        JSONObject basicReport = new JSONObject();
        try {
            basicReport.put("monitor_available", false);
            basicReport.put("collecting", isCollecting);
            basicReport.put("queue_size", getQueueSize());
            basicReport.put("ocr_enabled", ENABLE_OCR);
            basicReport.put("ocr_processing", isOcrProcessing);
        } catch (JSONException e) {
            Log.e(TAG, "创建基本诊断报告时出错", e);
        }
        
        return basicReport;
    }
    
    /**
     * 检查收集器健康状态
     */
    public boolean isHealthy() {
        if (contentMonitor != null) {
            return contentMonitor.isHealthy();
        }
        
        // 基本健康检查
        return isCollecting && AccessibilityDataService.isServiceConnected();
    }
    
    /**
     * 重置统计数据
     */
    public void resetStatistics() {
        if (contentMonitor != null) {
            contentMonitor.resetStatistics();
        }
        Log.i(TAG, "ScreenContentCollector统计数据已重置");
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
        
        // 记录无障碍事件
        if (contentMonitor != null) {
            contentMonitor.recordAccessibilityEvent();
        }
        
        currentAppPackage = packageName;
        
        // 检查屏幕稳定性
        if (isScreenStable(content)) {
            // 创建屏幕内容数据
            ScreenContentData screenData = createScreenContentData(content);
            
            // 检查是否需要进行OCR处理
            if (shouldPerformOcr()) {
                performOcrOnScreen(screenData);
            } else {
                // 检查是否与队列中最新内容相似
                if (!isDuplicateContent(screenData)) {
                    addToQueue(screenData);
                    Log.d(TAG, "New screen content added from: " + packageName);
                }
            }
        }
    }
    
    @Override
    public void onServiceError(String error) {
        Log.e(TAG, "无障碍服务错误: " + error);
        
        // 可以在这里实现错误处理逻辑
        // 例如：停止收集、通知用户、尝试恢复等
        if (error.contains("权限不足")) {
            Log.w(TAG, "权限不足，暂停屏幕内容收集");
            stopCollection();
        } else if (error.contains("内存不足")) {
            Log.w(TAG, "内存不足，清理队列");
            clearQueue();
        } else if (error.contains("服务中断")) {
            Log.w(TAG, "服务被中断，等待恢复");
            // 可以在这里实现重连逻辑
        }
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopCollection();
        
        // 停止后台线程池
        if (ocrExecutor != null && !ocrExecutor.isShutdown()) {
            ocrExecutor.shutdown();
            try {
                if (!ocrExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    ocrExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ocrExecutor.shutdownNow();
            }
            ocrExecutor = null;
        }
        
        // 停止后台Handler线程
        if (backgroundHandler != null) {
            backgroundHandler.getLooper().quit();
            backgroundHandler = null;
        }
        
        // 释放OCR相关资源
        if (ocrProcessor != null) {
            ocrProcessor.release();
            ocrProcessor = null;
        }
        
        if (screenshotCapture != null) {
            screenshotCapture.release();
            screenshotCapture = null;
        }
        
        if (screenshotManager != null) {
            screenshotManager.release();
            screenshotManager = null;
        }
        
        // PowerOptimizer不需要特殊释放，置空即可
        powerOptimizer = null;
        
        // 清理队列中的Bitmap资源
        clearQueueBitmaps();
        
        Log.d(TAG, "ScreenContentCollector resources released");
    }
    
    /**
     * 清理队列中的Bitmap资源
     */
    private void clearQueueBitmaps() {
        for (ScreenContentData data : screenQueue) {
            if (data.screenshot != null && !data.screenshot.isRecycled()) {
                data.screenshot.recycle();
                data.screenshot = null;
            }
        }
    }
} 