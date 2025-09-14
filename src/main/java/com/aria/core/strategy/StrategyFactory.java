package com.aria.core.strategy;

import com.aria.ai.ResponseGenerator;
import com.aria.analysis.ChatAnalyzer;

public class StrategyFactory {
    public enum StrategyType {
        BASIC,
        ADVANCED
    }

    public static ResponseStrategy createStrategy(StrategyType type, ResponseGenerator responseGenerator) {
        return createStrategy(type, responseGenerator, null);
    }

    public static ResponseStrategy createStrategy(StrategyType type,
                                                  ResponseGenerator responseGenerator,
                                                  ChatAnalyzer chatAnalyzer) {
        switch (type) {
            case ADVANCED:
                if (chatAnalyzer == null) {
                    throw new IllegalArgumentException("ChatAnalyzer required for advanced strategy");
                }
                return new AdvancedResponseStrategy(responseGenerator, chatAnalyzer);
            case BASIC:
            default:
                return new BasicResponseStrategy(responseGenerator);
        }
    }

    public static ResponseStrategy createStrategy(String type, ResponseGenerator responseGenerator) {
        try {
            StrategyType strategyType = StrategyType.valueOf(type.toUpperCase());
            return createStrategy(strategyType, responseGenerator);
        } catch (IllegalArgumentException e) {
            return createStrategy(StrategyType.BASIC, responseGenerator);
        }
    }
}