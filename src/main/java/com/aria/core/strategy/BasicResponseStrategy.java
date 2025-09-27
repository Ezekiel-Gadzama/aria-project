package com.aria.core.strategy;

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

        // Use the helper method from base class
        addToHistory(getCurrentTargetAlias(), incomingMessage);

        String aiResponse = responseGenerator.generateResponse(
                incomingMessage,
                getConversationHistory()
        );

        addToHistory("You", aiResponse);
        return aiResponse;
    }
}