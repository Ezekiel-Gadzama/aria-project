// StyleExtractor.java

package com.aria.analysis;

import com.aria.core.model.Message;
import com.aria.core.model.ChatProfile;
import java.util.List;

public class StyleExtractor {

    public ChatProfile extractStyleProfile(List<Message> conversation) {
        ChatProfile profile = new ChatProfile();

        profile.setHumorLevel(calculateHumorLevel(convention));
        profile.setFormalityLevel(calculateFormality(conversation));
        profile.setEmpathyLevel(calculateEmpathy(conversation));
        profile.setPreferredOpening(extractOpeningPattern(conversation));
        profile.setResponseTimeAverage(calculateAvgResponseTime(conversation));

        return profile;
    }

    private double calculateHumorLevel(List<Message> conversation) {
        long jokeCount = conversation.stream()
                .filter(msg -> msg.getContent().contains("😂") ||
                        msg.getContent().contains("😆") ||
                        msg.getContent().contains("haha") ||
                        msg.getContent().contains("lol"))
                .count();
        return (double) jokeCount / conversation.size();
    }

    // Other style calculation methods...
}