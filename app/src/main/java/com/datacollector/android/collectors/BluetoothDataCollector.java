package com.datacollector.android.collectors;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * 蓝牙设备数据收集器
 * 收集蓝牙设备信息
 */
public class BluetoothDataCollector extends BaseDataCollector<JSONArray> {
    
    private static final String COLLECTOR_ID = "bluetooth";
    
    private BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver bluetoothReceiver;
    
    public BluetoothDataCollector(Context context) {
        super(context, COLLECTOR_ID);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        initializeReceiver();
    }
    
    private void initializeReceiver() {
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action) ||
                    BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) ||
                    BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    // 蓝牙状态变化时可以触发数据收集
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
        
        // 检查蓝牙适配器是否可用
        if (bluetoothAdapter == null) {
            return false;
        }
        
        // 检查权限
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    @Override
    protected void doStartCollection() {
        if (!isAvailable()) {
            return;
        }
        
        // 注册蓝牙状态变化监听器
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        
        try {
            context.registerReceiver(bluetoothReceiver, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void doStopCollection() {
        if (bluetoothReceiver != null) {
            try {
                context.unregisterReceiver(bluetoothReceiver);
            } catch (Exception e) {
                // 可能已经注销过了
            }
        }
    }
    
    @Override
    protected JSONArray doCollectData() {
        JSONArray devices = new JSONArray();
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return devices; // 返回空数组
        }
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
            != PackageManager.PERMISSION_GRANTED) {
            return devices;
        }
        
        try {
            // 获取已配对的设备
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            
            for (BluetoothDevice device : pairedDevices) {
                try {
                    JSONObject deviceInfo = new JSONObject();
                    deviceInfo.put("name", device.getName());
                    deviceInfo.put("address", device.getAddress());
                    deviceInfo.put("type", getDeviceTypeString(device.getType()));
                    deviceInfo.put("bond_state", getBondStateString(device.getBondState()));
                    deviceInfo.put("device_class", device.getBluetoothClass().getDeviceClass());
                    
                    devices.put(deviceInfo);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        
        return devices;
    }
    
    /**
     * 获取设备类型字符串
     */
    private String getDeviceTypeString(int type) {
        switch (type) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                return "CLASSIC";
            case BluetoothDevice.DEVICE_TYPE_LE:
                return "LOW_ENERGY";
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                return "DUAL";
            default:
                return "UNKNOWN";
        }
    }
    
    /**
     * 获取绑定状态字符串
     */
    private String getBondStateString(int bondState) {
        switch (bondState) {
            case BluetoothDevice.BOND_BONDED:
                return "BONDED";
            case BluetoothDevice.BOND_BONDING:
                return "BONDING";
            case BluetoothDevice.BOND_NONE:
                return "NONE";
            default:
                return "UNKNOWN";
        }
    }
} 