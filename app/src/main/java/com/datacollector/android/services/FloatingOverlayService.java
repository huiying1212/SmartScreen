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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.datacollector.android.R;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.collectors.CalendarDataCollector;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.collectors.WeatherDataCollector;
import com.datacollector.android.utils.AppForegroundTracker;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.MoodScoreEngine;
import com.datacollector.android.utils.UnconsciousUsageTracker;
import com.datacollector.android.views.MoodFaceView;
import com.datacollector.android.views.SpeechBubbleDrawable;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 悬浮窗引擎（实时反思 - Real-time Reflection）。
 *
 * 核心功能：
 * 1. 全局悬浮窗展示拟人表情，表情由 UUT 算法驱动
 * 2. 点击后由 LLM 生成提醒气泡（独立悬浮窗，带小尾巴指向图标），8 秒后消失
 * 3. 气泡自动避免遮挡图标和超出屏幕
 * 4. 支持拖动定位
 */
public class FloatingOverlayService extends Service {

    private static final String TAG = "FloatingOverlay";
    private static final String CHANNEL_ID = "FloatingOverlayChannel";
    private static final int NOTIFICATION_ID = 2001;
    private static final long BUBBLE_DISPLAY_MS = 5_000L;

    private WindowManager windowManager;
    private View overlayView;
    private MoodFaceView moodFace;
    private WindowManager.LayoutParams layoutParams;

    // ── Bubble (separate floating window) ──
    private View bubbleView;
    private TextView bubbleText;
    private WindowManager.LayoutParams bubbleParams;
    private SpeechBubbleDrawable bubbleDrawable;
    private boolean bubbleAdded = false;

    private Handler mainHandler;
    private UnconsciousUsageTracker uutTracker;
    private ScreenUsageCollector screenUsageCollector;
    private MoodScoreEngine moodScoreEngine;
    private DeepSeekApiClient deepSeekClient;
    private CalendarDataCollector calendarCollector;
    private WeatherDataCollector weatherCollector;
    private CollectionConfig config;

    private boolean isBubbleShowing = false;
    private boolean isGeneratingBubble = false;
    private Runnable periodicUpdateRunnable = null;

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

    public static final String ACTION_UPDATE_FACE_STYLE = "com.datacollector.ACTION_UPDATE_FACE_STYLE";
    public static final String EXTRA_FACE_STYLE = "face_style";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPDATE_FACE_STYLE.equals(intent.getAction())) {
            String styleName = intent.getStringExtra(EXTRA_FACE_STYLE);
            if (styleName != null && moodFace != null) {
                moodFace.setFaceStyle(MoodFaceView.FaceStyle.fromName(styleName));
            }
        }
        return START_STICKY;
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
        moodScoreEngine = new MoodScoreEngine(this);
        deepSeekClient = new DeepSeekApiClient(this);
        calendarCollector = new CalendarDataCollector(this);
        weatherCollector = new WeatherDataCollector(this);

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
        moodFace = overlayView.findViewById(R.id.overlay_mood_face);

        String styleName = config.getString(CollectionConfig.KEY_FACE_STYLE, "CLASSIC");
        moodFace.setFaceStyle(MoodFaceView.FaceStyle.fromName(styleName));
        moodFace.setStressImmediate(0f);

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

        // ── Prepare bubble window (hidden, added on first use) ──
        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null);
        bubbleText = bubbleView.findViewById(R.id.bubble_text);
        bubbleDrawable = new SpeechBubbleDrawable();
        bubbleText.setBackground(bubbleDrawable);

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
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
                        // Bubble follows the icon during drag
                        if (isBubbleShowing && bubbleAdded) {
                            positionBubble();
                        }
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

        showBubble("思考中...", false);

        new Thread(() -> {
            try {
                // Collect fresh screen data first — this also serves as the
                // authoritative source for both foreground app and usage time.
                String currentApp = null;
                int usageMins = 0;
                try {
                    JSONObject screenData = screenUsageCollector.collectData();
                    if (screenData != null) {
                        currentApp = screenData.optString(
                                "foreground_app_package", null);
                        usageMins = (int) (screenData.optLong(
                                "foreground_app_current_open_ms", 0) / 60_000L);
                        // If per-app open time is 0, fall back to session time
                        if (usageMins == 0) {
                            usageMins = (int) (screenData.optLong(
                                    "current_session_ms", 0) / 60_000L);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get screen usage data", e);
                }

                // Fall back to UUT tracker's cached package if collector missed it
                if (currentApp == null || currentApp.isEmpty()) {
                    currentApp = uutTracker.getCurrentPackage();
                }
                if (currentApp == null || currentApp.isEmpty()) {
                    currentApp = "unknown";
                }

                int uutValue = uutTracker.getUUT();
                Log.i(TAG, "onOverlayClicked: app=" + currentApp
                        + " mins=" + usageMins + " uut=" + uutValue);

                String calendarInfo = getCalendarContext();

                // Collect weather info
                String weatherInfo = null;
                try {
                    if (weatherCollector != null && weatherCollector.isAvailable()) {
                        JSONObject weatherData = weatherCollector.collectData();
                        if (weatherData != null) {
                            weatherInfo = weatherData.optString("readable_summary", null);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get weather data", e);
                }

                final String text = deepSeekClient.generateBubbleText(
                        currentApp, usageMins, uutValue, calendarInfo, weatherInfo);

                Log.i(TAG, "Bubble text result: " + text);

                final String displayText = (text != null && !text.isEmpty()) ? text : "注意休息一下吧";
                mainHandler.post(() -> {
                    updateBubbleText(displayText);
                });

                // Notify MainActivity to sync the reminder text
                Intent updateIntent = new Intent("com.datacollector.android.BUBBLE_TEXT_UPDATED");
                updateIntent.putExtra("bubble_text", displayText);
                sendBroadcast(updateIntent);

            } catch (Exception e) {
                Log.e(TAG, "Error generating bubble", e);
                final String err = "出错了，稍后再试";
                mainHandler.post(() -> {
                    updateBubbleText(err);
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

            // CalendarDataCollector outputs "begin_timestamp" / "end_timestamp"
            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.optJSONObject(i);
                if (ev == null) continue;
                long start = ev.optLong("begin_timestamp", 0);
                long end = ev.optLong("end_timestamp", 0);
                if (now >= start && now <= end) {
                    return "计划: " + ev.optString("title", "日程中");
                }
            }

            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.optJSONObject(i);
                if (ev == null) continue;
                long start = ev.optLong("begin_timestamp", 0);
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
        if (bubbleView != null) {
            bubbleView.clearAnimation();
            bubbleView.setVisibility(View.GONE);
        }
        isBubbleShowing = false;
        mainHandler.removeCallbacks(bubbleDismissRunnable);
    }

    /**
     * Replace the text in the already-visible bubble, reposition it,
     * and start the auto-dismiss countdown.
     */
    private void updateBubbleText(String text) {
        if (bubbleText == null || text == null) return;
        bubbleText.setText(text);
        // Clear any running animation (e.g. fadeIn) before scheduling dismiss
        if (bubbleView != null) bubbleView.clearAnimation();
        // Reposition — bubble size may have changed with new text
        if (bubbleAdded) {
            positionBubble();
        }
        // Start auto-dismiss timer
        scheduleBubbleDismiss();
    }

    private void scheduleBubbleDismiss() {
        // Remove any previous dismiss callback (but keep periodic updates)
        mainHandler.removeCallbacks(bubbleDismissRunnable);
        mainHandler.postDelayed(bubbleDismissRunnable, BUBBLE_DISPLAY_MS);
    }

    private final Runnable bubbleDismissRunnable = () -> {
        if (bubbleView == null) return;
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(400);
        fadeOut.setFillAfter(true);
        bubbleView.startAnimation(fadeOut);
        // Don't rely on onAnimationEnd — it's unreliable for WindowManager views.
        // Force-hide after the animation duration.
        mainHandler.postDelayed(() -> {
            if (bubbleView != null) {
                bubbleView.clearAnimation();
                bubbleView.setVisibility(View.GONE);
            }
            isBubbleShowing = false;
        }, 420);
    };

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

        // Add bubble window if not yet added
        if (!bubbleAdded) {
            windowManager.addView(bubbleView, bubbleParams);
            bubbleAdded = true;
        }

        // Position the bubble relative to the icon
        positionBubble();

        bubbleView.setVisibility(View.VISIBLE);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(300);
        bubbleView.startAnimation(fadeIn);

        if (autoDismiss) {
            scheduleBubbleDismiss();
        }
    }

    /**
     * Position the bubble window so it appears to "speak" from the icon.
     * - Prefers placing the bubble above the icon.
     * - If not enough room above, places it below.
     * - Horizontally centers on the icon, clamped to screen edges.
     * - Adjusts the tail position on the SpeechBubbleDrawable to point at the icon center.
     */
    private void positionBubble() {
        if (bubbleView == null || overlayView == null) return;

        // Measure the bubble to know its size
        bubbleView.measure(
                View.MeasureSpec.makeMeasureSpec(dpToPx(260), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int bubbleW = bubbleView.getMeasuredWidth();
        int bubbleH = bubbleView.getMeasuredHeight();

        // Icon position and size
        int iconX = layoutParams.x;
        int iconY = layoutParams.y;
        int iconW = overlayView.getWidth();
        int iconH = overlayView.getHeight();
        if (iconW == 0) iconW = dpToPx(56);
        if (iconH == 0) iconH = dpToPx(56);
        int iconCenterX = iconX + iconW / 2;

        // Screen dimensions
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        int margin = dpToPx(6);
        int gap = dpToPx(4); // gap between bubble and icon

        // ── Vertical: prefer above, fall back to below ──
        boolean placeAbove = (iconY - bubbleH - gap) >= 0;
        int bubbleY;
        if (placeAbove) {
            bubbleY = iconY - bubbleH - gap;
            bubbleDrawable.setTailAtBottom(true);
        } else {
            bubbleY = iconY + iconH + gap;
            bubbleDrawable.setTailAtBottom(false);
        }
        // Clamp vertical
        bubbleY = Math.max(0, Math.min(bubbleY, screenH - bubbleH));

        // ── Horizontal: center on icon, clamp to screen ──
        int bubbleX = iconCenterX - bubbleW / 2;
        bubbleX = Math.max(margin, Math.min(bubbleX, screenW - bubbleW - margin));

        // ── Tail position: point at icon center ──
        float tailPos = (float)(iconCenterX - bubbleX) / (float) Math.max(1, bubbleW);
        tailPos = Math.max(0.12f, Math.min(0.88f, tailPos));
        bubbleDrawable.setTailPosition(tailPos);

        bubbleParams.x = bubbleX;
        bubbleParams.y = bubbleY;
        windowManager.updateViewLayout(bubbleView, bubbleParams);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ── 定期 UUT 更新 ────────────────────────────────────────

    private void startPeriodicUpdates() {
        // Cancel any existing periodic update to avoid duplicate chains
        if (periodicUpdateRunnable != null) {
            mainHandler.removeCallbacks(periodicUpdateRunnable);
        }

        long interval = config.getLong(
                CollectionConfig.KEY_OVERLAY_UPDATE_INTERVAL_MS, 30_000L);

        periodicUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                refreshUUT();
                mainHandler.postDelayed(this, interval);
            }
        };
        mainHandler.post(periodicUpdateRunnable);
    }

    private void refreshUUT() {
        new Thread(() -> {
            try {
                boolean screenOn = isScreenOn();
                AppForegroundTracker fgTracker = AppForegroundTracker.getInstance(
                        FloatingOverlayService.this);

                String foregroundPkg = null;
                if (screenOn && screenUsageCollector.isAvailable()) {
                    JSONObject data = screenUsageCollector.collectData();
                    if (data != null) {
                        // ScreenUsageCollector 内部已优先读 Tracker 缓存；
                        // 这里拿到的 foreground_app_package 是最终可信值，
                        // 再写回 Tracker 以刷新 lastUpdateTime、更新切换时间。
                        foregroundPkg = data.optString("foreground_app_package", null);
                    }
                }

                // 将最新前台 App 写入全局 Tracker（null 时也刷新 lastUpdateTime）
                fgTracker.update(foregroundPkg);

                // 屏幕关闭超过阈值时重置 Tracker，避免缓存污染下次采集
                if (!screenOn) {
                    // 具体衰减逻辑已在 UUT 内处理，这里仅在 Tracker 过期后 reset
                    if (fgTracker.isStale()) {
                        fgTracker.reset();
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
        // 多维度评分引擎：综合全天时长、娱乐占比、会话强度、目标达成度
        float stress = moodScoreEngine.computeStress(uut, screenUsageCollector);
        if (moodFace != null) {
            moodFace.setStress(stress);
        }
        Log.d(TAG, "Mood updated: stress=" + String.format("%.3f", stress)
                + " (UUT=" + uut + ")");
    }

    /**
     * 获取 MoodScoreEngine 实例（供外部调试面板读取各维度分数）。
     */
    public MoodScoreEngine getMoodScoreEngine() {
        return moodScoreEngine;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
        if (bubbleAdded && bubbleView != null && windowManager != null) {
            try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
            bubbleAdded = false;
        }
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        if (deepSeekClient != null) deepSeekClient.shutdown();
        Log.i(TAG, "FloatingOverlayService destroyed");
    }
}
