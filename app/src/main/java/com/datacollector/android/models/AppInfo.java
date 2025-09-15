package com.datacollector.android.models;

import android.graphics.drawable.Drawable;

/**
 * 应用信息数据模型
 */
public class AppInfo {
    public String label;
    public String packageName;
    public String className;
    public Drawable icon;
    public boolean isSystemApp;
    
    public AppInfo() {
    }
    
    public AppInfo(String label, String packageName, String className, Drawable icon, boolean isSystemApp) {
        this.label = label;
        this.packageName = packageName;
        this.className = className;
        this.icon = icon;
        this.isSystemApp = isSystemApp;
    }
} 