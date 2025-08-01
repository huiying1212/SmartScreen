package com.datacollector.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 全部应用列表Activity
 * 显示所有已安装的应用程序
 */
public class AllAppsActivity extends Activity {
    
    private GridView appsGridView;
    private List<LauncherActivity.AppInfo> installedApps;
    private AppsAdapter appsAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_apps);
        
        initViews();
        loadInstalledApps();
        setupAppsGrid();
    }
    
    private void initViews() {
        appsGridView = findViewById(R.id.apps_grid);
    }
    
    private void loadInstalledApps() {
        PackageManager packageManager = getPackageManager();
        
        // 获取所有可启动的应用
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(mainIntent, 0);
        
        installedApps = new ArrayList<>();
        
        for (ResolveInfo resolveInfo : resolveInfos) {
            LauncherActivity.AppInfo appInfo = new LauncherActivity.AppInfo();
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
        Collections.sort(installedApps, new Comparator<LauncherActivity.AppInfo>() {
            @Override
            public int compare(LauncherActivity.AppInfo app1, LauncherActivity.AppInfo app2) {
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
                LauncherActivity.AppInfo appInfo = installedApps.get(position);
                launchApp(appInfo);
            }
        });
    }
    
    private void launchApp(LauncherActivity.AppInfo appInfo) {
        try {
            Intent intent = new Intent();
            intent.setClassName(appInfo.packageName, appInfo.className);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            
            // 关闭当前Activity，返回到launcher
            finish();
            
        } catch (Exception e) {
            e.printStackTrace();
            // 如果直接启动失败，尝试使用包管理器启动
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(appInfo.packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
                finish();
            } else {
                Toast.makeText(this, "无法启动应用: " + appInfo.label, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // 应用网格适配器
    private class AppsAdapter extends BaseAdapter {
        private Context context;
        private List<LauncherActivity.AppInfo> apps;
        
        public AppsAdapter(Context context, List<LauncherActivity.AppInfo> apps) {
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
            
            LauncherActivity.AppInfo appInfo = apps.get(position);
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