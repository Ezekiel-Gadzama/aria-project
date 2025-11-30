# Backend AI Suggestion and Analysis Logic

This document explains how the backend handles AI suggestions and analysis in the ARIA system.

## Table of Contents

1. [AI Suggestion System](#ai-suggestion-system)
2. [Analysis System](#analysis-system)
3. [Key Files and Components](#key-files-and-components)
4. [Data Flow](#data-flow)

---

## AI Suggestion System

### Overview

The AI suggestion system generates contextual reply suggestions for users during conversations using a sophisticated **70/15/15 strategy** with OpenAI Responses API for conversation state management. The system:

1. **Uses ALL conversation history** (not just last 50 messages) to understand communication style
2. **Learns from similar conversations** in the same categories (70% successful, 15% failed, 15% AI improvement)
3. **Maintains conversation state** using OpenAI Responses API (remembers full context across requests)
4. **Gradually progresses toward goals** with each suggested reply
5. **Supports cross-platform context** when enabled for a target user

### API Endpoint

**GET `/api/conversations/suggest`**

**Parameters:**
- `targetUserId` (required): ID of the target user
- `userId` (optional): ID of the current user (defaults to 1)
- `subtargetUserId` (optional): ID of the subtarget user (platform-specific instance)

**Response:**
```json
{
  "success": true,
  "message": "AI suggestion generated",
  "data": "Generated AI suggestion text"
}
```

**Fallback Response:**
If OpenAI API fails (missing key, quota exceeded, etc.), returns a default contextually appropriate response:
```json
{
  "success": true,
  "message": "AI suggestion generated",
  "data": "How about we discuss this further?"
}
```

### Implementation Flow

#### First Request (No Previous Response ID)

1. **Load Target User**: Retrieves target user information (name, bio, desired outcome, meeting context, important details)
2. **Load SubTarget User**: If provided, retrieves platform-specific instance and communication settings
3. **Check Cross-Platform Context**: Determines if cross-platform context is enabled
4. **Load ALL Conversation History**: Fetches **ALL messages** from current conversation (not just last 50)
5. **Get Categories**: Retrieves categories for current conversation from database
6. **Build 70/15/15 Context**: 
   - Loads reference dialogs in same categories
   - Separates into: 70% successful (score >= 0.7), 15% failed (score < 0.3), 15% AI improvement
   - Builds comprehensive context including:
     - Target user information
     - SubTarget user information and communication style
     - Full conversation history
     - Reference examples (70/15/15)
     - Instructions for AI on goal progression
7. **Create OpenAI Response**: Sends full context to OpenAI Responses API with `store=true`
8. **Save Response ID**: Stores response ID in database for future requests
9. **Return Suggestion**: Returns generated text to frontend

#### Subsequent Requests (Has Previous Response ID)

1. **Load Response ID**: Retrieves stored response ID from database
2. **Get Last Message**: Fetches the last message from target user (the message to respond to)
3. **Continue Conversation**: Sends only the new message to OpenAI Responses API with `previous_response_id`
4. **Update Response ID**: Updates stored response ID in database
5. **Return Suggestion**: Returns generated text to frontend

### 70/15/15 Strategy

The system uses a weighted learning approach:

- **70% Successful Examples**: Dialogs with success score >= 0.7 in the same categories
  - Shows what worked in similar situations
  - Demonstrates effective communication patterns
  - Guides AI to replicate successful approaches

- **15% Failed Examples**: Dialogs with success score < 0.3 in the same categories
  - Shows what didn't work
  - Helps AI avoid repeating mistakes
  - Provides negative examples for learning

- **15% AI Improvement Examples**: Top successful examples enhanced with AI techniques
  - Demonstrates how AI can improve communication
  - Shows advanced communication patterns
  - Guides gradual goal progression

### Context Building

The `ContextBuilder70_15_15` class builds comprehensive context including:

1. **Target User Information**:
   - Name, Bio, Desired Outcome, Where/How You Met, Important Details

2. **SubTarget User Information**:
   - Platform, Username, Platform-specific Name, Phone Number
   - Communication Style Profile:
     - Humor Level (0.0-1.0)
     - Formality Level (0.0-1.0)
     - Empathy Level (0.0-1.0)
     - Response Time Average (seconds)
     - Message Length Average (words)
     - Question Rate (0.0-1.0)
     - Engagement Level (0.0-1.0)
     - Preferred Opening

3. **Current Conversation History**:
   - ALL messages from the conversation (not just last 50)
   - Includes message IDs and reference IDs for replies
   - Preserves full context for understanding communication style

4. **Conversation Categories**:
   - Categories the current conversation belongs to
   - Used to find similar reference dialogs

5. **Reference Conversations (70/15/15)**:
   - Successful examples (70%)
   - Failed examples (15%)
   - AI improvement examples (15%)
   - All from dialogs in the same categories

6. **AI Instructions**:
   - Goal-oriented guidance
   - Communication style matching
   - Gradual progression toward desired outcome
   - Natural, authentic responses

### Conversation State Management

The system uses OpenAI Responses API to maintain conversation state:

- **First Request**: Sends full 70/15/15 context, OpenAI stores it and returns a response ID
- **Subsequent Requests**: Sends only new message with previous response ID, OpenAI remembers all context
- **Per-Subtarget Isolation**: Each subtarget user has its own response ID (or shared for cross-platform)
- **Database Storage**: Response IDs stored in `target_user_responses` table:
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

### Key Components

#### 1. AriaResponseManager (`src/main/java/com/aria/ai/AriaResponseManager.java`)

Manages OpenAI Responses API state and response IDs.

**Key Methods:**
- `getResponseId(int targetUserId, Integer subtargetUserId)`: Gets stored response ID
- `saveResponseId(int targetUserId, Integer subtargetUserId, String responseId)`: Saves response ID to database
- `generateReply(TargetUser, SubTargetUser, String newMessage, String fullContext)`: Generates reply using Responses API

#### 2. ContextBuilder70_15_15 (`src/main/java/com/aria/ai/ContextBuilder70_15_15.java`)

Builds comprehensive 70/15/15 context for first request.

**Key Methods:**
- `build70_15_15_Context(TargetUser, SubTargetUser, int userId, boolean crossPlatformContextEnabled)`: Builds full context
- `loadAllCurrentConversationMessages(...)`: Loads ALL messages (not just last 50)
- `getCategoriesForCurrentConversation(...)`: Gets categories for current conversation
- `getReferenceDialogsInCategories(...)`: Gets reference dialogs in same categories
- `separateDialogsBySuccess(...)`: Separates into 70/15/15 groups

#### 3. OpenAIResponsesClient (`src/main/java/com/aria/ai/OpenAIResponsesClient.java`)

Client for OpenAI Responses API.

**Key Methods:**
- `createResponse(String input)`: Creates new response with full context (first call)
- `continueResponse(String previousResponseId, String input)`: Continues existing conversation

#### 4. ResponseGenerator (`src/main/java/com/aria/ai/ResponseGenerator.java`)

Legacy response generation (still used for fallback scenarios).

**Key Methods:**
- `generateResponse(String prompt)`: Generates response from a prompt
- `generateResponse(String incomingMessage, String conversationHistory)`: Generates contextual response
- `generateOpeningLine()`: Generates an opening message
- `setStyleProfile(ChatProfile)`: Sets communication style preferences

#### 5. ChatProfile (`src/main/java/com/aria/core/model/ChatProfile.java`)

Stores communication style preferences:
- `humorLevel`: 0.0-1.0 (serious to humorous)
- `formalityLevel`: 0.0-1.0 (casual to formal)
- `empathyLevel`: 0.0-1.0 (direct to empathetic)
- `responseTimeAverage`: Average response time in seconds
- `messageLengthAverage`: Average message length in words
- `questionRate`: 0.0-1.0 (rarely asks to asks many questions)
- `engagementLevel`: 0.0-1.0 (low to high engagement)

### Code Location

**Main Endpoint**: `src/main/java/com/aria/api/controller/ConversationController.java`
- Method: `generateSuggestion()` (lines ~1157-1290)

**Core Components**:
- `src/main/java/com/aria/ai/AriaResponseManager.java`: Response ID management
- `src/main/java/com/aria/ai/ContextBuilder70_15_15.java`: Context building
- `src/main/java/com/aria/ai/OpenAIResponsesClient.java`: OpenAI Responses API client

---

## Analysis System

### Overview

The analysis system calculates metrics about conversations with target users, including sentiment, engagement, disinterest detection, conversation flow, and goal progression.

### API Endpoint

**GET `/api/targets/analysis`**

**Parameters:**
- `userId` (required): ID of the current user
- `targetId` (optional): ID of specific target user (if null, returns general analysis)
- `platform` (optional): Filter by platform name
- `platformAccountId` (optional): Filter by platform account ID
- `category` (optional): Filter by chat category

**Response:**
```json
{
  "success": true,
  "data": {
    "sentiment": {
      "average": 0.65,
      "trend": "positive",
      "withTargets": 0.65,
      "withoutTargets": 0.55,
      "improvement": 0.10
    },
    "engagement": {
      "score": 0.72,
      "responsiveness": 0.68,
      "messageLength": 45,
      "initiationFrequency": 0.35
    },
    "disinterest": {
      "detected": false,
      "signs": []
    },
    "conversationFlow": {
      "avgResponseTime": 120,
      "turnTaking": 0.75
    },
    "goalProgression": {
      "score": 0.60,
      "status": "Making progress"
    },
    "topTargets": [...] // Only for general analysis
  }
}
```

### Implementation Flow

1. **Query Messages**: Retrieves messages from last 3 months matching filters
2. **Separate Target vs Non-Target**: Categorizes messages as from target users or others
3. **Calculate Metrics**:
   - **Sentiment**: Analyzes positive/negative keywords and message length
   - **Engagement**: Calculates responsiveness, message length, initiation frequency
   - **Disinterest**: Detects signs of disinterest (short replies, delays, etc.)
   - **Conversation Flow**: Analyzes response times and turn-taking patterns
   - **Goal Progression**: Estimates progress toward conversation goals
4. **Return Analysis**: Returns calculated metrics

### Key Components

#### 1. TargetController (`src/main/java/com/aria/api/controller/TargetController.java`)

**Main Method**: `calculateAnalysis()` (lines ~1456-1604)

**Helper Methods**:
- `calculateSentiment()`: Calculates sentiment scores
- `calculateEngagement()`: Calculates engagement metrics
- `detectDisinterest()`: Detects disinterest signs
- `calculateConversationFlow()`: Analyzes conversation patterns
- `calculateGoalProgression()`: Estimates goal progress

#### 2. MessageData Class

Internal class in TargetController to represent message data:
```java
private static class MessageData {
    String text;
    String sender;
    java.sql.Timestamp timestamp;
    boolean fromUser;
    boolean hasMedia;
    int targetId;
    String targetName;
}
```

### Analysis Metrics Explained

#### Sentiment Analysis
- **Average**: Overall sentiment score (0.0-1.0)
- **Trend**: "positive", "negative", or "neutral"
- **With Targets**: Sentiment in conversations with target users
- **Without Targets**: Sentiment in other conversations
- **Improvement**: Difference between target and non-target sentiment

#### Engagement Score
- **Score**: Overall engagement (0.0-1.0)
- **Responsiveness**: How quickly and frequently target responds
- **Message Length**: Average characters per message
- **Initiation Frequency**: How often target starts conversations

#### Disinterest Detection
- **Detected**: Boolean indicating if disinterest is detected
- **Signs**: List of disinterest indicators (e.g., "Short replies", "Long delays")

#### Conversation Flow
- **Avg Response Time**: Average time between messages (seconds)
- **Turn Taking**: Balance of conversation turns (0.0-1.0)

#### Goal Progression
- **Score**: Progress toward goal (0.0-1.0)
- **Status**: Text description of progress level

### Code Location

**Main Endpoint**: `src/main/java/com/aria/api/controller/TargetController.java`
- Method: `getAnalysis()` (lines ~1370-1396)
- Calculation: `calculateAnalysis()` (lines ~1456-1604)

---

## Key Files and Components

### AI Suggestion Files

1. **`src/main/java/com/aria/api/controller/ConversationController.java`**
   - Endpoint: `generateSuggestion()` (GET `/api/conversations/suggest`)
   - Handles request/response and fallback logic

2. **`src/main/java/com/aria/ai/AriaResponseManager.java`**
   - Manages OpenAI Responses API state
   - Stores and retrieves response IDs from database
   - Handles conversation continuation

3. **`src/main/java/com/aria/ai/ContextBuilder70_15_15.java`**
   - Builds comprehensive 70/15/15 context
   - Loads all conversation history
   - Retrieves and separates reference dialogs

4. **`src/main/java/com/aria/ai/OpenAIResponsesClient.java`**
   - Client for OpenAI Responses API
   - Handles conversation state management

5. **`src/main/java/com/aria/ai/ResponseGenerator.java`**
   - Legacy response generation (fallback scenarios)
   - Handles style profiles and conversation context

6. **`src/main/java/com/aria/ai/OpenAIClient.java`**
   - Direct OpenAI API integration (legacy)
   - Handles API calls and retries

7. **`src/main/java/com/aria/core/model/ChatProfile.java`**
   - Communication style profile model
   - Stores humor, formality, empathy levels, etc.

### Analysis Files

1. **`src/main/java/com/aria/api/controller/TargetController.java`**
   - Endpoint: `getAnalysis()` (GET `/api/targets/analysis`)
   - Calculation methods for all metrics

2. **`src/main/java/com/aria/analysis/ChatCategorizationService.java`**
   - Categorizes chats into categories
   - Used for filtering and analysis

3. **`src/main/java/com/aria/analysis/DisinterestDetector.java`**
   - Detects disinterest in conversations
   - Analyzes conversation patterns

### Supporting Files

1. **`src/main/java/com/aria/storage/DatabaseManager.java`**
   - Database operations
   - Message and dialog queries

2. **`src/main/java/com/aria/service/TargetUserService.java`**
   - Target user operations
   - Retrieves target user data

3. **`src/main/java/com/aria/service/SubTargetUserService.java`**
   - SubTarget user operations
   - Retrieves platform-specific instances

---

## Data Flow

### AI Suggestion Flow

#### First Request (No Previous Response ID)

```
Frontend (ConversationView.js)
    ↓
    Click "AI" button
    ↓
    Call conversationApi.getSuggestion()
    ↓
Backend (ConversationController.generateSuggestion())
    ↓
    1. Load target user and subtarget user
    2. Check if response ID exists in database
    3. If not, build 70/15/15 context:
       a. Load ALL conversation messages (not just last 50)
       b. Get categories for current conversation
       c. Get reference dialogs in same categories
       d. Separate into 70% successful, 15% failed, 15% AI improvement
       e. Build comprehensive context with:
          - Target user info
          - SubTarget user info and communication style
          - Full conversation history
          - Reference examples (70/15/15)
          - AI instructions
    ↓
AriaResponseManager.generateReply()
    ↓
OpenAIResponsesClient.createResponse()
    ↓
OpenAI Responses API (stores full context)
    ↓
Save response ID to database
    ↓
Return generated suggestion
    ↓
Frontend displays suggestion
    ↓
User clicks "Copy to Message Field"
    ↓
Suggestion copied to input field
```

#### Subsequent Requests (Has Previous Response ID)

```
Frontend (ConversationView.js)
    ↓
    Click "AI" button
    ↓
    Call conversationApi.getSuggestion()
    ↓
Backend (ConversationController.generateSuggestion())
    ↓
    1. Load response ID from database
    2. Get last message from target user
    ↓
AriaResponseManager.generateReply()
    ↓
OpenAIResponsesClient.continueResponse()
    ↓
OpenAI Responses API (remembers all context)
    ↓
Update response ID in database
    ↓
Return generated suggestion
    ↓
Frontend displays suggestion
```

### Analysis Flow

```
Frontend (AnalysisDashboard.js)
    ↓
    Call targetApi.getAnalysis()
    ↓
Backend (TargetController.getAnalysis())
    ↓
    1. Query messages from database (last 3 months)
    2. Filter by target, platform, category
    3. Separate target vs non-target messages
    ↓
Calculate Metrics:
    - Sentiment
    - Engagement
    - Disinterest
    - Conversation Flow
    - Goal Progression
    ↓
Return analysis data
    ↓
Frontend displays metrics
```

---

## Configuration

### OpenAI Configuration

The OpenAI client is configured via environment variables or defaults:
- **API Key**: Set via `OPENAI_API_KEY` environment variable
- **Model**: Defaults to `gpt-4` for Responses API (configurable in `OpenAIResponsesClient`)
- **Legacy Model**: `gpt-3.5-turbo` for legacy `OpenAIClient` (fallback scenarios)
- **Max Tokens**: Defaults to 500 (configurable)
- **Temperature**: Defaults to 0.7 (configurable)

**OpenAI Responses API**:
- Uses `gpt-4` model
- Stores conversation state on OpenAI servers
- First request: Sends full context with `store=true`
- Subsequent requests: Sends only new message with `previous_response_id`

### Database Configuration

Database connection is configured via environment variables:
- **DATABASE_URL**: PostgreSQL connection URL
- **DATABASE_USER**: Database username
- **DATABASE_PASSWORD**: Database password

---

## Error Handling

### AI Suggestion Errors

- **Target user not found**: Returns 400 Bad Request
- **SubTarget user not found or invalid**: Returns 400 Bad Request
- **OpenAI API failure** (missing key, quota exceeded, etc.): Returns default contextually appropriate response (200 OK)
- **Message loading failure**: Logs warning but continues (uses empty history)
- **Context building failure**: Logs error and falls back to default response
- **Response ID retrieval failure**: Treats as first request and builds full context

**Fallback Behavior**:
- If OpenAI API fails, system returns a default response based on:
  - Whether conversation has messages (opening vs reply)
  - Preferred opening from SubTarget user settings (if available)
  - Contextually appropriate default messages
- Frontend always receives a valid response (never fails silently)

### Analysis Errors

- **Database connection failure**: Returns default analysis values
- **Query errors**: Logs error and returns default analysis
- **Invalid parameters**: Returns 400 Bad Request

---

## Future Enhancements

1. **Multiple Suggestions**: Generate multiple suggestions and let user choose
2. **Learning**: Improve suggestions based on user feedback (which suggestions were used/edited)
3. **Real-time Analysis**: Update analysis metrics in real-time as messages arrive
4. **Advanced Sentiment**: Use NLP libraries for more accurate sentiment analysis
5. **Goal Tracking**: Track specific goal milestones and achievements
6. **Response Quality Scoring**: Score generated suggestions before showing to user
7. **A/B Testing**: Test different suggestion strategies and learn which work best
8. **Context Optimization**: Optimize context size for better token efficiency while maintaining quality

