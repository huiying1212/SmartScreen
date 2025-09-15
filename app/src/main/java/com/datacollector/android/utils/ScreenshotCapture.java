package com.datacollector.android.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 屏幕截图捕获工具类
 * 支持多种截图方式：View截图、MediaProjection截图等
 */
public class ScreenshotCapture {
    
    private static final String TAG = "ScreenshotCapture";
    private static final String SCREENSHOT_DIR = "screenshots";
    
    private Context context;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    public ScreenshotCapture(Context context) {
        this.context = context;
        initializeScreenMetrics();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
    }
    
    /**
     * 截图回调接口
     */
    public interface ScreenshotCallback {
        void onSuccess(Bitmap bitmap, String filePath);
        void onError(String error);
    }
    
    /**
     * 初始化屏幕参数
     */
    private void initializeScreenMetrics() {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        
        Log.d(TAG, String.format("屏幕参数: %dx%d, 密度: %d", screenWidth, screenHeight, screenDensity));
    }
    
    /**
     * 从View截图（适用于应用内截图）
     */
    public void captureView(View view, ScreenshotCallback callback) {
        if (view == null) {
            callback.onError("目标View为空");
            return;
        }
        
        try {
            // 创建与View相同大小的Bitmap
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            
            // 保存截图到文件
            String filePath = saveBitmapToFile(bitmap, "view_screenshot");
            callback.onSuccess(bitmap, filePath);
            
            Log.d(TAG, "View截图成功: " + filePath);
            
        } catch (Exception e) {
            Log.e(TAG, "View截图失败", e);
            callback.onError("View截图失败: " + e.getMessage());
        }
    }
    
    /**
     * 设置MediaProjection（需要在Activity中获取权限后调用）
     */
    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
        setupImageReader();
    }
    
    /**
     * 设置ImageReader
     */
    private void setupImageReader() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        }
    }
    
    /**
     * 使用MediaProjection进行全屏截图（Android 5.0+）
     */
    public void captureScreen(ScreenshotCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            callback.onError("当前Android版本不支持屏幕截图");
            return;
        }
        
        if (mediaProjection == null) {
            callback.onError("MediaProjection未初始化，请先获取屏幕录制权限");
            return;
        }
        
        try {
            // 设置图片读取监听器
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image == null) {
                        callback.onError("无法获取屏幕图像");
                        return;
                    }
                    
                    try {
                        // 将Image转换为Bitmap
                        Bitmap bitmap = imageToBitmap(image);
                        String filePath = saveBitmapToFile(bitmap, "screen_screenshot");
                        
                        // 在主线程回调
                        new Handler(Looper.getMainLooper()).post(() -> {
                            callback.onSuccess(bitmap, filePath);
                        });
                        
                        Log.d(TAG, "屏幕截图成功: " + filePath);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "处理截图时出错", e);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            callback.onError("处理截图时出错: " + e.getMessage());
                        });
                    } finally {
                        image.close();
                        stopScreenCapture(); // 停止虚拟显示
                    }
                }
            }, null);
            
            // 创建虚拟显示
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, null
            );
            
        } catch (Exception e) {
            Log.e(TAG, "屏幕截图失败", e);
            callback.onError("屏幕截图失败: " + e.getMessage());
        }
    }
    
    /**
     * 将Image转换为Bitmap
     */
    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        
        // 创建Bitmap
        Bitmap bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride, 
            screenHeight, 
            Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);
        
        // 裁剪到正确尺寸
        if (rowPadding > 0) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
        }
        
        return bitmap;
    }
    
    /**
     * 保存Bitmap到文件
     */
    private String saveBitmapToFile(Bitmap bitmap, String prefix) throws IOException {
        // 创建截图目录
        File screenshotDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), SCREENSHOT_DIR);
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
        }
        
        // 生成文件名
        String fileName = prefix + "_" + System.currentTimeMillis() + ".png";
        File file = new File(screenshotDir, fileName);
        
        // 保存Bitmap到文件
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        }
        
        return file.getAbsolutePath();
    }
    
    /**
     * 停止屏幕捕获
     */
    private void stopScreenCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopScreenCapture();
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        Log.d(TAG, "截图工具资源已释放");
    }
    
    /**
     * 获取屏幕尺寸信息
     */
    public DisplayMetrics getScreenMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.widthPixels = screenWidth;
        metrics.heightPixels = screenHeight;
        metrics.densityDpi = screenDensity;
        return metrics;
    }
} 