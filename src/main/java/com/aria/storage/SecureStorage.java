// SecureStorage.java
package com.aria.storage;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.prefs.Preferences;

/**
 * Secure storage helper for sensitive data (API keys, message text, etc.).
 * Uses AES-256-GCM with a key provided via ARIA_ENCRYPTION_KEY env var (Base64-encoded).
 */
public class SecureStorage {
    private static final Preferences prefs = Preferences.userNodeForPackage(SecureStorage.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final String ENV_KEY = "ARIA_ENCRYPTION_KEY";

    private static SecretKeySpec getSecretKey() {
        String base64Key = System.getenv(ENV_KEY);
        byte[] keyBytes;
        if (base64Key != null && !base64Key.isEmpty()) {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } else {
            // Fallback development key (NOT for production) - exactly 32 bytes
            byte[] fallback = "aria-default-dev-key-32-bytes!!!".getBytes(StandardCharsets.UTF_8);
            // Ensure exactly 32 bytes by padding or truncating
            if (fallback.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(fallback, 0, padded, 0, fallback.length);
                // Fill remainder with '!' characters
                for (int i = fallback.length; i < 32; i++) {
                    padded[i] = '!';
                }
                keyBytes = padded;
            } else if (fallback.length > 32) {
                byte[] truncated = new byte[32];
                System.arraycopy(fallback, 0, truncated, 0, 32);
                keyBytes = truncated;
            } else {
                keyBytes = fallback;
            }
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("ARIA_ENCRYPTION_KEY must be 32 bytes (256-bit) after Base64 decoding");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static void storeApiKey(String keyName, String keyValue) {
        prefs.put(keyName, encrypt(keyValue));
    }

    public static String getApiKey(String keyName) {
        String encrypted = prefs.get(keyName, null);
        return encrypted != null ? decrypt(encrypted) : null;
    }

    /**
     * Encrypt arbitrary text for storage (DB, prefs, etc.)
     */
    public static String encrypt(String data) {
        try {
            if (data == null) return null;
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec);
            byte[] cipherText = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt text previously encrypted by encrypt(...)
     */
    public static String decrypt(String data) {
        try {
            if (data == null) return null;
            byte[] combined = Base64.getDecoder().decode(data);
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}