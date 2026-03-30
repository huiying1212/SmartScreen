package com.datacollector.android.utils;

import com.datacollector.android.R;

/**
 * 基于 UUT（无意识使用时间）的表情/心情映射器。
 *
 * UUT 范围 0-100：
 *   0-20  → 开心/平静
 *  21-60  → 逐渐呆滞/黑眼圈
 *  61-100 → 痛苦/崩溃/融化
 *
 * 同时保留基于屏幕使用时长的辅助映射（壁纸引擎使用）。
 */
public class MoodMapper {

    public enum Mood {
        HAPPY(R.drawable.mood_happy, "开心", "happy and relaxed"),
        CALM(R.drawable.mood_energetic, "平静", "calm and peaceful"),
        NEUTRAL(R.drawable.mood_neutral, "一般", "neutral"),
        DULL(R.drawable.mood_concerned, "呆滞", "dull and unfocused"),
        TIRED(R.drawable.mood_tired, "疲惫", "tired and strained"),
        PAINFUL(R.drawable.mood_exhausted, "崩溃", "painful and overwhelmed");

        public final int drawableRes;
        public final String labelCn;
        public final String labelEn;

        Mood(int drawableRes, String labelCn, String labelEn) {
            this.drawableRes = drawableRes;
            this.labelCn = labelCn;
            this.labelEn = labelEn;
        }
    }

    /**
     * 从 UUT 值（0-100）映射到 Mood，支持 6 级细粒度控制。
     */
    public static Mood fromUUT(int uut) {
        if (uut <= 5)  return Mood.HAPPY;
        if (uut <= 20) return Mood.CALM;
        if (uut <= 40) return Mood.NEUTRAL;
        if (uut <= 60) return Mood.DULL;
        if (uut <= 80) return Mood.TIRED;
        return Mood.PAINFUL;
    }

    /**
     * 从屏幕使用时长映射到 Mood（壁纸引擎使用）。
     */
    public static Mood fromScreenTime(long screenTimeMs) {
        long minutes = screenTimeMs / 60_000L;
        if (minutes < 30)  return Mood.HAPPY;
        if (minutes < 60)  return Mood.CALM;
        if (minutes < 120) return Mood.NEUTRAL;
        if (minutes < 180) return Mood.DULL;
        if (minutes < 240) return Mood.TIRED;
        return Mood.PAINFUL;
    }

    /**
     * UUT 值 (0–100) → 连续 stress 值 [0.0, 1.0]，直接驱动 MoodFaceView 绘制。
     * 使用非线性映射使中段变化更敏感。
     */
    public static float uutToStress(int uut) {
        float t = Math.max(0f, Math.min(1f, uut / 100f));
        // ease-in-out curve: gentle at extremes, responsive in the middle
        return t * t * (3f - 2f * t);
    }

    /**
     * 屏幕时长 → 连续 stress 值 [0.0, 1.0]，用于壁纸或其他需要连续值的场景。
     */
    public static float screenTimeToStress(long screenTimeMs) {
        float minutes = screenTimeMs / 60_000f;
        float t = Math.max(0f, Math.min(1f, minutes / 300f));
        return t * t * (3f - 2f * t);
    }

    /**
     * 返回适合文生图的心情描述片段，用于壁纸 Prompt 拼接。
     */
    public static String toImagePromptFragment(Mood mood, long screenTimeMs) {
        long hours = screenTimeMs / (60 * 60_000L);
        long mins = (screenTimeMs / 60_000L) % 60;
        String time = hours > 0 ? hours + "小时" + mins + "分钟" : mins + "分钟";

        switch (mood) {
            case HAPPY:
                return "用户今天刚开始使用手机（" + time + "），状态非常好，"
                        + "画面应充满活力与清新感，色调明亮温暖";
            case CALM:
                return "用户使用手机" + time + "，心情平静，"
                        + "画面应温馨舒适，带有轻松愉快的氛围";
            case NEUTRAL:
                return "用户已使用手机" + time + "，状态平稳，"
                        + "画面应平和宁静，色调柔和自然";
            case DULL:
                return "用户已使用手机" + time + "，有些沉迷，"
                        + "画面应带有提醒休息的暗示，如夕阳、傍晚的自然景色";
            case TIRED:
                return "用户已使用手机" + time + "，比较疲惫，"
                        + "画面应营造放松休息的氛围，如星空、安静的夜晚";
            case PAINFUL:
            default:
                return "用户已使用手机超过" + time + "，严重过度使用，"
                        + "画面应传达需要立即放下手机的信息，如深夜沉静的场景";
        }
    }
}
