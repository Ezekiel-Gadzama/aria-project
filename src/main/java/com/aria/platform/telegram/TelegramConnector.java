// TelegramConnector.java
package com.aria.platform.telegram;

import com.aria.platform.PlatformConnector;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TelegramConnector implements PlatformConnector {
    private final TelethonBridge telethonBridge;

    public TelegramConnector() {
        this.telethonBridge = new TelethonBridge();
    }

    @Override
    public void ingestChatHistory() {
        telethonBridge.ingestChatHistory();
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

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            return exitCode == 0;

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String getPlatformName() {
        return "telegram";
    }

    @Override
    public boolean testConnection() {
        try {
            Process process = Runtime.getRuntime().exec("python --version");
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}