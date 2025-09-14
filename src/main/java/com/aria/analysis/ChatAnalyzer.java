// ChatAnalyzer.java

package com.aria.analysis;

import com.aria.core.model.Message;
import com.aria.core.model.ChatProfile;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChatAnalyzer {
    private final SuccessScorer successScorer;
    private final StyleExtractor styleExtractor;

    public ChatAnalyzer() {
        this.successScorer = new SuccessScorer();
        this.styleExtractor = new StyleExtractor();
    }

    public Map<String, ChatProfile> analyzeAllChats(Map<String, List<Message>> allChats, String currentGoalType) {
        return allChats.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            ChatProfile profile = styleExtractor.extractStyleProfile(entry.getValue());
                            double successScore = successScorer.calculateSuccessScore(entry.getValue(), currentGoalType);
                            profile.setSuccessScore(successScore);
                            return profile;
                        }
                ));
    }

    public ChatProfile createSyntheticProfile(double successScore) {
        // Create base AI personality profile (Category C - 15%)
        ChatProfile baseProfile = new ChatProfile();
        baseProfile.setSuccessScore(successScore);
        baseProfile.setHumorLevel(0.3);
        baseProfile.setFormalityLevel(0.6);
        baseProfile.setEmpathyLevel(0.7);
        return baseProfile;
    }
}