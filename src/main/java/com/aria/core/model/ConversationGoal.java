// ConversationGoal.java
package com.aria.core.model;

import com.aria.platform.Platforms;

public class ConversationGoal {
    private String targetAlias_Number;
    private Platforms platform;
    private String context;
    private String desiredOutcome;
    private String meetingContext;

    // Constructors, getters, setters
    public String getTargetAlias_Number() {
        return targetAlias_Number;
    }

    public void setTargetAlias_Number(String targetAlias_Number) {
        this.targetAlias_Number = targetAlias_Number;
    }

    public Platforms getPlatform() {
        return platform;
    }

    public void setPlatform(Platforms platform) {
        this.platform = platform;
    }

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