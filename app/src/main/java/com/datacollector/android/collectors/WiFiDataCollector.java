package com.datacollector.android.collectors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * WiFi信息数据收集器
 * 收集WiFi连接信息
 */
public class WiFiDataCollector extends BaseDataCollector<JSONObject> {
    
    private static final String COLLECTOR_ID = "wifi";
    
    private WifiManager wifiManager;
    private BroadcastReceiver wifiReceiver;
    
    public WiFiDataCollector(Context context) {
        super(context, COLLECTOR_ID);
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        initializeReceiver();
    }
    
    private void initializeReceiver() {
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action) ||
                    WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                    // WiFi状态变化时可以触发数据收集
                    collectData();
                }
            }
        };
    }
    
    @Override
    public boolean isAvailable() {
        if (!isEnabled()) {
            return false;
        }
        
        return wifiManager != null;
    }
    
    @Override
    protected void doStartCollection() {
        if (!isAvailable()) {
            return;
        }
        
        // 注册WiFi状态变化监听器
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        
        try {
            context.registerReceiver(wifiReceiver, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void doStopCollection() {
        if (wifiReceiver != null) {
            try {
                context.unregisterReceiver(wifiReceiver);
            } catch (Exception e) {
                // 可能已经注销过了
            }
        }
    }
    
    @Override
    protected JSONObject doCollectData() {
        if (wifiManager == null) {
            return null;
        }
        
        try {
            JSONObject wifiData = new JSONObject();
            
            // WiFi基本状态
            wifiData.put("wifi_enabled", wifiManager.isWifiEnabled());
            wifiData.put("wifi_state", getWifiStateString(wifiManager.getWifiState()));
            
            // 如果WiFi已启用且已连接，获取连接信息
            if (wifiManager.isWifiEnabled()) {
                WifiInfo connectionInfo = wifiManager.getConnectionInfo();
                if (connectionInfo != null) {
                    JSONObject connectionData = new JSONObject();
                    connectionData.put("ssid", connectionInfo.getSSID());
                    connectionData.put("bssid", connectionInfo.getBSSID());
                    connectionData.put("rssi", connectionInfo.getRssi());
                    connectionData.put("link_speed", connectionInfo.getLinkSpeed());
                    connectionData.put("frequency", connectionInfo.getFrequency());
                    connectionData.put("network_id", connectionInfo.getNetworkId());
                    connectionData.put("ip_address", formatIpAddress(connectionInfo.getIpAddress()));
                    connectionData.put("mac_address", connectionInfo.getMacAddress());
                    
                    wifiData.put("connection_info", connectionData);
                }
            }
            
            return wifiData;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 获取WiFi状态字符串
     */
    private String getWifiStateString(int wifiState) {
        switch (wifiState) {
            case WifiManager.WIFI_STATE_DISABLED:
                return "DISABLED";
            case WifiManager.WIFI_STATE_DISABLING:
                return "DISABLING";
            case WifiManager.WIFI_STATE_ENABLED:
                return "ENABLED";
            case WifiManager.WIFI_STATE_ENABLING:
                return "ENABLING";
            case WifiManager.WIFI_STATE_UNKNOWN:
                return "UNKNOWN";
            default:
                return "UNKNOWN";
        }
    }
    
    /**
     * 格式化IP地址
     */
    private String formatIpAddress(int ipAddress) {
        return String.format("%d.%d.%d.%d", 
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff),
                (ipAddress >> 24 & 0xff));
    }
} 