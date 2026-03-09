package com.datacollector.android.collectors;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.datacollector.android.utils.CollectionConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 屏幕使用时长收集器
 *
 * 收集三个维度的数据：
 * 1. 今日屏幕总使用时长（亮屏时间）
 * 2. 本次解锁后的使用时长（当前 session）
 * 3. 今日各 App 的前台使用时长（Top N）
 *
 * 依赖：PACKAGE_USAGE_STATS（特殊权限，需用户在「设置-有权查看使用情况的应用」手动开启）
 */
public class ScreenUsageCollector extends BaseDataCollector<JSONObject> {

    private static final String COLLECTOR_ID = "screen_usage";
    private static final int DEFAULT_TOP_APPS = 10;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public ScreenUsageCollector(Context context) {
        super(context, COLLECTOR_ID);
    }

    @Override
    protected void initializeDefaultConfiguration() {
        super.initializeDefaultConfiguration();
        try {
            configuration.put("top_apps_count", DEFAULT_TOP_APPS);
            configuration.put("include_system_apps", false);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) return false;
        if (!CollectionConfig.getInstance(context).getBoolean(
                CollectionConfig.KEY_SCREEN_USAGE_ENABLED, true)) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return false;
        return hasUsageStatsPermission();
    }

    /**
     * 检查 PACKAGE_USAGE_STATS 权限（该权限不能通过 checkSelfPermission 判断，
     * 需要通过 AppOpsManager 判断）
     */
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    protected JSONObject doCollectData() {
        JSONObject result = new JSONObject();

        try {
            long now = System.currentTimeMillis();

            // 今天零点时间戳
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStartMs = cal.getTimeInMillis();

            UsageStatsManager usm = (UsageStatsManager)
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) {
                result.put("error", "UsageStatsManager unavailable");
                return result;
            }

            // ── 1. 今日各 App 前台使用时长 ──────────────────────────────
            JSONArray topApps = collectTopApps(usm, todayStartMs, now);

            // ── 2-4. 单次遍历 UsageEvents，同时计算：
            //    屏幕总时长 / 本次 session / 当前 App 及其本次打开时长 ──────
            EventScanResult scan = scanEvents(usm, todayStartMs, now);

            // ── 5. 当前前台 App 今日累计使用时长 ─────────────────────────
            long foregroundAppTodayMs = 0;
            if (scan.foregroundPkg != null) {
                foregroundAppTodayMs = getAppUsageToday(usm, scan.foregroundPkg, todayStartMs, now);
            }

            result.put("collection_time", dateFormat.format(new Date(now)));
            result.put("today_start", dateFormat.format(new Date(todayStartMs)));

            result.put("today_screen_time_ms", scan.todayScreenMs);
            result.put("today_screen_time_minutes", scan.todayScreenMs / 60000);
            result.put("today_screen_time_readable", formatDuration(scan.todayScreenMs));

            result.put("current_session_ms", scan.sessionMs);
            result.put("current_session_minutes", scan.sessionMs / 60000);
            result.put("current_session_readable", formatDuration(scan.sessionMs));

            result.put("foreground_app_package", scan.foregroundPkg);
            result.put("foreground_app_today_ms", foregroundAppTodayMs);
            result.put("foreground_app_today_minutes", foregroundAppTodayMs / 60000);
            result.put("foreground_app_today_readable", formatDuration(foregroundAppTodayMs));

            // 当前这次打开 App 的持续时长
            result.put("foreground_app_current_open_ms", scan.currentOpenMs);
            result.put("foreground_app_current_open_minutes", scan.currentOpenMs / 60000);
            result.put("foreground_app_current_open_readable", formatDuration(scan.currentOpenMs));
            result.put("foreground_app_open_since",
                    scan.currentOpenSince > 0
                            ? dateFormat.format(new Date(scan.currentOpenSince))
                            : null);

            result.put("top_apps_today", topApps);

            Log.d(TAG, "screen_usage collected:"
                    + " today=" + formatDuration(scan.todayScreenMs)
                    + " session=" + formatDuration(scan.sessionMs)
                    + " foreground=" + scan.foregroundPkg
                    + " current_open=" + formatDuration(scan.currentOpenMs));

        } catch (JSONException e) {
            Log.e(TAG, "doCollectData error", e);
        }

        return result;
    }

    /**
     * 收集今日前台使用时长 Top N 的 App
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private JSONArray collectTopApps(UsageStatsManager usm, long startMs, long endMs) {
        JSONArray out = new JSONArray();
        int topN = configuration.optInt("top_apps_count", DEFAULT_TOP_APPS);
        boolean includeSystem = configuration.optBoolean("include_system_apps", false);

        Map<String, UsageStats> statsMap =
                usm.queryAndAggregateUsageStats(startMs, endMs);
        if (statsMap == null || statsMap.isEmpty()) {
            Log.d(TAG, "collectTopApps: no usage stats returned");
            return out;
        }

        List<UsageStats> statsList = new ArrayList<>(statsMap.values());
        // 按前台时间降序排列
        Collections.sort(statsList,
                (a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

        int count = 0;
        for (UsageStats stats : statsList) {
            if (count >= topN) break;
            long foregroundMs = stats.getTotalTimeInForeground();
            if (foregroundMs <= 0) continue;

            String pkg = stats.getPackageName();
            if (!includeSystem && isSystemPackage(pkg)) continue;
            // 排除自身
            if (pkg.equals(context.getPackageName())) continue;

            try {
                JSONObject app = new JSONObject();
                app.put("package_name", pkg);
                app.put("usage_ms", foregroundMs);
                app.put("usage_minutes", foregroundMs / 60000);
                app.put("usage_readable", formatDuration(foregroundMs));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    app.put("last_time_visible", dateFormat.format(
                            new Date(stats.getLastTimeVisible())));
                } else {
                    app.put("last_time_used", dateFormat.format(
                            new Date(stats.getLastTimeUsed())));
                }
                out.put(app);
                count++;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "collectTopApps: " + out.length() + " apps");
        return out;
    }

    /** 单次 UsageEvents 扫描的结果 */
    private static class EventScanResult {
        long todayScreenMs = 0;   // 今日总亮屏时长
        long sessionMs = 0;       // 本次解锁后时长
        String foregroundPkg = null;    // 当前前台 App 包名
        long currentOpenMs = 0;         // 当前这次打开 App 的持续时长
        long currentOpenSince = -1;     // 当前这次打开的起始时间戳
    }

    /**
     * 单次遍历 UsageEvents，同时计算：
     *  - 今日总亮屏时长
     *  - 本次解锁 session 时长
     *  - 当前前台 App 包名
     *  - 当前这次打开 App 的持续时长（MOVE_TO_FOREGROUND → 现在）
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private EventScanResult scanEvents(UsageStatsManager usm, long startMs, long endMs) {
        EventScanResult result = new EventScanResult();

        UsageEvents events = usm.queryEvents(startMs, endMs);
        if (events == null) return result;

        long screenOnStart = -1;
        long lastUnlockTime = -1;
        // 追踪当前 App：记录其最近一次进入前台的时间
        String lastFgPkg = null;
        long lastFgTime = -1;

        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            long ts = event.getTimeStamp();

            switch (type) {
                case UsageEvents.Event.SCREEN_INTERACTIVE:
                    screenOnStart = ts;
                    break;

                case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                    if (screenOnStart > 0) {
                        result.todayScreenMs += ts - screenOnStart;
                        screenOnStart = -1;
                    }
                    break;

                case UsageEvents.Event.KEYGUARD_HIDDEN:
                    // 解锁成功
                    lastUnlockTime = ts;
                    break;

                case UsageEvents.Event.MOVE_TO_FOREGROUND:
                    // 新 App 进入前台：记录包名和时间
                    lastFgPkg = event.getPackageName();
                    lastFgTime = ts;
                    break;

                case UsageEvents.Event.MOVE_TO_BACKGROUND:
                    // 如果离开前台的是我们正在追踪的 App，清除（表示它已不在前台）
                    if (lastFgPkg != null && lastFgPkg.equals(event.getPackageName())) {
                        lastFgPkg = null;
                        lastFgTime = -1;
                    }
                    break;
            }
        }

        // 屏幕仍亮着
        if (screenOnStart > 0) {
            result.todayScreenMs += endMs - screenOnStart;
        }
        // 回退：ROM 不暴露屏幕事件时用前台时间近似
        if (result.todayScreenMs == 0) {
            result.todayScreenMs = calcScreenTimeFromForeground(usm, startMs, endMs);
        }

        // 本次 session
        if (lastUnlockTime > 0) {
            result.sessionMs = endMs - lastUnlockTime;
            Log.d(TAG, "session: unlocked at " + dateFormat.format(new Date(lastUnlockTime))
                    + " duration=" + formatDuration(result.sessionMs));
        }

        // 当前前台 App（lastFgPkg != null 意味着它还在前台）
        if (lastFgPkg != null && !lastFgPkg.equals(context.getPackageName())) {
            result.foregroundPkg = lastFgPkg;
            result.currentOpenSince = lastFgTime;
            result.currentOpenMs = lastFgTime > 0 ? endMs - lastFgTime : 0;
            Log.d(TAG, "foreground app: " + lastFgPkg
                    + " open since=" + (lastFgTime > 0 ? dateFormat.format(new Date(lastFgTime)) : "unknown")
                    + " duration=" + formatDuration(result.currentOpenMs));
        }

        return result;
    }

    /**
     * 回退方案：用各 App 前台时间的最大值近似屏幕使用时长
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private long calcScreenTimeFromForeground(UsageStatsManager usm, long startMs, long endMs) {
        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startMs, endMs);
        if (statsMap == null) return 0;
        long total = 0;
        for (UsageStats s : statsMap.values()) {
            if (!isSystemPackage(s.getPackageName())) {
                total = Math.max(total, s.getTotalTimeInForeground());
            }
        }
        return total;
    }

    /**
     * 获取指定 App 今日的前台使用时长
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private long getAppUsageToday(UsageStatsManager usm, String packageName,
            long startMs, long endMs) {
        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startMs, endMs);
        if (statsMap == null) return 0;
        UsageStats stats = statsMap.get(packageName);
        return stats != null ? stats.getTotalTimeInForeground() : 0;
    }

    /**
     * 粗略判断是否为系统包（launcher、设置、系统 UI 等），过滤掉噪音数据
     */
    private boolean isSystemPackage(String packageName) {
        if (packageName == null) return true;
        return packageName.startsWith("com.android.")
                || packageName.startsWith("android.")
                || packageName.startsWith("com.google.android.inputmethod")
                || packageName.equals("android")
                || packageName.equals("com.samsung.android.lool")
                || packageName.contains(".launcher")
                || packageName.contains(".systemui");
    }

    /**
     * 将毫秒格式化为 "Xh Ym" 的可读字符串
     */
    private String formatDuration(long ms) {
        if (ms <= 0) return "0m";
        long totalMin = ms / 60000;
        long hours = totalMin / 60;
        long minutes = totalMin % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
