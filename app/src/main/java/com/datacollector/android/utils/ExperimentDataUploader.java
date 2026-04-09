package com.datacollector.android.utils;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 将本地交互日志和上下文数据定期上报到实验服务器。
 *
 * 上报策略：
 *   - 每 30 分钟尝试一次批量上传
 *   - 上传成功后本地日志文件标记为已同步（重命名加 .synced 后缀）
 *   - 网络失败时静默跳过，下次重试
 *
 * 使用方式：
 * <pre>
 *   ExperimentDataUploader.get(context).start();
 * </pre>
 */
public class ExperimentDataUploader {

    private static final String TAG = "DataUploader";
    private static final long UPLOAD_INTERVAL_MS = 30 * 60_000L; // 30 分钟
    private static final int CONNECT_TIMEOUT = 15_000;
    private static final int READ_TIMEOUT = 30_000;

    private static volatile ExperimentDataUploader instance;

    private final Context appContext;
    private final CollectionConfig config;
    private final Handler handler;
    private boolean started = false;

    private ExperimentDataUploader(Context context) {
        this.appContext = context.getApplicationContext();
        this.config = CollectionConfig.getInstance(context);
        this.handler = new Handler(Looper.getMainLooper());
    }

    public static ExperimentDataUploader get(Context context) {
        if (instance == null) {
            synchronized (ExperimentDataUploader.class) {
                if (instance == null) {
                    instance = new ExperimentDataUploader(context);
                }
            }
        }
        return instance;
    }

    /** 启动定期上报 */
    public void start() {
        if (started) return;
        started = true;
        // 首次延迟 2 分钟，让 App 先完成初始化
        handler.postDelayed(uploadRunnable, 2 * 60_000L);
        Log.i(TAG, "Uploader started (interval=" + UPLOAD_INTERVAL_MS / 60000 + "min)");
    }

    /** 立即触发一次上传（异步） */
    public void uploadNow() {
        new Thread(this::doUpload).start();
    }

    private final Runnable uploadRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> doUpload()).start();
            handler.postDelayed(this, UPLOAD_INTERVAL_MS);
        }
    };

    // ── 核心上传逻辑 ──────────────────────────────────────────

    private void doUpload() {
        String serverUrl = config.getString(CollectionConfig.KEY_EXPERIMENT_SERVER_URL, "");
        String pid = config.getString(CollectionConfig.KEY_PARTICIPANT_ID, "");
        if (serverUrl.isEmpty() || pid.isEmpty()) {
            Log.d(TAG, "Server URL or participant ID not set, skipping upload");
            return;
        }

        // 1. 注册参与者（幂等）
        registerParticipant(serverUrl, pid);

        // 2. 上传交互日志
        uploadInteractionLogs(serverUrl, pid);
    }

    private void registerParticipant(String serverUrl, String pid) {
        try {
            JSONObject body = new JSONObject();
            body.put("participant_id", pid);
            body.put("device_model", Build.MODEL);
            body.put("android_ver", Build.VERSION.RELEASE);
            postJson(serverUrl + "/api/register", body);
        } catch (Exception e) {
            Log.w(TAG, "Register failed (will retry)", e);
        }
    }

    private void uploadInteractionLogs(String serverUrl, String pid) {
        File logDir = UserInteractionLogger.get(appContext).getLogDir();
        if (!logDir.exists()) return;

        File[] files = logDir.listFiles((dir, name) ->
                name.endsWith(".jsonl") && !name.endsWith(".synced.jsonl"));
        if (files == null || files.length == 0) return;

        for (File file : files) {
            try {
                List<JSONObject> entries = readJsonlFile(file);
                if (entries.isEmpty()) continue;

                // 给每条日志注入 participant_id
                JSONArray arr = new JSONArray();
                for (JSONObject entry : entries) {
                    entry.put("participant_id", pid);
                    arr.put(entry);
                }

                JSONObject wrapper = new JSONObject();
                wrapper.put("logs", arr);
                String resp = postJson(serverUrl + "/api/logs", wrapper);

                if (resp != null) {
                    // 标记为已同步
                    File synced = new File(file.getParent(),
                            file.getName().replace(".jsonl", ".synced.jsonl"));
                    file.renameTo(synced);
                    Log.i(TAG, "Uploaded " + entries.size() + " logs from " + file.getName());
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to upload " + file.getName(), e);
            }
        }
    }

    // ── HTTP 工具 ─────────────────────────────────────────────

    private String postJson(String urlStr, JSONObject body) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);

            // 可选 API Key
            String apiKey = config.getString("experiment_api_key", "");
            if (!apiKey.isEmpty()) {
                conn.setRequestProperty("X-API-Key", apiKey);
            }

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                try (BufferedReader br = new BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    return sb.toString();
                }
            } else {
                // Drain error stream to avoid connection pool exhaustion
                try (java.io.InputStream es = conn.getErrorStream()) {
                    if (es != null) {
                        byte[] buf = new byte[1024];
                        while (es.read(buf) != -1) { /* discard */ }
                    }
                } catch (Exception ignored) {}
                Log.w(TAG, "HTTP " + code + " from " + urlStr);
                return null;
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private List<JSONObject> readJsonlFile(File file) {
        List<JSONObject> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    list.add(new JSONObject(line));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading " + file.getName(), e);
        }
        return list;
    }

    public void stop() {
        handler.removeCallbacks(uploadRunnable);
        started = false;
    }
}
