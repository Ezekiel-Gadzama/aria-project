// SuccessScorer.java
package com.aria.analysis;

import com.aria.ai.OpenAIClient;
import com.aria.core.model.Message;
import com.aria.core.model.ChatCategory;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Uses OpenAI to calculate success scores (0-100%) for conversations per category.
 * Determines if the conversation achieved the goal for each category it belongs to.
 */
public class SuccessScorer {
    private final OpenAIClient openAIClient;

    public SuccessScorer(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    /**
     * Calculate success score (0.0-1.0) for a conversation given a goal type
     * @param conversation The conversation messages
     * @param goalType The goal type (e.g., "Secure investment", "Arrange a date")
     * @return Success score from 0.0 (failed) to 1.0 (completely successful)
     */
    public double calculateSuccessScore(List<Message> conversation, String goalType) {
        if (conversation == null || conversation.isEmpty()) {
            return 0.0;
        }

        // Format conversation for OpenAI
        StringBuilder conversationText = new StringBuilder();
        for (Message msg : conversation) {
            String sender = msg.isFromUser() ? "You" : "Them";
            conversationText.append(sender).append(": ")
                          .append(msg.getContent() != null ? msg.getContent() : "").append("\n");
        }

        String prompt = String.format("""
            Analyze the following conversation and determine if the goal was achieved.
            
            Goal: %s
            
            Conversation:
            %s
            
            Rate the success of this conversation on a scale of 0 to 100, where:
            - 0-30: Goal was not achieved (conversation failed or didn't progress)
            - 31-69: Goal was partially achieved (some progress but not complete)
            - 70-100: Goal was achieved (e.g., date happened, meeting scheduled, investment secured)
            
            Consider:
            1. Did the conversation achieve the stated goal?
            2. Was there clear evidence of the outcome (e.g., "Let's meet on Friday", "I'll invest $50k")?
            3. Did the conversation show positive progression toward the goal?
            
            Return a JSON response with this format:
            {
                "success_score": 85,
                "reason": "The conversation resulted in a date being scheduled for Friday evening"
            }
            
            Return JSON only, no additional text:""",
            goalType, conversationText.toString());

        try {
            String response = openAIClient.generateResponse(prompt);
            return parseSuccessScore(response);
        } catch (Exception e) {
            System.err.println("Error calculating success score: " + e.getMessage());
            // Fallback to engagement-based scoring
            return calculateEngagementScore(conversation);
        }
    }

    /**
     * Calculate success scores for each category a chat belongs to
     * @param conversation The conversation messages
     * @param categories List of category names this chat belongs to
     * @return Map of category name to success score (0.0-1.0)
     */
    public Map<String, Double> calculateSuccessScoresPerCategory(
            List<Message> conversation, List<String> categories) {
        Map<String, Double> scores = new HashMap<>();

        if (conversation == null || conversation.isEmpty() || categories == null || categories.isEmpty()) {
            return scores;
        }

        // Format conversation for OpenAI
        StringBuilder conversationText = new StringBuilder();
        for (Message msg : conversation) {
            String sender = msg.isFromUser() ? "You" : "Them";
            conversationText.append(sender).append(": ")
                          .append(msg.getContent() != null ? msg.getContent() : "").append("\n");
        }

        // Build category descriptions
        StringBuilder categoryDescriptions = new StringBuilder();
        for (String categoryName : categories) {
            ChatCategory category = ChatCategory.fromName(categoryName);
            if (category != null) {
                categoryDescriptions.append("- ").append(category.getName())
                    .append(": ").append(category.getDescription()).append("\n");
            }
        }

        String prompt = String.format("""
            Analyze the following conversation and rate its success for each category.
            
            Categories:
            %s
            
            Conversation:
            %s
            
            For each category, rate the success from 0 to 100, where:
            - 0-30: Goal was not achieved for this category (failed)
            - 31-69: Goal was partially achieved (some progress)
            - 70-100: Goal was achieved (e.g., for dating: date happened; for investment: investment secured)
            
            Return a JSON response with this format:
            {
                "category_scores": [
                    {"category": "dating", "score": 85, "reason": "A date was scheduled for Friday"},
                    {"category": "flirting", "score": 70, "reason": "Flirtatious conversation led to romantic interest"}
                ]
            }
            
            Return JSON only, no additional text:""",
            categoryDescriptions.toString(), conversationText.toString());

        try {
            String response = openAIClient.generateResponse(prompt);
            return parseCategorySuccessScores(response, categories);
        } catch (Exception e) {
            System.err.println("Error calculating category success scores: " + e.getMessage());
            // Fallback: return 0.5 for all categories
            for (String category : categories) {
                scores.put(category, 0.5);
            }
            return scores;
        }
    }

    private double parseSuccessScore(String response) {
        try {
            String cleanResponse = response.trim();
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7);
            }
            if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.substring(3);
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
            }
            cleanResponse = cleanResponse.trim();

            JSONObject jsonResponse = new JSONObject(cleanResponse);
            int score = jsonResponse.getInt("success_score");
            // Convert 0-100 to 0.0-1.0
            return Math.max(0.0, Math.min(1.0, score / 100.0));
        } catch (Exception e) {
            System.err.println("Error parsing success score: " + e.getMessage());
            return 0.5; // Default to 50%
        }
    }

    private Map<String, Double> parseCategorySuccessScores(String response, List<String> validCategories) {
        Map<String, Double> scores = new HashMap<>();

        try {
            String cleanResponse = response.trim();
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7);
            }
            if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.substring(3);
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
            }
            cleanResponse = cleanResponse.trim();

            JSONObject jsonResponse = new JSONObject(cleanResponse);
            JSONArray categoryScores = jsonResponse.getJSONArray("category_scores");

            for (int i = 0; i < categoryScores.length(); i++) {
                JSONObject item = categoryScores.getJSONObject(i);
                String category = item.getString("category").toLowerCase().trim();
                int score = item.getInt("score");
                
                // Match to valid category
                ChatCategory matchedCategory = ChatCategory.fromName(category);
                if (matchedCategory != null && validCategories.contains(matchedCategory.getName())) {
                    // Convert 0-100 to 0.0-1.0
                    scores.put(matchedCategory.getName(), Math.max(0.0, Math.min(1.0, score / 100.0)));
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing category success scores: " + e.getMessage());
        }

        // Fill missing categories with 0.5
        for (String category : validCategories) {
            if (!scores.containsKey(category)) {
                scores.put(category, 0.5);
            }
        }

        return scores;
    }

    private double calculateEngagementScore(List<Message> conversation) {
        // Fallback engagement-based scoring
        if (conversation.isEmpty()) {
            return 0.0;
        }

        // Calculate based on message length, response time, etc.
        long totalChars = conversation.stream()
            .filter(msg -> msg.getContent() != null)
            .mapToLong(msg -> msg.getContent().length())
            .sum();

        double avgMessageLength = totalChars / (double) conversation.size();
        
        // Normalize: 0-50 chars = low, 50-150 = medium, 150+ = high
        if (avgMessageLength < 50) {
            return 0.3;
        } else if (avgMessageLength < 150) {
            return 0.6;
        } else {
            return 0.8;
        }
    }
}