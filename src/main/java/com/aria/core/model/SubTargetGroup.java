package com.aria.core.model;

import com.aria.platform.Platform;

/**
 * Represents a platform-specific instance of a Target Group.
 * This is the child entity that holds platform-specific group information.
 */
public class SubTargetGroup {
    private int id;
    private int targetGroupId; // Parent Target Group ID
    private String name; // Platform-specific name
    private Platform platform;
    private Integer platformAccountId; // Reference to platform_accounts
    private Long platformGroupId; // Platform-specific group ID
    private String username; // Group username if applicable
    private java.time.LocalDateTime createdAt;

    public SubTargetGroup() {
    }

    public SubTargetGroup(int targetGroupId, String name, Platform platform, Long platformGroupId) {
        this.targetGroupId = targetGroupId;
        this.name = name;
        this.platform = platform;
        this.platformGroupId = platformGroupId;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTargetGroupId() {
        return targetGroupId;
    }

    public void setTargetGroupId(int targetGroupId) {
        this.targetGroupId = targetGroupId;
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

    public Long getPlatformGroupId() {
        return platformGroupId;
    }

    public void setPlatformGroupId(Long platformGroupId) {
        this.platformGroupId = platformGroupId;
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

