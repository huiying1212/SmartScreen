package com.datacollector.android.collectors;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.datacollector.android.utils.AppCategoryClassifier;
import com.datacollector.android.utils.AppForegroundTracker;
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
 * 屏幕使用数据收集器，采集：
 *   screenTime    — 今日累计屏幕使用时长
 *   unlockCount   — 过去一小时内解锁次数
 *   currentApp    — 当前前台 App 包名
 *   appCategory   — 当前 App 分类（社交/娱乐/生产力/工具等）
 *   topApps       — 今日各 App 前台使用时长 Top N
 *   sessionTime   — 本次解锁后的使用时长
 */
public class ScreenUsageCollector extends BaseDataCollector<JSONObject> {

    private static final String COLLECTOR_ID = "screen_usage";
    private static final int DEFAULT_TOP_APPS = 10;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private final AppCategoryClassifier categoryClassifier;

    public ScreenUsageCollector(Context context) {
        super(context, COLLECTOR_ID);
        this.categoryClassifier = new AppCategoryClassifier(context);
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

            JSONArray topApps = collectTopApps(usm, todayStartMs, now);
            EventScanResult scan = scanEvents(usm, todayStartMs, now);

            // ── 优先使用 AppForegroundTracker 缓存（30s 精度，OEM 兼容）────
            AppForegroundTracker tracker = AppForegroundTracker.getInstance(context);
            String trackerPkg = tracker.getCurrentPackage();
            if (trackerPkg != null && !trackerPkg.isEmpty()) {
                // Tracker 有新鲜缓存，直接覆盖 UsageEvents 的扫描结果
                scan.foregroundPkg = trackerPkg;
                long trackerSwitchTime = tracker.getSwitchTime();
                if (trackerSwitchTime > 0) {
                    scan.currentOpenSince = trackerSwitchTime;
                    scan.currentOpenMs = now - trackerSwitchTime;
                }
                Log.d(TAG, "Foreground from Tracker: " + trackerPkg
                        + " (open " + scan.currentOpenMs / 1000 + "s)");
            } else {
                Log.d(TAG, "Tracker stale/empty, using UsageEvents result: " + scan.foregroundPkg);
            }

            long foregroundAppTodayMs = 0;
            if (scan.foregroundPkg != null) {
                foregroundAppTodayMs = getAppUsageToday(usm, scan.foregroundPkg, todayStartMs, now);
            }

            // App 分类
            String appCategoryLabel = "未知";
            String appCategoryEn = "other";
            if (scan.foregroundPkg != null) {
                AppCategoryClassifier.AppCategory cat =
                        categoryClassifier.classify(scan.foregroundPkg);
                appCategoryLabel = cat.labelCn;
                appCategoryEn = cat.labelEn;
            }

            result.put("collection_time", dateFormat.format(new Date(now)));
            result.put("today_start", dateFormat.format(new Date(todayStartMs)));

            // screenTime
            result.put("today_screen_time_ms", scan.todayScreenMs);
            result.put("today_screen_time_minutes", scan.todayScreenMs / 60000);
            result.put("today_screen_time_readable", formatDuration(scan.todayScreenMs));

            // unlockCount（过去一小时）
            result.put("unlock_count_last_hour", scan.unlockCountLastHour);

            // session
            result.put("current_session_ms", scan.sessionMs);
            result.put("current_session_minutes", scan.sessionMs / 60000);
            result.put("current_session_readable", formatDuration(scan.sessionMs));

            // currentApp & appCategory
            result.put("foreground_app_package", scan.foregroundPkg);
            result.put("foreground_app_category", appCategoryLabel);
            result.put("foreground_app_category_en", appCategoryEn);
            result.put("foreground_app_today_ms", foregroundAppTodayMs);
            result.put("foreground_app_today_readable", formatDuration(foregroundAppTodayMs));

            // 当前这次打开的持续时长（Tracker 提供时精度 ~30s；fallback 时来自 UsageEvents）
            result.put("foreground_app_current_open_ms", scan.currentOpenMs);
            result.put("foreground_app_current_open_minutes", scan.currentOpenMs / 60000);
            result.put("foreground_app_current_open_readable", formatDuration(scan.currentOpenMs));
            result.put("foreground_app_open_since",
                    scan.currentOpenSince > 0
                            ? dateFormat.format(new Date(scan.currentOpenSince))
                            : null);
            result.put("foreground_source", trackerPkg != null ? "tracker" : "usage_events");

            result.put("top_apps_today", topApps);

        } catch (JSONException e) {
            Log.e(TAG, "doCollectData error", e);
        }

        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private JSONArray collectTopApps(UsageStatsManager usm, long startMs, long endMs) {
        JSONArray out = new JSONArray();
        int topN = configuration.optInt("top_apps_count", DEFAULT_TOP_APPS);
        boolean includeSystem = configuration.optBoolean("include_system_apps", false);

        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startMs, endMs);
        if (statsMap == null || statsMap.isEmpty()) return out;

        List<UsageStats> statsList = new ArrayList<>(statsMap.values());
        Collections.sort(statsList,
                (a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

        int count = 0;
        for (UsageStats stats : statsList) {
            if (count >= topN) break;
            long foregroundMs = stats.getTotalTimeInForeground();
            if (foregroundMs <= 0) continue;

            String pkg = stats.getPackageName();
            if (!includeSystem && isSystemPackage(pkg)) continue;
            if (pkg.equals(context.getPackageName())) continue;

            try {
                AppCategoryClassifier.AppCategory cat = categoryClassifier.classify(pkg);
                JSONObject app = new JSONObject();
                app.put("package_name", pkg);
                app.put("category", cat.labelCn);
                app.put("category_en", cat.labelEn);
                app.put("usage_ms", foregroundMs);
                app.put("usage_minutes", foregroundMs / 60000);
                app.put("usage_readable", formatDuration(foregroundMs));
                out.put(app);
                count++;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return out;
    }

    private static class EventScanResult {
        long todayScreenMs = 0;
        long sessionMs = 0;
        int unlockCountLastHour = 0;
        String foregroundPkg = null;
        long currentOpenMs = 0;
        long currentOpenSince = -1;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private EventScanResult scanEvents(UsageStatsManager usm, long startMs, long endMs) {
        EventScanResult result = new EventScanResult();

        UsageEvents events = usm.queryEvents(startMs, endMs);
        if (events == null) return result;

        long screenOnStart = -1;
        long lastUnlockTime = -1;
        String lastFgPkg = null;
        long lastFgTime = -1;
        long oneHourAgo = endMs - 3600_000L;

        // ACTIVITY_RESUMED (=1) works on Android 10+; MOVE_TO_FOREGROUND (=1)
        // is the same constant but some OEMs stopped emitting it under the old
        // name. We also check ACTIVITY_PAUSED (=2) / MOVE_TO_BACKGROUND (=2).
        final int EVENT_FG = 1;  // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
        final int EVENT_BG = 2;  // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED

        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            long ts = event.getTimeStamp();

            if (type == UsageEvents.Event.SCREEN_INTERACTIVE) {
                screenOnStart = ts;
            } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                if (screenOnStart > 0) {
                    result.todayScreenMs += ts - screenOnStart;
                    screenOnStart = -1;
                }
            } else if (type == UsageEvents.Event.KEYGUARD_HIDDEN) {
                lastUnlockTime = ts;
                if (ts >= oneHourAgo) {
                    result.unlockCountLastHour++;
                }
            } else if (type == EVENT_FG) {
                lastFgPkg = event.getPackageName();
                lastFgTime = ts;
            } else if (type == EVENT_BG) {
                if (lastFgPkg != null && lastFgPkg.equals(event.getPackageName())) {
                    lastFgPkg = null;
                    lastFgTime = -1;
                }
            }
        }

        if (screenOnStart > 0) {
            result.todayScreenMs += endMs - screenOnStart;
        }
        if (result.todayScreenMs == 0) {
            result.todayScreenMs = calcScreenTimeFromForeground(usm, startMs, endMs);
        }

        if (lastUnlockTime > 0) {
            result.sessionMs = endMs - lastUnlockTime;
        }

        if (lastFgPkg != null && !lastFgPkg.equals(context.getPackageName())) {
            result.foregroundPkg = lastFgPkg;
            result.currentOpenSince = lastFgTime;
            result.currentOpenMs = lastFgTime > 0 ? endMs - lastFgTime : 0;
        }

        // Fallback: if event stream did not yield a foreground app (common on
        // many OEM ROMs), infer it from UsageStats — the package with the most
        // recent lastTimeUsed that isn't our own app is likely the current one.
        if (result.foregroundPkg == null) {
            result.foregroundPkg = inferForegroundFromStats(usm, startMs, endMs);
            if (result.foregroundPkg != null) {
                result.currentOpenSince = -1;
                result.currentOpenMs = 0;
            }
        }

        return result;
    }

    /**
     * Fallback: infer the current foreground app from UsageStats when the
     * event stream doesn't contain MOVE_TO_FOREGROUND / ACTIVITY_RESUMED.
     * Picks the non-system, non-self package with the most recent lastTimeUsed.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private String inferForegroundFromStats(UsageStatsManager usm, long startMs, long endMs) {
        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startMs, endMs);
        if (statsMap == null || statsMap.isEmpty()) return null;

        String bestPkg = null;
        long bestLastUsed = 0;
        String myPkg = context.getPackageName();

        for (UsageStats stats : statsMap.values()) {
            String pkg = stats.getPackageName();
            if (pkg.equals(myPkg)) continue;
            if (isSystemPackage(pkg)) continue;
            if (stats.getTotalTimeInForeground() <= 0) continue;

            long lastUsed = stats.getLastTimeUsed();
            if (lastUsed > bestLastUsed) {
                bestLastUsed = lastUsed;
                bestPkg = pkg;
            }
        }

        // Only trust this if lastTimeUsed is very recent (within 2 minutes)
        if (bestPkg != null && (endMs - bestLastUsed) < 2 * 60_000L) {
            Log.d(TAG, "Inferred foreground from UsageStats: " + bestPkg
                    + " (lastUsed " + (endMs - bestLastUsed) / 1000 + "s ago)");
            return bestPkg;
        }

        return null;
    }

    /**
     * Fallback: sum all non-system apps' foreground time as an approximation
     * of total screen-on time. This is used when SCREEN_INTERACTIVE /
     * SCREEN_NON_INTERACTIVE events are not available (common on many OEM ROMs).
     *
     * Note: UsageStats.getTotalTimeInForeground() may overlap between apps
     * (e.g. split-screen), but small overlaps are acceptable compared to the
     * previous bug of only taking the max single-app time.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private long calcScreenTimeFromForeground(UsageStatsManager usm, long startMs, long endMs) {
        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startMs, endMs);
        if (statsMap == null) return 0;
        long total = 0;
        for (UsageStats s : statsMap.values()) {
            if (!isSystemPackage(s.getPackageName())) {
                total += s.getTotalTimeInForeground();
            }
        }
        return total;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private long getAppUsageToday(UsageStatsManager usm, String packageName,
                                  long startMs, long endMs) {
        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startMs, endMs);
        if (statsMap == null) return 0;
        UsageStats stats = statsMap.get(packageName);
        return stats != null ? stats.getTotalTimeInForeground() : 0;
    }

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

    private String formatDuration(long ms) {
        if (ms <= 0) return "0m";
        long totalMin = ms / 60000;
        long hours = totalMin / 60;
        long minutes = totalMin % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
