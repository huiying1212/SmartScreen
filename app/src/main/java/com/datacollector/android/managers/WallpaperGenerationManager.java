package com.datacollector.android.managers;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.api.QwenImageApiClient;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.processing.DataAggregator;
import com.datacollector.android.views.MoodFaceView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;

import java.util.concurrent.atomic.AtomicBoolean;

/**
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
    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private static final String ORIGINAL_WALLPAPER_FILE = "original_wallpaper.png";
    private static final Object WALLPAPER_LOCK = new Object();

    public WallpaperGenerationManager(Context context) {
        this.context = context.getApplicationContext();
        this.qwenClient = new QwenImageApiClient(context);
        this.deepSeekClient = new DeepSeekApiClient(context);
        this.aggregator = new DataAggregator(context);
        this.config = CollectionConfig.getInstance(context);
    }

    public boolean isGenerating() {
        return isGenerating.get();
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
        if (!isGenerating.compareAndSet(false, true)) {
            callback.onError("正在生成中，请稍候");
            return;
        }

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
                        "人物，人脸，人影，剪影，动物，低分辨率，低画质，画面过饱和，蜡像感，文字，水印，logo，畸形，魔幻，虚幻，卡通，动漫",
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
                backupOriginalWallpaperIfNeeded();
                synchronized (WALLPAPER_LOCK) {
                    setWallpaper(resultBitmap[0]);
                }
                saveBitmapLocally(resultBitmap[0]);

                config.setLong(CollectionConfig.KEY_LAST_WALLPAPER_GENERATION_TIME,
                        System.currentTimeMillis());

                callback.onSuccess("壁纸已更新！关键词：" + keywords);
                Log.i(TAG, "Wallpaper generation complete");

            } catch (Exception e) {
                Log.e(TAG, "Wallpaper generation failed", e);
                callback.onError("壁纸生成出错: " + e.getMessage());
            } finally {
                isGenerating.set(false);
            }
        }).start();
    }

    private String buildImagePrompt(String keywords) {
        String styleDesc = config.getWallpaperStyleDescription();

        StringBuilder prompt = new StringBuilder();
        prompt.append("请创作一幅适合手机竖屏壁纸的高度写实风景或静物画面，避免过分虚幻。\n");
        prompt.append("场景关键词：").append(keywords).append("\n");
        prompt.append("风格要求：").append(styleDesc).append("，注重光影的真实感和材质的写实细节\n");
        prompt.append("要求：画面中自然融入以上关键词所描绘的场景氛围，");
        prompt.append("【绝对不要包含任何人物、人脸、剪影或动物】，");
        prompt.append("不包含文字和 UI 元素，适合作为手机壁纸的高质量纯景物竖屏构图。");
        return prompt.toString();
    }

    private String getFallbackKeywords() {
        String[] fallbacks = {
                "窗边、阳光、咖啡杯",
                "书桌、台灯、绿植",
                "清晨、露珠、小路",
                "沙发、暖光、毛毯",
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

    /**
     * Called when the user toggles "反思壁纸" ON.
     * - Backup current wallpaper (once)
     * - If a generated wallpaper exists in the last 6 hours, apply the newest one
     * - Otherwise apply a themed placeholder wallpaper (brand color + face + "hi")
     */
    public void applyRecentOrPlaceholderWallpaperOnEnable() {
        try {
            backupOriginalWallpaperIfNeeded();
            Bitmap recent = loadLatestGeneratedWallpaperWithinMs(6L * 60 * 60 * 1000);
            if (recent != null) {
                synchronized (WALLPAPER_LOCK) {
                    setWallpaper(recent);
                }
                Log.i(TAG, "Applied latest generated wallpaper from last 6h");
                return;
            }
            Bitmap placeholder = buildPlaceholderWallpaper();
            synchronized (WALLPAPER_LOCK) {
                setWallpaper(placeholder);
            }
            Log.i(TAG, "Applied placeholder wallpaper (no recent generation)");
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply recent/placeholder wallpaper", e);
        }
    }

    private Bitmap loadLatestGeneratedWallpaperWithinMs(long windowMs) {
        try {
            File dir = new File(context.getExternalFilesDir(null), "wallpapers");
            if (!dir.exists() || !dir.isDirectory()) return null;
            File[] files = dir.listFiles((d, name) -> name.startsWith("wallpaper_") && name.endsWith(".png"));
            if (files == null || files.length == 0) return null;

            long now = System.currentTimeMillis();
            File best = null;
            long bestTs = -1;
            for (File f : files) {
                long ts = f.lastModified();
                if (now - ts > windowMs) continue;
                if (ts > bestTs) {
                    bestTs = ts;
                    best = f;
                }
            }
            if (best == null) return null;
            return android.graphics.BitmapFactory.decodeFile(best.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "Failed to load recent wallpaper", e);
            return null;
        }
    }

    private Bitmap buildPlaceholderWallpaper() {
        // Match system's desired wallpaper dimensions to avoid extra scaling blur.
        int w = 1080;
        int h = 1920;
        try {
            WallpaperManager wm = WallpaperManager.getInstance(context);
            int dw = wm.getDesiredMinimumWidth();
            int dh = wm.getDesiredMinimumHeight();
            if (dw > 0) w = dw;
            if (dh > 0) h = dh;
        } catch (Exception ignored) {}
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        int bg = 0xFFC3C2F2; // app theme accent
        canvas.drawColor(bg);

        // Render MoodFaceView into the bitmap
        MoodFaceView face = new MoodFaceView(context);
        String styleName = config.getString(CollectionConfig.KEY_FACE_STYLE, "CLASSIC");
        face.setFaceStyle(MoodFaceView.FaceStyle.fromName(styleName));
        face.setStressImmediate(0f);
        face.setGlobalAlpha(255);
        face.setFeaturesOnly(true);

        int faceSize = dpToPx(220);
        int spec = android.view.View.MeasureSpec.makeMeasureSpec(faceSize, android.view.View.MeasureSpec.EXACTLY);
        face.measure(spec, spec);
        face.layout(0, 0, faceSize, faceSize);

        int cx = w / 2;
        int cy = (int) (h * 0.42f);
        canvas.save();
        canvas.translate(cx - faceSize / 2f, cy - faceSize / 2f);
        face.draw(canvas);
        canvas.restore();

        // Add "hi"
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        paint.setTextSize(dpToPx(44));
        // Avoid shadow blur on some launchers' wallpaper scaling paths.
        canvas.drawText("hi", cx, cy + faceSize / 2f + dpToPx(56), paint);

        return bmp;
    }

    /** Apply the themed placeholder wallpaper immediately (for preview/debug). */
    public void applyPlaceholderWallpaperNow() {
        try {
            backupOriginalWallpaperIfNeeded();
            Bitmap placeholder = buildPlaceholderWallpaper();
            synchronized (WALLPAPER_LOCK) {
                setWallpaper(placeholder);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply placeholder wallpaper", e);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 备份用户原本的系统壁纸（只备份一次，保存在应用私有目录）。
     * 用于在“反思系统/反思壁纸关闭”时恢复。
     */
    public void backupOriginalWallpaperIfNeeded() {
        try {
            if (config.getBoolean(CollectionConfig.KEY_ORIGINAL_WALLPAPER_BACKED_UP, false)) return;
            WallpaperManager wm = WallpaperManager.getInstance(context);
            Drawable d = wm.getDrawable();
            if (d == null) return;

            // Cap dimensions to prevent OOM on devices with very large wallpapers
            final int MAX_DIM = 4096;
            int w = Math.max(1, d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 1080);
            int h = Math.max(1, d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 1920);
            if (w > MAX_DIM || h > MAX_DIM) {
                float scale = Math.min((float) MAX_DIM / w, (float) MAX_DIM / h);
                w = Math.round(w * scale);
                h = Math.round(h * scale);
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            d.draw(canvas);

            File out = new File(context.getFilesDir(), ORIGINAL_WALLPAPER_FILE);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 95, fos);
            }
            config.setBoolean(CollectionConfig.KEY_ORIGINAL_WALLPAPER_BACKED_UP, true);
            Log.i(TAG, "Original wallpaper backed up to " + out.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "Failed to backup original wallpaper", e);
        }
    }

    /**
     * 恢复备份的原壁纸；如果没有备份则不做任何事。
     */
    public void restoreOriginalWallpaperIfExists() {
        try {
            // If the reflection wallpaper feature is currently enabled, do NOT restore.
            // This avoids races where an async restore overrides a newly generated wallpaper.
            if (config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true)
                    && config.getBoolean(CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, true)) {
                return;
            }
            if (!config.getBoolean(CollectionConfig.KEY_ORIGINAL_WALLPAPER_BACKED_UP, false)) return;
            File f = new File(context.getFilesDir(), ORIGINAL_WALLPAPER_FILE);
            if (!f.exists()) return;
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp == null) return;
            synchronized (WALLPAPER_LOCK) {
                setWallpaper(bmp);
            }
            Log.i(TAG, "Original wallpaper restored");
        } catch (Exception e) {
            Log.w(TAG, "Failed to restore original wallpaper", e);
        }
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
