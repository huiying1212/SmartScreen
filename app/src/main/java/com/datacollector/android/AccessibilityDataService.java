package com.datacollector.android;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;

/**
 * 无障碍服务类
 * 用于收集屏幕内容信息
 */
public class AccessibilityDataService extends AccessibilityService {
    
    private static final String TAG = "AccessibilityDataService";
    private static AccessibilityDataService instance;
    private static ScreenContentCallback contentCallback;
    
    // 屏幕内容变化回调接口
    public interface ScreenContentCallback {
        void onScreenContentChanged(String content, String packageName);
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 处理无障碍事件
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                try {
                    // 提取屏幕文本内容
                    String screenText = extractTextFromNode(rootNode);
                    String packageName = event.getPackageName() != null ? 
                                       event.getPackageName().toString() : "unknown";
                    
                    // 通知回调
                    if (contentCallback != null && screenText != null && !screenText.trim().isEmpty()) {
                        contentCallback.onScreenContentChanged(screenText, packageName);
                    }
                    
                    Log.d(TAG, "Screen content changed in: " + packageName);
                } finally {
                    rootNode.recycle();
                }
            }
        }
    }
    
    /**
     * 从节点提取文本内容
     */
    private String extractTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return "";
        
        StringBuilder textBuilder = new StringBuilder();
        extractTextRecursively(node, textBuilder);
        return textBuilder.toString().trim();
    }
    
    /**
     * 递归提取文本
     */
    private void extractTextRecursively(AccessibilityNodeInfo node, StringBuilder textBuilder) {
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
                extractTextRecursively(child, textBuilder);
                child.recycle();
            }
        }
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility service connected");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        contentCallback = null;
        Log.d(TAG, "Accessibility service destroyed");
    }
    
    /**
     * 获取根节点信息
     */
    public static AccessibilityNodeInfo getRootNodeInfo() {
        if (instance != null) {
            return instance.getRootInActiveWindow();
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
} 