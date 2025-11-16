# AI Services

This document describes all AI service integrations in the ARIA application, including OpenAI, Undetectable.ai, and related services.

**Related Documentation**: [Architecture](ARCHITECTURE.md) | [Automated Conversation Management](AUTOMATED_CONVERSATION.md)

## Table of Contents

1. [Overview](#overview)
2. [OpenAI Client](#openai-client)
3. [Undetectable.ai Client](#undetectableai-client)
4. [Response Generator](#response-generator)
5. [Conversation Summarizer](#conversation-summarizer)
6. [Quiz Generator](#quiz-generator)

## Overview

The ARIA application integrates with multiple AI services:
- **OpenAI**: Text generation, categorization, summarization
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

