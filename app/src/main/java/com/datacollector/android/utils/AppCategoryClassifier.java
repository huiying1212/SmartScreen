package com.datacollector.android.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * App 分类器：基于包名规则将 App 分为社交、娱乐、生产力、工具、游戏等类别。
 * 分类结果用于 UUT（无意识使用时间）算法判断和壁纸引擎的 Prompt 组装。
 */
public class AppCategoryClassifier {

    private static final String PREFS_NAME = "app_category_overrides";

    public enum AppCategory {
        SOCIAL("社交", "social"),
        ENTERTAINMENT("娱乐", "entertainment"),
        SHORT_VIDEO("短视频", "short_video"),
        GAMES("游戏", "games"),
        PRODUCTIVITY("生产力", "productivity"),
        TOOLS("工具", "tools"),
        EDUCATION("教育", "education"),
        OTHER("其他", "other");

        public final String labelCn;
        public final String labelEn;

        AppCategory(String labelCn, String labelEn) {
            this.labelCn = labelCn;
            this.labelEn = labelEn;
        }
    }

    private static final Map<String, AppCategory> KNOWN_PACKAGES = new HashMap<>();

    static {
        // --- 社交 ---
        KNOWN_PACKAGES.put("com.tencent.mm", AppCategory.SOCIAL);           // 微信
        KNOWN_PACKAGES.put("com.tencent.mobileqq", AppCategory.SOCIAL);     // QQ
        KNOWN_PACKAGES.put("com.whatsapp", AppCategory.SOCIAL);
        KNOWN_PACKAGES.put("com.facebook.orca", AppCategory.SOCIAL);
        KNOWN_PACKAGES.put("com.facebook.katana", AppCategory.SOCIAL);
        KNOWN_PACKAGES.put("org.telegram.messenger", AppCategory.SOCIAL);
        KNOWN_PACKAGES.put("com.instagram.android", AppCategory.SOCIAL);
        KNOWN_PACKAGES.put("com.twitter.android", AppCategory.SOCIAL);
        KNOWN_PACKAGES.put("com.linkedin.android", AppCategory.SOCIAL);
        KNOWN_PACKAGES.put("com.snapchat.android", AppCategory.SOCIAL);
        KNOWN_PACKAGES.put("com.sina.weibo", AppCategory.SOCIAL);           // 微博
        KNOWN_PACKAGES.put("com.immomo.momo", AppCategory.SOCIAL);          // 陌陌
        KNOWN_PACKAGES.put("com.tencent.tim", AppCategory.SOCIAL);          // TIM
        KNOWN_PACKAGES.put("com.alibaba.android.rimet", AppCategory.PRODUCTIVITY); // 钉钉 → 生产力
        KNOWN_PACKAGES.put("com.tencent.wework", AppCategory.PRODUCTIVITY); // 企业微信 → 生产力

        // --- 短视频 ---
        KNOWN_PACKAGES.put("com.ss.android.ugc.aweme", AppCategory.SHORT_VIDEO);   // 抖音
        KNOWN_PACKAGES.put("com.kuaishou.nebula", AppCategory.SHORT_VIDEO);         // 快手
        KNOWN_PACKAGES.put("com.smile.gifmaker", AppCategory.SHORT_VIDEO);          // 快手极速版
        KNOWN_PACKAGES.put("com.ss.android.ugc.aweme.lite", AppCategory.SHORT_VIDEO); // 抖音极速版

        // --- 娱乐 ---
        KNOWN_PACKAGES.put("tv.danmaku.bili", AppCategory.ENTERTAINMENT);    // Bilibili
        KNOWN_PACKAGES.put("com.youku.phone", AppCategory.ENTERTAINMENT);    // 优酷
        KNOWN_PACKAGES.put("com.tencent.qqlive", AppCategory.ENTERTAINMENT); // 腾讯视频
        KNOWN_PACKAGES.put("com.iqiyi.client", AppCategory.ENTERTAINMENT);   // 爱奇艺
        KNOWN_PACKAGES.put("com.google.android.youtube", AppCategory.ENTERTAINMENT);
        KNOWN_PACKAGES.put("com.netflix.mediaclient", AppCategory.ENTERTAINMENT);
        KNOWN_PACKAGES.put("com.spotify.music", AppCategory.ENTERTAINMENT);
        KNOWN_PACKAGES.put("com.kugou.android", AppCategory.ENTERTAINMENT);  // 酷狗
        KNOWN_PACKAGES.put("com.netease.cloudmusic", AppCategory.ENTERTAINMENT); // 网易云音乐
        KNOWN_PACKAGES.put("com.tencent.qqmusic", AppCategory.ENTERTAINMENT);    // QQ音乐
        KNOWN_PACKAGES.put("com.ximalaya.ting.android", AppCategory.ENTERTAINMENT); // 喜马拉雅
        KNOWN_PACKAGES.put("com.ss.android.article.news", AppCategory.ENTERTAINMENT); // 今日头条
        KNOWN_PACKAGES.put("com.zhihu.android", AppCategory.ENTERTAINMENT);  // 知乎

        // --- 游戏 ---
        KNOWN_PACKAGES.put("com.tencent.tmgp.sgame", AppCategory.GAMES);    // 王者荣耀
        KNOWN_PACKAGES.put("com.tencent.ig", AppCategory.GAMES);            // 和平精英
        KNOWN_PACKAGES.put("com.miHoYo.Yuanshen", AppCategory.GAMES);       // 原神
        KNOWN_PACKAGES.put("com.supercell.clashofclans", AppCategory.GAMES);

        // --- 生产力 ---
        KNOWN_PACKAGES.put("com.microsoft.office.word", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.microsoft.office.excel", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.microsoft.office.powerpoint", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.microsoft.office.onenote", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.microsoft.teams", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.google.android.apps.docs", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.google.android.apps.docs.editors.docs", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.google.android.apps.docs.editors.sheets", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.google.android.apps.docs.editors.slides", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.google.android.gm", AppCategory.PRODUCTIVITY);  // Gmail
        KNOWN_PACKAGES.put("com.microsoft.office.outlook", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.notion.id", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.todoist", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.ticktick.task", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("md.obsidian", AppCategory.PRODUCTIVITY);
        KNOWN_PACKAGES.put("com.evernote", AppCategory.PRODUCTIVITY);

        // --- 工具 ---
        KNOWN_PACKAGES.put("com.android.calculator2", AppCategory.TOOLS);
        KNOWN_PACKAGES.put("com.google.android.calculator", AppCategory.TOOLS);
        KNOWN_PACKAGES.put("com.android.deskclock", AppCategory.TOOLS);
        KNOWN_PACKAGES.put("com.google.android.deskclock", AppCategory.TOOLS);
        KNOWN_PACKAGES.put("com.android.camera", AppCategory.TOOLS);
        KNOWN_PACKAGES.put("com.android.camera2", AppCategory.TOOLS);
        KNOWN_PACKAGES.put("com.google.android.apps.maps", AppCategory.TOOLS);
        KNOWN_PACKAGES.put("com.autonavi.minimap", AppCategory.TOOLS);      // 高德
        KNOWN_PACKAGES.put("com.baidu.BaiduMap", AppCategory.TOOLS);        // 百度地图
        KNOWN_PACKAGES.put("com.eg.android.AlipayGphone", AppCategory.TOOLS); // 支付宝

        // --- 教育 ---
        KNOWN_PACKAGES.put("com.duolingo", AppCategory.EDUCATION);
        KNOWN_PACKAGES.put("com.baidu.translate", AppCategory.EDUCATION);
        KNOWN_PACKAGES.put("com.youdao.dict", AppCategory.EDUCATION);
    }

    private final SharedPreferences overrides;

    public AppCategoryClassifier(Context context) {
        this.overrides = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public AppCategory classify(String packageName) {
        if (packageName == null) return AppCategory.OTHER;

        String overrideCat = overrides.getString(packageName, null);
        if (overrideCat != null) {
            try {
                return AppCategory.valueOf(overrideCat);
            } catch (IllegalArgumentException ignored) {}
        }

        AppCategory known = KNOWN_PACKAGES.get(packageName);
        if (known != null) return known;

        return classifyByPattern(packageName);
    }

    private AppCategory classifyByPattern(String pkg) {
        if (pkg.contains(".game") || pkg.contains(".games")
                || pkg.contains("game.") || pkg.contains("games.")) {
            return AppCategory.GAMES;
        }
        if (pkg.contains("video") || pkg.contains("movie") || pkg.contains("tv.")) {
            return AppCategory.ENTERTAINMENT;
        }
        if (pkg.contains("music") || pkg.contains("radio") || pkg.contains("podcast")) {
            return AppCategory.ENTERTAINMENT;
        }
        if (pkg.contains("chat") || pkg.contains("messenger") || pkg.contains("social")) {
            return AppCategory.SOCIAL;
        }
        if (pkg.contains("office") || pkg.contains("mail") || pkg.contains("calendar")
                || pkg.contains("note") || pkg.contains("todo") || pkg.contains("task")) {
            return AppCategory.PRODUCTIVITY;
        }
        if (pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.")) {
            return AppCategory.TOOLS;
        }
        return AppCategory.OTHER;
    }

    /**
     * 判断该分类是否会触发 UUT 累加（无意识使用类别）
     */
    public static boolean isUnconsciousCategory(AppCategory category) {
        return category == AppCategory.SOCIAL
                || category == AppCategory.ENTERTAINMENT
                || category == AppCategory.SHORT_VIDEO
                || category == AppCategory.GAMES;
    }

    /**
     * 判断该分类是否会引起 UUT 衰减（生产力类别）
     */
    public static boolean isProductiveCategory(AppCategory category) {
        return category == AppCategory.PRODUCTIVITY
                || category == AppCategory.TOOLS
                || category == AppCategory.EDUCATION;
    }

    public void setOverride(String packageName, AppCategory category) {
        overrides.edit().putString(packageName, category.name()).apply();
    }

    public void removeOverride(String packageName) {
        overrides.edit().remove(packageName).apply();
    }
}
