package com.datacollector.android.activities;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.datacollector.android.R;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.managers.WallpaperGenerationManager;
import com.datacollector.android.services.DataCollectionService;
import com.datacollector.android.services.FloatingOverlayService;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.MoodMapper;

import org.json.JSONObject;

/**
 * 主控制面板：管理悬浮图标、壁纸生成、权限请求和数据收集服务
 */
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_USAGE_STATS = 1002;

    private CollectionConfig config;
    private WallpaperGenerationManager wallpaperManager;
    private ScreenUsageCollector screenUsageCollector;
    private Handler uiHandler;

    // UI components
    private ImageView headerMoodIcon;
    private TextView tvMoodStatus;
    private TextView tvScreenTime;
    private TextView tvMoodLabel;
    private Switch switchOverlay;
    private Switch switchWallpaper;
    private Button btnInterval30m, btnInterval1h, btnInterval2h;
    private Button btnGenerateNow;
    private TextView tvGenerationStatus;
    private Button btnPermOverlay, btnPermUsage, btnStartCollection;
    private Button btnDataManagement;

    private DataCollectionService dataCollectionService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DataCollectionService.DataCollectionBinder binder =
                    (DataCollectionService.DataCollectionBinder) service;
            dataCollectionService = binder.getService();
            serviceBound = true;
            Log.d(TAG, "DataCollectionService bound");
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
        uiHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupListeners();
        loadSavedState();

        startDataCollectionService();
        bindDataCollectionService();
    }

    private void initViews() {
        headerMoodIcon = findViewById(R.id.header_mood_icon);
        tvMoodStatus = findViewById(R.id.tv_mood_status);
        tvScreenTime = findViewById(R.id.tv_screen_time);
        tvMoodLabel = findViewById(R.id.tv_mood_label);
        switchOverlay = findViewById(R.id.switch_overlay);
        switchWallpaper = findViewById(R.id.switch_wallpaper);
        btnInterval30m = findViewById(R.id.btn_interval_30m);
        btnInterval1h = findViewById(R.id.btn_interval_1h);
        btnInterval2h = findViewById(R.id.btn_interval_2h);
        btnGenerateNow = findViewById(R.id.btn_generate_now);
        tvGenerationStatus = findViewById(R.id.tv_generation_status);
        btnPermOverlay = findViewById(R.id.btn_perm_overlay);
        btnPermUsage = findViewById(R.id.btn_perm_usage);
        btnStartCollection = findViewById(R.id.btn_start_collection);
        btnDataManagement = findViewById(R.id.btn_data_management);
    }

    private void setupListeners() {
        switchOverlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, isChecked);
            if (isChecked) {
                startOverlayService();
            } else {
                stopOverlayService();
            }
        });

        switchWallpaper.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setBoolean(CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, isChecked);
        });

        btnInterval30m.setOnClickListener(v -> setInterval(30 * 60_000L));
        btnInterval1h.setOnClickListener(v -> setInterval(60 * 60_000L));
        btnInterval2h.setOnClickListener(v -> setInterval(120 * 60_000L));

        btnGenerateNow.setOnClickListener(v -> generateWallpaperNow());

        btnPermOverlay.setOnClickListener(v -> requestOverlayPermission());
        btnPermUsage.setOnClickListener(v -> requestUsageStatsPermission());

        btnStartCollection.setOnClickListener(v -> {
            startDataCollectionService();
            Toast.makeText(this, "数据收集服务已启动", Toast.LENGTH_SHORT).show();
        });

        btnDataManagement.setOnClickListener(v -> {
            startActivity(new Intent(this, AndroidDataCollector.class));
        });
    }

    private void loadSavedState() {
        switchOverlay.setChecked(config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true));
        switchWallpaper.setChecked(config.getBoolean(
                CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, true));

        long interval = config.getLong(CollectionConfig.KEY_WALLPAPER_GENERATION_INTERVAL_MS, 3600_000L);
        highlightIntervalButton(interval);

        updatePermissionButtons();
    }

    private void setInterval(long intervalMs) {
        config.setLong(CollectionConfig.KEY_WALLPAPER_GENERATION_INTERVAL_MS, intervalMs);
        highlightIntervalButton(intervalMs);
    }

    private void highlightIntervalButton(long intervalMs) {
        int activeColor = 0xFF03DAC5;
        int inactiveColor = 0xFF3F3F3F;
        int activeTextColor = 0xFF000000;
        int inactiveTextColor = 0xFFFFFFFF;

        btnInterval30m.setBackgroundColor(intervalMs <= 30 * 60_000L ? activeColor : inactiveColor);
        btnInterval30m.setTextColor(intervalMs <= 30 * 60_000L ? activeTextColor : inactiveTextColor);

        boolean is1h = intervalMs > 30 * 60_000L && intervalMs <= 60 * 60_000L;
        btnInterval1h.setBackgroundColor(is1h ? activeColor : inactiveColor);
        btnInterval1h.setTextColor(is1h ? activeTextColor : inactiveTextColor);

        btnInterval2h.setBackgroundColor(intervalMs > 60 * 60_000L ? activeColor : inactiveColor);
        btnInterval2h.setTextColor(intervalMs > 60 * 60_000L ? activeTextColor : inactiveTextColor);
    }

    private void generateWallpaperNow() {
        btnGenerateNow.setEnabled(false);
        btnGenerateNow.setText("生成中...");

        wallpaperManager.generateAndSetWallpaper(new WallpaperGenerationManager.WallpaperGenerationCallback() {
            @Override
            public void onSuccess(String message) {
                uiHandler.post(() -> {
                    tvGenerationStatus.setText(message);
                    tvGenerationStatus.setTextColor(0xFF03DAC5);
                    btnGenerateNow.setEnabled(true);
                    btnGenerateNow.setText("立即生成壁纸");
                });
            }

            @Override
            public void onError(String error) {
                uiHandler.post(() -> {
                    tvGenerationStatus.setText(error);
                    tvGenerationStatus.setTextColor(0xFFFF5252);
                    btnGenerateNow.setEnabled(true);
                    btnGenerateNow.setText("立即生成壁纸");
                });
            }

            @Override
            public void onProgress(String status) {
                uiHandler.post(() -> {
                    tvGenerationStatus.setText(status);
                    tvGenerationStatus.setTextColor(0xFFB0B0B0);
                });
            }
        });
    }

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

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            } else {
                Toast.makeText(this, "悬浮窗权限已授权", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            startActivityForResult(
                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQUEST_USAGE_STATS);
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

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionButtons();
        refreshScreenTimeDisplay();

        if (config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true)
                && Settings.canDrawOverlays(this)) {
            startOverlayService();
        }
    }

    private void refreshScreenTimeDisplay() {
        if (!screenUsageCollector.isAvailable()) {
            tvScreenTime.setText("--");
            tvMoodLabel.setText("需要授权使用情况访问权限");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject data = screenUsageCollector.collectData();
                if (data != null) {
                    long screenTimeMs = data.optLong("today_screen_time_ms", 0);
                    String readable = data.optString("today_screen_time_readable", "--");
                    MoodMapper.Mood mood = MoodMapper.fromScreenTime(screenTimeMs);

                    uiHandler.post(() -> {
                        tvScreenTime.setText(readable);
                        tvMoodLabel.setText("心情：" + mood.labelCn);
                        tvMoodStatus.setText(mood.labelCn + " | 数字健康伙伴");
                        headerMoodIcon.setImageResource(mood.drawableRes);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing screen time", e);
            }
        }).start();
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
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        if (wallpaperManager != null) {
            wallpaperManager.shutdown();
        }
    }
}
