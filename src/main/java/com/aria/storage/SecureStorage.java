// SecureStorage.java
package com.aria.storage;

import java.util.prefs.Preferences;

public class SecureStorage {
    private static final Preferences prefs = Preferences.userNodeForPackage(SecureStorage.class);

    public static void storeApiKey(String keyName, String keyValue) {
        prefs.put(keyName, encrypt(keyValue));
    }

    public static String getApiKey(String keyName) {
        String encrypted = prefs.get(keyName, null);
        return encrypted != null ? decrypt(encrypted) : null;
    }

    private static String encrypt(String data) {
        // Simple encryption - replace with proper encryption in production
        return new StringBuilder(data).reverse().toString();
    }

    private static String decrypt(String data) {
        return new StringBuilder(data).reverse().toString();
    }
}