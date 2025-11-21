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
    private String contextDetails;
    private Integer platformAccountId;
    private String platformAccountUsername; // Username of the platform account (e.g., @Gadzama23)
    private String platformAccountName; // Display name of the platform account (e.g., Ezekiel)
    
    // ChatProfile fields for communication preferences
    private Double humorLevel;
    private Double formalityLevel;
    private Double empathyLevel;
    private Double responseTimeAverage;
    private Double messageLengthAverage;
    private Double questionRate;
    private Double engagementLevel;
    private String preferredOpening;

    // Constructors
    public TargetUserDTO() {}

    public TargetUserDTO(TargetUser targetUser) {
        this.id = targetUser.getTargetId();
        this.name = targetUser.getName();
        this.username = targetUser.getSelectedUsername();
        this.platform = targetUser.getSelectedPlatformType();
        // TargetUser doesn't have bio field yet - will be null for now
        this.bio = null;
        this.platformAccountId = targetUser.getSelectedPlatform() != null
                ? targetUser.getSelectedPlatform().getPlatformId()
                : null;
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
}

