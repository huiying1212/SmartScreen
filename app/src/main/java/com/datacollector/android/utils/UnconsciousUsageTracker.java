package com.datacollector.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.LinkedList;

/**
 * 无意识使用时间 (Unconscious Usage Time, UUT) 追踪器。
 *
 * UUT 值范围 0-100，反映用户当前"无意识刷手机"的程度。
 *
 * 规则：
 * - 触发条件：当前处于娱乐/社交/短视频/游戏类 App
 * - 累加机制：在触发类 App 停留超过 3 分钟后，每多停留 1 分钟 → UUT +5
 * - 惩罚机制：5 分钟内连续切换超过 4 个不同 App → UUT +20
 * - 衰减机制：屏幕锁定超过 15 分钟 → UUT 归零；
 *             处于生产力/工具类 App 超过 10 分钟 → UUT 逐渐归零
 */
public class UnconsciousUsageTracker {

    private static final String TAG = "UUTracker";
    private static final String PREFS_NAME = "uut_state";

    private static final int UUT_MAX = 100;
    private static final int UUT_MIN = 0;

    private static final long UNCONSCIOUS_GRACE_MS = 3 * 60_000L;  // 3 min grace
    private static final int ACCUMULATE_PER_MINUTE = 5;
    private static final int APP_SWITCH_PENALTY = 20;
    private static final int APP_SWITCH_THRESHOLD = 4;
    private static final long APP_SWITCH_WINDOW_MS = 5 * 60_000L;  // 5 min window
    private static final long SCREEN_OFF_DECAY_MS = 15 * 60_000L;  // 15 min → full reset
    private static final long PRODUCTIVE_DECAY_START_MS = 10 * 60_000L; // 10 min productive → start decay
    private static final int PRODUCTIVE_DECAY_PER_MINUTE = 3;

    private int uutValue;
    private long unconsciousAppStartTime = -1;
    private long lastAccumulationTime = -1;
    private long screenOffSince = -1;
    private long productiveAppStartTime = -1;
    private String currentPackage = null;

    private final LinkedList<AppSwitchRecord> recentSwitches = new LinkedList<>();
    private final SharedPreferences prefs;
    private final AppCategoryClassifier classifier;

    private static class AppSwitchRecord {
        final String packageName;
        final long timestamp;

        AppSwitchRecord(String packageName, long timestamp) {
            this.packageName = packageName;
            this.timestamp = timestamp;
        }
    }

    public UnconsciousUsageTracker(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.classifier = new AppCategoryClassifier(context);
        restoreState();
    }

    private void restoreState() {
        uutValue = prefs.getInt("uut_value", 0);
        long lastSave = prefs.getLong("last_save_time", 0);
        long elapsed = System.currentTimeMillis() - lastSave;
        if (elapsed > SCREEN_OFF_DECAY_MS) {
            uutValue = 0;
        }
    }

    private void saveState() {
        prefs.edit()
                .putInt("uut_value", uutValue)
                .putLong("last_save_time", System.currentTimeMillis())
                .apply();
    }

    /**
     * 核心更新方法，由 FloatingOverlayService 定期调用（建议 30s 间隔）。
     *
     * @param foregroundPackage 当前前台 App 包名，null 表示无法获取
     * @param screenOn          屏幕是否亮着
     */
    public synchronized void update(String foregroundPackage, boolean screenOn) {
        long now = System.currentTimeMillis();

        // --- 屏幕关闭处理 ---
        if (!screenOn) {
            if (screenOffSince < 0) {
                screenOffSince = now;
            }
            long offDuration = now - screenOffSince;
            if (offDuration >= SCREEN_OFF_DECAY_MS) {
                uutValue = 0;
            }
            unconsciousAppStartTime = -1;
            productiveAppStartTime = -1;
            lastAccumulationTime = -1;
            saveState();
            return;
        }

        screenOffSince = -1;

        if (foregroundPackage == null) {
            saveState();
            return;
        }

        AppCategoryClassifier.AppCategory category = classifier.classify(foregroundPackage);

        // --- App 切换惩罚检测 ---
        if (!foregroundPackage.equals(currentPackage)) {
            recordAppSwitch(foregroundPackage, now);
            checkAppSwitchPenalty(now);

            currentPackage = foregroundPackage;
            unconsciousAppStartTime = -1;
            lastAccumulationTime = -1;
            productiveAppStartTime = -1;
        }

        // --- 无意识使用类别：累加 UUT ---
        if (AppCategoryClassifier.isUnconsciousCategory(category)) {
            productiveAppStartTime = -1;

            if (unconsciousAppStartTime < 0) {
                unconsciousAppStartTime = now;
                lastAccumulationTime = now;
            }

            long elapsed = now - unconsciousAppStartTime;
            if (elapsed > UNCONSCIOUS_GRACE_MS) {
                long timeSinceLastAccum = now - lastAccumulationTime;
                int minutesPassed = (int) (timeSinceLastAccum / 60_000L);
                if (minutesPassed > 0) {
                    uutValue = Math.min(UUT_MAX, uutValue + minutesPassed * ACCUMULATE_PER_MINUTE);
                    lastAccumulationTime = now;
                    Log.d(TAG, "UUT accumulated +" + (minutesPassed * ACCUMULATE_PER_MINUTE)
                            + " → " + uutValue + " (app=" + foregroundPackage + ")");
                }
            }
        }
        // --- 生产力类别：衰减 UUT ---
        else if (AppCategoryClassifier.isProductiveCategory(category)) {
            unconsciousAppStartTime = -1;
            lastAccumulationTime = -1;

            if (productiveAppStartTime < 0) {
                productiveAppStartTime = now;
            }

            long productiveElapsed = now - productiveAppStartTime;
            if (productiveElapsed > PRODUCTIVE_DECAY_START_MS && uutValue > 0) {
                long decayElapsed = productiveElapsed - PRODUCTIVE_DECAY_START_MS;
                int decayMinutes = (int) (decayElapsed / 60_000L);
                int decay = decayMinutes * PRODUCTIVE_DECAY_PER_MINUTE;
                uutValue = Math.max(UUT_MIN, uutValue - decay);
                if (uutValue == 0) {
                    productiveAppStartTime = -1;
                }
                Log.d(TAG, "UUT decayed -" + decay + " → " + uutValue);
            }
        }
        // --- 其他类别：不累加也不衰减 ---
        else {
            unconsciousAppStartTime = -1;
            lastAccumulationTime = -1;
            productiveAppStartTime = -1;
        }

        saveState();
    }

    private void recordAppSwitch(String packageName, long timestamp) {
        recentSwitches.add(new AppSwitchRecord(packageName, timestamp));
        long cutoff = timestamp - APP_SWITCH_WINDOW_MS;
        while (!recentSwitches.isEmpty() && recentSwitches.getFirst().timestamp < cutoff) {
            recentSwitches.removeFirst();
        }
    }

    private void checkAppSwitchPenalty(long now) {
        long windowStart = now - APP_SWITCH_WINDOW_MS;
        java.util.HashSet<String> uniqueApps = new java.util.HashSet<>();
        for (AppSwitchRecord record : recentSwitches) {
            if (record.timestamp >= windowStart) {
                uniqueApps.add(record.packageName);
            }
        }
        if (uniqueApps.size() > APP_SWITCH_THRESHOLD) {
            uutValue = Math.min(UUT_MAX, uutValue + APP_SWITCH_PENALTY);
            recentSwitches.clear();
            Log.d(TAG, "UUT penalty +" + APP_SWITCH_PENALTY
                    + " (switched " + uniqueApps.size() + " apps in 5min) → " + uutValue);
        }
    }

    /**
     * 通知屏幕关闭事件
     */
    public synchronized void onScreenOff() {
        if (screenOffSince < 0) {
            screenOffSince = System.currentTimeMillis();
        }
        unconsciousAppStartTime = -1;
        lastAccumulationTime = -1;
    }

    /**
     * 通知屏幕开启事件
     */
    public synchronized void onScreenOn() {
        long now = System.currentTimeMillis();
        if (screenOffSince > 0) {
            long offDuration = now - screenOffSince;
            if (offDuration >= SCREEN_OFF_DECAY_MS) {
                uutValue = 0;
                saveState();
            }
        }
        screenOffSince = -1;
    }

    public synchronized int getUUT() {
        return uutValue;
    }

    /**
     * 归一化 UUT 值 [0.0, 1.0]，用于 UI 插值
     */
    public synchronized float getUUTNormalized() {
        return uutValue / (float) UUT_MAX;
    }

    /**
     * 获取当前 App 类别文本，用于 LLM Prompt
     */
    public String getCurrentAppCategoryLabel() {
        if (currentPackage == null) return "未知";
        return classifier.classify(currentPackage).labelCn;
    }

    public AppCategoryClassifier.AppCategory getCurrentAppCategory() {
        if (currentPackage == null) return AppCategoryClassifier.AppCategory.OTHER;
        return classifier.classify(currentPackage);
    }

    public String getCurrentPackage() {
        return currentPackage;
    }

    /**
     * 手动重置 UUT（调试用）
     */
    public synchronized void reset() {
        uutValue = 0;
        unconsciousAppStartTime = -1;
        lastAccumulationTime = -1;
        screenOffSince = -1;
        productiveAppStartTime = -1;
        recentSwitches.clear();
        saveState();
    }
}
