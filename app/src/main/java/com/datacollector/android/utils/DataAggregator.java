package com.datacollector.android.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 借鉴Beiwe的time-binning数据处理策略：将多次采集的数据聚合为
 * 时间段摘要，为LLM提供更丰富的上下文。
 * Beiwe将CSV数据按小时分桶合并；我们将JSON数据按时间窗口聚合成统计摘要。
 */
public class DataAggregator {

    private static final String TAG = "DataAggregator";

    private final Context context;

    public DataAggregator(Context context) {
        this.context = context;
    }

    /**
     * 聚合指定时间窗口内的所有数据文件，生成摘要
     * @param windowHours 回溯的小时数
     * @return 聚合摘要JSON
     */
    public JSONObject aggregateRecentData(int windowHours) {
        JSONObject summary = new JSONObject();
        long cutoffTime = System.currentTimeMillis() - (windowHours * 3600_000L);

        try {
            File dataDir = new File(context.getExternalFilesDir(null), "data");
            if (!dataDir.exists()) {
                summary.put("status", "no_data");
                return summary;
            }

            File[] files = dataDir.listFiles((dir, name) ->
                    name.startsWith("context_data_") && name.endsWith(".json"));
            if (files == null || files.length == 0) {
                summary.put("status", "no_data");
                return summary;
            }

            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            // Aggregation accumulators
            int totalCollections = 0;
            JSONArray locationTrail = new JSONArray();
            Map<String, Integer> activityCounts = new HashMap<>();
            Map<String, Integer> appUsageCounts = new HashMap<>();
            JSONArray screenContentSamples = new JSONArray();
            JSONArray wifiHistory = new JSONArray();
            long earliestTimestamp = Long.MAX_VALUE;
            long latestTimestamp = 0;

            for (File file : files) {
                long fileTimestamp = extractTimestamp(file.getName());
                if (fileTimestamp < cutoffTime) continue;

                totalCollections++;
                earliestTimestamp = Math.min(earliestTimestamp, fileTimestamp);
                latestTimestamp = Math.max(latestTimestamp, fileTimestamp);

                JSONObject data = readJsonFile(file);
                if (data == null) continue;

                JSONObject contextData = data.optJSONObject("context_data");
                if (contextData == null) continue;

                // Aggregate locations into a trail
                JSONObject location = contextData.optJSONObject("location");
                if (location != null && location.has("latitude")) {
                    JSONObject point = new JSONObject();
                    point.put("lat", location.optDouble("latitude"));
                    point.put("lng", location.optDouble("longitude"));
                    point.put("t", fileTimestamp);
                    if (locationTrail.length() < 50) {
                        locationTrail.put(point);
                    }
                }

                // Aggregate activities
                JSONObject activity = contextData.optJSONObject("activity");
                if (activity != null) {
                    String type = activity.optString("activity_type", "unknown");
                    activityCounts.merge(type, 1, Integer::sum);
                }

                // Aggregate WiFi connections
                JSONObject wifi = contextData.optJSONObject("wifi_info");
                if (wifi != null && wifi.has("ssid")) {
                    JSONObject wifiEntry = new JSONObject();
                    wifiEntry.put("ssid", wifi.optString("ssid"));
                    wifiEntry.put("t", fileTimestamp);
                    if (wifiHistory.length() < 20) {
                        wifiHistory.put(wifiEntry);
                    }
                }

                // Aggregate screen content (sample the latest few)
                JSONArray screens = contextData.optJSONArray("screen_content");
                if (screens != null && screenContentSamples.length() < 10) {
                    for (int i = 0; i < Math.min(screens.length(), 3); i++) {
                        screenContentSamples.put(screens.get(i));
                    }
                }

                // Track trigger reasons as a proxy for app usage
                String trigger = contextData.optString("trigger_reason", "");
                if (!trigger.isEmpty()) {
                    appUsageCounts.merge(trigger, 1, Integer::sum);
                }
            }

            // Build summary
            summary.put("window_hours", windowHours);
            summary.put("total_collections", totalCollections);
            summary.put("time_range_start", earliestTimestamp);
            summary.put("time_range_end", latestTimestamp);

            // Location summary
            JSONObject locationSummary = new JSONObject();
            locationSummary.put("unique_points", locationTrail.length());
            locationSummary.put("trail", locationTrail);
            summary.put("location_summary", locationSummary);

            // Activity summary
            JSONObject activitySummary = new JSONObject();
            for (Map.Entry<String, Integer> entry : activityCounts.entrySet()) {
                activitySummary.put(entry.getKey(), entry.getValue());
            }
            summary.put("activity_summary", activitySummary);

            // WiFi summary
            summary.put("wifi_history", wifiHistory);

            // Screen content samples
            summary.put("recent_screen_content", screenContentSamples);

            // Usage pattern
            JSONObject usagePattern = new JSONObject();
            for (Map.Entry<String, Integer> entry : appUsageCounts.entrySet()) {
                usagePattern.put(entry.getKey(), entry.getValue());
            }
            summary.put("usage_pattern", usagePattern);

            summary.put("status", "aggregated");

        } catch (JSONException e) {
            Log.e(TAG, "Error building aggregation summary", e);
        }

        return summary;
    }

    private long extractTimestamp(String fileName) {
        try {
            String ts = fileName.substring("context_data_".length(), fileName.length() - ".json".length());
            return Long.parseLong(ts);
        } catch (Exception e) {
            return 0;
        }
    }

    private JSONObject readJsonFile(File file) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return new JSONObject(sb.toString());
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Failed to read file: " + file.getName());
            return null;
        }
    }
}
