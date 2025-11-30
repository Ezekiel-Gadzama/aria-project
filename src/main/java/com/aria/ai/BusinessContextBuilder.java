package com.aria.ai;

import com.aria.core.model.TargetBusiness;
import com.aria.core.model.BusinessSubTarget;
import com.aria.core.model.Message;
import com.aria.storage.DatabaseManager;
import java.sql.*;
import java.util.*;

/**
 * Builds comprehensive context for Business Bot by aggregating messages
 * from all sub-targets (channels, groups, private chats) within a business.
 */
public class BusinessContextBuilder {
    public BusinessContextBuilder() {
    }
    
    /**
     * Build comprehensive context for Business Bot
     * Aggregates messages from ALL sub-targets in the business
     */
    public String buildBusinessContext(
            TargetBusiness business,
            int userId,
            String userQuestion) throws SQLException {
        
        StringBuilder context = new StringBuilder();
        
        // ============================================
        // SECTION 1: BUSINESS INFORMATION
        // ============================================
        context.append("=== BUSINESS CONTEXT ===\n\n");
        context.append("Business Name: ").append(business.getName()).append("\n");
        if (business.getDescription() != null && !business.getDescription().trim().isEmpty()) {
            context.append("Description: ").append(business.getDescription()).append("\n");
        }
        context.append("\n");
        
        // ============================================
        // SECTION 2: SUB-TARGETS OVERVIEW
        // ============================================
        context.append("=== BUSINESS SUB-TARGETS ===\n\n");
        List<BusinessSubTarget> subTargets = business.getSubTargets();
        if (subTargets == null || subTargets.isEmpty()) {
            context.append("No sub-targets configured for this business.\n\n");
        } else {
            context.append("This business contains ").append(subTargets.size()).append(" sub-target(s):\n");
            for (BusinessSubTarget subTarget : subTargets) {
                context.append("- ").append(subTarget.getName())
                      .append(" (").append(subTarget.getType()).append(" on ").append(subTarget.getPlatform()).append(")\n");
            }
            context.append("\n");
        }
        
        // ============================================
        // SECTION 3: AGGREGATED CONVERSATION HISTORY
        // ============================================
        context.append("=== CONVERSATION HISTORY FROM ALL SUB-TARGETS ===\n\n");
        context.append("The following messages are from all channels, groups, and private chats " +
                      "within this business. Use this information to answer questions about tasks, " +
                      "projects, discussions, and team activities.\n\n");
        
        List<Message> allBusinessMessages = loadAllBusinessMessages(business, userId);
        
        if (allBusinessMessages.isEmpty()) {
            context.append("No messages found in business sub-targets.\n\n");
        } else {
            // Group messages by sub-target for better context
            Map<Integer, List<Message>> messagesBySubTarget = new HashMap<>();
            for (Message msg : allBusinessMessages) {
                Integer dialogId = messageDialogMap.get(msg.getId());
                if (dialogId != null) {
                    messagesBySubTarget.computeIfAbsent(dialogId, k -> new ArrayList<>()).add(msg);
                }
            }
            
            // Format messages by sub-target
            for (Map.Entry<Integer, List<Message>> entry : messagesBySubTarget.entrySet()) {
                Integer dialogId = entry.getKey();
                List<Message> messages = entry.getValue();
                
                // Get sub-target name for this dialog
                String subTargetName = getSubTargetNameForDialog(business, dialogId);
                context.append("--- Messages from: ").append(subTargetName).append(" ---\n");
                
                for (Message msg : messages) {
                    if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                        context.append("[ID: ").append(msg.getId()).append("] ");
                        if (msg.getReferenceId() != null) {
                            context.append("[REPLY TO ID: ").append(msg.getReferenceId()).append("] ");
                        }
                        context.append(msg.isFromUser() ? "You" : "Team Member")
                              .append(": ")
                              .append(msg.getContent())
                              .append("\n");
                    }
                }
                context.append("\n");
            }
        }
        
        // ============================================
        // SECTION 4: USER QUESTION AND INSTRUCTIONS
        // ============================================
        context.append("=== USER QUESTION ===\n\n");
        context.append("The user is asking: ").append(userQuestion).append("\n\n");
        
        context.append("=== INSTRUCTIONS ===\n\n");
        context.append("You are an intelligent assistant for this business. Your role is to:\n");
        context.append("1. Answer questions about tasks, projects, and discussions within the business\n");
        context.append("2. Provide summaries of conversations from specific sub-targets\n");
        context.append("3. Identify who is working on what based on conversation history\n");
        context.append("4. Track progress on tasks and projects mentioned in conversations\n");
        context.append("5. Provide insights based on patterns across all business sub-targets\n\n");
        context.append("When answering:\n");
        context.append("- Be specific and reference actual conversations when possible\n");
        context.append("- If asked about a specific task/project, search for mentions in the conversation history\n");
        context.append("- If information is not available, clearly state that\n");
        context.append("- Use the sub-target names to indicate where information came from\n");
        context.append("- Provide actionable insights when possible\n\n");
        
        context.append("=== END OF CONTEXT ===\n");
        context.append("Now answer the user's question based on the business context provided above.\n");
        
        return context.toString();
    }
    
    /**
     * Load ALL messages from all sub-targets in the business
     */
    // Map to track which dialog each message belongs to
    private final Map<Integer, Integer> messageDialogMap = new HashMap<>();
    
    private List<Message> loadAllBusinessMessages(TargetBusiness business, int userId) throws SQLException {
        List<Message> allMessages = new ArrayList<>();
        messageDialogMap.clear();
        List<BusinessSubTarget> subTargets = business.getSubTargets();
        
        if (subTargets == null || subTargets.isEmpty()) {
            return allMessages;
        }
        
        try (Connection conn = getConnection()) {
            // Build query to get messages from all dialogs associated with business sub-targets
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT DISTINCT m.message_id, m.dialog_id, m.text, m.sender, m.timestamp, m.has_media, m.reference_id ");
            sql.append("FROM messages m ");
            sql.append("JOIN business_subtargets bst ON m.dialog_id = bst.dialog_id ");
            sql.append("WHERE bst.business_id = ? ");
            sql.append("AND m.timestamp >= NOW() - INTERVAL '6 months' "); // Last 6 months of business context
            sql.append("ORDER BY m.timestamp DESC ");
            sql.append("LIMIT 1000"); // Limit to prevent context overflow
            
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setInt(1, business.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Message msg = new Message();
                        int messageId = (int) rs.getLong("message_id"); // Message.id is int
                        msg.setId(messageId);
                        int dialogId = rs.getInt("dialog_id");
                        
                        // Decrypt message text
                        String encryptedText = rs.getString("text");
                        if (encryptedText != null && !encryptedText.isEmpty()) {
                            try {
                                msg.setContent(com.aria.storage.SecureStorage.decrypt(encryptedText));
                            } catch (Exception e) {
                                msg.setContent("");
                            }
                        }
                        
                        String sender = rs.getString("sender");
                        msg.setFromUser("me".equalsIgnoreCase(sender));
                        Timestamp ts = rs.getTimestamp("timestamp");
                        if (ts != null) {
                            msg.setTimestamp(ts.toLocalDateTime());
                        }
                        msg.setHasMedia(rs.getBoolean("has_media"));
                        
                        Long referenceId = rs.getObject("reference_id", Long.class);
                        if (referenceId != null) {
                            msg.setReferenceId(referenceId);
                        }
                        
                        // Store dialogId mapping for later grouping
                        messageDialogMap.put(messageId, dialogId);
                        allMessages.add(msg);
                    }
                }
            }
        }
        
        return allMessages;
    }
    
    /**
     * Get sub-target name for a dialog ID
     */
    private String getSubTargetNameForDialog(TargetBusiness business, Integer dialogId) {
        if (dialogId == null) {
            return "Unknown";
        }
        
        List<BusinessSubTarget> subTargets = business.getSubTargets();
        if (subTargets != null) {
            for (BusinessSubTarget subTarget : subTargets) {
                if (dialogId.equals(subTarget.getDialogId())) {
                    return subTarget.getName() + " (" + subTarget.getType() + ")";
                }
            }
        }
        
        // If not found, try to get from database
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name FROM dialogs WHERE id = ?")) {
                ps.setInt(1, dialogId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("name");
                    }
                }
            }
        } catch (SQLException e) {
            // Ignore
        }
        
        return "Dialog " + dialogId;
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

