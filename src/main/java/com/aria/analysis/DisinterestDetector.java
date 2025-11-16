package com.aria.analysis;

import com.aria.core.model.Message;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Duration;
import java.util.*;

/**
 * Detects disinterest indicators in conversations and calculates disinterest probability
 */
public class DisinterestDetector {
    
    // Thresholds for disinterest indicators
    private static final double SHORT_RESPONSE_THRESHOLD = 5.0; // Average words per message
    private static final long LONG_DELAY_THRESHOLD = 86400; // 24 hours in seconds
    private static final double LOW_QUESTION_RATE_THRESHOLD = 0.1; // Less than 10% questions
    private static final int MIN_MESSAGES_FOR_ANALYSIS = 3;

    /**
     * Analyze conversation for disinterest indicators
     * @param messages List of messages in the conversation
     * @return DisinterestAnalysis result
     */
    public DisinterestAnalysis analyzeConversation(List<Message> messages) {
        if (messages == null || messages.size() < MIN_MESSAGES_FOR_ANALYSIS) {
            return new DisinterestAnalysis(0.0, new ArrayList<>(), "Insufficient data for analysis");
        }

        List<DisinterestIndicator> indicators = new ArrayList<>();
        List<Double> indicatorWeights = new ArrayList<>();

        // 1. Analyze response length (weight: 0.25)
        double avgTargetResponseLength = calculateAverageResponseLength(messages, false);
        if (avgTargetResponseLength < SHORT_RESPONSE_THRESHOLD) {
            double severity = 1.0 - (avgTargetResponseLength / SHORT_RESPONSE_THRESHOLD);
            indicators.add(new DisinterestIndicator(
                "short_responses",
                "Target's responses are very short (avg " + String.format("%.1f", avgTargetResponseLength) + " words)",
                severity
            ));
            indicatorWeights.add(0.25);
        }

        // 2. Analyze response timing (weight: 0.30)
        double avgResponseDelay = calculateAverageResponseDelay(messages, false);
        if (avgResponseDelay > LONG_DELAY_THRESHOLD) {
            double severity = Math.min(1.0, (avgResponseDelay / LONG_DELAY_THRESHOLD) / 2.0);
            indicators.add(new DisinterestIndicator(
                "long_delays",
                "Target takes very long to respond (avg " + formatDuration(avgResponseDelay) + ")",
                severity
            ));
            indicatorWeights.add(0.30);
        }

        // 3. Analyze question rate (weight: 0.20)
        double questionRate = calculateQuestionRate(messages, false);
        if (questionRate < LOW_QUESTION_RATE_THRESHOLD) {
            double severity = 1.0 - (questionRate / LOW_QUESTION_RATE_THRESHOLD);
            indicators.add(new DisinterestIndicator(
                "low_engagement",
                "Target asks few questions (only " + String.format("%.1f%%", questionRate * 100) + " of messages are questions)",
                severity
            ));
            indicatorWeights.add(0.20);
        }

        // 4. Analyze one-word responses (weight: 0.15)
        double oneWordRate = calculateOneWordResponseRate(messages, false);
        if (oneWordRate > 0.3) {
            double severity = Math.min(1.0, oneWordRate / 0.5);
            indicators.add(new DisinterestIndicator(
                "one_word_responses",
                "High rate of one-word responses (" + String.format("%.1f%%", oneWordRate * 100) + ")",
                severity
            ));
            indicatorWeights.add(0.15);
        }

        // 5. Analyze engagement decline over time (weight: 0.10)
        double engagementDecline = calculateEngagementDecline(messages);
        if (engagementDecline > 0.3) {
            double severity = Math.min(1.0, engagementDecline);
            indicators.add(new DisinterestIndicator(
                "declining_engagement",
                "Engagement is declining over time (decline: " + String.format("%.1f%%", engagementDecline * 100) + ")",
                severity
            ));
            indicatorWeights.add(0.10);
        }

        // Calculate weighted disinterest probability
        double disinterestProbability = calculateWeightedProbability(indicators, indicatorWeights);

        // Generate recommendation
        String recommendation = generateRecommendation(disinterestProbability, indicators);

        return new DisinterestAnalysis(disinterestProbability, indicators, recommendation);
    }

    private double calculateAverageResponseLength(List<Message> messages, boolean fromUser) {
        return messages.stream()
                .filter(msg -> msg.isFromUser() == fromUser)
                .mapToInt(msg -> msg.getContent() != null ? msg.getContent().split("\\s+").length : 0)
                .average()
                .orElse(0.0);
    }

    private double calculateAverageResponseDelay(List<Message> messages, boolean fromUser) {
        List<Long> delays = new ArrayList<>();
        Message previousMessage = null;

        for (Message msg : messages) {
            if (previousMessage != null && 
                msg.isFromUser() == fromUser && 
                previousMessage.isFromUser() != fromUser) {
                
                if (msg.getTimestamp() != null && previousMessage.getTimestamp() != null) {
                    long seconds = Duration.between(previousMessage.getTimestamp(), msg.getTimestamp()).getSeconds();
                    delays.add(seconds);
                }
            }
            previousMessage = msg;
        }

        return delays.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private double calculateQuestionRate(List<Message> messages, boolean fromUser) {
        long totalMessages = messages.stream()
                .filter(msg -> msg.isFromUser() == fromUser)
                .count();

        if (totalMessages == 0) return 0.0;

        long questionMessages = messages.stream()
                .filter(msg -> msg.isFromUser() == fromUser && msg.getContent() != null)
                .filter(msg -> msg.getContent().trim().endsWith("?") || 
                              msg.getContent().trim().toLowerCase().startsWith("how") ||
                              msg.getContent().trim().toLowerCase().startsWith("what") ||
                              msg.getContent().trim().toLowerCase().startsWith("when") ||
                              msg.getContent().trim().toLowerCase().startsWith("where") ||
                              msg.getContent().trim().toLowerCase().startsWith("why") ||
                              msg.getContent().trim().toLowerCase().startsWith("who"))
                .count();

        return (double) questionMessages / totalMessages;
    }

    private double calculateOneWordResponseRate(List<Message> messages, boolean fromUser) {
        long totalMessages = messages.stream()
                .filter(msg -> msg.isFromUser() == fromUser)
                .count();

        if (totalMessages == 0) return 0.0;

        long oneWordMessages = messages.stream()
                .filter(msg -> msg.isFromUser() == fromUser && msg.getContent() != null)
                .filter(msg -> {
                    String content = msg.getContent().trim();
                    String[] words = content.split("\\s+");
                    return words.length == 1;
                })
                .count();

        return (double) oneWordMessages / totalMessages;
    }

    private double calculateEngagementDecline(List<Message> messages) {
        if (messages.size() < 6) return 0.0;

        // Split messages into first half and second half
        int midPoint = messages.size() / 2;
        List<Message> firstHalf = messages.subList(0, midPoint);
        List<Message> secondHalf = messages.subList(midPoint, messages.size());

        double firstHalfAvgLength = calculateAverageResponseLength(firstHalf, false);
        double secondHalfAvgLength = calculateAverageResponseLength(secondHalf, false);

        if (firstHalfAvgLength == 0) return 0.0;

        double decline = (firstHalfAvgLength - secondHalfAvgLength) / firstHalfAvgLength;
        return Math.max(0.0, decline);
    }

    private double calculateWeightedProbability(List<DisinterestIndicator> indicators, List<Double> weights) {
        if (indicators.isEmpty()) return 0.0;

        double totalWeight = 0.0;
        double weightedSum = 0.0;

        for (int i = 0; i < indicators.size() && i < weights.size(); i++) {
            double weight = weights.get(i);
            double severity = indicators.get(i).getSeverity();
            weightedSum += weight * severity;
            totalWeight += weight;
        }

        return totalWeight > 0 ? Math.min(1.0, weightedSum / totalWeight) : 0.0;
    }

    private String generateRecommendation(double probability, List<DisinterestIndicator> indicators) {
        if (probability < 0.3) {
            return "Continue conversation. Engagement looks good.";
        } else if (probability < 0.5) {
            return "Monitor engagement. Some indicators suggest mild disinterest. Try to ask more engaging questions.";
        } else if (probability < 0.7) {
            return "Warning: Moderate disinterest detected. Consider changing approach or taking a break from messaging.";
        } else {
            return "High disinterest probability. Strongly recommend disengaging or waiting for target to initiate.";
        }
    }

    private String formatDuration(double seconds) {
        if (seconds < 60) {
            return String.format("%.0f seconds", seconds);
        } else if (seconds < 3600) {
            return String.format("%.1f minutes", seconds / 60);
        } else if (seconds < 86400) {
            return String.format("%.1f hours", seconds / 3600);
        } else {
            return String.format("%.1f days", seconds / 86400);
        }
    }

    public static class DisinterestAnalysis {
        private final double probability;
        private final List<DisinterestIndicator> indicators;
        private final String recommendation;

        public DisinterestAnalysis(double probability, List<DisinterestIndicator> indicators, String recommendation) {
            this.probability = probability;
            this.indicators = indicators;
            this.recommendation = recommendation;
        }

        public double getProbability() { return probability; }
        public List<DisinterestIndicator> getIndicators() { return indicators; }
        public String getRecommendation() { return recommendation; }

        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("probability", probability);
            json.put("recommendation", recommendation);
            
            JSONArray indicatorsArray = new JSONArray();
            for (DisinterestIndicator indicator : indicators) {
                indicatorsArray.put(indicator.toJSON());
            }
            json.put("indicators", indicatorsArray);
            
            return json;
        }
    }

    public static class DisinterestIndicator {
        private final String type;
        private final String description;
        private final double severity;

        public DisinterestIndicator(String type, String description, double severity) {
            this.type = type;
            this.description = description;
            this.severity = severity;
        }

        public String getType() { return type; }
        public String getDescription() { return description; }
        public double getSeverity() { return severity; }

        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("description", description);
            json.put("severity", severity);
            return json;
        }
    }
}

