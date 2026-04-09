package com.datacollector.android.utils;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.datacollector.android.R;
import com.datacollector.android.activities.ESMSurveyActivity;

import java.util.Calendar;
import java.util.Random;

/**
 * ESM（经验取样法）调度器。
 *
 * 每天在 9:00–21:00 之间随机安排 1 次问卷提醒。
 * 通过 AlarmManager 精确触发，弹出通知引导用户打开问卷。
 *
 * 使用方式：
 * <pre>
 *   ESMScheduler.scheduleToday(context);
 * </pre>
 */
public class ESMScheduler extends BroadcastReceiver {

    private static final String TAG = "ESMScheduler";
    private static final String CHANNEL_ID = "ESMChannel";
    private static final int NOTIFICATION_ID_BASE = 3000;
    private static final int ESM_COUNT_PER_DAY = 1;

    // 问卷时间窗口：9:00 - 21:00
    private static final int WINDOW_START_HOUR = 9;
    private static final int WINDOW_END_HOUR = 21;
    // 两次问卷之间最少间隔（分钟）
    private static final int MIN_GAP_MINUTES = 120;

    @Override
    public void onReceive(Context context, Intent intent) {
        int slot = intent != null ? intent.getIntExtra("esm_slot", -1) : -1;
        Log.i(TAG, "ESM alarm triggered (slot=" + slot + ")");
        createNotificationChannel(context);
        showNotification(context, slot);
    }

    /**
     * 为今天安排 3 次随机 ESM 提醒。
     * 应在每天首次启动 App 或 DataCollectionService 启动时调用。
     */
    public static void scheduleToday(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Random random = new Random();
        int windowMinutes = (WINDOW_END_HOUR - WINDOW_START_HOUR) * 60; // 720 min

        // 生成随机时间点
        int[] offsets = generateRandomOffsets(random, windowMinutes, ESM_COUNT_PER_DAY, MIN_GAP_MINUTES);

        for (int i = 0; i < ESM_COUNT_PER_DAY; i++) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, WINDOW_START_HOUR);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.MINUTE, offsets[i]);

            // 如果时间已过，跳过
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                Log.d(TAG, "ESM slot " + i + " already passed, skipping");
                continue;
            }

            Intent intent = new Intent(context, ESMScheduler.class);
            intent.putExtra("esm_slot", i);
            PendingIntent pi = PendingIntent.getBroadcast(context,
                    NOTIFICATION_ID_BASE + i, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: check SCHEDULE_EXACT_ALARM permission at runtime
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                } else {
                    // Fallback to inexact alarm if permission not granted
                    Log.w(TAG, "SCHEDULE_EXACT_ALARM not granted, using inexact alarm for slot " + i);
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }

            Log.i(TAG, "ESM slot " + i + " scheduled at " +
                    String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)));
        }
    }

    private static int[] generateRandomOffsets(Random random, int windowMinutes, int count, int minGap) {
        int[] offsets = new int[count];
        for (int attempt = 0; attempt < 100; attempt++) {
            for (int i = 0; i < count; i++) {
                offsets[i] = random.nextInt(windowMinutes);
            }
            java.util.Arrays.sort(offsets);

            boolean valid = true;
            for (int i = 1; i < count; i++) {
                if (offsets[i] - offsets[i - 1] < minGap) {
                    valid = false;
                    break;
                }
            }
            if (valid) return offsets;
        }
        // fallback: 均匀分布
        for (int i = 0; i < count; i++) {
            offsets[i] = (windowMinutes / (count + 1)) * (i + 1);
        }
        return offsets;
    }

    private void showNotification(Context context, int slot) {
        Intent intent = new Intent(context, ESMSurveyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int requestCode = (slot >= 0 ? (NOTIFICATION_ID_BASE + slot) : NOTIFICATION_ID_BASE);
        PendingIntent pi = PendingIntent.getActivity(context, requestCode,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("RI4SU 快速反馈")
                .setContentText("花 30 秒告诉我们你的使用感受吧")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(requestCode, builder.build());
        }
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "ESM 问卷提醒", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("经验取样法问卷提醒");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
