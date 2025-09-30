package com.aria.platform.telegram;

import com.aria.platform.PlatformConnector;
import com.aria.core.model.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelegramConnector implements PlatformConnector {
    private final String apiId;
    private final String apiHash;
    private final String phoneNumber;
    private boolean isConnected;

    // Updated constructor to accept parameters from UI
    public TelegramConnector(String apiId, String apiHash, String phoneNumber) {
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.phoneNumber = phoneNumber;
        this.isConnected = false;
    }

    // Keep default constructor for backward compatibility
    public TelegramConnector() {
        this.apiId = "";
        this.apiHash = "";
        this.phoneNumber = "";
        this.isConnected = false;
    }

    @Override
    public void ingestChatHistory() {
        if (!isConfigured()) {
            System.err.println("Telegram connector not configured properly");
            return;
        }

        System.out.println("Running telegram python code to get chat history");
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    "scripts/telethon/chat_ingestor.py"
            );

            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);

            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());
            processBuilder.redirectErrorStream(true);

            System.out.println("Starting chat ingestion with Python script...");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Python: " + line);
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("Chat ingestion completed successfully!");
                parseChatExport();
            } else {
                System.err.println("Python script failed with exit code: " + exitCode);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean sendMessage(String target, String message) {
        if (!isConfigured()) {
            System.err.println("Telegram connector not configured");
            return false;
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    "scripts/telethon/message_sender.py",
                    target,
                    message
            );

            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);

            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            return exitCode == 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void parseChatExport() {
        TelethonBridge bridge = new TelethonBridge();
        Map<String, List<Message>> chats = bridge.parseChatExport();
        System.out.println("Loaded " + chats.size() + " chat histories into memory.");
    }

    @Override
    public boolean testConnection() {
        return isConfigured();
    }

    @Override
    public String getPlatformName() {
        return "telegram";
    }

    @Override
    public String getApiHash() {
        return apiHash;
    }

    @Override
    public String getApiId() {
        return apiId;
    }

    @Override
    public Map<String, List<Message>> getHistoricalChats() {
        return new HashMap<>();
    }

    @Override
    public void connect() {
        if (testConnection()) {
            isConnected = true;
            System.out.println("Connected to Telegram platform");
        } else {
            System.out.println("Failed to connect to Telegram - not configured");
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

    private boolean isConfigured() {
        return apiId != null && !apiId.isEmpty() &&
                apiHash != null && !apiHash.isEmpty() &&
                phoneNumber != null && !phoneNumber.isEmpty();
    }
}