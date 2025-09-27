package com.aria.core;

import com.aria.analysis.ChatAnalyzer;
import com.aria.core.model.ConversationGoal;
import com.aria.ai.OpenAIClient;
import com.aria.ai.ResponseGenerator;
import com.aria.core.strategy.AdvancedResponseStrategy;
import com.aria.platform.PlatformConnector;
import com.aria.platform.Platforms;
import com.aria.platform.telegram.TelegramConnector;
import com.aria.core.strategy.ResponseStrategy;
import com.aria.core.strategy.StrategyFactory;
import java.util.Map;
import java.util.List;
import com.aria.core.model.Message;
import com.aria.service.UserService;
import com.aria.storage.DatabaseManager;

public class AriaOrchestrator {
    private PlatformConnector platformConnector;
    private OpenAIClient openAIClient;
    private ResponseGenerator responseGenerator;
    private ResponseStrategy responseStrategy;
    private ConversationGoal currentGoal;
    private UserService userService; // Add this

    public AriaOrchestrator(UserService userService) {
        this.openAIClient = new OpenAIClient();
        this.responseGenerator = new ResponseGenerator(openAIClient);
        this.responseStrategy = StrategyFactory.createStrategy(
                StrategyFactory.StrategyType.BASIC,
                responseGenerator
        );
        this.userService = userService;

    }

    public void initializeConversation(ConversationGoal goal) {
        this.currentGoal = goal;
        this.platformConnector = createPlatformConnector(goal.getPlatform());
        this.responseStrategy.initialize(goal);
    }

    public void initializeAdvancedConversation(ConversationGoal goal, Map<String, List<Message>> historicalChats) {
        this.currentGoal = goal;
        this.platformConnector = createPlatformConnector(goal.getPlatform());

        // Switch to advanced strategy
        this.responseStrategy = StrategyFactory.createStrategy(
                StrategyFactory.StrategyType.ADVANCED,
                responseGenerator,
                new ChatAnalyzer() // You'll need to implement ChatAnalyzer
        );

        this.responseStrategy.initialize(goal);

        // Load historical data for advanced strategy
        if (responseStrategy instanceof AdvancedResponseStrategy) {
            ((AdvancedResponseStrategy) responseStrategy).loadHistoricalData(historicalChats);
        }
    }

    private PlatformConnector createPlatformConnector(Platforms platform) {
        switch (platform) {
            case Platforms.TELEGRAM:
                return new TelegramConnector();
            case Platforms.WHATSAPP:
                throw new IllegalArgumentException("WHATSAPP not yet integrated");
            case Platforms.INSTAGRAM:
                throw new IllegalArgumentException("INSTAGRAM not yet integrated");
            default:
                throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
    }

    public String generateResponse(String incomingMessage) {
        return responseStrategy.generateResponse(incomingMessage);
    }

    public String generateOpeningMessage() {
        return responseStrategy.generateOpeningMessage();
    }

    public boolean sendMessage(String message) {
        if (platformConnector != null && currentGoal != null) {
            boolean success = platformConnector.sendMessage(currentGoal.getTargetAlias_Number(), message);
            if (success) {
                // Message sent successfully, it will be added to history by the strategy
            }
            return success;
        }
        return false;
    }

    public void startChatIngestion() {
        if (platformConnector != null) {
            DatabaseManager.savePlatformAccount(
                    userService.getUser().getUserAppId(),
                    platformConnector.getPlatformName(),
                    "ezekiel_wa",
                    platformConnector.getApiId(),
                    platformConnector.getApiHash(),
                    null,
                    "waAccessToken",
                    "waRefreshToken"
            );
            System.out.println("Got here");
            platformConnector.ingestChatHistory();
        }

    }

    public String getConversationHistory() {
        return responseStrategy.getConversationHistory();
    }

    public void clearConversationHistory() {
        responseStrategy.clearHistory();
    }

    public double getEngagementLevel() {
        return responseStrategy.estimateEngagementLevel();
    }

    // Getters for external access
    public PlatformConnector getPlatformConnector() {
        return platformConnector;
    }

    public ConversationGoal getCurrentGoal() {
        return currentGoal;
    }

    public ResponseStrategy getResponseStrategy() {
        return responseStrategy;
    }
}