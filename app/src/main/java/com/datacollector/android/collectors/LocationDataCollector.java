package com.datacollector.android.collectors;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.datacollector.android.utils.CollectionConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * 位置数据收集器
 * 采集参数通过CollectionConfig动态配置（借鉴Beiwe的device_settings远程配置模式）
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
            CollectionConfig config = CollectionConfig.getInstance(context);
            configuration.put("update_interval",
                    config.getLong(CollectionConfig.KEY_LOCATION_INTERVAL, 60000));
            configuration.put("min_distance",
                    config.getFloat(CollectionConfig.KEY_LOCATION_MIN_DISTANCE, 10f));
            configuration.put("provider", LocationManager.GPS_PROVIDER);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public boolean isAvailable() {
        if (!isEnabled()) return false;
        if (!CollectionConfig.getInstance(context).getBoolean(CollectionConfig.KEY_LOCATION_ENABLED, true))
            return false;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false;
        if (locationManager == null) return false;
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
    
    @Override
    protected void doStartCollection() {
        if (!isAvailable()) {
            return;
        }
        
        try {
            long updateInterval = configuration.optLong("update_interval", 60000);
            float minDistance = (float) configuration.optDouble("min_distance", 10.0);
            String provider = resolveProvider();
            
            locationManager.requestLocationUpdates(provider, updateInterval, minDistance, this);
            
            lastKnownLocation = locationManager.getLastKnownLocation(provider);

            // 同时尝试从备用 provider 获取更新鲜的位置
            String altProvider = LocationManager.GPS_PROVIDER.equals(provider)
                    ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
            if (locationManager.isProviderEnabled(altProvider)) {
                Location alt = locationManager.getLastKnownLocation(altProvider);
                if (alt != null && (lastKnownLocation == null
                        || alt.getTime() > lastKnownLocation.getTime())) {
                    lastKnownLocation = alt;
                }
                locationManager.requestLocationUpdates(altProvider, updateInterval, minDistance, this);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
    
    private String resolveProvider() {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        return LocationManager.GPS_PROVIDER;
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
     * 将 GPS 坐标转换为可读地址。优先返回 POI/地标名称+道路，
     * 回退到区/街道，最差情况返回坐标字符串。
     */
    private String getReadableLocation(Location location) {
        if (!Geocoder.isPresent()) {
            return formatCoords(location);
        }
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                return buildReadableAddress(addr);
            }
        } catch (Exception e) {
            Log.w("LocationCollector", "Geocoder failed", e);
        }
        return formatCoords(location);
    }

    private String buildReadableAddress(Address addr) {
        StringBuilder sb = new StringBuilder();

        String featureName = addr.getFeatureName();
        String thoroughfare = addr.getThoroughfare();
        String subLocality = addr.getSubLocality();
        String locality = addr.getLocality();

        if (featureName != null && !featureName.isEmpty()
                && !isNumericOnly(featureName)
                && !featureName.equals(thoroughfare)) {
            sb.append(featureName);
        }

        if (thoroughfare != null && !thoroughfare.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(thoroughfare);
        } else if (subLocality != null && !subLocality.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(subLocality);
        }

        if (locality != null && !locality.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(locality);
        }

        return sb.length() > 0 ? sb.toString() : addr.getAddressLine(0);
    }

    private boolean isNumericOnly(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '-' && c != '.' && !Character.isDigit(c)) return false;
        }
        return true;
    }

    private String formatCoords(Location location) {
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