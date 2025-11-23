package com.aria.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a target group (parent entity for communal spaces).
 * A parent Target Group can have multiple SubTarget Groups across different platforms.
 */
public class TargetGroup {
    private int id;
    private int userId;
    private String name;
    private String description;
    private List<SubTargetGroup> subTargetGroups;
    private java.time.LocalDateTime createdAt;

    public TargetGroup() {
        this.subTargetGroups = new ArrayList<>();
    }

    public TargetGroup(String name, String description) {
        this.name = name;
        this.description = description;
        this.subTargetGroups = new ArrayList<>();
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

    public List<SubTargetGroup> getSubTargetGroups() {
        return subTargetGroups;
    }

    public void setSubTargetGroups(List<SubTargetGroup> subTargetGroups) {
        this.subTargetGroups = subTargetGroups != null ? subTargetGroups : new ArrayList<>();
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

