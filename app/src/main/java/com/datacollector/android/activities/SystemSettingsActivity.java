package com.datacollector.android.activities;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.Manifest;
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
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.datacollector.android.R;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.managers.WallpaperGenerationManager;
import com.datacollector.android.services.DataCollectionService;
import com.datacollector.android.services.FloatingOverlayService;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.UserInteractionLogger;
import com.datacollector.android.processing.LLMScoringEngine;

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
    private static final int REQUEST_USAGE_STATS = 1002;
    private static final int REQUEST_RUNTIME_PERMISSIONS = 1003;

    private CollectionConfig config;
    private UserInteractionLogger logger;
    private WallpaperGenerationManager wallpaperManager;
    private ScreenUsageCollector screenUsageCollector;
    private DeepSeekApiClient deepSeekClient;
    private LLMScoringEngine llmScoringEngine;
    private Handler uiHandler;

    private Switch switchLocation, switchActivity, switchScreenUsage, switchCalendar,
            switchWifi, switchBluetooth;
    private Button btnStartCollection;
    private LinearLayout historyContainer;
    private Button btnTestData, btnTestAi, btnTestWallpaper, btnTestBubblePrompt, btnTestWallpaperPrompt, btnTestScorePrompt;
    private Button btnPreviewInitialWallpaper;
    private TextView tvGenerationStatus, tvTestOutput;

    // Developer test gating
    private static final String DEV_TEST_PASSWORD = "011212";
    private LinearLayout devTestHeader;
    private TextView tvDevTestState;
    private LinearLayout devTestUnlockContainer;
    private EditText etDevTestPassword;
    private Button btnDevTestUnlock;
    private TextView tvDevTestUnlockHint;
    private LinearLayout devTestContent;
    private boolean devTestExpanded = false;
    private boolean devTestUnlockUiVisible = false;

    private boolean isUpdatingToggleUi = false;
    private PendingPermissionRequest pendingPermissionRequest = null;

    private enum PendingPermissionRequest {
        LOCATION,
        CALENDAR,
        WIFI,
        BLUETOOTH,
        SCREEN_USAGE
    }

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
        logger = UserInteractionLogger.get(this);
        logger.log("system_settings_open");
        wallpaperManager = new WallpaperGenerationManager(getApplicationContext());
        screenUsageCollector = new ScreenUsageCollector(getApplicationContext());
        deepSeekClient = new DeepSeekApiClient(getApplicationContext());
        llmScoringEngine = new LLMScoringEngine(getApplicationContext(), deepSeekClient);
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
        switchWifi = findViewById(R.id.switch_wifi);
        switchBluetooth = findViewById(R.id.switch_bluetooth);

        btnStartCollection = findViewById(R.id.btn_start_collection);
        historyContainer = findViewById(R.id.history_container);

        btnTestData = findViewById(R.id.btn_test_data);
        btnTestAi = findViewById(R.id.btn_test_ai);
        btnTestWallpaper = findViewById(R.id.btn_test_wallpaper);
        btnPreviewInitialWallpaper = findViewById(R.id.btn_preview_initial_wallpaper);
        btnTestBubblePrompt = findViewById(R.id.btn_test_bubble_prompt);
        btnTestWallpaperPrompt = findViewById(R.id.btn_test_wallpaper_prompt);
        btnTestScorePrompt = findViewById(R.id.btn_test_score_prompt);
        tvGenerationStatus = findViewById(R.id.tv_generation_status);
        tvTestOutput = findViewById(R.id.tv_test_output);

        devTestHeader = findViewById(R.id.dev_test_header);
        tvDevTestState = findViewById(R.id.tv_dev_test_state);
        devTestUnlockContainer = findViewById(R.id.dev_test_unlock_container);
        etDevTestPassword = findViewById(R.id.et_dev_test_password);
        btnDevTestUnlock = findViewById(R.id.btn_dev_test_unlock);
        tvDevTestUnlockHint = findViewById(R.id.tv_dev_test_unlock_hint);
        devTestContent = findViewById(R.id.dev_test_content);
    }

    private void setupDataCollectionToggles() {
        switchLocation.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingToggleUi) return;
            if (checked) {
                if (ensureLocationPermission()) {
                    config.setBoolean(CollectionConfig.KEY_LOCATION_ENABLED, true);
                    logger.log("collection_toggle", "type", "location", "enabled", true);
                } else {
                    // Permission flow started; keep config off until granted
                    config.setBoolean(CollectionConfig.KEY_LOCATION_ENABLED, false);
                    setToggleCheckedSafely(switchLocation, false);
                }
            } else {
                config.setBoolean(CollectionConfig.KEY_LOCATION_ENABLED, false);
                logger.log("collection_toggle", "type", "location", "enabled", false);
            }
        });

        // Activity recognition collector uses sensors and doesn't rely on a runtime permission.
        switchActivity.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingToggleUi) return;
            config.setBoolean(CollectionConfig.KEY_ACTIVITY_ENABLED, checked);
            logger.log("collection_toggle", "type", "activity", "enabled", checked);
        });

        switchScreenUsage.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingToggleUi) return;
            if (checked) {
                if (hasUsageStatsPermission()) {
                    config.setBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, true);
                    logger.log("collection_toggle", "type", "screen_usage", "enabled", true);
                } else {
                    // Launch system settings; only enable after user grants it
                    pendingPermissionRequest = PendingPermissionRequest.SCREEN_USAGE;
                    startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQUEST_USAGE_STATS);
                    config.setBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, false);
                    setToggleCheckedSafely(switchScreenUsage, false);
                    Toast.makeText(this, "请在系统设置中授予“使用情况访问”权限", Toast.LENGTH_SHORT).show();
                }
            } else {
                config.setBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, false);
                logger.log("collection_toggle", "type", "screen_usage", "enabled", false);
            }
        });

        switchCalendar.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingToggleUi) return;
            if (checked) {
                if (ensureCalendarPermission()) {
                    config.setBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, true);
                    logger.log("collection_toggle", "type", "calendar", "enabled", true);
                } else {
                    config.setBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, false);
                    setToggleCheckedSafely(switchCalendar, false);
                }
            } else {
                config.setBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, false);
                logger.log("collection_toggle", "type", "calendar", "enabled", false);
            }
        });

        switchWifi.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingToggleUi) return;
            if (checked) {
                if (ensureWifiPermission()) {
                    config.setBoolean(CollectionConfig.KEY_WIFI_ENABLED, true);
                    logger.log("collection_toggle", "type", "wifi", "enabled", true);
                } else {
                    config.setBoolean(CollectionConfig.KEY_WIFI_ENABLED, false);
                    setToggleCheckedSafely(switchWifi, false);
                }
            } else {
                config.setBoolean(CollectionConfig.KEY_WIFI_ENABLED, false);
                logger.log("collection_toggle", "type", "wifi", "enabled", false);
            }
        });

        switchBluetooth.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingToggleUi) return;
            if (checked) {
                if (ensureBluetoothPermission()) {
                    config.setBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, true);
                    logger.log("collection_toggle", "type", "bluetooth", "enabled", true);
                } else {
                    config.setBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, false);
                    setToggleCheckedSafely(switchBluetooth, false);
                }
            } else {
                config.setBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, false);
                logger.log("collection_toggle", "type", "bluetooth", "enabled", false);
            }
        });
    }

    private void setToggleCheckedSafely(Switch s, boolean checked) {
        isUpdatingToggleUi = true;
        try {
            s.setChecked(checked);
        } finally {
            isUpdatingToggleUi = false;
        }
    }

    private boolean ensureLocationPermission() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (fineGranted || coarseGranted) return true;

        pendingPermissionRequest = PendingPermissionRequest.LOCATION;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_RUNTIME_PERMISSIONS);
        return false;
    }

    private boolean ensureCalendarPermission() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (granted) return true;
        pendingPermissionRequest = PendingPermissionRequest.CALENDAR;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_CALENDAR},
                REQUEST_RUNTIME_PERMISSIONS);
        return false;
    }

    /**
     * WiFi SSID requires location permission on Android 8.1+ in many cases.
     * We reuse the location permission gate here to make the toggle behavior intuitive.
     */
    private boolean ensureWifiPermission() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (fineGranted) return true;
        pendingPermissionRequest = PendingPermissionRequest.WIFI;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_RUNTIME_PERMISSIONS);
        return false;
    }

    private boolean ensureBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean connectGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean scanGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (connectGranted && scanGranted) return true;
            pendingPermissionRequest = PendingPermissionRequest.BLUETOOTH;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_RUNTIME_PERMISSIONS);
            return false;
        } else {
            // Pre-Android 12: fine location is commonly required for scanning/identifying devices.
            boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (fineGranted) return true;
            pendingPermissionRequest = PendingPermissionRequest.BLUETOOTH;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_RUNTIME_PERMISSIONS);
            return false;
        }
    }

    private void setupPermissionButtons() {
        btnStartCollection.setOnClickListener(v -> {
            boolean enabled = config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true);
            if (enabled) {
                config.setBoolean(CollectionConfig.KEY_RI4SU_ENABLED, false);
                logger.log("service_toggle", "enabled", false);
                stopDataCollectionService();
                stopService(new Intent(this, FloatingOverlayService.class));
                Toast.makeText(this, "RI4SU 服务已关闭", Toast.LENGTH_SHORT).show();
            } else {
                config.setBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true);
                logger.log("service_toggle", "enabled", true);
                // Usage Stats is only required when "screen usage" collection is enabled.
                boolean screenUsageEnabled = config.getBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, true);
                if (screenUsageEnabled && !hasUsageStatsPermission()) {
                    pendingPermissionRequest = PendingPermissionRequest.SCREEN_USAGE;
                    startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQUEST_USAGE_STATS);
                    Toast.makeText(this, "请先在系统设置中授予“使用情况访问”权限", Toast.LENGTH_SHORT).show();
                    return;
                }
                startDataCollectionService();
                bindDataCollectionService();
                Toast.makeText(this, "RI4SU 服务已启动", Toast.LENGTH_SHORT).show();
            }
            updateServiceButton();
            applyGlobalEnabledState();
        });
        updateServiceButton();
    }

    private void setupTestButtons() {
        setupDevTestGateUi();

        btnTestData.setOnClickListener(v -> testGetData());
        btnTestAi.setOnClickListener(v -> testAiReminder());
        btnTestWallpaper.setOnClickListener(v -> generateWallpaperNow());
        btnPreviewInitialWallpaper.setOnClickListener(v -> previewInitialWallpaper());
        btnTestBubblePrompt.setOnClickListener(v -> showBubblePromptStructure());
        btnTestWallpaperPrompt.setOnClickListener(v -> showWallpaperPromptStructure());
        btnTestScorePrompt.setOnClickListener(v -> showScorePromptStructure());
    }

    private void setupDevTestGateUi() {
        if (devTestHeader == null || devTestContent == null || devTestUnlockContainer == null) return;

        boolean unlocked = config.getBoolean(CollectionConfig.KEY_DEV_TEST_UNLOCKED, false);
        if (unlocked) {
            devTestUnlockContainer.setVisibility(View.GONE);
            devTestUnlockUiVisible = false;
            setDevTestExpanded(false); // keep folded by default even when unlocked
        } else {
            devTestContent.setVisibility(View.GONE);
            devTestExpanded = false;
            tvDevTestState.setText("已折叠");
            devTestUnlockContainer.setVisibility(View.GONE);
            devTestUnlockUiVisible = false;
        }

        devTestHeader.setOnClickListener(v -> {
            boolean isUnlocked = config.getBoolean(CollectionConfig.KEY_DEV_TEST_UNLOCKED, false);
            if (!isUnlocked) {
                // Not unlocked: show inline unlock UI only after user taps header.
                setDevTestExpanded(false);
                if (devTestUnlockContainer != null) {
                    devTestUnlockUiVisible = !devTestUnlockUiVisible;
                    devTestUnlockContainer.setVisibility(devTestUnlockUiVisible ? View.VISIBLE : View.GONE);
                }
                if (etDevTestPassword != null) {
                    etDevTestPassword.requestFocus();
                }
                return;
            }
            setDevTestExpanded(!devTestExpanded);
        });

        if (btnDevTestUnlock != null) {
            btnDevTestUnlock.setOnClickListener(v -> attemptUnlockDevTest());
        }
    }

    private void attemptUnlockDevTest() {
        if (etDevTestPassword == null) return;
        String input = etDevTestPassword.getText() != null ? etDevTestPassword.getText().toString().trim() : "";

        if (DEV_TEST_PASSWORD.equals(input)) {
            config.setBoolean(CollectionConfig.KEY_DEV_TEST_UNLOCKED, true);
            if (tvDevTestUnlockHint != null) tvDevTestUnlockHint.setVisibility(View.GONE);
            devTestUnlockContainer.setVisibility(View.GONE);
            devTestUnlockUiVisible = false;
            setDevTestExpanded(true);
        } else {
            if (tvDevTestUnlockHint != null) {
                tvDevTestUnlockHint.setText("密码不正确");
                tvDevTestUnlockHint.setVisibility(View.VISIBLE);
            }
            setDevTestExpanded(false);
        }
    }

    private void setDevTestExpanded(boolean expanded) {
        devTestExpanded = expanded;
        if (devTestContent != null) devTestContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (tvDevTestState != null) tvDevTestState.setText(expanded ? "已展开" : "已折叠");
    }

    private void previewInitialWallpaper() {
        tvGenerationStatus.setText("正在设置初始占位壁纸...");
        tvGenerationStatus.setTextColor(0xFF8B89B8);
        new Thread(() -> {
            try {
                wallpaperManager.applyPlaceholderWallpaperNow();
                uiHandler.post(() -> {
                    tvGenerationStatus.setText("已设置初始占位壁纸");
                    tvGenerationStatus.setTextColor(0xFFC3C2F2);
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    tvGenerationStatus.setText("设置失败: " + e.getMessage());
                    tvGenerationStatus.setTextColor(0xFFFF5252);
                });
            }
        }).start();
    }

    private void loadSavedState() {
        setToggleCheckedSafely(switchLocation, config.getBoolean(CollectionConfig.KEY_LOCATION_ENABLED, true));
        setToggleCheckedSafely(switchActivity, config.getBoolean(CollectionConfig.KEY_ACTIVITY_ENABLED, true));
        setToggleCheckedSafely(switchScreenUsage, config.getBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, true));
        setToggleCheckedSafely(switchCalendar, config.getBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, true));
        setToggleCheckedSafely(switchWifi, config.getBoolean(CollectionConfig.KEY_WIFI_ENABLED, true));
        setToggleCheckedSafely(switchBluetooth, config.getBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, true));

        applyGlobalEnabledState();
        loadUsageHistory();
    }

    private void applyGlobalEnabledState() {
        boolean globalEnabled = config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true);
        if (!globalEnabled) {
            new Thread(() -> wallpaperManager
                    .restoreOriginalWallpaperIfExists()).start();
            // Force all collection toggles off when globally disabled.
            config.setBoolean(CollectionConfig.KEY_LOCATION_ENABLED, false);
            config.setBoolean(CollectionConfig.KEY_ACTIVITY_ENABLED, false);
            config.setBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, false);
            config.setBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, false);
            config.setBoolean(CollectionConfig.KEY_WIFI_ENABLED, false);
            config.setBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, false);

            setToggleCheckedSafely(switchLocation, false);
            setToggleCheckedSafely(switchActivity, false);
            setToggleCheckedSafely(switchScreenUsage, false);
            setToggleCheckedSafely(switchCalendar, false);
            setToggleCheckedSafely(switchWifi, false);
            setToggleCheckedSafely(switchBluetooth, false);
        }

        if (switchLocation != null) switchLocation.setEnabled(globalEnabled);
        if (switchActivity != null) switchActivity.setEnabled(globalEnabled);
        if (switchScreenUsage != null) switchScreenUsage.setEnabled(globalEnabled);
        if (switchCalendar != null) switchCalendar.setEnabled(globalEnabled);
        if (switchWifi != null) switchWifi.setEnabled(globalEnabled);
        if (switchBluetooth != null) switchBluetooth.setEnabled(globalEnabled);
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
                            logger.log("wallpaper_generated", "result", "success");
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
                            logger.log("wallpaper_generated", "result", "error");
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
        showTestOutput("正在生成 AI 提醒...");
        new Thread(() -> {
            try {
                // 尝试通过 Service 获取完整上下文快照
                JSONObject snapshot = null;
                if (serviceBound && dataCollectionService != null) {
                    try {
                        snapshot = dataCollectionService.getCompleteContextData();
                    } catch (Exception e) {
                        Log.w("SystemSettings", "Service snapshot failed, falling back", e);
                    }
                }

                // Service 不可用或返回 null 时降级：仅采集 screen_usage
                if (snapshot == null) {
                    snapshot = new JSONObject();
                    snapshot.put("timestamp", System.currentTimeMillis());
                    try {
                        JSONObject screenData = screenUsageCollector.collectData();
                        if (screenData != null) {
                            snapshot.put("screen_usage", screenData);
                            snapshot.put("foreground_app_package",
                                    screenData.optString("foreground_app_package", "unknown"));
                        }
                    } catch (Exception e) {
                        Log.w("SystemSettings", "Screen usage collection failed", e);
                    }
                }

                // 读取后台定时更新的 LLM 分数（不触发新的评分）
                int score = llmScoringEngine.getScore();
                String scoreReason = llmScoringEngine.getLastReason();

                // 生成 AI 提醒（与首页、浮窗使用同一个 generateBubbleText 路径）
                String reminder = deepSeekClient.generateBubbleText(snapshot, score);

                StringBuilder sb = new StringBuilder();
                sb.append("── 当前 LLM 评分 ──\n");
                sb.append("得分: ").append(score).append("/100\n");
                if (scoreReason != null && !scoreReason.isEmpty()) {
                    sb.append("原因: ").append(scoreReason).append("\n");
                }
                sb.append("\n── AI 提醒 ──\n");
                sb.append("当前应用: ").append(snapshot.optString("foreground_app_package", "unknown")).append("\n");
                sb.append("AI 回复: ").append(reminder);

                uiHandler.post(() -> showTestOutput(sb.toString()));
            } catch (Exception e) {
                uiHandler.post(() -> showTestOutput("错误: " + e.getMessage()));
            }
        }).start();
    }

    private void showBubblePromptStructure() {
        String userGoal = config.getString(CollectionConfig.KEY_USER_PERSONAL_GOAL, "");
        boolean hasGoal = userGoal != null && !userGoal.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("══ 图标提醒 Prompt 结构 ══\n\n");

        sb.append("── System Prompt（与 DeepSeekApiClient.generateBubbleText 一致） ──\n");
        sb.append("你是一个手机使用反馈助手，语气温和、像朋友一样关心用户。\n");
        sb.append("根据用户当前的手机使用情况，生成一段简短的中文提醒。\n\n");
        sb.append("你会收到用户手机的实时采集数据（JSON），包含屏幕使用、位置、活动状态、日历、天气、WiFi、蓝牙等信息。\n");
        sb.append("请综合这些信息来理解用户当前的场景和状态。\n\n");
        sb.append("提醒内容分为两部分：\n");
        sb.append("1. 使用小结：用一两句话概括用户当前的状态（在用什么、用了多久、在哪里、在做什么等）\n");
        sb.append("2. 建议：结合用户的完整使用情况");
        if (hasGoal) sb.append("和用户设定的个人目标");
        sb.append("，给出一条友善、有针对性的建议\n\n");
        sb.append("格式要求：\n");
        sb.append("- 总字数控制在 30～60 字之间\n");
        sb.append("- 两部分之间用换行分隔\n");
        sb.append("- 不要加标题、编号或引号\n");
        sb.append("- 语气亲切自然，不要说教\n");
        sb.append("- 每次回复要有变化，不要重复");
        if (hasGoal) {
            sb.append("\n\n用户设定的个人目标：\n").append(userGoal.trim());
            sb.append("\n（请在建议部分适当结合此目标，但不要每次都生硬提及，自然融入即可）");
        } else {
            sb.append("\n\n（用户未设置个人目标）");
        }

        sb.append("\n── User Content ──\n");
        sb.append("以下是用户手机的实时采集数据：\n");
        sb.append("{完整采集数据 JSON（实际发送前会做脱敏：DataSanitizer.sanitizeSnapshot）}\n\n");
        sb.append("当前使用状态评分：{score}/100\n");
        sb.append("（评分由 AI 根据使用行为持续评估，分数越高表示越可能处于无意识/过度使用状态）\n");
        sb.append("请生成提醒。(t={timestamp_ms})\n\n");

        sb.append("── Parameters ──\n");
        sb.append("max_tokens: 120\n");
        sb.append("temperature: 0.95");

        showTestOutput(sb.toString());
    }

    private void showWallpaperPromptStructure() {
        String styleDesc = config.getWallpaperStyleDescription();
        String weightDesc = config.getUserPreferenceDescription();

        StringBuilder sb = new StringBuilder();
        sb.append("══ 壁纸生成 Prompt 结构 ══\n\n");

        sb.append("── 第一步：场景关键词提取 (DeepSeek) ──\n");
        sb.append("System Prompt（与 DeepSeekApiClient.extractKeywords 一致）:\n");
        sb.append("你是一位擅长场景化表达的创意概念提炼师。");
        sb.append("根据用户近几小时的手机使用数据，提炼出最能描绘用户这段时间生活场景的纯景物关键词。\n\n");
        sb.append("规则：\n");
        sb.append("1. 关键词数量不限，通常 3-6 个，视数据丰富程度而定\n");
        sb.append("2. 关键词必须是具体的、有画面感的事物或静物场景元素，且【绝对不能包含人物、人群或任何生物】\n");
        sb.append("3. 场景需注重“写实感”和“环境氛围”，避免任何魔幻、超现实或抽象元素\n");
        sb.append("4. 例如：如果用户在工作，可以是「办公桌、键盘、半杯咖啡、百叶窗透过的光」；");
        sb.append("如果以娱乐为主，可以是「舒适的沙发、亮着的屏幕、零食、室内暖光」；");
        sb.append("如果在运动，可以是「空旷的跑道、阳光、树影、运动水壶」\n");
        sb.append("5. 用中文顿号分隔，不要输出任何解释，只输出关键词\n\n");
        sb.append("偏好权重说明：\n").append(weightDesc).append("\n\n");
        sb.append("User Content: 用户手机使用数据摘要（实际发送前会脱敏：DataSanitizer.sanitizeAggregatedData）\n");
        sb.append("max_tokens: 100, temperature: 0.8\n\n");

        sb.append("── 第二步：图像生成 (Qwen) ──\n");
        sb.append("Prompt:\n");
        sb.append("请创作一幅适合手机竖屏壁纸的高度写实风景或静物画面，避免过分虚幻。\n");
        sb.append("场景关键词：{提炼出的关键词}\n");
        sb.append("风格要求：").append(styleDesc).append("，注重光影的真实感和材质的写实细节\n");
        sb.append("要求：画面中自然融入以上关键词所描绘的场景氛围，");
        sb.append("【绝对不要包含任何人物、人脸或剪影】，");
        sb.append("不包含文字和 UI 元素，适合作为手机壁纸的高质量纯景物竖屏构图。\n\n");
        sb.append("Negative Prompt: 人物，人脸，人影，剪影，低分辨率，低画质，画面过饱和，蜡像感，文字，水印，logo，畸形，魔幻，虚幻，卡通，动漫\n");
        sb.append("图片尺寸: 928*1664");

        showTestOutput(sb.toString());
    }

    private void showScorePromptStructure() {
        StringBuilder sb = new StringBuilder();
        sb.append("══ LLM 评分 Prompt 结构 ══\n\n");
        sb.append("对应代码：DeepSeekApiClient.assessUsageScore()\n\n");

        sb.append("── System Prompt ──\n");
        sb.append("你是一个手机使用行为评估引擎。你的任务是根据用户手机的实时采集数据，评估用户当前的「过度/无意识使用程度」并给出分数增量。\n\n");
        sb.append("## 评分规则\n");
        sb.append("分数范围 0-100。0 = 完全有意识/健康使用，100 = 极度无意识/沉迷使用。\n");
        sb.append("你每次返回一个 delta（增量），而非绝对分数。delta 范围 [-5, +5]。\n");
        sb.append("本系统每约 2 分钟调用你一次。\n\n");
        sb.append("## 重要说明\n");
        sb.append("时间间隔导致的分数调整（如长时间未使用手机）已由系统在调用你之前自动处理，");
        sb.append("你收到的「当前分数」已经反映了这些调整。\n");
        sb.append("你只需根据本次快照与上次快照之间的行为变化来判断 delta，无需再考虑时间间隔本身。\n\n");
        sb.append("## 默认行为\n");
        sb.append("默认情况下 delta = +1（即用户正常使用手机，分数缓慢上升）。\n");
        sb.append("只有当你判断情况明显偏离「普通使用」时，才应给出不同的 delta。\n");
        sb.append("如果 delta ≠ +1，你必须在 reason 中说明为什么偏离默认值。\n");
        sb.append("如果 delta = +1（默认），reason 可以为空字符串。\n\n");
        sb.append("## delta 判定标准\n");
        sb.append("- 普通使用（无明显好坏信号）→ delta = +1（默认，无需解释）\n");
        sb.append("- 生产力/工具类 App（办公、学习、编程、阅读、地图、银行等）→ delta = 0（reason: 说明在做什么）\n");
        sb.append("- 屏幕关闭 / 用户主动休息 / 刚解锁还没开始用 → delta = -1 到 -3（reason: 说明休息情况）\n");
        sb.append("- 娱乐/社交 App 持续使用（短视频、社交媒体、游戏等）→ delta = +2（reason: 说明在用什么）\n");
        sb.append("- 深夜（22:00-06:00）使用娱乐 App → delta = +3 到 +4（reason: 说明深夜使用情况）\n");
        sb.append("- 多个无意识信号叠加（深夜 + 长时间娱乐 + 高频切换 + 忽略日程）→ delta 最高 +5（reason: 说明叠加了哪些信号）\n");
        sb.append("- 用户正在做与日历日程相关的事 → delta = 0 或 -1（reason: 说明与日程的关联）\n");
        sb.append("- 用户在通勤/移动中短暂使用 → delta = +1（默认）\n\n");
        sb.append("## 综合考量因素\n");
        sb.append("你会收到完整的手机采集数据，包括：屏幕使用（当前 App、使用时长、今日总时长）、");
        sb.append("位置、活动状态（静止/步行/驾车）、日历日程、天气、WiFi、蓝牙设备等。\n");
        sb.append("请综合所有信息判断用户的使用意图和场景，不要只看单一指标。\n\n");
        sb.append("## 输出格式\n");
        sb.append("严格返回 JSON，不要包含任何其他文字：\n");
        sb.append("{\"delta\": <整数, -5到+5>, \"reason\": \"<delta≠+1时给出一句话中文理由, 20字以内; delta=+1时可为空>\"}\n\n");

        sb.append("── User Content ──\n");
        sb.append("当前分数：{currentScore}/100\n\n");
        sb.append("上一次采集数据：\n");
        sb.append("{lastSnapshotJson（如果存在，实际发送前会脱敏：DataSanitizer.sanitizeSnapshot）}\n\n");
        sb.append("上一次评估理由：{lastReason（如果存在）}\n\n");
        sb.append("本次最新采集数据：\n");
        sb.append("{newSnapshot（实际发送前会脱敏：DataSanitizer.sanitizeSnapshot）}\n\n");
        sb.append("请评估并返回 JSON。\n\n");

        sb.append("── Parameters ──\n");
        sb.append("max_tokens: 80\n");
        sb.append("temperature: 0.3\n");

        showTestOutput(sb.toString());
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
        boolean enabled = config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true);
        btnStartCollection.setText(enabled ? "关闭 RI4SU 服务" : "启动 RI4SU 服务");
    }

    private void bindDataCollectionService() {
        Intent intent = new Intent(this, DataCollectionService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    // ── Lifecycle ───────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        ensureOverlayServiceRunning();
        applyGlobalEnabledState();
        syncTogglesWithSystemPermissions();
        // Keep toggles consistent with system-granted permissions for flows that require Settings screens.
        if (switchScreenUsage != null) {
            boolean usageGranted = hasUsageStatsPermission();
            boolean enabled = config.getBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, true);
            if (usageGranted && pendingPermissionRequest == PendingPermissionRequest.SCREEN_USAGE) {
                config.setBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, true);
                enabled = true;
            } else if (!usageGranted) {
                // Can't function without permission; reflect that in UI + config
                config.setBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, false);
                enabled = false;
            }
            setToggleCheckedSafely(switchScreenUsage, enabled);
        }
        if (pendingPermissionRequest == PendingPermissionRequest.SCREEN_USAGE) {
            pendingPermissionRequest = null;
        }
    }

    /**
     * If the user revokes a system permission in Android Settings,
     * reflect it immediately in our in-app "collection enabled" toggles.
     */
    private void syncTogglesWithSystemPermissions() {
        // Location
        boolean locationGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!locationGranted) {
            config.setBoolean(CollectionConfig.KEY_LOCATION_ENABLED, false);
            if (switchLocation != null) setToggleCheckedSafely(switchLocation, false);
        }

        // Calendar
        boolean calendarGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!calendarGranted) {
            config.setBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, false);
            if (switchCalendar != null) setToggleCheckedSafely(switchCalendar, false);
        }

        // WiFi (SSID access often depends on fine location)
        boolean wifiGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!wifiGranted) {
            config.setBoolean(CollectionConfig.KEY_WIFI_ENABLED, false);
            if (switchWifi != null) setToggleCheckedSafely(switchWifi, false);
        }

        // Bluetooth
        boolean btGranted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btGranted =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED
                            && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } else {
            // Pre-Android 12: fine location is commonly needed for scanning/identifying.
            btGranted =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        if (!btGranted) {
            config.setBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, false);
            if (switchBluetooth != null) setToggleCheckedSafely(switchBluetooth, false);
        }
    }

    private void ensureOverlayServiceRunning() {
        if (!config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true)) return;
        if (!config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true)) return;
        if (!Settings.canDrawOverlays(this)) return;
        Intent intent = new Intent(this, FloatingOverlayService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to ensure overlay service running", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RUNTIME_PERMISSIONS) return;

        boolean anyGranted = false;
        if (grantResults != null) {
            for (int r : grantResults) {
                if (r == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    anyGranted = true;
                    break;
                }
            }
        }

        PendingPermissionRequest req = pendingPermissionRequest;
        pendingPermissionRequest = null;

        if (req == null) return;

        switch (req) {
            case LOCATION:
                if (anyGranted) {
                    config.setBoolean(CollectionConfig.KEY_LOCATION_ENABLED, true);
                    setToggleCheckedSafely(switchLocation, true);
                } else {
                    config.setBoolean(CollectionConfig.KEY_LOCATION_ENABLED, false);
                    setToggleCheckedSafely(switchLocation, false);
                    Toast.makeText(this, "未授予位置权限，已关闭位置采集", Toast.LENGTH_SHORT).show();
                }
                break;
            case CALENDAR:
                if (anyGranted) {
                    config.setBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, true);
                    setToggleCheckedSafely(switchCalendar, true);
                } else {
                    config.setBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, false);
                    setToggleCheckedSafely(switchCalendar, false);
                    Toast.makeText(this, "未授予日历权限，已关闭日程采集", Toast.LENGTH_SHORT).show();
                }
                break;
            case WIFI:
                if (anyGranted) {
                    config.setBoolean(CollectionConfig.KEY_WIFI_ENABLED, true);
                    setToggleCheckedSafely(switchWifi, true);
                } else {
                    config.setBoolean(CollectionConfig.KEY_WIFI_ENABLED, false);
                    setToggleCheckedSafely(switchWifi, false);
                    Toast.makeText(this, "未授予所需权限，已关闭 WiFi 采集", Toast.LENGTH_SHORT).show();
                }
                break;
            case BLUETOOTH:
                if (anyGranted) {
                    config.setBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, true);
                    setToggleCheckedSafely(switchBluetooth, true);
                } else {
                    config.setBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, false);
                    setToggleCheckedSafely(switchBluetooth, false);
                    Toast.makeText(this, "未授予蓝牙相关权限，已关闭蓝牙采集", Toast.LENGTH_SHORT).show();
                }
                break;
            case SCREEN_USAGE:
                // handled via onActivityResult / onResume
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_USAGE_STATS) {
            // UI refresh handled in onResume() (toggle sync)
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
