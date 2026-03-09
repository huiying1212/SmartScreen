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
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.datacollector.android.R;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.utils.MoodMapper;

import org.json.JSONObject;

/**
 * 悬浮窗服务：在所有应用上方显示一个可拖动的拟人化图标，
 * 其表情根据屏幕使用时长动态变化。
 */
public class FloatingOverlayService extends Service {

    private static final String TAG = "FloatingOverlay";
    private static final String CHANNEL_ID = "FloatingOverlayChannel";
    private static final int NOTIFICATION_ID = 2001;
    private static final long UPDATE_INTERVAL_MS = 5 * 60 * 1000L; // 5 minutes

    public static final String ACTION_MOOD_UPDATE = "com.datacollector.android.MOOD_UPDATE";
    public static final String EXTRA_SCREEN_TIME_MS = "screen_time_ms";

    private WindowManager windowManager;
    private View overlayView;
    private ImageView moodIcon;
    private WindowManager.LayoutParams layoutParams;
    private Handler updateHandler;
    private ScreenUsageCollector screenUsageCollector;
    private MoodMapper.Mood currentMood = MoodMapper.Mood.HAPPY;
    private long lastScreenTimeMs = 0;

    private final BroadcastReceiver moodUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_MOOD_UPDATE.equals(intent.getAction())) {
                long screenTimeMs = intent.getLongExtra(EXTRA_SCREEN_TIME_MS, 0);
                if (screenTimeMs > 0) {
                    updateMoodFromScreenTime(screenTimeMs);
                }
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
        updateHandler = new Handler(Looper.getMainLooper());
        screenUsageCollector = new ScreenUsageCollector(this);

        if (Settings.canDrawOverlays(this)) {
            createOverlay();
            startPeriodicUpdates();
        } else {
            Log.w(TAG, "No SYSTEM_ALERT_WINDOW permission");
            stopSelf();
            return;
        }

        IntentFilter filter = new IntentFilter(ACTION_MOOD_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(moodUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(moodUpdateReceiver, filter);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "悬浮图标服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("CATIA3 悬浮心情图标");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CATIA3 心情助手")
                .setContentText("正在监测使用状态")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .build();
    }

    private void createOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.floating_overlay, null);
        moodIcon = overlayView.findViewById(R.id.overlay_mood_icon);
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

    private void onOverlayClicked() {
        long hours = lastScreenTimeMs / (60 * 60_000L);
        long mins = (lastScreenTimeMs / 60_000L) % 60;
        String timeStr = hours > 0 ? hours + "小时" + mins + "分钟" : mins + "分钟";
        String msg = "今日使用 " + timeStr + " | " + currentMood.labelCn;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void startPeriodicUpdates() {
        Runnable updateRunnable = new Runnable() {
            @Override
            public void run() {
                refreshMoodFromSystem();
                updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        };
        // Initial update immediately
        updateHandler.post(updateRunnable);
    }

    private void refreshMoodFromSystem() {
        if (screenUsageCollector == null || !screenUsageCollector.isAvailable()) {
            return;
        }
        new Thread(() -> {
            try {
                JSONObject data = screenUsageCollector.collectData();
                if (data != null) {
                    long screenTimeMs = data.optLong("today_screen_time_ms", 0);
                    updateHandler.post(() -> updateMoodFromScreenTime(screenTimeMs));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing mood", e);
            }
        }).start();
    }

    private void updateMoodFromScreenTime(long screenTimeMs) {
        lastScreenTimeMs = screenTimeMs;
        MoodMapper.Mood newMood = MoodMapper.fromScreenTime(screenTimeMs);
        if (newMood != currentMood) {
            currentMood = newMood;
            if (moodIcon != null) {
                moodIcon.setImageResource(currentMood.drawableRes);
            }
            Log.d(TAG, "Mood updated: " + currentMood.name()
                    + " (screen time: " + screenTimeMs / 60000 + " min)");
        }
    }

    /**
     * Called externally (e.g., from DataCollectionService via broadcast)
     * to push a screen time update.
     */
    public static void sendMoodUpdate(Context context, long screenTimeMs) {
        Intent intent = new Intent(ACTION_MOOD_UPDATE);
        intent.putExtra(EXTRA_SCREEN_TIME_MS, screenTimeMs);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (updateHandler != null) {
            updateHandler.removeCallbacksAndMessages(null);
        }
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.w(TAG, "Error removing overlay", e);
            }
        }
        try {
            unregisterReceiver(moodUpdateReceiver);
        } catch (Exception ignored) {
        }
        Log.i(TAG, "FloatingOverlayService destroyed");
    }
}
