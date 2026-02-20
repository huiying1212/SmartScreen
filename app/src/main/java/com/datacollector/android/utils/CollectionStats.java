package com.datacollector.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 借鉴Beiwe的 calculate_data_quantity_stats：追踪采集质量指标，
 * 提供数据量统计、各传感器成功率、以及数据文件大小趋势。
 */
public class CollectionStats {

    private static final String TAG = "CollectionStats";
    private static final String PREFS_NAME = "collection_stats";

    private static final String KEY_TOTAL_COLLECTIONS = "total_collections";
    private static final String KEY_SUCCESSFUL_COLLECTIONS = "successful_collections";
    private static final String KEY_FAILED_COLLECTIONS = "failed_collections";
    private static final String KEY_TOTAL_API_CALLS = "total_api_calls";
    private static final String KEY_SUCCESSFUL_API_CALLS = "successful_api_calls";
    private static final String KEY_FAILED_API_CALLS = "failed_api_calls";
    private static final String KEY_TOTAL_BYTES_SAVED = "total_bytes_saved";
    private static final String KEY_TOTAL_FILES_SAVED = "total_files_saved";
    private static final String KEY_LAST_COLLECTION_TIME = "last_collection_time";
    private static final String KEY_LAST_API_CALL_TIME = "last_api_call_time";
    private static final String KEY_COLLECTOR_PREFIX = "collector_success_";
    private static final String KEY_COLLECTOR_FAIL_PREFIX = "collector_fail_";

    private final SharedPreferences prefs;

    private static CollectionStats instance;

    public static synchronized CollectionStats getInstance(Context context) {
        if (instance == null) {
            instance = new CollectionStats(context.getApplicationContext());
        }
        return instance;
    }

    private CollectionStats(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void recordCollectionAttempt(boolean success) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_TOTAL_COLLECTIONS, prefs.getLong(KEY_TOTAL_COLLECTIONS, 0) + 1);
        if (success) {
            editor.putLong(KEY_SUCCESSFUL_COLLECTIONS, prefs.getLong(KEY_SUCCESSFUL_COLLECTIONS, 0) + 1);
        } else {
            editor.putLong(KEY_FAILED_COLLECTIONS, prefs.getLong(KEY_FAILED_COLLECTIONS, 0) + 1);
        }
        editor.putLong(KEY_LAST_COLLECTION_TIME, System.currentTimeMillis());
        editor.apply();
    }

    public void recordCollectorResult(String collectorId, boolean success) {
        String key = success ? KEY_COLLECTOR_PREFIX + collectorId : KEY_COLLECTOR_FAIL_PREFIX + collectorId;
        prefs.edit().putLong(key, prefs.getLong(key, 0) + 1).apply();
    }

    public void recordApiCall(boolean success) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_TOTAL_API_CALLS, prefs.getLong(KEY_TOTAL_API_CALLS, 0) + 1);
        if (success) {
            editor.putLong(KEY_SUCCESSFUL_API_CALLS, prefs.getLong(KEY_SUCCESSFUL_API_CALLS, 0) + 1);
        } else {
            editor.putLong(KEY_FAILED_API_CALLS, prefs.getLong(KEY_FAILED_API_CALLS, 0) + 1);
        }
        editor.putLong(KEY_LAST_API_CALL_TIME, System.currentTimeMillis());
        editor.apply();
    }

    public void recordFileSaved(long sizeBytes) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_TOTAL_BYTES_SAVED, prefs.getLong(KEY_TOTAL_BYTES_SAVED, 0) + sizeBytes);
        editor.putLong(KEY_TOTAL_FILES_SAVED, prefs.getLong(KEY_TOTAL_FILES_SAVED, 0) + 1);
        editor.apply();
    }

    public JSONObject getStatsJson() {
        JSONObject stats = new JSONObject();
        try {
            long totalCollections = prefs.getLong(KEY_TOTAL_COLLECTIONS, 0);
            long successfulCollections = prefs.getLong(KEY_SUCCESSFUL_COLLECTIONS, 0);
            long failedCollections = prefs.getLong(KEY_FAILED_COLLECTIONS, 0);
            long totalApiCalls = prefs.getLong(KEY_TOTAL_API_CALLS, 0);
            long successfulApiCalls = prefs.getLong(KEY_SUCCESSFUL_API_CALLS, 0);

            stats.put("total_collections", totalCollections);
            stats.put("successful_collections", successfulCollections);
            stats.put("failed_collections", failedCollections);
            stats.put("collection_success_rate",
                    totalCollections > 0 ? (double) successfulCollections / totalCollections : 0);
            stats.put("total_api_calls", totalApiCalls);
            stats.put("successful_api_calls", successfulApiCalls);
            stats.put("failed_api_calls", prefs.getLong(KEY_FAILED_API_CALLS, 0));
            stats.put("api_success_rate",
                    totalApiCalls > 0 ? (double) successfulApiCalls / totalApiCalls : 0);
            stats.put("total_bytes_saved", prefs.getLong(KEY_TOTAL_BYTES_SAVED, 0));
            stats.put("total_files_saved", prefs.getLong(KEY_TOTAL_FILES_SAVED, 0));
            stats.put("last_collection_time", prefs.getLong(KEY_LAST_COLLECTION_TIME, 0));
            stats.put("last_api_call_time", prefs.getLong(KEY_LAST_API_CALL_TIME, 0));
        } catch (JSONException e) {
            Log.e(TAG, "Error building stats JSON", e);
        }
        return stats;
    }

    public String getStatsSummary() {
        long totalCollections = prefs.getLong(KEY_TOTAL_COLLECTIONS, 0);
        long successfulCollections = prefs.getLong(KEY_SUCCESSFUL_COLLECTIONS, 0);
        long totalApiCalls = prefs.getLong(KEY_TOTAL_API_CALLS, 0);
        long successfulApiCalls = prefs.getLong(KEY_SUCCESSFUL_API_CALLS, 0);
        long totalBytes = prefs.getLong(KEY_TOTAL_BYTES_SAVED, 0);

        double collectionRate = totalCollections > 0 ? 100.0 * successfulCollections / totalCollections : 0;
        double apiRate = totalApiCalls > 0 ? 100.0 * successfulApiCalls / totalApiCalls : 0;

        return String.format(
                "数据采集统计:\n" +
                "总采集次数: %d (成功率: %.1f%%)\n" +
                "API调用次数: %d (成功率: %.1f%%)\n" +
                "总存储数据: %.2f MB\n" +
                "总文件数: %d",
                totalCollections, collectionRate,
                totalApiCalls, apiRate,
                totalBytes / 1024.0 / 1024.0,
                prefs.getLong(KEY_TOTAL_FILES_SAVED, 0)
        );
    }

    public void reset() {
        prefs.edit().clear().apply();
    }
}
