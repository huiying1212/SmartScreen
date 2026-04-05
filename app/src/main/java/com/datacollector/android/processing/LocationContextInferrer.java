package com.datacollector.android.processing;

import android.content.Context;

import com.datacollector.android.utils.WifiFingerprint;

import org.json.JSONObject;

/**
 * 多信号加权评分推断位置上下文。
 *
 * 对于"家"和"公司/学校"，优先使用 WiFi BSSID 指纹（自动学习）；
 * 指纹未知时仅判断通勤/户外/室内三类，不猜测家或公司。
 * 如果连基本场景都无法判定，返回 null（不写入数据）。
 *
 * 从 DataCollectionService 中提取，属于中层处理逻辑。
 */
public class LocationContextInferrer {

    private final Context context;

    public LocationContextInferrer(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 根据上下文数据推断位置场景。
     *
     * @param contextData 包含 location / user_activity / wifi_info / bluetooth_devices 的完整上下文
     * @return 位置标签（"家"/"公司"/"通勤中"/"户外"/"室内"），或 null 表示无法判断
     */
    public String infer(JSONObject contextData) {
        try {
            double sCommute = 0;
            double sOutdoor = 0;
            double sIndoor  = 0;

            // ── 提取原始数据 ──
            JSONObject activity = contextData.optJSONObject("user_activity");
            String actType = activity != null ? activity.optString("activity_type", "unknown") : "unknown";
            int actConf = activity != null ? activity.optInt("confidence", 50) : 0;
            double confWeight = actConf / 100.0;

            JSONObject location = contextData.optJSONObject("location");
            double accuracy = 999;
            float speed = 0;
            boolean hasGps = false;
            boolean isStale = false;
            if (location != null && location.has("latitude")) {
                accuracy = location.optDouble("accuracy", 999);
                speed = (float) location.optDouble("speed", 0);
                isStale = location.optBoolean("is_stale", false);
                hasGps = true;
            }

            JSONObject wifi = contextData.optJSONObject("wifi_info");
            JSONObject ap = wifi != null ? wifi.optJSONObject("connected_ap") : null;
            boolean connectedToWifi = ap != null
                    && !ap.optString("ssid", "").isEmpty();
            String bssid = ap != null ? ap.optString("bssid", "") : "";

            JSONObject bt = contextData.optJSONObject("bluetooth_devices");
            int btCount = bt != null ? bt.optInt("device_count", 0) : 0;

            // ── WiFi 指纹优先判定家/公司 ──
            if (connectedToWifi && !bssid.isEmpty()) {
                String place = WifiFingerprint.getInstance(context).classify(bssid);
                if (place != null) {
                    return place; // 指纹已学会，直接返回
                }
            }

            // ── 指纹未知，回退到通勤/户外/室内三分类 ──

            // 信号 1: 活动识别
            if ("driving".equals(actType) || "cycling".equals(actType)) {
                sCommute += 5.0 * confWeight;
            } else if ("running".equals(actType)) {
                sOutdoor += 4.0 * confWeight;
            } else if ("walking".equals(actType)) {
                sOutdoor += 2.5 * confWeight;
                sCommute += 0.5 * confWeight;
            } else if ("still".equals(actType)) {
                sIndoor += 1.5 * confWeight;
            }

            // 信号 2: GPS 速度
            if (hasGps && !isStale) {
                if (speed > 5.0f)      sCommute += 4.0;
                else if (speed > 3.0f) sCommute += 2.5;
                else if (speed > 1.2f) sOutdoor += 1.5;
                else                   sIndoor  += 0.5;
            }

            // 信号 3: GPS 精度
            if (hasGps && !isStale) {
                if (accuracy < 15)       sOutdoor += 3.0;
                else if (accuracy < 30)  sOutdoor += 1.5;
                else if (accuracy < 60)  { /* 模糊地带 */ }
                else if (accuracy < 150) sIndoor  += 2.0;
                else                     sIndoor  += 3.0;
            }

            // 信号 4: WiFi 连接
            if (connectedToWifi) {
                sIndoor += 2.5;
            } else {
                sOutdoor += 0.8;
                sCommute += 0.5;
            }

            // 信号 5: 蓝牙设备数
            if (btCount >= 5) {
                sIndoor += 1.5;
            } else if (btCount >= 2) {
                sIndoor += 0.5;
            }

            // ── 选出得分最高的标签（仅三类）──
            double bestScore = 1.0; // 最低门槛
            String best = null;

            if (sCommute > bestScore) { best = "通勤中"; bestScore = sCommute; }
            if (sOutdoor > bestScore) { best = "户外";   bestScore = sOutdoor; }
            if (sIndoor  > bestScore) { best = "室内";   bestScore = sIndoor;  }

            return best; // null 表示判断不出来，不写入

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 contextData 中提取 WiFi BSSID 并记录到指纹学习器。
     */
    public void recordWifiFingerprint(JSONObject contextData) {
        String bssid = extractBssid(contextData);
        if (bssid != null) {
            WifiFingerprint.getInstance(context).recordObservation(bssid);
        }
    }

    private String extractBssid(JSONObject contextData) {
        JSONObject wifi = contextData.optJSONObject("wifi_info");
        if (wifi == null) return null;
        JSONObject ap = wifi.optJSONObject("connected_ap");
        if (ap == null) return null;
        String bssid = ap.optString("bssid", "");
        return bssid.isEmpty() ? null : bssid;
    }
}
