package com.datacollector.android.activities;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.datacollector.android.R;
import com.datacollector.android.views.MoodFaceView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 辅助工具 Activity：将 MoodFaceView 在 6 个不同 stress 等级下的真实渲染结果
 * 横排展示并导出为一张 PNG 图片，用于论文插图。
 *
 * 使用方式：从 SystemSettings 或 adb 启动此 Activity，点击"导出为 PNG"按钮，
 * 图片会保存到设备的 Pictures 目录。
 */
public class FaceExportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_export);

        // Set each face to its corresponding stress level (score / 100)
        MoodFaceView face0 = findViewById(R.id.face_0);
        MoodFaceView face20 = findViewById(R.id.face_20);
        MoodFaceView face40 = findViewById(R.id.face_40);
        MoodFaceView face60 = findViewById(R.id.face_60);
        MoodFaceView face80 = findViewById(R.id.face_80);
        MoodFaceView face100 = findViewById(R.id.face_100);

        // Use setStressImmediate so they render at the correct level without animation
        face0.setGlobalAlpha(255);   // Full opacity for paper figure
        face20.setGlobalAlpha(255);
        face40.setGlobalAlpha(255);
        face60.setGlobalAlpha(255);
        face80.setGlobalAlpha(255);
        face100.setGlobalAlpha(255);

        face0.setStressImmediate(0.0f);
        face20.setStressImmediate(0.2f);
        face40.setStressImmediate(0.4f);
        face60.setStressImmediate(0.6f);
        face80.setStressImmediate(0.8f);
        face100.setStressImmediate(1.0f);

        TextView tvStatus = findViewById(R.id.tv_status);
        Button btnExport = findViewById(R.id.btn_export);

        btnExport.setOnClickListener(v -> {
            View faceRow = findViewById(R.id.face_row);
            exportViewAsPng(faceRow, tvStatus);
        });
    }

    private void exportViewAsPng(View view, TextView tvStatus) {
        // Ensure the view is fully laid out
        view.setDrawingCacheEnabled(false);

        int width = view.getWidth();
        int height = view.getHeight();

        if (width == 0 || height == 0) {
            tvStatus.setText("错误：视图尚未渲染完成，请稍后重试");
            return;
        }

        // Render the view to a bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // Draw white background
        canvas.drawColor(0xFFFFFFFF);
        view.draw(canvas);

        // Save to Pictures directory
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "mood_face_scores.png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CATIA3");

                Uri uri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    tvStatus.setText("已保存到: Pictures/CATIA3/mood_face_scores.png");
                    Toast.makeText(this, "导出成功！", Toast.LENGTH_LONG).show();
                }
            } else {
                // Legacy path for older Android
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "CATIA3");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, "mood_face_scores.png");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                tvStatus.setText("已保存到: " + file.getAbsolutePath());
                Toast.makeText(this, "导出成功！", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            tvStatus.setText("导出失败: " + e.getMessage());
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
        } finally {
            bitmap.recycle();
        }
    }
}
