package com.datacollector.android.utils;

import android.content.Context;
import android.util.Log;

/**
 * 全局前台 App 状态缓存（单例）。
 *
 * 解决的问题：UsageStatsManager 事件流在 OEM ROM 上常常缺失
 * MOVE_TO_FOREGROUND 事件，导致每次采集时无法可靠判断当前前台 App
 * 和用户在该 App 上的停留时长。
 *
 * 工作方式：
 *   - FloatingOverlayService 每 30 秒轮询一次 ScreenUsageCollector，
 *     把结果写入本 Tracker（update）；
 *   - ScreenUsageCollector 在 doCollectData 时优先读本 Tracker 的缓存，
 *     得不到再 fallback 到 UsageEvents 扫描。
 *
 * 时间精度：与 FloatingOverlayService 轮询间隔（默认 30 秒）相同。
 */
public class AppForegroundTracker {

    private static final String TAG = "AppFgTracker";

    private static volatile AppForegroundTracker instance;

    /** 当前前台 App 包名，null 表示未知 */
    private volatile String currentPackage = null;

    /** 当前 App 进入前台的时间戳（毫秒），-1 表示未记录 */
    private volatile long switchTime = -1;

    /** 上一次成功更新的时间戳，用于判断缓存是否过期 */
    private volatile long lastUpdateTime = -1;

    /** 缓存有效期：超过此时间未更新则视为过期，采集时不信任缓存 */
    private static final long CACHE_STALE_MS = 3 * 60_000L; // 3 分钟

    private AppForegroundTracker() {}

    public static AppForegroundTracker getInstance(Context context) {
        if (instance == null) {
            synchronized (AppForegroundTracker.class) {
                if (instance == null) {
                    instance = new AppForegroundTracker();
                }
            }
        }
        return instance;
    }

    /**
     * 更新前台 App 缓存。
     *
     * 由 FloatingOverlayService 定期调用。
     *
     * @param packageName 当前前台 App 包名，null 表示无法确定（屏幕关闭等）
     */
    public synchronized void update(String packageName) {
        long now = System.currentTimeMillis();
        lastUpdateTime = now;

        if (packageName == null) {
            // 屏幕关闭或权限不足时不清空 currentPackage，保留上次的值
            // 但 switchTime 不变，调用方可通过 isStale() 判断可信度
            return;
        }

        if (!packageName.equals(currentPackage)) {
            Log.d(TAG, "Foreground app switched: " + currentPackage + " → " + packageName);
            currentPackage = packageName;
            switchTime = now;
        }
    }

    /**
     * 获取当前前台 App 包名。
     *
     * @return 包名，或 null（未知 / 缓存过期）
     */
    public synchronized String getCurrentPackage() {
        if (isStale()) return null;
        return currentPackage;
    }

    /**
     * 获取用户在当前前台 App 上的持续停留时长（毫秒）。
     *
     * 精度等于轮询间隔（默认 30 秒）；缓存过期时返回 0。
     */
    public synchronized long getTimeInCurrentAppMs() {
        if (isStale() || switchTime < 0) return 0;
        return System.currentTimeMillis() - switchTime;
    }

    /**
     * 直接获取切换时间戳（毫秒），供 ScreenUsageCollector 写入 JSON。
     * 缓存过期时返回 -1。
     */
    public synchronized long getSwitchTime() {
        if (isStale()) return -1;
        return switchTime;
    }

    /**
     * 判断缓存是否过期（超过 CACHE_STALE_MS 未更新）。
     */
    public synchronized boolean isStale() {
        if (lastUpdateTime < 0) return true;
        return System.currentTimeMillis() - lastUpdateTime > CACHE_STALE_MS;
    }

    /**
     * 重置缓存（例如屏幕关闭超过阈值后由外部调用）。
     */
    public synchronized void reset() {
        currentPackage = null;
        switchTime = -1;
        lastUpdateTime = -1;
        Log.d(TAG, "Cache reset");
    }
}
