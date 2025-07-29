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
import android.widget.Toast;

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
    
    private GridView appsGridView;
    private List<AppInfo> installedApps;
    private AppsAdapter appsAdapter;
    private Button settingsButton;
    private Button appsButton;
    private Button dataButton;
    private Button exitLauncherButton;
    private TextView statusTextView;
    private TextView timeTextView;
    
    private Handler timeHandler;
    private Runnable timeRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        
        initViews();
        loadInstalledApps();
        setupAppsGrid();
        startTimeUpdater();
    }
    
    private void initViews() {
        appsGridView = findViewById(R.id.apps_grid);
        settingsButton = findViewById(R.id.settings_button);
        appsButton = findViewById(R.id.apps_button);
        dataButton = findViewById(R.id.data_button);
        exitLauncherButton = findViewById(R.id.exit_launcher_button);
        statusTextView = findViewById(R.id.status_text);
        timeTextView = findViewById(R.id.time_text);
        
        // 设置按钮点击事件 - 跳转到原来的数据收集界面
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LauncherActivity.this, AndroidDataCollector.class);
                startActivity(intent);
            }
        });
        
        // 应用按钮点击事件 - 刷新应用列表
        appsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadInstalledApps();
                if (appsAdapter != null) {
                    appsAdapter.notifyDataSetChanged();
                }
            }
        });
        
        // 数据按钮点击事件 - 查看数据收集状态
        dataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDataStatus();
            }
        });
        
        // 退出Launcher模式按钮点击事件
        exitLauncherButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExitLauncherDialog();
            }
        });
        
        // 更新状态文本
        statusTextView.setText("CATIA3 Launcher - 上下文感知启动器");
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
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String currentTime = sdf.format(new Date());
        timeTextView.setText(currentTime);
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
    
    private void setupAppsGrid() {
        appsAdapter = new AppsAdapter(this, installedApps);
        appsGridView.setAdapter(appsAdapter);
        
        appsGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo appInfo = installedApps.get(position);
                launchApp(appInfo);
            }
        });
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
        // 每次回到主屏幕时刷新应用列表
        loadInstalledApps();
        if (appsAdapter != null) {
            appsAdapter.notifyDataSetChanged();
        }
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
    
    // 应用网格适配器
    private class AppsAdapter extends BaseAdapter {
        private Context context;
        private List<AppInfo> apps;
        
        public AppsAdapter(Context context, List<AppInfo> apps) {
            this.context = context;
            this.apps = apps;
        }
        
        @Override
        public int getCount() {
            return apps.size();
        }
        
        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.app_item, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.iconImageView = convertView.findViewById(R.id.app_icon);
                viewHolder.labelTextView = convertView.findViewById(R.id.app_label);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            
            AppInfo appInfo = apps.get(position);
            viewHolder.iconImageView.setImageDrawable(appInfo.icon);
            viewHolder.labelTextView.setText(appInfo.label);
            
            return convertView;
        }
        
        private class ViewHolder {
            ImageView iconImageView;
            TextView labelTextView;
        }
    }
} 