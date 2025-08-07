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