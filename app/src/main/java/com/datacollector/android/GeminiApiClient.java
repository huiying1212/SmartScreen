package com.datacollector.android;

import android.content.Context;
import android.util.Log;

import okhttp3.Call;
import okhttp3.Callback;
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
 * 负责调用Google Gemini API进行AI推理
 */
public class GeminiApiClient {
    
    private static final String TAG = "GeminiApiClient";
    private static final String API_URL = ApiConfig.GEMINI_API_URL;
    private static final String API_KEY = ApiConfig.GEMINI_API_KEY;
    
    private final OkHttpClient httpClient;
    private final Context context;
    
    public interface GeminiApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    public GeminiApiClient(Context context) {
        this.context = context;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 调用Gemini API，使用最新收集的数据文件和prompt.txt
     */
    public void callGeminiWithLatestData(GeminiApiCallback callback) {
        new Thread(() -> {
            try {
                // 检查API密钥是否已配置
                if (!ApiConfig.isGeminiApiKeyConfigured()) {
                    callback.onError("请先配置Gemini API密钥。\n请在ApiConfig.java文件中设置GEMINI_API_KEY。");
                    return;
                }
                
                // 获取最新的数据文件
                String latestDataContent = getLatestDataFileContent();
                if (latestDataContent == null) {
                    callback.onError("未找到数据文件");
                    return;
                }
                
                // 读取prompt.txt文件
                String promptContent = readPromptFile();
                if (promptContent == null) {
                    callback.onError("无法读取prompt.txt文件");
                    return;
                }
                
                // 获取已安装应用列表
                String installedAppsJson = InstalledAppsManager.getInstance(context).getInstalledAppsListJson();
                
                // 构建请求内容，包含应用列表
                String combinedContent = promptContent + 
                    "\n\n设备已安装应用信息:\n" + installedAppsJson +
                    "\n\n用户数据:\n" + latestDataContent +
                    "\n\n重要提示：在推荐应用时，请只从上述已安装应用列表中选择，不要推荐设备上不存在的应用。";
                
                // 调用API
                callGeminiApi(combinedContent, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "Error calling Gemini API", e);
                callback.onError("调用API时出错: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 获取最新数据文件的内容
     */
    private String getLatestDataFileContent() {
        try {
            File dataDir = context.getExternalFilesDir(null);
            if (dataDir == null || !dataDir.exists()) {
                Log.e(TAG, "数据目录不存在");
                return null;
            }
            
            File[] files = dataDir.listFiles((dir, name) -> 
                name.startsWith("context_data_") && name.endsWith(".json"));
            
            if (files == null || files.length == 0) {
                Log.e(TAG, "未找到数据文件");
                return null;
            }
            
            // 找到最新的文件（按文件名中的时间戳排序）
            File latestFile = null;
            long latestTimestamp = 0;
            
            for (File file : files) {
                try {
                    String fileName = file.getName();
                    String timestampStr = fileName.substring("context_data_".length(), fileName.length() - ".json".length());
                    long timestamp = Long.parseLong(timestampStr);
                    
                    if (timestamp > latestTimestamp) {
                        latestTimestamp = timestamp;
                        latestFile = file;
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "无法解析文件时间戳: " + file.getName());
                }
            }
            
            if (latestFile == null) {
                Log.e(TAG, "未找到有效的数据文件");
                return null;
            }
            
            Log.i(TAG, "使用最新数据文件: " + latestFile.getName());
            
            // 读取文件内容
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
    
    /**
     * 读取prompt.txt文件内容
     */
    private String readPromptFile() {
        try {
            // 首先尝试从assets目录读取
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(context.getAssets().open("prompt.txt")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString();
            } catch (IOException e) {
                Log.w(TAG, "无法从assets读取prompt.txt，尝试从外部存储读取");
            }
            
            // 如果assets中没有，尝试从项目根目录或外部存储读取
            File[] possibleLocations = {
                new File(context.getExternalFilesDir(null), "prompt.txt"),
                new File(context.getFilesDir(), "prompt.txt"),
                // 尝试从应用的上级目录查找
                new File("/sdcard/prompt.txt"),
                new File("/storage/emulated/0/prompt.txt")
            };
            
            for (File promptFile : possibleLocations) {
                if (promptFile.exists() && promptFile.canRead()) {
                    Log.i(TAG, "找到prompt.txt文件: " + promptFile.getAbsolutePath());
                    try (BufferedReader reader = new BufferedReader(new FileReader(promptFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line).append("\n");
                        }
                        return content.toString();
                    }
                }
            }
            
            // 如果都找不到，返回默认的prompt内容
            Log.w(TAG, "未找到prompt.txt文件，使用默认prompt");
            return getDefaultPrompt();
            
        } catch (IOException e) {
            Log.e(TAG, "读取prompt.txt文件时出错", e);
            return getDefaultPrompt();
        }
    }
    
    /**
     * 获取默认的prompt内容
     */
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
     * 实际调用Gemini API
     */
    private void callGeminiApi(String content, GeminiApiCallback callback) {
        try {
            // 构建请求JSON
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
            
            // 添加生成配置
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 2048);
            requestJson.put("generationConfig", generationConfig);
            
            // 创建请求
            RequestBody requestBody = RequestBody.create(
                requestJson.toString(),
                MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                    .url(API_URL + "?key=" + API_KEY)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            // 发送请求
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "API请求失败", e);
                    callback.onError("网络请求失败: " + e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    
                    if (response.isSuccessful()) {
                        try {
                            // 解析响应
                            JSONObject responseJson = new JSONObject(responseBody);
                            JSONArray candidates = responseJson.getJSONArray("candidates");
                            
                            if (candidates.length() > 0) {
                                JSONObject candidate = candidates.getJSONObject(0);
                                JSONObject content = candidate.getJSONObject("content");
                                JSONArray parts = content.getJSONArray("parts");
                                
                                if (parts.length() > 0) {
                                    String text = parts.getJSONObject(0).getString("text");
                                    callback.onSuccess(text);
                                } else {
                                    callback.onError("API响应中没有找到文本内容");
                                }
                            } else {
                                callback.onError("API响应中没有找到候选结果");
                            }
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "解析API响应时出错", e);
                            callback.onError("解析响应失败: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "API响应错误: " + response.code() + " " + responseBody);
                        callback.onError("API调用失败: " + response.code() + " " + responseBody);
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "构建请求JSON时出错", e);
            callback.onError("构建请求失败: " + e.getMessage());
        }
    }
} 