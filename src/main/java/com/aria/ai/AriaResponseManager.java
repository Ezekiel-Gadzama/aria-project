package com.aria.ai;

import com.aria.core.model.TargetUser;
import com.aria.core.model.SubTargetUser;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages OpenAI Responses API state for target users
 * Stores response IDs in database to maintain conversation context
 */
public class AriaResponseManager {
    private final OpenAIResponsesClient responsesClient;
    
    // In-memory cache: target_user_id -> {subtarget_user_id: response_id}
    // This is temporary and synced with database
    private final Map<Integer, Map<Integer, String>> responseCache = new HashMap<>();
    
    public AriaResponseManager() {
        this.responsesClient = new OpenAIResponsesClient();
        loadCacheFromDatabase();
    }
    
    /**
     * Load response IDs from database into cache
     */
    private void loadCacheFromDatabase() {
        try (Connection conn = getConnection()) {
            String sql = "SELECT target_user_id, subtarget_user_id, openai_response_id FROM target_user_responses";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int targetUserId = rs.getInt("target_user_id");
                    Integer subtargetUserId = rs.getObject("subtarget_user_id", Integer.class);
                    String responseId = rs.getString("openai_response_id");
                    
                    // Use -1 for cross-platform (null subtarget_user_id)
                    int key = subtargetUserId != null ? subtargetUserId : -1;
                    
                    responseCache.computeIfAbsent(targetUserId, k -> new HashMap<>()).put(key, responseId);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading response cache from database: " + e.getMessage());
        }
    }
    
    /**
     * Get last message ID for a target user and subtarget user
     * @param targetUserId Target user ID
     * @param subtargetUserId SubTarget user ID (null for cross-platform)
     * @return Last message ID or null if not found
     */
    public Long getLastMessageId(int targetUserId, Integer subtargetUserId) {
        try (Connection conn = getConnection()) {
            String sql = """
                SELECT last_message_id FROM target_user_responses
                WHERE target_user_id = ? AND (subtarget_user_id = ? OR (subtarget_user_id IS NULL AND ? IS NULL))
            """;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, targetUserId);
                if (subtargetUserId != null) {
                    pstmt.setInt(2, subtargetUserId);
                    pstmt.setNull(3, Types.INTEGER);
                } else {
                    pstmt.setNull(2, Types.INTEGER);
                    pstmt.setNull(3, Types.INTEGER);
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getObject("last_message_id", Long.class);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting last message ID: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get or create response ID for a target user and subtarget user
     * @param targetUserId Target user ID
     * @param subtargetUserId SubTarget user ID (null for cross-platform)
     * @return Response ID or null if not found
     */
    public String getResponseId(int targetUserId, Integer subtargetUserId) {
        Map<Integer, String> subtargetMap = responseCache.get(targetUserId);
        if (subtargetMap == null) {
            return null;
        }
        int key = subtargetUserId != null ? subtargetUserId : -1;
        return subtargetMap.get(key);
    }
    
    /**
     * Save response ID and last message ID to database and cache
     */
    public void saveResponseId(int targetUserId, Integer subtargetUserId, String responseId, Long lastMessageId) throws SQLException {
        try (Connection conn = getConnection()) {
            // First, try to update existing record
            String updateSql = """
                UPDATE target_user_responses
                SET openai_response_id = ?, last_message_id = ?, updated_at = NOW()
                WHERE target_user_id = ? AND (subtarget_user_id = ? OR (subtarget_user_id IS NULL AND ? IS NULL))
            """;
            try (PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {
                updatePstmt.setString(1, responseId);
                if (lastMessageId != null) {
                    updatePstmt.setLong(2, lastMessageId);
                } else {
                    updatePstmt.setNull(2, Types.BIGINT);
                }
                updatePstmt.setInt(3, targetUserId);
                if (subtargetUserId != null) {
                    updatePstmt.setInt(4, subtargetUserId);
                    updatePstmt.setNull(5, Types.INTEGER);
                } else {
                    updatePstmt.setNull(4, Types.INTEGER);
                    updatePstmt.setNull(5, Types.INTEGER);
                }
                
                int updated = updatePstmt.executeUpdate();
                
                // If no row was updated, insert new record
                if (updated == 0) {
                    String insertSql = """
                        INSERT INTO target_user_responses (target_user_id, subtarget_user_id, openai_response_id, last_message_id, updated_at)
                        VALUES (?, ?, ?, ?, NOW())
                    """;
                    try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                        insertPstmt.setInt(1, targetUserId);
                        if (subtargetUserId != null) {
                            insertPstmt.setInt(2, subtargetUserId);
                        } else {
                            insertPstmt.setNull(2, Types.INTEGER);
                        }
                        insertPstmt.setString(3, responseId);
                        if (lastMessageId != null) {
                            insertPstmt.setLong(4, lastMessageId);
                        } else {
                            insertPstmt.setNull(4, Types.BIGINT);
                        }
                        insertPstmt.executeUpdate();
                    }
                }
            }
        }
        
        // Update cache
        responseCache.computeIfAbsent(targetUserId, k -> new HashMap<>())
                    .put(subtargetUserId != null ? subtargetUserId : -1, responseId);
    }
    
    /**
     * Generate reply using OpenAI Responses API
     * @param targetUser Target user
     * @param subtargetUser SubTarget user (null for cross-platform)
     * @param newMessage New message to respond to (can be full conversation or single message)
     * @param fullContext Full 70/15/15 context (only used on first call)
     * @param lastMessageId Last message ID to track (null if first call)
     * @return Generated response text
     */
    public String generateReply(TargetUser targetUser, SubTargetUser subtargetUser, 
                                String newMessage, String fullContext, Long lastMessageId) {
        int targetUserId = targetUser.getTargetId();
        Integer subtargetUserId = subtargetUser != null ? subtargetUser.getId() : null;
        
        String previousResponseId = getResponseId(targetUserId, subtargetUserId);
        
        OpenAIResponsesClient.ResponseResult result;
        
        if (previousResponseId != null) {
            // Continue conversation - AI remembers everything!
            result = responsesClient.continueResponse(previousResponseId, newMessage);
        } else {
            // Start new conversation with full 70/15/15 context
            result = responsesClient.createResponse(fullContext);
        }
        
        if (result.isSuccess() && result.responseId != null) {
            try {
                // Save response ID and last message ID for future use
                saveResponseId(targetUserId, subtargetUserId, result.responseId, lastMessageId);
            } catch (SQLException e) {
                System.err.println("Error saving response ID: " + e.getMessage());
            }
            return result.outputText;
        }
        
        return null;
    }
    
    private Connection getConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
            System.getenv("DATABASE_URL") != null
                ? System.getenv("DATABASE_URL")
                : "jdbc:postgresql://localhost:5432/aria",
            System.getenv("DATABASE_USER") != null
                ? System.getenv("DATABASE_USER")
                : "postgres",
            System.getenv("DATABASE_PASSWORD") != null
                ? System.getenv("DATABASE_PASSWORD")
                : "Ezekiel(23)");
    }
}

