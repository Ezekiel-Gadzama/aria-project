// ConfigurationManager.java
package com.aria.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationManager {
    private static final Properties properties = new Properties();
    private static boolean loaded = false;

    static {
        loadProperties();
    }

    private static void loadProperties() {
        try (InputStream input = ConfigurationManager.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                properties.load(input);
                loaded = true;
                System.out.println("Configuration loaded successfully");
            } else {
                System.out.println("Config file not found, using environment variables only");
            }
        } catch (IOException e) {
            System.out.println("Error loading config file: " + e.getMessage());
        }
    }

    public static String getProperty(String key, String defaultValue) {
        // First try environment variable
        String envValue = System.getenv(key.toUpperCase().replace('.', '_'));
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue;
        }

        // Then try config file
        if (loaded) {
            String configValue = properties.getProperty(key);
            if (configValue != null && !configValue.trim().isEmpty()) {
                return configValue;
            }
        }

        // Return default value
        return defaultValue;
    }

    public static String getRequiredProperty(String key) {
        String value = getProperty(key, null);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required configuration property not found: " + key +
                    "\nPlease set environment variable " + key.toUpperCase().replace('.', '_') +
                    " or add to config.properties");
        }
        return value;
    }

    public static int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double getDoubleProperty(String key, double defaultValue) {
        try {
            return Double.parseDouble(getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }
}