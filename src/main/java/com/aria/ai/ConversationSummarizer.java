package com.aria.ai;

import com.aria.core.model.Message;
import com.aria.core.model.ConversationGoal;
import org.json.JSONObject;
import java.util.*;

/**
 * Generates conversation summaries and extracts key personal details
 */
public class ConversationSummarizer {
    private final OpenAIClient openAIClient;

    public ConversationSummarizer(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    /**
     * Generate a comprehensive summary of the conversation
     * @param messages List of messages in the conversation
     * @param goal The conversation goal
     * @return ConversationSummary
     */
    public ConversationSummary generateSummary(List<Message> messages, ConversationGoal goal) {
        // Format conversation history
        StringBuilder conversationText = new StringBuilder();
        for (Message msg : messages) {
            String sender = msg.isFromUser() ? "You" : "Target";
            String timestamp = msg.getTimestamp() != null ? 
                msg.getTimestamp().toString() : "";
            conversationText.append(String.format("[%s] %s: %s\n", 
                timestamp, sender, msg.getContent()));
        }

        String prompt = String.format("""
            Analyze the following conversation and provide a comprehensive summary.
            
            Conversation Goal: %s
            Meeting Context: %s
            
            Conversation:
            %s
            
            Provide a JSON response with this structure:
            {
                "summary": "A concise summary of the conversation flow, key topics discussed, and overall tone.",
                "key_personal_details": {
                    "name": "Person's name if mentioned",
                    "occupation": "Occupation or profession",
                    "interests": ["interest1", "interest2"],
                    "location": "Location if mentioned",
                    "important_facts": ["fact1", "fact2"],
                    "preferences": ["preference1", "preference2"]
                },
                "next_steps": "Agreed-upon next steps, plans, or commitments",
                "outcome_status": "pending/achieved/failed",
                "tone": "friendly/professional/romantic/etc"
            }
            
            Return JSON only, no additional text:""",
            goal.getDesiredOutcome(), 
            goal.getMeetingContext(),
            conversationText.toString());

        String response = openAIClient.generateResponse(prompt);
        return parseSummaryResponse(response);
    }

    private ConversationSummary parseSummaryResponse(String response) {
        ConversationSummary summary = new ConversationSummary();

        try {
            // Clean response
            String cleanResponse = response.trim();
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7);
            }
            if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.substring(3);
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
            }
            cleanResponse = cleanResponse.trim();

            JSONObject json = new JSONObject(cleanResponse);
            
            summary.summary = json.optString("summary", "No summary available");
            summary.nextSteps = json.optString("next_steps", "None");
            summary.outcomeStatus = json.optString("outcome_status", "pending");
            summary.tone = json.optString("tone", "neutral");

            // Parse key personal details
            if (json.has("key_personal_details")) {
                JSONObject details = json.getJSONObject("key_personal_details");
                summary.keyDetails = new HashMap<>();
                
                if (details.has("name")) summary.keyDetails.put("name", details.getString("name"));
                if (details.has("occupation")) summary.keyDetails.put("occupation", details.getString("occupation"));
                if (details.has("location")) summary.keyDetails.put("location", details.getString("location"));
                
                if (details.has("interests")) {
                    List<String> interests = new ArrayList<>();
                    for (Object interest : details.getJSONArray("interests")) {
                        interests.add(interest.toString());
                    }
                    summary.keyDetails.put("interests", interests);
                }
                
                if (details.has("important_facts")) {
                    List<String> facts = new ArrayList<>();
                    for (Object fact : details.getJSONArray("important_facts")) {
                        facts.add(fact.toString());
                    }
                    summary.keyDetails.put("important_facts", facts);
                }
                
                if (details.has("preferences")) {
                    List<String> preferences = new ArrayList<>();
                    for (Object pref : details.getJSONArray("preferences")) {
                        preferences.add(pref.toString());
                    }
                    summary.keyDetails.put("preferences", preferences);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing summary response: " + e.getMessage());
            System.err.println("Response was: " + response);
            summary.summary = "Error generating summary: " + e.getMessage();
            summary.keyDetails = new HashMap<>();
        }

        return summary;
    }

    public static class ConversationSummary {
        public String summary;
        public Map<String, Object> keyDetails;
        public String nextSteps;
        public String outcomeStatus;
        public String tone;

        public ConversationSummary() {
            this.keyDetails = new HashMap<>();
        }

        public String toJSONString() {
            JSONObject json = new JSONObject();
            json.put("summary", summary);
            json.put("key_personal_details", new JSONObject(keyDetails));
            json.put("next_steps", nextSteps);
            json.put("outcome_status", outcomeStatus);
            json.put("tone", tone);
            return json.toString();
        }
    }
}

