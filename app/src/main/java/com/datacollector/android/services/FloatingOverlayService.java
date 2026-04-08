package com.datacollector.android.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.datacollector.android.R;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.collectors.ActivityRecognitionCollector;
import com.datacollector.android.collectors.BluetoothDataCollector;
import com.datacollector.android.collectors.CalendarDataCollector;
import com.datacollector.android.collectors.LocationDataCollector;
import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.collectors.WeatherDataCollector;
import com.datacollector.android.collectors.WifiDataCollector;
import com.datacollector.android.processing.ContextSnapshotCollector;
import com.datacollector.android.utils.AppForegroundTracker;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.processing.LLMScoringEngine;
import com.datacollector.android.utils.UserInteractionLogger;
import com.datacollector.android.views.MoodFaceView;
import com.datacollector.android.views.SpeechBubbleDrawable;

import org.json.JSONObject;

/**
 * 悬浮窗引擎（实时反思 - Real-time Reflection）。
 *
 * 职责：纯展示层 — 管理悬浮窗 UI、拖动、气泡动画。
 * 数据采集和 LLM 评分通过 ContextSnapshotCollector + LLMScoringEngine 完成。
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
    private CollectionConfig config;
    private UserInteractionLogger logger;

    // ── 中层处理组件 ──
    private ContextSnapshotCollector snapshotCollector;
    private LLMScoringEngine llmScoringEngine;
    private DeepSeekApiClient deepSeekClient;

    private boolean isBubbleShowing = false;
    private volatile boolean isGeneratingBubble = false;
    private Runnable periodicUpdateRunnable = null;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Screen on/off events are captured by the periodic LLM scoring snapshot
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
        if (!config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true)) {
            Log.i(TAG, "RI4SU disabled — stopping FloatingOverlayService");
            stopSelf();
            return;
        }
        logger = UserInteractionLogger.get(this);

        // 初始化采集器实例（底层）
        ScreenUsageCollector screenUsageCollector = new ScreenUsageCollector(this);
        CalendarDataCollector calendarCollector = new CalendarDataCollector(this);
        WeatherDataCollector weatherCollector = new WeatherDataCollector(this);
        LocationDataCollector locationCollector = new LocationDataCollector(this);
        ActivityRecognitionCollector activityCollector = new ActivityRecognitionCollector(this);
        WifiDataCollector wifiCollector = new WifiDataCollector(this);
        BluetoothDataCollector bluetoothCollector = new BluetoothDataCollector(this);

        // 初始化中层处理组件
        snapshotCollector = new ContextSnapshotCollector(this,
                screenUsageCollector, calendarCollector, weatherCollector,
                locationCollector, activityCollector, wifiCollector, bluetoothCollector);
        deepSeekClient = new DeepSeekApiClient(this);
        llmScoringEngine = new LLMScoringEngine(this, deepSeekClient);

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
                    CHANNEL_ID, "RI4SU 反思图标", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("RI4SU 反思图标悬浮窗");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RI4SU 反思助手")
                .setContentText("RI4SU 反思引擎运行中")
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
        logger.log("overlay_click");

        showBubble("思考中...", false);

        new Thread(() -> {
            try {
                // 通过中层统一快照构建器采集完整上下文
                JSONObject snapshot = snapshotCollector.collectFullSnapshot();

                int uutValue = llmScoringEngine.getScore();
                Log.i(TAG, "onOverlayClicked: snapshot keys=" + snapshot.length()
                        + " uut=" + uutValue);

                final String text = deepSeekClient.generateBubbleText(snapshot, uutValue);

                Log.i(TAG, "Bubble text result: " + text);

                final String displayText = (text != null && !text.isEmpty()) ? text : "注意休息一下吧";
                mainHandler.post(() -> {
                    updateBubbleText(displayText);
                    logger.log("bubble_show", "score", uutValue,
                            "text_length", displayText.length());
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

    // ── 定期 LLM 评分 ────────────────────────────────────────

    private void startPeriodicUpdates() {
        // Cancel any existing periodic update to avoid duplicate chains
        if (periodicUpdateRunnable != null) {
            mainHandler.removeCallbacks(periodicUpdateRunnable);
        }

        long interval = config.getLong(
                CollectionConfig.KEY_LIGHT_COLLECTION_INTERVAL_MS, 2 * 60_000L);

        periodicUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                refreshLLMScore();
                mainHandler.postDelayed(this, interval);
            }
        };
        // First scoring after 15s to let collectors warm up
        mainHandler.postDelayed(periodicUpdateRunnable, 15_000L);
    }

    /**
     * 采集完整快照并请求 LLM 评分，更新表情。
     */
    private void refreshLLMScore() {
        // 息屏时跳过 LLM 评分，节省 API 调用
        if (!isScreenOn()) {
            Log.d(TAG, "Screen off — skipping LLM scoring");
            return;
        }

        new Thread(() -> {
            try {
                // 通过中层统一快照构建器采集
                JSONObject snapshot = snapshotCollector.collectLightSnapshot();

                int score = llmScoringEngine.assess(snapshot);
                mainHandler.post(() -> updateMoodFromLLMScore(score));

            } catch (Exception e) {
                Log.e(TAG, "Error in LLM scoring refresh", e);
            }
        }).start();
    }

    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return true;
        return pm.isInteractive();
    }

    private void updateMoodFromLLMScore(int score) {
        // LLM score (0-100) → stress (0.0-1.0) for MoodFaceView
        float stress = score / 100f;
        if (moodFace != null) {
            moodFace.setStress(stress);
        }
        logger.log("llm_score_update", "score", score, "stress", stress);
        Log.d(TAG, "Mood updated: stress=" + String.format("%.3f", stress)
                + " (LLM score=" + score + ")");
    }

    /**
     * 获取 LLMScoringEngine 实例（供外部调试面板读取分数和理由）。
     */
    public LLMScoringEngine getLLMScoringEngine() {
        return llmScoringEngine;
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
