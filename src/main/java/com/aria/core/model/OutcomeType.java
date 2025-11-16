package com.aria.core.model;

/**
 * Enum representing the outcome type of a conversation for a specific category.
 * Used to understand why a conversation succeeded or failed.
 */
public enum OutcomeType {
    /**
     * Goal was successfully achieved
     * Example: Date happened, investment secured, meeting scheduled
     */
    SUCCESS("success", "Goal was successfully achieved"),

    /**
     * Partial success or progress made
     * Example: "Maybe later", "I'll think about it", "Let's discuss next week"
     */
    PARTIAL_SUCCESS("partial_success", "Partial success or progress made"),

    /**
     * Rejection due to circumstances, not approach
     * High success score expected because communication was effective
     * Example: "I have a boyfriend, but you seem really nice!"
     * Example: "Not enough funds now, but let's revisit in Q2"
     */
    CIRCUMSTANTIAL_REJECTION("circumstantial_rejection", 
        "Rejection due to circumstances, not approach - communication was effective"),

    /**
     * Rejection due to poor approach or genuine disinterest
     * Low success score expected because approach itself was the problem
     * Example: "You're weird", "Leave me alone", "Not interested"
     */
    APPROACH_REJECTION("approach_rejection", 
        "Rejection due to poor approach - the communication method itself was ineffective"),

    /**
     * Neutral outcome - unclear or no clear outcome
     * Example: General conversation, informational exchange
     */
    NEUTRAL("neutral", "Neutral outcome - no clear success or failure");

    private final String name;
    private final String description;

    OutcomeType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse outcome type from string (case-insensitive)
     * Returns null if not found
     */
    public static OutcomeType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        
        String normalized = name.toLowerCase().trim().replace(" ", "_");
        
        for (OutcomeType type : values()) {
            if (type.name.equalsIgnoreCase(normalized) || 
                type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        
        return null;
    }

    /**
     * Format all outcome types for OpenAI prompt
     */
    public static String formatForOpenAI() {
        StringBuilder sb = new StringBuilder();
        for (OutcomeType type : values()) {
            sb.append("- ").append(type.name)
              .append(": ").append(type.description).append("\n");
        }
        return sb.toString();
    }
}

