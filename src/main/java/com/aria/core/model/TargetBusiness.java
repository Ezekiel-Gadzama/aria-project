package com.aria.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Target Business (parent entity for company/organization contexts).
 * This holds global information about a business, while BusinessSubTarget holds 
 * platform-specific instances (channels, groups, private chats).
 */
public class TargetBusiness {
    private int id;
    private int userId; // Owner of this business
    private String name;
    private String description;
    private List<BusinessSubTarget> subTargets; // Child entities (channels, groups, private chats)
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    public TargetBusiness() {
        this.subTargets = new ArrayList<>();
    }

    public TargetBusiness(String name, String description) {
        this.name = name;
        this.description = description;
        this.subTargets = new ArrayList<>();
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

    public List<BusinessSubTarget> getSubTargets() {
        return subTargets;
    }

    public void setSubTargets(List<BusinessSubTarget> subTargets) {
        this.subTargets = subTargets != null ? subTargets : new ArrayList<>();
    }

    public void addSubTarget(BusinessSubTarget subTarget) {
        if (this.subTargets == null) {
            this.subTargets = new ArrayList<>();
        }
        this.subTargets.add(subTarget);
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

