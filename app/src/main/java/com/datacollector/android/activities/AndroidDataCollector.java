package com.datacollector.android.activities;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.datacollector.android.R;
import com.datacollector.android.managers.DataCollectorManager;
import com.datacollector.android.services.DataCollectionService;

import org.json.JSONObject;

/**
 * 数据收集调试/管理界面。
 * 提供：启停服务、查看采集数据、查看收集器状态、切换自动分析。
 */
public class AndroidDataCollector extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private Button startButton, stopButton, getDataButton, statusButton;
    private Button aiAnalysisToggleButton;
    private TextView statusTextView, dataDisplayTextView;

    private DataCollectionService dataCollectionService;
    private boolean isServiceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DataCollectionService.DataCollectionBinder binder =
                    (DataCollectionService.DataCollectionBinder) service;
            dataCollectionService = binder.getService();
            isServiceBound = true;
            setButtonsEnabled(true);
            updateStatus("服务已连接");
            updateAiToggleText();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            dataCollectionService = null;
            isServiceBound = false;
            setButtonsEnabled(false);
            updateStatus("服务已断开");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createUI();
        requestPermissions();
    }

    private void createUI() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundColor(0xFF1A1A2E);

        TextView title = new TextView(this);
        title.setText("数据收集管理");
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        statusTextView = new TextView(this);
        statusTextView.setText("状态: 未连接");
        statusTextView.setTextSize(14);
        statusTextView.setTextColor(0xFFB0B0B0);
        statusTextView.setPadding(0, 0, 0, 20);
        layout.addView(statusTextView);

        startButton = addButton(layout, "启动数据收集", v -> startDataCollection());
        stopButton = addButton(layout, "停止数据收集", v -> stopDataCollection());
        getDataButton = addButton(layout, "获取当前数据", v -> getCurrentData());
        statusButton = addButton(layout, "查看收集器状态", v -> getCollectorStatus());
        aiAnalysisToggleButton = addButton(layout, "AI自动分析: 检查中...", v -> toggleAiAnalysis());

        stopButton.setEnabled(false);
        getDataButton.setEnabled(false);
        statusButton.setEnabled(false);
        aiAnalysisToggleButton.setEnabled(false);

        dataDisplayTextView = new TextView(this);
        dataDisplayTextView.setText("数据将在这里显示...");
        dataDisplayTextView.setTextSize(11);
        dataDisplayTextView.setPadding(16, 16, 16, 16);
        dataDisplayTextView.setMaxLines(Integer.MAX_VALUE);
        dataDisplayTextView.setTextIsSelectable(true);
        dataDisplayTextView.setTextColor(0xFFCCCCCC);
        dataDisplayTextView.setBackgroundColor(0xFF222233);
        dataDisplayTextView.setTypeface(android.graphics.Typeface.MONOSPACE);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout.LayoutParams scrollParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.topMargin = 20;
        scrollView.setLayoutParams(scrollParams);
        scrollView.addView(dataDisplayTextView);
        layout.addView(scrollView);

        setContentView(layout);
    }

    private Button addButton(android.widget.LinearLayout parent, String text,
                             android.view.View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setOnClickListener(listener);
        android.widget.LinearLayout.LayoutParams p =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = 8;
        btn.setLayoutParams(p);
        parent.addView(btn);
        return btn;
    }

    private void setButtonsEnabled(boolean enabled) {
        stopButton.setEnabled(enabled);
        getDataButton.setEnabled(enabled);
        statusButton.setEnabled(enabled);
        aiAnalysisToggleButton.setEnabled(enabled);
        startButton.setEnabled(!enabled);
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.BODY_SENSORS
        };

        java.util.List<String> needed = new java.util.ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            updateStatus("所有权限已获取");
        }
    }

    private void startDataCollection() {
        Intent intent = new Intent(this, DataCollectionService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        updateStatus("正在启动...");
    }

    private void stopDataCollection() {
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        stopService(new Intent(this, DataCollectionService.class));
        updateStatus("已停止");
        setButtonsEnabled(false);
    }

    private void getCurrentData() {
        if (!isServiceBound || dataCollectionService == null) return;

        new Thread(() -> {
            try {
                JSONObject data = dataCollectionService.getCompleteContextData();
                runOnUiThread(() -> {
                    if (data != null) {
                        try {
                            dataDisplayTextView.setText(data.toString(2));
                            updateStatus("数据获取成功");
                        } catch (Exception e) {
                            dataDisplayTextView.setText("格式化错误: " + e.getMessage());
                        }
                    } else {
                        dataDisplayTextView.setText("未获取到数据");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> dataDisplayTextView.setText("错误: " + e.getMessage()));
            }
        }).start();
    }

    private void getCollectorStatus() {
        if (!isServiceBound || dataCollectionService == null) return;

        new Thread(() -> {
            try {
                DataCollectorManager mgr = dataCollectionService.getCollectorManager();
                if (mgr == null) return;
                JSONObject status = mgr.getCollectorsStatus();
                String stats = dataCollectionService.getCollectionStatsSummary();

                runOnUiThread(() -> {
                    try {
                        String text = "收集器状态:\n" + status.toString(2)
                                + "\n\n统计:\n" + stats;
                        dataDisplayTextView.setText(text);
                        updateStatus("状态获取成功");
                    } catch (Exception e) {
                        dataDisplayTextView.setText("错误: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> dataDisplayTextView.setText("错误: " + e.getMessage()));
            }
        }).start();
    }

    private void toggleAiAnalysis() {
        if (!isServiceBound || dataCollectionService == null) return;
        boolean current = dataCollectionService.isAutoAnalysisEnabled();
        dataCollectionService.setAutoAnalysisEnabled(!current);
        updateAiToggleText();
        Toast.makeText(this, "AI自动分析已" + (!current ? "开启" : "关闭"),
                Toast.LENGTH_SHORT).show();
    }

    private void updateAiToggleText() {
        if (isServiceBound && dataCollectionService != null) {
            boolean on = dataCollectionService.isAutoAnalysisEnabled();
            aiAnalysisToggleButton.setText("AI自动分析: " + (on ? "开启" : "关闭"));
        }
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> statusTextView.setText("状态: " + status));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isServiceBound) {
            Intent intent = new Intent(this, DataCollectionService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            int granted = 0;
            for (int r : results) { if (r == PackageManager.PERMISSION_GRANTED) granted++; }
            updateStatus(granted == results.length ? "所有权限已获取"
                    : "权限: " + granted + "/" + results.length);
        }
    }
}
