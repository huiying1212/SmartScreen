package com.datacollector.android.managers;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.api.QwenImageApiClient;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.DataAggregator;

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
 * 2. 将数据 + 用户偏好权重发送给 LLM，提炼场景关键词（数量不固定）
 * 3. 将关键词 + 风格传入 Image Generation API 生成壁纸
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
        default void onDebugInfo(String debugText) {}
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

                // Step 2: LLM 根据使用数据提炼场景关键词
                callback.onProgress("正在提取创意关键词...");
                String llmInputSummary = deepSeekClient.summarizeForKeywords(aggregatedData);
                String weightDesc = config.getUserPreferenceDescription();
                String llmKeywords = deepSeekClient.extractKeywords(aggregatedData, weightDesc);

                String keywordsSource;
                String keywords;
                if (llmKeywords != null && !llmKeywords.isEmpty()) {
                    keywords = llmKeywords;
                    keywordsSource = "LLM 返回";
                } else {
                    keywords = getFallbackKeywords();
                    keywordsSource = "LLM 调用失败，使用兜底关键词";
                }
                Log.i(TAG, "Keywords (" + keywordsSource + "): " + keywords);

                // Step 3: 用关键词构造图像 Prompt，调用 Image API
                callback.onProgress("正在生成壁纸图片...");
                String imagePrompt = buildImagePrompt(keywords);
                Log.i(TAG, "Image prompt: " + imagePrompt);

                StringBuilder debugInfo = new StringBuilder();
                debugInfo.append("══ 发给 LLM 的数据摘要 ══\n");
                debugInfo.append(llmInputSummary.isEmpty() ? "(空 — 没有聚合到任何数据)\n" : llmInputSummary);
                debugInfo.append("\n══ 聚合状态 ══\n");
                debugInfo.append("status: ").append(aggregatedData.optString("status", "unknown"));
                debugInfo.append(", total_collections: ").append(aggregatedData.optInt("total_collections", 0));
                debugInfo.append(", window_hours: ").append(aggregatedData.optInt("window_hours", 0));
                debugInfo.append("\n\n══ 关键词（").append(keywordsSource).append("）══\n");
                debugInfo.append(keywords);
                debugInfo.append("\n\n══ 发给图片生成模型的 Prompt ══\n");
                debugInfo.append(imagePrompt);
                callback.onDebugInfo(debugInfo.toString());

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

    private String buildImagePrompt(String keywords) {
        String styleDesc = config.getWallpaperStyleDescription();

        StringBuilder prompt = new StringBuilder();
        prompt.append("请创作一幅适合手机竖屏壁纸的隐喻性艺术画面。\n");
        prompt.append("场景关键词：").append(keywords).append("\n");
        prompt.append("风格要求：").append(styleDesc).append("\n");
        prompt.append("要求：画面中自然融入以上关键词所描绘的场景氛围，");
        prompt.append("不包含文字和 UI 元素，适合作为手机壁纸的高质量竖屏构图。");
        return prompt.toString();
    }

    private String getFallbackKeywords() {
        String[] fallbacks = {
                "窗边、阳光、咖啡杯",
                "书桌、台灯、绿植",
                "清晨、露珠、小路",
                "沙发、暖光、猫",
                "湖面、倒影、薄雾",
        };
        int index = (int) (System.currentTimeMillis() % fallbacks.length);
        return fallbacks[index];
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
