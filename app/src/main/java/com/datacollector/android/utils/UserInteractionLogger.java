package com.datacollector.android.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 用户交互埋点日志。
 *
 * 每条日志为一行 JSON（JSONL 格式），按天分文件存储到
 * {@code <externalFilesDir>/interaction_logs/interaction_YYYY-MM-DD.jsonl}。
 *
 * 线程安全：所有写操作通过 synchronized 保护。
 *
 * 使用方式：
 * <pre>
 *   UserInteractionLogger.get(context).log("overlay_click", "score", 72);
 * </pre>
 */
public class UserInteractionLogger {

    private static final String TAG = "InteractionLogger";
    private static final String DIR_NAME = "interaction_logs";

    private static volatile UserInteractionLogger instance;

    private final Context appContext;
    private final File logDir;

    private UserInteractionLogger(Context context) {
        this.appContext = context.getApplicationContext();
        this.logDir = new File(appContext.getExternalFilesDir(null), DIR_NAME);
        if (!logDir.exists()) logDir.mkdirs();
    }

    public static UserInteractionLogger get(Context context) {
        if (instance == null) {
            synchronized (UserInteractionLogger.class) {
                if (instance == null) {
                    instance = new UserInteractionLogger(context);
                }
            }
        }
        return instance;
    }

    // ── 便捷方法 ──────────────────────────────────────────────

    /** 记录一个无额外参数的事件 */
    public void log(String event) {
        log(event, (JSONObject) null);
    }

    /** 记录一个带单个 key-value 的事件 */
    public void log(String event, String key, Object value) {
        try {
            JSONObject params = new JSONObject();
            params.put(key, value);
            log(event, params);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to build params", e);
        }
    }

    /** 记录一个带两个 key-value 的事件 */
    public void log(String event, String k1, Object v1, String k2, Object v2) {
        try {
            JSONObject params = new JSONObject();
            params.put(k1, v1);
            params.put(k2, v2);
            log(event, params);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to build params", e);
        }
    }

    /** 记录一个带完整参数的事件 */
    public void log(String event, JSONObject params) {
        try {
            JSONObject entry = new JSONObject();
            long now = System.currentTimeMillis();
            entry.put("ts", now);
            entry.put("time", formatTime(now));
            entry.put("event", event);
            if (params != null && params.length() > 0) {
                entry.put("params", params);
            }
            writeLine(entry.toString());
        } catch (JSONException e) {
            Log.w(TAG, "Failed to write log entry", e);
        }
    }

    // ── 内部实现 ──────────────────────────────────────────────

    private synchronized void writeLine(String line) {
        File file = new File(logDir, "interaction_" + todayDate() + ".jsonl");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
            pw.println(line);
        } catch (IOException e) {
            Log.e(TAG, "IO error writing log", e);
        }
    }

    private static String todayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(millis));
    }

    /** 返回日志目录，供导出或清理使用 */
    public File getLogDir() {
        return logDir;
    }
}
