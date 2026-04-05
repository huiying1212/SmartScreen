package com.datacollector.android.processing;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.datacollector.android.api.DeepSeekApiClient;

import org.json.JSONObject;

/**
 * LLM 驱动的使用评分引擎。
 *
 * 取代基于手工公式的 UUT 评分，改由 LLM 根据完整采集快照进行增量评估。
 *
 * 设计要点：
 *   - 每次评估时，将上一次快照 + 上一次分数 + 本次最新快照发送给 LLM
 *   - LLM 返回一个 delta 值（增量），受以下约束：
 *       · 生产力 App 使用 → delta = 0
 *       · 娱乐 App 持续使用 → delta = +1
 *       · 深夜 / 无意识使用迹象 → delta 最高 +5
 *       · 屏幕关闭 / 主动休息 → delta 可为负数，最低 -5
 *   - 分数范围 [0, 100]，每日重置为 0
 *   - 上一次快照和分数持久化到 SharedPreferences，支持进程重启恢复
 */
public class LLMScoringEngine {

    private static final String TAG = "LLMScoringEngine";
    private static final String PREFS_NAME = "llm_scoring_state";
    private static final String KEY_CURRENT_SCORE = "current_score";
    private static final String KEY_LAST_SNAPSHOT = "last_snapshot";
    private static final String KEY_LAST_REASON = "last_reason";
    private static final String KEY_LAST_DATE = "last_date";

    private final Context appContext;
    private final DeepSeekApiClient deepSeekClient;
    private final SharedPreferences prefs;

    private volatile int currentScore;
    private volatile String lastSnapshotJson;
    private volatile String lastReason;
    private volatile boolean isAssessing = false;

    public LLMScoringEngine(Context context, DeepSeekApiClient deepSeekClient) {
        this.appContext = context.getApplicationContext();
        this.deepSeekClient = deepSeekClient;
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        restoreState();
    }

    private void restoreState() {
        // Daily reset: if the stored date differs from today, start fresh
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.US).format(new java.util.Date());
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        if (!today.equals(lastDate)) {
            currentScore = 0;
            lastSnapshotJson = null;
            lastReason = null;
            prefs.edit()
                    .putInt(KEY_CURRENT_SCORE, 0)
                    .putString(KEY_LAST_SNAPSHOT, null)
                    .putString(KEY_LAST_REASON, null)
                    .putString(KEY_LAST_DATE, today)
                    .apply();
            Log.i(TAG, "New day — score reset to 0");
        } else {
            currentScore = prefs.getInt(KEY_CURRENT_SCORE, 0);
            lastSnapshotJson = prefs.getString(KEY_LAST_SNAPSHOT, null);
            lastReason = prefs.getString(KEY_LAST_REASON, null);
            Log.i(TAG, "Restored score=" + currentScore);
        }
    }

    /**
     * 请求 LLM 评估新快照并更新分数。同步调用，需在后台线程执行。
     *
     * @param newSnapshot 本次最新采集的完整上下文快照
     * @return 更新后的分数 (0-100)，如果 LLM 调用失败则返回当前分数不变
     */
    public synchronized int assess(JSONObject newSnapshot) {
        if (isAssessing) {
            Log.w(TAG, "Assessment already in progress, skipping");
            return currentScore;
        }
        isAssessing = true;

        try {
            String result = deepSeekClient.assessUsageScore(
                    currentScore, lastSnapshotJson, lastReason, newSnapshot);

            if (result == null || result.isEmpty()) {
                Log.w(TAG, "LLM returned empty, keeping score=" + currentScore);
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

            Log.i(TAG, "Score: " + currentScore + " → " + newScore
                    + " (delta=" + delta + ") reason: " + reason);

            currentScore = newScore;
            // 存储到 SharedPreferences 的快照也做脱敏处理
            JSONObject sanitizedForStorage = DataSanitizer.sanitizeSnapshot(newSnapshot);
            lastSnapshotJson = sanitizedForStorage != null
                    ? sanitizedForStorage.toString() : newSnapshot.toString();
            lastReason = reason;

            // Persist
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                    java.util.Locale.US).format(new java.util.Date());
            prefs.edit()
                    .putInt(KEY_CURRENT_SCORE, currentScore)
                    .putString(KEY_LAST_SNAPSHOT, lastSnapshotJson)
                    .putString(KEY_LAST_REASON, lastReason)
                    .putString(KEY_LAST_DATE, today)
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
