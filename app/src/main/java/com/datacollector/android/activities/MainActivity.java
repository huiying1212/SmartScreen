package com.datacollector.android.activities;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.TimePickerDialog;
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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.datacollector.android.services.FloatingOverlayService;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.MoodMapper;
import com.datacollector.android.utils.UnconsciousUsageTracker;

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

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_USAGE_STATS = 1002;

    private CollectionConfig config;
    private WallpaperGenerationManager wallpaperManager;
    private ScreenUsageCollector screenUsageCollector;
    private DeepSeekApiClient deepSeekClient;
    private UnconsciousUsageTracker uutTracker;
    private Handler uiHandler;

    // Module 1: Basic Info
    private TextView tvReminderStatus, tvReminderResult;
    private Switch switchOverlay, switchWallpaper;

    // Module 2: Personal Settings
    private LinearLayout styleChipsContainer, iconGridContainer;
    private TextView tvTimeSlot1, tvTimeSlot2, tvTimeSlot3;
    private EditText etPersonalGoal;
    private Button btnSaveGoal;
    private TextView tvGenerationStatus;

    // Module 3: System Settings
    private Switch switchLocation, switchActivity, switchScreenUsage, switchCalendar, switchAutoAnalysis;
    private Button btnPermOverlay, btnPermUsage, btnStartCollection;
    private LinearLayout historyContainer;
    private Button btnTestData, btnTestAi, btnTestWallpaper, btnTestBubblePrompt, btnTestWallpaperPrompt;
    private TextView tvTestOutput;

    // Style / Icon selection state
    private static final String[][] WALLPAPER_STYLES = {
            {"唯美艺术", "风格唯美具有艺术感，色彩丰富细腻"},
            {"水墨国风", "中国水墨画风格，黑白灰为主调，留白意境深远"},
            {"印象派",   "印象派油画风格，笔触明显，光影交织变幻"},
            {"极简主义", "极简主义风格，简洁线条，大面积纯色留白"},
            {"自然风光", "写实自然风光摄影风格，高清细腻逼真"},
            {"赛博朋克", "赛博朋克风格，霓虹灯光，暗色调未来感"}
    };

    private static final int[] ICON_DRAWABLES = {
            R.drawable.mood_happy, R.drawable.mood_energetic, R.drawable.mood_neutral,
            R.drawable.mood_concerned, R.drawable.mood_tired, R.drawable.mood_exhausted,
            R.drawable.mood_love, R.drawable.mood_thinking,
            R.drawable.mood_sleeping, R.drawable.mood_surprised
    };

    private static final String[] ICON_LABELS = {
            "开心", "活力", "平静", "担忧", "疲惫",
            "崩溃", "爱心", "思考", "睡眠", "惊讶"
    };

    private int selectedStyleIndex = 0;
    private int selectedIconIndex = 0;
    private final List<TextView> styleChipViews = new ArrayList<>();
    private final List<View> iconItemViews = new ArrayList<>();

    // Service
    private DataCollectionService dataCollectionService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DataCollectionService.DataCollectionBinder binder =
                    (DataCollectionService.DataCollectionBinder) service;
            dataCollectionService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            dataCollectionService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_setup);

        config = CollectionConfig.getInstance(this);
        wallpaperManager = new WallpaperGenerationManager(this);
        screenUsageCollector = new ScreenUsageCollector(this);
        deepSeekClient = new DeepSeekApiClient(this);
        uutTracker = new UnconsciousUsageTracker(this);
        uiHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupModule1();
        setupModule2();
        setupModule3();
        loadSavedState();
        autoGenerateReminder();

        startDataCollectionService();
        bindDataCollectionService();
    }

    // ── View Initialization ─────────────────────────────────────

    private void initViews() {
        tvReminderStatus = findViewById(R.id.tv_reminder_status);
        tvReminderResult = findViewById(R.id.tv_reminder_result);
        switchOverlay = findViewById(R.id.switch_overlay);
        switchWallpaper = findViewById(R.id.switch_wallpaper);

        styleChipsContainer = findViewById(R.id.style_chips_container);
        iconGridContainer = findViewById(R.id.icon_grid_container);
        tvTimeSlot1 = findViewById(R.id.tv_time_slot_1);
        tvTimeSlot2 = findViewById(R.id.tv_time_slot_2);
        tvTimeSlot3 = findViewById(R.id.tv_time_slot_3);

        etPersonalGoal = findViewById(R.id.et_personal_goal);
        btnSaveGoal = findViewById(R.id.btn_save_goal);

        switchLocation = findViewById(R.id.switch_location);
        switchActivity = findViewById(R.id.switch_activity);
        switchScreenUsage = findViewById(R.id.switch_screen_usage);
        switchCalendar = findViewById(R.id.switch_calendar);
        switchAutoAnalysis = findViewById(R.id.switch_auto_analysis);

        btnPermOverlay = findViewById(R.id.btn_perm_overlay);
        btnPermUsage = findViewById(R.id.btn_perm_usage);
        btnStartCollection = findViewById(R.id.btn_start_collection);
        historyContainer = findViewById(R.id.history_container);

        btnTestData = findViewById(R.id.btn_test_data);
        btnTestAi = findViewById(R.id.btn_test_ai);
        btnTestWallpaper = findViewById(R.id.btn_test_wallpaper);
        btnTestBubblePrompt = findViewById(R.id.btn_test_bubble_prompt);
        btnTestWallpaperPrompt = findViewById(R.id.btn_test_wallpaper_prompt);
        tvGenerationStatus = findViewById(R.id.tv_generation_status);
        tvTestOutput = findViewById(R.id.tv_test_output);
    }

    // ══════ Module 1: 基础信息 ══════

    private void setupModule1() {
        switchOverlay.setOnCheckedChangeListener((btn, checked) -> {
            config.setBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, checked);
            if (checked) startOverlayService(); else stopOverlayService();
        });

        switchWallpaper.setOnCheckedChangeListener((btn, checked) ->
                config.setBoolean(CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, checked));
    }

    private void autoGenerateReminder() {
        tvReminderStatus.setText("生成中...");
        tvReminderResult.setText("");

        new Thread(() -> {
            try {
                String currentApp = uutTracker.getCurrentPackage();
                if (currentApp == null) currentApp = "unknown";
                int uut = uutTracker.getUUT();
                int usageMins = 0;

                try {
                    JSONObject screenData = screenUsageCollector.collectData();
                    if (screenData != null) {
                        usageMins = (int) (screenData.optLong("foreground_app_current_open_ms", 0) / 60_000L);
                    }
                } catch (Exception ignored) {}

                final String text = deepSeekClient.generateBubbleText(currentApp, usageMins, uut, null);

                uiHandler.post(() -> {
                    tvReminderStatus.setVisibility(android.view.View.GONE);
                    tvReminderResult.setText(text);
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    tvReminderStatus.setText("生成失败");
                    tvReminderResult.setText(e.getMessage());
                    tvReminderResult.setTextColor(0xFFFF5252);
                });
            }
        }).start();
    }

    // ══════ Module 2: 个人设置 ══════

    private void setupModule2() {
        setupStyleChips();
        setupTimeSlots();
        setupIconGrid();
        setupPersonalGoal();
    }

    private void setupStyleChips() {
        String currentStyle = config.getString(CollectionConfig.KEY_WALLPAPER_STYLE, "唯美艺术");
        styleChipViews.clear();

        for (int i = 0; i < WALLPAPER_STYLES.length; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(WALLPAPER_STYLES[i][0]);
            chip.setTextSize(13);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);

            if (WALLPAPER_STYLES[i][0].equals(currentStyle)) {
                selectedStyleIndex = i;
                chip.setBackgroundResource(R.drawable.chip_selected_bg);
                chip.setTextColor(0xFF000000);
            } else {
                chip.setBackgroundResource(R.drawable.chip_unselected_bg);
                chip.setTextColor(0xFFB0B0B0);
            }

            chip.setOnClickListener(v -> selectStyle(idx));
            styleChipsContainer.addView(chip);
            styleChipViews.add(chip);
        }
    }

    private void selectStyle(int index) {
        selectedStyleIndex = index;
        config.setString(CollectionConfig.KEY_WALLPAPER_STYLE, WALLPAPER_STYLES[index][0]);

        for (int i = 0; i < styleChipViews.size(); i++) {
            TextView chip = styleChipViews.get(i);
            if (i == index) {
                chip.setBackgroundResource(R.drawable.chip_selected_bg);
                chip.setTextColor(0xFF000000);
            } else {
                chip.setBackgroundResource(R.drawable.chip_unselected_bg);
                chip.setTextColor(0xFFB0B0B0);
            }
        }
    }

    private void setupTimeSlots() {
        int[] slots = config.getWallpaperScheduleSlots();
        tvTimeSlot1.setText(formatMinuteOfDay(slots[0]));
        tvTimeSlot2.setText(formatMinuteOfDay(slots[1]));
        tvTimeSlot3.setText(formatMinuteOfDay(slots[2]));

        View slot1Parent = (View) tvTimeSlot1.getParent();
        View slot2Parent = (View) tvTimeSlot2.getParent();
        View slot3Parent = (View) tvTimeSlot3.getParent();

        slot1Parent.setOnClickListener(v -> showTimePicker(0, tvTimeSlot1,
                CollectionConfig.KEY_WALLPAPER_SCHEDULE_SLOT_1));
        slot2Parent.setOnClickListener(v -> showTimePicker(1, tvTimeSlot2,
                CollectionConfig.KEY_WALLPAPER_SCHEDULE_SLOT_2));
        slot3Parent.setOnClickListener(v -> showTimePicker(2, tvTimeSlot3,
                CollectionConfig.KEY_WALLPAPER_SCHEDULE_SLOT_3));
    }

    private void showTimePicker(int slotIndex, TextView display, String configKey) {
        int current = config.getInt(configKey,
                slotIndex == 0 ? 480 : slotIndex == 1 ? 720 : 1200);
        int hour = current / 60;
        int minute = current % 60;

        new TimePickerDialog(this, (view, h, m) -> {
            int minuteOfDay = h * 60 + m;
            config.setInt(configKey, minuteOfDay);
            display.setText(formatMinuteOfDay(minuteOfDay));
        }, hour, minute, true).show();
    }

    private String formatMinuteOfDay(int minuteOfDay) {
        return String.format(Locale.getDefault(), "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private void setupIconGrid() {
        selectedIconIndex = config.getInt(CollectionConfig.KEY_SELECTED_ICON_INDEX, 0);
        iconItemViews.clear();

        for (int i = 0; i < ICON_DRAWABLES.length; i++) {
            final int idx = i;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8), dp(8), dp(8), dp(8));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(88));
            lp.setMarginEnd(dp(8));
            item.setLayoutParams(lp);

            ImageView icon = new ImageView(this);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
            icon.setImageResource(ICON_DRAWABLES[i]);
            item.addView(icon);

            TextView label = new TextView(this);
            label.setText(ICON_LABELS[i]);
            label.setTextSize(11);
            label.setTextColor(0xFFB0B0B0);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(4);
            label.setLayoutParams(labelLp);
            item.addView(label);

            if (i == selectedIconIndex) {
                item.setBackgroundResource(R.drawable.icon_selected_bg);
            } else {
                item.setBackgroundResource(R.drawable.icon_unselected_bg);
            }

            item.setOnClickListener(v -> selectIcon(idx));
            iconGridContainer.addView(item);
            iconItemViews.add(item);
        }
    }

    private void selectIcon(int index) {
        selectedIconIndex = index;
        config.setInt(CollectionConfig.KEY_SELECTED_ICON_INDEX, index);

        for (int i = 0; i < iconItemViews.size(); i++) {
            iconItemViews.get(i).setBackgroundResource(
                    i == index ? R.drawable.icon_selected_bg : R.drawable.icon_unselected_bg);
        }

        Toast.makeText(this, "已选择: " + ICON_LABELS[index], Toast.LENGTH_SHORT).show();
    }

    private void setupPersonalGoal() {
        String savedGoal = config.getString(CollectionConfig.KEY_USER_PERSONAL_GOAL, "");
        if (savedGoal != null && !savedGoal.isEmpty()) {
            etPersonalGoal.setText(savedGoal);
        }

        btnSaveGoal.setOnClickListener(v -> {
            String goal = etPersonalGoal.getText().toString().trim();
            config.setString(CollectionConfig.KEY_USER_PERSONAL_GOAL, goal);
            Toast.makeText(this, goal.isEmpty() ? "目标已清除" : "目标已保存", Toast.LENGTH_SHORT).show();
        });
    }

    private void generateWallpaperNow() {
        btnTestWallpaper.setEnabled(false);
        btnTestWallpaper.setText("生成中...");
        tvGenerationStatus.setText("");
        tvGenerationStatus.setTextColor(0xFFB0B0B0);

        wallpaperManager.generateAndSetWallpaper(
                new WallpaperGenerationManager.WallpaperGenerationCallback() {
                    @Override
                    public void onSuccess(String msg) {
                        uiHandler.post(() -> {
                            tvGenerationStatus.setText(msg);
                            tvGenerationStatus.setTextColor(0xFF03DAC5);
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
                            tvGenerationStatus.setTextColor(0xFFB0B0B0);
                        });
                    }
                });
    }

    // ══════ Module 3: 系统设置 ══════

    private void setupModule3() {
        setupDataCollectionToggles();
        setupPermissionButtons();
        setupTestButtons();
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
        switchAutoAnalysis.setOnCheckedChangeListener((btn, checked) ->
                config.setBoolean(CollectionConfig.KEY_AUTO_ANALYSIS, checked));
    }

    private void setupPermissionButtons() {
        btnPermOverlay.setOnClickListener(v -> requestOverlayPermission());
        btnPermUsage.setOnClickListener(v -> requestUsageStatsPermission());
        btnStartCollection.setOnClickListener(v -> {
            startDataCollectionService();
            Toast.makeText(this, "数据收集服务已启动", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupTestButtons() {
        btnTestData.setOnClickListener(v -> testGetData());
        btnTestAi.setOnClickListener(v -> testAiReminder());
        btnTestWallpaper.setOnClickListener(v -> generateWallpaperNow());
        btnTestBubblePrompt.setOnClickListener(v -> showBubblePromptStructure());
        btnTestWallpaperPrompt.setOnClickListener(v -> showWallpaperPromptStructure());
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
                String currentApp = uutTracker.getCurrentPackage();
                if (currentApp == null) currentApp = "unknown";
                int uut = uutTracker.getUUT();
                int usageMins = 0;
                try {
                    JSONObject screenData = screenUsageCollector.collectData();
                    if (screenData != null) {
                        usageMins = (int) (screenData.optLong("foreground_app_current_open_ms", 0) / 60_000L);
                    }
                } catch (Exception ignored) {}

                final String app = currentApp;
                final int mins = usageMins;
                final int uutVal = uut;

                String result = deepSeekClient.generateBubbleText(app, mins, uutVal, null);

                StringBuilder sb = new StringBuilder();
                sb.append("── AI 提醒测试结果 ──\n");
                sb.append("当前应用: ").append(app).append("\n");
                sb.append("使用时长: ").append(mins).append(" 分钟\n");
                sb.append("UUT 值: ").append(uutVal).append("/100\n");
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
        String goalLine = (userGoal != null && !userGoal.isEmpty()) ? "\nUser's personal goals:\n" + userGoal + "\n" : "";

        showTestOutput(
            "══ 图标提醒 Prompt 结构 ══\n\n" +
            "── System Prompt ──\n" +
            "You are a phone-use feedback assistant. " +
            "Based on the user's current phone usage, generate a short reminder.\n\n" +
            "Rules:\n" +
            "1. No more than 15 Chinese characters\n" +
            "2. Friendly but guiding tone\n" +
            "3. Return ONLY the reminder text, no explanation\n" +
            "4. Do NOT wrap in quotes\n" +
            "5. Each response must be different\n" +
            goalLine +
            "\n── User Content ──\n" +
            "User is on [{当前应用}], spent [{使用分钟}min], " +
            "unconscious-usage-index: {UUT}/100, calendar: [{日程信息}]. " +
            "Generate a short Chinese reminder.\n\n" +
            "── Parameters ──\n" +
            "max_tokens: 48\n" +
            "temperature: 0.95"
        );
    }

    private void showWallpaperPromptStructure() {
        String userGoal = config.getString(CollectionConfig.KEY_USER_PERSONAL_GOAL, "");
        String style = config.getString(CollectionConfig.KEY_WALLPAPER_STYLE, "唯美艺术");
        String styleDesc = getStyleDescription(style);

        String goalLine1 = (userGoal != null && !userGoal.isEmpty()) ? "用户个人目标：\n" + userGoal + "\n" : "";
        String goalLine2 = (userGoal != null && !userGoal.isEmpty()) ? "用户目标背景：" + userGoal + "\n" : "";

        showTestOutput(
            "══ 壁纸生成 Prompt 结构 ══\n\n" +
            "── 第一步：关键词提取 (DeepSeek) ──\n" +
            "System Prompt:\n" +
            "根据用户的手机使用数据，总结出最能代表用户当前状态的 3-4 个核心元素词。\n" +
            "规则：用中文顿号分隔...\n" +
            goalLine1 +
            "\nUser Content: 用户手机使用数据摘要 (聚合数据)\n" +
            "max_tokens: 64, temperature: 0.8\n\n" +
            "── 第二步：图像生成 (Qwen) ──\n" +
            "Prompt:\n" +
            "请创作一幅适合手机竖屏壁纸的隐喻性艺术画面。\n" +
            "核心元素词：{3个关键词}\n" +
            "情绪背景：{基于屏幕时长的心情描述}\n" +
            "风格要求：" + styleDesc + "\n" +
            goalLine2 +
            "要求：画面中融入以上元素的隐喻表达，不包含文字和 UI 元素，适合作为手机壁纸的高质量竖屏构图。\n\n" +
            "Negative Prompt: 低分辨率，低画质，画面过饱和...\n" +
            "图片尺寸: 928×1664"
        );
    }

    private void showTestOutput(String text) {
        tvTestOutput.setVisibility(View.VISIBLE);
        tvTestOutput.setText(text);
    }

    // ── Load saved state ────────────────────────────────────────

    private void loadSavedState() {
        switchOverlay.setChecked(config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true));
        switchWallpaper.setChecked(config.getBoolean(
                CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, true));

        switchLocation.setChecked(config.getBoolean(CollectionConfig.KEY_LOCATION_ENABLED, true));
        switchActivity.setChecked(config.getBoolean(CollectionConfig.KEY_ACTIVITY_ENABLED, true));
        switchScreenUsage.setChecked(config.getBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, true));
        switchCalendar.setChecked(config.getBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, true));
        switchAutoAnalysis.setChecked(config.getBoolean(CollectionConfig.KEY_AUTO_ANALYSIS, false));

        updatePermissionButtons();
        loadUsageHistory();
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

                Map<String, List<File>> grouped = new LinkedHashMap<>();
                for (File f : files) {
                    String dateStr = dateFormat.format(new Date(f.lastModified()));
                    grouped.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(f);
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
        tv.setTextColor(0xFF888888);
        tv.setTextSize(13);
        tv.setPadding(0, dp(8), 0, dp(8));
        historyContainer.addView(tv);
    }

    private void addHistoryDay(String date, List<File> wallpapers, SimpleDateFormat timeFormat) {
        TextView dateLabel = new TextView(this);
        dateLabel.setText(date + " (" + wallpapers.size() + " 张壁纸)");
        dateLabel.setTextColor(0xFFFFFFFF);
        dateLabel.setTextSize(14);
        dateLabel.setPadding(0, dp(8), 0, dp(4));
        historyContainer.addView(dateLabel);

        LinearLayout thumbRow = new LinearLayout(this);
        thumbRow.setOrientation(LinearLayout.HORIZONTAL);
        thumbRow.setPadding(0, 0, 0, dp(8));

        for (int i = 0; i < Math.min(wallpapers.size(), 3); i++) {
            File file = wallpapers.get(i);
            ImageView thumb = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(80), dp(140));
            lp.setMarginEnd(dp(8));
            thumb.setLayoutParams(lp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(0xFF2A2A3E);

            loadThumbnail(thumb, file);

            String time = timeFormat.format(new Date(file.lastModified()));
            thumb.setContentDescription(date + " " + time);
            thumbRow.addView(thumb);
        }

        historyContainer.addView(thumbRow);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(0xFF2A2A3E);
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

    // ── Service Management ──────────────────────────────────────

    private void startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            switchOverlay.setChecked(false);
            return;
        }
        Intent intent = new Intent(this, FloatingOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopOverlayService() {
        stopService(new Intent(this, FloatingOverlayService.class));
    }

    private void startDataCollectionService() {
        Intent intent = new Intent(this, DataCollectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void bindDataCollectionService() {
        Intent intent = new Intent(this, DataCollectionService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    // ── Permissions ─────────────────────────────────────────────

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

        if (config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true)
                && Settings.canDrawOverlays(this)) {
            startOverlayService();
        }
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

    // ── Utility ─────────────────────────────────────────────────

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    static String getStyleDescription(String styleName) {
        return CollectionConfig.getStyleDescriptionByName(styleName);
    }
}
