package com.aria.ai;

import com.aria.core.model.ConversationGoal;
import com.aria.core.model.ChatProfile;

public class ResponseGenerator {
    private final OpenAIClient openAIClient;
    private ConversationGoal currentGoal;
    private ChatProfile styleProfile;
    private String currentTargetAlias;
    private String currentPlatform;

    public ResponseGenerator(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
        this.styleProfile = new ChatProfile(); // Default profile
    }

    public void setCurrentGoal(ConversationGoal goal) {
        this.currentGoal = goal;
    }

    public void setCurrentTarget(String targetAlias, String platform) {
        this.currentTargetAlias = targetAlias;
        this.currentPlatform = platform;
    }

    // Add this method to set all conversation context at once
    public void setConversationContext(ConversationGoal goal, String targetAlias, String platform) {
        this.currentGoal = goal;
        this.currentTargetAlias = targetAlias;
        this.currentPlatform = platform;
    }

    public void setStyleProfile(ChatProfile styleProfile) {
        this.styleProfile = styleProfile;
    }

    public ChatProfile getStyleProfile() {
        return styleProfile;
    }

    public ConversationGoal getCurrentGoal() {
        return currentGoal;
    }

    public String generateResponse(String incomingMessage, String conversationHistory) {
        String context = buildContext(incomingMessage, conversationHistory);
        return openAIClient.generateResponse(context);
    }

    public String generateResponse(String prompt) {
        return openAIClient.generateResponse(prompt);
    }

    private String buildContext(String incomingMessage, String conversationHistory) {
        if (currentGoal == null || currentTargetAlias == null) {
            throw new IllegalStateException("Conversation goal and target not set. Call setConversationContext() first.");
        }

        // Include style profile in the context if available
        String styleContext = "";
        if (styleProfile != null) {
            styleContext = String.format("""
                
                Communication Style:
                - Humor: %.1f/1.0
                - Formality: %.1f/1.0
                - Empathy: %.1f/1.0""",
                    styleProfile.getHumorLevel(),
                    styleProfile.getFormalityLevel(),
                    styleProfile.getEmpathyLevel());
        }
        
        // Note: If cross-platform context is enabled, conversationHistory may contain messages
        // from multiple platforms (same person, different platforms). The AI should understand
        // this is the same person communicating across different platforms.

        return String.format("""
            Conversation Context:
            Target: %s
            Platform: %s
            How we met: %s
            Goal: %s%s
            Conversation History: %s
            Latest message from target: %s
            
            Generate a natural, engaging response that moves toward the goal:""",
                currentTargetAlias,
                currentPlatform,
                currentGoal.getMeetingContext(),
                currentGoal.getDesiredOutcome(),
                styleContext,
                conversationHistory,
                incomingMessage);
    }

    public String generateOpeningLine() {
        if (currentGoal == null || currentTargetAlias == null) {
            throw new IllegalStateException("Conversation goal and target not set. Call setConversationContext() first.");
        }

        // Include style in opening message prompt
        String stylePrompt = "";
        if (styleProfile != null) {
            stylePrompt = String.format("""
                Style Guidelines:
                - Humor level: %.1f/1.0
                - Formality: %.1f/1.0
                - Empathy: %.1f/1.0
                """,
                    styleProfile.getHumorLevel(),
                    styleProfile.getFormalityLevel(),
                    styleProfile.getEmpathyLevel());
        }

        String prompt = String.format("""
            Generate an engaging opening message for:
            Target: %s
            Platform: %s
            Context: %s
            Goal: %s
            %s
            Make it natural and personalized:""",
                currentTargetAlias,
                currentPlatform,
                currentGoal.getMeetingContext(),
                currentGoal.getDesiredOutcome(),
                stylePrompt);

        return openAIClient.generateResponse(prompt);
    }

    public String generateStyledResponse(String prompt, String styleConstraints) {
        String styledPrompt = String.format("""
            %s
            
            Style Guidelines:
            %s
            
            Generate a response that follows these style guidelines:""",
                prompt, styleConstraints);

        return openAIClient.generateResponse(styledPrompt);
    }

    // New method to generate response with specific style
    public String generateResponseWithStyle(String incomingMessage, String conversationHistory, ChatProfile style) {
        if (currentGoal == null || currentTargetAlias == null) {
            throw new IllegalStateException("Conversation goal and target not set. Call setConversationContext() first.");
        }

        String styleContext = String.format("""
            
            Communication Style to emulate:
            - Humor: %.1f/1.0 (%.1f = very serious, %.1f = very humorous)
            - Formality: %.1f/1.0 (%.1f = very casual, %.1f = very formal)
            - Empathy: %.1f/1.0 (%.1f = direct, %.1f = very empathetic)""",
                style.getHumorLevel(), 0.0, 1.0,
                style.getFormalityLevel(), 0.0, 1.0,
                style.getEmpathyLevel(), 0.0, 1.0);

        String context = String.format("""
            Conversation Context:
            Target: %s
            Platform: %s
            How we met: %s
            Goal: %s%s
            Conversation History: %s
            Latest message from target: %s
            
            Generate a response that matches the specified communication style:""",
                currentTargetAlias,
                currentPlatform,
                currentGoal.getMeetingContext(),
                currentGoal.getDesiredOutcome(),
                styleContext,
                conversationHistory,
                incomingMessage);

        return openAIClient.generateResponse(context);
    }

    // Utility method for testing without full context
    public String generateBasicResponse(String message) {
        return openAIClient.generateResponse(message);
    }

    // Getters for current target information
    public String getCurrentTargetAlias() {
        return currentTargetAlias;
    }

    public String getCurrentPlatform() {
        return currentPlatform;
    }
}