package com.aria.core.model;

import com.aria.platform.Platform;

/**
 * Represents a platform-specific instance of a Target User.
 * This is the child entity that holds platform-specific information.
 */
public class SubTargetUser {
    private int id;
    private int targetUserId; // Parent Target User ID
    private String name; // Platform-specific name/nickname
    private String username;
    private Platform platform;
    private Integer platformAccountId; // Reference to platform_accounts
    private Long platformId; // Platform-specific ID (e.g., Telegram user ID)
    private String number; // Phone number if applicable
    private String advancedCommunicationSettings; // JSON string for advanced settings
    private java.time.LocalDateTime createdAt;

    public SubTargetUser() {
    }

    public SubTargetUser(int targetUserId, String name, String username, Platform platform) {
        this.targetUserId = targetUserId;
        this.name = name;
        this.username = username;
        this.platform = platform;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(int targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getAdvancedCommunicationSettings() {
        return advancedCommunicationSettings;
    }

    public void setAdvancedCommunicationSettings(String advancedCommunicationSettings) {
        this.advancedCommunicationSettings = advancedCommunicationSettings;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

