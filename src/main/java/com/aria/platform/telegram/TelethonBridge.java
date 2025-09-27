// TelethonBridge.java
package com.aria.platform.telegram;

import com.aria.core.model.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class TelethonBridge {

    public Map<String, List<Message>> parseChatExport() {
        Map<String, List<Message>> chatData = new HashMap<>();
        Path exportPath = Paths.get("chats_export.json");

        try {
            String content = Files.readString(exportPath);
            JSONArray chatsArray = new JSONArray(content);

            for (int i = 0; i < chatsArray.length(); i++) {
                JSONObject chatObj = chatsArray.getJSONObject(i);
                String contact = chatObj.getString("contact");
                JSONArray messagesArray = chatObj.getJSONArray("messages");

                List<Message> messages = new ArrayList<>();
                for (int j = 0; j < messagesArray.length(); j++) {
                    JSONObject msgObj = messagesArray.getJSONObject(j);
                    Message message = new Message();
                    message.setContent(msgObj.getString("text"));
                    message.setSender(msgObj.getString("sender"));
                    message.setFromUser("me".equals(msgObj.getString("sender")));
                    messages.add(message);
                }

                chatData.put(contact, messages);
            }

            System.out.println("Parsed " + chatData.size() + " chats with total " +
                    chatData.values().stream().mapToInt(List::size).sum() + " messages");

        } catch (IOException e) {
            System.out.println("No chat export found or error reading file: " + e.getMessage());
        }

        return chatData;
    }
}