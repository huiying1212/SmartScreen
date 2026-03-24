package com.datacollector.android.api;

import com.datacollector.android.BuildConfig;

/**
 * API配置类
 * API密钥通过BuildConfig注入（来源于local.properties），不再硬编码在源码中。
 * 要配置密钥，请在项目根目录的 local.properties 中添加：
 *   DEEPSEEK_API_KEY=your_key_here
 *   QWEN_IMAGE_API_KEY=your_key_here
 */
public class ApiConfig {

    public static final String DEEPSEEK_API_KEY = BuildConfig.DEEPSEEK_API_KEY;
    public static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";

    public static final String QWEN_IMAGE_API_KEY = BuildConfig.QWEN_IMAGE_API_KEY;
    public static final String QWEN_IMAGE_API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    public static boolean isDeepSeekApiKeyConfigured() {
        return DEEPSEEK_API_KEY != null && !DEEPSEEK_API_KEY.isEmpty()
                && !DEEPSEEK_API_KEY.equals("YOUR_DEEPSEEK_API_KEY_HERE");
    }

    public static boolean isQwenImageApiKeyConfigured() {
        return QWEN_IMAGE_API_KEY != null && !QWEN_IMAGE_API_KEY.isEmpty();
    }
}
