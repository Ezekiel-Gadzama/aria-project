package com.aria.core;

import com.aria.analysis.ChatAnalyzer;
import com.aria.core.model.ConversationGoal;
import com.aria.core.model.TargetUser;
import com.aria.ai.OpenAIClient;
import com.aria.ai.ResponseGenerator;
import com.aria.core.strategy.AdvancedResponseStrategy;
import com.aria.platform.PlatformConnector;
import com.aria.platform.Platform;
import com.aria.platform.telegram.TelegramConnector;
import com.aria.core.strategy.ResponseStrategy;
import com.aria.core.strategy.StrategyFactory;
import java.util.Map;
import java.util.List;
import com.aria.core.model.Message;
import com.aria.service.UserService;

public class AriaOrchestrator {
    private PlatformConnector platformConnector;
    private OpenAIClient openAIClient;
    private ResponseGenerator responseGenerator;
    private ResponseStrategy responseStrategy;
    private ConversationGoal currentGoal;
    private TargetUser currentTargetUser; // Store the target user
    private UserService userService;

    public AriaOrchestrator(UserService userService) {
        this.openAIClient = new OpenAIClient();
        this.responseGenerator = new ResponseGenerator(openAIClient);
        this.responseStrategy = StrategyFactory.createStrategy(
                StrategyFactory.StrategyType.BASIC,
                responseGenerator
        );
        this.userService = userService;
    }

    public void initializeConversation(ConversationGoal goal, TargetUser targetUser) {
        this.currentGoal = goal;
        this.currentTargetUser = targetUser;

        Platform platform = targetUser.getSelectedPlatformType();
        this.platformConnector = createPlatformConnector(platform);
        this.responseStrategy.initialize(goal, targetUser);
    }

    public void initializeAdvancedConversation(ConversationGoal goal, TargetUser targetUser,
                                               Map<String, List<Message>> historicalChats) {
        this.currentGoal = goal;
        this.currentTargetUser = targetUser;

        Platform platform = targetUser.getSelectedPlatformType();
        this.platformConnector = createPlatformConnector(platform);

        this.responseStrategy = StrategyFactory.createStrategy(
                StrategyFactory.StrategyType.ADVANCED,
                responseGenerator,
                new ChatAnalyzer()
        );

        this.responseStrategy.initialize(goal, targetUser);

        if (responseStrategy instanceof AdvancedResponseStrategy) {
            ((AdvancedResponseStrategy) responseStrategy).loadHistoricalData(historicalChats);
        }
    }

    private PlatformConnector createPlatformConnector(Platform platform) {
        switch (platform) {
            case TELEGRAM:
                return new TelegramConnector();
            case WHATSAPP:
                throw new IllegalArgumentException("WHATSAPP not yet integrated");
            case INSTAGRAM:
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
        if (platformConnector != null && currentTargetUser != null) {
            boolean success = platformConnector.sendMessage(
                    currentTargetUser.getSelectedUsername(),
                    message
            );
            if (success) {
                // Message sent successfully
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
        return responseStrategy.getConversationHistory();
    }

    public void clearConversationHistory() {
        responseStrategy.clearHistory();
    }

    public double getEngagementLevel() {
        return responseStrategy.estimateEngagementLevel();
    }

    // Getters
    public PlatformConnector getPlatformConnector() {
        return platformConnector;
    }

    public ConversationGoal getCurrentGoal() {
        return currentGoal;
    }

    public TargetUser getCurrentTargetUser() {
        return currentTargetUser;
    }

    public ResponseStrategy getResponseStrategy() {
        return responseStrategy;
    }
}