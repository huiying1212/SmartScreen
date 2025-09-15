package com.datacollector.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.view.View;

/**
 * 服务健康监控界面
 * 展示如何使用AccessibilityServiceMonitor监控无障碍服务状态
 */
public class ServiceHealthActivity extends Activity implements AccessibilityServiceMonitor.ServiceStatusCallback {
    
    private static final String TAG = "ServiceHealthActivity";
    
    private AccessibilityServiceMonitor monitor;
    private TextView statusTextView;
    private TextView diagnosticTextView;
    private Button startMonitorButton;
    private Button stopMonitorButton;
    private Button checkHealthButton;
    private Button openSettingsButton;
    private Button recoveryButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 创建简单的布局
        createLayout();
        
        // 初始化监控器
        monitor = new AccessibilityServiceMonitor(this);
        monitor.setStatusCallback(this);
        
        // 设置按钮点击事件
        setupButtonListeners();
        
        // 初始检查
        updateServiceStatus();
    }
    
    private void createLayout() {
        // 创建垂直线性布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        // 标题
        TextView titleView = new TextView(this);
        titleView.setText("无障碍服务健康监控");
        titleView.setTextSize(20);
        titleView.setPadding(0, 0, 0, 16);
        layout.addView(titleView);
        
        // 状态显示
        statusTextView = new TextView(this);
        statusTextView.setText("状态: 检查中...");
        statusTextView.setTextSize(16);
        statusTextView.setPadding(0, 0, 0, 16);
        layout.addView(statusTextView);
        
        // 诊断信息
        diagnosticTextView = new TextView(this);
        diagnosticTextView.setText("诊断信息将显示在这里");
        diagnosticTextView.setTextSize(12);
        diagnosticTextView.setPadding(0, 0, 0, 16);
        diagnosticTextView.setMaxLines(10);
        layout.addView(diagnosticTextView);
        
        // 按钮
        startMonitorButton = new Button(this);
        startMonitorButton.setText("开始监控");
        layout.addView(startMonitorButton);
        
        stopMonitorButton = new Button(this);
        stopMonitorButton.setText("停止监控");
        layout.addView(stopMonitorButton);
        
        checkHealthButton = new Button(this);
        checkHealthButton.setText("立即检查");
        layout.addView(checkHealthButton);
        
        openSettingsButton = new Button(this);
        openSettingsButton.setText("打开无障碍设置");
        layout.addView(openSettingsButton);
        
        recoveryButton = new Button(this);
        recoveryButton.setText("尝试恢复服务");
        layout.addView(recoveryButton);
        
        setContentView(layout);
    }
    
    private void setupButtonListeners() {
        startMonitorButton.setOnClickListener(v -> {
            monitor.startMonitoring();
            Toast.makeText(this, "已开始监控服务", Toast.LENGTH_SHORT).show();
        });
        
        stopMonitorButton.setOnClickListener(v -> {
            monitor.stopMonitoring();
            Toast.makeText(this, "已停止监控服务", Toast.LENGTH_SHORT).show();
        });
        
        checkHealthButton.setOnClickListener(v -> {
            monitor.performHealthCheck();
            updateDiagnosticInfo();
        });
        
        openSettingsButton.setOnClickListener(v -> {
            monitor.openAccessibilitySettings();
            Toast.makeText(this, "正在打开无障碍设置", Toast.LENGTH_SHORT).show();
        });
        
        recoveryButton.setOnClickListener(v -> {
            monitor.attemptServiceRecovery();
            Toast.makeText(this, "正在尝试恢复服务", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void updateServiceStatus() {
        boolean isEnabled = monitor.isAccessibilityServiceEnabled();
        boolean isConnected = AccessibilityDataService.isServiceConnected();
        
        String status = "服务状态: ";
        if (isEnabled && isConnected) {
            status += "✅ 正常运行";
            statusTextView.setTextColor(0xFF4CAF50); // 绿色
        } else if (isEnabled && !isConnected) {
            status += "⚠️ 已启用但未连接";
            statusTextView.setTextColor(0xFFFF9800); // 橙色
        } else {
            status += "❌ 未启用";
            statusTextView.setTextColor(0xFFFF5722); // 红色
        }
        
        statusTextView.setText(status);
    }
    
    private void updateDiagnosticInfo() {
        String diagnosticInfo = monitor.getDiagnosticInfo();
        diagnosticTextView.setText(diagnosticInfo);
    }
    
    // 实现ServiceStatusCallback接口
    @Override
    public void onServiceHealthy() {
        runOnUiThread(() -> {
            statusTextView.setText("状态: ✅ 服务健康");
            statusTextView.setTextColor(0xFF4CAF50);
            Log.i(TAG, "无障碍服务状态健康");
        });
    }
    
    @Override
    public void onServiceUnhealthy(String reason, String suggestion) {
        runOnUiThread(() -> {
            statusTextView.setText("状态: ⚠️ " + reason);
            statusTextView.setTextColor(0xFFFF9800);
            
            Toast.makeText(this, "建议: " + suggestion, Toast.LENGTH_LONG).show();
            Log.w(TAG, "无障碍服务不健康: " + reason + " - " + suggestion);
        });
    }
    
    @Override
    public void onServiceRecovered() {
        runOnUiThread(() -> {
            statusTextView.setText("状态: ✅ 服务已恢复");
            statusTextView.setTextColor(0xFF4CAF50);
            Toast.makeText(this, "服务已成功恢复!", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "无障碍服务已恢复");
        });
    }
    
    @Override
    public void onServiceError(String error) {
        runOnUiThread(() -> {
            statusTextView.setText("状态: ❌ 服务错误");
            statusTextView.setTextColor(0xFFFF5722);
            Toast.makeText(this, "错误: " + error, Toast.LENGTH_LONG).show();
            Log.e(TAG, "无障碍服务错误: " + error);
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 恢复时更新状态
        updateServiceStatus();
        updateDiagnosticInfo();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理监控器
        if (monitor != null) {
            monitor.stopMonitoring();
        }
    }
} 