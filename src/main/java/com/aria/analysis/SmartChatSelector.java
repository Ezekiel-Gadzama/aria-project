package com.aria.analysis;

import com.aria.core.model.Message;
import com.aria.storage.DatabaseSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Smart chat selector that:
 * 1. Deduplicates chats across categories
 * 2. Uses progressive AND filtering (2+ categories, 3+, etc.) to manage conversation length
 * 3. Ensures conversation fits within OpenAI token limits
 */
public class SmartChatSelector {
    private static final int MAX_TOKENS_PER_CHAT = 500; // Approximate tokens per chat
    private static final int MAX_TOTAL_TOKENS = 8000; // Conservative limit for OpenAI
    private static final int MIN_CATEGORIES_FOR_AND = 2; // Start with 2 categories AND

    /**
     * Get chats that match categories, deduplicated and filtered by AND conditions
     * @param categories List of relevant categories
     * @param userId User ID
     * @param maxChats Maximum number of chats to return
     * @return Map of dialog ID to list of messages (deduplicated)
     */
    public Map<Integer, List<Message>> getFilteredChats(
            List<String> categories, 
            int userId, 
            int maxChats) throws SQLException {
        
        if (categories == null || categories.isEmpty()) {
            return new HashMap<>();
        }

        // Start with OR logic (any matching category)
        Map<Integer, List<Message>> chats = getChatsWithOR(categories, userId);
        
        // Deduplicate by dialog ID
        chats = deduplicateChats(chats);
        
        // Check conversation length
        int totalTokens = estimateTotalTokens(chats);
        
        // If too long, use AND filtering
        if (totalTokens > MAX_TOTAL_TOKENS) {
            chats = applyAndFiltering(categories, userId, chats, maxChats);
        }
        
        // Limit to maxChats if still too many
        if (chats.size() > maxChats) {
            chats = limitChats(chats, maxChats);
        }
        
        return chats;
    }

    /**
     * Get chats matching ANY of the categories (OR logic)
     */
    private Map<Integer, List<Message>> getChatsWithOR(
            List<String> categories, int userId) throws SQLException {
        
        String placeholders = String.join(",", Collections.nCopies(categories.size(), "?"));
        String sql = String.format("""
            SELECT DISTINCT d.id as dialog_id,
                   MAX(cg.relevance_score) as max_relevance,
                   MAX(cg.success_score) as max_success
            FROM dialogs d
            INNER JOIN chat_goals cg ON d.id = cg.dialog_id
            WHERE d.user_id = ?
                AND d.type NOT IN ('group', 'channel', 'supergroup', 'bot')
                AND (d.is_bot IS NULL OR d.is_bot = FALSE)
                AND cg.category_name IN (%s)
            GROUP BY d.id
            ORDER BY MAX(cg.relevance_score) DESC, MAX(cg.success_score) DESC
            """, placeholders);

        Set<Integer> dialogIds = new LinkedHashSet<>();
        
        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            for (int i = 0; i < categories.size(); i++) {
                pstmt.setString(i + 2, categories.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    dialogIds.add(rs.getInt("dialog_id"));
                }
            }
        }

        // Load messages for each dialog
        return loadMessagesForDialogs(dialogIds);
    }

    /**
     * Apply AND filtering: chats must match 2+ categories, then 3+, etc.
     * until conversation fits within token limit
     */
    private Map<Integer, List<Message>> applyAndFiltering(
            List<String> categories,
            int userId,
            Map<Integer, List<Message>> currentChats,
            int maxChats) throws SQLException {
        
        int minCategories = MIN_CATEGORIES_FOR_AND;
        
        while (minCategories <= categories.size()) {
            // Get chats matching at least minCategories
            Map<Integer, List<Message>> filteredChats = getChatsWithAND(
                categories, userId, minCategories);
            
            // Deduplicate
            filteredChats = deduplicateChats(filteredChats);
            
            // Check token count
            int totalTokens = estimateTotalTokens(filteredChats);
            
            if (totalTokens <= MAX_TOTAL_TOKENS || filteredChats.size() <= maxChats) {
                return filteredChats;
            }
            
            // If still too long, increase AND requirement
            minCategories++;
        }
        
        // If still too long even with max AND, limit to top chats
        return limitChats(currentChats, maxChats);
    }

    /**
     * Get chats matching at least N categories (AND logic)
     */
    private Map<Integer, List<Message>> getChatsWithAND(
            List<String> categories,
            int userId,
            int minCategories) throws SQLException {
        
        String placeholders = String.join(",", Collections.nCopies(categories.size(), "?"));
        String sql = String.format("""
            SELECT d.id as dialog_id, COUNT(DISTINCT cg.category_name) as category_count
            FROM dialogs d
            INNER JOIN chat_goals cg ON d.id = cg.dialog_id
            WHERE d.user_id = ?
                AND d.type NOT IN ('group', 'channel', 'supergroup', 'bot')
                AND (d.is_bot IS NULL OR d.is_bot = FALSE)
                AND cg.category_name IN (%s)
            GROUP BY d.id
            HAVING COUNT(DISTINCT cg.category_name) >= ?
            ORDER BY MAX(cg.relevance_score) DESC, MAX(cg.success_score) DESC, category_count DESC
            """, placeholders);

        Set<Integer> dialogIds = new LinkedHashSet<>();
        
        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            for (int i = 0; i < categories.size(); i++) {
                pstmt.setString(i + 2, categories.get(i));
            }
            pstmt.setInt(categories.size() + 2, minCategories);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    dialogIds.add(rs.getInt("dialog_id"));
                }
            }
        }

        return loadMessagesForDialogs(dialogIds);
    }

    /**
     * Deduplicate chats by dialog ID (removes duplicates)
     */
    private Map<Integer, List<Message>> deduplicateChats(
            Map<Integer, List<Message>> chats) {
        // Already using dialog ID as key, so no duplicates by definition
        // But we can ensure uniqueness if needed
        return new LinkedHashMap<>(chats);
    }

    /**
     * Load messages for multiple dialogs
     */
    private Map<Integer, List<Message>> loadMessagesForDialogs(Set<Integer> dialogIds) throws SQLException {
        if (dialogIds.isEmpty()) {
            return new HashMap<>();
        }

        String placeholders = String.join(",", Collections.nCopies(dialogIds.size(), "?"));
        String sql = String.format("""
            SELECT m.dialog_id, m.message_id, m.sender, m.text, m.timestamp, m.has_media
            FROM messages m
            WHERE m.dialog_id IN (%s)
            ORDER BY m.dialog_id, m.timestamp
            """, placeholders);

        Map<Integer, List<Message>> chats = new LinkedHashMap<>();
        
        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int index = 1;
            for (Integer dialogId : dialogIds) {
                pstmt.setInt(index++, dialogId);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int dialogId = rs.getInt("dialog_id");
                    
                    Message msg = new Message();
                    msg.setId(rs.getInt("message_id"));
                    msg.setSender(rs.getString("sender"));
                    msg.setContent(rs.getString("text"));
                    
                    java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");
                    if (timestamp != null) {
                        msg.setTimestamp(timestamp.toLocalDateTime());
                    }
                    
                    msg.setFromUser("You".equalsIgnoreCase(msg.getSender()));
                    msg.setHasMedia(rs.getBoolean("has_media"));
                    
                    chats.computeIfAbsent(dialogId, k -> new ArrayList<>()).add(msg);
                }
            }
        }
        
        return chats;
    }

    /**
     * Estimate total tokens for all chats
     * Rough estimate: 1 token ≈ 4 characters
     */
    private int estimateTotalTokens(Map<Integer, List<Message>> chats) {
        int totalChars = 0;
        for (List<Message> messages : chats.values()) {
            for (Message msg : messages) {
                if (msg.getContent() != null) {
                    totalChars += msg.getContent().length();
                }
            }
        }
        // Add overhead for formatting and structure
        return (totalChars / 4) + (chats.size() * 100);
    }

    /**
     * Limit chats to top N based on relevance and success scores
     */
    private Map<Integer, List<Message>> limitChats(
            Map<Integer, List<Message>> chats, int maxChats) throws SQLException {
        
        if (chats.size() <= maxChats) {
            return chats;
        }

        // Get top dialogs by relevance and success
        Set<Integer> dialogIds = chats.keySet();
        String placeholders = String.join(",", Collections.nCopies(dialogIds.size(), "?"));
        String sql = String.format("""
            SELECT DISTINCT d.id as dialog_id,
                   MAX(cg.relevance_score) as max_relevance,
                   MAX(cg.success_score) as max_success
            FROM dialogs d
            INNER JOIN chat_goals cg ON d.id = cg.dialog_id
            WHERE d.id IN (%s)
            GROUP BY d.id
            ORDER BY max_relevance DESC, max_success DESC
            LIMIT ?
            """, placeholders);

        Set<Integer> topDialogIds = new LinkedHashSet<>();
        
        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int index = 1;
            for (Integer dialogId : dialogIds) {
                pstmt.setInt(index++, dialogId);
            }
            pstmt.setInt(index, maxChats);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    topDialogIds.add(rs.getInt("dialog_id"));
                }
            }
        }

        // Return only top chats
        Map<Integer, List<Message>> limitedChats = new LinkedHashMap<>();
        for (Integer dialogId : topDialogIds) {
            if (chats.containsKey(dialogId)) {
                limitedChats.put(dialogId, chats.get(dialogId));
            }
        }
        
        return limitedChats;
    }

    /**
     * Format chats for OpenAI prompt (with token limit awareness)
     */
    public String formatChatsForPrompt(Map<Integer, List<Message>> chats, int maxTokens) {
        StringBuilder sb = new StringBuilder();
        int currentTokens = 0;
        int chatCount = 0;
        
        for (Map.Entry<Integer, List<Message>> entry : chats.entrySet()) {
            List<Message> messages = entry.getValue();
            
            // Estimate tokens for this chat
            int chatTokens = estimateChatTokens(messages);
            
            if (currentTokens + chatTokens > maxTokens && chatCount > 0) {
                // Skip this chat if it would exceed limit
                break;
            }
            
            sb.append("\n=== Chat ").append(++chatCount).append(" ===\n");
            for (Message msg : messages) {
                String sender = msg.isFromUser() ? "You" : msg.getSender();
                sb.append(sender).append(": ").append(msg.getContent()).append("\n");
            }
            
            currentTokens += chatTokens;
        }
        
        return sb.toString();
    }

    /**
     * Estimate tokens for a single chat
     */
    public int estimateChatTokens(List<Message> messages) {
        int totalChars = 0;
        for (Message msg : messages) {
            if (msg.getContent() != null) {
                totalChars += msg.getContent().length();
            }
        }
        // Add overhead for formatting
        // Rough estimate: 1 token ≈ 4 characters
        return (totalChars / 4) + 50;
    }
    
    /**
     * Static method to estimate tokens (for use in other classes)
     */
    public static int estimateTokensForMessages(List<Message> messages) {
        int totalChars = 0;
        for (Message msg : messages) {
            if (msg.getContent() != null) {
                totalChars += msg.getContent().length();
            }
        }
        return (totalChars / 4) + 50;
    }
}

