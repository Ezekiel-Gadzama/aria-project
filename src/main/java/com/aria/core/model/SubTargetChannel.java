package com.aria.core.model;

import com.aria.platform.Platform;

/**
 * Represents a platform-specific instance of a Target Channel.
 * This is the child entity that holds platform-specific channel information.
 */
public class SubTargetChannel {
    private int id;
    private int targetChannelId; // Parent Target Channel ID
    private String name; // Platform-specific name
    private Platform platform;
    private Integer platformAccountId; // Reference to platform_accounts
    private Long platformChannelId; // Platform-specific channel ID
    private String username; // Channel username if applicable
    private java.time.LocalDateTime createdAt;

    public SubTargetChannel() {
    }

    public SubTargetChannel(int targetChannelId, String name, Platform platform, Long platformChannelId) {
        this.targetChannelId = targetChannelId;
        this.name = name;
        this.platform = platform;
        this.platformChannelId = platformChannelId;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTargetChannelId() {
        return targetChannelId;
    }

    public void setTargetChannelId(int targetChannelId) {
        this.targetChannelId = targetChannelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public Integer getPlatformAccountId() {
        return platformAccountId;
    }

    public void setPlatformAccountId(Integer platformAccountId) {
        this.platformAccountId = platformAccountId;
    }

    public Long getPlatformChannelId() {
        return platformChannelId;
    }

    public void setPlatformChannelId(Long platformChannelId) {
        this.platformChannelId = platformChannelId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

