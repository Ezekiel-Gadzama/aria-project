package com.aria.core.model;

import com.aria.platform.Platform;

/**
 * Represents a platform-specific instance within a Target Business.
 * Can be a CHANNEL, GROUP, or PRIVATE_CHAT.
 */
public class BusinessSubTarget {
    public enum SubTargetType {
        CHANNEL,
        GROUP,
        PRIVATE_CHAT
    }

    private int id;
    private int businessId; // Parent Target Business ID
    private String name;
    private SubTargetType type; // CHANNEL, GROUP, or PRIVATE_CHAT
    private Platform platform;
    private Integer platformAccountId; // Reference to platform_accounts
    private Integer dialogId; // Reference to dialogs table
    private Long platformId; // Platform-specific ID (e.g., Telegram chat ID)
    private String username;
    private String description;
    private java.time.LocalDateTime createdAt;

    public BusinessSubTarget() {
    }

    public BusinessSubTarget(int businessId, String name, SubTargetType type, Platform platform) {
        this.businessId = businessId;
        this.name = name;
        this.type = type;
        this.platform = platform;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SubTargetType getType() {
        return type;
    }

    public void setType(SubTargetType type) {
        this.type = type;
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

    public Integer getDialogId() {
        return dialogId;
    }

    public void setDialogId(Integer dialogId) {
        this.dialogId = dialogId;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

