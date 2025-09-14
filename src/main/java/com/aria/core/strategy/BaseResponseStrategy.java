package com.aria.core.strategy;

import com.aria.core.model.ConversationGoal;
import com.aria.ai.ResponseGenerator;
import java.util.stream.DoubleStream;

public abstract class BaseResponseStrategy implements ResponseStrategy {
    protected final ResponseGenerator responseGenerator;
    protected ConversationGoal currentGoal;
    protected StringBuilder conversationHistory;

    protected BaseResponseStrategy(ResponseGenerator responseGenerator) {
        this.responseGenerator = responseGenerator;
        this.conversationHistory = new StringBuilder();
    }

    @Override
    public void initialize(ConversationGoal goal) {
        this.currentGoal = goal;
        this.responseGenerator.setCurrentGoal(goal);
        clearHistory();
    }

    @Override
    public String getConversationHistory() {
        return conversationHistory.toString();
    }

    @Override
    public void clearHistory() {
        conversationHistory.setLength(0);
    }

    @Override
    public double estimateEngagementLevel() {
        String history = conversationHistory.toString();
        int totalMessages = history.split("\n\n").length;
        if (totalMessages == 0) return 1.0;

        int shortResponses = countShortResponses(history);
        double engagement = 1.0 - ((double) shortResponses / totalMessages * 0.5);
        return Math.max(0.1, Math.min(1.0, engagement));
    }

    protected void addToHistory(String sender, String message) {
        conversationHistory.append(sender)
                .append(": ")
                .append(message)
                .append("\n\n");
    }

    protected int countShortResponses(String history) {
        String[] messages = history.split("\n\n");
        int count = 0;

        for (String message : messages) {
            if (message.contains(": ")) {
                String content = message.substring(message.indexOf(": ") + 2);
                if (content.split("\\s+").length <= 3) {
                    count++;
                }
            }
        }
        return count;
    }

    protected void validateInitialization() {
        if (currentGoal == null) {
            throw new IllegalStateException("Strategy not initialized. Call initialize() first.");
        }
    }

    protected double calculateWeightedAverage(DoubleStream successfulValues,
                                              DoubleStream failedValues,
                                              double baseValue) {
        double successfulAvg = successfulValues.average().orElse(0.0);
        double failedAvg = failedValues.average().orElse(0.0);

        return (successfulAvg * 0.7) + (failedAvg * 0.15) + (baseValue * 0.15);
    }
}