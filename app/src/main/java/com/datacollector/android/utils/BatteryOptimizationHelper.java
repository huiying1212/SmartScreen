package com.datacollector.android.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

/**
 * 电池优化辅助类
 * 帮助应用获得电池优化豁免，确保后台正常运行
 */
public class BatteryOptimizationHelper {
    
    private static final String TAG = "BatteryOptimizationHelper";
    private static final int REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1000;
    
    /**
     * 检查是否已经豁免电池优化
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true; // 低版本Android默认允许
    }
    
    /**
     * 显示对话框解释为什么需要电池优化豁免
     */
    public static void showBatteryOptimizationDialog(Activity activity) {
        new AlertDialog.Builder(activity)
            .setTitle("需要电池优化豁免")
            .setMessage("为了确保CATIA3能够持续在后台收集数据，请允许应用不受电池优化限制。\n\n" +
                       "这不会显著影响您的电池续航，但能确保launcher功能正常运行。")
            .setPositiveButton("去设置", (dialog, which) -> requestIgnoreBatteryOptimizations(activity))
            .setNegativeButton("暂不设置", (dialog, which) -> {
                Log.w(TAG, "User declined battery optimization exemption");
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * 请求电池优化豁免
     */
    @SuppressLint("BatteryLife")
    public static void requestIgnoreBatteryOptimizations(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            } catch (Exception e) {
                Log.e(TAG, "Failed to request battery optimization exemption", e);
                // 如果直接请求失败，打开电池优化设置页面
                openBatteryOptimizationSettings(activity);
            }
        }
    }
    
    /**
     * 打开系统电池优化设置页面
     */
    public static void openBatteryOptimizationSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open battery optimization settings", e);
            // 作为后备方案，打开应用详情页面
            openAppDetailsSettings(activity);
        }
    }
    
    /**
     * 打开应用详情设置页面（后备方案）
     */
    public static void openAppDetailsSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open app details settings", e);
        }
    }
    
    /**
     * 检查并请求电池优化豁免（如果需要）
     */
    public static void checkAndRequestBatteryOptimization(Activity activity) {
        if (!isIgnoringBatteryOptimizations(activity)) {
            Log.i(TAG, "App is not ignoring battery optimizations, requesting exemption");
            showBatteryOptimizationDialog(activity);
        } else {
            Log.i(TAG, "App is already ignoring battery optimizations");
            // 即使已经豁免，也检查其他可能的限制
            checkAdditionalRestrictions(activity);
        }
    }
    
    /**
     * 检查其他可能的后台限制
     */
    private static void checkAdditionalRestrictions(Activity activity) {
        // 检查自启动管理（主要针对国产ROM）
        if (Build.MANUFACTURER.toLowerCase().contains("xiaomi") ||
            Build.MANUFACTURER.toLowerCase().contains("huawei") ||
            Build.MANUFACTURER.toLowerCase().contains("oppo") ||
            Build.MANUFACTURER.toLowerCase().contains("vivo") ||
            Build.MANUFACTURER.toLowerCase().contains("meizu")) {
            
            Log.i(TAG, "Detected custom ROM: " + Build.MANUFACTURER + ", may need additional settings");
            showCustomRomOptimizationTips(activity);
        }
    }
    
    /**
     * 显示针对定制ROM的优化提示
     */
    private static void showCustomRomOptimizationTips(Activity activity) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        String tips = getCustomRomTips(manufacturer);
        
        new AlertDialog.Builder(activity)
            .setTitle("系统优化建议")
            .setMessage("检测到您使用的是 " + Build.MANUFACTURER + " 设备。\n\n" +
                       "为确保Launcher稳定运行，建议进行以下设置：\n\n" + tips)
            .setPositiveButton("知道了", null)
            .setNeutralButton("打开设置", (dialog, which) -> {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open settings", e);
                }
            })
            .show();
    }
    
    /**
     * 获取针对不同ROM的优化建议
     */
    private static String getCustomRomTips(String manufacturer) {
        if (manufacturer.contains("xiaomi")) {
            return "• 设置 → 应用设置 → 应用管理 → CATIA3 → 省电策略 → 无限制\n" +
                   "• 安全中心 → 应用管理 → 权限 → 自启动管理 → 允许CATIA3自启动";
        } else if (manufacturer.contains("huawei")) {
            return "• 设置 → 应用 → 应用启动管理 → CATIA3 → 手动管理 → 全部开启\n" +
                   "• 手机管家 → 应用启动管理 → CATIA3 → 允许";
        } else if (manufacturer.contains("oppo")) {
            return "• 设置 → 电池 → 应用耗电管理 → CATIA3 → 允许后台运行\n" +
                   "• 手机管家 → 权限隐私 → 自启动管理 → CATIA3 → 允许";
        } else if (manufacturer.contains("vivo")) {
            return "• i管家 → 应用管理 → 权限管理 → 自启动 → CATIA3 → 允许\n" +
                   "• 设置 → 电池 → 后台应用管理 → CATIA3 → 允许后台高耗电";
        } else {
            return "• 检查应用自启动权限\n" +
                   "• 检查后台应用限制\n" +
                   "• 将应用加入内存清理白名单";
        }
    }
    
    /**
     * 处理电池优化请求结果
     */
    public static void handleBatteryOptimizationResult(Activity activity, int requestCode, int resultCode) {
        if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            if (isIgnoringBatteryOptimizations(activity)) {
                Log.i(TAG, "Battery optimization exemption granted");
            } else {
                Log.w(TAG, "Battery optimization exemption not granted");
                // 可以选择再次提示用户或提供手动设置指导
            }
        }
    }
} 