// Message.java
// Message.java
package com.aria.core.model;

import java.time.LocalDateTime;

public class Message {
    private String id;
    private String sender;
    private String content;
    private LocalDateTime timestamp;
    private boolean isFromUser;
    private MessageType type;

    public enum MessageType {
        TEXT, IMAGE, LINK, EMOJI
    }

    // Constructor
    public Message() {}

    public Message(String content, String sender, boolean isFromUser) {
        this.content = content;
        this.sender = sender;
        this.isFromUser = isFromUser;
        this.timestamp = LocalDateTime.now();
        this.type = MessageType.TEXT;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isFromUser() {
        return isFromUser;
    }

    public void setFromUser(boolean fromUser) {
        isFromUser = fromUser;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }
}