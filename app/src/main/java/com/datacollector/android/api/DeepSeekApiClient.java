package com.datacollector.android.api;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.CollectionStats;
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
 * 2. 悬浮窗气泡文本生成（≤15 字短提醒）
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

            String userContent = "用户手机使用数据摘要：\n" + summarizeForKeywords(aggregatedData);

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
     */
    public String generateBubbleText(String currentApp, int usageMins,
                                     int uutValue, String calendarInfo,
                                     String weatherInfo) {
        if (!ApiConfig.isDeepSeekApiKeyConfigured()) {
            String err = "[API Key not set] check local.properties";
            Log.e(TAG, "generateBubbleText: " + err);
            return err;
        }

        CollectionConfig cfg = CollectionConfig.getInstance(context);
        String userGoal = cfg.getString(CollectionConfig.KEY_USER_PERSONAL_GOAL, "");

        StringBuilder sb = new StringBuilder();
        sb.append("You are a phone-use feedback assistant. ");
        sb.append("Based on the user's current phone usage, generate a short reminder.\n\n");
        sb.append("Rules:\n");
        sb.append("1. No more than 15 Chinese characters\n");
        sb.append("2. Friendly but guiding tone\n");
        sb.append("3. Return ONLY the reminder text, no explanation\n");
        sb.append("4. Do NOT wrap in quotes\n");
        sb.append("5. Each response must be different");
        if (userGoal != null && !userGoal.trim().isEmpty()) {
            sb.append("\n\nUser's personal goals:\n").append(userGoal.trim());
        }
        String systemPrompt = sb.toString();

        StringBuilder userContent = new StringBuilder();
        userContent.append("User is on [").append(currentApp).append("], ");
        userContent.append("spent [").append(usageMins).append(" min], ");
        userContent.append("unconscious-usage-index: ").append(uutValue).append("/100");
        if (calendarInfo != null && !calendarInfo.isEmpty()) {
            userContent.append(", calendar: [").append(calendarInfo).append("]");
        }
        if (weatherInfo != null && !weatherInfo.isEmpty()) {
            userContent.append(", weather: [").append(weatherInfo).append("]");
        }
        userContent.append(". Generate a short Chinese reminder. (t=")
                .append(System.currentTimeMillis()).append(")");

        Log.i(TAG, "generateBubbleText: calling LLM, app=" + currentApp
                + " mins=" + usageMins + " uut=" + uutValue);

        try {
            String response = callChatSync(systemPrompt, userContent.toString(), 48, 0.95f);

            if (response == null || response.isEmpty()) {
                return "[LLM returned empty] see Logcat DeepSeekApiClient";
            }

            Log.i(TAG, "generateBubbleText: raw LLM response: " + response);
            response = response.trim()
                    .replaceAll("^[\"'\u201c\u201d]+", "")
                    .replaceAll("[\"'\u201c\u201d\u3002\uff01!.]+$", "");
            if (response.length() > 20) response = response.substring(0, 20);
            return response;

        } catch (Exception e) {
            Log.e(TAG, "generateBubbleText exception", e);
            return "[Error] " + e.getClass().getSimpleName();
        }
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
                String respBody = response.body() != null ? response.body().string() : "";
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
