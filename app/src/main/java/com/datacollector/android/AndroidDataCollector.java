package com.datacollector.android;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.database.Cursor;
import android.content.ContentResolver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Bitmap;
import android.view.View;
import androidx.core.app.ActivityCompat;

import com.datacollector.android.ScreenContentData;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 安卓用户行为数据收集器
 * 基于CATIA论文实现的上下文感知数据收集系统
 * 收集时间、位置、活动、蓝牙/WiFi连接、日历事件、屏幕内容等信息
 */
public class AndroidDataCollector extends Activity implements SensorEventListener, LocationListener {
    
    private static final String TAG = "AndroidDataCollector";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final int CALENDAR_PERMISSION_REQUEST = 1002;
    private static final int PHONE_PERMISSION_REQUEST = 1003;
    
    // 传感器和位置管理器
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;
    private ActivityManager activityManager;
    
    // 数据存储
    private Queue<ScreenContentData> screenContentQueue;
    private JSONObject currentContextData;
    private Timer dataCollectionTimer;
    
    // 活动识别
    private ActivityRecognizer activityRecognizer;
    
    // 屏幕内容收集
    private ScreenContentCollector screenCollector;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        initializeComponents();
        requestPermissions();
        startDataCollection();
    }
    
    /**
     * 初始化各种组件和服务
     */
    private void initializeComponents() {
        // 初始化传感器管理器
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        
        // 初始化数据结构
        screenContentQueue = new ConcurrentLinkedQueue<>();
        currentContextData = new JSONObject();
        
        // 初始化活动识别器
        activityRecognizer = new ActivityRecognizer(this);
        
        // 初始化屏幕内容收集器
        screenCollector = new ScreenContentCollector(this);
    }
    
    /**
     * 请求必要的权限
     */
    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        };
        
        ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST);
    }
    
    /**
     * 开始数据收集
     */
    private void startDataCollection() {
        // 注册传感器监听器
        registerSensorListeners();
        
        // 注册位置监听器
        registerLocationListener();
        
        // 注册蓝牙和WiFi监听器
        registerConnectivityListeners();
        
        // 启动定时数据收集
        startPeriodicDataCollection();
        
        // 启动屏幕内容收集
        screenCollector.startCollection();
    }
    
    /**
     * 注册传感器监听器
     */
    private void registerSensorListeners() {
        // 加速度计用于活动识别
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        
        // 陀螺仪
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }
    
    /**
     * 注册位置监听器
     */
    private void registerLocationListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 
                60000, // 1分钟更新一次
                10,    // 10米距离变化
                this
            );
        }
    }
    
    /**
     * 注册连接性监听器（WiFi和蓝牙）
     */
    private void registerConnectivityListeners() {
        // WiFi状态变化监听器
        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(wifiReceiver, wifiFilter);
        
        // 蓝牙状态变化监听器
        IntentFilter bluetoothFilter = new IntentFilter();
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        bluetoothFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        bluetoothFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, bluetoothFilter);
    }
    
    /**
     * 启动周期性数据收集
     */
    private void startPeriodicDataCollection() {
        dataCollectionTimer = new Timer();
        dataCollectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                collectCurrentContextData();
            }
        }, 0, 30000); // 每30秒收集一次完整的上下文数据
    }
    
    /**
     * 收集当前上下文数据
     */
    private void collectCurrentContextData() {
        try {
            JSONObject contextData = new JSONObject();
            
            // 时间信息
            contextData.put("timestamp", System.currentTimeMillis());
            contextData.put("date_time", getCurrentDateTime());
            contextData.put("day_of_week", getCurrentDayOfWeek());
            
            // 位置信息
            JSONObject locationData = getLocationData();
            if (locationData != null) {
                contextData.put("location", locationData);
            }
            
            // 活动信息
            contextData.put("activity", activityRecognizer.getCurrentActivity());
            
            // 连接设备信息
            contextData.put("bluetooth_devices", getBluetoothDevices());
            contextData.put("wifi_info", getWifiInfo());
            
            // 日历事件
            contextData.put("calendar_events", getCalendarEvents());
            
            // 屏幕内容
            contextData.put("screen_content", getScreenContent());
            
            // 当前应用信息
            contextData.put("current_app", getCurrentAppInfo());
            
            // 保存数据
            saveContextData(contextData);
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 获取当前日期时间
     */
    private String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    /**
     * 获取当前星期几
     */
    private String getCurrentDayOfWeek() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    /**
     * 获取位置数据
     */
    private JSONObject getLocationData() {
        try {
            JSONObject locationData = new JSONObject();
            
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
                
                Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation != null) {
                    locationData.put("latitude", lastKnownLocation.getLatitude());
                    locationData.put("longitude", lastKnownLocation.getLongitude());
                    locationData.put("accuracy", lastKnownLocation.getAccuracy());
                    locationData.put("altitude", lastKnownLocation.getAltitude());
                    
                    // 这里可以添加地理编码将坐标转换为可读地址
                    String readableLocation = getReadableLocation(lastKnownLocation);
                    locationData.put("readable_address", readableLocation);
                }
            }
            
            return locationData;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 将GPS坐标转换为可读地址
     */
    private String getReadableLocation(Location location) {
        // 这里应该使用Geocoder或其他地理编码服务
        // 为简化示例，返回坐标字符串
        return String.format("%.6f, %.6f", location.getLatitude(), location.getLongitude());
    }
    
    /**
     * 获取蓝牙设备信息
     */
    private JSONArray getBluetoothDevices() {
        JSONArray bluetoothDevices = new JSONArray();
        
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) 
                == PackageManager.PERMISSION_GRANTED) {
                
                Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
                for (BluetoothDevice device : bondedDevices) {
                    try {
                        JSONObject deviceInfo = new JSONObject();
                        deviceInfo.put("name", device.getName());
                        deviceInfo.put("address", device.getAddress());
                        deviceInfo.put("type", device.getType());
                        deviceInfo.put("bond_state", device.getBondState());
                        bluetoothDevices.put(deviceInfo);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        return bluetoothDevices;
    }
    
    /**
     * 获取WiFi信息
     */
    private JSONObject getWifiInfo() {
        try {
            JSONObject wifiInfo = new JSONObject();
            
            if (wifiManager != null) {
                WifiInfo connectionInfo = wifiManager.getConnectionInfo();
                if (connectionInfo != null) {
                    wifiInfo.put("ssid", connectionInfo.getSSID());
                    wifiInfo.put("bssid", connectionInfo.getBSSID());
                    wifiInfo.put("rssi", connectionInfo.getRssi());
                    wifiInfo.put("link_speed", connectionInfo.getLinkSpeed());
                    wifiInfo.put("frequency", connectionInfo.getFrequency());
                }
            }
            
            return wifiInfo;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 获取日历事件
     */
    private JSONArray getCalendarEvents() {
        JSONArray events = new JSONArray();
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) 
            == PackageManager.PERMISSION_GRANTED) {
            
            ContentResolver contentResolver = getContentResolver();
            
            // 获取过去3个和未来3个事件
            long currentTime = System.currentTimeMillis();
            long pastTime = currentTime - (3 * 24 * 60 * 60 * 1000); // 3天前
            long futureTime = currentTime + (3 * 24 * 60 * 60 * 1000); // 3天后
            
            String[] projection = {
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION
            };
            
            String selection = CalendarContract.Events.DTSTART + " >= ? AND " + 
                             CalendarContract.Events.DTSTART + " <= ?";
            String[] selectionArgs = {String.valueOf(pastTime), String.valueOf(futureTime)};
            
            Cursor cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                CalendarContract.Events.DTSTART + " ASC"
            );
            
            if (cursor != null) {
                while (cursor.moveToNext() && events.length() < 6) {
                    try {
                        JSONObject event = new JSONObject();
                        event.put("title", cursor.getString(0));
                        event.put("start_time", cursor.getLong(1));
                        event.put("end_time", cursor.getLong(2));
                        event.put("description", cursor.getString(3));
                        event.put("location", cursor.getString(4));
                        events.put(event);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                cursor.close();
            }
        }
        
        return events;
    }
    
    /**
     * 获取屏幕内容
     */
    private JSONArray getScreenContent() {
        return screenCollector.getRecentScreenContent();
    }
    
    /**
     * 获取当前应用信息
     */
    private JSONObject getCurrentAppInfo() {
        try {
            JSONObject appInfo = new JSONObject();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<ActivityManager.RunningAppProcessInfo> runningApps = 
                    activityManager.getRunningAppProcesses();
                
                if (runningApps != null && !runningApps.isEmpty()) {
                    ActivityManager.RunningAppProcessInfo frontApp = runningApps.get(0);
                    appInfo.put("package_name", frontApp.processName);
                    appInfo.put("importance", frontApp.importance);
                }
            }
            
            return appInfo;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 保存上下文数据到JSON文件
     */
    private void saveContextData(JSONObject contextData) {
        try {
            // 创建输出JSON对象
            JSONObject outputData = new JSONObject();
            outputData.put("context_data", contextData);
            outputData.put("collection_time", System.currentTimeMillis());
            
            // 保存到文件
            String fileName = "context_data_" + System.currentTimeMillis() + ".json";
            FileWriter fileWriter = new FileWriter(getExternalFilesDir(null) + "/" + fileName);
            fileWriter.write(outputData.toString(4)); // 4 spaces for pretty printing
            fileWriter.close();
            
            // 同时保存到实时数据结构
            this.currentContextData = outputData;
            
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 获取完整的上下文数据（外部调用接口）
     */
    public JSONObject getCompleteContextData() {
        collectCurrentContextData();
        return currentContextData;
    }
    
    // WiFi状态广播接收器
    private BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // WiFi状态变化时的处理
            collectCurrentContextData();
        }
    };
    
    // 蓝牙状态广播接收器
    private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 蓝牙状态变化时的处理
            collectCurrentContextData();
        }
    };
    
    // 传感器事件监听器实现
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (activityRecognizer != null) {
            activityRecognizer.processSensorData(event);
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 传感器精度变化处理
    }
    
    // 位置监听器实现
    @Override
    public void onLocationChanged(Location location) {
        // 位置变化时更新上下文数据
        collectCurrentContextData();
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    @Override
    public void onProviderEnabled(String provider) {}
    
    @Override
    public void onProviderDisabled(String provider) {}
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 清理资源
        if (dataCollectionTimer != null) {
            dataCollectionTimer.cancel();
        }
        
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        
        try {
            unregisterReceiver(wifiReceiver);
            unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {
            // 接收器可能未注册
        }
        
        if (screenCollector != null) {
            screenCollector.stopCollection();
        }
    }
} 