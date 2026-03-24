package com.datacollector.android.managers;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import com.datacollector.android.api.ApiConfig;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.api.QwenImageApiClient;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.DataAggregator;
import com.datacollector.android.utils.MoodMapper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;

/**
 * 壁纸引擎（阶段性反思 - Phased Reflection）。
 *
 * 工作流：
 * 1. 聚合过去一个时间段内的使用数据
 * 2. 将数据 + 用户偏好权重发送给 LLM，提取 3 个核心元素词
 * 3. 将 3 个元素词传入 Image Generation API 生成隐喻风格图片
 * 4. 下载图像并设为桌面 + 锁屏壁纸
 *
 * 调度：默认 08:00, 12:00, 20:00，支持用户自定义。
 */
public class WallpaperGenerationManager {

    private static final String TAG = "WallpaperGenManager";

    public interface WallpaperGenerationCallback {
        void onSuccess(String message);
        void onError(String error);
        void onProgress(String status);
    }

    private final Context context;
    private final QwenImageApiClient qwenClient;
    private final DeepSeekApiClient deepSeekClient;
    private final DataAggregator aggregator;
    private final CollectionConfig config;
    private volatile boolean isGenerating = false;

    public WallpaperGenerationManager(Context context) {
        this.context = context.getApplicationContext();
        this.qwenClient = new QwenImageApiClient(context);
        this.deepSeekClient = new DeepSeekApiClient(context);
        this.aggregator = new DataAggregator(context);
        this.config = CollectionConfig.getInstance(context);
    }

    public boolean isGenerating() {
        return isGenerating;
    }

    /**
     * 检查是否到达壁纸生成调度时间。
     * 支持用户自定义的 3 个时间点。
     */
    public boolean shouldGenerate() {
        if (!config.getBoolean(CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, true)) {
            return false;
        }

        Calendar now = Calendar.getInstance();
        int currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        int[] slots = config.getWallpaperScheduleSlots();

        int latestPassedSlot = -1;
        for (int slot : slots) {
            if (currentMinuteOfDay >= slot) {
                latestPassedSlot = slot;
            }
        }

        if (latestPassedSlot < 0) return false;

        Calendar slotTime = Calendar.getInstance();
        slotTime.set(Calendar.HOUR_OF_DAY, latestPassedSlot / 60);
        slotTime.set(Calendar.MINUTE, latestPassedSlot % 60);
        slotTime.set(Calendar.SECOND, 0);
        slotTime.set(Calendar.MILLISECOND, 0);
        long slotTimestamp = slotTime.getTimeInMillis();

        long lastTime = config.getLong(CollectionConfig.KEY_LAST_WALLPAPER_GENERATION_TIME, 0);
        return lastTime < slotTimestamp;
    }

    /**
     * 执行完整的壁纸生成流水线（后台线程运行）。
     */
    public void generateAndSetWallpaper(WallpaperGenerationCallback callback) {
        if (isGenerating) {
            callback.onError("正在生成中，请稍候");
            return;
        }
        isGenerating = true;

        new Thread(() -> {
            try {
                // Step 1: 聚合使用数据
                callback.onProgress("正在收集使用数据...");
                int windowHours = config.getInt(CollectionConfig.KEY_AGGREGATION_WINDOW_HOURS, 6);
                JSONObject aggregatedData = aggregator.aggregateRecentData(windowHours);

                long screenTimeMs = 0;
                JSONObject screenUsage = aggregatedData.optJSONObject("screen_usage");
                if (screenUsage != null) {
                    screenTimeMs = screenUsage.optLong("today_screen_time_ms", 0);
                }
                MoodMapper.Mood mood = MoodMapper.fromScreenTime(screenTimeMs);

                // Step 2: LLM 提取 3 个核心元素词
                callback.onProgress("正在提取创意关键词...");
                String weightDesc = config.getWeightDescription();
                String keywords = deepSeekClient.extractKeywords(aggregatedData, weightDesc);

                if (keywords == null || keywords.isEmpty()) {
                    keywords = getFallbackKeywords(mood);
                }
                Log.i(TAG, "Keywords: " + keywords);

                // Step 3: 用元素词构造图像 Prompt，调用 Image API
                callback.onProgress("正在生成壁纸图片...");
                String imagePrompt = buildImagePrompt(keywords, mood, screenTimeMs);
                Log.i(TAG, "Image prompt: " + imagePrompt);

                final Bitmap[] resultBitmap = {null};
                final String[] resultError = {null};

                qwenClient.generateImage(imagePrompt,
                        "低分辨率，低画质，画面过饱和，蜡像感，文字，水印，logo，畸形",
                        "928*1664",
                        new QwenImageApiClient.ImageGenerationCallback() {
                            @Override
                            public void onSuccess(String imageUrl, Bitmap bitmap) {
                                resultBitmap[0] = bitmap;
                            }
                            @Override
                            public void onError(String error) {
                                resultError[0] = error;
                            }
                        });

                if (resultError[0] != null) {
                    callback.onError("图片生成失败: " + resultError[0]);
                    return;
                }
                if (resultBitmap[0] == null) {
                    callback.onError("图片生成返回空结果");
                    return;
                }

                // Step 4: 设置壁纸
                callback.onProgress("正在设置壁纸和锁屏...");
                setWallpaper(resultBitmap[0]);
                saveBitmapLocally(resultBitmap[0]);

                config.setLong(CollectionConfig.KEY_LAST_WALLPAPER_GENERATION_TIME,
                        System.currentTimeMillis());

                callback.onSuccess("壁纸已更新！关键词：" + keywords);
                Log.i(TAG, "Wallpaper generation complete");

            } catch (Exception e) {
                Log.e(TAG, "Wallpaper generation failed", e);
                callback.onError("壁纸生成出错: " + e.getMessage());
            } finally {
                isGenerating = false;
            }
        }).start();
    }

    /**
     * 根据 3 个元素词和心情构建文生图 Prompt。
     */
    private String buildImagePrompt(String keywords, MoodMapper.Mood mood, long screenTimeMs) {
        String moodHint = MoodMapper.toImagePromptFragment(mood, screenTimeMs);

        return "请创作一幅适合手机竖屏壁纸的隐喻性艺术画面。\n"
                + "核心元素词：" + keywords + "\n"
                + "情绪背景：" + moodHint + "\n"
                + "要求：画面中融入以上三个元素的隐喻表达，"
                + "风格唯美具有艺术感，不包含文字和 UI 元素，"
                + "适合作为手机壁纸的高质量竖屏构图。";
    }

    private String getFallbackKeywords(MoodMapper.Mood mood) {
        switch (mood) {
            case HAPPY:   return "阳光、花朵、微风";
            case CALM:    return "清晨、露珠、鸟鸣";
            case NEUTRAL: return "湖面、倒影、薄雾";
            case DULL:    return "夕阳、归途、灯塔";
            case TIRED:   return "星空、萤火、小路";
            case PAINFUL:
            default:      return "深夜、月光、安眠";
        }
    }

    private void setWallpaper(Bitmap bitmap) throws IOException {
        WallpaperManager wm = WallpaperManager.getInstance(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
        } else {
            wm.setBitmap(bitmap);
        }
        Log.i(TAG, "Wallpaper set (home + lock)");
    }

    private void saveBitmapLocally(Bitmap bitmap) {
        try {
            File wallpaperDir = new File(context.getExternalFilesDir(null), "wallpapers");
            if (!wallpaperDir.exists()) wallpaperDir.mkdirs();

            String fileName = "wallpaper_" + System.currentTimeMillis() + ".png";
            File file = new File(wallpaperDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos);
            }

            cleanupOldWallpapers(wallpaperDir, 5);
        } catch (Exception e) {
            Log.w(TAG, "Failed to save wallpaper locally", e);
        }
    }

    private void cleanupOldWallpapers(File dir, int keepCount) {
        File[] files = dir.listFiles((d, name) ->
                name.startsWith("wallpaper_") && name.endsWith(".png"));
        if (files == null || files.length <= keepCount) return;

        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keepCount; i < files.length; i++) {
            files[i].delete();
        }
    }

    public void shutdown() {
        qwenClient.shutdown();
        deepSeekClient.shutdown();
    }
}
