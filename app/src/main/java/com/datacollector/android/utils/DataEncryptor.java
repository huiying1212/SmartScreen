package com.datacollector.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 借鉴Beiwe的数据加密策略：所有敏感数据在本地存储前进行AES-GCM加密。
 * 密钥在首次运行时生成并存储在SharedPreferences中（生产环境应使用Android Keystore）。
 */
public class DataEncryptor {

    private static final String TAG = "DataEncryptor";
    private static final String PREFS_NAME = "encryption_prefs";
    private static final String KEY_AES = "aes_key";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int KEY_SIZE = 256;

    private final SecretKey secretKey;

    public DataEncryptor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_AES, null);
        if (stored != null) {
            byte[] decoded = Base64.decode(stored, Base64.NO_WRAP);
            this.secretKey = new SecretKeySpec(decoded, "AES");
        } else {
            this.secretKey = generateKey();
            String encoded = Base64.encodeToString(secretKey.getEncoded(), Base64.NO_WRAP);
            prefs.edit().putString(KEY_AES, encoded).apply();
        }
    }

    private SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(KEY_SIZE, new SecureRandom());
            return keyGen.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate AES key", e);
            throw new RuntimeException("Encryption key generation failed", e);
        }
    }

    /**
     * 加密字符串，返回 Base64 编码的 (IV + ciphertext)
     */
    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    /**
     * 解密 Base64 编码的密文
     */
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.decode(ciphertext, Base64.NO_WRAP);

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }

    /**
     * 加密字节数组
     */
    public byte[] encryptBytes(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] encrypted = cipher.doFinal(data);
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            return combined;
        } catch (Exception e) {
            Log.e(TAG, "Byte encryption failed", e);
            return null;
        }
    }

    /**
     * 解密字节数组
     */
    public byte[] decryptBytes(byte[] combined) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            Log.e(TAG, "Byte decryption failed", e);
            return null;
        }
    }
}
