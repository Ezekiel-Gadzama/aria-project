// TelegramConnector.java
package com.aria.platform.telegram;

import com.aria.platform.PlatformConnector;
import com.aria.core.model.Message;
import com.aria.platform.telegram.TelethonBridge;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

public class TelegramConnector implements PlatformConnector {
    private final TelethonBridge telethonBridge;
    private boolean isConnected;

    public TelegramConnector() {
        this.telethonBridge = new TelethonBridge();
        this.isConnected = false;
    }

    @Override
    public void ingestChatHistory() {
        telethonBridge.ingestChatHistory();
    }

    @Override
    public boolean sendMessage(String target, String message) {
        if (!isConnected) {
            connect();
        }

        try {
            // Use the telethon bridge to send message
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    "scripts/telethon/message_sender.py",
                    target,
                    message
            );

            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean testConnection() {
        try {
            Process process = Runtime.getRuntime().exec("python --version");
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getPlatformName() {
        return "telegram";
    }

    @Override
    public Map<String, List<Message>> getHistoricalChats() {
        // This would parse the exported chat data from Telethon
        // For now, return empty map - you'll implement this based on your chat export format
        return new HashMap<>();
    }

    @Override
    public void connect() {
        // Initialize connection to Telegram
        if (testConnection()) {
            isConnected = true;
            System.out.println("Connected to Telegram platform");
        } else {
            System.out.println("Failed to connect to Telegram");
        }
    }

    @Override
    public void disconnect() {
        isConnected = false;
        System.out.println("Disconnected from Telegram");
    }

    public boolean isConnected() {
        return isConnected;
    }
}