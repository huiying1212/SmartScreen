package com.datacollector.android.api;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.datacollector.android.utils.CollectionConfig;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * 千问Qwen-Image文生图API客户端（同步接口）
 * 使用DashScope multimodal-generation/generation端点生成图片，
 * 返回的图片URL有效期24小时。
 */
public class QwenImageApiClient {

    private static final String TAG = "QwenImageApiClient";
    private static final String API_URL = ApiConfig.QWEN_IMAGE_API_URL;
    private static final String API_KEY = ApiConfig.QWEN_IMAGE_API_KEY;

    private final Context context;
    private final OkHttpClient httpClient;
    private final OkHttpClient downloadClient;

    public interface ImageGenerationCallback {
        void onSuccess(String imageUrl, Bitmap bitmap);
        void onError(String error);
    }

    public QwenImageApiClient(Context context) {
        this.context = context.getApplicationContext();
        CollectionConfig config = CollectionConfig.getInstance(context);
        int connectTimeout = config.getInt(CollectionConfig.KEY_API_CONNECT_TIMEOUT, 30);
        int readTimeout = Math.max(config.getInt(CollectionConfig.KEY_API_READ_TIMEOUT, 60), 120);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        // Separate client for downloading pre-signed OSS URLs (no Authorization header)
        this.downloadClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * Generate an image from a text prompt. Runs on the calling thread.
     * Call from a background thread.
     *
     * @param prompt       positive prompt (<= 800 chars)
     * @param negativePrompt negative prompt (<= 500 chars, nullable)
     * @param size         output size, e.g. "1080*1920" -- must be one of the supported sizes
     */
    public void generateImage(String prompt, String negativePrompt, String size,
                              ImageGenerationCallback callback) {
        if (!ApiConfig.isQwenImageApiKeyConfigured()) {
            callback.onError("Qwen-Image API key 未配置");
            return;
        }

        try {
            JSONObject requestJson = buildRequest(prompt, negativePrompt, size);

            RequestBody body = RequestBody.create(
                    requestJson.toString(), MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                okhttp3.ResponseBody rb = response.body();
                String responseBody = rb != null ? rb.string() : "";

                if (!response.isSuccessful()) {
                    callback.onError("API错误 " + response.code() + ": " + responseBody);
                    return;
                }

                String imageUrl = parseImageUrl(responseBody);
                if (imageUrl == null) {
                    callback.onError("无法从响应中解析图片URL: " + responseBody);
                    return;
                }

                Log.i(TAG, "Image generated, downloading from: " + imageUrl);
                Bitmap bitmap = downloadBitmapViaFile(imageUrl);
                if (bitmap == null) {
                    callback.onError("图片下载或解码失败，URL: " + imageUrl);
                    return;
                }
                Log.i(TAG, "Bitmap decoded: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                callback.onSuccess(imageUrl, bitmap);
            }

        } catch (JSONException e) {
            Log.e(TAG, "构建请求失败", e);
            callback.onError("构建请求失败: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "网络请求失败", e);
            callback.onError("网络请求失败: " + e.getMessage());
        }
    }

    private JSONObject buildRequest(String prompt, String negativePrompt, String size)
            throws JSONException {
        JSONObject requestJson = new JSONObject();
        // qwen-image-max supports the sync multimodal-generation endpoint.
        // qwen-image-plus only supports the async text2image endpoint.
        requestJson.put("model", "qwen-image-max");

        // input.messages
        JSONObject textContent = new JSONObject();
        textContent.put("text", prompt);

        JSONArray contentArray = new JSONArray();
        contentArray.put(textContent);

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", contentArray);

        JSONArray messages = new JSONArray();
        messages.put(userMessage);

        JSONObject input = new JSONObject();
        input.put("messages", messages);
        requestJson.put("input", input);

        // parameters
        JSONObject parameters = new JSONObject();
        if (negativePrompt != null && !negativePrompt.isEmpty()) {
            parameters.put("negative_prompt", negativePrompt);
        }
        parameters.put("prompt_extend", true);
        parameters.put("watermark", false);

        if (size != null && !size.isEmpty()) {
            parameters.put("size", size);
        } else {
            parameters.put("size", "928*1664"); // 9:16 portrait, ideal for phone wallpaper
        }

        requestJson.put("parameters", parameters);
        return requestJson;
    }

    private String parseImageUrl(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONObject output = json.optJSONObject("output");
            if (output == null) return null;

            JSONArray choices = output.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return null;

            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.optJSONObject("message");
            if (message == null) return null;

            JSONArray content = message.optJSONArray("content");
            if (content == null || content.length() == 0) return null;

            JSONObject firstContent = content.getJSONObject(0);
            return firstContent.optString("image", null);

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing image response", e);
            return null;
        }
    }

    /**
     * Download image to a temp file, then decode to Bitmap.
     * Uses a separate OkHttpClient with no auth headers (OSS pre-signed URLs are self-contained).
     */
    private Bitmap downloadBitmapViaFile(String url) {
        File tempFile = new File(context.getCacheDir(),
                "qwen_temp_" + System.currentTimeMillis() + ".png");
        try {
            // Use downloadClient (no Authorization header) — OSS pre-signed URLs
            // embed auth in query params and reject extra Authorization headers.
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            Log.d(TAG, "Downloading image from URL (first 120 chars): "
                    + url.substring(0, Math.min(url.length(), 120)));

            try (Response response = downloadClient.newCall(request).execute()) {
                int code = response.code();
                Log.d(TAG, "Download response code: " + code);

                okhttp3.ResponseBody responseBody = response.body();
                if (!response.isSuccessful()) {
                    String errBody = responseBody != null ? responseBody.string() : "(empty)";
                    Log.e(TAG, "Image download failed " + code + ": " + errBody);
                    return null;
                }
                if (responseBody == null) {
                    Log.e(TAG, "Image download: response body is null");
                    return null;
                }

                String contentType = responseBody.contentType() != null
                        ? responseBody.contentType().toString() : "unknown";
                long contentLength = responseBody.contentLength();
                Log.d(TAG, "Content-Type: " + contentType + ", Content-Length: " + contentLength);

                try (InputStream is = responseBody.byteStream();
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[16384];
                    int bytesRead;
                    long totalBytes = 0;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    fos.flush();
                    Log.i(TAG, "Saved " + totalBytes + " bytes -> " + tempFile.getAbsolutePath());
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0) {
                Log.e(TAG, "Temp file empty after download, size="
                        + (tempFile.exists() ? tempFile.length() : -1));
                return null;
            }

            // Probe dimensions first (no memory allocation)
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tempFile.getAbsolutePath(), opts);
            Log.d(TAG, "Image dimensions: " + opts.outWidth + "x" + opts.outHeight
                    + " mime=" + opts.outMimeType);

            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                Log.e(TAG, "Cannot decode image dimensions — file may be corrupt or not an image");
                return null;
            }

            // Scale down if larger than 2048px to avoid OOM
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = 1;
            int maxDim = 2048;
            while (opts.outWidth / opts.inSampleSize > maxDim
                    || opts.outHeight / opts.inSampleSize > maxDim) {
                opts.inSampleSize *= 2;
            }
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bitmap = BitmapFactory.decodeFile(tempFile.getAbsolutePath(), opts);
            if (bitmap != null) {
                Log.i(TAG, "Decoded bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight()
                        + " (sample=" + opts.inSampleSize + ")");
            } else {
                Log.e(TAG, "BitmapFactory.decodeFile returned null");
            }
            return bitmap;

        } catch (IOException e) {
            Log.e(TAG, "IOException during image download", e);
            return null;
        } finally {
            tempFile.delete();
        }
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        downloadClient.dispatcher().executorService().shutdown();
    }
}
