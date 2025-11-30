package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Business Sub-Target management
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/subtargets")
@CrossOrigin(origins = "*")
public class BusinessSubTargetController {

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
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
     * Get all sub-targets for a business
     * GET /api/businesses/{businessId}/subtargets?userId=...
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSubTargets(
            @PathVariable("businessId") Integer businessId,
            @RequestParam("userId") Integer userId) {
        try {
            // Verify business belongs to user
            try (Connection conn = getConnection()) {
                try (PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT id FROM target_businesses WHERE id = ? AND user_id = ?")) {
                    checkPs.setInt(1, businessId);
                    checkPs.setInt(2, userId);
                    try (ResultSet checkRs = checkPs.executeQuery()) {
                        if (!checkRs.next()) {
                            return ResponseEntity.notFound().build();
                        }
                    }
                }
            }
            
            List<Map<String, Object>> subTargets = new ArrayList<>();
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, business_id, name, type, platform, platform_account_id, " +
                        "dialog_id, platform_id, username, description, created_at " +
                        "FROM business_subtargets WHERE business_id = ? ORDER BY created_at DESC")) {
                    ps.setInt(1, businessId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> subTarget = new HashMap<>();
                            subTarget.put("id", rs.getInt("id"));
                            subTarget.put("businessId", rs.getInt("business_id"));
                            subTarget.put("name", rs.getString("name"));
                            subTarget.put("type", rs.getString("type"));
                            subTarget.put("platform", rs.getString("platform"));
                            subTarget.put("platformAccountId", rs.getObject("platform_account_id"));
                            subTarget.put("dialogId", rs.getObject("dialog_id"));
                            subTarget.put("platformId", rs.getObject("platform_id"));
                            subTarget.put("username", rs.getString("username"));
                            subTarget.put("description", rs.getString("description"));
                            if (rs.getTimestamp("created_at") != null) {
                                subTarget.put("createdAt", rs.getTimestamp("created_at").getTime());
                            }
                            subTargets.add(subTarget);
                        }
                    }
                }
            }
            return ResponseEntity.ok(ApiResponse.success("OK", subTargets));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get sub-targets: " + e.getMessage()));
        }
    }

    /**
     * Get a specific sub-target
     * GET /api/businesses/{businessId}/subtargets/{id}?userId=...
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSubTarget(
            @PathVariable("businessId") Integer businessId,
            @PathVariable("id") Integer id,
            @RequestParam("userId") Integer userId) {
        try {
            // Verify business belongs to user
            try (Connection conn = getConnection()) {
                try (PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT id FROM target_businesses WHERE id = ? AND user_id = ?")) {
                    checkPs.setInt(1, businessId);
                    checkPs.setInt(2, userId);
                    try (ResultSet checkRs = checkPs.executeQuery()) {
                        if (!checkRs.next()) {
                            return ResponseEntity.notFound().build();
                        }
                    }
                }
            }
            
            Map<String, Object> subTarget = null;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, business_id, name, type, platform, platform_account_id, " +
                        "dialog_id, platform_id, username, description, created_at " +
                        "FROM business_subtargets WHERE id = ? AND business_id = ?")) {
                    ps.setInt(1, id);
                    ps.setInt(2, businessId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            subTarget = new HashMap<>();
                            subTarget.put("id", rs.getInt("id"));
                            subTarget.put("businessId", rs.getInt("business_id"));
                            subTarget.put("name", rs.getString("name"));
                            subTarget.put("type", rs.getString("type"));
                            subTarget.put("platform", rs.getString("platform"));
                            subTarget.put("platformAccountId", rs.getObject("platform_account_id"));
                            subTarget.put("dialogId", rs.getObject("dialog_id"));
                            subTarget.put("platformId", rs.getObject("platform_id"));
                            subTarget.put("username", rs.getString("username"));
                            subTarget.put("description", rs.getString("description"));
                            if (rs.getTimestamp("created_at") != null) {
                                subTarget.put("createdAt", rs.getTimestamp("created_at").getTime());
                            }
                        }
                    }
                }
            }
            
            if (subTarget == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(ApiResponse.success("OK", subTarget));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get sub-target: " + e.getMessage()));
        }
    }

    /**
     * Add a new sub-target to a business
     * POST /api/businesses/{businessId}/subtargets?userId=...
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> addSubTarget(
            @PathVariable("businessId") Integer businessId,
            @RequestBody Map<String, Object> subTargetData,
            @RequestParam("userId") Integer userId) {
        try {
            // Verify business belongs to user
            try (Connection conn = getConnection()) {
                try (PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT id FROM target_businesses WHERE id = ? AND user_id = ?")) {
                    checkPs.setInt(1, businessId);
                    checkPs.setInt(2, userId);
                    try (ResultSet checkRs = checkPs.executeQuery()) {
                        if (!checkRs.next()) {
                            return ResponseEntity.notFound().build();
                        }
                    }
                }
            }
            
            String name = (String) subTargetData.get("name");
            String type = (String) subTargetData.get("type"); // CHANNEL, GROUP, PRIVATE_CHAT
            String platform = (String) subTargetData.get("platform");
            Integer platformAccountId = subTargetData.get("platformAccountId") != null 
                ? ((Number) subTargetData.get("platformAccountId")).intValue() : null;
            Integer dialogId = subTargetData.get("dialogId") != null 
                ? ((Number) subTargetData.get("dialogId")).intValue() : null;
            Long platformId = subTargetData.get("platformId") != null 
                ? ((Number) subTargetData.get("platformId")).longValue() : 0L;
            String username = (String) subTargetData.get("username");
            String description = (String) subTargetData.get("description");
            
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Sub-target name is required"));
            }
            if (type == null || (!type.equals("CHANNEL") && !type.equals("GROUP") && !type.equals("PRIVATE_CHAT"))) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Type must be CHANNEL, GROUP, or PRIVATE_CHAT"));
            }
            if (platform == null || platform.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Platform is required"));
            }
            
            Map<String, Object> createdSubTarget = null;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO business_subtargets " +
                        "(business_id, name, type, platform, platform_account_id, dialog_id, platform_id, username, description, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                        "RETURNING id, created_at")) {
                    ps.setInt(1, businessId);
                    ps.setString(2, name);
                    ps.setString(3, type);
                    ps.setString(4, platform);
                    if (platformAccountId != null) {
                        ps.setInt(5, platformAccountId);
                    } else {
                        ps.setNull(5, Types.INTEGER);
                    }
                    if (dialogId != null) {
                        ps.setInt(6, dialogId);
                    } else {
                        ps.setNull(6, Types.INTEGER);
                    }
                    ps.setLong(7, platformId);
                    ps.setString(8, username);
                    ps.setString(9, description);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            createdSubTarget = new HashMap<>();
                            createdSubTarget.put("id", rs.getInt("id"));
                            createdSubTarget.put("businessId", businessId);
                            createdSubTarget.put("name", name);
                            createdSubTarget.put("type", type);
                            createdSubTarget.put("platform", platform);
                            createdSubTarget.put("platformAccountId", platformAccountId);
                            createdSubTarget.put("dialogId", dialogId);
                            createdSubTarget.put("platformId", platformId);
                            createdSubTarget.put("username", username);
                            createdSubTarget.put("description", description);
                            if (rs.getTimestamp("created_at") != null) {
                                createdSubTarget.put("createdAt", rs.getTimestamp("created_at").getTime());
                            }
                        }
                    }
                }
            }
            
            if (createdSubTarget == null) {
                return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to create sub-target"));
            }
            
            return ResponseEntity.ok(ApiResponse.success("Sub-target added", createdSubTarget));
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE") || e.getMessage().contains("duplicate")) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("A sub-target with these properties already exists"));
            }
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to create sub-target: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to create sub-target: " + e.getMessage()));
        }
    }

    /**
     * Update a sub-target
     * PUT /api/businesses/{businessId}/subtargets/{id}?userId=...
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSubTarget(
            @PathVariable("businessId") Integer businessId,
            @PathVariable("id") Integer id,
            @RequestBody Map<String, Object> subTargetData,
            @RequestParam("userId") Integer userId) {
        try {
            // Verify business belongs to user
            try (Connection conn = getConnection()) {
                try (PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT id FROM target_businesses WHERE id = ? AND user_id = ?")) {
                    checkPs.setInt(1, businessId);
                    checkPs.setInt(2, userId);
                    try (ResultSet checkRs = checkPs.executeQuery()) {
                        if (!checkRs.next()) {
                            return ResponseEntity.notFound().build();
                        }
                    }
                }
            }
            
            String name = (String) subTargetData.get("name");
            String description = (String) subTargetData.get("description");
            
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Sub-target name is required"));
            }
            
            int updated = 0;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE business_subtargets SET name = ?, description = ? " +
                        "WHERE id = ? AND business_id = ?")) {
                    ps.setString(1, name);
                    ps.setString(2, description);
                    ps.setInt(3, id);
                    ps.setInt(4, businessId);
                    updated = ps.executeUpdate();
                }
            }
            
            if (updated == 0) {
                return ResponseEntity.notFound().build();
            }
            
            // Return updated sub-target
            return getSubTarget(businessId, id, userId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to update sub-target: " + e.getMessage()));
        }
    }

    /**
     * Delete a sub-target
     * DELETE /api/businesses/{businessId}/subtargets/{id}?userId=...
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteSubTarget(
            @PathVariable("businessId") Integer businessId,
            @PathVariable("id") Integer id,
            @RequestParam("userId") Integer userId) {
        try {
            // Verify business belongs to user
            try (Connection conn = getConnection()) {
                try (PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT id FROM target_businesses WHERE id = ? AND user_id = ?")) {
                    checkPs.setInt(1, businessId);
                    checkPs.setInt(2, userId);
                    try (ResultSet checkRs = checkPs.executeQuery()) {
                        if (!checkRs.next()) {
                            return ResponseEntity.notFound().build();
                        }
                    }
                }
            }
            
            int deleted = 0;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM business_subtargets WHERE id = ? AND business_id = ?")) {
                    ps.setInt(1, id);
                    ps.setInt(2, businessId);
                    deleted = ps.executeUpdate();
                }
            }
            
            if (deleted > 0) {
                return ResponseEntity.ok(ApiResponse.success("Sub-target deleted", null));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to delete sub-target: " + e.getMessage()));
        }
    }
}

