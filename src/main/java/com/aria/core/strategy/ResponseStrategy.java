// ResponseStrategy.java
package com.aria.core.strategy;

import com.aria.core.model.ConversationGoal;
import com.aria.core.model.TargetUser;

public interface ResponseStrategy {
    void initialize(ConversationGoal goal, TargetUser targetUser);
    String generateResponse(String incomingMessage);
    String generateOpeningMessage();
    String getConversationHistory();
    void clearHistory();
    double estimateEngagementLevel();
}