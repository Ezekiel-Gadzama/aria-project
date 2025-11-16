package com.aria.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * Generates quiz questions from conversation summaries to test user's memory
 */
public class QuizGenerator {
    private final OpenAIClient openAIClient;

    public QuizGenerator(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    /**
     * Generate quiz questions from conversation summary
     * @param summary The conversation summary text
     * @param keyDetails JSON string with key personal details
     * @param numQuestions Number of questions to generate
     * @return List of quiz questions
     */
    public List<QuizQuestion> generateQuiz(String summary, String keyDetails, int numQuestions) {
        String prompt = String.format("""
            Based on the following conversation summary and key personal details, generate %d quiz questions to test if someone remembers important information about the person they chatted with.
            
            Conversation Summary:
            %s
            
            Key Personal Details:
            %s
            
            Generate questions that test:
            1. Personal details (name, occupation, interests)
            2. Important information mentioned
            3. Agreed-upon next steps
            4. Preferences or opinions shared
            
            Return your response as a JSON array with this format:
            [
                {
                    "question": "What is [person]'s occupation?",
                    "correct_answer": "Venture Capitalist",
                    "type": "text"
                },
                ...
            ]
            
            Make questions clear and specific. Include 2-4 critical questions.
            Return JSON only, no additional text:""",
            numQuestions, summary, keyDetails);

        String response = openAIClient.generateResponse(prompt);
        return parseQuizResponse(response);
    }

    private List<QuizQuestion> parseQuizResponse(String response) {
        List<QuizQuestion> questions = new ArrayList<>();

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

            JSONArray jsonArray = new JSONArray(cleanResponse);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject qObj = jsonArray.getJSONObject(i);
                QuizQuestion question = new QuizQuestion();
                question.question = qObj.getString("question");
                question.correctAnswer = qObj.getString("correct_answer");
                question.type = qObj.optString("type", "text");
                questions.add(question);
            }
        } catch (Exception e) {
            System.err.println("Error parsing quiz response: " + e.getMessage());
            System.err.println("Response was: " + response);
            // Create a fallback question
            QuizQuestion fallback = new QuizQuestion();
            fallback.question = "Review the conversation summary and key details";
            fallback.correctAnswer = "Please review";
            fallback.type = "text";
            questions.add(fallback);
        }

        return questions;
    }

    public static class QuizQuestion {
        public String question;
        public String correctAnswer;
        public String type; // "text", "multiple_choice", etc.

        public boolean isCorrect(String userAnswer) {
            if (userAnswer == null || userAnswer.trim().isEmpty()) {
                return false;
            }
            
            // Simple comparison (case-insensitive, trimmed)
            String normalizedUser = userAnswer.trim().toLowerCase();
            String normalizedCorrect = correctAnswer.trim().toLowerCase();
            
            // Exact match
            if (normalizedUser.equals(normalizedCorrect)) {
                return true;
            }
            
            // Check if user answer contains the correct answer or vice versa
            return normalizedUser.contains(normalizedCorrect) || 
                   normalizedCorrect.contains(normalizedUser);
        }
    }
}

