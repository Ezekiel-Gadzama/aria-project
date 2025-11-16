package com.aria.core.strategy;

import com.aria.core.model.ChatProfile;
import com.aria.core.model.Message;
import com.aria.analysis.SuccessScorer;
import com.aria.analysis.StyleExtractor;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements the 70%/15%/15% weighted response synthesis algorithm
 * - 70%: Successful conversations (Category A)
 * - 15%: Failed/attempted conversations (Category B)
 * - 15%: Base AI personality (Category C)
 */
public class WeightedResponseSynthesis {
    private static final double WEIGHT_SUCCESSFUL = 0.70;
    private static final double WEIGHT_FAILED = 0.15;
    private static final double WEIGHT_BASE = 0.15;

    private final SuccessScorer successScorer;
    private final StyleExtractor styleExtractor;

    public WeightedResponseSynthesis(SuccessScorer successScorer, StyleExtractor styleExtractor) {
        this.successScorer = successScorer;
        this.styleExtractor = styleExtractor;
    }

    /**
     * Get the SuccessScorer instance for external use
     */
    public SuccessScorer getSuccessScorer() {
        return successScorer;
    }

    /**
     * Synthesize a communication profile using weighted blending
     * @param allChats Map of chat identifier to messages
     * @param goalType The goal type for success scoring
     * @return Synthesized ChatProfile
     */
    public ChatProfile synthesizeProfile(Map<String, List<Message>> allChats, String goalType) {
        // Categorize chats into Category A, B, and prepare Category C
        Map<String, List<Message>> categoryA = new HashMap<>();
        Map<String, List<Message>> categoryB = new HashMap<>();
        
        double successThreshold = 0.7; // 70% threshold for successful conversations
        double failedThreshold = 0.3;  // Below 30% for failed conversations

        // Analyze each chat and categorize
        for (Map.Entry<String, List<Message>> entry : allChats.entrySet()) {
            double successScore = successScorer.calculateSuccessScore(entry.getValue(), goalType);
            
            if (successScore >= successThreshold) {
                categoryA.put(entry.getKey(), entry.getValue());
            } else if (successScore < failedThreshold) {
                categoryB.put(entry.getKey(), entry.getValue());
            }
            // Chats in between are not used to avoid ambiguity
        }

        // Extract profiles from each category
        List<ChatProfile> successfulProfiles = categoryA.values().stream()
                .map(messages -> styleExtractor.extractStyleProfile(messages))
                .collect(Collectors.toList());

        List<ChatProfile> failedProfiles = categoryB.values().stream()
                .map(messages -> styleExtractor.extractStyleProfile(messages))
                .collect(Collectors.toList());

        ChatProfile baseProfile = createBaseProfile(); // Category C

        // Blend profiles with weights
        ChatProfile blended = blendProfiles(successfulProfiles, failedProfiles, baseProfile);

        return blended;
    }

    /**
     * Blend multiple profiles with the 70%/15%/15% weighting
     */
    private ChatProfile blendProfiles(List<ChatProfile> successful, 
                                     List<ChatProfile> failed, 
                                     ChatProfile base) {
        ChatProfile blended = new ChatProfile();

        // Calculate averages for each category
        ChatProfile avgSuccessful = averageProfiles(successful);
        ChatProfile avgFailed = averageProfiles(failed);

        // Weighted blending: 70% successful + 15% failed + 15% base
        if (avgSuccessful != null) {
            blended.setHumorLevel(
                avgSuccessful.getHumorLevel() * WEIGHT_SUCCESSFUL +
                (avgFailed != null ? avgFailed.getHumorLevel() * WEIGHT_FAILED : 0) +
                base.getHumorLevel() * WEIGHT_BASE
            );

            blended.setFormalityLevel(
                avgSuccessful.getFormalityLevel() * WEIGHT_SUCCESSFUL +
                (avgFailed != null ? avgFailed.getFormalityLevel() * WEIGHT_FAILED : 0) +
                base.getFormalityLevel() * WEIGHT_BASE
            );

            blended.setEmpathyLevel(
                avgSuccessful.getEmpathyLevel() * WEIGHT_SUCCESSFUL +
                (avgFailed != null ? avgFailed.getEmpathyLevel() * WEIGHT_FAILED : 0) +
                base.getEmpathyLevel() * WEIGHT_BASE
            );

            blended.setResponseTimeAverage(
                avgSuccessful.getResponseTimeAverage() * WEIGHT_SUCCESSFUL +
                (avgFailed != null ? avgFailed.getResponseTimeAverage() * WEIGHT_FAILED : 0) +
                base.getResponseTimeAverage() * WEIGHT_BASE
            );

            blended.setMessageLengthAverage(
                avgSuccessful.getMessageLengthAverage() * WEIGHT_SUCCESSFUL +
                (avgFailed != null ? avgFailed.getMessageLengthAverage() * WEIGHT_FAILED : 0) +
                base.getMessageLengthAverage() * WEIGHT_BASE
            );

            blended.setQuestionRate(
                avgSuccessful.getQuestionRate() * WEIGHT_SUCCESSFUL +
                (avgFailed != null ? avgFailed.getQuestionRate() * WEIGHT_FAILED : 0) +
                base.getQuestionRate() * WEIGHT_BASE
            );

            blended.setEngagementLevel(
                avgSuccessful.getEngagementLevel() * WEIGHT_SUCCESSFUL +
                (avgFailed != null ? avgFailed.getEngagementLevel() * WEIGHT_FAILED : 0) +
                base.getEngagementLevel() * WEIGHT_BASE
            );

            // Preferred opening: use from successful if available
            if (avgSuccessful.getPreferredOpening() != null && 
                !avgSuccessful.getPreferredOpening().isEmpty()) {
                blended.setPreferredOpening(avgSuccessful.getPreferredOpening());
            } else {
                blended.setPreferredOpening(base.getPreferredOpening());
            }
        } else {
            // Fallback: if no successful chats, blend failed + base (50/50)
            if (avgFailed != null) {
                blended.setHumorLevel(avgFailed.getHumorLevel() * 0.5 + base.getHumorLevel() * 0.5);
                blended.setFormalityLevel(avgFailed.getFormalityLevel() * 0.5 + base.getFormalityLevel() * 0.5);
                blended.setEmpathyLevel(avgFailed.getEmpathyLevel() * 0.5 + base.getEmpathyLevel() * 0.5);
                blended.setResponseTimeAverage(avgFailed.getResponseTimeAverage() * 0.5 + base.getResponseTimeAverage() * 0.5);
                blended.setMessageLengthAverage(avgFailed.getMessageLengthAverage() * 0.5 + base.getMessageLengthAverage() * 0.5);
                blended.setQuestionRate(avgFailed.getQuestionRate() * 0.5 + base.getQuestionRate() * 0.5);
                blended.setEngagementLevel(avgFailed.getEngagementLevel() * 0.5 + base.getEngagementLevel() * 0.5);
                blended.setPreferredOpening(avgFailed.getPreferredOpening());
            } else {
                // If no historical data, use base profile
                return base;
            }
        }

        return blended;
    }

    /**
     * Average multiple profiles into one
     */
    private ChatProfile averageProfiles(List<ChatProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return null;
        }

        ChatProfile averaged = new ChatProfile();
        
        double humorSum = profiles.stream().mapToDouble(ChatProfile::getHumorLevel).sum();
        double formalitySum = profiles.stream().mapToDouble(ChatProfile::getFormalityLevel).sum();
        double empathySum = profiles.stream().mapToDouble(ChatProfile::getEmpathyLevel).sum();
        double responseTimeSum = profiles.stream().mapToDouble(ChatProfile::getResponseTimeAverage).sum();
        double messageLengthSum = profiles.stream().mapToDouble(ChatProfile::getMessageLengthAverage).sum();
        double questionRateSum = profiles.stream().mapToDouble(ChatProfile::getQuestionRate).sum();
        double engagementSum = profiles.stream().mapToDouble(ChatProfile::getEngagementLevel).sum();

        int count = profiles.size();

        averaged.setHumorLevel(humorSum / count);
        averaged.setFormalityLevel(formalitySum / count);
        averaged.setEmpathyLevel(empathySum / count);
        averaged.setResponseTimeAverage(responseTimeSum / count);
        averaged.setMessageLengthAverage(messageLengthSum / count);
        averaged.setQuestionRate(questionRateSum / count);
        averaged.setEngagementLevel(engagementSum / count);

        // Use most common preferred opening
        String preferredOpening = profiles.stream()
                .map(ChatProfile::getPreferredOpening)
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Hi there!");

        averaged.setPreferredOpening(preferredOpening);

        return averaged;
    }

    /**
     * Create base AI personality profile (Category C)
     */
    private ChatProfile createBaseProfile() {
        ChatProfile base = new ChatProfile();
        // Balanced, engaging personality
        base.setHumorLevel(0.4);          // Moderate humor
        base.setFormalityLevel(0.5);      // Balanced formality
        base.setEmpathyLevel(0.7);        // High empathy
        base.setResponseTimeAverage(120.0); // 2 minutes average
        base.setMessageLengthAverage(25.0); // Average message length
        base.setQuestionRate(0.3);        // 30% questions
        base.setEngagementLevel(0.6);     // Moderate-high engagement
        base.setPreferredOpening("Hey! How are you doing?");
        return base;
    }

    /**
     * Get successful chat examples for context
     */
    public List<List<Message>> getSuccessfulChatExamples(Map<String, List<Message>> allChats, String goalType, int maxExamples) {
        return allChats.entrySet().stream()
                .filter(entry -> {
                    double score = successScorer.calculateSuccessScore(entry.getValue(), goalType);
                    return score >= 0.7;
                })
                .sorted((e1, e2) -> {
                    double score1 = successScorer.calculateSuccessScore(e1.getValue(), goalType);
                    double score2 = successScorer.calculateSuccessScore(e2.getValue(), goalType);
                    return Double.compare(score2, score1); // Descending order
                })
                .limit(maxExamples)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
}

