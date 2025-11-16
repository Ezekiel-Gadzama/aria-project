package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import com.aria.api.dto.UserRegistrationDTO;
import com.aria.core.AriaOrchestrator;
import com.aria.core.model.User;
import com.aria.platform.Platform;
import com.aria.platform.PlatformConnector;
import com.aria.platform.telegram.TelegramConnector;
import com.aria.service.UserService;
import com.aria.storage.DatabaseManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller for user management
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*") // Allow all origins for development
public class UserController {

    // DatabaseManager uses static methods, so no autowiring needed

    /**
     * Register a new user
     * POST /api/users/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserRegistrationDTO>> registerUser(@RequestBody UserRegistrationDTO registrationDTO) {
        try {
            // Prevent re-registering if phone already exists
            if (registrationDTO.getPhoneNumber() != null && !registrationDTO.getPhoneNumber().isEmpty()) {
                if (DatabaseManager.userExists(registrationDTO.getPhoneNumber())) {
                    return ResponseEntity.badRequest()
                        .body(ApiResponse.error("User already registered with this phone"));
                }
            }

            if (registrationDTO.getPassword() == null || registrationDTO.getPassword().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Password is required"));
            }

            // Create User object with required constructor parameters
            // User constructor requires: phone, username, firstName, lastName, appGoal
            User user = new User(
                registrationDTO.getPhoneNumber() != null ? registrationDTO.getPhoneNumber() : "",
                registrationDTO.getEmail() != null ? registrationDTO.getEmail() : "",
                registrationDTO.getName() != null ? registrationDTO.getName().split(" ", 2)[0] : "",
                registrationDTO.getName() != null && registrationDTO.getName().split(" ", 2).length > 1 
                    ? registrationDTO.getName().split(" ", 2)[1] : "",
                registrationDTO.getBio() != null ? registrationDTO.getBio() : ""
            );

            DatabaseManager databaseManager = new DatabaseManager();
            UserService userService = new UserService(databaseManager, user);
            boolean success = userService.registerUser();

            if (success) {
                // Store password hash
                String hash = org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
                    registrationDTO.getPassword(), org.springframework.security.crypto.bcrypt.BCrypt.gensalt(10)
                );
                DatabaseManager.upsertUserPasswordHashByPhone(user.getPhone(), hash);

                return ResponseEntity.ok(ApiResponse.success("User registered successfully", registrationDTO));
            } else {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Registration failed", "Failed to save user to database"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error during registration: " + e.getMessage()));
        }
    }

    /**
     * Danger: Deletes all users and related data (CASCADE).
     * DELETE /api/users (admin/reset)
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<String>> deleteAllUsers() {
        try {
            DatabaseManager.deleteAllUsersCascade();
            return ResponseEntity.ok(ApiResponse.success("All users (and related data) deleted", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error deleting users: " + e.getMessage()));
        }
    }

    /**
     * Login with phone or email (no password yet).
     * POST /api/users/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserRegistrationDTO>> login(@RequestBody UserRegistrationDTO dto) {
        try {
            String identifier = dto.getPhoneNumber();
            if (identifier == null || identifier.isEmpty()) identifier = dto.getEmail();
            if (identifier == null || identifier.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Provide phoneNumber or email to login"));
            }
            if (dto.getPassword() == null || dto.getPassword().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Password is required"));
            }

            User found = DatabaseManager.verifyCredentials(identifier, dto.getPassword());
            if (found == null) return ResponseEntity.status(401).body(ApiResponse.error("Invalid credentials"));

            UserRegistrationDTO resp = new UserRegistrationDTO();
            resp.setPhoneNumber(found.getPhone());
            resp.setEmail(found.getUsername());
            resp.setName((found.getFirstName() != null ? found.getFirstName() : "") +
                    (found.getLastName() != null && !found.getLastName().isEmpty() ? " " + found.getLastName() : ""));
            resp.setBio(found.getAppGoal());

            // After successful login, trigger ingestion for all registered connectors (background)
            // Only if ingestion is not already running for each account
            try {
                DatabaseManager databaseManager = new DatabaseManager();
                UserService userService = new UserService(databaseManager, found);
                AriaOrchestrator orchestrator = new AriaOrchestrator(userService);
                for (DatabaseManager.PlatformAccount acc : DatabaseManager.getPlatformAccountsForUser(found.getUserAppId())) {
                    // Check if ingestion is already running for this account
                    try {
                        if (DatabaseManager.isIngestionRunning(found.getUserAppId(), acc.id)) {
                            System.out.println("Ingestion already running for platform account " + acc.id + ", skipping");
                            continue;
                        }
                    } catch (Exception e) {
                        // If check fails, proceed anyway (don't block ingestion)
                        System.err.println("Error checking ingestion status: " + e.getMessage());
                    }
                    
                    Platform platform = Platform.valueOf(acc.platform.toUpperCase());
                    PlatformConnector connector = null;
                    if (platform == Platform.TELEGRAM) {
                        // Use ConnectorRegistry and pass username from database to ensure session path consistency
                        connector = com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
                    }
                    // TODO: add connectors for WHATSAPP, INSTAGRAM when implemented
                    if (connector != null) {
                        final PlatformConnector c = connector;
                        final int accountId = acc.id;
                        new Thread(() -> {
                            try {
                                // Mark ingestion as running before starting
                                try {
                                    DatabaseManager.setIngestionRunning(found.getUserAppId(), accountId);
                                } catch (Exception ignored) {}
                                orchestrator.startChatIngestion(c);
                                try {
                                    DatabaseManager.setIngestionFinished(found.getUserAppId(), accountId, null);
                                } catch (Exception ignored) {}
                            } catch (Exception ex) {
                                try {
                                    DatabaseManager.setIngestionFinished(found.getUserAppId(), accountId, ex.getMessage());
                                } catch (Exception ignored) {}
                            }
                        }, "ingest-on-login-" + platform.name().toLowerCase() + "-" + acc.id).start();
                    }
                }
            } catch (Exception e) {
                // Log but don't fail login
                System.err.println("Failed to start ingestion after login: " + e.getMessage());
                e.printStackTrace();
            }

            return ResponseEntity.ok(ApiResponse.success("Login successful", resp));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error during login: " + e.getMessage()));
        }
    }

    /**
     * Get current user info
     * GET /api/users/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserRegistrationDTO>> getCurrentUser(@RequestParam(value = "userId", required = false) Integer userId) {
        try {
            // TODO: Get current user from session/authentication
            // For now, return a placeholder
            return ResponseEntity.ok(ApiResponse.error("Not implemented yet"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error fetching user: " + e.getMessage()));
        }
    }
}

