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
            List<TargetUserDTO> targetDTOs = targets.stream()
                .map(TargetUserDTO::new)
                .collect(Collectors.toList());

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
                return ResponseEntity.ok(ApiResponse.success(new TargetUserDTO(target)));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error fetching target: " + e.getMessage()));
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
}

