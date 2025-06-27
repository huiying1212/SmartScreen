package com.datacollector.android;

/**
 * 屏幕内容数据类
 */
public class ScreenContentData {
    long timestamp;
    String content;
    String type; // "chat" 或 "screen"
    String appPackage;
    
    public ScreenContentData() {
        this.timestamp = System.currentTimeMillis();
    }
} 