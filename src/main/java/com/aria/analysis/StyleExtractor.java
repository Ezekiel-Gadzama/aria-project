// StyleExtractor.java

// StyleExtractor.java
package com.aria.analysis;

import com.aria.core.model.Message;
import com.aria.core.model.ChatProfile;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class StyleExtractor {

    public ChatProfile extractStyleProfile(List<Message> conversation) {
        ChatProfile profile = new ChatProfile();

        profile.setHumorLevel(calculateHumorLevel(conversation));
        profile.setFormalityLevel(calculateFormality(conversation));
        profile.setEmpathyLevel(calculateEmpathy(conversation));
        profile.setPreferredOpening(extractOpeningPattern(conversation));
        profile.setResponseTimeAverage(calculateAvgResponseTime(conversation));
        profile.setMessageLengthAverage(calculateAvgMessageLength(conversation));

        return profile;
    }

    private double calculateHumorLevel(List<Message> conversation) {
        if (conversation.isEmpty()) return 0.5;

        long jokeCount = conversation.stream()
                .filter(msg -> msg.getContent() != null)
                .filter(msg -> {
                    String content = msg.getContent().toLowerCase();
                    return content.contains("😂") || content.contains("😆") ||
                            content.contains("😊") || content.contains("🙂") ||
                            content.contains("haha") || content.contains("lol") ||
                            content.contains("lmao") || content.contains("hehe") ||
                            content.contains("joke") || content.contains("funny") ||
                            content.contains("😂") || content.contains("😅");
                })
                .count();

        return Math.min(1.0, (double) jokeCount / conversation.size() * 3); // Scale factor
    }

    private double calculateFormality(List<Message> conversation) {
        if (conversation.isEmpty()) return 0.5;

        long formalElements = conversation.stream()
                .filter(msg -> msg.getContent() != null)
                .filter(msg -> {
                    String content = msg.getContent().toLowerCase();
                    // Formal indicators
                    boolean hasFormalGreeting = content.matches("(?i).*(hello|hi|hey|greetings|good morning|good afternoon).*");
                    boolean hasPleaseThankYou = content.contains("please") || content.contains("thank you") || content.contains("thanks");
                    boolean hasFullWords = !content.contains("u ") && !content.contains("ur ") && !content.contains("r ") && !content.contains("plz");
                    boolean hasPunctuation = content.matches(".*[.!?]$");

                    return hasFormalGreeting || hasPleaseThankYou || hasFullWords || hasPunctuation;
                })
                .count();

        long informalElements = conversation.stream()
                .filter(msg -> msg.getContent() != null)
                .filter(msg -> {
                    String content = msg.getContent().toLowerCase();
                    // Informal indicators
                    boolean hasSlang = content.matches("(?i).*(yo|sup|wassup|hey there|what's up).*");
                    boolean hasAbbreviations = content.contains("u ") || content.contains("ur ") || content.contains("r ") || content.contains("plz");
                    boolean hasEmojis = content.matches(".*[😂😊😎🤣😜].*");
                    boolean hasExcessivePunctuation = content.matches(".*[!?]{2,}.*");

                    return hasSlang || hasAbbreviations || hasEmojis || hasExcessivePunctuation;
                })
                .count();

        double formalityScore = (double) formalElements / (formalElements + informalElements + 1);
        return Math.max(0.1, Math.min(0.9, formalityScore));
    }

    private double calculateEmpathy(List<Message> conversation) {
        if (conversation.isEmpty()) return 0.5;

        long empatheticMessages = conversation.stream()
                .filter(msg -> msg.getContent() != null)
                .filter(msg -> {
                    String content = msg.getContent().toLowerCase();
                    return content.matches("(?i).*(sorry|understand|feel|hope|wish|care|support|help|listen).*") ||
                            content.matches(".*[❤️🤗🙏].*") ||
                            content.contains("how are you") || content.contains("how do you feel") ||
                            content.contains("that must be") || content.contains("i can imagine");
                })
                .count();

        return Math.min(1.0, (double) empatheticMessages / conversation.size() * 2);
    }

    private String extractOpeningPattern(List<Message> conversation) {
        if (conversation.isEmpty()) return "Hi there!";

        // Get the first few messages to analyze opening patterns
        List<Message> openingMessages = conversation.stream()
                .limit(Math.min(5, conversation.size()))
                .toList();

        Optional<Message> firstUserMessage = openingMessages.stream()
                .filter(Message::isFromUser)
                .findFirst();

        if (firstUserMessage.isPresent()) {
            String content = firstUserMessage.get().getContent();
            // Extract the essence of the opening
            if (content.toLowerCase().contains("hello") || content.toLowerCase().contains("hi")) {
                return "Friendly greeting";
            } else if (content.toLowerCase().contains("hey") || content.toLowerCase().contains("yo")) {
                return "Casual greeting";
            } else if (content.toLowerCase().contains("how are you")) {
                return "Inquisitive greeting";
            } else if (content.matches(".*[😂😊].*")) {
                return "Playful greeting";
            }
        }

        return "Standard greeting";
    }

    private double calculateAvgResponseTime(List<Message> conversation) {
        if (conversation.size() < 2) return 60.0; // Default 1 minute

        double totalResponseTime = 0;
        int responseCount = 0;

        for (int i = 1; i < conversation.size(); i++) {
            Message current = conversation.get(i);
            Message previous = conversation.get(i - 1);

            // Only calculate if different senders (actual response)
            if (!current.getSender().equals(previous.getSender())) {
                Duration duration = Duration.between(previous.getTimestamp(), current.getTimestamp());
                totalResponseTime += duration.getSeconds();
                responseCount++;
            }
        }

        return responseCount > 0 ? totalResponseTime / responseCount : 60.0;
    }

    private double calculateAvgMessageLength(List<Message> conversation) {
        if (conversation.isEmpty()) return 20.0; // Default average length

        double totalLength = conversation.stream()
                .filter(msg -> msg.getContent() != null)
                .mapToInt(msg -> msg.getContent().length())
                .sum();

        return totalLength / conversation.size();
    }

    // Additional style metrics
    private double calculateEngagementLevel(List<Message> conversation) {
        if (conversation.isEmpty()) return 0.5;

        long engagingMessages = conversation.stream()
                .filter(msg -> msg.getContent() != null)
                .filter(msg -> {
                    String content = msg.getContent();
                    return content.length() > 10 &&
                            !content.matches("(?i).*(ok|yes|no|maybe|k|cool|nice).*") &&
                            content.matches(".*[?].*"); // Contains questions
                })
                .count();

        return (double) engagingMessages / conversation.size();
    }

    private double calculateQuestionRate(List<Message> conversation) {
        if (conversation.isEmpty()) return 0.3;

        long questionMessages = conversation.stream()
                .filter(msg -> msg.getContent() != null)
                .filter(msg -> msg.getContent().contains("?"))
                .count();

        return (double) questionMessages / conversation.size();
    }
}