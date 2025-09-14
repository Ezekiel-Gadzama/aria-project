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
    // Constructors, getters, setters
}