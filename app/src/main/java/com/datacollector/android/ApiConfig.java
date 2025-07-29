package com.datacollector.android;

/**
 * API配置类
 * 用于管理Gemini API和DeepSeek API的配置信息
 */
public class ApiConfig {
    
    /**
     * Gemini API密钥
     * 请将此处替换为您从Google AI Studio获取的实际API密钥
     * 获取地址：https://aistudio.google.com/
     */
    public static final String GEMINI_API_KEY = "AIzaSyARjeVVTLxsKjZoMMM4qr_sLGDTj_1Csz4";
    
    /**
     * Gemini API端点URL
     * 使用gemini-2.0-flash-exp模型
     */
    public static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash-exp:generateContent";
    
    /**
     * DeepSeek API密钥
     * 获取地址：https://platform.deepseek.com/api_keys
     */
    public static final String DEEPSEEK_API_KEY = "sk-f7446c6b40aa4db29702451a4fa87de9";
    
    /**
     * DeepSeek API端点URL
     * 使用deepseek-chat模型
     */
    public static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";
    
    /**
     * 检查Gemini API密钥是否已配置
     */
    public static boolean isGeminiApiKeyConfigured() {
        return !GEMINI_API_KEY.equals("YOUR_API_KEY_HERE") && 
               !GEMINI_API_KEY.trim().isEmpty();
    }
    
    /**
     * 检查DeepSeek API密钥是否已配置
     */
    public static boolean isDeepSeekApiKeyConfigured() {
        return !DEEPSEEK_API_KEY.equals("YOUR_DEEPSEEK_API_KEY_HERE") && 
               !DEEPSEEK_API_KEY.trim().isEmpty();
    }
    
    /**
     * 检查API密钥是否已配置（向后兼容）
     */
    @Deprecated
    public static boolean isApiKeyConfigured() {
        return isGeminiApiKeyConfigured();
    }
} 