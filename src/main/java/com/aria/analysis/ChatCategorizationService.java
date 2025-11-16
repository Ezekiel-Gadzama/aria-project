package com.aria.analysis;

import com.aria.ai.OpenAIClient;
import com.aria.core.model.ChatCategory;
import com.aria.core.model.Message;
import com.aria.core.model.OutcomeType;
import com.aria.storage.DatabaseSchema;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service that uses OpenAI API to categorize chats into multiple goals/categories.
 * Uses enum-based categories with descriptions to ensure consistent naming and better categorization.
 */
public class ChatCategorizationService {
    private final OpenAIClient openAIClient;

    public ChatCategorizationService(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    /**
     * Get all available categories from enum (ensures consistency)
     */
    public List<String> getAllCategories() {
        return ChatCategory.getAllNames();
    }

    /**
     * Get category enum values
     */
    public List<ChatCategory> getAllCategoryEnums() {
        return Arrays.asList(ChatCategory.values());
    }

    /**
     * Categorize a single chat/dialog using OpenAI API and calculate success scores per category
     * This combines categorization and success scoring in one OpenAI call for efficiency
     * @param dialogId Database ID of the dialog
     * @param messages List of messages in the dialog
     * @return Map of category name to CategoryScore (contains relevance and success score)
     */
    public Map<String, CategoryScore> categorizeChatWithScores(int dialogId, List<Message> messages) throws SQLException {
        List<String> availableCategories = getAllCategories();
        
        if (messages == null || messages.isEmpty()) {
            return new HashMap<>();
        }

        // Format messages for OpenAI
        StringBuilder chatText = new StringBuilder();
        for (Message msg : messages) {
            String sender = msg.isFromUser() ? "You" : msg.getSender();
            chatText.append(sender).append(": ").append(msg.getContent() != null ? msg.getContent() : "").append("\n");
        }

        // Build prompt that asks for both categorization AND success scoring
        String prompt = buildCategorizationWithScoresPrompt(chatText.toString(), availableCategories);

        // Get categorization and success scores from OpenAI
        String response = openAIClient.generateResponse(prompt);
        
        // Parse response to extract categories, relevance scores, and success scores
        Map<String, CategoryScore> categoryScores = response != null
                ? parseCategorizationWithScoresResponse(response, availableCategories)
                : new HashMap<>();

        // Fallback: if parsing failed or AI unavailable, assign a safe default
        if (categoryScores.isEmpty()) {
            String fallback = getFallbackCategoryName();
            categoryScores.put(fallback, new CategoryScore(0.5, 0.5, OutcomeType.NEUTRAL.getName(), "Fallback due to AI unavailability"));
        }

        // Save categorization and success scores to database
        saveChatCategorizationWithScores(dialogId, categoryScores);

        return categoryScores;
    }

    /**
     * Categorize a single chat/dialog using OpenAI API (backward compatibility)
     * @param dialogId Database ID of the dialog
     * @param messages List of messages in the dialog
     * @return Map of category name to relevance score
     */
    public Map<String, Double> categorizeChat(int dialogId, List<Message> messages) throws SQLException {
        Map<String, CategoryScore> scores = categorizeChatWithScores(dialogId, messages);
        Map<String, Double> relevanceScores = new HashMap<>();
        for (Map.Entry<String, CategoryScore> entry : scores.entrySet()) {
            relevanceScores.put(entry.getKey(), entry.getValue().relevanceScore);
        }
        return relevanceScores;
    }

    /**
     * Categorize chats based on a goal description and meeting context
     * Uses OpenAI to determine which categories are relevant
     */
    public List<String> getRelevantCategories(String goalDescription, String meetingContext) {
        String categoryList = ChatCategory.formatForOpenAI();
        
        String prompt = String.format("""
            Given the following goal and meeting context, determine which categories from the list are relevant.
            
            Goal Description: %s
            Meeting Context: %s
            
            %s
            
            IMPORTANT: You MUST use the EXACT category names from the list above. Do not create variations.
            
            Instructions:
            1. Read each category's description and keywords
            2. Select categories that are relevant based on:
               - The goal could involve conversations related to that category
               - The meeting context suggests activities or topics in that category
               - The category could help inform conversation style or strategy
            3. Return ONLY a JSON array of EXACT category names (strings), sorted by relevance
            4. Use only lowercase category names exactly as they appear in the list
            
            Example: ["dating", "flirting", "romance"]
            
            Return JSON only, no additional text:""",
            goalDescription, meetingContext, categoryList);

        String response = openAIClient.generateResponse(prompt);
        List<String> allCategoryNames = getAllCategories();
        return parseCategoryListResponse(response, allCategoryNames);
    }

    /**
     * Get all chats that belong to specific categories
     * Filters out groups, channels, and bots
     * Returns deduplicated results
     */
    public List<ChatCategoryResult> getChatsByCategories(List<String> categories, int userId) throws SQLException {
        if (categories == null || categories.isEmpty()) {
            return new ArrayList<>();
        }

        String placeholders = String.join(",", Collections.nCopies(categories.size(), "?"));
        String sql = String.format("""
            SELECT DISTINCT
                d.id as dialog_id,
                d.dialog_id,
                d.name as dialog_name,
                d.type as dialog_type,
                cg.category_name,
                cg.relevance_score,
                cg.success_score,
                cg.outcome
            FROM dialogs d
            INNER JOIN chat_goals cg ON d.id = cg.dialog_id
            WHERE d.user_id = ?
                AND d.type NOT IN ('group', 'channel', 'supergroup', 'bot')
                AND cg.category_name IN (%s)
            ORDER BY cg.relevance_score DESC, cg.success_score DESC
            """, placeholders);

        // Use Set to deduplicate by dialog_id
        Map<Integer, ChatCategoryResult> resultMap = new LinkedHashMap<>();
        
        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            for (int i = 0; i < categories.size(); i++) {
                pstmt.setString(i + 2, categories.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int dialogId = rs.getInt("dialog_id");
                    
                    // If we already have this dialog, skip (deduplication)
                    // or update if this category has higher relevance
                    if (!resultMap.containsKey(dialogId)) {
                        ChatCategoryResult result = new ChatCategoryResult();
                        result.dialogId = dialogId;
                        result.platformDialogId = rs.getLong("dialog_id");
                        result.dialogName = rs.getString("dialog_name");
                        result.dialogType = rs.getString("dialog_type");
                        result.categoryName = rs.getString("category_name");
                        result.relevanceScore = rs.getDouble("relevance_score");
                        result.successScore = rs.getDouble("success_score");
                        result.outcome = rs.getString("outcome");
                        resultMap.put(dialogId, result);
                    } else {
                        // Update if this category has higher relevance
                        ChatCategoryResult existing = resultMap.get(dialogId);
                        double newRelevance = rs.getDouble("relevance_score");
                        if (newRelevance > existing.relevanceScore) {
                            existing.relevanceScore = newRelevance;
                            existing.categoryName = rs.getString("category_name");
                        }
                    }
                }
            }
        }

        return new ArrayList<>(resultMap.values());
    }

    /**
     * Get chats with smart AND filtering to manage conversation length
     * Uses SmartChatSelector for progressive filtering
     */
    public Map<Integer, List<Message>> getSmartFilteredChats(
            List<String> categories, 
            int userId, 
            int maxChats) throws SQLException {
        
        SmartChatSelector selector = new SmartChatSelector();
        return selector.getFilteredChats(categories, userId, maxChats);
    }

    private String buildCategorizationPrompt(String chatText, List<String> categories) {
        // Build detailed category list with descriptions
        String categoryList = ChatCategory.formatForOpenAI();
        
        return String.format("""
            Analyze the following chat conversation and categorize it into one or more of these EXACT categories.
            
            IMPORTANT: You MUST use the EXACT category names provided below. Do not create variations or similar names.
            
            %s
            
            Chat Conversation:
            %s
            
            Instructions:
            1. Match the conversation to one or more categories using the EXACT category names provided
            2. Use the category descriptions and keywords to determine relevance
            3. Provide a relevance score between 0.0 and 1.0 for each relevant category
            4. Only include categories with relevance >= 0.3
            
            Return your response as a JSON object with this format:
            {
                "categories": [
                    {"name": "EXACT_CATEGORY_NAME", "relevance": 0.85, "reason": "brief explanation"},
                    ...
                ]
            }
            
            Remember: Use ONLY the exact category names from the list above. Return JSON only, no additional text:""",
            categoryList, chatText);
    }

    /**
     * Build prompt that asks for both categorization AND success scoring in one call
     * Includes enhanced logic to understand contextual rejections vs. approach failures
     */
    private String buildCategorizationWithScoresPrompt(String chatText, List<String> categories) {
        String categoryList = ChatCategory.formatForOpenAI();
        String outcomeTypes = OutcomeType.formatForOpenAI();
        
        return String.format("""
            Analyze the following chat conversation and:
            1. Categorize it into one or more EXACT categories
            2. For each category, rate the success (0-100) of achieving that category's goal
            
            IMPORTANT: You MUST use the EXACT category names provided below.
            
            %s
            
            Chat Conversation:
            %s
            
            For each relevant category, provide:
            
            **Relevance Score (0.0-1.0)**: How well does this conversation match the category?
            
            **Success Score (0-100)**: Evaluate success based on THREE key factors:
            
            1. **Communication Quality (30%% weight)**:
               - Was the approach respectful, appropriate, and well-crafted?
               - Did it demonstrate good social/communication skills?
               - Was the message clear, engaging, and professional?
            
            2. **Engagement Level (30%% weight)**:
               - Did the person respond thoughtfully (even if negative)?
               - Were responses substantial and engaging, or dismissive?
               - Was there genuine interaction or just rejection?
            
            3. **Contextual Understanding (40%% weight)**:
               - **HIGH SCORE (70-100)**: Rejection due to circumstances, not approach
                 * Examples: "I have a boyfriend, but you seem really nice!"
                 * "Not enough funds now, but let's revisit in Q2"
                 * "Can't help now, but connect me on LinkedIn"
                 * These show GOOD approach but external factors
               
               - **MEDIUM SCORE (40-69)**: Partial success or unclear outcome
                 * Examples: "Maybe later", "I'll think about it", "Need time"
               
               - **LOW SCORE (0-39)**: Poor approach or genuine disinterest
                 * Examples: "You're weird", "Leave me alone", "Not interested"
                 * These show the approach itself was the problem
            
            **Key Principle**: A thoughtful rejection with positive feedback should score HIGH
            because it indicates the communication was effective, even if the outcome wasn't ideal.
            
            **Outcome Types** (use exact names):
            %s
            
            Return your response as a JSON object with this format:
            {
                "categories": [
                    {
                        "name": "EXACT_CATEGORY_NAME",
                        "relevance": 0.85,
                        "success_score": 75,
                        "outcome_type": "circumstantial_rejection",
                        "reason": "brief explanation of scoring rationale"
                    },
                    ...
                ]
            }
            
            Remember: Use ONLY the exact category names from the list above. Return JSON only, no additional text:""",
            categoryList, outcomeTypes, chatText);
    }

    private Map<String, Double> parseCategorizationResponse(String response, List<String> validCategories) {
        Map<String, Double> categoryScores = new HashMap<>();
        
        try {
            if (response == null) throw new IllegalArgumentException("null response");
            // Clean response - remove markdown code blocks if present
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
            JSONArray categories = jsonResponse.getJSONArray("categories");

            for (int i = 0; i < categories.length(); i++) {
                JSONObject cat = categories.getJSONObject(i);
                String name = cat.getString("name").toLowerCase().trim();
                double relevance = cat.getDouble("relevance");

                // Try exact match first
                if (validCategories.contains(name)) {
                    categoryScores.put(name, relevance);
                } else {
                    // Try to find matching enum category (handles variations)
                    ChatCategory matchedCategory = ChatCategory.fromName(name);
                    if (matchedCategory != null && validCategories.contains(matchedCategory.getName())) {
                        categoryScores.put(matchedCategory.getName(), relevance);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing categorization response: " + e.getMessage());
            System.err.println("Response was: " + response);
            // Fallback: try to match categories using enum
            for (ChatCategory category : ChatCategory.values()) {
                String categoryName = category.getName();
                if (response.toLowerCase().contains(categoryName.toLowerCase())) {
                    // Check if any keywords match
                    for (String keyword : category.getKeywords()) {
                        if (response.toLowerCase().contains(keyword.toLowerCase())) {
                            categoryScores.put(categoryName, 0.5); // Default score
                            break;
                        }
                    }
                }
            }
            if (categoryScores.isEmpty()) {
                String fallback = getFallbackCategoryName();
                categoryScores.put(fallback, 0.5);
            }
        }

        return categoryScores;
    }

    private List<String> parseCategoryListResponse(String response, List<String> validCategories) {
        List<String> categories = new ArrayList<>();
        
        try {
            if (response == null) throw new IllegalArgumentException("null response");
            // Clean response
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

            JSONArray jsonArray = new JSONArray(cleanResponse);
            for (int i = 0; i < jsonArray.length(); i++) {
                String categoryName = jsonArray.getString(i).toLowerCase().trim();
                
                // Try exact match first
                if (validCategories.contains(categoryName)) {
                    categories.add(categoryName);
                } else {
                    // Try to match using enum (handles variations)
                    ChatCategory matchedCategory = ChatCategory.fromName(categoryName);
                    if (matchedCategory != null && validCategories.contains(matchedCategory.getName())) {
                        String exactName = matchedCategory.getName();
                        if (!categories.contains(exactName)) {
                            categories.add(exactName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing category list: " + e.getMessage());
            System.err.println("Response was: " + response);
            // Fallback: try to match using enum keywords
            for (ChatCategory category : ChatCategory.values()) {
                String categoryName = category.getName();
                if (response.toLowerCase().contains(categoryName.toLowerCase())) {
                    // Check if any keywords match
                    for (String keyword : category.getKeywords()) {
                        if (response.toLowerCase().contains(keyword.toLowerCase())) {
                            if (!categories.contains(categoryName)) {
                                categories.add(categoryName);
                            }
                            break;
                        }
                    }
                }
            }
            if (categories.isEmpty()) {
                categories.add(getFallbackCategoryName());
            }
        }

        return categories;
    }

    // Note: legacy saveChatCategorization(dialogId, Map<String,Double>) has been removed.
    // We now persist via saveChatCategorizationWithScores(...) and saveChatCategorizationWithScoresMerge(...)

    /**
     * Parse response that includes both relevance and success scores
     */
    private Map<String, CategoryScore> parseCategorizationWithScoresResponse(String response, List<String> validCategories) {
        Map<String, CategoryScore> categoryScores = new HashMap<>();
        
        try {
            if (response == null) throw new IllegalArgumentException("null response");
            // Clean response
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
            JSONArray categories = jsonResponse.getJSONArray("categories");

            for (int i = 0; i < categories.length(); i++) {
                JSONObject cat = categories.getJSONObject(i);
                String name = cat.getString("name").toLowerCase().trim();
                double relevance = cat.getDouble("relevance");
                int successScoreInt = cat.optInt("success_score", 50); // Default to 50 if missing
                double successScore = successScoreInt / 100.0; // Convert 0-100 to 0.0-1.0
                
                // Get outcome type if provided (for enhanced understanding)
                String outcomeTypeStr = cat.optString("outcome_type", "");
                String reason = cat.optString("reason", "");
                
                // Parse outcome type using enum
                OutcomeType outcomeType = OutcomeType.fromName(outcomeTypeStr);
                String outcomeTypeName = outcomeType != null ? outcomeType.getName() : outcomeTypeStr;

                // Try exact match first
                String matchedCategory = null;
                if (validCategories.contains(name)) {
                    matchedCategory = name;
                } else {
                    // Try to find matching enum category
                    ChatCategory matched = ChatCategory.fromName(name);
                    if (matched != null && validCategories.contains(matched.getName())) {
                        matchedCategory = matched.getName();
                    }
                }

                if (matchedCategory != null) {
                    categoryScores.put(matchedCategory, new CategoryScore(relevance, successScore, outcomeTypeName, reason));
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing categorization with scores response: " + e.getMessage());
            System.err.println("Response was: " + response);
            // Final safety fallback: choose a random valid category to avoid empty result
            String fallback = getFallbackCategoryName();
            categoryScores.put(fallback, new CategoryScore(0.5, 0.5, OutcomeType.NEUTRAL.getName(), "Fallback due to parsing error"));
        }

        return categoryScores;
    }

    /**
     * Save categorization with both relevance and success scores to database
     */
    private void saveChatCategorizationWithScores(int dialogId, Map<String, CategoryScore> categoryScores) throws SQLException {
        String sql = """
            INSERT INTO chat_goals (dialog_id, category_name, relevance_score, success_score, outcome, categorized_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            ON CONFLICT (dialog_id, category_name) 
            DO UPDATE SET 
                relevance_score = EXCLUDED.relevance_score,
                success_score = EXCLUDED.success_score,
                outcome = EXCLUDED.outcome,
                categorized_at = EXCLUDED.categorized_at
            """;

        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, CategoryScore> entry : categoryScores.entrySet()) {
                CategoryScore score = entry.getValue();
                // Store outcome type and reason in outcome field as JSON or formatted string
                String outcomeInfo = score.outcomeType.isEmpty() ? score.reason : 
                    String.format("%s: %s", score.outcomeType, score.reason);
                
                pstmt.setInt(1, dialogId);
                pstmt.setString(2, entry.getKey());
                pstmt.setDouble(3, score.relevanceScore);
                pstmt.setDouble(4, score.successScore);
                pstmt.setString(5, (outcomeInfo == null || outcomeInfo.isEmpty()) ? "NEUTRAL: Fallback" : outcomeInfo);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    /**
     * Helper class to store both relevance and success scores, plus outcome context
     */
    public static class CategoryScore {
        public final double relevanceScore;
        public final double successScore;
        public final String outcomeType; // circumstantial_rejection, approach_rejection, success, etc.
        public final String reason;

        public CategoryScore(double relevanceScore, double successScore) {
            this(relevanceScore, successScore, "", "");
        }

        public CategoryScore(double relevanceScore, double successScore, String outcomeType, String reason) {
            this.relevanceScore = relevanceScore;
            this.successScore = successScore;
            this.outcomeType = outcomeType != null ? outcomeType : "";
            this.reason = reason != null ? reason : "";
        }
    }

    /**
     * Categorize all dialogs for a user (used after ingestion)
     * This will categorize dialogs that haven't been categorized yet, or re-categorize
     * dialogs that have new messages (incremental update)
     */
    public void categorizeAllDialogs(int userId) throws SQLException {
        // Get all dialogs for the user that need categorization
        String sql = """
            SELECT DISTINCT d.id as dialog_id
            FROM dialogs d
            WHERE d.user_id = ?
              AND d.type NOT IN ('group', 'channel', 'supergroup', 'bot')
              AND (d.is_bot IS NULL OR d.is_bot = FALSE)
            ORDER BY d.id
            """;

        List<Integer> dialogIds = new ArrayList<>();
        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    dialogIds.add(rs.getInt("dialog_id"));
                }
            }
        }

        System.out.println("Categorizing " + dialogIds.size() + " dialogs...");
        
        for (int dialogId : dialogIds) {
            try {
                // Check if dialog has been categorized before
                LocalDateTime lastCategorizedAt = getLastCategorizationTimestamp(dialogId);
                
                if (lastCategorizedAt != null) {
                    // Use incremental re-categorization (only new messages)
                    recategorizeDialog(dialogId);
                } else {
                    // First-time categorization (all messages)
                    List<Message> messages = loadMessagesForDialog(dialogId);
                    
                    if (messages.isEmpty()) {
                        continue;
                    }

                    // Categorize with scores (this saves to database)
                    categorizeChatWithScores(dialogId, messages);
                    System.out.println("Categorized dialog " + dialogId + " (" + messages.size() + " messages)");
                }
                
            } catch (Exception e) {
                System.err.println("Error categorizing dialog " + dialogId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Categorization complete!");
    }

    /**
     * Re-categorize a dialog that has new messages (incremental update)
     * This will:
     * 1. Load only new messages (after last categorization timestamp)
     * 2. Send only new messages to OpenAI for categorization
     * 3. Merge new categories with existing ones (only add new, don't overwrite)
     * 4. Update existing category scores using smart merging formula
     */
    public void recategorizeDialog(int dialogId) throws SQLException {
        // Get existing categories from database
        Map<String, CategoryScore> existingCategories = getExistingCategories(dialogId);
        
        // Get last categorization timestamp
        LocalDateTime lastCategorizedAt = getLastCategorizationTimestamp(dialogId);
        
        // Load only new messages (after last categorization)
        List<Message> newMessages = loadNewMessagesForDialog(dialogId, lastCategorizedAt);
        
        if (newMessages.isEmpty()) {
            System.out.println("No new messages for dialog " + dialogId);
            return;
        }
        
        // Load ALL messages for engagement metrics calculation
        List<Message> allMessages = loadMessagesForDialog(dialogId);
        
        // Categorize only new messages
        Map<String, CategoryScore> newCategoryScores = categorizeMessagesWithScores(newMessages);
        
        // Merge categories intelligently:
        // 1. Add new categories that don't exist
        // 2. Update existing categories using smart score merging
        // Pass both old and new message counts for proper weighting
        int oldMessageCount = allMessages.size() - newMessages.size();
        mergeCategoryScores(dialogId, existingCategories, newCategoryScores, allMessages, newMessages.size(), oldMessageCount);
        
        System.out.println("Re-categorized dialog " + dialogId + " with " + newMessages.size() + " new messages");
    }
    
    /**
     * Get existing categories from database for a dialog
     */
    private Map<String, CategoryScore> getExistingCategories(int dialogId) throws SQLException {
        Map<String, CategoryScore> categories = new HashMap<>();
        
        String sql = """
            SELECT category_name, relevance_score, success_score, outcome
            FROM chat_goals
            WHERE dialog_id = ?
            """;
        
        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, dialogId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String categoryName = rs.getString("category_name");
                    double relevance = rs.getDouble("relevance_score");
                    double success = rs.getDouble("success_score");
                    String outcome = rs.getString("outcome");
                    
                    // Parse outcome to extract outcome_type and reason if available
                    String outcomeTypeStr = "";
                    String reason = "";
                    if (outcome != null && !outcome.isEmpty()) {
                        if (outcome.contains(": ")) {
                            String[] parts = outcome.split(": ", 2);
                            outcomeTypeStr = parts[0];
                            reason = parts.length > 1 ? parts[1] : "";
                        } else {
                            reason = outcome;
                        }
                    }
                    
                    // Validate outcome type using enum
                    OutcomeType outcomeType = OutcomeType.fromName(outcomeTypeStr);
                    String finalOutcomeType = outcomeType != null ? outcomeType.getName() : outcomeTypeStr;
                    
                    categories.put(categoryName, new CategoryScore(relevance, success, finalOutcomeType, reason));
                }
            }
        }
        
        return categories;
    }
    
    /**
     * Get last categorization timestamp for a dialog
     */
    private LocalDateTime getLastCategorizationTimestamp(int dialogId) throws SQLException {
        String sql = """
            SELECT MAX(categorized_at) as last_categorized
            FROM chat_goals
            WHERE dialog_id = ?
            """;
        
        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, dialogId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    java.sql.Timestamp timestamp = rs.getTimestamp("last_categorized");
                    if (timestamp != null && !rs.wasNull()) {
                        return timestamp.toLocalDateTime();
                    }
                }
            }
        }
        
        return null; // Never categorized before
    }
    
    /**
     * Load only new messages (after last categorization timestamp)
     */
    private List<Message> loadNewMessagesForDialog(int dialogId, LocalDateTime afterTimestamp) throws SQLException {
        List<Message> messages = new ArrayList<>();
        
        String sql;
        PreparedStatement pstmt;
        
        if (afterTimestamp != null) {
            sql = """
                SELECT message_id, sender, text, timestamp, has_media
                FROM messages
                WHERE dialog_id = ? AND timestamp > ?
                ORDER BY timestamp ASC
                """;
        } else {
            // No previous categorization, return all messages
            return loadMessagesForDialog(dialogId);
        }
        
        try (Connection conn = DatabaseSchema.getConnectionInstance()) {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, dialogId);
            if (afterTimestamp != null) {
                pstmt.setTimestamp(2, java.sql.Timestamp.valueOf(afterTimestamp));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Message msg = new Message();
                    msg.setId(rs.getInt("message_id"));
                    msg.setSender(rs.getString("sender"));
                    msg.setContent(rs.getString("text"));
                    
                    java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");
                    if (timestamp != null) {
                        msg.setTimestamp(timestamp.toLocalDateTime());
                    }
                    
                    msg.setFromUser("me".equalsIgnoreCase(msg.getSender()) || "You".equalsIgnoreCase(msg.getSender()));
                    msg.setHasMedia(rs.getBoolean("has_media"));
                    
                    messages.add(msg);
                }
            }
        }
        
        return messages;
    }
    
    /**
     * Categorize messages without saving (used for incremental updates)
     */
    private Map<String, CategoryScore> categorizeMessagesWithScores(List<Message> messages) throws SQLException {
        List<String> availableCategories = getAllCategories();
        
        if (messages == null || messages.isEmpty()) {
            return new HashMap<>();
        }

        // Format messages for OpenAI
        StringBuilder chatText = new StringBuilder();
        for (Message msg : messages) {
            String sender = msg.isFromUser() ? "You" : msg.getSender();
            chatText.append(sender).append(": ").append(msg.getContent() != null ? msg.getContent() : "").append("\n");
        }

        // Build prompt that asks for both categorization AND success scoring
        String prompt = buildCategorizationWithScoresPrompt(chatText.toString(), availableCategories);

        // Get categorization and success scores from OpenAI
        String response = openAIClient.generateResponse(prompt);
        
        // Parse response to extract categories, relevance scores, and success scores
        Map<String, CategoryScore> scores = response != null
                ? parseCategorizationWithScoresResponse(response, availableCategories)
                : new HashMap<>();
        if (scores.isEmpty()) {
            String fallback = getFallbackCategoryName();
            scores.put(fallback, new CategoryScore(0.5, 0.5, OutcomeType.NEUTRAL.getName(), "Fallback due to AI unavailability"));
        }
        return scores;
    }
    
    /**
     * Merge new category scores with existing ones intelligently
     * - Adds new categories that don't exist
     * - Updates existing categories using smart score merging formula
     * 
     * @param newMessageCount Number of new messages (for weighting calculation)
     * @param oldMessageCount Number of old messages (for weighting calculation)
     */
    private void mergeCategoryScores(int dialogId, 
                                     Map<String, CategoryScore> existingCategories,
                                     Map<String, CategoryScore> newCategoryScores,
                                     List<Message> allMessages,
                                     int newMessageCount,
                                     int oldMessageCount) throws SQLException {
        
        // Calculate engagement metrics for smart score merging
        EngagementMetrics metrics = calculateEngagementMetrics(allMessages);
        
        Map<String, CategoryScore> mergedScores = new HashMap<>();
        
        // Process each new category from OpenAI
        for (Map.Entry<String, CategoryScore> entry : newCategoryScores.entrySet()) {
            String categoryName = entry.getKey();
            CategoryScore newScore = entry.getValue();
            
            if (existingCategories.containsKey(categoryName)) {
                // Category exists - merge scores intelligently
                CategoryScore existingScore = existingCategories.get(categoryName);
                CategoryScore mergedScore = mergeScoresIntelligently(
                    existingScore, newScore, metrics, newMessageCount, oldMessageCount);
                mergedScores.put(categoryName, mergedScore);
            } else {
                // New category - just add it
                mergedScores.put(categoryName, newScore);
            }
        }
        
        // Keep existing categories that weren't in new categorization (don't remove them)
        for (Map.Entry<String, CategoryScore> entry : existingCategories.entrySet()) {
            if (!mergedScores.containsKey(entry.getKey())) {
                mergedScores.put(entry.getKey(), entry.getValue());
            }
        }
        
        // Save merged scores to database
        saveChatCategorizationWithScoresMerge(dialogId, existingCategories, mergedScores);
    }
    
    /**
     * Calculate engagement metrics from conversation for smart score merging
     */
    private EngagementMetrics calculateEngagementMetrics(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new EngagementMetrics(0, 0, 1.0, 120.0, 0.0);
        }
        
        long userMessageCount = messages.stream()
            .filter(Message::isFromUser)
            .count();
        
        long otherMessageCount = messages.size() - userMessageCount;
        
        // Calculate reply ratio (how often the other person replies to user)
        double replyRatio = userMessageCount > 0 ? (double) otherMessageCount / userMessageCount : 0.0;
        
        // Calculate average reply time (how long before the other person replies)
        double avgReplyTime = calculateAverageReplyTime(messages);
        
        // Calculate engagement level (based on message length, questions, etc.)
        double engagementLevel = calculateEngagementLevel(messages);
        
        return new EngagementMetrics(userMessageCount, otherMessageCount, replyRatio, avgReplyTime, engagementLevel);
    }
    
    /**
     * Calculate average reply time (how long the other person takes to reply)
     */
    private double calculateAverageReplyTime(List<Message> messages) {
        if (messages.size() < 2) return 120.0; // Default 2 minutes
        
        long totalDelay = 0;
        int delayCount = 0;
        
        for (int i = 1; i < messages.size(); i++) {
            Message current = messages.get(i);
            Message previous = messages.get(i - 1);
            
            // Only consider when other person replies after user
            if (!current.isFromUser() && previous.isFromUser() &&
                current.getTimestamp() != null && previous.getTimestamp() != null) {
                
                long seconds = java.time.Duration.between(previous.getTimestamp(), current.getTimestamp()).getSeconds();
                if (seconds > 0 && seconds < 86400) { // Within 24 hours
                    totalDelay += seconds;
                    delayCount++;
                }
            }
        }
        
        return delayCount > 0 ? (double) totalDelay / delayCount : 120.0;
    }
    
    /**
     * Calculate engagement level based on message characteristics
     */
    private double calculateEngagementLevel(List<Message> messages) {
        if (messages.isEmpty()) return 0.5;
        
        long engagingMessages = messages.stream()
            .filter(msg -> msg.getContent() != null && !msg.isFromUser())
            .filter(msg -> {
                String content = msg.getContent();
                return content.length() > 20 && // Substantial message
                       !content.matches("(?i).*(ok|yes|no|maybe|k|cool|nice|thanks).*") && // Not just acknowledgment
                       (content.matches(".*[?].*") || content.length() > 50); // Questions or detailed responses
            })
            .count();
        
        long otherMessages = messages.stream().filter(msg -> !msg.isFromUser()).count();
        return otherMessages > 0 ? Math.min(1.0, (double) engagingMessages / otherMessages) : 0.5;
    }
    
    /**
     * Intelligently merge old and new scores based on engagement metrics and message count ratio
     * 
     * @param oldScore The existing score from previous categorization
     * @param newScore The new score from recent messages
     * @param metrics Engagement metrics calculated from all messages
     * @param newMessageCount Number of new messages being categorized
     * @param oldMessageCount Number of old messages that were previously categorized
     */
    private CategoryScore mergeScoresIntelligently(CategoryScore oldScore, 
                                                   CategoryScore newScore,
                                                   EngagementMetrics metrics,
                                                   int newMessageCount,
                                                   int oldMessageCount) {
        
        // CRITICAL: Calculate message ratio weight FIRST
        // If we have 50 old messages and 10 new messages, we should trust old score more
        // even if new messages are highly engaging
        double totalMessages = newMessageCount + oldMessageCount;
        double messageRatioWeight = totalMessages > 0 ? (double) oldMessageCount / totalMessages : 0.5;
        
        // Message count weight: favors old score if there are more old messages
        // Formula: oldMessageRatio^2 to emphasize large differences
        // Example: 50 old, 10 new → ratio = 0.83 → weight = 0.69
        // Example: 10 old, 50 new → ratio = 0.17 → weight = 0.03 (trust new more)
        double messageCountWeight = Math.pow(messageRatioWeight, 2.0);
        
        // Weight factors based on engagement (from new messages context)
        double engagementWeight = metrics.engagementLevel; // Higher engagement = trust new score more
        double replyRatioWeight = Math.min(1.0, metrics.replyRatio); // Higher reply ratio = more reliable
        
        // Faster replies indicate higher engagement (inverse relationship, capped)
        double replyTimeWeight = 1.0 - Math.min(0.5, (metrics.avgReplyTime - 60) / 3600); // Normalize to 0.5-1.0
        
        // Engagement-based weight: how much to trust the new score based on quality
        // If engagement is high, reply ratio is good, and replies are fast, trust new score more
        double engagementBasedNewWeight = (engagementWeight * 0.4) + (replyRatioWeight * 0.3) + (replyTimeWeight * 0.3);
        
        // COMBINED WEIGHT: Balance message count (data volume) vs engagement (data quality)
        // Message count gets 60% weight, engagement gets 40% weight
        // This ensures that if we have 50 old messages vs 10 new messages, 
        // we still favor old score even if new messages are engaging
        
        double finalNewScoreWeight = (engagementBasedNewWeight * 0.4) + ((1.0 - messageCountWeight) * 0.6);
        double finalOldScoreWeight = 1.0 - finalNewScoreWeight;
        
        // Additional adjustment: If old message count is significantly larger, further reduce new weight
        if (oldMessageCount > 0 && newMessageCount > 0) {
            double oldToNewRatio = (double) oldMessageCount / newMessageCount;
            if (oldToNewRatio > 3.0) {
                // If we have 3x+ more old messages, apply additional reduction
                // Example: 30 old, 10 new → ratio 3.0 → reduce new weight by 20%
                double reductionFactor = Math.min(0.3, (oldToNewRatio - 3.0) / 10.0);
                finalNewScoreWeight *= (1.0 - reductionFactor);
                finalOldScoreWeight = 1.0 - finalNewScoreWeight;
            }
        }
        
        // Merge relevance scores (weighted average)
        double mergedRelevance = (oldScore.relevanceScore * finalOldScoreWeight) + 
                                 (newScore.relevanceScore * finalNewScoreWeight);
        
        // Merge success scores (weighted average with engagement adjustment)
        double mergedSuccess = (oldScore.successScore * finalOldScoreWeight) + 
                               (newScore.successScore * finalNewScoreWeight);
        
        // Preserve outcome type from new score if available, otherwise keep old
        String mergedOutcomeType = newScore.outcomeType != null && !newScore.outcomeType.isEmpty() ? 
            newScore.outcomeType : oldScore.outcomeType;
        String mergedReason = newScore.reason != null && !newScore.reason.isEmpty() ? 
            newScore.reason : oldScore.reason;
        
        return new CategoryScore(mergedRelevance, mergedSuccess, mergedOutcomeType, mergedReason);
    }
    
    /**
     * Save merged category scores (only update existing, insert new)
     */
    private void saveChatCategorizationWithScoresMerge(int dialogId,
                                                       Map<String, CategoryScore> existingCategories,
                                                       Map<String, CategoryScore> mergedScores) throws SQLException {
        
        // Separate into new categories and updated categories
        List<String> newCategories = new ArrayList<>();
        Map<String, CategoryScore> updatedCategories = new HashMap<>();
        
        for (Map.Entry<String, CategoryScore> entry : mergedScores.entrySet()) {
            if (existingCategories.containsKey(entry.getKey())) {
                updatedCategories.put(entry.getKey(), entry.getValue());
            } else {
                newCategories.add(entry.getKey());
            }
        }
        
        // Insert new categories
        if (!newCategories.isEmpty()) {
            String insertSql = """
                INSERT INTO chat_goals (dialog_id, category_name, relevance_score, success_score, outcome, categorized_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """;
            
            try (Connection conn = DatabaseSchema.getConnectionInstance();
                 PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                for (String categoryName : newCategories) {
                    CategoryScore score = mergedScores.get(categoryName);
                    pstmt.setInt(1, dialogId);
                    pstmt.setString(2, categoryName);
                    pstmt.setDouble(3, score.relevanceScore);
                    pstmt.setDouble(4, score.successScore);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
        
        // Update existing categories
        if (!updatedCategories.isEmpty()) {
            String updateSql = """
                UPDATE chat_goals
                SET relevance_score = ?,
                    success_score = ?,
                    categorized_at = NOW()
                WHERE dialog_id = ? AND category_name = ?
                """;
            
            try (Connection conn = DatabaseSchema.getConnectionInstance();
                 PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                for (Map.Entry<String, CategoryScore> entry : updatedCategories.entrySet()) {
                    CategoryScore score = entry.getValue();
                    String outcomeInfo = score.outcomeType != null && !score.outcomeType.isEmpty() ? 
                        (score.reason != null && !score.reason.isEmpty() ? 
                            String.format("%s: %s", score.outcomeType, score.reason) : score.outcomeType) :
                        (score.reason != null ? score.reason : null);
                    
                    pstmt.setDouble(1, score.relevanceScore);
                    pstmt.setDouble(2, score.successScore);
                    pstmt.setString(3, outcomeInfo);
                    pstmt.setInt(4, dialogId);
                    pstmt.setString(5, entry.getKey());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
    }
    
    /**
     * Helper class to store engagement metrics
     */
    private static class EngagementMetrics {
        final long userMessageCount;
        final long otherMessageCount;
        final double replyRatio; // other/user messages
        final double avgReplyTime; // seconds
        final double engagementLevel; // 0.0-1.0
        
        EngagementMetrics(long userMessageCount, long otherMessageCount, 
                         double replyRatio, double avgReplyTime, double engagementLevel) {
            this.userMessageCount = userMessageCount;
            this.otherMessageCount = otherMessageCount;
            this.replyRatio = replyRatio;
            this.avgReplyTime = avgReplyTime;
            this.engagementLevel = engagementLevel;
        }
    }

    /**
     * Load all messages for a dialog from database
     */
    private List<Message> loadMessagesForDialog(int dialogId) throws SQLException {
        List<Message> messages = new ArrayList<>();
        
        String sql = """
            SELECT message_id, sender, text, timestamp, has_media
            FROM messages
            WHERE dialog_id = ?
            ORDER BY timestamp ASC
            """;

        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, dialogId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Message msg = new Message();
                    msg.setId(rs.getInt("message_id"));
                    msg.setSender(rs.getString("sender"));
                    msg.setContent(rs.getString("text"));
                    
                    java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");
                    if (timestamp != null) {
                        msg.setTimestamp(timestamp.toLocalDateTime());
                    }
                    
                    msg.setFromUser("me".equalsIgnoreCase(msg.getSender()) || "You".equalsIgnoreCase(msg.getSender()));
                    msg.setHasMedia(rs.getBoolean("has_media"));
                    
                    messages.add(msg);
                }
            }
        }
        
        return messages;
    }

    public static class ChatCategoryResult {
        public int dialogId;
        public long platformDialogId;
        public String dialogName;
        public String dialogType;
        public String categoryName;
        public double relevanceScore;
        public double successScore;
        public String outcome;
    }

    /**
     * Fallback category name to use when AI services are unavailable or responses are invalid.
     * Prefers a generic 'other' category when present; otherwise returns the first enum value
     * or the literal 'general' as a last resort.
     */
    private String getFallbackCategoryName() {
        try {
            com.aria.core.model.ChatCategory other = com.aria.core.model.ChatCategory.fromName("other");
            if (other != null) {
                return other.getName();
            }
            java.util.List<String> all = getAllCategories();
            if (all != null && !all.isEmpty()) {
                return all.get(0);
            }
        } catch (Exception ignored) { }
        return "general";
    }

}

