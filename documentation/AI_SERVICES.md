# AI Services

This document describes all AI service integrations in the ARIA application, including OpenAI, Undetectable.ai, and related services.

**Related Documentation**: [Architecture](ARCHITECTURE.md) | [Automated Conversation Management](AUTOMATED_CONVERSATION.md)

## Table of Contents

1. [Overview](#overview)
2. [OpenAI Client](#openai-client)
3. [OpenAI Responses API Client](#openai-responses-api-client)
4. [Aria Response Manager](#aria-response-manager)
5. [Context Builder (70/15/15)](#context-builder-701515)
6. [Undetectable.ai Client](#undetectableai-client)
7. [Response Generator](#response-generator)
8. [Conversation Summarizer](#conversation-summarizer)
9. [Quiz Generator](#quiz-generator)

## Overview

The ARIA application integrates with multiple AI services:
- **OpenAI**: Text generation, categorization, summarization
- **OpenAI Responses API**: Conversation state management for AI suggestions
- **Undetectable.ai**: Humanization of AI-generated text

**Location**: `src/main/java/com/aria/ai/`

## OpenAI Client

**Location**: `src/main/java/com/aria/ai/OpenAIClient.java` (lines 1-144)

### Purpose

Provides HTTP client for OpenAI API interactions. Handles text generation, categorization, and summarization requests.

### Configuration

**Reference**: `OpenAIClient.java` lines 16-26

```java
public OpenAIClient() {
    this.client = new OkHttpClient();
    this.apiKey = ConfigurationManager.getRequiredProperty("openai.api.key");
    this.model = ConfigurationManager.getProperty("openai.model", "gpt-3.5-turbo");
    this.maxTokens = ConfigurationManager.getIntProperty("openai.max_tokens", 150);
    this.temperature = ConfigurationManager.getDoubleProperty("openai.temperature", 0.7);
}
```

### Methods

#### `generateResponse(String prompt)`

**Reference**: `OpenAIClient.java` lines 28-67

Generates a simple text response from a prompt.

```java
public String generateResponse(String prompt) {
    // Build request with model, messages, max_tokens, temperature
    // Send POST to https://api.openai.com/v1/chat/completions
    // Return content from response
}
```

#### `generateResponseWithMessages(JSONArray messages)`

**Reference**: `OpenAIClient.java` lines 74-108

Generates response with multiple messages (conversation context).

```java
public String generateResponseWithMessages(org.json.JSONArray messages) {
    // Supports system/user/assistant roles
    // Allows multi-turn conversations
}
```

#### `generateResponseWithContext(String systemPrompt, String conversationHistory, String userMessage)`

**Reference**: `OpenAIClient.java` lines 117-143

Generates response with extended context (system prompt + history + current message).

```java
public String generateResponseWithContext(String systemPrompt, String conversationHistory, String userMessage) {
    // Builds message array:
    // 1. System prompt (if provided)
    // 2. Conversation history (as assistant)
    // 3. User message (as user)
}
```

### API Configuration

- **Base URL**: `https://api.openai.com/v1/chat/completions`
- **Model**: `gpt-3.5-turbo` (default)
- **Max Tokens**: 150 (default)
- **Temperature**: 0.7 (default)

### Usage

```java
OpenAIClient client = new OpenAIClient();
String response = client.generateResponse("Hello, how are you?");
```

**Reference**: `OpenAIClient.java` lines 28-67

---

## OpenAI Responses API Client

**Location**: `src/main/java/com/aria/ai/OpenAIResponsesClient.java`

### Purpose

Provides client for OpenAI Responses API, which maintains conversation state across requests. This enables the AI to remember full conversation context without resending it every time.

### Configuration

**Reference**: `OpenAIResponsesClient.java` lines 12-22

```java
public OpenAIResponsesClient() {
    this.client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();
    this.apiKey = System.getenv("OPENAI_API_KEY");
    this.model = "gpt-4";
}
```

### Methods

#### `createResponse(String input)`

**Reference**: `OpenAIResponsesClient.java` lines 28-70

Creates a new response with full context (first call). OpenAI stores the conversation state and returns a response ID.

```java
public ResponseResult createResponse(String input) {
    // Sends full context to OpenAI with store=true
    // Returns response ID and output text
}
```

#### `continueResponse(String previousResponseId, String input)`

**Reference**: `OpenAIResponsesClient.java` lines 77-119

Continues existing conversation (subsequent calls). Only sends the new message; OpenAI remembers all previous context.

```java
public ResponseResult continueResponse(String previousResponseId, String input) {
    // Sends only new message with previous_response_id
    // OpenAI remembers all previous context
    // Returns updated response ID and output text
}
```

### API Configuration

- **Base URL**: `https://api.openai.com/v1/responses`
- **Model**: `gpt-4`
- **Timeout**: 30 seconds (connect), 60 seconds (read)

### Usage

```java
OpenAIResponsesClient client = new OpenAIResponsesClient();

// First call - send full context
ResponseResult result = client.createResponse(fullContext);
String responseId = result.responseId;
String suggestion = result.outputText;

// Subsequent calls - send only new message
ResponseResult nextResult = client.continueResponse(responseId, newMessage);
```

**Reference**: `OpenAIResponsesClient.java` lines 28-119

---

## Aria Response Manager

**Location**: `src/main/java/com/aria/ai/AriaResponseManager.java`

### Purpose

Manages OpenAI Responses API state for target users. Stores response IDs in database to maintain conversation context across sessions.

### Key Features

- **Per-Subtarget Isolation**: Each subtarget user has its own response ID
- **Cross-Platform Support**: Single response ID for cross-platform context (NULL subtarget_user_id)
- **Database Persistence**: Response IDs stored in `target_user_responses` table
- **In-Memory Cache**: Fast access with database sync

### Methods

#### `getResponseId(int targetUserId, Integer subtargetUserId)`

Gets stored response ID for a target/subtarget user combination.

#### `saveResponseId(int targetUserId, Integer subtargetUserId, String responseId)`

Saves response ID to database and cache.

#### `generateReply(TargetUser, SubTargetUser, String newMessage, String fullContext)`

Generates reply using OpenAI Responses API:
- If response ID exists: Continues conversation (sends only new message)
- If no response ID: Creates new conversation (sends full context)

### Database Schema

```sql
CREATE TABLE target_user_responses (
    id SERIAL PRIMARY KEY,
    target_user_id INTEGER NOT NULL REFERENCES target_users(id),
    subtarget_user_id INTEGER REFERENCES subtarget_users(id), -- NULL for cross-platform
    openai_response_id VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(target_user_id, subtarget_user_id)
)
```

### Usage

```java
AriaResponseManager manager = new AriaResponseManager();

// Generate reply (handles first vs subsequent calls automatically)
String suggestion = manager.generateReply(
    targetUser, 
    subtargetUser, 
    newMessage, 
    fullContext
);
```

**Reference**: `AriaResponseManager.java`

---

## Context Builder (70/15/15)

**Location**: `src/main/java/com/aria/ai/ContextBuilder70_15_15.java`

### Purpose

Builds comprehensive context for AI suggestions using the 70/15/15 strategy:
- **70%**: Successful dialogs (success score >= 0.7) in same categories
- **15%**: Failed dialogs (success score < 0.3) in same categories
- **15%**: AI improvement examples (enhanced successful examples)

### Key Features

- **Full Conversation History**: Loads ALL messages (not just last 50)
- **Category-Based Learning**: Finds similar dialogs in same categories
- **Comprehensive Context**: Includes target user info, subtarget user info, communication style, goals
- **Goal-Oriented Instructions**: Guides AI to gradually progress toward desired outcome

### Methods

#### `build70_15_15_Context(TargetUser, SubTargetUser, int userId, boolean crossPlatformContextEnabled)`

Builds comprehensive context including:

1. **Target User Information**:
   - Name, Bio, Desired Outcome, Where/How You Met, Important Details

2. **SubTarget User Information**:
   - Platform, Username, Communication Style Profile

3. **Current Conversation History**:
   - ALL messages with IDs and reference IDs

4. **Conversation Categories**:
   - Categories the current conversation belongs to

5. **Reference Conversations (70/15/15)**:
   - Successful examples (70%)
   - Failed examples (15%)
   - AI improvement examples (15%)

6. **AI Instructions**:
   - Goal progression guidance
   - Communication style matching
   - Response guidelines

### Usage

```java
ContextBuilder70_15_15 builder = new ContextBuilder70_15_15();

String fullContext = builder.build70_15_15_Context(
    targetUser,
    subtargetUser,
    userId,
    crossPlatformContextEnabled
);
```

**Reference**: `ContextBuilder70_15_15.java`

---

### Success Scoring and Categorization

**Location**: `src/main/java/com/aria/analysis/ChatCategorizationService.java`

The OpenAI client is used for chat categorization and success scoring with enhanced contextual understanding.

#### Enhanced Success Scoring

The system uses an enhanced prompt that evaluates success based on **three key factors**:

1. **Communication Quality (30% weight)**: 
   - Was the approach respectful, appropriate, and well-crafted?
   - Did it demonstrate good social/communication skills?
   - Was the message clear, engaging, and professional?

2. **Engagement Level (30% weight)**:
   - Did the person respond thoughtfully (even if negative)?
   - Were responses substantial and engaging, or dismissive?
   - Was there genuine interaction or just rejection?

3. **Contextual Understanding (40% weight)**:
   - **HIGH SCORE (70-100)**: Rejection due to circumstances, not approach
     - Example: "I have a boyfriend, but you seem really nice!" → Success score: 75-85%
     - Example: "Not enough funds now, but let's revisit in Q2" → Success score: 70-80%
     - Example: "Can't help now, but connect me on LinkedIn" → Success score: 75-85%
     - These show GOOD approach but external factors prevented success
   
   - **MEDIUM SCORE (40-69)**: Partial success or unclear outcome
     - Example: "Maybe later", "I'll think about it", "Need time"
   
   - **LOW SCORE (0-39)**: Poor approach or genuine disinterest
     - Example: "You're weird", "Leave me alone", "Not interested"
     - These show the approach itself was the problem

**Key Principle**: A thoughtful rejection with positive feedback should score HIGH because it indicates the communication was effective, even if the outcome wasn't ideal.

**Reference**: `ChatCategorizationService.java` lines 249-317: `buildCategorizationWithScoresPrompt()`

#### Outcome Types

The system uses `OutcomeType` enum to classify conversation outcomes:
- `SUCCESS`: Goal was successfully achieved
- `PARTIAL_SUCCESS`: Partial success or progress made
- `CIRCUMSTANTIAL_REJECTION`: Rejection due to circumstances, not approach (high score expected)
- `APPROACH_REJECTION`: Rejection due to poor approach (low score expected)
- `NEUTRAL`: No clear outcome

**Reference**: 
- [OutcomeType Enum](CORE_MODELS.md#outcometype-enum)
- `src/main/java/com/aria/core/model/OutcomeType.java` (lines 1-93)

#### Response Format

OpenAI returns JSON with categories, relevance scores, success scores, and outcome types:

```json
{
  "categories": [
    {
      "name": "dating",
      "relevance": 0.85,
      "success_score": 75,
      "outcome_type": "circumstantial_rejection",
      "reason": "Person has boyfriend but appreciated the approach"
    },
    {
      "name": "flirting",
      "relevance": 0.75,
      "success_score": 80,
      "outcome_type": "circumstantial_rejection",
      "reason": "Positive feedback despite rejection"
    }
  ]
}
```

**Reference**: `ChatCategorizationService.java` lines 453-507: `parseCategorizationWithScoresResponse()`

## Undetectable.ai Client

**Location**: `src/main/java/com/aria/ai/UndetectableAIClient.java` (lines 1-144)

### Purpose

Humanizes AI-generated text to make it undetectable as AI content. Uses Undetectable.ai API for text humanization.

### Configuration

**Reference**: `UndetectableAIClient.java` lines 17-26

```java
public UndetectableAIClient() {
    this.client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();
    this.apiKey = ConfigurationManager.getProperty("undetectable.ai.api.key", 
        System.getenv("UNDETECTABLE_AI_API_KEY"));
}
```

### Methods

#### `humanizeText(String text)`

**Reference**: `UndetectableAIClient.java` lines 33-60

Humanizes AI-generated text using Undetectable.ai API.

```java
public String humanizeText(String text) {
    // 1. Submit text for humanization
    String jobId = submitHumanization(text);
    
    // 2. Poll for result
    String humanizedText = pollForResult(jobId);
    
    // 3. Return humanized text or original on error
}
```

### API Configuration

- **Submit URL**: `https://api.undetectable.ai/submit`
- **Status URL**: `https://api.undetectable.ai/status`
- **Timeout**: 30 seconds (connect), 60 seconds (read)
- **Polling**: Max 30 attempts (1 second intervals)

### Humanization Settings

**Reference**: `UndetectableAIClient.java` lines 63-67

```java
requestBody.put("readability", "High School");
requestBody.put("purpose", "General Writing");
requestBody.put("strength", "More Human");
```

### Usage

```java
UndetectableAIClient client = new UndetectableAIClient();
String humanized = client.humanizeText(aiGeneratedText);
```

**Reference**: `UndetectableAIClient.java` lines 33-60

## Response Generator

**Location**: `src/main/java/com/aria/ai/ResponseGenerator.java` (lines 1-192)

### Purpose

Generates context-aware responses using OpenAI. Integrates conversation goals, style profiles, and historical examples.

### Configuration

**Reference**: `ResponseGenerator.java` lines 13-16

```java
public ResponseGenerator(OpenAIClient openAIClient) {
    this.openAIClient = openAIClient;
    this.styleProfile = new ChatProfile(); // Default profile
}
```

### Methods

#### `setConversationContext(ConversationGoal goal, String targetAlias, String platform)`

**Reference**: `ResponseGenerator.java` lines 28-32

Sets all conversation context at once.

```java
public void setConversationContext(ConversationGoal goal, String targetAlias, String platform) {
    this.currentGoal = goal;
    this.currentTargetAlias = targetAlias;
    this.currentPlatform = platform;
}
```

#### `generateResponse(String incomingMessage, String conversationHistory)`

**Reference**: `ResponseGenerator.java` lines 46-49

Generates response for incoming message.

```java
public String generateResponse(String incomingMessage, String conversationHistory) {
    String context = buildContext(incomingMessage, conversationHistory);
    return openAIClient.generateResponse(context);
}
```

#### `buildContext(String incomingMessage, String conversationHistory)`

**Reference**: `ResponseGenerator.java` lines 51-100

Builds enhanced context prompt with goal, style profile, and conversation history.

### Usage

```java
ResponseGenerator generator = new ResponseGenerator(openAIClient);
generator.setConversationContext(goal, "Alex", "telegram");
generator.setStyleProfile(profile);
String response = generator.generateResponse(incomingMessage, history);
```

**Reference**: `ResponseGenerator.java` lines 46-49

## Conversation Summarizer

**Location**: `src/main/java/com/aria/ai/ConversationSummarizer.java` (lines 1-159)

### Purpose

Generates comprehensive summaries of conversations and extracts key personal details using OpenAI.

### Configuration

**Reference**: `ConversationSummarizer.java` lines 14-16

```java
public ConversationSummarizer(OpenAIClient openAIClient) {
    this.openAIClient = openAIClient;
}
```

### Methods

#### `generateSummary(List<Message> messages, ConversationGoal goal)`

**Reference**: `ConversationSummarizer.java` lines 24-67

Generates comprehensive conversation summary with key details.

```java
public ConversationSummary generateSummary(List<Message> messages, ConversationGoal goal) {
    // 1. Format conversation history
    // 2. Build prompt with goal and conversation
    // 3. Request JSON response with:
    //    - summary
    //    - key_personal_details
    //    - next_steps
    //    - outcome_status
    //    - tone
    // 4. Parse and return ConversationSummary
}
```

### Summary Structure

**Reference**: `ConversationSummarizer.java` lines 44-59

```json
{
    "summary": "A concise summary of the conversation flow...",
    "key_personal_details": {
        "name": "Person's name",
        "occupation": "Occupation",
        "interests": ["interest1", "interest2"],
        "location": "Location",
        "important_facts": ["fact1", "fact2"],
        "preferences": ["preference1", "preference2"]
    },
    "next_steps": "Agreed-upon next steps",
    "outcome_status": "pending/achieved/failed",
    "tone": "friendly/professional/romantic/etc"
}
```

### Usage

```java
ConversationSummarizer summarizer = new ConversationSummarizer(openAIClient);
ConversationSummary summary = summarizer.generateSummary(messages, goal);
```

**Reference**: `ConversationSummarizer.java` lines 24-67

## Quiz Generator

**Location**: `src/main/java/com/aria/ai/QuizGenerator.java` (lines 1-150)

### Purpose

Generates quiz questions from conversation summaries to ensure user remembers key details before meetings/dates.

### Configuration

**Reference**: `QuizGenerator.java` lines 14-16

```java
public QuizGenerator(OpenAIClient openAIClient) {
    this.openAIClient = openAIClient;
}
```

### Methods

#### `generateQuiz(String summary, String keyDetailsJson, int numQuestions)`

**Reference**: `QuizGenerator.java` lines 23-80

Generates quiz questions from summary and key details.

```java
public List<QuizQuestion> generateQuiz(String summary, String keyDetailsJson, int numQuestions) {
    // 1. Build prompt with summary and key details
    // 2. Request JSON with questions and answers
    // 3. Parse and return QuizQuestion list
}
```

### Quiz Structure

**Reference**: `QuizGenerator.java` lines 44-58

```json
{
    "questions": [
        {
            "question": "What is the person's occupation?",
            "answer": "VC investor",
            "type": "text"
        },
        // ... more questions
    ]
}
```

### Usage

```java
QuizGenerator quizGenerator = new QuizGenerator(openAIClient);
List<QuizQuestion> questions = quizGenerator.generateQuiz(summary, keyDetailsJson, 5);
```

**Reference**: `QuizGenerator.java` lines 23-80

## Error Handling

### OpenAI Errors

- **API Errors**: Logged and fallback response returned
- **Timeout**: Handled with exception and fallback
- **Rate Limiting**: Should implement retry logic

**Reference**: `OpenAIClient.java` lines 49-65

### Undetectable.ai Errors

- **API Errors**: Logged, original text returned
- **Timeout**: Polling timeout, original text returned
- **Missing API Key**: Warning logged, original text returned

**Reference**: `UndetectableAIClient.java` lines 34-59

## Configuration

### Required Configuration

Add to `config.properties`:

```properties
openai.api.key=your_openai_api_key
undetectable.ai.api.key=your_undetectable_ai_api_key
openai.model=gpt-3.5-turbo
openai.max_tokens=150
openai.temperature=0.7
```

### Environment Variables

- `OPENAI_API_KEY`
- `UNDETECTABLE_AI_API_KEY`

## Integration Flow

```
User message received
    ↓
ResponseGenerator.generateResponse()
    ↓
OpenAIClient.generateResponseWithContext()
    ↓
OpenAI API → AI-generated response
    ↓
UndetectableAIClient.humanizeText()
    ↓
Undetectable.ai API → Humanized response
    ↓
Response sent to user
```

**Reference**: [Automated Conversation Management](AUTOMATED_CONVERSATION.md) for complete flow

---

**Next**: [Analysis Services](ANALYSIS_SERVICES.md) | [Automated Conversation Management](AUTOMATED_CONVERSATION.md)

