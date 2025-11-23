package com.aria.api.dto;

import com.aria.core.model.TargetUser;
import com.aria.platform.Platform;

/**
 * Data Transfer Object for target users
 */
public class TargetUserDTO {
    private Integer id;
    private String name;
    private String username;
    private Platform platform;
    private String bio;
    private String desiredOutcome;
    private String meetingContext;
    private String importantDetails; // Optional important details
    private Boolean crossPlatformContextEnabled; // Toggle for cross-platform context
    private String contextDetails;
    private Integer platformAccountId;
    private String number; // Phone number if applicable
    private String platformAccountUsername; // Username of the platform account (e.g., @Gadzama23)
    private String platformAccountName; // Display name of the platform account (e.g., Ezekiel)
    private String profilePictureUrl; // Profile picture URL/path
    
    // ChatProfile fields for communication preferences
    private Double humorLevel;
    private Double formalityLevel;
    private Double empathyLevel;
    private Double responseTimeAverage;
    private Double messageLengthAverage;
    private Double questionRate;
    private Double engagementLevel;
    private String preferredOpening;
    
    // SubTarget Users (child entities)
    private java.util.List<SubTargetUserDTO> subTargetUsers;

    // Constructors
    public TargetUserDTO() {
        this.subTargetUsers = new java.util.ArrayList<>();
    }

    public TargetUserDTO(TargetUser targetUser) {
        this.id = targetUser.getTargetId();
        this.name = targetUser.getName();
        this.username = targetUser.getSelectedUsername();
        this.platform = targetUser.getSelectedPlatformType();
        this.bio = targetUser.getBio();
        this.desiredOutcome = targetUser.getDesiredOutcome();
        this.meetingContext = targetUser.getMeetingContext();
        this.importantDetails = targetUser.getImportantDetails();
        this.crossPlatformContextEnabled = targetUser.isCrossPlatformContextEnabled();
        this.platformAccountId = targetUser.getSelectedPlatform() != null
                ? targetUser.getSelectedPlatform().getPlatformId()
                : null;
        
        // Convert SubTarget Users to DTOs
        this.subTargetUsers = new java.util.ArrayList<>();
        if (targetUser.getSubTargetUsers() != null) {
            for (com.aria.core.model.SubTargetUser subTarget : targetUser.getSubTargetUsers()) {
                this.subTargetUsers.add(new SubTargetUserDTO(subTarget));
            }
        }
    }

    // Getters and setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getContextDetails() {
        return contextDetails;
    }

    public void setContextDetails(String contextDetails) {
        this.contextDetails = contextDetails;
    }

    public Integer getPlatformAccountId() {
        return platformAccountId;
    }

    public void setPlatformAccountId(Integer platformAccountId) {
        this.platformAccountId = platformAccountId;
    }

    public String getPlatformAccountUsername() {
        return platformAccountUsername;
    }

    public void setPlatformAccountUsername(String platformAccountUsername) {
        this.platformAccountUsername = platformAccountUsername;
    }

    public String getPlatformAccountName() {
        return platformAccountName;
    }

    public void setPlatformAccountName(String platformAccountName) {
        this.platformAccountName = platformAccountName;
    }

    // ChatProfile getters and setters
    public Double getHumorLevel() {
        return humorLevel;
    }

    public void setHumorLevel(Double humorLevel) {
        this.humorLevel = humorLevel;
    }

    public Double getFormalityLevel() {
        return formalityLevel;
    }

    public void setFormalityLevel(Double formalityLevel) {
        this.formalityLevel = formalityLevel;
    }

    public Double getEmpathyLevel() {
        return empathyLevel;
    }

    public void setEmpathyLevel(Double empathyLevel) {
        this.empathyLevel = empathyLevel;
    }

    public Double getResponseTimeAverage() {
        return responseTimeAverage;
    }

    public void setResponseTimeAverage(Double responseTimeAverage) {
        this.responseTimeAverage = responseTimeAverage;
    }

    public Double getMessageLengthAverage() {
        return messageLengthAverage;
    }

    public void setMessageLengthAverage(Double messageLengthAverage) {
        this.messageLengthAverage = messageLengthAverage;
    }

    public Double getQuestionRate() {
        return questionRate;
    }

    public void setQuestionRate(Double questionRate) {
        this.questionRate = questionRate;
    }

    public Double getEngagementLevel() {
        return engagementLevel;
    }

    public void setEngagementLevel(Double engagementLevel) {
        this.engagementLevel = engagementLevel;
    }

    public String getPreferredOpening() {
        return preferredOpening;
    }

    public void setPreferredOpening(String preferredOpening) {
        this.preferredOpening = preferredOpening;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public String getImportantDetails() {
        return importantDetails;
    }

    public void setImportantDetails(String importantDetails) {
        this.importantDetails = importantDetails;
    }

    public Boolean getCrossPlatformContextEnabled() {
        return crossPlatformContextEnabled;
    }

    public void setCrossPlatformContextEnabled(Boolean crossPlatformContextEnabled) {
        this.crossPlatformContextEnabled = crossPlatformContextEnabled;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public java.util.List<SubTargetUserDTO> getSubTargetUsers() {
        return subTargetUsers;
    }

    public void setSubTargetUsers(java.util.List<SubTargetUserDTO> subTargetUsers) {
        this.subTargetUsers = subTargetUsers != null ? subTargetUsers : new java.util.ArrayList<>();
    }
}

