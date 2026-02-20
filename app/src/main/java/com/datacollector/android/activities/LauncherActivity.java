package com.datacollector.android.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
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

import com.datacollector.android.managers.InstalledAppsManager;
import com.datacollector.android.models.WidgetSuggestion;
import com.datacollector.android.services.DataCollectionService;
import com.datacollector.android.api.GeminiApiClient;
import com.datacollector.android.api.DeepSeekApiClient;
import com.datacollector.android.utils.BatteryOptimizationHelper;
import com.datacollector.android.utils.LauncherStabilityManager;
import com.datacollector.android.R;

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
 * 增强版本：添加了进程保活和稳定性管理机制
 */
public class LauncherActivity extends Activity implements DeepSeekApiClient.LauncherUpdateCallback {
    
    private static final String TAG = "LauncherActivity";
    private static final String PREFS_NAME = "launcher_prefs";
    private static final String KEY_LAST_ACTIVE_TIME = "last_active_time";
    private static final String KEY_RESTART_COUNT = "restart_count";
    
    // 稳定性管理器
    private LauncherStabilityManager stabilityManager;
    
    // SharedPreferences for state persistence
    private SharedPreferences prefs;
    
    // UI组件
    private ImageButton settingsButton;
    private ImageButton exitLauncherButton;
    private TextView deepseekResultTextView;
    
    // 智能建议widgets相关UI组件
    private LinearLayout dynamicWidgetsContainer;
    private TextView widgetsPlaceholderText;
    private List<WidgetSuggestion> currentWidgetSuggestions;
    
    // 新增：widget状态管理
    private boolean hasValidWidgetSuggestions = false;
    private long lastWidgetUpdateTime = 0;
    private static final long WIDGET_CACHE_DURATION = 10 * 60 * 1000; // 10分钟缓存
    
    // AI分析触发优化 - 只在有变化时触发
    private long lastAnalysisTime = 0;
    private boolean hasChangedSinceLastAnalysis = false;
    private long lastPauseTime = 0;
    private static final long MIN_ANALYSIS_INTERVAL = 5000; // 最小分析间隔5秒
    private static final long MIN_PAUSE_DURATION = 2000; // 最小暂停时长2秒
    
    // 底部四个app快捷方式
    private LinearLayout[] appShortcuts = new LinearLayout[4];
    private ImageView[] appIcons = new ImageView[4];
    private TextView[] appNames = new TextView[4];
    private AppInfo[] shortcutApps = new AppInfo[4];
    
    // 数据相关
    private List<AppInfo> installedApps;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private DeepSeekApiClient deepSeekApiClient;
    
    // 数据收集服务绑定
    private DataCollectionService dataCollectionService;
    private boolean isServiceBound = false;
    
    // 服务连接对象
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DataCollectionService.DataCollectionBinder binder = 
                (DataCollectionService.DataCollectionBinder) service;
            dataCollectionService = binder.getService();
            isServiceBound = true;
            Log.d(TAG, "Connected to DataCollectionService");
            
            // 确保回调已注册
            DeepSeekApiClient.setLauncherUpdateCallback(LauncherActivity.this);
            Log.d(TAG, "LauncherUpdateCallback re-registered after service connection");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            dataCollectionService = null;
            isServiceBound = false;
            Log.d(TAG, "Disconnected from DataCollectionService");
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        
        // 初始化SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // 初始化稳定性管理器
        stabilityManager = new LauncherStabilityManager(this);
        
        // 检查是否是重启
        checkForRestart();
        
        // 检查电池优化
        BatteryOptimizationHelper.checkAndRequestBatteryOptimization(this);

        // 初始化已安装应用管理器 - 在应用启动时立即检查并缓存应用列表
        InstalledAppsManager.getInstance(this).refreshAppsList();
        
        initViews();
        initializeData();
        setupEventListeners();
        startTimeUpdater();
        loadInstalledApps();
        setupDefaultShortcuts();
        
        // 初始化widget占位符
        showDefaultWidgetPlaceholder();
        
        // 绑定数据收集服务
        bindDataCollectionService();
        
        // 注册DeepSeek Launcher更新回调
        DeepSeekApiClient.setLauncherUpdateCallback(this);
        
        // 启动稳定性监控
        stabilityManager.startStabilityMonitoring();
        
        // 记录活跃时间
        recordActiveTime();
    }
    
    private void initViews() {
        // 左上角控制按钮
        settingsButton = findViewById(R.id.settings_button);
        exitLauncherButton = findViewById(R.id.exit_launcher_button);
        
        // DeepSeek区域
        deepseekResultTextView = findViewById(R.id.deepseek_result_text);
        
        // 智能建议widgets相关UI组件
        dynamicWidgetsContainer = findViewById(R.id.dynamic_widgets_container);
        widgetsPlaceholderText = findViewById(R.id.widgets_placeholder_text);
        
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
        currentWidgetSuggestions = new ArrayList<>();
        
        // 设置初始的AI助手提示信息
        deepseekResultTextView.setText("AI智能助手准备就绪\n回到桌面时将自动分析并更新建议 🤖");
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
        
        // 长按设置按钮显示数据清理状态
        settingsButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showDataCleanupStatus();
                return true;
            }
        });
        
        // 退出Launcher模式按钮点击事件
        exitLauncherButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExitLauncherDialog();
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
        deepseekResultTextView.setText("正在调用AI分析，请稍候...");
        
        deepSeekApiClient.callDeepSeekWithLatestData(new DeepSeekApiClient.DeepSeekApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    try {
                        // 尝试解析JSON响应，提取notification_text和next_move字段
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(response);
                        
                        String notificationText = null;
                        org.json.JSONArray nextMoveArray = null;
                        
                        // 尝试直接获取字段
                        if (jsonResponse.has("notification_text")) {
                            notificationText = jsonResponse.getString("notification_text");
                        }
                        if (jsonResponse.has("next_move")) {
                            nextMoveArray = jsonResponse.getJSONArray("next_move");
                        }
                        
                        // 如果没有找到，可能在嵌套的对象中
                        if ((notificationText == null || nextMoveArray == null) && jsonResponse.has("data")) {
                            org.json.JSONObject data = jsonResponse.getJSONObject("data");
                            if (notificationText == null && data.has("notification_text")) {
                                notificationText = data.getString("notification_text");
                            }
                            if (nextMoveArray == null && data.has("next_move")) {
                                nextMoveArray = data.getJSONArray("next_move");
                            }
                        }
                        
                        // 如果还没找到，尝试在choices数组中查找（OpenAI格式）
                        if ((notificationText == null || nextMoveArray == null) && jsonResponse.has("choices")) {
                            org.json.JSONArray choices = jsonResponse.getJSONArray("choices");
                            if (choices.length() > 0) {
                                org.json.JSONObject choice = choices.getJSONObject(0);
                                if (choice.has("message")) {
                                    org.json.JSONObject message = choice.getJSONObject("message");
                                    String content = message.getString("content");
                                    
                                    // 尝试解析content中的JSON
                                    try {
                                        org.json.JSONObject contentJson = new org.json.JSONObject(content);
                                        if (notificationText == null && contentJson.has("notification_text")) {
                                            notificationText = contentJson.getString("notification_text");
                                        }
                                        if (nextMoveArray == null && contentJson.has("next_move")) {
                                            nextMoveArray = contentJson.getJSONArray("next_move");
                                        }
                                    } catch (org.json.JSONException e) {
                                        // 如果content不是JSON，直接使用content作为notification
                                        if (notificationText == null) {
                                            notificationText = content;
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 显示notification_text
                        if (notificationText != null && !notificationText.trim().isEmpty()) {
                            deepseekResultTextView.setText(notificationText);
                        } else {
                            deepseekResultTextView.setText("AI分析结果:\n" + response);
                        }
                        
                        // 处理next_move数据并显示widget建议
                        if (nextMoveArray != null && nextMoveArray.length() > 0) {
                            parseAndDisplayWidgetSuggestions(nextMoveArray);
                        } else {
                            // 如果没有next_move数据，显示分析完成但无建议的状态
                            showAnalysisCompleteNoSuggestions();
                        }
                        
                    } catch (org.json.JSONException e) {
                        // 如果解析JSON失败，显示原始响应
                        deepseekResultTextView.setText("AI分析结果:\n" + response);
                        // 解析失败时显示分析完成但无建议状态
                        showAnalysisCompleteNoSuggestions();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    deepseekResultTextView.setText("AI分析失败: " + error);
                    // 错误时显示分析失败状态
                    showAnalysisErrorWidget();
                });
            }
        });
    }
    
    /**
     * 解析next_move数据并显示widget建议
     */
    private void parseAndDisplayWidgetSuggestions(org.json.JSONArray nextMoveArray) {
        Log.d(TAG, "parseAndDisplayWidgetSuggestions called with " + nextMoveArray.length() + " items");
        try {
            currentWidgetSuggestions.clear();
            
            // 解析每个widget建议
            for (int i = 0; i < nextMoveArray.length(); i++) {
                org.json.JSONObject widgetObj = nextMoveArray.getJSONObject(i);
                
                String type = widgetObj.optString("type", "widget");
                String app = widgetObj.optString("app", "Unknown");
                String action = widgetObj.optString("action", "No action specified");
                
                Log.d(TAG, "Parsed widget " + i + ": type=" + type + ", app=" + app + ", action=" + action);
                
                WidgetSuggestion suggestion = new WidgetSuggestion(type, app, action);
                currentWidgetSuggestions.add(suggestion);
            }
            
            // 显示widget建议
            displayWidgetSuggestions();
            
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error parsing widget suggestions", e);
            showDefaultWidgetPlaceholder();
        }
    }
    
    /**
     * 显示widget建议到UI
     */
    private void displayWidgetSuggestions() {
        Log.d(TAG, "displayWidgetSuggestions called with " + currentWidgetSuggestions.size() + " suggestions");
        // 清空现有的widget视图
        dynamicWidgetsContainer.removeAllViews();
        
        if (currentWidgetSuggestions.isEmpty()) {
            Log.d(TAG, "No widget suggestions to display, showing placeholder");
            showDefaultWidgetPlaceholder();
            return;
        }
        
        // 隐藏占位符文本
        widgetsPlaceholderText.setVisibility(View.GONE);
        
        // 为每个建议创建widget视图
        LayoutInflater inflater = LayoutInflater.from(this);
        for (WidgetSuggestion suggestion : currentWidgetSuggestions) {
            View widgetView = inflater.inflate(R.layout.widget_item, dynamicWidgetsContainer, false);
            
            // 设置widget内容
            setupWidgetView(widgetView, suggestion);
            
            // 添加到容器
            dynamicWidgetsContainer.addView(widgetView);
            Log.d(TAG, "Added widget for app: " + suggestion.getApp());
        }
        
        // 更新widget状态
        hasValidWidgetSuggestions = true;
        lastWidgetUpdateTime = System.currentTimeMillis();
        Log.d(TAG, "Widget建议已更新，数量: " + currentWidgetSuggestions.size());
    }
    
    /**
     * 设置单个widget视图的内容和点击事件
     */
    private void setupWidgetView(View widgetView, WidgetSuggestion suggestion) {
        ImageView appIcon = widgetView.findViewById(R.id.widget_app_icon);
        TextView appName = widgetView.findViewById(R.id.widget_app_name);
        TextView actionText = widgetView.findViewById(R.id.widget_action_text);
        
        // 设置文本内容
        appName.setText(suggestion.getApp());
        actionText.setText(suggestion.getAction());
        
        // 尝试获取应用图标
        try {
            PackageManager pm = getPackageManager();
            // 尝试通过应用名称查找对应的包名
            String packageName = findPackageNameByAppName(suggestion.getApp());
            if (packageName != null) {
                Drawable icon = pm.getApplicationIcon(packageName);
                appIcon.setImageDrawable(icon);
            } else {
                // 使用默认图标
                appIcon.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } catch (Exception e) {
            // 如果获取图标失败，使用默认图标
            appIcon.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        
        // 设置点击事件
        widgetView.setOnClickListener(v -> {
            handleWidgetClick(suggestion);
        });
    }
    
    /**
     * 根据应用名称查找对应的包名
     */
    private String findPackageNameByAppName(String appName) {
        for (AppInfo app : installedApps) {
            if (app.label.equalsIgnoreCase(appName) || 
                app.label.toLowerCase().contains(appName.toLowerCase()) ||
                appName.toLowerCase().contains(app.label.toLowerCase())) {
                return app.packageName;
            }
        }
        return null;
    }
    
    /**
     * 处理widget点击事件
     */
    private void handleWidgetClick(WidgetSuggestion suggestion) {
        String packageName = findPackageNameByAppName(suggestion.getApp());
        if (packageName != null) {
            // 找到对应的应用，启动它
            for (AppInfo app : installedApps) {
                if (app.packageName.equals(packageName)) {
                    launchApp(app);
                    return;
                }
            }
        }
        
        // 如果没找到应用，显示提示并尝试搜索相似应用
        Toast.makeText(this, "未找到应用: " + suggestion.getApp() + "\n建议: " + suggestion.getAction(), 
                      Toast.LENGTH_LONG).show();
    }
    
    /**
     * 显示默认的widget占位符
     */
    private void showDefaultWidgetPlaceholder() {
        // 如果有有效的widget建议且未过期，不要显示占位符
        if (hasValidWidgetSuggestions && 
            (System.currentTimeMillis() - lastWidgetUpdateTime) < WIDGET_CACHE_DURATION) {
            Log.d(TAG, "保持现有widget建议显示，不显示占位符");
            return;
        }
        
        dynamicWidgetsContainer.removeAllViews();
        widgetsPlaceholderText.setVisibility(View.VISIBLE);
        widgetsPlaceholderText.setText("等待AI分析后显示智能建议...");
        hasValidWidgetSuggestions = false;
    }
    
    /**
     * 显示AI分析进行中的widget状态
     */
    private void showAnalysisInProgressWidget() {
        Log.d(TAG, "显示AI分析进行中状态");
        
        // 清空现有的widget视图
        dynamicWidgetsContainer.removeAllViews();
        
        // 显示分析中状态
        widgetsPlaceholderText.setVisibility(View.VISIBLE);
        widgetsPlaceholderText.setText("AI正在分析中，请稍候... 🔍");
        
        // 暂时标记为无效状态，强制等待新结果
        hasValidWidgetSuggestions = false;
        lastWidgetUpdateTime = 0; // 重置时间，确保新结果会立即显示
        
        Log.d(TAG, "AI分析进行中状态已显示");
    }
    
    /**
     * 显示AI分析完成但无建议的状态
     */
    private void showAnalysisCompleteNoSuggestions() {
        Log.d(TAG, "显示AI分析完成但无建议状态");
        
        // 清空现有的widget视图
        dynamicWidgetsContainer.removeAllViews();
        
        // 显示分析完成状态
        widgetsPlaceholderText.setVisibility(View.VISIBLE);
        widgetsPlaceholderText.setText("AI分析完成，暂无智能建议");
        
        // 标记为无效状态，等待下次分析
        hasValidWidgetSuggestions = false;
        lastWidgetUpdateTime = System.currentTimeMillis();
        
        Log.d(TAG, "AI分析完成但无建议状态已显示");
    }
    
    /**
     * 显示AI分析错误状态
     */
    private void showAnalysisErrorWidget() {
        Log.d(TAG, "显示AI分析错误状态");
        
        // 清空现有的widget视图
        dynamicWidgetsContainer.removeAllViews();
        
        // 显示分析错误状态
        widgetsPlaceholderText.setVisibility(View.VISIBLE);
        widgetsPlaceholderText.setText("AI分析失败，请稍后重试 ⚠️");
        
        // 标记为无效状态，等待下次分析
        hasValidWidgetSuggestions = false;
        lastWidgetUpdateTime = System.currentTimeMillis();
        
        Log.d(TAG, "AI分析错误状态已显示");
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
        
        // 标记有变化 - 用户修改了快捷方式
        hasChangedSinceLastAnalysis = true;
        
        // Toast提示已移除 - 用户要求不显示设置成功提示
        // Toast.makeText(this, "快捷方式 " + (index + 1) + " 已设置为: " + appInfo.label, 
        //               Toast.LENGTH_SHORT).show();
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
                // updateTime(); // Removed as time display is no longer needed
                timeHandler.postDelayed(this, 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }
    
    private void updateDataStatus() {
        // This method is no longer needed as dataStatusTextView is removed.
        // Keeping it for now in case it's called elsewhere, but it will do nothing.
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
        // updateAppsCount(); // Removed as app statistics are no longer displayed
        // updateDataStatus(); // Removed as dataStatusTextView is no longer available
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
            
            // 标记有变化 - 用户启动了应用
            hasChangedSinceLastAnalysis = true;
            
        } catch (Exception e) {
            e.printStackTrace();
            // 如果直接启动失败，尝试使用包管理器启动
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(appInfo.packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
                collectAppLaunchData(appInfo);
                // 标记有变化 - 用户启动了应用
                hasChangedSinceLastAnalysis = true;
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
    
    /**
     * 绑定数据收集服务
     */
    private void bindDataCollectionService() {
        Intent serviceIntent = new Intent(this, DataCollectionService.class);
        // 先启动前台服务，然后绑定
        startForegroundService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    
    /**
     * 判断是否应该触发AI分析
     * 只在有变化或首次启动时返回true
     */
    private boolean shouldTriggerAnalysis() {
        long currentTime = System.currentTimeMillis();
        
        // 首次启动（从未分析过）
        if (lastAnalysisTime == 0) {
            return true;
        }
        
        // 防止频繁分析 - 距离上次分析不足最小间隔
        if (currentTime - lastAnalysisTime < MIN_ANALYSIS_INTERVAL) {
            return false;
        }
        
        // 有变化需要分析
        if (hasChangedSinceLastAnalysis) {
            return true;
        }
        
        // 检测用户是否从其他应用返回（切换应用行为）
        if (lastPauseTime > 0 && lastPauseTime > lastAnalysisTime) {
            // 检查用户是否真的离开了足够长的时间（避免短暂切换）
            long pauseDuration = currentTime - lastPauseTime;
            if (pauseDuration >= MIN_PAUSE_DURATION) {
                return true;
            }
        }
        
        // 超过一定时间间隔强制分析（避免长时间不更新）
        long timeSinceLastAnalysis = currentTime - lastAnalysisTime;
        if (timeSinceLastAnalysis > 30 * 60 * 1000) { // 30分钟强制更新一次
            return true;
        }
        
        return false;
    }
    
    /**
     * 触发数据收集
     * 当用户回到桌面时调用
     */
    private void triggerDataCollection() {
        try {
            // 显示分析开始提示
            deepseekResultTextView.setText("正在分析当前情况... 🔍");
            
            // 立即清除widget显示，显示分析中状态
            showAnalysisInProgressWidget();
            
            Intent serviceIntent = new Intent(this, DataCollectionService.class);
            serviceIntent.putExtra("action", "trigger_collection");
            serviceIntent.putExtra("trigger_reason", "home_screen_resume");
            serviceIntent.putExtra("timestamp", System.currentTimeMillis());
            startForegroundService(serviceIntent);
            
            Log.d(TAG, "Triggered data collection on home screen resume");
        } catch (Exception e) {
            Log.e(TAG, "Error triggering data collection", e);
            deepseekResultTextView.setText("数据收集启动失败，请稍后重试");
            // 错误时也显示分析中状态
            showAnalysisInProgressWidget();
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
        // 同时刷新已安装应用管理器的缓存，以防在后台时有新应用安装
        InstalledAppsManager.getInstance(this).refreshAppsList();
        loadInstalledApps();
        // updateTime(); // Removed as time display is no longer needed
        
        // 重新启动稳定性监控（如果被暂停）
        if (stabilityManager != null) {
            stabilityManager.startStabilityMonitoring();
        }
        
        // 重新注册回调（防止被清除）
        DeepSeekApiClient.setLauncherUpdateCallback(this);
        
        Log.d(TAG, "onResume: Current widget suggestions count: " + 
              (currentWidgetSuggestions != null ? currentWidgetSuggestions.size() : "null") +
              ", hasValidWidgetSuggestions: " + hasValidWidgetSuggestions);
        
        // 如果有有效的widget建议，恢复显示
        if (hasValidWidgetSuggestions && currentWidgetSuggestions != null && !currentWidgetSuggestions.isEmpty()) {
            displayWidgetSuggestions();
            Log.d(TAG, "恢复显示之前的widget建议");
        }
        
        // 记录活跃时间
        recordActiveTime();
        
        // 触发数据收集 - 只在有变化或首次启动时触发
        if (shouldTriggerAnalysis()) {
            triggerDataCollection();
            hasChangedSinceLastAnalysis = false;
            lastAnalysisTime = System.currentTimeMillis();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 记录用户离开launcher的时间
        lastPauseTime = System.currentTimeMillis();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
        
        // 停止稳定性监控
        if (stabilityManager != null) {
            stabilityManager.stopStabilityMonitoring();
        }
        
        // 解绑数据收集服务
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        
        // 清除DeepSeek回调
        DeepSeekApiClient.clearLauncherUpdateCallback();
    }
    
    // 实现LauncherUpdateCallback接口
    @Override
    public void onAnalysisComplete(String notificationText) {
        runOnUiThread(() -> {
            // 更新DeepSeek显示区域
            deepseekResultTextView.setText(notificationText);
            
            Log.d(TAG, "Launcher updated with notification: " + notificationText);
        });
    }
    
    /**
     * 新增：处理完整的DeepSeek响应，包括widget建议
     */
    @Override
    public void onFullAnalysisComplete(String fullResponse) {
        Log.d(TAG, "onFullAnalysisComplete called with response length: " + fullResponse.length());
        runOnUiThread(() -> {
            try {
                // 解析完整响应
                org.json.JSONObject jsonResponse = new org.json.JSONObject(fullResponse);
                Log.d(TAG, "Parsed JSON response: " + jsonResponse.toString());
                
                String notificationText = null;
                org.json.JSONArray nextMoveArray = null;
                
                // 尝试直接获取字段
                if (jsonResponse.has("notification_text")) {
                    notificationText = jsonResponse.getString("notification_text");
                    Log.d(TAG, "Found notification_text: " + notificationText);
                }
                if (jsonResponse.has("next_move")) {
                    nextMoveArray = jsonResponse.getJSONArray("next_move");
                    Log.d(TAG, "Found next_move array with " + nextMoveArray.length() + " items");
                }
                
                // 如果没有找到，可能在嵌套的对象中
                if ((notificationText == null || nextMoveArray == null) && jsonResponse.has("data")) {
                    org.json.JSONObject data = jsonResponse.getJSONObject("data");
                    if (notificationText == null && data.has("notification_text")) {
                        notificationText = data.getString("notification_text");
                    }
                    if (nextMoveArray == null && data.has("next_move")) {
                        nextMoveArray = data.getJSONArray("next_move");
                    }
                }
                
                // 如果还没找到，尝试在choices数组中查找（OpenAI格式）
                if ((notificationText == null || nextMoveArray == null) && jsonResponse.has("choices")) {
                    org.json.JSONArray choices = jsonResponse.getJSONArray("choices");
                    if (choices.length() > 0) {
                        org.json.JSONObject choice = choices.getJSONObject(0);
                        if (choice.has("message")) {
                            org.json.JSONObject message = choice.getJSONObject("message");
                            String content = message.getString("content");
                            
                            // 尝试解析content中的JSON
                            try {
                                org.json.JSONObject contentJson = new org.json.JSONObject(content);
                                if (notificationText == null && contentJson.has("notification_text")) {
                                    notificationText = contentJson.getString("notification_text");
                                }
                                if (nextMoveArray == null && contentJson.has("next_move")) {
                                    nextMoveArray = contentJson.getJSONArray("next_move");
                                }
                            } catch (org.json.JSONException e) {
                                // 如果content不是JSON，直接使用content作为notification
                                if (notificationText == null) {
                                    notificationText = content;
                                }
                            }
                        }
                    }
                }
                
                // 显示notification_text
                if (notificationText != null && !notificationText.trim().isEmpty()) {
                    deepseekResultTextView.setText(notificationText);
                } else {
                    deepseekResultTextView.setText("AI分析完成");
                }
                
                // 处理next_move数据并显示widget建议
                if (nextMoveArray != null && nextMoveArray.length() > 0) {
                    Log.d(TAG, "Processing " + nextMoveArray.length() + " widget suggestions");
                    parseAndDisplayWidgetSuggestions(nextMoveArray);
                    Log.d(TAG, "Found " + nextMoveArray.length() + " widget suggestions");
                } else {
                    Log.d(TAG, "No next_move data found, showing analysis complete no suggestions");
                    // 如果没有next_move数据，显示分析完成但无建议的状态
                    showAnalysisCompleteNoSuggestions();
                }
                
            } catch (org.json.JSONException e) {
                Log.e(TAG, "Error parsing full analysis response", e);
                deepseekResultTextView.setText("AI分析完成");
                // 解析失败时显示分析完成但无建议状态
                showAnalysisCompleteNoSuggestions();
            }
        });
    }
    
    @Override
    public void onAnalysisError(String error) {
        runOnUiThread(() -> {
            // 显示错误信息
            deepseekResultTextView.setText("AI分析暂时不可用，请检查网络连接或稍后重试 ⚠️");
            // 显示分析错误状态
            showAnalysisErrorWidget();
            Log.e(TAG, "Analysis error: " + error);
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 处理电池优化设置结果
        BatteryOptimizationHelper.handleBatteryOptimizationResult(this, requestCode, resultCode);
    }
    
    /**
     * 检查是否是重启
     */
    private void checkForRestart() {
        long lastActiveTime = prefs.getLong(KEY_LAST_ACTIVE_TIME, 0);
        long currentTime = System.currentTimeMillis();
        
        if (lastActiveTime > 0) {
            long timeSinceLastActive = currentTime - lastActiveTime;
            
            // 如果距离上次活跃时间超过1分钟，可能是重启
            if (timeSinceLastActive > 60 * 1000) {
                int restartCount = prefs.getInt(KEY_RESTART_COUNT, 0) + 1;
                prefs.edit().putInt(KEY_RESTART_COUNT, restartCount).apply();
                
                Log.w(TAG, "Detected launcher restart. Count: " + restartCount + 
                           ", Time since last active: " + (timeSinceLastActive / 1000) + " seconds");
                
                // 如果重启次数过多，显示提示
                if (restartCount > 5) {
                    showStabilityWarning();
                }
            }
        }
    }
    
    /**
     * 记录活跃时间
     */
    private void recordActiveTime() {
        long currentTime = System.currentTimeMillis();
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, currentTime).apply();
    }
    
    /**
     * 显示稳定性警告
     */
    private void showStabilityWarning() {
        if (stabilityManager != null) {
            String stats = stabilityManager.getStabilityStats();
            
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Launcher稳定性提醒")
                   .setMessage("检测到Launcher频繁重启，这可能影响使用体验。\n\n" +
                              "建议检查以下设置：\n" +
                              "1. 电池优化豁免\n" +
                              "2. 后台应用限制\n" +
                              "3. 内存清理白名单\n\n" +
                              stats)
                   .setPositiveButton("去设置", (dialog, which) -> {
                       BatteryOptimizationHelper.checkAndRequestBatteryOptimization(this);
                   })
                   .setNegativeButton("忽略", (dialog, which) -> {
                       // 重置重启计数
                       prefs.edit().putInt(KEY_RESTART_COUNT, 0).apply();
                   })
                   .show();
        }
    }
    
    /**
     * 显示数据清理状态和采集统计
     */
    private void showDataCleanupStatus() {
        if (dataCollectionService != null) {
            String cleanupStats = dataCollectionService.getDataCleanupStats();
            String collectionStats = dataCollectionService.getCollectionStatsSummary();
            
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("数据管理状态")
                   .setMessage(cleanupStats + "\n\n" + collectionStats +
                              "\n\n数据清理会自动删除旧文件以节省存储空间。\n" +
                              "数据文件使用AES-GCM加密 + GZIP压缩存储。")
                   .setPositiveButton("立即清理", (dialog, which) -> {
                       if (dataCollectionService != null) {
                           dataCollectionService.performManualCleanup();
                           Toast.makeText(this, "数据清理已开始", Toast.LENGTH_SHORT).show();
                       }
                   })
                   .setNegativeButton("关闭", null)
                   .show();
        } else {
            Toast.makeText(this, "数据收集服务未连接", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存重要状态
        if (currentWidgetSuggestions != null) {
            outState.putBoolean("hasValidWidgets", hasValidWidgetSuggestions);
            outState.putLong("lastWidgetUpdate", lastWidgetUpdateTime);
        }
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 恢复状态
        if (savedInstanceState != null) {
            hasValidWidgetSuggestions = savedInstanceState.getBoolean("hasValidWidgets", false);
            lastWidgetUpdateTime = savedInstanceState.getLong("lastWidgetUpdate", 0);
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