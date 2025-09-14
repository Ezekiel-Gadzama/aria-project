package com.aria.ai;

import com.aria.core.model.ConversationGoal;

public class ResponseGenerator {
    private final OpenAIClient openAIClient;
    private ConversationGoal currentGoal;

    public ResponseGenerator(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    public void setCurrentGoal(ConversationGoal goal) {
        this.currentGoal = goal;
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
        if (currentGoal == null) {
            throw new IllegalStateException("Conversation goal not set. Call setCurrentGoal() first.");
        }

        return String.format("""
            Conversation Context:
            Target: %s
            How we met: %s
            Goal: %s
            Conversation History: %s
            Latest message from target: %s
            
            Generate a natural, engaging response that moves toward the goal:""",
                currentGoal.getTargetName(),
                currentGoal.getMeetingContext(),
                currentGoal.getDesiredOutcome(),
                conversationHistory,
                incomingMessage);
    }

    public String generateOpeningLine() {
        if (currentGoal == null) {
            throw new IllegalStateException("Conversation goal not set. Call setCurrentGoal() first.");
        }

        String prompt = String.format("""
            Generate an engaging opening message for:
            Target: %s
            Context: %s
            Goal: %s
            
            Make it natural and personalized:""",
                currentGoal.getTargetName(),
                currentGoal.getMeetingContext(),
                currentGoal.getDesiredOutcome());

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

    // Utility method for testing without full context
    public String generateBasicResponse(String message) {
        return openAIClient.generateResponse(message);
    }
}