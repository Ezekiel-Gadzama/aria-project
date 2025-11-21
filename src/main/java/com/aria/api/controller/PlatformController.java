package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import com.aria.api.dto.PlatformAccountDTO;
import com.aria.core.AriaOrchestrator;
import com.aria.core.model.User;
import com.aria.platform.Platform;
import com.aria.platform.PlatformConnector;
import com.aria.platform.telegram.TelegramConnector;
import com.aria.service.UserService;
import com.aria.storage.DatabaseManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for platform management
 */
@RestController
@RequestMapping("/api/platforms")
@CrossOrigin(origins = "*")
public class PlatformController {

    /**
     * Get all available platforms
     * GET /api/platforms
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> getPlatforms() {
        try {
            List<String> platforms = Arrays.stream(Platform.values())
                .map(Platform::name)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success(platforms));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error fetching platforms: " + e.getMessage()));
        }
    }

    /**
     * Get all registered platform accounts for the current user.
     * GET /api/platforms/accounts?userId=1
     */
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<PlatformAccountDTO>>> getPlatformAccounts(
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            List<DatabaseManager.PlatformAccount> accounts =
                    DatabaseManager.getPlatformAccountsForUser(currentUserId);
            List<PlatformAccountDTO> dtos = accounts.stream()
                    .map(PlatformAccountDTO::new)
                    .toList();
            return ResponseEntity.ok(ApiResponse.success(dtos));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error fetching platform accounts: " + e.getMessage()));
        }
    }

    /**
     * Register platform credentials
     * POST /api/platforms/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> registerPlatform(
            @RequestParam(value = "platform") String platform,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestBody Object credentials) {
        try {
            int currentUserId = userId != null ? userId : 1;
            DatabaseManager databaseManager = new DatabaseManager();

            // Parse credentials
            if (!(credentials instanceof Map)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid credentials format. Expecting JSON object."));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> creds = (Map<String, Object>) credentials;

            String username = (String) creds.getOrDefault("username", "");
            String number = (String) creds.getOrDefault("phoneNumber", "");
            String apiId = (String) creds.getOrDefault("apiId", "");
            String apiHash = (String) creds.getOrDefault("apiHash", "");
            String password = (String) creds.getOrDefault("password", "");
            String accessToken = (String) creds.getOrDefault("accessToken", "");
            String refreshToken = (String) creds.getOrDefault("refreshToken", "");
            String accountName = (String) creds.getOrDefault("accountName", "");

            // If account name not provided, get it from Telegram session
            if ((accountName == null || accountName.trim().isEmpty()) && "TELEGRAM".equalsIgnoreCase(platform)) {
                try {
                    String sessionPath = buildSessionPath(username, number);
                    ProcessBuilder pb = new ProcessBuilder("python3", "scripts/telethon/get_account_name.py");
                    Map<String, String> env = pb.environment();
                    env.put("TELEGRAM_API_ID", apiId);
                    env.put("TELEGRAM_API_HASH", apiHash);
                    env.put("TELEGRAM_PHONE", number);
                    if (password != null && !password.isEmpty()) env.put("TELEGRAM_PASSWORD", password);
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
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(outputStr).getAsJsonObject();
                        if (json.has("account_name") && !json.get("account_name").isJsonNull()) {
                            accountName = json.get("account_name").getAsString();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to get account name from Telegram: " + e.getMessage());
                    // Continue without account name
                }
            }

            // Persist platform account
            int accountId = DatabaseManager.savePlatformAccount(
                currentUserId, platform.toUpperCase(), username, number, apiId, apiHash, password, accessToken, refreshToken, accountName
            );

            // Create appropriate platform connector
            final Platform p = Platform.valueOf(platform.toUpperCase());
            final PlatformConnector connectorFinal;
            if (p == Platform.TELEGRAM) {
                DatabaseManager.PlatformAccount acc = new DatabaseManager.PlatformAccount(accountId, p.name(), username, number, apiId, apiHash, accountName);
                connectorFinal = com.aria.platform.ConnectorRegistry.getInstance().getOrCreateTelegramConnector(acc);
            } else {
                connectorFinal = null;
            }
            // TODO: add connectors for WHATSAPP, INSTAGRAM when implemented

            // Create orchestrator and start ingestion in the background for this connector
            // Only if ingestion is not already running
            if (connectorFinal != null) {
                // Check if ingestion is already running for this account
                try {
                    if (DatabaseManager.isIngestionRunning(currentUserId, accountId)) {
                        System.out.println("Ingestion already running for platform account " + accountId + ", skipping");
                        return ResponseEntity.ok(ApiResponse.success("Platform registered (ingestion already running)", null));
                    }
                } catch (Exception e) {
                    // If check fails, proceed anyway (don't block registration)
                    System.err.println("Error checking ingestion status: " + e.getMessage());
                }
                
                User tempUser = new User(number != null ? number : "", username != null ? username : "", "", "", "");
                UserService userService = new UserService(databaseManager, tempUser);
                AriaOrchestrator orchestrator = new AriaOrchestrator(userService);
                new Thread(() -> {
                    try {
                        // mark ingestion running for this user/account
                        try {
                            // Resolve current user id from the account owner
                            // We have currentUserId already
                            DatabaseManager.setIngestionRunning(currentUserId, accountId);
                        } catch (Exception ignored) {}
                        orchestrator.startChatIngestion(connectorFinal);
                        try {
                            DatabaseManager.setIngestionFinished(currentUserId, accountId, null);
                        } catch (Exception ignored) {}
                    } catch (Exception ex) {
                        try { DatabaseManager.setIngestionFinished(currentUserId, accountId, ex.getMessage()); } catch (Exception ignored) {}
                    }
                }, "ingest-" + platform.toLowerCase() + "-" + accountId).start();
            }

            return ResponseEntity.ok(ApiResponse.success("Platform registered and ingestion started", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Error registering platform: " + e.getMessage()));
        }
    }

    /**
     * Trigger OTP sending for Telegram account before registration completes
     * POST /api/platforms/telegram/sendOtp
     */
    @PostMapping("/telegram/sendOtp")
    public ResponseEntity<ApiResponse<String>> sendTelegramOtp(
            @RequestParam("apiId") String apiId,
            @RequestParam("apiHash") String apiHash,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam(value = "username", required = false) String username
    ) {
        try {
            String sessionPath = buildSessionPath(username, phoneNumber);
            ProcessBuilder pb = new ProcessBuilder("python3", "scripts/telethon/otp_requester.py");
            Map<String, String> env = pb.environment();
            env.put("TELEGRAM_API_ID", apiId);
            env.put("TELEGRAM_API_HASH", apiHash);
            env.put("TELEGRAM_PHONE", phoneNumber);
            env.put("TELETHON_SESSION_PATH", sessionPath);
            env.put("TELETHON_LOCK_PATH", "/app/telethon_send.lock");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit == 0) {
                return ResponseEntity.ok(ApiResponse.success("OTP sent", null));
            }
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to send OTP"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error sending OTP: " + e.getMessage()));
        }
    }

    /**
     * Verify OTP to finalize Telegram session creation
     * POST /api/platforms/telegram/verifyOtp?code=12345
     */
    @PostMapping("/telegram/verifyOtp")
    public ResponseEntity<ApiResponse<String>> verifyTelegramOtp(
            @RequestParam("apiId") String apiId,
            @RequestParam("apiHash") String apiHash,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("code") String code,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password
    ) {
        try {
            String sessionPath = buildSessionPath(username, phoneNumber);
            ProcessBuilder pb = new ProcessBuilder("python3", "scripts/telethon/otp_verifier.py", code);
            Map<String, String> env = pb.environment();
            env.put("TELEGRAM_API_ID", apiId);
            env.put("TELEGRAM_API_HASH", apiHash);
            env.put("TELEGRAM_PHONE", phoneNumber);
            if (password != null) env.put("TELEGRAM_PASSWORD", password);
            env.put("TELETHON_SESSION_PATH", sessionPath);
            env.put("TELETHON_LOCK_PATH", "/app/telethon_send.lock");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit == 0) {
                return ResponseEntity.ok(ApiResponse.success("OTP verified", null));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid OTP or verification failed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error verifying OTP: " + e.getMessage()));
        }
    }

    private String buildSessionPath(String username, String phoneNumber) {
        String userPart = (username != null && !username.isBlank()) ? username : (phoneNumber != null ? phoneNumber : "unknown");
        // Strip "@" prefix to match TelegramConnector behavior
        if (userPart.startsWith("@")) userPart = userPart.substring(1);
        // Normalize separators/spaces (allow + for phone numbers, _ for usernames)
        userPart = userPart.replaceAll("[^A-Za-z0-9_@+]", "_");
        // Match TelegramConnector: directory per user, file inside; Telethon appends ".session"
        return "Session/telegramConnector/user_" + userPart + "/user_" + userPart;
    }

    /**
     * Delete a specific platform account for the user.
     * DELETE /api/platforms/accounts/{id}?userId=1
     */
    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<String>> deletePlatformAccount(
            @PathVariable("id") int id,
            @RequestParam(value = "userId", required = false) Integer userId) {
        try {
            int currentUserId = userId != null ? userId : 1;
            // Fetch account first for session path cleanup
            DatabaseManager.PlatformAccount acc = DatabaseManager.getPlatformAccountById(id);
            boolean deleted = DatabaseManager.deletePlatformAccountCascade(id, currentUserId);
            if (deleted) {
                if (acc != null && "TELEGRAM".equalsIgnoreCase(acc.platform)) {
                    String sessionPath = buildSessionPath(acc.username, acc.number);
                    try {
                        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(sessionPath));
                    } catch (Exception ignored) {}
                }
                return ResponseEntity.ok(ApiResponse.success("Platform account and related data deleted", null));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Platform account not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error deleting platform account: " + e.getMessage()));
        }
    }
}

