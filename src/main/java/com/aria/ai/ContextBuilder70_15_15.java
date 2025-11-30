package com.aria.ai;

import com.aria.core.model.TargetUser;
import com.aria.core.model.SubTargetUser;
import com.aria.core.model.Message;
import com.aria.storage.DatabaseManager;
import java.sql.*;
import java.util.*;

/**
 * Builds comprehensive 70/15/15 context for OpenAI Responses API
 * 70% successful dialogs, 15% failed dialogs, 15% AI improvement examples
 */
public class ContextBuilder70_15_15 {
    private final DatabaseManager databaseManager;
    
    public ContextBuilder70_15_15() {
        this.databaseManager = new DatabaseManager();
    }
    
    /**
     * Build comprehensive 70/15/15 context for OpenAI Responses API
     * This is called only on the FIRST request to establish conversation state
     */
    public String build70_15_15_Context(
            TargetUser targetUser,
            SubTargetUser subtargetUser,
            int userId,
            boolean crossPlatformContextEnabled,
            boolean adminModeEnabled) throws SQLException {
        
        StringBuilder context = new StringBuilder();
        
        // ============================================
        // SECTION 1: TARGET USER INFORMATION
        // ============================================
        context.append("=== TARGET USER INFORMATION ===\n\n");
        context.append("Target User Name: ").append(targetUser.getName()).append("\n");
        if (targetUser.getBio() != null && !targetUser.getBio().trim().isEmpty()) {
            context.append("Bio: ").append(targetUser.getBio()).append("\n");
        }
        context.append("Desired Outcome: ").append(
            targetUser.getDesiredOutcome() != null ? targetUser.getDesiredOutcome() : "Build a connection"
        ).append("\n");
        if (targetUser.getMeetingContext() != null && !targetUser.getMeetingContext().trim().isEmpty()) {
            context.append("Where/How You Met: ").append(targetUser.getMeetingContext()).append("\n");
        }
        if (targetUser.getImportantDetails() != null && !targetUser.getImportantDetails().trim().isEmpty()) {
            context.append("Important Details: ").append(targetUser.getImportantDetails()).append("\n");
        }
        context.append("\n");
        
        // ============================================
        // SECTION 2: SUBTARGET USER / PLATFORM INFORMATION
        // ============================================
        context.append("=== CURRENT CONVERSATION PLATFORM ===\n\n");
        if (subtargetUser != null) {
            context.append("Platform: ").append(subtargetUser.getPlatform()).append("\n");
            context.append("Username: ").append(subtargetUser.getUsername()).append("\n");
            if (subtargetUser.getName() != null && !subtargetUser.getName().trim().isEmpty()) {
                context.append("Platform-specific Name: ").append(subtargetUser.getName()).append("\n");
            }
            if (subtargetUser.getNumber() != null && !subtargetUser.getNumber().trim().isEmpty()) {
                context.append("Phone Number: ").append(subtargetUser.getNumber()).append("\n");
            }
        }
        context.append("Cross-Platform Context: ").append(crossPlatformContextEnabled ? "Enabled" : "Disabled").append("\n");
        context.append("\n");
        
        // ============================================
        // SECTION 3: COMMUNICATION STYLE PROFILE
        // ============================================
        context.append("=== COMMUNICATION STYLE PROFILE ===\n\n");
        if (subtargetUser != null && subtargetUser.getAdvancedCommunicationSettings() != null) {
            try {
                org.json.JSONObject settings = new org.json.JSONObject(subtargetUser.getAdvancedCommunicationSettings());
                context.append("Humor Level: ").append(String.format("%.1f", settings.optDouble("humorLevel", 0.4)))
                      .append(" (0.0 = very serious, 1.0 = very humorous)\n");
                context.append("Formality Level: ").append(String.format("%.1f", settings.optDouble("formalityLevel", 0.5)))
                      .append(" (0.0 = very casual, 1.0 = very formal)\n");
                context.append("Empathy Level: ").append(String.format("%.1f", settings.optDouble("empathyLevel", 0.7)))
                      .append(" (0.0 = direct, 1.0 = very empathetic)\n");
                context.append("Response Time Average: ").append(String.format("%.0f", settings.optDouble("responseTimeAverage", 120.0)))
                      .append(" seconds\n");
                context.append("Message Length Average: ").append(String.format("%.0f", settings.optDouble("messageLengthAverage", 25.0)))
                      .append(" words\n");
                context.append("Question Rate: ").append(String.format("%.1f", settings.optDouble("questionRate", 0.3)))
                      .append(" (0.0 = rarely asks questions, 1.0 = asks many questions)\n");
                context.append("Engagement Level: ").append(String.format("%.1f", settings.optDouble("engagementLevel", 0.6)))
                      .append(" (0.0 = low engagement, 1.0 = high engagement)\n");
                if (settings.has("preferredOpening") && !settings.getString("preferredOpening").trim().isEmpty()) {
                    context.append("Preferred Opening: ").append(settings.getString("preferredOpening")).append("\n");
                }
            } catch (Exception e) {
                context.append("Using default communication style settings.\n");
            }
        } else {
            context.append("Using default communication style settings.\n");
        }
        context.append("\n");
        
        // ============================================
        // SECTION 4: CURRENT CONVERSATION HISTORY (ALL MESSAGES)
        // ============================================
        context.append("=== CURRENT CONVERSATION HISTORY ===\n\n");
        context.append("IMPORTANT: Messages with 'reference_id' are replies to the message with that ID.\n");
        context.append("Each message is identified by its 'id' field. When a message has a 'reference_id', it is replying to the message with that ID.\n\n");
        
        List<Message> currentConversationMessages = loadAllCurrentConversationMessages(
            targetUser, subtargetUser, userId, crossPlatformContextEnabled);
        
        if (currentConversationMessages.isEmpty()) {
            context.append("No conversation history yet. This is a new conversation.\n");
        } else {
            for (Message msg : currentConversationMessages) {
                if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                    context.append("[ID: ").append(msg.getId()).append("] ");
                    if (msg.getReferenceId() != null) {
                        context.append("[REPLY TO ID: ").append(msg.getReferenceId()).append("] ");
                    }
                    context.append(msg.isFromUser() ? "You" : "Them")
                          .append(": ")
                          .append(msg.getContent())
                          .append("\n");
                }
            }
        }
        context.append("\n");
        
        // ============================================
        // SECTION 5: GET CATEGORIES FOR CURRENT CONVERSATION
        // ============================================
        List<String> currentCategories = getCategoriesForCurrentConversation(
            targetUser, subtargetUser, userId, crossPlatformContextEnabled);
        
        if (currentCategories.isEmpty()) {
            // If no categories yet, use goal-based categories
            currentCategories = inferCategoriesFromGoal(targetUser.getDesiredOutcome());
        }
        
        context.append("=== CONVERSATION CATEGORIES ===\n\n");
        context.append("This conversation belongs to the following categories: ");
        context.append(String.join(", ", currentCategories)).append("\n");
        context.append("These categories help identify similar successful and failed conversations for learning.\n\n");
        
        // ============================================
        // SECTION 6: REFERENCE CONVERSATIONS (70/15/15)
        // ============================================
        context.append("=== REFERENCE CONVERSATIONS FOR LEARNING ===\n\n");
        context.append("The following examples show your communication style in similar situations.\n");
        context.append("These are organized as: 70% successful examples, 15% failed examples, 15% AI improvement examples.\n");
        context.append("Study these to understand what works and what doesn't, then apply that knowledge to this conversation.\n\n");
        
        // Get reference dialogs in same categories
        Map<Integer, List<Message>> referenceDialogs = getReferenceDialogsInCategories(
            currentCategories, userId, targetUser.getTargetId(), subtargetUser, crossPlatformContextEnabled);
        
        // Separate into successful (70%), failed (15%), and AI improvement (15%)
        // Store dialog metadata with examples
        List<DialogExample> successfulExamples = new ArrayList<>();
        List<DialogExample> failedExamples = new ArrayList<>();
        List<DialogExample> aiImprovementExamples = new ArrayList<>();
        
        separateDialogsBySuccessWithMetadata(referenceDialogs, currentCategories, successfulExamples, failedExamples, aiImprovementExamples);
        
        // Add 70% successful examples
        int successfulCount = (int) Math.round(referenceDialogs.size() * 0.70);
        context.append("--- SUCCESSFUL EXAMPLES (70%) ---\n");
        context.append("These conversations achieved their goals. Learn from what worked:\n\n");
        for (int i = 0; i < Math.min(successfulCount, successfulExamples.size()); i++) {
            DialogExample example = successfulExamples.get(i);
            context.append("SUCCESSFUL EXAMPLE ").append(i + 1).append(":\n");
            context.append("[DIALOG_ID: ").append(example.dialogId).append("] ");
            if (example.dialogName != null) {
                context.append("[DIALOG_NAME: ").append(example.dialogName).append("] ");
            }
            context.append("\n");
            formatConversationExample(context, example.messages, "SUCCESS");
            context.append("\n");
        }
        context.append("\n");
        
        // Add 15% failed examples
        int failedCount = (int) Math.round(referenceDialogs.size() * 0.15);
        context.append("--- FAILED EXAMPLES (15%) ---\n");
        context.append("These conversations did not achieve their goals. Learn from what went wrong:\n\n");
        for (int i = 0; i < Math.min(failedCount, failedExamples.size()); i++) {
            DialogExample example = failedExamples.get(i);
            context.append("FAILED EXAMPLE ").append(i + 1).append(":\n");
            context.append("[DIALOG_ID: ").append(example.dialogId).append("] ");
            if (example.dialogName != null) {
                context.append("[DIALOG_NAME: ").append(example.dialogName).append("] ");
            }
            context.append("\n");
            formatConversationExample(context, example.messages, "FAILED");
            context.append("\n");
        }
        context.append("\n");
        
        // Add 15% AI improvement examples (use successful ones with AI-enhanced responses)
        int aiCount = (int) Math.round(referenceDialogs.size() * 0.15);
        context.append("--- AI IMPROVEMENT EXAMPLES (15%) ---\n");
        context.append("These show how AI-enhanced responses can improve communication:\n\n");
        // For now, use top successful examples as AI improvement (can be enhanced later)
        for (int i = 0; i < Math.min(aiCount, successfulExamples.size()); i++) {
            DialogExample example = successfulExamples.get(i);
            context.append("AI IMPROVEMENT EXAMPLE ").append(i + 1).append(":\n");
            context.append("[DIALOG_ID: ").append(example.dialogId).append("] ");
            if (example.dialogName != null) {
                context.append("[DIALOG_NAME: ").append(example.dialogName).append("] ");
            }
            context.append("\n");
            formatConversationExample(context, example.messages, "AI_ENHANCED");
            context.append("\n");
        }
        context.append("\n");
        
        // ============================================
        // SECTION 7: INSTRUCTIONS FOR AI
        // ============================================
        context.append("=== YOUR ROLE AND INSTRUCTIONS ===\n\n");
        context.append("You are an AI assistant helping to craft responses for conversations.\n");
        context.append("Your goal is to help gradually move this conversation toward the desired outcome: \"")
              .append(targetUser.getDesiredOutcome() != null ? targetUser.getDesiredOutcome() : "Build a connection")
              .append("\"\n\n");
        
        context.append("KEY PRINCIPLES:\n");
        context.append("1. Each response should gradually progress toward the desired outcome\n");
        context.append("2. Match the communication style profile above (humor, formality, empathy levels)\n");
        context.append("3. Learn from the successful examples (70%) - use similar patterns that worked\n");
        context.append("4. Avoid patterns from failed examples (15%) - don't repeat mistakes\n");
        context.append("5. Use AI improvement techniques (15%) - enhance communication quality\n");
        context.append("6. Maintain consistency with your communication style across all messages\n");
        context.append("7. Be natural, authentic, and human-like in your responses\n");
        context.append("8. Consider the full conversation context, not just the last message\n");
        context.append("9. If replying to a specific message (reference_id), acknowledge that context\n");
        context.append("10. Build rapport gradually - don't rush toward the goal\n\n");
        
        context.append("RESPONSE GUIDELINES:\n");
        context.append("- Keep responses appropriate to the communication style profile\n");
        context.append("- Match the average message length and response time patterns\n");
        context.append("- Use questions when appropriate (based on question rate)\n");
        context.append("- Show appropriate empathy level based on the situation\n");
        context.append("- Progress toward goal naturally, not forcefully\n");
        context.append("- Remember all previous context in this conversation\n\n");
        
        context.append("When generating a response, consider:\n");
        context.append("- The full conversation history above\n");
        context.append("- The desired outcome and how to move toward it\n");
        context.append("- The communication style that matches this person\n");
        context.append("- What worked in successful examples and what failed in failed examples\n");
        context.append("- The current platform and context\n");
        if (crossPlatformContextEnabled) {
            context.append("- This is the same person across multiple platforms - maintain consistency\n");
        }
        context.append("\n");
        
        // Only include reference instructions if Admin Mode is enabled
        if (adminModeEnabled) {
            context.append("REFERENCE MESSAGES:\n");
            context.append("If your suggested response is based on or inspired by a specific message from the reference examples above,\n");
            context.append("you can reference it by including the message ID and dialog ID in your response.\n");
            context.append("Format: [REFERENCE: DIALOG_ID={dialog_id}, MESSAGE_ID={message_id}]\n");
            context.append("Example: [REFERENCE: DIALOG_ID=123, MESSAGE_ID=456] Your suggested response here...\n");
            context.append("This helps the user understand which message from which conversation inspired your suggestion.\n");
            context.append("Only include references when your response is directly based on a specific message from the examples.\n");
            context.append("Do NOT include references for simple, generic responses like 'have a nice day' or 'thanks'.\n");
            context.append("References should only be used when they add meaningful context to the suggestion.\n");
            context.append("If your response is a general synthesis of patterns, no reference is needed.\n\n");
        }
        
        context.append("=== END OF CONTEXT ===\n");
        context.append("From now on, you will receive only new messages. Remember all this context.\n");
        context.append("Generate responses that are natural, goal-oriented, and style-appropriate.\n");
        if (adminModeEnabled) {
            context.append("If referencing a specific message, include [REFERENCE: DIALOG_ID=X, MESSAGE_ID=Y] at the start of your response.\n");
        }
        
        return context.toString();
    }
    
    /**
     * Load ALL messages from current conversation (not just last 50)
     */
    private List<Message> loadAllCurrentConversationMessages(
            TargetUser targetUser,
            SubTargetUser subtargetUser,
            int userId,
            boolean crossPlatformContextEnabled) throws SQLException {
        
        List<Message> messages = new ArrayList<>();
        
        try (Connection conn = getConnection()) {
            List<Integer> dialogIds = new ArrayList<>();
            
            if (crossPlatformContextEnabled) {
                // Get all dialogs for this target user across all platforms
                dialogIds = databaseManager.getDialogIdsForTargetUser(targetUser.getTargetId(), userId);
            } else if (subtargetUser != null) {
                // Get dialog for this specific subtarget user
                Integer dialogId = findDialogForSubTarget(conn, subtargetUser, userId);
                if (dialogId != null) {
                    dialogIds.add(dialogId);
                }
            }
            
            if (dialogIds.isEmpty()) {
                return messages;
            }
            
            // Load ALL messages from these dialogs (no limit)
            String placeholders = String.join(",", Collections.nCopies(dialogIds.size(), "?"));
            String sql = String.format("""
                SELECT m.message_id, m.sender, m.text, m.timestamp, m.has_media, m.reference_id
                FROM messages m
                WHERE m.dialog_id IN (%s)
                ORDER BY m.timestamp ASC
            """, placeholders);
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < dialogIds.size(); i++) {
                    pstmt.setInt(i + 1, dialogIds.get(i));
                }
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Message msg = new Message();
                        msg.setId(rs.getInt("message_id"));
                        msg.setSender(rs.getString("sender"));
                        
                        // Decrypt text if needed
                        String encryptedText = rs.getString("text");
                        if (encryptedText != null && !encryptedText.isEmpty()) {
                            try {
                                msg.setContent(com.aria.storage.SecureStorage.decrypt(encryptedText));
                            } catch (Exception e) {
                                msg.setContent(encryptedText); // Use as-is if decryption fails
                            }
                        }
                        
                        java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");
                        if (timestamp != null) {
                            msg.setTimestamp(timestamp.toLocalDateTime());
                        }
                        
                        msg.setFromUser("me".equalsIgnoreCase(msg.getSender()) || "You".equalsIgnoreCase(msg.getSender()));
                        msg.setHasMedia(rs.getBoolean("has_media"));
                        
                        Long referenceId = rs.getObject("reference_id", Long.class);
                        if (referenceId != null) {
                            msg.setReferenceId(referenceId);
                        }
                        
                        messages.add(msg);
                    }
                }
            }
        }
        
        return messages;
    }
    
    /**
     * Get categories for current conversation
     */
    private List<String> getCategoriesForCurrentConversation(
            TargetUser targetUser,
            SubTargetUser subtargetUser,
            int userId,
            boolean crossPlatformContextEnabled) throws SQLException {
        
        List<String> categories = new ArrayList<>();
        List<Integer> dialogIds = new ArrayList<>();
        
        if (crossPlatformContextEnabled) {
            dialogIds = databaseManager.getDialogIdsForTargetUser(targetUser.getTargetId(), userId);
        } else if (subtargetUser != null) {
            try (Connection conn = getConnection()) {
                Integer dialogId = findDialogForSubTarget(conn, subtargetUser, userId);
                if (dialogId != null) {
                    dialogIds.add(dialogId);
                }
            }
        }
        
        if (dialogIds.isEmpty()) {
            return categories;
        }
        
        String placeholders = String.join(",", Collections.nCopies(dialogIds.size(), "?"));
        String sql = String.format("""
            SELECT DISTINCT cg.category_name
            FROM chat_goals cg
            WHERE cg.dialog_id IN (%s)
            ORDER BY cg.relevance_score DESC
        """, placeholders);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < dialogIds.size(); i++) {
                pstmt.setInt(i + 1, dialogIds.get(i));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(rs.getString("category_name"));
                }
            }
        }
        
        return categories;
    }
    
    /**
     * Infer categories from goal if no categories exist yet
     */
    private List<String> inferCategoriesFromGoal(String desiredOutcome) {
        List<String> categories = new ArrayList<>();
        if (desiredOutcome == null || desiredOutcome.trim().isEmpty()) {
            return Arrays.asList("general", "conversation");
        }
        
        String lower = desiredOutcome.toLowerCase();
        // Simple keyword-based inference (can be enhanced with AI)
        if (lower.contains("date") || lower.contains("romantic") || lower.contains("relationship")) {
            categories.add("dating");
            categories.add("romance");
        }
        if (lower.contains("investment") || lower.contains("fund") || lower.contains("business")) {
            categories.add("business");
            categories.add("investment");
        }
        if (lower.contains("friend") || lower.contains("connection")) {
            categories.add("friendship");
            categories.add("networking");
        }
        
        if (categories.isEmpty()) {
            categories.add("general");
        }
        
        return categories;
    }
    
    /**
     * Get reference dialogs in same categories (excluding current conversation)
     */
    private Map<Integer, List<Message>> getReferenceDialogsInCategories(
            List<String> categories,
            int userId,
            int currentTargetUserId,
            SubTargetUser currentSubtargetUser,
            boolean crossPlatformContextEnabled) throws SQLException {
        
        if (categories.isEmpty()) {
            return new HashMap<>();
        }
        
        // Get dialogs in same categories, excluding current target user's dialogs
        String placeholders = String.join(",", Collections.nCopies(categories.size(), "?"));
        String sql = String.format("""
            SELECT d.id as dialog_id, 
                   MAX(cg.relevance_score) as max_relevance_score,
                   MAX(cg.success_score) as max_success_score
            FROM dialogs d
            INNER JOIN chat_goals cg ON d.id = cg.dialog_id
            WHERE d.user_id = ?
                AND d.type NOT IN ('group', 'channel', 'supergroup', 'bot')
                AND (d.is_bot IS NULL OR d.is_bot = FALSE)
                AND cg.category_name IN (%s)
                AND NOT EXISTS (
                    SELECT 1 FROM subtarget_users stu
                    JOIN target_users tu ON stu.target_user_id = tu.id
                    WHERE tu.id = ? AND d.platform_account_id = stu.platform_account_id
                    AND (stu.platform_id > 0 AND d.dialog_id = stu.platform_id 
                         OR LOWER(d.name) = LOWER(stu.username) 
                         OR LOWER(d.name) = LOWER('@' || stu.username))
                )
            GROUP BY d.id
            ORDER BY MAX(cg.relevance_score) DESC, MAX(cg.success_score) DESC
            LIMIT 50
        """, placeholders);
        
        Set<Integer> dialogIds = new LinkedHashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            for (int i = 0; i < categories.size(); i++) {
                pstmt.setString(i + 2, categories.get(i));
            }
            pstmt.setInt(categories.size() + 2, currentTargetUserId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    dialogIds.add(rs.getInt("dialog_id"));
                }
            }
        }
        
        // Load messages for these dialogs
        return loadMessagesForDialogs(dialogIds);
    }
    
    /**
     * Separate dialogs into successful (70%), failed (15%), and AI improvement (15%)
     * Includes dialog metadata (ID and name)
     */
    private void separateDialogsBySuccessWithMetadata(
            Map<Integer, List<Message>> dialogs,
            List<String> categories,
            List<DialogExample> successfulExamples,
            List<DialogExample> failedExamples,
            List<DialogExample> aiImprovementExamples) throws SQLException {
        
        if (dialogs.isEmpty() || categories.isEmpty()) {
            return;
        }
        
        // Get success scores and dialog names from database
        Map<Integer, Double> successScores = getSuccessScoresForDialogs(
            new ArrayList<>(dialogs.keySet()), categories);
        Map<Integer, String> dialogNames = getDialogNames(new ArrayList<>(dialogs.keySet()));
        
        List<DialogWithScore> scoredDialogs = new ArrayList<>();
        for (Map.Entry<Integer, List<Message>> entry : dialogs.entrySet()) {
            double score = successScores.getOrDefault(entry.getKey(), 0.5);
            scoredDialogs.add(new DialogWithScore(entry.getKey(), entry.getValue(), score));
        }
        
        // Sort by score
        scoredDialogs.sort((a, b) -> Double.compare(b.score, a.score));
        
        // Separate: successful (>= 0.7), failed (< 0.3)
        for (DialogWithScore dialog : scoredDialogs) {
            DialogExample example = new DialogExample(
                dialog.dialogId, 
                dialog.messages, 
                dialogNames.getOrDefault(dialog.dialogId, null)
            );
            if (dialog.score >= 0.7) {
                successfulExamples.add(example);
            } else if (dialog.score < 0.3) {
                failedExamples.add(example);
            }
        }
        
        // AI improvement examples: use top successful ones (can be enhanced with AI-generated improvements)
        int aiCount = Math.min(aiImprovementExamples.size() + (int)(scoredDialogs.size() * 0.15), successfulExamples.size());
        for (int i = 0; i < aiCount && i < successfulExamples.size(); i++) {
            aiImprovementExamples.add(successfulExamples.get(i));
        }
    }
    
    /**
     * Get dialog names for dialog IDs
     */
    private Map<Integer, String> getDialogNames(List<Integer> dialogIds) throws SQLException {
        Map<Integer, String> names = new HashMap<>();
        if (dialogIds.isEmpty()) {
            return names;
        }
        
        String placeholders = String.join(",", Collections.nCopies(dialogIds.size(), "?"));
        String sql = String.format("""
            SELECT id, name FROM dialogs WHERE id IN (%s)
        """, placeholders);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < dialogIds.size(); i++) {
                pstmt.setInt(i + 1, dialogIds.get(i));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    names.put(rs.getInt("id"), rs.getString("name"));
                }
            }
        }
        
        return names;
    }
    
    /**
     * Get success scores for dialogs from database
     */
    private Map<Integer, Double> getSuccessScoresForDialogs(
            List<Integer> dialogIds, List<String> categories) throws SQLException {
        
        Map<Integer, Double> scores = new HashMap<>();
        if (dialogIds.isEmpty() || categories.isEmpty()) {
            return scores;
        }
        
        String dialogPlaceholders = String.join(",", Collections.nCopies(dialogIds.size(), "?"));
        String categoryPlaceholders = String.join(",", Collections.nCopies(categories.size(), "?"));
        String sql = String.format("""
            SELECT dialog_id, MAX(success_score) as max_success_score
            FROM chat_goals
            WHERE dialog_id IN (%s)
              AND category_name IN (%s)
            GROUP BY dialog_id
        """, dialogPlaceholders, categoryPlaceholders);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int index = 1;
            for (Integer dialogId : dialogIds) {
                pstmt.setInt(index++, dialogId);
            }
            for (String category : categories) {
                pstmt.setString(index++, category);
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    scores.put(rs.getInt("dialog_id"), rs.getDouble("max_success_score"));
                }
            }
        }
        
        return scores;
    }
    
    /**
     * Format conversation example for context
     */
    private void formatConversationExample(StringBuilder context, List<Message> messages, String type) {
        context.append("Type: ").append(type).append("\n");
        context.append("Full Conversation:\n");
        for (Message msg : messages) {
            if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                context.append("[ID: ").append(msg.getId()).append("] ");
                if (msg.getReferenceId() != null) {
                    context.append("[REPLY TO ID: ").append(msg.getReferenceId()).append("] ");
                }
                context.append(msg.isFromUser() ? "You" : "Them")
                      .append(": ")
                      .append(msg.getContent())
                      .append("\n");
            }
        }
    }
    
    /**
     * Find dialog for SubTarget User
     */
    private Integer findDialogForSubTarget(Connection conn, SubTargetUser subtargetUser, int userId) throws SQLException {
        // Try to find by platform_id first
        if (subtargetUser.getPlatformId() != null && subtargetUser.getPlatformId() > 0) {
            String sql = """
                SELECT id FROM dialogs
                WHERE user_id = ? AND platform_account_id = ? AND dialog_id = ? AND type = 'private'
                LIMIT 1
            """;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, subtargetUser.getPlatformAccountId());
                pstmt.setLong(3, subtargetUser.getPlatformId());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        }
        
        // Try to find by username
        if (subtargetUser.getUsername() != null && !subtargetUser.getUsername().trim().isEmpty()) {
            String sql = """
                SELECT id FROM dialogs
                WHERE user_id = ? AND platform_account_id = ? 
                AND (LOWER(name) = LOWER(?) OR LOWER(name) = LOWER('@' || ?))
                AND type = 'private'
                LIMIT 1
            """;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, subtargetUser.getPlatformAccountId());
                String username = subtargetUser.getUsername().replace("@", "");
                pstmt.setString(3, username);
                pstmt.setString(4, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
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
    
    /**
     * Helper class for dialogs with success scores
     */
    private static class DialogWithScore {
        final int dialogId;
        final List<Message> messages;
        final double score;
        
        DialogWithScore(int dialogId, List<Message> messages, double score) {
            this.dialogId = dialogId;
            this.messages = messages;
            this.score = score;
        }
    }
    
    /**
     * Helper class for dialog examples with metadata
     */
    private static class DialogExample {
        final int dialogId;
        final List<Message> messages;
        final String dialogName;
        
        DialogExample(int dialogId, List<Message> messages, String dialogName) {
            this.dialogId = dialogId;
            this.messages = messages;
            this.dialogName = dialogName;
        }
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
            SELECT m.dialog_id, m.message_id, m.sender, m.text, m.timestamp, m.has_media, m.reference_id
            FROM messages m
            WHERE m.dialog_id IN (%s)
            ORDER BY m.dialog_id, m.timestamp
        """, placeholders);

        Map<Integer, List<Message>> chats = new LinkedHashMap<>();
        
        try (Connection conn = getConnection();
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
                    
                    // Decrypt text if needed
                    String encryptedText = rs.getString("text");
                    if (encryptedText != null && !encryptedText.isEmpty()) {
                        try {
                            msg.setContent(com.aria.storage.SecureStorage.decrypt(encryptedText));
                        } catch (Exception e) {
                            msg.setContent(encryptedText); // Use as-is if decryption fails
                        }
                    }
                    
                    java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");
                    if (timestamp != null) {
                        msg.setTimestamp(timestamp.toLocalDateTime());
                    }
                    
                    msg.setFromUser("me".equalsIgnoreCase(msg.getSender()) || "You".equalsIgnoreCase(msg.getSender()));
                    msg.setHasMedia(rs.getBoolean("has_media"));
                    
                    Long referenceId = rs.getObject("reference_id", Long.class);
                    if (referenceId != null) {
                        msg.setReferenceId(referenceId);
                    }
                    
                    chats.computeIfAbsent(dialogId, k -> new ArrayList<>()).add(msg);
                }
            }
        }
        
        return chats;
    }
}

