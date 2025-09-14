// SuccessScorer.java
package com.aria.analysis;

import com.aria.core.model.Message;
import java.util.List;

public class SuccessScorer {

    public double calculateSuccessScore(List<Message> conversation, String goalType) {
        double score = 0.0;

        // Analyze conversation outcome
        if (isGoalAchieved(conversation, goalType)) {
            score += 0.7; // Base success score
        }

        // Add engagement metrics
        score += calculateEngagementScore(conversation) * 0.3;

        return Math.min(1.0, score);
    }

    private boolean isGoalAchieved(List<Message> conversation, String goalType) {
        String lastMessages = conversation.stream()
                .limit(10)
                .map(Message::getContent)
                .reduce("", (a, b) -> a + " " + b)
                .toLowerCase();

        return switch (goalType.toLowerCase()) {
            case "date" -> lastMessages.contains("date") || lastMessages.contains("meet") || lastMessages.contains("coffee");
            case "investment" -> lastMessages.contains("invest") || lastMessages.contains("meeting") || lastMessages.contains("pitch");
            case "sponsorship" -> lastMessages.contains("sponsor") || lastMessages.contains("deal") || lastMessages.contains("partner");
            default -> false;
        };
    }

    private double calculateEngagementScore(List<Message> conversation) {
        // Calculate based on message length, response time, etc.
        return 0.8; // Placeholder
    }
}