package com.datacollector.android.api;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.managers.InstalledAppsManager;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek API客户端
 * 升级功能：
 * - API调用自动重试（借鉴Beiwe的S3重试机制）
 * - 数据聚合后发送（借鉴Beiwe的time-binning）
 * - 采集统计追踪（借鉴Beiwe的data_quantity_stats）
 */
public class DeepSeekApiClient {

    private static final String TAG = "DeepSeekApiClient";
    private static final String API_URL = ApiConfig.DEEPSEEK_API_URL;
    private static final String API_KEY = ApiConfig.DEEPSEEK_API_KEY;

    private final OkHttpClient httpClient;
    private final Context context;
    private final CollectionStats stats;
    private final DataAggregator aggregator;

    public interface DeepSeekApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public interface LauncherUpdateCallback {
        void onAnalysisComplete(String notificationText);
        void onAnalysisError(String error);
        default void onFullAnalysisComplete(String fullResponse) {}
    }

    private static LauncherUpdateCallback launcherUpdateCallback;

    public static void setLauncherUpdateCallback(LauncherUpdateCallback callback) {
        launcherUpdateCallback = callback;
    }

    public static void clearLauncherUpdateCallback() {
        launcherUpdateCallback = null;
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

    public void callDeepSeekWithLatestData(DeepSeekApiCallback callback) {
        new Thread(() -> {
            try {
                if (!ApiConfig.isDeepSeekApiKeyConfigured()) {
                    callback.onError("请先在local.properties中配置DEEPSEEK_API_KEY。");
                    return;
                }

                String latestDataContent = getLatestDataFileContent();
                if (latestDataContent == null) {
                    callback.onError("未找到数据文件");
                    return;
                }

                String promptContent = readPromptFile();
                if (promptContent == null) {
                    callback.onError("无法读取prompt.txt文件");
                    return;
                }

                String installedAppsJson = InstalledAppsManager.getInstance(context)
                        .getInstalledAppsListJson();

                // 聚合历史数据为摘要，提供更丰富的上下文
                CollectionConfig config = CollectionConfig.getInstance(context);
                int windowHours = config.getInt(CollectionConfig.KEY_AGGREGATION_WINDOW_HOURS, 24);
                JSONObject aggregatedData = aggregator.aggregateRecentData(windowHours);

                String combinedContent = promptContent +
                        "\n\n设备已安装应用信息:\n" + installedAppsJson +
                        "\n\n用户数据(最新):\n" + latestDataContent +
                        "\n\n历史数据摘要(过去" + windowHours + "小时):\n" + aggregatedData.toString(2) +
                        "\n\n重要提示：在推荐应用时，请只从上述已安装应用列表中选择。";

                callDeepSeekApi(combinedContent, callback);

            } catch (Exception e) {
                Log.e(TAG, "Error calling DeepSeek API", e);
                callback.onError("调用API时出错: " + e.getMessage());
            }
        }).start();
    }

    private String getLatestDataFileContent() {
        try {
            File rootDataDir = context.getExternalFilesDir(null);
            if (rootDataDir == null || !rootDataDir.exists()) return null;

            File dataSubDir = new File(rootDataDir, "data");
            File[] files = null;

            if (dataSubDir.exists()) {
                files = dataSubDir.listFiles((dir, name) ->
                        name.startsWith("context_data_") && name.endsWith(".json"));
            }

            if (files == null || files.length == 0) {
                files = rootDataDir.listFiles((dir, name) ->
                        name.startsWith("context_data_") && name.endsWith(".json"));
            }

            if (files == null || files.length == 0) return null;

            File latestFile = null;
            long latestTimestamp = 0;

            for (File file : files) {
                try {
                    String fileName = file.getName();
                    String timestampStr = fileName.substring("context_data_".length(),
                            fileName.length() - ".json".length());
                    long timestamp = Long.parseLong(timestampStr);
                    if (timestamp > latestTimestamp) {
                        latestTimestamp = timestamp;
                        latestFile = file;
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "无法解析文件时间戳: " + file.getName());
                }
            }

            if (latestFile == null) return null;

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(latestFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            return content.toString();

        } catch (IOException e) {
            Log.e(TAG, "读取数据文件时出错", e);
            return null;
        }
    }

    private String readPromptFile() {
        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(context.getAssets().open("prompt.txt")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString();
            } catch (IOException e) {
                Log.w(TAG, "无法从assets读取prompt.txt");
            }

            File[] possibleLocations = {
                    new File(context.getExternalFilesDir(null), "prompt.txt"),
                    new File(context.getFilesDir(), "prompt.txt")
            };

            for (File promptFile : possibleLocations) {
                if (promptFile.exists() && promptFile.canRead()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(promptFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line).append("\n");
                        }
                        return content.toString();
                    }
                }
            }

            return getDefaultPrompt();

        } catch (IOException e) {
            Log.e(TAG, "读取prompt.txt文件时出错", e);
            return getDefaultPrompt();
        }
    }

    private String getDefaultPrompt() {
        return "You are an intelligent smartphone Home Screen generator. Your duty is to infer and generate a json format output that indicates the ideal user attention according to the json file you have received, which indicates the current user activity, and also you need to pay attention to self requirements that's what the user want.\n\n" +
                "Self requirements: do not use cell phone so often, better sleep early and be healthy.\n\n" +
                "You should follow this format:\n" +
                "{\n" +
                "  \"next_move\": [\n" +
                "    {\n" +
                "      \"type\": \"widget\",\n" +
                "      \"name\": \"Wind Down Timer\",\n" +
                "      \"action\": \"Start 20-min reading timer\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"suggestions\": [\n" +
                "    \"Read a book for 20 minutes to wind down\"\n" +
                "  ],\n" +
                "  \"notification_text\": \"It's time to go to bed. Sleep early is healthy for you 💤\"\n" +
                "}\n\n" +
                "constraint: do not give any additional words, the required json format only.";
    }

    /**
     * 使用RetryHelper执行带重试的API调用
     */
    private void callDeepSeekApi(String content, DeepSeekApiCallback callback) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", "deepseek-chat");

            JSONArray messagesArray = new JSONArray();
            JSONObject messageObj = new JSONObject();
            messageObj.put("role", "user");
            messageObj.put("content", content);
            messagesArray.put(messageObj);

            requestJson.put("messages", messagesArray);
            requestJson.put("max_tokens", 2048);
            requestJson.put("temperature", 0.7);
            requestJson.put("stream", false);

            RequestBody requestBody = RequestBody.create(
                    requestJson.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .build();

            int maxRetries = CollectionConfig.getInstance(context)
                    .getInt(CollectionConfig.KEY_API_MAX_RETRIES, RetryHelper.DEFAULT_MAX_RETRIES);

            RetryHelper.executeWithRetry(httpClient, request, maxRetries, new RetryHelper.RetryCallback() {
                @Override
                public void onSuccess(Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject responseJson = new JSONObject(responseBody);
                            JSONArray choices = responseJson.getJSONArray("choices");
                            if (choices.length() > 0) {
                                String text = choices.getJSONObject(0)
                                        .getJSONObject("message").getString("content");
                                stats.recordApiCall(true);
                                callback.onSuccess(text);
                            } else {
                                stats.recordApiCall(false);
                                callback.onError("API响应中没有找到选择结果");
                            }
                        } catch (JSONException e) {
                            stats.recordApiCall(false);
                            callback.onError("解析响应失败: " + e.getMessage());
                        }
                    } else {
                        stats.recordApiCall(false);
                        callback.onError("API调用失败: " + response.code() + " " + responseBody);
                    }
                }

                @Override
                public void onFinalFailure(String error) {
                    stats.recordApiCall(false);
                    callback.onError(error);
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "构建请求JSON时出错", e);
            stats.recordApiCall(false);
            callback.onError("构建请求失败: " + e.getMessage());
        }
    }

    public void analyzeContextData(JSONObject contextData) {
        new Thread(() -> {
            try {
                if (!ApiConfig.isDeepSeekApiKeyConfigured()) {
                    Log.w(TAG, "DeepSeek API密钥未配置，跳过自动分析");
                    return;
                }

                String promptContent = readPromptFile();
                if (promptContent == null) {
                    Log.w(TAG, "无法读取prompt.txt文件，跳过自动分析");
                    return;
                }

                String installedAppsJson = InstalledAppsManager.getInstance(context)
                        .getInstalledAppsListJson();

                CollectionConfig config = CollectionConfig.getInstance(context);
                int windowHours = config.getInt(CollectionConfig.KEY_AGGREGATION_WINDOW_HOURS, 24);
                JSONObject aggregatedData = aggregator.aggregateRecentData(windowHours);

                String combinedContent = promptContent +
                        "\n\n设备已安装应用信息:\n" + installedAppsJson +
                        "\n\n用户数据:\n" + contextData.toString(2) +
                        "\n\n历史数据摘要(过去" + windowHours + "小时):\n" + aggregatedData.toString(2) +
                        "\n\n重要提示：在推荐应用时，请只从上述已安装应用列表中选择。";

                callDeepSeekApi(combinedContent, new DeepSeekApiCallback() {
                    @Override
                    public void onSuccess(String response) {
                        Log.i(TAG, "自动LLM分析完成");

                        String notificationText = extractNotificationText(response);
                        if (notificationText != null && launcherUpdateCallback != null) {
                            launcherUpdateCallback.onAnalysisComplete(notificationText);
                        }

                        if (launcherUpdateCallback != null) {
                            launcherUpdateCallback.onFullAnalysisComplete(response);
                        }

                        saveAnalysisResult(response, contextData);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "自动LLM分析失败: " + error);
                        if (launcherUpdateCallback != null) {
                            launcherUpdateCallback.onAnalysisError("分析失败: " + error);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "自动分析时出错", e);
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
            if (!analysisDir.exists()) {
                analysisDir.mkdirs();
            }

            String fileName = "analysis_result_" + System.currentTimeMillis() + ".json";
            File analysisFile = new File(analysisDir, fileName);
            java.io.FileWriter fileWriter = new java.io.FileWriter(analysisFile);
            fileWriter.write(resultData.toString(4));
            fileWriter.close();

            stats.recordFileSaved(analysisFile.length());

        } catch (Exception e) {
            Log.e(TAG, "保存分析结果时出错", e);
        }
    }

    private String extractNotificationText(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.has("notification_text")) {
                return jsonResponse.getString("notification_text");
            }
            if (jsonResponse.has("choices")) {
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    if (choice.has("message")) {
                        String content = choice.getJSONObject("message").getString("content");
                        try {
                            JSONObject contentJson = new JSONObject(content);
                            if (contentJson.has("notification_text")) {
                                return contentJson.getString("notification_text");
                            }
                        } catch (JSONException e) {
                            return extractNotificationFromText(content);
                        }
                    }
                }
            }
            return null;
        } catch (JSONException e) {
            return extractNotificationFromText(response);
        }
    }

    private String extractNotificationFromText(String text) {
        try {
            String pattern = "\"notification_text\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = regex.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return text.length() > 100 ? text.substring(0, 100) + "..." : text;
        } catch (Exception e) {
            return "AI分析完成";
        }
    }

    public void shutdown() {
        Log.d(TAG, "DeepSeekApiClient shutdown");
    }
}
