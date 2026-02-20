package com.datacollector.android.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * OCR日志记录工具
 * 专门用于记录OCR模型下载和识别相关的日志
 */
public class OcrLogger {
    
    private static final String TAG = "OcrLogger";
    private static final String LOG_FILE_NAME = "ocr_model_log.txt";
    
    private Context context;
    private File logFile;
    
    public OcrLogger(Context context) {
        this.context = context;
        initializeLogFile();
    }
    
    /**
     * 初始化日志文件
     */
    private void initializeLogFile() {
        try {
            File logDir = new File(context.getFilesDir(), "ocr_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            logFile = new File(logDir, LOG_FILE_NAME);
            
            // 如果日志文件不存在，创建并写入初始信息
            if (!logFile.exists()) {
                writeToLog("=== OCR模型日志开始 ===");
                writeToLog("日志文件创建时间: " + getCurrentTime());
                writeToLog("设备信息: " + android.os.Build.MODEL + " (" + android.os.Build.VERSION.RELEASE + ")");
                writeToLog("应用版本: " + getAppVersion());
                writeToLog("");
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化OCR日志文件失败", e);
        }
    }
    
    /**
     * 记录OCR模型下载开始
     */
    public void logModelDownloadStart(String modelType) {
        String message = "[" + getCurrentTime() + "] OCR模型下载开始 - " + modelType;
        Log.i(TAG, message);
        writeToLog(message);
    }
    
    /**
     * 记录OCR模型下载成功
     */
    public void logModelDownloadSuccess(String modelType) {
        String message = "[" + getCurrentTime() + "] OCR模型下载成功 - " + modelType;
        Log.i(TAG, message);
        writeToLog(message);
    }
    
    /**
     * 记录OCR模型下载失败
     */
    public void logModelDownloadFailure(String modelType, String error) {
        String message = "[" + getCurrentTime() + "] OCR模型下载失败 - " + modelType + " - 错误: " + error;
        Log.e(TAG, message);
        writeToLog(message);
        
        // 分析失败原因
        analyzeFailureReason(error);
    }
    
    /**
     * 记录OCR识别成功
     */
    public void logOcrSuccess(String textLength, float confidence) {
        String message = "[" + getCurrentTime() + "] OCR识别成功 - 文本长度: " + textLength + ", 置信度: " + confidence;
        Log.d(TAG, message);
        writeToLog(message);
    }
    
    /**
     * 记录OCR识别失败
     */
    public void logOcrFailure(String error) {
        String message = "[" + getCurrentTime() + "] OCR识别失败 - 错误: " + error;
        Log.w(TAG, message);
        writeToLog(message);
    }
    
    /**
     * 记录OCR初始化状态
     */
    public void logOcrInitialization(boolean success, String details) {
        String status = success ? "成功" : "失败";
        String message = "[" + getCurrentTime() + "] OCR初始化" + status + " - " + details;
        
        if (success) {
            Log.i(TAG, message);
        } else {
            Log.e(TAG, message);
        }
        writeToLog(message);
    }
    
    /**
     * 分析失败原因
     */
    private void analyzeFailureReason(String error) {
        String analysis = "";
        
        if (error.contains("network") || error.contains("connection") || error.contains("timeout")) {
            analysis = "分析: 网络连接问题，请检查网络连接状态";
        } else if (error.contains("storage") || error.contains("space") || error.contains("disk")) {
            analysis = "分析: 存储空间不足，请清理设备存储空间";
        } else if (error.contains("permission")) {
            analysis = "分析: 权限问题，请检查应用权限设置";
        } else if (error.contains("model") || error.contains("download")) {
            analysis = "分析: 模型下载问题，可能是网络或服务器问题";
        } else {
            analysis = "分析: 未知错误，建议重启应用或检查设备状态";
        }
        
        Log.w(TAG, analysis);
        writeToLog("  " + analysis);
    }
    
    /**
     * 写入日志到文件
     */
    private void writeToLog(String message) {
        if (logFile == null) {
            return;
        }
        
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(message + "\n");
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "写入OCR日志失败", e);
        }
    }
    
    /**
     * 获取当前时间
     */
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    /**
     * 获取应用版本
     */
    private String getAppVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "未知版本";
        }
    }
    
    /**
     * 获取日志文件路径
     */
    public String getLogFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : "日志文件未初始化";
    }
    
    /**
     * 清理旧日志（保留最近7天）
     */
    public void cleanupOldLogs() {
        try {
            File logDir = logFile.getParentFile();
            if (logDir != null && logDir.exists()) {
                File[] files = logDir.listFiles();
                if (files != null) {
                    long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
                    for (File file : files) {
                        if (file.lastModified() < sevenDaysAgo) {
                            file.delete();
                            Log.d(TAG, "删除旧日志文件: " + file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "清理旧日志失败", e);
        }
    }
}

