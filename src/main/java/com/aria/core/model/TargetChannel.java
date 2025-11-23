package com.aria.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a target channel (parent entity for channels).
 * A parent Target Channel can have multiple SubTarget Channels across different platforms.
 */
public class TargetChannel {
    private int id;
    private int userId;
    private String name;
    private String description;
    private List<SubTargetChannel> subTargetChannels;
    private java.time.LocalDateTime createdAt;

    public TargetChannel() {
        this.subTargetChannels = new ArrayList<>();
    }

    public TargetChannel(String name, String description) {
        this.name = name;
        this.description = description;
        this.subTargetChannels = new ArrayList<>();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<SubTargetChannel> getSubTargetChannels() {
        return subTargetChannels;
    }

    public void setSubTargetChannels(List<SubTargetChannel> subTargetChannels) {
        this.subTargetChannels = subTargetChannels != null ? subTargetChannels : new ArrayList<>();
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

