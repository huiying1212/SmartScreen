package com.datacollector.android.api;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.managers.InstalledAppsManager;

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
 * DeepSeek API客户端
 * 负责调用DeepSeek API进行AI推理
 */
public class DeepSeekApiClient {
    
    private static final String TAG = "DeepSeekApiClient";
    private static final String API_URL = ApiConfig.DEEPSEEK_API_URL;
    private static final String API_KEY = ApiConfig.DEEPSEEK_API_KEY;
    
    private final OkHttpClient httpClient;
    private final Context context;
    
    public interface DeepSeekApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    /**
     * Launcher更新回调接口
     */
    public interface LauncherUpdateCallback {
        void onAnalysisComplete(String notificationText);
        void onAnalysisError(String error);
        
        // 新增：处理完整响应的方法
        default void onFullAnalysisComplete(String fullResponse) {
            // 默认实现，向后兼容
        }
    }
    
    // 静态回调引用，用于launcher更新
    private static LauncherUpdateCallback launcherUpdateCallback;
    
    /**
     * 设置Launcher更新回调
     */
    public static void setLauncherUpdateCallback(LauncherUpdateCallback callback) {
        launcherUpdateCallback = callback;
    }
    
    /**
     * 清除Launcher更新回调
     */
    public static void clearLauncherUpdateCallback() {
        launcherUpdateCallback = null;
    }
    
    public DeepSeekApiClient(Context context) {
        this.context = context;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 调用DeepSeek API，使用最新收集的数据文件和prompt.txt
     */
    public void callDeepSeekWithLatestData(DeepSeekApiCallback callback) {
        new Thread(() -> {
            try {
                // 检查API密钥是否已配置
                if (!ApiConfig.isDeepSeekApiKeyConfigured()) {
                    callback.onError("请先配置DeepSeek API密钥。\n请在ApiConfig.java文件中设置DEEPSEEK_API_KEY。");
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
                callDeepSeekApi(combinedContent, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "Error calling DeepSeek API", e);
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
     * 实际调用DeepSeek API
     */
    private void callDeepSeekApi(String content, DeepSeekApiCallback callback) {
        try {
            // 构建请求JSON（OpenAI兼容格式）
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
            
            // 创建请求
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
                            // 解析响应（OpenAI兼容格式）
                            JSONObject responseJson = new JSONObject(responseBody);
                            JSONArray choices = responseJson.getJSONArray("choices");
                            
                            if (choices.length() > 0) {
                                JSONObject choice = choices.getJSONObject(0);
                                JSONObject message = choice.getJSONObject("message");
                                String text = message.getString("content");
                                callback.onSuccess(text);
                            } else {
                                callback.onError("API响应中没有找到选择结果");
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
    
    /**
     * 分析上下文数据（自动调用）
     * 用于在数据生成时自动进行LLM分析
     */
    public void analyzeContextData(JSONObject contextData) {
        new Thread(() -> {
            try {
                // 检查API密钥是否已配置
                if (!ApiConfig.isDeepSeekApiKeyConfigured()) {
                    Log.w(TAG, "DeepSeek API密钥未配置，跳过自动分析");
                    return;
                }
                
                // 读取prompt.txt文件
                String promptContent = readPromptFile();
                if (promptContent == null) {
                    Log.w(TAG, "无法读取prompt.txt文件，跳过自动分析");
                    return;
                }
                
                // 获取已安装应用列表
                String installedAppsJson = InstalledAppsManager.getInstance(context).getInstalledAppsListJson();
                
                // 构建请求内容，包含应用列表
                String combinedContent = promptContent + 
                    "\n\n设备已安装应用信息:\n" + installedAppsJson +
                    "\n\n用户数据:\n" + contextData.toString(2) +
                    "\n\n重要提示：在推荐应用时，请只从上述已安装应用列表中选择，不要推荐设备上不存在的应用。";
                
                // 调用API进行自动分析
                callDeepSeekApi(combinedContent, new DeepSeekApiCallback() {
                    @Override
                    public void onSuccess(String response) {
                        Log.i(TAG, "自动LLM分析完成");
                        
                        // 解析notification_text并更新launcher
                        String notificationText = extractNotificationText(response);
                        if (notificationText != null && launcherUpdateCallback != null) {
                            Log.d(TAG, "Calling onAnalysisComplete with: " + notificationText);
                            launcherUpdateCallback.onAnalysisComplete(notificationText);
                        }
                        
                        // 新增：调用完整响应处理方法
                        if (launcherUpdateCallback != null) {
                            Log.d(TAG, "Calling onFullAnalysisComplete with response length: " + response.length());
                            launcherUpdateCallback.onFullAnalysisComplete(response);
                        } else {
                            Log.w(TAG, "launcherUpdateCallback is null, cannot update widgets");
                        }
                        
                        // 保存分析结果
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
    
    /**
     * 保存分析结果
     */
    private void saveAnalysisResult(String analysisResult, JSONObject originalData) {
        try {
            // 创建分析结果文件
            JSONObject resultData = new JSONObject();
            resultData.put("analysis_result", analysisResult);
            resultData.put("analysis_time", System.currentTimeMillis());
            resultData.put("original_data_timestamp", originalData.optLong("collection_time"));
            
            // 保存到文件
            String fileName = "analysis_result_" + System.currentTimeMillis() + ".json";
            java.io.FileWriter fileWriter = new java.io.FileWriter(context.getExternalFilesDir(null) + "/" + fileName);
            fileWriter.write(resultData.toString(4));
            fileWriter.close();
            
            Log.d(TAG, "保存分析结果到: " + fileName);
            
        } catch (Exception e) {
            Log.e(TAG, "保存分析结果时出错", e);
        }
    }
    
    /**
     * 从LLM响应中提取notification_text
     */
    private String extractNotificationText(String response) {
        try {
            // 首先尝试直接解析为JSON
            JSONObject jsonResponse = new JSONObject(response);
            
            // 直接查找notification_text字段
            if (jsonResponse.has("notification_text")) {
                return jsonResponse.getString("notification_text");
            }
            
            // 如果在choices数组中（OpenAI格式）
            if (jsonResponse.has("choices")) {
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    if (choice.has("message")) {
                        JSONObject message = choice.getJSONObject("message");
                        String content = message.getString("content");
                        
                        // 尝试解析content中的JSON
                        try {
                            JSONObject contentJson = new JSONObject(content);
                            if (contentJson.has("notification_text")) {
                                return contentJson.getString("notification_text");
                            }
                        } catch (JSONException e) {
                            // content不是JSON，尝试从纯文本中提取
                            return extractNotificationFromText(content);
                        }
                    }
                }
            }
            
            // 如果都没找到，返回null
            return null;
            
        } catch (JSONException e) {
            // 如果整个响应不是JSON，尝试从纯文本中提取
            return extractNotificationFromText(response);
        }
    }
    
    /**
     * 从纯文本响应中提取notification_text
     */
    private String extractNotificationFromText(String text) {
        try {
            // 查找JSON模式的notification_text
            String pattern = "\"notification_text\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = regex.matcher(text);
            
            if (matcher.find()) {
                return matcher.group(1);
            }
            
            // 如果没找到，返回响应的前100个字符作为fallback
            if (text.length() > 100) {
                return text.substring(0, 100) + "...";
            } else {
                return text;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting notification from text", e);
            return "AI分析完成";
        }
    }
    
    /**
     * 清理资源
     */
    public void shutdown() {
        // 如果有需要清理的资源，在这里处理
        Log.d(TAG, "DeepSeekApiClient shutdown");
    }
} 