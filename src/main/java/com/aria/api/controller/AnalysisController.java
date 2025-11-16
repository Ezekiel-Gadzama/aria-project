package com.aria.api.controller;

import com.aria.api.dto.ApiResponse;
import com.aria.analysis.ChatCategorizationService;
import com.aria.ai.OpenAIClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*")
public class AnalysisController {

    /**
     * Trigger categorization for a user in the background.
     * POST /api/analysis/categorize?userId=1
     */
    @PostMapping("/categorize")
    public ResponseEntity<ApiResponse<String>> categorizeForUser(
            @RequestParam(value = "userId") Integer userId
    ) {
        try {
            final int uid = userId != null ? userId : 1;
            new Thread(() -> {
                try {
                    com.aria.storage.DatabaseManager.setAnalysisRunning(uid);
                    ChatCategorizationService service = new ChatCategorizationService(new OpenAIClient());
                    service.categorizeAllDialogs(uid);
                    com.aria.storage.DatabaseManager.setAnalysisFinished(uid, null);
                } catch (Exception ex) {
                    try { com.aria.storage.DatabaseManager.setAnalysisFinished(uid, ex.getMessage()); } catch (Exception ignored) {}
                }
            }, "categorize-user-" + uid).start();
            return ResponseEntity.ok(ApiResponse.success("Categorization started", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to start categorization: " + e.getMessage()));
        }
    }

    /**
     * Get ingestion status per platform account for a user.
     * GET /api/analysis/status/ingestion?userId=1
     */
    @GetMapping("/status/ingestion")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getIngestionStatus(
            @RequestParam(value = "userId", required = false) Integer userId
    ) {
        try {
            int uid = userId != null ? userId : 1;
            java.util.List<java.util.Map<String, Object>> rows = com.aria.storage.DatabaseManager.getIngestionStatuses(uid);
            return ResponseEntity.ok(ApiResponse.success(rows));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch ingestion status: " + e.getMessage()));
        }
    }

    /**
     * Get overall analysis status for a user.
     * GET /api/analysis/status/analysis?userId=1
     */
    @GetMapping("/status/analysis")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getAnalysisStatus(
            @RequestParam(value = "userId", required = false) Integer userId
    ) {
        try {
            int uid = userId != null ? userId : 1;
            java.util.Map<String, Object> row = com.aria.storage.DatabaseManager.getAnalysisStatus(uid);
            return ResponseEntity.ok(ApiResponse.success(row));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch analysis status: " + e.getMessage()));
        }
    }
}


