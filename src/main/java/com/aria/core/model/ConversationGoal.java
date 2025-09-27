package com.aria.core.model;

public class ConversationGoal {
    private String context;
    private String desiredOutcome;
    private String meetingContext;

    // Constructors
    public ConversationGoal() {
        // Default constructor
    }

    public ConversationGoal(String context, String desiredOutcome, String meetingContext) {
        this.context = context;
        this.desiredOutcome = desiredOutcome;
        this.meetingContext = meetingContext;
    }

    // Getters and Setters
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
}