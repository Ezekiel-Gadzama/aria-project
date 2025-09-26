package com.aria.ai;

import com.aria.core.ConfigurationManager;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class OpenAIClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public OpenAIClient() {
        this.client = new OkHttpClient();

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

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            return jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

        } catch (IOException e) {
            e.printStackTrace();
            return "Sorry, I encountered an error generating a response.";
        }
    }
}