package com.datacollector.android;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

/**
 * 安卓用户行为数据收集器主界面
 * 基于CATIA论文实现的上下文感知数据收集系统
 * 负责用户界面展示、权限管理和服务控制
 */
public class AndroidDataCollector extends Activity {
    
    private static final String TAG = "AndroidDataCollector";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    // UI组件
    private Button startButton;
    private Button stopButton;
    private Button getDataButton;
    private TextView statusTextView;
    private TextView dataDisplayTextView;
    
    // 数据收集服务
    private DataCollectionService dataCollectionService;
    private boolean isServiceBound = false;
    
    // 服务连接
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DataCollectionService.DataCollectionBinder binder = 
                (DataCollectionService.DataCollectionBinder) service;
            dataCollectionService = binder.getService();
            isServiceBound = true;
            updateUI();
            updateStatus("数据收集服务已连接");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            dataCollectionService = null;
            isServiceBound = false;
            updateUI();
            updateStatus("数据收集服务已断开");
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 创建简单的UI布局
        createUI();
        
        // 请求权限
        requestPermissions();
    }
    
    /**
     * 创建用户界面
     */
    private void createUI() {
        // 创建垂直线性布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        
        // 标题
        TextView titleTextView = new TextView(this);
        titleTextView.setText("Android数据收集器");
        titleTextView.setTextSize(24);
        titleTextView.setPadding(0, 0, 0, 30);
        layout.addView(titleTextView);
        
        // 状态显示
        statusTextView = new TextView(this);
        statusTextView.setText("状态: 未启动");
        statusTextView.setTextSize(16);
        statusTextView.setPadding(0, 0, 0, 20);
        layout.addView(statusTextView);
        
        // 启动按钮
        startButton = new Button(this);
        startButton.setText("启动数据收集");
        startButton.setOnClickListener(v -> startDataCollection());
        layout.addView(startButton);
        
        // 停止按钮
        stopButton = new Button(this);
        stopButton.setText("停止数据收集");
        stopButton.setOnClickListener(v -> stopDataCollection());
        stopButton.setEnabled(false);
        layout.addView(stopButton);
        
        // 获取数据按钮
        getDataButton = new Button(this);
        getDataButton.setText("获取当前数据");
        getDataButton.setOnClickListener(v -> getCurrentData());
        getDataButton.setEnabled(false);
        layout.addView(getDataButton);
        
        // 数据显示区域
        dataDisplayTextView = new TextView(this);
        dataDisplayTextView.setText("数据将在这里显示...");
        dataDisplayTextView.setTextSize(12);
        dataDisplayTextView.setPadding(0, 20, 0, 0);
        dataDisplayTextView.setMaxLines(20);
        layout.addView(dataDisplayTextView);
        
        setContentView(layout);
    }
    
    /**
     * 请求必要的权限
     */
    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS
        };
        
        // 检查哪些权限需要请求
        java.util.List<String> permissionsToRequest = new java.util.ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissionsToRequest.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        } else {
            updateStatus("所有权限已获取");
        }
    }
    
    /**
     * 启动数据收集
     */
    private void startDataCollection() {
        if (!checkPermissions()) {
            Toast.makeText(this, "请先授予必要权限", Toast.LENGTH_SHORT).show();
            requestPermissions();
            return;
        }
        
        // 启动数据收集服务
        Intent serviceIntent = new Intent(this, DataCollectionService.class);
        startService(serviceIntent);
        
        // 绑定服务
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        updateStatus("正在启动数据收集服务...");
    }
    
    /**
     * 停止数据收集
     */
    private void stopDataCollection() {
        // 解绑服务
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        
        // 停止服务
        Intent serviceIntent = new Intent(this, DataCollectionService.class);
        stopService(serviceIntent);
        
        updateStatus("数据收集已停止");
        updateUI();
    }
    
    /**
     * 获取当前数据
     */
    private void getCurrentData() {
        if (isServiceBound && dataCollectionService != null) {
            new Thread(() -> {
                try {
                    JSONObject data = dataCollectionService.getCompleteContextData();
                    runOnUiThread(() -> {
                        if (data != null) {
                            try {
                                String displayText = data.toString(2); // 格式化显示
                                dataDisplayTextView.setText(displayText);
                                updateStatus("数据获取成功");
                            } catch (Exception e) {
                                dataDisplayTextView.setText("数据格式化错误: " + e.getMessage());
                            }
                        } else {
                            dataDisplayTextView.setText("未获取到数据");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        dataDisplayTextView.setText("获取数据出错: " + e.getMessage());
                        updateStatus("数据获取失败");
                    });
                }
            }).start();
        } else {
            Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 检查权限是否已获取
     */
    private boolean checkPermissions() {
        String[] criticalPermissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        
        for (String permission : criticalPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 更新UI状态
     */
    private void updateUI() {
        runOnUiThread(() -> {
            startButton.setEnabled(!isServiceBound);
            stopButton.setEnabled(isServiceBound);
            getDataButton.setEnabled(isServiceBound);
        });
    }
    
    /**
     * 更新状态显示
     */
    private void updateStatus(String status) {
        runOnUiThread(() -> {
            statusTextView.setText("状态: " + status);
        });
    }
    
    /**
     * 权限请求回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, 
                                         String[] permissions, 
                                         int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            int grantedCount = 0;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    grantedCount++;
                }
            }
            
            if (grantedCount == grantResults.length) {
                updateStatus("所有权限已获取");
                Toast.makeText(this, "权限获取成功", Toast.LENGTH_SHORT).show();
            } else {
                updateStatus("权限获取不完整 (" + grantedCount + "/" + grantResults.length + ")");
                Toast.makeText(this, "部分权限未获取，可能影响功能", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    /**
     * 对外接口：获取完整上下文数据
     * 保持向后兼容性
     */
    public JSONObject getCompleteContextData() {
        if (isServiceBound && dataCollectionService != null) {
            return dataCollectionService.getCompleteContextData();
        }
        return null;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 清理服务连接
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // 如果服务正在运行，尝试重新绑定
        if (!isServiceBound) {
            Intent serviceIntent = new Intent(this, DataCollectionService.class);
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 注意：不在onPause中解绑服务，以保持数据收集的连续性
    }
} 