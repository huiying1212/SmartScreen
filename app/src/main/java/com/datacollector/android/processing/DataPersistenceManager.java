package com.datacollector.android.processing;

import android.content.Context;
import android.util.Log;

import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.utils.CollectionStats;
import com.datacollector.android.utils.DataEncryptor;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * 数据持久化管理器。
 *
 * 负责将采集到的上下文数据保存到磁盘，支持：
 *   - JSON 明文
 *   - GZIP 压缩
 *   - AES 加密（可选叠加压缩）
 *
 * 从 DataCollectionService 中提取，属于中层处理逻辑。
 */
public class DataPersistenceManager {

    private static final String TAG = "DataPersistence";

    private final Context context;
    private final DataEncryptor dataEncryptor;
    private final CollectionConfig collectionConfig;
    private final CollectionStats collectionStats;

    public DataPersistenceManager(Context context) {
        this.context = context.getApplicationContext();
        this.dataEncryptor = new DataEncryptor(context);
        this.collectionConfig = CollectionConfig.getInstance(context);
        this.collectionStats = CollectionStats.getInstance(context);
    }

    /**
     * 保存上下文数据到磁盘。
     *
     * @param contextData 要保存的上下文数据
     * @return 保存后的包装 JSONObject（含 context_data + collection_time），或 null 表示失败
     */
    public JSONObject save(JSONObject contextData) {
        try {
            JSONObject outputData = new JSONObject();
            outputData.put("context_data", contextData);
            outputData.put("collection_time", System.currentTimeMillis());

            File dataDir = new File(context.getExternalFilesDir(null), "data");
            if (!dataDir.exists()) dataDir.mkdirs();

            String jsonString = outputData.toString(4);
            boolean encryptionEnabled = collectionConfig.getBoolean(
                    CollectionConfig.KEY_DATA_ENCRYPTION, true);
            boolean compressionEnabled = collectionConfig.getBoolean(
                    CollectionConfig.KEY_DATA_COMPRESSION, true);

            String fileName = "context_data_" + System.currentTimeMillis();
            File dataFile;

            if (encryptionEnabled) {
                byte[] data = jsonString.getBytes("UTF-8");
                if (compressionEnabled) data = compressGzip(data);
                byte[] encrypted = dataEncryptor.encryptBytes(data);
                if (encrypted != null) {
                    dataFile = new File(dataDir, fileName + ".enc");
                    try (FileOutputStream fos = new FileOutputStream(dataFile)) {
                        fos.write(encrypted);
                    }
                } else {
                    dataFile = new File(dataDir, fileName + ".json");
                    try (FileWriter fw = new FileWriter(dataFile)) { fw.write(jsonString); }
                }
            } else if (compressionEnabled) {
                dataFile = new File(dataDir, fileName + ".json.gz");
                try (FileOutputStream fos = new FileOutputStream(dataFile);
                     GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
                    gzos.write(jsonString.getBytes("UTF-8"));
                }
            } else {
                dataFile = new File(dataDir, fileName + ".json");
                try (FileWriter fw = new FileWriter(dataFile)) { fw.write(jsonString); }
            }

            collectionStats.recordFileSaved(dataFile.length());
            Log.d(TAG, "Saved: " + dataFile.getName());

            // 仅在未加密时才写明文副本（加密模式下不再泄漏明文）
            if (!encryptionEnabled) {
                File plainFile = new File(dataDir, "context_data_latest.json");
                try (FileWriter fw = new FileWriter(plainFile)) { fw.write(jsonString); }
            }

            return outputData;

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error saving context data", e);
            return null;
        }
    }

    private byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) { gzos.write(data); }
        return bos.toByteArray();
    }
}
