package com.datacollector.android.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Switch;
import android.widget.TextView;

import com.datacollector.android.R;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.services.DataCollectionService;
import com.datacollector.android.services.FloatingOverlayService;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.UnconsciousUsageTracker;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private CollectionConfig config;
    private ScreenUsageCollector screenUsageCollector;
    private DeepSeekApiClient deepSeekClient;
    private UnconsciousUsageTracker uutTracker;
    private Handler uiHandler;

    // Module 1: Basic Info
    private TextView tvReminderStatus, tvReminderResult;
    private Switch switchOverlay, switchWallpaper;

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
        screenUsageCollector = new ScreenUsageCollector(this);
        deepSeekClient = new DeepSeekApiClient(this);
        uutTracker = new UnconsciousUsageTracker(this);
        uiHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupModule1();
        setupNavigation();
        loadSavedState();
        autoGenerateReminder();

        startDataCollectionService();
        bindDataCollectionService();
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

                final String text = deepSeekClient.generateBubbleText(currentApp, usageMins, uut, null, null);

                uiHandler.post(() -> {
                    tvReminderStatus.setVisibility(android.view.View.GONE);
                    tvReminderResult.setText(text != null ? text : "");
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

    // ══════ Navigation ══════

    private void setupNavigation() {
        findViewById(R.id.nav_personal_settings).setOnClickListener(v ->
                startActivity(new Intent(this, PersonalSettingsActivity.class)));

        findViewById(R.id.nav_system_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SystemSettingsActivity.class)));
    }

    // ── Load saved state ────────────────────────────────────────

    private void loadSavedState() {
        switchOverlay.setChecked(config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true));
        switchWallpaper.setChecked(config.getBoolean(
                CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, true));
    }

    // ── Service Management ──────────────────────────────────────

    private void startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
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

    // ── Lifecycle ───────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        if (config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true)
                && Settings.canDrawOverlays(this)) {
            startOverlayService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false; }
        if (deepSeekClient != null) deepSeekClient.shutdown();
    }
}
