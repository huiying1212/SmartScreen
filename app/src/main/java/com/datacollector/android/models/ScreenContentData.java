package com.datacollector.android.models;

/**
 * 屏幕内容数据类
 * 仅包含无障碍服务获取的文本内容
 */
public class ScreenContentData {
    public long timestamp;
    public String content;       // 无障碍服务获取的文本内容
    public String type;         // "chat" 或 "screen"
    public String appPackage;

    public ScreenContentData() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取完整文本内容
     */
    public String getFullTextContent() {
        return content != null ? content.trim() : "";
    }

    /**
     * 检查是否有文本内容
     */
    public boolean hasTextContent() {
        return content != null && !content.trim().isEmpty();
    }

    /**
     * 获取数据大小估算（用于内存管理）
     */
    public long getEstimatedMemorySize() {
        return content != null ? content.length() * 2L : 0;
    }
}
