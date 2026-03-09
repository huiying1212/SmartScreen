package com.datacollector.android.managers;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import com.datacollector.android.api.ApiConfig;
import com.datacollector.android.api.QwenImageApiClient;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.DataAggregator;
import com.datacollector.android.utils.MoodMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 壁纸生成管理器：
 * 1. 收集聚合数据，通过DeepSeek生成文生图提示词
 * 2. 调用Qwen-Image API生成图片
 * 3. 设置为桌面壁纸和锁屏壁纸
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
    private final DataAggregator aggregator;
    private final CollectionConfig config;
    private final OkHttpClient httpClient;
    private volatile boolean isGenerating = false;

    public WallpaperGenerationManager(Context context) {
        this.context = context.getApplicationContext();
        this.qwenClient = new QwenImageApiClient(context);
        this.aggregator = new DataAggregator(context);
        this.config = CollectionConfig.getInstance(context);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public boolean isGenerating() {
        return isGenerating;
    }

    /**
     * Check if enough time has passed since last generation.
     */
    public boolean shouldGenerate() {
        if (!config.getBoolean(CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, true)) {
            return false;
        }
        long interval = config.getLong(CollectionConfig.KEY_WALLPAPER_GENERATION_INTERVAL_MS, 3600_000L);
        long lastTime = config.getLong(CollectionConfig.KEY_LAST_WALLPAPER_GENERATION_TIME, 0);
        return System.currentTimeMillis() - lastTime >= interval;
    }

    /**
     * Run the full wallpaper generation pipeline on a background thread.
     */
    public void generateAndSetWallpaper(WallpaperGenerationCallback callback) {
        if (isGenerating) {
            callback.onError("正在生成中，请稍候");
            return;
        }
        isGenerating = true;

        new Thread(() -> {
            try {
                // Step 1: Aggregate usage data
                callback.onProgress("正在收集使用数据...");
                int windowHours = config.getInt(CollectionConfig.KEY_AGGREGATION_WINDOW_HOURS, 24);
                JSONObject aggregatedData = aggregator.aggregateRecentData(windowHours);

                long screenTimeMs = 0;
                JSONObject screenUsage = aggregatedData.optJSONObject("screen_usage");
                if (screenUsage != null) {
                    screenTimeMs = screenUsage.optLong("today_screen_time_ms", 0);
                }
                MoodMapper.Mood mood = MoodMapper.fromScreenTime(screenTimeMs);

                // Step 2: Generate image prompt via DeepSeek
                callback.onProgress("正在生成创意壁纸描述...");
                String imagePrompt = generateImagePrompt(aggregatedData, mood, screenTimeMs);
                if (imagePrompt == null) {
                    callback.onError("无法生成壁纸描述（DeepSeek API失败）");
                    return;
                }
                Log.i(TAG, "Generated image prompt: " + imagePrompt);

                // Step 3: Generate image via Qwen-Image (synchronous call)
                callback.onProgress("正在生成壁纸图片...");
                final Bitmap[] resultBitmap = {null};
                final String[] resultError = {null};

                qwenClient.generateImage(imagePrompt,
                        "低分辨率，低画质，画面过饱和，蜡像感，文字，水印，logo，畸形",
                        "928*1664",  // 9:16 portrait — ideal for phone wallpaper
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

                // Step 4: Set wallpaper
                callback.onProgress("正在设置壁纸和锁屏...");
                setWallpaper(resultBitmap[0]);

                // Save a copy locally
                saveBitmapLocally(resultBitmap[0]);

                config.setLong(CollectionConfig.KEY_LAST_WALLPAPER_GENERATION_TIME,
                        System.currentTimeMillis());

                callback.onSuccess("壁纸已更新！心情状态：" + mood.labelCn);
                Log.i(TAG, "Wallpaper generation complete, mood: " + mood.name());

            } catch (Exception e) {
                Log.e(TAG, "Wallpaper generation failed", e);
                callback.onError("壁纸生成出错: " + e.getMessage());
            } finally {
                isGenerating = false;
            }
        }).start();
    }

    /**
     * Ask DeepSeek to produce a creative text-to-image prompt based on usage data.
     */
    private String generateImagePrompt(JSONObject aggregatedData, MoodMapper.Mood mood,
                                       long screenTimeMs) {
        if (!ApiConfig.isDeepSeekApiKeyConfigured()) {
            return buildFallbackPrompt(mood, screenTimeMs);
        }

        try {
            String moodFragment = MoodMapper.toImagePromptFragment(mood, screenTimeMs);

            String systemPrompt = "你是一个手机壁纸创意设计师。根据用户的手机使用数据，"
                    + "生成一段用于文生图模型的壁纸描述提示词。要求：\n"
                    + "1. 提示词应描述一幅适合手机竖屏壁纸的艺术画面\n"
                    + "2. 画面应含蓄地反映用户当前的数字健康状态\n"
                    + "3. 不要包含文字、logo、UI元素\n"
                    + "4. 提示词长度不超过400个字符\n"
                    + "5. 只返回提示词本身，不要任何其他说明文字\n"
                    + "6. 使用中文描述";

            String userContent = "用户手机使用状态：" + moodFragment + "\n\n"
                    + "使用数据摘要：\n" + summarizeDataForPrompt(aggregatedData);

            JSONObject requestJson = new JSONObject();
            requestJson.put("model", "deepseek-chat");

            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.put(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);
            messages.put(userMsg);

            requestJson.put("messages", messages);
            requestJson.put("max_tokens", 512);
            requestJson.put("temperature", 0.9);
            requestJson.put("stream", false);

            RequestBody body = RequestBody.create(
                    requestJson.toString(), MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(ApiConfig.DEEPSEEK_API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + ApiConfig.DEEPSEEK_API_KEY)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "DeepSeek prompt generation failed: " + response.code());
                    return buildFallbackPrompt(mood, screenTimeMs);
                }

                String respBody = response.body().string();
                JSONObject respJson = new JSONObject(respBody);
                JSONArray choices = respJson.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    String text = choices.getJSONObject(0)
                            .getJSONObject("message").getString("content").trim();
                    if (text.length() > 800) text = text.substring(0, 800);
                    return text;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error generating image prompt via DeepSeek", e);
        }

        return buildFallbackPrompt(mood, screenTimeMs);
    }

    private String summarizeDataForPrompt(JSONObject data) {
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject screenUsage = data.optJSONObject("screen_usage");
            if (screenUsage != null) {
                sb.append("今日屏幕使用：")
                        .append(screenUsage.optString("today_screen_time_readable", "未知"))
                        .append("\n");
                String fgApp = screenUsage.optString("foreground_app_package", "");
                if (!fgApp.isEmpty()) {
                    sb.append("当前应用：").append(fgApp).append("\n");
                }
            }

            JSONObject actSummary = data.optJSONObject("activity_summary");
            if (actSummary != null && actSummary.length() > 0) {
                sb.append("活动模式：").append(actSummary.toString()).append("\n");
            }

            int totalCollections = data.optInt("total_collections", 0);
            sb.append("数据采集次数：").append(totalCollections).append("\n");

            JSONObject locSummary = data.optJSONObject("location_summary");
            if (locSummary != null) {
                sb.append("位置点数：").append(locSummary.optInt("unique_points", 0)).append("\n");
            }
        } catch (Exception e) {
            sb.append("(数据解析异常)");
        }
        return sb.toString();
    }

    private String buildFallbackPrompt(MoodMapper.Mood mood, long screenTimeMs) {
        switch (mood) {
            case ENERGETIC:
                return "清新明亮的早晨风景画，阳光透过树叶洒落，"
                        + "远处是翠绿的山丘和蔚蓝天空，充满活力与希望的氛围，"
                        + "适合手机壁纸的竖屏构图，色调温暖明亮";
            case HAPPY:
                return "温馨舒适的午后花园场景，各色花朵盛开，"
                        + "蝴蝶在花丛中飞舞，柔和的光线营造愉悦的氛围，"
                        + "水彩风格，适合手机壁纸的竖屏构图";
            case NEUTRAL:
                return "宁静的湖面倒映着天空和远山，薄雾缭绕，"
                        + "几只白鹤在水面上悠然飞翔，平和安详的意境，"
                        + "中国水墨风格，适合手机壁纸的竖屏构图";
            case CONCERNED:
                return "夕阳西下的海边沙滩，温暖的橙色光芒铺满大地，"
                        + "远处的灯塔发出柔和的光，提醒归家的方向，"
                        + "油画风格，适合手机壁纸的竖屏构图";
            case TIRED:
                return "深蓝色的星空下，一棵古老的大树静静矗立，"
                        + "萤火虫在树下点点闪烁，月光柔和地照亮小路，"
                        + "梦幻治愈风格，适合手机壁纸的竖屏构图";
            case EXHAUSTED:
            default:
                return "深夜中安静的小镇，万家灯火逐渐熄灭，"
                        + "只剩月光和星辰陪伴，远处传来轻柔的摇篮曲，"
                        + "温柔治愈的深色调画面，适合手机壁纸的竖屏构图";
        }
    }

    private void setWallpaper(Bitmap bitmap) throws IOException {
        WallpaperManager wm = WallpaperManager.getInstance(context);

        // Set home screen wallpaper
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
        } else {
            wm.setBitmap(bitmap);
        }

        Log.i(TAG, "Wallpaper set successfully (home + lock screen)");
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

            // Keep only last 5 wallpapers to save storage
            cleanupOldWallpapers(wallpaperDir, 5);

            Log.d(TAG, "Wallpaper saved locally: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "Failed to save wallpaper locally", e);
        }
    }

    private void cleanupOldWallpapers(File dir, int keepCount) {
        File[] files = dir.listFiles((d, name) -> name.startsWith("wallpaper_") && name.endsWith(".png"));
        if (files == null || files.length <= keepCount) return;

        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keepCount; i < files.length; i++) {
            files[i].delete();
        }
    }

    public void shutdown() {
        qwenClient.shutdown();
        httpClient.dispatcher().executorService().shutdown();
    }
}
