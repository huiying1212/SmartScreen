package com.datacollector.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * CATIA3 Launcher启动器主界面
 * 替代Android原生主屏幕，提供应用启动和数据收集功能
 */
public class LauncherActivity extends Activity {
    
    private static final String TAG = "LauncherActivity";
    
    // UI组件
    private ImageButton settingsButton;
    private ImageButton exitLauncherButton;
    private TextView timeTextView;
    private TextView dateTextView;
    private TextView deepseekResultTextView;
    private Button refreshDeepseekButton;
    private TextView dataStatusTextView;
    private TextView appsCountTextView;
    
    // 底部四个app快捷方式
    private LinearLayout[] appShortcuts = new LinearLayout[4];
    private ImageView[] appIcons = new ImageView[4];
    private TextView[] appNames = new TextView[4];
    private AppInfo[] shortcutApps = new AppInfo[4];
    
    // Widget区域
    private LinearLayout dataStatusWidget;
    private LinearLayout appsStatsWidget;
    private LinearLayout allAppsWidget;
    
    // 数据相关
    private List<AppInfo> installedApps;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private DeepSeekApiClient deepSeekApiClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        
        initViews();
        initializeData();
        setupEventListeners();
        startTimeUpdater();
        loadInstalledApps();
        setupDefaultShortcuts();
    }
    
    private void initViews() {
        // 左上角控制按钮
        settingsButton = findViewById(R.id.settings_button);
        exitLauncherButton = findViewById(R.id.exit_launcher_button);
        
        // DeepSeek区域
        deepseekResultTextView = findViewById(R.id.deepseek_result_text);
        refreshDeepseekButton = findViewById(R.id.refresh_deepseek_button);
        
        // Widgets区域
        timeTextView = findViewById(R.id.time_text);
        dateTextView = findViewById(R.id.date_text);
        dataStatusTextView = findViewById(R.id.data_status_text);
        appsCountTextView = findViewById(R.id.apps_count_text);
        
        dataStatusWidget = findViewById(R.id.data_status_widget);
        appsStatsWidget = findViewById(R.id.apps_stats_widget);
        allAppsWidget = findViewById(R.id.all_apps_widget);
        
        // 底部四个app快捷方式
        appShortcuts[0] = findViewById(R.id.app_shortcut_1);
        appShortcuts[1] = findViewById(R.id.app_shortcut_2);
        appShortcuts[2] = findViewById(R.id.app_shortcut_3);
        appShortcuts[3] = findViewById(R.id.app_shortcut_4);
        
        appIcons[0] = findViewById(R.id.app_icon_1);
        appIcons[1] = findViewById(R.id.app_icon_2);
        appIcons[2] = findViewById(R.id.app_icon_3);
        appIcons[3] = findViewById(R.id.app_icon_4);
        
        appNames[0] = findViewById(R.id.app_name_1);
        appNames[1] = findViewById(R.id.app_name_2);
        appNames[2] = findViewById(R.id.app_name_3);
        appNames[3] = findViewById(R.id.app_name_4);
    }
    
    private void initializeData() {
        deepSeekApiClient = new DeepSeekApiClient(this);
        installedApps = new ArrayList<>();
    }
    
    private void setupEventListeners() {
        // 设置按钮点击事件 - 跳转到原来的数据收集界面
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LauncherActivity.this, AndroidDataCollector.class);
                startActivity(intent);
            }
        });
        
        // 退出Launcher模式按钮点击事件
        exitLauncherButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExitLauncherDialog();
            }
        });
        
        // DeepSeek刷新按钮
        refreshDeepseekButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callDeepSeekApi();
            }
        });
        
        // 数据状态widget点击事件
        dataStatusWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDataStatus();
            }
        });
        
        // 应用统计widget点击事件
        appsStatsWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadInstalledApps();
                updateAppsCount();
                Toast.makeText(LauncherActivity.this, "应用列表已刷新", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 所有应用widget点击事件
        allAppsWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAllAppsDialog();
            }
        });
        
        // 底部app快捷方式点击事件
        for (int i = 0; i < 4; i++) {
            final int index = i;
            appShortcuts[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (shortcutApps[index] != null) {
                        launchApp(shortcutApps[index]);
                    } else {
                        selectAppForShortcut(index);
                    }
                }
            });
            
            // 长按设置快捷方式
            appShortcuts[i].setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    selectAppForShortcut(index);
                    return true;
                }
            });
        }
    }
    
    /**
     * 调用DeepSeek API进行数据分析
     */
    private void callDeepSeekApi() {
        refreshDeepseekButton.setEnabled(false);
        deepseekResultTextView.setText("正在调用AI分析，请稍候...");
        
        deepSeekApiClient.callDeepSeekWithLatestData(new DeepSeekApiClient.DeepSeekApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    try {
                        // 尝试解析JSON响应，提取notification_text字段
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(response);
                        
                        String notificationText = null;
                        
                        // 尝试直接获取notification_text
                        if (jsonResponse.has("notification_text")) {
                            notificationText = jsonResponse.getString("notification_text");
                        }
                        // 如果没有找到，可能在嵌套的对象中
                        else if (jsonResponse.has("data") && jsonResponse.getJSONObject("data").has("notification_text")) {
                            notificationText = jsonResponse.getJSONObject("data").getString("notification_text");
                        }
                        // 如果还没找到，尝试在choices数组中查找（OpenAI格式）
                        else if (jsonResponse.has("choices")) {
                            org.json.JSONArray choices = jsonResponse.getJSONArray("choices");
                            if (choices.length() > 0) {
                                org.json.JSONObject choice = choices.getJSONObject(0);
                                if (choice.has("message")) {
                                    org.json.JSONObject message = choice.getJSONObject("message");
                                    String content = message.getString("content");
                                    
                                    // 尝试解析content中的JSON
                                    try {
                                        org.json.JSONObject contentJson = new org.json.JSONObject(content);
                                        if (contentJson.has("notification_text")) {
                                            notificationText = contentJson.getString("notification_text");
                                        }
                                    } catch (org.json.JSONException e) {
                                        // 如果content不是JSON，直接使用content
                                        notificationText = content;
                                    }
                                }
                            }
                        }
                        
                        // 如果找到了notification_text，显示它
                        if (notificationText != null && !notificationText.trim().isEmpty()) {
                            deepseekResultTextView.setText(notificationText);
                        } else {
                            // 如果没有找到notification_text字段，显示原始响应
                            deepseekResultTextView.setText("AI分析结果:\n" + response);
                        }
                        
                    } catch (org.json.JSONException e) {
                        // 如果解析JSON失败，显示原始响应
                        deepseekResultTextView.setText("AI分析结果:\n" + response);
                    }
                    refreshDeepseekButton.setEnabled(true);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    deepseekResultTextView.setText("AI分析失败: " + error);
                    refreshDeepseekButton.setEnabled(true);
                });
            }
        });
    }
    
    /**
     * 显示所有应用的对话框
     */
    private void showAllAppsDialog() {
        Intent intent = new Intent(this, AllAppsActivity.class);
        startActivity(intent);
    }
    
    /**
     * 选择应用作为快捷方式
     */
    private void selectAppForShortcut(int index) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        
        // 创建应用列表
        String[] appNames = new String[installedApps.size()];
        for (int i = 0; i < installedApps.size(); i++) {
            appNames[i] = installedApps.get(i).label;
        }
        
        builder.setTitle("选择应用作为快捷方式 " + (index + 1))
               .setItems(appNames, (dialog, which) -> {
                   AppInfo selectedApp = installedApps.get(which);
                   setShortcutApp(index, selectedApp);
               })
               .setNegativeButton("取消", null)
               .show();
    }
    
    /**
     * 设置快捷方式应用
     */
    private void setShortcutApp(int index, AppInfo appInfo) {
        shortcutApps[index] = appInfo;
        appIcons[index].setImageDrawable(appInfo.icon);
        appNames[index].setText(appInfo.label);
        
        Toast.makeText(this, "快捷方式 " + (index + 1) + " 已设置为: " + appInfo.label, 
                      Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 设置默认快捷方式
     */
    private void setupDefaultShortcuts() {
        // 等待应用加载完成后设置默认快捷方式
        new Handler().postDelayed(() -> {
            if (installedApps.size() > 0) {
                // 尝试设置一些常用应用作为默认快捷方式
                setDefaultShortcutApp(0, "com.android.settings", "设置");
                setDefaultShortcutApp(1, "com.android.camera", "相机");
                setDefaultShortcutApp(2, "com.android.contacts", "联系人");
                setDefaultShortcutApp(3, "com.android.chrome", "浏览器");
            }
        }, 1000);
    }
    
    private void setDefaultShortcutApp(int index, String packageName, String defaultName) {
        for (AppInfo app : installedApps) {
            if (app.packageName.equals(packageName)) {
                setShortcutApp(index, app);
                return;
            }
        }
        // 如果没找到指定应用，设置为第一个可用应用
        if (installedApps.size() > index) {
            setShortcutApp(index, installedApps.get(index));
        }
    }
    
    private void updateAppsCount() {
        appsCountTextView.setText("已安装 " + installedApps.size() + " 个应用");
    }
    
    private void showExitLauncherDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("退出Launcher模式")
               .setMessage("您确定要退出CATIA3 Launcher模式吗？\n\n这将打开系统设置，您可以选择其他启动器作为默认主屏幕。")
               .setPositiveButton("确定退出", new android.content.DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(android.content.DialogInterface dialog, int which) {
                       exitLauncherMode();
                   }
               })
               .setNegativeButton("取消", null)
               .show();
    }
    
    private void exitLauncherMode() {
        try {
            // 方法1：打开默认应用设置页面
            Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                Toast.makeText(this, "请在设置中选择其他启动器", Toast.LENGTH_LONG).show();
            } else {
                // 方法2：如果上面的方法不工作，打开应用管理页面
                intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                startActivity(intent);
                Toast.makeText(this, "请在应用管理中找到默认应用设置", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 方法3：如果都不行，启动一个其他的启动器（如果存在）
            tryLaunchSystemLauncher();
        }
    }
    
    private void tryLaunchSystemLauncher() {
        try {
            // 尝试找到系统默认启动器
            PackageManager pm = getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(homeIntent, 0);
            
            // 寻找非当前应用的启动器
            for (ResolveInfo resolveInfo : resolveInfos) {
                if (!resolveInfo.activityInfo.packageName.equals(getPackageName())) {
                    Intent launchIntent = new Intent(Intent.ACTION_MAIN);
                    launchIntent.addCategory(Intent.CATEGORY_HOME);
                    launchIntent.setClassName(resolveInfo.activityInfo.packageName, 
                                            resolveInfo.activityInfo.name);
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(launchIntent);
                    Toast.makeText(this, "已切换到系统启动器", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // 如果没找到其他启动器，显示提示
            Toast.makeText(this, "未找到其他启动器，请手动在设置中更改", Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "切换失败，请在设置中手动更改默认启动器", Toast.LENGTH_LONG).show();
        }
    }
    
    private void startTimeUpdater() {
        timeHandler = new Handler();
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateTime();
                timeHandler.postDelayed(this, 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }
    
    private void updateTime() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        
        Date now = new Date();
        timeTextView.setText(timeFormat.format(now));
        dateTextView.setText(dateFormat.format(now));
    }
    
    private void showDataStatus() {
        String message = "数据收集状态：\n" +
                        "当前时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n" +
                        "已安装应用：" + (installedApps != null ? installedApps.size() : 0) + " 个";
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("CATIA3 数据状态")
               .setMessage(message)
               .setPositiveButton("确定", null)
               .show();
    }
    
    private void loadInstalledApps() {
        PackageManager packageManager = getPackageManager();
        
        // 获取所有可启动的应用
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(mainIntent, 0);
        
        installedApps = new ArrayList<>();
        
        for (ResolveInfo resolveInfo : resolveInfos) {
            AppInfo appInfo = new AppInfo();
            appInfo.label = resolveInfo.loadLabel(packageManager).toString();
            appInfo.packageName = resolveInfo.activityInfo.packageName;
            appInfo.className = resolveInfo.activityInfo.name;
            appInfo.icon = resolveInfo.loadIcon(packageManager);
            
            // 过滤掉系统设置等不需要显示的应用，但保留一些常用的系统应用
            if (!isSystemApp(resolveInfo.activityInfo.applicationInfo) || 
                isWhitelistedSystemApp(appInfo.packageName)) {
                installedApps.add(appInfo);
            }
        }
        
        // 按应用名称排序
        Collections.sort(installedApps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo app1, AppInfo app2) {
                return app1.label.compareToIgnoreCase(app2.label);
            }
        });
        
        // 更新应用计数显示
        updateAppsCount();
        updateDataStatus();
    }
    
    private void updateDataStatus() {
        dataStatusTextView.setText("运行中 - " + installedApps.size() + " 个应用");
    }
    
    private boolean isSystemApp(ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }
    
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
               packageName.equals("com.tencent.mm");
    }
    
    private void launchApp(AppInfo appInfo) {
        try {
            Intent intent = new Intent();
            intent.setClassName(appInfo.packageName, appInfo.className);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            
            // 这里可以添加应用启动的数据收集逻辑
            collectAppLaunchData(appInfo);
            
        } catch (Exception e) {
            e.printStackTrace();
            // 如果直接启动失败，尝试使用包管理器启动
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(appInfo.packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
                collectAppLaunchData(appInfo);
            } else {
                Toast.makeText(this, "无法启动应用: " + appInfo.label, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void collectAppLaunchData(AppInfo appInfo) {
        // 收集应用启动数据，用于CATIA3系统的上下文感知
        try {
            Intent serviceIntent = new Intent(this, DataCollectionService.class);
            serviceIntent.putExtra("action", "app_launch");
            serviceIntent.putExtra("package_name", appInfo.packageName);
            serviceIntent.putExtra("app_label", appInfo.label);
            serviceIntent.putExtra("timestamp", System.currentTimeMillis());
            startService(serviceIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onBackPressed() {
        // 在Launcher中禁用返回键，保持在主屏幕
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到主屏幕时刷新应用列表和更新显示
        loadInstalledApps();
        updateTime();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }
    
    // 应用信息类
    public static class AppInfo {
        public String label;
        public String packageName;
        public String className;
        public Drawable icon;
    }
} 