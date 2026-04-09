package com.datacollector.android.api;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.CollectionStats;
import com.datacollector.android.processing.DataSanitizer;
import com.datacollector.android.utils.RetryHelper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek API 客户端，提供：
 * 1. 壁纸引擎关键词提取（3 个元素词）
 * 2. 悬浮窗气泡文本生成（使用小结 + 建议，30～60 字）
 * 3. 通用聊天完成接口
 */
public class DeepSeekApiClient {

    private static final String TAG = "DeepSeekApiClient";

    private final OkHttpClient httpClient;
    private final Context context;
    private final CollectionStats stats;

    public interface SimpleCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public DeepSeekApiClient(Context context) {
        this.context = context;
        this.stats = CollectionStats.getInstance(context);

        CollectionConfig config = CollectionConfig.getInstance(context);
        int connectTimeout = config.getInt(CollectionConfig.KEY_API_CONNECT_TIMEOUT, 30);
        int readTimeout = config.getInt(CollectionConfig.KEY_API_READ_TIMEOUT, 60);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // ── 壁纸引擎：提取场景关键词 ────────────────────────────

    /**
     * 根据聚合数据，让 LLM 提炼出能代表用户这段时间使用场景的关键词（同步调用，需在后台线程执行）。
     *
     * @return 场景关键词字符串（如 "工作电脑、咖啡、闹钟"），失败返回 null
     */
    public String extractKeywords(JSONObject aggregatedData, String weightDescription) {
        if (!ApiConfig.isDeepSeekApiKeyConfigured()) return null;

        try {
            // 脱敏聚合数据后再发送给 LLM
            JSONObject sanitizedData = DataSanitizer.sanitizeAggregatedData(aggregatedData);

            String systemPrompt = "你是一位擅长场景化表达的创意概念提炼师。"
                    + "根据用户近几小时的手机使用数据，提炼出最能描绘用户这段时间生活场景的纯景物关键词。\n\n"
                    + "规则：\n"
                    + "1. 关键词数量不限，通常 3-6 个，视数据丰富程度而定\n"
                    + "2. 关键词必须是具体的、有画面感的事物或静物场景元素，且【绝对不能包含人物、人群或任何生物】\n"
                    + "3. 场景需注重“写实感”和“环境氛围”，避免任何魔幻、超现实或抽象元素\n"
                    + "4. 例如：如果用户在工作，可以是「办公桌、键盘、半杯咖啡、百叶窗透过的光」；"
                    + "如果以娱乐为主，可以是「舒适的沙发、亮着的屏幕、零食、室内暖光」；"
                    + "如果在运动，可以是「空旷的跑道、阳光、树影、运动水壶」\n"
                    + "5. 用中文顿号分隔，不要输出任何解释，只输出关键词\n\n"
                    + "偏好权重说明：\n" + weightDescription;

            String userContent = "用户手机使用数据摘要：\n" + summarizeForKeywords(sanitizedData);

            String response = callChatSync(systemPrompt, userContent, 100, 0.8f);
            if (response != null) {
                response = response.trim().replaceAll("[\"'\\s]+$", "").replaceAll("^[\"'\\s]+", "");
                if (response.length() > 100) response = response.substring(0, 100);
            }
            return response;

        } catch (Exception e) {
            Log.e(TAG, "extractKeywords failed", e);
            return null;
        }
    }

    public String summarizeForKeywords(JSONObject data) {
        StringBuilder sb = new StringBuilder();
        try {
            // 0. 数据时间范围
            long rangeStart = data.optLong("time_range_start", 0);
            long rangeEnd = data.optLong("time_range_end", 0);
            if (rangeStart > 0 && rangeEnd > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                sb.append("【数据时间范围】\n");
                sb.append("  从 ").append(sdf.format(new java.util.Date(rangeStart)));
                sb.append(" 到 ").append(sdf.format(new java.util.Date(rangeEnd))).append("\n");
            }

            // 1. 各分类 App 使用时长（最能反映用户在做什么）
            JSONObject catUsage = data.optJSONObject("category_usage_minutes");
            if (catUsage != null && catUsage.length() > 0) {
                sb.append("【各类App使用时长】\n");
                java.util.Iterator<String> keys = catUsage.keys();
                while (keys.hasNext()) {
                    String cat = keys.next();
                    long mins = catUsage.optLong(cat, 0);
                    if (mins > 0) sb.append("  ").append(cat).append(": ").append(mins).append("分钟\n");
                }
            }

            // 2. Top Apps 明细（具体在用什么 App）
            JSONObject su = data.optJSONObject("screen_usage");
            if (su != null) {
                sb.append("【屏幕总时长】").append(su.optString("today_screen_time_readable", "未知")).append("\n");
                JSONArray topApps = su.optJSONArray("top_apps_today");
                if (topApps != null && topApps.length() > 0) {
                    sb.append("【今日使用最多的App】\n");
                    for (int i = 0; i < Math.min(topApps.length(), 8); i++) {
                        JSONObject app = topApps.optJSONObject(i);
                        if (app != null) {
                            sb.append("  ").append(app.optString("package_name", ""))
                              .append("(").append(app.optString("category", "")).append(")")
                              .append(" ").append(app.optString("usage_readable", "")).append("\n");
                        }
                    }
                }
            }

            // 3. 前台App变化轨迹（反映用户这段时间在不同 App 间切换的过程）
            JSONArray timeline = data.optJSONArray("foreground_app_timeline");
            if (timeline != null && timeline.length() > 0) {
                sb.append("【前台App变化轨迹】\n");
                String prevCat = "";
                for (int i = 0; i < timeline.length(); i++) {
                    JSONObject entry = timeline.optJSONObject(i);
                    if (entry == null) continue;
                    String cat = entry.optString("cat", "");
                    if (!cat.equals(prevCat)) {
                        sb.append("  → ").append(cat);
                        String pkg = entry.optString("pkg", "");
                        if (!pkg.isEmpty()) sb.append("(").append(pkg).append(")");
                        sb.append("\n");
                        prevCat = cat;
                    }
                }
            }

            // 4. 身体活动（运动/静止/步行/骑行等）
            JSONObject act = data.optJSONObject("activity_summary");
            if (act != null && act.length() > 0) {
                sb.append("【身体活动检测】\n");
                java.util.Iterator<String> actKeys = act.keys();
                while (actKeys.hasNext()) {
                    String type = actKeys.next();
                    int count = act.optInt(type, 0);
                    sb.append("  ").append(type).append(": 检测到").append(count).append("次\n");
                }
            }

            // 5. Wi-Fi 环境（判断用户在家/办公室/外出）
            JSONObject wifiSummary = data.optJSONObject("wifi_ssid_summary");
            if (wifiSummary != null && wifiSummary.length() > 0) {
                sb.append("【Wi-Fi环境】\n");
                java.util.Iterator<String> wifiKeys = wifiSummary.keys();
                while (wifiKeys.hasNext()) {
                    String ssid = wifiKeys.next();
                    sb.append("  ").append(ssid).append("\n");
                }
            }

            // 6. 位置信息（去过什么地方、移动距离）
            JSONObject loc = data.optJSONObject("location_summary");
            if (loc != null) {
                JSONArray places = loc.optJSONArray("visited_places");
                JSONObject ctxSum = loc.optJSONObject("context_summary");
                int pts = loc.optInt("unique_points", 0);
                boolean stationary = loc.optBoolean("is_stationary", false);

                if (places != null && places.length() > 0) {
                    sb.append("【到过的地方】\n");
                    for (int i = 0; i < Math.min(places.length(), 6); i++) {
                        sb.append("  ").append(places.optString(i)).append("\n");
                    }
                } else if (pts > 0) {
                    sb.append("【位置变化】");
                    if (pts == 1) {
                        sb.append("一直在同一个地方，没有移动");
                    } else {
                        sb.append("去过").append(pts).append("个不同的地方");
                    }
                    sb.append("\n");
                }

                // 移动与停留摘要
                if (stationary) {
                    long stayMin = loc.optLong("primary_stay_minutes", 0);
                    if (stayMin > 0) {
                        sb.append("【移动状态】基本没有移动，已在原地停留约")
                          .append(stayMin).append("分钟\n");
                    } else {
                        sb.append("【移动状态】基本没有移动\n");
                    }
                } else {
                    double distKm = loc.optDouble("total_distance_km", 0);
                    long distM = loc.optLong("total_distance_meters", 0);
                    if (distKm > 0) {
                        sb.append("【移动距离】约").append(distKm).append("公里");
                    } else if (distM > 0) {
                        sb.append("【移动距离】约").append(distM).append("米");
                    }
                    // 补充在各地的停留时间
                    JSONArray clusters = loc.optJSONArray("location_clusters");
                    if (clusters != null && clusters.length() > 1) {
                        sb.append("，途经").append(clusters.length()).append("个地点");
                    }
                    sb.append("\n");
                }

                if (ctxSum != null && ctxSum.length() > 0) {
                    sb.append("【所处环境】");
                    java.util.Iterator<String> ctxKeys = ctxSum.keys();
                    while (ctxKeys.hasNext()) {
                        String ctx = ctxKeys.next();
                        sb.append(ctx);
                        if (ctxKeys.hasNext()) sb.append("、");
                    }
                    sb.append("\n");
                }
            }

            // 7. 日历事件（用户可能在忙什么）
            JSONArray cal = data.optJSONArray("calendar_events");
            if (cal != null && cal.length() > 0) {
                sb.append("【日历事件】\n");
                for (int i = 0; i < Math.min(cal.length(), 5); i++) {
                    JSONObject ev = cal.optJSONObject(i);
                    if (ev != null) {
                        sb.append("  ").append(ev.optString("title", "无标题"));
                        String loc2 = ev.optString("location", "");
                        if (!loc2.isEmpty()) sb.append("@").append(loc2);
                        sb.append("\n");
                    }
                }
            }

            // 8. 天气状况
            JSONObject weather = data.optJSONObject("latest_weather");
            if (weather != null) {
                sb.append("【天气状况】\n");
                sb.append("  ").append(weather.optString("readable_summary", "未知")).append("\n");
                int windSpeed = weather.optInt("wind_speed_kmph", 0);
                if (windSpeed > 0) {
                    sb.append("  风速: ").append(windSpeed).append("km/h")
                      .append(" 方向: ").append(weather.optString("wind_dir", "")).append("\n");
                }
                int uvIndex = weather.optInt("uv_index", 0);
                if (uvIndex > 0) {
                    sb.append("  紫外线指数: ").append(uvIndex).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("(数据解析异常)");
        }
        return sb.toString();
    }

    // ── 悬浮窗引擎：生成气泡短文本 ─────────────────────────────

    /**
     * 生成悬浮窗气泡提醒文本（同步调用，需在后台线程执行）。
     * 只走 LLM 路径，失败时返回错误原因字符串（不会返回 null）。
     *
     * @param contextSnapshot 各采集器最新一次采集的完整数据快照
     * @param llmScore        当前 LLM 驱动的使用状态评分 (0-100)
     */
    public String generateBubbleText(JSONObject contextSnapshot, int llmScore) {
        if (!ApiConfig.isDeepSeekApiKeyConfigured()) {
            String err = "[API Key not set] check local.properties";
            Log.e(TAG, "generateBubbleText: " + err);
            return err;
        }

        CollectionConfig cfg = CollectionConfig.getInstance(context);
        String userGoal = cfg.getString(CollectionConfig.KEY_USER_PERSONAL_GOAL, "");

        StringBuilder sb = new StringBuilder();
        sb.append("你是一个手机使用反馈助手，语气温和、像朋友一样关心用户。\n");
        sb.append("根据用户当前的手机使用情况，生成一段简短的中文提醒。\n\n");
        sb.append("你会收到用户手机的实时采集数据（JSON），包含屏幕使用、位置、活动状态、日历、天气、WiFi、蓝牙等信息。\n");
        sb.append("请综合这些信息来理解用户当前的场景和状态。\n\n");
        sb.append("提醒内容分为两部分：\n");
        sb.append("1. 使用小结：用一两句话概括用户当前的状态（在用什么、用了多久、在哪里、在做什么等）\n");
        sb.append("2. 建议：结合用户的完整使用情况");
        if (userGoal != null && !userGoal.trim().isEmpty()) {
            sb.append("和用户设定的个人目标");
        }
        sb.append("，给出一条友善、有针对性的建议\n\n");
        sb.append("格式要求：\n");
        sb.append("- 总字数控制在 30～60 字之间\n");
        sb.append("- 两部分之间用换行分隔\n");
        sb.append("- 不要加标题、编号或引号\n");
        sb.append("- 语气亲切自然，不要说教\n");
        sb.append("- 每次回复要有变化，不要重复");
        if (userGoal != null && !userGoal.trim().isEmpty()) {
            sb.append("\n\n用户设定的个人目标：\n").append(userGoal.trim());
            sb.append("\n（请在建议部分适当结合此目标，但不要每次都生硬提及，自然融入即可）");
        }
        String systemPrompt = sb.toString();

        // Build user content: sanitized context JSON + LLM score
        JSONObject sanitizedSnapshot = DataSanitizer.sanitizeSnapshot(contextSnapshot);
        StringBuilder userContent = new StringBuilder();
        userContent.append("以下是用户手机的实时采集数据：\n");
        userContent.append(sanitizedSnapshot.toString()).append("\n\n");
        userContent.append("当前使用状态评分：").append(llmScore).append("/100\n");
        userContent.append("（评分由 AI 根据使用行为持续评估，分数越高表示越可能处于无意识/过度使用状态）\n");
        userContent.append("请生成提醒。(t=")
                .append(System.currentTimeMillis()).append(")");

        String currentApp = contextSnapshot.optString("foreground_app_package", "unknown");
        Log.i(TAG, "generateBubbleText: calling LLM, app=" + currentApp
                + " score=" + llmScore + " keys=" + contextSnapshot.length());

        try {
            String response = callChatSync(systemPrompt, userContent.toString(), 120, 0.95f);

            if (response == null || response.isEmpty()) {
                return "[LLM returned empty] see Logcat DeepSeekApiClient";
            }

            Log.i(TAG, "generateBubbleText: raw LLM response: " + response);
            // Only remove wrapping quotes (and surrounding whitespace). Keep sentence-ending punctuation.
            response = response.trim()
                    .replaceAll("^[\\s\"'\u201c\u201d]+", "")
                    .replaceAll("[\\s\"'\u201c\u201d]+$", "");
            if (response.length() > 80) response = response.substring(0, 80);
            return response;

        } catch (Exception e) {
            Log.e(TAG, "generateBubbleText exception", e);
            return "[Error] " + e.getClass().getSimpleName();
        }
    }

    // ── LLM 使用评分 ──────────────────────────────────────────

    /**
     * 请求 LLM 对用户当前使用状态进行增量评分（同步调用，需在后台线程执行）。
     *
     * @param currentScore   当前分数 (0-100)
     * @param lastSnapshot   上一次采集快照 JSON 字符串（首次可为 null）
     * @param lastReason     上一次评估理由（首次可为 null）
     * @param newSnapshot    本次最新采集快照
     * @return LLM 返回的 JSON 字符串，格式 {"delta": N, "reason": "..."}，失败返回 null
     */
    public String assessUsageScore(int currentScore, String lastSnapshot,
                                   String lastReason, JSONObject newSnapshot) {
        if (!ApiConfig.isDeepSeekApiKeyConfigured()) {
            Log.e(TAG, "assessUsageScore: API key not configured");
            return null;
        }

        String systemPrompt =
                "你是一个手机使用行为评估引擎。你的任务是根据用户手机的实时采集数据，评估用户当前的「无意识使用程度」并给出分数增量。\n\n"
                + "## 评分规则\n"
                + "分数范围 0-100。0 = 完全有意识/健康使用，100 = 极度无意识/沉迷使用。\n"
                + "你每次返回一个 delta（增量），而非绝对分数。delta 范围 [-5, +5]。\n"
                + "本系统每约 2 分钟调用你一次。\n\n"
                + "## 默认行为\n"
                + "默认情况下 delta = +1（即用户正常使用手机，分数缓慢上升）。\n"
                + "只有当你判断情况明显偏离「普通使用」时，才应给出不同的 delta。\n"
                + "如果 delta ≠ +1，你必须在 reason 中说明为什么偏离默认值。\n"
                + "如果 delta = +1（默认），reason 可以为空字符串。\n\n"
                + "## delta 判定标准\n"
                + "- 普通使用（无明显好坏信号）→ delta = +1（默认，无需解释）\n"
                + "- 生产力/工具类 App（办公、学习、编程、阅读、地图、银行等）→ delta = 0（reason: 说明在做什么）\n"
                + "- 屏幕关闭 / 用户主动休息 / 刚解锁还没开始用 → delta = -1 到 -3（reason: 说明休息情况）\n"
                + "- 长时间未使用手机后恢复 → delta = -5（reason: 说明离开了多久）\n"
                + "- 娱乐/社交 App 持续使用（短视频、社交媒体、游戏等）→ delta = +2（reason: 说明在用什么）\n"
                + "- 深夜（22:00-06:00）使用娱乐 App → delta = +3 到 +4（reason: 说明深夜使用情况）\n"
                + "- 多个无意识信号叠加（深夜 + 长时间娱乐 + 高频切换 + 忽略日程）→ delta 最高 +5（reason: 说明叠加了哪些信号）\n"
                + "- 用户正在做与日历日程相关的事 → delta = 0 或 -1（reason: 说明与日程的关联）\n"
                + "- 用户在通勤/移动中短暂使用 → delta = +1（默认）\n\n"
                + "## 综合考量因素\n"
                + "你会收到完整的手机采集数据，包括：屏幕使用（当前 App、使用时长、今日总时长）、"
                + "位置、活动状态（静止/步行/驾车）、日历日程、天气、WiFi、蓝牙设备等。\n"
                + "请综合所有信息判断用户的使用意图和场景，不要只看单一指标。\n\n"
                + "## 输出格式\n"
                + "严格返回 JSON，不要包含任何其他文字：\n"
                + "{\"delta\": <整数, -5到+5>, \"reason\": \"<delta≠+1时给出一句话中文理由, 20字以内; delta=+1时可为空>\"}\n";

        StringBuilder userContent = new StringBuilder();
        userContent.append("当前分数：").append(currentScore).append("/100\n\n");

        if (lastSnapshot != null && !lastSnapshot.isEmpty()) {
            // 脱敏上一次快照
            try {
                JSONObject lastObj = new JSONObject(lastSnapshot);
                JSONObject sanitizedLast = DataSanitizer.sanitizeSnapshot(lastObj);
                userContent.append("上一次采集数据：\n").append(sanitizedLast.toString()).append("\n\n");
            } catch (JSONException e) {
                userContent.append("上一次采集数据：\n").append(lastSnapshot).append("\n\n");
            }
        }
        if (lastReason != null && !lastReason.isEmpty()) {
            userContent.append("上一次评估理由：").append(lastReason).append("\n\n");
        }

        // 脱敏本次快照
        JSONObject sanitizedNew = DataSanitizer.sanitizeSnapshot(newSnapshot);
        userContent.append("本次最新采集数据：\n").append(sanitizedNew.toString()).append("\n\n");
        userContent.append("请评估并返回 JSON。");

        Log.i(TAG, "assessUsageScore: calling LLM, currentScore=" + currentScore);

        String response = callChatSync(systemPrompt, userContent.toString(), 80, 0.3f);

        if (response == null || response.isEmpty()) {
            Log.w(TAG, "assessUsageScore: LLM returned empty");
            return null;
        }

        // Strip markdown code fences if present
        response = response.trim();
        if (response.startsWith("```")) {
            response = response.replaceAll("^```[a-z]*\\s*", "").replaceAll("\\s*```$", "").trim();
        }

        Log.i(TAG, "assessUsageScore: raw response: " + response);
        return response;
    }

    // ── 通用同步聊天接口 ─────────────────────────────────────

    /**
     * 同步 DeepSeek Chat 调用（阻塞，需在后台线程执行）。
     */
    public String callChatSync(String systemPrompt, String userContent,
                               int maxTokens, float temperature) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", "deepseek-chat");

            JSONArray messages = new JSONArray();

            if (systemPrompt != null) {
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.put(sysMsg);
            }

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);
            messages.put(userMsg);

            requestJson.put("messages", messages);
            requestJson.put("max_tokens", maxTokens);
            requestJson.put("temperature", temperature);
            requestJson.put("stream", false);

            RequestBody body = RequestBody.create(
                    requestJson.toString(), MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(ApiConfig.DEEPSEEK_API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + ApiConfig.DEEPSEEK_API_KEY)
                    .build();

            Log.d(TAG, "callChatSync: sending request to " + ApiConfig.DEEPSEEK_API_URL);
            try (Response response = httpClient.newCall(request).execute()) {
                okhttp3.ResponseBody rb = response.body();
                String respBody = rb != null ? rb.string() : "";
                Log.d(TAG, "callChatSync: HTTP " + response.code()
                        + " body_len=" + respBody.length());

                if (!response.isSuccessful()) {
                    stats.recordApiCall(false);
                    Log.w(TAG, "DeepSeek call failed: HTTP " + response.code()
                            + " body=" + respBody.substring(0, Math.min(200, respBody.length())));
                    return null;
                }

                JSONObject respJson = new JSONObject(respBody);
                JSONArray choices = respJson.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    String text = choices.getJSONObject(0)
                            .getJSONObject("message").getString("content").trim();
                    stats.recordApiCall(true);
                    Log.d(TAG, "callChatSync: success, response=" + text);
                    return text;
                } else {
                    Log.w(TAG, "callChatSync: no choices in response");
                }
            }

        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "callChatSync: network timeout", e);
            stats.recordApiCall(false);
        } catch (java.io.IOException e) {
            Log.e(TAG, "callChatSync: network error", e);
            stats.recordApiCall(false);
        } catch (Exception e) {
            Log.e(TAG, "callChatSync: unexpected error", e);
            stats.recordApiCall(false);
        }
        return null;
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
    }
}
