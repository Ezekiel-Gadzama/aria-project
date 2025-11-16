package com.aria.analysis;

import com.aria.core.model.Message;
import com.aria.core.model.ChatProfile;
import java.time.Duration;
import java.util.List;

/**
 * Analyzes response timing patterns and calculates optimal response delays
 */
public class ResponseTimingAnalyzer {
    
    /**
     * Calculate optimal response delay based on:
     * 1. Historical response patterns (70% weight)
     * 2. Target's engagement level (20% weight)
     * 3. Base personality timing (10% weight)
     */
    public long calculateOptimalResponseDelay(List<Message> conversationHistory, 
                                               ChatProfile styleProfile,
                                               double engagementScore) {
        // 1. Analyze historical response patterns (70% weight)
        double historicalDelay = calculateHistoricalDelay(conversationHistory);
        
        // 2. Adjust based on engagement (20% weight)
        // Higher engagement = can reply faster
        // Lower engagement = should wait longer
        double engagementAdjustment = 1.0 - (engagementScore * 0.3); // 0.7 to 1.0 multiplier
        double adjustedDelay = historicalDelay * engagementAdjustment;
        
        // 3. Base personality timing (10% weight)
        double baseDelay = styleProfile != null ? 
            styleProfile.getResponseTimeAverage() : 120.0; // Default 2 minutes
        
        // Weighted calculation
        double optimalDelay = (adjustedDelay * 0.7) + (baseDelay * 0.1);
        
        // Clamp to reasonable bounds (30 seconds to 2 hours)
        optimalDelay = Math.max(30, Math.min(7200, optimalDelay));
        
        return (long) optimalDelay;
    }

    /**
     * Calculate average response delay in the conversation
     */
    private double calculateHistoricalDelay(List<Message> conversationHistory) {
        if (conversationHistory == null || conversationHistory.size() < 2) {
            return 120.0; // Default 2 minutes
        }

        long totalDelay = 0;
        int delayCount = 0;

        for (int i = 1; i < conversationHistory.size(); i++) {
            Message current = conversationHistory.get(i);
            Message previous = conversationHistory.get(i - 1);

            // Only consider delays when user responds after target
            if (current.isFromUser() && !previous.isFromUser() && 
                current.getTimestamp() != null && previous.getTimestamp() != null) {
                
                long seconds = Duration.between(previous.getTimestamp(), current.getTimestamp()).getSeconds();
                if (seconds > 0 && seconds < 86400) { // Within 24 hours
                    totalDelay += seconds;
                    delayCount++;
                }
            }
        }

        return delayCount > 0 ? (double) totalDelay / delayCount : 120.0;
    }

    /**
     * Analyze target's response timing patterns
     */
    public TargetTimingPattern analyzeTargetTiming(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new TargetTimingPattern(120.0, 60.0, 300.0);
        }

        List<Long> targetDelays = new java.util.ArrayList<>();
        
        for (int i = 1; i < messages.size(); i++) {
            Message current = messages.get(i);
            Message previous = messages.get(i - 1);

            // Only consider delays when target responds after user
            if (!current.isFromUser() && previous.isFromUser() &&
                current.getTimestamp() != null && previous.getTimestamp() != null) {
                
                long seconds = Duration.between(previous.getTimestamp(), current.getTimestamp()).getSeconds();
                if (seconds > 0 && seconds < 86400) { // Within 24 hours
                    targetDelays.add(seconds);
                }
            }
        }

        if (targetDelays.isEmpty()) {
            return new TargetTimingPattern(120.0, 60.0, 300.0);
        }

        double avg = targetDelays.stream().mapToLong(Long::longValue).average().orElse(120);
        double min = targetDelays.stream().mapToLong(Long::longValue).min().orElse(60);
        double max = targetDelays.stream().mapToLong(Long::longValue).max().orElse(300);

        return new TargetTimingPattern(avg, min, max);
    }

    /**
     * Determine if user should respond now or wait based on engagement
     */
    public boolean shouldRespondNow(List<Message> messages, double engagementScore, 
                                     long lastTargetMessageAge) {
        // If engagement is very high, can respond faster
        if (engagementScore > 0.8 && lastTargetMessageAge > 30) {
            return true;
        }

        // If engagement is very low, wait longer
        if (engagementScore < 0.3) {
            return lastTargetMessageAge > 3600; // Wait at least 1 hour
        }

        // Normal engagement: wait for reasonable delay
        return lastTargetMessageAge > 300; // 5 minutes minimum
    }

    public static class TargetTimingPattern {
        public final double averageDelay;
        public final double minDelay;
        public final double maxDelay;

        public TargetTimingPattern(double averageDelay, double minDelay, double maxDelay) {
            this.averageDelay = averageDelay;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
        }

        public double getRecommendedDelay() {
            // Use average, but adjust if target is very fast/slow
            if (averageDelay < 60) {
                return averageDelay * 1.5; // If they're fast, be a bit slower
            } else if (averageDelay > 3600) {
                return averageDelay * 0.8; // If they're slow, be a bit faster
            }
            return averageDelay;
        }
    }
}

