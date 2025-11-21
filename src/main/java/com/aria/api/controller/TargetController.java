package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import com.aria.api.dto.TargetUserDTO;
import com.aria.core.model.TargetUser;
import com.aria.platform.Platform;
import com.aria.service.TargetUserService;
import com.aria.service.UserService;
import com.aria.storage.DatabaseManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
            List<TargetUserDTO> targetDTOs = new java.util.ArrayList<>();
            
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
                    
                    // Load profile_json
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT profile_json FROM target_users WHERE id = ?")) {
                        ps.setInt(1, target.getTargetId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String profileJson = rs.getString(1);
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
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
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
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
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
                            "SELECT profile_json FROM target_users WHERE id = ?")) {
                        ps.setInt(1, id);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String profileJson = rs.getString(1);
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
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
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
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> checkOnlineStatus(
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
                java.util.Map<String, Object> errorResult = new java.util.HashMap<>();
                errorResult.put("isOnline", false);
                errorResult.put("lastActive", "unknown");
                return ResponseEntity.ok(ApiResponse.success(errorResult));
            }

            // Only Telegram supports online status checking
            if (selected.getPlatform() != Platform.TELEGRAM) {
                java.util.Map<String, Object> errorResult = new java.util.HashMap<>();
                errorResult.put("isOnline", false);
                errorResult.put("lastActive", "not supported");
                return ResponseEntity.ok(ApiResponse.success(errorResult));
            }

            // Get platform account
            DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(selected.getPlatformId());
            if (acc == null) {
                java.util.Map<String, Object> errorResult = new java.util.HashMap<>();
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
            
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("isOnline", status.online);
            result.put("lastActive", status.lastActive);
            
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            // On error, assume offline
            java.util.Map<String, Object> errorResult = new java.util.HashMap<>();
            errorResult.put("isOnline", false);
            errorResult.put("lastActive", "error");
            return ResponseEntity.ok(ApiResponse.success(errorResult));
        }
    }
}

