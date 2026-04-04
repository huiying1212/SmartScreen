package com.datacollector.android.activities;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.datacollector.android.R;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.managers.WallpaperGenerationManager;
import com.datacollector.android.services.DataCollectionService;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.LLMScoringEngine;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SystemSettingsActivity extends Activity {

    private static final String TAG = "SystemSettingsActivity";
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_USAGE_STATS = 1002;

    private CollectionConfig config;
    private WallpaperGenerationManager wallpaperManager;
    private ScreenUsageCollector screenUsageCollector;
    private DeepSeekApiClient deepSeekClient;
    private LLMScoringEngine llmScoringEngine;
    private Handler uiHandler;

    private Switch switchLocation, switchActivity, switchScreenUsage, switchCalendar;
    private Button btnPermOverlay, btnPermUsage, btnStartCollection;
    private LinearLayout historyContainer;
    private Button btnTestData, btnTestAi, btnTestWallpaper, btnTestBubblePrompt, btnTestWallpaperPrompt, btnTestMoodScore;
    private TextView tvGenerationStatus, tvTestOutput;

    private DataCollectionService dataCollectionService;
    private boolean serviceBound = false;
    private boolean serviceRunning = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DataCollectionService.DataCollectionBinder binder =
                    (DataCollectionService.DataCollectionBinder) service;
            dataCollectionService = binder.getService();
            serviceBound = true;
            serviceRunning = true;
            updateServiceButton();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            dataCollectionService = null;
            serviceRunning = false;
            updateServiceButton();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_settings);

        config = CollectionConfig.getInstance(this);
        wallpaperManager = new WallpaperGenerationManager(this);
        screenUsageCollector = new ScreenUsageCollector(this);
        deepSeekClient = new DeepSeekApiClient(this);
        llmScoringEngine = new LLMScoringEngine(this, deepSeekClient);
        uiHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupDataCollectionToggles();
        setupPermissionButtons();
        setupTestButtons();
        loadSavedState();

        bindDataCollectionService();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        switchLocation = findViewById(R.id.switch_location);
        switchActivity = findViewById(R.id.switch_activity);
        switchScreenUsage = findViewById(R.id.switch_screen_usage);
        switchCalendar = findViewById(R.id.switch_calendar);

        btnPermOverlay = findViewById(R.id.btn_perm_overlay);
        btnPermUsage = findViewById(R.id.btn_perm_usage);
        btnStartCollection = findViewById(R.id.btn_start_collection);
        historyContainer = findViewById(R.id.history_container);

        btnTestData = findViewById(R.id.btn_test_data);
        btnTestAi = findViewById(R.id.btn_test_ai);
        btnTestWallpaper = findViewById(R.id.btn_test_wallpaper);
        btnTestBubblePrompt = findViewById(R.id.btn_test_bubble_prompt);
        btnTestWallpaperPrompt = findViewById(R.id.btn_test_wallpaper_prompt);
        btnTestMoodScore = findViewById(R.id.btn_test_mood_score);
        tvGenerationStatus = findViewById(R.id.tv_generation_status);
        tvTestOutput = findViewById(R.id.tv_test_output);
    }

    private void setupDataCollectionToggles() {
        switchLocation.setOnCheckedChangeListener((btn, checked) ->
                config.setBoolean(CollectionConfig.KEY_LOCATION_ENABLED, checked));
        switchActivity.setOnCheckedChangeListener((btn, checked) ->
                config.setBoolean(CollectionConfig.KEY_ACTIVITY_ENABLED, checked));
        switchScreenUsage.setOnCheckedChangeListener((btn, checked) ->
                config.setBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, checked));
        switchCalendar.setOnCheckedChangeListener((btn, checked) ->
                config.setBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, checked));
    }

    private void setupPermissionButtons() {
        btnPermOverlay.setOnClickListener(v -> requestOverlayPermission());
        btnPermUsage.setOnClickListener(v -> requestUsageStatsPermission());
        btnStartCollection.setOnClickListener(v -> {
            if (serviceRunning) {
                stopDataCollectionService();
                Toast.makeText(this, "数据收集服务已关闭", Toast.LENGTH_SHORT).show();
            } else {
                if (!Settings.canDrawOverlays(this) || !hasUsageStatsPermission()) {
                    Toast.makeText(this, "请先授权所有必要权限", Toast.LENGTH_SHORT).show();
                    return;
                }
                startDataCollectionService();
                bindDataCollectionService();
                Toast.makeText(this, "数据收集服务已启动", Toast.LENGTH_SHORT).show();
            }
        });
        updateServiceButton();
    }

    private void setupTestButtons() {
        btnTestData.setOnClickListener(v -> testGetData());
        btnTestAi.setOnClickListener(v -> testAiReminder());
        btnTestWallpaper.setOnClickListener(v -> generateWallpaperNow());
        btnTestBubblePrompt.setOnClickListener(v -> showBubblePromptStructure());
        btnTestWallpaperPrompt.setOnClickListener(v -> showWallpaperPromptStructure());
        btnTestMoodScore.setOnClickListener(v -> testMoodScore());
    }

    private void loadSavedState() {
        switchLocation.setChecked(config.getBoolean(CollectionConfig.KEY_LOCATION_ENABLED, true));
        switchActivity.setChecked(config.getBoolean(CollectionConfig.KEY_ACTIVITY_ENABLED, true));
        switchScreenUsage.setChecked(config.getBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, true));
        switchCalendar.setChecked(config.getBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, true));

        updatePermissionButtons();
        loadUsageHistory();
    }

    private void generateWallpaperNow() {
        btnTestWallpaper.setEnabled(false);
        btnTestWallpaper.setText("生成中...");
        tvGenerationStatus.setText("");
        tvGenerationStatus.setTextColor(0xFF8B89B8);

        wallpaperManager.generateAndSetWallpaper(
                new WallpaperGenerationManager.WallpaperGenerationCallback() {
                    @Override
                    public void onSuccess(String msg) {
                        uiHandler.post(() -> {
                            tvGenerationStatus.setText(msg);
                            tvGenerationStatus.setTextColor(0xFFC3C2F2);
                            btnTestWallpaper.setEnabled(true);
                            btnTestWallpaper.setText("测试壁纸生成功能");
                            loadUsageHistory();
                        });
                    }

                    @Override
                    public void onError(String err) {
                        uiHandler.post(() -> {
                            tvGenerationStatus.setText(err);
                            tvGenerationStatus.setTextColor(0xFFFF5252);
                            btnTestWallpaper.setEnabled(true);
                            btnTestWallpaper.setText("测试壁纸生成功能");
                        });
                    }

                    @Override
                    public void onProgress(String s) {
                        uiHandler.post(() -> {
                            tvGenerationStatus.setText(s);
                            tvGenerationStatus.setTextColor(0xFF8B89B8);
                        });
                    }

                    @Override
                    public void onDebugInfo(String debugText) {
                        uiHandler.post(() -> showTestOutput(debugText));
                    }
                });
    }

    private void testGetData() {
        if (!serviceBound || dataCollectionService == null) {
            showTestOutput("服务未连接，请先启动数据收集服务");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject data = dataCollectionService.getCompleteContextData();
                String display = data != null ? data.toString(2) : "未获取到数据";
                uiHandler.post(() -> showTestOutput(display));
            } catch (Exception e) {
                uiHandler.post(() -> showTestOutput("错误: " + e.getMessage()));
            }
        }).start();
    }

    private void testAiReminder() {
        showTestOutput("正在调用 AI 生成提醒...");
        new Thread(() -> {
            try {
                // Build a snapshot like FloatingOverlayService does
                JSONObject snapshot = new JSONObject();
                snapshot.put("timestamp", System.currentTimeMillis());
                try {
                    JSONObject screenData = screenUsageCollector.collectData();
                    if (screenData != null) {
                        snapshot.put("screen_usage", screenData);
                        snapshot.put("foreground_app_package",
                                screenData.optString("foreground_app_package", "unknown"));
                    }
                } catch (Exception ignored) {}

                int score = llmScoringEngine.getScore();
                String result = deepSeekClient.generateBubbleText(snapshot, score);

                StringBuilder sb = new StringBuilder();
                sb.append("── AI 提醒测试结果 ──\n");
                sb.append("当前应用: ").append(snapshot.optString("foreground_app_package", "unknown")).append("\n");
                sb.append("LLM 评分: ").append(score).append("/100\n");
                sb.append("──────────────\n");
                sb.append("AI 回复: ").append(result);

                uiHandler.post(() -> showTestOutput(sb.toString()));
            } catch (Exception e) {
                uiHandler.post(() -> showTestOutput("错误: " + e.getMessage()));
            }
        }).start();
    }

    private void showBubblePromptStructure() {
        String userGoal = config.getString(CollectionConfig.KEY_USER_PERSONAL_GOAL, "");
        boolean hasGoal = userGoal != null && !userGoal.isEmpty();
        String goalSection = hasGoal
                ? "\n用户设定的个人目标：\n" + userGoal + "\n（请在建议部分适当结合此目标，但不要每次都生硬提及，自然融入即可）\n"
                : "\n（用户未设置个人目标）\n";

        showTestOutput(
            "══ 图标提醒 Prompt 结构 ══\n\n" +
            "── System Prompt ──\n" +
            "你是一个手机使用反馈助手，语气温和、像朋友一样关心用户。\n" +
            "根据用户当前的手机使用情况，生成一段简短的中文提醒。\n\n" +
            "提醒内容分为两部分：\n" +
            "1. 使用小结：用一两句话概括用户最近的屏幕使用情况\n" +
            "2. 建议：结合用户的使用情况" + (hasGoal ? "和用户设定的个人目标" : "") +
            "，给出一条友善、有针对性的建议\n\n" +
            "格式要求：\n" +
            "- 总字数控制在 30～60 字之间\n" +
            "- 不要加标题、编号或引号\n" +
            "- 语气亲切自然，不要说教\n" +
            goalSection +
            "\n── User Content ──\n" +
            "用户正在使用【{当前应用}】，已使用【{使用分钟}分钟】，" +
            "无意识使用指数：{UUT}/100，日程：【{日程信息}】，" +
            "天气：【{天气信息}】。请生成提醒。\n\n" +
            "── Parameters ──\n" +
            "max_tokens: 120\n" +
            "temperature: 0.95"
        );
    }

    private void showWallpaperPromptStructure() {
        String style = config.getString(CollectionConfig.KEY_WALLPAPER_STYLE, "唯美艺术");
        String styleDesc = CollectionConfig.getStyleDescriptionByName(style);

        showTestOutput(
            "══ 壁纸生成 Prompt 结构 ══\n\n" +
            "── 第一步：场景关键词提取 (DeepSeek) ──\n" +
            "System Prompt:\n" +
            "根据用户近几小时的手机使用数据，提炼出最能描绘用户这段时间生活场景的关键词。\n" +
            "关键词数量不限（通常 3-6 个），应为具体的、有画面感的事物或场景元素。\n" +
            "例如：工作场景→「电脑、咖啡、台灯」；娱乐场景→「沙发、零食、暖光」\n" +
            "用中文顿号分隔，只输出关键词。\n\n" +
            "User Content: 用户手机使用数据摘要 (聚合数据)\n" +
            "max_tokens: 100, temperature: 0.8\n\n" +
            "── 第二步：图像生成 (Qwen) ──\n" +
            "Prompt:\n" +
            "请创作一幅适合手机竖屏壁纸的隐喻性艺术画面。\n" +
            "场景关键词：{提炼出的关键词}\n" +
            "风格要求：" + styleDesc + "\n" +
            "要求：画面中自然融入以上关键词所描绘的场景氛围，不包含文字和 UI 元素，适合作为手机壁纸的高质量竖屏构图。\n\n" +
            "Negative Prompt: 低分辨率，低画质，画面过饱和...\n" +
            "图片尺寸: 928×1664"
        );
    }

    private void testMoodScore() {
        showTestOutput("正在请求 LLM 评分...");
        new Thread(() -> {
            try {
                // Build snapshot and run LLM assessment
                JSONObject snapshot = new JSONObject();
                snapshot.put("timestamp", System.currentTimeMillis());
                try {
                    JSONObject screenData = screenUsageCollector.collectData();
                    if (screenData != null) snapshot.put("screen_usage", screenData);
                } catch (Exception ignored) {}

                int oldScore = llmScoringEngine.getScore();
                int newScore = llmScoringEngine.assess(snapshot);
                String reason = llmScoringEngine.getLastReason();

                StringBuilder sb = new StringBuilder();
                sb.append("══ LLM 使用评分 ══\n\n");
                sb.append("评分前: ").append(oldScore).append("/100\n");
                sb.append("评分后: ").append(newScore).append("/100\n");
                sb.append("变化量: ").append(newScore - oldScore > 0 ? "+" : "").append(newScore - oldScore).append("\n");
                sb.append("理由: ").append(reason != null ? reason : "无").append("\n");

                String result = sb.toString();
                uiHandler.post(() -> showTestOutput(result));
            } catch (Exception e) {
                uiHandler.post(() -> showTestOutput("错误: " + e.getMessage()));
            }
        }).start();
    }
                uiHandler.post(() -> showTestOutput("错误: " + e.getMessage()));
            }
        }).start();
    }

    private void showTestOutput(String text) {
        tvTestOutput.setVisibility(View.VISIBLE);
        tvTestOutput.setText(text);
    }

    // ── Usage History ───────────────────────────────────────────

    private void loadUsageHistory() {
        historyContainer.removeAllViews();
        new Thread(() -> {
            try {
                File wallpaperDir = new File(getExternalFilesDir(null), "wallpapers");
                if (!wallpaperDir.exists() || !wallpaperDir.isDirectory()) {
                    uiHandler.post(() -> addHistoryPlaceholder("暂无壁纸生成记录"));
                    return;
                }

                File[] files = wallpaperDir.listFiles((d, name) ->
                        name.startsWith("wallpaper_") && name.endsWith(".png"));
                if (files == null || files.length == 0) {
                    uiHandler.post(() -> addHistoryPlaceholder("暂无壁纸生成记录"));
                    return;
                }

                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

                long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;

                Map<String, List<File>> grouped = new LinkedHashMap<>();
                for (File f : files) {
                    if (f.lastModified() < sevenDaysAgo) continue;
                    String dateStr = dateFormat.format(new Date(f.lastModified()));
                    grouped.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(f);
                }

                if (grouped.isEmpty()) {
                    uiHandler.post(() -> addHistoryPlaceholder("最近一周暂无壁纸生成记录"));
                    return;
                }

                uiHandler.post(() -> {
                    for (Map.Entry<String, List<File>> entry : grouped.entrySet()) {
                        addHistoryDay(entry.getKey(), entry.getValue(), timeFormat);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading history", e);
                uiHandler.post(() -> addHistoryPlaceholder("加载历史记录失败"));
            }
        }).start();
    }

    private void addHistoryPlaceholder(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF8B89B8);
        tv.setTextSize(13);
        tv.setPadding(0, dp(8), 0, dp(8));
        historyContainer.addView(tv);
    }

    private void addHistoryDay(String date, List<File> wallpapers, SimpleDateFormat timeFormat) {
        TextView dateLabel = new TextView(this);
        dateLabel.setText(date);
        dateLabel.setTextColor(0xFF2E2C50);
        dateLabel.setTextSize(14);
        dateLabel.setPadding(0, dp(8), 0, dp(4));
        historyContainer.addView(dateLabel);

        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout thumbRow = new LinearLayout(this);
        thumbRow.setOrientation(LinearLayout.HORIZONTAL);
        thumbRow.setPadding(0, 0, 0, dp(8));

        for (int i = 0; i < wallpapers.size(); i++) {
            File file = wallpapers.get(i);
            ImageView thumb = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(80), dp(140));
            lp.setMarginEnd(dp(8));
            thumb.setLayoutParams(lp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(0xFFEAE9F6);

            loadThumbnail(thumb, file);

            String time = timeFormat.format(new Date(file.lastModified()));
            thumb.setContentDescription(date + " " + time);
            thumbRow.addView(thumb);
        }

        scrollView.addView(thumbRow);
        historyContainer.addView(scrollView);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(0xFFE4E3F0);
        historyContainer.addView(divider);
    }

    private void loadThumbnail(ImageView imageView, File file) {
        new Thread(() -> {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 8;
                Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                if (bmp != null) {
                    uiHandler.post(() -> imageView.setImageBitmap(bmp));
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load thumbnail: " + file.getName());
            }
        }).start();
    }

    // ── Service & Permissions ────────────────────────────────────

    private void startDataCollectionService() {
        Intent intent = new Intent(this, DataCollectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        serviceRunning = true;
        updateServiceButton();
    }

    private void stopDataCollectionService() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
            dataCollectionService = null;
        }
        stopService(new Intent(this, DataCollectionService.class));
        serviceRunning = false;
        updateServiceButton();
    }

    private void updateServiceButton() {
        if (btnStartCollection == null) return;
        btnStartCollection.setText(serviceRunning ? "关闭数据收集服务" : "启动数据收集服务");
    }

    private void bindDataCollectionService() {
        Intent intent = new Intent(this, DataCollectionService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQUEST_OVERLAY_PERMISSION);
        } else {
            Toast.makeText(this, "悬浮窗权限已授权", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQUEST_USAGE_STATS);
        } else {
            Toast.makeText(this, "使用情况访问已授权", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void updatePermissionButtons() {
        boolean overlayOk = Settings.canDrawOverlays(this);
        btnPermOverlay.setText(overlayOk ? "悬浮窗权限 ✓" : "授权悬浮窗权限");
        btnPermOverlay.setEnabled(!overlayOk);

        boolean usageOk = hasUsageStatsPermission();
        btnPermUsage.setText(usageOk ? "使用情况访问 ✓" : "授权使用情况访问");
        btnPermUsage.setEnabled(!usageOk);
    }

    // ── Lifecycle ───────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionButtons();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION || requestCode == REQUEST_USAGE_STATS) {
            updatePermissionButtons();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false; }
        if (wallpaperManager != null) wallpaperManager.shutdown();
        if (deepSeekClient != null) deepSeekClient.shutdown();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
