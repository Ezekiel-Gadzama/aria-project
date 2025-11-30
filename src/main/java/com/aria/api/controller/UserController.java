package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import com.aria.cache.RedisCacheManager;
import com.aria.storage.DatabaseManager;
import com.aria.storage.SecureStorage;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * User management controller for 2FA, API keys, credits, subscriptions
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
    
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private static final TimeProvider timeProvider = new SystemTimeProvider();
    private static final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private static final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    
    /**
     * Register a new user
     * POST /api/users/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @RequestBody Map<String, String> userData) {
        try {
            String phone = userData.get("phone");
            String email = userData.get("email");
            String username = email != null ? email : userData.get("username");
            String firstName = userData.get("firstName");
            String lastName = userData.get("lastName");
            String password = userData.get("password");
            String bio = userData.get("bio");
            
            if (password == null || password.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Password is required"));
            }
            
            // Hash password
            String passwordHash = passwordEncoder.encode(password);
            
            // Save user to database
            try (Connection conn = getConnection()) {
                String sql = """
                    INSERT INTO users (phone, username, first_name, last_name, bio, password_hash)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id
                """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, phone);
                    ps.setString(2, username);
                    ps.setString(3, firstName);
                    ps.setString(4, lastName);
                    ps.setString(5, bio);
                    ps.setString(6, passwordHash);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int userId = rs.getInt("id");
                            Map<String, Object> response = new HashMap<>();
                            response.put("id", userId);
                            response.put("phone", phone);
                            response.put("username", username);
                            response.put("firstName", firstName);
                            response.put("lastName", lastName);
                            return ResponseEntity.ok(ApiResponse.success("User registered", response));
                        }
                    }
                }
            }
            
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to register user"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Registration failed: " + e.getMessage()));
        }
    }
    
    /**
     * Login user
     * POST /api/users/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody Map<String, String> credentials) {
        try {
            String identifier = credentials.get("email");
            if (identifier == null) {
                identifier = credentials.get("phoneNumber");
            }
            String password = credentials.get("password");
            
            if (identifier == null || password == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Email/phone and password are required"));
            }
            
            // Verify credentials
            com.aria.core.model.User user = DatabaseManager.verifyCredentials(identifier, password);
            
            if (user == null) {
                return ResponseEntity.status(401)
                    .body(ApiResponse.error("Invalid credentials"));
            }
            
            // Check if 2FA is enabled
            boolean requires2FA = false;
            String twoFactorSecret = null;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT two_factor_secret, two_factor_enabled FROM users WHERE id = ?")) {
                    ps.setInt(1, user.getUserAppId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            twoFactorSecret = rs.getString("two_factor_secret");
                            requires2FA = rs.getBoolean("two_factor_enabled");
                        }
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getUserAppId());
            response.put("phone", user.getPhone());
            response.put("username", user.getUsername());
            response.put("firstName", user.getFirstName());
            response.put("lastName", user.getLastName());
            
            if (requires2FA && twoFactorSecret != null) {
                // Return temp session for 2FA verification
                String tempSession = UUID.randomUUID().toString();
                // In production, store tempSession in Redis with userId and expiry
                response.put("requires2FA", true);
                response.put("tempSession", tempSession);
                return ResponseEntity.ok(ApiResponse.success("2FA required", response));
            }
            
            return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Login failed: " + e.getMessage()));
        }
    }
    
    /**
     * Setup 2FA for a user
     * POST /api/users/2fa/setup?userId=...
     */
    @PostMapping("/2fa/setup")
    public ResponseEntity<ApiResponse<Map<String, String>>> setup2FA(
            @RequestParam("userId") Integer userId) {
        try {
            String secret = secretGenerator.generate();
            String appName = "ARIA";
            String account = "user_" + userId;
            
            QrData qrData = new QrData.Builder()
                .label(account)
                .secret(secret)
                .issuer(appName)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
            
            QrGenerator qrGenerator = new ZxingPngQrGenerator();
            byte[] qrCode = qrGenerator.generate(qrData);
            String qrCodeBase64 = Base64.getEncoder().encodeToString(qrCode);
            
            // Save secret to database (encrypted)
            try (Connection conn = getConnection()) {
                String encryptedSecret = SecureStorage.encrypt(secret);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET two_factor_secret = ? WHERE id = ?")) {
                    ps.setString(1, encryptedSecret);
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                }
            }
            
            Map<String, String> response = new HashMap<>();
            response.put("secret", secret);
            response.put("qrCodeUrl", "data:image/png;base64," + qrCodeBase64);
            
            return ResponseEntity.ok(ApiResponse.success("2FA setup successful", response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to setup 2FA: " + e.getMessage()));
        }
    }
    
    /**
     * Get 2FA QR code for a user
     * GET /api/users/2fa/qrcode?session=...
     */
    @GetMapping("/2fa/qrcode")
    public ResponseEntity<ApiResponse<Map<String, String>>> get2FAQRCode(
            @RequestParam("session") String session) {
        try {
            // In a real implementation, you'd validate the session and get userId from it
            // For now, this is a placeholder
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Session validation not implemented"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get QR code: " + e.getMessage()));
        }
    }
    
    /**
     * Verify 2FA code
     * POST /api/users/2fa/verify?session=...
     */
    @PostMapping("/2fa/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verify2FA(
            @RequestParam("session") String session,
            @RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            if (code == null || code.length() != 6) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid 2FA code"));
            }
            
            // In a real implementation, you'd validate the session and get userId from it
            // For now, this is a placeholder
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Session validation not implemented"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to verify 2FA: " + e.getMessage()));
        }
    }
    
    /**
     * Get all API keys for a user
     * GET /api/users/api-keys?userId=...
     */
    @GetMapping("/api-keys")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getApiKeys(
            @RequestParam("userId") Integer userId) {
        try {
            List<Map<String, Object>> keys = new ArrayList<>();
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, key_name, api_key, is_active, created_at, last_used_at " +
                        "FROM api_keys WHERE user_id = ? ORDER BY created_at DESC")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> key = new HashMap<>();
                            key.put("id", rs.getInt("id"));
                            key.put("name", rs.getString("key_name"));
                            key.put("key", rs.getString("api_key"));
                            key.put("isActive", rs.getBoolean("is_active"));
                            key.put("createdAt", rs.getTimestamp("created_at").getTime());
                            if (rs.getTimestamp("last_used_at") != null) {
                                key.put("lastUsedAt", rs.getTimestamp("last_used_at").getTime());
                            }
                            keys.add(key);
                        }
                    }
                }
            }
            return ResponseEntity.ok(ApiResponse.success("OK", keys));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get API keys: " + e.getMessage()));
        }
    }
    
    /**
     * Create a new API key
     * POST /api/users/api-keys?userId=...
     */
    @PostMapping("/api-keys")
    public ResponseEntity<ApiResponse<Map<String, String>>> createApiKey(
            @RequestParam("userId") Integer userId,
            @RequestBody Map<String, String> request) {
        try {
            String keyName = request.get("name");
            if (keyName == null || keyName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Key name is required"));
            }
            
            // Generate API key and secret
            String apiKey = "aria_" + UUID.randomUUID().toString().replace("-", "");
            String secretKey = "aria_secret_" + UUID.randomUUID().toString().replace("-", "");
            
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO api_keys (user_id, key_name, api_key, secret_key) " +
                        "VALUES (?, ?, ?, ?) RETURNING id")) {
                    ps.setInt(1, userId);
                    ps.setString(2, keyName);
                    ps.setString(3, apiKey);
                    ps.setString(4, secretKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Map<String, String> response = new HashMap<>();
                            response.put("id", String.valueOf(rs.getInt("id")));
                            response.put("key", apiKey);
                            response.put("secret", secretKey);
                            return ResponseEntity.ok(ApiResponse.success("API key created", response));
                        }
                    }
                }
            }
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to create API key"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to create API key: " + e.getMessage()));
        }
    }
    
    /**
     * Delete an API key
     * DELETE /api/users/api-keys/{keyId}?userId=...
     */
    @DeleteMapping("/api-keys/{keyId}")
    public ResponseEntity<ApiResponse<String>> deleteApiKey(
            @PathVariable("keyId") Integer keyId,
            @RequestParam("userId") Integer userId) {
        try {
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM api_keys WHERE id = ? AND user_id = ?")) {
                    ps.setInt(1, keyId);
                    ps.setInt(2, userId);
                    int deleted = ps.executeUpdate();
                    if (deleted > 0) {
                        return ResponseEntity.ok(ApiResponse.success("API key deleted", null));
                    } else {
                        return ResponseEntity.badRequest()
                            .body(ApiResponse.error("API key not found"));
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to delete API key: " + e.getMessage()));
        }
    }
    
    /**
     * Get user credits
     * GET /api/users/credits?userId=...
     */
    @GetMapping("/credits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCredits(
            @RequestParam("userId") Integer userId) {
        try {
            double credits = 0.0;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT credits FROM user_credits WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            credits = rs.getDouble("credits");
                        } else {
                            // Initialize credits for new user
                            try (PreparedStatement insertPs = conn.prepareStatement(
                                    "INSERT INTO user_credits (user_id, credits) VALUES (?, 0.0)")) {
                                insertPs.setInt(1, userId);
                                insertPs.executeUpdate();
                            }
                        }
                    }
                }
            }
            Map<String, Object> response = new HashMap<>();
            response.put("credits", credits);
            return ResponseEntity.ok(ApiResponse.success("OK", response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get credits: " + e.getMessage()));
        }
    }
    
    /**
     * Add credits to user account (mock payment integration)
     * POST /api/users/credits?userId=...
     */
    @PostMapping("/credits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addCredits(
            @RequestParam("userId") Integer userId,
            @RequestBody Map<String, Object> request) {
        try {
            // Mock payment - in production, this would integrate with Stripe
            Object amountObj = request.get("amount");
            double amount = 10.0; // Default $10
            if (amountObj instanceof Number) {
                amount = ((Number) amountObj).doubleValue();
            } else if (amountObj instanceof String) {
                try {
                    amount = Double.parseDouble((String) amountObj);
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid amount format"));
                }
            }
            
            // Mock payment processing (simulate Stripe checkout)
            String mockPaymentId = "mock_payment_" + System.currentTimeMillis();
            String mockStripeSessionId = "cs_mock_" + UUID.randomUUID().toString().substring(0, 24);
            
            // Simulate payment delay
            Thread.sleep(500);
            
            // Add credits to user account
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Get current credits
                    double currentCredits = 0.0;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT credits FROM user_credits WHERE user_id = ? FOR UPDATE")) {
                        ps.setInt(1, userId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                currentCredits = rs.getDouble("credits");
                            } else {
                                // Initialize credits
                                try (PreparedStatement insertPs = conn.prepareStatement(
                                        "INSERT INTO user_credits (user_id, credits) VALUES (?, 0.0)")) {
                                    insertPs.setInt(1, userId);
                                    insertPs.executeUpdate();
                                }
                            }
                        }
                    }
                    
                    // Add new credits
                    double newCredits = currentCredits + amount;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE user_credits SET credits = ?, last_updated = NOW() WHERE user_id = ?")) {
                        ps.setDouble(1, newCredits);
                        ps.setInt(2, userId);
                        ps.executeUpdate();
                    }
                    
                    // Record transaction
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO credit_transactions (user_id, amount, transaction_type, description) " +
                            "VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, userId);
                        ps.setDouble(2, amount);
                        ps.setString(3, "purchase");
                        ps.setString(4, "Mock payment: " + mockPaymentId + " (Stripe Session: " + mockStripeSessionId + ")");
                        ps.executeUpdate();
                    }
                    
                    conn.commit();
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("credits", newCredits);
                    response.put("amountAdded", amount);
                    response.put("paymentId", mockPaymentId);
                    response.put("stripeSessionId", mockStripeSessionId);
                    response.put("message", "Credits added successfully (mock payment)");
                    
                    return ResponseEntity.ok(ApiResponse.success("Credits added", response));
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Payment processing interrupted"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to add credits: " + e.getMessage()));
        }
    }
    
    /**
     * Get user subscription
     * GET /api/users/subscription?userId=...
     */
    @GetMapping("/subscription")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSubscription(
            @RequestParam("userId") Integer userId) {
        try {
            Map<String, Object> subscription = null;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, subscription_type, status, stripe_subscription_id, " +
                        "current_period_start, current_period_end, cancel_at_period_end " +
                        "FROM subscriptions WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            subscription = new HashMap<>();
                            subscription.put("id", rs.getInt("id"));
                            subscription.put("type", rs.getString("subscription_type"));
                            subscription.put("status", rs.getString("status"));
                            subscription.put("stripeSubscriptionId", rs.getString("stripe_subscription_id"));
                            if (rs.getTimestamp("current_period_start") != null) {
                                subscription.put("currentPeriodStart", rs.getTimestamp("current_period_start").getTime());
                            }
                            if (rs.getTimestamp("current_period_end") != null) {
                                subscription.put("nextBilling", rs.getTimestamp("current_period_end").getTime());
                            }
                            subscription.put("cancelAtPeriodEnd", rs.getBoolean("cancel_at_period_end"));
                        }
                    }
                }
            }
            return ResponseEntity.ok(ApiResponse.success("OK", subscription));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get subscription: " + e.getMessage()));
        }
    }
    
    /**
     * Subscribe to ARIAssistance (mock payment integration)
     * POST /api/users/subscribe?userId=...
     */
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<Map<String, Object>>> subscribe(
            @RequestParam("userId") Integer userId) {
        try {
            // Mock payment - in production, this would integrate with Stripe
            String mockStripeCustomerId = "cus_mock_" + UUID.randomUUID().toString().substring(0, 24);
            String mockStripeSubscriptionId = "sub_mock_" + UUID.randomUUID().toString().substring(0, 24);
            
            // Simulate payment processing delay
            Thread.sleep(800);
            
            // Create or update subscription
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Check if subscription exists
                    boolean exists = false;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM subscriptions WHERE user_id = ?")) {
                        ps.setInt(1, userId);
                        try (ResultSet rs = ps.executeQuery()) {
                            exists = rs.next();
                        }
                    }
                    
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime periodEnd = now.plusMonths(1);
                    
                    if (exists) {
                        // Update existing subscription
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE subscriptions SET " +
                                "subscription_type = ?, status = ?, stripe_subscription_id = ?, " +
                                "stripe_customer_id = ?, current_period_start = ?, " +
                                "current_period_end = ?, cancel_at_period_end = FALSE, updated_at = NOW() " +
                                "WHERE user_id = ?")) {
                            ps.setString(1, "ariassistance");
                            ps.setString(2, "active");
                            ps.setString(3, mockStripeSubscriptionId);
                            ps.setString(4, mockStripeCustomerId);
                            ps.setTimestamp(5, Timestamp.valueOf(now));
                            ps.setTimestamp(6, Timestamp.valueOf(periodEnd));
                            ps.setInt(7, userId);
                            ps.executeUpdate();
                        }
                    } else {
                        // Create new subscription
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO subscriptions " +
                                "(user_id, subscription_type, status, stripe_subscription_id, stripe_customer_id, " +
                                "current_period_start, current_period_end) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                            ps.setInt(1, userId);
                            ps.setString(2, "ariassistance");
                            ps.setString(3, "active");
                            ps.setString(4, mockStripeSubscriptionId);
                            ps.setString(5, mockStripeCustomerId);
                            ps.setTimestamp(6, Timestamp.valueOf(now));
                            ps.setTimestamp(7, Timestamp.valueOf(periodEnd));
                            ps.executeUpdate();
                        }
                    }
                    
                    conn.commit();
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("subscriptionId", mockStripeSubscriptionId);
                    response.put("customerId", mockStripeCustomerId);
                    response.put("status", "active");
                    response.put("type", "ariassistance");
                    response.put("currentPeriodStart", now.toString());
                    response.put("nextBilling", periodEnd.toString());
                    response.put("amount", 5.0);
                    response.put("currency", "USD");
                    response.put("message", "Subscription activated successfully (mock payment)");
                    
                    return ResponseEntity.ok(ApiResponse.success("Subscription created", response));
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Subscription processing interrupted"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to subscribe: " + e.getMessage()));
        }
    }
    
    /**
     * Get admin mode status for a user
     * GET /api/users/admin-mode?userId=...
     */
    @GetMapping("/admin-mode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminMode(
            @RequestParam("userId") Integer userId) {
        try {
            boolean adminModeEnabled = false;
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT admin_mode_enabled FROM users WHERE id = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            adminModeEnabled = rs.getBoolean("admin_mode_enabled");
                        }
                    }
                }
            }
            Map<String, Object> response = new HashMap<>();
            response.put("adminModeEnabled", adminModeEnabled);
            return ResponseEntity.ok(ApiResponse.success("OK", response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to get admin mode: " + e.getMessage()));
        }
    }
    
    /**
     * Update admin mode status for a user
     * PUT /api/users/admin-mode?userId=...
     */
    @PutMapping("/admin-mode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAdminMode(
            @RequestParam("userId") Integer userId,
            @RequestBody Map<String, Object> request) {
        try {
            Object enabledObj = request.get("adminModeEnabled");
            boolean adminModeEnabled = false;
            if (enabledObj instanceof Boolean) {
                adminModeEnabled = (Boolean) enabledObj;
            } else if (enabledObj instanceof String) {
                adminModeEnabled = Boolean.parseBoolean((String) enabledObj);
            }
            
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET admin_mode_enabled = ? WHERE id = ?")) {
                    ps.setBoolean(1, adminModeEnabled);
                    ps.setInt(2, userId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        return ResponseEntity.badRequest()
                            .body(ApiResponse.error("User not found"));
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("adminModeEnabled", adminModeEnabled);
            return ResponseEntity.ok(ApiResponse.success("Admin mode updated", response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to update admin mode: " + e.getMessage()));
        }
    }
    
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
}
