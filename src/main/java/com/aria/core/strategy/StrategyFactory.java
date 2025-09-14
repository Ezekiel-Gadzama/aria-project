// StrategyFactory.java

package com.aria.core.strategy;

import com.aria.ai.ResponseGenerator;
import com.aria.analysis.ChatAnalyzer;

public class StrategyFactory {

    public static ResponseStrategy createStrategy(String phase, ResponseGenerator responseGenerator) {
        switch (phase.toLowerCase()) {
            case "phase2":
            case "advanced":
                return new AdvancedResponseStrategy(responseGenerator, new ChatAnalyzer());
            case "phase0":
            case "phase1":
            case "basic":
            default:
                return new BasicResponseStrategy(responseGenerator);
        }
    }
}