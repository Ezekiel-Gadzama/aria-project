// OpenAIClient.java
package com.aria.ai;

import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class OpenAIClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient client;
    private final String apiKey;

    public OpenAIClient() {
        this.client = new OkHttpClient();
        this.apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable not set");
        }
    }

    public String generateResponse(String prompt) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "gpt-3.5-turbo");

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);

        requestBody.put("messages", new JSONObject[] {message});
        requestBody.put("max_tokens", 150);
        requestBody.put("temperature", 0.7);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(
                        requestBody.toString(),
                        MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
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