package com.datacollector.android.collectors;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.datacollector.android.utils.CollectionConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 蓝牙数据收集器。
 *
 * 收集两类信息：
 *   1. 已配对设备列表（名称、MAC、设备类型、是否已连接）
 *   2. 通过 BroadcastReceiver 被动监听 ACTION_FOUND 得到的周边发现设备
 *
 * 蓝牙主动扫描耗电高且在 Android 12+ 需要 BLUETOOTH_SCAN 权限，
 * 因此周边扫描仅在 doStartCollection 时触发一次，后续靠被动广播更新缓存。
 * 每次 doCollectData 返回当前缓存快照，不重复发起扫描。
 */
public class BluetoothDataCollector extends BaseDataCollector<JSONObject> {

    private static final String COLLECTOR_ID = "bluetooth_devices";
    private static final int MAX_NEARBY_DEVICES = 20;

    private BluetoothAdapter bluetoothAdapter;
    private final List<JSONObject> nearbyDeviceCache = new ArrayList<>();

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) return;

                // Android 12+ 需要 BLUETOOTH_CONNECT 才能读 name
                boolean hasConnectPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || ActivityCompat.checkSelfPermission(ctx,
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

                try {
                    JSONObject entry = new JSONObject();
                    entry.put("mac_address", device.getAddress());
                    entry.put("name", hasConnectPerm ? safeDeviceName(device) : "unknown");
                    entry.put("device_class", classifyDevice(device.getBluetoothClass()));
                    entry.put("rssi", intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE));
                    entry.put("bond_state", bondStateName(device.getBondState()));

                    synchronized (nearbyDeviceCache) {
                        boolean found = false;
                        for (int i = 0; i < nearbyDeviceCache.size(); i++) {
                            if (device.getAddress().equals(nearbyDeviceCache.get(i).optString("mac_address"))) {
                                nearbyDeviceCache.set(i, entry);
                                found = true;
                                break;
                            }
                        }
                        if (!found && nearbyDeviceCache.size() < MAX_NEARBY_DEVICES) {
                            nearbyDeviceCache.add(entry);
                        }
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse BT device", e);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // 扫描结束，不再自动重启，等下一个采集周期的 doStartCollection
            }
        }
    };

    public BluetoothDataCollector(Context context) {
        super(context, COLLECTOR_ID);
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) {
            bluetoothAdapter = bm.getAdapter();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) return false;
        if (!CollectionConfig.getInstance(context)
                .getBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, true)) return false;
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) return false;
        // Android 12+ 需要 BLUETOOTH_SCAN；低版本只需 ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void doStartCollection() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(discoveryReceiver, filter);

        // 触发一次扫描（需要 BLUETOOTH_SCAN on Android 12+）
        if (bluetoothAdapter != null && !bluetoothAdapter.isDiscovering()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.startDiscovery();
                }
            } else {
                bluetoothAdapter.startDiscovery();
            }
        }
    }

    @Override
    protected void doStopCollection() {
        try {
            context.unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (bluetoothAdapter != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.cancelDiscovery();
                }
            } else {
                bluetoothAdapter.cancelDiscovery();
            }
        }
    }

    @Override
    protected JSONObject doCollectData() {
        try {
            JSONObject result = new JSONObject();

            // ── 已配对设备 ────────────────────────────────────
            boolean hasConnectPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    || ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

            JSONArray pairedArray = new JSONArray();
            if (hasConnectPerm) {
                Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
                if (paired != null) {
                    for (BluetoothDevice device : paired) {
                        JSONObject d = new JSONObject();
                        d.put("name", safeDeviceName(device));
                        d.put("mac_address", device.getAddress());
                        d.put("device_class", classifyDevice(device.getBluetoothClass()));
                        d.put("bond_state", bondStateName(device.getBondState()));
                        pairedArray.put(d);
                    }
                }
            }
            result.put("paired_devices", pairedArray);
            result.put("paired_device_count", pairedArray.length());

            // ── 周边扫描设备（缓存快照）────────────────────────
            JSONArray nearbyArray = new JSONArray();
            synchronized (nearbyDeviceCache) {
                for (JSONObject d : nearbyDeviceCache) {
                    nearbyArray.put(d);
                }
            }
            result.put("nearby_devices", nearbyArray);
            result.put("nearby_device_count", nearbyArray.length());

            result.put("is_discovering", bluetoothAdapter.isDiscovering());
            result.put("collector_id", COLLECTOR_ID);
            result.put("data_collection_time", System.currentTimeMillis());
            return result;

        } catch (JSONException e) {
            Log.w(TAG, "Failed to build BT data", e);
            return null;
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name != null ? name : "unnamed";
        } catch (SecurityException e) {
            return "unknown";
        }
    }

    private String classifyDevice(BluetoothClass btClass) {
        if (btClass == null) return "unknown";
        switch (btClass.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO:  return "audio_video";
            case BluetoothClass.Device.Major.COMPUTER:     return "computer";
            case BluetoothClass.Device.Major.PHONE:        return "phone";
            case BluetoothClass.Device.Major.HEALTH:       return "health";
            case BluetoothClass.Device.Major.WEARABLE:     return "wearable";
            case BluetoothClass.Device.Major.PERIPHERAL:   return "peripheral";
            case BluetoothClass.Device.Major.IMAGING:      return "imaging";
            case BluetoothClass.Device.Major.NETWORKING:   return "networking";
            default:                                        return "other";
        }
    }

    private String bondStateName(int state) {
        switch (state) {
            case BluetoothDevice.BOND_BONDED:  return "bonded";
            case BluetoothDevice.BOND_BONDING: return "bonding";
            default:                           return "none";
        }
    }
}
