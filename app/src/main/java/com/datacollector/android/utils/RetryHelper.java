package com.datacollector.android.utils;

import android.util.Log;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 借鉴Beiwe的网络重试机制：API调用失败时自动重试，带指数退避。
 * Beiwe的S3上传使用3次重试，我们将此模式应用到LLM API调用中。
 */
public class RetryHelper {

    private static final String TAG = "RetryHelper";

    public static final int DEFAULT_MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 30000;

    public interface RetryCallback {
        void onSuccess(Response response) throws IOException;
        void onFinalFailure(String error);
    }

    /**
     * 带指数退避的HTTP请求重试
     */
    public static void executeWithRetry(OkHttpClient client, Request request,
                                        int maxRetries, RetryCallback callback) {
        executeWithRetryInternal(client, request, 0, maxRetries, callback);
    }

    private static void executeWithRetryInternal(OkHttpClient client, Request request,
                                                  int attempt, int maxRetries,
                                                  RetryCallback callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (attempt < maxRetries) {
                    long delay = calculateDelay(attempt);
                    Log.w(TAG, "Request failed (attempt " + (attempt + 1) + "/" + (maxRetries + 1)
                            + "), retrying in " + delay + "ms: " + e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    executeWithRetryInternal(client, request, attempt + 1, maxRetries, callback);
                } else {
                    Log.e(TAG, "Request failed after " + (maxRetries + 1) + " attempts", e);
                    callback.onFinalFailure("网络请求失败（已重试" + maxRetries + "次）: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(response);
                } else if (isRetryableStatusCode(response.code()) && attempt < maxRetries) {
                    response.close();
                    long delay = calculateDelay(attempt);
                    Log.w(TAG, "Server error " + response.code() + " (attempt " + (attempt + 1)
                            + "), retrying in " + delay + "ms");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    executeWithRetryInternal(client, request, attempt + 1, maxRetries, callback);
                } else {
                    // Non-retryable error or max retries reached - pass to callback as success for status handling
                    callback.onSuccess(response);
                }
            }
        });
    }

    private static long calculateDelay(int attempt) {
        long delay = BASE_DELAY_MS * (1L << attempt); // exponential: 1s, 2s, 4s, 8s...
        return Math.min(delay, MAX_DELAY_MS);
    }

    private static boolean isRetryableStatusCode(int code) {
        return code == 429 || code == 500 || code == 502 || code == 503 || code == 504;
    }
}
