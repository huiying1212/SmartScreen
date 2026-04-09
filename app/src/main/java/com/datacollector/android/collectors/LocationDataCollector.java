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
    /** 位置数据超过此时间视为过期（默认5分钟） */
    private static final long LOCATION_STALE_THRESHOLD_MS = 5 * 60_000L;
    /** 新位置精度差于旧位置多少米以上时拒绝替换（容忍度） */
    private static final float ACCURACY_TOLERANCE_METERS = 200f;

    private LocationManager locationManager;
    private volatile Location lastKnownLocation;

    // Geocoder 缓存：坐标变化 < GEOCODE_CACHE_RADIUS_M 时复用上次结果
    private static final double GEOCODE_CACHE_RADIUS_M = 80.0;
    private double cachedGeoLat = Double.NaN;
    private double cachedGeoLng = Double.NaN;
    private String cachedAddress = null;

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
            Log.w(TAG, "Failed to build configuration", e);
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

            // 从两个 provider 中选出质量最优的初始位置
            Location primary = locationManager.getLastKnownLocation(provider);
            if (isBetterLocation(primary, lastKnownLocation)) {
                lastKnownLocation = primary;
            }

            String altProvider = LocationManager.GPS_PROVIDER.equals(provider)
                    ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
            if (locationManager.isProviderEnabled(altProvider)) {
                Location alt = locationManager.getLastKnownLocation(altProvider);
                if (isBetterLocation(alt, lastKnownLocation)) {
                    lastKnownLocation = alt;
                }
                locationManager.requestLocationUpdates(altProvider, updateInterval, minDistance, this);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException requesting location updates", e);
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
                Log.e(TAG, "SecurityException removing location updates", e);
            }
        }
    }
    
    @Override
    protected JSONObject doCollectData() {
        if (lastKnownLocation == null) {
            return null;
        }

        // 检查位置时效性：超过阈值的数据标记为 stale
        long age = System.currentTimeMillis() - lastKnownLocation.getTime();
        boolean isStale = age > LOCATION_STALE_THRESHOLD_MS;

        // 坐标有效性检查：(0,0) 通常是未初始化
        double lat = lastKnownLocation.getLatitude();
        double lng = lastKnownLocation.getLongitude();
        if (lat == 0.0 && lng == 0.0) {
            Log.w("LocationCollector", "Skipping invalid (0,0) location");
            return null;
        }

        try {
            JSONObject locationData = new JSONObject();
            locationData.put("latitude", lat);
            locationData.put("longitude", lng);
            locationData.put("accuracy", lastKnownLocation.getAccuracy());
            locationData.put("altitude", lastKnownLocation.getAltitude());
            locationData.put("speed", lastKnownLocation.getSpeed());
            locationData.put("bearing", lastKnownLocation.getBearing());
            locationData.put("timestamp", lastKnownLocation.getTime());
            locationData.put("provider", lastKnownLocation.getProvider());
            locationData.put("is_stale", isStale);
            locationData.put("age_seconds", age / 1000);

            // 添加可读地址格式
            String readableLocation = getReadableLocation(lastKnownLocation);
            locationData.put("readable_address", readableLocation);

            return locationData;
        } catch (JSONException e) {
            Log.w(TAG, "Failed to build location data", e);
            return null;
        }
    }
    
    /**
     * 将 GPS 坐标转换为可读地址。带缓存——位置变化 < 80m 时复用上次结果，
     * 避免对 Geocoder 的频繁同步调用。
     */
    private String getReadableLocation(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        // 命中缓存：位置未显著移动
        if (cachedAddress != null && !Double.isNaN(cachedGeoLat)) {
            double dist = haversineMeters(lat, lng, cachedGeoLat, cachedGeoLng);
            if (dist < GEOCODE_CACHE_RADIUS_M) {
                return cachedAddress;
            }
        }

        if (!Geocoder.isPresent()) {
            return formatCoords(location);
        }
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            // Geocoder.getFromLocation(double,double,int) is synchronous and deprecated on API 33+.
            // The async overload requires a callback and cannot return inline, so we keep the
            // synchronous call (still functional) but wrap it in a try-catch for safety.
            @SuppressWarnings("deprecation")
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                String result = buildReadableAddress(addresses.get(0));
                cachedGeoLat = lat;
                cachedGeoLng = lng;
                cachedAddress = result;
                return result;
            }
        } catch (Exception e) {
            Log.w("LocationCollector", "Geocoder failed", e);
        }
        return formatCoords(location);
    }

    /** 简化版 Haversine，仅供缓存距离判断 */
    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
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
        if (location == null) return;
        if (isBetterLocation(location, lastKnownLocation)) {
            this.lastKnownLocation = location;
        }
    }

    /**
     * 综合评分判断新位置是否优于旧位置。
     * 对时效性、精度、provider 类型三个维度分别打分后加权比较，
     * 避免简单 if-else 在边界情况下做出不合理的决策。
     */
    private boolean isBetterLocation(Location newLoc, Location oldLoc) {
        if (newLoc == null) return false;
        if (oldLoc == null) return true;

        long timeDelta = newLoc.getTime() - oldLoc.getTime();

        // 快速路径：旧位置已严重过期 → 无条件接受新数据
        if (timeDelta > LOCATION_STALE_THRESHOLD_MS) return true;
        // 快速路径：新位置远比旧位置陈旧 → 拒绝
        if (timeDelta < -LOCATION_STALE_THRESHOLD_MS) return false;

        // ── 时效性得分 ──
        // timeDelta 映射到 [-1, 1]，越新分越高
        double timeScore = clamp(timeDelta / (double) LOCATION_STALE_THRESHOLD_MS, -1.0, 1.0);

        // ── 精度得分 ──
        // 精度差值（负 = 新更精确），归一化到 [-1, 1]
        float accDelta = newLoc.getAccuracy() - oldLoc.getAccuracy();
        double accScore = clamp(-accDelta / ACCURACY_TOLERANCE_METERS, -1.0, 1.0);

        // ── Provider 得分 ──
        // GPS 比 Network 更可信，同类型为 0
        double providerScore = providerRank(newLoc.getProvider()) - providerRank(oldLoc.getProvider());

        // 加权求和：时效 40%、精度 45%、provider 15%
        double score = timeScore * 0.40 + accScore * 0.45 + providerScore * 0.15;
        return score > 0;
    }

    /** provider 信任排名，GPS > Network > 其他 */
    private static double providerRank(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) return 1.0;
        if (LocationManager.NETWORK_PROVIDER.equals(provider)) return 0.5;
        return 0.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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