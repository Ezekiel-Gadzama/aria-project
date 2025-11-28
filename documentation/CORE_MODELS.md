# Core Data Models

This document describes all data models used in the ARIA application, their structure, relationships, and usage patterns.

**Related Documentation**: [Database Schema](STORAGE.md) | [Architecture](ARCHITECTURE.md)

## Table of Contents

1. [Overview](#overview)
2. [Message Model](#message-model)
3. [ChatProfile Model](#chatprofile-model)
4. [ChatCategory Enum](#chatcategory-enum)
5. [OutcomeType Enum](#outcometype-enum)
6. [ConversationGoal Model](#conversationgoal-model)
7. [TargetUser Model](#targetuser-model)
8. [User Model](#user-model)
9. [CommunicationStyle Model](#communicationstyle-model)
10. [Model Relationships](#model-relationships)

## Overview

The ARIA application uses several core data models to represent:
- **Messages**: Individual chat messages
- **Chat Profiles**: Communication style profiles extracted from conversations
- **Chat Categories**: 500+ predefined categories for chat classification
- **Conversation Goals**: User-defined goals for conversations
- **Users**: Application users and target users
- **Communication Styles**: Analyzed communication patterns

**Location**: `src/main/java/com/aria/core/model/`

## Message Model

**Location**: `src/main/java/com/aria/core/model/Message.java` (lines 1-87)

### Purpose

Represents an individual message in a conversation. Stores message content, sender information, timestamp, and media indicators.

### Structure

```java
public class Message {
    private int id;                    // Database ID
    private String sender;             // Sender name ("me" or contact name)
    private String content;            // Message text content
    private LocalDateTime timestamp;   // When the message was sent
    private boolean isFromUser;        // true if from user, false if from contact
    private MessageType type;          // TEXT, IMAGE, LINK, EMOJI
    private boolean hasMedia;          // true if message has media (photo, video, file)
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | `int` | Database primary key |
| `sender` | `String` | Sender name or "me" |
| `content` | `String` | Message text (null if media-only) |
| `timestamp` | `LocalDateTime` | Message timestamp |
| `isFromUser` | `boolean` | true if sent by user |
| `type` | `MessageType` | Message type enum |
| `hasMedia` | `boolean` | Indicates if media exists |

### MessageType Enum

**Reference**: `Message.java` lines 16-18

```java
public enum MessageType {
    TEXT,   // Text-only message
    IMAGE,  // Image message
    LINK,   // Link message
    EMOJI   // Emoji-only message
}
```

### Usage Example

```java
Message msg = new Message("Hello!", "Alex", false);
msg.setTimestamp(LocalDateTime.now());
msg.setHasMedia(false);
msg.setType(MessageType.TEXT);
```

### Database Mapping

- **Table**: `messages`
- **Primary Key**: `id`
- **Foreign Key**: `dialog_id` → `dialogs.id`
- **Media Link**: If `has_media = true`, query `media` table with `message_id`

**Reference**: [Storage Documentation](STORAGE.md#messages-table)

### Key Implementation Notes

1. **Content Field**: Stores text content. Can be `null` if message is media-only
2. **Media Handling**: `hasMedia` flag indicates if media exists. Actual file paths stored in `media` table
3. **Sender Identification**: Uses `isFromUser` boolean for user/contact distinction
4. **Type Detection**: `MessageType` determined during ingestion from platform

**Reference**: `Message.java` lines 23-29: Constructor

## ChatProfile Model

**Location**: `src/main/java/com/aria/core/model/ChatProfile.java` (lines 1-72)

### Purpose

Represents a communication style profile extracted from a conversation. Used for weighted response synthesis (70%/15%/15%).

### Structure

```java
public class ChatProfile {
    private double humorLevel;              // 0.0 - 1.0
    private double formalityLevel;          // 0.0 - 1.0
    private double empathyLevel;            // 0.0 - 1.0
    private String preferredOpening;        // Preferred opening line
    private double responseTimeAverage;     // Average response time (seconds)
    private double messageLengthAverage;    // Average message length (characters)
    private double engagementLevel;         // 0.0 - 1.0
    private double questionRate;            // 0.0 - 1.0 (percentage of messages that are questions)
    private double successScore;            // 0.0 - 1.0 (calculated success score)
}
```

### Fields

| Field | Type | Range | Description |
|-------|------|-------|-------------|
| `humorLevel` | `double` | 0.0 - 1.0 | Amount of humor used |
| `formalityLevel` | `double` | 0.0 - 1.0 | Formality of language |
| `empathyLevel` | `double` | 0.0 - 1.0 | Empathy shown in messages |
| `preferredOpening` | `String` | - | Most common opening line |
| `responseTimeAverage` | `double` | ≥ 0.0 | Average response delay (seconds) |
| `messageLengthAverage` | `double` | ≥ 0.0 | Average message length (chars) |
| `engagementLevel` | `double` | 0.0 - 1.0 | Overall engagement level |
| `questionRate` | `double` | 0.0 - 1.0 | Rate of questions asked |
| `successScore` | `double` | 0.0 - 1.0 | Calculated success score |

### Default Values

**Reference**: `ChatProfile.java` lines 15-25

```java
public ChatProfile() {
    this.humorLevel = 0.5;
    this.formalityLevel = 0.5;
    this.empathyLevel = 0.5;
    this.preferredOpening = "Hi there!";
    this.responseTimeAverage = 60.0;
    this.messageLengthAverage = 20.0;
    this.engagementLevel = 0.5;
    this.questionRate = 0.3;
}
```

### Value Constraints

All setters clamp values to valid ranges:

```java
public void setHumorLevel(double humorLevel) {
    this.humorLevel = Math.max(0.0, Math.min(1.0, humorLevel));
}
```

**Reference**: `ChatProfile.java` lines 28-71: Getters and setters

### Usage

**Extraction**: `StyleExtractor.extractStyleProfile(List<Message>)`
**Synthesis**: `WeightedResponseSynthesis.synthesizeProfile(Map<String, List<Message>>, String)`

**Reference**: 
- `StyleExtractor.java`: Style extraction
- `WeightedResponseSynthesis.java`: Profile synthesis

### Database Mapping

- **Table**: `style_profiles`
- **Linked to**: `dialogs` via `dialog_id`
- **Stored when**: Chat analysis is performed

**Reference**: [Storage Documentation](STORAGE.md#style_profiles-table)

## ChatCategory Enum

**Location**: `src/main/java/com/aria/core/model/ChatCategory.java` (lines 1-1430)

### Purpose

Enumeration of 500+ predefined chat categories. Each category includes a name, description, and keywords to help OpenAI categorize chats accurately.

### Structure

```java
public enum ChatCategory {
    DATING(
        "dating",
        "Conversations about going on dates...",
        new String[]{"date", "go out", "coffee", "dinner", "movie", "together", "romantic interest"}
    ),
    FLIRTING(
        "flirting",
        "Playful, suggestive, or teasing conversations...",
        new String[]{"cute", "hot", "beautiful", "handsome", "flirting", "teasing", "wink", "kiss"}
    ),
    // ... 500+ more categories
}
```

**Reference**: `ChatCategory.java` lines 12-1430

### Category Information

Each category contains:
- **Name**: Exact identifier (e.g., "dating", "romance", "investment")
- **Description**: When a chat belongs to this category
- **Keywords**: Common terms associated with the category

### Category Domains

Categories cover:
- **Romantic/Dating**: dating, flirting, romance, relationship, etc.
- **Business/Professional**: business, investment, sponsorship, networking, etc.
- **Academic/Educational**: academic, studies, educational, etc.
- **Social/Friendship**: friendship, social, casual, etc.
- **Emotional**: support, encouragement, sympathy, etc.
- **Activity**: travel, sports, hobbies, entertainment, etc.
- **And 400+ more categories**

**Reference**: `ChatCategory.java` lines 13-1430: All categories

### Static Methods

**Reference**: `ChatCategory.java` lines 1400-1430

- `getName()`: Returns category name
- `getDescription()`: Returns category description
- `getKeywords()`: Returns category keywords
- `getAllNames()`: Returns all category names as List
- `formatForOpenAI()`: Formats all categories for OpenAI prompt

### Database Sync

Categories are synced to `chat_categories` table on startup:
- **Method**: `DatabaseSchema.insertDefaultCategories()`
- **Table**: `chat_categories`
- **Columns**: `category_name`, `description`

**Reference**: 
- `DatabaseSchema.java` lines 162-180
- `ChatCategory.java`: All category definitions

## OutcomeType Enum

**Location**: `src/main/java/com/aria/core/model/OutcomeType.java` (lines 1-93)

### Purpose

Enumeration representing the outcome type of a conversation for a specific category. Used to understand why a conversation succeeded or failed, distinguishing between circumstantial rejections (high success score) and approach failures (low success score).

### Structure

```java
public enum OutcomeType {
    SUCCESS("success", "Goal was successfully achieved"),
    PARTIAL_SUCCESS("partial_success", "Partial success or progress made"),
    CIRCUMSTANTIAL_REJECTION("circumstantial_rejection", 
        "Rejection due to circumstances, not approach - communication was effective"),
    APPROACH_REJECTION("approach_rejection", 
        "Rejection due to poor approach - the communication method itself was ineffective"),
    NEUTRAL("neutral", "Neutral outcome - no clear success or failure");
}
```

**Reference**: `OutcomeType.java` lines 9-34

### Enum Values

| Value | Name | Description | Expected Success Score |
|-------|------|-------------|----------------------|
| `SUCCESS` | "success" | Goal was successfully achieved | 70-100% |
| `PARTIAL_SUCCESS` | "partial_success" | Partial success or progress made | 40-69% |
| `CIRCUMSTANTIAL_REJECTION` | "circumstantial_rejection" | Rejection due to circumstances, not approach | 70-85% (high) |
| `APPROACH_REJECTION` | "approach_rejection" | Rejection due to poor approach | 0-39% (low) |
| `NEUTRAL` | "neutral" | No clear outcome | 40-60% |

### Key Concept: Circumstantial vs Approach Rejection

The `OutcomeType` enum helps distinguish between:

- **Circumstantial Rejection (High Score)**: The communication was effective, but external factors prevented success.
  - Example: "I have a boyfriend, but you seem really nice!" → Success score: 75-85%
  - Example: "Not enough funds now, but let's revisit in Q2" → Success score: 70-80%

- **Approach Rejection (Low Score)**: The communication method itself was the problem.
  - Example: "You're weird", "Leave me alone" → Success score: 10-30%
  - Example: "Not interested" (dismissive) → Success score: 20-40%

### Static Methods

**Reference**: `OutcomeType.java` lines 48-93

- `fromName(String name)`: Parse outcome type from string (case-insensitive)
- `formatForOpenAI()`: Format all outcome types for OpenAI prompt

```java
OutcomeType type = OutcomeType.fromName("circumstantial_rejection");
String promptFormat = OutcomeType.formatForOpenAI();
```

### Database Storage

Outcome types are stored in the `chat_goals` table:
- **Column**: `outcome` (TEXT)
- **Format**: `"{outcome_type}: {reason}"` or just `"{reason}"`
- **Example**: `"circumstantial_rejection: Person has boyfriend but appreciated the approach"`

**Reference**: 
- `ChatCategorizationService.java` lines 528-529: Saving outcome info
- `ChatCategorizationService.java` lines 693-708: Parsing outcome from database

### Usage

Outcome types are used by:
1. **Success Scoring**: OpenAI categorizes conversations and assigns outcome types
2. **Smart Score Merging**: Outcome types help weight new scores appropriately
3. **Chat Selection**: 70/15/15 selection uses outcome types to identify successful vs failed chats

**Reference**: 
- `ChatCategorizationService.java` lines 249-317: Prompt building with outcome types
- `ChatCategorizationService.java` lines 489-491: Parsing outcome types from OpenAI response

## ConversationGoal Model

**Location**: `src/main/java/com/aria/core/model/ConversationGoal.java` (lines 1-43)

### Purpose

Represents a user-defined goal for a conversation with a target user. Contains context, desired outcome, and meeting information.

### Structure

```java
public class ConversationGoal {
    private String context;           // General context
    private String desiredOutcome;    // What the user wants to achieve
    private String meetingContext;    // How they met, background info
}
```

**Reference**: `ConversationGoal.java` lines 1-43

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `context` | `String` | General conversation context |
| `desiredOutcome` | `String` | What the user wants to achieve (e.g., "Secure investment") |
| `meetingContext` | `String` | How they met, background info |

### Usage Example

```java
ConversationGoal goal = new ConversationGoal(
    "Tech conference networking",
    "Secure investment for AI startup",
    "Met at TechCrunch Disrupt, she works in VC, showed interest in AI"
);
```

**Reference**: `ConversationGoal.java` lines 13-17: Constructor

### Database Mapping

- **Table**: `goals`
- **Foreign Keys**: `user_id` → `users.id`, `target_user_id` → `target_users.id`
- **Status**: `active`, `completed`, `failed`

**Reference**: [Storage Documentation](STORAGE.md#goals-table)

### Related Services

- **Category Identification**: `ChatCategorizationService.getRelevantCategories()`
- **Chat Retrieval**: `SmartChatSelector.getFilteredChats()`
- **Response Generation**: `ResponseGenerator` uses goal for context

## TargetUser Model (Parent Entity)

**Location**: `src/main/java/com/aria/core/model/TargetUser.java`

### Purpose

Represents a Target User (parent entity) - platform-agnostic person information. This holds global information about the person, while SubTargetUser holds platform-specific instances.

### Structure

```java
public class TargetUser {
    private int targetId;
    private int userId;                      // Owner of this target
    private String name;                     // Person's name
    private String bio;                     // Bio/description
    private String desiredOutcome;          // What the user wants to achieve
    private String meetingContext;          // Where/How You Met
    private String importantDetails;        // Optional important details
    private boolean crossPlatformContextEnabled; // Toggle for cross-platform context
    private String profileJson;             // JSON profile data
    private String profilePictureUrl;        // Profile picture URL
    private List<SubTargetUser> subTargetUsers; // Child entities (platform-specific)
    private ConversationGoal conversationGoal;
    
    // Legacy fields for backward compatibility
    private List<UserPlatform> platforms;
    private int selectedPlatformIndex;
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `targetId` | `int` | Database primary key |
| `userId` | `int` | Owner of this target |
| `name` | `String` | Person's name |
| `bio` | `String` | Bio/description |
| `desiredOutcome` | `String` | What the user wants to achieve |
| `meetingContext` | `String` | Where/How You Met |
| `importantDetails` | `String` | Optional important details |
| `crossPlatformContextEnabled` | `boolean` | Toggle for cross-platform context aggregation |
| `profileJson` | `String` | JSON profile data |
| `profilePictureUrl` | `String` | Profile picture URL |
| `subTargetUsers` | `List<SubTargetUser>` | Child entities (platform-specific instances) |

### Key Methods

- `addSubTargetUser(SubTargetUser)` - Add a SubTarget User
- `findSubTargetByPlatform(Platform, Integer)` - Find SubTarget by platform and account
- `getSubTargetUsers()` - Get all SubTarget Users

### Database Mapping

- **Table**: `target_users`
- **Foreign Key**: `user_id` → `users.id`
- **Unique**: `(user_id, name)`
- **Relationships**: One-to-many with `subtarget_users`

**Reference**: [Storage Documentation](STORAGE.md#target_users-table)

## SubTargetUser Model (Child Entity)

**Location**: `src/main/java/com/aria/core/model/SubTargetUser.java`

### Purpose

Represents a platform-specific instance of a Target User. This is the child entity that holds platform-specific information. Conversations are started at the SubTarget User level.

### Structure

```java
public class SubTargetUser {
    private int id;
    private int targetUserId;              // Parent Target User ID
    private String name;                   // Platform-specific name/nickname
    private String username;               // Platform username
    private Platform platform;             // Platform enum (TELEGRAM, etc.)
    private Integer platformAccountId;     // Reference to platform_accounts
    private Long platformId;               // Platform-specific ID (e.g., Telegram user ID)
    private String number;                 // Phone number if applicable
    private String advancedCommunicationSettings; // JSON string for advanced settings
    private LocalDateTime createdAt;
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | `int` | Database primary key |
| `targetUserId` | `int` | Parent Target User ID |
| `name` | `String` | Platform-specific name/nickname |
| `username` | `String` | Platform username |
| `platform` | `Platform` | Platform enum (TELEGRAM, etc.) |
| `platformAccountId` | `Integer` | Reference to platform_accounts table |
| `platformId` | `Long` | Platform-specific ID (e.g., Telegram user ID) |
| `number` | `String` | Phone number if applicable |
| `advancedCommunicationSettings` | `String` | JSON string for advanced settings |

### Database Mapping

- **Table**: `subtarget_users`
- **Foreign Key**: `target_user_id` → `target_users.id` ON DELETE CASCADE
- **Foreign Key**: `platform_account_id` → `platform_accounts.id` ON DELETE SET NULL
- **Unique**: `(target_user_id, platform, platform_account_id, platform_id)`

**Reference**: [Storage Documentation](STORAGE.md#subtarget_users-table)

### Usage

- Conversations are started at the SubTarget User level
- Each SubTarget User represents the same person on a different platform/account
- Cross-platform context can aggregate chat history from all SubTarget Users of a Target User

## User Model

**Location**: `src/main/java/com/aria/core/model/User.java` (lines 1-76)

### Purpose

Represents an ARIA application user. Stores user profile information and authentication details.

### Structure

```java
public class User {
    private int id;
    private String username;
    private String email;
    private String phoneNumber;
    // ... other fields
}
```

**Reference**: `User.java` lines 1-76

### Database Mapping

- **Table**: `users`
- **Primary Key**: `id`
- **Relationships**: One-to-many with `dialogs`, `goals`, `target_users`

**Reference**: [Storage Documentation](STORAGE.md#users-table)

## CommunicationStyle Model

**Location**: `src/main/java/com/aria/core/model/CommunicationStyle.java`

### Purpose

Represents a communication style pattern. Similar to `ChatProfile` but used for different purposes.

### Usage

Used internally for style analysis and comparison.

## TargetGroup and TargetChannel Models

### TargetGroup Model (Parent Entity)

**Location**: `src/main/java/com/aria/core/model/TargetGroup.java`

Represents a parent Target Group for communal spaces. Has a hierarchical structure with SubTarget Groups.

### SubTargetGroup Model (Child Entity)

**Location**: `src/main/java/com/aria/core/model/SubTargetGroup.java`

Represents a platform-specific instance of a Target Group. Only explicitly defined groups are ingested.

### TargetChannel Model (Parent Entity)

**Location**: `src/main/java/com/aria/core/model/TargetChannel.java`

Represents a parent Target Channel for communal spaces. Has a hierarchical structure with SubTarget Channels.

### SubTargetChannel Model (Child Entity)

**Location**: `src/main/java/com/aria/core/model/SubTargetChannel.java`

Represents a platform-specific instance of a Target Channel. Only explicitly defined channels are ingested.

### Usage

- Groups/channels are used for trend analysis, admin oversight, and knowledge base
- Messages from groups/channels include `message_link` for reference
- **Never used for 1-on-1 reply generation**

## Model Relationships

### Entity Relationship Diagram

```
users
  ├── dialogs (one-to-many)
  │   ├── messages (one-to-many)
  │   │   └── pinned (boolean)
  │   │   └── message_link (for groups/channels)
  │   ├── chat_goals (one-to-many)
  │   └── style_profiles (one-to-one)
  ├── target_users (one-to-many) [PARENT]
  │   ├── subtarget_users (one-to-many) [CHILD]
  │   │   └── conversations (one-to-many)
  │   │       └── subtarget_user_id (foreign key)
  │   └── goals (one-to-many)
  │       └── conversation_states (one-to-many)
  ├── target_groups (one-to-many) [PARENT]
  │   └── subtarget_groups (one-to-many) [CHILD]
  ├── target_channels (one-to-many) [PARENT]
  │   └── subtarget_channels (one-to-many) [CHILD]
  └── platform_accounts (one-to-many)
```

### Key Relationships

1. **User → Dialogs**: One user has many chat dialogs
2. **Dialog → Messages**: One dialog has many messages (with `pinned` and `message_link` fields)
3. **Dialog → ChatGoals**: One dialog can belong to multiple categories
4. **Dialog → StyleProfile**: One dialog has one style profile (after analysis)
5. **User → TargetUsers**: One user has many target users (parent entities)
6. **TargetUser → SubTargetUsers**: One target user has many subtarget users (child entities) - **HIERARCHICAL**
7. **SubTargetUser → Conversations**: Conversations are started at SubTarget User level
8. **Conversation → SubTargetUser**: Each conversation is linked to a SubTarget User via `subtarget_user_id`
9. **TargetUser → Goals**: One target user can have multiple goals
10. **Goal → ConversationStates**: One goal can have multiple conversation states
11. **User → TargetGroups**: One user has many target groups (parent entities)
12. **TargetGroup → SubTargetGroups**: One target group has many subtarget groups (child entities) - **HIERARCHICAL**
13. **User → TargetChannels**: One user has many target channels (parent entities)
14. **TargetChannel → SubTargetChannels**: One target channel has many subtarget channels (child entities) - **HIERARCHICAL**

### Database Schema

For complete database schema, see [Storage Documentation](STORAGE.md).

## Model Validation

### Message Validation

- `content` can be null (for media-only messages)
- `hasMedia` must be `true` if media exists
- `timestamp` must not be null
- `sender` must not be null

### ChatProfile Validation

- All level fields (0.0 - 1.0): Clamped in setters
- Time/length fields (≥ 0.0): Enforced in setters
- `preferredOpening` can be null/empty

### ConversationGoal Validation

- `desiredOutcome` should not be empty
- `context` and `meetingContext` can be null

## Usage Patterns

### Creating Models

```java
// Message
Message msg = new Message("Hello!", "Alex", false);
msg.setTimestamp(LocalDateTime.now());

// ConversationGoal
ConversationGoal goal = new ConversationGoal(
    "Context",
    "Desired outcome",
    "Meeting context"
);

// ChatProfile
ChatProfile profile = new ChatProfile();
profile.setHumorLevel(0.7);
profile.setFormalityLevel(0.3);
```

### Loading from Database

**Reference**: `DatabaseManager.java` for database loading methods:
- `loadMessagesForDialog(int dialogId)`
- `loadConversationGoal(int goalId)`
- `loadStyleProfile(int dialogId)`

## Serialization

### JSON Support

Models can be serialized to JSON using Gson:
- `Message`: Serialized for OpenAI API
- `ConversationGoal`: Stored in JSONB columns
- `ChatProfile`: Serialized for storage

**Reference**: `pom.xml` for Gson dependency

---

**Next**: [Database Schema](STORAGE.md) | [Analysis Services](ANALYSIS_SERVICES.md)

