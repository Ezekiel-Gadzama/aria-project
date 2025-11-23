package com.aria.core;

import com.aria.analysis.ChatAnalyzer;
import com.aria.analysis.ChatCategorizationService;
import com.aria.core.model.ConversationGoal;
import com.aria.core.model.TargetUser;
import com.aria.ai.OpenAIClient;
import com.aria.ai.ResponseGenerator;
import com.aria.core.strategy.AdvancedResponseStrategy;
import com.aria.platform.PlatformConnector;
import com.aria.platform.Platform;
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
    private ChatCategorizationService categorizationService;

    public AriaOrchestrator(UserService userService) {
        this.openAIClient = new OpenAIClient();
        this.responseGenerator = new ResponseGenerator(openAIClient);
        this.categorizationService = new ChatCategorizationService(openAIClient);
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
        // Only create platform connector if platform is not null
        // When using SubTarget Users, platform may be null and that's OK
        // The platform connector is not needed for basic conversation initialization
        if (platform != null) {
            this.platformConnector = createPlatformConnector(platform);
        }
        this.responseStrategy.initialize(goal, targetUser);
    }

    public void initializeAdvancedConversation(ConversationGoal goal, TargetUser targetUser,
                                               Map<String, List<Message>> historicalChats) {
        this.currentGoal = goal;
        this.currentTargetUser = targetUser;

        Platform platform = targetUser.getSelectedPlatformType();
        // Only create platform connector if platform is not null
        // When using SubTarget Users, platform may be null and that's OK
        if (platform != null) {
            this.platformConnector = createPlatformConnector(platform);
        }

        this.responseStrategy = StrategyFactory.createStrategy(
                StrategyFactory.StrategyType.ADVANCED,
                responseGenerator,
                new ChatAnalyzer(openAIClient)
        );

        this.responseStrategy.initialize(goal, targetUser);

        if (responseStrategy instanceof AdvancedResponseStrategy) {
            ((AdvancedResponseStrategy) responseStrategy).loadHistoricalData(historicalChats);
        }
    }

    private PlatformConnector createPlatformConnector(Platform platform) {
        return switch (platform) {
            case TELEGRAM -> userService.getUser().getTelegramConnector();
            case WHATSAPP -> userService.getUser().getWhatsappConnector();
            case INSTAGRAM -> userService.getUser().getInstagramConnector();
            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        };
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
            
            // After ingestion, categorize dialogs with success scores
            // For incremental updates, only categorize dialogs with new messages
            // For first-time ingestion, categorize all dialogs
            try {
                int userId = getCurrentUserId();
                if (userId > 0) {
                    System.out.println("Starting categorization of ingested chats...");
                    // Categorize all dialogs (will re-categorize ones with new messages)
                    // This ensures success scores are updated for chats with new messages
                    categorizationService.categorizeAllDialogs(userId);
                }
            } catch (Exception e) {
                System.err.println("Error categorizing chats after ingestion: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Start chat ingestion for a specific connector (used for platform registration/login flows).
     */
    public void startChatIngestion(PlatformConnector connector) {
        this.platformConnector = connector;
        startChatIngestion();
    }
    
    private int getCurrentUserId() {
        // TODO: Implement to get current user ID
        // For now, return 1 as default
        return 1;
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