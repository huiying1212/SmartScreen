package com.datacollector.android.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.CompletableFuture;

/**
 * OCR文字识别处理器
 * 使用Google ML Kit进行图片文字识别
 */
public class OcrProcessor {
    
    private static final String TAG = "OcrProcessor";
    
    private TextRecognizer latinTextRecognizer;
    private TextRecognizer chineseTextRecognizer;
    private Context context;
    
    public OcrProcessor(Context context) {
        this.context = context;
        initializeRecognizers();
    }
    
    /**
     * 初始化文字识别器
     */
    private void initializeRecognizers() {
        try {
            // 初始化拉丁文本识别器
            latinTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            
            // 初始化中文文本识别器
            chineseTextRecognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            
            Log.d(TAG, "OCR识别器初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "OCR识别器初始化失败", e);
        }
    }
    
    /**
     * OCR识别结果回调接口
     */
    public interface OcrCallback {
        void onSuccess(String recognizedText, float confidence);
        void onError(String error);
    }
    
    /**
     * 从Bitmap识别文字（支持中英文混合识别）
     */
    public void recognizeTextFromBitmap(Bitmap bitmap, OcrCallback callback) {
        if (bitmap == null) {
            callback.onError("输入图片为空");
            return;
        }
        
        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            
            // 先尝试中文识别器（通常对中英文混合效果更好）
            chineseTextRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String recognizedText = extractTextFromVisionText(visionText);
                    float confidence = calculateAverageConfidence(visionText);
                    
                    if (recognizedText.trim().isEmpty()) {
                        // 如果中文识别器没有结果，尝试拉丁文识别器
                        recognizeWithLatinRecognizer(image, callback);
                    } else {
                        callback.onSuccess(recognizedText, confidence);
                        Log.d(TAG, "中文OCR识别成功，文本长度: " + recognizedText.length());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "中文OCR识别失败，尝试拉丁文识别", e);
                    // 中文识别失败，尝试拉丁文识别
                    recognizeWithLatinRecognizer(image, callback);
                });
                
        } catch (Exception e) {
            Log.e(TAG, "OCR识别过程出错", e);
            callback.onError("OCR识别过程出错: " + e.getMessage());
        }
    }
    
    /**
     * 使用拉丁文识别器进行识别
     */
    private void recognizeWithLatinRecognizer(InputImage image, OcrCallback callback) {
        latinTextRecognizer.process(image)
            .addOnSuccessListener(visionText -> {
                String recognizedText = extractTextFromVisionText(visionText);
                float confidence = calculateAverageConfidence(visionText);
                callback.onSuccess(recognizedText, confidence);
                Log.d(TAG, "拉丁文OCR识别成功，文本长度: " + recognizedText.length());
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "拉丁文OCR识别也失败", e);
                callback.onError("OCR识别失败: " + e.getMessage());
            });
    }
    
    /**
     * 从Vision Text对象提取文本内容
     */
    private String extractTextFromVisionText(Text visionText) {
        StringBuilder textBuilder = new StringBuilder();
        
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                textBuilder.append(line.getText()).append("\n");
            }
        }
        
        return textBuilder.toString().trim();
    }
    
    /**
     * 计算平均置信度
     */
    private float calculateAverageConfidence(Text visionText) {
        float totalConfidence = 0.0f;
        int elementCount = 0;
        
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    // ML Kit不直接提供置信度，我们基于识别的元素数量估算
                    totalConfidence += 1.0f; // 假设每个成功识别的元素置信度为1
                    elementCount++;
                }
            }
        }
        
        if (elementCount == 0) {
            return 0.0f;
        }
        
        // 基于识别元素的数量和文本长度计算置信度
        String fullText = extractTextFromVisionText(visionText);
        float lengthFactor = Math.min(1.0f, fullText.length() / 100.0f); // 文本长度因子
        
        return Math.min(0.95f, (totalConfidence / elementCount) * lengthFactor);
    }
    
    /**
     * 异步识别文字（返回CompletableFuture）
     */
    public CompletableFuture<OcrResult> recognizeTextAsync(Bitmap bitmap) {
        CompletableFuture<OcrResult> future = new CompletableFuture<>();
        
        recognizeTextFromBitmap(bitmap, new OcrCallback() {
            @Override
            public void onSuccess(String recognizedText, float confidence) {
                future.complete(new OcrResult(recognizedText, confidence, true));
            }
            
            @Override
            public void onError(String error) {
                future.complete(new OcrResult("", 0.0f, false, error));
            }
        });
        
        return future;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        try {
            if (latinTextRecognizer != null) {
                latinTextRecognizer.close();
            }
            if (chineseTextRecognizer != null) {
                chineseTextRecognizer.close();
            }
            Log.d(TAG, "OCR识别器资源已释放");
        } catch (Exception e) {
            Log.e(TAG, "释放OCR识别器资源时出错", e);
        }
    }
    
    /**
     * OCR识别结果数据类
     */
    public static class OcrResult {
        public final String text;
        public final float confidence;
        public final boolean success;
        public final String error;
        
        public OcrResult(String text, float confidence, boolean success) {
            this(text, confidence, success, null);
        }
        
        public OcrResult(String text, float confidence, boolean success, String error) {
            this.text = text;
            this.confidence = confidence;
            this.success = success;
            this.error = error;
        }
    }
} 