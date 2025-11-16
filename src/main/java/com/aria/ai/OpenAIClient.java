package com.aria.ai;

import com.aria.core.ConfigurationManager;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.time.Duration;

public class OpenAIClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public OpenAIClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(ConfigurationManager.getIntProperty("openai.timeout.connect.seconds", 20)))
                .readTimeout(Duration.ofSeconds(ConfigurationManager.getIntProperty("openai.timeout.read.seconds", 90)))
                .writeTimeout(Duration.ofSeconds(ConfigurationManager.getIntProperty("openai.timeout.write.seconds", 90)))
                .retryOnConnectionFailure(true)
                .build();

        // Use ConfigurationManager instead of System.getenv()
        this.apiKey = ConfigurationManager.getRequiredProperty("openai.api.key");
        this.model = ConfigurationManager.getProperty("openai.model", "gpt-3.5-turbo");
        this.maxTokens = ConfigurationManager.getIntProperty("openai.max_tokens", 150);
        this.temperature = ConfigurationManager.getDoubleProperty("openai.temperature", 0.7);

        System.out.println("OpenAIClient initialized with model: " + model);
    }

    public String generateResponse(String prompt) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", this.model);

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);

        requestBody.put("messages", new JSONObject[] {message});
        requestBody.put("max_tokens", this.maxTokens);
        requestBody.put("temperature", this.temperature);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(
                        requestBody.toString(),
                        MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + this.apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        return executeWithRetry(request);
    }

    /**
     * Generate response with multiple messages (conversation context)
     * @param messages Array of message objects with "role" and "content"
     * @return Generated response
     */
    public String generateResponseWithMessages(org.json.JSONArray messages) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", this.model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", this.maxTokens);
        requestBody.put("temperature", this.temperature);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(
                        requestBody.toString(),
                        MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + this.apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        return executeWithRetry(request);
    }

    /**
     * Generate response with extended context (for longer conversations)
     * @param systemPrompt System prompt/instructions
     * @param conversationHistory Conversation history
     * @param userMessage Current user message
     * @return Generated response
     */
    public String generateResponseWithContext(String systemPrompt, String conversationHistory, String userMessage) {
        org.json.JSONArray messages = new org.json.JSONArray();
        
        // System prompt
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.put(systemMsg);
        }
        
        // Conversation history
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            JSONObject historyMsg = new JSONObject();
            historyMsg.put("role", "assistant");
            historyMsg.put("content", conversationHistory);
            messages.put(historyMsg);
        }
        
        // User message
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.put(userMsg);

        return generateResponseWithMessages(messages);
    }

    /**
     * Execute request with simple retries and return message content, or null on failure.
     */
    private String executeWithRetry(Request request) {
        int attempts = ConfigurationManager.getIntProperty("openai.retry.attempts", 2);
        IOException last = null;
        for (int i = 0; i <= attempts; i++) {
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    System.err.println("OpenAI API error: " + response.code() + " - " + errorBody);
                    last = new IOException("Unexpected code " + response + ": " + errorBody);
                } else {
                    String responseBody = response.body() != null ? response.body().string() : null;
                    if (responseBody == null || responseBody.isEmpty()) return null;
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    return jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                }
            } catch (IOException e) {
                last = e;
                // small backoff
                try { Thread.sleep(500L * (i + 1)); } catch (InterruptedException ignored) {}
            }
        }
        if (last != null) last.printStackTrace();
        return null;
    }
}