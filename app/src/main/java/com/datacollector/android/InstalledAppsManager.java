package com.datacollector.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 管理已安装应用列表的工具类
 * 用于在LLM请求中提供设备上可用的应用信息
 */
public class InstalledAppsManager {
    
    private static final String TAG = "InstalledAppsManager";
    private static InstalledAppsManager instance;
    
    private Context context;
    private List<AppInfo> installedApps;
    private String appsListJson;
    private long lastUpdateTime = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存
    
    /**
     * 应用信息类
     */
    public static class AppInfo {
        public String label;
        public String packageName;
        public String className;
        
        public AppInfo(String label, String packageName, String className) {
            this.label = label;
            this.packageName = packageName;
            this.className = className;
        }
    }
    
    private InstalledAppsManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized InstalledAppsManager getInstance(Context context) {
        if (instance == null) {
            instance = new InstalledAppsManager(context);
        }
        return instance;
    }
    
    /**
     * 获取已安装应用列表的JSON字符串
     * 供LLM请求使用
     */
    public String getInstalledAppsListJson() {
        updateAppsListIfNeeded();
        return appsListJson;
    }
    
    /**
     * 获取已安装应用列表
     */
    public List<AppInfo> getInstalledAppsList() {
        updateAppsListIfNeeded();
        return new ArrayList<>(installedApps);
    }
    
    /**
     * 强制刷新应用列表
     */
    public void refreshAppsList() {
        lastUpdateTime = 0; // 重置缓存时间
        updateAppsListIfNeeded();
    }
    
    /**
     * 如果需要则更新应用列表
     */
    private void updateAppsListIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (installedApps == null || currentTime - lastUpdateTime > CACHE_DURATION) {
            loadInstalledApps();
            generateAppsListJson();
            lastUpdateTime = currentTime;
            Log.d(TAG, "更新了应用列表，共找到 " + installedApps.size() + " 个应用");
        }
    }
    
    /**
     * 加载已安装的应用列表
     */
    private void loadInstalledApps() {
        PackageManager packageManager = context.getPackageManager();
        
        // 获取所有可启动的应用
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(mainIntent, 0);
        
        installedApps = new ArrayList<>();
        
        for (ResolveInfo resolveInfo : resolveInfos) {
            try {
                String label = resolveInfo.loadLabel(packageManager).toString();
                String packageName = resolveInfo.activityInfo.packageName;
                String className = resolveInfo.activityInfo.name;
                
                // 过滤掉系统设置等不需要显示的应用，但保留一些常用的系统应用
                if (!isSystemApp(resolveInfo.activityInfo.applicationInfo) || 
                    isWhitelistedSystemApp(packageName)) {
                    
                    AppInfo appInfo = new AppInfo(label, packageName, className);
                    installedApps.add(appInfo);
                }
            } catch (Exception e) {
                Log.w(TAG, "加载应用信息时出错: " + e.getMessage());
            }
        }
        
        // 按应用名称排序
        Collections.sort(installedApps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo app1, AppInfo app2) {
                return app1.label.compareToIgnoreCase(app2.label);
            }
        });
    }
    
    /**
     * 生成应用列表的JSON字符串
     */
    private void generateAppsListJson() {
        try {
            JSONObject appsJson = new JSONObject();
            appsJson.put("device_apps_count", installedApps.size());
            
            JSONArray appsArray = new JSONArray();
            for (AppInfo app : installedApps) {
                JSONObject appJson = new JSONObject();
                appJson.put("name", app.label);
                appJson.put("package", app.packageName);
                appsArray.put(appJson);
            }
            
            appsJson.put("installed_apps", appsArray);
            appsListJson = appsJson.toString();
            
            Log.d(TAG, "生成应用列表JSON完成，包含 " + installedApps.size() + " 个应用");
            
        } catch (JSONException e) {
            Log.e(TAG, "生成应用列表JSON时出错", e);
            // 如果JSON生成失败，使用简单的文本格式
            StringBuilder sb = new StringBuilder();
            sb.append("设备上已安装的应用(共").append(installedApps.size()).append("个):\n");
            for (AppInfo app : installedApps) {
                sb.append("- ").append(app.label).append(" (").append(app.packageName).append(")\n");
            }
            appsListJson = sb.toString();
            Log.d(TAG, "JSON生成失败，使用文本格式，包含 " + installedApps.size() + " 个应用");
        }
    }
    
    /**
     * 检查是否是系统应用
     */
    private boolean isSystemApp(ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }
    
    /**
     * 检查是否是白名单系统应用
     */
    private boolean isWhitelistedSystemApp(String packageName) {
        // 允许显示的系统应用白名单
        return packageName.equals("com.android.settings") ||
               packageName.equals("com.android.calculator2") ||
               packageName.equals("com.android.camera") ||
               packageName.equals("com.android.gallery3d") ||
               packageName.equals("com.android.music") ||
               packageName.equals("com.android.browser") ||
               packageName.equals("com.android.chrome") ||
               packageName.equals("com.android.contacts") ||
               packageName.equals("com.android.phone") ||
               packageName.equals("com.android.mms") ||
               packageName.equals("com.google.android.apps.photos") ||
               packageName.equals("com.whatsapp") ||
               packageName.equals("com.tencent.mm") ||
               packageName.equals("com.google.android.gm") ||
               packageName.equals("com.google.android.apps.maps") ||
               packageName.equals("com.spotify.music") ||
               packageName.equals("com.facebook.katana") ||
               packageName.equals("com.instagram.android") ||
               packageName.equals("com.twitter.android") ||
               packageName.equals("com.linkedin.android");
    }
} 