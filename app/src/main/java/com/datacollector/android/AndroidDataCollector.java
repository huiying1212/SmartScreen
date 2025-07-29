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

import com.datacollector.android.managers.DataCollectorManager;

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
    private Button statusButton;  // 添加状态按钮引用
    private Button geminiButton;  // 添加Gemini API按钮
    private Button deepSeekButton;  // 添加DeepSeek API按钮
    private TextView statusTextView;
    private TextView dataDisplayTextView;
    
    // 数据收集服务
    private DataCollectionService dataCollectionService;
    private boolean isServiceBound = false;
    
    // Gemini API客户端
    private GeminiApiClient geminiApiClient;
    
    // DeepSeek API客户端
    private DeepSeekApiClient deepSeekApiClient;
    
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
        
        // 初始化Gemini API客户端
        geminiApiClient = new GeminiApiClient(this);
        
        // 初始化DeepSeek API客户端
        deepSeekApiClient = new DeepSeekApiClient(this);
        
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
        titleTextView.setText("Android数据收集器 (重构版)");
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
        
        // 查看状态按钮
        statusButton = new Button(this);
        statusButton.setText("查看收集器状态");
        statusButton.setOnClickListener(v -> getCollectorStatus());
        statusButton.setEnabled(false);
        layout.addView(statusButton);
        
        // Gemini API按钮
        geminiButton = new Button(this);
        geminiButton.setText("调用Gemini AI分析");
        geminiButton.setOnClickListener(v -> callGeminiApi());
        geminiButton.setEnabled(false);
        layout.addView(geminiButton);
        
        // DeepSeek API按钮
        deepSeekButton = new Button(this);
        deepSeekButton.setText("调用DeepSeek AI分析");
        deepSeekButton.setOnClickListener(v -> callDeepSeekApi());
        deepSeekButton.setEnabled(false);
        layout.addView(deepSeekButton);
        
        // 数据显示区域
        dataDisplayTextView = new TextView(this);
        dataDisplayTextView.setText("数据将在这里显示...");
        dataDisplayTextView.setTextSize(12);
        dataDisplayTextView.setPadding(16, 16, 16, 16); // 增加内边距
        dataDisplayTextView.setMaxLines(Integer.MAX_VALUE); // 移除行数限制
        dataDisplayTextView.setTextIsSelectable(true); // 允许选择文本
        dataDisplayTextView.setBackgroundColor(0xFFF5F5F5); // 设置浅灰色背景
        dataDisplayTextView.setTypeface(android.graphics.Typeface.MONOSPACE); // 使用等宽字体
        
        // 添加ScrollView包装数据显示区域
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout.LayoutParams scrollParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f  // 权重为1，占用剩余空间
        );
        scrollParams.topMargin = 20; // 添加上边距
        scrollView.setLayoutParams(scrollParams);
        scrollView.addView(dataDisplayTextView);
        layout.addView(scrollView);
        
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
     * 获取收集器状态信息
     */
    private void getCollectorStatus() {
        if (isServiceBound && dataCollectionService != null) {
            new Thread(() -> {
                try {
                    // 通过Service获取DataCollectorManager - 修复var兼容性问题
                    DataCollectorManager collectorManager = dataCollectionService.getCollectorManager();
                    if (collectorManager != null) {
                        JSONObject status = collectorManager.getCollectorsStatus();
                        runOnUiThread(() -> {
                            if (status != null) {
                                try {
                                    String statusText = "收集器状态信息:\n" + status.toString(2);
                                    dataDisplayTextView.setText(statusText);
                                    updateStatus("状态获取成功");
                                } catch (Exception e) {
                                    dataDisplayTextView.setText("状态格式化错误: " + e.getMessage());
                                }
                            } else {
                                dataDisplayTextView.setText("未获取到状态信息");
                            }
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        dataDisplayTextView.setText("获取状态出错: " + e.getMessage());
                        updateStatus("状态获取失败");
                    });
                }
            }).start();
        } else {
            Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 调用Gemini API进行数据分析
     */
    private void callGeminiApi() {
        if (!isServiceBound || dataCollectionService == null) {
            Toast.makeText(this, "数据收集服务未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        
        updateStatus("正在调用Gemini API分析...");
        dataDisplayTextView.setText("正在分析数据，请稍候...");
        
        // 禁用按钮防止重复点击
        geminiButton.setEnabled(false);
        
        geminiApiClient.callGeminiWithLatestData(new GeminiApiClient.GeminiApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    try {
                        // 尝试格式化JSON响应
                        JSONObject responseJson = new JSONObject(response);
                        String formattedResponse = "Gemini AI分析结果:\n" + responseJson.toString(2);
                        dataDisplayTextView.setText(formattedResponse);
                        updateStatus("Gemini API调用成功");
                    } catch (Exception e) {
                        // 如果不是JSON格式，直接显示原始响应
                        String displayText = "Gemini AI分析结果:\n" + response;
                        dataDisplayTextView.setText(displayText);
                        updateStatus("Gemini API调用成功");
                    }
                    geminiButton.setEnabled(true);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    dataDisplayTextView.setText("Gemini API调用失败:\n" + error);
                    updateStatus("Gemini API调用失败");
                    geminiButton.setEnabled(true);
                });
            }
        });
    }
    
    /**
     * 调用DeepSeek API进行数据分析
     */
    private void callDeepSeekApi() {
        if (!isServiceBound || dataCollectionService == null) {
            Toast.makeText(this, "数据收集服务未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        updateStatus("正在调用DeepSeek API分析...");
        dataDisplayTextView.setText("正在分析数据，请稍候...");

        // 禁用按钮防止重复点击
        deepSeekButton.setEnabled(false);

        deepSeekApiClient.callDeepSeekWithLatestData(new DeepSeekApiClient.DeepSeekApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    try {
                        // 尝试格式化JSON响应
                        JSONObject responseJson = new JSONObject(response);
                        String formattedResponse = "DeepSeek AI分析结果:\n" + responseJson.toString(2);
                        dataDisplayTextView.setText(formattedResponse);
                        updateStatus("DeepSeek API调用成功");
                    } catch (Exception e) {
                        // 如果不是JSON格式，直接显示原始响应
                        String displayText = "DeepSeek AI分析结果:\n" + response;
                        dataDisplayTextView.setText(displayText);
                        updateStatus("DeepSeek API调用成功");
                    }
                    deepSeekButton.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    dataDisplayTextView.setText("DeepSeek API调用失败:\n" + error);
                    updateStatus("DeepSeek API调用失败");
                    deepSeekButton.setEnabled(true);
                });
            }
        });
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
            statusButton.setEnabled(isServiceBound);
            geminiButton.setEnabled(isServiceBound); // 更新Gemini按钮状态
            deepSeekButton.setEnabled(isServiceBound); // 更新DeepSeek按钮状态
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