package com.datacollector.android.activities;

import android.app.Activity;
import java.util.UUID;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.datacollector.android.R;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.collectors.CalendarDataCollector;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.collectors.WeatherDataCollector;
import com.datacollector.android.processing.ContextSnapshotCollector;
import com.datacollector.android.services.DataCollectionService;
import com.datacollector.android.services.FloatingOverlayService;
import com.datacollector.android.managers.WallpaperGenerationManager;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.ExperimentDataUploader;
import com.datacollector.android.utils.UserInteractionLogger;
import com.datacollector.android.processing.LLMScoringEngine;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_NOTIFICATIONS = 1201;
    public static final String ACTION_BUBBLE_TEXT_UPDATED =
            "com.datacollector.android.BUBBLE_TEXT_UPDATED";
    public static final String EXTRA_BUBBLE_TEXT = "bubble_text";

    private CollectionConfig config;
    private DeepSeekApiClient deepSeekClient;
    private LLMScoringEngine llmScoringEngine;
    private Handler uiHandler;
    private UserInteractionLogger logger;

    // ── 中层处理组件 ──
    private ContextSnapshotCollector snapshotCollector;
    private WallpaperGenerationManager wallpaperManager;

    // Module 1: Basic Info
    private TextView tvReminderStatus, tvReminderResult;
    private Switch switchOverlay, switchWallpaper;

    // Service
    private DataCollectionService dataCollectionService;
    private boolean serviceBound = false;

    private final BroadcastReceiver bubbleTextReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra(EXTRA_BUBBLE_TEXT);
            if (text != null && !text.isEmpty()) {
                tvReminderStatus.setVisibility(android.view.View.GONE);
                tvReminderResult.setText(text);
                tvReminderResult.setTextColor(0xFF6B69A0);
            }
        }
    };

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
        deepSeekClient = new DeepSeekApiClient(getApplicationContext());
        llmScoringEngine = new LLMScoringEngine(getApplicationContext(), deepSeekClient);
        wallpaperManager = new WallpaperGenerationManager(getApplicationContext());
        uiHandler = new Handler(Looper.getMainLooper());
        logger = UserInteractionLogger.get(this);

        logger.log("app_open");
        ensureNotificationPermissionIfNeeded();

        // 首次启动时弹出参与者 ID 输入框
        ensureParticipantId();

        // 启动实验数据上报器
        ExperimentDataUploader.get(this).start();

        // 初始化采集器和中层快照构建器
        ScreenUsageCollector screenUsageCollector = new ScreenUsageCollector(getApplicationContext());
        CalendarDataCollector calendarCollector = new CalendarDataCollector(getApplicationContext());
        WeatherDataCollector weatherCollector = new WeatherDataCollector(getApplicationContext());
        snapshotCollector = new ContextSnapshotCollector(getApplicationContext(),
                screenUsageCollector, calendarCollector, weatherCollector,
                null, null, null, null);

        initViews();
        setupModule1();
        setupNavigation();
        loadSavedState();

        // 仅在 RI4SU 启用时自动生成 AI 提醒
        if (config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true)) {
            autoGenerateReminder();
            startDataCollectionService();
            bindDataCollectionService();
        } else {
            tvReminderStatus.setText("服务未开启");
            tvReminderResult.setText("");
        }
    }

    private void ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS);
    }

    private void initViews() {
        tvReminderStatus = findViewById(R.id.tv_reminder_status);
        tvReminderResult = findViewById(R.id.tv_reminder_result);
        switchOverlay = findViewById(R.id.switch_overlay);
        switchWallpaper = findViewById(R.id.switch_wallpaper);
    }

    // ══════ Module 1: 基础信息 ══════

    private void setupModule1() {
        switchOverlay.setOnCheckedChangeListener((btn, checked) -> {
            config.setBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, checked);
            logger.log("toggle_overlay", "enabled", checked);
            if (checked) startOverlayService(); else stopOverlayService();
        });

        switchWallpaper.setOnCheckedChangeListener((btn, checked) -> {
                config.setBoolean(CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, checked);
                logger.log("toggle_wallpaper", "enabled", checked);
                if (checked) {
                    new Thread(() -> wallpaperManager
                            .applyRecentOrPlaceholderWallpaperOnEnable()).start();
                } else {
                    new Thread(() -> wallpaperManager
                            .restoreOriginalWallpaperIfExists()).start();
                }
        });
    }

    private void autoGenerateReminder() {
        tvReminderStatus.setText("生成中...");
        tvReminderResult.setText("");
        logger.log("reminder_generate_start");

        new Thread(() -> {
            try {
                // 通过中层统一快照构建器采集上下文
                JSONObject snapshot = snapshotCollector.collectFullSnapshot();

                int score = llmScoringEngine.getScore();
                final String text = deepSeekClient.generateBubbleText(snapshot, score);

                uiHandler.post(() -> {
                    tvReminderStatus.setVisibility(android.view.View.GONE);
                    tvReminderResult.setText(text != null ? text : "");
                    logger.log("reminder_generate_done", "score", score,
                            "text_length", text != null ? text.length() : 0);
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    tvReminderStatus.setText("生成失败");
                    tvReminderResult.setText(e.getMessage());
                    tvReminderResult.setTextColor(0xFFFF5252);
                    logger.log("reminder_generate_error", "error", e.getMessage());
                });
            }
        }).start();
    }

    // ══════ Navigation ══════

    private void setupNavigation() {
        findViewById(R.id.nav_personal_settings).setOnClickListener(v -> {
                logger.log("nav_personal_settings");
                startActivity(new Intent(this, PersonalSettingsActivity.class));
        });

        findViewById(R.id.nav_system_settings).setOnClickListener(v -> {
                logger.log("nav_system_settings");
                startActivity(new Intent(this, SystemSettingsActivity.class));
        });
    }

    // ── Load saved state ────────────────────────────────────────

    private void loadSavedState() {
        switchOverlay.setChecked(config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true));
        switchWallpaper.setChecked(config.getBoolean(
                CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, true));
        applyGlobalEnabledState();
    }

    private void applyGlobalEnabledState() {
        boolean globalEnabled = config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true);
        // When globally disabled, keep toggles off and non-interactive.
        if (!globalEnabled) {
            config.setBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, false);
            config.setBoolean(CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, false);
            switchOverlay.setChecked(false);
            switchWallpaper.setChecked(false);
            new Thread(() -> wallpaperManager
                    .restoreOriginalWallpaperIfExists()).start();
        }
        switchOverlay.setEnabled(globalEnabled);
        switchWallpaper.setEnabled(globalEnabled);
    }

    // ── Service Management ──────────────────────────────────────

    private void startOverlayService() {
        if (!config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true)) {
            switchOverlay.setChecked(false);
            config.setBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, false);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            // Jump to system settings to grant overlay permission.
            switchOverlay.setChecked(false);
            config.setBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, false);
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
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

    // ── 参与者 ID ────────────────────────────────────────────────

    private void ensureParticipantId() {
        String pid = config.getString(CollectionConfig.KEY_PARTICIPANT_ID, "");
        if (!pid.isEmpty()) return; // 已设置过

        // 自动生成唯一参与者 ID，无需用户手动输入
        String id = "U-" + UUID.randomUUID().toString().substring(0, 8);
        config.setString(CollectionConfig.KEY_PARTICIPANT_ID, id);
        logger.log("participant_registered", "participant_id", id);
        // 注册后立即触发一次上传
        ExperimentDataUploader.get(this).uploadNow();
    }

    // ── Lifecycle ───────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(bubbleTextReceiver, new IntentFilter(ACTION_BUBBLE_TEXT_UPDATED));
        applyGlobalEnabledState();
        if (!config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true)) {
            stopService(new Intent(this, FloatingOverlayService.class));
            return;
        }
        boolean overlayEnabled = config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true);
        boolean overlayGranted = Settings.canDrawOverlays(this);
        if (overlayEnabled && overlayGranted) {
            startOverlayService();
        } else if (overlayEnabled && !overlayGranted) {
            // Keep UI + config consistent with system permission.
            config.setBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, false);
            switchOverlay.setChecked(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(bubbleTextReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false; }
        if (wallpaperManager != null) wallpaperManager.shutdown();
        if (deepSeekClient != null) deepSeekClient.shutdown();
    }
}
