package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import com.aria.api.dto.TargetUserDTO;
import com.aria.core.model.TargetUser;
import com.aria.core.model.ChatCategory;
import com.aria.platform.Platform;
import com.aria.service.TargetUserService;
import com.aria.service.UserService;
import com.aria.storage.DatabaseManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * REST API Controller for target user management
 */
@RestController
@RequestMapping("/api/targets")
@CrossOrigin(origins = "*")
public class TargetController {

    // DatabaseManager uses static methods, so no autowiring needed

    /**
     * Get all target users
     * GET /api/targets
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TargetUserDTO>>> getAllTargets(@RequestParam(value = "userId", required = false) Integer userId) {
        try {
            // TODO: Get userId from authentication context
            int currentUserId = userId != null ? userId : 1; // Default for now
            
            DatabaseManager databaseManager = new DatabaseManager();
            // Create a minimal User object for UserService (placeholder)
            com.aria.core.model.User user = new com.aria.core.model.User("", "", "", "", "");
            UserService userService = new UserService(databaseManager, user);
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            
            List<TargetUser> targets = targetUserService.getTargetUsersByUserId(currentUserId);
            List<TargetUserDTO> targetDTOs = new ArrayList<>();
            
            // Load profile_json for each target to include ChatProfile fields
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
                for (TargetUser target : targets) {
                    TargetUserDTO dto = new TargetUserDTO(target);
                    
                    // Load platform account username and name if platformAccountId is available
                    if (dto.getPlatformAccountId() != null) {
                        try {
                            DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(dto.getPlatformAccountId());
                            if (acc != null) {
                                if (acc.username != null && !acc.username.isEmpty()) {
                                    dto.setPlatformAccountUsername(acc.username);
                                }
                                if (acc.accountName != null && !acc.accountName.isEmpty()) {
                                    dto.setPlatformAccountName(acc.accountName);
                                }
                            }
                        } catch (Exception e) {
                            // Log but continue - account info is optional
                            System.err.println("Warning: Failed to load platform account info: " + e.getMessage());
                        }
                    }
                    
                    // Load profile_json and profile_picture_url
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT profile_json, profile_picture_url FROM target_users WHERE id = ?")) {
                        ps.setInt(1, target.getTargetId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                // Load profile picture URL
                                String profilePictureUrl = rs.getString("profile_picture_url");
                                if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
                                    dto.setProfilePictureUrl(profilePictureUrl);
                                }
                                
                                String profileJson = rs.getString("profile_json");
                                if (profileJson != null && !profileJson.isEmpty()) {
                                    org.json.JSONObject profile = new org.json.JSONObject(profileJson);
                                    
                                    // Load basic fields
                                    if (profile.has("desiredOutcome")) dto.setDesiredOutcome(profile.getString("desiredOutcome"));
                                    if (profile.has("meetingContext")) dto.setMeetingContext(profile.getString("meetingContext"));
                                    if (profile.has("contextDetails")) dto.setContextDetails(profile.getString("contextDetails"));
                                    
                                    // Load ChatProfile fields
                                    if (profile.has("humorLevel")) dto.setHumorLevel(profile.getDouble("humorLevel"));
                                    if (profile.has("formalityLevel")) dto.setFormalityLevel(profile.getDouble("formalityLevel"));
                                    if (profile.has("empathyLevel")) dto.setEmpathyLevel(profile.getDouble("empathyLevel"));
                                    if (profile.has("responseTimeAverage")) dto.setResponseTimeAverage(profile.getDouble("responseTimeAverage"));
                                    if (profile.has("messageLengthAverage")) dto.setMessageLengthAverage(profile.getDouble("messageLengthAverage"));
                                    if (profile.has("questionRate")) dto.setQuestionRate(profile.getDouble("questionRate"));
                                    if (profile.has("engagementLevel")) dto.setEngagementLevel(profile.getDouble("engagementLevel"));
                                    if (profile.has("preferredOpening")) dto.setPreferredOpening(profile.getString("preferredOpening"));
                                }
                            }
                        }
                    }
                    targetDTOs.add(dto);
                }
            } catch (Exception e) {
                // Log but continue - profile_json is optional
                System.err.println("Warning: Failed to load profile_json: " + e.getMessage());
                // Fallback to basic DTOs without profile
                targetDTOs = targets.stream()
                    .map(TargetUserDTO::new)
                    .collect(Collectors.toList());
            }

            return ResponseEntity.ok(ApiResponse.success(targetDTOs));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error fetching targets: " + e.getMessage()));
        }
    }

    /**
     * Create a new target user
     * POST /api/targets
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TargetUserDTO>> createTarget(
            @RequestBody TargetUserDTO targetDTO,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            
            // Check for duplicate target user (same username, platform, and account)
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
                String checkSql = """
                    SELECT tu.id, tu.name, tup.platform, tup.username, tup.platform_id
                    FROM target_users tu
                    JOIN target_user_platforms tup ON tu.id = tup.target_user_id
                    WHERE tu.user_id = ? 
                    AND tup.username = ? 
                    AND tup.platform = ? 
                    AND tup.platform_id = ?
                    LIMIT 1
                """;
                try (java.sql.PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setInt(1, currentUserId);
                    ps.setString(2, targetDTO.getUsername());
                    ps.setString(3, targetDTO.getPlatform() != null ? targetDTO.getPlatform().name() : "");
                    ps.setInt(4, targetDTO.getPlatformAccountId() != null ? targetDTO.getPlatformAccountId() : 0);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String existingName = rs.getString("name");
                            return ResponseEntity.badRequest()
                                .body(ApiResponse.error("A target user with the same username, platform, and account already exists with the name: " + existingName));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to check for duplicate target user: " + e.getMessage());
                // Continue anyway - don't block creation if check fails
            }
            
            TargetUser targetUser = new TargetUser();
            targetUser.setName(targetDTO.getName());
            targetUser.setUserId(String.valueOf(currentUserId));
            
            // Create a UserPlatform for the selected platform
            com.aria.platform.UserPlatform userPlatform = new com.aria.platform.UserPlatform(
                targetDTO.getUsername(),
                "", // number - optional here
                targetDTO.getPlatformAccountId() != null ? targetDTO.getPlatformAccountId() : 0,
                targetDTO.getPlatform()
            );
            targetUser.setPlatforms(java.util.Arrays.asList(userPlatform));
            targetUser.setSelectedPlatformIndex(0);

            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            boolean success = targetUserService.saveTargetUser(currentUserId, targetUser);

            if (success) {
                // Persist questionnaire profile to target_users.profile_json
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
                    // get target id by unique (user_id, name)
                    int targetId = -1;
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM target_users WHERE user_id = ? AND name = ?")) {
                        ps.setInt(1, currentUserId);
                        ps.setString(2, targetUser.getName());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) targetId = rs.getInt(1);
                        }
                    }
                    if (targetId > 0) {
                        org.json.JSONObject profile = new org.json.JSONObject();
                        if (targetDTO.getDesiredOutcome() != null) profile.put("desiredOutcome", targetDTO.getDesiredOutcome());
                        if (targetDTO.getMeetingContext() != null) profile.put("meetingContext", targetDTO.getMeetingContext());
                        if (targetDTO.getContextDetails() != null) profile.put("contextDetails", targetDTO.getContextDetails());
                        
                        // Add ChatProfile fields
                        if (targetDTO.getHumorLevel() != null) profile.put("humorLevel", targetDTO.getHumorLevel());
                        if (targetDTO.getFormalityLevel() != null) profile.put("formalityLevel", targetDTO.getFormalityLevel());
                        if (targetDTO.getEmpathyLevel() != null) profile.put("empathyLevel", targetDTO.getEmpathyLevel());
                        if (targetDTO.getResponseTimeAverage() != null) profile.put("responseTimeAverage", targetDTO.getResponseTimeAverage());
                        if (targetDTO.getMessageLengthAverage() != null) profile.put("messageLengthAverage", targetDTO.getMessageLengthAverage());
                        if (targetDTO.getQuestionRate() != null) profile.put("questionRate", targetDTO.getQuestionRate());
                        if (targetDTO.getEngagementLevel() != null) profile.put("engagementLevel", targetDTO.getEngagementLevel());
                        if (targetDTO.getPreferredOpening() != null) profile.put("preferredOpening", targetDTO.getPreferredOpening());
                        
                        try (java.sql.PreparedStatement up = conn.prepareStatement(
                                "UPDATE target_users SET profile_json = ?::jsonb WHERE id = ?")) {
                            up.setString(1, profile.toString());
                            up.setInt(2, targetId);
                            up.executeUpdate();
                        }
                    }
                } catch (Exception ignore) {}

                // Get the saved target user to return with ID
                List<TargetUser> targets = targetUserService.getTargetUsersByUserId(currentUserId);
                TargetUser savedTarget = targets.stream()
                    .filter(t -> t.getName().equals(targetUser.getName()))
                    .findFirst()
                    .orElse(targetUser);
                
                // Trigger priority ingestion for this target user (last 50 messages)
                // This ensures messages are available immediately when user opens conversation
                if (targetDTO.getPlatformAccountId() != null && targetDTO.getUsername() != null) {
                    try {
                        DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(targetDTO.getPlatformAccountId());
                        if (acc != null && "TELEGRAM".equalsIgnoreCase(acc.platform)) {
                            com.aria.platform.PlatformConnector connectorBase = 
                                com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
                            if (connectorBase instanceof com.aria.platform.telegram.TelegramConnector) {
                                com.aria.platform.telegram.TelegramConnector connector = 
                                    (com.aria.platform.telegram.TelegramConnector) connectorBase;
                                // Trigger priority ingestion in background thread
                                String targetUsername = targetDTO.getUsername();
                                if (targetUsername != null && targetUsername.startsWith("@")) {
                                    targetUsername = targetUsername.substring(1);
                                }
                                final String finalTargetUsername = targetUsername;
                                new Thread(() -> {
                                    try {
                                        System.out.println("Starting priority ingestion for target user: " + finalTargetUsername);
                                        connector.ingestChatHistory(finalTargetUsername);
                                    } catch (Exception e) {
                                        System.err.println("Error in priority ingestion: " + e.getMessage());
                                    }
                                }, "priority-ingest-" + finalTargetUsername).start();
                            }
                        }
                    } catch (Exception e) {
                        // Log but don't fail target creation if priority ingestion fails
                        System.err.println("Warning: Failed to trigger priority ingestion: " + e.getMessage());
                    }
                }
                
                return ResponseEntity.ok(ApiResponse.success("Target user created", new TargetUserDTO(savedTarget)));
            } else {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create target user"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error creating target: " + e.getMessage()));
        }
    }

    /**
     * Get a specific target user
     * GET /api/targets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TargetUserDTO>> getTarget(
            @PathVariable("id") Integer id,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            
            // Get all targets and find by ID
            List<TargetUser> targets = targetUserService.getTargetUsersByUserId(currentUserId);
            TargetUser target = targets.stream()
                .filter(t -> t.getTargetId() == id)
                .findFirst()
                .orElse(null);
            
            if (target != null) {
                TargetUserDTO dto = new TargetUserDTO(target);
                
                // Load platform account username and name if platformAccountId is available
                if (dto.getPlatformAccountId() != null) {
                    try {
                        DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(dto.getPlatformAccountId());
                        if (acc != null) {
                            if (acc.username != null && !acc.username.isEmpty()) {
                                dto.setPlatformAccountUsername(acc.username);
                            }
                            if (acc.accountName != null && !acc.accountName.isEmpty()) {
                                dto.setPlatformAccountName(acc.accountName);
                            }
                        }
                    } catch (Exception e) {
                        // Log but continue - account info is optional
                        System.err.println("Warning: Failed to load platform account info: " + e.getMessage());
                    }
                }
                
                // Load ChatProfile fields from profile_json
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
                            "SELECT profile_json, profile_picture_url FROM target_users WHERE id = ?")) {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                // Load profile picture URL
                                String profilePictureUrl = rs.getString("profile_picture_url");
                                if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
                                    dto.setProfilePictureUrl(profilePictureUrl);
                                }
                                
                                String profileJson = rs.getString("profile_json");
                                if (profileJson != null && !profileJson.isEmpty()) {
                                    org.json.JSONObject profile = new org.json.JSONObject(profileJson);
                                    
                                    // Load basic fields
                                    if (profile.has("desiredOutcome")) dto.setDesiredOutcome(profile.getString("desiredOutcome"));
                                    if (profile.has("meetingContext")) dto.setMeetingContext(profile.getString("meetingContext"));
                                    if (profile.has("contextDetails")) dto.setContextDetails(profile.getString("contextDetails"));
                                    
                                    // Load ChatProfile fields
                                    if (profile.has("humorLevel")) dto.setHumorLevel(profile.getDouble("humorLevel"));
                                    if (profile.has("formalityLevel")) dto.setFormalityLevel(profile.getDouble("formalityLevel"));
                                    if (profile.has("empathyLevel")) dto.setEmpathyLevel(profile.getDouble("empathyLevel"));
                                    if (profile.has("responseTimeAverage")) dto.setResponseTimeAverage(profile.getDouble("responseTimeAverage"));
                                    if (profile.has("messageLengthAverage")) dto.setMessageLengthAverage(profile.getDouble("messageLengthAverage"));
                                    if (profile.has("questionRate")) dto.setQuestionRate(profile.getDouble("questionRate"));
                                    if (profile.has("engagementLevel")) dto.setEngagementLevel(profile.getDouble("engagementLevel"));
                                    if (profile.has("preferredOpening")) dto.setPreferredOpening(profile.getString("preferredOpening"));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Log but continue - profile_json is optional
                    System.err.println("Warning: Failed to load profile_json: " + e.getMessage());
                }
                
                return ResponseEntity.ok(ApiResponse.success(dto));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error fetching target: " + e.getMessage()));
        }
    }

    /**
     * Update a target user
     * PUT /api/targets/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TargetUserDTO>> updateTarget(
            @PathVariable("id") Integer id,
            @RequestBody TargetUserDTO targetDTO,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            
            // Get existing target
            List<TargetUser> targets = targetUserService.getTargetUsersByUserId(currentUserId);
            TargetUser target = targets.stream()
                .filter(t -> t.getTargetId() == id)
                .findFirst()
                .orElse(null);
            
            if (target == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Update target user fields
            if (targetDTO.getName() != null) {
                target.setName(targetDTO.getName());
            }
            
            // Update platforms if provided
            if (targetDTO.getPlatform() != null && targetDTO.getUsername() != null) {
                com.aria.platform.UserPlatform platform = new com.aria.platform.UserPlatform(
                    targetDTO.getUsername(),
                    "",
                    targetDTO.getPlatformAccountId() != null ? targetDTO.getPlatformAccountId() : 0,
                    targetDTO.getPlatform()
                );
                target.setPlatforms(java.util.Arrays.asList(platform));
                target.setSelectedPlatformIndex(0);
            }
            
            // Save updated target user
            boolean success = targetUserService.saveTargetUser(currentUserId, target);
            
            if (success) {
                // Update profile_json with all fields
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
                    // Get existing profile_json first to merge
                    org.json.JSONObject profile = new org.json.JSONObject();
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT profile_json FROM target_users WHERE id = ?")) {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String existingJson = rs.getString(1);
                                if (existingJson != null && !existingJson.isEmpty()) {
                                    profile = new org.json.JSONObject(existingJson);
                                }
                            }
                        }
                    }
                    
                    // Update with new values (only if provided)
                    if (targetDTO.getDesiredOutcome() != null) profile.put("desiredOutcome", targetDTO.getDesiredOutcome());
                    if (targetDTO.getMeetingContext() != null) profile.put("meetingContext", targetDTO.getMeetingContext());
                    if (targetDTO.getContextDetails() != null) profile.put("contextDetails", targetDTO.getContextDetails());
                    
                    // Update ChatProfile fields
                    if (targetDTO.getHumorLevel() != null) profile.put("humorLevel", targetDTO.getHumorLevel());
                    if (targetDTO.getFormalityLevel() != null) profile.put("formalityLevel", targetDTO.getFormalityLevel());
                    if (targetDTO.getEmpathyLevel() != null) profile.put("empathyLevel", targetDTO.getEmpathyLevel());
                    if (targetDTO.getResponseTimeAverage() != null) profile.put("responseTimeAverage", targetDTO.getResponseTimeAverage());
                    if (targetDTO.getMessageLengthAverage() != null) profile.put("messageLengthAverage", targetDTO.getMessageLengthAverage());
                    if (targetDTO.getQuestionRate() != null) profile.put("questionRate", targetDTO.getQuestionRate());
                    if (targetDTO.getEngagementLevel() != null) profile.put("engagementLevel", targetDTO.getEngagementLevel());
                    if (targetDTO.getPreferredOpening() != null) profile.put("preferredOpening", targetDTO.getPreferredOpening());
                    
                    try (java.sql.PreparedStatement up = conn.prepareStatement(
                            "UPDATE target_users SET profile_json = ?::jsonb WHERE id = ?")) {
                        up.setString(1, profile.toString());
                        up.setInt(2, id);
                        up.executeUpdate();
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to update profile_json: " + e.getMessage());
                }
                
                // Return updated target
                return getTarget(id, userId);
            } else {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to update target user"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error updating target: " + e.getMessage()));
        }
    }

    /**
     * Delete a target user
     * DELETE /api/targets/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTarget(
            @PathVariable("id") Integer id,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            
            // Get target name first
            List<TargetUser> targets = targetUserService.getTargetUsersByUserId(currentUserId);
            TargetUser target = targets.stream()
                .filter(t -> t.getTargetId() == id)
                .findFirst()
                .orElse(null);
            
            if (target == null) {
                return ResponseEntity.notFound().build();
            }
            
            boolean deleted = targetUserService.deleteTargetUser(currentUserId, target.getName());
            
            if (deleted) {
                return ResponseEntity.ok(ApiResponse.success("Target user deleted", null));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error deleting target: " + e.getMessage()));
        }
    }

    /**
     * Check if a target user is online
     * GET /api/targets/{id}/online?userId=1
     * Returns a Map with "isOnline" (boolean) and "lastActive" (String)
     */
    @GetMapping("/{id}/online")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkOnlineStatus(
            @PathVariable("id") Integer id,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            
            // Get target user
            List<TargetUser> targets = targetUserService.getTargetUsersByUserId(currentUserId);
            TargetUser target = targets.stream()
                .filter(t -> t.getTargetId() == id)
                .findFirst()
                .orElse(null);
            
            if (target == null) {
                return ResponseEntity.notFound().build();
            }

            com.aria.platform.UserPlatform selected = target.getSelectedPlatform();
            if (selected == null) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("isOnline", false);
                errorResult.put("lastActive", "unknown");
                return ResponseEntity.ok(ApiResponse.success(errorResult));
            }

            // Only Telegram supports online status checking
            if (selected.getPlatform() != Platform.TELEGRAM) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("isOnline", false);
                errorResult.put("lastActive", "not supported");
                return ResponseEntity.ok(ApiResponse.success(errorResult));
            }

            // Get platform account
            DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
            if (acc == null) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("isOnline", false);
                errorResult.put("lastActive", "unknown");
                return ResponseEntity.ok(ApiResponse.success(errorResult));
            }

            // Check online status using Telegram connector
            com.aria.platform.telegram.TelegramConnector connector =
                    (com.aria.platform.telegram.TelegramConnector)
                    com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
            
            com.aria.platform.telegram.TelegramConnector.OnlineStatus status = 
                connector.checkUserOnline(selected.getUsername());
            
            Map<String, Object> result = new HashMap<>();
            result.put("isOnline", status.online);
            result.put("lastActive", status.lastActive);
            
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            // On error, assume offline
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("isOnline", false);
            errorResult.put("lastActive", "error");
            return ResponseEntity.ok(ApiResponse.success(errorResult));
        }
    }
    
    /**
     * Upload profile picture for a target user
     * POST /api/targets/{id}/profile-picture?userId=...
     */
    @PostMapping("/{id}/profile-picture")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadProfilePicture(
            @PathVariable("id") Integer targetId,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            int currentUserId = userId != null ? userId : 1;
            
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty"));
            }
            
            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File must be an image"));
            }
            
            // Get target user to find platform account and chat name
            DatabaseManager databaseManager = new DatabaseManager();
            TargetUserService targetUserService = new TargetUserService(databaseManager);
            TargetUser targetUser = targetUserService.getTargetUserById(targetId);
            if (targetUser == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Target user not found"));
            }
            
            // Get platform account to determine user folder
            com.aria.platform.UserPlatform selected = targetUser.getSelectedPlatform();
            if (selected == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Target user has no selected platform"));
            }
            
            DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
            if (acc == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Platform account not found"));
            }
            
            // Use same folder structure as dialog medias: media/telegramConnector/user_<username>/<chat_name>_chat/
            String userLabel = (acc.username != null && !acc.username.isBlank()) ? acc.username : 
                              (acc.number != null ? acc.number : "unknown");
            
            // Get the actual dialog name from database (matches what Python script uses)
            // This ensures we use the same folder name as the Python ingestion script
            // The dialog name is the Telegram display name (e.g., "Philip inno 2023"), not the target user name
            String chatName = targetUser.getName(); // Default to target name
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
                // Try to find dialog by matching target user name first
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT name FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type = 'private' AND (name = ? OR name LIKE ?) ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, currentUserId);
                    ps.setInt(2, selected.getPlatformId());
                    ps.setString(3, targetUser.getName());
                    ps.setString(4, targetUser.getName() + "%"); // Also try partial match
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String dialogName = rs.getString("name");
                            if (dialogName != null && !dialogName.isEmpty()) {
                                chatName = dialogName;
                            }
                        }
                    }
                }
                
                // If not found, try to get any dialog for this platform account (fallback)
                if (chatName.equals(targetUser.getName())) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT name FROM dialogs WHERE user_id = ? AND platform_account_id = ? AND type = 'private' ORDER BY last_synced DESC NULLS LAST, id DESC LIMIT 1")) {
                        ps.setInt(1, currentUserId);
                        ps.setInt(2, selected.getPlatformId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String dialogName = rs.getString("name");
                                if (dialogName != null && !dialogName.isEmpty()) {
                                    // Only use if it seems related (contains target name or starts with it)
                                    if (dialogName.contains(targetUser.getName()) || targetUser.getName().contains(dialogName.split(" ")[0])) {
                                        chatName = dialogName;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to get dialog name from database, using target name: " + e.getMessage());
            }
            
            // Apply same sanitization as Python script: remove only [<>:"/\\|?*] characters, preserve spaces
            // Python: re.sub(r'[<>:"/\\|?*]', '', chat_name).strip()
            chatName = chatName.replaceAll("[<>:\"/\\\\|?*]", "").trim();
            if (chatName.isEmpty()) {
                chatName = targetUser.getName().replaceAll("[<>:\"/\\\\|?*]", "").trim();
                if (chatName.isEmpty()) {
                    chatName = "unknown";
                }
            }
            
            String fileName = "profile_" + targetId + "_" + System.currentTimeMillis() + 
                getFileExtension(file.getOriginalFilename());
            
            // Save to the same folder as dialog medias
            // Use absolute path to avoid Tomcat work directory issues
            java.nio.file.Path baseDir = java.nio.file.Paths.get(System.getProperty("user.dir"));
            java.nio.file.Path mediaDir = baseDir.resolve("media").resolve("telegramConnector").resolve("user_" + userLabel).resolve(chatName + "_chat");
            
            // Ensure parent directories exist
            try {
                java.nio.file.Files.createDirectories(mediaDir);
                System.out.println("Created profile picture directory: " + mediaDir.toAbsolutePath().toString());
            } catch (Exception e) {
                System.err.println("Failed to create directory: " + mediaDir.toAbsolutePath().toString());
                System.err.println("Error: " + e.getMessage());
                throw new RuntimeException("Failed to create media directory: " + e.getMessage(), e);
            }
            
            java.nio.file.Path filePath = mediaDir.resolve(fileName);
            
            System.out.println("Target user name: " + targetUser.getName());
            System.out.println("Chat name used: " + chatName);
            System.out.println("User label: " + userLabel);
            System.out.println("Full file path: " + filePath.toAbsolutePath().toString());
            
            // Verify directory exists before writing
            if (!java.nio.file.Files.exists(mediaDir)) {
                throw new RuntimeException("Media directory does not exist: " + mediaDir.toAbsolutePath().toString());
            }
            
            // Write file
            try {
                file.transferTo(filePath.toFile());
                System.out.println("Profile picture saved successfully to: " + filePath.toAbsolutePath().toString());
            } catch (Exception e) {
                System.err.println("Failed to save file to: " + filePath.toAbsolutePath().toString());
                System.err.println("Error: " + e.getMessage());
                throw new RuntimeException("Failed to save profile picture: " + e.getMessage(), e);
            }
            
            // Save relative path to database (same structure as media files)
            // Format: media/telegramConnector/user_<username>/<chat_name>_chat/<fileName>
            // Use relative path from project root for database storage
            String relativePath = "media/telegramConnector/user_" + userLabel + "/" + chatName + "_chat/" + fileName;
            // Store the full API URL for the frontend to use directly
            String profilePictureUrl = "/api/targets/" + targetId + "/profile-picture?path=" + 
                java.net.URLEncoder.encode(relativePath, "UTF-8");
            
            System.out.println("Profile picture uploaded for target " + targetId + ": " + profilePictureUrl);
            System.out.println("File saved to: " + filePath.toString());
            
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
                        "UPDATE target_users SET profile_picture_url = ? WHERE id = ? AND user_id = ?")) {
                    ps.setString(1, profilePictureUrl);
                    ps.setInt(2, targetId);
                    ps.setInt(3, currentUserId);
                    ps.executeUpdate();
                }
            }
            
            // Invalidate cache
            com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
            cache.invalidateTarget(currentUserId, targetId);
            
            Map<String, String> response = new HashMap<>();
            response.put("profilePictureUrl", profilePictureUrl);
            return ResponseEntity.ok(ApiResponse.success("Profile picture uploaded", response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to upload profile picture: " + e.getMessage()));
        }
    }
    
    /**
     * Serve profile picture file
     * GET /api/targets/{id}/profile-picture?path=<encoded_path>
     * Also supports legacy format: GET /api/targets/{id}/profile-picture/{fileName}
     */
    @GetMapping({"/{id}/profile-picture", "/{id}/profile-picture/{fileName}"})
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> serveProfilePicture(
            @PathVariable("id") Integer targetId,
            @PathVariable(value = "fileName", required = false) String fileName,
            @org.springframework.web.bind.annotation.RequestParam(value = "path", required = false) String path) {
        try {
            java.nio.file.Path filePath;
            
            // New format: use path parameter (from database)
            if (path != null && !path.isEmpty()) {
                try {
                    String decodedPath = java.net.URLDecoder.decode(path, "UTF-8");
                    filePath = java.nio.file.Paths.get(decodedPath);
                } catch (Exception e) {
                    // Fallback: try using path as-is
                    filePath = java.nio.file.Paths.get(path);
                }
            } 
            // Legacy format: try old location first, then check database
            else if (fileName != null && !fileName.isEmpty()) {
                // First try old location
                java.nio.file.Path oldPath = java.nio.file.Paths.get("media/profiles").resolve(fileName);
                if (java.nio.file.Files.exists(oldPath)) {
                    filePath = oldPath;
                } else {
                    // If not found, try to get path from database
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
                                "SELECT profile_picture_url FROM target_users WHERE id = ?")) {
                            ps.setInt(1, targetId);
                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    String url = rs.getString("profile_picture_url");
                                    if (url != null && url.contains("path=")) {
                                        String pathParam = url.substring(url.indexOf("path=") + 5);
                                        String decodedPath = java.net.URLDecoder.decode(pathParam, "UTF-8");
                                        filePath = java.nio.file.Paths.get(decodedPath);
                                    } else {
                                        filePath = oldPath; // Fallback to old path
                                    }
                                } else {
                                    filePath = oldPath; // Fallback to old path
                                }
                            }
                        }
                    } catch (Exception e) {
                        filePath = oldPath; // Fallback to old path
                    }
                }
            } else {
                // No path or fileName provided, try to get from database
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
                            "SELECT profile_picture_url FROM target_users WHERE id = ?")) {
                        ps.setInt(1, targetId);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String url = rs.getString("profile_picture_url");
                                if (url != null && url.contains("path=")) {
                                    String pathParam = url.substring(url.indexOf("path=") + 5);
                                    String decodedPath = java.net.URLDecoder.decode(pathParam, "UTF-8");
                                    filePath = java.nio.file.Paths.get(decodedPath);
                                } else {
                                    return org.springframework.http.ResponseEntity.notFound().build();
                                }
                            } else {
                                return org.springframework.http.ResponseEntity.notFound().build();
                            }
                        }
                    }
                } catch (Exception e) {
                    return org.springframework.http.ResponseEntity.notFound().build();
                }
            }
            
            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(filePath);
            
            if (resource.exists() && resource.isReadable()) {
                String contentType = org.springframework.http.MediaType.IMAGE_JPEG_VALUE;
                String fileNameStr = filePath.getFileName().toString().toLowerCase();
                if (fileNameStr.endsWith(".png")) {
                    contentType = org.springframework.http.MediaType.IMAGE_PNG_VALUE;
                } else if (fileNameStr.endsWith(".gif")) {
                    contentType = org.springframework.http.MediaType.IMAGE_GIF_VALUE;
                } else if (fileNameStr.endsWith(".webp")) {
                    contentType = "image/webp";
                }
                
                return org.springframework.http.ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .body(resource);
            } else {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Get all available categories
     * GET /api/targets/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> getCategories() {
        try {
            List<String> categories = ChatCategory.getAllNames();
            return ResponseEntity.ok(ApiResponse.success(categories));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get categories: " + e.getMessage()));
        }
    }
    
    /**
     * Get analysis data for targets
     * GET /api/targets/analysis?userId=...&targetId=...&platform=...&category=...
     */
    @GetMapping("/analysis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalysis(
            @RequestParam("userId") Integer userId,
            @RequestParam(value = "targetId", required = false) Integer targetId,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "category", required = false) String category) {
        try {
            // Check cache first
            com.aria.cache.RedisCacheManager cache = com.aria.cache.RedisCacheManager.getInstance();
            Map<String, Object> cachedAnalysis = cache.getCachedAnalysis(userId, targetId, HashMap.class);
            if (cachedAnalysis != null) {
                return ResponseEntity.ok(ApiResponse.success("OK", cachedAnalysis));
            }
            
            // Calculate analysis (placeholder - implement actual logic)
            Map<String, Object> analysis = calculateAnalysis(userId, targetId, platform, category);
            
            // Cache for 5 minutes
            cache.cacheAnalysis(userId, targetId, analysis, 300);
            
            return ResponseEntity.ok(ApiResponse.success("OK", analysis));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get analysis: " + e.getMessage()));
        }
    }
    
    private Map<String, Object> calculateAnalysis(Integer userId, Integer targetId, String platform, String category) {
        Map<String, Object> analysis = new HashMap<>();
        
        try (Connection conn = java.sql.DriverManager.getConnection(
                System.getenv("DATABASE_URL") != null
                        ? System.getenv("DATABASE_URL")
                        : "jdbc:postgresql://localhost:5432/aria",
                System.getenv("DATABASE_USER") != null
                        ? System.getenv("DATABASE_USER")
                        : "postgres",
                System.getenv("DATABASE_PASSWORD") != null
                        ? System.getenv("DATABASE_PASSWORD")
                        : "Ezekiel(23)")) {
            
            // Build query filters
            StringBuilder whereClause = new StringBuilder("tu.user_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(userId);
            
            if (targetId != null) {
                whereClause.append(" AND tu.id = ?");
                params.add(targetId);
            }
            
            if (platform != null && !platform.equals("all")) {
                whereClause.append(" AND tup.platform = ?");
                params.add(platform);
            }
            
            // Get messages for analysis (last 3 months)
            String messagesSql = "SELECT m.text, m.sender, m.timestamp, m.has_media, d.id as dialog_id, tu.id as target_id, tu.name as target_name " +
                "FROM messages m " +
                "JOIN dialogs d ON m.dialog_id = d.id " +
                "JOIN target_user_platforms tup ON d.platform_account_id = tup.platform_id AND d.dialog_id = tup.platform_id " +
                "JOIN target_users tu ON tup.target_user_id = tu.id " +
                "WHERE " + whereClause.toString() + " " +
                "AND m.timestamp >= NOW() - INTERVAL '3 months' " +
                "ORDER BY m.timestamp DESC";
            
            List<MessageData> messages = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(messagesSql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MessageData msg = new MessageData();
                        String encryptedText = rs.getString("text");
                        if (encryptedText != null && !encryptedText.isEmpty()) {
                            try {
                                msg.text = com.aria.storage.SecureStorage.decrypt(encryptedText);
                            } catch (Exception e) {
                                msg.text = "";
                            }
                        }
                        msg.sender = rs.getString("sender");
                        msg.timestamp = rs.getTimestamp("timestamp");
                        msg.fromUser = "me".equalsIgnoreCase(msg.sender);
                        msg.hasMedia = rs.getBoolean("has_media");
                        msg.targetId = rs.getInt("target_id");
                        msg.targetName = rs.getString("target_name");
                        messages.add(msg);
                    }
                }
            }
            
            // Calculate metrics based on actual message data
            if (messages.isEmpty()) {
                // Return default values if no messages
                return getDefaultAnalysis();
            }
            
            // Separate messages by target vs non-target
            List<MessageData> targetMessages = new ArrayList<>();
            List<MessageData> nonTargetMessages = new ArrayList<>();
            Set<Integer> targetIds = new HashSet<>();
            
            if (targetId != null) {
                targetIds.add(targetId);
            } else {
                // Get all target IDs for this user
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM target_users WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            targetIds.add(rs.getInt("id"));
                        }
                    }
                }
            }
            
            for (MessageData msg : messages) {
                if (targetIds.contains(msg.targetId)) {
                    targetMessages.add(msg);
                } else {
                    nonTargetMessages.add(msg);
                }
            }
            
            // Sentiment Analysis (mock - based on message length and keywords)
            Map<String, Object> sentiment = calculateSentiment(targetMessages, nonTargetMessages);
            analysis.put("sentiment", sentiment);
            
            // Engagement Score
            Map<String, Object> engagement = calculateEngagement(targetMessages);
            analysis.put("engagement", engagement);
            
            // Disinterest Detection
            Map<String, Object> disinterest = detectDisinterest(targetMessages);
            analysis.put("disinterest", disinterest);
            
            // Conversation Flow
            Map<String, Object> flow = calculateConversationFlow(targetMessages);
            analysis.put("conversationFlow", flow);
            
            // Goal Progression (mock - would need actual goal data)
            Map<String, Object> goal = calculateGoalProgression(targetMessages);
            analysis.put("goalProgression", goal);
            
            // Top 5 Targets (only for general analysis)
            if (targetId == null) {
                List<Map<String, Object>> topTargets = calculateTopTargets(conn, userId, platform);
                analysis.put("topTargets", topTargets);
            }
            
        } catch (Exception e) {
            System.err.println("Error calculating analysis: " + e.getMessage());
            e.printStackTrace();
            // Return default analysis on error
            return getDefaultAnalysis();
        }
        
        return analysis;
    }
    
    private Map<String, Object> calculateSentiment(List<MessageData> targetMessages, 
                                                   List<MessageData> nonTargetMessages) {
        Map<String, Object> sentiment = new HashMap<>();
        
        // Simple sentiment based on positive/negative keywords and message length
        double targetSentiment = calculateMessageSentiment(targetMessages);
        double nonTargetSentiment = calculateMessageSentiment(nonTargetMessages);
        
        sentiment.put("average", targetSentiment);
        sentiment.put("trend", targetSentiment > 0.6 ? "positive" : targetSentiment < 0.4 ? "negative" : "neutral");
        sentiment.put("withTargets", targetSentiment);
        sentiment.put("withoutTargets", nonTargetMessages.isEmpty() ? 0.5 : nonTargetSentiment);
        sentiment.put("improvement", targetSentiment - (nonTargetMessages.isEmpty() ? 0.5 : nonTargetSentiment));
        
        return sentiment;
    }
    
    private double calculateMessageSentiment(List<MessageData> messages) {
        if (messages.isEmpty()) return 0.5;
        
        int positiveCount = 0;
        int negativeCount = 0;
        int totalLength = 0;
        
        String[] positiveWords = {"great", "good", "nice", "love", "happy", "excited", "thanks", "thank you", 
                                   "awesome", "amazing", "perfect", "wonderful", "yes", "sure", "okay", "ok"};
        String[] negativeWords = {"bad", "hate", "no", "sorry", "unfortunately", "can't", "won't", "disappointed",
                                   "sad", "angry", "frustrated", "problem", "issue", "wrong"};
        
        for (MessageData msg : messages) {
            if (msg.text == null || msg.text.isEmpty()) continue;
            
            String lowerText = msg.text.toLowerCase();
            totalLength += msg.text.length();
            
            for (String word : positiveWords) {
                if (lowerText.contains(word)) {
                    positiveCount++;
                    break;
                }
            }
            
            for (String word : negativeWords) {
                if (lowerText.contains(word)) {
                    negativeCount++;
                    break;
                }
            }
        }
        
        // Base sentiment on positive/negative ratio and message length (longer = more engaged = more positive)
        double ratio = totalLength > 0 ? (double) positiveCount / (positiveCount + negativeCount + 1) : 0.5;
        double lengthFactor = Math.min(1.0, totalLength / (messages.size() * 50.0)); // Normalize by expected length
        
        return Math.max(0.0, Math.min(1.0, 0.5 + (ratio - 0.5) * 0.5 + (lengthFactor - 0.5) * 0.3));
    }
    
    private Map<String, Object> calculateEngagement(List<MessageData> messages) {
        Map<String, Object> engagement = new HashMap<>();
        
        if (messages.isEmpty()) {
            engagement.put("score", 0.5);
            engagement.put("responsiveness", 0.5);
            engagement.put("messageLength", 0);
            engagement.put("initiationFrequency", 0.5);
            return engagement;
        }
        
        int userMessages = 0;
        int targetMessages = 0;
        int totalLength = 0;
        long totalResponseTime = 0;
        int responseCount = 0;
        
        Timestamp lastUserMessage = null;
        for (MessageData msg : messages) {
            if (msg.fromUser) {
                userMessages++;
                lastUserMessage = msg.timestamp;
            } else {
                targetMessages++;
                if (msg.text != null) {
                    totalLength += msg.text.length();
                }
                
                // Calculate response time
                if (lastUserMessage != null && msg.timestamp != null) {
                    long diff = msg.timestamp.getTime() - lastUserMessage.getTime();
                    if (diff > 0 && diff < 86400000) { // Less than 24 hours
                        totalResponseTime += diff / 1000; // Convert to seconds
                        responseCount++;
                    }
                }
            }
        }
        
        double responsiveness = messages.size() > 0 ? (double) targetMessages / messages.size() : 0.5;
        double avgLength = targetMessages > 0 ? (double) totalLength / targetMessages : 0;
        double initiationFreq = messages.size() > 0 ? (double) userMessages / messages.size() : 0.5;
        double avgResponseTime = responseCount > 0 ? (double) totalResponseTime / responseCount : 300; // Default 5 min
        
        // Engagement score: combination of responsiveness, message length, and response time
        double score = (responsiveness * 0.4) + 
                      (Math.min(1.0, avgLength / 100.0) * 0.3) + 
                      (Math.min(1.0, 600.0 / Math.max(60.0, avgResponseTime)) * 0.3);
        
        engagement.put("score", Math.max(0.0, Math.min(1.0, score)));
        engagement.put("responsiveness", Math.max(0.0, Math.min(1.0, responsiveness)));
        engagement.put("messageLength", (int) avgLength);
        engagement.put("initiationFrequency", Math.max(0.0, Math.min(1.0, initiationFreq)));
        
        return engagement;
    }
    
    private Map<String, Object> detectDisinterest(List<MessageData> messages) {
        Map<String, Object> disinterest = new HashMap<>();
        List<String> signs = new ArrayList<>();
        
        if (messages.size() < 5) {
            disinterest.put("detected", false);
            disinterest.put("signs", signs);
            return disinterest;
        }
        
        // Check for disinterest signs
        int shortResponses = 0;
        int longDelays = 0;
        int noInitiation = 0;
        
        Timestamp lastUserMessage = null;
        boolean userInitiated = false;
        
        for (MessageData msg : messages) {
            if (msg.fromUser) {
                lastUserMessage = msg.timestamp;
                userInitiated = true;
            } else {
                // Check for short responses
                if (msg.text != null && msg.text.length() < 10) {
                    shortResponses++;
                }
                
                // Check for long delays
                if (lastUserMessage != null && msg.timestamp != null) {
                    long delay = (msg.timestamp.getTime() - lastUserMessage.getTime()) / (1000 * 60 * 60); // hours
                    if (delay > 24) {
                        longDelays++;
                    }
                }
            }
        }
        
        // Check if target never initiates
        boolean targetInitiates = false;
        for (int i = 1; i < messages.size(); i++) {
            MessageData prev = messages.get(i - 1);
            MessageData curr = messages.get(i);
            if (!prev.fromUser && curr.fromUser && 
                prev.timestamp != null && curr.timestamp != null) {
                long gap = (curr.timestamp.getTime() - prev.timestamp.getTime()) / (1000 * 60 * 60);
                if (gap > 2) { // More than 2 hours gap suggests target initiated
                    targetInitiates = true;
                    break;
                }
            }
        }
        if (!targetInitiates && messages.size() > 10) {
            noInitiation++;
        }
        
        boolean detected = false;
        if (shortResponses > messages.size() * 0.5) {
            signs.add("Frequent short responses");
            detected = true;
        }
        if (longDelays > messages.size() * 0.3) {
            signs.add("Frequent long response delays");
            detected = true;
        }
        if (noInitiation > 0) {
            signs.add("Target rarely initiates conversation");
            detected = true;
        }
        
        disinterest.put("detected", detected);
        disinterest.put("signs", signs);
        
        return disinterest;
    }
    
    private Map<String, Object> calculateConversationFlow(List<MessageData> messages) {
        Map<String, Object> flow = new HashMap<>();
        
        if (messages.size() < 2) {
            flow.put("avgResponseTime", 300);
            flow.put("turnTaking", 0.5);
            return flow;
        }
        
        long totalResponseTime = 0;
        int responseCount = 0;
        int turnSwitches = 0;
        
        for (int i = 1; i < messages.size(); i++) {
            MessageData prev = messages.get(i - 1);
            MessageData curr = messages.get(i);
            
            if (prev.timestamp != null && curr.timestamp != null) {
                long diff = (curr.timestamp.getTime() - prev.timestamp.getTime()) / 1000; // seconds
                
                // Response time (when target responds to user)
                if (!prev.fromUser && curr.fromUser) {
                    totalResponseTime += diff;
                    responseCount++;
                }
                
                // Turn-taking (alternating messages)
                if (prev.fromUser != curr.fromUser) {
                    turnSwitches++;
                }
            }
        }
        
        double avgResponseTime = responseCount > 0 ? (double) totalResponseTime / responseCount : 300;
        double turnTaking = messages.size() > 1 ? (double) turnSwitches / (messages.size() - 1) : 0.5;
        
        flow.put("avgResponseTime", (int) avgResponseTime);
        flow.put("turnTaking", Math.max(0.0, Math.min(1.0, turnTaking)));
        
        return flow;
    }
    
    private Map<String, Object> calculateGoalProgression(List<MessageData> messages) {
        Map<String, Object> goal = new HashMap<>();
        
        // Mock goal progression based on engagement and sentiment
        if (messages.isEmpty()) {
            goal.put("score", 0.0);
            goal.put("status", "not_started");
            return goal;
        }
        
        // Calculate based on message frequency and engagement
        long daysSinceFirst = 1;
        if (messages.size() > 0 && messages.get(messages.size() - 1).timestamp != null) {
            long firstTime = messages.get(messages.size() - 1).timestamp.getTime();
            long lastTime = messages.get(0).timestamp.getTime();
            daysSinceFirst = Math.max(1, (lastTime - firstTime) / (1000 * 60 * 60 * 24));
        }
        
        double messageFrequency = (double) messages.size() / Math.max(1, daysSinceFirst);
        double progression = Math.min(1.0, messageFrequency / 10.0); // Normalize to 0-1
        
        goal.put("score", Math.max(0.0, Math.min(1.0, progression)));
        goal.put("status", progression > 0.7 ? "on_track" : progression > 0.3 ? "in_progress" : "needs_attention");
        
        return goal;
    }
    
    private List<Map<String, Object>> calculateTopTargets(Connection conn, Integer userId, String platform) 
            throws SQLException {
        List<Map<String, Object>> topTargets = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder("""
            SELECT tu.id, tu.name, COUNT(m.id) as message_count,
                   AVG(LENGTH(COALESCE(m.text, ''))) as avg_length,
                   COUNT(CASE WHEN m.sender != 'me' THEN 1 END) as target_responses
            FROM target_users tu
            JOIN target_user_platforms tup ON tu.id = tup.target_user_id
            JOIN dialogs d ON d.platform_account_id = tup.platform_id
            JOIN messages m ON m.dialog_id = d.id
            WHERE tu.user_id = ? AND m.timestamp >= NOW() - INTERVAL '3 months'
        """);
        
        List<Object> params = new ArrayList<>();
        params.add(userId);
        
        if (platform != null && !platform.equals("all")) {
            sql.append(" AND tup.platform = ?");
            params.add(platform);
        }
        
        sql.append(" GROUP BY tu.id, tu.name ORDER BY message_count DESC, avg_length DESC LIMIT 5");
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> target = new HashMap<>();
                    target.put("id", rs.getInt("id"));
                    target.put("name", rs.getString("name"));
                    
                    // Calculate quality score
                    int messageCount = rs.getInt("message_count");
                    double avgLength = rs.getDouble("avg_length");
                    int targetResponses = rs.getInt("target_responses");
                    double responseRate = messageCount > 0 ? (double) targetResponses / messageCount : 0.5;
                    
                    double score = (Math.min(1.0, messageCount / 100.0) * 0.3) +
                                  (Math.min(1.0, avgLength / 50.0) * 0.3) +
                                  (responseRate * 0.4);
                    
                    target.put("score", Math.max(0.0, Math.min(1.0, score)));
                    topTargets.add(target);
                }
            }
        }
        
        // Fill with defaults if less than 5
        while (topTargets.size() < 5) {
            Map<String, Object> target = new HashMap<>();
            target.put("id", topTargets.size() + 1);
            target.put("name", "Target " + (topTargets.size() + 1));
            target.put("score", 0.85 - (topTargets.size() * 0.03));
            topTargets.add(target);
        }
        
        return topTargets;
    }
    
    private Map<String, Object> getDefaultAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        
        Map<String, Object> sentiment = new HashMap<>();
        sentiment.put("average", 0.5);
        sentiment.put("trend", "neutral");
        sentiment.put("withTargets", 0.5);
        sentiment.put("withoutTargets", 0.5);
        sentiment.put("improvement", 0.0);
        analysis.put("sentiment", sentiment);
        
        Map<String, Object> engagement = new HashMap<>();
        engagement.put("score", 0.5);
        engagement.put("responsiveness", 0.5);
        engagement.put("messageLength", 0);
        engagement.put("initiationFrequency", 0.5);
        analysis.put("engagement", engagement);
        
        Map<String, Object> disinterest = new HashMap<>();
        disinterest.put("detected", false);
        disinterest.put("signs", new ArrayList<>());
        analysis.put("disinterest", disinterest);
        
        Map<String, Object> flow = new HashMap<>();
        flow.put("avgResponseTime", 300);
        flow.put("turnTaking", 0.5);
        analysis.put("conversationFlow", flow);
        
        Map<String, Object> goal = new HashMap<>();
        goal.put("score", 0.0);
        goal.put("status", "not_started");
        analysis.put("goalProgression", goal);
        
        return analysis;
    }
    
    private static class MessageData {
        String text;
        String sender;
        Timestamp timestamp;
        boolean fromUser;
        boolean hasMedia;
        int targetId;
        String targetName;
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot) : "";
    }
}

