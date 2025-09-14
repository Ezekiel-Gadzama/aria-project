// TelethonBridge.java
package com.aria.platform.telegram;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TelethonBridge {

    public void ingestChatHistory() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    "scripts/telethon/chat_ingestor.py"
            );

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("Chat ingestion successful");
                parseChatExport();
            } else {
                BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()));
                String line;
                while ((line = errorReader.readLine()) != null) {
                    System.err.println("Python Error: " + line);
                }
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void parseChatExport() {
        Path exportPath = Paths.get("chats_export.json");
        try {
            String content = Files.readString(exportPath);
            // Parse JSON and store in database
            System.out.println("Parsed chat export: " + content.length() + " characters");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}