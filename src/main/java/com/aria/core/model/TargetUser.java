package com.aria.core.model;

import com.aria.platform.Platform;
import com.aria.platform.UserPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TargetUser {
    private int targetId;
    private String name;
    private String userId;
    private List<UserPlatform> platforms;
    private ConversationGoal conversationGoal;
    private int selectedPlatformIndex;

    public TargetUser() {
        this.platforms = new ArrayList<>();
        this.conversationGoal = new ConversationGoal(); // Initialize with default goal
        this.selectedPlatformIndex = 0;
    }

    public TargetUser(String name, List<UserPlatform> platforms) {
        this.name = name;
        this.platforms = platforms != null ? platforms : new ArrayList<>();
        this.conversationGoal = new ConversationGoal();
        this.selectedPlatformIndex = 0;
    }

    public TargetUser(String name, List<UserPlatform> platforms, ConversationGoal conversationGoal) {
        this.name = name;
        this.platforms = platforms != null ? platforms : new ArrayList<>();
        this.conversationGoal = conversationGoal != null ? conversationGoal : new ConversationGoal();
    }

    public int getSelectedPlatformIndex() {
        return selectedPlatformIndex;
    }

    public void setSelectedPlatformIndex(int selectedPlatformIndex) {
        if (selectedPlatformIndex >= 0 && selectedPlatformIndex < platforms.size()) {
            this.selectedPlatformIndex = selectedPlatformIndex;
        }
    }

    // Get the currently selected platform
    public UserPlatform getSelectedPlatform() {
        if (platforms != null && !platforms.isEmpty() && selectedPlatformIndex < platforms.size()) {
            return platforms.get(selectedPlatformIndex);
        }
        return null;
    }

    // Get the currently selected platform's username (target alias)
    public String getSelectedUsername() {
        UserPlatform platform = getSelectedPlatform();
        return platform != null ? platform.getUsername() : "";
    }

    // Get the currently selected platform type
    public Platform getSelectedPlatformType() {
        UserPlatform platform = getSelectedPlatform();
        return platform != null ? platform.getPlatform() : null;
    }

    // Helper to find platform index by platform type
    public Optional<Integer> findPlatformIndex(Platform platform) {
        if (platforms != null) {
            for (int i = 0; i < platforms.size(); i++) {
                if (platforms.get(i).getPlatform() == platform) {
                    return Optional.of(i);
                }
            }
        }
        return Optional.empty();
    }

    // Check if a specific platform exists for this user
    public boolean hasPlatform(Platform platform) {
        return platforms.stream()
                .anyMatch(userPlatform -> userPlatform.getPlatform() == platform);
    }

    // Getters and Setters
    public int getTargetId() {
        return targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<UserPlatform> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(List<UserPlatform> platforms) {
        this.platforms = platforms != null ? platforms : new ArrayList<>();
    }

    public ConversationGoal getConversationGoal() {
        return conversationGoal;
    }

    public void setConversationGoal(ConversationGoal conversationGoal) {
        this.conversationGoal = conversationGoal != null ? conversationGoal : new ConversationGoal();
    }

    // Helper method to get primary platform (useful for UI)
    public Platform getPrimaryPlatform() {
        if (platforms != null && !platforms.isEmpty()) {
            return platforms.get(0).getPlatform();
        }
        return null;
    }

    // Helper method to get primary username
    public String getPrimaryUsername() {
        if (platforms != null && !platforms.isEmpty()) {
            return platforms.get(0).getUsername();
        }
        return "";
    }
}