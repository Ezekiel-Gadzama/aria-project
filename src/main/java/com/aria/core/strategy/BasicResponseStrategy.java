package com.aria.core.strategy;

import com.aria.core.model.ConversationGoal;
import com.aria.ai.ResponseGenerator;

/**
 * Basic response strategy that uses the AI to generate responses
 * without personalization from historical chats (for Phase 0 MVP)
 */
public class BasicResponseStrategy {
    private final ResponseGenerator responseGenerator;
    private ConversationGoal currentGoal;
    private StringBuilder conversationHistory;

    public BasicResponseStrategy(ResponseGenerator responseGenerator) {
        this.responseGenerator = responseGenerator;
        this.conversationHistory = new StringBuilder();
    }

    public void setConversationGoal(ConversationGoal goal) {
        this.currentGoal = goal;
        this.responseGenerator.setCurrentGoal(goal);
        this.conversationHistory.setLength(0); // Clear previous history
    }

    public String generateOpeningMessage() {
        String openingMessage = responseGenerator.generateOpeningLine();
        addToHistory("You", openingMessage);
        return openingMessage;
    }

    public String generateResponse(String incomingMessage) {
        // Add the incoming message to history
        addToHistory(currentGoal.getTargetName(), incomingMessage);

        // Generate AI response
        String aiResponse = responseGenerator.generateResponse(incomingMessage, conversationHistory.toString());

        // Add AI response to history
        addToHistory("You", aiResponse);

        return aiResponse;
    }

    public void addUserMessage(String message) {
        addToHistory("You", message);
    }

    public void addTargetMessage(String message) {
        addToHistory(currentGoal.getTargetName(), message);
    }

    public String getConversationHistory() {
        return conversationHistory.toString();
    }

    public void clearHistory() {
        conversationHistory.setLength(0);
    }

    private void addToHistory(String sender, String message) {
        conversationHistory.append(sender)
                .append(": ")
                .append(message)
                .append("\n\n");
    }

    // Utility method to estimate conversation engagement level
    public double estimateEngagementLevel() {
        String history = conversationHistory.toString();
        int totalMessages = history.split("\n\n").length;
        int shortResponses = countShortResponses(history);

        // Simple engagement heuristic: ratio of substantial messages
        if (totalMessages == 0) return 1.0; // Default to high engagement at start

        double engagement = 1.0 - ((double) shortResponses / totalMessages * 0.5);
        return Math.max(0.1, Math.min(1.0, engagement)); // Clamp between 0.1 and 1.0
    }

    private int countShortResponses(String history) {
        // Count messages with <= 3 words (considered short/unengaged responses)
        String[] messages = history.split("\n\n");
        int count = 0;

        for (String message : messages) {
            if (message.contains(": ")) {
                String content = message.substring(message.indexOf(": ") + 2);
                if (content.split("\\s+").length <= 3) {
                    count++;
                }
            }
        }

        return count;
    }
}