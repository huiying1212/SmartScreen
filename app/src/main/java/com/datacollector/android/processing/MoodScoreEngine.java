package com.datacollector.android.processing;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.datacollector.android.collectors.ScreenUsageCollector;
import com.datacollector.android.utils.CollectionConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Map;

/**
 * 多维度心情评分引擎。
 *
 * 综合四个维度计算最终 stress 值 [0, 1]：
 *   D1 — dailyUsageScore:        全天屏幕时长评估
 *   D2 — entertainmentRatioScore: 娱乐占比评估
 *   D3 — sessionIntensityScore:   当前会话强度 (UUT)
 *   D4 — goalComplianceScore:     个人目标达成度
 *
 * 最终 stress = w1*D1 + w2*D2 + w3*D3 + w4*D4
 */
public class MoodScoreEngine {

    private static final String TAG = "MoodScoreEngine";

    // 默认权重
    private static final float W_DAILY_USAGE = 0.20f;
    private static final float W_ENTERTAINMENT_RATIO = 0.20f;
    private static final float W_SESSION_INTENSITY = 0.35f;
    private static final float W_GOAL_COMPLIANCE = 0.25f;

    // dailyUsageScore 默认基准（分钟）
    private static final int DEFAULT_DAILY_LIMIT_MINUTES = 300; // 5 小时

    // entertainmentRatioScore 阈值
    private static final float ENT_RATIO_SOFT = 0.60f;
    private static final float ENT_RATIO_HARD = 0.85f;
    private static final long ENT_MIN_TOTAL_MS = 30 * 60_000L; // 30 分钟最低总量

    // 最新一次计算的各维度分数（供调试面板读取）
    private float lastD1, lastD2, lastD3, lastD4, lastFinal;

    private final Context context;
    private final CollectionConfig config;

    public MoodScoreEngine(Context context) {
        this.context = context.getApplicationContext();
        this.config = CollectionConfig.getInstance(context);
    }

    /**
     * 核心计算方法。返回最终 stress [0, 1]。
     *
     * @param uut                当前 UUT 值 (0-100)
     * @param screenUsageCollector 屏幕使用收集器（用于获取今日分类时长）
     * @return stress [0.0, 1.0]
     */
    public float computeStress(int uut, ScreenUsageCollector screenUsageCollector) {
        Map<String, Long> categoryUsage = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                && screenUsageCollector != null) {
            try {
                categoryUsage = screenUsageCollector.getCategoryUsageToday();
            } catch (Exception e) {
                Log.w(TAG, "Failed to get category usage", e);
            }
        }

        long totalMs = 0;
        long entertainmentMs = 0;
        if (categoryUsage != null) {
            totalMs = categoryUsage.getOrDefault("__total__", 0L);
            entertainmentMs += categoryUsage.getOrDefault("social", 0L);
            entertainmentMs += categoryUsage.getOrDefault("entertainment", 0L);
            entertainmentMs += categoryUsage.getOrDefault("short_video", 0L);
            entertainmentMs += categoryUsage.getOrDefault("games", 0L);
        }

        // D1: 全天屏幕时长
        lastD1 = calcDailyUsageScore(totalMs);

        // D2: 娱乐占比
        lastD2 = calcEntertainmentRatioScore(entertainmentMs, totalMs);

        // D3: 当前会话强度 (UUT smoothstep)
        lastD3 = calcSessionIntensityScore(uut);

        // D4: 目标达成度
        lastD4 = calcGoalComplianceScore(categoryUsage, screenUsageCollector);

        // 加权融合
        boolean hasGoals = hasStructuredGoals();
        float w1, w2, w3, w4;
        if (hasGoals) {
            w1 = W_DAILY_USAGE;
            w2 = W_ENTERTAINMENT_RATIO;
            w3 = W_SESSION_INTENSITY;
            w4 = W_GOAL_COMPLIANCE;
        } else {
            // 无结构化目标时，w4 的权重分配给其他三个维度
            float redistribute = W_GOAL_COMPLIANCE / 3f;
            w1 = W_DAILY_USAGE + redistribute;
            w2 = W_ENTERTAINMENT_RATIO + redistribute;
            w3 = W_SESSION_INTENSITY + redistribute;
            w4 = 0f;
        }

        lastFinal = clamp(w1 * lastD1 + w2 * lastD2 + w3 * lastD3 + w4 * lastD4);

        // 夜间加权：22:00 后整体 stress 提升（该休息了）
        float nightBoost = calcNightBoost();
        if (nightBoost > 0 && lastFinal > 0.05f) {
            lastFinal = clamp(lastFinal + nightBoost);
        }

        Log.d(TAG, String.format("Stress=%.3f [D1=%.2f D2=%.2f D3=%.2f D4=%.2f night=+%.2f goals=%b]",
                lastFinal, lastD1, lastD2, lastD3, lastD4, nightBoost, hasGoals));
        return lastFinal;
    }

    // ── 维度 1: 全天屏幕时长 ──────────────────────────────────

    private float calcDailyUsageScore(long totalMs) {
        int dailyLimitMin = getDailyScreenLimitMinutes();
        float softLimit = dailyLimitMin * 0.80f;
        float hardLimit = dailyLimitMin * 1.50f;
        float actualMin = totalMs / 60_000f;
        return clamp((actualMin - softLimit) / (hardLimit - softLimit));
    }

    private int getDailyScreenLimitMinutes() {
        // 从结构化目标中查找 daily_screen_limit
        try {
            JSONArray goals = new JSONArray(
                    config.getString(CollectionConfig.KEY_STRUCTURED_GOALS, "[]"));
            for (int i = 0; i < goals.length(); i++) {
                JSONObject g = goals.optJSONObject(i);
                if (g != null && "daily_screen_limit".equals(g.optString("type"))) {
                    int limit = g.optInt("limit_minutes", 0);
                    if (limit > 0) return limit;
                }
            }
        } catch (Exception ignored) {}
        return DEFAULT_DAILY_LIMIT_MINUTES;
    }

    // ── 维度 2: 娱乐占比 ──────────────────────────────────────

    private float calcEntertainmentRatioScore(long entertainmentMs, long totalMs) {
        if (totalMs < ENT_MIN_TOTAL_MS) {
            // 总使用时长太短，比例没意义，返回低分
            return 0f;
        }
        float ratio = (float) entertainmentMs / totalMs;
        return clamp((ratio - ENT_RATIO_SOFT) / (ENT_RATIO_HARD - ENT_RATIO_SOFT));
    }

    // ── 维度 3: 当前会话强度 (UUT) ────────────────────────────

    private float calcSessionIntensityScore(int uut) {
        float t = clamp(uut / 100f);
        // smoothstep ease-in-out
        return t * t * (3f - 2f * t);
    }

    // ── 维度 4: 目标达成度 ────────────────────────────────────

    private float calcGoalComplianceScore(Map<String, Long> categoryUsage,
                                          ScreenUsageCollector collector) {
        float worstViolation = 0f;
        try {
            JSONArray goals = new JSONArray(
                    config.getString(CollectionConfig.KEY_STRUCTURED_GOALS, "[]"));
            for (int i = 0; i < goals.length(); i++) {
                JSONObject g = goals.optJSONObject(i);
                if (g == null) continue;
                String type = g.optString("type", "");
                int limitMin = g.optInt("limit_minutes", 0);
                if (limitMin <= 0) continue;

                float violation = 0f;
                switch (type) {
                    case "daily_screen_limit": {
                        // 已在 D1 中处理，这里用更严格的映射
                        long totalMs = categoryUsage != null
                                ? categoryUsage.getOrDefault("__total__", 0L) : 0;
                        float actualMin = totalMs / 60_000f;
                        float softLimit = limitMin * 0.90f;
                        float hardLimit = limitMin * 1.30f;
                        violation = clamp((actualMin - softLimit) / (hardLimit - softLimit));
                        break;
                    }
                    case "category_limit": {
                        String category = g.optString("category", "");
                        long catMs = categoryUsage != null
                                ? categoryUsage.getOrDefault(category, 0L) : 0;
                        float actualMin = catMs / 60_000f;
                        float softLimit = limitMin * 0.85f;
                        float hardLimit = limitMin * 1.50f;
                        violation = clamp((actualMin - softLimit) / (hardLimit - softLimit));
                        break;
                    }
                    case "app_limit": {
                        String pkg = g.optString("package", "");
                        long appMs = 0;
                        if (!pkg.isEmpty() && collector != null
                                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            try {
                                appMs = collector.getAppUsageTodayMs(pkg);
                            } catch (Exception ignored) {}
                        }
                        float actualMin = appMs / 60_000f;
                        float softLimit = limitMin * 0.85f;
                        float hardLimit = limitMin * 1.50f;
                        violation = clamp((actualMin - softLimit) / (hardLimit - softLimit));
                        break;
                    }
                }
                worstViolation = Math.max(worstViolation, violation);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing structured goals", e);
        }
        return worstViolation;
    }

    // ── 夜间加权 ──────────────────────────────────────────────

    private float calcNightBoost() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        // 22:00-23:59 → 逐渐增加 0~0.10 的 boost
        if (hour >= 22) {
            int minutesPast22 = (hour - 22) * 60 + cal.get(Calendar.MINUTE);
            return clamp(minutesPast22 / 120f) * 0.10f;
        }
        // 00:00-05:59 → 固定 0.10 boost
        if (hour < 6) {
            return 0.10f;
        }
        return 0f;
    }

    // ── 辅助方法 ──────────────────────────────────────────────

    private boolean hasStructuredGoals() {
        try {
            JSONArray goals = new JSONArray(
                    config.getString(CollectionConfig.KEY_STRUCTURED_GOALS, "[]"));
            return goals.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ── 调试用 Getter ─────────────────────────────────────────

    public float getLastD1() { return lastD1; }
    public float getLastD2() { return lastD2; }
    public float getLastD3() { return lastD3; }
    public float getLastD4() { return lastD4; }
    public float getLastFinalStress() { return lastFinal; }

    /**
     * 返回各维度的调试摘要字符串。
     */
    public String getDebugSummary() {
        return String.format(
                "最终stress: %.3f\n" +
                "D1 全天时长: %.3f (权重%.0f%%)\n" +
                "D2 娱乐占比: %.3f (权重%.0f%%)\n" +
                "D3 会话强度: %.3f (权重%.0f%%)\n" +
                "D4 目标达成: %.3f (权重%.0f%%)\n" +
                "夜间加权: +%.3f\n" +
                "结构化目标: %s",
                lastFinal,
                lastD1, (hasStructuredGoals() ? W_DAILY_USAGE : W_DAILY_USAGE + W_GOAL_COMPLIANCE / 3f) * 100,
                lastD2, (hasStructuredGoals() ? W_ENTERTAINMENT_RATIO : W_ENTERTAINMENT_RATIO + W_GOAL_COMPLIANCE / 3f) * 100,
                lastD3, (hasStructuredGoals() ? W_SESSION_INTENSITY : W_SESSION_INTENSITY + W_GOAL_COMPLIANCE / 3f) * 100,
                lastD4, (hasStructuredGoals() ? W_GOAL_COMPLIANCE : 0f) * 100,
                calcNightBoost(),
                hasStructuredGoals() ? "已设置" : "未设置"
        );
    }
}
