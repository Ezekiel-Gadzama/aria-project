package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import com.aria.ai.BusinessContextBuilder;
import com.aria.ai.OpenAIResponsesClient;
import com.aria.core.model.TargetBusiness;
import com.aria.core.model.BusinessSubTarget;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.*;

/**
 * REST API Controller for Business Bot chat functionality
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/bot")
@CrossOrigin(origins = "*")
public class BusinessBotController {

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
     * Send a message to the business bot
     * POST /api/businesses/{businessId}/bot/chat?userId=...
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chatWithBot(
            @PathVariable("businessId") Integer businessId,
            @RequestBody Map<String, String> request,
            @RequestParam("userId") Integer userId) {
        try {
            String userMessage = request.get("message");
            if (userMessage == null || userMessage.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Message is required"));
            }

            // Verify business belongs to user
            TargetBusiness business = loadBusiness(businessId, userId);
            if (business == null) {
                return ResponseEntity.notFound().build();
            }

            // Load sub-targets for the business
            loadBusinessSubTargets(business);

            // Get or create bot conversation state
            String previousResponseId = getBotResponseId(businessId, userId);

            BusinessContextBuilder contextBuilder = new BusinessContextBuilder();
            String botResponse = null;

            if (previousResponseId == null) {
                // First message - build full business context
                try {
                    String fullContext = contextBuilder.buildBusinessContext(business, userId, userMessage);
                    
                    // Use OpenAI Responses API
                    OpenAIResponsesClient responsesClient = new OpenAIResponsesClient();
                    OpenAIResponsesClient.ResponseResult result = responsesClient.createResponse(fullContext);
                    
                    if (result.isSuccess() && result.responseId != null) {
                        saveBotResponseId(businessId, userId, result.responseId, null);
                        botResponse = result.outputText;
                    }
                } catch (Exception e) {
                    System.err.println("Error building business context: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // Continue conversation
                try {
                    OpenAIResponsesClient responsesClient = new OpenAIResponsesClient();
                    OpenAIResponsesClient.ResponseResult result = responsesClient.continueResponse(previousResponseId, userMessage);
                    
                    if (result.isSuccess() && result.responseId != null) {
                        saveBotResponseId(businessId, userId, result.responseId, null);
                        botResponse = result.outputText;
                    }
                } catch (Exception e) {
                    System.err.println("Error continuing bot conversation: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Fallback to default response if AI fails
            if (botResponse == null || botResponse.trim().isEmpty()) {
                botResponse = "I'm here to help you with questions about your business. " +
                            "I can answer questions about tasks, projects, and discussions " +
                            "across all channels, groups, and private chats in this business. " +
                            "What would you like to know?";
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", botResponse);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(ApiResponse.success("OK", response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to process bot message: " + e.getMessage()));
        }
    }

    /**
     * Get bot conversation history
     * GET /api/businesses/{businessId}/bot/history?userId=...
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBotHistory(
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

            // For now, return empty history
            // In the future, we could store bot conversation messages in a separate table
            List<Map<String, Object>> history = new ArrayList<>();
            return ResponseEntity.ok(ApiResponse.success("OK", history));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get bot history: " + e.getMessage()));
        }
    }

    /**
     * Load business from database
     */
    private TargetBusiness loadBusiness(int businessId, int userId) throws SQLException {
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, user_id, name, description, created_at, updated_at " +
                    "FROM target_businesses WHERE id = ? AND user_id = ?")) {
                ps.setInt(1, businessId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        TargetBusiness business = new TargetBusiness();
                        business.setId(rs.getInt("id"));
                        business.setUserId(rs.getInt("user_id"));
                        business.setName(rs.getString("name"));
                        business.setDescription(rs.getString("description"));
                        if (rs.getTimestamp("created_at") != null) {
                            business.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                        }
                        if (rs.getTimestamp("updated_at") != null) {
                            business.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                        }
                        return business;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Load sub-targets for a business
     */
    private void loadBusinessSubTargets(TargetBusiness business) throws SQLException {
        List<BusinessSubTarget> subTargets = new ArrayList<>();
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, business_id, name, type, platform, platform_account_id, " +
                    "dialog_id, platform_id, username, description, created_at " +
                    "FROM business_subtargets WHERE business_id = ? ORDER BY created_at DESC")) {
                ps.setInt(1, business.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BusinessSubTarget subTarget = new BusinessSubTarget();
                        subTarget.setId(rs.getInt("id"));
                        subTarget.setBusinessId(rs.getInt("business_id"));
                        subTarget.setName(rs.getString("name"));
                        
                        String typeStr = rs.getString("type");
                        if (typeStr != null) {
                            try {
                                subTarget.setType(BusinessSubTarget.SubTargetType.valueOf(typeStr));
                            } catch (IllegalArgumentException e) {
                                // Skip invalid types
                                continue;
                            }
                        }
                        
                        String platformStr = rs.getString("platform");
                        if (platformStr != null) {
                            try {
                                subTarget.setPlatform(com.aria.platform.Platform.valueOf(platformStr));
                            } catch (IllegalArgumentException e) {
                                // Skip invalid platforms
                                continue;
                            }
                        }
                        
                        subTarget.setPlatformAccountId(rs.getObject("platform_account_id", Integer.class));
                        subTarget.setDialogId(rs.getObject("dialog_id", Integer.class));
                        subTarget.setPlatformId(rs.getObject("platform_id", Long.class));
                        subTarget.setUsername(rs.getString("username"));
                        subTarget.setDescription(rs.getString("description"));
                        if (rs.getTimestamp("created_at") != null) {
                            subTarget.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                        }
                        subTargets.add(subTarget);
                    }
                }
            }
        }
        business.setSubTargets(subTargets);
    }

    /**
     * Get bot response ID for a business and user
     */
    private String getBotResponseId(int businessId, int userId) {
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT openai_response_id FROM business_bot_conversations " +
                    "WHERE business_id = ? AND user_id = ?")) {
                ps.setInt(1, businessId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("openai_response_id");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting bot response ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get bot last message ID
     */
    private Long getBotLastMessageId(int businessId, int userId) {
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT last_message_id FROM business_bot_conversations " +
                    "WHERE business_id = ? AND user_id = ?")) {
                ps.setInt(1, businessId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getObject("last_message_id", Long.class);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting bot last message ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Save bot response ID and last message ID
     */
    private void saveBotResponseId(int businessId, int userId, String responseId, Long lastMessageId) throws SQLException {
        try (Connection conn = getConnection()) {
            // Try to update existing record
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE business_bot_conversations " +
                    "SET openai_response_id = ?, last_message_id = ?, updated_at = NOW() " +
                    "WHERE business_id = ? AND user_id = ?")) {
                ps.setString(1, responseId);
                if (lastMessageId != null) {
                    ps.setLong(2, lastMessageId);
                } else {
                    ps.setNull(2, Types.BIGINT);
                }
                ps.setInt(3, businessId);
                ps.setInt(4, userId);
                int updated = ps.executeUpdate();
                
                // If no row was updated, insert new record
                if (updated == 0) {
                    try (PreparedStatement insertPs = conn.prepareStatement(
                            "INSERT INTO business_bot_conversations " +
                            "(business_id, user_id, openai_response_id, last_message_id, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, NOW(), NOW())")) {
                        insertPs.setInt(1, businessId);
                        insertPs.setInt(2, userId);
                        insertPs.setString(3, responseId);
                        if (lastMessageId != null) {
                            insertPs.setLong(4, lastMessageId);
                        } else {
                            insertPs.setNull(4, Types.BIGINT);
                        }
                        insertPs.executeUpdate();
                    }
                }
            }
        }
    }
}

