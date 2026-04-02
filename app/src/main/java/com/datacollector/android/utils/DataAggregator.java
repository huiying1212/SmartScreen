package com.datacollector.android.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 借鉴Beiwe的time-binning数据处理策略：将多次采集的数据聚合为
 * 时间段摘要，为LLM提供更丰富的上下文。
 * Beiwe将CSV数据按小时分桶合并；我们将JSON数据按时间窗口聚合成统计摘要。
 */
public class DataAggregator {

    private static final String TAG = "DataAggregator";

    private final Context context;
    private final DataEncryptor dataEncryptor;
    private final CollectionConfig config;

    public DataAggregator(Context context) {
        this.context = context;
        this.dataEncryptor = new DataEncryptor(context);
        this.config = CollectionConfig.getInstance(context);
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
                    name.startsWith("context_data_")
                    && (name.endsWith(".json") || name.endsWith(".enc") || name.endsWith(".json.gz")));
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
            // Calendar / reminder accumulators (deduplicated by event_id)
            Map<Long, JSONObject> calendarEventMap = new HashMap<>();
            Map<Long, JSONObject> reminderEventMap = new HashMap<>();
            // Screen usage: keep the chronologically newest snapshot
            JSONObject latestScreenUsage = null;
            long latestScreenUsageTimestamp = -1;
            long earliestTimestamp = Long.MAX_VALUE;
            long latestTimestamp = 0;
            // Foreground app timeline: ordered list of (timestamp, package, category)
            List<long[]> fgTimestamps = new ArrayList<>();
            List<String[]> fgDetails = new ArrayList<>();
            // Per-category usage time accumulated from top_apps snapshots
            Map<String, Long> categoryUsageMs = new LinkedHashMap<>();
            // Location addresses and contexts (deduplicated)
            List<String> locationAddresses = new ArrayList<>();
            Map<String, Integer> locationContextCounts = new LinkedHashMap<>();
            // Weather: keep the latest snapshot
            JSONObject latestWeather = null;
            long latestWeatherTimestamp = 0;

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

                // Aggregate locations into a trail + collect readable addresses
                JSONObject location = contextData.optJSONObject("location");
                if (location != null && location.has("latitude")) {
                    double lat = location.optDouble("latitude", 0);
                    double lng = location.optDouble("longitude", 0);
                    // 过滤无效坐标 (0,0) 和标记为过期的位置
                    if (lat != 0.0 || lng != 0.0) {
                        boolean stale = location.optBoolean("is_stale", false);
                        JSONObject point = new JSONObject();
                        point.put("lat", lat);
                        point.put("lng", lng);
                        point.put("acc", location.optDouble("accuracy", -1));
                        point.put("t", fileTimestamp);
                        if (stale) point.put("stale", true);
                        if (locationTrail.length() < 50) {
                            locationTrail.put(point);
                        }
                    }

                    String addr = location.optString("readable_address", "");
                    if (!addr.isEmpty() && !isCoordString(addr)
                            && !locationAddresses.contains(addr)) {
                        locationAddresses.add(addr);
                    }
                }

                // Aggregate location context labels
                String locCtx = contextData.optString("location_context", "");
                if (!locCtx.isEmpty() && !"未知".equals(locCtx)) {
                    locationContextCounts.merge(locCtx, 1, Integer::sum);
                }

                // Aggregate activities (stored as "user_activity" by DataCollectionService)
                JSONObject activity = contextData.optJSONObject("user_activity");
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

                // Aggregate screen usage: keep the chronologically newest snapshot
                JSONObject screenUsage = contextData.optJSONObject("screen_usage");
                if (screenUsage != null) {
                    if (fileTimestamp > latestScreenUsageTimestamp) {
                        latestScreenUsageTimestamp = fileTimestamp;
                        latestScreenUsage = screenUsage;
                    }

                    // Foreground app timeline
                    String fgPkg = screenUsage.optString("foreground_app_package", "");
                    String fgCat = screenUsage.optString("foreground_app_category", "");
                    if (!fgPkg.isEmpty()) {
                        fgTimestamps.add(new long[]{fileTimestamp});
                        fgDetails.add(new String[]{fgPkg, fgCat});
                    }
                }

                // Aggregate calendar events (deduplicate by event_id, keep latest snapshot)
                JSONObject calendar = contextData.optJSONObject("calendar");
                if (calendar != null) {
                    JSONArray calEvents = calendar.optJSONArray("events");
                    if (calEvents != null) {
                        for (int i = 0; i < calEvents.length(); i++) {
                            JSONObject ev = calEvents.optJSONObject(i);
                            if (ev != null) {
                                long evId = ev.optLong("event_id", -1);
                                if (evId >= 0) calendarEventMap.put(evId, ev);
                            }
                        }
                    }
                }

                // Aggregate reminders (deduplicate by event_id)
                JSONObject remindersObj = contextData.optJSONObject("reminders");
                if (remindersObj != null) {
                    JSONArray remList = remindersObj.optJSONArray("reminders");
                    if (remList != null) {
                        for (int i = 0; i < remList.length(); i++) {
                            JSONObject rem = remList.optJSONObject(i);
                            if (rem != null) {
                                long evId = rem.optLong("event_id", -1);
                                if (evId >= 0) reminderEventMap.put(evId, rem);
                            }
                        }
                    }
                }

                // Aggregate weather: keep the most recent snapshot
                JSONObject weather = contextData.optJSONObject("weather");
                if (weather != null) {
                    long wTs = weather.optLong("timestamp", fileTimestamp);
                    if (wTs > latestWeatherTimestamp) {
                        latestWeatherTimestamp = wTs;
                        latestWeather = weather;
                    }
                }
            }

            // Build summary
            summary.put("window_hours", windowHours);
            summary.put("total_collections", totalCollections);
            summary.put("time_range_start", earliestTimestamp);
            summary.put("time_range_end", latestTimestamp);

            // Location summary (cluster nearby points within ~100m as same location)
            JSONObject locationSummary = new JSONObject();
            JSONArray clusters = clusterLocations(locationTrail, 100.0);
            int uniqueLocations = clusters.length();
            locationSummary.put("total_samples", locationTrail.length());
            locationSummary.put("unique_points", uniqueLocations);
            locationSummary.put("trail", locationTrail);
            locationSummary.put("location_clusters", clusters);

            // 主要停留地：停留时间最长的簇
            if (clusters.length() > 0) {
                long maxStay = -1;
                JSONObject primaryCluster = null;
                for (int i = 0; i < clusters.length(); i++) {
                    JSONObject cl = clusters.optJSONObject(i);
                    if (cl != null) {
                        long stay = cl.optLong("stay_minutes", 0);
                        if (stay > maxStay) {
                            maxStay = stay;
                            primaryCluster = cl;
                        }
                    }
                }
                if (primaryCluster != null) {
                    locationSummary.put("primary_stay_minutes", maxStay);
                }
            }

            // 总移动距离（带 GPS 抖动过滤）
            double totalDistanceM = computeTrailDistance(locationTrail);
            locationSummary.put("total_distance_meters", Math.round(totalDistanceM));
            if (totalDistanceM >= 1000) {
                locationSummary.put("total_distance_km",
                        Math.round(totalDistanceM / 100.0) / 10.0);
            }

            // 判断是否基本没移动
            locationSummary.put("is_stationary", uniqueLocations <= 1 && totalDistanceM < 200);

            JSONArray addrArray = new JSONArray();
            for (String a : locationAddresses) addrArray.put(a);
            locationSummary.put("visited_places", addrArray);

            JSONObject ctxSummary = new JSONObject();
            for (Map.Entry<String, Integer> e : locationContextCounts.entrySet()) {
                ctxSummary.put(e.getKey(), e.getValue());
            }
            locationSummary.put("context_summary", ctxSummary);

            summary.put("location_summary", locationSummary);

            // Activity summary
            JSONObject activitySummary = new JSONObject();
            for (Map.Entry<String, Integer> entry : activityCounts.entrySet()) {
                activitySummary.put(entry.getKey(), entry.getValue());
            }
            summary.put("activity_summary", activitySummary);

            // WiFi summary
            summary.put("wifi_history", wifiHistory);

            // Unique WiFi SSIDs
            Map<String, Integer> wifiSsidCounts = new LinkedHashMap<>();
            for (int i = 0; i < wifiHistory.length(); i++) {
                JSONObject w = wifiHistory.optJSONObject(i);
                if (w != null) {
                    String ssid = w.optString("ssid", "");
                    if (!ssid.isEmpty()) wifiSsidCounts.merge(ssid, 1, Integer::sum);
                }
            }
            JSONObject wifiSummary = new JSONObject();
            for (Map.Entry<String, Integer> e : wifiSsidCounts.entrySet()) {
                wifiSummary.put(e.getKey(), e.getValue());
            }
            summary.put("wifi_ssid_summary", wifiSummary);

            // Screen content samples
            summary.put("recent_screen_content", screenContentSamples);

            // Usage pattern
            JSONObject usagePattern = new JSONObject();
            for (Map.Entry<String, Integer> entry : appUsageCounts.entrySet()) {
                usagePattern.put(entry.getKey(), entry.getValue());
            }
            summary.put("usage_pattern", usagePattern);

            // Screen usage summary (latest snapshot)
            if (latestScreenUsage != null) {
                summary.put("screen_usage", latestScreenUsage);
            }

            // Foreground app timeline (chronological)
            JSONArray fgTimeline = new JSONArray();
            for (int i = 0; i < fgTimestamps.size(); i++) {
                JSONObject entry = new JSONObject();
                entry.put("t", fgTimestamps.get(i)[0]);
                entry.put("pkg", fgDetails.get(i)[0]);
                entry.put("cat", fgDetails.get(i)[1]);
                fgTimeline.put(entry);
            }
            summary.put("foreground_app_timeline", fgTimeline);

            // Per-category usage time from the latest top_apps snapshot
            if (latestScreenUsage != null) {
                JSONArray topApps = latestScreenUsage.optJSONArray("top_apps_today");
                if (topApps != null) {
                    for (int i = 0; i < topApps.length(); i++) {
                        JSONObject app = topApps.optJSONObject(i);
                        if (app != null) {
                            String cat = app.optString("category", "其他");
                            long ms = app.optLong("usage_ms", 0);
                            categoryUsageMs.merge(cat, ms, Long::sum);
                        }
                    }
                }
            }
            JSONObject categoryUsageSummary = new JSONObject();
            for (Map.Entry<String, Long> e : categoryUsageMs.entrySet()) {
                categoryUsageSummary.put(e.getKey(), e.getValue() / 60_000L);
            }
            summary.put("category_usage_minutes", categoryUsageSummary);

            // Calendar events summary (up to 30 unique events)
            JSONArray calendarSummary = new JSONArray();
            int calCount = 0;
            for (JSONObject ev : calendarEventMap.values()) {
                if (calCount >= 30) break;
                calendarSummary.put(ev);
                calCount++;
            }
            summary.put("calendar_events", calendarSummary);
            summary.put("calendar_event_count", calendarSummary.length());

            // Reminders summary (up to 30 unique events with reminders)
            JSONArray reminderSummary = new JSONArray();
            int remCount = 0;
            for (JSONObject rem : reminderEventMap.values()) {
                if (remCount >= 30) break;
                reminderSummary.put(rem);
                remCount++;
            }
            summary.put("reminder_events", reminderSummary);
            summary.put("reminder_event_count", reminderSummary.length());

            // Weather summary (latest snapshot)
            if (latestWeather != null) {
                summary.put("latest_weather", latestWeather);
            }

            summary.put("status", "aggregated");

        } catch (JSONException e) {
            Log.e(TAG, "Error building aggregation summary", e);
        }

        return summary;
    }

    private long extractTimestamp(String fileName) {
        try {
            String base = fileName;
            if (base.endsWith(".json.gz")) {
                base = base.substring(0, base.length() - ".json.gz".length());
            } else if (base.endsWith(".enc")) {
                base = base.substring(0, base.length() - ".enc".length());
            } else if (base.endsWith(".json")) {
                base = base.substring(0, base.length() - ".json".length());
            }
            String ts = base.substring("context_data_".length());
            return Long.parseLong(ts);
        } catch (Exception e) {
            return 0;
        }
    }

    private JSONObject readJsonFile(File file) {
        try {
            String name = file.getName();
            String jsonString;

            if (name.endsWith(".enc")) {
                jsonString = readEncryptedFile(file);
            } else if (name.endsWith(".json.gz")) {
                jsonString = readGzipFile(file);
            } else {
                jsonString = readPlainFile(file);
            }

            if (jsonString == null || jsonString.isEmpty()) return null;
            return new JSONObject(jsonString);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read file: " + file.getName(), e);
            return null;
        }
    }

    private String readPlainFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String readEncryptedFile(File file) {
        try {
            byte[] encryptedBytes = readAllBytes(file);
            byte[] decrypted = dataEncryptor.decryptBytes(encryptedBytes);
            if (decrypted == null) return null;

            boolean compressionEnabled = config.getBoolean(
                    CollectionConfig.KEY_DATA_COMPRESSION, true);
            if (compressionEnabled) {
                decrypted = decompressGzip(decrypted);
            }
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            Log.w(TAG, "Failed to decrypt file: " + file.getName(), e);
            return null;
        }
    }

    private String readGzipFile(File file) {
        try {
            byte[] compressed = readAllBytes(file);
            byte[] decompressed = decompressGzip(compressed);
            return new String(decompressed, "UTF-8");
        } catch (Exception e) {
            Log.w(TAG, "Failed to decompress file: " + file.getName(), e);
            return null;
        }
    }

    private byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private byte[] decompressGzip(byte[] data) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = gis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private boolean isCoordString(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '.' && c != ',' && c != '-' && c != ' ' && !Character.isDigit(c)) return false;
        }
        return true;
    }

    /**
     * 计算轨迹总距离（米），带 GPS 抖动过滤。
     *
     * 策略：跳过 stale 点；两点间距离若小于两点精度之和（即在各自误差圆
     * 重叠范围内），视为 GPS 噪声不计入总距离。这避免了静止时因信号
     * 漂移虚增几百米的问题。
     */
    private double computeTrailDistance(JSONArray trail) {
        if (trail == null || trail.length() < 2) return 0;

        double total = 0;
        double prevLat = Double.NaN, prevLng = Double.NaN;
        double prevAcc = 0;

        for (int i = 0; i < trail.length(); i++) {
            JSONObject pt = trail.optJSONObject(i);
            if (pt == null || pt.optBoolean("stale", false)) continue;

            double lat = pt.optDouble("lat", 0);
            double lng = pt.optDouble("lng", 0);
            double acc = pt.optDouble("acc", 50);
            if (lat == 0 && lng == 0) continue;

            if (!Double.isNaN(prevLat)) {
                double d = haversineMeters(prevLat, prevLng, lat, lng);
                // 抖动过滤：移动距离需超过两点精度之和才算真实位移
                double jitterThreshold = prevAcc + acc;
                if (d > jitterThreshold) {
                    total += d;
                }
            }
            prevLat = lat;
            prevLng = lng;
            prevAcc = acc;
        }
        return total;
    }

    /**
     * 精度加权聚类 + 停留时长追踪。
     *
     * 改进点：
     * 1. 聚类中心用精度加权平均更新，不再由第一个到达的点决定
     * 2. 记录每个簇的首末时间戳，计算停留时长
     * 3. 返回结构化的簇信息（JSONArray），而不仅是数量
     */
    private JSONArray clusterLocations(JSONArray trail, double radiusMeters) {
        if (trail == null || trail.length() == 0) return new JSONArray();

        // 每个簇：[sumLat, sumLng, totalWeight, count, firstTimestamp, lastTimestamp]
        List<double[]> clusters = new ArrayList<>();

        for (int i = 0; i < trail.length(); i++) {
            JSONObject pt = trail.optJSONObject(i);
            if (pt == null) continue;
            double lat = pt.optDouble("lat", 0);
            double lng = pt.optDouble("lng", 0);
            if (lat == 0 && lng == 0) continue;

            double acc = pt.optDouble("acc", 50);
            // 精度越高（acc 越小）权重越大；最低权重 0.1 防止除零
            double weight = Math.max(0.1, 1.0 / Math.max(acc, 1.0));
            long ts = pt.optLong("t", 0);

            int matchedIdx = -1;
            double minDist = Double.MAX_VALUE;
            for (int j = 0; j < clusters.size(); j++) {
                double[] c = clusters.get(j);
                double cLat = c[0] / c[2];
                double cLng = c[1] / c[2];
                double d = haversineMeters(lat, lng, cLat, cLng);
                if (d < radiusMeters && d < minDist) {
                    minDist = d;
                    matchedIdx = j;
                }
            }

            if (matchedIdx >= 0) {
                double[] c = clusters.get(matchedIdx);
                c[0] += lat * weight;    // sumLat
                c[1] += lng * weight;    // sumLng
                c[2] += weight;          // totalWeight
                c[3] += 1;              // count
                c[4] = Math.min(c[4], ts); // firstTimestamp
                c[5] = Math.max(c[5], ts); // lastTimestamp
            } else {
                clusters.add(new double[]{
                        lat * weight, lng * weight, weight, 1, ts, ts
                });
            }
        }

        // 构建输出
        JSONArray result = new JSONArray();
        for (double[] c : clusters) {
            try {
                JSONObject cluster = new JSONObject();
                cluster.put("lat", c[0] / c[2]);
                cluster.put("lng", c[1] / c[2]);
                cluster.put("point_count", (int) c[3]);
                long stayMs = (long) (c[5] - c[4]);
                cluster.put("stay_minutes", stayMs / 60_000L);
                cluster.put("first_seen", (long) c[4]);
                cluster.put("last_seen", (long) c[5]);
                result.put(cluster);
            } catch (JSONException ignored) {}
        }
        return result;
    }

    /** 向后兼容：返回唯一地点数 */
    private int countUniqueLocations(JSONArray trail, double radiusMeters) {
        return clusterLocations(trail, radiusMeters).length();
    }

    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
