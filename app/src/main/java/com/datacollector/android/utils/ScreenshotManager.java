package com.datacollector.android.utils;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 截图管理器
 * 负责截图文件的自动清理、存储管理和资源优化
 */
public class ScreenshotManager {
    
    private static final String TAG = "ScreenshotManager";
    private static final String SCREENSHOT_DIR = "screenshots";
    
    // 清理策略配置
    private static final long MAX_FILE_AGE_MS = TimeUnit.DAYS.toMillis(3); // 文件最大保存3天
    private static final long MAX_TOTAL_SIZE_MB = 500; // 最大总存储500MB
    private static final int MAX_FILE_COUNT = 1000; // 最大文件数量1000个
    private static final long CLEANUP_INTERVAL_MS = TimeUnit.HOURS.toMillis(2); // 每2小时清理一次
    
    private Context context;
    private Handler cleanupHandler;
    private Runnable cleanupRunnable;
    private File screenshotDir;
    private boolean isCleanupScheduled = false;
    
    public ScreenshotManager(Context context) {
        this.context = context;
        this.cleanupHandler = new Handler(Looper.getMainLooper());
        this.screenshotDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), SCREENSHOT_DIR);
        
        // 确保目录存在
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
        }
        
        initializeCleanupTask();
        Log.d(TAG, "ScreenshotManager initialized, directory: " + screenshotDir.getAbsolutePath());
    }
    
    /**
     * 初始化清理任务
     */
    private void initializeCleanupTask() {
        cleanupRunnable = new Runnable() {
            @Override
            public void run() {
                performCleanup();
                // 重新调度下次清理
                if (isCleanupScheduled) {
                    cleanupHandler.postDelayed(this, CLEANUP_INTERVAL_MS);
                }
            }
        };
    }
    
    /**
     * 启动自动清理
     */
    public void startAutoCleanup() {
        if (!isCleanupScheduled) {
            isCleanupScheduled = true;
            // 立即执行一次清理
            cleanupHandler.post(cleanupRunnable);
            Log.d(TAG, "Auto cleanup started");
        }
    }
    
    /**
     * 停止自动清理
     */
    public void stopAutoCleanup() {
        if (isCleanupScheduled) {
            isCleanupScheduled = false;
            cleanupHandler.removeCallbacks(cleanupRunnable);
            Log.d(TAG, "Auto cleanup stopped");
        }
    }
    
    /**
     * 执行清理操作
     */
    public void performCleanup() {
        new Thread(() -> {
            try {
                CleanupResult result = cleanupScreenshots();
                Log.i(TAG, String.format("Cleanup completed: deleted %d files, freed %.2f MB", 
                    result.deletedCount, result.freedSpaceMB));
            } catch (Exception e) {
                Log.e(TAG, "Error during cleanup", e);
            }
        }).start();
    }
    
    /**
     * 清理截图文件
     */
    private CleanupResult cleanupScreenshots() {
        CleanupResult result = new CleanupResult();
        
        if (!screenshotDir.exists() || !screenshotDir.isDirectory()) {
            return result;
        }
        
        File[] files = screenshotDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null || files.length == 0) {
            return result;
        }
        
        List<File> fileList = new ArrayList<>(Arrays.asList(files));
        long currentTime = System.currentTimeMillis();
        
        // 1. 删除过期文件（超过3天）
        List<File> filesToDelete = new ArrayList<>();
        for (File file : fileList) {
            if (currentTime - file.lastModified() > MAX_FILE_AGE_MS) {
                filesToDelete.add(file);
            }
        }
        
        // 2. 检查总大小限制
        long totalSize = calculateTotalSize(fileList);
        if (totalSize > MAX_TOTAL_SIZE_MB * 1024 * 1024) {
            // 按修改时间排序，删除最旧的文件
            Collections.sort(fileList, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
            
            long targetSize = (long) (MAX_TOTAL_SIZE_MB * 1024 * 1024 * 0.8); // 目标为限制的80%
            long currentSize = totalSize;
            
            for (File file : fileList) {
                if (currentSize <= targetSize) break;
                if (!filesToDelete.contains(file)) {
                    filesToDelete.add(file);
                    currentSize -= file.length();
                }
            }
        }
        
        // 3. 检查文件数量限制
        if (fileList.size() > MAX_FILE_COUNT) {
            Collections.sort(fileList, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
            
            int filesToKeep = (int) (MAX_FILE_COUNT * 0.8); // 保留限制的80%
            for (int i = 0; i < fileList.size() - filesToKeep; i++) {
                File file = fileList.get(i);
                if (!filesToDelete.contains(file)) {
                    filesToDelete.add(file);
                }
            }
        }
        
        // 执行删除
        for (File file : filesToDelete) {
            long fileSize = file.length();
            if (file.delete()) {
                result.deletedCount++;
                result.freedSpaceMB += fileSize / (1024.0 * 1024.0);
            }
        }
        
        return result;
    }
    
    /**
     * 计算总文件大小
     */
    private long calculateTotalSize(List<File> files) {
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }
        return totalSize;
    }
    
    /**
     * 获取存储统计信息
     */
    public StorageStats getStorageStats() {
        StorageStats stats = new StorageStats();
        
        if (!screenshotDir.exists()) {
            return stats;
        }
        
        File[] files = screenshotDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) {
            return stats;
        }
        
        stats.fileCount = files.length;
        stats.totalSizeMB = calculateTotalSize(Arrays.asList(files)) / (1024.0 * 1024.0);
        
        // 计算最旧和最新文件时间
        long oldestTime = Long.MAX_VALUE;
        long newestTime = 0;
        
        for (File file : files) {
            long modTime = file.lastModified();
            if (modTime < oldestTime) oldestTime = modTime;
            if (modTime > newestTime) newestTime = modTime;
        }
        
        if (oldestTime != Long.MAX_VALUE) {
            stats.oldestFileAgeDays = (System.currentTimeMillis() - oldestTime) / (1000 * 60 * 60 * 24);
        }
        
        if (newestTime > 0) {
            stats.newestFileAgeMinutes = (System.currentTimeMillis() - newestTime) / (1000 * 60);
        }
        
        return stats;
    }
    
    /**
     * 手动清理所有截图文件
     */
    public CleanupResult clearAllScreenshots() {
        CleanupResult result = new CleanupResult();
        
        if (!screenshotDir.exists()) {
            return result;
        }
        
        File[] files = screenshotDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) {
            return result;
        }
        
        for (File file : files) {
            long fileSize = file.length();
            if (file.delete()) {
                result.deletedCount++;
                result.freedSpaceMB += fileSize / (1024.0 * 1024.0);
            }
        }
        
        Log.i(TAG, String.format("Manual cleanup: deleted %d files, freed %.2f MB", 
            result.deletedCount, result.freedSpaceMB));
        
        return result;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopAutoCleanup();
        if (cleanupHandler != null) {
            cleanupHandler.removeCallbacksAndMessages(null);
        }
        Log.d(TAG, "ScreenshotManager resources released");
    }
    
    /**
     * 清理结果数据类
     */
    public static class CleanupResult {
        public int deletedCount = 0;
        public double freedSpaceMB = 0.0;
    }
    
    /**
     * 存储统计数据类
     */
    public static class StorageStats {
        public int fileCount = 0;
        public double totalSizeMB = 0.0;
        public long oldestFileAgeDays = 0;
        public long newestFileAgeMinutes = 0;
    }
} 