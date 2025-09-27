package com.aria.core.strategy;

import com.aria.core.model.ChatProfile;
import com.aria.core.model.Message;
import com.aria.analysis.ChatAnalyzer;
import com.aria.ai.ResponseGenerator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdvancedResponseStrategy extends BaseResponseStrategy {
    private final ChatAnalyzer chatAnalyzer;
    private Map<String, ChatProfile> analyzedChats;
    private ChatProfile synthesizedProfile;

    public AdvancedResponseStrategy(ResponseGenerator responseGenerator, ChatAnalyzer chatAnalyzer) {
        super(responseGenerator);
        this.chatAnalyzer = chatAnalyzer;
    }

    public void loadHistoricalData(Map<String, List<Message>> historicalChats) {
        validateInitialization();
        this.analyzedChats = chatAnalyzer.analyzeAllChats(historicalChats, currentGoal.getDesiredOutcome());
        this.synthesizedProfile = synthesizeOptimalProfile();
        responseGenerator.setStyleProfile(synthesizedProfile);
    }

    @Override
    public String generateOpeningMessage() {
        validateInitialization();
        String openingMessage = generatePersonalizedOpening();
        addToHistory("You", openingMessage);
        return openingMessage;
    }

    @Override
    public String generateResponse(String incomingMessage) {
        validateInitialization();

        addToHistory(getCurrentTargetAlias(), incomingMessage);

        String personalizedResponse = generatePersonalizedResponse(
                incomingMessage,
                getConversationHistory()
        );

        addToHistory("You", personalizedResponse);
        return personalizedResponse;
    }

    private String generatePersonalizedOpening() {
        String prompt = buildPersonalizedPrompt("", "");
        return responseGenerator.generateResponse(prompt);
    }

    private String generatePersonalizedResponse(String incomingMessage, String history) {
        String prompt = buildPersonalizedPrompt(incomingMessage, history);
        return responseGenerator.generateResponse(prompt);
    }

    private ChatProfile synthesizeOptimalProfile() {
        Map<String, ChatProfile> successfulChats = filterChatsByScore(0.7, 1.0);
        Map<String, ChatProfile> failedChats = filterChatsByScore(0.3, 0.7);
        ChatProfile baseProfile = chatAnalyzer.createSyntheticProfile(0.5);

        return blendProfiles(successfulChats, failedChats, baseProfile);
    }

    private Map<String, ChatProfile> filterChatsByScore(double minScore, double maxScore) {
        return analyzedChats.entrySet().stream()
                .filter(entry -> {
                    double score = entry.getValue().getSuccessScore();
                    return score >= minScore && score < maxScore;
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private ChatProfile blendProfiles(Map<String, ChatProfile> successful,
                                      Map<String, ChatProfile> failed,
                                      ChatProfile base) {
        ChatProfile blended = new ChatProfile();

        blended.setHumorLevel(calculateWeightedAverage(
                successful.values().stream().mapToDouble(ChatProfile::getHumorLevel),
                failed.values().stream().mapToDouble(ChatProfile::getHumorLevel),
                base.getHumorLevel()
        ));

        blended.setFormalityLevel(calculateWeightedAverage(
                successful.values().stream().mapToDouble(ChatProfile::getFormalityLevel),
                failed.values().stream().mapToDouble(ChatProfile::getFormalityLevel),
                base.getFormalityLevel()
        ));

        blended.setEmpathyLevel(calculateWeightedAverage(
                successful.values().stream().mapToDouble(ChatProfile::getEmpathyLevel),
                failed.values().stream().mapToDouble(ChatProfile::getEmpathyLevel),
                base.getEmpathyLevel()
        ));

        return blended;
    }

    private String buildPersonalizedPrompt(String incomingMessage, String conversationHistory) {
        return String.format("""
            Communication Style Profile:
            - Humor Level: %.2f
            - Formality: %.2f
            - Empathy: %.2f
            
            Conversation Context:
            Target: %s
            Platform: %s
            Meeting Context: %s
            Goal: %s
            History: %s
            New Message: %s
            
            Generate a response matching the communication style:""",
                synthesizedProfile.getHumorLevel(),
                synthesizedProfile.getFormalityLevel(),
                synthesizedProfile.getEmpathyLevel(),
                getCurrentTargetAlias(),  // Use helper method
                getCurrentPlatform(),     // Use helper method
                currentGoal.getMeetingContext(),
                currentGoal.getDesiredOutcome(),
                conversationHistory,
                incomingMessage
        );
    }

    @Override
    public double estimateEngagementLevel() {
        validateInitialization();
        double baseEngagement = super.estimateEngagementLevel();

        if (analyzedChats != null && analyzedChats.containsKey(getCurrentTargetAlias())) {
            ChatProfile targetProfile = analyzedChats.get(getCurrentTargetAlias());
            double historicalEngagement = targetProfile.getSuccessScore();
            return (baseEngagement * 0.6) + (historicalEngagement * 0.4);
        }

        return baseEngagement;
    }

    public ChatProfile getSynthesizedProfile() {
        return synthesizedProfile;
    }

    public Map<String, ChatProfile> getAnalyzedChats() {
        return analyzedChats;
    }
}