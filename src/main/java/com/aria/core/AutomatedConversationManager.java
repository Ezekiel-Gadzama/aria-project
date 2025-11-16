package com.aria.core;

import com.aria.ai.*;
import com.aria.analysis.*;
import com.aria.core.model.*;
import com.aria.core.strategy.WeightedResponseSynthesis;
import com.aria.platform.PlatformConnector;
import com.aria.storage.DatabaseSchema;
import com.aria.analysis.SmartChatSelector;
import org.json.JSONObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages automated conversations with full Phase 3 features:
 * - Disinterest detection
 * - Response timing analysis
 * - Humanized responses via Undetectable.ai
 * - Weighted response synthesis (70%/15%/15%)
 * - Goal-based chat categorization
 * - Summarization and quiz
 */
public class AutomatedConversationManager {
    private final ResponseGenerator responseGenerator;
    private final DisinterestDetector disinterestDetector;
    private final ResponseTimingAnalyzer timingAnalyzer;
    private final UndetectableAIClient undetectableAI;
    private final ChatCategorizationService categorizationService;
    private final ConversationSummarizer summarizer;
    private final QuizGenerator quizGenerator;
    private final WeightedResponseSynthesis synthesisEngine;
    private final PlatformConnector platformConnector;
    
    private final Map<Integer, ConversationState> activeConversations;
    private final ScheduledExecutorService scheduler;

    public AutomatedConversationManager(
            ResponseGenerator responseGenerator,
            DisinterestDetector disinterestDetector,
            ResponseTimingAnalyzer timingAnalyzer,
            UndetectableAIClient undetectableAI,
            ChatCategorizationService categorizationService,
            ConversationSummarizer summarizer,
            QuizGenerator quizGenerator,
            WeightedResponseSynthesis synthesisEngine,
            PlatformConnector platformConnector) {
        
        this.responseGenerator = responseGenerator;
        this.disinterestDetector = disinterestDetector;
        this.timingAnalyzer = timingAnalyzer;
        this.undetectableAI = undetectableAI;
        this.categorizationService = categorizationService;
        this.summarizer = summarizer;
        this.quizGenerator = quizGenerator;
        this.synthesisEngine = synthesisEngine;
        this.platformConnector = platformConnector;
        
        this.activeConversations = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(5);
    }

    /**
     * Initialize conversation for a target user with a specific goal
     */
    public void initializeConversation(int userId, int targetUserId, int goalId, 
                                      ConversationGoal goal, String platform) throws SQLException {
        // Get relevant categories for this goal
        List<String> relevantCategories = categorizationService.getRelevantCategories(
            goal.getDesiredOutcome(), goal.getMeetingContext());

        // Get historical chats in these categories
        List<ChatCategorizationService.ChatCategoryResult> relevantChats = 
            categorizationService.getChatsByCategories(relevantCategories, userId);

        // Load and categorize all messages for these dialogs
        Map<String, List<Message>> categorizedChats = new HashMap<>();
        for (ChatCategorizationService.ChatCategoryResult result : relevantChats) {
            List<Message> messages = loadMessagesForDialog(result.dialogId);
            if (messages != null && !messages.isEmpty()) {
                categorizedChats.put(result.dialogName, messages);
            }
        }

        // Synthesize communication profile (70%/15%/15%)
        ChatProfile synthesizedProfile = synthesisEngine.synthesizeProfile(
            categorizedChats, goal.getDesiredOutcome());

        // Set up response generator
        responseGenerator.setConversationContext(goal, getTargetName(targetUserId), platform);
        responseGenerator.setStyleProfile(synthesizedProfile);

        // Create conversation state
        ConversationState state = new ConversationState();
        state.userId = userId;
        state.targetUserId = targetUserId;
        state.goalId = goalId;
        state.goal = goal;
        state.platform = platform;
        state.synthesizedProfile = synthesizedProfile;
        state.relevantCategories = relevantCategories;
        state.messages = new ArrayList<>();

        // Save to database
        saveConversationState(state);

        activeConversations.put(goalId, state);

        // Start monitoring for new messages
        startConversationMonitoring(goalId);
    }

    /**
     * Process incoming message and generate response
     */
    public String processIncomingMessage(int goalId, String incomingMessage, String sender) throws SQLException {
        ConversationState state = activeConversations.get(goalId);
        if (state == null) {
            throw new IllegalStateException("Conversation not initialized for goal: " + goalId);
        }

        // Create message object
        Message message = new Message(incomingMessage, sender, false);
        message.setTimestamp(LocalDateTime.now());
        state.messages.add(message);

        // Update engagement score
        updateEngagementScore(state);

        // Check for disinterest
        DisinterestDetector.DisinterestAnalysis disinterestAnalysis = 
            disinterestDetector.analyzeConversation(state.messages);

        if (disinterestAnalysis.getProbability() > 0.7) {
            // High disinterest - notify user
            notifyUserOfDisinterest(state, disinterestAnalysis);
            
            // Don't auto-respond if disinterest is very high
            if (disinterestAnalysis.getProbability() > 0.8) {
                return null; // Return null to indicate manual intervention needed
            }
        }

        // Step 1: Get chat examples from database using smart filtering
        // This filters based on categories, deduplicates, and manages token limits
        // We get MORE chats first (50) to ensure we have enough for proper 70/15/15 selection
        // The filtering happens BEFORE OpenAI scoring to reduce API calls
        Map<Integer, List<Message>> filteredChats = categorizationService.getSmartFilteredChats(
            state.relevantCategories, state.userId, 50); // Get more chats for proper 70/15/15 selection
        
        // Step 2: Score ALL filtered chats using OpenAI (determine success 0-100%)
        // This processes all chats to determine which were successful/failed
        // Step 3: Select chats for 70/15/15 weighting based on scores
        List<List<Message>> successfulExamples = selectChatsForWeighting(
            filteredChats, state.goal.getDesiredOutcome(), state.relevantCategories);

        // Build enhanced prompt with historical context
        String enhancedPrompt = buildEnhancedPrompt(state, incomingMessage, successfulExamples);

        // Generate AI response
        String aiResponse = responseGenerator.generateResponse(enhancedPrompt);

        // Humanize using Undetectable.ai
        String humanizedResponse = undetectableAI.humanizeText(aiResponse);

        // Calculate optimal response delay
        long optimalDelay = timingAnalyzer.calculateOptimalResponseDelay(
            state.messages, state.synthesizedProfile, state.engagementScore);

        // Schedule response sending
        state.pendingResponse = humanizedResponse;
        state.responseDelay = optimalDelay;

        // Save updated state
        saveConversationState(state);

        return humanizedResponse;
    }

    /**
     * Send scheduled response after delay
     */
    public void sendScheduledResponse(int goalId) {
        ConversationState state = activeConversations.get(goalId);
        if (state == null || state.pendingResponse == null) {
            return;
        }

            try {
                // Check if should respond now based on timing analysis
                long lastMessageAge = calculateLastMessageAge(state);
                boolean shouldRespond = timingAnalyzer.shouldRespondNow(
                    state.messages, state.engagementScore, lastMessageAge);

            if (shouldRespond) {
                // Send message
                String targetName = getTargetName(state.targetUserId);
                boolean sent = platformConnector.sendMessage(targetName, state.pendingResponse);

                if (sent) {
                    // Record sent message
                    Message sentMsg = new Message(state.pendingResponse, "You", true);
                    sentMsg.setTimestamp(LocalDateTime.now());
                    state.messages.add(sentMsg);
                    state.pendingResponse = null;
                    state.lastResponseTimestamp = LocalDateTime.now();

                    saveConversationState(state);
                }
            } else {
                // Reschedule
                scheduler.schedule(() -> sendScheduledResponse(goalId), 
                    state.responseDelay, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            System.err.println("Error sending scheduled response: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generate conversation summary and quiz
     */
    public ConversationSummaryResult generateSummaryAndQuiz(int goalId) throws SQLException {
        ConversationState state = activeConversations.get(goalId);
        if (state == null) {
            throw new IllegalStateException("Conversation not found: " + goalId);
        }

        // Generate summary
        ConversationSummarizer.ConversationSummary summary = 
            summarizer.generateSummary(state.messages, state.goal);

        // Save summary to database
        int summaryId = saveConversationSummary(state, summary);

        // Generate quiz questions
        String keyDetailsJSON = new JSONObject(summary.keyDetails).toString();
        List<QuizGenerator.QuizQuestion> quizQuestions = 
            quizGenerator.generateQuiz(summary.summary, keyDetailsJSON, 4);

        // Save quiz questions
        saveQuizQuestions(summaryId, quizQuestions);

        // Create result
        ConversationSummaryResult result = new ConversationSummaryResult();
        result.summary = summary;
        result.quizQuestions = quizQuestions;
        result.summaryId = summaryId;

        // Update goal status
        updateGoalStatus(state.goalId, summary.outcomeStatus);

        return result;
    }

    // Helper methods...

    /**
     * Select chats for 70/15/15 weighting using database success scores:
     * - 70%: Successful chats (score >= 0.7) from database
     * - 15%: Failed chats (score < 0.3) from database
     * - 15%: Base AI personality (handled separately)
     * 
     * Uses success scores stored in database (no OpenAI call needed)
     * Returns a list of example conversations selected to achieve 70/15/15 ratio
     */
    private List<List<Message>> selectChatsForWeighting(
            Map<Integer, List<Message>> filteredChats,
            String goalType,
            List<String> relevantCategories) {
        
        List<List<Message>> selectedExamples = new ArrayList<>();
        
        if (filteredChats.isEmpty()) {
            return selectedExamples;
        }

        // Get success scores from database (no OpenAI call needed!)
        List<ChatWithScore> allScoredChats = new ArrayList<>();
        
        try {
            // Get success scores from database for each dialog
            Map<Integer, Double> dialogSuccessScores = getSuccessScoresFromDatabase(
                new ArrayList<>(filteredChats.keySet()), relevantCategories);
            
            for (Map.Entry<Integer, List<Message>> entry : filteredChats.entrySet()) {
                int dialogId = entry.getKey();
                // Use database score if available, otherwise use 0.5 (neutral)
                double score = dialogSuccessScores.getOrDefault(dialogId, 0.5);
                allScoredChats.add(new ChatWithScore(dialogId, entry.getValue(), score));
            }
        } catch (SQLException e) {
            System.err.println("Error getting success scores from database: " + e.getMessage());
            // Fallback: use OpenAI scoring if database fails
            SuccessScorer successScorer = synthesisEngine.getSuccessScorer();
            for (Map.Entry<Integer, List<Message>> entry : filteredChats.entrySet()) {
                double score = successScorer.calculateSuccessScore(entry.getValue(), goalType);
                allScoredChats.add(new ChatWithScore(entry.getKey(), entry.getValue(), score));
            }
        }

        // Sort by score (descending)
        allScoredChats.sort((a, b) -> Double.compare(b.score, a.score));

        // Separate into successful (>= 0.7) and failed (< 0.3)
        List<ChatWithScore> successfulChats = new ArrayList<>();
        List<ChatWithScore> failedChats = new ArrayList<>();

        for (ChatWithScore chat : allScoredChats) {
            if (chat.score >= 0.7) {
                successfulChats.add(chat);
            } else if (chat.score < 0.3) {
                failedChats.add(chat);
            }
            // Chats with 0.3-0.69 are excluded (ambiguous)
        }

        // Select chats to achieve approximately 70/15 ratio
        // For example: if we want 10 total examples, 7 successful + 1-2 failed
        int maxExamples = Math.min(10, allScoredChats.size());
        int successfulCount = (int) Math.round(maxExamples * 0.70);
        int failedCount = Math.min(failedChats.size(), maxExamples - successfulCount);

        // Add successful chats (70%)
        for (int i = 0; i < Math.min(successfulCount, successfulChats.size()); i++) {
            selectedExamples.add(successfulChats.get(i).messages);
        }

        // Add failed chats (15%)
        for (int i = 0; i < failedCount; i++) {
            selectedExamples.add(failedChats.get(i).messages);
        }

        // If we have fewer successful chats than needed, fill with more failed chats
        if (selectedExamples.size() < maxExamples && failedChats.size() > failedCount) {
            int remaining = maxExamples - selectedExamples.size();
            for (int i = failedCount; i < Math.min(failedCount + remaining, failedChats.size()); i++) {
                selectedExamples.add(failedChats.get(i).messages);
            }
        }

        // If we still don't have enough, add more successful chats
        if (selectedExamples.size() < maxExamples && successfulChats.size() > successfulCount) {
            int remaining = maxExamples - selectedExamples.size();
            for (int i = successfulCount; i < Math.min(successfulCount + remaining, successfulChats.size()); i++) {
                selectedExamples.add(successfulChats.get(i).messages);
            }
        }

        return selectedExamples;
    }

    /**
     * Get success scores from database for dialogs (optimized - no OpenAI call)
     * Returns the maximum success score across all relevant categories for each dialog
     */
    private Map<Integer, Double> getSuccessScoresFromDatabase(
            List<Integer> dialogIds, List<String> relevantCategories) throws SQLException {
        
        Map<Integer, Double> scores = new HashMap<>();
        
        if (dialogIds.isEmpty() || relevantCategories.isEmpty()) {
            return scores;
        }

        String dialogPlaceholders = String.join(",", Collections.nCopies(dialogIds.size(), "?"));
        String categoryPlaceholders = String.join(",", Collections.nCopies(relevantCategories.size(), "?"));
        
        String sql = String.format("""
            SELECT dialog_id, MAX(success_score) as max_success_score
            FROM chat_goals
            WHERE dialog_id IN (%s)
              AND category_name IN (%s)
            GROUP BY dialog_id
            """, dialogPlaceholders, categoryPlaceholders);

        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            int index = 1;
            for (Integer dialogId : dialogIds) {
                pstmt.setInt(index++, dialogId);
            }
            for (String category : relevantCategories) {
                pstmt.setString(index++, category);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int dialogId = rs.getInt("dialog_id");
                    double successScore = rs.getDouble("max_success_score");
                    scores.put(dialogId, successScore);
                }
            }
        }

        return scores;
    }

    /**
     * Helper class to store chat with its success score
     */
    private static class ChatWithScore {
        final int dialogId;
        final List<Message> messages;
        final double score;

        ChatWithScore(int dialogId, List<Message> messages, double score) {
            this.dialogId = dialogId;
            this.messages = messages;
            this.score = score;
        }
    }

    private String buildEnhancedPrompt(ConversationState state, String incomingMessage, 
                                       List<List<Message>> successfulExamples) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are helping User X have a conversation with User Y.\n\n");
        prompt.append("Goal: ").append(state.goal.getDesiredOutcome()).append("\n");
        prompt.append("Meeting Context: ").append(state.goal.getMeetingContext()).append("\n\n");

        // Add successful examples with smart formatting and token management
        if (!successfulExamples.isEmpty()) {
            prompt.append("Here are examples of successful similar conversations:\n\n");
            
            SmartChatSelector selector = new SmartChatSelector();
            int remainingTokens = 6000; // Reserve tokens for prompt and response
            
            for (int i = 0; i < Math.min(5, successfulExamples.size()); i++) {
                List<Message> example = successfulExamples.get(i);
                
                // Estimate tokens for this example
                int exampleTokens = selector.estimateChatTokens(example);
                if (exampleTokens > remainingTokens / 2) {
                    // Skip if this example alone would use too much
                    continue;
                }
                
                prompt.append("Example ").append(i + 1).append(":\n");
                
                // Include FULL conversation (not just last 15 messages)
                for (Message msg : example) {
                    if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                        prompt.append(msg.isFromUser() ? "You" : "Them").append(": ")
                              .append(msg.getContent()).append("\n");
                    }
                }
                prompt.append("\n");
                
                remainingTokens -= exampleTokens;
                
                // Stop if we're running low on tokens
                if (remainingTokens < 1000) {
                    break;
                }
            }
        }

        // Add style profile
        ChatProfile profile = state.synthesizedProfile;
        prompt.append("Communication Style Guidelines:\n");
        prompt.append("- Humor Level: ").append(String.format("%.1f", profile.getHumorLevel())).append("\n");
        prompt.append("- Formality: ").append(String.format("%.1f", profile.getFormalityLevel())).append("\n");
        prompt.append("- Empathy: ").append(String.format("%.1f", profile.getEmpathyLevel())).append("\n");
        prompt.append("- Average Response Time: ").append(String.format("%.0f seconds", 
            profile.getResponseTimeAverage())).append("\n");
        prompt.append("- Average Message Length: ").append(String.format("%.0f words", 
            profile.getMessageLengthAverage())).append("\n\n");

        // Add FULL conversation history (not just last 10 messages)
        prompt.append("Current Conversation:\n");
        for (Message msg : state.messages) {
            if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                prompt.append(msg.isFromUser() ? "You" : "Them").append(": ")
                      .append(msg.getContent()).append("\n");
            }
        }
        if (!incomingMessage.equals(state.messages.isEmpty() ? "" : 
            (state.messages.get(state.messages.size() - 1).getContent()))) {
            prompt.append("\nLatest message from them: ").append(incomingMessage).append("\n");
        }
        prompt.append("\n");

        prompt.append("Generate a natural, engaging response that:");
        prompt.append("1. Matches the communication style guidelines above");
        prompt.append("2. Moves toward achieving the goal");
        prompt.append("3. Uses patterns from successful examples");
        prompt.append("4. Sounds human and authentic\n\n");
        prompt.append("Response:");

        return prompt.toString();
    }

    private void updateEngagementScore(ConversationState state) {
        if (state.messages.size() < 3) {
            state.engagementScore = 0.5;
            return;
        }

        // Calculate based on message length, question rate, response time
        DisinterestDetector.DisinterestAnalysis analysis = 
            disinterestDetector.analyzeConversation(state.messages);
        
        state.engagementScore = 1.0 - analysis.getProbability();
    }

    private void notifyUserOfDisinterest(ConversationState state, 
                                         DisinterestDetector.DisinterestAnalysis analysis) {
        // Save disinterest log to database
        try {
            saveDisinterestLog(state, analysis);
        } catch (SQLException e) {
            System.err.println("Error saving disinterest log: " + e.getMessage());
        }
        
        // In a real implementation, this would notify the UI
        System.out.println("DISINTEREST ALERT for conversation " + state.goalId);
        System.out.println("Probability: " + String.format("%.1f%%", analysis.getProbability() * 100));
        System.out.println("Recommendation: " + analysis.getRecommendation());
    }

    private long calculateLastMessageAge(ConversationState state) {
        if (state.messages.isEmpty()) return 0;
        
        Message lastMessage = state.messages.get(state.messages.size() - 1);
        if (lastMessage.getTimestamp() == null) return 0;
        
        return java.time.Duration.between(lastMessage.getTimestamp(), LocalDateTime.now()).getSeconds();
    }

    private void startConversationMonitoring(int goalId) {
        // Schedule periodic checks for new messages
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkForNewMessages(goalId);
            } catch (Exception e) {
                System.err.println("Error checking for new messages: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void checkForNewMessages(int goalId) {
        // Implementation would poll platform for new messages
        // This is platform-specific and would be handled by PlatformConnector
    }

    // Database operations (simplified - would need proper implementation)
    private void saveConversationState(ConversationState state) throws SQLException {
        // Implementation would save to conversation_states table
    }

    private List<Message> loadMessagesForDialog(int dialogId) {
        // Implementation would load from database
        return new ArrayList<>();
    }

    private Map<String, List<Message>> loadAllCategorizedChats(int userId, List<String> categories) {
        try {
            // Use smart filtering to get chats (deduplicated, AND filtered)
            Map<Integer, List<Message>> filteredChats = categorizationService.getSmartFilteredChats(
                categories, userId, 15);
            
            // Convert to Map<String, List<Message>> for compatibility
            Map<String, List<Message>> allChats = new HashMap<>();
            for (Map.Entry<Integer, List<Message>> entry : filteredChats.entrySet()) {
                String dialogName = "Dialog_" + entry.getKey();
                allChats.put(dialogName, entry.getValue());
            }
            return allChats;
        } catch (SQLException e) {
            System.err.println("Error loading categorized chats: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private String getTargetName(int targetUserId) throws SQLException {
        String sql = "SELECT name FROM target_users WHERE id = ?";
        try (Connection conn = DatabaseSchema.getConnectionInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, targetUserId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        }
        return "Target User";
    }

    private int saveConversationSummary(ConversationState state, 
                                       ConversationSummarizer.ConversationSummary summary) throws SQLException {
        // Implementation would save to conversation_summaries table
        return 0;
    }

    private void saveQuizQuestions(int summaryId, List<QuizGenerator.QuizQuestion> questions) throws SQLException {
        // Implementation would save to quiz_questions table
    }

    private void updateGoalStatus(int goalId, String status) throws SQLException {
        // Implementation would update goals table
    }

    private void saveDisinterestLog(ConversationState state, 
                                   DisinterestDetector.DisinterestAnalysis analysis) throws SQLException {
        // Implementation would save to disinterest_logs table
    }

    // Data classes
    private static class ConversationState {
        int userId;
        int targetUserId;
        int goalId;
        ConversationGoal goal;
        String platform;
        ChatProfile synthesizedProfile;
        List<String> relevantCategories;
        List<Message> messages;
        double engagementScore = 0.5;
        String pendingResponse;
        long responseDelay;
        LocalDateTime lastResponseTimestamp;
    }

    public static class ConversationSummaryResult {
        public ConversationSummarizer.ConversationSummary summary;
        public List<QuizGenerator.QuizQuestion> quizQuestions;
        public int summaryId;
    }
}

