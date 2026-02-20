package com.datacollector.android.utils;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 借鉴Beiwe的ErrorHandler模式：收集处理过程中的所有错误，
 * 单个组件的失败不会中断整个数据采集/分析流程。
 * Beiwe中一个文件处理失败不影响其他文件；我们在此实现相同理念。
 */
public class ErrorCollector {

    private static final String TAG = "ErrorCollector";

    private final String scope;
    private final List<ErrorEntry> errors = new ArrayList<>();

    public ErrorCollector(String scope) {
        this.scope = scope;
    }

    /**
     * 在安全上下文中执行操作，捕获异常而不中断流程
     */
    public void runSafely(String operationName, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            errors.add(new ErrorEntry(operationName, e));
            Log.e(TAG, "[" + scope + "] Error in " + operationName + ": " + e.getMessage(), e);
        }
    }

    /**
     * 在安全上下文中执行并返回结果
     */
    public <T> T runSafelyWithResult(String operationName, java.util.concurrent.Callable<T> action, T fallback) {
        try {
            return action.call();
        } catch (Exception e) {
            errors.add(new ErrorEntry(operationName, e));
            Log.e(TAG, "[" + scope + "] Error in " + operationName + ": " + e.getMessage(), e);
            return fallback;
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public List<ErrorEntry> getErrors() {
        return new ArrayList<>(errors);
    }

    public String getSummary() {
        if (errors.isEmpty()) {
            return scope + ": no errors";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(scope).append(": ").append(errors.size()).append(" error(s)\n");
        for (ErrorEntry entry : errors) {
            sb.append("  - ").append(entry.operation).append(": ")
              .append(entry.exception.getMessage()).append("\n");
        }
        return sb.toString();
    }

    public void clear() {
        errors.clear();
    }

    public static class ErrorEntry {
        public final String operation;
        public final Exception exception;
        public final long timestamp;

        ErrorEntry(String operation, Exception exception) {
            this.operation = operation;
            this.exception = exception;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
