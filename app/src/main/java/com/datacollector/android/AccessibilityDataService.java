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
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 处理无障碍事件
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                // 这里可以提取屏幕内容
                Log.d(TAG, "Screen content changed");
                rootNode.recycle();
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
    
    public static AccessibilityNodeInfo getRootNodeInfo() {
        if (instance != null) {
            return instance.getRootInActiveWindow();
        }
        return null;
    }
} 