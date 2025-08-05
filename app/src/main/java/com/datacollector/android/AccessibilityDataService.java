package com.datacollector.android;

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
    private static final long MIN_EVENT_INTERVAL = 200; // 最小事件间隔200ms
    private static final int MAX_RECURSION_DEPTH = 20; // 最大递归深度
    private static final int MAX_TEXT_LENGTH = 10000; // 最大文本长度
    
    private long lastEventTime = 0;
    private Handler mainHandler;
    private AtomicBoolean isProcessing = new AtomicBoolean(false);
    
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
            processAccessibilityEvent(event);
            lastEventTime = currentTime;
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
            
            // 递归遍历子节点
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount && textBuilder.length() <= MAX_TEXT_LENGTH; i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    if (child != null) {
                        extractTextRecursivelySafe(child, textBuilder, depth + 1);
                    }
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
        Log.i(TAG, "无障碍服务已连接");
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
        
        return status.toString();
    }
} 