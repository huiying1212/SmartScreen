package com.datacollector.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.LinkedList;

/**
 * 无意识使用时间 (Unconscious Usage Time, UUT) 追踪器。
 *
 * UUT 值范围 0-100，反映用户当前"无意识刷手机"的程度。
 *
 * 科学依据:
 * - 时段权重 (Duke & Montag 2017): 深夜/睡前手机使用与焦虑和睡眠障碍强相关
 * - 解锁频率 (Harari et al. 2016, Montag et al. 2021): 每小时解锁次数是无意识使用的核心指标 (r=0.52)
 * - 每日疲劳效应 (Hartmann et al. 2021): 每日使用时长超过3小时后认知控制力下降，加剧无意识使用
 * - 方向性切换惩罚 (Baumgartner et al. 2018): 从生产力→娱乐的切换比随机切换更具有无意识特征
 * - 冲动性短会话 (Billieux et al. 2015): 短暂重复解锁（<90秒会话）是成瘾性使用的强预测因子
 */
public class UnconsciousUsageTracker {

    private static final String TAG = "UUTracker";
    private static final String PREFS_NAME = "uut_state";

    private static final int UUT_MAX = 100;
    private static final int UUT_MIN = 0;

    // --- 基础参数 ---
    private static final long UNCONSCIOUS_GRACE_MS = 3 * 60_000L;       // 3分钟宽限期
    private static final float BASE_ACCUMULATE_PER_MINUTE = 5f;          // 基础累加速率（pts/min）
    private static final long SCREEN_OFF_DECAY_MS = 15 * 60_000L;        // 屏幕关闭→指数衰减参考阈值
    private static final double DECAY_TAU_MINUTES = 10.0;                 // 指数衰减时间常数（分钟）
    private static final long PRODUCTIVE_DECAY_START_MS = 10 * 60_000L;  // 生产力App 10分钟后开始衰减
    private static final int PRODUCTIVE_DECAY_PER_MINUTE = 3;            // 每分钟衰减量

    // --- 切换惩罚 (Baumgartner et al. 2018) ---
    private static final int APP_SWITCH_THRESHOLD = 4;         // 5分钟内切换超过4个App触发
    private static final long APP_SWITCH_WINDOW_MS = 5 * 60_000L;
    private static final int RANDOM_SWITCH_PENALTY = 20;       // 随机切换惩罚
    private static final int PRODUCTIVE_TO_UNCONSCIOUS_PENALTY = 30; // 生产力→娱乐定向切换惩罚
    private static final int DIRECTIONAL_SINGLE_PENALTY = 5;  // 单次定向切换小额惩罚

    // --- 冲动性短会话检测 (Billieux et al. 2015) ---
    private static final long SHORT_SESSION_MS = 90_000L;           // <90秒视为冲动性短会话
    private static final int IMPULSIVE_SESSION_THRESHOLD = 8;        // 1小时内≥8次触发惩罚
    private static final long IMPULSIVE_WINDOW_MS = 60 * 60_000L;   // 1小时检测窗口
    private static final int IMPULSIVE_PENALTY = 15;

    // --- 时段权重数组 (Duke & Montag 2017) ---
    // 索引 = 小时 (0-23)；深夜/睡前权重最高，工作时段权重最低
    private static final float[] TIME_OF_DAY_WEIGHTS = {
        2.0f,  // 00 - 深夜（睡眠剥夺高风险）
        2.0f,  // 01
        2.0f,  // 02
        2.0f,  // 03
        1.8f,  // 04
        1.2f,  // 05 - 早起
        0.8f,  // 06 - 早晨
        0.8f,  // 07
        0.8f,  // 08 - 工作时间
        0.9f,  // 09
        1.0f,  // 10
        1.0f,  // 11
        1.3f,  // 12 - 午休（无意识使用风险较高）
        1.0f,  // 13
        1.0f,  // 14
        1.0f,  // 15
        1.0f,  // 16
        1.0f,  // 17
        1.4f,  // 18 - 傍晚
        1.4f,  // 19
        1.6f,  // 20 - 睡前高风险
        1.8f,  // 21
        2.0f,  // 22 - 深睡前
        2.0f,  // 23
    };

    // --- 状态字段 ---
    private int uutValue;
    private long unconsciousAppStartTime = -1;
    private long lastAccumulationTime = -1;
    private long screenOffSince = -1;
    private long productiveAppStartTime = -1;
    private long currentSessionStart = -1;
    private String currentPackage = null;
    private AppCategoryClassifier.AppCategory previousCategory = null; // 用于方向性切换检测

    // --- 外部指标（由 FloatingOverlayService 注入） ---
    private int unlockCountLastHour = 0;   // 最近1小时解锁次数
    private long todayScreenTimeMs = 0;    // 今日累计屏幕时间

    private final Context appContext;
    private final LinkedList<AppSwitchRecord> recentSwitches = new LinkedList<>();
    private final LinkedList<Long> shortSessionTimestamps = new LinkedList<>();
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
        this.appContext = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.classifier = new AppCategoryClassifier(context);
        restoreState();
    }

    private void restoreState() {
        uutValue = prefs.getInt("uut_value", 0);
        long lastSave = prefs.getLong("last_save_time", 0);
        double elapsedMinutes = (System.currentTimeMillis() - lastSave) / 60_000.0;
        // 指数衰减：长时间未保存时 UUT 自然趋近于零
        int decayed = (int) Math.round(uutValue * Math.exp(-elapsedMinutes / DECAY_TAU_MINUTES));
        uutValue = Math.max(UUT_MIN, decayed);
    }

    private void saveState() {
        prefs.edit()
                .putInt("uut_value", uutValue)
                .putLong("last_save_time", System.currentTimeMillis())
                .apply();
    }

    /**
     * 注入外部指标（由 FloatingOverlayService 每次 refreshUUT 时调用）。
     * 数据来自 ScreenUsageCollector，用于时段权重和疲劳因子计算。
     *
     * @param unlockCountLastHour 最近1小时解锁次数 (Harari et al. 2016)
     * @param todayScreenTimeMs   今日累计屏幕时间（毫秒）(Hartmann et al. 2021)
     */
    public synchronized void updateExternalMetrics(int unlockCountLastHour, long todayScreenTimeMs) {
        this.unlockCountLastHour = unlockCountLastHour;
        this.todayScreenTimeMs = todayScreenTimeMs;
    }

    /**
     * 核心更新方法，由 FloatingOverlayService 定期调用（建议 30s 间隔）。
     *
     * @param foregroundPackage 当前前台 App 包名，null 表示无法获取
     * @param screenOn          屏幕是否亮着
     */
    public synchronized void update(String foregroundPackage, boolean screenOn) {
        long now = System.currentTimeMillis();

        // --- 屏幕关闭处理：指数衰减 ---
        if (!screenOn) {
            if (screenOffSince < 0) {
                screenOffSince = now;
            }
            long offDuration = now - screenOffSince;
            double offMinutes = offDuration / 60_000.0;
            // 指数衰减：uut = uut * e^(-offMinutes / tau)
            int decayed = (int) Math.round(uutValue * Math.exp(-offMinutes / DECAY_TAU_MINUTES));
            uutValue = Math.max(UUT_MIN, decayed);
            unconsciousAppStartTime = -1;
            productiveAppStartTime = -1;
            lastAccumulationTime = -1;
            currentSessionStart = -1;
            saveState();
            return;
        }

        screenOffSince = -1;

        if (foregroundPackage == null) {
            saveState();
            return;
        }

        AppCategoryClassifier.AppCategory category = classifier.classify(foregroundPackage);

        // --- App 切换检测 ---
        if (!foregroundPackage.equals(currentPackage)) {
            // 冲动性短会话检测 (Billieux et al. 2015)
            if (currentSessionStart > 0) {
                long sessionDuration = now - currentSessionStart;
                if (sessionDuration < SHORT_SESSION_MS) {
                    recordShortSession(now);
                    checkImpulsivePenalty(now);
                }
            }
            currentSessionStart = now;

            // 方向性切换检测：生产力 → 娱乐 (Baumgartner et al. 2018)
            boolean isDirectionalSwitch = previousCategory != null
                    && AppCategoryClassifier.isProductiveCategory(previousCategory)
                    && AppCategoryClassifier.isUnconsciousCategory(category);

            recordAppSwitch(foregroundPackage, now);
            checkAppSwitchPenalty(now, isDirectionalSwitch);

            currentPackage = foregroundPackage;
            unconsciousAppStartTime = -1;
            lastAccumulationTime = -1;
            productiveAppStartTime = -1;
        }

        previousCategory = category;

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
                    float timeWeight = getTimeOfDayWeight();
                    float fatigueFactor = getDailyFatigueFactor();
                    float unlockBonus = getUnlockRatePerMinute();

                    // 综合累加公式：受时段、疲劳、解锁频率共同调制
                    float rawIncrease = minutesPassed
                            * (BASE_ACCUMULATE_PER_MINUTE + unlockBonus)
                            * timeWeight * fatigueFactor;
                    int deltaUUT = Math.round(rawIncrease);

                    uutValue = Math.min(UUT_MAX, uutValue + deltaUUT);
                    lastAccumulationTime = now;
                    Log.d(TAG, "UUT +=" + deltaUUT
                            + " [timeW=" + String.format("%.1f", timeWeight)
                            + " fatigF=" + String.format("%.1f", fatigueFactor)
                            + " unlockB=" + String.format("%.2f", unlockBonus)
                            + "] → " + uutValue + " (" + foregroundPackage + ")");
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
                Log.d(TAG, "UUT -=" + decay + " → " + uutValue);
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

    // --- 时段权重 (Duke & Montag 2017) ---
    private float getTimeOfDayWeight() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return TIME_OF_DAY_WEIGHTS[hour];
    }

    // --- 每日疲劳因子 (Hartmann et al. 2021) ---
    // >3小时使用后认知控制能力下降，>5小时后显著受损
    private float getDailyFatigueFactor() {
        long screenMins = todayScreenTimeMs / 60_000L;
        if (screenMins > 300) return 1.6f;  // >5小时: 高度疲劳
        if (screenMins > 180) return 1.3f;  // >3小时: 中度疲劳
        return 1.0f;
    }

    // --- 解锁频率补偿速率 (Harari et al. 2016, Montag et al. 2021) ---
    // 每小时解锁次数转为每分钟额外累加速率
    private float getUnlockRatePerMinute() {
        int score;
        if (unlockCountLastHour >= 20) score = 40;
        else if (unlockCountLastHour >= 10) score = 25;
        else if (unlockCountLastHour >= 5) score = 10;
        else score = 0;
        return score / 60f;
    }

    // --- 冲动性短会话 (Billieux et al. 2015) ---
    private void recordShortSession(long timestamp) {
        shortSessionTimestamps.add(timestamp);
        long cutoff = timestamp - IMPULSIVE_WINDOW_MS;
        while (!shortSessionTimestamps.isEmpty()
                && shortSessionTimestamps.getFirst() < cutoff) {
            shortSessionTimestamps.removeFirst();
        }
    }

    private void checkImpulsivePenalty(long now) {
        if (shortSessionTimestamps.size() >= IMPULSIVE_SESSION_THRESHOLD) {
            uutValue = Math.min(UUT_MAX, uutValue + IMPULSIVE_PENALTY);
            shortSessionTimestamps.clear();
            Log.d(TAG, "UUT impulsive penalty +" + IMPULSIVE_PENALTY
                    + " (" + IMPULSIVE_SESSION_THRESHOLD + "+ short sessions/hr) → " + uutValue);
        }
    }

    private void recordAppSwitch(String packageName, long timestamp) {
        recentSwitches.add(new AppSwitchRecord(packageName, timestamp));
        long cutoff = timestamp - APP_SWITCH_WINDOW_MS;
        while (!recentSwitches.isEmpty() && recentSwitches.getFirst().timestamp < cutoff) {
            recentSwitches.removeFirst();
        }
    }

    private void checkAppSwitchPenalty(long now, boolean isDirectionalSwitch) {
        long windowStart = now - APP_SWITCH_WINDOW_MS;
        java.util.HashSet<String> uniqueApps = new java.util.HashSet<>();
        for (AppSwitchRecord record : recentSwitches) {
            if (record.timestamp >= windowStart) {
                uniqueApps.add(record.packageName);
            }
        }
        if (uniqueApps.size() > APP_SWITCH_THRESHOLD) {
            // 方向性切换（生产力→娱乐）使用更高惩罚 (Baumgartner et al. 2018)
            int penalty = isDirectionalSwitch
                    ? PRODUCTIVE_TO_UNCONSCIOUS_PENALTY : RANDOM_SWITCH_PENALTY;
            uutValue = Math.min(UUT_MAX, uutValue + penalty);
            recentSwitches.clear();
            Log.d(TAG, "UUT switch penalty +" + penalty
                    + " (directional=" + isDirectionalSwitch
                    + ", apps=" + uniqueApps.size() + ") → " + uutValue);
        } else if (isDirectionalSwitch) {
            // 即使未达到切换阈值，单次生产力→娱乐切换也给予小额惩罚
            uutValue = Math.min(UUT_MAX, uutValue + DIRECTIONAL_SINGLE_PENALTY);
            Log.d(TAG, "UUT directional single penalty +" + DIRECTIONAL_SINGLE_PENALTY
                    + " → " + uutValue);
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
        currentSessionStart = -1;
    }

    /**
     * 通知屏幕开启事件
     */
    public synchronized void onScreenOn() {
        long now = System.currentTimeMillis();
        if (screenOffSince > 0) {
            double offMinutes = (now - screenOffSince) / 60_000.0;
            int decayed = (int) Math.round(uutValue * Math.exp(-offMinutes / DECAY_TAU_MINUTES));
            uutValue = Math.max(UUT_MIN, decayed);
            saveState();
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
     * 返回当前前台 App 包名。
     * 优先从 AppForegroundTracker 读取（最新鲜、最可信）；
     * Tracker 过期时 fallback 到本地缓存副本。
     */
    @Nullable
    public String getCurrentPackage() {
        String tracked = AppForegroundTracker.getInstance(appContext).getCurrentPackage();
        return tracked != null ? tracked : currentPackage;
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
        currentSessionStart = -1;
        previousCategory = null;
        recentSwitches.clear();
        shortSessionTimestamps.clear();
        unlockCountLastHour = 0;
        todayScreenTimeMs = 0;
        saveState();
    }
}
