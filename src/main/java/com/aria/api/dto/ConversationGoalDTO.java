package com.aria.api.dto;

/**
 * Data Transfer Object for conversation goals
 */
public class ConversationGoalDTO {
    private String context;
    private String desiredOutcome;
    private String meetingContext;
    // Optional: list of platform_account IDs whose historical chats should be included
    private java.util.List<Integer> includedPlatformAccountIds;

    // Constructors
    public ConversationGoalDTO() {}

    public ConversationGoalDTO(String context, String desiredOutcome, String meetingContext) {
        this.context = context;
        this.desiredOutcome = desiredOutcome;
        this.meetingContext = meetingContext;
    }

    // Getters and setters
    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
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

    public java.util.List<Integer> getIncludedPlatformAccountIds() {
        return includedPlatformAccountIds;
    }

    public void setIncludedPlatformAccountIds(java.util.List<Integer> includedPlatformAccountIds) {
        this.includedPlatformAccountIds = includedPlatformAccountIds;
    }
}

