package com.datacollector.android.collectors;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;

import androidx.core.app.ActivityCompat;

import com.datacollector.android.utils.CollectionConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 提醒事项数据收集器
 * 通过 CalendarContract.Reminders 和 CalendarContract.CalendarAlerts
 * 读取事件提醒设置及近期触发的日历警报，为LLM提供用户待办上下文
 */
public class ReminderDataCollector extends BaseDataCollector<JSONObject> {

    private static final String COLLECTOR_ID = "reminders";

    private static final int DEFAULT_MAX_REMINDERS = 100;
    // 查询过去 3 天到未来 30 天范围内与提醒关联的事件
    private static final long DEFAULT_PAST_DAYS_MS = 3L * 24 * 3600 * 1000;
    private static final long DEFAULT_FUTURE_DAYS_MS = 30L * 24 * 3600 * 1000;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public ReminderDataCollector(Context context) {
        super(context, COLLECTOR_ID);
    }

    @Override
    protected void initializeDefaultConfiguration() {
        super.initializeDefaultConfiguration();
        try {
            configuration.put("past_days", 3);
            configuration.put("future_days", 30);
            configuration.put("max_reminders", DEFAULT_MAX_REMINDERS);
            configuration.put("include_fired_alerts", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) return false;
        if (!CollectionConfig.getInstance(context).getBoolean(
                CollectionConfig.KEY_REMINDER_ENABLED, true)) return false;
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected JSONObject doCollectData() {
        JSONObject result = new JSONObject();

        try {
            JSONArray reminders = collectEventReminders();
            JSONArray alerts = collectFiredAlerts();

            result.put("reminders", reminders);
            result.put("reminder_count", reminders.length());
            result.put("fired_alerts", alerts);
            result.put("fired_alert_count", alerts.length());
            result.put("query_time", dateFormat.format(new Date()));
            result.put("past_days", configuration.optInt("past_days", 3));
            result.put("future_days", configuration.optInt("future_days", 30));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 收集与近期/未来事件关联的 Reminders 记录（提前多少分钟提醒、提醒方式等）
     */
    private JSONArray collectEventReminders() {
        JSONArray out = new JSONArray();
        int maxReminders = configuration.optInt("max_reminders", DEFAULT_MAX_REMINDERS);
        int pastDays = configuration.optInt("past_days", 3);
        int futureDays = configuration.optInt("future_days", 30);

        long now = System.currentTimeMillis();
        long startMs = now - (long) pastDays * 24 * 3600 * 1000;
        long endMs = now + (long) futureDays * 24 * 3600 * 1000;

        String[] instanceProjection = {
                CalendarContract.Instances.EVENT_ID,           // 0
                CalendarContract.Instances.TITLE,              // 1
                CalendarContract.Instances.BEGIN,              // 2
                CalendarContract.Instances.END,                // 3
                CalendarContract.Instances.ALL_DAY,            // 4
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME, // 5
        };

        ContentResolver cr = context.getContentResolver();
        int count = 0;

        // 必须使用 CalendarContract.Instances.query() 静态辅助方法
        try (Cursor instanceCursor = CalendarContract.Instances.query(
                cr, instanceProjection, startMs, endMs)) {

            if (instanceCursor == null) return out;

            while (instanceCursor.moveToNext() && count < maxReminders) {
                long eventId = instanceCursor.getLong(0);
                String title = instanceCursor.getString(1);
                long beginMs = instanceCursor.getLong(2);
                long endMs2 = instanceCursor.getLong(3);
                boolean allDay = instanceCursor.getInt(4) == 1;
                String calendarName = instanceCursor.getString(5);

                // Query reminders for this event
                JSONArray eventReminders = queryRemindersForEvent(cr, eventId);
                if (eventReminders.length() == 0) continue;

                try {
                    JSONObject item = new JSONObject();
                    item.put("event_id", eventId);
                    item.put("event_title", title);
                    item.put("event_begin_timestamp", beginMs);
                    item.put("event_begin_datetime", dateFormat.format(new Date(beginMs)));
                    item.put("event_end_datetime", dateFormat.format(new Date(endMs2)));
                    item.put("all_day", allDay);
                    item.put("calendar_name", calendarName);
                    item.put("is_past", beginMs < now);
                    item.put("minutes_until_event", (beginMs - now) / 60000);
                    item.put("reminder_settings", eventReminders);
                    out.put(item);
                    count++;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return out;
    }

    private JSONArray queryRemindersForEvent(ContentResolver cr, long eventId) {
        JSONArray reminders = new JSONArray();
        String[] projection = {
                CalendarContract.Reminders._ID,
                CalendarContract.Reminders.MINUTES,
                CalendarContract.Reminders.METHOD,
        };
        String selection = CalendarContract.Reminders.EVENT_ID + " = ?";
        String[] selArgs = {String.valueOf(eventId)};

        try (Cursor cursor = cr.query(
                CalendarContract.Reminders.CONTENT_URI,
                projection, selection, selArgs, null)) {

            if (cursor == null) return reminders;
            while (cursor.moveToNext()) {
                try {
                    JSONObject r = new JSONObject();
                    r.put("reminder_id", cursor.getLong(0));
                    r.put("minutes_before", cursor.getInt(1));
                    r.put("method", decodeReminderMethod(cursor.getInt(2)));
                    reminders.put(r);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return reminders;
    }

    /**
     * 收集已触发/待触发的 CalendarAlerts（系统弹出的日历提醒）
     */
    private JSONArray collectFiredAlerts() {
        JSONArray out = new JSONArray();
        if (!configuration.optBoolean("include_fired_alerts", true)) return out;

        long now = System.currentTimeMillis();
        // Look at alerts from the past 24 hours and next hour
        long alertStart = now - 24L * 3600 * 1000;
        long alertEnd = now + 3600 * 1000L;

        String[] projection = {
                CalendarContract.CalendarAlerts.EVENT_ID,
                CalendarContract.CalendarAlerts.TITLE,
                CalendarContract.CalendarAlerts.BEGIN,
                CalendarContract.CalendarAlerts.END,
                CalendarContract.CalendarAlerts.ALARM_TIME,
                CalendarContract.CalendarAlerts.STATE,
                CalendarContract.CalendarAlerts.MINUTES,
        };
        String selection = CalendarContract.CalendarAlerts.ALARM_TIME + " BETWEEN ? AND ?";
        String[] selArgs = {String.valueOf(alertStart), String.valueOf(alertEnd)};

        ContentResolver cr = context.getContentResolver();
        try (Cursor cursor = cr.query(
                CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                projection, selection, selArgs,
                CalendarContract.CalendarAlerts.ALARM_TIME + " DESC")) {

            if (cursor == null) return out;
            while (cursor.moveToNext()) {
                try {
                    JSONObject alert = new JSONObject();
                    alert.put("event_id", cursor.getLong(0));
                    alert.put("title", cursor.getString(1));
                    long beginMs = cursor.getLong(2);
                    alert.put("event_begin_datetime", dateFormat.format(new Date(beginMs)));
                    alert.put("event_end_datetime", dateFormat.format(new Date(cursor.getLong(3))));
                    long alarmMs = cursor.getLong(4);
                    alert.put("alarm_timestamp", alarmMs);
                    alert.put("alarm_datetime", dateFormat.format(new Date(alarmMs)));
                    alert.put("state", decodeAlertState(cursor.getInt(5)));
                    alert.put("minutes_before_event", cursor.getInt(6));
                    alert.put("already_fired", alarmMs <= now);
                    out.put(alert);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return out;
    }

    private String decodeReminderMethod(int method) {
        switch (method) {
            case CalendarContract.Reminders.METHOD_ALERT:   return "alert";
            case CalendarContract.Reminders.METHOD_EMAIL:   return "email";
            case CalendarContract.Reminders.METHOD_SMS:     return "sms";
            case CalendarContract.Reminders.METHOD_ALARM:   return "alarm";
            case CalendarContract.Reminders.METHOD_DEFAULT: return "default";
            default: return "unknown";
        }
    }

    private String decodeAlertState(int state) {
        switch (state) {
            case CalendarContract.CalendarAlerts.STATE_SCHEDULED: return "scheduled";
            case CalendarContract.CalendarAlerts.STATE_FIRED:     return "fired";
            case CalendarContract.CalendarAlerts.STATE_DISMISSED: return "dismissed";
            default: return "unknown";
        }
    }
}
