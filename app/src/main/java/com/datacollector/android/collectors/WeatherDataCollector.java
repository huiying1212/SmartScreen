package com.datacollector.android.collectors;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.datacollector.android.utils.CollectionConfig;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 天气数据收集器
 * 通过 Open-Meteo 免费 API 根据设备 GPS 定位获取当前天气信息。
 * Open-Meteo 完全免费、无需 API Key、全球坐标精确。
 * GPS 不可用时跳过采集。
 *
 * 采集数据包括：温度、体感温度、湿度、天气描述、风速风向、
 * 云量、气压等。
 *
 * 内置缓存机制（默认 30 分钟），避免频繁请求。
 */
public class WeatherDataCollector extends BaseDataCollector<JSONObject> {

    private static final String COLLECTOR_ID = "weather";

    /**
     * Open-Meteo API — 完全免费，无需 Key，坐标定位精确。
     * 参数说明：
     *   latitude/longitude: GPS 坐标
     *   current: 请求的当前天气变量（温度、湿度、体感温度、天气代码、云量、风速、风向、气压）
     *   timezone=auto: 自动根据坐标推断时区
     */
    private static final String OPEN_METEO_API_URL =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=%s&longitude=%s"
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
            + "weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,surface_pressure"
            + "&timezone=auto";

    private final LocationManager locationManager;
    private final OkHttpClient httpClient;

    // 缓存最近一次成功获取的天气数据
    private JSONObject cachedWeatherData;
    private long cachedTimestamp = 0;

    public WeatherDataCollector(Context context) {
        super(context, COLLECTOR_ID);
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Override
    protected void initializeDefaultConfiguration() {
        super.initializeDefaultConfiguration();
        try {
            CollectionConfig config = CollectionConfig.getInstance(context);
            configuration.put("cache_duration_ms",
                    config.getLong(CollectionConfig.KEY_WEATHER_CACHE_DURATION_MS, 30 * 60_000L));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) return false;
        if (!CollectionConfig.getInstance(context)
                .getBoolean(CollectionConfig.KEY_WEATHER_ENABLED, true)) return false;
        // 需要位置权限来获取经纬度
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return locationManager != null;
    }

    @Override
    protected JSONObject doCollectData() {
        // 检查缓存是否仍然有效
        long cacheDuration = configuration.optLong("cache_duration_ms", 30 * 60_000L);
        if (cachedWeatherData != null
                && (System.currentTimeMillis() - cachedTimestamp) < cacheDuration) {
            Log.d(TAG, "Returning cached weather data (age="
                    + (System.currentTimeMillis() - cachedTimestamp) / 1000 + "s)");
            return cachedWeatherData;
        }

        // 获取当前位置
        Location location = getLastKnownLocation();
        if (location == null) {
            Log.w(TAG, "No location available, skipping weather collection");
            return cachedWeatherData; // 返回旧缓存（可能为 null）
        }

        // 请求天气 API
        try {
            // 坐标模糊化：截断到小数点后 2 位（约 1.1km 精度），
            // 天气数据在此精度下完全一致，避免向第三方泄露精确位置
            double fuzzyLat = Math.floor(location.getLatitude() * 100.0) / 100.0;
            double fuzzyLng = Math.floor(location.getLongitude() * 100.0) / 100.0;

            // 使用 Locale.US 确保小数点格式一致（不会因中文 locale 变成逗号）
            String url = String.format(Locale.US, OPEN_METEO_API_URL,
                    fuzzyLat, fuzzyLng);
            Log.d(TAG, "Fetching weather from Open-Meteo: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "RI4SU-Android")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "Weather API returned HTTP " + response.code());
                    return cachedWeatherData;
                }

                String body = response.body() != null ? response.body().string() : "";
                JSONObject rawData = new JSONObject(body);
                JSONObject weatherData = parseOpenMeteoResponse(rawData);

                if (weatherData != null) {
                    cachedWeatherData = weatherData;
                    cachedTimestamp = System.currentTimeMillis();
                    Log.i(TAG, "Weather data updated: "
                            + weatherData.optString("readable_summary"));
                }

                return weatherData;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching weather data", e);
            return cachedWeatherData;
        }
    }

    /**
     * 解析 Open-Meteo JSON 响应，提取关键天气信息。
     *
     * Open-Meteo 返回结构：
     * {
     *   "latitude": 39.875,
     *   "longitude": 116.375,
     *   "timezone": "Asia/Shanghai",
     *   "current": {
     *     "time": "2026-03-31T13:30",
     *     "temperature_2m": 17.5,
     *     "relative_humidity_2m": 38,
     *     "apparent_temperature": 16.3,
     *     "weather_code": 1,
     *     "cloud_cover": 10,
     *     "wind_speed_10m": 6.8,
     *     "wind_direction_10m": 205,
     *     "surface_pressure": 1005.8
     *   }
     * }
     */
    private JSONObject parseOpenMeteoResponse(JSONObject raw) {
        try {
            JSONObject current = raw.optJSONObject("current");
            if (current == null) {
                Log.w(TAG, "No 'current' field in Open-Meteo response");
                return null;
            }

            JSONObject weatherData = new JSONObject();

            double tempC = current.optDouble("temperature_2m", Double.NaN);
            double feelsLikeC = current.optDouble("apparent_temperature", Double.NaN);
            int humidity = current.optInt("relative_humidity_2m", 0);
            int weatherCode = current.optInt("weather_code", -1);

            int tempRound = Double.isNaN(tempC) ? 0 : (int) Math.round(tempC);
            int feelsRound = Double.isNaN(feelsLikeC) ? 0 : (int) Math.round(feelsLikeC);

            weatherData.put("temperature_c", tempRound);
            weatherData.put("feels_like_c", feelsRound);
            weatherData.put("humidity", humidity);

            // 将 WMO 天气代码转换为中文描述
            String weatherDesc = wmoCodeToDescription(weatherCode);
            weatherData.put("weather_desc", weatherDesc);
            weatherData.put("weather_code", weatherCode);

            double windSpeed = current.optDouble("wind_speed_10m", 0);
            int windDir = current.optInt("wind_direction_10m", 0);
            weatherData.put("wind_speed_kmph", (int) Math.round(windSpeed));
            weatherData.put("wind_dir", degreesToDirection(windDir));
            weatherData.put("wind_dir_degree", windDir);

            weatherData.put("cloud_cover", current.optInt("cloud_cover", 0));
            weatherData.put("pressure_mb", (int) Math.round(
                    current.optDouble("surface_pressure", 0)));

            weatherData.put("observation_time", current.optString("time", ""));
            weatherData.put("timezone", raw.optString("timezone", ""));
            weatherData.put("timestamp", System.currentTimeMillis());

            // 生成人类可读摘要
            StringBuilder summary = new StringBuilder();
            summary.append(weatherDesc);
            summary.append(" ").append(tempRound).append("°C");
            if (Math.abs(feelsRound - tempRound) >= 3) {
                summary.append(" 体感").append(feelsRound).append("°C");
            }
            summary.append(" 湿度").append(humidity).append("%");
            weatherData.put("readable_summary", summary.toString());

            return weatherData;

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Open-Meteo response", e);
            return null;
        }
    }

    /**
     * 将 WMO 天气代码转换为中文天气描述。
     * 参考：https://open-meteo.com/en/docs#weathervariables
     */
    private String wmoCodeToDescription(int code) {
        switch (code) {
            case 0: return "晴";
            case 1: return "大部晴朗";
            case 2: return "多云";
            case 3: return "阴天";
            case 45: return "雾";
            case 48: return "雾凇";
            case 51: return "小毛毛雨";
            case 53: return "中毛毛雨";
            case 55: return "大毛毛雨";
            case 56: return "冻毛毛雨";
            case 57: return "强冻毛毛雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 66: return "冻雨";
            case 67: return "强冻雨";
            case 71: return "小雪";
            case 73: return "中雪";
            case 75: return "大雪";
            case 77: return "冰粒";
            case 80: return "小阵雨";
            case 81: return "中阵雨";
            case 82: return "强阵雨";
            case 85: return "小阵雪";
            case 86: return "大阵雪";
            case 95: return "雷暴";
            case 96: return "雷暴伴小冰雹";
            case 99: return "雷暴伴大冰雹";
            default: return "未知";
        }
    }

    /**
     * 将风向角度（0-360°）转换为中文方位。
     */
    private String degreesToDirection(int degrees) {
        // 标准化到 0-360
        degrees = ((degrees % 360) + 360) % 360;
        String[] directions = {
            "北", "北东北", "东北", "东东北",
            "东", "东东南", "东南", "南东南",
            "南", "南西南", "西南", "西西南",
            "西", "西西北", "西北", "北西北"
        };
        int index = (int) Math.round(degrees / 22.5) % 16;
        return directions[index];
    }

    /**
     * 获取设备最近已知的位置，优先使用 GPS，回退到 Network provider。
     */
    private Location getLastKnownLocation() {
        try {
            Location gpsLocation = null;
            Location networkLocation = null;

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            // 返回更新鲜的那个
            if (gpsLocation != null && networkLocation != null) {
                return gpsLocation.getTime() > networkLocation.getTime()
                        ? gpsLocation : networkLocation;
            }
            return gpsLocation != null ? gpsLocation : networkLocation;

        } catch (SecurityException e) {
            Log.w(TAG, "No location permission", e);
            return null;
        }
    }

    /**
     * 获取缓存的天气数据（供其他组件快速读取，不触发网络请求）。
     */
    public JSONObject getCachedWeatherData() {
        return cachedWeatherData;
    }

    /**
     * 获取天气的简短可读摘要（供悬浮窗气泡使用）。
     */
    public String getReadableSummary() {
        if (cachedWeatherData == null) return null;
        return cachedWeatherData.optString("readable_summary", null);
    }
}
