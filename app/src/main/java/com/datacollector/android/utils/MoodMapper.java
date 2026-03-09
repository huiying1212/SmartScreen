package com.datacollector.android.utils;

import com.datacollector.android.R;

/**
 * 将屏幕使用时长映射为6种心情状态，用于悬浮图标表情和壁纸生成提示词
 */
public class MoodMapper {

    public enum Mood {
        ENERGETIC(R.drawable.mood_energetic, "精力充沛", "energetic and fresh"),
        HAPPY(R.drawable.mood_happy, "开心", "happy and content"),
        NEUTRAL(R.drawable.mood_neutral, "平静", "calm and neutral"),
        CONCERNED(R.drawable.mood_concerned, "有点担忧", "slightly concerned"),
        TIRED(R.drawable.mood_tired, "疲惫", "tired and fatigued"),
        EXHAUSTED(R.drawable.mood_exhausted, "精疲力竭", "exhausted and overwhelmed");

        public final int drawableRes;
        public final String labelCn;
        public final String labelEn;

        Mood(int drawableRes, String labelCn, String labelEn) {
            this.drawableRes = drawableRes;
            this.labelCn = labelCn;
            this.labelEn = labelEn;
        }
    }

    private static final long MINUTES = 60_000L;

    /**
     * @param screenTimeMs 今日屏幕使用时长（毫秒）
     */
    public static Mood fromScreenTime(long screenTimeMs) {
        long minutes = screenTimeMs / MINUTES;
        if (minutes < 30) return Mood.ENERGETIC;
        if (minutes < 60) return Mood.HAPPY;
        if (minutes < 120) return Mood.NEUTRAL;
        if (minutes < 180) return Mood.CONCERNED;
        if (minutes < 240) return Mood.TIRED;
        return Mood.EXHAUSTED;
    }

    /**
     * 返回适合文生图的心情描述片段，用于壁纸 prompt 拼接
     */
    public static String toImagePromptFragment(Mood mood, long screenTimeMs) {
        long hours = screenTimeMs / (60 * MINUTES);
        long mins = (screenTimeMs / MINUTES) % 60;
        String time = hours > 0 ? hours + "小时" + mins + "分钟" : mins + "分钟";

        switch (mood) {
            case ENERGETIC:
                return "用户今天刚开始使用手机（" + time + "），状态非常好，" +
                        "画面应充满活力与清新感，色调明亮温暖";
            case HAPPY:
                return "用户使用手机" + time + "，心情愉悦，" +
                        "画面应温馨舒适，带有轻松愉快的氛围";
            case NEUTRAL:
                return "用户已使用手机" + time + "，状态平稳，" +
                        "画面应平和宁静，色调柔和自然";
            case CONCERNED:
                return "用户已使用手机" + time + "，需要适当休息，" +
                        "画面应带有提醒休息的暗示，如夕阳、傍晚的自然景色";
            case TIRED:
                return "用户已使用手机" + time + "，比较疲惫，" +
                        "画面应营造放松休息的氛围，如星空、安静的夜晚";
            case EXHAUSTED:
                return "用户已使用手机超过" + time + "，严重过度使用，" +
                        "画面应传达需要立即放下手机的信息，如深夜沉静的场景";
            default:
                return "用户使用手机" + time;
        }
    }
}
