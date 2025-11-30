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
     * Now supports SubTarget User (subtargetUserId parameter) - conversation is started at SubTarget level
     */
    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<String>> initializeConversation(
            @RequestParam(value = "targetUserId") Integer targetUserId,
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId,
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

            // If subtargetUserId is provided, validate it belongs to this target user
            com.aria.core.model.SubTargetUser subTargetUser = null;
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                subTargetUser = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (subTargetUser == null || subTargetUser.getTargetUserId() != targetUserId) {
                    return ResponseEntity.badRequest()
                        .body(ApiResponse.error("SubTarget user not found or does not belong to this Target user"));
                }
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

            // Persist active conversation record (now with subtarget_user_id)
            DatabaseManager.upsertActiveConversation(currentUserId, targetUserId, subtargetUserId, 
                    goalDTO.getDesiredOutcome(), goalDTO.getContext(), goalDTO.getIncludedPlatformAccountIds());

            orchestrator.initializeConversation(goal, targetUser);
            
            return ResponseEntity.ok(ApiResponse.success("Conversation initialized successfully", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error initializing conversation: " + e.getMessage()));
        }
    }

    /**
     * Get recent messages for a target's dialog, including media flags
     * GET /api/conversations/messages?targetUserId=...&userId=...&limit=100&subtargetUserId=...
     * 
     * If cross-platform context is enabled, aggregates messages from all SubTarget Users.
     * Otherwise, returns messages only from the specified SubTarget User (or selected platform).
     */
    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getMessages(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId
    ) {
        try {
            int currentUserId = userId != null ? userId : 1;
            int lim = (limit == null || limit <= 0 || limit > 500) ? 100 : limit;

            // Skip cache for now - always fetch fresh from database
            // Cache is invalidated after priority ingestion, but we want real-time updates
            // This ensures messages sent/edited on Telegram appear immediately
            // TODO: Could optimize by checking cache timestamp and only using if very recent (< 2 seconds)

            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            if (targetUser == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            }

            // Check if cross-platform context is enabled
            boolean crossPlatformContextEnabled = targetUser.isCrossPlatformContextEnabled();
            
            // Determine which SubTarget User to use
            com.aria.core.model.SubTargetUser currentSubTarget = null;
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                currentSubTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (currentSubTarget == null || currentSubTarget.getTargetUserId() != targetUserId) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("SubTarget user not found or does not belong to this Target user"));
                }
            } else {
                // Fallback to legacy platform selection
                // If target has SubTarget Users, we can still load messages (cross-platform context might be enabled)
                // Only return error if no SubTarget Users exist AND no legacy platform AND cross-platform context is disabled
                com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
                boolean hasSubTargetUsers = targetUser.getSubTargetUsers() != null && !targetUser.getSubTargetUsers().isEmpty();
                if (selected == null && !crossPlatformContextEnabled && !hasSubTargetUsers) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Target user has no selected platform or SubTarget Users, and cross-platform context is disabled"));
                }
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

                java.util.List<Integer> dialogRowIds = new java.util.ArrayList<>();
                
                // If cross-platform context is enabled, get all dialog IDs for all SubTarget Users
                if (crossPlatformContextEnabled) {
                    dialogRowIds = databaseManager.getDialogIdsForTargetUser(targetUserId, currentUserId);
                    if (dialogRowIds.isEmpty()) {
                        // Fallback: try to find at least one dialog using current SubTarget or legacy platform
                        Integer singleDialogId = findDialogForSubTarget(conn, currentUserId, targetUser, currentSubTarget);
                        if (singleDialogId != null) {
                            dialogRowIds.add(singleDialogId);
                        }
                    }
                } else {
                    // Single dialog mode: find dialog for current SubTarget User or selected platform
                    Integer dialogRowId = findDialogForSubTarget(conn, currentUserId, targetUser, currentSubTarget);
                    if (dialogRowId != null) {
                        dialogRowIds.add(dialogRowId);
                    } else if (currentSubTarget == null && targetUser.getSubTargetUsers() != null && !targetUser.getSubTargetUsers().isEmpty()) {
                        // If no specific SubTarget User provided but target has SubTarget Users, try to find dialog for first one
                        com.aria.core.model.SubTargetUser firstSubTarget = targetUser.getSubTargetUsers().get(0);
                        dialogRowId = findDialogForSubTarget(conn, currentUserId, targetUser, firstSubTarget);
                        if (dialogRowId != null) {
                            dialogRowIds.add(dialogRowId);
                        }
                    }
                }
                
                if (dialogRowIds.isEmpty()) {
                    System.err.println("No dialogs found for targetUserId=" + targetUserId + ", userId=" + currentUserId + 
                        ", subtargetUserId=" + subtargetUserId + ", crossPlatformContextEnabled=" + crossPlatformContextEnabled);
                    if (currentSubTarget != null) {
                        System.err.println("  SubTarget User: name=" + currentSubTarget.getName() + 
                            ", username=" + currentSubTarget.getUsername() + 
                            ", platform=" + currentSubTarget.getPlatform() + 
                            ", platformAccountId=" + currentSubTarget.getPlatformAccountId() + 
                            ", platformId=" + currentSubTarget.getPlatformId());
                    }
                    return ResponseEntity.ok(ApiResponse.success("OK", out)); // Return empty list if no dialogs found
                }

                // Build query to get messages from all relevant dialogs
                String placeholders = String.join(",", java.util.Collections.nCopies(dialogRowIds.size(), "?"));
                String messagesSql = "SELECT m.id, m.message_id, m.sender, m.text, m.timestamp, m.has_media, m.reference_id, " +
                        "CASE WHEN m.last_updated IS NOT NULL AND m.last_updated > m.timestamp THEN TRUE ELSE FALSE END as edited, " +
                        "m.last_updated, COALESCE(m.status, 'sent') as status, m.dialog_id, COALESCE(m.pinned, FALSE) as pinned " +
                        "FROM messages m WHERE m.dialog_id IN (" + placeholders + ") " +
                        "ORDER BY m.timestamp DESC LIMIT ?";
                
                try (java.sql.PreparedStatement ps = conn.prepareStatement(messagesSql)) {
                    int paramIndex = 1;
                    for (Integer dialogId : dialogRowIds) {
                        ps.setInt(paramIndex++, dialogId);
                    }
                    ps.setInt(paramIndex, lim);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            java.util.Map<String, Object> row = buildMessageRow(rs, targetUserId, currentUserId, conn);
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
     * Helper method to find dialog for a SubTarget User or legacy platform
     * Uses the same robust strategies as the old implementation
     */
    private Integer findDialogForSubTarget(java.sql.Connection conn, int currentUserId, 
                                          com.aria.core.model.TargetUser targetUser,
                                          com.aria.core.model.SubTargetUser subTargetUser) throws Exception {
        Integer dialogRowId = null;
        
        // If we have a SubTarget User, use it
        if (subTargetUser != null) {
            // Strategy 1: Try to find dialog by platform_id (Telegram user ID) - most reliable
            if (subTargetUser.getPlatformId() != null && subTargetUser.getPlatformId() > 0) {
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND dialog_id = ? AND type='private' LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setObject(2, subTargetUser.getPlatformAccountId());
                    ps.setLong(3, subTargetUser.getPlatformId());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dialogRowId = rs.getInt(1);
                            System.out.println("Found dialog by platform_id: " + subTargetUser.getPlatformId());
                        }
                    }
                }
            }
            
            // Strategy 2: Try to get entity ID from Telegram and match by dialog_id (if platform_id not available)
            if (dialogRowId == null && subTargetUser.getUsername() != null && !subTargetUser.getUsername().isEmpty() 
                && subTargetUser.getPlatform() == com.aria.platform.Platform.TELEGRAM) {
                try {
                    DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(subTargetUser.getPlatformAccountId());
                    if (acc != null) {
                        String usernameForLookup = subTargetUser.getUsername().startsWith("@") ? 
                            subTargetUser.getUsername().substring(1) : subTargetUser.getUsername();
                        
                        // Get entity ID from Telegram
                        ProcessBuilder pb = new ProcessBuilder("python3", "scripts/telethon/get_entity_id.py", usernameForLookup);
                        Map<String, String> env = pb.environment();
                        env.put("TELEGRAM_API_ID", acc.apiId);
                        env.put("TELEGRAM_API_HASH", acc.apiHash);
                        env.put("TELEGRAM_PHONE", acc.number);
                        if (acc.username != null && !acc.username.isEmpty()) {
                            env.put("TELEGRAM_USERNAME", acc.username);
                        }
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
                                        ps.setInt(2, subTargetUser.getPlatformAccountId());
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
            
            // Strategy 3: Try to find existing dialog by name (case-insensitive)
            if (dialogRowId == null) {
                String nameToMatch = subTargetUser.getName() != null && !subTargetUser.getName().isEmpty() ? 
                    subTargetUser.getName() : targetUser.getName();
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND LOWER(name) = LOWER(?) ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setObject(2, subTargetUser.getPlatformAccountId());
                    ps.setString(3, nameToMatch);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dialogRowId = rs.getInt(1);
                        }
                    }
                }
            }
            
            // Strategy 4: Try to find by username (case-insensitive, with/without @)
            if (dialogRowId == null && subTargetUser.getUsername() != null && !subTargetUser.getUsername().isEmpty()) {
                String usernameToMatch = subTargetUser.getUsername().startsWith("@") ? 
                    subTargetUser.getUsername().substring(1) : subTargetUser.getUsername();
                // Try exact match (case-insensitive)
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND (LOWER(name) = LOWER(?) OR LOWER(name) = LOWER(?)) ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setObject(2, subTargetUser.getPlatformAccountId());
                    ps.setString(3, usernameToMatch);
                    ps.setString(4, "@" + usernameToMatch);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dialogRowId = rs.getInt(1);
                        }
                    }
                }
                // Try partial match (contains)
                if (dialogRowId == null) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND (LOWER(name) LIKE ? OR LOWER(name) LIKE ?) ORDER BY id DESC LIMIT 1")) {
                        ps.setInt(1, currentUserId);
                        ps.setObject(2, subTargetUser.getPlatformAccountId());
                        ps.setString(3, "%" + usernameToMatch.toLowerCase() + "%");
                        ps.setString(4, "%@" + usernameToMatch.toLowerCase() + "%");
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                dialogRowId = rs.getInt(1);
                            }
                        }
                    }
                }
            }
            
            // Strategy 5: If still not found, list all dialogs and find best match
            if (dialogRowId == null) {
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, name FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' ORDER BY last_synced DESC")) {
                    ps.setInt(1, currentUserId);
                    ps.setObject(2, subTargetUser.getPlatformAccountId());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        String targetNameLower = targetUser.getName().toLowerCase();
                        String subTargetNameLower = subTargetUser.getName() != null ? subTargetUser.getName().toLowerCase() : "";
                        String usernameLower = subTargetUser.getUsername() != null ? subTargetUser.getUsername().toLowerCase().replace("@", "") : "";
                        while (rs.next()) {
                            String dialogName = rs.getString("name");
                            String dialogNameLower = dialogName != null ? dialogName.toLowerCase() : "";
                            // Check if dialog name matches target name, sub-target name, or username
                            if ((dialogNameLower.contains(targetNameLower) || targetNameLower.contains(dialogNameLower)) ||
                                (!subTargetNameLower.isEmpty() && (dialogNameLower.contains(subTargetNameLower) || subTargetNameLower.contains(dialogNameLower))) ||
                                (!usernameLower.isEmpty() && (dialogNameLower.contains(usernameLower) || dialogNameLower.contains("@" + usernameLower)))) {
                                dialogRowId = rs.getInt("id");
                                System.out.println("Found dialog by fuzzy match: " + dialogName);
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        // Fallback to legacy platform selection
        if (dialogRowId == null) {
            com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
            if (selected != null) {
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
            }
        }
        
        return dialogRowId;
    }
    
    /**
     * Helper method to build a message row from ResultSet
     */
    private java.util.Map<String, Object> buildMessageRow(java.sql.ResultSet rs, int targetUserId, int currentUserId, java.sql.Connection conn) throws Exception {
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
        
        // Get edited flag (column 8) - check if message was edited by comparing last_updated with timestamp
        boolean isEdited = false;
        try {
            java.sql.Timestamp lastUpdated = rs.getTimestamp(9); // last_updated column (column 9)
            java.sql.Timestamp messageTimestamp = rs.getTimestamp(5); // timestamp column
            if (lastUpdated != null && messageTimestamp != null) {
                isEdited = lastUpdated.after(messageTimestamp);
            } else {
                isEdited = rs.getBoolean(8);
            }
        } catch (Exception e) {
            try {
                isEdited = rs.getBoolean(8);
            } catch (Exception e2) {
                isEdited = false;
            }
        }
        row.put("edited", isEdited);
        
        // Get message status (default to 'sent' if null)
        String status = rs.getString(10); // status column (column 10)
        if (status == null || status.isEmpty()) {
            status = "sent";
        }
        row.put("status", status);
        
        // Get media metadata (fileName, mimeType, fileSize) from media table using internal message ID
        if (rs.getBoolean(6)) { // hasMedia
            try (java.sql.PreparedStatement mediaPs = conn.prepareStatement(
                    "SELECT file_name, mime_type, file_size, file_path FROM media WHERE message_id = ? ORDER BY id ASC LIMIT 1")) {
                mediaPs.setInt(1, internalMessageId);
                try (java.sql.ResultSet mediaRs = mediaPs.executeQuery()) {
                    if (mediaRs.next()) {
                        row.put("mediaDownloadUrl", "/api/conversations/media/download?targetUserId=" + targetUserId + "&userId=" + currentUserId + "&messageId=" + rs.getInt(2));
                        String fn = mediaRs.getString(1);
                        String filePath = mediaRs.getString(4);
                        if (fn == null || fn.trim().isEmpty()) {
                            if (filePath != null && !filePath.isEmpty()) {
                                int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
                                if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
                                    fn = filePath.substring(lastSlash + 1);
                                } else {
                                    fn = filePath;
                                }
                            }
                        }
                        // Clean up filename - remove chat prefix if present
                        if (fn != null && !fn.trim().isEmpty() && fn.contains("_")) {
                            String[] parts = fn.split("_", 3);
                            if (parts.length >= 3) {
                                try {
                                    Long.parseLong(parts[1]);
                                    fn = parts[2]; // Extract original filename
                                } catch (NumberFormatException e) {
                                    // Not prefixed
                                }
                            }
                        }
                        row.put("fileName", fn != null && !fn.trim().isEmpty() ? fn : "");
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
        
        // Get pinned status (column 12)
        try {
            boolean pinned = rs.getBoolean(12);
            row.put("pinned", pinned);
        } catch (Exception e) {
            row.put("pinned", false);
        }
        
        return row;
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
                // Return plain text error, not JSON (to avoid browser downloading JSON as "media.json")
                return ResponseEntity.status(404)
                        .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                        .body("Target user not found");
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
                
                // Strategy 1: Try to find by message_id first (most reliable - message knows its dialog)
                // This works regardless of SubTarget User or legacy platform
                // message_id is unique per platform account, so if we find it, it's the right dialog
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT m.dialog_id FROM messages m " +
                        "JOIN dialogs d ON m.dialog_id = d.id " +
                        "WHERE m.message_id = ? AND d.user_id = ? " +
                        "ORDER BY m.id DESC LIMIT 1")) {
                    ps.setInt(1, messageId);
                    ps.setInt(2, currentUserId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dialogRowId = rs.getInt(1);
                            System.out.println("Found dialog by message_id: " + dialogRowId);
                        }
                    }
                }
                
                // Strategy 2: If not found by message_id, try using SubTarget Users or legacy platform
                if (dialogRowId == null) {
                    // Try to find SubTarget User that matches
                    com.aria.core.model.SubTargetUser matchingSubTarget = null;
                    if (targetUser.getSubTargetUsers() != null && !targetUser.getSubTargetUsers().isEmpty()) {
                        // Use first SubTarget User as fallback
                        matchingSubTarget = targetUser.getSubTargetUsers().get(0);
                    }
                    
                    // Use findDialogForSubTarget helper method (same as getMessages)
                    dialogRowId = findDialogForSubTarget(conn, currentUserId, targetUser, matchingSubTarget);
                }
                
                if (dialogRowId == null) {
                    System.err.println("Dialog not found for targetUserId=" + targetUserId + ", userId=" + currentUserId + ", messageId=" + messageId);
                    // Return plain text error, not JSON (to avoid browser downloading JSON as "media.json")
                    return ResponseEntity.status(404)
                            .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                            .body("Dialog not found");
                }

                Integer internalMsgId = null;
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM messages WHERE dialog_id = ? AND message_id = ?")) {
                    ps.setInt(1, dialogRowId);
                    ps.setInt(2, messageId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            internalMsgId = rs.getInt(1);
                        }
                    }
                }
                if (internalMsgId == null) {
                    System.err.println("Message not found: messageId=" + messageId + ", dialogRowId=" + dialogRowId);
                    // Return plain text error, not JSON (to avoid browser downloading JSON as "media.json")
                    return ResponseEntity.status(404)
                            .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                            .body("Message not found");
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
                    // Return 404 with proper headers, not JSON (to avoid browser downloading JSON as "media.json")
                    return ResponseEntity.status(404)
                            .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                            .body("No media for this message");
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
                    // Return 404 with proper headers, not JSON (to avoid browser downloading JSON as "media.json")
                    return ResponseEntity.status(404)
                            .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                            .body("File not found on disk. Path from DB: " + filePath);
                }
                
                byte[] bytes = java.nio.file.Files.readAllBytes(path);
                
                // Sanitize fileName for Content-Disposition header (remove quotes, newlines, etc.)
                String safeFileName = fileName.replace("\"", "").replace("\n", "").replace("\r", "").replace("\\", "");
                if (safeFileName.isEmpty() || safeFileName.equals("media.bin")) {
                    // Try to infer filename from file path
                    String pathStr = path.toString();
                    int lastSlash = pathStr.lastIndexOf('/');
                    int lastBackslash = pathStr.lastIndexOf('\\');
                    int lastSeparator = Math.max(lastSlash, lastBackslash);
                    if (lastSeparator >= 0 && lastSeparator < pathStr.length() - 1) {
                        safeFileName = pathStr.substring(lastSeparator + 1);
                    } else {
                        safeFileName = "media"; // Fallback if filename is empty
                    }
                }
                
                // Determine if it should be inline (for images/videos) or attachment (for other files)
                boolean isInline = mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"));
                String contentDisposition = isInline 
                    ? "inline; filename=\"" + safeFileName + "\""
                    : "attachment; filename=\"" + safeFileName + "\"";
                
                // Set proper Content-Type based on file extension if mimeType is generic
                String finalMimeType = mimeType;
                if (mimeType == null || mimeType.equals("application/octet-stream")) {
                    String lowerFileName = safeFileName.toLowerCase();
                    if (lowerFileName.endsWith(".csv")) {
                        finalMimeType = "text/csv";
                    } else if (lowerFileName.endsWith(".pdf")) {
                        finalMimeType = "application/pdf";
                    } else if (lowerFileName.endsWith(".txt")) {
                        finalMimeType = "text/plain";
                    } else if (lowerFileName.endsWith(".json")) {
                        finalMimeType = "application/json";
                    } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
                        finalMimeType = "image/jpeg";
                    } else if (lowerFileName.endsWith(".png")) {
                        finalMimeType = "image/png";
                    } else if (lowerFileName.endsWith(".gif")) {
                        finalMimeType = "image/gif";
                    } else if (lowerFileName.endsWith(".mp4")) {
                        finalMimeType = "video/mp4";
                    }
                }
                
                return ResponseEntity.ok()
                        .header("Content-Disposition", contentDisposition)
                        .header("Content-Type", finalMimeType)
                        .body(bytes);
            }
        } catch (Exception e) {
            // Return plain text error, not JSON (to avoid browser downloading JSON/CSV)
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body("Error downloading media: " + e.getMessage());
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
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId,
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

            // Try to get platform info from SubTarget User first
            DatabaseManager.PlatformAccount acc = null;
            String username = null;
            com.aria.platform.Platform platform = null;
            Integer platformAccountId = null;
            
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                com.aria.core.model.SubTargetUser subTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (subTarget != null && subTarget.getTargetUserId() == targetUserId) {
                    platform = subTarget.getPlatform();
                    if (platform == com.aria.platform.Platform.TELEGRAM && subTarget.getPlatformAccountId() != null) {
                        acc = DatabaseManager.getPlatformAccountById(subTarget.getPlatformAccountId());
                        username = subTarget.getUsername();
                        platformAccountId = subTarget.getPlatformAccountId();
                    }
                }
            }
            
            // Fallback to legacy platform selection
            if (acc == null) {
                com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
                if (selected == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Target user has no selected platform or SubTarget User"));
                }
                platform = selected.getPlatform();
                acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
                username = selected.getUsername();
                platformAccountId = selected.getPlatformId();
            }

            if (acc == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
            }

            com.aria.platform.telegram.TelegramConnector.SendMessageResult sendResult = null;
            if (platform == com.aria.platform.Platform.TELEGRAM) {
                // Reuse connector from registry for this account
                com.aria.platform.telegram.TelegramConnector connector =
                        (com.aria.platform.telegram.TelegramConnector)
                        com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
                sendResult = connector.sendMessageAndGetResult(username, incomingMessage, referenceId);
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Sending not yet supported for platform: " + platform));
            }

            if (sendResult == null || !sendResult.success || sendResult.messageId == null || sendResult.messageId < 0) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to send message"));
            }

            Long telegramMessageId = sendResult.messageId;
            Long peerId = sendResult.peerId;

            // Return the message data immediately so UI can display it instantly
            // Database operations will happen in background (non-blocking)
            java.util.Map<String, Object> messageData = new java.util.HashMap<>();
            messageData.put("messageId", telegramMessageId.intValue());
            messageData.put("fromUser", true);
            messageData.put("text", incomingMessage);
            messageData.put("timestamp", System.currentTimeMillis());
            messageData.put("hasMedia", false);
            
            // Create final copies for use in lambda
            final int finalCurrentUserId = currentUserId;
            final int finalPlatformAccountId = platformAccountId;
            final String finalTargetUserName = targetUser.getName();
            final Long finalPeerId = peerId;
            final Long finalTelegramMessageId = telegramMessageId;
            final String finalIncomingMessage = incomingMessage;
            final Long finalReferenceId = referenceId;
            
            // Save to database in background (non-blocking) - don't wait for it
            new Thread(() -> {
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
                        ps.setInt(1, finalCurrentUserId);
                        ps.setInt(2, finalPlatformAccountId);
                        ps.setString(3, finalTargetUserName);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                dialogRowId = rs.getInt(1);
                            }
                        }
                    }
                    
                    // If no dialog found and we have peer ID, try to find by peer ID
                    if (dialogRowId == null && finalPeerId != null && finalPeerId > 0) {
                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND dialog_id = ? LIMIT 1")) {
                            ps.setInt(1, finalCurrentUserId);
                            ps.setInt(2, finalPlatformAccountId);
                            ps.setLong(3, finalPeerId);
                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    dialogRowId = rs.getInt(1);
                                }
                            }
                        }
                    }
                    
                    // If still no dialog, create one using peer ID if available
                    if (dialogRowId == null && finalPeerId != null && finalPeerId > 0) {
                        try {
                            dialogRowId = DatabaseManager.saveDialog(
                                finalCurrentUserId,
                                finalPlatformAccountId,
                                finalPeerId,
                                finalTargetUserName,
                                "private",
                                0, // message_count
                                0  // media_count
                            );
                            System.out.println("Created new dialog for target: " + finalTargetUserName + ", dialogId=" + dialogRowId);
                        } catch (Exception e) {
                            System.err.println("Warning: Failed to create dialog: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    
                    // Save the message if we have a dialog
                    if (dialogRowId != null && finalTelegramMessageId > 0) {
                        java.time.LocalDateTime now = java.time.LocalDateTime.now();
                        try {
                            DatabaseManager.saveMessage(
                                dialogRowId,
                                finalTelegramMessageId,
                                "me",
                                finalIncomingMessage,
                                now,
                                false, // hasMedia
                                finalReferenceId, // reference_id for replies
                                "sent" // status
                            );
                            System.out.println("Saved sent message to database: messageId=" + finalTelegramMessageId + ", dialogId=" + dialogRowId);
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
            }).start();

            // Invalidate cache when message is sent
            com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
            cache.invalidateMessages(currentUserId, targetUserId);
            
            return ResponseEntity.ok(ApiResponse.success("Message sent", messageData));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error generating response: " + e.getMessage()));
        }
    }

    /**
     * Generate an AI suggestion for a reply (without sending it)
     * Uses 70/15/15 strategy with OpenAI Responses API for conversation state management
     * GET /api/conversations/suggest?targetUserId=...&userId=...&subtargetUserId=...&multiple=false
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<Object>> generateSuggestion(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId,
            @RequestParam(value = "multiple", required = false, defaultValue = "false") Boolean multiple) {
        try {
            int currentUserId = userId != null ? userId : 1;
            
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);

            if (targetUser == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            }

            // Get SubTarget User if provided
            com.aria.core.model.SubTargetUser subtargetUser = null;
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                subtargetUser = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (subtargetUser == null || subtargetUser.getTargetUserId() != targetUserId) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("SubTarget user not found or does not belong to this Target user"));
                }
            }

            // Check if cross-platform context is enabled
            boolean crossPlatformContextEnabled = targetUser.isCrossPlatformContextEnabled();

            // Initialize response manager and context builder
            com.aria.ai.AriaResponseManager responseManager = new com.aria.ai.AriaResponseManager();
            com.aria.ai.ContextBuilder70_15_15 contextBuilder = new com.aria.ai.ContextBuilder70_15_15();

            // Get or create response ID
            Integer subtargetUserIdForResponse = subtargetUser != null ? subtargetUser.getId() : null;
            String previousResponseId = responseManager.getResponseId(targetUserId, subtargetUserIdForResponse);
            Long lastStoredMessageId = responseManager.getLastMessageId(targetUserId, subtargetUserIdForResponse);

            // Get all messages (enough to get new ones since last suggestion)
            java.util.List<java.util.Map<String, Object>> allMessages = new java.util.ArrayList<>();
            Long highestMessageId = null;
            try {
                ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> messagesResp = 
                    getMessages(targetUserId, currentUserId, 100, subtargetUserId); // Get more messages to find new ones
                ApiResponse<java.util.List<java.util.Map<String, Object>>> body = messagesResp.getBody();
                if (body != null && body.isSuccess()) {
                    allMessages = body.getData();
                    // Find highest message ID
                    for (java.util.Map<String, Object> msg : allMessages) {
                        Object msgIdObj = msg.get("messageId");
                        if (msgIdObj != null) {
                            long msgId = ((Number) msgIdObj).longValue();
                            if (highestMessageId == null || msgId > highestMessageId) {
                                highestMessageId = msgId;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to load messages: " + e.getMessage());
            }

            // Build conversation snippet from new messages (since last suggestion)
            StringBuilder conversationSnippet = new StringBuilder();
            boolean hasNewMessages = false;
            String lastMessageFromTarget = null; // Track for fallback
            
            if (lastStoredMessageId != null && !allMessages.isEmpty()) {
                // Get all messages after the last stored message ID
                for (java.util.Map<String, Object> msg : allMessages) {
                    Object msgIdObj = msg.get("messageId");
                    if (msgIdObj != null) {
                        long msgId = ((Number) msgIdObj).longValue();
                        if (msgId > lastStoredMessageId) {
                            Boolean fromUser = (Boolean) msg.get("fromUser");
                            String text = (String) msg.get("text");
                            if (text != null && !text.trim().isEmpty()) {
                                conversationSnippet.append(fromUser ? "You" : "Them")
                                                  .append(": ")
                                                  .append(text)
                                                  .append("\n");
                                hasNewMessages = true;
                                // Track last message from target for fallback
                                if (!fromUser) {
                                    lastMessageFromTarget = text;
                                }
                            }
                        }
                    }
                }
            }
            
            // If no new messages since last suggestion, get the last message from target user
            if (!hasNewMessages || conversationSnippet.length() == 0) {
                int count = 0;
                for (int i = allMessages.size() - 1; i >= 0 && count < 10; i--) {
                    java.util.Map<String, Object> msg = allMessages.get(i);
                    Boolean fromUser = (Boolean) msg.get("fromUser");
                    String text = (String) msg.get("text");
                    if (!fromUser && text != null && !text.trim().isEmpty()) {
                        lastMessageFromTarget = text;
                        break;
                    }
                    count++;
                }
            }
            
            // Determine what to send to OpenAI
            String newMessage;
            if (!hasNewMessages || conversationSnippet.length() == 0) {
                // No new messages, use last target message or default
                newMessage = lastMessageFromTarget != null && !lastMessageFromTarget.trim().isEmpty()
                    ? lastMessageFromTarget
                    : "Generate an appropriate opening message or continue the conversation naturally.";
            } else {
                // Use the conversation snippet (all new messages since last suggestion)
                newMessage = conversationSnippet.toString().trim();
            }

            String suggestion = null;

            if (previousResponseId != null) {
                // Continue existing conversation - send all new messages since last suggestion
                suggestion = responseManager.generateReply(targetUser, subtargetUser, newMessage, null, highestMessageId);
            } else {
                // First call - build full 70/15/15 context
                try {
                    String fullContext = contextBuilder.build70_15_15_Context(
                        targetUser, subtargetUser, currentUserId, crossPlatformContextEnabled);
                    suggestion = responseManager.generateReply(targetUser, subtargetUser, 
                        fullContext + "\n\nNew message to respond to: " + newMessage, fullContext, highestMessageId);
                } catch (Exception e) {
                    System.err.println("Error building 70/15/15 context: " + e.getMessage());
                    e.printStackTrace();
                    // Fall through to default response
                }
            }

            // Fallback to default response if AI fails (e.g., API key issues, quota exceeded)
            if (suggestion == null || suggestion.trim().isEmpty()) {
                // Generate a contextually appropriate default response
                StringBuilder defaultResponse = new StringBuilder();
                
                if (lastMessageFromTarget == null || lastMessageFromTarget.trim().isEmpty()) {
                    // Opening message
                    if (subtargetUser != null && subtargetUser.getAdvancedCommunicationSettings() != null) {
                        try {
                            org.json.JSONObject settings = new org.json.JSONObject(subtargetUser.getAdvancedCommunicationSettings());
                            String preferredOpening = settings.optString("preferredOpening", "");
                            if (!preferredOpening.trim().isEmpty()) {
                                defaultResponse.append(preferredOpening);
                            } else {
                                defaultResponse.append("Hey! How are you doing?");
                            }
                        } catch (Exception e) {
                            defaultResponse.append("Hey! How are you doing?");
                        }
                    } else {
                        defaultResponse.append("Hey! How are you doing?");
                    }
                } else {
                    // Response to existing message
                    defaultResponse.append("That's interesting! Tell me more about that.");
                }
                
                suggestion = defaultResponse.toString();
            }

            // If multiple suggestions requested, generate variations
            if (Boolean.TRUE.equals(multiple)) {
                java.util.List<String> suggestions = new java.util.ArrayList<>();
                suggestions.add(suggestion); // Add the first suggestion
                
                // Generate 2-3 additional variations using different style prompts
                // Note: For variations, we generate them without saving to main response ID
                // to avoid interfering with the main conversation state
                String[] variationPrompts = {
                    "Generate a more casual and friendly response",
                    "Generate a more professional and concise response",
                    "Generate a more engaging and question-based response"
                };
                
                for (int i = 0; i < Math.min(3, variationPrompts.length); i++) {
                    String variationPrompt = variationPrompts[i] + " to: " + newMessage;
                    String variation = null;
                    
                    // Try to generate variation (without saving to main response ID)
                    // We'll use the same context but with a variation instruction
                    try {
                        if (previousResponseId != null) {
                            // For existing conversation, create a temporary variation
                            // by modifying the prompt slightly (don't update lastMessageId for variations)
                            variation = responseManager.generateReply(targetUser, subtargetUser, 
                                variationPrompt, null, null);
                        } else {
                            // For new conversation, use full context with variation instruction
                            String fullContext = contextBuilder.build70_15_15_Context(
                                targetUser, subtargetUser, currentUserId, crossPlatformContextEnabled);
                            variation = responseManager.generateReply(targetUser, subtargetUser, 
                                fullContext + "\n\n" + variationPrompt + "\n\nNew message to respond to: " + newMessage, 
                                fullContext, null);
                        }
                    } catch (Exception e) {
                        System.err.println("Error generating variation " + i + ": " + e.getMessage());
                    }
                    
                    // Fallback to default variations if AI fails
                    if (variation == null || variation.trim().isEmpty()) {
                        if (lastMessageFromTarget == null || lastMessageFromTarget.trim().isEmpty()) {
                            // Opening message variations
                            if (i == 0) {
                                variation = "Hey there! What's up?";
                            } else if (i == 1) {
                                variation = "Hello, how can I help you today?";
                            } else {
                                variation = "Hi! How are things going?";
                            }
                        } else {
                            // Response variations
                            if (i == 0) {
                                variation = "That sounds great! I'd love to hear more.";
                            } else if (i == 1) {
                                variation = "Interesting point. Can you elaborate?";
                            } else {
                                variation = "That's really cool! What made you think of that?";
                            }
                        }
                    }
                    
                    // Only add if different from existing suggestions
                    if (variation != null && !variation.trim().isEmpty() && 
                        !suggestions.contains(variation) && !variation.equals(suggestion)) {
                        suggestions.add(variation);
                    }
                }
                
                // Ensure we have at least the original suggestion
                if (suggestions.isEmpty()) {
                    suggestions.add(suggestion);
                }
                
                // Return list of suggestions
                return ResponseEntity.ok(ApiResponse.success("AI suggestions generated", suggestions));
            }

            // Return single suggestion
            return ResponseEntity.ok(ApiResponse.success("AI suggestion generated", suggestion));
        } catch (Exception e) {
            System.err.println("Error generating AI suggestion: " + e.getMessage());
            e.printStackTrace();
            // Return a safe default response even on error
            return ResponseEntity.ok(ApiResponse.success("AI suggestion generated", 
                "How about we discuss this further?"));
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
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId
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

            // Try to get platform info from SubTarget User first
            DatabaseManager.PlatformAccount acc = null;
            String username = null;
            com.aria.platform.Platform platform = null;
            com.aria.core.model.SubTargetUser currentSubTarget = null;
            
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                currentSubTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (currentSubTarget != null && currentSubTarget.getTargetUserId() == targetUserId) {
                    platform = currentSubTarget.getPlatform();
                    if (platform == com.aria.platform.Platform.TELEGRAM && currentSubTarget.getPlatformAccountId() != null) {
                        acc = DatabaseManager.getPlatformAccountById(currentSubTarget.getPlatformAccountId());
                        username = currentSubTarget.getUsername();
                    }
                }
            }
            
            // Fallback to legacy platform selection
            if (acc == null) {
                com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
                if (selected == null || selected.getPlatform() != com.aria.platform.Platform.TELEGRAM) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Editing supported only for Telegram for now, and target user has no selected platform or SubTarget User"));
                }
                platform = selected.getPlatform();
                acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
                username = selected.getUsername();
            }

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
                // Use findDialogForSubTarget helper method (same as getMessages)
                dialogsRowId = findDialogForSubTarget(conn, currentUserId, targetUser, currentSubTarget);

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
                boolean ok = connector.editMessage(username, lastMsgId, newText != null ? newText : "");
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
                    
                    // Return updated message data immediately (database update happens in background)
                    java.util.Map<String, Object> messageData = new java.util.HashMap<>();
                    messageData.put("messageId", lastMsgId);
                    messageData.put("fromUser", true);
                    messageData.put("text", newText != null && !newText.isEmpty() ? newText : null);
                    messageData.put("edited", true);
                    messageData.put("timestamp", System.currentTimeMillis()); // Use current time, will be corrected by polling
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
                    
                    // Update database in background (non-blocking) - don't wait for it
                    final Integer finalDialogsRowId = dialogsRowId;
                    final Integer finalLastMsgId = lastMsgId;
                    final String finalNewText = newText;
                    new Thread(() -> {
                        try (java.sql.Connection bgConn = java.sql.DriverManager.getConnection(
                                System.getenv("DATABASE_URL") != null
                                        ? System.getenv("DATABASE_URL")
                                        : "jdbc:postgresql://localhost:5432/aria",
                                System.getenv("DATABASE_USER") != null
                                        ? System.getenv("DATABASE_USER")
                                        : "postgres",
                                System.getenv("DATABASE_PASSWORD") != null
                                        ? System.getenv("DATABASE_PASSWORD")
                                        : "Ezekiel(23)")) {
                            bgConn.setAutoCommit(false);
                            try {
                                try (java.sql.PreparedStatement ps = bgConn.prepareStatement(
                                        "UPDATE messages SET text = ?, last_updated = NOW() WHERE dialog_id = ? AND message_id = ?")) {
                                    String encryptedText = finalNewText != null && !finalNewText.isEmpty() ? 
                                        com.aria.storage.SecureStorage.encrypt(finalNewText) : null;
                                    ps.setString(1, encryptedText);
                                    ps.setInt(2, finalDialogsRowId);
                                    ps.setInt(3, finalLastMsgId);
                                    int updated = ps.executeUpdate();
                                    
                                    // Commit the transaction
                                    bgConn.commit();
                                    System.out.println("Database transaction committed for message edit (editLast): messageId=" + finalLastMsgId + ", rowsUpdated=" + updated);
                                    
                                    // Invalidate cache
                                    com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
                                    // Note: We need targetUserId here - this is a background thread, so we'd need to pass it
                                    // For now, we'll invalidate in the main response handler
                                }
                            } catch (Exception e) {
                                // Rollback on error
                                try {
                                    bgConn.rollback();
                                    System.err.println("Rolled back transaction due to error: " + e.getMessage());
                                } catch (Exception rollbackEx) {
                                    System.err.println("Failed to rollback: " + rollbackEx.getMessage());
                                }
                                // Log but don't fail - message was edited in Telegram
                                System.err.println("Warning: Failed to update message in database: " + e.getMessage());
                            } finally {
                                bgConn.setAutoCommit(true); // Restore auto-commit
                            }
                        } catch (Exception e) {
                            System.err.println("Warning: Failed to update message in database: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }).start();
                    
                    // Invalidate cache when message is edited
                    com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
                    cache.invalidateMessages(currentUserId, targetUserId);
                    
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
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId
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
            
            // Try to get platform info from SubTarget User first
            DatabaseManager.PlatformAccount acc = null;
            String username = null;
            com.aria.platform.Platform platform = null;
            Integer platformAccountId = null;
            com.aria.core.model.SubTargetUser currentSubTarget = null;
            
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                currentSubTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (currentSubTarget != null && currentSubTarget.getTargetUserId() == targetUserId) {
                    platform = currentSubTarget.getPlatform();
                    if (platform == com.aria.platform.Platform.TELEGRAM && currentSubTarget.getPlatformAccountId() != null) {
                        acc = DatabaseManager.getPlatformAccountById(currentSubTarget.getPlatformAccountId());
                        username = currentSubTarget.getUsername();
                        platformAccountId = currentSubTarget.getPlatformAccountId();
                    }
                }
            }
            
            // Fallback to legacy platform selection
            if (acc == null) {
                com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
                if (selected == null || selected.getPlatform() != com.aria.platform.Platform.TELEGRAM) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Editing supported only for Telegram for now, and target user has no selected platform or SubTarget User"));
                }
                platform = selected.getPlatform();
                acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
                username = selected.getUsername();
                platformAccountId = selected.getPlatformId();
            }
            
            if (acc == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
            }
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
                // Use findDialogForSubTarget helper method (same as getMessages)
                dialogsRowId = findDialogForSubTarget(checkConn, currentUserId, targetUser, currentSubTarget);
                
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
            
            boolean ok = connector.editMessage(username, messageId, newText != null ? newText : "");
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
                        
                        // Find dialog row id using findDialogForSubTarget helper
                        Integer dialogsRowId = findDialogForSubTarget(conn, currentUserId, targetUser, currentSubTarget);
                        
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
                            
                            // Return updated message data immediately (database update happens in background)
                            java.util.Map<String, Object> messageData = new java.util.HashMap<>();
                            messageData.put("messageId", messageId);
                            messageData.put("fromUser", true);
                            messageData.put("text", newText != null && !newText.isEmpty() ? newText : null);
                            messageData.put("edited", true);
                            messageData.put("timestamp", System.currentTimeMillis()); // Use current time, will be corrected by polling
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
                            
                            // Update database in background (non-blocking) - don't wait for it
                            final Integer finalDialogsRowId = dialogsRowId;
                            final Integer finalMessageId = messageId;
                            final String finalNewText = newText;
                            new Thread(() -> {
                                try (java.sql.Connection bgConn = java.sql.DriverManager.getConnection(
                                        System.getenv("DATABASE_URL") != null
                                                ? System.getenv("DATABASE_URL")
                                                : "jdbc:postgresql://localhost:5432/aria",
                                        System.getenv("DATABASE_USER") != null
                                                ? System.getenv("DATABASE_USER")
                                                : "postgres",
                                        System.getenv("DATABASE_PASSWORD") != null
                                                ? System.getenv("DATABASE_PASSWORD")
                                                : "Ezekiel(23)")) {
                                    bgConn.setAutoCommit(false);
                                    try {
                                        try (java.sql.PreparedStatement ps = bgConn.prepareStatement(
                                                "UPDATE messages SET text = ?, last_updated = NOW() WHERE dialog_id = ? AND message_id = ?")) {
                                            String encryptedText = finalNewText != null && !finalNewText.isEmpty() ? 
                                                com.aria.storage.SecureStorage.encrypt(finalNewText) : null;
                                            ps.setString(1, encryptedText);
                                            ps.setInt(2, finalDialogsRowId);
                                            ps.setInt(3, finalMessageId);
                                            int updated = ps.executeUpdate();
                                            
                                            // Commit the transaction
                                            bgConn.commit();
                                            System.out.println("Database transaction committed for message edit: messageId=" + finalMessageId + ", rowsUpdated=" + updated);
                                        }
                                    } catch (Exception e) {
                                        // Rollback on error
                                        try {
                                            bgConn.rollback();
                                            System.err.println("Rolled back transaction due to error: " + e.getMessage());
                                        } catch (Exception rollbackEx) {
                                            System.err.println("Failed to rollback: " + rollbackEx.getMessage());
                                        }
                                        System.err.println("Warning: Failed to update message in database: " + e.getMessage());
                                    } finally {
                                        bgConn.setAutoCommit(true); // Restore auto-commit
                                    }
                                } catch (Exception e) {
                                    System.err.println("Warning: Failed to update message in database: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }).start();
                            
                            // Invalidate cache when message is edited
                            com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
                            cache.invalidateMessages(currentUserId, targetUserId);
                            
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
                            
                            // Invalidate cache
                            com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
                            cache.invalidateMessages(currentUserId, targetUserId);
                            
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
            @RequestParam(value = "revoke", defaultValue = "true") Boolean revoke,
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId
    ) {
        try {
            int currentUserId = userId != null ? userId : 1;
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            if (targetUser == null) return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            
            // Try to get platform info from SubTarget User first
            DatabaseManager.PlatformAccount acc = null;
            String username = null;
            com.aria.platform.Platform platform = null;
            Integer platformAccountId = null;
            com.aria.core.model.SubTargetUser currentSubTarget = null;
            
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                currentSubTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (currentSubTarget != null && currentSubTarget.getTargetUserId() == targetUserId) {
                    platform = currentSubTarget.getPlatform();
                    if (platform == com.aria.platform.Platform.TELEGRAM && currentSubTarget.getPlatformAccountId() != null) {
                        acc = DatabaseManager.getPlatformAccountById(currentSubTarget.getPlatformAccountId());
                        username = currentSubTarget.getUsername();
                        platformAccountId = currentSubTarget.getPlatformAccountId();
                    }
                }
            }
            
            // Fallback to legacy platform selection
            if (acc == null) {
                com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
                if (selected == null || selected.getPlatform() != com.aria.platform.Platform.TELEGRAM) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Deletion supported only for Telegram for now, and target user has no selected platform or SubTarget User"));
                }
                platform = selected.getPlatform();
                acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
                username = selected.getUsername();
                platformAccountId = selected.getPlatformId();
            }
            
            if (acc == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
            }
            // Use direct cast like in respond method
            com.aria.platform.telegram.TelegramConnector connector =
                    (com.aria.platform.telegram.TelegramConnector)
                    com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
            if (connector == null) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to create connector"));
            }
            com.aria.platform.telegram.TelegramConnector.DeleteMessageResult result = connector.deleteMessage(username, messageId, revoke);
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
                        
                        // Find dialog row id using findDialogForSubTarget helper (same as getMessages)
                        Integer dialogsRowId = findDialogForSubTarget(conn, currentUserId, targetUser, currentSubTarget);
                        
                        // If still not found, try to find by message_id (look up which dialog this message belongs to)
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
                                            verifyPs.setInt(3, platformAccountId);
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
                                    
                                    // Record this deletion in app_deleted_messages to prevent priority ingestion from re-adding it
                                    // This is especially important when revoke=false (message still exists in Telegram)
                                    try (java.sql.PreparedStatement delTrackPs = conn.prepareStatement(
                                            "INSERT INTO app_deleted_messages (dialog_id, message_id, deleted_at) VALUES (?, ?, NOW()) " +
                                            "ON CONFLICT (dialog_id, message_id) DO UPDATE SET deleted_at = NOW()")) {
                                        delTrackPs.setInt(1, dialogsRowId);
                                        delTrackPs.setInt(2, messageId);
                                        delTrackPs.executeUpdate();
                                        System.out.println("Recorded app-side deletion for messageId=" + messageId);
                                    }
                                    
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
                    
                    // Invalidate cache when message is deleted
                    com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
                    cache.invalidateMessages(currentUserId, targetUserId);
                    
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
     * Trigger priority ingestion for a specific target user conversation
     * POST /api/conversations/ingestTarget?targetUserId=123&userId=1
     * This will prioritize ingesting this target's messages and check for deletions
     */
    @PostMapping("/ingestTarget")
    public ResponseEntity<ApiResponse<String>> ingestTargetConversation(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            if (targetUser == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            }
            
            // Try to get platform info from SubTarget User first
            DatabaseManager.PlatformAccount acc = null;
            String targetUsername = null;
            
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                com.aria.core.model.SubTargetUser subTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (subTarget != null && subTarget.getTargetUserId() == targetUserId) {
                    if (subTarget.getPlatform() == com.aria.platform.Platform.TELEGRAM && subTarget.getPlatformAccountId() != null) {
                        acc = DatabaseManager.getPlatformAccountById(subTarget.getPlatformAccountId());
                        targetUsername = subTarget.getUsername();
                    }
                }
            }
            
            // Fallback to legacy platform selection
            if (acc == null) {
                com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
                if (selected == null || selected.getPlatform() != com.aria.platform.Platform.TELEGRAM) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Priority ingestion only supported for Telegram. Please ensure the SubTarget User has a Telegram platform configured."));
                }
                acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
                if (acc == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
                }
                targetUsername = selected.getUsername();
            }
            
            if (acc == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
            }
            
            // Use ConnectorRegistry to get or create connector
            com.aria.platform.telegram.TelegramConnector connector = 
                (com.aria.platform.telegram.TelegramConnector) 
                com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
            
            if (connector == null) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to create connector"));
            }
            
            // Normalize username (remove @ if present)
            if (targetUsername != null && targetUsername.startsWith("@")) {
                targetUsername = targetUsername.substring(1);
            } else if (targetUsername == null || targetUsername.isBlank()) {
                targetUsername = targetUser.getName(); // Fallback to name
            }
            
            // Create final copies for use in lambda
            final String finalTargetUsername = targetUsername;
            final DatabaseManager.PlatformAccount finalAcc = acc;
            final com.aria.platform.telegram.TelegramConnector finalConnector = connector;
            final int finalCurrentUserId = currentUserId;
            final int finalTargetUserId = targetUserId;
            
            // Priority ingestion runs independently - it doesn't need to check if main ingestion is running
            // It's lightweight (just 50 messages) and should run every 5 seconds
            // Trigger priority ingestion in background thread (non-blocking)
            // This runs every 5 seconds and always re-ingests the last 50 messages
            // to check for new messages and deletions
            new Thread(() -> {
                try {
                    System.out.println("Starting priority ingestion for target user conversation: " + finalTargetUsername);
                    finalConnector.ingestPriorityTarget(finalTargetUsername, finalAcc);
                    System.out.println("Priority ingestion completed for target user: " + finalTargetUsername);
                    
                    // CRITICAL: Invalidate cache after priority ingestion completes
                    // This ensures the next getMessages() call fetches fresh data from database
                    com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
                    cache.invalidateMessages(finalCurrentUserId, finalTargetUserId);
                    System.out.println("Cache invalidated for user " + finalCurrentUserId + ", target " + finalTargetUserId);
                } catch (Exception e) {
                    System.err.println("Error in priority ingestion for target user: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "priority-ingest-target-" + finalTargetUsername).start();
            
            return ResponseEntity.ok(ApiResponse.success("Priority ingestion started for target user", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error starting priority ingestion: " + e.getMessage()));
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
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId,
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

            // Determine platform and account id - prioritize SubTarget User if provided
            com.aria.platform.Platform platform = null;
            int accountId = 0;
            String targetUsername = null;
            DatabaseManager.PlatformAccount acc = null;
            
            if (subtargetUserId != null) {
                // Use SubTarget User
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                com.aria.core.model.SubTargetUser subTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (subTarget == null || subTarget.getTargetUserId() != targetUserId) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("SubTarget user not found or does not belong to this Target user"));
                }
                platform = subTarget.getPlatform();
                accountId = subTarget.getPlatformAccountId();
                targetUsername = subTarget.getUsername();
                acc = DatabaseManager.getPlatformAccountById(accountId);
            } else {
                // Fallback to legacy platform selection
                com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
                if (selected == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Target user has no selected platform or SubTarget User"));
                }
                platform = selected.getPlatform();
                accountId = selected.getPlatformId();
                targetUsername = selected.getUsername();
                acc = DatabaseManager.getPlatformAccountById(accountId);
            }
            
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
                sendResult = connector.sendMediaAndGetResult(targetUsername, absoluteMediaPath, caption, referenceId);
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
                    ps.setInt(2, accountId);
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
                        ps.setInt(2, accountId);
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
                            accountId,
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
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId,
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

            // Determine platform and account id - prioritize SubTarget User if provided
            com.aria.platform.Platform platform = null;
            int accountId = 0;
            String targetUsername = null;
            DatabaseManager.PlatformAccount acc = null;
            
            if (subtargetUserId != null) {
                // Use SubTarget User
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                com.aria.core.model.SubTargetUser subTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (subTarget == null || subTarget.getTargetUserId() != targetUserId) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("SubTarget user not found or does not belong to this Target user"));
                }
                platform = subTarget.getPlatform();
                accountId = subTarget.getPlatformAccountId();
                targetUsername = subTarget.getUsername();
                acc = DatabaseManager.getPlatformAccountById(accountId);
            } else {
                // Fallback to legacy platform selection
                com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
                if (selected == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Target user has no selected platform or SubTarget User"));
                }
                platform = selected.getPlatform();
                accountId = selected.getPlatformId();
                targetUsername = selected.getUsername();
                acc = DatabaseManager.getPlatformAccountById(accountId);
            }
            
            if (acc == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Platform account not found"));
            }

            if (platform != com.aria.platform.Platform.TELEGRAM) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Media replacement only supported for Telegram"));
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
            
            boolean edited = connector.editMediaMessage(targetUsername, oldMessageId, absoluteMediaPath, caption);
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
                
                // Find dialog using findDialogForSubTarget helper
                com.aria.core.model.SubTargetUser currentSubTarget = null;
                if (subtargetUserId != null) {
                    com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                    currentSubTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                }
                dialogRowId = findDialogForSubTarget(conn, currentUserId, targetUser, currentSubTarget);
                
                // Fallback: try direct lookup if helper didn't find it
                if (dialogRowId == null) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type='private' AND name = ? ORDER BY id DESC LIMIT 1")) {
                        ps.setInt(1, currentUserId);
                        ps.setInt(2, accountId);
                        ps.setString(3, targetUser.getName());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) dialogRowId = rs.getInt(1);
                        }
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

    /**
     * Pin or unpin a message
     * POST /api/conversations/pin
     */
    @PostMapping("/pin")
    public ResponseEntity<ApiResponse<String>> pinMessage(
            @RequestParam("targetUserId") Integer targetUserId,
            @RequestParam("messageId") Long messageId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "subtargetUserId", required = false) Integer subtargetUserId,
            @RequestParam(value = "pin", defaultValue = "true") Boolean pin) {
        try {
            int currentUserId = userId != null ? userId : 1;
            
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetUserId);
            
            if (targetUser == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Target user not found"));
            }

            // Find dialog using the same logic as other endpoints
            com.aria.core.model.SubTargetUser subTargetForDialog = null;
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                subTargetForDialog = subTargetUserService.getSubTargetUserById(subtargetUserId);
            }
            
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
                
                dialogRowId = findDialogForSubTarget(conn, currentUserId, targetUser, subTargetForDialog);
                
                if (dialogRowId == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Dialog not found"));
                }

                // Update pinned status in database
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "UPDATE messages SET pinned = ? WHERE dialog_id = ? AND message_id = ?")) {
                    ps.setBoolean(1, pin);
                    ps.setInt(2, dialogRowId);
                    ps.setLong(3, messageId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        return ResponseEntity.badRequest().body(ApiResponse.error("Message not found"));
                    }
                }
            } catch (java.sql.SQLException e) {
                System.err.println("SQL error in pin endpoint: " + e.getMessage());
                e.printStackTrace();
                return ResponseEntity.internalServerError().body(ApiResponse.error("Database error: " + e.getMessage()));
            }

            // Pin/unpin on Telegram if connector is available
            com.aria.platform.Platform platform = null;
            int accountId = 0;
            String targetUsername = null;
            DatabaseManager.PlatformAccount acc = null;
            
            if (subtargetUserId != null) {
                com.aria.service.SubTargetUserService subTargetUserService = new com.aria.service.SubTargetUserService(databaseManager);
                com.aria.core.model.SubTargetUser subTarget = subTargetUserService.getSubTargetUserById(subtargetUserId);
                if (subTarget != null && subTarget.getTargetUserId() == targetUserId) {
                    platform = subTarget.getPlatform();
                    accountId = subTarget.getPlatformAccountId();
                    targetUsername = subTarget.getUsername();
                    acc = DatabaseManager.getPlatformAccountById(accountId);
                }
            } else {
                // Fallback to legacy platform selection
                com.aria.platform.UserPlatform selectedPlatform = targetUser.getSelectedPlatform();
                if (selectedPlatform != null) {
                    platform = selectedPlatform.getPlatform();
                    accountId = selectedPlatform.getPlatformId(); // Use platformId as accountId for legacy
                    targetUsername = selectedPlatform.getUsername();
                    acc = DatabaseManager.getPlatformAccountById(accountId);
                }
            }

            if (platform == com.aria.platform.Platform.TELEGRAM && acc != null && targetUsername != null) {
                try {
                    com.aria.platform.telegram.TelegramConnector connector = new com.aria.platform.telegram.TelegramConnector(
                        acc.apiId, acc.apiHash, acc.number, acc.username, acc.id);
                    
                    // Pin/unpin message on Telegram
                    boolean success = connector.pinMessage(targetUsername, messageId.intValue(), pin);
                    if (!success) {
                        System.err.println("Warning: Failed to pin/unpin message on Telegram, but database was updated");
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Error pinning/unpinning message on Telegram: " + e.getMessage());
                    e.printStackTrace();
                    // Continue - database was already updated
                }
            }

            // Invalidate cache
            com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
            cache.invalidateMessages(currentUserId, targetUserId);

            return ResponseEntity.ok(ApiResponse.success(pin ? "Message pinned" : "Message unpinned"));
        } catch (Exception e) {
            System.err.println("Error in pin endpoint: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error pinning message: " + e.getMessage()));
        }
    }
}


