package com.datacollector.android.api;

import com.datacollector.android.BuildConfig;

/**
 * API配置类
 * API密钥通过BuildConfig注入（来源于local.properties），不再硬编码在源码中。
 * 要配置密钥，请在项目根目录的 local.properties 中添加：
 *   GEMINI_API_KEY=your_key_here
 *   DEEPSEEK_API_KEY=your_key_here
 */
public class ApiConfig {

    public static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    public static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash-exp:generateContent";

    public static final String DEEPSEEK_API_KEY = BuildConfig.DEEPSEEK_API_KEY;
    public static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";

    public static boolean isGeminiApiKeyConfigured() {
        return GEMINI_API_KEY != null && !GEMINI_API_KEY.isEmpty()
                && !GEMINI_API_KEY.equals("YOUR_API_KEY_HERE");
    }

    public static boolean isDeepSeekApiKeyConfigured() {
        return DEEPSEEK_API_KEY != null && !DEEPSEEK_API_KEY.isEmpty()
                && !DEEPSEEK_API_KEY.equals("YOUR_DEEPSEEK_API_KEY_HERE");
    }

    @Deprecated
    public static boolean isApiKeyConfigured() {
        return isGeminiApiKeyConfigured();
    }
}
