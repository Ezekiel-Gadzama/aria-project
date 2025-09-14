// AdvancedResponseStrategy.java

package com.aria.core.strategy;

import com.aria.core.model.ConversationGoal;
import com.aria.core.model.ChatProfile;
import com.aria.analysis.ChatAnalyzer;
import com.aria.ai.ResponseGenerator;
import java.util.Map;
import java.util.stream.Collectors;

public class AdvancedResponseStrategy {
    private final ResponseGenerator responseGenerator;
    private final ChatAnalyzer chatAnalyzer;
    private Map<String, ChatProfile> analyzedChats;
    private ChatProfile synthesizedProfile;

    public AdvancedResponseStrategy(ResponseGenerator responseGenerator, ChatAnalyzer chatAnalyzer) {
        this.responseGenerator = responseGenerator;
        this.chatAnalyzer = chatAnalyzer;
    }

    public void initializeStrategy(Map<String, List<Message>> historicalChats, ConversationGoal goal) {
        // Analyze all historical chats
        this.analyzedChats = chatAnalyzer.analyzeAllChats(historicalChats, goal.getDesiredOutcome());

        // Synthesize the optimal communication profile
        this.synthesizedProfile = synthesizeOptimalProfile();

        responseGenerator.setCurrentGoal(goal);
        responseGenerator.setStyleProfile(synthesizedProfile);
    }

    private ChatProfile synthesizeOptimalProfile() {
        // Category A: Successful chats (70% weight)
        Map<String, ChatProfile> successfulChats = analyzedChats.entrySet().stream()
                .filter(entry -> entry.getValue().getSuccessScore() >= 0.7)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Category B: Attempted but failed (15% weight)
        Map<String, ChatProfile> failedChats = analyzedChats.entrySet().stream()
                .filter(entry -> entry.getValue().getSuccessScore() < 0.7 && entry.getValue().getSuccessScore() >= 0.3)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Category C: Base AI personality (15% weight)
        ChatProfile baseProfile = chatAnalyzer.createSyntheticProfile(0.5);

        return blendProfiles(successfulChats, failedChats, baseProfile);
    }

    private ChatProfile blendProfiles(Map<String, ChatProfile> successful,
                                      Map<String, ChatProfile> failed,
                                      ChatProfile base) {
        ChatProfile blended = new ChatProfile();

        // Weighted average of all attributes
        blended.setHumorLevel(
                successful.values().stream().mapToDouble(ChatProfile::getHumorLevel).average().orElse(0.0) * 0.7 +
                        failed.values().stream().mapToDouble(ChatProfile::getHumorLevel).average().orElse(0.0) * 0.15 +
                        base.getHumorLevel() * 0.15
        );

        // Repeat for other attributes...

        return blended;
    }

    public String generatePersonalizedResponse(String incomingMessage, String conversationHistory) {
        String enhancedPrompt = buildPersonalizedPrompt(incomingMessage, conversationHistory);
        return responseGenerator.generateResponse(enhancedPrompt);
    }

    private String buildPersonalizedPrompt(String incomingMessage, String conversationHistory) {
        return String.format("""
            Communication Style Profile:
            - Humor Level: %.2f
            - Formality: %.2f
            - Empathy: %.2f
            - Preferred Opening: %s
            
            Conversation Context:
            Target: %s
            Goal: %s
            History: %s
            New Message: %s
            
            Generate a response that matches the above communication style:""",
                synthesizedProfile.getHumorLevel(),
                synthesizedProfile.getFormalityLevel(),
                synthesizedProfile.getEmpathyLevel(),
                synthesizedProfile.getPreferredOpening(),
                currentGoal.getTargetName(),
                currentGoal.getDesiredOutcome(),
                conversationHistory,
                incomingMessage
        );
    }
}