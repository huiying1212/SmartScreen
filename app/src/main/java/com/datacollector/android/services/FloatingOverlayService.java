package com.datacollector.android.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.datacollector.android.R;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.collectors.CalendarDataCollector;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.MoodMapper;
import com.datacollector.android.utils.UnconsciousUsageTracker;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 悬浮窗引擎（实时反思 - Real-time Reflection）。
 *
 * 核心功能：
 * 1. 全局悬浮窗展示拟人表情，表情由 UUT 算法驱动
 * 2. 点击后由 LLM 生成 ≤15 字的短提醒气泡，5 秒后消失
 * 3. 支持拖动定位
 */
public class FloatingOverlayService extends Service {

    private static final String TAG = "FloatingOverlay";
    private static final String CHANNEL_ID = "FloatingOverlayChannel";
    private static final int NOTIFICATION_ID = 2001;
    private static final long BUBBLE_DISPLAY_MS = 5_000L;

    private WindowManager windowManager;
    private View overlayView;
    private ImageView moodIcon;
    private TextView bubbleText;
    private WindowManager.LayoutParams layoutParams;

    private Handler mainHandler;
    private UnconsciousUsageTracker uutTracker;
    private ScreenUsageCollector screenUsageCollector;
    private DeepSeekApiClient deepSeekClient;
    private CalendarDataCollector calendarCollector;
    private CollectionConfig config;

    private MoodMapper.Mood currentMood = MoodMapper.Mood.HAPPY;
    private boolean isBubbleShowing = false;
    private boolean isGeneratingBubble = false;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                uutTracker.onScreenOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                uutTracker.onScreenOn();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        config = CollectionConfig.getInstance(this);

        uutTracker = new UnconsciousUsageTracker(this);
        screenUsageCollector = new ScreenUsageCollector(this);
        deepSeekClient = new DeepSeekApiClient(this);
        calendarCollector = new CalendarDataCollector(this);

        if (Settings.canDrawOverlays(this)) {
            createOverlay();
            startPeriodicUpdates();
        } else {
            Log.w(TAG, "No SYSTEM_ALERT_WINDOW permission");
            stopSelf();
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "悬浮图标服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("CATIA3 实时反思悬浮窗");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CATIA3 心情助手")
                .setContentText("实时反思引擎运行中")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .build();
    }

    // ── 悬浮窗 UI ────────────────────────────────────────────

    private void createOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.floating_overlay, null);
        moodIcon = overlayView.findViewById(R.id.overlay_mood_icon);
        bubbleText = overlayView.findViewById(R.id.overlay_bubble_text);
        moodIcon.setImageResource(currentMood.drawableRes);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 50;
        layoutParams.y = 200;

        setupTouchListener();
        windowManager.addView(overlayView, layoutParams);
    }

    private void setupTouchListener() {
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;
            private static final int CLICK_THRESHOLD = 10;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > CLICK_THRESHOLD || Math.abs(dy) > CLICK_THRESHOLD) {
                            isDragging = true;
                        }
                        layoutParams.x = initialX + (int) dx;
                        layoutParams.y = initialY + (int) dy;
                        windowManager.updateViewLayout(overlayView, layoutParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            onOverlayClicked();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // ── 点击 → LLM 气泡 ──────────────────────────────────────

    private void onOverlayClicked() {
        if (isBubbleShowing || isGeneratingBubble) return;
        isGeneratingBubble = true;

        showBubble("thinking...", false);

        new Thread(() -> {
            try {
                String currentApp = uutTracker.getCurrentPackage();
                if (currentApp == null) currentApp = "unknown";

                int uutValue = uutTracker.getUUT();
                Log.i(TAG, "onOverlayClicked: app=" + currentApp + " uut=" + uutValue);

                int usageMins = 0;
                try {
                    JSONObject screenData = screenUsageCollector.collectData();
                    if (screenData != null) {
                        usageMins = (int) (screenData.optLong(
                                "foreground_app_current_open_ms", 0) / 60_000L);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get current app usage", e);
                }

                String calendarInfo = getCalendarContext();

                // 只走 LLM，generateBubbleText 永远返回非 null 字符串
                final String text = deepSeekClient.generateBubbleText(
                        currentApp, usageMins, uutValue, calendarInfo);

                Log.i(TAG, "Bubble text result: " + text);

                mainHandler.post(() -> {
                    dismissBubbleImmediately();
                    showBubble(text);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error generating bubble", e);
                final String err = "Error: " + e.getMessage();
                mainHandler.post(() -> {
                    dismissBubbleImmediately();
                    showBubble(err);
                });
            } finally {
                isGeneratingBubble = false;
            }
        }).start();
    }

    private String getCalendarContext() {
        try {
            if (calendarCollector == null || !calendarCollector.isAvailable()) return null;
            JSONObject calData = calendarCollector.collectData();
            if (calData == null) return null;

            JSONArray events = calData.optJSONArray("events");
            if (events == null || events.length() == 0) return "空闲时间";

            long now = System.currentTimeMillis();
            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.optJSONObject(i);
                if (ev == null) continue;
                long start = ev.optLong("start_time", 0);
                long end = ev.optLong("end_time", 0);
                if (now >= start && now <= end) {
                    return "计划: " + ev.optString("title", "日程中");
                }
            }

            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.optJSONObject(i);
                if (ev == null) continue;
                long start = ev.optLong("start_time", 0);
                if (start > now && start - now < 3600_000L) {
                    return "即将: " + ev.optString("title", "有安排");
                }
            }

            return "空闲时间";
        } catch (Exception e) {
            return null;
        }
    }

    private void dismissBubbleImmediately() {
        if (bubbleText != null) {
            bubbleText.clearAnimation();
            bubbleText.setVisibility(View.GONE);
        }
        isBubbleShowing = false;
        mainHandler.removeCallbacksAndMessages(null);
        // 恢复定期更新（removeCallbacksAndMessages 会移除所有回调）
        startPeriodicUpdates();
    }

    private void showBubble(String text) {
        showBubble(text, true);
    }

    /**
     * @param autoDismiss 为 false 时气泡不会自动消失（用于"思考中"占位）
     */
    private void showBubble(String text, boolean autoDismiss) {
        if (bubbleText == null || text == null) return;
        isBubbleShowing = true;

        bubbleText.setText(text);
        bubbleText.setVisibility(View.VISIBLE);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(300);
        bubbleText.startAnimation(fadeIn);

        if (autoDismiss) {
            mainHandler.postDelayed(() -> {
                AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
                fadeOut.setDuration(500);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation a) {}
                    @Override public void onAnimationRepeat(Animation a) {}
                    @Override public void onAnimationEnd(Animation a) {
                        bubbleText.setVisibility(View.GONE);
                        isBubbleShowing = false;
                    }
                });
                bubbleText.startAnimation(fadeOut);
            }, BUBBLE_DISPLAY_MS);
        }
    }

    // ── 定期 UUT 更新 ────────────────────────────────────────

    private void startPeriodicUpdates() {
        long interval = config.getLong(
                CollectionConfig.KEY_OVERLAY_UPDATE_INTERVAL_MS, 30_000L);

        Runnable updateRunnable = new Runnable() {
            @Override
            public void run() {
                refreshUUT();
                mainHandler.postDelayed(this, interval);
            }
        };
        mainHandler.post(updateRunnable);
    }

    private void refreshUUT() {
        new Thread(() -> {
            try {
                boolean screenOn = isScreenOn();

                String foregroundPkg = null;
                if (screenOn && screenUsageCollector.isAvailable()) {
                    JSONObject data = screenUsageCollector.collectData();
                    if (data != null) {
                        foregroundPkg = data.optString("foreground_app_package", null);
                    }
                }

                uutTracker.update(foregroundPkg, screenOn);
                int uut = uutTracker.getUUT();

                mainHandler.post(() -> updateMoodFromUUT(uut));

            } catch (Exception e) {
                Log.e(TAG, "Error refreshing UUT", e);
            }
        }).start();
    }

    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return true;
        return pm.isInteractive();
    }

    private void updateMoodFromUUT(int uut) {
        MoodMapper.Mood newMood = MoodMapper.fromUUT(uut);
        if (newMood != currentMood) {
            currentMood = newMood;
            if (moodIcon != null) {
                moodIcon.setImageResource(currentMood.drawableRes);
            }
            Log.d(TAG, "Mood updated: " + currentMood.name() + " (UUT=" + uut + ")");
        }

        // 细粒度视觉反馈：根据 UUT 值微调 alpha
        if (moodIcon != null) {
            float stress = MoodMapper.uutToStress(uut);
            float alpha = 1.0f - stress * 0.3f; // 高 UUT 时图标略变暗
            moodIcon.setAlpha(alpha);
        }
    }

    // ── 静态辅助方法（供 DataCollectionService 调用）──────────

    public static void sendMoodUpdate(Context context, long screenTimeMs) {
        // 保留接口兼容，UUT 模式下由 FloatingOverlayService 自驱动
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        if (deepSeekClient != null) deepSeekClient.shutdown();
        Log.i(TAG, "FloatingOverlayService destroyed");
    }
}
