package com.aria.ai;

import com.aria.core.ConfigurationManager;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

/**
 * Client for Undetectable.ai API to humanize AI-generated responses
 */
public class UndetectableAIClient {
    private static final String API_URL = "https://api.undetectable.ai/submit";
    private static final String STATUS_URL = "https://api.undetectable.ai/status";
    private final OkHttpClient client;
    private final String apiKey;

    public UndetectableAIClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        
        // Try to get API key from config, fallback to environment variable
        this.apiKey = ConfigurationManager.getProperty("undetectable.ai.api.key", 
                System.getenv("UNDETECTABLE_AI_API_KEY"));
    }

    /**
     * Humanize AI-generated text using Undetectable.ai
     * @param text The AI-generated text to humanize
     * @return Humanized text
     */
    public String humanizeText(String text) {
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("Warning: Undetectable.ai API key not configured. Returning original text.");
            return text;
        }

        try {
            // Submit text for humanization
            String jobId = submitHumanization(text);
            
            if (jobId == null) {
                return text; // Fallback to original text
            }

            // Poll for result
            String humanizedText = pollForResult(jobId);
            
            if (humanizedText == null || humanizedText.isEmpty()) {
                return text; // Fallback to original text
            }

            return humanizedText;
        } catch (Exception e) {
            System.err.println("Error humanizing text with Undetectable.ai: " + e.getMessage());
            e.printStackTrace();
            return text; // Fallback to original text on error
        }
    }

    private String submitHumanization(String text) throws IOException {
        JSONObject requestBody = new JSONObject();
        requestBody.put("content", text);
        requestBody.put("readability", "High School");
        requestBody.put("purpose", "General Writing");
        requestBody.put("strength", "More Human");

        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(
                        requestBody.toString(),
                        MediaType.parse("application/json")))
                .addHeader("api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Undetectable.ai API error: " + response.code());
                String errorBody = response.body() != null ? response.body().string() : "";
                System.err.println("Error body: " + errorBody);
                return null;
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (jsonResponse.has("job_id")) {
                return jsonResponse.getString("job_id");
            } else if (jsonResponse.has("status") && jsonResponse.getString("status").equals("success")) {
                // Sometimes the response is immediate
                if (jsonResponse.has("output")) {
                    return jsonResponse.getString("output");
                }
            }
            
            return null;
        }
    }

    private String pollForResult(String jobId) throws IOException, InterruptedException {
        int maxAttempts = 30; // 30 attempts = 30 seconds max
        int attempt = 0;

        while (attempt < maxAttempts) {
            Request request = new Request.Builder()
                    .url(STATUS_URL + "?job_id=" + jobId)
                    .get()
                    .addHeader("api-key", apiKey)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    
                    String status = jsonResponse.optString("status", "pending");
                    
                    if ("completed".equals(status) || "success".equals(status)) {
                        if (jsonResponse.has("output")) {
                            return jsonResponse.getString("output");
                        }
                        if (jsonResponse.has("result")) {
                            JSONObject result = jsonResponse.getJSONObject("result");
                            if (result.has("output")) {
                                return result.getString("output");
                            }
                        }
                    } else if ("failed".equals(status) || "error".equals(status)) {
                        System.err.println("Undetectable.ai humanization failed");
                        return null;
                    }
                }
            }

            Thread.sleep(1000); // Wait 1 second before next poll
            attempt++;
        }

        System.err.println("Timeout waiting for Undetectable.ai humanization result");
        return null;
    }
}

