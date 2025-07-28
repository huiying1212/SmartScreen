package com.datacollector.android.collectors;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * 位置数据收集器
 * 收集GPS位置信息
 */
public class LocationDataCollector extends BaseDataCollector<JSONObject> implements LocationListener {
    
    private static final String COLLECTOR_ID = "location";
    
    private LocationManager locationManager;
    private Location lastKnownLocation;
    
    public LocationDataCollector(Context context) {
        super(context, COLLECTOR_ID);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
    
    @Override
    protected void initializeDefaultConfiguration() {
        super.initializeDefaultConfiguration();
        try {
            configuration.put("update_interval", 60000); // 1分钟
            configuration.put("min_distance", 10); // 10米
            configuration.put("provider", LocationManager.GPS_PROVIDER);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public boolean isAvailable() {
        if (!isEnabled()) {
            return false;
        }
        
        // 检查权限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        
        // 检查LocationManager和GPS提供者
        return locationManager != null && 
               locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
    
    @Override
    protected void doStartCollection() {
        if (!isAvailable()) {
            return;
        }
        
        try {
            long updateInterval = configuration.optLong("update_interval", 60000);
            float minDistance = (float) configuration.optDouble("min_distance", 10.0);
            String provider = configuration.optString("provider", LocationManager.GPS_PROVIDER);
            
            locationManager.requestLocationUpdates(provider, updateInterval, minDistance, this);
            
            // 获取最后已知位置
            lastKnownLocation = locationManager.getLastKnownLocation(provider);
            
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void doStopCollection() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    protected JSONObject doCollectData() {
        if (lastKnownLocation == null) {
            return null;
        }
        
        try {
            JSONObject locationData = new JSONObject();
            locationData.put("latitude", lastKnownLocation.getLatitude());
            locationData.put("longitude", lastKnownLocation.getLongitude());
            locationData.put("accuracy", lastKnownLocation.getAccuracy());
            locationData.put("altitude", lastKnownLocation.getAltitude());
            locationData.put("speed", lastKnownLocation.getSpeed());
            locationData.put("bearing", lastKnownLocation.getBearing());
            locationData.put("timestamp", lastKnownLocation.getTime());
            locationData.put("provider", lastKnownLocation.getProvider());
            
            // 添加可读地址格式
            String readableLocation = getReadableLocation(lastKnownLocation);
            locationData.put("readable_address", readableLocation);
            
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
        return String.format(Locale.getDefault(), "%.6f, %.6f", 
                           location.getLatitude(), location.getLongitude());
    }
    
    // LocationListener 接口实现
    @Override
    public void onLocationChanged(Location location) {
        this.lastKnownLocation = location;
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // 可以在这里处理提供者状态变化
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        // 提供者启用时的处理
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        // 提供者禁用时的处理
    }
} 