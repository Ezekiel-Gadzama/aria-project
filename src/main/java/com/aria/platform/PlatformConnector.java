// PlatformConnector.java
package com.aria.platform;

import java.util.Map;
import java.util.List;
import com.aria.core.model.Message;

public interface PlatformConnector {
    void ingestChatHistory();
    boolean sendMessage(String target, String message);
    boolean testConnection();
    String getPlatformName();
    Map<String, List<Message>> getHistoricalChats();
    void connect();
    void disconnect();
}