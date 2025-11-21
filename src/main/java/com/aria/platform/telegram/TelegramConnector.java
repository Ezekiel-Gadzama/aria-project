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
    private final String username;
    private final int platformAccountId;
    private boolean isConnected;

    // Updated constructor to accept parameters from UI
    public TelegramConnector(String apiId, String apiHash, String phoneNumber) {
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.phoneNumber = phoneNumber;
        this.username = "";
        this.isConnected = false;
        this.platformAccountId = 0;
    }

    public TelegramConnector(String apiId, String apiHash, String phoneNumber, int platformAccountId) {
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.phoneNumber = phoneNumber;
        this.username = "";
        this.platformAccountId = platformAccountId;
        this.isConnected = false;
    }

    public TelegramConnector(String apiId, String apiHash, String phoneNumber, String username, int platformAccountId) {
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.phoneNumber = phoneNumber;
        this.username = username != null ? username : "";
        this.platformAccountId = platformAccountId;
        this.isConnected = false;
    }

    // Keep default constructor for backward compatibility
    public TelegramConnector() {
        this.apiId = "";
        this.apiHash = "";
        this.phoneNumber = "";
        this.username = "";
        this.platformAccountId = 0;
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
                    "python3",
                    "scripts/telethon/chat_ingestor.py"
            );

            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);
            env.put("TELEGRAM_USERNAME", this.username);
            env.put("PLATFORM_ACCOUNT_ID", String.valueOf(this.platformAccountId));
            env.put("TELETHON_LOCK_PATH", "/app/telethon_send.lock");
            env.put("TELETHON_SESSION_PATH", buildSessionPath(this.username, this.phoneNumber));

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
        SendMessageResult result = sendMessageAndGetResult(target, message);
        return result != null && result.success;
    }

    /**
     * Result class for sending messages
     */
    public static class SendMessageResult {
        public final Long messageId;
        public final Long peerId;
        public final boolean success;
        
        public SendMessageResult(Long messageId, Long peerId, boolean success) {
            this.messageId = messageId;
            this.peerId = peerId;
            this.success = success;
        }
    }

    /**
     * Send a message and return the Telegram message ID and peer ID
     * @return The result containing message ID and peer ID, or null if sending failed
     */
    public SendMessageResult sendMessageAndGetResult(String target, String message) {
        if (!isConfigured()) {
            System.err.println("Telegram connector not configured");
            return null;
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python3",
                    "scripts/telethon/message_sender.py",
                    target,
                    message
            );

            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);
            env.put("TELEGRAM_USERNAME", this.username);
            env.put("PLATFORM_ACCOUNT_ID", String.valueOf(this.platformAccountId));
            env.put("TELETHON_LOCK_PATH", "/app/telethon_send.lock");
            env.put("TELETHON_SESSION_PATH", buildSessionPath(this.username, this.phoneNumber));

            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python: " + line);
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            
            // Parse JSON output from Python to extract message ID and peer ID
            if (exitCode == 0 && output.toString().contains("\"messageId\"")) {
                try {
                    String jsonLine = output.toString().lines()
                        .filter(l -> l.trim().startsWith("{"))
                        .findFirst()
                        .orElse(null);
                    if (jsonLine != null) {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonLine).getAsJsonObject();
                        if (json.has("messageId")) {
                            long msgId = json.get("messageId").getAsLong();
                            Long peerId = null;
                            if (json.has("peerId") && !json.get("peerId").isJsonNull()) {
                                peerId = json.get("peerId").getAsLong();
                            }
                            System.out.println("Extracted message ID: " + msgId + ", peer ID: " + peerId);
                            return new SendMessageResult(msgId, peerId, true);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing message result from Python output: " + e.getMessage());
                }
            }

            // If no message ID found but exit code is 0, assume success but return null result
            return exitCode == 0 ? new SendMessageResult(-1L, null, true) : null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean sendMedia(String target, String filePath) {
        SendMessageResult result = sendMediaAndGetResult(target, filePath);
        return result != null && result.success;
    }

    /**
     * Send media and return the Telegram message ID and peer ID
     * @param caption Optional caption text to send with the media
     * @return The result containing message ID and peer ID, or null if sending failed
     */
    public SendMessageResult sendMediaAndGetResult(String target, String filePath) {
        return sendMediaAndGetResult(target, filePath, null);
    }

    /**
     * Send media with caption and return the Telegram message ID and peer ID
     * @param caption Optional caption text to send with the media
     * @return The result containing message ID and peer ID, or null if sending failed
     */
    public SendMessageResult sendMediaAndGetResult(String target, String filePath, String caption) {
        if (!isConfigured()) {
            System.err.println("Telegram connector not configured");
            return null;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python3",
                    "scripts/telethon/media_sender.py",
                    target,
                    filePath,
                    caption != null && !caption.isEmpty() ? caption : ""
            );
            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);
            env.put("TELEGRAM_USERNAME", this.username);
            env.put("PLATFORM_ACCOUNT_ID", String.valueOf(this.platformAccountId));
            env.put("TELETHON_LOCK_PATH", "/app/telethon_send.lock");
            env.put("TELETHON_SESSION_PATH", buildSessionPath(this.username, this.phoneNumber));

            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python: " + line);
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            
            // Parse JSON output from Python to extract message ID and peer ID
            if (exitCode == 0 && output.toString().contains("\"messageId\"")) {
                try {
                    String jsonLine = output.toString().lines()
                        .filter(l -> l.trim().startsWith("{"))
                        .findFirst()
                        .orElse(null);
                    if (jsonLine != null) {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonLine).getAsJsonObject();
                        if (json.has("messageId")) {
                            long msgId = json.get("messageId").getAsLong();
                            Long peerId = null;
                            if (json.has("peerId") && !json.get("peerId").isJsonNull()) {
                                peerId = json.get("peerId").getAsLong();
                            }
                            System.out.println("Extracted media message ID: " + msgId + ", peer ID: " + peerId);
                            return new SendMessageResult(msgId, peerId, true);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing media message result from Python output: " + e.getMessage());
                }
            }

            // If no message ID found but exit code is 0, assume success but return null result
            return exitCode == 0 ? new SendMessageResult(-1L, null, true) : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean editMessage(String target, int messageId, String newText) {
        if (!isConfigured()) {
            System.err.println("Telegram connector not configured");
            return false;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python3",
                    "scripts/telethon/message_editor.py",
                    target,
                    String.valueOf(messageId),
                    newText
            );
            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);
            env.put("TELEGRAM_USERNAME", this.username);
            env.put("PLATFORM_ACCOUNT_ID", String.valueOf(this.platformAccountId));
            env.put("TELETHON_LOCK_PATH", "/app/telethon_send.lock");
            env.put("TELETHON_SESSION_PATH", buildSessionPath(this.username, this.phoneNumber));

            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python: " + line);
                }
            }
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Edit a media message by replacing the media file and/or caption
     * @param target Target username
     * @param messageId Telegram message ID to edit
     * @param filePath Path to new media file
     * @param caption New caption text (can be null/empty)
     * @return true if successful
     */
    public boolean editMediaMessage(String target, int messageId, String filePath, String caption) {
        if (!isConfigured()) {
            System.err.println("Telegram connector not configured");
            return false;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python3",
                    "scripts/telethon/message_editor.py",
                    target,
                    String.valueOf(messageId),
                    filePath,
                    caption != null && !caption.isEmpty() ? caption : ""
            );
            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);
            env.put("TELEGRAM_USERNAME", this.username);
            env.put("PLATFORM_ACCOUNT_ID", String.valueOf(this.platformAccountId));
            env.put("TELETHON_LOCK_PATH", "/app/telethon_send.lock");
            env.put("TELETHON_SESSION_PATH", buildSessionPath(this.username, this.phoneNumber));

            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python: " + line);
                }
            }
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteMessage(String target, int messageId) {
        if (!isConfigured()) {
            System.err.println("Telegram connector not configured");
            return false;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python3",
                    "scripts/telethon/message_deleter.py",
                    target,
                    String.valueOf(messageId)
            );
            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);
            env.put("TELEGRAM_USERNAME", this.username);
            env.put("PLATFORM_ACCOUNT_ID", String.valueOf(this.platformAccountId));
            env.put("TELETHON_LOCK_PATH", "/app/telethon_send.lock");
            env.put("TELETHON_SESSION_PATH", buildSessionPath(this.username, this.phoneNumber));

            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python: " + line);
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            
            // Check if output indicates success
            String fullOutput = output.toString();
            boolean hasSuccess = fullOutput.contains("Deleted message") || fullOutput.contains("deleted");
            boolean hasError = fullOutput.toLowerCase().contains("error") || (exitCode != 0 && !hasSuccess);
            
            if (hasError) {
                System.err.println("Delete message failed. Exit code: " + exitCode + ", Output: " + fullOutput);
                return false;
            }
            
            return exitCode == 0 || hasSuccess;
        } catch (Exception e) {
            System.err.println("Exception in deleteMessage: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String buildSessionPath(String username, String phone) {
        String userPart = (username != null && !username.isBlank()) ? username : (phone != null ? phone : "unknown");
        if (userPart.startsWith("@")) userPart = userPart.substring(1);
        userPart = userPart.replaceAll("[^A-Za-z0-9_@+]", "_");
        // Directory per user, with session file inside that directory.
        // Telethon will append ".session" automatically to this base path.
        return "Session/telegramConnector/user_" + userPart + "/user_" + userPart;
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

    /**
     * Check if a user is online on Telegram
     * @param target Target username
     * @return true if user is online, false otherwise
     */
    public boolean checkUserOnline(String target) {
        if (!isConfigured()) {
            System.err.println("Telegram connector not configured");
            return false;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python3",
                    "scripts/telethon/online_checker.py",
                    target
            );
            Map<String, String> env = processBuilder.environment();
            env.put("TELEGRAM_API_ID", this.apiId);
            env.put("TELEGRAM_API_HASH", this.apiHash);
            env.put("TELEGRAM_PHONE", this.phoneNumber);
            env.put("TELEGRAM_USERNAME", this.username);
            env.put("PLATFORM_ACCOUNT_ID", String.valueOf(this.platformAccountId));
            env.put("TELETHON_SESSION_PATH", buildSessionPath(this.username, this.phoneNumber));

            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python: " + line);
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            
            // Check output for "true" or "false"
            String outputStr = output.toString().trim();
            if (outputStr.contains("true")) {
                return true;
            } else if (outputStr.contains("false")) {
                return false;
            }
            
            // Default to false if unclear
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isConfigured() {
        return apiId != null && !apiId.isEmpty() &&
                apiHash != null && !apiHash.isEmpty() &&
                phoneNumber != null && !phoneNumber.isEmpty();
    }
}