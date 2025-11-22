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
import java.util.Map;

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
                
                // Strategy 1: Try to get entity ID from Telegram and match by dialog_id (peer ID) - most reliable
                if (selected.getUsername() != null && !selected.getUsername().isEmpty() && selected.getPlatform() == com.aria.platform.Platform.TELEGRAM) {
                    try {
                        // Get platform account for API credentials
                        DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
                        if (acc != null) {
                            String usernameForLookup = selected.getUsername().startsWith("@") ? 
                                selected.getUsername().substring(1) : selected.getUsername();
                            
                            // Get entity ID from Telegram
                            ProcessBuilder pb = new ProcessBuilder("python3", "scripts/telethon/get_entity_id.py", usernameForLookup);
                            Map<String, String> env = pb.environment();
                            env.put("TELEGRAM_API_ID", acc.apiId);
                            env.put("TELEGRAM_API_HASH", acc.apiHash);
                            env.put("TELEGRAM_PHONE", acc.number);
                            if (acc.username != null && !acc.username.isEmpty()) {
                                env.put("TELEGRAM_USERNAME", acc.username);
                            }
                            // Build session path (same as in TelegramConnector)
                            String sessionPath = "Session/telegramConnector/user_" + (acc.username != null && !acc.username.isEmpty() ? acc.username.replace("@", "") : acc.number.replace("+", "")) + "/user_" + (acc.username != null && !acc.username.isEmpty() ? acc.username.replace("@", "") : acc.number.replace("+", ""));
                            env.put("TELETHON_SESSION_PATH", sessionPath);
                            pb.redirectErrorStream(true);
                            Process p = pb.start();
                            StringBuilder output = new StringBuilder();
                            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(p.getInputStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    output.append(line).append("\n");
                                }
                            }
                            int exit = p.waitFor();
                            if (exit == 0) {
                                String outputStr = output.toString().trim();
                                try {
                                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(outputStr).getAsJsonObject();
                                    if (json.has("success") && json.get("success").getAsBoolean() && json.has("entityId")) {
                                        Long entityId = json.get("entityId").getAsLong();
                                        // Try to find dialog by entity ID (dialog_id in dialogs table)
                                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                                "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND dialog_id = ? LIMIT 1")) {
                                            ps.setInt(1, currentUserId);
                                            ps.setInt(2, selected.getPlatformId());
                                            ps.setLong(3, entityId);
                                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                                if (rs.next()) {
                                                    dialogRowId = rs.getInt(1);
                                                    System.out.println("Found dialog by entity ID: " + entityId);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Failed to parse entity ID response: " + e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to get entity ID from Telegram: " + e.getMessage());
                        // Continue with other strategies
                    }
                }
                
                // Strategy 2: Try to find existing dialog by name (case-insensitive)
                if (dialogRowId == null) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND LOWER(name) = LOWER(?) ORDER BY id DESC LIMIT 1")) {
                        ps.setInt(1, currentUserId);
                        ps.setInt(2, selected.getPlatformId());
                        ps.setString(3, targetUser.getName());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) dialogRowId = rs.getInt(1);
                        }
                    }
                }
                
                // Strategy 3: Try to find by username (case-insensitive, with/without @)
                if (dialogRowId == null && selected.getUsername() != null && !selected.getUsername().isEmpty()) {
                    String usernameToMatch = selected.getUsername().startsWith("@") ? 
                        selected.getUsername().substring(1) : selected.getUsername();
                    // Try exact match (case-insensitive)
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND (LOWER(name) = LOWER(?) OR LOWER(name) = LOWER(?)) ORDER BY id DESC LIMIT 1")) {
                        ps.setInt(1, currentUserId);
                        ps.setInt(2, selected.getPlatformId());
                        ps.setString(3, usernameToMatch);
                        ps.setString(4, "@" + usernameToMatch);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) dialogRowId = rs.getInt(1);
                        }
                    }
                    // Try partial match (contains)
                    if (dialogRowId == null) {
                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND (LOWER(name) LIKE ? OR LOWER(name) LIKE ?) ORDER BY id DESC LIMIT 1")) {
                            ps.setInt(1, currentUserId);
                            ps.setInt(2, selected.getPlatformId());
                            ps.setString(3, "%" + usernameToMatch.toLowerCase() + "%");
                            ps.setString(4, "%@" + usernameToMatch.toLowerCase() + "%");
                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) dialogRowId = rs.getInt(1);
                            }
                        }
                    }
                }
                
                // Strategy 4: If still not found, list all dialogs and find best match
                if (dialogRowId == null) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id, name FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' ORDER BY last_synced DESC")) {
                        ps.setInt(1, currentUserId);
                        ps.setInt(2, selected.getPlatformId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            String targetNameLower = targetUser.getName().toLowerCase();
                            String usernameLower = selected.getUsername() != null ? selected.getUsername().toLowerCase().replace("@", "") : "";
                            while (rs.next()) {
                                String dialogName = rs.getString("name");
                                String dialogNameLower = dialogName != null ? dialogName.toLowerCase() : "";
                                // Check if dialog name matches target name or username
                                if ((dialogNameLower.contains(targetNameLower) || targetNameLower.contains(dialogNameLower)) ||
                                    (!usernameLower.isEmpty() && (dialogNameLower.contains(usernameLower) || dialogNameLower.contains("@" + usernameLower)))) {
                                    dialogRowId = rs.getInt("id");
                                    System.out.println("Found dialog by fuzzy match: " + dialogName);
                                    break;
                                }
                            }
                        }
                    }
                }
                
                if (dialogRowId == null) {
                    System.err.println("Dialog not found for targetUserId=" + targetUserId + ", userId=" + currentUserId + ", platformAccountId=" + selected.getPlatformId() + ", name=" + targetUser.getName() + ", username=" + (selected.getUsername() != null ? selected.getUsername() : "null"));
                    // Log all available dialogs for debugging
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id, name, dialog_id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' ORDER BY last_synced DESC LIMIT 10")) {
                        ps.setInt(1, currentUserId);
                        ps.setInt(2, selected.getPlatformId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            System.err.println("Available dialogs for this platform account:");
                            while (rs.next()) {
                                System.err.println("  - Dialog ID: " + rs.getInt("id") + ", Name: " + rs.getString("name") + ", Telegram ID: " + rs.getLong("dialog_id"));
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error listing dialogs: " + e.getMessage());
                    }
                    return ResponseEntity.ok(ApiResponse.success("OK", out)); // Return empty list if dialog not found
                }

                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT m.id, m.message_id, m.sender, m.text, m.timestamp, m.has_media, m.reference_id, " +
                                "CASE WHEN m.text IS NOT NULL AND EXISTS (SELECT 1 FROM messages m2 WHERE m2.dialog_id = m.dialog_id AND m2.message_id = m.message_id AND m2.timestamp < m.timestamp) THEN TRUE ELSE FALSE END as edited " +
                                "FROM messages m WHERE m.dialog_id = ? " +
                                "ORDER BY m.message_id DESC LIMIT ?")) {
                    ps.setInt(1, dialogRowId);
                    ps.setInt(2, lim);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            java.util.Map<String, Object> row = new java.util.HashMap<>();
                            int internalMessageId = rs.getInt(1); // Get internal message ID
                            row.put("messageId", rs.getInt(2)); // Telegram message_id
                            row.put("fromUser", "me".equalsIgnoreCase(rs.getString(3)));
                            
                            // Decrypt message text
                            String encryptedText = rs.getString(4);
                            String decryptedText = null;
                            if (encryptedText != null && !encryptedText.isEmpty()) {
                                try {
                                    decryptedText = com.aria.storage.SecureStorage.decrypt(encryptedText);
                                } catch (Exception e) {
                                    System.err.println("Failed to decrypt message text: " + e.getMessage());
                                    // If decryption fails, return empty string instead of encrypted text
                                    // This prevents showing encrypted base64 strings to users
                                    decryptedText = "";
                                }
                            }
                            row.put("text", decryptedText);
                            row.put("timestamp", rs.getTimestamp(5) != null ? rs.getTimestamp(5).getTime() : null);
                            row.put("hasMedia", rs.getBoolean(6));
                            
                            // Get reference_id (column 7)
                            Long referenceId = (Long) rs.getObject(7);
                            if (referenceId != null && !rs.wasNull()) {
                                row.put("referenceId", referenceId);
                            }
                            
                            // Get edited flag (column 8) - check if message was edited by comparing text with raw_json
                            // For now, we'll check if the message text differs from what would be in the original raw_json
                            // Since we don't have an edited column, we'll mark all as false and let the edit endpoint set it
                            row.put("edited", false);
                            
                            // Get media metadata (fileName, mimeType, fileSize) from media table using internal message ID
                            if (rs.getBoolean(6)) { // hasMedia
                                row.put("mediaDownloadUrl", "/api/conversations/media/download?targetUserId=" + targetUserId + "&userId=" + currentUserId + "&messageId=" + rs.getInt(2));
                                try (java.sql.PreparedStatement mediaPs = conn.prepareStatement(
                                        "SELECT file_name, mime_type, file_size FROM media WHERE message_id = ? ORDER BY id ASC LIMIT 1")) {
                                    mediaPs.setInt(1, internalMessageId); // Use internal message ID
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
                                            Long fs = (Long) mediaRs.getObject(3);
                                            if (fs != null && !mediaRs.wasNull()) {
                                                row.put("fileSize", fs);
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
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "referenceId", required = false) Long referenceId) {
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
                sendResult = connector.sendMessageAndGetResult(selected.getUsername(), incomingMessage, referenceId);
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
                            false, // hasMedia
                            referenceId // reference_id for replies
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
            // Allow empty text for media messages (to remove caption)
            // We'll check if the message has media later

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

                // Check if message has media first (declare outside try blocks)
                boolean hasMedia = false;
                String fileName = null;
                String mimeType = null;
                
                // Check if message has media before editing
                try (java.sql.PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT m.has_media FROM messages m WHERE m.dialog_id = ? AND m.message_id = ?")) {
                    checkPs.setInt(1, dialogsRowId);
                    checkPs.setInt(2, lastMsgId);
                    try (java.sql.ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next()) {
                            hasMedia = rs.getBoolean(1);
                        }
                    }
                }
                
                // Allow empty text only for media messages (to remove caption)
                if (!hasMedia && (newText == null || newText.trim().isEmpty())) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("New text is required for non-media messages"));
                }

                // Call connector to edit - use direct cast like in respond method
                com.aria.platform.telegram.TelegramConnector connector =
                        (com.aria.platform.telegram.TelegramConnector)
                        com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
                if (connector == null) {
                    return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to create connector"));
                }
                boolean ok = connector.editMessage(selected.getUsername(), lastMsgId, newText != null ? newText : "");
                if (ok) {
                    // If it has media, get media metadata
                    if (hasMedia) {
                        try (java.sql.PreparedStatement mediaPs = conn.prepareStatement(
                                "SELECT me.file_name, me.mime_type FROM media me " +
                                "JOIN messages m ON me.message_id = m.id " +
                                "WHERE m.dialog_id = ? AND m.message_id = ? LIMIT 1")) {
                            mediaPs.setInt(1, dialogsRowId);
                            mediaPs.setInt(2, lastMsgId);
                            try (java.sql.ResultSet rs = mediaPs.executeQuery()) {
                                if (rs.next()) {
                                    fileName = rs.getString(1);
                                    mimeType = rs.getString(2);
                                }
                            }
                        }
                        // If media metadata wasn't found in database, the message still has media
                        // (might have been sent via app but metadata not saved yet, or ingested before media download)
                        // We'll still return hasMedia=true so frontend can display it properly
                    }
                    
                    // Update the message in the database after successful edit in Telegram (encrypt it like saveMessage does)
                    // Use transaction to ensure atomic update
                    conn.setAutoCommit(false);
                    try {
                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                "UPDATE messages SET text = ? WHERE dialog_id = ? AND message_id = ?")) {
                            String encryptedText = newText != null && !newText.isEmpty() ? 
                                com.aria.storage.SecureStorage.encrypt(newText) : null;
                            ps.setString(1, encryptedText);
                            ps.setInt(2, dialogsRowId);
                            ps.setInt(3, lastMsgId);
                            int updated = ps.executeUpdate();
                            
                            // Commit the transaction
                            conn.commit();
                            System.out.println("Database transaction committed for message edit (editLast): messageId=" + lastMsgId + ", rowsUpdated=" + updated);
                        }
                    } catch (Exception e) {
                        // Rollback on error
                        try {
                            conn.rollback();
                            System.err.println("Rolled back transaction due to error: " + e.getMessage());
                        } catch (Exception rollbackEx) {
                            System.err.println("Failed to rollback: " + rollbackEx.getMessage());
                        }
                        // Log but don't fail - message was edited in Telegram
                        System.err.println("Warning: Failed to update message in database: " + e.getMessage());
                    } finally {
                        conn.setAutoCommit(true); // Restore auto-commit
                    }
                    
                    // Return updated message data
                    java.util.Map<String, Object> messageData = new java.util.HashMap<>();
                    messageData.put("messageId", lastMsgId);
                    messageData.put("fromUser", true);
                    messageData.put("text", newText != null && !newText.isEmpty() ? newText : null);
                    messageData.put("edited", true);
                    // Keep original timestamp, don't update to current time when editing
                    try (java.sql.PreparedStatement tsPs = conn.prepareStatement(
                            "SELECT timestamp FROM messages WHERE dialog_id = ? AND message_id = ?")) {
                        tsPs.setInt(1, dialogsRowId);
                        tsPs.setInt(2, lastMsgId);
                        try (java.sql.ResultSet tsRs = tsPs.executeQuery()) {
                            if (tsRs.next() && tsRs.getTimestamp(1) != null) {
                                messageData.put("timestamp", tsRs.getTimestamp(1).getTime());
                            } else {
                                messageData.put("timestamp", System.currentTimeMillis());
                            }
                        }
                    } catch (Exception e) {
                        messageData.put("timestamp", System.currentTimeMillis());
                    }
                    messageData.put("hasMedia", hasMedia);
                    if (hasMedia) {
                        // Always include mediaDownloadUrl when hasMedia is true
                        messageData.put("mediaDownloadUrl", "/api/conversations/media/download?targetUserId=" + targetUserId + "&userId=" + currentUserId + "&messageId=" + lastMsgId);
                        // Include fileName and mimeType if available (may be null if media metadata not in DB yet)
                        if (fileName != null) {
                            messageData.put("fileName", fileName);
                        }
                        if (mimeType != null) {
                            messageData.put("mimeType", mimeType);
                        }
                    }
                    
                    return ResponseEntity.ok(ApiResponse.success("Edited", messageData));
                } else {
                    return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to edit message"));
                }
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Error editing message: " + e.getMessage()));
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
            // Use direct cast like in respond method
            com.aria.platform.telegram.TelegramConnector connector =
                    (com.aria.platform.telegram.TelegramConnector)
                    com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
            if (connector == null) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to create connector"));
            }
            // Check if message has media first (declare outside try blocks)
            boolean hasMedia = false;
            String fileName = null;
            String mimeType = null;
            
            // Check if message has media before editing
            try (java.sql.Connection checkConn = java.sql.DriverManager.getConnection(
                    System.getenv("DATABASE_URL") != null
                            ? System.getenv("DATABASE_URL")
                            : "jdbc:postgresql://localhost:5432/aria",
                    System.getenv("DATABASE_USER") != null
                            ? System.getenv("DATABASE_USER")
                            : "postgres",
                    System.getenv("DATABASE_PASSWORD") != null
                            ? System.getenv("DATABASE_PASSWORD")
                            : "Ezekiel(23)")) {
                
                Integer dialogsRowId = null;
                try (java.sql.PreparedStatement ps = checkConn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type = 'private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setInt(2, selected.getPlatformId());
                    ps.setString(3, targetUser.getName());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) dialogsRowId = rs.getInt(1);
                    }
                }
                
                if (dialogsRowId != null) {
                    try (java.sql.PreparedStatement checkPs = checkConn.prepareStatement(
                            "SELECT m.has_media FROM messages m WHERE m.dialog_id = ? AND m.message_id = ?")) {
                        checkPs.setInt(1, dialogsRowId);
                        checkPs.setInt(2, messageId);
                        try (java.sql.ResultSet rs = checkPs.executeQuery()) {
                            if (rs.next()) {
                                hasMedia = rs.getBoolean(1);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Log but continue - we'll check again after editing
                System.err.println("Warning: Failed to check if message has media: " + e.getMessage());
            }
            
            // Allow empty text only for media messages (to remove caption)
            if (!hasMedia && (newText == null || newText.trim().isEmpty())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("New text is required for non-media messages"));
            }
            
            boolean ok = connector.editMessage(selected.getUsername(), messageId, newText != null ? newText : "");
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
                        
                        // Re-check if message has media (in case first check failed)
                        if (dialogsRowId != null) {
                            try (java.sql.PreparedStatement checkPs = conn.prepareStatement(
                                    "SELECT m.has_media FROM messages m WHERE m.dialog_id = ? AND m.message_id = ?")) {
                                checkPs.setInt(1, dialogsRowId);
                                checkPs.setInt(2, messageId);
                                try (java.sql.ResultSet rs = checkPs.executeQuery()) {
                                    if (rs.next()) {
                                        hasMedia = rs.getBoolean(1);
                                    }
                                }
                            }
                            
                            // If it has media, get media metadata
                            if (hasMedia) {
                                try (java.sql.PreparedStatement mediaPs = conn.prepareStatement(
                                        "SELECT me.file_name, me.mime_type FROM media me " +
                                        "JOIN messages m ON me.message_id = m.id " +
                                        "WHERE m.dialog_id = ? AND m.message_id = ? LIMIT 1")) {
                                    mediaPs.setInt(1, dialogsRowId);
                                    mediaPs.setInt(2, messageId);
                                    try (java.sql.ResultSet rs = mediaPs.executeQuery()) {
                                        if (rs.next()) {
                                            fileName = rs.getString(1);
                                            mimeType = rs.getString(2);
                                        }
                                    }
                                }
                                // If media metadata wasn't found in database, the message still has media
                                // (might have been sent via app but metadata not saved yet, or ingested before media download)
                                // We'll still return hasMedia=true so frontend can display it properly
                                // The mediaDownloadUrl will work because downloadMedia endpoint can fetch from Telegram if needed
                            }
                            
                            // Update message text in database (encrypt it like saveMessage does)
                            // Use transaction to ensure atomic update
                            conn.setAutoCommit(false);
                            try {
                                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                        "UPDATE messages SET text = ? WHERE dialog_id = ? AND message_id = ?")) {
                                    String encryptedText = newText != null && !newText.isEmpty() ? 
                                        com.aria.storage.SecureStorage.encrypt(newText) : null;
                                    ps.setString(1, encryptedText);
                                    ps.setInt(2, dialogsRowId);
                                    ps.setInt(3, messageId);
                                    int updated = ps.executeUpdate();
                                    
                                    // Commit the transaction
                                    conn.commit();
                                    System.out.println("Database transaction committed for message edit: messageId=" + messageId + ", rowsUpdated=" + updated);
                                }
                            } catch (Exception e) {
                                // Rollback on error
                                try {
                                    conn.rollback();
                                    System.err.println("Rolled back transaction due to error: " + e.getMessage());
                                } catch (Exception rollbackEx) {
                                    System.err.println("Failed to rollback: " + rollbackEx.getMessage());
                                }
                                throw e; // Re-throw to be caught by outer catch
                            } finally {
                                conn.setAutoCommit(true); // Restore auto-commit
                            }
                            
                            // Return updated message data
                            java.util.Map<String, Object> messageData = new java.util.HashMap<>();
                            messageData.put("messageId", messageId);
                            messageData.put("fromUser", true);
                            messageData.put("text", newText != null && !newText.isEmpty() ? newText : null);
                            messageData.put("edited", true);
                            // Keep original timestamp, don't update to current time when editing
                            try (java.sql.PreparedStatement tsPs = conn.prepareStatement(
                                    "SELECT timestamp FROM messages WHERE dialog_id = ? AND message_id = ?")) {
                                tsPs.setInt(1, dialogsRowId);
                                tsPs.setInt(2, messageId);
                                try (java.sql.ResultSet tsRs = tsPs.executeQuery()) {
                                    if (tsRs.next() && tsRs.getTimestamp(1) != null) {
                                        messageData.put("timestamp", tsRs.getTimestamp(1).getTime());
                                    } else {
                                        messageData.put("timestamp", System.currentTimeMillis());
                                    }
                                }
                            } catch (Exception e) {
                                messageData.put("timestamp", System.currentTimeMillis());
                            }
                            messageData.put("hasMedia", hasMedia);
                            if (hasMedia) {
                                // Always include mediaDownloadUrl when hasMedia is true
                                messageData.put("mediaDownloadUrl", "/api/conversations/media/download?targetUserId=" + targetUserId + "&userId=" + currentUserId + "&messageId=" + messageId);
                                // Include fileName and mimeType if available (may be null if media metadata not in DB yet)
                                if (fileName != null) {
                                    messageData.put("fileName", fileName);
                                }
                                if (mimeType != null) {
                                    messageData.put("mimeType", mimeType);
                                }
                            }
                            
                            return ResponseEntity.ok(ApiResponse.success("Edited", messageData));
                        } else {
                            // Dialog not found, but message was edited in Telegram
                            // Return basic success response
                            java.util.Map<String, Object> messageData = new java.util.HashMap<>();
                            messageData.put("messageId", messageId);
                            messageData.put("fromUser", true);
                            messageData.put("text", newText != null && !newText.isEmpty() ? newText : null);
                            messageData.put("edited", true);
                            messageData.put("timestamp", System.currentTimeMillis());
                            return ResponseEntity.ok(ApiResponse.success("Edited", messageData));
                        }
                    } catch (Exception e) {
                        // Log but don't fail - message was edited in Telegram
                        System.err.println("Warning: Failed to update message in database: " + e.getMessage());
                        // Return basic success response even if DB update failed
                        java.util.Map<String, Object> messageData = new java.util.HashMap<>();
                        messageData.put("messageId", messageId);
                        messageData.put("fromUser", true);
                        messageData.put("text", newText != null && !newText.isEmpty() ? newText : null);
                        messageData.put("edited", true);
                        messageData.put("timestamp", System.currentTimeMillis());
                        return ResponseEntity.ok(ApiResponse.success("Edited", messageData));
                    }
                } else {
                    return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to edit message"));
                }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error editing message: " + e.getMessage()));
        }
    }

    /**
     * Delete a specific message by messageId
     * DELETE /api/conversations/message?targetUserId=...&messageId=...&userId=...&revoke=true/false
     */
    @DeleteMapping("/message")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> deleteMessage(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam("messageId") Integer messageId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "revoke", defaultValue = "true") Boolean revoke
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
            // Use direct cast like in respond method
            com.aria.platform.telegram.TelegramConnector connector =
                    (com.aria.platform.telegram.TelegramConnector)
                    com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
            if (connector == null) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to create connector"));
            }
            com.aria.platform.telegram.TelegramConnector.DeleteMessageResult result = connector.deleteMessage(selected.getUsername(), messageId, revoke);
                if (result.success) {
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
                        
                        // Find dialog row id using the same robust lookup as getMessages
                        Integer dialogsRowId = null;
                        
                        // Strategy 1: Try to resolve username to entity ID and match by dialog_id
                        try {
                            // Get target username for lookup
                            String targetUsername = selected.getUsername();
                            if (targetUsername != null && !targetUsername.isBlank() && !targetUsername.startsWith("@")) {
                                targetUsername = "@" + targetUsername;
                            } else if (targetUsername == null || targetUsername.isBlank()) {
                                targetUsername = targetUser.getName(); // Fallback to name
                            }
                            
                            // Try to get entity ID from Python script
                            java.lang.ProcessBuilder entityPb = new java.lang.ProcessBuilder(
                                    "python3",
                                    "scripts/telethon/get_entity_id.py",
                                    targetUsername.replace("@", "")
                            );
                            entityPb.environment().put("TELEGRAM_API_ID", acc.apiId);
                            entityPb.environment().put("TELEGRAM_API_HASH", acc.apiHash);
                            entityPb.environment().put("TELEGRAM_PHONE", acc.number != null ? acc.number : "");
                            entityPb.environment().put("TELEGRAM_USERNAME", acc.username != null ? acc.username : "");
                            entityPb.environment().put("TELETHON_SESSION_PATH", 
                                    "Session/telegramConnector/user_" + (acc.username != null && !acc.username.isBlank() ? acc.username : 
                                                                        (acc.number != null ? acc.number : "unknown")));
                            entityPb.directory(new java.io.File(System.getProperty("user.dir")));
                            java.lang.Process entityProcess = entityPb.start();
                            java.lang.StringBuilder entityOutput = new java.lang.StringBuilder();
                            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(entityProcess.getInputStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    entityOutput.append(line).append("\n");
                                }
                            }
                            int entityExitCode = entityProcess.waitFor();
                            if (entityExitCode == 0) {
                                try {
                                    com.google.gson.JsonObject entityJson = com.google.gson.JsonParser.parseString(entityOutput.toString()).getAsJsonObject();
                                    if (entityJson.has("success") && entityJson.get("success").getAsBoolean() && entityJson.has("entityId")) {
                                        long entityId = entityJson.get("entityId").getAsLong();
                                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                                "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND dialog_id = ? AND type='private' LIMIT 1")) {
                                            ps.setInt(1, currentUserId);
                                            ps.setInt(2, selected.getPlatformId());
                                            ps.setLong(3, entityId);
                                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                                if (rs.next()) dialogsRowId = rs.getInt(1);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignore JSON parse errors, fall through to next strategy
                                }
                            }
                        } catch (Exception e) {
                            // Ignore entity ID lookup errors, fall through to next strategy
                        }
                        
                        // Strategy 2: Try to find by exact name match
                        if (dialogsRowId == null) {
                            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                    "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type = 'private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                                ps.setInt(1, currentUserId);
                                ps.setInt(2, selected.getPlatformId());
                                ps.setString(3, targetUser.getName());
                                try (java.sql.ResultSet rs = ps.executeQuery()) {
                                    if (rs.next()) dialogsRowId = rs.getInt(1);
                                }
                            }
                        }
                        
                        // Strategy 3: Try to find by username match
                        if (dialogsRowId == null && selected.getUsername() != null && !selected.getUsername().isBlank()) {
                            String usernameToMatch = selected.getUsername().toLowerCase().replace("@", "");
                            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                    "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type = 'private' AND (LOWER(name) LIKE ? OR LOWER(name) LIKE ?) ORDER BY id DESC LIMIT 1")) {
                                ps.setInt(1, currentUserId);
                                ps.setInt(2, selected.getPlatformId());
                                ps.setString(3, "%" + usernameToMatch + "%");
                                ps.setString(4, "%@" + usernameToMatch + "%");
                                try (java.sql.ResultSet rs = ps.executeQuery()) {
                                    if (rs.next()) dialogsRowId = rs.getInt(1);
                                }
                            }
                        }
                        
                        // Strategy 4: Try to find by message_id (look up which dialog this message belongs to)
                        if (dialogsRowId == null) {
                            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                    "SELECT dialog_id FROM messages WHERE message_id = ? LIMIT 1")) {
                                ps.setInt(1, messageId);
                                try (java.sql.ResultSet rs = ps.executeQuery()) {
                                    if (rs.next()) {
                                        int msgDialogId = rs.getInt(1);
                                        // Verify this dialog belongs to the correct user and platform
                                        try (java.sql.PreparedStatement verifyPs = conn.prepareStatement(
                                                "SELECT id FROM dialogs WHERE id = ? AND user_id = ? AND platform_account_id = ? LIMIT 1")) {
                                            verifyPs.setInt(1, msgDialogId);
                                            verifyPs.setInt(2, currentUserId);
                                            verifyPs.setInt(3, selected.getPlatformId());
                                            try (java.sql.ResultSet verifyRs = verifyPs.executeQuery()) {
                                                if (verifyRs.next()) {
                                                    dialogsRowId = verifyRs.getInt(1);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Delete message from database (and associated media)
                        if (dialogsRowId != null) {
                            // Use transaction to ensure atomic deletion
                            conn.setAutoCommit(false);
                            try {
                                // First, find the internal message ID to delete associated media
                                Integer internalMessageId = null;
                                try (java.sql.PreparedStatement findPs = conn.prepareStatement(
                                        "SELECT id FROM messages WHERE dialog_id = ? AND message_id = ?")) {
                                    findPs.setInt(1, dialogsRowId);
                                    findPs.setInt(2, messageId);
                                    try (java.sql.ResultSet rs = findPs.executeQuery()) {
                                        if (rs.next()) {
                                            internalMessageId = rs.getInt(1);
                                        }
                                    }
                                }
                                
                                // Delete associated media first (if any)
                                if (internalMessageId != null) {
                                    try (java.sql.PreparedStatement mediaPs = conn.prepareStatement(
                                            "DELETE FROM media WHERE message_id = ?")) {
                                        mediaPs.setInt(1, internalMessageId);
                                        int mediaDeleted = mediaPs.executeUpdate();
                                        System.out.println("Deleted " + mediaDeleted + " media record(s) for message " + internalMessageId);
                                    }
                                }
                                
                                // Delete the message
                                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                        "DELETE FROM messages WHERE dialog_id = ? AND message_id = ?")) {
                                    ps.setInt(1, dialogsRowId);
                                    ps.setInt(2, messageId);
                                    int deleted = ps.executeUpdate();
                                    System.out.println("Deleted message from database: messageId=" + messageId + ", dialogId=" + dialogsRowId + ", rowsDeleted=" + deleted);
                                    
                                    // Commit the transaction
                                    conn.commit();
                                    System.out.println("Database transaction committed for message deletion");
                                }
                            } catch (Exception e) {
                                // Rollback on error
                                try {
                                    conn.rollback();
                                    System.err.println("Rolled back transaction due to error: " + e.getMessage());
                                } catch (Exception rollbackEx) {
                                    System.err.println("Failed to rollback: " + rollbackEx.getMessage());
                                }
                                throw e; // Re-throw to be caught by outer catch
                            } finally {
                                conn.setAutoCommit(true); // Restore auto-commit
                            }
                        } else {
                            System.err.println("Warning: Could not find dialog for deletion. messageId=" + messageId + ", targetName=" + targetUser.getName());
                        }
                    } catch (Exception e) {
                        // Log but don't fail - message was deleted in Telegram
                        System.err.println("Warning: Failed to delete message from database: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    java.util.Map<String, Object> resultMap = new java.util.HashMap<>();
                    resultMap.put("success", true);
                    resultMap.put("revoked", result.revoked);
                    if (!result.revoked && revoke) {
                        resultMap.put("message", "Message deleted only for you (Telegram doesn't allow deleting for other user)");
                    }
                    return ResponseEntity.ok(ApiResponse.success("Deleted", resultMap));
                } else {
                    return ResponseEntity.internalServerError().body(ApiResponse.error(result.error != null ? result.error : "Failed to delete message from Telegram"));
                }
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
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(value = "caption", required = false) String caption,
            @org.springframework.web.bind.annotation.RequestParam(value = "referenceId", required = false) Long referenceId
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
                sendResult = connector.sendMediaAndGetResult(selected.getUsername(), absoluteMediaPath, caption, referenceId);
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
                            caption != null && !caption.isEmpty() ? caption : null, // Save caption if provided
                            now,
                            true, // hasMedia
                            referenceId // reference_id for replies
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
            messageData.put("text", caption != null && !caption.isEmpty() ? caption : null);
            messageData.put("timestamp", System.currentTimeMillis());
            messageData.put("hasMedia", true);
            messageData.put("fileName", fileName);
            messageData.put("mimeType", mimeType);
            messageData.put("fileSize", file.getSize()); // Include file size
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

    /**
     * Replace media in an existing message (delete old message and send new one with new media)
     * POST /api/conversations/replaceMedia?targetUserId=123&userId=1&messageId=456
     * multipart/form-data: file, caption (optional)
     */
    @PostMapping(value = "/replaceMedia", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> replaceMedia(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam("messageId") Integer oldMessageId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(value = "caption", required = false) String caption
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

            if (platform != com.aria.platform.Platform.TELEGRAM) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Media replacement only supported for Telegram"));
            }

            DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(accountId);
            if (acc == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
            }

            // Determine MIME type and file name
            String mimeType = file.getContentType();
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "application/octet-stream";
            }
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isEmpty()) {
                fileName = "media.bin";
            }

            // Save file to media folder structure: media/<platform>/user_<username>/<chat_name>_chat/<filename>
            String userLabel = (acc.username != null && !acc.username.isBlank()) ? acc.username : 
                              (acc.number != null ? acc.number : "unknown");
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
            
            // Get absolute path for Telegram (for editing)
            String absoluteMediaPath = mediaFile.toAbsolutePath().toString().replace("\\", "/");
            
            // Create relative path for database storage
            String mediaFilePath = mediaFile.toString().replace("\\", "/");
            if (mediaFilePath.startsWith("/")) {
                int mediaIdx = mediaFilePath.indexOf("media/");
                if (mediaIdx > 0) {
                    mediaFilePath = mediaFilePath.substring(mediaIdx);
                }
            }
            while (mediaFilePath.startsWith("/")) {
                mediaFilePath = mediaFilePath.substring(1);
            }
            
            // Edit the existing message in Telegram to replace media (using Telegram's edit functionality)
            com.aria.platform.telegram.TelegramConnector connector =
                    (com.aria.platform.telegram.TelegramConnector)
                    com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
            
            boolean edited = connector.editMediaMessage(selected.getUsername(), oldMessageId, absoluteMediaPath, caption);
            if (!edited) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to edit media message"));
            }

            // Message was edited (not deleted/recreated), so messageId stays the same
            // We use oldMessageId throughout since the message was edited in place

            // Update the existing message in the database (message was edited, not deleted/recreated)
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
                
                // Find dialog
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setInt(2, selected.getPlatformId());
                    ps.setString(3, targetUser.getName());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) dialogRowId = rs.getInt(1);
                    }
                }
                
                if (dialogRowId != null) {
                    // Update message text/caption in database
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "UPDATE messages SET text = ? WHERE dialog_id = ? AND message_id = ?")) {
                        String encryptedText = caption != null && !caption.isEmpty() ? 
                            com.aria.storage.SecureStorage.encrypt(caption) : null;
                        ps.setString(1, encryptedText);
                        ps.setInt(2, dialogRowId);
                        ps.setInt(3, oldMessageId);
                        ps.executeUpdate();
                    }
                    
                    // Update or insert media metadata
                    try {
                        // Get the internal message ID
                        Integer internalMessageId = null;
                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                "SELECT id FROM messages WHERE dialog_id = ? AND message_id = ?")) {
                            ps.setInt(1, dialogRowId);
                            ps.setInt(2, oldMessageId);
                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    internalMessageId = rs.getInt(1);
                                }
                            }
                        }
                        
                        if (internalMessageId != null) {
                            // Update existing media record or insert new one
                            String mediaType = "document";
                            if (mimeType != null) {
                                if (mimeType.startsWith("image/")) mediaType = "photo";
                                else if (mimeType.startsWith("video/")) mediaType = "video";
                                else if (mimeType.startsWith("audio/")) mediaType = "audio";
                            }
                            
                            // Try to update existing media record
                            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                    "UPDATE media SET type = ?, file_path = ?, file_name = ?, file_size = ?, mime_type = ? " +
                                    "WHERE message_id = ?")) {
                                ps.setString(1, mediaType);
                                ps.setString(2, mediaFilePath);
                                ps.setString(3, fileName);
                                ps.setLong(4, file.getSize());
                                ps.setString(5, mimeType);
                                ps.setInt(6, internalMessageId);
                                int updated = ps.executeUpdate();
                                
                                // If no row was updated, insert new media record
                                if (updated == 0) {
                                    try (java.sql.PreparedStatement insertPs = conn.prepareStatement(
                                            "INSERT INTO media (message_id, type, file_path, file_name, file_size, mime_type) " +
                                            "VALUES (?, ?, ?, ?, ?, ?)")) {
                                        insertPs.setInt(1, internalMessageId);
                                        insertPs.setString(2, mediaType);
                                        insertPs.setString(3, mediaFilePath);
                                        insertPs.setString(4, fileName);
                                        insertPs.setLong(5, file.getSize());
                                        insertPs.setString(6, mimeType);
                                        insertPs.executeUpdate();
                                    }
                                }
                            }
                            
                            System.out.println("Updated media message in database: messageId=" + oldMessageId + ", dialogId=" + dialogRowId);
                        }
                    } catch (Exception e) {
                        System.err.println("Warning: Error updating media metadata in database: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                // Return the updated message data (same messageId, edited)
                java.util.Map<String, Object> messageData = new java.util.HashMap<>();
                messageData.put("messageId", oldMessageId); // Same message ID (was edited, not replaced)
                messageData.put("fromUser", true);
                messageData.put("text", caption != null && !caption.isEmpty() ? caption : null);
                messageData.put("timestamp", System.currentTimeMillis());
                messageData.put("hasMedia", true);
                messageData.put("fileName", fileName);
                messageData.put("mimeType", mimeType);
                messageData.put("edited", true); // Mark as edited
                if (dialogRowId != null) {
                    messageData.put("mediaDownloadUrl", "/api/conversations/media/download?targetUserId=" + targetUserId + "&userId=" + currentUserId + "&messageId=" + oldMessageId);
                }

                return ResponseEntity.ok(ApiResponse.success("Media edited", messageData));
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Error editing media: " + e.getMessage()));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error replacing media: " + e.getMessage()));
        }
    }
}

