# ARIA Documentation Index

Welcome to the ARIA (Automated Relationship & Interaction Assistant) code documentation. This documentation provides a comprehensive guide to the codebase, explaining implementation details, design decisions, and code references.

## Documentation Structure

### 1. [Architecture Overview](ARCHITECTURE.md)
- System architecture and design patterns
- Component interactions and data flow
- Technology stack and dependencies
- Module organization

### 2. [Core Models](CORE_MODELS.md)
- Data models and entities
- `Message`, `ChatProfile`, `ChatCategory`, `OutcomeType`, `ConversationGoal`, etc.
- Model relationships and usage patterns
- Reference: `src/main/java/com/aria/core/model/`

### 3. [Database & Storage](STORAGE.md)
- Database schema and table definitions
- Indexing strategy for optimal performance
- Database operations and queries
- Reference: `src/main/java/com/aria/storage/`

### 4. [AI Services](AI_SERVICES.md)
- OpenAI integration for text generation and categorization
- Undetectable.ai integration for humanization
- Response generation and conversation summarization
- Quiz generation
- Reference: `src/main/java/com/aria/ai/`

### 5. [Analysis Services](ANALYSIS_SERVICES.md)
- Chat categorization using OpenAI
- Incremental categorization (only new messages)
- Smart score merging with message count ratio
- Communication style extraction
- Success scoring with contextual understanding (circumstantial vs approach rejection)
- Disinterest detection
- Response timing analysis
- Smart chat selection with progressive AND filtering
- Reference: `src/main/java/com/aria/analysis/`

### 6. [Categorization Logic](CATEGORIZATION_LOGIC.md)
- 500+ chat category system
- Deduplication across categories
- Filtering groups, channels, and bots
- Progressive AND filtering for token management
- Incremental categorization (only new messages)
- Smart score merging formula (message count ratio + engagement metrics)
- Reference: `src/main/java/com/aria/core/model/ChatCategory.java`

### 7. [Response Strategy](RESPONSE_STRATEGY.md)
- Weighted response synthesis (70%/15%/15%)
- Category A (successful), Category B (failed), Category C (base)
- Profile blending algorithms
- Strategy pattern implementation
- Reference: `src/main/java/com/aria/core/strategy/`

### 8. [Platform Integration](PLATFORM_INTEGRATION.md)
- Telegram integration via Telethon (Python)
- Java-Python bridge implementation
- Platform abstraction layer
- Chat ingestion from platforms
- Reference: `src/main/java/com/aria/platform/` and `scripts/telethon/`

### 9. [UI Components](UI_COMPONENTS.md)
- JavaFX user interface controllers
- User registration and authentication
- Target user management
- Conversation monitoring and control
- Reference: `src/main/java/com/aria/ui/`

### 10. [Automated Conversation Management](AUTOMATED_CONVERSATION.md)
- Conversation orchestration and state management
- Incoming message processing
- Response generation and scheduling
- Disinterest detection and alerts
- Human-in-the-loop features
- Reference: `src/main/java/com/aria/core/AutomatedConversationManager.java`

## Quick Links

### Key Files by Functionality

**Core Orchestration:**
- `src/main/java/com/aria/core/AutomatedConversationManager.java` - Main conversation manager
- `src/main/java/com/aria/core/AriaOrchestrator.java` - High-level orchestration
- `src/main/java/com/aria/Main.java` - Application entry point

**Data Models:**
- `src/main/java/com/aria/core/model/ChatCategory.java` - 500+ categories enum (lines 1-1430)
- `src/main/java/com/aria/core/model/OutcomeType.java` - Outcome type enum (circumstantial_rejection, approach_rejection, etc.)
- `src/main/java/com/aria/core/model/Message.java` - Message data model
- `src/main/java/com/aria/core/model/ChatProfile.java` - Communication style profile

**Analysis:**
- `src/main/java/com/aria/analysis/ChatCategorizationService.java` - Categorization service
- `src/main/java/com/aria/analysis/SmartChatSelector.java` - Smart chat filtering
- `src/main/java/com/aria/analysis/StyleExtractor.java` - Style extraction

**AI Integration:**
- `src/main/java/com/aria/ai/OpenAIClient.java` - OpenAI API client
- `src/main/java/com/aria/ai/UndetectableAIClient.java` - Undetectable.ai client

**Database:**
- `src/main/java/com/aria/storage/DatabaseSchema.java` - Schema definition
- `src/main/java/com/aria/storage/DatabaseManager.java` - Database operations

**Platform:**
- `src/main/java/com/aria/platform/telegram/TelegramConnector.java` - Telegram connector
- `scripts/telethon/chat_ingestor.py` - Python chat ingestion script

## How to Use This Documentation

1. **Start with Architecture**: Understand the overall system design before diving into specifics
2. **Follow Data Flow**: Use the documentation to trace data from platform ingestion → categorization → analysis → response generation
3. **Reference Code**: Each section includes references to specific files and line numbers
4. **Cross-Reference**: Use links between documents to understand how components interact

## Documentation Standards

- **Code References**: Format: `[File Name](relative/path/to/file.java#L123)` - Links to specific files with line numbers when available
- **Inline Code**: Code snippets are formatted with language-specific highlighting
- **Diagrams**: ASCII diagrams are used to illustrate flow and relationships
- **Examples**: Real examples from the codebase are included where helpful

## Contributing to Documentation

When adding new features or modifying existing code:

1. Update relevant documentation files
2. Add code references with line numbers
3. Include examples of usage
4. Cross-reference related documentation
5. Update this index if adding new sections

---

*Last Updated: 2024-01-16*

