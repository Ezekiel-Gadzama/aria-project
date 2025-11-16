package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import com.aria.api.dto.ConversationGoalDTO;
import com.aria.core.AriaOrchestrator;
import com.aria.core.model.ConversationGoal;
import com.aria.core.model.TargetUser;
import com.aria.service.TargetUserService;
import com.aria.service.UserService;
import com.aria.storage.DatabaseManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller for conversation management
 */
@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = "*")
public class ConversationController {

    // DatabaseManager uses static methods, so no autowiring needed

    /**
     * Initialize a conversation with a target user
     * POST /api/conversations/initialize
     */
    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<String>> initializeConversation(
            @RequestParam(value = "targetUserId") Integer targetUserId,
            @RequestBody ConversationGoalDTO goalDTO,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            
            DatabaseManager databaseManager = new DatabaseManager();
            // Create a minimal User object for UserService
            // Note: This is a placeholder - in production, get actual user from authentication context
            com.aria.core.model.User user = new com.aria.core.model.User(
                "", "", "", "", ""
            );
            UserService userService = new UserService(databaseManager, user);
            AriaOrchestrator orchestrator = new AriaOrchestrator(userService);
            
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            
            if (targetUser == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Target user not found"));
            }

            if (goalDTO.getDesiredOutcome() == null || goalDTO.getDesiredOutcome().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Desired outcome (goal) is required."));
            }

            ConversationGoal goal = new ConversationGoal();
            goal.setContext(goalDTO.getContext());
            goal.setDesiredOutcome(goalDTO.getDesiredOutcome());
            goal.setMeetingContext(goalDTO.getMeetingContext());
            goal.setIncludedPlatformAccountIds(goalDTO.getIncludedPlatformAccountIds());

            // Load fixed profile from target_users.profile_json and attach to goal
            String profileJson = null;
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    System.getenv("DATABASE_URL") != null
                            ? System.getenv("DATABASE_URL")
                            : "jdbc:postgresql://localhost:5432/aria",
                    System.getenv("DATABASE_USER") != null
                            ? System.getenv("DATABASE_USER")
                            : "postgres",
                    System.getenv("DATABASE_PASSWORD") != null
                            ? System.getenv("DATABASE_PASSWORD")
                            : "Ezekiel(23)")) {
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT profile_json FROM target_users WHERE id = ?")) {
                    ps.setInt(1, targetUserId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            java.sql.SQLXML xml = null;
                            profileJson = rs.getString(1);
                        }
                    }
                }
            } catch (Exception ignore) {}
            goal.setProfileJson(profileJson);

            // Persist active conversation record
            DatabaseManager.upsertActiveConversation(currentUserId, targetUserId, goalDTO.getDesiredOutcome(), goalDTO.getContext(), goalDTO.getIncludedPlatformAccountIds());

            orchestrator.initializeConversation(goal, targetUser);
            
            return ResponseEntity.ok(ApiResponse.success("Conversation initialized successfully", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error initializing conversation: " + e.getMessage()));
        }
    }

    /**
     * Get recent messages for a target's dialog, including media flags
     * GET /api/conversations/messages?targetUserId=...&userId=...&limit=100
     */
    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getMessages(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        try {
            int currentUserId = userId != null ? userId : 1;
            int lim = (limit == null || limit <= 0 || limit > 500) ? 100 : limit;

            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            if (targetUser == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            }

            com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
            if (selected == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user has no selected platform"));
            }

            java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    System.getenv("DATABASE_URL") != null
                            ? System.getenv("DATABASE_URL")
                            : "jdbc:postgresql://localhost:5432/aria",
                    System.getenv("DATABASE_USER") != null
                            ? System.getenv("DATABASE_USER")
                            : "postgres",
                    System.getenv("DATABASE_PASSWORD") != null
                            ? System.getenv("DATABASE_PASSWORD")
                            : "Ezekiel(23)")) {

                Integer dialogRowId = null;
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setInt(2, selected.getPlatformId());
                    ps.setString(3, targetUser.getName());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) dialogRowId = rs.getInt(1);
                    }
                }
                if (dialogRowId == null) {
                    return ResponseEntity.ok(ApiResponse.success("OK", out));
                }

                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT m.message_id, m.sender, m.text, m.timestamp, m.has_media, " +
                                "(SELECT id FROM media WHERE message_id = m.id LIMIT 1) as media_id " +
                                "FROM messages m WHERE m.dialog_id = ? " +
                                "ORDER BY m.message_id DESC LIMIT ?")) {
                    ps.setInt(1, dialogRowId);
                    ps.setInt(2, lim);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            java.util.Map<String, Object> row = new java.util.HashMap<>();
                            row.put("messageId", rs.getInt(1));
                            row.put("fromUser", "me".equalsIgnoreCase(rs.getString(2)));
                            row.put("text", rs.getString(3));
                            row.put("timestamp", rs.getTimestamp(4) != null ? rs.getTimestamp(4).getTime() : null);
                            row.put("hasMedia", rs.getBoolean(5));
                            Object mediaId = rs.getObject(6);
                            if (mediaId != null) {
                                row.put("mediaDownloadUrl", "/api/conversations/media/download?targetUserId=" + targetUserId + "&userId=" + currentUserId + "&messageId=" + rs.getInt(1));
                                
                                // Get media metadata (fileName, mimeType) from media table
                                try (java.sql.PreparedStatement mediaPs = conn.prepareStatement(
                                        "SELECT file_name, mime_type FROM media WHERE message_id = ? ORDER BY id ASC LIMIT 1")) {
                                    mediaPs.setInt(1, (Integer) mediaId);
                                    try (java.sql.ResultSet mediaRs = mediaPs.executeQuery()) {
                                        if (mediaRs.next()) {
                                            String fn = mediaRs.getString(1);
                                            if (fn != null && !fn.isEmpty()) {
                                                row.put("fileName", fn);
                                            }
                                            String mt = mediaRs.getString(2);
                                            if (mt != null && !mt.isEmpty()) {
                                                row.put("mimeType", mt);
                                            }
                                        }
                                    }
                                }
                            }
                            out.add(row);
                        }
                    }
                }
            }
            // reverse chronological to chronological
            java.util.Collections.reverse(out);
            return ResponseEntity.ok(ApiResponse.success("OK", out));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error fetching messages: " + e.getMessage()));
        }
    }

    /**
     * Download media for a given message (first media if multiple)
     * GET /api/conversations/media/download?targetUserId=...&userId=...&messageId=...
     */
    @GetMapping("/media/download")
    public ResponseEntity<?> downloadMedia(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam("messageId") Integer messageId
    ) {
        try {
            int currentUserId = userId != null ? userId : 1;

            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            if (targetUser == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            }
            com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
            if (selected == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user has no selected platform"));
            }

            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    System.getenv("DATABASE_URL") != null
                            ? System.getenv("DATABASE_URL")
                            : "jdbc:postgresql://localhost:5432/aria",
                    System.getenv("DATABASE_USER") != null
                            ? System.getenv("DATABASE_USER")
                            : "postgres",
                    System.getenv("DATABASE_PASSWORD") != null
                            ? System.getenv("DATABASE_PASSWORD")
                            : "Ezekiel(23)")) {

                Integer dialogRowId = null;
                // First, try to find existing dialog by name
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setInt(2, selected.getPlatformId());
                    ps.setString(3, targetUser.getName());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) dialogRowId = rs.getInt(1);
                    }
                }
                
                // If not found by name, try to find by message_id (look up which dialog this message belongs to)
                if (dialogRowId == null) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT m.dialog_id FROM messages m " +
                            "JOIN dialogs d ON m.dialog_id = d.id " +
                            "WHERE m.message_id = ? AND d.user_id = ? AND d.platform_account_id = ? " +
                            "ORDER BY m.id DESC LIMIT 1")) {
                        ps.setInt(1, messageId);
                        ps.setInt(2, currentUserId);
                        ps.setInt(3, selected.getPlatformId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) dialogRowId = rs.getInt(1);
                        }
                    }
                }
                
                if (dialogRowId == null) {
                    System.err.println("Dialog not found for targetUserId=" + targetUserId + ", userId=" + currentUserId + ", platformAccountId=" + selected.getPlatformId() + ", messageId=" + messageId);
                    return ResponseEntity.status(404)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body(ApiResponse.error("Dialog not found"));
                }

                Integer internalMsgId = null;
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM messages WHERE dialog_id = ? AND message_id = ?")) {
                    ps.setInt(1, dialogRowId);
                    ps.setInt(2, messageId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) internalMsgId = rs.getInt(1);
                    }
                }
                if (internalMsgId == null) {
                    System.err.println("Message not found: messageId=" + messageId + ", dialogRowId=" + dialogRowId);
                    // Try to find the message without requiring the dialog (in case dialog lookup failed)
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT m.id FROM messages m " +
                            "JOIN dialogs d ON m.dialog_id = d.id " +
                            "WHERE m.message_id = ? AND d.user_id = ? AND d.platform_account_id = ? " +
                            "ORDER BY m.id DESC LIMIT 1")) {
                        ps.setInt(1, messageId);
                        ps.setInt(2, currentUserId);
                        ps.setInt(3, selected.getPlatformId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                internalMsgId = rs.getInt(1);
                                // Also get the dialog_id
                                try (java.sql.PreparedStatement dialogPs = conn.prepareStatement(
                                        "SELECT dialog_id FROM messages WHERE id = ?")) {
                                    dialogPs.setInt(1, internalMsgId);
                                    try (java.sql.ResultSet dialogRs = dialogPs.executeQuery()) {
                                        if (dialogRs.next()) {
                                            dialogRowId = dialogRs.getInt(1);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (internalMsgId == null) {
                        return ResponseEntity.status(404)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .body(ApiResponse.error("Message not found"));
                    }
                }

                String filePath = null;
                String fileName = "media.bin";
                String mimeType = "application/octet-stream";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT file_path, file_name, mime_type FROM media WHERE message_id = ? ORDER BY id ASC LIMIT 1")) {
                    ps.setInt(1, internalMsgId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            filePath = rs.getString(1);
                            String fn = rs.getString(2);
                            if (fn != null && !fn.isEmpty()) fileName = fn;
                            String mt = rs.getString(3);
                            if (mt != null && !mt.isEmpty()) mimeType = mt;
                        }
                    }
                }
                if (filePath == null) {
                    System.err.println("No media found for message ID: " + messageId + ", internal message ID: " + internalMsgId);
                    return ResponseEntity.status(404)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body(ApiResponse.error("No media for this message"));
                }

                // Handle both absolute and relative paths
                // Normalize path separators first (convert backslashes to forward slashes if needed)
                String normalizedPath = filePath.replace("\\", "/");
                
                java.nio.file.Path path = null;
                java.util.List<String> triedPaths = new java.util.ArrayList<>();
                
                // Try 1: Use path as-is (if absolute)
                java.nio.file.Path testPath = java.nio.file.Paths.get(normalizedPath);
                if (testPath.isAbsolute()) {
                    triedPaths.add(testPath.toString());
                    if (java.nio.file.Files.exists(testPath)) {
                        path = testPath;
                    }
                }
                
                // Try 2: Try with /app prefix (for Docker)
                if (path == null) {
                    testPath = java.nio.file.Paths.get("/app", normalizedPath);
                    triedPaths.add(testPath.toString());
                    if (java.nio.file.Files.exists(testPath)) {
                        path = testPath;
                    }
                }
                
                // Try 3: Try from current working directory
                if (path == null) {
                    String userDir = System.getProperty("user.dir");
                    testPath = java.nio.file.Paths.get(userDir, normalizedPath);
                    triedPaths.add(testPath.toString());
                    if (java.nio.file.Files.exists(testPath)) {
                        path = testPath;
                    }
                }
                
                // Try 4: Try as relative path from current directory
                if (path == null) {
                    testPath = java.nio.file.Paths.get(normalizedPath);
                    triedPaths.add(testPath.toString());
                    if (java.nio.file.Files.exists(testPath)) {
                        path = testPath;
                    }
                }
                
                // Try 5: Handle Windows-style paths in Docker (if running on Windows host but in Linux container)
                if (path == null && normalizedPath.contains("media/")) {
                    // Extract just the media part and try from /app
                    int mediaIdx = normalizedPath.indexOf("media/");
                    if (mediaIdx >= 0) {
                        String mediaPart = normalizedPath.substring(mediaIdx);
                        testPath = java.nio.file.Paths.get("/app", mediaPart);
                        triedPaths.add(testPath.toString());
                        if (java.nio.file.Files.exists(testPath)) {
                            path = testPath;
                        }
                    }
                }
                
                if (path == null || !java.nio.file.Files.exists(path)) {
                    System.err.println("=== File not found error ===");
                    System.err.println("Original filePath from DB: " + filePath);
                    System.err.println("Normalized path: " + normalizedPath);
                    System.err.println("Current working directory: " + System.getProperty("user.dir"));
                    System.err.println("Tried paths:");
                    for (String tried : triedPaths) {
                        System.err.println("  - " + tried + " (exists: " + java.nio.file.Files.exists(java.nio.file.Paths.get(tried)) + ")");
                    }
                    System.err.println("============================");
                    return ResponseEntity.status(404)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body(ApiResponse.error("File not found on disk. Path from DB: " + filePath));
                }
                
                byte[] bytes = java.nio.file.Files.readAllBytes(path);
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + fileName.replace("\"", "\\\"") + "\"")
                        .header("Content-Type", mimeType)
                        .body(bytes);
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error downloading media: " + e.getMessage()));
        }
    }

    /**
     * End the active conversation for a target user
     * POST /api/conversations/end
     */
    @PostMapping("/end")
    public ResponseEntity<ApiResponse<String>> endConversation(
            @RequestParam(value = "targetUserId") Integer targetUserId,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            DatabaseManager.endActiveConversation(currentUserId, targetUserId);
            return ResponseEntity.ok(ApiResponse.success("Conversation ended", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error ending conversation: " + e.getMessage()));
        }
    }

    /**
     * Check if there is an active conversation
     * GET /api/conversations/active?targetUserId=...&userId=...
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<Boolean>> isActiveConversation(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam(value = "userId", required = false) Integer userId
    ) {
        try {
            int currentUserId = userId != null ? userId : 1;
            boolean active = DatabaseManager.getActiveConversation(currentUserId, targetUserId) != null;
            return ResponseEntity.ok(ApiResponse.success("OK", active));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error checking active conversation: " + e.getMessage()));
        }
    }

    /**
     * Generate a response for an incoming message
     * POST /api/conversations/respond
     * Returns the sent message data so UI can display it immediately
     */
    @PostMapping("/respond")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> generateResponse(
            @RequestParam(value = "targetUserId") Integer targetUserId,
            @RequestBody String incomingMessage,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);

            if (targetUser == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            }

            if (incomingMessage == null || incomingMessage.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Message is required"));
            }

            // Determine selected platform/account
            com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
            if (selected == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user has no selected platform"));
            }

            com.aria.platform.telegram.TelegramConnector.SendMessageResult sendResult = null;
            if (selected.getPlatform() == com.aria.platform.Platform.TELEGRAM) {
                // Reuse connector from registry for this account
                DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
                if (acc == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
                }
                com.aria.platform.telegram.TelegramConnector connector =
                        (com.aria.platform.telegram.TelegramConnector)
                        com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
                sendResult = connector.sendMessageAndGetResult(selected.getUsername(), incomingMessage);
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Sending not yet supported for platform: " + selected.getPlatform()));
            }

            if (sendResult == null || !sendResult.success || sendResult.messageId == null || sendResult.messageId < 0) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to send message"));
            }

            Long telegramMessageId = sendResult.messageId;
            Long peerId = sendResult.peerId;

            // Get or create dialog and save the message to the database
            Integer dialogRowId = null;
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    System.getenv("DATABASE_URL") != null
                            ? System.getenv("DATABASE_URL")
                            : "jdbc:postgresql://localhost:5432/aria",
                    System.getenv("DATABASE_USER") != null
                            ? System.getenv("DATABASE_USER")
                            : "postgres",
                    System.getenv("DATABASE_PASSWORD") != null
                            ? System.getenv("DATABASE_PASSWORD")
                            : "Ezekiel(23)")) {
                
                // First, try to find existing dialog by name
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setInt(2, selected.getPlatformId());
                    ps.setString(3, targetUser.getName());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dialogRowId = rs.getInt(1);
                        }
                    }
                }
                
                // If no dialog found and we have peer ID, try to find by peer ID
                if (dialogRowId == null && peerId != null && peerId > 0) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND dialog_id = ? LIMIT 1")) {
                        ps.setInt(1, currentUserId);
                        ps.setInt(2, selected.getPlatformId());
                        ps.setLong(3, peerId);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                dialogRowId = rs.getInt(1);
                            }
                        }
                    }
                }
                
                // If still no dialog, create one using peer ID if available
                if (dialogRowId == null && peerId != null && peerId > 0) {
                    try {
                        dialogRowId = DatabaseManager.saveDialog(
                            currentUserId,
                            selected.getPlatformId(),
                            peerId,
                            targetUser.getName(),
                            "private",
                            0, // message_count
                            0  // media_count
                        );
                        System.out.println("Created new dialog for target: " + targetUser.getName() + ", dialogId=" + dialogRowId);
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to create dialog: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // Save the message if we have a dialog
                if (dialogRowId != null && telegramMessageId > 0) {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    try {
                        DatabaseManager.saveMessage(
                            dialogRowId,
                            telegramMessageId,
                            "me",
                            incomingMessage,
                            now,
                            false // hasMedia
                        );
                        System.out.println("Saved sent message to database: messageId=" + telegramMessageId + ", dialogId=" + dialogRowId);
                    } catch (Exception e) {
                        // Log but don't fail - message was sent, just couldn't save to DB
                        System.err.println("Warning: Failed to save sent message to database: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                // Log but don't fail - message was sent successfully
                System.err.println("Warning: Error saving sent message to database: " + e.getMessage());
                e.printStackTrace();
            }

            // Return the message data so UI can display it immediately
            java.util.Map<String, Object> messageData = new java.util.HashMap<>();
            messageData.put("messageId", telegramMessageId.intValue());
            messageData.put("fromUser", true);
            messageData.put("text", incomingMessage);
            messageData.put("timestamp", System.currentTimeMillis());
            messageData.put("hasMedia", false);

            return ResponseEntity.ok(ApiResponse.success("Message sent", messageData));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error generating response: " + e.getMessage()));
        }
    }

    /**
     * Edit the last outgoing message to the target (Telegram)
     * POST /api/conversations/editLast?targetUserId=...&userId=...
     * body: text/plain (new message text)
     */
    @PostMapping("/editLast")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> editLastMessage(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestBody String newText,
            @RequestParam(value = "userId", required = false) Integer userId
    ) {
        try {
            int currentUserId = userId != null ? userId : 1;
            if (newText == null || newText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("New text is required"));
            }

            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            if (targetUser == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            }

            com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
            if (selected == null || selected.getPlatform() != com.aria.platform.Platform.TELEGRAM) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Editing supported only for Telegram for now"));
            }

            // Resolve platform account
            DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
            if (acc == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
            }

            // Find dialog row id (our internal id) for this peer and account
            Integer dialogsRowId = null;
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    System.getenv("DATABASE_URL") != null
                            ? System.getenv("DATABASE_URL")
                            : "jdbc:postgresql://localhost:5432/aria",
                    System.getenv("DATABASE_USER") != null
                            ? System.getenv("DATABASE_USER")
                            : "postgres",
                    System.getenv("DATABASE_PASSWORD") != null
                            ? System.getenv("DATABASE_PASSWORD")
                            : "Ezekiel(23)")) {
                // We assume targetUser stores the telegram peer id in profile_json or we have mapping by name.
                // Fallback: find a private dialog with name matching targetUser.getName()
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type = 'private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setInt(2, selected.getPlatformId());
                    ps.setString(3, targetUser.getName());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) dialogsRowId = rs.getInt(1);
                    }
                }

                if (dialogsRowId == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Dialog not found for target"));
                }

                // Get last outgoing message id ('me')
                Integer lastMsgId = null;
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT message_id FROM messages WHERE dialog_id = ? AND sender = 'me' ORDER BY message_id DESC LIMIT 1")) {
                    ps.setInt(1, dialogsRowId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) lastMsgId = rs.getInt(1);
                    }
                }
                if (lastMsgId == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("No outgoing messages to edit"));
                }

                // Call connector to edit
                com.aria.platform.PlatformConnector connector =
                        com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
                if (connector instanceof com.aria.platform.telegram.TelegramConnector tg) {
                    boolean ok = tg.editMessage(selected.getUsername(), lastMsgId, newText);
                    if (ok) {
                        // Update the message in the database after successful edit in Telegram (encrypt it like saveMessage does)
                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                "UPDATE messages SET text = ? WHERE dialog_id = ? AND message_id = ?")) {
                            String encryptedText = com.aria.storage.SecureStorage.encrypt(newText);
                            ps.setString(1, encryptedText);
                            ps.setInt(2, dialogsRowId);
                            ps.setInt(3, lastMsgId);
                            ps.executeUpdate();
                        } catch (Exception e) {
                            // Log but don't fail - message was edited in Telegram
                            System.err.println("Warning: Failed to update message in database: " + e.getMessage());
                        }
                        
                        // Return updated message data
                        java.util.Map<String, Object> messageData = new java.util.HashMap<>();
                        messageData.put("messageId", lastMsgId);
                        messageData.put("fromUser", true);
                        messageData.put("text", newText);
                        messageData.put("edited", true);
                        messageData.put("timestamp", System.currentTimeMillis());
                        messageData.put("hasMedia", false);
                        
                        return ResponseEntity.ok(ApiResponse.success("Edited", messageData));
                    } else {
                        return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to edit message"));
                    }
                } else {
                    return ResponseEntity.internalServerError().body(ApiResponse.error("Connector not available"));
                }
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error editing message: " + e.getMessage()));
        }
    }

    /**
     * Edit a specific message by messageId
     * POST /api/conversations/edit?targetUserId=...&messageId=...&userId=...
     * body: text/plain new text
     */
    @PostMapping("/edit")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> editMessage(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam("messageId") Integer messageId,
            @RequestBody String newText,
            @RequestParam(value = "userId", required = false) Integer userId
    ) {
        try {
            int currentUserId = userId != null ? userId : 1;
            if (newText == null || newText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("New text is required"));
            }
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            if (targetUser == null) return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
            if (selected == null || selected.getPlatform() != com.aria.platform.Platform.TELEGRAM) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Editing supported only for Telegram for now"));
            }
            DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
            if (acc == null) return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
            com.aria.platform.PlatformConnector connector =
                    com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
            if (connector instanceof com.aria.platform.telegram.TelegramConnector tg) {
                boolean ok = tg.editMessage(selected.getUsername(), messageId, newText);
                if (ok) {
                    // Update the message in the database after successful edit in Telegram
                    try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                            System.getenv("DATABASE_URL") != null
                                    ? System.getenv("DATABASE_URL")
                                    : "jdbc:postgresql://localhost:5432/aria",
                            System.getenv("DATABASE_USER") != null
                                    ? System.getenv("DATABASE_USER")
                                    : "postgres",
                            System.getenv("DATABASE_PASSWORD") != null
                                    ? System.getenv("DATABASE_PASSWORD")
                                    : "Ezekiel(23)")) {
                        
                        // Find dialog row id
                        Integer dialogsRowId = null;
                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type = 'private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                            ps.setInt(1, currentUserId);
                            ps.setInt(2, selected.getPlatformId());
                            ps.setString(3, targetUser.getName());
                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) dialogsRowId = rs.getInt(1);
                            }
                        }
                        
                        // Update message text in database (encrypt it like saveMessage does)
                        if (dialogsRowId != null) {
                            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                    "UPDATE messages SET text = ? WHERE dialog_id = ? AND message_id = ?")) {
                                String encryptedText = com.aria.storage.SecureStorage.encrypt(newText);
                                ps.setString(1, encryptedText);
                                ps.setInt(2, dialogsRowId);
                                ps.setInt(3, messageId);
                                ps.executeUpdate();
                            }
                        }
                    } catch (Exception e) {
                        // Log but don't fail - message was edited in Telegram
                        System.err.println("Warning: Failed to update message in database: " + e.getMessage());
                    }
                    
                    // Return updated message data
                    java.util.Map<String, Object> messageData = new java.util.HashMap<>();
                    messageData.put("messageId", messageId);
                    messageData.put("fromUser", true);
                    messageData.put("text", newText);
                    messageData.put("edited", true);
                    messageData.put("timestamp", System.currentTimeMillis());
                    messageData.put("hasMedia", false);
                    
                    return ResponseEntity.ok(ApiResponse.success("Edited", messageData));
                } else {
                    return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to edit message"));
                }
            }
            return ResponseEntity.internalServerError().body(ApiResponse.error("Connector not available"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error editing message: " + e.getMessage()));
        }
    }

    /**
     * Delete a specific message by messageId
     * DELETE /api/conversations/message?targetUserId=...&messageId=...&userId=...
     */
    @DeleteMapping("/message")
    public ResponseEntity<ApiResponse<String>> deleteMessage(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam("messageId") Integer messageId,
            @RequestParam(value = "userId", required = false) Integer userId
    ) {
        try {
            int currentUserId = userId != null ? userId : 1;
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            if (targetUser == null) return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
            if (selected == null || selected.getPlatform() != com.aria.platform.Platform.TELEGRAM) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Deletion supported only for Telegram for now"));
            }
            DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
            if (acc == null) return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
            com.aria.platform.PlatformConnector connector =
                    com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
            if (connector instanceof com.aria.platform.telegram.TelegramConnector tg) {
                boolean ok = tg.deleteMessage(selected.getUsername(), messageId);
                if (ok) {
                    // Delete the message from the database after successful deletion in Telegram
                    try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                            System.getenv("DATABASE_URL") != null
                                    ? System.getenv("DATABASE_URL")
                                    : "jdbc:postgresql://localhost:5432/aria",
                            System.getenv("DATABASE_USER") != null
                                    ? System.getenv("DATABASE_USER")
                                    : "postgres",
                            System.getenv("DATABASE_PASSWORD") != null
                                    ? System.getenv("DATABASE_PASSWORD")
                                    : "Ezekiel(23)")) {
                        
                        // Find dialog row id
                        Integer dialogsRowId = null;
                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type = 'private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                            ps.setInt(1, currentUserId);
                            ps.setInt(2, selected.getPlatformId());
                            ps.setString(3, targetUser.getName());
                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) dialogsRowId = rs.getInt(1);
                            }
                        }
                        
                        // Delete message from database
                        if (dialogsRowId != null) {
                            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                    "DELETE FROM messages WHERE dialog_id = ? AND message_id = ?")) {
                                ps.setInt(1, dialogsRowId);
                                ps.setInt(2, messageId);
                                int deleted = ps.executeUpdate();
                                System.out.println("Deleted message from database: messageId=" + messageId + ", dialogId=" + dialogsRowId + ", rowsDeleted=" + deleted);
                            }
                        }
                    } catch (Exception e) {
                        // Log but don't fail - message was deleted in Telegram
                        System.err.println("Warning: Failed to delete message from database: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    return ResponseEntity.ok(ApiResponse.success("Deleted", null));
                } else {
                    return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to delete message from Telegram"));
                }
            }
            return ResponseEntity.internalServerError().body(ApiResponse.error("Connector not available"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error deleting message: " + e.getMessage()));
        }
    }

    /**
     * Start chat ingestion for a platform
     * POST /api/conversations/ingest
     */
    @PostMapping("/ingest")
    public ResponseEntity<ApiResponse<String>> startIngestion(
            @RequestParam(value = "platform") String platform,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            
            DatabaseManager databaseManager = new DatabaseManager();
            // Create a minimal User object for UserService
            // Note: This is a placeholder - in production, get actual user from authentication context
            com.aria.core.model.User user = new com.aria.core.model.User(
                "", "", "", "", ""
            );
            UserService userService = new UserService(databaseManager, user);
            AriaOrchestrator orchestrator = new AriaOrchestrator(userService);
            
            // TODO: Initialize platform connector and start ingestion
            orchestrator.startChatIngestion();
            
            return ResponseEntity.ok(ApiResponse.success("Chat ingestion started", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error starting ingestion: " + e.getMessage()));
        }
    }

    /**
     * Send media to target via platform connector (Telegram supported)
     * POST /api/conversations/sendMedia?targetUserId=123&userId=1
     * multipart/form-data: file
     */
    @PostMapping(value = "/sendMedia", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> sendMedia(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file
    ) {
        try {
            int currentUserId = userId != null ? userId : 1;
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("File is required"));
            }

            // Load target user
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            if (targetUser == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            }

            // Determine platform and account id
            com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
            if (selected == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user has no selected platform"));
            }
            com.aria.platform.Platform platform = selected.getPlatform();
            int accountId = selected.getPlatformId();

            // Determine MIME type and file name
            String mimeType = file.getContentType();
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "application/octet-stream";
            }
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isEmpty()) {
                fileName = "media.bin";
            }

            DatabaseManager.PlatformAccount acc = null;
            if (platform == com.aria.platform.Platform.TELEGRAM) {
                acc = DatabaseManager.getPlatformAccountById(accountId);
                if (acc == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
                }
            }

            // Save file to media folder structure: media/<platform>/user_<username>/<chat_name>_chat/<filename>
            String userLabel = (acc != null && acc.username != null && !acc.username.isBlank()) ? acc.username : 
                              (acc != null && acc.number != null ? acc.number : "unknown");
            String chatName = targetUser.getName().replaceAll("[^A-Za-z0-9_]", "_");
            java.nio.file.Path mediaDir = java.nio.file.Paths.get("media", "telegramConnector", "user_" + userLabel, chatName + "_chat");
            java.nio.file.Files.createDirectories(mediaDir);
            
            // Create unique filename to avoid conflicts
            String baseName = fileName;
            int lastDot = baseName.lastIndexOf('.');
            String nameWithoutExt = lastDot > 0 ? baseName.substring(0, lastDot) : baseName;
            String ext = lastDot > 0 ? baseName.substring(lastDot) : "";
            java.nio.file.Path mediaFile = mediaDir.resolve(baseName);
            int counter = 1;
            while (java.nio.file.Files.exists(mediaFile)) {
                mediaFile = mediaDir.resolve(nameWithoutExt + "_" + counter + ext);
                counter++;
            }
            
            // Save uploaded file to media folder
            java.nio.file.Files.write(mediaFile, file.getBytes());
            
            // Get absolute path for Telegram (for sending)
            String absoluteMediaPath = mediaFile.toAbsolutePath().toString().replace("\\", "/");
            
            // Create relative path for database storage (to avoid absolute path issues)
            String mediaFilePath = mediaFile.toString().replace("\\", "/");
            // If path is absolute, try to make it relative to a known base (media folder)
            if (mediaFilePath.startsWith("/")) {
                // Try to extract relative part from /app/media/... or similar
                int mediaIdx = mediaFilePath.indexOf("media/");
                if (mediaIdx > 0) {
                    mediaFilePath = mediaFilePath.substring(mediaIdx);
                }
            }
            // Remove any leading slashes to make it relative
            while (mediaFilePath.startsWith("/")) {
                mediaFilePath = mediaFilePath.substring(1);
            }
            
            System.out.println("Saved media file:");
            System.out.println("  Absolute path (for Telegram): " + absoluteMediaPath);
            System.out.println("  Relative path (for DB): " + mediaFilePath);
            System.out.println("  Actual file exists: " + java.nio.file.Files.exists(mediaFile));

            // Send media using the absolute path
            com.aria.platform.telegram.TelegramConnector.SendMessageResult sendResult = null;
            if (platform == com.aria.platform.Platform.TELEGRAM) {
                com.aria.platform.telegram.TelegramConnector connector =
                        (com.aria.platform.telegram.TelegramConnector)
                        com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
                sendResult = connector.sendMediaAndGetResult(selected.getUsername(), absoluteMediaPath);
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Media sending not yet supported for platform: " + platform));
            }

            if (sendResult == null || !sendResult.success || sendResult.messageId == null || sendResult.messageId < 0) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to send media"));
            }

            Long telegramMessageId = sendResult.messageId;
            Long peerId = sendResult.peerId;

            // Get or create dialog and save the media message to the database
            Integer dialogRowId = null;
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    System.getenv("DATABASE_URL") != null
                            ? System.getenv("DATABASE_URL")
                            : "jdbc:postgresql://localhost:5432/aria",
                    System.getenv("DATABASE_USER") != null
                            ? System.getenv("DATABASE_USER")
                            : "postgres",
                    System.getenv("DATABASE_PASSWORD") != null
                            ? System.getenv("DATABASE_PASSWORD")
                            : "Ezekiel(23)")) {
                
                // First, try to find existing dialog by name
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setInt(2, selected.getPlatformId());
                    ps.setString(3, targetUser.getName());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dialogRowId = rs.getInt(1);
                        }
                    }
                }
                
                // If no dialog found and we have peer ID, try to find by peer ID
                if (dialogRowId == null && peerId != null && peerId > 0) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND dialog_id = ? LIMIT 1")) {
                        ps.setInt(1, currentUserId);
                        ps.setInt(2, selected.getPlatformId());
                        ps.setLong(3, peerId);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                dialogRowId = rs.getInt(1);
                            }
                        }
                    }
                }
                
                // If still no dialog, create one using peer ID if available
                if (dialogRowId == null && peerId != null && peerId > 0) {
                    try {
                        dialogRowId = DatabaseManager.saveDialog(
                            currentUserId,
                            selected.getPlatformId(),
                            peerId,
                            targetUser.getName(),
                            "private",
                            0, // message_count
                            0  // media_count
                        );
                        System.out.println("Created new dialog for target: " + targetUser.getName() + ", dialogId=" + dialogRowId);
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to create dialog: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // Save the media message if we have a dialog
                Integer internalMessageId = null;
                if (dialogRowId != null && telegramMessageId > 0) {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    try {
                        internalMessageId = DatabaseManager.saveMessage(
                            dialogRowId,
                            telegramMessageId,
                            "me",
                            null, // No text for media-only messages
                            now,
                            true // hasMedia
                        );
                        System.out.println("Saved sent media message to database: messageId=" + telegramMessageId + ", dialogId=" + dialogRowId);
                        
                        // Save media metadata (we don't have the actual file path since it was sent from temp file)
                        // The media will be downloaded by ingestion later, but we can save a placeholder
                        if (internalMessageId != null) {
                            try {
                                String mediaType = "document";
                                if (mimeType != null) {
                                    if (mimeType.startsWith("image/")) mediaType = "photo";
                                    else if (mimeType.startsWith("video/")) mediaType = "video";
                                    else if (mimeType.startsWith("audio/")) mediaType = "audio";
                                }
                                
                                // Save media metadata with the actual file path
                                DatabaseManager.saveMedia(
                                    internalMessageId,
                                    mediaType,
                                    mediaFilePath,
                                    fileName,
                                    file.getSize(),
                                    mimeType
                                );
                            } catch (Exception e) {
                                System.err.println("Warning: Failed to save media metadata: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        // Log but don't fail - message was sent, just couldn't save to DB
                        System.err.println("Warning: Failed to save sent media message to database: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                // Log but don't fail - message was sent successfully
                System.err.println("Warning: Error saving sent media message to database: " + e.getMessage());
                e.printStackTrace();
            }

            // Return the message data so UI can display it immediately
            java.util.Map<String, Object> messageData = new java.util.HashMap<>();
            messageData.put("messageId", telegramMessageId.intValue());
            messageData.put("fromUser", true);
            messageData.put("text", null);
            messageData.put("timestamp", System.currentTimeMillis());
            messageData.put("hasMedia", true);
            messageData.put("fileName", fileName);
            messageData.put("mimeType", mimeType);
            // Add media download URL if we have the message in database
            if (dialogRowId != null) {
                messageData.put("mediaDownloadUrl", "/api/conversations/media/download?targetUserId=" + targetUserId + "&userId=" + currentUserId + "&messageId=" + telegramMessageId);
            }

            return ResponseEntity.ok(ApiResponse.success("Media sent", messageData));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error sending media: " + e.getMessage()));
        }
    }
}

