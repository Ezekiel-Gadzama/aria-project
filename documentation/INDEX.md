# ARIA Documentation Index

Welcome to the ARIA (Automated Relationship & Interaction Assistant) code documentation. This documentation provides a comprehensive guide to the codebase, explaining implementation details, design decisions, and code references.

## Documentation Structure

### 1. [Architecture Overview](ARCHITECTURE.md)
- System architecture and design patterns
- REST API structure (Spring Boot)
- React frontend architecture
- Component interactions and data flow
- Technology stack and dependencies
- Module organization

### 2. [Core Models](CORE_MODELS.md)
- Data models and entities
- Hierarchical Target-SubTarget model
- Target Groups and Channels
- `Message`, `TargetUser`, `SubTargetUser`, `ChatProfile`, `ChatCategory`, `OutcomeType`, `ConversationGoal`, etc.
- Model relationships and usage patterns
- Reference: `src/main/java/com/aria/core/model/`

### 3. [Database & Storage](STORAGE.md)
- Database schema and table definitions
- Hierarchical tables (target_users, subtarget_users, target_groups, subtarget_groups, target_channels, subtarget_channels)
- Indexing strategy for optimal performance
- Database operations and queries
- Reference: `src/main/java/com/aria/storage/`

### 4. [AI Services](AI_SERVICES.md)
- OpenAI integration for text generation and categorization
- Undetectable.ai integration for humanization
- Response generation and conversation summarization
- Quiz generation
- Cross-platform context handling
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
- Filtering groups, channels, and bots (with exceptions for Target Groups/Channels)
- Progressive AND filtering for token management
- Incremental categorization (only new messages)
- Smart score merging formula (message count ratio + engagement metrics)
- Reference: `src/main/java/com/aria/core/model/ChatCategory.java`

### 7. [Response Strategy](RESPONSE_STRATEGY.md)
- Weighted response synthesis (70%/15%/15%)
- Category A (successful), Category B (failed), Category C (base)
- Profile blending algorithms
- Strategy pattern implementation
- Cross-platform context aggregation
- Reference: `src/main/java/com/aria/core/strategy/`

### 8. [Platform Integration](PLATFORM_INTEGRATION.md)
- Telegram integration via Telethon (Python)
- Java-Python bridge implementation
- Platform abstraction layer
- Chat ingestion from platforms
- Message sending, editing, deleting, pinning
- Media handling
- Reference: `src/main/java/com/aria/platform/` and `scripts/telethon/`

### 9. [REST API](REST_API.md)
- API endpoints and controllers
- Request/response formats
- Authentication and authorization
- Error handling
- Reference: `src/main/java/com/aria/api/controller/`

### 10. [Frontend Components](FRONTEND_COMPONENTS.md)
- React component structure
- Routing and navigation
- State management
- API integration
- UI/UX patterns
- Reference: `frontend/src/components/`

### 11. [Automated Conversation Management](AUTOMATED_CONVERSATION.md)
- Conversation orchestration and state management
- Incoming message processing
- Response generation and scheduling
- Disinterest detection and alerts
- Human-in-the-loop features
- Reference: `src/main/java/com/aria/core/AutomatedConversationManager.java`

## Quick Links

### Key Files by Functionality

**REST API Controllers:**
- `src/main/java/com/aria/api/controller/TargetController.java` - Target user management
- `src/main/java/com/aria/api/controller/ConversationController.java` - Conversation management
- `src/main/java/com/aria/api/controller/PlatformController.java` - Platform registration
- `src/main/java/com/aria/api/controller/UserController.java` - User management
- `src/main/java/com/aria/api/controller/AnalysisController.java` - Analysis endpoints

**Core Orchestration:**
- `src/main/java/com/aria/core/AriaOrchestrator.java` - High-level orchestration
- `src/main/java/com/aria/core/AutomatedConversationManager.java` - Main conversation manager
- `src/main/java/com/aria/Main.java` - Application entry point (legacy)
- `src/main/java/com/aria/api/AriaApiApplication.java` - Spring Boot application entry point

**Data Models:**
- `src/main/java/com/aria/core/model/TargetUser.java` - Parent Target User model
- `src/main/java/com/aria/core/model/SubTargetUser.java` - Child SubTarget User model
- `src/main/java/com/aria/core/model/TargetGroup.java` - Parent Target Group model
- `src/main/java/com/aria/core/model/SubTargetGroup.java` - Child SubTarget Group model
- `src/main/java/com/aria/core/model/TargetChannel.java` - Parent Target Channel model
- `src/main/java/com/aria/core/model/SubTargetChannel.java` - Child SubTarget Channel model
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
- `src/main/java/com/aria/ai/ResponseGenerator.java` - Response generation

**Database:**
- `src/main/java/com/aria/storage/DatabaseManager.java` - Database operations
- `src/main/java/com/aria/storage/DatabaseSchema.java` - Schema definition

**Platform:**
- `src/main/java/com/aria/platform/telegram/TelegramConnector.java` - Telegram connector
- `scripts/telethon/chat_ingestor.py` - Python chat ingestion script
- `scripts/telethon/priority_ingestor.py` - Priority ingestion script
- `scripts/telethon/message_sender.py` - Message sending script
- `scripts/telethon/message_editor.py` - Message editing script
- `scripts/telethon/message_deleter.py` - Message deletion script
- `scripts/telethon/message_pinner.py` - Message pinning script
- `scripts/telethon/media_sender.py` - Media sending script

**Frontend Components:**
- `frontend/src/components/TargetManagement.js` - Target user list and management
- `frontend/src/components/EditTargetUser.js` - Target user editing
- `frontend/src/components/SubTargetUsersView.js` - SubTarget user management
- `frontend/src/components/ConversationView.js` - Conversation UI
- `frontend/src/components/AnalysisDashboard.js` - Analysis dashboard
- `frontend/src/services/api.js` - API client

**Services:**
- `src/main/java/com/aria/service/TargetUserService.java` - Target user business logic
- `src/main/java/com/aria/service/SubTargetUserService.java` - SubTarget user business logic
- `src/main/java/com/aria/service/UserService.java` - User business logic

## How to Use This Documentation

1. **Start with Architecture**: Understand the overall system design before diving into specifics
2. **Follow Data Flow**: Use the documentation to trace data from platform ingestion → categorization → analysis → response generation
3. **Reference Code**: Each section includes references to specific files and line numbers
4. **Cross-Reference**: Use links between documents to understand how components interact
5. **API Documentation**: See REST API documentation for endpoint details
6. **Frontend Guide**: See Frontend Components documentation for UI implementation

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

## Recent Updates

### Hierarchical Target-SubTarget Model
- Implemented parent-child relationship between Target Users and SubTarget Users
- Cross-platform context toggle for aggregating chat history
- Platform-specific conversation management

### REST API Architecture
- Migrated from JavaFX desktop app to Spring Boot REST API
- React frontend for modern web UI
- RESTful endpoints for all operations

### Target Groups & Channels
- Hierarchical structure for groups and channels
- Selective ingestion of explicitly defined groups/channels
- Message link storage for reference

### Enhanced Features
- Message pinning (syncs with Telegram)
- Media support (send, edit, delete)
- Real-time conversation UI with polling
- Analysis dashboard with platform filtering

---

*Last Updated: 2024-01-16*
