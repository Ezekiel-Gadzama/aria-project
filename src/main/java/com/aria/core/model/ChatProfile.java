// ChatProfile.java
package com.aria.core.model;

public class ChatProfile {
    private double successScore;
    private double humorLevel;
    private double formalityLevel;
    private double empathyLevel;
    private String preferredOpening;
    private double responseTimeAverage;
    private double messageLengthAverage;
    private double engagementLevel;
    private double questionRate;

    public ChatProfile() {
        // Default values
        this.humorLevel = 0.5;
        this.formalityLevel = 0.5;
        this.empathyLevel = 0.5;
        this.preferredOpening = "Hi there!";
        this.responseTimeAverage = 60.0;
        this.messageLengthAverage = 20.0;
        this.engagementLevel = 0.5;
        this.questionRate = 0.3;
    }

    // Getters and setters for all fields
    public double getHumorLevel() { return humorLevel; }
    public void setHumorLevel(double humorLevel) {
        this.humorLevel = Math.max(0.0, Math.min(1.0, humorLevel));
    }

    public double getFormalityLevel() { return formalityLevel; }
    public void setFormalityLevel(double formalityLevel) {
        this.formalityLevel = Math.max(0.0, Math.min(1.0, formalityLevel));
    }

    public double getEmpathyLevel() { return empathyLevel; }
    public void setEmpathyLevel(double empathyLevel) {
        this.empathyLevel = Math.max(0.0, Math.min(1.0, empathyLevel));
    }

    public String getPreferredOpening() { return preferredOpening; }
    public void setPreferredOpening(String preferredOpening) {
        this.preferredOpening = preferredOpening;
    }

    public double getResponseTimeAverage() { return responseTimeAverage; }
    public void setResponseTimeAverage(double responseTimeAverage) {
        this.responseTimeAverage = Math.max(0.0, responseTimeAverage);
    }

    public double getMessageLengthAverage() { return messageLengthAverage; }
    public void setMessageLengthAverage(double messageLengthAverage) {
        this.messageLengthAverage = Math.max(0.0, messageLengthAverage);
    }

    public double getSuccessScore() { return successScore; }
    public void setSuccessScore(double successScore) {
        this.successScore = Math.max(0.0, Math.min(1.0, successScore));
    }

    public double getEngagementLevel() { return engagementLevel; }
    public void setEngagementLevel(double engagementLevel) {
        this.engagementLevel = Math.max(0.0, Math.min(1.0, engagementLevel));
    }

    public double getQuestionRate() { return questionRate; }
    public void setQuestionRate(double questionRate) {
        this.questionRate = Math.max(0.0, Math.min(1.0, questionRate));
    }
}