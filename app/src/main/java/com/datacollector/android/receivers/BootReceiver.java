package com.datacollector.android.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.datacollector.android.services.DataCollectionService;
import com.datacollector.android.utils.CollectionConfig;

/**
 * 开机自启动接收器 —— 设备重启后自动恢复 DataCollectionService。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        CollectionConfig config = CollectionConfig.getInstance(context);
        if (!config.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true)) {
            Log.i(TAG, "RI4SU disabled, skipping auto-start after boot");
            return;
        }

        Log.i(TAG, "Boot/update completed — starting DataCollectionService");
        Intent serviceIntent = new Intent(context, DataCollectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
