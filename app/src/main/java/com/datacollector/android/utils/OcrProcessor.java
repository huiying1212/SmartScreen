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
    private OcrLogger ocrLogger;
    
    public OcrProcessor(Context context) {
        this.context = context;
        this.ocrLogger = new OcrLogger(context);
        initializeRecognizers();
    }
    
    /**
     * 初始化文字识别器
     */
    private void initializeRecognizers() {
        try {
            Log.d(TAG, "开始初始化OCR识别器...");
            ocrLogger.logOcrInitialization(true, "开始初始化OCR识别器");
            
            // 初始化拉丁文本识别器
            latinTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Log.d(TAG, "拉丁文本识别器创建成功");
            ocrLogger.logModelDownloadStart("拉丁文本识别器");
            
            // 初始化中文文本识别器
            chineseTextRecognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            Log.d(TAG, "中文文本识别器创建成功");
            ocrLogger.logModelDownloadStart("中文文本识别器");
            
            Log.d(TAG, "OCR识别器初始化成功");
            ocrLogger.logOcrInitialization(true, "OCR识别器初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "OCR识别器初始化失败", e);
            
            // 记录具体的初始化失败原因
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("network") || e.getMessage().contains("download")) {
                    Log.e(TAG, "OCR模型下载失败，可能是网络连接问题: " + e.getMessage());
                    ocrLogger.logModelDownloadFailure("OCR识别器", "网络连接问题: " + errorMsg);
                } else if (e.getMessage().contains("storage") || e.getMessage().contains("space")) {
                    Log.e(TAG, "OCR模型下载失败，可能是存储空间不足: " + e.getMessage());
                    ocrLogger.logModelDownloadFailure("OCR识别器", "存储空间不足: " + errorMsg);
                } else if (e.getMessage().contains("permission")) {
                    Log.e(TAG, "OCR模型下载失败，可能是权限问题: " + e.getMessage());
                    ocrLogger.logModelDownloadFailure("OCR识别器", "权限问题: " + errorMsg);
                } else {
                    Log.e(TAG, "OCR模型下载失败，未知原因: " + e.getMessage());
                    ocrLogger.logModelDownloadFailure("OCR识别器", "未知原因: " + errorMsg);
                }
            }
            ocrLogger.logOcrInitialization(false, "OCR识别器初始化失败: " + errorMsg);
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
                        ocrLogger.logOcrSuccess(String.valueOf(recognizedText.length()), confidence);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "中文OCR识别失败，尝试拉丁文识别", e);
                    
                    // 检查是否是模型下载失败
                    if (e.getMessage() != null) {
                        if (e.getMessage().contains("model") || e.getMessage().contains("download") || 
                            e.getMessage().contains("network") || e.getMessage().contains("connection")) {
                            Log.e(TAG, "中文OCR模型可能下载失败: " + e.getMessage());
                            ocrLogger.logModelDownloadFailure("中文文本识别器", e.getMessage());
                        }
                    }
                    ocrLogger.logOcrFailure("中文OCR识别失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
                    
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
                ocrLogger.logOcrSuccess(String.valueOf(recognizedText.length()), confidence);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "拉丁文OCR识别也失败", e);
                
                // 检查是否是模型下载失败
                String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                if (e.getMessage() != null) {
                    if (e.getMessage().contains("model") || e.getMessage().contains("download") || 
                        e.getMessage().contains("network") || e.getMessage().contains("connection")) {
                        Log.e(TAG, "拉丁文OCR模型可能下载失败: " + e.getMessage());
                        ocrLogger.logModelDownloadFailure("拉丁文本识别器", e.getMessage());
                        callback.onError("OCR模型下载失败: " + e.getMessage());
                    } else {
                        ocrLogger.logOcrFailure("拉丁文OCR识别失败: " + errorMsg);
                        callback.onError("OCR识别失败: " + e.getMessage());
                    }
                } else {
                    ocrLogger.logOcrFailure("拉丁文OCR识别失败: 未知错误");
                    callback.onError("OCR识别失败: 未知错误");
                }
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
     * 检查OCR模型是否可用
     */
    public boolean isOcrModelAvailable() {
        try {
            if (latinTextRecognizer == null || chineseTextRecognizer == null) {
                Log.w(TAG, "OCR识别器未初始化");
                return false;
            }
            
            // 检查识别器对象是否存在
            // 注意：Google ML Kit的模型下载是在第一次使用时进行的
            // 这里我们只能检查识别器对象是否创建成功
            Log.d(TAG, "OCR模型检查: 识别器对象已创建");
            Log.i(TAG, "注意：OCR模型将在首次使用时自动下载，请确保网络连接正常");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "OCR模型检查失败", e);
            return false;
        }
    }
    
    /**
     * 测试OCR模型下载状态（使用小图片进行测试）
     */
    public void testOcrModelDownload(OcrCallback callback) {
        try {
            // 创建一个1x1像素的测试图片
            Bitmap testBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            testBitmap.setPixel(0, 0, 0xFF000000); // 黑色像素
            
            Log.d(TAG, "开始测试OCR模型下载状态...");
            
            // 使用中文识别器进行测试
            chineseTextRecognizer.process(InputImage.fromBitmap(testBitmap, 0))
                .addOnSuccessListener(visionText -> {
                    Log.d(TAG, "OCR模型测试成功，模型已可用");
                    ocrLogger.logModelDownloadSuccess("中文文本识别器测试");
                    testBitmap.recycle();
                    callback.onSuccess("OCR模型测试成功", 1.0f);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR模型测试失败", e);
                    testBitmap.recycle();
                    
                    // 检查是否是模型下载问题
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                    if (e.getMessage() != null && 
                        (e.getMessage().contains("model") || e.getMessage().contains("download") || 
                         e.getMessage().contains("network") || e.getMessage().contains("connection"))) {
                        Log.e(TAG, "OCR模型下载失败: " + e.getMessage());
                        ocrLogger.logModelDownloadFailure("中文文本识别器测试", errorMsg);
                        callback.onError("OCR模型下载失败: " + e.getMessage());
                    } else {
                        ocrLogger.logOcrFailure("OCR模型测试失败: " + errorMsg);
                        callback.onError("OCR模型测试失败: " + e.getMessage());
                    }
                });
                
        } catch (Exception e) {
            Log.e(TAG, "OCR模型测试过程出错", e);
            callback.onError("OCR模型测试过程出错: " + e.getMessage());
        }
    }
    
    /**
     * 获取OCR模型状态信息
     */
    public String getOcrModelStatus() {
        StringBuilder status = new StringBuilder();
        
        if (latinTextRecognizer != null) {
            status.append("拉丁文识别器: 已初始化\n");
        } else {
            status.append("拉丁文识别器: 未初始化\n");
        }
        
        if (chineseTextRecognizer != null) {
            status.append("中文识别器: 已初始化\n");
        } else {
            status.append("中文识别器: 未初始化\n");
        }
        
        return status.toString();
    }
    
    /**
     * 获取OCR日志文件路径
     */
    public String getOcrLogFilePath() {
        return ocrLogger != null ? ocrLogger.getLogFilePath() : "OCR日志未初始化";
    }
    
    /**
     * 清理OCR日志
     */
    public void cleanupOcrLogs() {
        if (ocrLogger != null) {
            ocrLogger.cleanupOldLogs();
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