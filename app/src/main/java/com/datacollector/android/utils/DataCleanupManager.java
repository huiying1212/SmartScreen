package com.datacollector.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.os.HandlerThread;

/**
 * 数据清理管理器
 * 使用CollectionConfig的可配置保留策略（借鉴Beiwe的可配置数据管理）。
 * 支持清理 .json、.enc（加密）和 .json.gz（压缩）文件。
 */
public class DataCleanupManager {
    
    private static final String TAG = "DataCleanupManager";
    private static final String PREFS_NAME = "data_cleanup_prefs";
    private static final String KEY_LAST_CLEANUP_TIME = "last_cleanup_time";
    private static final String KEY_TOTAL_FILES_CLEANED = "total_files_cleaned";
    private static final String KEY_TOTAL_SPACE_FREED = "total_space_freed";
    
    private static final long CLEANUP_INTERVAL = TimeUnit.HOURS.toMillis(6);
    private static final long LOG_FILE_MAX_AGE = TimeUnit.DAYS.toMillis(1);
    private static final long TEMP_FILE_MAX_AGE = TimeUnit.HOURS.toMillis(2);
    
    private static final int MAX_CONTEXT_FILES = 500;
    private static final int MAX_ANALYSIS_FILES = 100;
    
    private final Context context;
    private SharedPreferences prefs;
    private Handler cleanupHandler;
    private HandlerThread cleanupThread;
    private Runnable cleanupRunnable;
    private boolean isCleanupScheduled = false;
    
    public DataCleanupManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.cleanupThread = new HandlerThread("DataCleanupThread");
        this.cleanupThread.start();
        this.cleanupHandler = new Handler(cleanupThread.getLooper());
        
        initializeCleanupTask();
        Log.i(TAG, "DataCleanupManager initialized");
    }
    
    /**
     * 初始化清理任务
     */
    private void initializeCleanupTask() {
        cleanupRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    performCleanup();
                    scheduleNextCleanup();
                } catch (Exception e) {
                    Log.e(TAG, "清理任务执行出错", e);
                    // 即使出错也要安排下次清理
                    scheduleNextCleanup();
                }
            }
        };
        
        // 检查是否需要立即执行清理
        long lastCleanupTime = prefs.getLong(KEY_LAST_CLEANUP_TIME, 0);
        long currentTime = System.currentTimeMillis();
        
        if (lastCleanupTime == 0 || (currentTime - lastCleanupTime) > CLEANUP_INTERVAL) {
            // 延迟5秒后开始第一次清理，避免应用启动时的性能影响
            cleanupHandler.postDelayed(cleanupRunnable, 5000);
        } else {
            // 安排下次清理
            long nextCleanupDelay = CLEANUP_INTERVAL - (currentTime - lastCleanupTime);
            cleanupHandler.postDelayed(cleanupRunnable, nextCleanupDelay);
        }
        
        isCleanupScheduled = true;
        Log.d(TAG, "清理任务已安排");
    }
    
    /**
     * 安排下次清理
     */
    private void scheduleNextCleanup() {
        if (isCleanupScheduled && cleanupHandler != null) {
            cleanupHandler.postDelayed(cleanupRunnable, CLEANUP_INTERVAL);
            Log.d(TAG, "已安排下次清理，间隔: " + (CLEANUP_INTERVAL / 1000 / 60) + " 分钟");
        }
    }
    
    /**
     * 执行清理操作
     */
    public void performCleanup() {
        Log.i(TAG, "开始执行数据清理...");
        
        long startTime = System.currentTimeMillis();
        int totalFilesDeleted = 0;
        long totalSpaceFreed = 0;
        
        try {
            File dataDir = context.getExternalFilesDir(null);
            if (dataDir == null || !dataDir.exists()) {
                Log.w(TAG, "数据目录不存在，跳过清理");
                return;
            }
            
            // 1. 清理上下文数据文件
            CleanupResult contextResult = cleanupContextDataFiles(dataDir);
            totalFilesDeleted += contextResult.filesDeleted;
            totalSpaceFreed += contextResult.spaceFreed;
            
            // 2. 清理分析结果文件
            CleanupResult analysisResult = cleanupAnalysisResultFiles(dataDir);
            totalFilesDeleted += analysisResult.filesDeleted;
            totalSpaceFreed += analysisResult.spaceFreed;
            
            // 3. 清理日志文件
            CleanupResult logResult = cleanupLogFiles(dataDir);
            totalFilesDeleted += logResult.filesDeleted;
            totalSpaceFreed += logResult.spaceFreed;
            
            // 4. 清理临时文件
            CleanupResult tempResult = cleanupTempFiles(dataDir);
            totalFilesDeleted += tempResult.filesDeleted;
            totalSpaceFreed += tempResult.spaceFreed;
            
            // 5. 检查总存储大小，如果超限则进一步清理
            long maxSizeMb = CollectionConfig.getInstance(context)
                    .getInt(CollectionConfig.KEY_MAX_STORAGE_MB, 200);
            if (getTotalDataSize(dataDir) > maxSizeMb * 1024 * 1024) {
                CleanupResult emergencyResult = performEmergencyCleanup(dataDir);
                totalFilesDeleted += emergencyResult.filesDeleted;
                totalSpaceFreed += emergencyResult.spaceFreed;
            }
            
            // 更新统计信息
            updateCleanupStats(totalFilesDeleted, totalSpaceFreed);
            
            long duration = System.currentTimeMillis() - startTime;
            Log.i(TAG, String.format("清理完成: 删除 %d 个文件，释放 %.2f MB 空间，耗时 %d ms", 
                                   totalFilesDeleted, 
                                   totalSpaceFreed / 1024.0 / 1024.0, 
                                   duration));
            
        } catch (Exception e) {
            Log.e(TAG, "执行清理时出错", e);
        }
    }
    
    /**
     * 清理上下文数据文件（支持 .json / .enc / .json.gz 格式）
     */
    private CleanupResult cleanupContextDataFiles(File dataDir) {
        Log.d(TAG, "清理上下文数据文件...");
        
        CollectionConfig config = CollectionConfig.getInstance(context);
        long contextDataMaxAge = TimeUnit.DAYS.toMillis(
                config.getInt(CollectionConfig.KEY_DATA_RETENTION_DAYS, 7));
        long maxTotalSizeMb = config.getInt(CollectionConfig.KEY_MAX_STORAGE_MB, 200);
        
        File contextDataDir = new File(dataDir, "data");
        File[] contextFiles = null;
        
        if (contextDataDir.exists()) {
            contextFiles = contextDataDir.listFiles((dir, name) -> 
                name.startsWith("context_data_") && 
                (name.endsWith(".json") || name.endsWith(".enc") || name.endsWith(".json.gz")));
        } else {
            contextFiles = dataDir.listFiles((dir, name) -> 
                name.startsWith("context_data_") && 
                (name.endsWith(".json") || name.endsWith(".enc") || name.endsWith(".json.gz")));
        }
        
        if (contextFiles == null || contextFiles.length == 0) {
            return new CleanupResult(0, 0);
        }
        
        // 按时间排序，保留最新的文件
        Arrays.sort(contextFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        
        int filesDeleted = 0;
        long spaceFreed = 0;
        long currentTime = System.currentTimeMillis();
        
        for (int i = 0; i < contextFiles.length; i++) {
            File file = contextFiles[i];
            long fileAge = currentTime - file.lastModified();
            
            if (fileAge > contextDataMaxAge || i >= MAX_CONTEXT_FILES) {
                long fileSize = file.length();
                if (file.delete()) {
                    filesDeleted++;
                    spaceFreed += fileSize;
                    Log.d(TAG, "删除上下文文件: " + file.getName() + " (大小: " + fileSize + " bytes)");
                } else {
                    Log.w(TAG, "无法删除文件: " + file.getName());
                }
            }
        }
        
        return new CleanupResult(filesDeleted, spaceFreed);
    }
    
    /**
     * 清理分析结果文件
     */
    private CleanupResult cleanupAnalysisResultFiles(File dataDir) {
        Log.d(TAG, "清理分析结果文件...");
        
        CollectionConfig config = CollectionConfig.getInstance(context);
        long analysisResultMaxAge = TimeUnit.DAYS.toMillis(
                config.getInt(CollectionConfig.KEY_ANALYSIS_RETENTION_DAYS, 3));
        
        File analysisDataDir = new File(dataDir, "analysis");
        File[] analysisFiles = null;
        
        if (analysisDataDir.exists()) {
            analysisFiles = analysisDataDir.listFiles((dir, name) -> 
                name.startsWith("analysis_result_") && 
                (name.endsWith(".json") || name.endsWith(".enc")));
        } else {
            analysisFiles = dataDir.listFiles((dir, name) -> 
                name.startsWith("analysis_result_") && 
                (name.endsWith(".json") || name.endsWith(".enc")));
        }
        
        if (analysisFiles == null || analysisFiles.length == 0) {
            return new CleanupResult(0, 0);
        }
        
        // 按时间排序，保留最新的文件
        Arrays.sort(analysisFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        
        int filesDeleted = 0;
        long spaceFreed = 0;
        long currentTime = System.currentTimeMillis();
        
        for (int i = 0; i < analysisFiles.length; i++) {
            File file = analysisFiles[i];
            long fileAge = currentTime - file.lastModified();
            
            if (fileAge > analysisResultMaxAge || i >= MAX_ANALYSIS_FILES) {
                long fileSize = file.length();
                if (file.delete()) {
                    filesDeleted++;
                    spaceFreed += fileSize;
                    Log.d(TAG, "删除分析文件: " + file.getName() + " (大小: " + fileSize + " bytes)");
                }
            }
        }
        
        return new CleanupResult(filesDeleted, spaceFreed);
    }
    
    /**
     * 清理日志文件
     */
    private CleanupResult cleanupLogFiles(File dataDir) {
        Log.d(TAG, "清理日志文件...");
        
        int filesDeleted = 0;
        long spaceFreed = 0;
        long currentTime = System.currentTimeMillis();
        
        // 查找各种可能的日志文件
        String[] logPatterns = {"log_", "error_", "debug_", "trace_"};
        String[] logExtensions = {".txt", ".log"};
        
        for (String pattern : logPatterns) {
            for (String extension : logExtensions) {
                File[] logFiles = dataDir.listFiles((dir, name) -> 
                    name.startsWith(pattern) && name.endsWith(extension));
                
                if (logFiles != null) {
                    for (File file : logFiles) {
                        long fileAge = currentTime - file.lastModified();
                        if (fileAge > LOG_FILE_MAX_AGE) {
                            long fileSize = file.length();
                            if (file.delete()) {
                                filesDeleted++;
                                spaceFreed += fileSize;
                                Log.d(TAG, "删除日志文件: " + file.getName());
                            }
                        }
                    }
                }
            }
        }
        
        return new CleanupResult(filesDeleted, spaceFreed);
    }
    
    /**
     * 清理临时文件
     */
    private CleanupResult cleanupTempFiles(File dataDir) {
        Log.d(TAG, "清理临时文件...");
        
        int filesDeleted = 0;
        long spaceFreed = 0;
        long currentTime = System.currentTimeMillis();
        
        // 查找临时文件
        String[] tempPatterns = {"temp_", "tmp_", ".tmp", "cache_"};
        
        for (String pattern : tempPatterns) {
            File[] tempFiles = dataDir.listFiles((dir, name) -> 
                name.startsWith(pattern) || name.endsWith(pattern));
            
            if (tempFiles != null) {
                for (File file : tempFiles) {
                    long fileAge = currentTime - file.lastModified();
                    if (fileAge > TEMP_FILE_MAX_AGE) {
                        long fileSize = file.length();
                        if (file.delete()) {
                            filesDeleted++;
                            spaceFreed += fileSize;
                            Log.d(TAG, "删除临时文件: " + file.getName());
                        }
                    }
                }
            }
        }
        
        return new CleanupResult(filesDeleted, spaceFreed);
    }
    
    /**
     * 紧急清理 - 当存储空间超限时
     */
    private CleanupResult performEmergencyCleanup(File dataDir) {
        Log.w(TAG, "执行紧急清理 - 存储空间超限");
        
        int filesDeleted = 0;
        long spaceFreed = 0;
        
        List<File> allDataFiles = new ArrayList<>();
        
        // Collect files from data/ and analysis/ subdirectories as well as root
        collectDataFiles(allDataFiles, dataDir);
        collectDataFiles(allDataFiles, new File(dataDir, "data"));
        collectDataFiles(allDataFiles, new File(dataDir, "analysis"));
        
        File[] contextFiles = dataDir.listFiles((dir, name) -> 
            name.startsWith("context_data_") && 
            (name.endsWith(".json") || name.endsWith(".enc") || name.endsWith(".json.gz")));
        if (contextFiles != null) {
            allDataFiles.addAll(Arrays.asList(contextFiles));
        }
        
        File[] analysisFiles = dataDir.listFiles((dir, name) -> 
            name.startsWith("analysis_result_") && 
            (name.endsWith(".json") || name.endsWith(".enc")));
        if (analysisFiles != null) {
            allDataFiles.addAll(Arrays.asList(analysisFiles));
        }
        
        // 按修改时间排序，最旧的文件在前
        Collections.sort(allDataFiles, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
        
        long maxBytes = CollectionConfig.getInstance(context)
                .getInt(CollectionConfig.KEY_MAX_STORAGE_MB, 200) * 1024L * 1024L;
        for (File file : allDataFiles) {
            if (getTotalDataSize(dataDir) <= (long)(maxBytes * 0.8)) {
                break; // 降到限制的80%就停止
            }
            
            long fileSize = file.length();
            if (file.delete()) {
                filesDeleted++;
                spaceFreed += fileSize;
                Log.d(TAG, "紧急删除文件: " + file.getName());
            }
        }
        
        return new CleanupResult(filesDeleted, spaceFreed);
    }
    
    private void collectDataFiles(List<File> target, File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles((d, name) ->
                name.endsWith(".json") || name.endsWith(".enc") || name.endsWith(".json.gz"));
        if (files != null) {
            target.addAll(Arrays.asList(files));
        }
    }

    /**
     * 递归计算数据目录的总大小
     */
    private long getTotalDataSize(File dataDir) {
        long totalSize = 0;
        
        File[] files = dataDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    totalSize += file.length();
                } else if (file.isDirectory()) {
                    totalSize += getTotalDataSize(file);
                }
            }
        }
        
        return totalSize;
    }
    
    /**
     * 更新清理统计信息
     */
    private void updateCleanupStats(int filesDeleted, long spaceFreed) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_LAST_CLEANUP_TIME, System.currentTimeMillis());
        
        int totalFilesCleanedBefore = prefs.getInt(KEY_TOTAL_FILES_CLEANED, 0);
        long totalSpaceFreedBefore = prefs.getLong(KEY_TOTAL_SPACE_FREED, 0);
        
        editor.putInt(KEY_TOTAL_FILES_CLEANED, totalFilesCleanedBefore + filesDeleted);
        editor.putLong(KEY_TOTAL_SPACE_FREED, totalSpaceFreedBefore + spaceFreed);
        
        editor.apply();
    }
    
    /**
     * 获取清理统计信息
     */
    public String getCleanupStats() {
        long lastCleanupTime = prefs.getLong(KEY_LAST_CLEANUP_TIME, 0);
        int totalFilesCleaned = prefs.getInt(KEY_TOTAL_FILES_CLEANED, 0);
        long totalSpaceFreed = prefs.getLong(KEY_TOTAL_SPACE_FREED, 0);
        
        StringBuilder stats = new StringBuilder();
        stats.append("数据清理统计:\n");
        stats.append("上次清理: ").append(lastCleanupTime > 0 ? new java.util.Date(lastCleanupTime).toString() : "从未清理").append("\n");
        stats.append("总清理文件数: ").append(totalFilesCleaned).append("\n");
        stats.append("总释放空间: ").append(String.format("%.2f MB", totalSpaceFreed / 1024.0 / 1024.0)).append("\n");
        
        // 当前数据目录大小
        File dataDir = context.getExternalFilesDir(null);
        if (dataDir != null && dataDir.exists()) {
            long currentSize = getTotalDataSize(dataDir);
            stats.append("当前数据大小: ").append(String.format("%.2f MB", currentSize / 1024.0 / 1024.0));
        }
        
        return stats.toString();
    }
    
    /**
     * 立即执行清理（手动触发）
     */
    public void performImmediateCleanup() {
        Log.i(TAG, "手动触发立即清理");
        cleanupHandler.post(cleanupRunnable);
    }
    
    /**
     * 停止清理任务
     */
    public void stopCleanup() {
        if (cleanupHandler != null && cleanupRunnable != null) {
            cleanupHandler.removeCallbacks(cleanupRunnable);
            isCleanupScheduled = false;
            Log.i(TAG, "数据清理任务已停止");
        }
        if (cleanupThread != null) {
            cleanupThread.quitSafely();
        }
    }
    
    /**
     * 清理结果内部类
     */
    private static class CleanupResult {
        final int filesDeleted;
        final long spaceFreed;
        
        CleanupResult(int filesDeleted, long spaceFreed) {
            this.filesDeleted = filesDeleted;
            this.spaceFreed = spaceFreed;
        }
    }
} 