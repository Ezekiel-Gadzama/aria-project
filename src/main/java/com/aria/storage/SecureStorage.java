package com.aria.storage;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for encrypting/decrypting sensitive data (messages, etc.)
 * Uses AES-256-GCM for authenticated encryption.
 */
public class SecureStorage {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // 96 bits for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits for GCM tag
    private static final int KEY_LENGTH = 256; // 256 bits = 32 bytes

    private static SecretKey getSecretKey() {
        String keyEnv = System.getenv("ARIA_ENCRYPTION_KEY");
        if (keyEnv == null || keyEnv.trim().isEmpty()) {
            // Fallback: generate a development key (not secure for production)
            // This is a 32-byte key in Base64 format
            keyEnv = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="; // 32 bytes of zeros
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyEnv);
            // Ensure key is exactly 32 bytes
            if (keyBytes.length != 32) {
                byte[] padded = new byte[32];
                System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
                keyBytes = padded;
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ARIA_ENCRYPTION_KEY must be 32 bytes (256-bit) after Base64 decoding", e);
        }
    }

    /**
     * Encrypt text using AES-256-GCM
     */
    public static String encrypt(String plainText) {
        try {
            if (plainText == null) return null;
            SecretKey key = getSecretKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            
            // Combine IV and encrypted data
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt text previously encrypted by encrypt(...)
     * If decryption fails (e.g., text was never encrypted), returns the original text
     */
    public static String decrypt(String data) {
        try {
            if (data == null) return null;
            // Try to detect if data is encrypted (Base64 encoded with IV+GCM tag structure)
            try {
                byte[] combined = Base64.getDecoder().decode(data);
                // Encrypted data should have IV (12 bytes) + ciphertext (at least GCM tag 16 bytes)
                if (combined.length < IV_LENGTH + 16) {
                    // Too short to be encrypted, return as-is (plaintext)
                    return data;
                }
                byte[] iv = new byte[IV_LENGTH];
                byte[] cipherText = new byte[combined.length - IV_LENGTH];
                System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
                System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);
                byte[] plain = cipher.doFinal(cipherText);
                return new String(plain, StandardCharsets.UTF_8);
            } catch (java.lang.IllegalArgumentException e) {
                // Not valid Base64, return as-is (plaintext from Python ingestion)
                return data;
            } catch (javax.crypto.AEADBadTagException e) {
                // Decryption failed (wrong key or not encrypted), return as-is
                return data;
            } catch (Exception e) {
                // Other decryption errors - return as-is (might be plaintext)
                return data;
            }
        } catch (Exception e) {
            // If anything fails, return the original data (might be plaintext)
            return data;
        }
    }
}
