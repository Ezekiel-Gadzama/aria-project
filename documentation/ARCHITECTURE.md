# ARIA System Architecture

This document describes the overall architecture of the ARIA application, including design patterns, component interactions, and system design decisions.

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture Layers](#architecture-layers)
3. [Component Interactions](#component-interactions)
4. [Data Flow](#data-flow)
5. [Technology Stack](#technology-stack)
6. [Design Patterns](#design-patterns)

## System Overview

ARIA is a full-stack web application (Spring Boot REST API + React frontend) that automates and optimizes conversations on social platforms. The system uses AI to personalize communication strategies based on the user's successful historical chats.

### Core Principles

1. **Platform Abstraction**: Support for multiple platforms (Telegram, WhatsApp, Instagram) through a unified connector interface
2. **AI-Driven Personalization**: Uses OpenAI to analyze and categorize chats, then synthesizes communication styles
3. **Weighted Learning**: 70%/15%/15% weighting system (successful/failed/base) for response generation
4. **Hierarchical Data Model**: Target Users (parent) with SubTarget Users (children) for cross-platform management
5. **Cross-Platform Context**: Aggregate chat history across platforms for unified AI understanding
6. **Human-in-the-Loop**: Allows user intervention for complex decisions
7. **Token Management**: Intelligent filtering to manage OpenAI API token limits
8. **RESTful Architecture**: Clean separation between backend API and frontend UI

## Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│              Frontend Layer (React)                      │
│  TargetManagement | ConversationView | AnalysisDashboard │
│  EditTargetUser | SubTargetUsersView | Navigation        │
└─────────────────────────────────────────────────────────┘
                          ↓ HTTP/REST
┌─────────────────────────────────────────────────────────┐
│              REST API Layer (Spring Boot)                │
│  TargetController | ConversationController | Platform    │
│  UserController | AnalysisController                      │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│              Core Orchestration Layer                    │
│  AutomatedConversationManager | AriaOrchestrator        │
└─────────────────────────────────────────────────────────┘
                          ↓
        ┌─────────────────┴─────────────────┐
        ↓                                   ↓
┌──────────────────────┐        ┌──────────────────────┐
│   Analysis Layer     │        │    AI Services       │
│ - Categorization     │        │ - OpenAI Client      │
│ - Style Extraction   │        │ - Undetectable AI    │
│ - Success Scoring    │        │ - Summarization      │
│ - Disinterest Detect │        │ - Quiz Generation    │
└──────────────────────┘        └──────────────────────┘
        ↓                                   ↓
┌─────────────────────────────────────────────────────────┐
│              Strategy Layer                              │
│  WeightedResponseSynthesis | ResponseStrategy           │
└─────────────────────────────────────────────────────────┘
        ↓                                   ↓
┌──────────────────────┐        ┌──────────────────────┐
│  Platform Layer      │        │   Storage Layer      │
│ - Telegram Connector │        │ - Database Schema    │
│ - Platform Abstraction│       │ - Database Manager   │
│ - Python Scripts     │        │ - PostgreSQL         │
└──────────────────────┘        └──────────────────────┘
```

### Layer Descriptions

#### 1. Frontend Layer (React)
**Location**: `frontend/src/`

- **Purpose**: React-based web user interface for user interaction
- **Components**:
  - `App.js` - Main application router
  - `components/TargetManagement.js` - Target user list and management
  - `components/EditTargetUser.js` - Target user editing page
  - `components/SubTargetUsersView.js` - SubTarget user management page
  - `components/ConversationView.js` - Conversation UI with real-time messaging
  - `components/AnalysisDashboard.js` - Analysis and statistics dashboard
  - `components/PlatformRegistration.js` - Platform account registration
  - `components/UserRegistration.js` - User registration
  - `services/api.js` - API client for backend communication
- **Key Features**:
  - React Router for navigation
  - Real-time message polling
  - Optimistic UI updates
  - Media preview and upload
  - Message pinning UI
  - Cross-platform context toggle
- **Reference**: See [Frontend Components Documentation](FRONTEND_COMPONENTS.md)

#### 1b. REST API Layer (Spring Boot)
**Location**: `src/main/java/com/aria/api/`

- **Purpose**: RESTful API endpoints for frontend communication
- **Components**:
  - `controller/TargetController.java` - Target user CRUD operations
  - `controller/ConversationController.java` - Conversation management (messages, send, edit, delete, pin)
  - `controller/PlatformController.java` - Platform registration and management
  - `controller/UserController.java` - User management
  - `controller/AnalysisController.java` - Analysis and statistics endpoints
  - `dto/` - Data Transfer Objects for API requests/responses
- **Key Features**:
  - RESTful endpoints at `/api/*`
  - JSON request/response format
  - CORS support for frontend
  - Error handling with ApiResponse wrapper
  - SubTarget User support in all endpoints
- **Reference**: See [REST API Documentation](REST_API.md)

#### 2. Core Orchestration Layer
**Location**: `src/main/java/com/aria/core/`

- **Purpose**: Coordinates all system components and manages conversation lifecycle
- **Components**:
  - `AutomatedConversationManager.java` - Main conversation manager (lines 1-478)
  - `AriaOrchestrator.java` - High-level orchestration
  - `ApplicationInitializer.java` - Application startup initialization
  - `ConfigurationManager.java` - Configuration management
- **Key Responsibilities**:
  - Initialize conversations with goals
  - Process incoming messages
  - Coordinate AI services and analysis
  - Manage conversation state
  - Handle disinterest detection
  - Schedule responses
- **Reference**: See [Automated Conversation Management](AUTOMATED_CONVERSATION.md)

#### 3. Analysis Layer
**Location**: `src/main/java/com/aria/analysis/`

- **Purpose**: Analyze chat history, extract patterns, and detect engagement
- **Components**:
  - `ChatCategorizationService.java` - Categorizes chats using OpenAI
  - `SmartChatSelector.java` - Intelligent chat filtering with AND logic
  - `StyleExtractor.java` - Extracts communication style profiles
  - `SuccessScorer.java` - Calculates success scores for conversations
  - `DisinterestDetector.java` - Detects disinterest indicators
  - `ResponseTimingAnalyzer.java` - Analyzes optimal response timing
- **Reference**: See [Analysis Services Documentation](ANALYSIS_SERVICES.md)

#### 4. AI Services Layer
**Location**: `src/main/java/com/aria/ai/`

- **Purpose**: Integrations with external AI services
- **Components**:
  - `OpenAIClient.java` - OpenAI API client (lines 1-144)
  - `UndetectableAIClient.java` - Undetectable.ai API client
  - `ResponseGenerator.java` - Response generation using OpenAI
  - `ConversationSummarizer.java` - Conversation summarization
  - `QuizGenerator.java` - Quiz question generation
- **Reference**: See [AI Services Documentation](AI_SERVICES.md)

#### 5. Strategy Layer
**Location**: `src/main/java/com/aria/core/strategy/`

- **Purpose**: Implements the 70%/15%/15% weighted response synthesis
- **Components**:
  - `WeightedResponseSynthesis.java` - Core synthesis algorithm (lines 1-235)
  - `ResponseStrategy.java` - Strategy interface
  - `AdvancedResponseStrategy.java` - Advanced strategy implementation
  - `StrategyFactory.java` - Strategy factory pattern
- **Reference**: See [Response Strategy Documentation](RESPONSE_STRATEGY.md)

#### 6. Platform Layer
**Location**: `src/main/java/com/aria/platform/`

- **Purpose**: Platform-specific integrations (Telegram, WhatsApp, Instagram)
- **Components**:
  - `PlatformConnector.java` - Platform abstraction interface
  - `TelegramConnector.java` - Telegram implementation
  - `TelethonBridge.java` - Java-Python bridge for Telethon
  - `scripts/telethon/chat_ingestor.py` - Python script for Telegram chat ingestion
- **Reference**: See [Platform Integration Documentation](PLATFORM_INTEGRATION.md)

#### 7. Storage Layer
**Location**: `src/main/java/com/aria/storage/`

- **Purpose**: Database schema and operations
- **Components**:
  - `DatabaseSchema.java` - Schema definition and initialization (lines 1-239)
  - `DatabaseManager.java` - Database operations (CRUD)
  - `SecureStorage.java` - Secure credential storage
- **Reference**: See [Storage Documentation](STORAGE.md)

## Component Interactions

### Conversation Initialization Flow

```
User defines goal → AriaOrchestrator
                    ↓
              AutomatedConversationManager.initializeConversation()
                    ↓
        ┌───────────┴───────────┐
        ↓                       ↓
ChatCategorizationService   Goal stored in DB
  .getRelevantCategories()
        ↓
SmartChatSelector
  .getFilteredChats()
        ↓
StyleExtractor
  .extractStyleProfile()
        ↓
WeightedResponseSynthesis
  .synthesizeProfile()
        ↓
ResponseGenerator configured
        ↓
ConversationState created
```

**Code References**:
- `AutomatedConversationManager.java` lines 70-100: Conversation initialization
- `ChatCategorizationService.java` lines 50-80: Category retrieval
- `SmartChatSelector.java` lines 29-58: Chat filtering
- `WeightedResponseSynthesis.java` lines 35-70: Profile synthesis

### Incoming Message Processing Flow

```
Message received from platform
        ↓
AutomatedConversationManager.processIncomingMessage()
        ↓
DisinterestDetector.analyzeConversation()
        ↓ (if disinterest detected)
Alert user with recommendations
        ↓
ResponseTimingAnalyzer.shouldRespondNow()
        ↓ (if should respond)
Load historical examples (SmartChatSelector)
        ↓
Build enhanced prompt with:
  - Goal context
  - Successful examples
  - Style profile
        ↓
OpenAIClient.generateResponseWithContext()
        ↓
UndetectableAIClient.humanizeText()
        ↓
ResponseTimingAnalyzer.calculateOptimalResponseDelay()
        ↓
Schedule response (ScheduledExecutorService)
        ↓
Send via PlatformConnector
```

**Code References**:
- `AutomatedConversationManager.java` lines 120-200: Message processing
- `DisinterestDetector.java`: Disinterest analysis
- `ResponseTimingAnalyzer.java`: Timing calculation
- `OpenAIClient.java` lines 117-143: Context-aware generation

## Data Flow

### Chat Ingestion Flow

```
Telegram → chat_ingestor.py (Python)
            ↓
        Filter groups/channels/bots
            ↓
        Save to PostgreSQL:
          - dialogs table
          - messages table
          - media table
            ↓
Java: DatabaseManager
            ↓
ChatCategorizationService.categorizeChat()
            ↓
OpenAI API → Categories + relevance scores
            ↓
chat_goals table (dialog_id, category_name)
```

**Code References**:
- `scripts/telethon/chat_ingestor.py`: Python ingestion script
- `DatabaseManager.java`: Database save operations
- `ChatCategorizationService.java` lines 100-150: Categorization

### Response Generation Flow

```
User goal defined → Categories identified
                        ↓
            Historical chats retrieved
            (filtered, deduplicated)
                        ↓
            Style profiles extracted
                        ↓
            Weighted synthesis (70/15/15)
                        ↓
            Synthesized profile created
                        ↓
            Incoming message processed
                        ↓
            Enhanced prompt built
                        ↓
            OpenAI generates response
                        ↓
            Undetectable.ai humanizes
                        ↓
            Scheduled and sent
```

**Code References**:
- `AutomatedConversationManager.java` lines 250-350: Response generation
- `WeightedResponseSynthesis.java` lines 75-153: Profile blending
- `OpenAIClient.java`: Response generation

## Technology Stack

### Backend Technologies

- **Java 17+**: Primary language for application logic
- **Spring Boot 3.2.0**: REST API framework
- **Maven**: Build and dependency management
- **PostgreSQL 15+**: Relational database
- **OkHttp**: HTTP client for API calls
- **Gson**: JSON processing

### Frontend Technologies

- **React 18+**: UI framework
- **React Router 6+**: Client-side routing
- **Axios**: HTTP client for API calls
- **npm**: Package management

### External Services

- **OpenAI API**: Text generation, categorization, summarization
- **Undetectable.ai API**: AI text humanization
- **Telethon (Python)**: Telegram API integration

### Dependencies

**Backend** (See `pom.xml`):
- `org.springframework.boot:spring-boot-starter-web` - Spring Boot web
- `org.springframework.boot:spring-boot-starter-data-jpa` - JPA support
- `com.google.code.gson:gson` - JSON processing
- `org.json:json` - JSON handling
- `org.postgresql:postgresql` - PostgreSQL JDBC driver
- `com.squareup.okhttp3:okhttp` - HTTP client

**Frontend** (See `frontend/package.json`):
- `react` - React library
- `react-dom` - React DOM rendering
- `react-router-dom` - Routing
- `axios` - HTTP client
- `react-scripts` - Build tooling

## Design Patterns

### 1. Strategy Pattern

**Purpose**: Flexible response generation strategies

**Implementation**:
- `ResponseStrategy.java` - Strategy interface
- `BasicResponseStrategy.java`, `AdvancedResponseStrategy.java` - Concrete strategies
- `StrategyFactory.java` - Factory for strategy selection

**Reference**: `src/main/java/com/aria/core/strategy/StrategyFactory.java`

### 2. Factory Pattern

**Purpose**: Create response strategies based on context

**Implementation**:
- `StrategyFactory.java` - Creates appropriate strategy instances

### 3. Repository Pattern (Implicit)

**Purpose**: Abstract database operations

**Implementation**:
- `DatabaseManager.java` - Provides high-level database operations
- Methods like `saveDialog()`, `loadMessagesForDialog()`

**Reference**: `src/main/java/com/aria/storage/DatabaseManager.java`

### 4. Service Layer Pattern

**Purpose**: Separate business logic from data access

**Implementation**:
- Analysis services in `com.aria.analysis`
- AI services in `com.aria.ai`
- Platform services in `com.aria.platform`

### 5. Dependency Injection (Manual)

**Purpose**: Loose coupling between components

**Implementation**:
- Components receive dependencies through constructors
- Example: `AutomatedConversationManager` constructor receives all required services

**Reference**: `AutomatedConversationManager.java` lines 42-65

## Module Organization

```
Aria/
├── src/main/java/com/aria/
│   ├── api/                           # REST API layer
│   │   ├── controller/                # API controllers
│   │   │   ├── TargetController.java
│   │   │   ├── ConversationController.java
│   │   │   ├── PlatformController.java
│   │   │   ├── UserController.java
│   │   │   └── AnalysisController.java
│   │   └── dto/                       # Data Transfer Objects
│   ├── ai/                            # AI service integrations
│   ├── analysis/                      # Chat analysis services
│   ├── core/                          # Core orchestration
│   │   ├── model/                     # Data models
│   │   │   ├── TargetUser.java
│   │   │   ├── SubTargetUser.java
│   │   │   ├── TargetGroup.java
│   │   │   ├── SubTargetGroup.java
│   │   │   └── ...
│   │   └── strategy/                  # Response strategies
│   ├── platform/                      # Platform connectors
│   │   ├── telegram/
│   │   ├── whatsapp/
│   │   └── instagram/
│   ├── service/                       # Business logic services
│   ├── storage/                       # Database layer
│   └── AriaApiApplication.java        # Spring Boot entry point
├── frontend/
│   ├── src/
│   │   ├── components/                # React components
│   │   │   ├── TargetManagement.js
│   │   │   ├── EditTargetUser.js
│   │   │   ├── SubTargetUsersView.js
│   │   │   ├── ConversationView.js
│   │   │   └── ...
│   │   ├── services/
│   │   │   └── api.js                 # API client
│   │   └── App.js                     # Main app component
│   └── package.json
└── scripts/telethon/                  # Python scripts
    ├── chat_ingestor.py
    ├── priority_ingestor.py
    ├── message_sender.py
    └── ...
```

## Configuration Management

**Location**: `src/main/java/com/aria/core/ConfigurationManager.java`

**Purpose**: Centralized configuration management

**Sources**:
1. `src/main/resources/config.properties` - Default configuration
2. Environment variables - Override defaults
3. System properties - Runtime configuration

**Key Configuration Properties**:
- `openai.api.key` - OpenAI API key
- `undetectable.ai.api.key` - Undetectable.ai API key
- `database.url` - PostgreSQL connection URL
- `telegram.api.id` - Telegram API ID
- `telegram.api.hash` - Telegram API hash

## Error Handling

### Strategy

1. **Logging**: All errors are logged using SLF4J
2. **Graceful Degradation**: System continues operation when possible
3. **User Notifications**: Critical errors shown in UI
4. **Retry Logic**: API calls have retry mechanisms

### Error Types

- **API Errors**: Handled with retry and fallback responses
- **Database Errors**: Logged and user notified
- **Platform Errors**: Logged, connection status updated
- **Validation Errors**: User-friendly error messages

## Security Considerations

1. **API Keys**: Stored securely, never logged
2. **Database Credentials**: Configuration-based, not hardcoded
3. **User Data**: Encrypted at rest in database
4. **Platform Credentials**: Secured via Telethon session files

## Performance Optimizations

1. **Database Indexing**: Comprehensive indexes on frequently queried columns
   - See `DatabaseSchema.java` lines 183-227
2. **Token Management**: Progressive AND filtering to reduce OpenAI API costs
   - See `SmartChatSelector.java` lines 29-58
3. **Connection Pooling**: Database connection reuse
4. **Caching**: Style profiles cached to avoid recalculation

## Future Architecture Considerations

### Potential Enhancements

1. **Microservices**: Split into separate services for scalability
2. **Message Queue**: Use queues for async message processing
3. **Caching Layer**: Redis for frequently accessed data
4. **Event-Driven**: Event sourcing for conversation state
5. **Multi-tenancy**: Support multiple users with isolation

---

**Next**: [Core Models Documentation](CORE_MODELS.md) | [Storage Documentation](STORAGE.md)

