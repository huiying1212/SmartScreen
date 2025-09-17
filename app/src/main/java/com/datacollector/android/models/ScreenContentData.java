package com.datacollector.android.models;

import android.graphics.Bitmap;

/**
 * 屏幕内容数据类
 * 扩展支持截图和OCR识别
 * 增强内存管理，避免内存泄漏
 */
public class ScreenContentData {
    public long timestamp;
    public String content;           // 无障碍服务获取的文本内容
    public String ocrText;          // OCR识别的文本内容
    public String type;             // "chat" 或 "screen"
    public String appPackage;
    public String screenshotPath;   // 截图文件路径
    public Bitmap screenshot;       // 截图Bitmap（临时存储，不持久化）
    public boolean hasOcrData;      // 是否包含OCR数据
    public float ocrConfidence;     // OCR识别置信度
    
    public ScreenContentData() {
        this.timestamp = System.currentTimeMillis();
        this.hasOcrData = false;
        this.ocrConfidence = 0.0f;
    }
    
    /**
     * 获取完整的文本内容（合并无障碍服务文本和OCR文本）
     */
    public String getFullTextContent() {
        StringBuilder fullText = new StringBuilder();
        
        // 添加无障碍服务获取的文本
        if (content != null && !content.trim().isEmpty()) {
            fullText.append("无障碍文本: ").append(content.trim());
        }
        
        // 添加OCR识别的文本
        if (ocrText != null && !ocrText.trim().isEmpty()) {
            if (fullText.length() > 0) {
                fullText.append("\n");
            }
            fullText.append("OCR文本: ").append(ocrText.trim());
        }
        
        return fullText.toString();
    }
    
    /**
     * 检查是否有任何文本内容
     */
    public boolean hasTextContent() {
        return (content != null && !content.trim().isEmpty()) || 
               (ocrText != null && !ocrText.trim().isEmpty());
    }
    
    /**
     * 清理Bitmap资源，避免内存泄漏
     */
    public void recycleBitmap() {
        if (screenshot != null && !screenshot.isRecycled()) {
            screenshot.recycle();
            screenshot = null;
        }
    }
    
    /**
     * 检查Bitmap是否有效
     */
    public boolean hasBitmap() {
        return screenshot != null && !screenshot.isRecycled();
    }
    
    /**
     * 获取数据大小估算（用于内存管理）
     */
    public long getEstimatedMemorySize() {
        long size = 0;
        
        if (content != null) {
            size += content.length() * 2; // 字符串大约2字节每字符
        }
        
        if (ocrText != null) {
            size += ocrText.length() * 2;
        }
        
        if (screenshot != null && !screenshot.isRecycled()) {
            size += screenshot.getByteCount();
        }
        
        return size;
    }
} 