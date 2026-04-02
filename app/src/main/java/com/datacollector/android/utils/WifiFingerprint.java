package com.datacollector.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Iterator;

/**
 * WiFi BSSID 指纹自动学习：根据连接时间段自动推断 BSSID 属于"家"还是"公司/学校"。
 *
 * 学习策略：
 * - 深夜~清晨（23:00-06:00）连接的 BSSID → 投票为 home
 * - 工作日（周一~周五）9:00-18:00 连接的 BSSID → 投票为 work
 * - 其他时间段不投票（通勤、周末外出等噪声太大）
 *
 * 判定条件：某 BSSID 累计投票 ≥ MIN_SAMPLES 且某类投票占比 ≥ THRESHOLD 时确认。
 */
public class WifiFingerprint {

    private static final String TAG = "WifiFingerprint";
    private static final String PREFS_NAME = "wifi_fingerprint";

    /** 最少投票数，低于此不做判定 */
    private static final int MIN_SAMPLES = 5;
    /** 占比阈值，超过此比例可确认 */
    private static final double THRESHOLD = 0.6;

    /** 判定结果 */
    public static final String PLACE_HOME = "家";
    public static final String PLACE_WORK = "公司/学校";
    public static final String PLACE_UNKNOWN = null;

    private final SharedPreferences prefs;
    private static WifiFingerprint instance;

    public static synchronized WifiFingerprint getInstance(Context context) {
        if (instance == null) {
            instance = new WifiFingerprint(context.getApplicationContext());
        }
        return instance;
    }

    private WifiFingerprint(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 每次数据采集时调用，记录当前 BSSID 在当前时间段的投票。
     *
     * @param bssid 当前连接的 BSSID，null 或空则跳过
     */
    public void recordObservation(String bssid) {
        if (bssid == null || bssid.isEmpty() || "02:00:00:00:00:00".equals(bssid)) {
            return;
        }

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int dow = cal.get(Calendar.DAY_OF_WEEK); // SUN=1, SAT=7

        String voteType = null;
        if (hour >= 23 || hour < 6) {
            voteType = "home";
        } else if (dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY
                && hour >= 9 && hour < 18) {
            voteType = "work";
        }

        if (voteType == null) {
            return; // 非诊断时间段，不投票
        }

        try {
            String json = prefs.getString(bssid, "{}");
            JSONObject record = new JSONObject(json);

            int homeVotes = record.optInt("home", 0);
            int workVotes = record.optInt("work", 0);

            if ("home".equals(voteType)) {
                homeVotes++;
            } else {
                workVotes++;
            }

            record.put("home", homeVotes);
            record.put("work", workVotes);
            prefs.edit().putString(bssid, record.toString()).apply();

        } catch (JSONException e) {
            Log.w(TAG, "Failed to record observation for " + bssid, e);
        }
    }

    /**
     * 查询 BSSID 的学习结果。
     *
     * @return PLACE_HOME / PLACE_WORK / null（未知）
     */
    public String classify(String bssid) {
        if (bssid == null || bssid.isEmpty() || "02:00:00:00:00:00".equals(bssid)) {
            return PLACE_UNKNOWN;
        }

        try {
            String json = prefs.getString(bssid, null);
            if (json == null) return PLACE_UNKNOWN;

            JSONObject record = new JSONObject(json);
            int homeVotes = record.optInt("home", 0);
            int workVotes = record.optInt("work", 0);
            int total = homeVotes + workVotes;

            if (total < MIN_SAMPLES) return PLACE_UNKNOWN;

            double homeRatio = (double) homeVotes / total;
            double workRatio = (double) workVotes / total;

            if (homeRatio >= THRESHOLD) return PLACE_HOME;
            if (workRatio >= THRESHOLD) return PLACE_WORK;

            return PLACE_UNKNOWN;

        } catch (JSONException e) {
            return PLACE_UNKNOWN;
        }
    }
}
