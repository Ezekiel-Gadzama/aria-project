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
}

