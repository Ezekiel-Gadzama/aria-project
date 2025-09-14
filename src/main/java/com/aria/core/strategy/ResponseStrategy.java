// ResponseStrategy.java
package com.aria.core.strategy;

import com.aria.core.model.ConversationGoal;

public interface ResponseStrategy {
    void initialize(ConversationGoal goal);
    String generateResponse(String incomingMessage);
    String generateOpeningMessage();
    String getConversationHistory();
    void clearHistory();
    double estimateEngagementLevel();
}