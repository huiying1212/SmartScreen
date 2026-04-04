package com.datacollector.android.utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.Iterator;

/**
 * 数据脱敏工具类。
 *
 * 在数据发送到第三方 LLM API 或存储到 SharedPreferences 之前，
 * 对敏感 PII 字段进行脱敏处理：
 *
 *   - GPS 坐标：截断到小数点后 2 位（约 1.1km 精度）
 *   - 可读地址：替换为模糊区域描述（仅保留城市级别）
 *   - 邮箱：掩码处理（a***@domain.com）
 *   - 姓名：仅保留姓氏首字 + *
 *   - MAC 地址 / BSSID：SHA-256 哈希后取前 8 位
 *   - IP 地址：掩码最后一段（192.168.1.***）
 *   - WiFi SSID：保留前 2 字符 + ***
 *   - 蓝牙设备名：保留前 2 字符 + ***
 *   - 日历事件：移除 description、organizer、attendees 的详细 PII
 *   - App 包名：保留（非 PII，LLM 需要用于判断使用场景）
 *
 * 设计原则：
 *   1. 不修改原始 JSONObject，返回脱敏后的深拷贝
 *   2. 保留 LLM 评分和关键词提取所需的语义信息
 *   3. 移除可直接定位到个人身份的信息
 */
public class DataSanitizer {

    private static final String TAG = "DataSanitizer";

    private DataSanitizer() {}

    /**
     * 对完整的上下文快照进行脱敏，返回脱敏后的深拷贝。
     * 适用于发送给 LLM API 的场景。
     */
    public static JSONObject sanitizeSnapshot(JSONObject snapshot) {
        if (snapshot == null) return null;
        try {
            JSONObject sanitized = deepCopy(snapshot);

            sanitizeLocation(sanitized);
            sanitizeWifi(sanitized);
            sanitizeBluetooth(sanitized);
            sanitizeCalendar(sanitized);
            sanitizeScreenUsage(sanitized);

            return sanitized;
        } catch (Exception e) {
            Log.e(TAG, "sanitizeSnapshot failed, returning original", e);
            return snapshot;
        }
    }

    /**
     * 对聚合数据进行脱敏（DataAggregator 输出）。
     */
    public static JSONObject sanitizeAggregatedData(JSONObject aggregated) {
        if (aggregated == null) return null;
        try {
            JSONObject sanitized = deepCopy(aggregated);

            // 位置摘要
            sanitizeLocationSummary(sanitized);
            // WiFi 历史和摘要
            sanitizeWifiSummary(sanitized);
            // 蓝牙摘要
            sanitizeBluetoothSummary(sanitized);
            // 日历事件
            sanitizeCalendarEvents(sanitized, "calendar_events");
            sanitizeCalendarEvents(sanitized, "reminder_events");

            return sanitized;
        } catch (Exception e) {
            Log.e(TAG, "sanitizeAggregatedData failed", e);
            return aggregated;
        }
    }

    // ── 位置脱敏 ────────────────────────────────────────────────

    private static void sanitizeLocation(JSONObject data) throws JSONException {
        JSONObject location = data.optJSONObject("location");
        if (location == null) return;

        // GPS 坐标模糊化：截断到小数点后 2 位
        if (location.has("latitude")) {
            location.put("latitude", truncateCoord(location.optDouble("latitude", 0)));
        }
        if (location.has("longitude")) {
            location.put("longitude", truncateCoord(location.optDouble("longitude", 0)));
        }

        // 移除精确辅助字段
        location.remove("altitude");
        location.remove("bearing");
        location.remove("speed");

        // 可读地址 → 仅保留城市级别
        if (location.has("readable_address")) {
            String addr = location.optString("readable_address", "");
            location.put("readable_address", fuzzyAddress(addr));
        }
    }

    private static void sanitizeLocationSummary(JSONObject data) throws JSONException {
        JSONObject locSummary = data.optJSONObject("location_summary");
        if (locSummary == null) return;

        // 模糊化轨迹点坐标
        JSONArray trail = locSummary.optJSONArray("trail");
        if (trail != null) {
            for (int i = 0; i < trail.length(); i++) {
                JSONObject pt = trail.optJSONObject(i);
                if (pt != null) {
                    if (pt.has("lat")) pt.put("lat", truncateCoord(pt.optDouble("lat", 0)));
                    if (pt.has("lng")) pt.put("lng", truncateCoord(pt.optDouble("lng", 0)));
                }
            }
        }

        // 模糊化聚类中心坐标
        JSONArray clusters = locSummary.optJSONArray("location_clusters");
        if (clusters != null) {
            for (int i = 0; i < clusters.length(); i++) {
                JSONObject cl = clusters.optJSONObject(i);
                if (cl != null) {
                    if (cl.has("lat")) cl.put("lat", truncateCoord(cl.optDouble("lat", 0)));
                    if (cl.has("lng")) cl.put("lng", truncateCoord(cl.optDouble("lng", 0)));
                }
            }
        }

        // 到过的地方 → 模糊化地址
        JSONArray places = locSummary.optJSONArray("visited_places");
        if (places != null) {
            JSONArray fuzzyPlaces = new JSONArray();
            for (int i = 0; i < places.length(); i++) {
                fuzzyPlaces.put(fuzzyAddress(places.optString(i, "")));
            }
            locSummary.put("visited_places", fuzzyPlaces);
        }
    }

    // ── WiFi 脱敏 ───────────────────────────────────────────────

    private static void sanitizeWifi(JSONObject data) throws JSONException {
        JSONObject wifi = data.optJSONObject("wifi_info");
        if (wifi == null) return;

        JSONObject ap = wifi.optJSONObject("connected_ap");
        if (ap != null) {
            if (ap.has("ssid")) ap.put("ssid", maskSsid(ap.optString("ssid", "")));
            if (ap.has("bssid")) ap.put("bssid", hashIdentifier(ap.optString("bssid", "")));
            if (ap.has("ip_address")) ap.put("ip_address", maskIp(ap.optString("ip_address", "")));
        }
    }

    private static void sanitizeWifiSummary(JSONObject data) throws JSONException {
        // wifi_history
        JSONArray wifiHistory = data.optJSONArray("wifi_history");
        if (wifiHistory != null) {
            for (int i = 0; i < wifiHistory.length(); i++) {
                JSONObject w = wifiHistory.optJSONObject(i);
                if (w != null && w.has("ssid")) {
                    w.put("ssid", maskSsid(w.optString("ssid", "")));
                }
            }
        }

        // wifi_ssid_summary: key 是 SSID
        JSONObject wifiSummary = data.optJSONObject("wifi_ssid_summary");
        if (wifiSummary != null) {
            JSONObject masked = new JSONObject();
            Iterator<String> keys = wifiSummary.keys();
            while (keys.hasNext()) {
                String ssid = keys.next();
                masked.put(maskSsid(ssid), wifiSummary.get(ssid));
            }
            data.put("wifi_ssid_summary", masked);
        }
    }

    // ── 蓝牙脱敏 ───────────────────────────────────────────────

    private static void sanitizeBluetooth(JSONObject data) throws JSONException {
        JSONObject bt = data.optJSONObject("bluetooth_devices");
        if (bt == null) return;

        sanitizeDeviceArray(bt, "paired_devices");
        sanitizeDeviceArray(bt, "nearby_devices");
    }

    private static void sanitizeDeviceArray(JSONObject bt, String key) throws JSONException {
        JSONArray devices = bt.optJSONArray(key);
        if (devices == null) return;

        for (int i = 0; i < devices.length(); i++) {
            JSONObject d = devices.optJSONObject(i);
            if (d == null) continue;
            if (d.has("mac_address")) d.put("mac_address", hashIdentifier(d.optString("mac_address", "")));
            if (d.has("name")) d.put("name", maskDeviceName(d.optString("name", "")));
            // 保留 device_class（非 PII，LLM 需要判断设备类型）
        }
    }

    private static void sanitizeBluetoothSummary(JSONObject data) throws JSONException {
        JSONObject btSummary = data.optJSONObject("bluetooth_summary");
        if (btSummary == null) return;

        // 设备名列表脱敏
        JSONArray names = btSummary.optJSONArray("unique_device_names");
        if (names != null) {
            JSONArray masked = new JSONArray();
            for (int i = 0; i < names.length(); i++) {
                masked.put(maskDeviceName(names.optString(i, "")));
            }
            btSummary.put("unique_device_names", masked);
        }
        // device_class_distribution 保留（非 PII）
    }

    // ── 日历脱敏 ────────────────────────────────────────────────

    private static void sanitizeCalendar(JSONObject data) throws JSONException {
        JSONObject calendar = data.optJSONObject("calendar");
        if (calendar == null) return;

        // 日历账户信息脱敏
        JSONArray calendars = calendar.optJSONArray("calendars");
        if (calendars != null) {
            for (int i = 0; i < calendars.length(); i++) {
                JSONObject cal = calendars.optJSONObject(i);
                if (cal == null) continue;
                if (cal.has("account_name")) cal.put("account_name", maskEmail(cal.optString("account_name", "")));
                // display_name 保留（通常是 "个人"、"工作" 等非 PII 标签）
            }
        }

        // 事件脱敏
        sanitizeCalendarEvents(data.optJSONObject("calendar"), "events");
    }

    private static void sanitizeCalendarEvents(JSONObject container, String key) throws JSONException {
        if (container == null) return;
        JSONArray events = container.optJSONArray(key);
        if (events == null) return;

        for (int i = 0; i < events.length(); i++) {
            JSONObject ev = events.optJSONObject(i);
            if (ev == null) continue;

            // title 保留（LLM 需要判断用户在做什么）
            // description 移除（可能包含敏感详情）
            ev.remove("description");
            // location 模糊化
            if (ev.has("location")) {
                String loc = ev.optString("location", "");
                ev.put("location", fuzzyAddress(loc));
            }
            // organizer 邮箱脱敏
            if (ev.has("organizer")) {
                ev.put("organizer", maskEmail(ev.optString("organizer", "")));
            }
            // attendees 脱敏
            JSONArray attendees = ev.optJSONArray("attendees");
            if (attendees != null) {
                for (int j = 0; j < attendees.length(); j++) {
                    JSONObject att = attendees.optJSONObject(j);
                    if (att == null) continue;
                    if (att.has("name")) att.put("name", maskName(att.optString("name", "")));
                    if (att.has("email")) att.put("email", maskEmail(att.optString("email", "")));
                }
            }
            // calendar_name 保留（通常是 "个人"、"工作"）
        }
    }

    // ── 屏幕使用脱敏 ───────────────────────────────────────────

    private static void sanitizeScreenUsage(JSONObject data) throws JSONException {
        // App 包名和使用时长保留（LLM 核心判断依据，非直接 PII）
        // 无需额外脱敏
    }

    // ── 脱敏工具方法 ───────────────────────────────────────────

    /** GPS 坐标截断到小数点后 2 位（约 1.1km 精度） */
    static double truncateCoord(double coord) {
        return Math.floor(coord * 100.0) / 100.0;
    }

    /**
     * 地址模糊化：仅保留最后一个逗号分隔的部分（通常是城市名）。
     * 例如 "星巴克, 南京西路, 上海" → "上海"
     * 如果没有逗号，返回 "某地点"。
     */
    static String fuzzyAddress(String address) {
        if (address == null || address.trim().isEmpty()) return "";
        String trimmed = address.trim();

        // 尝试用中文逗号或英文逗号分割
        String[] parts = trimmed.split("[,，]");
        if (parts.length >= 2) {
            // 返回最后一个部分（城市级别）
            return parts[parts.length - 1].trim();
        }

        // 没有逗号分隔符，可能是单一地名 → 返回泛化描述
        // 如果很短（<=4字符），可能本身就是城市名，保留
        if (trimmed.length() <= 4) return trimmed;
        return "某地点";
    }

    /** 邮箱掩码：a***@domain.com */
    static String maskEmail(String email) {
        if (email == null || email.isEmpty()) return "";
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(atIdx);
    }

    /** 姓名掩码：保留第一个字符 + * */
    static String maskName(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String trimmed = name.trim();
        if (trimmed.length() <= 1) return trimmed;
        return trimmed.charAt(0) + "*".repeat(Math.min(trimmed.length() - 1, 3));
    }

    /** MAC/BSSID 哈希：SHA-256 取前 8 位 hex */
    static String hashIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(identifier.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "hashed";
        }
    }

    /** IP 地址掩码：192.168.1.*** */
    static String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) return "";
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) return "***";
        return ip.substring(0, lastDot + 1) + "***";
    }

    /** SSID 掩码：保留前 2 字符 + *** */
    static String maskSsid(String ssid) {
        if (ssid == null || ssid.isEmpty()) return "";
        String cleaned = ssid.replace("\"", "").trim();
        if (cleaned.length() <= 2) return cleaned;
        return cleaned.substring(0, 2) + "***";
    }

    /** 蓝牙设备名掩码：保留前 2 字符 + *** */
    static String maskDeviceName(String name) {
        if (name == null || name.isEmpty() || "unknown".equals(name) || "unnamed".equals(name)) {
            return name;
        }
        if (name.length() <= 2) return name;
        return name.substring(0, 2) + "***";
    }

    // ── JSON 深拷贝 ────────────────────────────────────────────

    private static JSONObject deepCopy(JSONObject source) throws JSONException {
        return new JSONObject(source.toString());
    }
}
