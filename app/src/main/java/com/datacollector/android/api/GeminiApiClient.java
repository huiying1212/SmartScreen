package com.datacollector.android.api;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.managers.InstalledAppsManager;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Gemini API客户端
 * 升级功能：API调用自动重试 + 采集统计
 */
public class GeminiApiClient {

    private static final String TAG = "GeminiApiClient";
    private static final String API_URL = ApiConfig.GEMINI_API_URL;
    private static final String API_KEY = ApiConfig.GEMINI_API_KEY;

    private final OkHttpClient httpClient;
    private final Context context;
    private final CollectionStats stats;

    public interface GeminiApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public GeminiApiClient(Context context) {
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

    public void callGeminiWithLatestData(GeminiApiCallback callback) {
        new Thread(() -> {
            try {
                if (!ApiConfig.isGeminiApiKeyConfigured()) {
                    callback.onError("请先在local.properties中配置GEMINI_API_KEY。");
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

                String combinedContent = promptContent +
                        "\n\n设备已安装应用信息:\n" + installedAppsJson +
                        "\n\n用户数据:\n" + latestDataContent +
                        "\n\n重要提示：在推荐应用时，请只从上述已安装应用列表中选择。";

                callGeminiApi(combinedContent, callback);

            } catch (Exception e) {
                Log.e(TAG, "Error calling Gemini API", e);
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

    private void callGeminiApi(String content, GeminiApiCallback callback) {
        try {
            JSONObject requestJson = new JSONObject();
            JSONArray contentsArray = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray partsArray = new JSONArray();
            JSONObject partObj = new JSONObject();

            partObj.put("text", content);
            partsArray.put(partObj);
            contentObj.put("parts", partsArray);
            contentsArray.put(contentObj);
            requestJson.put("contents", contentsArray);

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 2048);
            requestJson.put("generationConfig", generationConfig);

            RequestBody requestBody = RequestBody.create(
                    requestJson.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(API_URL + "?key=" + API_KEY)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
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
                            JSONArray candidates = responseJson.getJSONArray("candidates");

                            if (candidates.length() > 0) {
                                JSONObject candidate = candidates.getJSONObject(0);
                                JSONArray parts = candidate.getJSONObject("content")
                                        .getJSONArray("parts");
                                if (parts.length() > 0) {
                                    String text = parts.getJSONObject(0).getString("text");
                                    stats.recordApiCall(true);
                                    callback.onSuccess(text);
                                } else {
                                    stats.recordApiCall(false);
                                    callback.onError("API响应中没有找到文本内容");
                                }
                            } else {
                                stats.recordApiCall(false);
                                callback.onError("API响应中没有找到候选结果");
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
}
