package com.aria.core;

import com.aria.core.model.ConversationGoal;
import com.aria.ai.OpenAIClient;
import com.aria.ai.ResponseGenerator;
import com.aria.platform.PlatformConnector;
import com.aria.platform.telegram.TelegramConnector;
import com.aria.core.strategy.BasicResponseStrategy;

public class AriaOrchestrator {
    private PlatformConnector platformConnector;
    private OpenAIClient openAIClient;
    private ResponseGenerator responseGenerator;
    private BasicResponseStrategy responseStrategy;
    private ConversationGoal currentGoal;
    private StringBuilder conversationHistory;

    public AriaOrchestrator() {
        this.openAIClient = new OpenAIClient();
        this.responseGenerator = new ResponseGenerator(openAIClient);
        this.responseStrategy = new BasicResponseStrategy(responseGenerator);
        this.conversationHistory = new StringBuilder();
    }

    public void initializeConversation(ConversationGoal goal) {
        this.currentGoal = goal;
        this.platformConnector = createPlatformConnector(goal.getPlatform());
        this.responseStrategy.setConversationGoal(goal);
        this.conversationHistory.setLength(0); // Clear previous history
    }

    private PlatformConnector createPlatformConnector(String platform) {
        switch (platform.toLowerCase()) {
            case "telegram":
                return new TelegramConnector();
            default:
                throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
    }

    public String generateResponse(String incomingMessage) {
        // Add incoming message to history
        addToHistory(currentGoal.getTargetName(), incomingMessage);

        // Generate response using the strategy
        String response = responseStrategy.generateResponse(incomingMessage);

        // Add AI response to history
        addToHistory("You", response);

        return response;
    }

    public String generateOpeningMessage() {
        String openingMessage = responseStrategy.generateOpeningMessage();
        addToHistory("You", openingMessage);
        return openingMessage;
    }

    public boolean sendMessage(String message) {
        if (platformConnector != null) {
            boolean success = platformConnector.sendMessage(currentGoal.getTargetName(), message);
            if (success) {
                addToHistory("You", message);
            }
            return success;
        }
        return false;
    }

    public void startChatIngestion() {
        if (platformConnector != null) {
            platformConnector.ingestChatHistory();
        }
    }

    public String getConversationHistory() {
        return conversationHistory.toString();
    }

    public void clearConversationHistory() {
        conversationHistory.setLength(0);
    }

    private void addToHistory(String sender, String message) {
        conversationHistory.append(sender)
                .append(": ")
                .append(message)
                .append("\n\n");
    }

    // Getters for external access
    public PlatformConnector getPlatformConnector() {
        return platformConnector;
    }

    public ConversationGoal getCurrentGoal() {
        return currentGoal;
    }

    public BasicResponseStrategy getResponseStrategy() {
        return responseStrategy;
    }
}