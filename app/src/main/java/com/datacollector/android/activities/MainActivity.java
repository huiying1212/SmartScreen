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
import android.widget.SeekBar;
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
 * 主控制面板：管理悬浮窗、壁纸引擎、权重偏好、权限和数据收集服务。
 */
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_USAGE_STATS = 1002;

    private CollectionConfig config;
    private WallpaperGenerationManager wallpaperManager;
    private ScreenUsageCollector screenUsageCollector;
    private Handler uiHandler;

    // UI
    private ImageView headerMoodIcon;
    private TextView tvMoodStatus, tvScreenTime, tvMoodLabel;
    private Switch switchOverlay, switchWallpaper;
    private Button btnGenerateNow;
    private TextView tvGenerationStatus;
    private Button btnPermOverlay, btnPermUsage, btnStartCollection, btnDataManagement;

    // 权重滑块
    private SeekBar seekProductivity, seekEntertainment, seekHealth, seekSocial;
    private TextView tvWeightProductivity, tvWeightEntertainment, tvWeightHealth, tvWeightSocial;

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
        btnGenerateNow = findViewById(R.id.btn_generate_now);
        tvGenerationStatus = findViewById(R.id.tv_generation_status);
        btnPermOverlay = findViewById(R.id.btn_perm_overlay);
        btnPermUsage = findViewById(R.id.btn_perm_usage);
        btnStartCollection = findViewById(R.id.btn_start_collection);
        btnDataManagement = findViewById(R.id.btn_data_management);

        seekProductivity = findViewById(R.id.seek_productivity);
        seekEntertainment = findViewById(R.id.seek_entertainment);
        seekHealth = findViewById(R.id.seek_health);
        seekSocial = findViewById(R.id.seek_social);
        tvWeightProductivity = findViewById(R.id.tv_weight_productivity);
        tvWeightEntertainment = findViewById(R.id.tv_weight_entertainment);
        tvWeightHealth = findViewById(R.id.tv_weight_health);
        tvWeightSocial = findViewById(R.id.tv_weight_social);
    }

    private void setupListeners() {
        switchOverlay.setOnCheckedChangeListener((btn, checked) -> {
            config.setBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, checked);
            if (checked) startOverlayService(); else stopOverlayService();
        });

        switchWallpaper.setOnCheckedChangeListener((btn, checked) ->
                config.setBoolean(CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, checked));

        btnGenerateNow.setOnClickListener(v -> generateWallpaperNow());
        btnPermOverlay.setOnClickListener(v -> requestOverlayPermission());
        btnPermUsage.setOnClickListener(v -> requestUsageStatsPermission());

        btnStartCollection.setOnClickListener(v -> {
            startDataCollectionService();
            Toast.makeText(this, "数据收集服务已启动", Toast.LENGTH_SHORT).show();
        });

        btnDataManagement.setOnClickListener(v ->
                startActivity(new Intent(this, AndroidDataCollector.class)));

        setupWeightSeekBars();
    }

    private void setupWeightSeekBars() {
        SeekBar.OnSeekBarChangeListener weightListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float value = progress / 100f;
                int id = seekBar.getId();
                if (id == R.id.seek_productivity) {
                    config.setFloat(CollectionConfig.KEY_WEIGHT_PRODUCTIVITY, value);
                    tvWeightProductivity.setText(String.format("生产力: %.0f%%", value * 100));
                } else if (id == R.id.seek_entertainment) {
                    config.setFloat(CollectionConfig.KEY_WEIGHT_ENTERTAINMENT, value);
                    tvWeightEntertainment.setText(String.format("娱乐: %.0f%%", value * 100));
                } else if (id == R.id.seek_health) {
                    config.setFloat(CollectionConfig.KEY_WEIGHT_HEALTH, value);
                    tvWeightHealth.setText(String.format("健康: %.0f%%", value * 100));
                } else if (id == R.id.seek_social) {
                    config.setFloat(CollectionConfig.KEY_WEIGHT_SOCIAL, value);
                    tvWeightSocial.setText(String.format("社交: %.0f%%", value * 100));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekProductivity.setOnSeekBarChangeListener(weightListener);
        seekEntertainment.setOnSeekBarChangeListener(weightListener);
        seekHealth.setOnSeekBarChangeListener(weightListener);
        seekSocial.setOnSeekBarChangeListener(weightListener);
    }

    private void loadSavedState() {
        switchOverlay.setChecked(config.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true));
        switchWallpaper.setChecked(config.getBoolean(
                CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, true));

        int wp = (int) (config.getFloat(CollectionConfig.KEY_WEIGHT_PRODUCTIVITY, 0.4f) * 100);
        int we = (int) (config.getFloat(CollectionConfig.KEY_WEIGHT_ENTERTAINMENT, 0.2f) * 100);
        int wh = (int) (config.getFloat(CollectionConfig.KEY_WEIGHT_HEALTH, 0.2f) * 100);
        int ws = (int) (config.getFloat(CollectionConfig.KEY_WEIGHT_SOCIAL, 0.2f) * 100);

        seekProductivity.setProgress(wp);
        seekEntertainment.setProgress(we);
        seekHealth.setProgress(wh);
        seekSocial.setProgress(ws);

        tvWeightProductivity.setText(String.format("生产力: %d%%", wp));
        tvWeightEntertainment.setText(String.format("娱乐: %d%%", we));
        tvWeightHealth.setText(String.format("健康: %d%%", wh));
        tvWeightSocial.setText(String.format("社交: %d%%", ws));

        updatePermissionButtons();
    }

    private void generateWallpaperNow() {
        btnGenerateNow.setEnabled(false);
        btnGenerateNow.setText("生成中...");

        wallpaperManager.generateAndSetWallpaper(
                new WallpaperGenerationManager.WallpaperGenerationCallback() {
                    @Override
                    public void onSuccess(String msg) {
                        uiHandler.post(() -> {
                            tvGenerationStatus.setText(msg);
                            tvGenerationStatus.setTextColor(0xFF03DAC5);
                            btnGenerateNow.setEnabled(true);
                            btnGenerateNow.setText("立即生成壁纸");
                        });
                    }

                    @Override
                    public void onError(String err) {
                        uiHandler.post(() -> {
                            tvGenerationStatus.setText(err);
                            tvGenerationStatus.setTextColor(0xFFFF5252);
                            btnGenerateNow.setEnabled(true);
                            btnGenerateNow.setText("立即生成壁纸");
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

    // ── 服务管理 ─────────────────────────────────────────────

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

    // ── 权限 ─────────────────────────────────────────────────

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

    // ── 生命周期 ─────────────────────────────────────────────

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
                    int unlockCount = data.optInt("unlock_count_last_hour", 0);
                    String appCat = data.optString("foreground_app_category", "");
                    MoodMapper.Mood mood = MoodMapper.fromScreenTime(screenTimeMs);

                    uiHandler.post(() -> {
                        tvScreenTime.setText(readable);
                        tvMoodLabel.setText("心情: " + mood.labelCn
                                + " | 解锁: " + unlockCount + "次/小时"
                                + (appCat.isEmpty() ? "" : " | " + appCat));
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
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false; }
        if (wallpaperManager != null) wallpaperManager.shutdown();
    }
}
