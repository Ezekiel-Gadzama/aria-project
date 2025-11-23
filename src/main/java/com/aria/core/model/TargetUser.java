package com.aria.core.model;

import com.aria.platform.Platform;
import com.aria.platform.UserPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a Target User (parent entity - platform-agnostic person information).
 * This holds global information about the person, while SubTargetUser holds platform-specific instances.
 */
public class TargetUser {
    private int targetId;
    private int userId; // Owner of this target (changed from String to int)
    private String name;
    private String bio;
    private String desiredOutcome;
    private String meetingContext; // Where/How You Met
    private String importantDetails; // Optional important details
    private boolean crossPlatformContextEnabled; // Toggle for cross-platform context aggregation
    private String profileJson; // JSON profile data
    private String profilePictureUrl;
    private List<SubTargetUser> subTargetUsers; // Child entities
    private ConversationGoal conversationGoal;
    
    // Legacy fields for backward compatibility during migration
    private List<UserPlatform> platforms;
    private int selectedPlatformIndex;

    public TargetUser() {
        this.subTargetUsers = new ArrayList<>();
        this.platforms = new ArrayList<>();
        this.conversationGoal = new ConversationGoal(); // Initialize with default goal
        this.selectedPlatformIndex = 0;
        this.crossPlatformContextEnabled = false;
    }

    public TargetUser(String name, List<UserPlatform> platforms) {
        this.name = name;
        this.platforms = platforms != null ? platforms : new ArrayList<>();
        this.subTargetUsers = new ArrayList<>();
        this.conversationGoal = new ConversationGoal();
        this.selectedPlatformIndex = 0;
        this.crossPlatformContextEnabled = false;
    }

    public TargetUser(String name, List<UserPlatform> platforms, ConversationGoal conversationGoal) {
        this.name = name;
        this.platforms = platforms != null ? platforms : new ArrayList<>();
        this.subTargetUsers = new ArrayList<>();
        this.conversationGoal = conversationGoal != null ? conversationGoal : new ConversationGoal();
        this.crossPlatformContextEnabled = false;
    }

    public int getSelectedPlatformIndex() {
        return selectedPlatformIndex;
    }

    public void setSelectedPlatformIndex(int selectedPlatformIndex) {
        if (selectedPlatformIndex >= 0 && selectedPlatformIndex < platforms.size()) {
            this.selectedPlatformIndex = selectedPlatformIndex;
        }
    }

    // Get the currently selected platform (legacy method)
    public UserPlatform getSelectedPlatform() {
        if (platforms != null && !platforms.isEmpty() && selectedPlatformIndex < platforms.size()) {
            return platforms.get(selectedPlatformIndex);
        }
        return null;
    }

    // Get the currently selected platform's username (target alias) - legacy
    public String getSelectedUsername() {
        UserPlatform platform = getSelectedPlatform();
        return platform != null ? platform.getUsername() : "";
    }

    // Get the currently selected platform type - legacy
    public Platform getSelectedPlatformType() {
        UserPlatform platform = getSelectedPlatform();
        return platform != null ? platform.getPlatform() : null;
    }

    // Helper to find platform index by platform type - legacy
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

    // Check if a specific platform exists for this user - legacy
    public boolean hasPlatform(Platform platform) {
        return platforms.stream()
                .anyMatch(userPlatform -> userPlatform.getPlatform() == platform);
    }
    
    // New methods for SubTarget Users
    public List<SubTargetUser> getSubTargetUsers() {
        return subTargetUsers;
    }

    public void setSubTargetUsers(List<SubTargetUser> subTargetUsers) {
        this.subTargetUsers = subTargetUsers != null ? subTargetUsers : new ArrayList<>();
    }
    
    public void addSubTargetUser(SubTargetUser subTargetUser) {
        if (this.subTargetUsers == null) {
            this.subTargetUsers = new ArrayList<>();
        }
        this.subTargetUsers.add(subTargetUser);
    }
    
    public Optional<SubTargetUser> findSubTargetByPlatform(Platform platform, Integer platformAccountId) {
        if (subTargetUsers != null) {
            return subTargetUsers.stream()
                    .filter(st -> st.getPlatform() == platform && 
                            (platformAccountId == null || platformAccountId.equals(st.getPlatformAccountId())))
                    .findFirst();
        }
        return Optional.empty();
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

    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    // Legacy method for backward compatibility
    public String getUserIdAsString() {
        return String.valueOf(userId);
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

    // Helper method to get primary username - legacy
    public String getPrimaryUsername() {
        if (platforms != null && !platforms.isEmpty()) {
            return platforms.get(0).getUsername();
        }
        // Try to get from first SubTarget User
        if (subTargetUsers != null && !subTargetUsers.isEmpty()) {
            return subTargetUsers.get(0).getUsername();
        }
        return "";
    }
    
    // New getters and setters for parent entity fields
    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getDesiredOutcome() {
        return desiredOutcome;
    }

    public void setDesiredOutcome(String desiredOutcome) {
        this.desiredOutcome = desiredOutcome;
    }

    public String getMeetingContext() {
        return meetingContext;
    }

    public void setMeetingContext(String meetingContext) {
        this.meetingContext = meetingContext;
    }

    public String getImportantDetails() {
        return importantDetails;
    }

    public void setImportantDetails(String importantDetails) {
        this.importantDetails = importantDetails;
    }

    public boolean isCrossPlatformContextEnabled() {
        return crossPlatformContextEnabled;
    }

    public void setCrossPlatformContextEnabled(boolean crossPlatformContextEnabled) {
        this.crossPlatformContextEnabled = crossPlatformContextEnabled;
    }

    public String getProfileJson() {
        return profileJson;
    }

    public void setProfileJson(String profileJson) {
        this.profileJson = profileJson;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }
}