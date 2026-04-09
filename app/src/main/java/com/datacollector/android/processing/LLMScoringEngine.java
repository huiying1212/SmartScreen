package com.datacollector.android.processing;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.datacollector.android.api.DeepSeekApiClient;

import org.json.JSONObject;

/**
 * LLM 驱动的使用评分引擎。
 *
 * 完全由 LLM 驱动的使用状态评分引擎，根据完整采集快照进行增量评估。
 *
 * 设计要点：
 *   - 每次评估时，将上一次快照 + 上一次分数 + 本次最新快照发送给 LLM
 *   - LLM 返回一个 delta 值（增量），受以下约束：
 *       · 生产力 App 使用 → delta = 0
 *       · 娱乐 App 持续使用 → delta = +1
 *       · 深夜 / 无意识使用迹象 → delta 最高 +5
 *       · 屏幕关闭 / 主动休息 → delta 可为负数，最低 -5
 *   - 分数范围 [0, 100]
 *   - 时间间隔规则（Java 侧预处理，不依赖 LLM）：
 *       · 距上次快照 ≥ 5h → 判定为睡眠/长时间离开，直接清零
 *       · 距上次快照 ≥ 1h → 判定为较长休息，先扣 10 分再交给 LLM 评估
 *   - 上一次快照和分数持久化到 SharedPreferences，支持进程重启恢复
 */
public class LLMScoringEngine {

    private static final String TAG = "LLMScoringEngine";
    private static final String PREFS_NAME = "llm_scoring_state";
    private static final String KEY_CURRENT_SCORE = "current_score";
    private static final String KEY_LAST_SNAPSHOT = "last_snapshot";
    private static final String KEY_LAST_REASON = "last_reason";
    private static final String KEY_LAST_SNAPSHOT_TS = "last_snapshot_ts";

    // 时间间隔阈值
    private static final long SLEEP_GAP_MS   = 5 * 60 * 60 * 1000L; // 5 小时 → 清零
    private static final long BREAK_GAP_MS   = 1 * 60 * 60 * 1000L; // 1 小时 → 扣 10 分

    private final Context appContext;
    private final DeepSeekApiClient deepSeekClient;
    private final SharedPreferences prefs;

    private volatile int currentScore;
    private volatile String lastSnapshotJson;
    private volatile String lastReason;
    private volatile long lastSnapshotTs;   // 上次快照的 Unix 毫秒时间戳
    private volatile boolean isAssessing = false;

    public LLMScoringEngine(Context context, DeepSeekApiClient deepSeekClient) {
        this.appContext = context.getApplicationContext();
        this.deepSeekClient = deepSeekClient;
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        restoreState();
    }

    private void restoreState() {
        currentScore     = prefs.getInt(KEY_CURRENT_SCORE, 0);
        lastSnapshotJson = prefs.getString(KEY_LAST_SNAPSHOT, null);
        lastReason       = prefs.getString(KEY_LAST_REASON, null);
        lastSnapshotTs   = prefs.getLong(KEY_LAST_SNAPSHOT_TS, 0L);
        Log.i(TAG, "Restored score=" + currentScore
                + " lastSnapshotTs=" + lastSnapshotTs);
    }

    /**
     * 请求 LLM 评估新快照并更新分数。同步调用，需在后台线程执行。
     *
     * <p>在调用 LLM 之前，Java 侧会先根据时间间隔做预处理：
     * <ul>
     *   <li>距上次快照 ≥ 5h → 判定为睡眠/长时间离开，直接清零，跳过 LLM</li>
     *   <li>距上次快照 ≥ 1h → 判定为较长休息，先扣 10 分，再交给 LLM 评估</li>
     * </ul>
     *
     * @param newSnapshot 本次最新采集的完整上下文快照（需含顶层 "timestamp" 毫秒字段）
     * @return 更新后的分数 (0-100)，如果 LLM 调用失败则返回当前分数不变
     */
    public synchronized int assess(JSONObject newSnapshot) {
        if (isAssessing) {
            Log.w(TAG, "Assessment already in progress, skipping");
            return currentScore;
        }
        isAssessing = true;

        try {
            // ── 时间间隔预处理 ──────────────────────────────────────
            long newTs = newSnapshot.optLong("timestamp", System.currentTimeMillis());
            String gapReason = null;

            if (lastSnapshotTs > 0) {
                long gapMs = newTs - lastSnapshotTs;

                if (gapMs >= SLEEP_GAP_MS) {
                    long gapHours = gapMs / (60 * 60 * 1000L);
                    gapReason = "距上次快照 " + gapHours + " 小时，判定为睡眠/长时间离开，分数清零";
                    Log.i(TAG, "Gap=" + gapHours + "h ≥ 5h → reset to 0, then LLM assess. " + gapReason);
                    currentScore = 0;
                    // 继续走 LLM，基于清零后的分数评估当前快照

                } else if (gapMs >= BREAK_GAP_MS) {
                    long gapMins = gapMs / (60 * 1000L);
                    gapReason = "距上次快照 " + gapMins + " 分钟，判定为较长休息，预扣 10 分";
                    Log.i(TAG, "Gap=" + gapMins + "min ≥ 60min → pre-deduct 10. " + gapReason);
                    currentScore = Math.max(0, currentScore - 10);
                    // 继续走 LLM，让它在此基础上再评估
                }
            }

            // ── LLM 评估 ────────────────────────────────────────────
            String result = deepSeekClient.assessUsageScore(
                    currentScore, lastSnapshotJson, lastReason, newSnapshot);

            if (result == null || result.isEmpty()) {
                Log.w(TAG, "LLM returned empty, keeping score=" + currentScore);
                // 即使 LLM 失败，也要更新时间戳，避免下次误判间隔
                lastSnapshotTs = newTs;
                prefs.edit().putLong(KEY_LAST_SNAPSHOT_TS, lastSnapshotTs).apply();
                return currentScore;
            }

            // Parse response: expect JSON like {"delta": 2, "reason": "..."}
            JSONObject resp = new JSONObject(result);
            int delta = resp.optInt("delta", 0);
            String reason = resp.optString("reason", "");

            // Clamp delta to [-5, +5]
            delta = Math.max(-5, Math.min(5, delta));

            // Apply delta and clamp score to [0, 100]
            int newScore = Math.max(0, Math.min(100, currentScore + delta));

            // 如果有预处理原因，拼接到 LLM 理由前面
            if (gapReason != null && !gapReason.isEmpty()) {
                reason = reason.isEmpty() ? gapReason : gapReason + "；" + reason;
            }

            Log.i(TAG, "Score: " + currentScore + " → " + newScore
                    + " (delta=" + delta + ") reason: " + reason);

            currentScore = newScore;
            JSONObject sanitizedForStorage = DataSanitizer.sanitizeSnapshot(newSnapshot);
            lastSnapshotJson = sanitizedForStorage != null
                    ? sanitizedForStorage.toString() : newSnapshot.toString();
            lastReason = reason;
            lastSnapshotTs = newTs;

            // Persist
            prefs.edit()
                    .putInt(KEY_CURRENT_SCORE, currentScore)
                    .putString(KEY_LAST_SNAPSHOT, lastSnapshotJson)
                    .putString(KEY_LAST_REASON, lastReason)
                    .putLong(KEY_LAST_SNAPSHOT_TS, lastSnapshotTs)
                    .apply();

            return currentScore;

        } catch (Exception e) {
            Log.e(TAG, "Assessment failed", e);
            return currentScore;
        } finally {
            isAssessing = false;
        }
    }

    /** 获取当前分数 (0-100)。从 SharedPreferences 读取最新值，支持跨进程/跨实例同步。 */
    public int getScore() {
        currentScore = prefs.getInt(KEY_CURRENT_SCORE, 0);
        return currentScore;
    }

    /** 获取归一化分数 [0.0, 1.0]，供 MoodFaceView 使用。 */
    public float getScoreNormalized() {
        return getScore() / 100f;
    }

    /** 获取上次 LLM 评估的理由。从 SharedPreferences 读取最新值。 */
    public String getLastReason() {
        lastReason = prefs.getString(KEY_LAST_REASON, null);
        return lastReason;
    }

    /** 手动重置分数（如用户主动休息）。 */
    public void reset() {
        currentScore = 0;
        lastReason = "manual reset";
        prefs.edit().putInt(KEY_CURRENT_SCORE, 0)
                .putString(KEY_LAST_REASON, lastReason).apply();
        Log.i(TAG, "Score manually reset to 0");
    }
}
