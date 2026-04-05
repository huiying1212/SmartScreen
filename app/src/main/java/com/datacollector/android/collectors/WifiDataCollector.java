package com.datacollector.android.collectors;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.datacollector.android.utils.CollectionConfig;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Wi-Fi 数据收集器。
 *
 * 收集当前已连接 AP 的详情（SSID、BSSID、RSSI、连接速度、频率）。
 */
public class WifiDataCollector extends BaseDataCollector<JSONObject> {

    private static final String COLLECTOR_ID = "wifi_info";

    private WifiManager wifiManager;

    public WifiDataCollector(Context context) {
        super(context, COLLECTOR_ID);
        wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) return false;
        if (!CollectionConfig.getInstance(context)
                .getBoolean(CollectionConfig.KEY_WIFI_ENABLED, true)) return false;
        if (wifiManager == null || !wifiManager.isWifiEnabled()) return false;
        // ACCESS_FINE_LOCATION 是 Android 8.1+ 获取 SSID 所必需的
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void doStartCollection() {
    }

    @Override
    protected void doStopCollection() {
    }

    @Override
    protected JSONObject doCollectData() {
        try {
            JSONObject result = new JSONObject();

            // TODO: Android 12+ 弃用了 getConnectionInfo()，
            //  后续应迁移至 ConnectivityManager.getNetworkCapabilities() +
            //  NetworkCapabilities.getTransportInfo() 方式获取 WifiInfo。
            //  当前 targetSdk=33 下仍可正常工作，但会产生编译警告。
            @SuppressWarnings("deprecation")
            WifiInfo connectedInfo = wifiManager.getConnectionInfo();
            if (connectedInfo != null && connectedInfo.getNetworkId() != -1) {
                JSONObject connected = new JSONObject();
                String ssid = connectedInfo.getSSID();
                // Android Q+ 若无位置权限则返回 <unknown ssid>
                connected.put("ssid", ssid != null ? ssid.replace("\"", "") : "unknown");
                connected.put("bssid", connectedInfo.getBSSID());
                connected.put("rssi", connectedInfo.getRssi());
                connected.put("link_speed_mbps", connectedInfo.getLinkSpeed());
                connected.put("frequency_mhz", connectedInfo.getFrequency());
                connected.put("ip_address", intToIp(connectedInfo.getIpAddress()));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    connected.put("wifi_standard", wifiStandardName(connectedInfo.getWifiStandard()));
                }
                result.put("connected_ap", connected);
            }

            result.put("collector_id", COLLECTOR_ID);
            result.put("data_collection_time", System.currentTimeMillis());
            return result;

        } catch (JSONException e) {
            Log.w("WifiCollector", "Failed to build WiFi data", e);
            return null;
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────

    private String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    private String wifiStandardName(int standard) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unknown";
        // WifiInfo.WIFI_STANDARD_* constants (API 29): LEGACY=0, 11N=4, 11AC=5, 11AX=6
        switch (standard) {
            case 0:  return "802.11a/b/g";       // WIFI_STANDARD_LEGACY
            case 4:  return "Wi-Fi 4 (802.11n)"; // WIFI_STANDARD_11N
            case 5:  return "Wi-Fi 5 (802.11ac)";// WIFI_STANDARD_11AC
            case 6:  return "Wi-Fi 6 (802.11ax)";// WIFI_STANDARD_11AX
            default: return "unknown(" + standard + ")";
        }
    }

}
