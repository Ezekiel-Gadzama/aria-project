package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import com.aria.core.model.TargetBusiness;
import com.aria.core.model.BusinessSubTarget;
import com.aria.storage.DatabaseManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Target Business management
 */
@RestController
@RequestMapping("/api/businesses")
@CrossOrigin(origins = "*")
public class BusinessController {

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
     * Get all target businesses for a user
     * GET /api/businesses?userId=...
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllBusinesses(
            @RequestParam("userId") Integer userId) {
        try {
            List<Map<String, Object>> businesses = new ArrayList<>();
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, user_id, name, description, created_at, updated_at " +
                        "FROM target_businesses WHERE user_id = ? ORDER BY created_at DESC")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> business = new HashMap<>();
                            business.put("id", rs.getInt("id"));
                            business.put("userId", rs.getInt("user_id"));
                            business.put("name", rs.getString("name"));
                            business.put("description", rs.getString("description"));
                            if (rs.getTimestamp("created_at") != null) {
                                business.put("createdAt", rs.getTimestamp("created_at").getTime());
                            }
                            if (rs.getTimestamp("updated_at") != null) {
                                business.put("updatedAt", rs.getTimestamp("updated_at").getTime());
                            }
                            
                            // Load sub-targets count
                            try (PreparedStatement countPs = conn.prepareStatement(
                                    "SELECT COUNT(*) as count FROM business_subtargets WHERE business_id = ?")) {
                                countPs.setInt(1, rs.getInt("id"));
                                try (ResultSet countRs = countPs.executeQuery()) {
                                    if (countRs.next()) {
                                        business.put("subTargetsCount", countRs.getInt("count"));
                                    }
                                }
                            }
                            
                            businesses.add(business);
                        }
                    }
                }
            }
            return ResponseEntity.ok(ApiResponse.success("OK", businesses));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get businesses: " + e.getMessage()));
        }
    }

    /**
     * Get a specific target business
     * GET /api/businesses/{id}?userId=...
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBusiness(
            @PathVariable("id") Integer id,
            @RequestParam("userId") Integer userId) {
        try {
            Map<String, Object> business = null;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, user_id, name, description, created_at, updated_at " +
                        "FROM target_businesses WHERE id = ? AND user_id = ?")) {
                    ps.setInt(1, id);
                    ps.setInt(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            business = new HashMap<>();
                            business.put("id", rs.getInt("id"));
                            business.put("userId", rs.getInt("user_id"));
                            business.put("name", rs.getString("name"));
                            business.put("description", rs.getString("description"));
                            if (rs.getTimestamp("created_at") != null) {
                                business.put("createdAt", rs.getTimestamp("created_at").getTime());
                            }
                            if (rs.getTimestamp("updated_at") != null) {
                                business.put("updatedAt", rs.getTimestamp("updated_at").getTime());
                            }
                            
                            // Load sub-targets
                            List<Map<String, Object>> subTargets = new ArrayList<>();
                            try (PreparedStatement subPs = conn.prepareStatement(
                                    "SELECT id, business_id, name, type, platform, platform_account_id, " +
                                    "dialog_id, platform_id, username, description, created_at " +
                                    "FROM business_subtargets WHERE business_id = ? ORDER BY created_at DESC")) {
                                subPs.setInt(1, id);
                                try (ResultSet subRs = subPs.executeQuery()) {
                                    while (subRs.next()) {
                                        Map<String, Object> subTarget = new HashMap<>();
                                        subTarget.put("id", subRs.getInt("id"));
                                        subTarget.put("businessId", subRs.getInt("business_id"));
                                        subTarget.put("name", subRs.getString("name"));
                                        subTarget.put("type", subRs.getString("type"));
                                        subTarget.put("platform", subRs.getString("platform"));
                                        subTarget.put("platformAccountId", subRs.getObject("platform_account_id"));
                                        subTarget.put("dialogId", subRs.getObject("dialog_id"));
                                        subTarget.put("platformId", subRs.getObject("platform_id"));
                                        subTarget.put("username", subRs.getString("username"));
                                        subTarget.put("description", subRs.getString("description"));
                                        if (subRs.getTimestamp("created_at") != null) {
                                            subTarget.put("createdAt", subRs.getTimestamp("created_at").getTime());
                                        }
                                        subTargets.add(subTarget);
                                    }
                                }
                            }
                            business.put("subTargets", subTargets);
                        }
                    }
                }
            }
            
            if (business == null) {
                return ResponseEntity.notFound()
                    .build();
            }
            
            return ResponseEntity.ok(ApiResponse.success("OK", business));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get business: " + e.getMessage()));
        }
    }

    /**
     * Create a new target business
     * POST /api/businesses?userId=...
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBusiness(
            @RequestBody Map<String, String> businessData,
            @RequestParam("userId") Integer userId) {
        try {
            String name = businessData.get("name");
            String description = businessData.get("description");
            
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Business name is required"));
            }
            
            Map<String, Object> createdBusiness = null;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO target_businesses (user_id, name, description, created_at, updated_at) " +
                        "VALUES (?, ?, ?, NOW(), NOW()) RETURNING id, created_at, updated_at")) {
                    ps.setInt(1, userId);
                    ps.setString(2, name);
                    ps.setString(3, description);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            createdBusiness = new HashMap<>();
                            createdBusiness.put("id", rs.getInt("id"));
                            createdBusiness.put("userId", userId);
                            createdBusiness.put("name", name);
                            createdBusiness.put("description", description);
                            if (rs.getTimestamp("created_at") != null) {
                                createdBusiness.put("createdAt", rs.getTimestamp("created_at").getTime());
                            }
                            if (rs.getTimestamp("updated_at") != null) {
                                createdBusiness.put("updatedAt", rs.getTimestamp("updated_at").getTime());
                            }
                            createdBusiness.put("subTargets", new ArrayList<>());
                        }
                    }
                }
            }
            
            if (createdBusiness == null) {
                return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to create business"));
            }
            
            return ResponseEntity.ok(ApiResponse.success("Business created", createdBusiness));
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE") || e.getMessage().contains("duplicate")) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("A business with this name already exists"));
            }
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to create business: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to create business: " + e.getMessage()));
        }
    }

    /**
     * Update a target business
     * PUT /api/businesses/{id}?userId=...
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBusiness(
            @PathVariable("id") Integer id,
            @RequestBody Map<String, String> businessData,
            @RequestParam("userId") Integer userId) {
        try {
            String name = businessData.get("name");
            String description = businessData.get("description");
            
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Business name is required"));
            }
            
            int updated = 0;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE target_businesses SET name = ?, description = ?, updated_at = NOW() " +
                        "WHERE id = ? AND user_id = ?")) {
                    ps.setString(1, name);
                    ps.setString(2, description);
                    ps.setInt(3, id);
                    ps.setInt(4, userId);
                    updated = ps.executeUpdate();
                }
            }
            
            if (updated == 0) {
                return ResponseEntity.notFound()
                    .build();
            }
            
            // Return updated business
            return getBusiness(id, userId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to update business: " + e.getMessage()));
        }
    }

    /**
     * Delete a target business
     * DELETE /api/businesses/{id}?userId=...
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteBusiness(
            @PathVariable("id") Integer id,
            @RequestParam("userId") Integer userId) {
        try {
            int deleted = 0;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM target_businesses WHERE id = ? AND user_id = ?")) {
                    ps.setInt(1, id);
                    ps.setInt(2, userId);
                    deleted = ps.executeUpdate();
                }
            }
            
            if (deleted > 0) {
                return ResponseEntity.ok(ApiResponse.success("Business deleted", null));
            } else {
                return ResponseEntity.notFound()
                    .build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to delete business: " + e.getMessage()));
        }
    }
}

