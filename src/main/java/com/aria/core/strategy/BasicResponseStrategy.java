package com.aria.core.strategy;

import com.aria.core.model.ConversationGoal;
import com.aria.ai.ResponseGenerator;

public class BasicResponseStrategy extends BaseResponseStrategy {

    public BasicResponseStrategy(ResponseGenerator responseGenerator) {
        super(responseGenerator);
    }

    @Override
    public String generateOpeningMessage() {
        validateInitialization();
        String openingMessage = responseGenerator.generateOpeningLine();
        addToHistory("You", openingMessage);
        return openingMessage;
    }

    @Override
    public String generateResponse(String incomingMessage) {
        validateInitialization();

        // Add incoming message to history
        addToHistory(currentGoal.getTargetName(), incomingMessage);

        // Generate AI response with full context
        String aiResponse = responseGenerator.generateResponse(
                incomingMessage,
                getConversationHistory()
        );

        // Add AI response to history
        addToHistory("You", aiResponse);

        return aiResponse;
    }
}