# Chat Categorization and Smart Filtering Logic

This document explains the intelligent categorization and filtering system used by ARIA to categorize chats, deduplicate across categories, filter unwanted chats, and manage conversation length for OpenAI API calls.

**Related Documentation**: [Analysis Services](ANALYSIS_SERVICES.md) | [Database Schema](STORAGE.md) | [AI Services](AI_SERVICES.md)

## Table of Contents

1. [Overview](#overview)
2. [Category System](#category-system)
3. [Chat Ingestion and Filtering](#chat-ingestion-and-filtering)
4. [Categorization Process](#categorization-process)
5. [Deduplication](#deduplication)
6. [Smart AND Filtering](#smart-and-filtering)
7. [Token Management](#token-management)
8. [Database Schema](#database-schema)
9. [Example Scenario](#example-scenario)
10. [Configuration](#configuration)
11. [Incremental Categorization](#incremental-categorization)
12. [Smart Score Merging](#smart-score-merging)
13. [Code Flow](#code-flow)

## Overview

The ARIA system uses an intelligent categorization and filtering system to:

1. **Categorize chats** into 500+ predefined categories using OpenAI
2. **Deduplicate chats** that belong to multiple categories
3. **Filter out groups, channels, and bots** during ingestion
4. **Use progressive AND filtering** to manage conversation length for OpenAI

### Key Benefits

- **No Duplicates**: Each chat appears once, even if in multiple categories
- **Clean Data**: No groups, channels, or bots in analysis
- **Token Management**: Automatically adjusts to fit OpenAI limits
- **Relevance**: Uses AND filtering to get most relevant chats
- **Scalability**: Works with thousands of chats

## Category System

### ChatCategory Enum

**Location**: `src/main/java/com/aria/core/model/ChatCategory.java` (lines 1-1430)

The system uses a `ChatCategory` enum with **500+ categories**, each containing:

- **Name**: Exact category identifier (e.g., "dating", "romance", "investment")
- **Description**: Clear explanation of when a chat belongs to this category
- **Keywords**: Common terms associated with the category

**Example Category Definition**:
```java
DATING(
    "dating",
    "Conversations about going on dates, setting up romantic meetings, or expressing romantic interest. Includes topics like asking someone out, planning dates, discussing relationship intentions.",
    new String[]{"date", "go out", "coffee", "dinner", "movie", "together", "romantic interest"}
)
```

**Reference**: `ChatCategory.java` lines 14-18

### Why This Design?

1. **Consistent Naming**: OpenAI always returns exact category names from the enum
2. **Better Categorization**: Descriptions help OpenAI understand when to apply each category
3. **No Duplicates**: Same chat won't be categorized as both "dating" and "date"
4. **Database Sync**: Categories are synced to `chat_categories` table on startup

**Code Reference**: 
- `DatabaseSchema.java` lines 162-180: `insertDefaultCategories()` method
- `ChatCategory.formatForOpenAI()`: Formats categories for OpenAI prompts

## Chat Ingestion and Filtering

### Python Script: Filtering During Ingestion

**Location**: `scripts/telethon/chat_ingestor.py`

When ingesting chats from Telegram, the Python script filters out unwanted chats:

```python
# Only process private user chats
if dialog.is_user:
    # Skip bots
    if hasattr(entity, 'bot') and entity.bot:
        safe_print(f"Skipping bot: {dialog.name}")
        continue
    
    # Determine type
    dialog_type = "private"  # Default
    if dialog.is_group: dialog_type = "group"
    elif dialog.is_channel: dialog_type = "channel"
    elif hasattr(entity, 'bot') and entity.bot: dialog_type = "bot"
    
    # Skip groups, channels, and bots
    if dialog_type in ["group", "channel", "bot", "supergroup"]:
        safe_print(f"Skipping {dialog_type}: {dialog.name}")
        continue
    
    # Process only private chats
    dialog_id = save_dialog(user_id, dialog.entity.id, safe_chat_name, dialog_type, 0, 0)
```

**Reference**: `scripts/telethon/chat_ingestor.py` lines 80-95

### Java Side: Database-Level Filtering

**Location**: `src/main/java/com/aria/storage/DatabaseManager.java`

The `saveDialog()` method also filters out unwanted chats:

```java
public static int saveDialog(int userId, long dialogId, String name, String type,
                             int messageCount, int mediaCount) throws SQLException {
    // Filter out groups, channels, and bots
    if (type != null && (type.equalsIgnoreCase("group") ||
                         type.equalsIgnoreCase("channel") ||
                         type.equalsIgnoreCase("supergroup") ||
                         type.equalsIgnoreCase("bot"))) {
        throw new SQLException("Skipping group/channel/bot dialog: " + name);
    }
    
    // Save private chats only
    // ...
}
```

**Reference**: `DatabaseManager.java` lines 150-165

### SQL-Level Filtering

When retrieving chats, SQL queries also filter out unwanted types:

```sql
WHERE d.type NOT IN ('group', 'channel', 'supergroup', 'bot')
  AND (d.is_bot IS NULL OR d.is_bot = FALSE)
```

**Reference**: 
- `SmartChatSelector.java` lines 72-73: OR logic filtering
- `SmartChatSelector.java` lines 148-149: AND logic filtering

## Categorization Process

### Initial Categorization (First Time)

When a chat is categorized for the first time:

1. **Get all available categories** from the `ChatCategory` enum
   - **Code**: `ChatCategorizationService.getAllCategories()` returns `ChatCategory.getAllNames()`
   - **Reference**: `ChatCategorizationService.java` lines 50-55

2. **Format categories and outcome types** for OpenAI prompt
   - Uses `ChatCategory.formatForOpenAI()` to generate formatted category list
   - Uses `OutcomeType.formatForOpenAI()` to include outcome type definitions
   - **Reference**: `ChatCategorizationService.java` lines 250-252

3. **Send entire chat conversation to OpenAI** with category list
   - Builds prompt with full chat text and all categories
   - Asks for both categorization AND success scoring in one call
   - **Reference**: `ChatCategorizationService.java` lines 47-74: `categorizeChatWithScores()`

4. **OpenAI returns** JSON with:
   - Category names and relevance scores (0.0-1.0)
   - Success scores (0-100 converted to 0.0-1.0)
   - Outcome types (circumstantial_rejection, approach_rejection, success, etc.)
   - Reasons for scoring

5. **Parse and save** to `chat_goals` table
   - Each category stored with `dialog_id`, `category_name`, `relevance_score`, `success_score`, and `outcome`
   - **Reference**: `ChatCategorizationService.java` lines 511-540: `saveChatCategorizationWithScores()`

### Incremental Categorization (Updates)

When a chat has new messages (incremental ingestion):

1. **Get last categorization timestamp** from database
   - **Code**: `getLastCategorizationTimestamp(dialogId)` 
   - **Reference**: `ChatCategorizationService.java` lines 703-721

2. **Load only NEW messages** (after last categorization)
   - **Code**: `loadNewMessagesForDialog(dialogId, lastCategorizedAt)`
   - Query: `SELECT * FROM messages WHERE dialog_id = ? AND timestamp > ?`
   - **Reference**: `ChatCategorizationService.java` lines 723-759

3. **Get existing categories** from database
   - **Code**: `getExistingCategories(dialogId)`
   - Loads all categories with their scores for this dialog
   - **Reference**: `ChatCategorizationService.java` lines 662-701

4. **Send ONLY new messages to OpenAI** (not entire conversation)
   - Saves significant tokens and API costs
   - **Reference**: `ChatCategorizationService.java` lines 784-806: `categorizeMessagesWithScores()`

5. **OpenAI returns** new categories and updated scores based on new messages

6. **Merge categories intelligently**:
   - **New categories**: Simply add to database
   - **Existing categories**: Use smart score merging formula (see below)
   - **Reference**: `ChatCategorizationService.java` lines 808-860: `mergeCategoryScores()`

### Example OpenAI Response (Initial Categorization)

```json
{
  "categories": [
    {
      "name": "dating",
      "relevance": 0.85,
      "success_score": 75,
      "outcome_type": "circumstantial_rejection",
      "reason": "Person has boyfriend but appreciated the approach - high communication quality"
    },
    {
      "name": "flirting",
      "relevance": 0.75,
      "success_score": 80,
      "outcome_type": "circumstantial_rejection",
      "reason": "Positive feedback despite rejection - communication was effective"
    },
    {
      "name": "romance",
      "relevance": 0.70,
      "success_score": 70,
      "outcome_type": "partial_success",
      "reason": "Romantic interest expressed, partial progress made"
    }
  ]
}
```

**Code Reference**: `ChatCategorizationService.java` lines 453-507: `parseCategorizationWithScoresResponse()`

### Example OpenAI Response (Incremental Update)

When only new messages are sent, OpenAI may return:
- New categories that weren't in the chat before
- Updated scores for existing categories (based on new context)
- New outcome types if circumstances changed

The system then merges these with existing categories intelligently.

## Deduplication

### Problem

A chat can belong to multiple categories (e.g., both "dating" and "flirting"). Without deduplication, the same chat would be retrieved multiple times when fetching chats by categories.

### Solution

1. **Database Level**: `chat_goals` table uses `(dialog_id, category_name)` as unique key
   - **Reference**: `DatabaseSchema.java` lines 48-61

2. **SQL Level**: Use `DISTINCT` on `dialog_id` when retrieving chats
   ```sql
   SELECT DISTINCT d.id as dialog_id
   FROM dialogs d
   INNER JOIN chat_goals cg ON d.id = cg.dialog_id
   WHERE cg.category_name IN (...)
   ```
   - **Reference**: `SmartChatSelector.java` lines 67-77

3. **Java Level**: Use `Map<Integer, ChatCategoryResult>` with `dialog_id` as key
   - **Reference**: `ChatCategorizationService.java` lines 320-340: `getChatsByCategories()`

### Example

- Chat with "Sarah" belongs to: ["dating", "flirting", "romance"]
- Only **ONE copy** is retrieved, not three copies
- The chat is stored in `chat_goals` table with 3 rows (one per category)
- When retrieving, `DISTINCT` ensures only one `dialog_id` is returned

**Code Reference**: `ChatCategorizationService.java` lines 300-350: Deduplication logic

## Smart AND Filtering

### Problem

Too many chats can exceed OpenAI's token limit (8000 tokens). Simple OR logic (any matching category) can return hundreds of chats, exceeding token limits.

### Solution: Progressive AND Filtering

The system progressively increases the AND requirement (2+ categories, 3+, etc.) until the conversation fits within token limits.

### Step 1: Try OR Logic (Any Matching Category)

```sql
-- Get chats matching ANY category
SELECT DISTINCT d.id as dialog_id
FROM dialogs d
INNER JOIN chat_goals cg ON d.id = cg.dialog_id
WHERE d.user_id = ?
  AND d.type NOT IN ('group', 'channel', 'supergroup', 'bot')
  AND cg.category_name IN ('dating', 'flirting', 'romance', 'business')
GROUP BY d.id
ORDER BY MAX(cg.relevance_score) DESC
```

**Reference**: `SmartChatSelector.java` lines 63-97: `getChatsWithOR()`

### Step 2: Check Token Count

- Estimate total tokens: `(total_chars / 4) + overhead`
- If > 8000 tokens, proceed to AND filtering
- **Reference**: `SmartChatSelector.java` lines 240-251: `estimateTotalTokens()`

### Step 3: Apply AND Filtering (Progressive)

```sql
-- Get chats matching AT LEAST 2 categories
SELECT d.id as dialog_id, COUNT(DISTINCT cg.category_name) as category_count
FROM dialogs d
INNER JOIN chat_goals cg ON d.id = cg.dialog_id
WHERE d.user_id = ?
  AND d.type NOT IN ('group', 'channel', 'supergroup', 'bot')
  AND cg.category_name IN ('dating', 'flirting', 'romance', 'business')
GROUP BY d.id
HAVING COUNT(DISTINCT cg.category_name) >= 2  -- Start with 2
ORDER BY MAX(cg.relevance_score) DESC, category_count DESC
```

**Reference**: `SmartChatSelector.java` lines 137-174: `getChatsWithAND()`

### Step 4: Increase AND Requirement If Needed

- If still > 8000 tokens, increase to 3 categories
- If still too long, increase to 4 categories
- Continue until conversation fits

**Reference**: `SmartChatSelector.java` lines 103-132: `applyAndFiltering()`

### Example Flow

1. **User wants**: ["dating", "flirting", "romance", "business"]
2. **OR logic**: Gets 50 chats → 15,000 tokens (too much!)
3. **AND 2**: Gets chats matching 2+ categories → 25 chats → 8,000 tokens (still too much)
4. **AND 3**: Gets chats matching 3+ categories → 10 chats → 4,000 tokens (perfect!)
5. **Use these 10 chats** for OpenAI

## Token Management

### Token Estimation

**Formula**: `1 token ≈ 4 characters`

```java
private int estimateTotalTokens(Map<Integer, List<Message>> chats) {
    int totalChars = 0;
    for (List<Message> messages : chats.values()) {
        for (Message msg : messages) {
            if (msg.getContent() != null) {
                totalChars += msg.getContent().length();
            }
        }
    }
    // Add overhead for formatting and structure
    return (totalChars / 4) + (chats.size() * 100);
}
```

**Reference**: `SmartChatSelector.java` lines 240-251

### Token Budget

- **Total limit**: 8000 tokens (conservative for OpenAI)
- **Reserve**: 2000 tokens for prompt + response
- **Available for examples**: 6000 tokens

**Constants**:
- `MAX_TOTAL_TOKENS = 8000`
- `MAX_TOKENS_PER_CHAT = 500`
- `MIN_CATEGORIES_FOR_AND = 2`

**Reference**: `SmartChatSelector.java` lines 18-20

### Smart Truncation

- Use **last 15 messages** from each example (most relevant)
- Stop adding examples if remaining tokens < 1000

**Reference**: `AutomatedConversationManager.java` lines 250-280: `buildEnhancedPrompt()`

## Database Schema

### Tables Used

#### `dialogs` Table

**Reference**: `DatabaseSchema.java` lines 60-75

```sql
CREATE TABLE dialogs (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dialog_id BIGINT NOT NULL,
    name TEXT,
    type TEXT,  -- 'private', 'group', 'channel', 'bot' (filtered)
    is_bot BOOLEAN DEFAULT FALSE,
    message_count INT DEFAULT 0,
    media_count INT DEFAULT 0,
    last_synced TIMESTAMPTZ,
    UNIQUE(user_id, dialog_id)
)
```

#### `chat_goals` Table (Mapping)

**Reference**: `DatabaseSchema.java` lines 48-61

```sql
CREATE TABLE chat_goals (
    id SERIAL PRIMARY KEY,
    dialog_id INT NOT NULL REFERENCES dialogs(id) ON DELETE CASCADE,
    goal_id INT REFERENCES goals(id) ON DELETE SET NULL,
    category_name TEXT NOT NULL REFERENCES chat_categories(category_name),
    relevance_score DOUBLE PRECISION DEFAULT 0.0,
    success_score DOUBLE PRECISION DEFAULT 0.0,  -- Success score per category (0.0-1.0)
    outcome TEXT,  -- Stores outcome type and reason (e.g., "circumstantial_rejection: Person has boyfriend but appreciated the approach")
    categorized_at TIMESTAMPTZ DEFAULT NOW(),  -- Used for incremental categorization
    UNIQUE(dialog_id, category_name)  -- Prevents duplicate mappings
)
```

#### `chat_categories` Table

**Reference**: `DatabaseSchema.java` lines 38-46

```sql
CREATE TABLE chat_categories (
    id SERIAL PRIMARY KEY,
    category_name TEXT UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
)
```

### Indexes

**Reference**: `DatabaseSchema.java` lines 183-227

- `idx_chat_goals_dialog_id` - Fast lookup by dialog
- `idx_chat_goals_category` - Fast lookup by category
- `idx_chat_goals_relevance_score` - Sort by relevance
- `idx_chat_goals_success_score` - Sort by success
- `idx_dialogs_user_id` - Fast lookup by user
- `idx_dialogs_type` - Fast filtering by type

## Example Scenario

### User Goal

- **Target**: Sarah (VC investor)
- **Categories**: ["dating", "flirting", "romance", "business", "investment"]

### Process Flow

1. **Get Relevant Categories**:
   - OpenAI analyzes goal: "Secure investment for AI startup"
   - Returns: ["investment", "business", "networking", "dating", "flirting"]
   - **Code**: `ChatCategorizationService.getRelevantCategories()`
   - **Reference**: `ChatCategorizationService.java` lines 60-90

2. **Find Matching Chats** (with filtering):
   ```sql
   -- Excludes groups, channels, bots
   SELECT DISTINCT d.id
   FROM dialogs d
   INNER JOIN chat_goals cg ON d.id = cg.dialog_id
   WHERE d.type = 'private'  -- Only one-on-one chats
     AND cg.category_name IN ('investment', 'business', 'networking', 'dating', 'flirting')
   ```
   - **Code**: `SmartChatSelector.getFilteredChats()`
   - **Reference**: `SmartChatSelector.java` lines 29-58

3. **Deduplicate**:
   - Chat with "Emily" belongs to: ["dating", "flirting", "romance"]
   - Only ONE instance retrieved (not three)

4. **Apply AND Filtering**:
   - OR: 100 chats → 15,000 tokens (too much!)
   - AND 2: 40 chats → 8,000 tokens (still too much)
   - AND 3: 15 chats → 4,000 tokens (perfect!)
   - Use these 15 chats
   - **Code**: `SmartChatSelector.applyAndFiltering()`
   - **Reference**: `SmartChatSelector.java` lines 103-132

5. **Format for OpenAI**:
   ```
   Example 1:
   You: Hey! I saw your post about AI...
   Emily: Thanks! Are you working on something similar?
   You: Yes, actually...
   ...
   ```
   - **Code**: `SmartChatSelector.formatChatsForPrompt()`
   - **Reference**: `SmartChatSelector.java` lines 309-335

6. **Generate Response**:
   - OpenAI uses these examples + style profile
   - Generates personalized response
   - Humanizes with Undetectable.ai
   - **Code**: `AutomatedConversationManager.processIncomingMessage()`
   - **Reference**: `AutomatedConversationManager.java` lines 120-200

## Incremental Categorization

### Overview

When the application runs again after initial ingestion, it uses **incremental categorization** to optimize API usage and computational time. Instead of re-processing entire chat histories, it only processes **new messages** that arrived since the last categorization.

### How It Works

1. **Get Last Categorization Timestamp**
   - Query: `SELECT MAX(categorized_at) FROM chat_goals WHERE dialog_id = ?`
   - **Reference**: `ChatCategorizationService.java` lines 703-721: `getLastCategorizationTimestamp()`

2. **Load Only New Messages**
   - Query: `SELECT * FROM messages WHERE dialog_id = ? AND timestamp > ?`
   - Only messages after the last categorization timestamp
   - **Reference**: `ChatCategorizationService.java` lines 723-759: `loadNewMessagesForDialog()`

3. **Send Only New Messages to OpenAI**
   - Instead of entire conversation (potentially hundreds of messages)
   - Only new messages (often just a few)
   - **Reference**: `ChatCategorizationService.java` lines 784-806: `categorizeMessagesWithScores()`

4. **Merge with Existing Categories**
   - Get existing categories from database
   - Add new categories that don't exist
   - Update existing categories using smart score merging (see below)
   - **Reference**: `ChatCategorizationService.java` lines 808-860: `mergeCategoryScores()`

### Benefits

- **Reduced Token Usage**: Only send new messages, not entire conversations
- **Lower API Costs**: Fewer tokens = lower costs
- **Faster Processing**: Less data to process
- **Automatic Updates**: New messages automatically trigger categorization

### Python Script Integration

The Python ingestion script (`scripts/telethon/chat_ingestor.py`) also supports incremental updates:

1. **Check Last Message ID**: `get_last_message_id(dialog_id)`
2. **Only Process New Messages**: `iter_messages(dialog.entity, offset_id=last_message_id)`
3. **Skip Already Processed**: Uses `ON CONFLICT DO NOTHING` for messages

**Reference**: `scripts/telethon/chat_ingestor.py` lines 84-90: `get_last_message_id()`
**Reference**: `scripts/telethon/chat_ingestor.py` lines 237-301: Incremental ingestion logic

## Smart Score Merging

### Overview

When a category already exists in the database and new messages arrive, the system uses **smart score merging** to intelligently combine old and new scores. The formula considers:

1. **Message Count Ratio**: How many old messages vs new messages
2. **Engagement Metrics**: Reply ratio, response timing, engagement level
3. **Data Reliability**: More messages = more reliable scores

### Formula Breakdown

The merging formula balances **data volume** (message count) and **data quality** (engagement):

```java
// Step 1: Calculate message ratio weight
messageRatioWeight = oldMessageCount / totalMessages
messageCountWeight = (messageRatioWeight)^2  // Squared to emphasize differences

// Step 2: Calculate engagement-based weight (from new messages)
engagementBasedNewWeight = (engagementLevel * 0.4) + 
                          (replyRatio * 0.3) + 
                          (replyTimeWeight * 0.3)

// Step 3: Combined weight (60% message count, 40% engagement)
finalNewScoreWeight = (engagementBasedNewWeight * 0.4) + 
                     ((1.0 - messageCountWeight) * 0.6)
finalOldScoreWeight = 1.0 - finalNewScoreWeight

// Step 4: Additional adjustment for large disparities
if (oldToNewRatio > 3.0) {
    reductionFactor = min(0.3, (oldToNewRatio - 3.0) / 10.0)
    finalNewScoreWeight *= (1.0 - reductionFactor)
}

// Step 5: Weighted average merge
mergedScore = (oldScore * finalOldScoreWeight) + (newScore * finalNewScoreWeight)
```

**Reference**: `ChatCategorizationService.java` lines 936-1009: `mergeScoresIntelligently()`

### Example Calculations

#### Scenario 1: 50 Old Messages, 10 New Messages (High Engagement)

**Inputs:**
- Old score: 0.75 (75%)
- New score: 0.80 (80%)
- Old messages: 50
- New messages: 10
- Engagement level: 0.9
- Reply ratio: 1.2
- Avg reply time: 90 seconds

**Calculation:**
```
messageRatioWeight = 50 / 60 = 0.83
messageCountWeight = 0.83² = 0.69
engagementBasedNewWeight = (0.9 * 0.4) + (1.2 * 0.3) + (0.92 * 0.3) = 0.98
finalNewScoreWeight = (0.98 * 0.4) + ((1 - 0.69) * 0.6) = 0.392 + 0.186 = 0.578

// Additional adjustment (5x ratio)
oldToNewRatio = 50 / 10 = 5.0
reductionFactor = min(0.3, (5.0 - 3.0) / 10.0) = 0.2
finalNewScoreWeight *= (1 - 0.2) = 0.578 * 0.8 = 0.462

finalOldScoreWeight = 1 - 0.462 = 0.538
mergedScore = (0.75 * 0.538) + (0.80 * 0.462) = 0.404 + 0.370 = 0.774 (77.4%)
```

**Result**: Old score gets **53.8%** weight, new gets **46.2%** (properly favors old data despite high engagement)

#### Scenario 2: 10 Old Messages, 50 New Messages (High Engagement)

**Inputs:**
- Old messages: 10
- New messages: 50
- Engagement level: 0.9

**Calculation:**
```
messageRatioWeight = 10 / 60 = 0.17
messageCountWeight = 0.17² = 0.03
finalNewScoreWeight = (0.98 * 0.4) + ((1 - 0.03) * 0.6) = 0.392 + 0.582 = 0.974
finalOldScoreWeight = 1 - 0.974 = 0.026
mergedScore = (oldScore * 0.026) + (newScore * 0.974)
```

**Result**: New score gets **97.4%** weight (correctly trusts new data when it's more substantial)

### Engagement Metrics

The formula considers three engagement metrics:

1. **Engagement Level** (40% weight): Percentage of substantial messages
   - Based on message length, questions asked, vs. just "ok", "yes"
   - **Reference**: `ChatCategorizationService.java` lines 904-933: `calculateEngagementLevel()`

2. **Reply Ratio** (30% weight): How often the other person replies
   - Formula: `(other person's messages) / (user's messages)`
   - Higher ratio = more responsive
   - **Reference**: `ChatCategorizationService.java` lines 870-880: `calculateEngagementMetrics()`

3. **Average Reply Time** (30% weight): How long before the other person replies
   - Faster replies = higher engagement
   - Normalized: `1.0 - min(0.5, (avgReplyTime - 60) / 3600)`
   - **Reference**: `ChatCategorizationService.java` lines 882-902: `calculateAverageReplyTime()`

### Key Principles

1. **Data Volume Matters (60% weight)**: More messages = more reliable scores
2. **Data Quality Matters (40% weight)**: Higher engagement = trust new score more
3. **Squared Ratio**: Emphasizes large differences (50 vs 10 messages)
4. **Additional Adjustment**: Reduces new weight if old messages are 3x+ more
5. **Preserves Existing Categories**: Don't remove categories that weren't in new categorization

### Outcome Type Preservation

When merging, outcome types are preserved:
- If new score has outcome type → use it
- If not → keep old outcome type
- **Reference**: `ChatCategorizationService.java` lines 1002-1006: Outcome type merging

## Configuration

### Token Limits

**Location**: `src/main/java/com/aria/analysis/SmartChatSelector.java` lines 18-20

```java
private static final int MAX_TOKENS_PER_CHAT = 500;  // Approximate tokens per chat
private static final int MAX_TOTAL_TOKENS = 8000;    // Conservative limit for OpenAI
private static final int MIN_CATEGORIES_FOR_AND = 2; // Start with 2 categories AND
```

### Adjusting Limits

Edit `SmartChatSelector.java`:

```java
// Increase for longer conversations
private static final int MAX_TOTAL_TOKENS = 10000;

// Start with 3 categories instead of 2
private static final int MIN_CATEGORIES_FOR_AND = 3;
```

## Code Flow

```
User defines goal
    ↓
Get relevant categories (OpenAI)
    ↓
Get chats by categories (SmartChatSelector)
    ├─ Filter groups/channels/bots
    ├─ Deduplicate by dialog_id
    ├─ Estimate tokens
    ├─ If too long: Apply AND filtering
    │   ├─ Try 2 categories
    │   ├─ Try 3 categories
    │   └─ Continue until fits
    └─ Limit to top N chats
    ↓
Format chats for OpenAI prompt
    ├─ Token-aware truncation
    └─ Use last 15 messages per chat
    ↓
Send to OpenAI
    ↓
Generate response
    ↓
Humanize with Undetectable.ai
    ↓
Return to user
```

### Key Methods

1. **`ChatCategorizationService.getRelevantCategories()`** - Get categories for goal
   - **Reference**: `ChatCategorizationService.java` lines 60-90

2. **`ChatCategorizationService.categorizeChatWithScores()`** - Initial categorization with success scores
   - **Reference**: `ChatCategorizationService.java` lines 47-74

3. **`ChatCategorizationService.recategorizeDialog()`** - Incremental categorization (only new messages)
   - **Reference**: `ChatCategorizationService.java` lines 630-660

4. **`ChatCategorizationService.mergeScoresIntelligently()`** - Smart score merging formula
   - **Reference**: `ChatCategorizationService.java` lines 936-1009

5. **`SmartChatSelector.getFilteredChats()`** - Main filtering method
   - **Reference**: `SmartChatSelector.java` lines 29-58

6. **`SmartChatSelector.applyAndFiltering()`** - Progressive AND filtering
   - **Reference**: `SmartChatSelector.java` lines 103-132

7. **`SmartChatSelector.estimateTotalTokens()`** - Token estimation
   - **Reference**: `SmartChatSelector.java` lines 240-251

8. **`AutomatedConversationManager.getSuccessScoresFromDatabase()`** - Get stored success scores (optimized)
   - **Reference**: `AutomatedConversationManager.java` lines 362-403

9. **`AutomatedConversationManager.buildEnhancedPrompt()`** - Build OpenAI prompt
   - **Reference**: `AutomatedConversationManager.java` lines 250-280

---

**Next**: [Analysis Services Documentation](ANALYSIS_SERVICES.md) | [Storage Documentation](STORAGE.md)

