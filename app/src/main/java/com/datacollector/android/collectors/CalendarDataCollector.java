package com.datacollector.android.collectors;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.datacollector.android.utils.CollectionConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 日历数据收集器
 * 从系统日历读取近期和即将到来的日程事件，为LLM提供时间上下文
 */
public class CalendarDataCollector extends BaseDataCollector<JSONObject> {

    private static final String COLLECTOR_ID = "calendar";

    // 默认向前查询 7 天历史事件，向后查询 30 天未来事件
    private static final long DEFAULT_PAST_DAYS_MS = 7L * 24 * 3600 * 1000;
    private static final long DEFAULT_FUTURE_DAYS_MS = 30L * 24 * 3600 * 1000;
    private static final int DEFAULT_MAX_EVENTS = 50;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public CalendarDataCollector(Context context) {
        super(context, COLLECTOR_ID);
    }

    @Override
    protected void initializeDefaultConfiguration() {
        super.initializeDefaultConfiguration();
        try {
            configuration.put("past_days", 7);
            configuration.put("future_days", 30);
            configuration.put("max_events", DEFAULT_MAX_EVENTS);
            configuration.put("include_attendees", false);
        } catch (JSONException e) {
            Log.w(COLLECTOR_ID, "Failed to build configuration", e);
        }
    }

    @Override
    public CollectionWeight getWeight() { return CollectionWeight.LIGHT; }

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) return false;
        if (!CollectionConfig.getInstance(context).getBoolean(
                CollectionConfig.KEY_CALENDAR_ENABLED, true)) return false;
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected JSONObject doCollectData() {
        JSONObject result = new JSONObject();
        JSONArray calendars = new JSONArray();
        JSONArray events = new JSONArray();

        try {
            collectCalendars(calendars);
            Log.d(TAG, "Calendars found: " + calendars.length());

            collectEvents(events);
            Log.d(TAG, "Events found: " + events.length());

            result.put("calendars", calendars);
            result.put("calendar_count", calendars.length());
            result.put("events", events);
            result.put("event_count", events.length());
            result.put("query_time", dateFormat.format(new Date()));
            result.put("past_days", configuration.optInt("past_days", 7));
            result.put("future_days", configuration.optInt("future_days", 30));
        } catch (JSONException e) {
            Log.e(TAG, "doCollectData JSONException", e);
        }

        return result;
    }

    private void collectCalendars(JSONArray out) {
        ContentResolver cr = context.getContentResolver();
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.IS_PRIMARY
        };

        try (Cursor cursor = cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, null, null, null)) {

            if (cursor == null) {
                Log.w(TAG, "collectCalendars: cursor is null");
                return;
            }
            Log.d(TAG, "collectCalendars: cursor count=" + cursor.getCount());

            while (cursor.moveToNext()) {
                try {
                    JSONObject cal = new JSONObject();
                    cal.put("id", cursor.getLong(0));
                    cal.put("display_name", cursor.getString(1));
                    cal.put("account_name", cursor.getString(2));
                    cal.put("account_type", cursor.getString(3));
                    cal.put("color", cursor.getInt(4));
                    cal.put("visible", cursor.getInt(5) == 1);
                    cal.put("is_primary", cursor.getInt(6) == 1);
                    Log.d(TAG, "  calendar: id=" + cursor.getLong(0)
                            + " name=" + cursor.getString(1)
                            + " account=" + cursor.getString(2)
                            + " visible=" + (cursor.getInt(5) == 1));
                    out.put(cal);
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse calendar entry", e);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "collectCalendars SecurityException", e);
        }
    }

    private void collectEvents(JSONArray out) {
        int maxEvents = configuration.optInt("max_events", DEFAULT_MAX_EVENTS);
        int pastDays = configuration.optInt("past_days", 7);
        int futureDays = configuration.optInt("future_days", 30);
        boolean includeAttendees = configuration.optBoolean("include_attendees", false);

        long now = System.currentTimeMillis();
        long startMs = now - (long) pastDays * 24 * 3600 * 1000;
        long endMs = now + (long) futureDays * 24 * 3600 * 1000;

        Log.d(TAG, "collectEvents: range [" + dateFormat.format(new Date(startMs))
                + "] ~ [" + dateFormat.format(new Date(endMs)) + "]");

        ContentResolver cr = context.getContentResolver();

        // 直接查询 Events 表，比 Instances.query() 兼容性更好
        String[] projection = {
                CalendarContract.Events._ID,                   // 0
                CalendarContract.Events.TITLE,                 // 1
                CalendarContract.Events.DESCRIPTION,           // 2
                CalendarContract.Events.EVENT_LOCATION,        // 3
                CalendarContract.Events.DTSTART,               // 4
                CalendarContract.Events.DTEND,                 // 5
                CalendarContract.Events.ALL_DAY,               // 6
                CalendarContract.Events.CALENDAR_DISPLAY_NAME, // 7
                CalendarContract.Events.ORGANIZER,             // 8
                CalendarContract.Events.STATUS,                // 9
                CalendarContract.Events.AVAILABILITY,          // 10
                CalendarContract.Events.RRULE,                 // 11
                CalendarContract.Events.DELETED,               // 12
        };

        // 普通事件：DTSTART 在时间窗口内
        // 循环事件：RRULE 非空且 DTSTART <= endMs（循环事件本身不设 DTEND，另行处理）
        String selection =
                CalendarContract.Events.DELETED + " = 0 AND ("
                + "(" + CalendarContract.Events.DTSTART + " >= ? AND "
                +       CalendarContract.Events.DTSTART + " <= ?) OR "
                + "(" + CalendarContract.Events.RRULE + " IS NOT NULL AND "
                +       CalendarContract.Events.DTSTART + " <= ?)"
                + ")";
        String[] selArgs = {
                String.valueOf(startMs),
                String.valueOf(endMs),
                String.valueOf(endMs)
        };

        try (Cursor cursor = cr.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selArgs,
                CalendarContract.Events.DTSTART + " ASC")) {

            if (cursor == null) {
                Log.w(TAG, "collectEvents: cursor is null — ContentProvider may be unavailable");
                return;
            }
            Log.d(TAG, "collectEvents: cursor.getCount()=" + cursor.getCount());

            int count = 0;
            while (cursor.moveToNext() && count < maxEvents) {
                try {
                    JSONObject event = new JSONObject();
                    long eventId = cursor.getLong(0);
                    event.put("event_id", eventId);
                    event.put("title", cursor.getString(1));
                    event.put("description", truncate(cursor.getString(2), 500));
                    event.put("location", cursor.getString(3));

                    long beginMs = cursor.getLong(4);
                    // DTEND 对循环事件可能为 null，补默认值 1 小时
                    long endMs2 = cursor.isNull(5) ? beginMs + 3600_000L : cursor.getLong(5);
                    event.put("begin_timestamp", beginMs);
                    event.put("end_timestamp", endMs2);
                    event.put("begin_datetime", dateFormat.format(new Date(beginMs)));
                    event.put("end_datetime", dateFormat.format(new Date(endMs2)));
                    event.put("duration_minutes", (endMs2 - beginMs) / 60000);
                    event.put("is_past", beginMs < now);
                    event.put("minutes_until", (beginMs - now) / 60000);

                    event.put("all_day", cursor.getInt(6) == 1);
                    event.put("calendar_name", cursor.getString(7));
                    event.put("organizer", cursor.getString(8));
                    event.put("status", decodeEventStatus(cursor.getInt(9)));
                    event.put("availability", decodeAvailability(cursor.getInt(10)));

                    String rrule = cursor.getString(11);
                    event.put("is_recurring", rrule != null && !rrule.isEmpty());

                    Log.d(TAG, "  event[" + count + "]: id=" + eventId
                            + " title=" + cursor.getString(1)
                            + " begin=" + dateFormat.format(new Date(beginMs)));

                    if (includeAttendees) {
                        event.put("attendees", collectEventAttendees(cr, eventId));
                    }

                    out.put(event);
                    count++;
                } catch (JSONException e) {
                    Log.e(TAG, "collectEvents: JSONException on event row", e);
                }
            }
            Log.d(TAG, "collectEvents: finished, collected " + out.length() + " events");

        } catch (SecurityException e) {
            Log.e(TAG, "collectEvents: SecurityException", e);
        }
    }

    private JSONArray collectEventAttendees(ContentResolver cr, long eventId) {
        JSONArray attendees = new JSONArray();
        String[] projection = {
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_STATUS
        };
        String selection = CalendarContract.Attendees.EVENT_ID + " = ?";
        String[] selArgs = {String.valueOf(eventId)};

        try (Cursor cursor = cr.query(
                CalendarContract.Attendees.CONTENT_URI,
                projection, selection, selArgs, null)) {

            if (cursor == null) return attendees;
            while (cursor.moveToNext()) {
                try {
                    JSONObject attendee = new JSONObject();
                    attendee.put("name", cursor.getString(0));
                    attendee.put("email", cursor.getString(1));
                    attendee.put("status", decodeAttendeeStatus(cursor.getInt(2)));
                    attendees.put(attendee);
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse attendee", e);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "collectAttendees SecurityException", e);
        }
        return attendees;
    }

    private String decodeEventStatus(int status) {
        switch (status) {
            case CalendarContract.Events.STATUS_TENTATIVE: return "tentative";
            case CalendarContract.Events.STATUS_CONFIRMED:  return "confirmed";
            case CalendarContract.Events.STATUS_CANCELED:   return "canceled";
            default: return "unknown";
        }
    }

    private String decodeAvailability(int avail) {
        switch (avail) {
            case CalendarContract.Events.AVAILABILITY_BUSY:      return "busy";
            case CalendarContract.Events.AVAILABILITY_FREE:      return "free";
            case CalendarContract.Events.AVAILABILITY_TENTATIVE: return "tentative";
            default: return "unknown";
        }
    }

    private String decodeAttendeeStatus(int status) {
        switch (status) {
            case CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED:  return "accepted";
            case CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED:  return "declined";
            case CalendarContract.Attendees.ATTENDEE_STATUS_INVITED:   return "invited";
            case CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE: return "tentative";
            default: return "none";
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
