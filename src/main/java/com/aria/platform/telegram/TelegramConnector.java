// TelegramConnector.java
package com.aria.platform.telegram;

import com.aria.core.ConfigurationManager;
import com.aria.platform.PlatformConnector;
import com.aria.core.model.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelegramConnector implements PlatformConnector {
    private final String apiId;
    private final String apiHash;
    private final String phoneNumber;
    private final String password;
    private boolean isConnected;

    public TelegramConnector() {
        this.apiId = ConfigurationManager.getRequiredProperty("telegram.api.id");
        this.apiHash = ConfigurationManager.getRequiredProperty("telegram.api.hash");
        this.phoneNumber = ConfigurationManager.getProperty("telegram.phone", "");
        this.password = ConfigurationManager.getRequiredProperty("telegram.telegram.password");
        this.isConnected = false;
    }

    @Override
    public void ingestChatHistory() {
        System.out.println("Running telegram python code to get chat history");
        try {
            // Create ProcessBuilder for the Python script
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    "scripts/telethon/chat_ingestor.py"
            );

            // Set environment variables from your config
            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);
            env.put("TELEGRAM_PASSWORD", this.password);
            try {
                env.put("TELEGRAM_CODE", ConfigurationManager.getRequiredProperty("telegram.code"));
            }catch (Exception ignored){
                System.out.println("No Telegram Code Yet");
            }

            // Set the working directory to project root
            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());

            // Redirect error stream to see Python errors
            processBuilder.redirectErrorStream(true);

            System.out.println("Starting chat ingestion with Python script...");
            Process process = processBuilder.start();

            // Read Python script output
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
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    "scripts/telethon/message_sender.py",
                    target,
                    message
            );

            // Set environment variables for message sender too
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

    // Update the parseChatExport method to be in this class
    private void parseChatExport() {
        TelethonBridge bridge = new TelethonBridge();
        Map<String, List<Message>> chats = bridge.parseChatExport();

        // Optionally save chats to orchestrator or DB
        System.out.println("Loaded " + chats.size() + " chat histories into memory.");
    }

    @Override
    public boolean testConnection() {
        try {
            // Test both Python and Telegram connection
            Process process = Runtime.getRuntime().exec("python --version");
            int pythonExitCode = process.waitFor();

            if (pythonExitCode != 0) {
                System.err.println("Python is not available");
                return false;
            }

            // Test if we have the required configuration
            if (apiId == null || apiId.isEmpty() || apiHash == null || apiHash.isEmpty()) {
                System.err.println("Telegram API credentials not configured");
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
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
        // Implement this based on your chat export format
        return new HashMap<>();
    }

    @Override
    public void connect() {
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