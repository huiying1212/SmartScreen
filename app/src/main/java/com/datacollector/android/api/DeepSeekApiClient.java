package com.datacollector.android.api;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.CollectionStats;
import com.datacollector.android.utils.DataAggregator;
import com.datacollector.android.utils.RetryHelper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
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
    private final DataAggregator aggregator;

    public interface SimpleCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public DeepSeekApiClient(Context context) {
        this.context = context;
        this.stats = CollectionStats.getInstance(context);
        this.aggregator = new DataAggregator(context);

        CollectionConfig config = CollectionConfig.getInstance(context);
        int connectTimeout = config.getInt(CollectionConfig.KEY_API_CONNECT_TIMEOUT, 30);
        int readTimeout = config.getInt(CollectionConfig.KEY_API_READ_TIMEOUT, 60);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // ── 壁纸引擎：提取 3 个核心元素词 ────────────────────────────

    /**
     * 根据聚合数据和用户权重，让 LLM 提取 3 个核心元素词（同步调用，需在后台线程执行）。
     *
     * @return 3 个元素词组成的字符串（如 "专注、疲惫、代码"），失败返回 null
     */
    public String extractKeywords(JSONObject aggregatedData, String weightDescription) {
        if (!ApiConfig.isDeepSeekApiKeyConfigured()) return null;

        try {
            String systemPrompt = "你是一位抽象艺术概念提炼师。根据用户的手机使用数据和偏好权重，"
                    + "总结出最能代表用户当前状态的 3 个核心元素词。\n\n"
                    + "规则：\n"
                    + "1. 严格返回且只返回 3 个中文词，用中文顿号分隔\n"
                    + "2. 词语应具有隐喻性和画面感，适合作为文生图的关键词\n"
                    + "3. 结合数据事实和用户偏好权重来确定词语\n"
                    + "4. 不要输出任何解释，只输出 3 个词\n\n"
                    + "偏好权重说明：\n" + weightDescription;

            String userContent = "用户手机使用数据摘要：\n" + summarizeForKeywords(aggregatedData);

            String response = callChatSync(systemPrompt, userContent, 64, 0.8f);
            if (response != null) {
                response = response.trim().replaceAll("[\"'\\s]+$", "").replaceAll("^[\"'\\s]+", "");
                if (response.length() > 50) response = response.substring(0, 50);
            }
            return response;

        } catch (Exception e) {
            Log.e(TAG, "extractKeywords failed", e);
            return null;
        }
    }

    private String summarizeForKeywords(JSONObject data) {
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject su = data.optJSONObject("screen_usage");
            if (su != null) {
                sb.append("屏幕使用：").append(su.optString("today_screen_time_readable", "未知"));
                String fgPkg = su.optString("foreground_app_package", "");
                String fgCat = su.optString("foreground_app_category", "");
                if (!fgPkg.isEmpty()) sb.append("，当前: ").append(fgPkg);
                if (!fgCat.isEmpty()) sb.append("(").append(fgCat).append(")");
                sb.append("\n");
            }

            JSONObject act = data.optJSONObject("activity_summary");
            if (act != null && act.length() > 0) {
                sb.append("活动: ").append(act.toString()).append("\n");
            }

            JSONObject loc = data.optJSONObject("location_summary");
            if (loc != null) {
                sb.append("位置点数: ").append(loc.optInt("unique_points", 0)).append("\n");
            }

            JSONArray cal = data.optJSONArray("calendar_events");
            if (cal != null && cal.length() > 0) {
                sb.append("日历事件数: ").append(cal.length()).append("\n");
            }
        } catch (Exception e) {
            sb.append("(数据解析异常)");
        }
        return sb.toString();
    }

    // ── 悬浮窗引擎：生成气泡短文本 ─────────────────────────────

    /**
     * 生成悬浮窗气泡提醒文本（同步调用，需在后台线程执行）。
     *
     * @param currentApp   当前 App 包名
     * @param usageMins    当次使用时长（分钟）
     * @param uutValue     当前 UUT 值
     * @param calendarInfo 用户日程简述（可为 null）
     * @return ≤15 字的短提醒文本，失败返回 null
     */
    /**
     * 生成悬浮窗气泡提醒文本（同步调用，需在后台线程执行）。
     * 只走 LLM 路径，失败时返回错误原因字符串（不会返回 null）。
     */
    public String generateBubbleText(String currentApp, int usageMins,
                                     int uutValue, String calendarInfo) {
        if (!ApiConfig.isDeepSeekApiKeyConfigured()) {
            String err = "[API Key not set] check local.properties";
            Log.e(TAG, "generateBubbleText: " + err);
            return err;
        }

        String systemPrompt = "You are a phone-use feedback assistant. "
                + "Based on the user's current phone usage, generate a short reminder.\n\n"
                + "Rules:\n"
                + "1. No more than 15 Chinese characters\n"
                + "2. Friendly but guiding tone\n"
                + "3. Return ONLY the reminder text, no explanation\n"
                + "4. Do NOT wrap in quotes\n"
                + "5. Each response must be different";

        StringBuilder userContent = new StringBuilder();
        userContent.append("User is on [").append(currentApp).append("], ");
        userContent.append("spent [").append(usageMins).append(" min], ");
        userContent.append("unconscious-usage-index: ").append(uutValue).append("/100");
        if (calendarInfo != null && !calendarInfo.isEmpty()) {
            userContent.append(", calendar: [").append(calendarInfo).append("]");
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

    // ── 异步通用接口 ─────────────────────────────────────────

    public void callChatAsync(String systemPrompt, String userContent,
                              int maxTokens, float temperature, SimpleCallback callback) {
        new Thread(() -> {
            String result = callChatSync(systemPrompt, userContent, maxTokens, temperature);
            if (result != null) {
                callback.onSuccess(result);
            } else {
                callback.onError("API 调用失败");
            }
        }).start();
    }

    /**
     * 分析上下文数据并保存结果（用于定期自动分析）。
     */
    public void analyzeContextData(JSONObject contextData) {
        new Thread(() -> {
            try {
                if (!ApiConfig.isDeepSeekApiKeyConfigured()) return;

                CollectionConfig config = CollectionConfig.getInstance(context);
                int windowHours = config.getInt(CollectionConfig.KEY_AGGREGATION_WINDOW_HOURS, 6);
                JSONObject aggregatedData = aggregator.aggregateRecentData(windowHours);

                String systemPrompt = "你是一个数字健康分析师。分析用户的手机使用数据，"
                        + "给出一个简短的健康状态总结和建议。返回 JSON 格式：\n"
                        + "{\"summary\": \"...\", \"suggestion\": \"...\"}";

                String userContent = "用户数据:\n" + contextData.toString(2)
                        + "\n\n历史摘要:\n" + aggregatedData.toString(2);

                String response = callChatSync(systemPrompt, userContent, 512, 0.7f);
                if (response != null) {
                    saveAnalysisResult(response, contextData);
                }
            } catch (Exception e) {
                Log.e(TAG, "analyzeContextData error", e);
            }
        }).start();
    }

    private void saveAnalysisResult(String analysisResult, JSONObject originalData) {
        try {
            JSONObject resultData = new JSONObject();
            resultData.put("analysis_result", analysisResult);
            resultData.put("analysis_time", System.currentTimeMillis());
            resultData.put("original_data_timestamp", originalData.optLong("collection_time"));

            File analysisDir = new File(context.getExternalFilesDir(null), "analysis");
            if (!analysisDir.exists()) analysisDir.mkdirs();

            String fileName = "analysis_result_" + System.currentTimeMillis() + ".json";
            File analysisFile = new File(analysisDir, fileName);
            try (FileWriter fw = new FileWriter(analysisFile)) {
                fw.write(resultData.toString(4));
            }

            stats.recordFileSaved(analysisFile.length());
        } catch (Exception e) {
            Log.e(TAG, "保存分析结果时出错", e);
        }
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
    }
}
