package com.aria.ai;

import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Client for OpenAI Responses API
 * Manages conversation state using response IDs
 */
public class OpenAIResponsesClient {
    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    
    public OpenAIResponsesClient() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        // Get API key from environment or config
        String envKey = System.getenv("OPENAI_API_KEY");
        this.apiKey = envKey != null ? envKey : "";
        this.model = "gpt-4";
    }
    
    /**
     * Create a new response with full context (first call)
     */
    public ResponseResult createResponse(String input) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("input", input);
            requestBody.put("store", true); // Store conversation state
            
            RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    System.err.println("OpenAI Responses API error: " + response.code() + " - " + errorBody);
                    return new ResponseResult(false, null, null, "API error: " + response.code());
                }
                
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                
                String responseId = json.optString("id", null);
                String outputText = json.optString("output_text", null);
                
                return new ResponseResult(true, responseId, outputText, null);
            }
        } catch (IOException e) {
            System.err.println("Error calling OpenAI Responses API: " + e.getMessage());
            return new ResponseResult(false, null, null, e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return new ResponseResult(false, null, null, e.getMessage());
        }
    }
    
    /**
     * Continue existing conversation (subsequent calls)
     */
    public ResponseResult continueResponse(String previousResponseId, String input) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("input", input);
            requestBody.put("previous_response_id", previousResponseId);
            
            RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    System.err.println("OpenAI Responses API error: " + response.code() + " - " + errorBody);
                    return new ResponseResult(false, null, null, "API error: " + response.code());
                }
                
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                
                String responseId = json.optString("id", null);
                String outputText = json.optString("output_text", null);
                
                return new ResponseResult(true, responseId, outputText, null);
            }
        } catch (IOException e) {
            System.err.println("Error calling OpenAI Responses API: " + e.getMessage());
            return new ResponseResult(false, null, null, e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return new ResponseResult(false, null, null, e.getMessage());
        }
    }
    
    /**
     * Result of a response API call
     */
    public static class ResponseResult {
        public final boolean success;
        public final String responseId;
        public final String outputText;
        public final String error;
        
        public ResponseResult(boolean success, String responseId, String outputText, String error) {
            this.success = success;
            this.responseId = responseId;
            this.outputText = outputText;
            this.error = error;
        }
        
        public boolean isSuccess() {
            return success;
        }
    }
}
